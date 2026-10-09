package tools.vitruv.casestudies.brakesystem.consistency;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;
import simulink.Block;
import simulink.BusCreator;
import simulink.BusSelector;
import simulink.BusSignalMapping;
import simulink.BusSpecification;
import simulink.From;
import simulink.InPort;
import simulink.InPortBlock;
import simulink.OutPort;
import simulink.OutPortBlock;
import simulink.Port;
import simulink.SingleConnection;
import simulink.VirtualBlock;

/**
 * Resolves Simulink signal paths to the ports they really connect.
 *
 * <p>Virtual blocks (Goto/From, tag visibility, port blocks) and bus blocks only route signals; they are no
 * components of the system. A signal from a component to another component may pass through any number of
 * them. This utility follows such a path backwards from its destination to the component ports the signal
 * originates from:
 * <ul>
 *   <li>a From block receives the signal of the InPort of its Goto block,</li>
 *   <li>a bus creator bundles the signals of all its InPorts,</li>
 *   <li>a bus selector OutPort carries the signals its {@link BusSignalMapping}s select (all signals of the bus
 *       if it has no mapping),</li>
 *   <li>an InPortBlock inside a SubSystem emits the signal of the SubSystem's InPort it represents, and an
 *       OutPortBlock passes its signal on to the SubSystem's OutPort it represents.</li>
 * </ul>
 */
public final class SimulinkSignalUtility {
    private SimulinkSignalUtility() {
    }

    /** Whether the block only routes signals instead of being a component of the system. */
    public static boolean isVirtual(Block block) {
        return block instanceof VirtualBlock || block instanceof BusSpecification;
    }

    /**
     * The component ports whose signals arrive at the given port. Empty if the port is no signal destination of
     * a component: an InPort of a virtual block, or an OutPort that is not fed by an OutPortBlock.
     */
    public static List<Port> realSources(Port target) {
        Set<Port> sources = new LinkedHashSet<>();
        Set<EObject> visited = new HashSet<>();
        if (target instanceof InPort inPort && inPort.getContainer() != null && !isVirtual(inPort.getContainer())) {
            collectFromInPort(inPort, sources, visited);
        } else if (target instanceof OutPort && target.getPortBlock() instanceof OutPortBlock portBlock) {
            for (InPort inPort : inPorts(portBlock)) {
                collectFromInPort(inPort, sources, visited);
            }
        }
        return List.copyOf(sources);
    }

    private static void collectFromInPort(InPort inPort, Set<Port> sources, Set<EObject> visited) {
        SingleConnection connection = inPort.getConnection();
        if (!visited.add(inPort) || connection == null) {
            return;
        }
        // A branch of a MultiConnection has no outport of its own; the signal comes from the MultiConnection's.
        OutPort outPort = connection.getParent() != null ? connection.getParent().getOutport() : connection.getOutport();
        if (outPort != null) {
            collectFromOutPort(outPort, sources, visited);
        }
    }

    private static void collectFromOutPort(OutPort outPort, Set<Port> sources, Set<EObject> visited) {
        Block block = outPort.getContainer();
        if (!visited.add(outPort) || block == null) {
            return;
        }
        if (!isVirtual(block)) {
            sources.add(outPort);
        } else if (block instanceof InPortBlock portBlock) {
            if (portBlock.getPort() != null) {
                sources.add(portBlock.getPort());
            }
        } else if (block instanceof From from) {
            if (from.getGotoBlock() != null) {
                for (InPort inPort : inPorts(from.getGotoBlock())) {
                    collectFromInPort(inPort, sources, visited);
                }
            }
        } else if (block instanceof BusSelector selector) {
            List<BusSignalMapping> mappings = selector.getMappings()
                    .stream()
                    .filter(mapping -> mapping.getMappingTo() == outPort && mapping.getMappingFrom() != null)
                    .toList();
            if (mappings.isEmpty()) {
                for (InPort inPort : inPorts(selector)) {
                    collectFromInPort(inPort, sources, visited);
                }
            }
            for (BusSignalMapping mapping : mappings) {
                collectFromOutPort(mapping.getMappingFrom(), sources, visited);
            }
        } else if (block instanceof BusCreator) {
            for (InPort inPort : inPorts(block)) {
                collectFromInPort(inPort, sources, visited);
            }
        }
    }

    private static List<InPort> inPorts(Block block) {
        return block.getPorts().stream().filter(InPort.class::isInstance).map(InPort.class::cast).toList();
    }
}
