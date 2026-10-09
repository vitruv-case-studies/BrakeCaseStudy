package tools.vitruv.casestudies.brakesystem.vsum.Simulink2SysMLTests;

import static tools.vitruv.casestudies.brakesystem.vsum.Simulink2SysMLTests.SysMLTestUtil.hasEmptyMemberships;
import static tools.vitruv.casestudies.brakesystem.vsum.Simulink2SysMLTests.SysMLTestUtil.member;
import static tools.vitruv.casestudies.brakesystem.vsum.Simulink2SysMLTests.SysMLTestUtil.members;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omg.sysml.lang.sysml.AttributeUsage;
import org.omg.sysml.lang.sysml.FeatureDirectionKind;
import org.omg.sysml.lang.sysml.FlowUsage;
import org.omg.sysml.lang.sysml.Package;
import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.PortUsage;

import mir.reactions.simulink2Sysml.Simulink2SysmlChangePropagationSpecification;
import mir.reactions.sysml2Simulink.Sysml2SimulinkChangePropagationSpecification;
import simulink.Block;
import simulink.BusCreator;
import simulink.BusSelector;
import simulink.BusSignalMapping;
import simulink.From;
import simulink.Goto;
import simulink.InPort;
import simulink.InPortBlock;
import simulink.OutPort;
import simulink.Parameter;
import simulink.SimuLinkFactory;
import simulink.SimulinkModel;
import simulink.SingleConnection;
import simulink.SubSystem;
import tools.vitruv.casestudies.brakesystem.consistency.SysmlUtility;
import tools.vitruv.casestudies.brakesystem.vsum.TestBase;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

public class Simulink2SysMLTest extends TestBase {
    private static final SimuLinkFactory FACTORY = SimuLinkFactory.eINSTANCE;

    @Override protected List<ChangePropagationSpecification> createCPS() {
        return List.of(new Simulink2SysmlChangePropagationSpecification(),
                new Sysml2SimulinkChangePropagationSpecification());
    }

    private VirtualModel createVsumWithSimulinkModel(Path tempDir) throws Exception {
        VirtualModel vsum = util.createDefaultVirtualModel(tempDir, necessaryCPS);
        modifySimulink(vsum, model -> { }, tempDir);
        return vsum;
    }

    private void modifySimulink(VirtualModel vsum, Consumer<SimulinkModel> modification, Path tempDir) {
        CommittableView view = util.getDefaultView(vsum, List.of(SimulinkModel.class)).withChangeDerivingTrait();
        util.modifyView(view, (CommittableView v) -> {
            SimulinkModel model = v.getRootObjects(SimulinkModel.class).stream().findFirst().orElse(null);
            if (model == null) {
                model = FACTORY.createSimulinkModel();
                model.setName("BrakeModel");
                v.registerRoot(model, URI.createFileURI(tempDir.resolve("example.simulink").toString()));
            }
            modification.accept(model);
        });
    }

    private SimulinkModel simulinkModel(VirtualModel vsum) {
        View view = util.getDefaultView(vsum, List.of(SimulinkModel.class));
        return view.getRootObjects(SimulinkModel.class).stream().findFirst().orElseThrow();
    }

    private Package sysmlPackage(VirtualModel vsum) {
        View view = util.getDefaultView(vsum, List.of(Package.class));
        return view.getRootObjects(Package.class).stream().findFirst().orElseThrow();
    }

    private static Block block(String name) {
        Block block = FACTORY.createBlock();
        block.setName(name);
        return block;
    }

    private static SubSystem subSystem(String name) {
        SubSystem subSystem = FACTORY.createSubSystem();
        subSystem.setName(name);
        return subSystem;
    }

    private static InPort inPort(Block block, String name) {
        InPort port = FACTORY.createInPort();
        port.setName(name);
        block.getPorts().add(port);
        return port;
    }

    private static OutPort outPort(Block block, String name) {
        OutPort port = FACTORY.createOutPort();
        port.setName(name);
        block.getPorts().add(port);
        return port;
    }

    private static SingleConnection line(OutPort from, InPort to) {
        SingleConnection connection = FACTORY.createSingleConnection();
        connection.setOutport(from);
        connection.setInport(to);
        return connection;
    }

    @Test public void testSimulinkModelCreatesPackage(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithSimulinkModel(tempDir);

        Assertions.assertEquals("BrakeModel", sysmlPackage(vsum).getDeclaredName());
    }

    @Test public void testBlockCreatesPartUsage(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithSimulinkModel(tempDir);

        modifySimulink(vsum, model -> model.getContains().add(block("Caliper")), tempDir);

        Package pkg = sysmlPackage(vsum);
        member(pkg, PartUsage.class, "Caliper");
        Assertions.assertEquals(1, SysmlUtility.ownedMembers(pkg).size());
    }

