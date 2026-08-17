/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.sboutlining;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.sboutlining.concat.SubstrateSBConcatHelper;
import com.oracle.svm.hosted.sboutlining.SBOutliningAnalysis.SBAllocation;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.MaterializedObjectState;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Rewrites supported {@link StringBuilder} and {@link StringBuffer} chains as calls to shared
 * outlined methods.
 *
 * <p>
 * {@link SBOutliningAnalysis} first identifies virtual append states and decides where each value
 * must become a builder, buffer, or string. This phase applies that plan. It inserts constructor
 * validation and operand stringification, emits calls to {@link OutlinedSBMethod} implementations,
 * reconstructs required phi, proxy, and exception control flow, redirects uses, and removes the
 * original allocation and append nodes.
 *
 * <p>
 * Keeping analysis and rewriting separate makes the placement decisions independent of temporary
 * graph mutations. {@link SBMaterializationOutliner} contains the transformation for one accepted
 * allocation.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class SBOutliningPhase extends BasePhase<CoreProviders> {

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        AnalysisMetaAccess metaAccess = (AnalysisMetaAccess) context.getMetaAccess();
        SBOutliningAnalysis analysis = new SBOutliningAnalysis(graph, metaAccess);
        List<SBAllocation> sbAllocations = analysis.performAnalysis();
        SBMetadataHelper metadataHelper = new SBMetadataHelper(metaAccess, graph);

        /*
         * Map to track invokes which have been replaced with stringifies. This is used to ensure
         * that stringifies's operands will be materialized sbs when necessary.
         */
        EconomicMap<InvokeWithExceptionNode, InvokeWithExceptionNode> replacedStringifies = EconomicMap.create();

        // outline sb sequences
        sbAllocations.stream().map(analysis::calculateMaterializations).forEach(materializationInfo -> SBMaterializationOutliner.outlineMaterializations(graph, metaAccess, metadataHelper,
                        materializationInfo, replacedStringifies));
        // finally, delete original sb operations
        deleteVirtualizedNodes(graph, SBOutliningAnalysis.virtualizedNodesFor(sbAllocations));

        if (Assertions.detailedAssertionsEnabled(graph.getOptions())) {
            assert GraphOrder.assertSchedulableGraph(graph);
            assert graph.verify();
        }
    }

    static class SBMetadataHelper {

        final EconomicMap<ResolvedJavaType, JavaKind> boxedTypes;
        final ResolvedJavaType stringType;
        final ResolvedJavaType stringBuilderType;
        final ResolvedJavaType stringBufferType;
        final ResolvedJavaMethod stringValueOfMethod;
        final StampPair positiveIntStamp;
        final StampPair nonNullStringStamp;
        final StampPair nonNullCharSequenceStamp;
        final Stamp stringBufferStamp;
        final Stamp stringBuilderStamp;

        final ResolvedJavaMethod initialCapacityForMethod;
        final ResolvedJavaMethod validateCapacityMethod;
        final ResolvedJavaMethod ensureNonNullMethod;

        SBMetadataHelper(MetaAccessProvider metaAccessProvider, StructuredGraph graph) {

            MetadataLookup metadataLookup = ImageSingletons.lookup(MetadataLookup.class);
            metadataLookup.initialize(metaAccessProvider);

            boxedTypes = metadataLookup.getBoxedTypes();
            stringType = metadataLookup.getStringType();
            stringBuilderType = metadataLookup.getStringBuilderType();
            stringBufferType = metadataLookup.getStringBufferType();
            stringValueOfMethod = metadataLookup.getStringValueOfMethod();

            positiveIntStamp = StampPair.createSingle(StampFactory.forInteger(JavaKind.Int, 0L, Integer.MAX_VALUE));
            nonNullStringStamp = StampFactory.forDeclaredType(graph.getAssumptions(), stringType, true);
            nonNullCharSequenceStamp = StampFactory.forDeclaredType(graph.getAssumptions(), metadataLookup.getCharSequenceType(), true);
            stringBufferStamp = StampFactory.forDeclaredType(graph.getAssumptions(), stringBufferType, true).getTrustedStamp();
            stringBuilderStamp = StampFactory.forDeclaredType(graph.getAssumptions(), stringBuilderType, true).getTrustedStamp();

            initialCapacityForMethod = metadataLookup.getInitialCapacityForMethod();
            validateCapacityMethod = metadataLookup.getValidateCapacityMethod();
            ensureNonNullMethod = metadataLookup.getEnsureNonNullMethod();
        }

        public JavaType getSBType(Class<?> clazz) {
            if (clazz.equals(String.class)) {
                return stringType;
            } else if (clazz.equals(StringBuilder.class)) {
                return stringBuilderType;
            } else {
                assert clazz.equals(StringBuffer.class);
                return stringBufferType;
            }
        }

        public Class<?> getSBClass(ResolvedJavaType type) {
            if (type.equals(stringBuilderType)) {
                return StringBuilder.class;
            } else if (type.equals(stringBufferType)) {
                return StringBuffer.class;
            }
            throw VMError.shouldNotReachHereUnexpectedInput(type); // ExcludeFromJacocoGeneratedReport
        }

        public Stamp getSBStamp(ResolvedJavaType type) {
            if (type.equals(stringBuilderType)) {
                return stringBuilderStamp;
            } else if (type.equals(stringBufferType)) {
                return stringBufferStamp;
            }
            throw VMError.shouldNotReachHereUnexpectedInput(type); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * This class outlines the materialized StringBuilder|StringBuffer (SB) and SB.toString
     * operations. Doing so consists of the following tasks:
     *
     * <ul>
     * <li>Inserting checks performed by SB constructors.</li>
     * <li>Inserting new stringify calls (i.e., calls to an object's toString method). These serve
     * as a replacement for the original append calls.</li>
     * <li>Inserting new SB materialization calls</li>
     * <li>Inserting Phis for SBMaterializations</li>
     * <li>Updating uses of materializedSB</li>
     * <li>Removing original SB actions (allocation, init, appends)</li>
     * <li>Removing other dead/unneeded uses of the original SB objects</li>
     * </ul>
     *
     * <p>
     * All inserted stringify and SB materialization calls must also have exception edges. We
     * accomplish this by creating a new exception which is merged into the exceptional control flow
     * of the original invoke.
     */
    static class SBMaterializationOutliner {
        final StructuredGraph graph;
        final AnalysisMetaAccess metaAccess;
        final SBOutliningAnalysis.SBAllocationMaterializationInfo materializationInfo;
        final SBMetadataHelper metadataHelper;
        final EconomicMap<InvokeWithExceptionNode, ValueNode> stringifyMap;
        final EconomicMap<SBOutliningAnalysis.MaterializedSB, ValueNode> materializationMap;
        final EconomicMap<InvokeWithExceptionNode, InvokeWithExceptionNode> replacedStringifies;
        final OutlinedSBMethodSupport outlinedSBMethodSupport;

        SBMaterializationOutliner(StructuredGraph graph, AnalysisMetaAccess metaAccess, SBMetadataHelper metadataHelper, SBOutliningAnalysis.SBAllocationMaterializationInfo materializationInfo,
                        EconomicMap<InvokeWithExceptionNode, InvokeWithExceptionNode> replacedStringifies) {
            this.graph = graph;
            this.metaAccess = metaAccess;
            this.metadataHelper = metadataHelper;
            this.materializationInfo = materializationInfo;
            this.stringifyMap = EconomicMap.create(Equivalence.DEFAULT, materializationInfo.getStringifies().size());
            this.materializationMap = EconomicMap.create(Equivalence.DEFAULT, materializationInfo.getUsedMaterializations().size());
            this.replacedStringifies = replacedStringifies;
            outlinedSBMethodSupport = OutlinedSBMethodSupport.singleton();
        }

        static void outlineMaterializations(StructuredGraph graph, AnalysisMetaAccess metaAccess, SBMetadataHelper metadataHelper,
                        SBOutliningAnalysis.SBAllocationMaterializationInfo materializationInfo, EconomicMap<InvokeWithExceptionNode, InvokeWithExceptionNode> replacedStringifies) {
            SBMaterializationOutliner outliner = new SBMaterializationOutliner(graph, metaAccess, metadataHelper, materializationInfo, replacedStringifies);
            outliner.insertConstructorsChecks();
            outliner.insertStringifies();
            outliner.insertConcreteMaterializations();
            outliner.insertMaterializationControlFlowAndAdjustUses();
            outliner.removeDeadUses();
        }

        private void insertConstructorsChecks() {
            InvokeWithExceptionNode constructorToVerify = materializationInfo.getConstructorToVerify();
            if (constructorToVerify != null) {
                insertFallibleConstructorChecks(constructorToVerify);
            }
        }

        private void insertFallibleConstructorChecks(InvokeWithExceptionNode originalCall) {
            final ValueNode arg = originalCall.callTarget().arguments().get(1);
            final Stamp stamp = arg.stamp(NodeView.DEFAULT);

            final SubstrateMethodCallTargetNode validateMethod;
            if (stamp.isIntegerStamp()) {
                // StringBuilder(int)
                // StringBuffer(int)
                if (((IntegerStamp) stamp).isPositive()) {
                    return;
                }

                validateMethod = graph.add(new SubstrateMethodCallTargetNode(
                                CallTargetNode.InvokeKind.Static, metadataHelper.validateCapacityMethod,
                                new ValueNode[]{arg}, metadataHelper.positiveIntStamp));
            } else if (stamp.isPointerStamp()) {
                final AbstractPointerStamp pointerStamp = (AbstractPointerStamp) stamp;
                if (pointerStamp.nonNull()) {
                    return;
                }

                final ResolvedJavaType javaType = pointerStamp.javaType(metaAccess);
                final StampPair returnType;
                if (javaType.equals(metadataHelper.nonNullStringStamp.getTrustedStamp().javaType(metaAccess))) {
                    // StringBuilder(String)
                    // StringBuffer(String)
                    returnType = metadataHelper.nonNullStringStamp;
                } else if (javaType.equals(metadataHelper.nonNullCharSequenceStamp.getTrustedStamp().javaType(metaAccess))) {
                    // StringBuilder(CharSequence)
                    // StringBuffer(CharSequence)
                    returnType = metadataHelper.nonNullCharSequenceStamp;
                } else {
                    throw VMError.shouldNotReachHere("javaType = " + javaType + ", metaAccess = " + metaAccess);
                }

                validateMethod = graph.add(new SubstrateMethodCallTargetNode(
                                CallTargetNode.InvokeKind.Static, metadataHelper.ensureNonNullMethod,
                                new ValueNode[]{arg}, returnType));
            } else {
                throw VMError.shouldNotReachHere("Invalid parameter type for StringBuilder/StringBuffer constructor");
            }

            validateMethod.setNodeSourcePosition(originalCall.getNodeSourcePosition());
            insertInvoke(validateMethod, originalCall);
        }

        /**
         * Because internally SB append calls the object's toString method (i.e., Stringifies the
         * object), which is an @Overridable method, in the pathological case is possible for an
         * object's toString method to have arbitrary side effects which we cannot virtualize. Thus,
         * for Objects which must keep the "stringify" at the original location, but then can feed
         * the resulting string to the SB materialization at a later point. If the value to append
         * is a primitive or its stringify operation is known to be side effect free (such as for a
         * String or Boxed primitive object), then the append can be entirely virtualized and the
         * stringification can also happen at the materialization point.
         */
        private void insertStringify(InvokeWithExceptionNode originalCall) {
            if (stringifyMap.containsKey(originalCall)) {
                return;
            }
            ValueNode appendOperand = getStringifyOperand(originalCall);
            Stamp stamp = appendOperand.stamp(NodeView.DEFAULT);
            ResolvedJavaType type = stamp.javaType(metaAccess);
            ValueNode stringifyOp;
            if (type.isPrimitive()) {
                /*
                 * For primitive types we do not need to stringify anything. We can directly pass
                 * the primitive value to the materialization since we know its stringification will
                 * not have any side effects.
                 */
                stringifyOp = appendOperand;
                outlinedSBMethodSupport.primitiveStringifies.inc();
            } else if (type.equals(metadataHelper.stringType)) {
                /* Strings do not need to be stringified. */
                stringifyOp = appendOperand;
                outlinedSBMethodSupport.stringStringifies.inc();
            } else if (metadataHelper.boxedTypes.containsKey(type)) {
                if (stamp instanceof AbstractPointerStamp && ((AbstractPointerStamp) stamp).nonNull()) {
                    /*
                     * For Java types which can be unboxed and are known to be non-null, we pass the
                     * unboxed primitive to the materialization. We do so with the hope that a
                     * corresponding Box operation can be found and the entire boxing process can be
                     * eliminated.
                     */
                    UnboxNode newUnbox = graph.add(new UnboxNode(appendOperand, metadataHelper.boxedTypes.get(type), metaAccess));
                    FixedWithNextNode predecessor = (FixedWithNextNode) originalCall.predecessor();
                    predecessor.setNext(newUnbox);
                    newUnbox.setNext(originalCall);

                    stringifyOp = newUnbox;
                    outlinedSBMethodSupport.unboxedStringifies.inc();
                } else {
                    /*
                     * Although we cannot unbox the value since it may be null, it also does not
                     * need to be stringified since we know the behavior of "toString" on boxed
                     * primitives.
                     */
                    stringifyOp = appendOperand;
                    outlinedSBMethodSupport.boxedStringifies.inc();
                }
            } else {
                /*
                 * Otherwise, we call the String.valueOf(Object obj) to get the string
                 * representation.
                 */

                ResolvedJavaMethod target = metadataHelper.stringValueOfMethod;
                StampPair returnStamp = metadataHelper.nonNullStringStamp;
                ValueNode[] arguments = {appendOperand};
                SubstrateMethodCallTargetNode stringifyCall = graph.add(new SubstrateMethodCallTargetNode(CallTargetNode.InvokeKind.Static, target, arguments, returnStamp));
                stringifyCall.setNodeSourcePosition(originalCall.getNodeSourcePosition());

                InvokeWithExceptionNode invoke = insertInvoke(stringifyCall, originalCall);
                var previousVal = replacedStringifies.put(originalCall, invoke);
                assert previousVal == null : "stringify replaced multiple times " + originalCall;
                stringifyOp = invoke;

                outlinedSBMethodSupport.explicitStringifies.inc();
            }
            outlinedSBMethodSupport.totalStringifies.inc();
            stringifyMap.put(originalCall, stringifyOp);
        }

        /**
         * Note both appends and inits which are stringified will have the stringify operand in
         * index 1.
         */
        private static ValueNode getStringifyOperand(InvokeWithExceptionNode invoke) {
            return invoke.callTarget().arguments().get(1);
        }

        private void insertStringifies() {
            for (InvokeWithExceptionNode stringify : materializationInfo.getStringifies()) {
                insertStringify(stringify);
            }
        }

        private static MethodType computeMethodType(SBOutliningAnalysis.MaterializedConcreteSB materializedSB, boolean isSBMaterialization, Class<?> sbClass,
                        EconomicMap<InvokeWithExceptionNode, ValueNode> stringifyMap) {
            Class<?> returnType;
            if (!isSBMaterialization) {
                returnType = String.class;
            } else {
                returnType = sbClass;
            }

            ArrayList<Class<?>> paramTypes = new ArrayList<>(materializedSB.virtualSB.stringifies.size() + (isSBMaterialization ? 1 : 0));

            if (isSBMaterialization) {
                // need to add argument for initial capacity
                paramTypes.add(int.class);
            }

            materializedSB.virtualSB.stringifies.forEach(invoke -> {
                JavaKind kind = invoke.getTargetMethod().getSignature().getParameterKind(0);
                Class<?> type;
                if (kind.isObject()) {
                    ValueNode stringifyOp = stringifyMap.get(invoke);
                    if (stringifyOp instanceof UnboxNode) {
                        type = ((UnboxNode) stringifyOp).getBoxingKind().toJavaClass();
                    } else {
                        type = Object.class;
                    }
                } else {
                    type = kind.toJavaClass();
                }
                paramTypes.add(type);
            });

            return MethodType.methodType(returnType, paramTypes);
        }

        private ValueNode insertInitialCapacity(InvokeWithExceptionNode init) {
            Signature signature = init.getTargetMethod().getSignature();
            if (signature.getParameterCount(false) == 1) {
                if (signature.getParameterKind(0) == JavaKind.Int) {
                    return init.callTarget().arguments().get(1);
                } else {
                    assert stringifyMap.containsKey(init);

                    // am passing a string to compute the initial capacity
                    ResolvedJavaMethod target = metadataHelper.initialCapacityForMethod;
                    StampPair returnStamp = StampPair.createSingle(StampFactory.intValue());
                    ValueNode[] arguments = {stringifyMap.get(init)};
                    SubstrateMethodCallTargetNode initialCapacityCall = graph
                                    .add(new SubstrateMethodCallTargetNode(CallTargetNode.InvokeKind.Static, target, arguments, returnStamp));
                    // since one call is becoming two, there isn't a proper spot for this
                    initialCapacityCall.setNodeSourcePosition(NodeSourcePosition.placeholder(graph.method()));
                    outlinedSBMethodSupport.explicitCapacityInits.inc();

                    return insertInvoke(initialCapacityCall, init);
                }
            }
            return ConstantNode.forInt(SubstrateSBConcatHelper.CapacityCalculator.defaultInitialCapacity(), graph);
        }

        private InvokeWithExceptionNode insertInvoke(SubstrateMethodCallTargetNode call, InvokeWithExceptionNode insertionPoint) {
            outlinedSBMethodSupport.totalCalls.inc();
            ExceptionObjectNode exceptionNode = createExceptionAndMerge(call, insertionPoint);
            InvokeWithExceptionNode newInvoke = graph.add(new InvokeWithExceptionNode(call, exceptionNode, insertionPoint.bci()));
            newInvoke.setStateAfter(createNewStateAfter(List.of(), newInvoke, insertionPoint.stateAfter()));

            FixedWithNextNode invokePredecessor = (FixedWithNextNode) insertionPoint.predecessor();
            invokePredecessor.setNext(newInvoke);
            newInvoke.setNext(BeginNode.begin(insertionPoint));

            return newInvoke;
        }

        private void insertConcreteMaterializations() {
            for (SBOutliningAnalysis.MaterializedConcreteSB materializedSB : materializationInfo.getConcreteSBs()) {
                // create call to outlined SB materialization

                boolean isSBMaterialization = materializedSB instanceof SBOutliningAnalysis.MaterializedInstanceSB;
                MethodType methodType = computeMethodType(materializedSB, isSBMaterialization, metadataHelper.getSBClass(materializationInfo.getType()), stringifyMap);

                if (SBOutliningFeature.Options.PrintOutlinedSBMethodMetrics.getValue()) {
                    OutlinedSBMethodSupport.UseKind kind = OutlinedSBMethodSupport.UseKind.String;
                    if (isSBMaterialization) {
                        kind = methodType.returnType().equals(StringBuffer.class) ? OutlinedSBMethodSupport.UseKind.StringBuffer : OutlinedSBMethodSupport.UseKind.StringBuilder;
                    }
                    OutlinedSBMethodSupport.registerOutliningUse(graph.method(), kind);
                }

                List<ValueNode> arguments = new ArrayList<>();

                if (isSBMaterialization) {
                    ValueNode capacity = insertInitialCapacity(materializedSB.virtualSB.init);
                    arguments.add(capacity);
                }

                materializedSB.virtualSB.stringifies.forEach(s -> arguments.add(stringifyMap.get(s)));
                ResolvedJavaMethod target = outlinedSBMethodSupport.lookup(metaAccess, methodType);

                JavaType returnType = metadataHelper.getSBType(methodType.returnType());
                StampPair returnStamp = StampFactory.forDeclaredType(graph.getAssumptions(), returnType, true);

                SubstrateMethodCallTargetNode materializeSBCall = graph
                                .add(new SubstrateMethodCallTargetNode(CallTargetNode.InvokeKind.Static, target, arguments.toArray(ValueNode.EMPTY_ARRAY), returnStamp));

                // insert new invoke for materialization and unwind
                NodeSourcePosition nsp;
                if (materializedSB instanceof SBOutliningAnalysis.MaterializedInstanceSB) {
                    nsp = NodeSourcePosition.placeholder(graph.method());
                } else {
                    nsp = ((SBOutliningAnalysis.MaterializedToStringSB) materializedSB).toString.callTarget().getNodeSourcePosition();
                }
                materializeSBCall.setNodeSourcePosition(nsp);

                materializationMap.put(materializedSB, insertInvoke(materializeSBCall, materializedSB.getMaterializationInsertionPoint()));
            }
        }

        private ValueNode materializationFor(SBOutliningAnalysis.MaterializedSB key) {
            SBOutliningAnalysis.MaterializedSB remappedKey;
            if (key instanceof SBOutliningAnalysis.MaterializedConcreteSB) {
                remappedKey = materializationInfo.getRemappedConcreteSB((SBOutliningAnalysis.MaterializedConcreteSB) key);
            } else {
                remappedKey = key;
            }
            return materializationMap.get(remappedKey);
        }

        private void insertMaterializationControlFlowAndAdjustUses() {
            for (SBOutliningAnalysis.MaterializedSB materializedSB : materializationInfo.getUsedMaterializations()) {
                ValueNode replacement = materializationFor(materializedSB);
                if (replacement == null) {
                    // need to create a ValueProxy or ValuePhi
                    SBOutliningAnalysis.MaterializedControlFlowSB controlFlow = (SBOutliningAnalysis.MaterializedControlFlowSB) materializedSB;

                    Stamp proxyStamp = metadataHelper.getSBStamp(materializationInfo.getType());
                    replacement = controlFlowNodeFor(controlFlow, proxyStamp);
                } else if (materializedSB instanceof SBOutliningAnalysis.MaterializedToStringSB) {
                    // simply need to replace toString at all usages
                    InvokeWithExceptionNode originalToString = ((SBOutliningAnalysis.MaterializedToStringSB) materializedSB).toString;
                    originalToString.replaceAtAllUsages(replacement, false);
                    /*
                     * There is only one use of this materialization (the toString itself) so no
                     * other work needs to be done
                     */
                    assert materializationInfo.materializedUsesFor(materializedSB) == null;
                    continue;
                }

                for (Node use : materializationInfo.materializedUsesFor(materializedSB)) {
                    // for recorded Invokes need to adjust inputs within the CallTargetNode
                    Node nodeWithInputs = use instanceof Invoke ? ((Invoke) use).callTarget() : use;
                    if (use instanceof InvokeWithExceptionNode) {
                        /*
                         * If the use is a virtualized stringify, then need to update the new
                         * stringify.
                         */
                        InvokeWithExceptionNode stringifyOp = replacedStringifies.get((InvokeWithExceptionNode) use);
                        if (stringifyOp != null) {
                            nodeWithInputs = stringifyOp.callTarget();
                        }
                    }

                    // searching for any use of an alias to replace
                    for (ValueNode alias : materializationInfo.getAliases()) {
                        nodeWithInputs.replaceAllInputs(alias, replacement);
                    }
                }
            }
        }

        private void removeDeadUses() {
            /*
             * At this point all meaningful uses have already been adjusted; however, usages of the
             * aliases still may exist either in FrameStates or in nodes which will soon be deleted.
             * Instead of trying to manually remove all these uses, we create a "placeholder" object
             * and replace all uses with this placeholder
             */
            ValueNode placeholderNode = graph.addOrUniqueWithInputs(ConstantNode.forConstant(JavaConstant.NULL_POINTER, null));

            SBAllocation sb = materializationInfo.getSBAllocation();
            /*
             * Replace references to value within materialized state with placeholder.
             */
            if (sb.allocNode instanceof AllocatedObjectNode allocatedObject) {
                for (MaterializedObjectState materializedObjectState : allocatedObject.usages().filter(MaterializedObjectState.class).snapshot()) {
                    VMError.guarantee(materializedObjectState.materializedValue() == allocatedObject);
                    for (FrameState frameState : materializedObjectState.usages().filter(FrameState.class).snapshot()) {
                        for (int i = 0; i < frameState.virtualObjectMappingCount(); i++) {
                            if (frameState.virtualObjectMappingAt(i) == materializedObjectState) {
                                frameState.virtualObjectMappings().remove(i);
                                frameState.values().replaceAll(value -> value == allocatedObject.getVirtualObject() ? placeholderNode : value);

                                assert !frameState.virtualObjectMappings()
                                                .contains(materializedObjectState) : "Each MaterializedObjectState must be registered only once in the object mappings";
                                break;
                            }
                        }
                    }
                    if (!materializedObjectState.usages().isEmpty()) {
                        throw VMError.shouldNotReachHere("Unexpected usages of MaterializedObjectState: " + materializedObjectState.usages().first());
                    }
                    materializedObjectState.safeDelete();
                }
            }

            // now have to go through all of the remaining uses and adjust them
            for (ValueNode def : materializationInfo.getAliases()) {
                for (Node use : def.usages().snapshot()) {
                    if (!GraphUtil.tryKillUnused(use)) {
                        if (use instanceof IsNullNode) {
                            // We always create an object
                            use.replaceAtUsages(LogicConstantNode.contradiction(graph));
                            use.safeDelete();
                        } else if (use instanceof PiNode) {
                            /*
                             * The alive uses to the PiNode should have been already replaced, so
                             * this node can be removed.
                             */
                            use.replaceAtUsages(placeholderNode);
                            use.safeDelete();
                        } else {
                            if (Assertions.assertionsEnabled() && !SBOutliningAnalysis.allowedEscapingUse(use)) {
                                boolean validUse = false;
                                if (use instanceof CallTargetNode target) {
                                    Invoke invoke = target.invoke();
                                    // this is only executed with asserts and the list is short
                                    @SuppressWarnings("all")
                                    boolean internalSBOp = materializationInfo.getSBAllocation().virtualizedNodes.contains(invoke);
                                    boolean handledExternalSBOp = (invoke instanceof InvokeWithExceptionNode) && (replacedStringifies.containsKey((InvokeWithExceptionNode) invoke));
                                    validUse = internalSBOp || handledExternalSBOp;
                                }
                                assert validUse : "unexpected use: " + use;
                            }
                            use.replaceAllInputs(def, placeholderNode);
                        }
                    }
                }
            }
        }

        /**
         * This method assumes that only 1 value is passed to represent the stack values.
         */
        private FrameState createNewStateAfter(List<ValueNode> locals, ValueNode stack, FrameState originalState) {
            ArrayList<ValueNode> values = new ArrayList<>();
            for (int i = 0; i < originalState.locksSize(); i++) {
                values.add(originalState.lockAt(i));
            }
            values.add(stack);
            values.addAll(locals);
            return graph.add(new FrameState(originalState.outerFrameState(), originalState.getCode(), originalState.bci, values, locals.size(), 1, originalState.locksSize(),
                            originalState.getStackState(), originalState.isValidForDeoptimization(),
                            originalState.monitorIds(), originalState.virtualObjectMappings(), null));
        }

        private ValueNode controlFlowNodeFor(SBOutliningAnalysis.MaterializedControlFlowSB controlFlow, Stamp stamp) {
            // return if already exists
            ValueNode materialization = materializationFor(controlFlow);
            if (materialization != null) {
                return materialization;
            }
            if (controlFlow instanceof SBOutliningAnalysis.MaterializedPhiSB phi) {
                // otherwise need to create node and populate edges
                ValuePhiNode phiNode = graph.addWithoutUnique(new ValuePhiNode(stamp, phi.merge));
                materializationMap.put(phi, phiNode);
                for (SBOutliningAnalysis.MaterializedSB edge : phi.materializedEdges) {
                    ValueNode phiInput = materializationFor(edge);
                    if (phiInput == null) {
                        phiInput = controlFlowNodeFor((SBOutliningAnalysis.MaterializedControlFlowSB) edge, stamp);
                    }
                    phiNode.addInput(phiInput);
                }

                return phiNode;
            } else {
                SBOutliningAnalysis.MaterializedValueProxySB valueProxy = (SBOutliningAnalysis.MaterializedValueProxySB) controlFlow;
                ValueNode proxyInput = materializationFor(valueProxy.materializedSB);
                if (proxyInput == null) {
                    proxyInput = controlFlowNodeFor((SBOutliningAnalysis.MaterializedControlFlowSB) valueProxy.materializedSB, stamp);
                }
                ValueProxyNode valueProxyNode = graph.addWithoutUnique(new ValueProxyNode(proxyInput, valueProxy.loopExit));
                materializationMap.put(valueProxy, valueProxyNode);

                return valueProxyNode;
            }
        }

        private ExceptionObjectNode createException(CallTargetNode target, ExceptionObjectNode originalException) {
            ExceptionObjectNode exceptionObject = graph.add(new ExceptionObjectNode(metaAccess));
            exceptionObject.setStateAfter(createNewStateAfter(target.arguments(), exceptionObject, originalException.stateAfter()));

            return exceptionObject;
        }

        private ExceptionObjectNode createExceptionAndMerge(CallTargetNode target, InvokeWithExceptionNode originalInvoke) {
            FixedNode curPos = originalInvoke.exceptionEdge();
            while (!(curPos instanceof ExceptionObjectNode originalException)) {
                curPos = ((FixedWithNextNode) curPos).next();
            }
            ExceptionObjectNode newException = createException(target, originalException);
            mergeExceptions(newException, originalException);
            return newException;
        }

        /**
         * Creates a merge immediately after the two exceptions so that the rest of the logic can be
         * shared.
         */
        private void mergeExceptions(ExceptionObjectNode newException, ExceptionObjectNode originalException) {

            EndNode branchEndOriginal = graph.add(new EndNode());
            EndNode branchEndNew = graph.add(new EndNode());
            MergeNode merge = graph.add(new MergeNode());
            merge.addForwardEnd(branchEndOriginal);
            merge.addForwardEnd(branchEndNew);

            Stamp phiStamp = originalException.stamp(NodeView.DEFAULT).meet(newException.stamp(NodeView.DEFAULT));
            ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(phiStamp, merge));
            FrameState originalExceptionStateAfter = originalException.stateAfter();
            originalException.replaceAtMatchingUsages(phi, node -> node != originalExceptionStateAfter);
            phi.addInput(originalException);
            phi.addInput(newException);

            merge.setStateAfter(createNewStateAfter(List.of(), phi, originalException.stateAfter()));

            FixedNode originalNext = originalException.next();
            originalException.setNext(branchEndOriginal);
            newException.setNext(branchEndNew);
            merge.setNext(originalNext);
        }
    }

    private static void deleteVirtualizedNodes(StructuredGraph graph, List<ValueNode> nodes) {
        for (ValueNode node : nodes) {
            if (node.isAlive()) {
                if (node instanceof ControlSplitNode split) {
                    graph.removeSplitPropagate(split, split.getPrimarySuccessor());
                } else if (node instanceof FixedWithNextNode) {
                    graph.removeFixed((FixedWithNextNode) node);
                } else {
                    node.safeDelete();
                }
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @AutomaticallyRegisteredImageSingleton
    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
    static class MetadataLookup {
        private ResolvedJavaType stringType;
        private ResolvedJavaType stringBuilderType;
        private ResolvedJavaType stringBufferType;
        private ResolvedJavaType charSequenceType;

        private ResolvedJavaMethod stringValueOfMethod;
        private ResolvedJavaMethod initialCapacityForMethod;
        private ResolvedJavaMethod validateCapacityMethod;
        private ResolvedJavaMethod ensureNonNullMethod;

        private EconomicMap<ResolvedJavaType, JavaKind> boxedTypes;

        private volatile boolean initialized = false;

        public void initialize(MetaAccessProvider metaAccessProvider) {
            if (!initialized) {
                synchronized (this) {
                    if (!initialized) {
                        stringType = metaAccessProvider.lookupJavaType(String.class);
                        stringBuilderType = metaAccessProvider.lookupJavaType(StringBuilder.class);
                        stringBufferType = metaAccessProvider.lookupJavaType(StringBuffer.class);
                        charSequenceType = metaAccessProvider.lookupJavaType(CharSequence.class);

                        stringValueOfMethod = metaAccessProvider.lookupJavaMethod(ReflectionUtil.lookupMethod(String.class, "valueOf", Object.class));
                        initialCapacityForMethod = metaAccessProvider
                                        .lookupJavaMethod(ReflectionUtil.lookupMethod(SubstrateSBConcatHelper.CapacityCalculator.class, "initialCapacityFor", String.class));
                        validateCapacityMethod = metaAccessProvider.lookupJavaMethod(ReflectionUtil.lookupMethod(SubstrateSBConcatHelper.class, "validateCapacity", int.class));
                        ensureNonNullMethod = metaAccessProvider.lookupJavaMethod(ReflectionUtil.lookupMethod(Objects.class, "requireNonNull", Object.class));

                        boxedTypes = EconomicMap.create(Equivalence.DEFAULT, JavaKind.values().length - 2);
                        for (JavaKind kind : JavaKind.values()) {
                            if (kind.isPrimitive()) {
                                boxedTypes.put(metaAccessProvider.lookupJavaType(kind.toBoxedJavaClass()), kind);
                            }
                        }

                        initialized = true;
                    }
                }
            }
        }

        public EconomicMap<ResolvedJavaType, JavaKind> getBoxedTypes() {
            assert initialized && boxedTypes != null;
            return boxedTypes;
        }

        public ResolvedJavaType getStringType() {
            assert initialized && stringType != null;
            return stringType;
        }

        public ResolvedJavaType getStringBuilderType() {
            assert initialized && stringBuilderType != null;
            return stringBuilderType;
        }

        public ResolvedJavaType getStringBufferType() {
            assert initialized && stringBufferType != null;
            return stringBufferType;
        }

        public ResolvedJavaType getCharSequenceType() {
            assert initialized && charSequenceType != null;
            return charSequenceType;
        }

        public ResolvedJavaMethod getStringValueOfMethod() {
            assert initialized && stringValueOfMethod != null;
            return stringValueOfMethod;
        }

        public ResolvedJavaMethod getInitialCapacityForMethod() {
            assert initialized && initialCapacityForMethod != null;
            return initialCapacityForMethod;
        }

        public ResolvedJavaMethod getValidateCapacityMethod() {
            assert initialized && validateCapacityMethod != null;
            return validateCapacityMethod;
        }

        public ResolvedJavaMethod getEnsureNonNullMethod() {
            assert initialized && ensureNonNullMethod != null;
            return ensureNonNullMethod;
        }

    }
}
