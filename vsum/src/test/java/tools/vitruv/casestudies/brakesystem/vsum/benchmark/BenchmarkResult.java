package tools.vitruv.casestudies.brakesystem.vsum.benchmark;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures timing and metric data for a single benchmark merge run.
 */
public class BenchmarkResult {

    private final String configLabel;
    private final int numComponents;
    private final int numTransactions;
    private final double overlapFraction;
    private final boolean highReactionDensity;

    // Timing (nanoseconds)
    private long setupTimeNanos;
    private long totalMergeTimeNanos;
    private long gitStateExtractionNanos;
    private long dtoLoadingNanos;
    private long conflictDetectionNanos;
    private long replayTimeNanos;

    // Merge outcome
    private int directConflicts;
    private int warnings;
    private int changesReplayed;
    private int transactionsReplayed;
    private boolean mergeSucceeded;
    private String mergeDirection; // FORWARD, REVERSED, or null

    // Memory
    private long peakMemoryBytes;

    public BenchmarkResult(String configLabel, int numComponents, int numTransactions,
                           double overlapFraction, boolean highReactionDensity) {
        this.configLabel = configLabel;
        this.numComponents = numComponents;
        this.numTransactions = numTransactions;
        this.overlapFraction = overlapFraction;
        this.highReactionDensity = highReactionDensity;
    }

    // Setters (builder-style)
    public BenchmarkResult setupTime(long nanos) { this.setupTimeNanos = nanos; return this; }
    public BenchmarkResult totalMergeTime(long nanos) { this.totalMergeTimeNanos = nanos; return this; }
    public BenchmarkResult gitStateExtractionTime(long nanos) { this.gitStateExtractionNanos = nanos; return this; }
    public BenchmarkResult dtoLoadingTime(long nanos) { this.dtoLoadingNanos = nanos; return this; }
    public BenchmarkResult conflictDetectionTime(long nanos) { this.conflictDetectionNanos = nanos; return this; }
    public BenchmarkResult replayTime(long nanos) { this.replayTimeNanos = nanos; return this; }
    public BenchmarkResult directConflicts(int c) { this.directConflicts = c; return this; }
    public BenchmarkResult warnings(int w) { this.warnings = w; return this; }
    public BenchmarkResult changesReplayed(int c) { this.changesReplayed = c; return this; }
    public BenchmarkResult transactionsReplayed(int t) { this.transactionsReplayed = t; return this; }
    public BenchmarkResult mergeSucceeded(boolean s) { this.mergeSucceeded = s; return this; }
    public BenchmarkResult mergeDirection(String d) { this.mergeDirection = d; return this; }
    public BenchmarkResult peakMemory(long bytes) { this.peakMemoryBytes = bytes; return this; }

    // Getters
    public String getConfigLabel() { return configLabel; }
    public int getNumComponents() { return numComponents; }
    public int getNumTransactions() { return numTransactions; }
    public double getOverlapFraction() { return overlapFraction; }
    public boolean isHighReactionDensity() { return highReactionDensity; }
    public long getSetupTimeNanos() { return setupTimeNanos; }
    public long getTotalMergeTimeNanos() { return totalMergeTimeNanos; }
    public long getGitStateExtractionNanos() { return gitStateExtractionNanos; }
    public long getDtoLoadingNanos() { return dtoLoadingNanos; }
    public long getConflictDetectionNanos() { return conflictDetectionNanos; }
    public long getReplayTimeNanos() { return replayTimeNanos; }
    public int getDirectConflicts() { return directConflicts; }
    public int getWarnings() { return warnings; }
    public int getChangesReplayed() { return changesReplayed; }
    public int getTransactionsReplayed() { return transactionsReplayed; }
    public boolean isMergeSucceeded() { return mergeSucceeded; }
    public String getMergeDirection() { return mergeDirection; }
    public long getPeakMemoryBytes() { return peakMemoryBytes; }

    public double getTotalMergeTimeMs() { return totalMergeTimeNanos / 1_000_000.0; }
    public double getSetupTimeMs() { return setupTimeNanos / 1_000_000.0; }
    public double getReplayTimeMs() { return replayTimeNanos / 1_000_000.0; }
    public double getPeakMemoryMB() { return peakMemoryBytes / (1024.0 * 1024.0); }

    /**
     * Returns a CSV header line.
     */
    public static String csvHeader() {
        return "config,numComponents,numTransactions,overlapFraction,reactionDensity,"
                + "setupTimeMs,totalMergeTimeMs,replayTimeMs,"
                + "directConflicts,warnings,changesReplayed,transactionsReplayed,"
                + "mergeSucceeded,mergeDirection,peakMemoryMB";
    }

    /**
     * Returns a CSV data line.
     */
    public String toCsvLine() {
        return String.format("%s,%d,%d,%.2f,%s,%.1f,%.1f,%.1f,%d,%d,%d,%d,%s,%s,%.1f",
                configLabel, numComponents, numTransactions, overlapFraction,
                highReactionDensity ? "high" : "low",
                getSetupTimeMs(), getTotalMergeTimeMs(), getReplayTimeMs(),
                directConflicts, warnings, changesReplayed, transactionsReplayed,
                mergeSucceeded, mergeDirection != null ? mergeDirection : "FORWARD",
                getPeakMemoryMB());
    }

    /**
     * Returns a formatted summary line for console output.
     */
    public String toSummaryLine() {
        return String.format("%-25s | %4d comp | %3d txn | merge: %7.1f ms | replay: %7.1f ms | "
                        + "conflicts: %d | warnings: %d | mem: %.0f MB",
                configLabel, numComponents, numTransactions,
                getTotalMergeTimeMs(), getReplayTimeMs(),
                directConflicts, warnings, getPeakMemoryMB());
    }

    /**
     * Returns all metrics as an ordered map (for flexible formatting).
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("config", configLabel);
        map.put("numComponents", numComponents);
        map.put("numTransactions", numTransactions);
        map.put("overlapFraction", overlapFraction);
        map.put("reactionDensity", highReactionDensity ? "high" : "low");
        map.put("setupTimeMs", getSetupTimeMs());
        map.put("totalMergeTimeMs", getTotalMergeTimeMs());
        map.put("replayTimeMs", getReplayTimeMs());
        map.put("directConflicts", directConflicts);
        map.put("warnings", warnings);
        map.put("changesReplayed", changesReplayed);
        map.put("transactionsReplayed", transactionsReplayed);
        map.put("mergeSucceeded", mergeSucceeded);
        map.put("mergeDirection", mergeDirection);
        map.put("peakMemoryMB", getPeakMemoryMB());
        return map;
    }
}
