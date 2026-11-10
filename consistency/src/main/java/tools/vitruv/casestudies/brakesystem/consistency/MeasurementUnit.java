package tools.vitruv.casestudies.brakesystem.consistency;

/**
 * Units of numeric parameter values. The BOM stores the unit as a string given by the literal
 * (e.g. "mm"). The CAD model has fixed units instead: lengths in metres ({@link #M}) and angles in
 * degrees ({@link #DEG}). Counts and angles are no lengths and are never converted.
 */
public enum MeasurementUnit {
    MM("mm"),
    CM("cm"),
    NM("nm"),
    UM("um"),
    M("m"),
    INCH("in"),
    FT("ft"),
    COUNT("COUNT"),
    DEG("deg");

    private final String literal;

    MeasurementUnit(String literal) {
        this.literal = literal;
    }

    public String getLiteral() {
        return literal;
    }
}
