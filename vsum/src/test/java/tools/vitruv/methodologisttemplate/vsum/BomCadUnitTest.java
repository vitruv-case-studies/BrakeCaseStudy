package tools.vitruv.methodologisttemplate.vsum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bom.BOM;
import bom.BomFactory;
import bom.DataType;
import bom.DoubleParameterValue;
import bom.Item;
import bom.ItemType;
import bom.ParameterDefinition;
import edu.kit.ipd.sdq.metamodels.cad.CAD_Model;
import edu.kit.ipd.sdq.metamodels.cad.CadFactory;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;
import edu.kit.ipd.sdq.metamodels.cad.NumericParameter;
import edu.kit.ipd.sdq.metamodels.cad.Unit;
import mir.reactions.bom2cad.Bom2cadChangePropagationSpecification;
import mir.reactions.cad2bom.Cad2bomChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.vsum.VirtualModel;

class BomCadUnitTest {

    private final TestUtil util = new TestUtil();

    @BeforeAll
    static void setupResourceFactory() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
    }

    @Test
    void propagatesNumericUnitFromBomToCad(@TempDir Path tempDir) {
        VirtualModel vsum = createVirtualModel(tempDir);

        CommittableView initialView = util.getDefaultView(vsum, List.of(BOM.class, ItemType.class))
                .withChangeDerivingTrait();
        util.modifyView(initialView, view -> {
            ItemType type = BomFactory.eINSTANCE.createItemType();
            type.setName("BrakeDisk");
            ParameterDefinition diameter = BomFactory.eINSTANCE.createParameterDefinition();
            diameter.setName("Diameter");
            diameter.setDataType(DataType.EDOUBLE);
            type.getParameterDefinitions().add(diameter);
            view.registerRoot(type, URI.createFileURI(tempDir.resolve("types.xmi").toString()));

            BOM bom = BomFactory.eINSTANCE.createBOM();
            bom.setName("Brake BOM");
            view.registerRoot(bom, URI.createFileURI(tempDir.resolve("bom.xmi").toString()));
        });

        CommittableView bomView = util.getDefaultView(vsum,
                        List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .withChangeRecordingTrait();
        util.modifyView(bomView, view -> {
            ItemType type = view.getRootObjects(ItemType.class).iterator().next();
            Item item = BomFactory.eINSTANCE.createItem();
            item.setId("disk-1");
            item.setType(type);
            item.setNumberOfItems(2);
            DoubleParameterValue diameter = BomFactory.eINSTANCE.createDoubleParameterValue();
            diameter.setDefinition(type.getParameterDefinitions().get(0));
            diameter.setValue(42.5);
            diameter.setUnit("cm");
            item.getParameterValues().add(diameter);
            view.getRootObjects(BOM.class).iterator().next().getItems().add(item);
        });

        CAD_Model cadModel = getCadModel(vsum);
        assertEquals(2, cadModel.getNamespaces().size());
        cadModel.getNamespaces().forEach(namespace -> {
            NumericParameter cadDiameter = (NumericParameter) namespace.getParameters().get(0);
            assertEquals(42.5f, cadDiameter.getValue());
            assertEquals(Unit.CM, cadDiameter.getUnit());
        });

        CommittableView updateView = util.getDefaultView(vsum, List.of(BOM.class))
                .withChangeRecordingTrait();
        util.modifyView(updateView, view -> ((DoubleParameterValue) view.getRootObjects(BOM.class)
                .iterator().next().getItems().get(0).getParameterValues().get(0)).setUnit("in"));
        getCadModel(vsum).getNamespaces().forEach(namespace ->
                assertEquals(Unit.INCH, ((NumericParameter) namespace.getParameters().get(0)).getUnit()));

        util.modifyView(updateView, view -> ((DoubleParameterValue) view.getRootObjects(BOM.class)
                .iterator().next().getItems().get(0).getParameterValues().get(0)).setUnit(null));
        getCadModel(vsum).getNamespaces().forEach(namespace ->
                assertEquals(Unit.MM, ((NumericParameter) namespace.getParameters().get(0)).getUnit()));

        CommittableView cadDeleteView = util.getDefaultView(vsum, List.of(CAD_Model.class))
                .withChangeRecordingTrait();
        util.modifyView(cadDeleteView, view -> view.getRootObjects(CAD_Model.class).iterator().next()
                .getNamespaces().remove(0));
        assertEquals(1, getCadModel(vsum).getNamespaces().size());
        assertEquals(1, util.getDefaultView(vsum, List.of(BOM.class)).getRootObjects(BOM.class)
                .iterator().next().getItems().get(0).getNumberOfItems());
    }

    @Test
    void propagatesNumericUnitFromCadToBom(@TempDir Path tempDir) {
        VirtualModel vsum = createVirtualModel(tempDir);

        CommittableView cadView = util.getDefaultView(vsum, List.of(CAD_Model.class))
                .withChangeDerivingTrait();
        util.modifyView(cadView, view -> {
            CAD_Model model = CadFactory.eINSTANCE.createCAD_Model();
            model.setName("CAD model");
            Namespace namespace = CadFactory.eINSTANCE.createNamespace();
            namespace.setId("part-1");
            namespace.setName("BrakeDisk");
            NumericParameter diameter = CadFactory.eINSTANCE.createNumericParameter();
            diameter.setName("Diameter");
            diameter.setValue(20.5f);
            diameter.setUnit(Unit.FT);
            namespace.getParameters().add(diameter);
            model.getNamespaces().add(namespace);
            view.registerRoot(model, URI.createFileURI(tempDir.resolve("cad.xmi").toString()));
        });

        DoubleParameterValue bomDiameter = getBomNumericParameter(vsum);
        assertEquals(20.5, bomDiameter.getValue());
        assertEquals("ft", bomDiameter.getUnit());

        CommittableView updateView = util.getDefaultView(vsum, List.of(CAD_Model.class))
                .withChangeRecordingTrait();
        util.modifyView(updateView, view -> ((NumericParameter) view.getRootObjects(CAD_Model.class)
                .iterator().next().getNamespaces().get(0).getParameters().get(0)).setUnit(Unit.COUNT));
        assertEquals("COUNT", getBomNumericParameter(vsum).getUnit());
    }

    private VirtualModel createVirtualModel(Path tempDir) {
        return util.createDefaultVirtualModel(tempDir, List.of(
                new Bom2cadChangePropagationSpecification(),
                new Cad2bomChangePropagationSpecification()));
    }

    private NumericParameter getCadNumericParameter(VirtualModel vsum) {
        return (NumericParameter) getCadModel(vsum)
                .getNamespaces().get(0).getParameters().get(0);
    }

    private CAD_Model getCadModel(VirtualModel vsum) {
        return util.getDefaultView(vsum, List.of(CAD_Model.class))
                .getRootObjects(CAD_Model.class).iterator().next();
    }

    private DoubleParameterValue getBomNumericParameter(VirtualModel vsum) {
        return (DoubleParameterValue) util.getDefaultView(vsum,
                        List.of(BOM.class, ItemType.class, ParameterDefinition.class))
                .getRootObjects(BOM.class).iterator().next()
                .getItems().get(0).getParameterValues().get(0);
    }
}
