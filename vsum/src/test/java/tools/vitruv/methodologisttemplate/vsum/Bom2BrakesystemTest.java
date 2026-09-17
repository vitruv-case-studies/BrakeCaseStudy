package tools.vitruv.methodologisttemplate.vsum;

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

import bom.BOM;
import bom.BomFactory;
import bom.IntegerParameterValue;
import bom.Item;
import bom.ItemType;
import bom.ParameterDefinition;
import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import mir.reactions.bom2brakesystem.Bom2brakesystemChangePropagationSpecification;
import mir.reactions.brakesystem2bom.Brakesystem2bomChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.VirtualModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Bom2BrakesystemTest {

    private final TestUtil util = new TestUtil();

    @BeforeAll
    static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
    }

    @Test
    void createsAndUpdatesBrakeDiskFromBom(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir,
                List.of(new Bom2brakesystemChangePropagationSpecification(),
                        new Brakesystem2bomChangePropagationSpecification()));

        // ItemType and its definitions are separate roots: BOM.ecore has no
        // containment reference for them. Register them in their own resource.
        CommittableView initialView = util.getDefaultView(vsum, List.of(BOM.class, ItemType.class))
                .withChangeDerivingTrait();
        util.modifyView(initialView, view -> {
            ItemType diskType = BomFactory.eINSTANCE.createItemType();
            diskType.setName("BrakeDisk");
            ParameterDefinition diameter = BomFactory.eINSTANCE.createParameterDefinition();
            diameter.setName("Diameter");
            diskType.getParameterDefinitions().add(diameter);
            view.registerRoot(diskType, URI.createFileURI(tempDir.resolve("types.xmi").toString()));

            BOM bom = BomFactory.eINSTANCE.createBOM();
            bom.setName("Front brakes");
            view.registerRoot(bom, URI.createFileURI(tempDir.resolve("parts.xmi").toString()));
        });

        CommittableView bomView = util.getDefaultView(vsum,
                        List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .withChangeRecordingTrait();
        util.modifyView(bomView, view -> {
            ItemType diskType = view.getRootObjects(ItemType.class).iterator().next();
            Item diskItem = BomFactory.eINSTANCE.createItem();
            diskItem.setId("disk-1");
            diskItem.setType(diskType);
            diskItem.setNumberOfItems(2);
            IntegerParameterValue diameter = BomFactory.eINSTANCE.createIntegerParameterValue();
            diameter.setDefinition(diskType.getParameterDefinitions().get(0));
            diameter.setValue(120);
            diskItem.getParameterValues().add(diameter);
            view.getRootObjects(BOM.class).iterator().next().getItems().add(diskItem);
        });

        Brakesystem result = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .getRootObjects(Brakesystem.class).iterator().next();
        assertEquals("Front brakes", result.getInstanceName());
        assertEquals(2, result.getBrakeComponents().size());
        assertEquals(Set.of("disk-1", "disk-1-2"), result.getBrakeComponents().stream()
                .map(component -> component.getId()).collect(Collectors.toSet()));
        result.getBrakeComponents().forEach(component ->
                assertEquals(120, ((BrakeDisk) component).getDiameterInMM()));

        CommittableView removeOneView = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .withChangeRecordingTrait();
        util.modifyView(removeOneView, view -> view.getRootObjects(Brakesystem.class)
                .iterator().next().getBrakeComponents().remove(0));
        BOM afterRemovingOne = util.getDefaultView(vsum, List.of(BOM.class))
                .getRootObjects(BOM.class).iterator().next();
        assertEquals(1, afterRemovingOne.getItems().get(0).getNumberOfItems());

        CommittableView updateView = util.getDefaultView(vsum, List.of(BOM.class))
                .withChangeRecordingTrait();
        util.modifyView(updateView, view -> {
            BOM bom = view.getRootObjects(BOM.class).iterator().next();
            bom.setName("Rear brakes");
            bom.getItems().get(0).setNumberOfItems(3);
            IntegerParameterValue diameter = (IntegerParameterValue) bom.getItems().get(0)
                    .getParameterValues().get(0);
            diameter.setValue(130);
        });

        Brakesystem updated = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .getRootObjects(Brakesystem.class).iterator().next();
        assertEquals("Rear brakes", updated.getInstanceName());
        assertEquals(3, updated.getBrakeComponents().size());
        assertEquals(Set.of("disk-1", "disk-1-2", "disk-1-3"), updated.getBrakeComponents()
                .stream().map(component -> component.getId()).collect(Collectors.toSet()));
        updated.getBrakeComponents().forEach(component ->
                assertEquals(130, ((BrakeDisk) component).getDiameterInMM()));

        CommittableView shrinkView = util.getDefaultView(vsum, List.of(BOM.class))
                .withChangeRecordingTrait();
        util.modifyView(shrinkView, view -> view.getRootObjects(BOM.class)
                .iterator().next().getItems().get(0).setNumberOfItems(1));
        Brakesystem shrunk = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .getRootObjects(Brakesystem.class).iterator().next();
        assertEquals(1, shrunk.getBrakeComponents().size());

        CommittableView emptyView = util.getDefaultView(vsum, List.of(BOM.class))
                .withChangeRecordingTrait();
        util.modifyView(emptyView, view -> view.getRootObjects(BOM.class)
                .iterator().next().getItems().get(0).setNumberOfItems(0));
        Brakesystem empty = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .getRootObjects(Brakesystem.class).iterator().next();
        assertEquals(0, empty.getBrakeComponents().size());

        CommittableView regrowView = util.getDefaultView(vsum, List.of(BOM.class))
                .withChangeRecordingTrait();
        util.modifyView(regrowView, view -> view.getRootObjects(BOM.class)
                .iterator().next().getItems().get(0).setNumberOfItems(2));
        Brakesystem regrown = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .getRootObjects(Brakesystem.class).iterator().next();
        assertEquals(Set.of("disk-1", "disk-1-2"), regrown.getBrakeComponents().stream()
                .map(component -> component.getId()).collect(Collectors.toSet()));
        regrown.getBrakeComponents().forEach(component ->
                assertEquals(130, ((BrakeDisk) component).getDiameterInMM()));

        CommittableView deleteView = util.getDefaultView(vsum, List.of(BOM.class))
                .withChangeRecordingTrait();
        util.modifyView(deleteView, view -> view.getRootObjects(BOM.class)
                .iterator().next().getItems().clear());

        Brakesystem afterDeletion = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .getRootObjects(Brakesystem.class).iterator().next();
        assertEquals(0, afterDeletion.getBrakeComponents().size());
    }
}
