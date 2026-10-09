package tools.vitruv.casestudies.brakesystem.vsum.Simulink2SysMLTests;

import static tools.vitruv.casestudies.brakesystem.vsum.Simulink2SysMLTests.SysMLTestUtil.member;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omg.sysml.lang.sysml.AttributeUsage;
import org.omg.sysml.lang.sysml.ConnectionUsage;
import org.omg.sysml.lang.sysml.FeatureDirectionKind;
import org.omg.sysml.lang.sysml.FlowUsage;
import org.omg.sysml.lang.sysml.Package;
import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.SysmlFactory;

import mir.reactions.simulink2Sysml.Simulink2SysmlChangePropagationSpecification;
import mir.reactions.sysml2Simulink.Sysml2SimulinkChangePropagationSpecification;
import simulink.Block;
import simulink.InPort;
import simulink.OutPort;
import simulink.SimulinkModel;
import simulink.SingleConnection;
import simulink.SubSystem;
import tools.vitruv.casestudies.brakesystem.consistency.SysmlUtility;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

public class SysML2SimulinkTest extends TestBase {
    private static final SysmlFactory FACTORY = SysmlFactory.eINSTANCE;

    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Simulink2SysmlChangePropagationSpecification(),
                new Sysml2SimulinkChangePropagationSpecification());
    }

    private VirtualModel createVsumWithPackage(Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        modifySysml(vsum, pkg -> { }, tempDir);
        return vsum;
    }

    private void modifySysml(VirtualModel vsum, Consumer<Package> modification, Path tempDir) {
        CommittableView view = util.getDefaultView(vsum, List.of(Package.class)).withChangeRecordingTrait();
        util.modifyView(view, (CommittableView v) -> {
            Package pkg = v.getRootObjects(Package.class).stream().findFirst().orElse(null);
            if (pkg == null) {
                pkg = FACTORY.createPackage();
                pkg.setDeclaredName("BrakeArchitecture");
                v.registerRoot(pkg, URI.createFileURI(tempDir.resolve("example.sysml").toString()));
            }
            modification.accept(pkg);
        });
    }

    private SimulinkModel simulinkModel(VirtualModel vsum) {
        View view = util.getDefaultView(vsum, List.of(SimulinkModel.class));
        return view.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
    }

    private static PartUsage part(String name) {
        PartUsage part = FACTORY.createPartUsage();
        part.setDeclaredName(name);
        return part;
    }

    private static PortUsage port(String name, FeatureDirectionKind direction) {
        PortUsage port = FACTORY.createPortUsage();
        port.setDeclaredName(name);
        port.setDirection(direction);
        return port;
    }

    private static Block block(SimulinkModel model, String name) {
        List<Block> found = model.getContains().stream().filter(it -> name.equals(it.getName())).toList();
        Assertions.assertEquals(1, found.size(), "blocks named " + name);
        return found.getFirst();
    }

    @Test public void testPackageCreatesSimulinkModel(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithPackage(tempDir);

        Assertions.assertEquals("BrakeArchitecture", simulinkModel(vsum).getName());
    }

    @Test public void testPartUsageCreatesBlockInSimulinkModel(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithPackage(tempDir);

        modifySysml(vsum, pkg -> SysmlUtility.attachAsMember(pkg, part("Caliper")), tempDir);

        Block caliper = block(simulinkModel(vsum), "Caliper");
        Assertions.assertFalse(caliper instanceof SubSystem);
    }

    // Connections, interfaces and allocations are PartUsages in SysML v2 as well, but no blocks
    @Test public void testConnectionUsageCreatesNoBlock(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithPackage(tempDir);

        modifySysml(vsum, pkg -> {
            ConnectionUsage connection = FACTORY.createConnectionUsage();
            connection.setDeclaredName("brakeLine");
            SysmlUtility.attachAsMember(pkg, connection);
        }, tempDir);

        Assertions.assertTrue(simulinkModel(vsum).getContains().isEmpty());
    }

    @Test public void testNestedPartTurnsBlockIntoSubSystemAndBack(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithPackage(tempDir);
        modifySysml(vsum, pkg -> {
            PartUsage controller = part("Controller");
            SysmlUtility.attachAsMember(pkg, controller);
            SysmlUtility.attachAsMember(controller, port("brakeRequest", FeatureDirectionKind.IN));
        }, tempDir);
        Assertions.assertFalse(block(simulinkModel(vsum), "Controller") instanceof SubSystem);

        modifySysml(vsum, pkg -> SysmlUtility.attachAsMember(member(pkg, PartUsage.class, "Controller"),
                part("Filter")), tempDir);

        // The swapped block keeps its place in the model and its ports
        SimulinkModel model = simulinkModel(vsum);
        Assertions.assertEquals(1, model.getContains().size());
        SubSystem controller = (SubSystem) block(model, "Controller");
        Assertions.assertEquals(List.of("Filter"), controller.getSubBlocks().stream().map(Block::getName).toList());
        Assertions.assertEquals(List.of("brakeRequest"), controller.getPorts().stream().map(it -> it.getName()).toList());

        modifySysml(vsum, pkg -> SysmlUtility.detach(member(member(pkg, PartUsage.class, "Controller"),
                PartUsage.class, "Filter")), tempDir);

        Block downgraded = block(simulinkModel(vsum), "Controller");
        Assertions.assertFalse(downgraded instanceof SubSystem);
        Assertions.assertEquals(1, downgraded.getPorts().size());
    }

    @Test public void testFlowUsageCreatesContainedConnection(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithPackage(tempDir);

        modifySysml(vsum, pkg -> {
            PartUsage sensor = part("Sensor");
            PortUsage speed = port("speed", FeatureDirectionKind.OUT);
            SysmlUtility.attachAsMember(sensor, speed);
            PartUsage controller = part("Controller");
            PortUsage measured = port("measured", FeatureDirectionKind.IN);
            SysmlUtility.attachAsMember(controller, measured);
            SysmlUtility.attachAsMember(pkg, sensor);
            SysmlUtility.attachAsMember(pkg, controller);

            FlowUsage flow = FACTORY.createFlowUsage();
            flow.getSource().add(speed);
            flow.getTarget().add(measured);
            SysmlUtility.attachAsMember(pkg, flow);
        }, tempDir);

        SimulinkModel model = simulinkModel(vsum);
        OutPort speed = (OutPort) block(model, "Sensor").getPorts().getFirst();
        InPort measured = (InPort) block(model, "Controller").getPorts().getFirst();
        Assertions.assertEquals(1, model.getConnection().size());
        SingleConnection connection = (SingleConnection) model.getConnection().getFirst();
        Assertions.assertEquals(speed, connection.getOutport());
        Assertions.assertEquals(measured, connection.getInport());
    }

    // The attribute of a port belongs to the port, not to the block owning the port
    @Test public void testAttributeUsagesCreateParametersAtTheRightOwner(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithPackage(tempDir);

        modifySysml(vsum, pkg -> {
            PartUsage caliper = part("Caliper");
            AttributeUsage pistonArea = FACTORY.createAttributeUsage();
            pistonArea.setDeclaredName("pistonArea");
            SysmlUtility.attachAsMember(caliper, pistonArea);
            PortUsage pressure = port("pressure", FeatureDirectionKind.IN);
            AttributeUsage unit = FACTORY.createAttributeUsage();
            unit.setDeclaredName("unit");
            SysmlUtility.attachAsMember(pressure, unit);
            SysmlUtility.attachAsMember(caliper, pressure);
            SysmlUtility.attachAsMember(pkg, caliper);
        }, tempDir);

        Block caliper = block(simulinkModel(vsum), "Caliper");
        Assertions.assertEquals(List.of("pistonArea"), caliper.getParameters().stream().map(it -> it.getName()).toList());
        Assertions.assertEquals(List.of("unit"),
                caliper.getPorts().getFirst().getParameters().stream().map(it -> it.getName()).toList());
    }

    @Test public void testRenamingPartUsageRenamesBlock(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithPackage(tempDir);
        modifySysml(vsum, pkg -> SysmlUtility.attachAsMember(pkg, part("Caliper")), tempDir);

        modifySysml(vsum, pkg -> member(pkg, PartUsage.class, "Caliper").setDeclaredName("FrontCaliper"), tempDir);

        block(simulinkModel(vsum), "FrontCaliper");
    }
}
