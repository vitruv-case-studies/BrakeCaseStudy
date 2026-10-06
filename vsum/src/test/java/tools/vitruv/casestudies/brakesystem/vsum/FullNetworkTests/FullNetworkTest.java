package tools.vitruv.casestudies.brakesystem.vsum.FullNetworkTests;

import autosar.ARPackage;
import autosar.AUTOSAR;
import autosar.ApplicationSwComponentType;
import autosar.AutoSARFactory;
import autosar.CompositionSwComponentType;
import autosar.SensorActuatorSwComponentType;
import autosar.SwComponentType;
import bom.BOM;
import bom.BomFactory;
import bom.DataType;
import bom.DoubleParameterValue;
import bom.Item;
import bom.ItemType;
import bom.ParameterDefinition;
import brakesystem.ABSSensor;
import brakesystem.BrakeCaliper;
import brakesystem.BrakeComponent;
import brakesystem.BrakeDisk;
import brakesystem.BrakePad;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.CadFactory;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import edu.kit.ipd.sdq.metamodels.cad.Unit;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import mir.reactions.autosar2brakesystem.Autosar2brakesystemChangePropagationSpecification;
import mir.reactions.bom2brakesystem.Bom2brakesystemChangePropagationSpecification;
import mir.reactions.brakesystem2bom.Brakesystem2bomChangePropagationSpecification;
import mir.reactions.bom2cad.Bom2cadChangePropagationSpecification;
import mir.reactions.cad2bom.Cad2bomChangePropagationSpecification;
import mir.reactions.bom2simulink.Bom2simulinkChangePropagationSpecification;
import mir.reactions.simulink2bom.Simulink2bomChangePropagationSpecification;
import mir.reactions.autosar2simulink.Autosar2simulinkChangePropagationSpecification;
import mir.reactions.brakesystem2autosar.Brakesystem2autosarChangePropagationSpecification;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.brakesystem2simulink.Brakesystem2simulinkChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import mir.reactions.cad2simulink.Cad2simulinkChangePropagationSpecification;
import mir.reactions.simulink2autosar.Simulink2autosarChangePropagationSpecification;
import mir.reactions.simulink2brakesystem.Simulink2brakesystemChangePropagationSpecification;
import mir.reactions.simulink2cad.Simulink2cadChangePropagationSpecification;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import simulink.Block;
import simulink.Parameter;
import simulink.SimuLinkFactory;
import simulink.SimulinkModel;
import simulink.SubSystem;
import tools.vitruv.casestudies.brakesystem.consistency.ParameterValueUtility;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

/**
 * Runs all reactions together, so changes propagate transitively through every pair of models.
 * Every scenario checks that each concept exists exactly once per model, i.e. that no cyclic
 * propagation creates duplicates, contradicting elements or oscillating values.
 *
 * <p>The order of dialogs from different reactions is not deterministic, so dialogs are answered
 * by their message and the label of the choice, never by their position.
 */
