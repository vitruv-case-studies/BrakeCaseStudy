package tools.vitruv.casestudies.brakesystem.vsum.Autosar2CadTests;

import autosar.ARPackage;
import autosar.AUTOSAR;
import autosar.SD;
import autosar.SDG;
import brakesystem.ABSSensor;
import brakesystem.BrakeCaliper;
import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import edu.kit.ipd.sdq.metamodels.cad.*;
import mir.reactions.autosar2cad.Autosar2cadChangePropagationSpecification;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2autosar.Cad2autosarChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class Cad2AutosarTest {
    TestUtil util = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Autosar2cadChangePropagationSpecification(), new Cad2autosarChangePropagationSpecification());

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    @Test
    void rootInsertionAndPropagationTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.modifyView(util.getDefaultView(vsum, List.of(CAD_Model.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            CAD_Model cad = CadFactory.eINSTANCE.createCAD_Model();
            cad.setName("TestCADModel");
            v.registerRoot(cad, URI.createFileURI(tempDir.resolve("example.cad").toString()));
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(AUTOSAR.class)), (View v) -> {
            AUTOSAR autosar = v.getRootObjects(AUTOSAR.class).stream().findFirst().orElseThrow();
            ARPackage rootPackage = autosar.getArpackage().getFirst();

            return rootPackage.getShortName().equals("TestCADModel");
        }));
    }

    @Test
    void testCreateNamespace(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.modifyView(util.getDefaultView(vsum, List.of(CAD_Model.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            CAD_Model cad = CadFactory.eINSTANCE.createCAD_Model();
            cad.setName("TestCADModel");
            v.registerRoot(cad, URI.createFileURI(tempDir.resolve("example.cad").toString()));

            Namespace namespace = CadFactory.eINSTANCE.createNamespace();
            namespace.setName("TestNamespace");
            namespace.setId("TestNamespaceID");
            cad.getNamespaces().add(namespace);
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(AUTOSAR.class)), (View v) -> {
            AUTOSAR autosar = v.getRootObjects(AUTOSAR.class).stream().findFirst().orElseThrow();
            ARPackage rootPackage = autosar.getArpackage().getFirst();
            ARPackage testPackage = rootPackage.getSubPackages().getFirst();

            SDG cadNamespace = testPackage.getAdminData().getSdgs().stream().filter(sdg -> sdg.getGid().equals("cadNamespace")).findFirst().orElseThrow();
            SD cadNamespaceId = cadNamespace.getSd().stream().filter(sd -> sd.getKey().equals("cadNamespaceId")).findFirst().orElseThrow();

            return rootPackage.getSubPackages().size() == 1 && testPackage.getShortName().equals("TestNamespace") && cadNamespaceId.getValue().equals("TestNamespaceID");
        }));
    }

    @Test
    void testCreateParameter(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.modifyView(util.getDefaultView(vsum, List.of(CAD_Model.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            CAD_Model cad = CadFactory.eINSTANCE.createCAD_Model();
            cad.setName("TestCADModel");
            v.registerRoot(cad, URI.createFileURI(tempDir.resolve("example.cad").toString()));

            Namespace namespace = CadFactory.eINSTANCE.createNamespace();
            namespace.setName("TestNamespace");
            cad.getNamespaces().add(namespace);

            StringParameter stringParameter = CadFactory.eINSTANCE.createStringParameter();
            stringParameter.setId("ID1");
            stringParameter.setName("TestStringParameter");
            stringParameter.setValue("TestValue");
            stringParameter.setUnit(Unit.CM);
            namespace.getParameters().add(stringParameter);

            NumericParameter numericParameter = CadFactory.eINSTANCE.createNumericParameter();
            numericParameter.setId("ID2");
            numericParameter.setName("TestNumericParameter");
            numericParameter.setValue(42.0f);
            numericParameter.setUnit(Unit.MM);
            namespace.getParameters().add(numericParameter);

            BooleanParameter booleanParameter = CadFactory.eINSTANCE.createBooleanParameter();
            booleanParameter.setId("ID3");
            booleanParameter.setName("TestBooleanParameter");
            booleanParameter.setValue(true);
            booleanParameter.setDescription("This is a test parameter");
            namespace.getParameters().add(booleanParameter);
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(AUTOSAR.class)), (View v) -> {
            AUTOSAR autosar = v.getRootObjects(AUTOSAR.class).stream().findFirst().orElseThrow();
            ARPackage rootPackage = autosar.getArpackage().getFirst();
            ARPackage testPackage = rootPackage.getSubPackages().getFirst();

            SDG stringParameter = testPackage.getAdminData().getSdgs().stream().filter(sdg -> sdg.getGid().equals("ID1")).findFirst().orElseThrow();
            SD stringParameterName = stringParameter.getSd().stream().filter(sd -> sd.getKey().equals("name")).findFirst().orElseThrow();
            SD stringParameterValue = stringParameter.getSd().stream().filter(sd -> sd.getKey().equals("value")).findFirst().orElseThrow();
            SD stringParameterUnit = stringParameter.getSd().stream().filter(sd -> sd.getKey().equals("unit")).findFirst().orElseThrow();
            SD stringParameterType = stringParameter.getSd().stream().filter(sd -> sd.getKey().equals("type")).findFirst().orElseThrow();
            boolean stringParameterOk = stringParameterName.getValue().equals("TestStringParameter") && stringParameterValue.getValue().equals("TestValue") && stringParameterUnit.getValue().equals(Unit.CM) && stringParameterType.getValue().equals("string");

            SDG numericParameter = testPackage.getAdminData().getSdgs().stream().filter(sdg -> sdg.getGid().equals("ID2")).findFirst().orElseThrow();
            SD numericParameterName = numericParameter.getSd().stream().filter(sd -> sd.getKey().equals("name")).findFirst().orElseThrow();
            SD numericParameterValue = numericParameter.getSd().stream().filter(sd -> sd.getKey().equals("value")).findFirst().orElseThrow();
            SD numericParameterUnit = numericParameter.getSd().stream().filter(sd -> sd.getKey().equals("unit")).findFirst().orElseThrow();
            boolean numericParameterOk = numericParameterName.getValue().equals("TestNumericParameter") && numericParameterValue.getValue().equals(42.0f) && numericParameterUnit.getValue().equals(Unit.MM);

            SDG booleanParameter = testPackage.getAdminData().getSdgs().stream().filter(sdg -> sdg.getGid().equals("ID3")).findFirst().orElseThrow();
            SD booleanParameterName = booleanParameter.getSd().stream().filter(sd -> sd.getKey().equals("name")).findFirst().orElseThrow();
            SD booleanParameterDescription = booleanParameter.getSd().stream().filter(sd -> sd.getKey().equals("description")).findFirst().orElseThrow();
            SD booleanParameterValue = booleanParameter.getSd().stream().filter(sd -> sd.getKey().equals("value")).findFirst().orElseThrow();
            boolean booleanParameterOk = booleanParameterName.getValue().equals("TestBooleanParameter") && booleanParameterDescription.getValue().equals("This is a test parameter") && booleanParameterValue.getValue().equals(true);

            return rootPackage.getSubPackages().size() == 1 && testPackage.getShortName().equals("TestNamespace") && stringParameterOk && numericParameterOk && booleanParameterOk;
        }));
    }

    @Test
    void testParameterDeletion(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.modifyView(util.getDefaultView(vsum, List.of(CAD_Model.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            CAD_Model cad = CadFactory.eINSTANCE.createCAD_Model();
            cad.setName("TestCADModel");
            v.registerRoot(cad, URI.createFileURI(tempDir.resolve("example.cad").toString()));

            Namespace namespace = CadFactory.eINSTANCE.createNamespace();
            namespace.setName("TestNamespace");
            namespace.setId("TestNamespaceID");
            cad.getNamespaces().add(namespace);

            StringParameter stringParameter = CadFactory.eINSTANCE.createStringParameter();
            stringParameter.setId("ID1");
            stringParameter.setName("TestStringParameter");
            stringParameter.setValue("TestValue");
            stringParameter.setUnit(Unit.CM);
            namespace.getParameters().add(stringParameter);
        });

        util.modifyView(util.getDefaultView(vsum, List.of(CAD_Model.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            CAD_Model cad = v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
            cad.getNamespaces().getFirst().getParameters().removeFirst();
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(AUTOSAR.class)), (View v) -> {
            AUTOSAR autosar = v.getRootObjects(AUTOSAR.class).stream().findFirst().orElseThrow();
            ARPackage rootPackage = autosar.getArpackage().getFirst();
            ARPackage testPackage = rootPackage.getSubPackages().getFirst();

            return testPackage.getAdminData().getSdgs().size() == 1 && testPackage.getAdminData().getSdgs().getFirst().getGid().equals("cadNamespace");
        }));
    }

    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
        return viewAssertionFunction.apply(view);
    }
}
