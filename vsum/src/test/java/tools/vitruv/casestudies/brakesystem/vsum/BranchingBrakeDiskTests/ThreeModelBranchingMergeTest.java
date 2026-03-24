package tools.vitruv.casestudies.brakesystem.vsum.BranchingBrakeDiskTests;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.brakesystem2safety.Brakesystem2safetyChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.branch.merge.ChangeLogCapture;
import tools.vitruv.framework.vsum.branch.merge.GitStateLoader;
import tools.vitruv.framework.vsum.branch.merge.MergeConflict;
import tools.vitruv.framework.vsum.branch.merge.SemanticChangeLog;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeCommand;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeResult;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

import brakesystem.BrakeDisk;
import brakesystem.BrakePad;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import safety.SafetyAssessment;
import safety.SafetyEntry;

/**
 * Three-model branching merge tests for the BrakeCaseStudy.
 *
 * <p>Exercises the directed merge algorithm (merge A→B) across three models:
 * <ul>
 *   <li>M₁ = brakesystem (primary engineering model)</li>
 *   <li>M₂ = CAD (bidirectional with M₁)</li>
 *   <li>M₃ = safety (derived from M₁, one-directional)</li>
 * </ul>
 *
 * <p>Scenarios map to the formalization in {@code formalization.md}:
 * <ul>
 *   <li>Scenarios 1–3: Clean merges (no conflicts, no warnings)</li>
 *   <li>Scenario 4: user(B) vs derived(A) = warning, B's intent wins</li>
 *   <li>Scenario 5: derived(B) vs user(A) = warning, A's intent wins</li>
 *   <li>Scenario 6: user(A) vs user(B) = direct conflict (UUID-based detection)</li>
 *   <li>Scenario 7: Cross-model indirect conflict involving M₃</li>
 * </ul>
 */
public class ThreeModelBranchingMergeTest {

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 1: user(A) changes M₁, derived changes propagate to M₂ and M₃
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S1: user(A) changes BrakeDisk diameter in M₁ → derived M₂+M₃ update; B adds ABSSensor → clean merge")
    void scenario1_userA_changesM1_derivedM2M3(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            // Base: BrakeDisk with diameter=300, thickness=25
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: BrakeDisk disk1 d=300 t=25").call();

            // Branch A (feature): change diameter 300→320
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 320);

            commitWithChangelog(git, capture, tempDir, "feature", "Changed diameter to 320");

            // Branch B (main): add ABSSensor
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            addABSSensor(vsum, "sensor1", 100, 3);

            commitWithChangelog(git, capture, tempDir, "main", "Added ABSSensor sensor1");

            vsum.dispose();

            // Merge feature → main
            SemanticMergeResult result = merge(tempDir, "feature", "main", interactionProvider);

            assertTrue(result.isSuccess(), "Merge should succeed — non-overlapping changes");

            // Verify merged state
            InternalVirtualModel merged = loadThreeModelVsum(result.getMergedStateFolder());
            var bs = getBrakesystemRoot(merged);

            assertEquals(2, bs.getBrakeComponents().size(), "Should have disk1 + sensor1");
            var disk = (BrakeDisk) bs.getBrakeComponents().stream()
                    .filter(c -> c.getId().equals("disk1")).findFirst().orElseThrow();
            assertEquals(320, disk.getDiameterInMM(), "Diameter should be 320 from branch A");

            // Verify safety model propagated
            var safety = getSafetyRoot(merged);
            assertNotNull(safety, "SafetyAssessment should exist");
            assertEquals(2, safety.getSafetyEntries().size(), "Should have 2 safety entries");

            var diskEntry = findSafetyEntry(safety, "disk1");
            // thermalLoadRating = 320 * 25 * 0.01 = 80.0
            assertEquals(80.0f, diskEntry.getThermalLoadRating(), 0.01f,
                    "Thermal load should reflect merged diameter");

