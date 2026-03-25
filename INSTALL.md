# Installation

## Requirements

- **Java 17** (OpenJDK recommended — e.g., Eclipse Temurin)
- **Git** (for JGit operations within tests)
- **Maven** is included via Maven Wrapper (`./mvnw`) — no global install needed

## Option A: Docker (Recommended)

All Docker commands must be run from the **workspace root** (the parent directory containing `Vitruv-Change/`, `Vitruv/`, and `BrakeCaseStudy/`):

```bash
# From workspace root:
docker build -f BrakeCaseStudy/Dockerfile -t vitruv-merge-ae .
docker run --rm vitruv-merge-ae                  # comparison tables (both case studies)
docker run --rm vitruv-merge-ae --performance    # + performance benchmarks
docker run --rm vitruv-merge-ae --all            # + Track B (63 scenarios) + benchmarks
```

## Option B: Local Build

```bash
# 1. Build dependencies (order matters)
cd Vitruv-Change && ./mvnw clean install -Dmaven.test.skip=true
cd ../Vitruv      && ./mvnw clean install -Dmaven.test.skip=true

# 2. Run evaluation
cd ../BrakeCaseStudy && bash reproduce.sh

# Or run individual tests:
./mvnw -pl vsum test -Dtest=ThreeModelBranchingMergeTest        # S1-S9
./mvnw -pl vsum test -Dtest=MergeApproachComparisonTest         # comparison table
./mvnw -pl vsum test -Dtest=MergePerformanceBenchmarkTest       # performance
```

## Output

- `vsum/target/merge-comparison-table.md` — Markdown comparison table (Table 1)
- `vsum/target/trackb-summary.md` — Track B evaluation summary (63 scenarios)
- `vsum/target/benchmark-results/` — CSV and markdown performance data (if run)

## Troubleshooting

- **Xtend compilation errors**: Use `-Dmaven.test.skip=true` for dependency builds.
- **Out of memory**: Increase heap with `export MAVEN_OPTS="-Xmx4g"`.
- **Windows**: Use Git Bash or WSL. Maven Wrapper works cross-platform.
