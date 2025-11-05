package tools.vitruv.methodologisttemplate.vsum.BrakeDiskTests;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import brakesystem.ABSSensor;
import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import edu.kit.ipd.sdq.metamodels.cad.BooleanParameter;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.CadFactory;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import edu.kit.ipd.sdq.metamodels.cad.StringParameter;
import edu.kit.ipd.sdq.metamodels.cad.Unit;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.methodologisttemplate.vsum.TestUtil;

public class Cad2BrakeDiskTest {

    TestUtil util = new TestUtil();

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
                new XMIResourceFactoryImpl());

    }

    @Test
    void brakeDiskInsertionAndPropagationTest(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        // Setting the user interaction to 0, thus an ABSSensor should be created
        util.userInteraction.addNextSingleSelection(0);
        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum,
                List.of(CAD_Model.class))
                .withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Namespace namespace = createDefaultNamespace();
            namespace.setId("brakeDisk1");
            v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().add(namespace);
        });

        Assertions.assertTrue(
                assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                        (View v) -> !v.getRootObjects(Brakesystem.class).iterator().next()
                                .getBrakeComponents().isEmpty() &&
                                v.getRootObjects(Brakesystem.class).iterator().next()
                                        .getBrakeComponents().get(0) instanceof ABSSensor));
    }

    @Test
    void nochoice(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum,
                List.of(CAD_Model.class))
                .withChangeRecordingTrait();
        assertThrows(AssertionError.class, () -> {
            util.modifyView(view, (CommittableView v) -> {
                Namespace namespace = createDefaultNamespace();
                namespace.setId("brakeDisk1");
                v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().add(namespace);
            });
        });
    }

    @Test
    void parameterInsertionAndPropagationTestForABSSensor(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        // Setting the user interaction to 0, thus an ABSSensor should be created
        util.userInteraction.addNextSingleSelection(0);
        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum,
                List.of(CAD_Model.class))
                .withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Namespace namespace = createDefaultNamespace();
            namespace.setId("brakeDisk1");
            v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().add(namespace);
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
                assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                        (View v) -> !v.getRootObjects(Brakesystem.class).iterator().next()
                                .getBrakeComponents().isEmpty() &&
                                v.getRootObjects(Brakesystem.class).iterator().next()
                                        .getBrakeComponents().get(0) instanceof ABSSensor));

        // Assert that the parameters were propagated correctly
        Assertions.assertTrue(
                assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                        (View v) -> {
                            ABSSensor sensor = (ABSSensor) v.getRootObjects(Brakesystem.class)
                                    .iterator().next().getBrakeComponents().get(0);
                            return sensor.getSpecificationType().equals("some example specification")
                                    && sensor.getLengthInMM() == 100;
                        }));

    }

    @Test
    void parameterInsertionAndPropagationForBrakeDiskTest(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        // Setting the user interaction to 0, thus an ABSSensor should be created
        util.userInteraction.addNextSingleSelection(3);
        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum,
                List.of(CAD_Model.class))
                .withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Namespace namespace = createDefaultNamespace();
            namespace.setId("brakeDisk1");
            v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().add(namespace);
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
                assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                        (View v) -> !v.getRootObjects(Brakesystem.class).iterator().next()
                                .getBrakeComponents().isEmpty() &&
                                v.getRootObjects(Brakesystem.class).iterator().next()
                                        .getBrakeComponents().get(0) instanceof BrakeDisk));

        // Assert that the parameters were propagated correctly
        Assertions.assertTrue(
                assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                        (View v) -> {
                            BrakeDisk disk = (BrakeDisk) v.getRootObjects(Brakesystem.class)
                                    .iterator().next().getBrakeComponents().get(0);
                            return disk.isVentilated() && disk.getBrakeDiskThicknessInMM() == 30;
                        }));

    }

    // Edit same parameter/attribute in both models and check if the change is
    // propagated
    // back and forth correctly
    @Test
    void biDirectionalPropagationTest(@TempDir Path tempDir) {
        // Starting from the CAD model
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        // Setting the user interaction to 0, thus an ABSSensor should be created
        util.userInteraction.addNextSingleSelection(0);
        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum,
                List.of(CAD_Model.class))
                .withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Namespace namespace = createDefaultNamespace();
            namespace.setId("brakeDisk1");
            v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().add(namespace);

            StringParameter parameter = CadFactory.eINSTANCE.createStringParameter();
            parameter.setName("Specification Type");
            parameter.setValue("some example specification");
            namespace.getParameters().add(parameter);
        });

        // Assert that the ABSSensor was created
        Assertions.assertTrue(
                assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                        (View v) -> !v.getRootObjects(Brakesystem.class).iterator().next()
                                .getBrakeComponents().isEmpty() &&
                                v.getRootObjects(Brakesystem.class).iterator().next()
                                        .getBrakeComponents().get(0) instanceof ABSSensor));

        // Assert that the parameters were propagated correctly
        Assertions.assertTrue(
                assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                        (View v) -> {
                            ABSSensor sensor = (ABSSensor) v.getRootObjects(Brakesystem.class)
                                    .iterator().next().getBrakeComponents().get(0);
                            return sensor.getSpecificationType().equals("some example specification");
                        }));

        CommittableView brakeView = util.getDefaultView(vsum,
                List.of(Brakesystem.class))
                .withChangeRecordingTrait();

        util.modifyView(brakeView, (CommittableView v) -> {
            ABSSensor sensor = (ABSSensor) v.getRootObjects(Brakesystem.class)
                    .iterator().next().getBrakeComponents().get(0);
            sensor.setSpecificationType("new specification");
        });

        // Assert that the change was propagated back to the CAD model
        Assertions.assertTrue(
                assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                        (View v) -> {
                            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next()
                                    .getNamespaces().stream()
                                    .filter(ns -> ns.getId().equals("brakeDisk1"))
                                    .findFirst().orElse(null);
                            if (namespace == null) {
                                return false;
                            } else {
                                StringParameter param = namespace.getParameters().stream()
                                        .filter(p -> p instanceof StringParameter)
                                        .map(p -> (StringParameter) p)
                                        .filter(p -> p.getName().equals("Specification Type"))
                                        .findFirst().orElse(null);
                                return param != null && param.getValue().equals("new specification");
                            }
                        }));

    }

    // CAD Model creates a ABSSensor
    // Attribute changes to the ABSSensor are propagated to the CAD model
    @Test
    void absSensorAttributePropagationTest(@TempDir Path tempDir) {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
        util.registerRootObjects(vsum, tempDir);
        // Setting the user interaction to 0, thus an ABSSensor should be created
        util.userInteraction.addNextSingleSelection(0);
        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum,
                List.of(CAD_Model.class))
                .withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Namespace namespace = createDefaultNamespace();
            namespace.setId("brakeDisk1");
            v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().add(namespace);
        });

        Assertions.assertTrue(
                assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)),
                        (View v) -> !v.getRootObjects(Brakesystem.class).iterator().next()
                                .getBrakeComponents().isEmpty() &&
                                v.getRootObjects(Brakesystem.class).iterator().next()
                                        .getBrakeComponents().get(0) instanceof ABSSensor));

        CommittableView brakeView = util.getDefaultView(vsum,
                List.of(Brakesystem.class))
                .withChangeRecordingTrait();

        util.modifyView(brakeView, (CommittableView v) -> {
            ABSSensor sensor = (ABSSensor) v.getRootObjects(Brakesystem.class)
                    .iterator().next().getBrakeComponents().get(0);
            sensor.setFittingDepth(50);
            sensor.setNumberOfPins(4);
        });

        // Assert that the changes were propagated back to the CAD model
        Assertions.assertTrue(
                assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
                        (View v) -> {
                            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next()
                                    .getNamespaces().stream()
                                    .filter(ns -> ns.getId().equals("brakeDisk1"))
                                    .findFirst().orElse(null);
                            if (namespace == null) {
                                return false;
                            } else {
                                System.out.println("Params: " + namespace.getParameters());
                                NumericParameter fittingDepthParam = namespace.getParameters()
                                        .stream()
                                        .filter(p -> p instanceof NumericParameter)
                                        .map(p -> (NumericParameter) p)
                                        .filter(p -> p.getName().equals("Fitting Depth"))
                                        .findFirst().orElse(null);
                                NumericParameter numberOfPinsParam = namespace.getParameters()
                                        .stream()
                                        .filter(p -> p instanceof NumericParameter)
                                        .map(p -> (NumericParameter) p)
                                        .filter(p -> p.getName().equals("Number of Pins"))
                                        .findFirst().orElse(null);
                                return fittingDepthParam != null &&
                                        fittingDepthParam.getValue() == 50 &&
                                        numberOfPinsParam != null &&
                                        numberOfPinsParam.getValue() == 4;
                            }
                        }));
    }

    private Namespace createDefaultNamespace() {
        Namespace namespace = CadFactory.eINSTANCE.createNamespace();
        namespace.setName("myNamespace");
        namespace.setId("myID");
        return namespace;
    }

    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
        return viewAssertionFunction.apply(view);
    }

}
