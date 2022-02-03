/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.ForeignCall;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndExchangeNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndSwapNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.graph.MergeableState;
import org.graalvm.compiler.phases.graph.PostOrderNodeIterator;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.MacroInvokable;
import org.graalvm.compiler.replacements.nodes.ObjectClone;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.word.WordCastNode;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.LoadFieldTypeFlow.LoadInstanceFieldTypeFlow;
import com.oracle.graal.pointsto.flow.LoadFieldTypeFlow.LoadStaticFieldTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.AtomicReadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.LoadIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.UnsafeLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.UnsafePartitionLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.AtomicWriteTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.CompareAndSwapTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.StoreIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.UnsafePartitionStoreTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.UnsafeStoreTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow.StoreInstanceFieldTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow.StoreStaticFieldTypeFlow;
import com.oracle.graal.pointsto.flow.builder.TypeFlowBuilder;
import com.oracle.graal.pointsto.flow.builder.TypeFlowGraphBuilder;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.nodes.UnsafePartitionLoadNode;
import com.oracle.graal.pointsto.nodes.UnsafePartitionStoreNode;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysis;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.VMConstant;

public class MethodTypeFlowBuilder {

    protected final PointsToAnalysis bb;
    protected final MethodTypeFlow methodFlow;
    protected final PointsToAnalysisMethod method;
    protected StructuredGraph graph;
    private NodeBitMap processedNodes;
    private Map<PhiNode, TypeFlowBuilder<?>> loopPhiFlows;

    protected final TypeFlowGraphBuilder typeFlowGraphBuilder;

    public MethodTypeFlowBuilder(PointsToAnalysis bb, MethodTypeFlow methodFlow) {
        this.bb = bb;
        this.methodFlow = methodFlow;
        this.method = methodFlow.getMethod();
        typeFlowGraphBuilder = new TypeFlowGraphBuilder(bb);
    }

    public MethodTypeFlowBuilder(PointsToAnalysis bb, StructuredGraph graph) {
        this.bb = bb;
        this.graph = graph;
        this.method = (PointsToAnalysisMethod) graph.method();
        this.methodFlow = method.getTypeFlow();
        this.typeFlowGraphBuilder = null;
    }

