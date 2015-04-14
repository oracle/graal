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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.expression.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.compiler.*;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;
import com.oracle.truffle.dsl.processor.model.NodeChildData.Cardinality;
import com.oracle.truffle.dsl.processor.model.SpecializationData.SpecializationKind;

@DSLOptions
public class NodeParser extends AbstractParser<NodeData> {

    public static final List<Class<? extends Annotation>> ANNOTATIONS = Arrays.asList(Fallback.class, TypeSystemReference.class, ShortCircuit.class, Specialization.class, NodeChild.class,
                    NodeChildren.class);

    @Override
    protected NodeData parse(Element element, AnnotationMirror mirror) {
        NodeData node = parseRootType((TypeElement) element);
        if (Log.isDebug() && node != null) {
            String dump = node.dump();
            log.message(Kind.ERROR, null, null, null, dump);
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

    private NodeData parseRootType(TypeElement rootType) {
        List<NodeData> enclosedNodes = new ArrayList<>();
        for (TypeElement enclosedType : ElementFilter.typesIn(rootType.getEnclosedElements())) {
            NodeData enclosedChild = parseRootType(enclosedType);
            if (enclosedChild != null) {
                enclosedNodes.add(enclosedChild);
            }
        }
        NodeData node;
        try {
            node = parseNode(rootType);
        } catch (CompileErrorException e) {
            throw e;
        } catch (Throwable e) {
            RuntimeException e2 = new RuntimeException(String.format("Parsing of Node %s failed.", ElementUtils.getQualifiedName(rootType)));
            e2.addSuppressed(e);
            throw e2;
        }
        if (node == null && !enclosedNodes.isEmpty()) {
            node = new NodeData(context, rootType);
        }

        if (node != null) {
            for (NodeData enclosedNode : enclosedNodes) {
                node.addEnclosedNode(enclosedNode);
            }
        }
        return node;
    }

    private NodeData parseNode(TypeElement originalTemplateType) {
        // reloading the type elements is needed for ecj
        TypeElement templateType = ElementUtils.fromTypeMirror(context.reloadTypeElement(originalTemplateType));

        if (ElementUtils.findAnnotationMirror(processingEnv, originalTemplateType, GeneratedBy.class) != null) {
            // generated nodes should not get called again.
            return null;
        }

        if (!ElementUtils.isAssignable(templateType.asType(), context.getTruffleTypes().getNode())) {
            return null;
        }

        List<TypeElement> lookupTypes = collectSuperClasses(new ArrayList<TypeElement>(), templateType);
        List<Element> members = loadMembers(templateType);
        // ensure the processed element has at least one @Specialization annotation.
        if (!containsSpecializations(members)) {
            return null;
        }

        NodeData node = parseNodeData(templateType, lookupTypes);
        if (node.hasErrors()) {
            return node;
        }

        node.getFields().addAll(parseFields(lookupTypes, members));
        node.getChildren().addAll(parseChildren(lookupTypes, members));
        node.getChildExecutions().addAll(parseExecutions(node.getFields(), node.getChildren(), members));
        node.getExecutableTypes().addAll(parseExecutableTypeData(members, node.getChildExecutions().size(), context.getFrameTypes(), false));

        initializeExecutableTypes(node);
        initializeImportGuards(node, lookupTypes, members);
        initializeChildren(node);

        if (node.hasErrors()) {
            return node; // error sync point
        }

        if (node.hasErrors()) {
            return node; // error sync point
        }

        node.getSpecializations().addAll(new SpecializationMethodParser(context, node).parse(members));
        node.getSpecializations().addAll(new GenericParser(context, node).parse(members));
        node.getCasts().addAll(new CreateCastParser(context, node).parse(members));
        node.getShortCircuits().addAll(new ShortCircuitParser(context, node).parse(members));

        if (node.hasErrors()) {
            return node;  // error sync point
        }
        initializeSpecializations(members, node);
        initializeExecutableTypeHierarchy(node);

        verifySpecializationSameLength(node);
        initializeShortCircuits(node); // requires specializations and polymorphic specializations

        verifyVisibilities(node);
        verifyMissingAbstractMethods(node, members);
        verifyConstructors(node);
        verifyNamingConvention(node.getShortCircuits(), "needs");
        verifySpecializationThrows(node);
        return node;
    }

    private static void initializeExecutableTypeHierarchy(NodeData node) {
        SpecializationData polymorphic = node.getPolymorphicSpecialization();
        if (polymorphic != null) {
            boolean polymorphicSignatureFound = false;
            TypeMirror frame = polymorphic.getFrame() != null ? polymorphic.getFrame().getType() : null;
            ExecutableTypeData polymorphicType = new ExecutableTypeData(polymorphic.getReturnType().getType(), "execute", frame, TemplateMethod.getSignatureTypes(polymorphic));
            for (ExecutableTypeData type : node.getExecutableTypes()) {
                if (polymorphicType.sameSignature(type)) {
                    polymorphicSignatureFound = true;
                    break;
                }
            }

            if (!polymorphicSignatureFound) {
                node.getExecutableTypes().add(polymorphicType);
            }
        }

        List<ExecutableTypeData> rootTypes = buildExecutableHierarchy(node);
        List<ExecutableTypeData> additionalAbstractRootTypes = new ArrayList<>();
        for (int i = 1; i < rootTypes.size(); i++) {
            ExecutableTypeData rootType = rootTypes.get(i);
            if (rootType.isAbstract()) {
                // cannot implemement root
                additionalAbstractRootTypes.add(rootType);
            } else {
                node.getExecutableTypes().remove(rootType);
            }
        }
        if (!additionalAbstractRootTypes.isEmpty()) {
            node.addError("Incompatible abstract execute methods found %s.", rootTypes);
        }

    }

    private static List<ExecutableTypeData> buildExecutableHierarchy(NodeData node) {
        List<ExecutableTypeData> executes = node.getExecutableTypes();
        if (executes.isEmpty()) {
            return Collections.emptyList();
        }
        List<ExecutableTypeData> hierarchyExecutes = new ArrayList<>(executes);
        Collections.sort(hierarchyExecutes);
        ExecutableTypeData parent = hierarchyExecutes.get(0);
        ListIterator<ExecutableTypeData> executesIterator = hierarchyExecutes.listIterator(1);
        buildExecutableHierarchy(node, parent, executesIterator);
        return hierarchyExecutes;
    }

    private static void buildExecutableHierarchy(NodeData node, ExecutableTypeData parent, ListIterator<ExecutableTypeData> executesIterator) {
        while (executesIterator.hasNext()) {
            ExecutableTypeData other = executesIterator.next();
            if (other.canDelegateTo(node, parent)) {
                parent.addDelegatedFrom(other);
                executesIterator.remove();
            }
        }
        for (int i = 1; i < parent.getDelegatedFrom().size(); i++) {
            buildExecutableHierarchy(node, parent.getDelegatedFrom().get(i - 1), parent.getDelegatedFrom().listIterator(i));
        }
    }

    private List<Element> loadMembers(TypeElement templateType) {
        List<Element> members = new ArrayList<>(CompilerFactory.getCompiler(templateType).getAllMembersInDeclarationOrder(context.getEnvironment(), templateType));

        return members;
    }

    private boolean containsSpecializations(List<Element> elements) {
        boolean foundSpecialization = false;
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            if (ElementUtils.findAnnotationMirror(processingEnv, method, Specialization.class) != null) {
                foundSpecialization = true;
                break;
            }
        }
        return foundSpecialization;
    }

    private void initializeImportGuards(NodeData node, List<TypeElement> lookupTypes, List<Element> elements) {
        for (TypeElement lookupType : lookupTypes) {
            AnnotationMirror importAnnotation = ElementUtils.findAnnotationMirror(processingEnv, lookupType, ImportStatic.class);
            if (importAnnotation == null) {
                continue;
            }
            AnnotationValue importClassesValue = ElementUtils.getAnnotationValue(importAnnotation, "value");
            List<TypeMirror> importClasses = ElementUtils.getAnnotationValueList(TypeMirror.class, importAnnotation, "value");
            if (importClasses.isEmpty()) {
                node.addError(importAnnotation, importClassesValue, "At least import guard classes must be specified.");
                continue;
            }
            for (TypeMirror importGuardClass : importClasses) {
                if (importGuardClass.getKind() != TypeKind.DECLARED) {
                    node.addError(importAnnotation, importClassesValue, "The specified import guard class '%s' is not a declared type.", ElementUtils.getQualifiedName(importGuardClass));
                    continue;
                }

                TypeElement typeElement = ElementUtils.fromTypeMirror(importGuardClass);
                if (typeElement.getEnclosingElement().getKind().isClass() && !typeElement.getModifiers().contains(Modifier.PUBLIC)) {
                    node.addError(importAnnotation, importClassesValue, "The specified import guard class '%s' must be public.", ElementUtils.getQualifiedName(importGuardClass));
                    continue;
                }
                elements.addAll(importPublicStaticMembers(typeElement, false));
            }
        }
    }

    private List<? extends Element> importPublicStaticMembers(TypeElement importGuardClass, boolean includeConstructors) {
        // hack to reload type is necessary for incremental compiling in eclipse.
        // otherwise methods inside of import guard types are just not found.
        TypeElement typeElement = ElementUtils.fromTypeMirror(context.reloadType(importGuardClass.asType()));

        List<Element> members = new ArrayList<>();
        for (Element importElement : processingEnv.getElementUtils().getAllMembers(typeElement)) {
            if (!importElement.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }

            if (includeConstructors && importElement.getKind() == ElementKind.CONSTRUCTOR) {
                members.add(importElement);
            }

            if (!importElement.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }

            ElementKind kind = importElement.getKind();
            if (kind.isField() || kind == ElementKind.METHOD) {
                members.add(importElement);
            }
        }
        return members;
    }

    private NodeData parseNodeData(TypeElement templateType, List<TypeElement> typeHierarchy) {
        AnnotationMirror typeSystemMirror = findFirstAnnotation(typeHierarchy, TypeSystemReference.class);
        TypeSystemData typeSystem = null;
        if (typeSystemMirror != null) {
            TypeMirror typeSystemType = ElementUtils.getAnnotationValue(TypeMirror.class, typeSystemMirror, "value");
            typeSystem = (TypeSystemData) context.getTemplate(typeSystemType, true);
            if (typeSystem == null) {
                NodeData nodeData = new NodeData(context, templateType);
                nodeData.addError("The used type system '%s' is invalid. Fix errors in the type system first.", ElementUtils.getQualifiedName(typeSystemType));
                return nodeData;
            }
        } else {
            // default dummy type system
            typeSystem = new TypeSystemData(context, templateType, null, NodeParser.class.getAnnotation(DSLOptions.class), true);
        }
        AnnotationMirror nodeInfoMirror = findFirstAnnotation(typeHierarchy, NodeInfo.class);
        String shortName = null;
        if (nodeInfoMirror != null) {
            shortName = ElementUtils.getAnnotationValue(String.class, nodeInfoMirror, "shortName");
        }
        boolean useNodeFactory = findFirstAnnotation(typeHierarchy, GenerateNodeFactory.class) != null;
        return new NodeData(context, templateType, shortName, typeSystem, useNodeFactory);

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
                fields.add(new NodeFieldData(field, null, field, false));
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

                NodeFieldData field = new NodeFieldData(typeElement, mirror, new CodeVariableElement(type, name), true);
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

        return filteredChildren;
    }

    private List<NodeExecutionData> parseExecutions(List<NodeFieldData> fields, List<NodeChildData> children, List<? extends Element> elements) {
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

        List<NodeFieldData> nonGetterFields = new ArrayList<>();
        for (NodeFieldData field : fields) {
            if (field.getGetter() == null && field.isGenerated()) {
                nonGetterFields.add(field);
            }
        }

        TypeMirror cacheAnnotation = context.getType(Cached.class);
        List<TypeMirror> frameTypes = context.getFrameTypes();
        // pre-parse specializations to find signature size
        for (ExecutableElement method : methods) {
            AnnotationMirror mirror = ElementUtils.findAnnotationMirror(processingEnv, method, Specialization.class);
            if (mirror == null) {
                continue;
            }
            int currentArgumentIndex = 0;
            boolean skipShortCircuit = false;
            parameter: for (VariableElement var : method.getParameters()) {
                if (skipShortCircuit) {
                    skipShortCircuit = false;
                    continue parameter;
                }

                TypeMirror type = var.asType();
                if (currentArgumentIndex == 0) {
                    // skip optionals
                    for (TypeMirror frameType : frameTypes) {
                        if (ElementUtils.typeEquals(type, frameType)) {
                            continue parameter;
                        }
                    }
                }

                if (currentArgumentIndex < nonGetterFields.size()) {
                    for (NodeFieldData field : nonGetterFields) {
                        if (ElementUtils.typeEquals(var.asType(), field.getType())) {
                            continue parameter;
                        }
                    }
                }

                if (ElementUtils.findAnnotationMirror(var.getAnnotationMirrors(), cacheAnnotation) != null) {
                    continue parameter;
                }

                int childIndex = currentArgumentIndex < children.size() ? currentArgumentIndex : children.size() - 1;
                if (childIndex != -1) {
                    NodeChildData child = children.get(childIndex);
                    if (shortCircuits.contains(NodeExecutionData.createIndexedName(child, currentArgumentIndex - childIndex))) {
                        skipShortCircuit = true;
                    }
                }

                currentArgumentIndex++;
            }
            maxSignatureSize = Math.max(maxSignatureSize, currentArgumentIndex);
        }

        List<NodeExecutionData> executions = new ArrayList<>();
        for (int i = 0; i < maxSignatureSize; i++) {
            boolean varArgParameter = false;
            int childIndex = i;
            if (i >= children.size() - 1) {
                if (hasVarArgs) {
                    varArgParameter = hasVarArgs;
                    childIndex = Math.min(i, children.size() - 1);
                } else if (i >= children.size()) {
                    childIndex = -1;
                }
            }
            int varArgsIndex = -1;
            boolean shortCircuit = false;
            NodeChildData child = null;
            if (childIndex != -1) {
                varArgsIndex = varArgParameter ? Math.abs(childIndex - i) : -1;
                child = children.get(childIndex);
                shortCircuit = shortCircuits.contains(NodeExecutionData.createIndexedName(child, varArgsIndex));
            }
            executions.add(new NodeExecutionData(child, i, varArgsIndex, shortCircuit));
        }
        return executions;
    }

    private List<ExecutableTypeData> parseExecutableTypeData(List<? extends Element> elements, int signatureSize, List<TypeMirror> frameTypes, boolean includeFinals) {
        List<ExecutableTypeData> typeData = new ArrayList<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements)) {
            Set<Modifier> modifiers = method.getModifiers();
            if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC)) {
                continue;
            }
            if (!includeFinals && modifiers.contains(Modifier.FINAL)) {
                continue;
            }

            if (!method.getSimpleName().toString().startsWith("execute")) {
                continue;
            }
            if (ElementUtils.findAnnotationMirror(context.getEnvironment(), method, Specialization.class) != null) {
                continue;
            }

            ExecutableTypeData executableType = new ExecutableTypeData(method, signatureSize, context.getFrameTypes());

            if (executableType.getFrameParameter() != null) {
                boolean supportedType = false;
                for (TypeMirror type : frameTypes) {
                    if (ElementUtils.isAssignable(type, executableType.getFrameParameter())) {
                        supportedType = true;
                        break;
                    }
                }
                if (!supportedType) {
                    continue;
                }
            }

            typeData.add(executableType);
        }

        Collections.sort(typeData);

        List<String> names = new ArrayList<>();
        for (ExecutableTypeData type : typeData) {
            names.add(type.getUniqueName());
        }
        while (renameDuplicateIds(names)) {
            // fix point
        }

        for (int i = 0; i < typeData.size(); i++) {
            typeData.get(i).setUniqueName(names.get(i));
        }

        return typeData;
    }

    private void initializeExecutableTypes(NodeData node) {
        List<ExecutableTypeData> allExecutes = node.getExecutableTypes();

        Set<String> inconsistentFrameTypes = new HashSet<>();
        TypeMirror frameType = null;
        Set<Integer> evaluatedCounts = new HashSet<>();
        for (ExecutableTypeData execute : allExecutes) {
            evaluatedCounts.add(execute.getEvaluatedCount());

            TypeMirror frame = execute.getFrameParameter();
            TypeMirror resolvedFrameType;
            if (frame != null) {
                resolvedFrameType = frame;
                if (frameType == null) {
                    frameType = resolvedFrameType;
                } else if (!ElementUtils.typeEquals(frameType, resolvedFrameType)) {
                    // found inconsistent frame types
                    inconsistentFrameTypes.add(ElementUtils.getSimpleName(frameType));
                    inconsistentFrameTypes.add(ElementUtils.getSimpleName(resolvedFrameType));
                }
            }
        }
        if (!inconsistentFrameTypes.isEmpty()) {
            // ensure they are sorted somehow
            List<String> inconsistentFrameTypesList = new ArrayList<>(inconsistentFrameTypes);
            Collections.sort(inconsistentFrameTypesList);
            node.addError("Invalid inconsistent frame types %s found for the declared execute methods. The frame type must be identical for all execute methods.", inconsistentFrameTypesList);
        }
        if (frameType == null) {
            frameType = context.getType(void.class);
        }

        node.setFrameType(frameType);

        boolean genericFound = false;
        for (ExecutableTypeData type : node.getExecutableTypes()) {
            if (!type.hasUnexpectedValue(context)) {
                genericFound = true;
                break;
            }
        }

        // no generic executes
        if (!genericFound) {
            node.addError("No accessible and overridable generic execute method found. Generic execute methods usually have the "
                            + "signature 'public abstract {Type} execute(VirtualFrame)' and must not throw any checked exceptions.");
        }

        int nodeChildDeclarations = 0;
        int nodeChildDeclarationsRequired = 0;
        List<NodeExecutionData> executions = node.getChildExecutions();
        for (NodeExecutionData execution : executions) {
            if (execution.getChild() == null) {
                nodeChildDeclarationsRequired = execution.getIndex() + 1;
            } else {
                nodeChildDeclarations++;
            }
        }

        List<String> requireNodeChildDeclarations = new ArrayList<>();
        for (ExecutableTypeData type : allExecutes) {
            if (type.getEvaluatedCount() < nodeChildDeclarationsRequired) {
                requireNodeChildDeclarations.add(ElementUtils.createReferenceName(type.getMethod()));
            }
        }

        if (!requireNodeChildDeclarations.isEmpty()) {
            node.addError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. "
                            + "The following execute methods do not provide all evaluated values for the expected signature size %s: %s.", executions.size(), requireNodeChildDeclarations);
        }

        if (nodeChildDeclarations > 0 && executions.size() == node.getMinimalEvaluatedParameters()) {
            for (NodeChildData child : node.getChildren()) {
                child.addError("Unnecessary @NodeChild declaration. All evaluated child values are provided as parameters in execute methods.");
            }
        }

    }

    private void initializeChildren(NodeData node) {
        initializeExecuteWith(node);

        for (NodeChildData child : node.getChildren()) {
            TypeMirror nodeType = child.getNodeType();
            NodeData fieldNodeData = parseChildNodeData(node, child, ElementUtils.fromTypeMirror(nodeType));

            child.setNode(fieldNodeData);
            if (fieldNodeData == null || fieldNodeData.hasErrors()) {
                child.addError("Node type '%s' is invalid or not a subclass of Node.", ElementUtils.getQualifiedName(nodeType));
            } else {
                List<ExecutableTypeData> types = child.findGenericExecutableTypes(context);
                if (types.isEmpty()) {
                    AnnotationValue executeWithValue = ElementUtils.getAnnotationValue(child.getMessageAnnotation(), "executeWith");
                    child.addError(executeWithValue, "No generic execute method found with %s evaluated arguments for node type %s and frame types %s.", child.getExecuteWith().size(),
                                    ElementUtils.getSimpleName(nodeType), ElementUtils.getUniqueIdentifiers(createAllowedChildFrameTypes(node)));
                }
            }
        }
    }

    private static void initializeExecuteWith(NodeData node) {
        for (NodeChildData child : node.getChildren()) {
            List<String> executeWithStrings = ElementUtils.getAnnotationValueList(String.class, child.getMessageAnnotation(), "executeWith");
            AnnotationValue executeWithValue = ElementUtils.getAnnotationValue(child.getMessageAnnotation(), "executeWith");
            List<NodeExecutionData> executeWith = new ArrayList<>();
            for (String executeWithString : executeWithStrings) {
                if (child.getName().equals(executeWithString)) {
                    child.addError(executeWithValue, "The child node '%s' cannot be executed with itself.", executeWithString);
                    continue;
                }
                NodeExecutionData found = null;
                boolean before = true;
                for (NodeExecutionData resolveChild : node.getChildExecutions()) {
                    if (resolveChild.getChild() == child) {
                        before = false;
                        continue;
                    }
                    if (resolveChild.getIndexedName().equals(executeWithString)) {
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
        }
    }

    private NodeData parseChildNodeData(NodeData parentNode, NodeChildData child, TypeElement originalTemplateType) {
        TypeElement templateType = ElementUtils.fromTypeMirror(context.reloadTypeElement(originalTemplateType));

        if (ElementUtils.findAnnotationMirror(processingEnv, originalTemplateType, GeneratedBy.class) != null) {
            // generated nodes should not get called again.
            return null;
        }

        if (!ElementUtils.isAssignable(templateType.asType(), context.getTruffleTypes().getNode())) {
            return null;
        }

        List<TypeElement> lookupTypes = collectSuperClasses(new ArrayList<TypeElement>(), templateType);

        // Declaration order is not required for child nodes.
        List<? extends Element> members = processingEnv.getElementUtils().getAllMembers(templateType);
        NodeData node = parseNodeData(templateType, lookupTypes);
        if (node.hasErrors()) {
            return node;
        }
        List<TypeMirror> frameTypes = Collections.emptyList();
        if (parentNode.getFrameType() != null) {
            frameTypes = Arrays.asList(parentNode.getFrameType());
        }
        node.getExecutableTypes().addAll(parseExecutableTypeData(members, child.getExecuteWith().size(), frameTypes, true));
        node.setFrameType(parentNode.getFrameType());
        return node;
    }

    private List<TypeMirror> createAllowedChildFrameTypes(NodeData parentNode) {
        List<TypeMirror> allowedFrameTypes = new ArrayList<>();
        for (TypeMirror frameType : context.getFrameTypes()) {
            if (ElementUtils.isAssignable(parentNode.getFrameType(), frameType)) {
                allowedFrameTypes.add(frameType);
            }
        }
        return allowedFrameTypes;
    }

    private void initializeSpecializations(List<? extends Element> elements, final NodeData node) {
        if (node.getSpecializations().isEmpty()) {
            return;
        }

        initializeExpressions(elements, node);

        if (node.hasErrors()) {
            return;
        }

        initializeGeneric(node);
        initializeUninitialized(node);
        initializeOrder(node);
        initializePolymorphism(node); // requires specializations
        initializeReachability(node);
        initializeContains(node);
        resolveContains(node);

        List<SpecializationData> specializations = node.getSpecializations();
        for (SpecializationData cur : specializations) {
            for (SpecializationData contained : cur.getContains()) {
                if (contained != cur) {
                    contained.getExcludedBy().add(cur);
                }
            }
        }

        initializeSpecializationIdsWithMethodNames(node.getSpecializations());
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

        for (int i = 0; i < specializations.size(); i++) {
            SpecializationData specialization = specializations.get(i);
            SpecializationData insertBefore = specialization.getInsertBefore();
            if (insertBefore != null) {
                int insertIndex = specializations.indexOf(insertBefore);
                if (insertIndex < i) {
                    specializations.remove(i);
                    specializations.add(insertIndex, specialization);
                }
            }
        }

        for (int i = 0; i < specializations.size(); i++) {
            specializations.get(i).setIndex(i);
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
                    if (foundSpecialization.compareTo(specialization) > 0) {
                        AnnotationValue value = ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "contains");
                        if (foundSpecialization.compareTo(specialization) > 0) {
                            specialization.addError(value, "The contained specialization '%s' must be declared before the containing specialization.", includeName);
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
                current.addError("%s is not reachable. It is shadowed by %s.", current.isFallback() ? "Generic" : "Specialization", name);
            }
            current.setReachable(shadowedBy == null);
        }
    }

    private static void initializeSpecializationIdsWithMethodNames(List<SpecializationData> specializations) {
        List<String> signatures = new ArrayList<>();
        for (SpecializationData specialization : specializations) {
            if (specialization.isFallback()) {
                signatures.add("Fallback");
            } else if (specialization.isUninitialized()) {
                signatures.add("Uninitialized");
            } else if (specialization.isPolymorphic()) {
                signatures.add("Polymorphic");
            } else {
                String name = specialization.getMethodName();

                // hack for name clashes with BaseNode.
                if (name.equalsIgnoreCase("base")) {
                    name = name + "0";
                } else if (name.startsWith("do")) {
                    String filteredDo = name.substring(2, name.length());
                    if (!filteredDo.isEmpty() && Character.isJavaIdentifierStart(filteredDo.charAt(0))) {
                        name = filteredDo;
                    }
                }
                signatures.add(ElementUtils.firstLetterUpperCase(name));
            }
        }

        while (renameDuplicateIds(signatures)) {
            // fix point
        }

        for (int i = 0; i < specializations.size(); i++) {
            specializations.get(i).setId(signatures.get(i));
        }
    }

    private static boolean renameDuplicateIds(List<String> signatures) {
        boolean changed = false;
        Map<String, Integer> counts = new HashMap<>();
        for (String s1 : signatures) {
            Integer count = counts.get(s1.toLowerCase());
            if (count == null) {
                count = 0;
            }
            count++;
            counts.put(s1.toLowerCase(), count);
        }

        for (String s : counts.keySet()) {
            int count = counts.get(s);
            if (count > 1) {
                changed = true;
                int number = 0;
                for (ListIterator<String> iterator = signatures.listIterator(); iterator.hasNext();) {
                    String s2 = iterator.next();
                    if (s.equalsIgnoreCase(s2)) {
                        iterator.set(s2 + number);
                        number++;
                    }
                }
            }
        }
        return changed;
    }

    private void initializeExpressions(List<? extends Element> elements, NodeData node) {
        List<Element> members = filterNotAccessibleElements(node.getTemplateType(), elements);

        List<VariableElement> fields = new ArrayList<>();
        for (NodeFieldData field : node.getFields()) {
            fields.add(field.getVariable());
        }

        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getMethod() == null) {
                continue;
            }

            List<Element> specializationMembers = new ArrayList<>(members.size() + specialization.getParameters().size() + fields.size());
            for (Parameter p : specialization.getParameters()) {
                specializationMembers.add(p.getVariableElement());
            }
            specializationMembers.addAll(fields);
            specializationMembers.addAll(members);
            DSLExpressionResolver resolver = new DSLExpressionResolver(context, specializationMembers);

            initializeCaches(specialization, resolver);
            initializeGuards(specialization, resolver);
            if (specialization.hasErrors()) {
                continue;
            }
            initializeLimit(specialization, resolver);
            initializeAssumptions(specialization, resolver);
        }
    }

    private void initializeAssumptions(SpecializationData specialization, DSLExpressionResolver resolver) {
        final DeclaredType assumptionType = context.getDeclaredType(Assumption.class);
        final TypeMirror assumptionArrayType = new ArrayCodeTypeMirror(assumptionType);
        final List<String> assumptionDefinitions = ElementUtils.getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "assumptions");
        List<AssumptionExpression> assumptionExpressions = new ArrayList<>();
        int assumptionId = 0;
        for (String assumption : assumptionDefinitions) {
            AssumptionExpression assumptionExpression;
            DSLExpression expression = null;
            try {
                expression = DSLExpression.parse(assumption);
                expression.accept(resolver);
                assumptionExpression = new AssumptionExpression(specialization, expression, "assumption" + assumptionId);
                if (!ElementUtils.isAssignable(expression.getResolvedType(), assumptionType) && !ElementUtils.isAssignable(expression.getResolvedType(), assumptionArrayType)) {
                    assumptionExpression.addError("Incompatible return type %s. Assumptions must be assignable to %s or %s.", ElementUtils.getSimpleName(expression.getResolvedType()),
                                    ElementUtils.getSimpleName(assumptionType), ElementUtils.getSimpleName(assumptionArrayType));
                }
                if (specialization.isDynamicParameterBound(expression)) {
                    specialization.addError("Assumption expressions must not bind dynamic parameter values.");
                }
            } catch (InvalidExpressionException e) {
                assumptionExpression = new AssumptionExpression(specialization, null, "assumption" + assumptionId);
                assumptionExpression.addError("Error parsing expression '%s': %s", assumption, e.getMessage());
            }
            assumptionExpressions.add(assumptionExpression);
        }
        specialization.setAssumptionExpressions(assumptionExpressions);
    }

    private void initializeLimit(SpecializationData specialization, DSLExpressionResolver resolver) {
        AnnotationValue annotationValue = ElementUtils.getAnnotationValue(specialization.getMessageAnnotation(), "limit");

        String limitValue;
        if (annotationValue == null) {
            limitValue = "";
        } else {
            limitValue = (String) annotationValue.getValue();
        }
        if (limitValue.isEmpty()) {
            limitValue = "3";
        } else if (!specialization.hasMultipleInstances()) {
            specialization.addWarning(annotationValue, "The limit expression has no effect. Multiple specialization instantiations are impossible for this specialization.");
            return;
        }

        TypeMirror expectedType = context.getType(int.class);
        try {
            DSLExpression expression = DSLExpression.parse(limitValue);
            expression.accept(resolver);
            if (!ElementUtils.typeEquals(expression.getResolvedType(), expectedType)) {
                specialization.addError(annotationValue, "Incompatible return type %s. Limit expressions must return %s.", ElementUtils.getSimpleName(expression.getResolvedType()),
                                ElementUtils.getSimpleName(expectedType));
            }
            if (specialization.isDynamicParameterBound(expression)) {
                specialization.addError(annotationValue, "Limit expressions must not bind dynamic parameter values.");
            }

            specialization.setLimitExpression(expression);
        } catch (InvalidExpressionException e) {
            specialization.addError(annotationValue, "Error parsing expression '%s': %s", limitValue, e.getMessage());
        }
    }

    private void initializeCaches(SpecializationData specialization, DSLExpressionResolver resolver) {
        TypeMirror cacheMirror = context.getType(Cached.class);
        List<CacheExpression> expressions = new ArrayList<>();
        for (Parameter parameter : specialization.getParameters()) {
            AnnotationMirror annotationMirror = ElementUtils.findAnnotationMirror(parameter.getVariableElement().getAnnotationMirrors(), cacheMirror);
            if (annotationMirror != null) {
                String initializer = ElementUtils.getAnnotationValue(String.class, annotationMirror, "value");

                TypeMirror parameterType = parameter.getType();

                DSLExpressionResolver localResolver = resolver;
                if (parameterType.getKind() == TypeKind.DECLARED) {
                    localResolver = localResolver.copy(importPublicStaticMembers(ElementUtils.fromTypeMirror(parameterType), true));
                }

                CacheExpression cacheExpression;
                DSLExpression expression = null;
                try {
                    expression = DSLExpression.parse(initializer);
                    expression.accept(localResolver);
                    cacheExpression = new CacheExpression(parameter, annotationMirror, expression);
                    if (!ElementUtils.typeEquals(expression.getResolvedType(), parameter.getType())) {
                        cacheExpression.addError("Incompatible return type %s. The expression type must be equal to the parameter type %s.", ElementUtils.getSimpleName(expression.getResolvedType()),
                                        ElementUtils.getSimpleName(parameter.getType()));
                    }
                } catch (InvalidExpressionException e) {
                    cacheExpression = new CacheExpression(parameter, annotationMirror, null);
                    cacheExpression.addError("Error parsing expression '%s': %s", initializer, e.getMessage());
                }
                expressions.add(cacheExpression);
            }
        }
        specialization.setCaches(expressions);

        if (specialization.hasErrors()) {
            return;
        }

        // verify that cache expressions are bound in the correct order.
        for (int i = 0; i < expressions.size(); i++) {
            CacheExpression currentExpression = expressions.get(i);
            Set<VariableElement> boundVariables = currentExpression.getExpression().findBoundVariableElements();
            for (int j = i + 1; j < expressions.size(); j++) {
                CacheExpression boundExpression = expressions.get(j);
                if (boundVariables.contains(boundExpression.getParameter().getVariableElement())) {
                    currentExpression.addError("The initializer expression of parameter '%s' binds unitialized parameter '%s. Reorder the parameters to resolve the problem.",
                                    currentExpression.getParameter().getLocalName(), boundExpression.getParameter().getLocalName());
                    break;
                }
            }
        }
    }

    private void initializeGuards(SpecializationData specialization, DSLExpressionResolver resolver) {
        final TypeMirror booleanType = context.getType(boolean.class);
        List<String> guardDefinitions = ElementUtils.getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "guards");
        List<GuardExpression> guardExpressions = new ArrayList<>();
        for (String guard : guardDefinitions) {
            GuardExpression guardExpression;
            DSLExpression expression = null;
            try {
                expression = DSLExpression.parse(guard);
                expression.accept(resolver);
                guardExpression = new GuardExpression(specialization, expression);
                if (!ElementUtils.typeEquals(expression.getResolvedType(), booleanType)) {
                    guardExpression.addError("Incompatible return type %s. Guards must return %s.", ElementUtils.getSimpleName(expression.getResolvedType()), ElementUtils.getSimpleName(booleanType));
                }
            } catch (InvalidExpressionException e) {
                guardExpression = new GuardExpression(specialization, null);
                guardExpression.addError("Error parsing expression '%s': %s", guard, e.getMessage());
            }
            guardExpressions.add(guardExpression);
        }
        specialization.setGuards(guardExpressions);
    }

    private static List<Element> filterNotAccessibleElements(TypeElement templateType, List<? extends Element> elements) {
        String packageName = ElementUtils.getPackageName(templateType);
        List<Element> filteredElements = new ArrayList<>(elements);
        for (Element element : elements) {
            Modifier visibility = ElementUtils.getVisibility(element.getModifiers());
            if (visibility == Modifier.PRIVATE) {
                continue;
            } else if (visibility == null) {
                String elementPackageName = ElementUtils.getPackageName(element.getEnclosingElement().asType());
                if (!Objects.equals(packageName, elementPackageName) && !elementPackageName.equals("java.lang")) {
                    continue;
                }
            }

            filteredElements.add(element);
        }
        return filteredElements;
    }

    private void initializeGeneric(final NodeData node) {
        List<SpecializationData> generics = new ArrayList<>();
        for (SpecializationData spec : node.getSpecializations()) {
            if (spec.isFallback()) {
                generics.add(spec);
            }
        }

        if (generics.size() == 1 && node.getSpecializations().size() == 1) {
            // TODO this limitation should be lifted
            for (SpecializationData generic : generics) {
                generic.addError("@%s defined but no @%s.", Fallback.class.getSimpleName(), Specialization.class.getSimpleName());
            }
        }

        if (generics.isEmpty()) {
            node.getSpecializations().add(createGenericSpecialization(node));
        } else {
            if (generics.size() > 1) {
                for (SpecializationData generic : generics) {
                    generic.addError("Only one @%s is allowed per operation.", Fallback.class.getSimpleName());
                }
            }
        }
    }

    private SpecializationData createGenericSpecialization(final NodeData node) {
        GenericParser parser = new GenericParser(context, node);
        MethodSpec specification = parser.createDefaultMethodSpec(node.getSpecializations().iterator().next().getMethod(), null, true, null);

        List<VariableElement> parameterTypes = new ArrayList<>();
        int signatureIndex = 1;
        for (ParameterSpec spec : specification.getRequired()) {
            parameterTypes.add(new CodeVariableElement(createGenericType(node, spec), "arg" + signatureIndex));
            if (spec.isSignature()) {
                signatureIndex++;
            }
        }

        TypeMirror returnType = createGenericType(node, specification.getReturnType());
        SpecializationData generic = parser.create("Generic", TemplateMethod.NO_NATURAL_ORDER, null, null, returnType, parameterTypes);
        if (generic == null) {
            throw new RuntimeException("Unable to create generic signature for node " + node.getNodeId() + " with " + parameterTypes + ". Specification " + specification + ".");
        }

        return generic;
    }

    private TypeMirror createGenericType(NodeData node, ParameterSpec spec) {
        NodeExecutionData execution = spec.getExecution();
        Collection<TypeMirror> allowedTypes;
        if (execution == null) {
            allowedTypes = spec.getAllowedTypes();
        } else {
            allowedTypes = node.getGenericTypes(execution);
        }
        if (allowedTypes.size() == 1) {
            return allowedTypes.iterator().next();
        } else {
            return ElementUtils.getCommonSuperType(context, allowedTypes.toArray(new TypeMirror[allowedTypes.size()]));
        }
    }

    private void initializeUninitialized(final NodeData node) {
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
                generic.replaceParameter(parameter.getLocalName(), new Parameter(parameter, context.getType(Object.class)));
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

        List<VariableElement> types = new ArrayList<>();

        Set<TypeMirror> frameTypes = new HashSet<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getFrame() != null) {
                frameTypes.add(specialization.getFrame().getType());
            }
        }
        if (!frameTypes.isEmpty()) {
            TypeMirror frameType;
            if (frameTypes.size() == 1) {
                frameType = frameTypes.iterator().next();
            } else {
                frameType = context.getType(Frame.class);
            }
            types.add(new CodeVariableElement(frameType, "frameValue"));
        }

        TypeMirror returnType = null;
        int index = 0;
        for (Parameter genericParameter : generic.getReturnTypeAndParameters()) {
            TypeMirror polymorphicType;
            if (!genericParameter.getSpecification().isSignature()) {
                polymorphicType = genericParameter.getType();
            } else {
                Set<TypeMirror> usedTypes = new HashSet<>();
                for (SpecializationData specialization : node.getSpecializations()) {
                    if (specialization.isUninitialized()) {
                        continue;
                    }
                    Parameter parameter = specialization.findParameter(genericParameter.getLocalName());
                    if (parameter == specialization.getReturnType() && specialization.isFallback() && specialization.getMethod() == null) {
                        continue;
                    }
                    if (parameter == null) {
                        throw new AssertionError("Parameter existed in generic specialization but not in specialized. param = " + genericParameter.getLocalName());
                    }
                    usedTypes.add(parameter.getType());
                }

                if (usedTypes.size() == 1) {
                    polymorphicType = usedTypes.iterator().next();

                    if (node.getTypeSystem().hasImplicitSourceTypes(polymorphicType)) {
                        polymorphicType = context.getType(Object.class);
                    }
                } else {
                    polymorphicType = context.getType(Object.class);
                }
            }
            if (genericParameter == generic.getReturnType()) {
                returnType = polymorphicType;
            } else {
                types.add(new CodeVariableElement(polymorphicType, "param" + index));
            }
            index++;
        }

        SpecializationMethodParser parser = new SpecializationMethodParser(context, node);

        SpecializationData polymorphic = parser.create("Polymorphic", TemplateMethod.NO_NATURAL_ORDER, null, null, returnType, types);
        polymorphic.setKind(SpecializationKind.POLYMORPHIC);
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
            String valueName = execution.getIndexedName();
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
                List<ShortCircuitData> availableShortCuts = groupedShortCircuits.get(shortCircuit.getIndexedName());

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
                if (ElementUtils.typeEquals(executable.getReturnType(), parameter.getType())) {
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
        for (ExecutableElement method : nodeData.getAllTemplateMethods()) {
            unusedElements.remove(method);
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

    private static void verifyConstructors(NodeData nodeData) {
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(nodeData.getTemplateType().getEnclosedElements());
        if (constructors.isEmpty()) {
            return;
        }

        boolean oneNonPrivate = false;
        for (ExecutableElement constructor : constructors) {
            if (ElementUtils.getVisibility(constructor.getModifiers()) != Modifier.PRIVATE) {
                oneNonPrivate = true;
                break;
            }
        }
        if (!oneNonPrivate && !nodeData.getTemplateType().getModifiers().contains(Modifier.PRIVATE)) {
            nodeData.addError("At least one constructor must be non-private.");
        }
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
