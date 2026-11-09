package tools.vitruv.casestudies.brakesystem.consistency;

import edu.kit.ipd.sdq.metamodels.cad.Assembly;
import edu.kit.ipd.sdq.metamodels.cad.CADDocument;
import edu.kit.ipd.sdq.metamodels.cad.CadFactory;
import edu.kit.ipd.sdq.metamodels.cad.Circle;
import edu.kit.ipd.sdq.metamodels.cad.Constraint;
import edu.kit.ipd.sdq.metamodels.cad.ConstraintType;
import edu.kit.ipd.sdq.metamodels.cad.LineSegment;
import edu.kit.ipd.sdq.metamodels.cad.PointPos;
import edu.kit.ipd.sdq.metamodels.cad.Sketch;
import edu.kit.ipd.sdq.metamodels.cad.Vector2;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

/**
 * Conventions shared by the reactions between the CAD model and the other models.
 *
 * <ul>
 *   <li>A physical part (brake component, instance of a BOM item, Simulink block of a physical
 *       part) is an {@link Assembly}. Its geometry (sketches, solids) lives in its children and is
 *       not part of the overlap. {@code Object.name} is the stable identifier of the part (brake
 *       component id, BOM item instance id), {@code Object.label} its user-visible name (Simulink
 *       block name).</li>
 *   <li>Parts may be nested: an assembly contained in another assembly is a sub-part, e.g. the
 *       brake pads of a caliper or the blocks of a Simulink subsystem.</li>
 *   <li>A design parameter of a part is a named sketch {@link Constraint} of type Distance, Radius,
 *       Diameter or Angle in a sketch that is a direct child of the part. Its name is the parameter
 *       name used in the BOM and in Simulink, its value is the parameter value. Lengths are in
 *       metres, angles in degrees. Parameters created from other models are put into the sketch
 *       labelled "Parameters" and constrain a construction circle (Diameter, Radius) or a
 *       construction line (Distance, Angle).</li>
 *   <li>Only dimensions are part of the overlap. Texts, flags and counts (see {@link #isCount})
 *       have no counterpart in CAD.</li>
 * </ul>
 */
public final class CadUtility {
    /** Label of the sketch that holds the parameters created from other models. */
    public static final String PARAMETER_SKETCH_LABEL = "Parameters";
    private static final String PARAMETER_SKETCH_SUFFIX = "_Parameters";

    /** Numeric parameters of the case study that count something and thus have no CAD dimension. */
    private static final Set<String> COUNT_PARAMETERS = Set.of("Number of Pins", "Rim Hole Number", "Hole Arrangement Number");

    private CadUtility() {
    }

    // Parts

    /** The document that (transitively) contains the element, or null. */
    public static CADDocument documentOf(EObject element) {
        EObject current = element;
        while (current != null && !(current instanceof CADDocument)) {
            current = current.eContainer();
        }
        return (CADDocument) current;
    }

    /** The part that directly contains the part, or null for a top-level part. */
    public static Assembly parentPart(Assembly part) {
        return part.eContainer() instanceof Assembly parent ? parent : null;
    }

    /** All parts of the document, including nested ones. */
    public static List<Assembly> allParts(CADDocument document) {
        List<Assembly> parts = new ArrayList<>();
        collectParts(document.getObjects(), parts);
        return parts;
    }

    private static void collectParts(List<edu.kit.ipd.sdq.metamodels.cad.Object> objects, List<Assembly> parts) {
        for (edu.kit.ipd.sdq.metamodels.cad.Object object : objects) {
            if (object instanceof Assembly assembly) {
                parts.add(assembly);
                collectParts(assembly.getChildren(), parts);
            }
        }
    }

    public static Assembly createPart(String name, String label) {
        Assembly part = CadFactory.eINSTANCE.createAssembly();
        part.setName(name);
        part.setLabel(label);
        return part;
    }

    /**
     * Places the part into the given parent part, or at the top level of the document if the parent
     * is null. Does nothing if the part is already there, so mirrored moves terminate.
     */
    public static void placePart(Assembly part, Assembly parent, CADDocument document) {
        EList<edu.kit.ipd.sdq.metamodels.cad.Object> target = parent != null ? parent.getChildren() : document.getObjects();
        if (part.eContainer() == (parent != null ? parent : document) && target.contains(part)) {
            return;
        }
        target.add(part);
    }

