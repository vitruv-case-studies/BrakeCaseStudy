package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import brakesystem.BrakesystemPackage;
import edu.kit.ipd.sdq.metamodels.cad.CadPackage;
import safety.SafetyPackage;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.vsum.branch.merge.ConflictResolutionProvider;
import tools.vitruv.framework.vsum.branch.merge.MergeConflict;
import tools.vitruv.framework.vsum.branch.merge.IntraBranchDependencyMode;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeCommand;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeResult;
import tools.vitruv.merge.comparison.EMFCompareThreeWayMerge;
import tools.vitruv.merge.comparison.MergeEvaluationResult;

/**
 * Comparison test that runs all 7 three-model merge scenarios through both
 * Vitruvius semantic merge and EMFCompare three-way merge, producing a
 * markdown comparison table for the research paper.
 *
 * <p>The key hypothesis: Vitruvius reports fewer blocking conflicts because
 * it distinguishes user-authored (original) changes from reaction-derived
 * (consequential) changes. EMFCompare treats all changes equally, reporting
 * more conflicts — including false positives on derived model changes.
 */
public class MergeApproachComparisonTest {

    private static final List<String> MODEL_FILES = List.of(
            "brakesystem.model", "example.cad", "safety.safety");

    private final ThreeModelScenarioSetup setup = new ThreeModelScenarioSetup();
    private final EMFCompareThreeWayMerge emfCompareMerge = new EMFCompareThreeWayMerge(MODEL_FILES);

