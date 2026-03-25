package tools.vitruv.casestudies.brakesystem.vsum.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.eclipse.emf.common.util.URI;
import org.eclipse.jgit.api.Git;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import tools.vitruv.framework.vsum.branch.merge.ChangeLogCapture;
import tools.vitruv.framework.vsum.branch.merge.SemanticChangeLog;
import tools.vitruv.casestudies.brakesystem.vsum.comparison.ThreeModelScenarioSetup;

/**
 * Generates BrakeCaseStudy models of variable size for performance benchmarking.
 *
 * <p>Creates a Git repository with a base commit containing {@code numComponents}
 * BrakeDisk components (each triggering reactions to create CAD parameters in M2
 * and SafetyEntry in M3), then creates two branches with {@code numTransactions}
 * commits each.
 *
 * <p>The generated model size scales approximately as:
 * <ul>
 *   <li>M1 (Brakesystem): 1 root + numComponents BrakeDisk objects</li>
 *   <li>M2 (CAD): 1 root + numComponents Namespaces, each with ~5 Parameters</li>
 *   <li>M3 (Safety): 1 root + numComponents SafetyEntry objects</li>
 *   <li>Total EMF objects: ~7 * numComponents</li>
 * </ul>
 */
public class ScalableModelGenerator {

    /**
     * Configuration for a benchmark scenario.
     */
    public record BenchmarkConfig(
            int numComponents,
            int numTransactionsPerBranch,
            double overlapFraction,
            boolean highReactionDensity,
            long seed
    ) {
        public String label() {
            return String.format("C%d_T%d_O%.0f%%_R%s",
                    numComponents, numTransactionsPerBranch,
                    overlapFraction * 100,
                    highReactionDensity ? "high" : "low");
        }
    }

    /**
     * Result of generating a benchmark scenario.
     */
    public record GeneratedScenario(
            Path repoPath,
            String sourceBranch,
            String targetBranch,
            int totalModelElements,
            int changesPerBranch,
            long setupTimeNanos
    ) {}

    /**
     * Generates a complete benchmark scenario with the given configuration.
     *
     * @param config the benchmark configuration
     * @param tempDir the directory for the Git repository
     * @return the generated scenario metadata
     */
    public GeneratedScenario generate(BenchmarkConfig config, Path tempDir) throws Exception {
        long startTime = System.nanoTime();
        Random rng = new Random(config.seed());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = ThreeModelScenarioSetup.createThreeModelVsum(tempDir);

            // Create Brakesystem root
            ThreeModelScenarioSetup.addBrakesystem(vsum, tempDir);

            // Add base components — all BrakeDisks for simplicity and uniform reaction triggering
            for (int i = 0; i < config.numComponents(); i++) {
                ThreeModelScenarioSetup.addBrakeDisk(vsum,
                        "disk_" + i,
                        200 + rng.nextInt(200),   // diameter 200-399
                        rng.nextBoolean(),         // ventilated
                        15 + rng.nextInt(20));     // thickness 15-34
            }

            // Count total model elements (approximate)
            int totalElements = 1 + config.numComponents() * 7; // root + ~7 per component across M1/M2/M3

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: " + config.numComponents() + " components").call();

            // Determine which components each branch modifies
            int overlapCount = (int) (config.numComponents() * config.overlapFraction());
            // Branch A modifies components [0, numA)
            // Branch B modifies components [numB_start, numB_start + numB)
            // Overlap region: components modified by both branches

            List<Integer> branchATargets = selectBranchTargets(
                    config.numComponents(), config.numTransactionsPerBranch(),
                    0, overlapCount, rng);
            List<Integer> branchBTargets = selectBranchTargets(
                    config.numComponents(), config.numTransactionsPerBranch(),
                    config.numComponents() / 2, overlapCount, rng);

            // Branch A (feature)
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();

            int changesA = generateBranchChanges(vsum, git, tempDir, "feature",
                    branchATargets, config.highReactionDensity(), rng, 1000);

            // Branch B (main)
            git.checkout().setName("main").call();
            vsum.reload();

            int changesB = generateBranchChanges(vsum, git, tempDir, "main",
                    branchBTargets, config.highReactionDensity(), rng, 2000);

            vsum.dispose();

            long setupTime = System.nanoTime() - startTime;
            return new GeneratedScenario(tempDir, "feature", "main",
                    totalElements, changesA + changesB, setupTime);
        }
    }

    /**
     * Selects which component indices a branch will modify across its transactions.
     */
    private List<Integer> selectBranchTargets(int totalComponents, int numTransactions,
                                               int startOffset, int overlapCount, Random rng) {
        List<Integer> targets = new ArrayList<>();
        for (int t = 0; t < numTransactions; t++) {
            // Each transaction modifies one component
            int idx;
            if (t < overlapCount && overlapCount > 0) {
                // First few transactions target the overlap region
                idx = t % totalComponents;
            } else {
                // Remaining transactions target non-overlapping region starting from offset
                idx = (startOffset + t) % totalComponents;
            }
            targets.add(idx);
        }
        return targets;
    }

    /**
     * Generates commits on a branch by modifying the specified components.
     *
     * @return total number of changes made
     */
    private int generateBranchChanges(InternalVirtualModel vsum, Git git, Path tempDir,
                                       String branch, List<Integer> targetIndices,
                                       boolean highReactionDensity, Random rng,
                                       int valueOffset) throws Exception {
        int totalChanges = 0;
        for (int t = 0; t < targetIndices.size(); t++) {
            var capture = ThreeModelScenarioSetup.freshCapture(vsum);
            int componentIdx = targetIndices.get(t);
            String diskId = "disk_" + componentIdx;

            if (highReactionDensity) {
                // Change diameter — triggers M1->M2 reaction (CAD parameter update)
                // and M1->M3 reaction (SafetyEntry thermalLoadRating update)
                int newDiameter = valueOffset + t * 10 + rng.nextInt(50);
                ThreeModelScenarioSetup.changeBrakeDiskDiameter(vsum, diskId, newDiameter);
            } else {
                // Change diameter only (still triggers reactions, but simpler change)
                int newDiameter = valueOffset + t * 10 + rng.nextInt(50);
                ThreeModelScenarioSetup.changeBrakeDiskDiameter(vsum, diskId, newDiameter);
            }
            totalChanges++;

            ThreeModelScenarioSetup.commitWithChangelog(git, capture, tempDir, branch,
                    branch + " txn " + (t + 1) + ": modify " + diskId);
        }
        return totalChanges;
    }
}
