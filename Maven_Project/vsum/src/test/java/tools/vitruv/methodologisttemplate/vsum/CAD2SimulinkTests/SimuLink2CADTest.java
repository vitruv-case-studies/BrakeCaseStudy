package tools.vitruv.methodologisttemplate.vsum.CAD2SimulinkTests;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.CadFactory;
import mir.reactions.cad2simulink.Cad2simulinkChangePropagationSpecification;
import mir.reactions.simulink2cad.Simulink2cadChangePropagationSpecification;
import simulink.Block;
import simulink.SimuLinkFactory;
import simulink.SimulinkModel;
import simulink.SubSystem;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.methodologisttemplate.vsum.TestUtil;

public class SimuLink2CADTest {

      TestUtil testUtil = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Cad2simulinkChangePropagationSpecification(),new Simulink2cadChangePropagationSpecification());

    @BeforeAll
    public static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
    }


    @Test
    public void testCreateAndRegisterRootSimulinkModel(@TempDir Path tempDir) throws Exception {

         // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);
       

        // Create a Simulink_Model in the Simulink view
        CommittableView simulinkView = testUtil.getDefaultView(vsum,
				List.of(SimulinkModel.class)).withChangeDerivingTrait();

        testUtil.modifyView(simulinkView,(CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");
                  v.registerRoot(simulinkModel,
                          URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });


        Assertions.assertTrue(
				assertView(testUtil.getDefaultView(vsum, List.of(CAD_Model.class)),
						(View v) -> {
							CAD_Model cadModel = v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
							return cadModel.getName().equals("TestSimulinkModel");
						}));
    }



    @Test @Disabled
    public void testSimulinkBlockToCADNamespace(@TempDir Path tempDir) throws Exception {

         // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);
       

        // Create a Simulink_Model in the Simulink view
        CommittableView simulinkView = testUtil.getDefaultView(vsum,List.of(SimulinkModel.class)).withChangeDerivingTrait();
        testUtil.modifyView(simulinkView,(CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");

            Block block = SimuLinkFactory.eINSTANCE.createBlock();
            block.setName("TestBlock");
            
            simulinkModel.getContains().add(block);

        
            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(
        assertView(testUtil.getDefaultView(vsum, List.of(CAD_Model.class)),
            (View v) -> {
              CAD_Model cadModel = v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
              return cadModel.getNamespaces().stream()
                  .anyMatch(ns -> ns.getName().equals("TestBlock"));
            }));
    }

     @Test
    public void testSimulinkSubsystemToCADNamespace(@TempDir Path tempDir) throws Exception {

         // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir,necessaryCPS);
       

        // Create a Simulink_Model in the Simulink view
        CommittableView simulinkView = testUtil.getDefaultView(vsum,List.of(SimulinkModel.class)).withChangeDerivingTrait();
        testUtil.modifyView(simulinkView,(CommittableView v) -> {
            SimulinkModel simulinkModel = SimuLinkFactory.eINSTANCE.createSimulinkModel();
            simulinkModel.setName("TestSimulinkModel");

            SubSystem subSystem = SimuLinkFactory.eINSTANCE.createSubSystem();
            subSystem.setName("TestSubsystem");
            simulinkModel.getContains().add(subSystem);

        
            v.registerRoot(simulinkModel,
                    URI.createFileURI(tempDir.resolve("simulink.xmi").toString()));
        });

        Assertions.assertTrue(
        assertView(testUtil.getDefaultView(vsum, List.of(CAD_Model.class)),
            (View v) -> {
              CAD_Model cadModel = v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
              return cadModel.getNamespaces().stream()
                  .anyMatch(ns -> ns.getName().equals("TestSubsystem"));
            }));
    }


    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
      return viewAssertionFunction.apply(view);
	  }
    
}
