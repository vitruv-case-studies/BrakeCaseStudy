package tools.vitruv.methodologisttemplate.vsum.BrakeDiskTests;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import edu.kit.ipd.sdq.metamodels.cad.BooleanParameter;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import edu.kit.ipd.sdq.metamodels.cad.StringParameter;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.methodologisttemplate.vsum.TestUtil;

public class BrakeDisk2CadTest {

	// TODO add logging framework
	// private static final Logger logger = org.slf4j.LoggerFactory
	// .getLogger(BrakeDisk2CadTest.class);

	TestUtil util = new TestUtil();

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	@Test
	void brakeDiskInsertionAndPropagationTest(@TempDir Path tempDir) {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// add brake disk with parameters
		CommittableView view = util.getDefaultView(vsum,
				List.of(Brakesystem.class))
				.withChangeRecordingTrait();
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = createDefaultBrakeDisk();
			brakeDisk.setId("brakeDisk1");

			v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
						(View v) -> {
							System.out.println(v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces());
							Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							boolean isOEMNumber = namespace.getParameters().stream()
									.filter(param -> param instanceof StringParameter)
									.anyMatch(param -> param.getName().equals("OEM Number")
											&& ((StringParameter) param).getValue().equals("VW123456"));
							boolean isDiameterInMM = namespace.getParameters().stream()
									.filter(param -> param instanceof NumericParameter)
									.anyMatch(param -> param.getName().equals("Diameter")
											&& ((NumericParameter) param)
													.getValue() == 120.0);
							boolean isCenteringDiameterInMM = namespace.getParameters().stream()
									.filter(param -> param instanceof NumericParameter)
									.anyMatch(param -> param.getName().equals("Centering Diameter")
											&& ((NumericParameter) param).getValue() == 20.0);
							boolean isRimHoleNumber = namespace.getParameters().stream()
									.filter(param -> param instanceof NumericParameter)
									.anyMatch(param -> param.getName().equals("Rim Hole Number")
											&& ((NumericParameter) param).getValue() == 1.0);
							boolean isHoleArrangementNumber = namespace.getParameters().stream()
									.filter(param -> param instanceof NumericParameter)
									.anyMatch(param -> param.getName().equals("Hole Arrangement Number")
											&& ((NumericParameter) param).getValue() == 20.0);
							boolean isBoltHoleCircleInMM = namespace.getParameters().stream()
									.filter(param -> param instanceof NumericParameter)
									.anyMatch(param -> param.getName().equals("Bolt Hole Circle")
											&& ((NumericParameter) param).getValue() == 60.0);
							boolean isBrakeDiskThicknessInMM = namespace.getParameters().stream()
									.filter(param -> param instanceof NumericParameter)
									.anyMatch(param -> param.getName().equals("Brake Disk Thickness")
											&& ((NumericParameter) param).getValue() == 30.0);
							boolean isMinimumThicknessInMM = namespace.getParameters().stream()
									.filter(param -> param instanceof NumericParameter)
									.anyMatch(param -> param.getName().equals("Minimum Thickness")
											&& ((NumericParameter) param).getValue() == 25.0);
							boolean isVentilated = namespace.getParameters().stream()
									.filter(param -> param instanceof BooleanParameter)
									.anyMatch(param -> param.getName().equals("Ventilated")
											&& ((BooleanParameter) param).isValue());

							return isOEMNumber && isDiameterInMM & isCenteringDiameterInMM && isRimHoleNumber
									&& isHoleArrangementNumber && isBoltHoleCircleInMM
									&& isBrakeDiskThicknessInMM && isMinimumThicknessInMM && isVentilated;

						}));

	}

	@Test
	void changeDiameterTest(@TempDir Path tempDir) {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// add brake disk with parameters
		CommittableView view = util.getDefaultView(vsum,
				List.of(Brakesystem.class))
				.withChangeRecordingTrait();
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
			brakeDisk.setId("brakeDisk1");
			brakeDisk.setDiameterInMM(120);
			v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
						(View v) -> {
							Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return namespace.getParameters().stream()
									.filter(param -> param instanceof NumericParameter)
									.anyMatch(param -> param.getName().equals("Diameter")
											&& ((NumericParameter) param).getValue() == 120.0);
						}));

		// change diameter
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).findFirst().orElseThrow();
			brakeDisk.setDiameterInMM(130);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
						(View v) -> {
							Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return namespace.getParameters().stream()
									.filter(param -> param instanceof NumericParameter)
									.anyMatch(param -> param.getName().equals("Diameter")
											&& ((NumericParameter) param).getValue() == 130.0);
						}));
	}

	@Test
	void changeIdTest(@TempDir Path tempDir) {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// add brake disk with parameters
		CommittableView view = util.getDefaultView(vsum,
				List.of(Brakesystem.class))
				.withChangeRecordingTrait();
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = createDefaultBrakeDisk();
			v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
						(View v) -> {
							Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return namespace.getId().equals("brakeDisk1")
									&& namespace.getParameters().stream()
											.anyMatch(param -> param instanceof StringParameter
													&& param.getName().equals("OEM Number")
													&& ((StringParameter) param).getValue().equals("VW123456"));
						}));

		// change id
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).findFirst().orElseThrow();
			brakeDisk.setId("newId");
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
						(View v) -> {
							Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces()
									.stream().filter(ns -> ns.getId().equals("newId")).findFirst()
									.orElseThrow();
							return namespace.getId().equals("newId")
									&& namespace.getParameters().stream()
											.anyMatch(param -> param instanceof StringParameter
													&& param.getName().equals("OEM Number")
													&& ((StringParameter) param).getValue().equals("VW123456"));
						}));
	}

	@Test
	void changeVentilatedTest(@TempDir Path tempDir) {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// add brake disk with parameters
		CommittableView view = util.getDefaultView(vsum,
				List.of(Brakesystem.class))
				.withChangeRecordingTrait();
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = createDefaultBrakeDisk();
			v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
						(View v) -> {
							Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return namespace.getParameters().stream()
									.filter(param -> param instanceof BooleanParameter)
									.anyMatch(param -> param.getName().equals("Ventilated")
											&& ((BooleanParameter) param).isValue());
						}));

		// change ventilated
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).findFirst().orElseThrow();
			brakeDisk.setVentilated(false);
		});

		Assertions.assertTrue(
				assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
						(View v) -> {
							Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return namespace.getParameters().stream()
									.filter(param -> param instanceof BooleanParameter)
									.anyMatch(param -> param.getName().equals("Ventilated")
											&& !((BooleanParameter) param).isValue());
						}));
	}

	@Test
	void changeOEMNumberTest(@TempDir Path tempDir) {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// add brake disk with parameters
		CommittableView view = util.getDefaultView(vsum,
				List.of(Brakesystem.class))
				.withChangeRecordingTrait();
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = createDefaultBrakeDisk();
			v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
						(View v) -> {
							Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return namespace.getParameters().stream()
									.filter(param -> param instanceof StringParameter)
									.anyMatch(param -> param.getName().equals("OEM Number")
											&& ((StringParameter) param).getValue().equals("VW123456"));
						}));

		// change OEM number
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).findFirst().orElseThrow();
			brakeDisk.setOEM_number("VW654321");
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getDefaultView(vsum, List.of(CAD_Model.class)),
						(View v) -> {
							Namespace namespace = v.getRootObjects(CAD_Model.class).iterator().next().getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return namespace.getParameters().stream()
									.filter(param -> param instanceof StringParameter)
									.anyMatch(param -> param.getName().equals("OEM Number")
											&& ((StringParameter) param).getValue().equals("VW654321"));
						}));

	}

	private BrakeDisk createDefaultBrakeDisk() {
		BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
		brakeDisk.setId("brakeDisk1");
		brakeDisk.setOEM_number("VW123456");
		brakeDisk.setDiameterInMM(120);
		brakeDisk.setCenteringDiameterInMM(20);
		brakeDisk.setRimHoleNumber(1);
		brakeDisk.setHoleArrangementNumber(20);
		brakeDisk.setBoltHoleCircleInMM(60);
		brakeDisk.setBrakeDiskThicknessInMM(30);
		brakeDisk.setMinimumThicknessInMM(25);
		brakeDisk.setVentilated(true);
		return brakeDisk;
	}

	private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
		return viewAssertionFunction.apply(view);
	}

}