    @SuppressWarnings("try")
    private boolean parse() {
        AnalysisParsedGraph analysisParsedGraph = method.ensureGraphParsed(bb);
        if (analysisParsedGraph.isIntrinsic()) {
            method.registerAsIntrinsicMethod();
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
                registerUsedElements(false);
            }
            CanonicalizerPhase canonicalizerPhase = CanonicalizerPhase.create();
            canonicalizerPhase.apply(graph, bb.getProviders());
            if (bb.strengthenGraalGraphs()) {
                /*
                 * Removing unnecessary conditions before the static analysis runs reduces the size
                 * of the type flow graph. For example, this removes redundant null checks: the
                 * bytecode parser emits explicit null checks before e.g., all method calls, field
                 * access, array accesses; many of those dominate each other.
                 */
                new IterativeConditionalEliminationPhase(canonicalizerPhase, false).apply(graph, bb.getProviders());
            }

            // Do it again after canonicalization changed type checks and field accesses.
            registerUsedElements(true);

            /*
             * When we intend to strengthen Graal graphs, then the graph needs to be preserved. Type
             * flow nodes references Graal IR nodes directly as their source position.
             *
             * When we create separate StaticAnalysisResults objects, then Graal graphs are not
             * needed after static analysis.
             */
            if (bb.strengthenGraalGraphs()) {
                method.setAnalyzedGraph(graph);
            }

            return true;
        } catch (Throwable ex) {
            throw graph.getDebug().handle(ex);
        }
    }

    public void registerUsedElements(boolean registerEmbeddedRoots) {
        for (Node n : graph.getNodes()) {
            if (n instanceof InstanceOfNode) {
                InstanceOfNode node = (InstanceOfNode) n;
                AnalysisType type = (AnalysisType) node.type().getType();
                type.registerAsReachable();

            } else if (n instanceof NewInstanceNode) {
                NewInstanceNode node = (NewInstanceNode) n;
                AnalysisType type = (AnalysisType) node.instanceClass();
                type.registerAsAllocated(node);

            } else if (n instanceof VirtualObjectNode) {
                VirtualObjectNode node = (VirtualObjectNode) n;
                AnalysisType type = (AnalysisType) node.type();
                type.registerAsAllocated(node);

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
                                field.registerAsWritten(methodFlow);
                            }
                        }
                    }
                    objectStartIndex += virtualObject.entryCount();
                }

            } else if (n instanceof NewArrayNode) {
                NewArrayNode node = (NewArrayNode) n;
                AnalysisType type = ((AnalysisType) node.elementType()).getArrayClass();
                type.registerAsAllocated(node);

            } else if (n instanceof NewMultiArrayNode) {
                NewMultiArrayNode node = (NewMultiArrayNode) n;
                AnalysisType type = ((AnalysisType) node.type());
                for (int i = 0; i < node.dimensionCount(); i++) {
                    type.registerAsAllocated(node);
                    type = type.getComponentType();
                }

            } else if (n instanceof BoxNode) {
                BoxNode node = (BoxNode) n;
                AnalysisType type = (AnalysisType) StampTool.typeOrNull(node);
                type.registerAsAllocated(node);

            } else if (n instanceof LoadFieldNode) {
                LoadFieldNode node = (LoadFieldNode) n;
                AnalysisField field = (AnalysisField) node.field();
                field.registerAsRead(methodFlow);

            } else if (n instanceof StoreFieldNode) {
                StoreFieldNode node = (StoreFieldNode) n;
                AnalysisField field = (AnalysisField) node.field();
                field.registerAsWritten(methodFlow);

            } else if (n instanceof ConstantNode) {
                ConstantNode cn = (ConstantNode) n;
                if (cn.hasUsages() && cn.isJavaConstant() && cn.asJavaConstant().getJavaKind() == JavaKind.Object && cn.asJavaConstant().isNonNull()) {
                    assert StampTool.isExactType(cn);
                    AnalysisType type = (AnalysisType) StampTool.typeOrNull(cn);
                    type.registerAsInHeap();
                    if (registerEmbeddedRoots) {
                        registerEmbeddedRoot(cn);
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
                    frameStateMethod.getDeclaringClass().registerAsReachable();
                }

            } else if (n instanceof ForeignCall) {
                ForeignCall node = (ForeignCall) n;
                registerForeignCall(bb, node.getDescriptor());
            } else if (n instanceof UnaryMathIntrinsicNode) {
                UnaryMathIntrinsicNode node = (UnaryMathIntrinsicNode) n;
                registerForeignCall(bb, bb.getProviders().getForeignCalls().getDescriptor(node.getOperation().foreignCallSignature));
            } else if (n instanceof BinaryMathIntrinsicNode) {
                BinaryMathIntrinsicNode node = (BinaryMathIntrinsicNode) n;
                registerForeignCall(bb, bb.getProviders().getForeignCalls().getDescriptor(node.getOperation().foreignCallSignature));
            }
        }
    }

    private void registerEmbeddedRoot(ConstantNode cn) {
        JavaConstant root = cn.asJavaConstant();
        if (bb.scanningPolicy().trackConstant(bb, root)) {
            BytecodePosition position = cn.getNodeSourcePosition();
            if (position == null) {
                position = new BytecodePosition(null, method, 0);
            }
            bb.getUniverse().registerEmbeddedRoot(root, position);
        }
    }

    private static void registerForeignCall(PointsToAnalysis bb, ForeignCallDescriptor foreignCallDescriptor) {
        Optional<AnalysisMethod> targetMethod = bb.getHostVM().handleForeignCall(foreignCallDescriptor, bb.getProviders().getForeignCalls());
        targetMethod.ifPresent(analysisMethod -> {
            bb.addRootMethod(analysisMethod);
        });
    }

    protected void apply() {
        // assert method.getAnnotation(Fold.class) == null : method;
        if (GuardedAnnotationAccess.isAnnotationPresent(method, NodeIntrinsic.class)) {
            graph.getDebug().log("apply MethodTypeFlow on node intrinsic %s", method);
            AnalysisType returnType = (AnalysisType) method.getSignature().getReturnType(method.getDeclaringClass());
            if (returnType.getJavaKind() == JavaKind.Object) {
                /*
                 * This is a method used in a snippet, so most likely the return value does not
                 * matter at all. However, some methods return an object, and the snippet continues
                 * to work with the object. So pretend that this method returns an object of the
                 * exact return type.
                 */
                TypeFlow<?> returnTypeFlow = methodFlow.getResultFlow().getDeclaredType().getTypeFlow(this.bb, true);
                BytecodePosition source = new BytecodePosition(null, method, 0);
                returnTypeFlow = new ProxyTypeFlow(source, returnTypeFlow);
                FormalReturnTypeFlow resultFlow = new FormalReturnTypeFlow(source, returnType, method);
                returnTypeFlow.addOriginalUse(this.bb, resultFlow);
                methodFlow.addMiscEntry(returnTypeFlow);
                methodFlow.setResult(resultFlow);
            }
            return;
        }

        if (!parse()) {
            return;
        }

        bb.getHostVM().methodBeforeTypeFlowCreationHook(bb, method, graph);

        processedNodes = new NodeBitMap(graph);

        TypeFlowsOfNodes typeFlows = new TypeFlowsOfNodes();
        for (Node n : graph.getNodes()) {
            if (n instanceof ParameterNode) {
                ParameterNode node = (ParameterNode) n;
                if (node.getStackKind() == JavaKind.Object) {
                    TypeFlowBuilder<?> paramBuilder = TypeFlowBuilder.create(bb, node, FormalParamTypeFlow.class, () -> {
                        boolean isStatic = Modifier.isStatic(method.getModifiers());
                        int index = node.index();
                        FormalParamTypeFlow parameter;
                        if (!isStatic && index == 0) {
                            AnalysisType paramType = method.getDeclaringClass();
                            parameter = new FormalReceiverTypeFlow(node, paramType, method);
                        } else {
                            int offset = isStatic ? 0 : 1;
                            AnalysisType paramType = (AnalysisType) method.getSignature().getParameterType(index - offset, method.getDeclaringClass());
                            parameter = new FormalParamTypeFlow(node, paramType, method, index);
                        }
                        methodFlow.setParameter(index, parameter);
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
                Object key = uniqueKey(node);
                BytecodeLocation boxSite = bb.analysisPolicy().createAllocationSite(bb, key, methodFlow.getMethod());
                AnalysisType type = (AnalysisType) StampTool.typeOrNull(node);

                TypeFlowBuilder<?> boxBuilder = TypeFlowBuilder.create(bb, node, BoxTypeFlow.class, () -> {
                    BoxTypeFlow boxFlow = new BoxTypeFlow(node, type, boxSite);
                    methodFlow.addMiscEntry(boxFlow);
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
                        TypeFlowBuilder<SourceTypeFlow> sourceBuilder = TypeFlowBuilder.create(bb, node, SourceTypeFlow.class, () -> {
                            SourceTypeFlow constantSource = new SourceTypeFlow(node, TypeState.forNull());
                            methodFlow.addMiscEntry(constantSource);
                            return constantSource;
                        });
                        typeFlows.add(node, sourceBuilder);
                    } else if (node.asJavaConstant().getJavaKind() == JavaKind.Object) {
                        /*
                         * TODO a SubstrateObjectConstant wrapping a PrimitiveConstant has kind
                         * equals to Object. Do we care about the effective value of these primitive
                         * constants in the analysis?
                         */
                        assert StampTool.isExactType(node);
                        AnalysisType type = (AnalysisType) StampTool.typeOrNull(node);
                        assert type.isInstantiated();
                        TypeFlowBuilder<SourceTypeFlow> sourceBuilder = TypeFlowBuilder.create(bb, node, SourceTypeFlow.class, () -> {
                            SourceTypeFlow constantSource = new SourceTypeFlow(node, TypeState.forConstant(this.bb, node.asJavaConstant(), type));
                            methodFlow.addMiscEntry(constantSource);
                            return constantSource;
                        });
                        typeFlows.add(node, sourceBuilder);
                    }
                }
            }
        }

        // Propagate the type flows through the method's graph
        new NodeIterator(graph.start(), typeFlows).apply();

        /* Prune the method graph. Eliminate nodes with no uses. */
        typeFlowGraphBuilder.build();

        /*
         * Make sure that all existing InstanceOfNodes are registered even when only used as an
         * input of a conditional.
         */
        for (Node n : graph.getNodes()) {
            if (n instanceof InstanceOfNode) {
                InstanceOfNode instanceOf = (InstanceOfNode) n;
                markFieldsUsedInComparison(instanceOf.getValue());
            } else if (n instanceof ObjectEqualsNode) {
                ObjectEqualsNode compareNode = (ObjectEqualsNode) n;
                markFieldsUsedInComparison(compareNode.getX());
                markFieldsUsedInComparison(compareNode.getY());
            }
        }
    }

    /**
     * If the node corresponding to the compared value is an instance field load then mark that
     * field as being used in a comparison.
     *
     * @param comparedValue the node corresponding to the compared value
     */
    private static void markFieldsUsedInComparison(ValueNode comparedValue) {
        if (comparedValue instanceof LoadFieldNode) {
            LoadFieldNode load = (LoadFieldNode) comparedValue;
            AnalysisField field = (AnalysisField) load.field();
            if (!field.isStatic()) {
                field.markAsUsedInComparison();
            }
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
            return flows.containsKey(GraphUtil.unproxify(node));
        }

        public TypeFlowBuilder<?> lookup(ValueNode n) {
            assert n.stamp(NodeView.DEFAULT) instanceof ObjectStamp;

            ValueNode node = GraphUtil.unproxify(n);
            TypeFlowBuilder<?> result = flows.get(node);
            if (result == null) {
                /*
                 * There is no type flow set, yet. Therefore we have no info for the node.
                 */
                ObjectStamp stamp = (ObjectStamp) n.stamp(NodeView.DEFAULT);
                if (stamp.isEmpty()) {
                    result = TypeFlowBuilder.create(bb, node, SourceTypeFlow.class, () -> new SourceTypeFlow(node, TypeState.forEmpty()));
                } else if (stamp.isExactType()) {
                    /*
                     * We are lucky: the stamp tells us which type the node has.
                     */
                    result = TypeFlowBuilder.create(bb, node, SourceTypeFlow.class, () -> {
                        SourceTypeFlow src = new SourceTypeFlow(node, TypeState.forExactType(bb, (AnalysisType) stamp.type(), !stamp.nonNull()));
                        methodFlow.addMiscEntry(src);
                        return src;
                    });

                } else {
                    /*
                     * Use a type state which consists of all allocated types (which are compatible
                     * to the node's type). Is is a conservative assumption.
                     */
                    AnalysisType type = (AnalysisType) (stamp.type() == null ? bb.getObjectType() : stamp.type());

                    if (type.isJavaLangObject()) {
                        /* Return a proxy to the all-instantiated type flow. */
                        result = TypeFlowBuilder.create(bb, node, ProxyTypeFlow.class, () -> {
                            ProxyTypeFlow proxy = new ProxyTypeFlow(node, bb.getAllInstantiatedTypeFlow());
                            methodFlow.addMiscEntry(proxy);
                            return proxy;
                        });
                    } else {
                        result = TypeFlowBuilder.create(bb, node, ProxyTypeFlow.class, () -> {
                            ProxyTypeFlow proxy = new ProxyTypeFlow(node, type.getTypeFlow(bb, true));
                            methodFlow.addMiscEntry(proxy);
                            return proxy;
                        });
                    }
                }
                flows.put(node, result);
            }
            return result;
        }

        public void add(ValueNode node, TypeFlowBuilder<?> flow) {
            assert !contains(node);
            flows.put(GraphUtil.unproxify(node), flow);
        }

        public void update(ValueNode node, TypeFlowBuilder<?> flow) {
            assert contains(node);
            flows.put(GraphUtil.unproxify(node), flow);
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
                                MergeTypeFlow newMergeFlow = new MergeTypeFlow(merge);
                                methodFlow.addMiscEntry(newMergeFlow);
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

        private HashMap<Object, TypeFlowBuilder<?>> instanceOfFlows;
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
                        FormalReturnTypeFlow resultFlow = new FormalReturnTypeFlow(node, returnType, method);
                        methodFlow.setResult(resultFlow);
                        return resultFlow;
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
            Object key = uniqueKey(node);
            return instanceOfFlows.computeIfAbsent(key, (bciKey) -> {
                BytecodeLocation location = BytecodeLocation.create(bciKey, method);
                TypeFlowBuilder<?> instanceOfBuilder = TypeFlowBuilder.create(bb, node, InstanceOfTypeFlow.class, () -> {
                    InstanceOfTypeFlow instanceOf = new InstanceOfTypeFlow(node, location, declaredType);
                    methodFlow.addInstanceOf(key, instanceOf);
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
                    NullCheckTypeFlow nullCheckFlow = new NullCheckTypeFlow(source, inputBuilder.get().getDeclaredType(), !isTrue);
                    methodFlow.addNodeFlow(bb, source, nullCheckFlow);
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
                NodeSourcePosition instanceOfPosition = instanceOf.getNodeSourcePosition();
                if (!bb.strengthenGraalGraphs() && instanceOfPosition != null && instanceOfPosition.getBCI() >= 0) {
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
                    FilterTypeFlow filterFlow = new FilterTypeFlow(source, type, typeReference.isExact(), isTrue, !isTrue ^ instanceOf.allowsNull());
                    methodFlow.addNodeFlow(bb, source, filterFlow);
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
            if (n instanceof LoopEndNode) {
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
                            MergeTypeFlow newFlow = new MergeTypeFlow(merge);
                            methodFlow.addMiscEntry(newFlow);
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
                TypeFlowBuilder<?> exceptionObjectBuilder = TypeFlowBuilder.create(bb, node, ExceptionObjectTypeFlow.class, () -> {
                    TypeFlow<?> input = ((AnalysisType) StampTool.typeOrNull(node)).getTypeFlow(bb, false);
                    ExceptionObjectTypeFlow exceptionObjectFlow = new ExceptionObjectTypeFlow(node, input);
                    methodFlow.addMiscEntry(exceptionObjectFlow);
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
                ReturnNode node = (ReturnNode) n;
                TypeFlowBuilder<?> returnFlowBuilder = uniqueReturnFlowBuilder(node);
                if (node.result() != null && node.result().getStackKind() == JavaKind.Object) {
                    returnFlowBuilder.addUseDependency(state.lookup(node.result()));
                }
            } else if (n instanceof CommitAllocationNode) {
                processCommitAllocation((CommitAllocationNode) n, state);
            } else if (n instanceof NewInstanceNode) {
                processNewInstance((NewInstanceNode) n, state);
            } else if (n instanceof DynamicNewInstanceNode) {
                DynamicNewInstanceNode node = (DynamicNewInstanceNode) n;
                ValueNode instanceTypeNode = node.getInstanceType();

                JVMCIError.guarantee(!instanceTypeNode.isConstant(), "DynamicNewInstanceNode.instanceType is constant, should have been canonicalized to NewInstanceNode.");

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
                    instanceType = (AnalysisType) StampTool.typeOrNull(getClassReceiver);
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
                AnalysisType nonNullInstanceType = Optional.ofNullable(instanceType).orElseGet(bb::getObjectType);
                Object key = uniqueKey(node);
                BytecodeLocation allocationLabel = bb.analysisPolicy().createAllocationSite(bb, key, method);
                TypeFlowBuilder<DynamicNewInstanceTypeFlow> dynamicNewInstanceBuilder = TypeFlowBuilder.create(bb, node, DynamicNewInstanceTypeFlow.class, () -> {
                    DynamicNewInstanceTypeFlow newInstanceTypeFlow = new DynamicNewInstanceTypeFlow(instanceTypeBuilder.get(), nonNullInstanceType, node, allocationLabel);
                    methodFlow.addMiscEntry(newInstanceTypeFlow);
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
                ValueNode elementType = node.getElementType();

                JVMCIError.guarantee(!elementType.isConstant(), "DynamicNewArrayNode.element is constant, should have been canonicalized to NewArrayNode.");

                /*
                 * Without precise type information the dynamic new array node has to generate a
                 * heap object for each instantiated array type.
                 *
                 * The node can allocate subclasses of Object[] but also primitive arrays. So there
                 * is no better type than java.lang.Object that we can use.
                 */
                AnalysisType arrayType = bb.getObjectType();

                Object key = uniqueKey(node);
                BytecodeLocation allocationLabel = bb.analysisPolicy().createAllocationSite(bb, key, method);

                TypeFlowBuilder<DynamicNewInstanceTypeFlow> dynamicNewArrayBuilder = TypeFlowBuilder.create(bb, node, DynamicNewInstanceTypeFlow.class, () -> {
                    DynamicNewInstanceTypeFlow newArrayTypeFlow = new DynamicNewInstanceTypeFlow(arrayType.getTypeFlow(bb, false), arrayType, node, allocationLabel);
                    methodFlow.addMiscEntry(newArrayTypeFlow);
                    return newArrayTypeFlow;
                });
                state.add(node, dynamicNewArrayBuilder);

            } else if (n instanceof NewMultiArrayNode) {
                NewMultiArrayNode node = (NewMultiArrayNode) n;
                AnalysisType type = ((AnalysisType) node.type());
                assert type.isInstantiated();

                Object key = uniqueKey(node);
                BytecodeLocation allocationLabel = bb.analysisPolicy().createAllocationSite(bb, key, method);
                TypeFlowBuilder<NewInstanceTypeFlow> newArrayBuilder = TypeFlowBuilder.create(bb, node, NewInstanceTypeFlow.class, () -> {
                    NewInstanceTypeFlow newArray = new NewInstanceTypeFlow(node, type, allocationLabel);
                    methodFlow.addMiscEntry(newArray);
                    return newArray;
                });

                state.add(node, newArrayBuilder);

            } else if (n instanceof LoadFieldNode) { // value = object.field
                LoadFieldNode node = (LoadFieldNode) n;
                AnalysisField field = (AnalysisField) node.field();
                assert field.isAccessed();
                if (node.getStackKind() == JavaKind.Object) {
                    TypeFlowBuilder<? extends LoadFieldTypeFlow> loadFieldBuilder;
                    if (node.isStatic()) {
                        loadFieldBuilder = TypeFlowBuilder.create(bb, node, LoadStaticFieldTypeFlow.class, () -> {
                            FieldTypeFlow fieldFlow = field.getStaticFieldFlow();
                            LoadStaticFieldTypeFlow loadFieldFLow = new LoadStaticFieldTypeFlow(node, fieldFlow);
                            methodFlow.addNodeFlow(bb, node, loadFieldFLow);
                            return loadFieldFLow;
                        });
                    } else {
                        TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                        loadFieldBuilder = TypeFlowBuilder.create(bb, node, LoadInstanceFieldTypeFlow.class, () -> {
                            LoadInstanceFieldTypeFlow loadFieldFLow = new LoadInstanceFieldTypeFlow(node, objectBuilder.get());
                            methodFlow.addNodeFlow(bb, node, loadFieldFLow);
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
                    AnalysisType arrayType = (AnalysisType) StampTool.typeOrNull(node.array());
                    AnalysisType nonNullArrayType = Optional.ofNullable(arrayType).orElseGet(bb::getObjectArrayType);

                    TypeFlowBuilder<?> loadIndexedBuilder = TypeFlowBuilder.create(bb, node, LoadIndexedTypeFlow.class, () -> {
                        LoadIndexedTypeFlow loadIndexedFlow = new LoadIndexedTypeFlow(node, nonNullArrayType, arrayBuilder.get(), methodFlow);
                        methodFlow.addNodeFlow(bb, node, loadIndexedFlow);
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
                assert node.object().getStackKind() == JavaKind.Object;

                checkUnsafeOffset(node.object(), node.offset());

                AnalysisType partitionType = (AnalysisType) node.partitionType();

                AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(node.object());
                assert bb.getGraalNodeType().isAssignableFrom(objectType);

                /* Use the Object type as a conservative type for the values loaded. */
                AnalysisType componentType = bb.getObjectType();

                TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                TypeFlowBuilder<?> unsafeLoadBuilder = TypeFlowBuilder.create(bb, node, UnsafePartitionLoadTypeFlow.class, () -> {
                    UnsafePartitionLoadTypeFlow loadTypeFlow = new UnsafePartitionLoadTypeFlow(node, objectType, componentType, objectBuilder.get(), methodFlow,
                                    node.unsafePartitionKind(), partitionType);
                    methodFlow.addMiscEntry(loadTypeFlow);
                    return loadTypeFlow;
                });
                unsafeLoadBuilder.addObserverDependency(objectBuilder);
                state.add(node, unsafeLoadBuilder);

            } else if (n instanceof UnsafePartitionStoreNode) {
                UnsafePartitionStoreNode node = (UnsafePartitionStoreNode) n;

                assert node.object().getStackKind() == JavaKind.Object;
                assert node.value().getStackKind() == JavaKind.Object;

                checkUnsafeOffset(node.object(), node.offset());

                AnalysisType partitionType = (AnalysisType) node.partitionType();

                AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(node.object());
                assert bb.getGraalNodeType().isAssignableFrom(objectType);

                /* Use the Object type as a conservative type for the values stored. */
                AnalysisType componentType = bb.getObjectType();

                AnalysisType valueType = (AnalysisType) StampTool.typeOrNull(node.value());
                assert valueType == null || bb.getGraalNodeType().isAssignableFrom(valueType) || bb.getGraalNodeListType().isAssignableFrom(valueType);

                TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                TypeFlowBuilder<?> valueBuilder = state.lookup(node.value());

                TypeFlowBuilder<?> unsafeStoreBuilder = TypeFlowBuilder.create(bb, node, UnsafePartitionStoreTypeFlow.class, () -> {
                    UnsafePartitionStoreTypeFlow storeTypeFlow = new UnsafePartitionStoreTypeFlow(node, objectType, componentType, objectBuilder.get(), valueBuilder.get(),
                                    node.partitionKind(), partitionType);
                    methodFlow.addMiscEntry(storeTypeFlow);
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
                    AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(node.object());
                    TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                    TypeFlowBuilder<?> loadBuilder;
                    if (objectType != null && objectType.isArray() && objectType.getComponentType().getJavaKind() == JavaKind.Object) {
                        /*
                         * Unsafe load from an array object is essentially an array load since we
                         * don't have separate type flows for different array elements.
                         */
                        loadBuilder = TypeFlowBuilder.create(bb, node, LoadIndexedTypeFlow.class, () -> {
                            LoadIndexedTypeFlow loadTypeFlow = new LoadIndexedTypeFlow(node, objectType, objectBuilder.get(), methodFlow);
                            methodFlow.addMiscEntry(loadTypeFlow);
                            return loadTypeFlow;
                        });
                    } else {
                        /*
                         * Use the Object type as a conservative approximation for both the receiver
                         * object type and the loaded values type.
                         */
                        AnalysisType nonNullObjectType = bb.getObjectType();
                        loadBuilder = TypeFlowBuilder.create(bb, node, UnsafeLoadTypeFlow.class, () -> {
                            UnsafeLoadTypeFlow loadTypeFlow = new UnsafeLoadTypeFlow(node, nonNullObjectType, nonNullObjectType, objectBuilder.get(), methodFlow);
                            methodFlow.addMiscEntry(loadTypeFlow);
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
                    AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(node.object());
                    TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                    TypeFlowBuilder<?> valueBuilder = state.lookup(node.value());
                    TypeFlowBuilder<?> storeBuilder;
                    if (objectType != null && objectType.isArray() && objectType.getComponentType().getJavaKind() == JavaKind.Object) {
                        /*
                         * Unsafe store to an array object is essentially an array store since we
                         * don't have separate type flows for different array elements.
                         */
                        storeBuilder = TypeFlowBuilder.create(bb, node, StoreIndexedTypeFlow.class, () -> {
                            StoreIndexedTypeFlow storeTypeFlow = new StoreIndexedTypeFlow(node, objectType, objectBuilder.get(), valueBuilder.get());
                            methodFlow.addMiscEntry(storeTypeFlow);
                            return storeTypeFlow;
                        });
                    } else {
                        /*
                         * Use the Object type as a conservative approximation for both the receiver
                         * object type and the stored values type.
                         */
                        AnalysisType nonNullObjectType = bb.getObjectType();
                        storeBuilder = TypeFlowBuilder.create(bb, node, UnsafeStoreTypeFlow.class, () -> {
                            UnsafeStoreTypeFlow storeTypeFlow = new UnsafeStoreTypeFlow(node, nonNullObjectType, nonNullObjectType, objectBuilder.get(), valueBuilder.get());
                            methodFlow.addMiscEntry(storeTypeFlow);
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
                checkUnsafeOffset(node.object(), node.offset());
                if (node.object().getStackKind() == JavaKind.Object && node.newValue().getStackKind() == JavaKind.Object) {
                    AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(node.object());
                    TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                    TypeFlowBuilder<?> newValueBuilder = state.lookup(node.newValue());
                    TypeFlowBuilder<?> storeBuilder;
                    if (objectType != null && objectType.isArray() && objectType.getComponentType().getJavaKind() == JavaKind.Object) {
                        /*
                         * Unsafe compare and swap is essentially unsafe store and unsafe store to
                         * an array object is essentially an array store since we don't have
                         * separate type flows for different array elements.
                         */
                        storeBuilder = TypeFlowBuilder.create(bb, node, StoreIndexedTypeFlow.class, () -> {
                            StoreIndexedTypeFlow storeTypeFlow = new StoreIndexedTypeFlow(node, objectType, objectBuilder.get(), newValueBuilder.get());
                            methodFlow.addMiscEntry(storeTypeFlow);
                            return storeTypeFlow;
                        });
                    } else {
                        /*
                         * Use the Object type as a conservative approximation for both the receiver
                         * object type and the swapped values type.
                         */
                        AnalysisType nonNullObjectType = bb.getObjectType();
                        storeBuilder = TypeFlowBuilder.create(bb, node, CompareAndSwapTypeFlow.class, () -> {
                            CompareAndSwapTypeFlow storeTypeFlow = new CompareAndSwapTypeFlow(node, nonNullObjectType, nonNullObjectType, objectBuilder.get(), newValueBuilder.get());
                            methodFlow.addMiscEntry(storeTypeFlow);
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
                    AnalysisType type = (AnalysisType) StampTool.typeOrNull(node.asNode());

                    TypeFlowBuilder<?> arrayCopyBuilder = TypeFlowBuilder.create(bb, node, ArrayCopyTypeFlow.class, () -> {
                        ArrayCopyTypeFlow arrayCopyFlow = new ArrayCopyTypeFlow(node.asNode(), type, srcBuilder.get(), dstBuilder.get());
                        methodFlow.addMiscEntry(arrayCopyFlow);
                        return arrayCopyFlow;
                    });

                    arrayCopyBuilder.addObserverDependency(srcBuilder);
                    arrayCopyBuilder.addObserverDependency(dstBuilder);

                    /* Array copies must not be removed. */
                    typeFlowGraphBuilder.registerSinkBuilder(arrayCopyBuilder);
                }

            } else if (n instanceof WordCastNode) {
                WordCastNode node = (WordCastNode) n;
                ValueNode input = node.getInput();

                if (input.getStackKind() == JavaKind.Object) {
                    /*
                     * The object-to-word operation converts an object into its address. The
                     * points-to analysis doesn't model object-to-word operations and they must be
                     * handled at a different level.
                     */
                } else {
                    /* Word-to-object: Any object can flow out from a low level memory read. */
                    TypeFlowBuilder<?> wordToObjectBuilder = TypeFlowBuilder.create(bb, node, WordToObjectTypeFlow.class, () -> {
                        /* Use the all-instantiated type flow. */
                        WordToObjectTypeFlow objectFlow = new WordToObjectTypeFlow(node, bb.getAllInstantiatedTypeFlow());
                        methodFlow.addMiscEntry(objectFlow);
                        return objectFlow;
                    });

                    state.add(node, wordToObjectBuilder);
                }

            } else if (n instanceof InvokeNode || n instanceof InvokeWithExceptionNode) {
                Invoke invoke = (Invoke) n;
                if (invoke.callTarget() instanceof MethodCallTargetNode) {
                    guarantee(bb.strengthenGraalGraphs() || invoke.stateAfter().outerFrameState() == null,
                                    "Outer FrameState of %s must be null, but was %s. A non-null outer FrameState indicates that a method inlining has happened, but inlining should only happen after analysis.",
                                    invoke.stateAfter(), invoke.stateAfter().outerFrameState());
                    MethodCallTargetNode target = (MethodCallTargetNode) invoke.callTarget();

                    processMethodInvocation(invoke.asFixedNode(), target.invokeKind(), invoke.bci(), (PointsToAnalysisMethod) target.targetMethod(), target.arguments());
                }

            } else if (n instanceof ObjectClone) {
                ObjectClone node = (ObjectClone) n;
                BytecodeLocation cloneLabel = bb.analysisPolicy().createAllocationSite(bb, node.bci(), methodFlow.getMethod());
                TypeFlowBuilder<?> inputBuilder = state.lookup(node.getObject());
                AnalysisType inputType = Optional.ofNullable((AnalysisType) StampTool.typeOrNull(node.getObject())).orElseGet(bb::getObjectType);

                TypeFlowBuilder<?> cloneBuilder = TypeFlowBuilder.create(bb, node, CloneTypeFlow.class, () -> {
                    CloneTypeFlow cloneFlow = new CloneTypeFlow(node.asNode(), inputType, cloneLabel, inputBuilder.get());
                    methodFlow.addMiscEntry(cloneFlow);
                    return cloneFlow;
                });
                cloneBuilder.addObserverDependency(inputBuilder);
                state.add(node.asFixedNode(), cloneBuilder);
            } else if (n instanceof MonitorEnterNode) {
                MonitorEnterNode node = (MonitorEnterNode) n;
                BytecodeLocation monitorLocation = BytecodeLocation.create(uniqueKey(node), methodFlow.getMethod());
                TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());

                TypeFlowBuilder<?> monitorEntryBuilder = TypeFlowBuilder.create(bb, node, MonitorEnterTypeFlow.class, () -> {
                    MonitorEnterTypeFlow monitorEntryFlow = new MonitorEnterTypeFlow(bb, node, monitorLocation, methodFlow.getMethod());
                    methodFlow.addMiscEntry(monitorEntryFlow);
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
                processMethodInvocation(n, node.getInvokeKind(), node.bci(), (PointsToAnalysisMethod) node.getTargetMethod(), node.getArguments());

            } else {
                delegateNodeProcessing(n, state);
            }
        }

        private void processMethodInvocation(ValueNode invoke, InvokeKind invokeKind, int bci, PointsToAnalysisMethod targetMethod, NodeInputList<ValueNode> arguments) {
            // check if the call is allowed
            AnalysisMethod callerMethod = methodFlow.getMethod();
            bb.isCallAllowed(bb, callerMethod, targetMethod, invoke.getNodeSourcePosition());

            Object key = uniqueKey(invoke);
            BytecodeLocation location = BytecodeLocation.create(key, methodFlow.getMethod());

            /*
             * Collect the parameters builders into an array so that we don't capture the `state`
             * reference in the closure.
             */
            boolean targetIsStatic = Modifier.isStatic(targetMethod.getModifiers());

            TypeFlowBuilder<?>[] actualParametersBuilders = new TypeFlowBuilder<?>[arguments.size()];
            for (int i = 0; i < actualParametersBuilders.length; i++) {
                ValueNode actualParam = arguments.get(i);
                if (actualParam.getStackKind() == JavaKind.Object) {
                    TypeFlowBuilder<?> paramBuilder = state.lookup(actualParam);
                    actualParametersBuilders[i] = paramBuilder;
                    paramBuilder.markAsBuildingAnActualParameter();
                    if (i == 0 && !targetIsStatic) {
                        paramBuilder.markAsBuildingAnActualReceiver();
                    }
                    /*
                     * Actual parameters must not be removed. They are linked when the callee is
                     * analyzed, hence, although they might not have any uses, cannot be removed
                     * during parsing.
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
                 * below only when the returned value is actually used, i.e., the actual return
                 * builder is materialized.
                 */
                ActualReturnTypeFlow actualReturn = null;
                /*
                 * Get the receiver type from the invoke, it may be more precise than the method
                 * declaring class.
                 */
                AnalysisType receiverType = null;
                if (invokeKind.hasReceiver()) {
                    receiverType = (AnalysisType) StampTool.typeOrNull(arguments.get(0));
                    if (receiverType == null) {
                        receiverType = targetMethod.getDeclaringClass();
                    }
                }

                /*
                 * The invokeLocation is used for all sorts of call stack printing (for error
                 * messages and diagnostics), so we must have a non-null BytecodePosition.
                 */
                BytecodePosition invokeLocation = invoke.getNodeSourcePosition();
                if (invokeLocation == null) {
                    invokeLocation = new BytecodePosition(null, invoke.graph().method(), bci);
                }

                InvokeTypeFlow invokeFlow = null;
                switch (invokeKind) {
                    case Static:
                        invokeFlow = new StaticInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
                        break;
                    case Special:
                        invokeFlow = bb.analysisPolicy().createSpecialInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
                        break;
                    case Virtual:
                    case Interface:
                        invokeFlow = bb.analysisPolicy().createVirtualInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
                        break;
                    default:
                        throw shouldNotReachHere();
                }

                methodFlow.addInvoke(key, invokeFlow);
                return invokeFlow;
            });

            if (invokeKind == InvokeKind.Special || invokeKind == InvokeKind.Virtual || invokeKind == InvokeKind.Interface) {
                invokeBuilder.addObserverDependency(actualParametersBuilders[0]);
            }

            if (invoke.asNode().getStackKind() == JavaKind.Object) {
                /* Create the actual return builder. */
                AnalysisType returnType = (AnalysisType) targetMethod.getSignature().getReturnType(null);
                TypeFlowBuilder<?> actualReturnBuilder = TypeFlowBuilder.create(bb, invoke.asNode(), ActualReturnTypeFlow.class, () -> {
                    InvokeTypeFlow invokeFlow = invokeBuilder.get();
                    ActualReturnTypeFlow actualReturn = new ActualReturnTypeFlow(invokeFlow.source, returnType);
                    methodFlow.addMiscEntry(actualReturn);
                    /*
                     * Only set the actual return in the invoke when it is materialized, i.e., it is
                     * used by other flows.
                     */
                    invokeFlow.setActualReturn(actualReturn);
                    actualReturn.setInvokeFlow(invokeFlow);
                    return actualReturn;
                });

                ObjectStamp stamp = (ObjectStamp) invoke.stamp(NodeView.DEFAULT);
                if (stamp.nonNull() && !returnType.equals(stamp.type()) && returnType.isAssignableFrom(stamp.type())) {
                    /*
                     * If the invoke stamp has a more precise type than the return type use that to
                     * filter the returned values. This can happen for example for MacroInvokable
                     * nodes when more concrete stamp information can be inferred for example from
                     * parameter types. In that case the Graal graph optimizations may decide to
                     * remove a checkcast that would normally follow the invoke, so we need to
                     * introduce the filter to avoid loosing precision.
                     */
                    TypeFlowBuilder<?> filterBuilder = TypeFlowBuilder.create(bb, invoke, FilterTypeFlow.class, () -> {
                        FilterTypeFlow filterFlow = new FilterTypeFlow(invoke, (AnalysisType) stamp.type(), stamp.isExactType(), true, true);
                        methodFlow.addMiscEntry(filterFlow);
                        return filterFlow;
                    });
                    filterBuilder.addUseDependency(actualReturnBuilder);
                    actualReturnBuilder = filterBuilder;
                }

                if (bb.strengthenGraalGraphs()) {
                    typeFlowGraphBuilder.registerSinkBuilder(actualReturnBuilder);
                }
                state.add(invoke.asNode(), actualReturnBuilder);
            }

            /* Invokes must not be removed. */
            typeFlowGraphBuilder.registerSinkBuilder(invokeBuilder);
        }

        /**
         * Model an unsafe-read-and-write operation.
         *
         * In the analysis this is used to model both {@link AtomicReadAndWriteNode}, i.e., an
         * atomic read-and-write operation like
         * {@code sun.misc.Unsafe#getAndSetObject(Object, long, Object)}, and a
         * {@link UnsafeCompareAndExchangeNode}, i.e., an atomic compare-and-swap operation like
         * jdk.internal.misc.Unsafe#compareAndExchangeObject(Object, long, Object, Object) where the
         * result is the current value of the memory location that was compared. The
         * jdk.internal.misc.Unsafe.compareAndExchangeObject(Object, long, Object, Object) operation
         * is similar to the
         * {@code sun.misc.Unsafe#compareAndSwapObject(Object, long, Object, Object)} operation.
         * However, from the analysis stand point in both the "expected" value is ignored, but
         * Unsafe.compareAndExchangeObject() returns the previous value, therefore it is equivalent
         * to the model for Unsafe.getAndSetObject().
         */
        private void modelUnsafeReadAndWriteFlow(ValueNode node, ValueNode object, ValueNode newValue, ValueNode offset) {
            assert node instanceof UnsafeCompareAndExchangeNode || node instanceof AtomicReadAndWriteNode;

            checkUnsafeOffset(object, offset);

            if (object.getStackKind() == JavaKind.Object && newValue.getStackKind() == JavaKind.Object) {
                AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(object);
                TypeFlowBuilder<?> objectBuilder = state.lookup(object);
                TypeFlowBuilder<?> newValueBuilder = state.lookup(newValue);

                TypeFlowBuilder<?> storeBuilder;
                TypeFlowBuilder<?> loadBuilder;

                if (objectType != null && objectType.isArray() && objectType.getComponentType().getJavaKind() == JavaKind.Object) {
                    /*
                     * Atomic read and write is essentially unsafe store and unsafe store to an
                     * array object is essentially an array store since we don't have separate type
                     * flows for different array elements.
                     */
                    storeBuilder = TypeFlowBuilder.create(bb, node, StoreIndexedTypeFlow.class, () -> {
                        StoreIndexedTypeFlow storeTypeFlow = new StoreIndexedTypeFlow(node, objectType, objectBuilder.get(), newValueBuilder.get());
                        methodFlow.addMiscEntry(storeTypeFlow);
                        return storeTypeFlow;
                    });

                    loadBuilder = TypeFlowBuilder.create(bb, node, LoadIndexedTypeFlow.class, () -> {
                        LoadIndexedTypeFlow loadTypeFlow = new LoadIndexedTypeFlow(node, objectType, objectBuilder.get(), methodFlow);
                        methodFlow.addMiscEntry(loadTypeFlow);
                        return loadTypeFlow;
                    });

                } else {
                    /*
                     * Use the Object type as a conservative approximation for both the receiver
                     * object type and the read/written values type.
                     */
                    AnalysisType nonNullObjectType = bb.getObjectType();
                    storeBuilder = TypeFlowBuilder.create(bb, node, AtomicWriteTypeFlow.class, () -> {
                        AtomicWriteTypeFlow storeTypeFlow = new AtomicWriteTypeFlow(node, nonNullObjectType, nonNullObjectType, objectBuilder.get(), newValueBuilder.get());
                        methodFlow.addMiscEntry(storeTypeFlow);
                        return storeTypeFlow;
                    });

                    loadBuilder = TypeFlowBuilder.create(bb, node, AtomicReadTypeFlow.class, () -> {
                        AtomicReadTypeFlow loadTypeFlow = new AtomicReadTypeFlow(node, nonNullObjectType, nonNullObjectType, objectBuilder.get(), methodFlow);
                        methodFlow.addMiscEntry(loadTypeFlow);
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
    protected void delegateNodeProcessing(FixedNode n, TypeFlowsOfNodes state) {
        // Hook for subclasses to do their own processing.
    }

    /**
     * This method returns a unique key for the given node. Unless the node comes from a
     * substitution, the unique key is the BCI of the node. Every
     * newinstance/newarray/newmultiarray/instanceof/checkcast node coming from a substitution
     * method cannot have a BCI. If one substitution has multiple nodes of the same type, then the
     * BCI would not be unique. In the later case the key is a unique object.
     */
    protected Object uniqueKey(Node node) {
        if (bb.strengthenGraalGraphs()) {
            return node;
        }

        NodeSourcePosition position = node.getNodeSourcePosition();
        // If the 'position' has a 'caller' then it is inlined, case in which the BCI is
        // probably not unique.
        if (position != null && position.getCaller() == null) {
            if (position.getBCI() >= 0) {
                return position.getBCI();
            }
        }
        return new Object();
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
        assert values.size() == objectStartIndex;
    }

    protected void processNewInstance(NewInstanceNode node, TypeFlowsOfNodes state) {
        /* Instance fields of a new object are initialized to null state in AnalysisField. */
        processNewInstance(node, (AnalysisType) node.instanceClass(), state);
    }

    protected void processNewArray(NewArrayNode node, TypeFlowsOfNodes state) {
        processNewInstance(node, ((AnalysisType) node.elementType()).getArrayClass(), state);
    }

    protected void processNewInstance(ValueNode node, AnalysisType type, TypeFlowsOfNodes state) {
        assert type.isInstantiated();
        Object key = uniqueKey(node);
        BytecodeLocation allocationLabel = bb.analysisPolicy().createAllocationSite(bb, key, method);

        TypeFlowBuilder<?> newInstanceBuilder = TypeFlowBuilder.create(bb, node, NewInstanceTypeFlow.class, () -> {
            NewInstanceTypeFlow newInstance = new NewInstanceTypeFlow(node, type, allocationLabel);
            methodFlow.addMiscEntry(newInstance);
            return newInstance;
        });
        state.add(node, newInstanceBuilder);
    }

    protected void processStoreField(StoreFieldNode node, TypeFlowsOfNodes state) {
        processStoreField(node, (AnalysisField) node.field(), node.object(), node.value(), state);
    }

    protected void processStoreField(ValueNode node, AnalysisField field, ValueNode object, ValueNode value, TypeFlowsOfNodes state) {
        assert field.isWritten();
        if (value.getStackKind() == JavaKind.Object) {
            TypeFlowBuilder<?> valueBuilder = state.lookup(value);

            TypeFlowBuilder<StoreFieldTypeFlow> storeFieldBuilder;
            if (field.isStatic()) {
                storeFieldBuilder = TypeFlowBuilder.create(bb, node, StoreFieldTypeFlow.class, () -> {
                    FieldTypeFlow fieldFlow = field.getStaticFieldFlow();
                    StoreStaticFieldTypeFlow storeFieldFlow = new StoreStaticFieldTypeFlow(node, field, valueBuilder.get(), fieldFlow);
                    methodFlow.addMiscEntry(storeFieldFlow);
                    return storeFieldFlow;
                });
                storeFieldBuilder.addUseDependency(valueBuilder);
            } else {
                TypeFlowBuilder<?> objectBuilder = state.lookup(object);
                storeFieldBuilder = TypeFlowBuilder.create(bb, node, StoreFieldTypeFlow.class, () -> {
                    StoreInstanceFieldTypeFlow storeFieldFlow = new StoreInstanceFieldTypeFlow(node, field, valueBuilder.get(), objectBuilder.get());
                    methodFlow.addMiscEntry(storeFieldFlow);
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
            AnalysisType arrayType = (AnalysisType) StampTool.typeOrNull(array);
            AnalysisType nonNullArrayType = Optional.ofNullable(arrayType).orElseGet(bb::getObjectArrayType);
            TypeFlowBuilder<?> arrayBuilder = state.lookup(array);
            TypeFlowBuilder<?> valueBuilder = state.lookup(value);
            TypeFlowBuilder<?> storeIndexedBuilder = TypeFlowBuilder.create(bb, node, StoreIndexedTypeFlow.class, () -> {
                StoreIndexedTypeFlow storeIndexedFlow = new StoreIndexedTypeFlow(node, nonNullArrayType, arrayBuilder.get(), valueBuilder.get());
                methodFlow.addMiscEntry(storeIndexedFlow);
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
