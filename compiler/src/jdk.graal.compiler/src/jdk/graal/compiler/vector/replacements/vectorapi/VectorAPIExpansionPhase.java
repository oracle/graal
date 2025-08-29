/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.graph.NodeUnionFind;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.MinMaxNode;
import jdk.graal.compiler.nodes.extended.FixedValueAnchorNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectState;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.replacements.nodes.MacroWithExceptionNode;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIMacroNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPISinkNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.collections.Equivalence;

/**
 * Expands {@link VectorAPIMacroNode}s to SIMD operations if they are supported by the target
 * architecture. For example, for input code like:
 *
 * <pre>
 * IntVector.fromArray(IntVector.SPECIES_128, source, 0).add(y).intoArray(dest, 0);
 * </pre>
 *
 * this phase performs a transformation like the following:
 *
 * <pre>
 *                        y                                           y
 *                        |                                           |
 * VectorAPILoad     VectorAPIFromBitsCoerced     ===>    Read   SimdBroadcast
 *            \          /                                    \ /
 *         VectorAPIBinaryOp +                                 +
 *                  |                                          |
 *           VectorAPIStore                                  Write
 * </pre>
 *
 * where the new read, add, and broadcast nodes have a {@code <i32, i32, i32, i32>}
 * {@link SimdStamp}. Each macro maps to one or a few new nodes, which will then map directly to the
 * target's SIMD instructions.
 * <p/>
 *
 * Expansion is only done if an entire computation graph can be converted to SIMD code. That is, all
 * vector inputs and all usages of macro nodes must be expanded together or not at all. For this
 * purpose, this phase groups nodes into {@linkplain ConnectedComponent connected components}, with
 * all macro nodes linked by input/usage relationships in the same component. Nodes inside a
 * component may also be linked through phis or proxies if the computation involves control flow
 * (typically loops). Inputs to the component may also be constants representing Vector API values,
 * or {@linkplain VectorAPIBoxingUtils#asUnboxableVectorType unboxable values} like certain field
 * reads or parameters.
 * <p/>
 *
 * Vector values produced inside a component must only be used inside the component. For example, a
 * vector value flowing to a return or a field store is not accepted. Any such usage would force us
 * to materialize the vector value as a heap object, with large allocation costs. C2 seems to have
 * heuristics for trying to determine when such boxing may be beneficial. We can explore this if a
 * compelling use case comes along, but for simplicity let's reject any such boxing cases for now.
 * <p/>
 *
 * Usages in frame states can also cause boxing if the program deoptimizes with a vector in the
 * state. For such cases we build a virtual instance representing the heap object in the state. The
 * VM will materialize the virtual object as an actual heap object only if an actual deoptimization
 * happens.
 * <p/>
 *
 * After building connected components and checking the above restrictions, this phase determines if
 * all macros in a component {@linkplain VectorAPIMacroNode#canExpand can be expanded to SIMD code}
 * and if yes, {@linkplain VectorAPIMacroNode#expand performs the expansion}. Any macro nodes that
 * are part of a component which is not well-formed or which contains non-expandable nodes will
 * remain as a macro. Later lowering will transform them back into invokes.
 * <p/>
 *
 * The work of this phase is structured as multiple linear-time passes over the macro nodes, plus
 * some connecting nodes like phis, in the graph. Each node expansion removes the original node and
 * produces one new node, or at most a few (e.g., a core operation plus some type conversion).
 */
public class VectorAPIExpansionPhase extends PostRunCanonicalizationPhase<HighTierContext> {

