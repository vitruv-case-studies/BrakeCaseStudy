package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import java.util.Collection;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formats RQ2 robustness evaluation results as markdown summary tables and CSV.
 */
public class RobustnessReportGenerator {

    private final List<RobustnessEvaluationMetrics> results;

    public RobustnessReportGenerator(Collection<RobustnessEvaluationMetrics> results) {
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

        Map<String, List<RobustnessEvaluationMetrics>> byFamily = results.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().family()));
        for (var entry : byFamily.entrySet()) {
            var metrics = entry.getValue();
            sb.append(String.format("%s,%d,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f%n",
                    entry.getKey(), metrics.size(),
                    avg(metrics, RobustnessEvaluationMetrics::getVitruviusBlockingTotal),
                    stddev(metrics, RobustnessEvaluationMetrics::getVitruviusBlockingTotal),
                    avg(metrics, RobustnessEvaluationMetrics::getEmfCompareConflicts),
                    stddev(metrics, RobustnessEvaluationMetrics::getEmfCompareConflicts),
                    avg(metrics, RobustnessEvaluationMetrics::getConflictReduction),
                    stddev(metrics, RobustnessEvaluationMetrics::getConflictReduction)));
        }

        return sb.toString();
    }

    /**
     * Generates markdown summary tables grouped by family.
     */
    public String markdownSummary() {
        var sb = new StringBuilder();
        sb.append("# RQ2: Robustness of Conflict Reduction\n\n");
        sb.append(String.format("**Total scenarios:** %d%n%n", results.size()));

        // Overall summary
        appendOverallSummary(sb);

        // Consistency verification summary
        appendConsistencySummary(sb);

        // Per-family tables
        Map<String, List<RobustnessEvaluationMetrics>> byFamily = results.stream()
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
        if (byFamily.containsKey("F6-ConseqOverlap")) {
            appendFamily6Table(sb, byFamily.get("F6-ConseqOverlap"));
        }

        return sb.toString();
    }

    private void appendOverallSummary(StringBuilder sb) {
        sb.append("## Overall Summary\n\n");
        sb.append("| Family | Scenarios | Vit. Conflicts (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time ms (avg ± σ) | EMF Time ms (avg ± σ) |\n");
        sb.append("|--------|:---------:|:-----------------------:|:-----------------------:|:-------------------:|:---------------------:|:--------------------:|\n");

        Map<String, List<RobustnessEvaluationMetrics>> byFamily = results.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().family()));

        for (var entry : byFamily.entrySet()) {
            var metrics = entry.getValue();
            sb.append(String.format("| %s | %d | %s | %s | %s | %s | %s |\n",
                    entry.getKey(),
                    metrics.size(),
                    fmtAvgStd(metrics, RobustnessEvaluationMetrics::getVitruviusBlockingTotal),
                    fmtAvgStd(metrics, RobustnessEvaluationMetrics::getEmfCompareConflicts),
                    fmtAvgStd(metrics, RobustnessEvaluationMetrics::getConflictReduction),
                    fmtAvgStd(metrics, m -> (int) m.getVitruviusMergeTimeMs()),
                    fmtAvgStd(metrics, m -> (int) m.getEmfCompareMergeTimeMs())));
        }
        sb.append("\n");
    }

    private void appendConsistencySummary(StringBuilder sb) {
        long totalSuccessful = results.stream().filter(RobustnessEvaluationMetrics::isVitruviusSuccess).count();
        long verified = results.stream().filter(RobustnessEvaluationMetrics::isConsistencyVerified).count();
        long notVerified = totalSuccessful - verified;
        List<RobustnessEvaluationMetrics> failures = results.stream()
                .filter(m -> m.isVitruviusSuccess() && !m.isConsistencyVerified())
                .toList();

        long f6Total = results.stream().filter(m -> m.getConfig().family().startsWith("F6")).count();
        long f6Success = results.stream()
                .filter(m -> m.getConfig().family().startsWith("F6") && m.isVitruviusSuccess()).count();
        long otherSuccess = totalSuccessful - f6Success;

        sb.append("## Post-Merge Consistency Verification\n\n");
        sb.append(String.format("- **Successful merges:** %d / %d (Family 6: %d/%d; Families 1--5: %d/%d)%n",
                totalSuccessful, results.size(),
                f6Success, f6Total,
                otherSuccess, results.size() - f6Total));
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

    private void appendFamily1Table(StringBuilder sb, List<RobustnessEvaluationMetrics> metrics) {
        sb.append("## Family 1: History Length Scaling\n\n");
        sb.append("| Commits/Branch | Vit. Conflicts (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time ms (avg ± σ) | EMF Time ms (avg ± σ) |\n");
        sb.append("|:--------------:|:-----------------------:|:-----------------------:|:-------------------:|:---------------------:|:--------------------:|\n");

        Map<Integer, List<RobustnessEvaluationMetrics>> byK = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().commitsPerBranchA()));

        for (var entry : byK.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, String.valueOf(entry.getKey()), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily2Table(StringBuilder sb, List<RobustnessEvaluationMetrics> metrics) {
        sb.append("## Family 2: Overlap Density\n\n");
        sb.append("| Overlap Fraction | Vit. Conflicts (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time ms (avg ± σ) | EMF Time ms (avg ± σ) |\n");
        sb.append("|:----------------:|:-----------------------:|:-----------------------:|:-------------------:|:---------------------:|:--------------------:|\n");

        Map<String, List<RobustnessEvaluationMetrics>> byOverlap = metrics.stream()
                .collect(Collectors.groupingBy(m -> String.format("%.1f", m.getConfig().overlapFraction())));

        for (var entry : byOverlap.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, entry.getKey(), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily3Table(StringBuilder sb, List<RobustnessEvaluationMetrics> metrics) {
        sb.append("## Family 3: Reaction-Trigger Density\n\n");
        sb.append("| Reaction Fraction | Vit. Conflicts (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time ms (avg ± σ) | EMF Time ms (avg ± σ) |\n");
        sb.append("|:-----------------:|:-----------------------:|:-----------------------:|:-------------------:|:---------------------:|:--------------------:|\n");

        Map<String, List<RobustnessEvaluationMetrics>> byRT = metrics.stream()
                .collect(Collectors.groupingBy(m -> String.format("%.1f", m.getConfig().reactionTriggerFraction())));

        for (var entry : byRT.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, entry.getKey(), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily4Table(StringBuilder sb, List<RobustnessEvaluationMetrics> metrics) {
        sb.append("## Family 4: Base State Size\n\n");
        sb.append("| Components | Vit. Conflicts (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time ms (avg ± σ) | EMF Time ms (avg ± σ) |\n");
        sb.append("|:----------:|:-----------------------:|:-----------------------:|:-------------------:|:---------------------:|:--------------------:|\n");

        Map<Integer, List<RobustnessEvaluationMetrics>> bySize = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().baseComponentCount()));

        for (var entry : bySize.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, String.valueOf(entry.getKey()), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendFamily5Table(StringBuilder sb, List<RobustnessEvaluationMetrics> metrics) {
        sb.append("## Family 5: Bidirectional Merge\n\n");
        sb.append("| Overlap | Reaction | Vit. Conflicts (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time ms (avg ± σ) | EMF Time ms (avg ± σ) |\n");
        sb.append("|:-------:|:--------:|:-----------------------:|:-----------------------:|:-------------------:|:---------------------:|:--------------------:|\n");

        // Group by overlap x reaction
        Map<String, List<RobustnessEvaluationMetrics>> byGroup = metrics.stream()
                .collect(Collectors.groupingBy(m -> String.format("%.1f|%.1f",
                        m.getConfig().overlapFraction(), m.getConfig().reactionTriggerFraction())));

        for (var entry : byGroup.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var group = entry.getValue();
            String[] parts = entry.getKey().split("\\|");
            sb.append(String.format("| %s | %s | %s | %s | %s | %s | %s |\n",
                    parts[0], parts[1],
                    fmtAvgStd(group, RobustnessEvaluationMetrics::getVitruviusBlockingTotal),
                    fmtAvgStd(group, RobustnessEvaluationMetrics::getEmfCompareConflicts),
                    fmtAvgStd(group, RobustnessEvaluationMetrics::getConflictReduction),
                    fmtAvgStd(group, m -> (int) m.getVitruviusMergeTimeMs()),
                    fmtAvgStd(group, m -> (int) m.getEmfCompareMergeTimeMs())));
        }
        sb.append("\n");
    }

    private void appendFamily6Table(StringBuilder sb, List<RobustnessEvaluationMetrics> metrics) {
        sb.append("## Family 6: Consequential Overlap Resolution\n\n");
        sb.append("| Commits/Branch | Vit. Conflicts (avg ± σ) | EMF Conflicts (avg ± σ) | Reduction (avg ± σ) | Vit. Time ms (avg ± σ) | EMF Time ms (avg ± σ) |\n");
        sb.append("|:--------------:|:-----------------------:|:-----------------------:|:-------------------:|:---------------------:|:--------------------:|\n");

        Map<Integer, List<RobustnessEvaluationMetrics>> byK = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getConfig().commitsPerBranchA()));

        for (var entry : byK.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            appendAggRow(sb, String.valueOf(entry.getKey()), entry.getValue());
        }
        sb.append("\n");
    }

    private void appendAggRow(StringBuilder sb, String label, List<RobustnessEvaluationMetrics> group) {
        sb.append(String.format("| %s | %s | %s | %s | %s | %s |\n",
                label,
                fmtAvgStd(group, RobustnessEvaluationMetrics::getVitruviusBlockingTotal),
                fmtAvgStd(group, RobustnessEvaluationMetrics::getEmfCompareConflicts),
                fmtAvgStd(group, RobustnessEvaluationMetrics::getConflictReduction),
                fmtAvgStd(group, m -> (int) m.getVitruviusMergeTimeMs()),
                fmtAvgStd(group, m -> (int) m.getEmfCompareMergeTimeMs())));
    }

    @FunctionalInterface
    private interface IntMetric {
        int get(RobustnessEvaluationMetrics m);
    }

    private static double avg(List<RobustnessEvaluationMetrics> metrics, IntMetric extractor) {
        return metrics.stream().mapToInt(extractor::get).average().orElse(0.0);
    }

    private static double stddev(List<RobustnessEvaluationMetrics> metrics, IntMetric extractor) {
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

    private static String fmtAvgStd(List<RobustnessEvaluationMetrics> metrics, IntMetric extractor) {
        return String.format("%.1f ± %.1f", avg(metrics, extractor), stddev(metrics, extractor));
    }
}
