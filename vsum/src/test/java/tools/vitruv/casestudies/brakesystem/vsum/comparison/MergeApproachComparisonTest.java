package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.vsum.branch.merge.ConflictResolutionProvider;
import tools.vitruv.framework.vsum.branch.merge.MergeConflict;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeCommand;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeResult;

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

    private final ThreeModelScenarioSetup setup = new ThreeModelScenarioSetup();
    private final EMFCompareThreeWayMerge emfCompareMerge = new EMFCompareThreeWayMerge();

    @BeforeAll
    static void registerFactories() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("*", new XMIResourceFactoryImpl());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Main comparison table test
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Generate comparison table: Vitruvius vs EMFCompare across all scenarios")
    void generateComparisonTable(@TempDir Path tempDir) throws Exception {
        List<MergeEvaluationResult> results = new ArrayList<>();

        for (int i = 1; i <= 7; i++) {
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

        // Save to file
        Path outputDir = Path.of("target");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("merge-comparison-table.md"), table);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Individual scenario tests (for debugging)
    // ═══════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "S{0}: Vitruvius semantic merge")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void vitruviusMerge(int scenario, @TempDir Path tempDir) throws Exception {
        var prepared = setup.setupScenario(scenario, tempDir);
        var result = runVitruviusMerge(prepared, scenario);

        System.out.printf("S%d [Vitruvius]: conflicts=%d, warnings=%d, success=%s%n",
                scenario, result.getDirectConflictCount(), result.getWarningCount(),
                result.isMergeSucceeded());
        for (String detail : result.getConflictDetails()) {
            System.out.println("  CONFLICT: " + detail);
        }
        for (String detail : result.getWarningDetails()) {
            System.out.println("  WARNING: " + detail);
        }
    }

    @ParameterizedTest(name = "S{0}: EMFCompare three-way merge")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void emfCompareMerge(int scenario, @TempDir Path tempDir) throws Exception {
        var prepared = setup.setupScenario(scenario, tempDir);
        var result = runEMFCompareMerge(prepared, scenario);

        System.out.printf("S%d [EMFCompare]: conflicts=%d (per model: %s), success=%s%n",
                scenario, result.getDirectConflictCount(), result.getConflictsPerModel(),
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

        SemanticMergeResult mergeResult;
        try {
            // Try without conflict resolution first
            mergeResult = new SemanticMergeCommand().execute(
                    scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                    ThreeModelScenarioSetup.allCPS(), interactionProvider);
        } catch (Exception e) {
            // If merge fails with an exception, try with resolution to get conflict info
            try {
                mergeResult = new SemanticMergeCommand().execute(
                        scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                        ThreeModelScenarioSetup.allCPS(), interactionProvider,
                        ConflictResolutionProvider.chooseAllTheirs());
            } catch (Exception e2) {
                // Complete failure
                return MergeEvaluationResult.builder("S" + scenarioNum, "Vitruvius")
                        .scenarioDescription(ThreeModelScenarioSetup.scenarioDescription(scenarioNum))
                        .mergeSucceeded(false)
                        .addConflictDetail("Merge failed: " + e.getMessage())
                        .directConflictCount(1)
                        .build();
            }
        }

        var builder = MergeEvaluationResult.builder("S" + scenarioNum, "Vitruvius")
                .scenarioDescription(ThreeModelScenarioSetup.scenarioDescription(scenarioNum))
                .mergeSucceeded(mergeResult.isSuccess())
                .directConflictCount(mergeResult.getConflicts().size())
                .warningCount(mergeResult.getWarnings().size());

        for (MergeConflict conflict : mergeResult.getConflicts()) {
            builder.addConflictDetail(formatVitruviusConflict(conflict));
        }
        for (MergeConflict warning : mergeResult.getWarnings()) {
            builder.addWarningDetail(formatVitruviusConflict(warning));
        }

        return builder.build();
    }

    private MergeEvaluationResult runEMFCompareMerge(
            ThreeModelScenarioSetup.PreparedScenario scenario, int scenarioNum) throws Exception {
        try {
            return emfCompareMerge.evaluate(
                    scenario.repoPath(), scenario.sourceBranch(), scenario.targetBranch(),
                    "S" + scenarioNum);
        } catch (Exception e) {
            return MergeEvaluationResult.builder("S" + scenarioNum, "EMFCompare")
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
        sb.append("| Scenario | Category | Description | Vitruvius Conflicts | Vitruvius Warnings | EMFCompare Conflicts | EMFCompare Conflicts/Model | Fewer with Vitruvius? |\n");
        sb.append("|----------|----------|-------------|--------------------|--------------------|---------------------|---------------------------|----------------------|\n");

        for (int i = 1; i <= 7; i++) {
            String sid = "S" + i;
            var scenarioResults = byScenario.get(sid);
            if (scenarioResults == null) continue;

            var vit = scenarioResults.stream()
                    .filter(r -> r.getApproach().equals("Vitruvius")).findFirst().orElse(null);
            var emf = scenarioResults.stream()
                    .filter(r -> r.getApproach().equals("EMFCompare")).findFirst().orElse(null);

            if (vit == null || emf == null) continue;

            String category = ThreeModelScenarioSetup.scenarioCategory(i);
            String desc = ThreeModelScenarioSetup.scenarioDescription(i);

            String vitConflicts = formatCount(vit.getDirectConflictCount(), vit.getConflictDetails());
            String vitWarnings = formatCount(vit.getWarningCount(), vit.getWarningDetails());
            String emfConflicts = formatCount(emf.getDirectConflictCount(), emf.getConflictDetails());
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

            sb.append(String.format("| %s | %s | %s | %d | %d | %d | %s | %s |\n",
                    sid, category, desc,
                    vit.getDirectConflictCount(), vit.getWarningCount(),
                    emf.getDirectConflictCount(), emfPerModel, delta));
        }

        // Add detailed breakdown
        sb.append("\n## Detailed Conflict Information\n\n");
        for (int i = 1; i <= 7; i++) {
            String sid = "S" + i;
            var scenarioResults = byScenario.get(sid);
            if (scenarioResults == null) continue;

            sb.append("### ").append(sid).append(": ").append(ThreeModelScenarioSetup.scenarioDescription(i)).append("\n\n");

            for (var result : scenarioResults) {
                sb.append("**").append(result.getApproach()).append("**: ");
                sb.append(result.getDirectConflictCount()).append(" conflicts, ");
                sb.append(result.getWarningCount()).append(" warnings");
                sb.append(result.isMergeSucceeded() ? " (merge succeeded)" : " (merge failed)");
                sb.append("\n");

                for (String detail : result.getConflictDetails()) {
                    sb.append("- CONFLICT: ").append(detail).append("\n");
                }
                for (String detail : result.getWarningDetails()) {
                    sb.append("- WARNING: ").append(detail).append("\n");
                }
                sb.append("\n");
            }
        }

        // Add summary
        sb.append("## Summary\n\n");
        int vitTotalConflicts = results.stream()
                .filter(r -> r.getApproach().equals("Vitruvius"))
                .mapToInt(MergeEvaluationResult::getDirectConflictCount).sum();
        int vitTotalWarnings = results.stream()
                .filter(r -> r.getApproach().equals("Vitruvius"))
                .mapToInt(MergeEvaluationResult::getWarningCount).sum();
        int emfTotalConflicts = results.stream()
                .filter(r -> r.getApproach().equals("EMFCompare"))
                .mapToInt(MergeEvaluationResult::getDirectConflictCount).sum();

        sb.append(String.format("- **Vitruvius**: %d total conflicts, %d total warnings across all scenarios%n",
                vitTotalConflicts, vitTotalWarnings));
        sb.append(String.format("- **EMFCompare**: %d total conflicts across all scenarios%n",
                emfTotalConflicts));
        sb.append(String.format("- **Conflict reduction**: %d fewer blocking conflicts with Vitruvius%n",
                Math.max(0, emfTotalConflicts - vitTotalConflicts)));

        return sb.toString();
    }

    private String formatCount(int count, List<String> details) {
        if (count == 0) return "0";
        if (details.isEmpty()) return String.valueOf(count);
        return count + " (" + details.get(0) + ")";
    }

    private String formatPerModel(Map<String, Integer> perModel) {
        if (perModel.isEmpty()) return "-";
        return perModel.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
