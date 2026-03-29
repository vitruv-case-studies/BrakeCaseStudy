# Vitruvius Brake Case Study 
- based on the Methodologist Template (see [here](https://github.com/vitruv-tools/Methodologist-Template/tree/main))

## Getting Started

The project comes with a maven wrapper, so you can run it without installing Maven.
To build the project you can run the following command:

```bash
./mvnw clean verify
```

Verify that all tests are passing. The tests are located in the `vsum` folder.
Now you can start to modify the project to your needs.


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
cd /workspace/Vitruv-Change   && ./mvnw clean install -Dmaven.test.skip=true
cd /workspace/Vitruv          && ./mvnw clean install -Dmaven.test.skip=true
cd /workspace/BrakeCaseStudy  && ./mvnw clean install -Dmaven.test.skip=true

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

Useful Links
------------
Details about the build process and configurations can be found in the readmes of the relevant projects.
* https://github.com/vitruv-tools/Maven-Build-Parent/blob/main/readme.md
* https://github.com/vitruv-tools/EMF-Template/blob/main/readme.md
