/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.phases;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.graph.*;
import com.oracle.graal.compiler.util.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.max.criutils.*;


public class EscapeAnalysisPhase extends Phase {

    /**
     * Encapsulates the state of the virtual object, which is updated while traversing the control flow graph.
     */
    public static class BlockExitState implements MergeableState<BlockExitState> {
        public final ValueNode[] fieldState;
        public final VirtualObjectNode virtualObject;
        public final Graph graph;

        public BlockExitState(EscapeField[] fields, VirtualObjectNode virtualObject) {
            this.fieldState = new ValueNode[fields.length];
            this.virtualObject = virtualObject;
            this.graph = virtualObject.graph();
            for (int i = 0; i < fields.length; i++) {
                fieldState[i] = ConstantNode.defaultForKind(fields[i].type().kind(), virtualObject.graph());
            }
        }

        public BlockExitState(BlockExitState state) {
            this.fieldState = state.fieldState.clone();
            this.virtualObject = state.virtualObject;
            this.graph = state.graph;
        }

        @Override
        public BlockExitState clone() {
            return new BlockExitState(this);
        }

        @Override
        public boolean merge(MergeNode merge, List<BlockExitState> withStates) {
            PhiNode[] valuePhis = new PhiNode[fieldState.length];
            for (BlockExitState other : withStates) {
                for (int i2 = 0; i2 < fieldState.length; i2++) {
                    if (fieldState[i2] != other.fieldState[i2] && valuePhis[i2] == null) {
                        valuePhis[i2] = graph.add(new PhiNode(fieldState[i2].kind(), merge));
                        valuePhis[i2].addInput(fieldState[i2]);
                        fieldState[i2] = valuePhis[i2];
                    }
                }
            }
            for (BlockExitState other : withStates) {
                for (int i2 = 0; i2 < fieldState.length; i2++) {
                    if (valuePhis[i2] != null) {
                        valuePhis[i2].addInput(other.fieldState[i2]);
                    }
                }
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
            for (int i2 = 0; i2 < fieldState.length; i2++) {
                PhiNode valuePhi = graph.add(new PhiNode(fieldState[i2].kind(), loopBegin));
                valuePhi.addInput(fieldState[i2]);
                fieldState[i2] = valuePhi;
            }
        }

        @Override
        public void loopEnds(LoopBeginNode loopBegin, List<BlockExitState> loopEndStates) {
            for (BlockExitState loopEndState : loopEndStates) {
                for (int i2 = 0; i2 < fieldState.length; i2++) {
                    ((PhiNode) fieldState[i2]).addInput(loopEndState.fieldState[i2]);
                }
            }
        }

        @Override
        public void afterSplit(FixedNode node) {
            // nothing to do...
        }
    }


    public static class EscapementFixup {

        private final Map<Object, Integer> fields = new HashMap<>();
        private final EscapeOp op;
        private final StructuredGraph graph;
        private final FixedWithNextNode node;
        private EscapeField[] escapeFields;

        public EscapementFixup(EscapeOp op, StructuredGraph graph, FixedWithNextNode node) {
            this.op = op;
            this.graph = graph;
            this.node = node;
        }

        public void apply() {
            if (node.usages().isEmpty()) {
                graph.removeFixed(node);
            } else {
                process();
                removeAllocation();
            }
        }

        private void process() {
            for (Node usage : node.usages().snapshot()) {
                op.beforeUpdate(node, usage);
            }
        }

        public void removeAllocation() {
            escapeFields = op.fields(node);
            for (int i = 0; i < escapeFields.length; i++) {
                fields.put(escapeFields[i].representation(), i);
            }
            assert node.objectStamp().isExactType();
            final VirtualObjectNode virtual = graph.add(new VirtualObjectNode(node.objectStamp().type(), escapeFields));
            if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                TTY.println("new virtual object: " + virtual);
            }
            node.replaceAtUsages(virtual);
            FixedNode next = node.next();
            graph.removeFixed(node);

            List<ValueProxyNode> proxies;
            while (!(proxies = virtual.usages().filter(ValueProxyNode.class).snapshot()).isEmpty()) {
                for (ValueProxyNode vpn : proxies) {
                    assert vpn.value() == virtual;
                    graph.replaceFloating(vpn, virtual);
                }
            }

