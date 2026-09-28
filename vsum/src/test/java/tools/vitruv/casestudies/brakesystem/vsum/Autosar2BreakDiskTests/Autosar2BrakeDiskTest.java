package tools.vitruv.casestudies.brakesystem.vsum.Autosar2BreakDiskTests;

import autosar.*;
import brakesystem.ABSSensor;
import brakesystem.Brakesystem;
import mir.reactions.autosar2brakesystem.Autosar2brakesystemChangePropagationSpecification;
import mir.reactions.brakesystem2autosar.Brakesystem2autosarChangePropagationSpecification;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

import java.nio.file.Path;
import java.util.List;

public class Autosar2BrakeDiskTest extends TestBase {
    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Autosar2brakesystemChangePropagationSpecification(),
                new Brakesystem2autosarChangePropagationSpecification());
    }

    @Test public void testCreateAndRegisterAutosarModel(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.modifyView(util.getDefaultView(vsum, List.of(AUTOSAR.class)).withChangeDerivingTrait(),
                (CommittableView v) -> {
                    AUTOSAR autosarModel = AutoSARFactory.eINSTANCE.createAUTOSAR();
                    v.registerRoot(autosarModel,
                            URI.createFileURI(tempDir.resolve("example.autosar").toString()));
                });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                (View v) -> {
                    Brakesystem
                            brakesystem =
                            v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
                    return brakesystem != null;
                }));
    }

    @Test public void testCreateSensor(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.modifyView(util.getDefaultView(vsum, List.of(AUTOSAR.class)).withChangeDerivingTrait(),
                (CommittableView v) -> {
                    AUTOSAR autosarModel = AutoSARFactory.eINSTANCE.createAUTOSAR();
                    v.registerRoot(autosarModel,
                            URI.createFileURI(tempDir.resolve("example.autosar").toString()));

                    ARPackage arPackage = AutoSARFactory.eINSTANCE.createARPackage();
                    autosarModel.getArpackage().add(arPackage);

                    SensorActuatorSwComponentType
                            sensor =
                            AutoSARFactory.eINSTANCE.createSensorActuatorSwComponentType();
                    sensor.setShortName("Sensor1");
                    arPackage.getElements().add(sensor);
                });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                (View v) -> {
                    Brakesystem
                            brakesystem =
                            v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
                    ABSSensor sensor = (ABSSensor) brakesystem.getBrakeComponents().getFirst();
                    return sensor.getId().equals("Sensor1");
                }));
    }
}