public class FullNetworkTest extends TestBase {
    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Brakesystem2simulinkChangePropagationSpecification(),
                new Simulink2brakesystemChangePropagationSpecification(),
                new Brakesystem2cadChangePropagationSpecification(),
                new Cad2brakesystemChangePropagationSpecification(),
                new Brakesystem2autosarChangePropagationSpecification(),
                new Autosar2brakesystemChangePropagationSpecification(),
                new Simulink2autosarChangePropagationSpecification(),
                new Autosar2simulinkChangePropagationSpecification(),
                new Cad2simulinkChangePropagationSpecification(),
                new Simulink2cadChangePropagationSpecification(),
                new Bom2brakesystemChangePropagationSpecification(),
                new Brakesystem2bomChangePropagationSpecification(),
                new Bom2cadChangePropagationSpecification(),
                new Cad2bomChangePropagationSpecification(),
                new Bom2simulinkChangePropagationSpecification(),
                new Simulink2bomChangePropagationSpecification());
    }

    // Roots

    @Test public void brakesystemRootIsCreatedOnceInEveryModel(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createBrakesystem(tempDir);
        assertOneRootPerModel(vsum);
        View view = allModels(vsum);
        Assertions.assertEquals("bs", single(view, SimulinkModel.class).getName());
        Assertions.assertEquals("bs", single(view, CAD_Model.class).getName());
    }

    @Test public void simulinkRootIsCreatedOnceInEveryModel(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createSimulink(tempDir);
        assertOneRootPerModel(vsum);
        Assertions.assertEquals("sl", single(allModels(vsum), Brakesystem.class).getInstanceName());
    }

    @Test public void cadRootIsCreatedOnceInEveryModel(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createCad(tempDir);
        assertOneRootPerModel(vsum);
        Assertions.assertEquals("cad", single(allModels(vsum), SimulinkModel.class).getName());
    }

    @Test public void autosarRootIsCreatedOnceInEveryModel(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createAutosar(tempDir);
        assertOneRootPerModel(vsum);
    }

    // Brake components created in the brake system model

    @Test public void brakeDiskIsPropagatedOnceAndKeptConsistent(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createBrakesystem(tempDir);
        answerAutosarComponent("ApplicationSwComponentType");

        modify(vsum, v -> {
            BrakeDisk disk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
            disk.setId("disk1");
            disk.setDiameterInMM(120);
            single(v, Brakesystem.class).getBrakeComponents().add(disk);
        });
        assertDisk(vsum, "disk1", 120);

        modify(vsum, v -> ((BrakeDisk) single(v, Brakesystem.class).getBrakeComponents().get(0)).setDiameterInMM(130));
        assertDisk(vsum, "disk1", 130);

        modify(vsum, v -> single(v, Brakesystem.class).getBrakeComponents().get(0).setId("disk2"));
        assertDisk(vsum, "disk2", 130);

        modify(vsum, v -> single(v, Brakesystem.class).getBrakeComponents().clear());
        View view = allModels(vsum);
        Assertions.assertTrue(single(view, BOM.class).getItems().isEmpty());
        Assertions.assertTrue(single(view, SimulinkModel.class).getContains().isEmpty());
        Assertions.assertTrue(single(view, CAD_Model.class).getNamespaces().isEmpty());
        Assertions.assertTrue(swComponents(single(view, AUTOSAR.class)).isEmpty());
    }

    @Test public void absSensorBecomesSensorActuatorWithoutAsking(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createBrakesystem(tempDir);

        modify(vsum, v -> {
            ABSSensor sensor = BrakesystemFactory.eINSTANCE.createABSSensor();
            sensor.setId("abs1");
            sensor.setFittingDepth(10);
            single(v, Brakesystem.class).getBrakeComponents().add(sensor);
        });

        View view = allModels(vsum);
        Block block = single(single(view, SimulinkModel.class).getContains());
        Assertions.assertEquals("abs1", block.getName());
        Assertions.assertEquals("abs1", single(single(view, CAD_Model.class).getNamespaces()).getId());
        SwComponentType component = single(swComponents(single(view, AUTOSAR.class)));
        Assertions.assertInstanceOf(SensorActuatorSwComponentType.class, component);
        Assertions.assertEquals("abs1", component.getShortName());
    }

    @Test public void brakePadIsPlacedInTheSubsystemOfItsCaliper(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createBrakesystem(tempDir);
        answerAutosarComponent("ApplicationSwComponentType");

        modify(vsum, v -> {
            BrakeCaliper caliper = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
            caliper.setId("cal1");
            BrakePad pad = BrakesystemFactory.eINSTANCE.createBrakePad();
            pad.setId("pad1");
            caliper.getBrakePads().add(pad);
            single(v, Brakesystem.class).getBrakeComponents().add(caliper);
        });

        assertPadInCaliper(vsum);
    }

    // Brake components created in the other models

    @Test public void sensorDecisionCannotContradict(@TempDir Path tempDir) throws Exception {
        // Try to provoke contradicting answers: the block is a brake disk, but a sensor/actuator in AUTOSAR.
        // Whichever dialog comes first decides, the other one must follow.
        VirtualModel vsum = createSimulink(tempDir);
        answerConfirmation("physical part", true);
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("brake component"))
                .always().respondWith("BrakeDisk");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("AutoSAR component") &&
                        contains(d.getChoices(), "SensorActuatorSwComponentType"))
                .always().respondWith("SensorActuatorSwComponentType");
        answerAutosarComponent("ApplicationSwComponentType");

        addBlock(vsum, "blk1");
        modify(vsum, v -> single(v, SimulinkModel.class).getContains().get(0).setName("blk2"));

        View view = allModels(vsum);
        BrakeComponent component = single(single(view, Brakesystem.class).getBrakeComponents());
        SwComponentType swComponent = single(swComponents(single(view, AUTOSAR.class)));
        Assertions.assertEquals(component instanceof ABSSensor, swComponent instanceof SensorActuatorSwComponentType,
                "ABS sensors and sensor/actuator components must correspond to each other");
        Assertions.assertEquals("blk2", component.getId());
        Assertions.assertEquals("blk2", swComponent.getShortName());
    }

    @Test public void cadLengthsAreConvertedToMillimetresForSimulink(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createCad(tempDir);
        answerBrakeComponent("BrakeDisk");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().startsWith("What Simulink component"))
                .always().respondWith("SimulinkBlock");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("Parametertype"))
                .always().respondWith("double");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("Parametertype"))
                .always().respondWith("double");
        answerAutosarComponent("ApplicationSwComponentType");

        modify(vsum, v -> {
            Namespace namespace = CadFactory.eINSTANCE.createNamespace();
            namespace.setId("ns1");
            namespace.setName("ns1");
            NumericParameter diameter = CadFactory.eINSTANCE.createNumericParameter();
            diameter.setName("Diameter");
            diameter.setValue(12);
            diameter.setUnit(Unit.CM);
            namespace.getParameters().add(diameter);
            single(v, CAD_Model.class).getNamespaces().add(namespace);
        });
        assertDiameter(vsum, 120, "120", 12, Unit.CM);

        // The value written by the user in Simulink is kept as written, the CAD unit is kept as well
        modify(vsum, v -> diameterParameter(single(v, SimulinkModel.class)).setValue("130.0"));
        assertDiameter(vsum, 130, "130.0", 13, Unit.CM);

        // Changing only the unit reinterprets the value
        modify(vsum, v -> diameterNumericParameter(single(v, CAD_Model.class)).setUnit(Unit.MM));
        assertDiameter(vsum, 13, "13", 13, Unit.MM);
    }

    @Test public void simulinkValuesAreNotOverwrittenByEchoes(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createSimulink(tempDir);
        answerConfirmation("physical part", true);
        answerBrakeComponent("BrakeDisk");
        answerAutosarComponent("ApplicationSwComponentType");
        addBlock(vsum, "blk1");

        modify(vsum, v -> {
            Parameter diameter = SimuLinkFactory.eINSTANCE.createParameter();
            diameter.setName("Diameter");
            diameter.setType("int32");
            diameter.setValue("120");
            single(v, SimulinkModel.class).getContains().get(0).getParameters().add(diameter);
        });
        assertDiameter(vsum, 120, "120", 120, Unit.MM);

        // Values that are no numbers (e.g. MATLAB expressions) are not propagated, but cause no errors
        modify(vsum, v -> diameterParameter(single(v, SimulinkModel.class)).setValue("diskD"));
        assertDiameter(vsum, 120, "diskD", 120, Unit.MM);
    }

    @Test public void brakePadFromCadIsPlacedInTheSubsystemOfItsCaliper(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createCad(tempDir);
        answerBrakeComponent("BrakeCaliper");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().startsWith("What Simulink component"))
                .always().respondWith("SimulinkSubsystem");
        addNamespace(vsum, "cal1");

        util.userInteraction.clearResponses();
        answerBrakeComponent("BrakePad");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("calipher"))
                .always().respondWith("cal1");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().startsWith("What Simulink component"))
                .always().respondWith("SimulinkBlock");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("Parametertype"))
                .always().respondWith("double");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("Where do you want to insert"))
                .always().respondWith("cal1");
        answerAutosarComponent("ApplicationSwComponentType");
        addNamespace(vsum, "pad1");

        assertPadInCaliper(vsum);
    }

    // Parts that are no brake components

    @Test public void softwareComponentDoesNotBecomeAPhysicalPart(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createAutosar(tempDir);
        answerBrakeComponent("None");
        answerConfirmation("physical part", false);

        modify(vsum, v -> {
            ApplicationSwComponentType controller = AutoSARFactory.eINSTANCE.createApplicationSwComponentType();
            controller.setShortName("brakeController");
            single(v, AUTOSAR.class).getArpackage().get(0).getElements().add(controller);
        });

        View view = allModels(vsum);
        Assertions.assertEquals("brakeController", single(single(view, SimulinkModel.class).getContains()).getName());
        Assertions.assertTrue(single(view, Brakesystem.class).getBrakeComponents().isEmpty());
        Assertions.assertTrue(single(view, CAD_Model.class).getNamespaces().isEmpty());
        Assertions.assertTrue(single(view, BOM.class).getItems().isEmpty());
    }

    @Test public void physicalPartNeedsNoBrakeComponent(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createSimulink(tempDir);
        answerBrakeComponent("None");
        answerConfirmation("physical part", true);
        answerAutosarComponent("ApplicationSwComponentType");

        addBlock(vsum, "motor");

        View view = allModels(vsum);
        Assertions.assertEquals("motor", single(single(view, CAD_Model.class).getNamespaces()).getName());
        Assertions.assertTrue(single(view, Brakesystem.class).getBrakeComponents().isEmpty());
        Assertions.assertEquals("motor", single(single(view, BOM.class).getItems()).getId());
    }

    @Test public void deletionInSimulinkIsPropagatedToAllModels(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createSimulink(tempDir);
        answerConfirmation("physical part", true);
        answerBrakeComponent("BrakeDisk");
        answerAutosarComponent("ApplicationSwComponentType");
        addBlock(vsum, "blk1");

        modify(vsum, v -> single(v, SimulinkModel.class).getContains().clear());

        View view = allModels(vsum);
        Assertions.assertTrue(single(view, Brakesystem.class).getBrakeComponents().isEmpty());
        Assertions.assertTrue(single(view, CAD_Model.class).getNamespaces().isEmpty());
        Assertions.assertTrue(swComponents(single(view, AUTOSAR.class)).isEmpty());
        Assertions.assertTrue(single(view, BOM.class).getItems().isEmpty());
    }

    // Bill of materials

    @Test public void brakeDisksShareOneItemType(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createBrakesystem(tempDir);
        answerAutosarComponent("ApplicationSwComponentType");

        modify(vsum, v -> {
            for (String id : List.of("disk1", "disk2")) {
                BrakeDisk disk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
                disk.setId(id);
                single(v, Brakesystem.class).getBrakeComponents().add(disk);
            }
        });

        BOM bom = single(allModels(vsum), BOM.class);
        Assertions.assertEquals(2, bom.getItems().size());
        ItemType type = single(bom.getItemTypes());
        Assertions.assertEquals("BrakeDisk", type.getName());
        Assertions.assertTrue(bom.getItems().stream().allMatch(item -> item.getType() == type));
    }

    @Test public void itemQuantityCreatesConsistentInstancesInAllModels(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createBom(tempDir);
        answerAutosarComponent("ApplicationSwComponentType");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().startsWith("What Simulink component"))
                .always().respondWith("SimulinkBlock");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("Parametertype"))
                .always().respondWith("double");

        addBrakeDiskItem(vsum, "disk", 2);
        assertDiskInstances(vsum, "disk", 2);

        modify(vsum, v -> single(single(v, BOM.class).getItems()).setNumberOfItems(1));
        assertDiskInstances(vsum, "disk", 1);

        modify(vsum, v -> single(single(v, BOM.class).getItems()).setNumberOfItems(3));
        assertDiskInstances(vsum, "disk", 3);

        modify(vsum, v -> single(single(v, BOM.class).getItems()).setId("rotor"));
        assertDiskInstances(vsum, "rotor", 3);
    }

    @Test public void deletingAnInstanceDecreasesTheQuantity(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createBom(tempDir);
        answerAutosarComponent("ApplicationSwComponentType");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().startsWith("What Simulink component"))
                .always().respondWith("SimulinkBlock");
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("Parametertype"))
                .always().respondWith("double");
        addBrakeDiskItem(vsum, "disk", 2);

        modify(vsum, v -> single(v, Brakesystem.class).getBrakeComponents().removeIf(component -> component.getId().equals("disk-2")));

        assertDiskInstances(vsum, "disk", 1);
    }

    @Test public void partThatIsNoBrakeComponentStaysOutOfTheBrakeSystem(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createBom(tempDir);
        // Not every physical part is simulated
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().startsWith("What Simulink component"))
                .always().respondWith("None");

        modify(vsum, v -> {
            BOM bom = single(v, BOM.class);
            ItemType type = BomFactory.eINSTANCE.createItemType();
            type.setName("Bolt");
            bom.getItemTypes().add(type);
            Item item = BomFactory.eINSTANCE.createItem();
            item.setId("bolt");
            item.setType(type);
            item.setNumberOfItems(4);
            bom.getItems().add(item);
        });

        View view = allModels(vsum);
        Assertions.assertTrue(single(view, Brakesystem.class).getBrakeComponents().isEmpty());
        Assertions.assertTrue(single(view, SimulinkModel.class).getContains().isEmpty());
        Assertions.assertEquals(List.of("bolt", "bolt-2", "bolt-3", "bolt-4"),
                single(view, CAD_Model.class).getNamespaces().stream().map(Namespace::getId).sorted().toList());
    }

    // Setup

    private VirtualModel createBrakesystem(Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        modify(vsum, v -> {
            Brakesystem brakesystem = BrakesystemFactory.eINSTANCE.createBrakesystem();
            brakesystem.setInstanceName("bs");
            v.registerRoot(brakesystem, URI.createFileURI(tempDir.resolve("my.brakesystem").toString()));
        });
        return vsum;
    }

    private VirtualModel createSimulink(Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        modify(vsum, v -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("sl");
            simulinkModel.setVersion("1");
            v.registerRoot(simulinkModel, URI.createFileURI(tempDir.resolve("my.simulink").toString()));
        });
        return vsum;
    }

    private VirtualModel createCad(Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        modify(vsum, v -> {
            CAD_Model cadModel = CadFactory.eINSTANCE.createCAD_Model();
            cadModel.setName("cad");
            v.registerRoot(cadModel, URI.createFileURI(tempDir.resolve("my.cad").toString()));
        });
        return vsum;
    }

    private VirtualModel createAutosar(Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        modify(vsum, v -> {
            AUTOSAR autosar = AutoSARFactory.eINSTANCE.createAUTOSAR();
            ARPackage arPackage = AutoSARFactory.eINSTANCE.createARPackage();
            arPackage.setShortName("pkg");
            autosar.getArpackage().add(arPackage);
            v.registerRoot(autosar, URI.createFileURI(tempDir.resolve("my.autosar").toString()));
        });
        return vsum;
    }

    private VirtualModel createBom(Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        modify(vsum, v -> {
            BOM bom = BomFactory.eINSTANCE.createBOM();
            bom.setName("bom");
            v.registerRoot(bom, URI.createFileURI(tempDir.resolve("my.bom").toString()));
        });
        return vsum;
    }

    /** Adds an item of brake disks with a diameter of 12 cm. */
    private void addBrakeDiskItem(VirtualModel vsum, String id, int quantity) {
        modify(vsum, v -> {
            BOM bom = single(v, BOM.class);
            ItemType type = BomFactory.eINSTANCE.createItemType();
            type.setName("BrakeDisk");
            ParameterDefinition definition = BomFactory.eINSTANCE.createParameterDefinition();
            definition.setName("Diameter");
            definition.setDataType(DataType.EDOUBLE);
            type.getParameterDefinitions().add(definition);
            bom.getItemTypes().add(type);

            Item item = BomFactory.eINSTANCE.createItem();
            item.setId(id);
            item.setType(type);
            item.setNumberOfItems(quantity);
            DoubleParameterValue diameter = BomFactory.eINSTANCE.createDoubleParameterValue();
            diameter.setDefinition(definition);
            diameter.setValue(12);
            diameter.setUnit("cm");
            item.getParameterValues().add(diameter);
            bom.getItems().add(item);
        });
    }

    private void addBlock(VirtualModel vsum, String name) {
        modify(vsum, v -> {
            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName(name);
            single(v, SimulinkModel.class).getContains().add(block);
        });
    }

    private void addNamespace(VirtualModel vsum, String id) {
        modify(vsum, v -> {
            Namespace namespace = CadFactory.eINSTANCE.createNamespace();
            namespace.setId(id);
            namespace.setName(id);
            single(v, CAD_Model.class).getNamespaces().add(namespace);
        });
    }

    private void modify(VirtualModel vsum, Consumer<CommittableView> modification) {
        util.modifyView(allModels(vsum).withChangeRecordingTrait(), modification);
    }

    private View allModels(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(Brakesystem.class, SimulinkModel.class, CAD_Model.class, AUTOSAR.class, BOM.class));
    }

    private void answerBrakeComponent(String choice) {
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("brake component"))
                .always().respondWith(choice);
    }

    private void answerAutosarComponent(String choice) {
        util.userInteraction.onMultipleChoiceSingleSelection(d -> d.getMessage().contains("AutoSAR component"))
                .always().respondWith(choice);
    }

    private void answerConfirmation(String messagePart, boolean answer) {
        util.userInteraction.onConfirmation(d -> d.getMessage().contains(messagePart)).always().respondWith(answer);
    }

    // Assertions

    private void assertOneRootPerModel(VirtualModel vsum) {
        View view = allModels(vsum);
        single(view, Brakesystem.class);
        single(view, SimulinkModel.class);
        single(view, CAD_Model.class);
        single(view, AUTOSAR.class);
        single(view, BOM.class);
    }

    private void assertDisk(VirtualModel vsum, String id, int diameterInMM) {
        View view = allModels(vsum);
        BrakeDisk disk = (BrakeDisk) single(single(view, Brakesystem.class).getBrakeComponents());
        Assertions.assertEquals(id, disk.getId());
        Assertions.assertEquals(diameterInMM, disk.getDiameterInMM());

        Block block = single(single(view, SimulinkModel.class).getContains());
        Assertions.assertEquals(id, block.getName());
        Assertions.assertEquals(diameterInMM, Float.parseFloat(single(block.getParameters()).getValue()));

        Namespace namespace = single(single(view, CAD_Model.class).getNamespaces());
        Assertions.assertEquals(id, namespace.getId());
        Assertions.assertEquals(id, namespace.getName());
        NumericParameter diameter = (NumericParameter) single(namespace.getParameters());
        Assertions.assertEquals(diameterInMM, diameter.getValue());
        Assertions.assertEquals(Unit.MM, diameter.getUnit());

        Assertions.assertEquals(id, single(swComponents(single(view, AUTOSAR.class))).getShortName());

        BOM bom = single(view, BOM.class);
        Item item = single(bom.getItems());
        Assertions.assertEquals(id, item.getId());
        Assertions.assertEquals(1, item.getNumberOfItems());
        Assertions.assertEquals("BrakeDisk", single(bom.getItemTypes()).getName());
        Assertions.assertEquals(diameterInMM, ((bom.IntegerParameterValue) parameterValue(item, "Diameter")).getValue());
    }

    /** Every model has the instances "id", "id-2", ... of the item, with a diameter of 12 cm. */
    private void assertDiskInstances(VirtualModel vsum, String itemId, int quantity) {
        View view = allModels(vsum);
        List<String> expectedIds = java.util.stream.IntStream.range(0, quantity)
                .mapToObj(index -> index == 0 ? itemId : itemId + "-" + (index + 1))
                .sorted()
                .toList();

        BOM bom = single(view, BOM.class);
        Item item = single(bom.getItems());
        Assertions.assertEquals(itemId, item.getId());
        Assertions.assertEquals(quantity, item.getNumberOfItems());
        Assertions.assertEquals("BrakeDisk", single(bom.getItemTypes()).getName());
        DoubleParameterValue diameter = (DoubleParameterValue) single(item.getParameterValues());
        Assertions.assertEquals(12, diameter.getValue(), 1e-6);
        Assertions.assertEquals("cm", diameter.getUnit());

        List<BrakeComponent> components = single(view, Brakesystem.class).getBrakeComponents();
        Assertions.assertEquals(expectedIds, components.stream().map(BrakeComponent::getId).sorted().toList());
        Assertions.assertTrue(components.stream().allMatch(component -> ((BrakeDisk) component).getDiameterInMM() == 120));

        List<Namespace> namespaces = single(view, CAD_Model.class).getNamespaces();
        Assertions.assertEquals(expectedIds, namespaces.stream().map(Namespace::getId).sorted().toList());
        Assertions.assertEquals(expectedIds, namespaces.stream().map(Namespace::getName).sorted().toList());
        for (Namespace namespace : namespaces) {
            NumericParameter cadDiameter = (NumericParameter) single(namespace.getParameters());
            Assertions.assertEquals(12, cadDiameter.getValue(), 1e-4);
            Assertions.assertEquals(Unit.CM, cadDiameter.getUnit());
        }

        List<Block> blocks = single(view, SimulinkModel.class).getContains();
        Assertions.assertEquals(expectedIds, blocks.stream().map(Block::getName).sorted().toList());
        for (Block block : blocks) {
            Assertions.assertEquals(120, Float.parseFloat(single(block.getParameters()).getValue()));
        }

        Assertions.assertEquals(expectedIds, swComponents(single(view, AUTOSAR.class)).stream().map(SwComponentType::getShortName).sorted().toList());
    }

    private static bom.ParameterValue parameterValue(Item item, String name) {
        return item.getParameterValues().stream()
                .filter(value -> value.getDefinition().getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void assertDiameter(VirtualModel vsum, int brakesystemValue, String simulinkValue, float cadValue, Unit cadUnit) {
        View view = allModels(vsum);
        BrakeDisk disk = (BrakeDisk) single(single(view, Brakesystem.class).getBrakeComponents());
        Assertions.assertEquals(brakesystemValue, disk.getDiameterInMM());
        Assertions.assertEquals(simulinkValue, diameterParameter(single(view, SimulinkModel.class)).getValue());
        NumericParameter cadDiameter = diameterNumericParameter(single(view, CAD_Model.class));
        Assertions.assertEquals(cadValue, cadDiameter.getValue(), 1e-4);
        Assertions.assertEquals(cadUnit, cadDiameter.getUnit());

        // The BOM keeps its own unit, but denotes the same length
        bom.NumericParameterValue bomDiameter = (bom.NumericParameterValue) parameterValue(single(single(view, BOM.class).getItems()), "Diameter");
        double bomValue = bomDiameter instanceof DoubleParameterValue decimal ? decimal.getValue() : ((bom.IntegerParameterValue) bomDiameter).getValue();
        Assertions.assertEquals(brakesystemValue, ParameterValueUtility.convert(bomValue, ParameterValueUtility.parseUnit(bomDiameter.getUnit()), Unit.MM), 1e-3);
    }

    private void assertPadInCaliper(VirtualModel vsum) {
        View view = allModels(vsum);
        BrakeCaliper caliper = (BrakeCaliper) single(single(view, Brakesystem.class).getBrakeComponents());
        Assertions.assertEquals("pad1", single(caliper.getBrakePads()).getId());

        SubSystem subSystem = (SubSystem) single(single(view, SimulinkModel.class).getContains());
        Assertions.assertEquals("cal1", subSystem.getName());
        Assertions.assertEquals("pad1", single(subSystem.getSubBlocks()).getName());

        Assertions.assertEquals(2, single(view, CAD_Model.class).getNamespaces().size());

        // Parts of unknown kind are unclassified until the brake system decides their kind
        BOM bom = single(view, BOM.class);
        Assertions.assertEquals(List.of("cal1:BrakeCaliper", "pad1:BrakePad"),
                bom.getItems().stream().map(item -> item.getId() + ":" + item.getType().getName()).sorted().toList());
        Assertions.assertEquals(List.of("BrakeCaliper", "BrakePad"), bom.getItemTypes().stream().map(ItemType::getName).sorted().toList());

        List<SwComponentType> components = swComponents(single(view, AUTOSAR.class));
        Assertions.assertEquals(2, components.size());
        CompositionSwComponentType composition = components.stream()
                .filter(CompositionSwComponentType.class::isInstance)
                .map(CompositionSwComponentType.class::cast)
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals("pad1", single(composition.getComponents()).getType().getShortName());
    }

    private static Parameter diameterParameter(SimulinkModel simulinkModel) {
        return single(single(simulinkModel.getContains()).getParameters());
    }

    private static NumericParameter diameterNumericParameter(CAD_Model cadModel) {
        return (NumericParameter) single(single(cadModel.getNamespaces()).getParameters());
    }

    private static List<SwComponentType> swComponents(AUTOSAR autosar) {
        return autosar.getArpackage()
                .stream()
                .flatMap(arPackage -> arPackage.getElements().stream())
                .filter(SwComponentType.class::isInstance)
                .map(SwComponentType.class::cast)
                .toList();
    }

    private static <T> T single(View view, Class<T> rootType) {
        return single(view.getRootObjects(rootType));
    }

    private static <T> T single(java.util.Collection<T> elements) {
        Assertions.assertEquals(1, elements.size(), "Expected exactly one element, but got " + elements);
        return elements.iterator().next();
    }

    private static boolean contains(Iterable<String> choices, String choice) {
        for (String it : choices) {
            if (it.equals(choice)) {
                return true;
            }
        }
        return false;
    }
}