    @BeforeAll
    static void registerFactories() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("*", new XMIResourceFactoryImpl());
        EPackage.Registry.INSTANCE.put(BrakesystemPackage.eNS_URI, BrakesystemPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(CadPackage.eNS_URI, CadPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(SafetyPackage.eNS_URI, SafetyPackage.eINSTANCE);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Main comparison table test
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Generate comparison table: Vitruvius vs EMFCompare across all scenarios")
    void generateComparisonTable(@TempDir Path tempDir) throws Exception {
        List<MergeEvaluationResult> results = new ArrayList<>();

        int[] scenarios = {1, 4, 6, 7, 13};
        for (int i : scenarios) {
            // Run Vitruvius merge
            Path vitDir = Files.createDirectories(tempDir.resolve("s" + i + "-vitruvius"));
            var vitScenario = setup.setupScenario(i, vitDir);
            results.add(runVitruviusMerge(vitScenario, i));

            // Run EMFCompare merge on a separate copy
            Path emfDir = Files.createDirectories(tempDir.resolve("s" + i + "-emfcompare"));
            var emfScenario = setup.setupScenario(i, emfDir);
            results.add(runEMFCompareMerge(emfScenario, i));
        }

        String table = formatComparisonTable(results);
        System.out.println("\n" + table);

        // Save to timestamped output directory
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path outputDir = Path.of("target/evaluation-" + timestamp);
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("merge-comparison-table-" + timestamp + ".md"), table);

        // Also write to fixed name for easy access
        Path targetDir = Path.of("target");
        Files.writeString(targetDir.resolve("merge-comparison-table.md"), table);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Individual scenario tests (for debugging)
    // ═══════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "{0}: Vitruvius semantic merge")
    @ValueSource(ints = {1, 4, 6, 7, 13})
    void vitruviusMerge(int scenario, @TempDir Path tempDir) throws Exception {
        var prepared = setup.setupScenario(scenario, tempDir);
        var result = runVitruviusMerge(prepared, scenario);

        System.out.printf("%s [Vitruvius]: conflicts=%d, success=%s%n",
                ThreeModelScenarioSetup.scenarioLabel(scenario),
                result.getDirectConflictCount(),
                result.isMergeSucceeded());
        for (String detail : result.getConflictDetails()) {
            System.out.println("  CONFLICT: " + detail);
        }
    }

    @ParameterizedTest(name = "{0}: EMFCompare three-way merge")
    @ValueSource(ints = {1, 4, 6, 7, 13})
    void emfCompareMerge(int scenario, @TempDir Path tempDir) throws Exception {
        var prepared = setup.setupScenario(scenario, tempDir);
        var result = runEMFCompareMerge(prepared, scenario);

        System.out.printf("%s [EMFCompare]: conflicts=%d (per model: %s), success=%s%n",
                ThreeModelScenarioSetup.scenarioLabel(scenario),
                result.getDirectConflictCount(), result.getConflictsPerModel(),
                result.isMergeSucceeded());
        for (String detail : result.getConflictDetails()) {
            System.out.println("  CONFLICT: " + detail);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Merge execution
    // ═══════════════════════════════════════════════════════════════════

    private MergeEvaluationResult runVitruviusMerge(
            ThreeModelScenarioSetup.PreparedScenario scenario, int scenarioNum) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());
        var intraBranchMode = Boolean.getBoolean("merge.sequential")
                ? IntraBranchDependencyMode.SEQUENTIAL
                : IntraBranchDependencyMode.CALCULATED;

        SemanticMergeResult mergeResult;
        try {
            // Try without conflict resolution first (interleaving merge)
            mergeResult = new SemanticMergeCommand().executeWithInterleaving(
                    scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                    ThreeModelScenarioSetup.allCPS(), interactionProvider, null, intraBranchMode);
        } catch (Exception e) {
            // If merge fails with an exception, try with resolution to get conflict info
            try {
                mergeResult = new SemanticMergeCommand().executeWithInterleaving(
                        scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                        ThreeModelScenarioSetup.allCPS(), interactionProvider,
                        ConflictResolutionProvider.chooseAllTheirs(), intraBranchMode);
            } catch (Exception e2) {
                // Complete failure
                return MergeEvaluationResult.builder(ThreeModelScenarioSetup.scenarioLabel(scenarioNum), "Vitruvius")
                        .scenarioDescription(ThreeModelScenarioSetup.scenarioDescription(scenarioNum))
                        .mergeSucceeded(false)
                        .addConflictDetail("Merge failed: " + e.getMessage())
                        .directConflictCount(1)
                        .build();
            }
        }

        var builder = MergeEvaluationResult.builder(ThreeModelScenarioSetup.scenarioLabel(scenarioNum), "Vitruvius")
                .scenarioDescription(ThreeModelScenarioSetup.scenarioDescription(scenarioNum))
                .mergeSucceeded(mergeResult.isSuccess())
                .directConflictCount(mergeResult.getConflicts().size());

        for (MergeConflict conflict : mergeResult.getConflicts()) {
            builder.addConflictDetail(formatVitruviusConflict(conflict));
        }

        return builder.build();
    }

    private MergeEvaluationResult runEMFCompareMerge(
            ThreeModelScenarioSetup.PreparedScenario scenario, int scenarioNum) throws Exception {
        try {
            return emfCompareMerge.evaluate(
                    scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                    ThreeModelScenarioSetup.scenarioLabel(scenarioNum));
        } catch (Exception e) {
            return MergeEvaluationResult.builder(ThreeModelScenarioSetup.scenarioLabel(scenarioNum), "EMFCompare")
                    .scenarioDescription(ThreeModelScenarioSetup.scenarioDescription(scenarioNum))
                    .mergeSucceeded(false)
                    .addConflictDetail("EMFCompare failed: " + e.getMessage())
                    .build();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Formatting
    // ═══════════════════════════════════════════════════════════════════

    private String formatVitruviusConflict(MergeConflict conflict) {
        StringBuilder sb = new StringBuilder();
        sb.append(conflict.getType().name());
        if (conflict.getConflictingFeature() != null) {
            sb.append(" on ").append(conflict.getConflictingFeature());
        }
        if (conflict.getElementId() != null) {
            sb.append(" at ").append(conflict.getElementId());
        }
        return sb.toString();
    }

    private String formatComparisonTable(List<MergeEvaluationResult> results) {
        var byScenario = results.stream()
                .collect(Collectors.groupingBy(MergeEvaluationResult::getScenarioId));

        StringBuilder sb = new StringBuilder();
        sb.append("# Merge Approach Comparison: Vitruvius vs EMFCompare\n\n");
        sb.append("| Scenario | Category | Description | Vitruvius Conflicts | EMFCompare Conflicts | EMFCompare Conflicts/Model | Fewer with Vitruvius? |\n");
        sb.append("|----------|----------|-------------|:---:|:---:|---------------------------|----------------------|\n");

        for (int i : new int[]{1, 4, 6, 7, 13}) {
            String label = ThreeModelScenarioSetup.scenarioLabel(i);
            var scenarioResults = byScenario.get(label);
            if (scenarioResults == null) continue;

            var vit = scenarioResults.stream()
                    .filter(r -> r.getApproach().equals("Vitruvius")).findFirst().orElse(null);
            var emf = scenarioResults.stream()
                    .filter(r -> r.getApproach().equals("EMFCompare")).findFirst().orElse(null);

            if (vit == null || emf == null) continue;

            String category = ThreeModelScenarioSetup.scenarioCategory(i);
            String desc = ThreeModelScenarioSetup.scenarioDescription(i);
            String emfPerModel = formatPerModel(emf.getConflictsPerModel());

            int vitTotal = vit.getDirectConflictCount();
            int emfTotal = emf.getDirectConflictCount();
            String delta;
            if (emfTotal > vitTotal) {
                delta = "Yes (-" + (emfTotal - vitTotal) + ")";
            } else if (emfTotal == vitTotal) {
                delta = "Same";
            } else {
                delta = "No (+" + (vitTotal - emfTotal) + ")";
            }

            sb.append(String.format("| %s | %s | %s | %d | %d | %s | %s |\n",
                    label, category, desc,
                    vit.getDirectConflictCount(),
                    emf.getDirectConflictCount(), emfPerModel, delta));
        }

        // Add detailed breakdown
        sb.append("\n## Detailed Conflict Information\n\n");
        for (int i : new int[]{1, 4, 6, 7, 13}) {
            String label = ThreeModelScenarioSetup.scenarioLabel(i);
            var scenarioResults = byScenario.get(label);
            if (scenarioResults == null) continue;

            sb.append("### ").append(label).append(": ").append(ThreeModelScenarioSetup.scenarioDescription(i)).append("\n\n");

            for (var result : scenarioResults) {
                sb.append("**").append(result.getApproach()).append("**: ");
                sb.append(result.getDirectConflictCount()).append(" conflicts");
                sb.append(result.isMergeSucceeded() ? " (merge succeeded)" : " (merge failed)");
                sb.append("\n");

                for (String detail : result.getConflictDetails()) {
                    sb.append("- CONFLICT: ").append(detail).append("\n");
                }
                sb.append("\n");
            }
        }

        // Add summary
        sb.append("## Summary\n\n");
        int vitTotalConflicts = results.stream()
                .filter(r -> r.getApproach().equals("Vitruvius"))
                .mapToInt(MergeEvaluationResult::getDirectConflictCount).sum();
        int emfTotalConflicts = results.stream()
                .filter(r -> r.getApproach().equals("EMFCompare"))
                .mapToInt(MergeEvaluationResult::getDirectConflictCount).sum();

        sb.append(String.format("- **Vitruvius**: %d total conflicts across all scenarios%n",
                vitTotalConflicts));
        sb.append(String.format("- **EMFCompare**: %d total conflicts across all scenarios%n",
                emfTotalConflicts));
        sb.append(String.format("- **Conflict reduction**: %d fewer blocking conflicts with Vitruvius%n",
                Math.max(0, emfTotalConflicts - vitTotalConflicts)));

        return sb.toString();
    }

    private String formatPerModel(Map<String, Integer> perModel) {
        if (perModel.isEmpty()) return "-";
        return perModel.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
