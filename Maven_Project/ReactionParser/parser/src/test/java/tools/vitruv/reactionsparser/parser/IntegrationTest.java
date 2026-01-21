package tools.vitruv.reactionsparser.parser;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;


import java.util.Map;
import java.util.Set;

import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.BiConsumer;

import org.eclipse.emf.common.util.URI;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;

import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.xtext.common.types.JvmOperation;
import org.eclipse.xtext.xbase.XAssignment;
import org.eclipse.xtext.xbase.XBlockExpression;
import org.eclipse.xtext.xbase.XExpression;
import org.eclipse.xtext.xbase.XFeatureCall;
import org.eclipse.xtext.xbase.XMemberFeatureCall;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


import tools.vitruv.dsls.reactions.ReactionsLanguageStandaloneSetup;
import tools.vitruv.dsls.reactions.language.ModelAttributeReplacedChange;
import tools.vitruv.dsls.reactions.language.ModelElementChange;
import tools.vitruv.dsls.reactions.language.toplevelelements.ReactionsFile;



public class IntegrationTest {

    @BeforeAll
    public static void setupAll() {
        ReactionsLanguageStandaloneSetup.doSetup();
    }

    @Test
    public void testMultipleReactionsFiles() {
        // testReactionsParsing("template.reactions", "template.xmi");
        testReactionsParsing("template2.reactions", "template2.xmi");
    }

    private void testReactionsParsing(String inputFile, String outputFile) {
        var parser = new GenericXtextParser();
        ReactionsFile result = null;
        try {
            String inputPath = resourcePath(inputFile);
            result = (ReactionsFile) parser.parse(inputPath);
        } catch (Exception e) {
            fail("Parsing failed for " + inputFile + ": " + e.getMessage());
        }
        for (var mmImport : result.getMetamodelImports()) {
            System.out.println("DEBUG: [" + inputFile + "] import " + mmImport.getName());
        }
        try {
            save(result, outputFile);
            generateSemanticModel(result, outputFile.replace(".xmi", "_semantic.xmi"));
        } catch (IOException e) {
            e.printStackTrace();
            fail("Saving XMI failed for " + outputFile);
        }
    }

    private String resourcePath(String fileName) {
        return new File(this.getClass().getClassLoader().getResource(fileName).getFile()).getAbsolutePath();
    }

    private void save(EObject content, String path) throws IOException {
        Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
        Map<String, Object> m = reg.getExtensionToFactoryMap();
        m.put("xmi", new XMIResourceFactoryImpl());
        ResourceSet resSet = new ResourceSetImpl();
        Resource resource = resSet.createResource(URI.createURI(path));
        resource.getContents().add(content);
        resource.save(Map.of());
    }

