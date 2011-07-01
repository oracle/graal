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
import java.util.Map.Entry;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class EscapeAnalysisPhase extends Phase {


    public static class BlockExitState {
        public final Map<EscapeField, Value> fieldState;
        public VirtualObject obj;

        public BlockExitState() {
            this.fieldState = new HashMap<EscapeField, Value>();
        }
    }

    public class EscapementFixup {

        private List<Block> blocks;
        private final Map<Object, EscapeField> fields = new HashMap<Object, EscapeField>();
        private final Map<Block, BlockExitState> exitStates = new HashMap<Block, BlockExitState>();

        private final EscapeOp op;
        private Graph graph;
        private final Node node;
        private RiType type;
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
            final IdentifyBlocksPhase identifyBlocksPhase = new IdentifyBlocksPhase(true);
            identifyBlocksPhase.apply(graph);
            blocks = identifyBlocksPhase.getBlocks();

            final HashMap<Phi, EscapeField> phis = new HashMap<Phi, EscapeField>();
            final Block startBlock = identifyBlocksPhase.getNodeToBlock().get(node);
            assert startBlock != null;
            type = ((Value) node).exactType();
            escapeFields = op.fields(node);
            for (EscapeField field : escapeFields) {
                fields.put(field.representation(), field);
            }

            Block.iteratePostOrder(blocks, new BlockClosure() {

                public void apply(Block block) {
                    if (GraalOptions.TraceEscapeAnalysis) {
                        TTY.println("Block %d", block.blockID());
                    }
//                    for (Node n : block.getInstructions()) {
//                        TTY.println("  %d %s", n.id(), n.shortName());
//                    }
//                    for (Block b : block.getSuccessors()) {
//                        TTY.print(" %d", b.blockID());
//                    }
//                    TTY.println();

                    BlockExitState state = new BlockExitState();
                    if (/*block == startBlock ||*/ block.getPredecessors().size() == 0) {
                        state.obj = null;
                        for (EscapeField field : fields.values()) {
                            Constant value = Constant.defaultForKind(field.kind(), graph);
                            state.fieldState.put(field, value);
                            state.obj = new VirtualObject(state.obj, value, field, type, escapeFields, graph);
                        }
                    } else {
                        List<Block> predecessors = block.getPredecessors();
                        Set<EscapeField> mergedFields = new HashSet<EscapeField>();

                        BlockExitState predState = exitStates.get(predecessors.get(0));
                        state.obj = predState == null ? null : predState.obj;

                        for (int i = 0; i < predecessors.size(); i++) {
                            BlockExitState exitState = exitStates.get(predecessors.get(i));
                            if (exitState == null) {
                                mergedFields.addAll(fields.values());
                                state.obj = null;
                                break;
                            } else {
                                for (EscapeField field : fields.values()) {
                                    if (state.fieldState.get(field) == null) {
                                        state.fieldState.put(field, exitState.fieldState.get(field));
                                    } else if (state.fieldState.get(field) != exitState.fieldState.get(field)) {
                                        mergedFields.add(field);
                                    }
                                }
                            }
                        }
                        if (!mergedFields.isEmpty()) {
                            assert block.firstNode() instanceof Merge : "unexpected: " + block.firstNode().shortName() + " " + block.firstNode().id();
                            for (EscapeField field : mergedFields) {
                                Phi phi = new Phi(field.kind().stackKind(), (Merge) block.firstNode(), graph);
                                state.fieldState.put(field, phi);
                                phis.put(phi, field);
                                state.obj = new VirtualObject(state.obj, phi, field, type, escapeFields, graph);
                            }
                        }
                    }

                    Node current;
                    if (block.firstNode() instanceof StartNode) {
                        current = ((StartNode) block.firstNode()).start();
                    } else {
                        current = block.firstNode();
                    }
                    while (current != block.lastNode()) {
                        Node next = ((FixedNodeWithNext) current).next();
                        EscapeField changedField = op.updateState(node, current, fields, state.fieldState);
                        if (changedField != null) {
                            state.obj = new VirtualObject(state.obj, state.fieldState.get(changedField), changedField, type, escapeFields, graph);
                        }
                        if (!current.isDeleted() && current instanceof StateSplit) {
                            FrameState stateAfter = ((StateSplit) current).stateAfter();
                            if (stateAfter != null) {
                                updateFrameState(stateAfter, state.obj);
                            }
                        }
                        current = next;
                    }

                    if (GraalOptions.TraceEscapeAnalysis) {
                        TTY.print(" block end state: ");
                        for (Entry<EscapeField, Value> entry : state.fieldState.entrySet()) {
                            TTY.print("%s->%s ", entry.getKey().name(), entry.getValue());
                        }
                        TTY.println();
                    }
                    exitStates.put(block, state);
                }
            });

            for (Entry<Phi, EscapeField> entry : phis.entrySet()) {
                Phi phi = entry.getKey();
                EscapeField field = entry.getValue();
                Block block = identifyBlocksPhase.getNodeToBlock().get(entry.getKey().merge());

                List<Block> predecessors = block.getPredecessors();
                assert predecessors.size() > 0;
                Node simple = exitStates.get(predecessors.get(0)).fieldState.get(field);
                for (int i = 1; i < predecessors.size(); i++) {
                    BlockExitState exitState = exitStates.get(predecessors.get(i));
                    if (exitState.fieldState.get(field) != simple) {
                        simple = null;
                    }
                }
                if (simple != null) {
                    for (Node usage : new ArrayList<Node>(phi.usages())) {
                        usage.inputs().replace(phi, simple);
                    }
                    phi.delete();
                } else {
                    for (int i = 0; i < predecessors.size(); i++) {
                        BlockExitState exitState = exitStates.get(predecessors.get(i));
                        assert exitState != null;
                        Node value = exitState.fieldState.get(field);
                        if (GraalOptions.TraceEscapeAnalysis) {
                            TTY.println("fixing phi %d with %s", phi.id(), value);
                        }
                        if (value == null) {
                            phi.addInput(Constant.defaultForKind(field.kind(), graph));
                        } else {
                            phi.addInput(value);
                        }
                    }
                }
            }
            // the rest of the usages should be dead frame states...
            for (Node usage : new ArrayList<Node>(node.usages())) {
                assert usage instanceof FrameState || usage instanceof VirtualObject : "usage: " + usage;
                usage.inputs().replace(node, Node.Null);
            }

            if (node instanceof FixedNodeWithNext) {
                node.replaceAndDelete(((FixedNodeWithNext) node).next());
            } else {
                node.delete();
            }
        }

        private VirtualObject updateFrameState(FrameState frameState, VirtualObject current) {
            for (int i = 0; i < frameState.inputs().size(); i++) {
                if (frameState.inputs().get(i) == node) {
                    frameState.inputs().set(i, current);
                } else if (frameState.inputs().get(i) instanceof VirtualObject) {
                    VirtualObject obj = (VirtualObject) frameState.inputs().get(i);
                    do {
                        current = updateVirtualObject(obj, current);
                        obj = obj.object();
                    } while (obj != null);
                }
            }
            FrameState outer = frameState.outerFrameState();
            if (outer != null) {
                boolean duplicate = false;
                for (int i = 0; i < outer.inputs().size(); i++) {
                    if (outer.inputs().get(i) == node) {
                        duplicate = true;
                    }
                }
                if (duplicate) {
                    outer = outer.duplicate(outer.bci);
                    frameState.setOuterFrameState(outer);
                }
                current = updateFrameState(outer, current);
            }
            return current;
        }

        private VirtualObject updateVirtualObject(VirtualObject obj, VirtualObject current) {
            if (obj.input() == node) {
                obj.setInput(current);
            } else if (obj.input() instanceof VirtualObject) {
                VirtualObject obj2 = (VirtualObject) obj.input();
                do {
                    current = updateVirtualObject(obj2, current);
                    obj2 = obj2.object();
                } while (obj2 != null);
            }
            return current;
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
//        if (compilation.method.holder().name().contains("oracle")) {
//            return;
//        }
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
                        new PhiSimplifier(graph);
                        if (compilation.compiler.isObserved()) {
                            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After escape " + node.id(), graph, true, false));
                        }
                        break;
                    }
                    if (weight < minimumWeight) {
                        if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                            TTY.println("%n####### possibly escaping object: %d in %s (insufficient weight for inlining)", node.id(), compilation.method);
                        }
                        break;
                    }
                    new InliningPhase(compilation, ir, invokes).apply(graph);
                    new DeadCodeEliminationPhase().apply(graph);
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

    public static interface EscapeOp extends Op {

        boolean canAnalyze(Node node);

        boolean escape(Node node, Node usage);

        EscapeField[] fields(Node node);

        void beforeUpdate(Node node, Node usage);

        EscapeField updateState(Node node, Node current, Map<Object, EscapeField> fields, Map<EscapeField, Value> fieldState);

    }
}
