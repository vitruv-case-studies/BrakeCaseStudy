package tools.vitruv.methodologisttemplate.vsum.SimulinkAndBrakeSystemTests;

import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;


import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.methodologisttemplate.vsum.TestUtil;

public class BrakeDisk2SimulinkTests {
    
	TestUtil util = new TestUtil();
	Iterable<ChangePropagationSpecification> necessaryCPS =
        List.of(
        );

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());
	}

}
