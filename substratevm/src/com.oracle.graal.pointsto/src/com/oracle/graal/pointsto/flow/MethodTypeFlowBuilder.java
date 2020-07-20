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

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
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
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
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
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.graph.MergeableState;
import org.graalvm.compiler.phases.graph.PostOrderNodeIterator;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopy;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.ObjectClone;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.word.WordCastNode;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
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
import com.oracle.graal.pointsto.infrastructure.GraphProvider.Purpose;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.nodes.AnalysisArraysCopyOfNode;
import com.oracle.graal.pointsto.nodes.AnalysisUnsafePartitionLoadNode;
import com.oracle.graal.pointsto.nodes.AnalysisUnsafePartitionStoreNode;
import com.oracle.graal.pointsto.nodes.ConvertUnknownValueNode;
import com.oracle.graal.pointsto.phases.SubstrateIntrinsicGraphBuilder;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.runtime.JVMCI;

public class MethodTypeFlowBuilder {

    protected final BigBang bb;
    protected final MethodTypeFlow methodFlow;
    protected final AnalysisMethod method;
    protected StructuredGraph graph;
    private NodeBitMap processedNodes;
    private Map<PhiNode, TypeFlowBuilder<?>> loopPhiFlows;

    private final TypeFlowGraphBuilder typeFlowGraphBuilder;

    public MethodTypeFlowBuilder(BigBang bb, MethodTypeFlow methodFlow) {
        this.bb = bb;
        this.methodFlow = methodFlow;
        this.method = methodFlow.getMethod();
        typeFlowGraphBuilder = new TypeFlowGraphBuilder(bb);
    }

    public MethodTypeFlowBuilder(BigBang bb, StructuredGraph graph) {
        this.bb = bb;
        this.graph = graph;
        this.method = (AnalysisMethod) graph.method();
        this.methodFlow = method.getTypeFlow();
        this.typeFlowGraphBuilder = null;
    }

    @SuppressWarnings("try")
    private boolean parse() {
        OptionValues options = bb.getOptions();
        GraalJVMCICompiler compiler = (GraalJVMCICompiler) JVMCI.getRuntime().getCompiler();
        SnippetReflectionProvider snippetReflection = compiler.getGraalRuntime().getRequiredCapability(SnippetReflectionProvider.class);
        // Use the real SnippetReflectionProvider for dumping
        Description description = new Description(method, toString());
        DebugContext debug = new Builder(options, new GraalDebugHandlersFactory(snippetReflection)).description(description).build();
        try (Indent indent = debug.logAndIndent("parse graph %s", method)) {

            boolean needParsing = false;
            graph = method.buildGraph(debug, method, bb.getProviders(), Purpose.ANALYSIS);
            if (graph == null) {
                InvocationPlugin plugin = bb.getProviders().getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(method);
                if (plugin != null && !plugin.inlineOnly()) {
                    Bytecode code = new ResolvedJavaMethodBytecode(method);
                    graph = new SubstrateIntrinsicGraphBuilder(options, debug, bb.getProviders(), code).buildGraph(plugin);
                    if (graph != null) {
                        method.registerAsIntrinsicMethod();
                    }
                }
            }
            if (graph == null) {
                if (!method.hasBytecodes()) {
                    return false;
                }
                needParsing = true;
                graph = new StructuredGraph.Builder(options, debug).method(method).build();
            }

            try (DebugContext.Scope s = debug.scope("ClosedWorldAnalysis", graph, method, this)) {

                // enable this logging to get log output in compilation passes
                try (Indent indent2 = debug.logAndIndent("parse graph phases")) {

                    if (needParsing) {
                        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(bb.getProviders().getGraphBuilderPlugins()).withEagerResolving(true)
                                        .withUnresolvedIsError(PointstoOptions.UnresolvedIsError.getValue(bb.getOptions()))
                                        .withNodeSourcePosition(true).withBytecodeExceptionMode(BytecodeExceptionMode.CheckAll);

                        /*
                         * We want to always disable the liveness analysis, since we want the
                         * points-to analysis to be as conservative as possible. The analysis
                         * results can then be used with the liveness analysis enabled or disabled.
                         */
                        config = config.withRetainLocalVariables(true);

                        bb.getHostVM().createGraphBuilderPhase(bb.getProviders(), config, OptimisticOptimizations.NONE, null).apply(graph);
                    }
                } catch (PermanentBailoutException ex) {
                    bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, ex.getLocalizedMessage(), null, ex);
                    return false;
                }

                // Register used types and fields before canonicalization can optimize them.
                registerUsedElements(false);

                CanonicalizerPhase.create().apply(graph, bb.getProviders());

                // Do it again after canonicalization changed type checks and field accesses.
                registerUsedElements(true);

            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }
        return true;
    }

