package tools.vitruv.casestudies.brakesystem.consistency;

import simulink.Block;
import simulink.SimulinkModel;
import simulink.SubSystem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Cad2SimulinkUtility {
    private Cad2SimulinkUtility() {
    }

    public static List<SubSystem> getAllSubSystems(SimulinkModel model) {
        Set<SubSystem> subSystemsToProcess = new HashSet<>();
        Set<SubSystem> processedSubSystems = new HashSet<>();

        for (Block block : model.getContains()) {
            if (block instanceof SubSystem subSystem) {
                subSystemsToProcess.add(subSystem);
            }
        }

        while (!subSystemsToProcess.isEmpty()) {
            SubSystem processeeSubSystem = subSystemsToProcess.iterator().next();
            subSystemsToProcess.remove(processeeSubSystem);
            processedSubSystems.add(processeeSubSystem);

            for (Block block : processeeSubSystem.getSubBlocks()) {
                if (block instanceof SubSystem potentiallyUnprocessedSubSystem && !processedSubSystems.contains(potentiallyUnprocessedSubSystem)) {
                    subSystemsToProcess.add(potentiallyUnprocessedSubSystem);
                }
            }
        }

        return processedSubSystems.stream().toList();
    }
}
