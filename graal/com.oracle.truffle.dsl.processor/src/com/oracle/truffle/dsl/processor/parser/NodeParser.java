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
package com.oracle.truffle.dsl.processor.parser;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.compiler.*;
import com.oracle.truffle.dsl.processor.model.*;
import com.oracle.truffle.dsl.processor.model.NodeChildData.Cardinality;
import com.oracle.truffle.dsl.processor.model.SpecializationData.SpecializationKind;
import com.oracle.truffle.dsl.processor.model.TemplateMethod.TypeSignature;

public class NodeParser extends AbstractParser<NodeData> {

    public static final List<Class<? extends Annotation>> ANNOTATIONS = Arrays.asList(Generic.class, TypeSystemReference.class, ShortCircuit.class, Specialization.class, NodeChild.class,
                    NodeChildren.class);

    private Map<String, NodeData> parsedNodes;

    @Override
    protected NodeData parse(Element element, AnnotationMirror mirror) {
        NodeData node = null;
        try {
            parsedNodes = new HashMap<>();
            node = resolveNode((TypeElement) element);
            if (Log.DEBUG) {
                NodeData parsed = parsedNodes.get(ElementUtils.getQualifiedName((TypeElement) element));
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
        for (Iterator<NodeData> iterator = model.getEnclosingNodes().iterator(); iterator.hasNext();) {
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
        String typeName = ElementUtils.getQualifiedName(rootType);
        if (parsedNodes.containsKey(typeName)) {
            return parsedNodes.get(typeName);
        }

        List<NodeData> enclosedNodes = new ArrayList<>();
        for (TypeElement enclosedType : ElementFilter.typesIn(rootType.getEnclosedElements())) {
            NodeData enclosedChild = resolveNode(enclosedType);
            if (enclosedChild != null) {
                enclosedNodes.add(enclosedChild);
            }
        }

        NodeData node = parseNode(rootType);
        if (node == null && !enclosedNodes.isEmpty()) {
            node = new NodeData(context, rootType);
        }

        if (node != null) {
            for (NodeData enclosedNode : enclosedNodes) {
                node.addEnclosedNode(enclosedNode);
            }
        }

        parsedNodes.put(typeName, node);
        return node;
    }

    private NodeData parseNode(TypeElement originalTemplateType) {
        // reloading the type elements is needed for ecj
        TypeElement templateType = ElementUtils.fromTypeMirror(context.reloadTypeElement(originalTemplateType));

        if (ElementUtils.findAnnotationMirror(processingEnv, originalTemplateType, GeneratedBy.class) != null) {
            // generated nodes should not get called again.
            return null;
        }

        List<TypeElement> lookupTypes = collectSuperClasses(new ArrayList<TypeElement>(), templateType);
        if (!ElementUtils.isAssignable(templateType.asType(), context.getTruffleTypes().getNode())) {
            return null;
        }
        List<? extends Element> elements = CompilerFactory.getCompiler(templateType).getAllMembersInDeclarationOrder(context.getEnvironment(), templateType);

        NodeData node = parseNodeData(templateType, elements, lookupTypes);
        if (node.hasErrors()) {
            return node; // error sync point
        }

        initializeChildren(node);

        node.getSpecializations().addAll(new SpecializationMethodParser(context, node).parse(elements));
        node.getSpecializations().addAll(new GenericParser(context, node).parse(elements));
        node.getCasts().addAll(new CreateCastParser(context, node).parse(elements));
        node.getShortCircuits().addAll(new ShortCircuitParser(context, node).parse(elements));

        if (node.hasErrors()) {
            return node;  // error sync point
        }

        verifySpecializationSameLength(node);
        initializeSpecializations(elements, node);
        initializeShortCircuits(node); // requires specializations and polymorphic specializations

        verifyVisibilities(node);
        verifyMissingAbstractMethods(node, elements);
        verifyConstructors(node);
        verifyNamingConvention(node.getShortCircuits(), "needs");
        verifySpecializationThrows(node);
        return node;
    }

    private NodeData parseNodeData(TypeElement templateType, List<? extends Element> elements, List<TypeElement> typeHierarchy) {
        AnnotationMirror typeSystemMirror = findFirstAnnotation(typeHierarchy, TypeSystemReference.class);
        if (typeSystemMirror == null) {
            NodeData nodeData = new NodeData(context, templateType);
            nodeData.addError("No @%s annotation found in type hierarchy of %s.", TypeSystemReference.class.getSimpleName(), ElementUtils.getQualifiedName(templateType));
            return nodeData;
        }

        TypeMirror typeSystemType = ElementUtils.getAnnotationValue(TypeMirror.class, typeSystemMirror, "value");
        final TypeSystemData typeSystem = (TypeSystemData) context.getTemplate(typeSystemType, true);
        if (typeSystem == null) {
            NodeData nodeData = new NodeData(context, templateType);
            nodeData.addError("The used type system '%s' is invalid or not a Node.", ElementUtils.getQualifiedName(typeSystemType));
            return nodeData;
        }

        List<String> assumptionsList = new ArrayList<>();
        for (int i = typeHierarchy.size() - 1; i >= 0; i--) {
            TypeElement type = typeHierarchy.get(i);
            AnnotationMirror assumptions = ElementUtils.findAnnotationMirror(context.getEnvironment(), type, NodeAssumptions.class);
            if (assumptions != null) {
                List<String> assumptionStrings = ElementUtils.getAnnotationValueList(String.class, assumptions, "value");
                for (String string : assumptionStrings) {
                    if (assumptionsList.contains(string)) {
                        assumptionsList.remove(string);
                    }
                    assumptionsList.add(string);
                }
            }
        }
        AnnotationMirror nodeInfoMirror = findFirstAnnotation(typeHierarchy, NodeInfo.class);
        String shortName = null;
        if (nodeInfoMirror != null) {
            shortName = ElementUtils.getAnnotationValue(String.class, nodeInfoMirror, "shortName");
        }

        List<NodeFieldData> fields = parseFields(typeHierarchy, elements);
        List<NodeChildData> children = parseChildren(typeHierarchy, elements);
        List<NodeExecutionData> executions = parseExecutions(children, elements);

        NodeData nodeData = new NodeData(context, templateType, shortName, typeSystem, children, executions, fields, assumptionsList);
        nodeData.setExecutableTypes(groupExecutableTypes(new ExecutableTypeMethodParser(context, nodeData).parse(elements)));

        parsedNodes.put(ElementUtils.getQualifiedName(templateType), nodeData);

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
            AnnotationMirror nodeChildrenMirror = ElementUtils.findAnnotationMirror(processingEnv, typeElement, NodeFields.class);
            List<AnnotationMirror> children = ElementUtils.collectAnnotations(context, nodeChildrenMirror, "value", typeElement, NodeField.class);

            for (AnnotationMirror mirror : children) {
                String name = ElementUtils.firstLetterLowerCase(ElementUtils.getAnnotationValue(String.class, mirror, "name"));
                TypeMirror type = ElementUtils.getAnnotationValue(TypeMirror.class, mirror, "type");

                NodeFieldData field = new NodeFieldData(typeElement, mirror, type, name, true);
                if (name.isEmpty()) {
                    field.addError(ElementUtils.getAnnotationValue(mirror, "name"), "Field name cannot be empty.");
                } else if (names.contains(name)) {
                    field.addError(ElementUtils.getAnnotationValue(mirror, "name"), "Duplicate field name '%s'.", name);
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

    private List<NodeChildData> parseChildren(final List<TypeElement> typeHierarchy, List<? extends Element> elements) {
        Set<String> shortCircuits = new HashSet<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            AnnotationMirror mirror = ElementUtils.findAnnotationMirror(processingEnv, method, ShortCircuit.class);
            if (mirror != null) {
                shortCircuits.add(ElementUtils.getAnnotationValue(String.class, mirror, "value"));
            }
        }
        Map<String, TypeMirror> castNodeTypes = new HashMap<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            AnnotationMirror mirror = ElementUtils.findAnnotationMirror(processingEnv, method, CreateCast.class);
            if (mirror != null) {
                List<String> children = (ElementUtils.getAnnotationValueList(String.class, mirror, "value"));
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
            AnnotationMirror nodeChildrenMirror = ElementUtils.findAnnotationMirror(processingEnv, type, NodeChildren.class);

            TypeMirror nodeClassType = type.getSuperclass();
            if (!ElementUtils.isAssignable(nodeClassType, context.getTruffleTypes().getNode())) {
                nodeClassType = null;
            }

            List<AnnotationMirror> children = ElementUtils.collectAnnotations(context, nodeChildrenMirror, "value", type, NodeChild.class);
            int index = 0;
            for (AnnotationMirror childMirror : children) {
                String name = ElementUtils.getAnnotationValue(String.class, childMirror, "value");
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

                NodeChildData nodeChild = new NodeChildData(type, childMirror, name, childType, originalChildType, getter, cardinality);

                parsedChildren.add(nodeChild);

                if (nodeChild.getNodeType() == null) {
                    nodeChild.addError("No valid node type could be resoleved.");
                }
                if (nodeChild.hasErrors()) {
                    continue;
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
            List<String> executeWithStrings = ElementUtils.getAnnotationValueList(String.class, child.getMessageAnnotation(), "executeWith");
            AnnotationValue executeWithValue = ElementUtils.getAnnotationValue(child.getMessageAnnotation(), "executeWith");
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
        }

        return filteredChildren;
    }

    private List<NodeExecutionData> parseExecutions(List<NodeChildData> children, List<? extends Element> elements) {
        if (children == null) {
            return null;
        }

        // pre-parse short circuits
        Set<String> shortCircuits = new HashSet<>();
        List<ExecutableElement> methods = ElementFilter.methodsIn(elements);
        for (ExecutableElement method : methods) {
            AnnotationMirror mirror = ElementUtils.findAnnotationMirror(processingEnv, method, ShortCircuit.class);
            if (mirror != null) {
                shortCircuits.add(ElementUtils.getAnnotationValue(String.class, mirror, "value"));
            }
        }

        boolean hasVarArgs = false;
        int maxSignatureSize = 0;
        if (!children.isEmpty()) {
            int lastIndex = children.size() - 1;
            hasVarArgs = children.get(lastIndex).getCardinality() == Cardinality.MANY;
            if (hasVarArgs) {
                maxSignatureSize = lastIndex;
            } else {
                maxSignatureSize = children.size();
            }
        }

        // pre-parse specializations
        for (ExecutableElement method : methods) {
            AnnotationMirror mirror = ElementUtils.findAnnotationMirror(processingEnv, method, Specialization.class);
            if (mirror == null) {
                continue;
            }

            int currentArgumentCount = 0;
            boolean skipShortCircuit = false;
            for (VariableElement var : method.getParameters()) {
                TypeMirror type = var.asType();
                if (currentArgumentCount == 0) {
                    // skip optionals
                    if (ElementUtils.typeEquals(type, context.getTruffleTypes().getFrame())) {
                        continue;
                    }
                    // TODO skip optional fields?
                }
                int childIndex = currentArgumentCount < children.size() ? currentArgumentCount : children.size() - 1;
                if (childIndex == -1) {
                    continue;
                }
                if (!skipShortCircuit) {
                    NodeChildData child = children.get(childIndex);
                    if (shortCircuits.contains(NodeExecutionData.createShortCircuitId(child, currentArgumentCount - childIndex))) {
                        skipShortCircuit = true;
                        continue;
                    }
                } else {
                    skipShortCircuit = false;
                }

                currentArgumentCount++;
            }
            maxSignatureSize = Math.max(maxSignatureSize, currentArgumentCount);
        }

        List<NodeExecutionData> executions = new ArrayList<>();
        for (int i = 0; i < maxSignatureSize; i++) {
            int childIndex = i;
            boolean varArg = false;
            if (childIndex >= children.size() - 1) {
                if (hasVarArgs) {
                    childIndex = children.size() - 1;
                    varArg = hasVarArgs;
                } else if (childIndex >= children.size()) {
                    break;
                }
            }
            int varArgsIndex = varArg ? Math.abs(childIndex - i) : -1;
            NodeChildData child = children.get(childIndex);
            boolean shortCircuit = shortCircuits.contains(NodeExecutionData.createShortCircuitId(child, varArgsIndex));
            executions.add(new NodeExecutionData(child, varArgsIndex, shortCircuit));
        }
        return executions;
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

    private void initializeChildren(NodeData node) {
        for (NodeChildData nodeChild : node.getChildren()) {
            NodeData fieldNodeData = resolveNode(ElementUtils.fromTypeMirror(nodeChild.getNodeType()));
            nodeChild.setNode(fieldNodeData);
            if (fieldNodeData == null) {
                nodeChild.addError("Node type '%s' is invalid or not a valid Node.", ElementUtils.getQualifiedName(nodeChild.getNodeType()));
            } else if (!ElementUtils.typeEquals(fieldNodeData.getTypeSystem().getTemplateType().asType(), (node.getTypeSystem().getTemplateType().asType()))) {
                nodeChild.addError("The @%s of the node and the @%s of the @%s does not match. %s != %s. ", TypeSystem.class.getSimpleName(), TypeSystem.class.getSimpleName(),
                                NodeChild.class.getSimpleName(), ElementUtils.getSimpleName(node.getTypeSystem().getTemplateType()),
                                ElementUtils.getSimpleName(fieldNodeData.getTypeSystem().getTemplateType()));
            }
            if (fieldNodeData != null) {
                List<ExecutableTypeData> types = nodeChild.findGenericExecutableTypes(context);
                if (types.isEmpty()) {
                    AnnotationValue executeWithValue = ElementUtils.getAnnotationValue(nodeChild.getMessageAnnotation(), "executeWith");
                    nodeChild.addError(executeWithValue, "No generic execute method found with %s evaluated arguments for node type %s.", nodeChild.getExecuteWith().size(),
                                    ElementUtils.getSimpleName(nodeChild.getNodeType()));
                }
            }
        }
    }

    private void initializeSpecializations(List<? extends Element> elements, final NodeData node) {
        if (node.getSpecializations().isEmpty()) {
            return;
        }

        initializeGuards(elements, node);
        initializeGeneric(node);
        initializeUninitialized(node);
        initializeOrder(node);
        initializePolymorphism(node); // requires specializations
        initializeReachability(node);
        initializeContains(node);

        if (!node.hasErrors()) {
            initializeExceptions(node);
        }
        resolveContains(node);

        List<SpecializationData> needsId = new ArrayList<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.isGeneric()) {
                specialization.setId("Generic");
            } else if (specialization.isUninitialized()) {
                specialization.setId("Uninitialized");
            } else if (specialization.isPolymorphic()) {
                specialization.setId("Polymorphic");
            } else if (specialization.isSpecialized()) {
                needsId.add(specialization);
            } else {
                throw new AssertionError();
            }
        }

        // verify specialization parameter length
        List<String> ids = initializeSpecializationIds(needsId);
        for (int i = 0; i < ids.size(); i++) {
            needsId.get(i).setId(ids.get(i));
        }

    }

    private static void initializeOrder(NodeData node) {
        List<SpecializationData> specializations = node.getSpecializations();
        Collections.sort(specializations);

        for (SpecializationData specialization : specializations) {
            String searchName = specialization.getInsertBeforeName();
            if (searchName == null || specialization.getMethod() == null) {
                continue;
            }
            SpecializationData found = lookupSpecialization(node, searchName);
            if (found == null || found.getMethod() == null) {
                AnnotationValue value = ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "insertBefore");
                specialization.addError(value, "The referenced specialization '%s' could not be found.", searchName);
                continue;
            }

            ExecutableElement currentMethod = specialization.getMethod();
            ExecutableElement insertBeforeMethod = found.getMethod();

            TypeMirror currentEnclosedType = currentMethod.getEnclosingElement().asType();
            TypeMirror insertBeforeEnclosedType = insertBeforeMethod.getEnclosingElement().asType();

            if (ElementUtils.typeEquals(currentEnclosedType, insertBeforeEnclosedType) || !ElementUtils.isSubtype(currentEnclosedType, insertBeforeEnclosedType)) {
                AnnotationValue value = ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "insertBefore");
                specialization.addError(value, "Specializations can only be inserted before specializations in superclasses.", searchName);
                continue;
            }

            specialization.setInsertBefore(found);
        }

        int endIndex = specializations.size() - 1;
        for (int i = endIndex; i >= 0; i--) {
            SpecializationData specialization = specializations.get(i);
            if (specialization.isGeneric() || specialization.isPolymorphic()) {
                endIndex--;
                continue;
            }

            SpecializationData insertBefore = specialization.getInsertBefore();
            if (insertBefore != null) {
                int insertIndex = specializations.indexOf(insertBefore);
                if (insertIndex < i) {
                    List<SpecializationData> range = new ArrayList<>(specializations.subList(i, endIndex + 1));
                    specializations.removeAll(range);
                    specializations.addAll(insertIndex, range);
                }
            }
        }

        for (int i = 0; i < specializations.size(); i++) {
            specializations.get(i).setIndex(i);
        }
    }

    private static void initializeExceptions(NodeData node) {
        List<SpecializationData> specializations = node.getSpecializations();
        for (int i = 0; i < specializations.size(); i++) {
            SpecializationData cur = specializations.get(i);
            if (cur.getExceptions().isEmpty()) {
                continue;
            }
            SpecializationData next = i + 1 < specializations.size() ? specializations.get(i + 1) : null;

            if (!cur.isContainedBy(next)) {
                // error should be able to contain
                next.addError("This specialiation is not a valid exceptional rewrite target for %s. To fix this make %s compatible to %s or remove the exceptional rewrite.",
                                cur.createReferenceName(), next.createReferenceName(), cur.createReferenceName());
                continue;
            }
            if (!next.getContains().contains(cur)) {
                next.getContains().add(cur);
                // TODO resolve transitive contains
            }
        }

        for (SpecializationData cur : specializations) {
            if (cur.getExceptions().isEmpty()) {
                continue;
            }
            for (SpecializationData child : specializations) {
                if (child != null && child != cur && child.getContains().contains(cur)) {
                    cur.getExcludedBy().add(child);
                }
            }
        }
    }

    private static void initializeContains(NodeData node) {
        for (SpecializationData specialization : node.getSpecializations()) {
            Set<SpecializationData> resolvedSpecializations = specialization.getContains();
            resolvedSpecializations.clear();
            Set<String> includeNames = specialization.getContainsNames();
            for (String includeName : includeNames) {
                // TODO reduce complexity of this lookup.
                SpecializationData foundSpecialization = lookupSpecialization(node, includeName);

                if (foundSpecialization == null) {
                    AnnotationValue value = ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "contains");
                    specialization.addError(value, "The referenced specialization '%s' could not be found.", includeName);
                } else {
                    if (!foundSpecialization.isContainedBy(specialization)) {
                        AnnotationValue value = ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "contains");
                        if (foundSpecialization.compareTo(specialization) > 0) {
                            specialization.addError(value, "The contained specialization '%s' must be declared before the containing specialization.", includeName);
                        } else {
                            specialization.addError(value,
                                            "The contained specialization '%s' is not fully compatible. The contained specialization must be strictly more generic than the containing one.",
                                            includeName);
                        }

                    }
                    resolvedSpecializations.add(foundSpecialization);
                }
            }
        }
    }

    private void resolveContains(NodeData node) {
        // flatten transitive includes
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getContains().isEmpty()) {
                continue;
            }
            Set<SpecializationData> foundSpecializations = new HashSet<>();
            collectIncludes(specialization, foundSpecializations, new HashSet<SpecializationData>());
            specialization.getContains().addAll(foundSpecializations);
        }
    }

