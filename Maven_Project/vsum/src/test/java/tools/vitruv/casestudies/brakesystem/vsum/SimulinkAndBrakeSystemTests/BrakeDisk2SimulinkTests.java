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
                var simulinkModel = simulinkView.getRootObjects(SimulinkModel.class)
                    .iterator().next();
                util.findSimulinkBlockWithName(simulinkModel, brakeDisk.getId());
                return true;
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
                util.findSimulinkSubystemWithName(simulinkModel, brakeCaliper.getId());
                return true;
            })
        );
    }
}