    // === generateSemanticModel ===
    private void generateSemanticModel(ReactionsFile reactionsFile, String outputPath) {
        ResourceSet resourceSet = new ResourceSetImpl();
        // Register the XMI resource factory
        Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
        Map<String, Object> m = reg.getExtensionToFactoryMap();
        m.put("xmi", new XMIResourceFactoryImpl());
        
        // Load semantic.ecore
        String projectRoot = new File(System.getProperty("user.dir")).getParent();
        String semanticEcorePath = projectRoot + "/model/src/main/ecore/semantic.ecore";
        Resource semanticEcore = resourceSet.getResource(URI.createFileURI(semanticEcorePath), true);
        EObject semanticRoot = semanticEcore.getContents().get(0);
        if (!(semanticRoot instanceof org.eclipse.emf.ecore.EPackage)) {
            fail("semantic.ecore root is not an EPackage");
        }
        var semanticPackage = (org.eclipse.emf.ecore.EPackage) semanticRoot;
        var factory = semanticPackage.getEFactoryInstance();
        var metamodelClass = (EClass) semanticPackage.getEClassifier("Metamodel");
        var metaclassClass = (EClass) semanticPackage.getEClassifier("Metaclass");
        var metaattributeClass = (EClass) semanticPackage.getEClassifier("Metaattribute");
        var metareferenceClass = (EClass) semanticPackage.getEClassifier("Metareference");

        // Build mapping from URI to metamodel name (using metamodelImports)
        Map<String, String> uriToName = new HashMap<>();
        for (var mmImport : reactionsFile.getMetamodelImports()) {
            var pack = mmImport.getPackage();
            String uri = (pack != null && pack.eResource() != null) ? pack.eResource().getURI().toString() : null;
            if (uri != null) {
                uriToName.put(uri, mmImport.getName());
                System.out.println("DEBUG: Mapping URI " + uri + " to metamodel " + mmImport.getName());
            }
        }

        // Maps to record metamodel-to-metaclass and metaclass-to-metaattribute relationships
        Map<String, Set<String>> metamodelToMetaclasses = new HashMap<>();
        Map<String, Set<String>> metaclassToMetaattributes = new HashMap<>();
        Map<String, List<Object[]>> metaclassToMetareferences = new HashMap<>(); // key = mm#Class, value = List<[refName, targetClassName]>
        Map<String, EObject> classKeyToMetaclassObj = new HashMap<>();


        BiConsumer<String, String> addMetaclass = (uri, className) -> {
            String metamodelName = uriToName.get(uri);
            if (metamodelName == null) {
                System.out.println("DEBUG: addMetaclass: No metamodel for URI " + uri);
                return;
            }
            metamodelToMetaclasses.computeIfAbsent(metamodelName, k -> new HashSet<>()).add(className);
            System.out.println("DEBUG: addMetaclass: metamodel " + metamodelName + ", class " + className);
        };

        BiConsumer<String, String> addMetaattribute = (clsUri, featureUri) -> {
            // If using the dummy prefix, parse it directly
            if (clsUri.startsWith("dummy:")) {
                String dummy = clsUri.substring("dummy:".length()); // e.g., "ascet#//InitTask"
                int idx = dummy.indexOf("#//");
                if (idx >= 0) {
                    String mmNameDummy = dummy.substring(0, idx);
                    String classNameDummy = dummy.substring(idx + 3);
                    metamodelToMetaclasses.computeIfAbsent(mmNameDummy, k -> new HashSet<>()).add(classNameDummy);
                    String key = mmNameDummy + "#" + classNameDummy;
                    // Remove dummyHref: prefix if present in featureUri
                    if (featureUri.startsWith("dummyHref:")) {
                        featureUri = featureUri.substring("dummyHref:".length());
                    }
                    metaclassToMetaattributes.computeIfAbsent(key, k -> new HashSet<>()).add(extractAttributeName(featureUri));
                    System.out.println("DEBUG: addMetaattribute (dummy): metamodel " + mmNameDummy + ", class " + classNameDummy + ", attribute " + extractAttributeName(featureUri));
                    return;
                }
            }
            // Otherwise, standardize clsUri to format "nsURI#//ClassName"
            if (!clsUri.contains("#//")) {
                clsUri = clsUri.replace("#", "#//");
                System.out.println("DEBUG: Normalized clsUri to " + clsUri);
            }
            String className = extractClassName(clsUri);
            String mmUri = clsUri.contains("#//") ? clsUri.replaceAll("#//[^/]+$", "#/") 
                                                  : clsUri.substring(0, clsUri.lastIndexOf("#") + 1) + "/";
            String mmName = uriToName.get(mmUri);
            if (mmName == null) {
                String altKey = mmUri.endsWith("/") ? mmUri.substring(0, mmUri.length() - 1) : mmUri + "/";
                System.out.println("DEBUG: mmName not found for mmUri: " + mmUri + ", trying alternative key: " + altKey);
                mmName = uriToName.get(altKey);
            }
            String attributeName = extractAttributeName(featureUri);
            if (mmName != null && className != null && attributeName != null) {
                metamodelToMetaclasses.computeIfAbsent(mmName, k -> new HashSet<>()).add(className);
                String key = mmName + "#" + className;
                metaclassToMetaattributes.computeIfAbsent(key, k -> new HashSet<>()).add(attributeName);
                System.out.println("DEBUG: addMetaattribute: metamodel " + mmName + ", class " + className + ", attribute " + attributeName);
            } else {
                System.out.println("DEBUG: addMetaattribute skipped: mmName=" + mmName + ", className=" + className + ", attributeName=" + attributeName);
            }
        };

        // Process each segment in the reactions file
        for (var segment : reactionsFile.getReactionsSegments()) {
            System.out.println("DEBUG: Processing segment: " + segment.getName());
            for (var reaction : segment.getReactions()) {
                System.out.println("DEBUG: Processing reaction: " + reaction.getName());
                var trigger = reaction.getTrigger();
                if (trigger instanceof ModelElementChange mec) {
                    EClassifier c = mec.getElementType().getMetaclass();
                    if (c instanceof EClass cls) {
                        extractHref(cls, addMetaclass);
                    }
                
                    var changeType = mec.getChangeType();
                    dumpEObjectStructure("changeType", changeType);
                
                    if (changeType != null && changeType.eClass().getName().equals("ElementInsertionInListChangeType")) {
                        var featureObj = (EObject) changeType.eGet(changeType.eClass().getEStructuralFeature("feature"));
                        if (featureObj != null) {
                            EObject sourceClsObj = (EObject) featureObj.eGet(featureObj.eClass().getEStructuralFeature("metaclass"));
                            
                            if (sourceClsObj instanceof EClass sourceCls && sourceCls.eResource() != null) {
                                String sourceClass = sourceCls.getName();
                                extractHref(sourceCls, addMetaclass);
                
                                if (mec.getElementType().getMetaclass() instanceof EClass targetCls && targetCls.eResource() != null) {
                                    
                                    extractHref(targetCls, addMetaclass);
                
                                    EObject featureRef = (EObject) featureObj.eGet(featureObj.eClass().getEStructuralFeature("feature"));
                                    if (featureRef instanceof org.eclipse.emf.ecore.EReference eReference) {
                                        String refName = eReference.getName(); // e.g., "tasks"
                                        String targetClass = eReference.getEType().getName(); // e.g., "Task"
                                    
                                        // 获取 metamodel name
                                        Object mmObj = featureObj.eGet(featureObj.eClass().getEStructuralFeature("metamodel"));
                                        String mmName = null;
                                        if (mmObj instanceof EObject) {
                                            mmName = (String) ((EObject) mmObj)
                                                .eGet(((EObject) mmObj).eClass().getEStructuralFeature("name"));
                                        }
                                    
                                        if (mmName != null && sourceClass != null && targetClass != null && refName != null) {
                                            String key = mmName + "#" + sourceClass;
                                            metaclassToMetareferences.computeIfAbsent(key, k -> new java.util.ArrayList<>())
                                                                     .add(new Object[]{refName, targetClass});
                                            System.out.println("DEBUG: addMetareference (resolved): " + key + "." + refName + " -> " + targetClass);
                                        } else {
                                            System.out.println("DEBUG: Incomplete information to add metareference.");
                                        }
                                    } else {
                                        System.out.println("DEBUG: feature field is not an EReference.");
                                    }
                                    System.out.println("featureRef is null");
                                }
                            }
                        } 
                    } else {
                        System.out.println("Debug: changetype is null or not ElementInsertionInListChangeType");
                    }
                }
                 else if (trigger instanceof ModelAttributeReplacedChange marc) {
                    // Directly read tracking information from the feature object
                    EObject featureObj = marc.getFeature();
                    Object mmObj = featureObj.eGet(featureObj.eClass().getEStructuralFeature("metamodel"));
                    Object mcObj = featureObj.eGet(featureObj.eClass().getEStructuralFeature("metaclass"));
                    if (mmObj instanceof EObject && mcObj instanceof EObject) {
                        String mmNameRetrieved = (String) ((EObject) mmObj).eGet(((EObject) mmObj).eClass().getEStructuralFeature("name"));
                        String metaclassName = (String) ((EObject) mcObj).eGet(((EObject) mcObj).eClass().getEStructuralFeature("name"));
                        System.out.println("DEBUG: Retrieved from feature: metamodel = " + mmNameRetrieved + ", metaclass = " + metaclassName);
                        // Construct a dummy clsUri in the format "dummy:metamodelName#//metaclassName"
                        String dummyClsUri = "dummy:" + mmNameRetrieved + "#//" + metaclassName;
                        // Extract the metaattribute name from the feature object
                        dumpEObjectStructure("Feature Object", featureObj);
                        extractFeatureHref(featureObj, (uri, featureUri) -> {
                            System.out.println("DEBUG: Processing ModelAttributeReplacedChange for metaclass " + metaclassName + " in metamodel " + mmNameRetrieved);
                            addMetaattribute.accept(dummyClsUri, "dummyHref:" + featureUri);
                        });
                    } else {
                        System.out.println("DEBUG: Unable to retrieve metamodel/metaclass info directly from feature");
                    }
                }
            }
            for (var routine : segment.getRoutines()) {
                System.out.println("DEBUG: Processing routine: " + routine.getName());
                for (var input : routine.getInput().getModelInputElements()) {
                    EClassifier c = input.getMetaclass();
                    if (c instanceof EClass cls) {
                        extractHref(cls, addMetaclass);
                    }
                }
                if (routine.getMatchBlock() != null) {
                    for (var match : routine.getMatchBlock().getMatchStatements()) {
                        if (match instanceof tools.vitruv.dsls.reactions.language.RetrieveModelElement retrieve) {
                            var elementType = retrieve.getElementType();
                            if (elementType != null && elementType.getMetaclass() instanceof EClass cls) {
                                extractHref(cls, addMetaclass);
                            }
                        }
                    }
                }
                if (routine.getCreateBlock() != null) {
                    for (var create : routine.getCreateBlock().getCreateStatements()) {
                        var c = create.getMetaclass();
                        if (c instanceof EClass cls) {
                            extractHref(cls, addMetaclass);
                        }
                    }
                }

                // New: extract metaattributes from update block
                extractMetaattributesFromUpdateBlock(routine, metaclassToMetaattributes, metamodelToMetaclasses, uriToName);

            }
        }
        System.out.println("DEBUG: metamodelToMetaclasses = " + metamodelToMetaclasses);
        System.out.println("DEBUG: metaclassToMetaattributes = " + metaclassToMetaattributes);

        // Build semantic.xmi by iterating over metamodel imports
        Resource semanticXmi = resourceSet.createResource(URI.createFileURI(outputPath));
        for (var mmImport : reactionsFile.getMetamodelImports()) {
            String metamodelName = mmImport.getName();
            var metamodelObj = factory.create(metamodelClass);
            metamodelObj.eSet(metamodelClass.getEStructuralFeature("name"), metamodelName);
            System.out.println("DEBUG: Building Metamodel: " + metamodelName);
            var metaclassSet = metamodelToMetaclasses.getOrDefault(metamodelName, Set.of());
            
            for (String metaclassName : metaclassSet) {
                    var metaclassObj = factory.create(metaclassClass);
                    metaclassObj.eSet(metaclassClass.getEStructuralFeature("name"), metaclassName);
                    System.out.println("DEBUG:  Building Metaclass: " + metaclassName);
                    String key = metamodelName + "#" + metaclassName;
                    classKeyToMetaclassObj.put(key, metaclassObj);
                
                    // 添加 metaattributes
                    Set<String> metaAttrs = metaclassToMetaattributes.getOrDefault(key, Set.of());
                    for (String attrName : metaAttrs) {
                        var metaAttrObj = factory.create(metaattributeClass);
                        metaAttrObj.eSet(metaattributeClass.getEStructuralFeature("name"), attrName);
                        @SuppressWarnings("unchecked")
                        List<EObject> attributesList = (List<EObject>) metaclassObj.eGet(metaclassClass.getEStructuralFeature("attributes"));
                        attributesList.add(metaAttrObj);
                        System.out.println("DEBUG:   Adding Metaattribute: " + attrName + " to class " + metaclassName);
                    }
                
                    // ✅ 插入你这段 reference 处理逻辑
                    var metarefs = metaclassToMetareferences.getOrDefault(key, List.of());
                    for (Object[] ref : metarefs) {
                        String refName = (String) ref[0];
                        String targetClass = (String) ref[1];
                        var metaRefObj = factory.create(metareferenceClass);
                        metaRefObj.eSet(metareferenceClass.getEStructuralFeature("name"), refName);
                        String targetKey = metamodelName + "#" + targetClass;
                        EObject targetMetaclassObj = classKeyToMetaclassObj.get(targetKey);
                        if (targetMetaclassObj != null) {
                        metaRefObj.eSet(metareferenceClass.getEStructuralFeature("target"), targetMetaclassObj);
                        System.out.println("DEBUG:   Adding Metareference: " + refName + " -> " + targetClass + " to class " + metaclassName);
                        } else {
                        System.out.println("WARNING: target Metaclass not found for reference: " + targetClass);
                        }

                        @SuppressWarnings("unchecked")
                        List<EObject> refList = (List<EObject>) metaclassObj.eGet(metaclassClass.getEStructuralFeature("references"));
                        refList.add(metaRefObj);
                        System.out.println("DEBUG:   Adding Metareference: " + refName + " -> " + targetClass + " to class " + metaclassName);
                    }
                
                    @SuppressWarnings("unchecked")
                    List<EObject> list = (List<EObject>) metamodelObj.eGet(metamodelClass.getEStructuralFeature("metaclasses"));
                    list.add(metaclassObj);
                }
                
            semanticXmi.getContents().add(metamodelObj);
        }
        try {
            semanticXmi.save(Map.of());
            System.out.println("DEBUG: semantic.xmi saved to " + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
            fail("Saving semantic.xmi failed: " + e.getMessage());
        }
    }

    /**
     * Utility method: prints the type, all structural features with their values,
     * all children (eContents), and cross references of the given EObject.
     */
    private void dumpEObjectStructure(String prefix, EObject obj) {
        System.out.println("DEBUG: " + prefix + " of type " + obj.eClass().getName());
        for (var sf : obj.eClass().getEAllStructuralFeatures()) {
            Object val = obj.eGet(sf);
            System.out.println("  DEBUG: Structural feature: " + sf.getName() + " = " + val);
        }
        for (EObject child : obj.eContents()) {
            System.out.println("  DEBUG: Child: " + child.eClass().getName());
        }
        for (EObject ref : obj.eCrossReferences()) {
            System.out.println("  DEBUG: Cross reference: " + ref.eClass().getName());
        }
    }

    /**
     * Extracts the class name from a given clsUri.
     * If clsUri contains "#//", returns the substring after "#//",
     * otherwise returns the substring after "#".
     */
    private String extractClassName(String clsUri) {
        int hashIndex = clsUri.lastIndexOf("#");
        if (hashIndex < 0 || hashIndex + 1 >= clsUri.length()) {
            return null;
        }
        if (clsUri.charAt(hashIndex + 1) == '/' && hashIndex + 2 < clsUri.length() && clsUri.charAt(hashIndex + 2) == '/') {
            return clsUri.substring(hashIndex + 3);
        } else {
            return clsUri.substring(hashIndex + 1);
        }
    }

    /**
     * Extracts the attribute name from a feature URI by taking the substring after the last "/".
     */
    private String extractAttributeName(String featureUri) {
        return featureUri.substring(featureUri.lastIndexOf("/") + 1);
    }

    /**
     * Modified extractFeatureHref method:
     * 
     * If the feature's type is MetaclassEAttributeReference, then directly read the "name"
     * from its "feature" attribute.
     */
    private void extractFeatureHref(EObject feature, BiConsumer<String, String> collector) {
        if (feature == null) return;
        
        System.out.println("DEBUG: extractFeatureHref: processing feature of type " + feature.eClass().getName());
        dumpEObjectStructure("Feature", feature);
        
        // Case 1: If feature is of type MetaclassFeatureReference, process accordingly.
        if (feature instanceof tools.vitruv.dsls.common.elements.MetaclassFeatureReference) {
            tools.vitruv.dsls.common.elements.MetaclassFeatureReference ref =
                (tools.vitruv.dsls.common.elements.MetaclassFeatureReference) feature;
            String uri = ref.getFeature().eResource().getURI().toString();
            System.out.println("DEBUG: extractFeatureHref: Found MetaclassFeatureReference with URI " + uri);
            collector.accept(uri, uri);
            return;
        }
        
        // Case 2: If feature is of type MetaclassEAttributeReference, directly read the attribute "name"
        if (feature.eClass().getName().contains("MetaclassEAttributeReference")) {
            Object attObj = feature.eGet(feature.eClass().getEStructuralFeature("feature"));
            if (attObj instanceof EObject) {
                EObject att = (EObject) attObj;
                Object nameVal = att.eGet(att.eClass().getEStructuralFeature("name"));
                if (nameVal instanceof String) {
                    String name = (String) nameVal;
                    System.out.println("DEBUG: extractFeatureHref: Extracted metaattribute name from MetaclassEAttributeReference: " + name);
                    collector.accept("dummyHref:" + name, name);
                    return;
                }
            }
        }
        
        // Other cases can be added here if needed...
        System.out.println("DEBUG: extractFeatureHref: No applicable extraction rule for this feature");
    }
    
    private void extractHref(EClass eclass, BiConsumer<String, String> collector) {
        if (eclass == null) return;
        String uri = null;
        String className = null;
        if (eclass.eResource() != null) {
            uri = eclass.eResource().getURI().toString();
            className = eclass.getName();
        } else if (eclass.eIsProxy()) {
            var proxyUri = ((InternalEObject) eclass).eProxyURI();
            uri = proxyUri.trimFragment().toString();
            String fragment = proxyUri.fragment();
            if (fragment != null && fragment.startsWith("//")) {
                className = fragment.substring(2);
            }
        }
        if (uri != null && className != null) {
            System.out.println("DEBUG: extractHref: " + uri + " -> " + className);
            collector.accept(uri, className);
        }
    }  

    private void extractMetaattributesFromUpdateBlock(
        tools.vitruv.dsls.reactions.language.toplevelelements.Routine routine,
        Map<String, Set<String>> metaclassToMetaattributes,
        Map<String, Set<String>> metamodelToMetaclasses,
        Map<String, String> uriToName
) {
    Map<String, String[]> varToClassUri = new HashMap<>();

    // 1. Head parameters (routine header)
    for (var input : routine.getInput().getModelInputElements()) {
        var mc = input.getMetaclass();
        if (mc instanceof EClass eClass && eClass.eResource() != null) {
            String uri = eClass.eResource().getURI().toString();
            varToClassUri.put(input.getName(), new String[]{uri, eClass.getName()});
        }
    }

    // 2. Match block variables (retrieve/require)
    if (routine.getMatchBlock() != null) {
        for (var match : routine.getMatchBlock().getMatchStatements()) {
            if (match instanceof tools.vitruv.dsls.reactions.language.RetrieveModelElement retrieve) {
                var elementType = retrieve.getElementType();
                if (elementType != null && elementType.getMetaclass() instanceof EClass eClass &&
                    retrieve.getName() != null && eClass.eResource() != null) {
                    String uri = eClass.eResource().getURI().toString();
                    varToClassUri.put(retrieve.getName(), new String[]{uri, eClass.getName()});
                }
            }
        }
    }

    // 3. Parse update block
    if (routine.getUpdateBlock() != null && routine.getUpdateBlock().getCode() instanceof XBlockExpression block) {
        for (XExpression expr : block.getExpressions()) {
            if (expr instanceof XAssignment assignment && assignment.getFeature() instanceof JvmOperation jvmOp) {
                if (assignment.getAssignable() instanceof XFeatureCall left) {
                    handleJvmOperationAttribute(jvmOp, left, varToClassUri, metaclassToMetaattributes, metamodelToMetaclasses, uriToName);
                }
            } else if (expr instanceof XMemberFeatureCall call && call.getFeature() instanceof JvmOperation jvmOp) {
                if (call.getMemberCallTarget() instanceof XFeatureCall left) {
                    handleJvmOperationAttribute(jvmOp, left, varToClassUri, metaclassToMetaattributes, metamodelToMetaclasses, uriToName);
                }
            }
        }
    }
}

private void handleJvmOperationAttribute(
        JvmOperation jvmOp,
        XFeatureCall left,
        Map<String, String[]> varToClassUri,
        Map<String, Set<String>> metaclassToMetaattributes,
        Map<String, Set<String>> metamodelToMetaclasses,
        Map<String, String> uriToName
) {
    String methodName = jvmOp.getSimpleName();
    if ((methodName.startsWith("get") || methodName.startsWith("set")) && methodName.length() > 3) {
        String attrName = methodName.substring(3);
        attrName = Character.toLowerCase(attrName.charAt(0)) + attrName.substring(1);
        String varName = left.getFeature().getSimpleName();
        String[] classInfo = varToClassUri.get(varName);
        if (classInfo != null) {
            String uri = classInfo[0];
            String className = classInfo[1];
            String mmName = uriToName.get(uri);
            if (mmName == null) {
                System.out.println("DEBUG: Cannot find metamodel name for URI: " + uri);
                return;
            }
            metamodelToMetaclasses.computeIfAbsent(mmName, k -> new HashSet<>()).add(className);
            String key = mmName + "#" + className;
            metaclassToMetaattributes.computeIfAbsent(key, k -> new HashSet<>()).add(attrName);
            System.out.println("DEBUG: [updateBlock] addMetaattribute: metamodel " + mmName + ", class " + className + ", attr " + attrName);
        } else {
            System.out.println("DEBUG: [updateBlock] Unknown variable " + varName);
        }
    }

}
    
}