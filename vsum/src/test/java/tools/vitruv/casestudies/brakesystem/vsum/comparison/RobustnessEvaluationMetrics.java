package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import java.util.Map;
import java.util.stream.Collectors;

import tools.vitruv.framework.vsum.branch.merge.MergeConflict;
import tools.vitruv.framework.vsum.branch.merge.MergeConflict.ConflictType;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeResult;
import tools.vitruv.merge.comparison.MergeEvaluationResult;

/**
 * Extended metrics DTO for Track B evaluation with per-type conflict/warning breakdown,
 * timing, and EMFCompare comparison data.
 */
public class RobustnessEvaluationMetrics {

    private final ScenarioConfig config;

    // Conflict breakdown by type
    private final int modifyModifyConflicts;
    private final int deleteModifyConflicts;
    private final int modifyDeleteConflicts;
    private final int bidirectionalIndirectConflicts;

    // Warning breakdown by type
    private final int indirectConflictWarnings;
    private final int userVsDerivedWarnings;

    // Totals
    private final int vitruviusBlockingTotal;
    private final int vitruviusWarningsTotal;

    // EMFCompare
    private final int emfCompareConflicts;
    private final Map<String, Integer> emfComparePerModel;

    // Merge info
    private final int totalChangesReplayed;
    private final String mergeDirection;
    private final boolean vitruviusSuccess;
    private final String vitruviusError;

    // Consistency verification
    private final boolean consistencyVerified;
    private final String consistencyError;

    // Timing (milliseconds)
    private final long setupTimeMs;
    private final long vitruviusMergeTimeMs;
    private final long emfCompareMergeTimeMs;

    private RobustnessEvaluationMetrics(Builder b) {
        this.config = b.config;
        this.modifyModifyConflicts = b.modifyModifyConflicts;
        this.deleteModifyConflicts = b.deleteModifyConflicts;
        this.modifyDeleteConflicts = b.modifyDeleteConflicts;
        this.bidirectionalIndirectConflicts = b.bidirectionalIndirectConflicts;
        this.indirectConflictWarnings = b.indirectConflictWarnings;
        this.userVsDerivedWarnings = b.userVsDerivedWarnings;
        this.vitruviusBlockingTotal = b.modifyModifyConflicts + b.deleteModifyConflicts
                + b.modifyDeleteConflicts + b.bidirectionalIndirectConflicts;
        this.vitruviusWarningsTotal = b.indirectConflictWarnings + b.userVsDerivedWarnings;
        this.emfCompareConflicts = b.emfCompareConflicts;
        this.emfComparePerModel = b.emfComparePerModel;
        this.totalChangesReplayed = b.totalChangesReplayed;
        this.mergeDirection = b.mergeDirection;
        this.vitruviusSuccess = b.vitruviusSuccess;
        this.vitruviusError = b.vitruviusError;
        this.consistencyVerified = b.consistencyVerified;
        this.consistencyError = b.consistencyError;
        this.setupTimeMs = b.setupTimeMs;
        this.vitruviusMergeTimeMs = b.vitruviusMergeTimeMs;
        this.emfCompareMergeTimeMs = b.emfCompareMergeTimeMs;
    }

    /**
     * Builds metrics from a SemanticMergeResult and EMFCompare result.
     */
    public static RobustnessEvaluationMetrics from(ScenarioConfig config,
            SemanticMergeResult vitResult, MergeEvaluationResult emfResult,
            long setupMs, long vitMs, long emfMs) {
        var b = new Builder(config);
        b.setupTimeMs = setupMs;
        b.vitruviusMergeTimeMs = vitMs;
        b.emfCompareMergeTimeMs = emfMs;

        if (vitResult != null) {
            b.vitruviusSuccess = vitResult.isSuccess();
            b.totalChangesReplayed = vitResult.getAppliedChanges() != null
                    ? vitResult.getAppliedChanges().size() : 0;
            b.mergeDirection = vitResult.getMergeDirection() != null
                    ? vitResult.getMergeDirection().name() : "N/A";

            // Breakdown conflicts by type
            if (vitResult.getConflicts() != null) {
                var conflictsByType = vitResult.getConflicts().stream()
                        .collect(Collectors.groupingBy(MergeConflict::getType, Collectors.counting()));
                b.modifyModifyConflicts = conflictsByType.getOrDefault(ConflictType.MODIFY_MODIFY, 0L).intValue();
                b.deleteModifyConflicts = conflictsByType.getOrDefault(ConflictType.DELETE_MODIFY, 0L).intValue();
                b.modifyDeleteConflicts = conflictsByType.getOrDefault(ConflictType.MODIFY_DELETE, 0L).intValue();
                b.bidirectionalIndirectConflicts = conflictsByType
                        .getOrDefault(ConflictType.BIDIRECTIONAL_INDIRECT_CONFLICT, 0L).intValue();
            }

            // Breakdown warnings by type
            if (vitResult.getWarnings() != null) {
                var warningsByType = vitResult.getWarnings().stream()
                        .collect(Collectors.groupingBy(MergeConflict::getType, Collectors.counting()));
                b.indirectConflictWarnings = warningsByType.getOrDefault(ConflictType.INDIRECT_CONFLICT, 0L).intValue();
                b.userVsDerivedWarnings = warningsByType
                        .getOrDefault(ConflictType.USER_VS_DERIVED_WARNING, 0L).intValue();
            }
        }

        if (emfResult != null) {
            b.emfCompareConflicts = emfResult.getDirectConflictCount();
            b.emfComparePerModel = emfResult.getConflictsPerModel();
        }

        return b.build();
    }

