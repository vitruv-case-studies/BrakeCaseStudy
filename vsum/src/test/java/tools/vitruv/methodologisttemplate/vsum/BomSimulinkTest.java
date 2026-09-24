package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bom.BOM;
import bom.BoolParameterValue;
import bom.BomFactory;
import bom.DataType;
import bom.DoubleParameterValue;
import bom.IntegerParameterValue;
import bom.Item;
import bom.ItemType;
import bom.ParameterDefinition;
import bom.ParameterValue;
import bom.StringParameterValue;
import mir.reactions.bom2simulink.Bom2simulinkChangePropagationSpecification;
import mir.reactions.simulink2bom.Simulink2bomChangePropagationSpecification;
import simulink.Block;
import simulink.Parameter;
import simulink.SimuLinkFactory;
import simulink.SimulinkModel;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.VirtualModel;

class BomSimulinkTest {

    private final TestUtil util = new TestUtil();

    @BeforeAll
    static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    @Test
    void propagatesBomItemsAndTypedValuesToSimulink(@TempDir Path tempDir) {
        VirtualModel vsum = createVirtualModel(tempDir);

        CommittableView initialView = util.getDefaultView(vsum, List.of(BOM.class, ItemType.class))
                .withChangeDerivingTrait();
        util.modifyView(initialView, view -> {
            ItemType type = BomFactory.eINSTANCE.createItemType();
            type.setName("BrakeDisk");
            type.getParameterDefinitions().add(definition("Material", DataType.ESTRING));
            type.getParameterDefinitions().add(definition("Hole Number", DataType.EINT));
            type.getParameterDefinitions().add(definition("Diameter", DataType.EDOUBLE));
            type.getParameterDefinitions().add(definition("Ventilated", DataType.EBOOLEAN));
            view.registerRoot(type, URI.createFileURI(tempDir.resolve("types.xmi").toString()));

            BOM bom = BomFactory.eINSTANCE.createBOM();
            bom.setName("Brake BOM");
            view.registerRoot(bom, URI.createFileURI(tempDir.resolve("bom.xmi").toString()));
        });

        CommittableView bomView = util.getDefaultView(vsum,
                        List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .withChangeRecordingTrait();
        util.modifyView(bomView, view -> {
            ItemType type = view.getRootObjects(ItemType.class).iterator().next();
            Item item = BomFactory.eINSTANCE.createItem();
            item.setId("disk-1");
            item.setType(type);
            item.setNumberOfItems(2);
            item.getParameterValues().add(stringValue(type, "Material", "steel"));
            item.getParameterValues().add(integerValue(type, "Hole Number", 5));
            item.getParameterValues().add(doubleValue(type, "Diameter", 31.5));
            item.getParameterValues().add(booleanValue(type, "Ventilated", true));
            view.getRootObjects(BOM.class).iterator().next().getItems().add(item);
        });

        SimulinkModel result = getSimulinkModel(vsum);
        assertEquals("Brake BOM", result.getName());
        assertEquals("1.0", result.getVersion());
        assertEquals(2, result.getContains().size());
        result.getContains().forEach(block -> {
            assertEquals("BrakeDisk", block.getName());
            assertParameter(block, "Material", "string", "steel");
            assertParameter(block, "Hole Number", "int32", "5");
            assertParameter(block, "Diameter", "double", "31.5");
            assertParameter(block, "Ventilated", "boolean", "true");
        });

        CommittableView updateView = util.getDefaultView(vsum,
                        List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .withChangeRecordingTrait();
        util.modifyView(updateView, view -> {
            BOM bom = view.getRootObjects(BOM.class).iterator().next();
            bom.setName("Updated BOM");
            Item item = bom.getItems().get(0);
            item.setNumberOfItems(1);
            ((IntegerParameterValue) value(item, "Hole Number")).setValue(6);
            value(item, "Hole Number").getDefinition().setName("Number of Holes");
        });

        SimulinkModel updated = getSimulinkModel(vsum);
        assertEquals("Updated BOM", updated.getName());
        assertEquals(1, updated.getContains().size());
        assertParameter(updated.getContains().get(0), "Number of Holes", "int32", "6");

        CommittableView deleteBlockView = util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeRecordingTrait();
        util.modifyView(deleteBlockView,
                view -> view.getRootObjects(SimulinkModel.class).iterator().next().getContains().clear());
        assertTrue(getBom(vsum).getItems().isEmpty());
    }

    @Test
    void propagatesSimulinkBlocksAndTypedValuesToBom(@TempDir Path tempDir) {
        VirtualModel vsum = createVirtualModel(tempDir);

        CommittableView simulinkView = util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeDerivingTrait();
        util.modifyView(simulinkView, view -> {
            SimulinkModel model = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            model.setName("Control Model");
            model.setVersion("1.0");
            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName("Controller");
            block.getParameters().add(parameter("Mode", "string", "sport"));
            block.getParameters().add(parameter("Retries", "int32", "3"));
            block.getParameters().add(parameter("Gain", "double", "2.5"));
            block.getParameters().add(parameter("Enabled", "boolean", "true"));
            model.getContains().add(block);
            view.registerRoot(model, URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        BOM result = getBom(vsum);
        assertEquals("Control Model", result.getName());
        assertEquals(1, result.getItems().size());
        Item item = result.getItems().get(0);
        assertEquals("Controller", item.getId());
        assertEquals("Controller", item.getType().getName());
        assertEquals(1, item.getNumberOfItems());
        assertEquals("sport", assertInstanceOf(StringParameterValue.class, value(item, "Mode")).getValue());
        assertEquals(3, assertInstanceOf(IntegerParameterValue.class, value(item, "Retries")).getValue());
        assertEquals(2.5, assertInstanceOf(DoubleParameterValue.class, value(item, "Gain")).getValue());
        assertTrue(assertInstanceOf(BoolParameterValue.class, value(item, "Enabled")).isValue());

        CommittableView updateView = util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeRecordingTrait();
        util.modifyView(updateView, view -> {
            SimulinkModel model = view.getRootObjects(SimulinkModel.class).iterator().next();
            model.setName("Updated Control Model");
            Block block = model.getContains().get(0);
            block.setName("Updated Controller");
            Parameter retries = block.getParameters().stream()
                    .filter(candidate -> "Retries".equals(candidate.getName())).findFirst().orElseThrow();
            retries.setName("Attempts");
            retries.setValue("4");
        });

        BOM updated = getBom(vsum);
        assertEquals("Updated Control Model", updated.getName());
        assertEquals("Updated Controller", updated.getItems().get(0).getType().getName());
        assertEquals(4, assertInstanceOf(IntegerParameterValue.class,
                value(updated.getItems().get(0), "Attempts")).getValue());

        CommittableView deleteParameterView = util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeRecordingTrait();
        util.modifyView(deleteParameterView, view -> {
            Block block = view.getRootObjects(SimulinkModel.class).iterator().next().getContains().get(0);
            block.getParameters().removeIf(candidate -> "Mode".equals(candidate.getName()));
        });
        assertEquals(3, getBom(vsum).getItems().get(0).getParameterValues().size());
    }

    private VirtualModel createVirtualModel(Path tempDir) {
        return util.createDefaultVirtualModel(tempDir, List.of(
                new Bom2simulinkChangePropagationSpecification(),
                new Simulink2bomChangePropagationSpecification()));
    }

    private SimulinkModel getSimulinkModel(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .getRootObjects(SimulinkModel.class).iterator().next();
    }

    private BOM getBom(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .getRootObjects(BOM.class).iterator().next();
    }

    private static ParameterDefinition definition(String name, DataType dataType) {
        ParameterDefinition definition = BomFactory.eINSTANCE.createParameterDefinition();
        definition.setName(name);
        definition.setDataType(dataType);
        return definition;
    }

    private static StringParameterValue stringValue(ItemType type, String name, String text) {
        StringParameterValue value = BomFactory.eINSTANCE.createStringParameterValue();
        value.setDefinition(definition(type, name));
        value.setValue(text);
        return value;
    }

    private static IntegerParameterValue integerValue(ItemType type, String name, int number) {
        IntegerParameterValue value = BomFactory.eINSTANCE.createIntegerParameterValue();
        value.setDefinition(definition(type, name));
        value.setValue(number);
        return value;
    }

    private static DoubleParameterValue doubleValue(ItemType type, String name, double number) {
        DoubleParameterValue value = BomFactory.eINSTANCE.createDoubleParameterValue();
        value.setDefinition(definition(type, name));
        value.setValue(number);
        return value;
    }

    private static BoolParameterValue booleanValue(ItemType type, String name, boolean flag) {
        BoolParameterValue value = BomFactory.eINSTANCE.createBoolParameterValue();
        value.setDefinition(definition(type, name));
        value.setValue(flag);
        return value;
    }

    private static ParameterDefinition definition(ItemType type, String name) {
        return type.getParameterDefinitions().stream()
                .filter(candidate -> name.equals(candidate.getName())).findFirst().orElseThrow();
    }

    private static ParameterValue value(Item item, String name) {
        return item.getParameterValues().stream()
                .filter(candidate -> name.equals(candidate.getDefinition().getName())).findFirst().orElseThrow();
    }

    private static Parameter parameter(String name, String type, String value) {
        Parameter parameter = SimuLinkFactory.eINSTANCE.createParameter();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setValue(value);
        return parameter;
    }

    private static void assertParameter(Block block, String name, String type, String value) {
        Parameter parameter = block.getParameters().stream()
                .filter(candidate -> name.equals(candidate.getName())).findFirst().orElseThrow();
        assertEquals(type, parameter.getType());
        assertEquals(value, parameter.getValue());
    }
}
