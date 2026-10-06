package tools.vitruv.casestudies.brakesystem.consistency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import tools.vitruv.change.correspondence.view.CorrespondenceModelView;

public final class TransitiveCorrespondenceUtility {
    private static final List<String> IDENTIFIER_FEATURES = List.of("id", "name", "shortName");

    private TransitiveCorrespondenceUtility() {
    }

    /**
     * Finds an element of the given type that corresponds to the source via other models, i.e. over
     * a chain of correspondences. Returns null if there is none.
     *
     * <p>The correspondence is one-to-one: elements that already correspond to another element of the
     * source's model are skipped. If several elements qualify (e.g. the instances of a BOM item with a
     * quantity greater than one), the one with the same identifier as the source is preferred.
     */
    public static <T extends EObject> T findTransitivelyCorresponding(CorrespondenceModelView<?> correspondenceModel, EObject source, Class<T> type) {
        return find(correspondenceModel, source, type, true, false);
    }

    /**
     * Like {@link #findTransitivelyCorresponding}, but ignores elements that already correspond to the
     * source directly. Used to find further instances for a source that corresponds to several elements.
     */
    public static <T extends EObject> T findFurtherTransitivelyCorresponding(CorrespondenceModelView<?> correspondenceModel, EObject source, Class<T> type) {
        return find(correspondenceModel, source, type, true, true);
    }

    /**
     * Like {@link #findTransitivelyCorresponding}, but the found element may correspond to several
     * elements of the source's model, e.g. a BOM item to all instances it counts.
     */
    public static <T extends EObject> T findSharedTransitivelyCorresponding(CorrespondenceModelView<?> correspondenceModel, EObject source, Class<T> type) {
        return find(correspondenceModel, source, type, false, false);
    }

    /** Whether the element has a direct correspondence to an element of the given type. */
    public static boolean hasDirectlyCorresponding(CorrespondenceModelView<?> correspondenceModel, EObject source, Class<? extends EObject> type) {
        return correspondenceModel.getCorrespondingEObjects(source).stream().anyMatch(type::isInstance);
    }

    /** The elements of the given type that directly correspond to the source and were not deleted. */
    public static <T extends EObject> List<T> getLiveCorresponding(CorrespondenceModelView<?> correspondenceModel, EObject source, Class<T> type) {
        return correspondenceModel.getCorrespondingEObjects(source)
                .stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(TransitiveCorrespondenceUtility::isLive)
                .toList();
    }

    private static <T extends EObject> T find(CorrespondenceModelView<?> correspondenceModel, EObject source, Class<T> type, boolean oneToOne, boolean excludeDirect) {
        Set<EObject> direct = correspondenceModel.getCorrespondingEObjects(source);
        List<T> candidates = new ArrayList<>();
        Set<EObject> visited = new HashSet<>();
        Deque<EObject> toVisit = new ArrayDeque<>();
        visited.add(source);
        toVisit.add(source);
        while (!toVisit.isEmpty()) {
            EObject current = toVisit.poll();
            for (EObject corresponding : correspondenceModel.getCorrespondingEObjects(current)) {
                if (!isLive(corresponding) || !visited.add(corresponding)) {
                    continue;
                }
                if (type.isInstance(corresponding) && !(excludeDirect && direct.contains(corresponding)) &&
                        !(oneToOne && isTakenByOther(correspondenceModel, corresponding, source))) {
                    candidates.add(type.cast(corresponding));
                }
                toVisit.add(corresponding);
            }
        }
        Object identifier = identifierOf(source);
        return candidates.stream()
                .filter(candidate -> identifier != null && Objects.equals(identifier, identifierOf(candidate)))
                .findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.get(0));
    }

    /** Whether the candidate already corresponds to another element of the source's model. */
    private static boolean isTakenByOther(CorrespondenceModelView<?> correspondenceModel, EObject candidate, EObject source) {
        return correspondenceModel.getCorrespondingEObjects(candidate)
                .stream()
                .anyMatch(other -> other != source && isLive(other) && other.eClass().getEPackage() == source.eClass().getEPackage());
    }

    private static Object identifierOf(EObject element) {
        for (String featureName : IDENTIFIER_FEATURES) {
            EStructuralFeature feature = element.eClass().getEStructuralFeature(featureName);
            if (feature != null && element.eGet(feature) != null) {
                return element.eGet(feature);
            }
        }
        return null;
    }

    /** Deleted elements may still have correspondences until all reactions have run. */
    private static boolean isLive(EObject element) {
        return element.eResource() != null;
    }
}
