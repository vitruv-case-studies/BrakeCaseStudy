package tools.vitruv.casestudies.brakesystem.consistency;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import simulink.Block;

/** Conventions shared by the reactions that change the Simulink model structurally. */
public final class SimulinkUtility {
    private SimulinkUtility() {
    }

    /**
     * Puts the replacement at the place of the original block (same container, same position) and
     * moves everything both block types have in common onto it: name, ports, parameters, and the
     * other block features. Used for the Block <-> SubSystem swap, as SubSystem is a separate EClass.
     */
    @SuppressWarnings("unchecked")
    public static void replaceBlock(Block original, Block replacement) {
        for (EStructuralFeature feature : original.eClass().getEAllStructuralFeatures()) {
            if (!feature.isChangeable() || feature.isDerived() || feature.isTransient()
                    || replacement.eClass().getEStructuralFeature(feature.getName()) != feature
                    || feature instanceof EReference reference && reference.isContainer()
                    || !original.eIsSet(feature)) {
                continue;
            }
            if (feature.isMany()) {
                List<Object> values = new ArrayList<>((EList<Object>) original.eGet(feature));
                ((EList<Object>) replacement.eGet(feature)).addAll(values);
            } else {
                replacement.eSet(feature, original.eGet(feature));
            }
        }
        EObject container = original.eContainer();
        if (container != null) {
            EList<Object> siblings = (EList<Object>) container.eGet(original.eContainingFeature());
            siblings.set(siblings.indexOf(original), replacement);
        } else {
            EcoreUtil.replace(original, replacement);
        }
    }
}
