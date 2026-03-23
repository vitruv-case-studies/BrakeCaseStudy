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

    private CommittableView getBrakesystemView(VirtualModel vsum) {
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("brakesystem"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof Brakesystem)
                .forEach(e -> selector.setSelected(e, true));
        return selector.createView().withChangeDerivingTrait();
    }
}
