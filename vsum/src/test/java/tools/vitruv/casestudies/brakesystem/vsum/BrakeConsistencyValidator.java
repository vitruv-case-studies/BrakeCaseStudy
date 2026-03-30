package tools.vitruv.casestudies.brakesystem.vsum;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import brakesystem.BrakeComponent;
import brakesystem.BrakeDisk;
import brakesystem.BrakePad;
import brakesystem.Brakesystem;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import safety.SafetyAssessment;
import safety.SafetyEntry;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;

/**
 * Cross-model consistency validator for the three-model Brake case study.
 *
 * <p>Checks invariants maintained by the Vitruvius Reactions across
 * M₁ (Brakesystem), M₂ (CAD), and M₃ (Safety).
 */
public class BrakeConsistencyValidator {

    /**
     * Validates structural consistency invariants that must always hold
     * after a successful merge, regardless of whether safety values were
     * overridden by original changes.
     *
     * @return list of violation messages (empty if consistent)
     */
    public static List<String> validateStructure(VirtualModel merged) {
        List<String> violations = new ArrayList<>();

        Brakesystem bs = getBrakesystemRoot(merged);
        CAD_Model cad = getCADRoot(merged);
        SafetyAssessment safety = getSafetyRoot(merged);

        if (bs == null) {
            violations.add("Brakesystem root is missing");
            return violations;
        }
        if (cad == null) {
            violations.add("CAD_Model root is missing");
            return violations;
        }
        if (safety == null) {
            violations.add("SafetyAssessment root is missing");
            return violations;
        }

        var components = bs.getBrakeComponents();
        var namespaces = cad.getNamespaces();
        var entries = safety.getSafetyEntries();

        // Count consistency
        if (components.size() != namespaces.size()) {
            violations.add(String.format(
                    "Count mismatch: %d BrakeComponents vs %d Namespaces",
                    components.size(), namespaces.size()));
        }
        if (components.size() != entries.size()) {
            violations.add(String.format(
                    "Count mismatch: %d BrakeComponents vs %d SafetyEntries",
                    components.size(), entries.size()));
        }

        Set<String> namespaceIds = namespaces.stream()
                .map(Namespace::getId).collect(Collectors.toSet());
        Set<String> safetyComponentIds = entries.stream()
                .map(SafetyEntry::getComponentId).collect(Collectors.toSet());

        for (BrakeComponent comp : components) {
            String id = comp.getId();

            // Identity sync: BrakeComponent.id == Namespace.id
            if (!namespaceIds.contains(id)) {
                violations.add("BrakeComponent '" + id
                        + "' has no corresponding Namespace with matching id");
            }

            // Safety correspondence: BrakeComponent.id == SafetyEntry.componentId
            if (!safetyComponentIds.contains(id)) {
                violations.add("BrakeComponent '" + id
                        + "' has no corresponding SafetyEntry with matching componentId");
            }
        }

        // Check reverse: every Namespace has a corresponding BrakeComponent
        Set<String> componentIds = components.stream()
                .map(BrakeComponent::getId).collect(Collectors.toSet());
        for (Namespace ns : namespaces) {
            if (!componentIds.contains(ns.getId())) {
                violations.add("Namespace '" + ns.getId()
                        + "' has no corresponding BrakeComponent");
            }
        }

        return violations;
    }

