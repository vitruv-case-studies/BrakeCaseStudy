package tools.vitruv.casestudies.brakesystem.vsum.Simulink2Autosar;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import autosar.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mir.reactions.autosar2simulink.Autosar2simulinkChangePropagationSpecification;
import simulink.Block;
import simulink.SimulinkModel;
import simulink.SubSystem;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

public class Autosar2SimulinkTest {
    TestUtil testUtil = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Autosar2simulinkChangePropagationSpecification());

    @BeforeAll
    public static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
    }

    @Test
    public void testCreateAndRegisterAutosarModel(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a AutoSAR in the AutoSAR view
        CommittableView autoSARView = testUtil.getDefaultView(vsum,
                List.of(AUTOSAR.class)).withChangeDerivingTrait();

        testUtil.modifyView(autoSARView, (CommittableView v) -> {
            AUTOSAR autosarModel = AutoSARFactory.eINSTANCE.createAUTOSAR();
            v.registerRoot(autosarModel,
                    URI.createFileURI(tempDir.resolve("autosar.xmi").toString()));
        });


        Assertions.assertTrue(
                assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
                        (View v) -> {
                            SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
                            return simulinkModel != null;
                        }));
    }

    @Test
    public void createAtomicSwComponentTest(@TempDir Path tempDir) throws Exception {
        // Create a new virtual model
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir, necessaryCPS);

        // Create a AutoSAR in the AutoSAR view
        CommittableView autoSARView = testUtil.getDefaultView(vsum,
                List.of(AUTOSAR.class)).withChangeDerivingTrait();
        testUtil.modifyView(autoSARView, (CommittableView v) -> {
            AUTOSAR autosarModel = AutoSARFactory.eINSTANCE.createAUTOSAR();
            v.registerRoot(autosarModel,
                    URI.createFileURI(tempDir.resolve("autosar.xmi").toString()));

            ARPackage arPackage = AutoSARFactory.eINSTANCE.createARPackage();
            arPackage.setShortName("RootPackage");
            autosarModel.getArpackage().add(arPackage);

            ApplicationSwComponentType atomicSWComponent =
                    AutoSARFactory.eINSTANCE.createApplicationSwComponentType();
            atomicSWComponent.setShortName("AtomicSwComponentType");

            arPackage.getElements().add(atomicSWComponent);
        });

        Assertions.assertTrue(
                assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
                        (View v) -> {
                            SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
                            return simulinkModel.getContains().stream().anyMatch(
                                    b -> b.getName() != null && b.getName().equals("AtomicSwComponentType"));
                        }));
    }

    @Test
    public void createCompositionSwComponentTest(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir, necessaryCPS);

        CommittableView autoSARView = testUtil.getDefaultView(vsum,
                List.of(AUTOSAR.class)).withChangeDerivingTrait();
        testUtil.modifyView(autoSARView, (CommittableView v) -> {
            AUTOSAR autosarModel = AutoSARFactory.eINSTANCE.createAUTOSAR();
            v.registerRoot(autosarModel,
                    URI.createFileURI(tempDir.resolve("autosar.xmi").toString()));

            ARPackage arPackage = AutoSARFactory.eINSTANCE.createARPackage();
            arPackage.setShortName("RootPackage");
            autosarModel.getArpackage().add(arPackage);

            ApplicationSwComponentType innerSwComponent =
                    AutoSARFactory.eINSTANCE.createApplicationSwComponentType();
            innerSwComponent.setShortName("InnerSwComponentType");

            arPackage.getElements().add(innerSwComponent);

            CompositionSwComponentType compositionSwComponent =
                    AutoSARFactory.eINSTANCE.createCompositionSwComponentType();
            compositionSwComponent.setShortName("CompositionSwComponentType");

            SwComponentPrototype prototype = AutoSARFactory.eINSTANCE.createSwComponentPrototype();
            prototype.setType(innerSwComponent);

            compositionSwComponent.getComponents().add(prototype);

            arPackage.getElements().add(compositionSwComponent);
        });

        Assertions.assertTrue(
                assertView(testUtil.getDefaultView(vsum, List.of(SimulinkModel.class)),
                        (View v) -> {
                            SimulinkModel simulinkModel = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
                            SubSystem subSystem = (SubSystem) simulinkModel.getContains().getFirst();
                            Block inner = subSystem.getSubBlocks().getFirst();
                            return simulinkModel.getContains().size() == 1 && subSystem.getSubBlocks().size() == 1
                                    && subSystem.getName().equals("CompositionSwComponentType")
                                    && inner.getName().equals("InnerSwComponentType");
                        }));
    }

    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
        return viewAssertionFunction.apply(view);
    }
}