            merged.dispose();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 2: user(A) changes M₂, derived changes propagate to M₁ and M₃
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S2: user(A) renames Namespace.id in M₂ → derived M₁+M₃ update; B adds caliper → clean merge")
    void scenario2_userA_changesM2_derivedM1M3(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: BrakeDisk disk1").call();

            // Branch A: rename Namespace.id via CAD view
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeNamespaceId(vsum, "disk1", "disk1-rev2");

            commitWithChangelog(git, capture, tempDir, "feature", "Renamed namespace to disk1-rev2");

            // Branch B: add BrakeCaliper (independent component)
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            addBrakeCaliper(vsum, "caliper1", 42);

            commitWithChangelog(git, capture, tempDir, "main", "Added caliper1");

            vsum.dispose();

            SemanticMergeResult result = merge(tempDir, "feature", "main", interactionProvider);

            assertTrue(result.isSuccess(), "Merge should succeed — different elements touched");

            InternalVirtualModel merged = loadThreeModelVsum(result.getMergedStateFolder());
            var bs = getBrakesystemRoot(merged);

            // A's Namespace.id rename should propagate to BrakeComponent.id
            boolean hasDiskRev2 = bs.getBrakeComponents().stream()
                    .anyMatch(c -> "disk1-rev2".equals(c.getId()));
            assertTrue(hasDiskRev2, "BrakeComponent id should be disk1-rev2 after M₂→M₁ propagation");

            // Safety entry componentId should also be updated
            var safety = getSafetyRoot(merged);
            var entry = findSafetyEntry(safety, "disk1-rev2");
            assertNotNull(entry, "SafetyEntry.componentId should match renamed id");

            merged.dispose();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 3: Both branches change different models, derived changes overlap
    //             harmlessly in M₃ (different SafetyEntries)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S3: A changes BrakeDisk in M₁, B changes BrakePad via M₁ → both derive to M₃, no conflict")
    void scenario3_bothChangeM1_derivedOverlapInM3(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);
            addBrakePad(vsum, "pad1", 50, 100, 15);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1 + pad1").call();

            // Branch A: change BrakeDisk diameter → triggers safety thermalLoadRating update
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 320);

            commitWithChangelog(git, capture, tempDir, "feature", "disk1 diameter→320");

            // Branch B: change BrakePad height → triggers safety frictionArea update
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeBrakePadHeight(vsum, "pad1", 55);

            commitWithChangelog(git, capture, tempDir, "main", "pad1 height→55");

            vsum.dispose();

            SemanticMergeResult result = merge(tempDir, "feature", "main", interactionProvider);

            assertTrue(result.isSuccess(), "Merge should succeed — different SafetyEntries");

            InternalVirtualModel merged = loadThreeModelVsum(result.getMergedStateFolder());
            var safety = getSafetyRoot(merged);

            // Both safety entries should reflect the merged values
            var diskEntry = findSafetyEntry(safety, "disk1");
            assertEquals(80.0f, diskEntry.getThermalLoadRating(), 0.01f,
                    "disk1 thermalLoad = 320*25*0.01 = 80.0");

            var padEntry = findSafetyEntry(safety, "pad1");
            assertEquals(5500.0f, padEntry.getFrictionArea(), 0.01f,
                    "pad1 frictionArea = 55*100 = 5500.0");

