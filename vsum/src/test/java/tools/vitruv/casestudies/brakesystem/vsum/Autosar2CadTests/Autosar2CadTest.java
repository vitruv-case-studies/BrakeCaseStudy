package tools.vitruv.casestudies.brakesystem.vsum.Autosar2CadTests;

import autosar.*;
import brakesystem.ABSSensor;
import brakesystem.Brakesystem;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import edu.kit.ipd.sdq.metamodels.cad.StringParameter;
import mir.reactions.autosar2brakesystem.Autosar2brakesystemChangePropagationSpecification;
import mir.reactions.autosar2cad.Autosar2cadChangePropagationSpecification;
import mir.reactions.brakesystem2autosar.Brakesystem2autosarChangePropagationSpecification;
import mir.reactions.cad2autosar.Cad2autosarChangePropagationSpecification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.casestudies.brakesystem.vsum.TestUtil;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public class Autosar2CadTest {
    TestUtil testUtil = new TestUtil();
    Iterable<ChangePropagationSpecification> necessaryCPS = List.of(new Autosar2cadChangePropagationSpecification(), new Cad2autosarChangePropagationSpecification());

    @BeforeAll
    public static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    @Test
    public void testCreateAndRegisterAutosarModel(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir, necessaryCPS);

        testUtil.modifyView(testUtil.getDefaultView(vsum, List.of(AUTOSAR.class)).withChangeDerivingTrait(), (CommittableView v) -> {
            AUTOSAR autosarModel = AutoSARFactory.eINSTANCE.createAUTOSAR();
            v.registerRoot(autosarModel, URI.createFileURI(tempDir.resolve("example.autosar").toString()));
        });

        Assertions.assertTrue(assertView(testUtil.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            CAD_Model cad = v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
            return cad != null;
        }));
    }

    @Test
    public void testCreateSubPackage(@TempDir Path tempDir) throws Exception {
        VirtualModel vsum = testUtil.createDefaultVirtualModel(tempDir, necessaryCPS);

        testUtil.modifyView(testUtil.getDefaultView(vsum, List.of(AUTOSAR.class)).withChangeDerivingTrait(), (CommittableView v) -> {
            AUTOSAR autosarModel = AutoSARFactory.eINSTANCE.createAUTOSAR();
            v.registerRoot(autosarModel, URI.createFileURI(tempDir.resolve("example.autosar").toString()));

            ARPackage rootPackage = AutoSARFactory.eINSTANCE.createARPackage();
            rootPackage.setShortName("RootPackage");
            autosarModel.getArpackage().add(rootPackage);

            ARPackage subPackage = AutoSARFactory.eINSTANCE.createARPackage();
            subPackage.setShortName("SubPackage");
            rootPackage.getSubPackages().add(subPackage);

            subPackage.setAdminData(AutoSARFactory.eINSTANCE.createAdminData());

            SDG cadNamespace = AutoSARFactory.eINSTANCE.createSDG();
            cadNamespace.setGid("cadNamespace");
            addSD(cadNamespace, "cadNamespaceId", "id_of_namespace");
            subPackage.getAdminData().getSdgs().add(cadNamespace);

            SDG parameter1 = AutoSARFactory.eINSTANCE.createSDG();
            addSD(parameter1, "type", "string");
            addSD(parameter1, "name", "String Parameter");
            addSD(parameter1, "value", "Test Value");
            subPackage.getAdminData().getSdgs().add(parameter1);

            SDG parameter2 = AutoSARFactory.eINSTANCE.createSDG();
            addSD(parameter2, "type", "float");
            addSD(parameter2, "name", "Numeric Parameter");
            addSD(parameter2, "value", 42.0f);
            subPackage.getAdminData().getSdgs().add(parameter2);
        });

        Assertions.assertTrue(assertView(testUtil.getDefaultView(vsum, List.of(CAD_Model.class)), (View v) -> {
            CAD_Model cad = v.getRootObjects(CAD_Model.class).stream().findFirst().orElseThrow();
            Namespace namespace = cad.getNamespaces().stream().filter(ns -> ns.getName().equals("SubPackage")).findFirst().orElseThrow();

            StringParameter stringParameter = (StringParameter) namespace.getParameters().stream().filter(p -> p.getName().equals("String Parameter")).findFirst().orElseThrow();
            NumericParameter numericParameter = (NumericParameter) namespace.getParameters().stream().filter(p -> p.getName().equals("Numeric Parameter")).findFirst().orElseThrow();

            return namespace.getId().equals("id_of_namespace") && stringParameter.getValue().equals("Test Value") && numericParameter.getValue() == 42.0f;
        }));
    }

    private void addSD(SDG sdg, String key, Object value) {
        SD sd = AutoSARFactory.eINSTANCE.createSD();
        sd.setKey(key);
        sd.setValue(value);
        sdg.getSd().add(sd);
    }

    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
        return viewAssertionFunction.apply(view);
    }
}