    /** Renames the part and the parameter sketch whose name is derived from it. */
    public static void renamePart(Assembly part, String name) {
        if (Objects.equals(part.getName(), name)) {
            return;
        }
        Sketch sketch = findParameterSketch(part);
        if (sketch != null && Objects.equals(sketch.getName(), parameterSketchName(part.getName()))) {
            sketch.setName(parameterSketchName(name));
        }
        part.setName(name);
    }

    // Parameters

    /** The part whose parameter the constraint is, or null if it is no parameter. */
    public static Assembly partOf(Constraint constraint) {
        if (constraint.eContainer() instanceof Sketch sketch && sketch.eContainer() instanceof Assembly part) {
            return part;
        }
        return null;
    }

    /** Whether the constraint is a design parameter of a part (see class comment). */
    public static boolean isParameter(Constraint constraint) {
        return constraint.getName() != null && isDimension(constraint.getType()) && partOf(constraint) != null;
    }

    private static boolean isDimension(ConstraintType type) {
        return type == ConstraintType.DISTANCE || type == ConstraintType.RADIUS || type == ConstraintType.DIAMETER || type == ConstraintType.ANGLE;
    }

    /** The parameters of the part, i.e. the named dimensions of its sketches. */
    public static List<Constraint> parameters(Assembly part) {
        List<Constraint> parameters = new ArrayList<>();
        for (edu.kit.ipd.sdq.metamodels.cad.Object child : part.getChildren()) {
            if (child instanceof Sketch sketch) {
                for (Constraint constraint : sketch.getConstraints()) {
                    if (isParameter(constraint)) {
                        parameters.add(constraint);
                    }
                }
            }
        }
        return parameters;
    }

    public static Constraint findParameter(Assembly part, String name) {
        for (Constraint constraint : parameters(part)) {
            if (Objects.equals(constraint.getName(), name)) {
                return constraint;
            }
        }
        return null;
    }

    /** Whether a numeric parameter of the given name counts something and thus is no CAD dimension. */
    public static boolean isCount(String name) {
        return name != null && COUNT_PARAMETERS.contains(name);
    }

    /** The unit of the value of the constraint: degrees for angles, metres otherwise. */
    public static MeasurementUnit unitOf(Constraint constraint) {
        return constraint.getType() == ConstraintType.ANGLE ? MeasurementUnit.DEG : MeasurementUnit.M;
    }

    /** The constraint type for a new parameter: diameters (incl. bolt hole circles) or distances. */
    public static ConstraintType defaultTypeFor(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return lower.contains("diameter") || lower.contains("circle") ? ConstraintType.DIAMETER : ConstraintType.DISTANCE;
    }

    /** The value of a length parameter in whole millimetres, or null if it is no length. */
    public static Integer lengthInMillimetres(Constraint constraint) {
        return ParameterValueUtility.toMillimetres(constraint.getValue(), unitOf(constraint));
    }

    /**
     * Writes a length given in millimetres into the parameter of the part, creating the parameter if
     * necessary. A value that already represents the length is not rewritten; an angle of that name
     * becomes a length.
     */
    public static Constraint putLength(Assembly part, String name, int millimetres) {
        double metres = ParameterValueUtility.fromMillimetres(millimetres, MeasurementUnit.M);
        Constraint constraint = findParameter(part, name);
        if (constraint == null) {
            return createParameter(part, name, defaultTypeFor(name), metres);
        }
        if (unitOf(constraint) != MeasurementUnit.M) {
            constraint.setType(defaultTypeFor(name));
            setValue(constraint, metres);
        } else if (!ParameterValueUtility.representsMillimetres(constraint.getValue(), MeasurementUnit.M, millimetres)) {
            setValue(constraint, metres);
        }
        return constraint;
    }

