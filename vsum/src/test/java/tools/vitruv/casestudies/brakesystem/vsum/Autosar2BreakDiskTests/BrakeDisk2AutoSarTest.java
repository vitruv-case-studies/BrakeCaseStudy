package tools.vitruv.casestudies.brakesystem.vsum.Autosar2BreakDiskTests;

import autosar.AUTOSAR;
import autosar.SD;
import autosar.SensorActuatorSwComponentType;
import brakesystem.*;
import mir.reactions.autosar2brakesystem.Autosar2brakesystemChangePropagationSpecification;
import mir.reactions.brakesystem2autosar.Brakesystem2autosarChangePropagationSpecification;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class BrakeDisk2AutoSarTest extends TestBase {
    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Autosar2brakesystemChangePropagationSpecification(),
                new Brakesystem2autosarChangePropagationSpecification());
    }

    @Test void absSensorInsertionAndPropagationTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class))
                .withChangeRecordingTrait(), (CommittableView v) -> {
            ABSSensor sensor = BrakesystemFactory.eINSTANCE.createABSSensor();
            sensor.setId("absSensor1");

            sensor.setSpecificationType("ExampleSpecification");
            sensor.setFittingDepth(120);

            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(sensor);
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(AUTOSAR.class)),
                (View v) -> {
                    AUTOSAR autosar = v.getRootObjects(AUTOSAR.class).iterator().next();
                    SensorActuatorSwComponentType
                            sensor =
                            (SensorActuatorSwComponentType) autosar.getArpackage()
                                    .getFirst()
                                    .getElements()
                                    .getFirst();

                    SD
                            specificationType =
                            sensor.getAdminData()
                                    .getSdgs()
                                    .getFirst()
                                    .getSd()
                                    .stream()
                                    .filter(it -> it.getKey().equals("specificationType"))
                                    .findFirst()
                                    .orElseThrow();
                    SD
                            fittingDepth =
                            sensor.getAdminData()
                                    .getSdgs()
                                    .getFirst()
                                    .getSd()
                                    .stream()
                                    .filter(it -> it.getKey().equals("fittingDepth"))
                                    .findFirst()
                                    .orElseThrow();

                    return sensor.getShortName().equals("absSensor1") &&
                            specificationType.getValue().equals("ExampleSpecification") &&
                            fittingDepth.getValue().equals(120);
                }));
    }

    @Test void absSensorChangeFittingDepthTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class))
                .withChangeRecordingTrait(), (CommittableView v) -> {
            ABSSensor sensor = BrakesystemFactory.eINSTANCE.createABSSensor();
            sensor.setId("absSensor1");

            sensor.setSpecificationType("ExampleSpecification");
            sensor.setFittingDepth(120);

            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(sensor);
        });

        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class))
                .withChangeRecordingTrait(), (CommittableView v) -> {
            ABSSensor
                    sensor =
                    (ABSSensor) v.getRootObjects(Brakesystem.class)
                            .iterator()
                            .next()
                            .getBrakeComponents()
                            .getFirst();
            sensor.setFittingDepth(130);
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(AUTOSAR.class)),
                (View v) -> {
                    AUTOSAR autosar = v.getRootObjects(AUTOSAR.class).iterator().next();
                    SensorActuatorSwComponentType
                            sensor =
                            (SensorActuatorSwComponentType) autosar.getArpackage()
                                    .getFirst()
                                    .getElements()
                                    .getFirst();

                    SD
                            specificationType =
                            sensor.getAdminData()
                                    .getSdgs()
                                    .getFirst()
                                    .getSd()
                                    .stream()
                                    .filter(it -> it.getKey().equals("specificationType"))
                                    .findFirst()
                                    .orElseThrow();
                    SD
                            fittingDepth =
                            sensor.getAdminData()
                                    .getSdgs()
                                    .getFirst()
                                    .getSd()
                                    .stream()
                                    .filter(it -> it.getKey().equals("fittingDepth"))
                                    .findFirst()
                                    .orElseThrow();

                    return sensor.getShortName().equals("absSensor1") &&
                            specificationType.getValue().equals("ExampleSpecification") &&
                            fittingDepth.getValue().equals(130);
                }));
    }

    @Test void absSensorDeleteTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class))
                .withChangeRecordingTrait(), (CommittableView v) -> {
            ABSSensor sensor = BrakesystemFactory.eINSTANCE.createABSSensor();
            sensor.setId("absSensor1");

            sensor.setSpecificationType("ExampleSpecification");
            sensor.setFittingDepth(120);

            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(sensor);
        });

        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class))
                .withChangeRecordingTrait(), (CommittableView v) -> {
            v.getRootObjects(Brakesystem.class)
                    .iterator()
                    .next()
                    .getBrakeComponents()
                    .removeFirst();
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(AUTOSAR.class)),
                (View v) -> {
                    AUTOSAR autosar = v.getRootObjects(AUTOSAR.class).iterator().next();
                    return autosar.getArpackage().getFirst().getElements().isEmpty();
                }));
    }
}
