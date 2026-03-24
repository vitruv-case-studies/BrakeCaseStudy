package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified result for comparing merge approaches (Vitruvius semantic merge vs EMFCompare baseline).
 */
public class MergeEvaluationResult {

    private final String scenarioId;
    private final String scenarioDescription;
    private final String approach;
    private final int directConflictCount;
    private final int warningCount;
    private final List<String> conflictDetails;
    private final List<String> warningDetails;
    private final Map<String, Integer> conflictsPerModel;
    private final boolean mergeSucceeded;

    private MergeEvaluationResult(Builder builder) {
        this.scenarioId = builder.scenarioId;
        this.scenarioDescription = builder.scenarioDescription;
        this.approach = builder.approach;
        this.directConflictCount = builder.directConflictCount;
        this.warningCount = builder.warningCount;
        this.conflictDetails = List.copyOf(builder.conflictDetails);
        this.warningDetails = List.copyOf(builder.warningDetails);
        this.conflictsPerModel = Map.copyOf(builder.conflictsPerModel);
        this.mergeSucceeded = builder.mergeSucceeded;
    }

    public String getScenarioId() { return scenarioId; }
    public String getScenarioDescription() { return scenarioDescription; }
    public String getApproach() { return approach; }
    public int getDirectConflictCount() { return directConflictCount; }
    public int getWarningCount() { return warningCount; }
    public List<String> getConflictDetails() { return conflictDetails; }
    public List<String> getWarningDetails() { return warningDetails; }
    public Map<String, Integer> getConflictsPerModel() { return conflictsPerModel; }
    public boolean isMergeSucceeded() { return mergeSucceeded; }

    public static Builder builder(String scenarioId, String approach) {
        return new Builder(scenarioId, approach);
    }

    public static class Builder {
        private final String scenarioId;
        private final String approach;
        private String scenarioDescription = "";
        private int directConflictCount;
        private int warningCount;
        private final List<String> conflictDetails = new ArrayList<>();
        private final List<String> warningDetails = new ArrayList<>();
        private final Map<String, Integer> conflictsPerModel = new LinkedHashMap<>();
        private boolean mergeSucceeded = true;

        private Builder(String scenarioId, String approach) {
            this.scenarioId = scenarioId;
            this.approach = approach;
        }

        public Builder scenarioDescription(String desc) { this.scenarioDescription = desc; return this; }
        public Builder directConflictCount(int c) { this.directConflictCount = c; return this; }
        public Builder warningCount(int w) { this.warningCount = w; return this; }
        public Builder addConflictDetail(String d) { this.conflictDetails.add(d); return this; }
        public Builder addWarningDetail(String d) { this.warningDetails.add(d); return this; }
        public Builder conflictsForModel(String model, int count) { this.conflictsPerModel.put(model, count); return this; }
        public Builder mergeSucceeded(boolean s) { this.mergeSucceeded = s; return this; }

        public MergeEvaluationResult build() {
            return new MergeEvaluationResult(this);
        }
    }
}