            if (virtual.fieldsCount() > 0) {
                final BlockExitState startState = new BlockExitState(escapeFields, virtual);
                final PostOrderNodeIterator<?> iterator = new PostOrderNodeIterator<BlockExitState>(next, startState) {
                    @Override
                    protected void node(FixedNode curNode) {
                        op.updateState(virtual, curNode, fields, state.fieldState);
                        if (curNode instanceof LoopExitNode) {
                            for (int i = 0; i < state.fieldState.length; i++) {
                                state.fieldState[i] = graph.unique(new ValueProxyNode(state.fieldState[i], (LoopExitNode) curNode, PhiType.Value));
                            }
                        }
                        if (!curNode.isDeleted() && curNode instanceof StateSplit && ((StateSplit) curNode).stateAfter() != null) {
                            VirtualObjectState v = graph.add(new VirtualObjectState(virtual, state.fieldState));
                            ((StateSplit) curNode).stateAfter().addVirtualObjectMapping(v);
                        }
                    }
                };
                iterator.apply();
            }
        }
    }

    private final TargetDescription target;
    private final GraalCodeCacheProvider runtime;
    private final Assumptions assumptions;
    private final GraphCache cache;
    private final PhasePlan plan;
    private final OptimisticOptimizations optimisticOpts;

    public EscapeAnalysisPhase(TargetDescription target, GraalCodeCacheProvider runtime, Assumptions assumptions, GraphCache cache, PhasePlan plan, OptimisticOptimizations optimisticOpts) {
        this.runtime = runtime;
        this.target = target;
        this.assumptions = assumptions;
        this.cache = cache;
        this.plan = plan;
        this.optimisticOpts = optimisticOpts;
    }

    public static class EscapeRecord {

        public final Node node;
        public final ArrayList<Node> escapesThrough = new ArrayList<>();
        public final ArrayList<Invoke> invokes = new ArrayList<>();
        public double localWeight;

        public EscapeRecord(Node node) {
            this.node = node;
        }

        public void dump() {
            TTY.print("node %s (%f) escapes through ", node, localWeight);
            for (Node escape : escapesThrough) {
                TTY.print("%s ", escape);
            }
            TTY.println();
        }
    }

    private static Node escape(EscapeRecord record, Node usage) {
        final Node node = record.node;
        if (usage instanceof VirtualState) {
            assert usage.inputs().contains(node);
            return null;
        } else {
            if (usage instanceof FixedNode) {
                record.localWeight += ((FixedNode) usage).probability();
            }
            if (usage instanceof IsNullNode) {
                assert ((IsNullNode) usage).object() == node;
                return null;
            } else if (usage instanceof IsTypeNode) {
                assert ((IsTypeNode) usage).objectClass() == node;
                return null;
            } else if (usage instanceof AccessMonitorNode) {
                assert ((AccessMonitorNode) usage).object() == node;
                return null;
            } else if (usage instanceof LoadFieldNode) {
                assert ((LoadFieldNode) usage).object() == node;
                return null;
            } else if (usage instanceof StoreFieldNode) {
                StoreFieldNode x = (StoreFieldNode) usage;
                // self-references do not escape
                return x.value() == node ? x.object() : null;
            } else if (usage instanceof LoadIndexedNode) {
                LoadIndexedNode x = (LoadIndexedNode) usage;
                if (x.index() == node) {
                    return x.array();
                } else {
                    assert x.array() == node;
                    return EscapeOp.isValidConstantIndex(x) ? null : x.array();
                }
            } else if (usage instanceof StoreIndexedNode) {
                StoreIndexedNode x = (StoreIndexedNode) usage;
                if (x.index() == node) {
                    return x.array();
                } else {
                    assert x.array() == node || x.value() == node;
                    // in order to not escape, the access needs to have a valid constant index and either a store into node or be self-referencing
                    return EscapeOp.isValidConstantIndex(x) && x.value() != node ? null : x.array();
                }
            } else if (usage instanceof RegisterFinalizerNode) {
                assert ((RegisterFinalizerNode) usage).object() == node;
                return null;
            } else if (usage instanceof ArrayLengthNode) {
                assert ((ArrayLengthNode) usage).array() == node;
                return null;
            } else {
                return usage;
            }
        }
    }

    @SuppressWarnings("unused")
    private static void completeAnalysis(StructuredGraph graph) {
        // TODO (lstadler): debugging code

        TTY.println("================================================================");
        for (Node node : graph.getNodes()) {
            if (node != null && node instanceof FixedWithNextNode && node instanceof EscapeAnalyzable) {
                EscapeOp op = ((EscapeAnalyzable) node).getEscapeOp();
                if (op != null && op.canAnalyze(node)) {
                    EscapeRecord record = new EscapeRecord(node);

                    for (Node usage : node.usages()) {
                        Node escapesThrough = escape(record, usage);
                        if (escapesThrough != null && escapesThrough != node) {
                            record.escapesThrough.add(escapesThrough);
                        }
                    }
                    record.dump();
                }
            }
        }
    }


