package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import brakesystem.ABSSensor;
import brakesystem.BrakeCaliper;
import brakesystem.BrakeDisk;
import brakesystem.BrakePad;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Atomic model manipulation operations for scenario generation.
 * Each action knows which component type it applies to and whether it triggers
 * cross-model reactions (M1->M2+M3) or only M1->M2 propagation.
 */
public enum ModelAction {

    // ── Reaction-triggering: propagate to M2 AND M3 ──
    CHANGE_DISK_DIAMETER(BrakeDisk.class, Category.REACTION_TRIGGERING),
    CHANGE_DISK_THICKNESS(BrakeDisk.class, Category.REACTION_TRIGGERING),
    CHANGE_PAD_HEIGHT(BrakePad.class, Category.REACTION_TRIGGERING),
    CHANGE_PAD_WIDTH(BrakePad.class, Category.REACTION_TRIGGERING),
    // Note: CHANGE_COMPONENT_ID and CHANGE_NAMESPACE_ID are excluded from generation
    // because ID renames cause cascading changes that invalidate the component pool.
    // They are covered by hand-crafted scenarios S2 and S7.

    // ── Non-reaction-triggering: propagate to M2 only (not M3) ──
    CHANGE_DISK_CENTERING_DIAMETER(BrakeDisk.class, Category.NON_TRIGGERING),
    CHANGE_DISK_RIM_HOLE_NUMBER(BrakeDisk.class, Category.NON_TRIGGERING),
    CHANGE_CALIPER_PISTON_DIAMETER(BrakeCaliper.class, Category.NON_TRIGGERING),
    CHANGE_SENSOR_LENGTH(ABSSensor.class, Category.NON_TRIGGERING),
    CHANGE_SENSOR_PINS(ABSSensor.class, Category.NON_TRIGGERING),

    // ── Additive: always non-conflicting for distinct IDs ──
    ADD_BRAKE_DISK(BrakeDisk.class, Category.ADDITIVE),
    ADD_BRAKE_PAD(BrakePad.class, Category.ADDITIVE),
    ADD_ABS_SENSOR(ABSSensor.class, Category.ADDITIVE),
    ADD_BRAKE_CALIPER(BrakeCaliper.class, Category.ADDITIVE);

    public enum Category {
        REACTION_TRIGGERING,
        NON_TRIGGERING,
        ADDITIVE
    }

    private final Class<?> componentType;
    private final Category category;

    ModelAction(Class<?> componentType, Category category) {
        this.componentType = componentType;
        this.category = category;
    }

    public Class<?> getComponentType() {
        return componentType;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isReactionTriggering() {
        return category == Category.REACTION_TRIGGERING;
    }

    public boolean isAdditive() {
        return category == Category.ADDITIVE;
    }

    /**
     * Returns modify actions applicable to the given component type and category.
     */
    public static List<ModelAction> modifyActionsFor(Class<?> componentType, boolean reactionTriggering) {
        Category target = reactionTriggering ? Category.REACTION_TRIGGERING : Category.NON_TRIGGERING;
        return Arrays.stream(values())
                .filter(a -> a.category == target)
                .filter(a -> a.componentType == null || a.componentType.isAssignableFrom(componentType))
                .collect(Collectors.toList());
    }

    /**
     * Returns all modify actions (reaction-triggering or not) applicable to the given component type.
     */
    public static List<ModelAction> allModifyActionsFor(Class<?> componentType) {
        return Arrays.stream(values())
                .filter(a -> !a.isAdditive())
                .filter(a -> a.componentType == null || a.componentType.isAssignableFrom(componentType))
                .collect(Collectors.toList());
    }
}
