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
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.graphbuilder.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.virtual.*;
import com.sun.cri.ci.*;


public class EscapeAnalysisPhase extends Phase {
    public static class GraphOrder implements Iterable<Node> {

        private final ArrayList<Node> nodes = new ArrayList<Node>();

        public GraphOrder(Graph graph) {
            NodeBitMap visited = graph.createNodeBitMap();

            for (ReturnNode node : graph.getNodes(ReturnNode.class)) {
                visit(visited, node);
            }
            for (UnwindNode node : graph.getNodes(UnwindNode.class)) {
                visit(visited, node);
            }
            for (DeoptimizeNode node : graph.getNodes(DeoptimizeNode.class)) {
                visit(visited, node);
            }
        }

        private void visit(NodeBitMap visited, Node node) {
            if (node != null && !visited.isMarked(node)) {
                visited.mark(node);
                for (Node input : node.inputs()) {
                    visit(visited, input);
                }
                if (node.predecessor() != null) {
                    visit(visited, node.predecessor());
                }
                nodes.add(node);
            }
        }

        @Override
        public Iterator<Node> iterator() {
            return new Iterator<Node>() {

                private int pos = 0;

                private void removeDeleted() {
                    while (pos < nodes.size() && nodes.get(pos).isDeleted()) {
                        pos++;
                    }
                }

                @Override
                public boolean hasNext() {
                    removeDeleted();
                    return pos < nodes.size();
                }

                @Override
                public Node next() {
                    return nodes.get(pos++);
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    public static class BlockExitState implements MergeableState<BlockExitState> {
        public final ValueNode[] fieldState;
        public final VirtualObjectNode virtualObject;
        public ValueNode virtualObjectField;
        public final Graph graph;

        public BlockExitState(EscapeField[] fields, VirtualObjectNode virtualObject) {
            this.fieldState = new ValueNode[fields.length];
            this.virtualObject = virtualObject;
            this.virtualObjectField = null;
            this.graph = virtualObject.graph();
            for (int i = 0; i < fields.length; i++) {
                fieldState[i] = ConstantNode.defaultForKind(fields[i].type().kind(true), virtualObject.graph());
                virtualObjectField = graph.add(new VirtualObjectFieldNode(virtualObject, virtualObjectField, fieldState[i], i));
            }
        }

        public BlockExitState(BlockExitState state) {
            this.fieldState = state.fieldState.clone();
            this.virtualObject = state.virtualObject;
            this.virtualObjectField = state.virtualObjectField;
            this.graph = state.graph;
        }

        public void updateField(int fieldIndex) {
            virtualObjectField = graph.add(new VirtualObjectFieldNode(virtualObject, virtualObjectField, fieldState[fieldIndex], fieldIndex));
        }

        @Override
        public BlockExitState clone() {
            return new BlockExitState(this);
        }

        @Override
        public boolean merge(MergeNode merge, Collection<BlockExitState> withStates) {
            PhiNode vobjPhi = null;
            PhiNode[] valuePhis = new PhiNode[fieldState.length];
            for (BlockExitState other : withStates) {
                if (virtualObjectField != other.virtualObjectField && vobjPhi == null) {
                    vobjPhi = graph.add(new PhiNode(CiKind.Illegal, merge, PhiType.Virtual));
                    vobjPhi.addInput(virtualObjectField);
                    virtualObjectField = vobjPhi;
                }
                for (int i2 = 0; i2 < fieldState.length; i2++) {
                    if (fieldState[i2] != other.fieldState[i2] && valuePhis[i2] == null) {
                        valuePhis[i2] = graph.add(new PhiNode(fieldState[i2].kind(), merge, PhiType.Value));
                        valuePhis[i2].addInput(fieldState[i2]);
                        fieldState[i2] = valuePhis[i2];
                    }
                }
            }
            for (BlockExitState other : withStates) {
                if (vobjPhi != null) {
                    vobjPhi.addInput(other.virtualObjectField);
                }
                for (int i2 = 0; i2 < fieldState.length; i2++) {
                    if (valuePhis[i2] != null) {
                        valuePhis[i2].addInput(other.fieldState[i2]);
                    }
                }
            }
            assert vobjPhi == null || vobjPhi.valueCount() == withStates.size() + 1;
            for (int i2 = 0; i2 < fieldState.length; i2++) {
                if (valuePhis[i2] != null) {
                    virtualObjectField = graph.add(new VirtualObjectFieldNode(virtualObject, virtualObjectField, valuePhis[i2], i2));
                    assert valuePhis[i2].valueCount() == withStates.size() + 1;
                }
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
            assert virtualObjectField != null : "unexpected null virtualObjectField";
            PhiNode vobjPhi = null;
            vobjPhi = graph.add(new PhiNode(CiKind.Illegal, loopBegin, PhiType.Virtual));
            vobjPhi.addInput(virtualObjectField);
            virtualObjectField = vobjPhi;
            for (int i2 = 0; i2 < fieldState.length; i2++) {
                PhiNode valuePhi = graph.add(new PhiNode(fieldState[i2].kind(), loopBegin, PhiType.Value));
                valuePhi.addInput(fieldState[i2]);
                fieldState[i2] = valuePhi;
                updateField(i2);
            }
        }

        @Override
        public void loopEnd(LoopEndNode x, BlockExitState loopEndState) {
            while (!(virtualObjectField instanceof PhiNode)) {
                virtualObjectField = ((VirtualObjectFieldNode) virtualObjectField).lastState();
            }
            ((PhiNode) virtualObjectField).addInput(loopEndState.virtualObjectField);
            assert ((PhiNode) virtualObjectField).valueCount() == 2;
            for (int i2 = 0; i2 < fieldState.length; i2++) {
                ((PhiNode) fieldState[i2]).addInput(loopEndState.fieldState[i2]);
                assert ((PhiNode) fieldState[i2]).valueCount() == 2;
            }
        }

        @Override
        public void afterSplit(FixedNode node) {
            // nothing to do...
        }
    }


    public class EscapementFixup {

        private List<Block> blocks;
        private final Map<Object, Integer> fields = new HashMap<Object, Integer>();
        private final Map<Block, BlockExitState> exitStates = new IdentityHashMap<Block, BlockExitState>();

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
                node.replaceAndDelete(node.next());
            } else {
                process();
                removeAllocation();
            }
        }

        public void removeAllocation() {
            escapeFields = op.fields(node);
            for (int i = 0; i < escapeFields.length; i++) {
                fields.put(escapeFields[i].representation(), i);
            }
            final VirtualObjectNode virtual = graph.add(new VirtualObjectNode(((ValueNode) node).exactType(), escapeFields));
            if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                TTY.println("new virtual object: " + virtual);
            }
            node.replaceAtUsages(virtual);
            final FixedNode next = node.next();
            node.replaceAndDelete(next);

            if (virtual.fieldsCount() > 0) {
                final BlockExitState startState = new BlockExitState(escapeFields, virtual);
                final PostOrderNodeIterator<?> iterator = new PostOrderNodeIterator<BlockExitState>(next, startState) {
                    @Override
                    protected void node(FixedNode node) {
                        int changedField = op.updateState(virtual, node, fields, state.fieldState);
                        if (changedField != -1) {
                            state.updateField(changedField);
                        }
                        if (!node.isDeleted() && node instanceof StateSplit && ((StateSplit) node).stateAfter() != null) {
                            if (state.virtualObjectField != null) {
                                ((StateSplit) node).stateAfter().addVirtualObjectMapping(state.virtualObjectField);
                            }
                        }
                    }
                };
                iterator.apply();
            }
        }

        private void process() {
            for (Node usage : node.usages().snapshot()) {
                op.beforeUpdate(node, usage);
            }
        }
    }

    private final CiTarget target;
    private final GraalRuntime runtime;
    private final CiAssumptions assumptions;
    private final PhasePlan plan;
    private final GraphBuilderConfiguration config;

    public EscapeAnalysisPhase(CiTarget target, GraalRuntime runtime, CiAssumptions assumptions, PhasePlan plan) {
        this(target, runtime, assumptions, plan, GraphBuilderConfiguration.getDefault(plan));
    }

    public EscapeAnalysisPhase(CiTarget target, GraalRuntime runtime, CiAssumptions assumptions, PhasePlan plan, GraphBuilderConfiguration config) {
        this.runtime = runtime;
        this.target = target;
        this.assumptions = assumptions;
        this.plan = plan;
        this.config = config;
    }

    public static class EscapeRecord {

        public final Node node;
        public final ArrayList<Node> escapesThrough = new ArrayList<Node>();
        public final ArrayList<Invoke> invokes = new ArrayList<Invoke>();
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
        if (usage instanceof FrameState) {
            assert ((FrameState) usage).inputs().contains(node);
            return null;
        } else {
            if (usage instanceof FixedNode) {
                record.localWeight += ((FixedNode) usage).probability();
            }
            if (usage instanceof NullCheckNode) {
                assert ((NullCheckNode) usage).object() == node;
                return null;
            } else if (usage instanceof IsTypeNode) {
                assert ((IsTypeNode) usage).object() == node;
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
            } else if (usage instanceof VirtualObjectFieldNode) {
                return null;
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

    private void completeAnalysis(StructuredGraph graph) {
        // TODO(ls) debugging code

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
        Set<Node> exits = new HashSet<Node>();
        Set<Invoke> invokes = new HashSet<Invoke>();
        int iterations = 0;

        int minimumWeight = getMinimumWeight(node);
        do {
            double weight = analyze(op, node, exits, invokes);
            if (exits.size() != 0) {
                if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                    TTY.println("%n####### escaping object: %s (%s)", node, node.exactType());
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

                if (context.isObserved()) {
                    context.observable.fireCompilationEvent("Before escape " + node, graph);
                }
                if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                    TTY.println("%n!!!!!!!! non-escaping object: %s (%s)", node, node.exactType());
                }
                try {
                    context.timers.startScope("Escape Analysis Fixup");
                    removeAllocation(node, op);
                } finally {
                    context.timers.endScope();
                }
                if (context.isObserved()) {
                    context.observable.fireCompilationEvent("After escape", graph);
                }
                break;
            }
            if (weight < minimumWeight) {
                if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                    TTY.println("%n####### possibly escaping object: %s (insufficient weight for inlining)", node);
                }
                break;
            }
            if (!GraalOptions.Inline) {
                break;
            }
            if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                TTY.println("Trying inlining to get a non-escaping object for %s", node);
            }
            new InliningPhase(target, runtime, invokes, assumptions, plan, config).apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph, context);
            if (node.isDeleted()) {
                if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                    TTY.println("%n!!!!!!!! object died while performing escape analysis: %s (%s)", node, node.exactType());
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
                phi.replaceAndDelete(simpleValue);
            }
        }
    }

    protected boolean shouldAnalyze(FixedWithNextNode node) {
        return true;
    }

    protected int getMinimumWeight(FixedWithNextNode node) {
        return GraalOptions.ForcedInlineEscapeWeight;
    }

    private double analyze(EscapeOp op, Node node, Collection<Node> exits, Collection<Invoke> invokes) {
        double weight = 0;
        for (Node usage : node.usages().snapshot()) {
            boolean escapes = op.escape(node, usage);
            if (escapes) {
                if (usage instanceof FrameState) {
                    // nothing to do...
                } else if (usage instanceof MethodCallTargetNode) {
                    if (usage.usages().size() == 0) {
                        usage.safeDelete();
                    } else {
                        invokes.add(((MethodCallTargetNode) usage).invoke());
                    }
                } else {
                    exits.add(usage);
                    if (!GraalOptions.TraceEscapeAnalysis) {
                        break;
                    }
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
