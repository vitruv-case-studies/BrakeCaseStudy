package tools.vitruv.casestudies.brakesystem.consistency;

import bom.BOM;
import bom.BomFactory;
import bom.BoolParameterValue;
import bom.DataType;
import bom.DoubleParameterValue;
import bom.IntegerParameterValue;
import bom.Item;
import bom.ItemType;
import bom.NumericParameterValue;
import bom.ParameterDefinition;
import bom.ParameterValue;
import bom.StringParameterValue;
import edu.kit.ipd.sdq.metamodels.cad.BooleanParameter;
import edu.kit.ipd.sdq.metamodels.cad.Parameter;
import edu.kit.ipd.sdq.metamodels.cad.StringParameter;
import edu.kit.ipd.sdq.metamodels.cad.Unit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Conventions shared by the reactions between the BOM and the other models.
 *
 * <ul>
 *   <li>An {@link ItemType} describes a kind of part and is shared by all items of that kind. For
 *       brake components, the kind is the name of the brake component class.</li>
 *   <li>Parameter values are identified by the name of their definition, like the parameters in CAD
 *       and Simulink.</li>
 *   <li>Numeric values carry a unit given by the literal of the CAD unit (e.g. "mm"). An unset unit
 *       is interpreted as millimetres, like everywhere else in the case study.</li>
 *   <li>An item with quantity n corresponds to n instances in the other models, identified by
 *       "id", "id-2", ..., "id-n".</li>
 * </ul>
 */
public final class BomUtility {
    private static final Set<String> BRAKE_COMPONENT_KINDS = Set.of("ABSSensor", "BrakeCaliper", "BrakeDisk", "BrakeHose", "BrakePad");
    private static final String DEFAULT_KIND = "Part";

    private BomUtility() {
    }

    // Item types

    /** Whether items of the kind are brake components in the brake system model. */
    public static boolean isBrakeComponentKind(String kind) {
        return kind != null && BRAKE_COMPONENT_KINDS.contains(kind);
    }

    /**
     * Whether the item is decided to be no brake component, e.g. a bolt. Items of the type "Part" are
     * not classified yet (see {@link #findOrCreateItemType}), so they are no decision.
     */
    public static boolean isNoBrakeComponent(Item item) {
        return item != null && item.getType() != null && !isBrakeComponentKind(item.getType().getName()) &&
                !DEFAULT_KIND.equals(item.getType().getName());
    }

    /** The shared type of the kind. Parts of unknown kind (null) get the type "Part". */
    public static ItemType findOrCreateItemType(BOM bom, String kind) {
        String name = kind == null ? DEFAULT_KIND : kind;
        for (ItemType type : bom.getItemTypes()) {
            if (name.equals(type.getName())) {
                return type;
            }
        }
        ItemType type = BomFactory.eINSTANCE.createItemType();
        type.setName(name);
        bom.getItemTypes().add(type);
        return type;
    }

    /**
     * Changes the type of an item. The parameter values keep their names, so they are moved to the
     * definitions of the new type. A type no item uses anymore is removed.
     */
    public static void setItemType(Item item, ItemType newType) {
        ItemType oldType = item.getType();
        if (oldType == newType) {
            return;
        }
        for (ParameterValue value : item.getParameterValues()) {
            if (value.getDefinition() != null) {
                value.setDefinition(findOrCreateDefinition(newType, value.getDefinition().getName(), value.getDefinition().getDataType()));
            }
        }
        item.setType(newType);
        if (oldType != null && oldType.eContainer() instanceof BOM bom &&
                bom.getItems().stream().noneMatch(other -> other.getType() == oldType)) {
            EcoreUtil.remove(oldType);
        }
    }

    // Parameter values

