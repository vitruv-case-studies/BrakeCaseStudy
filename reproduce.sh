#!/bin/bash
#
# Reproduces evaluation results for the MODELS 2026 paper:
# "Replay-Based Merge for Delta-Based Consistency Management
#  in Multi-Model Development"
#
# Usage:
#   ./reproduce.sh                  # Full run: build + RQ1 + RQ2 + RQ3
#   ./reproduce.sh --rq1            # RQ1 only: conflict classification (both case studies)
#   ./reproduce.sh --rq2            # RQ2 only: robustness (105 generated scenarios)
#   ./reproduce.sh --rq3            # RQ3 only: scalability benchmarks (E1 + E2 + E6)
#   ./reproduce.sh --e1             # E1 only: model size scaling (Brake)
#   ./reproduce.sh --e2             # E2 only: history length scaling (Brake)
#   ./reproduce.sh --e3             # E3 only: conflict density (not in paper)
#   ./reproduce.sh --e4             # E4 only: reaction density (not in paper)
#   ./reproduce.sh --e5             # E5 only: bidirectional overhead (not in paper)
#   ./reproduce.sh --e6             # E6 only: Brake RQ1 scenario timings (Table 1)
#   ./reproduce.sh --scenarios      # ThreeModelBranchingMergeTest (S1-S13, D1-D5, C1-C2)
#   ./reproduce.sh --branching      # BranchingMergeTest (two-model merge scenarios)
#   ./reproduce.sh --brake2cad      # BrakeDisk2CadTest (M1→M2 propagation unit tests)
#   ./reproduce.sh --cad2brake      # Cad2BrakeDiskTest (M2→M1 propagation unit tests)
#   ./reproduce.sh --all-tests      # Run ALL test classes
#   ./reproduce.sh --debug          # Enable DEBUG logging for merge engine
#   ./reproduce.sh --no-build       # Skip build step
#
# Flags can be combined: ./reproduce.sh --rq1 --e6 --no-build --debug
#
# Requirements: Java 17 (OpenJDK), Maven (wrapper included)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="$SCRIPT_DIR/output"
LOG4J_CONFIG="$SCRIPT_DIR/vsum/src/test/resources/log4j2-test.xml"

RUN_RQ1=false
RUN_RQ2=false
RUN_RQ3=false
NO_BUILD=false
EXPLICIT=false
DEBUG_MODE=false

# Individual E experiments (override --rq3 selection)
RUN_E1=false; RUN_E2=false; RUN_E3=false; RUN_E4=false; RUN_E5=false; RUN_E6=false
E_EXPLICIT=false

# Individual test classes
RUN_SCENARIOS=false
RUN_BRANCHING=false
RUN_BRAKE2CAD=false
RUN_CAD2BRAKE=false
RUN_ALL_TESTS=false

for arg in "$@"; do
    case "$arg" in
        --rq1)       RUN_RQ1=true; EXPLICIT=true ;;
        --rq2)       RUN_RQ2=true; EXPLICIT=true ;;
        --rq3)       RUN_RQ3=true; EXPLICIT=true ;;
        --e1)        RUN_E1=true; E_EXPLICIT=true; EXPLICIT=true ;;
        --e2)        RUN_E2=true; E_EXPLICIT=true; EXPLICIT=true ;;
        --e3)        RUN_E3=true; E_EXPLICIT=true; EXPLICIT=true ;;
        --e4)        RUN_E4=true; E_EXPLICIT=true; EXPLICIT=true ;;
        --e5)        RUN_E5=true; E_EXPLICIT=true; EXPLICIT=true ;;
        --e6)        RUN_E6=true; E_EXPLICIT=true; EXPLICIT=true ;;
        --scenarios) RUN_SCENARIOS=true; EXPLICIT=true ;;
        --branching) RUN_BRANCHING=true; EXPLICIT=true ;;
        --brake2cad) RUN_BRAKE2CAD=true; EXPLICIT=true ;;
        --cad2brake) RUN_CAD2BRAKE=true; EXPLICIT=true ;;
        --all-tests) RUN_ALL_TESTS=true; EXPLICIT=true ;;
        --debug)     DEBUG_MODE=true ;;
        --no-build)  NO_BUILD=true ;;
        *) echo "Unknown flag: $arg"
           echo "Usage: $0 [--rq1] [--rq2] [--rq3] [--e1..--e6] [--scenarios] [--branching] [--brake2cad] [--cad2brake] [--all-tests] [--debug] [--no-build]"
           exit 1 ;;
    esac
