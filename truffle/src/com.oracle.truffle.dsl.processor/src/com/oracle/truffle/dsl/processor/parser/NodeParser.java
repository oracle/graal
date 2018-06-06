/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.Executed;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.NodeFields;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.dsl.processor.CompileErrorException;
import com.oracle.truffle.dsl.processor.Log;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpressionResolver;
import com.oracle.truffle.dsl.processor.expression.InvalidExpressionException;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.AssumptionExpression;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.GuardExpression;
import com.oracle.truffle.dsl.processor.model.MethodSpec;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeChildData.Cardinality;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.NodeFieldData;
import com.oracle.truffle.dsl.processor.model.Parameter;
import com.oracle.truffle.dsl.processor.model.ParameterSpec;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.SpecializationData.SpecializationKind;
import com.oracle.truffle.dsl.processor.model.SpecializationThrowsData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;

public class NodeParser extends AbstractParser<NodeData> {

    public static final List<Class<? extends Annotation>> ANNOTATIONS = Arrays.asList(Fallback.class, TypeSystemReference.class,
                    Specialization.class,
                    NodeChild.class,
                    Executed.class,
                    NodeChildren.class,
                    ReportPolymorphism.class);

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

        AnnotationMirror reflectable = findFirstAnnotation(lookupTypes, Introspectable.class);
        if (reflectable != null) {
            node.setReflectable(true);
        }

        AnnotationMirror reportPolymorphism = findFirstAnnotation(lookupTypes, ReportPolymorphism.class);
        AnnotationMirror excludePolymorphism = findFirstAnnotation(lookupTypes, ReportPolymorphism.Exclude.class);
        if (reportPolymorphism != null && excludePolymorphism == null) {
            node.setReportPolymorphism(true);
        }
        node.getFields().addAll(parseFields(lookupTypes, members));
        node.getChildren().addAll(parseChildren(node, lookupTypes, members));
        node.getChildExecutions().addAll(parseExecutions(node.getFields(), node.getChildren(), members));
        node.getExecutableTypes().addAll(parseExecutableTypeData(node, members, node.getSignatureSize(), context.getFrameTypes(), false));

        initializeExecutableTypes(node);
        initializeImportGuards(node, lookupTypes, members);
        initializeChildren(node);

        if (node.hasErrors()) {
            return node; // error sync point
        }

        node.getSpecializations().addAll(new SpecializationMethodParser(context, node).parse(members));
        node.getSpecializations().addAll(new FallbackParser(context, node).parse(members));
        node.getCasts().addAll(new CreateCastParser(context, node).parse(members));

        if (node.hasErrors()) {
            return node;  // error sync point
        }
        initializeSpecializations(members, node);
        initializeExecutableTypeHierarchy(node);

