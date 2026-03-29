package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import java.nio.file.Path;
import java.util.List;

import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.brakesystem2safety.Brakesystem2safetyChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.jgit.api.Git;

import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.branch.merge.ChangeLogCapture;
import tools.vitruv.framework.vsum.branch.merge.SemanticChangeLog;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

import brakesystem.ABSSensor;
import brakesystem.BrakeCaliper;
import brakesystem.BrakeComponent;
import brakesystem.BrakeDisk;
import brakesystem.BrakeHose;
import brakesystem.BrakePad;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;

/**
 * Shared scenario setup for the three-model branching merge comparison tests.
 * Extracted from ThreeModelBranchingMergeTest so that both Vitruvius and EMFCompare
 * can run against the same prepared git repositories.
 *
 * <p>Each scenario creates a git repo with two branches containing model changes.
 * Changelogs are always captured (required by Vitruvius; ignored by EMFCompare).
 */
public class ThreeModelScenarioSetup {

    public record PreparedScenario(
            Path repoPath,
            String sourceBranch,
            String targetBranch
    ) {}

    static {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .putIfAbsent("*", new XMIResourceFactoryImpl());
    }

    public static List<ChangePropagationSpecification> allCPS() {
        return List.of(
                new Brakesystem2cadChangePropagationSpecification(),
                new Cad2brakesystemChangePropagationSpecification(),
                new Brakesystem2safetyChangePropagationSpecification());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario setup methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Dispatch to the appropriate scenario setup.
     */
    public PreparedScenario setupScenario(int scenarioNumber, Path tempDir) throws Exception {
        return switch (scenarioNumber) {
            case 1 -> setupScenario1(tempDir);
            case 2 -> setupScenario2(tempDir);
            case 3 -> setupScenario3(tempDir);
            case 4 -> setupScenario4(tempDir);
            case 5 -> setupScenario5(tempDir);
            case 6 -> setupScenario6(tempDir);
            case 7 -> setupScenario7(tempDir);
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenarioNumber);
        };
    }

