#!/bin/bash
#
# Reproduces evaluation results for the MODELS 2026 paper:
# "Directed Replay-Based Merge for Multi-Model Consistency Networks"
#
# Usage:
#   ./reproduce.sh                  # Run both case study comparison tables (Table 1)
#   ./reproduce.sh --performance    # Also run performance benchmarks (E1-E5)
#   ./reproduce.sh --all            # Run everything: comparisons + Track B (105 scenarios) + performance
#
# Requirements: Java 17 (OpenJDK), Maven (wrapper included)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="$SCRIPT_DIR/output"

RUN_PERFORMANCE=false
RUN_TRACK_B=false

case "${1:-}" in
    --performance)
        RUN_PERFORMANCE=true
        ;;
    --all)
        RUN_PERFORMANCE=true
        RUN_TRACK_B=true
        ;;
esac

# Count total steps
TOTAL_STEPS=6
if $RUN_TRACK_B; then
    TOTAL_STEPS=$((TOTAL_STEPS + 1))
fi
if $RUN_PERFORMANCE; then
    TOTAL_STEPS=$((TOTAL_STEPS + 1))
fi

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

# Run BrakeCaseStudy evaluation
next_step "Running BrakeCaseStudy comparison (Table 1a: Vitruvius vs EMFCompare)..."
echo ""

echo "--- Brake RQ1: Conflict Classification (Vitruvius vs EMFCompare) ---"
cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
    -Dtest=BrakeConflictClassificationTest#generateComparisonTable \
    -Devaluation.outputDir="$OUTPUT_DIR" \
    -Dsurefire.useFile=false

# Run MobSTr comparison
next_step "Running MobSTr comparison (RQ1: Vitruvius vs EMFCompare)..."
echo ""

echo "--- MobSTr RQ1: Conflict Classification (Vitruvius vs EMFCompare) ---"
cd "$WORKSPACE/mobstr-vsum" && ./mvnw -pl vsum test \
    -Dtest=MobSTrConflictClassificationTest \
    -Devaluation.outputDir="$OUTPUT_DIR" \
    -Dsurefire.useFile=false

# RQ2: Robustness evaluation (105 scenarios) — only with --all
if $RUN_TRACK_B; then
    echo ""
    next_step "Running RQ2: Robustness evaluation (105 scenarios)..."
    echo ""

    echo "--- Brake RQ2: Robustness of Reduction (105 generated scenarios) ---"
    cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
        -Dtest=BrakeRobustnessEvaluationTest \
        -Devaluation.outputDir="$OUTPUT_DIR" \
        -Dsurefire.useFile=false
fi

# RQ3: Scalability benchmarks — with --performance or --all
if $RUN_PERFORMANCE; then
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
echo "   Brake RQ1:  output/brake-RQ1-conflicts-*/"
echo "   MobSTr RQ1: output/mobstr-RQ1-conflicts-*/"
if $RUN_TRACK_B; then
    echo "   Brake RQ2:  output/brake-RQ2-robustness-*/"
fi
if $RUN_PERFORMANCE; then
    echo "   Brake RQ3:  output/brake-RQ3-scalability-*/"
fi
echo "=================================================="
