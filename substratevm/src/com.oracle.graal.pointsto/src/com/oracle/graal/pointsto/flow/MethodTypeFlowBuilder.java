/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.AbstractAnalysisEngine;
import com.oracle.graal.pointsto.ObjectScanner.EmbeddedRootScan;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.LoadFieldTypeFlow.LoadInstanceFieldTypeFlow;
import com.oracle.graal.pointsto.flow.LoadFieldTypeFlow.LoadStaticFieldTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph.GraphKind;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.LoadIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.UnsafeLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.StoreIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.UnsafeStoreTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow.StoreInstanceFieldTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow.StoreStaticFieldTypeFlow;
import com.oracle.graal.pointsto.flow.builder.TypeFlowBuilder;
import com.oracle.graal.pointsto.flow.builder.TypeFlowGraphBuilder;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysis;
import com.oracle.graal.pointsto.results.StrengthenGraphs;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.common.meta.MultiMethod;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.core.common.type.VoidStamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatEqualsNode;
import jdk.graal.compiler.nodes.calc.FloatLessThanNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLowerThanNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import jdk.graal.compiler.nodes.extended.ClassIsArrayNode;
import jdk.graal.compiler.nodes.extended.FieldOffsetProvider;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.extended.ObjectIsArrayNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndAddNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewArrayWithExceptionNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewInstanceWithExceptionNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayWithExceptionNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndExchangeNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndSwapNode;
import jdk.graal.compiler.nodes.spi.LimitedValueProxy;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.phases.common.BoxNodeIdentityPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.graph.MergeableState;
import jdk.graal.compiler.phases.graph.PostOrderNodeIterator;
import jdk.graal.compiler.replacements.nodes.BasicArrayCopyNode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.CStringConstant;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.graal.compiler.replacements.nodes.ObjectClone;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

/**
 * This class creates the type flow graph for a given method, which is done via a reverse post-order
 * traversal of its IR. The state kept during the traversal is mostly contained within
 * {@link TypeFlowsOfNodes} and its predicate-specific subclass
 * {@link TypeFlowsOfNodesWithPredicates}.
 */
public class MethodTypeFlowBuilder {

    protected final PointsToAnalysis bb;
    protected final MethodFlowsGraph flowsGraph;
    protected final PointsToAnalysisMethod method;
    protected StructuredGraph graph;
    private NodeBitMap processedNodes;
    private Map<PhiNode, TypeFlowBuilder<?>> loopPhiFlows;
    private final GraphKind graphKind;
    private boolean processed = false;
    private final boolean newFlowsGraph;

    protected final TypeFlowGraphBuilder typeFlowGraphBuilder;
    protected List<TypeFlow<?>> postInitFlows = List.of();

    /**
     * Always enabled predicate builder used when no other flow is available as a predicate yet.
     */
    protected final TypeFlowBuilder<?> alwaysEnabled;
    /**
     * Any primitive source type flow builder used for modelling unsupported primitive operations
     * when no predicate better than alwaysEnabled is available, so it is not worth to make a
     * 'local' flow for that.
     */
    private final TypeFlowBuilder<AnyPrimitiveSourceTypeFlow> anyPrimitiveSourceTypeFlowBuilder;

