/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.dsl.processor.ProcessorContext.types;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createTransferToInterpreterAndInvalidate;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.boxType;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.executableEquals;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterLowerCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getAnnotationValue;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getTypeSimpleId;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getVisibility;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isAssignable;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isObject;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isPrimitive;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isSubtypeBoxed;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isVoid;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.needsCastTo;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.setVisibility;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEquals;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.uniqueSortedTypes;
import static com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder.singleString;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.oracle.truffle.dsl.processor.TruffleProcessorOptions;
import com.oracle.truffle.dsl.processor.TruffleSuppressedWarnings;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.AbstractDSLExpressionVisitor;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.ClassLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionReducer;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.generator.BitSet.BitRange;
import com.oracle.truffle.dsl.processor.generator.BitStateList.AOTPreparedState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.BitRangedState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.EncodedEnumState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.GuardActive;
import com.oracle.truffle.dsl.processor.generator.BitStateList.ImplicitCastState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.InlinedNodeState;
import com.oracle.truffle.dsl.processor.generator.BitStateList.SpecializationActive;
import com.oracle.truffle.dsl.processor.generator.BitStateList.SpecializationCachesInitialized;
import com.oracle.truffle.dsl.processor.generator.BitStateList.SpecializationExcluded;
import com.oracle.truffle.dsl.processor.generator.BitStateList.State;
import com.oracle.truffle.dsl.processor.generator.MultiBitSet.StateTransaction;
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
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.library.ExportsGenerator;
import com.oracle.truffle.dsl.processor.model.AssumptionExpression;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.CreateCastData;
import com.oracle.truffle.dsl.processor.model.ExecutableTypeData;
import com.oracle.truffle.dsl.processor.model.GuardExpression;
import com.oracle.truffle.dsl.processor.model.ImplicitCastData;
import com.oracle.truffle.dsl.processor.model.InlineFieldData;
import com.oracle.truffle.dsl.processor.model.InlinedNodeData;
import com.oracle.truffle.dsl.processor.model.NodeChildData;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.NodeFieldData;
import com.oracle.truffle.dsl.processor.model.Parameter;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.SpecializationThrowsData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.parser.NodeParser;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

public class FlatNodeGenFactory {

    /*
     * Specifies after how many bits a new bit set field is created in the state multi-set. Must be
     * > 0 and <= 64. By default we use 32 as we want to always use the same type for all state bit
     * fields. This will make it easier to implement inlined profiles. (see GR-27647)
     *
     * If this field is updated also update StateBitTest#DEFAULT_MAX_BIT_WIDTH.
     */
    public static final int DEFAULT_MAX_BIT_WIDTH = 32;

    private static final String FRAME_VALUE = TemplateMethod.FRAME_NAME;
    private static final String NAME_SUFFIX = "_";

    public static final int INLINED_NODE_INDEX = 0;
    private static final String VARARGS_NAME = "args";

    static final StateQuery AOT_PREPARED = StateQuery.create(AOTPreparedState.class, "aot-prepared");

    private final ProcessorContext context;
    private final TruffleTypes types = ProcessorContext.getInstance().getTypes();
    private final NodeData node;
    private final TypeSystemData typeSystem;
    private final Set<TypeMirror> expectedTypes = new HashSet<>();
    private final Collection<NodeData> sharingNodes;

    private final boolean boxingEliminationEnabled;
    private int boxingSplitIndex = 0;

    private final MultiStateBitSet multiState; // only active node
    private final MultiStateBitSet allMultiState; // all nodes

    private final ExecutableTypeData executeAndSpecializeType;
    private boolean fallbackNeedsState = false;
    private boolean fallbackNeedsFrame = false;

    private final Map<SpecializationData, CodeTypeElement> specializationClasses = new LinkedHashMap<>();
    private final boolean primaryNode;
    private final Map<CacheExpression, String> sharedCaches;
    private final Map<CacheExpression, CacheExpression> sharedCacheKey;
    private final ParentInlineData parentInlineAccess;
    private final Map<ExecutableElement, Function<Call, DSLExpression>> substitutions = new LinkedHashMap<>();
    private final StaticConstants constants;
    private NodeConstants nodeConstants;
    private final NodeGeneratorPlugs plugs;

    private final GeneratorMode generatorMode;
    private final NodeStateResult state;

    public enum GeneratorMode {
        DEFAULT,
        EXPORTED_MESSAGE
    }

    public FlatNodeGenFactory(ProcessorContext context, GeneratorMode mode, NodeData node,
                    StaticConstants constants, NodeConstants nodeConstants, NodeGeneratorPlugs plugs) {
        this(context, mode, node, Arrays.asList(node), node.getSharedCaches(), constants, nodeConstants, plugs);
    }

    @SuppressWarnings("this-escape")
    public FlatNodeGenFactory(ProcessorContext context, GeneratorMode mode, NodeData node,
                    Collection<NodeData> stateSharingNodes,
                    Map<CacheExpression, String> sharedCaches,
                    StaticConstants constants,
                    NodeConstants nodeConstants,
                    NodeGeneratorPlugs plugs) {
        Objects.requireNonNull(node);
        this.plugs = plugs;
        this.generatorMode = mode;
        this.context = context;
        this.sharingNodes = stateSharingNodes;
        this.node = node;
        this.typeSystem = node.getTypeSystem();
        this.boxingEliminationEnabled = !TruffleProcessorOptions.generateSlowPathOnly(context.getEnvironment());
        this.primaryNode = stateSharingNodes.iterator().next() == node;
        this.sharedCaches = sharedCaches;
        this.sharedCacheKey = computeSharedCacheKeys(stateSharingNodes, sharedCaches);
        this.parentInlineAccess = computeParentInlineAccess();
        this.state = createNodeState();
        this.multiState = state.activeState;
        this.allMultiState = state.allState;
        this.executeAndSpecializeType = createExecuteAndSpecializeType();

        this.constants = constants;
        this.nodeConstants = nodeConstants;
        this.substitutions.put(ElementUtils.findExecutableElement(types.LibraryFactory, "resolve"),
                        (binary) -> substituteLibraryCall(binary));
        this.substitutions.put(ElementUtils.findExecutableElement(types.TruffleLanguage_ContextReference, "create"),
                        (binary) -> substituteContextReference(binary));
        this.substitutions.put(ElementUtils.findExecutableElement(types.TruffleLanguage_LanguageReference, "create"),
                        (binary) -> substituteLanguageReference(binary));

    }

    private static final class NodeStateResult {

        final MultiStateBitSet activeState;
        final MultiStateBitSet allState;

        NodeStateResult(MultiStateBitSet state, MultiStateBitSet allState) {
            this.activeState = state;
            this.allState = allState;
        }
    }

    public static List<InlineFieldData> createInlinedFields(NodeData node) {
        FlatNodeGenFactory factory = new FlatNodeGenFactory(ProcessorContext.getInstance(), GeneratorMode.DEFAULT, node, new StaticConstants(), new NodeConstants(), NodeGeneratorPlugs.DEFAULT);
        return factory.createInlineFields(true);
    }

    private List<InlineFieldData> createInlineFields(boolean pruneInternalClasses) {
        List<InlineFieldData> fields = new ArrayList<>();

        for (BitSet bitSet : state.activeState.getSets()) {
            String name = bitSet.getName() + "_";
            TypeMirror referenceType = types().InlineSupport_StateField;
            CodeVariableElement var = MultiStateBitSet.createCachedField(bitSet);
            fields.add(new InlineFieldData(var, name, referenceType, bitSet.getBitCount(), null, 0));
        }

        /*
         * createInlineFieldSignature should not generate nodeConstants so we discard everything
         * generated there.
         */
        NodeConstants savedConstants = this.nodeConstants;
        this.nodeConstants = new NodeConstants();

        List<Element> elements = createCachedFields(null);
        for (Element element : elements) {
            if (!(element instanceof CodeVariableElement)) {
                continue;
            }
            CodeVariableElement var = (CodeVariableElement) element;
            if (var.getModifiers().contains(STATIC)) {
                // for inlining we only care about instance fields
                continue;
            }
            TypeMirror type = var.asType();
            String name = var.getName();
            if (ElementUtils.isPrimitive(type)) {
                fields.add(new InlineFieldData(element, name, InlineFieldData.resolvePrimitiveFieldType(type), null, type, 0));
            } else {
                int dimensions = 0;
                if (pruneInternalClasses) {
                    if (ElementUtils.isAssignable(type, types.Node)) {
                        type = types.Node;
                    } else if (ElementUtils.isAssignable(type, types.NodeInterface)) {
                        type = types.NodeInterface;
                    } else if (isNodeArray(type)) {
                        type = new ArrayCodeTypeMirror(types.Node);
                    } else if (type.getKind() == TypeKind.ARRAY) {
                        type = context.getType(Object[].class);
                        AnnotationMirror annotationMirror = ElementUtils.findAnnotationMirror(var, types.CompilerDirectives_CompilationFinal);
                        if (annotationMirror != null) {
                            dimensions = ElementUtils.getAnnotationValue(Integer.class, annotationMirror, "dimensions");
                        }
                    } else {
                        type = context.getType(Object.class);
                    }
                }

                fields.add(new InlineFieldData(element, name, types().InlineSupport_ReferenceField, null, type, dimensions));
            }
        }

        this.nodeConstants = savedConstants;
        return fields;

    }

    private static boolean isImplicitCastUsed(ExecutableTypeData executable, Collection<SpecializationData> usedSpecializations, TypeGuard guard) {
        int signatureIndex = guard.getSignatureIndex();
        TypeMirror polymorphicParameter = executable.getSignatureParameters().get(signatureIndex);
        for (SpecializationData specialization : usedSpecializations) {
            TypeMirror specializationType = specialization.getSignatureParameters().get(signatureIndex).getType();
            if (ElementUtils.needsCastTo(polymorphicParameter, specializationType)) {
                return true;
            }
        }
        return false;
    }

    BitStateList computeNodeState() {
        List<State<?>> stateObjects = new ArrayList<>();
        boolean aotStateAdded = false;

        Set<String> handledCaches = new HashSet<>();
        for (NodeData stateNode : sharingNodes) {
            Set<TypeGuard> implicitCasts = new LinkedHashSet<>();
            boolean needSpecialize = stateNode.needsSpecialize();

            List<SpecializationData> specializations = stateNode.getReachableSpecializations();
            for (SpecializationData specialization : specializations) {
                if (!aotStateAdded && needsAOTReset(node, sharingNodes)) {
                    stateObjects.add(new AOTPreparedState(node));
                    aotStateAdded = true;
                }

                if (needSpecialize) {
                    stateObjects.add(new SpecializationActive(specialization));
                }

                if (hasExcludeBit(specialization)) {
                    stateObjects.add(new SpecializationExcluded(specialization));
                }

                for (GuardExpression guard : specialization.getGuards()) {
                    if (guardNeedsNodeStateBit(specialization, guard)) {
                        stateObjects.add(new GuardActive(specialization, guard));
                    }
                }

                boolean useSpecializationClass = useSpecializationClass(specialization);
                for (CacheExpression cache : specialization.getCaches()) {
                    if (useSpecializationClass && canCacheBeStoredInSpecialializationClass(cache)) {
                        continue;
                    }
                    if (!cache.isEncodedEnum()) {
                        continue;
                    }
                    String sharedGroup = cache.getSharedGroup();
                    if (sharedGroup == null || !handledCaches.contains(sharedGroup)) {
                        stateObjects.add(new BitStateList.EncodedEnumState(node, cache));
                        if (sharedGroup != null) {
                            handledCaches.add(sharedGroup);
                        }
                    }
                }

                int index = 0;
                for (Parameter p : specialization.getSignatureParameters()) {
                    TypeMirror targetType = p.getType();
                    Collection<TypeMirror> sourceTypes = stateNode.getTypeSystem().lookupSourceTypes(targetType);
                    if (sourceTypes.size() > 1) {
                        implicitCasts.add(new TypeGuard(stateNode.getTypeSystem(), targetType, index));
                    }
                    index++;
                }
            }
            for (TypeGuard cast : implicitCasts) {
                if (isImplicitCastUsed(stateNode.getPolymorphicExecutable(), specializations, cast)) {
                    stateObjects.add(new ImplicitCastState(stateNode, cast));
                }
            }
        }

        for (NodeData stateNode : sharingNodes) {
            for (SpecializationData specialization : stateNode.getReachableSpecializations()) {
                boolean useSpecializationClass = useSpecializationClass(specialization);
                BitStateList specializationState = computeSpecializationState(specialization);
                for (CacheExpression cache : specialization.getCaches()) {
                    InlinedNodeData inline = cache.getInlinedNode();
                    if (inline == null) {
                        continue;
                    }

                    String cacheGroup = cache.getSharedGroup();
                    if (cacheGroup != null) {
                        if (handledCaches.contains(cacheGroup)) {
                            continue;
                        }
                        handledCaches.add(cacheGroup);
                    }

                    if (cacheGroup == null && useSpecializationClass) {
                        // state is handled in computeSpecializationState
                        for (InlineFieldData fieldData : cache.getInlinedNode().getFields()) {
                            if (fieldData.isState()) {
                                if (!specializationState.contains(InlinedNodeState.class, fieldData)) {
                                    throw new AssertionError("Detected unhandled state");
                                }
                            }
                        }
                        continue;
                    }

                    SpecializationData excludeSpecialization = null;
                    if (cache.isUsedInGuard()) {
                        /*
                         * Inlined caches that are bound in guards must not be in the same state
                         * bitset as the dependent specialization bits. At the end of slow-path
                         * specialization we set the state bits of the specialization. If an inlined
                         * node in the guard changes the state bits we would override when we set
                         * the specialization bits. Alternatively we could re-read the state bit-set
                         * before we specialize in such case after the bound guards were executed,
                         * but that is very hard to get right in a thread-safe manner.
                         */
                        excludeSpecialization = specialization;
                    }

                    for (InlineFieldData fieldData : cache.getInlinedNode().getFields()) {
                        if (fieldData.isState()) {
                            stateObjects.add(new InlinedNodeState(stateNode, cache, fieldData, excludeSpecialization));
                        }
                    }
                }
            }
        }

        return new BitStateList(stateObjects);
    }

    private static BitStateList computeSpecializationState(SpecializationData specialization) {
        List<State<?>> stateObjects = new ArrayList<>();
        if (useSpecializationClass(specialization)) {

            for (GuardExpression guard : specialization.getGuards()) {
                if (guardNeedsSpecializationStateBit(specialization, guard)) {
                    stateObjects.add(new GuardActive(specialization, guard));
                }
            }

            if (specializationNeedsInitializedBit(specialization)) {
                stateObjects.add(new SpecializationCachesInitialized(specialization));
            }

            for (CacheExpression cache : specialization.getCaches()) {
                if (!canCacheBeStoredInSpecialializationClass(cache)) {
                    continue;
                }

                if (cache.getInlinedNode() != null) {
                    for (InlineFieldData field : cache.getInlinedNode().getFields()) {
                        if (field.isState()) {
                            stateObjects.add(new InlinedNodeState(specialization.getNode(), cache, field, null));
                        }
                    }
                } else if (cache.isEncodedEnum()) {
                    stateObjects.add(new EncodedEnumState(specialization.getNode(), cache));
                }
            }
        }
        return new BitStateList(stateObjects);
    }

    @SuppressWarnings("hiding")
    NodeStateResult createNodeState() {
        BitStateList list = computeNodeState();
        MultiStateBitSet state = createMultiStateBitset("", node, list);
        MultiStateBitSet allState = new MultiStateBitSet(state.all, state.all);
        return new NodeStateResult(state, allState);
    }

    private static MultiStateBitSet createMultiStateBitset(String namePrefix, NodeData activeNode, BitStateList objects) {
        int maxBits = TruffleProcessorOptions.stateBitWidth(activeNode);
        return objects.splitBitSets(namePrefix, activeNode, maxBits);
    }

    private static boolean needsAOTReset(NodeData node, Collection<NodeData> stateSharingNodes) {
        if (!node.isGenerateAOT()) {
            return false;
        }
        for (NodeData currentNode : stateSharingNodes) {
            if (currentNode.needsState()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMultipleNodes() {
        return sharingNodes.size() > 1;
    }

    private String createSpecializationTypeName(SpecializationData s) {
        if (sharingNodes.size() > 1) {
            return firstLetterUpperCase(getNodePrefix(s.getNode())) + firstLetterUpperCase(s.getId()) + "Data";
        } else {
            return firstLetterUpperCase(s.getId()) + "Data";
        }
    }

    private static TypeMirror createCacheClassType(CacheExpression cache) {
        return new GeneratedTypeMirror("", createCacheClassName(cache));
    }

    private static String createCacheClassName(CacheExpression cache) {
        return firstLetterUpperCase(cache.getSharedGroup()) + "SharedWrapper";
    }

    private String createSpecializationFieldName(SpecializationData s) {
        if (sharingNodes.size() > 1) {
            return firstLetterLowerCase(getNodePrefix(s.getNode())) + "_" + firstLetterLowerCase(s.getId()) + "_cache";
        } else {
            return firstLetterLowerCase(s.getId()) + "_cache";
        }
    }

    private String createStaticInlinedCacheName(SpecializationData specialization, CacheExpression cache) {
        String baseName;
        String sharedName = sharedCaches.get(cache);
        if (sharedName != null && specialization != null && hasCacheParentAccess(cache)) {
            baseName = specialization.getId() + "_" + sharedName;
        } else {
            baseName = createFieldName(specialization, cache);
        }
        return "INLINED_" + ElementUtils.createConstantName(baseName);
    }

    private String createFieldName(SpecializationData specialization, CacheExpression cache) {
        String sharedName = sharedCaches.get(cache);
        if (sharedName != null) {
            return sharedName;
        }

        if (specialization == null) {
            throw new AssertionError("if specialization is null it must be shared cache: " + specialization + " " + cache + " " + sharedCaches);
        }

        Parameter parameter = cache.getParameter();
        if (useSpecializationClass(specialization) && cache.getInlinedNode() == null) {
            return parameter.getLocalName() + "_";
        } else {
            String prefix = "";
            if (sharingNodes.size() > 1) {
                prefix = firstLetterLowerCase(getNodePrefix(specialization.getNode())) + "_" + firstLetterLowerCase(specialization.getId()) + "_";
            } else if (specialization.getNode().getReachableSpecializations().size() > 1) {
                prefix = firstLetterLowerCase(specialization.getId()) + "_";
            }
            return prefix + parameter.getLocalName() + "_";
        }
    }

    private static String getNodePrefix(NodeData node) {
        String name = node.getNodeId();
        if (name.endsWith("Node")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static String createAssumptionFieldName(SpecializationData specialization, AssumptionExpression assumption) {
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

    private static String createSpecializationLocalOriginalName(SpecializationData s) {
        if (s == null) {
            return null;
        }
        return "s" + s.getIndex() + "_original";
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

    private CacheExpression lookupSharedCacheKey(CacheExpression cache) {
        if (!sharedCaches.containsKey(cache)) {
            return cache;
        }
        CacheExpression key = sharedCacheKey.get(cache);
        if (key == null) {
            return cache;
        }
        return key;
    }

    private static Map<CacheExpression, CacheExpression> computeSharedCacheKeys(Collection<NodeData> stateSharingNodes, Map<CacheExpression, String> sharedCaches) {
        Map<CacheExpression, CacheExpression> foundCaches = new LinkedHashMap<>();
        Map<String, CacheExpression> firstCache = new LinkedHashMap<>();
        for (NodeData n : stateSharingNodes) {
            for (SpecializationData specialization : n.getReachableSpecializations()) {
                // shared caches not supported with multiple instances at the moment
                for (CacheExpression cache : specialization.getCaches()) {
                    String cacheName = sharedCaches.get(cache);
                    if (cacheName == null) {
                        continue;
                    }
                    CacheExpression key = firstCache.get(cacheName);
                    if (key != null) {
                        foundCaches.put(cache, key);
                    } else {
                        firstCache.put(cacheName, cache);
                        foundCaches.put(cache, cache);
                    }
                }
            }
        }
        return foundCaches;
    }

    private boolean hasCacheParentAccess(CacheExpression cache) {
        return parentInlineAccess.foundSharedParentAccess.contains(cache);
    }

    private boolean hasSharedCacheDirectAccess(CacheExpression cache) {
        return parentInlineAccess.foundSharedDirectAccess.contains(cache);
    }

    private static final class ParentInlineData {

        final Set<CacheExpression> foundSharedParentAccess = new LinkedHashSet<>();
        final Set<CacheExpression> foundSharedDirectAccess = new LinkedHashSet<>();

    }

    private ParentInlineData computeParentInlineAccess() {
        ParentInlineData data = new ParentInlineData();
        for (NodeData n : this.sharingNodes) {
            for (SpecializationData specialization : n.getReachableSpecializations()) {
                // shared caches are not supported with multiple instances at the moment
                boolean parentInlinedAccess = useParentInlinedAccess(specialization);
                for (CacheExpression cache : specialization.getCaches()) {
                    if (sharedCaches.containsKey(cache) && cache.getInlinedNode() != null) {
                        if (parentInlinedAccess) {
                            data.foundSharedParentAccess.add(cache);
                        } else {
                            data.foundSharedDirectAccess.add(lookupSharedCacheKey(cache));
                        }
                    }
                }
            }
        }
        return data;
    }

    /**
     * This is needed if a specialization contains both shared and non-shared inlined elements and
     * at the same time requires a specialization class. So in order to pass in a single node into
     * the specialization to access inlined nodes, the shared inlined nodes must use a special field
     * with a configured parent class.
     */
    private static boolean useParentInlinedAccess(SpecializationData specialization) {
        if (!useSpecializationClass(specialization)) {
            return false;
        }
        boolean hasSharedInlined = false;
        boolean hasSpecializationClassInlined = false;
        for (CacheExpression cache : specialization.getCaches()) {
            if (cache.getInlinedNode() != null) {
                if (canCacheBeStoredInSpecialializationClass(cache)) {
                    hasSpecializationClassInlined = true;
                } else if (cache.getSharedGroup() != null) {
                    hasSharedInlined = true;
                }
            }
            if (hasSharedInlined && hasSpecializationClassInlined) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLayoutBenefittingFromNeverDefault(SpecializationData specialization) {
        if (specialization.hasMultipleInstances()) {
            return false;
        }
        if (specialization.isGuardBindsExclusiveCache() && FlatNodeGenFactory.usesExclusiveInstanceField(specialization)) {
            return false;
        }
        return !FlatNodeGenFactory.shouldUseSpecializationClassBySize(specialization);
    }

    /* Whether a new class should be generated for specialization instance fields. */
    public static boolean useSpecializationClass(SpecializationData specialization) {
        if (shouldUseSpecializationClassBySize(specialization)) {
            return true;
        } else {
            if (specialization.hasMultipleInstances()) {
                // we need a data class if we need to support multiple specialization instances
                // we need a place to store the next pointer.
                return true;
            }

            if (specialization.isGuardBindsExclusiveCache() && usesExclusiveInstanceField(specialization)) {
                /*
                 * For specializations that bind cached values in guards that use instance fields we
                 * need to use specialization classes because the duplication check is not reliable
                 * otherwise. E.g. NeverDefaultTest#testSingleInstancePrimitiveCacheNode fails
                 * without this check.
                 */
                return true;
            }

            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.isEncodedEnum()) {
                    continue;
                }
                if (!canCacheBeStoredInSpecialializationClass(cache)) {
                    continue;
                }
                if (!cache.isNeverDefault()) {
                    /*
                     * If we do not know whether a cache initializer is non-default we must use a
                     * specialization class because otherwise we cannot guarantee a thread-safe
                     * initialization.
                     */
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean usesExclusiveInstanceField(SpecializationData s) {
        for (CacheExpression cache : s.getCaches()) {
            if (usesExclusiveInstanceField(cache)) {
                return true;
            }
        }
        return false;

    }

    public static boolean shouldUseSpecializationClassBySize(SpecializationData specialization) {
        SpecializationClassSizeEstimate result = computeSpecializationClassSizeEstimate(specialization);
        return result.sizeWithClass < result.sizeWithoutClass;
    }

    private static SpecializationClassSizeEstimate computeSpecializationClassSizeEstimate(SpecializationData specialization) {
        boolean needsNode = specializationClassIsNode(specialization);
        int fieldsSize = 0;
        int stateBits = 0;
        for (CacheExpression cache : specialization.getCaches()) {
            if (!canCacheBeStoredInSpecialializationClass(cache)) {
                continue;
            }
            if (cache.getInlinedNode() != null) {
                for (InlineFieldData field : cache.getInlinedNode().getFields()) {
                    if (field.isState()) {
                        stateBits += field.getBits();
                    } else {
                        fieldsSize += ElementUtils.getCompressedReferenceSize(field.getType());
                    }
                }
            } else {
                TypeMirror type = cache.getParameter().getType();
                fieldsSize += ElementUtils.getCompressedReferenceSize(type);
            }
        }
        // for a specialization class we always need to create all the state ints, even if they are
        // unused
        int stateBytesByInt = (int) Math.ceil((double) stateBits / 32) * 4;
        // without a specialization class we can compress somewhat so we use byte granularity.
        int stateBytesByByte = (int) Math.ceil((double) stateBits / 8);

        int instanceOverhead = ElementUtils.COMPRESSED_HEADER_SIZE;
        if (needsNode) {
            instanceOverhead += ElementUtils.COMPRESSED_POINTER_SIZE; // parent pointer
        }
        if (specialization.hasMultipleInstances()) {
            instanceOverhead += ElementUtils.COMPRESSED_POINTER_SIZE; // next pointer;
        }
        int sizeWithClass = ElementUtils.COMPRESSED_POINTER_SIZE + (int) ((instanceOverhead + fieldsSize + stateBytesByInt) * specialization.getActivationProbability());
        int sizeWithoutClass = fieldsSize + stateBytesByByte;

        return new SpecializationClassSizeEstimate(sizeWithClass, sizeWithoutClass);
    }

    static class SpecializationClassSizeEstimate {

        final int sizeWithClass;
        final int sizeWithoutClass;

        SpecializationClassSizeEstimate(int sizeWithClass, int sizeWithoutClass) {
            this.sizeWithClass = sizeWithClass;
            this.sizeWithoutClass = sizeWithoutClass;
        }

    }

    static boolean canCacheBeStoredInSpecialializationClass(CacheExpression cache) {
        if (cache.isBind()) {
            return false;
        } else if (cache.isAlwaysInitialized()) {
            return false;
        } else if (cache.getSharedGroup() != null) {
            return false;
        } else if (cache.isEagerInitialize()) {
            return false;
        } else {
            return true;
        }
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
        String name = firstLetterLowerCase(getTypeSimpleId(execution.getType()));
        return name + "Cast" + execution.getSpecification().getExecution().getIndex();
    }

    private static boolean hasExcludeBit(SpecializationData specialization) {
        return !specialization.getExceptions().isEmpty();
    }

    private static boolean hasExcludes(SpecializationData specialization) {
        return !specialization.getExceptions().isEmpty() || !specialization.getReplacedBy().isEmpty();
    }

    public CodeTypeElement create(CodeTypeElement clazz) {
        TypeMirror genericReturnType = node.getPolymorphicExecutable().getReturnType();

        List<ExecutableTypeData> executableTypes = filterExecutableTypes(node.getExecutableTypes(), node.getReachableSpecializations());
        List<ExecutableTypeData> genericExecutableTypes = new ArrayList<>();
        List<ExecutableTypeData> specializedExecutableTypes = new ArrayList<>();
        List<ExecutableTypeData> voidExecutableTypes = new ArrayList<>();

        GeneratorUtils.mergeSuppressWarnings(clazz, "javadoc");

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

        if (node.isGenerateCached()) {
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

            if (primaryNode) {
                if (state.activeState.getAllCapacity() > 0) {
                    clazz.getEnclosedElements().addAll(state.activeState.createCachedFields());
                }
            }

            clazz.getEnclosedElements().addAll(createCachedFields(clazz));
            generateStatisticsFields(clazz);

            SpecializationData fallback = node.getFallbackSpecialization();
            if (fallback.getMethod() != null && fallback.isReachable()) {
                clazz.add(createFallbackGuard(false));
            }

            for (ExecutableTypeData type : genericExecutableTypes) {
                wrapWithTraceOnReturn(createExecute(clazz, type, Collections.<ExecutableTypeData> emptyList(), false));
            }

            for (ExecutableTypeData type : specializedExecutableTypes) {
                wrapWithTraceOnReturn(createExecute(clazz, type, genericExecutableTypes, false));
            }

            for (ExecutableTypeData type : voidExecutableTypes) {
                List<ExecutableTypeData> genericAndSpecialized = new ArrayList<>();
                genericAndSpecialized.addAll(genericExecutableTypes);
                genericAndSpecialized.addAll(specializedExecutableTypes);
                wrapWithTraceOnReturn(createExecute(clazz, type, genericAndSpecialized, false));
            }

            clazz.addOptional(createExecuteAndSpecialize(false));
            final ReportPolymorphismAction reportPolymorphismAction = reportPolymorphismAction(node, node.getReachableSpecializations());
            if (reportPolymorphismAction.required()) {
                clazz.addOptional(createCheckForPolymorphicSpecialize(reportPolymorphismAction, false));
                if (requiresCacheCheck(reportPolymorphismAction)) {
                    clazz.addOptional(createCountCaches(false));
                }
            }

            for (TypeMirror type : uniqueSortedTypes(expectedTypes, false)) {
                if (!typeSystem.hasType(type)) {
                    clazz.addOptional(TypeSystemCodeGenerator.createExpectMethod(PRIVATE, typeSystem,
                                    context.getType(Object.class), type));
                }
            }

            clazz.getEnclosedElements().addAll(removeThisMethods.values());

            if (isGenerateIntrospection()) {
                generateIntrospectionInfo(clazz, false);
            }

            if (isGenerateAOT()) {
                generateAOT(clazz, false);
            }
        }

        removeThisMethods.clear();

        if (node.isGenerateInline()) {
            CodeTypeElement inlined = GeneratorUtils.createClass(node, null, modifiers(PRIVATE, STATIC, FINAL), "Inlined", node.getTemplateType().asType());

            inlined.addAnnotationMirror(new CodeAnnotationMirror(types.DenyReplace));

            List<InlineFieldData> inlinedFields = createInlineFields(false);
            CodeExecutableElement constructor = inlined.add(GeneratorUtils.createConstructorUsingFields(modifiers(PRIVATE), inlined));
            CodeTreeBuilder builder = constructor.appendBuilder();

            CodeVariableElement inlineTarget = new CodeVariableElement(types.InlineSupport_InlineTarget, "target");
            constructor.addParameter(inlineTarget);
            builder.startAssert().string("target.getTargetClass().isAssignableFrom(").typeLiteral(node.getTemplateType().asType()).string(")").end();

            if (primaryNode) {
                int index = 0;
                for (InlineFieldData inlinedField : inlinedFields) {
                    CodeVariableElement field = inlined.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), inlinedField.getFieldType(), inlinedField.getName()));

                    if (index < state.activeState.getSets().size()) {
                        CodeTreeBuilder docBuilder = field.createDocBuilder();
                        docBuilder.startJavadoc();
                        FlatNodeGenFactory.addStateDoc(docBuilder, state.activeState.getSets().get(index));
                        docBuilder.end();
                    }

                    builder.startStatement();
                    builder.string("this.", field.getName(), " = ");
                    if (inlinedField.isReference()) {
                        GeneratorUtils.mergeSuppressWarnings(constructor, "unchecked");
                        builder.startCall(inlineTarget.getName(), "getReference").string(String.valueOf(index)).typeLiteral(inlinedField.getType()).end();
                    } else if (inlinedField.isState()) {
                        builder.startCall(inlineTarget.getName(), "getState").string(String.valueOf(index)).string(String.valueOf(inlinedField.getBits())).end();
                    } else {
                        builder.startCall(inlineTarget.getName(), "getPrimitive").string(String.valueOf(index)).typeLiteral(inlinedField.getFieldType()).end();
                    }
                    builder.end();
                    index++;
                }

                Set<String> expressions = new HashSet<>();
                for (Entry<CacheExpression, String> entry : sharedCaches.entrySet()) {
                    CacheExpression cache = entry.getKey();
                    String fieldName = entry.getValue();
                    if (expressions.contains(fieldName)) {
                        continue;
                    }
                    expressions.add(fieldName);

                    if (cache.getInlinedNode() == null) {
                        continue;
                    }

                    if (!hasSharedCacheDirectAccess(cache)) {
                        continue;
                    }

                    inlined.addOptional(createCacheInlinedField(builder, null, null, cache));
                }
            }

            SpecializationData fallback = node.getFallbackSpecialization();
            if (fallback.getMethod() != null && fallback.isReachable()) {
                inlined.add(createFallbackGuard(true));
            }

            for (SpecializationData specialization : node.getReachableSpecializations()) {
                MultiStateBitSet specializationState = lookupSpecializationState(specialization);

                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.getInlinedNode() == null) {
                        continue;
                    }

                    if (sharedCaches.containsKey(cache) && !hasCacheParentAccess(cache)) {
                        // already generated
                        continue;
                    }

                    inlined.addOptional(createCacheInlinedField(builder, specialization, specializationState, cache));
                }
            }

            for (ExecutableTypeData type : genericExecutableTypes) {
                wrapWithTraceOnReturn(createExecute(inlined, type, Collections.<ExecutableTypeData> emptyList(), true));
            }

            for (ExecutableTypeData type : specializedExecutableTypes) {
                wrapWithTraceOnReturn(createExecute(inlined, type, genericExecutableTypes, true));
            }

            for (ExecutableTypeData type : voidExecutableTypes) {
                List<ExecutableTypeData> genericAndSpecialized = new ArrayList<>();
                genericAndSpecialized.addAll(genericExecutableTypes);
                genericAndSpecialized.addAll(specializedExecutableTypes);
                wrapWithTraceOnReturn(createExecute(inlined, type, genericAndSpecialized, true));
            }

            inlined.addOptional(createExecuteAndSpecialize(true));

            final ReportPolymorphismAction reportPolymorphismAction = reportPolymorphismAction(node, node.getReachableSpecializations());
            if (reportPolymorphismAction.required()) {
                inlined.addOptional(createCheckForPolymorphicSpecialize(reportPolymorphismAction, true));
                if (requiresCacheCheck(reportPolymorphismAction)) {
                    inlined.addOptional(createCountCaches(true));
                }
            }

            inlined.getEnclosedElements().addAll(removeThisMethods.values());

            inlined.getImplements().add(types.UnadoptableNode);

            if (isGenerateIntrospection()) {
                generateIntrospectionInfo(inlined, true);
            }

            if (isGenerateAOT()) {
                generateAOT(inlined, true);
            }

            if (!node.isGenerateCached()) {
                // if no cached node is generated we need to generate the expect methods
                // in the inlined node.
                for (TypeMirror type : uniqueSortedTypes(expectedTypes, false)) {
                    if (!typeSystem.hasType(type)) {
                        clazz.addOptional(TypeSystemCodeGenerator.createExpectMethod(PRIVATE, typeSystem,
                                        context.getType(Object.class), type));
                    }
                }
            }

            generateStatisticsFields(inlined);

            clazz.add(inlined);
        }

        for (CodeTypeElement specializationClass : specializationClasses.values()) {
            clazz.add(specializationClass);
        }

        if (node.isUncachable() && node.isGenerateUncached()) {
            CodeTypeElement uncached = GeneratorUtils.createClass(node, null, modifiers(PRIVATE, STATIC, FINAL), "Uncached", node.getTemplateType().asType());
            uncached.getEnclosedElements().addAll(createUncachedFields());
            uncached.addAnnotationMirror(new CodeAnnotationMirror(types.DenyReplace));

            for (NodeFieldData field : node.getFields()) {
                if (!field.isGenerated()) {
                    continue;
                }
                if (field.getGetter() != null && field.getGetter().getModifiers().contains(Modifier.ABSTRACT)) {
                    CodeExecutableElement method = CodeExecutableElement.clone(field.getGetter());
                    method.getModifiers().remove(Modifier.ABSTRACT);
                    method.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
                    method.createBuilder().startThrow().startNew(context.getType(UnsupportedOperationException.class)).end().end();
                    uncached.add(method);
                }
                if (field.isSettable()) {
                    CodeExecutableElement method = CodeExecutableElement.clone(field.getSetter());
                    method.getModifiers().remove(Modifier.ABSTRACT);
                    method.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
                    method.createBuilder().startThrow().startNew(context.getType(UnsupportedOperationException.class)).end().end();
                    uncached.add(method);
                }
            }
            generateStatisticsFields(uncached);

            for (NodeChildData child : node.getChildren()) {
                uncached.addOptional(createAccessChildMethod(child, true));
            }

            for (ExecutableTypeData type : genericExecutableTypes) {
                wrapWithTraceOnReturn(uncached.add(createUncachedExecute(type)));
            }

            for (ExecutableTypeData type : specializedExecutableTypes) {
                wrapWithTraceOnReturn(uncached.add(createUncachedExecute(type)));
            }

            for (ExecutableTypeData type : voidExecutableTypes) {
                wrapWithTraceOnReturn(uncached.add(createUncachedExecute(type)));
            }

            uncached.getImplements().add(types.UnadoptableNode);

            clazz.add(uncached);
            GeneratedTypeMirror uncachedType = new GeneratedTypeMirror("", uncached.getSimpleName().toString());
            CodeVariableElement uncachedField = clazz.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), uncachedType, "UNCACHED"));
            uncachedField.createInitBuilder().startNew(uncachedType).end();
        }

        // generate debug info

        CodeTreeBuilder debug = clazz.createDocBuilder();
        debug.startJavadoc();
        debug.string("Debug Info: <pre>").newLine();

        for (SpecializationData specialization : node.getReachableSpecializations()) {
            debug.string("  Specialization ").javadocLink(specialization.getMethod(), null).newLine();
            SpecializationClassSizeEstimate estimate = computeSpecializationClassSizeEstimate(specialization);
            debug.string("    Activation probability: ").string(String.format("%.5f", specialization.getActivationProbability())).newLine();
            debug.string("    With/without class size: ").string(String.valueOf(estimate.sizeWithClass)).string("/", String.valueOf(estimate.sizeWithoutClass), " bytes").newLine();
        }
        debug.string("</pre>");
        debug.end();

        return clazz;
    }

    private CodeVariableElement createCacheInlinedField(CodeTreeBuilder init, SpecializationData specialization,
                    MultiStateBitSet specializationState, CacheExpression cache) {
        final Parameter parameter = cache.getParameter();
        final String fieldName = createLocalCachedInlinedName(specialization, cache);

        // for state access we need use the shared cache
        boolean needsInlineTarget = needsInlineTarget(specialization, cache);

        CodeTreeBuilder b = init.create();
        b.startStaticCall(cache.getInlinedNode().getMethod());
        b.startStaticCall(types().InlineSupport_InlineTarget, "create");
        b.typeLiteral(cache.getParameter().getType());

        CacheExpression sharedCache = lookupSharedCacheKey(cache);
        for (InlineFieldData field : sharedCache.getInlinedNode().getFields()) {
            if (field.isState()) {
                BitSet bitSet = allMultiState.findSet(InlinedNodeState.class, field);

                if (bitSet == null) { // bitset in specialized class
                    bitSet = findInlinedState(specializationState, field);
                    CodeVariableElement stateVariable = createStateUpdaterField(specialization, specializationState, field, specializationClasses.get(specialization).getEnclosedElements());
                    BitRange range = bitSet.getStates().queryRange(StateQuery.create(InlinedNodeState.class, field));

                    CodeTreeBuilder helper = b.create();
                    helper.startCall(stateVariable.getName(), "subUpdater");
                    helper.string(String.valueOf(range.offset));
                    helper.string(String.valueOf(range.length));
                    helper.end();

                    /*
                     * If we need a target to instantiate we should create a constant for the
                     * individual field to avoid unnecessary allocations for each instance.
                     *
                     * If we don't need an inline target, we don't need to extract into a constant
                     * here as the entire cached initializer result will be stored as constant. No
                     * need to create constants for individual fields, which would then be more
                     * costly.
                     */
                    if (needsInlineTarget) {
                        String updaterFieldName = stateVariable.getSimpleName().toString() + "_" + range.offset + "_" + range.length;
                        CodeVariableElement var = nodeConstants.updaterReferences.get(updaterFieldName);
                        if (var == null) {
                            var = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), stateVariable.getType(), updaterFieldName);
                            var.createInitBuilder().tree(helper.build());
                            nodeConstants.updaterReferences.put(updaterFieldName, var);
                        }
                        b.string(updaterFieldName);
                    } else {
                        b.tree(helper.build());
                    }
                } else {
                    if (specializationState != null && specializationState.findSet(InlinedNodeState.class, field) != null) {
                        throw new AssertionError("Inlined field in specializationState and regular state at the same time.");
                    }
                    b.startGroup();
                    b.startCall(bitSet.getName() + "_", "subUpdater");
                    BitRange range = bitSet.getStates().queryRange(StateQuery.create(InlinedNodeState.class, field));
                    b.string(String.valueOf(range.offset));
                    b.string(String.valueOf(range.length));
                    b.end();
                    b.end();
                }
            } else {
                String inlinedFieldName = createCachedInlinedFieldName(specialization, cache, field);
                if (specialization != null && useSpecializationClass(specialization) && cache.getSharedGroup() == null) {
                    CodeTypeElement specializationDataClass = specializationClasses.get(specialization);
                    CodeTreeBuilder helper = b.create();

                    helper.startStaticCall(field.getFieldType(), "create");
                    helper.startGroup();
                    helper.tree(createLookupNodeType(createSpecializationClassReferenceType(specialization), specializationDataClass.getEnclosedElements()));
                    helper.end();
                    helper.doubleQuote(inlinedFieldName);
                    if (field.isReference()) {
                        helper.typeLiteral(field.getType());
                    }
                    helper.end();

                    if (needsInlineTarget) {
                        String updaterFieldName = ElementUtils.createConstantName(specializationDataClass.getSimpleName().toString() + "_" + inlinedFieldName) + "_UPDATER";
                        CodeVariableElement var = nodeConstants.updaterReferences.get(updaterFieldName);
                        if (var == null) {
                            var = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), field.getFieldType(), updaterFieldName);
                            var.createInitBuilder().tree(helper.build());
                            nodeConstants.updaterReferences.put(updaterFieldName, var);
                        }
                        b.string(updaterFieldName);
                    } else {
                        b.tree(helper.build());
                    }

                } else {
                    b.string(inlinedFieldName);
                }
            }
        }
        b.end();
        b.end();

