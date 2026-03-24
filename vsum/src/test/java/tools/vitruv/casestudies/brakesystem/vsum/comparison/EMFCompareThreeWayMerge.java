package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Conflict;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryImpl;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryRegistryImpl;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.jgit.api.Git;

import brakesystem.BrakesystemPackage;
import edu.kit.ipd.sdq.metamodels.cad.CadPackage;
import safety.SafetyPackage;
import tools.vitruv.framework.vsum.branch.merge.GitStateLoader;

/**
 * Performs EMFCompare three-way merge on model files extracted from git branches.
 *
 * <p>This is the <b>baseline</b> merge approach for the paper comparison.
 * Unlike the Vitruvius semantic merge, EMFCompare treats ALL model changes equally —
 * it cannot distinguish between user-authored (original) changes and reaction-derived
 * (consequential) changes. Any overlapping modification on both branches is reported
 * as a conflict, regardless of whether it was user-intended or automatically derived.
 *
 * <p>The comparison is performed directly on serialized EMF Resources (XMI files),
 * without going through the VSUM or reaction infrastructure.
 */
public class EMFCompareThreeWayMerge {

    /** Model files to compare (relative to the project root). */
    private static final List<String> MODEL_FILES = List.of(
            "brakesystem.model",
            "example.cad",
            "safety.safety"
    );

