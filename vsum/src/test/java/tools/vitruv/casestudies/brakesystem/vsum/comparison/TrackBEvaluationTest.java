package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.vsum.branch.merge.ConflictResolutionProvider;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeCommand;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeResult;

/**
 * Track B evaluation: runs automatically generated merge scenarios across
 * five families varying history length, overlap density, reaction-trigger density,
 * base state size, and bidirectional merge behavior.
 *
 * <p>Produces markdown summary tables and CSV for plotting in {@code target/}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TrackBEvaluationTest {

    private static final Map<String, TrackBEvaluationMetrics> allResults = new ConcurrentHashMap<>();
    private static final long[] SEEDS = {42L, 43L, 44L};

    private final ScenarioGenerator generator = new ScenarioGenerator();
    private final EMFCompareThreeWayMerge emfCompareMerge = new EMFCompareThreeWayMerge();

    @BeforeAll
    static void registerFactories() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("*", new XMIResourceFactoryImpl());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Parameterized scenario evaluation
    // ═══════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarioConfigs")
    @Order(1)
    void evaluateScenario(ScenarioConfig config, @TempDir Path tempDir) throws Exception {
        long setupMs = 0, vitMs = 0, emfMs = 0;
        SemanticMergeResult vitResult = null;
        MergeEvaluationResult emfResult = null;
        String vitError = null;

        try {
            // 1. Generate scenario for Vitruvius (timed)
            Path vitDir = Files.createDirectories(tempDir.resolve("vitruvius"));
            long t0 = System.nanoTime();
            var scenario = generator.generate(config, vitDir);
            setupMs = (System.nanoTime() - t0) / 1_000_000;

            // 2. Run Vitruvius merge (timed)
            long t1 = System.nanoTime();
            try {
                var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());
                if (config.bidirectional()) {
                    vitResult = new SemanticMergeCommand().executeBidirectional(
                            scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                            ThreeModelScenarioSetup.allCPS(), interactionProvider,
                            ConflictResolutionProvider.chooseAllTheirs());
                } else {
                    try {
                        vitResult = new SemanticMergeCommand().execute(
                                scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                                ThreeModelScenarioSetup.allCPS(), interactionProvider);
                    } catch (Exception e) {
                        // Retry with conflict resolution to get conflict info
                        vitResult = new SemanticMergeCommand().execute(
                                scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                                ThreeModelScenarioSetup.allCPS(), interactionProvider,
                                ConflictResolutionProvider.chooseAllTheirs());
                    }
                }
            } catch (Exception e) {
                vitError = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            vitMs = (System.nanoTime() - t1) / 1_000_000;

            // 3. Run EMFCompare on a fresh copy (timed)
            Path emfDir = Files.createDirectories(tempDir.resolve("emfcompare"));
            var emfScenario = generator.generate(config, emfDir);
            long t2 = System.nanoTime();
            try {
                emfResult = emfCompareMerge.evaluate(
                        emfScenario.repoPath(), emfScenario.sourceBranch(), emfScenario.targetBranch(),
                        config.id());
            } catch (Exception e) {
                emfResult = MergeEvaluationResult.builder(config.id(), "EMFCompare")
                        .mergeSucceeded(false)
                        .build();
            }
            emfMs = (System.nanoTime() - t2) / 1_000_000;

        } catch (Exception e) {
            // Scenario generation itself failed
            vitError = "SETUP: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        // 4. Collect metrics (always, even on failure)
        TrackBEvaluationMetrics metrics;
        if (vitError != null) {
            metrics = TrackBEvaluationMetrics.forError(config, vitError, emfResult, setupMs, vitMs, emfMs);
        } else {
            metrics = TrackBEvaluationMetrics.from(config, vitResult, emfResult, setupMs, vitMs, emfMs);
        }
        allResults.put(config.id(), metrics);

        // Log per-scenario result
        System.out.printf("[%s] Vit: %d blocking, %d warnings | EMF: %d conflicts | "
                        + "setup=%dms, vit=%dms, emf=%dms%s%n",
                config.id(),
                metrics.getVitruviusBlockingTotal(),
                metrics.getVitruviusWarningsTotal(),
                metrics.getEmfCompareConflicts(),
                setupMs, vitMs, emfMs,
                vitError != null ? " | ERROR: " + vitError : "");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Summary report generation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Order(Integer.MAX_VALUE)
    @DisplayName("Generate Track B summary report")
    void generateReport() throws Exception {
        if (allResults.isEmpty()) {
            System.out.println("No results to report (run evaluateScenario tests first).");
            return;
        }

        var report = new TrackBReportGenerator(allResults.values());
        String markdown = report.markdownSummary();
        String csv = report.csv();

        Path outputDir = Path.of("target");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("trackb-summary.md"), markdown);
        Files.writeString(outputDir.resolve("trackb-evaluation.csv"), csv);

        System.out.println("\n" + markdown);
        System.out.println("\nCSV written to target/trackb-evaluation.csv");
        System.out.println("Summary written to target/trackb-summary.md");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario configuration generation (5 families, 63 total)
    // ═══════════════════════════════════════════════════════════════════

    static Stream<ScenarioConfig> scenarioConfigs() {
        List<ScenarioConfig> configs = new ArrayList<>();
        configs.addAll(family1_historyLength());
        configs.addAll(family2_overlapDensity());
        configs.addAll(family3_reactionTriggerDensity());
        configs.addAll(family4_baseStateSize());
        configs.addAll(family5_bidirectional());
        return configs.stream();
    }

    /**
     * Family 1: History Length Scaling (15 scenarios).
     * How does merge time and conflict count scale with commit count?
     */
    static List<ScenarioConfig> family1_historyLength() {
        List<ScenarioConfig> configs = new ArrayList<>();
        for (int k : new int[]{1, 3, 5, 10, 25}) {
            for (long seed : SEEDS) {
                configs.add(ScenarioConfig.of(seed, 5, k, 0.3, 0.5, false, "F1-HistoryLength"));
            }
        }
        return configs;
    }

    /**
     * Family 2: Overlap Density (12 scenarios).
     * How does conflict rate change as branches touch more of the same elements?
     */
    static List<ScenarioConfig> family2_overlapDensity() {
        List<ScenarioConfig> configs = new ArrayList<>();
        for (double overlap : new double[]{0.0, 0.3, 0.6, 1.0}) {
            for (long seed : SEEDS) {
                configs.add(ScenarioConfig.of(seed, 5, 5, overlap, 0.5, false, "F2-OverlapDensity"));
            }
        }
        return configs;
    }

    /**
     * Family 3: Reaction-Trigger Density (9 scenarios).
     * Does higher reaction density increase Vitruvius's advantage?
     */
    static List<ScenarioConfig> family3_reactionTriggerDensity() {
        List<ScenarioConfig> configs = new ArrayList<>();
        for (double rt : new double[]{0.2, 0.5, 0.8}) {
            for (long seed : SEEDS) {
                configs.add(ScenarioConfig.of(seed, 5, 5, 0.5, rt, false, "F3-ReactionDensity"));
            }
        }
        return configs;
    }

    /**
     * Family 4: Base State Size (9 scenarios).
     * How does component count affect merge behavior?
     */
    static List<ScenarioConfig> family4_baseStateSize() {
        List<ScenarioConfig> configs = new ArrayList<>();
        for (int c : new int[]{2, 5, 10}) {
            for (long seed : SEEDS) {
                configs.add(ScenarioConfig.of(seed, c, 5, 0.3, 0.5, false, "F4-BaseSize"));
            }
        }
        return configs;
    }

    /**
     * Family 5: Bidirectional Merge (18 scenarios).
     * How often does bidirectional fallback help?
     */
    static List<ScenarioConfig> family5_bidirectional() {
        List<ScenarioConfig> configs = new ArrayList<>();
        for (double overlap : new double[]{0.3, 0.6, 1.0}) {
            for (double rt : new double[]{0.5, 0.8}) {
                for (long seed : SEEDS) {
                    configs.add(ScenarioConfig.of(seed, 5, 5, overlap, rt, true, "F5-Bidirectional"));
                }
            }
        }
        return configs;
    }
}
