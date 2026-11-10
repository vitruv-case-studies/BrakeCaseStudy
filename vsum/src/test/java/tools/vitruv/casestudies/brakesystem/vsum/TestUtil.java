package tools.vitruv.casestudies.brakesystem.vsum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import lombok.SneakyThrows;
import org.eclipse.emf.common.util.URI;

import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import edu.kit.ipd.sdq.metamodels.cad.Assembly;
import edu.kit.ipd.sdq.metamodels.cad.CADDocument;
import edu.kit.ipd.sdq.metamodels.cad.Constraint;
import edu.kit.ipd.sdq.metamodels.cad.ConstraintType;
import simulink.Block;
import tools.vitruv.casestudies.brakesystem.consistency.CadUtility;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

public class TestUtil {

    public TestUserInteraction userInteraction = new TestUserInteraction();

    public InternalVirtualModel createDefaultVirtualModel(Path projectPath, Iterable<ChangePropagationSpecification> additionalCPS) throws
            IOException {
        InternalVirtualModel
                model =
                new VirtualModelBuilder().withStorageFolder(projectPath)
                        .withUserInteractorForResultProvider(new TestUserInteraction.ResultProvider(
                                userInteraction))
                        .withChangePropagationSpecifications(additionalCPS)
                        .buildAndInitialize();
        model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
        return model;
    }

    public void registerRootObjects(VirtualModel virtualModel, Path filePath) {
        CommittableView
                view =
                getDefaultView(virtualModel, List.of(Brakesystem.class)).withChangeRecordingTrait();
        modifyView(view, (CommittableView v) -> {
            v.registerRoot(BrakesystemFactory.eINSTANCE.createBrakesystem(),
                    URI.createFileURI(filePath.toString() + "/brakesystem.model"));
        });

    }

    public void modifyView(CommittableView view, Consumer<CommittableView> modificationFunction) {
        modificationFunction.accept(view);
        view.commitChanges();
    }

    // See https://github.com/vitruv-tools/Vitruv/issues/717 for more information
    // about the rootTypes
    public View getDefaultView(VirtualModel vsum, Collection<Class<?>> rootTypes) {
        var
                selector =
                vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
        selector.getSelectableElements()
                .stream()
                .filter(element -> rootTypes.stream().anyMatch(it -> it.isInstance(element)))
                .forEach(it -> selector.setSelected(it, true));
        return selector.createView();
    }

    // CAD: parts are Assemblies, dimensions are named constraints in metres (see CadUtility)

    /** A new CAD part with the given name, used as label as well. */
    public static Assembly createPart(String name) {
        return CadUtility.createPart(name, name);
    }

    /** Adds a named length (in metres) to the part, as a user would in a sketch of the part. */
    public static Constraint addLength(Assembly part, String name, double metres) {
        return CadUtility.createParameter(part, name, CadUtility.defaultTypeFor(name), metres);
    }

    /** Adds a named dimension of the given type to the part. */
    public static Constraint addDimension(Assembly part, String name, ConstraintType type, double value) {
        return CadUtility.createParameter(part, name, type, value);
    }

    /** The part with the given name anywhere in the document, or null. */
    public static Assembly findPart(CADDocument document, String name) {
        return CadUtility.allParts(document).stream().filter(part -> name.equals(part.getName())).findFirst().orElse(null);
    }

    /** Whether the part has a dimension with the given name and value (in metres or degrees). */
    public static boolean expectDimension(Assembly part, String name, double value) {
        Constraint dimension = CadUtility.findParameter(part, name);
        return dimension != null && Math.abs(dimension.getValue() - value) < 1e-9;
    }

    public static boolean expectParameter(Block block, String name, String type, Object value) {
        return block.getParameters()
                .stream()
                .anyMatch(parameter -> parameter.getName().equals(name) &&
                        parameter.getType().equals(type) &&
                        parameter.getValue().equals(value.toString()));
    }
}
