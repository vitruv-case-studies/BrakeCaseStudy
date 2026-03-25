#!/bin/bash
#
# Reproduces evaluation results for the MODELS 2026 paper:
# "Directed Replay-Based Merge for Multi-Model Consistency Networks"
#
# Usage:
#   ./reproduce.sh                  # Run scenario comparison (Table 1)
#   ./reproduce.sh --performance    # Also run performance benchmarks
#   ./reproduce.sh --all            # Run everything
#
# Requirements: Java 17 (OpenJDK), Maven (wrapper included)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE="$(dirname "$SCRIPT_DIR")"

echo "=================================================="
echo " Vitruvius Replay-Based Merge — Evaluation Runner"
echo "=================================================="
echo ""

# Build dependencies
echo "[1/3] Building dependencies..."
cd "$WORKSPACE/Vitruv-Change" && ./mvnw clean install -Dmaven.test.skip=true -q
cd "$WORKSPACE/Vitruv" && ./mvnw clean install -Dmaven.test.skip=true -q
echo "      Dependencies built successfully."

# Build BrakeCaseStudy (compile tests but don't run yet)
echo "[2/3] Building BrakeCaseStudy..."
cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw clean install -DskipTests -q
echo "      BrakeCaseStudy built successfully."

# Run evaluation
echo "[3/3] Running evaluation..."
echo ""

echo "--- Scenario Comparison (Table 1: Vitruvius vs EMFCompare) ---"
cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
    -Dtest=MergeApproachComparisonTest#generateComparisonTable \
    -Dsurefire.useFile=false 2>&1 | grep -E '^\||\#|Summary|Vitruvius|EMFCompare|==='

echo ""
echo "--- Bidirectional Merge Scenarios (S8-S9) ---"
cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
    -Dtest=ThreeModelBranchingMergeTest#s8_bidirectionalMerge_reverseResolvesIndirectConflict,ThreeModelBranchingMergeTest#s9_bidirectionalMerge_bothDirectionsConflict \
    -Dsurefire.useFile=false 2>&1 | grep -E 'Tests run|BUILD'

if [[ "${1:-}" == "--performance" || "${1:-}" == "--all" ]]; then
    echo ""
    echo "--- Performance Benchmarks ---"
    cd "$WORKSPACE/BrakeCaseStudy" && ./mvnw -pl vsum test \
        -Dtest=MergePerformanceBenchmarkTest#fullBenchmarkSuite \
        -Dsurefire.useFile=false
fi

echo ""
echo "=================================================="
echo " Evaluation complete."
echo " Comparison table: BrakeCaseStudy/target/merge-comparison-table.md"
echo "=================================================="