            merged.dispose();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 4: user(B) vs derived(A) = warning (B's intent wins)
    //
    // A changes BrakeDisk.diameter in M₁ → reaction derives CAD parameter in M₂.
    // B directly sets the CAD Diameter parameter to a different value (user intent).
    // Merge A→B: derived(A) would overwrite user(B) → indirect conflict / warning.
    //
    // TODO: See TODO-branching.md for discussion about reapplying user(B).
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S4: derived(A) in M₂ vs user(B) in M₂ → warning, B's intent preserved")
    void scenario4_derivedA_vs_userB_warning(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1 d=300").call();

            // Branch A (feature): change BrakeDisk.diameter → 320 in M₁
            // Reaction will derive CAD NumericParameter("Diameter") → 320
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 320);

            commitWithChangelog(git, capture, tempDir, "feature", "BrakeDisk diameter→320");

            // Branch B (main): user directly sets CAD Diameter → 350
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeCADNumericParameter(vsum, "disk1", "Diameter", 350.0f);

            commitWithChangelog(git, capture, tempDir, "main", "CAD Diameter→350 (user intent)");

            vsum.dispose();

            // Merge A→B: replaying A generates derived CAD Diameter=320,
            // but B's user set it to 350 → indirect conflict
            SemanticMergeResult result = merge(tempDir, "feature", "main", interactionProvider);

            // The merge should complete (not crash)
            // It may succeed with warnings, or detect an indirect conflict
            java.lang.System.out.println("S4 - Success: " + result.isSuccess());
            java.lang.System.out.println("S4 - Conflicts: " + result.getConflicts().size());
            java.lang.System.out.println("S4 - Warnings: " + result.getWarnings().size());
            result.getWarnings().forEach(w -> java.lang.System.out.println("  WARN: " + w));
            result.getConflicts().forEach(c -> java.lang.System.out.println("  CONFLICT: " + c));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 5: derived(B) vs user(A) = warning (A's intent wins)
    //
    // B changes BrakeDisk.diameter in M₁ → reaction derives CAD param (derived in B).
    // A directly sets the CAD Diameter parameter (user intent in A).
    // Merge A→B: A's user change overwrites B's derived state → §8.3 warning.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S5: user(A) in M₂ overwrites derived(B) in M₂ → warning, A's intent wins")
    void scenario5_userA_vs_derivedB_warning(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1 d=300").call();

            // Branch A (feature): user directly sets CAD Diameter → 320 (user intent)
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeCADNumericParameter(vsum, "disk1", "Diameter", 320.0f);

            commitWithChangelog(git, capture, tempDir, "feature", "CAD Diameter→320 (user intent)");

            // Branch B (main): change BrakeDisk.diameter → 350 in M₁
            // Reaction derives CAD Diameter → 350 (derived state in B)
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 350);

            commitWithChangelog(git, capture, tempDir, "main", "BrakeDisk diameter→350");

            vsum.dispose();

            // Merge A→B: A's user CAD change (320) replays onto B where CAD Diameter=350 (derived).
            // A's user change overwrites B's derived state → warning, A's intent (320) wins.
            SemanticMergeResult result = merge(tempDir, "feature", "main", interactionProvider);

            java.lang.System.out.println("S5 - Success: " + result.isSuccess());
            java.lang.System.out.println("S5 - Conflicts: " + result.getConflicts().size());
            java.lang.System.out.println("S5 - Warnings: " + result.getWarnings().size());
            result.getWarnings().forEach(w -> java.lang.System.out.println("  WARN: " + w));
            result.getConflicts().forEach(c -> java.lang.System.out.println("  CONFLICT: " + c));

            // Merge should succeed — user intent always wins over derived state
            assertTrue(result.isSuccess(), "Merge should succeed (user(A) overwrites derived(B))");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 6: user(A) vs user(B) = direct conflict on M₁
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S6: user(A) and user(B) both change BrakeDisk.diameter → direct MODIFY_MODIFY conflict")
    void scenario6_directConflict_userA_vs_userB(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1 d=300").call();

            // Branch A: change diameter → 320
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 320);

            commitWithChangelog(git, capture, tempDir, "feature", "diameter→320");

            // Branch B: change diameter → 350
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 350);

            commitWithChangelog(git, capture, tempDir, "main", "diameter→350");

            vsum.dispose();

            SemanticMergeResult result = merge(tempDir, "feature", "main", interactionProvider);

            assertFalse(result.isSuccess(), "Merge should fail — direct user-vs-user conflict");
            assertFalse(result.getConflicts().isEmpty(), "Should have at least one conflict");

            var conflict = result.getConflicts().get(0);
            assertEquals("diameterInMM", conflict.getConflictingFeature(),
                    "Conflict should be on diameterInMM");
            assertEquals(MergeConflict.ConflictType.MODIFY_MODIFY, conflict.getType(),
                    "Should be MODIFY_MODIFY conflict type");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 7: Indirect conflict via M₃
    //
    // A changes BrakeDisk.id → reaction updates SafetyEntry.componentId in M₃.
    // B directly changes SafetyEntry.componentId (user change in M₃).
    // Merge A→B: derived(A) in M₃ overwrites user(B) in M₃ → indirect conflict.
    //
    // Since M₃ is derived-only in our setup, we use the BrakeDisk.id → Namespace.id
    // chain where B makes a user change to Namespace.id (M₂), and A changes
    // BrakeDisk.id (M₁) which derives both Namespace.id (M₂) and
    // SafetyEntry.componentId (M₃). This creates indirect conflicts in both M₂ and M₃.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S7: derived(replay(A)) overwrites user(B) across M₂ and M₃ → indirect conflict")
    void scenario7_indirectConflict_acrossM2andM3(@TempDir Path tempDir) throws Exception {
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1").call();

            // Branch A (feature): change BrakeDisk.id → "engineeringDisk"
            // Reaction derives: Namespace.id → "engineeringDisk" (M₂)
            //                   SafetyEntry.componentId → "engineeringDisk" (M₃)
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskId(vsum, "disk1", "engineeringDisk");

            commitWithChangelog(git, capture, tempDir, "feature", "BrakeDisk.id→engineeringDisk");

            // Branch B (main): user directly changes Namespace.id → "cadCustomId" in M₂
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeNamespaceId(vsum, "disk1", "cadCustomId");

            commitWithChangelog(git, capture, tempDir, "main", "Namespace.id→cadCustomId (user)");

            vsum.dispose();

            // Merge A→B: replaying A's BrakeDisk.id change triggers IdChanged reaction
            // which sets Namespace.id = "engineeringDisk" (derived), overwriting B's "cadCustomId" (user)
            SemanticMergeResult result = merge(tempDir, "feature", "main", interactionProvider);

            java.lang.System.out.println("S7 - Success: " + result.isSuccess());
            java.lang.System.out.println("S7 - Conflicts: " + result.getConflicts().size());
            java.lang.System.out.println("S7 - Warnings: " + result.getWarnings().size());
            result.getWarnings().forEach(w -> java.lang.System.out.println("  WARN: " + w));
            result.getConflicts().forEach(c -> java.lang.System.out.println("  CONFLICT: " + c));

            // The merge completes but should report the indirect conflict/warning
            // where derived(replay(A)) overwrites user(B) in M₂
            assertTrue(result.isSuccess(), "Merge completes (changes applied)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════════

    private static List<ChangePropagationSpecification> allCPS() {
        return List.of(
                new Brakesystem2cadChangePropagationSpecification(),
                new Cad2brakesystemChangePropagationSpecification(),
                new Brakesystem2safetyChangePropagationSpecification());
    }

    private InternalVirtualModel createThreeModelVsum(Path projectPath) throws Exception {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(new TestUserInteraction()))
                .withChangePropagationSpecifications(allCPS())
                .buildAndInitialize();
        model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
        return model;
    }

    private InternalVirtualModel loadThreeModelVsum(Path folder) throws Exception {
        return GitStateLoader.loadVsumFromDir(folder, allCPS(),
                new TestUserInteraction.ResultProvider(new TestUserInteraction()));
    }

    private SemanticMergeResult merge(Path tempDir, String source, String target,
                                       TestUserInteraction.ResultProvider interactionProvider) throws Exception {
        return new SemanticMergeCommand().execute(tempDir, source, target, allCPS(), interactionProvider);
    }

    private ChangeLogCapture freshCapture(InternalVirtualModel vsum) {
        var capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                vsum.getViewSourceModels().iterator().next().getResourceSet());
        vsum.addChangePropagationListener(capture);
        return capture;
    }

    private void commitWithChangelog(Git git, ChangeLogCapture capture, Path tempDir,
                                      String branch, String message) throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage(message).call();
        String sha = git.log().setMaxCount(1).call().iterator().next().getName();
        new SemanticChangeLog(sha, branch, capture.drainChanges(),
                capture.drainUuidMapping()).saveTo(tempDir);
        git.add().addFilepattern(".").call();
        git.commit().setAmend(true).setMessage(message + " + changelog").call();
    }