    public MethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod method, MethodFlowsGraph flowsGraph, GraphKind graphKind) {
        this.bb = bb;
        this.method = method;
        this.graphKind = graphKind;
        if (bb.trackPrimitiveValues()) {
            this.alwaysEnabled = bb.usePredicates()
                            ? TypeFlowBuilder.create(bb, method, null, PointsToAnalysis.syntheticSourcePosition(method), AlwaysEnabledPredicateFlow.class, bb::getAlwaysEnabledPredicateFlow)
                            : null;
            this.anyPrimitiveSourceTypeFlowBuilder = TypeFlowBuilder.create(bb, method, alwaysEnabled, null, AnyPrimitiveSourceTypeFlow.class, bb::getAnyPrimitiveSourceTypeFlow);
        } else {
            this.alwaysEnabled = null;
            this.anyPrimitiveSourceTypeFlowBuilder = null;
        }
        if (flowsGraph == null) {
            this.flowsGraph = new MethodFlowsGraph(method, graphKind);
            newFlowsGraph = true;
        } else {
            this.flowsGraph = flowsGraph;
            newFlowsGraph = false;
            assert graphKind == GraphKind.FULL : graphKind;
        }
        typeFlowGraphBuilder = new TypeFlowGraphBuilder(bb);
    }

    @SuppressWarnings("try")
    private boolean parse(Object reason, boolean forceReparse) {
        AnalysisParsedGraph analysisParsedGraph = forceReparse ? method.reparseGraph(bb) : method.ensureGraphParsed(bb);

        if (analysisParsedGraph.isIntrinsic()) {
            method.registerAsIntrinsicMethod(reason);
        }

        if (analysisParsedGraph.getEncodedGraph() == null) {
            return false;
        }
        graph = InlineBeforeAnalysis.decodeGraph(bb, method, analysisParsedGraph);

        try (DebugContext.Scope s = graph.getDebug().scope("MethodTypeFlowBuilder", graph)) {
            optimizeGraphBeforeAnalysis(bb, method, graph);

            if (!bb.getUniverse().hostVM().validateGraph(bb, graph)) {
                graph = null;
                return false;
            }

            // Do it again after canonicalization changed type checks and field accesses.
            registerUsedElements(bb, graph, bb.usePredicates());

            return true;
        } catch (Throwable ex) {
            throw graph.getDebug().handle(ex);
        }
    }

    public static void optimizeGraphBeforeAnalysis(AbstractAnalysisEngine bb, AnalysisMethod method, StructuredGraph graph) {
        CanonicalizerPhase canonicalizerPhase = CanonicalizerPhase.create();
        canonicalizerPhase.apply(graph, bb.getProviders(method));
        if (PointstoOptions.ConditionalEliminationBeforeAnalysis.getValue(bb.getOptions())) {
            /*
             * Removing unnecessary conditions before the static analysis runs reduces the size of
             * the type flow graph. For example, this removes redundant null checks: the bytecode
             * parser emits explicit null checks before e.g., all method calls, field access, array
             * accesses; many of those dominate each other.
             */
            new IterativeConditionalEliminationPhase(canonicalizerPhase, false).apply(graph, bb.getProviders(method));
        }
        if (PointstoOptions.EscapeAnalysisBeforeAnalysis.getValue(bb.getOptions())) {
            if (method.isOriginalMethod()) {
                /*
                 * Deoptimization Targets cannot have virtual objects in frame states.
                 *
                 * Also, more work is needed to enable PEA in Runtime Compiled Methods.
                 */
                new BoxNodeIdentityPhase().apply(graph, bb.getProviders(method));
                new PartialEscapePhase(false, canonicalizerPhase, bb.getOptions()).apply(graph, bb.getProviders(method));
            }
        }
    }

    public static void registerUsedElements(AbstractAnalysisEngine bb, StructuredGraph graph, boolean usePredicates) {
        var method = (AnalysisMethod) graph.method();
        HostedProviders providers = bb.getProviders(method);
        for (Node n : graph.getNodes()) {
            if (n instanceof InstanceOfNode) {
                InstanceOfNode node = (InstanceOfNode) n;
                AnalysisType type = (AnalysisType) node.type().getType();
                if (!ignoreInstanceOfType(bb, type)) {
                    type.registerAsReachable(AbstractAnalysisEngine.sourcePosition(node));
                }

            } else if (n instanceof NewInstanceNode) {
                NewInstanceNode node = (NewInstanceNode) n;
                AnalysisType type = (AnalysisType) node.instanceClass();
                if (!usePredicates) {
                    type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));
                    for (var f : type.getInstanceFields(true)) {
                        var field = (AnalysisField) f;
                        PointsToAnalysis pta = (PointsToAnalysis) bb;
                        field.getInitialFlow().addState(pta, TypeState.defaultValueForKind(pta, field.getStorageKind()));
                    }
                }

            } else if (n instanceof NewInstanceWithExceptionNode) {
                NewInstanceWithExceptionNode node = (NewInstanceWithExceptionNode) n;
                AnalysisType type = (AnalysisType) node.instanceClass();
                type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof VirtualObjectNode) {
                VirtualObjectNode node = (VirtualObjectNode) n;
                AnalysisType type = (AnalysisType) node.type();
                type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof NewArrayNode) {
                NewArrayNode node = (NewArrayNode) n;
                AnalysisType type = ((AnalysisType) node.elementType()).getArrayClass();
                type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof NewArrayWithExceptionNode) {
                NewArrayWithExceptionNode node = (NewArrayWithExceptionNode) n;
                AnalysisType type = ((AnalysisType) node.elementType()).getArrayClass();
                type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof NewMultiArrayNode) {
                NewMultiArrayNode node = (NewMultiArrayNode) n;
                AnalysisType type = ((AnalysisType) node.type());
                for (int i = 0; i < node.dimensionCount(); i++) {
                    type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));
                    type = type.getComponentType();
                }

            } else if (n instanceof NewMultiArrayWithExceptionNode) {
                NewMultiArrayWithExceptionNode node = (NewMultiArrayWithExceptionNode) n;
                AnalysisType type = ((AnalysisType) node.type());
                for (int i = 0; i < node.dimensionCount(); i++) {
                    type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));
                    type = type.getComponentType();
                }

            } else if (n instanceof BoxNode) {
                BoxNode node = (BoxNode) n;
                AnalysisType type = (AnalysisType) StampTool.typeOrNull(node, bb.getMetaAccess());
                type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof LoadFieldNode) {
                LoadFieldNode node = (LoadFieldNode) n;
                AnalysisField field = (AnalysisField) node.field();
                field.registerAsRead(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof StoreFieldNode) {
                StoreFieldNode node = (StoreFieldNode) n;
                AnalysisField field = (AnalysisField) node.field();
                field.registerAsWritten(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof ConstantNode cn) {
                /* GR-58472: We should try to delay marking constants as instantiated */
                JavaConstant root = cn.asJavaConstant();
                if (cn.hasUsages() && cn.isJavaConstant() && root.getJavaKind() == JavaKind.Object && root.isNonNull()) {
                    assert StampTool.isExactType(cn) : cn;
                    if (!ignoreConstant(bb, cn)) {
                        AnalysisType type = (AnalysisType) StampTool.typeOrNull(cn, bb.getMetaAccess());
                        type.registerAsInstantiated(new EmbeddedRootScan(AbstractAnalysisEngine.sourcePosition(cn), root));
                        registerEmbeddedRoot(bb, cn);
                    }
                }

            } else if (n instanceof FieldOffsetProvider node) {
                if (needsUnsafeRegistration(node)) {
                    ((AnalysisField) node.getField()).registerAsUnsafeAccessed(AbstractAnalysisEngine.sourcePosition(node.asNode()));
                }

            } else if (n instanceof FrameState) {
                FrameState node = (FrameState) n;
                AnalysisMethod frameStateMethod = (AnalysisMethod) node.getMethod();
                if (frameStateMethod != null) {
                    /*
                     * All types referenced in (possibly inlined) frame states must be reachable,
                     * because these classes will be reachable from stack walking metadata. This
                     * metadata is only constructed after AOT compilation, so the image heap
                     * scanning during static analysis does not see these classes.
                     */
                    frameStateMethod.getDeclaringClass().registerAsReachable(AbstractAnalysisEngine.syntheticSourcePosition(node, method));
                }

            } else if (n instanceof ForeignCall) {
                ForeignCall node = (ForeignCall) n;
                registerForeignCall(bb, providers.getForeignCalls(), node.getDescriptor(), graph.method());
            } else if (n instanceof UnaryMathIntrinsicNode) {
                UnaryMathIntrinsicNode node = (UnaryMathIntrinsicNode) n;
                registerForeignCall(bb, providers.getForeignCalls(), providers.getForeignCalls().getDescriptor(node.getOperation().foreignCallSignature), graph.method());
            } else if (n instanceof BinaryMathIntrinsicNode) {
                BinaryMathIntrinsicNode node = (BinaryMathIntrinsicNode) n;
                registerForeignCall(bb, providers.getForeignCalls(), providers.getForeignCalls().getDescriptor(node.getOperation().foreignCallSignature), graph.method());
            }
        }
    }

    /**
     * This method filters constants, i.e., these constants are not seen as reachable on its own.
     * This avoids making things reachable just because of that constant usage. For all cases where
     * this method returns true, {@link StrengthenGraphs} must have a corresponding re-write of the
     * constant in case nothing else in the application made that constant reachable.
     *
     * {@link Class#isAssignableFrom} is often used with a constant receiver class. In that case, we
     * do not want to make the receiver class reachable, because as long as the receiver class is
     * not reachable for any other "real" reason we know that isAssignableFrom will always return
     * false. So in {@link StrengthenGraphs} we can then constant-fold the
     * {@link ClassIsAssignableFromNode} to false. We only apply this optimization for
     * {@link ClassIsAssignableFromNode} if it's a closed type world, for open world we cannot fold
     * the type check since the type may be used later.
     *
     * Similarly, a class should not be marked as reachable only so that we can add the class name
     * to the error message of a {@link ClassCastException}. In {@link StrengthenGraphs} we can
     * re-write the Class constant to a String constant, i.e., only embed the class name and not the
     * full java.lang.Class object in the image. We can apply this optimization optimistically for
     * both closed and open type world.
     *
     * {@link FrameState} are only used for debugging. We do not want to have larger images just so
     * that users can see a constant value in the debugger.
     */
    protected static boolean ignoreConstant(AbstractAnalysisEngine bb, ConstantNode node) {
        for (var u : node.usages()) {
            if (u instanceof ClassIsAssignableFromNode usage) {
                if (!bb.getHostVM().isClosedTypeWorld() || usage.getOtherClass() == node || usage.getThisClass() != node) {
                    return false;
                }
            } else if (u instanceof BytecodeExceptionNode usage) {
                /* The checked type is the second argument for a CLASS_CAST. */
                if (usage.getExceptionKind() != BytecodeExceptionKind.CLASS_CAST || usage.getArguments().size() != 2 || usage.getArguments().get(0) == node || usage.getArguments().get(1) != node) {
                    return false;
                }
            } else if (u instanceof FrameState) {
                /* FrameState usages are only for debugging and not necessary for correctness. */
            } else {
                return false;
            }
        }
        /* Success, the ConstantNode does not need to be seen as reachable. */
        return true;
    }

    /**
     * Unsafe access nodes whose offset is a {@link FieldOffsetProvider} are modeled directly as
     * field access type flows and therefore do not need unsafe registration.
     *
     * We do not want that a field is registered as unsafe accessed just so that we have the field
     * offset during debugging, so we also ignore {@link FrameState}. {@link StrengthenGraphs}
     * removes the node from the {@link FrameState} if it is not registered for unsafe access for
     * any other reason.
     */
    protected static boolean needsUnsafeRegistration(FieldOffsetProvider node) {
        for (var usage : node.asNode().usages()) {
            if (usage instanceof RawLoadNode || usage instanceof RawStoreNode ||
                            usage instanceof UnsafeCompareAndSwapNode || usage instanceof UnsafeCompareAndExchangeNode ||
                            usage instanceof AtomicReadAndWriteNode || usage instanceof AtomicReadAndAddNode) {
                /* Unsafe usages are modeled as field type flows. */
            } else if (usage instanceof FrameState) {
                /* FrameState usages are only for debugging and not necessary for correctness. */
            } else {
                return true;
            }
        }
        /* Success, the field does not need to be registered for unsafe access. */
        return false;
    }

    /**
     * In closed type world, just using a type in an instanceof type check doesn't mark the type as
     * reachable. Assuming the type is not otherwise made reachable, this allows the graph
     * strengthening to eliminate the type check completely by replacing a stamp with an unreachable
     * type with an empty stamp (see StrengthenSimplifier#strengthenStamp).
     * <p>
     * However, in open world we cannot make assumptions about types that may become reachable
     * later. Therefore, we must mark the instanceof checked type as reachable. Moreover, stamp
     * strengthening based on reachability status of types must be disabled.
     */
    protected static boolean ignoreInstanceOfType(AbstractAnalysisEngine bb, AnalysisType type) {
        if (bb.getHostVM().ignoreInstanceOfTypeDisallowed()) {
            return false;
        }
        if (!bb.getHostVM().isClosedTypeWorld()) {
            return false;
        }
        if (type == null) {
            return false;
        }
        if (type.isArray()) {
            /*
             * There is no real overhead when making array types reachable (which automatically also
             * makes them instantiated), and it avoids manual reflection configuration because
             * casting to an array type then automatically marks the array type as instantiated.
             */
            return false;
        }
        return true;
    }

    private static void registerEmbeddedRoot(AbstractAnalysisEngine bb, ConstantNode cn) {
        bb.getUniverse().registerEmbeddedRoot(cn.asJavaConstant(), AbstractAnalysisEngine.sourcePosition(cn));
    }

    private static void registerForeignCall(AbstractAnalysisEngine bb, ForeignCallsProvider foreignCallsProvider, ForeignCallDescriptor foreignCallDescriptor, ResolvedJavaMethod from) {
        Optional<AnalysisMethod> targetMethod = bb.getHostVM().handleForeignCall(foreignCallDescriptor, foreignCallsProvider);
        targetMethod.ifPresent(analysisMethod -> bb.addRootMethod(analysisMethod, true, from));
    }

    private boolean handleNodeIntrinsic() {
        if (AnnotationAccess.isAnnotationPresent(method, NodeIntrinsic.class)) {
            graph.getDebug().log("apply MethodTypeFlow on node intrinsic %s", method);
            AnalysisType returnType = method.getSignature().getReturnType();
            if (bb.isSupportedJavaKind(returnType.getJavaKind())) {
                /*
                 * This is a method used in a snippet, so most likely the return value does not
                 * matter at all. However, some methods return an object, and the snippet continues
                 * to work with the object. So pretend that this method returns an object of the
                 * exact return type.
                 */
                TypeFlow<?> returnTypeFlow = flowsGraph.getReturnFlow().getDeclaredType().getTypeFlow(this.bb, true);
                BytecodePosition source = new BytecodePosition(null, method, 0);
                returnTypeFlow = bb.analysisPolicy().proxy(source, returnTypeFlow);
                FormalReturnTypeFlow resultFlow = new FormalReturnTypeFlow(source, returnType);
                resultFlow.enableFlow(bb);
                bb.analysisPolicy().addOriginalUse(bb, returnTypeFlow, resultFlow);
                flowsGraph.addMiscEntryFlow(returnTypeFlow);
                flowsGraph.setReturnFlow(resultFlow);
            }
            return true;
        }

        return false;
    }

    /**
     * If a method has opaque return, we model it conservatively by returning a) all instantiated
     * subtypes of its return type for an object type or b) any primitive value for a primitive.
     */
    private void handleOpaqueReturn() {
        AnalysisError.guarantee(flowsGraph.getReturnFlow() == null, "Expected null return flow");

        AnalysisType returnType = TypeFlow.filterUncheckedInterface(method.getSignature().getReturnType());

        BytecodePosition position = AbstractAnalysisEngine.syntheticSourcePosition(null, method);
        var returnFlow = new FormalReturnTypeFlow(position, returnType);
        /*
         * If we cannot compute the return, we also probably cannot determine its reachability, e.g.
         * in the case of ContinuationInternals.{enterSpecial1,doYield1}.
         */
        returnFlow.enableFlow(bb);
        flowsGraph.setReturnFlow(returnFlow);

        assert returnType.equals(returnFlow.getDeclaredType()) : returnType + " != " + returnFlow.getDeclaredType();
        returnType.getTypeFlow(bb, true).addUse(bb, returnFlow);
    }

    /**
     * Placeholder flows are placed in the graph for any missing flows.
     */
    private void insertPlaceholderParamAndReturnFlows() {
        var paramTypes = method.toParameterList();
        BytecodePosition position = AbstractAnalysisEngine.syntheticSourcePosition(null, method);
        for (int index = 0; index < paramTypes.size(); index++) {
            if (flowsGraph.getParameter(index) == null) {
                if (bb.isSupportedJavaKind(paramTypes.get(index).getJavaKind())) {
                    AnalysisType paramType = paramTypes.get(index);
                    FormalParamTypeFlow parameter;
                    if (index == 0 && !method.isStatic()) {
                        assert paramType.equals(method.getDeclaringClass()) : paramType + ", " + method;
                        parameter = new FormalReceiverTypeFlow(position, paramType);
                    } else {
                        parameter = new FormalParamTypeFlow(position, paramType, index);
                    }
                    parameter.enableFlow(bb);
                    flowsGraph.setParameter(index, parameter);
                }
            }
        }

        if (flowsGraph.getReturnFlow() == null) {
            AnalysisType returnType = method.getSignature().getReturnType();
            if (bb.isSupportedJavaKind(returnType.getJavaKind()) || (bb.usePredicates() && returnType.getJavaKind() == JavaKind.Void)) {
                /*
                 * We want to determine whether void methods can return, so we need to create
                 * FormalReturnTypeFlow for them.
                 */
                FormalReturnTypeFlow returnFlow = new FormalReturnTypeFlow(position, returnType);
                returnFlow.enableFlow(bb);
                flowsGraph.setReturnFlow(returnFlow);
            }
        }

    }

    private void createTypeFlow() {
        processedNodes = new NodeBitMap(graph);

        var typeFlows = bb.usePredicates() ? new TypeFlowsOfNodesWithPredicates() : new TypeFlowsOfNodes();
        typeFlows.setPredicate(alwaysEnabled);
        for (Node n : graph.getNodes()) {
            if (n instanceof ParameterNode) {
                ParameterNode node = (ParameterNode) n;
                if (bb.isSupportedJavaKind(node.getStackKind())) {
                    TypeFlowBuilder<?> paramBuilder = TypeFlowBuilder.create(bb, method, typeFlows.getPredicate(), node, FormalParamTypeFlow.class, () -> {
                        boolean isStatic = Modifier.isStatic(method.getModifiers());
                        int index = node.index();
                        FormalParamTypeFlow parameter = flowsGraph.getParameter(index);
                        if (parameter != null) {
                            // updating source to reflect position in new parsing of code
                            parameter.updateSource(AbstractAnalysisEngine.sourcePosition(node));
                        } else {
                            assert newFlowsGraph : "missing flow from original graph " + parameter;
                            if (!isStatic && index == 0) {
                                AnalysisType paramType = method.getDeclaringClass();
                                parameter = new FormalReceiverTypeFlow(AbstractAnalysisEngine.sourcePosition(node), paramType);
                            } else {
                                int offset = isStatic ? 0 : 1;
                                AnalysisType paramType = method.getSignature().getParameterType(index - offset);
                                parameter = new FormalParamTypeFlow(AbstractAnalysisEngine.sourcePosition(node), paramType, index);
                            }
                            flowsGraph.setParameter(index, parameter);
                        }
                        return parameter;
                    });
                    typeFlowGraphBuilder.checkFormalParameterBuilder(paramBuilder);
                    typeFlows.add(node, paramBuilder);
                    typeFlowGraphBuilder.registerSinkBuilder(paramBuilder);
                }
            } else if (n instanceof BoxNode && !typeFlows.usePredicates()) {
                BoxNode node = (BoxNode) n;
                AnalysisType type = (AnalysisType) StampTool.typeOrNull(node, bb.getMetaAccess());

                TypeFlowBuilder<?> boxBuilder = TypeFlowBuilder.create(bb, method, typeFlows.getPredicate(), node, BoxTypeFlow.class, () -> {
                    BoxTypeFlow boxFlow = new BoxTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type);
                    flowsGraph.addMiscEntryFlow(boxFlow);
                    return boxFlow;
                });
                typeFlows.add(node, boxBuilder);
            }

            for (Node input : n.inputs()) {
                /*
                 * GR-58474: Change the handling of constants so that the SourceTypeFlow is created
                 * on demand, with the optimization that only one SourceTypeFlow is created ever for
                 * every distinct object (using, e.g., caching in a global IdentityHashMap).
                 */
                if (input instanceof ConstantNode && !typeFlows.contains((ConstantNode) input)) {
                    ConstantNode node = (ConstantNode) input;
                    Constant constant = node.getValue();
                    if (node.asJavaConstant() == null && (constant instanceof VMConstant || constant instanceof CStringConstant)) {
                        // do nothing
                    } else if (node.asJavaConstant().isNull()) {
                        if (!typeFlows.usePredicates()) {
                            TypeFlowBuilder<ConstantTypeFlow> sourceBuilder = TypeFlowBuilder.create(bb, method, typeFlows.getPredicate(), node, ConstantTypeFlow.class, () -> {
                                ConstantTypeFlow constantSource = new ConstantTypeFlow(AbstractAnalysisEngine.sourcePosition(node), null, TypeState.forNull());
                                flowsGraph.addMiscEntryFlow(constantSource);
                                return constantSource;
                            });
                            typeFlows.add(node, sourceBuilder);
                        }
                    } else if (node.asJavaConstant().getJavaKind() == JavaKind.Object) {
                        if (!typeFlows.usePredicates()) {
                            assert StampTool.isExactType(node) : node;
                            TypeFlowBuilder<ConstantTypeFlow> sourceBuilder = TypeFlowBuilder.create(bb, method, typeFlows.getPredicate(), node, ConstantTypeFlow.class, () -> {
                                AnalysisType type = (AnalysisType) StampTool.typeOrNull(node, bb.getMetaAccess());
                                assert type.isInstantiated() : type;
                                JavaConstant constantValue = node.asJavaConstant();
                                BytecodePosition position = AbstractAnalysisEngine.sourcePosition(node);
                                JavaConstant heapConstant = bb.getUniverse().getHeapScanner().toImageHeapObject(constantValue, new EmbeddedRootScan(position, constantValue));
                                ConstantTypeFlow constantSource = new ConstantTypeFlow(position, type, TypeState.forConstant(this.bb, heapConstant, type));
                                flowsGraph.addMiscEntryFlow(constantSource);
                                return constantSource;
                            });
                            typeFlows.add(node, sourceBuilder);
                        }
                    }
                }
            }
        }

        // Propagate the type flows through the method's graph
        new NodeIterator(graph.start(), typeFlows).apply();

        /* Prune the method graph. Eliminate nodes with no uses. Collect flows that need init. */
        postInitFlows = typeFlowGraphBuilder.build();
    }

    /**
     * Within typeflow graphs we unproxify values and instead filter types via our typeflows. Note
     * that we also must unproxify {@link LimitedValueProxy}s, as opposed to merely
     * {@link ValueProxy}s, as it is necessary to see through DeoptProxies. The precautionary
     * measures needed for DeoptProxies are accounted for via method linking.
     */
    protected ValueNode typeFlowUnproxify(ValueNode value) {
        ValueNode result = value;
        while (result instanceof LimitedValueProxy) {
            result = ((LimitedValueProxy) result).getOriginalNode();
        }
        return result;
    }

    protected void apply(boolean forceReparse, Object reason) {
        assert !processed : "can only call apply once per MethodTypeFlowBuilder";
        processed = true;

        method.setReachableInCurrentLayer();

        if (method.isDelayed()) {
            /* The method will be analyzed in the application layer */
            return;
        }

        if (method.analyzedInPriorLayer()) {
            /*
             * We don't need to analyze this method. We already know its return type state from the
             * open world analysis. We just install a return flow to link it with its uses.
             */
            // GR-52421: the return should not be opaque, it should be the
            // persisted result of the open-world analysis
            handleOpaqueReturn();
            // GR-52421: verify that tracked parameter state is subset of persisted state
            insertPlaceholderParamAndReturnFlows();
            return;
        }

        // assert method.getAnnotation(Fold.class) == null : method;
        if (handleNodeIntrinsic()) {
            assert !method.hasOpaqueReturn() : method;
            return;
        }

        if (method.hasOpaqueReturn()) {
            handleOpaqueReturn();
        }

        boolean insertPlaceholderFlows = bb.getHostVM().getMultiMethodAnalysisPolicy().insertPlaceholderParamAndReturnFlows(method.getMultiMethodKey());
        if (graphKind == GraphKind.STUB) {
            AnalysisError.guarantee(insertPlaceholderFlows, "placeholder flows must be enabled for STUB graphkinds.");
            insertPlaceholderParamAndReturnFlows();
            return;
        }

        if (!parse(reason, forceReparse)) {
            return;
        }

        bb.getHostVM().methodBeforeTypeFlowCreationHook(bb, method, graph);

        createTypeFlow();

        if (insertPlaceholderFlows) {
            insertPlaceholderParamAndReturnFlows();
        }

        method.setAnalyzedGraph(GraphEncoder.encodeSingleGraph(graph, AnalysisParsedGraph.HOST_ARCHITECTURE, flowsGraph.getNodeFlows().getKeys()));
    }

    /**
     * It only makes sense to create a local version of all instantiated if it will be guarded by a
     * predicate more precise than alwaysEnabled.
     */
    protected TypeFlow<?> maybePatchAllInstantiated(TypeFlow<?> flow, BytecodePosition position, AnalysisType declaredType, Object predicate) {
        if (bb.usePredicates() && flow instanceof AllInstantiatedTypeFlow && predicate != alwaysEnabled) {
            var localFlow = new LocalAllInstantiatedFlow(position, declaredType);
            flowsGraph.addMiscEntryFlow(localFlow);
            flow.addUse(bb, localFlow);
            return localFlow;
        }
        return flow;
    }

    /**
     * Fixed point analysis state. It stores the type flows for all nodes of the method's graph.
     */
    protected class TypeFlowsOfNodes extends MergeableState<TypeFlowsOfNodes> implements Cloneable {

        /**
         * Used to establish dependencies between flows.
         */
        protected final Map<Node, TypeFlowBuilder<?>> flows;

        TypeFlowsOfNodes() {
            this.flows = new HashMap<>();
        }

        protected TypeFlowsOfNodes(TypeFlowsOfNodes copyFrom) {
            this.flows = new HashMap<>(copyFrom.flows);
        }

        public boolean contains(ValueNode node) {
            return flows.containsKey(typeFlowUnproxify(node));
        }

        public TypeFlowBuilder<?> lookupOrAny(ValueNode n, JavaKind kind) {
            if (n != null) {
                return lookup(n);
            } else if (kind == JavaKind.Int || kind == JavaKind.Long) {
                return anyPrimitiveSourceTypeFlowBuilder;
            } else {
                /* For now, we do not need support JavaKind.Object here. */
                throw AnalysisError.shouldNotReachHere("Unimplemented kind: " + kind);
            }
        }

        public TypeFlowBuilder<?> lookup(ValueNode n) {
            ValueNode node = typeFlowUnproxify(n);
            TypeFlowBuilder<?> result = flows.get(node);
            if (result == null) {
                /*
                 * There is no type flow set, yet. Therefore, we have no info for the node.
                 */
                Stamp s = n.stamp(NodeView.DEFAULT);
                if (s instanceof IntegerStamp stamp) {
                    result = handleIntegerStamp(stamp, node);
                } else if (s instanceof ObjectStamp stamp) {
                    result = handleObjectStamp(stamp, node);
                } else {
                    AnalysisError.shouldNotReachHere("Unsupported stamp " + s + ", node " + node + ", class " + node.getClass().getName());
                }
                flows.put(node, result);
            }
            return result;
        }

        protected TypeFlowBuilder<?> handleObjectStamp(ObjectStamp stamp, ValueNode node) {
            if (stamp.isEmpty()) {
                throw AnalysisError.shouldNotReachHere("Stamp for node " + node + " is empty.");
            }
            AnalysisType stampType = (AnalysisType) StampTool.typeOrNull(stamp, bb.getMetaAccess());
            BytecodePosition position = AbstractAnalysisEngine.sourcePosition(node);
            if (stamp.isExactType()) {
                /*
                 * We are lucky: the stamp tells us which type the node has. Happens e.g. for a
                 * predicated boxed node.
                 */
                return TypeFlowBuilder.create(bb, method, getPredicate(), node, SourceTypeFlow.class, () -> {
                    SourceTypeFlow src = new SourceTypeFlow(position, stampType, !stamp.nonNull());
                    flowsGraph.addMiscEntryFlow(src);
                    return src;
                });
            } else {
                /*
                 * Use a type state which consists of all allocated types (which are compatible to
                 * the node's type). It is a conservative assumption.
                 */
                TypeFlowBuilder<?> predicate = getPredicate();
                return TypeFlowBuilder.create(bb, method, predicate, node, TypeFlow.class, () -> {
                    TypeFlow<?> proxy = bb.analysisPolicy().proxy(position, stampType.getTypeFlow(bb, true));
                    flowsGraph.addMiscEntryFlow(proxy);
                    return maybePatchAllInstantiated(proxy, position, stampType, predicate);
                });
            }
        }

        protected TypeFlowBuilder<?> handleIntegerStamp(IntegerStamp stamp, ValueNode node) {
            AnalysisType type = getNodeType(node);
            long lo = stamp.lowerBound();
            long hi = stamp.upperBound();
            if (lo == hi) {
                return TypeFlowBuilder.create(bb, method, getPredicate(), node, ConstantPrimitiveSourceTypeFlow.class, () -> {
                    var flow = new ConstantPrimitiveSourceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type, TypeState.forPrimitiveConstant(bb, lo));
                    flowsGraph.addMiscEntryFlow(flow);
                    return flow;
                });
            } else if (getPredicate() != alwaysEnabled) {
                /*
                 * It only makes sense to create a local version of any primitive flow if it will be
                 * guarded by a more specific predicate than alwaysEnabled. Otherwise, use the
                 * global singleton.
                 */
                return TypeFlowBuilder.create(bb, method, getPredicate(), node, AnyPrimitiveSourceTypeFlow.class, () -> {
                    var flow = new AnyPrimitiveSourceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type);
                    flowsGraph.addMiscEntryFlow(flow);
                    return flow;
                });
            } else {
                return anyPrimitiveSourceTypeFlowBuilder;
            }
        }

        public void add(ValueNode node, TypeFlowBuilder<?> flow) {
            assert !contains(node) : node;
            flows.put(typeFlowUnproxify(node), flow);
        }

        public void update(ValueNode node, TypeFlowBuilder<?> flow) {
            assert contains(node) : node;
            flows.put(typeFlowUnproxify(node), flow);
        }

        @Override
        public boolean merge(AbstractMergeNode merge, List<TypeFlowsOfNodes> withStates) {
            for (AbstractEndNode end : merge.forwardEnds()) {
                if (!processedNodes.contains(end)) {
                    return false;
                }
            }

            Iterator<Map.Entry<Node, TypeFlowBuilder<?>>> iterator = flows.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Node, TypeFlowBuilder<?>> entry = iterator.next();
                Node node = entry.getKey();
                TypeFlowBuilder<?> oldFlow = entry.getValue();
                TypeFlowBuilder<?> newFlow = oldFlow;

                for (TypeFlowsOfNodes other : withStates) {
                    TypeFlowBuilder<?> mergeFlow = other.flows.get(node);

                    if (mergeFlow == null) {
                        iterator.remove();
                        break;
                    } else if (mergeFlow != newFlow) {
                        if (newFlow == oldFlow) {
                            newFlow = TypeFlowBuilder.create(bb, method, null, merge, MergeTypeFlow.class, () -> {
                                MergeTypeFlow newMergeFlow = new MergeTypeFlow(AbstractAnalysisEngine.sourcePosition(merge), mergeFlow.get().getDeclaredType());
                                flowsGraph.addMiscEntryFlow(newMergeFlow);
                                return newMergeFlow;
                            });
                            newFlow.addUseDependency(oldFlow);
                            entry.setValue(newFlow);
                        }
                        newFlow.addUseDependency(mergeFlow);
                    }
                }
            }

            return true;
        }

        @Override
        @SuppressFBWarnings(value = "CN_IDIOM_NO_SUPER_CALL", justification = "TypeFlowsOfNodes uses the clone method in a non-standard way.")
        public TypeFlowsOfNodes clone() {
            return new TypeFlowsOfNodes(this);
        }

        public void setPredicate(@SuppressWarnings("unused") TypeFlowBuilder<?> predicate) {
            // noop
        }

        public TypeFlowBuilder<?> getPredicate() {
            // noop
            return null;
        }

        public boolean usePredicates() {
            return false;
        }

        public void addInvoke(@SuppressWarnings("unused") ValueNode invoke, @SuppressWarnings("unused") TypeFlowBuilder<InvokeTypeFlow> invokeBuilder) {
            // no-op
        }

        public TypeFlowBuilder<InvokeTypeFlow> getInvoke(@SuppressWarnings("unused") ValueNode invoke) {
            // no-op
            return null;
        }
    }

    /**
     * An extension of {@link TypeFlowsOfNodes} that contains predicate-specific functionality.
     * Also, it materializes many more types of flows compared to its parent.
     */
    private class TypeFlowsOfNodesWithPredicates extends TypeFlowsOfNodes implements Cloneable {

        /**
         * Keeps a reference to the current predicate, used to establish predicate edges.
         */
        private TypeFlowBuilder<?> predicate;

        /**
         * Keep a mapping for invokes, so that we can look corresponding TypeFlowBuilders up when
         * handling ExceptionObjectNodes, where we want to use the predicate of the invoke as the
         * predicate for the exception handler, i.e. the exception handler is reachable iff the
         * invoke is.
         * <p>
         * We cannot reuse the {@link TypeFlowsOfNodes#flows}, because it already maps the invoke to
         * the actual return builder and here we want the invoke builder.
         */
        private final Map<ValueNode, TypeFlowBuilder<InvokeTypeFlow>> invokes;

        protected TypeFlowsOfNodesWithPredicates() {
            super();
            this.invokes = new HashMap<>();
        }

        protected TypeFlowsOfNodesWithPredicates(TypeFlowsOfNodesWithPredicates copyFrom) {
            super(copyFrom);
            this.predicate = copyFrom.predicate;
            this.invokes = new HashMap<>(copyFrom.invokes);
        }

        @Override
        public TypeFlowBuilder<?> lookup(ValueNode n) {
            ValueNode node = typeFlowUnproxify(n);
            TypeFlowBuilder<?> result = flows.get(node);
            if (result != null && result.getPredicate() != getPredicate() && n instanceof ParameterNode) {
                /*
                 * If the previous result has outdated predicate, which happens e.g. when a
                 * different method parameter is assigned into a phi node in each branch, create a
                 * local anchor to prevent the value from flowing through unreachable branch.
                 *
                 * We currently do this only for parameter nodes to support the pattern `cond ? p1 :
                 * p2`. In the future, we might extend it to other nodes at the cost of introducing
                 * more LocalAnchorFlows into the graph.
                 */
                var resultBuilder = result;
                var anchorFlow = TypeFlowBuilder.create(bb, method, getPredicate(), result.getSource(), LocalAnchorFlow.class, () -> {
                    var flow = new LocalAnchorFlow(AbstractAnalysisEngine.syntheticSourcePosition(method), resultBuilder.get().declaredType);
                    flowsGraph.addMiscEntryFlow(flow);
                    return flow;
                });
                anchorFlow.addUseDependency(result);
                flows.put(node, anchorFlow);
                return anchorFlow;
            }
            if (result == null) {
                /*
                 * There is no type flow set, yet. Therefore, we have no info for the node. Note
                 * that we use the stamp of the path-dependent proxy, which should be more precise.
                 * If the same node ends up having multiple representations in different branches, a
                 * MergeFlow is introduced when these branches are merged.
                 */
                Stamp s = n.stamp(NodeView.DEFAULT);
                if (node instanceof ConditionalNode conditionalNode) {
                    var condition = lookup(conditionalNode.condition());
                    var trueValue = lookup(conditionalNode.trueValue());
                    var falseValue = lookup(conditionalNode.falseValue());
                    var declaredType = ((AnalysisType) s.javaType(bb.getMetaAccess()));
                    result = TypeFlowBuilder.create(bb, method, getPredicate(), node, ConditionalFlow.class, () -> {
                        var flow = new ConditionalFlow(AbstractAnalysisEngine.sourcePosition(node), declaredType, condition.get(), trueValue.get(), falseValue.get());
                        flowsGraph.addMiscEntryFlow(flow);
                        return flow;
                    });
                    result.addObserverDependency(condition);
                    result.addUseDependency(trueValue);
                    result.addUseDependency(falseValue);
                } else if (node instanceof IntegerEqualsNode equalsNode) {
                    var x = lookup(equalsNode.getX());
                    var y = lookup(equalsNode.getY());
                    var type = getNodeType(equalsNode);
                    result = TypeFlowBuilder.create(bb, method, getPredicate(), node, BooleanPrimitiveCheckTypeFlow.class, () -> {
                        var flow = new BooleanPrimitiveCheckTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type, x.get(), y.get(), PrimitiveComparison.EQ, false);
                        flowsGraph.addMiscEntryFlow(flow);
                        return flow;
                    });
                    result.addUseDependency(x);
                    result.addUseDependency(y);
                } else if (node instanceof IntegerLowerThanNode lowerThan) {
                    var x = lookup(lowerThan.getX());
                    var y = lookup(lowerThan.getY());
                    var isUnsigned = lowerThan instanceof IntegerBelowNode;
                    var type = getNodeType(lowerThan);
                    result = TypeFlowBuilder.create(bb, method, getPredicate(), node, BooleanPrimitiveCheckTypeFlow.class, () -> {
                        var flow = new BooleanPrimitiveCheckTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type, x.get(), y.get(), PrimitiveComparison.LT, isUnsigned);
                        flowsGraph.addMiscEntryFlow(flow);
                        return flow;
                    });
                    result.addUseDependency(x);
                    result.addUseDependency(y);
                } else if (node instanceof IsNullNode isNull) {
                    ValueNode object = isNull.getValue();
                    TypeFlowBuilder<?> inputBuilder = lookup(object);
                    var type = getNodeType(isNull);
                    result = TypeFlowBuilder.create(bb, method, getPredicate(), node, BooleanNullCheckTypeFlow.class, () -> {
                        var flow = new BooleanNullCheckTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type);
                        flowsGraph.addMiscEntryFlow(flow);
                        return flow;
                    });
                    result.addUseDependency(inputBuilder);
                } else if (node instanceof InstanceOfNode instanceOf) {
                    ValueNode object = instanceOf.getValue();
                    TypeReference typeReference = instanceOf.type();
                    var type = (AnalysisType) instanceOf.type().getType();
                    TypeFlowBuilder<?> objectBuilder = lookup(object);
                    result = TypeFlowBuilder.create(bb, method, getPredicate(), node, BooleanInstanceOfCheckTypeFlow.class, () -> {
                        var flow = new BooleanInstanceOfCheckTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type, typeReference.isExact(), instanceOf.allowsNull(), bb.getLongType());
                        flowsGraph.addMiscEntryFlow(flow);
                        return flow;
                    });
                    result.addUseDependency(objectBuilder);
                } else if (node instanceof ConstantNode && node.asJavaConstant().isNull()) {
                    result = TypeFlowBuilder.create(bb, method, getPredicate(), node, ConstantTypeFlow.class, () -> {
                        ConstantTypeFlow constantSource = new ConstantTypeFlow(AbstractAnalysisEngine.sourcePosition(node), null, TypeState.forNull());
                        flowsGraph.addMiscEntryFlow(constantSource);
                        return constantSource;
                    });
                } else if (node instanceof ConstantNode && node.asJavaConstant().getJavaKind() == JavaKind.Object) {
                    assert StampTool.isExactType(node) : node;
                    AnalysisType type = (AnalysisType) StampTool.typeOrNull(node, bb.getMetaAccess());
                    result = TypeFlowBuilder.create(bb, method, getPredicate(), node, ConstantTypeFlow.class, () -> {
                        JavaConstant constantValue = node.asJavaConstant();
                        BytecodePosition position = AbstractAnalysisEngine.sourcePosition(node);
                        JavaConstant heapConstant = bb.getUniverse().getHeapScanner().toImageHeapObject(constantValue, new EmbeddedRootScan(position, constantValue));
                        ConstantTypeFlow constantSource = new ConstantTypeFlow(position, type, TypeState.forConstant(bb, heapConstant, type));
                        flowsGraph.addMiscEntryFlow(constantSource);
                        return constantSource;
                    });
                } else if (s instanceof IntegerStamp stamp) {
                    result = handleIntegerStamp(stamp, node);
                } else if (s instanceof ObjectStamp stamp) {
                    result = handleObjectStamp(stamp, node);
                } else {
                    assert s instanceof VoidStamp : "The only remaining case should be a void stamp: " + s;
                    /*
                     * GR-58471: Some of the nodes below might still be handled, some of them maybe
                     * should not be encountered (e.g. ShortCircuitOrNode).
                     */
                    assert node instanceof ClassIsArrayNode || node instanceof ClassIsAssignableFromNode || node instanceof ShortCircuitOrNode || node instanceof ObjectEqualsNode ||
                                    node instanceof FloatEqualsNode ||
                                    node instanceof FloatLessThanNode ||
                                    node instanceof IntegerTestNode || node instanceof InstanceOfDynamicNode ||
                                    node instanceof ObjectIsArrayNode : "Unsupported combination of stamp " + s + ", node " + node + ", class " + node.getClass().getName();
                    var type = getNodeType(node);
                    if (getPredicate() != null) {
                        result = TypeFlowBuilder.create(bb, method, getPredicate(), node, AnyPrimitiveSourceTypeFlow.class, () -> {
                            var flow = new AnyPrimitiveSourceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type);
                            flowsGraph.addMiscEntryFlow(flow);
                            return flow;
                        });
                    } else {
                        result = anyPrimitiveSourceTypeFlowBuilder;
                    }

                }
                flows.put(node, result);
            }
            return result;
        }

        @Override
        public void addInvoke(ValueNode invoke, TypeFlowBuilder<InvokeTypeFlow> invokeBuilder) {
            invokes.put(invoke, invokeBuilder);
        }

        @Override
        public TypeFlowBuilder<InvokeTypeFlow> getInvoke(ValueNode invoke) {
            return invokes.get(invoke);
        }

        @Override
        public boolean merge(AbstractMergeNode merge, List<TypeFlowsOfNodes> withStates) {
            for (AbstractEndNode end : merge.forwardEnds()) {
                if (!processedNodes.contains(end)) {
                    return false;
                }
            }

            var oldPredicate = getPredicate();
            /*
             * If there are no other states to merge, we can just keep the current predicate.
             * Otherwise, we create a PredicateMergeFlow.
             */
            if (!withStates.isEmpty()) {
                var predicates = new ArrayList<TypeFlowBuilder<?>>();
                predicates.add(getPredicate());
                for (TypeFlowsOfNodes other : withStates) {
                    if (other.getPredicate() != null) {
                        predicates.add(other.getPredicate());
                    } else {
                        break;
                    }
                }
                if (predicates.size() == withStates.size() + 1) {
                    assert predicates.size() > 1 : "PredMerge with 1 input is not necessary, just reuse the input.";
                    var predicateMergeFlow = TypeFlowBuilder.create(bb, method, predicates, merge, PredicateMergeFlow.class, () -> {
                        var flow = new PredicateMergeFlow(AbstractAnalysisEngine.sourcePosition(merge));
                        flowsGraph.addMiscEntryFlow(flow);
                        return flow;
                    });
                    setPredicate(predicateMergeFlow);
                } else {
                    setPredicate(alwaysEnabled);
                }
            }
            assert getPredicate() != null : "Each merge should have a predicate.";

            Iterator<Map.Entry<Node, TypeFlowBuilder<?>>> iterator = flows.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Node, TypeFlowBuilder<?>> entry = iterator.next();
                Node node = entry.getKey();
                TypeFlowBuilder<?> oldFlow = entry.getValue();
                if (shouldAddAnchorFlow(oldFlow, oldPredicate)) {
                    oldFlow = anchorFlow(merge, oldPredicate, oldFlow);
                }
                TypeFlowBuilder<?> newFlow = oldFlow;

                for (TypeFlowsOfNodes other : withStates) {
                    TypeFlowBuilder<?> mergeFlow = other.flows.get(node);

                    if (mergeFlow == null) {
                        iterator.remove();
                        break;
                    } else if (mergeFlow != newFlow) {
                        if (newFlow == oldFlow) {
                            TypeFlowBuilder<?> m = mergeFlow;
                            newFlow = TypeFlowBuilder.create(bb, method, getPredicate(), merge, MergeTypeFlow.class, () -> {
                                MergeTypeFlow newMergeFlow = new MergeTypeFlow(AbstractAnalysisEngine.sourcePosition(merge), m.get().getDeclaredType());
                                flowsGraph.addMiscEntryFlow(newMergeFlow);
                                return newMergeFlow;
                            });
                            newFlow.addUseDependency(oldFlow);
                            entry.setValue(newFlow);
                        }
                        if (shouldAddAnchorFlow(mergeFlow, other.getPredicate())) {
                            mergeFlow = anchorFlow(merge, other.getPredicate(), mergeFlow);
                        }
                        newFlow.addUseDependency(mergeFlow);
                    }
                }
            }

            return true;
        }

        /**
         * Test whether it is worth it to add a LocalAnchorFlow for a given flow. LocalAnchorFlows
         * are beneficial if the branch can never return, which happens due to always failing method
         * invocation, or infinite loop. To prevent from introducing too many LocalAnchorFlows, we
         * only materialize them for ActualReturnTypeFlow (results of method invocations) or
         * PredicateMergeFlows.
         */
        private boolean shouldAddAnchorFlow(TypeFlowBuilder<?> flow, TypeFlowBuilder<?> branchPredicate) {
            if (branchPredicate.getFlowClass() == PredicateMergeFlow.class || branchPredicate.getFlowClass() == ActualReturnTypeFlow.class) {
                if (flow.getFlowClass() == FilterTypeFlow.class || flow.getFlowClass() == NullCheckTypeFlow.class || flow.getFlowClass() == PrimitiveFilterTypeFlow.class) {
                    return flow.getPredicate() != branchPredicate;
                }
            }
            return false;
        }

        /**
         * Create a flow anchored at the end of a given branch that is enabled by the latest
         * predicate in that branch.
         */
        private TypeFlowBuilder<?> anchorFlow(AbstractMergeNode merge, TypeFlowBuilder<?> currentPredicate, TypeFlowBuilder<?> oldFlow) {
            var anchorFlow = TypeFlowBuilder.create(bb, method, currentPredicate, merge, LocalAnchorFlow.class, () -> {
                var flow = new LocalAnchorFlow(AbstractAnalysisEngine.syntheticSourcePosition(merge, method), oldFlow.get().declaredType);
                flowsGraph.addMiscEntryFlow(flow);
                return flow;
            });
            anchorFlow.addUseDependency(oldFlow);
            return anchorFlow;
        }

        @Override
        public void loopEnds(LoopBeginNode loopBegin, List<TypeFlowsOfNodes> loopEndStates) {
            for (ValuePhiNode phi : loopBegin.valuePhis()) {
                if (bb.isSupportedJavaKind(phi.getStackKind())) {
                    var mergeFlow = loopPhiFlows.get(phi);
                    for (int i = 0; i < loopEndStates.size(); i++) {
                        ValueNode valueNode = phi.valueAt(i + 1);
                        var x = loopEndStates.get(i).lookup(valueNode);
                        mergeFlow.addUseDependency(x);
                    }
                    update(phi, mergeFlow);
                }
            }
        }

        @Override
        @SuppressFBWarnings(value = "CN_IDIOM_NO_SUPER_CALL", justification = "TypeFlowsOfNodes uses the clone method in a non-standard way.")
        public TypeFlowsOfNodes clone() {
            return new TypeFlowsOfNodesWithPredicates(this);
        }

        @Override
        public void setPredicate(TypeFlowBuilder<?> predicate) {
            this.predicate = predicate;
        }

        @Override
        public TypeFlowBuilder<?> getPredicate() {
            return this.predicate;
        }

        @Override
        public boolean usePredicates() {
            return true;
        }
    }

    /**
     * Fixed point analysis iterator. It propagates the type flows through the nodes of the method's
     * graph.
     */
    class NodeIterator extends PostOrderNodeIterator<TypeFlowsOfNodes> {

        private TypeFlowBuilder<?> returnBuilder;

        NodeIterator(FixedNode start, TypeFlowsOfNodes typeFlows) {
            super(start, typeFlows);
            returnBuilder = null;
        }

        /**
         * A method can have multiple return flows. Since we create the return flow lazily we want
         * to make sure it is created only once. The source for the return flow, used for debugging
         * only, will be the first parsed ReturnNode.
         */
        private TypeFlowBuilder<?> uniqueReturnFlowBuilder(ReturnNode node) {
            if (returnBuilder == null) {
                AnalysisType returnType = method.getSignature().getReturnType();
                if (bb.isSupportedJavaKind(returnType.getJavaKind()) || (state.usePredicates() && returnType.getJavaKind() == JavaKind.Void)) {
                    returnBuilder = TypeFlowBuilder.create(bb, method, alwaysEnabled, node, FormalReturnTypeFlow.class, () -> {
                        FormalReturnTypeFlow returnFlow = flowsGraph.getReturnFlow();
                        if (returnFlow != null) {
                            // updating source to reflect position in new parsing of code
                            returnFlow.updateSource(AbstractAnalysisEngine.sourcePosition(node));
                        } else {
                            assert newFlowsGraph : "missing flow from original graph " + returnFlow;
                            returnFlow = new FormalReturnTypeFlow(AbstractAnalysisEngine.sourcePosition(node), returnType);
                            flowsGraph.setReturnFlow(returnFlow);
                        }
                        return returnFlow;
                    });
                    /*
                     * Formal return must not be removed. It is linked when the callee is analyzed,
                     * hence, although it might not have any uses, cannot be removed during parsing.
                     */
                    typeFlowGraphBuilder.registerSinkBuilder(returnBuilder);
                }
            }
            return returnBuilder;
        }

        private void handleCompareNode(ValueNode source, CompareNode condition, PrimitiveComparison comparison, boolean isUnsigned) {
            var xNode = typeFlowUnproxify(condition.getX());
            var yNode = typeFlowUnproxify(condition.getY());
            /* Ensure that if one input is constant, it is always y. */
            PrimitiveComparison maybeFlipped;
            if (xNode.isConstant() && !yNode.isConstant()) {
                var tmp = xNode;
                xNode = yNode;
                yNode = tmp;
                maybeFlipped = comparison.flip();
            } else {
                maybeFlipped = comparison;
            }
            var xFlow = state.lookup(xNode);
            if (yNode.isConstant()) {
                TypeState rightState = TypeState.forPrimitiveConstant(bb, yNode.asJavaConstant().asLong());
                var builder = TypeFlowBuilder.create(bb, method, state.getPredicate(), source, PrimitiveFilterTypeFlow.class, () -> {
                    var flow = new PrimitiveFilterTypeFlow.ConstantFilter(AbstractAnalysisEngine.sourcePosition(source), xFlow.get().declaredType, xFlow.get(), rightState, maybeFlipped, isUnsigned);
                    flowsGraph.addNodeFlow(source, flow);
                    return flow;
                });
                builder.addUseDependency(xFlow);
                typeFlowGraphBuilder.registerSinkBuilder(builder);
                state.update(xNode, builder);
                state.setPredicate(builder);
            } else {
                var yFlow = state.lookup(yNode);
                var leftFlowBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), source, PrimitiveFilterTypeFlow.class, () -> {
                    var flow = new PrimitiveFilterTypeFlow.VariableFilter(AbstractAnalysisEngine.sourcePosition(source), xFlow.get().declaredType, xFlow.get(), yFlow.get(), maybeFlipped,
                                    isUnsigned);
                    flowsGraph.addNodeFlow(source, flow);
                    return flow;
                });
                leftFlowBuilder.addUseDependency(xFlow);
                leftFlowBuilder.addUseDependency(yFlow);
                typeFlowGraphBuilder.registerSinkBuilder(leftFlowBuilder);
                state.update(xNode, leftFlowBuilder);
                state.setPredicate(leftFlowBuilder);
                var rightFlowBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), source, PrimitiveFilterTypeFlow.class, () -> {
                    var flow = new PrimitiveFilterTypeFlow.VariableFilter(AbstractAnalysisEngine.sourcePosition(source), yFlow.get().declaredType, yFlow.get(), xFlow.get(), maybeFlipped.flip(),
                                    isUnsigned);
                    flowsGraph.addMiscEntryFlow(flow);
                    return flow;
                });
                rightFlowBuilder.addUseDependency(yFlow);
                rightFlowBuilder.addUseDependency(xFlow);
                typeFlowGraphBuilder.registerSinkBuilder(rightFlowBuilder);
                state.update(yNode, rightFlowBuilder);
                state.setPredicate(rightFlowBuilder);
            }
        }

        private void handleUnaryOpLogicNode(UnaryOpLogicNode condition, TypeFlowBuilder<?> builder) {
            ValueNode object = condition.getValue();
            TypeFlowBuilder<?> inputBuilder = state.lookup(object);
            builder.addUseDependency(inputBuilder);
            typeFlowGraphBuilder.registerSinkBuilder(builder);
            state.update(object, builder);
            state.setPredicate(builder);
        }

        private void handleCondition(ValueNode source, LogicNode condition, boolean isTrue) {
            if (state.usePredicates()) {
                if (condition instanceof IntegerLowerThanNode lowerThan) {
                    var isUnsigned = lowerThan instanceof IntegerBelowNode;
                    handleCompareNode(source, lowerThan, isTrue ? PrimitiveComparison.LT : PrimitiveComparison.GE, isUnsigned);
                } else if (condition instanceof IntegerEqualsNode equalsNode) {
                    handleCompareNode(source, equalsNode, isTrue ? PrimitiveComparison.EQ : PrimitiveComparison.NEQ, false);
                }
            }
            if (condition instanceof IsNullNode nullCheck) {
                var inputBuilder = state.lookup(nullCheck.getValue());
                var nullCheckBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), source, NullCheckTypeFlow.class, () -> {
                    NullCheckTypeFlow nullCheckFlow = new NullCheckTypeFlow(AbstractAnalysisEngine.sourcePosition(source), inputBuilder.get().getDeclaredType(), !isTrue);
                    flowsGraph.addNodeFlow(source, nullCheckFlow);
                    return nullCheckFlow;
                });
                handleUnaryOpLogicNode(nullCheck, nullCheckBuilder);
            } else if (condition instanceof InstanceOfNode instanceOf) {
                var typeReference = instanceOf.type();
                var type = (AnalysisType) instanceOf.type().getType();
                var filterBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), source, FilterTypeFlow.class, () -> {
                    FilterTypeFlow filterFlow = new FilterTypeFlow(AbstractAnalysisEngine.sourcePosition(source), type, typeReference.isExact(), isTrue, !isTrue ^ instanceOf.allowsNull());
                    flowsGraph.addNodeFlow(source, filterFlow);
                    return filterFlow;
                });
                handleUnaryOpLogicNode(instanceOf, filterBuilder);
            }
        }

        @Override
        protected void node(FixedNode n) {
            processedNodes.mark(n);

            // Note: the state is the typeFlows which was passed to the constructor.
            if (delegateNodeProcessing(n, state)) {
                // processed by subclass
                return;
            } else if (n instanceof LoopEndNode end) {
                LoopBeginNode merge = end.loopBegin();
                int predIdx = merge.phiPredecessorIndex(end);
                for (PhiNode phi : merge.phis()) {
                    if (bb.isSupportedJavaKind(phi.getStackKind())) {
                        /*
                         * Looking up the input of a phi node at the end of its branch ensures it is
                         * created with the appropriate predicate.
                         */
                        loopPhiFlows.get(phi).addUseDependency(state.lookup(phi.valueAt(predIdx)));
                    }
                }

            } else if (n instanceof LoopBeginNode merge) {
                for (PhiNode phi : merge.phis()) {
                    if (bb.isSupportedJavaKind(phi.getStackKind())) {
                        TypeFlowBuilder<MergeTypeFlow> newFlowBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), merge, MergeTypeFlow.class, () -> {
                            MergeTypeFlow newFlow = new MergeTypeFlow(AbstractAnalysisEngine.sourcePosition(merge), getNodeType(phi));
                            flowsGraph.addMiscEntryFlow(newFlow);
                            return newFlow;
                        });
                        newFlowBuilder.addUseDependency(state.lookup(phi));
                        state.update(phi, newFlowBuilder);

                        if (loopPhiFlows == null) {
                            loopPhiFlows = new HashMap<>();
                        }
                        loopPhiFlows.put(phi, newFlowBuilder);
                    }
                }

            } else if (n instanceof EndNode end) {
                AbstractMergeNode merge = end.merge();
                int predIdx = merge.phiPredecessorIndex(end);
                for (PhiNode phi : merge.phis()) {
                    if (bb.isSupportedJavaKind(phi.getStackKind())) {
                        /*
                         * Looking up the input of a phi node at the end of its branch ensures it is
                         * created with the appropriate predicate.
                         */
                        state.add(phi, state.lookup(phi.valueAt(predIdx)));
                    }
                }

            } else if (n instanceof ExceptionObjectNode node) {
                /*
                 * We are entering an exception handler. This block can be reachable even when the
                 * method return type state is empty (when it always throws exception).
                 */
                if (n.predecessor() instanceof Invoke invoke) {
                    TypeFlowBuilder<InvokeTypeFlow> invokeBuilder = state.getInvoke(invoke.asFixedNode());
                    state.setPredicate(invokeBuilder != null ? ((TypeFlowBuilder<?>) invokeBuilder.getPredicate()) : alwaysEnabled);
                } else {
                    state.setPredicate(alwaysEnabled);
                }
                TypeFlowBuilder<?> predicate = state.getPredicate();
                TypeFlowBuilder<?> exceptionObjectBuilder = TypeFlowBuilder.create(bb, method, predicate, node, TypeFlow.class, () -> {
                    AnalysisType analysisType = (AnalysisType) StampTool.typeOrNull(node, bb.getMetaAccess());
                    TypeFlow<?> input = analysisType.getTypeFlow(bb, false);
                    BytecodePosition position = AbstractAnalysisEngine.sourcePosition(node);
                    TypeFlow<?> exceptionObjectFlow = bb.analysisPolicy().proxy(position, input);
                    flowsGraph.addMiscEntryFlow(exceptionObjectFlow);
                    return maybePatchAllInstantiated(exceptionObjectFlow, position, analysisType, predicate);
                });
                state.add(node, exceptionObjectBuilder);

            } else if (n instanceof AbstractBeginNode node) {
                if (node.predecessor() instanceof IfNode ifNode) {
                    handleCondition(node, ifNode.condition(), node == ifNode.trueSuccessor());
                }

            } else if (n instanceof FixedGuardNode) {
                FixedGuardNode node = (FixedGuardNode) n;
                handleCondition(node, node.condition(), !node.isNegated());

            } else if (n instanceof ReturnNode node) {
                /*
                 * Return type flows within the graph do not need to be linked if the method has
                 * opaque return.
                 */
                if (!method.hasOpaqueReturn()) {
                    if (node.result() != null && bb.isSupportedJavaKind(node.result().getStackKind())) {
                        TypeFlowBuilder<?> returnFlowBuilder = uniqueReturnFlowBuilder(node);
                        returnFlowBuilder.addUseDependency(state.lookup(node.result()));
                    } else if (state.usePredicates() && method.getSignature().getReturnType().getJavaKind() == JavaKind.Void) {
                        TypeFlowBuilder<?> returnFlowBuilder = uniqueReturnFlowBuilder(node);
                        /*
                         * This return is reachable iff the latest predicate has non-empty
                         * TypeState, which we encode by a use edge between the predicate and the
                         * ReturnFlow.
                         */
                        returnFlowBuilder.addUseDependency(state.getPredicate());
                    }
                }
            } else if (n instanceof CommitAllocationNode) {
                processCommitAllocation((CommitAllocationNode) n, state);
            } else if (n instanceof NewInstanceNode) {
                processNewInstance((NewInstanceNode) n, state, true);
            } else if (n instanceof DynamicNewInstanceNode) {
                DynamicNewInstanceNode node = (DynamicNewInstanceNode) n;
                ValueNode instanceTypeNode = node.getInstanceType();

                TypeFlowBuilder<?> instanceTypeBuilder;
                AnalysisType instanceType;
                if (instanceTypeNode instanceof GetClassNode) {
                    /*
                     * The dynamic new instance will create a new heap object for each type in the
                     * type state of the GetClassNode object. Thus, we don't need the type flow of
                     * the GetClassNode itself, which will give us Class objects, but that of its
                     * receiver object.
                     */
                    GetClassNode getClassNode = (GetClassNode) instanceTypeNode;
                    ValueNode getClassReceiver = getClassNode.getObject();
                    instanceType = (AnalysisType) StampTool.typeOrNull(getClassReceiver, bb.getMetaAccess());
                    instanceTypeBuilder = state.lookup(getClassReceiver);
                } else {
                    /*
                     * Without precise type information the dynamic new instance node has to
                     * generate a heap object for each instantiated type.
                     */
                    instanceType = bb.getObjectType();
                    TypeFlowBuilder<?> predicate = state.getPredicate();
                    instanceTypeBuilder = TypeFlowBuilder.create(bb, method, predicate, instanceType, TypeFlow.class,
                                    () -> maybePatchAllInstantiated(instanceType.getTypeFlow(bb, false), AbstractAnalysisEngine.sourcePosition(node), instanceType, predicate));
                }
                TypeFlowBuilder<DynamicNewInstanceTypeFlow> dynamicNewInstanceBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, DynamicNewInstanceTypeFlow.class, () -> {
                    DynamicNewInstanceTypeFlow newInstanceTypeFlow = new DynamicNewInstanceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), instanceTypeBuilder.get(), instanceType);
                    flowsGraph.addMiscEntryFlow(newInstanceTypeFlow);
                    return newInstanceTypeFlow;
                });

                dynamicNewInstanceBuilder.addObserverDependency(instanceTypeBuilder);

                state.add(node, dynamicNewInstanceBuilder);

            } else if (n instanceof NewArrayNode) {
                processNewArray((NewArrayNode) n, state);
            } else if (n instanceof DynamicNewArrayNode) {
                DynamicNewArrayNode node = (DynamicNewArrayNode) n;

                /*
                 * Without precise type information the dynamic new array node has to generate a
                 * heap object for each instantiated array type.
                 *
                 * The node can allocate subclasses of Object[] but also primitive arrays. So there
                 * is no better type than java.lang.Object that we can use.
                 */
                AnalysisType arrayType = bb.getObjectType();

                TypeFlowBuilder<DynamicNewInstanceTypeFlow> dynamicNewArrayBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, DynamicNewInstanceTypeFlow.class, () -> {
                    DynamicNewInstanceTypeFlow newArrayTypeFlow = new DynamicNewInstanceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), arrayType.getTypeFlow(bb, false), arrayType);
                    flowsGraph.addMiscEntryFlow(newArrayTypeFlow);
                    return newArrayTypeFlow;
                });
                state.add(node, dynamicNewArrayBuilder);

            } else if (n instanceof NewMultiArrayNode) {
                NewMultiArrayNode node = (NewMultiArrayNode) n;
                AnalysisType type = ((AnalysisType) node.type());
                assert type.isInstantiated() : type;

                TypeFlowBuilder<NewInstanceTypeFlow> newArrayBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, NewInstanceTypeFlow.class, () -> {
                    NewInstanceTypeFlow newArray = new NewInstanceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type, true);
                    flowsGraph.addMiscEntryFlow(newArray);
                    return newArray;
                });

                state.add(node, newArrayBuilder);

            } else if (n instanceof LoadFieldNode node) { // value = object.field
                processLoadField(node, (AnalysisField) node.field(), node.object(), state);
                if (node.object() != null) {
                    processImplicitNonNull(node.object(), state);
                }

            } else if (n instanceof StoreFieldNode node) { // object.field = value
                processStoreField(node, (AnalysisField) node.field(), node.object(), node.value(), node.value().getStackKind(), state);
                if (node.object() != null) {
                    processImplicitNonNull(node.object(), state);
                }

            } else if (n instanceof LoadIndexedNode node) {
                processLoadIndexed(node, node.array(), state);
                processImplicitNonNull(node.array(), state);

            } else if (n instanceof StoreIndexedNode node) {
                processStoreIndexed(node, node.array(), node.value(), node.value().getStackKind(), state);
                processImplicitNonNull(node.array(), state);

            } else if (n instanceof RawLoadNode node) {
                modelUnsafeReadOnlyFlow(node, node.object(), node.offset());
            } else if (n instanceof RawStoreNode node) {
                modelUnsafeWriteOnlyFlow(node, node.object(), node.value(), node.value().getStackKind(), node.offset());
            } else if (n instanceof UnsafeCompareAndSwapNode node) {
                modelUnsafeWriteOnlyFlow(node, node.object(), node.newValue(), node.newValue().getStackKind(), node.offset());
            } else if (n instanceof UnsafeCompareAndExchangeNode node) {
                modelUnsafeReadAndWriteFlow(node, node.object(), node.newValue(), node.newValue().getStackKind(), node.offset());
            } else if (n instanceof AtomicReadAndWriteNode node) {
                modelUnsafeReadAndWriteFlow(node, node.object(), node.newValue(), node.newValue().getStackKind(), node.offset());
            } else if (n instanceof AtomicReadAndAddNode node) {
                modelUnsafeReadAndWriteFlow(node, node.object(), null, node.offset().getStackKind(), node.offset());

            } else if (n instanceof BasicArrayCopyNode) {
                BasicArrayCopyNode node = (BasicArrayCopyNode) n;

                TypeFlowBuilder<?> srcBuilder = state.lookup(node.getSource());
                TypeFlowBuilder<?> dstBuilder = state.lookup(node.getDestination());

                /*
                 * Shuffling elements around in the same array (source and target are the same) does
                 * not need a type flow. We do not track individual array elements.
                 */
                if (srcBuilder != dstBuilder) {
                    AnalysisType type = (AnalysisType) StampTool.typeOrNull(node.asNode(), bb.getMetaAccess());

                    TypeFlowBuilder<?> arrayCopyBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, ArrayCopyTypeFlow.class, () -> {
                        ArrayCopyTypeFlow arrayCopyFlow = new ArrayCopyTypeFlow(AbstractAnalysisEngine.sourcePosition(node.asNode()), type, srcBuilder.get(), dstBuilder.get());
                        flowsGraph.addMiscEntryFlow(arrayCopyFlow);
                        return arrayCopyFlow;
                    });

                    arrayCopyBuilder.addObserverDependency(srcBuilder);
                    arrayCopyBuilder.addObserverDependency(dstBuilder);

                    /* Array copies must not be removed. */
                    typeFlowGraphBuilder.registerSinkBuilder(arrayCopyBuilder);
                }

            } else if (n instanceof InvokeNode || n instanceof InvokeWithExceptionNode) {
                Invoke invoke = (Invoke) n;
                if (invoke.callTarget() instanceof MethodCallTargetNode target) {
                    var arguments = target.arguments();
                    processMethodInvocation(state, invoke, target.invokeKind(), (PointsToAnalysisMethod) target.targetMethod(), arguments);

                    if (target.invokeKind().hasReceiver()) {
                        processImplicitNonNull(arguments.get(0), invoke.asNode(), state);
                    }
                }

            } else if (n instanceof ObjectClone) {
                ObjectClone node = (ObjectClone) n;
                TypeFlowBuilder<?> inputBuilder = state.lookup(node.getObject());
                AnalysisType inputType = (AnalysisType) StampTool.typeOrNull(node.getObject(), bb.getMetaAccess());

                TypeFlowBuilder<?> cloneBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, CloneTypeFlow.class, () -> {
                    CloneTypeFlow cloneFlow = new CloneTypeFlow(AbstractAnalysisEngine.sourcePosition(node.asNode()), inputType, inputBuilder.get());
                    flowsGraph.addMiscEntryFlow(cloneFlow);
                    return cloneFlow;
                });
                cloneBuilder.addObserverDependency(inputBuilder);
                state.add(node.asFixedNode(), cloneBuilder);
            } else if (n instanceof MacroInvokable node) {
                /*
                 * Macro nodes can either be constant folded during compilation, or lowered back to
                 * invocations if constant folding is not possible. So the static analysis needs to
                 * treat them as possible invocations.
                 *
                 * Note that some macro nodes, like for object cloning, are handled separately
                 * above.
                 */
                processMacroInvokable(state, node, true);
                if (node.getInvokeKind().hasReceiver()) {
                    processImplicitNonNull(node.getArgument(0), node.asNode(), state);
                }
            }
        }

        /*
         * The various Unsafe access nodes either only read, only write, or write-and-read directly
         * based on an offset. All three cases are handled similarly:
         *
         * 1) If we have precise information about the accessed field, we can model the access using
         * proper field access type flows.
         *
         * 2) If the accessed object is always an array, we ca model the access using array type
         * flows. The Unsafe access of an array is essentially an array access because we do not
         * have separate type flows for different array elements.
         *
         * 3) In the generic case, we use the unsafe access type flows.
         */

        private void modelUnsafeReadOnlyFlow(RawLoadNode node, ValueNode object, ValueNode offset) {
            checkUnsafeOffset(object, offset);
            if (object.getStackKind() == JavaKind.Object) {
                if (offset instanceof FieldOffsetProvider fieldOffsetProvider) {
                    processLoadField(node, (AnalysisField) fieldOffsetProvider.getField(), object, state);
                } else if (StampTool.isAlwaysArray(object)) {
                    processLoadIndexed(node, object, state);
                } else {
                    processUnsafeLoad(node, object, state);
                }
            }
        }

        private void modelUnsafeWriteOnlyFlow(ValueNode node, ValueNode object, ValueNode newValue, JavaKind newValueKind, ValueNode offset) {
            checkUnsafeOffset(object, offset);
            if (object.getStackKind() == JavaKind.Object) {
                if (offset instanceof FieldOffsetProvider fieldOffsetProvider) {
                    processStoreField(node, (AnalysisField) fieldOffsetProvider.getField(), object, newValue, newValueKind, state);
                } else if (StampTool.isAlwaysArray(object)) {
                    processStoreIndexed(node, object, newValue, newValueKind, state);
                } else {
                    processUnsafeStore(node, object, newValue, newValueKind, state);
                }
            }
        }

        private void modelUnsafeReadAndWriteFlow(ValueNode node, ValueNode object, ValueNode newValue, JavaKind newValueKind, ValueNode offset) {
            checkUnsafeOffset(object, offset);
            if (object.getStackKind() == JavaKind.Object) {
                if (offset instanceof FieldOffsetProvider fieldOffsetProvider) {
                    var field = (AnalysisField) fieldOffsetProvider.getField();
                    processStoreField(node, field, object, newValue, newValueKind, state);
                    processLoadField(node, field, object, state);
                } else if (StampTool.isAlwaysArray(object)) {
                    processStoreIndexed(node, object, newValue, newValueKind, state);
                    processLoadIndexed(node, object, state);
                } else {
                    processUnsafeStore(node, object, newValue, newValueKind, state);
                    processUnsafeLoad(node, object, state);
                }
            }
        }
    }

    private AnalysisType getNodeType(ValueNode node) {
        return (AnalysisType) node.stamp(NodeView.DEFAULT).javaType(bb.getMetaAccess());
    }

    @SuppressWarnings("unused")
    protected boolean delegateNodeProcessing(FixedNode n, TypeFlowsOfNodes state) {
        // Hook for subclasses to do their own processing.
        return false;
    }

    protected void processMacroInvokable(TypeFlowsOfNodes state, MacroInvokable macro, boolean installResult) {
        ValueNode macroNode = macro.asNode();
        BytecodePosition invokePosition = getInvokePosition(macro, macroNode);
        processMethodInvocation(state, macroNode, macro.getInvokeKind(), (PointsToAnalysisMethod) macro.getTargetMethod(), macro.getArguments(), installResult, invokePosition, false);
    }

    /* Reconstruct the macro node invoke position, avoiding cycles in the parsing backtrace. */
    private BytecodePosition getInvokePosition(MacroInvokable macro, ValueNode macroNode) {
        BytecodePosition invokePosition = null;
        NodeSourcePosition position = macroNode.getNodeSourcePosition();
        if (position != null) {
            /*
             * BytecodeParser.applyInvocationPlugin() gives the macro nodes a position in the target
             * method. We pop it here because the invoke flow needs a position in the caller, i.e.,
             * the currently parsed method.
             */
            assert position.getMethod().equals(macro.getTargetMethod()) : "Unexpected macro node source position: " + macro + " at " + position;
            invokePosition = position.getCaller();
        }
        if (invokePosition == null) {
            invokePosition = AbstractAnalysisEngine.syntheticSourcePosition(macroNode, method);
        }
        return invokePosition;
    }

    protected void processMethodInvocation(TypeFlowsOfNodes state, Invoke invoke, InvokeKind invokeKind, PointsToAnalysisMethod targetMethod, NodeInputList<ValueNode> arguments) {
        FixedNode invokeNode = invoke.asFixedNode();
        BytecodePosition invokePosition = getInvokePosition(invokeNode);
        processMethodInvocation(state, invokeNode, invokeKind, targetMethod, arguments, true, invokePosition, false);
    }

    /* Get a reasonable position for inlined invokes, avoiding cycles in the parsing backtrace. */
    protected BytecodePosition getInvokePosition(FixedNode invokeNode) {
        BytecodePosition invokePosition = invokeNode.getNodeSourcePosition();
        /* Get the outermost caller position for inlined invokes. */
        while (invokePosition != null && invokePosition.getCaller() != null) {
            /*
             * Invokes coming from recursive inlined methods can lead to cycles when reporting the
             * parsing backtrace if we use the reported position directly. For inlined nodes Graal
             * reports the original position (in the original method before inlining) and sets its
             * caller to the inline location. So by using the outermost caller we simply use a
             * version of the graph preprocessed by inline-before-analysis, whose shape may differ
             * from the original source code. In some cases this will lead to stack traces with
             * missing frames, but it is always correct.
             */
            invokePosition = invokePosition.getCaller();
        }

        if (invokePosition == null) {
            /*
             * The invokePosition is used for all sorts of call stack printing (for error messages
             * and diagnostics), so we must have a non-null BytecodePosition.
             */
            invokePosition = AbstractAnalysisEngine.syntheticSourcePosition(invokeNode, method);
        }
        return invokePosition;
    }

    protected void processMethodInvocation(TypeFlowsOfNodes state, ValueNode invoke, InvokeKind invokeKind, PointsToAnalysisMethod targetMethod,
                    NodeInputList<ValueNode> arguments,
                    boolean installResult, BytecodePosition invokeLocation, boolean createDeoptInvokeTypeFlow) {
        // check if the call is allowed
        bb.isCallAllowed(bb, method, targetMethod, invokeLocation);

        /*
         * Collect the parameters builders into an array so that we don't capture the `state`
         * reference in the closure.
         */

        TypeFlowBuilder<?>[] actualParametersBuilders = new TypeFlowBuilder<?>[arguments.size()];
        for (int i = 0; i < actualParametersBuilders.length; i++) {
            ValueNode actualParam = arguments.get(i);
            if (bb.isSupportedJavaKind(actualParam.getStackKind())) {
                TypeFlowBuilder<?> paramBuilder = state.lookup(actualParam);
                actualParametersBuilders[i] = paramBuilder;
                paramBuilder.markAsBuildingAnActualParameter();
                /*
                 * Actual parameters must not be removed. They are linked when the callee is
                 * analyzed, hence, although they might not have any uses, cannot be removed during
                 * parsing.
                 */
                typeFlowGraphBuilder.registerSinkBuilder(paramBuilder);
            }
        }

        TypeFlowBuilder<InvokeTypeFlow> invokeBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), invoke, InvokeTypeFlow.class, () -> {

            TypeFlow<?>[] actualParameters = new TypeFlow<?>[actualParametersBuilders.length];
            for (int i = 0; i < actualParameters.length; i++) {
                actualParameters[i] = actualParametersBuilders[i] != null ? actualParametersBuilders[i].get() : null;
            }

            /*
             * Initially the actual return is null. It will be set by the actual return builder
             * below only when the returned value is actually used, i.e., the actual return builder
             * is materialized.
             */
            ActualReturnTypeFlow actualReturn = null;

            AnalysisType receiverType = null;
            if (invokeKind.hasReceiver()) {
                receiverType = targetMethod.getDeclaringClass();
                AnalysisType receiverArgType = (AnalysisType) StampTool.typeOrNull(arguments.get(0));
                if (receiverArgType != null && receiverType.isAssignableFrom(receiverArgType)) {
                    /*
                     * If the stamp of the receiver argument is a subtype of the declared type, then
                     * adjust the receiver type to be more precise.
                     */
                    receiverType = receiverArgType;
                }
            }

            MultiMethod.MultiMethodKey multiMethodKey = method.getMultiMethodKey();
            InvokeTypeFlow invokeFlow;
            if (createDeoptInvokeTypeFlow) {
                invokeFlow = bb.analysisPolicy().createDeoptInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, multiMethodKey);
            } else {
                switch (invokeKind) {
                    case Static:
                        invokeFlow = bb.analysisPolicy().createStaticInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, multiMethodKey);
                        break;
                    case Special:
                        invokeFlow = bb.analysisPolicy().createSpecialInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, multiMethodKey);
                        break;
                    case Virtual:
                    case Interface:
                        invokeFlow = bb.analysisPolicy().createVirtualInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, multiMethodKey);
                        break;
                    default:
                        throw shouldNotReachHere();
                }
            }

            flowsGraph.addInvoke(invokeFlow);
            flowsGraph.addNodeFlow(invoke, invokeFlow);

            /*
             * Directly add the invoke as an observer of the receiver flow. There's no need to use
             * an observer dependency link between the respective builders because both the invoke
             * and the param builders are registered as sinks. Moreover, the receiver itself may be
             * replaced with a filter by the call to getReceiverType() above.
             */
            if (invokeKind == InvokeKind.Special || invokeKind == InvokeKind.Virtual || invokeKind == InvokeKind.Interface) {
                bb.analysisPolicy().addOriginalObserver(bb, actualParameters[0], invokeFlow);
            }

            return invokeFlow;
        });

        state.addInvoke(invoke, invokeBuilder);

        JavaKind stackKind = invoke.asNode().getStackKind();
        if (!createDeoptInvokeTypeFlow && (bb.isSupportedJavaKind(stackKind) || (state.usePredicates() && stackKind == JavaKind.Void))) {
            /* Create the actual return builder. */
            AnalysisType returnType = targetMethod.getSignature().getReturnType();
            TypeFlowBuilder<?> actualReturnBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), invoke.asNode(), ActualReturnTypeFlow.class, () -> {
                InvokeTypeFlow invokeFlow = invokeBuilder.get();
                ActualReturnTypeFlow actualReturn = new ActualReturnTypeFlow(invokeFlow.source, returnType);
                flowsGraph.addMiscEntryFlow(actualReturn);
                /*
                 * Only set the actual return in the invoke when it is materialized, i.e., it is
                 * used by other flows.
                 */
                invokeFlow.setActualReturn(bb, targetMethod.isStatic(), actualReturn);
                actualReturn.setInvokeFlow(invokeFlow);
                return actualReturn;
            });
            state.setPredicate(actualReturnBuilder);

            if (invoke.stamp(NodeView.DEFAULT) instanceof ObjectStamp stamp) {
                AnalysisType stampType = (AnalysisType) StampTool.typeOrNull(stamp, bb.getMetaAccess());
                if (stamp.nonNull() && !returnType.equals(stampType) && returnType.isAssignableFrom(stampType)) {
                    /*
                     * If the invoke stamp has a more precise type than the return type use that to
                     * filter the returned values. This can happen for example for MacroInvokable
                     * nodes when more concrete stamp information can be inferred for example from
                     * parameter types. In that case the Graal graph optimizations may decide to
                     * remove a checkcast that would normally follow the invoke, so we need to
                     * introduce the filter to avoid loosing precision.
                     */
                    TypeFlowBuilder<?> filterBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), invoke, FilterTypeFlow.class, () -> {
                        FilterTypeFlow filterFlow = new FilterTypeFlow(invokeLocation, stampType, stamp.isExactType(), true, true);
                        flowsGraph.addMiscEntryFlow(filterFlow);
                        return filterFlow;
                    });
                    filterBuilder.addUseDependency(actualReturnBuilder);
                    actualReturnBuilder = filterBuilder;
                    state.setPredicate(actualReturnBuilder);
                }
            }

            typeFlowGraphBuilder.registerSinkBuilder(actualReturnBuilder);
            if (installResult) {
                /*
                 * Some MacroInvokable nodes may have an optimized result, but we still need process
                 * to the invocation.
                 */
                state.add(invoke.asNode(), actualReturnBuilder);
            }
        }

        /* Invokes must not be removed. */
        typeFlowGraphBuilder.registerSinkBuilder(invokeBuilder);
    }

    protected void processCommitAllocation(CommitAllocationNode commitAllocationNode, TypeFlowsOfNodes state) {
        Map<VirtualObjectNode, AllocatedObjectNode> allocatedObjects = new HashMap<>();
        for (AllocatedObjectNode allocatedObjectNode : commitAllocationNode.usages().filter(AllocatedObjectNode.class)) {
            AnalysisType type = (AnalysisType) allocatedObjectNode.getVirtualObject().type();
            processNewInstance(allocatedObjectNode, type, state, false);
            allocatedObjects.put(allocatedObjectNode.getVirtualObject(), allocatedObjectNode);
        }

        List<ValueNode> values = commitAllocationNode.getValues();
        int objectStartIndex = 0;
        for (VirtualObjectNode virtualObject : commitAllocationNode.getVirtualObjects()) {
            AnalysisType type = (AnalysisType) virtualObject.type();
            ValueNode object = allocatedObjects.get(virtualObject);
            if (object == null) {
                /*
                 * The AllocatedObjectNode itself is not used directly, so it got removed from the
                 * graph. We still need to register field/array stores because otherwise we can miss
                 * types that flow into the field/array. We use the VirtualObjectNode as the
                 * placeholder for the stores.
                 */
                object = virtualObject;
            }
            for (int i = 0; i < virtualObject.entryCount(); i++) {
                ValueNode value = values.get(objectStartIndex + i);
                if (!value.isJavaConstant() || !value.asJavaConstant().isDefaultForKind()) {
                    if (type.isArray()) {
                        processStoreIndexed(commitAllocationNode, object, value, value.getStackKind(), state);
                    } else {
                        AnalysisField field = (AnalysisField) ((VirtualInstanceNode) virtualObject).field(i);
                        processStoreField(commitAllocationNode, field, object, value, value.getStackKind(), state);
                    }
                } else {
                    if (!type.isArray()) {
                        AnalysisField field = (AnalysisField) ((VirtualInstanceNode) virtualObject).field(i);
                        field.getInitialFlow().addState(bb, TypeState.defaultValueForKind(bb, field.getStorageKind()));
                    }
                }
            }
            objectStartIndex += virtualObject.entryCount();
        }
        assert values.size() == objectStartIndex : values;
    }

    protected void processNewInstance(NewInstanceNode node, TypeFlowsOfNodes state, boolean insertDefaultFieldValues) {
        /* Instance fields of a new object are initialized to null state in AnalysisField. */
        processNewInstance(node, (AnalysisType) node.instanceClass(), state, insertDefaultFieldValues);
    }

    protected void processNewArray(NewArrayNode node, TypeFlowsOfNodes state) {
        processNewInstance(node, ((AnalysisType) node.elementType()).getArrayClass(), state, false);
    }

    protected void processNewInstance(ValueNode node, AnalysisType type, TypeFlowsOfNodes state, boolean insertDefaultFieldValues) {
        TypeFlowBuilder<?> newInstanceBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, NewInstanceTypeFlow.class, () -> {
            NewInstanceTypeFlow newInstance = new NewInstanceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type, insertDefaultFieldValues);
            flowsGraph.addMiscEntryFlow(newInstance);
            return newInstance;
        });
        state.add(node, newInstanceBuilder);
    }

    protected void processLoadField(ValueNode node, AnalysisField field, ValueNode object, TypeFlowsOfNodes state) {
        field.registerAsRead(AbstractAnalysisEngine.sourcePosition(node));

        if (bb.isSupportedJavaKind(node.getStackKind())) {
            TypeFlowBuilder<?> loadFieldBuilder;
            if (field.isStatic()) {
                loadFieldBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, LoadStaticFieldTypeFlow.class, () -> {
                    FieldTypeFlow fieldFlow = field.getStaticFieldFlow();
                    LoadStaticFieldTypeFlow loadFieldFLow = new LoadStaticFieldTypeFlow(AbstractAnalysisEngine.sourcePosition(node), field, fieldFlow);
                    flowsGraph.addNodeFlow(node, loadFieldFLow);
                    return loadFieldFLow;
                });
            } else {
                TypeFlowBuilder<?> objectBuilder = state.lookup(object);
                loadFieldBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, LoadInstanceFieldTypeFlow.class, () -> {
                    LoadInstanceFieldTypeFlow loadFieldFLow = new LoadInstanceFieldTypeFlow(AbstractAnalysisEngine.sourcePosition(node), field, objectBuilder.get());
                    flowsGraph.addNodeFlow(node, loadFieldFLow);
                    return loadFieldFLow;
                });
                loadFieldBuilder.addObserverDependency(objectBuilder);
            }
            typeFlowGraphBuilder.registerSinkBuilder(loadFieldBuilder);
            state.add(node, loadFieldBuilder);
        }
    }

    protected void processStoreField(ValueNode node, AnalysisField field, ValueNode object, ValueNode newValue, JavaKind newValueKind, TypeFlowsOfNodes state) {
        field.registerAsWritten(AbstractAnalysisEngine.sourcePosition(node));

        if (bb.isSupportedJavaKind(newValueKind)) {
            TypeFlowBuilder<?> valueBuilder = state.lookupOrAny(newValue, newValueKind);

            TypeFlowBuilder<?> storeFieldBuilder;
            if (field.isStatic()) {
                storeFieldBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, StoreStaticFieldTypeFlow.class, () -> {
                    FieldTypeFlow fieldFlow = field.getStaticFieldFlow();
                    StoreStaticFieldTypeFlow storeFieldFlow = new StoreStaticFieldTypeFlow(AbstractAnalysisEngine.sourcePosition(node), field, valueBuilder.get(), fieldFlow);
                    flowsGraph.addMiscEntryFlow(storeFieldFlow);
                    return storeFieldFlow;
                });
            } else {
                TypeFlowBuilder<?> objectBuilder = state.lookup(object);
                storeFieldBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, StoreInstanceFieldTypeFlow.class, () -> {
                    StoreInstanceFieldTypeFlow storeFieldFlow = new StoreInstanceFieldTypeFlow(AbstractAnalysisEngine.sourcePosition(node), field, valueBuilder.get(), objectBuilder.get());
                    flowsGraph.addMiscEntryFlow(storeFieldFlow);
                    return storeFieldFlow;
                });
                storeFieldBuilder.addObserverDependency(objectBuilder);
            }
            storeFieldBuilder.addUseDependency(valueBuilder);
            /* Field stores must not be removed. */
            typeFlowGraphBuilder.registerSinkBuilder(storeFieldBuilder);
        }
    }

    protected void processLoadIndexed(ValueNode node, ValueNode array, TypeFlowsOfNodes state) {
        /* All primitive array loads are always saturated. */
        if (node.getStackKind() == JavaKind.Object) {
            TypeFlowBuilder<?> arrayBuilder = state.lookup(array);
            AnalysisType type = (AnalysisType) StampTool.typeOrNull(array, bb.getMetaAccess());
            AnalysisType arrayType = type.isArray() ? type : bb.getObjectArrayType();

            TypeFlowBuilder<?> loadIndexedBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, LoadIndexedTypeFlow.class, () -> {
                LoadIndexedTypeFlow loadIndexedFlow = new LoadIndexedTypeFlow(AbstractAnalysisEngine.sourcePosition(node), arrayType, arrayBuilder.get());
                flowsGraph.addNodeFlow(node, loadIndexedFlow);
                return loadIndexedFlow;
            });

            typeFlowGraphBuilder.registerSinkBuilder(loadIndexedBuilder);
            loadIndexedBuilder.addObserverDependency(arrayBuilder);
            state.add(node, loadIndexedBuilder);
        }
    }

    protected void processStoreIndexed(ValueNode node, ValueNode array, ValueNode newValue, JavaKind newValueKind, TypeFlowsOfNodes state) {
        /* All primitive array loads are always saturated. */
        if (newValueKind == JavaKind.Object) {
            AnalysisType type = (AnalysisType) StampTool.typeOrNull(array, bb.getMetaAccess());
            AnalysisType arrayType = type.isArray() ? type : bb.getObjectArrayType();
            TypeFlowBuilder<?> arrayBuilder = state.lookup(array);
            TypeFlowBuilder<?> valueBuilder = state.lookupOrAny(newValue, newValueKind);

            TypeFlowBuilder<?> storeIndexedBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, StoreIndexedTypeFlow.class, () -> {
                StoreIndexedTypeFlow storeIndexedFlow = new StoreIndexedTypeFlow(AbstractAnalysisEngine.sourcePosition(node), arrayType, arrayBuilder.get(), valueBuilder.get());
                flowsGraph.addMiscEntryFlow(storeIndexedFlow);
                return storeIndexedFlow;
            });

            storeIndexedBuilder.addUseDependency(valueBuilder);
            storeIndexedBuilder.addObserverDependency(arrayBuilder);
            /* Index stores must not be removed. */
            typeFlowGraphBuilder.registerSinkBuilder(storeIndexedBuilder);
        }
    }

    protected void processUnsafeLoad(ValueNode node, ValueNode object, TypeFlowsOfNodes state) {
        /* All unsafe accessed primitive fields are always saturated. */
        if (node.getStackKind() == JavaKind.Object) {
            TypeFlowBuilder<?> objectBuilder = state.lookup(object);

            TypeFlowBuilder<?> loadBuilder;
            if (bb.analysisPolicy().useConservativeUnsafeAccess()) {
                /*
                 * When unsafe loads are modeled conservatively they start as saturated since the
                 * exact fields that are marked as unsafe accessed are not tracked and cannot be
                 * used as an input to the UnsafeLoadTypeFlow. Using a pre-saturated flow will
                 * signal the saturation to any future uses.
                 */
                loadBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, PreSaturatedTypeFlow.class, () -> {
                    PreSaturatedTypeFlow preSaturated = new PreSaturatedTypeFlow(AbstractAnalysisEngine.sourcePosition(node));
                    flowsGraph.addMiscEntryFlow(preSaturated);
                    return preSaturated;
                });
            } else {
                /*
                 * Use the Object type as a conservative approximation for both the receiver object
                 * type and the loaded values type.
                 */
                loadBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, UnsafeLoadTypeFlow.class, () -> {
                    UnsafeLoadTypeFlow loadTypeFlow = new UnsafeLoadTypeFlow(AbstractAnalysisEngine.sourcePosition(node), bb.getObjectType(), bb.getObjectType(), objectBuilder.get());
                    flowsGraph.addMiscEntryFlow(loadTypeFlow);
                    return loadTypeFlow;
                });
            }

            loadBuilder.addObserverDependency(objectBuilder);
            state.add(node, loadBuilder);
        }
    }

    protected void processUnsafeStore(ValueNode node, ValueNode object, ValueNode newValue, JavaKind newValueKind, TypeFlowsOfNodes state) {
        if (bb.analysisPolicy().useConservativeUnsafeAccess()) {
            /*
             * When unsafe writes are modeled conservatively all unsafe accessed fields contain all
             * instantiated subtypes of their declared type, so no need to model the unsafe store.
             */
            return;
        }
        /* All unsafe accessed primitive fields are always saturated. */
        if (newValueKind == JavaKind.Object) {
            TypeFlowBuilder<?> objectBuilder = state.lookup(object);
            TypeFlowBuilder<?> newValueBuilder = state.lookupOrAny(newValue, newValueKind);

            /*
             * Use the Object type as a conservative approximation for both the receiver object type
             * and the stored values type.
             */
            var storeBuilder = TypeFlowBuilder.create(bb, method, state.getPredicate(), node, UnsafeStoreTypeFlow.class, () -> {
                UnsafeStoreTypeFlow storeTypeFlow = new UnsafeStoreTypeFlow(AbstractAnalysisEngine.sourcePosition(node), bb.getObjectType(), bb.getObjectType(),
                                objectBuilder.get(), newValueBuilder.get());
                flowsGraph.addMiscEntryFlow(storeTypeFlow);
                return storeTypeFlow;
            });

            storeBuilder.addUseDependency(newValueBuilder);
            storeBuilder.addObserverDependency(objectBuilder);
            /* Offset stores must not be removed. */
            typeFlowGraphBuilder.registerSinkBuilder(storeBuilder);
        }
    }

    /** Hook for unsafe offset value checks. */
    protected void checkUnsafeOffset(@SuppressWarnings("unused") ValueNode base, @SuppressWarnings("unused") ValueNode offset) {
    }

    private void processImplicitNonNull(ValueNode node, TypeFlowsOfNodes state) {
        processImplicitNonNull(node, node, state);
    }

    protected void processImplicitNonNull(ValueNode node, ValueNode source, TypeFlowsOfNodes state) {
        assert node.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp : node;
        if (!StampTool.isPointerNonNull(node)) {
            TypeFlowBuilder<?> inputBuilder = state.lookup(node);
            TypeFlowBuilder<?> predicate = state.getPredicate();
            TypeFlowBuilder<?> nullCheckBuilder = TypeFlowBuilder.create(bb, method, predicate, source, NullCheckTypeFlow.class, () -> {
                var inputFlow = inputBuilder.get();
                // only allow if they have the same predicate
                if (inputFlow instanceof NullCheckTypeFlow nullCheck && nullCheck.isBlockingNull() && inputBuilder.getPredicate() == predicate) {
                    // unnecessary to create redundant null type check
                    return nullCheck;
                }
                NullCheckTypeFlow nullCheckFlow = new NullCheckTypeFlow(AbstractAnalysisEngine.sourcePosition(source), inputFlow.getDeclaredType(), true);
                flowsGraph.addMiscEntryFlow(nullCheckFlow);
                return nullCheckFlow;
            });
            nullCheckBuilder.addUseDependency(inputBuilder);
            state.update(node, nullCheckBuilder);
        }
    }
}