    /**
     * S1: user(A) changes BrakeDisk diameter in M1 -> derived M2+M3 update; B adds ABSSensor.
     */
    public PreparedScenario setupScenario1(Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: BrakeDisk disk1 d=300 t=25").call();

            // Branch A (feature): change diameter 300->320
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
        }
        return new PreparedScenario(tempDir, "feature", "main");
    }

    /**
     * S2: user(A) renames Namespace.id in M2 -> derived M1+M3 update; B adds caliper.
     */
    public PreparedScenario setupScenario2(Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: BrakeDisk disk1").call();

            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeNamespaceId(vsum, "disk1", "disk1-rev2");

            commitWithChangelog(git, capture, tempDir, "feature", "Renamed namespace to disk1-rev2");

            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            addBrakeCaliper(vsum, "caliper1", 42);

            commitWithChangelog(git, capture, tempDir, "main", "Added caliper1");

            vsum.dispose();
        }
        return new PreparedScenario(tempDir, "feature", "main");
    }

    /**
     * S3: A changes BrakeDisk in M1, B changes BrakePad in M1 -> both derive to M3, no conflict.
     */
    public PreparedScenario setupScenario3(Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);
            addBrakePad(vsum, "pad1", 50, 100, 15);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1 + pad1").call();

            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 320);

            commitWithChangelog(git, capture, tempDir, "feature", "disk1 diameter->320");

            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeBrakePadHeight(vsum, "pad1", 55);

            commitWithChangelog(git, capture, tempDir, "main", "pad1 height->55");

            vsum.dispose();
        }
        return new PreparedScenario(tempDir, "feature", "main");
    }

    /**
     * S4: derived(A) in M2 vs user(B) in M2 -> warning, B's intent preserved.
     * A changes BrakeDisk.diameter in M1 -> reaction derives CAD parameter.
     * B directly sets CAD Diameter parameter to a different value.
     */
    public PreparedScenario setupScenario4(Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1 d=300").call();

            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 320);

            commitWithChangelog(git, capture, tempDir, "feature", "BrakeDisk diameter->320");

            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeCADNumericParameter(vsum, "disk1", "Diameter", 350.0f);

            commitWithChangelog(git, capture, tempDir, "main", "CAD Diameter->350 (user intent)");

            vsum.dispose();
        }
        return new PreparedScenario(tempDir, "feature", "main");
    }

    /**
     * S5: user(A) in M2 overwrites derived(B) in M2 -> warning, A's intent wins.
     * A directly sets CAD Diameter parameter (user intent).
     * B changes BrakeDisk.diameter in M1 -> reaction derives CAD param (derived in B).
     */
    public PreparedScenario setupScenario5(Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1 d=300").call();

            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeCADNumericParameter(vsum, "disk1", "Diameter", 320.0f);

            commitWithChangelog(git, capture, tempDir, "feature", "CAD Diameter->320 (user intent)");

            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 350);

            commitWithChangelog(git, capture, tempDir, "main", "BrakeDisk diameter->350");

            vsum.dispose();
        }
        return new PreparedScenario(tempDir, "feature", "main");
    }

    /**
     * S6: user(A) and user(B) both change BrakeDisk.diameter -> direct MODIFY_MODIFY conflict.
     */
    public PreparedScenario setupScenario6(Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1 d=300").call();

            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 320);

            commitWithChangelog(git, capture, tempDir, "feature", "diameter->320");

            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeBrakeDiskDiameter(vsum, "disk1", 350);

            commitWithChangelog(git, capture, tempDir, "main", "diameter->350");

            vsum.dispose();
        }
        return new PreparedScenario(tempDir, "feature", "main");
    }

    /**
     * S7: derived(replay(A)) overwrites user(B) across M2 and M3 -> indirect conflict.
     * A changes BrakeDisk.id -> derives Namespace.id (M2) and SafetyEntry.componentId (M3).
     * B directly changes Namespace.id in M2 (user change).
     */
    public PreparedScenario setupScenario7(Path tempDir) throws Exception {
        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);
            addBrakesystem(vsum, tempDir);
            addBrakeDisk(vsum, "disk1", 300, true, 25);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: disk1").call();

            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            var capture = freshCapture(vsum);

            changeBrakeDiskId(vsum, "disk1", "engineeringDisk");

            commitWithChangelog(git, capture, tempDir, "feature", "BrakeDisk.id->engineeringDisk");

            git.checkout().setName("main").call();
            vsum.reload();
            capture = freshCapture(vsum);

            changeNamespaceId(vsum, "disk1", "cadCustomId");

            commitWithChangelog(git, capture, tempDir, "main", "Namespace.id->cadCustomId (user)");

            vsum.dispose();
        }
        return new PreparedScenario(tempDir, "feature", "main");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Scenario descriptions (for table output)
    // ═══════════════════════════════════════════════════════════════════

    public static String scenarioDescription(int scenario) {
        return switch (scenario) {
            case 1 -> "Clean: A changes M1 diameter, B adds sensor";
            case 2 -> "Clean: A renames M2 namespace, B adds caliper";
            case 3 -> "Clean: A changes disk, B changes pad (diff M3 entries)";
            case 4 -> "derived(A) vs user(B) in M2 (CAD Diameter)";
            case 5 -> "user(A) vs derived(B) in M2 (CAD Diameter)";
            case 6 -> "Direct conflict: both change BrakeDisk.diameter";
            case 7 -> "Indirect: derived(A) overwrites user(B) across M2/M3";
            default -> "Unknown";
        };
    }

    public static String scenarioCategory(int scenario) {
        return switch (scenario) {
            case 1, 2, 3 -> "Clean merge";
            case 4 -> "Indirect conflict";
            case 5 -> "User vs derived";
            case 6 -> "Direct conflict";
            case 7 -> "Cross-model indirect";
            default -> "Unknown";
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // VSUM creation helpers
    // ═══════════════════════════════════════════════════════════════════

    public static InternalVirtualModel createThreeModelVsum(Path projectPath) throws Exception {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(new TestUserInteraction()))
                .withChangePropagationSpecifications(allCPS())
                .buildAndInitialize();
        model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
        return model;
    }

    public static ChangeLogCapture freshCapture(InternalVirtualModel vsum) {
        var capture = ChangeLogCapture.create(vsum.getUuidResolver(),
                vsum.getViewSourceModels().iterator().next().getResourceSet());
        vsum.addChangePropagationListener(capture);
        return capture;
    }

    public static void commitWithChangelog(Git git, ChangeLogCapture capture, Path tempDir,
                                            String branch, String message) throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage(message).call();
        String sha = git.log().setMaxCount(1).call().iterator().next().getName();
        new SemanticChangeLog(sha, branch, capture.drainChanges(),
                capture.drainUuidMapping(), capture.drainCascadeDeletedUuids(),
                capture.drainConsequentialFootprints()).saveTo(tempDir);
        git.add().addFilepattern(".").call();
        git.commit().setAmend(true).setMessage(message + " + changelog").call();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Model manipulation helpers
    // ═══════════════════════════════════════════════════════════════════

    public static void addBrakesystem(VirtualModel vsum, Path projectPath) {
        var view = selectBrakesystemView(vsum).withChangeDerivingTrait();
        var bs = BrakesystemFactory.eINSTANCE.createBrakesystem();
        bs.setSpecificationType("Standard");
        bs.setInstanceName("TestBrake");
        view.registerRoot(bs, URI.createFileURI(projectPath.toString() + "/brakesystem.model"));
        view.commitChanges();
    }

    public static void addBrakeDisk(VirtualModel vsum, String id, int diameter, boolean ventilated, int thickness) {
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

    public static void addBrakePad(VirtualModel vsum, String id, int height, int width, int thickness) {
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

    public static void addABSSensor(VirtualModel vsum, String id, int length, int pins) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var sensor = BrakesystemFactory.eINSTANCE.createABSSensor();
        sensor.setId(id);
        sensor.setLengthInMM(length);
        sensor.setNumberOfPins(pins);
        bs.getBrakeComponents().add(sensor);
        view.commitChanges();
    }

    public static void addBrakeCaliper(VirtualModel vsum, String id, int pistonDiameter) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var caliper = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
        caliper.setId(id);
        caliper.setPistonDiameterInMM(pistonDiameter);
        bs.getBrakeComponents().add(caliper);
        view.commitChanges();
    }

    public static void changeBrakeDiskDiameter(VirtualModel vsum, String id, int newDiameter) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var disk = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakeDisk)
                .map(c -> (BrakeDisk) c)
                .findFirst().orElseThrow();
        disk.setDiameterInMM(newDiameter);
        view.commitChanges();
    }

    public static void changeBrakePadHeight(VirtualModel vsum, String id, int newHeight) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var pad = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakePad)
                .map(c -> (BrakePad) c)
                .findFirst().orElseThrow();
        pad.setHeightInMM(newHeight);
        view.commitChanges();
    }

    public static void changeBrakeDiskId(VirtualModel vsum, String oldId, String newId) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var comp = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(oldId))
                .findFirst().orElseThrow();
        comp.setId(newId);
        view.commitChanges();
    }

    public static void changeNamespaceId(VirtualModel vsum, String oldId, String newId) {
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

    public static void changeCADNumericParameter(VirtualModel vsum, String namespaceId,
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

    // ═══════════════════════════════════════════════════════════════════
    // Additional model manipulation helpers (Track B evaluation)
    // ═══════════════════════════════════════════════════════════════════

    public static void addBrakeHose(VirtualModel vsum, String id, int length, String threadSize1, String threadSize2) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var hose = BrakesystemFactory.eINSTANCE.createBrakeHose();
        hose.setId(id);
        hose.setLengthInMM(length);
        hose.setThreadSize1(threadSize1);
        hose.setThreadSize2(threadSize2);
        bs.getBrakeComponents().add(hose);
        view.commitChanges();
    }

    public static void changeBrakeDiskThickness(VirtualModel vsum, String id, int newThickness) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var disk = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakeDisk)
                .map(c -> (BrakeDisk) c)
                .findFirst().orElseThrow();
        disk.setBrakeDiskThicknessInMM(newThickness);
        view.commitChanges();
    }

    public static void changeBrakePadWidth(VirtualModel vsum, String id, int newWidth) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var pad = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakePad)
                .map(c -> (BrakePad) c)
                .findFirst().orElseThrow();
        pad.setWidthInMM(newWidth);
        view.commitChanges();
    }

    public static void changeBrakeDiskCenteringDiameter(VirtualModel vsum, String id, int newValue) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var disk = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakeDisk)
                .map(c -> (BrakeDisk) c)
                .findFirst().orElseThrow();
        disk.setCenteringDiameterInMM(newValue);
        view.commitChanges();
    }

    public static void changeBrakeDiskRimHoleNumber(VirtualModel vsum, String id, int newValue) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var disk = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakeDisk)
                .map(c -> (BrakeDisk) c)
                .findFirst().orElseThrow();
        disk.setRimHoleNumber(newValue);
        view.commitChanges();
    }

    public static void changeCaliperPistonDiameter(VirtualModel vsum, String id, int newValue) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var caliper = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof BrakeCaliper)
                .map(c -> (BrakeCaliper) c)
                .findFirst().orElseThrow();
        caliper.setPistonDiameterInMM(newValue);
        view.commitChanges();
    }

    public static void changeABSSensorLength(VirtualModel vsum, String id, int newLength) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var sensor = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof ABSSensor)
                .map(c -> (ABSSensor) c)
                .findFirst().orElseThrow();
        sensor.setLengthInMM(newLength);
        view.commitChanges();
    }

    public static void changeABSSensorPins(VirtualModel vsum, String id, int newPins) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        var sensor = bs.getBrakeComponents().stream()
                .filter(c -> c.getId().equals(id) && c instanceof ABSSensor)
                .map(c -> (ABSSensor) c)
                .findFirst().orElseThrow();
        sensor.setNumberOfPins(newPins);
        view.commitChanges();
    }

    /**
     * Changes the Brakesystem root's instanceName — triggers NO cross-model Reactions
     * (neither M1→M2 CAD nor M1→M3 Safety), making it suitable for low reaction density benchmarks.
     */
    public static void changeBrakesystemInstanceName(VirtualModel vsum, String newName) {
        var view = selectBrakesystemView(vsum).withChangeRecordingTrait();
        var bs = view.getRootObjects(Brakesystem.class).iterator().next();
        bs.setInstanceName(newName);
        view.commitChanges();
    }

    // ═══════════════════════════════════════════════════════════════════
    // View helpers
    // ═══════════════════════════════════════════════════════════════════

    public static CommittableView selectBrakesystemView(VirtualModel vsum) {
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("brakesystem"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof Brakesystem)
                .forEach(e -> selector.setSelected(e, true));
        return selector.createView().withChangeDerivingTrait();
    }
}