done

# No flags = run RQ1 + RQ2 + RQ3
if ! $EXPLICIT; then
    RUN_RQ1=true
    RUN_RQ2=true
    RUN_RQ3=true
fi

# --rq3 without individual --eN flags = run E1 + E2 + E6 (paper experiments)
if $RUN_RQ3 && ! $E_EXPLICIT; then
    RUN_E1=true; RUN_E2=true; RUN_E6=true
fi

echo "=================================================="
echo " Vitruvius Replay-Based Merge — BrakeCaseStudy"
echo "=================================================="
echo ""

# ── Debug mode: toggle log4j2 level ──

LOG4J_ORIGINAL_LEVEL=""
if $DEBUG_MODE; then
    echo "[debug] Enabling DEBUG logging for merge engine..."
    if [ -f "$LOG4J_CONFIG" ]; then
        # Save original level for restoration
        LOG4J_ORIGINAL_LEVEL=$(sed -n 's/.*<Logger name="tools.vitruv.framework.vsum.branch.merge" level="\([^"]*\)".*/\1/p' "$LOG4J_CONFIG")
        sed -i 's/\(<Logger name="tools.vitruv.framework.vsum.branch.merge" level="\)[^"]*"/\1DEBUG"/' "$LOG4J_CONFIG"
        echo "         Log level set to DEBUG (was: ${LOG4J_ORIGINAL_LEVEL:-INFO})"
    fi
    echo ""
fi

# Restore log level on exit
cleanup() {
    if [ -n "$LOG4J_ORIGINAL_LEVEL" ] && [ -f "$LOG4J_CONFIG" ]; then
        sed -i "s/\(<Logger name=\"tools.vitruv.framework.vsum.branch.merge\" level=\"\)[^\"]*\"/\1${LOG4J_ORIGINAL_LEVEL}\"/" "$LOG4J_CONFIG"
        echo "[debug] Restored log level to $LOG4J_ORIGINAL_LEVEL"
    fi
}
trap cleanup EXIT

# ── Build ──

if ! $NO_BUILD; then
    echo "[build] Building dependencies (Vitruv-Change, Vitruv)..."
    cd "$WORKSPACE/Vitruv-Change" && ./mvnw clean install -Dmaven.test.skip=true -q
    cd "$WORKSPACE/Vitruv-Change" && ./mvnw install -pl propagation -am -Dmaven.test.skip=true -ff -q
    cd "$WORKSPACE/Vitruv" && ./mvnw clean install -Dmaven.test.skip=true -q
    echo "        Dependencies built successfully."

    echo "[build] Building Vitruv-Merge-Tests..."
    cd "$WORKSPACE/Vitruv-Merge-Tests" && ./mvnw clean install -Dmaven.test.skip=true -q
    echo "        Vitruv-Merge-Tests built successfully."

    echo "[build] Building BrakeCaseStudy..."
    cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw clean install -DskipTests -q
    echo "        BrakeCaseStudy built successfully."

    if $RUN_RQ1; then
        echo "[build] Building MobSTr case study..."
        cd "$WORKSPACE/mobstr-vsum" && ./mvnw clean install -DskipTests -q
        echo "        MobSTr case study built successfully."
    fi
else
    echo "[build] Skipped (--no-build)"
fi

# System properties forwarded to test JVMs
SUREFIRE_SYS_PROPS="-Devaluation.outputDir=$OUTPUT_DIR"

echo ""

# ── Helper function to run a BrakeCaseStudy test ──