        verifySpecializationSameLength(node);
        verifyVisibilities(node);
        verifyMissingAbstractMethods(node, members);
        verifyConstructors(node);
        verifySpecializationThrows(node);
        return node;
    }

    private static void initializeFallbackReachability(NodeData node) {
        List<SpecializationData> specializations = node.getSpecializations();
        SpecializationData fallback = null;
        for (int i = specializations.size() - 1; i >= 0; i--) {
            SpecializationData specialization = specializations.get(i);
            if (specialization.isFallback() && specialization.getMethod() != null) {
                fallback = specialization;
                break;
            }
        }

        if (fallback == null) {
            // no need to compute reachability
            return;
        }

        for (int index = 0; index < specializations.size(); index++) {
            SpecializationData specialization = specializations.get(index);
            SpecializationData lastReachable = specialization;
            for (int searchIndex = index + 1; searchIndex < specializations.size(); searchIndex++) {
                SpecializationData search = specializations.get(searchIndex);
                if (search == fallback) {
                    // reached the end of the specialization
                    break;
                }
                assert lastReachable != search;
                if (!lastReachable.isReachableAfter(search)) {
                    lastReachable = search;
                } else if (search.getReplaces().contains(specialization)) {
                    lastReachable = search;
                }
            }

            specialization.setReachesFallback(lastReachable == specialization);

            List<SpecializationData> failedSpecializations = null;
            if (specialization.isReachesFallback() && !specialization.getCaches().isEmpty() && !specialization.getGuards().isEmpty()) {
                boolean guardBoundByCache = false;
                for (GuardExpression guard : specialization.getGuards()) {
                    if (specialization.isGuardBoundWithCache(guard)) {
                        guardBoundByCache = true;
                        break;
                    }
                }

                if (guardBoundByCache && specialization.getMaximumNumberOfInstances() > 1) {
                    if (failedSpecializations == null) {
                        failedSpecializations = new ArrayList<>();
                    }
                    failedSpecializations.add(specialization);
                }
            }

            if (failedSpecializations != null) {
                List<String> specializationIds = failedSpecializations.stream().map((e) -> e.getId()).collect(Collectors.toList());

                fallback.addError(
                                "Some guards for the following specializations could not be negated for the @%s specialization: %s. " +
                                                "Guards cannot be negated for the @%s when they bind @%s parameters and the specialization may consist of multiple instances. " +
                                                "To fix this limit the number of instances to '1' or " +
                                                "introduce a more generic specialization declared between this specialization and the fallback. " +
                                                "Alternatively the use of @%s can be avoided by declaring a @%s with manually specified negated guards.",
                                Fallback.class.getSimpleName(), specializationIds, Fallback.class.getSimpleName(), Cached.class.getSimpleName(), Fallback.class.getSimpleName(),
                                Specialization.class.getSimpleName());
            }

        }
    }

    private static void initializeExecutableTypeHierarchy(NodeData node) {
        SpecializationData polymorphic = node.getPolymorphicSpecialization();
        if (polymorphic != null) {
            boolean polymorphicSignatureFound = false;
            List<TypeMirror> dynamicTypes = polymorphic.getDynamicTypes();
            TypeMirror frame = null;
            if (polymorphic.getFrame() != null) {
                frame = dynamicTypes.remove(0);
            }

            ExecutableTypeData polymorphicType = new ExecutableTypeData(node, polymorphic.getReturnType().getType(), "execute", frame, dynamicTypes);
            String genericName = ExecutableTypeData.createName(polymorphicType) + "_";
            polymorphicType.setUniqueName(genericName);

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
            node.addError("Incompatible abstract execute methods found %s.", additionalAbstractRootTypes);
        }

        namesUnique(node.getExecutableTypes());

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
            if (other.canDelegateTo(parent)) {
                parent.addDelegatedFrom(other);
                executesIterator.remove();
            }
        }
        for (int i = 1; i < parent.getDelegatedFrom().size(); i++) {
            buildExecutableHierarchy(node, parent.getDelegatedFrom().get(i - 1), parent.getDelegatedFrom().listIterator(i));
        }
    }

    private List<Element> loadMembers(TypeElement templateType) {
        return newElementList(CompilerFactory.getCompiler(templateType).getAllMembersInDeclarationOrder(context.getEnvironment(), templateType));
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

    @SuppressWarnings("unchecked")
    private List<Element> importPublicStaticMembers(TypeElement importGuardClass, boolean includeConstructors) {
        // hack to reload type is necessary for incremental compiling in eclipse.
        // otherwise methods inside of import guard types are just not found.
        TypeElement typeElement = ElementUtils.fromTypeMirror(context.reloadType(importGuardClass.asType()));

        List<Element> members = new ArrayList<>();
        List<Element> typeElementMembers = (List<Element>) processingEnv.getElementUtils().getAllMembers(typeElement);

        // add default constructor
        if (typeElement.getModifiers().contains(Modifier.PUBLIC) && ElementFilter.constructorsIn(typeElementMembers).isEmpty()) {
            typeElementMembers = new ArrayList<>(typeElementMembers);
            typeElementMembers.add(new CodeExecutableElement(ElementUtils.modifiers(Modifier.PUBLIC), typeElement.asType(), null));
        }

        for (Element importElement : typeElementMembers) {
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

        /*
         * Sort elements by enclosing type to ensure that duplicate static methods are used from the
         * most concrete subtype.
         */
        Collections.sort(members, new Comparator<Element>() {
            Map<TypeMirror, Set<String>> cachedQualifiedNames = new HashMap<>();

            public int compare(Element o1, Element o2) {
                TypeMirror e1 = o1.getEnclosingElement() != null ? o1.getEnclosingElement().asType() : null;
                TypeMirror e2 = o2.getEnclosingElement() != null ? o2.getEnclosingElement().asType() : null;

                Set<String> e1SuperTypes = getCachedSuperTypes(e1);
                Set<String> e2SuperTypes = getCachedSuperTypes(e2);
                return ElementUtils.compareByTypeHierarchy(e1, e1SuperTypes, e2, e2SuperTypes);
            }

            private Set<String> getCachedSuperTypes(TypeMirror e) {
                if (e == null) {
                    return Collections.emptySet();
                }
                Set<String> superTypes = cachedQualifiedNames.get(e);
                if (superTypes == null) {
                    superTypes = new HashSet<>(ElementUtils.getQualifiedSuperTypeNames(ElementUtils.fromTypeMirror(e)));
                    cachedQualifiedNames.put(e, superTypes);
                }
                return superTypes;
            }
        });

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
            typeSystem = new TypeSystemData(context, templateType, null, true);
        }
        boolean useNodeFactory = findFirstAnnotation(typeHierarchy, GenerateNodeFactory.class) != null;
        return new NodeData(context, templateType, typeSystem, useNodeFactory);

    }

    private List<NodeFieldData> parseFields(List<TypeElement> typeHierarchy, List<? extends Element> elements) {
        Set<String> names = new HashSet<>();

        List<NodeFieldData> fields = new ArrayList<>();
        for (VariableElement field : ElementFilter.fieldsIn(elements)) {
            if (field.getModifiers().contains(Modifier.STATIC)) {
                continue;
            } else if (field.getAnnotation(Executed.class) != null) {
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

                if (type != null) {
                    NodeFieldData field = new NodeFieldData(typeElement, mirror, new CodeVariableElement(type, name), true);
                    if (name.isEmpty()) {
                        field.addError(ElementUtils.getAnnotationValue(mirror, "name"), "Field name cannot be empty.");
                    } else if (names.contains(name)) {
                        field.addError(ElementUtils.getAnnotationValue(mirror, "name"), "Duplicate field name '%s'.", name);
                    }

                    names.add(name);

                    fields.add(field);
                } else {
                    // Type is null here. This indicates that the type could not be resolved.
                    // The Java compiler will subsequently raise the appropriate error.
                }
            }
        }

        for (NodeFieldData nodeFieldData : fields) {
            nodeFieldData.setGetter(findGetter(elements, nodeFieldData.getName(), nodeFieldData.getType()));
        }

        return fields;
    }

    private List<NodeChildData> parseChildren(NodeData node, final List<TypeElement> typeHierarchy, List<? extends Element> elements) {
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

        List<NodeChildData> executedFieldChildren = new ArrayList<>();
        for (VariableElement field : ElementFilter.fieldsIn(elements)) {
            if (field.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            AnnotationMirror executed = ElementUtils.findAnnotationMirror(field.getAnnotationMirrors(), context.getDeclaredType(Executed.class));
            if (executed != null) {
                TypeMirror type = field.asType();
                String name = field.getSimpleName().toString();
                Cardinality cardinality = Cardinality.ONE;
                if (type.getKind() == TypeKind.ARRAY) {
                    cardinality = Cardinality.MANY;
                }
                AnnotationValue executedWith = ElementUtils.getAnnotationValue(executed, "with");
                NodeChildData child = new NodeChildData(field, executed, name, type, type, field, cardinality, executedWith);
                executedFieldChildren.add(child);

                if (field.getModifiers().contains(Modifier.PRIVATE)) {
                    child.addError("Field annotated with @%s must be visible for the generated subclass to execute.", Executed.class.getSimpleName());
                }

                if (cardinality == Cardinality.ONE) {
                    if (field.getAnnotation(Child.class) == null) {
                        child.addError("Field annotated with @%s must also be annotated with @%s.", Executed.class.getSimpleName(), Child.class.getSimpleName());
                    }
                } else {
                    assert cardinality == Cardinality.MANY;
                    if (field.getAnnotation(Children.class) == null) {
                        child.addError("Field annotated with @%s must also be annotated with @%s.", Executed.class.getSimpleName(), Children.class.getSimpleName());
                    }
                }
            }
        }

        NodeChildData many = null;
        Set<String> names = new HashSet<>();
        for (NodeChildData child : executedFieldChildren) {
            if (child.needsGeneratedField()) {
                throw new AssertionError("Should not need generated field.");
            }
            if (names.contains(child.getName())) {
                child.addError("Field annotated with @%s has duplicate name '%s'. " +
                                "Executed children must have unique names.", Executed.class.getSimpleName(), child.getName());
            } else if (many != null) {
                child.addError("Field annotated with @%s is hidden by executed field '%s'. " +
                                "Executed child fields with multiple children hide all following executed child declarations. " +
                                "Reorder or remove this executed child declaration.", Executed.class.getSimpleName(), many.getName());
            } else if (child.getCardinality().isMany()) {
                many = child;
            }
            names.add(child.getName());
        }

        List<NodeChildData> nodeChildren = new ArrayList<>();
        List<TypeElement> typeHierarchyReversed = new ArrayList<>(typeHierarchy);
        Collections.reverse(typeHierarchyReversed);
        for (TypeElement type : typeHierarchyReversed) {
            AnnotationMirror nodeChildrenMirror = ElementUtils.findAnnotationMirror(processingEnv, type, NodeChildren.class);

            TypeMirror nodeClassType = type.getSuperclass();
            if (nodeClassType.getKind() == TypeKind.NONE || !ElementUtils.isAssignable(nodeClassType, context.getTruffleTypes().getNode())) {
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

                TypeMirror childNodeType = inheritType(childMirror, "type", nodeClassType);
                if (childNodeType.getKind() == TypeKind.ARRAY) {
                    cardinality = Cardinality.MANY;
                }

                TypeMirror originalChildType = childNodeType;
                TypeMirror castNodeType = castNodeTypes.get(name);
                if (castNodeType != null) {
                    childNodeType = castNodeType;
                }

                Element getter = findGetter(elements, name, childNodeType);
                AnnotationValue executeWith = ElementUtils.getAnnotationValue(childMirror, "executeWith");
                NodeChildData nodeChild = new NodeChildData(type, childMirror, name, childNodeType, originalChildType, getter, cardinality, executeWith);

                nodeChildren.add(nodeChild);

                if (nodeChild.getNodeType() == null) {
                    nodeChild.addError("No valid node type could be resoleved.");
                }
                if (nodeChild.hasErrors()) {
                    continue;
                }

                index++;
            }
        }

        if (!nodeChildren.isEmpty() && !executedFieldChildren.isEmpty()) {
            node.addError("The use of @%s and @%s at the same time is not supported.", NodeChild.class.getSimpleName(), Executed.class.getSimpleName());
            return executedFieldChildren;
        } else if (!executedFieldChildren.isEmpty()) {
            return executedFieldChildren;
        } else {
            List<NodeChildData> filteredChildren = new ArrayList<>();
            Set<String> encounteredNames = new HashSet<>();
            for (int i = nodeChildren.size() - 1; i >= 0; i--) {
                NodeChildData child = nodeChildren.get(i);
                if (!encounteredNames.contains(child.getName())) {
                    filteredChildren.add(0, child);
                    encounteredNames.add(child.getName());
                }
            }

            return filteredChildren;
        }

    }

    private List<NodeExecutionData> parseExecutions(List<NodeFieldData> fields, List<NodeChildData> children, List<? extends Element> elements) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(elements);
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
            parameter: for (VariableElement var : method.getParameters()) {
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
            NodeChildData child = null;
            if (childIndex != -1) {
                varArgsIndex = varArgParameter ? Math.abs(childIndex - i) : -1;
                child = children.get(childIndex);
            }
            executions.add(new NodeExecutionData(child, i, varArgsIndex));
        }
        return executions;
    }

    private List<ExecutableTypeData> parseExecutableTypeData(NodeData node, List<? extends Element> elements, int signatureSize, List<TypeMirror> frameTypes, boolean includeFinals) {
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

            ExecutableTypeData executableType = new ExecutableTypeData(node, method, signatureSize, context.getFrameTypes());

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

        namesUnique(typeData);

        return typeData;
    }

    private static void namesUnique(List<ExecutableTypeData> typeData) {
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
    }

    private void initializeExecutableTypes(NodeData node) {
        List<ExecutableTypeData> allExecutes = node.getExecutableTypes();

        Set<String> inconsistentFrameTypes = new HashSet<>();
        TypeMirror frameType = null;
        for (ExecutableTypeData execute : allExecutes) {

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
            node.addError("No accessible and overridable generic execute method found. Generic execute methods usually have the " +
                            "signature 'public abstract {Type} execute(VirtualFrame)' and must not throw any checked exceptions.");
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
            node.addError("Not enough child node declarations found. Please annotate the node class with addtional @NodeChild annotations or remove all execute methods that do not provide all evaluated values. " +
                            "The following execute methods do not provide all evaluated values for the expected signature size %s: %s.", executions.size(), requireNodeChildDeclarations);
        }

        if (nodeChildDeclarations > 0 && executions.size() == node.getMinimalEvaluatedParameters()) {
            for (NodeChildData child : node.getChildren()) {
                child.addError("Unnecessary @NodeChild declaration. All evaluated child values are provided as parameters in execute methods.");
            }
        }

    }

    @SuppressWarnings("unchecked")
    private void initializeChildren(NodeData node) {
        for (NodeChildData child : node.getChildren()) {
            AnnotationValue executeWithValue1 = child.getExecuteWithValue();

            List<AnnotationValue> executeWithValues = ElementUtils.resolveAnnotationValue(List.class, executeWithValue1);
            List<NodeExecutionData> executeWith = new ArrayList<>();
            for (AnnotationValue executeWithValue : executeWithValues) {
                String executeWithString = ElementUtils.resolveAnnotationValue(String.class, executeWithValue);

                if (child.getName().equals(executeWithString)) {
                    child.addError(executeWithValue1, "The child node '%s' cannot be executed with itself.", executeWithString);
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
                    child.addError(executeWithValue1, "The child node '%s' cannot be executed with '%s'. The child node was not found.", child.getName(), executeWithString);
                    continue;
                } else if (!before) {
                    child.addError(executeWithValue1, "The child node '%s' cannot be executed with '%s'. The node %s is executed after the current node.", child.getName(), executeWithString,
                                    executeWithString);
                    continue;
                }
                executeWith.add(found);
            }

            child.setExecuteWith(executeWith);
        }

        for (NodeChildData child : node.getChildren()) {
            TypeMirror nodeType = child.getNodeType();
            NodeData fieldNodeData = parseChildNodeData(node, child, ElementUtils.fromTypeMirror(nodeType));

            child.setNode(fieldNodeData);
            if (fieldNodeData == null || fieldNodeData.hasErrors()) {
                child.addError("Node type '%s' is invalid or not a subclass of Node.", ElementUtils.getQualifiedName(nodeType));
            } else {
                List<ExecutableTypeData> types = child.findGenericExecutableTypes(context);
                if (types.isEmpty()) {
                    AnnotationValue executeWithValue = child.getExecuteWithValue();
                    child.addError(executeWithValue, "No generic execute method found with %s evaluated arguments for node type %s and frame types %s.", child.getExecuteWith().size(),
                                    ElementUtils.getSimpleName(nodeType), ElementUtils.getUniqueIdentifiers(createAllowedChildFrameTypes(node)));
                }
            }
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
        node.getExecutableTypes().addAll(parseExecutableTypeData(node, members, child.getExecuteWith().size(), frameTypes, true));
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
        initializeReplaces(node);
        initializeFallbackReachability(node);
        resolveReplaces(node);

        List<SpecializationData> specializations = node.getSpecializations();
        for (SpecializationData cur : specializations) {
            for (SpecializationData contained : cur.getReplaces()) {
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

    private static void initializeReplaces(NodeData node) {
        for (SpecializationData specialization : node.getSpecializations()) {
            Set<SpecializationData> resolvedSpecializations = specialization.getReplaces();
            resolvedSpecializations.clear();
            Set<String> includeNames = specialization.getReplacesNames();
            for (String includeName : includeNames) {
                // TODO reduce complexity of this lookup.
                SpecializationData foundSpecialization = lookupSpecialization(node, includeName);

                AnnotationValue value = ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "replaces");
                if (value == null) {
                    // TODO remove if deprecated api was removed.
                    value = ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "contains");
                }
                if (foundSpecialization == null) {
                    specialization.addError(value, "The referenced specialization '%s' could not be found.", includeName);
                } else {
                    if (foundSpecialization.compareTo(specialization) > 0) {
                        if (foundSpecialization.compareTo(specialization) > 0) {
                            specialization.addError(value, "The replaced specialization '%s' must be declared before the replacing specialization.", includeName);
                        }
                    }
                    resolvedSpecializations.add(foundSpecialization);
                }
            }
        }
    }

    private void resolveReplaces(NodeData node) {
        // flatten transitive includes
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getReplaces().isEmpty()) {
                continue;
            }
            Set<SpecializationData> foundSpecializations = new HashSet<>();
            collectIncludes(specialization, foundSpecializations, new HashSet<SpecializationData>());
            specialization.getReplaces().addAll(foundSpecializations);
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
            specialization.addError("Circular replaced specialization '%s' found.", specialization.createReferenceName());
            return;
        }
        visited.add(specialization);

        for (SpecializationData included : specialization.getReplaces()) {
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
            assumptionId++;
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

                if (!cacheExpression.hasErrors()) {
                    Cached cached = cacheExpression.getParameter().getVariableElement().getAnnotation(Cached.class);
                    cacheExpression.setDimensions(cached.dimensions());
                    if (parameterType.getKind() == TypeKind.ARRAY &&
                                    !ElementUtils.isSubtype(((ArrayType) parameterType).getComponentType(), context.getType(NodeInterface.class))) {
                        if (cacheExpression.getDimensions() == -1) {
                            cacheExpression.addWarning("The cached dimensions attribute must be specified for array types.");
                        }
                    } else {
                        if (cacheExpression.getDimensions() != -1) {
                            cacheExpression.addError("The dimensions attribute has no affect for the type %s.", ElementUtils.getSimpleName(parameterType));
                        }
                    }
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
        List<Element> filteredElements = newElementList(elements);
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
        FallbackParser parser = new FallbackParser(context, node);
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
            allowedTypes = Arrays.asList(node.getGenericType(execution));
        }
        if (allowedTypes.size() == 1) {
            return allowedTypes.iterator().next();
        } else {
            return ElementUtils.getCommonSuperType(context, allowedTypes);
        }
    }

    private static void initializeUninitialized(final NodeData node) {
        SpecializationData generic = node.getGenericSpecialization();
        if (generic == null) {
            return;
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

        Collection<TypeMirror> frameTypes = new HashSet<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getFrame() != null) {
                frameTypes.add(specialization.getFrame().getType());
            }
        }
        if (node.supportsFrame()) {
            frameTypes.add(node.getFrameType());
        }

        if (!frameTypes.isEmpty()) {
            frameTypes = ElementUtils.uniqueSortedTypes(frameTypes, false);
            TypeMirror frameType;
            if (frameTypes.size() == 1) {
                frameType = frameTypes.iterator().next();
            } else {
                frameType = context.getType(Frame.class);
            }
            types.add(new CodeVariableElement(frameType, TemplateMethod.FRAME_NAME));
        }

        TypeMirror returnType = null;
        int index = 0;
        for (Parameter genericParameter : generic.getReturnTypeAndParameters()) {
            TypeMirror polymorphicType;
            if (genericParameter.getLocalName().equals(TemplateMethod.FRAME_NAME)) {
                continue;
            }
            boolean isReturnParameter = genericParameter == generic.getReturnType();
            if (!genericParameter.getSpecification().isSignature()) {
                polymorphicType = genericParameter.getType();
            } else {
                NodeExecutionData execution = genericParameter.getSpecification().getExecution();
                Collection<TypeMirror> usedTypes = new HashSet<>();
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
                    if (isReturnParameter && specialization.hasUnexpectedResultRewrite()) {
                        if (!ElementUtils.isSubtypeBoxed(context, context.getType(Object.class), node.getGenericType(execution))) {
                            specialization.addError("Implicit 'Object' return type from UnexpectedResultException not compatible with generic type '%s'.", node.getGenericType(execution));
                        } else {
                            // if any specialization throws UnexpectedResultException, Object could
                            // be returned
                            usedTypes.add(context.getType(Object.class));
                        }
                    }
                    usedTypes.add(parameter.getType());
                }
                usedTypes = ElementUtils.uniqueSortedTypes(usedTypes, false);

                if (usedTypes.size() == 1) {
                    polymorphicType = usedTypes.iterator().next();
                } else {
                    polymorphicType = ElementUtils.getCommonSuperType(context, usedTypes);
                }

                if (execution != null && !ElementUtils.isSubtypeBoxed(context, polymorphicType, node.getGenericType(execution))) {
                    throw new AssertionError(String.format("Polymorphic types %s not compatible to generic type %s.", polymorphicType, node.getGenericType(execution)));
                }

            }
            if (isReturnParameter) {
                returnType = polymorphicType;
            } else {
                types.add(new CodeVariableElement(polymorphicType, "param" + index));
            }
            index++;
        }

        SpecializationMethodParser parser = new SpecializationMethodParser(context, node);
        SpecializationData polymorphic = parser.create("Polymorphic", TemplateMethod.NO_NATURAL_ORDER, null, null, returnType, types);
        if (polymorphic == null) {
            throw new AssertionError("Failed to parse polymorphic signature. " + parser.createDefaultMethodSpec(null, null, false, null) + " Types: " + returnType + " - " + types);
        }

        polymorphic.setKind(SpecializationKind.POLYMORPHIC);
        node.getSpecializations().add(polymorphic);
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

    /**
     * @see "https://bugs.openjdk.java.net/browse/JDK-8039214"
     */
    @SuppressWarnings("unused")
    private static List<Element> newElementList(List<? extends Element> src) {
        List<Element> workaround = new ArrayList<Element>(src);
        return workaround;
    }

    private static void verifyMissingAbstractMethods(NodeData nodeData, List<? extends Element> originalElements) {
        if (!nodeData.needsFactory()) {
            // missing abstract methods only needs to be implemented
            // if we need go generate factory for it.
            return;
        }

        List<Element> elements = newElementList(originalElements);
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

        Map<String, List<ExecutableElement>> methodsByName = null;

        outer: for (ExecutableElement unusedMethod : ElementFilter.methodsIn(unusedElements)) {
            if (unusedMethod.getModifiers().contains(Modifier.ABSTRACT)) {

                // group by method name to avoid N^2 worst case complexity.
                if (methodsByName == null) {
                    methodsByName = new HashMap<>();
                    for (ExecutableElement m : ElementFilter.methodsIn(unusedElements)) {
                        String name = m.getSimpleName().toString();
                        List<ExecutableElement> groupedElements = methodsByName.get(name);
                        if (groupedElements == null) {
                            groupedElements = new ArrayList<>();
                            methodsByName.put(name, groupedElements);
                        }
                        groupedElements.add(m);
                    }
                }

                for (ExecutableElement otherMethod : methodsByName.get(unusedMethod.getSimpleName().toString())) {
                    if (unusedMethod == otherMethod) {
                        continue;
                    }
                    if (ProcessorContext.getInstance().getEnvironment().getElementUtils().overrides(otherMethod, unusedMethod, nodeData.getTemplateType())) {
                        // the abstract method overridden by another method in the template type.
                        // -> the method does not need an implementation.
                        continue outer;
                    }
                }

                nodeData.addError("The type %s must implement the inherited abstract method %s.", ElementUtils.getSimpleName(nodeData.getTemplateType()),
                                ElementUtils.getReadableSignature(unusedMethod));
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