    public void registerUsedElements(boolean registerEmbeddedRoots) {
        for (Node n : graph.getNodes()) {
            if (n instanceof InstanceOfNode) {
                InstanceOfNode node = (InstanceOfNode) n;
                AnalysisType type = (AnalysisType) node.type().getType();
                type.registerAsInTypeCheck();

            } else if (n instanceof NewInstanceNode) {
                NewInstanceNode node = (NewInstanceNode) n;
                AnalysisType type = (AnalysisType) node.instanceClass();
                type.registerAsAllocated(node);

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

            } else if (n instanceof StoreIndexedNode) {
                StoreIndexedNode node = (StoreIndexedNode) n;
                AnalysisType arrayType = (AnalysisType) StampTool.typeOrNull(node.array());
                if (arrayType != null) {
                    assert arrayType.isArray();
                    arrayType.getComponentType().registerAsInTypeCheck();
                }

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

            } else if (n instanceof ForeignCallNode) {
                ForeignCallNode node = (ForeignCallNode) n;
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
        if (bb.scanningPolicy().trackConstant(bb, cn.asJavaConstant())) {
            BytecodePosition position = cn.getNodeSourcePosition();
            if (position == null) {
                position = new BytecodePosition(null, method, 0);
            }
            bb.getUniverse().registerEmbeddedRoot(cn.asJavaConstant(), position);
        }
    }

    private static void registerForeignCall(BigBang bb, ForeignCallDescriptor foreignCallDescriptor) {
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

        bb.getHostVM().checkMethod(bb, method, graph);

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
                }
            } else if (n instanceof BoxNode) {
                BoxNode node = (BoxNode) n;
                Object key = uniqueKey(node);
                BytecodeLocation boxSite = bb.analysisPolicy().createAllocationSite(bb, key, methodFlow.getMethod());
                AnalysisType type = (AnalysisType) StampTool.typeOrNull(node);

                TypeFlowBuilder<?> boxBuilder = TypeFlowBuilder.create(bb, node, BoxTypeFlow.class, () -> {
                    BoxTypeFlow boxFlow = new BoxTypeFlow(node, type, boxSite);
                    methodFlow.addAllocation(boxFlow);
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
                    if (node.asJavaConstant().isNull()) {
                        TypeFlowBuilder<SourceTypeFlow> sourceBuilder = TypeFlowBuilder.create(bb, node, SourceTypeFlow.class, () -> {
                            SourceTypeFlow constantSource = new SourceTypeFlow(node, TypeState.forNull());
                            methodFlow.addSource(constantSource);
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
                            methodFlow.addSource(constantSource);
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
                        methodFlow.addSource(src);
                        return src;
                    });

                } else {
                    /*
                     * Use a type state which consists of all allocated types (which are compatible
                     * to the node's type). Is is a conservative assumption.
                     */
                    AnalysisType type = (AnalysisType) (stamp.type() == null ? bb.getObjectType() : stamp.type());

                    if (type.isJavaLangObject()) {
                        /* Return a proxy to the unknown type flow. */
                        result = TypeFlowBuilder.create(bb, node, ProxyTypeFlow.class, () -> {
                            ProxyTypeFlow proxy = new ProxyTypeFlow(node, bb.getUnknownTypeFlow());
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
                    methodFlow.addMiscEntry(nullCheckFlow);
                    return nullCheckFlow;
                });
                nullCheckBuilder.addUseDependency(inputBuilder);
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
                if (instanceOfPosition != null && instanceOfPosition.getBCI() >= 0) {
                    /*
                     * An InstanceOf with negative BCI is not useful. This can happen for example
                     * for instanceof bytecodes for exception unwind. However, the filtering below
                     * is still useful for other further operations in the exception handler.
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
                    methodFlow.addMiscEntry(filterFlow);
                    return filterFlow;
                });
                filterBuilder.addUseDependency(objectBuilder);
                state.update(object, filterBuilder);
            }
        }

        /**
         * Get the type flow of a dynamic type. If the type results from a GetClassNode or a
         * JavaConstant this method will return accurate type information. Otherwise it will return
         * all instantiated types or, if it is an array, all instantiated array types.
         */
        protected TypeFlowBuilder<?> getDynamicTypeFlow(ValueNode node, ValueNode typeSource, boolean isArrayType) {

            TypeFlowBuilder<?> dynamicTypeBuilder;
            if (typeSource instanceof GetClassNode) {
                GetClassNode getClassNode = (GetClassNode) typeSource;
                dynamicTypeBuilder = state.lookup(getClassNode.getObject());

            } else if (typeSource.isConstant()) {
                assert state.lookup(typeSource).getFlowClass() == SourceTypeFlow.class;

                Constant constant = typeSource.asJavaConstant();
                AnalysisType exactType = (AnalysisType) bb.getProviders().getConstantReflection().asJavaType(constant);
                exactType.registerAsAllocated(node);

                dynamicTypeBuilder = TypeFlowBuilder.create(bb, node, SourceTypeFlow.class, () -> {
                    SourceTypeFlow dynamicTypeFlow = new SourceTypeFlow(node, TypeState.forExactType(bb, exactType, false));
                    methodFlow.addMiscEntry(dynamicTypeFlow);
                    return dynamicTypeFlow;
                });
            } else {
                /*
                 * Without precise type information either the type flow corresponding to all
                 * instantiated object types or all instantiated array types will be returned.
                 */
                AnalysisType arrayType = isArrayType ? bb.getObjectArrayType() : bb.getObjectType();
                dynamicTypeBuilder = TypeFlowBuilder.create(bb, node, AllInstantiatedTypeFlow.class, () -> {
                    return arrayType.getTypeFlow(bb, false);
                });
            }

            return dynamicTypeBuilder;
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
                    handleCondition(ifNode, ifNode.condition(), node == ifNode.trueSuccessor());
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
                     * generate a heap object for each instantiated type. This means that the source
                     * flow for dynamic new instance is 'close to all instantiated' and will be
                     * reduced to abstract objects, unless
                     * BootImageAnalysisOptions.ReduceCloseToAllInstantiatedFlows is disabled.
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
                    methodFlow.addDynamicAllocation(newInstanceTypeFlow);
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
                 */
                AnalysisType arrayType = bb.getObjectArrayType();

                Object key = uniqueKey(node);
                BytecodeLocation allocationLabel = bb.analysisPolicy().createAllocationSite(bb, key, method);

                TypeFlowBuilder<DynamicNewInstanceTypeFlow> dynamicNewArrayBuilder = TypeFlowBuilder.create(bb, node, DynamicNewInstanceTypeFlow.class, () -> {
                    DynamicNewInstanceTypeFlow newArrayTypeFlow = new DynamicNewInstanceTypeFlow(arrayType.getTypeFlow(bb, false), arrayType, node, allocationLabel);
                    methodFlow.addDynamicAllocation(newArrayTypeFlow);
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
                    methodFlow.addAllocation(newArray);
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
                            methodFlow.addFieldLoad(loadFieldFLow);
                            return loadFieldFLow;
                        });
                    } else {
                        TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                        loadFieldBuilder = TypeFlowBuilder.create(bb, node, LoadInstanceFieldTypeFlow.class, () -> {
                            LoadInstanceFieldTypeFlow loadFieldFLow = new LoadInstanceFieldTypeFlow(node, objectBuilder.get());
                            methodFlow.addFieldLoad(loadFieldFLow);
                            return loadFieldFLow;
                        });
                        loadFieldBuilder.addObserverDependency(objectBuilder);
                    }
                    state.add(node, loadFieldBuilder);
                }

            } else if (n instanceof StoreFieldNode) { // object.field = value
                StoreFieldNode node = (StoreFieldNode) n;
                AnalysisField field = (AnalysisField) node.field();
                assert field.isWritten();
                if (node.value().getStackKind() == JavaKind.Object) {
                    TypeFlowBuilder<?> valueBuilder = state.lookup(node.value());

                    TypeFlowBuilder<StoreFieldTypeFlow> storeFieldBuilder;
                    if (node.isStatic()) {
                        storeFieldBuilder = TypeFlowBuilder.create(bb, node, StoreFieldTypeFlow.class, () -> {
                            FieldTypeFlow fieldFlow = field.getStaticFieldFlow();
                            StoreStaticFieldTypeFlow storeFieldFlow = new StoreStaticFieldTypeFlow(node, valueBuilder.get(), fieldFlow);
                            methodFlow.addMiscEntry(storeFieldFlow);
                            return storeFieldFlow;
                        });
                        storeFieldBuilder.addUseDependency(valueBuilder);
                    } else {
                        TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());
                        storeFieldBuilder = TypeFlowBuilder.create(bb, node, StoreFieldTypeFlow.class, () -> {
                            StoreInstanceFieldTypeFlow storeFieldFlow = new StoreInstanceFieldTypeFlow(node, valueBuilder.get(), objectBuilder.get());
                            methodFlow.addMiscEntry(storeFieldFlow);
                            return storeFieldFlow;
                        });
                        storeFieldBuilder.addUseDependency(valueBuilder);
                        storeFieldBuilder.addObserverDependency(objectBuilder);
                    }
                    /* Field stores must not be removed. */
                    typeFlowGraphBuilder.registerSinkBuilder(storeFieldBuilder);
                }

            } else if (n instanceof LoadIndexedNode) {
                LoadIndexedNode node = (LoadIndexedNode) n;
                TypeFlowBuilder<?> arrayBuilder = state.lookup(node.array());
                if (node.getStackKind() == JavaKind.Object) {
                    AnalysisType arrayType = (AnalysisType) StampTool.typeOrNull(node.array());
                    AnalysisType nonNullArrayType = Optional.ofNullable(arrayType).orElseGet(bb::getObjectArrayType);

                    TypeFlowBuilder<?> loadIndexedBuilder = TypeFlowBuilder.create(bb, node, LoadIndexedTypeFlow.class, () -> {
                        LoadIndexedTypeFlow loadIndexedFlow = new LoadIndexedTypeFlow(node, nonNullArrayType, arrayBuilder.get(), methodFlow);
                        methodFlow.addIndexedLoad(loadIndexedFlow);
                        return loadIndexedFlow;
                    });

                    loadIndexedBuilder.addObserverDependency(arrayBuilder);
                    state.add(node, loadIndexedBuilder);
                }

            } else if (n instanceof StoreIndexedNode) {
                StoreIndexedNode node = (StoreIndexedNode) n;
                if (node.value().getStackKind() == JavaKind.Object) {
                    AnalysisType arrayType = (AnalysisType) StampTool.typeOrNull(node.array());
                    AnalysisType nonNullArrayType = Optional.ofNullable(arrayType).orElseGet(bb::getObjectArrayType);
                    TypeFlowBuilder<?> arrayBuilder = state.lookup(node.array());
                    TypeFlowBuilder<?> valueBuilder = state.lookup(node.value());
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

            } else if (n instanceof AnalysisUnsafePartitionLoadNode) {
                AnalysisUnsafePartitionLoadNode node = (AnalysisUnsafePartitionLoadNode) n;
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

            } else if (n instanceof AnalysisUnsafePartitionStoreNode) {
                AnalysisUnsafePartitionStoreNode node = (AnalysisUnsafePartitionStoreNode) n;

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

            } else if (n instanceof ArrayCopy) {
                ArrayCopy node = (ArrayCopy) n;

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
                        /* Use the unknown type flow. */
                        TypeFlow<?> unknown = bb.getUnknownTypeFlow();
                        WordToObjectTypeFlow objectFlow = new WordToObjectTypeFlow(node, unknown);
                        methodFlow.addMiscEntry(objectFlow);
                        return objectFlow;
                    });

                    state.add(node, wordToObjectBuilder);
                }

            } else if (n instanceof AnalysisArraysCopyOfNode) {
                AnalysisArraysCopyOfNode node = (AnalysisArraysCopyOfNode) n;
                ValueNode original = node.getOriginal();
                ValueNode newArrayType = node.getNewArrayType();

                TypeFlowBuilder<?> originalArrayBuilder = state.lookup(original);

                Object key = uniqueKey(node);
                BytecodeLocation newArrayLabel = bb.analysisPolicy().createAllocationSite(bb, key, methodFlow.getMethod());

                /*
                 * Arrays.copyOf is essentially a dynamic new instance plus a System.arrayCopy.
                 */

                /*
                 * First we determine the type of the new array and create a dynamic new instance
                 * flow to allocate it.
                 */
                TypeFlowBuilder<?> newArrayTypeBuilder;
                if (newArrayType == null) {
                    /*
                     * The type of the new array and that of the original are the same. The dynamic
                     * new instance flow will create a new heap object for each incoming type and
                     * for each different heap context.
                     */
                    newArrayTypeBuilder = originalArrayBuilder;
                } else {
                    /*
                     * The type of the new array and that of the original are different. The
                     * elements are copied from the original array but the type of the resulting
                     * objects are given by the new type. Both the original 'values' and the 'new
                     * type' are needed for a proper intercept when the analysis is context
                     * sensitive.
                     */
                    newArrayTypeBuilder = getDynamicTypeFlow(node, newArrayType, true);
                }

                TypeFlowBuilder<?> newArrayBuilder = TypeFlowBuilder.create(bb, node, DynamicNewInstanceTypeFlow.class, () -> {
                    DynamicNewInstanceTypeFlow newArrayFlow = new DynamicNewInstanceTypeFlow(newArrayTypeBuilder.get(), bb.getObjectArrayType(), node, newArrayLabel);
                    methodFlow.addDynamicAllocation(newArrayFlow);
                    return newArrayFlow;
                });

                if (newArrayType != null && (newArrayType instanceof GetClassNode || newArrayType.isConstant())) {
                    newArrayBuilder.addObserverDependency(newArrayTypeBuilder);
                }

                state.add(node, newArrayBuilder);

                /*
                 * Then we use an array copy type flow to propagate the elements type state from the
                 * original array into the copy.
                 */
                TypeFlowBuilder<?> arrayCopyBuilder = TypeFlowBuilder.create(bb, node, ArrayCopyTypeFlow.class, () -> {
                    ArrayCopyTypeFlow arrayCopyFlow = new ArrayCopyTypeFlow(node, null, originalArrayBuilder.get(), newArrayBuilder.get());
                    methodFlow.addMiscEntry(arrayCopyFlow);
                    return arrayCopyFlow;
                });

                arrayCopyBuilder.addObserverDependency(originalArrayBuilder);
                arrayCopyBuilder.addObserverDependency(newArrayBuilder);

                /* Array copies must not be removed. */
                typeFlowGraphBuilder.registerSinkBuilder(arrayCopyBuilder);

            } else if (n instanceof InvokeNode || n instanceof InvokeWithExceptionNode) {
                Invoke invoke = (Invoke) n;
                if (invoke.callTarget() instanceof MethodCallTargetNode) {
                    guarantee(invoke.stateAfter().outerFrameState() == null, "Outer FrameState must not be null.");

                    MethodCallTargetNode target = (MethodCallTargetNode) invoke.callTarget();

                    // check if the call is allowed
                    AnalysisMethod callerMethod = methodFlow.getMethod();
                    AnalysisMethod targetMethod = (AnalysisMethod) target.targetMethod();
                    bb.isCallAllowed(bb, callerMethod, targetMethod, target.getNodeSourcePosition());

                    Object key = uniqueKey(n);
                    BytecodeLocation location = BytecodeLocation.create(key, methodFlow.getMethod());

                    /*
                     * Collect the parameters builders into an array so that we don't capture the
                     * `state` reference in the closure.
                     */
                    boolean targetIsStatic = Modifier.isStatic(targetMethod.getModifiers());

                    TypeFlowBuilder<?>[] actualParametersBuilders = new TypeFlowBuilder<?>[target.arguments().size()];
                    for (int i = 0; i < actualParametersBuilders.length; i++) {
                        ValueNode actualParam = target.arguments().get(i);
                        if (actualParam.getStackKind() == JavaKind.Object) {
                            TypeFlowBuilder<?> paramBuilder = state.lookup(actualParam);
                            actualParametersBuilders[i] = paramBuilder;
                            paramBuilder.markAsBuildingAnActualParameter();
                            if (i == 0 && !targetIsStatic) {
                                paramBuilder.markAsBuildingAnActualReceiver();
                            }
                            /*
                             * Actual parameters must not be removed. They are linked when the
                             * callee is analyzed, hence, although they might not have any uses,
                             * cannot be removed during parsing.
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
                         * Initially the actual return is null. It will be set by the actual return
                         * builder bellow only when the returned value is actually used, i.e., the
                         * actual return builder is materialized.
                         */
                        ActualReturnTypeFlow actualReturn = null;
                        /*
                         * Get the receiver type from the invoke, it may be more precise than the
                         * method declaring class.
                         */
                        AnalysisType receiverType = invoke.getInvokeKind().hasReceiver() ? (AnalysisType) invoke.getReceiverType() : null;
                        BytecodePosition invokeLocation = InvokeTypeFlow.findBytecodePosition(invoke);
                        InvokeTypeFlow invokeFlow = null;
                        switch (target.invokeKind()) {
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

                    if (target.invokeKind() == InvokeKind.Special || target.invokeKind() == InvokeKind.Virtual || target.invokeKind() == InvokeKind.Interface) {
                        invokeBuilder.addObserverDependency(actualParametersBuilders[0]);
                    }

                    if (invoke.asNode().getStackKind() == JavaKind.Object) {
                        /* Create the actual return builder. */
                        AnalysisType returnType = (AnalysisType) target.targetMethod().getSignature().getReturnType(null);
                        TypeFlowBuilder<?> actualReturnBuilder = TypeFlowBuilder.create(bb, invoke.asNode(), ActualReturnTypeFlow.class, () -> {
                            ActualReturnTypeFlow actualReturn = new ActualReturnTypeFlow(invoke.asNode(), returnType);
                            methodFlow.addMiscEntry(actualReturn);
                            /*
                             * Only set the actual return in the invoke when it is materialized,
                             * i.e., it is used by other flows.
                             */
                            InvokeTypeFlow invokeFlow = invokeBuilder.get();
                            invokeFlow.setActualReturn(actualReturn);
                            actualReturn.setInvokeFlow(invokeFlow);
                            return actualReturn;
                        });
                        state.add(invoke.asNode(), actualReturnBuilder);
                    }

                    /* Invokes must not be removed. */
                    typeFlowGraphBuilder.registerSinkBuilder(invokeBuilder);
                    state.add(target, invokeBuilder);
                }

            } else if (n instanceof ObjectClone) {
                ObjectClone node = (ObjectClone) n;
                BytecodeLocation cloneLabel = bb.analysisPolicy().createAllocationSite(bb, node.bci(), methodFlow.getMethod());
                TypeFlowBuilder<?> inputBuilder = state.lookup(node.getObject());
                AnalysisType inputType = (AnalysisType) StampTool.typeOrNull(node.getObject());

                TypeFlowBuilder<?> cloneBuilder = TypeFlowBuilder.create(bb, node, CloneTypeFlow.class, () -> {
                    CloneTypeFlow cloneFlow = new CloneTypeFlow(node.asNode(), inputType, cloneLabel, inputBuilder.get());
                    methodFlow.addClone(cloneFlow);
                    return cloneFlow;
                });
                cloneBuilder.addObserverDependency(inputBuilder);
                state.add(node.asNode(), cloneBuilder);
            } else if (n instanceof MonitorEnterNode) {
                MonitorEnterNode node = (MonitorEnterNode) n;
                BytecodeLocation monitorLocation = BytecodeLocation.create(uniqueKey(node), methodFlow.getMethod());
                TypeFlowBuilder<?> objectBuilder = state.lookup(node.object());

                TypeFlowBuilder<?> monitorEntryBuilder = TypeFlowBuilder.create(bb, node, MonitorEnterTypeFlow.class, () -> {
                    MonitorEnterTypeFlow monitorEntryFlow = new MonitorEnterTypeFlow(bb, node, monitorLocation, methodFlow.getMethod());
                    methodFlow.addMonitorEntryFlow(monitorEntryFlow);
                    return monitorEntryFlow;
                });
                monitorEntryBuilder.addUseDependency(objectBuilder);
                /* Monitor enters must not be removed. */
                typeFlowGraphBuilder.registerSinkBuilder(monitorEntryBuilder);
            } else if (n instanceof ConvertUnknownValueNode) {
                ConvertUnknownValueNode node = (ConvertUnknownValueNode) n;

                /*
                 * Wire the all-instantiated type flow, of either the Object type or a more concrete
                 * sub-type if precise type information is available, to the uses of this node.
                 */
                AnalysisType nodeType = (AnalysisType) StampTool.typeOrNull(node);
                TypeFlowBuilder<?> resultBuilder = TypeFlowBuilder.create(bb, node, ConvertUnknownValueTypeFlow.class, () -> {
                    ConvertUnknownValueTypeFlow resultFlow = new ConvertUnknownValueTypeFlow(node, nodeType.getTypeFlow(bb, true));
                    methodFlow.addMiscEntry(resultFlow);
                    return resultFlow;
                });

                state.add(node, resultBuilder);
            } else {
                delegateNodeProcessing(n, state);
            }
        }

        /**
         * Model an unsafe-read-and-write operation.
         *
         * In the analysis this is used to model both {@link AtomicReadAndWriteNode}, i.e., an
         * atomic read-and-write operation like
         * {@link sun.misc.Unsafe#getAndSetObject(Object, long, Object)}, and a
         * {@link UnsafeCompareAndExchangeNode}, i.e., an atomic compare-and-swap operation like
         * jdk.internal.misc.Unsafe#compareAndExchangeObject(Object, long, Object, Object) where the
         * result is the current value of the memory location that was compared. The
         * jdk.internal.misc.Unsafe.compareAndExchangeObject(Object, long, Object, Object) operation
         * is similar to the
         * {@link sun.misc.Unsafe#compareAndSwapObject(Object, long, Object, Object)} operation.
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
    protected static Object uniqueKey(Node node) {
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

    protected void processNewInstance(NewInstanceNode node, TypeFlowsOfNodes state) {

        AnalysisType type = (AnalysisType) node.instanceClass();
        assert type.isInstantiated();
        Object key = uniqueKey(node);
        BytecodeLocation allocationLabel = bb.analysisPolicy().createAllocationSite(bb, key, method);

        TypeFlowBuilder<?> newInstanceBuilder = TypeFlowBuilder.create(bb, node, NewInstanceTypeFlow.class, () -> {
            NewInstanceTypeFlow newInstance = createNewInstanceTypeFlow(node, type, allocationLabel);
            /* Instance fields of a new object are initialized to null state in AnalysisField. */
            methodFlow.addAllocation(newInstance);
            return newInstance;
        });
        state.add(node, newInstanceBuilder);
    }

    protected NewInstanceTypeFlow createNewInstanceTypeFlow(NewInstanceNode node, AnalysisType type, BytecodeLocation allocationLabel) {
        return new NewInstanceTypeFlow(node, type, allocationLabel);
    }

    protected void processNewArray(NewArrayNode node, TypeFlowsOfNodes state) {
        AnalysisType type = ((AnalysisType) node.elementType()).getArrayClass();
        assert type.isInstantiated();

        Object key = uniqueKey(node);
        BytecodeLocation allocationLabel = bb.analysisPolicy().createAllocationSite(bb, key, method);

        TypeFlowBuilder<?> newArrayBuilder = TypeFlowBuilder.create(bb, node, NewInstanceTypeFlow.class, () -> {
            NewInstanceTypeFlow newArray = createNewArrayTypeFlow(node, type, allocationLabel);
            methodFlow.addAllocation(newArray);
            return newArray;
        });
        state.add(node, newArrayBuilder);
    }

    protected NewInstanceTypeFlow createNewArrayTypeFlow(NewArrayNode node, AnalysisType type, BytecodeLocation allocationLabel) {
        return new NewInstanceTypeFlow(node, type, allocationLabel);
    }

    /** Hook for unsafe offset value checks. */
    protected void checkUnsafeOffset(@SuppressWarnings("unused") ValueNode base, @SuppressWarnings("unused") ValueNode offset) {
    }

}