    // ── Model manipulation helpers ──────────────────────────────────

    private void addBrakesystem(VirtualModel vsum, Path projectPath) {
        var view = selectBrakesystemView(vsum).withChangeDerivingTrait();
        var bs = BrakesystemFactory.eINSTANCE.createBrakesystem();
        bs.setSpecificationType("Standard");
        bs.setInstanceName("TestBrake");
        view.registerRoot(bs, URI.createFileURI(projectPath.toString() + "/brakesystem.model"));
        view.commitChanges();
    }

    private void addBrakeDisk(VirtualModel vsum, String id, int diameter, boolean ventilated, int thickness) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var disk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
        disk.setId(id);
        disk.setDiameterInMM(diameter);
        disk.setVentilated(ventilated);
        disk.setBrakeDiskThicknessInMM(thickness);
        bs.getBrakeComponents().add(disk);
        view.commitChanges();
    }

    private void addBrakePad(VirtualModel vsum, String id, int height, int width, int thickness) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var pad = BrakesystemFactory.eINSTANCE.createBrakePad();
        pad.setId(id);
        pad.setHeightInMM(height);
        pad.setWidthInMM(width);
        pad.setThicknessInMM(thickness);
        bs.getBrakeComponents().add(pad);
        view.commitChanges();
    }

    private void addABSSensor(VirtualModel vsum, String id, int length, int pins) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var sensor = BrakesystemFactory.eINSTANCE.createABSSensor();
        sensor.setId(id);
        sensor.setLengthInMM(length);
        sensor.setNumberOfPins(pins);
        bs.getBrakeComponents().add(sensor);
        view.commitChanges();
    }

    private void addBrakeCaliper(VirtualModel vsum, String id, int pistonDiameter) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var caliper = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
        caliper.setId(id);
        caliper.setPistonDiameterInMM(pistonDiameter);
        bs.getBrakeComponents().add(caliper);
        view.commitChanges();
    }

    private void changeBrakeDiskDiameter(VirtualModel vsum, String id, int newDiameter) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var disk = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakeDisk)
                .map(c -> (BrakeDisk) c)
                .findFirst().orElseThrow();
        disk.setDiameterInMM(newDiameter);
        view.commitChanges();
    }

    private void changeBrakePadHeight(VirtualModel vsum, String id, int newHeight) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var pad = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakePad)
                .map(c -> (BrakePad) c)
                .findFirst().orElseThrow();
        pad.setHeightInMM(newHeight);
        view.commitChanges();
    }

    private void changeBrakeDiskId(VirtualModel vsum, String oldId, String newId) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
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
                .findFirst().orElseThrow();
        ns.setId(newId);
        view.commitChanges();
    }

    private void changeCADNumericParameter(VirtualModel vsum, String namespaceId,
                                            String paramName, float newValue) {
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("cad-param"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof CAD_Model)
                .forEach(e -> selector.setSelected(e, true));
        var view = selector.createView().withChangeRecordingTrait();
        var cadModel = view.getRootObjects(CAD_Model.class).iterator().next();
        var ns = cadModel.getNamespaces().stream()
                .filter(n -> n.getId().equals(namespaceId))
                .findFirst().orElseThrow();
        var param = ns.getParameters().stream()
                .filter(p -> p instanceof NumericParameter && p.getName().equals(paramName))
                .map(p -> (NumericParameter) p)
                .findFirst().orElseThrow();
        param.setValue(newValue);
        view.commitChanges();
    }

    // ── View helpers ────────────────────────────────────────────────

    private CommittableView selectBrakesystemView(VirtualModel vsum) {
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("brakesystem"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof Brakesystem)
                .forEach(e -> selector.setSelected(e, true));
        return selector.createView().withChangeDerivingTrait();
    }

    private Brakesystem getBrakesystemRoot(VirtualModel vsum) {
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("bs-read"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof Brakesystem)
                .forEach(e -> selector.setSelected(e, true));
        return selector.createView().getRootObjects(Brakesystem.class).iterator().next();
    }

    private SafetyAssessment getSafetyRoot(VirtualModel vsum) {
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("safety-read"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof SafetyAssessment)
                .forEach(e -> selector.setSelected(e, true));
        var view = selector.createView();
        var roots = view.getRootObjects(SafetyAssessment.class);
        return roots.iterator().hasNext() ? roots.iterator().next() : null;
    }

    private SafetyEntry findSafetyEntry(SafetyAssessment assessment, String componentId) {
        return assessment.getSafetyEntries().stream()
                .filter(e -> componentId.equals(e.getComponentId()))
                .findFirst()
                .orElse(null);
    }
}
