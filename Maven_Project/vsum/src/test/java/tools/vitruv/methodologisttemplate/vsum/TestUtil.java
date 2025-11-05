package tools.vitruv.methodologisttemplate.vsum;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;

import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

public class TestUtil {

    public TestUserInteraction userInteraction = new TestUserInteraction();

    public InternalVirtualModel createDefaultVirtualModel(Path projectPath) {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(userInteraction))
                .withChangePropagationSpecification(new Brakesystem2cadChangePropagationSpecification())
                .withChangePropagationSpecification(new Cad2brakesystemChangePropagationSpecification())
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

}