    /** Creates a named driving constraint with construction geometry in the parameter sketch of the part. */
    public static Constraint createParameter(Assembly part, String name, ConstraintType type, double value) {
        Sketch sketch = findOrCreateParameterSketch(part);
        Constraint constraint = CadFactory.eINSTANCE.createConstraint();
        constraint.setName(name);
        constraint.setType(type);
        constraint.setValue(value);
        constraint.setFirstPos(PointPos.NONE);
        if (type == ConstraintType.DIAMETER || type == ConstraintType.RADIUS) {
            Circle circle = CadFactory.eINSTANCE.createCircle();
            circle.setConstruction(true);
            circle.setCenter(vector(0, 0));
            circle.setRadius(type == ConstraintType.DIAMETER ? value / 2 : value);
            sketch.getGeometries().add(circle);
            constraint.setFirst(circle);
        } else {
            LineSegment line = CadFactory.eINSTANCE.createLineSegment();
            line.setConstruction(true);
            line.setStart(vector(0, 0));
            line.setEnd(type == ConstraintType.ANGLE ? vector(Math.cos(Math.toRadians(value)), Math.sin(Math.toRadians(value))) : vector(value, 0));
            sketch.getGeometries().add(line);
            constraint.setFirst(line);
        }
        sketch.getConstraints().add(constraint);
        return constraint;
    }

    /**
     * Sets the value of the constraint and adapts the constrained geometry, as the sketch solver
     * would. Geometry of other shapes than created by {@link #createParameter} is left unchanged.
     */
    public static void setValue(Constraint constraint, double value) {
        if (!ParameterValueUtility.sameNumber(constraint.getValue(), value)) {
            constraint.setValue(value);
        }
        if (constraint.getSecond() != null || !constraint.isDriving()) {
            return;
        }
        if (constraint.getFirst() instanceof Circle circle) {
            double radius = constraint.getType() == ConstraintType.DIAMETER ? value / 2
                    : constraint.getType() == ConstraintType.RADIUS ? value : circle.getRadius();
            if (circle.getRadius() != radius) {
                circle.setRadius(radius);
            }
        } else if (constraint.getFirst() instanceof LineSegment line && line.getStart() != null && line.getEnd() != null) {
            double dx = line.getEnd().getX() - line.getStart().getX();
            double dy = line.getEnd().getY() - line.getStart().getY();
            double length = Math.hypot(dx, dy);
            double x;
            double y;
            if (constraint.getType() == ConstraintType.DISTANCE) {
                x = length == 0 ? value : dx / length * value;
                y = length == 0 ? 0 : dy / length * value;
            } else if (constraint.getType() == ConstraintType.ANGLE) {
                double keep = length == 0 ? 1 : length;
                x = Math.cos(Math.toRadians(value)) * keep;
                y = Math.sin(Math.toRadians(value)) * keep;
            } else {
                return;
            }
            if (dx != x || dy != y) {
                line.setEnd(vector(line.getStart().getX() + x, line.getStart().getY() + y));
            }
        }
    }

    /** Removes the parameter together with its construction geometry if nothing else uses it. */
    public static void removeParameter(Constraint constraint) {
        if (!(constraint.eContainer() instanceof Sketch sketch)) {
            return;
        }
        var first = constraint.getFirst();
        sketch.getConstraints().remove(constraint);
        if (first != null && first.isConstruction() && sketch.getGeometries().contains(first) &&
                sketch.getConstraints().stream().noneMatch(other -> other.getFirst() == first || other.getSecond() == first)) {
            sketch.getGeometries().remove(first);
        }
    }

    public static Sketch findParameterSketch(Assembly part) {
        for (edu.kit.ipd.sdq.metamodels.cad.Object child : part.getChildren()) {
            if (child instanceof Sketch sketch && PARAMETER_SKETCH_LABEL.equals(sketch.getLabel())) {
                return sketch;
            }
        }
        return null;
    }

    private static Sketch findOrCreateParameterSketch(Assembly part) {
        Sketch sketch = findParameterSketch(part);
        if (sketch == null) {
            sketch = CadFactory.eINSTANCE.createSketch();
            sketch.setName(parameterSketchName(part.getName()));
            sketch.setLabel(PARAMETER_SKETCH_LABEL);
            sketch.setVisible(false);
            part.getChildren().add(sketch);
        }
        return sketch;
    }

    private static String parameterSketchName(String partName) {
        return partName + PARAMETER_SKETCH_SUFFIX;
    }

    private static Vector2 vector(double x, double y) {
        Vector2 vector = CadFactory.eINSTANCE.createVector2();
        vector.setX(x);
        vector.setY(y);
        return vector;
    }
}