run_brake_test() {
    local test_spec="$1"
    local label="$2"
    echo ""
    echo "--- $label ---"
    cd "$SCRIPT_DIR" && ./mvnw -pl vsum test \
        -Dtest="$test_spec" \
        -DargLine="$SUREFIRE_SYS_PROPS" \
        -Dsurefire.useFile=false
}

# ── RQ1: Conflict classification ──

if $RUN_RQ1; then
    echo "--- RQ1: Brake conflict classification (Vitruvius vs EMFCompare) ---"
    cd "$SCRIPT_DIR" && ./mvnw -pl vsum test \
        -Dtest=BrakeConflictClassificationTest#generateComparisonTable \
        -DargLine="$SUREFIRE_SYS_PROPS" \
        -Dsurefire.useFile=false

    echo ""
    echo "--- RQ1: MobSTr conflict classification (Vitruvius vs EMFCompare) ---"
    cd "$WORKSPACE/mobstr-vsum" && ./mvnw -pl vsum test \
        -Dtest=MobSTrConflictClassificationTest \
        -DargLine="$SUREFIRE_SYS_PROPS" \
        -Dsurefire.useFile=false
fi

# ── RQ2: Robustness evaluation ──

if $RUN_RQ2; then
    run_brake_test "BrakeRobustnessEvaluationTest" \
        "RQ2: Robustness of Reduction (105 generated scenarios)"
fi

# ── RQ3 / Individual E experiments ──

run_experiment() {
    local test_method="$1"
    local label="$2"
    run_brake_test "BrakeScalabilityBenchmarkTest#$test_method" "RQ3: $label"
}

$RUN_E1 && run_experiment "e1_fullSuite" "E1: Model size scaling (10-1000 components)"
$RUN_E2 && run_experiment "e2_fullSuite" "E2: History length scaling (1-50 transactions)"
$RUN_E3 && run_experiment "e3_conflictDensity" "E3: Conflict density (overlap 0-50%)"
$RUN_E4 && run_experiment "e4_reactionDensity" "E4: Reaction density (low vs high)"
$RUN_E5 && run_experiment "e5_bidirectionalOverhead" "E5: Bidirectional merge overhead"
$RUN_E6 && run_experiment "e6_rq1ScenarioTimings" "E6: Brake RQ1 scenario timings"

# ── Individual test classes ──

$RUN_SCENARIOS && run_brake_test "ThreeModelBranchingMergeTest" \
    "ThreeModelBranchingMergeTest (S1-S13, D1-D5, C1-C2)"

$RUN_BRANCHING && run_brake_test "BranchingMergeTest" \
    "BranchingMergeTest (two-model merge scenarios)"

$RUN_BRAKE2CAD && run_brake_test "BrakeDisk2CadTest" \
    "BrakeDisk2CadTest (M1→M2 propagation)"

$RUN_CAD2BRAKE && run_brake_test "Cad2BrakeDiskTest" \
    "Cad2BrakeDiskTest (M2→M1 propagation)"

if $RUN_ALL_TESTS; then
    echo ""
    echo "--- Running ALL BrakeCaseStudy tests ---"
    cd "$SCRIPT_DIR" && ./mvnw -pl vsum test \
        -DargLine="$SUREFIRE_SYS_PROPS" \
        -Dsurefire.useFile=false
fi

# ── Summary ──

echo ""
echo "=================================================="
echo " Evaluation complete."
echo ""
echo " All results written to: $OUTPUT_DIR"
echo ""
if $RUN_RQ1; then
    echo "   Brake RQ1:  output/brake-RQ1-conflicts-*/"
    echo "   MobSTr RQ1: output/mobstr-RQ1-conflicts-*/"
fi
if $RUN_RQ2; then
    echo "   Brake RQ2:  output/brake-RQ2-robustness-*/"
fi
if $RUN_E1 || $RUN_E2 || $RUN_E3 || $RUN_E4 || $RUN_E5 || $RUN_E6; then
    echo "   Brake RQ3:  output/brake-RQ3-scalability-*/"
fi
echo "=================================================="
