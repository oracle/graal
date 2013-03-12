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
package com.oracle.truffle.codegen.processor.node;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.ast.*;
import com.oracle.truffle.codegen.processor.node.NodeFieldData.ExecutionKind;
import com.oracle.truffle.codegen.processor.node.NodeFieldData.FieldKind;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class NodeParser extends TemplateParser<NodeData> {

    public static final List<Class<? extends Annotation>> ANNOTATIONS = Arrays.asList(Generic.class, TypeSystemReference.class, ShortCircuit.class, Specialization.class, SpecializationListener.class);

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
        } finally {
            parsedNodes = null;
        }
        return node;
    }

    @Override
    public boolean isDelegateToRootDeclaredType() {
        return true;
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
            rootNode = new NodeData(rootType, null, rootType.getSimpleName().toString());
        }

        parsedNodes.put(typeName, rootNode);

        if (rootNode != null) {
            children.addAll(rootNode.getDeclaredChildren());
            rootNode.setDeclaredChildren(children);
        }

        if (Log.DEBUG) {
            NodeData parsed = parsedNodes.get(typeName);
            if (parsed != null) {
                String dump = parsed.dump();
                String valid = rootNode != null ? "" : " failed";
                String msg = String.format("Node parsing %s : %s", valid, dump);
                log.error(msg);
                System.out.println(msg);
            }
        }

        return rootNode;
    }

    private NodeData parseNode(TypeElement type) {
        if (Utils.findAnnotationMirror(processingEnv, type, GeneratedBy.class) != null) {
            // generated nodes should not get called again.
            return null;
        }

        AnnotationMirror methodNodes = Utils.findAnnotationMirror(processingEnv, type, NodeClass.class);

        if (methodNodes == null && !Utils.isAssignable(type.asType(), context.getTruffleTypes().getNode())) {
            return null; // not a node
        }

        TypeElement nodeType;
        boolean needsSplit;
        if (methodNodes != null) {
            needsSplit = methodNodes != null;
            nodeType = Utils.fromTypeMirror(Utils.getAnnotationValue(TypeMirror.class, methodNodes, "value"));
        } else {
            needsSplit = false;
            nodeType = type;
        }

        if (type.getModifiers().contains(Modifier.PRIVATE)) {
            return null; // not visible
        }

        NodeData nodeData = parseNodeData(type, nodeType);
        if (nodeData == null) {
            return null;
        }

        List<Element> elements = new ArrayList<>(context.getEnvironment().getElementUtils().getAllMembers(type));
        nodeData.setExtensionElements(getExtensionParser().parseAll(type, elements));
        if (nodeData.getExtensionElements() != null) {
            elements.addAll(nodeData.getExtensionElements());
        }

        if (!parseMethods(nodeData, elements)) {
            return null;
        }

        List<NodeData> nodes;
        if (needsSplit) {
            nodes = splitNodeData(nodeData);
            if (nodes == null) {
                return null;
            }
        } else {
            nodes = new ArrayList<>();
            nodes.add(nodeData);
        }

        boolean valid = true;
        for (NodeData splittedNode : nodes) {
            if (!finalizeSpecializations(splittedNode)) {
                valid = false;
            }
            if (!verifyNode(splittedNode)) {
                valid = false;
            }
        }

        if (!valid) {
            return null;
        }

        if (needsSplit) {
            nodeData.setDeclaredChildren(nodes);
            nodeData.setSpecializationListeners(new ArrayList<SpecializationListenerData>());
            nodeData.setSpecializations(new ArrayList<SpecializationData>());
            return nodeData;
        } else {
            return nodeData;
        }
    }

    private static List<NodeData> splitNodeData(NodeData node) {
        SortedMap<String, List<SpecializationData>> groupedSpecializations = groupByNodeId(node.getSpecializations());
        SortedMap<String, List<SpecializationListenerData>> groupedListeners = groupByNodeId(node.getSpecializationListeners());

        Set<String> ids = new TreeSet<>();
        ids.addAll(groupedSpecializations.keySet());
        ids.addAll(groupedListeners.keySet());

        List<NodeData> splitted = new ArrayList<>();
        for (String id : ids) {
            List<SpecializationData> specializations = groupedSpecializations.get(id);
            List<SpecializationListenerData> listeners = groupedListeners.get(id);

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

            splitted.add(copy);
        }

        node.setSpecializations(new ArrayList<SpecializationData>());
        node.setSpecializationListeners(new ArrayList<SpecializationListenerData>());

        return splitted;
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

    private boolean parseMethods(final NodeData node, List<Element> elements) {
        node.setGuards(new GuardParser(context, node, node.getTypeSystem()).parse(elements));
        node.setShortCircuits(new ShortCircuitParser(context, node).parse(elements));
        node.setSpecializationListeners(new SpecializationListenerParser(context, node).parse(elements));
        List<SpecializationData> generics = new GenericParser(context, node).parse(elements);
        List<SpecializationData> specializations = new SpecializationMethodParser(context, node).parse(elements);

        if (generics == null || specializations == null || node.getGuards() == null || node.getShortCircuits() == null || node.getSpecializationListeners() == null) {
            return false;
        }

        List<SpecializationData> allSpecializations = new ArrayList<>();
        allSpecializations.addAll(generics);
        allSpecializations.addAll(specializations);

        node.setSpecializations(allSpecializations);

        return true;
    }

    private boolean finalizeSpecializations(final NodeData node) {
        List<SpecializationData> specializations = new ArrayList<>(node.getSpecializations());

        if (specializations.isEmpty()) {
            return true;
        }

        List<SpecializationData> generics = new ArrayList<>();
        for (SpecializationData spec : specializations) {
            if (spec.isGeneric()) {
                generics.add(spec);
            }
        }

        SpecializationData genericSpecialization = null;
        if (generics.size() > 1) {
            for (SpecializationData generic : generics) {
                log.error(generic.getMethod(), "Only one method with @%s is allowed per operation.", Generic.class.getSimpleName());
            }
            return false;
        } else if (generics.size() == 1) {
            genericSpecialization = generics.get(0);
        } else {
            // TODO support generation of generic if not ambiguous.
        }

        if (specializations.size() > 1 && genericSpecialization == null) {
            log.error(node.getTemplateType(), "Need a @%s method.", Generic.class.getSimpleName());
            return false;
        }

        if (genericSpecialization != null) {
            CodeExecutableElement uninitializedMethod = new CodeExecutableElement(Utils.modifiers(Modifier.PUBLIC), context.getType(void.class), "doUninitialized");
            TemplateMethod uninializedMethod = new TemplateMethod(genericSpecialization.getId(), node, genericSpecialization.getSpecification(), uninitializedMethod,
                            genericSpecialization.getMarkerAnnotation(), genericSpecialization.getReturnType(), genericSpecialization.getParameters());
            specializations.add(new SpecializationData(uninializedMethod, false, true));
        }

        Collections.sort(specializations, new Comparator<SpecializationData>() {

            @Override
            public int compare(SpecializationData o1, SpecializationData o2) {
                return compareSpecialization(node.getTypeSystem(), o1, o2);
            }
        });

        node.setSpecializations(specializations);

        for (SpecializationData specialization : specializations) {
            specialization.setId(findUniqueSpecializationId(specialization));
        }

        return true;
    }

    private static String findUniqueSpecializationId(SpecializationData specialization) {

        String name;
        if (specialization.isGeneric()) {
            name = "Generic";
        } else if (specialization.isUninitialized()) {
            name = "Uninitialized";
        } else {
            List<SpecializationData> specializations = new ArrayList<>(specialization.getNode().getSpecializations());
            for (ListIterator<SpecializationData> iterator = specializations.listIterator(); iterator.hasNext();) {
                SpecializationData data = iterator.next();
                if (data.isGeneric() || data.isUninitialized()) {
                    iterator.remove();
                }
            }

            Map<ParameterSpec, Set<String>> usedIds = new HashMap<>();
            for (SpecializationData other : specializations) {
                for (ActualParameter param : other.getReturnTypeAndParameters()) {
                    if (other.getNode().findField(param.getSpecification().getName()) == null) {
                        continue;
                    }

                    Set<String> types = usedIds.get(param.getSpecification());
                    if (types == null) {
                        types = new HashSet<>();
                        usedIds.put(param.getSpecification(), types);
                    }
                    types.add(Utils.getTypeId(param.getActualType()));
                }
            }

            List<ParameterSpec> ambiguousSpecs = new ArrayList<>();
            for (ActualParameter param : specialization.getReturnTypeAndParameters()) {
                Set<String> ids = usedIds.get(param.getSpecification());
                if (ids != null && ids.size() > 1) {
                    ambiguousSpecs.add(param.getSpecification());
                }
            }

            String specializationId = findSpecializationId(specialization, ambiguousSpecs);
            int specializationIndex = 0;
            int totalIndex = 0;

            for (SpecializationData other : specializations) {
                String id = findSpecializationId(other, ambiguousSpecs);
                if (id.equals(specializationId)) {
                    totalIndex++;
                    if (specialization == other) {
                        specializationIndex = totalIndex;
                    }
                }
            }

            if (specializationIndex != totalIndex) {
                name = specializationId + specializationIndex;
            } else {
                name = specializationId;
            }
        }
        return name;
    }

    private static String findSpecializationId(SpecializationData specialization, List<ParameterSpec> specs) {
        boolean allSame = true;
        ActualParameter prevParam = specialization.getReturnType();
        for (ParameterSpec spec : specs) {
            ActualParameter param = specialization.findParameter(spec);
            if (!Utils.typeEquals(prevParam.getActualType(), param.getActualType())) {
                allSame = false;
                break;
            }
            prevParam = param;
        }

        if (allSame) {
            return Utils.getTypeId(prevParam.getActualType());
        } else {
            StringBuilder nameBuilder = new StringBuilder();
            nameBuilder.append(Utils.getTypeId(prevParam.getActualType()));
            for (ParameterSpec spec : specs) {
                ActualParameter param = specialization.findParameter(spec);
                nameBuilder.append(Utils.getTypeId(param.getActualType()));
            }
            return nameBuilder.toString();
        }
    }

    private boolean verifyNode(NodeData nodeData) {
        // verify specialization parameter length
        if (!verifySpecializationParameters(nodeData)) {
            return false;
        }

        // verify order is not ambiguous
        if (!verifySpecializationOrder(nodeData)) {
            return false;
        }

        if (!verifyMissingAbstractMethods(nodeData)) {
            return false;
        }

        if (!assignShortCircuitsToSpecializations(nodeData)) {
            return false;
        }

        if (!verifyConstructors(nodeData)) {
            return false;
        }

// if (!verifyNamingConvention(specializations, "do")) {
// return null;
// }
//
// if (!verifyNamesUnique(specializations)) {
// return null;
// }

        if (!verifyNamingConvention(nodeData.getShortCircuits(), "needs")) {
            return false;
        }

        if (!verifySpecializationThrows(nodeData)) {
            return false;
        }

        return true;
    }

    private NodeData parseNodeData(TypeElement templateType, TypeElement nodeType) {
        List<Element> elements = new ArrayList<>(context.getEnvironment().getElementUtils().getAllMembers(nodeType));
        List<TypeElement> typeHierarchy = findSuperClasses(new ArrayList<TypeElement>(), nodeType);
        Collections.reverse(typeHierarchy);

        AnnotationMirror typeSystemMirror = findFirstAnnotation(typeHierarchy, TypeSystemReference.class);
        if (typeSystemMirror == null) {
            log.error(templateType, "No @%s annotation found in type hierarchy of %s.", TypeSystemReference.class.getSimpleName(), nodeType.getQualifiedName().toString());
            return null;
        }

        TypeMirror typeSytemType = Utils.getAnnotationValue(TypeMirror.class, typeSystemMirror, "value");
        final TypeSystemData typeSystem = (TypeSystemData) context.getTemplate(typeSytemType, true);
        if (typeSystem == null) {
            log.error(templateType, "The used type system '%s' is invalid.", Utils.getQualifiedName(typeSytemType));
            return null;
        }

        NodeData nodeData = new NodeData(templateType, typeSystem, templateType.getSimpleName().toString());
        nodeData.setNodeType(nodeType.asType());

        List<ExecutableTypeData> executableTypes = filterExecutableTypes(new ExecutableTypeMethodParser(context, nodeData).parse(elements));

        nodeData.setExecutableTypes(executableTypes);

        parsedNodes.put(Utils.getQualifiedName(templateType), nodeData);

        List<NodeFieldData> fields = parseFields(nodeData, elements, typeHierarchy);
        if (fields == null) {
            return null;
        }
        nodeData.setFields(fields);

        return nodeData;
    }

    private boolean verifySpecializationParameters(NodeData nodeData) {
        boolean valid = true;
        int args = -1;
        for (SpecializationData specializationData : nodeData.getSpecializations()) {
            int specializationArgs = 0;
            for (ActualParameter param : specializationData.getParameters()) {
                if (!param.getSpecification().isOptional()) {
                    specializationArgs++;
                }
            }
            if (args != -1 && args != specializationArgs) {
                valid = false;
                break;
            }
            args = specializationArgs;
        }
        if (!valid) {
            for (SpecializationData specialization : nodeData.getSpecializations()) {
                context.getLog().error(specialization.getMethod(), specialization.getMarkerAnnotation(), "All specializations must have the same number of arguments.");
            }
        }
        return valid;
    }

    private boolean verifyMissingAbstractMethods(NodeData nodeData) {
        if (nodeData.needsFactory()) {
            // missing abstract methods only needs to be implemented
            // if we need go generate factory for it.
            return true;
        }

        List<Element> elements = new ArrayList<>(context.getEnvironment().getElementUtils().getAllMembers(nodeData.getTemplateType()));

        Set<Element> unusedElements = new HashSet<>(elements);
        for (TemplateMethod method : nodeData.getAllTemplateMethods()) {
            unusedElements.remove(method.getMethod());
        }
        if (nodeData.getExtensionElements() != null) {
            unusedElements.removeAll(nodeData.getExtensionElements());
        }

        boolean valid = true;
        for (ExecutableElement unusedMethod : ElementFilter.methodsIn(unusedElements)) {
            if (unusedMethod.getModifiers().contains(Modifier.ABSTRACT)) {
                context.getLog().error(nodeData.getTemplateType(), "The type %s must implement the inherited abstract method %s.", Utils.getSimpleName(nodeData.getTemplateType()),
                                Utils.getReadableSignature(unusedMethod));
                valid = false;
            }
        }

        return valid;
    }

    private boolean verifyConstructors(NodeData nodeData) {
        TypeElement type = Utils.fromTypeMirror(nodeData.getNodeType());
        if (!nodeData.needsRewrites(context)) {
            // no specialization constructor is needed if the node never rewrites.
            return true;
        }

        List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements());
        for (ExecutableElement e : constructors) {
            if (e.getParameters().size() == 1) {
                TypeMirror firstArg = e.getParameters().get(0).asType();
                if (Utils.typeEquals(firstArg, nodeData.getNodeType())) {
                    if (e.getModifiers().contains(Modifier.PRIVATE)) {
                        context.getLog().error(e, "The specialization constructor must not be private.");
                        return false;
                    } else if (constructors.size() <= 1) {
                        context.getLog().error(e, "The specialization constructor must not be the only constructor. The definition of an alternative constructor is required.");
                        return false;
                    }
                    return true;
                }
            }
        }

        // not found
        context.getLog().error(type, "Specialization constructor '%s(%s previousNode) { this(...); }' is required.", Utils.getSimpleName(type), Utils.getSimpleName(type));
        return false;
    }

    private static List<ExecutableTypeData> filterExecutableTypes(List<ExecutableTypeData> executableTypes) {
        List<ExecutableTypeData> filteredExecutableTypes = new ArrayList<>();
        for (ExecutableTypeData t1 : executableTypes) {
            boolean add = true;
            for (ExecutableTypeData t2 : executableTypes) {
                if (t1 == t2) {
                    continue;
                }
                if (Utils.typeEquals(t1.getType().getPrimitiveType(), t2.getType().getPrimitiveType())) {
                    if (t1.isFinal() && !t2.isFinal()) {
                        add = false;
                    }
                }
            }
            if (add) {
                filteredExecutableTypes.add(t1);
            }
        }

        Collections.sort(filteredExecutableTypes, new Comparator<ExecutableTypeData>() {

            @Override
            public int compare(ExecutableTypeData o1, ExecutableTypeData o2) {
                int index1 = o1.getTypeSystem().findType(o1.getType());
                int index2 = o2.getTypeSystem().findType(o2.getType());
                if (index1 == -1 || index2 == -1) {
                    return 0;
                }
                return index1 - index2;
            }
        });
        return filteredExecutableTypes;
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

    private List<NodeFieldData> parseFields(NodeData nodeData, List<? extends Element> elements, final List<TypeElement> typeHierarchy) {
        AnnotationMirror executionOrderMirror = findFirstAnnotation(typeHierarchy, ExecuteChildren.class);
        List<String> executionDefinition = null;
        if (executionOrderMirror != null) {
            executionDefinition = new ArrayList<>();
            for (String object : Utils.getAnnotationValueList(String.class, executionOrderMirror, "value")) {
                executionDefinition.add(object);
            }
        }

        Set<String> shortCircuits = new HashSet<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            AnnotationMirror mirror = Utils.findAnnotationMirror(processingEnv, method, ShortCircuit.class);
            if (mirror != null) {
                shortCircuits.add(Utils.getAnnotationValue(String.class, mirror, "value"));
            }
        }

        boolean valid = true;

        List<NodeFieldData> fields = new ArrayList<>();
        for (VariableElement var : ElementFilter.fieldsIn(elements)) {
            if (var.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }

            if (executionDefinition != null) {
                if (!executionDefinition.contains(var.getSimpleName().toString())) {
                    continue;
                }
            }

            NodeFieldData field = parseField(nodeData, var, shortCircuits);
            if (field != null) {
                if (field.getExecutionKind() != ExecutionKind.IGNORE) {
                    fields.add(field);
                }
            } else {
                valid = false;
            }
        }

        // TODO parse getters
        if (!valid) {
            return null;
        }

        sortByExecutionOrder(fields, executionDefinition == null ? Collections.<String> emptyList() : executionDefinition, typeHierarchy);
        return fields;
    }

    private NodeFieldData parseField(NodeData parentNodeData, VariableElement var, Set<String> foundShortCircuits) {
        AnnotationMirror childMirror = Utils.findAnnotationMirror(processingEnv, var, Child.class);
        AnnotationMirror childrenMirror = Utils.findAnnotationMirror(processingEnv, var, Children.class);

        FieldKind kind;

        ExecutionKind execution;
        if (foundShortCircuits.contains(var.getSimpleName().toString())) {
            execution = ExecutionKind.SHORT_CIRCUIT;
        } else {
            execution = ExecutionKind.DEFAULT;
        }

        AnnotationMirror mirror;
        TypeMirror nodeType;

        if (childMirror != null) {
            mirror = childMirror;
            nodeType = var.asType();
            kind = FieldKind.CHILD;
        } else if (childrenMirror != null) {
            mirror = childrenMirror;
            nodeType = getComponentType(var.asType());
            kind = FieldKind.CHILDREN;
        } else {
            execution = ExecutionKind.IGNORE;
            nodeType = null;
            mirror = null;
            kind = null;
        }

        NodeData fieldNodeData = null;
        if (nodeType != null) {
            fieldNodeData = resolveNode(Utils.fromTypeMirror(nodeType));
            Element errorElement = Utils.typeEquals(parentNodeData.getTemplateType().asType(), var.getEnclosingElement().asType()) ? var : parentNodeData.getTemplateType();

            if (fieldNodeData == null) {
                // TODO redirect errors from resolve.
                context.getLog().error(errorElement, "Node type '%s' is invalid.", Utils.getQualifiedName(nodeType));
                return null;
            } else if (fieldNodeData.findGenericExecutableTypes(context).isEmpty()) {
                // TODO better error handling for (no or multiple?)
                context.getLog().error(errorElement, "No executable generic types found for node '%s'.", Utils.getQualifiedName(nodeType));
                return null;
            }

            // TODO correct handling of access elements
            if (var.getModifiers().contains(Modifier.PRIVATE) && Utils.typeEquals(var.getEnclosingElement().asType(), parentNodeData.getTemplateType().asType())) {
                execution = ExecutionKind.IGNORE;
            }
        }
        return new NodeFieldData(fieldNodeData, var, findAccessElement(var), mirror, kind, execution);
    }

    private Element findAccessElement(VariableElement variableElement) {
        Element enclosed = variableElement.getEnclosingElement();
        if (!enclosed.getKind().isClass()) {
            throw new IllegalArgumentException("Field must be enclosed in a class.");
        }

        String methodName;
        if (Utils.typeEquals(variableElement.asType(), context.getType(boolean.class))) {
            methodName = "is" + Utils.firstLetterUpperCase(variableElement.getSimpleName().toString());
        } else {
            methodName = "get" + Utils.firstLetterUpperCase(variableElement.getSimpleName().toString());
        }

        ExecutableElement getter = null;
        for (ExecutableElement method : ElementFilter.methodsIn(enclosed.getEnclosedElements())) {
            if (method.getSimpleName().toString().equals(methodName) && method.getParameters().size() == 0 && !Utils.typeEquals(method.getReturnType(), context.getType(void.class))) {
                getter = method;
                break;
            }
        }

        if (getter != null) {
            return getter;
        } else {
            return variableElement;
        }
    }

    private static void sortByExecutionOrder(List<NodeFieldData> fields, final List<String> executionOrder, final List<TypeElement> typeHierarchy) {
        Collections.sort(fields, new Comparator<NodeFieldData>() {

            @Override
            public int compare(NodeFieldData o1, NodeFieldData o2) {
                // sort by execution order
                int index1 = executionOrder.indexOf(o1.getName());
                int index2 = executionOrder.indexOf(o2.getName());
                if (index1 == -1 || index2 == -1) {
                    // sort by type hierarchy
                    index1 = typeHierarchy.indexOf(o1.getFieldElement().getEnclosingElement());
                    index2 = typeHierarchy.indexOf(o2.getFieldElement().getEnclosingElement());

                    // finally sort by name (will emit warning)
                    if (index1 == -1 || index2 == -1) {
                        return o1.getName().compareTo(o2.getName());
                    }
                }
                return index1 - index2;
            }
        });
    }

    private boolean assignShortCircuitsToSpecializations(NodeData node) {
        Map<String, List<ShortCircuitData>> groupedShortCircuits = groupShortCircuits(node.getShortCircuits());

        boolean valid = true;

        for (NodeFieldData field : node.filterFields(null, ExecutionKind.SHORT_CIRCUIT)) {
            String valueName = field.getName();
            List<ShortCircuitData> availableCircuits = groupedShortCircuits.get(valueName);

            if (availableCircuits == null || availableCircuits.isEmpty()) {
                log.error(node.getTemplateType(), "@%s method for short cut value '%s' required.", ShortCircuit.class.getSimpleName(), valueName);
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
                    log.error(circuit.getMethod(), circuit.getMarkerAnnotation(), "All short circuits for short cut value '%s' must have the same method name.", valueName);
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
                log.error(node.getTemplateType(), "No generic @%s method available for short cut value '%s'.", ShortCircuit.class.getSimpleName(), valueName);
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
            return valid;
        }

        NodeFieldData[] fields = node.filterFields(null, ExecutionKind.SHORT_CIRCUIT);
        for (SpecializationData specialization : node.getSpecializations()) {
            ShortCircuitData[] assignedShortCuts = new ShortCircuitData[fields.length];

            for (int i = 0; i < fields.length; i++) {
                List<ShortCircuitData> availableShortCuts = groupedShortCircuits.get(fields[i].getName());

                ShortCircuitData genericShortCircuit = null;
                for (ShortCircuitData circuit : availableShortCuts) {
                    if (circuit.isGeneric()) {
                        genericShortCircuit = circuit;
                    } else if (circuit.isCompatibleTo(specialization)) {
                        assignedShortCuts[i] = circuit;
                    }
                }

                if (assignedShortCuts[i] == null) {
                    assignedShortCuts[i] = genericShortCircuit;
                }
            }
            specialization.setShortCircuits(assignedShortCuts);
        }
        return true;
    }

    private boolean verifyNamingConvention(List<? extends TemplateMethod> methods, String prefix) {
        boolean valid = true;
        for (int i = 0; i < methods.size(); i++) {
            TemplateMethod m1 = methods.get(i);
            if (m1.getMethodName().length() < 3 || !m1.getMethodName().startsWith(prefix)) {
                log.error(m1.getMethod(), m1.getMarkerAnnotation(), "Naming convention: method name must start with '%s'.", prefix);
                valid = false;
            }
        }
        return valid;
    }

    @SuppressWarnings("unused")
    private boolean verifyNamesUnique(List<? extends TemplateMethod> methods) {
        boolean valid = true;
        for (int i = 0; i < methods.size(); i++) {
            TemplateMethod m1 = methods.get(i);
            for (int j = i + 1; j < methods.size(); j++) {
                TemplateMethod m2 = methods.get(j);

                if (m1.getMethodName().equalsIgnoreCase(m2.getMethodName())) {
                    log.error(m1.getMethod(), m1.getMarkerAnnotation(), "Method name '%s' used multiple times", m1.getMethodName());
                    log.error(m2.getMethod(), m2.getMarkerAnnotation(), "Method name '%s' used multiple times", m1.getMethodName());
                    return false;
                }
            }
        }
        return valid;
    }

    private boolean isGenericShortCutMethod(NodeData node, TemplateMethod method) {
        for (ActualParameter parameter : method.getParameters()) {
            NodeFieldData field = node.findField(parameter.getSpecification().getName());
            if (field == null) {
                continue;
            }
            ExecutableTypeData found = null;
            List<ExecutableTypeData> executableElements = field.getNodeData().findGenericExecutableTypes(context);
            for (ExecutableTypeData executable : executableElements) {
                if (executable.getType().equalsType(parameter.getActualTypeData(node.getTypeSystem()))) {
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

    private TypeMirror getComponentType(TypeMirror type) {
        if (type instanceof ArrayType) {
            return getComponentType(((ArrayType) type).getComponentType());
        }
        return type;
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

    private boolean verifySpecializationOrder(NodeData node) {
        TypeSystemData typeSystem = node.getTypeSystem();
        List<SpecializationData> specializations = node.getSpecializations();

        for (int i = 0; i < specializations.size(); i++) {
            SpecializationData m1 = specializations.get(i);
            for (int j = i + 1; j < specializations.size(); j++) {
                SpecializationData m2 = specializations.get(j);
                int inferredOrder = compareSpecializationWithoutOrder(typeSystem, m1, m2);

                if (m1.getOrder() != Specialization.DEFAULT_ORDER && m2.getOrder() != Specialization.DEFAULT_ORDER) {
                    int specOrder = m1.getOrder() - m2.getOrder();
                    if (specOrder == 0) {
                        log.error(m1.getMethod(), m1.getMarkerAnnotation(), "Order value %d used multiple times", m1.getOrder());
                        log.error(m2.getMethod(), m2.getMarkerAnnotation(), "Order value %d used multiple times", m1.getOrder());
                        return false;
                    } else if ((specOrder < 0 && inferredOrder > 0) || (specOrder > 0 && inferredOrder < 0)) {
                        log.error(m1.getMethod(), m1.getMarkerAnnotation(), "Explicit order values %d and %d are inconsistent with type lattice ordering.", m1.getOrder(), m2.getOrder());
                        log.error(m2.getMethod(), m2.getMarkerAnnotation(), "Explicit order values %d and %d are inconsistent with type lattice ordering.", m1.getOrder(), m2.getOrder());
                        return false;
                    }
                } else if (inferredOrder == 0) {
                    SpecializationData m = (m1.getOrder() == Specialization.DEFAULT_ORDER ? m1 : m2);
                    log.error(m.getMethod(), m.getMarkerAnnotation(), "Cannot calculate a consistent order for this specialization. Define the order attribute to resolve this.");
                    return false;
                }
            }
        }
        return true;
    }

    private boolean verifySpecializationThrows(NodeData node) {
        Map<String, SpecializationData> specializationMap = new HashMap<>();
        for (SpecializationData spec : node.getSpecializations()) {
            specializationMap.put(spec.getMethodName(), spec);
        }
        boolean valid = true;
        for (SpecializationData sourceSpecialization : node.getSpecializations()) {
            if (sourceSpecialization.getExceptions() != null) {
                for (SpecializationThrowsData throwsData : sourceSpecialization.getExceptions()) {
                    for (SpecializationThrowsData otherThrowsData : sourceSpecialization.getExceptions()) {
                        if (otherThrowsData != throwsData && Utils.typeEquals(otherThrowsData.getJavaClass(), throwsData.getJavaClass())) {
                            AnnotationValue javaClassValue = Utils.getAnnotationValue(throwsData.getAnnotationMirror(), "rewriteOn");
                            log.error(throwsData.getSpecialization().getMethod(), throwsData.getAnnotationMirror(), javaClassValue, "Duplicate exception type.");
                            valid = false;
                        }
                    }
                }
            }
        }
        return valid;
    }

    private static int compareSpecialization(TypeSystemData typeSystem, SpecializationData m1, SpecializationData m2) {
        if (m1 == m2) {
            return 0;
        }
        int result = compareSpecializationWithoutOrder(typeSystem, m1, m2);
        if (result == 0) {
            if (m1.getOrder() != Specialization.DEFAULT_ORDER && m2.getOrder() != Specialization.DEFAULT_ORDER) {
                return m1.getOrder() - m2.getOrder();
            }
        }
        return result;
    }

    private static int compareSpecializationWithoutOrder(TypeSystemData typeSystem, SpecializationData m1, SpecializationData m2) {
        if (m1 == m2) {
            return 0;
        }

        if (m1.isUninitialized() && !m2.isUninitialized()) {
            return -1;
        } else if (!m1.isUninitialized() && m2.isUninitialized()) {
            return 1;
        } else if (m1.isGeneric() && !m2.isGeneric()) {
            return 1;
        } else if (!m1.isGeneric() && m2.isGeneric()) {
            return -1;
        }

        if (m1.getTemplate() != m2.getTemplate()) {
            throw new UnsupportedOperationException("Cannot compare two specializations with different templates.");
        }

        int result = compareActualParameter(typeSystem, m1.getReturnType(), m2.getReturnType());

        for (ParameterSpec spec : m1.getSpecification().getParameters()) {
            ActualParameter p1 = m1.findParameter(spec);
            ActualParameter p2 = m2.findParameter(spec);

            if (p1 != null && p2 != null && !Utils.typeEquals(p1.getActualType(), p2.getActualType())) {
                int typeResult = compareActualParameter(typeSystem, p1, p2);
                if (result == 0) {
                    result = typeResult;
                } else if (Math.signum(result) != Math.signum(typeResult)) {
                    // We cannot define an order.
                    return 0;
                }
            }
        }
        return result;
    }

    private static int compareActualParameter(TypeSystemData typeSystem, ActualParameter p1, ActualParameter p2) {
        int index1 = typeSystem.findType(p1.getActualType());
        int index2 = typeSystem.findType(p2.getActualType());

        assert index1 != index2;
        assert !(index1 == -1 ^ index2 == -1);

        return index1 - index2;
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return null;
    }

    @Override
    public List<Class<? extends Annotation>> getTypeDelegatedAnnotationTypes() {
        return ANNOTATIONS;
    }

}
