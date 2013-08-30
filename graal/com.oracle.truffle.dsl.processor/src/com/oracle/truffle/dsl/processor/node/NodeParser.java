/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.dsl.processor.node;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.node.NodeChildData.Cardinality;
import com.oracle.truffle.dsl.processor.node.NodeChildData.ExecutionKind;
import com.oracle.truffle.dsl.processor.template.*;
import com.oracle.truffle.dsl.processor.template.TemplateMethod.Signature;
import com.oracle.truffle.dsl.processor.typesystem.*;

public class NodeParser extends TemplateParser<NodeData> {

    public static final List<Class<? extends Annotation>> ANNOTATIONS = Arrays.asList(Generic.class, TypeSystemReference.class, ShortCircuit.class, Specialization.class, SpecializationListener.class,
                    NodeContainer.class, NodeChild.class, NodeChildren.class, NodeId.class);

    private Map<String, NodeData> parsedNodes;

    public NodeParser(ProcessorContext c) {
        super(c);
    }

    @Override
    protected NodeData parse(Element element, AnnotationMirror mirror) {
        assert element instanceof TypeElement;
        NodeData node = null;
        try {
            parsedNodes = new HashMap<>();
            node = resolveNode((TypeElement) element);
            if (Log.DEBUG) {
                NodeData parsed = parsedNodes.get(Utils.getQualifiedName((TypeElement) element));
                if (node != null) {
                    String dump = parsed.dump();
                    log.message(Kind.ERROR, null, null, null, dump);
                }
            }
        } finally {
            parsedNodes = null;
        }

        return node;
    }

    @Override
    protected NodeData filterErrorElements(NodeData model) {
        for (Iterator<NodeData> iterator = model.getDeclaredNodes().iterator(); iterator.hasNext();) {
            NodeData node = filterErrorElements(iterator.next());
            if (node == null) {
                iterator.remove();
            }
        }
        if (model.hasErrors()) {
            return null;
        }
        return model;
    }

    @Override
    public boolean isDelegateToRootDeclaredType() {
        return true;
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return null;
    }

    @Override
    public List<Class<? extends Annotation>> getTypeDelegatedAnnotationTypes() {
        return ANNOTATIONS;
    }

    private NodeData resolveNode(TypeElement rootType) {
        String typeName = Utils.getQualifiedName(rootType);
        if (parsedNodes.containsKey(typeName)) {
            return parsedNodes.get(typeName);
        }

        List<? extends TypeElement> types = ElementFilter.typesIn(rootType.getEnclosedElements());

        List<NodeData> children = new ArrayList<>();
        for (TypeElement childElement : types) {
            NodeData childNode = resolveNode(childElement);
            if (childNode != null) {
                children.add(childNode);
            }
        }

        NodeData rootNode = parseNode(rootType);
        if (rootNode == null && children.size() > 0) {
            rootNode = new NodeData(rootType, rootType.getSimpleName().toString());
        }

        parsedNodes.put(typeName, rootNode);

        if (rootNode != null) {
            children.addAll(rootNode.getDeclaredNodes());
            rootNode.setDeclaredNodes(children);
        }

        return rootNode;
    }

    private NodeData parseNode(TypeElement originalTemplateType) {
        // reloading the type elements is needed for ecj
        TypeElement templateType = Utils.fromTypeMirror(context.reloadTypeElement(originalTemplateType));

        if (Utils.findAnnotationMirror(processingEnv, originalTemplateType, GeneratedBy.class) != null) {
            // generated nodes should not get called again.
            return null;
        }

        List<TypeElement> lookupTypes = findSuperClasses(new ArrayList<TypeElement>(), templateType);
        Collections.reverse(lookupTypes);

        AnnotationMirror nodeClass = findFirstAnnotation(lookupTypes, NodeContainer.class);
        TypeMirror nodeType = null;
        if (Utils.isAssignable(context, templateType.asType(), context.getTruffleTypes().getNode())) {
            nodeType = templateType.asType();
        }
        if (nodeClass != null) {
            nodeType = inheritType(nodeClass, "value", nodeType);
        }

        if (nodeType == null) {
            return null;
        }

        Elements elementUtil = context.getEnvironment().getElementUtils();
        Set<Element> elementSet = new HashSet<>(elementUtil.getAllMembers(templateType));
        if (!Utils.typeEquals(templateType.asType(), nodeType)) {
            elementSet.addAll(elementUtil.getAllMembers(Utils.fromTypeMirror(nodeType)));

            List<TypeElement> nodeLookupTypes = findSuperClasses(new ArrayList<TypeElement>(), Utils.fromTypeMirror(nodeType));
            Collections.reverse(nodeLookupTypes);
            lookupTypes.addAll(nodeLookupTypes);

            Set<TypeElement> types = new HashSet<>();
            for (ListIterator<TypeElement> iterator = lookupTypes.listIterator(); iterator.hasNext();) {
                TypeElement typeElement = iterator.next();
                if (types.contains(typeElement)) {
                    iterator.remove();
                } else {
                    types.add(typeElement);
                }
            }
        }
        List<Element> elements = new ArrayList<>(elementSet);

        NodeData node = parseNodeData(templateType, nodeType, elements, lookupTypes);

        if (node.hasErrors()) {
            return node; // error sync point
        }

        parseMethods(node, elements);

        if (node.hasErrors()) {
            return node;
        }

        List<NodeData> nodes;

        if (node.isNodeContainer()) {
            nodes = splitNodeData(node);
        } else {
            nodes = new ArrayList<>();
            nodes.add(node);
        }

        for (NodeData splittedNode : nodes) {
            if (templateType.getModifiers().contains(Modifier.PRIVATE) && splittedNode.getSpecializations().size() > 0) {
                splittedNode.addError("Classes containing a @%s annotation must not be private.", Specialization.class.getSimpleName());
            }

            finalizeSpecializations(elements, splittedNode);
            verifyNode(splittedNode, elements);
            createPolymorphicSpecializations(splittedNode);
            assignShortCircuitsToSpecializations(splittedNode);
        }

        if (node.isNodeContainer()) {
            node.setDeclaredNodes(nodes);
            node.setSpecializationListeners(new ArrayList<SpecializationListenerData>());
            node.setSpecializations(new ArrayList<SpecializationData>());
        }
        return node;
    }