    private static SpecializationData lookupSpecialization(NodeData node, String includeName) {
        SpecializationData foundSpecialization = null;
        for (SpecializationData searchSpecialization : node.getSpecializations()) {
            if (searchSpecialization.getMethodName().equals(includeName)) {
                foundSpecialization = searchSpecialization;
                break;
            }
        }
        return foundSpecialization;
    }

    private void collectIncludes(SpecializationData specialization, Set<SpecializationData> found, Set<SpecializationData> visited) {
        if (visited.contains(specialization)) {
            // circle found
            specialization.addError("Circular contained specialization '%s' found.", specialization.createReferenceName());
            return;
        }
        visited.add(specialization);

        for (SpecializationData included : specialization.getContains()) {
            collectIncludes(included, found, new HashSet<>(visited));
            found.add(included);
        }
    }

    private static void initializeReachability(final NodeData node) {
        List<SpecializationData> specializations = node.getSpecializations();
        for (int i = specializations.size() - 1; i >= 0; i--) {
            SpecializationData current = specializations.get(i);
            if (current.isPolymorphic()) {
                current.setReachable(true);
                continue;
            }

            List<SpecializationData> shadowedBy = null;
            for (int j = i - 1; j >= 0; j--) {
                SpecializationData prev = specializations.get(j);
                if (prev.isPolymorphic()) {
                    continue;
                }
                if (!current.isReachableAfter(prev)) {
                    if (shadowedBy == null) {
                        shadowedBy = new ArrayList<>();
                    }
                    shadowedBy.add(prev);
                }
            }

            if (shadowedBy != null) {
                StringBuilder name = new StringBuilder();
                String sep = "";
                for (SpecializationData shadowSpecialization : shadowedBy) {
                    name.append(sep);
                    name.append(shadowSpecialization.createReferenceName());
                    sep = ", ";
                }
                current.addError("%s is not reachable. It is shadowed by %s.", current.isGeneric() ? "Generic" : "Specialization", name);
            }
            current.setReachable(shadowedBy == null);
        }
    }

