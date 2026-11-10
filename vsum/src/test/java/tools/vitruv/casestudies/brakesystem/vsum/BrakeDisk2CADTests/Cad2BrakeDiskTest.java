package tools.vitruv.casestudies.brakesystem.vsum.BrakeDisk2CADTests;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import brakesystem.ABSSensor;
import brakesystem.BrakeCaliper;
import brakesystem.BrakeComponent;
import brakesystem.BrakeDisk;
import brakesystem.BrakePad;
import brakesystem.Brakesystem;
import edu.kit.ipd.sdq.metamodels.cad.Assembly;
import edu.kit.ipd.sdq.metamodels.cad.CADDocument;
import edu.kit.ipd.sdq.metamodels.cad.ConstraintType;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import tools.vitruv.casestudies.brakesystem.consistency.CadUtility;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.VirtualModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CAD parts (Assemblies) become brake components chosen by the user; named dimensions (in metres)
 * become the length attributes (in whole millimetres). A part nested in the part of a caliper is
 * a brake pad of that caliper.
 */
public class Cad2BrakeDiskTest extends TestBase {
    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Cad2brakesystemChangePropagationSpecification(),
                new Brakesystem2cadChangePropagationSpecification());
    }

    @Test void brakeDiskInsertionAndPropagationTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        util.userInteraction.addNextSingleSelection(0); // ABSSensor
        modifyCad(vsum, document -> document.getObjects().add(TestUtil.createPart("brakeDisk1")));

        BrakeComponent component = single(brakesystem(vsum).getBrakeComponents());
        assertInstanceOf(ABSSensor.class, component);
        assertEquals("brakeDisk1", component.getId());
    }

    @Test void nochoice(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        assertThrows(RuntimeException.class,
                () -> modifyCad(vsum, document -> document.getObjects().add(TestUtil.createPart("brakeDisk1"))));
    }

    @Test void parameterInsertionAndPropagationTestForABSSensor(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        util.userInteraction.addNextSingleSelection(0); // ABSSensor
        modifyCad(vsum, document -> {
            Assembly part = TestUtil.createPart("sensor1");
            document.getObjects().add(part);
            TestUtil.addLength(part, "Length", 0.1);
            TestUtil.addLength(part, "Fitting Depth", 0.0125);
        });

        ABSSensor sensor = (ABSSensor) single(brakesystem(vsum).getBrakeComponents());
        assertEquals(100, sensor.getLengthInMM());
        // Whole millimetres in the brake system
        assertEquals(13, sensor.getFittingDepth());
        // The more precise CAD value is not overwritten by the propagated whole millimetres
        assertTrue(TestUtil.expectDimension(part(vsum, "sensor1"), "Fitting Depth", 0.0125));
    }

    @Test void parameterInsertionAndPropagationForBrakeDiskTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        util.userInteraction.addNextSingleSelection(3); // BrakeDisk
        modifyCad(vsum, document -> {
            Assembly part = TestUtil.createPart("brakeDisk1");
            document.getObjects().add(part);
            TestUtil.addLength(part, "Brake Disk Thickness", 0.03);
            TestUtil.addLength(part, "Diameter", 0.28);
            // An angle is no length and is ignored
            TestUtil.addDimension(part, "Minimum Thickness", ConstraintType.ANGLE, 45);
        });

        BrakeDisk disk = (BrakeDisk) single(brakesystem(vsum).getBrakeComponents());
        assertEquals(30, disk.getBrakeDiskThicknessInMM());
        assertEquals(280, disk.getDiameterInMM());
        assertEquals(0, disk.getMinimumThicknessInMM());
    }

    @Test void parameterInsertionAndPropagationForBrakePadTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        util.userInteraction.addNextSingleSelection(1); // Caliper
        modifyCad(vsum, document -> document.getObjects().add(TestUtil.createPart("brakeCaliper1")));
        util.userInteraction.addNextSingleSelection(1); // Caliper
        modifyCad(vsum, document -> document.getObjects().add(TestUtil.createPart("brakeCaliper2")));

        util.userInteraction.addNextSingleSelection(4); // Brake Pad
        util.userInteraction.addNextSingleSelection(1); // Second caliper
        modifyCad(vsum, document -> document.getObjects().add(TestUtil.createPart("brakePad1")));

        Brakesystem brakesystem = brakesystem(vsum);
        assertTrue(caliper(brakesystem, "brakeCaliper1").getBrakePads().isEmpty());
        assertEquals("brakePad1", single(caliper(brakesystem, "brakeCaliper2").getBrakePads()).getId());
    }

    @Test void nestedPartOfACaliperIsOneOfItsPads(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        util.userInteraction.addNextSingleSelection(1); // Caliper
        modifyCad(vsum, document -> document.getObjects().add(TestUtil.createPart("brakeCaliper1")));
        util.userInteraction.addNextSingleSelection(1); // Caliper
        modifyCad(vsum, document -> document.getObjects().add(TestUtil.createPart("brakeCaliper2")));

        // No question for the caliper: the parent part decides
        util.userInteraction.addNextSingleSelection(4); // Brake Pad
        modifyCad(vsum, document -> {
            Assembly pad = TestUtil.createPart("brakePad1");
            TestUtil.findPart(document, "brakeCaliper1").getChildren().add(pad);
            TestUtil.addLength(pad, "Thickness", 0.012);
        });

        BrakePad pad = single(caliper(brakesystem(vsum), "brakeCaliper1").getBrakePads());
        assertEquals("brakePad1", pad.getId());
        assertEquals(12, pad.getThicknessInMM());

        // Moving the part into the other caliper's part moves the pad
        modifyCad(vsum, document -> TestUtil.findPart(document, "brakeCaliper2").getChildren().add(TestUtil.findPart(document, "brakePad1")));
        assertTrue(caliper(brakesystem(vsum), "brakeCaliper1").getBrakePads().isEmpty());
        assertEquals("brakePad1", single(caliper(brakesystem(vsum), "brakeCaliper2").getBrakePads()).getId());

        // ... and moving it to the top level detaches it from the calipers
        modifyCad(vsum, document -> document.getObjects().add(TestUtil.findPart(document, "brakePad1")));
        assertTrue(caliper(brakesystem(vsum), "brakeCaliper2").getBrakePads().isEmpty());
        assertTrue(brakesystem(vsum).getBrakeComponents().stream().anyMatch(c -> c instanceof BrakePad && c.getId().equals("brakePad1")));
    }

    @Test void partNameIsComponentId(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        util.userInteraction.addNextSingleSelection(3); // BrakeDisk
        modifyCad(vsum, document -> document.getObjects().add(TestUtil.createPart("brakeDisk1")));

        modifyCad(vsum, document -> TestUtil.findPart(document, "brakeDisk1").setName("frontDisk"));

        assertEquals("frontDisk", single(brakesystem(vsum).getBrakeComponents()).getId());
    }

    // Edit the same dimension in both models and check that it is propagated back and forth
    @Test void biDirectionalPropagationTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        util.userInteraction.addNextSingleSelection(0); // ABSSensor
        modifyCad(vsum, document -> {
            Assembly part = TestUtil.createPart("sensor1");
            document.getObjects().add(part);
            TestUtil.addLength(part, "Length", 0.1);
        });
        assertEquals(100, ((ABSSensor) single(brakesystem(vsum).getBrakeComponents())).getLengthInMM());

        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            ABSSensor sensor = (ABSSensor) v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().get(0);
            sensor.setLengthInMM(120);
            sensor.setFittingDepth(50);
            sensor.setNumberOfPins(4);
        });

        Assembly part = part(vsum, "sensor1");
        assertTrue(TestUtil.expectDimension(part, "Length", 0.12));
        assertTrue(TestUtil.expectDimension(part, "Fitting Depth", 0.05));
        // A count is no CAD dimension
        assertNull(CadUtility.findParameter(part, "Number of Pins"));
    }

    private VirtualModel createVsum(Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);
        return vsum;
    }

    private void modifyCad(VirtualModel vsum, Consumer<CADDocument> modification) {
        util.modifyView(util.getDefaultView(vsum, List.of(CADDocument.class)).withChangeRecordingTrait(),
                (CommittableView v) -> modification.accept(v.getRootObjects(CADDocument.class).iterator().next()));
    }

    private Brakesystem brakesystem(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(Brakesystem.class)).getRootObjects(Brakesystem.class).iterator().next();
    }

    private Assembly part(VirtualModel vsum, String name) {
        CADDocument document = util.getDefaultView(vsum, List.of(CADDocument.class)).getRootObjects(CADDocument.class).iterator().next();
        Assembly part = TestUtil.findPart(document, name);
        assertNotNull(part, "no CAD part " + name);
        return part;
    }

    private static BrakeCaliper caliper(Brakesystem brakesystem, String id) {
        return brakesystem.getBrakeComponents().stream()
                .filter(c -> c instanceof BrakeCaliper && c.getId().equals(id))
                .map(BrakeCaliper.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static <T> T single(List<T> elements) {
        assertEquals(1, elements.size(), elements.toString());
        return elements.get(0);
    }
}
