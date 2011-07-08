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

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.ir.Phi.PhiType;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public class EscapeAnalysisPhase extends Phase {

    public interface MergeableState <T> {
        T clone();
        void merge(Merge merge, Collection<T> withStates);
        void loopBegin(LoopBegin loopBegin);
        void loopEnd(LoopEnd loopEnd, T loopEndState);
    }

    public abstract static class PostOrderNodeIterator<T extends MergeableState<T>> {

        private final NodeBitMap visitedEnds;
        private final Deque<FixedNode> nodeQueue;
        private final HashMap<FixedNode, T> nodeStates;
        private final FixedNode start;

        protected T state;

        public PostOrderNodeIterator(FixedNode start, T initialState) {
            visitedEnds = start.graph().createNodeBitMap();
            nodeQueue = new ArrayDeque<FixedNode>();
            nodeStates = new HashMap<FixedNode, T>();
            this.start = start;
            this.state = initialState;
        }

        public void apply() {
            FixedNode current = start;

            do {
                if (current instanceof Invoke) {
                    invoke((Invoke) current);
                    queueSuccessors(current);
                    current = nextQueuedNode();
                } else if (current instanceof LoopBegin) {
                    state.loopBegin((LoopBegin) current);
                    nodeStates.put(current, state.clone());
                    loopBegin((LoopBegin) current);
                    current = ((LoopBegin) current).next();
                    assert current != null;
                } else if (current instanceof LoopEnd) {
                    T loopBeginState = nodeStates.get(((LoopEnd) current).loopBegin());
                    if (loopBeginState != null) {
                        loopBeginState.loopEnd((LoopEnd) current, state);
                    }
                    loopEnd((LoopEnd) current);
                    current = nextQueuedNode();
                } else if (current instanceof Merge) {
                    merge((Merge) current);
                    current = ((Merge) current).next();
                    assert current != null;
                } else if (current instanceof FixedNodeWithNext) {
                    FixedNode next = ((FixedNodeWithNext) current).next();
                    node(current);
                    current = next;
                    assert current != null;
                } else if (current instanceof EndNode) {
                    end((EndNode) current);
                    queueMerge((EndNode) current);
                    current = nextQueuedNode();
                } else if (current instanceof Deoptimize) {
                    deoptimize((Deoptimize) current);
                    current = nextQueuedNode();
                } else if (current instanceof Return) {
                    returnNode((Return) current);
                    current = nextQueuedNode();
                } else if (current instanceof Unwind) {
                    unwind((Unwind) current);
                    current = nextQueuedNode();
                } else if (current instanceof ControlSplit) {
                    controlSplit((ControlSplit) current);
                    queueSuccessors(current);
                    current = nextQueuedNode();
                } else {
                    assert false : current.shortName();
                }
            } while(current != null);
        }

        private void queueSuccessors(FixedNode x) {
            nodeStates.put(x, state);
            for (Node node : x.successors()) {
                if (node != null) {
                    nodeQueue.addFirst((FixedNode) node);
                }
            }
        }

        private FixedNode nextQueuedNode() {
            int maxIterations = nodeQueue.size();
            while (maxIterations-- > 0) {
                FixedNode node = nodeQueue.removeFirst();
                if (node instanceof Merge) {
                    Merge merge = (Merge) node;
                    state = nodeStates.get(merge.endAt(0)).clone();
                    ArrayList<T> states = new ArrayList<T>(merge.endCount() - 1);
                    for (int i = 1; i < merge.endCount(); i++) {
                        T other = nodeStates.get(merge.endAt(i));
                        assert other != null;
                        states.add(other);
                    }
                    state.merge(merge, states);
                    return merge;
                } else {
                    assert node.predecessors().size() == 1;
                    state = nodeStates.get(node.predecessors().get(0)).clone();
                    return node;
                }
            }
            return null;
        }

        private void queueMerge(EndNode end) {
            assert !visitedEnds.isMarked(end);
            assert !nodeStates.containsKey(end);
            nodeStates.put(end, state);
            visitedEnds.mark(end);
            Merge merge = end.merge();
            boolean endsVisited = true;
            for (int i = 0; i < merge.endCount(); i++) {
                if (!visitedEnds.isMarked(merge.endAt(i))) {
                    endsVisited = false;
                    break;
                }
            }
            if (endsVisited) {
                nodeQueue.add(merge);
            }
        }

        protected abstract void node(FixedNode node);

        protected void end(EndNode endNode) {
            node(endNode);
        }

        protected void merge(Merge merge) {
            node(merge);
        }

        protected void loopBegin(LoopBegin loopBegin) {
            node(loopBegin);
        }

        protected void loopEnd(LoopEnd loopEnd) {
            node(loopEnd);
        }

        protected void deoptimize(Deoptimize deoptimize) {
            node(deoptimize);
        }

        protected void controlSplit(ControlSplit controlSplit) {
            node(controlSplit);
        }

        protected void returnNode(Return returnNode) {
            node(returnNode);
        }

        protected void invoke(Invoke invoke) {
            node(invoke);
        }

        protected void unwind(Unwind unwind) {
            node(unwind);
        }
    }

    public static class BlockExitState implements MergeableState<BlockExitState> {
        public final Value[] fieldState;
        public final VirtualObject virtualObject;
        public FloatingNode virtualObjectField;

        public BlockExitState(EscapeField[] fields, VirtualObject virtualObject) {
            this.fieldState = new Value[fields.length];
            this.virtualObject = virtualObject;
            this.virtualObjectField = null;
            for (int i = 0; i < fields.length; i++) {
                fieldState[i] = Constant.defaultForKind(fields[i].kind(), virtualObject.graph());
                virtualObjectField = new VirtualObjectField(virtualObject, virtualObjectField, fieldState[i], i, virtualObject.graph());
            }
        }

        public BlockExitState(BlockExitState state) {
            this.fieldState = state.fieldState.clone();
            this.virtualObject = state.virtualObject;
            this.virtualObjectField = state.virtualObjectField;
        }

        public void updateField(int fieldIndex) {
            virtualObjectField = new VirtualObjectField(virtualObject, virtualObjectField, fieldState[fieldIndex], fieldIndex, virtualObject.graph());
        }

        @Override
        public BlockExitState clone() {
            return new BlockExitState(this);
        }

        @Override
        public void merge(Merge merge, Collection<BlockExitState> withStates) {
            Phi vobjPhi = null;
            Phi[] valuePhis = new Phi[fieldState.length];
            for (BlockExitState other : withStates) {
                if (virtualObjectField != other.virtualObjectField && vobjPhi == null) {
                    vobjPhi = new Phi(CiKind.Illegal, merge, PhiType.Virtual, virtualObject.graph());
                    vobjPhi.addInput(virtualObjectField);
                    virtualObjectField = vobjPhi;
                }
                for (int i2 = 0; i2 < fieldState.length; i2++) {
                    if (fieldState[i2] != other.fieldState[i2] && valuePhis[i2] == null) {
                        valuePhis[i2] = new Phi(fieldState[i2].kind, merge, PhiType.Value, virtualObject.graph());
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
                    virtualObjectField = new VirtualObjectField(virtualObject, virtualObjectField, valuePhis[i2], i2, virtualObject.graph());
                    assert valuePhis[i2].valueCount() == withStates.size() + 1;
                }
            }
        }

        @Override
        public void loopBegin(LoopBegin loopBegin) {
            Phi vobjPhi = null;
            vobjPhi = new Phi(CiKind.Illegal, loopBegin, PhiType.Virtual, virtualObject.graph());
            vobjPhi.addInput(virtualObjectField);
            virtualObjectField = vobjPhi;
            for (int i2 = 0; i2 < fieldState.length; i2++) {
                Phi valuePhi = new Phi(fieldState[i2].kind, loopBegin, PhiType.Value, virtualObject.graph());
                valuePhi.addInput(fieldState[i2]);
                fieldState[i2] = valuePhi;
                updateField(i2);
            }
        }

        @Override
        public void loopEnd(LoopEnd x, BlockExitState loopEndState) {
            while (!(virtualObjectField instanceof Phi)) {
                virtualObjectField = ((VirtualObjectField) virtualObjectField).lastState();
            }
            ((Phi) virtualObjectField).addInput(loopEndState.virtualObjectField);
            assert ((Phi) virtualObjectField).valueCount() == 2;
            for (int i2 = 0; i2 < fieldState.length; i2++) {
                ((Phi) fieldState[i2]).addInput(loopEndState.fieldState[i2]);
                assert ((Phi) fieldState[i2]).valueCount() == 2;
            }
        }
    }


    public class EscapementFixup {

        private List<Block> blocks;
        private final Map<Object, Integer> fields = new HashMap<Object, Integer>();
        private final Map<Block, BlockExitState> exitStates = new HashMap<Block, BlockExitState>();

        private final EscapeOp op;
        private final Graph graph;
        private final Node node;
        private EscapeField[] escapeFields;

        public EscapementFixup(EscapeOp op, Graph graph, Node node) {
            this.op = op;
            this.graph = graph;
            this.node = node;
        }

        public void apply() {
            process();
            removeAllocation();
        }

        public void removeAllocation() {
            assert node instanceof FixedNodeWithNext;

            escapeFields = op.fields(node);
            for (int i = 0; i < escapeFields.length; i++) {
                fields.put(escapeFields[i].representation(), i);
            }
            final VirtualObject virtual = new VirtualObject(((Value) node).exactType(), escapeFields, graph);
            if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                TTY.println("new virtual object: " + virtual);
            }
            node.replaceAtUsages(virtual);
            final FixedNode next = ((FixedNodeWithNext) node).next();
            node.replaceAndDelete(next);

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

        private void process() {
            ArrayList<Node> arrayList = new ArrayList<Node>(node.usages());
            for (Node usage : arrayList) {
                op.beforeUpdate(node, usage);
            }
        }
    }

    private final GraalCompilation compilation;
    private final IR ir;

    public EscapeAnalysisPhase(GraalCompilation compilation, IR ir) {
        this.compilation = compilation;
        this.ir = ir;
    }

    @Override
    protected void run(Graph graph) {
        for (Node node : graph.getNodes()) {
            EscapeOp op = node.lookup(EscapeOp.class);
            if (op != null && op.canAnalyze(node)) {
                Set<Node> exits = new HashSet<Node>();
                Set<Invoke> invokes = new HashSet<Invoke>();
                int iterations = 0;

                int weight;
                int minimumWeight = GraalOptions.ForcedInlineEscapeWeight;
                do {
                    weight = analyze(op, node, exits, invokes);
                    if (exits.size() != 0) {
                        if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                            TTY.println("%n####### escaping object: %d %s (%s) in %s", node.id(), node.shortName(), ((Value) node).exactType(), compilation.method);
                            if (GraalOptions.TraceEscapeAnalysis) {
                                TTY.print("%d: new value: %d %s, weight %d, escapes at ", iterations, node.id(), node.shortName(), weight);
                                for (Node n : exits) {
                                    TTY.print("%d %s, ", n.id(), n.shortName());
                                }
                                for (Node n : invokes) {
                                    TTY.print("%d %s, ", n.id(), n.shortName());
                                }
                                TTY.println();
                            }
                        }
                        break;
                    }
                    if (invokes.size() == 0) {

                        if (compilation.compiler.isObserved()) {
                            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "Before escape " + node.id(), graph, true, false));
                        }
                        if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                            TTY.println("%n!!!!!!!! non-escaping object: %d %s (%s) in %s", node.id(), node.shortName(), ((Value) node).exactType(), compilation.method);
                        }
                        new EscapementFixup(op, graph, node).apply();
                        if (compilation.compiler.isObserved()) {
                            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After escape", graph, true, false));
                        }
                        new PhiSimplifier(graph);
                        break;
                    }
                    if (weight < minimumWeight) {
                        if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                            TTY.println("%n####### possibly escaping object: %d in %s (insufficient weight for inlining)", node.id(), compilation.method);
                        }
                        break;
                    }
                    if (!GraalOptions.Inline) {
                        break;
                    }
                    if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                        TTY.println("Trying inlining to get a non-escaping object for %d", node.id());
                    }
                    new InliningPhase(compilation, ir, invokes).apply(graph);
                    new DeadCodeEliminationPhase().apply(graph);
                    if (node.isDeleted()) {
                        if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                            TTY.println("%n!!!!!!!! object died while performing escape analysis: %d %s (%s) in %s", node.id(), node.shortName(), ((Value) node).exactType(), compilation.method);
                        }
                        break;
                    }
                    exits.clear();
                    invokes.clear();
                } while (iterations++ < 3);
            }
        }
    }

    private int analyze(EscapeOp op, Node node, Collection<Node> exits, Collection<Invoke> invokes) {
        int weight = 0;
        for (Node usage : node.usages()) {
            boolean escapes = op.escape(node, usage);
            if (escapes) {
                if (usage instanceof FrameState) {
                    // nothing to do...
                } else if (usage instanceof Invoke) {
                    invokes.add((Invoke) usage);
                } else {
                    exits.add(usage);
                    if (!GraalOptions.TraceEscapeAnalysis) {
                        break;
                    }
                }
            } else {
                weight++;
            }
        }
        return weight;
    }

    public static class EscapeField {

        private String name;
        private Object representation;
        private CiKind kind;

        public EscapeField(String name, Object representation, CiKind kind) {
            this.name = name;
            this.representation = representation;
            this.kind = kind;
        }

        public String name() {
            return name;
        }

        public Object representation() {
            return representation;
        }

        public CiKind kind() {
            return kind;
        }

        @Override
        public String toString() {
            return name();
        }
    }

    public abstract static class EscapeOp implements Op {

        public abstract boolean canAnalyze(Node node);

        public boolean escape(Node node, Node usage) {
            if (usage instanceof IsNonNull) {
                IsNonNull x = (IsNonNull) usage;
                assert x.object() == node;
                return false;
            } else if (usage instanceof IsType) {
                IsType x = (IsType) usage;
                assert x.object() == node;
                return false;
            } else if (usage instanceof FrameState) {
                FrameState x = (FrameState) usage;
                assert x.inputs().contains(node);
                return true;
            } else if (usage instanceof AccessMonitor) {
                AccessMonitor x = (AccessMonitor) usage;
                assert x.object() == node;
                return false;
            } else {
                return true;
            }
        }

        public abstract EscapeField[] fields(Node node);

        public void beforeUpdate(Node node, Node usage) {
            if (usage instanceof IsNonNull) {
                IsNonNull x = (IsNonNull) usage;
                if (x.usages().size() == 1 && x.usages().get(0) instanceof FixedGuard) {
                    FixedGuard guard = (FixedGuard) x.usages().get(0);
                    guard.replaceAndDelete(guard.next());
                }
                x.delete();
            } else if (usage instanceof IsType) {
                IsType x = (IsType) usage;
                assert x.type() == ((Value) node).exactType();
                if (x.usages().size() == 1 && x.usages().get(0) instanceof FixedGuard) {
                    FixedGuard guard = (FixedGuard) x.usages().get(0);
                    guard.replaceAndDelete(guard.next());
                }
                x.delete();
            } else if (usage instanceof AccessMonitor) {
                AccessMonitor x = (AccessMonitor) usage;
                x.replaceAndDelete(x.next());
            }
        }

        public abstract int updateState(Node node, Node current, Map<Object, Integer> fieldIndex, Value[] fieldState);

    }
}
