package tools.vitruv.casestudies.brakesystem.vsum.SimulinkAndBrakeSystemTests;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static tools.vitruv.casestudies.brakesystem.vsum.DefaultModelElements.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import brakesystem.Brakesystem;
import simulink.SimulinkModel;

class BrakeDisk2SimulinkTests extends BrakeDiskAndSimulinkTests {
    @Test
    void testCreateAndInsertBrakeDisk(@TempDir Path tempDir) {
        var vsum = createVirtualModel(tempDir);
        var brakesystemView = util.getBrakesystemView(vsum);
        var brakeDisk = createDefaultBrakeDisk();
        
        util.modifyView(brakesystemView, (view) -> {
            view.getRootObjects(Brakesystem.class).iterator().next()
                .getBrakeComponents()
                .add(brakeDisk);
        });

        var simulinkView = util.getSimulinkView(vsum);
        assertTrue(
            assertView(simulinkView, view -> {
                var simulinkModel = util.getRootOfSimulinkModel(simulinkView);
                var blockForBrakeDisk = util.findSimulinkBlockWithName(simulinkModel, brakeDisk.getId());

                return 
                    util.expectStringParameterForSimulink(blockForBrakeDisk, "OEM Number", "VW123456") &&
                    util.expectInt32ParameterForSimulink(blockForBrakeDisk, "Diameter", 120)
                    && util.expectInt32ParameterForSimulink(blockForBrakeDisk, "Centering Diameter", 20)
                    && util.expectInt32ParameterForSimulink(blockForBrakeDisk, "Rim Hole Number", 1)
                    && util.expectInt32ParameterForSimulink(blockForBrakeDisk, "Bolt Hole Circle", 60)
                    && util.expectInt32ParameterForSimulink(blockForBrakeDisk, "Brake Disk Thickness", 30)
                    && util.expectInt32ParameterForSimulink(blockForBrakeDisk, "Minimum Thickness", 25)
                    && util.expectBooleanParameterForSimulink(blockForBrakeDisk, "Ventilated", true);
            })
        );
    }

    @Test
    void testCreateAndInsertBrakeCaliper(@TempDir Path tempDir) {
        var vsum = createVirtualModel(tempDir);
        var brakesystemView = util.getBrakesystemView(vsum);
        var brakeCaliper = createDefaultBrakeCaliper();

        util.modifyView(brakesystemView, view -> {
            var brakesystemModel = util.getRootOfBrakesystemView(view);
            brakesystemModel.getBrakeComponents().add(brakeCaliper);
        });

        var simulinkView = util.getSimulinkView(vsum);
        assertTrue(
            assertView(simulinkView, view -> {
                var simulinkModel = util.getRootOfSimulinkModel(simulinkView);
                var caliperSubsystem = util.findSimulinkSubystemWithName(simulinkModel, brakeCaliper.getId());
                return util.expectInt32ParameterForSimulink(caliperSubsystem, "Brake Disk Thickness", 20)
                && util.expectInt32ParameterForSimulink(caliperSubsystem, "Piston Diameter", 10)
                && util.expectStringParameterForSimulink(caliperSubsystem, "Fitting Position", "left")
                && util.expectStringParameterForSimulink(caliperSubsystem, "Specification Type", "Single Brake Caliper");
            })
        );
    }
}
