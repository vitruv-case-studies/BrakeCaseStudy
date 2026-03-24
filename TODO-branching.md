# Branching TODO

## Resolved Questions

### ~~Scenario 4: user(B) vs derived(A) — reapply user(B)?~~ → Resolved via Bidirectional Merge

**Previous concern:** When derived(A) overwrites user(B) during replay A→B, discarding
derived(A) leaves the reaction unexecuted and the model potentially inconsistent.

**Resolution:** Instead of reapplying user(B) after the replay (which risks triggering
additional reactions that could undo A's primary change), we now use a **bidirectional merge**:

1. Try A→B. If indirect conflicts are detected (derived(A) vs user(B)), try B→A.
2. In B→A, user(B)'s changes are replayed onto A's state. Reactions fire naturally
   for B's changes, producing consistent derived state.
3. If B→A has no indirect conflicts → use the reverse result (direction=REVERSED).
4. If both directions have indirect conflicts → report BIDIRECTIONAL_INDIRECT_CONFLICT.

This avoids the concerns about reapplication order and reaction cascades, because the
merge engine always replays through `propagateChange()` in the normal way—it just
selects the direction that avoids indirect conflicts.

**Implementation:**
- `SemanticMergeEngine.mergeBidirectional()` in Vitruv
- `SemanticMergeCommand.executeBidirectional()` entry point
- Test coverage: S8 (reverse resolves S4) and S9 (both directions conflict)
