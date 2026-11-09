package tools.vitruv.casestudies.brakesystem.consistency;

/**
 * Lenient conversion of loosely typed parameter values (Simulink parameter strings, AUTOSAR
 * AnySimpleType values) and of units (see {@link MeasurementUnit}). All parse methods return null instead of
 * throwing if the value is missing or cannot be interpreted, so callers can skip the update.
 */
public final class ParameterValueUtility {
    private ParameterValueUtility() {
    }

    public static String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    public static Integer toInteger(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return ((Number) value).intValue();
        }
        if (value instanceof Number number) {
            return roundOrNull(number.doubleValue());
        }
        if (value instanceof String string) {
            String trimmed = string.trim();
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                // fall through to decimal parsing, e.g. "120.0"
            }
            try {
                return roundOrNull(Double.parseDouble(trimmed));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Float toFloat(Object value) {
        if (value instanceof Number number) {
            return finiteOrNull(number.floatValue());
        }
        if (value instanceof String string) {
            try {
                return finiteOrNull(Float.parseFloat(string.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        if (value instanceof String string) {
            String trimmed = string.trim();
            if (trimmed.equalsIgnoreCase("true") || trimmed.equals("1")) {
                return true;
            }
            if (trimmed.equalsIgnoreCase("false") || trimmed.equals("0")) {
                return false;
            }
        }
        return null;
    }

    /**
     * Whether the unit describes a length. An unset unit is interpreted as millimetres, the
     * convention of the brake system model.
     */
    public static boolean isLengthUnit(MeasurementUnit unit) {
        return unit != MeasurementUnit.COUNT && unit != MeasurementUnit.DEG;
    }

    /**
     * Converts a length into whole millimetres, or returns {@code null} if the unit is not a
     * length unit.
     */
    public static Integer toMillimetres(double value, MeasurementUnit unit) {
        if (!isLengthUnit(unit)) {
            return null;
        }
        return roundOrNull(value * millimetresPerUnit(unit));
    }

    /**
     * Converts millimetres into the given length unit.
     */
    public static double fromMillimetres(int millimetres, MeasurementUnit unit) {
        if (!isLengthUnit(unit)) {
            throw new IllegalArgumentException("Not a length unit: " + unit);
        }
        return millimetres / millimetresPerUnit(unit);
    }

    /**
     * Whether the value already represents the given number of whole millimetres, so writing it
     * again would only change the user's representation (e.g. 0.0125 m vs. 0.013 m).
     */
    public static boolean representsMillimetres(double value, MeasurementUnit unit, int millimetres) {
        Integer current = toMillimetres(value, unit);
        return current != null && current == millimetres;
    }

    // Simulink parameters have no unit. By convention they hold lengths in millimetres, like the
    // brake system model, so that the path CAD -> Simulink gives the same result as
    // CAD -> brake system -> Simulink. CAD holds lengths in metres (see CadUtility).

    /** Converts a CAD value into the Simulink convention (lengths in millimetres, angles unchanged). */
    public static double cadToSimulinkValue(double value, MeasurementUnit unit) {
        return isLengthUnit(unit) ? value * millimetresPerUnit(unit) : value;
    }

    /** Converts a Simulink value (lengths in millimetres) into the given CAD unit. */
    public static double simulinkToCadValue(double value, MeasurementUnit unit) {
        return isLengthUnit(unit) ? value / millimetresPerUnit(unit) : value;
    }

    /**
     * Converts a value between two units. Only lengths are converted; if one of the units is no
     * length unit, the value is returned unchanged. An unset unit is interpreted as millimetres.
     */
    public static double convert(double value, MeasurementUnit from, MeasurementUnit to) {
        if (!isLengthUnit(from) || !isLengthUnit(to)) {
            return value;
        }
        return value * millimetresPerUnit(from) / millimetresPerUnit(to);
    }

    /** Parses a unit given by its literal (e.g. "mm") or name (e.g. "MM"), ignoring case. */
    public static MeasurementUnit parseUnit(String text) {
        if (text == null) {
            return null;
        }
        for (MeasurementUnit unit : MeasurementUnit.values()) {
            if (unit.getLiteral().equalsIgnoreCase(text.trim()) || unit.name().equalsIgnoreCase(text.trim())) {
                return unit;
            }
        }
        return null;
    }

    /** Formats a number without a trailing ".0" for whole numbers. */
    public static String formatNumber(float value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }

    /**
     * Formats a number with float precision, so that rounding noise of unit conversions
     * (e.g. 0.03 m = 30.000000000000004 mm) does not show up.
     */
    public static String formatNumber(double value) {
        return formatNumber((float) value);
    }

    /** Whether the textual value already denotes the given number. */
    public static boolean denotesNumber(String text, float value) {
        Float current = toFloat(text);
        return current != null && sameNumber(current, value);
    }

    /** Whether the textual value already denotes the given boolean. */
    public static boolean denotesBoolean(String text, boolean value) {
        Boolean current = toBoolean(text);
        return current != null && current == value;
    }

    /** Whether the textual value already denotes the given number (compared with float precision). */
    public static boolean denotesNumber(String text, double value) {
        return denotesNumber(text, (float) value);
    }

    public static boolean sameNumber(float a, float b) {
        return Math.abs(a - b) <= 1e-6f * Math.max(1f, Math.max(Math.abs(a), Math.abs(b)));
    }

    /** Whether two numbers are equal up to float precision, relative to their magnitude. */
    public static boolean sameNumber(double a, double b) {
        return Math.abs(a - b) <= 1e-6 * Math.max(Math.abs(a), Math.abs(b)) || a == b;
    }

    private static double millimetresPerUnit(MeasurementUnit unit) {
        if (unit == null) {
            return 1;
        }
        return switch (unit) {
            case MM -> 1;
            case CM -> 10;
            case M -> 1000;
            case UM -> 1e-3;
            case NM -> 1e-6;
            case INCH -> 25.4;
            case FT -> 304.8;
            case COUNT, DEG -> throw new IllegalArgumentException("Not a length unit: " + unit);
        };
    }

    private static Integer roundOrNull(double value) {
        if (Double.isNaN(value) || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return null;
        }
        return (int) Math.round(value);
    }

    private static Float finiteOrNull(float value) {
        return Float.isFinite(value) ? value : null;
    }
}
