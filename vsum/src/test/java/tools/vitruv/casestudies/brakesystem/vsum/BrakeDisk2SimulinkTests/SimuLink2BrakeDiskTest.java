package tools.vitruv.casestudies.brakesystem.vsum.BrakeDisk2SimulinkTests;

import brakesystem.BrakeCaliper;
import brakesystem.BrakeHose;
import brakesystem.BrakePad;
import brakesystem.Brakesystem;
import mir.reactions.brakesystem2simulink.Brakesystem2simulinkChangePropagationSpecification;
import mir.reactions.simulink2brakesystem.Simulink2brakesystemChangePropagationSpecification;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import simulink.Block;
import simulink.SimuLinkFactory;
import simulink.SimulinkModel;
import simulink.SubSystem;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

import java.nio.file.Path;
import java.util.List;

public class SimuLink2BrakeDiskTest extends TestBase {
    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Brakesystem2simulinkChangePropagationSpecification(),
                new Simulink2brakesystemChangePropagationSpecification());
    }

    @Test public void testCreateAndRegisterRootSimulinkModel(@TempDir Path tempDir) throws
            Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.modifyView(util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeDerivingTrait(), (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");
            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                (View v) -> {
                    Brakesystem
                            brakesystem =
                            v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
                    return brakesystem.getInstanceName().equals("TestSimulinkModel");
                }));
    }

    @Test public void testSimulinkBlockToBrakesystemComponent(@TempDir Path tempDir) throws
            Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.userInteraction.addNextSingleSelection(1); // Brake Hose
        util.modifyView(util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeDerivingTrait(), (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");

            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName("TestBlock");

            simulinkModel.getContains().add(block);

            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                (View v) -> {
                    Brakesystem
                            brakesystem =
                            v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
                    BrakeHose hose = (BrakeHose) brakesystem.getBrakeComponents().getFirst();

                    return brakesystem.getBrakeComponents().size() == 1 &&
                            hose.getId().equals("TestBlock");
                }));
    }

    @Test public void testSimulinkSubSystemToBrakesystemCaliperWithPad(@TempDir Path tempDir) throws
            Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.userInteraction.addNextSingleSelection(3); // Brake Caliper
        util.modifyView(util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeDerivingTrait(), (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");

            SubSystem subSystem = SimuLinkFactory.eINSTANCE.createSubSystem();
            subSystem.setName("TestSubSystem");
            simulinkModel.getContains().add(subSystem);

            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName("TestBlock");
            subSystem.getSubBlocks().add(block);

            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                (View v) -> {
                    Brakesystem
                            brakesystem =
                            v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
                    BrakeCaliper
                            caliper =
                            (BrakeCaliper) brakesystem.getBrakeComponents().getFirst();
                    BrakePad pad = caliper.getBrakePads().getFirst();

                    return brakesystem.getBrakeComponents().size() == 1 &&
                            caliper.getId().equals("TestSubSystem") &&
                            pad.getId().equals("TestBlock");
                }));
    }

    @Test public void testSimulinkBlockDelete(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.userInteraction.addNextSingleSelection(1); // Brake Hose
        util.modifyView(util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeDerivingTrait(), (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");

            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName("TestBlock");

            simulinkModel.getContains().add(block);

            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                (View v) -> {
                    Brakesystem
                            brakesystem =
                            v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
                    BrakeHose hose = (BrakeHose) brakesystem.getBrakeComponents().getFirst();

                    return brakesystem.getBrakeComponents().size() == 1 &&
                            hose.getId().equals("TestBlock");
                }));

        util.modifyView(util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeDerivingTrait(), (CommittableView v) -> {
            SimulinkModel
                    simulinkModel =
                    v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
            simulinkModel.getContains().removeFirst();
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                (View v) -> {
                    Brakesystem
                            brakesystem =
                            v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();

                    return brakesystem.getBrakeComponents().isEmpty();
                }));
    }

    @Test public void testSimulinkBlockRename(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.userInteraction.addNextSingleSelection(1); // Brake Hose
        util.modifyView(util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeDerivingTrait(), (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");

            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName("TestBlock");

            simulinkModel.getContains().add(block);

            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                (View v) -> {
                    Brakesystem
                            brakesystem =
                            v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
                    BrakeHose hose = (BrakeHose) brakesystem.getBrakeComponents().getFirst();

                    return brakesystem.getBrakeComponents().size() == 1 &&
                            hose.getId().equals("TestBlock");
                }));

        util.modifyView(util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeDerivingTrait(), (CommittableView v) -> {
            SimulinkModel
                    simulinkModel =
                    v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
            simulinkModel.getContains().getFirst().setName("UpdatedBlockName");
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                (View v) -> {
                    Brakesystem
                            brakesystem =
                            v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
                    BrakeHose hose = (BrakeHose) brakesystem.getBrakeComponents().getFirst();

                    return brakesystem.getBrakeComponents().size() == 1 &&
                            hose.getId().equals("UpdatedBlockName");
                }));
    }

    @Test public void testSimulinkBlockWithParameter(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.userInteraction.addNextSingleSelection(1); // Brake Hose
        util.modifyView(util.getDefaultView(vsum, List.of(SimulinkModel.class))
                .withChangeDerivingTrait(), (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");

            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName("TestBlock");

            block.getParameters().add(SimuLinkFactory.eINSTANCE.createParameter());
            block.getParameters().getLast().setName("Thread 1");
            block.getParameters().getLast().setType("string");
            block.getParameters().getLast().setValue("TestValue");

            block.getParameters().add(SimuLinkFactory.eINSTANCE.createParameter());
            block.getParameters().getLast().setName("Length");
            block.getParameters().getLast().setType("int32");
            block.getParameters().getLast().setValue("100");

            simulinkModel.getContains().add(block);

            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                (View v) -> {
                    Brakesystem
                            brakesystem =
                            v.getRootObjects(Brakesystem.class).stream().findFirst().orElseThrow();
                    BrakeHose hose = (BrakeHose) brakesystem.getBrakeComponents().getFirst();

                    return brakesystem.getBrakeComponents().size() == 1 &&
                            hose.getId().equals("TestBlock") &&
                            hose.getThreadSize1().equals("TestValue") &&
                            hose.getLengthInMM() == 100;
                }));
    }
}
