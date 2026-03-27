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
import tools.vitruv.framework.vsum.branch.merge.MergeTracer;
import tools.vitruv.framework.vsum.branch.merge.SemanticChangeLog;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeCommand;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeResult;
import tools.vitruv.framework.vsum.branch.merge.SemanticMergeResult.MergeDirection;
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
        // Write trace logs into merge-traces/ under the BrakeCaseStudy project
        MergeTracer.setOutputDirectory(Path.of("merge-traces"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 1: user(A) changes M₁, derived changes propagate to M₂ and M₃
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S1: user(A) changes BrakeDisk diameter in M₁ → derived M₂+M₃ update; B adds ABSSensor → clean merge")
    void scenario1_userA_changesM1_derivedM2M3(@TempDir Path tempDir) throws Exception {
        printScenarioHeader("S1", "Clean merge — diameter change + sensor addition",
                "user(A) changes BrakeDisk.diameter 300→320 in M₁ (derives M₂ CAD + M₃ Safety);\n"
                + "║  user(B) adds ABSSensor in M₁. Non-overlapping user changes.",
                "SUCCESS, 0 blocking conflicts, 1 warning (user-vs-derived on CAD).\n"
                + "║  Merged state: disk1.diameter=320, sensor1 present, safety thermalLoad=80.0");
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
            printScenarioResult("S1", result);

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
        printScenarioHeader("S2", "Clean merge — namespace rename + caliper addition",
                "user(A) renames Namespace.id 'disk1'→'disk1-rev2' in M₂ (derives M₁+M₃);\n"
                + "║  user(B) adds BrakeCaliper in M₁. Non-overlapping user changes.",
                "SUCCESS, 0 blocking conflicts, 1 warning.\n"
                + "║  Merged state: component id='disk1-rev2', caliper1 present, safety componentId updated");
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
            printScenarioResult("S2", result);

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
        printScenarioHeader("S3", "Clean merge — disk + pad changes, both derive to M₃",
                "user(A) changes BrakeDisk.diameter 300→320 in M₁ (derives M₃ thermalLoadRating);\n"
                + "║  user(B) changes BrakePad.height 50→55 in M₁ (derives M₃ frictionArea).\n"
                + "║  Both derive to different SafetyEntries in M₃ — no overlap.",
                "SUCCESS, 0 blocking conflicts, 1 warning.\n"
                + "║  Merged: disk1 thermalLoad=80.0, pad1 frictionArea=5500.0");
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
            printScenarioResult("S3", result);

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
        printScenarioHeader("S4", "Indirect conflict — derived(A) vs user(B) in M₂",
                "user(A) changes BrakeDisk.diameter 300→320 in M₁ → reaction derives CAD Diameter=320;\n"
                + "║  user(B) directly sets CAD Diameter=350 in M₂ (user intent).\n"
                + "║  Merge A→B: replay(A) derives CAD Diameter=320, overwriting user(B)'s 350.",
                "SUCCESS with INDIRECT_CONFLICT warning(s) (2 warnings).\n"
                + "║  Vitruvius detects derived(A) overwriting user(B). EMF Compare: 1 hard conflict.");
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
            // but B's user set it to 350 → indirect conflict (derived(A) vs user(B))
            SemanticMergeResult result = merge(tempDir, "feature", "main", interactionProvider);
            printScenarioResult("S4", result);

            // Merge succeeds (directed merge — A's changes are applied)
            assertTrue(result.isSuccess(), "Merge should succeed in directed merge");

            // Should have indirect conflict warnings: derived(replay(A)) overwrites user(B)
            // on the CAD Diameter parameter (and possibly safety thermalLoadRating)
            assertFalse(result.getWarnings().isEmpty(),
                    "Should have warnings for derived(A) overwriting user(B)");

            boolean hasIndirectConflict = result.getWarnings().stream()
                    .anyMatch(w -> w.getType() == MergeConflict.ConflictType.INDIRECT_CONFLICT);
            assertTrue(hasIndirectConflict,
                    "Should have INDIRECT_CONFLICT warning (derived(A) overwrites user(B))");
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
        printScenarioHeader("S5", "User vs derived — user(A) overwrites derived(B) in M₂",
                "user(A) directly sets CAD Diameter=320 in M₂ (user intent);\n"
                + "║  user(B) changes BrakeDisk.diameter 300→350 in M₁ → reaction derives CAD Diameter=350.\n"
                + "║  Merge A→B: A's user CAD change (320) overwrites B's derived state (350).",
                "SUCCESS with USER_VS_DERIVED_WARNING (1 warning).\n"
                + "║  User(A) intent wins over derived(B). EMF Compare: 1 hard conflict.");
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
            printScenarioResult("S5", result);

            // Merge should succeed — user intent always wins over derived state
            assertTrue(result.isSuccess(), "Merge should succeed (user(A) overwrites derived(B))");

            // Should have USER_VS_DERIVED_WARNING: A's user change to CAD parameter
            // overwrites B's derived value (set by brakesystem2cad reaction on B)
            assertFalse(result.getWarnings().isEmpty(),
                    "Should have warnings for user(A) overwriting derived(B)");

            boolean hasUserVsDerived = result.getWarnings().stream()
                    .anyMatch(w -> w.getType() == MergeConflict.ConflictType.USER_VS_DERIVED_WARNING);
            assertTrue(hasUserVsDerived,
                    "Should have USER_VS_DERIVED_WARNING (A's user change overwrites B's reaction state)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 6: user(A) vs user(B) = direct conflict on M₁
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S6: user(A) and user(B) both change BrakeDisk.diameter → direct MODIFY_MODIFY conflict")
    void scenario6_directConflict_userA_vs_userB(@TempDir Path tempDir) throws Exception {
        printScenarioHeader("S6", "Direct conflict — user(A) vs user(B) on same attribute",
                "user(A) changes BrakeDisk.diameter 300→320 in M₁;\n"
                + "║  user(B) changes BrakeDisk.diameter 300→350 in M₁.\n"
                + "║  Same element, same feature, different values → MODIFY_MODIFY.",
                "CONFLICT with 1 blocking MODIFY_MODIFY conflict on 'diameterInMM'.\n"
                + "║  EMF Compare: 3 conflicts (brakesystem + derived CAD + derived safety).");
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
            printScenarioResult("S6", result);

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
        printScenarioHeader("S7", "Cross-model indirect conflict via id rename cascade",
                "user(A) renames BrakeDisk.id 'disk1'→'engineeringDisk' in M₁\n"
                + "║    → reaction derives Namespace.id (M₂) and SafetyEntry.componentId (M₃);\n"
                + "║  user(B) directly sets Namespace.id='cadCustomId' in M₂ (user intent).\n"
                + "║  Merge A→B: derived(A) Namespace.id overwrites user(B) in M₂.",
                "SUCCESS with INDIRECT_CONFLICT warning(s) (2 warnings).\n"
                + "║  EMF Compare: 5 hard conflicts (brakesystem + cad + safety).");
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
            printScenarioResult("S7", result);

            // The merge completes but should report indirect conflicts/warnings
            assertTrue(result.isSuccess(), "Merge completes (changes applied)");

            // Should have INDIRECT_CONFLICT warnings: derived(replay(A)) overwrites user(B)
            // A's BrakeDisk.id change → reaction sets Namespace.id (M₂) and SafetyEntry.componentId (M₃),
            // overwriting B's user-authored Namespace.id = "cadCustomId"
            assertFalse(result.getWarnings().isEmpty(),
                    "Should have warnings for derived(replay(A)) overwriting user(B)");

            boolean hasIndirectConflict = result.getWarnings().stream()
                    .anyMatch(w -> w.getType() == MergeConflict.ConflictType.INDIRECT_CONFLICT);
            assertTrue(hasIndirectConflict,
                    "Should have INDIRECT_CONFLICT warning (derived(A) overwrites user(B) in M₂/M₃)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 8: Bidirectional merge — reverse resolves S4's indirect conflict
    //
    // Same setup as S4: A changes BrakeDisk.diameter (M₁), B sets CAD Diameter (M₂).
    // A→B has INDIRECT_CONFLICT (derived(A) overwrites user(B) in M₂).
    // B→A: replaying B's CAD parameter change onto A. If no reaction fires for
    // M₂→M₁ on parameter value change, B→A is clean → use reversed result.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S8: bidirectional merge reverses S4 — derived(A) vs user(B) resolved via B→A")
    void scenario8_bidirectional_reversesS4(@TempDir Path tempDir) throws Exception {
        printScenarioHeader("S8", "Bidirectional merge — reverse resolves S4's indirect conflict",
                "Same setup as S4: A changes diameter in M₁, B sets CAD Diameter in M₂.\n"
                + "║  A→B has INDIRECT_CONFLICT (derived(A) overwrites user(B) in M₂).\n"
                + "║  B→A: replaying B's CAD change onto A — no reaction interference → clean.",
                "SUCCESS with direction=REVERSED.\n"
                + "║  The bidirectional fallback resolves the indirect conflict by trying B→A.");
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1 d=300").call();

            // Branch A: change BrakeDisk.diameter → 320 in M₁
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 320);

            commitWithChangelog(git, capture, tempDir, "feature", "BrakeDisk diameter→320");

            // Branch B: user directly sets CAD Diameter → 350
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeCADNumericParameter(vsum, "disk1", "Diameter", 350.0f);

            commitWithChangelog(git, capture, tempDir, "main", "CAD Diameter→350 (user intent)");

            vsum.dispose();

            // Bidirectional merge: A→B has indirect conflict, B→A should be clean
            SemanticMergeResult result = mergeBidirectional(
                    tempDir, "feature", "main", interactionProvider);
            printScenarioResult("S8", result);

            assertTrue(result.isSuccess(),
                    "Bidirectional merge should succeed via reverse direction");
            assertEquals(MergeDirection.REVERSED, result.getMergeDirection(),
                    "Should use REVERSED direction since A→B had indirect conflicts");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 9: Bidirectional merge — both directions have indirect conflicts
    //
    // A changes BrakeDisk.id in M₁ → reaction derives Namespace.id in M₂.
    // B changes Namespace.id in M₂ → reaction derives BrakeComponent.id in M₁.
    // A→B: derived(A) Namespace.id overwrites user(B) Namespace.id → INDIRECT
    // B→A: derived(B) BrakeComponent.id overwrites user(A) BrakeDisk.id → INDIRECT
    // → BIDIRECTIONAL_INDIRECT_CONFLICT (true conflict)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S9: bidirectional — both directions have indirect conflicts → true conflict")
    void scenario9_bidirectional_bothDirectionsConflict(@TempDir Path tempDir) throws Exception {
        printScenarioHeader("S9", "Bidirectional — both directions have indirect conflicts",
                "user(A) renames BrakeDisk.id 'disk1'→'diskA' in M₁ → derives Namespace.id in M₂;\n"
                + "║  user(B) renames Namespace.id 'disk1'→'nsB' in M₂ → derives BrakeComponent.id in M₁.\n"
                + "║  A→B: derived(A) Namespace.id='diskA' overwrites user(B) 'nsB' → INDIRECT.\n"
                + "║  B→A: derived(B) BrakeComponent.id='nsB' overwrites user(A) 'diskA' → INDIRECT.",
                "CONFLICT with BIDIRECTIONAL_INDIRECT_CONFLICT.\n"
                + "║  Both directions produce indirect conflicts — true semantic conflict, requires user.");
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1").call();

            // Branch A: change BrakeDisk.id → "diskA" in M₁
            // Reaction derives: Namespace.id → "diskA" (M₂)
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskId(vsum, "disk1", "diskA");

            commitWithChangelog(git, capture, tempDir, "feature", "BrakeDisk.id→diskA");

            // Branch B: change Namespace.id → "nsB" in M₂
            // Reaction derives: BrakeComponent.id → "nsB" (M₁)
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeNamespaceId(vsum, "disk1", "nsB");

            commitWithChangelog(git, capture, tempDir, "main", "Namespace.id→nsB");

            vsum.dispose();

            // Bidirectional merge:
            // A→B: derived(A) Namespace.id="diskA" overwrites user(B) "nsB" → INDIRECT
            // B→A: derived(B) BrakeComponent.id="nsB" overwrites user(A) "diskA" → INDIRECT
            // → Both directions conflict → BIDIRECTIONAL_INDIRECT_CONFLICT
            SemanticMergeResult result = mergeBidirectional(
                    tempDir, "feature", "main", interactionProvider);
            printScenarioResult("S9", result);

            assertFalse(result.isSuccess(),
                    "Both directions have indirect conflicts → should report CONFLICT");
            assertFalse(result.getConflicts().isEmpty(),
                    "Should have BIDIRECTIONAL_INDIRECT_CONFLICT(s)");

            boolean hasBidirectionalConflict = result.getConflicts().stream()
                    .anyMatch(c -> c.getType() == MergeConflict.ConflictType.BIDIRECTIONAL_INDIRECT_CONFLICT);
            assertTrue(hasBidirectionalConflict,
                    "Should have BIDIRECTIONAL_INDIRECT_CONFLICT type");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 10: Interleaving merge succeeds where A→B has indirect conflict
    //
    // Same setup as S4/S8: A changes BrakeDisk.diameter → reaction derives CAD Diameter.
    // B directly sets CAD Diameter (user intent).
    // A→B: INDIRECT_CONFLICT (derived(A) overwrites user(B)).
    // Interleaving: a clean ordering exists (either A-first-from-base or B-first) → SUCCESS.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S10: interleaving merge succeeds where A→B has indirect conflict")
    void scenario10_interleaving_succeedsWhereDirectedFails(@TempDir Path tempDir) throws Exception {
        printScenarioHeader("S10", "Interleaving — resolves indirect conflict via commit ordering",
                "Same as S4/S8: A changes BrakeDisk.diameter in M\u2081 \u2192 derives CAD Diameter;\n"
                + "\u2551  B directly sets CAD Diameter in M\u2082 (user intent).\n"
                + "\u2551  A\u2192B: INDIRECT_CONFLICT. Interleaving tries all orderings from base.",
                "SUCCESS with direction=INTERLEAVED (or REVERSED).\n"
                + "\u2551  At least one ordering avoids indirect conflicts.");
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1 d=300").call();

            // Branch A: change BrakeDisk.diameter -> 320 in M1
            // Reaction derives CAD NumericParameter("Diameter") -> 320
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 320);

            commitWithChangelog(git, capture, tempDir, "feature", "BrakeDisk diameter->320");

            // Branch B: user directly sets CAD Diameter -> 350
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeCADNumericParameter(vsum, "disk1", "Diameter", 350.0f);

            commitWithChangelog(git, capture, tempDir, "main", "CAD Diameter->350 (user intent)");

            vsum.dispose();

            // Interleaving merge: tries all orderings from base
            SemanticMergeResult result = mergeWithInterleaving(
                    tempDir, "feature", "main", interactionProvider);
            printScenarioResult("S10", result);

            assertTrue(result.isSuccess(),
                    "Interleaving merge should succeed - at least one ordering is clean");

            boolean hasIndirectConflict = result.getWarnings().stream()
                    .anyMatch(w -> w.getType() == MergeConflict.ConflictType.INDIRECT_CONFLICT);
            assertFalse(hasIndirectConflict,
                    "The chosen ordering should have no INDIRECT_CONFLICT warnings");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 11: Interleaving merge fails — no ordering resolves bidirectional conflict
    //
    // Same setup as S9: A renames BrakeDisk.id, B renames Namespace.id.
    // Both directions cascade: no interleaving avoids indirect conflicts.
    // -> INTERLEAVING_CONFLICT
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S11: interleaving merge fails — no ordering resolves bidirectional id rename conflict")
    void scenario11_interleaving_failsWhenNoOrderingWorks(@TempDir Path tempDir) throws Exception {
        printScenarioHeader("S11", "Interleaving — fails with INTERLEAVING_CONFLICT",
                "Same as S9: A renames BrakeDisk.id \u2192 'diskA' (derives Namespace.id);\n"
                + "\u2551  B renames Namespace.id \u2192 'nsB' (derives BrakeComponent.id).\n"
                + "\u2551  Both orderings [A,B] and [B,A] produce indirect conflicts.",
                "CONFLICT with INTERLEAVING_CONFLICT.\n"
                + "\u2551  No commit ordering avoids indirect conflicts.");
        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1").call();

            // Branch A: change BrakeDisk.id -> "diskA" in M1
            // Reaction derives: Namespace.id -> "diskA" (M2)
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskId(vsum, "disk1", "diskA");

            commitWithChangelog(git, capture, tempDir, "feature", "BrakeDisk.id->diskA");

            // Branch B: change Namespace.id -> "nsB" in M2
            // Reaction derives: BrakeComponent.id -> "nsB" (M1)
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeNamespaceId(vsum, "disk1", "nsB");

            commitWithChangelog(git, capture, tempDir, "main", "Namespace.id->nsB");

            vsum.dispose();

            // Interleaving merge: all orderings produce indirect conflicts
            SemanticMergeResult result = mergeWithInterleaving(
                    tempDir, "feature", "main", interactionProvider);
            printScenarioResult("S11", result);

            assertFalse(result.isSuccess(),
                    "Interleaving merge should fail - no ordering resolves bidirectional id rename");
            assertFalse(result.getConflicts().isEmpty(),
                    "Should have INTERLEAVING_CONFLICT(s)");

            boolean hasInterleavingConflict = result.getConflicts().stream()
                    .anyMatch(c -> c.getType() == MergeConflict.ConflictType.INTERLEAVING_CONFLICT);
            assertTrue(hasInterleavingConflict,
                    "Should have INTERLEAVING_CONFLICT type");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario 12: Interleaving — guard failure causes INTERLEAVING_CONFLICT
    //
    // Base: BrakeDisk 'disk1' (M1) + CAD Namespace 'disk1' (M2, reaction-derived).
    // Branch A (feature, 2 commits):
    //   a_1: rename BrakeDisk.id 'disk1' → 'diskA'
    //        Reaction: Namespace.id → 'diskA'
    //   a_2: delete BrakeDisk 'diskA' from M1
    //        Reaction: delete Namespace 'diskA' from M2 (cascade)
    // Branch B (main, 1 commit):
    //   b_1: directly rename Namespace.id 'disk1' → 'diskB' (M2)
    //        Reaction (cad2brakesystem): BrakeComponent.id → 'diskB'
    //
    // Three orderings with m=2, n=1 are tried:
    //   [a_1, a_2, b_1]: namespace deleted by a_2; b_1 can't access it
    //                    → replay-applicability conflict (guard failure) → skipped.
    //   [a_1, b_1, a_2]: a_1 sets disk1.id='diskA'; b_1 reaction sets disk1.id='diskB'
    //                    → indirect conflict (b_1 reaction overwrites a_1 user change).
    //   [b_1, a_1, a_2]: b_1 sets namespace.id='diskB'; a_1 reaction sets namespace.id='diskA'
    //                    → indirect conflict (a_1 reaction overwrites b_1 user change).
    // All orderings fail → INTERLEAVING_CONFLICT.
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("S12: interleaving — guard failure + indirect conflicts cause INTERLEAVING_CONFLICT")
    void scenario12_interleaving_guardFailureCausesInterleavingConflict(@TempDir Path tempDir) throws Exception {
        printScenarioHeader("S12", "Interleaving — guard failure → INTERLEAVING_CONFLICT",
                "Base: disk1 in M1, CAD Namespace 'disk1' in M2.\n"
                + "║  Branch A (feature, 2 commits):\n"
                + "║    a_1: rename BrakeDisk.id 'disk1'→'diskA' (reaction: Namespace.id='diskA').\n"
                + "║    a_2: delete BrakeDisk 'diskA' (reaction: delete Namespace 'diskA').\n"
                + "║  Branch B (main, 1 commit):\n"
                + "║    b_1: rename Namespace.id 'disk1'→'diskB' (reaction: BrakeComponent.id='diskB').\n"
                + "║  [a_1,a_2,b_1]: namespace deleted before b_1 → guard failure → skipped.\n"
                + "║  [a_1,b_1,a_2]: b_1 reaction overwrites a_1 user BrakeDisk.id → INDIRECT_CONFLICT.\n"
                + "║  [b_1,a_1,a_2]: a_1 reaction overwrites b_1 user Namespace.id → INDIRECT_CONFLICT.",
                "INTERLEAVING_CONFLICT — no ordering avoids the conflict.");

        var interactionProvider = new TestUserInteraction.ResultProvider(new TestUserInteraction());

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1").call();

            // Branch A: a_1 rename disk1→diskA, a_2 delete diskA
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskId(vsum, "disk1", "diskA");

            commitWithChangelog(git, capture, tempDir, "feature", "BrakeDisk.id: disk1→diskA");

            capture = freshCapture(vsum);

            removeBrakeDisk(vsum, "diskA");

            commitWithChangelog(git, capture, tempDir, "feature", "Delete diskA");

            // Branch B: b_1 rename Namespace.id disk1→diskB directly in M2
            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeNamespaceId(vsum, "disk1", "diskB");

            commitWithChangelog(git, capture, tempDir, "main", "Namespace.id: disk1→diskB");

            vsum.dispose();

            // Interleaving merge: all 3 orderings fail
            SemanticMergeResult result = mergeWithInterleaving(
                    tempDir, "feature", "main", interactionProvider);
            printScenarioResult("S12", result);

            assertFalse(result.isSuccess(),
                    "Interleaving merge should fail — no ordering is conflict-free");
            assertFalse(result.getConflicts().isEmpty(),
                    "Should have INTERLEAVING_CONFLICT(s)");

            boolean hasInterleavingConflict = result.getConflicts().stream()
                    .anyMatch(c -> c.getType() == MergeConflict.ConflictType.INTERLEAVING_CONFLICT);
            assertTrue(hasInterleavingConflict,
                    "Should have INTERLEAVING_CONFLICT type");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Trace output helpers
    // ═══════════════════════════════════════════════════════════════════

    private static final String CASE_STUDY_NAME = "BrakeCaseStudy";

    private static void printScenarioHeader(String scenario, String title, String description, String expected) {
        MergeTracer.init(CASE_STUDY_NAME, scenario);
        MergeTracer.trace("");
        MergeTracer.boxStart(scenario + ": " + title);
        MergeTracer.boxLine("");
        for (String line : description.split("\n")) {
            // Strip leading "║  " if present (from multi-line string literals)
            String cleaned = line.startsWith("║  ") ? line.substring(3) : line;
            MergeTracer.boxLine(cleaned);
        }
        MergeTracer.boxLine("");
        for (String line : ("EXPECTED: " + expected).split("\n")) {
            String cleaned = line.startsWith("║  ") ? line.substring(3) : line;
            MergeTracer.boxLine(cleaned);
        }
        MergeTracer.boxEnd();
        MergeTracer.trace("");
    }

    private static void printScenarioResult(String scenario, SemanticMergeResult result) {
        MergeTracer.trace("");
        MergeTracer.resultStart(scenario + " — ACTUAL RESULT: " + result.getStatus());
        if (result.isSuccess()) {
            MergeTracer.resultLine("  Changes applied: " + result.getAppliedChanges().size());
            MergeTracer.resultLine("  Direction: " + result.getMergeDirection());
        }
        if (!result.getConflicts().isEmpty()) {
            MergeTracer.resultLine("  Blocking conflicts: " + result.getConflicts().size());
            for (var c : result.getConflicts()) {
                MergeTracer.resultLine("    - " + c.getType() + " on '" + c.getConflictingFeature()
                        + "' (ours=" + c.getOursValue() + ", theirs=" + c.getTheirsValue() + ")");
            }
        }
        if (!result.getWarnings().isEmpty()) {
            MergeTracer.resultLine("  Warnings: " + result.getWarnings().size());
            for (var w : result.getWarnings()) {
                MergeTracer.resultLine("    - " + w.getType() + " on '" + w.getConflictingFeature() + "'");
            }
        }
        MergeTracer.resultEnd();
        MergeTracer.trace("");
        MergeTracer.close();
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

    private SemanticMergeResult mergeBidirectional(Path tempDir, String branchA, String branchB,
                                                     TestUserInteraction.ResultProvider interactionProvider) throws Exception {
        return new SemanticMergeCommand().executeBidirectional(
                tempDir, branchA, branchB, allCPS(), interactionProvider, null);
    }

    private SemanticMergeResult mergeWithInterleaving(Path tempDir, String branchA, String branchB,
                                                       TestUserInteraction.ResultProvider interactionProvider) throws Exception {
        return new SemanticMergeCommand().executeWithInterleaving(
                tempDir, branchA, branchB, allCPS(), interactionProvider, null);
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

    private void removeBrakeDisk(VirtualModel vsum, String id) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var disk = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakeDisk)
                .map(c -> (BrakeDisk) c)
                .findFirst().orElseThrow();
        bs.getBrakeComponents().remove(disk);
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
