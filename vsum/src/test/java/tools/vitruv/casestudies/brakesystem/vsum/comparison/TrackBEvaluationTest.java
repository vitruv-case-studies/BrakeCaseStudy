package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
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

import brakesystem.Brakesystem;
import brakesystem.BrakesystemPackage;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.CadPackage;
import safety.SafetyAssessment;
import safety.SafetyPackage;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.branch.merge.ConflictResolutionProvider;
import tools.vitruv.framework.vsum.branch.merge.GitStateLoader;
import tools.vitruv.framework.vsum.branch.merge.MergeTracer;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeCommand;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeResult;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import tools.vitruv.merge.comparison.EMFCompareThreeWayMerge;
import tools.vitruv.merge.comparison.MergeEvaluationResult;

/**
 * Track B evaluation: runs automatically generated merge scenarios across
 * five families varying history length, overlap density, reaction-trigger density,
 * base state size, and bidirectional merge behavior.
 *
 * <p>Produces markdown summary tables and CSV for plotting in {@code target/}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TrackBEvaluationTest {

    private static final List<String> MODEL_FILES = List.of(
            "brakesystem.model", "example.cad", "safety.safety");
    private static final Map<String, TrackBEvaluationMetrics> allResults = new ConcurrentHashMap<>();
    private static final long[] SEEDS = {42L, 43L, 44L, 45L, 46L};

    private final ScenarioGenerator generator = new ScenarioGenerator();
    private final EMFCompareThreeWayMerge emfCompareMerge = new EMFCompareThreeWayMerge(MODEL_FILES);

    @BeforeAll
    static void registerFactories() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("*", new XMIResourceFactoryImpl());
        EPackage.Registry.INSTANCE.put(BrakesystemPackage.eNS_URI, BrakesystemPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(CadPackage.eNS_URI, CadPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(SafetyPackage.eNS_URI, SafetyPackage.eINSTANCE);
        MergeTracer.setOutputDirectory(Path.of("merge-traces"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Parameterized scenario evaluation
    // ═══════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarioConfigs")
    @Order(1)
    void evaluateScenario(ScenarioConfig config, @TempDir Path tempDir) throws Exception {
        // Initialize trace for this scenario
        MergeTracer.init("BrakeCaseStudy-TrackB", config.id());
        MergeTracer.trace("");
        MergeTracer.boxStart("Track B: " + config.id());
        MergeTracer.boxLine("Family: " + config.family());
        MergeTracer.boxLine("Base components: " + config.baseComponentCount()
                + " | Commits/branch: " + config.commitsPerBranchA()
                + " | Overlap: " + (int)(config.overlapFraction() * 100) + "%"
                + " | Reaction density: " + (int)(config.reactionTriggerFraction() * 100) + "%");
        MergeTracer.boxLine("Bidirectional: " + config.bidirectional() + " | Seed: " + config.seed());
        MergeTracer.boxEnd();
        MergeTracer.trace("");

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
                try {
                    vitResult = new SemanticMergeCommand().executeWithInterleaving(
                            scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                            ThreeModelScenarioSetup.allCPS(), interactionProvider, null);
                } catch (Throwable e) {
                    // Retry with conflict resolution to get conflict info
                    vitResult = new SemanticMergeCommand().executeWithInterleaving(
                            scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                            ThreeModelScenarioSetup.allCPS(), interactionProvider,
                            ConflictResolutionProvider.chooseAllTheirs());
                }
            } catch (Throwable e) {
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

        // 5. Post-merge consistency check
        if (vitResult != null && vitResult.isSuccess() && vitResult.getMergedStateFolder() != null) {
            metrics = verifyConsistency(metrics, vitResult.getMergedStateFolder());
        } else {
            metrics = metrics.withConsistencyResult(false,
                    vitError != null ? "Merge failed: " + vitError : "Merge not successful");
        }

        allResults.put(config.id(), metrics);

        // Log per-scenario result to trace and console
        MergeTracer.trace("");
        MergeTracer.resultStart("Track B Result: " + config.id());
        MergeTracer.resultLine("  Vitruvius: " + metrics.getVitruviusBlockingTotal() + " blocking, "
                + metrics.getVitruviusWarningsTotal() + " warnings");
        MergeTracer.resultLine("  EMF Compare: " + metrics.getEmfCompareConflicts() + " conflicts");
        MergeTracer.resultLine("  Conflict reduction: " + metrics.getConflictReduction());
        if (vitResult != null) {
            MergeTracer.resultLine("  Merge status: " + vitResult.getStatus()
                    + " | Direction: " + vitResult.getMergeDirection());
        }
        MergeTracer.resultLine("  Consistency verified: "
                + (metrics.isConsistencyVerified() ? "YES" : "NO"
                + (metrics.getConsistencyError() != null ? " — " + metrics.getConsistencyError() : "")));
        MergeTracer.resultLine("  Timing: setup=" + setupMs + "ms, vitruvius=" + vitMs + "ms, emf=" + emfMs + "ms");
        if (vitError != null) {
            MergeTracer.resultLine("  ERROR: " + vitError);
        }
        MergeTracer.resultEnd();
        MergeTracer.trace("");
        MergeTracer.close();

        System.out.printf("[%s] Vit: %d blocking, %d warnings | EMF: %d conflicts | "
                        + "consistent=%s | setup=%dms, vit=%dms, emf=%dms%s%n",
                config.id(),
                metrics.getVitruviusBlockingTotal(),
                metrics.getVitruviusWarningsTotal(),
                metrics.getEmfCompareConflicts(),
                metrics.isConsistencyVerified() ? "YES" : "NO",
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

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path outputDir = Path.of("target/evaluation-" + timestamp);
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("trackb-summary-" + timestamp + ".md"), markdown);
        Files.writeString(outputDir.resolve("trackb-evaluation-" + timestamp + ".csv"), csv);

        // Also write to fixed names for easy access
        Path targetDir = Path.of("target");
        Files.writeString(targetDir.resolve("trackb-summary.md"), markdown);
        Files.writeString(targetDir.resolve("trackb-evaluation.csv"), csv);

        System.out.println("\n" + markdown);
        System.out.println("\nOutput written to " + outputDir);
        System.out.println("  " + outputDir.resolve("trackb-summary-" + timestamp + ".md"));
        System.out.println("  " + outputDir.resolve("trackb-evaluation-" + timestamp + ".csv"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Post-merge consistency verification
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Verifies that the merged VSUM state is consistent: all three model resources
     * exist, no unresolved proxies remain, and component count is positive.
     */
    private TrackBEvaluationMetrics verifyConsistency(TrackBEvaluationMetrics metrics, Path mergedStateFolder) {
        InternalVirtualModel merged = null;
        try {
            var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());
            merged = GitStateLoader.loadVsumFromDir(mergedStateFolder,
                    ThreeModelScenarioSetup.allCPS(), interactionProvider);

            // Check M1: Brakesystem model exists and has components
            Brakesystem bs = getModelRoot(merged, Brakesystem.class, "bs-check");
            if (bs == null) {
                return metrics.withConsistencyResult(false, "Brakesystem model root not found");
            }
            if (bs.getBrakeComponents().isEmpty()) {
                return metrics.withConsistencyResult(false, "Brakesystem has no components");
            }

            // Check M2: CAD model exists
            CAD_Model cad = getModelRoot(merged, CAD_Model.class, "cad-check");
            if (cad == null) {
                return metrics.withConsistencyResult(false, "CAD model root not found");
            }

            // Check M3: Safety model exists
            SafetyAssessment safety = getModelRoot(merged, SafetyAssessment.class, "safety-check");
            if (safety == null) {
                return metrics.withConsistencyResult(false, "Safety model root not found");
            }

            // Check for unresolved proxies across all model resources
            for (var sourceModel : merged.getViewSourceModels()) {
                var rs = sourceModel.getResourceSet();
                EcoreUtil.resolveAll(rs);
                for (Resource resource : rs.getResources()) {
                    for (var error : resource.getErrors()) {
                        if (error.getMessage() != null && error.getMessage().contains("proxy")) {
                            return metrics.withConsistencyResult(false,
                                    "Unresolved proxy in " + resource.getURI() + ": " + error.getMessage());
                        }
                    }
                }
            }

            return metrics.withConsistencyResult(true, null);

        } catch (Exception e) {
            return metrics.withConsistencyResult(false,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (merged != null) {
                try {
                    merged.dispose();
                } catch (Exception ignored) {
                    // Best-effort cleanup
                }
            }
        }
    }

    /**
     * Loads a model root of the given type from the VSUM via an identity-mapping view.
     */
    private <T> T getModelRoot(VirtualModel vsum, Class<T> rootType, String viewName) {
        try {
            var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType(viewName));
            selector.getSelectableElements().stream()
                    .filter(rootType::isInstance)
                    .forEach(e -> selector.setSelected(e, true));
            var view = selector.createView();
            var roots = view.getRootObjects(rootType);
            return roots.iterator().hasNext() ? roots.iterator().next() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario configuration generation (5 families, 105 total)
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
     * Family 1: History Length Scaling (25 scenarios).
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
     * Family 2: Overlap Density (20 scenarios).
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
     * Family 3: Reaction-Trigger Density (15 scenarios).
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
     * Family 4: Base State Size (15 scenarios).
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
     * Family 5: Bidirectional Merge (30 scenarios).
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