    static {
        // Ensure EPackages are registered for XMI deserialization
        EPackage.Registry.INSTANCE.put(BrakesystemPackage.eNS_URI, BrakesystemPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(CadPackage.eNS_URI, CadPackage.eINSTANCE);
        EPackage.Registry.INSTANCE.put(SafetyPackage.eNS_URI, SafetyPackage.eINSTANCE);
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("*", new XMIResourceFactoryImpl());
    }

    /**
     * Evaluates a merge scenario using EMFCompare three-way comparison.
     *
     * @param repoRoot     path to the git repository
     * @param sourceBranch the source branch (e.g., "feature")
     * @param targetBranch the target branch (e.g., "main")
     * @param scenarioId   identifier for the comparison table (e.g., "S1")
     * @return evaluation result with conflict counts per model
     */
    public MergeEvaluationResult evaluate(Path repoRoot, String sourceBranch, String targetBranch,
                                           String scenarioId) throws Exception {
        var loader = new GitStateLoader(repoRoot);

        // Resolve branch SHAs
        String oursSha;
        String theirsSha;
        try (var git = Git.open(repoRoot.toFile())) {
            oursSha = git.getRepository().resolve(targetBranch).getName();
            theirsSha = git.getRepository().resolve(sourceBranch).getName();
        }
        String baseSha = loader.findMergeBase(oursSha, theirsSha);

        // Extract states into temp dirs
        Path baseDir = Files.createTempDirectory("emfcompare-base-");
        Path oursDir = Files.createTempDirectory("emfcompare-ours-");
        Path theirsDir = Files.createTempDirectory("emfcompare-theirs-");

        try {
            loader.checkoutStateAtCommit(baseSha, baseDir);
            loader.checkoutStateAtCommit(oursSha, oursDir);
            loader.checkoutStateAtCommit(theirsSha, theirsDir);

            // Run three-way comparison per model file
            var builder = MergeEvaluationResult.builder(scenarioId, "EMFCompare");
            int totalRealConflicts = 0;
            Map<String, Integer> conflictsPerModel = new LinkedHashMap<>();
            List<String> conflictDetails = new ArrayList<>();

            for (String modelFile : MODEL_FILES) {
                Path baseFile = baseDir.resolve(modelFile);
                Path oursFile = oursDir.resolve(modelFile);
                Path theirsFile = theirsDir.resolve(modelFile);

                // Skip if model file doesn't exist (e.g., safety not created in some scenarios)
                if (!Files.exists(baseFile) || !Files.exists(oursFile) || !Files.exists(theirsFile)) {
                    conflictsPerModel.put(modelFile, 0);
                    continue;
                }

                var result = compareThreeWay(baseFile, oursFile, theirsFile);
                int realConflicts = result.realConflictCount;
                totalRealConflicts += realConflicts;
                conflictsPerModel.put(modelFile, realConflicts);

                for (String detail : result.conflictDescriptions) {
                    conflictDetails.add("[" + modelFile + "] " + detail);
                }
            }

            builder.directConflictCount(totalRealConflicts)
                    .warningCount(0)  // EMFCompare has no warning concept
                    .mergeSucceeded(totalRealConflicts == 0);

            for (var entry : conflictsPerModel.entrySet()) {
                builder.conflictsForModel(entry.getKey(), entry.getValue());
            }
            for (String detail : conflictDetails) {
                builder.addConflictDetail(detail);
            }

            return builder.build();
        } finally {
            deleteRecursive(baseDir);
            deleteRecursive(oursDir);
            deleteRecursive(theirsDir);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EMFCompare three-way comparison
    // ═══════════════════════════════════════════════════════════════════

    private record ComparisonResult(
            int realConflictCount,
            int pseudoConflictCount,
            int leftDiffCount,
            int rightDiffCount,
            List<String> conflictDescriptions
    ) {}

    private ComparisonResult compareThreeWay(Path baseFile, Path oursFile, Path theirsFile) {
        // Load resources into separate ResourceSets to avoid URI collisions
        Resource baseResource = loadResource(baseFile);
        Resource oursResource = loadResource(oursFile);
        Resource theirsResource = loadResource(theirsFile);

        // Resolve proxies
        EcoreUtil.resolveAll(baseResource);
        EcoreUtil.resolveAll(oursResource);
        EcoreUtil.resolveAll(theirsResource);

        // Set up match engine with identifier-based matching
        var matchRegistry = MatchEngineFactoryRegistryImpl.createStandaloneInstance();
        var factory = new MatchEngineFactoryImpl(UseIdentifiers.WHEN_AVAILABLE);
        factory.setRanking(20);
        matchRegistry.add(factory);

        // Three-way comparison: ours (left) vs theirs (right) with base (origin)
        var scope = new DefaultComparisonScope(oursResource, theirsResource, baseResource);
        Comparison comparison = EMFCompare.builder()
                .setMatchEngineFactoryRegistry(matchRegistry)
                .build()
                .compare(scope);

        // Count conflicts by kind
        int realCount = 0;
        int pseudoCount = 0;
        List<String> descriptions = new ArrayList<>();

        for (Conflict conflict : comparison.getConflicts()) {
            if (conflict.getKind() == ConflictKind.REAL) {
                realCount++;
                descriptions.add(describeConflict(conflict));
            } else {
                pseudoCount++;
            }
        }

        int leftDiffs = (int) comparison.getDifferences().stream()
                .filter(d -> d.getSource() == DifferenceSource.LEFT)
                .count();
        int rightDiffs = (int) comparison.getDifferences().stream()
                .filter(d -> d.getSource() == DifferenceSource.RIGHT)
                .count();

        return new ComparisonResult(realCount, pseudoCount, leftDiffs, rightDiffs, descriptions);
    }

    private Resource loadResource(Path file) {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
        // Register EPackages in this ResourceSet
        rs.getPackageRegistry().put(BrakesystemPackage.eNS_URI, BrakesystemPackage.eINSTANCE);
        rs.getPackageRegistry().put(CadPackage.eNS_URI, CadPackage.eINSTANCE);
        rs.getPackageRegistry().put(SafetyPackage.eNS_URI, SafetyPackage.eINSTANCE);

        URI uri = URI.createFileURI(file.toAbsolutePath().toString());
        return rs.getResource(uri, true);
    }

    private String describeConflict(Conflict conflict) {
        var leftDiffs = conflict.getLeftDifferences();
        var rightDiffs = conflict.getRightDifferences();

        StringBuilder sb = new StringBuilder();
        sb.append("REAL conflict: ");
        sb.append(leftDiffs.size()).append(" left diff(s) vs ");
        sb.append(rightDiffs.size()).append(" right diff(s)");

        // Add detail about the first diff on each side
        if (!leftDiffs.isEmpty()) {
            Diff left = leftDiffs.get(0);
            sb.append(" | LEFT: ").append(left.getKind()).append(" on ").append(describeMatch(left));
        }
        if (!rightDiffs.isEmpty()) {
            Diff right = rightDiffs.get(0);
            sb.append(" | RIGHT: ").append(right.getKind()).append(" on ").append(describeMatch(right));
        }

        return sb.toString();
    }

    private String describeMatch(Diff diff) {
        var match = diff.getMatch();
        if (match != null && match.getLeft() != null) {
            return match.getLeft().eClass().getName();
        }
        if (match != null && match.getRight() != null) {
            return match.getRight().eClass().getName();
        }
        return "unknown";
    }

    private void deleteRecursive(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }
}
