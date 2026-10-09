package tools.vitruv.casestudies.brakesystem.consistency;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.FeatureMembership;
import org.omg.sysml.lang.sysml.FlowUsage;
import org.omg.sysml.lang.sysml.OwningMembership;
import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.Relationship;
import org.omg.sysml.lang.sysml.SysmlPackage;
import org.omg.sysml.lang.sysml.SysmlFactory;
import org.omg.sysml.lang.sysml.Type;

/**
 * Ownership conventions shared by the reactions between Simulink and SysML.
 *
 * <p>SysML has no direct parent-child containment: an element is owned through a membership
 * relationship of its owner. Members of a type (e.g. a part) use a {@link FeatureMembership},
 * members of a package an {@link OwningMembership}. {@code nestedPart} is not derived in this
 * metamodel, so it is kept in sync explicitly.
 */
public final class SysmlUtility {
    private SysmlUtility() {
    }

    /** The element owning the given element via a membership, or null if it has none. */
    public static Element owner(Element element) {
        Relationship membership = element.getOwningRelationship();
        return membership == null ? null : membership.getOwningRelatedElement();
    }

    /** The elements owned by the given element via its memberships. */
    public static List<Element> ownedMembers(Element element) {
        List<Element> members = new ArrayList<>();
        for (Relationship relationship : element.getOwnedRelationship()) {
            members.addAll(relationship.getOwnedRelatedElement());
        }
        return members;
    }

    /**
     * Whether the element is a plain part, not one of the specialized parts (connections, interfaces,
     * allocations, renderings, views) that have no Simulink counterpart.
     */
    public static boolean isPlainPart(EObject element) {
        return element != null && element.eClass() == SysmlPackage.eINSTANCE.getPartUsage();
    }

    /**
     * Makes the member owned by the parent. A member that is already owned elsewhere is moved, and
     * its previous membership is removed instead of being left behind empty.
     */
    public static void attachAsMember(Element parent, Element member) {
        if (owner(member) == parent) {
            return;
        }
        Relationship previous = member.getOwningRelationship();
        Element previousOwner = owner(member);

        Relationship membership = parent instanceof Type ? SysmlFactory.eINSTANCE.createFeatureMembership()
                : SysmlFactory.eINSTANCE.createOwningMembership();
        parent.getOwnedRelationship().add(membership);
        membership.getOwnedRelatedElement().add(member);

        if (previous != null) {
            EcoreUtil.remove(previous);
        }
        if (previousOwner instanceof PartUsage previousPart && member instanceof PartUsage part) {
            previousPart.getNestedPart().remove(part);
        }
        if (parent instanceof PartUsage parentPart && member instanceof PartUsage part) {
            parentPart.getNestedPart().add(part);
        }
    }

    /** Removes the member from its owner together with its membership. */
    public static void detach(Element member) {
        Element previousOwner = owner(member);
        if (previousOwner instanceof PartUsage previousPart && member instanceof PartUsage part) {
            previousPart.getNestedPart().remove(part);
        }
        Relationship membership = member.getOwningRelationship();
        EcoreUtil.remove(membership != null ? membership : member);
    }

    /** The flows in the port's model that start or end at the port. */
    public static List<FlowUsage> flowsAt(Element port) {
        List<FlowUsage> flows = new ArrayList<>();
        EcoreUtil.getRootContainer(port).eAllContents().forEachRemaining(element -> {
            if (element instanceof FlowUsage flow && (flow.getSource().contains(port) || flow.getTarget().contains(port))) {
                flows.add(flow);
            }
        });
        return flows;
    }

    /**
     * The element that should own a flow between the two ports: the closest common owner of the
     * ports' parts, e.g. the parent of two sibling parts, or the outer part for a flow between a
     * part's own port and a port of one of its nested parts. Null if they share no owner.
     */
    public static Element flowOwner(Element sourcePort, Element targetPort) {
        Set<Element> sourceOwners = new HashSet<>();
        for (Element current = owner(sourcePort); current != null; current = owner(current)) {
            sourceOwners.add(current);
        }
        for (Element current = owner(targetPort); current != null; current = owner(current)) {
            if (sourceOwners.contains(current)) {
                return current;
            }
        }
        return null;
    }
}