    private void createPolymorphicSpecializations(NodeData node) {
        if (!node.needsRewrites(context) || !node.isPolymorphic()) {
            node.setPolymorphicSpecializations(Collections.<SpecializationData> emptyList());
            return;
        }

        Signature genericSignature = node.getGenericSpecialization().getSignature();
        Set<Signature> signatures = new TreeSet<>();

        for (SpecializationData specialization1 : node.getSpecializations()) {
            Signature signature = specialization1.getSignature();

            for (SpecializationData specialization2 : node.getSpecializations()) {
                if (specialization1 == specialization2) {
                    continue;
                }
                signatures.add(signature.combine(genericSignature, specialization2.getSignature()));
            }
        }

        while (true) {
            List<Signature> newSignatures = new ArrayList<>();
            for (Signature signature1 : signatures) {
                for (Signature signature2 : signatures) {
                    if (signature1 == signature2) {
                        continue;
                    }
                    newSignatures.add(signature1.combine(genericSignature, signature2));
                }
            }
            if (!signatures.addAll(newSignatures)) {
                break;
            }
        }

        List<Signature> sortedSignatures = new ArrayList<>(signatures);

        SpecializationData polymorphicGeneric = null;
        List<SpecializationData> specializations = new ArrayList<>();
        SpecializationData generic = node.getGenericSpecialization();
        for (Signature signature : sortedSignatures) {
            SpecializationData specialization = new SpecializationData(generic, false, false, true);

            for (Iterator<ActualParameter> iterator = specialization.getParameters().iterator(); iterator.hasNext();) {
                ActualParameter param = iterator.next();
                if (param.getSpecification().isLocal()) {
                    iterator.remove();
                }
            }

            specialization.forceFrame(context.getTruffleTypes().getFrame());
            specialization.setNode(node);
            specialization.updateSignature(signature);
            specializations.add(specialization);

            if (genericSignature.equals(signature)) {
                polymorphicGeneric = specialization;
            }
        }

        node.setGenericPolymorphicSpecialization(polymorphicGeneric);
        node.setPolymorphicSpecializations(specializations);
    }

    private NodeData parseNodeData(TypeElement templateType, TypeMirror nodeType, List<? extends Element> elements, List<TypeElement> typeHierarchy) {
        NodeData nodeData = new NodeData(templateType, templateType.getSimpleName().toString());

        AnnotationMirror typeSystemMirror = findFirstAnnotation(typeHierarchy, TypeSystemReference.class);
        if (typeSystemMirror == null) {
            nodeData.addError("No @%s annotation found in type hierarchy of %s.", TypeSystemReference.class.getSimpleName(), Utils.getQualifiedName(nodeType));
            return nodeData;
        }

        TypeMirror typeSytemType = Utils.getAnnotationValue(TypeMirror.class, typeSystemMirror, "value");
        final TypeSystemData typeSystem = (TypeSystemData) context.getTemplate(typeSytemType, true);
        if (typeSystem == null) {
            nodeData.addError("The used type system '%s' is invalid or not a Node.", Utils.getQualifiedName(typeSytemType));
            return nodeData;
        }

        AnnotationMirror polymorphicMirror = findFirstAnnotation(typeHierarchy, PolymorphicLimit.class);
        if (polymorphicMirror != null) {
            AnnotationValue limitValue = Utils.getAnnotationValue(polymorphicMirror, "value");
            int polymorphicLimit = Utils.getAnnotationValue(Integer.class, polymorphicMirror, "value");
            if (polymorphicLimit < 1) {
                nodeData.addError(limitValue, "Invalid polymorphic limit %s.", polymorphicLimit);
            }
            nodeData.setPolymorphicDepth(polymorphicLimit);
        }

        List<String> assumptionsList = new ArrayList<>();
        for (int i = typeHierarchy.size() - 1; i >= 0; i--) {
            TypeElement type = typeHierarchy.get(i);
            AnnotationMirror assumptions = Utils.findAnnotationMirror(context.getEnvironment(), type, NodeAssumptions.class);
            if (assumptions != null) {
                List<String> assumptionStrings = Utils.getAnnotationValueList(String.class, assumptions, "value");
                for (String string : assumptionStrings) {
                    if (assumptionsList.contains(string)) {
                        assumptionsList.remove(string);
                    }
                    assumptionsList.add(string);
                }
            }
        }
        AnnotationMirror nodeInfoMirror = findFirstAnnotation(typeHierarchy, NodeInfo.class);
        if (nodeInfoMirror != null) {
            nodeData.setShortName(Utils.getAnnotationValue(String.class, nodeInfoMirror, "shortName"));
        }

        nodeData.setAssumptions(new ArrayList<>(assumptionsList));
        nodeData.setNodeType(nodeType);
        AnnotationMirror nodeContainer = findFirstAnnotation(typeHierarchy, NodeContainer.class);
        nodeData.setNodeContainer(nodeContainer != null);
        nodeData.setTypeSystem(typeSystem);
        nodeData.setFields(parseFields(typeHierarchy, elements));
        parsedNodes.put(Utils.getQualifiedName(templateType), nodeData);
        // parseChildren invokes cyclic parsing.
        nodeData.setChildren(parseChildren(nodeData, elements, typeHierarchy));
        nodeData.setExecutableTypes(groupExecutableTypes(new ExecutableTypeMethodParser(context, nodeData).parse(elements)));

        return nodeData;
    }

