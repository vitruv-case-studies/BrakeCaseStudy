# Status

## Badge Target

**Functional** — The artifact can be used to reproduce the main evaluation
results presented in the paper.

## Evidence

- All 9 merge scenarios (S1–S9) are implemented as automated JUnit tests
  that assert the exact conflict/warning counts reported in the paper.
- 105 automatically generated scenarios (RQ4, Track B) across 5 experimental
  families with 5 deterministic seeds each.
- Performance benchmarks (RQ3) with 5 repetitions + warmup, reporting medians.
- A second case study (MobSTr, automated driving) with 7 scenarios.
- A one-command reproduction script (`reproduce.sh`) runs the comparison
  tables (both case studies), and optionally the Track B evaluation
  (`--all`) and performance benchmarks (`--performance`).
- A Dockerfile is provided for containerized execution without local setup.
- The evaluation is deterministic: scenarios use fixed model states,
  deterministic seeds, and identical Git repository inputs for both approaches.