    public VectorAPIExpansionPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer.copyWithCustomSimplification(new VectorAPIExpansionPhase.VectorAPISimplification()));
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunBefore(this, GraphState.StageFlag.HIGH_TIER_LOWERING, graphState);
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(GraphState.StageFlag.VECTOR_API_EXPANSION);
    }

    /**
     * A "connected component" of macro nodes connected by input/usage relationships or through phi
     * or proxy nodes.
     */
    private static class ConnectedComponent {
        /** All the macro nodes in this component. */
        private ArrayList<VectorAPIMacroNode> macros;
        /** The sink nodes in this component. All elements must also be in {@link #macros}. */
        private ArrayList<VectorAPISinkNode> sinks;
        /** The phi nodes in this component. */
        private ArrayList<ValuePhiNode> phis;
        /** The value proxies in this component. */
        private ArrayList<ValueProxyNode> proxies;
        /** Constants representing vector values as inputs to other nodes in this component. */
        private ArrayList<ConstantNode> constants;
        /** Unboxable vector values as inputs to other nodes in this component. */
        private ArrayList<ValueNode> unboxes;

        /**
         * Record locations at which nodes in this component need to be materialized due to
         * unexpected usage. This cuts the usages off the component, allows the connected component
         * to be expanded. For example, a macro node {@code v} is used as an argument to a call:
         *
         * <pre>
         * {@code
         * IntVector v;
         * consume(v);
         * }
         * </pre>
         *
         * In general, the escape of {@code v} disallows its expansion. However, if it seems that
         * the call {@code consume(v)} happens rarely, we may manually box the vector instance, so
         * that the pseudocode snippet changes to:
         *
         * <pre>
         * {@code
         * IntVector v;
         * IntVector v1 = new IntVector;
         * v.intoArray(v1.payload);
         * consume(v1);
         * }
         * </pre>
         *
         * This disconnects {@code v} from the call to {@code consume}, allows it to be expanded.
         */
        private ArrayList<VectorAPIMacroNode> boxes;

        /**
         * A map from each node in this component to the corresponding SIMD stamp. The keys of this
         * map can be used to iterate over all the nodes in this component, i.e., the union of
         * macros, phis, proxies, constants, and unboxes.
         */
        private EconomicMap<ValueNode, Stamp> simdStamps;
        /**
         * Flag recording whether all nodes in this component can be expanded to SIMD code. This
         * starts out optimistically as {@code true} and can become {@code false} through analysis
         * of the component. Afterwards it can never again become {@code true}.
         */
        private boolean canExpand = true;

        ConnectedComponent() {
            this.macros = new ArrayList<>();
            this.sinks = new ArrayList<>();
            this.phis = new ArrayList<>();
            this.proxies = new ArrayList<>();
            this.constants = new ArrayList<>();
            this.unboxes = new ArrayList<>();
            this.boxes = new ArrayList<>();
            this.simdStamps = EconomicMap.create();
        }

        @Override
        public String toString() {
            return "ConnectedComponent(sinks: %s, macros: %s, can expand: %s)".formatted(sinks, macros, canExpand);
        }

        /** Checks some properties that should always hold of connected components. */
        public void checkInvariants() {
            for (VectorAPIMacroNode macro : macros) {
                GraalError.guarantee(simdStamps.containsKey(macro), "macro not in simdStamps: %s", macro);
            }
            for (ValuePhiNode phi : phis) {
                GraalError.guarantee(simdStamps.containsKey(phi), "phi not in simdStamps: %s", phi);
            }
            for (ValueProxyNode proxy : proxies) {
                GraalError.guarantee(simdStamps.containsKey(proxy), "proxy not in simdStamps: %s", proxy);
            }
            for (ConstantNode constant : constants) {
                GraalError.guarantee(simdStamps.containsKey(constant), "constant not in simdStamps: %s", constant);
            }
            GraalError.guarantee(macros.size() + phis.size() + proxies.size() + constants.size() + unboxes.size() == simdStamps.size(),
                            "expected %s macros + %s phis + %s proxies + %s constants + %s unboxes = %s SIMD nodes in %s",
                            macros.size(), phis.size(), proxies.size(), constants.size(), unboxes.size(), simdStamps.size(), this);
        }
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        graph.getGraphState().setDuringStage(GraphState.StageFlag.VECTOR_API_EXPANSION);
        if (!graph.hasNode(VectorAPIMacroNode.TYPE)) {
            return;
        }

        /*
         * Canonicalize first. Needed for computing SIMD stamps, since we delay their computation to
         * compile time. We can't generally compute SIMD stamps at the time we build the macro nodes
         * because the target architecture for SVM runtime compilations is not known at that time.
         */
        canonicalizer.applyIncremental(graph, context, graph.getNodes(VectorAPIMacroNode.TYPE));
        graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after Vector API macro canonicalization");

        VectorArchitecture vectorArch = ((VectorLoweringProvider) context.getLowerer()).getVectorArchitecture();
        /*
         * Nodes visited while discovering the equivalence classes. All nodes that are in one of the
         * macro equivalence classes will be marked here.
         */
        NodeFlood flood = graph.createNodeFlood();
        /*
         * Cached mapping of Vector API constant nodes to corresponding SIMD constants. Note that
         * logic vectors from mask constants are not fully constant folded, so these are not
         * necessarily ConstantNodes.
         */
        EconomicMap<ConstantNode, ValueNode> simdConstantCache = EconomicMap.create();

        NodeUnionFind unionFind = collectNodes(graph, flood);
        Iterable<ConnectedComponent> components = buildConnectedComponents(graph, context, unionFind, flood, simdConstantCache);
        checkComponentExpandability(graph, components, vectorArch);
        expandComponents(graph, context, simdConstantCache, components, vectorArch);
    }

    /**
     * Visit all {@link VectorAPIMacroNode}s, grouping them with their vector inputs in a union-find
     * data structure. Also visit phis and proxies connected to macros and group them accordingly.
     * Exactly the nodes added to the union-find are also marked in {@code flood}.
     */
    private static NodeUnionFind collectNodes(StructuredGraph graph, NodeFlood flood) {
        /*
         * A grouping of nodes in the graph into equivalence classes. Each class will become a
         * connected component.
         */
        NodeUnionFind unionFind = new NodeUnionFind(graph);
        /* Connect all macro nodes to their inputs. */
        for (VectorAPIMacroNode macro : graph.getNodes(VectorAPIMacroNode.TYPE)) {
            flood.add(macro);
            for (Node input : macro.vectorInputs()) {
                unionFind.union(macro, input);
                flood.add(input);
            }
        }
        /*
         * Collect any proxies and phis between macro nodes. Also collect all phi and proxy usages
         * of macros, transitively. This is relevant for phi or proxy nodes used only as inputs to
         * frame states but not to other macros. We don't want to lose these usages.
         */
        for (Node node : flood) {
            if ((node instanceof VectorAPIMacroNode && !(node instanceof VectorAPISinkNode)) || node instanceof ValuePhiNode || node instanceof ValueProxyNode) {
                for (Node usage : node.usages()) {
                    if (usage instanceof ValuePhiNode || usage instanceof ValueProxyNode) {
                        unionFind.union(node, usage);
                        flood.add(usage);
                    }
                }
            }
            if (node instanceof ValuePhiNode phi) {
                for (Node input : phi.values()) {
                    unionFind.union(phi, input);
                    flood.add(input);
                }
            } else if (node instanceof ValueProxyNode proxy) {
                unionFind.union(proxy, proxy.getOriginalNode());
                flood.add(proxy.getOriginalNode());
            }
        }
        return unionFind;
    }

    /**
     * Build connected components from the given {@code unionFind}. From the union-find we have an
     * implicit representation of connected components by mapping each node to a representative.
     * This method makes the grouping explicit by putting all nodes with the same representative in
     * the same component.
     * <p/>
     *
     * For example, we might have a computation graph like:
     *
     * <pre>
     *      Load     Load
     *         \     /
     *        BinaryOp
     *           |
     *         Store
     * </pre>
     *
     * and a union-find data structure in which the {@code BinaryOp} is the representative for this
     * set of nodes, i.e., for each of these four nodes, {@code unionFind.find(node)} will return
     * the {@code BinaryOp}. This method below will build a component containing all four nodes.
     */
    private static Iterable<ConnectedComponent> buildConnectedComponents(StructuredGraph graph, HighTierContext context, NodeUnionFind unionFind, NodeFlood flood,
                    EconomicMap<ConstantNode, ValueNode> simdConstantCache) {
        /*
         * This map contains the components we build. For each node n in the union find, its
         * component can be found using unionFind.find(n) as the key.
         */
        NodeMap<ConnectedComponent> components = new NodeMap<>(graph);
        for (Node node : flood.getVisited()) {
            Node representative = unionFind.find(node);
            ConnectedComponent component = components.get(representative);
            if (component == null) {
                component = new ConnectedComponent();
                components.put(representative, component);
            }
            boolean isSink = false;
            boolean isNullConstant = false;

            /*
             * Add the node to the relevant data structures inside the component. Check local
             * properties to determine if the component can still be expanded to SIMD code.
             */
            if (node instanceof VectorAPIMacroNode macro) {
                component.macros.add(macro);
                if (macro instanceof VectorAPISinkNode sink) {
                    component.sinks.add(sink);
                    isSink = true;
                }
                if (macro.vectorStamp() == null) {
                    node.graph().getDebug().log(DebugContext.DETAILED_LEVEL, "macro %s has null vector stamp %s", macro, macro.vectorStamp());
                    component.canExpand = false;
                } else if (component.canExpand) {
                    component.simdStamps.put(macro, macro.vectorStamp());
                    propagateStampToUsages(macro, macro.vectorStamp(), component, flood);
                }
            } else if (node instanceof ValuePhiNode phi) {
                component.phis.add(phi);
            } else if (node instanceof ValueProxyNode proxy) {
                component.proxies.add(proxy);
            } else if (node instanceof ConstantNode constant && constant.isNullConstant()) {
                /* Nothing to do, this is an optional mask input to some node. */
                isNullConstant = true;
            } else if (node instanceof ConstantNode constant && constant.isJavaConstant()) {
                ValueNode simdConstant = simdConstantCache.get(constant);
                if (simdConstant == null) {
                    simdConstant = VectorAPIBoxingUtils.tryReadSimdConstant(constant.asJavaConstant(), context);
                    if (simdConstant != null) {
                        simdConstant = graph.addOrUniqueWithInputs(simdConstant);
                        simdConstantCache.put(constant, simdConstant);
                        component.constants.add(constant);
                        component.simdStamps.put(constant, simdConstant.stamp(NodeView.DEFAULT));
                        propagateStampToUsages(constant, simdConstant.stamp(NodeView.DEFAULT), component, flood);
                    }
                }
                if (simdConstant == null) {
                    graph.getDebug().log(DebugContext.DETAILED_LEVEL, "can't expand constant to SIMD: %s", constant);
                    component.canExpand = false;
                }
            } else if (node instanceof ValueNode value && VectorAPIBoxingUtils.asUnboxableVectorType(value, context) != null) {
                component.unboxes.add(value);
                Stamp unboxedStamp = VectorAPIBoxingUtils.asUnboxableVectorType(value, context).stamp;
                component.simdStamps.put(value, unboxedStamp);
                propagateStampToUsages(value, unboxedStamp, component, flood);
            } else {
                /* Some unexpected input to a node. */
                graph.getDebug().log(DebugContext.DETAILED_LEVEL, "input %s (stamp %s) to a component prevents SIMD expansion", node, node instanceof ValueNode v ? v.stamp(NodeView.DEFAULT) : null);
                component.canExpand = false;
            }

            /* Check for unsupported usages of vector values outside the connected component. */
            if (!isSink && component.canExpand && !isNullConstant) {
                for (Node usage : node.usages()) {
                    if (unionFind.find(usage) == representative) {
                        /*
                         * The usage is in the same connected component, so it will be expanded to
                         * SIMD code iff this node is expanded.
                         */
                        continue;
                    } else if (usage instanceof FrameState || usage instanceof VirtualObjectState) {
                        /*
                         * SIMD values may be used in frame states. If the state is used for
                         * deoptimization, this will materialize the SIMD value as a vector object
                         * on the heap.
                         */
                        continue;
                    } else if (node instanceof VectorAPIMacroNode macro && shouldBox(macro, usage, context)) {
                        // Manually box the vector node to disconnect the unexpected usage from the
                        // ConnectedComponent
                        component.boxes.add(macro);
                        continue;
                    } else {
                        /*
                         * Some other usage, this would force materialization of the vector object.
                         * Don't try to expand this component to SIMD code.
                         */
                        graph.getDebug().log(DebugContext.DETAILED_LEVEL, "unexpected usage %s for node %s prevents SIMD expansion", usage, node);
                        component.canExpand = false;
                        break;
                    }
                }
            }
        }

        return components.getValues();
    }

    /**
     * Propagate the given {@code stamp} to all (transitive) phi and proxy usages of {@code node}.
     * Catch cases where a phi has inputs with different SIMD stamps, we can't expand those.
     */
    private static void propagateStampToUsages(ValueNode node, Stamp stamp, ConnectedComponent component, NodeFlood flood) {
        /*
         * The stamp might come from an unboxed constant and be too precise for a phi, which
         * presumably has non-constant inputs too. Therefore make it unrestricted.
         */
        Stamp propagateStamp = stamp.unrestricted();
        NodeStack stack = new NodeStack();
        for (Node usage : node.usages()) {
            if (usage instanceof ValuePhiNode || usage instanceof ValueProxyNode) {
                stack.push(usage);
            }
        }
        while (!stack.isEmpty()) {
            ValueNode usage = (ValueNode) stack.pop();
            if (node instanceof VectorAPISinkNode && usage.stamp(NodeView.DEFAULT) instanceof PrimitiveStamp) {
                /* This is a scalar usage, it doesn't need a SIMD stamp. */
                continue;
            }
            if (!flood.isMarked(usage)) {
                /* This usage is outside all components. Outside usages are not allowed. */
                node.graph().getDebug().log(DebugContext.DETAILED_LEVEL, "usage %s of node %s outside of all components", usage, node);
                component.canExpand = false;
                break;
            }
            Stamp knownStamp = component.simdStamps.get(usage);
            if (knownStamp == null) {
                component.simdStamps.put(usage, propagateStamp);
                for (Node transitiveUsage : usage.usages()) {
                    if (transitiveUsage instanceof ValuePhiNode || transitiveUsage instanceof ValueProxyNode) {
                        stack.push(transitiveUsage);
                    }
                }
            } else if (!knownStamp.isCompatible(propagateStamp)) {
                node.graph().getDebug().log(DebugContext.DETAILED_LEVEL, "usage %s of node %s: incompatible stamps %s / %s", usage, node, knownStamp, propagateStamp);
                component.canExpand = false;
                break;
            }
        }
    }

    /**
     * Visit each node in each component and check if {@linkplain VectorAPIMacroNode#canExpand it
     * can be expanded}. Set {@link ConnectedComponent#canExpand} accordingly.
     */
    private static void checkComponentExpandability(StructuredGraph graph, Iterable<ConnectedComponent> components, VectorArchitecture vectorArch) {
        for (ConnectedComponent component : components) {
            if (component.canExpand) {
                component.checkInvariants();
                for (VectorAPIMacroNode macro : component.macros) {
                    if (!macro.canExpand(vectorArch, component.simdStamps)) {
                        graph.getDebug().log(DebugContext.DETAILED_LEVEL, "macro %s can't expand, this prevents SIMD expansion of its component", macro);
                        component.canExpand = false;
                        break;
                    }
                }
            }
            graph.getDebug().log(DebugContext.DETAILED_LEVEL, "checked component: %s", component);
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "checked component: %s", component);
        }
    }

    /**
     * Having grouped nodes into components and determined if the components can expand, perform the
     * actual expansion of expandable components.
     */
    private static void expandComponents(StructuredGraph graph, HighTierContext context, EconomicMap<ConstantNode, ValueNode> simdConstantCache, Iterable<ConnectedComponent> components,
                    VectorArchitecture vectorArch) {
        for (ConnectedComponent component : components) {
            if (component.canExpand) {
                graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "before expanding component %s", component);
                NodeMap<ValueNode> expanded = new NodeMap<>(graph);
                expanded.putAll(simdConstantCache);

                /* Expand unboxing operations that are inputs to the component. */
                unboxComponentInputs(graph, context, component, expanded);
                /* Box the nodes that escape to make the component expandable */
                boxComponentOutputs(graph, context, component, expanded, vectorArch);
                /* Expand, starting from sinks and recursing upwards through inputs. */
                for (VectorAPISinkNode sink : component.sinks) {
                    expandRecursivelyUpwards(graph, context, expanded, component.simdStamps, sink, vectorArch);
                }
                /*
                 * Normally, expanding upwards from sinks should take care of all nodes in the
                 * component. However, some unexpected code shapes might contain vector computations
                 * that have no usages or are only used by frame states. We must make sure these are
                 * handled as well.
                 */
                for (ValueNode node : component.simdStamps.getKeys()) {
                    if (!expanded.containsKey(node)) {
                        expandRecursivelyUpwards(graph, context, expanded, component.simdStamps, node, vectorArch);
                    }
                }
                graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after expanding component %s", component);

                replaceComponentNodes(graph, context, component, expanded, vectorArch);
            }
        }
    }

    /**
     * For all unboxable inputs to nodes in the component, insert the necessary unboxing code. We
     * insert separate occurrences of the unboxing code for each usage of an unboxable input because
     * otherwise it would be difficult to find a single correct insertion point for the unboxing
     * code.
     */
    private static void unboxComponentInputs(StructuredGraph graph, CoreProviders providers, ConnectedComponent component, NodeMap<ValueNode> expanded) {
        GraalError.guarantee(component.canExpand, "should only place unbox nodes once we know the component can expand");
        for (ValueNode unboxableInput : component.unboxes) {
            for (ValueNode usage : unboxableInput.usages().filter(ValueNode.class).snapshot()) {
                if (component.simdStamps.containsKey(usage)) {
                    if (usage instanceof VectorAPIMacroNode macro) {
                        anchorAndUnboxInput(graph, providers, macro, unboxableInput, macro, expanded);
                    } else if (usage instanceof ValuePhiNode phi) {
                        for (int i = 0; i < phi.valueCount(); i++) {
                            if (phi.valueAt(i) == unboxableInput) {
                                anchorAndUnboxInput(graph, providers, phi, unboxableInput, phi.merge().phiPredecessorAt(i), expanded, i);
                            }
                        }
                    } else if (usage instanceof ValueProxyNode proxy) {
                        anchorAndUnboxInput(graph, providers, proxy, unboxableInput, proxy.proxyPoint(), expanded);
                    } else {
                        throw GraalError.shouldNotReachHereUnexpectedValue(usage);
                    }
                }
            }
        }
        if (!component.unboxes.isEmpty()) {
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after unboxing %s inputs for %s", component.unboxes.size(), component);
        }
        /* Remove these unboxing operations from the component to mark them as handled. */
        for (ValueNode unboxableInput : component.unboxes) {
            component.simdStamps.removeKey(unboxableInput);
        }
        component.unboxes.clear();
    }

    private static void boxComponentOutputs(StructuredGraph graph, CoreProviders providers, ConnectedComponent component, NodeMap<ValueNode> expanded, VectorArchitecture vectorArch) {
        GraalError.guarantee(component.canExpand, "should only place box nodes once we know the component can expand");
        for (VectorAPIMacroNode macro : component.boxes) {
            expandRecursivelyUpwards(graph, providers, expanded, component.simdStamps, macro, vectorArch);
            ValueNode expandedDef = expanded.get(macro);
            GraalError.guarantee(expandedDef != null, "must be expanded %s", macro);
            ResolvedJavaType boxType = macro.stamp(NodeView.DEFAULT).javaType(providers.getMetaAccess());
            EconomicSet<Node> uses = EconomicSet.create(Equivalence.DEFAULT);
            uses.addAll(macro.usages());
            for (Node use : uses) {
                // For a use, this operation might replace it with a clone that has the macro input
                // fixed. As a result, we need to collect the uses here instead of recording them
                // while constructing the connected components.
                if (!shouldBox(macro, use, providers)) {
                    continue;
                }

                if (use instanceof FixedNode successor) {
                    // If the usage is a FixedNode, box the macro there and replace the macro input
                    // with the allocated box instance
                    ValueNode boxedMacro = VectorAPIBoxingUtils.boxVector(boxType, successor, expandedDef, providers);
                    successor.replaceAllInputs(macro, boxedMacro);
                } else {
                    // The pattern here looks similar to macro -> MethodCallTarget -> Invoke. As a
                    // result, we need to clone a MethodCallTarget for each of its Invoke output,
                    // then replace the macro in the cloned MethodCallTarget with a boxed vector
                    // instance.
                    GraalError.guarantee(use instanceof MethodCallTargetNode, "unexpected use %s", use);
                    EconomicSet<Node> successors = EconomicSet.create();
                    successors.addAll(use.usages());
                    for (Node successor : successors) {
                        FixedNode fixedSuccessor = (FixedNode) successor;
                        ValueNode useCloned = (ValueNode) use.copyWithInputs();
                        ValueNode boxedMacro = VectorAPIBoxingUtils.boxVector(boxType, fixedSuccessor, expandedDef, providers);
                        useCloned.replaceAllInputs(macro, boxedMacro);
                        useCloned = graph.addOrUniqueWithInputs(useCloned);
                        fixedSuccessor.replaceAllInputs(use, useCloned);
                    }
                }
            }

            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "after boxing %s for %s", macro, component);
        }

        graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after boxing all escapes of %s", component);
    }

    /**
     * If a {@link VectorAPIMacroNode} has a usage {@code use} that cannot be expanded. We try to
     * see if it is profitable to box {@code macro} at {@code use}, disconnecting {@code use} from
     * the {@link ConnectedComponent}, allowing it to be expanded.
     */
    private static boolean shouldBox(VectorAPIMacroNode macro, Node use, CoreProviders providers) {
        if (use instanceof MethodCallTargetNode method) {
            /*
             * If a macro node is used in a method call that appears to be uncommon, we can manually
             * box the vector, disconnecting the method call from the connected component. The
             * conservative heuristics now is that a method returning a throwable is likely
             * uncommon. Revisit and expand the heuristic if the need arises.
             */
            ResolvedJavaType throwableType = providers.getMetaAccess().lookupJavaType(Throwable.class);
            if (method.returnKind() == JavaKind.Object && throwableType.isAssignableFrom(method.returnStamp().getTrustedStamp().javaType(providers.getMetaAccess())) &&
                            method.usages().filter(n -> !(n instanceof Invoke)).isEmpty()) {
                return true;
            }
        } else if (use instanceof ReturnNode returnNode && returnNode.result().equals(macro)) {
            // If a macro node is returned, we can also box the vector there
            return true;
        }

        return false;
    }

    private static void anchorAndUnboxInput(StructuredGraph graph, CoreProviders providers, ValueNode usage, ValueNode unboxableInput, FixedNode insertionPoint, NodeMap<ValueNode> expanded) {
        anchorAndUnboxInput(graph, providers, usage, unboxableInput, insertionPoint, expanded, -1);
    }

    /**
     * Adds a node anchoring {@code unboxableInput} before {@code insertionPoint}, replaces the
     * anchor value in {@code usage}, then inserts unboxing code and records the unboxed version in
     * the {@code expanded} map. If {@code usage} is a {@link ValuePhiNode}, {@code phiInputIndex}
     * indicates the input to replace; it must be -1 otherwise.
     */
    private static void anchorAndUnboxInput(StructuredGraph graph, CoreProviders providers, ValueNode usage, ValueNode unboxableInput, FixedNode insertionPoint, NodeMap<ValueNode> expanded,
                    int phiInputIndex) {
        GraalError.guarantee(usage instanceof ValuePhiNode == (phiInputIndex != -1), "input index must be defined for phis, but not for any other node");
        FixedValueAnchorNode anchor = graph.add(new FixedValueAnchorNode(unboxableInput));
        graph.addBeforeFixed(insertionPoint, anchor);
        if (phiInputIndex != -1) {
            ((ValuePhiNode) usage).setValueAt(phiInputIndex, anchor);
        } else {
            usage.replaceAllInputs(unboxableInput, anchor);
        }
        ValueNode unboxed = VectorAPIBoxingUtils.unboxObject(anchor, providers);
        expanded.setAndGrow(anchor, unboxed);
    }

    /**
     * Expand the given {@code node}, recursively expanding its input first if needed. Record a
     * mapping from each node to its expansion in the {@code expanded} map. Do nothing on nodes that
     * are already expanded.
     * <p/>
     *
     * For example, given a computation graph like:
     *
     * <pre>
     *      Load     Load
     *         \     /
     *        BinaryOp
     *           |
     *         Store
     * </pre>
     *
     * this method expects (purely for efficiency) to be originally called on the bottom-most
     * {@link VectorAPISinkNode}, i.e., the {@code Store}. It will recursively call itself to expand
     * in a depth-first traversal all nodes in the sequence {@code Load, Load, BinaryOp, Store}.
     * "Expanding" a macro node means calling its {@link VectorAPIMacroNode#expand} method. Phis and
     * proxies are duplicated with their new expanded inputs, unboxable constants and other values
     * are unboxed.
     */
    private static void expandRecursivelyUpwards(StructuredGraph graph, CoreProviders providers, NodeMap<ValueNode> expanded, EconomicMap<ValueNode, Stamp> simdStamps, ValueNode root,
                    VectorArchitecture vectorArch) {
        if (expanded.containsKey(root)) {
            return;
        }

        /*
         * Visit nodes in a depth-first manner, assisted by a stack. The topmost node is not popped
         * automatically because we might want to keep it on the stack and push its inputs above it
         * first.
         */
        Deque<ValueNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            ValueNode node = stack.peek();
            if (expanded.containsKey(node) && !(node instanceof ValuePhiNode)) {
                stack.pop();
                continue;
            }
            expandOneNode(graph, providers, expanded, simdStamps, node, vectorArch, stack);
        }
    }

    /*
     * Visit the given node, which must be the top of the stack. If this node has inputs to expand,
     * push those inputs on the stack and return. This ensures that all inputs will be expanded
     * before the current node. Otherwise, pop the node and add its expansion to the expanded map.
     * Cyclic phis are handled by building the replacement phi right away, before pushing the inputs
     * to the stack.
     */
    @SuppressWarnings("try")
    private static void expandOneNode(StructuredGraph graph, CoreProviders providers, NodeMap<ValueNode> expanded, EconomicMap<ValueNode, Stamp> simdStamps, ValueNode node,
                    VectorArchitecture vectorArch, Deque<ValueNode> stack) {
        if (expanded.containsKey(node) && !(node instanceof ValuePhiNode)) {
            /*
             * Nodes can be on the stack multiple times. For example, an operation like x + x will
             * enqueue the node for x twice for expansion. Once we have expanded it the first time,
             * there is nothing else to do. The only exception are phi nodes, which we visit twice:
             * once to set up the replacement placeholder, and a second time to fill in its expanded
             * input values.
             */
            return;
        }
        try (DebugCloseable sourcePosition = graph.withNodeSourcePosition(node)) {
            ValueNode expansion = null;
            if (node instanceof VectorAPIMacroNode macro) {
                boolean mustExpandInputsFirst = false;
                for (ValueNode input : macro.vectorInputs()) {
                    if (input.isNullConstant()) {
                        /*
                         * This is an absent mask to some macro node with an optional mask. We can
                         * ignore it.
                         */
                    } else if (!expanded.containsKey(input)) {
                        stack.push(input);
                        mustExpandInputsFirst = true;
                    }
                }
                if (mustExpandInputsFirst) {
                    /* Delay expansion of this node until after all inputs have been expanded. */
                    return;
                } else {
                    expansion = macro.expand(vectorArch, expanded);
                }
            } else if (node instanceof ValuePhiNode phi) {
                expansion = visitPhiForExpansion(graph, expanded, simdStamps, stack, phi);
                if (expansion == null) {
                    return;
                }
            } else if (node instanceof ValueProxyNode proxy) {
                ValueNode input = proxy.getOriginalNode();
                if (!expanded.containsKey(input)) {
                    stack.push(input);
                    return;
                } else {
                    expansion = proxy.duplicateOn(proxy.proxyPoint(), expanded.get(proxy.getOriginalNode()));
                }
            } else if (VectorAPIBoxingUtils.asUnboxableVectorType(node, providers) != null) {
                throw GraalError.shouldNotReachHere("unboxable input should have been expanded before members of the component: " + node);
            } else {
                throw GraalError.shouldNotReachHere("unexpected node during expansion: " + node);
            }
            /* This node and all of its inputs have now been expanded. */
            stack.pop();
            if (!expansion.isAlive()) {
                graph.addWithoutUniqueWithInputs(expansion);
            }
            expanded.put(node, expansion);
            graph.getOptimizationLog().withProperty("expansion", expansion).report(VectorAPIExpansionPhase.class, "SIMD expansion", node);
        }
    }

    /**
     * Replace a phi on Vector API values by a phi on SIMD values. This visits the phi twice: On the
     * first visit, it adds a placeholder phi to the expanded map and pushes the phi's values to the
     * stack for expansion. This visit returns {@code null}. The second visit, after all inputs have
     * been expanded from Vector API nodes to SIMD nodes, adds the expanded input values to the phi
     * and returns the actual expansion.
     */
    private static ValueNode visitPhiForExpansion(StructuredGraph graph, NodeMap<ValueNode> expanded, EconomicMap<ValueNode, Stamp> simdStamps, Deque<ValueNode> stack, ValuePhiNode phi) {
        ValueNode expansion;
        SimdStamp phiStamp = (SimdStamp) simdStamps.get(phi);
        if (!expanded.containsKey(phi)) {
            /* Expand the phi to a placeholder before expanding its inputs. */
            ValuePhiNode expandedPhi = graph.addWithoutUnique(new ValuePhiNode(phiStamp, phi.merge()));
            expansion = expandedPhi;
            expanded.put(phi, expansion);
        }
        boolean mustExpandInputsFirst = false;
        for (ValueNode input : phi.values()) {
            if (!expanded.containsKey(input)) {
                stack.push(input);
                mustExpandInputsFirst = true;
            }
        }
        if (mustExpandInputsFirst) {
            return null;
        } else {
            expansion = expanded.get(phi);
            GraalError.guarantee(expansion != null, "phi %s with no inputs to expand have been expanded already", phi);
            ValuePhiNode expandedPhi = (ValuePhiNode) expansion;
            if (expandedPhi.valueCount() > 0) {
                /*
                 * In rare cases, we visit the same phi multiple times. This one has had its inputs
                 * set already, so there is nothing more to do.
                 */
                return expansion;
            }
            for (ValueNode input : phi.values()) {
                ValueNode expandedInput = expanded.get(input);
                expandedPhi.addInput(expandedInput);
            }
        }
        return expansion;
    }

    /**
     * Replace the computation represented by this component by its expanded version. The expansion
     * procedure has already linked up the input/usage relationships inside the component, but
     * usages outside the component (scalar usages, states) are replaced here.
     */
    private static void replaceComponentNodes(StructuredGraph graph, HighTierContext context, ConnectedComponent component, NodeMap<ValueNode> expanded, VectorArchitecture vectorArch) {
        for (ValueNode node : component.simdStamps.getKeys()) {
            if (!node.isAlive()) {
                // As we kill CFGs while replacing each element of the component, it may be the case
                // that an element is killed because its control dies, simply skip those elements
                continue;
            }
            ValueNode replacement = expanded.get(node);
            GraalError.guarantee(replacement != null, "node was not expanded: %s", node);
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "before replacing %s -> %s", node, replacement);

            /* Replace things like reductions, which produce a primitive value. */
            if (node instanceof VectorAPISinkNode && replacement.stamp(NodeView.DEFAULT) instanceof PrimitiveStamp) {
                node.replaceAtUsages(replacement);
            }

            /*
             * Fix up frame state usages. These may need to materialize a SIMD value as a Vector API
             * object, so we need to build a corresponding virtual object.
             */
            if (node.usages().filter(u -> u instanceof FrameState || u instanceof VirtualObjectState).isNotEmpty()) {
                ResolvedJavaType type = StampTool.typeOrNull(node);
                GraalError.guarantee(type != null, "could not resolve type for %s (%s)", node, node.stamp(NodeView.DEFAULT));
                VectorAPIType vectorType = VectorAPIType.ofType(type, context);
                GraalError.guarantee(type != null, "could not find Vector API type for %s (%s)", node, node.stamp(NodeView.DEFAULT));
                VirtualInstanceNode virtualInstance = graph.add(new VirtualInstanceNode(type, true));
                ValueNode replacementValue = replacement;
                if (vectorType.isMask) {
                    replacementValue = graph.addOrUniqueWithInputs(VectorAPIBoxingUtils.logicAsBooleans(replacementValue, vectorArch));
                }
                VirtualObjectState virtualState = graph.unique(new VirtualObjectState(virtualInstance, List.of(replacementValue)));
                EconomicSet<FrameState> statesToAdd = EconomicSet.create();
                for (FrameState state : node.usages().filter(FrameState.class)) {
                    statesToAdd.add(state);
                }
                for (VirtualObjectState holder : node.usages().filter(VirtualObjectState.class)) {
                    // We also need to add the virtual state to all frames where an object holding
                    // this virtual object appears
                    for (FrameState state : holder.usages().filter(FrameState.class)) {
                        statesToAdd.add(state);
                    }
                }
                for (FrameState state : statesToAdd) {
                    state.addVirtualObjectMapping(virtualState);
                }
                node.replaceAtUsages(virtualInstance, usage -> usage != virtualState && (usage instanceof FrameState || usage instanceof VirtualObjectState));
            }

            /*
             * Clear out all other usages because we will replace nodes with SIMD versions that have
             * different stamps. The usages themselves will be deleted since they are part of the
             * same component, and all nodes in the component are replaced.
             */
            node.replaceAtUsages(null, usage -> component.simdStamps.containsKey((ValueNode) usage));
            if (node instanceof FixedWithNextNode fixedNode) {
                if (replacement instanceof FixedWithNextNode fixedReplacement && fixedReplacement.next() != null) {
                    /*
                     * The replacement is already linked into the control flow. This happens for
                     * unboxing operations, which expand to multiple fixed nodes that we add to the
                     * control flow during unboxing.
                     */
                    fixedNode.replaceAtUsages(replacement);
                    graph.removeFixed(fixedNode);
                } else {
                    graph.replaceFixed(fixedNode, replacement);
                }
            } else if (node instanceof MacroWithExceptionNode macroWithExceptionNode) {
                AbstractBeginNode exceptionEdge = macroWithExceptionNode.exceptionEdge();
                if (replacement instanceof FixedWithNextNode fixedReplacement && fixedReplacement.next() != null) {
                    macroWithExceptionNode.replaceAtUsages(replacement);
                    graph.removeSplit(macroWithExceptionNode, macroWithExceptionNode.getPrimarySuccessor());
                } else {
                    graph.replaceSplit(macroWithExceptionNode, replacement, macroWithExceptionNode.getPrimarySuccessor());
                }
                GraphUtil.killCFG(exceptionEdge);
            }
        }
        graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after adding duplicates for %s", component);
    }

    public static class VectorAPISimplification implements CanonicalizerPhase.CustomSimplification {

        @Override
        public void simplify(Node node, SimplifierTool tool) {
            if (node instanceof MinMaxNode<?> minMax && minMax.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                /*
                 * Our targets don't support direct min/max instructions on scalar integers, but the
                 * expansion of min/max reduce operations can produce these nodes. Legalize them.
                 */
                ValueNode replacement = minMax.graph().addOrUniqueWithInputs(minMax.asConditional(tool.getLowerer()));
                node.replaceAtUsagesAndDelete(replacement);
                tool.addToWorkList(replacement.usages());
            }
        }
    }
}
