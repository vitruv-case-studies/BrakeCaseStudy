package tools.vitruv.casestudies.brakesystem.vsum.Autosar2BreakDiskTests;

import autosar.*;
import brakesystem.ABSSensor;
import brakesystem.Brakesystem;
import mir.reactions.autosar2brakesystem.Autosar2brakesystemChangePropagationSpecification;
import mir.reactions.brakesystem2autosar.Brakesystem2autosarChangePropagationSpecification;
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

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public class Autosar2BrakeDiskTest {
    TestUtil testUtil = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Autosar2brakesystemChangePropagationSpecification(), new Brakesystem2autosarChangePropagationSpecification());

    @BeforeAll
    public static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    @Test
    public void testCreateAndRegisterAutosarModel(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir, necessaryCPS);

        testUtil.modifyView(testUtil.getDefaultView(vsum, List.of(AUTOSAR.class)).withChangeDerivingTrait(), (CommittableView v) -> {
            AUTOSAR autosarModel = AutoSARFactory.eINSTANCE.createAUTOSAR();
            v.registerRoot(autosarModel, URI.createFileURI(tempDir.resolve("example.autosar").toString()));
        });

        Assertions.assertTrue(assertView(testUtil.getDefaultView(vsum, List.of(Brakesystem.class)), (View v) -> {
            Brakesystem brakesystem = v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
            return brakesystem != null;
        }));
    }

    @Test
    public void testCreateSensor(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir, necessaryCPS);

        testUtil.modifyView(testUtil.getDefaultView(vsum, List.of(AUTOSAR.class)).withChangeDerivingTrait(), (CommittableView v) -> {
            AUTOSAR autosarModel = AutoSARFactory.eINSTANCE.createAUTOSAR();
            v.registerRoot(autosarModel, URI.createFileURI(tempDir.resolve("example.autosar").toString()));

            ARPackage arPackage = AutoSARFactory.eINSTANCE.createARPackage();
            autosarModel.getArpackage().add(arPackage);

            SensorActuatorSwComponentType sensor = AutoSARFactory.eINSTANCE.createSensorActuatorSwComponentType();
            sensor.setShortName("Sensor1");
            arPackage.getElements().add(sensor);
        });

        Assertions.assertTrue(assertView(testUtil.getDefaultView(vsum, List.of(Brakesystem.class)), (View v) -> {
            Brakesystem brakesystem = v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
            ABSSensor sensor = (ABSSensor) brakesystem.getBrakeComponents().getFirst();
            return sensor.getId().equals("Sensor1");
        }));
    }

    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
        return viewAssertionFunction.apply(view);
    }
}