    @Test public void testNestedBlocksCreateNestedPartUsages(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithSimulinkModel(tempDir);

        modifySimulink(vsum, model -> {
            SubSystem controller = subSystem("Controller");
            controller.getSubBlocks().add(block("Filter"));
            model.getContains().add(controller);
        }, tempDir);

        Package pkg = sysmlPackage(vsum);
        PartUsage controller = member(pkg, PartUsage.class, "Controller");
        PartUsage filter = member(controller, PartUsage.class, "Filter");
        Assertions.assertEquals(List.of(filter), controller.getNestedPart());
        Assertions.assertFalse(hasEmptyMemberships(pkg));
    }

    @Test public void testPortsParametersAndConnectionAreMapped(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithSimulinkModel(tempDir);

        modifySimulink(vsum, model -> {
            SubSystem controller = subSystem("Controller");
            Block sensor = block("Sensor");
            OutPort speed = FACTORY.createOutPort();
            speed.setName("speed");
            sensor.getPorts().add(speed);
            Parameter rate = FACTORY.createParameter();
            rate.setName("sampleRate");
            sensor.getParameters().add(rate);
            Block filter = block("Filter");
            InPort raw = FACTORY.createInPort();
            raw.setName("raw");
            filter.getPorts().add(raw);
            controller.getSubBlocks().add(sensor);
            controller.getSubBlocks().add(filter);
            model.getContains().add(controller);

            SingleConnection connection = FACTORY.createSingleConnection();
            connection.setOutport(speed);
            connection.setInport(raw);
            model.getConnection().add(connection);
        }, tempDir);

        PartUsage controller = member(sysmlPackage(vsum), PartUsage.class, "Controller");
        PartUsage sensor = member(controller, PartUsage.class, "Sensor");
        PortUsage speed = member(sensor, PortUsage.class, "speed");
        PortUsage raw = member(member(controller, PartUsage.class, "Filter"), PortUsage.class, "raw");
        Assertions.assertEquals(FeatureDirectionKind.OUT, speed.getDirection());
        Assertions.assertEquals(FeatureDirectionKind.IN, raw.getDirection());
        member(sensor, AttributeUsage.class, "sampleRate");

        // The flow between two sibling parts belongs to their parent
        FlowUsage flow = members(controller, FlowUsage.class).stream().findFirst().orElseThrow();
        Assertions.assertEquals(List.of(speed), flow.getSource());
        Assertions.assertEquals(List.of(raw), flow.getTarget());
        // The flow adopts the existing line instead of getting a second one
        Assertions.assertEquals(1, simulinkModel(vsum).getConnection().size());
    }

    @Test public void testRenamingBlockRenamesPartUsage(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithSimulinkModel(tempDir);
        modifySimulink(vsum, model -> model.getContains().add(block("Caliper")), tempDir);

        modifySimulink(vsum, model -> model.getContains().getFirst().setName("FrontCaliper"), tempDir);

        member(sysmlPackage(vsum), PartUsage.class, "FrontCaliper");
    }

    @Test public void testDeletingBlockDeletesPartUsage(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithSimulinkModel(tempDir);
        modifySimulink(vsum, model -> model.getContains().add(block("Caliper")), tempDir);

        modifySimulink(vsum, model -> EcoreUtil.delete(model.getContains().getFirst()), tempDir);

        Package pkg = sysmlPackage(vsum);
        Assertions.assertTrue(members(pkg, PartUsage.class).isEmpty());
        Assertions.assertFalse(hasEmptyMemberships(pkg));
    }

    // Goto/From only route the signal: no parts, but a flow between the real ends of the path
    @Test public void testGotoFromPathCreatesFlowBetweenRealPorts(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithSimulinkModel(tempDir);

        modifySimulink(vsum, model -> {
            Block sensor = block("Sensor");
            OutPort speed = outPort(sensor, "speed");
            Goto gotoBlock = FACTORY.createGoto();
            gotoBlock.setName("SpeedTag");
            InPort gotoIn = inPort(gotoBlock, "in");
            From from = FACTORY.createFrom();
            from.setName("SpeedTagReader");
            OutPort fromOut = outPort(from, "out");
            gotoBlock.getFromBlocks().add(from);
            Block controller = block("Controller");
            InPort measured = inPort(controller, "measured");
            model.getContains().addAll(List.of(sensor, gotoBlock, from, controller));
            model.getConnection().add(line(speed, gotoIn));
            model.getConnection().add(line(fromOut, measured));
        }, tempDir);

        Package pkg = sysmlPackage(vsum);
        Assertions.assertEquals(List.of("Sensor", "Controller"),
                members(pkg, PartUsage.class).stream().map(PartUsage::getDeclaredName).toList());
        FlowUsage flow = members(pkg, FlowUsage.class).stream().findFirst().orElseThrow();
        Assertions.assertEquals(List.of(member(member(pkg, PartUsage.class, "Sensor"), PortUsage.class, "speed")),
                flow.getSource());
        Assertions.assertEquals(List.of(member(member(pkg, PartUsage.class, "Controller"), PortUsage.class, "measured")),
                flow.getTarget());

        // The flow does not get a direct line of its own: the measured port is already fed via Goto/From
        Assertions.assertEquals(2, simulinkModel(vsum).getConnection().size());

        // Breaking the path removes the flow
        modifySimulink(vsum, model -> EcoreUtil.delete(model.getContains().get(2), true), tempDir);

        Assertions.assertTrue(members(sysmlPackage(vsum), FlowUsage.class).isEmpty());
    }

