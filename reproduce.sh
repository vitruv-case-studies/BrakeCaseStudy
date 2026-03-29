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

# Build dependencies
next_step "Building dependencies (Vitruv-Change, Vitruv)..."
cd "$WORKSPACE/Vitruv-Change" && ./mvnw clean install -Dmaven.test.skip=true -q
cd "$WORKSPACE/Vitruv" && ./mvnw clean install -Dmaven.test.skip=true -q
echo "      Dependencies built successfully."

# Build BrakeCaseStudy (compile tests but don't run yet)
next_step "Building BrakeCaseStudy..."
cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw clean install -DskipTests -q
echo "      BrakeCaseStudy built successfully."

# Build Vitruv-Merge-Tests (shared comparison infrastructure, dependency of MobSTr)
next_step "Building Vitruv-Merge-Tests..."
cd "$WORKSPACE/Vitruv-Merge-Tests" && ./mvnw clean install -Dmaven.test.skip=true -q
echo "      Vitruv-Merge-Tests built successfully."

# Build MobSTr case study
next_step "Building MobSTr case study..."
cd "$WORKSPACE/mobstr-vsum" && ./mvnw clean install -DskipTests -q
echo "      MobSTr case study built successfully."

# Run BrakeCaseStudy evaluation
next_step "Running BrakeCaseStudy comparison (Table 1a: Vitruvius vs EMFCompare)..."
echo ""

echo "--- BrakeCaseStudy Scenario Comparison (Vitruvius vs EMFCompare) ---"
cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
    -Dtest=MergeApproachComparisonTest#generateComparisonTable \
    -Dsurefire.useFile=false

echo ""
echo "--- Bidirectional Merge Scenarios (S8-S9) ---"
cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
    -Dtest=ThreeModelBranchingMergeTest#s8_bidirectionalMerge_reverseResolvesIndirectConflict,ThreeModelBranchingMergeTest#s9_bidirectionalMerge_bothDirectionsConflict \
    -Dsurefire.useFile=false

# Run MobSTr comparison
next_step "Running MobSTr comparison (Table 1b: Vitruvius vs EMFCompare)..."
echo ""

echo "--- MobSTr Scenario Comparison (Vitruvius vs EMFCompare) ---"
cd "$WORKSPACE/mobstr-vsum" && ./mvnw -pl vsum test \
    -Dtest=MobSTrComparisonTest \
    -Dsurefire.useFile=false

# Track B evaluation (105 scenarios) — only with --all
if $RUN_TRACK_B; then
    echo ""
    next_step "Running Track B evaluation (105 scenarios)..."
    echo ""

    echo "--- Track B Evaluation (105 scenarios) ---"
    cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
        -Dtest=TrackBEvaluationTest \
        -Dsurefire.useFile=false
fi

# Performance benchmarks — with --performance or --all
if $RUN_PERFORMANCE; then
    echo ""
    next_step "Running performance benchmarks (E1-E5)..."
    echo ""

    echo "--- Performance Benchmarks (E1: model size, E2: history length — 5 reps + warmup) ---"
    cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
        -Dtest="MergePerformanceBenchmarkTest#e1_fullSuite+e2_fullSuite" \
        -Dsurefire.useFile=false
fi

echo ""
echo "=================================================="
echo " Evaluation complete."
echo ""
echo " Output locations:"
echo "   BrakeCaseStudy comparison: BrakeCaseStudy/vsum/target/merge-comparison-table.md"
echo "   MobSTr comparison:        mobstr-vsum/vsum/target/merge-comparison-table.md"
if $RUN_TRACK_B; then
    echo "   Track B results:           BrakeCaseStudy/vsum/target/trackb-summary.md"
fi
if $RUN_PERFORMANCE; then
    echo "   Performance benchmarks:    BrakeCaseStudy/vsum/target/benchmark-results/"
fi
echo "=================================================="
