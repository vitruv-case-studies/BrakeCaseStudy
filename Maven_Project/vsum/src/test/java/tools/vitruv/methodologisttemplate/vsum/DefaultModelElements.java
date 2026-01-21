package tools.vitruv.methodologisttemplate.vsum;

import brakesystem.BrakeDisk;
import brakesystem.BrakesystemFactory;
import edu.kit.ipd.sdq.metamodels.cad.CadFactory;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;

public class DefaultModelElements {

    public static BrakeDisk createDefaultBrakeDisk() {
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

    public static Namespace createDefaultNamespace() {
        Namespace namespace = CadFactory.eINSTANCE.createNamespace();
        namespace.setName("myNamespace");
        namespace.setId("myID");
        return namespace;
    }
}
