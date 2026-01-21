package tools.vitruv.casestudies.brakesystem.vsum.SimulinkAndBrakeSystemTests;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;


import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;

abstract class BrakeDiskAndSimulinkTests {
    
	TestUtil util = new TestUtil();
	Iterable<ChangePropagationSpecification> necessaryCPS =
        List.of(
            // TODO Insert Simulink <-> BrakeSystemCPS
        );

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());
	}

    protected boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
        return viewAssertionFunction.apply(view);
    }

	protected VirtualModel createVirtualModel(Path tempDir) {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
        util.registerRootObjects(vsum, tempDir);
		return vsum;
	}
}
