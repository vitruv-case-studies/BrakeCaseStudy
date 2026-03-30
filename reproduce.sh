#!/bin/bash
#
# Reproduces evaluation results for the MODELS 2026 paper:
# "Replay-Based Merge for Delta-Based Consistency Management
#  in Multi-Model Development"
#
# Usage:
#   ./reproduce.sh           # Run all research questions (RQ1 + RQ2 + RQ3)
#   ./reproduce.sh --rq1     # RQ1 only: conflict classification (both case studies)
#   ./reproduce.sh --rq2     # RQ2 only: robustness (105 generated scenarios)
#   ./reproduce.sh --rq3     # RQ3 only: scalability benchmarks
#
# Flags can be combined: ./reproduce.sh --rq1 --rq3
#
# Requirements: Java 17 (OpenJDK), Maven (wrapper included)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="$SCRIPT_DIR/output"

RUN_RQ1=false
RUN_RQ2=false
RUN_RQ3=false
EXPLICIT=false

for arg in "$@"; do
    case "$arg" in
        --rq1) RUN_RQ1=true; EXPLICIT=true ;;
        --rq2) RUN_RQ2=true; EXPLICIT=true ;;
        --rq3) RUN_RQ3=true; EXPLICIT=true ;;
        *) echo "Unknown flag: $arg"; echo "Usage: $0 [--rq1] [--rq2] [--rq3]"; exit 1 ;;
    esac
done

# No flags = run everything
if ! $EXPLICIT; then
    RUN_RQ1=true
    RUN_RQ2=true
    RUN_RQ3=true
fi

# Count total steps (4 build steps + selected RQs)
TOTAL_STEPS=4
if $RUN_RQ1; then TOTAL_STEPS=$((TOTAL_STEPS + 2)); fi
if $RUN_RQ2; then TOTAL_STEPS=$((TOTAL_STEPS + 1)); fi
if $RUN_RQ3; then TOTAL_STEPS=$((TOTAL_STEPS + 1)); fi

STEP=0
next_step() {
    STEP=$((STEP + 1))
    echo "[$STEP/$TOTAL_STEPS] $1"
}

echo "=================================================="
echo " Vitruvius Replay-Based Merge — Evaluation Runner"
echo "=================================================="
echo ""

# Build Vitruv-Change from source. The remote SNAPSHOT has broken Xtend bytecode.
# Step 1: full reactor build (--fail-at-end tolerates Xtend test compile failures).
# Step 2: rebuild propagation module specifically to fix its broken jar.
next_step "Building dependencies (Vitruv-Change, Vitruv)..."
cd "$WORKSPACE/Vitruv-Change" && ./mvnw clean install -Dmaven.test.skip=true -q
cd "$WORKSPACE/Vitruv-Change" && ./mvnw install -pl propagation -am -Dmaven.test.skip=true -ff -q
cd "$WORKSPACE/Vitruv" && ./mvnw clean install -Dmaven.test.skip=true -q
echo "      Dependencies built successfully."

# Build Vitruv-Merge-Tests (shared comparison infrastructure, dependency of BrakeCaseStudy and MobSTr)
next_step "Building Vitruv-Merge-Tests..."
cd "$WORKSPACE/Vitruv-Merge-Tests" && ./mvnw clean install -Dmaven.test.skip=true -q
echo "      Vitruv-Merge-Tests built successfully."

# Build BrakeCaseStudy (compile tests but don't run yet)
next_step "Building BrakeCaseStudy..."
cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw clean install -DskipTests -q
echo "      BrakeCaseStudy built successfully."

# Build MobSTr case study
next_step "Building MobSTr case study..."
cd "$WORKSPACE/mobstr-vsum" && ./mvnw clean install -DskipTests -q
echo "      MobSTr case study built successfully."

# RQ1: Conflict classification (both case studies)
if $RUN_RQ1; then
    next_step "Running RQ1: Brake conflict classification (Vitruvius vs EMFCompare)..."
    echo ""

    echo "--- Brake RQ1: Conflict Classification (Vitruvius vs EMFCompare) ---"
    cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
        -Dtest=BrakeConflictClassificationTest#generateComparisonTable \
        -Devaluation.outputDir="$OUTPUT_DIR" \
        -Dsurefire.useFile=false

    next_step "Running RQ1: MobSTr conflict classification (Vitruvius vs EMFCompare)..."
    echo ""

    echo "--- MobSTr RQ1: Conflict Classification (Vitruvius vs EMFCompare) ---"
    cd "$WORKSPACE/mobstr-vsum" && ./mvnw -pl vsum test \
        -Dtest=MobSTrConflictClassificationTest \
        -Devaluation.outputDir="$OUTPUT_DIR" \
        -Dsurefire.useFile=false
fi

# RQ2: Robustness evaluation (105 scenarios)
if $RUN_RQ2; then
    echo ""
    next_step "Running RQ2: Robustness evaluation (105 scenarios)..."
    echo ""

    echo "--- Brake RQ2: Robustness of Reduction (105 generated scenarios) ---"
    cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
        -Dtest=BrakeRobustnessEvaluationTest \
        -Devaluation.outputDir="$OUTPUT_DIR" \
        -Dsurefire.useFile=false
fi

# RQ3: Scalability benchmarks
if $RUN_RQ3; then
    echo ""
    next_step "Running RQ3: Scalability benchmarks..."
    echo ""

    echo "--- Brake RQ3: Scalability (E1: model size, E2: history length) ---"
    cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
        -Dtest="BrakeScalabilityBenchmarkTest#e1_fullSuite+e2_fullSuite" \
        -Devaluation.outputDir="$OUTPUT_DIR" \
        -Dsurefire.useFile=false
fi

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
if $RUN_RQ3; then
    echo "   Brake RQ3:  output/brake-RQ3-scalability-*/"
fi
echo "=================================================="
