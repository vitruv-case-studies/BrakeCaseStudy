package tools.vitruv.casestudies.brakesystem.vsum.Simulink2SysMLTests;

import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.emf.ecore.EObject;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.OwningMembership;

import tools.vitruv.casestudies.brakesystem.consistency.SysmlUtility;

/** Navigation helpers for SysML models, which own their elements via memberships. */
final class SysMLTestUtil {
    private SysMLTestUtil() {
    }

    static <T extends Element> List<T> members(Element owner, Class<T> type) {
        return SysmlUtility.ownedMembers(owner).stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** The single member of the given type and name; fails if there is none or more than one. */
    static <T extends Element> T member(Element owner, Class<T> type, String name) {
        List<T> found = members(owner, type).stream().filter(it -> name.equals(it.getDeclaredName())).toList();
        if (found.size() != 1) {
            throw new AssertionError("Expected exactly one " + type.getSimpleName() + " '" + name + "' in "
                    + owner.getDeclaredName() + ", found " + found.size());
        }
        return found.getFirst();
    }

    /** Memberships that own nothing are leftovers of moved or deleted elements. */
    static boolean hasEmptyMemberships(Element root) {
        return Stream.concat(Stream.of((EObject) root), streamContents(root))
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .anyMatch(membership -> membership.getOwnedRelatedElement().isEmpty());
    }

    private static Stream<EObject> streamContents(EObject root) {
        Iterable<EObject> contents = root::eAllContents;
        return StreamSupport.stream(contents.spliterator(), false);
    }
}