    private List<NodeFieldData> parseFields(List<TypeElement> typeHierarchy, List<? extends Element> elements) {
        Set<String> names = new HashSet<>();

        List<NodeFieldData> fields = new ArrayList<>();
        for (VariableElement field : ElementFilter.fieldsIn(elements)) {
            if (field.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (field.getModifiers().contains(Modifier.PUBLIC) || field.getModifiers().contains(Modifier.PROTECTED)) {
                String name = field.getSimpleName().toString();
                fields.add(new NodeFieldData(field, null, field.asType(), name, false));
                names.add(name);
            }
        }

        List<TypeElement> reversedTypeHierarchy = new ArrayList<>(typeHierarchy);
        Collections.reverse(reversedTypeHierarchy);
        for (TypeElement typeElement : reversedTypeHierarchy) {
            AnnotationMirror nodeChildrenMirror = Utils.findAnnotationMirror(processingEnv, typeElement, NodeFields.class);
            List<AnnotationMirror> children = Utils.collectAnnotations(context, nodeChildrenMirror, "value", typeElement, NodeField.class);

            for (AnnotationMirror mirror : children) {
                String name = Utils.firstLetterLowerCase(Utils.getAnnotationValue(String.class, mirror, "name"));
                TypeMirror type = Utils.getAnnotationValue(TypeMirror.class, mirror, "type");

                NodeFieldData field = new NodeFieldData(typeElement, mirror, type, name, true);
                if (name.isEmpty()) {
                    field.addError(Utils.getAnnotationValue(mirror, "name"), "Field name cannot be empty.");
                } else if (names.contains(name)) {
                    field.addError(Utils.getAnnotationValue(mirror, "name"), "Duplicate field name '%s'.", name);
                }
                names.add(name);

                fields.add(field);
            }
        }

        for (NodeFieldData nodeFieldData : fields) {
            nodeFieldData.setGetter(findGetter(elements, nodeFieldData.getName(), nodeFieldData.getType()));
        }

        return fields;
    }

    private List<NodeChildData> parseChildren(NodeData node, List<? extends Element> elements, final List<TypeElement> typeHierarchy) {
        Set<String> shortCircuits = new HashSet<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, method, ShortCircuit.class);
            if (mirror != null) {
                shortCircuits.add(Utils.getAnnotationValue(String.class, mirror, "value"));
            }
        }
        Map<String, TypeMirror> castNodeTypes = new HashMap<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, method, CreateCast.class);
            if (mirror != null) {
                List<String> children = (Utils.getAnnotationValueList(String.class, mirror, "value"));
                if (children != null) {
                    for (String child : children) {
                        castNodeTypes.put(child, method.getReturnType());
                    }
                }
            }
        }

        List<NodeChildData> parsedChildren = new ArrayList<>();
        List<TypeElement> typeHierarchyReversed = new ArrayList<>(typeHierarchy);
        Collections.reverse(typeHierarchyReversed);
        for (TypeElement type : typeHierarchyReversed) {
            AnnotationMirror nodeClassMirror = Utils.findAnnotationMirror(processingEnv, type, NodeContainer.class);
            AnnotationMirror nodeChildrenMirror = Utils.findAnnotationMirror(processingEnv, type, NodeChildren.class);

            TypeMirror nodeClassType = type.getSuperclass();
            if (!Utils.isAssignable(context, nodeClassType, context.getTruffleTypes().getNode())) {
                nodeClassType = null;
            }

            if (nodeClassMirror != null) {
                nodeClassType = inheritType(nodeClassMirror, "value", nodeClassType);
            }

            List<AnnotationMirror> children = Utils.collectAnnotations(context, nodeChildrenMirror, "value", type, NodeChild.class);
            int index = 0;
            for (AnnotationMirror childMirror : children) {
                String name = Utils.getAnnotationValue(String.class, childMirror, "value");
                if (name.equals("")) {
                    name = "child" + index;
                }

                Cardinality cardinality = Cardinality.ONE;

                TypeMirror childType = inheritType(childMirror, "type", nodeClassType);
                if (childType.getKind() == TypeKind.ARRAY) {
                    cardinality = Cardinality.MANY;
                }

                TypeMirror originalChildType = childType;
                TypeMirror castNodeType = castNodeTypes.get(name);
                if (castNodeType != null) {
                    childType = castNodeType;
                }

                Element getter = findGetter(elements, name, childType);

                ExecutionKind kind = ExecutionKind.DEFAULT;
                if (shortCircuits.contains(name)) {
                    kind = ExecutionKind.SHORT_CIRCUIT;
                }

                NodeChildData nodeChild = new NodeChildData(type, childMirror, name, childType, originalChildType, getter, cardinality, kind);

                parsedChildren.add(nodeChild);

                verifyNodeChild(nodeChild);
                if (nodeChild.hasErrors()) {
                    continue;
                }

                NodeData fieldNodeData = resolveNode(Utils.fromTypeMirror(childType));
                nodeChild.setNode(fieldNodeData);
                if (fieldNodeData == null) {
                    nodeChild.addError("Node type '%s' is invalid or not a valid Node.", Utils.getQualifiedName(childType));
                } else if (!Utils.typeEquals(fieldNodeData.getTypeSystem().getTemplateType().asType(), (node.getTypeSystem().getTemplateType().asType()))) {
                    nodeChild.addError("The @%s of the node and the @%s of the @%s does not match. %s != %s. ", TypeSystem.class.getSimpleName(), TypeSystem.class.getSimpleName(),
                                    NodeChild.class.getSimpleName(), Utils.getSimpleName(node.getTypeSystem().getTemplateType()), Utils.getSimpleName(fieldNodeData.getTypeSystem().getTemplateType()));
                }
                index++;
            }
        }

        List<NodeChildData> filteredChildren = new ArrayList<>();
        Set<String> encounteredNames = new HashSet<>();
        for (int i = parsedChildren.size() - 1; i >= 0; i--) {
            NodeChildData child = parsedChildren.get(i);
            if (!encounteredNames.contains(child.getName())) {
                filteredChildren.add(0, child);
                encounteredNames.add(child.getName());
            }
        }

        for (NodeChildData child : filteredChildren) {
            List<String> executeWithStrings = Utils.getAnnotationValueList(String.class, child.getMessageAnnotation(), "executeWith");
            AnnotationValue executeWithValue = Utils.getAnnotationValue(child.getMessageAnnotation(), "executeWith");
            List<NodeChildData> executeWith = new ArrayList<>();
            for (String executeWithString : executeWithStrings) {

                if (child.getName().equals(executeWithString)) {
                    child.addError(executeWithValue, "The child node '%s' cannot be executed with itself.", executeWithString);
                    continue;
                }

                NodeChildData found = null;
                boolean before = true;
                for (NodeChildData resolveChild : filteredChildren) {
                    if (resolveChild == child) {
                        before = false;
                        continue;
                    }
                    if (resolveChild.getName().equals(executeWithString)) {
                        found = resolveChild;
                        break;
                    }
                }

                if (found == null) {
                    child.addError(executeWithValue, "The child node '%s' cannot be executed with '%s'. The child node was not found.", child.getName(), executeWithString);
                    continue;
                } else if (!before) {
                    child.addError(executeWithValue, "The child node '%s' cannot be executed with '%s'. The node %s is executed after the current node.", child.getName(), executeWithString,
                                    executeWithString);
                    continue;
                }
                executeWith.add(found);
            }
            child.setExecuteWith(executeWith);
            if (child.getNodeData() == null) {
                continue;
            }

            List<ExecutableTypeData> types = child.findGenericExecutableTypes(context);
            if (types.isEmpty()) {
                child.addError(executeWithValue, "No generic execute method found with %s evaluated arguments for node type %s.", executeWith.size(), Utils.getSimpleName(child.getNodeType()));
                continue;
            }
        }

        return filteredChildren;
    }

    private void parseMethods(final NodeData node, List<Element> elements) {
        node.setShortCircuits(new ShortCircuitParser(context, node).parse(elements));
        node.setSpecializationListeners(new SpecializationListenerParser(context, node).parse(elements));
        List<SpecializationData> generics = new GenericParser(context, node).parse(elements);
        List<SpecializationData> specializations = new SpecializationMethodParser(context, node).parse(elements);
        node.setCasts(new CreateCastParser(context, node).parse(elements));

        List<SpecializationData> allSpecializations = new ArrayList<>();
        allSpecializations.addAll(generics);
        allSpecializations.addAll(specializations);

        node.setSpecializations(allSpecializations);
    }

    private static List<NodeData> splitNodeData(NodeData node) {
        SortedMap<String, List<SpecializationData>> groupedSpecializations = groupByNodeId(node.getSpecializations());
        SortedMap<String, List<SpecializationListenerData>> groupedListeners = groupByNodeId(node.getSpecializationListeners());
        SortedMap<String, List<CreateCastData>> groupedCasts = groupByNodeId(node.getCasts());

        Set<String> ids = new TreeSet<>();
        ids.addAll(groupedSpecializations.keySet());
        ids.addAll(groupedListeners.keySet());

        List<NodeData> splitted = new ArrayList<>();
        for (String id : ids) {
            List<SpecializationData> specializations = groupedSpecializations.get(id);
            List<SpecializationListenerData> listeners = groupedListeners.get(id);
            List<CreateCastData> casts = groupedCasts.get(id);

            if (specializations == null) {
                specializations = new ArrayList<>();
            }

            if (listeners == null) {
                listeners = new ArrayList<>();
            }

            String nodeId = node.getNodeId();
            if (nodeId.endsWith("Node") && !nodeId.equals("Node")) {
                nodeId = nodeId.substring(0, nodeId.length() - 4);
            }
            String newNodeId = nodeId + Utils.firstLetterUpperCase(id);
            NodeData copy = new NodeData(node, id, newNodeId);

            copy.setSpecializations(specializations);
            copy.setSpecializationListeners(listeners);
            copy.setCasts(casts);

            splitted.add(copy);
        }

        node.setSpecializations(new ArrayList<SpecializationData>());
        node.setSpecializationListeners(new ArrayList<SpecializationListenerData>());
        node.setCasts(new ArrayList<CreateCastData>());

        return splitted;
    }

    private void finalizeSpecializations(List<Element> elements, final NodeData node) {
        List<SpecializationData> specializations = new ArrayList<>(node.getSpecializations());

        if (specializations.isEmpty()) {
            return;
        }

        for (SpecializationData specialization : specializations) {
            matchGuards(elements, specialization);
        }

        List<SpecializationData> generics = new ArrayList<>();
        for (SpecializationData spec : specializations) {
            if (spec.isGeneric()) {
                generics.add(spec);
            }
        }

        if (generics.size() == 1 && specializations.size() == 1) {
            for (SpecializationData generic : generics) {
                generic.addError("@%s defined but no @%s.", Generic.class.getSimpleName(), Specialization.class.getSimpleName());
            }
        }

        SpecializationData genericSpecialization = null;
        if (generics.size() > 1) {
            for (SpecializationData generic : generics) {
                generic.addError("Only @%s is allowed per operation.", Generic.class.getSimpleName());
            }
            return;
        } else if (generics.size() == 1) {
            genericSpecialization = generics.get(0);
        } else if (node.needsRewrites(context)) {
            genericSpecialization = createGenericSpecialization(node, specializations);
            specializations.add(genericSpecialization);
        }

        if (genericSpecialization != null) {
            for (ActualParameter parameter : genericSpecialization.getReturnTypeAndParameters()) {
                if (Utils.isObject(parameter.getType())) {
                    continue;
                }
                Set<String> types = new HashSet<>();
                for (SpecializationData specialization : specializations) {
                    ActualParameter actualParameter = specialization.findParameter(parameter.getLocalName());
                    if (actualParameter != null) {
                        types.add(Utils.getQualifiedName(actualParameter.getType()));
                    }
                }
                if (types.size() > 1) {
                    genericSpecialization.replaceParameter(parameter.getLocalName(), new ActualParameter(parameter, node.getTypeSystem().getGenericTypeData()));
                }
            }
            TemplateMethod uninializedMethod = new TemplateMethod("Uninitialized", node, genericSpecialization.getSpecification(), null, null, genericSpecialization.getReturnType(),
                            genericSpecialization.getParameters());
            // should not use messages from generic specialization
            uninializedMethod.getMessages().clear();
            specializations.add(new SpecializationData(uninializedMethod, false, true, false));
        }

        Collections.sort(specializations);

        node.setSpecializations(specializations);

        List<SpecializationData> needsId = new ArrayList<>();
        for (SpecializationData specialization : specializations) {
            if (specialization.isGeneric()) {
                specialization.setId("Generic");
            } else if (specialization.isUninitialized()) {
                specialization.setId("Uninitialized");
            } else {
                needsId.add(specialization);
            }
        }

        // verify specialization parameter length
        if (verifySpecializationParameters(node)) {
            List<String> ids = calculateSpecializationIds(needsId);
            for (int i = 0; i < ids.size(); i++) {
                needsId.get(i).setId(ids.get(i));
            }
        }

        // calculate reachability
        SpecializationData prev = null;
        int specializationCount = 0;
        boolean reachable = true;
        for (SpecializationData specialization : specializations) {
            if (specialization.isUninitialized()) {
                specialization.setReachable(true);
                continue;
            }
            if (prev != null && prev.equalsGuards(specialization) && prev.getExceptions().isEmpty()) {
                specialization.addError("%s is not reachable.", specialization.isGeneric() ? "Generic" : "Specialization");
            } else if (!reachable && specialization.getMethod() != null) {
                specialization.addError("%s is not reachable.", specialization.isGeneric() ? "Generic" : "Specialization");
            }
            specialization.setReachable(reachable);
            if (!specialization.hasRewrite(context)) {
                reachable = false;
            }
            if (!specialization.isGeneric()) {
                specializationCount++;
            }
            prev = specialization;
        }

        // initialize polymorphic depth
        if (node.getPolymorphicDepth() < 0) {
            node.setPolymorphicDepth(specializationCount - 1);
        }

        // reduce polymorphicness if generic is not reachable
        if (node.getGenericSpecialization() != null && !node.getGenericSpecialization().isReachable()) {
            node.setPolymorphicDepth(1);
        }
    }

    private SpecializationData createGenericSpecialization(final NodeData node, List<SpecializationData> specializations) {
        SpecializationData genericSpecialization;
        SpecializationData specialization = specializations.get(0);
        GenericParser parser = new GenericParser(context, node);
        MethodSpec specification = parser.createDefaultMethodSpec(specialization.getMethod(), null, true, null);

        List<ActualParameter> parameters = new ArrayList<>();
        for (ActualParameter parameter : specialization.getReturnTypeAndParameters()) {
            if (!parameter.getSpecification().isSignature()) {
                parameters.add(new ActualParameter(parameter));
                continue;
            }
            NodeData childNode = node;
            NodeChildData child = node.findChild(parameter.getSpecification().getName());
            if (child != null) {
                childNode = child.getNodeData();
            }

            TypeData genericType = null;

            Set<TypeData> types = new HashSet<>();
            for (SpecializationData otherSpecialization : specializations) {
                ActualParameter otherParameter = otherSpecialization.findParameter(parameter.getLocalName());
                if (otherParameter != null) {
                    types.add(otherParameter.getTypeSystemType());
                }
            }

            assert !types.isEmpty();

            if (types.size() == 1) {
                ExecutableTypeData executable = childNode.findExecutableType(types.iterator().next(), 0);
                if (executable != null && !executable.hasUnexpectedValue(context)) {
                    genericType = types.iterator().next();
                } else {
                    genericType = childNode.findAnyGenericExecutableType(context, 0).getType();
                }
            } else {
                genericType = childNode.findAnyGenericExecutableType(context, 0).getType();
            }

            parameters.add(new ActualParameter(parameter, genericType));
        }
        ActualParameter returnType = parameters.get(0);
        parameters = parameters.subList(1, parameters.size());

        TemplateMethod genericMethod = new TemplateMethod("Generic", node, specification, null, null, returnType, parameters);
        genericSpecialization = new SpecializationData(genericMethod, true, false, false);
        return genericSpecialization;
    }

    private void assignShortCircuitsToSpecializations(NodeData node) {
        Map<String, List<ShortCircuitData>> groupedShortCircuits = groupShortCircuits(node.getShortCircuits());

        boolean valid = true;
        for (NodeChildData field : node.filterFields(ExecutionKind.SHORT_CIRCUIT)) {
            String valueName = field.getName();
            List<ShortCircuitData> availableCircuits = groupedShortCircuits.get(valueName);

            if (availableCircuits == null || availableCircuits.isEmpty()) {
                node.addError("@%s method for short cut value '%s' required.", ShortCircuit.class.getSimpleName(), valueName);
                valid = false;
                continue;
            }

            boolean sameMethodName = true;
            String methodName = availableCircuits.get(0).getMethodName();
            for (ShortCircuitData circuit : availableCircuits) {
                if (!circuit.getMethodName().equals(methodName)) {
                    sameMethodName = false;
                }
            }

            if (!sameMethodName) {
                for (ShortCircuitData circuit : availableCircuits) {
                    circuit.addError("All short circuits for short cut value '%s' must have the same method name.", valueName);
                }
                valid = false;
                continue;
            }

            ShortCircuitData genericCircuit = null;
            for (ShortCircuitData circuit : availableCircuits) {
                if (isGenericShortCutMethod(node, circuit)) {
                    genericCircuit = circuit;
                    break;
                }
            }

            if (genericCircuit == null) {
                node.addError("No generic @%s method available for short cut value '%s'.", ShortCircuit.class.getSimpleName(), valueName);
                valid = false;
                continue;
            }

            for (ShortCircuitData circuit : availableCircuits) {
                if (circuit != genericCircuit) {
                    circuit.setGenericShortCircuitMethod(genericCircuit);
                }
            }
        }

        if (!valid) {
            return;
        }

        NodeChildData[] fields = node.filterFields(ExecutionKind.SHORT_CIRCUIT);
        List<SpecializationData> specializations = new ArrayList<>();
        specializations.addAll(node.getSpecializations());
        specializations.addAll(node.getPolymorphicSpecializations());

        for (SpecializationData specialization : specializations) {
            List<ShortCircuitData> assignedShortCuts = new ArrayList<>(fields.length);

            for (int i = 0; i < fields.length; i++) {
                List<ShortCircuitData> availableShortCuts = groupedShortCircuits.get(fields[i].getName());

                ShortCircuitData genericShortCircuit = null;
                ShortCircuitData compatibleShortCircuit = null;
                for (ShortCircuitData circuit : availableShortCuts) {
                    if (circuit.isGeneric()) {
                        genericShortCircuit = circuit;
                    } else if (circuit.isCompatibleTo(specialization)) {
                        compatibleShortCircuit = circuit;
                    }
                }

                if (compatibleShortCircuit == null) {
                    compatibleShortCircuit = genericShortCircuit;
                }
                assignedShortCuts.add(compatibleShortCircuit);
            }
            specialization.setShortCircuits(assignedShortCuts);
        }
    }

    private void matchGuards(List<Element> elements, SpecializationData specialization) {
        if (specialization.getGuardDefinitions().isEmpty()) {
            specialization.setGuards(Collections.<GuardData> emptyList());
            return;
        }

        List<GuardData> foundGuards = new ArrayList<>();
        List<ExecutableElement> methods = ElementFilter.methodsIn(elements);
        for (String guardDefinition : specialization.getGuardDefinitions()) {
            GuardParser parser = new GuardParser(context, specialization, guardDefinition);
            List<GuardData> guards = parser.parse(methods);
            if (!guards.isEmpty()) {
                foundGuards.add(guards.get(0));
            } else {
                // error no guard found
                MethodSpec spec = parser.createSpecification(specialization.getMethod(), null);
                spec.applyTypeDefinitions("types");
                specialization.addError("Guard with method name '%s' not found. Expected signature: %n%s", guardDefinition, spec.toSignatureString("guard"));
            }
        }

        specialization.setGuards(foundGuards);

    }

    private static List<String> calculateSpecializationIds(List<SpecializationData> specializations) {
        int lastSize = -1;
        List<List<String>> signatureChunks = new ArrayList<>();
        for (SpecializationData other : specializations) {
            if (other.isUninitialized() || other.isGeneric()) {
                continue;
            }
            List<String> paramIds = new LinkedList<>();
            paramIds.add(Utils.getTypeId(other.getReturnType().getType()));
            for (ActualParameter param : other.getParameters()) {
                if (other.getNode().findChild(param.getSpecification().getName()) == null) {
                    continue;
                }
                paramIds.add(Utils.getTypeId(param.getType()));
            }
            assert lastSize == -1 || lastSize == paramIds.size();
            if (lastSize != -1 && lastSize != paramIds.size()) {
                throw new AssertionError();
            }
            signatureChunks.add(paramIds);
            lastSize = paramIds.size();
        }

        // reduce id vertically
        for (int i = 0; i < lastSize; i++) {
            String prev = null;
            boolean allSame = true;
            for (List<String> signature : signatureChunks) {
                String arg = signature.get(i);
                if (prev == null) {
                    prev = arg;
                    continue;
                } else if (!prev.equals(arg)) {
                    allSame = false;
                    break;
                }
                prev = arg;
            }

            if (allSame) {
                for (List<String> signature : signatureChunks) {
                    signature.remove(i);
                }
                lastSize--;
            }
        }

        // reduce id horizontally
        for (List<String> signature : signatureChunks) {
            if (signature.isEmpty()) {
                continue;
            }
            String prev = null;
            boolean allSame = true;
            for (String arg : signature) {
                if (prev == null) {
                    prev = arg;
                    continue;
                } else if (!prev.equals(arg)) {
                    allSame = false;
                    break;
                }
                prev = arg;
            }

            if (allSame) {
                signature.clear();
                signature.add(prev);
            }
        }

        // create signatures
        List<String> signatures = new ArrayList<>();
        for (List<String> signatureChunk : signatureChunks) {
            StringBuilder b = new StringBuilder();
            if (signatureChunk.isEmpty()) {
                b.append("Default");
            } else {
                for (String s : signatureChunk) {
                    b.append(s);
                }
            }
            signatures.add(b.toString());
        }

        Map<String, Integer> counts = new HashMap<>();
        for (String s1 : signatures) {
            Integer count = counts.get(s1);
            if (count == null) {
                count = 0;
            }
            count++;
            counts.put(s1, count);
        }

        for (String s : counts.keySet()) {
            int count = counts.get(s);
            if (count > 1) {
                int number = 0;
                for (ListIterator<String> iterator = signatures.listIterator(); iterator.hasNext();) {
                    String s2 = iterator.next();
                    if (s.equals(s2)) {
                        iterator.set(s2 + number);
                        number++;
                    }
                }
            }
        }

        return signatures;
    }

    private void verifyNode(NodeData nodeData, List<? extends Element> elements) {
        // verify order is not ambiguous
        verifySpecializationOrder(nodeData);

        verifyMissingAbstractMethods(nodeData, elements);

        verifyConstructors(nodeData);

        verifyNamingConvention(nodeData.getShortCircuits(), "needs");

        verifySpecializationThrows(nodeData);
    }

    private static void verifyNodeChild(NodeChildData nodeChild) {
        if (nodeChild.getNodeType() == null) {
            nodeChild.addError("No valid node type could be resoleved.");
        }
        // FIXME verify node child
        // FIXME verify node type set
    }

    private static void verifyMissingAbstractMethods(NodeData nodeData, List<? extends Element> originalElements) {
        if (!nodeData.needsFactory()) {
            // missing abstract methods only needs to be implemented
            // if we need go generate factory for it.
            return;
        }

        List<Element> elements = new ArrayList<>(originalElements);

        Set<Element> unusedElements = new HashSet<>(elements);
        for (TemplateMethod method : nodeData.getAllTemplateMethods()) {
            unusedElements.remove(method.getMethod());
        }

        for (NodeFieldData field : nodeData.getFields()) {
            if (field.getGetter() != null) {
                unusedElements.remove(field.getGetter());
            }
        }

        for (NodeChildData child : nodeData.getChildren()) {
            if (child.getAccessElement() != null) {
                unusedElements.remove(child.getAccessElement());
            }
        }

        for (ExecutableElement unusedMethod : ElementFilter.methodsIn(unusedElements)) {
            if (unusedMethod.getModifiers().contains(Modifier.ABSTRACT)) {
                nodeData.addError("The type %s must implement the inherited abstract method %s.", Utils.getSimpleName(nodeData.getTemplateType()), Utils.getReadableSignature(unusedMethod));
            }
        }
    }

    private void verifyConstructors(NodeData nodeData) {
        if (!nodeData.needsRewrites(context)) {
            // no specialization constructor is needed if the node never rewrites.
            return;
        }

        TypeElement type = Utils.fromTypeMirror(nodeData.getNodeType());
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements());

        boolean parametersFound = false;
        for (ExecutableElement constructor : constructors) {
            if (!constructor.getParameters().isEmpty()) {
                parametersFound = true;
            }
        }
        if (!parametersFound) {
            return;
        }
        for (ExecutableElement e : constructors) {
            if (e.getParameters().size() == 1) {
                TypeMirror firstArg = e.getParameters().get(0).asType();
                if (Utils.typeEquals(firstArg, nodeData.getNodeType())) {
                    if (e.getModifiers().contains(Modifier.PRIVATE)) {
                        nodeData.addError("The specialization constructor must not be private.");
                    } else if (constructors.size() <= 1) {
                        nodeData.addError("The specialization constructor must not be the only constructor. The definition of an alternative constructor is required.");
                    }
                    return;
                }
            }
        }

        // not found
        nodeData.addError("Specialization constructor '%s(%s previousNode) { this(...); }' is required.", Utils.getSimpleName(type), Utils.getSimpleName(type));
    }

    private static boolean verifySpecializationParameters(NodeData nodeData) {
        boolean valid = true;
        int args = -1;
        for (SpecializationData specializationData : nodeData.getSpecializations()) {
            int signatureArgs = 0;
            for (ActualParameter param : specializationData.getParameters()) {
                if (param.getSpecification().isSignature()) {
                    signatureArgs++;
                }
            }
            if (args != -1 && args != signatureArgs) {
                valid = false;
                break;
            }
            args = signatureArgs;
        }
        if (!valid) {
            for (SpecializationData specialization : nodeData.getSpecializations()) {
                specialization.addError("All specializations must have the same number of arguments.");
            }
        }
        return valid;
    }

    private static void verifySpecializationOrder(NodeData node) {
        List<SpecializationData> specializations = node.getSpecializations();
        for (int i = 0; i < specializations.size(); i++) {
            SpecializationData m1 = specializations.get(i);
            for (int j = i + 1; j < specializations.size(); j++) {
                SpecializationData m2 = specializations.get(j);
                int inferredOrder = m1.compareBySignature(m2);

                if (m1.getOrder() != Specialization.DEFAULT_ORDER && m2.getOrder() != Specialization.DEFAULT_ORDER) {
                    int specOrder = m1.getOrder() - m2.getOrder();
                    if (specOrder == 0) {
                        m1.addError("Order value %d used multiple times", m1.getOrder());
                        m2.addError("Order value %d used multiple times", m1.getOrder());
                        return;
                    } else if ((specOrder < 0 && inferredOrder > 0) || (specOrder > 0 && inferredOrder < 0)) {
                        m1.addError("Explicit order values %d and %d are inconsistent with type lattice ordering.", m1.getOrder(), m2.getOrder());
                        m2.addError("Explicit order values %d and %d are inconsistent with type lattice ordering.", m1.getOrder(), m2.getOrder());
                        return;
                    }
                } else if (inferredOrder == 0) {
                    SpecializationData m = (m1.getOrder() == Specialization.DEFAULT_ORDER ? m1 : m2);
                    m.addError("Cannot calculate a consistent order for this specialization. Define the order attribute to resolve this.");
                    return;
                }
            }
        }
    }

    private static void verifySpecializationThrows(NodeData node) {
        Map<String, SpecializationData> specializationMap = new HashMap<>();
        for (SpecializationData spec : node.getSpecializations()) {
            specializationMap.put(spec.getMethodName(), spec);
        }
        for (SpecializationData sourceSpecialization : node.getSpecializations()) {
            if (sourceSpecialization.getExceptions() != null) {
                for (SpecializationThrowsData throwsData : sourceSpecialization.getExceptions()) {
                    for (SpecializationThrowsData otherThrowsData : sourceSpecialization.getExceptions()) {
                        if (otherThrowsData != throwsData && Utils.typeEquals(otherThrowsData.getJavaClass(), throwsData.getJavaClass())) {
                            throwsData.addError("Duplicate exception type.");
                        }
                    }
                }
            }
        }
    }

    private static void verifyNamingConvention(List<? extends TemplateMethod> methods, String prefix) {
        for (int i = 0; i < methods.size(); i++) {
            TemplateMethod m1 = methods.get(i);
            if (m1.getMethodName().length() < 3 || !m1.getMethodName().startsWith(prefix)) {
                m1.addError("Naming convention: method name must start with '%s'.", prefix);
            }
        }
    }

    private static Map<Integer, List<ExecutableTypeData>> groupExecutableTypes(List<ExecutableTypeData> executableTypes) {
        Map<Integer, List<ExecutableTypeData>> groupedTypes = new TreeMap<>();
        for (ExecutableTypeData type : executableTypes) {
            int evaluatedCount = type.getEvaluatedCount();

            List<ExecutableTypeData> types = groupedTypes.get(evaluatedCount);
            if (types == null) {
                types = new ArrayList<>();
                groupedTypes.put(evaluatedCount, types);
            }
            types.add(type);
        }

        for (List<ExecutableTypeData> types : groupedTypes.values()) {
            Collections.sort(types);
        }
        return groupedTypes;
    }

    private AnnotationMirror findFirstAnnotation(List<? extends Element> elements, Class<? extends Annotation> annotation) {
        for (Element element : elements) {
            AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, element, annotation);
            if (mirror != null) {
                return mirror;
            }
        }
        return null;
    }

    private TypeMirror inheritType(AnnotationMirror annotation, String valueName, TypeMirror parentType) {
        TypeMirror inhertNodeType = context.getTruffleTypes().getNode();
        TypeMirror value = Utils.getAnnotationValue(TypeMirror.class, annotation, valueName);
        if (Utils.typeEquals(inhertNodeType, value)) {
            return parentType;
        } else {
            return value;
        }
    }

    private ExecutableElement findGetter(List<? extends Element> elements, String variableName, TypeMirror type) {
        if (type == null) {
            return null;
        }
        String methodName;
        if (Utils.typeEquals(type, context.getType(boolean.class))) {
            methodName = "is" + Utils.firstLetterUpperCase(variableName);
        } else {
            methodName = "get" + Utils.firstLetterUpperCase(variableName);
        }

        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            if (method.getSimpleName().toString().equals(methodName) && method.getParameters().size() == 0 && Utils.isAssignable(context, type, method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    private boolean isGenericShortCutMethod(NodeData node, TemplateMethod method) {
        for (ActualParameter parameter : method.getParameters()) {
            NodeChildData field = node.findChild(parameter.getSpecification().getName());
            if (field == null) {
                continue;
            }
            ExecutableTypeData found = null;
            List<ExecutableTypeData> executableElements = field.findGenericExecutableTypes(context);
            for (ExecutableTypeData executable : executableElements) {
                if (executable.getType().equalsType(parameter.getTypeSystemType())) {
                    found = executable;
                    break;
                }
            }
            if (found == null) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, List<ShortCircuitData>> groupShortCircuits(List<ShortCircuitData> shortCircuits) {
        Map<String, List<ShortCircuitData>> group = new HashMap<>();
        for (ShortCircuitData shortCircuit : shortCircuits) {
            List<ShortCircuitData> circuits = group.get(shortCircuit.getValueName());
            if (circuits == null) {
                circuits = new ArrayList<>();
                group.put(shortCircuit.getValueName(), circuits);
            }
            circuits.add(shortCircuit);
        }
        return group;
    }

    private static <M extends TemplateMethod> SortedMap<String, List<M>> groupByNodeId(List<M> methods) {
        SortedMap<String, List<M>> grouped = new TreeMap<>();
        for (M m : methods) {
            List<M> list = grouped.get(m.getId());
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(m.getId(), list);
            }
            list.add(m);
        }
        return grouped;
    }

    private static List<TypeElement> findSuperClasses(List<TypeElement> collection, TypeElement element) {
        if (element.getSuperclass() != null) {
            TypeElement superElement = Utils.fromTypeMirror(element.getSuperclass());
            if (superElement != null) {
                findSuperClasses(collection, superElement);
            }
        }
        collection.add(element);
        return collection;
    }

}
