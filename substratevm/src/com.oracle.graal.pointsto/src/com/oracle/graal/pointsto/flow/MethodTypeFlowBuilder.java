/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.common.JVMCIError.guarantee;
import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jdk.compiler.graal.core.common.spi.ForeignCallDescriptor;
import jdk.compiler.graal.core.common.spi.ForeignCallsProvider;
import jdk.compiler.graal.core.common.type.ObjectStamp;
import jdk.compiler.graal.core.common.type.TypeReference;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.Node.NodeIntrinsic;
import jdk.compiler.graal.graph.NodeBitMap;
import jdk.compiler.graal.graph.NodeInputList;
import jdk.compiler.graal.graph.NodeSourcePosition;
import jdk.compiler.graal.nodes.AbstractEndNode;
import jdk.compiler.graal.nodes.AbstractMergeNode;
import jdk.compiler.graal.nodes.BeginNode;
import jdk.compiler.graal.nodes.CallTargetNode.InvokeKind;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.EndNode;
import jdk.compiler.graal.nodes.FixedGuardNode;
import jdk.compiler.graal.nodes.FixedNode;
import jdk.compiler.graal.nodes.FrameState;
import jdk.compiler.graal.nodes.GraphEncoder;
import jdk.compiler.graal.nodes.IfNode;
import jdk.compiler.graal.nodes.Invoke;
import jdk.compiler.graal.nodes.InvokeNode;
import jdk.compiler.graal.nodes.InvokeWithExceptionNode;
import jdk.compiler.graal.nodes.LogicNode;
import jdk.compiler.graal.nodes.LoopBeginNode;
import jdk.compiler.graal.nodes.LoopEndNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ParameterNode;
import jdk.compiler.graal.nodes.PhiNode;
import jdk.compiler.graal.nodes.ReturnNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.IsNullNode;
import jdk.compiler.graal.nodes.extended.BoxNode;
import jdk.compiler.graal.nodes.extended.BytecodeExceptionNode;
import jdk.compiler.graal.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import jdk.compiler.graal.nodes.extended.ForeignCall;
import jdk.compiler.graal.nodes.extended.GetClassNode;
import jdk.compiler.graal.nodes.extended.RawLoadNode;
import jdk.compiler.graal.nodes.extended.RawStoreNode;
import jdk.compiler.graal.nodes.java.AtomicReadAndWriteNode;
import jdk.compiler.graal.nodes.java.ClassIsAssignableFromNode;
import jdk.compiler.graal.nodes.java.DynamicNewArrayNode;
import jdk.compiler.graal.nodes.java.DynamicNewInstanceNode;
import jdk.compiler.graal.nodes.java.ExceptionObjectNode;
import jdk.compiler.graal.nodes.java.InstanceOfNode;
import jdk.compiler.graal.nodes.java.LoadFieldNode;
import jdk.compiler.graal.nodes.java.LoadIndexedNode;
import jdk.compiler.graal.nodes.java.MethodCallTargetNode;
import jdk.compiler.graal.nodes.java.MonitorEnterNode;
import jdk.compiler.graal.nodes.java.NewArrayNode;
import jdk.compiler.graal.nodes.java.NewInstanceNode;
import jdk.compiler.graal.nodes.java.NewMultiArrayNode;
import jdk.compiler.graal.nodes.java.StoreFieldNode;
import jdk.compiler.graal.nodes.java.StoreIndexedNode;
import jdk.compiler.graal.nodes.java.UnsafeCompareAndExchangeNode;
import jdk.compiler.graal.nodes.java.UnsafeCompareAndSwapNode;
import jdk.compiler.graal.nodes.spi.LimitedValueProxy;
import jdk.compiler.graal.nodes.spi.ValueProxy;
import jdk.compiler.graal.nodes.type.StampTool;
import jdk.compiler.graal.nodes.virtual.AllocatedObjectNode;
import jdk.compiler.graal.nodes.virtual.CommitAllocationNode;
import jdk.compiler.graal.nodes.virtual.VirtualInstanceNode;
import jdk.compiler.graal.nodes.virtual.VirtualObjectNode;
import jdk.compiler.graal.phases.common.BoxNodeIdentityPhase;
import jdk.compiler.graal.phases.common.CanonicalizerPhase;
import jdk.compiler.graal.phases.common.IterativeConditionalEliminationPhase;
import jdk.compiler.graal.phases.graph.MergeableState;
import jdk.compiler.graal.phases.graph.PostOrderNodeIterator;
import jdk.compiler.graal.replacements.nodes.BasicArrayCopyNode;
import jdk.compiler.graal.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.compiler.graal.replacements.nodes.MacroInvokable;
import jdk.compiler.graal.replacements.nodes.ObjectClone;
import jdk.compiler.graal.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.compiler.graal.virtual.phases.ea.PartialEscapePhase;
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
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.UnsafePartitionLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.StoreIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.UnsafePartitionStoreTypeFlow;
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
import com.oracle.graal.pointsto.nodes.UnsafePartitionLoadNode;
import com.oracle.graal.pointsto.nodes.UnsafePartitionStoreNode;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysis;
import com.oracle.graal.pointsto.results.StaticAnalysisResultsBuilder;
import com.oracle.graal.pointsto.results.StrengthenGraphs;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.common.meta.MultiMethod;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

public class MethodTypeFlowBuilder {

    protected final PointsToAnalysis bb;
    protected final MethodFlowsGraph flowsGraph;
    protected final PointsToAnalysisMethod method;
    protected StructuredGraph graph;
    private NodeBitMap processedNodes;
    private Map<PhiNode, TypeFlowBuilder<?>> loopPhiFlows;
    private final MethodFlowsGraph.GraphKind graphKind;
    private boolean processed = false;
    private final boolean newFlowsGraph;

    protected final TypeFlowGraphBuilder typeFlowGraphBuilder;
    protected List<TypeFlow<?>> postInitFlows = List.of();