    private static List<String> initializeSpecializationIds(List<SpecializationData> specializations) {
        int lastSize = -1;
        List<List<String>> signatureChunks = new ArrayList<>();
        for (SpecializationData other : specializations) {
            if (!other.isSpecialized()) {
                continue;
            }
            List<String> paramIds = new LinkedList<>();
            paramIds.add(ElementUtils.getTypeId(other.getReturnType().getType()));
            for (Parameter param : other.getParameters()) {
                if (param.getSpecification().getExecution() == null) {
                    continue;
                }
                paramIds.add(ElementUtils.getTypeId(param.getType()));
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

    private void initializeGuards(List<? extends Element> elements, NodeData node) {
        Map<String, List<GuardData>> guards = new HashMap<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            for (GuardExpression exp : specialization.getGuards()) {
                guards.put(exp.getGuardName(), null);
            }
        }

        GuardParser parser = new GuardParser(context, node, null, guards.keySet());
        List<GuardData> resolvedGuards = parser.parse(elements);
        for (GuardData guard : resolvedGuards) {
            List<GuardData> groupedGuards = guards.get(guard.getMethodName());
            if (groupedGuards == null) {
                groupedGuards = new ArrayList<>();
                guards.put(guard.getMethodName(), groupedGuards);
            }
            groupedGuards.add(guard);
        }

        for (SpecializationData specialization : node.getSpecializations()) {
            for (GuardExpression exp : specialization.getGuards()) {
                resolveGuardExpression(node, specialization, guards, exp);
            }
        }
    }

    private void resolveGuardExpression(NodeData node, TemplateMethod source, Map<String, List<GuardData>> guards, GuardExpression expression) {
        List<GuardData> availableGuards = guards.get(expression.getGuardName());
        if (availableGuards == null) {
            source.addError("No compatible guard with method name '%s' found. Please note that all signature types of the method guard must be declared in the type system.", expression.getGuardName());
            return;
        }
        List<ExecutableElement> guardMethods = new ArrayList<>();
        for (GuardData guard : availableGuards) {
            guardMethods.add(guard.getMethod());
        }
        GuardParser parser = new GuardParser(context, node, source, new HashSet<>(Arrays.asList(expression.getGuardName())));
        List<GuardData> matchingGuards = parser.parse(guardMethods);
        if (!matchingGuards.isEmpty()) {
            GuardData guard = matchingGuards.get(0);
            // use the shared instance of the guard data
            for (GuardData guardData : availableGuards) {
                if (guardData.getMethod() == guard.getMethod()) {
                    expression.setGuard(guardData);
                    return;
                }
            }
            throw new AssertionError("Should not reach here.");
        } else {
            MethodSpec spec = parser.createSpecification(source.getMethod(), source.getMarkerAnnotation());
            spec.applyTypeDefinitions("types");
            source.addError("No guard with name '%s' matched the required signature. Expected signature: %n%s", expression.getGuardName(), spec.toSignatureString("guard"));
        }
    }

    private void initializeGeneric(final NodeData node) {
        if (!node.needsRewrites(context)) {
            return;
        }

        List<SpecializationData> generics = new ArrayList<>();
        for (SpecializationData spec : node.getSpecializations()) {
            if (spec.isGeneric()) {
                generics.add(spec);
            }
        }

        if (generics.size() == 1 && node.getSpecializations().size() == 1) {
            // TODO this limitation should be lifted
            for (SpecializationData generic : generics) {
                generic.addError("@%s defined but no @%s.", Generic.class.getSimpleName(), Specialization.class.getSimpleName());
            }
        }

        if (generics.isEmpty()) {
            node.getSpecializations().add(createGenericSpecialization(node));
        } else {
            if (generics.size() > 1) {
                for (SpecializationData generic : generics) {
                    generic.addError("Only @%s is allowed per operation.", Generic.class.getSimpleName());
                }
            }
        }
    }

    private SpecializationData createGenericSpecialization(final NodeData node) {
        GenericParser parser = new GenericParser(context, node);
        MethodSpec specification = parser.createDefaultMethodSpec(node.getSpecializations().iterator().next().getMethod(), null, true, null);

        List<TypeMirror> parameterTypes = new ArrayList<>();
        int signatureIndex = 1;
        for (ParameterSpec spec : specification.getRequired()) {
            parameterTypes.add(createGenericType(spec, node.getSpecializations(), signatureIndex));
            if (spec.isSignature()) {
                signatureIndex++;
            }
        }

        TypeMirror returnType = createGenericType(specification.getReturnType(), node.getSpecializations(), 0);
        SpecializationData generic = parser.create("Generic", TemplateMethod.NO_NATURAL_ORDER, null, null, returnType, parameterTypes);
        if (generic == null) {
            throw new RuntimeException("Unable to create generic signature for node " + node.getNodeId() + " with " + parameterTypes + ". Specification " + specification + ".");
        }

        return generic;
    }

    private TypeMirror createGenericType(ParameterSpec spec, List<SpecializationData> specializations, int signatureIndex) {
        NodeExecutionData execution = spec.getExecution();
        if (execution == null) {
            if (spec.getAllowedTypes().size() == 1) {
                return spec.getAllowedTypes().get(0);
            } else {
                return ElementUtils.getCommonSuperType(context, spec.getAllowedTypes().toArray(new TypeMirror[0]));
            }
        } else {
            Set<TypeData> types = new HashSet<>();
            for (SpecializationData specialization : specializations) {
                types.add(specialization.getTypeSignature().get(signatureIndex));
            }

            NodeChildData child = execution.getChild();
            TypeData genericType = null;
            if (types.size() == 1) {
                ExecutableTypeData executable = child.findExecutableType(context, types.iterator().next());
                if (executable != null && (signatureIndex == 0 || !executable.hasUnexpectedValue(context))) {
                    genericType = types.iterator().next();
                }
            }
            if (genericType == null) {
                genericType = child.findAnyGenericExecutableType(context).getType();
            }
            return genericType.getPrimitiveType();
        }
    }

    private static void initializeUninitialized(final NodeData node) {
        SpecializationData generic = node.getGenericSpecialization();
        if (generic == null) {
            return;
        }
        for (Parameter parameter : generic.getReturnTypeAndParameters()) {
            if (ElementUtils.isObject(parameter.getType())) {
                continue;
            }
            Set<String> types = new HashSet<>();
            for (SpecializationData specialization : node.getSpecializations()) {
                Parameter actualParameter = specialization.findParameter(parameter.getLocalName());
                if (actualParameter != null) {
                    types.add(ElementUtils.getQualifiedName(actualParameter.getType()));
                }
            }
            if (types.size() > 1) {
                generic.replaceParameter(parameter.getLocalName(), new Parameter(parameter, node.getTypeSystem().getGenericTypeData()));
            }
        }
        TemplateMethod uninializedMethod = new TemplateMethod("Uninitialized", -1, node, generic.getSpecification(), null, null, generic.getReturnType(), generic.getParameters());
        // should not use messages from generic specialization
        uninializedMethod.getMessages().clear();
        node.getSpecializations().add(new SpecializationData(node, uninializedMethod, SpecializationKind.UNINITIALIZED));
    }

    private void initializePolymorphism(NodeData node) {
        if (!node.needsRewrites(context)) {
            return;
        }

        SpecializationData generic = node.getGenericSpecialization();

        List<TypeData> polymorphicSignature = new ArrayList<>();
        List<Parameter> updatePolymorphic = Arrays.asList();
        for (Parameter genericParameter : updatePolymorphic) {
            if (!genericParameter.getSpecification().isSignature()) {
                continue;
            }

            Set<TypeData> usedTypes = new HashSet<>();
            for (SpecializationData specialization : node.getSpecializations()) {
                if (!specialization.isSpecialized()) {
                    continue;
                }
                Parameter parameter = specialization.findParameter(genericParameter.getLocalName());
                if (parameter == null) {
                    throw new AssertionError("Parameter existed in generic specialization but not in specialized. param = " + genericParameter.getLocalName());
                }
                usedTypes.add(parameter.getTypeSystemType());
            }

            TypeData polymorphicType;
            if (usedTypes.size() == 1) {
                polymorphicType = usedTypes.iterator().next();
            } else {
                polymorphicType = node.getTypeSystem().getGenericTypeData();
            }
            polymorphicSignature.add(polymorphicType);
        }

        SpecializationData polymorphic = new SpecializationData(node, generic, SpecializationKind.POLYMORPHIC);
        polymorphic.updateSignature(new TypeSignature(polymorphicSignature));
        node.getSpecializations().add(polymorphic);
    }

    private void initializeShortCircuits(NodeData node) {
        Map<String, List<ShortCircuitData>> groupedShortCircuits = groupShortCircuits(node.getShortCircuits());

        boolean valid = true;
        List<NodeExecutionData> shortCircuitExecutions = new ArrayList<>();
        for (NodeExecutionData execution : node.getChildExecutions()) {
            if (!execution.isShortCircuit()) {
                continue;
            }
            shortCircuitExecutions.add(execution);
            String valueName = execution.getShortCircuitId();
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
                if (isGenericShortCutMethod(circuit)) {
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

        List<SpecializationData> specializations = new ArrayList<>();
        specializations.addAll(node.getSpecializations());
        for (SpecializationData specialization : specializations) {
            List<ShortCircuitData> assignedShortCuts = new ArrayList<>(shortCircuitExecutions.size());

            for (NodeExecutionData shortCircuit : shortCircuitExecutions) {
                List<ShortCircuitData> availableShortCuts = groupedShortCircuits.get(shortCircuit.getShortCircuitId());

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

    private boolean isGenericShortCutMethod(ShortCircuitData method) {
        for (Parameter parameter : method.getParameters()) {
            NodeExecutionData execution = parameter.getSpecification().getExecution();
            if (execution == null) {
                continue;
            }
            ExecutableTypeData found = null;
            List<ExecutableTypeData> executableElements = execution.getChild().findGenericExecutableTypes(context);
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

    private static boolean verifySpecializationSameLength(NodeData nodeData) {
        int lastArgs = -1;
        for (SpecializationData specializationData : nodeData.getSpecializations()) {
            int signatureArgs = specializationData.getSignatureSize();
            if (lastArgs == signatureArgs) {
                continue;
            }
            if (lastArgs != -1) {
                for (SpecializationData specialization : nodeData.getSpecializations()) {
                    specialization.addError("All specializations must have the same number of arguments.");
                }
                return false;
            } else {
                lastArgs = signatureArgs;
            }
        }
        return true;
    }

    private static void verifyVisibilities(NodeData node) {
        if (node.getTemplateType().getModifiers().contains(Modifier.PRIVATE) && node.getSpecializations().size() > 0) {
            node.addError("Classes containing a @%s annotation must not be private.", Specialization.class.getSimpleName());
        }
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
                nodeData.addError("The type %s must implement the inherited abstract method %s.", ElementUtils.getSimpleName(nodeData.getTemplateType()),
                                ElementUtils.getReadableSignature(unusedMethod));
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

    private static void verifySpecializationThrows(NodeData node) {
        Map<String, SpecializationData> specializationMap = new HashMap<>();
        for (SpecializationData spec : node.getSpecializations()) {
            specializationMap.put(spec.getMethodName(), spec);
        }
        for (SpecializationData sourceSpecialization : node.getSpecializations()) {
            if (sourceSpecialization.getExceptions() != null) {
                for (SpecializationThrowsData throwsData : sourceSpecialization.getExceptions()) {
                    for (SpecializationThrowsData otherThrowsData : sourceSpecialization.getExceptions()) {
                        if (otherThrowsData != throwsData && ElementUtils.typeEquals(otherThrowsData.getJavaClass(), throwsData.getJavaClass())) {
                            throwsData.addError("Duplicate exception type.");
                        }
                    }
                }
            }
        }
    }

    private void verifyConstructors(NodeData nodeData) {
        if (!nodeData.needsRewrites(context)) {
            // no specialization constructor is needed if the node never rewrites.
            return;
        }

        TypeElement type = ElementUtils.fromTypeMirror(nodeData.getNodeType());
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements());

        boolean parametersFound = false;
        for (ExecutableElement constructor : constructors) {
            if (!constructor.getParameters().isEmpty() && !isSourceSectionConstructor(context, constructor)) {
                parametersFound = true;
            }
        }
        if (!parametersFound) {
            return;
        }
        for (ExecutableElement e : constructors) {
            if (e.getParameters().size() == 1) {
                TypeMirror firstArg = e.getParameters().get(0).asType();
                if (ElementUtils.typeEquals(firstArg, nodeData.getNodeType())) {
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
        nodeData.addError("Specialization constructor '%s(%s previousNode) { this(...); }' is required.", ElementUtils.getSimpleName(type), ElementUtils.getSimpleName(type));
    }

    public static boolean isSourceSectionConstructor(ProcessorContext context, ExecutableElement constructor) {
        return constructor.getParameters().size() == 1 && ElementUtils.typeEquals(constructor.getParameters().get(0).asType(), context.getTruffleTypes().getSourceSection());
    }

    private AnnotationMirror findFirstAnnotation(List<? extends Element> elements, Class<? extends Annotation> annotation) {
        for (Element element : elements) {
            AnnotationMirror mirror = ElementUtils.findAnnotationMirror(processingEnv, element, annotation);
            if (mirror != null) {
                return mirror;
            }
        }
        return null;
    }

    private TypeMirror inheritType(AnnotationMirror annotation, String valueName, TypeMirror parentType) {
        TypeMirror inhertNodeType = context.getTruffleTypes().getNode();
        TypeMirror value = ElementUtils.getAnnotationValue(TypeMirror.class, annotation, valueName);
        if (ElementUtils.typeEquals(inhertNodeType, value)) {
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
        if (ElementUtils.typeEquals(type, context.getType(boolean.class))) {
            methodName = "is" + ElementUtils.firstLetterUpperCase(variableName);
        } else {
            methodName = "get" + ElementUtils.firstLetterUpperCase(variableName);
        }

        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            if (method.getSimpleName().toString().equals(methodName) && method.getParameters().size() == 0 && ElementUtils.isAssignable(type, method.getReturnType())) {
                return method;
            }
        }
        return null;
    }

    private static List<TypeElement> collectSuperClasses(List<TypeElement> collection, TypeElement element) {
        if (element != null) {
            collection.add(element);
            if (element.getSuperclass() != null) {
                collectSuperClasses(collection, ElementUtils.fromTypeMirror(element.getSuperclass()));
            }
        }
        return collection;
    }

}
