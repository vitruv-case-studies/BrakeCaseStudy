package tools.vitruv.casestudies.brakesystem.vsum.Simulink2Autosar;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import autosar.AUTOSAR;
import mir.reactions.simulink2autosar.Simulink2autosarChangePropagationSpecification;
import simulink.SimuLinkFactory;
import simulink.SimulinkModel;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

public class Simulink2AutosarTest {


    TestUtil testUtil = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Simulink2autosarChangePropagationSpecification());

    @BeforeAll
    public static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
    }


    @Test
    public void testCreateAndRegisterRootSimulinkModel(@TempDir Path tempDir) throws Exception {

        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir, necessaryCPS);


        // Create a Simulink_Model in the Simulink view
        CommittableView simulinkView = testUtil.getDefaultView(vsum,
                List.of(SimulinkModel.class)).withChangeDerivingTrait();

        testUtil.modifyView(simulinkView, (CommittableView v) -> {
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
