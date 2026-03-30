package tools.vitruv.casestudies.brakesystem.vsum.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.vitruv.casestudies.brakesystem.vsum.benchmark.ScalableModelGenerator.BenchmarkConfig;
import tools.vitruv.casestudies.brakesystem.vsum.benchmark.ScalableModelGenerator.GeneratedScenario;
import tools.vitruv.casestudies.brakesystem.vsum.comparison.ThreeModelScenarioSetup;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.vsum.branch.merge.ConflictResolutionProvider;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeCommand;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeResult;

/**
 * Performance benchmark for the semantic three-way merge engine.
 *
 * <p>Measures merge time, replay time, and memory consumption across
 * varying model sizes, branch history lengths, conflict densities,
 * and reaction densities. Results are output as CSV and markdown
 * for inclusion in the research paper.
 *
 * <p>Each experiment uses controlled configurations with deterministic
 * seeds for reproducibility. A warmup run is included and discarded.
 *
 * <h3>Experiments:</h3>
 * <ul>
 *   <li>E1: Model size scaling (numComponents varies, fixed transactions)</li>
 *   <li>E2: History length scaling (numTransactions varies, fixed components)</li>
 *   <li>E3: Conflict density (overlapFraction varies)</li>
 *   <li>E4: Reaction density (low vs high)</li>
 *   <li>E5: Bidirectional overhead (directed vs bidirectional)</li>
 * </ul>
 */
public class BrakeScalabilityBenchmarkTest {

    private static final int REPETITIONS = 5;
    private static final int WARMUP_RUNS = 1;
    private static final long BASE_SEED = 42L;

    private final ScalableModelGenerator generator = new ScalableModelGenerator();

