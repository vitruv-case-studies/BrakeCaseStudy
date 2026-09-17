package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bom.BOM;
import bom.IntegerParameterValue;
import bom.Item;
import bom.ItemType;
import bom.ParameterDefinition;
import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import mir.reactions.bom2brakesystem.Bom2brakesystemChangePropagationSpecification;
import mir.reactions.brakesystem2bom.Brakesystem2bomChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.VirtualModel;

class Brakesystem2BomTest {

    private final TestUtil util = new TestUtil();

    @BeforeAll
    static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    @Test
    void createsUpdatesAndDeletesBomItem(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, List.of(
                new Brakesystem2bomChangePropagationSpecification(),
                new Bom2brakesystemChangePropagationSpecification()));

        CommittableView initialView = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .withChangeDerivingTrait();
        util.modifyView(initialView, view -> {
            Brakesystem source = BrakesystemFactory.eINSTANCE.createBrakesystem();
            source.setInstanceName("Front brakes");
            view.registerRoot(source, URI.createFileURI(tempDir.resolve("brakes.xmi").toString()));
        });

        CommittableView brakeView = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .withChangeRecordingTrait();
        util.modifyView(brakeView, view -> {
            BrakeDisk disk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
            disk.setId("disk-1");
            disk.setDiameterInMM(120);
            disk.setVentilated(true);
            view.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(disk);
        });

        BOM bom = util.getDefaultView(vsum, List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .getRootObjects(BOM.class).iterator().next();
        assertEquals("Front brakes", bom.getName());
        assertEquals(1, bom.getItems().size());
        Item item = bom.getItems().get(0);
        assertEquals("disk-1", item.getId());
        assertEquals("BrakeDisk", item.getType().getName());
        assertEquals(1, item.getNumberOfItems());
        assertEquals(120, ((IntegerParameterValue) item.getParameterValues().stream()
                .filter(value -> value.getDefinition().getName().equals("Diameter"))
                .findFirst().orElseThrow()).getValue());

        CommittableView updateView = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .withChangeRecordingTrait();
        util.modifyView(updateView, view -> {
            Brakesystem source = view.getRootObjects(Brakesystem.class).iterator().next();
            source.setInstanceName("Rear brakes");
            ((BrakeDisk) source.getBrakeComponents().get(0)).setDiameterInMM(130);
        });
        BOM updated = util.getDefaultView(vsum, List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .getRootObjects(BOM.class).iterator().next();
        assertEquals("Rear brakes", updated.getName());
        assertEquals(130, ((IntegerParameterValue) updated.getItems().get(0).getParameterValues().stream()
                .filter(value -> value.getDefinition().getName().equals("Diameter"))
                .findFirst().orElseThrow()).getValue());

        CommittableView deleteView = util.getDefaultView(vsum, List.of(Brakesystem.class))
                .withChangeRecordingTrait();
        util.modifyView(deleteView, view -> view.getRootObjects(Brakesystem.class)
                .iterator().next().getBrakeComponents().clear());
        BOM afterDeletion = util.getDefaultView(vsum, List.of(BOM.class))
                .getRootObjects(BOM.class).iterator().next();
        assertEquals(0, afterDeletion.getItems().size());
    }
}
