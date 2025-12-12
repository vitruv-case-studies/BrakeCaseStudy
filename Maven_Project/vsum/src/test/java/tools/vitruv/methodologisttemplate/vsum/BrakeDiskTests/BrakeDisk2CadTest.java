package tools.vitruv.methodologisttemplate.vsum.BrakeDiskTests;

import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
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
	void brakeDiskDeletionTest(@TempDir Path tempDir) {
		var vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// Add brake disk with parameters
		var brakeView = util.getBrakesystemView(vsum)
			.withChangeRecordingTrait();
		util.modifyView(brakeView, view -> {
			var defaultBrakeDisk = createDefaultBrakeDisk();
			util.getRootOfBrakesystemView(brakeView).getBrakeComponents().add(defaultBrakeDisk);
		});

		// Assert a namespace has been created
		Assertions.assertTrue(assertView(util.getCADView(vsum),
			view -> {
				util.findNamespaceWithId(
					util.getRootOfCADView(view),
					"brakeDisk1");
				return true;
			}
		));

		// Remove the brake disk
		util.modifyView(brakeView, view -> {
			util.getRootOfBrakesystemView(brakeView).getBrakeComponents().clear();
		});

		// Assert no namespace exists.
		Assertions.assertThrows(NoSuchElementException.class, () -> {
			var cadView = util.getCADView(vsum);
			var model = util.getRootOfCADView(cadView);
			util.findNamespaceWithId(model, "brakeDisk1");
		});
	}

	@Test
	void brakeDiskInsertionAndPropagationTest(@TempDir Path tempDir) {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// add brake disk with parameters
		CommittableView view = util.getBrakesystemView(vsum)
				.withChangeRecordingTrait();
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = createDefaultBrakeDisk();
			brakeDisk.setId("brakeDisk1");

			util.getRootOfBrakesystemView(v).getBrakeComponents().add(brakeDisk);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getCADView(vsum),
						(View v) -> {
							System.out.println(util.getRootOfCADView(v).getNamespaces());
							Namespace namespace = util.getRootOfCADView(v).getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							boolean isOEMNumber = TestUtil.expectStringParameter(namespace, "OEM Number", "VW123456");
							boolean isDiameterInMM = TestUtil.expectNumericParameter(namespace, "Diameter", 120.0f);
							boolean isCenteringDiameterInMM = TestUtil.expectNumericParameter(namespace, "Centering Diameter", 20.0f);
							boolean isRimHoleNumber = TestUtil.expectNumericParameter(namespace, "Rim Hole Number", 1.0f); 
							boolean isHoleArrangementNumber = TestUtil.expectNumericParameter(namespace, "Hole Arrangement Number", 20.0f);
							boolean isBoltHoleCircleInMM = TestUtil.expectNumericParameter(namespace, "Bolt Hole Circle", 60.0f);
							boolean isBrakeDiskThicknessInMM = TestUtil.expectNumericParameter(namespace, "Brake Disk Thickness", 30);
							boolean isMinimumThicknessInMM = TestUtil.expectNumericParameter(namespace, "Minimum Thickness", 25);
							boolean isVentilated = TestUtil.expectBooleanParameter(namespace, "Ventilated", true);

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
		CommittableView view = util.getBrakesystemView(vsum)
				.withChangeRecordingTrait();
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
			brakeDisk.setId("brakeDisk1");
			brakeDisk.setDiameterInMM(120);
			util.getRootOfBrakesystemView(v).getBrakeComponents().add(brakeDisk);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getCADView(vsum),
						(View v) -> {
							Namespace namespace = util.getRootOfCADView(v).getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return TestUtil.expectNumericParameter(namespace, "Diameter", 120);
						}));

		// change diameter
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = util.getRootOfBrakesystemView(v).getBrakeComponents()
					.stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).findFirst().orElseThrow();
			brakeDisk.setDiameterInMM(130);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getCADView(vsum),
						(View v) -> {
							Namespace namespace = util.getRootOfCADView(v).getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return TestUtil.expectNumericParameter(namespace, "Diameter", 130);
						}));
	}

	@Test
	void changeIdTest(@TempDir Path tempDir) {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// add brake disk with parameters
		CommittableView view = util.getBrakesystemView(vsum)
				.withChangeRecordingTrait();
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = createDefaultBrakeDisk();
			util.getRootOfBrakesystemView(v).getBrakeComponents().add(brakeDisk);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getCADView(vsum),
						(View v) -> {
							Namespace namespace = util.getRootOfCADView(v).getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return namespace.getId().equals("brakeDisk1")
									&& TestUtil.expectStringParameter(namespace, "OEM Number", "VW123456");
						}));

		// change id
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = util.getRootOfBrakesystemView(v).getBrakeComponents()
					.stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).findFirst().orElseThrow();
			brakeDisk.setId("newId");
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getCADView(vsum),
						(View v) -> {
							Namespace namespace = util.getRootOfCADView(v).getNamespaces()
									.stream().filter(ns -> ns.getId().equals("newId")).findFirst()
									.orElseThrow();
							return TestUtil.expectStringParameter(namespace, "OEM Number", "VW123456");
						}));
	}

	@Test
	void changeVentilatedTest(@TempDir Path tempDir) {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// add brake disk with parameters
		CommittableView view = util.getBrakesystemView(vsum)
				.withChangeRecordingTrait();
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = createDefaultBrakeDisk();
			util.getRootOfBrakesystemView(v).getBrakeComponents().add(brakeDisk);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getCADView(vsum),
						(View v) -> {
							Namespace namespace = util.getRootOfCADView(v).getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return TestUtil.expectBooleanParameter(namespace, "Ventilated", true);
						}));

		// change ventilated
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = util.getRootOfBrakesystemView(v).getBrakeComponents()
					.stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).findFirst().orElseThrow();
			brakeDisk.setVentilated(false);
		});

		Assertions.assertTrue(
				assertView(util.getCADView(vsum),
						(View v) -> {
							Namespace namespace = util.getRootOfCADView(v).getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return TestUtil.expectBooleanParameter(namespace, "Ventilated", false);
						}));
	}

	@Test
	void changeOEMNumberTest(@TempDir Path tempDir) {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir);
		util.registerRootObjects(vsum, tempDir);

		// add brake disk with parameters
		CommittableView view = util.getBrakesystemView(vsum)
				.withChangeRecordingTrait();
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = createDefaultBrakeDisk();
			util.getRootOfBrakesystemView(v).getBrakeComponents().add(brakeDisk);
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getCADView(vsum),
						(View v) -> {
							Namespace namespace = util.getRootOfCADView(v).getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return TestUtil.expectStringParameter(namespace, "OEM Number", "VW123456");
						}));

		// change OEM number
		util.modifyView(view, (CommittableView v) -> {
			BrakeDisk brakeDisk = util.getRootOfBrakesystemView(v).getBrakeComponents()
					.stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).findFirst().orElseThrow();
			brakeDisk.setOEM_number("VW654321");
		});

		// assert that namespace with parameters as above has been created
		Assertions.assertTrue(
				assertView(util.getCADView(vsum),
						(View v) -> {
							Namespace namespace = util.getRootOfCADView(v).getNamespaces()
									.stream().filter(ns -> ns.getId().equals("brakeDisk1")).findFirst()
									.orElseThrow();
							return TestUtil.expectStringParameter(namespace, "OEM Number", "VW654321");
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
