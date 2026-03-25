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
                + "changes_replayed,merge_direction,vit_success,consistency_verified,"
                + "setup_time_ms,vit_merge_time_ms,emf_merge_time_ms,"
                + "conflict_reduction\n");

        for (var m : results) {
            var c = m.getConfig();
            sb.append(String.format("%s,%s,%d,%d,%d,%.2f,%.2f,%s,"
                            + "%d,%d,%d,%d,%d,"
                            + "%d,%d,%d,"
                            + "%d,"
                            + "%d,%s,%s,%s,"
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
                    m.isConsistencyVerified(),
                    m.getSetupTimeMs(), m.getVitruviusMergeTimeMs(), m.getEmfCompareMergeTimeMs(),
                    m.getConflictReduction()));
        }

        // Aggregate summary section with stddev
        sb.append("\n# Aggregate summary (per family)\n");
        sb.append("family,scenarios,vit_blocking_avg,vit_blocking_stddev,"
                + "emf_conflicts_avg,emf_conflicts_stddev,"
                + "conflict_reduction_avg,conflict_reduction_stddev\n");

        Map<String, List<TrackBEvaluationMetrics>> byFamily = results.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().family()));
        for (var entry : byFamily.entrySet()) {
            var metrics = entry.getValue();
            sb.append(String.format("%s,%d,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f%n",
                    entry.getKey(), metrics.size(),
                    avg(metrics, TrackBEvaluationMetrics::getVitruviusBlockingTotal),
                    stddev(metrics, TrackBEvaluationMetrics::getVitruviusBlockingTotal),
                    avg(metrics, TrackBEvaluationMetrics::getEmfCompareConflicts),
                    stddev(metrics, TrackBEvaluationMetrics::getEmfCompareConflicts),
                    avg(metrics, TrackBEvaluationMetrics::getConflictReduction),
                    stddev(metrics, TrackBEvaluationMetrics::getConflictReduction)));
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

        // Consistency verification summary
        appendConsistencySummary(sb);

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
        sb.append("| Family | Scenarios | Vit. Blocking (avg ± σ) | Vit. Warnings (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Avg Vit. Time (ms) |\n");
        sb.append("|--------|:---------:|:-----------------------:|:-----------------------:|:-----------------------:|:-------------------:|:------------------:|\n");

        Map<String, List<TrackBEvaluationMetrics>> byFamily = results.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().family()));

        for (var entry : byFamily.entrySet()) {
            var metrics = entry.getValue();
            sb.append(String.format("| %s | %d | %s | %s | %s | %s | %.0f |\n",
                    entry.getKey(),
                    metrics.size(),
                    fmtAvgStd(metrics, TrackBEvaluationMetrics::getVitruviusBlockingTotal),
                    fmtAvgStd(metrics, TrackBEvaluationMetrics::getVitruviusWarningsTotal),
                    fmtAvgStd(metrics, TrackBEvaluationMetrics::getEmfCompareConflicts),
                    fmtAvgStd(metrics, TrackBEvaluationMetrics::getConflictReduction),
                    avg(metrics, m -> (int) m.getVitruviusMergeTimeMs())));
        }
        sb.append("\n");
    }

    private void appendConsistencySummary(StringBuilder sb) {
        long totalSuccessful = results.stream().filter(TrackBEvaluationMetrics::isVitruviusSuccess).count();
        long verified = results.stream().filter(TrackBEvaluationMetrics::isConsistencyVerified).count();
        long notVerified = totalSuccessful - verified;
        List<TrackBEvaluationMetrics> failures = results.stream()
                .filter(m -> m.isVitruviusSuccess() && !m.isConsistencyVerified())
                .toList();

        sb.append("## Post-Merge Consistency Verification\n\n");
        sb.append(String.format("- **Successful merges:** %d / %d%n", totalSuccessful, results.size()));
        sb.append(String.format("- **Consistency verified:** %d / %d%n", verified, totalSuccessful));
        if (failures.isEmpty()) {
            sb.append(String.format("- **Result:** All %d successful merges produced consistent three-model states%n",
                    verified));
        } else {
            sb.append(String.format("- **Failures:** %d scenarios failed consistency check:%n", failures.size()));
            for (var f : failures) {
                sb.append(String.format("  - %s: %s%n", f.getConfig().id(),
                        f.getConsistencyError() != null ? f.getConsistencyError() : "unknown error"));
            }
        }
        sb.append("\n");
    }

    private void appendFamily1Table(StringBuilder sb, List<TrackBEvaluationMetrics> metrics) {
        sb.append("## Family 1: History Length Scaling\n\n");
        sb.append("| Commits/Branch | Vit. Blocking (avg ± σ) | Vit. Warnings (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time (avg ms) |\n");
        sb.append("|:--------------:|:-----------------------:|:-----------------------:|:-----------------------:|:-------------------:|:------------------:|\n");

        Map<Integer, List<TrackBEvaluationMetrics>> byK = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().commitsPerBranchA()));

        for (var entry : byK.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, String.valueOf(entry.getKey()), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily2Table(StringBuilder sb, List<TrackBEvaluationMetrics> metrics) {
        sb.append("## Family 2: Overlap Density\n\n");
        sb.append("| Overlap Fraction | Vit. Blocking (avg ± σ) | Vit. Warnings (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time (avg ms) |\n");
        sb.append("|:----------------:|:-----------------------:|:-----------------------:|:-----------------------:|:-------------------:|:------------------:|\n");

        Map<String, List<TrackBEvaluationMetrics>> byOverlap = metrics.stream()
                .collect(Collectors.groupingBy(m -> String.format("%.1f", m.getConfig().overlapFraction())));

        for (var entry : byOverlap.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, entry.getKey(), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily3Table(StringBuilder sb, List<TrackBEvaluationMetrics> metrics) {
        sb.append("## Family 3: Reaction-Trigger Density\n\n");
        sb.append("| Reaction Fraction | Vit. Blocking (avg ± σ) | Vit. Warnings (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time (avg ms) |\n");
        sb.append("|:-----------------:|:-----------------------:|:-----------------------:|:-----------------------:|:-------------------:|:------------------:|\n");

        Map<String, List<TrackBEvaluationMetrics>> byRT = metrics.stream()
                .collect(Collectors.groupingBy(m -> String.format("%.1f", m.getConfig().reactionTriggerFraction())));

        for (var entry : byRT.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, entry.getKey(), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily4Table(StringBuilder sb, List<TrackBEvaluationMetrics> metrics) {
        sb.append("## Family 4: Base State Size\n\n");
        sb.append("| Components | Vit. Blocking (avg ± σ) | Vit. Warnings (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time (avg ms) |\n");
        sb.append("|:----------:|:-----------------------:|:-----------------------:|:-----------------------:|:-------------------:|:------------------:|\n");

        Map<Integer, List<TrackBEvaluationMetrics>> bySize = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().baseComponentCount()));

        for (var entry : bySize.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, String.valueOf(entry.getKey()), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily5Table(StringBuilder sb, List<TrackBEvaluationMetrics> metrics) {
        sb.append("## Family 5: Bidirectional Merge\n\n");
        sb.append("| Overlap | Reaction | Vit. Blocking (avg ± σ) | Vit. Warnings (avg ± σ) | Direction (FORWARD/REVERSED) | Vit. Time (avg ms) |\n");
        sb.append("|:-------:|:--------:|:-----------------------:|:-----------------------:|:----------------------------:|:------------------:|\n");

        // Group by overlap x reaction
        Map<String, List<TrackBEvaluationMetrics>> byGroup = metrics.stream()
                .collect(Collectors.groupingBy(m -> String.format("%.1f|%.1f",
                        m.getConfig().overlapFraction(), m.getConfig().reactionTriggerFraction())));

        for (var entry : byGroup.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var group = entry.getValue();
            String[] parts = entry.getKey().split("\\|");
            long fwd = group.stream().filter(m -> "FORWARD".equals(m.getMergeDirection())).count();
            long rev = group.stream().filter(m -> "REVERSED".equals(m.getMergeDirection())).count();
            sb.append(String.format("| %s | %s | %s | %s | %d / %d | %.0f |\n",
                    parts[0], parts[1],
                    fmtAvgStd(group, TrackBEvaluationMetrics::getVitruviusBlockingTotal),
                    fmtAvgStd(group, TrackBEvaluationMetrics::getVitruviusWarningsTotal),
                    fwd, rev,
                    avg(group, m -> (int) m.getVitruviusMergeTimeMs())));
        }
        sb.append("\n");
    }

    private void appendAggRow(StringBuilder sb, String label, List<TrackBEvaluationMetrics> group) {
        sb.append(String.format("| %s | %s | %s | %s | %s | %.0f |\n",
                label,
                fmtAvgStd(group, TrackBEvaluationMetrics::getVitruviusBlockingTotal),
                fmtAvgStd(group, TrackBEvaluationMetrics::getVitruviusWarningsTotal),
                fmtAvgStd(group, TrackBEvaluationMetrics::getEmfCompareConflicts),
                fmtAvgStd(group, TrackBEvaluationMetrics::getConflictReduction),
                avg(group, m -> (int) m.getVitruviusMergeTimeMs())));
    }

    @FunctionalInterface
    private interface IntMetric {
        int get(TrackBEvaluationMetrics m);
    }

    private static double avg(List<TrackBEvaluationMetrics> metrics, IntMetric extractor) {
        return metrics.stream().mapToInt(extractor::get).average().orElse(0.0);
    }

    private static double stddev(List<TrackBEvaluationMetrics> metrics, IntMetric extractor) {
        if (metrics.size() < 2) return 0.0;
        double mean = avg(metrics, extractor);
        double sumSq = metrics.stream()
                .mapToDouble(m -> {
                    double v = extractor.get(m) - mean;
                    return v * v;
                })
                .sum();
        return Math.sqrt(sumSq / (metrics.size() - 1));
    }

    private static String fmtAvgStd(List<TrackBEvaluationMetrics> metrics, IntMetric extractor) {
        return String.format("%.1f ± %.1f", avg(metrics, extractor), stddev(metrics, extractor));
    }
}
