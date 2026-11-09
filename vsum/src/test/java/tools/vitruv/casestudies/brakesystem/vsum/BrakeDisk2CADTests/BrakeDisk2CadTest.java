package tools.vitruv.casestudies.brakesystem.vsum.BrakeDisk2CADTests;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import brakesystem.BrakeCaliper;
import brakesystem.BrakeComponent;
import brakesystem.BrakeDisk;
import brakesystem.BrakePad;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import edu.kit.ipd.sdq.metamodels.cad.Assembly;
import edu.kit.ipd.sdq.metamodels.cad.CADDocument;
import edu.kit.ipd.sdq.metamodels.cad.Circle;
import edu.kit.ipd.sdq.metamodels.cad.Constraint;
import edu.kit.ipd.sdq.metamodels.cad.ConstraintType;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import tools.vitruv.casestudies.brakesystem.consistency.CadUtility;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.VirtualModel;

/**
 * Brake components become CAD parts (Assemblies) named by their id; their length attributes become
 * named dimensions in metres. Pads are nested in the part of their caliper.
 */
public class BrakeDisk2CadTest extends TestBase {
    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Cad2brakesystemChangePropagationSpecification(),
                new Brakesystem2cadChangePropagationSpecification());
    }

    @Test void brakeDiskInsertionAndPropagationTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        modifyBrakesystem(vsum, brakesystem -> brakesystem.getBrakeComponents().add(createDefaultBrakeDisk()));

        Assembly part = part(vsum, "brakeDisk1");
        Assertions.assertEquals("brakeDisk1", part.getLabel());
        Assertions.assertTrue(TestUtil.expectDimension(part, "Diameter", 0.12));
        Assertions.assertTrue(TestUtil.expectDimension(part, "Centering Diameter", 0.02));
        Assertions.assertTrue(TestUtil.expectDimension(part, "Bolt Hole Circle", 0.06));
        Assertions.assertTrue(TestUtil.expectDimension(part, "Brake Disk Thickness", 0.03));
        Assertions.assertTrue(TestUtil.expectDimension(part, "Minimum Thickness", 0.025));
        // Texts, flags and counts (OEM number, ventilated, rim hole number, ...) are no CAD dimensions
        Assertions.assertEquals(5, CadUtility.parameters(part).size());
        Assertions.assertEquals(ConstraintType.DIAMETER, CadUtility.findParameter(part, "Diameter").getType());
        Assertions.assertEquals(ConstraintType.DISTANCE, CadUtility.findParameter(part, "Brake Disk Thickness").getType());
    }

    @Test void unsetLengthsGetNoDimension(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        modifyBrakesystem(vsum, brakesystem -> {
            BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
            brakeDisk.setId("brakeDisk1");
            brakeDisk.setDiameterInMM(120);
            brakesystem.getBrakeComponents().add(brakeDisk);
        });

        Assertions.assertEquals(List.of("Diameter"), CadUtility.parameters(part(vsum, "brakeDisk1")).stream().map(Constraint::getName).toList());
    }

    @Test void brakePadIsNestedInThePartOfItsCaliper(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        modifyBrakesystem(vsum, brakesystem -> {
            BrakeCaliper brakeCaliper = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
            brakeCaliper.setId("brakeCaliper1");
            brakeCaliper.setPistonDiameterInMM(50);
            BrakePad brakePad = BrakesystemFactory.eINSTANCE.createBrakePad();
            brakePad.setId("brakePad1");
            brakePad.setWearWarning(true);
            brakePad.setThicknessInMM(12);
            brakeCaliper.getBrakePads().add(brakePad);
            brakesystem.getBrakeComponents().add(brakeCaliper);
        });

        CADDocument document = document(vsum);
        Assembly caliper = TestUtil.findPart(document, "brakeCaliper1");
        Assembly pad = TestUtil.findPart(document, "brakePad1");
        Assertions.assertTrue(TestUtil.expectDimension(caliper, "Piston Diameter", 0.05));
        Assertions.assertSame(caliper, pad.eContainer()); // same view
        Assertions.assertTrue(TestUtil.expectDimension(pad, "Thickness", 0.012));
        Assertions.assertEquals(1, CadUtility.parameters(pad).size());
    }

    @Test void movingABrakePadMovesItsPart(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        modifyBrakesystem(vsum, brakesystem -> {
            BrakeCaliper first = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
            first.setId("brakeCaliper1");
            BrakeCaliper second = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
            second.setId("brakeCaliper2");
            BrakePad brakePad = BrakesystemFactory.eINSTANCE.createBrakePad();
            brakePad.setId("brakePad1");
            first.getBrakePads().add(brakePad);
            brakesystem.getBrakeComponents().addAll(List.of(first, second));
        });

        modifyBrakesystem(vsum, brakesystem -> ((BrakeCaliper) component(brakesystem, "brakeCaliper2"))
                .getBrakePads().add(((BrakeCaliper) component(brakesystem, "brakeCaliper1")).getBrakePads().get(0)));
        Assertions.assertEquals("brakeCaliper2", ((Assembly) part(vsum, "brakePad1").eContainer()).getName());

        modifyBrakesystem(vsum, brakesystem -> brakesystem.getBrakeComponents()
                .add(((BrakeCaliper) component(brakesystem, "brakeCaliper2")).getBrakePads().get(0)));
        Assertions.assertInstanceOf(CADDocument.class, part(vsum, "brakePad1").eContainer());
    }

    @Test void changeDiameterTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        modifyBrakesystem(vsum, brakesystem -> {
            BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
            brakeDisk.setId("brakeDisk1");
            brakeDisk.setDiameterInMM(120);
            brakesystem.getBrakeComponents().add(brakeDisk);
        });
        Assertions.assertTrue(TestUtil.expectDimension(part(vsum, "brakeDisk1"), "Diameter", 0.12));

        modifyBrakesystem(vsum, brakesystem -> ((BrakeDisk) component(brakesystem, "brakeDisk1")).setDiameterInMM(130));

        Constraint diameter = CadUtility.findParameter(part(vsum, "brakeDisk1"), "Diameter");
        Assertions.assertEquals(0.13, diameter.getValue(), 1e-12);
        // The constrained construction circle follows, as the sketch solver would do
        Assertions.assertEquals(0.065, ((Circle) diameter.getFirst()).getRadius(), 1e-12);
    }

    @Test void changeDimensionInCadTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        modifyBrakesystem(vsum, brakesystem -> {
            BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
            brakeDisk.setId("brakeDisk1");
            brakeDisk.setDiameterInMM(120);
            brakesystem.getBrakeComponents().add(brakeDisk);
        });

        util.modifyView(cadView(vsum), (CommittableView v) -> CadUtility.findParameter(
                TestUtil.findPart(v.getRootObjects(CADDocument.class).iterator().next(), "brakeDisk1"), "Diameter").setValue(0.13));

        Assertions.assertEquals(130, ((BrakeDisk) component(brakesystem(vsum), "brakeDisk1")).getDiameterInMM());
    }

    @Test void changeIdTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        modifyBrakesystem(vsum, brakesystem -> brakesystem.getBrakeComponents().add(createDefaultBrakeDisk()));

        modifyBrakesystem(vsum, brakesystem -> component(brakesystem, "brakeDisk1").setId("newId"));

        Assembly part = part(vsum, "newId");
        Assertions.assertTrue(TestUtil.expectDimension(part, "Diameter", 0.12));
        Assertions.assertEquals("newId_Parameters", CadUtility.findParameterSketch(part).getName());
        Assertions.assertNull(TestUtil.findPart(document(vsum), "brakeDisk1"));
    }

    @Test void deleteBrakeComponentTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = createVsum(tempDir);
        modifyBrakesystem(vsum, brakesystem -> brakesystem.getBrakeComponents().add(createDefaultBrakeDisk()));

        modifyBrakesystem(vsum, brakesystem -> EcoreUtil.delete(component(brakesystem, "brakeDisk1")));

        Assertions.assertTrue(document(vsum).getObjects().isEmpty());
    }

    private VirtualModel createVsum(Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);
        return vsum;
    }

    private void modifyBrakesystem(VirtualModel vsum, Consumer<Brakesystem> modification) {
        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait(),
                (CommittableView v) -> modification.accept(v.getRootObjects(Brakesystem.class).iterator().next()));
    }

    private CommittableView cadView(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(CADDocument.class)).withChangeRecordingTrait();
    }

    private Brakesystem brakesystem(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(Brakesystem.class)).getRootObjects(Brakesystem.class).iterator().next();
    }

    private CADDocument document(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(CADDocument.class)).getRootObjects(CADDocument.class).iterator().next();
    }

    private Assembly part(VirtualModel vsum, String name) {
        Assembly part = TestUtil.findPart(document(vsum), name);
        Assertions.assertNotNull(part, "no CAD part " + name);
        return part;
    }

    /** The component with the given id, also a pad of a caliper. */
    private static BrakeComponent component(Brakesystem brakesystem, String id) {
        for (BrakeComponent component : brakesystem.getBrakeComponents()) {
            if (id.equals(component.getId())) {
                return component;
            }
            if (component instanceof BrakeCaliper caliper) {
                for (BrakePad pad : caliper.getBrakePads()) {
                    if (id.equals(pad.getId())) {
                        return pad;
                    }
                }
            }
        }
        throw new AssertionError("no brake component " + id);
    }

    private static BrakeDisk createDefaultBrakeDisk() {
        BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
        brakeDisk.setId("brakeDisk1");
        brakeDisk.setOEM_number("VW123456");
        brakeDisk.setDiameterInMM(120);
        brakeDisk.setCenteringDiameterInMM(20);
        brakeDisk.setRimHoleNumber(1);
        brakeDisk.setHoleArrangementNumber(20);
        brakeDisk.setBoltHoleCircleInMM(60);
        brakeDisk.setBrakeDiskThicknessInMM(30);
        brakeDisk.setMinimumThicknessInMM(25);
        brakeDisk.setVentilated(true);
        return brakeDisk;
    }
}
