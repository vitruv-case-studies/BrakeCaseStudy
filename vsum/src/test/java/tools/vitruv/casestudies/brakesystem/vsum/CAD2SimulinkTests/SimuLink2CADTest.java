package tools.vitruv.casestudies.brakesystem.vsum.CAD2SimulinkTests;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.kit.ipd.sdq.metamodels.cad.Assembly;
import edu.kit.ipd.sdq.metamodels.cad.CADDocument;
import edu.kit.ipd.sdq.metamodels.cad.Constraint;
import edu.kit.ipd.sdq.metamodels.cad.ConstraintType;
import mir.reactions.cad2simulink.Cad2simulinkChangePropagationSpecification;
import mir.reactions.simulink2cad.Simulink2cadChangePropagationSpecification;
import simulink.Block;
import simulink.SimuLinkFactory;
import simulink.SimulinkModel;
import simulink.SubSystem;
import tools.vitruv.casestudies.brakesystem.consistency.CadUtility;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.VirtualModel;

/**
 * Simulink blocks of physical parts become CAD parts (Assemblies); numeric block parameters
 * (lengths in millimetres) become named constraints in metres. Texts, flags and counts are not
 * part of the CAD overlap.
 */
public class SimuLink2CADTest extends TestBase {
    private static final String BLOCK = "TestBlock";
    private static final String LENGTH = "TestLength";

    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Cad2simulinkChangePropagationSpecification(),
                new Simulink2cadChangePropagationSpecification());
    }

    @Test public void testCreateAndRegisterRootSimulinkModel(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.modifyView(simulinkView(vsum), (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");
            v.registerRoot(simulinkModel, URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertEquals("TestSimulinkModel", document(vsum).getName());
    }

    @Test public void testSimulinkBlockToCADPart(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextConfirmationInput(true);
        util.modifyView(simulinkView(vsum), (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");
            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName(BLOCK);
            simulinkModel.getContains().add(block);
            v.registerRoot(simulinkModel, URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assembly part = single(document(vsum).getObjects(), Assembly.class);
        Assertions.assertEquals(BLOCK, part.getName());
        Assertions.assertEquals(BLOCK, part.getLabel());
    }

    @Test public void testNonPhysicalBlockGetsNoCADPart(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.userInteraction.addNextConfirmationInput(false);
        util.modifyView(simulinkView(vsum), (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName("Controller");
            simulinkModel.getContains().add(block);
            v.registerRoot(simulinkModel, URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(document(vsum).getObjects().isEmpty());
    }

    @Test public void testSimulinkSubsystemToCADPart(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);

        Assertions.assertNotNull(TestUtil.findPart(document(vsum), BLOCK));
    }

    @Test public void testSimulinkNumericParameterToCADLength(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);

        Constraint length = CadUtility.findParameter(TestUtil.findPart(document(vsum), BLOCK), LENGTH);
        Assertions.assertEquals(0.042, length.getValue(), 1e-12);
        Assertions.assertEquals(ConstraintType.DISTANCE, length.getType());
        Assertions.assertTrue(length.isDriving());
    }

    @Test public void testSimulinkTextsFlagsAndCountsAreNoCADDimensions(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);

        modifyBlock(vsum, block -> {
            block.getParameters().add(parameter("TestText", "string", "TestValue"));
            block.getParameters().add(parameter("TestFlag", "boolean", "true"));
            block.getParameters().add(parameter("Number of Pins", "int32", "4"));
        });

        Assertions.assertEquals(List.of(LENGTH), CadUtility.parameters(TestUtil.findPart(document(vsum), BLOCK)).stream().map(Constraint::getName).toList());
    }

    @Test public void testSimulinkParameterWithoutTypeAsksForTheDimension(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);

        util.userInteraction.addNextSingleSelection(1); // angle
        util.userInteraction.addNextSingleSelection(2); // none
        modifyBlock(vsum, block -> {
            block.getParameters().add(parameter("Tilt", null, "30"));
            block.getParameters().add(parameter("Gain", null, "3"));
        });

        Assembly part = TestUtil.findPart(document(vsum), BLOCK);
        Constraint tilt = CadUtility.findParameter(part, "Tilt");
        Assertions.assertEquals(ConstraintType.ANGLE, tilt.getType());
        Assertions.assertEquals(30, tilt.getValue(), 1e-12);
        Assertions.assertNull(CadUtility.findParameter(part, "Gain"));
    }

    @Test public void testSimulinkParameterDeletion(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);

        modifyBlock(vsum, block -> EcoreUtil.delete(block.getParameters().get(0)));

        Assembly part = TestUtil.findPart(document(vsum), BLOCK);
        Assertions.assertNull(CadUtility.findParameter(part, LENGTH));
        // The construction geometry of the dimension is removed as well
        Assertions.assertTrue(CadUtility.findParameterSketch(part).getGeometries().isEmpty());
    }

    @Test public void SimulinkBlockDeletion(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);

        util.modifyView(simulinkView(vsum), (CommittableView v) -> EcoreUtil.delete(block(v, BLOCK)));

        Assertions.assertNull(TestUtil.findPart(document(vsum), BLOCK));
    }

    @Test public void SimulinkBlockNameUpdateTest(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);

        modifyBlock(vsum, block -> block.setName("UpdatedBlockName"));

        // The label is the user-visible name; the name stays the stable identifier
        Assembly part = TestUtil.findPart(document(vsum), BLOCK);
        Assertions.assertEquals("UpdatedBlockName", part.getLabel());
    }

    @Test public void SimulinkParameterNameUpdateTest(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);

        modifyBlock(vsum, block -> block.getParameters().get(0).setName("UpdatedParameterName"));

        Assembly part = TestUtil.findPart(document(vsum), BLOCK);
        Assertions.assertTrue(TestUtil.expectDimension(part, "UpdatedParameterName", 0.042));
        Assertions.assertNull(CadUtility.findParameter(part, LENGTH));
    }

    @Test public void updateSimulinkParameterValueTest(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);

        modifyBlock(vsum, block -> block.getParameters().get(0).setValue("12.5"));
        Assertions.assertTrue(TestUtil.expectDimension(TestUtil.findPart(document(vsum), BLOCK), LENGTH, 0.0125));

        // Values that are no numbers (e.g. MATLAB expressions) leave the dimension unchanged
        modifyBlock(vsum, block -> block.getParameters().get(0).setValue("diskD"));
        Assertions.assertTrue(TestUtil.expectDimension(TestUtil.findPart(document(vsum), BLOCK), LENGTH, 0.0125));
    }

    @Test public void blockInSubsystemIsNestedInThePartOfTheSubsystem(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);

        util.userInteraction.addNextConfirmationInput(true);
        modifyBlock(vsum, block -> ((SubSystem) block).getSubBlocks().add(block("Inner")));

        Assembly outer = TestUtil.findPart(document(vsum), BLOCK);
        Assertions.assertEquals("Inner", single(outer.getChildren(), Assembly.class).getName());
        Assertions.assertEquals(1, document(vsum).getObjects().size());
    }

    @Test public void movingABlockMovesItsPart(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        createDefaultSimuLinkModel(vsum, tempDir);
        util.userInteraction.addNextConfirmationInput(true);
        modifyBlock(vsum, block -> ((SubSystem) block).getSubBlocks().add(block("Inner")));

        util.modifyView(simulinkView(vsum), (CommittableView v) -> {
            SimulinkModel model = v.getRootObjects(SimulinkModel.class).iterator().next();
            model.getContains().add(((SubSystem) block(v, BLOCK)).getSubBlocks().get(0));
        });

        Assertions.assertTrue(TestUtil.findPart(document(vsum), BLOCK).getChildren().stream().noneMatch(Assembly.class::isInstance));
        Assertions.assertEquals(2, document(vsum).getObjects().size());
    }

    /** A Simulink model with the subsystem "TestBlock" that has the length "TestLength" of 42 mm. */
    private void createDefaultSimuLinkModel(VirtualModel vsum, Path filePath) {
        util.userInteraction.addNextConfirmationInput(true);
        util.modifyView(simulinkView(vsum), (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");
            SubSystem block = SimuLinkFactory.eINSTANCE.createSubSystem();
            block.setName(BLOCK);
            block.getParameters().add(parameter(LENGTH, "double", "42"));
            simulinkModel.getContains().add(block);
            v.registerRoot(simulinkModel, URI.createFileURI(filePath.resolve("simulink.xmi").toString()));
        });
    }

    private void modifyBlock(VirtualModel vsum, Consumer<Block> modification) {
        util.modifyView(simulinkView(vsum), (CommittableView v) -> modification.accept(block(v, BLOCK)));
    }

    private static simulink.Parameter parameter(String name, String type, String value) {
        simulink.Parameter parameter = SimuLinkFactory.eINSTANCE.createParameter();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setValue(value);
        return parameter;
    }

    private static Block block(String name) {
        Block block = SimuLinkFactory.eINSTANCE.createBlock();
        block.setName(name);
        return block;
    }

    private static Block block(CommittableView view, String name) {
        return view.getRootObjects(SimulinkModel.class).iterator().next().getContains().stream()
                .filter(b -> name.equals(b.getName())).findFirst().orElseThrow();
    }

    private CommittableView simulinkView(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
    }

    private CADDocument document(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(CADDocument.class)).getRootObjects(CADDocument.class).iterator().next();
    }

    private static <T> T single(List<?> elements, Class<T> type) {
        List<T> matching = elements.stream().filter(type::isInstance).map(type::cast).toList();
        Assertions.assertEquals(1, matching.size(), elements.toString());
        return matching.get(0);
    }
}
