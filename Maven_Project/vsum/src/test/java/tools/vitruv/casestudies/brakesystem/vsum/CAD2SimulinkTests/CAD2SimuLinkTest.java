package tools.vitruv.casestudies.brakesystem.vsum.CAD2SimulinkTests;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.eclipse.emf.common.util.URI;
import edu.kit.ipd.sdq.metamodels.cad.BooleanParameter;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.CadFactory;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import edu.kit.ipd.sdq.metamodels.cad.StringParameter;
import edu.kit.ipd.sdq.metamodels.cad.Parameter;
import mir.reactions.cad2simulink.Cad2simulinkChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;

import simulink.SimulinkModel;
import simulink.SubSystem;
import simulink.Block;



public class CAD2SimuLinkTest {

    TestUtil testUtil = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Cad2simulinkChangePropagationSpecification());

    @BeforeAll
    public static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
    }

    @Test
    public void testCreateAndRegisterRoot(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);
       

        // Create a CAD_Model in the CAD view
        CommittableView cadView = testUtil.getDefaultView(vsum,
				List.of(CAD_Model.class)).withChangeDerivingTrait();

        testUtil.modifyView(cadView,(CommittableView v) -> {
            CAD_Model cad_Model = CadFactory.eINSTANCE.createCAD_Model();
            cad_Model.setName("TestCADModel");
                  v.registerRoot(cad_Model,
                          URI.createFileURI(tempDir.resolve("cad.xmi").toString()));
        });


        Assertions.assertTrue(
				assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
						(View v) -> {
							SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
							return simulinkModel.getName().equals("TestCADModel");
						}));
    }


    @Test
    public void testCADNamespaceToSimuLinkBlock(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);
        testUtil.userInteraction.addNextSingleSelection(0);

        // Create a CAD_Model in the CAD view
        CommittableView cadView = testUtil.getDefaultView(vsum,List.of(CAD_Model.class)).withChangeDerivingTrait();
        createDefaultCADModel(cadView, tempDir);

        Assertions.assertTrue(
        assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
                (View v) -> {
                    SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
                    Block block = simulinkModel.getContains().stream()
                            .filter(b -> b.getName().equals("TestNamespace"))
                            .findFirst()
                            .orElseThrow();
                    return block.getName().equals("TestNamespace");
                }));       
    }



    @Test
    public void testCADNamespaceToSimuLinkSubsystem(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);
        testUtil.userInteraction.addNextSingleSelection(1);

        // Create a CAD_Model in the CAD view
        CommittableView cadView = testUtil.getDefaultView(vsum,List.of(CAD_Model.class)).withChangeDerivingTrait();
        createDefaultCADModel(cadView, tempDir);

        Assertions.assertTrue(
        assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
                (View v) -> {
                    SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
                    SubSystem subSystem = (SubSystem)simulinkModel.getContains().stream()
                            .filter(b -> b.getName().equals("TestNamespace"))
                            .findFirst()
                            .orElseThrow();
                    return subSystem.getName().equals("TestNamespace");
                }));
    }


    @Test
    public void testCADStringParameterToSimuLinkParameter(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);
        testUtil.userInteraction.addNextSingleSelection(0);

        // Create a CAD_Model in the CAD view
        CommittableView cadView = testUtil.getDefaultView(vsum,List.of(CAD_Model.class)).withChangeDerivingTrait();
        createDefaultCADModel(cadView, tempDir);

        Assertions.assertTrue(
        assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
                (View v) -> {
                    SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
                    Block block = simulinkModel.getContains().stream()
                            .filter(b -> b.getName().equals("TestNamespace"))
                            .findFirst()
                            .orElseThrow();
                    return block.getParameters().stream()
                            .anyMatch(p -> p.getName().equals("TestStringParameter") && p.getValue().equals("TestValue") && p.getType().equals("string"));
                }));
    }

     @Test
    public void testCADNumericParameterToSimuLinkParameter(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);
        //selects BLock
        testUtil.userInteraction.addNextSingleSelection(0);
        // selects Parameter type double
        testUtil.userInteraction.addNextSingleSelection(0);
        // Create a CAD_Model in the CAD view
        CommittableView cadView = testUtil.getDefaultView(vsum,List.of(CAD_Model.class)).withChangeDerivingTrait();
        testUtil.modifyView(cadView,(CommittableView v) -> {
            CAD_Model cad_Model = CadFactory.eINSTANCE.createCAD_Model();
            cad_Model.setName("TestCADModel");

            Namespace namespace = CadFactory.eINSTANCE.createNamespace();
            namespace.setName("TestNamespace");

            NumericParameter numericParameter = CadFactory.eINSTANCE.createNumericParameter();
            numericParameter.setName("TestNumericParameter");
            numericParameter.setValue(42);
            namespace.getParameters().add(numericParameter);

            cad_Model.getNamespaces().add(namespace);

            v.registerRoot(cad_Model,
                    URI.createFileURI(tempDir.resolve("cad.xmi").toString()));
        });

        Assertions.assertTrue(
        assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
                (View v) -> {
                    SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
                    Block block = simulinkModel.getContains().stream()
                            .filter(b -> b.getName().equals("TestNamespace"))
                            .findFirst()
                            .orElseThrow();
                    return block.getParameters().stream()
                            .anyMatch(p -> p.getName().equals("TestNumericParameter") && p.getValue().equals("42.0") && p.getType().equals("double"));
                }));
    }


    @Test
    public void testCADBooleanParameterToSimuLinkParameter(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);
        testUtil.userInteraction.addNextSingleSelection(0);

        // Create a CAD_Model in the CAD view
        CommittableView cadView = testUtil.getDefaultView(vsum,List.of(CAD_Model.class)).withChangeDerivingTrait();
        testUtil.modifyView(cadView,(CommittableView v) -> {
            CAD_Model cad_Model = CadFactory.eINSTANCE.createCAD_Model();
            cad_Model.setName("TestCADModel");

            Namespace namespace = CadFactory.eINSTANCE.createNamespace();
            namespace.setName("TestNamespace");

            BooleanParameter booleanParameter = CadFactory.eINSTANCE.createBooleanParameter();
            booleanParameter.setName("TestBooleanParameter");
            booleanParameter.setValue(true);
            namespace.getParameters().add(booleanParameter);

            cad_Model.getNamespaces().add(namespace);

            v.registerRoot(cad_Model,
                    URI.createFileURI(tempDir.resolve("cad.xmi").toString()));
        });

        Assertions.assertTrue(
        assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
                (View v) -> {
                    SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
                    Block block = simulinkModel.getContains().stream()
                            .filter(b -> b.getName().equals("TestNamespace"))
                            .findFirst()
                            .orElseThrow();
                    return block.getParameters().stream()
                            .anyMatch(p -> p.getName().equals("TestBooleanParameter") && p.getValue().equals("true") && p.getType().equals("boolean"));
                }));
              
    }



    @Test
    public void testParameterDeletion(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);

        testUtil.userInteraction.addNextSingleSelection(0);
        testUtil.userInteraction.addNextSingleSelection(0);

        // Create a CAD_Model in the CAD view
        CommittableView cadView = testUtil.getDefaultView(vsum,List.of(CAD_Model.class)).withChangeDerivingTrait();
        createDefaultCADModel(cadView, tempDir);
        

        CommittableView deletecadView = testUtil.getDefaultView(vsum,List.of(CAD_Model.class)).withChangeDerivingTrait();

        // Now delete the parameter
        testUtil.modifyView(deletecadView, (CommittableView v) -> {
            CAD_Model cadModel = v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
            Namespace namespace = cadModel.getNamespaces().stream()
                    .filter(n -> n.getName().equals("TestNamespace"))
                    .findFirst()
                    .orElseThrow();
            Parameter parameterToDelete = namespace.getParameters().stream()
                    .filter(p -> p.getName().equals("TestStringParameter"))
                    .findFirst()
                    .orElseThrow();
            EcoreUtil.delete(parameterToDelete);
            //namespace.getParameters().remove(parameterToDelete);
            
        });


        Assertions.assertTrue(
        assertView(testUtil.getDefaultView(vsum, List.of(CAD_Model.class)),
                (View v) -> {
                CAD_Model cad_Model = v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                Namespace namespace = cad_Model.getNamespaces().stream()
                        .filter(b -> b.getName().equals("TestNamespace"))
                        .findFirst()
                        .orElseThrow();
                return namespace.getParameters().stream()
                        .noneMatch(p -> { System.out.println(p.getName()); return p.getName().equals("TestStringParameter"); });
                }));

        // Assert that the corresponding Simulink Parameter is also deleted
        Assertions.assertTrue(
            assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
                    (View v) -> {
                        SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
                        Block block = simulinkModel.getContains().stream()
                                .filter(b -> b.getName().equals("TestNamespace"))
                                .findFirst()
                                .orElseThrow();
                        return block.getParameters().stream()
                                .noneMatch(p -> { System.out.println(p.getName()); return p.getName().equals("TestStringParameter"); });
                    }));
    }


    @Test
    public void testParameterModification(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);

        testUtil.userInteraction.addNextSingleSelection(0);
        testUtil.userInteraction.addNextSingleSelection(0);

        // Create a CAD_Model in the CAD view
        CommittableView cadView = testUtil.getDefaultView(vsum,List.of(CAD_Model.class)).withChangeDerivingTrait();
        createDefaultCADModel(cadView, tempDir);
        

        CommittableView updateCADView = testUtil.getDefaultView(vsum,List.of(CAD_Model.class)).withChangeDerivingTrait();

        // Now modify the parameter value
        testUtil.modifyView(updateCADView, (CommittableView v) -> {
            CAD_Model cadModel = v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
            Namespace namespace = cadModel.getNamespaces().stream()
                    .filter(n -> n.getName().equals("TestNamespace"))
                    .findFirst()
                    .orElseThrow();
            StringParameter parameterToModify = (StringParameter) namespace.getParameters().stream()
                    .filter(p -> p.getName().equals("TestStringParameter"))
                    .findFirst()
                    .orElseThrow();
            //parameterToModify.setName("ModifiedName");
            parameterToModify.setValue("ModifiedValue");
        });

         Assertions.assertTrue(
                assertView(testUtil.getDefaultView(vsum, List.of(CAD_Model.class)),
                        (View v) -> {
                        CAD_Model cad_Model = v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
                        Namespace namespace = cad_Model.getNamespaces().stream()
                                .filter(b -> b.getName().equals("TestNamespace"))
                                .findFirst()
                                .orElseThrow();
                        return namespace.getParameters().stream()
                                .anyMatch(p -> { System.out.println(p.getName()); return p.getName().equals("TestStringParameter") && ((StringParameter)p).getValue().equals("ModifiedValue"); });
                        }));

        // Assert that the corresponding Simulink Parameter is also modified
        Assertions.assertTrue(
            assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
                    (View v) -> {
                        SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
                        Block block = simulinkModel.getContains().stream()
                                .filter(b -> b.getName().equals("TestNamespace"))
                                .findFirst()
                                .orElseThrow();
                        return block.getParameters().stream()
                                .anyMatch(p -> { System.out.println(p.getValue()); return p.getName().equals("TestStringParameter") && p.getValue().equals("ModifiedValue"); });
                    }));
    }


    /*
        Creates a default CAD model with a Namespace and a StringParameter and registers it as root object in the given view.
    */
    private void createDefaultCADModel(CommittableView view, Path filePath) {
       
         testUtil.modifyView(view,(CommittableView v) -> {
            CAD_Model cad_Model = CadFactory.eINSTANCE.createCAD_Model();
            cad_Model.setName("TestCADModel");

            Namespace namespace = CadFactory.eINSTANCE.createNamespace();
            namespace.setName("TestNamespace");

            StringParameter stringParameter = CadFactory.eINSTANCE.createStringParameter();
            stringParameter.setName("TestStringParameter");
            stringParameter.setValue("TestValue");
            namespace.getParameters().add(stringParameter);

            cad_Model.getNamespaces().add(namespace);

            v.registerRoot(cad_Model,
                    URI.createFileURI(filePath.resolve("cad.xmi").toString()));
        });


    }

    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
		return viewAssertionFunction.apply(view);
	}
}