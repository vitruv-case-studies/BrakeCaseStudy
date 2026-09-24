package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import autosar.ARPackage;
import autosar.AUTOSAR;
import autosar.AdminData;
import autosar.AtomicSwComponentType;
import autosar.AutoSARFactory;
import autosar.SD;
import autosar.SDG;
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
import mir.reactions.autosar2bom.Autosar2bomChangePropagationSpecification;
import mir.reactions.bom2autosar.Bom2autosarChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.VirtualModel;

class BomAutosarTest {

    private final TestUtil util = new TestUtil();

    @BeforeAll
    static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    @Test
    void propagatesBomItemsAndParametersToAutosar(@TempDir Path tempDir) {
        VirtualModel vsum = createVirtualModel(tempDir);

        CommittableView initialView = util.getDefaultView(vsum, List.of(BOM.class, ItemType.class))
                .withChangeDerivingTrait();
        util.modifyView(initialView, view -> {
            ItemType type = BomFactory.eINSTANCE.createItemType();
            type.setName("BrakeController");
            type.getParameterDefinitions().add(definition("Material", DataType.ESTRING));
            type.getParameterDefinitions().add(definition("Pins", DataType.EINT));
            type.getParameterDefinitions().add(definition("Gain", DataType.EDOUBLE));
            type.getParameterDefinitions().add(definition("Enabled", DataType.EBOOLEAN));
            view.registerRoot(type, URI.createFileURI(tempDir.resolve("types.xmi").toString()));

            BOM bom = BomFactory.eINSTANCE.createBOM();
            bom.setName("Brake Package");
            view.registerRoot(bom, URI.createFileURI(tempDir.resolve("bom.xmi").toString()));
        });

        CommittableView bomView = util.getDefaultView(vsum,
                        List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .withChangeRecordingTrait();
        util.modifyView(bomView, view -> {
            ItemType type = view.getRootObjects(ItemType.class).iterator().next();
            Item item = BomFactory.eINSTANCE.createItem();
            item.setId("controller-1");
            item.setType(type);
            item.setNumberOfItems(2);

            StringParameterValue material = BomFactory.eINSTANCE.createStringParameterValue();
            material.setDefinition(findDefinition(type, "Material"));
            material.setValue("steel");
            item.getParameterValues().add(material);

            IntegerParameterValue pins = BomFactory.eINSTANCE.createIntegerParameterValue();
            pins.setDefinition(findDefinition(type, "Pins"));
            pins.setValue(8);
            pins.setDescription("Connector pins");
            item.getParameterValues().add(pins);

            DoubleParameterValue gain = BomFactory.eINSTANCE.createDoubleParameterValue();
            gain.setDefinition(findDefinition(type, "Gain"));
            gain.setValue(2.5);
            gain.setUnit("m");
            item.getParameterValues().add(gain);

            BoolParameterValue enabled = BomFactory.eINSTANCE.createBoolParameterValue();
            enabled.setDefinition(findDefinition(type, "Enabled"));
            enabled.setValue(true);
            item.getParameterValues().add(enabled);

            view.getRootObjects(BOM.class).iterator().next().getItems().add(item);
        });

        AUTOSAR result = getAutosar(vsum);
        assertEquals(1, result.getArpackage().size());
        ARPackage rootPackage = result.getArpackage().get(0);
        assertEquals("Brake Package", rootPackage.getShortName());
        List<AtomicSwComponentType> components = components(rootPackage);
        assertEquals(2, components.size());
        assertEquals(Set.of("controller-1", "controller-1-2"), components.stream()
                .map(AtomicSwComponentType::getUuid).collect(Collectors.toSet()));
        components.forEach(component -> {
            assertEquals("BrakeController", component.getShortName());
            assertGroup(component, "Material", "EString", "steel", null, null);
            assertGroup(component, "Pins", "EInt", "8", null, "Connector pins");
            assertGroup(component, "Gain", "EDouble", "2.5", "m", null);
            assertGroup(component, "Enabled", "EBoolean", "true", null, null);
        });

        CommittableView updateView = util.getDefaultView(vsum,
                        List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .withChangeRecordingTrait();
        util.modifyView(updateView, view -> {
            BOM bom = view.getRootObjects(BOM.class).iterator().next();
            bom.setName("Updated Package");
            Item item = bom.getItems().get(0);
            item.setId("controller-x");
            item.setNumberOfItems(1);
            item.getType().setName("UpdatedController");
            IntegerParameterValue pins = (IntegerParameterValue) findValue(item, "Pins");
            pins.setValue(10);
            pins.setUnit("COUNT");
            pins.getDefinition().setName("Pin Count");
        });

        ARPackage updatedPackage = getAutosar(vsum).getArpackage().get(0);
        assertEquals("Updated Package", updatedPackage.getShortName());
        AtomicSwComponentType updated = components(updatedPackage).get(0);
        assertEquals("controller-x", updated.getUuid());
        assertEquals("UpdatedController", updated.getShortName());
        assertGroup(updated, "Pin Count", "EInt", "10", "COUNT", "Connector pins");

        CommittableView deleteView = util.getDefaultView(vsum, List.of(AUTOSAR.class))
                .withChangeRecordingTrait();
        util.modifyView(deleteView, view -> view.getRootObjects(AUTOSAR.class).iterator().next()
                .getArpackage().get(0).getElements().clear());
        assertTrue(getBom(vsum).getItems().isEmpty());
    }

    @Test
    void propagatesAutosarComponentsAndAdministrativeParametersToBom(@TempDir Path tempDir) {
        VirtualModel vsum = createVirtualModel(tempDir);

        CommittableView autosarView = util.getDefaultView(vsum, List.of(AUTOSAR.class))
                .withChangeDerivingTrait();
        util.modifyView(autosarView, view -> {
            AUTOSAR model = AutoSARFactory.eINSTANCE.createAUTOSAR();
            ARPackage rootPackage = AutoSARFactory.eINSTANCE.createARPackage();
            rootPackage.setShortName("Controls");
            AtomicSwComponentType component = AutoSARFactory.eINSTANCE.createAtomicSwComponentType();
            component.setShortName("Controller");
            component.setUuid("controller-7");
            AdminData adminData = AutoSARFactory.eINSTANCE.createAdminData();
            adminData.getSdgs().add(group("Mode", "EString", "sport", null, null));
            adminData.getSdgs().add(group("Retries", "EInt", "3", null, "Retry count"));
            adminData.getSdgs().add(group("Gain", "EDouble", "1.25", "m", null));
            adminData.getSdgs().add(group("Enabled", "EBoolean", "true", null, null));
            component.setAdminData(adminData);
            rootPackage.getElements().add(component);
            model.getArpackage().add(rootPackage);
            view.registerRoot(model, URI.createFileURI(tempDir.resolve("autosar.xmi").toString()));
        });

        BOM result = getBom(vsum);
        assertEquals("Controls", result.getName());
        assertEquals(1, result.getItems().size());
        Item item = result.getItems().get(0);
        assertEquals("controller-7", item.getId());
        assertEquals("Controller", item.getType().getName());
        assertEquals(1, item.getNumberOfItems());
        assertEquals("sport", assertInstanceOf(StringParameterValue.class, findValue(item, "Mode")).getValue());
        assertEquals(3, assertInstanceOf(IntegerParameterValue.class, findValue(item, "Retries")).getValue());
        assertEquals("Retry count", findValue(item, "Retries").getDescription());
        DoubleParameterValue gain = assertInstanceOf(DoubleParameterValue.class, findValue(item, "Gain"));
        assertEquals(1.25, gain.getValue());
        assertEquals("m", gain.getUnit());
        assertTrue(assertInstanceOf(BoolParameterValue.class, findValue(item, "Enabled")).isValue());

        CommittableView updateView = util.getDefaultView(vsum, List.of(AUTOSAR.class))
                .withChangeRecordingTrait();
        util.modifyView(updateView, view -> {
            ARPackage rootPackage = view.getRootObjects(AUTOSAR.class).iterator().next().getArpackage().get(0);
            rootPackage.setShortName("Updated Controls");
            AtomicSwComponentType component = components(rootPackage).get(0);
            component.setShortName("UpdatedController");
            component.setUuid("controller-8");
            SDG retries = findGroup(component, "Retries");
            entry(retries, "name").setValue("Attempts");
            entry(retries, "value").setValue("4");
            entry(retries, "description").setValue("Attempt count");
        });

        BOM updated = getBom(vsum);
        Item updatedItem = updated.getItems().get(0);
        assertEquals("Updated Controls", updated.getName());
        assertEquals("controller-8", updatedItem.getId());
        assertEquals("UpdatedController", updatedItem.getType().getName());
        ParameterValue attempts = findValue(updatedItem, "Attempts");
        assertEquals(4, ((IntegerParameterValue) attempts).getValue());
        assertEquals("Attempt count", attempts.getDescription());

    }

    private VirtualModel createVirtualModel(Path tempDir) {
        return util.createDefaultVirtualModel(tempDir, List.of(
                new Bom2autosarChangePropagationSpecification(),
                new Autosar2bomChangePropagationSpecification()));
    }

    private AUTOSAR getAutosar(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(AUTOSAR.class))
                .getRootObjects(AUTOSAR.class).iterator().next();
    }

    private BOM getBom(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .getRootObjects(BOM.class).iterator().next();
    }

    private static List<AtomicSwComponentType> components(ARPackage rootPackage) {
        return rootPackage.getElements().stream().filter(AtomicSwComponentType.class::isInstance)
                .map(AtomicSwComponentType.class::cast).toList();
    }

    private static ParameterDefinition definition(String name, DataType dataType) {
        ParameterDefinition definition = BomFactory.eINSTANCE.createParameterDefinition();
        definition.setName(name);
        definition.setDataType(dataType);
        return definition;
    }

    private static ParameterDefinition findDefinition(ItemType type, String name) {
        return type.getParameterDefinitions().stream()
                .filter(candidate -> name.equals(candidate.getName())).findFirst().orElseThrow();
    }

    private static ParameterValue findValue(Item item, String name) {
        return item.getParameterValues().stream()
                .filter(value -> name.equals(value.getDefinition().getName())).findFirst().orElseThrow();
    }

    private static SDG group(String name, String type, String value, String unit, String description) {
        SDG group = AutoSARFactory.eINSTANCE.createSDG();
        group.setGid("BOM_PARAMETER");
        group.getSd().add(entry("name", name));
        group.getSd().add(entry("type", type));
        group.getSd().add(entry("value", value));
        if (unit != null) group.getSd().add(entry("unit", unit));
        if (description != null) group.getSd().add(entry("description", description));
        return group;
    }

    private static SD entry(String key, String value) {
        SD entry = AutoSARFactory.eINSTANCE.createSD();
        entry.setKey(key);
        entry.setValue(value);
        return entry;
    }

    private static SD entry(SDG group, String key) {
        return group.getSd().stream().filter(candidate -> key.equals(candidate.getKey())).findFirst().orElseThrow();
    }

    private static SDG findGroup(AtomicSwComponentType component, String parameterName) {
        return component.getAdminData().getSdgs().stream()
                .filter(group -> "BOM_PARAMETER".equals(group.getGid()))
                .filter(group -> group.getSd().stream().anyMatch(value ->
                        "name".equals(value.getKey()) && parameterName.equals(value.getValue())))
                .findFirst().orElseThrow();
    }

    private static void assertGroup(AtomicSwComponentType component, String name, String type,
            String value, String unit, String description) {
        SDG group = findGroup(component, name);
        assertEquals(type, entry(group, "type").getValue());
        assertEquals(value, entry(group, "value").getValue());
        if (unit == null) {
            assertTrue(group.getSd().stream().noneMatch(candidate -> "unit".equals(candidate.getKey())));
        } else {
            assertEquals(unit, entry(group, "unit").getValue());
        }
        if (description == null) {
            assertTrue(group.getSd().stream().noneMatch(candidate -> "description".equals(candidate.getKey())));
        } else {
            assertEquals(description, entry(group, "description").getValue());
        }
    }
}