    public MethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod method, MethodFlowsGraph flowsGraph, GraphKind graphKind) {
        this.bb = bb;
        this.method = method;
        this.graphKind = graphKind;
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
            if (!bb.strengthenGraalGraphs()) {
                /*
                 * Register used types and fields before canonicalization can optimize them. When
                 * parsing graphs again for compilation, we need to have all types, methods, fields
                 * of the original graph registered properly.
                 */
                registerUsedElements(bb, graph, false);
            }
            CanonicalizerPhase canonicalizerPhase = CanonicalizerPhase.create();
            canonicalizerPhase.apply(graph, bb.getProviders(method));
            if (bb.strengthenGraalGraphs()) {
                /*
                 * Removing unnecessary conditions before the static analysis runs reduces the size
                 * of the type flow graph. For example, this removes redundant null checks: the
                 * bytecode parser emits explicit null checks before e.g., all method calls, field
                 * access, array accesses; many of those dominate each other.
                 */
                if (PointstoOptions.ConditionalEliminationBeforeAnalysis.getValue(bb.getOptions())) {
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

            if (!bb.getUniverse().hostVM().validateGraph(bb, graph)) {
                graph = null;
                return false;
            }

            // Do it again after canonicalization changed type checks and field accesses.
            registerUsedElements(bb, graph, true);

            return true;
        } catch (Throwable ex) {
            throw graph.getDebug().handle(ex);
        }
    }

    protected static void registerUsedElements(PointsToAnalysis bb, StructuredGraph graph, boolean registerEmbeddedRoots) {
        PointsToAnalysisMethod method = (PointsToAnalysisMethod) graph.method();
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
                type.registerAsAllocated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof VirtualObjectNode) {
                VirtualObjectNode node = (VirtualObjectNode) n;
                AnalysisType type = (AnalysisType) node.type();
                type.registerAsAllocated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof CommitAllocationNode) {
                CommitAllocationNode node = (CommitAllocationNode) n;
                List<ValueNode> values = node.getValues();
                int objectStartIndex = 0;
                for (VirtualObjectNode virtualObject : node.getVirtualObjects()) {
                    AnalysisType type = (AnalysisType) virtualObject.type();
                    if (!type.isArray()) {
                        for (int i = 0; i < virtualObject.entryCount(); i++) {
                            ValueNode value = values.get(objectStartIndex + i);
                            if (!value.isJavaConstant() || !value.asJavaConstant().isDefaultForKind()) {
                                AnalysisField field = (AnalysisField) ((VirtualInstanceNode) virtualObject).field(i);
                                field.registerAsWritten(AbstractAnalysisEngine.sourcePosition(node));
                            }
                        }
                    }
                    objectStartIndex += virtualObject.entryCount();
                }

            } else if (n instanceof NewArrayNode) {
                NewArrayNode node = (NewArrayNode) n;
                AnalysisType type = ((AnalysisType) node.elementType()).getArrayClass();
                type.registerAsAllocated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof NewMultiArrayNode) {
                NewMultiArrayNode node = (NewMultiArrayNode) n;
                AnalysisType type = ((AnalysisType) node.type());
                for (int i = 0; i < node.dimensionCount(); i++) {
                    type.registerAsAllocated(AbstractAnalysisEngine.sourcePosition(node));
                    type = type.getComponentType();
                }

            } else if (n instanceof BoxNode) {
                BoxNode node = (BoxNode) n;
                AnalysisType type = (AnalysisType) StampTool.typeOrNull(node, bb.getMetaAccess());
                type.registerAsAllocated(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof LoadFieldNode) {
                LoadFieldNode node = (LoadFieldNode) n;
                AnalysisField field = (AnalysisField) node.field();
                field.registerAsRead(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof StoreFieldNode) {
                StoreFieldNode node = (StoreFieldNode) n;
                AnalysisField field = (AnalysisField) node.field();
                field.registerAsWritten(AbstractAnalysisEngine.sourcePosition(node));

            } else if (n instanceof ConstantNode) {
                ConstantNode cn = (ConstantNode) n;
                JavaConstant root = cn.asJavaConstant();
                if (cn.hasUsages() && cn.isJavaConstant() && root.getJavaKind() == JavaKind.Object && root.isNonNull()) {
                    assert StampTool.isExactType(cn) : cn;
                    AnalysisType type = (AnalysisType) StampTool.typeOrNull(cn, bb.getMetaAccess());
                    type.registerAsInHeap(new EmbeddedRootScan(AbstractAnalysisEngine.sourcePosition(cn), root));
                    if (registerEmbeddedRoots && !ignoreConstant(bb, cn)) {
                        registerEmbeddedRoot(bb, cn);
                    }
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
     * {@link ClassIsAssignableFromNode} to false.
     *
     * Similarly, a class should not be marked as reachable only so that we can add the class name
     * to the error message of a {@link ClassCastException}. In {@link StrengthenGraphs} we can
     * re-write the Class constant to a String constant, i.e., only embed the class name and not the
     * full java.lang.Class object in the image.
     */
    protected static boolean ignoreConstant(PointsToAnalysis bb, ConstantNode cn) {
        if (!ignoreInstanceOfType(bb, (AnalysisType) bb.getConstantReflectionProvider().asJavaType(cn.asConstant()))) {
            return false;
        }
        for (var usage : cn.usages()) {
            if (usage instanceof ClassIsAssignableFromNode) {
                if (((ClassIsAssignableFromNode) usage).getThisClass() != cn) {
                    return false;
                }
            } else if (usage instanceof BytecodeExceptionNode) {
                if (((BytecodeExceptionNode) usage).getExceptionKind() != BytecodeExceptionKind.CLASS_CAST) {
                    return false;
                }
            } else if (usage instanceof FrameState) {
                /* FrameState usages are only for debugging and not necessary for correctness. */
            } else {
                return false;
            }
        }
        /* Success, the ConstantNode do not need to be seen as reachable. */
        return true;
    }

    protected static boolean ignoreInstanceOfType(PointsToAnalysis bb, AnalysisType type) {
        if (bb.getHostVM().ignoreInstanceOfTypeDisallowed()) {
            return false;
        }
        if (type == null || !bb.strengthenGraalGraphs()) {
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

    private static void registerEmbeddedRoot(PointsToAnalysis bb, ConstantNode cn) {
        bb.getUniverse().registerEmbeddedRoot(cn.asJavaConstant(), AbstractAnalysisEngine.sourcePosition(cn));
    }

    private static void registerForeignCall(PointsToAnalysis bb, ForeignCallsProvider foreignCallsProvider, ForeignCallDescriptor foreignCallDescriptor, ResolvedJavaMethod from) {
        Optional<AnalysisMethod> targetMethod = bb.getHostVM().handleForeignCall(foreignCallDescriptor, foreignCallsProvider);
        targetMethod.ifPresent(analysisMethod -> bb.addRootMethod(analysisMethod, true, from));
    }

    private boolean handleNodeIntrinsic() {
        if (AnnotationAccess.isAnnotationPresent(method, NodeIntrinsic.class)) {
            graph.getDebug().log("apply MethodTypeFlow on node intrinsic %s", method);
            AnalysisType returnType = (AnalysisType) method.getSignature().getReturnType(method.getDeclaringClass());
            if (returnType.getJavaKind() == JavaKind.Object) {
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
                bb.analysisPolicy().addOriginalUse(bb, returnTypeFlow, resultFlow);
                flowsGraph.addMiscEntryFlow(returnTypeFlow);
                flowsGraph.setReturnFlow(resultFlow);
            }
            return true;
        }

        return false;
    }

    private void insertAllInstantiatedTypesReturn() {
        AnalysisError.guarantee(flowsGraph.getReturnFlow() == null, "Expected null return flow");

        AnalysisType returnType = TypeFlow.filterUncheckedInterface((AnalysisType) method.getSignature().getReturnType(method.getDeclaringClass()));
        AnalysisError.guarantee(returnType.getJavaKind().isObject(), "Unexpected return type: %s", returnType);

        BytecodePosition position = AbstractAnalysisEngine.syntheticSourcePosition(null, method);
        var returnFlow = new FormalReturnTypeFlow(position, returnType);
        flowsGraph.setReturnFlow(returnFlow);

        assert returnType.equals(returnFlow.getDeclaredType()) : returnType + " != " + returnFlow.getDeclaredType();
        returnType.getTypeFlow(bb, true).addUse(bb, returnFlow);
    }

    /**
     * Placeholder flows are placed in the graph for any missing flows.
     */
    private void insertPlaceholderParamAndReturnFlows() {
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        JavaType[] paramTypes = method.getSignature().toParameterTypes(isStatic ? null : method.getDeclaringClass());
        BytecodePosition position = AbstractAnalysisEngine.syntheticSourcePosition(null, method);
        for (int index = 0; index < paramTypes.length; index++) {
            if (flowsGraph.getParameter(index) == null) {
                if (paramTypes[index].getJavaKind().isObject()) {
                    AnalysisType paramType = (AnalysisType) paramTypes[index];
                    FormalParamTypeFlow parameter;
                    if (!isStatic && index == 0) {
                        assert paramType.equals(method.getDeclaringClass()) : paramType + ", " + method;
                        parameter = new FormalReceiverTypeFlow(position, paramType);
                    } else {
                        parameter = new FormalParamTypeFlow(position, paramType, index);
                    }
                    flowsGraph.setParameter(index, parameter);
                }
            }
        }

        if (flowsGraph.getReturnFlow() == null) {
            AnalysisType returnType = (AnalysisType) method.getSignature().getReturnType(method.getDeclaringClass());
            if (returnType.getJavaKind().isObject()) {
                flowsGraph.setReturnFlow(new FormalReturnTypeFlow(position, returnType));
            }
        }

    }

    private void createTypeFlow() {
        processedNodes = new NodeBitMap(graph);

        TypeFlowsOfNodes typeFlows = new TypeFlowsOfNodes();
        for (Node n : graph.getNodes()) {
            if (n instanceof ParameterNode) {
                ParameterNode node = (ParameterNode) n;
                if (node.getStackKind() == JavaKind.Object) {
                    TypeFlowBuilder<?> paramBuilder = TypeFlowBuilder.create(bb, node, FormalParamTypeFlow.class, () -> {
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
                                AnalysisType paramType = (AnalysisType) method.getSignature().getParameterType(index - offset, method.getDeclaringClass());
                                parameter = new FormalParamTypeFlow(AbstractAnalysisEngine.sourcePosition(node), paramType, index);
                            }
                            flowsGraph.setParameter(index, parameter);
                        }
                        return parameter;
                    });
                    typeFlowGraphBuilder.checkFormalParameterBuilder(paramBuilder);
                    typeFlows.add(node, paramBuilder);
                    if (bb.strengthenGraalGraphs()) {
                        typeFlowGraphBuilder.registerSinkBuilder(paramBuilder);
                    }
                }
            } else if (n instanceof BoxNode) {
                BoxNode node = (BoxNode) n;
                AnalysisType type = (AnalysisType) StampTool.typeOrNull(node, bb.getMetaAccess());

                TypeFlowBuilder<?> boxBuilder = TypeFlowBuilder.create(bb, node, BoxTypeFlow.class, () -> {
                    BoxTypeFlow boxFlow = new BoxTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type);
                    flowsGraph.addMiscEntryFlow(boxFlow);
                    return boxFlow;
                });
                typeFlows.add(node, boxBuilder);
            }

            for (Node input : n.inputs()) {
                /*
                 * TODO change the handling of constants so that the SourceTypeFlow is created on
                 * demand, with the optimization that only one SourceTypeFlow is created ever for
                 * every distinct object (using, e.g., caching in a global IdentityHashMap).
                 */
                if (input instanceof ConstantNode && !typeFlows.contains((ConstantNode) input)) {
                    ConstantNode node = (ConstantNode) input;
                    Constant constant = node.getValue();
                    if (node.asJavaConstant() == null && constant instanceof VMConstant) {
                        // do nothing
                    } else if (node.asJavaConstant().isNull()) {
                        TypeFlowBuilder<ConstantTypeFlow> sourceBuilder = TypeFlowBuilder.create(bb, node, ConstantTypeFlow.class, () -> {
                            ConstantTypeFlow constantSource = new ConstantTypeFlow(AbstractAnalysisEngine.sourcePosition(node), null, TypeState.forNull());
                            flowsGraph.addMiscEntryFlow(constantSource);
                            return constantSource;
                        });
                        typeFlows.add(node, sourceBuilder);
                    } else if (node.asJavaConstant().getJavaKind() == JavaKind.Object) {
                        /*
                         * TODO a SubstrateObjectConstant wrapping a PrimitiveConstant has kind
                         * equals to Object. Do we care about the effective value of these primitive
                         * constants in the analysis?
                         */
                        assert StampTool.isExactType(node) : node;
                        AnalysisType type = (AnalysisType) StampTool.typeOrNull(node, bb.getMetaAccess());
                        assert type.isInstantiated() : type;
                        TypeFlowBuilder<ConstantTypeFlow> sourceBuilder = TypeFlowBuilder.create(bb, node, ConstantTypeFlow.class, () -> {
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

        // assert method.getAnnotation(Fold.class) == null : method;
        if (handleNodeIntrinsic()) {
            assert !method.getReturnsAllInstantiatedTypes() : method;
            return;
        }

        if (method.getReturnsAllInstantiatedTypes()) {
            insertAllInstantiatedTypesReturn();
        }

        boolean insertPlaceholderFlows = bb.getHostVM().getMultiMethodAnalysisPolicy().insertPlaceholderParamAndReturnFlows(method.getMultiMethodKey());
        if (graphKind == MethodFlowsGraph.GraphKind.STUB) {
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

        /*
         * When we intend to strengthen Graal graphs, then the graph needs to be preserved. Type
         * flow nodes references Graal IR nodes directly as their source position.
         *
         * When we create separate StaticAnalysisResults objects, then Graal graphs are not needed
         * after static analysis.
         */
        if (bb.strengthenGraalGraphs()) {
            method.setAnalyzedGraph(GraphEncoder.encodeSingleGraph(graph, AnalysisParsedGraph.HOST_ARCHITECTURE, flowsGraph.getNodeFlows().getKeys()));
        }
    }

    /**
     * Fixed point analysis state. It stores the type flows for all nodes of the method's graph.
     */
    protected class TypeFlowsOfNodes extends MergeableState<TypeFlowsOfNodes> implements Cloneable {

        private final Map<Node, TypeFlowBuilder<?>> flows;

        TypeFlowsOfNodes() {
            this.flows = new HashMap<>();
        }

        protected TypeFlowsOfNodes(TypeFlowsOfNodes copyFrom) {
            this.flows = new HashMap<>(copyFrom.flows);
        }

        public boolean contains(ValueNode node) {
            return flows.containsKey(typeFlowUnproxify(node));
        }

        public TypeFlowBuilder<?> lookup(ValueNode n) {
            assert n.stamp(NodeView.DEFAULT) instanceof ObjectStamp : n;

            ValueNode node = typeFlowUnproxify(n);
            TypeFlowBuilder<?> result = flows.get(node);
            if (result == null) {
                /*
                 * There is no type flow set, yet. Therefore we have no info for the node.
                 */
                ObjectStamp stamp = (ObjectStamp) n.stamp(NodeView.DEFAULT);
                if (stamp.isEmpty()) {
                    throw AnalysisError.shouldNotReachHere("Stamp for node " + n + " is empty.");
                }
                AnalysisType stampType = (AnalysisType) StampTool.typeOrNull(stamp, bb.getMetaAccess());
                if (stamp.isExactType()) {
                    /*
                     * We are lucky: the stamp tells us which type the node has.
                     */
                    result = TypeFlowBuilder.create(bb, node, SourceTypeFlow.class, () -> {
                        SourceTypeFlow src = new SourceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), stampType, !stamp.nonNull());
                        flowsGraph.addMiscEntryFlow(src);
                        return src;
                    });

                } else {
                    /*
                     * Use a type state which consists of all allocated types (which are compatible
                     * to the node's type). This is a conservative assumption.
                     */
                    result = TypeFlowBuilder.create(bb, node, TypeFlow.class, () -> {
                        TypeFlow<?> proxy = bb.analysisPolicy().proxy(AbstractAnalysisEngine.sourcePosition(node), stampType.getTypeFlow(bb, true));
                        flowsGraph.addMiscEntryFlow(proxy);
                        return proxy;
                    });
                }

                flows.put(node, result);
            }
            return result;
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
                            newFlow = TypeFlowBuilder.create(bb, merge, MergeTypeFlow.class, () -> {
                                MergeTypeFlow newMergeFlow = new MergeTypeFlow(AbstractAnalysisEngine.sourcePosition(merge));
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
        public TypeFlowsOfNodes clone() {
            return new TypeFlowsOfNodes(this);
        }
    }

    /**
     * Fixed point analysis iterator. It propagates the type flows through the nodes of the method's
     * graph.
     */
    class NodeIterator extends PostOrderNodeIterator<TypeFlowsOfNodes> {

        private final HashMap<Object, TypeFlowBuilder<?>> instanceOfFlows;
        private TypeFlowBuilder<?> returnBuilder;

        NodeIterator(FixedNode start, TypeFlowsOfNodes typeFlows) {
            super(start, typeFlows);
            instanceOfFlows = new HashMap<>();
            returnBuilder = null;
        }

        /**
         * A method can have multiple return flows. Since we create the return flow lazily we want
         * to make sure it is created only once. The source for the return flow, used for debugging
         * only, will be the first parsed ReturnNode.
         */
        private TypeFlowBuilder<?> uniqueReturnFlowBuilder(ReturnNode node) {
            if (returnBuilder == null) {
                AnalysisType returnType = (AnalysisType) method.getSignature().getReturnType(method.getDeclaringClass());
                if (returnType.getJavaKind() == JavaKind.Object) {
                    returnBuilder = TypeFlowBuilder.create(bb, node, FormalReturnTypeFlow.class, () -> {
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

        private TypeFlowBuilder<?> uniqueInstanceOfFlow(InstanceOfNode node, AnalysisType declaredType) {
            /*
             * This happens during method parsing, which is single threaded, so there is no need for
             * synchronization.
             */
            Object key = StaticAnalysisResultsBuilder.uniqueKey(node);
            return instanceOfFlows.computeIfAbsent(key, (bciKey) -> {
                TypeFlowBuilder<?> instanceOfBuilder = TypeFlowBuilder.create(bb, node, InstanceOfTypeFlow.class, () -> {
                    InstanceOfTypeFlow instanceOf = new InstanceOfTypeFlow(AbstractAnalysisEngine.sourcePosition(node), declaredType);
                    flowsGraph.addInstanceOf(key, instanceOf);
                    return instanceOf;
                });
                /* InstanceOf must not be removed as it is reported by the analysis results. */
                typeFlowGraphBuilder.registerSinkBuilder(instanceOfBuilder);
                return instanceOfBuilder;
            });
        }

        private void handleCondition(ValueNode source, LogicNode condition, boolean isTrue) {
            if (condition instanceof IsNullNode) {
                IsNullNode nullCheck = (IsNullNode) condition;
                ValueNode object = nullCheck.getValue();
                TypeFlowBuilder<?> inputBuilder = state.lookup(object);
                TypeFlowBuilder<?> nullCheckBuilder = TypeFlowBuilder.create(bb, source, NullCheckTypeFlow.class, () -> {
                    NullCheckTypeFlow nullCheckFlow = new NullCheckTypeFlow(AbstractAnalysisEngine.sourcePosition(source), inputBuilder.get().getDeclaredType(), !isTrue);
                    flowsGraph.addNodeFlow(bb, source, nullCheckFlow);
                    return nullCheckFlow;
                });
                nullCheckBuilder.addUseDependency(inputBuilder);
                if (bb.strengthenGraalGraphs()) {
                    typeFlowGraphBuilder.registerSinkBuilder(nullCheckBuilder);
                }
                state.update(object, nullCheckBuilder);

            } else if (condition instanceof InstanceOfNode) {
                InstanceOfNode instanceOf = (InstanceOfNode) condition;
                ValueNode object = instanceOf.getValue();
                TypeReference typeReference = instanceOf.type();
                AnalysisType type = (AnalysisType) instanceOf.type().getType();

                /*
                 * It is possible that the instanceof is processed multiple times, because the same
                 * InstanceOfNode can be used by multiple conditions and is processed once for each
                 * branch of an if statement, so we have to make sure that its associated type flow
                 * is unique.
                 */
                TypeFlowBuilder<?> objectBuilder = state.lookup(object);
                BytecodePosition instanceOfPosition = AbstractAnalysisEngine.sourcePosition(instanceOf);
                if (!bb.strengthenGraalGraphs() && instanceOfPosition.getBCI() >= 0) {
                    /*
                     * An InstanceOf with negative BCI is not useful. This can happen for example
                     * for instanceof bytecodes for exception unwind. However, the filtering below
                     * is still useful for other further operations in the exception handler.
                     *
                     * When strengthenGraalGraphs is true, then there is never a need for an
                     * InstanceOfTypeFlow. The information is taken from the FilterTypeFlow instead,
                     * i.e., when the filtered type flow of either the true or false successor is
                     * empty, then that branch is unreachable and the instanceOf will be removed.
                     */
                    TypeFlowBuilder<?> instanceOfBuilder = uniqueInstanceOfFlow(instanceOf, type);
                    instanceOfBuilder.addUseDependency(objectBuilder);
                }

                /*
                 * Note that we create the filter flow with the original objectFlow as the input and
                 * not with the instanceOfFlow. When the same InstanceOfNode is used by multiple
                 * conditions, the type state of instanceOfFlow is less precise than the type state
                 * of objectFlow (which is context sensitive for exactly our condition).
                 */
                TypeFlowBuilder<?> filterBuilder = TypeFlowBuilder.create(bb, source, FilterTypeFlow.class, () -> {
                    FilterTypeFlow filterFlow = new FilterTypeFlow(AbstractAnalysisEngine.sourcePosition(source), type, typeReference.isExact(), isTrue, !isTrue ^ instanceOf.allowsNull());
                    flowsGraph.addNodeFlow(bb, source, filterFlow);
                    return filterFlow;
                });
                filterBuilder.addUseDependency(objectBuilder);
                if (bb.strengthenGraalGraphs()) {
                    typeFlowGraphBuilder.registerSinkBuilder(filterBuilder);
                }
                state.update(object, filterBuilder);
            }
        }

        @Override
        protected void node(FixedNode n) {
            processedNodes.mark(n);

            // Note: the state is the typeFlows which was passed to the constructor.
            if (delegateNodeProcessing(n, state)) {
                // processed by subclass
                return;
            } else if (n instanceof LoopEndNode) {
                LoopEndNode end = (LoopEndNode) n;
                LoopBeginNode merge = end.loopBegin();
                int predIdx = merge.phiPredecessorIndex(end);
                for (PhiNode phi : merge.phis()) {
                    if (phi.getStackKind() == JavaKind.Object) {
                        loopPhiFlows.get(phi).addUseDependency(state.lookup(phi.valueAt(predIdx)));
                    }
                }

            } else if (n instanceof LoopBeginNode) {
                LoopBeginNode merge = (LoopBeginNode) n;
                for (PhiNode phi : merge.phis()) {
                    if (phi.getStackKind() == JavaKind.Object) {
                        TypeFlowBuilder<MergeTypeFlow> newFlowBuilder = TypeFlowBuilder.create(bb, merge, MergeTypeFlow.class, () -> {
                            MergeTypeFlow newFlow = new MergeTypeFlow(AbstractAnalysisEngine.sourcePosition(merge));
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

            } else if (n instanceof EndNode) {
                EndNode end = (EndNode) n;
                AbstractMergeNode merge = end.merge();
                int predIdx = merge.phiPredecessorIndex(end);
                for (PhiNode phi : merge.phis()) {
                    if (phi.getStackKind() == JavaKind.Object) {
                        state.add(phi, state.lookup(phi.valueAt(predIdx)));
                    }
                }

            } else if (n instanceof ExceptionObjectNode) {
                ExceptionObjectNode node = (ExceptionObjectNode) n;
                TypeFlowBuilder<?> exceptionObjectBuilder = TypeFlowBuilder.create(bb, node, TypeFlow.class, () -> {
                    TypeFlow<?> input = ((AnalysisType) StampTool.typeOrNull(node, bb.getMetaAccess())).getTypeFlow(bb, false);
                    TypeFlow<?> exceptionObjectFlow = bb.analysisPolicy().proxy(AbstractAnalysisEngine.sourcePosition(node), input);
                    flowsGraph.addMiscEntryFlow(exceptionObjectFlow);
                    return exceptionObjectFlow;
                });
                state.add(node, exceptionObjectBuilder);

            } else if (n instanceof BeginNode) {
                BeginNode node = (BeginNode) n;
                Node pred = node.predecessor();

                if (pred instanceof IfNode) {
                    IfNode ifNode = (IfNode) pred;
                    handleCondition(node, ifNode.condition(), node == ifNode.trueSuccessor());
                }

            } else if (n instanceof FixedGuardNode) {
                FixedGuardNode node = (FixedGuardNode) n;
                handleCondition(node, node.condition(), !node.isNegated());

            } else if (n instanceof ReturnNode) {
                /*
                 * Return type flows within the graph do not need to be linked when all instantiated
                 * types are returned.
                 */
                if (!method.getReturnsAllInstantiatedTypes()) {
                    ReturnNode node = (ReturnNode) n;
                    if (node.result() != null && node.result().getStackKind() == JavaKind.Object) {
                        TypeFlowBuilder<?> returnFlowBuilder = uniqueReturnFlowBuilder(node);
                        returnFlowBuilder.addUseDependency(state.lookup(node.result()));
                    }
                }
            } else if (n instanceof CommitAllocationNode) {
                processCommitAllocation((CommitAllocationNode) n, state);
            } else if (n instanceof NewInstanceNode) {
                processNewInstance((NewInstanceNode) n, state);
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
                    instanceTypeBuilder = TypeFlowBuilder.create(bb, instanceType, AllInstantiatedTypeFlow.class, () -> {
                        return instanceType.getTypeFlow(bb, false);
                    });
                }
                TypeFlowBuilder<DynamicNewInstanceTypeFlow> dynamicNewInstanceBuilder = TypeFlowBuilder.create(bb, node, DynamicNewInstanceTypeFlow.class, () -> {
                    DynamicNewInstanceTypeFlow newInstanceTypeFlow = new DynamicNewInstanceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), instanceTypeBuilder.get(), instanceType);
                    flowsGraph.addMiscEntryFlow(newInstanceTypeFlow);
                    return newInstanceTypeFlow;
                });

                if (instanceTypeNode instanceof GetClassNode) {
                    dynamicNewInstanceBuilder.addObserverDependency(instanceTypeBuilder);
                }

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

                TypeFlowBuilder<DynamicNewInstanceTypeFlow> dynamicNewArrayBuilder = TypeFlowBuilder.create(bb, node, DynamicNewInstanceTypeFlow.class, () -> {
                    DynamicNewInstanceTypeFlow newArrayTypeFlow = new DynamicNewInstanceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), arrayType.getTypeFlow(bb, false), arrayType);
                    flowsGraph.addMiscEntryFlow(newArrayTypeFlow);
                    return newArrayTypeFlow;
                });
                state.add(node, dynamicNewArrayBuilder);

            } else if (n instanceof NewMultiArrayNode) {
                NewMultiArrayNode node = (NewMultiArrayNode) n;
                AnalysisType type = ((AnalysisType) node.type());
                assert type.isInstantiated() : type;

                TypeFlowBuilder<NewInstanceTypeFlow> newArrayBuilder = TypeFlowBuilder.create(bb, node, NewInstanceTypeFlow.class, () -> {
                    NewInstanceTypeFlow newArray = new NewInstanceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type);
                    flowsGraph.addMiscEntryFlow(newArray);
                    return newArray;
                });

                state.add(node, newArrayBuilder);

            } else if (n instanceof LoadFieldNode) { // value = object.field
                LoadFieldNode node = (LoadFieldNode) n;
                AnalysisField field = (AnalysisField) node.field();
                assert field.isAccessed() : field;
                if (node.getStackKind() == JavaKind.Object) {
                    TypeFlowBuilder<? extends LoadFieldTypeFlow> loadFieldBuilder;
                    BytecodePosition loadLocation = AbstractAnalysisEngine.sourcePosition(node);
                    if (node.isStatic()) {
                        loadFieldBuilder = TypeFlowBuilder.create(bb, node, LoadStaticFieldTypeFlow.class, () -> {
                            FieldTypeFlow fieldFlow = field.getStaticFieldFlow();
                            LoadStaticFieldTypeFlow loadFieldFLow = new LoadStaticFieldTypeFlow(loadLocation, field, fieldFlow);
                            flowsGraph.addNodeFlow(bb, node, loadFieldFLow);
                            return loadFieldFLow;
                        });
                    } else {
                        TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                        loadFieldBuilder = TypeFlowBuilder.create(bb, node, LoadInstanceFieldTypeFlow.class, () -> {
                            LoadInstanceFieldTypeFlow loadFieldFLow = new LoadInstanceFieldTypeFlow(loadLocation, field, objectBuilder.get());
                            flowsGraph.addNodeFlow(bb, node, loadFieldFLow);
                            return loadFieldFLow;
                        });
                        loadFieldBuilder.addObserverDependency(objectBuilder);
                    }
                    if (bb.strengthenGraalGraphs()) {
                        typeFlowGraphBuilder.registerSinkBuilder(loadFieldBuilder);
                    }
                    state.add(node, loadFieldBuilder);
                }

            } else if (n instanceof StoreFieldNode) { // object.field = value
                processStoreField((StoreFieldNode) n, state);

            } else if (n instanceof LoadIndexedNode) {
                LoadIndexedNode node = (LoadIndexedNode) n;
                TypeFlowBuilder<?> arrayBuilder = state.lookup(node.array());
                if (node.getStackKind() == JavaKind.Object) {
                    AnalysisType type = (AnalysisType) StampTool.typeOrNull(node.array(), bb.getMetaAccess());
                    AnalysisType arrayType = type.isArray() ? type : bb.getObjectArrayType();

                    TypeFlowBuilder<?> loadIndexedBuilder = TypeFlowBuilder.create(bb, node, LoadIndexedTypeFlow.class, () -> {
                        LoadIndexedTypeFlow loadIndexedFlow = new LoadIndexedTypeFlow(AbstractAnalysisEngine.sourcePosition(node), arrayType, arrayBuilder.get());
                        flowsGraph.addNodeFlow(bb, node, loadIndexedFlow);
                        return loadIndexedFlow;
                    });

                    if (bb.strengthenGraalGraphs()) {
                        typeFlowGraphBuilder.registerSinkBuilder(loadIndexedBuilder);
                    }
                    loadIndexedBuilder.addObserverDependency(arrayBuilder);
                    state.add(node, loadIndexedBuilder);
                }

            } else if (n instanceof StoreIndexedNode) {
                processStoreIndexed((StoreIndexedNode) n, state);

            } else if (n instanceof UnsafePartitionLoadNode) {
                UnsafePartitionLoadNode node = (UnsafePartitionLoadNode) n;
                assert node.object().getStackKind() == JavaKind.Object : node.object();

                checkUnsafeOffset(node.object(), node.offset());

                AnalysisType partitionType = (AnalysisType) node.partitionType();

                AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(node.object(), bb.getMetaAccess());
                assert bb.getGraalNodeType().isAssignableFrom(objectType) : objectType;

                /* Use the Object type as a conservative type for the values loaded. */
                AnalysisType componentType = bb.getObjectType();

                TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                TypeFlowBuilder<?> unsafeLoadBuilder = TypeFlowBuilder.create(bb, node, UnsafePartitionLoadTypeFlow.class, () -> {
                    UnsafePartitionLoadTypeFlow loadTypeFlow = new UnsafePartitionLoadTypeFlow(AbstractAnalysisEngine.sourcePosition(node), objectType, componentType, objectBuilder.get(),
                                    node.unsafePartitionKind(), partitionType);
                    flowsGraph.addMiscEntryFlow(loadTypeFlow);
                    return loadTypeFlow;
                });
                unsafeLoadBuilder.addObserverDependency(objectBuilder);
                state.add(node, unsafeLoadBuilder);

            } else if (n instanceof UnsafePartitionStoreNode) {
                UnsafePartitionStoreNode node = (UnsafePartitionStoreNode) n;

                assert node.object().getStackKind() == JavaKind.Object : node.object();
                assert node.value().getStackKind() == JavaKind.Object : node.value();

                checkUnsafeOffset(node.object(), node.offset());

                AnalysisType partitionType = (AnalysisType) node.partitionType();

                AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(node.object(), bb.getMetaAccess());
                assert bb.getGraalNodeType().isAssignableFrom(objectType) : objectType;

                /* Use the Object type as a conservative type for the values stored. */
                AnalysisType componentType = bb.getObjectType();

                AnalysisType valueType = (AnalysisType) StampTool.typeOrNull(node.value(), bb.getMetaAccess());
                assert valueType.isJavaLangObject() || bb.getGraalNodeType().isAssignableFrom(valueType) || bb.getGraalNodeListType().isAssignableFrom(valueType) : valueType;

                TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                TypeFlowBuilder<?> valueBuilder = state.lookup(node.value());

                TypeFlowBuilder<?> unsafeStoreBuilder = TypeFlowBuilder.create(bb, node, UnsafePartitionStoreTypeFlow.class, () -> {
                    UnsafePartitionStoreTypeFlow storeTypeFlow = new UnsafePartitionStoreTypeFlow(AbstractAnalysisEngine.sourcePosition(node), objectType, componentType, objectBuilder.get(),
                                    valueBuilder.get(),
                                    node.partitionKind(), partitionType);
                    flowsGraph.addMiscEntryFlow(storeTypeFlow);
                    return storeTypeFlow;
                });
                unsafeStoreBuilder.addUseDependency(valueBuilder);
                unsafeStoreBuilder.addObserverDependency(objectBuilder);

                /* Unsafe stores must not be removed. */
                typeFlowGraphBuilder.registerSinkBuilder(unsafeStoreBuilder);

            } else if (n instanceof RawLoadNode) {
                RawLoadNode node = (RawLoadNode) n;

                checkUnsafeOffset(node.object(), node.offset());

                if (node.object().getStackKind() == JavaKind.Object && node.getStackKind() == JavaKind.Object) {
                    AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(node.object(), bb.getMetaAccess());
                    TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                    TypeFlowBuilder<?> loadBuilder;
                    BytecodePosition loadLocation = AbstractAnalysisEngine.sourcePosition(node);
                    if (objectType != null && objectType.isArray() && objectType.getComponentType().getJavaKind() == JavaKind.Object) {
                        /*
                         * Unsafe load from an array object is essentially an array load since we
                         * don't have separate type flows for different array elements.
                         */
                        loadBuilder = TypeFlowBuilder.create(bb, node, LoadIndexedTypeFlow.class, () -> {
                            LoadIndexedTypeFlow loadTypeFlow = new LoadIndexedTypeFlow(loadLocation, objectType, objectBuilder.get());
                            flowsGraph.addMiscEntryFlow(loadTypeFlow);
                            return loadTypeFlow;
                        });
                    } else {
                        /*
                         * Use the Object type as a conservative approximation for both the receiver
                         * object type and the loaded values type.
                         */
                        AnalysisType nonNullObjectType = bb.getObjectType();
                        loadBuilder = TypeFlowBuilder.create(bb, node, UnsafeLoadTypeFlow.class, () -> {
                            UnsafeLoadTypeFlow loadTypeFlow = new UnsafeLoadTypeFlow(loadLocation, nonNullObjectType, nonNullObjectType, objectBuilder.get());
                            flowsGraph.addMiscEntryFlow(loadTypeFlow);
                            return loadTypeFlow;
                        });
                    }

                    loadBuilder.addObserverDependency(objectBuilder);
                    state.add(node, loadBuilder);
                }

            } else if (n instanceof RawStoreNode) {
                RawStoreNode node = (RawStoreNode) n;

                checkUnsafeOffset(node.object(), node.offset());

                if (node.object().getStackKind() == JavaKind.Object && node.value().getStackKind() == JavaKind.Object) {
                    AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(node.object(), bb.getMetaAccess());
                    TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                    TypeFlowBuilder<?> valueBuilder = state.lookup(node.value());
                    TypeFlowBuilder<?> storeBuilder;
                    BytecodePosition storeLocation = AbstractAnalysisEngine.sourcePosition(node);
                    if (objectType != null && objectType.isArray() && objectType.getComponentType().getJavaKind() == JavaKind.Object) {
                        /*
                         * Unsafe store to an array object is essentially an array store since we
                         * don't have separate type flows for different array elements.
                         */
                        storeBuilder = TypeFlowBuilder.create(bb, node, StoreIndexedTypeFlow.class, () -> {
                            StoreIndexedTypeFlow storeTypeFlow = new StoreIndexedTypeFlow(storeLocation, objectType, objectBuilder.get(), valueBuilder.get());
                            flowsGraph.addMiscEntryFlow(storeTypeFlow);
                            return storeTypeFlow;
                        });
                    } else {
                        /*
                         * Use the Object type as a conservative approximation for both the receiver
                         * object type and the stored values type.
                         */
                        AnalysisType nonNullObjectType = bb.getObjectType();
                        storeBuilder = TypeFlowBuilder.create(bb, node, UnsafeStoreTypeFlow.class, () -> {
                            UnsafeStoreTypeFlow storeTypeFlow = new UnsafeStoreTypeFlow(storeLocation, nonNullObjectType, nonNullObjectType, objectBuilder.get(), valueBuilder.get());
                            flowsGraph.addMiscEntryFlow(storeTypeFlow);
                            return storeTypeFlow;
                        });
                    }
                    storeBuilder.addUseDependency(valueBuilder);
                    storeBuilder.addObserverDependency(objectBuilder);

                    /* Offset stores must not be removed. */
                    typeFlowGraphBuilder.registerSinkBuilder(storeBuilder);
                }
            } else if (n instanceof UnsafeCompareAndSwapNode) {
                UnsafeCompareAndSwapNode node = (UnsafeCompareAndSwapNode) n;
                ValueNode object = node.object();
                ValueNode newValue = node.newValue();

                checkUnsafeOffset(object, node.offset());
                if (object.getStackKind() == JavaKind.Object && newValue.getStackKind() == JavaKind.Object) {
                    AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(object, bb.getMetaAccess());
                    TypeFlowBuilder<?> objectBuilder = state.lookup(object);
                    TypeFlowBuilder<?> newValueBuilder = state.lookup(newValue);
                    TypeFlowBuilder<?> storeBuilder;
                    BytecodePosition storeLocation = AbstractAnalysisEngine.sourcePosition(node);
                    if (objectType != null && objectType.isArray() && objectType.getComponentType().getJavaKind() == JavaKind.Object) {
                        /*
                         * Unsafe compare and swap is essentially unsafe store and unsafe store to
                         * an array object is essentially an array store since we don't have
                         * separate type flows for different array elements.
                         */
                        storeBuilder = TypeFlowBuilder.create(bb, node, StoreIndexedTypeFlow.class, () -> {
                            StoreIndexedTypeFlow storeTypeFlow = new StoreIndexedTypeFlow(storeLocation, objectType, objectBuilder.get(), newValueBuilder.get());
                            flowsGraph.addMiscEntryFlow(storeTypeFlow);
                            return storeTypeFlow;
                        });
                    } else {
                        /*
                         * Use the Object type as a conservative approximation for both the receiver
                         * object type and the swapped values type.
                         */
                        AnalysisType nonNullObjectType = bb.getObjectType();
                        storeBuilder = TypeFlowBuilder.create(bb, node, UnsafeStoreTypeFlow.class, () -> {
                            UnsafeStoreTypeFlow storeTypeFlow = new UnsafeStoreTypeFlow(storeLocation, nonNullObjectType, nonNullObjectType, objectBuilder.get(), newValueBuilder.get());
                            flowsGraph.addMiscEntryFlow(storeTypeFlow);
                            return storeTypeFlow;
                        });
                    }
                    storeBuilder.addUseDependency(newValueBuilder);
                    storeBuilder.addObserverDependency(objectBuilder);

                    /* Offset stores must not be removed. */
                    typeFlowGraphBuilder.registerSinkBuilder(storeBuilder);
                }

            } else if (n instanceof UnsafeCompareAndExchangeNode) {
                UnsafeCompareAndExchangeNode node = (UnsafeCompareAndExchangeNode) n;
                modelUnsafeReadAndWriteFlow(node, node.object(), node.newValue(), node.offset());

            } else if (n instanceof AtomicReadAndWriteNode) {
                AtomicReadAndWriteNode node = (AtomicReadAndWriteNode) n;
                modelUnsafeReadAndWriteFlow(node, node.object(), node.newValue(), node.offset());

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

                    TypeFlowBuilder<?> arrayCopyBuilder = TypeFlowBuilder.create(bb, node, ArrayCopyTypeFlow.class, () -> {
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
                if (invoke.callTarget() instanceof MethodCallTargetNode) {
                    guarantee(bb.strengthenGraalGraphs() || invoke.stateAfter().outerFrameState() == null,
                                    "Outer FrameState of %s must be null, but was %s. A non-null outer FrameState indicates that a method inlining has happened, but inlining should only happen after analysis.",
                                    invoke.stateAfter(), invoke.stateAfter().outerFrameState());
                    MethodCallTargetNode target = (MethodCallTargetNode) invoke.callTarget();

                    processMethodInvocation(state, invoke, target.invokeKind(), (PointsToAnalysisMethod) target.targetMethod(), target.arguments());
                }

            } else if (n instanceof ObjectClone) {
                ObjectClone node = (ObjectClone) n;
                TypeFlowBuilder<?> inputBuilder = state.lookup(node.getObject());
                AnalysisType inputType = (AnalysisType) StampTool.typeOrNull(node.getObject(), bb.getMetaAccess());

                TypeFlowBuilder<?> cloneBuilder = TypeFlowBuilder.create(bb, node, CloneTypeFlow.class, () -> {
                    CloneTypeFlow cloneFlow = new CloneTypeFlow(AbstractAnalysisEngine.sourcePosition(node.asNode()), inputType, inputBuilder.get());
                    flowsGraph.addMiscEntryFlow(cloneFlow);
                    return cloneFlow;
                });
                cloneBuilder.addObserverDependency(inputBuilder);
                state.add(node.asFixedNode(), cloneBuilder);
            } else if (n instanceof MonitorEnterNode) {
                MonitorEnterNode node = (MonitorEnterNode) n;
                TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());

                TypeFlowBuilder<?> monitorEntryBuilder = TypeFlowBuilder.create(bb, node, MonitorEnterTypeFlow.class, () -> {
                    MonitorEnterTypeFlow monitorEntryFlow = new MonitorEnterTypeFlow(AbstractAnalysisEngine.sourcePosition(node), bb);
                    flowsGraph.addMiscEntryFlow(monitorEntryFlow);
                    return monitorEntryFlow;
                });
                monitorEntryBuilder.addUseDependency(objectBuilder);
                /* Monitor enters must not be removed. */
                typeFlowGraphBuilder.registerSinkBuilder(monitorEntryBuilder);
            } else if (n instanceof MacroInvokable) {
                /*
                 * Macro nodes can either be constant folded during compilation, or lowered back to
                 * invocations if constant folding is not possible. So the static analysis needs to
                 * treat them as possible invocations.
                 *
                 * Note that some macro nodes, like for object cloning, are handled separately
                 * above.
                 */
                MacroInvokable node = (MacroInvokable) n;
                processMacroInvokable(state, node, true);
            }
        }

        /**
         * Model an unsafe-read-and-write operation.
         *
         * In the analysis this is used to model both {@link AtomicReadAndWriteNode}, i.e., an
         * atomic read-and-write operation like
         * {@code sun.misc.Unsafe#getAndSetObject(Object, long, Object)}, and
         * {@link UnsafeCompareAndExchangeNode}, i.e., an atomic compare-and-swap operation like
         * {@code jdk.internal.misc.Unsafe#compareAndExchangeObject(Object, long, Object, Object)}
         * where the result is the current value of the memory location that was compared.
         *
         * The Unsafe.compareAndExchangeObject() operation is similar to the
         * Unsafe.compareAndSwapObject() operation which is modeled by the
         * {@link UnsafeCompareAndSwapNode} above. However, Unsafe.compareAndSwapObject() returns a
         * boolean, which the analysis ignores, whereas Unsafe.compareAndExchangeObject() returns
         * the previous value, therefore it is equivalent to the model for Unsafe.getAndSetObject().
         */
        private void modelUnsafeReadAndWriteFlow(ValueNode node, ValueNode object, ValueNode newValue, ValueNode offset) {
            assert node instanceof UnsafeCompareAndExchangeNode || node instanceof AtomicReadAndWriteNode : node;

            checkUnsafeOffset(object, offset);

            if (object.getStackKind() == JavaKind.Object && newValue.getStackKind() == JavaKind.Object) {
                AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(object, bb.getMetaAccess());
                TypeFlowBuilder<?> objectBuilder = state.lookup(object);
                TypeFlowBuilder<?> newValueBuilder = state.lookup(newValue);

                TypeFlowBuilder<?> storeBuilder;
                TypeFlowBuilder<?> loadBuilder;

                BytecodePosition location = AbstractAnalysisEngine.sourcePosition(node);
                if (objectType != null && objectType.isArray() && objectType.getComponentType().getJavaKind() == JavaKind.Object) {
                    /*
                     * Atomic read and write is essentially unsafe store and unsafe store to an
                     * array object is essentially an array store since we don't have separate type
                     * flows for different array elements.
                     */
                    storeBuilder = TypeFlowBuilder.create(bb, node, StoreIndexedTypeFlow.class, () -> {
                        StoreIndexedTypeFlow storeTypeFlow = new StoreIndexedTypeFlow(location, objectType, objectBuilder.get(), newValueBuilder.get());
                        flowsGraph.addMiscEntryFlow(storeTypeFlow);
                        return storeTypeFlow;
                    });

                    loadBuilder = TypeFlowBuilder.create(bb, node, LoadIndexedTypeFlow.class, () -> {
                        LoadIndexedTypeFlow loadTypeFlow = new LoadIndexedTypeFlow(location, objectType, objectBuilder.get());
                        flowsGraph.addMiscEntryFlow(loadTypeFlow);
                        return loadTypeFlow;
                    });

                } else {
                    /*
                     * Use the Object type as a conservative approximation for both the receiver
                     * object type and the read/written values type.
                     */
                    AnalysisType nonNullObjectType = bb.getObjectType();
                    storeBuilder = TypeFlowBuilder.create(bb, node, UnsafeStoreTypeFlow.class, () -> {
                        UnsafeStoreTypeFlow storeTypeFlow = new UnsafeStoreTypeFlow(location, nonNullObjectType, nonNullObjectType, objectBuilder.get(), newValueBuilder.get());
                        flowsGraph.addMiscEntryFlow(storeTypeFlow);
                        return storeTypeFlow;
                    });

                    loadBuilder = TypeFlowBuilder.create(bb, node, UnsafeLoadTypeFlow.class, () -> {
                        UnsafeLoadTypeFlow loadTypeFlow = new UnsafeLoadTypeFlow(location, nonNullObjectType, nonNullObjectType, objectBuilder.get());
                        flowsGraph.addMiscEntryFlow(loadTypeFlow);
                        return loadTypeFlow;
                    });

                }

                storeBuilder.addUseDependency(newValueBuilder);
                storeBuilder.addObserverDependency(objectBuilder);
                loadBuilder.addObserverDependency(objectBuilder);

                /* Offset stores must not be removed. */
                typeFlowGraphBuilder.registerSinkBuilder(storeBuilder);

                state.add(node, loadBuilder);
            }
        }
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
            if (actualParam.getStackKind() == JavaKind.Object) {
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

        TypeFlowBuilder<InvokeTypeFlow> invokeBuilder = TypeFlowBuilder.create(bb, invoke, InvokeTypeFlow.class, () -> {

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

            flowsGraph.addInvoke(StaticAnalysisResultsBuilder.uniqueKey(invoke), invokeFlow);
            if (bb.strengthenGraalGraphs()) {
                flowsGraph.addNodeFlow(bb, invoke, invokeFlow);
            }

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

        if (!createDeoptInvokeTypeFlow && invoke.asNode().getStackKind() == JavaKind.Object) {
            /* Create the actual return builder. */
            AnalysisType returnType = (AnalysisType) targetMethod.getSignature().getReturnType(null);
            TypeFlowBuilder<?> actualReturnBuilder = TypeFlowBuilder.create(bb, invoke.asNode(), ActualReturnTypeFlow.class, () -> {
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

            ObjectStamp stamp = (ObjectStamp) invoke.stamp(NodeView.DEFAULT);
            AnalysisType stampType = (AnalysisType) StampTool.typeOrNull(stamp, bb.getMetaAccess());
            if (stamp.nonNull() && !returnType.equals(stampType) && returnType.isAssignableFrom(stampType)) {
                /*
                 * If the invoke stamp has a more precise type than the return type use that to
                 * filter the returned values. This can happen for example for MacroInvokable nodes
                 * when more concrete stamp information can be inferred for example from parameter
                 * types. In that case the Graal graph optimizations may decide to remove a
                 * checkcast that would normally follow the invoke, so we need to introduce the
                 * filter to avoid loosing precision.
                 */
                TypeFlowBuilder<?> filterBuilder = TypeFlowBuilder.create(bb, invoke, FilterTypeFlow.class, () -> {
                    FilterTypeFlow filterFlow = new FilterTypeFlow(invokeLocation, stampType, stamp.isExactType(), true, true);
                    flowsGraph.addMiscEntryFlow(filterFlow);
                    return filterFlow;
                });
                filterBuilder.addUseDependency(actualReturnBuilder);
                actualReturnBuilder = filterBuilder;
            }

            if (bb.strengthenGraalGraphs()) {
                typeFlowGraphBuilder.registerSinkBuilder(actualReturnBuilder);
            }
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
            processNewInstance(allocatedObjectNode, type, state);
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
                        processStoreIndexed(commitAllocationNode, object, value, state);
                    } else {
                        AnalysisField field = (AnalysisField) ((VirtualInstanceNode) virtualObject).field(i);
                        processStoreField(commitAllocationNode, field, object, value, state);
                    }
                }
            }
            objectStartIndex += virtualObject.entryCount();
        }
        assert values.size() == objectStartIndex : values;
    }

    protected void processNewInstance(NewInstanceNode node, TypeFlowsOfNodes state) {
        /* Instance fields of a new object are initialized to null state in AnalysisField. */
        processNewInstance(node, (AnalysisType) node.instanceClass(), state);
    }

    protected void processNewArray(NewArrayNode node, TypeFlowsOfNodes state) {
        processNewInstance(node, ((AnalysisType) node.elementType()).getArrayClass(), state);
    }

    protected void processNewInstance(ValueNode node, AnalysisType type, TypeFlowsOfNodes state) {
        assert type.isInstantiated() : type;

        TypeFlowBuilder<?> newInstanceBuilder = TypeFlowBuilder.create(bb, node, NewInstanceTypeFlow.class, () -> {
            NewInstanceTypeFlow newInstance = new NewInstanceTypeFlow(AbstractAnalysisEngine.sourcePosition(node), type);
            flowsGraph.addMiscEntryFlow(newInstance);
            return newInstance;
        });
        state.add(node, newInstanceBuilder);
    }

    protected void processStoreField(StoreFieldNode node, TypeFlowsOfNodes state) {
        processStoreField(node, (AnalysisField) node.field(), node.object(), node.value(), state);
    }

    protected void processStoreField(ValueNode node, AnalysisField field, ValueNode object, ValueNode value, TypeFlowsOfNodes state) {
        assert field.isWritten() : field;
        if (value.getStackKind() == JavaKind.Object) {
            TypeFlowBuilder<?> valueBuilder = state.lookup(value);

            TypeFlowBuilder<StoreFieldTypeFlow> storeFieldBuilder;
            BytecodePosition storeLocation = AbstractAnalysisEngine.sourcePosition(node);
            if (field.isStatic()) {
                storeFieldBuilder = TypeFlowBuilder.create(bb, node, StoreFieldTypeFlow.class, () -> {
                    FieldTypeFlow fieldFlow = field.getStaticFieldFlow();
                    StoreStaticFieldTypeFlow storeFieldFlow = new StoreStaticFieldTypeFlow(storeLocation, field, valueBuilder.get(), fieldFlow);
                    flowsGraph.addMiscEntryFlow(storeFieldFlow);
                    return storeFieldFlow;
                });
                storeFieldBuilder.addUseDependency(valueBuilder);
            } else {
                TypeFlowBuilder<?> objectBuilder = state.lookup(object);
                storeFieldBuilder = TypeFlowBuilder.create(bb, node, StoreFieldTypeFlow.class, () -> {
                    StoreInstanceFieldTypeFlow storeFieldFlow = new StoreInstanceFieldTypeFlow(storeLocation, field, valueBuilder.get(), objectBuilder.get());
                    flowsGraph.addMiscEntryFlow(storeFieldFlow);
                    return storeFieldFlow;
                });
                storeFieldBuilder.addUseDependency(valueBuilder);
                storeFieldBuilder.addObserverDependency(objectBuilder);
            }
            /* Field stores must not be removed. */
            typeFlowGraphBuilder.registerSinkBuilder(storeFieldBuilder);
        }
    }

    private void processStoreIndexed(StoreIndexedNode node, TypeFlowsOfNodes state) {
        processStoreIndexed(node, node.array(), node.value(), state);
    }

    private void processStoreIndexed(ValueNode node, ValueNode array, ValueNode value, TypeFlowsOfNodes state) {
        if (value.getStackKind() == JavaKind.Object) {
            AnalysisType type = (AnalysisType) StampTool.typeOrNull(array, bb.getMetaAccess());
            AnalysisType arrayType = type.isArray() ? type : bb.getObjectArrayType();
            TypeFlowBuilder<?> arrayBuilder = state.lookup(array);
            TypeFlowBuilder<?> valueBuilder = state.lookup(value);
            TypeFlowBuilder<?> storeIndexedBuilder = TypeFlowBuilder.create(bb, node, StoreIndexedTypeFlow.class, () -> {
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

    /** Hook for unsafe offset value checks. */
    protected void checkUnsafeOffset(@SuppressWarnings("unused") ValueNode base, @SuppressWarnings("unused") ValueNode offset) {
    }
}