    // The bus selector's mapping decides which of the bundled signals reaches the destination
    @Test public void testBusPathCreatesFlowFromSelectedSignalOnly(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithSimulinkModel(tempDir);

        modifySimulink(vsum, model -> {
            Block wheel = block("WheelSensor");
            OutPort speed = outPort(wheel, "speed");
            Block pedal = block("PedalSensor");
            OutPort position = outPort(pedal, "position");
            BusCreator creator = FACTORY.createBusCreator();
            creator.setName("SensorBus");
            InPort creatorIn1 = inPort(creator, "in1");
            InPort creatorIn2 = inPort(creator, "in2");
            OutPort bus = outPort(creator, "bus");
            BusSelector selector = FACTORY.createBusSelector();
            selector.setName("SelectSpeed");
            InPort selectorIn = inPort(selector, "bus");
            OutPort selected = outPort(selector, "speed");
            BusSignalMapping mapping = FACTORY.createBusSignalMapping();
            mapping.setMappingFrom(speed);
            mapping.setMappingTo(selected);
            selector.getMappings().add(mapping);
            Block controller = block("Controller");
            InPort measured = inPort(controller, "measured");
            model.getContains().addAll(List.of(wheel, pedal, creator, selector, controller));
            model.getConnection().addAll(List.of(line(speed, creatorIn1), line(position, creatorIn2),
                    line(bus, selectorIn), line(selected, measured)));
        }, tempDir);

        Package pkg = sysmlPackage(vsum);
        Assertions.assertEquals(3, members(pkg, PartUsage.class).size());
        List<FlowUsage> flows = members(pkg, FlowUsage.class);
        Assertions.assertEquals(1, flows.size());
        Assertions.assertEquals(List.of(member(member(pkg, PartUsage.class, "WheelSensor"), PortUsage.class, "speed")),
                flows.getFirst().getSource());
    }

    // An Inport block passes the signal of its SubSystem's port inward: the flow starts at that port
    @Test public void testPortBlockPathCreatesFlowFromSubSystemPort(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithSimulinkModel(tempDir);

        modifySimulink(vsum, model -> {
            SubSystem controller = subSystem("Controller");
            InPort request = inPort(controller, "request");
            InPortBlock requestBlock = FACTORY.createInPortBlock();
            requestBlock.setName("request");
            requestBlock.setPort(request);
            OutPort requestOut = outPort(requestBlock, "out");
            Block filter = block("Filter");
            InPort raw = inPort(filter, "raw");
            controller.getSubBlocks().addAll(List.of(requestBlock, filter));
            model.getContains().add(controller);
            model.getConnection().add(line(requestOut, raw));
        }, tempDir);

        PartUsage controller = member(sysmlPackage(vsum), PartUsage.class, "Controller");
        Assertions.assertEquals(List.of("Filter"), members(controller, PartUsage.class).stream()
                .map(PartUsage::getDeclaredName).toList());
        FlowUsage flow = members(controller, FlowUsage.class).stream().findFirst().orElseThrow();
        Assertions.assertEquals(List.of(member(controller, PortUsage.class, "request")), flow.getSource());
        Assertions.assertEquals(List.of(member(member(controller, PartUsage.class, "Filter"), PortUsage.class, "raw")),
                flow.getTarget());
    }

    @Test public void testMovingBlockMovesPartUsage(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = createVsumWithSimulinkModel(tempDir);
        modifySimulink(vsum, model -> {
            SubSystem front = subSystem("FrontAxle");
            front.getSubBlocks().add(block("Caliper"));
            model.getContains().add(front);
            model.getContains().add(subSystem("RearAxle"));
        }, tempDir);

        modifySimulink(vsum, model -> {
            SubSystem front = (SubSystem) model.getContains().get(0);
            SubSystem rear = (SubSystem) model.getContains().get(1);
            rear.getSubBlocks().add(front.getSubBlocks().getFirst());
        }, tempDir);

        Package pkg = sysmlPackage(vsum);
        PartUsage front = member(pkg, PartUsage.class, "FrontAxle");
        PartUsage rear = member(pkg, PartUsage.class, "RearAxle");
        PartUsage caliper = member(rear, PartUsage.class, "Caliper");
        Assertions.assertTrue(members(front, PartUsage.class).isEmpty());
        Assertions.assertTrue(front.getNestedPart().isEmpty());
        Assertions.assertEquals(List.of(caliper), rear.getNestedPart());
        Assertions.assertFalse(hasEmptyMemberships(pkg));
    }
}
