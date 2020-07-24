/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.generator;

import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createTransferToInterpreterAndInvalidate;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.boxType;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.createReferenceName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.executableEquals;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.findAnnotationMirror;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterLowerCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getAnnotationValue;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getTypeId;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getVisibility;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isAssignable;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isObject;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isPrimitive;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isSubtype;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isSubtypeBoxed;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isVoid;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.needsCastTo;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.setVisibility;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEquals;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.uniqueSortedTypes;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.AbstractDSLExpressionVisitor;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.ClassLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionReducer;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeParameterElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.library.ExportsGenerator;
import com.oracle.truffle.dsl.processor.model.AssumptionExpression;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.CreateCastData;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.GuardExpression;
import com.oracle.truffle.dsl.processor.model.ImplicitCastData;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.NodeFieldData;
import com.oracle.truffle.dsl.processor.model.Parameter;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.SpecializationThrowsData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

public class FlatNodeGenFactory {

    private static final String FRAME_VALUE = TemplateMethod.FRAME_NAME;
    private static final String STATE_VALUE = "state";

    private static final String NAME_SUFFIX = "_";

    private static final String VARARGS_NAME = "args";

    private final ProcessorContext context;
    private final TruffleTypes types = ProcessorContext.getInstance().getTypes();
    private final NodeData node;
    private final TypeSystemData typeSystem;
    private final TypeMirror genericType;
    private final Set<TypeMirror> expectedTypes = new HashSet<>();
    private List<SpecializationData> reachableSpecializations;
    private SpecializationData[] reachableSpecializationsArray;
    private final Collection<NodeData> sharingNodes;

    private final boolean boxingEliminationEnabled;
    private int boxingSplitIndex = 0;

    private final BitSet state;
    private final BitSet exclude;

    private final ExecutableTypeData executeAndSpecializeType;
    private boolean fallbackNeedsState = false;
    private boolean fallbackNeedsFrame = false;

    private final Map<SpecializationData, CodeTypeElement> specializationClasses = new LinkedHashMap<>();
    private final Set<SpecializationData> usedInsertAccessorsArray = new LinkedHashSet<>();
    private final Set<SpecializationData> usedInsertAccessorsSimple = new LinkedHashSet<>();
    private final boolean primaryNode;
    private final Map<CacheExpression, String> sharedCaches;
    private final Map<ExecutableElement, Function<Call, DSLExpression>> substitutions = new LinkedHashMap<>();
    private final Map<String, CodeVariableElement> libraryConstants;

    private final boolean needsSpecializeLocking;
    private final GeneratorMode generatorMode;

    public enum GeneratorMode {
        DEFAULT,
        EXPORTED_MESSAGE
    }

    public FlatNodeGenFactory(ProcessorContext context, GeneratorMode mode, NodeData node, Map<String, CodeVariableElement> libraryConstants) {
        this(context, mode, node, Arrays.asList(node), node.getSharedCaches(), libraryConstants);
    }

    public FlatNodeGenFactory(ProcessorContext context, GeneratorMode mode, NodeData node,
                    Collection<NodeData> stateSharingNodes,
                    Map<CacheExpression, String> sharedCaches,
                    Map<String, CodeVariableElement> libraryConstants) {
        Objects.requireNonNull(node);
        this.generatorMode = mode;
        this.context = context;
        this.sharingNodes = stateSharingNodes;
        this.node = node;
        this.typeSystem = node.getTypeSystem();
        this.genericType = context.getType(Object.class);
        this.boxingEliminationEnabled = true;
        this.reachableSpecializations = calculateReachableSpecializations(node);
        this.reachableSpecializationsArray = reachableSpecializations.toArray(new SpecializationData[0]);
        this.primaryNode = stateSharingNodes.iterator().next() == node;
        this.sharedCaches = sharedCaches;

        List<Object> stateObjects = new ArrayList<>();
        List<SpecializationData> excludeObjects = new ArrayList<>();
        boolean volatileState = false;
        for (NodeData stateNode : stateSharingNodes) {
            boolean needsRewrites = stateNode.needsRewrites(context);
            if (!needsRewrites) {
                continue;
            }
            List<SpecializationData> specializations = calculateReachableSpecializations(stateNode);
            Set<TypeGuard> implicitCasts = new LinkedHashSet<>();
            for (SpecializationData specialization : specializations) {
                stateObjects.add(specialization);
                int index = 0;
                for (Parameter p : specialization.getSignatureParameters()) {
                    TypeMirror targetType = p.getType();
                    List<TypeMirror> sourceTypes = stateNode.getTypeSystem().lookupSourceTypes(targetType);
                    if (sourceTypes.size() > 1) {
                        implicitCasts.add(new TypeGuard(targetType, index));
                    }
                    index++;
                }
                if (!specialization.getCaches().isEmpty()) {
                    volatileState = true;
                }

                for (GuardExpression guard : specialization.getGuards()) {
                    if (guardNeedsStateBit(specialization, guard)) {
                        stateObjects.add(guard);
                    }
                }
            }
            stateObjects.addAll(implicitCasts);
            excludeObjects.addAll(specializations);
        }
        this.state = new StateBitSet(stateObjects.toArray(new Object[0]), volatileState);
        this.exclude = new ExcludeBitSet(excludeObjects.toArray(new SpecializationData[0]), volatileState);
        this.executeAndSpecializeType = createExecuteAndSpecializeType();
        this.needsSpecializeLocking = exclude.computeStateLength() != 0 || reachableSpecializations.stream().anyMatch((s) -> !s.getCaches().isEmpty());

        this.libraryConstants = libraryConstants;
        this.substitutions.put(ElementUtils.findExecutableElement(types.LibraryFactory, "resolve"),
                        (binary) -> substituteLibraryCall(binary));
    }

    private boolean needsRewrites() {
        return node.needsRewrites(context);
    }

    private boolean hasMultipleNodes() {
        return sharingNodes.size() > 1;
    }

    private String createSpecializationTypeName(SpecializationData s) {
        if (hasMultipleNodes()) {
            return firstLetterUpperCase(getNodePrefix(s)) + firstLetterUpperCase(s.getId()) + "Data";
        } else {
            return firstLetterUpperCase(s.getId()) + "Data";
        }
    }

    private String createSpecializationFieldName(SpecializationData s) {
        if (hasMultipleNodes()) {
            return firstLetterLowerCase(getNodePrefix(s)) + "_" + firstLetterLowerCase(s.getId()) + "_cache";
        } else {
            return firstLetterLowerCase(s.getId()) + "_cache";
        }
    }

    private String createFieldName(SpecializationData specialization, Parameter cacheParameter) {
        if (useSpecializationClass(specialization)) {
            return cacheParameter.getLocalName() + "_";
        } else {
            String prefix = "";
            if (hasMultipleNodes()) {
                prefix = firstLetterLowerCase(getNodePrefix(specialization)) + "_";
            }
            if (reachableSpecializations.size() > 1) {
                prefix = prefix + firstLetterLowerCase(specialization.getId()) + "_";
            }
            return prefix + cacheParameter.getLocalName() + "_";
        }
    }

    private static String getNodePrefix(SpecializationData specialization) {
        String name = specialization.getNode().getNodeId();
        if (name.endsWith("Node")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    private String createAssumptionFieldName(SpecializationData specialization, AssumptionExpression assumption) {
        if (useSpecializationClass(specialization)) {
            return assumption.getId() + "_";
        } else {
            return firstLetterLowerCase(specialization.getId()) + "_" + assumption.getId() + "_";
        }
    }

    private static String createSpecializationLocalName(SpecializationData s) {
        if (s == null) {
            return null;
        }
        return "s" + s.getIndex() + "_";
    }

    private static String nodeFieldName(NodeExecutionData execution) {
        if (execution.getChild() == null || execution.getChild().needsGeneratedField()) {
            return execution.getName() + NAME_SUFFIX;
        } else {
            return execution.getName();
        }
    }

    private static String accessNodeField(NodeExecutionData execution) {
        if (execution.getChild() == null || execution.getChild().needsGeneratedField()) {
            return "this." + nodeFieldName(execution);
        } else {
            String access = "super." + execution.getChild().getName();
            if (execution.hasChildArrayIndex()) {
                access += "[" + execution.getChildArrayIndex() + "]";
            }
            return access;
        }
    }

    /* Whether a new class should be generated for specialization instance fields. */
    private boolean useSpecializationClass(SpecializationData specialization) {
        /*
         * Children with node array require a final field. Therefore we need to always use a
         * specialization class in this case.
         */
        for (CacheExpression expression : specialization.getCaches()) {
            if (expression.getDefaultExpression() == null) {
                continue;
            }
            if (sharedCaches.containsKey(expression)) {
                return false;
            }
            if (isNodeInterfaceArray(expression.getDefaultExpression().getResolvedType())) {
                return true;
            }
        }

        int size = 0;
        for (CacheExpression expression : specialization.getCaches()) {
            if (expression.isAlwaysInitialized()) {
                // no space needed
                continue;
            }
            TypeMirror type = expression.getParameter().getType();
            if (isPrimitive(type)) {
                switch (type.getKind()) {
                    case BOOLEAN:
                    case BYTE:
                        size++;
                        break;
                    case CHAR:
                    case SHORT:
                        size += 2;
                        break;
                    case INT:
                    case FLOAT:
                        size += 4;
                        break;
                    case LONG:
                    case DOUBLE:
                        size += 8;
                        break;
                }
            } else {
                size += 4;
            }
        }
        // if we exceed the size of two references we generate a class
        if (size > 8 && !hasMultipleNodes()) {
            return true;
        }
        // we need a data class if we need to support multiple specialization instances
        return specialization.getMaximumNumberOfInstances() > 1;
    }

    private static boolean needsFrameToExecute(List<SpecializationData> specializations) {
        for (SpecializationData specialization : specializations) {
            if (specialization.getFrame() != null) {
                return true;
            }
        }
        return false;
    }

    private static String createImplicitTypeStateLocalName(Parameter execution) {
        String name = firstLetterLowerCase(getTypeId(execution.getType()));
        return name + "Cast" + execution.getSpecification().getExecution().getIndex();
    }

    private static boolean mayBeExcluded(SpecializationData specialization) {
        return !specialization.getExceptions().isEmpty() || !specialization.getExcludedBy().isEmpty();
    }

    public CodeTypeElement create(CodeTypeElement clazz) {
        if (primaryNode) {
            for (NodeChildData child : node.getChildren()) {
                clazz.addOptional(createAccessChildMethod(child, false));
            }

            for (NodeFieldData field : node.getFields()) {
                if (!field.isGenerated()) {
                    continue;
                }

                Set<Modifier> fieldModifiers;
                if (field.isSettable()) {
                    fieldModifiers = modifiers(PRIVATE);
                } else {
                    fieldModifiers = modifiers(PRIVATE, FINAL);
                }
                clazz.add(new CodeVariableElement(fieldModifiers, field.getType(), field.getName()));

                if (field.getGetter() != null && field.getGetter().getModifiers().contains(Modifier.ABSTRACT)) {
                    CodeExecutableElement method = CodeExecutableElement.clone(field.getGetter());
                    method.getModifiers().remove(Modifier.ABSTRACT);
                    method.createBuilder().startReturn().string("this.").string(field.getName()).end();
                    clazz.add(method);
                }

                if (field.isSettable()) {
                    CodeExecutableElement method = CodeExecutableElement.clone(field.getSetter());
                    method.renameArguments(field.getName());
                    method.getModifiers().remove(Modifier.ABSTRACT);
                    method.createBuilder().startStatement().string("this.").string(field.getName()).string(" = ", field.getName()).end();
                    clazz.add(method);
                }
            }
            for (ExecutableElement superConstructor : GeneratorUtils.findUserConstructors(node.getTemplateType().asType())) {
                clazz.add(createNodeConstructor(clazz, superConstructor));
            }

            for (NodeExecutionData execution : node.getChildExecutions()) {
                if (execution.getChild() != null && execution.getChild().needsGeneratedField()) {
                    clazz.add(createNodeField(PRIVATE, execution.getNodeType(), nodeFieldName(execution),
                                    types.Node_Child));
                }
            }
        }

        createFields(clazz);

        TypeMirror genericReturnType = node.getPolymorphicSpecialization().getReturnType().getType();

        List<ExecutableTypeData> executableTypes = filterExecutableTypes(node.getExecutableTypes(),
                        reachableSpecializations);
        List<ExecutableTypeData> genericExecutableTypes = new ArrayList<>();
        List<ExecutableTypeData> specializedExecutableTypes = new ArrayList<>();
        List<ExecutableTypeData> voidExecutableTypes = new ArrayList<>();

        for (ExecutableTypeData type : executableTypes) {
            if (isVoid(type.getReturnType())) {
                voidExecutableTypes.add(type);
            } else if (type.hasUnexpectedValue() && !typeEquals(genericReturnType, type.getReturnType())) {
                specializedExecutableTypes.add(type);
            } else {
                genericExecutableTypes.add(type);
            }
        }

        if (genericExecutableTypes.size() > 1) {
            boolean hasGenericTypeMatch = false;
            for (ExecutableTypeData genericExecutable : genericExecutableTypes) {
                if (typeEquals(genericExecutable.getReturnType(), genericReturnType)) {
                    hasGenericTypeMatch = true;
                    break;
                }
            }

            if (hasGenericTypeMatch) {
                for (ListIterator<ExecutableTypeData> iterator = genericExecutableTypes.listIterator(); iterator.hasNext();) {
                    ExecutableTypeData executableTypeData = iterator.next();
                    if (!isAssignable(genericReturnType, executableTypeData.getReturnType())) {
                        iterator.remove();
                        specializedExecutableTypes.add(executableTypeData);
                    }
                }
            }
        }

        SpecializationData fallback = node.getGenericSpecialization();
        if (fallback.getMethod() != null && fallback.isReachable()) {
            clazz.add(createFallbackGuard());
        }

        for (ExecutableTypeData type : genericExecutableTypes) {
            createExecute(clazz, type, Collections.<ExecutableTypeData> emptyList());
        }

        for (ExecutableTypeData type : specializedExecutableTypes) {
            createExecute(clazz, type, genericExecutableTypes);
        }

        for (ExecutableTypeData type : voidExecutableTypes) {
            List<ExecutableTypeData> genericAndSpecialized = new ArrayList<>();
            genericAndSpecialized.addAll(genericExecutableTypes);
            genericAndSpecialized.addAll(specializedExecutableTypes);
            createExecute(clazz, type, genericAndSpecialized);
        }

        clazz.addOptional(createExecuteAndSpecialize());
        if (shouldReportPolymorphism(node, reachableSpecializations)) {
            clazz.addOptional(createCheckForPolymorphicSpecialize());
            if (requiresCacheCheck()) {
                clazz.addOptional(createCountCaches());
            }
        }

        AnnotationMirror nodeInfo = null;
        try {
            nodeInfo = ElementUtils.findAnnotationMirror(node.getTemplateType(), types.NodeInfo);
        } catch (UnsupportedOperationException e) {
        }
        String cost = nodeInfo != null ? ElementUtils.getAnnotationValue(VariableElement.class, nodeInfo, "cost").getSimpleName().toString() : null;
        if (cost == null || cost.equals("MONOMORPHIC") /* the default */) {
            if (primaryNode) {
                clazz.add(createGetCostMethod(false));
            }
        }

        for (TypeMirror type : uniqueSortedTypes(expectedTypes, false)) {
            if (!typeSystem.hasType(type)) {
                clazz.addOptional(TypeSystemCodeGenerator.createExpectMethod(PRIVATE, typeSystem,
                                context.getType(Object.class), type));
            }
        }

        clazz.getEnclosedElements().addAll(removeThisMethods.values());

        for (SpecializationData specialization : specializationClasses.keySet()) {
            CodeTypeElement type = specializationClasses.get(specialization);
            if (getInsertAccessorSet(true).contains(specialization)) {
                type.add(createInsertAccessor(true));
            }
            if (getInsertAccessorSet(false).contains(specialization)) {
                type.add(createInsertAccessor(false));
            }
        }

        if (node.isReflectable()) {
            generateReflectionInfo(clazz);
        }

        if (node.isUncachable() && node.isGenerateUncached()) {
            CodeTypeElement uncached = GeneratorUtils.createClass(node, null, modifiers(PRIVATE, STATIC, FINAL), "Uncached", node.getTemplateType().asType());
            uncached.getEnclosedElements().addAll(createUncachedFields());

            for (NodeFieldData field : node.getFields()) {
                if (!field.isGenerated()) {
                    continue;
                }
                if (field.getGetter() != null && field.getGetter().getModifiers().contains(Modifier.ABSTRACT)) {
                    CodeExecutableElement method = CodeExecutableElement.clone(field.getGetter());
                    method.getModifiers().remove(Modifier.ABSTRACT);
                    method.createBuilder().startThrow().startNew(context.getType(UnsupportedOperationException.class)).end().end();
                    uncached.add(method);
                }
                if (field.isSettable()) {
                    CodeExecutableElement method = CodeExecutableElement.clone(field.getSetter());
                    method.getModifiers().remove(Modifier.ABSTRACT);
                    method.createBuilder().startThrow().startNew(context.getType(UnsupportedOperationException.class)).end().end();
                    uncached.add(method);
                }
            }

            for (NodeChildData child : node.getChildren()) {
                uncached.addOptional(createAccessChildMethod(child, true));
            }

            for (ExecutableTypeData type : genericExecutableTypes) {
                uncached.add(createUncachedExecute(type));
            }

            for (ExecutableTypeData type : specializedExecutableTypes) {
                uncached.add(createUncachedExecute(type));
            }

            for (ExecutableTypeData type : voidExecutableTypes) {
                uncached.add(createUncachedExecute(type));
            }

            if (cost == null || cost.equals("MONOMORPHIC") /* the default */) {
                uncached.add(createGetCostMethod(true));
            }
            CodeExecutableElement isAdoptable = CodeExecutableElement.cloneNoAnnotations(ElementUtils.findExecutableElement(types.Node, "isAdoptable"));
            isAdoptable.createBuilder().returnFalse();
            uncached.add(isAdoptable);

            clazz.add(uncached);
            GeneratedTypeMirror uncachedType = new GeneratedTypeMirror("", uncached.getSimpleName().toString());
            CodeVariableElement uncachedField = clazz.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), uncachedType, "UNCACHED"));
            uncachedField.createInitBuilder().startNew(uncachedType).end();
        }

