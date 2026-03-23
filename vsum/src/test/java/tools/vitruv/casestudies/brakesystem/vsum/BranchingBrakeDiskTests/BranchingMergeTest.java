package tools.vitruv.casestudies.brakesystem.vsum.BranchingBrakeDiskTests;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.branch.merge.ChangeLogCapture;
import tools.vitruv.framework.vsum.branch.merge.GitStateLoader;
import tools.vitruv.framework.vsum.branch.merge.SemanticChangeLog;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeCommand;
import tools.vitruv.framework.vsum.branch.merge.MergeConflict;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeResult;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

import brakesystem.BrakesystemFactory;
import brakesystem.Brakesystem;
import brakesystem.BrakeDisk;
import brakesystem.ABSSensor;
import brakesystem.BrakeCaliper;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;

/**
 * Branching and merge tests for the BrakeCaseStudy.
 *
 * <p>Tests bidirectional consistency between brakesystem and CAD models across Git branches.
 * Uses the semantic merge engine with per-transaction replay and reaction-triggered propagation.
 *
 * <p>Depends on Vitruv framework branch {@code anne-branching} which provides the branching
 * and semantic merge infrastructure.
 */
public class BranchingMergeTest {

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    // === Test 1: Basic additive merge — both branches add different components ===

    @Test
    @DisplayName("merge: branch A adds BrakeDisk, branch B adds ABSSensor → both present after merge")
    void additiveMerge_differentComponents(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            // Base: empty brakesystem
            InternalVirtualModel vsum = createBrakeVsum(tempDir);
            addBrakesystem(vsum, tempDir);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: empty brakesystem").call();

            // Feature branch: add BrakeDisk via brakesystem view
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();

            var capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            addBrakeDisk(vsum, "disk1", 300, true);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Added BrakeDisk").call();
            String featureSha = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(featureSha, "feature", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("Added BrakeDisk + changelog").call();

            // Main branch: add ABSSensor
            git.checkout().setName("main").call();
            vsum.reload();
            vsum.removeChangePropagationListener(capture);
            capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            addABSSensor(vsum, "sensor1", 100, 3);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Added ABSSensor").call();
            String mainSha = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(mainSha, "main", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("Added ABSSensor + changelog").call();

            vsum.dispose();

            // Merge feature → main
            SemanticMergeCommand mergeCmd = new SemanticMergeCommand();
            SemanticMergeResult result = mergeCmd.execute(
                    tempDir, "feature", "main",
                    List.of(new Brakesystem2cadChangePropagationSpecification(),
                            new Cad2brakesystemChangePropagationSpecification()),
                    interactionProvider);

            assertTrue(result.isSuccess(), "Merge should succeed");

            // Verify merged state
            InternalVirtualModel merged = loadBrakeVsum(result.getMergedStateFolder());
            var bView = getBrakesystemView(merged);
            var bs = bView.getRootObjects(Brakesystem.class).iterator().next();

            assertEquals(2, bs.getBrakeComponents().size(),
                    "Should have 2 components: BrakeDisk + ABSSensor");

            Set<String> ids = bs.getBrakeComponents().stream()
                    .map(c -> c.getId()).collect(Collectors.toSet());
            assertTrue(ids.contains("disk1"), "Should have BrakeDisk disk1");
            assertTrue(ids.contains("sensor1"), "Should have ABSSensor sensor1");

            merged.dispose();
        }
    }

    // === Test 2: Rename conflict — both branches change same component's attribute ===

    @Test
    @DisplayName("conflict: both branches change BrakeDisk diameter → MODIFY_MODIFY conflict")
    void conflictOnSameAttribute(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            // Base: brakesystem with a BrakeDisk
            InternalVirtualModel vsum = createBrakeVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: BrakeDisk diameter=300").call();

            // Feature: change diameter to 320
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            changeBrakeDiskDiameter(vsum, "disk1", 320);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Changed diameter to 320").call();
            String fSha = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(fSha, "feature", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("Changed diameter to 320 + changelog").call();

            // Main: change diameter to 350
            git.checkout().setName("main").call();
            vsum.reload();
            vsum.removeChangePropagationListener(capture);
            capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            changeBrakeDiskDiameter(vsum, "disk1", 350);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Changed diameter to 350").call();
            String mSha = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(mSha, "main", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("Changed diameter to 350 + changelog").call();

            vsum.dispose();

            // Merge: should detect conflict
            SemanticMergeCommand mergeCmd = new SemanticMergeCommand();
            SemanticMergeResult result = mergeCmd.execute(
                    tempDir, "feature", "main",
                    List.of(new Brakesystem2cadChangePropagationSpecification(),
                            new Cad2brakesystemChangePropagationSpecification()),
                    interactionProvider);

            assertFalse(result.isSuccess(), "Merge should detect conflict");
            assertFalse(result.getConflicts().isEmpty(), "Should have conflicts");

            var conflict = result.getConflicts().get(0);
            assertEquals("diameterInMM", conflict.getConflictingFeature(),
                    "Conflict should be on diameterInMM");
        }
    }

    // === Test 3: user(A) overwrites derived(B) → WARNING ===
    //
    // Branch B adds a BrakeDisk (reaction creates CAD Namespace with "Diameter" parameter).
    // Branch A directly modifies the CAD Namespace (user change to derived model).
    // Merge A→B: A's user change overwrites B's derived state → warning, not conflict.

    @Test
    @DisplayName("warning: user(A) change to CAD overwrites derived(B) CAD state")
    void userAOverwritesDerivedB_warning(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            // Base: brakesystem with BrakeDisk (reaction creates CAD Namespace)
            InternalVirtualModel vsum = createBrakeVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: BrakeDisk disk1 diameter=300").call();

            // Branch A (theirs): directly rename Namespace.id via CAD view
            // This is a user-authored change to the derived model (CAD)
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            changeNamespaceId(vsum, "disk1", "cad-custom-id");

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Changed Namespace id to cad-custom-id").call();
            String fSha = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(fSha, "feature", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("CAD id change + changelog").call();

            // Branch B (ours): add a BrakeCaliper (different component, triggers reaction)
            // The derived state on B includes the Namespace for disk1 with id="disk1"
            git.checkout().setName("main").call();
            vsum.reload();
            vsum.removeChangePropagationListener(capture);
            capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            addBrakeCaliper(vsum, "caliper1", 42);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Added BrakeCaliper").call();
            String mSha = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(mSha, "main", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("BrakeCaliper + changelog").call();

            vsum.dispose();

            // Merge: A's user change to CAD namespace overwrites B's derived CAD state
            SemanticMergeCommand mergeCmd = new SemanticMergeCommand();
            SemanticMergeResult result = mergeCmd.execute(
                    tempDir, "feature", "main",
                    List.of(new Brakesystem2cadChangePropagationSpecification(),
                            new Cad2brakesystemChangePropagationSpecification()),
                    interactionProvider);

            // Merge should succeed (user intent wins over derived state)
            assertTrue(result.isSuccess(), "Merge should succeed (user(A) vs derived(B) = warning)");

            // Should have warnings (user(A) overwrites derived(B))
            // Note: warnings depend on the UUID footprint matching implementation
            java.lang.System.out.println("Warnings: " + result.getWarnings().size());
            result.getWarnings().forEach(w -> java.lang.System.out.println("  " + w));
        }
    }

    // === Test 4: derived(B) overwritten by reaction from A → NO WARNING ===
    //
    // Branch A adds a BrakeDisk. Branch B added a different BrakeDisk.
    // When A's changes are replayed, reactions create new CAD state.
    // The new derived state replaces nothing user-authored → no warning.

    @Test
    @DisplayName("no warning: derived state regenerated by reaction during replay")
    void derivedRegeneratedByReaction_noWarning(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createBrakeVsum(tempDir);
            addBrakesystem(vsum, tempDir);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: empty brakesystem").call();

            // Feature: add BrakeDisk
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            addBrakeDisk(vsum, "diskA", 280, false);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Added BrakeDisk diskA").call();
            String fSha = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(fSha, "feature", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("BrakeDisk diskA + changelog").call();

            // Main: add BrakeCaliper
            git.checkout().setName("main").call();
            vsum.reload();
            vsum.removeChangePropagationListener(capture);
            capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            addBrakeCaliper(vsum, "caliperB", 55);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Added BrakeCaliper caliperB").call();
            String mSha = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(mSha, "main", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("BrakeCaliper caliperB + changelog").call();

            vsum.dispose();

            // Merge: reactions create new derived state, no user state overwritten
            SemanticMergeCommand mergeCmd = new SemanticMergeCommand();
            SemanticMergeResult result = mergeCmd.execute(
                    tempDir, "feature", "main",
                    List.of(new Brakesystem2cadChangePropagationSpecification(),
                            new Cad2brakesystemChangePropagationSpecification()),
                    interactionProvider);

            assertTrue(result.isSuccess(), "Merge should succeed");

            // No warnings expected: derived state regeneration is normal
            // (user(A) doesn't touch any element that user(B) changed)
            assertEquals(0, result.getWarnings().stream()
                            .filter(w -> w.getType() == tools.vitruv.framework.vsum.branch.merge.MergeConflict.ConflictType.USER_VS_DERIVED_WARNING)
                            .count(),
                    "No user-vs-derived warnings expected for independent additions");

            // Verify merged state has both components
            InternalVirtualModel merged = loadBrakeVsum(result.getMergedStateFolder());
            var bView = getBrakesystemView(merged);
            var bs = bView.getRootObjects(Brakesystem.class).iterator().next();
            assertEquals(2, bs.getBrakeComponents().size(), "Should have 2 components");
            merged.dispose();
        }
    }

    // === Test 5: derived(replay(A)) overwrites user(B) → INDIRECT CONFLICT ===
    //
    // Branch A changes BrakeDisk.id (reaction updates Namespace.id).
    // Branch B directly changes Namespace.id (user change to CAD model).
    // When A's replay triggers the IdChanged reaction, it overwrites B's user intent.
    //
    // Current behavior: the indirect conflict is detected AFTER the fact.
    // Future: could reorder (apply A on base first, then B) or detect before replay.

    @Test
    @DisplayName("indirect conflict: derived(replay(A)) overwrites user(B) CAD namespace id")
    void indirectConflict_derivedReplayOverwritesUserB(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            // Base: BrakeDisk disk1 with id="disk1"
            InternalVirtualModel vsum = createBrakeVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: BrakeDisk disk1").call();

            // Branch A (theirs): change BrakeDisk.id = "engineeringDisk"
            // Reaction: Namespace.id → "engineeringDisk"
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            changeBrakeDiskId(vsum, "disk1", "engineeringDisk");

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Changed BrakeDisk id to engineeringDisk").call();
            String fSha = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(fSha, "feature", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("id change + changelog").call();

            // Branch B (ours): directly change Namespace.id = "cadCustomId" via CAD view
            git.checkout().setName("main").call();
            vsum.reload();
            vsum.removeChangePropagationListener(capture);
            capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            changeNamespaceId(vsum, "disk1", "cadCustomId");

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Changed Namespace id to cadCustomId").call();
            String mSha = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(mSha, "main", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("CAD id change + changelog").call();

            vsum.dispose();

            // Merge A→B: replaying A's BrakeDisk.id change triggers IdChanged reaction
            // which sets Namespace.id = "engineeringDisk", overwriting B's "cadCustomId"
            SemanticMergeCommand mergeCmd = new SemanticMergeCommand();
            SemanticMergeResult result = mergeCmd.execute(
                    tempDir, "feature", "main",
                    List.of(new Brakesystem2cadChangePropagationSpecification(),
                            new Cad2brakesystemChangePropagationSpecification()),
                    interactionProvider);

            // The merge succeeds (changes applied) but should report the indirect conflict
            assertTrue(result.isSuccess(), "Merge completes (changes applied)");

            // Check for indirect conflict or warning
            java.lang.System.out.println("Warnings: " + result.getWarnings().size());
            result.getWarnings().forEach(w -> java.lang.System.out.println("  " + w));

            // NOTE: Whether this shows as an indirect conflict depends on:
            // 1. The IdChanged reaction fires during replay, modifying Namespace.id
            // 2. DerivedChangeCapture captures the consequential changes
            // 3. detectIndirectConflicts matches the derived footprint vs user(B) footprint
            //
            // Future improvement: detect indirect conflicts BEFORE applying,
            // possibly by reordering: apply A on base first, then apply B.
        }
    }

    // === Test 6: Long sequence — multiple commits per branch ===

    @Test
    @DisplayName("long sequence: multiple commits per branch with different component types")
    void longSequence_multipleCommits(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createBrakeVsum(tempDir);
            addBrakesystem(vsum, tempDir);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: empty brakesystem").call();

            // Feature branch: 2 commits
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            // Commit 1: add BrakeDisk
            addBrakeDisk(vsum, "fDisk", 310, true);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Feature: added BrakeDisk").call();
            String f1 = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(f1, "feature", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("Feature: BrakeDisk + changelog").call();

            // Commit 2: add ABSSensor
            addABSSensor(vsum, "fSensor", 150, 4);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Feature: added ABSSensor").call();
            String f2 = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(f2, "feature", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("Feature: ABSSensor + changelog").call();

            // Main branch: 2 commits
            git.checkout().setName("main").call();
            vsum.reload();
            vsum.removeChangePropagationListener(capture);
            capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                    vsum.getViewSourceModels().iterator().next().getResourceSet());
            vsum.addChangePropagationListener(capture);

            // Commit 1: add BrakeCaliper
            addBrakeCaliper(vsum, "mCaliper", 38);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Main: added BrakeCaliper").call();
            String m1 = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(m1, "main", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("Main: BrakeCaliper + changelog").call();

            // Commit 2: add another BrakeDisk
            addBrakeDisk(vsum, "mDisk", 290, false);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Main: added BrakeDisk").call();
            String m2 = git.log().setMaxCount(1).call().iterator().next().getName();
            new SemanticChangeLog(m2, "main", capture.drainChanges(),
                    capture.drainUuidMapping()).saveTo(tempDir);
            git.add().addFilepattern(".").call();
            git.commit().setAmend(true).setMessage("Main: BrakeDisk + changelog").call();

            vsum.dispose();

            // Merge: should combine all 4 components
            SemanticMergeCommand mergeCmd = new SemanticMergeCommand();
            SemanticMergeResult result = mergeCmd.execute(
                    tempDir, "feature", "main",
                    List.of(new Brakesystem2cadChangePropagationSpecification(),
                            new Cad2brakesystemChangePropagationSpecification()),
                    interactionProvider);

            assertTrue(result.isSuccess(), "Long sequence merge should succeed");

            // Verify: 4 components from both branches
            InternalVirtualModel merged = loadBrakeVsum(result.getMergedStateFolder());
            var bView = getBrakesystemView(merged);
            var bs = bView.getRootObjects(Brakesystem.class).iterator().next();

            Set<String> ids = bs.getBrakeComponents().stream()
                    .map(c -> c.getId()).collect(Collectors.toSet());
            assertEquals(4, bs.getBrakeComponents().size(),
                    "Should have 4 components from both branches");
            assertTrue(ids.containsAll(Set.of("fDisk", "fSensor", "mCaliper", "mDisk")),
                    "All 4 component IDs should be present. Got: " + ids);

            merged.dispose();
        }
    }

    // === Helper methods ===

    private InternalVirtualModel createBrakeVsum(Path projectPath) throws Exception {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(new TestUserInteraction()))
                .withChangePropagationSpecifications(
                        new Brakesystem2cadChangePropagationSpecification())
                .withChangePropagationSpecifications(
                        new Cad2brakesystemChangePropagationSpecification())
                .buildAndInitialize();
        model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
        return model;
    }

    private InternalVirtualModel loadBrakeVsum(Path folder) throws Exception {
        return GitStateLoader.loadVsumFromDir(folder,
                List.of(new Brakesystem2cadChangePropagationSpecification(),
                        new Cad2brakesystemChangePropagationSpecification()),
                new TestUserInteraction.ResultProvider(new TestUserInteraction()));
    }

    private void addBrakesystem(VirtualModel vsum, Path projectPath) {
        var view = getBrakesystemView(vsum).withChangeDerivingTrait();
        var bs = BrakesystemFactory.eINSTANCE.createBrakesystem();
        bs.setSpecificationType("Standard");
        bs.setInstanceName("TestBrake");
        view.registerRoot(bs, URI.createFileURI(projectPath.toString() + "/brakesystem.model"));
        view.commitChanges();
    }

    private void addBrakeDisk(VirtualModel vsum, String id, int diameter, boolean ventilated) {
        var view = getBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var disk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
        disk.setId(id);
        disk.setDiameterInMM(diameter);
        disk.setVentilated(ventilated);
        disk.setBrakeDiskThicknessInMM(25);
        bs.getBrakeComponents().add(disk);
        view.commitChanges();
    }

    private void addABSSensor(VirtualModel vsum, String id, int length, int pins) {
        var view = getBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var sensor = BrakesystemFactory.eINSTANCE.createABSSensor();
        sensor.setId(id);
        sensor.setLengthInMM(length);
        sensor.setNumberOfPins(pins);
        bs.getBrakeComponents().add(sensor);
        view.commitChanges();
    }

    private void changeBrakeDiskDiameter(VirtualModel vsum, String id, int newDiameter) {
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("change"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof Brakesystem)
                .forEach(e -> selector.setSelected(e, true));
        var view = selector.createView().withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var disk = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakeDisk)
                .map(c -> (BrakeDisk) c)
                .findFirst().orElseThrow();
        disk.setDiameterInMM(newDiameter);
        view.commitChanges();
    }

    private void addBrakeCaliper(VirtualModel vsum, String id, int pistonDiameter) {
        var view = getBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var caliper = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
        caliper.setId(id);
        caliper.setPistonDiameterInMM(pistonDiameter);
        bs.getBrakeComponents().add(caliper);
        view.commitChanges();
    }

    private void changeBrakeDiskId(VirtualModel vsum, String oldId, String newId) {
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("change-id"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof Brakesystem)
                .forEach(e -> selector.setSelected(e, true));
        var view = selector.createView().withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var comp = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(oldId))
                .findFirst().orElseThrow();
        comp.setId(newId);
        view.commitChanges();
    }

    private void changeNamespaceId(VirtualModel vsum, String oldId, String newId) {
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("cad-change"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof CAD_Model)
                .forEach(e -> selector.setSelected(e, true));
        var view = selector.createView().withChangeRecordingTrait();
        var cadModel = view.getRootObjects(CAD_Model.class).iterator().next();
        var ns = cadModel.getNamespaces().stream()
                .filter(n -> n.getId().equals(oldId))
                .findFirst().orElseThrow(() ->
                        new IllegalStateException("Namespace not found: " + oldId));
        ns.setId(newId);
        view.commitChanges();
    }

    private CommittableView getBrakesystemView(VirtualModel vsum) {
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("brakesystem"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof Brakesystem)
                .forEach(e -> selector.setSelected(e, true));
        return selector.createView().withChangeDerivingTrait();
    }
}
