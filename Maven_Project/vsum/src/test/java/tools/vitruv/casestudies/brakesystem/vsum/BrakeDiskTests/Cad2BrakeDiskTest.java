package tools.vitruv.casestudies.brakesystem.vsum.BrakeDiskTests;

import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.function.Function;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import brakesystem.ABSSensor;
import brakesystem.BrakeComponent;
import brakesystem.BrakeDisk;
import brakesystem.BrakesystemPackage;
import edu.kit.ipd.sdq.metamodels.cad.BooleanParameter;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.CadFactory;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import edu.kit.ipd.sdq.metamodels.cad.StringParameter;
import edu.kit.ipd.sdq.metamodels.cad.Unit;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.casestudies.brakesystem.vsum.DefaultModelElements;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

public class Cad2BrakeDiskTest {

    TestUtil util = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Cad2brakesystemChangePropagationSpecification(),new Brakesystem2cadChangePropagationSpecification());

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
                new XMIResourceFactoryImpl());

    }

    @Test
    void brakeDiskInsertionAndPropagationTest(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
        util.registerRootObjects(vsum, tempDir);
        // Setting the user interaction to 0, thus an ABSSensor should be created
        util.userInteraction.addNextSingleSelection(0);
        // add brake disk with parameters
        CommittableView view = util.getCADView(vsum)
                .withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Namespace namespace = DefaultModelElements.createDefaultNamespace();
            namespace.setId("brakeDisk1");
            util.getRootOfCADView(v).getNamespaces().add(namespace);
        });

        Assertions.assertTrue(
                assertView(util.getBrakesystemView(vsum),
                        (View v) -> !util.getRootOfBrakesystemView(v)
                                .getBrakeComponents().isEmpty() &&
                                util.getRootOfBrakesystemView(v)
                                        .getBrakeComponents().get(0) instanceof ABSSensor));
    }

    @Test
    void nochoice(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
        util.registerRootObjects(vsum, tempDir);
        // add brake disk with parameters
        CommittableView view = util.getCADView(vsum)
                .withChangeRecordingTrait();
        assertThrows(AssertionError.class, () -> {
            util.modifyView(view, (CommittableView v) -> {
                Namespace namespace = DefaultModelElements.createDefaultNamespace();
                namespace.setId("brakeDisk1");
                util.getRootOfCADView(v).getNamespaces().add(namespace);
            });
        });
    }

    @Test
    void parameterInsertionAndPropagationTestForABSSensor(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
        util.registerRootObjects(vsum, tempDir);
        // Setting the user interaction to 0, thus an ABSSensor should be created
        util.userInteraction.addNextSingleSelection(0);
        // add brake disk with parameters
        CommittableView view = util.getCADView(vsum)
                .withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Namespace namespace = DefaultModelElements.createDefaultNamespace();
            namespace.setId("brakeDisk1");
            util.getRootOfCADView(v).getNamespaces().add(namespace);
            namespace.getParameters().add(CadFactory.eINSTANCE.createStringParameter());

            StringParameter parameter = CadFactory.eINSTANCE.createStringParameter();
            parameter.setName("Specification Type");
            parameter.setValue("some example specification");
            namespace.getParameters().add(parameter);

            NumericParameter numericParameter = CadFactory.eINSTANCE.createNumericParameter();
            numericParameter.setName("Length");
            numericParameter.setValue(100);
            namespace.getParameters().add(numericParameter);
        });

        // Assert that the ABSSensor was created
        Assertions.assertTrue(
                assertView(util.getBrakesystemView(vsum),
                        (View v) -> !util.getRootOfBrakesystemView(v)
                                .getBrakeComponents().isEmpty() &&
                                util.getRootOfBrakesystemView(v)
                                        .getBrakeComponents().get(0) instanceof ABSSensor));

        // Assert that the parameters were propagated correctly
        Assertions.assertTrue(
                assertView(util.getBrakesystemView(vsum),
                        (View v) -> {
                            ABSSensor sensor = (ABSSensor) util.getRootOfBrakesystemView(v).getBrakeComponents().get(0);
                            return sensor.getSpecificationType().equals("some example specification")
                                    && sensor.getLengthInMM() == 100;
                        }));

    }

    @Test
    void parameterInsertionAndPropagationForBrakeDiskTest(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
        util.registerRootObjects(vsum, tempDir);
        // Setting the user interaction to 0, thus an ABSSensor should be created
        util.userInteraction.addNextSingleSelection(3);
        // add brake disk with parameters
        CommittableView view = util.getCADView(vsum)
                .withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Namespace namespace = DefaultModelElements.createDefaultNamespace();
            namespace.setId("brakeDisk1");
            util.getRootOfCADView(v).getNamespaces().add(namespace);
            namespace.getParameters().add(CadFactory.eINSTANCE.createStringParameter());

            BooleanParameter parameter = CadFactory.eINSTANCE.createBooleanParameter();
            parameter.setName("Ventilated");
            parameter.setValue(true);
            namespace.getParameters().add(parameter);

            NumericParameter numericParameter = CadFactory.eINSTANCE.createNumericParameter();
            numericParameter.setName("Brake Disk Thickness");
            numericParameter.setValue(30);
            numericParameter.setUnit(Unit.MM);
            namespace.getParameters().add(numericParameter);
        });

        // Assert that the ABSSensor was created
        Assertions.assertTrue(
                assertView(util.getBrakesystemView(vsum),
                        (View v) -> !util.getRootOfBrakesystemView(v)
                                .getBrakeComponents().isEmpty() &&
                                util.getRootOfBrakesystemView(v)
                                        .getBrakeComponents().get(0) instanceof BrakeDisk));

        // Assert that the parameters were propagated correctly
        Assertions.assertTrue(
                assertView(util.getBrakesystemView(vsum),
                        (View v) -> {
                            BrakeDisk disk = (BrakeDisk) util.getRootOfBrakesystemView(v)
                                .getBrakeComponents().get(0);
                            return disk.isVentilated() && disk.getBrakeDiskThicknessInMM() == 30;
                        }));

    }

    // Edit same parameter/attribute in both models and check if the change is
    // propagated
    // back and forth correctly
    @Test
    void biDirectionalPropagationTest(@TempDir Path tempDir) {
        // Starting from the CAD model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
        util.registerRootObjects(vsum, tempDir);
        // Setting the user interaction to 0, thus an ABSSensor should be created
        util.userInteraction.addNextSingleSelection(0);
        // add brake disk with parameters
        CommittableView view = util.getCADView(vsum)
                .withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Namespace namespace = DefaultModelElements.createDefaultNamespace();
            namespace.setId("brakeDisk1");
            util.getRootOfCADView(v).getNamespaces().add(namespace);

            StringParameter parameter = CadFactory.eINSTANCE.createStringParameter();
            parameter.setName("Specification Type");
            parameter.setValue("some example specification");
            namespace.getParameters().add(parameter);
        });

        // Assert that the ABSSensor was created
        Assertions.assertTrue(
                assertView(util.getBrakesystemView(vsum),
                        (View v) -> !util.getRootOfBrakesystemView(v)
                                .getBrakeComponents().isEmpty() &&
                                util.getRootOfBrakesystemView(v)
                                        .getBrakeComponents().get(0) instanceof ABSSensor));

        // Assert that the parameters were propagated correctly
        Assertions.assertTrue(
                assertView(util.getBrakesystemView(vsum),
                        (View v) -> {
                            ABSSensor sensor = (ABSSensor) util.getRootOfBrakesystemView(v)
                                .getBrakeComponents().get(0);
                            return sensor.getSpecificationType().equals("some example specification");
                        }));

        CommittableView brakeView = util.getBrakesystemView(vsum)
                .withChangeRecordingTrait();

        util.modifyView(brakeView, (CommittableView v) -> {
            ABSSensor sensor = (ABSSensor) util.getRootOfBrakesystemView(v)
                .getBrakeComponents().get(0);
            sensor.setSpecificationType("new specification");
        });

        // Assert that the change was propagated back to the CAD model
        Assertions.assertTrue(
                assertView(util.getCADView(vsum),
                        (View v) -> {
                            Namespace namespace = util.getRootOfCADView(v)
                                    .getNamespaces().stream()
                                    .filter(ns -> ns.getId().equals("brakeDisk1"))
                                    .findFirst().orElse(null);
                            if (namespace == null) {
                                return false;
                            } else {
                                return TestUtil.expectStringParameter(namespace, "Specification Type", "new specification");
                            }
                        }));

    }

    /**
     * 
     * @param component
     * @param tempDir
     */
    @ParameterizedTest
    @MethodSource("tools.vitruv.casestudies.brakesystem.vsum.BrakeDiskTests.BrakeDisk2CadTest#provideBrakeComponents")
    void propagateChangesToIdOfNamespaces(BrakeComponent component, @TempDir Path tempDir) {
        var vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        // Add brake component
        CommittableView brakeView = util.getBrakesystemView(vsum)
            .withChangeRecordingTrait();
        util.modifyView(brakeView, view -> {
            var brakesystem = util.getRootOfBrakesystemView(brakeView);
            brakesystem.getBrakeComponents().add(component);
        });

        // Assert namespace exists
        CommittableView cadView = util.getCADView(vsum)
            .withChangeRecordingTrait();


        // Update namespace id
        var newId = "423rdc423RDT734ctr092x3rm0";
        util.modifyView(cadView, view -> {
            CAD_Model cad_Model = util.getRootOfCADView(cadView);
            Namespace namespace = util.findNamespaceWithId(cad_Model, component.getId());
            namespace.setId(newId);
        });

        // Assert brake component has changed its id.
        brakeView.update();
        assertTrue(assertView(brakeView, view -> {
            var system = util.getRootOfBrakesystemView(brakeView);
            var component2 = util.findBrakeComponentWithId(system, newId);

            // Assume component2 and component are equivalent
            // in their features, except their id.
            var features = component.eClass().getEStructuralFeatures();
            return features.stream()
                .allMatch(feature -> 
                    feature.equals(BrakesystemPackage.eINSTANCE.getBrakeComponent_Id()) ||
                    component.eGet(feature).equals(component2.eGet(feature)));
        }));
    }

    /**
     * When creating a brake component, and deleting its corresponding namespace,
     * the created brake component must also be deleted.
     * 
     * @param component - {@link BrakeComponent}
     * @param tempDir - {@link Path}
     */
    @ParameterizedTest
    @MethodSource("tools.vitruv.casestudies.brakesystem.vsum.BrakeDiskTests.BrakeDisk2CadTest#provideBrakeComponents")
    void deletionOfNamespaceDeletesCorrespondingBrakeComponent(BrakeComponent component, @TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        // Add brake component
        CommittableView brakeView = util.getBrakesystemView(vsum)
            .withChangeRecordingTrait();
        util.modifyView(brakeView, view -> {
            var brakesystem = util.getRootOfBrakesystemView(brakeView);
            brakesystem.getBrakeComponents().add(component);
        });

        // Assert namespace exists, retrieve it.
        CommittableView cadView = util.getCADView(vsum)
            .withChangeRecordingTrait();
        var cadModel = util.getRootOfCADView(cadView);
        var namespaceForComponent = util.findNamespaceWithId(cadModel, component.getId());

        // Remove namespace from the CAD model.
        util.modifyView(cadView, view -> {
            cadModel.getNamespaces().remove(namespaceForComponent);
        });

        // Assert no brake component exists.
        brakeView.update();
        assertThrows(NoSuchElementException.class, () ->  {
            var brakesystem = util.getRootOfBrakesystemView(brakeView);
            var existingComponent = util.findBrakeComponentWithId(brakesystem, component.getId());
            System.out.println("Warning: Component " + existingComponent.toString() + "still exits!");
        });
    }

    // CAD Model creates a ABSSensor
    // Attribute changes to the ABSSensor are propagated to the CAD model
    @Test
    void absSensorAttributePropagationTest(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
        util.registerRootObjects(vsum, tempDir);
        // Setting the user interaction to 0, thus an ABSSensor should be created
        util.userInteraction.addNextSingleSelection(0);
        // add brake disk with parameters
        CommittableView view = util.getCADView(vsum)
                .withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Namespace namespace = DefaultModelElements.createDefaultNamespace();
            namespace.setId("brakeDisk1");
            util.getRootOfCADView(v).getNamespaces().add(namespace);
        });

        Assertions.assertTrue(
                assertView(util.getBrakesystemView(vsum),
                        (View v) -> !util.getRootOfBrakesystemView(v)
                                .getBrakeComponents().isEmpty() &&
                                util.getRootOfBrakesystemView(v)
                                        .getBrakeComponents().get(0) instanceof ABSSensor));

        CommittableView brakeView = util.getBrakesystemView(vsum)
                .withChangeRecordingTrait();

        util.modifyView(brakeView, (CommittableView v) -> {
            ABSSensor sensor = (ABSSensor) util.getRootOfBrakesystemView(brakeView).getBrakeComponents().get(0);
            sensor.setFittingDepth(50);
            sensor.setNumberOfPins(4);
        });

        // Assert that the changes were propagated back to the CAD model
        Assertions.assertTrue(
                assertView(util.getCADView(vsum),
                        (View v) -> {
                            Namespace namespace = util.getRootOfCADView(v)
                                    .getNamespaces().stream()
                                    .filter(ns -> ns.getId().equals("brakeDisk1"))
                                    .findFirst().orElse(null);
                            if (namespace == null) {
                                return false;
                            } else {
                                System.out.println("Params: " + namespace.getParameters());
                                var fittingDepthParamCorrect = TestUtil
                                        .expectNumericParameter(namespace, "Fitting Depth", 50);
                                var numberOfPinsParamCorrect = TestUtil
                                        .expectNumericParameter(namespace, "Number of Pins", 4);                                        
                                return fittingDepthParamCorrect && numberOfPinsParamCorrect;
                            }
                        }));
    }

    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
        return viewAssertionFunction.apply(view);
    }

}
