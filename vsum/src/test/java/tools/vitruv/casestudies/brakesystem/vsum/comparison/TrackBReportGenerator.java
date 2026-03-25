package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import java.util.Collection;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formats Track B evaluation results as markdown summary tables and CSV.
 */
public class TrackBReportGenerator {

    private final List<TrackBEvaluationMetrics> results;

    public TrackBReportGenerator(Collection<TrackBEvaluationMetrics> results) {
        this.results = List.copyOf(results);
    }

    /**
     * Generates a CSV with one row per scenario and all metrics as columns.
     */
    public String csv() {
        var sb = new StringBuilder();
        sb.append("scenario_id,family,seed,base_components,commits_per_branch,overlap_fraction,"
                + "reaction_trigger_fraction,bidirectional,"
                + "vit_blocking,vit_modify_modify,vit_delete_modify,vit_modify_delete,vit_bidir_indirect,"
                + "vit_warnings,vit_indirect_warnings,vit_user_vs_derived,"
                + "emf_conflicts,"
                + "changes_replayed,merge_direction,vit_success,"
                + "setup_time_ms,vit_merge_time_ms,emf_merge_time_ms,"
                + "conflict_reduction\n");

        for (var m : results) {
            var c = m.getConfig();
            sb.append(String.format("%s,%s,%d,%d,%d,%.2f,%.2f,%s,"
                            + "%d,%d,%d,%d,%d,"
                            + "%d,%d,%d,"
                            + "%d,"
                            + "%d,%s,%s,"
                            + "%d,%d,%d,"
                            + "%d%n",
                    c.id(), c.family(), c.seed(), c.baseComponentCount(), c.commitsPerBranchA(),
                    c.overlapFraction(), c.reactionTriggerFraction(), c.bidirectional(),
                    m.getVitruviusBlockingTotal(), m.getModifyModifyConflicts(),
                    m.getDeleteModifyConflicts(), m.getModifyDeleteConflicts(),
                    m.getBidirectionalIndirectConflicts(),
                    m.getVitruviusWarningsTotal(), m.getIndirectConflictWarnings(),
                    m.getUserVsDerivedWarnings(),
                    m.getEmfCompareConflicts(),
                    m.getTotalChangesReplayed(), m.getMergeDirection(), m.isVitruviusSuccess(),
                    m.getSetupTimeMs(), m.getVitruviusMergeTimeMs(), m.getEmfCompareMergeTimeMs(),
                    m.getConflictReduction()));
        }
        return sb.toString();
    }

