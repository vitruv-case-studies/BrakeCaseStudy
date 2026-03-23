package tools.vitruv.casestudies.brakesystem.vsum.BrakeDiskTests;

import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import brakesystem.BrakeCaliper;
import brakesystem.BrakeComponent;
import brakesystem.BrakeDisk;
import brakesystem.BrakeHose;
import brakesystem.BrakePad;
import brakesystem.BrakesystemFactory;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

public class BrakeDisk2CadTest {

	// TODO add logging framework
	// private static final Logger logger = org.slf4j.LoggerFactory
	// .getLogger(BrakeDisk2CadTest.class);

	TestUtil util = new TestUtil();
	Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Cad2brakesystemChangePropagationSpecification(),new Brakesystem2cadChangePropagationSpecification());

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	/**
	 * Provides a Stream of BrakeComponents for {@link #brakeComponentDeleteTest(BrakeComponent, Path)}.
	 * Note that tempDir is resolved through the {@link TempDir} annotation
	 * 
	 * @return {@link Stream}
	 */
	public static Stream<Arguments> provideBrakeComponents() {
		return Stream.of(
			createDefaultBrakeDisk(),
			createDefaultBrakeCaliper(),
			createDefaultBrakeHose(),
			createDefaultBrakePad()
		).map(Arguments::of);
	}

	/**
	 * Tests that:
	 * <ol>
	 * 	<li>when a brake component is inserted, a corresponding namespace is created.</li>
	 * 	<li>when a brake component is removed, the corresponding namespace is deleted.</li>
	 * </ol>
	 * 
	 * @param brakeComponent - {@link BrakeComponent}
	 * @param tempDir - {@link Path}
	 * @see {@link #provideBrakeComponents()}
	 */
	@ParameterizedTest(name = "Testing if deleting a {0} also deletes its corresponding Namespace")
	@MethodSource("provideBrakeComponents")
	void brakeComponentDeleteTest(BrakeComponent brakeComponent, @TempDir Path tempDir) throws Exception {
		var vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
		util.registerRootObjects(vsum, tempDir);

		// Add brake component 
		var brakeView = util.getBrakesystemView(vsum)
			.withChangeRecordingTrait();
		util.modifyView(brakeView, view -> {;
			util.getRootOfBrakesystemView(brakeView).getBrakeComponents().add(brakeComponent);
		});

		// Assert a namespace has been created
		Assertions.assertTrue(assertView(util.getCADView(vsum),
			view -> {
				util.findNamespaceWithId(
					util.getRootOfCADView(view),
					brakeComponent.getId());
				return true;
			}
		));

		// Remove the component
		util.modifyView(brakeView, view -> {
			util.getRootOfBrakesystemView(brakeView).getBrakeComponents().clear();
		});

		// Assert no namespace exists for the component, i.e. with the same ID
		Assertions.assertThrows(NoSuchElementException.class, () -> {
			var cadView = util.getCADView(vsum);
			var model = util.getRootOfCADView(cadView);
			util.findNamespaceWithId(model, brakeComponent.getId());
		});
	}

	@Test
	void brakeDiskInsertionAndPropagationTest(@TempDir Path tempDir) throws Exception {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
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
	void changeDiameterTest(@TempDir Path tempDir) throws Exception {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
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
	void changeIdTest(@TempDir Path tempDir) throws Exception {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
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
	void changeVentilatedTest(@TempDir Path tempDir) throws Exception {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
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
	void changeOEMNumberTest(@TempDir Path tempDir) throws Exception {
		VirtualModel vsum = util.createDefaultVirtualModel(tempDir,necessaryCPS);
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

	private static BrakeDisk createDefaultBrakeDisk() {
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

	private static BrakeCaliper createDefaultBrakeCaliper() {
		var brakeCaliper = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
		brakeCaliper.setId("brakeCaliper1");
		brakeCaliper.setOEM_number("BRMT420");
		brakeCaliper.setBrakeDiskThickness(20);
		brakeCaliper.setPistonDiameterInMM(10);
		brakeCaliper.setFittingPosition("left");
		brakeCaliper.setSpecificationType("Single Brake Caliper");
		return brakeCaliper; 
	}

	private static BrakeHose createDefaultBrakeHose() {
		var brakeHose = BrakesystemFactory.eINSTANCE.createBrakeHose();
		brakeHose.setId("brakeHose1");
		brakeHose.setOEM_number("BH90SS");
		brakeHose.setLengthInMM(1200);
		brakeHose.setThreadSize1("5mm");
		brakeHose.setThreadSize2("4mm");
		brakeHose.setFittingPosition("left");
		return brakeHose;
	}

	private static BrakePad createDefaultBrakePad() {
		var brakePad = BrakesystemFactory.eINSTANCE.createBrakePad();
		brakePad.setId("brakePadL");
		brakePad.setOEM_number("D02SRX");
		brakePad.setSpecificationType("Brake Pad for the Left Side");
		brakePad.setThicknessInMM(12);
		brakePad.setWidthInMM(50);
		brakePad.setHeightInMM(20);
		brakePad.setWearWarning(false);
		return brakePad;
	}

	private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
		return viewAssertionFunction.apply(view);
	}

}