    @Override
    protected void run(StructuredGraph graph) {
        for (Node node : new GraphOrder(graph)) {
            if (node != null && node instanceof FixedWithNextNode && node instanceof EscapeAnalyzable) {
                FixedWithNextNode fixedNode = (FixedWithNextNode) node;
                EscapeOp op = ((EscapeAnalyzable) node).getEscapeOp();
                if (op != null && op.canAnalyze(fixedNode)) {
                    try {
                        performAnalysis(graph, fixedNode, op);
                    } catch (GraalInternalError e) {
                        throw e.addContext("escape analysis of node", node);
                    }
                }
            }
        }
    }

    private void performAnalysis(StructuredGraph graph, FixedWithNextNode node, EscapeOp op) {
        if (!shouldAnalyze(node)) {
            return;
        }
        Set<Node> exits = new HashSet<>();
        Set<Invoke> invokes = new HashSet<>();
        int iterations = 0;

        int minimumWeight = getMinimumWeight(node);
        do {
            double weight = analyze(op, node, exits, invokes);
            if (exits.size() != 0) {
                if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                    TTY.println("%n####### escaping object: %s (%s)", node, node.stamp());
                    if (GraalOptions.TraceEscapeAnalysis) {
                        TTY.print("%d: new value: %s, weight %f, escapes at ", iterations, node, weight);
                        for (Node n : exits) {
                            TTY.print("%s, ", n);
                        }
                        for (Invoke n : invokes) {
                            TTY.print("%s, ", n);
                        }
                        TTY.println();
                    }
                }
                break;
            }
            if (invokes.size() == 0) {

                Debug.dump(graph, "Before escape %s", node);
                Debug.log("!!!!!!!! non-escaping object: %s (%s)", node, node.stamp());
                removeAllocation(node, op);
                Debug.dump(graph, "After escape", graph);
                break;
            }
            if (weight < minimumWeight) {
                if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                    TTY.println("####### possibly escaping object: %s (insufficient weight for inlining: %f)", node, weight);
                }
                break;
            }
            if (!GraalOptions.Inline) {
                break;
            }
            if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                TTY.println("Trying inlining to get a non-escaping object for %s", node);
            }
            new InliningPhase(target, runtime, invokes, assumptions, cache, plan, optimisticOpts).apply(graph);
            new DeadCodeEliminationPhase().apply(graph);
            if (node.isDeleted()) {
                if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                    TTY.println("!!!!!!!! object died while performing escape analysis: %s (%s)", node, node.stamp());
                }
                break;
            }
            exits.clear();
            invokes.clear();
        } while (iterations++ < 3);
    }

    protected void removeAllocation(FixedWithNextNode node, EscapeOp op) {
        new EscapementFixup(op, (StructuredGraph) node.graph(), node).apply();

        for (PhiNode phi : node.graph().getNodes(PhiNode.class)) {
            ValueNode simpleValue = phi;
            boolean required = false;
            for (ValueNode value : phi.values()) {
                if (value != phi && value != simpleValue) {
                    if (simpleValue != phi) {
                        required = true;
                        break;
                    }
                    simpleValue = value;
                }
            }
            if (!required) {
                ((StructuredGraph) node.graph()).replaceFloating(phi, simpleValue);
            }
        }
    }

    protected boolean shouldAnalyze(@SuppressWarnings("unused") FixedWithNextNode node) {
        return true;
    }

    protected int getMinimumWeight(@SuppressWarnings("unused") FixedWithNextNode node) {
        return GraalOptions.ForcedInlineEscapeWeight;
    }

    private static double analyze(EscapeOp op, Node node, Collection<Node> exits, Collection<Invoke> invokes) {
        double weight = 0;
        for (Node usage : node.usages().snapshot()) {
            boolean escapes = op.escape(node, usage);
            if (escapes) {
                if (usage instanceof VirtualState) {
                    // nothing to do...
                } else if (usage instanceof MethodCallTargetNode) {
                    if (usage.usages().size() == 0) {
                        usage.safeDelete();
                    } else {
                        invokes.add(((MethodCallTargetNode) usage).invoke());
                    }
                } else {
                    exits.add(usage);
                    break;
                }
            } else {
                if (GraalOptions.ProbabilityAnalysis && usage instanceof FixedNode) {
                    weight += ((FixedNode) usage).probability();
                } else {
                    weight++;
                }
            }
        }
        return weight;
    }

}
