package tools.vitruv.casestudies.brakesystem.vsum.comparison;

/**
 * Immutable configuration record describing a generated merge scenario for RQ2 robustness evaluation.
 * Each config fully determines a reproducible scenario via its seed.
 */
public record ScenarioConfig(
        String id,
        long seed,
        int baseComponentCount,
        int commitsPerBranchA,
        int commitsPerBranchB,
        double overlapFraction,
        double reactionTriggerFraction,
        boolean bidirectional,
        String family
) {

    /**
     * Factory method with symmetric branch lengths.
     */
    public static ScenarioConfig of(long seed, int baseComponents, int commits,
            double overlap, double reactionTrigger, boolean bidirectional, String family) {
        String id = String.format("%s-k%d-o%d-r%d-c%d-s%d%s",
                family,
                commits,
                (int) (overlap * 100),
                (int) (reactionTrigger * 100),
                baseComponents,
                seed,
                bidirectional ? "-bi" : "");
        return new ScenarioConfig(id, seed, baseComponents, commits, commits,
                overlap, reactionTrigger, bidirectional, family);
    }

    @Override
    public String toString() {
        return id;
    }
}
