package tools.vitruv.casestudies.brakesystem.vsum.CAD2SimulinkTests;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.StringParameter;
import mir.reactions.cad2simulink.Cad2simulinkChangePropagationSpecification;
import mir.reactions.simulink2cad.Simulink2cadChangePropagationSpecification;
import simulink.Block;
import simulink.SimuLinkFactory;
import simulink.SimulinkModel;
import simulink.SubSystem;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

public class SimuLink2CADTest extends TestBase {
    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Cad2simulinkChangePropagationSpecification(),
                new Simulink2cadChangePropagationSpecification());
    }

    @Test public void testCreateAndRegisterRootSimulinkModel(@TempDir Path tempDir) throws
            Exception {

        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();

        util.modifyView(simulinkView, (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");
            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    return cadModel.getName().equals("TestSimulinkModel");
                }));
    }

    @Test public void testSimulinkBlockToCADNamespace(@TempDir Path tempDir) throws Exception {

        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        util.modifyView(simulinkView, (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");

            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName("TestBlock");

            simulinkModel.getContains().add(block);

            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    return cadModel.getNamespaces()
                            .stream()
                            .anyMatch(ns -> ns.getName().equals("TestBlock"));
                }));
    }

    @Test public void testSimulinkSubsystemToCADNamespace(@TempDir Path tempDir) throws Exception {

        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        createDefaultSimuLinkModel(simulinkView, tempDir);

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    return cadModel.getNamespaces()
                            .stream()
                            .anyMatch(ns -> ns.getName().equals("TestBlock"));
                }));
    }

    @Test public void testSimuLinkParameterToCADStringParameter(@TempDir Path tempDir) throws
            Exception {
        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        createDefaultSimuLinkModel(simulinkView, tempDir);

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    Namespace
                            namespace =
                            cadModel.getNamespaces()
                                    .stream()
                                    .filter(b -> b.getName().equals("TestBlock"))
                                    .findFirst()
                                    .orElseThrow();
                    return namespace.getParameters()
                            .stream()
                            .anyMatch(p -> p.getName().equals("TestParameter") &&
                                    p instanceof StringParameter sp &&
                                    sp.getValue().equals("TestValue"));
                }));

    }

    @Test
    public void testSimuLinkParameterwithoutTypeToCADStringParameter(@TempDir Path tempDir) throws
            Exception {
        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        util.userInteraction.addNextSingleSelection(0);
        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        createDefaultSimuLinkModel(simulinkView, tempDir);

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    Namespace
                            namespace =
                            cadModel.getNamespaces()
                                    .stream()
                                    .filter(b -> b.getName().equals("TestBlock"))
                                    .findFirst()
                                    .orElseThrow();
                    return namespace.getParameters()
                            .stream()
                            .anyMatch(p -> p.getName().equals("TestParameter") &&
                                    p instanceof StringParameter sp &&
                                    sp.getValue().equals("TestValue"));
                }));

    }

    @Test public void testSimulinkParameterDeletion(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        createDefaultSimuLinkModel(simulinkView, tempDir);

        CommittableView
                simulinkDeleteView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        util.modifyView(simulinkDeleteView, (CommittableView v) -> {
            SimulinkModel
                    simulinkModel =
                    v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
            SubSystem
                    block =
                    (SubSystem) simulinkModel.getContains()
                            .stream()
                            .filter(b -> b.getName().equals("TestBlock"))
                            .findFirst()
                            .orElseThrow();
            simulink.Parameter
                    parameter =
                    block.getParameters()
                            .stream()
                            .filter(p -> p.getName().equals("TestParameter"))
                            .findFirst()
                            .orElseThrow();
            block.getParameters().remove(parameter);
            EcoreUtil.delete(parameter);
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    Namespace
                            namespace =
                            cadModel.getNamespaces()
                                    .stream()
                                    .filter(b -> b.getName().equals("TestBlock"))
                                    .findFirst()
                                    .orElseThrow();
                    return namespace.getParameters()
                            .stream()
                            .noneMatch(p -> p.getName().equals("TestParameter"));
                }));

    }

    @Test public void SimulinkBlockDeletion(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        createDefaultSimuLinkModel(simulinkView, tempDir);

        CommittableView
                simulinkDeleteView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        util.modifyView(simulinkDeleteView, (CommittableView v) -> {
            SimulinkModel
                    simulinkModel =
                    v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
            SubSystem
                    block =
                    (SubSystem) simulinkModel.getContains()
                            .stream()
                            .filter(b -> b.getName().equals("TestBlock"))
                            .findFirst()
                            .orElseThrow();
            simulinkModel.getContains().remove(block);
            EcoreUtil.delete(block);
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    return cadModel.getNamespaces()
                            .stream()
                            .noneMatch(ns -> ns.getName().equals("TestBlock"));
                }));
    }

    @Test public void SimulinkSubsystemNameUpdateTest(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        createDefaultSimuLinkModel(simulinkView, tempDir);

        CommittableView
                simulinkUpdateView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        util.modifyView(simulinkUpdateView, (CommittableView v) -> {
            SimulinkModel
                    simulinkModel =
                    v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
            SubSystem
                    block =
                    (SubSystem) simulinkModel.getContains()
                            .stream()
                            .filter(b -> b.getName().equals("TestBlock"))
                            .findFirst()
                            .orElseThrow();
            block.setName("UpdatedBlockName");
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    return cadModel.getNamespaces()
                            .stream()
                            .anyMatch(ns -> ns.getName().equals("UpdatedBlockName"));
                }));
    }

    @Test public void SimulinkBlockNameUpdateTest(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        util.modifyView(simulinkView, (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");

            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName("TestBlock");

            simulinkModel.getContains().add(block);

            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        CommittableView
                simulinkUpdateView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        util.modifyView(simulinkUpdateView, (CommittableView v) -> {
            SimulinkModel
                    simulinkModel =
                    v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
            Block
                    block =
                    simulinkModel.getContains()
                            .stream()
                            .filter(b -> b.getName().equals("TestBlock"))
                            .findFirst()
                            .orElseThrow();
            block.setName("UpdatedBlockName");
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    return cadModel.getNamespaces()
                            .stream()
                            .anyMatch(ns -> ns.getName().equals("UpdatedBlockName"));
                }));
    }

    @Test public void SimulinkParameterNameUpdateTest(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        createDefaultSimuLinkModel(simulinkView, tempDir);

        CommittableView
                simulinkUpdateView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        util.modifyView(simulinkUpdateView, (CommittableView v) -> {
            SimulinkModel
                    simulinkModel =
                    v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
            SubSystem
                    block =
                    (SubSystem) simulinkModel.getContains()
                            .stream()
                            .filter(b -> b.getName().equals("TestBlock"))
                            .findFirst()
                            .orElseThrow();
            simulink.Parameter
                    parameter =
                    block.getParameters()
                            .stream()
                            .filter(p -> p.getName().equals("TestParameter"))
                            .findFirst()
                            .orElseThrow();
            parameter.setName("UpdatedParameterName");
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    Namespace
                            namespace =
                            cadModel.getNamespaces()
                                    .stream()
                                    .filter(b -> b.getName().equals("TestBlock"))
                                    .findFirst()
                                    .orElseThrow();
                    return namespace.getParameters()
                            .stream()
                            .anyMatch(p -> p.getName().equals("UpdatedParameterName"));
                }));
    }

    @Test public void updateSimulinkStringParameterValueTest(@TempDir Path tempDir) throws
            Exception {
        // Create a new virtual model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a Simulink_Model in the Simulink view
        CommittableView
                simulinkView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        createDefaultSimuLinkModel(simulinkView, tempDir);

        CommittableView
                simulinkUpdateView =
                util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        util.modifyView(simulinkUpdateView, (CommittableView v) -> {
            SimulinkModel
                    simulinkModel =
                    v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
            SubSystem
                    block =
                    (SubSystem) simulinkModel.getContains()
                            .stream()
                            .filter(b -> b.getName().equals("TestBlock"))
                            .findFirst()
                            .orElseThrow();
            simulink.Parameter
                    parameter =
                    block.getParameters()
                            .stream()
                            .filter(p -> p.getName().equals("TestParameter"))
                            .findFirst()
                            .orElseThrow();
            parameter.setValue("UpdatedParameterValue");
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                    CAD_Model
                            cadModel =
                            v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                    Namespace
                            namespace =
                            cadModel.getNamespaces()
                                    .stream()
                                    .filter(b -> b.getName().equals("TestBlock"))
                                    .findFirst()
                                    .orElseThrow();
                    return namespace.getParameters()
                            .stream()
                            .anyMatch(p -> p.getName().equals("TestParameter") &&
                                    p instanceof StringParameter sp &&
                                    sp.getValue().equals("UpdatedParameterValue"));
                }));
    }

    /*
        Creates a default Simulink model with a Subsystem and a Parameter and registers it as root object in the given view.
    */
    private void createDefaultSimuLinkModel(CommittableView view, Path filePath) {
        util.modifyView(view, (CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");

            SubSystem block = SimuLinkFactory.eINSTANCE.createSubSystem();
            block.setName("TestBlock");

            simulink.Parameter parameter = SimuLinkFactory.eINSTANCE.createParameter();
            parameter.setName("TestParameter");
            parameter.setValue("TestValue");
            parameter.setType("string");

            block.getParameters().add(parameter);

            simulinkModel.getContains().add(block);

            v.registerRoot(simulinkModel,
                    URI.createFileURI(filePath.resolve("simulink.xmi").toString()));
        });
    }
}