    /**
     * Validates formula-based consistency invariants for consequential models.
     * Only call this when no direct user overrides of safety values occurred
     * (i.e., all safety values should be purely Reaction-derived).
     *
     * @return list of violation messages (empty if consistent)
     */
    public static List<String> validateFormulas(VirtualModel merged) {
        List<String> violations = new ArrayList<>();

        Brakesystem bs = getBrakesystemRoot(merged);
        CAD_Model cad = getCADRoot(merged);
        SafetyAssessment safety = getSafetyRoot(merged);

        if (bs == null || cad == null || safety == null) {
            violations.add("Cannot validate formulas: model root(s) missing");
            return violations;
        }

        for (BrakeComponent comp : bs.getBrakeComponents()) {
            SafetyEntry entry = findSafetyEntry(safety, comp.getId());
            if (entry == null) continue;

            if (comp instanceof BrakeDisk disk) {
                // thermalLoadRating == diameterInMM * brakeDiskThicknessInMM * 0.01
                float expected = disk.getDiameterInMM() * disk.getBrakeDiskThicknessInMM() * 0.01f;
                if (Math.abs(entry.getThermalLoadRating() - expected) > 0.01f) {
                    violations.add(String.format(
                            "BrakeDisk '%s': thermalLoadRating=%.2f but expected %.2f (d=%d, t=%d)",
                            disk.getId(), entry.getThermalLoadRating(), expected,
                            disk.getDiameterInMM(), disk.getBrakeDiskThicknessInMM()));
                }

                // CAD Diameter parameter == diameterInMM
                Namespace ns = findNamespace(cad, disk.getId());
                if (ns != null) {
                    NumericParameter diamParam = findNumericParam(ns, "Diameter");
                    if (diamParam == null) {
                        violations.add("BrakeDisk '" + disk.getId()
                                + "': CAD Namespace missing 'Diameter' parameter");
                    } else if (Math.abs(diamParam.getValue() - disk.getDiameterInMM()) > 0.01f) {
                        violations.add(String.format(
                                "BrakeDisk '%s': CAD Diameter=%.1f but M₁ diameterInMM=%d",
                                disk.getId(), diamParam.getValue(), disk.getDiameterInMM()));
                    }
                }
            }

            if (comp instanceof BrakePad pad) {
                // frictionArea == heightInMM * widthInMM
                float expected = pad.getHeightInMM() * pad.getWidthInMM() * 1.0f;
                if (Math.abs(entry.getFrictionArea() - expected) > 0.01f) {
                    violations.add(String.format(
                            "BrakePad '%s': frictionArea=%.2f but expected %.2f (h=%d, w=%d)",
                            pad.getId(), entry.getFrictionArea(), expected,
                            pad.getHeightInMM(), pad.getWidthInMM()));
                }
            }
        }

        return violations;
    }

    // ── Model access helpers ──

    private static Brakesystem getBrakesystemRoot(VirtualModel vsum) {
        var selector = vsum.createSelector(
                ViewTypeFactory.createIdentityMappingViewType("bs-validate"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof Brakesystem)
                .forEach(e -> selector.setSelected(e, true));
        var roots = selector.createView().getRootObjects(Brakesystem.class);
        return roots.iterator().hasNext() ? roots.iterator().next() : null;
    }

    private static CAD_Model getCADRoot(VirtualModel vsum) {
        var selector = vsum.createSelector(
                ViewTypeFactory.createIdentityMappingViewType("cad-validate"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof CAD_Model)
                .forEach(e -> selector.setSelected(e, true));
        var roots = selector.createView().getRootObjects(CAD_Model.class);
        return roots.iterator().hasNext() ? roots.iterator().next() : null;
    }

    private static SafetyAssessment getSafetyRoot(VirtualModel vsum) {
        var selector = vsum.createSelector(
                ViewTypeFactory.createIdentityMappingViewType("safety-validate"));
        selector.getSelectableElements().stream()
                .filter(e -> e instanceof SafetyAssessment)
                .forEach(e -> selector.setSelected(e, true));
        var roots = selector.createView().getRootObjects(SafetyAssessment.class);
        return roots.iterator().hasNext() ? roots.iterator().next() : null;
    }

    private static SafetyEntry findSafetyEntry(SafetyAssessment assessment, String componentId) {
        return assessment.getSafetyEntries().stream()
                .filter(e -> componentId.equals(e.getComponentId()))
                .findFirst().orElse(null);
    }

    private static Namespace findNamespace(CAD_Model cad, String id) {
        return cad.getNamespaces().stream()
                .filter(n -> id.equals(n.getId()))
                .findFirst().orElse(null);
    }

    private static NumericParameter findNumericParam(Namespace ns, String name) {
        return ns.getParameters().stream()
                .filter(p -> p instanceof NumericParameter && name.equals(p.getName()))
                .map(p -> (NumericParameter) p)
                .findFirst().orElse(null);
    }
}
