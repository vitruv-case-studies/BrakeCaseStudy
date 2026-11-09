package tools.vitruv.casestudies.brakesystem.vsum.CAD2SimulinkTests;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.emf.common.util.URI;
import edu.kit.ipd.sdq.metamodels.cad.Assembly;
import edu.kit.ipd.sdq.metamodels.cad.CADDocument;
import edu.kit.ipd.sdq.metamodels.cad.CadFactory;
import edu.kit.ipd.sdq.metamodels.cad.Constraint;
import edu.kit.ipd.sdq.metamodels.cad.ConstraintType;
import mir.reactions.cad2simulink.Cad2simulinkChangePropagationSpecification;
import tools.vitruv.casestudies.brakesystem.consistency.CadUtility;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.VirtualModel;

import simulink.SimulinkModel;
import simulink.SubSystem;
import simulink.Block;

/**
 * CAD parts (Assemblies) become Simulink blocks named by their label; named dimensional constraints
 * become numeric block parameters with lengths in millimetres.
 */
public class CAD2SimuLinkTest extends TestBase {
    private static final String PART = "TestPart";
    private static final String LENGTH = "TestLength";

    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Cad2simulinkChangePropagationSpecification());
    }

    @Test public void testCreateAndRegisterRoot(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.modifyView(cadView(vsum), (CommittableView v) -> {
            CADDocument document = CadFactory.eINSTANCE.createCADDocument();
            document.setName("TestCADModel");
            v.registerRoot(document, URI.createFileURI(tempDir.resolve("cad.xmi").toString()));
        });

        Assertions.assertEquals("TestCADModel", simulinkModel(vsum).getName());
    }

    @Test public void testCADPartToSimuLinkBlock(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextSingleSelection(0); // Block
        util.userInteraction.addNextSingleSelection(0); // double
        createDefaultCADModel(vsum, tempDir);

        Block block = block(simulinkModel(vsum), PART);
        Assertions.assertFalse(block instanceof SubSystem);
    }

    @Test public void testCADPartLabelIsBlockName(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextSingleSelection(0); // Block
        util.userInteraction.addNextSingleSelection(0); // double
        createDefaultCADModel(vsum, tempDir);

        util.modifyView(cadView(vsum), (CommittableView v) -> part(v).setLabel("Renamed"));

        Assertions.assertEquals("Renamed", single(simulinkModel(vsum).getContains()).getName());
    }

    @Test public void testCADPartToSimuLinkSubsystem(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextSingleSelection(1); // SubSystem
        util.userInteraction.addNextSingleSelection(0); // double
        createDefaultCADModel(vsum, tempDir);

        Assertions.assertInstanceOf(SubSystem.class, block(simulinkModel(vsum), PART));
    }

    @Test public void testCADLengthToSimuLinkParameterInMillimetres(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextSingleSelection(0); // Block
        util.userInteraction.addNextSingleSelection(0); // double
        createDefaultCADModel(vsum, tempDir);

        Assertions.assertTrue(TestUtil.expectParameter(block(simulinkModel(vsum), PART), LENGTH, "double", "42"));
    }

    @Test public void testCADAngleToSimuLinkParameterInDegrees(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextSingleSelection(0); // Block
        util.userInteraction.addNextSingleSelection(0); // double
        util.userInteraction.addNextSingleSelection(0); // double
        createDefaultCADModel(vsum, tempDir);

        util.modifyView(cadView(vsum), (CommittableView v) -> TestUtil.addDimension(part(v), "Tilt", ConstraintType.ANGLE, 30));

        Assertions.assertTrue(TestUtil.expectParameter(block(simulinkModel(vsum), PART), "Tilt", "double", "30"));
    }

    @Test public void testConstraintsThatAreNoDimensionsAreIgnored(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextSingleSelection(0); // Block
        util.userInteraction.addNextSingleSelection(0); // double
        createDefaultCADModel(vsum, tempDir);

        util.modifyView(cadView(vsum), (CommittableView v) -> {
            Constraint length = CadUtility.findParameter(part(v), LENGTH);
            // An unnamed dimension and a coincidence are no parameters
            Constraint unnamed = CadFactory.eINSTANCE.createConstraint();
            unnamed.setType(ConstraintType.DISTANCE);
            unnamed.setValue(1);
            unnamed.setFirst(length.getFirst());
            Constraint coincident = CadFactory.eINSTANCE.createConstraint();
            coincident.setName("Joint");
            coincident.setType(ConstraintType.COINCIDENT);
            coincident.setFirst(length.getFirst());
            CadUtility.findParameterSketch(part(v)).getConstraints().addAll(List.of(unnamed, coincident));
        });

        Assertions.assertEquals(List.of(LENGTH), block(simulinkModel(vsum), PART).getParameters().stream().map(simulink.Parameter::getName).toList());
    }

    @Test public void testParameterDeletion(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextSingleSelection(0); // Block
        util.userInteraction.addNextSingleSelection(0); // double
        createDefaultCADModel(vsum, tempDir);

        util.modifyView(cadView(vsum), (CommittableView v) -> CadUtility.removeParameter(CadUtility.findParameter(part(v), LENGTH)));

        Assertions.assertNull(CadUtility.findParameter(part(cadView(vsum)), LENGTH));
        Assertions.assertTrue(block(simulinkModel(vsum), PART).getParameters().isEmpty());
    }

    @Test public void testParameterModification(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextSingleSelection(0); // Block
        util.userInteraction.addNextSingleSelection(0); // double
        createDefaultCADModel(vsum, tempDir);

        util.modifyView(cadView(vsum), (CommittableView v) -> CadUtility.findParameter(part(v), LENGTH).setValue(0.0125));

        Assertions.assertTrue(TestUtil.expectParameter(block(simulinkModel(vsum), PART), LENGTH, "double", "12.5"));
    }

    @Test public void testNestedPartIsPlacedInTheSubsystemOfItsParent(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextSingleSelection(1); // SubSystem
        util.userInteraction.addNextSingleSelection(0); // double
        createDefaultCADModel(vsum, tempDir);

        // No question where to insert the block: the parent part decides
        util.userInteraction.addNextSingleSelection(0); // Block
        util.modifyView(cadView(vsum), (CommittableView v) -> part(v).getChildren().add(TestUtil.createPart("Child")));

        SubSystem subSystem = (SubSystem) block(simulinkModel(vsum), PART);
        Assertions.assertEquals("Child", single(subSystem.getSubBlocks()).getName());
        Assertions.assertEquals(1, simulinkModel(vsum).getContains().size());
    }

    @Test public void testMovingAPartMovesItsBlock(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextSingleSelection(1); // SubSystem
        util.userInteraction.addNextSingleSelection(0); // double
        createDefaultCADModel(vsum, tempDir);
        util.userInteraction.addNextSingleSelection(0); // Block
        util.userInteraction.addNextSingleSelection(0); // Insert at root level
        util.modifyView(cadView(vsum), (CommittableView v) -> document(v).getObjects().add(TestUtil.createPart("Other")));
        Assertions.assertEquals(2, simulinkModel(vsum).getContains().size());

        // into the part of the subsystem ...
        util.modifyView(cadView(vsum), (CommittableView v) -> part(v).getChildren().add(TestUtil.findPart(document(v), "Other")));
        Assertions.assertEquals("Other", single(((SubSystem) block(simulinkModel(vsum), PART)).getSubBlocks()).getName());
        Assertions.assertEquals(1, simulinkModel(vsum).getContains().size());

        // ... and back to the top level
        util.modifyView(cadView(vsum), (CommittableView v) -> document(v).getObjects().add(TestUtil.findPart(document(v), "Other")));
        Assertions.assertTrue(((SubSystem) block(simulinkModel(vsum), PART)).getSubBlocks().isEmpty());
        Assertions.assertEquals(2, simulinkModel(vsum).getContains().size());
    }

    /** Creates a CAD document with a part that has a length of 42 mm and registers it as root. */
    private void createDefaultCADModel(VirtualModel vsum, Path filePath) {
        util.modifyView(cadView(vsum), (CommittableView v) -> {
            CADDocument document = CadFactory.eINSTANCE.createCADDocument();
            document.setName("TestCADModel");
            Assembly part = TestUtil.createPart(PART);
            document.getObjects().add(part);
            TestUtil.addLength(part, LENGTH, 0.042);
            v.registerRoot(document, URI.createFileURI(filePath.resolve("cad.xmi").toString()));
        });
    }

    private CommittableView cadView(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(CADDocument.class)).withChangeDerivingTrait();
    }

    private static CADDocument document(CommittableView view) {
        return view.getRootObjects(CADDocument.class).iterator().next();
    }

    private static Assembly part(CommittableView view) {
        return TestUtil.findPart(document(view), PART);
    }

    private SimulinkModel simulinkModel(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(SimulinkModel.class)).getRootObjects(SimulinkModel.class).iterator().next();
    }

    private static Block block(SimulinkModel model, String name) {
        return model.getContains().stream().filter(b -> name.equals(b.getName())).findFirst().orElseThrow();
    }

    private static <T> T single(List<T> elements) {
        Assertions.assertEquals(1, elements.size(), elements.toString());
        return elements.get(0);
    }
}
