package tools.vitruv.casestudies.brakesystem.vsum;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;

import brakesystem.BrakeComponent;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import edu.kit.ipd.sdq.metamodels.cad.BooleanParameter;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import edu.kit.ipd.sdq.metamodels.cad.StringParameter;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import mir.reactions.cad2simulink.Cad2simulinkChangePropagationSpecification;
import simulink.SimulinkModel;
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

    public InternalVirtualModel createDefaultVirtualModel(Path projectPath, Iterable<ChangePropagationSpecification> additionalCPS) {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(userInteraction))
                .withChangePropagationSpecifications(additionalCPS)
                .buildAndInitialize();
        model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
        return model;
    }

    public void registerRootObjects(VirtualModel virtualModel, Path filePath) {
        CommittableView view = getDefaultView(virtualModel,
                List.of(Brakesystem.class))
                .withChangeRecordingTrait();
        modifyView(view, (CommittableView v) -> {
            v.registerRoot(
                    BrakesystemFactory.eINSTANCE.createBrakesystem(),
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
        var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
        selector.getSelectableElements().stream()
                .filter(element -> rootTypes.stream().anyMatch(it -> it.isInstance(element)))
                .forEach(it -> selector.setSelected(it, true));
        return selector.createView();
    }

    public CommittableView getBrakesystemView(VirtualModel vsum) {
        return getDefaultView(vsum, List.of(Brakesystem.class))
            .withChangeRecordingTrait();
    }

    public CommittableView getSimulinkView(VirtualModel vsum) {
        return getDefaultView(vsum, List.of(SimulinkModel.class))
            .withChangeRecordingTrait();
    }

    public CommittableView getCADView(VirtualModel vsum) {
        return getDefaultView(vsum, List.of(CAD_Model.class))
            .withChangeRecordingTrait();
    }

    public CAD_Model getRootOfCADView(View view) {
        return view.getRootObjects(CAD_Model.class).iterator().next();
    }

    public Brakesystem getRootOfBrakesystemView(View view) {
        return view.getRootObjects(Brakesystem.class).iterator().next();
    }

    public BrakeComponent findBrakeComponentWithId(Brakesystem root, String id) {
        return root
            .getBrakeComponents()
            .stream()
            .filter(component -> component.getId().equals(id))
            .findFirst()
            .orElseThrow();
    }


    public Namespace findNamespaceWithId(CAD_Model root, String id) {
        return root
            .getNamespaces()
            .stream()
            .filter(namespace -> namespace.getId().equals(id))
            .findFirst()
            .orElseThrow();
    }

    /**
     * Tests whether there exists a StringParameter for namespace with the given name and value.
     * @param namespace - Namespace
     * @param name - String
     * @param value - String
     * @return boolean
     */
    public static boolean expectStringParameter(Namespace namespace, String name, String value) {
        return namespace.getParameters().stream()
            .filter(param -> param instanceof StringParameter)
            .map(param -> (StringParameter) param)
            .anyMatch(param -> param.getName().equals(name) && param.getValue().equals(value));
    }

    /**
     * Tests whether there exists a NumericParameter for namespace with the given name and value.
     * @param namespace - Namespace
     * @param name - String
     * @param value - float
     * @return boolean
     */
    public static boolean expectNumericParameter(Namespace namespace, String name, float value) {
        return namespace.getParameters().stream()
            .filter(param -> param instanceof NumericParameter)
            .map(param -> (NumericParameter) param)
            .anyMatch(param -> param.getName().equals(name) && param.getValue() == value);
    }

    /**
     * Tests whether there exists a BooleanParameter for namespace with the given name and value.
     * @param namespace - Namespace
     * @param name - String
     * @param value - boolean
     * @return boolean
     */
    public static boolean expectBooleanParameter(Namespace namespace, String name, boolean value) {
        return namespace.getParameters().stream()
            .filter(param -> param instanceof BooleanParameter)
            .map(param -> (BooleanParameter) param)
            .anyMatch(param -> param.getName().equals(name) && param.isValue() == value);
    }
}
