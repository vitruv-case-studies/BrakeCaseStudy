# Branching TODO

## Open Questions

### Scenario 4: user(B) vs derived(A) — reapply user(B)?

In the current implementation, when derived(A) overwrites user(B) during replay,
we emit a warning and B's intent wins (the merge succeeds but the derived value
is not applied). An alternative approach would be to **reapply user(B)'s change
after the replay** so that:

1. A's transaction replays fully (reactions fire, derived state is generated)
2. B's user change is then reapplied on top, overriding the derived value

This would preserve both: A's primary change propagates correctly through
reactions, and B's explicit user intent is restored afterward.

**Concerns to investigate:**
- Reapplying user(B) after replay may trigger additional reactions that create
  an inconsistent state (e.g., if the CAD→brakesystem reaction fires again with
  B's value, it could undo A's primary change).
- The order of reapplication matters: if multiple derived(A) values conflict with
  multiple user(B) values, the reapplication sequence could produce different
  outcomes.
- Need to define whether reapplication should go through `propagateChange()` or
  be a raw model edit that bypasses reactions.

**Revisit after**: the 7 three-model scenarios are stable and the formalization
is aligned with the implementation behavior.
