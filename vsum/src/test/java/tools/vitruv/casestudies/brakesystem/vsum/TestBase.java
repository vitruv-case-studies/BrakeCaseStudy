package tools.vitruv.casestudies.brakesystem.vsum;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.View;

import java.util.List;
import java.util.function.Function;

public abstract class TestBase {
    protected TestUtil util = new TestUtil();
    protected List<ChangePropagationSpecification> necessaryCPS = createCPS();

    protected abstract List<ChangePropagationSpecification> createCPS();

    @BeforeAll public static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

    protected boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
        return viewAssertionFunction.apply(view);
    }
}
