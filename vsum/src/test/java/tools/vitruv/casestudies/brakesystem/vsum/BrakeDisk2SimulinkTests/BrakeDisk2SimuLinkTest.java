package tools.vitruv.casestudies.brakesystem.vsum.BrakeDisk2SimulinkTests;

import brakesystem.*;
import mir.reactions.brakesystem2simulink.Brakesystem2simulinkChangePropagationSpecification;
import mir.reactions.simulink2brakesystem.Simulink2brakesystemChangePropagationSpecification;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import simulink.Block;
import simulink.SimulinkModel;
import simulink.SubSystem;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public class BrakeDisk2SimuLinkTest {
    TestUtil util = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Brakesystem2simulinkChangePropagationSpecification(), new Simulink2brakesystemChangePropagationSpecification());

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    @Test
    void brakeDiskInsertionAndPropagationTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        util.userInteraction.addNextSingleSelection(0);
        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            BrakeDisk brakeDisk = createDefaultBrakeDisk();
            brakeDisk.setId("brakeDisk1");

            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(SimulinkModel.class)), (View v) -> {
            SimulinkModel model = v.getRootObjects(SimulinkModel.class).iterator().next();
            Block block = model.getContains().getFirst();

            boolean isOEMNumber = TestUtil.expectParameter(block, "OEM Number", "string", "VW123456");
            boolean isDiameterInMM = TestUtil.expectParameter(block, "Diameter", "int32", 120);
            boolean isCenteringDiameterInMM = TestUtil.expectParameter(block, "Centering Diameter", "int32", 20);
            boolean isRimHoleNumber = TestUtil.expectParameter(block, "Rim Hole Number", "int32", 1);
            boolean isHoleArrangementNumber = TestUtil.expectParameter(block, "Hole Arrangement Number", "int32", 20);
            boolean isBoltHoleCircleInMM = TestUtil.expectParameter(block, "Bolt Hole Circle", "int32", 60);
            boolean isBrakeDiskThicknessInMM = TestUtil.expectParameter(block, "Brake Disk Thickness", "int32", 30);
            boolean isMinimumThicknessInMM = TestUtil.expectParameter(block, "Minimum Thickness", "int32", 25);
            boolean isVentilated = TestUtil.expectParameter(block, "Ventilated", "boolean", true);

            boolean allParametersCorrect = isOEMNumber && isDiameterInMM && isCenteringDiameterInMM && isRimHoleNumber && isHoleArrangementNumber && isBoltHoleCircleInMM && isBrakeDiskThicknessInMM && isMinimumThicknessInMM && isVentilated;

            return model.getContains().size() == 1 && block.getName().equals("brakeDisk1") && allParametersCorrect;
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

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(SimulinkModel.class)), (View v) -> {
            SimulinkModel model = v.getRootObjects(SimulinkModel.class).iterator().next();
            SubSystem caliper = (SubSystem) model.getContains().getFirst();
            Block pad = caliper.getSubBlocks().getFirst();

            boolean isPistonDiameterInMM = TestUtil.expectParameter(caliper, "Piston Diameter", "int32", 50);
            boolean isWearWarning = TestUtil.expectParameter(pad, "Wear Warning", "boolean", true);

            return model.getContains().size() == 1 && caliper.getName().equals("brakeCaliper1") && pad.getName().equals("brakePad1") && isPistonDiameterInMM && isWearWarning;
        }));
    }

    @Test
    void changeDiameterTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        util.userInteraction.addNextSingleSelection(0);
        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
            brakeDisk.setId("brakeDisk1");
            brakeDisk.setDiameterInMM(120);
            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
        });

        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().stream().filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast).findFirst().orElseThrow();
            brakeDisk.setDiameterInMM(130);
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(SimulinkModel.class)), (View v) -> {
            Block block = v.getRootObjects(SimulinkModel.class).iterator().next().getContains().stream().filter(b -> b.getName().equals("brakeDisk1")).findFirst().orElseThrow();
            return TestUtil.expectParameter(block, "Diameter", "int32", 130);
        }));
    }

    @Test
    void changeIdTest(@TempDir Path tempDir) throws IOException {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        util.registerRootObjects(vsum, tempDir);

        util.userInteraction.addNextSingleSelection(0);
        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            BrakeDisk brakeDisk = createDefaultBrakeDisk();
            v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
        });

        util.modifyView(util.getDefaultView(vsum, List.of(Brakesystem.class)).withChangeRecordingTrait(), (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().stream().filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast).findFirst().orElseThrow();
            brakeDisk.setId("newId");
        });

        Assertions.assertTrue(assertView(util.getDefaultView(vsum, List.of(SimulinkModel.class)), (View v) -> {
            Block block = v.getRootObjects(SimulinkModel.class).iterator().next().getContains().stream().filter(b -> b.getName().equals("newId")).findFirst().orElseThrow();
            return block != null;
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