    public static ParameterValue findValue(Item item, String name) {
        for (ParameterValue value : item.getParameterValues()) {
            if (value.getDefinition() != null && Objects.equals(value.getDefinition().getName(), name)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Returns the value with the given name. If there is none, a value is created. Its datatype is
     * the one of an existing definition of the item type, otherwise the given one.
     */
    public static ParameterValue findOrCreateValue(Item item, String name, DataType dataType) {
        ParameterValue existing = findValue(item, name);
        if (existing != null) {
            return existing;
        }
        ParameterDefinition definition = findOrCreateDefinition(item.getType(), name, dataType);
        ParameterValue value = switch (definition.getDataType()) {
            case ESTRING -> BomFactory.eINSTANCE.createStringParameterValue();
            case EBOOLEAN -> BomFactory.eINSTANCE.createBoolParameterValue();
            case EINT -> BomFactory.eINSTANCE.createIntegerParameterValue();
            case EDOUBLE -> BomFactory.eINSTANCE.createDoubleParameterValue();
        };
        value.setDefinition(definition);
        item.getParameterValues().add(value);
        return value;
    }

    /** The BOM datatype for a CAD parameter. CAD stores numbers as floats, so they become doubles. */
    public static DataType dataTypeOf(Parameter parameter) {
        if (parameter instanceof StringParameter) {
            return DataType.ESTRING;
        }
        if (parameter instanceof BooleanParameter) {
            return DataType.EBOOLEAN;
        }
        return DataType.EDOUBLE;
    }

    private static ParameterDefinition findOrCreateDefinition(ItemType type, String name, DataType dataType) {
        for (ParameterDefinition definition : type.getParameterDefinitions()) {
            if (Objects.equals(definition.getName(), name)) {
                return definition;
            }
        }
        ParameterDefinition definition = BomFactory.eINSTANCE.createParameterDefinition();
        definition.setName(name);
        definition.setDataType(dataType);
        type.getParameterDefinitions().add(definition);
        return definition;
    }

    // Writing values of the brake system. Values that already represent the attribute are not
    // rewritten, so a propagated change does not overwrite the unit or formatting of the BOM.
    // Attributes that were never set get no value, so defaults are not listed as parameters.

    /** Whether the attribute of the brake component was set (or the item already has a value for it). */
    public static boolean isListed(Item item, String name, EObject component, String featureName) {
        return component.eIsSet(component.eClass().getEStructuralFeature(featureName)) || findValue(item, name) != null;
    }

    public static void putString(Item item, String name, String text) {
        if (findOrCreateValue(item, name, DataType.ESTRING) instanceof StringParameterValue value && !Objects.equals(value.getValue(), text)) {
            value.setValue(text);
        }
    }

    public static void putBoolean(Item item, String name, boolean flag) {
        if (findOrCreateValue(item, name, DataType.EBOOLEAN) instanceof BoolParameterValue value && value.isValue() != flag) {
            value.setValue(flag);
        }
    }

    public static void putLength(Item item, String name, int millimetres) {
        boolean created = findValue(item, name) == null;
        if (findOrCreateValue(item, name, DataType.EINT) instanceof NumericParameterValue value) {
            if (created) {
                value.setUnit(Unit.MM.getLiteral());
            }
            Integer current = lengthInMillimetres(value);
            if (current == null || current != millimetres) {
                if (!ParameterValueUtility.isLengthUnit(unitOf(value))) {
                    value.setUnit(Unit.MM.getLiteral());
                }
                setNumber(value, ParameterValueUtility.convert(millimetres, Unit.MM, unitOf(value)));
            }
        }
    }

    public static void putCount(Item item, String name, int count) {
        boolean created = findValue(item, name) == null;
        if (findOrCreateValue(item, name, DataType.EINT) instanceof NumericParameterValue value) {
            if (created) {
                value.setUnit(Unit.COUNT.getLiteral());
            }
            Integer current = count(value);
            if (current == null || current != count) {
                setNumber(value, count);
            }
        }
    }

    // Reading values for the brake system

    public static String stringValue(ParameterValue value) {
        return value instanceof StringParameterValue string ? string.getValue() : null;
    }

    public static Boolean booleanValue(ParameterValue value) {
        return value instanceof BoolParameterValue bool ? bool.isValue() : null;
    }

    /** The value in whole millimetres, or null if it is no length. */
    public static Integer lengthInMillimetres(ParameterValue value) {
        Double number = numberOf(value);
        if (number == null || !ParameterValueUtility.isLengthUnit(unitOf((NumericParameterValue) value))) {
            return null;
        }
        return (int) Math.round(ParameterValueUtility.convert(number, unitOf((NumericParameterValue) value), Unit.MM));
    }

    /** The value as a whole number, or null if it is no number. */
    public static Integer count(ParameterValue value) {
        Double number = numberOf(value);
        return number == null ? null : (int) Math.round(number);
    }

    // CAD values (unit given by the CAD parameter)

    /** Whether the BOM value already represents the CAD value, so that it need not be written. */
    public static boolean representsCadValue(NumericParameterValue value, float cadValue, Unit cadUnit) {
        Double number = numberOf(value);
        if (number == null) {
            return false;
        }
        double target = ParameterValueUtility.convert(cadValue, cadUnit, unitOf(value));
        return value instanceof IntegerParameterValue ? Math.round(target) == Math.round(number)
                : ParameterValueUtility.sameNumber((float) target, number.floatValue());
    }

    /** Writes the CAD value in the unit of the BOM value. If the units are incompatible, the CAD unit is taken over. */
    public static void setFromCad(NumericParameterValue value, float cadValue, Unit cadUnit) {
        if (representsCadValue(value, cadValue, cadUnit) && ParameterValueUtility.isLengthUnit(unitOf(value)) == ParameterValueUtility.isLengthUnit(cadUnit)) {
            return;
        }
        if (ParameterValueUtility.isLengthUnit(unitOf(value)) != ParameterValueUtility.isLengthUnit(cadUnit)) {
            value.setUnit(cadUnit == null ? null : cadUnit.getLiteral());
        }
        setNumber(value, ParameterValueUtility.convert(cadValue, cadUnit, unitOf(value)));
    }

    /** The BOM value in the given CAD unit. */
    public static float toCadValue(NumericParameterValue value, Unit cadUnit) {
        Double number = numberOf(value);
        return number == null ? 0 : (float) ParameterValueUtility.convert(number, unitOf(value), cadUnit);
    }

    /** The CAD unit for a new CAD parameter of the BOM value. */
    public static Unit cadUnitOf(NumericParameterValue value) {
        Unit unit = unitOf(value);
        return unit == null ? Unit.MM : unit;
    }

    // Simulink values (lengths in millimetres, see ParameterValueUtility)

    /** The text of a Simulink parameter for the BOM value, or null for values of unknown type. */
    public static String toSimulinkText(ParameterValue value) {
        if (value instanceof StringParameterValue string) {
            return string.getValue();
        }
        if (value instanceof BoolParameterValue bool) {
            return Boolean.toString(bool.isValue());
        }
        Double number = numberOf(value);
        return number == null ? null : ParameterValueUtility.formatNumber(simulinkNumber((NumericParameterValue) value));
    }

    /** Whether the Simulink text already denotes the BOM value, so that it need not be written. */
    public static boolean representedBySimulinkText(ParameterValue value, String text) {
        if (value instanceof StringParameterValue string) {
            return Objects.equals(string.getValue(), text);
        }
        if (value instanceof BoolParameterValue bool) {
            return ParameterValueUtility.denotesBoolean(text, bool.isValue());
        }
        Double number = numberOf(value);
        return number != null && ParameterValueUtility.denotesNumber(text, simulinkNumber((NumericParameterValue) value));
    }

    /** Writes a Simulink parameter text into the BOM value. Texts that are no valid value are ignored. */
    public static void setFromSimulink(ParameterValue value, String text) {
        if (representedBySimulinkText(value, text)) {
            return;
        }
        if (value instanceof StringParameterValue string) {
            string.setValue(text);
        } else if (value instanceof BoolParameterValue bool) {
            Boolean flag = ParameterValueUtility.toBoolean(text);
            if (flag != null) {
                bool.setValue(flag);
            }
        } else if (value instanceof NumericParameterValue numeric) {
            Float number = ParameterValueUtility.toFloat(text);
            if (number != null) {
                Unit unit = unitOf(numeric);
                setNumber(numeric, ParameterValueUtility.isLengthUnit(unit) ? ParameterValueUtility.convert(number, Unit.MM, unit) : number);
            }
        }
    }

    private static float simulinkNumber(NumericParameterValue value) {
        Unit unit = unitOf(value);
        double number = numberOf(value);
        return (float) (ParameterValueUtility.isLengthUnit(unit) ? ParameterValueUtility.convert(number, unit, Unit.MM) : number);
    }

    // Quantities

    /** The identifier of the instance with the given index (starting at 0) of an item. */
    public static String instanceId(String itemId, int index) {
        return index == 0 ? itemId : itemId + "-" + (index + 1);
    }

    /** The index of an instance identifier of the item, or -1 if it is not derived from the item. */
    public static int instanceIndex(String itemId, String instanceId) {
        if (itemId == null || instanceId == null) {
            return -1;
        }
        if (instanceId.equals(itemId)) {
            return 0;
        }
        if (instanceId.startsWith(itemId + "-")) {
            try {
                int number = Integer.parseInt(instanceId.substring(itemId.length() + 1));
                return number >= 2 ? number - 1 : -1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /** The identifier of an instance after the item identifier changed, or null if it is not derived from the item. */
    public static String renamedInstanceId(String oldItemId, String newItemId, String instanceId) {
        int index = instanceIndex(oldItemId, instanceId);
        return index < 0 ? null : instanceId(newItemId, index);
    }

    /** The smallest instance identifier of the item that is not used yet. */
    public static String freeInstanceId(String itemId, Collection<String> usedIds) {
        int index = 0;
        while (usedIds.contains(instanceId(itemId, index))) {
            index++;
        }
        return instanceId(itemId, index);
    }

    /**
     * The instances to remove so that the desired number remains. Instances with the highest index
     * are removed first, so all models remove the corresponding instances. Instances whose identifier
     * is not derived from the item are removed before them.
     */
    public static <T> List<T> instancesToRemove(Collection<T> instances, Function<T, String> idOf, String itemId, int desired) {
        List<T> sorted = new ArrayList<>(instances);
        sorted.sort(Comparator.comparingInt((T instance) -> {
            int index = instanceIndex(itemId, idOf.apply(instance));
            return index < 0 ? Integer.MAX_VALUE : index;
        }).reversed());
        return sorted.subList(0, Math.max(0, sorted.size() - Math.max(0, desired)));
    }

    // Numbers

    private static Double numberOf(ParameterValue value) {
        if (value instanceof IntegerParameterValue integer) {
            return (double) integer.getValue();
        }
        if (value instanceof DoubleParameterValue decimal) {
            return decimal.getValue();
        }
        return null;
    }

    private static Unit unitOf(NumericParameterValue value) {
        return ParameterValueUtility.parseUnit(value.getUnit());
    }

    private static void setNumber(NumericParameterValue value, double number) {
        if (value instanceof IntegerParameterValue integer && integer.getValue() != Math.round(number)) {
            integer.setValue((int) Math.round(number));
        } else if (value instanceof DoubleParameterValue decimal && !ParameterValueUtility.sameNumber((float) decimal.getValue(), (float) number)) {
            decimal.setValue(number);
        }
    }
}
