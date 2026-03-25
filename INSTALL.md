# Installation

## Requirements

- **Java 17** (OpenJDK recommended — e.g., Eclipse Temurin)
- **Git** (for JGit operations within tests)
- **Maven** is included via Maven Wrapper (`./mvnw`) — no global install needed

## Option A: Docker (Recommended)

```bash
docker build -t vitruv-merge-ae .
docker run --rm vitruv-merge-ae                  # scenario comparison table
docker run --rm vitruv-merge-ae --performance    # + performance benchmarks
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

- `target/merge-comparison-table.md` — Markdown comparison table (Table 1)
- `target/benchmark-results/` — CSV and markdown performance data (if run)

## Troubleshooting

- **Xtend compilation errors**: Use `-Dmaven.test.skip=true` for dependency builds.
- **Out of memory**: Increase heap with `export MAVEN_OPTS="-Xmx4g"`.
- **Windows**: Use Git Bash or WSL. Maven Wrapper works cross-platform.
