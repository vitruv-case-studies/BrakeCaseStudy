package tools.vitruv.casestudies.brakesystem.vsum;

import brakesystem.ABSSensor;
import brakesystem.BrakeCaliper;
import brakesystem.BrakeDisk;
import brakesystem.BrakeHose;
import brakesystem.BrakePad;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import mir.reactions.autosar2brakesystem.Autosar2brakesystemChangePropagationSpecification;
import mir.reactions.brakesystem2autosar.Brakesystem2autosarChangePropagationSpecification;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.brakesystem2simulink.Brakesystem2simulinkChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import mir.reactions.simulink2brakesystem.Simulink2brakesystemChangePropagationSpecification;
import org.eclipse.emf.common.util.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.change.atomic.uuid.Uuid;
import tools.vitruv.change.composite.description.PropagatedChange;
import tools.vitruv.change.composite.description.VitruviusChange;
import tools.vitruv.change.composite.propagation.ChangePropagationListener;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.VirtualModel;

public class PerformanceTest extends TestBase {
  @Test
  public void testLargeBrakesystemToEach(@TempDir Path tempDir) throws Exception {
    test(tempDir, List.of(new Brakesystem2autosarChangePropagationSpecification(),
                          new Autosar2brakesystemChangePropagationSpecification(),
                          new Brakesystem2cadChangePropagationSpecification(),
                          new Cad2brakesystemChangePropagationSpecification(),
                          new Brakesystem2simulinkChangePropagationSpecification(),
                          new Simulink2brakesystemChangePropagationSpecification()));
  }

  private void test(Path tempDir, List<ChangePropagationSpecification> cps) throws IOException {
    VirtualModel vsum = util.createDefaultVirtualModel(tempDir, cps);

    ChangePropagationTracker tracker = new ChangePropagationTracker();
    vsum.addChangePropagationListener(tracker);

    Stopwatch stopwatch = Stopwatch.createUnstarted();

    util.modifyView(util
                        .getDefaultView(vsum, List.of(Brakesystem.class))
                        .withChangeDerivingTrait(), (CommittableView v) -> {
      Brakesystem brakesystem = BrakesystemFactory.eINSTANCE.createBrakesystem();
      v.registerRoot(brakesystem, URI.createFileURI(tempDir
                                                        .resolve("example.brakesystem")
                                                        .toString()));

      for (int index = 0; index < 1000; index++) {
        switch (index % 4) {
          case 0:
            util.userInteraction.addNextSingleSelection(0); // ApplicationSwComponentType
            BrakeDisk brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
            brakeDisk.setId("brakeDisk" + index);
            brakeDisk.setOEM_number("OEM" + index);
            brakeDisk.setVentilated(true);
            brakeDisk.setDiameterInMM(120);
            brakesystem
                .getBrakeComponents()
                .add(brakeDisk);
            break;
          case 1:
            util.userInteraction.addNextSingleSelection(0); // ApplicationSwComponentType
            BrakeHose brakeHose = BrakesystemFactory.eINSTANCE.createBrakeHose();
            brakeHose.setId("brakeHose" + index);
            brakeHose.setThreadSize1("TestValue");
            brakeHose.setLengthInMM(100);
            brakesystem
                .getBrakeComponents()
                .add(brakeHose);
            break;
          case 2:
            ABSSensor absSensor = BrakesystemFactory.eINSTANCE.createABSSensor();
            absSensor.setId("absSensor" + index);
            absSensor.setSpecificationType("ExampleSpecification");
            absSensor.setFittingDepth(120);
            brakesystem
                .getBrakeComponents()
                .add(absSensor);
            break;
          case 3:
            BrakeCaliper brakeCaliper = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
            brakeCaliper.setId("brakeCaliper" + index);
            brakeCaliper.setPistonDiameterInMM(50);
            util.userInteraction.addNextSingleSelection(0); // ApplicationSwComponentType
            BrakePad brakePad1 = BrakesystemFactory.eINSTANCE.createBrakePad();
            brakePad1.setId("brakePad" + index + "_1");
            brakePad1.setWearWarning(true);
            brakePad1.setOEM_number("OEM" + index + "_1");
            util.userInteraction.addNextSingleSelection(0); // ApplicationSwComponentType
            BrakePad brakePad2 = BrakesystemFactory.eINSTANCE.createBrakePad();
            brakePad2.setId("brakePad" + index + "_2");
            brakePad2.setWearWarning(false);
            brakePad2.setOEM_number("OEM" + index + "_2");
            brakeCaliper
                .getBrakePads()
                .add(brakePad1);
            brakeCaliper
                .getBrakePads()
                .add(brakePad2);
            brakesystem
                .getBrakeComponents()
                .add(brakeCaliper);
            break;
        }
      }

      stopwatch.start();
    });

    stopwatch.stop();
    System.out.println("Time: " + stopwatch.elapsed());

    tracker.report();
  }

  @Override
  protected List<ChangePropagationSpecification> createCPS() {
    return List.of();
  }

  private static class ChangePropagationTracker implements ChangePropagationListener {
    private final List<Iterable<PropagatedChange>> changes = new ArrayList<>();

    @Override
    public void startedChangePropagation(VitruviusChange<Uuid> vitruviusChange) {

    }

    @Override
    public void finishedChangePropagation(Iterable<PropagatedChange> iterable) {
      changes.add(iterable);
    }

    public void report() {
      int numberOfPropagations = 0;
      int numberOfPropagationWaves = 0;
      int numberOfTransactionalChanges = 0;
      int numberOfEChanges = 0;

      for (Iterable<PropagatedChange> iterable : changes) {
        numberOfPropagations += 1;

        for (PropagatedChange propagatedChange : iterable) {
          numberOfPropagationWaves += 1;

          for (var transactionalChange : propagatedChange
              .getOriginalChange()
              .getTransactionalChangeSequence()) {
            numberOfTransactionalChanges += 1;
            numberOfEChanges += transactionalChange
                .getEChanges()
                .size();
          }
        }
      }

      System.out.println("Finished propagation, propagations: " + numberOfPropagations + ", waves: "
                             + numberOfPropagationWaves + ", transactional changes: "
                             + numberOfTransactionalChanges + ", e changes: " + numberOfEChanges);
    }
  }
}
