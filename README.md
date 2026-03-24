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

Useful Links
------------
Details about the build process and configurations can be found in the readmes of the relevant projects.
* https://github.com/vitruv-tools/Maven-Build-Parent/blob/main/readme.md
* https://github.com/vitruv-tools/EMF-Template/blob/main/readme.md



