package tools.vitruv.methodologisttemplate.vsum.Simulink2Autosar;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import autosar.AUTOSAR;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.StringParameter;
import mir.reactions.cad2simulink.Cad2simulinkChangePropagationSpecification;
import mir.reactions.simulink2autosar.Simulink2autosarChangePropagationSpecification;
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

public class  Simulink2AutosarTest {


      TestUtil testUtil = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Simulink2autosarChangePropagationSpecification());

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
				assertView(testUtil.getDefaultView(vsum, List.of(AUTOSAR.class)),
						(View v) -> {
							AUTOSAR autosarModel = v.getRootObjects(AUTOSAR.class).stream().findFirst().orElseThrow();
							return autosarModel != null;
						}));
    }


    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
      return viewAssertionFunction.apply(view);
	  }
    
}
