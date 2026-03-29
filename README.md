# Vitruvius Brake Case Study 
- based on the Methodologist Template (see [here](https://github.com/vitruv-tools/Methodologist-Template/tree/main))

## Getting Started

The project comes with a maven wrapper, so you can run it without installing Maven.

### Dependencies

This project depends on the following repositories, which must be built first (in order):

1. **[Vitruv-Change](https://github.com/vitruv-tools/Vitruv-Change)** — Change metamodels, propagation interfaces
2. **[Vitruv](https://github.com/AnneKoziolek/Vitruv)** (`anne-branching` branch) — Core framework with branching/merge engine
3. **[Vitruv-Merge-Tests](https://github.com/AnneKoziolek/Vitruv-Merge-Tests)** — Shared comparison infrastructure (EMFCompare baseline)

### Build

```bash
# Build dependencies (from the workspace root):
cd /workspace/Vitruv-Change      && ./mvnw clean install -Dmaven.test.skip=true
cd /workspace/Vitruv-Change      && ./mvnw install -pl propagation -am -Dmaven.test.skip=true -ff
cd /workspace/Vitruv             && ./mvnw clean install -Dmaven.test.skip=true
cd /workspace/Vitruv-Merge-Tests && ./mvnw clean install -Dmaven.test.skip=true

# Build and verify:
cd /workspace/BrakeCaseStudy && ./mvnw clean verify
```

The tests are located in the `vsum` folder.


## Documentation of the Case Study

In the wiki, you can find the documentation of the different ecore models and the semantic overlaps defined between the different metamodels.

## General Project Structure


model
-----
This folder contains the model in the ecore format. When you do not use eclipse, please provide a genmodel of your ecore model so that code can be generated. 

consistency
-----------
This folder contains the consistency specifications, like reactions.

viewType
--------
This folder contains the definition of the view types. These are necessary to create views of the vsum. 

vsum
----
This folder contains the VSUM, integration tests, and the merge approach comparison framework.

### Branching Merge Tests

The `vsum` module contains tests for the semantic three-way merge across three models (Brakesystem, CAD, Safety). Seven scenarios (S1–S7) cover clean merges, indirect conflicts, user-vs-derived warnings, and direct conflicts.

```bash
# Run all branching merge tests:
./mvnw -pl vsum test -Dtest=ThreeModelBranchingMergeTest
```

### Merge Approach Comparison (Vitruvius vs EMFCompare)

The `comparison/` subpackage contains a baseline comparison framework that runs the same 7 scenarios through both the Vitruvius semantic merge and EMFCompare three-way merge. EMFCompare serves as the state-of-the-art baseline that treats all model changes equally — it cannot distinguish user-authored (original) changes from reaction-derived (consequential) changes.

```bash
# Generate the comparison table:
./mvnw -pl vsum test -Dtest=MergeApproachComparisonTest#generateComparisonTable

# Run individual approach tests:
./mvnw -pl vsum test -Dtest=MergeApproachComparisonTest#vitruviusMerge
./mvnw -pl vsum test -Dtest=MergeApproachComparisonTest#emfCompareMerge
```

The comparison table is written to `vsum/target/merge-comparison-table.md`.

### Git Merge Driver Integration

The semantic merge can be invoked automatically by `git merge` via a custom merge driver.
This is a prototype integration that demonstrates end-to-end usage.

#### Prerequisites

- Java 17+
- The full workspace must be built first (see [Build](#getting-started))

#### Quick Setup

```bash
# Build all dependencies (from the workspace root):
cd /workspace/Vitruv-Change      && ./mvnw clean install -Dmaven.test.skip=true
cd /workspace/Vitruv-Change      && ./mvnw install -pl propagation -am -Dmaven.test.skip=true -ff
cd /workspace/Vitruv             && ./mvnw clean install -Dmaven.test.skip=true
cd /workspace/Vitruv-Merge-Tests && ./mvnw clean install -Dmaven.test.skip=true
cd /workspace/BrakeCaseStudy     && ./mvnw clean install -Dmaven.test.skip=true

# Configure the merge driver for this repository:
cd /workspace/BrakeCaseStudy
./scripts/setup-merge-driver.sh
```

This registers the `vitruvius` merge driver in `.git/config`, adds `*.model merge=vitruvius`
to `.gitattributes`, and creates `.vitruvius/merge-driver.properties` with the BrakeCaseStudy
Reaction specifications.

#### Usage

After setup, use Git as normal:

```bash
git checkout main
git merge feature-branch
```

When Git encounters conflicting `.model` files, it invokes the Vitruvius merge driver instead
of its built-in text merge. The driver:

1. Runs the full semantic merge (interleaving mode) using the changelogs from both branches
2. Replays transactions through the Vitruvius change propagation pipeline
3. Writes the merged model files back for Git to commit
4. If blocking conflicts are detected, the merge is aborted with an error message

#### Manual Setup (for other VSUM repositories)

```bash
# 1. Register the merge driver (adjust path to vitruvius-merge.sh)
git config merge.vitruvius.name "Vitruvius semantic merge"
git config merge.vitruvius.driver "/path/to/vitruvius-merge.sh %O %A %B %L %P"

# 2. Tell Git which files to merge with the driver
echo '*.model merge=vitruvius' >> .gitattributes

# 3. List your ChangePropagationSpecification classes
mkdir -p .vitruvius
cat > .vitruvius/merge-driver.properties <<EOF
specifications=your.package.Spec1,your.package.Spec2
EOF
```

#### Limitations

- **Prototype status.** The merge driver works programmatically (verified by JUnit tests) but
  end-to-end testing through `git merge` requires the full Java classpath to be available.
  The wrapper script (`scripts/vitruvius-merge.sh`) builds it from Maven on first run.
- **No interactive conflict resolution.** When blocking conflicts are detected, `git merge`
  is aborted. There is no UI to present semantic conflict details (UUID, feature, values)
  or to let the user choose resolutions interactively. Conflict resolution is currently
  only available programmatically via `ConflictResolutionProvider` in test code.
- **Merge history.** After a successful merge, Git creates a standard merge commit.
  `git blame` attributes all merged model lines to this commit. The semantic changelogs
  in `.vitruvius/semantic-changelogs/` provide richer provenance but are not surfaced by
  standard Git tools.
- **Cold start.** The first invocation per merge bootstraps the JVM, loads EMF metamodels,
  and initializes the VSUM, which takes several seconds.
- **Fixed Reactions.** Both branches must use the same set of Reactions. Co-evolution of
  Reaction specifications across branches is not supported.

### Reproducing the Evaluation

To reproduce the evaluation results reported in the paper:

```bash
# Scenario tests (S1-S9): Vitruvius semantic merge
./mvnw -pl vsum test -Dtest=ThreeModelBranchingMergeTest

# Comparison table: Vitruvius vs EMFCompare (S1-S7)
./mvnw -pl vsum test -Dtest=MergeApproachComparisonTest#generateComparisonTable

# Performance benchmarks (run on a quiet machine for stable results)
./mvnw -pl vsum test -Dtest="MergePerformanceBenchmarkTest#e1_fullSuite" -Dsurefire.useFile=false
./mvnw -pl vsum test -Dtest="MergePerformanceBenchmarkTest#e2_fullSuite" -Dsurefire.useFile=false
```

## RQ2: Parameterized Scenario Generator (TrackB)

The `TrackBEvaluationTest` automatically generates 105 merge scenarios across five experimental families. Each family varies one parameter while holding others fixed, using five deterministic random seeds (42–46) for variance estimation.

### Parameters

| Parameter | Description | Range |
|-----------|-------------|-------|
| `baseComponentCount` | Number of brake components in the base state | 2–10 |
| `commitsPerBranch` | Commits per branch (always equal for A and B) | 1–25 |
| `overlapFraction` | Fraction of base elements both branches can modify | 0.0–1.0 |
| `reactionTriggerFraction` | Fraction of operations targeting Reaction-triggering attributes | 0.2–0.8 |

### Five Experimental Families

| Family | Varied Parameter | Values | Fixed Parameters | Scenarios |
|--------|-----------------|--------|-----------------|-----------|
| F1: History length | commits per branch | {1, 3, 5, 10, 25} | n=5, overlap=0.3, reaction=0.5 | 25 |
| F2: Overlap density | overlap fraction | {0.0, 0.3, 0.6, 1.0} | n=5, k=5, reaction=0.5 | 20 |
| F3: Reaction density | reaction trigger fraction | {0.2, 0.5, 0.8} | n=5, k=5, overlap=0.5 | 15 |
| F4: Base state size | base components | {2, 5, 10} | k=5, overlap=0.3, reaction=0.5 | 15 |
| F5: Interleaving | overlap × reaction | {0.3,0.6,1.0} × {0.5,0.8} | n=5, k=5, bidirectional=true | 30 |

### Operation Distribution

Each commit applies 1–3 randomly selected model operations (uniform: `1 + rng.nextInt(3)`).

**Operation selection per action:**
- **20% probability**: Additive operation (create a new component with a unique branch-local ID). Component type selected uniformly from {BrakeDisk, BrakePad, ABSSensor, BrakeCaliper}. Additions never conflict across branches because each branch assigns distinct IDs.
- **80% probability**: Modify an existing component from the branch's focus set.
  - With probability `reactionTriggerFraction`: select from **Reaction-triggering** operations (attributes that propagate M1→M2 and M1→M3):
    - `CHANGE_DISK_DIAMETER`, `CHANGE_DISK_THICKNESS` (BrakeDisk)
    - `CHANGE_PAD_HEIGHT`, `CHANGE_PAD_WIDTH` (BrakePad)
  - With probability `1 - reactionTriggerFraction`: select from **non-triggering** operations (attributes that propagate M1→M2 only):
    - `CHANGE_DISK_CENTERING_DIAMETER`, `CHANGE_DISK_RIM_HOLE_NUMBER` (BrakeDisk)
    - `CHANGE_CALIPER_PISTON_DIAMETER` (BrakeCaliper)
    - `CHANGE_SENSOR_LENGTH`, `CHANGE_SENSOR_PINS` (ABSSensor)

The target element is selected uniformly from the branch's focus set.

### Overlap Instantiation

Overlap between branches is controlled by selecting overlapping focus sets from the base components:
- `focusSize = ceil(n × (0.5 + overlapFraction / 2.0))`, capped at `n`
- Branch A takes the first `focusSize` elements; Branch B takes the last `focusSize` elements
- The intersection of these ranges determines which elements both branches can modify

Example with n=5 components: overlap=0.0 → 1 shared element; overlap=0.3 → 3 shared; overlap=1.0 → all 5 shared.

### Conflict Count Aggregation

For each scenario, metrics are extracted from `SemanticMergeResult`:
- **Conflict types**: MODIFY_MODIFY (direct), DELETE_MODIFY, MODIFY_DELETE, INTERLEAVING_CONFLICT (cycle in dependency graph)
- **Aggregation**: For each parameter group (e.g., all scenarios with overlap=0.3), conflict counts are averaged across 5 seeds. Standard deviation uses Bessel's correction (`n-1` denominator). Reported as `mean ± stdev`.
- **Blocking total**: Sum of all conflict types for both Vitruvius and EMF Compare.

### Running

```bash
# Run all 105 generated scenarios:
cd /workspace/BrakeCaseStudy && ./mvnw -pl vsum test -Dtest=TrackBEvaluationTest
```

The report is written to `vsum/target/trackb-evaluation-report.md`.

### Source Files

| File | Purpose |
|------|---------|
| `TrackBEvaluationTest.java` | Test class with 5 family configurations and 5 seeds |
| `ScenarioGenerator.java` | Creates Git repos with parameterized branch histories |
| `ScenarioConfig.java` | Configuration record (all parameters) |
| `TrackBEvaluationMetrics.java` | Per-scenario metric extraction |
| `TrackBReportGenerator.java` | Aggregation and markdown report generation |
| `ModelAction.java` | Enum of all model operations with component type mappings |

All source files are in `vsum/src/test/java/tools/vitruv/casestudies/brakesystem/vsum/comparison/`.

Useful Links
------------
Details about the build process and configurations can be found in the readmes of the relevant projects.
* https://github.com/vitruv-tools/Maven-Build-Parent/blob/main/readme.md
* https://github.com/vitruv-tools/EMF-Template/blob/main/readme.md
