# Status

## Badge Target

**Functional** — The artifact can be used to reproduce the main evaluation
results presented in the paper.

## Evidence

- All 9 merge scenarios (S1–S9) are implemented as automated JUnit tests
  that assert the exact conflict/warning counts reported in the paper.
- A one-command reproduction script (`reproduce.sh`) runs the comparison
  table (Vitruvius vs EMFCompare) and optionally the performance benchmarks.
- A Dockerfile is provided for containerized execution without local setup.
- The evaluation is deterministic: scenarios use fixed model states and
  identical Git repository inputs for both approaches.
