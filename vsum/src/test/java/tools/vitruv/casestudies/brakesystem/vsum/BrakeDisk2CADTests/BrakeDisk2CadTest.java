package tools.vitruv.casestudies.brakesystem.vsum.BrakeDisk2CADTests;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import brakesystem.*;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

public class BrakeDisk2CadTest {

    // TODO add logging framework
    // private static final Logger logger = org.slf4j.LoggerFactory
    // .getLogger(BrakeDisk2CadTest.class);

    TestUtil util = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Cad2brakesystemChangePropagationSpecification(), new Brakesystem2cadChangePropagationSpecification());

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    @Test
    void brakeDiskInsertionAndPropagationTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = createDefaultBrakeDisk();
            brakeDisk.setId("brakeDisk1");

            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
        });

        // assert that namespace with parameters as above has been created
        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            System.out.println(v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces());
            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst().orElseThrow();
            boolean isOEMNumber = TestUtil.expectStringParameter(namespace, "OEM Number", "VW123456");
            boolean isDiameterInMM = TestUtil.expectNumericParameter(namespace, "Diameter", 120.0f);
            boolean isCenteringDiameterInMM = TestUtil.expectNumericParameter(namespace, "Centering Diameter", 20.0f);
            boolean isRimHoleNumber = TestUtil.expectNumericParameter(namespace, "Rim Hole Number", 1.0f);
            boolean isHoleArrangementNumber = TestUtil.expectNumericParameter(namespace, "Hole Arrangement Number", 20.0f);
            boolean isBoltHoleCircleInMM = TestUtil.expectNumericParameter(namespace, "Bolt Hole Circle", 60.0f);
            boolean isBrakeDiskThicknessInMM = TestUtil.expectNumericParameter(namespace, "Brake Disk Thickness", 30);
            boolean isMinimumThicknessInMM = TestUtil.expectNumericParameter(namespace, "Minimum Thickness", 25);
            boolean isVentilated = TestUtil.expectBooleanParameter(namespace, "Ventilated", true);

            return isOEMNumber && isDiameterInMM & isCenteringDiameterInMM && isRimHoleNumber && isHoleArrangementNumber && isBoltHoleCircleInMM && isBrakeDiskThicknessInMM && isMinimumThicknessInMM && isVentilated;

        }));

    }

    @Test
    void brakePadInsertionAndPropagationTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        CommittableView view = util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            BrakeCaliper brakeCaliper = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
            brakeCaliper.setId("brakeCaliper1");
            brakeCaliper.setPistonDiameterInMM(50);

            BrakePad brakePad = BrakesystemFactory.eINSTANCE.createBrakePad();
            brakePad.setId("brakePad1");
            brakePad.setWearWarning(true);
            brakeCaliper.getBrakePads().add(brakePad);

            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeCaliper);
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            System.out.println(v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces());
            Namespace caliperNamespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakeCaliper1")).findFirst().orElseThrow();
            boolean isPistonDiameterInMM = TestUtil.expectNumericParameter(caliperNamespace, "Piston Diameter", 50.0f);

            Namespace padNamespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakePad1")).findFirst().orElseThrow();
            boolean isWearWarning = TestUtil.expectBooleanParameter(padNamespace, "Wear Warning", true);

            return isPistonDiameterInMM && isWearWarning;
        }));

    }

    @Test
    void changeDiameterTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
            brakeDisk.setId("brakeDisk1");
            brakeDisk.setDiameterInMM(120);
            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
        });

        // assert that namespace with parameters as above has been created
        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst().orElseThrow();
            return TestUtil.expectNumericParameter(namespace, "Diameter", 120);
        }));

        // change diameter
        util.modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().stream().filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast).findFirst().orElseThrow();
            brakeDisk.setDiameterInMM(130);
        });

        // assert that namespace with parameters as above has been created
        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst().orElseThrow();
            return TestUtil.expectNumericParameter(namespace, "Diameter", 130);
        }));
    }

    @Test
    void changeNumericParameterTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
            brakeDisk.setId("brakeDisk1");
            brakeDisk.setDiameterInMM(120);
            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
        });

        util.modifyView(util.getDefaultView(vsum, List.of(CAD_Model.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst().orElseThrow();
            NumericParameter parameter = (NumericParameter) namespace.getParameters().stream().filter(p -> p.getName().equals("Diameter")).findFirst().orElseThrow();
            parameter.setValue(130);
        });

        // assert that namespace with parameters as above has been created
        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(Brakesystem.class)), (View v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().stream().filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast).findFirst().orElseThrow();
            return brakeDisk.getDiameterInMM() == 130;
        }));
    }

    @Test
    void changeIdTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = createDefaultBrakeDisk();
            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
        });

        // assert that namespace with parameters as above has been created
        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst().orElseThrow();
            return namespace.getId().equals("brakeDisk1") && TestUtil.expectStringParameter(namespace, "OEM Number", "VW123456");
        }));

        // change id
        util.modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().stream().filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast).findFirst().orElseThrow();
            brakeDisk.setId("newId");
        });

        // assert that namespace with parameters as above has been created
        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("newId")).findFirst().orElseThrow();
            return TestUtil.expectStringParameter(namespace, "OEM Number", "VW123456");
        }));
    }

    @Test
    void changeVentilatedTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = createDefaultBrakeDisk();
            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
        });

        // assert that namespace with parameters as above has been created
        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst().orElseThrow();
            return TestUtil.expectBooleanParameter(namespace, "Ventilated", true);
        }));

        // change ventilated
        util.modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().stream().filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast).findFirst().orElseThrow();
            brakeDisk.setVentilated(false);
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst().orElseThrow();
            return TestUtil.expectBooleanParameter(namespace, "Ventilated", false);
        }));
    }

    @Test
    void changeOEMNumberTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        // add brake disk with parameters
        CommittableView view = util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = createDefaultBrakeDisk();
            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
        });

        // assert that namespace with parameters as above has been created
        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst().orElseThrow();
            return TestUtil.expectStringParameter(namespace, "OEM Number", "VW123456");
        }));

        // change OEM number
        util.modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().stream().filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast).findFirst().orElseThrow();
            brakeDisk.setOEM_number("VW654321");
        });

        // assert that namespace with parameters as above has been created
        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces().stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst().orElseThrow();
            return TestUtil.expectStringParameter(namespace, "OEM Number", "VW654321");
        }));

    }

    private BrakeDisk createDefaultBrakeDisk() {
        BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
        brakeDisk.setId("brakeDisk1");
        brakeDisk.setOEM_number("VW123456");
        brakeDisk.setDiameterInMM(120);
        brakeDisk.setCenteringDiameterInMM(20);
        brakeDisk.setRimHoleNumber(1);
        brakeDisk.setHoleArrangementNumber(20);
        brakeDisk.setBoltHoleCircleInMM(60);
        brakeDisk.setBrakeDiskThicknessInMM(30);
        brakeDisk.setMinimumThicknessInMM(25);
        brakeDisk.setVentilated(true);
        return brakeDisk;
    }

    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
        return viewAssertionFunction.apply(view);
    }

}