    /**
     * Generates markdown summary tables grouped by family.
     */
    public String markdownSummary() {
        var sb = new StringBuilder();
        sb.append("# Track B: Depth Evaluation Results\n\n");
        sb.append(String.format("**Total scenarios:** %d%n%n", results.size()));

        // Overall summary
        appendOverallSummary(sb);

        // Per-family tables
        Map<String, List<TrackBEvaluationMetrics>> byFamily = results.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().family()));

        if (byFamily.containsKey("F1-HistoryLength")) {
            appendFamily1Table(sb, byFamily.get("F1-HistoryLength"));
        }
        if (byFamily.containsKey("F2-OverlapDensity")) {
            appendFamily2Table(sb, byFamily.get("F2-OverlapDensity"));
        }
        if (byFamily.containsKey("F3-ReactionDensity")) {
            appendFamily3Table(sb, byFamily.get("F3-ReactionDensity"));
        }
        if (byFamily.containsKey("F4-BaseSize")) {
            appendFamily4Table(sb, byFamily.get("F4-BaseSize"));
        }
        if (byFamily.containsKey("F5-Bidirectional")) {
            appendFamily5Table(sb, byFamily.get("F5-Bidirectional"));
        }

        return sb.toString();
    }

    private void appendOverallSummary(StringBuilder sb) {
        sb.append("## Overall Summary\n\n");
        sb.append("| Family | Scenarios | Avg Vit. Blocking | Avg Vit. Warnings | Avg EMF Conflicts | Avg Reduction | Avg Vit. Time (ms) |\n");
        sb.append("|--------|:---------:|:-----------------:|:-----------------:|:-----------------:|:-------------:|:------------------:|\n");

        Map<String, List<TrackBEvaluationMetrics>> byFamily = results.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().family()));

        for (var entry : byFamily.entrySet()) {
            var metrics = entry.getValue();
            sb.append(String.format("| %s | %d | %.1f | %.1f | %.1f | %.1f | %.0f |\n",
                    entry.getKey(),
                    metrics.size(),
                    avg(metrics, TrackBEvaluationMetrics::getVitruviusBlockingTotal),
                    avg(metrics, TrackBEvaluationMetrics::getVitruviusWarningsTotal),
                    avg(metrics, TrackBEvaluationMetrics::getEmfCompareConflicts),
                    avg(metrics, TrackBEvaluationMetrics::getConflictReduction),
                    avg(metrics, m -> (int) m.getVitruviusMergeTimeMs())));
        }
        sb.append("\n");
    }

    private void appendFamily1Table(StringBuilder sb, List<TrackBEvaluationMetrics> metrics) {
        sb.append("## Family 1: History Length Scaling\n\n");
        sb.append("| Commits/Branch | Vit. Blocking (avg) | Vit. Warnings (avg) | EMF Conflicts (avg) | Reduction (avg) | Vit. Time (avg ms) |\n");
        sb.append("|:--------------:|:-------------------:|:-------------------:|:-------------------:|:---------------:|:------------------:|\n");

        Map<Integer, List<TrackBEvaluationMetrics>> byK = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().commitsPerBranchA()));

        for (var entry : byK.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, String.valueOf(entry.getKey()), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily2Table(StringBuilder sb, List<TrackBEvaluationMetrics> metrics) {
        sb.append("## Family 2: Overlap Density\n\n");
        sb.append("| Overlap Fraction | Vit. Blocking (avg) | Vit. Warnings (avg) | EMF Conflicts (avg) | Reduction (avg) | Vit. Time (avg ms) |\n");
        sb.append("|:----------------:|:-------------------:|:-------------------:|:-------------------:|:---------------:|:------------------:|\n");

        Map<String, List<TrackBEvaluationMetrics>> byOverlap = metrics.stream()
                .collect(Collectors.groupingBy(m -> String.format("%.1f", m.getConfig().overlapFraction())));

        for (var entry : byOverlap.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, entry.getKey(), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily3Table(StringBuilder sb, List<TrackBEvaluationMetrics> metrics) {
        sb.append("## Family 3: Reaction-Trigger Density\n\n");
        sb.append("| Reaction Fraction | Vit. Blocking (avg) | Vit. Warnings (avg) | EMF Conflicts (avg) | Reduction (avg) | Vit. Time (avg ms) |\n");
        sb.append("|:-----------------:|:-------------------:|:-------------------:|:-------------------:|:---------------:|:------------------:|\n");

        Map<String, List<TrackBEvaluationMetrics>> byRT = metrics.stream()
                .collect(Collectors.groupingBy(m -> String.format("%.1f", m.getConfig().reactionTriggerFraction())));

        for (var entry : byRT.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, entry.getKey(), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily4Table(StringBuilder sb, List<TrackBEvaluationMetrics> metrics) {
        sb.append("## Family 4: Base State Size\n\n");
        sb.append("| Components | Vit. Blocking (avg) | Vit. Warnings (avg) | EMF Conflicts (avg) | Reduction (avg) | Vit. Time (avg ms) |\n");
        sb.append("|:----------:|:-------------------:|:-------------------:|:-------------------:|:---------------:|:------------------:|\n");

        Map<Integer, List<TrackBEvaluationMetrics>> bySize = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().baseComponentCount()));

        for (var entry : bySize.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, String.valueOf(entry.getKey()), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily5Table(StringBuilder sb, List<TrackBEvaluationMetrics> metrics) {
        sb.append("## Family 5: Bidirectional Merge\n\n");
        sb.append("| Overlap | Reaction | Vit. Blocking (avg) | Vit. Warnings (avg) | Direction (FORWARD/REVERSED) | Vit. Time (avg ms) |\n");
        sb.append("|:-------:|:--------:|:-------------------:|:-------------------:|:----------------------------:|:------------------:|\n");

        // Group by overlap x reaction
        Map<String, List<TrackBEvaluationMetrics>> byGroup = metrics.stream()
                .collect(Collectors.groupingBy(m -> String.format("%.1f|%.1f",
                        m.getConfig().overlapFraction(), m.getConfig().reactionTriggerFraction())));

        for (var entry : byGroup.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var group = entry.getValue();
            String[] parts = entry.getKey().split("\\|");
            long fwd = group.stream().filter(m -> "FORWARD".equals(m.getMergeDirection())).count();
            long rev = group.stream().filter(m -> "REVERSED".equals(m.getMergeDirection())).count();
            sb.append(String.format("| %s | %s | %.1f | %.1f | %d / %d | %.0f |\n",
                    parts[0], parts[1],
                    avg(group, TrackBEvaluationMetrics::getVitruviusBlockingTotal),
                    avg(group, TrackBEvaluationMetrics::getVitruviusWarningsTotal),
                    fwd, rev,
                    avg(group, m -> (int) m.getVitruviusMergeTimeMs())));
        }
        sb.append("\n");
    }

    private void appendAggRow(StringBuilder sb, String label, List<TrackBEvaluationMetrics> group) {
        sb.append(String.format("| %s | %.1f | %.1f | %.1f | %.1f | %.0f |\n",
                label,
                avg(group, TrackBEvaluationMetrics::getVitruviusBlockingTotal),
                avg(group, TrackBEvaluationMetrics::getVitruviusWarningsTotal),
                avg(group, TrackBEvaluationMetrics::getEmfCompareConflicts),
                avg(group, TrackBEvaluationMetrics::getConflictReduction),
                avg(group, m -> (int) m.getVitruviusMergeTimeMs())));
    }

    @FunctionalInterface
    private interface IntMetric {
        int get(TrackBEvaluationMetrics m);
    }

    private static double avg(List<TrackBEvaluationMetrics> metrics, IntMetric extractor) {
        return metrics.stream().mapToInt(extractor::get).average().orElse(0.0);
    }
}