        return clazz;
    }

    public List<CodeVariableElement> createUncachedFields() {
        List<CodeVariableElement> fields = new ArrayList<>();
        List<CacheExpression> cacheExpressions = computeUniqueReferenceCaches(true);
        for (CacheExpression cache : cacheExpressions) {
            CodeVariableElement supplierField = new CodeVariableElement(modifiers(PRIVATE, FINAL),
                            cache.getReferenceType(), createElementReferenceName(cache));
            CodeTreeBuilder builder = supplierField.createInitBuilder();
            if (cache.isCachedContext()) {
                builder.startCall("lookupContextReference").typeLiteral(cache.getLanguageType()).end();
            } else {
                builder.startCall("lookupLanguageReference").typeLiteral(cache.getLanguageType()).end();
            }
            fields.add(supplierField);
        }
        return fields;
    }

    /**
     * Used by {@link ExportsGenerator} to eagerly initialize caches referenced in accepts.
     */
    public CodeTree createInitializeCaches(SpecializationData specialization, List<CacheExpression> expressions,
                    CodeExecutableElement method, String receiverName) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        FrameState frameState = FrameState.load(this, NodeExecutionMode.SLOW_PATH, method);
        NodeExecutionData execution = specialization.getNode().getChildExecutions().get(0);
        frameState.set(execution, frameState.getValue(execution).accessWith(CodeTreeBuilder.singleString(receiverName)));
        for (CacheExpression cache : expressions) {
            Collection<IfTriple> triples = persistAndInitializeCache(frameState, specialization, cache, false, true);
            IfTriple.materialize(b, triples, true);
        }
        return b.build();
    }

    private static boolean shouldReportPolymorphism(NodeData node, List<SpecializationData> reachableSpecializations) {
        if (reachableSpecializations.size() == 1 && reachableSpecializations.get(0).getMaximumNumberOfInstances() == 1) {
            return false;
        }
        if (reachableSpecializations.stream().noneMatch(SpecializationData::isReportPolymorphism)) {
            return false;
        }
        return node.isReportPolymorphism();
    }

    private void generateReflectionInfo(CodeTypeElement clazz) {
        clazz.getImplements().add(types.Introspection_Provider);
        CodeExecutableElement reflection = new CodeExecutableElement(modifiers(PUBLIC), types.Introspection, "getIntrospectionData");

        CodeTreeBuilder builder = reflection.createBuilder();

        List<SpecializationData> filteredSpecializations = new ArrayList<>();
        for (SpecializationData s : node.getSpecializations()) {
            if (s.getMethod() == null) {
                continue;
            }
            filteredSpecializations.add(s);
        }

        ArrayCodeTypeMirror objectArray = new ArrayCodeTypeMirror(context.getType(Object.class));
        builder.declaration(objectArray, "data", builder.create().startNewArray(objectArray, CodeTreeBuilder.singleString(String.valueOf(filteredSpecializations.size() + 1))).end().build());
        builder.declaration(objectArray, "s", (CodeTree) null);

        builder.statement("data[0] = 0"); // declare version 0

        boolean needsRewrites = needsRewrites();

        FrameState frameState = FrameState.load(this, NodeExecutionMode.SLOW_PATH, reflection);

        if (needsRewrites) {
            builder.tree(state.createLoad(frameState));
            if (requiresExclude()) {
                builder.tree(exclude.createLoad(frameState));
            }
        }

        int index = 1;
        for (SpecializationData specialization : filteredSpecializations) {
            builder.startStatement().string("s = ").startNewArray(objectArray, CodeTreeBuilder.singleString("3")).end().end();
            builder.startStatement().string("s[0] = ").doubleQuote(specialization.getMethodName()).end();

            if (needsRewrites) {
                builder.startIf().tree(state.createContains(frameState, new Object[]{specialization})).end().startBlock();
            }
            builder.startStatement().string("s[1] = (byte)0b01 /* active */").end();
            TypeMirror listType = new DeclaredCodeTypeMirror((TypeElement) context.getDeclaredType(ArrayList.class).asElement(), Arrays.asList(context.getType(Object.class)));

            if (!specialization.getCaches().isEmpty()) {
                builder.declaration(listType, "cached", "new ArrayList<>()");

                boolean useSpecializationClass = useSpecializationClass(specialization);

                String name = createSpecializationLocalName(specialization);
                if (useSpecializationClass) {
                    builder.tree(loadSpecializationClass(frameState, specialization));

                    if (specialization.hasMultipleInstances()) {
                        builder.startWhile();
                    } else {
                        builder.startIf();
                    }
                    builder.string(name, " != null");
                    builder.end();
                    builder.startBlock();
                }

                builder.startStatement().startCall("cached", "add");
                builder.startStaticCall(context.getType(Arrays.class), "asList");
                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.isAlwaysInitialized()) {
                        continue;
                    }
                    builder.startGroup();
                    if (cache.isAlwaysInitialized() && cache.isCachedLibrary()) {
                        builder.staticReference(createLibraryConstant(libraryConstants, cache.getParameter().getType()));
                        builder.startCall(".getUncached").end();
                    } else {
                        builder.tree(createCacheReference(frameState, specialization, cache));
                    }
                    builder.end();
                }
                builder.end();
                builder.end().end();

                if (useSpecializationClass) {
                    if (specialization.getMaximumNumberOfInstances() > 1) {
                        builder.startStatement().string(name, " = ", name, ".next_").end();
                    }
                    builder.end(); // cache while or if
                }

                builder.statement("s[2] = cached");
            }
            if (needsRewrites) {
                builder.end();
                if (mayBeExcluded(specialization)) {
                    builder.startElseIf().tree(exclude.createContains(frameState, new Object[]{specialization})).end().startBlock();
                    builder.startStatement().string("s[1] = (byte)0b10 /* excluded */").end();
                    builder.end();
                }
                builder.startElseBlock();
                builder.startStatement().string("s[1] = (byte)0b00 /* inactive */").end();
                builder.end();
            }
            builder.startStatement().string("data[", String.valueOf(index), "] = s").end();
            index++;
        }

        builder.startReturn().startStaticCall(types.Introspection_Provider, "create").string("data").end().end();

        clazz.add(reflection);
    }

    private void createFields(CodeTypeElement clazz) {
        if (primaryNode) {
            if (state.computeStateLength() > 0) {
                state.declareFields(clazz);
            }

            if (exclude.computeStateLength() > 0) {
                exclude.declareFields(clazz);
            }
        }

        if (primaryNode && !sharedCaches.isEmpty()) {
            Set<String> expressions = new HashSet<>();
            for (Entry<CacheExpression, String> entry : sharedCaches.entrySet()) {
                CacheExpression cache = entry.getKey();
                String fieldName = entry.getValue();
                if (expressions.contains(fieldName)) {
                    continue;
                }
                if (cache.isAlwaysInitialized()) {
                    continue;
                }
                expressions.add(fieldName);
                Parameter parameter = cache.getParameter();
                TypeMirror type = parameter.getType();
                Modifier visibility = Modifier.PRIVATE;

                CodeVariableElement cachedField;
                if (isAssignable(type, types.NodeInterface) && cache.isAdopt()) {
                    cachedField = createNodeField(visibility, type, fieldName, types.Node_Child);
                } else if (isNodeInterfaceArray(type) && cache.isAdopt()) {
                    cachedField = createNodeField(visibility, type, fieldName, types.Node_Children);
                } else {
                    cachedField = createNodeField(visibility, type, fieldName, null);
                    AnnotationMirror mirror = findAnnotationMirror(parameter.getVariableElement().getAnnotationMirrors(), types.Cached);
                    int dimensions = mirror == null ? 0 : getAnnotationValue(Integer.class, mirror, "dimensions");
                    setFieldCompilationFinal(cachedField, dimensions);
                }
                clazz.getEnclosedElements().add(cachedField);
            }
        }

        if (primaryNode) {
            List<CacheExpression> cacheExpressions = computeUniqueReferenceCaches(false);
            for (CacheExpression cache : cacheExpressions) {
                CodeVariableElement supplierField = new CodeVariableElement(modifiers(PRIVATE),
                                cache.getReferenceType(), createElementReferenceName(cache));
                supplierField.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_CompilationFinal));
                clazz.getEnclosedElements().add(supplierField);
            }
        }

        for (SpecializationData specialization : reachableSpecializations) {
            List<CodeVariableElement> fields = new ArrayList<>();
            boolean useSpecializationClass = useSpecializationClass(specialization);

            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.isAlwaysInitialized()) {
                    // no field required for fast path caches.
                    continue;
                }

                String sharedName = sharedCaches.get(cache);
                if (sharedName != null) {
                    continue;
                }

                Parameter parameter = cache.getParameter();
                String fieldName = createFieldName(specialization, parameter);
                TypeMirror type = parameter.getType();
                Modifier visibility = useSpecializationClass ? null : Modifier.PRIVATE;
                CodeVariableElement cachedField;
                if (isAssignable(type, types.NodeInterface) && cache.isAdopt()) {
                    cachedField = createNodeField(visibility, type, fieldName, types.Node_Child);
                } else if (isNodeInterfaceArray(type) && cache.isAdopt()) {
                    cachedField = createNodeField(visibility, type, fieldName, types.Node_Children);
                } else {
                    cachedField = createNodeField(visibility, type, fieldName, null);
                    if (cache.isCached()) {
                        AnnotationMirror mirror = cache.getMessageAnnotation();
                        int dimensions = getAnnotationValue(Integer.class, mirror, "dimensions");
                        setFieldCompilationFinal(cachedField, dimensions);
                    }
                }
                fields.add(cachedField);
            }

            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                String fieldName = createAssumptionFieldName(specialization, assumption);
                TypeMirror type;
                int compilationFinalDimensions;
                if (assumption.getExpression().getResolvedType().getKind() == TypeKind.ARRAY) {
                    type = new ArrayCodeTypeMirror(types.Assumption);
                    compilationFinalDimensions = 1;
                } else {
                    type = types.Assumption;
                    compilationFinalDimensions = -1;
                }
                CodeVariableElement assumptionField;
                if (useSpecializationClass) {
                    assumptionField = createNodeField(null, type, fieldName, null);
                } else {
                    assumptionField = createNodeField(PRIVATE, type, fieldName, null);
                }

                setFieldCompilationFinal(assumptionField, compilationFinalDimensions);

                fields.add(assumptionField);
            }

            if (useSpecializationClass) {
                TypeMirror baseType;
                boolean useNode = specializationClassIsNode(specialization);
                if (useNode) {
                    baseType = types.Node;
                } else {
                    baseType = context.getType(Object.class);
                }

                String typeName = createSpecializationTypeName(specialization);
                CodeTypeElement cacheType = GeneratorUtils.createClass(node, null, modifiers(PRIVATE, FINAL,
                                STATIC), createSpecializationTypeName(specialization), baseType);

                TypeMirror referenceType = new GeneratedTypeMirror("", typeName);

                DeclaredType annotationType;
                if (useNode) {
                    annotationType = types.Node_Child;
                    if (specialization.getMaximumNumberOfInstances() > 1) {
                        cacheType.add(createNodeField(null, referenceType, "next_", types.Node_Child));
                    }

                    CodeExecutableElement getNodeCost = new CodeExecutableElement(modifiers(PUBLIC),
                                    types.NodeCost, "getCost");
                    getNodeCost.createBuilder().startReturn().staticReference(types.NodeCost,
                                    "NONE").end();
                    cacheType.add(getNodeCost);

                } else {
                    annotationType = types.CompilerDirectives_CompilationFinal;
                    if (specialization.getMaximumNumberOfInstances() > 1) {
                        cacheType.add(createNodeField(null, referenceType, "next_", annotationType));
                    }
                }

                cacheType.add(GeneratorUtils.createConstructorUsingFields(modifiers(), cacheType));
                cacheType.getEnclosedElements().addAll(fields);

                clazz.add(createNodeField(PRIVATE, referenceType,
                                createSpecializationFieldName(specialization), annotationType));

                clazz.add(cacheType);

                specializationClasses.put(specialization, cacheType);

            } else {
                clazz.getEnclosedElements().addAll(fields);
            }

        }
    }

    private List<CacheExpression> computeUniqueReferenceCaches(boolean uncached) {
        List<CacheExpression> cacheExpressions = new ArrayList<>();
        Set<String> computedContextReferences = new HashSet<>();
        Set<String> computedLanguageReferences = new HashSet<>();
        for (NodeData sharedNode : this.sharingNodes) {
            Collection<SpecializationData> specializations;
            if (uncached) {
                specializations = sharedNode.computeUncachedSpecializations(calculateReachableSpecializations(sharedNode));
            } else {
                specializations = calculateReachableSpecializations(sharedNode);
            }
            for (SpecializationData specialization : specializations) {
                for (CacheExpression cache : specialization.getCaches()) {
                    if (!cache.isCachedContext() && !cache.isCachedLanguage()) {
                        continue;
                    }
                    TypeMirror languageType = cache.getLanguageType();
                    String qualifiedLanguageTypeName = ElementUtils.getQualifiedName(languageType);
                    if (cache.isCachedLanguage()) {
                        if (computedLanguageReferences.contains(qualifiedLanguageTypeName)) {
                            continue;
                        } else {
                            computedLanguageReferences.add(qualifiedLanguageTypeName);
                        }
                    }
                    if (cache.isCachedContext()) {
                        if (computedContextReferences.contains(qualifiedLanguageTypeName)) {
                            continue;
                        } else {
                            computedContextReferences.add(qualifiedLanguageTypeName);
                        }
                    }
                    cacheExpressions.add(cache);
                }
            }
        }
        return cacheExpressions;
    }

    private static final String INSERT_ACCESSOR_NAME = "insertAccessor";

    private CodeExecutableElement createInsertAccessor(boolean array) {
        CodeTypeParameterElement tVar = new CodeTypeParameterElement(CodeNames.of("T"), types.Node);
        TypeMirror type = tVar.createMirror(null, null);
        if (array) {
            type = new ArrayCodeTypeMirror(type);
        }
        CodeExecutableElement insertAccessor = new CodeExecutableElement(modifiers(FINAL), type, INSERT_ACCESSOR_NAME);
        insertAccessor.getParameters().add(new CodeVariableElement(type, "node"));
        insertAccessor.getTypeParameters().add(tVar);
        insertAccessor.createBuilder().startReturn().string("super.insert(node)").end();
        return insertAccessor;
    }

    private String useInsertAccessor(SpecializationData specialization, boolean array) {
        getInsertAccessorSet(array).add(specialization);
        return INSERT_ACCESSOR_NAME;
    }

    private Set<SpecializationData> getInsertAccessorSet(boolean array) {
        if (array) {
            return usedInsertAccessorsArray;
        } else {
            return usedInsertAccessorsSimple;
        }
    }

    private boolean isNodeInterfaceArray(TypeMirror type) {
        if (type == null) {
            return false;
        }
        return type.getKind() == TypeKind.ARRAY && isAssignable(((ArrayType) type).getComponentType(), types.NodeInterface);
    }

    private static void setFieldCompilationFinal(CodeVariableElement field, int dimensions) {
        if (field.getModifiers().contains(Modifier.FINAL) && dimensions <= 0) {
            // no need for the compilation final annotation.
            return;
        }
        CodeAnnotationMirror annotation = new CodeAnnotationMirror(ProcessorContext.getInstance().getTypes().CompilerDirectives_CompilationFinal);
        if (dimensions > 0 || field.getType().getKind() == TypeKind.ARRAY) {
            annotation.setElementValue(annotation.findExecutableElement("dimensions"), new CodeAnnotationValue(dimensions < 0 ? 0 : dimensions));
        }
        field.getAnnotationMirrors().add(annotation);
    }

    /* Specialization class needs to be a Node in such a case. */
    private boolean specializationClassIsNode(SpecializationData specialization) {
        boolean useSpecializationClass = useSpecializationClass(specialization);
        if (useSpecializationClass) {
            for (CacheExpression cache : specialization.getCaches()) {
                TypeMirror type = cache.getParameter().getType();
                if (isAssignable(type, types.NodeInterface)) {
                    return true;
                } else if (isNodeInterfaceArray(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean requiresExclude() {
        for (SpecializationData specialization : reachableSpecializations) {
            if (mayBeExcluded(specialization)) {
                return true;
            }
        }
        return false;
    }

    private Element createFallbackGuard() {
        boolean frameUsed = false;

        List<SpecializationData> specializations = new ArrayList<>(reachableSpecializations);
        for (ListIterator<SpecializationData> iterator = specializations.listIterator(); iterator.hasNext();) {
            SpecializationData specialization = iterator.next();
            if (specialization.isFallback()) {
                iterator.remove();
            } else if (!specialization.isReachesFallback()) {
                iterator.remove();
            } else {
                if (specialization.isFrameUsedByGuard()) {
                    frameUsed = true;
                }
            }
        }

        SpecializationGroup group = SpecializationGroup.create(specializations);

        ExecutableTypeData executableType = node.findAnyGenericExecutableType(context, -1);

        CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE), getType(boolean.class), createFallbackName());
        FrameState frameState = FrameState.load(this, NodeExecutionMode.FALLBACK_GUARD, method);
        if (!frameUsed) {
            frameState.removeValue(FRAME_VALUE);
        }

        fallbackNeedsState = false;
        fallbackNeedsFrame = frameUsed;
        state.createLoad(frameState); // already loaded
        frameState.addParametersTo(method, Integer.MAX_VALUE, FRAME_VALUE, STATE_VALUE);

        Set<TypeMirror> thrownTypes = new LinkedHashSet<>();
        for (SpecializationData specialization : specializations) {
            for (GuardExpression expression : specialization.getGuards()) {
                for (ExecutableElement boundMethod : expression.getExpression().findBoundExecutableElements()) {
                    thrownTypes.addAll(boundMethod.getThrownTypes());
                }
            }
        }
        method.getThrownTypes().addAll(thrownTypes);

        CodeTree result = visitSpecializationGroup(CodeTreeBuilder.createBuilder(), null, group, executableType, frameState, null);

        if (!fallbackNeedsState) {
            VariableElement toRemove = null;
            for (VariableElement v : method.getParameters()) {
                if (v.getSimpleName().toString().equals(STATE_VALUE)) {
                    toRemove = v;
                    break;
                }
            }
            if (toRemove != null) {
                method.getParameters().remove(toRemove);
            }
        }
        final CodeTreeBuilder builder = method.createBuilder();
        for (SpecializationData implemented : specializations) {
            if (implemented.getMaximumNumberOfInstances() > 1) {
                method.getAnnotationMirrors().add(createExplodeLoop());
                break;
            }
        }

        builder.tree(result);
        builder.returnTrue();

        if (!accessesCachedState(specializations)) {
            method.getModifiers().add(STATIC);
        }

        return method;
    }

    private DSLExpression substituteLibraryCall(Call call) {
        ClassLiteral literal = (ClassLiteral) call.getParameters().get(0);
        CodeVariableElement var = createLibraryConstant(libraryConstants, literal.getLiteral());
        String constantName = var.getSimpleName().toString();
        Variable singleton = new Variable(null, constantName);
        singleton.setResolvedTargetType(var.asType());
        singleton.setResolvedVariable(var);
        return singleton;
    }

    public static CodeVariableElement createLibraryConstant(Map<String, CodeVariableElement> constants, TypeMirror libraryTypeMirror) {
        TypeElement libraryType = ElementUtils.castTypeElement(libraryTypeMirror);
        String name = libraryType.getSimpleName().toString();
        String constantName = ElementUtils.createConstantName(name);
        CodeVariableElement var;
        do {
            String useConstantName = constantName = constantName + "_";
            TypeElement resolvedLibrary = (TypeElement) ProcessorContext.getInstance().getTypes().LibraryFactory.asElement();
            DeclaredCodeTypeMirror constantType = new DeclaredCodeTypeMirror(resolvedLibrary, Arrays.asList(libraryType.asType()));
            var = constants.computeIfAbsent(constantName, (c) -> {
                CodeVariableElement newVar = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), constantType, useConstantName);
                newVar.createInitBuilder().startStaticCall(resolvedLibrary.asType(), "resolve").typeLiteral(libraryType.asType()).end();
                return newVar;
            });
        } while (!ElementUtils.typeEquals(libraryType.asType(), ((DeclaredType) var.getType()).getTypeArguments().get(0)));
        return var;
    }

    private DSLExpression optimizeExpression(DSLExpression expression) {
        return expression.reduce(new DSLExpressionReducer() {

            public DSLExpression visitVariable(Variable binary) {
                return binary;
            }

            public DSLExpression visitNegate(Negate negate) {
                return negate;
            }

            public DSLExpression visitCall(Call binary) {
                for (ExecutableElement substitution : substitutions.keySet()) {
                    if (ElementUtils.executableEquals(binary.getResolvedMethod(), substitution)) {
                        return substitutions.get(substitution).apply(binary);
                    }
                }
                return binary;
            }

            public DSLExpression visitBinary(Binary binary) {
                return binary;
            }
        });
    }

    private static boolean accessesCachedState(List<SpecializationData> specializations) {
        final AtomicBoolean needsState = new AtomicBoolean(false);
        for (final SpecializationData specialization : specializations) {
            if (!specialization.getAssumptionExpressions().isEmpty()) {
                needsState.set(true);
                break;
            }
            for (GuardExpression expression : specialization.getGuards()) {
                expression.getExpression().accept(new AbstractDSLExpressionVisitor() {
                    @Override
                    public void visitVariable(Variable binary) {
                        if (!needsState.get() && isVariableAccessMember(binary)) {
                            needsState.set(true);
                        }
                    }

                    private boolean isVariableAccessMember(Variable variable) {
                        if (variable.getName().equals("null") && variable.getReceiver() == null) {
                            return false;
                        }
                        Parameter p = specialization.findByVariable(variable.getResolvedVariable());
                        if (p == null && !variable.getResolvedVariable().getModifiers().contains(STATIC)) {
                            DSLExpression receiver = variable.getReceiver();
                            if (receiver instanceof Variable) {
                                return isVariableAccessMember((Variable) receiver);
                            } else if (receiver instanceof Call) {
                                return isMethodAccessMember((Call) receiver);
                            }
                            return true;
                        } else if (p != null && p.getSpecification().isCached()) {
                            CacheExpression cache = specialization.findCache(p);
                            if (cache != null && cache.isAlwaysInitialized()) {
                                // allowed access as is initialized in fast path.
                                return false;
                            }
                            return true;
                        }
                        return false;
                    }

                    private boolean isMethodAccessMember(Call call) {
                        if (!call.getResolvedMethod().getModifiers().contains(STATIC)) {
                            DSLExpression receiver = call.getReceiver();
                            if (receiver instanceof Variable) {
                                return isVariableAccessMember((Variable) receiver);
                            } else if (receiver instanceof Call) {
                                return isMethodAccessMember((Call) receiver);
                            }
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void visitCall(Call call) {
                        if (!needsState.get() && isMethodAccessMember(call)) {
                            needsState.set(true);
                        }
                    }

                });
            }
        }
        boolean needsStat = needsState.get();
        return needsStat;
    }

    private CodeAnnotationMirror createExplodeLoop() {
        return new CodeAnnotationMirror(types.ExplodeLoop);
    }

    private List<SpecializationData> filterCompatibleSpecializations(Collection<SpecializationData> specializations, ExecutableTypeData forType) {
        List<SpecializationData> filteredSpecializations = new ArrayList<>();
        outer: for (SpecializationData specialization : specializations) {
            if (specialization.isFallback() && specialization.getMethod() == null) {
                // undefined fallback can always deoptimize
                continue;
            }

            List<TypeMirror> signatureParameters = forType.getSignatureParameters();
            for (int i = 0; i < signatureParameters.size(); i++) {
                TypeMirror evaluatedType = signatureParameters.get(i);
                TypeMirror specializedType = specialization.findParameterOrDie(node.getChildExecutions().get(i)).getType();

                if (typeSystem.lookupCast(evaluatedType, specializedType) == null && !isSubtypeBoxed(context, specializedType, evaluatedType) &&
                                !isSubtypeBoxed(context, evaluatedType, specializedType)) {
                    // unreachable type parameter for the execute signature. For example evaluated
                    // int and specialized long. This does not account for reachability.
                    continue outer;
                }
            }

            TypeMirror returnType = forType.getReturnType();
            if (!isVoid(returnType) && !isSubtypeBoxed(context, specialization.getReturnType().getType(), returnType) &&
                            !isSubtypeBoxed(context, returnType, specialization.getReturnType().getType())) {
                continue outer;
            }
            filteredSpecializations.add(specialization);
        }

        return filteredSpecializations;
    }

    private List<SpecializationData> filterImplementedSpecializations(List<SpecializationData> specializations, TypeMirror expectedReturnType) {
        List<SpecializationData> filteredSpecializations = new ArrayList<>();
        TypeMirror returnType = boxType(context, expectedReturnType);

        for (SpecializationData specialization : specializations) {
            TypeMirror specializationReturnType = boxType(context, specialization.getReturnType().getType());
            if (typeEquals(specializationReturnType, returnType)) {
                filteredSpecializations.add(specialization);
            }
        }

        return filteredSpecializations;
    }

    private List<ExecutableTypeData> filterCompatibleExecutableTypes(ExecutableTypeData type, List<ExecutableTypeData> genericExecutes) {
        List<ExecutableTypeData> compatible = new ArrayList<>();
        outer: for (ExecutableTypeData genericExecute : genericExecutes) {
            if (genericExecute.getEvaluatedCount() != type.getEvaluatedCount()) {
                continue;
            }
            for (int i = 0; i < genericExecute.getEvaluatedCount(); i++) {
                TypeMirror sourceType = type.getSignatureParameters().get(i);
                TypeMirror targetType = genericExecute.getSignatureParameters().get(i);
                if (!isAssignable(sourceType, targetType)) {
                    continue outer;
                }
            }
            if (!isVoid(type.getReturnType()) && !isSubtypeBoxed(context, type.getReturnType(), genericExecute.getReturnType()) &&
                            !isSubtypeBoxed(context, genericExecute.getReturnType(), type.getReturnType())) {
                continue outer;
            }

            compatible.add(genericExecute);
        }
        return compatible;
    }

    private CodeExecutableElement createExecute(CodeTypeElement clazz, ExecutableTypeData type, List<ExecutableTypeData> delegateableTypes) {
        final List<SpecializationData> allSpecializations = reachableSpecializations;
        final List<SpecializationData> compatibleSpecializations = filterCompatibleSpecializations(allSpecializations, type);
        List<SpecializationData> implementedSpecializations;
        if (delegateableTypes.isEmpty()) {
            implementedSpecializations = compatibleSpecializations;
        } else {
            implementedSpecializations = filterImplementedSpecializations(compatibleSpecializations, type.getReturnType());
        }

        CodeExecutableElement method = createExecuteMethod(type);
        FrameState frameState = FrameState.load(this, type, Integer.MAX_VALUE, NodeExecutionMode.FAST_PATH, method);
        if (type.getMethod() == null) {
            frameState.addParametersTo(method, Integer.MAX_VALUE, FRAME_VALUE);
        } else {
            renameOriginalParameters(type, method, frameState);
        }
        clazz.add(method);
        CodeTreeBuilder builder = method.createBuilder();

        // do I miss specializations that are reachable from this executable?
        if (compatibleSpecializations.size() != implementedSpecializations.size()) {
            ExecuteDelegationResult delegation = createExecuteDelegation(builder, frameState, type, delegateableTypes, compatibleSpecializations, implementedSpecializations);
            builder.tree(delegation.tree);
            if (!delegation.hasFallthrough) {
                return method;
            }
        }

        if (implementedSpecializations.isEmpty()) {
            implementedSpecializations = compatibleSpecializations;
        }

        if (implementedSpecializations.isEmpty()) {
            builder.tree(GeneratorUtils.createShouldNotReachHere("Delegation failed."));
        } else {
            SpecializationGroup group = SpecializationGroup.create(implementedSpecializations);
            builder.tree(createFastPath(builder, implementedSpecializations, group, type, frameState));
        }
        return method;
    }

    public CodeExecutableElement createUncached() {
        SpecializationData fallback = node.getGenericSpecialization();
        TypeMirror returnType = fallback.getReturnType().getType();
        List<TypeMirror> parameterTypes = new ArrayList<>();
        for (Parameter parameter : fallback.getSignatureParameters()) {
            parameterTypes.add(parameter.getType());
        }
        ExecutableTypeData forType = new ExecutableTypeData(node, returnType, "uncached", null, parameterTypes);
        return createUncachedExecute(forType);
    }

    private CodeExecutableElement createUncachedExecute(ExecutableTypeData forType) {
        final Collection<SpecializationData> allSpecializations = node.computeUncachedSpecializations(reachableSpecializations);
        final List<SpecializationData> compatibleSpecializations = filterCompatibleSpecializations(allSpecializations, forType);

        CodeExecutableElement method = createExecuteMethod(forType);
        FrameState frameState = FrameState.load(this, forType, Integer.MAX_VALUE, NodeExecutionMode.UNCACHED, method);
        if (forType.getMethod() == null) {
            frameState.addParametersTo(method, Integer.MAX_VALUE, FRAME_VALUE);
        } else {
            renameOriginalParameters(forType, method, frameState);
        }

        boolean isExecutableInUncached = forType.getEvaluatedCount() != node.getExecutionCount();
        if (!isExecutableInUncached) {
            method.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
        }

        if (forType.getMethod() != null) {
            method.getModifiers().addAll(forType.getMethod().getModifiers());
            method.getModifiers().remove(Modifier.ABSTRACT);
        }

        CodeTreeBuilder builder = method.createBuilder();
        if (isExecutableInUncached) {
            builder.tree(GeneratorUtils.createShouldNotReachHere("This execute method cannot be used for uncached node versions as it requires child nodes to be present. " +
                            "Use an execute method that takes all arguments as parameters."));
        } else {
            SpecializationGroup group = SpecializationGroup.create(compatibleSpecializations);
            FrameState originalFrameState = frameState.copy();
            builder.tree(visitSpecializationGroup(builder, null, group, forType, frameState, allSpecializations));
            if (group.hasFallthrough()) {
                builder.tree(createThrowUnsupported(builder, originalFrameState));
            }
        }

        return method;
    }

    private ExecuteDelegationResult createExecuteDelegation(CodeTreeBuilder parent, FrameState frameState, ExecutableTypeData type,
                    List<ExecutableTypeData> delegateableTypes, final List<SpecializationData> compatibleSpecializations, List<SpecializationData> implementedSpecializations) {

        CodeTreeBuilder builder = parent.create();
        List<SpecializationData> notImplemented = new ArrayList<>(compatibleSpecializations);
        for (SpecializationData specialization : implementedSpecializations) {
            notImplemented.remove(specialization);
        }
        if (notImplemented.isEmpty()) {
            throw new AssertionError();
        }

        List<ExecutableTypeData> compatibleDelegateTypes = filterCompatibleExecutableTypes(type, delegateableTypes);
        List<ExecutableTypeData> delegatedDelegateTypes = new ArrayList<>();

        CodeTreeBuilder delegateBuilder = builder.create();
        boolean elseIf = false;
        boolean coversAllSpecializations = false;
        if (boxingEliminationEnabled) {
            Set<TypeMirror> optimizeTypes = new HashSet<>();
            for (SpecializationData specialization : reachableSpecializations) {
                TypeMirror returnType = specialization.getReturnType().getType();
                if (isPrimitive(returnType)) {
                    optimizeTypes.add(returnType);
                }
            }

            for (TypeMirror optimizedType : uniqueSortedTypes(optimizeTypes, true)) {
                ExecutableTypeData delegateType = null;
                for (ExecutableTypeData compatibleType : compatibleDelegateTypes) {
                    if (typeEquals(compatibleType.getReturnType(), optimizedType)) {
                        delegateType = compatibleType;
                        break;
                    }
                }

                if (delegateType != null) {
                    List<SpecializationData> delegateSpecializations = filterImplementedSpecializations(
                                    filterCompatibleSpecializations(reachableSpecializations, delegateType), delegateType.getReturnType());
                    coversAllSpecializations = delegateSpecializations.size() == reachableSpecializations.size();
                    if (!coversAllSpecializations) {
                        builder.tree(state.createLoad(frameState));
                        elseIf = delegateBuilder.startIf(elseIf);
                        delegateBuilder.startGroup();
                        delegateBuilder.tree(state.createContainsOnly(frameState, 0, -1, delegateSpecializations.toArray(), reachableSpecializationsArray)).end();
                        delegateBuilder.string(" && ");
                        delegateBuilder.tree(state.createIsNotAny(frameState, reachableSpecializationsArray));
                        delegateBuilder.end();
                        delegateBuilder.end();
                        delegateBuilder.startBlock();
                    }
                    delegatedDelegateTypes.add(delegateType);
                    delegateBuilder.tree(createCallExecute(type, delegateType, frameState));
                    if (!coversAllSpecializations) {
                        delegateBuilder.end();
                    }
                    if (coversAllSpecializations) {
                        break;
                    }
                }
            }
        }

        if (!compatibleDelegateTypes.isEmpty() && !coversAllSpecializations) {
            ExecutableTypeData delegateType = compatibleDelegateTypes.get(0);
            coversAllSpecializations = notImplemented.size() == reachableSpecializations.size();
            if (!coversAllSpecializations) {
                builder.tree(state.createLoad(frameState));
                elseIf = delegateBuilder.startIf(elseIf);
                delegateBuilder.tree(state.createContains(frameState, notImplemented.toArray())).end();
                delegateBuilder.startBlock();
            }
            delegatedDelegateTypes.add(delegateType);
            delegateBuilder.tree(createCallExecute(type, delegateType, frameState));
            if (!coversAllSpecializations) {
                delegateBuilder.end();
            }
        }

        boolean hasUnexpected = false;
        for (ExecutableTypeData delegateType : delegatedDelegateTypes) {
            if (needsUnexpectedResultException(delegateType)) {
                hasUnexpected = true;
                break;
            }
        }

        if (hasUnexpected) {
            builder.startTryBlock();
            builder.tree(delegateBuilder.build());
            builder.end().startCatchBlock(types.UnexpectedResultException, "ex");
            if (isVoid(type.getReturnType())) {
                builder.returnStatement();
            } else {
                builder.startReturn();
                builder.tree(expectOrCast(getType(Object.class), type, CodeTreeBuilder.singleString("ex")));
                builder.end();
            }
            builder.end();
        } else {
            builder.tree(delegateBuilder.build());
        }
        return new ExecuteDelegationResult(builder.build(), !coversAllSpecializations);
    }

    private String createFallbackName() {
        if (hasMultipleNodes()) {
            String messageName = node.getNodeId();
            if (messageName.endsWith("Node")) {
                messageName = messageName.substring(0, messageName.length() - 4);
            }
            return firstLetterLowerCase(messageName) + "FallbackGuard_";
        } else {
            return "fallbackGuard_";
        }
    }

    private String createExecuteAndSpecializeName() {
        if (hasMultipleNodes()) {
            String messageName = node.getNodeId();
            if (messageName.endsWith("Node")) {
                messageName = messageName.substring(0, messageName.length() - 4);
            }
            return firstLetterLowerCase(messageName) + "AndSpecialize";
        } else {
            return "executeAndSpecialize";
        }
    }

    private CodeExecutableElement createExecuteAndSpecialize() {
        if (!needsRewrites()) {
            return null;
        }
        String frame = null;
        if (needsFrameToExecute(reachableSpecializations)) {
            frame = FRAME_VALUE;
        }
        TypeMirror returnType = executeAndSpecializeType.getReturnType();
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE), returnType, createExecuteAndSpecializeName());
        final FrameState frameState = FrameState.load(this, NodeExecutionMode.SLOW_PATH, method);
        frameState.addParametersTo(method, Integer.MAX_VALUE, frame);

        final CodeTreeBuilder builder = method.createBuilder();
        boolean reportPolymorphism = shouldReportPolymorphism(node, reachableSpecializations);
        if (needsSpecializeLocking) {
            builder.declaration(context.getType(Lock.class), "lock", "getLock()");
            builder.declaration(context.getType(boolean.class), "hasLock", "true");
            builder.statement("lock.lock()");
        }

        builder.tree(state.createLoad(frameState));
        if (requiresExclude()) {
            builder.tree(exclude.createLoad(frameState));
        }
        if (reportPolymorphism) {
            generateSaveOldPolymorphismState(builder, frameState);
        }
        if (needsSpecializeLocking || reportPolymorphism) {
            builder.startTryBlock();
        }

        FrameState originalFrameState = frameState.copy();
        SpecializationGroup group = createSpecializationGroups();
        CodeTree execution = visitSpecializationGroup(builder, null, group, executeAndSpecializeType, frameState, null);

        builder.tree(execution);

        if (group.hasFallthrough()) {
            builder.tree(createThrowUnsupported(builder, originalFrameState));
        }

        if (needsSpecializeLocking || reportPolymorphism) {
            builder.end().startFinallyBlock();
            if (reportPolymorphism) {
                generateCheckNewPolymorphismState(builder);
            }
            if (needsSpecializeLocking) {
                builder.startIf().string("hasLock").end().startBlock();
                builder.statement("lock.unlock()");
                builder.end();
            }
            builder.end();
        }

        return method;
    }

    // Polymorphism reporting constants
    private static final String OLD_PREFIX = "old";
    private static final String NEW_PREFIX = "new";
    private static final String COUNT_SUFIX = "Count";
    private static final String OLD_STATE = OLD_PREFIX + "State";
    private static final String OLD_EXCLUDE = OLD_PREFIX + "Exclude";
    private static final String OLD_CACHE_COUNT = OLD_PREFIX + "Cache" + COUNT_SUFIX;
    private static final String NEW_STATE = NEW_PREFIX + "State";
    private static final String NEW_EXCLUDE = NEW_PREFIX + "Exclude";
    private static final String REPORT_POLYMORPHIC_SPECIALIZE = "reportPolymorphicSpecialize";
    private static final String CHECK_FOR_POLYMORPHIC_SPECIALIZE = "checkForPolymorphicSpecialize";
    private static final String COUNT_CACHES = "countCaches";

    private String createName(String defaultName) {
        if (hasMultipleNodes()) {
            String messageName = node.getNodeId();
            if (messageName.endsWith("Node")) {
                messageName = messageName.substring(0, messageName.length() - 4);
            }
            return firstLetterLowerCase(messageName) + "_" + defaultName;
        } else {
            return defaultName;
        }
    }

    private boolean requiresCacheCheck() {
        for (SpecializationData specialization : reachableSpecializations) {
            if (useSpecializationClass(specialization) && specialization.getMaximumNumberOfInstances() > 1) {
                return true;
            }
        }
        return false;
    }

    private Element createCheckForPolymorphicSpecialize() {
        final boolean requiresExclude = requiresExclude();
        final boolean requiresCacheCheck = requiresCacheCheck();
        TypeMirror returnType = getType(void.class);
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PRIVATE), returnType, createName(CHECK_FOR_POLYMORPHIC_SPECIALIZE));
        executable.addParameter(new CodeVariableElement(state.bitSetType, OLD_STATE));
        if (requiresExclude) {
            executable.addParameter(new CodeVariableElement(exclude.bitSetType, OLD_EXCLUDE));
        }
        if (requiresCacheCheck) {
            executable.addParameter(new CodeVariableElement(getType(int.class), OLD_CACHE_COUNT));
        }
        CodeTreeBuilder builder = executable.createBuilder();
        FrameState frameState = FrameState.load(this, NodeExecutionMode.SLOW_PATH, executable);
        builder.declaration(state.bitSetType, NEW_STATE, state.createMaskedReference(frameState, reachableSpecializationsReportingPolymorphism()));
        if (requiresExclude) {
            builder.declaration(exclude.bitSetType, NEW_EXCLUDE, exclude.createReference(frameState));
        }
        builder.startIf().string("(" + OLD_STATE + " ^ " + NEW_STATE + ") != 0");
        if (requiresExclude) {
            builder.string(" || ");
            builder.string("(" + OLD_EXCLUDE + " ^ " + NEW_EXCLUDE + ") != 0");
        }
        if (requiresCacheCheck) {
            builder.string(" || " + OLD_CACHE_COUNT + " < " + createName(COUNT_CACHES) + "()");
        }
        builder.end(); // if
        builder.startBlock().startStatement().startCall("this", REPORT_POLYMORPHIC_SPECIALIZE).end(2);
        builder.end(); // true block
        return executable;
    }

    private SpecializationData[] reachableSpecializationsReportingPolymorphism() {
        return reachableSpecializations.stream().filter(SpecializationData::isReportPolymorphism).toArray(SpecializationData[]::new);
    }

    private Element createCountCaches() {
        TypeMirror returnType = getType(int.class);
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PRIVATE), returnType, createName(COUNT_CACHES));
        CodeTreeBuilder builder = executable.createBuilder();
        final String cacheCount = "cache" + COUNT_SUFIX;
        builder.declaration(context.getType(int.class), cacheCount, "0");
        for (SpecializationData specialization : reachableSpecializationsReportingPolymorphism()) {
            if (useSpecializationClass(specialization) && specialization.getMaximumNumberOfInstances() > 1) {
                String typeName = createSpecializationTypeName(specialization);
                String fieldName = createSpecializationFieldName(specialization);
                String localName = createSpecializationLocalName(specialization);
                builder.declaration(typeName, localName, "this." + fieldName);
                builder.startWhile().string(localName, " != null");
                builder.end();
                builder.startBlock().statement(cacheCount + "++").statement(localName + "= " + localName + ".next_");
                builder.end();
            }
        }
        builder.startReturn().statement(cacheCount);
        return executable;
    }

    private void generateCheckNewPolymorphismState(CodeTreeBuilder builder) {
        builder.startIf().string(OLD_STATE + " != 0");
        if (requiresExclude()) {
            builder.string(" || " + OLD_EXCLUDE + " != 0");
        }
        builder.end();
        builder.startBlock();
        builder.string(createName(CHECK_FOR_POLYMORPHIC_SPECIALIZE) + "(" + OLD_STATE);
        if (requiresExclude()) {
            builder.string(", " + OLD_EXCLUDE);
        }
        if (requiresCacheCheck()) {
            builder.string(", " + OLD_CACHE_COUNT);
        }
        builder.string(");").newLine();
        builder.end(); // block
    }

    private void generateSaveOldPolymorphismState(CodeTreeBuilder builder, FrameState frameState) {
        builder.declaration(state.bitSetType, OLD_STATE, state.createMaskedReference(frameState, reachableSpecializationsReportingPolymorphism()));
        if (requiresExclude()) {
            builder.declaration(exclude.bitSetType, OLD_EXCLUDE, "exclude");
        }
        if (requiresCacheCheck()) {
            builder.declaration(context.getType(int.class), OLD_CACHE_COUNT, "state == 0 ? 0 : " + createName(COUNT_CACHES) + "()");
        }
    }

    private CodeTree createThrowUnsupported(final CodeTreeBuilder parent, final FrameState frameState) {
        CodeTreeBuilder builder = parent.create();
        builder.startThrow().startNew(types.UnsupportedSpecializationException);
        ExecutableElement method = parent.findMethod();
        if (method != null && method.getModifiers().contains(STATIC)) {
            builder.string("null");
        } else {
            builder.string("this");
        }
        builder.startNewArray(new ArrayCodeTypeMirror(types.Node), null);
        List<CodeTree> values = new ArrayList<>();

        for (NodeExecutionData execution : node.getChildExecutions()) {
            NodeChildData child = execution.getChild();
            LocalVariable var = frameState.getValue(execution);
            if (child != null && !frameState.getMode().isUncached()) {
                builder.string(accessNodeField(execution));
            } else {
                builder.string("null");
            }
            if (var != null) {
                values.add(var.createReference());
            }
        }
        builder.end();
        builder.trees(values.toArray(new CodeTree[0]));
        builder.end().end();
        return builder.build();

    }

    private CodeTree createFastPath(CodeTreeBuilder parent, List<SpecializationData> allSpecializations, SpecializationGroup originalGroup, final ExecutableTypeData currentType,
                    FrameState frameState) {
        final CodeTreeBuilder builder = parent.create();

        boolean needsRewrites = needsRewrites();
        if (needsRewrites) {
            builder.tree(state.createLoad(frameState));
        }

        int sharedExecutes = 0;
        for (NodeExecutionData execution : node.getChildExecutions()) {
            boolean canExecuteChild = execution.getIndex() < currentType.getEvaluatedCount();
            for (TypeGuard checkedGuard : originalGroup.getTypeGuards()) {
                if (checkedGuard.getSignatureIndex() == execution.getIndex()) {
                    canExecuteChild = true;
                    break;
                }
            }

            if (!canExecuteChild) {
                break;
            }
            for (TypeGuard checkedGuard : originalGroup.getTypeGuards()) {
                // we cannot pull out guards that use optimized implicit source types
                if (resolveOptimizedImplicitSourceTypes(execution, checkedGuard.getType()).size() > 1) {
                    canExecuteChild = false;
                    break;
                }
            }
            if (!canExecuteChild) {
                break;
            }

            builder.tree(createFastPathExecuteChild(builder, frameState.copy(), frameState, currentType, originalGroup, execution));
            sharedExecutes++;
        }

        List<BoxingSplit> boxingSplits = parameterBoxingElimination(originalGroup, sharedExecutes);

        if (boxingSplits.isEmpty()) {
            builder.tree(executeFastPathGroup(builder, frameState, currentType, originalGroup, sharedExecutes, null));
            addExplodeLoop(builder, originalGroup);
        } else {
            FrameState originalFrameState = frameState.copy();

            boolean elseIf = false;
            for (BoxingSplit split : boxingSplits) {
                elseIf = builder.startIf(elseIf);
                List<SpecializationData> specializations = split.group.collectSpecializations();
                builder.startGroup();
                builder.tree(state.createContainsOnly(frameState, 0, -1, specializations.toArray(), allSpecializations.toArray())).end();
                builder.string(" && ");
                builder.tree(state.createIsNotAny(frameState, allSpecializations.toArray()));
                builder.end();
                builder.end().startBlock();
                builder.tree(wrapInAMethod(builder, split.group, originalFrameState, split.getName(),
                                executeFastPathGroup(builder, frameState.copy(), currentType, split.group, sharedExecutes, specializations)));
                builder.end();
            }

            builder.startElseBlock();
            builder.tree(wrapInAMethod(builder, originalGroup, originalFrameState, "generic", executeFastPathGroup(builder, frameState, currentType, originalGroup, sharedExecutes, null)));
            builder.end();
        }

        return builder.build();
    }

    private void addExplodeLoop(final CodeTreeBuilder builder, SpecializationGroup originalGroup) {
        for (SpecializationData implemented : originalGroup.collectSpecializations()) {
            if (implemented.getMaximumNumberOfInstances() > 1) {
                ((CodeExecutableElement) builder.findMethod()).getAnnotationMirrors().add(createExplodeLoop());
                break;
            }
        }
    }

    private CodeTree wrapInAMethod(CodeTreeBuilder parent, SpecializationGroup group, FrameState frameState, String suffix, CodeTree codeTree) {
        CodeExecutableElement parentMethod = (CodeExecutableElement) parent.findMethod();
        CodeTypeElement parentClass = (CodeTypeElement) parentMethod.getEnclosingElement();
        String name = parentMethod.getSimpleName().toString() + "_" + suffix + (boxingSplitIndex++);
        CodeExecutableElement method = parentClass.add(new CodeExecutableElement(modifiers(Modifier.PRIVATE), parentMethod.getReturnType(), name));
        frameState.addParametersTo(method, Integer.MAX_VALUE, FRAME_VALUE, STATE_VALUE);
        CodeTreeBuilder builder = method.createBuilder();
        builder.tree(codeTree);
        method.getThrownTypes().addAll(parentMethod.getThrownTypes());
        addExplodeLoop(builder, group);

        CodeTreeBuilder parentBuilder = parent.create();
        parentBuilder.startReturn();
        parentBuilder.startCall(method.getSimpleName().toString());
        frameState.addReferencesTo(parentBuilder, FRAME_VALUE, STATE_VALUE);
        parentBuilder.end();
        parentBuilder.end();
        return parentBuilder.build();
    }

    private CodeTree executeFastPathGroup(final CodeTreeBuilder parent, FrameState frameState, final ExecutableTypeData currentType, SpecializationGroup group, int sharedExecutes,
                    List<SpecializationData> allowedSpecializations) {
        CodeTreeBuilder builder = parent.create();
        if (currentType.getMethod() != null && currentType.getMethod().isVarArgs()) {
            int readVarargsCount = node.getSignatureSize() - (currentType.getEvaluatedCount() - 1);
            int offset = node.getSignatureSize() - 1;
            for (int i = 0; i < readVarargsCount; i++) {
                NodeExecutionData execution = node.getChildExecutions().get(offset + i);
                LocalVariable var = frameState.getValue(execution);
                if (var != null) {
                    builder.tree(var.createDeclaration(var.createReference()));
                    frameState.setValue(execution, var.accessWith(null));
                }
            }
        }
        FrameState originalFrameState = frameState.copy();

        for (NodeExecutionData execution : node.getChildExecutions()) {
            if (execution.getIndex() < sharedExecutes) {
                // skip shared executes
                continue;
            }
            builder.tree(createFastPathExecuteChild(builder, originalFrameState, frameState, currentType, group, execution));
        }

        builder.tree(visitSpecializationGroup(builder, null, group, currentType, frameState, allowedSpecializations));

        if (group.hasFallthrough()) {
            builder.tree(createTransferToInterpreterAndInvalidate());
            builder.tree(createCallExecuteAndSpecialize(currentType, originalFrameState));
        }
        return builder.build();
    }

    /*
     * It duplicates a group into small subgroups of specializations that don't need boxing when
     * executing the children.
     */
    private List<BoxingSplit> parameterBoxingElimination(SpecializationGroup group, int evaluatedcount) {
        if (!boxingEliminationEnabled) {
            return Collections.emptyList();
        }

        List<SpecializationData> allSpecializations = group.collectSpecializations();
        List<Set<TypeGuard>> signatures = new ArrayList<>();
        List<List<SpecializationData>> signatureSpecializations = new ArrayList<>();

        for (SpecializationData specialization : allSpecializations) {
            int index = -1;
            List<TypeGuard> guards = new ArrayList<>();
            for (Parameter p : specialization.getSignatureParameters()) {
                index++;
                if (!isPrimitive(p.getType())) {
                    continue;
                } else if (index < evaluatedcount) {
                    continue;
                } else {
                    NodeChildData child = p.getSpecification().getExecution().getChild();
                    if (child != null && child.findExecutableType(p.getType()) == null) {
                        // type cannot be executed so it cannot be eliminated
                        continue;
                    }
                }
                guards.add(new TypeGuard(p.getType(), index));
            }
            if (!guards.isEmpty()) {
                boolean directFound = false;
                for (int i = 0; i < signatures.size(); i++) {
                    if (guards.containsAll(signatures.get(i))) {
                        if (signatures.get(i).containsAll(guards)) {
                            directFound = true;
                        }
                        signatureSpecializations.get(i).add(specialization);
                    }
                }
                if (!directFound) {
                    signatures.add(new LinkedHashSet<>(guards));
                    List<SpecializationData> specializations = new ArrayList<>();
                    specializations.add(specialization);
                    signatureSpecializations.add(specializations);
                }
            }
        }
        List<BoxingSplit> groups = new ArrayList<>();

        for (int i = 0; i < signatureSpecializations.size(); i++) {
            List<SpecializationData> groupedSpecialization = signatureSpecializations.get(i);
            if (allSpecializations.size() == groupedSpecialization.size()) {
                // contains all specializations does not make sense to group
                continue;
            }
            Set<TypeGuard> signature = signatures.get(i);

            TypeMirror[] signatureMirrors = new TypeMirror[signature.size()];
            int index = 0;
            for (TypeGuard typeGuard : signature) {
                signatureMirrors[index] = typeGuard.getType();
                index++;
            }

            groups.add(new BoxingSplit(SpecializationGroup.create(groupedSpecialization), signatureMirrors));
        }

        Collections.sort(groups, new Comparator<BoxingSplit>() {
            public int compare(BoxingSplit o1, BoxingSplit o2) {
                return Integer.compare(o2.primitiveSignature.length, o1.primitiveSignature.length);
            }
        });

        return groups;
    }

    private CodeTree createFastPathExecuteChild(final CodeTreeBuilder parent, FrameState originalFrameState, FrameState frameState, final ExecutableTypeData currentType, SpecializationGroup group,
                    NodeExecutionData execution) {
        CodeTreeBuilder builder = parent.create();

        LocalVariable var = frameState.getValue(execution);
        if (var == null) {
            TypeMirror targetType;

            TypeGuard eliminatedGuard = null;
            if (boxingEliminationEnabled) {
                for (TypeGuard checkedGuard : group.getTypeGuards()) {
                    if (!isPrimitive(checkedGuard.getType())) {
                        // no elimination for non primitive types
                        continue;
                    } else if (node.getChildExecutions().get(checkedGuard.getSignatureIndex()).getChild().findExecutableType(checkedGuard.getType()) == null) {
                        // type cannot be executed so it cannot be eliminated
                        continue;
                    }

                    if (checkedGuard.getSignatureIndex() == execution.getIndex()) {
                        eliminatedGuard = checkedGuard;
                        break;
                    }
                }
            }
            if (eliminatedGuard != null) {
                // we can optimize the type guard away by executing it
                group.getTypeGuards().remove(eliminatedGuard);
                targetType = eliminatedGuard.getType();
            } else {
                targetType = execution.getChild().findAnyGenericExecutableType(context).getReturnType();
            }
            var = frameState.createValue(execution, targetType).nextName();

            LocalVariable fallbackVar;
            List<TypeMirror> originalSourceTypes = typeSystem.lookupSourceTypes(targetType);
            List<TypeMirror> sourceTypes = resolveOptimizedImplicitSourceTypes(execution, targetType);
            if (sourceTypes.size() > 1) {
                TypeGuard typeGuard = new TypeGuard(targetType, execution.getIndex());
                TypeMirror generic = node.getPolymorphicSpecialization().findParameterOrDie(execution).getType();
                fallbackVar = originalFrameState.createValue(execution, generic);

                // we want to create the check tree in reverse order
                Collections.reverse(sourceTypes);
                CodeTree access = var.createReference();
                boolean first = true;
                for (TypeMirror sType : sourceTypes) {
                    if (typeEquals(sType, targetType)) {
                        continue;
                    }
                    String localName = createSourceTypeLocalName(var, sType);
                    builder.declaration(sType, localName, CodeTreeBuilder.createBuilder().defaultValue(sType).build());

                    CodeTreeBuilder accessBuilder = builder.create();
                    accessBuilder.startParantheses();

                    accessBuilder.tree(state.createContainsOnly(frameState, originalSourceTypes.indexOf(sType), 1, new Object[]{typeGuard},
                                    new Object[]{typeGuard}));
                    accessBuilder.string(" && ");
                    accessBuilder.tree(state.createIsNotAny(frameState, reachableSpecializationsArray));

                    accessBuilder.string(" ? ");
                    if (isPrimitive(sType)) {
                        accessBuilder.string("(").type(generic).string(") ");
                    }
                    accessBuilder.string(localName);
                    accessBuilder.string(" : ");
                    if (first && isPrimitive(targetType)) {
                        accessBuilder.string("(").type(generic).string(") ");
                    }
                    accessBuilder.tree(access);
                    accessBuilder.end();
                    access = accessBuilder.build();
                    first = false;
                }
                fallbackVar = fallbackVar.accessWith(access);
            } else {
                fallbackVar = var;
            }

            builder.tree(createAssignExecuteChild(originalFrameState, frameState, builder, execution, currentType, var));
            frameState.setValue(execution, var);
            originalFrameState.setValue(execution, fallbackVar);
        }
        return builder.build();
    }

    private CodeTree createAssignExecuteChild(FrameState originalFrameState, FrameState frameState, CodeTreeBuilder parent, NodeExecutionData execution, ExecutableTypeData forType,
                    LocalVariable targetValue) {
        CodeTreeBuilder builder = parent.create();

        ChildExecutionResult executeChild = createExecuteChild(builder, originalFrameState, frameState, execution, targetValue);
        builder.tree(createTryExecuteChild(targetValue, executeChild.code, true, executeChild.throwsUnexpectedResult));
        builder.end();
        if (executeChild.throwsUnexpectedResult) {
            builder.startCatchBlock(types.UnexpectedResultException, "ex");
            FrameState slowPathFrameState = originalFrameState.copy();
            slowPathFrameState.setValue(execution, targetValue.makeGeneric(context).accessWith(CodeTreeBuilder.singleString("ex.getResult()")));

            ExecutableTypeData delegateType = node.getGenericExecutableType(forType);
            boolean found = false;
            for (NodeExecutionData otherExecution : node.getChildExecutions()) {
                if (found) {
                    LocalVariable childEvaluatedValue = slowPathFrameState.createValue(otherExecution, genericType);
                    builder.tree(createAssignExecuteChild(slowPathFrameState.copy(), slowPathFrameState, builder, otherExecution, delegateType, childEvaluatedValue));
                    slowPathFrameState.setValue(otherExecution, childEvaluatedValue);
                } else {
                    // skip forward already evaluated
                    found = execution == otherExecution;
                }
            }
            builder.tree(createCallExecuteAndSpecialize(forType, slowPathFrameState));
            builder.end();
        }

        return builder.build();
    }

    private static String createSourceTypeLocalName(LocalVariable targetValue, TypeMirror sType) {
        return targetValue.getName() + getSimpleName(sType);
    }

    private ChildExecutionResult createCallSingleChildExecute(NodeExecutionData execution, LocalVariable target, FrameState frameState, ExecutableTypeData executableType) {
        CodeTree execute = callChildExecuteMethod(execution, executableType, frameState);
        TypeMirror sourceType = executableType.getReturnType();
        TypeMirror targetType = target.getTypeMirror();
        CodeTree result = expect(sourceType, targetType, execute);
        return new ChildExecutionResult(result, executableType.hasUnexpectedValue() || needsCastTo(sourceType, targetType));
    }

    private ChildExecutionResult createExecuteChild(CodeTreeBuilder parent, FrameState originalFrameState, FrameState frameState, NodeExecutionData execution, LocalVariable target) {

        ChildExecutionResult result;
        if (!typeSystem.hasImplicitSourceTypes(target.getTypeMirror())) {
            ExecutableTypeData targetExecutable = resolveTargetExecutable(execution, target.typeMirror);
            final CodeTreeBuilder builder = parent.create();
            result = createCallSingleChildExecute(execution, target, frameState, targetExecutable);
            builder.string(target.getName()).string(" = ");
            builder.tree(result.code);
            result.code = builder.build();
        } else {
            result = createExecuteChildImplicitCast(parent.create(), originalFrameState, frameState, execution, target);
        }
        return result;
    }

    // old code

    private CodeExecutableElement createNodeConstructor(CodeTypeElement clazz, ExecutableElement superConstructor) {
        Set<String> ignoreConstructorFields = new HashSet<>();
        for (NodeFieldData field : node.getFields()) {
            if (field.isSettable()) {
                ignoreConstructorFields.add(field.getName());
            }
        }
        CodeExecutableElement constructor = GeneratorUtils.createConstructorUsingFields(modifiers(), clazz, superConstructor, ignoreConstructorFields);
        setVisibility(constructor.getModifiers(), getVisibility(superConstructor.getModifiers()));
        constructor.setVarArgs(superConstructor.isVarArgs());

        List<CodeVariableElement> childParameters = new ArrayList<>();
        for (NodeChildData child : node.getChildren()) {
            if (child.needsGeneratedField()) {
                childParameters.add(new CodeVariableElement(child.getOriginalType(), child.getName()));
            }
        }
        constructor.getParameters().addAll(superConstructor.getParameters().size(), childParameters);

        CodeTreeBuilder builder = constructor.appendBuilder();
        List<String> childValues = new ArrayList<>(node.getChildren().size());
        if (!node.getChildExecutions().isEmpty()) {
            for (NodeChildData child : node.getChildren()) {
                if (child.needsGeneratedField()) {
                    String name = child.getName();
                    if (child.getCardinality().isMany()) {
                        CreateCastData createCast = node.findCast(child.getName());
                        if (createCast != null) {
                            CodeTree nameTree = CodeTreeBuilder.singleString(name);
                            CodeTreeBuilder callBuilder = builder.create();
                            callBuilder.string(name).string(" != null ? ");
                            callBuilder.tree(callMethod(null, null, createCast.getMethod(), nameTree));
                            callBuilder.string(" : null");
                            name += "_";
                            builder.declaration(child.getNodeType(), name, callBuilder.build());
                        }
                    }
                    childValues.add(name);
                }
            }
        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            if (execution.getChild() == null || !execution.getChild().needsGeneratedField()) {
                continue;
            }
            CreateCastData createCast = node.findCast(execution.getChild().getName());

            builder.startStatement();
            builder.string("this.").string(nodeFieldName(execution)).string(" = ");

            String name = childValues.get(node.getChildren().indexOf(execution.getChild()));
            CodeTreeBuilder accessorBuilder = builder.create();
            accessorBuilder.string(name);

            if (execution.hasChildArrayIndex()) {
                accessorBuilder.string("[").string(String.valueOf(execution.getChildArrayIndex())).string("]");
            }

            CodeTree accessor = accessorBuilder.build();

            if (createCast != null && execution.getChild().getCardinality().isOne()) {
                accessor = callMethod(null, null, createCast.getMethod(), accessor);
            }

            if (execution.hasChildArrayIndex()) {
                CodeTreeBuilder nullCheck = builder.create();
                nullCheck.string(name).string(" != null && ").string(String.valueOf(execution.getChildArrayIndex())).string(" < ").string(name).string(".length").string(" ? ");
                nullCheck.tree(accessor);
                nullCheck.string(" : null");
                accessor = nullCheck.build();
            }

            builder.tree(accessor);

            builder.end();
        }

        return constructor;
    }

    private List<ExecutableTypeData> filterExecutableTypes(List<ExecutableTypeData> executableTypes, List<SpecializationData> specializations) {
        Set<TypeMirror> specializedReturnTypes = new HashSet<>();
        for (SpecializationData specialization : specializations) {
            specializedReturnTypes.add(specialization.getReturnType().getType());
        }

        List<ExecutableTypeData> filteredTypes = new ArrayList<>();
        outer: for (ExecutableTypeData executable : executableTypes) {
            if (executable.getMethod() == null) {
                continue;
            }
            if (executable.isAbstract()) {
                filteredTypes.add(executable);
                continue;
            }
            if (executable.isFinal()) {
                // no way to implement that
                continue;
            }

            if (!executable.hasUnexpectedValue()) {
                filteredTypes.add(executable);
                continue;
            } else {
                TypeMirror returnType = executable.getReturnType();
                if (boxingEliminationEnabled && (isVoid(returnType) || isPrimitive(returnType))) {
                    for (TypeMirror specializedReturnType : specializedReturnTypes) {
                        if (isSubtypeBoxed(context, specializedReturnType, returnType)) {
                            filteredTypes.add(executable);
                            continue outer;
                        }
                    }
                }
            }

        }
        Collections.sort(filteredTypes);
        return filteredTypes;
    }

    private Element createGetCostMethod(boolean uncached) {
        TypeMirror returnType = types.NodeCost;
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getCost");
        executable.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getDeclaredType(Override.class)));
        CodeTreeBuilder builder = executable.createBuilder();

        if (uncached) {
            builder.startReturn().staticReference(types.NodeCost, "MEGAMORPHIC").end();
        } else {
            if (needsRewrites()) {
                FrameState frameState = FrameState.load(this, NodeExecutionMode.UNCACHED, executable);
                builder.tree(state.createLoad(frameState));
                builder.startIf().tree(state.createIs(frameState, new Object[0], reachableSpecializationsArray)).end();
                builder.startBlock();
                builder.startReturn().staticReference(types.NodeCost, "UNINITIALIZED").end();
                builder.end();
                if (reachableSpecializations.size() == 1 && !reachableSpecializations.iterator().next().hasMultipleInstances()) {
                    builder.startElseBlock();
                    builder.startReturn().staticReference(types.NodeCost, "MONOMORPHIC").end();
                    builder.end();
                } else {
                    builder.startElseIf();
                    builder.tree(state.createIsOneBitOf(frameState, reachableSpecializationsArray));
                    builder.end();
                    builder.startBlock();

                    List<CodeTree> additionalChecks = new ArrayList<>();
                    for (SpecializationData specialization : reachableSpecializations) {
                        if (useSpecializationClass(specialization) && specialization.getMaximumNumberOfInstances() > 1) {
                            String typeName = createSpecializationTypeName(specialization);
                            String fieldName = createSpecializationFieldName(specialization);
                            String localName = createSpecializationLocalName(specialization);
                            builder.declaration(typeName, localName, "this." + fieldName);
                            CodeTree check = builder.create().startParantheses().string(localName, " == null || ",
                                            localName, ".next_ == null").end().build();
                            additionalChecks.add(check);
                        }
                    }
                    if (!additionalChecks.isEmpty()) {
                        builder.startIf().tree(combineTrees(" && ", additionalChecks.toArray(new CodeTree[0]))).end().startBlock();
                    }
                    builder.startReturn().staticReference(types.NodeCost, "MONOMORPHIC").end();
                    if (!additionalChecks.isEmpty()) {
                        builder.end();
                    }
                    builder.end();

                    builder.startReturn().staticReference(types.NodeCost, "POLYMORPHIC").end();
                }
            } else {
                builder.startReturn().staticReference(types.NodeCost, "MONOMORPHIC").end();
            }
        }

        return executable;

    }

    private ExecutableElement createAccessChildMethod(NodeChildData child, boolean uncached) {
        if (child.getAccessElement() != null && child.getAccessElement().getModifiers().contains(Modifier.ABSTRACT)) {
            ExecutableElement getter = (ExecutableElement) child.getAccessElement();
            CodeExecutableElement method = CodeExecutableElement.clone(getter);
            method.getModifiers().remove(Modifier.ABSTRACT);

            List<NodeExecutionData> executions = new ArrayList<>();
            for (NodeExecutionData execution : node.getChildExecutions()) {
                if (execution.getChild() == child) {
                    executions.add(execution);
                }
            }

            CodeTreeBuilder builder = method.createBuilder();
            if (uncached) {
                method.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
                builder.tree(GeneratorUtils.createShouldNotReachHere("This getter method cannot be used for uncached node versions as it requires child nodes to be present."));
            } else {
                if (child.getCardinality().isMany()) {
                    builder.startReturn().startNewArray((ArrayType) child.getOriginalType(), null);
                    for (NodeExecutionData execution : executions) {
                        builder.string(accessNodeField(execution));
                    }
                    builder.end().end();
                } else {
                    for (NodeExecutionData execution : executions) {
                        builder.startReturn().string(accessNodeField(execution)).end();
                        break;
                    }
                }
            }
            return method;
        }
        return null;
    }

    private static List<SpecializationData> calculateReachableSpecializations(NodeData node) {
        List<SpecializationData> specializations = new ArrayList<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.isReachable() &&   //
                            (specialization.isSpecialized()   //
                                            || (specialization.isFallback() && specialization.getMethod() != null))) {
                specializations.add(specialization);
            }
        }
        return specializations;
    }

    private TypeMirror getType(Class<?> clazz) {
        return context.getType(clazz);
    }

    private static CodeVariableElement createNodeField(Modifier visibility, TypeMirror type, String name, DeclaredType annotationClass, Modifier... modifiers) {
        CodeVariableElement childField = new CodeVariableElement(modifiers(modifiers), type, name);
        if (annotationClass != null) {
            if (annotationClass == ProcessorContext.getInstance().getTypes().CompilerDirectives_CompilationFinal) {
                setFieldCompilationFinal(childField, 0);
            } else {
                childField.getAnnotationMirrors().add(new CodeAnnotationMirror(annotationClass));
            }
        }
        setVisibility(childField.getModifiers(), visibility);
        return childField;
    }

    private static CodeTree callMethod(FrameState frameState, CodeTree receiver, ExecutableElement method, CodeTree... boundValues) {
        if (frameState != null) {
            frameState.addThrownExceptions(method);
        }

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        List<? extends VariableElement> parameters = method.getParameters();
        CodeTree useReceiver = receiver;
        boolean staticMethod = method.getModifiers().contains(STATIC);
        int boundValueIndex = -1;
        if (!method.getParameters().isEmpty() && staticMethod) {
            VariableElement receiverVar = method.getParameters().get(0);
            /*
             * Special generated parameter name this is used for exported methods with explicit
             * receiver. This is safe because it should not appear as parameter names elsewhere.
             */
            if (receiverVar.getSimpleName().toString().equals("this")) {
                useReceiver = boundValues[0];
                parameters = parameters.subList(1, parameters.size());
                boundValueIndex = 0;
                staticMethod = false;
            }
        }

        if (staticMethod) {
            builder.startStaticCall(method);
        } else {
            builder.startCall(useReceiver, method.getSimpleName().toString());
        }
        for (VariableElement parameter : parameters) {
            boundValueIndex++;
            if (boundValueIndex < boundValues.length) {
                CodeTree tree = boundValues[boundValueIndex];
                if (tree != null) {
                    builder.tree(tree);
                    continue;
                }
            }
            builder.defaultValue(parameter.asType());
        }
        builder.end();
        return builder.build();
    }

    private CodeTree[] bindExecuteMethodParameters(NodeExecutionData execution, ExecutableTypeData method, FrameState frameState) {
        List<NodeExecutionData> executeWith = execution != null ? execution.getChild().getExecuteWith() : null;

        List<CodeTree> values = new ArrayList<>();
        if (method.getFrameParameter() != null) {
            LocalVariable frameLocal = frameState.get(FRAME_VALUE);
            if (frameLocal == null) {
                throw new AssertionError(method.getName() + " requires a frame parameter.");
            }
            values.add(createParameterReference(frameLocal, method.getMethod(), 0));
        }

        int evaluatedIndex = 0;
        for (int executionIndex = 0; executionIndex < node.getExecutionCount(); executionIndex++) {
            NodeExecutionData parameterExecution;
            if (executeWith != null && executionIndex < executeWith.size()) {
                parameterExecution = executeWith.get(executionIndex);
            } else {
                parameterExecution = node.getChildExecutions().get(executionIndex);
            }
            if (evaluatedIndex < method.getEvaluatedCount()) {
                TypeMirror targetType = method.getEvaluatedParameters().get(evaluatedIndex);
                LocalVariable value = frameState.getValue(parameterExecution);
                if (value != null) {
                    int parameterIndex = method.getParameterIndex(evaluatedIndex);
                    values.add(createParameterReference(value, method.getMethod(), parameterIndex));
                } else {
                    values.add(CodeTreeBuilder.createBuilder().defaultValue(targetType).build());
                }
                evaluatedIndex++;
            }
        }
        return values.toArray(new CodeTree[values.size()]);
    }

    private CodeTree callChildExecuteMethod(NodeExecutionData execution, ExecutableTypeData method, FrameState frameState) {
        return callMethod(frameState, CodeTreeBuilder.singleString(accessNodeField(execution)), method.getMethod(), bindExecuteMethodParameters(execution, method, frameState));
    }

    private CodeTree createParameterReference(LocalVariable sourceVariable, ExecutableElement targetMethod, int targetIndex) {
        CodeTree valueReference = sourceVariable.createReference();
        TypeMirror sourceType = sourceVariable.getTypeMirror();
        VariableElement targetParameter = targetMethod.getParameters().get(targetIndex);
        TypeMirror targetType = targetParameter.asType();

        if (targetType == null || sourceType == null) {
            return valueReference;
        }
        boolean hasCast = false;
        if (needsCastTo(sourceType, targetType)) {
            CodeTree castValue = TypeSystemCodeGenerator.cast(typeSystem, targetType, valueReference);
            hasCast = valueReference != castValue;
            valueReference = castValue;
        }

        // check for overloads that might conflict in the call and therefore needs a cast
        if (!typeEquals(sourceType, targetType) && !hasCast) {
            Element element = targetMethod.getEnclosingElement();
            boolean needsOverloadCast = false;
            if (element != null) {
                for (ExecutableElement executable : ElementFilter.methodsIn(element.getEnclosedElements())) {
                    if (executableEquals(executable, targetMethod)) {
                        continue;
                    }
                    if (!executable.getSimpleName().toString().equals(targetMethod.getSimpleName().toString())) {
                        continue;
                    }
                    if (executable.getParameters().size() != targetMethod.getParameters().size()) {
                        continue;
                    }
                    TypeMirror overloadedTarget = executable.getParameters().get(targetIndex).asType();
                    if (!needsCastTo(sourceType, overloadedTarget)) {
                        needsOverloadCast = true;
                        break;
                    }
                }
            }
            if (needsOverloadCast) {
                valueReference = TypeSystemCodeGenerator.cast(typeSystem, targetType, valueReference);
            }
        }
        return valueReference;
    }

    private SpecializationGroup createSpecializationGroups() {
        return SpecializationGroup.create(reachableSpecializations);
    }

    private CodeTree expectOrCast(TypeMirror sourceType, ExecutableTypeData targetType, CodeTree content) {
        if (needsUnexpectedResultException(targetType)) {
            return expect(sourceType, targetType.getReturnType(), content);
        } else {
            return cast(sourceType, targetType.getReturnType(), content);
        }
    }

    private CodeTree cast(TypeMirror sourceType, TypeMirror targetType, CodeTree content) {
        if (needsCastTo(sourceType, targetType) && !isVoid(sourceType)) {
            return TypeSystemCodeGenerator.cast(typeSystem, targetType, content);
        } else {
            return content;
        }
    }

    private CodeTree expect(TypeMirror sourceType, TypeMirror forType, CodeTree tree) {
        if (sourceType == null || needsCastTo(sourceType, forType)) {
            expectedTypes.add(forType);
            return TypeSystemCodeGenerator.expect(typeSystem, forType, tree);
        }
        return tree;
    }

    private CodeExecutableElement createExecuteMethod(ExecutableTypeData executedType) {
        TypeMirror returnType = executedType.getReturnType();

        String methodName;
        if (executedType.getMethod() != null) {
            methodName = executedType.getMethod().getSimpleName().toString();
        } else {
            methodName = executedType.getUniqueName();
        }

        CodeExecutableElement executable;
        if (executedType.getMethod() != null) {
            executable = CodeExecutableElement.clone(executedType.getMethod());
            executable.getAnnotationMirrors().clear();
            executable.getModifiers().remove(ABSTRACT);
            for (VariableElement var : executable.getParameters()) {
                ((CodeVariableElement) var).getAnnotationMirrors().clear();
            }
            executable.renameArguments(FRAME_VALUE);
            if (executable.isVarArgs()) {
                ((CodeVariableElement) executable.getParameters().get(executable.getParameters().size() - 1)).setName(VARARGS_NAME);
            }
        } else {
            executable = new CodeExecutableElement(modifiers(PUBLIC), returnType, methodName);
        }

        DeclaredType unexpectedResult = types.UnexpectedResultException;
        Iterator<TypeMirror> thrownTypes = executable.getThrownTypes().iterator();
        while (thrownTypes.hasNext()) {
            if (typeEquals(unexpectedResult, thrownTypes.next())) {
                thrownTypes.remove();
            }
        }
        if (needsUnexpectedResultException(executedType)) {
            executable.getThrownTypes().add(unexpectedResult);
        }

        return executable;
    }

    private void renameOriginalParameters(ExecutableTypeData executedType, CodeExecutableElement executable, FrameState frameState) {
        // rename varargs parameter
        int evaluatedIndex = 0;
        for (int executionIndex = 0; executionIndex < node.getExecutionCount(); executionIndex++) {
            NodeExecutionData execution = node.getChildExecutions().get(executionIndex);
            if (evaluatedIndex < executedType.getEvaluatedCount()) {
                TypeMirror evaluatedType = executedType.getEvaluatedParameters().get(evaluatedIndex);
                LocalVariable value = frameState.getValue(execution);
                if (value != null) {
                    frameState.setValue(execution, renameExecutableTypeParameter(executable, executedType, evaluatedIndex, evaluatedType, value));
                }
                evaluatedIndex++;
            }
        }
    }

    private static LocalVariable renameExecutableTypeParameter(CodeExecutableElement method, ExecutableTypeData executedType, int evaluatedIndex, TypeMirror targetType, LocalVariable var) {
        int parameterIndex = executedType.getParameterIndex(evaluatedIndex);
        int varArgsIndex = executedType.getVarArgsIndex(parameterIndex);
        LocalVariable returnVar = var;
        if (varArgsIndex >= 0) {
            returnVar = returnVar.accessWith(CodeTreeBuilder.singleString(VARARGS_NAME + "[" + varArgsIndex + "]"));
        } else {
            ((CodeVariableElement) method.getParameters().get(parameterIndex)).setName(returnVar.getName());
        }
        if (!isObject(targetType)) {
            returnVar = returnVar.newType(targetType);
        }
        return returnVar;
    }

    private boolean needsUnexpectedResultException(ExecutableTypeData executedType) {
        if (!executedType.hasUnexpectedValue()) {
            return false;
        }

        if (isSubtypeBoxed(context, executeAndSpecializeType.getReturnType(), executedType.getReturnType())) {
            return false;
        } else {
            return true;
        }
    }

    private CodeTree createFastPathExecute(CodeTreeBuilder parent, final ExecutableTypeData forType, SpecializationData specialization, FrameState frameState) {
        CodeTreeBuilder builder = parent.create();
        int ifCount = 0;
        if (specialization.isFallback()) {
            builder.startIf().startCall(createFallbackName());
            if (fallbackNeedsFrame) {
                if (frameState.get(FRAME_VALUE) != null) {
                    builder.string(FRAME_VALUE);
                } else {
                    builder.nullLiteral();
                }
            }
            if (fallbackNeedsState) {
                builder.string(STATE_VALUE);
            }
            frameState.addReferencesTo(builder);

            builder.end();
            builder.end();
            builder.startBlock();
            ifCount++;
        }
        builder.tree(createCallSpecialization(builder, frameState, forType, specialization));
        builder.end(ifCount);
        return builder.build();
    }

    private CodeTree createCallSpecialization(CodeTreeBuilder parent, FrameState parentState, final ExecutableTypeData forType, SpecializationData specialization) {
        CodeTreeBuilder builder = parent.create();
        FrameState frameState = parentState.copy();

        for (SpecializationThrowsData throwsData : specialization.getExceptions()) {
            frameState.addCaughtException(throwsData.getJavaClass());
        }

        if (needsSpecializeLocking && frameState.getMode().isSlowPath()) {
            builder.statement("lock.unlock()");
            builder.statement("hasLock = false");
        }

        if (specialization.getMethod() == null) {
            builder.tree(createThrowUnsupported(builder, frameState));
        } else {
            CodeTree[] bindings = new CodeTree[specialization.getParameters().size()];
            for (int i = 0; i < bindings.length; i++) {
                Parameter parameter = specialization.getParameters().get(i);

                if (parameter.getSpecification().isCached()) {
                    LocalVariable var = frameState.get(createFieldName(specialization, parameter));
                    if (var != null) {
                        bindings[i] = var.createReference();
                    } else {
                        bindings[i] = createCacheReference(frameState, specialization, specialization.findCache(parameter));
                    }
                } else {
                    LocalVariable variable = bindExpressionVariable(frameState, specialization, parameter);
                    if (variable != null) {
                        bindings[i] = createParameterReference(variable, specialization.getMethod(), i);
                    }
                }
            }

            CodeTree specializationCall = callMethod(frameState, null, specialization.getMethod(), bindings);
            if (isVoid(specialization.getMethod().getReturnType())) {
                builder.statement(specializationCall);
                if (isVoid(forType.getReturnType())) {
                    builder.returnStatement();
                } else {
                    builder.startReturn().defaultValue(forType.getReturnType()).end();
                }
            } else {
                builder.startReturn();
                builder.tree(expectOrCast(specialization.getReturnType().getType(), forType, specializationCall));
                builder.end();
            }
        }

        return createCatchRewriteException(builder, specialization, forType, frameState, builder.build());
    }

    static final class BlockState {

        static final BlockState NONE = new BlockState(0, 0);

        final int ifCount;
        final int blockCount;

        private BlockState(int ifCount, int blockCount) {
            this.ifCount = ifCount;
            this.blockCount = blockCount;
        }

        BlockState add(BlockState state) {
            return new BlockState(ifCount + state.ifCount, blockCount + state.blockCount);
        }

        BlockState incrementIf() {
            return new BlockState(ifCount + 1, blockCount + 1);
        }

        static BlockState create(int ifCount, int blockCount) {
            if (ifCount == 0 && blockCount == 0) {
                return NONE;
            } else {
                return new BlockState(ifCount, blockCount);
            }
        }

    }

    private static class IfTriple {

        private CodeTree prepare;
        private CodeTree condition;
        private CodeTree statements;

        IfTriple(CodeTree prepare, CodeTree condition, CodeTree statements) {
            this.prepare = prepare;
            this.condition = condition;
            this.statements = statements;
        }

        private boolean canBeMerged(IfTriple triple) {
            boolean prepareSet = !isEmpty(triple.prepare) || !isEmpty(prepare);
            boolean conditionSet = !isEmpty(triple.condition) || !isEmpty(condition);
            boolean statementsSet = !isEmpty(triple.statements) || !isEmpty(statements);
            return conditionSet ^ (prepareSet || statementsSet);
        }

        private static boolean isEmpty(CodeTree e) {
            return e == null || e.isEmpty();
        }

        @Override
        public String toString() {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startGroup();
            if (!isEmpty(prepare)) {
                b.tree(prepare);
            }
            if (!isEmpty(condition)) {
                b.startIf().tree(condition).end().startBlock();
            }
            if (!isEmpty(statements)) {
                b.tree(statements);
            }

            if (!isEmpty(condition)) {
                b.end();
            }
            b.end();
            return b.build().toString();
        }

        private static IfTriple merge(String conditionSep, Set<IfTriple> triples) {
            if (triples.isEmpty()) {
                throw new AssertionError();
            }
            if (triples.size() == 1) {
                return triples.iterator().next();
            }
            CodeTree[] prepareTrees = new CodeTree[triples.size()];
            CodeTree[] conditionTrees = new CodeTree[triples.size()];
            CodeTree[] statementTrees = new CodeTree[triples.size()];
            int index = 0;
            for (IfTriple triple : triples) {
                prepareTrees[index] = triple.prepare;
                conditionTrees[index] = triple.condition;
                statementTrees[index] = triple.statements;
                index++;
            }
            return new IfTriple(combineTrees(null, prepareTrees),
                            combineTrees(conditionSep, conditionTrees),
                            combineTrees(null, statementTrees));
        }

        public static List<IfTriple> optimize(List<IfTriple> triples) {
            List<IfTriple> newTriples = new ArrayList<>();
            Set<IfTriple> mergable = new LinkedHashSet<>();
            IfTriple prev = null;
            for (IfTriple triple : triples) {
                if (prev != null) {
                    if (prev.canBeMerged(triple)) {
                        mergable.add(triple);
                    } else {
                        newTriples.add(merge(" && ", mergable));
                        mergable.clear();
                    }
                }
                prev = triple;
                mergable.add(prev);
            }
            if (prev != null) {
                newTriples.add(merge(" && ", mergable));
            }
            return newTriples;
        }

        public static BlockState materialize(CodeTreeBuilder builder, Collection<IfTriple> triples, boolean forceNoBlocks) {
            int blockCount = 0;
            int ifCount = 0;
            boolean otherPrepare = false;
            for (IfTriple triple : triples) {
                if (triple.prepare != null && !triple.prepare.isEmpty()) {
                    if (!otherPrepare) {
                        if (blockCount == 0 && !forceNoBlocks) {
                            builder.startBlock();
                            blockCount++;
                        }
                        otherPrepare = true;
                    }
                    builder.tree(triple.prepare);
                }
                if (triple.condition != null && !triple.condition.isEmpty()) {
                    if (forceNoBlocks) {
                        throw new AssertionError("no blocks forced but block required");
                    }
                    builder.startIf().tree(triple.condition).end().startBlock();
                    blockCount++;
                    ifCount++;
                }
                if (triple.statements != null && !triple.statements.isEmpty()) {
                    builder.tree(triple.statements);
                }
            }
            return BlockState.create(ifCount, blockCount);
        }

    }

    private static boolean guardNeedsStateBit(SpecializationData specialization, GuardExpression guard) {
        if (specialization.isReachesFallback() && specialization.isGuardBoundWithCache(guard)) {
            return true;
        }
        return false;
    }

    private static GuardExpression getGuardThatNeedsStateBit(SpecializationData specialization, GuardExpression guard) {
        if (guardNeedsStateBit(specialization, guard)) {
            return guard;
        }
        List<GuardExpression> guards = specialization.getGuards();
        int index = guards.indexOf(guard);
        if (index < 0) {
            throw new AssertionError("guard must be contained");
        }
        for (int i = index - 1; i >= 0; i--) {
            GuardExpression otherGuard = guards.get(i);
            if (guardNeedsStateBit(specialization, otherGuard)) {
                return otherGuard;
            }
        }
        return null;
    }

    private CodeTree visitSpecializationGroup(CodeTreeBuilder parent, SpecializationGroup originalPrev, SpecializationGroup group, ExecutableTypeData forType,
                    FrameState frameState, Collection<SpecializationData> allowedSpecializations) {
        final CodeTreeBuilder builder = parent.create();
        SpecializationGroup prev = originalPrev;

        NodeExecutionMode mode = frameState.getMode();
        boolean hasFallthrough = false;
        boolean hasImplicitCast = false;
        List<IfTriple> cachedTriples = new ArrayList<>();
        for (TypeGuard guard : group.getTypeGuards()) {
            IfTriple triple = createTypeCheckOrCast(frameState, group, guard, mode, false, true);
            if (triple != null) {
                cachedTriples.add(triple);
            }
            hasImplicitCast = hasImplicitCast || node.getTypeSystem().hasImplicitSourceTypes(guard.getType());
            if (!mode.isGuardFallback()) {
                triple = createTypeCheckOrCast(frameState, group, guard, mode, true, true);
                if (triple != null) {
                    cachedTriples.add(triple);
                }
            }
        }

        SpecializationData specialization = group.getSpecialization();
        SpecializationData[] specializations = group.collectSpecializations().toArray(new SpecializationData[0]);
        List<GuardExpression> guardExpressions = new ArrayList<>(group.getGuards());

        // for specializations with multiple instances we can move certain guards
        // out of the loop.
        if (specialization != null && specialization.hasMultipleInstances()) {
            List<GuardExpression> unboundGuards = new ArrayList<>();
            for (GuardExpression guard : guardExpressions) {
                if (!specialization.isGuardBoundWithCache(guard)) {
                    unboundGuards.add(guard);
                } else {
                    // we need to stop as we need to ensure guard execution order
                    break;
                }
            }
            cachedTriples.addAll(createMethodGuardChecks(frameState, group, unboundGuards, mode));
            guardExpressions.removeAll(unboundGuards);
        }

        boolean useSpecializationClass = specialization != null && useSpecializationClass(specialization);
        boolean needsRewrites = needsRewrites();

        if (mode.isFastPath()) {

            BlockState ifCount = BlockState.NONE;
            final boolean stateGuaranteed = group.isLast() && allowedSpecializations != null && allowedSpecializations.size() == 1 &&
                            group.getAllSpecializations().size() == allowedSpecializations.size();
            if (needsRewrites && (!group.isEmpty() || specialization != null)) {
                CodeTree stateCheck = state.createContains(frameState, specializations);
                CodeTree stateGuard = null;
                CodeTree assertCheck = null;
                if (stateGuaranteed) {
                    assertCheck = CodeTreeBuilder.createBuilder().startAssert().tree(stateCheck).end().build();
                } else {
                    stateGuard = stateCheck;
                }
                cachedTriples.add(0, new IfTriple(null, stateGuard, assertCheck));
            }
            ifCount = ifCount.add(IfTriple.materialize(builder, IfTriple.optimize(cachedTriples), false));
            cachedTriples = new ArrayList<>(); // reset current triples

            String specializationLocalName = null;
            if (useSpecializationClass) {
                specializationLocalName = createSpecializationLocalName(specialization);
                builder.tree(loadSpecializationClass(frameState, specialization));
                if (specialization.getMaximumNumberOfInstances() > 1) {
                    builder.startWhile();
                } else {
                    builder.startIf();
                }
                builder.string(specializationLocalName, " != null");
                builder.end();
                builder.startBlock();
                ifCount = ifCount.incrementIf();
            }

            if (specialization != null && !specialization.getAssumptionExpressions().isEmpty()) {
                builder.tree(createFastPathAssumptionCheck(builder, specialization, forType, frameState));
            }

            boolean extractInBoundary = false;
            boolean pushEnclosingNode = false;

            if (specialization != null) {
                for (GuardExpression guard : guardExpressions) {
                    Set<CacheExpression> caches = group.getSpecialization().getBoundCaches(guard.getExpression(), true);
                    if (cachesRequireFastPathBoundary(caches)) {
                        // boundary cached is used in guard
                        pushEnclosingNode = true;
                    }
                }
                if (!pushEnclosingNode) {
                    List<CacheExpression> caches = specialization.getCaches();
                    extractInBoundary |= cachesRequireFastPathBoundary(caches);
                    if (specialization.getFrame() != null) {
                        if (ElementUtils.typeEquals(specialization.getFrame().getType(), types.VirtualFrame)) {
                            // not supported for frames
                            extractInBoundary = false;
                        }
                    }
                }
            }
            List<IfTriple> nonBoundaryGuards = new ArrayList<>();
            for (Iterator<GuardExpression> iterator = guardExpressions.iterator(); iterator.hasNext();) {
                GuardExpression guard = iterator.next();
                Set<CacheExpression> caches = group.getSpecialization().getBoundCaches(guard.getExpression(), true);
                if (cachesRequireFastPathBoundary(caches)) {
                    // boundary cached is used in guard
                    break;
                }
                nonBoundaryGuards.addAll(initializeCaches(frameState, mode, group, caches, true, false));
                nonBoundaryGuards.add(createMethodGuardCheck(frameState, group.getSpecialization(), guard, mode));
                iterator.remove();
            }

            FrameState innerFrameState = frameState;
            if (extractInBoundary) {
                innerFrameState = frameState.copy();
                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.isAlwaysInitialized()) {
                        setCacheInitialized(innerFrameState, specialization, cache, false);
                    }
                }
            }

            for (GuardExpression guard : guardExpressions) {
                Set<CacheExpression> caches = group.getSpecialization().getBoundCaches(guard.getExpression(), true);
                cachedTriples.addAll(initializeCaches(innerFrameState, mode, group, caches, true, false));
                cachedTriples.add(createMethodGuardCheck(innerFrameState, group.getSpecialization(), guard, mode));
            }

            if (specialization != null) {
                cachedTriples.addAll(initializeCaches(innerFrameState, frameState.getMode(), group, specialization.getCaches(), true, false));
            }

            if (pushEnclosingNode && extractInBoundary) {
                throw new AssertionError("cannot push enclosing node and extract boundary");
            }

            BlockState nonBoundaryIfCount = BlockState.NONE;
            final CodeTreeBuilder innerBuilder;
            if (extractInBoundary) {
                nonBoundaryIfCount = nonBoundaryIfCount.add(IfTriple.materialize(builder, IfTriple.optimize(nonBoundaryGuards), false));
                innerBuilder = extractInBoundaryMethod(builder, frameState, specialization);
            } else if (pushEnclosingNode) {
                innerBuilder = builder;
                nonBoundaryIfCount = IfTriple.materialize(innerBuilder, IfTriple.optimize(nonBoundaryGuards), false);
            } else {
                innerBuilder = builder;
                cachedTriples.addAll(0, nonBoundaryGuards);
            }

            if (pushEnclosingNode || extractInBoundary) {
                GeneratorUtils.pushEncapsulatingNode(innerBuilder, "this");
                innerBuilder.startTryBlock();
            }

            BlockState innerIfCount = BlockState.NONE;
            innerIfCount = innerIfCount.add(IfTriple.materialize(innerBuilder, IfTriple.optimize(cachedTriples), false));
            prev = visitSpecializationGroupChildren(builder, innerFrameState, prev, group, forType, allowedSpecializations);
            if (specialization != null && (prev == null || prev.hasFallthrough())) {
                innerBuilder.tree(createFastPathExecute(builder, forType, specialization, innerFrameState));
            }

            innerBuilder.end(innerIfCount.blockCount);
            hasFallthrough |= innerIfCount.ifCount > 0;

            if (pushEnclosingNode || extractInBoundary) {
                innerBuilder.end().startFinallyBlock();
                GeneratorUtils.popEncapsulatingNode(innerBuilder);
                innerBuilder.end();
            }
            builder.end(nonBoundaryIfCount.blockCount);

            if (useSpecializationClass && specialization.getMaximumNumberOfInstances() > 1) {
                String name = createSpecializationLocalName(specialization);
                builder.startStatement().string(name, " = ", name, ".next_").end();
            }

            builder.end(ifCount.blockCount);
            hasFallthrough |= ifCount.ifCount > 0;

        } else if (mode.isSlowPath()) {

            if (specialization != null && mayBeExcluded(specialization)) {
                CodeTree excludeCheck = exclude.createNotContains(frameState, specializations);
                cachedTriples.add(0, new IfTriple(null, excludeCheck, null));
            }

            BlockState outerIfCount = BlockState.NONE;
            if (specialization == null) {
                cachedTriples.addAll(createMethodGuardChecks(frameState, group, guardExpressions, mode));

                outerIfCount = outerIfCount.add(IfTriple.materialize(builder, IfTriple.optimize(cachedTriples), false));

                prev = visitSpecializationGroupChildren(builder, frameState, prev, group, forType, allowedSpecializations);
            } else {

                for (CacheExpression cache : specialization.getCaches()) {
                    if (!cache.isAlwaysInitialized()) {
                        continue;
                    }
                    CodeTree prepare = CodeTreeBuilder.createBuilder().declarationDefault(cache.getParameter().getType(),
                                    createCacheLocalName(specialization, cache)).build();
                    cachedTriples.add(0, new IfTriple(prepare, null, null));
                }

                outerIfCount = outerIfCount.add(IfTriple.materialize(builder, IfTriple.optimize(cachedTriples), false));
                String countName = specialization != null ? "count" + specialization.getIndex() + "_" : null;
                boolean needsDuplicationCheck = specialization.isGuardBindsCache() || specialization.hasMultipleInstances();
                boolean useDuplicateFlag = specialization.isGuardBindsCache() && !specialization.hasMultipleInstances();
                String duplicateFoundName = specialization.getId() + "_duplicateFound_";

                boolean pushBoundary = cachesRequireFastPathBoundary(specialization.getCaches());
                if (pushBoundary) {
                    builder.startBlock();
                    GeneratorUtils.pushEncapsulatingNode(builder, "this");
                    builder.startTryBlock();
                }
                BlockState innerIfCount = BlockState.NONE;

                String specializationLocalName = createSpecializationLocalName(specialization);

                if (needsDuplicationCheck) {
                    builder.tree(createDuplicationCheck(builder, frameState, group, guardExpressions, useDuplicateFlag, countName, duplicateFoundName,
                                    specializationLocalName));

                    builder.startIf();
                    if (useDuplicateFlag) {
                        // we reuse the specialization class local name instead of a duplicate found
                        // name
                        builder.string("!", duplicateFoundName);
                    } else {
                        builder.string(createSpecializationLocalName(specialization), " == null");
                    }
                    builder.end().startBlock();
                    innerIfCount = innerIfCount.incrementIf();
                }

                FrameState innerFrameState = frameState.copy();

                List<IfTriple> innerTripples = new ArrayList<>();
                innerTripples.addAll(createMethodGuardChecks(innerFrameState, group, guardExpressions, mode));

                List<AssumptionExpression> assumptions = specialization.getAssumptionExpressions();
                if (!assumptions.isEmpty()) {
                    for (AssumptionExpression assumption : assumptions) {
                        innerTripples.addAll(createAssumptionSlowPathTriples(innerFrameState, group, assumption));
                    }
                }

                if (specialization.hasMultipleInstances()) {
                    DSLExpression limit = optimizeExpression(specialization.getLimitExpression());

                    Set<CacheExpression> caches = specialization.getBoundCaches(limit, true);
                    innerTripples.addAll(initializeCaches(innerFrameState, innerFrameState.getMode(), group, caches, true, false));

                    CodeTree limitExpression = writeExpression(innerFrameState, specialization, limit);
                    CodeTree limitCondition = CodeTreeBuilder.createBuilder().string(countName).string(" < ").tree(limitExpression).build();

                    innerTripples.add(new IfTriple(null, limitCondition, null));

                    // assert that specialization is not initialized
                    // otherwise we have been inserting invalid instances
                    assertSpecializationClassNotInitialized(innerFrameState, specialization);
                } else if (needsDuplicationCheck) {
                    innerTripples.add(new IfTriple(null, state.createNotContains(innerFrameState, new Object[]{specialization}), null));
                }

                innerIfCount = innerIfCount.add(IfTriple.materialize(builder, IfTriple.optimize(innerTripples), false));
                builder.tree(createSpecialize(builder, innerFrameState, group, specialization));

                if (needsDuplicationCheck) {
                    hasFallthrough = true;
                    if (useDuplicateFlag) {
                        builder.startStatement().string(duplicateFoundName, " = true").end();
                    }
                    builder.end(innerIfCount.blockCount);

                    /*
                     * We keep around always initialized caches in locals explicitly to avoid that
                     * weak references get collected between null check and specialization
                     * invocation.
                     */
                    for (CacheExpression cache : specialization.getCaches()) {
                        if (!cache.isAlwaysInitialized()) {
                            continue;
                        }
                        setCacheInitialized(frameState, specialization, cache, true);
                    }

                    // need to ensure that we update the implicit cast specializations on duplicates
                    CodeTree updateImplicitCast = createUpdateImplicitCastState(builder, frameState, specialization);
                    if (updateImplicitCast != null) {
                        builder.startElseBlock();
                        builder.tree(createUpdateImplicitCastState(builder, frameState, specialization));
                        builder.tree(state.createSet(frameState, new Object[]{specialization}, true, true));
                        builder.end();
                    }

                    builder.startIf();
                    if (useDuplicateFlag) {
                        builder.string(duplicateFoundName);
                    } else {
                        builder.string(createSpecializationLocalName(specialization), " != null");
                    }
                    builder.end().startBlock();

                    builder.tree(createCallSpecialization(builder, frameState, executeAndSpecializeType, specialization));
                    builder.end();
                } else {
                    builder.tree(createCallSpecialization(builder, innerFrameState, executeAndSpecializeType, specialization));
                    builder.end(innerIfCount.blockCount);
                    hasFallthrough |= innerIfCount.ifCount > 0;
                }

                if (pushBoundary) {
                    builder.end().startFinallyBlock();
                    GeneratorUtils.popEncapsulatingNode(builder);
                    builder.end();
                    builder.end();
                }

            }

            builder.end(outerIfCount.blockCount);
            hasFallthrough |= outerIfCount.ifCount > 0;

        } else if (mode.isGuardFallback()) {
            BlockState ifCount = BlockState.NONE;

            if (specialization != null && specialization.getMaximumNumberOfInstances() > 1) {
                throw new AssertionError("unsupported path. should be caught by parser..");
            }

            BlockState innerIfCount = BlockState.NONE;
            cachedTriples.addAll(createMethodGuardChecks(frameState, group, guardExpressions, mode));
            cachedTriples.addAll(createAssumptionCheckTriples(frameState, specialization, NodeExecutionMode.FALLBACK_GUARD));

            cachedTriples = IfTriple.optimize(cachedTriples);

            if (specialization != null && !hasImplicitCast) {
                IfTriple singleCondition = null;
                if (cachedTriples.size() == 1) {
                    singleCondition = cachedTriples.get(0);
                }
                if (singleCondition != null) {
                    int index = cachedTriples.indexOf(singleCondition);
                    CodeTree stateCheck = state.createNotContains(frameState, specializations);
                    cachedTriples.set(index, new IfTriple(singleCondition.prepare, combineTrees(" && ", stateCheck, singleCondition.condition), singleCondition.statements));
                    fallbackNeedsState = true;
                }
            }

            innerIfCount = innerIfCount.add(IfTriple.materialize(builder, cachedTriples, false));
            prev = visitSpecializationGroupChildren(builder, frameState, prev, group, forType, allowedSpecializations);
            if (specialization != null && (prev == null || prev.hasFallthrough())) {
                builder.returnFalse();
            }

            builder.end(innerIfCount.blockCount);

            builder.end(ifCount.blockCount);
            hasFallthrough |= ifCount.ifCount > 0 || innerIfCount.ifCount > 0;

        } else if (mode.isUncached()) {
            BlockState ifCount = BlockState.NONE;

            if (specialization != null) {
                cachedTriples.addAll(createAssumptionCheckTriples(frameState, specialization, NodeExecutionMode.UNCACHED));
            }

            ifCount = ifCount.add(IfTriple.materialize(builder, IfTriple.optimize(cachedTriples), false));
            cachedTriples = createMethodGuardChecks(frameState, group, guardExpressions, mode);

            BlockState innerIfCount = IfTriple.materialize(builder, IfTriple.optimize(cachedTriples), false);

            prev = visitSpecializationGroupChildren(builder, frameState, prev, group, forType, allowedSpecializations);
            if (specialization != null && (prev == null || prev.hasFallthrough())) {
                builder.tree(createCallSpecialization(builder, frameState, forType, specialization));
            }
            builder.end(innerIfCount.blockCount);
            builder.end(ifCount.blockCount);
            hasFallthrough |= ifCount.ifCount > 0 || innerIfCount.ifCount > 0;
        } else {
            throw new AssertionError("unexpected path");
        }

        group.setFallthrough(hasFallthrough);

        return builder.build();
    }

    private SpecializationGroup visitSpecializationGroupChildren(final CodeTreeBuilder builder, FrameState frameState, SpecializationGroup prev, SpecializationGroup group, ExecutableTypeData forType,
                    Collection<SpecializationData> allowedSpecializations) {
        SpecializationGroup currentPrev = prev;
        for (SpecializationGroup child : group.getChildren()) {
            if (currentPrev != null && !currentPrev.hasFallthrough()) {
                break;
            }
            builder.tree(visitSpecializationGroup(builder, prev, child, forType, frameState.copy(), allowedSpecializations));
            currentPrev = child;
        }
        return currentPrev;
    }

    private int boundaryIndex = 0;
    private final Set<String> usedBoundaryNames = new HashSet<>();

    private CodeTreeBuilder extractInBoundaryMethod(CodeTreeBuilder builder, FrameState frameState, SpecializationData specialization) {
        CodeTreeBuilder innerBuilder;
        CodeExecutableElement parentMethod = (CodeExecutableElement) builder.findMethod();

        String boundaryMethodName;
        if (specialization != null) {
            boundaryMethodName = specialization.getId() + "Boundary";
        } else {
            boundaryMethodName = "specializationBoundary";
        }
        boundaryMethodName = firstLetterLowerCase(boundaryMethodName);

        if (usedBoundaryNames.contains(boundaryMethodName)) {
            boundaryMethodName = boundaryMethodName + (boundaryIndex++);
        }
        usedBoundaryNames.add(boundaryMethodName);

        String includeFrameParameter = null;
        if (specialization != null && specialization.getFrame() != null) {
            includeFrameParameter = FRAME_VALUE;
        }
        CodeExecutableElement boundaryMethod = new CodeExecutableElement(modifiers(PRIVATE), parentMethod.getReturnType(), boundaryMethodName);
        frameState.addParametersTo(boundaryMethod, Integer.MAX_VALUE, STATE_VALUE, includeFrameParameter,
                        createSpecializationLocalName(specialization));
        boundaryMethod.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
        boundaryMethod.getThrownTypes().addAll(parentMethod.getThrownTypes());
        innerBuilder = boundaryMethod.createBuilder();
        ((CodeTypeElement) parentMethod.getEnclosingElement()).add(boundaryMethod);
        builder.startReturn().startCall("this", boundaryMethod);
        frameState.addReferencesTo(builder, STATE_VALUE, includeFrameParameter, createSpecializationLocalName(specialization));
        builder.end().end();

        return innerBuilder;
    }

    private static boolean cachesRequireFastPathBoundary(Collection<CacheExpression> caches) {
        for (CacheExpression cache : caches) {
            if (cache.isAlwaysInitialized() && cache.isRequiresBoundary()) {
                return true;
            }
        }
        return false;
    }

    private List<IfTriple> createAssumptionCheckTriples(FrameState frameState, SpecializationData specialization, NodeExecutionMode mode) {
        if (specialization == null || specialization.getAssumptionExpressions().isEmpty()) {
            return Collections.emptyList();
        }

        List<IfTriple> triples = new ArrayList<>();
        List<AssumptionExpression> assumptions = specialization.getAssumptionExpressions();
        for (AssumptionExpression assumption : assumptions) {
            CodeTree prepare = null;
            CodeTree assumptionReference;
            if (mode.isUncached()) {
                String localName = assumption.getId();
                CodeTreeBuilder builder = new CodeTreeBuilder(null);
                CodeTree assumptionInit = writeExpression(frameState, specialization, assumption.getExpression());
                builder.declaration(assumption.getExpression().getResolvedType(), localName,
                                assumptionInit);
                prepare = builder.build();
                assumptionReference = CodeTreeBuilder.singleString(localName);
            } else {
                assumptionReference = createAssumptionReference(frameState, specialization, assumption);
            }

            CodeTree assumptionGuard = createAssumptionGuard(assumptionReference);
            CodeTreeBuilder builder = new CodeTreeBuilder(null);
            builder.string("(");
            builder.tree(assumptionReference).string(" == null || ");
            builder.tree(assumptionGuard);
            builder.string(")");
            triples.add(new IfTriple(prepare, builder.build(), null));
        }
        return triples;
    }

    private CodeTree writeExpression(FrameState frameState, SpecializationData specialization, DSLExpression expression) throws AssertionError {
        expression.accept(new AbstractDSLExpressionVisitor() {
            @Override
            public void visitCall(Call binary) {
                frameState.addThrownExceptions(binary.getResolvedMethod());
            }
        });
        return DSLExpressionGenerator.write(optimizeExpression(expression), null,
                        castBoundTypes(bindExpressionValues(frameState, expression, specialization)));
    }

    private List<IfTriple> createAssumptionSlowPathTriples(FrameState frameState, SpecializationGroup group, AssumptionExpression assumption) throws AssertionError {
        List<IfTriple> triples = new ArrayList<>();
        LocalVariable var = frameState.get(assumption.getId());
        CodeTree declaration = null;
        if (var == null) {
            triples.addAll(initializeCaches(frameState, frameState.getMode(), group, group.getSpecialization().getBoundCaches(assumption.getExpression(), true), true, false));
            CodeTree assumptionExpressions = writeExpression(frameState, group.getSpecialization(), assumption.getExpression());
            String name = createAssumptionFieldName(group.getSpecialization(), assumption);
            var = new LocalVariable(assumption.getExpression().getResolvedType(), name.substring(0, name.length() - 1), null);
            frameState.set(assumption.getId(), var);
            declaration = var.createDeclaration(assumptionExpressions);
        }
        triples.add(new IfTriple(declaration, createAssumptionGuard(var.createReference()), null));
        return triples;
    }

    private CodeTree createDuplicationCheck(CodeTreeBuilder parent, FrameState frameState, SpecializationGroup group, List<GuardExpression> guardExpressions,
                    boolean useDuplicate, String countName, String duplicateFoundName, String specializationLocalName) {
        SpecializationData specialization = group.getSpecialization();

        CodeTreeBuilder builder = parent.create();
        if (!useDuplicate) {
            builder.declaration("int", countName, CodeTreeBuilder.singleString("0"));
        }
        if (useSpecializationClass(specialization)) {
            builder.tree(loadSpecializationClass(frameState, specialization));
        }
        if (!specialization.hasMultipleInstances()) {
            builder.declaration("boolean", duplicateFoundName, CodeTreeBuilder.singleString("false"));
        }

        FrameState innerFrameState = frameState.copy();
        builder.startIf().tree(state.createContains(innerFrameState, new Object[]{specialization})).end().startBlock();

        if (specialization.hasMultipleInstances()) {
            builder.startWhile().string(specializationLocalName, " != null").end().startBlock();
        }

        List<IfTriple> duplicationtriples = new ArrayList<>();
        duplicationtriples.addAll(createMethodGuardChecks(innerFrameState, group, guardExpressions, NodeExecutionMode.FAST_PATH));
        duplicationtriples.addAll(createAssumptionCheckTriples(innerFrameState, specialization, NodeExecutionMode.SLOW_PATH));
        BlockState duplicationIfCount = IfTriple.materialize(builder, IfTriple.optimize(duplicationtriples), false);
        if (useDuplicate) {
            builder.startStatement().string(duplicateFoundName, " = true").end();
        }

        List<CacheExpression> cachesToInitialize = new ArrayList<>();
        for (CacheExpression cache : specialization.getCaches()) {
            if (!cache.isAlwaysInitialized()) {
                continue;
            }
            if (isCacheInitialized(innerFrameState, specialization, cache)) {
                continue;
            }

            cachesToInitialize.add(cache);
        }
        if (!cachesToInitialize.isEmpty()) {
            List<IfTriple> triples = initializeCaches(innerFrameState, NodeExecutionMode.FAST_PATH, group, cachesToInitialize, true, false);

            IfTriple.materialize(builder, IfTriple.optimize(triples), true);
        }

        if (specialization.hasMultipleInstances()) {
            builder.statement("break");
        }
        builder.end(duplicationIfCount.blockCount);

        if (useDuplicate) {
            // no counting and next traversal necessary for duplication only check
        } else {
            if (specialization.getMaximumNumberOfInstances() > 1) {
                builder.startStatement().string(specializationLocalName, " = ", specializationLocalName, ".next_").end();
            } else {
                builder.statement(specializationLocalName + " = null");
            }
            builder.statement(countName + "++");
            builder.end();
        }

        builder.end();
        return builder.build();
    }

    private CodeTree createSpecialize(CodeTreeBuilder parent, FrameState frameState, SpecializationGroup group, SpecializationData specialization) {
        CodeTreeBuilder builder = parent.create();

        List<IfTriple> triples = new ArrayList<>();

        triples.addAll(initializeSpecializationClass(frameState, specialization));
        triples.addAll(initializeCaches(frameState, frameState.getMode(), group, specialization.getCaches(), false, true));
        triples.addAll(persistAssumptions(frameState, specialization));
        triples.addAll(persistSpecializationClass(frameState, specialization));
        builder.end(IfTriple.materialize(builder, triples, true).blockCount);

        List<SpecializationData> excludesSpecializations = new ArrayList<>();
        for (SpecializationData otherSpeciailzation : reachableSpecializations) {
            if (otherSpeciailzation == specialization) {
                continue;
            }
            if (otherSpeciailzation.getExcludedBy().contains(specialization)) {
                excludesSpecializations.add(otherSpeciailzation);
            }
        }

        if (!excludesSpecializations.isEmpty()) {
            SpecializationData[] excludesArray = excludesSpecializations.toArray(new SpecializationData[0]);
            builder.tree(exclude.createSet(frameState, excludesArray, true, true));

            for (SpecializationData excludes : excludesArray) {
                if (useSpecializationClass(excludes)) {
                    builder.statement("this." + createSpecializationFieldName(excludes) + " = null");
                }
            }
            builder.tree((state.createSet(frameState, excludesArray, false, false)));
        }

        CodeTree updateImplicitCast = createUpdateImplicitCastState(builder, frameState, specialization);
        if (updateImplicitCast != null) {
            builder.tree(createUpdateImplicitCastState(builder, frameState, specialization));
        }

        builder.tree(state.createSet(frameState, new SpecializationData[]{specialization}, true, true));
        return builder.build();
    }

    private List<IfTriple> persistAssumptions(FrameState frameState, SpecializationData specialization) {
        List<IfTriple> triples = new ArrayList<>();
        for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
            LocalVariable var = frameState.get(assumption.getId());
            String name = createAssumptionFieldName(specialization, assumption);
            CodeTreeBuilder builder = new CodeTreeBuilder(null);
            builder.startStatement();
            builder.tree(createSpecializationFieldReference(frameState, specialization, name)).string(" = ").tree(var.createReference());
            builder.end();
            triples.add(new IfTriple(builder.build(), null, null));
        }
        return triples;
    }

    private CodeTree loadSpecializationClass(FrameState frameState, SpecializationData specialization) {
        if (!useSpecializationClass(specialization)) {
            return null;
        }

        String localName = createSpecializationLocalName(specialization);
        String typeName = createSpecializationTypeName(specialization);
        LocalVariable var = frameState.get(localName);
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        builder.startStatement();
        if (var == null) {
            builder.string(typeName);
            builder.string(" ");
        }
        builder.string(localName);
        builder.string(" = ");
        builder.tree(createSpecializationFieldReference(frameState, specialization, null));
        builder.end();
        if (var == null) {
            frameState.set(localName, new LocalVariable(new GeneratedTypeMirror("", typeName), localName, null));
        }
        return builder.build();
    }

    private Collection<IfTriple> persistSpecializationClass(FrameState frameState, SpecializationData specialization) {
        if (!useSpecializationClass(specialization)) {
            return Collections.emptyList();
        }
        String localName = createSpecializationLocalName(specialization);
        LocalVariable var = frameState.get(localName);
        if (var == null) {
            // no specialization class initialized
            return Collections.emptyList();
        }

        String persistFrameState = createSpecializationClassPersisted(specialization);
        if (frameState.getBoolean(persistFrameState, false)) {
            // no specialization class initialized
            return Collections.emptyList();
        } else {
            frameState.setBoolean(persistFrameState, true);
        }

        CodeTree ref = var.createReference();
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        builder.startStatement();
        builder.string("this.", createSpecializationFieldName(specialization));
        builder.string(" = ");
        builder.tree(ref);
        builder.end();
        return Arrays.asList(new IfTriple(builder.build(), null, null));
    }

    private static String createSpecializationClassPersisted(SpecializationData specialization) {
        return createSpecializationLocalName(specialization) + "$persisted";
    }

    private static void assertSpecializationClassNotInitialized(FrameState frameState, SpecializationData specialization) {
        String framestateVarName = createSpecializationClassInitialized(specialization);
        if (frameState.get(framestateVarName) != null) {
            throw new AssertionError("Specialization class already initialized. " + specialization);
        }
    }

    private Collection<? extends IfTriple> initializeSpecializationClass(FrameState frameState, SpecializationData specialization) {
        boolean useSpecializationClass = useSpecializationClass(specialization);
        if (useSpecializationClass) {
            String localName = createSpecializationLocalName(specialization);
            String typeName = createSpecializationTypeName(specialization);
            // we cannot use local name, because its used track reads not init writes
            String classInitialized = createSpecializationClassInitialized(specialization);
            if (!frameState.getBoolean(classInitialized, false)) {
                GeneratedTypeMirror type = new GeneratedTypeMirror("", typeName);

                CodeTreeBuilder initBuilder = new CodeTreeBuilder(null);
                boolean isNode = specializationClassIsNode(specialization);
                if (isNode) {
                    initBuilder.startCall("super", "insert");
                }
                initBuilder.startNew(typeName);
                if (specialization.getMaximumNumberOfInstances() > 1) {
                    initBuilder.string(createSpecializationFieldName(specialization));
                }
                initBuilder.end(); // new
                if (isNode) {
                    initBuilder.end();
                }

                CodeTree init = initBuilder.build();

                CodeTreeBuilder builder = new CodeTreeBuilder(null);
                builder.startStatement();
                if (frameState.get(localName) == null) {
                    // not yet declared
                    builder.string(typeName);
                    builder.string(" ");
                }
                builder.string(localName);
                builder.string(" = ");
                builder.tree(init);
                builder.end();
                frameState.setBoolean(classInitialized, true);
                frameState.set(localName, new LocalVariable(type, localName, CodeTreeBuilder.singleString(localName)));

                return Arrays.asList(new IfTriple(builder.build(), null, null));
            }
        }
        return Collections.emptyList();
    }

    private static String createSpecializationClassInitialized(SpecializationData specialization) {
        return createSpecializationLocalName(specialization) + "$initialized";
    }

    private CodeTree createUpdateImplicitCastState(CodeTreeBuilder parent, FrameState frameState, SpecializationData specialization) {
        CodeTreeBuilder builder = null;
        int signatureIndex = 0;
        for (Parameter p : specialization.getSignatureParameters()) {
            TypeMirror targetType = p.getType();
            TypeMirror polymorphicType = node.getPolymorphicSpecialization().findParameterOrDie(p.getSpecification().getExecution()).getType();
            if (typeSystem.hasImplicitSourceTypes(targetType) && needsCastTo(polymorphicType, targetType)) {
                String implicitFieldName = createImplicitTypeStateLocalName(p);
                if (builder == null) {
                    builder = parent.create();
                }
                builder.tree(state.createSetInteger(frameState, new TypeGuard(p.getType(), signatureIndex), CodeTreeBuilder.singleString(implicitFieldName)));
            }
            signatureIndex++;
        }
        return builder == null ? null : builder.build();
    }

    private CodeTree createAssumptionGuard(CodeTree assumptionValue) {
        return CodeTreeBuilder.createBuilder().startStaticCall(types.Assumption, "isValidAssumption").tree(assumptionValue).end().build();
    }

    private static CodeTree combineTrees(String sep, CodeTree... trees) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String s = "";
        for (CodeTree tree : trees) {
            if (tree != null && !tree.isEmpty()) {
                if (sep != null) {
                    builder.string(s);
                }
                builder.tree(tree);
                s = sep;
            }
        }
        return builder.build();
    }

    private CodeTree createFastPathAssumptionCheck(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData forType, FrameState frameState)
                    throws AssertionError {
        CodeTreeBuilder builder = parent.create();
        builder.startIf();
        String sep = "";
        for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
            builder.string(sep);
            builder.string("!");
            builder.tree(createAssumptionGuard(createAssumptionReference(frameState, specialization, assumption)));
            sep = " || ";
        }
        builder.end().startBlock();
        builder.tree(createTransferToInterpreterAndInvalidate());
        builder.tree(createRemoveThis(builder, frameState, forType, specialization));
        builder.end();
        return builder.build();
    }

    private static CodeTree createTryExecuteChild(LocalVariable value, CodeTree executeChild, boolean needDeclaration, boolean hasTry) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        boolean hasDeclaration = false;
        if ((hasTry || !executeChild.isSingleLine()) && needDeclaration) {
            builder.tree(value.createDeclaration(null));
            hasDeclaration = true;
        }

        if (hasTry) {
            builder.startTryBlock();
        } else {
            builder.startGroup();
        }

        if (executeChild.isSingleLine()) {
            builder.startStatement();
            if (hasDeclaration || !needDeclaration) {
                builder.tree(executeChild);
            } else {
                builder.type(value.getTypeMirror()).string(" ").tree(executeChild);
            }
            builder.end();
        } else {
            builder.tree(executeChild);
        }

        builder.end();

        return builder.build();
    }

    private ExecutableTypeData resolveTargetExecutable(NodeExecutionData execution, TypeMirror target) {
        NodeChildData child = execution.getChild();
        if (child == null) {
            return null;
        }
        ExecutableTypeData targetExecutable = child.findExecutableType(target);
        if (targetExecutable == null) {
            targetExecutable = child.findAnyGenericExecutableType(context);
        }
        return targetExecutable;
    }

    private CodeTree createCatchRewriteException(CodeTreeBuilder parent, SpecializationData specialization, ExecutableTypeData forType, FrameState frameState, CodeTree execution) {
        if (specialization.getExceptions().isEmpty()) {
            return execution;
        }
        CodeTreeBuilder builder = parent.create();
        builder.startTryBlock();
        builder.tree(execution);
        boolean nonSlowPath = false;
        TypeMirror[] exceptionTypes = new TypeMirror[specialization.getExceptions().size()];
        for (int i = 0; i < exceptionTypes.length; i++) {
            TypeMirror type = specialization.getExceptions().get(i).getJavaClass();
            if (!isAssignable(type, types.SlowPathException) && !isAssignable(type, context.getType(ArithmeticException.class))) {
                nonSlowPath = true;
            }
            exceptionTypes[i] = type;
        }
        builder.end().startCatchBlock(exceptionTypes, "ex");
        if (nonSlowPath) {
            builder.tree(createTransferToInterpreterAndInvalidate());
        } else {
            builder.lineComment("implicit transferToInterpreterAndInvalidate()");
        }

        builder.tree(createExcludeThis(builder, frameState, forType, specialization));

        builder.end();
        return builder.build();
    }

    private Map<SpecializationData, CodeExecutableElement> removeThisMethods = new HashMap<>();

    private CodeTree createExcludeThis(CodeTreeBuilder parent, FrameState frameState, ExecutableTypeData forType, SpecializationData specialization) {
        CodeTreeBuilder builder = parent.create();

        // slow path might be already already locked
        if (!frameState.getMode().isSlowPath()) {
            builder.declaration(context.getType(Lock.class), "lock", "getLock()");
        }

        if (needsSpecializeLocking) {
            builder.statement("lock.lock()");
            builder.startTryBlock();
        }

        SpecializationData[] specializations;
        if (specialization.getUncachedSpecialization() != null) {
            specializations = new SpecializationData[]{specialization, specialization.getUncachedSpecialization()};
        } else {
            specializations = new SpecializationData[]{specialization};
        }

        // pass null frame state to ensure values are reloaded.
        builder.tree(this.exclude.createSet(null, specializations, true, true));
        builder.tree(this.state.createSet(null, specializations, false, true));
        for (SpecializationData removeSpecialization : specializations) {
            if (useSpecializationClass(removeSpecialization)) {
                String fieldName = createSpecializationFieldName(removeSpecialization);
                builder.statement("this." + fieldName + " = null");
            }
        }

        if (needsSpecializeLocking) {
            builder.end().startFinallyBlock();
            builder.statement("lock.unlock()");
            builder.end();
        }
        boolean hasUnexpectedResultRewrite = specialization.hasUnexpectedResultRewrite();
        boolean hasReexecutingRewrite = !hasUnexpectedResultRewrite || specialization.getExceptions().size() > 1;

        if (hasReexecutingRewrite) {
            if (hasUnexpectedResultRewrite) {
                builder.startIf().string("ex").instanceOf(types.UnexpectedResultException).end().startBlock();
                builder.tree(createReturnUnexpectedResult(forType, true));
                builder.end().startElseBlock();
                builder.tree(createCallExecuteAndSpecialize(forType, frameState));
                builder.end();
            } else {
                builder.tree(createCallExecuteAndSpecialize(forType, frameState));
            }
        } else {
            assert hasUnexpectedResultRewrite;
            builder.tree(createReturnUnexpectedResult(forType, false));
        }

        builder.end();
        return builder.build();
    }

    private CodeTree createRemoveThis(CodeTreeBuilder parent, FrameState frameState, ExecutableTypeData forType, SpecializationData specialization) {
        CodeExecutableElement method = removeThisMethods.get(specialization);
        String specializationLocalName = createSpecializationLocalName(specialization);
        boolean useSpecializationClass = useSpecializationClass(specialization);
        if (method == null) {
            method = new CodeExecutableElement(context.getType(void.class), "remove" + specialization.getId() + "_");
            if (useSpecializationClass) {
                method.addParameter(new CodeVariableElement(context.getType(Object.class), specializationLocalName));
            }
            CodeTreeBuilder builder = method.createBuilder();
            if (needsSpecializeLocking) {
                builder.declaration(context.getType(Lock.class), "lock", "getLock()");
                builder.statement("lock.lock()");
                builder.startTryBlock();
            }
            String fieldName = createSpecializationFieldName(specialization);
            if (!useSpecializationClass || specialization.getMaximumNumberOfInstances() == 1) {
                // single instance remove
                builder.tree((state.createSet(null, new Object[]{specialization}, false, true)));
                if (useSpecializationClass) {
                    builder.statement("this." + fieldName + " = null");
                }
            } else {
                // multi instance remove
                String typeName = createSpecializationTypeName(specialization);
                boolean specializedIsNode = specializationClassIsNode(specialization);
                builder.declaration(typeName, "prev", "null");
                builder.declaration(typeName, "cur", "this." + fieldName);
                builder.startWhile();
                builder.string("cur != null");
                builder.end().startBlock();
                builder.startIf().string("cur == ").string(specializationLocalName).end().startBlock();
                builder.startIf().string("prev == null").end().startBlock();
                builder.statement("this." + fieldName + " = cur.next_");
                if (specializedIsNode) {
                    builder.statement("this.adoptChildren()");
                }
                builder.end().startElseBlock();
                builder.statement("prev.next_ = cur.next_");
                if (specializedIsNode) {
                    builder.statement("prev.adoptChildren()");
                }
                builder.end();
                builder.statement("break");
                builder.end(); // if block
                builder.statement("prev = cur");
                builder.statement("cur = cur.next_");
                builder.end(); // while block

                builder.startIf().string("this." + fieldName).string(" == null").end().startBlock();
                builder.tree((state.createSet(null, Arrays.asList(specialization).toArray(new SpecializationData[0]), false, true)));
                builder.end();
            }
            if (needsSpecializeLocking) {
                builder.end().startFinallyBlock();
                builder.statement("lock.unlock()");
                builder.end();
            }
            removeThisMethods.put(specialization, method);
        }
        CodeTreeBuilder builder = parent.create();
        builder.startStatement().startCall(method.getSimpleName().toString());
        if (useSpecializationClass) {
            builder.string(specializationLocalName);
        }
        builder.end().end();
        builder.tree(createCallExecuteAndSpecialize(forType, frameState));
        return builder.build();
    }

    private CodeTree createCallExecute(ExecutableTypeData forType, ExecutableTypeData targetType, FrameState frameState) {
        TypeMirror returnType = targetType.getReturnType();

        List<CodeTree> bindings = new ArrayList<>();

        List<TypeMirror> sourceTypes = forType.getSignatureParameters();
        List<TypeMirror> targetTypes = targetType.getSignatureParameters();
        if (sourceTypes.size() != targetTypes.size()) {
            throw new IllegalArgumentException();
        }

        if (targetType.getFrameParameter() != null) {
            LocalVariable parameterLocal = frameState.get(FRAME_VALUE);
            TypeMirror parameterTargetType = targetType.getFrameParameter();
            if (parameterLocal == null) {
                bindings.add(CodeTreeBuilder.createBuilder().defaultValue(parameterTargetType).build());
            } else {
                bindings.add(parameterLocal.createReference());
            }
        }

        for (int i = 0; i < sourceTypes.size(); i++) {
            LocalVariable parameterLocal = frameState.getValue(i);
            TypeMirror parameterTargetType = targetTypes.get(i);
            if (parameterLocal == null) {
                bindings.add(CodeTreeBuilder.createBuilder().defaultValue(parameterTargetType).build());
            } else {
                bindings.add(parameterLocal.createReference());
            }
        }

        CodeTree call = callMethod(frameState, null, targetType.getMethod(), bindings.toArray(new CodeTree[0]));
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder = builder.create();
        if (isVoid(forType.getReturnType())) {
            builder.statement(call);
            builder.returnStatement();
        } else {
            builder.startReturn();
            builder.tree(expectOrCast(returnType, forType, call));
            builder.end();
        }
        return builder.build();
    }

    private CodeTree createCallExecuteAndSpecialize(ExecutableTypeData forType, FrameState frameState) {
        TypeMirror returnType = node.getPolymorphicSpecialization().getReturnType().getType();
        String frame = null;
        if (needsFrameToExecute(reachableSpecializations)) {
            frame = FRAME_VALUE;
        }

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startCall(createExecuteAndSpecializeName());
        frameState.addReferencesTo(builder, frame);
        builder.end();
        CodeTree call = builder.build();

        builder = builder.create();
        if (isVoid(forType.getReturnType())) {
            builder.statement(call);
            builder.returnStatement();
        } else {
            builder.startReturn();
            builder.tree(expectOrCast(returnType, forType, call));
            builder.end();
        }
        return builder.build();
    }

    private CodeTree createReturnUnexpectedResult(ExecutableTypeData forType, boolean needsCast) {
        TypeMirror returnType = context.getType(Object.class);

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startCall(needsCast ? "((UnexpectedResultException) ex)" : "ex", "getResult").end();
        CodeTree call = builder.build();

        builder = builder.create();
        if (isVoid(forType.getReturnType())) {
            builder.statement(call);
            builder.returnStatement();
        } else {
            builder.startReturn();
            builder.tree(expectOrCast(returnType, forType, call));
            builder.end();
        }
        return builder.build();
    }

    private List<IfTriple> createMethodGuardChecks(FrameState frameState, SpecializationGroup group, List<GuardExpression> guardExpressions, NodeExecutionMode mode) {
        List<IfTriple> triples = new ArrayList<>();
        for (GuardExpression guard : guardExpressions) {
            if (mode.isSlowPath() && !guard.isConstantTrueInSlowPath(context, mode.isUncached())) {
                CodeTreeBuilder builder = new CodeTreeBuilder(null);
                List<IfTriple> innerTriples = new ArrayList<>();
                boolean guardStateBit = guardNeedsStateBit(group.getSpecialization(), guard);
                FrameState innerFrameState = frameState;
                if (guardStateBit) {
                    if (group.getSpecialization() == null) {
                        throw new AssertionError();
                    }
                    innerFrameState = frameState.copy();
                    builder.startIf().tree(state.createNotContains(innerFrameState, new Object[]{guard})).end().startBlock();
                    innerTriples.addAll(initializeSpecializationClass(innerFrameState, group.getSpecialization()));
                    innerTriples.addAll(persistSpecializationClass(innerFrameState, group.getSpecialization()));
                }
                boolean store = !guardStateBit;

                Set<CacheExpression> boundCaches = group.getSpecialization().getBoundCaches(guard.getExpression(), true);
                innerTriples.addAll(initializeCaches(innerFrameState, mode, group, boundCaches, store, guardStateBit));
                innerTriples.addAll(initializeCasts(innerFrameState, group, guard.getExpression(), mode));

                IfTriple.materialize(builder, innerTriples, true);

                if (guardStateBit) {
                    builder.tree(state.createSet(innerFrameState, new Object[]{guard}, true, true));
                    builder.end();
                }
                triples.add(new IfTriple(builder.build(), null, null));
            } else if (mode.isGuardFallback()) {
                triples.addAll(initializeCasts(frameState, group, guard.getExpression(), mode));
            } else if (mode.isFastPath()) {
                triples.addAll(initializeCaches(frameState, mode, group, group.getSpecialization().getBoundCaches(guard.getExpression(), true), true, false));
            }
            triples.add(createMethodGuardCheck(frameState, group.getSpecialization(), guard, mode));
        }
        return triples;
    }

    private List<IfTriple> initializeCaches(FrameState frameState, NodeExecutionMode mode, SpecializationGroup group, Collection<CacheExpression> caches, boolean store, boolean forcePersist) {
        if (group.getSpecialization() == null || caches.isEmpty()) {
            return Collections.emptyList();
        }
        List<IfTriple> triples = new ArrayList<>();
        for (CacheExpression cache : caches) {
            if (cache.isEagerInitialize()) {
                continue;
            } else if (mode.isFastPath() && !cache.isAlwaysInitialized()) {
                continue;
            } else if (mode.isUncached() && cache.isWeakReference()) {
                continue;
            }
            boolean useStore = store;
            if (cache.isAlwaysInitialized()) {
                useStore = true;
            }
            triples.addAll(initializeCasts(frameState, group, cache.getDefaultExpression(), mode));
            triples.addAll(persistAndInitializeCache(frameState, group.getSpecialization(), cache, useStore, forcePersist));
        }
        return triples;
    }

    private Collection<IfTriple> persistAndInitializeCache(FrameState frameState, SpecializationData specialization, CacheExpression cache, boolean store, boolean persist) {
        List<IfTriple> triples = new ArrayList<>();
        triples.addAll(initializeReferences(frameState, cache));
        CodeTree init = initializeCache(frameState, specialization, cache);
        if (store) {
            // store as local variable
            triples.addAll(storeCache(frameState, specialization, cache, init));
        }
        if (persist) {
            // persist to node instance
            triples.addAll(persistCache(frameState, specialization, cache, init));
        }
        return triples;
    }

    private Collection<IfTriple> persistCache(FrameState frameState, SpecializationData specialization, CacheExpression cache, CodeTree cacheValue) {
        if (cache.isAlwaysInitialized()) {
            return Collections.emptyList();
        } else {
            List<IfTriple> triples = new ArrayList<>();
            String name = createFieldName(specialization, cache.getParameter());
            LocalVariable local = frameState.get(name);
            CodeTree value;
            if (local != null) {
                // already initialized and stored don't use init.
                value = local.createReference();
            } else if (cacheValue == null) {
                return Collections.emptyList();
            } else {
                value = cacheValue;
            }

            TypeMirror type = cache.getParameter().getType();
            String frameStateInitialized = name + "$initialized";
            if (frameState.getBoolean(frameStateInitialized, false)) {
                return Collections.emptyList();
            } else {
                frameState.setBoolean(frameStateInitialized, true);
            }

            CodeTreeBuilder builder = new CodeTreeBuilder(null);
            Parameter parameter = cache.getParameter();
            boolean useSpecializationClass = useSpecializationClass(specialization);

            String insertTarget;
            if (useSpecializationClass) {
                insertTarget = createSpecializationLocalName(specialization);
            } else {
                insertTarget = "super";
            }
            TypeMirror nodeType = types.Node;
            TypeMirror nodeArrayType = new ArrayCodeTypeMirror(types.Node);

            boolean isNode = isAssignable(parameter.getType(), nodeType);
            boolean isNodeInterface = isNode || isAssignable(type, types.NodeInterface);
            boolean isNodeArray = isAssignable(type, nodeArrayType);
            boolean isNodeInterfaceArray = isNodeArray || isNodeInterfaceArray(type);

            if (isNodeInterface || isNodeInterfaceArray) {
                builder = new CodeTreeBuilder(null);
                String fieldName = createFieldName(specialization, cache.getParameter()) + "__";
                String insertName = useSpecializationClass ? useInsertAccessor(specialization, isNodeInterfaceArray) : "insert";
                final TypeMirror castType;
                if (isNodeInterface) {
                    if (isNode) {
                        castType = null;
                    } else {
                        castType = nodeType;
                    }
                } else {
                    assert isNodeInterfaceArray;
                    if (isNodeArray) {
                        castType = null;
                    } else {
                        castType = nodeArrayType;
                    }
                }
                if (castType == null) {
                    CodeTreeBuilder noCast = new CodeTreeBuilder(null);
                    if (cache.isAdopt()) {
                        noCast.startCall(insertTarget, insertName);
                    }
                    noCast.tree(value);
                    if (cache.isAdopt()) {
                        noCast.end();
                    }
                    value = noCast.build();
                } else {
                    builder.declaration(cache.getDefaultExpression().getResolvedType(), fieldName, value);
                    if (cache.isAdopt()) {
                        builder.startIf().string(fieldName).instanceOf(castType).end().startBlock();
                        builder.startStatement();
                        builder.startCall(insertTarget, insertName);
                        builder.startGroup().cast(castType).string(fieldName).end();
                        builder.end().end();
                    }
                    builder.end();
                    value = CodeTreeBuilder.singleString(fieldName);
                }
            }

            CodeTree cacheReference = createCacheReference(frameState, specialization, cache);
            if (!cache.isEagerInitialize() && sharedCaches.containsKey(cache) && !ElementUtils.isPrimitive(cache.getParameter().getType())) {
                builder.startIf().tree(cacheReference).string(" == null").end().startBlock();
                builder.startStatement().tree(cacheReference).string(" = ").tree(value).end();
                builder.end();
            } else {
                builder.startStatement().tree(cacheReference).string(" = ").tree(value).end();
            }

            triples.add(new IfTriple(builder.build(), null, null));
            return triples;
        }

    }

    private final Map<String, Integer> uniqueSupplierLocalIndexes = new HashMap<>();

    private List<IfTriple> initializeReferences(FrameState frameState, CacheExpression cache) {
        if (isSupplierInitialized(frameState, cache)) {
            return Collections.emptyList();
        }

        String supplierName = createElementReferenceName(cache);
        String supplierLocalName = supplierName + "_";

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        int index = uniqueSupplierLocalIndexes.getOrDefault(supplierLocalName, 0);
        uniqueSupplierLocalIndexes.put(supplierLocalName, index + 1);
        if (index > 0) {
            supplierLocalName = supplierLocalName + index;
        }

        String method = cache.isCachedContext() ? "super.lookupContextReference" : "super.lookupLanguageReference";
        if (frameState.getMode().isSlowPath()) {
            builder.declaration(cache.getReferenceType(), supplierLocalName, "this." + supplierName);
            builder.startIf().string(supplierLocalName).string(" == null").end().startBlock();
            builder.startStatement().string("this.", supplierName).string(" = ").string(supplierLocalName).string(" = ").startCall(method).typeLiteral(cache.getLanguageType()).end().end();
            builder.end();
        } else {
            builder.startStatement().type(cache.getReferenceType()).string(" ", supplierLocalName).string(" = ").string("this.", supplierName).end();
        }

        setSupplierInitialized(frameState, cache, true);
        frameState.set(supplierName, new LocalVariable(cache.getReferenceType(), supplierLocalName, null));
        return Arrays.asList(new IfTriple(builder.build(), null, null));
    }

    private static boolean isSupplierInitialized(FrameState frameState, CacheExpression cache) {
        if (!cache.isCachedContext() && !cache.isCachedLanguage()) {
            return true;
        }
        String supplierName = createElementReferenceName(cache);
        String supplierInitialized = supplierName + "$initialized";
        return frameState.getBoolean(supplierInitialized, false);
    }

    private static void setSupplierInitialized(FrameState frameState, CacheExpression cache, boolean initialized) {
        if (!cache.isCachedContext() && !cache.isCachedLanguage()) {
            return;
        }
        String supplierName = createElementReferenceName(cache);
        String supplierInitialized = supplierName + "$initialized";
        frameState.setBoolean(supplierInitialized, initialized);
    }

    private Map<String, List<Parameter>> uniqueCachedParameterLocalNames = new HashMap<>();

    private Collection<IfTriple> storeCache(FrameState frameState, SpecializationData specialization, CacheExpression cache, CodeTree value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (isCacheInitialized(frameState, specialization, cache)) {
            // already initialized
            return Collections.emptyList();
        }

        TypeMirror type = cache.getParameter().getType();
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        String refName = createCacheLocalName(specialization, cache);

        CodeTree useValue;
        if ((ElementUtils.isAssignable(type, types.Node) || ElementUtils.isAssignable(type, new ArrayCodeTypeMirror(types.Node))) &&
                        (!cache.isAlwaysInitialized())) {
            useValue = builder.create().startCall("super.insert").tree(value).end().build();
        } else {
            useValue = value;
        }
        if (cache.isAlwaysInitialized() && frameState.getMode().isSlowPath()) {
            /*
             * For slow path methods we try to not
             */
            builder.startStatement().string(refName, " = ").tree(useValue).end();
        } else {
            builder.declaration(type, refName, useValue);
        }

        setCacheInitialized(frameState, specialization, cache, true);
        List<IfTriple> triples = new ArrayList<>();
        triples.add(new IfTriple(builder.build(), null, null));
        return triples;
    }

    private boolean isCacheInitialized(FrameState frameState, SpecializationData specialization, CacheExpression cache) {
        String name = createFieldName(specialization, cache.getParameter());
        return frameState.get(name) != null;
    }

    private void setCacheInitialized(FrameState frameState, SpecializationData specialization, CacheExpression cache, boolean initialized) {
        String name = createFieldName(specialization, cache.getParameter());
        if (initialized) {
            frameState.set(name, new LocalVariable(cache.getParameter().getType(), name,
                            CodeTreeBuilder.singleString(createCacheLocalName(specialization, cache))));
        } else {
            frameState.set(name, null);
            setSupplierInitialized(frameState, cache, false);
        }
    }

    private String createCacheLocalName(SpecializationData specialization, CacheExpression cache) {
        String name = createFieldName(specialization, cache.getParameter());
        String refName = name + "_";
        List<Parameter> variables = uniqueCachedParameterLocalNames.computeIfAbsent(refName, (v) -> new ArrayList<>());
        int index = variables.indexOf(cache.getParameter());
        if (index == -1) {
            index = variables.size();
            variables.add(cache.getParameter());
        }
        if (index != 0) {
            refName = name + "_" + index;
        }
        return refName;
    }

    private CodeTree initializeCache(FrameState frameState, SpecializationData specialization, CacheExpression cache) {
        String name = createFieldName(specialization, cache.getParameter());
        if (frameState.get(name) != null) {
            // already initialized
            return null;
        }
        CodeTree tree;
        if (cache.isMergedLibrary()) {
            if (frameState.getMode().isUncached()) {
                CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
                builder.staticReference(createLibraryConstant(libraryConstants, cache.getParameter().getType()));
                builder.startCall(".getUncached");
                builder.tree(writeExpression(frameState, specialization, cache.getDefaultExpression()));
                builder.end();
                tree = builder.build();
            } else {
                tree = CodeTreeBuilder.singleString("this." + cache.getMergedLibraryIdentifier());
            }
        } else if (cache.isCachedContext() || cache.isCachedLanguage()) {
            String fieldName = createElementReferenceName(cache);
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

            LocalVariable var = frameState.get(fieldName);
            if (var != null) {
                builder.tree(var.createReference());
            } else {
                builder.string("this.").string(fieldName);
            }
            if (!cache.isReference()) {
                builder.string(".get()");
            }
            tree = builder.build();
        } else {
            DSLExpression expression;
            if (frameState.getMode().isUncached()) {
                expression = cache.getUncachedExpression();
            } else {
                expression = cache.getDefaultExpression();
            }
            tree = writeExpression(frameState, specialization, expression);
        }
        return tree;
    }

    private static String createElementReferenceName(CacheExpression cache) {
        if (cache.isCachedContext()) {
            return ElementUtils.firstLetterLowerCase(ElementUtils.getSimpleName(cache.getLanguageType())) + "ContextReference_";
        } else if (cache.isCachedLanguage()) {
            return ElementUtils.firstLetterLowerCase(ElementUtils.getSimpleName(cache.getLanguageType())) + "Reference_";
        } else {
            throw new AssertionError();
        }
    }

    private IfTriple createMethodGuardCheck(FrameState frameState, SpecializationData specialization, GuardExpression guard, NodeExecutionMode mode) {
        DSLExpression expression = optimizeExpression(guard.getExpression());
        CodeTree init = null;
        CodeTree expressionCode = writeExpression(frameState, specialization, expression);
        if (mode.isGuardFallback()) {
            GuardExpression guardWithBit = getGuardThatNeedsStateBit(specialization, guard);
            if (guardWithBit != null) {
                CodeTreeBuilder builder = new CodeTreeBuilder(null);
                builder.string("(");
                builder.tree(state.createNotContains(frameState, new Object[]{guardWithBit}));
                builder.string(" || ");
                builder.tree(expressionCode);
                builder.string(")");
                expressionCode = builder.build();
                fallbackNeedsState = true;
            }
        }

        CodeTree assertion = null; // overrule with assertion
        if (mode.isFastPath() || mode.isGuardFallback()) {
            if (!specialization.isDynamicParameterBound(expression, true)) {
                assertion = CodeTreeBuilder.createBuilder().startAssert().tree(expressionCode).end().build();
                expressionCode = null;
            }
        } else {
            if (guard.isConstantTrueInSlowPath(context, mode.isUncached())) {
                assertion = CodeTreeBuilder.createBuilder().startStatement().string("// assert ").tree(expressionCode).end().build();
                expressionCode = null;
            }
        }

        return new IfTriple(init, expressionCode, assertion);
    }

    private static Map<Variable, CodeTree> castBoundTypes(Map<Variable, LocalVariable> bindings) {
        Map<Variable, CodeTree> resolvedBindings = new HashMap<>();
        for (Variable variable : bindings.keySet()) {
            LocalVariable localVariable = bindings.get(variable);
            CodeTree resolved = localVariable.createReference();
            TypeMirror sourceType = localVariable.getTypeMirror();
            TypeMirror targetType = variable.getResolvedTargetType();
            if (targetType == null) {
                targetType = variable.getResolvedType();
            }
            if (!isAssignable(sourceType, targetType)) {
                resolved = CodeTreeBuilder.createBuilder().startParantheses().cast(targetType, resolved).end().build();
            }
            resolvedBindings.put(variable, resolved);
        }
        return resolvedBindings;
    }

    private Map<Variable, LocalVariable> bindExpressionValues(FrameState frameState, DSLExpression expression, SpecializationData specialization) throws AssertionError {
        Map<Variable, LocalVariable> bindings = new HashMap<>();
        Set<Variable> boundVariables = expression.findBoundVariables();
        if (specialization == null && !boundVariables.isEmpty()) {
            throw new AssertionError("Cannot bind guard variable in non-specialization group. yet.");
        }

        // resolve bindings for local context
        for (Variable variable : boundVariables) {
            Parameter resolvedParameter = specialization.findByVariable(variable.getResolvedVariable());
            if (resolvedParameter != null) {
                LocalVariable localVariable = bindExpressionVariable(frameState, specialization, resolvedParameter);
                if (localVariable != null) {
                    bindings.put(variable, localVariable);
                }
            }
        }
        return bindings;
    }

    private LocalVariable bindExpressionVariable(FrameState frameState, SpecializationData specialization, Parameter resolvedParameter) {
        LocalVariable localVariable;
        if (resolvedParameter.getSpecification().isCached()) {
            // bind cached variable
            String cachedMemberName = createFieldName(specialization, resolvedParameter);
            localVariable = frameState.get(cachedMemberName);
            CodeTree ref;
            if (localVariable == null) {
                CacheExpression cache = specialization.findCache(resolvedParameter);
                ref = createCacheReference(frameState, specialization, cache);
            } else {
                ref = localVariable.createReference();
            }
            localVariable = new LocalVariable(resolvedParameter.getType(), cachedMemberName, ref);
        } else {
            // bind local variable
            if (resolvedParameter.getSpecification().isSignature()) {
                NodeExecutionData execution = resolvedParameter.getSpecification().getExecution();
                localVariable = frameState.getValue(execution);
            } else {
                localVariable = frameState.get(resolvedParameter.getLocalName());
            }
        }
        return localVariable;
    }

    private CodeTree createSpecializationFieldReference(FrameState frameState, SpecializationData s, String fieldName) {
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        if (useSpecializationClass(s)) {
            String localName = createSpecializationLocalName(s);
            LocalVariable var = frameState.get(localName);
            if (var != null) {
                builder.string(localName);
            } else {
                builder.string("this.", createSpecializationFieldName(s));
            }
        } else {
            builder.string("this");
        }
        if (fieldName != null) {
            builder.string(".");
            builder.string(fieldName);
        }
        return builder.build();
    }

    private CodeTree createCacheReference(FrameState frameState, SpecializationData specialization, CacheExpression cache) {
        if (cache == null) {
            return CodeTreeBuilder.singleString("null /* cache not resolved */");
        }
        if (frameState.getMode().isUncached()) {
            return initializeCache(frameState, specialization, cache);
        } else {
            if (cache.isAlwaysInitialized()) {
                return initializeCache(frameState, specialization, cache);
            } else {
                String sharedName = sharedCaches.get(cache);
                CodeTree ref;
                if (sharedName != null) {
                    ref = CodeTreeBuilder.createBuilder().string("this.").string(sharedName).build();
                } else {
                    String cacheFieldName = createFieldName(specialization, cache.getParameter());
                    ref = createSpecializationFieldReference(frameState, specialization, cacheFieldName);
                }
                return ref;
            }
        }
    }

    private CodeTree createAssumptionReference(FrameState frameState, SpecializationData s, AssumptionExpression a) {
        String assumptionFieldName = createAssumptionFieldName(s, a);
        return createSpecializationFieldReference(frameState, s, assumptionFieldName);
    }

    private IfTriple createTypeCheckOrCast(FrameState frameState, SpecializationGroup group, TypeGuard typeGuard,
                    NodeExecutionMode specializationExecution, boolean castOnly, boolean forceImplicitCast) {
        CodeTreeBuilder prepareBuilder = CodeTreeBuilder.createBuilder();
        CodeTreeBuilder checkBuilder = CodeTreeBuilder.createBuilder();
        int signatureIndex = typeGuard.getSignatureIndex();
        LocalVariable value = frameState.getValue(signatureIndex);
        TypeMirror targetType = typeGuard.getType();

        if (!needsCastTo(value.getTypeMirror(), targetType)) {
            TypeMirror genericTargetType = node.getGenericSpecialization().findParameterOrDie(node.getChildExecutions().get(signatureIndex)).getType();
            if (typeEquals(value.getTypeMirror(), genericTargetType)) {
                // no implicit casts needed if it matches the generic type
                return null;
            }

            boolean foundImplicitSubType = false;
            if (forceImplicitCast) {
                List<ImplicitCastData> casts = typeSystem.lookupByTargetType(targetType);
                for (ImplicitCastData cast : casts) {
                    if (isSubtype(cast.getSourceType(), targetType)) {
                        foundImplicitSubType = true;
                        break;
                    }
                }
            }
            if (!foundImplicitSubType) {
                return null;
            }
        }

        NodeExecutionData execution = node.getChildExecutions().get(signatureIndex);
        CodeTreeBuilder castBuilder = prepareBuilder.create();

        List<ImplicitCastData> sourceTypes = typeSystem.lookupByTargetType(targetType);
        CodeTree valueReference = value.createReference();
        if (sourceTypes.isEmpty()) {
            checkBuilder.tree(TypeSystemCodeGenerator.check(typeSystem, targetType, valueReference));
            castBuilder.tree(TypeSystemCodeGenerator.cast(typeSystem, targetType, valueReference));
        } else {
            List<SpecializationData> specializations = group.collectSpecializations();
            List<Parameter> parameters = new ArrayList<>();
            for (SpecializationData otherSpecialization : specializations) {
                parameters.add(otherSpecialization.findParameterOrDie(execution));
            }

            if (specializationExecution.isFastPath() || specializationExecution.isGuardFallback() || specializationExecution.isUncached()) {
                CodeTree implicitState;
                if (specializationExecution.isGuardFallback() || specializationExecution.isUncached()) {
                    implicitState = null;
                } else {
                    implicitState = state.createExtractInteger(frameState, typeGuard);
                }
                checkBuilder.tree(TypeSystemCodeGenerator.implicitCheckFlat(typeSystem, targetType, valueReference, implicitState));
                castBuilder.tree(TypeSystemCodeGenerator.implicitCastFlat(typeSystem, targetType, valueReference, implicitState));
            } else {
                Parameter parameter = parameters.get(0);
                String implicitStateName = createImplicitTypeStateLocalName(parameter);
                CodeTree defaultValue = null;
                prepareBuilder.declaration(context.getType(int.class), implicitStateName, defaultValue);
                CodeTree specializeCall = TypeSystemCodeGenerator.implicitSpecializeFlat(typeSystem, targetType, valueReference);
                checkBuilder.startParantheses();
                checkBuilder.string(implicitStateName, " = ").tree(specializeCall);
                checkBuilder.end();
                checkBuilder.string(" != 0");
                castBuilder.tree(TypeSystemCodeGenerator.implicitCastFlat(typeSystem, targetType, valueReference, CodeTreeBuilder.singleString(implicitStateName)));
            }
        }

        if (castOnly) {
            LocalVariable currentValue = frameState.getValue(execution);
            CodeTreeBuilder localsBuilder = CodeTreeBuilder.createBuilder();
            LocalVariable castVariable = currentValue.nextName().newType(typeGuard.getType()).accessWith(null);
            frameState.setValue(execution, castVariable);
            localsBuilder.tree(castVariable.createDeclaration(castBuilder.build()));
            return new IfTriple(localsBuilder.build(), null, null);
        } else {
            return new IfTriple(prepareBuilder.build(), checkBuilder.build(), null);
        }
    }

    private List<IfTriple> initializeCasts(FrameState frameState, SpecializationGroup group, DSLExpression expression, NodeExecutionMode specializationExecution) {
        Set<VariableElement> boundElements = expression.findBoundVariableElements();
        if (boundElements.isEmpty()) {
            return Collections.emptyList();
        }
        List<IfTriple> triples = new ArrayList<>();
        for (VariableElement variable : boundElements) {
            Parameter p = group.getSpecialization().findByVariable(variable);
            if (p != null) {
                NodeExecutionData execution = p.getSpecification().getExecution();
                if (execution != null) {
                    LocalVariable var = frameState.getValue(execution);
                    if (var == null) {
                        throw new AssertionError();
                    }

                    IfTriple triple = createTypeCheckOrCast(frameState, group, new TypeGuard(p.getType(), execution.getIndex()), specializationExecution, true, false);
                    if (triple != null) {
                        triples.add(triple);
                    }
                }
            }
        }
        return triples;
    }

    private ExecutableTypeData createExecuteAndSpecializeType() {
        SpecializationData polymorphicSpecialization = node.getPolymorphicSpecialization();
        TypeMirror polymorphicType = polymorphicSpecialization.getReturnType().getType();
        List<TypeMirror> parameters = new ArrayList<>();
        for (Parameter param : polymorphicSpecialization.getSignatureParameters()) {
            parameters.add(param.getType());
        }
        return new ExecutableTypeData(node, polymorphicType, createExecuteAndSpecializeName(), node.getFrameType(), parameters);
    }

    private List<TypeMirror> resolveOptimizedImplicitSourceTypes(NodeExecutionData execution, TypeMirror targetType) {
        List<TypeMirror> allSourceTypes = typeSystem.lookupSourceTypes(targetType);
        List<TypeMirror> filteredSourceTypes = new ArrayList<>();
        for (TypeMirror sourceType : allSourceTypes) {

            ExecutableTypeData executableType = resolveTargetExecutable(execution, sourceType);
            if (executableType == null) {
                continue;
            }

            if (!isPrimitive(sourceType) || !boxingEliminationEnabled) {
                // don't optimize non primitives
                continue;
            }

            if (!typeEquals(executableType.getReturnType(), sourceType)) {
                // no boxing optimization possible
                continue;
            }

            filteredSourceTypes.add(sourceType);
        }
        return filteredSourceTypes;
    }

    private ChildExecutionResult createExecuteChildImplicitCast(CodeTreeBuilder parent, FrameState originalFrameState, FrameState frameState, NodeExecutionData execution, LocalVariable target) {
        CodeTreeBuilder builder = parent.create();
        List<TypeMirror> originalSourceTypes = typeSystem.lookupSourceTypes(target.getTypeMirror());
        List<TypeMirror> sourceTypes = resolveOptimizedImplicitSourceTypes(execution, target.getTypeMirror());
        TypeGuard typeGuard = new TypeGuard(target.getTypeMirror(), execution.getIndex());
        boolean throwsUnexpected = false;
        boolean elseIf = false;
        for (TypeMirror sourceType : sourceTypes) {
            ExecutableTypeData executableType = resolveTargetExecutable(execution, sourceType);
            elseIf = builder.startIf(elseIf);
            throwsUnexpected |= executableType.hasUnexpectedValue();
            builder.startGroup();
            builder.tree(state.createContainsOnly(frameState, originalSourceTypes.indexOf(sourceType), 1, new Object[]{typeGuard}, new Object[]{typeGuard}));
            builder.string(" && ");
            builder.tree(state.createIsNotAny(frameState, reachableSpecializationsArray));
            builder.end();
            builder.end();
            builder.startBlock();

            CodeTree value = callChildExecuteMethod(execution, executableType, frameState);
            value = expect(executableType.getReturnType(), sourceType, value);

            throwsUnexpected |= needsCastTo(executableType.getReturnType(), sourceType);
            ImplicitCastData cast = typeSystem.lookupCast(sourceType, target.getTypeMirror());
            if (cast != null) {
                // we need to store the original value to restore it in
                // case of a deopt
                String localName = createSourceTypeLocalName(target, sourceType);
                builder.startStatement().string(localName).string(" = ").tree(value).end();
                value = callMethod(frameState, null, cast.getMethod(), CodeTreeBuilder.singleString(localName));
            }

            builder.startStatement().string(target.getName()).string(" = ").tree(value).end();
            builder.end();
        }

        if (elseIf) {
            builder.startElseBlock();
        }
        LocalVariable genericValue = target.makeGeneric(context).nextName();
        builder.tree(createAssignExecuteChild(originalFrameState, frameState, builder, execution, node.getGenericExecutableType(null), genericValue));
        builder.startStatement().string(target.getName()).string(" = ");
        CodeTree implicitState = state.createExtractInteger(frameState, typeGuard);
        builder.tree(TypeSystemCodeGenerator.implicitExpectFlat(typeSystem, target.getTypeMirror(), genericValue.createReference(), implicitState));
        builder.end();

        if (!sourceTypes.isEmpty()) {
            builder.end();
        }
        return new ChildExecutionResult(builder.build(), throwsUnexpected);
    }

    private static class ChildExecutionResult {

        CodeTree code;
        final boolean throwsUnexpectedResult;

        ChildExecutionResult(CodeTree code, boolean throwsUnexpectedResult) {
            this.code = code;
            this.throwsUnexpectedResult = throwsUnexpectedResult;
        }

    }

    private static class ExecuteDelegationResult {

        public final CodeTree tree;
        public final boolean hasFallthrough;

        ExecuteDelegationResult(CodeTree tree, boolean hasFallthrough) {
            this.tree = tree;
            this.hasFallthrough = hasFallthrough;
        }

    }

    private abstract static class BitSet {

        private final int capacity;
        private final String name;
        private final Map<Object, Integer> offsets = new HashMap<>();
        private final Object[] objects;
        private final ProcessorContext context = ProcessorContext.getInstance();
        private final long allMask;
        private final TypeMirror bitSetType;

        private final boolean needsVolatile;

        BitSet(String name, Object[] objects, boolean needsVolatile) {
            this.name = name;
            this.objects = objects;
            this.needsVolatile = needsVolatile;
            this.capacity = computeStateLength();

            if (capacity <= 32) {
                bitSetType = context.getType(int.class);
            } else if (capacity <= 64) {
                bitSetType = context.getType(long.class);
            } else {
                throw new UnsupportedOperationException("State space too big " + capacity + ". Only <= 64 supported.");
            }
            this.allMask = createMask(objects);
        }

        private int computeStateLength() {
            if (objects.length == 0) {
                return 0;
            }

            int bitIndex = 0;
            for (Object specialization : objects) {
                int specializationSize = calculateRequiredBits(specialization);
                offsets.put(specialization, bitIndex);
                bitIndex += specializationSize;
            }
            return bitIndex;
        }

        public CodeVariableElement declareFields(CodeTypeElement clazz) {
            CodeVariableElement var = clazz.add(createNodeField(PRIVATE, bitSetType, name + "_", context.getTypes().CompilerDirectives_CompilationFinal));
            if (needsVolatile) {
                var.getModifiers().add(Modifier.VOLATILE);
            }
            return var;
        }

        public CodeTree createLoad(FrameState frameState) {
            if (frameState.get(name) != null) {
                // already loaded
                return CodeTreeBuilder.singleString("");
            }
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            String fieldName = name + "_";
            LocalVariable var = new LocalVariable(bitSetType, name, null);
            CodeTreeBuilder init = builder.create();
            init.tree(CodeTreeBuilder.singleString(fieldName));
            builder.tree(var.createDeclaration(init.build()));
            frameState.set(name, var);
            return builder.build();
        }

        public CodeTree createContainsOnly(FrameState frameState, int offset, int length, Object[] selectedElements, Object[] allElements) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            long mask = ~createMask(offset, length, selectedElements) & createMask(allElements);
            builder.tree(createMaskedReference(frameState, mask));
            builder.string(" == 0");
            builder.string(" /* only-active ", toString(selectedElements, " && "), " */");
            return builder.build();
        }

        public CodeTree createIs(FrameState frameState, Object[] selectedElements, Object[] maskedElements) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.tree(createMaskedReference(frameState, maskedElements));
            builder.string(" == ").string(formatMask(createMask(selectedElements)));
            return builder.build();
        }

        private CodeTree createMaskedReference(FrameState frameState, long maskedElements) {
            if (maskedElements == this.allMask) {
                // no masking needed
                return createReference(frameState);
            } else {
                CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
                // masking needed we use the state bitset for guards as well
                builder.string("(").tree(createReference(frameState)).string(" & ").string(formatMask(maskedElements)).string(")");
                return builder.build();
            }
        }

        private CodeTree createMaskedReference(FrameState frameState, Object[] maskedElements) {
            return createMaskedReference(frameState, createMask(maskedElements));
        }

        public CodeTree createIsNotAny(FrameState frameState, Object[] elements) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.tree(createMaskedReference(frameState, elements));
            builder.string(" != 0 ");
            builder.string(" /* is-not ", toString(elements, " && "), " */");
            return builder.build();
        }

        private String formatMask(long mask) {
            int bitsUsed = 64 - Long.numberOfLeadingZeros(mask);
            if (bitsUsed <= 16) {
                return "0b" + Integer.toBinaryString((int) mask);
            } else {
                if (capacity <= 32) {
                    return "0x" + Integer.toHexString((int) mask);
                } else {
                    return "0x" + Long.toHexString(mask) + "L";
                }
            }
        }

        public CodeTree createIsOneBitOf(FrameState frameState, Object[] elements) {
            CodeTree masked = createMaskedReference(frameState, elements);
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

            // use the calculation of power of two
            // (state & (state - 1L)) == 0L
            builder.startParantheses().tree(masked).string(" & ").startParantheses().tree(masked).string(" - 1").end().end().string(" == 0");

            builder.string(" /* ", label("is-single"), " */");
            return builder.build();
        }

        public CodeTree createContains(FrameState frameState, Object[] elements) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.tree(createMaskedReference(frameState, elements));
            builder.string(" != 0");
            builder.string(" /* ", label("is"), toString(elements, " || "), " */");
            return builder.build();
        }

        private String toString(Object[] elements, String elementSep) {
            StringBuilder b = new StringBuilder();
            String sep = "";
            for (int i = 0; i < elements.length; i++) {
                b.append(sep).append(toString(elements[i]));
                sep = elementSep;
            }
            return b.toString();
        }

        protected String toString(Object element) {
            if (element instanceof SpecializationData) {
                SpecializationData specialization = (SpecializationData) element;
                if (specialization.isUninitialized()) {
                    return "uninitialized";
                }
                return createReferenceName(specialization.getMethod());
            } else if (element instanceof TypeGuard) {
                int index = ((TypeGuard) element).getSignatureIndex();
                String simpleName = getSimpleName(((TypeGuard) element).getType());
                return index + ":" + simpleName;
            }
            return element.toString();

        }

        private CodeTree createLocalReference(FrameState frameState) {
            LocalVariable var = frameState != null ? frameState.get(name) : null;
            if (var != null) {
                return var.createReference();
            } else {
                return null;
            }
        }

        private CodeTree createReference(FrameState frameState) {
            CodeTree ref = createLocalReference(frameState);
            if (ref == null) {
                ref = CodeTreeBuilder.createBuilder().string("this.", name, "_").build();
            }
            return ref;
        }

        public CodeTree createNotContains(FrameState frameState, Object[] elements) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.startParantheses();
            builder.tree(createMaskedReference(frameState, elements));
            builder.end();
            builder.string(" == 0");
            builder.string(" /* ", label("is-not"), toString(elements, " && "), " */");
            return builder.build();
        }

        private String label(String message) {
            return message + "-" + getName() + " ";
        }

        protected String getName() {
            if (this instanceof ExcludeBitSet) {
                return "excluded";
            } else {
                return "active";
            }
        }

        public CodeTree createExtractInteger(FrameState frameState, Object element) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            if (capacity > 32) {
                builder.string("(int)(");
            }

            builder.tree(createMaskedReference(frameState, createMask(element)));
            builder.string(" >>> ", Integer.toString(getStateOffset(element)));
            if (capacity > 32) {
                builder.string(")");
            }
            builder.string(" /* ", label("extract-implicit"), toString(element), " */");
            return builder.build();
        }

        public CodeTree createSet(FrameState frameState, Object[] elements, boolean value, boolean persist) {
            CodeTreeBuilder valueBuilder = CodeTreeBuilder.createBuilder();
            valueBuilder.tree(createReference(frameState));
            if (value) {
                valueBuilder.string(" | ");
                valueBuilder.string(formatMask(createMask(elements)));
                valueBuilder.string(" /* ", label("add"), toString(elements, ", "), " */");
            } else {
                valueBuilder.string(" & ");
                valueBuilder.string(formatMask(~createMask(elements)));
                valueBuilder.string(" /* ", label("remove"), toString(elements, ", "), " */");
            }

            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.startStatement();
            if (persist) {
                builder.string("this.", name, "_ = ");

                // if there is a local variable we need to update it as well
                CodeTree localReference = createLocalReference(frameState);
                if (localReference != null) {
                    builder.tree(localReference).string(" = ");
                }
            } else {
                builder.tree(createReference(frameState)).string(" = ");
            }
            builder.tree(valueBuilder.build());
            builder.end(); // statement

            return builder.build();
        }

        public CodeTree createSetInteger(FrameState frameState, Object element, CodeTree value) {
            int offset = getStateOffset(element);
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.startStatement();
            builder.tree(createReference(frameState)).string(" = ");
            builder.startParantheses();
            builder.tree(createReference(frameState));
            builder.string(" | (");
            if (capacity > 32) {
                builder.string("(long) ");
            }
            builder.tree(value).string(" << ", Integer.toString(offset), ")");
            builder.string(" /* ", label("set-implicit"), toString(element), " */");
            builder.end();
            builder.end();
            return builder.build();
        }

        private long createMask(Object e) {
            return createMask(new Object[]{e});
        }

        private long createMask(Object[] e) {
            return createMask(0, -1, e);
        }

        private long createMask(int offset, int length, Object[] e) {
            long mask = 0;
            for (Object element : e) {
                if (!offsets.containsKey(element)) {
                    continue;
                }
                int stateOffset = getStateOffset(element);
                int stateLength = calculateRequiredBits(element);
                int realLength = length < 0 ? stateLength : Math.min(stateLength, offset + length);
                for (int i = offset; i < realLength; i++) {
                    mask |= 1L << (stateOffset + i);
                }
            }
            return mask;
        }

        private int getStateOffset(Object stateSpecialization) {
            Integer value = offsets.get(stateSpecialization);
            if (value == null) {
                return 0;
            }
            return value;
        }

        protected abstract int calculateRequiredBits(Object specialization);

    }

    private class StateBitSet extends BitSet {

        StateBitSet(Object[] objects, boolean needsVolatile) {
            super(STATE_VALUE, objects, needsVolatile);
        }

        @Override
        protected int calculateRequiredBits(Object object) {
            if (object instanceof SpecializationData) {
                SpecializationData specialization = (SpecializationData) object;
                if (specialization.isPolymorphic()) {
                    return 0;
                } else {
                    return 1;
                }
            } else if (object instanceof TypeGuard) {
                TypeGuard guard = (TypeGuard) object;

                TypeMirror type = guard.getType();
                List<TypeMirror> sourceTypes = typeSystem.lookupSourceTypes(type);
                if (sourceTypes.size() > 1) {
                    return sourceTypes.size();
                }
                throw new AssertionError();
            } else if (object instanceof GuardExpression) {
                return 1;
            } else {
                throw new AssertionError();
            }
        }

    }

    private static class ExcludeBitSet extends BitSet {

        ExcludeBitSet(SpecializationData[] specializations, boolean needsVolatile) {
            super("exclude", specializations, needsVolatile);
        }

        @Override
        protected int calculateRequiredBits(Object object) {
            if (object instanceof SpecializationData) {
                SpecializationData specialization = (SpecializationData) object;
                if (specialization.isPolymorphic()) {
                    return 0;
                } else if (specialization.isUninitialized()) {
                    return 0;
                }
                if (!specialization.getExceptions().isEmpty() || !specialization.getExcludedBy().isEmpty()) {
                    return 1;
                }
                return 0;
            }
            throw new IllegalArgumentException();
        }

    }

    private static final class FrameState {

        private final FlatNodeGenFactory factory;
        private final Map<String, LocalVariable> values = new HashMap<>();
        private final Map<String, Boolean> directValues = new HashMap<>();

        private final NodeExecutionMode mode;
        private final CodeExecutableElement method;

        private FrameState(FlatNodeGenFactory factory, NodeExecutionMode mode, CodeExecutableElement method) {
            this.factory = factory;
            this.mode = mode;
            this.method = method;
        }

        private final List<TypeMirror> caughtTypes = new ArrayList<>();

        public void addCaughtException(TypeMirror exceptionType) {
            this.caughtTypes.add(exceptionType);
        }

        public void addThrownExceptions(ExecutableElement calledMethod) {
            TruffleTypes types = ProcessorContext.getInstance().getTypes();
            outer: for (TypeMirror thrownType : calledMethod.getThrownTypes()) {
                if (!ElementUtils.isAssignable(thrownType, ProcessorContext.getInstance().getType(RuntimeException.class))) {
                    if (factory.generatorMode != GeneratorMode.EXPORTED_MESSAGE && ElementUtils.isAssignable(thrownType, types.UnexpectedResultException)) {
                        continue outer;
                    }

                    for (TypeMirror caughtType : caughtTypes) {
                        if (ElementUtils.typeEquals(caughtType, thrownType)) {
                            continue outer;
                        }
                    }

                    boolean found = false;
                    for (TypeMirror foundType : method.getThrownTypes()) {
                        if (ElementUtils.typeEquals(thrownType, foundType)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        method.getThrownTypes().add(thrownType);
                    }
                }
            }
        }

        public NodeExecutionMode getMode() {
            return mode;
        }

        public void setBoolean(String name, boolean value) {
            directValues.put(name, value);
        }

        public boolean getBoolean(String name, boolean defaultValue) {
            Boolean bool = directValues.get(name);
            if (bool == null) {
                return defaultValue;
            } else {
                return bool;
            }
        }

        public static FrameState load(FlatNodeGenFactory factory, ExecutableTypeData type, int varargsThreshold, NodeExecutionMode mode, CodeExecutableElement method) {
            FrameState context = new FrameState(factory, mode, method);
            context.loadEvaluatedValues(type, varargsThreshold);
            return context;
        }

        private void loadEvaluatedValues(ExecutableTypeData executedType, int varargsThreshold) {
            TypeMirror frame = executedType.getFrameParameter();
            if (frame == null) {
                removeValue(FRAME_VALUE);
            } else {
                set(FRAME_VALUE, new LocalVariable(frame, FRAME_VALUE, null));
            }
            for (NodeFieldData field : factory.node.getFields()) {
                String fieldName = fieldValueName(field);
                CodeTree lookupValue;
                if (getMode().isUncached()) {
                    lookupValue = CodeTreeBuilder.createBuilder().defaultValue(field.getType()).build();
                } else {
                    lookupValue = CodeTreeBuilder.createBuilder().string("this.", field.getName()).build();
                }
                values.put(fieldName, new LocalVariable(field.getType(), fieldName, lookupValue));
            }
            boolean varargs = needsVarargs(false, varargsThreshold);
            List<TypeMirror> evaluatedParameter = executedType.getEvaluatedParameters();
            int evaluatedIndex = 0;
            for (int executionIndex = 0; executionIndex < factory.node.getExecutionCount(); executionIndex++) {
                NodeExecutionData execution = factory.node.getChildExecutions().get(executionIndex);
                if (evaluatedIndex < executedType.getEvaluatedCount()) {
                    TypeMirror evaluatedType = evaluatedParameter.get(evaluatedIndex);
                    LocalVariable value = createValue(execution, evaluatedType);
                    if (varargs) {
                        value = value.accessWith(createReadVarargs(evaluatedIndex));
                    }
                    values.put(value.getName(), value.makeOriginal());
                    evaluatedIndex++;
                }
            }
        }

        public static FrameState load(FlatNodeGenFactory factory, NodeExecutionMode mode, CodeExecutableElement method) {
            return load(factory, factory.createExecuteAndSpecializeType(), Integer.MAX_VALUE, mode, method);
        }

        public FrameState copy() {
            FrameState copy = new FrameState(factory, mode, method);
            copy.values.putAll(values);
            copy.caughtTypes.addAll(caughtTypes);
            copy.directValues.putAll(directValues);
            return copy;
        }

        private static String fieldValueName(NodeFieldData field) {
            return field.getName() + "Value";
        }

        @SuppressWarnings("static-method")
        public LocalVariable createValue(NodeExecutionData execution, TypeMirror type) {
            return new LocalVariable(type, valueName(execution), null);
        }

        private static String valueName(NodeExecutionData execution) {
            return execution.getName() + "Value";
        }

        public void set(String id, LocalVariable var) {
            values.put(id, var);
        }

        public void set(NodeExecutionData execution, LocalVariable var) {
            set(valueName(execution), var);
        }

        public LocalVariable get(String id) {
            return values.get(id);
        }

        public LocalVariable getValue(NodeExecutionData execution) {
            return get(valueName(execution));
        }

        public LocalVariable getValue(int signatureIndex) {
            List<NodeExecutionData> childExecutions = factory.node.getChildExecutions();
            if (signatureIndex < childExecutions.size()) {
                return getValue(childExecutions.get(signatureIndex));
            } else {
                return null;
            }
        }

        public void removeValue(String id) {
            values.remove(id);
        }

        public void setValue(NodeExecutionData execution, LocalVariable var) {
            values.put(valueName(execution), var);
        }

        private boolean needsVarargs(boolean requireLoaded, int varArgsThreshold) {
            int size = 0;
            for (NodeExecutionData execution : factory.node.getChildExecutions()) {
                if (requireLoaded && getValue(execution) == null) {
                    continue;
                }
                size++;
            }
            return size >= varArgsThreshold;
        }

        private static CodeTree createReadVarargs(int i) {
            return CodeTreeBuilder.createBuilder().string("args_[").string(String.valueOf(i)).string("]").build();
        }

        public void addReferencesTo(CodeTreeBuilder builder, String... optionalNames) {
            for (String var : optionalNames) {
                LocalVariable local = values.get(var);
                if (local != null) {
                    builder.tree(local.createReference());
                }
            }

            List<NodeExecutionData> executions = factory.node.getChildExecutions();
            for (NodeExecutionData execution : executions) {
                LocalVariable var = getValue(execution);
                if (var != null) {
                    builder.startGroup().tree(var.createReference()).end();
                }
            }
        }

        public void addParametersTo(CodeExecutableElement targetMethod, int varArgsThreshold, String... optionalNames) {
            for (String var : optionalNames) {
                LocalVariable local = values.get(var);
                if (local != null) {
                    targetMethod.addParameter(local.createParameter());
                }
            }
            if (needsVarargs(true, varArgsThreshold)) {
                targetMethod.addParameter(new CodeVariableElement(factory.getType(Object[].class), "args_"));
                targetMethod.setVarArgs(true);
            } else {
                for (NodeExecutionData execution : factory.node.getChildExecutions()) {
                    LocalVariable var = getValue(execution);
                    if (var != null) {
                        targetMethod.addParameter(var.createParameter());
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "LocalContext [values=" + values + "]";
        }

    }

    private static final class LocalVariable {

        private final TypeMirror typeMirror;
        private final CodeTree accessorTree;
        private final String name;

        private LocalVariable(TypeMirror typeMirror, String name, CodeTree accessorTree) {
            Objects.requireNonNull(typeMirror);
            this.typeMirror = typeMirror;
            this.accessorTree = accessorTree;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        private static String createNextName(String name) {
            return name + "_";
        }

        public TypeMirror getTypeMirror() {
            return typeMirror;
        }

        public CodeVariableElement createParameter() {
            return new CodeVariableElement(getTypeMirror(), getName());
        }

        public CodeTree createDeclaration(CodeTree init) {
            return CodeTreeBuilder.createBuilder().declaration(getTypeMirror(), getName(), init).build();
        }

        public CodeTree createReference() {
            if (accessorTree != null) {
                return accessorTree;
            } else {
                return CodeTreeBuilder.singleString(getName());
            }
        }

        public LocalVariable newType(TypeMirror newType) {
            return new LocalVariable(newType, name, accessorTree);
        }

        public LocalVariable accessWith(CodeTree tree) {
            return new LocalVariable(typeMirror, name, tree);
        }

        public LocalVariable nextName() {
            return new LocalVariable(typeMirror, createNextName(name), accessorTree);
        }

        public LocalVariable makeOriginal() {
            return new LocalVariable(typeMirror, name, accessorTree);
        }

        public LocalVariable makeGeneric(ProcessorContext context) {
            return newType(context.getType(Object.class));
        }

        @Override
        public String toString() {
            return "Local[type = " + getTypeMirror() + ", name = " + name + ", accessWith = " + accessorTree + "]";
        }

    }

    private static class BoxingSplit {

        private final SpecializationGroup group;
        private final TypeMirror[] primitiveSignature;

        BoxingSplit(SpecializationGroup group, TypeMirror[] primitiveSignature) {
            this.group = group;
            this.primitiveSignature = primitiveSignature;
        }

        public String getName() {
            StringBuilder b = new StringBuilder();
            String sep = "";
            for (TypeMirror typeMirror : primitiveSignature) {
                b.append(sep).append(firstLetterLowerCase(getSimpleName(typeMirror)));
                sep = "_";
            }
            return b.toString();
        }

    }

    private enum NodeExecutionMode {

        FAST_PATH,
        SLOW_PATH,
        UNCACHED,
        FALLBACK_GUARD;

        public boolean isGuardFallback() {
            return this == FALLBACK_GUARD;
        }

        public boolean isUncached() {
            return this == NodeExecutionMode.UNCACHED;
        }

        public boolean isSlowPath() {
            return this == NodeExecutionMode.SLOW_PATH;
        }

        public final boolean isFastPath() {
            return this == FAST_PATH;
        }

    }

}