    /**
     * Builds metrics for a failed Vitruvius merge.
     */
    public static RobustnessEvaluationMetrics forError(ScenarioConfig config,
            String error, MergeEvaluationResult emfResult,
            long setupMs, long vitMs, long emfMs) {
        var b = new Builder(config);
        b.setupTimeMs = setupMs;
        b.vitruviusMergeTimeMs = vitMs;
        b.emfCompareMergeTimeMs = emfMs;
        b.vitruviusSuccess = false;
        b.vitruviusError = error;
        if (emfResult != null) {
            b.emfCompareConflicts = emfResult.getDirectConflictCount();
            b.emfComparePerModel = emfResult.getConflictsPerModel();
        }
        return b.build();
    }

    // ── Getters ──

    public ScenarioConfig getConfig() { return config; }
    public int getModifyModifyConflicts() { return modifyModifyConflicts; }
    public int getDeleteModifyConflicts() { return deleteModifyConflicts; }
    public int getModifyDeleteConflicts() { return modifyDeleteConflicts; }
    public int getBidirectionalIndirectConflicts() { return bidirectionalIndirectConflicts; }
    public int getIndirectConflictWarnings() { return indirectConflictWarnings; }
    public int getUserVsDerivedWarnings() { return userVsDerivedWarnings; }
    public int getVitruviusBlockingTotal() { return vitruviusBlockingTotal; }
    public int getVitruviusWarningsTotal() { return vitruviusWarningsTotal; }
    public int getEmfCompareConflicts() { return emfCompareConflicts; }
    public Map<String, Integer> getEmfComparePerModel() { return emfComparePerModel; }
    public int getTotalChangesReplayed() { return totalChangesReplayed; }
    public String getMergeDirection() { return mergeDirection; }
    public boolean isVitruviusSuccess() { return vitruviusSuccess; }
    public String getVitruviusError() { return vitruviusError; }
    public boolean isConsistencyVerified() { return consistencyVerified; }
    public String getConsistencyError() { return consistencyError; }
    public long getSetupTimeMs() { return setupTimeMs; }
    public long getVitruviusMergeTimeMs() { return vitruviusMergeTimeMs; }
    public long getEmfCompareMergeTimeMs() { return emfCompareMergeTimeMs; }

    public int getConflictReduction() {
        return emfCompareConflicts - vitruviusBlockingTotal;
    }

    /**
     * Returns a copy of this metrics with consistency verification results set.
     */
    public RobustnessEvaluationMetrics withConsistencyResult(boolean verified, String error) {
        var b = new Builder(this.config);
        b.modifyModifyConflicts = this.modifyModifyConflicts;
        b.deleteModifyConflicts = this.deleteModifyConflicts;
        b.modifyDeleteConflicts = this.modifyDeleteConflicts;
        b.bidirectionalIndirectConflicts = this.bidirectionalIndirectConflicts;
        b.indirectConflictWarnings = this.indirectConflictWarnings;
        b.userVsDerivedWarnings = this.userVsDerivedWarnings;
        b.emfCompareConflicts = this.emfCompareConflicts;
        b.emfComparePerModel = this.emfComparePerModel;
        b.totalChangesReplayed = this.totalChangesReplayed;
        b.mergeDirection = this.mergeDirection;
        b.vitruviusSuccess = this.vitruviusSuccess;
        b.vitruviusError = this.vitruviusError;
        b.consistencyVerified = verified;
        b.consistencyError = error;
        b.setupTimeMs = this.setupTimeMs;
        b.vitruviusMergeTimeMs = this.vitruviusMergeTimeMs;
        b.emfCompareMergeTimeMs = this.emfCompareMergeTimeMs;
        return b.build();
    }

    private static class Builder {
        final ScenarioConfig config;
        int modifyModifyConflicts, deleteModifyConflicts, modifyDeleteConflicts, bidirectionalIndirectConflicts;
        int indirectConflictWarnings, userVsDerivedWarnings;
        int emfCompareConflicts;
        Map<String, Integer> emfComparePerModel = Map.of();
        int totalChangesReplayed;
        String mergeDirection = "N/A";
        boolean vitruviusSuccess;
        String vitruviusError;
        boolean consistencyVerified;
        String consistencyError;
        long setupTimeMs, vitruviusMergeTimeMs, emfCompareMergeTimeMs;

        Builder(ScenarioConfig config) { this.config = config; }
        RobustnessEvaluationMetrics build() { return new RobustnessEvaluationMetrics(this); }
    }
}