    @BeforeAll
    static void registerFactories() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("*", new XMIResourceFactoryImpl());
    }

    // ═══════════════════════════════════════════════════════════════════
    // E1: Model Size Scaling
    // ═══════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "E1: Model size = {0} components")
    @ValueSource(ints = {10, 50, 100, 250, 500, 750, 1000})
    @DisplayName("E1: Model size scaling")
    void e1_modelSizeScaling(int numComponents, @TempDir Path tempDir) throws Exception {
        var config = new BenchmarkConfig(numComponents, 5, 0.0, true, BASE_SEED);
        var result = runBenchmark(config, tempDir);

        System.out.println(result.toSummaryLine());
        assertTrue(result.isMergeSucceeded(), "Merge should succeed for disjoint branches");
    }

    @Test
    @DisplayName("E1: Full model size scaling suite")
    void e1_fullSuite(@TempDir Path tempDir) throws Exception {
        int[] sizes = {10, 50, 100, 250, 500, 750, 1000};
        List<BenchmarkResult> results = new ArrayList<>();

        for (int size : sizes) {
            List<BenchmarkResult> runs = new ArrayList<>();
            for (int rep = 0; rep < WARMUP_RUNS + REPETITIONS; rep++) {
                Path runDir = Files.createDirectories(tempDir.resolve("e1_" + size + "_r" + rep));
                var config = new BenchmarkConfig(size, 5, 0.0, true, BASE_SEED + rep);
                var result = runBenchmark(config, runDir);
                if (rep >= WARMUP_RUNS) {
                    runs.add(result);
                }
            }
            results.add(aggregateResults(runs, "E1_C" + size));
        }

        printResults("E1: Model Size Scaling", results);
        saveResults("e1-model-size", results, tempDir);
    }

    @Test
    @DisplayName("E1: Quick smoke test (10, 50 components only)")
    void e1_smokeTest(@TempDir Path tempDir) throws Exception {
        int[] sizes = {10, 50};
        List<BenchmarkResult> results = new ArrayList<>();

        for (int size : sizes) {
            List<BenchmarkResult> runs = new ArrayList<>();
            for (int rep = 0; rep < WARMUP_RUNS + REPETITIONS; rep++) {
                Path runDir = Files.createDirectories(tempDir.resolve("e1s_" + size + "_r" + rep));
                var config = new BenchmarkConfig(size, 5, 0.0, true, BASE_SEED + rep);
                var result = runBenchmark(config, runDir);
                if (rep >= WARMUP_RUNS) {
                    runs.add(result);
                }
            }
            results.add(aggregateResults(runs, "E1_C" + size));
        }

        printResults("E1: Smoke Test", results);
        saveResults("e1-smoke-test", results, tempDir);
    }

    // ═══════════════════════════════════════════════════════════════════
    // E2: History Length Scaling
    // ═══════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "E2: History length = {0} transactions")
    @ValueSource(ints = {1, 5, 10, 25, 50})
    @DisplayName("E2: History length scaling")
    void e2_historyLengthScaling(int numTransactions, @TempDir Path tempDir) throws Exception {
        var config = new BenchmarkConfig(50, numTransactions, 0.0, true, BASE_SEED);
        var result = runBenchmark(config, tempDir);

        System.out.println(result.toSummaryLine());
        assertTrue(result.isMergeSucceeded(), "Merge should succeed for disjoint branches");
    }

    @Test
    @DisplayName("E2: Full history length suite")
    void e2_fullSuite(@TempDir Path tempDir) throws Exception {
        int[] lengths = {1, 5, 10, 25, 50};
        List<BenchmarkResult> results = new ArrayList<>();

        for (int length : lengths) {
            List<BenchmarkResult> runs = new ArrayList<>();
            for (int rep = 0; rep < WARMUP_RUNS + REPETITIONS; rep++) {
                Path runDir = Files.createDirectories(tempDir.resolve("e2_" + length + "_r" + rep));
                var config = new BenchmarkConfig(50, length, 0.0, true, BASE_SEED + rep);
                var result = runBenchmark(config, runDir);
                if (rep >= WARMUP_RUNS) {
                    runs.add(result);
                }
            }
            results.add(aggregateResults(runs, "E2_T" + length));
        }

        printResults("E2: History Length Scaling", results);
        saveResults("e2-history-length", results, tempDir);
    }

    // ═══════════════════════════════════════════════════════════════════
    // E3: Conflict Density
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E3: Conflict density suite")
    void e3_conflictDensity(@TempDir Path tempDir) throws Exception {
        double[] overlaps = {0.0, 0.1, 0.25, 0.5};
        List<BenchmarkResult> results = new ArrayList<>();

        for (double overlap : overlaps) {
            List<BenchmarkResult> runs = new ArrayList<>();
            for (int rep = 0; rep < WARMUP_RUNS + REPETITIONS; rep++) {
                Path runDir = Files.createDirectories(
                        tempDir.resolve("e3_" + (int)(overlap * 100) + "_r" + rep));
                var config = new BenchmarkConfig(50, 10, overlap, true, BASE_SEED + rep);
                var result = runBenchmark(config, runDir);
                if (rep >= WARMUP_RUNS) {
                    runs.add(result);
                }
            }
            results.add(aggregateResults(runs,
                    String.format("E3_O%.0f%%", overlap * 100)));
        }

        printResults("E3: Conflict Density", results);
        saveResults("e3-conflict-density", results, tempDir);
    }

    // ═══════════════════════════════════════════════════════════════════
    // E4: Reaction Density
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E4: Reaction density (low vs high)")
    void e4_reactionDensity(@TempDir Path tempDir) throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();

        for (boolean high : new boolean[]{false, true}) {
            List<BenchmarkResult> runs = new ArrayList<>();
            for (int rep = 0; rep < WARMUP_RUNS + REPETITIONS; rep++) {
                Path runDir = Files.createDirectories(
                        tempDir.resolve("e4_" + (high ? "high" : "low") + "_r" + rep));
                var config = new BenchmarkConfig(100, 10, 0.0, high, BASE_SEED + rep);
                var result = runBenchmark(config, runDir);
                if (rep >= WARMUP_RUNS) {
                    runs.add(result);
                }
            }
            results.add(aggregateResults(runs, "E4_" + (high ? "high" : "low")));
        }

        printResults("E4: Reaction Density", results);
        saveResults("e4-reaction-density", results, tempDir);
    }

    // ═══════════════════════════════════════════════════════════════════
    // E5: Bidirectional Overhead
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E5: Bidirectional merge overhead")
    void e5_bidirectionalOverhead(@TempDir Path tempDir) throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();

        // Directed merge
        {
            List<BenchmarkResult> runs = new ArrayList<>();
            for (int rep = 0; rep < WARMUP_RUNS + REPETITIONS; rep++) {
                Path runDir = Files.createDirectories(tempDir.resolve("e5_directed_r" + rep));
                var config = new BenchmarkConfig(50, 10, 0.0, true, BASE_SEED + rep);
                var result = runBenchmark(config, runDir);
                if (rep >= WARMUP_RUNS) {
                    runs.add(result);
                }
            }
            results.add(aggregateResults(runs, "E5_directed"));
        }

        // Bidirectional merge
        {
            List<BenchmarkResult> runs = new ArrayList<>();
            for (int rep = 0; rep < WARMUP_RUNS + REPETITIONS; rep++) {
                Path runDir = Files.createDirectories(tempDir.resolve("e5_bidir_r" + rep));
                var config = new BenchmarkConfig(50, 10, 0.0, true, BASE_SEED + rep);
                var result = runBidirectionalBenchmark(config, runDir);
                if (rep >= WARMUP_RUNS) {
                    runs.add(result);
                }
            }
            results.add(aggregateResults(runs, "E5_bidirectional"));
        }

        printResults("E5: Bidirectional Overhead", results);
        saveResults("e5-bidirectional", results, tempDir);
    }

    // ═══════════════════════════════════════════════════════════════════
    // E6: Hand-crafted RQ1 scenarios (interleaving, conflicts, cycles)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E6: RQ1 scenario timings (interleaving, conflicts, graph construction)")
    void e6_rq1ScenarioTimings(@TempDir Path tempDir) throws Exception {
        int[] scenarios = {1, 4, 6, 7, 13};
        List<BenchmarkResult> results = new ArrayList<>();
        var setup = new ThreeModelScenarioSetup();

        for (int scenarioNum : scenarios) {
            // First, run once without resolution to determine conflict count
            int conflictCount = 0;
            {
                Path probeDir = Files.createDirectories(
                        tempDir.resolve("e6_s" + scenarioNum + "_probe"));
                var prepared = setup.setupScenario(scenarioNum, probeDir);
                var ip = new TestUserInteraction.ResultProvider(new TestUserInteraction());
                try {
                    var probeResult = new SemanticMergeCommand().executeWithInterleaving(
                            prepared.repoPath(), prepared.sourceBranch(),
                            prepared.targetBranch(),
                            ThreeModelScenarioSetup.allCPS(), ip, null);
                    conflictCount = probeResult.getConflicts().size();
                } catch (Exception e) {
                    // Direct conflict scenarios throw when no resolution is provided.
                    // Retry with resolution to get the conflict list.
                    try {
                        var probeResult = new SemanticMergeCommand().executeWithInterleaving(
                                prepared.repoPath(), prepared.sourceBranch(),
                                prepared.targetBranch(),
                                ThreeModelScenarioSetup.allCPS(), ip,
                                ConflictResolutionProvider.chooseAllTheirs());
                        conflictCount = probeResult.getConflicts().size();
                    } catch (Exception e2) {
                        conflictCount = -1; // unknown
                    }
                }
            }

            // Now run timed repetitions (with resolution so all scenarios complete)
            List<BenchmarkResult> runs = new ArrayList<>();
            final int detectedConflicts = conflictCount;
            for (int rep = 0; rep < WARMUP_RUNS + REPETITIONS; rep++) {
                Path runDir = Files.createDirectories(
                        tempDir.resolve("e6_s" + scenarioNum + "_r" + rep));
                var prepared = setup.setupScenario(scenarioNum, runDir);
                var interactionProvider = new TestUserInteraction.ResultProvider(
                        new TestUserInteraction());

                long mergeStart = System.nanoTime();
                SemanticMergeResult mergeResult;
                boolean succeeded;
                try {
                    mergeResult = new SemanticMergeCommand().executeWithInterleaving(
                            prepared.repoPath(), prepared.sourceBranch(),
                            prepared.targetBranch(),
                            ThreeModelScenarioSetup.allCPS(), interactionProvider,
                            ConflictResolutionProvider.chooseAllTheirs());
                    succeeded = mergeResult.isSuccess();
                } catch (Exception e) {
                    mergeResult = null;
                    succeeded = false;
                }
                long mergeEnd = System.nanoTime();

                String label = ThreeModelScenarioSetup.scenarioLabel(scenarioNum);
                var benchmarkResult = new BenchmarkResult(label, 0, 0, 0.0, false)
                        .totalMergeTime(mergeEnd - mergeStart)
                        .mergeSucceeded(succeeded)
                        .directConflicts(detectedConflicts);

                if (rep >= WARMUP_RUNS) {
                    runs.add(benchmarkResult);
                }
            }
            results.add(aggregateResults(runs,
                    ThreeModelScenarioSetup.scenarioLabel(scenarioNum)));
        }

        printResults("E6: RQ1 Scenario Timings", results);
        saveResults("e6-rq1-scenario-timings", results, tempDir);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Full benchmark suite
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Run all benchmark experiments and generate report")
    void fullBenchmarkSuite(@TempDir Path tempDir) throws Exception {
        List<BenchmarkResult> allResults = new ArrayList<>();

        System.out.println("=== Running Full Benchmark Suite ===\n");

        // E1: Model size
        System.out.println("--- E1: Model Size Scaling ---");
        for (int size : new int[]{10, 50, 100, 250, 500, 750, 1000}) {
            var config = new BenchmarkConfig(size, 5, 0.0, true, BASE_SEED);
            Path runDir = Files.createDirectories(tempDir.resolve("full_e1_" + size));
            var result = runBenchmark(config, runDir);
            allResults.add(result);
            System.out.println(result.toSummaryLine());
        }

        // E2: History length
        System.out.println("\n--- E2: History Length Scaling ---");
        for (int txn : new int[]{1, 5, 10, 25, 50}) {
            var config = new BenchmarkConfig(50, txn, 0.0, true, BASE_SEED);
            Path runDir = Files.createDirectories(tempDir.resolve("full_e2_" + txn));
            var result = runBenchmark(config, runDir);
            allResults.add(result);
            System.out.println(result.toSummaryLine());
        }

        // E3: Conflict density
        System.out.println("\n--- E3: Conflict Density ---");
        for (double overlap : new double[]{0.0, 0.25, 0.5}) {
            var config = new BenchmarkConfig(50, 10, overlap, true, BASE_SEED);
            Path runDir = Files.createDirectories(
                    tempDir.resolve("full_e3_" + (int)(overlap * 100)));
            var result = runBenchmark(config, runDir);
            allResults.add(result);
            System.out.println(result.toSummaryLine());
        }

        // Save all results
        saveResults("full-benchmark", allResults, tempDir);
        System.out.println("\n=== Benchmark Complete ===");
        System.out.println("Results will be written to: "
                + Path.of(System.getProperty("evaluation.outputDir", "../output"))
                        .resolve("brake-RQ3-scalability-*/").toAbsolutePath().normalize());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Core benchmark execution
    // ═══════════════════════════════════════════════════════════════════

    private BenchmarkResult runBenchmark(BenchmarkConfig config, Path tempDir) throws Exception {
        // Generate scenario
        GeneratedScenario scenario = generator.generate(config, tempDir);

        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        // Measure memory before
        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Run merge with timing
        long mergeStart = System.nanoTime();

        SemanticMergeResult mergeResult;
        try {
            mergeResult = new SemanticMergeCommand().execute(
                    scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                    ThreeModelScenarioSetup.allCPS(), interactionProvider,
                    ConflictResolutionProvider.chooseAllTheirs());
        } catch (Exception e) {
            // Merge failed — record the failure
            long mergeEnd = System.nanoTime();
            long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            return new BenchmarkResult(config.label(), config.numComponents(),
                    config.numTransactionsPerBranch(), config.overlapFraction(),
                    config.highReactionDensity())
                    .setupTime(scenario.setupTimeNanos())
                    .totalMergeTime(mergeEnd - mergeStart)
                    .mergeSucceeded(false)
                    .peakMemory(Math.max(0, memAfter - memBefore));
        }

        long mergeEnd = System.nanoTime();
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        var benchmarkResult = new BenchmarkResult(config.label(), config.numComponents(),
                config.numTransactionsPerBranch(), config.overlapFraction(),
                config.highReactionDensity())
                .setupTime(scenario.setupTimeNanos())
                .totalMergeTime(mergeEnd - mergeStart)
                .directConflicts(mergeResult.getConflicts().size())
                .warnings(mergeResult.getWarnings().size())
                .changesReplayed(mergeResult.getAppliedChanges().size())
                .transactionsReplayed(config.numTransactionsPerBranch())
                .mergeSucceeded(mergeResult.isSuccess())
                .mergeDirection("FORWARD")
                .peakMemory(Math.max(0, memAfter - memBefore));

        applyTimingStats(benchmarkResult, mergeResult);
        return benchmarkResult;
    }

    private BenchmarkResult runBidirectionalBenchmark(BenchmarkConfig config, Path tempDir)
            throws Exception {
        GeneratedScenario scenario = generator.generate(config, tempDir);
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long mergeStart = System.nanoTime();

        SemanticMergeResult mergeResult = new SemanticMergeCommand().executeBidirectional(
                scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                ThreeModelScenarioSetup.allCPS(), interactionProvider,
                ConflictResolutionProvider.chooseAllTheirs());

        long mergeEnd = System.nanoTime();
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        String direction = mergeResult.getMergeDirection() != null
                ? mergeResult.getMergeDirection().name() : "FORWARD";

        var benchmarkResult = new BenchmarkResult(config.label(), config.numComponents(),
                config.numTransactionsPerBranch(), config.overlapFraction(),
                config.highReactionDensity())
                .setupTime(scenario.setupTimeNanos())
                .totalMergeTime(mergeEnd - mergeStart)
                .directConflicts(mergeResult.getConflicts().size())
                .warnings(mergeResult.getWarnings().size())
                .changesReplayed(mergeResult.getAppliedChanges().size())
                .transactionsReplayed(config.numTransactionsPerBranch())
                .mergeSucceeded(mergeResult.isSuccess())
                .mergeDirection(direction)
                .peakMemory(Math.max(0, memAfter - memBefore));

        applyTimingStats(benchmarkResult, mergeResult);
        return benchmarkResult;
    }

    /**
     * Extracts per-phase timing stats from the merge result and applies them to the benchmark result.
     */
    private void applyTimingStats(BenchmarkResult benchmark, SemanticMergeResult mergeResult) {
        var timing = mergeResult.getTimingStats();
        if (timing != null) {
            benchmark.gitStateExtractionTime(timing.getGitStateExtractionNanos());
            benchmark.dtoLoadingTime(timing.getDtoLoadingNanos());
            benchmark.conflictDetectionTime(timing.getConflictDetectionNanos());
            benchmark.replayTime(timing.getReplayNanos());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Result aggregation and output
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Aggregates multiple runs into a single result with median timing and mean ± stddev.
     */
    private BenchmarkResult aggregateResults(List<BenchmarkResult> runs, String label) {
        if (runs.isEmpty()) throw new IllegalArgumentException("No runs to aggregate");

        // Use median for timing values
        List<Long> mergeTimes = runs.stream()
                .map(BenchmarkResult::getTotalMergeTimeNanos).sorted().toList();
        List<Long> replayTimes = runs.stream()
                .map(BenchmarkResult::getReplayTimeNanos).sorted().toList();
        List<Long> gitExtractTimes = runs.stream()
                .map(BenchmarkResult::getGitStateExtractionNanos).sorted().toList();
        List<Long> dtoLoadTimes = runs.stream()
                .map(BenchmarkResult::getDtoLoadingNanos).sorted().toList();
        List<Long> conflictDetectTimes = runs.stream()
                .map(BenchmarkResult::getConflictDetectionNanos).sorted().toList();
        List<Long> peakMems = runs.stream()
                .map(BenchmarkResult::getPeakMemoryBytes).sorted().toList();

        var first = runs.get(0);
        int medianIdx = runs.size() / 2;

        // Compute mean and stddev for merge and replay times
        double meanMerge = runs.stream().mapToDouble(BenchmarkResult::getTotalMergeTimeMs).average().orElse(0);
        double meanReplay = runs.stream().mapToDouble(BenchmarkResult::getReplayTimeMs).average().orElse(0);
        double stddevMerge = stddev(runs.stream().mapToDouble(BenchmarkResult::getTotalMergeTimeMs).toArray());
        double stddevReplay = stddev(runs.stream().mapToDouble(BenchmarkResult::getReplayTimeMs).toArray());

        return new BenchmarkResult(label, first.getNumComponents(), first.getNumTransactions(),
                first.getOverlapFraction(), first.isHighReactionDensity())
                .setupTime(runs.stream().mapToLong(BenchmarkResult::getSetupTimeNanos)
                        .sorted().skip(medianIdx).findFirst().orElse(0))
                .totalMergeTime(mergeTimes.get(medianIdx))
                .replayTime(replayTimes.get(medianIdx))
                .gitStateExtractionTime(gitExtractTimes.get(medianIdx))
                .dtoLoadingTime(dtoLoadTimes.get(medianIdx))
                .conflictDetectionTime(conflictDetectTimes.get(medianIdx))
                .directConflicts(first.getDirectConflicts())
                .warnings(first.getWarnings())
                .changesReplayed(first.getChangesReplayed())
                .transactionsReplayed(first.getTransactionsReplayed())
                .mergeSucceeded(first.isMergeSucceeded())
                .mergeDirection(first.getMergeDirection())
                .peakMemory(peakMems.get(medianIdx))
                .stats(meanMerge, stddevMerge, meanReplay, stddevReplay, runs.size());
    }

    private static double stddev(double[] values) {
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;
        double sumSqDiff = 0;
        for (double v : values) sumSqDiff += (v - mean) * (v - mean);
        return Math.sqrt(sumSqDiff / values.length);
    }

    private void printResults(String title, List<BenchmarkResult> results) {
        System.out.println("\n=== " + title + " ===");
        System.out.println(String.format("%-25s | %9s | %7s | %14s | %14s | %9s | %8s | %8s",
                "Config", "Components", "Txns", "Merge Time", "Replay Time",
                "Conflicts", "Warnings", "Mem MB"));
        System.out.println("-".repeat(120));
        for (var r : results) {
            System.out.println(r.toSummaryLine());
        }
    }

    private void saveResults(String experimentName, List<BenchmarkResult> results, Path baseDir)
            throws IOException {
        String timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path outputBase = Path.of(System.getProperty("evaluation.outputDir", "../output"));
        Path outputDir = outputBase.resolve("brake-RQ3-scalability-" + timestamp);
        Files.createDirectories(outputDir);

        // CSV
        Path csvFile = outputDir.resolve(experimentName + ".csv");
        StringBuilder csv = new StringBuilder();
        csv.append(BenchmarkResult.csvHeader()).append("\n");
        for (var r : results) {
            csv.append(r.toCsvLine()).append("\n");
        }
        Files.writeString(csvFile, csv.toString());

        // Markdown table
        Path mdFile = outputDir.resolve(experimentName + ".md");
        StringBuilder md = new StringBuilder();
        md.append("# ").append(experimentName).append("\n\n");
        boolean hasStats = results.stream().anyMatch(BenchmarkResult::hasStats);
        if (hasStats) {
            md.append("| Config | Components | Transactions | Merge (median ms) | Merge (mean ± σ ms) | ")
                    .append("Replay (median ms) | Replay (mean ± σ ms) | n | Conflicts | Warnings | Memory (MB) |\n");
            md.append("|--------|-----------|-------------|:-----------------:|:-------------------:|")
                    .append(":-----------------:|:--------------------:|:-:|-----------|----------|------------|\n");
            for (var r : results) {
                md.append(String.format("| %s | %d | %d | %.1f | %.1f ± %.1f | %.1f | %.1f ± %.1f | %d | %d | %d | %.1f |\n",
                        r.getConfigLabel(), r.getNumComponents(), r.getNumTransactions(),
                        r.getTotalMergeTimeMs(), r.getMeanMergeTimeMs(), r.getStddevMergeTimeMs(),
                        r.getReplayTimeMs(), r.getMeanReplayTimeMs(), r.getStddevReplayTimeMs(),
                        r.getSampleCount(),
                        r.getDirectConflicts(), r.getWarnings(), r.getPeakMemoryMB()));
            }
        } else {
            md.append("| Config | Components | Transactions | Merge Time (ms) | Replay Time (ms) | ")
                    .append("Conflicts | Warnings | Memory (MB) |\n");
            md.append("|--------|-----------|-------------|----------------|-----------------|")
                    .append("-----------|----------|------------|\n");
            for (var r : results) {
                md.append(String.format("| %s | %d | %d | %.1f | %.1f | %d | %d | %.1f |\n",
                        r.getConfigLabel(), r.getNumComponents(), r.getNumTransactions(),
                        r.getTotalMergeTimeMs(), r.getReplayTimeMs(),
                        r.getDirectConflicts(), r.getWarnings(), r.getPeakMemoryMB()));
            }
        }
        Files.writeString(mdFile, md.toString());

        System.out.println("Output written to " + outputDir.toAbsolutePath().normalize());
    }
}
