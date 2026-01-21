package tools.vitruv.casestudies.brakesystem.vsum;

import brakesystem.BrakeCaliper;
import brakesystem.BrakeDisk;
import brakesystem.BrakeHose;
import brakesystem.BrakePad;
import brakesystem.BrakesystemFactory;
import edu.kit.ipd.sdq.metamodels.cad.CadFactory;
import edu.kit.ipd.sdq.metamodels.cad.Namespace;

public class DefaultModelElements {


    public static Namespace createDefaultNamespace() {
        Namespace namespace = CadFactory.eINSTANCE.createNamespace();
        namespace.setName("myNamespace");
        namespace.setId("myID");
        return namespace;
    }

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

    public static BrakeCaliper createDefaultBrakeCaliper() {
    	var brakeCaliper = BrakesystemFactory.eINSTANCE.createBrakeCaliper();
    	brakeCaliper.setId("brakeCaliper1");
    	brakeCaliper.setOEM_number("BRMT420");
    	brakeCaliper.setBrakeDiskThickness(20);
    	brakeCaliper.setPistonDiameterInMM(10);
    	brakeCaliper.setFittingPosition("left");
    	brakeCaliper.setSpecificationType("Single Brake Caliper");
    	return brakeCaliper; 
    }

    public static BrakePad createDefaultBrakePad() {
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

    public static BrakeHose createDefaultBrakeHose() {
    	var brakeHose = BrakesystemFactory.eINSTANCE.createBrakeHose();
    	brakeHose.setId("brakeHose1");
    	brakeHose.setOEM_number("BH90SS");
    	brakeHose.setLengthInMM(1200);
    	brakeHose.setThreadSize1("5mm");
    	brakeHose.setThreadSize2("4mm");
    	brakeHose.setFittingPosition("left");
    	return brakeHose;
    }
}
