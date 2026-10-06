package tools.vitruv.casestudies.brakesystem.consistency;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;
import tools.vitruv.change.correspondence.view.CorrespondenceModelView;

public final class TransitiveCorrespondenceUtility {
    private TransitiveCorrespondenceUtility() {
    }

    /**
     * Finds an element of the given type that corresponds to the source via other models, i.e. over
     * a chain of correspondences. Returns null if there is none.
     */
    public static <T extends EObject> T findTransitivelyCorresponding(CorrespondenceModelView<?> correspondenceModel, EObject source, Class<T> type) {
        Set<EObject> visited = new HashSet<>();
        Deque<EObject> toVisit = new ArrayDeque<>();
        visited.add(source);
        toVisit.add(source);
        while (!toVisit.isEmpty()) {
            EObject current = toVisit.poll();
            for (EObject corresponding : correspondenceModel.getCorrespondingEObjects(current)) {
                if (visited.add(corresponding)) {
                    if (type.isInstance(corresponding)) {
                        return type.cast(corresponding);
                    }
                    toVisit.add(corresponding);
                }
            }
        }
        return null;
    }

    /** Whether the element has a direct correspondence to an element of the given type. */
    public static boolean hasDirectlyCorresponding(CorrespondenceModelView<?> correspondenceModel, EObject source, Class<? extends EObject> type) {
        return correspondenceModel.getCorrespondingEObjects(source).stream().anyMatch(type::isInstance);
    }
}