        // if the initializer does not need the target we can create a constant instead
        if (needsInlineTarget) {
            init.startStatement();
            init.string("this.", fieldName, " = ");
            init.tree(b.build());
            init.end();
            CodeVariableElement var = new CodeVariableElement(modifiers(PRIVATE, FINAL), parameter.getType(), fieldName);
            CodeTreeBuilder builder = var.createDocBuilder();
            builder.startJavadoc();
            addSourceDoc(builder, specialization, cache, null);
            builder.end();
            return var;
        } else {
            String name = createStaticInlinedCacheName(specialization, cache);
            CodeVariableElement var = nodeConstants.updaterReferences.get(name);
            if (var == null) {
                var = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), parameter.getType(), name);
                var.createInitBuilder().tree(b.build());
                nodeConstants.updaterReferences.put(name, var);
            }
            return null;
        }
    }

    /**
     * Returns <code>true</code> if the inlined cache requires any field from the inline target to
     * get initialized, else <code>false</code>. If it does not depend on the inline target the code
     * generator can typically extract the instance into a static final field instead of
     * initializing it in the generated Inlined class constructor.
     */
    private boolean needsInlineTarget(SpecializationData specialization, CacheExpression cache) {
        if (cache.getSharedGroup() != null) {
            // shared cache -> never in data class
            return true;
        }
        for (InlineFieldData field : cache.getInlinedNode().getFields()) {
            if (field.isState() && allMultiState.findSet(InlinedNodeState.class, field) == null) {
                // bit set located in specialization data class, no access to inline target needed
            } else if (specialization != null && useSpecializationClass(specialization)) {
                // we use a data class for this specialization so the field is stored there
            } else {
                // by default state is stored in the parent node
                return true;
            }
        }
        return false;
    }

    private String createLocalCachedInlinedName(SpecializationData specialization, CacheExpression cache) {
        String sharedName = sharedCaches.get(cache);
        if (sharedName != null && specialization != null && hasCacheParentAccess(cache)) {
            return specialization.getId().toLowerCase() + "_" + sharedName + "_";
        } else {
            return createFieldName(specialization, cache);
        }
    }

    private String createCachedInlinedFieldName(SpecializationData specialization, CacheExpression cache, InlineFieldData field) {
        return createFieldName(specialization, cache) + "_" + field.getName() + "_";
    }

    static void addStateDoc(CodeTreeBuilder docBuilder, BitSet set) {
        docBuilder.string("State Info: <pre>").newLine();

        for (BitRangedState value : set.getStates().getEntries()) {
            BitRange range = value.bitRange;
            docBuilder.string("  ");
            docBuilder.string(String.valueOf(range.offset));

            if (range.length != 1) {
                docBuilder.string("-").string(String.valueOf(range.offset + range.length - 1));
            }
            docBuilder.string(": ");
            value.state.addStateDoc(docBuilder);
            docBuilder.newLine();
        }

        docBuilder.string("</pre>");
    }

    private static void addSourceDoc(CodeTreeBuilder builder, SpecializationData specialization, CacheExpression cache, InlineFieldData inlinedField) {
        builder.string("Source Info: <pre>").newLine();
        addCacheInfo(builder, "  ", specialization, cache, inlinedField);
        builder.string("</pre>");
    }

    static void addCacheInfo(CodeTreeBuilder builder, String linePrefix,
                    SpecializationData specialization, CacheExpression cache, InlineFieldData inlinedField) {

        Element specializationSource = resolveSpecializationSource(specialization, cache);
        if (specializationSource != null) {
            builder.string(linePrefix).string("Specialization: ").javadocLink(specializationSource, null);
        }
        if (cache != null) {
            builder.newLine();
            builder.string(linePrefix).string("Parameter: ");
            linkParameter(builder, cache.getParameter().getType(), cache.getParameter().getVariableElement().getSimpleName().toString());

            if (cache.getInlinedNode() != null) {
                builder.newLine();
                builder.string(linePrefix).string("Inline method: ");
                builder.javadocLink(cache.getInlinedNode().getMethod(), null);
            }
        }

        if (inlinedField != null && !inlinedField.isState()) {
            builder.newLine();
            builder.string(linePrefix).string("Inline field: ");
            linkParameter(builder, inlinedField.getType(), inlinedField.getName());
        }
    }

    static Element resolveSpecializationSource(SpecializationData specialization, CacheExpression cache) {
        Element specializationSource;
        if (specialization != null && specialization.getMethod() != null) {
            specializationSource = specialization.getMethod();
        } else if (cache != null) {
            specializationSource = cache.getParameter().getVariableElement().getEnclosingElement();
        } else {
            specializationSource = null;
        }
        return specializationSource;
    }

    private static void linkParameter(CodeTreeBuilder builder, TypeMirror type, String name) {
        TypeElement typeElement = ElementUtils.fromTypeMirror(type);
        if (typeElement != null) {
            builder.javadocLink(typeElement, null);
        } else {
            builder.string(getSimpleName(type));
        }
        builder.string(" ").string(name);
    }

    private static final String AOT_STATE = "$aot";

    private void generateAOT(CodeTypeElement clazz, boolean inlined) {
        TypeMirror aotProviderType = new GeneratedTypeMirror(ElementUtils.getPackageName(types.GenerateAOT_Provider), "GenerateAOT.Provider");
        clazz.getImplements().add(aotProviderType);

        CodeExecutableElement prepare = clazz.add(CodeExecutableElement.cloneNoAnnotations(
                        ElementUtils.findMethod(types.GenerateAOT_Provider, "prepareForAOT", inlined ? 3 : 2)));
        prepare.getModifiers().remove(Modifier.DEFAULT);
        GeneratorUtils.addOverride(prepare);
        prepare.getModifiers().remove(ABSTRACT);
        CodeTreeBuilder builder = prepare.createBuilder();

        List<SpecializationData> filteredSpecializations = new ArrayList<>();
        for (NodeData currentNode : sharingNodes) {
            for (SpecializationData s : currentNode.getReachableSpecializations()) {
                if (s.getMethod() == null || !s.isPrepareForAOT()) {
                    continue;
                }
                filteredSpecializations.add(s);
            }
        }

        FrameState frameState = FrameState.load(this, NodeExecutionMode.SLOW_PATH, prepare);
        frameState.setBoolean(AOT_STATE, true);
        frameState.setInlinedNode(inlined);

        if (inlined) {
            prepare.renameArguments("language", "root", frameState.getValue(node.getChildExecutions().get(0)).getName());
        } else {
            prepare.renameArguments("language", "root");
        }

        Map<BitSet, List<SpecializationData>> stateGroup = new LinkedHashMap<>();
        Set<TypeGuard> implicitCasts = new LinkedHashSet<>();

        for (SpecializationData specialization : filteredSpecializations) {
            for (BitSet set : allMultiState.getSets()) {
                if (set.contains(AOT_PREPARED)) {
                    // make sure we have an entry for a state bitset
                    // without any specialization but only with the AOT bit set
                    stateGroup.computeIfAbsent(set, (s) -> new ArrayList<>());
                }
                if (set.contains(StateQuery.create(SpecializationActive.class, specialization))) {
                    stateGroup.computeIfAbsent(set, (s) -> new ArrayList<>()).add(specialization);
                    break;
                }
            }

            int index = 0;
            for (Parameter p : specialization.getSignatureParameters()) {
                TypeMirror targetType = p.getType();
                Collection<TypeMirror> sourceTypes = node.getTypeSystem().lookupSourceTypes(targetType);
                if (sourceTypes.size() > 1) {
                    implicitCasts.add(new TypeGuard(node.getTypeSystem(), targetType, index));
                }
                index++;
            }
        }

        builder.startAssert();
        builder.string("!isAdoptable() || ");
        builder.string("(").cast(context.getType(ReentrantLock.class), CodeTreeBuilder.singleString("getLock()"));
        builder.string(").isHeldByCurrentThread()");
        builder.string(" : ").doubleQuote("During prepare AST lock must be held.");
        builder.end();

        for (BitSet set : multiState.getSets()) {
            if (set.contains(AOT_PREPARED)) {
                builder.startIf();
                builder.tree(set.createContains(frameState, AOT_PREPARED));
                builder.end().startBlock();
                builder.returnDefault();
                builder.end();
                break;
            }
        }

        List<GuardExpression> bulkStateSetGuards = new ArrayList<>();

        for (SpecializationData specialization : filteredSpecializations) {

            builder.startBlock(); // local can overlap between nodes

            // we need to copy otherwise local variables of caches may conflict.
            FrameState innerFrameState = frameState.copy();

            SpecializationGroup specializationGroup = SpecializationGroup.create(Arrays.asList(specialization));

            for (CacheExpression cache : specialization.getCaches()) {
                if (!cache.isAlwaysInitialized()) {
                    continue;
                }
                setCacheInitialized(innerFrameState, specialization, cache, true);
            }

            List<IfTriple> tripples = new ArrayList<>();
            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                tripples.addAll(createAssumptionSlowPathTriples(innerFrameState, specializationGroup, assumption));
            }

            /*
             * We don't need to materialize assumption conditions.
             */
            for (IfTriple triple : tripples) {
                triple.condition = null;
            }

            // compute guards that can be materialized
            List<GuardExpression> usedGuards = new ArrayList<>();
            for (GuardExpression guard : specialization.getGuards()) {
                if (guardNeedsStateBit(specialization, guard)) {
                    bulkStateSetGuards.add(guard);
                }
                if (specialization.isDynamicParameterBound(guard.getExpression(), true)) {
                    /*
                     * Guards with no dynamic parameters can be executed.
                     */
                    continue;
                }
                usedGuards.add(guard);
            }

            tripples.addAll(createMethodGuardChecks(innerFrameState, specializationGroup, usedGuards, NodeExecutionMode.SLOW_PATH));

            BlockState blockState = IfTriple.materialize(builder, tripples, false);

            builder.tree(createSpecialize(builder, innerFrameState, null, specializationGroup, specialization, true));

            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.isAlwaysInitialized()) {
                    continue;
                }

                /*
                 * Libraries might not be AOT preparable. E.g. if a cached library was created from
                 * a final field of the current language. In such a case we should just not call
                 * prepareForAOT.
                 *
                 * Specializable nodes are always known to be preparable if they reach the code
                 * generator.
                 */
                boolean cachedLibrary = cache.isCachedLibrary();
                if (cachedLibrary) {
                    builder.startIf().tree(createCacheAccess(innerFrameState, specialization, cache, null)).instanceOf(aotProviderType).end().startBlock();
                }
                if (NodeCodeGenerator.isSpecializedNode(cache.getParameter().getType()) || cachedLibrary) {
                    builder.startAssert().startStaticCall(types.NodeUtil, "assertRecursion");
                    builder.tree(createCacheAccess(innerFrameState, specialization, cache, null));
                    /*
                     * We allow a single recursion level only for AOT preparation. It is important
                     * that we only assert recursion for @Cached fields as regular AST children can
                     * be recursive arbitrarily deep.
                     *
                     * We might need to increase this limit in the future if it triggers to eagerly.
                     */
                    builder.string("1");
                    builder.end().end();

                    builder.startStatement();
                    builder.string("(");
                    builder.cast(aotProviderType);
                    builder.tree(createCacheAccess(innerFrameState, specialization, cache, null));
                    builder.string(")");
                    builder.startCall(".prepareForAOT");
                    builder.string("language").string("root");

                    if (cache.getInlinedNode() != null) {
                        if (frameState.isInlinedNode()) {
                            builder.tree(innerFrameState.getValue(INLINED_NODE_INDEX).createReference());
                        } else {
                            builder.string("this");
                        }
                    }
                    builder.end();
                    builder.end();
                }
                if (cachedLibrary) {
                    builder.end();
                }
            }

            builder.tree(multiState.createSet(innerFrameState, null, StateQuery.create(SpecializationActive.class, specialization), true, true));
            builder.end(blockState.blockCount);

            builder.end();
        }
        StateTransaction transaction = new StateTransaction();
        builder.tree(multiState.createForceLoad(frameState,
                        AOT_PREPARED,
                        StateQuery.create(GuardActive.class, bulkStateSetGuards), StateQuery.create(ImplicitCastState.class, implicitCasts)));

        builder.tree(multiState.createSet(frameState, transaction, AOT_PREPARED, true, false));
        builder.tree(multiState.createSet(frameState, transaction, StateQuery.create(GuardActive.class, bulkStateSetGuards), true, false));
        builder.tree(multiState.createSet(frameState, transaction, StateQuery.create(ImplicitCastState.class, implicitCasts), true, false));
        builder.tree(multiState.persistTransaction(frameState, transaction));

        if (!needsAOTReset(node, sharingNodes)) {
            return;
        }

        CodeExecutableElement reset = clazz.add(new CodeExecutableElement(modifiers(PRIVATE), context.getType(void.class), "resetAOT_"));
        frameState = FrameState.load(this, NodeExecutionMode.FAST_PATH, reset);
        frameState.setInlinedNode(inlined);
        if (inlined) {
            reset.addParameter(new CodeVariableElement(types.Node, frameState.getValue(node.getChildExecutions().get(0)).getName()));
        }

        reset.getModifiers().remove(ABSTRACT);
        builder = reset.createBuilder();

        for (BitSet set : multiState.all) {
            if (set.contains(AOT_PREPARED)) {
                builder.tree(set.createLoad(frameState));
                builder.startIf();
                builder.tree(set.createNotContains(frameState, AOT_PREPARED));
                builder.end().startBlock();
                builder.returnDefault();
                builder.end();
            }
            break;
        }

        for (BitSet set : multiState.getSets()) {
            builder.tree(set.createSetZero(frameState, true));
        }

        for (SpecializationData specialization : filteredSpecializations) {
            if (useSpecializationClass(specialization)) {
                builder.startStatement();
                builder.tree(createSpecializationFieldAccess(frameState, specialization, true, true, null, singleString("null")));
                builder.end();
            } else {
                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.isAlwaysInitialized()) {
                        continue;
                    } else if (cache.isEagerInitialize()) {
                        continue;
                    } else if (cache.isBind()) {
                        continue;
                    }
                    if (types.Profile != null &&
                                    (ElementUtils.isAssignable(cache.getParameter().getType(), types.Profile) || ElementUtils.isAssignable(cache.getParameter().getType(), types.InlinedProfile))) {
                        builder.startStatement();
                        builder.tree(createCacheAccess(frameState, specialization, cache, null));
                        builder.startCall(".reset");
                        if (cache.getInlinedNode() != null) {
                            builder.tree(createNodeAccess(frameState, specialization));
                        }
                        builder.end();
                        builder.end();
                    } else if (cache.getInlinedNode() == null) {
                        builder.startStatement();
                        builder.tree(createCacheAccess(frameState, specialization, cache, singleString(ElementUtils.defaultValue(cache.getParameter().getType()))));
                        builder.end();
                    }
                }
            }
        }

    }

    public List<CodeVariableElement> createUncachedFields() {
        List<CodeVariableElement> fields = new ArrayList<>();
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
            List<IfTriple> triples = new ArrayList<>();
            triples.addAll(persistAndInitializeCache(frameState, NodeExecutionMode.SLOW_PATH, specialization, cache, false, true));
            IfTriple.materialize(b, triples, true);
        }
        return b.build();
    }

    private static final class ReportPolymorphismAction {
        final boolean polymorphism;
        final boolean megamorphism;

        ReportPolymorphismAction(boolean polymorphism, boolean megamorphism) {
            this.polymorphism = polymorphism;
            this.megamorphism = megamorphism;
        }

        public boolean required() {
            return polymorphism || megamorphism;
        }
    }

    private static ReportPolymorphismAction reportPolymorphismAction(NodeData node, List<SpecializationData> reachableSpecializations) {
        if (reachableSpecializations.size() == 1 && reachableSpecializations.get(0).getMaximumNumberOfInstances() == 1) {
            return new ReportPolymorphismAction(false, false);
        }
        final boolean reportMegamorphism = reachableSpecializations.stream().anyMatch(SpecializationData::isReportMegamorphism);
        if (reachableSpecializations.stream().noneMatch(SpecializationData::isReportPolymorphism)) {
            return new ReportPolymorphismAction(false, reportMegamorphism);
        }
        return new ReportPolymorphismAction(node.isReportPolymorphism(), reportMegamorphism);
    }

    private void generateIntrospectionInfo(CodeTypeElement clazz, boolean inlined) {
        clazz.getImplements().add(new GeneratedTypeMirror(ElementUtils.getPackageName(types.Introspection_Provider), "Introspection.Provider"));
        CodeExecutableElement reflection = new CodeExecutableElement(modifiers(PUBLIC), types.Introspection, "getIntrospectionData");
        GeneratorUtils.addOverride(reflection);
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

        boolean needsRewrites = node.needsSpecialize();

        FrameState frameState = FrameState.load(this, NodeExecutionMode.SLOW_PATH, reflection);
        frameState.setInlinedNode(inlined);

        if (inlined) {
            reflection.addParameter(frameState.getValue(INLINED_NODE_INDEX).createParameter());
        }

        StateQuery specializationsActive = StateQuery.create(SpecializationActive.class, filteredSpecializations);
        StateQuery specializationsExcluded = StateQuery.create(SpecializationExcluded.class, filteredSpecializations);

        if (needsRewrites) {
            builder.tree(multiState.createLoad(frameState, specializationsActive, specializationsExcluded));
        }

        int index = 1;
        for (SpecializationData specialization : filteredSpecializations) {

            FrameState innerFrameState = frameState.copy();

            builder.startStatement().string("s = ").startNewArray(objectArray, CodeTreeBuilder.singleString("3")).end().end();
            builder.startStatement().string("s[0] = ").doubleQuote(specialization.getMethodName()).end();

            BlockState blocks = BlockState.NONE;
            if (needsRewrites) {
                builder.startIf().tree(createSpecializationActiveCheck(frameState, Arrays.asList(specialization))).end().startBlock();
                List<IfTriple> tripples = createFastPathNeverDefaultGuards(innerFrameState, specialization);
                blocks = IfTriple.materialize(builder, tripples, false);
            }
            builder.startStatement().string("s[1] = (byte)0b01 /* active */").end();
            TypeMirror listType = new DeclaredCodeTypeMirror((TypeElement) context.getDeclaredType(ArrayList.class).asElement(), Arrays.asList(context.getType(Object.class)));

            if (!specialization.getCaches().isEmpty()) {
                builder.declaration(listType, "cached", "new ArrayList<>()");

                boolean useSpecializationClass = useSpecializationClass(specialization);

                String name = createSpecializationLocalName(specialization);
                if (useSpecializationClass) {
                    builder.tree(loadSpecializationClass(innerFrameState, specialization, false));

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
                builder.startStaticCall(context.getType(Arrays.class), "<Object>asList");
                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.isAlwaysInitialized()) {
                        continue;
                    }
                    builder.startGroup();
                    if (cache.isAlwaysInitialized() && cache.isCachedLibrary()) {
                        builder.staticReference(createLibraryConstant(constants, cache.getParameter().getType()));
                        builder.startCall(".getUncached").end();
                    } else {
                        builder.tree(createCacheAccess(innerFrameState, specialization, cache, null));
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
                builder.end(blocks.blockCount);
                builder.end();

                builder.startIf().string("s[1] == null").end().startBlock();

                List<IfTriple> excludeTripples = new ArrayList<>();
                if (hasExcludeBit(specialization)) {
                    CodeTree excludeCheck = multiState.createNotContains(frameState, StateQuery.create(SpecializationExcluded.class, specialization));
                    excludeTripples.add(0, new IfTriple(null, excludeCheck, null));
                }
                if (hasExcludes(specialization)) {
                    CodeTree excludeCheck = multiState.createContains(frameState, StateQuery.create(SpecializationActive.class, specialization.getReplacedBy()));
                    excludeTripples.add(0, new IfTriple(null, excludeCheck, null));
                }
                BlockState excludeBlocks = BlockState.NONE;
                if (!excludeTripples.isEmpty()) {
                    excludeBlocks = IfTriple.materialize(builder, IfTriple.optimize(excludeTripples), false);
                    builder.startStatement().string("s[1] = (byte)0b10 /* excluded */").end();
                    builder.end(excludeBlocks.blockCount);
                    builder.startElseBlock();
                }
                builder.startStatement().string("s[1] = (byte)0b00 /* inactive */").end();

                if (!excludeTripples.isEmpty()) {
                    builder.end();
                }
                builder.end();
            }
            builder.startStatement().string("data[", String.valueOf(index), "] = s").end();
            index++;
        }

        builder.startReturn().startStaticCall(types.Introspection_Provider, "create").string("data").end().end();

        clazz.add(reflection);
    }

    private List<Element> createCachedFields(CodeTypeElement baseType) {
        List<Element> nodeElements = new ArrayList<>();

        Set<String> expressions = new HashSet<>();
        if (primaryNode) {
            /*
             * We only generated shared cached fields once for the primary node.
             */
            for (Entry<CacheExpression, String> entry : sharedCaches.entrySet()) {
                CacheExpression cache = entry.getKey();
                String fieldName = entry.getValue();
                if (expressions.contains(fieldName)) {
                    continue;
                }
                expressions.add(fieldName);
                createCachedFieldsImpl(nodeElements, nodeElements, null, null, cache, true);
            }
        }

        for (SpecializationData specialization : node.getReachableSpecializations()) {
            boolean useSpecializationClass = useSpecializationClass(specialization);
            MultiStateBitSet specializationState = lookupSpecializationState(specialization);

            List<Element> specializationClassElements = useSpecializationClass ? new ArrayList<>() : nodeElements;
            for (CacheExpression cache : specialization.getCaches()) {
                boolean shared = sharedCaches.containsKey(cache);
                if (shared && !hasCacheParentAccess(cache)) {
                    continue;
                }
                createCachedFieldsImpl(nodeElements, specializationClassElements,
                                specialization, specializationState, cache, !shared);
            }

            for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
                if (!assumption.needsCaching()) {
                    continue;
                }

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
                addCompilationFinalAnnotation(assumptionField, compilationFinalDimensions);

                if (useSpecializationClass) {
                    specializationClassElements.add(assumptionField);
                } else {
                    nodeElements.add(assumptionField);
                }
            }

            if (useSpecializationClass) {
                createSpecializationClass(baseType, specialization, specializationState, specializationClassElements);

                // force creating the specialization class.
                CodeVariableElement specializationClassVar = createNodeField(PRIVATE, createSpecializationClassReferenceType(specialization),
                                createSpecializationFieldName(specialization), null);

                if (needsUpdater(specialization) && baseType != null) {
                    GeneratorUtils.markUnsafeAccessed(specializationClassVar);
                }

                if (specializationClassIsNode(specialization)) {
                    specializationClassVar.getAnnotationMirrors().add(new CodeAnnotationMirror(types().Node_Child));
                } else {
                    specializationClassVar.getAnnotationMirrors().add(new CodeAnnotationMirror(types().CompilerDirectives_CompilationFinal));
                }

                nodeElements.add(specializationClassVar);
            }

        }
        return nodeElements;
    }

    private static boolean needsUpdater(SpecializationData specialization) {
        if (!specialization.getNode().isGenerateCached()) {
            return false;
        }
        return needsDuplicationCheck(specialization);
    }

    private static boolean needsDuplicationCheck(SpecializationData specialization) {
        if (specialization.hasMultipleInstances()) {
            return true;
        } else if (specialization.isGuardBindsExclusiveCache()) {
            return true;
        }
        return false;
    }

    private CodeTypeElement createCacheClass(CacheExpression cache, CodeVariableElement wrappedField, boolean needsAdoption) {
        TypeMirror baseType;
        if (needsAdoption) {
            baseType = types.Node;
        } else {
            baseType = context.getType(Object.class);
        }
        CodeTypeElement specializationClass = GeneratorUtils.createClass(node, null, modifiers(PRIVATE, FINAL, STATIC),
                        createCacheClassName(cache), baseType);
        specializationClass.getAnnotationMirrors().add(new CodeAnnotationMirror(types.DenyReplace));
        specializationClass.add(wrappedField);
        wrappedField.setName("delegate");

        return specializationClass;
    }

    @SuppressWarnings("unchecked")
    private CodeTree createLookupNodeType(TypeMirror lookupType, List<? extends Element> elements) {
        List<Element> enclosingElements = (List<Element>) elements;
        ExecutableElement found = null;
        for (ExecutableElement method : ElementFilter.methodsIn(enclosingElements)) {
            if (method.getSimpleName().toString().equals("lookup_")) {
                found = method;
                break;
            }
        }

        if (found == null) {
            found = createLookupMethod();
            enclosingElements.add(found);
        }
        return createLookupCall(lookupType);
    }

    private static CodeTree createLookupCall(TypeMirror type) {
        return CodeTreeBuilder.createBuilder().startStaticCall(type, "lookup_").end().build();
    }

    private ExecutableElement createLookupMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE, STATIC), context.getType(Lookup.class), "lookup_");
        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn().startStaticCall(context.getType(MethodHandles.class), "lookup").end().end();
        return method;
    }

    private String createSpecializationClassUpdaterName(SpecializationData specialization) {
        return ElementUtils.createConstantName(createSpecializationFieldName(specialization)) + "_UPDATER";
    }

    private void createSpecializationClass(CodeTypeElement enclosingType, SpecializationData specialization,
                    MultiStateBitSet specializationState,
                    List<Element> specializationClassElements) {
        CodeTypeElement specializationClass = specializationClasses.get(specialization);
        if (specializationClass != null) {
            return;
        }

        if (specializationClassElements.isEmpty() && specializationState != null && specializationState.getCapacity() == 0) {
            throw new AssertionError("Should not create an empty specialization class.");
        }

        boolean useNode = specializationClassIsNode(specialization);
        TypeMirror baseType;
        if (useNode) {
            baseType = types.Node;
        } else {
            baseType = context.getType(Object.class);
        }

        specializationClass = GeneratorUtils.createClass(node, null, modifiers(PRIVATE, FINAL, STATIC),
                        createSpecializationTypeName(specialization), baseType);
        specializationClass.getAnnotationMirrors().add(new CodeAnnotationMirror(types.DenyReplace));
        specializationClass.getImplements().add(types.DSLSupport_SpecializationDataNode);

        specializationClasses.put(specialization, specializationClass);

        TypeMirror referenceType = createSpecializationClassReferenceType(specialization);
        if (needsUpdater(specialization) && enclosingType != null) {
            TypeMirror fieldType = new DeclaredCodeTypeMirror(context.getTypeElement(types.InlineSupport_ReferenceField), Arrays.asList(referenceType));
            CodeVariableElement updater = new CodeVariableElement(modifiers(STATIC, FINAL), fieldType, createSpecializationClassUpdaterName(specialization));
            CodeTreeBuilder init = updater.createInitBuilder();
            init.startStaticCall(types.InlineSupport_ReferenceField, "create");
            init.startStaticCall(context.getType(MethodHandles.class), "lookup").end();
            init.doubleQuote(createSpecializationFieldName(specialization));
            init.typeLiteral(referenceType);
            init.end();
            enclosingType.getEnclosedElements().add(updater);
        }

        DeclaredType annotationType;
        if (useNode) {
            annotationType = types.Node_Child;
        } else {
            annotationType = types.CompilerDirectives_CompilationFinal;
        }

        String nextName = "next_";
        if (specialization.getMaximumNumberOfInstances() > 1) {
            CodeVariableElement var = createNodeField(null, referenceType, nextName, annotationType);
            if (annotationType != types.Node_Child) {
                var.getModifiers().add(Modifier.FINAL);
            }
            specializationClass.add(var);
        }

        specializationClass.add(GeneratorUtils.createConstructorUsingFields(modifiers(), specializationClass));

        if (specializationState != null) {
            specializationClass.addAll(specializationState.createCachedFields());
        }

        specializationClass.getEnclosedElements().addAll(specializationClassElements);

        if (specializationClassNeedsCopyConstructor(specialization)) {
            if (specialization.getMaximumNumberOfInstances() > 1) {
                throw new AssertionError("Copy constructor with next_ field is dangerous.");
            }
            specializationClass.add(GeneratorUtils.createCopyConstructorUsingFields(modifiers(), specializationClass, Collections.emptySet()));
        }

        if (specialization.hasMultipleInstances() && !specialization.getAssumptionExpressions().isEmpty()) {
            CodeExecutableElement remove = specializationClass.add(new CodeExecutableElement(referenceType, "remove"));
            if (useNode) {
                remove.addParameter(new CodeVariableElement(types.Node, "parent"));
            }
            remove.addParameter(new CodeVariableElement(referenceType, "search"));
            CodeTreeBuilder builder = remove.createBuilder();
            builder.declaration(referenceType, "newNext", "this.next_");
            builder.startIf().string("newNext != null").end().startBlock();
            builder.startIf().string("search == newNext").end().startBlock();
            builder.statement("newNext = newNext.next_");
            builder.end().startElseBlock();
            if (useNode) {
                builder.statement("newNext = newNext.remove(this, search)");
            } else {
                builder.statement("newNext = newNext.remove(search)");
            }
            builder.end();
            builder.end();

            builder.startStatement();
            builder.type(referenceType).string(" copy = ");
            if (useNode) {
                builder.startCall("parent.insert");
            }
            builder.startNew(referenceType).string("newNext").end();
            if (useNode) {
                builder.end();
            }
            builder.end();
            for (Element element : specializationClassElements) {
                if (element instanceof VariableElement variable && !element.getModifiers().contains(Modifier.STATIC)) {
                    String name = element.getSimpleName().toString();
                    TypeMirror type = variable.asType();

                    boolean needsInsert = useNode && isAssignable(type, types.Node) || isNodeArray(type);
                    if (needsInsert) {
                        builder.startStatement().string("copy.", name, " = copy.insert(this.", name, ")").end();
                    } else {
                        builder.startStatement().string("copy.", name, " = this.", name).end();
                    }

                }
            }
            builder.startReturn().string("copy").end();
        }
    }

    private TypeMirror createSpecializationClassReferenceType(SpecializationData specialization) {
        CodeTypeElement type = specializationClasses.get(specialization);
        if (type == null) {
            TypeMirror baseType = specializationClassIsNode(specialization) ? types.Node : context.getType(Object.class);
            return new GeneratedTypeMirror("", createSpecializationTypeName(specialization), baseType);
        }
        return new GeneratedTypeMirror("", type.getSimpleName().toString(), type.getSuperclass());
    }

    private final Map<SpecializationData, MultiStateBitSet> specializationStates = new HashMap<>();

    private MultiStateBitSet lookupSpecializationState(SpecializationData specialization) {
        MultiStateBitSet specializationState = specializationStates.get(specialization);
        if (!specializationStates.containsKey(specialization)) {
            BitStateList list = computeSpecializationState(specialization);
            if (list.getBitCount() > 0) {
                specializationState = createMultiStateBitset(ElementUtils.firstLetterLowerCase(specialization.getId()) + "_", specialization.getNode(), list);
            }
            specializationStates.put(specialization, specializationState);
        }
        return specializationState;
    }

    private void createCachedFieldsImpl(
                    List<Element> nodeElements,
                    List<Element> specializationClassElements,
                    SpecializationData specialization,
                    MultiStateBitSet specializationState,
                    CacheExpression cache,
                    boolean generateInlinedFields) {
        if (cache.isAlwaysInitialized()) {
            return;
        } else if (cache.isEncodedEnum()) {
            return;
        }
        CacheExpression sharedCache = lookupSharedCacheKey(cache);
        InlinedNodeData inline = sharedCache.getInlinedNode();
        /*
         * Handles corner case where we try to avoid generating shared cached fields if we are
         * always using parent access cache for a shared cache.
         */
        boolean generateCachedFields = specialization != null || !hasCacheParentAccess(cache) || hasSharedCacheDirectAccess(lookupSharedCacheKey(cache));

        if (inline != null) {
            Parameter parameter = cache.getParameter();
            String fieldName = createStaticInlinedCacheName(specialization, cache);

            ExecutableElement cacheMethod = cache.getInlinedNode().getMethod();
            CodeVariableElement cachedField = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), parameter.getType(), fieldName);
            CodeTreeBuilder builder = cachedField.createInitBuilder();
            builder.startStaticCall(cacheMethod);
            builder.startStaticCall(types().InlineSupport_InlineTarget, "create");
            builder.typeLiteral(cache.getParameter().getType());

            for (InlineFieldData field : inline.getFields()) {
                builder.startGroup();
                if (field.isState()) {
                    if (generateCachedFields) {
                        BitSet specializationBitSet = findInlinedState(specializationState, field);
                        CodeVariableElement updaterField = createStateUpdaterField(specialization, specializationState, field, specializationClassElements);
                        BitRange range = specializationBitSet.getStates().queryRange(StateQuery.create(InlinedNodeState.class, field));
                        String updaterFieldName = updaterField.getName();
                        builder.startCall(updaterFieldName, "subUpdater");
                        builder.string(String.valueOf(range.offset));
                        builder.string(String.valueOf(range.length));
                        builder.end();

                    }
                } else {
                    /*
                     * All other fields need fields to get inlined. We do not support specialization
                     * classes in this branch.
                     */
                    String inlinedFieldName = createCachedInlinedFieldName(specialization, cache, field);

                    TypeMirror type = field.getType();

                    if (generateInlinedFields) {
                        CodeVariableElement inlinedCacheField;
                        if (isAssignable(type, types().Node)) {
                            inlinedCacheField = createNodeField(Modifier.PRIVATE, types.Node, inlinedFieldName, types().Node_Child);
                        } else if (isAssignable(type, types().NodeInterface)) {
                            inlinedCacheField = createNodeField(Modifier.PRIVATE, types.NodeInterface, inlinedFieldName, types().Node_Child);
                        } else if (isNodeArray(type)) {
                            inlinedCacheField = createNodeField(Modifier.PRIVATE, new ArrayCodeTypeMirror(types.Node), inlinedFieldName, types().Node_Children);
                        } else {
                            inlinedCacheField = createNodeField(Modifier.PRIVATE, type, inlinedFieldName, null);
                            addCompilationFinalAnnotation(inlinedCacheField, field.getDimensions());
                        }
                        if (specialization != null && useSpecializationClass(specialization) && canCacheBeStoredInSpecialializationClass(cache)) {
                            specializationClassElements.add(inlinedCacheField);
                        } else {
                            nodeElements.add(inlinedCacheField);
                        }
                        GeneratorUtils.markUnsafeAccessed(inlinedCacheField);

                        CodeTreeBuilder javadoc = inlinedCacheField.createDocBuilder();
                        javadoc.startJavadoc();
                        addSourceDoc(javadoc, specialization, cache, field);
                        javadoc.end();

                        // never directly used, so will produce a warning.
                        GeneratorUtils.mergeSuppressWarnings(inlinedCacheField, "unused");
                    }

                    builder.startStaticCall(field.getFieldType(), "create");
                    if (specialization != null && useSpecializationClass(specialization) && canCacheBeStoredInSpecialializationClass(cache)) {
                        builder.tree(createLookupNodeType(createSpecializationClassReferenceType(specialization), specializationClassElements));
                    } else {
                        builder.startStaticCall(context.getType(MethodHandles.class), "lookup").end();
                    }
                    builder.doubleQuote(inlinedFieldName);

                    if (field.isReference()) {
                        builder.typeLiteral(field.getType());
                    }

                    builder.end(); // static call
                }

                builder.end();

            }
            builder.end();
            builder.end();

            CodeTreeBuilder javadoc = cachedField.createDocBuilder();
            javadoc.startJavadoc();
            addSourceDoc(javadoc, specialization, cache, null);
            javadoc.end();

            if (generateCachedFields) {
                nodeConstants.updaterReferences.putIfAbsent(fieldName, cachedField);
            }
        } else {
            Parameter parameter = cache.getParameter();
            String fieldName = createFieldName(specialization, cache);
            TypeMirror type = parameter.getType();

            boolean useSpecializationClass = specialization != null ? useSpecializationClass(specialization) : false;
            Modifier visibility = useSpecializationClass ? null : Modifier.PRIVATE;
            CodeVariableElement cachedField;
            boolean needsAdoption;
            if (isAssignable(type, types().NodeInterface) && cache.isAdopt()) {
                cachedField = createNodeField(visibility, type, fieldName, types().Node_Child);
                needsAdoption = true;
            } else if (isNodeArray(type) && cache.isAdopt()) {
                cachedField = createNodeField(visibility, type, fieldName, types().Node_Children);
                needsAdoption = true;
            } else {
                needsAdoption = false;
                cachedField = createNodeField(visibility, type, fieldName, null);
                if (cache.isCached()) {
                    AnnotationMirror mirror = cache.getMessageAnnotation();
                    int dimensions = getAnnotationValue(Integer.class, mirror, "dimensions");
                    addCompilationFinalAnnotation(cachedField, dimensions);
                }
            }

            CodeTreeBuilder javadoc = cachedField.createDocBuilder();
            javadoc.startJavadoc();
            addSourceDoc(javadoc, specialization, cache, null);
            javadoc.end();

            if (useCacheClass(specialization, sharedCache)) {
                String name = cachedField.getSimpleName().toString();
                CodeTypeElement cacheClass = createCacheClass(sharedCache, cachedField, needsAdoption);
                specializationClassElements.add(cacheClass);
                GeneratedTypeMirror generatedType = new GeneratedTypeMirror("", cacheClass.getSimpleName().toString(), cacheClass.asType());
                CodeVariableElement var = new CodeVariableElement(modifiers(PRIVATE), generatedType, name);
                addCompilationFinalAnnotation(var, 0, needsAdoption);

                javadoc = var.createDocBuilder();
                javadoc.startJavadoc();
                addSourceDoc(javadoc, specialization, cache, null);
                javadoc.newLine().string("Note: Shared cache value requires a wrapper class for thread-safety.");
                javadoc.newLine().string("Set Cached(neverDefault = true) to avoid the wrapper class.");
                javadoc.end();
                specializationClassElements.add(var);
            } else {
                specializationClassElements.add(cachedField);
            }
        }
    }

    private BitSet findInlinedState(MultiStateBitSet specializationState, InlineFieldData field) throws AssertionError {
        BitSet bitSet = state.allState.findSet(InlinedNodeState.class, field);
        if (bitSet == null) {
            bitSet = specializationState.findSet(InlinedNodeState.class, field);
        }
        if (bitSet == null) {
            throw new AssertionError("Bits not contained.");
        }
        return bitSet;
    }

    private CodeVariableElement createStateUpdaterField(SpecializationData specialization, MultiStateBitSet specializationState, InlineFieldData field,
                    List<Element> specializationClassElements) {
        BitSet bitSet = state.allState.findSet(InlinedNodeState.class, field);
        TypeMirror nodeType;
        String updaterFieldName;

        if (bitSet == null) {
            bitSet = findInlinedState(specializationState, field);
            if (bitSet == null) {
                throw new AssertionError("Inlined bits not contained.");
            }
            nodeType = createSpecializationClassReferenceType(specialization);
            updaterFieldName = ElementUtils.createConstantName(specialization.getId() + "_" + specialization.getNode().getNodeId() + "_" + bitSet.getName()) + "_UPDATER";
        } else {
            nodeType = null;
            updaterFieldName = ElementUtils.createConstantName(bitSet.getName()) + (specialization != null ? "_" + specialization.getNode().getNodeId() : "") + "_UPDATER";
        }

        CodeVariableElement var = nodeConstants.updaterReferences.get(updaterFieldName);
        if (var == null) {
            var = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), field.getFieldType(), updaterFieldName);
            CodeTreeBuilder b = var.createInitBuilder();
            b.startStaticCall(field.getFieldType(), "create");
            if (nodeType == null) {
                b.startStaticCall(context.getType(MethodHandles.class), "lookup").end();
            } else {
                b.tree(createLookupNodeType(nodeType, specializationClassElements));
            }
            b.doubleQuote(bitSet.getName() + "_");
            b.end();
            nodeConstants.updaterReferences.put(updaterFieldName, var);
        }
        return var;
    }

    private void generateStatisticsFields(CodeTypeElement clazz) {
        if (isGenerateStatistics()) {
            CodeTreeBuilder b;
            ArrayType stringArray = new ArrayCodeTypeMirror(context.getType(String.class));
            b = clazz.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), stringArray, "SPECIALIZATION_NAMES")).createInitBuilder();
            b.startNewArray(stringArray, null);
            for (SpecializationData specialization : node.getReachableSpecializations()) {
                if (specialization.getMethod() == null) {
                    continue;
                }
                b.doubleQuote(specialization.getMethodName());
            }
            b.end();

            b = clazz.add(new CodeVariableElement(modifiers(PRIVATE, FINAL), types.SpecializationStatistics_NodeStatistics, "statistics_")).createInitBuilder();
            b.startStaticCall(types.SpecializationStatistics_NodeStatistics, "create").string("this").string("SPECIALIZATION_NAMES").end();
        }
    }

    private boolean isGenerateAOT() {
        return primaryNode && node.isGenerateAOT();
    }

    private boolean isGenerateStatistics() {
        return generatorMode == GeneratorMode.DEFAULT && primaryNode && node.isGenerateStatistics();
    }

    private boolean isGenerateIntrospection() {
        return generatorMode == GeneratorMode.DEFAULT && primaryNode && node.isGenerateIntrospection();
    }

    private static boolean isNodeArray(TypeMirror type) {
        if (type == null) {
            return false;
        }
        return type.getKind() == TypeKind.ARRAY && isAssignable(((ArrayType) type).getComponentType(), ProcessorContext.getInstance().getTypes().NodeInterface);
    }

    private static void addCompilationFinalAnnotation(CodeVariableElement field, int dimensions, boolean adopt) {
        TypeMirror type = field.getType();
        if (adopt && isAssignable(type, types().NodeInterface)) {
            field.getAnnotationMirrors().add(new CodeAnnotationMirror(types().Node_Child));
        } else if (adopt && isNodeArray(type)) {
            field.getAnnotationMirrors().add(new CodeAnnotationMirror(types().Node_Children));
        } else {
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
    }

    private static void addCompilationFinalAnnotation(CodeVariableElement field, int dimensions) {
        addCompilationFinalAnnotation(field, dimensions, false);
    }

    /*
     * Shared caches that may be never default need a wrapper to be safely initialized on mmultiple
     * threads.
     */
    private static boolean useCacheClass(SpecializationData specialization, CacheExpression cache) {
        return specialization == null && cache.getSharedGroup() != null && !cache.isNeverDefault() && !cache.isEncodedEnum();
    }

    /* Specialization class needs to be a Node in such a case. */
    private static boolean specializationClassIsNode(SpecializationData specialization) {
        for (CacheExpression cache : specialization.getCaches()) {
            if (cache.getInlinedNode() != null) {
                /*
                 * Unfortunately inline fields only work with nodes.
                 */
                return true;
            }
            if (!canCacheBeStoredInSpecialializationClass(cache)) {
                continue;
            }
            TypeMirror type = cache.getParameter().getType();
            if (isAssignable(type, types().NodeInterface)) {
                return true;
            } else if (isNodeArray(type)) {
                return true;
            }
        }
        return false;
    }

    private List<SpecializationData> getFallbackSpecializations() {
        List<SpecializationData> specializations = new ArrayList<>(node.getReachableSpecializations());
        for (ListIterator<SpecializationData> iterator = specializations.listIterator(); iterator.hasNext();) {
            SpecializationData specialization = iterator.next();
            if (specialization.isFallback()) {
                iterator.remove();
            } else if (!specialization.isReachesFallback()) {
                iterator.remove();
            }
        }
        return specializations;
    }

    private List<GuardExpression> getFallbackGuards() {
        List<GuardExpression> fallbackState = new ArrayList<>();
        List<SpecializationData> specializations = getFallbackSpecializations();
        for (SpecializationData specialization : specializations) {
            for (GuardExpression guard : specialization.getGuards()) {
                if (guardNeedsStateBit(specialization, guard)) {
                    fallbackState.add(guard);
                }
            }
        }
        return fallbackState;
    }

    private List<TypeGuard> getFallbackImplicitCastGuards() {
        List<TypeGuard> fallbackState = new ArrayList<>();
        List<SpecializationData> specializations = getFallbackSpecializations();
        for (SpecializationData specialization : specializations) {
            fallbackState.addAll(specialization.getImplicitTypeGuards());
        }
        return fallbackState;
    }

    private Element createFallbackGuard(boolean inlined) {
        boolean frameUsed = false;

        List<SpecializationData> specializations = getFallbackSpecializations();
        for (SpecializationData specialization : specializations) {
            if (specialization.isFrameUsedByGuard()) {
                frameUsed = true;
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

        multiState.createLoad(frameState, collectFallbackState());

        multiState.addParametersTo(frameState, method);
        frameState.addParametersTo(method, Integer.MAX_VALUE, FRAME_VALUE);

        frameState.setInlinedNode(inlined);

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
            multiState.removeParametersFrom(method);
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

        // really not worth finding out whether we need a static method here
        GeneratorUtils.mergeSuppressWarnings(method, "static-method");
        return method;
    }

    private DSLExpression substituteContextReference(Call call) {
        ClassLiteral literal = (ClassLiteral) call.getParameters().get(0);
        CodeVariableElement var = createContextReferenceConstant(constants, literal.getLiteral());
        String constantName = var.getSimpleName().toString();
        Variable singleton = new Variable(null, constantName);
        singleton.setResolvedTargetType(var.asType());
        singleton.setResolvedVariable(var);
        return singleton;
    }

    private DSLExpression substituteLanguageReference(Call call) {
        ClassLiteral literal = (ClassLiteral) call.getParameters().get(0);
        CodeVariableElement var = createLanguageReferenceConstant(constants, literal.getLiteral());
        String constantName = var.getSimpleName().toString();
        Variable singleton = new Variable(null, constantName);
        singleton.setResolvedTargetType(var.asType());
        singleton.setResolvedVariable(var);
        return singleton;
    }

    public static CodeVariableElement createLanguageReferenceConstant(StaticConstants constants, TypeMirror languageType) {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        String constantName = ElementUtils.createConstantName(ElementUtils.getSimpleName(languageType) + "Lref");
        TypeElement languageReference = (TypeElement) types.TruffleLanguage_LanguageReference.asElement();
        DeclaredCodeTypeMirror constantType = new DeclaredCodeTypeMirror(languageReference, Arrays.asList(languageType));
        return lookupConstant(constants.languageReferences, constantName, (name) -> {
            CodeVariableElement newVar = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), constantType, name);
            newVar.createInitBuilder().startStaticCall(languageReference.asType(), "create").typeLiteral(languageType).end();
            return newVar;
        });
    }

    public static CodeVariableElement createContextReferenceConstant(StaticConstants constants, TypeMirror languageType) {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        String constantName = ElementUtils.createConstantName(ElementUtils.getSimpleName(languageType) + "Cref");
        TypeElement contextReference = (TypeElement) types.TruffleLanguage_ContextReference.asElement();
        DeclaredCodeTypeMirror constantType = new DeclaredCodeTypeMirror(contextReference, Arrays.asList(NodeParser.findContextTypeFromLanguage(languageType)));
        return lookupConstant(constants.languageReferences, constantName, (name) -> {
            CodeVariableElement newVar = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), constantType, name);
            newVar.createInitBuilder().startStaticCall(contextReference.asType(), "create").typeLiteral(languageType).end();
            return newVar;
        });
    }

    private DSLExpression substituteLibraryCall(Call call) {
        ClassLiteral literal = (ClassLiteral) call.getParameters().get(0);
        CodeVariableElement var = createLibraryConstant(constants, literal.getLiteral());
        String constantName = var.getSimpleName().toString();
        Variable singleton = new Variable(null, constantName);
        singleton.setResolvedTargetType(var.asType());
        singleton.setResolvedVariable(var);
        return singleton;
    }

    public static CodeVariableElement createLibraryConstant(StaticConstants constants, TypeMirror libraryTypeMirror) {
        TypeElement libraryType = ElementUtils.castTypeElement(libraryTypeMirror);
        String constantName = ElementUtils.createConstantName(libraryType.getSimpleName().toString());
        TypeElement resolvedLibrary = (TypeElement) ProcessorContext.getInstance().getTypes().LibraryFactory.asElement();
        DeclaredCodeTypeMirror constantType = new DeclaredCodeTypeMirror(resolvedLibrary, Arrays.asList(libraryType.asType()));
        return lookupConstant(constants.libraries, constantName, (name) -> {
            CodeVariableElement newVar = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), constantType, name);
            newVar.createInitBuilder().startStaticCall(resolvedLibrary.asType(), "resolve").typeLiteral(libraryType.asType()).end();
            return newVar;
        });
    }

    private static CodeVariableElement lookupConstant(Map<String, CodeVariableElement> constants, String constantName, Function<String, CodeVariableElement> factory) {
        String useConstantName = constantName + "_";
        while (true) {
            CodeVariableElement prev = constants.get(useConstantName);
            CodeVariableElement var = factory.apply(useConstantName);
            if (prev == null) {
                constants.put(useConstantName, var);
                return var;
            } else {
                if (ElementUtils.variableEquals(prev, var)) {
                    return prev;
                }
            }
            // retry with new constant name
            useConstantName = useConstantName + "_";
        }
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
                Parameter specializedParameter = specialization.findParameter(node.getChildExecutions().get(i));
                TypeMirror specializedType;
                if (specializedParameter == null) {
                    specializedType = evaluatedType;
                } else {
                    specializedType = specializedParameter.getType();
                }
                if (typeSystem.lookupCast(evaluatedType, specializedType) == null && !isSubtypeBoxed(context, specializedType, evaluatedType) &&
                                !isSubtypeBoxed(context, evaluatedType, specializedType)) {
                    // unreachable type parameter for the execute signature. For example evaluated
                    // int and specialized long. This does not account for reachability.
                    continue outer;
                }
            }

            TypeMirror executeReturnType = forType.getReturnType();
            TypeMirror specializationReturnType = specialization.lookupBoxingOverloadReturnType(forType);
            if (isReturnTypeCompatible(executeReturnType, specializationReturnType)) {
                filteredSpecializations.add(specialization);
            }
        }

        return filteredSpecializations;
    }

    private boolean isReturnTypeCompatible(TypeMirror executeReturnType, TypeMirror specializationReturnType) {
        return isVoid(executeReturnType) || isVoid(specializationReturnType) || //
                        isSubtypeBoxed(context, specializationReturnType, executeReturnType) ||
                        isSubtypeBoxed(context, executeReturnType, specializationReturnType);
    }

    private List<SpecializationData> filterImplementedSpecializations(List<SpecializationData> specializations, ExecutableTypeData forType) {
        List<SpecializationData> filteredSpecializations = new ArrayList<>();
        TypeMirror returnType = boxType(context, forType.getReturnType());

        for (SpecializationData specialization : specializations) {
            TypeMirror specializationReturnType = boxType(context, specialization.lookupBoxingOverloadReturnType(forType));
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

    public CodeExecutableElement createExecuteMethod(CodeTypeElement clazz, CodeExecutableElement baseMethod,
                    List<SpecializationData> specializations, boolean skipStateChecks) {
        final List<SpecializationData> allSpecializations = specializations;
        int signatureSize = node.getPolymorphicExecutable().getSignatureParameters().size();
        ExecutableTypeData type = new ExecutableTypeData(node, baseMethod, signatureSize, List.of(node.getFrameType()), false, true);

        List<SpecializationData> implementedSpecializations = allSpecializations;
        CodeExecutableElement method = createExecuteMethod(type);
        FrameState frameState = FrameState.load(this, type, Integer.MAX_VALUE, NodeExecutionMode.FAST_PATH, method);
        if (type.getMethod() == null) {
            frameState.addParametersTo(method, Integer.MAX_VALUE, FRAME_VALUE);
        } else {
            renameOriginalParameters(type, method, frameState);
        }
        clazz.add(method);
        CodeTreeBuilder builder = method.createBuilder();
        SpecializationGroup group = SpecializationGroup.create(implementedSpecializations);
        frameState.setSkipStateChecks(skipStateChecks);
        builder.tree(createFastPath(builder, implementedSpecializations, group, type, frameState));
        return method;
    }

    private CodeExecutableElement createExecute(CodeTypeElement clazz, ExecutableTypeData type, List<ExecutableTypeData> delegateableTypes, boolean inlined) {
        final List<SpecializationData> allSpecializations = node.getReachableSpecializations();
        final List<SpecializationData> compatibleSpecializations = filterCompatibleSpecializations(allSpecializations, type);
        List<SpecializationData> implementedSpecializations;
        if (delegateableTypes.isEmpty()) {
            implementedSpecializations = compatibleSpecializations;
        } else {
            implementedSpecializations = filterImplementedSpecializations(compatibleSpecializations, type);
        }

        CodeExecutableElement method = createExecuteMethod(type);
        FrameState frameState = FrameState.load(this, type, Integer.MAX_VALUE, NodeExecutionMode.FAST_PATH, method);
        frameState.setInlinedNode(inlined);
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
        SpecializationData fallback = node.getFallbackSpecialization();
        TypeMirror returnType = fallback.getReturnType().getType();
        List<TypeMirror> parameterTypes = new ArrayList<>();
        for (Parameter parameter : fallback.getSignatureParameters()) {
            parameterTypes.add(parameter.getType());
        }
        ExecutableTypeData forType = new ExecutableTypeData(node, returnType, "uncached", null, parameterTypes);
        return createUncachedExecute(forType);
    }

    private CodeExecutableElement createUncachedExecute(ExecutableTypeData forType) {
        final Collection<SpecializationData> allSpecializations = node.computeUncachedSpecializations(node.getReachableSpecializations());
        final List<SpecializationData> compatibleSpecializations = filterCompatibleSpecializations(allSpecializations, forType);

        CodeExecutableElement method = createExecuteMethod(forType);
        FrameState frameState = FrameState.load(this, forType, Integer.MAX_VALUE, NodeExecutionMode.UNCACHED, method);
        if (forType.getMethod() == null) {
            frameState.addParametersTo(method, Integer.MAX_VALUE, FRAME_VALUE);
        } else {
            renameOriginalParameters(forType, method, frameState);
        }

        CodeTreeBuilder builder = method.createBuilder();

        int effectiveEvaluatedCount = forType.getEvaluatedCount();
        while (effectiveEvaluatedCount < node.getExecutionCount()) {
            NodeExecutionData childExecution = node.getChildExecutions().get(effectiveEvaluatedCount);
            if (childExecution.getChild() == null || !childExecution.getChild().isAllowUncached()) {
                break;
            }

            ExecutableTypeData type = childExecution.getChild().findAnyGenericExecutableType(context);
            LocalVariable local = frameState.createValue(childExecution, type.getReturnType());

            CodeTree init = callUncachedChildExecuteMethod(childExecution, type, frameState);
            builder.declaration(type.getReturnType(), local.getName(), init);

            frameState.set(childExecution, local);
            effectiveEvaluatedCount++;
        }

        if (forType.getMethod() != null) {
            method.getModifiers().addAll(forType.getMethod().getModifiers());
            method.getModifiers().remove(Modifier.ABSTRACT);
        }

        boolean isExecutableInUncached = effectiveEvaluatedCount != node.getExecutionCount() && !node.getChildren().isEmpty();
        if (isExecutableInUncached) {
            builder.tree(GeneratorUtils.createShouldNotReachHere("This execute method cannot be used for uncached node versions as it requires child nodes to be present. " +
                            "Use an execute method that takes all arguments as parameters."));
        } else {
            if (forType.isReachableForRuntimeCompilation()) {
                GeneratorUtils.addBoundaryOrTransferToInterpreter(method, builder);
            }
            generateTraceOnEnterCall(builder, frameState);
            generateTraceOnExceptionStart(builder);
            SpecializationGroup group = SpecializationGroup.create(compatibleSpecializations);
            FrameState originalFrameState = frameState.copy();
            builder.tree(visitSpecializationGroup(builder, null, group, forType, frameState, allSpecializations));
            if (group.hasFallthrough()) {
                builder.tree(createThrowUnsupported(builder, originalFrameState, true));
            }
            generateTraceOnExceptionEnd(builder);
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
            for (SpecializationData specialization : node.getReachableSpecializations()) {
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
                                    filterCompatibleSpecializations(node.getReachableSpecializations(), delegateType), delegateType);
                    coversAllSpecializations = delegateSpecializations.size() == node.getReachableSpecializations().size();
                    if (!coversAllSpecializations) {
                        builder.tree(multiState.createLoadFastPath(frameState, delegateSpecializations));
                        elseIf = delegateBuilder.startIf(elseIf);

                        delegateBuilder.startGroup();
                        CodeTree tree = multiState.createContainsOnly(frameState, 0, -1,
                                        StateQuery.create(SpecializationActive.class, delegateSpecializations),
                                        StateQuery.create(SpecializationActive.class, node.getReachableSpecializations()));
                        if (!tree.isEmpty()) {
                            delegateBuilder.tree(tree);
                            delegateBuilder.string(" && ");
                        }

                        delegateBuilder.tree(multiState.createIsNotAny(frameState, StateQuery.create(SpecializationActive.class, node.getReachableSpecializations())));
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
            coversAllSpecializations = notImplemented.size() == node.getReachableSpecializations().size();
            if (!coversAllSpecializations) {
                builder.tree(multiState.createLoadFastPath(frameState, notImplemented));
                elseIf = delegateBuilder.startIf(elseIf);
                delegateBuilder.tree(multiState.createContains(frameState, StateQuery.create(SpecializationActive.class, notImplemented))).end();
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
            builder.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

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

    private CodeExecutableElement createExecuteAndSpecialize(boolean inlined) {
        if (!node.needsSpecialize()) {
            return null;
        }
        String frame = null;
        if (needsFrameToExecute(node.getReachableSpecializations())) {
            frame = FRAME_VALUE;
        }
        TypeMirror returnType = executeAndSpecializeType.getReturnType();
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE), returnType, createExecuteAndSpecializeName());
        final FrameState frameState = FrameState.load(this, NodeExecutionMode.SLOW_PATH, method);
        frameState.setInlinedNode(inlined);
        frameState.addParametersTo(method, Integer.MAX_VALUE, frame);

        final CodeTreeBuilder builder = method.createBuilder();
        ReportPolymorphismAction reportPolymorphismAction = reportPolymorphismAction(node, node.getReachableSpecializations());

        builder.tree(multiState.createLoadSlowPath(frameState, node.getReachableSpecializations(), false));

        if (needsAOTReset(node, sharingNodes)) {
            builder.startIf();
            builder.tree(allMultiState.createContains(frameState, AOT_PREPARED));
            builder.end().startBlock();
            builder.startStatement().startCall("this.resetAOT_");
            if (inlined) {
                builder.tree(frameState.getValue(INLINED_NODE_INDEX).createReference());
            }
            builder.end().end();
            builder.tree(multiState.createLoadSlowPath(frameState, node.getReachableSpecializations(), true));
            builder.end();
        }

        if (reportPolymorphismAction.required()) {
            generateSaveOldPolymorphismState(builder, frameState, reportPolymorphismAction);
            builder.startTryBlock();
        }

        FrameState originalFrameState = frameState.copy();
        SpecializationGroup group = createSpecializationGroups();
        CodeTree execution = visitSpecializationGroup(builder, null, group, executeAndSpecializeType, frameState, null);

        builder.tree(execution);

        if (group.hasFallthrough()) {
            builder.tree(createThrowUnsupported(builder, originalFrameState, inlined && node.isGenerateUncached()));
        }

        if (reportPolymorphismAction.required()) {
            builder.end().startFinallyBlock();
            generateCheckNewPolymorphismState(builder, frameState, reportPolymorphismAction);
            builder.end();
        }

        return method;
    }

    // Polymorphism reporting constants
    private static final String OLD_PREFIX = "old";
    private static final String COUNT_SUFIX = "Count";
    private static final String OLD_CACHE_COUNT = OLD_PREFIX + "Cache" + COUNT_SUFIX;
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

    private boolean requiresCacheCheck(ReportPolymorphismAction reportPolymorphismAction) {
        if (!reportPolymorphismAction.polymorphism) {
            return false;
        }
        for (SpecializationData specialization : node.getReachableSpecializations()) {
            if (useSpecializationClass(specialization) && specialization.getMaximumNumberOfInstances() > 1) {
                return true;
            }
        }
        return false;
    }

    private Element createCheckForPolymorphicSpecialize(ReportPolymorphismAction reportPolymorphismAction, boolean inlined) {
        TypeMirror returnType = getType(void.class);
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PRIVATE), returnType, createName(CHECK_FOR_POLYMORPHIC_SPECIALIZE));
        FrameState frameState = FrameState.load(this, NodeExecutionMode.SLOW_PATH, executable);
        frameState.setInlinedNode(inlined);
        final boolean requiresCacheCheck = requiresCacheCheck(reportPolymorphismAction);

        SpecializationData[] relevantSpecializations = getSpecalizationsForReportAction(reportPolymorphismAction);

        StateQuery specializationActive = StateQuery.create(SpecializationActive.class, relevantSpecializations);
        StateQuery specializationExcluded = StateQuery.create(SpecializationExcluded.class, relevantSpecializations);

        if (inlined) {
            executable.addParameter(frameState.getValue(INLINED_NODE_INDEX).createParameter());
        }

        List<BitSet> relevantSets = new ArrayList<>();
        for (BitSet s : multiState.getSets()) {
            if (s.contains(specializationActive, specializationExcluded)) {
                relevantSets.add(s);
            }
        }

        for (BitSet s : relevantSets) {
            executable.addParameter(new CodeVariableElement(s.getType(), getSetOldName(s)));
        }

        if (requiresCacheCheck) {
            executable.addParameter(new CodeVariableElement(getType(int.class), OLD_CACHE_COUNT));
        }

        CodeTreeBuilder builder = executable.createBuilder();

        if (reportPolymorphismAction.polymorphism) {
            builder.tree(multiState.createLoad(frameState, specializationActive, specializationExcluded));
            for (BitSet s : relevantSets) {
                builder.declaration(s.getType(), getSetNewName(s), s.createMaskedReference(frameState, specializationActive, specializationExcluded));
            }
        }
        builder.startIf();
        if (reportPolymorphismAction.polymorphism) {

            String sep = "";
            for (BitSet s : relevantSets) {
                builder.string(sep);
                builder.string("((", getSetOldName(s), " ^ ", getSetNewName(s), ") != 0)");
                sep = " || ";
            }
            if (requiresCacheCheck) {
                builder.string(" || ", OLD_CACHE_COUNT, " < ");
                builder.startCall(createName(COUNT_CACHES));
                if (inlined) {
                    builder.tree(frameState.getValue(INLINED_NODE_INDEX).createReference());
                }
                builder.end();
            }
            if (reportPolymorphismAction.megamorphism) {
                builder.string(" || ");
            }
        }
        if (reportPolymorphismAction.megamorphism) {
            String sep = "";
            for (BitSet s : relevantSets) {
                builder.string(sep);
                builder.string("(");
                builder.string("(", getSetOldName(s), " & ", s.formatMask(s.createMask(specializationActive, specializationExcluded)));
                builder.string(") == 0");
                builder.string(" && ");
                builder.tree(s.createMaskedReference(frameState, specializationActive, specializationExcluded));
                builder.string(" != 0");
                builder.string(")");
                sep = " || ";
            }
        }
        builder.end(); // if
        builder.startBlock().startStatement();
        if (inlined) {
            builder.tree(frameState.getValue(INLINED_NODE_INDEX).createReference());
        } else {
            builder.string("this");
        }
        builder.string(".");
        builder.startCall(REPORT_POLYMORPHIC_SPECIALIZE).end();

        builder.end(2); // statement, block
        return executable;
    }

    private SpecializationData[] getSpecalizationsForReportAction(ReportPolymorphismAction reportPolymorphismAction) {
        if (reportPolymorphismAction.polymorphism) {
            return node.getReachableSpecializations().stream().filter(SpecializationData::isReportPolymorphism).toArray(SpecializationData[]::new);
        } else if (reportPolymorphismAction.megamorphism) {
            return node.getReachableSpecializations().stream().filter(SpecializationData::isReportMegamorphism).toArray(SpecializationData[]::new);
        }
        return new SpecializationData[0];
    }

    private Element createCountCaches(boolean inlined) {
        TypeMirror returnType = getType(int.class);
        CodeExecutableElement executable = new CodeExecutableElement(modifiers(PRIVATE), returnType, createName(COUNT_CACHES));

        FrameState frameState = FrameState.load(this, NodeExecutionMode.SLOW_PATH, executable);
        frameState.setInlinedNode(inlined);

        if (inlined) {
            executable.addParameter(frameState.getValue(INLINED_NODE_INDEX).createParameter());
        }

        CodeTreeBuilder builder = executable.createBuilder();
        final String cacheCount = "cacheCount";
        builder.declaration(context.getType(int.class), cacheCount, "0");
        for (SpecializationData specialization : node.getReachableSpecializations().stream().filter(SpecializationData::isReportPolymorphism).toArray(SpecializationData[]::new)) {
            if (useSpecializationClass(specialization) && specialization.getMaximumNumberOfInstances() > 1) {
                builder.tree(loadSpecializationClass(frameState, specialization, false));
                CodeTree specializationClass = createGetSpecializationClass(frameState, specialization, true);
                builder.startWhile().tree(specializationClass).string(" != null");
                builder.end();
                builder.startBlock();
                builder.statement("cacheCount++");
                builder.startStatement().tree(specializationClass).string(" = ").tree(specializationClass).string(".next_").end();
                builder.end();
                builder.end();
            }
        }
        builder.startReturn().statement(cacheCount);
        return executable;
    }

    private void generateCheckNewPolymorphismState(CodeTreeBuilder builder, FrameState frameState, ReportPolymorphismAction reportPolymorphismAction) {
        SpecializationData[] relevantSpecializations = getSpecalizationsForReportAction(reportPolymorphismAction);
        StateQuery query = StateQuery.create(SpecializationActive.class, relevantSpecializations);
        builder.startIf();
        String sep = "";
        for (BitSet s : multiState.getSets()) {
            if (s.contains(query)) {
                builder.string(sep);
                builder.string(getSetOldName(s), " != 0");
                sep = " || ";
            }
        }

        builder.end();
        builder.startBlock();
        builder.startStatement();
        builder.startCall(createName(CHECK_FOR_POLYMORPHIC_SPECIALIZE));

        if (frameState.isInlinedNode()) {
            builder.tree(frameState.getValue(INLINED_NODE_INDEX).createReference());
        }
        for (BitSet s : multiState.getSets()) {
            if (s.contains(query)) {
                builder.string(getSetOldName(s));
            }
        }
        if (requiresCacheCheck(reportPolymorphismAction)) {
            builder.string(OLD_CACHE_COUNT);
        }
        builder.end().end().end(); // call, statement, block
    }

    private void generateSaveOldPolymorphismState(CodeTreeBuilder builder, FrameState frameState, ReportPolymorphismAction reportPolymorphismAction) {
        SpecializationData[] specializations = node.getReachableSpecializations().stream().filter(SpecializationData::isReportPolymorphism).toArray(SpecializationData[]::new);
        StateQuery specializationActive = StateQuery.create(SpecializationActive.class, specializations);
        StateQuery specializationExcluded = StateQuery.create(SpecializationExcluded.class, specializations);

        for (BitSet s : multiState.getSets()) {
            StateQuery localSpecializationActive = s.filter(specializationActive);
            StateQuery localExclude = s.filter(specializationExcluded);
            if (!localSpecializationActive.isEmpty() || !localExclude.isEmpty()) {
                builder.declaration(s.getType(), getSetOldName(s), s.createMaskedReference(frameState, localSpecializationActive, localExclude));
            }
        }

        if (requiresCacheCheck(reportPolymorphismAction)) {
            builder.startStatement();
            builder.type(context.getType(int.class)).string(" ", OLD_CACHE_COUNT, " = ");
            builder.startCall(createName(COUNT_CACHES));
            if (frameState.isInlinedNode()) {
                builder.tree(frameState.getValue(INLINED_NODE_INDEX).createReference());
            }
            builder.end();
            builder.end();
        }
    }

    private static String getSetOldName(BitSet bitSet) {
        return "old" + ElementUtils.firstLetterUpperCase(bitSet.getName());
    }

    private static String getSetNewName(BitSet bitSet) {
        return "new" + ElementUtils.firstLetterUpperCase(bitSet.getName());
    }

    private CodeTree createThrowUnsupported(CodeTreeBuilder parent, FrameState frameState) {
        return createThrowUnsupported(parent, frameState, false);
    }

    /**
     * Throws an UnsupportedSpecializationException, optionally outlining the allocations when
     * called from an uncached node execute method.
     */
    private CodeTree createThrowUnsupported(CodeTreeBuilder parent, FrameState frameState, boolean outlineIfPossible) {
        List<String> nodes = new ArrayList<>();
        List<LocalVariable> locals = new ArrayList<>();
        for (NodeExecutionData execution : node.getChildExecutions()) {
            NodeChildData child = execution.getChild();
            nodes.add(plugs.createNodeChildReferenceForException(this, frameState, execution, child));
            LocalVariable var = frameState.getValue(execution);
            if (var != null) {
                locals.add(var);
            }
        }

        CodeExecutableElement parentMethod = (CodeExecutableElement) parent.findMethod();
        boolean isStaticMethod = parentMethod != null && parentMethod.getModifiers().contains(STATIC);
        boolean allNodesNull = nodes.stream().allMatch("null"::equals);

        boolean outline = outlineIfPossible && parentMethod != null && allNodesNull;
        if (outline) {
            boolean hasPrimitives = locals.stream().anyMatch(local -> local.getTypeMirror().getKind().isPrimitive());
            String signatureId = locals.size() +
                            (hasPrimitives ? locals.stream().map(l -> ElementUtils.basicTypeId(l.typeMirror)).collect(Collectors.joining()) : "");
            String throwMethodName = "newUnsupportedSpecializationException" + signatureId;

            nodeConstants.addHelperMethod(throwMethodName, () -> {
                CodeExecutableElement throwMethod = new CodeExecutableElement(modifiers(PRIVATE, STATIC), types.UnsupportedSpecializationException, throwMethodName);
                String thisNodeParamName = "thisNode_";
                throwMethod.addParameter(new LocalVariable(types.Node, thisNodeParamName, null).createParameter());
                for (LocalVariable local : locals) {
                    TypeMirror erasedType = ElementUtils.isPrimitive(local.getTypeMirror()) ? local.getTypeMirror() : context.getType(Object.class);
                    throwMethod.addParameter(local.newType(erasedType).createParameter());
                }

                CodeTreeBuilder builder = throwMethod.createBuilder();
                GeneratorUtils.addBoundaryOrTransferToInterpreter(throwMethod, builder);
                builder.startReturn();
                newUnsupportedSpecializationException(builder, nodes, locals, thisNodeParamName, var -> CodeTreeBuilder.singleString(var.getName()));
                builder.end();
                return throwMethod;
            });

            CodeTreeBuilder callBuilder = parent.create();
            callBuilder.startThrow().startCall(throwMethodName);
            callBuilder.string(isStaticMethod ? "null" : "this");
            callBuilder.trees(locals.stream().map(var -> var.createReference()).toArray(CodeTree[]::new));
            callBuilder.end().end();
            return callBuilder.build();
        } else {
            CodeTreeBuilder builder = parent.create();
            builder.startThrow();
            newUnsupportedSpecializationException(builder, nodes, locals, isStaticMethod ? "null" : "this", var -> var.createReference());
            builder.end();
            return builder.build();
        }
    }

    private void newUnsupportedSpecializationException(CodeTreeBuilder builder, List<String> nodes, List<LocalVariable> locals, String nodeRef, Function<LocalVariable, CodeTree> localMapper) {
        builder.startNew(types.UnsupportedSpecializationException);
        builder.string(nodeRef);
        boolean allNodesNull = nodes.stream().allMatch("null"::equals);
        if (allNodesNull) {
            builder.nullLiteral();
        } else {
            builder.startNewArray(new ArrayCodeTypeMirror(types.Node), null);
            builder.trees(nodes.stream().map(CodeTreeBuilder::singleString).toArray(CodeTree[]::new));
            builder.end();
        }
        builder.trees(locals.stream().map(localMapper).toArray(CodeTree[]::new));
        builder.end();
    }

    @SuppressWarnings("unused")
    String createNodeChildReferenceForException(final FrameState frameState, NodeExecutionData execution, NodeChildData child) {
        if (child != null && !frameState.getMode().isUncached()) {
            return accessNodeField(execution);
        } else {
            return "null";
        }
    }

    private CodeTree createFastPath(CodeTreeBuilder parent, List<SpecializationData> allSpecializations, SpecializationGroup originalGroup, final ExecutableTypeData currentType,
                    FrameState frameState) {
        final CodeTreeBuilder builder = parent.create();

        boolean needsRewrites = node.needsSpecialize();
        if (needsRewrites) {
            builder.tree(multiState.createLoadFastPath(frameState, allSpecializations));
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
            StateQuery allSpecializationQuery = StateQuery.create(SpecializationActive.class, allSpecializations);

            boolean elseIf = false;
            for (BoxingSplit split : boxingSplits) {
                elseIf = builder.startIf(elseIf);
                builder.startGroup();
                List<SpecializationData> specializations = split.group.collectSpecializations();
                StateQuery specializationQuery = StateQuery.create(SpecializationActive.class, specializations);

                CodeTree tree = multiState.createContainsOnly(frameState, 0, -1, specializationQuery, allSpecializationQuery);
                if (!tree.isEmpty()) {
                    builder.tree(tree);
                    builder.string(" && ");
                }
                builder.tree(multiState.createIsNotAny(frameState, allSpecializationQuery));
                builder.end();
                builder.end().startBlock();
                builder.tree(wrapInAMethod(builder, specializations, split.group, originalFrameState, split.getName(),
                                executeFastPathGroup(builder, frameState.copy(), currentType, split.group, sharedExecutes, specializations)));
                builder.end();
            }

            builder.startElseBlock();
            builder.tree(wrapInAMethod(builder, allSpecializations, originalGroup, originalFrameState, "generic",
                            executeFastPathGroup(builder, frameState, currentType, originalGroup, sharedExecutes, null)));
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

    private CodeTree wrapInAMethod(CodeTreeBuilder parent, List<SpecializationData> specializations, SpecializationGroup group, FrameState frameState,
                    String suffix, CodeTree codeTree) {
        CodeExecutableElement parentMethod = (CodeExecutableElement) parent.findMethod();
        CodeTypeElement parentClass = (CodeTypeElement) parentMethod.getEnclosingElement();
        String name = parentMethod.getSimpleName().toString() + "_" + suffix + (boxingSplitIndex++);
        CodeExecutableElement method = parentClass.add(new CodeExecutableElement(modifiers(Modifier.PRIVATE), parentMethod.getReturnType(), name));

        multiState.addParametersTo(frameState, method);
        frameState.addParametersTo(method, Integer.MAX_VALUE, FRAME_VALUE);
        CodeTreeBuilder builder = method.createBuilder();

        /*
         * We might modify state bits in the code, so we reassign it to a variable.
         */
        int parameterIndex = 0;
        for (BitSet set : multiState.getSets()) {
            LocalVariable local = frameState.get(set.getName());
            if (local != null && MultiStateBitSet.isRelevantForFastPath(frameState, set, specializations)) {
                CodeVariableElement var = (CodeVariableElement) method.getParameters().get(parameterIndex);
                String oldName = var.getName();
                String newName = var.getName() + "__";
                var.setName(newName);
                builder.declaration(var.getType(), oldName, newName);
                parameterIndex++;
            }
        }

        builder.tree(codeTree);
        method.getThrownTypes().addAll(parentMethod.getThrownTypes());
        addExplodeLoop(builder, group);

        CodeTreeBuilder parentBuilder = parent.create();
        parentBuilder.startReturn();
        parentBuilder.startCall(method.getSimpleName().toString());
        multiState.addReferencesTo(frameState, parentBuilder);
        frameState.addReferencesTo(parentBuilder, FRAME_VALUE);
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

        generateTraceOnEnterCall(builder, frameState);
        generateTraceOnExceptionStart(builder);

        if (needsAOTReset(node, sharingNodes)) {
            builder.startIf();
            builder.startStaticCall(ElementUtils.findMethod(types.CompilerDirectives, "inInterpreter")).end();
            builder.string(" && ");
            builder.tree(allMultiState.createContains(frameState, AOT_PREPARED));
            builder.end().startBlock();

            if (!node.needsSpecialize()) {
                builder.startStatement().startCall("this.resetAOT_");
                if (frameState.isInlinedNode()) {
                    builder.tree(frameState.getValue(INLINED_NODE_INDEX).createReference());
                }
                builder.end().end();
            } else {
                builder.tree(createCallExecuteAndSpecialize(builder, currentType, originalFrameState));
            }

            builder.end();
        }

        builder.tree(visitSpecializationGroup(builder, null, group, currentType, frameState, allowedSpecializations));

        if (group.hasFallthrough()) {
            builder.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            builder.tree(createCallExecuteAndSpecialize(builder, currentType, originalFrameState));

        }
        generateTraceOnExceptionEnd(builder);
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
                guards.add(new TypeGuard(typeSystem, p.getType(), index));
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

            TypeGuard eliminatedGuard = findBoxingEliminationGuard(group, execution);
            if (eliminatedGuard != null) {
                // we can optimize the type guard away by executing it
                group.getTypeGuards().remove(eliminatedGuard);
                targetType = eliminatedGuard.getType();
            } else {
                targetType = execution.getChild().findAnyGenericExecutableType(context).getReturnType();
            }

            var = frameState.createValue(execution, targetType).nextName();

            LocalVariable fallbackVar;
            List<TypeMirror> originalSourceTypes = new ArrayList<>(typeSystem.lookupSourceTypes(targetType));
            List<TypeMirror> sourceTypes = resolveOptimizedImplicitSourceTypes(execution, targetType);
            if (sourceTypes.size() > 1) {
                TypeGuard typeGuard = new TypeGuard(typeSystem, targetType, execution.getIndex());
                TypeMirror generic = node.getPolymorphicExecutable().getParameterTypeOrDie(execution);
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

                    CodeTree containsOnly = multiState.createContainsOnly(frameState, originalSourceTypes.indexOf(sType), 1,
                                    StateQuery.create(ImplicitCastState.class, typeGuard),
                                    StateQuery.create(ImplicitCastState.class, typeGuard));
                    if (!containsOnly.isEmpty()) {
                        accessBuilder.tree(containsOnly);
                        accessBuilder.string(" && ");
                    }
                    accessBuilder.tree(multiState.createIsNotAny(frameState, StateQuery.create(SpecializationActive.class, node.getReachableSpecializations())));

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

    private TypeGuard findBoxingEliminationGuard(SpecializationGroup group, NodeExecutionData execution) {
        if (!boxingEliminationEnabled) {
            return null;
        }
        TypeGuard guard = group.getTypeGuards().stream().filter((g) -> g.getSignatureIndex() == execution.getIndex()).findFirst().orElse(null);
        if (guard == null) {
            return null;
        }
        NodeExecutionData currentExecution = node.getChildExecutions().get(guard.getSignatureIndex());
        if (plugs.canBoxingEliminateType(currentExecution, guard.getType())) {
            return guard;
        }
        return null;
    }

    private CodeTree createAssignExecuteChild(FrameState originalFrameState, FrameState frameState, CodeTreeBuilder parent, NodeExecutionData execution, ExecutableTypeData forType,
                    LocalVariable targetValue) {
        CodeTreeBuilder builder = parent.create();

        ChildExecutionResult executeChild = plugs.createExecuteChild(this, builder, originalFrameState, frameState, execution, targetValue);
        builder.tree(createTryExecuteChild(targetValue, executeChild.code, true, executeChild.throwsUnexpectedResult));
        builder.end();
        if (executeChild.throwsUnexpectedResult) {
            builder.startCatchBlock(types.UnexpectedResultException, "ex");
            builder.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            FrameState slowPathFrameState = originalFrameState.copy(NodeExecutionMode.SLOW_PATH);
            slowPathFrameState.setValue(execution, targetValue.makeGeneric(context).accessWith(CodeTreeBuilder.singleString("ex.getResult()")));

            ExecutableTypeData delegateType = node.getGenericExecutableType(forType);
            boolean found = false;
            for (NodeExecutionData otherExecution : node.getChildExecutions()) {
                if (found) {
                    LocalVariable childEvaluatedValue = slowPathFrameState.createValue(otherExecution, node.getGenericType(otherExecution));
                    builder.tree(createAssignExecuteChild(slowPathFrameState.copy(), slowPathFrameState, builder, otherExecution, delegateType, childEvaluatedValue));
                    slowPathFrameState.setValue(otherExecution, childEvaluatedValue);
                } else {
                    // skip forward already evaluated
                    found = execution == otherExecution;
                }
            }

            if (node.needsSpecialize()) {
                builder.tree(createCallExecuteAndSpecialize(builder, forType, slowPathFrameState));
            } else {
                LocalVariable slowPathValue = slowPathFrameState.getValue(execution);
                builder.startTryBlock();
                builder.startStatement();
                builder.string(targetValue.getName()).string(" = ");
                builder.tree(expect(slowPathValue.getTypeMirror(), targetValue.getTypeMirror(), slowPathValue.createReference()));
                builder.end();
                builder.end().startCatchBlock(types.UnexpectedResultException, "e");
                builder.tree(createThrowUnsupported(builder, slowPathFrameState));
                builder.end();

            }

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

    public ChildExecutionResult createExecuteChild(CodeTreeBuilder parent, FrameState originalFrameState, FrameState frameState, NodeExecutionData execution, LocalVariable target) {

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
            if (child.needsGeneratedField() && !child.isImplicit()) {
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
            CodeTree accessor;
            if (execution.getChild().isImplicit()) {
                accessor = DSLExpressionGenerator.write(execution.getChild().getImplicitCreateExpression(), null, null);
            } else {
                CodeTreeBuilder accessorBuilder = builder.create();
                accessorBuilder.string(name);

                if (execution.hasChildArrayIndex()) {
                    accessorBuilder.string("[").string(String.valueOf(execution.getChildArrayIndex())).string("]");
                }

                accessor = accessorBuilder.build();
            }

            if (createCast != null && execution.getChild().getCardinality().isOne()) {
                accessor = callMethod(null, null, createCast.getMethod(), accessor);
            }

            if (execution.hasChildArrayIndex() && !execution.getChild().isImplicit()) {
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
            for (SpecializationData boxingOverload : specialization.getBoxingOverloads()) {
                specializedReturnTypes.add(boxingOverload.getReturnType().getType());
            }
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
                if (child.isAllowUncached()) {
                    CodeTree uncachedNode = DSLExpressionGenerator.write(child.getUncachedExpression(), null, null);
                    builder.startReturn().tree(uncachedNode).end();
                } else {
                    GeneratorUtils.addBoundaryOrTransferToInterpreter(method, null);
                    builder.tree(GeneratorUtils.createShouldNotReachHere("This getter method cannot be used for uncached node versions as it requires child nodes to be present."));
                }
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

    private TypeMirror getType(Class<?> clazz) {
        return context.getType(clazz);
    }

    static CodeVariableElement createNodeField(Modifier visibility, TypeMirror type, String name, DeclaredType annotationClass, Modifier... modifiers) {
        CodeVariableElement childField = new CodeVariableElement(modifiers(modifiers), type, name);
        if (annotationClass != null) {
            if (annotationClass == ProcessorContext.getInstance().getTypes().CompilerDirectives_CompilationFinal) {
                addCompilationFinalAnnotation(childField, 0);
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

    private CodeTree callUncachedChildExecuteMethod(NodeExecutionData execution, ExecutableTypeData method, FrameState frameState) {
        assert execution.getChild().isAllowUncached();
        CodeTree uncachedNode = DSLExpressionGenerator.write(execution.getChild().getUncachedExpression(), null, null);
        return callMethod(frameState, uncachedNode, method.getMethod(), bindExecuteMethodParameters(execution, method, frameState));
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
        return SpecializationGroup.create(node.getReachableSpecializations());
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

        for (VariableElement arg : plugs.additionalArguments()) {
            executable.addParameter(arg);
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
            // unexpected result is not compatible
            return false;
        }

        if (isSubtypeBoxed(context, executeAndSpecializeType.getReturnType(), executedType.getReturnType())) {
            for (SpecializationData specialization : node.getReachableSpecializations()) {
                SpecializationData overload = specialization.lookupBoxingOverload(executedType);
                if (overload != null && overload.hasUnexpectedResultRewrite()) {
                    return true;
                }
            }

            // generic does not support boxed
            return false;
        } else {
            return true;
        }
    }

    private StateQuery[] collectFallbackState() {
        StateQuery fallbackActive = StateQuery.create(SpecializationActive.class, getFallbackSpecializations());
        StateQuery fallbackGuardsActive = StateQuery.create(GuardActive.class, getFallbackGuards());
        StateQuery fallbackImplicitCasts = StateQuery.create(ImplicitCastState.class, getFallbackImplicitCastGuards());
        return new StateQuery[]{fallbackActive, fallbackGuardsActive, fallbackImplicitCasts};
    }

    private CodeTree createFastPathExecute(CodeTreeBuilder parent, final ExecutableTypeData forType, SpecializationData specialization, FrameState frameState) {
        CodeTreeBuilder builder = parent.create();
        int ifCount = 0;
        if (specialization.isFallback()) {

            if (fallbackNeedsState) {
                builder.tree(multiState.createLoad(frameState, collectFallbackState()));
            }
            builder.startIf().startCall(createFallbackName());
            if (fallbackNeedsState) {
                multiState.addReferencesTo(frameState, builder, collectFallbackState());
            }
            if (fallbackNeedsFrame) {
                if (frameState.get(FRAME_VALUE) != null) {
                    builder.string(FRAME_VALUE);
                } else {
                    builder.nullLiteral();
                }
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

        Set<String> suppressed = TruffleSuppressedWarnings.getWarnings(specialization.getMethod());
        if (!suppressed.isEmpty()) {
            suppressed.retainAll(Arrays.asList("deprecated", "all"));
            GeneratorUtils.mergeSuppressWarnings(frameState.method, suppressed.toArray(new String[suppressed.size()]));
        }

        ExecutableElement targetMethod = specialization.getMethod();
        if (targetMethod == null) {
            builder.tree(createThrowUnsupported(builder, frameState));
        } else {
            CodeTree[] bindings = new CodeTree[targetMethod.getParameters().size()];
            TypeMirror[] bindingTypes = new TypeMirror[targetMethod.getParameters().size()];
            for (int i = 0; i < bindings.length; i++) {
                Parameter parameter = specialization.findByVariable(targetMethod.getParameters().get(i));
                if (parameter == null) {
                    // synthetic parameter, may happen for optional signature parameters
                    continue;
                }
                if (parameter.getSpecification().isCached()) {
                    CacheExpression cache = specialization.findCache(parameter);
                    LocalVariable var = frameState.get(createFieldName(specialization, cache));
                    if (var != null) {
                        bindings[i] = var.createReference();
                    } else {
                        bindings[i] = createCacheAccess(frameState, specialization, cache, null);
                    }
                    bindingTypes[i] = parameter.getType();
                } else {
                    LocalVariable variable = bindExpressionVariable(frameState, specialization, parameter);
                    if (variable != null) {
                        bindings[i] = createParameterReference(variable, specialization.getMethod(), i);
                        bindingTypes[i] = variable.getTypeMirror();
                    } else {
                        bindingTypes[i] = parameter.getType();
                    }
                }
            }

            if (isGenerateStatistics()) {
                CodeTreeBuilder statistics = builder.create();
                statistics.startStatement();
                statistics.startCall("statistics_", "acceptExecute");
                statistics.string(String.valueOf(specialization.getIntrospectionIndex()));
                for (int i = 0; i < bindings.length; i++) {
                    Parameter parameter = specialization.getParameters().get(i);
                    if (parameter.getSpecification().isSignature()) {
                        TypeMirror type = bindingTypes[i];
                        if (ElementUtils.isFinal(type)) {
                            statistics.typeLiteral(type);
                        } else {
                            statistics.startCall("statistics_", "resolveValueClass");
                            statistics.tree(bindings[i]);
                            statistics.end();
                        }
                    }
                }
                statistics.end(); // call
                statistics.end(); // statement

                builder.tree(statistics.build());
            }

            if (frameState.isInlinedNode() && !substituteNodeWithSpecializationClass(specialization)) {

                List<CodeTree> usedFields = new ArrayList<>();
                for (CacheExpression cache : specialization.getCaches()) {

                    if (cache.getSharedGroup() != null) {
                        // shared caches are validated at the beginning of the execute
                        continue;
                    }

                    if (cache.getInlinedNode() != null) {

                        for (InlineFieldData field : cache.getInlinedNode().getFields()) {
                            CodeTreeBuilder inner = builder.create();
                            if (field.isState()) {
                                BitSet bitSet = state.activeState.findSet(InlinedNodeState.class, field);
                                if (bitSet != null) {
                                    inner.string("this.", bitSet.getName(), "_");
                                    usedFields.add(inner.build());
                                }
                            } else {
                                inner.string("this.", createCachedInlinedFieldName(specialization, cache, field));
                                usedFields.add(inner.build());
                            }
                        }
                    }
                }

                if (!usedFields.isEmpty()) {
                    builder.startAssert();
                    builder.startStaticCall(types.InlineSupport, "validate");
                    builder.tree(createNodeAccess(frameState, specialization));
                    for (CodeTree field : usedFields) {
                        builder.tree(field);
                    }
                    builder.end();
                    builder.end();
                }

            }
            SpecializationData boxingOverload = specialization.lookupBoxingOverload(forType);
            if (boxingOverload != null) {
                targetMethod = boxingOverload.getMethod();
            }
            CodeTree specializationCall = callMethod(frameState, null, targetMethod, bindings);
            TypeMirror specializationReturnType = specialization.lookupBoxingOverloadReturnType(forType);

            if (isVoid(specializationReturnType)) {
                builder.statement(specializationCall);
                if (isVoid(forType.getReturnType())) {
                    builder.returnStatement();
                } else {
                    builder.startReturn().defaultValue(forType.getReturnType()).end();
                }
            } else {
                builder.startReturn();
                builder.tree(expectOrCast(specializationReturnType, forType, specializationCall));
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

    public static boolean guardNeedsStateBit(SpecializationData specialization, GuardExpression guard) {
        return guardNeedsSpecializationStateBit(specialization, guard) || guardNeedsNodeStateBit(specialization, guard);
    }

    public static boolean guardNeedsNodeStateBit(SpecializationData specialization, GuardExpression guard) {
        if (specialization.isReachesFallback() && !useSpecializationClass(specialization) && specialization.isGuardBoundWithCache(guard)) {
            return true;
        }
        return false;
    }

    public static boolean guardNeedsSpecializationStateBit(SpecializationData specialization, GuardExpression guard) {
        if (specialization.isReachesFallback() && useSpecializationClass(specialization) && specialization.isGuardBoundWithCache(guard)) {
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

    private CodeTree visitSpecializationGroup(CodeTreeBuilder parent, SpecializationGroup originalPrev, SpecializationGroup group,
                    ExecutableTypeData forType, FrameState frameState, Collection<SpecializationData> allowedSpecializations) {

        final CodeTreeBuilder builder = parent.create();
        SpecializationGroup prev = originalPrev;

        NodeExecutionMode mode = frameState.getMode();
        boolean hasFallthrough = false;
        List<IfTriple> cachedTriples = new ArrayList<>();
        for (TypeGuard guard : group.getTypeGuards()) {
            IfTriple triple = createTypeCheckOrCast(frameState, group, guard, mode, false, true);
            if (triple != null) {
                cachedTriples.add(triple);
            }
            if (!mode.isGuardFallback()) {
                triple = createTypeCheckOrCast(frameState, group, guard, mode, true, true);
                if (triple != null) {
                    cachedTriples.add(triple);
                }
            }
        }

        SpecializationData specialization = group.getSpecialization();
        List<GuardExpression> remainingGuards = new ArrayList<>(group.getGuards());

        // for specializations with multiple instances we can move certain guards
        // out of the loop.
        if (specialization != null && specialization.hasMultipleInstances()) {
            List<GuardExpression> unboundGuards = new ArrayList<>();
            for (GuardExpression guard : remainingGuards) {
                if (!specialization.isGuardBoundWithCache(guard)) {
                    unboundGuards.add(guard);
                } else {
                    // we need to stop as we need to ensure guard execution order
                    break;
                }
            }
            cachedTriples.addAll(createMethodGuardChecks(frameState, group, unboundGuards, mode));
            remainingGuards.removeAll(unboundGuards);
        }

        boolean useSpecializationClass = specialization != null && useSpecializationClass(specialization);

        if (mode.isFastPath()) {
            BlockState ifCount = BlockState.NONE;
            cachedTriples.addAll(0, createSpecializationActive(frameState, group, allowedSpecializations));
            ifCount = ifCount.add(IfTriple.materialize(builder, IfTriple.optimize(cachedTriples), false));

            if (specialization == null) {
                prev = visitSpecializationGroupChildren(builder, frameState.copy(), prev, group, forType, allowedSpecializations);
            } else {
                hasFallthrough |= buildSpecializationFastPath(builder, frameState, prev, group, forType, remainingGuards);
            }
            builder.end(ifCount.blockCount);
            hasFallthrough |= ifCount.ifCount > 0;
        } else if (mode.isSlowPath()) {
            if (specialization == null) {
                BlockState outerIfCount = BlockState.NONE;
                cachedTriples.addAll(createMethodGuardChecks(frameState, group, remainingGuards, mode));
                outerIfCount = outerIfCount.add(IfTriple.materialize(builder, IfTriple.optimize(cachedTriples), false));
                prev = visitSpecializationGroupChildren(builder, frameState, prev, group, forType, allowedSpecializations);

                builder.end(outerIfCount.blockCount);
                hasFallthrough |= outerIfCount.ifCount > 0;
            } else {
                hasFallthrough |= buildSpecializationSlowPath(builder, frameState, group, mode, cachedTriples, remainingGuards);
            }

        } else if (mode.isGuardFallback()) {
            BlockState ifCount = BlockState.NONE;

            if (specialization != null && specialization.hasMultipleInstances()) {
                throw new AssertionError("unsupported path. should be caught by the parser.");
            }

            BlockState innerIfCount = BlockState.NONE;

            if (useSpecializationClass) {
                outer: for (GuardExpression guard : specialization.getGuards()) {
                    for (CacheExpression cache : specialization.getBoundCaches(guard.getExpression(), true)) {
                        if (canCacheBeStoredInSpecialializationClass(cache)) {
                            cachedTriples.add(new IfTriple(loadSpecializationClass(frameState, specialization, false), null, null));
                            break outer;
                        }
                    }
                }
            }
            cachedTriples.addAll(createMethodGuardChecks(frameState, group, remainingGuards, mode));
            cachedTriples.addAll(createAssumptionCheck(frameState, specialization, NodeExecutionMode.FALLBACK_GUARD, true));

            cachedTriples = IfTriple.optimize(cachedTriples);

            if (specialization != null) {
                IfTriple singleCondition = null;
                if (cachedTriples.size() == 1) {
                    singleCondition = cachedTriples.get(0);
                }
                if (singleCondition != null) {
                    int index = cachedTriples.indexOf(singleCondition);
                    CodeTreeBuilder b = new CodeTreeBuilder(parent);

                    b.string("!(");
                    b.tree(createSpecializationActiveCheck(frameState, Arrays.asList(specialization)));

                    /*
                     * We can only optimize the fallback type check away if all implicit cast bits
                     * were exercised. Otherwise we need to keep in all implicit cast checks.
                     */
                    List<TypeGuard> guards = specialization.getImplicitTypeGuards();
                    if (!guards.isEmpty()) {
                        StateQuery query = StateQuery.create(ImplicitCastState.class, guards);
                        CodeTree stateCheck = multiState.createContainsAll(frameState, query);
                        if (!stateCheck.isEmpty()) {
                            b.newLine();
                            b.string(" && ").tree(stateCheck);
                        }
                    }

                    b.string(")");

                    cachedTriples.set(index, new IfTriple(singleCondition.prepare, combineTrees(" && ", b.build(), singleCondition.condition), singleCondition.statements));
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

            cachedTriples.addAll(createAssumptionCheck(frameState, specialization, NodeExecutionMode.UNCACHED, true));

            ifCount = ifCount.add(IfTriple.materialize(builder, IfTriple.optimize(cachedTriples), false));
            cachedTriples = createMethodGuardChecks(frameState, group, remainingGuards, mode);

            BlockState innerIfCount = IfTriple.materialize(builder, IfTriple.optimize(cachedTriples), false);

            prev = visitSpecializationGroupChildren(builder, frameState, prev, group, forType, allowedSpecializations);
            if (specialization != null && (prev == null || prev.hasFallthrough())) {
                builder.tree(createCallSpecialization(builder, frameState, forType, specialization));
            }
            builder.end(innerIfCount.blockCount);
            builder.end(ifCount.blockCount);
            hasFallthrough |= ifCount.ifCount > 0 || innerIfCount.ifCount > 0 || (specialization != null && !specialization.getExceptions().isEmpty());
        } else {
            throw new AssertionError("unexpected path");
        }

        group.setFallthrough(hasFallthrough);

        return builder.build();
    }

    private boolean buildSpecializationFastPath(final CodeTreeBuilder builder, FrameState frameState, SpecializationGroup prev, SpecializationGroup group,
                    ExecutableTypeData forType, List<GuardExpression> guardExpressions) {

        boolean hasFallthrough = false;
        SpecializationData specialization = group.getSpecialization();
        BlockState ifCount = BlockState.NONE;

        if (useSpecializationClass(specialization)) {
            builder.tree(loadSpecializationClass(frameState, specialization, false));
            if (specialization.hasMultipleInstances()) {
                builder.startWhile();
            } else {
                builder.startIf();
            }

            builder.tree(createGetSpecializationClass(frameState, specialization, true)).string(" != null");
            builder.end();
            builder.startBlock();
            ifCount = ifCount.incrementIf();
        }

        if (!specialization.getAssumptionExpressions().isEmpty()) {
            BlockState blockState = IfTriple.materialize(builder, createAssumptionCheck(frameState, specialization, NodeExecutionMode.FAST_PATH, false), false);
            builder.tree(createTransferToInterpreterAndInvalidate());
            builder.tree(createRemoveThis(builder, frameState, forType, specialization));
            builder.end(blockState.blockCount);
        }

        // if library is used in guard we need to push encapsulating node early
        // otherwise we can push it behind the guard
        boolean libraryInGuard = specialization.isAnyLibraryBoundInGuard();
        boolean pushEncapsulatingNode = specialization.needsPushEncapsulatingNode();
        boolean extractInBoundary = specialization.needsTruffleBoundary();

        if (extractInBoundary) {
            // Cannot extract to boundary with a virtual frame.
            if (specialization.hasFrameParameter()) {
                extractInBoundary = false;
            } else {
                for (VariableElement v : plugs.additionalArguments()) {
                    if (ElementUtils.typeEquals(v.asType(), types.VirtualFrame) || ElementUtils.typeEquals(v.asType(), types.Frame)) {
                        extractInBoundary = false;
                        break;
                    }
                }
            }
        }

        List<IfTriple> nonBoundaryGuards = new ArrayList<>();
        List<GuardExpression> nonBoundaryGuardExpressions = new ArrayList<>();
        guards: for (Iterator<GuardExpression> iterator = guardExpressions.iterator(); iterator.hasNext();) {
            GuardExpression guard = iterator.next();
            Set<CacheExpression> caches = group.getSpecialization().getBoundCaches(guard.getExpression(), true);
            for (CacheExpression cache : caches) {
                if (cache.isAlwaysInitialized() && cache.isRequiresBoundary()) {
                    break guards;
                }
            }

            nonBoundaryGuardExpressions.add(guard);
            iterator.remove();
        }

        nonBoundaryGuards.addAll(createFastPathNeverDefaultGuards(frameState, group.getSpecialization()));
        nonBoundaryGuards.addAll(createMethodGuardChecks(frameState, group, nonBoundaryGuardExpressions, NodeExecutionMode.FAST_PATH));

        if (pushEncapsulatingNode && libraryInGuard) {
            GeneratorUtils.pushEncapsulatingNode(builder, createNodeAccess(frameState));
            builder.startTryBlock();
        }

        nonBoundaryGuards.addAll(createMethodGuardChecks(frameState, group, guardExpressions, NodeExecutionMode.FAST_PATH));

        FrameState innerFrameState = frameState;
        BlockState nonBoundaryIfCount = BlockState.NONE;
        List<IfTriple> cachedTriples = new ArrayList<>();
        CodeTreeBuilder innerBuilder;
        if (extractInBoundary) {
            innerFrameState = frameState.copy();
            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.isAlwaysInitialized()) {
                    setCacheInitialized(innerFrameState, specialization, cache, false);
                }
            }

            nonBoundaryIfCount = nonBoundaryIfCount.add(IfTriple.materialize(builder, IfTriple.optimize(nonBoundaryGuards), false));
            innerBuilder = extractInBoundaryMethod(builder, frameState, specialization);

            for (NodeExecutionData execution : specialization.getNode().getChildExecutions()) {
                int index = forType.getVarArgsIndex(forType.getParameterIndex(execution.getIndex()));
                if (index != -1) {
                    LocalVariable var = innerFrameState.getValue(execution);
                    innerFrameState.set(execution, var.accessWith(CodeTreeBuilder.singleString(var.getName())));
                }
            }

        } else if (pushEncapsulatingNode) {
            innerBuilder = builder;
            nonBoundaryIfCount = IfTriple.materialize(innerBuilder, IfTriple.optimize(nonBoundaryGuards), false);
        } else {
            innerBuilder = builder;
            cachedTriples.addAll(0, nonBoundaryGuards);
        }

        cachedTriples.addAll(initializeCaches(innerFrameState, frameState.getMode(), group, specialization.getCaches(), true, false));

        if (pushEncapsulatingNode && !libraryInGuard) {
            GeneratorUtils.pushEncapsulatingNode(innerBuilder, createNodeAccess(frameState));
            innerBuilder.startTryBlock();
        }

        BlockState innerIfCount = BlockState.NONE;
        innerIfCount = innerIfCount.add(IfTriple.materialize(innerBuilder, IfTriple.optimize(cachedTriples), false));
        if (prev == null || prev.hasFallthrough()) {
            innerBuilder.tree(createFastPathExecute(builder, forType, specialization, innerFrameState));
        }
        innerBuilder.end(innerIfCount.blockCount);

        if (pushEncapsulatingNode && !libraryInGuard) {
            innerBuilder.end().startFinallyBlock();
            GeneratorUtils.popEncapsulatingNode(innerBuilder);
            innerBuilder.end();
        }

        hasFallthrough |= innerIfCount.ifCount > 0;
        builder.end(nonBoundaryIfCount.blockCount);

        if (pushEncapsulatingNode && libraryInGuard) {
            builder.end().startFinallyBlock();
            GeneratorUtils.popEncapsulatingNode(builder);
            builder.end();
        }

        if (useSpecializationClass(specialization) && specialization.hasMultipleInstances()) {
            String name = createSpecializationLocalName(specialization);
            builder.startStatement().string(name, " = ", name, ".next_").end();
        }

        builder.end(ifCount.blockCount);

        hasFallthrough |= ifCount.ifCount > 0;
        return hasFallthrough;
    }

    private List<IfTriple> createSpecializationActive(FrameState frameState, SpecializationGroup group,
                    Collection<SpecializationData> allowedSpecializations) {
        if (frameState.isSkipStateChecks()) {
            return List.of();
        }
        List<SpecializationData> specializations = group.collectSpecializations();
        final boolean stateGuaranteed = isStateGuaranteed(group, allowedSpecializations);
        if (node.needsSpecialize()) {
            CodeTree stateCheck = createSpecializationActiveCheck(frameState, specializations);
            CodeTree assertCheck = null;
            CodeTree stateGuard = null;
            if (stateGuaranteed) {
                assertCheck = CodeTreeBuilder.createBuilder().startAssert().tree(stateCheck).end().build();
            } else {
                stateGuard = stateCheck;
            }
            return Arrays.asList(new IfTriple(null, stateGuard, assertCheck));
        }
        return List.of();
    }

    private static boolean isStateGuaranteed(SpecializationGroup group, Collection<SpecializationData> allowedSpecializations) {
        return group.isLast() && allowedSpecializations != null && allowedSpecializations.size() == 1 &&
                        group.getAllSpecializations().size() == allowedSpecializations.size();
    }

    private CodeTree createSpecializationActiveCheck(FrameState frameState, List<SpecializationData> specializations) {
        StateQuery query = StateQuery.create(SpecializationActive.class, specializations);
        CodeTree stateCheck = multiState.createContains(frameState, query);

        if (specializations.size() == 1) {
            Set<SpecializationData> replacedBy = specializations.get(0).getReplacedBy();
            if (!replacedBy.isEmpty()) {
                BitSet set = multiState.findSet(query);
                CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
                b.tree(stateCheck);
                Set<BitSet> checkBitSets = new LinkedHashSet<>();

                for (SpecializationData replaced : replacedBy) {
                    StateQuery replacedQuery = StateQuery.create(SpecializationActive.class, replaced);
                    BitSet bitSet = multiState.findSet(replacedQuery);
                    if (!bitSet.equals(set)) {
                        checkBitSets.add(bitSet);
                    }
                }
                StateQuery replacedQuery = StateQuery.create(SpecializationActive.class, replacedBy);
                for (BitSet bitSet : checkBitSets) {
                    b.string(" && ");
                    b.tree(bitSet.createNotContains(frameState, replacedQuery));
                }

                stateCheck = b.build();
            }

        }
        return stateCheck;
    }

    private boolean buildSpecializationSlowPath(final CodeTreeBuilder builder, FrameState frameState, SpecializationGroup group, NodeExecutionMode mode,
                    List<IfTriple> outerTriples, List<GuardExpression> guardExpressions) throws AssertionError {
        SpecializationData specialization = group.getSpecialization();
        Objects.requireNonNull(specialization);

        if (hasExcludeBit(specialization)) {
            CodeTree excludeCheck = multiState.createNotContains(frameState, StateQuery.create(SpecializationExcluded.class, specialization));
            outerTriples.add(0, new IfTriple(null, excludeCheck, null));
        }

        if (hasExcludes(specialization)) {
            CodeTree excludeCheck = multiState.createNotContains(frameState, StateQuery.create(SpecializationActive.class, specialization.getReplacedBy()));
            outerTriples.add(0, new IfTriple(null, excludeCheck, null));
        }

        boolean hasFallthrough = false;
        BlockState outerIfCount = BlockState.NONE;
        for (CacheExpression cache : specialization.getCaches()) {
            if (!cache.isAlwaysInitialized()) {
                continue;
            }
            CodeTree prepare = CodeTreeBuilder.createBuilder().declarationDefault(cache.getParameter().getType(),
                            createCacheLocalName(cache)).build();
            outerTriples.add(0, new IfTriple(prepare, null, null));
        }

        outerIfCount = outerIfCount.add(IfTriple.materialize(builder, IfTriple.optimize(outerTriples), false));
        String countName = specialization != null ? "count" + specialization.getIndex() + "_" : null;
        final boolean useSpecializationClass = useSpecializationClass(specialization);
        final boolean multipleInstances = specialization.hasMultipleInstances();
        final boolean needsDuplicationCheck = needsDuplicationCheck(specialization);
        final boolean useDuplicateFlag = specialization.isGuardBindsExclusiveCache() && !useSpecializationClass;
        if (useDuplicateFlag) {
            validateDuplicateFlagUsage(specialization);
        }

        final String duplicateFoundName = specialization.getId() + "_duplicateFound_";

        boolean pushBoundary = specialization.needsPushEncapsulatingNode();
        if (pushBoundary) {
            builder.startBlock();
            GeneratorUtils.pushEncapsulatingNode(builder, createNodeAccess(frameState));
            builder.startTryBlock();
        }
        BlockState innerIfCount = BlockState.NONE;
        String specializationLocalName = createSpecializationLocalName(specialization);

        if (needsDuplicationCheck) {
            builder.startWhile().string("true").end();
            builder.startBlock();
            builder.tree(createDuplicationCheck(builder, frameState, group, guardExpressions, useDuplicateFlag, countName, duplicateFoundName,
                            specializationLocalName));

            builder.startIf();
            if (useDuplicateFlag) {
                // we reuse the specialization class local name instead of a duplicate found
                // name
                builder.string("!", duplicateFoundName);
            } else {
                builder.string(createSpecializationLocalName(specialization), " == null");

                if (!multipleInstances) {
                    builder.string(" && ", countName, " < 1");
                }
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

        CodeTree specializeElseBranch = null;
        if (needsDuplicationCheck && useSpecializationClass) {
            if (multipleInstances) {
                DSLExpression limit = optimizeExpression(specialization.getLimitExpression());
                Set<CacheExpression> caches = specialization.getBoundCaches(limit, true);
                innerTripples.addAll(initializeCaches(innerFrameState, innerFrameState.getMode(), group, caches, true, false));
                CodeTree limitExpression = writeExpression(innerFrameState, specialization, limit);
                CodeTreeBuilder limitBuilder = CodeTreeBuilder.createBuilder();
                limitBuilder.string(countName).string(" < ").tree(limitExpression);
                if (specialization.hasUnroll() && !specialization.isUnrolled()) {
                    // subtract unrolled count from limit
                    limitBuilder.string(" - ").string(String.valueOf(specialization.getUnroll()));
                }
                innerTripples.add(new IfTriple(null, limitBuilder.build(), null));
            }
        } else if (needsDuplicationCheck) {
            innerTripples.add(new IfTriple(null, multiState.createNotContains(innerFrameState, StateQuery.create(SpecializationActive.class, specialization)), null));
        }

        if (innerFrameState.isSpecializationClassInitialized(specialization)) {
            // we need to null an already initialized specialization class if initialized in the
            // else branch of any guard
            specializeElseBranch = builder.create().startStatement().string(createSpecializationLocalName(specialization)).string(" = null").end().build();
        }
        int innerIfCountDiff = innerIfCount.ifCount;
        innerIfCount = innerIfCount.add(IfTriple.materialize(builder, IfTriple.optimize(innerTripples), false));
        innerIfCountDiff = innerIfCount.ifCount - innerIfCountDiff;

        StateTransaction stateTransaction = new StateTransaction();
        builder.tree(createSpecialize(builder, innerFrameState, stateTransaction, group, specialization, false));
        CodeTree updateImplicitCast = createUpdateImplicitCastState(builder, innerFrameState, stateTransaction, specialization);
        if (updateImplicitCast != null) {
            builder.tree(updateImplicitCast);
        }

        for (CacheExpression cache : specialization.getCaches()) {
            if (cache.isEncodedEnum() && cache.getSharedGroup() == null) {
                BitSet bitSet = multiState.findSet(EncodedEnumState.class, cache);
                if (bitSet != null) {
                    builder.tree(bitSet.createLoad(innerFrameState));
                    stateTransaction.markModified(bitSet);
                }
            }
        }

        builder.tree(multiState.createSet(innerFrameState, stateTransaction, StateQuery.create(SpecializationActive.class, specialization), true, false));
        builder.tree(multiState.persistTransaction(innerFrameState, stateTransaction));

        plugs.notifySpecialize(this, builder, frameState, specialization);

        if (types.SlowPathListener != null && ElementUtils.isAssignable(specialization.getNode().getTemplateType().asType(), types.SlowPathListener)) {
            builder.startStatement().startCall("afterSpecialize").end().end();
        }

        if (needsDuplicationCheck) {
            hasFallthrough = true;
            if (useDuplicateFlag) {
                builder.startStatement().string(duplicateFoundName, " = true").end();
            }

            endAndElse(builder, innerIfCountDiff, specializeElseBranch);
            builder.end(innerIfCount.blockCount - innerIfCountDiff);

            /*
             * We keep around always initialized caches in locals explicitly to avoid that weak
             * references get collected between null check and specialization invocation.
             */
            for (CacheExpression cache : specialization.getCaches()) {
                if (!cache.isAlwaysInitialized()) {
                    continue;
                }
                setCacheInitialized(frameState, specialization, cache, true);
            }

            // need to ensure that we update the implicit cast specializations on duplicates
            if (updateImplicitCast != null) {
                builder.startElseBlock();
                stateTransaction = new StateTransaction();
                builder.tree(createUpdateImplicitCastState(builder, frameState, stateTransaction, specialization));
                builder.tree(multiState.createSet(frameState, stateTransaction, StateQuery.create(SpecializationActive.class, specialization), true, false));
                builder.tree(multiState.persistTransaction(innerFrameState, stateTransaction));
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
            builder.statement("break");
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

        builder.end(outerIfCount.blockCount);
        hasFallthrough |= outerIfCount.ifCount > 0;

        return hasFallthrough;
    }

    private static void validateDuplicateFlagUsage(SpecializationData specialization) throws AssertionError {
        for (CacheExpression cache : specialization.getCaches()) {
            if (usesExclusiveInstanceField(cache)) {
                throw new AssertionError("Using duplicate flag with cached reference fields is not thread-safe. " + specialization + ": " + cache);
            }
        }
    }

    static boolean usesExclusiveInstanceField(CacheExpression cache) {
        if (cache.isAlwaysInitialized()) {
            return false;
        } else if (cache.isEagerInitialize()) {
            return false;
        } else if (cache.isEncodedEnum()) {
            return false;
        } else if (cache.getSharedGroup() != null) {
            return false;
        } else if (cache.getInlinedNode() != null) {
            return false;
        } else {
            return true;
        }
    }

    static void endAndElse(CodeTreeBuilder b, int endCount, CodeTree elseBranch) {
        for (int i = 0; i < endCount; i++) {
            b.end();
            if (elseBranch != null) {
                b.startElseBlock();
                b.tree(elseBranch);
                b.end();
            }
        }
    }

    private static boolean specializationNeedsInitializedBit(SpecializationData specialization) {
        if (useSpecializationClass(specialization) && specialization.isReachesFallback() && !specialization.getCaches().isEmpty()) {
            for (GuardExpression guard : specialization.getGuards()) {
                if (guardNeedsStateBit(specialization, guard)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<IfTriple> createFastPathNeverDefaultGuards(FrameState frameState,
                    SpecializationData specialization) {
        List<CacheExpression> caches = specialization.getCaches();
        if (specialization == null || caches.isEmpty()) {
            return Collections.emptyList();
        }

        List<IfTriple> triples = new ArrayList<>();

        if (specializationNeedsInitializedBit(specialization)) {
            StateQuery query = StateQuery.create(SpecializationCachesInitialized.class, specialization);
            SpecializationStateReference ref = createStateReference(frameState, specialization, query);
            triples.add(new IfTriple(null, ref.bitSet.createContains(ref.reference, query), null));
        }

        for (CacheExpression cache : caches) {
            if (cache.isBind()) {
                continue;
            } else if (cache.isAlwaysInitialized()) {
                continue;
            } else if (cache.getInlinedNode() != null) {
                continue;
            } else if (cache.isEagerInitialize()) {
                continue;
            }

            boolean needsNeverDefaultCheck = cache.getSharedGroup() != null || (!useSpecializationClass(specialization));

            /*
             * If a cache is never default or is shared it must not have a default value. We add a
             * guard such that we go to executeAndSpecialize in such a case.
             */
            if (needsNeverDefaultCheck || isCacheNullDueToFallback(specialization, cache)) {
                triples.add(createNeverDefaultGuard(frameState, specialization, cache, " != "));
            }
        }
        return triples;
    }

    private static boolean isCacheNullDueToFallback(SpecializationData specialization, CacheExpression cache) {
        if (!specialization.isReachesFallback()) {
            return false;
        }
        /*
         * Guards that bind caches that reach fallback must never be null, as they can be
         * initialized.
         */
        for (GuardExpression guard : specialization.getGuards()) {
            if (getNullCachesDueToFallback(specialization, guard).contains(cache)) {
                return true;
            }
        }
        return false;
    }

    private static Collection<CacheExpression> getNullCachesDueToFallback(SpecializationData specialization, GuardExpression guard) {
        if (!specialization.isReachesFallback()) {
            return Collections.emptyList();
        }
        Set<CacheExpression> boundCaches = specialization.getBoundCaches(guard.getExpression(), false);
        if (useSpecializationClass(specialization)) {
            // with a specialization class only shared guards may be null.
            Set<CacheExpression> filteredCaches = new LinkedHashSet<>();
            for (CacheExpression cache : boundCaches) {
                if (cache.getSharedGroup() != null) {
                    filteredCaches.add(cache);
                }
            }
            return filteredCaches;
        } else {
            return boundCaches;
        }
    }

    private IfTriple createNeverDefaultGuard(FrameState frameState, SpecializationData specialization,
                    CacheExpression cache, String operator) {

        CodeTreeBuilder prepare = CodeTreeBuilder.createBuilder();
        CodeTreeBuilder condition = CodeTreeBuilder.createBuilder();

        if (cache.isEncodedEnum()) {
            CacheExpression sharedCache = lookupSharedCacheKey(cache);
            condition.startGroup();

            StateQuery query = StateQuery.create(EncodedEnumState.class, sharedCache);
            SpecializationStateReference ref = createStateReference(frameState, specialization, query);
            condition.tree(ref.bitSet.createExtractInteger(ref.reference, query));
            condition.string(" ", operator, " 0");
            condition.end();

        } else {
            LocalVariable wrapper = createCacheClassAccess(frameState, prepare, cache);
            String localName = createCacheLocalName(cache);
            CodeTree tree = createCacheAccess(frameState, specialization, cache, null);
            if (wrapper == null) {
                prepare.declaration(cache.getParameter().getType(), localName, tree);
                condition.string(localName).string(operator).defaultValue(cache.getParameter().getType());
                setCacheInitialized(frameState, specialization, cache, true);
            } else {
                condition.tree(wrapper.createReference()).string(operator, "null");
            }
        }

        return new IfTriple(prepare.build(), condition.build(), null);
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
        if (generatorMode.equals(GeneratorMode.EXPORTED_MESSAGE)) {
            boundaryMethodName = String.format("%s_%sBoundary", node.getNodeId(), specialization.getId());
        } else {
            boundaryMethodName = String.format("%sBoundary", specialization.getId());
        }
        boundaryMethodName = firstLetterLowerCase(boundaryMethodName);

        if (usedBoundaryNames.contains(boundaryMethodName)) {
            boundaryMethodName = boundaryMethodName + (boundaryIndex++);
        }
        usedBoundaryNames.add(boundaryMethodName);

        String includeFrameParameter = null;
        if (specialization.getFrame() != null) {
            if (ElementUtils.typeEquals(types.MaterializedFrame, specialization.getFrame().getType())) {
                includeFrameParameter = FRAME_VALUE;
            } else {
                includeFrameParameter = FRAME_VALUE + "Materialized";
                CodeTreeBuilder read = builder.create().startCall(FRAME_VALUE, "materialize").end();
                LocalVariable materializedFrame = new LocalVariable(types.MaterializedFrame, FRAME_VALUE, read.build());
                frameState.set(includeFrameParameter, materializedFrame);
            }
        }
        CodeExecutableElement boundaryMethod = new CodeExecutableElement(modifiers(PRIVATE), parentMethod.getReturnType(), boundaryMethodName);
        GeneratorUtils.mergeSuppressWarnings(boundaryMethod, "static-method");
        multiState.addParametersTo(frameState, boundaryMethod);
        frameState.addParametersTo(boundaryMethod, Integer.MAX_VALUE, includeFrameParameter,
                        createSpecializationLocalName(specialization));

        boundaryMethod.getAnnotationMirrors().add(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
        boundaryMethod.getThrownTypes().addAll(parentMethod.getThrownTypes());
        innerBuilder = boundaryMethod.createBuilder();
        ((CodeTypeElement) parentMethod.getEnclosingElement()).add(boundaryMethod);
        builder.startReturn().startCall("this", boundaryMethod);
        multiState.addReferencesTo(frameState, builder);
        frameState.addReferencesTo(builder, includeFrameParameter, createSpecializationLocalName(specialization));
        for (CacheExpression cache : specialization.getCaches()) {
            if (cache.isAlwaysInitialized()) {
                continue;
            }
            LocalVariable var = frameState.getCacheInitialized(specialization, cache);
            if (var != null) {
                CodeVariableElement v = var.createParameter();
                v.setName(createCacheLocalName(cache));
                boundaryMethod.addParameter(v);
                builder.tree(var.createReference());
            } else {
                var = frameState.getCacheClassInitialized(cache);
                if (var != null) {
                    boundaryMethod.addParameter(var.createParameter());
                    builder.tree(var.createReference());
                }
            }
        }

        builder.end().end();

        return innerBuilder;
    }

    private List<IfTriple> createAssumptionCheck(FrameState frameState, SpecializationData specialization, NodeExecutionMode mode, boolean testValid) {
        if (specialization == null || specialization.getAssumptionExpressions().isEmpty()) {
            return Collections.emptyList();
        }

        boolean useSpecializationClass = useSpecializationClass(specialization);

        CodeTreeBuilder builder = new CodeTreeBuilder(null);

        List<IfTriple> triples = new ArrayList<>();
        for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
            CodeTree assumptionReference;
            CodeTree nullCheckReference;

            boolean needsCaching = assumption.needsCaching();
            if (mode.isUncached() || !needsCaching) {
                assumptionReference = writeExpression(frameState, specialization, assumption.getExpression());

                /*
                 * For the receiver null check we need to get to the root field expression.
                 */
                DSLExpression e = assumption.getExpression();
                while (e instanceof Variable) {
                    Variable v = (Variable) e;
                    if (v.getReceiver() == null) {
                        break;
                    }
                    e = v.getReceiver();
                }
                nullCheckReference = writeExpression(frameState, specialization, e);
                if (!useSpecializationClass(specialization)) {
                    assumptionReference = createInlinedAccess(frameState, specialization, assumptionReference, null);
                    nullCheckReference = createInlinedAccess(frameState, specialization, nullCheckReference, null);
                }
            } else {
                assumptionReference = createAssumptionReference(frameState, specialization, assumption);
                nullCheckReference = assumptionReference;
            }

            if (testValid) {
                if (!builder.isEmpty()) {
                    builder.string(" && ");
                }
                if (mode.isGuardFallback()) {
                    builder.string("(");
                    if (useSpecializationClass) {
                        builder.tree(createGetSpecializationClass(frameState, specialization, true));
                        builder.string(" == null || ");
                    }

                    builder.tree(nullCheckReference);
                    builder.string(" == null || ");

                    builder.tree(createAssumptionGuard(assumptionReference));
                    builder.string(")");
                } else {
                    builder.tree(createAssumptionGuard(assumptionReference));
                }
            } else {
                if (!builder.isEmpty()) {
                    builder.string(" || ");
                }
                builder.string("!");
                builder.tree(createAssumptionGuard(assumptionReference));
            }

        }
        triples.add(new IfTriple(null, builder.build(), null));
        return triples;
    }

    private CodeTree writeExpression(FrameState frameState, SpecializationData specialization, DSLExpression expression) throws AssertionError {
        expression.accept(new AbstractDSLExpressionVisitor() {
            @Override
            public void visitCall(Call binary) {
                frameState.addThrownExceptions(binary.getResolvedMethod());
                if (ElementUtils.isDeprecated(binary.getResolvedMethod())) {
                    GeneratorUtils.mergeSuppressWarnings(frameState.method, "deprecation");
                }
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
        boolean multipleInstances = specialization.hasMultipleInstances();
        boolean useSpecializationClass = useSpecializationClass(specialization);

        if (useSpecializationClass) {
            builder.declaration("int", countName, CodeTreeBuilder.singleString("0"));
        }

        if (useSpecializationClass) {
            builder.tree(loadSpecializationClass(frameState, specialization, true));
            builder.declaration(createSpecializationClassReferenceType(specialization),
                            createSpecializationLocalOriginalName(specialization),
                            createGetSpecializationClass(frameState, specialization, true));
        }

        if (useDuplicate) {
            builder.declaration("boolean", duplicateFoundName, CodeTreeBuilder.singleString("false"));
        }

        for (CacheExpression cache : specialization.getCaches()) {
            createCacheClassAccess(frameState, builder, cache);
        }

        FrameState innerFrameState = frameState.copy();

        List<IfTriple> duplicationTriples = new ArrayList<>();
        if (useSpecializationClass) {
            builder.startWhile().string(specializationLocalName, " != null").end().startBlock();
        } else {
            duplicationTriples.add(new IfTriple(null, createSpecializationActiveCheck(innerFrameState, Arrays.asList(specialization)), null));
        }

        duplicationTriples.addAll(createFastPathNeverDefaultGuards(innerFrameState, group.getSpecialization()));
        duplicationTriples.addAll(createMethodGuardChecks(innerFrameState, group, guardExpressions, NodeExecutionMode.FAST_PATH));
        duplicationTriples.addAll(createAssumptionCheck(innerFrameState, specialization, NodeExecutionMode.SLOW_PATH, true));
        BlockState duplicationIfCount = IfTriple.materialize(builder, IfTriple.optimize(duplicationTriples), false);

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
            List<IfTriple> triples = initializeCaches(innerFrameState, NodeExecutionMode.FAST_PATH, group,
                            cachesToInitialize, true, false);

            IfTriple.materialize(builder, IfTriple.optimize(triples), true);
        }

        if (useSpecializationClass) {
            builder.statement("break");
        }

        builder.end(duplicationIfCount.blockCount);

        if (useSpecializationClass) {
            if (multipleInstances) {
                builder.statement(countName + "++");
                builder.startStatement().string(specializationLocalName, " = ", specializationLocalName, ".next_").end();
            } else {

                if (specializationNeedsInitializedBit(specialization)) {
                    StateQuery initializedQuery = StateQuery.create(SpecializationCachesInitialized.class, specialization);
                    SpecializationStateReference ref = createStateReference(frameState, specialization, initializedQuery);
                    builder.startIf().tree(ref.bitSet.createContains(ref.reference, initializedQuery)).end().startBlock();
                    builder.statement(countName + "++");
                    builder.end();
                } else {
                    builder.statement(countName + "++");
                }
                builder.statement(specializationLocalName, " = null");
                builder.statement("break");
            }
            builder.end();
        }

        builder.end();
        return builder.build();
    }

    private CodeTree createSpecialize(CodeTreeBuilder parent, FrameState frameState, StateTransaction transaction, SpecializationGroup group, SpecializationData specialization,
                    boolean aotSpecialize) {
        CodeTreeBuilder builder = parent.create();

        List<IfTriple> triples = new ArrayList<>();

        triples.add(new IfTriple(initializeSpecializationClass(frameState, specialization, aotSpecialize), null, null));
        triples.addAll(initializeCaches(frameState, frameState.getMode(), group,
                        specialization.getCaches(), false, true));
        triples.addAll(persistAssumptions(frameState, specialization));

        if (aotSpecialize) {
            for (CacheExpression cache : specialization.getCaches()) {
                if (cache.isAlwaysInitialized()) {
                    continue;
                }
                if (types.Profile != null &&
                                (ElementUtils.isAssignable(cache.getParameter().getType(), types.Profile) || ElementUtils.isAssignable(cache.getParameter().getType(), types.InlinedProfile))) {
                    CodeTreeBuilder b = builder.create();
                    b.startStatement();
                    b.tree(createCacheAccess(frameState, specialization, cache, null));
                    b.startCall(".disable");
                    if (cache.getInlinedNode() != null) {
                        b.tree(createNodeAccess(frameState, specialization));
                    }
                    b.end();
                    b.end();
                    triples.add(new IfTriple(null, null, b.build()));
                }

            }
        }

        if (specializationNeedsInitializedBit(specialization)) {
            StateQuery query = StateQuery.create(SpecializationCachesInitialized.class, specialization);
            SpecializationStateReference stateRef = createStateReference(frameState, specialization, query);
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startStatement();
            b.tree(stateRef.reference).string(" = ");
            b.tree(stateRef.bitSet.createSetExpression(stateRef.reference, query, true));
            b.end();
            triples.add(new IfTriple(null, null, b.build()));
        }

        triples.addAll(persistSpecializationClass(frameState, specialization, aotSpecialize));
        builder.end(IfTriple.materialize(builder, triples, true).blockCount);

        List<SpecializationData> excludesSpecializations = new ArrayList<>();
        for (SpecializationData otherSpeciailzation : node.getReachableSpecializations()) {
            if (otherSpeciailzation == specialization) {
                continue;
            }
            if (otherSpeciailzation.getReplacedBy().contains(specialization)) {
                excludesSpecializations.add(otherSpeciailzation);
            }
        }

        if (!excludesSpecializations.isEmpty()) {
            Set<String> clearedGroups = new HashSet<>();

            for (SpecializationData excludes : excludesSpecializations) {
                if (useSpecializationClass(excludes)) {
                    builder.startStatement();
                    builder.tree(createSpecializationFieldAccess(frameState, excludes, true, true, null, CodeTreeBuilder.singleString("null")));
                    builder.end();
                } else {
                    for (CacheExpression cache : excludes.getCaches()) {
                        if (cache.isEncodedEnum()) {
                            // encoded enums do not need to be cleared
                            continue;
                        } else if (cache.getInlinedNode() != null) {
                            // inlined nodes do not need to be clared
                            continue;
                        } else if (cache.isAlwaysInitialized()) {
                            continue;
                        } else if (cache.isMergedLibrary()) {
                            continue;
                        } else if (ElementUtils.isPrimitive(cache.getParameter().getType())) {
                            // no need to clear primitives
                            continue;
                        }
                        String sharedGroup = cache.getSharedGroup();
                        if (sharedGroup != null) {
                            if (clearedGroups.contains(sharedGroup)) {
                                continue;
                            }
                            clearedGroups.add(sharedGroup);
                            if (!isSharedExclusivelyIn(sharedGroup, excludesSpecializations)) {
                                // do not clear a cache if other specializations use it.
                                continue;
                            }
                        }
                        builder.startStatement();
                        builder.tree(createSpecializationFieldAccess(frameState, excludes, true, true, createFieldName(excludes, cache), CodeTreeBuilder.singleString("null")));
                        builder.end();
                    }
                }
            }

            builder.tree((multiState.createSet(frameState, transaction,
                            StateQuery.create(SpecializationActive.class, excludesSpecializations), false, transaction == null)));
        }

        return builder.build();
    }

    private boolean isSharedExclusivelyIn(String sharedKey, List<SpecializationData> specializations) {
        Set<SpecializationData> specializationSet = new HashSet<>(specializations);
        for (NodeData n : sharingNodes) {
            for (SpecializationData otherSpecialization : n.getReachableSpecializations()) {
                if (specializationSet.contains(otherSpecialization)) {
                    // only interested in other specializations
                    continue;
                }
                for (CacheExpression cache : otherSpecialization.getCaches()) {
                    if (cache.getSharedGroup() != null && cache.getSharedGroup().equals(sharedKey)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private List<IfTriple> persistAssumptions(FrameState frameState, SpecializationData specialization) {
        List<IfTriple> triples = new ArrayList<>();
        for (AssumptionExpression assumption : specialization.getAssumptionExpressions()) {
            if (!assumption.needsCaching()) {
                continue;
            }
            LocalVariable var = frameState.get(assumption.getId());
            String name = createAssumptionFieldName(specialization, assumption);
            CodeTreeBuilder builder = new CodeTreeBuilder(null);
            builder.startStatement();
            builder.tree(createSpecializationFieldAccess(frameState, specialization, true, true, name, var.createReference()));
            builder.end();
            triples.add(new IfTriple(builder.build(), null, null));
        }
        return triples;
    }

    private CodeTree loadSpecializationClass(FrameState frameState, SpecializationData specialization, boolean useUpdater) {
        if (!useSpecializationClass(specialization)) {
            throw new AssertionError("Not using specialization class.");
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
        if (useUpdater) {
            if (frameState.isInlinedNode()) {
                builder.string("this.", createSpecializationFieldName(specialization)).startCall(".getVolatile");
                builder.tree(frameState.getValue(INLINED_NODE_INDEX).createReference());
                builder.end();
            } else {
                builder.string(createSpecializationClassUpdaterName(specialization)).string(".getVolatile(this)");
            }
        } else {
            builder.tree(createSpecializationFieldAccess(frameState, specialization, true, true, null, null));
        }
        builder.end();
        if (var == null) {
            frameState.set(localName, new LocalVariable(createSpecializationClassReferenceType(specialization), localName, null));
        }
        return builder.build();
    }

    private Collection<IfTriple> persistSpecializationClass(FrameState frameState, SpecializationData specialization, boolean aotSpecialize) {
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

        if (!aotSpecialize && needsDuplicationCheck(specialization)) {
            /*
             * No storeStore fence in this branch as we always use compareAndSet which has implicit
             * storeStoreFence semantics.
             */
            builder.startIf();
            if (frameState.isInlinedNode()) {
                String fieldName = createSpecializationFieldName(specialization);
                builder.string("!this.", fieldName);
                builder.startCall(".compareAndSet");
                builder.tree(frameState.getValue(INLINED_NODE_INDEX).createReference());
                builder.string(createSpecializationLocalOriginalName(specialization));
                builder.tree(ref);
                builder.end();
            } else {
                builder.string("!").string(createSpecializationClassUpdaterName(specialization)).startCall(".compareAndSet");
                builder.string("this");
                builder.string(createSpecializationLocalOriginalName(specialization));
                builder.tree(ref);
                builder.end();
            }
            builder.end().startBlock();
            builder.statement("continue");
            builder.end();
        } else {
            // We need to insert memory fence if there are cached values and those are stored in a
            // linked list. Another thread may be traversing the linked list while we are updating
            // it here: we must ensure that the item that is being appended to the list is fully
            // initialized.
            builder.startStatement();
            builder.startStaticCall(context.getType(VarHandle.class), "storeStoreFence").end();
            builder.end();

            builder.startStatement();
            builder.tree(createSpecializationFieldAccess(frameState, specialization, true, true, null, ref));
            builder.end();
        }

        return Arrays.asList(new IfTriple(builder.build(), null, null));
    }

    private static String createSpecializationClassPersisted(SpecializationData specialization) {
        return createSpecializationLocalName(specialization) + "$persisted";
    }

    private CodeTree initializeSpecializationClass(FrameState frameState, SpecializationData specialization, boolean aotInitialize) {
        boolean useSpecializationClass = useSpecializationClass(specialization);
        if (useSpecializationClass) {
            String localName = createSpecializationLocalName(specialization);
            String typeName = createSpecializationTypeName(specialization);
            // we cannot use local name, because its used track reads not init writes
            if (!frameState.isSpecializationClassInitialized(specialization)) {

                if (frameState.getMode().isFastPath()) {
                    throw new AssertionError("Must never initialize the specialization cache on the fast-path.");
                }

                TypeMirror type = createSpecializationClassReferenceType(specialization);

                CodeTreeBuilder initBuilder = new CodeTreeBuilder(null);
                boolean isNode = specializationClassIsNode(specialization);
                if (isNode) {
                    if (frameState.isInlinedNode()) {
                        initBuilder.startCall(frameState.getValue(INLINED_NODE_INDEX).createReference(), "insert");
                    } else {
                        initBuilder.startCall("this.insert");
                    }
                }
                initBuilder.startNew(typeName);
                if (specialization.hasMultipleInstances()) {
                    if (aotInitialize) {
                        initBuilder.tree(createSpecializationFieldAccess(frameState, specialization, true, false, null, null));
                    } else {
                        initBuilder.string(createSpecializationLocalOriginalName(specialization));
                    }
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
                frameState.setSpecializationClassInitialized(specialization, true);
                frameState.set(localName, new LocalVariable(type, localName, CodeTreeBuilder.singleString(localName)));

                return builder.build();
            }
        }
        return null;
    }

    private CodeTree createUpdateImplicitCastState(CodeTreeBuilder parent, FrameState frameState, StateTransaction transaction, SpecializationData specialization) {
        CodeTreeBuilder builder = null;
        int signatureIndex = 0;
        for (Parameter p : specialization.getSignatureParameters()) {
            TypeMirror targetType = p.getType();
            TypeMirror polymorphicType = node.getPolymorphicExecutable().getParameterTypeOrDie(p.getSpecification().getExecution());
            if (typeSystem.hasImplicitSourceTypes(targetType) && needsCastTo(polymorphicType, targetType)) {
                String implicitFieldName = createImplicitTypeStateLocalName(p);
                if (builder == null) {
                    builder = parent.create();
                }
                StateQuery implicitCastState = StateQuery.create(ImplicitCastState.class, new TypeGuard(typeSystem, p.getType(), signatureIndex));
                builder.startStatement();
                builder.tree(multiState.createSetInteger(frameState, transaction, implicitCastState, CodeTreeBuilder.singleString(implicitFieldName)));
                builder.end();
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
        TypeMirror[] exceptionTypes = new TypeMirror[specialization.getExceptions().size()];
        for (int i = 0; i < exceptionTypes.length; i++) {
            TypeMirror type = specialization.getExceptions().get(i).getJavaClass();
            exceptionTypes[i] = type;
        }
        builder.end().startCatchBlock(exceptionTypes, "ex");

        // fallthrough uncached
        if (!frameState.getMode().isUncached()) {
            builder.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            builder.tree(createExcludeThis(builder, frameState, forType, specialization));
        }

        builder.end();
        return builder.build();
    }

    private final Map<SpecializationData, CodeExecutableElement> removeThisMethods = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    private CodeTree createExcludeThis(CodeTreeBuilder parent, FrameState frameState, ExecutableTypeData forType, SpecializationData specialization) {
        CodeTreeBuilder builder = parent.create();

        List<SpecializationData> specializations = new ArrayList<>();
        specializations.add(specialization);
        if (specialization.getUncachedSpecialization() != null) {
            specializations.add(specialization.getUncachedSpecialization());
        }

        FrameState innerFrameState = frameState.copy();

        StateQuery excludedQuery = StateQuery.create(SpecializationExcluded.class, specializations);
        StateQuery activeQuery = StateQuery.create(SpecializationActive.class, specializations);

        builder.tree(this.multiState.createForceLoad(innerFrameState, activeQuery, excludedQuery));
        StateTransaction transaction = new StateTransaction();
        builder.tree(this.multiState.createSet(innerFrameState, transaction, activeQuery, false, false));
        builder.tree(this.multiState.createSet(innerFrameState, transaction, excludedQuery, true, false));
        builder.tree(this.multiState.persistTransaction(innerFrameState, transaction));
        plugs.notifySpecialize(this, builder, innerFrameState, specialization);

        for (SpecializationData removeSpecialization : specializations) {
            if (useSpecializationClass(removeSpecialization)) {
                builder.startStatement();
                builder.tree(createSpecializationFieldAccess(innerFrameState, specialization, true, false, null, CodeTreeBuilder.singleString("null")));
                builder.end();
            }
        }

        boolean hasUnexpectedResultRewrite = specialization.hasUnexpectedResultRewrite();
        boolean hasReexecutingRewrite = !hasUnexpectedResultRewrite || specialization.getExceptions().size() > 1;

        if (hasReexecutingRewrite) {
            if (hasUnexpectedResultRewrite) {
                builder.startIf().string("ex").instanceOf(types.UnexpectedResultException).end().startBlock();
                builder.tree(createReturnUnexpectedResult(forType, true));
                builder.end().startElseBlock();
                builder.tree(createCallExecuteAndSpecialize(builder, forType, frameState));
                builder.end();
            } else {
                builder.tree(createCallExecuteAndSpecialize(builder, forType, frameState));
            }
        } else {
            assert hasUnexpectedResultRewrite;
            builder.tree(createReturnUnexpectedResult(forType, false));
        }

        builder.end();
        return builder.build();
    }

    private CodeTree createRemoveThis(CodeTreeBuilder parent, FrameState outerFrameState, ExecutableTypeData forType, SpecializationData specialization) {
        CodeExecutableElement method = removeThisMethods.get(specialization);
        String specializationLocalName = createSpecializationLocalName(specialization);
        FrameState frameState = outerFrameState.copy();
        multiState.clearLoaded(frameState);

        TypeMirror specializationType = createSpecializationClassReferenceType(specialization);
        boolean useSpecializationClass = useSpecializationClass(specialization);
        boolean inline = frameState.isInlinedNode();
        if (method == null) {
            method = new CodeExecutableElement(context.getType(void.class), "remove" + specialization.getId() + "_");
            if (inline) {
                method.addParameter(frameState.getValue(INLINED_NODE_INDEX).createParameter());
            }
            if (useSpecializationClass) {
                method.addParameter(new CodeVariableElement(specializationType, specializationLocalName));
            }
            CodeTreeBuilder builder = method.createBuilder();
            if (!useSpecializationClass || !specialization.hasMultipleInstances()) {
                // single instance remove
                builder.tree((multiState.createSet(frameState, null, StateQuery.create(SpecializationActive.class, specialization), false, true)));
                plugs.notifySpecialize(this, builder, frameState, specialization);
                if (useSpecializationClass) {
                    builder.startStatement();
                    builder.tree(createSpecializationFieldAccess(frameState, specialization, true, true, null, CodeTreeBuilder.singleString("null")));
                    builder.end();
                }
            } else {
                // multi instance remove
                builder.startWhile().string("true").end().startBlock();
                String typeName = createSpecializationTypeName(specialization);
                boolean specializedIsNode = specializationClassIsNode(specialization);
                builder.declaration(typeName, "cur", createSpecializationFieldAccess(frameState, specialization, true, false, null, null));
                builder.declaration(typeName, "original", "cur");
                builder.declaration(typeName, "update", "null");

                builder.startWhile().string("cur != null").end().startBlock();

                builder.startIf().string("cur == ").string(specializationLocalName).end().startBlock();

                builder.startIf().string("cur == original").end().startBlock();
                builder.statement("update = cur.next_");
                builder.end().startElseBlock();
                if (specializedIsNode) {
                    builder.statement("update = original.remove(this, ", specializationLocalName, ")");
                } else {
                    builder.statement("update = original.remove(", specializationLocalName, ")");
                }
                builder.end();
                builder.statement("break");
                builder.end(); // if cur == s0_
                builder.statement("cur = cur.next_");
                builder.end(); // while block

                builder.startIf();
                builder.string("cur != null && ");

                if (frameState.isInlinedNode()) {
                    String fieldName = createSpecializationFieldName(specialization);
                    builder.string("!this.", fieldName);
                    builder.startCall(".compareAndSet");
                    builder.tree(frameState.getValue(INLINED_NODE_INDEX).createReference());
                } else {
                    builder.string("!").string(createSpecializationClassUpdaterName(specialization)).startCall(".compareAndSet");
                    builder.string("this");
                }

                builder.string("original");
                builder.string("update");
                builder.end();
                builder.end().startBlock();
                builder.statement("continue");
                builder.end();
                builder.statement("break");
                builder.end();
                builder.end();
            }
            removeThisMethods.put(specialization, method);
        }
        CodeTreeBuilder builder = parent.create();
        builder.startStatement().startCall(method.getSimpleName().toString());
        if (inline) {
            builder.tree(frameState.getValue(INLINED_NODE_INDEX).createReference());
        }
        if (useSpecializationClass) {
            builder.string(specializationLocalName);
        }
        builder.end().end();
        builder.tree(createCallExecuteAndSpecialize(builder, forType, frameState));
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

        CodeTreeBuilder callBuilder = CodeTreeBuilder.createBuilder();
        callBuilder.startCall("this", targetType.getMethod());
        callBuilder.trees(bindings.toArray(new CodeTree[0]));
        callBuilder.variables(plugs.additionalArguments());
        callBuilder.end();

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder = builder.create();
        if (isVoid(forType.getReturnType())) {
            builder.statement(callBuilder.build());
            builder.returnStatement();
        } else {
            builder.startReturn();
            builder.tree(expectOrCast(returnType, forType, callBuilder.build()));
            builder.end();
        }
        return builder.build();
    }

    private CodeTree createCallExecuteAndSpecialize(CodeTreeBuilder parent, ExecutableTypeData forType, FrameState frameState) {
        if (!node.needsSpecialize()) {
            return createThrowUnsupported(parent, frameState);
        }

        TypeMirror returnType = node.getPolymorphicExecutable().getReturnType();
        String frame = null;
        if (needsFrameToExecute(node.getReachableSpecializations())) {
            frame = FRAME_VALUE;
        }

        CodeTreeBuilder builder = parent.create();
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

    private List<IfTriple> createMethodGuardChecks(FrameState frameState, SpecializationGroup group, Collection<GuardExpression> guardExpressions, NodeExecutionMode mode) {
        List<IfTriple> triples = new ArrayList<>();
        SpecializationData specialization = group.getSpecialization();
        for (GuardExpression guard : guardExpressions) {
            switch (mode) {
                case SLOW_PATH:
                    triples.addAll(initializeCachesForSlowPathGuard(frameState, mode, group, guard));
                    break;
                case FAST_PATH:
                    triples.addAll(initializeCaches(frameState, mode, group, specialization.getBoundCaches(guard.getExpression(), true), true, false));
                    break;
                case FALLBACK_GUARD:
                    triples.addAll(initializeCasts(frameState, group, guard.getExpression(), mode));
                    break;
                case UNCACHED:
                    // nothing to do
                    break;
                default:
                    throw new AssertionError("unhandled mode");
            }

            triples.add(createMethodGuardCheck(frameState, specialization, guard, mode));
        }
        return triples;
    }

    private List<IfTriple> initializeCachesForSlowPathGuard(FrameState frameState,
                    NodeExecutionMode mode, SpecializationGroup group, GuardExpression guard) {
        SpecializationData specialization = group.getSpecialization();
        boolean guardStateBit = guardNeedsStateBit(specialization, guard);
        if (!guardStateBit && guard.isConstantTrueInSlowPath(mode.isUncached())) {
            return Collections.emptyList();
        }

        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        StateQuery query = StateQuery.create(GuardActive.class, guard);
        SpecializationStateReference stateRef = null;
        if (guardStateBit) {
            if (specialization == null) {
                throw new AssertionError();
            }
            builder.tree(initializeSpecializationClass(frameState, group.getSpecialization(), false));
            stateRef = createStateReference(frameState, specialization, query);
        }

        Set<CacheExpression> boundCaches = group.getSpecialization().getBoundCaches(guard.getExpression(), true);

        List<IfTriple> triples = new ArrayList<>();

        /*
         * Initialize but not yet persist caches.
         */
        triples.addAll(initializeCaches(frameState, mode, group, boundCaches, true, false));
        triples.addAll(initializeCasts(frameState, group, guard.getExpression(), mode));
        IfTriple.materialize(builder, triples, true);

        FrameState innerFrameState = frameState;

        if (guardStateBit) {
            List<IfTriple> innerTriples = new ArrayList<>();
            innerFrameState = frameState.copy();

            builder.startIf().tree(stateRef.bitSet.createNotContains(stateRef.reference, StateQuery.create(GuardActive.class, guard))).end().startBlock();

            /*
             * Persist caches now, Only if the guard bit has not yet been set.
             */
            triples = new ArrayList<>();
            triples.addAll(initializeCaches(innerFrameState, mode, group, boundCaches, false, true));
            triples.addAll(initializeCasts(innerFrameState, group, guard.getExpression(), mode));
            IfTriple.materialize(builder, triples, true);

            builder.startStatement();
            builder.tree(stateRef.reference).string(" = ");
            builder.tree(stateRef.bitSet.createSetExpression(stateRef.reference, query, true));
            builder.end();

            innerTriples.addAll(persistSpecializationClass(innerFrameState, group.getSpecialization(), false));

            if (useSpecializationClass(specialization)) {
                CodeTreeBuilder b;
                if (needsDuplicationCheck(specialization)) {
                    b = builder.create();
                    b.startStatement();
                    b.string(createSpecializationLocalOriginalName(specialization));
                    b.string(" = ");
                    b.tree(createGetSpecializationClass(frameState, specialization, true));
                    b.end();
                    innerTriples.add(new IfTriple(null, null, b.build()));
                }

                b = builder.create();
                b.startStatement();
                String localName = createSpecializationLocalName(specialization);
                b.string(localName).string(" = ");
                boolean isNode = specializationClassIsNode(specialization);
                if (isNode) {
                    if (frameState.isInlinedNode()) {
                        b.startCall(frameState.getValue(INLINED_NODE_INDEX).createReference(), "insert");
                    } else {
                        b.startCall("this.insert");
                    }
                }
                if (!specializationClassNeedsCopyConstructor(specialization)) {
                    throw new AssertionError("Inconsistent copy constructor condition.");
                }
                b.startNew(createSpecializationClassReferenceType(specialization));
                b.string(localName);
                b.end();
                if (isNode) {
                    b.end();
                }
                b.end();

                innerTriples.add(new IfTriple(null, null, b.build()));
            }

            IfTriple.materialize(builder, innerTriples, true);

            builder.end();
        }

        return Arrays.asList(new IfTriple(builder.build(), null, null));
    }

    private static boolean specializationClassNeedsCopyConstructor(SpecializationData specialization) {
        if (!useSpecializationClass(specialization)) {
            return false;
        }

        if (!specialization.isReachesFallback()) {
            return false;
        }

        for (GuardExpression guard : specialization.getGuards()) {
            boolean guardStateBit = guardNeedsStateBit(specialization, guard);
            if (guardStateBit) {
                return true;
            }
        }

        return false;
    }

    private List<IfTriple> initializeCaches(FrameState frameState, NodeExecutionMode mode, SpecializationGroup group, Collection<CacheExpression> caches, boolean store, boolean forcePersist) {
        if (group.getSpecialization() == null || caches.isEmpty()) {
            return Collections.emptyList();
        }
        List<IfTriple> triples = new ArrayList<>();
        for (CacheExpression cache : caches) {
            if (cache.isEagerInitialize()) {
                triples.addAll(createLoadCacheClass(frameState, cache));
                continue;
            } else if (mode.isFastPath() && !cache.isAlwaysInitialized()) {
                triples.addAll(createLoadCacheClass(frameState, cache));
                continue;
            } else if (mode.isUncached() && cache.isWeakReference()) {
                continue;
            }
            boolean useStore = store;
            if (cache.isAlwaysInitialized() || sharedCaches.containsKey(cache)) {
                useStore = true;
            }
            triples.addAll(initializeCasts(frameState, group, cache.getDefaultExpression(), mode));
            triples.addAll(persistAndInitializeCache(frameState, mode, group.getSpecialization(), cache, useStore, forcePersist));
        }
        return triples;
    }

    private List<IfTriple> createLoadCacheClass(FrameState frameState, CacheExpression cache) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        createCacheClassAccess(frameState, b, cache);
        if (!b.isEmpty()) {
            return Arrays.asList(new IfTriple(b.build(), null, null));
        }
        return Collections.emptyList();
    }

    private Collection<IfTriple> persistAndInitializeCache(FrameState frameState, NodeExecutionMode mode, SpecializationData specialization, CacheExpression cache, boolean store, boolean persist) {
        List<IfTriple> triples = new ArrayList<>();
        CodeTree init = initializeCache(frameState, specialization, cache);

        if (store) {
            // store as local variable
            triples.addAll(storeCache(frameState, mode, specialization, cache, init));
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
        }

        List<IfTriple> triples = new ArrayList<>();
        String name = createFieldName(specialization, cache);
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

        String frameStateInitialized = name + "$initialized";
        if (frameState.getBoolean(frameStateInitialized, false)) {
            return Collections.emptyList();
        } else {
            frameState.setBoolean(frameStateInitialized, true);
        }

        CodeTree storeFence = null;

        /*
         * Make sure cached values are initialized before publishing it to other threads.
         *
         * This is needed for non-final fields in the cached value to be guaranteed to be
         * initialized.
         *
         * We can avoid the storeStore fence if there is a duplication check performed. With a
         * duplication check there is an implicit storeStore fence with the volatile write to
         * publish the specialization class.
         *
         * With a specialization class that is not bound in guards we have an explicit storeStore
         * fence already, so we do not do it for each cache.
         */
        if (!ElementUtils.isPrimitive(cache.getParameter().getType()) && //
                        !needsDuplicationCheck(specialization) && //
                        !useSpecializationClass(specialization)) {
            CodeTreeBuilder storeFenceBuilder = CodeTreeBuilder.createBuilder();
            storeFenceBuilder.startStatement();
            storeFenceBuilder.startStaticCall(context.getType(VarHandle.class), "storeStoreFence").end();
            storeFenceBuilder.end();
            storeFence = storeFenceBuilder.build();
        }

        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        value = createInsertNode(frameState, specialization, cache, value);

        boolean sharedCheck = sharedCaches.containsKey(cache);
        boolean defaultCheck = !cache.isWeakReference() && cache.isNeverDefault() && !cache.isNeverDefaultGuaranteed();

        if (sharedCheck || defaultCheck) {

            String localName = createCacheLocalName(cache);
            String defaultValue = ElementUtils.defaultValue(cache.getParameter().getType());
            boolean cacheInitialized = isCacheInitialized(frameState, specialization, cache);

            if (sharedCheck) {
                if (!cacheInitialized) {
                    builder.declaration(cache.getParameter().getType(), localName, value);
                }
                LocalVariable cacheClass = createCacheClassAccess(frameState, builder, cache);
                if (cacheClass != null) {
                    builder.startIf().tree(cacheClass.createReference()).string(" == null").end().startBlock();

                    builder.startStatement();
                    builder.string(cacheClass.getName(), " = ");
                    builder.tree(createInsertNode(frameState, specialization, cache, builder.create().startNew(createCacheClassType(cache)).end().build()));
                    builder.end();

                    builder.startStatement();
                    if (storeFence != null) {
                        builder.tree(storeFence);
                    }
                    builder.tree(createCacheAccess(frameState, specialization, cache, CodeTreeBuilder.singleString(localName)));
                    builder.end();

                    builder.startStatement();
                    builder.startStaticCall(context.getType(VarHandle.class), "storeStoreFence").end();
                    builder.end();

                    builder.startStatement();
                    builder.tree(createInlinedAccess(frameState, null, CodeTreeBuilder.singleString("this." + name), cacheClass.createReference()));
                    builder.end();

                    builder.end();

                } else {
                    if (!cache.isEagerInitialize()) {
                        if (cache.isEncodedEnum()) {
                            IfTriple triple = createNeverDefaultGuard(frameState, specialization, cache, " == ");
                            builder.startIf().tree(triple.condition).end().startBlock();
                        } else {
                            builder.startIf().tree(createCacheAccess(frameState, specialization, cache, null)).string(" == ").string(defaultValue).end().startBlock();
                        }
                    }

                    if (!cacheInitialized) {
                        checkSharedCacheNull(builder, localName, specialization, cache);
                    }

                    if (storeFence != null) {
                        builder.tree(storeFence);
                    }
                    builder.startStatement();
                    builder.tree(createCacheAccess(frameState, specialization, cache, CodeTreeBuilder.singleString(localName)));
                    builder.end();

                    if (!cache.isEagerInitialize()) {
                        builder.end();
                    }
                }
                builder.end();
                builder.end();

            } else if (defaultCheck) {
                if (cacheInitialized) {
                    GeneratorUtils.mergeSuppressWarnings(frameState.method, "unused");
                } else {
                    builder.declaration(cache.getParameter().getType(), localName, value);
                    value = CodeTreeBuilder.singleString(localName);
                }
                String message = String.format(
                                "A specialization cache returned a default value. The cache initializer must never return a default value for this cache. " +
                                                "Use @%s(neverDefault=false) to allow default values for this cached value or make sure the cache initializer never returns the default value.",
                                getSimpleName(types.Cached));

                if (ElementUtils.isPrimitive(cache.getParameter().getType())) {
                    builder.startIf().tree(value).string(" == ").string(defaultValue).end().startBlock();
                    builder.startThrow().startNew(context.getType(NullPointerException.class)).doubleQuote(message).end().end();
                    builder.end();
                } else {
                    builder.startStatement().startStaticCall(context.getType(Objects.class), "requireNonNull").tree(value).doubleQuote(message).end().end();
                }

                builder.end();
                if (storeFence != null) {
                    builder.tree(storeFence);
                }
                builder.startStatement().tree(createCacheAccess(frameState, specialization, cache, CodeTreeBuilder.singleString(localName))).end();
            } else {
                throw new AssertionError();
            }
            setCacheInitialized(frameState, specialization, cache, true);

        } else {
            if (storeFence != null) {
                builder.tree(storeFence);
            }
            builder.startStatement().tree(createCacheAccess(frameState, specialization, cache, value)).end();
        }

        triples.add(new IfTriple(builder.build(), null, null));
        return triples;

    }

    private CodeTree createInsertNode(FrameState frameState, SpecializationData specialization, CacheExpression cache, CodeTree value) {
        if (cache.isAlwaysInitialized()) {
            return value;
        } else if (cache.isBind()) {
            return value;
        } else if (!cache.isAdopt()) {
            return value;
        }

        Parameter parameter = cache.getParameter();
        TypeMirror type = parameter.getType();
        TypeMirror nodeType = types.Node;
        TypeMirror nodeArrayType = new ArrayCodeTypeMirror(types.Node);
        boolean isNode = isAssignable(parameter.getType(), nodeType);
        boolean isNodeInterface = isNode || isAssignable(type, types.NodeInterface);
        boolean isNodeArray = isAssignable(type, nodeArrayType);
        boolean isNodeInterfaceArray = isNodeArray || isNodeArray(type);

        if (isNodeInterface || isNodeInterfaceArray) {
            CodeTree insertTarget;
            if (frameState.isSpecializationClassInitialized(specialization) && specializationClassIsNode(specialization)) {
                insertTarget = singleString(createSpecializationLocalName(specialization));
            } else {
                insertTarget = createNodeAccess(frameState);
            }
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

            CodeTreeBuilder noCast = new CodeTreeBuilder(null);
            if (castType == null) {
                noCast.startCall(insertTarget, "insert");
            } else {
                noCast.startStaticCall(types.DSLSupport, "maybeInsert");
                noCast.tree(insertTarget);
            }
            noCast.tree(value);
            noCast.end();
            return noCast.build();
        }
        return value;
    }

    private static CodeTree createNodeAccess(FrameState frameState, SpecializationData specialization) {
        if (specialization != null && substituteNodeWithSpecializationClass(specialization) && !frameState.getMode().isUncached()) {
            return singleString(createSpecializationLocalName(specialization));
        } else {
            return createNodeAccess(frameState);
        }
    }

    private static CodeTree createNodeAccess(FrameState frameState) {
        if (frameState.isInlinedNode()) {
            return frameState.getValue(INLINED_NODE_INDEX).createReference();
        } else {
            return singleString("this");
        }
    }

    private Map<String, List<Parameter>> uniqueCachedParameterLocalNames = new HashMap<>();

    private Collection<IfTriple> storeCache(FrameState frameState, NodeExecutionMode mode, SpecializationData specialization, CacheExpression cache, CodeTree value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (isCacheInitialized(frameState, specialization, cache)) {
            // already initialized
            return Collections.emptyList();
        }

        TypeMirror type = cache.getParameter().getType();
        CodeTreeBuilder builder = new CodeTreeBuilder(null);
        String refName = createCacheLocalName(cache);

        if (mode.isSlowPath() && cacheNeedsSpecializationClass(frameState, specialization, cache)) {
            builder.tree(initializeSpecializationClass(frameState, specialization, false));
        }

        CodeTree useValue = createInsertNode(frameState, specialization, cache, value);

        if (sharedCaches.containsKey(cache)) {
            builder.declaration(type, refName, (CodeTree) null);

            LocalVariable cacheClass = createCacheClassAccess(frameState, builder, cache);
            if (cacheClass != null) {
                builder.startIf().tree(cacheClass.createReference()).string(" != null").end().startBlock();
                builder.startStatement();
                builder.string(refName).string(" = ").tree(createCacheAccess(frameState, specialization, cache, null));
                builder.end();
                builder.end().startElseBlock();
                builder.startStatement();
                builder.string(refName).string(" = ").tree(useValue);
                builder.end();
            } else {
                String sharedName = refName + "_shared";
                builder.declaration(type, sharedName, createCacheAccess(frameState, specialization, cache, null));
                builder.startIf().string(sharedName).string(" != ").string(ElementUtils.defaultValue(cache.getParameter().getType())).end().startBlock();
                builder.startStatement();
                builder.string(refName).string(" = ").string(sharedName);
                builder.end();
                builder.end().startElseBlock();
                builder.startStatement();
                builder.string(refName).string(" = ").tree(useValue);
                builder.end();
                checkSharedCacheNull(builder, refName, specialization, cache);
            }
            builder.end();
            builder.end();

            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.tree(createCacheAccess(frameState, specialization, cache, null));
            b.string(" == ");
            b.string(ElementUtils.defaultValue(cache.getParameter().getType()));
            b.string(" ? ");
            b.tree(useValue);
            b.string(" : ");
            b.tree(createCacheAccess(frameState, specialization, cache, null));

            useValue = b.build();
        } else if (cache.isAlwaysInitialized() && frameState.getMode().isSlowPath()) {
            builder.startStatement().string(refName, " = ").tree(useValue).end();
        } else {
            builder.declaration(type, refName, useValue);
        }

        setCacheInitialized(frameState, specialization, cache, true);

        List<IfTriple> triples = new ArrayList<>();
        triples.add(new IfTriple(builder.build(), null, null));
        return triples;
    }

    private static boolean cacheNeedsSpecializationClass(FrameState frameState, SpecializationData specialization, CacheExpression cache) {
        return frameState.getMode().isSlowPath() && substituteNodeWithSpecializationClass(specialization) && specialization.isNodeReceiverBound(cache.getDefaultExpression());
    }

    private void checkSharedCacheNull(CodeTreeBuilder builder, String refName, SpecializationData specialization, CacheExpression cache) {
        if (useCacheClass(specialization, cache)) {
            // no shared check needed.
            return;
        }
        if (cache.isEagerInitialize()) {
            // always default for eager initialization
            return;
        }
        builder.startIf().string(refName).string(" == ").string(ElementUtils.defaultValue(cache.getParameter().getType())).end().startBlock();
        builder.startThrow().startNew(context.getType(IllegalStateException.class));
        builder.doubleQuote("A specialization returned a default value for a cached initializer. " +
                        "Default values are not supported for shared cached initializers because the default value is reserved for the uninitialized state.");
        builder.end().end(); // new, throw

        builder.end();
    }

    private static boolean isCacheInitialized(FrameState frameState, SpecializationData specialization, CacheExpression cache) {
        return frameState.getCacheInitialized(specialization, cache) != null;
    }

    private void setCacheInitialized(FrameState frameState, SpecializationData specialization, CacheExpression cache, boolean initialized) {
        String name = createFieldName(specialization, cache);
        if (initialized) {
            frameState.set(name, new LocalVariable(cache.getParameter().getType(), name,
                            CodeTreeBuilder.singleString(createCacheLocalName(cache))));
        } else {
            frameState.set(name, null);
        }
    }

    private String createCacheLocalName(CacheExpression cache) {
        String name = sharedCaches.get(cache);
        if (name == null) {
            name = firstLetterLowerCase(cache.getParameter().getLocalName()) + "_";
        }
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
        String name = createFieldName(specialization, cache);
        if (frameState.get(name) != null) {
            // already initialized
            return null;
        }
        boolean aot = frameState.getBoolean(AOT_STATE, false);

        CodeTree tree;
        if (cache.isMergedLibrary()) {
            if (frameState.getMode().isUncached()) {
                CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
                builder.staticReference(createLibraryConstant(constants, cache.getParameter().getType()));
                builder.startCall(".getUncached");
                builder.tree(writeExpression(frameState, specialization, cache.getDefaultExpression()));
                builder.end();
                tree = builder.build();
            } else {
                tree = CodeTreeBuilder.singleString("this." + cache.getMergedLibraryIdentifier());
            }
        } else {
            DSLExpression expression;
            if (frameState.getMode().isUncached()) {
                expression = cache.getUncachedExpression();
            } else {
                expression = cache.getDefaultExpression();
                if (aot && expression != null) {
                    expression = substituteManualToAutoDispatch(expression);
                }
                if (cache.getInlinedNode() != null) {
                    // no initialization necessary
                    return null;
                } else {
                    if (specialization.needsTruffleBoundary() &&
                                    (specialization.isAnyLibraryBoundInGuard() || specialization.needsVirtualFrame())) {
                        /*
                         * Library.getUncached() should be used instead of
                         * Library.getUncached(receiver) in order to avoid non TruffleBoundary
                         * virtual dispatches on the compiled code path.
                         */
                        expression = substituteToDispatchedUncached(expression);
                    }
                }
            }
            tree = writeExpression(frameState, specialization, expression);

        }
        return tree;
    }

    private DSLExpression substituteManualToAutoDispatch(DSLExpression expression) {
        return expression.reduce(new DSLExpressionReducer() {
            public DSLExpression visitVariable(Variable binary) {
                return binary;
            }

            public DSLExpression visitNegate(Negate negate) {
                return negate;
            }

            public DSLExpression visitCall(Call call) {
                if (call.getName().equals("create") && ElementUtils.typeEquals(call.getResolvedMethod().getEnclosingElement().asType(), types.LibraryFactory)) {
                    // we can actually use any limit other then 0 as this would go directly to
                    // uncached. We use 2 to avoid single entry optimizations that might trigger in
                    // the future.
                    Call newCall = new Call(call.getReceiver(), call.getName(), Arrays.asList(new DSLExpression.IntLiteral("2")));
                    newCall.setResolvedMethod(ElementUtils.findExecutableElement(types.LibraryFactory,
                                    "createDispatched", 1));
                    newCall.setResolvedTargetType(call.getResolvedTargetType());
                    return newCall;
                }
                return call;
            }

            public DSLExpression visitBinary(Binary binary) {
                return binary;
            }
        });
    }

    private DSLExpression substituteToDispatchedUncached(DSLExpression expression) {
        return expression.reduce(new DSLExpressionReducer() {
            public DSLExpression visitVariable(Variable binary) {
                return binary;
            }

            public DSLExpression visitNegate(Negate negate) {
                return negate;
            }

            public DSLExpression visitCall(Call call) {
                if (call.getName().equals("getUncached") && ElementUtils.typeEquals(call.getResolvedMethod().getEnclosingElement().asType(), types.LibraryFactory)) {
                    Call newCall = new Call(call.getReceiver(), call.getName(), Collections.emptyList());
                    newCall.setResolvedMethod(ElementUtils.findExecutableElement(types.LibraryFactory, "getUncached", 0));
                    newCall.setResolvedTargetType(call.getResolvedTargetType());
                    return newCall;
                }
                return call;
            }

            public DSLExpression visitBinary(Binary binary) {
                return binary;
            }
        });
    }

    private IfTriple createMethodGuardCheck(FrameState frameState, SpecializationData specialization, GuardExpression guard, NodeExecutionMode mode) {
        CodeTreeBuilder init = CodeTreeBuilder.createBuilder();
        CodeTreeBuilder condition = CodeTreeBuilder.createBuilder();
        DSLExpression expression = optimizeExpression(guard.getExpression());

        CodeTree assertion = null; // overrule with assertion
        if (mode.isFastPath()) {
            CodeTree guardExpression = writeExpression(frameState, specialization, expression);

            /*
             * We do not need to invoke a guard on the fast-path if:
             *
             * (1) the guard is guaranteed constant true of the fast-path, after it was invoked in
             * the slow-path.
             *
             * (2) The guard is not a weak reference. Weak references do not bind dynamic
             * parameters, but need to be checked each time.
             *
             * (3) The guard needs a state bit and may be partially initialized.
             */
            if (guard.isFastPathIdempotent()) {
                assertion = CodeTreeBuilder.createBuilder().startAssert().startStaticCall(types.DSLSupport, "assertIdempotence").tree(guardExpression).end().end().build();
            } else {
                condition.tree(guardExpression);
            }
        } else if (mode.isSlowPath() || mode.isUncached()) {

            if (mode.isSlowPath() && specialization.isNodeReceiverBound(expression) && substituteNodeWithSpecializationClass(specialization)) {
                init.tree(initializeSpecializationClass(frameState, specialization, false));
            }

            CodeTree guardExpression = writeExpression(frameState, specialization, expression);
            if (guard.isConstantTrueInSlowPath(mode.isUncached())) {
                assertion = CodeTreeBuilder.createBuilder().startStatement().string("// assert ").tree(guardExpression).end().build();
            } else {
                condition.tree(guardExpression);
            }
        } else if (mode.isGuardFallback()) {

            GuardExpression guardWithBit = getGuardThatNeedsStateBit(specialization, guard);
            if (guardWithBit != null) {
                condition.string("(");
                StateQuery query = StateQuery.create(GuardActive.class, guardWithBit);
                SpecializationStateReference ref = createStateReference(frameState, specialization, StateQuery.create(GuardActive.class, guardWithBit));

                if (useSpecializationClass(specialization)) {
                    condition.tree(createGetSpecializationClass(frameState, specialization, true));
                    condition.string(" == null || ");
                }

                condition.tree(ref.bitSet.createNotContains(ref.reference, query));
                condition.string(" || ");

                for (CacheExpression cache : getNullCachesDueToFallback(specialization, guard)) {
                    String localName = createCacheLocalName(cache);
                    LocalVariable cacheWrapper = createCacheClassAccess(frameState, init, cache);
                    if (cacheWrapper == null) {

                        if (!isCacheInitialized(frameState, specialization, cache)) {
                            init.startStatement();
                            init.type(cache.getParameter().getType());
                            init.string(" ", localName, " = ");

                            if (useSpecializationClass(specialization) && cache.getSharedGroup() == null) {
                                init.tree(createGetSpecializationClass(frameState, specialization, true));
                                init.string(" == null ? null : ");
                            }

                            init.tree(createCacheAccess(frameState, specialization, cache, null));
                            init.end();
                            setCacheInitialized(frameState, specialization, cache, true);
                        }

                        condition.string(localName).string(" == ").defaultValue(cache.getParameter().getType());
                        condition.string(" || ");

                    } else {
                        condition.tree(cacheWrapper.createReference()).string(" == null");
                        condition.string(" || ");
                    }

                }

                condition.tree(writeExpression(frameState, specialization, expression));
                condition.string(")");
                fallbackNeedsState = true;
            } else {
                condition.tree(writeExpression(frameState, specialization, expression));
            }
        }

        return new IfTriple(init.build(), condition.build(), assertion);
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
            } else {
                CodeTree tree = plugs.bindExpressionValue(frameState, variable);
                if (tree != null) {
                    bindings.put(variable, new LocalVariable(variable.getResolvedType(), "$bytecode", tree));
                } else {
                    if (specialization.isNodeReceiverVariable(variable.getResolvedVariable())) {
                        CodeTree accessor = createNodeAccess(frameState, specialization);
                        if (substituteNodeWithSpecializationClass(specialization) && !frameState.mode.isUncached()) {
                            String localName = createSpecializationLocalName(specialization);
                            bindings.put(variable, new LocalVariable(types.Node, localName, accessor));
                        } else {
                            bindings.put(variable, new LocalVariable(variable.getResolvedType(), "this", accessor));
                        }
                    }
                }

            }
        }

        return bindings;
    }

    private LocalVariable bindExpressionVariable(FrameState frameState, SpecializationData specialization, Parameter resolvedParameter) {
        LocalVariable localVariable;
        if (resolvedParameter.getSpecification().isCached()) {
            // bind cached variable
            CacheExpression cache = specialization.findCache(resolvedParameter);
            String cachedMemberName = createFieldName(specialization, cache);
            localVariable = frameState.get(cachedMemberName);
            CodeTree ref;
            if (localVariable == null) {
                ref = createCacheAccess(frameState, specialization, cache, null);
            } else {
                ref = localVariable.createReference();
            }
            localVariable = new LocalVariable(resolvedParameter.getType(), cachedMemberName, ref);
        } else {
            // bind local variable
            if (resolvedParameter.getSpecification().isSignature()) {
                NodeExecutionData execution = resolvedParameter.getSpecification().getExecution();
                if (!frameState.getMode().isUncached() && specialization.isNodeReceiverVariable(resolvedParameter.getVariableElement())) {
                    if (substituteNodeWithSpecializationClass(specialization)) {
                        String localName = createSpecializationLocalName(specialization);
                        localVariable = new LocalVariable(types.Node, localName, createGetSpecializationClass(frameState, specialization, true));
                    } else {
                        if (frameState.isInlinedNode()) {
                            localVariable = frameState.getValue(execution);
                        } else {
                            localVariable = new LocalVariable(types.Node, "this", CodeTreeBuilder.singleString("this"));
                        }
                    }
                } else {
                    localVariable = frameState.getValue(execution);
                }
            } else {
                localVariable = frameState.get(resolvedParameter.getLocalName());
            }
        }
        return localVariable;
    }

    public static boolean substituteNodeWithSpecializationClass(SpecializationData specialization) {
        if (!useSpecializationClass(specialization)) {
            return false;
        }
        if (!specializationClassIsNode(specialization)) {
            return false;
        }
        if (useParentInlinedAccess(specialization)) {
            // we always need to pass the target node if that happens.
            return true;
        } else if (hasSharedInlinedCache(specialization)) {
            return false;
        }
        if (specialization.hasMultipleInstances()) {
            return true;
        }

        for (CacheExpression cache : specialization.getCaches()) {
            if (cache.getSharedGroup() == null && cache.getInlinedNode() != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSharedInlinedCache(SpecializationData specialization) {
        boolean hasSharedInlined = false;
        for (CacheExpression cache : specialization.getCaches()) {
            if (cache.getInlinedNode() != null && !canCacheBeStoredInSpecialializationClass(cache) && cache.getSharedGroup() != null) {
                hasSharedInlined = true;
                break;
            }
        }
        return hasSharedInlined;
    }

    private CodeTree createSpecializationFieldAccess(FrameState frameState, SpecializationData specialization, boolean useSpecializationClass, boolean useSpecializationClassLocal, String fieldName,
                    CodeTree value) {
        CodeTreeBuilder builder = new CodeTreeBuilder(null);

        if (value != null && fieldName == null) {
            if (useSpecializationClass(specialization)) {
                builder.string("this.", createSpecializationFieldName(specialization));
                return createInlinedAccess(frameState, null, builder.build(), value);
            } else {
                throw new AssertionError("Cannot set this");
            }
        } else {
            CodeTree specializationClass = useSpecializationClass ? createGetSpecializationClass(frameState, specialization, useSpecializationClassLocal) : null;
            if (specializationClass != null) {
                builder.tree(specializationClass);

                if (fieldName != null) {
                    builder.string(".");
                    builder.string(fieldName);
                }

                if (value != null) {
                    builder.string(" = ").tree(value);
                }
            } else {
                if (fieldName == null) {
                    throw new AssertionError("Invalid specialization field access.");
                }
                builder.string("this.");
                builder.string(fieldName);
                return createInlinedAccess(frameState, specialization, builder.build(), value);
            }
        }

        return builder.build();
    }

    private CodeTree createGetSpecializationClass(FrameState frameState, SpecializationData specialization, boolean useLocal) {
        if (useSpecializationClass(specialization)) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            String localName = createSpecializationLocalName(specialization);
            LocalVariable var = useLocal ? frameState.get(localName) : null;
            if (var != null) {
                builder.string(localName);
                return builder.build();
            } else {
                builder.string("this.", createSpecializationFieldName(specialization));
                return createInlinedAccess(frameState, null, builder.build(), null);
            }
        } else {
            return null;
        }
    }

    private LocalVariable createCacheClassAccess(FrameState frameState,
                    CodeTreeBuilder builder, CacheExpression cache) {
        if (cache.getSharedGroup() == null) {
            return null;
        }
        if (!useCacheClass(null, cache)) {
            return null;
        }

        LocalVariable var = frameState.getCacheClassInitialized(cache);
        if (var != null) {
            // already initialized
            return var;
        }
        if (builder != null) {
            TypeMirror type = createCacheClassType(cache);
            String name = createFieldName(null, cache);
            String localName = name + "_wrapper";
            builder.declaration(type, localName, createInlinedAccess(frameState, null, CodeTreeBuilder.singleString("this." + name), null));
            var = new LocalVariable(type, localName, CodeTreeBuilder.singleString(localName));
            frameState.set(frameState.createCacheClassInitializedKey(cache), var);
        }

        return var;
    }

    private CodeTree createCacheAccess(FrameState frameState, SpecializationData specialization, CacheExpression cache, CodeTree value) {
        if (cache == null) {
            return CodeTreeBuilder.singleString("null /* cache not resolved */");
        }
        if (frameState.getMode().isUncached() || cache.isAlwaysInitialized()) {
            return initializeCache(frameState, specialization, cache);
        }
        if (cache.getInlinedNode() != null) {
            if (frameState.isInlinedNode() && needsInlineTarget(specialization, cache)) {
                CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
                String cacheFieldName = createLocalCachedInlinedName(specialization, cache);
                builder.string("this.", cacheFieldName);
                if (value != null) {
                    throw new AssertionError("Cannot set inlined field.");
                }
                return builder.build();
            } else {
                return CodeTreeBuilder.singleString(createStaticInlinedCacheName(specialization, cache));
            }
        } else if (cache.isEncodedEnum()) {
            if (value == null) {
                return createDecodeEnum(frameState, specialization, cache);
            } else {
                return createEncodeEnum(frameState, specialization, cache, value);
            }
        } else if (cache.getSharedGroup() != null) {
            String cacheFieldName = createFieldName(specialization, cache);
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            LocalVariable cacheClass = createCacheClassAccess(frameState, null, cache);
            if (cacheClass != null) {
                builder.tree(cacheClass.createReference()).string(".delegate");
                if (value != null) {
                    builder.string(" = ").tree(value);
                }
                return builder.build();
            } else {
                builder.string("this.").string(cacheFieldName);
                if (useCacheClass(specialization, cache)) {
                    builder.string(".delegate");
                }
                CodeTree nodeAccess = createNodeAccess(frameState);
                return createInlinedAccess(frameState, specialization, builder.build(), value, nodeAccess);
            }
        } else {
            String cacheFieldName = createLocalCachedInlinedName(specialization, cache);
            return createSpecializationFieldAccess(frameState, specialization, true, true, cacheFieldName, value);
        }
    }

    private CodeTree createEncodeEnum(FrameState frameState, SpecializationData specialization, CacheExpression cache, CodeTree value) {
        CodeTreeBuilder innerValue = CodeTreeBuilder.createBuilder();

        innerValue.startGroup().string("(");
        if (cache.isNeverDefaultGuaranteed()) {
            innerValue.tree(value);
            innerValue.string(".ordinal()");
        } else {
            CodeExecutableElement method = lookupEncodeEnum(cache.getParameter().getType());
            innerValue.startCall(method.getSimpleName().toString());
            innerValue.tree(value);
            innerValue.end();
        }
        innerValue.string(" + ").string(getEncodedEnumOffset(cache));
        innerValue.string(")").end();

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        StateQuery query = StateQuery.create(EncodedEnumState.class, lookupSharedCacheKey(cache));
        SpecializationStateReference ref = createStateReference(frameState, specialization, query);

        if (cache.getSharedGroup() != null && !cache.isEagerInitialize()) {
            CodeTree stateRef = CodeTreeBuilder.createBuilder().string("this.", ref.bitSet.getName(), "_").build();
            builder.tree(createInlinedAccess(frameState, specialization, stateRef, ref.bitSet.createSetInteger(ref.reference, query, innerValue.build())));
        } else {
            builder.tree(ref.bitSet.createSetInteger(ref.reference, query, innerValue.build()));
            // persisted later when specialization is committed
        }

        return builder.build();
    }

    private static int getEncodedEnumOffset(CacheExpression cache) {
        if (cache.isNeverDefault()) {
            return 1;
        } else {
            return 2;
        }
    }

    private CodeTree createDecodeEnum(FrameState frameState, SpecializationData specialization, CacheExpression cache) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        CodeExecutableElement decodeMethod = lookupDecodeEnum(cache.getParameter().getType());
        StateQuery query = StateQuery.create(EncodedEnumState.class, lookupSharedCacheKey(cache));

        builder.startCall(decodeMethod.getSimpleName().toString());
        builder.startGroup().string("(");

        SpecializationStateReference reference = createStateReference(frameState, specialization, query);
        builder.tree(reference.bitSet.createExtractInteger(reference.reference, query));
        builder.string(")");
        builder.string(" - ").string(getEncodedEnumOffset(cache));
        builder.end(); // group
        builder.end(); // call
        return builder.build();
    }

    static class SpecializationStateReference {

        final BitSet bitSet;
        final CodeTree reference;

        SpecializationStateReference(BitSet set, CodeTree reference) {
            Objects.requireNonNull(set);
            Objects.requireNonNull(reference);
            this.bitSet = set;
            this.reference = reference;
        }

    }

    private SpecializationStateReference createStateReference(FrameState frameState, SpecializationData specialization, StateQuery query) {
        MultiStateBitSet multiSet = specialization != null ? lookupSpecializationState(specialization) : null;
        BitSet foundSet = null;
        if (multiSet != null) {
            foundSet = multiSet.findSet(query);
        }
        CodeTree reference;
        if (foundSet == null) { // state is in node
            multiSet = multiState;
            foundSet = multiSet.findSet(query);
            if (foundSet == null) {
                throw new AssertionError("Could not find state.");
            }
            reference = foundSet.createReference(frameState);
        } else { // state is in specialization class
            CodeTreeBuilder inner = CodeTreeBuilder.createBuilder();
            inner.tree(createGetSpecializationClass(frameState, specialization, true));
            inner.string(".", foundSet.getName(), "_");
            reference = inner.build();
        }
        return new SpecializationStateReference(foundSet, reference);
    }

    private CodeExecutableElement lookupEncodeEnum(TypeMirror mirror) {
        String typeId = ElementUtils.getUniqueIdentifier(mirror);
        CodeExecutableElement method = constants.encodeConstants.get(typeId);
        if (method == null) {
            String methodName = constants.reserveSymbol(mirror, "encode" + ElementUtils.firstLetterUpperCase(ElementUtils.getTypeSimpleId(mirror)));
            method = new CodeExecutableElement(modifiers(PRIVATE, STATIC), context.getType(int.class), methodName);
            method.addParameter(new CodeVariableElement(mirror, "e"));
            CodeTreeBuilder builder = method.createBuilder();
            builder.startIf().string("e != null").end().startBlock();
            builder.statement("return e.ordinal()");
            builder.end().startElseBlock();
            builder.statement("return -1");
            builder.end();
            constants.encodeConstants.put(typeId, method);
        }
        return method;
    }

    private CodeExecutableElement lookupDecodeEnum(TypeMirror mirror) {
        String typeId = ElementUtils.getUniqueIdentifier(mirror);
        CodeExecutableElement method = constants.decodeConstants.get(typeId);
        if (method == null) {
            String methodName = constants.reserveSymbol(mirror, "decode" + ElementUtils.firstLetterUpperCase(ElementUtils.getTypeSimpleId(mirror)));
            method = new CodeExecutableElement(modifiers(PRIVATE, STATIC), mirror, methodName);
            method.addParameter(new CodeVariableElement(context.getType(int.class), "state"));
            CodeTreeBuilder builder = method.createBuilder();
            builder.startIf().string("state >= 0").end().startBlock();
            builder.startReturn().string(lookupEnumConstants(mirror).getName()).string("[state]").end();
            builder.end().startElseBlock();
            builder.statement("return null");
            builder.end();
            constants.decodeConstants.put(typeId, method);
        }
        return method;
    }

    private CodeVariableElement lookupEnumConstants(TypeMirror mirror) {
        String typeId = ElementUtils.getUniqueIdentifier(mirror);
        CodeVariableElement var = constants.enumValues.get(typeId);
        if (var == null) {
            String constantName = constants.reserveSymbol(mirror, ElementUtils.createConstantName(ElementUtils.getTypeSimpleId(mirror)) + "_VALUES");
            var = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), new ArrayCodeTypeMirror(mirror), constantName);
            addCompilationFinalAnnotation(var, 1);
            CodeTreeBuilder init = var.createInitBuilder();
            init.startStaticCall(types.DSLSupport, "lookupEnumConstants").typeLiteral(mirror).end();
            constants.enumValues.put(typeId, var);
        }
        return var;
    }

    @SuppressWarnings("unused")
    static CodeTree createInlinedAccess(FrameState frameState, SpecializationData specialization, CodeTree reference, CodeTree value, CodeTree nodeReference) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.tree(reference);
        if (frameState != null && frameState.isInlinedNode()) {
            if (value == null) {
                builder.startCall(".get").tree(nodeReference).end();
            } else {
                builder.startCall(".set").tree(nodeReference).tree(value).end();
            }
        } else {
            if (value != null) {
                builder.string(" = ").tree(value);
            }
        }
        return builder.build();
    }

    static CodeTree createInlinedAccess(FrameState frameState, SpecializationData specialization, CodeTree reference, CodeTree value) {
        return createInlinedAccess(frameState, specialization, reference, value, createNodeAccess(frameState, specialization));
    }

    private CodeTree createAssumptionReference(FrameState frameState, SpecializationData s, AssumptionExpression a) {
        String assumptionFieldName = createAssumptionFieldName(s, a);
        return createSpecializationFieldAccess(frameState, s, true, true, assumptionFieldName, null);
    }

    private IfTriple createTypeCheckOrCast(FrameState frameState, SpecializationGroup group, TypeGuard typeGuard,
                    NodeExecutionMode specializationExecution, boolean castOnly, boolean forceImplicitCast) {
        CodeTreeBuilder prepareBuilder = CodeTreeBuilder.createBuilder();
        CodeTreeBuilder checkBuilder = CodeTreeBuilder.createBuilder();
        int signatureIndex = typeGuard.getSignatureIndex();
        LocalVariable value = frameState.getValue(signatureIndex);
        TypeMirror targetType = typeGuard.getType();

        if (value == null) {
            return null;
        }

        if (!needsCastTo(value.getTypeMirror(), targetType)) {
            Parameter parameter = node.getFallbackSpecialization().findParameter(node.getChildExecutions().get(signatureIndex));
            if (parameter == null || typeEquals(value.getTypeMirror(), parameter.getType())) {
                // no implicit casts needed if it matches the generic type
                return null;
            }

            boolean foundImplicitSubType = false;
            if (forceImplicitCast) {
                List<ImplicitCastData> casts = typeSystem.lookupByTargetType(targetType);
                for (ImplicitCastData cast : casts) {
                    if (ElementUtils.isSubtype(cast.getSourceType(), targetType)) {
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
                    implicitState = multiState.createExtractInteger(frameState, StateQuery.create(ImplicitCastState.class, typeGuard));
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
        if (expression == null) {
            return Collections.emptyList();
        }
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
                        continue;
                    }

                    IfTriple triple = createTypeCheckOrCast(frameState, group, new TypeGuard(typeSystem, p.getType(), execution.getIndex()), specializationExecution, true,
                                    false);
                    if (triple != null) {
                        triples.add(triple);
                    }
                }
            }
        }
        return triples;
    }

    private ExecutableTypeData createExecuteAndSpecializeType() {
        if (node.getPolymorphicExecutable() == null) {
            return null;
        }
        TypeMirror polymorphicType = node.getPolymorphicExecutable().getReturnType();
        List<TypeMirror> parameters = new ArrayList<>();
        for (TypeMirror param : node.getPolymorphicExecutable().getSignatureParameters()) {
            parameters.add(param);
        }
        return new ExecutableTypeData(node, polymorphicType, createExecuteAndSpecializeName(), node.getFrameType(), parameters);
    }

    private List<TypeMirror> resolveOptimizedImplicitSourceTypes(NodeExecutionData execution, TypeMirror targetType) {
        Collection<TypeMirror> allSourceTypes = typeSystem.lookupSourceTypes(targetType);
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
        List<TypeMirror> originalSourceTypes = new ArrayList<>(typeSystem.lookupSourceTypes(target.getTypeMirror()));
        List<TypeMirror> sourceTypes = resolveOptimizedImplicitSourceTypes(execution, target.getTypeMirror());
        StateQuery implicitTypeGuardQuery = StateQuery.create(ImplicitCastState.class, new TypeGuard(typeSystem, target.getTypeMirror(), execution.getIndex()));
        boolean throwsUnexpected = false;
        boolean elseIf = false;
        for (TypeMirror sourceType : sourceTypes) {
            ExecutableTypeData executableType = resolveTargetExecutable(execution, sourceType);
            elseIf = builder.startIf(elseIf);
            throwsUnexpected |= executableType.hasUnexpectedValue();
            builder.startGroup();
            CodeTree tree = multiState.createContainsOnly(frameState, originalSourceTypes.indexOf(sourceType), 1, implicitTypeGuardQuery, implicitTypeGuardQuery);
            if (!tree.isEmpty()) {
                builder.tree(tree);
                builder.string(" && ");
            }
            builder.tree(multiState.createIsNotAny(frameState, StateQuery.create(SpecializationActive.class, node.getReachableSpecializations())));
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
        CodeTree implicitState = multiState.createExtractInteger(frameState, implicitTypeGuardQuery);
        builder.tree(TypeSystemCodeGenerator.implicitExpectFlat(typeSystem, target.getTypeMirror(), genericValue.createReference(), implicitState));
        builder.end();

        if (!sourceTypes.isEmpty()) {
            builder.end();
        }
        return new ChildExecutionResult(builder.build(), throwsUnexpected);
    }

    private void generateTraceOnEnterCall(CodeTreeBuilder builder, FrameState frameState) {
        if (node.isGenerateTraceOnEnter()) {
            ArrayType objectArray = new ArrayCodeTypeMirror(context.getType(Object.class));
            builder.startIf().startCall("isTracingEnabled").end(2);
            builder.startBlock().startStatement().startCall("traceOnEnter").startNewArray(objectArray, null);
            frameState.addReferencesTo(builder);
            builder.end(4);  // new array, call traceOnEnter, statement, block
        }
    }

    private void generateTraceOnExceptionStart(CodeTreeBuilder builder) {
        if (node.isGenerateTraceOnException()) {
            builder.startTryBlock();
        }
    }

    private void generateTraceOnExceptionEnd(CodeTreeBuilder builder) {
        if (node.isGenerateTraceOnException()) {
            builder.end();  // tryBlock
            builder.startCatchBlock(context.getType(Throwable.class), "traceThrowable");
            builder.startIf().startCall("isTracingEnabled").end(2);
            builder.startBlock().startStatement().startCall("traceOnException").startGroup().string("traceThrowable").end(4);
            builder.startThrow().string("traceThrowable").end();
            builder.end();  // catchBlock
        }
    }

    private void wrapWithTraceOnReturn(CodeExecutableElement method) {
        if (node.isGenerateTraceOnReturn()) {
            CodeTypeElement enclosingClass = (CodeTypeElement) method.getEnclosingElement();
            CodeExecutableElement traceMethod = CodeExecutableElement.cloneNoAnnotations(method);

            method.setSimpleName(CodeNames.of(method.getSimpleName().toString() + "Traced"));
            method.setVisibility(PRIVATE);

            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            builder.startCall(method.getSimpleName().toString());
            for (VariableElement param : traceMethod.getParameters()) {
                builder.string(param.getSimpleName().toString());
            }
            builder.end();
            CodeTree initExpression = builder.build();

            builder = traceMethod.createBuilder();
            if (isVoid(method.getReturnType())) {
                builder.startStatement().tree(initExpression).end();
                builder.startIf().startCall("isTracingEnabled").end(2);
                builder.startBlock().startStatement().startCall("traceOnReturn").string("null").end(3);
            } else {
                builder.declaration(method.getReturnType(), "traceValue", initExpression);
                builder.startIf().startCall("isTracingEnabled").end(2);
                builder.startBlock().startStatement().startCall("traceOnReturn").string("traceValue").end(3);
                builder.startReturn().string("traceValue").end();
            }
            enclosingClass.add(traceMethod);
        }
    }

    public static class ChildExecutionResult {

        CodeTree code;
        final boolean throwsUnexpectedResult;

        public ChildExecutionResult(CodeTree code, boolean throwsUnexpectedResult) {
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

    static final class MultiStateBitSet extends MultiBitSet {

        /*
         * All bitsets in used by other nodes in the same generated class. E.g. nodes in exports are
         * all generated into the same class.
         */
        private final List<BitSet> all;

        MultiStateBitSet(List<BitSet> all, List<BitSet> active) {
            super(active);
            this.all = all;
        }

        public void clearLoaded(FrameState frameState) {
            for (BitSet state : all) {
                state.clearLoaded(frameState);
            }
        }

        <T> BitSet findSet(StateQuery query) {
            for (BitSet state : all) {
                if (state.contains(query)) {
                    return state;
                }
            }
            return null;
        }

        <T> BitSet findSet(Class<? extends State<T>> clazz, T param) {
            return findSet(StateQuery.create(clazz, param));
        }

        int getAllCapacity() {
            int length = 0;
            for (BitSet a : all) {
                length += a.getBitCount();
            }
            return length;
        }

        List<CodeVariableElement> createCachedFields() {
            List<CodeVariableElement> variables = new ArrayList<>();
            for (BitSet bitSet : all) {
                variables.add(createCachedField(bitSet));
            }
            return variables;
        }

        static CodeVariableElement createCachedField(BitSet bitSet) {
            CodeVariableElement var = FlatNodeGenFactory.createNodeField(PRIVATE, bitSet.getType(), bitSet.getName() + "_",
                            ProcessorContext.getInstance().getTypes().CompilerDirectives_CompilationFinal);
            CodeTreeBuilder docBuilder = var.createDocBuilder();

            for (BitRangedState state : bitSet.getStates().getEntries()) {
                if (state.state.isInlined()) {
                    GeneratorUtils.markUnsafeAccessed(var);
                }
            }

            docBuilder.startJavadoc();
            FlatNodeGenFactory.addStateDoc(docBuilder, bitSet);
            docBuilder.end();
            return var;
        }

        void addParametersTo(FrameState frameState, CodeExecutableElement targetMethod) {
            for (BitSet set : getSets()) {
                LocalVariable local = frameState.get(set.getName());
                if (local != null) {
                    targetMethod.addParameter(local.createParameter());
                }
            }
        }

        void removeParametersFrom(CodeExecutableElement targetMethod) {
            for (VariableElement var : targetMethod.getParameters().toArray(new VariableElement[0])) {
                for (BitSet set : getSets()) {
                    if (var.getSimpleName().toString().equals(set.getName())) {
                        targetMethod.getParameters().remove(var);
                    }
                }
            }
        }

        void addReferencesTo(FrameState frameState, CodeTreeBuilder builder) {
            for (BitSet set : getSets()) {
                LocalVariable local = frameState.get(set.getName());
                if (local != null) {
                    builder.tree(local.createReference());
                }
            }
        }

        void addReferencesTo(FrameState frameState, CodeTreeBuilder builder, StateQuery... relevantQuery) {
            for (BitSet set : getSets()) {
                LocalVariable local = frameState.get(set.getName());
                if (local != null) {
                    if (set.contains(relevantQuery)) {
                        builder.tree(local.createReference());
                    }
                }
            }
        }

        CodeTree createLoad(FrameState frameState, StateQuery... relevantQuery) {
            return createLoadImpl(getSets(), frameState, false, relevantQuery);
        }

        CodeTree createForceLoad(FrameState frameState, StateQuery... relevantQuery) {
            return createLoadImpl(getSets(), frameState, true, relevantQuery);
        }

        private static CodeTree createLoadImpl(List<? extends BitSet> sets, FrameState frameState, boolean force, StateQuery... relevantQuery) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            for (BitSet bitSet : sets) {
                if (bitSet.contains(relevantQuery)) {
                    builder.tree(bitSet.createLoad(frameState, force));
                }
            }
            return builder.build();
        }

        CodeTree createLoadAll(FrameState frameState, StateQuery relevantQuery) {
            return createLoadImpl(all, frameState, false, relevantQuery);
        }

        CodeTree createLoadFastPath(FrameState frameState, List<SpecializationData> specializations) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            for (BitSet bitSet : getSets()) {
                if (isRelevantForFastPath(frameState, bitSet, specializations)) {
                    builder.tree(bitSet.createLoad(frameState));
                }
            }
            return builder.build();
        }

        CodeTree createLoadSlowPath(FrameState frameState, List<SpecializationData> specializations, boolean force) {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

            for (BitSet bitSet : getSets()) {
                if (isRelevantForSlowPath(bitSet, specializations)) {
                    builder.tree(bitSet.createLoad(frameState, force));
                }
            }
            return builder.build();
        }

        static boolean isRelevantForFastPath(FrameState frameState, BitSet bitSet, Collection<SpecializationData> usedSpecializations) {
            if (!frameState.isSkipStateChecks() && bitSet.getStates().contains(StateQuery.create(SpecializationActive.class, usedSpecializations))) {
                return true;
            }
            if (bitSet.getStates().contains(AOT_PREPARED)) {
                return true;
            }
            for (SpecializationData specialization : usedSpecializations) {
                if (bitSet.getStates().contains(StateQuery.create(EncodedEnumState.class, specialization.getCaches()))) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("unused")
        static boolean isRelevantForSlowPath(BitSet bitSet, Collection<SpecializationData> usedSpecializations) {
            if (bitSet.getStates().contains(StateQuery.create(SpecializationActive.class, usedSpecializations))) {
                return true;
            }
            if (bitSet.getStates().contains(StateQuery.create(SpecializationExcluded.class, usedSpecializations))) {
                return true;
            }
            for (GuardActive state : bitSet.getStates().queryStates(GuardActive.class)) {
                if (usedSpecializations.contains(state.getDependentSpecialization())) {
                    return true;
                }
            }
            for (SpecializationData specialization : usedSpecializations) {
                if (bitSet.contains(StateQuery.create(EncodedEnumState.class, specialization.getCaches()))) {
                    return true;
                }
            }
            for (ImplicitCastState state : bitSet.getStates().queryStates(ImplicitCastState.class)) {
                if (isImplicitCastUsed(state.node.getPolymorphicExecutable(), usedSpecializations, state.key)) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("unused")
        static boolean isRelevantForQuickening(BitSet bitSet, Collection<SpecializationData> usedSpecializations) {
            if (bitSet.getStates().contains(StateQuery.create(SpecializationActive.class, usedSpecializations))) {
                return true;
            }
            for (ImplicitCastState state : bitSet.getStates().queryStates(ImplicitCastState.class)) {
                TypeGuard guard = state.key;
                int signatureIndex = guard.getSignatureIndex();
                for (SpecializationData specialization : usedSpecializations) {
                    TypeMirror specializationType = specialization.getSignatureParameters().get(signatureIndex).getType();
                    if (!state.node.getTypeSystem().lookupByTargetType(specializationType).isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }

    }

    public static final class FrameState {

        private final FlatNodeGenFactory factory;
        private final Map<String, LocalVariable> values = new HashMap<>();
        private final Map<String, Boolean> directValues = new HashMap<>();

        private final NodeExecutionMode mode;
        private final CodeExecutableElement method;

        private final List<TypeMirror> caughtTypes = new ArrayList<>();

        private FrameState(FlatNodeGenFactory factory, NodeExecutionMode mode, CodeExecutableElement method) {
            this.factory = factory;
            this.mode = mode;
            this.method = method;
        }

        public void setSkipStateChecks(boolean skipStateChecks) {
            setBoolean("$stateChecks", skipStateChecks);
        }

        public boolean isSkipStateChecks() {
            return getBoolean("$stateChecks", false);
        }

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

        public boolean isInlinedNode() {
            return getBoolean("$inlinedNode", false);
        }

        public void setInlinedNode(boolean b) {
            setBoolean("$inlinedNode", b);
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

        public boolean isSpecializationClassInitialized(SpecializationData specialization) {
            return getBoolean(createSpecializationClassInitialized(specialization), false);
        }

        public void setSpecializationClassInitialized(SpecializationData specialization, boolean b) {
            setBoolean(createSpecializationClassInitialized(specialization), b);
        }

        private static String createSpecializationClassInitialized(SpecializationData specialization) {
            return createSpecializationLocalName(specialization) + "$initialized";
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
            return copy(this.mode);
        }

        public FrameState copy(NodeExecutionMode newMode) {
            FrameState copy = new FrameState(factory, newMode, method);
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

        public static String valueName(NodeExecutionData execution) {
            return execution.getName() + "Value";
        }

        public void set(String id, LocalVariable var) {
            values.put(id, var);
        }

        public void clear(String id) {
            values.remove(id);
        }

        public void set(NodeExecutionData execution, LocalVariable var) {
            set(valueName(execution), var);
        }

        public LocalVariable getCacheInitialized(SpecializationData specialization, CacheExpression cache) {
            String name = factory.createFieldName(specialization, cache);
            return get(name);
        }

        public LocalVariable getCacheClassInitialized(CacheExpression cache) {
            if (cache.getSharedGroup() == null) {
                // only used for shared caches
                return null;
            }
            return get(createCacheClassInitializedKey(cache));
        }

        private String createCacheClassInitializedKey(CacheExpression cache) {
            String name = factory.createFieldName(null, cache);
            String key = name + "$wrapper";
            return key;
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

            for (VariableElement arg : factory.plugs.additionalArguments()) {
                builder.variable(arg);
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
            for (VariableElement arg : factory.plugs.additionalArguments()) {
                targetMethod.addParameter(arg);
            }
        }

        @Override
        public String toString() {
            return "LocalContext [values=" + values + "]";
        }

    }

    public static final class LocalVariable {

        private final TypeMirror typeMirror;
        private final CodeTree accessorTree;
        private final String name;

        LocalVariable(TypeMirror typeMirror, String name, CodeTree accessorTree) {
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

    public enum NodeExecutionMode {

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

    public CodeTree createOnlyActive(FrameState frameState, List<SpecializationData> filteredSpecializations, Collection<SpecializationData> allSpecializations) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        CodeTree tree = multiState.createContainsOnly(frameState, 0, -1, StateQuery.create(SpecializationActive.class, filteredSpecializations),
                        StateQuery.create(SpecializationActive.class, allSpecializations));
        b.tree(tree);
        if (!tree.isEmpty()) {
            b.string(" && ");
        }
        b.tree(multiState.createContains(frameState,
                        StateQuery.create(SpecializationActive.class, allSpecializations)));
        return b.build();
    }

    public CodeTree createIsImplicitTypeStateCheck(FrameState frameState, TypeMirror targetType, TypeMirror specializationType, int signatureIndex) {
        List<ImplicitCastData> casts = typeSystem.lookupByTargetType(specializationType);
        if (casts.isEmpty()) {
            return null;
        }

        long mask = 1;
        if (!ElementUtils.typeEquals(targetType, specializationType)) {
            for (ImplicitCastData cast : casts) {
                mask = mask << 1;
                if (ElementUtils.typeEquals(cast.getSourceType(), targetType)) {
                    break;
                }
            }
        }
        CodeTreeBuilder b = new CodeTreeBuilder(null);
        b.tree(multiState.createExtractInteger(frameState, StateQuery.create(ImplicitCastState.class, new TypeGuard(typeSystem, specializationType, signatureIndex))));
        b.string(" == ");
        b.string(BitSet.formatMask(mask, casts.size() + 1));
        return b.build();
    }

    public void addQuickeningStateParametersTo(CodeExecutableElement method, FrameState frameState, Collection<SpecializationData> specializations) {
        for (BitSet set : multiState.getSets()) {
            if (!MultiStateBitSet.isRelevantForQuickening(set, specializations)) {
                continue;
            }

            LocalVariable local = frameState.get(set.getName());
            if (local != null) {
                method.addParameter(local.createParameter());
            }
        }
    }

    public void loadQuickeningStateBitSets(CodeTreeBuilder b, FrameState frameState, Collection<SpecializationData> specializations) {
        for (BitSet set : multiState.getSets()) {
            if (!MultiStateBitSet.isRelevantForQuickening(set, specializations)) {
                continue;
            }
            b.tree(set.createLoad(frameState));
        }
    }

}
