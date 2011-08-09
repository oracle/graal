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
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.base.*;
import com.oracle.max.graal.nodes.base.PhiNode.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.virtual.*;
import com.sun.cri.ci.*;


public class EscapeAnalysisPhase extends Phase {

    public static class BlockExitState implements MergeableState<BlockExitState> {
        public final ValueNode[] fieldState;
        public final VirtualObjectNode virtualObject;
        public FloatingNode virtualObjectField;

        public BlockExitState(EscapeField[] fields, VirtualObjectNode virtualObject) {
            this.fieldState = new ValueNode[fields.length];
            this.virtualObject = virtualObject;
            this.virtualObjectField = null;
            for (int i = 0; i < fields.length; i++) {
                fieldState[i] = ConstantNode.defaultForKind(fields[i].kind(), virtualObject.graph());
                virtualObjectField = new VirtualObjectFieldNode(virtualObject, virtualObjectField, fieldState[i], i, virtualObject.graph());
            }
        }

        public BlockExitState(BlockExitState state) {
            this.fieldState = state.fieldState.clone();
            this.virtualObject = state.virtualObject;
            this.virtualObjectField = state.virtualObjectField;
        }

        public void updateField(int fieldIndex) {
            virtualObjectField = new VirtualObjectFieldNode(virtualObject, virtualObjectField, fieldState[fieldIndex], fieldIndex, virtualObject.graph());
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
                    vobjPhi = new PhiNode(CiKind.Illegal, merge, PhiType.Virtual, virtualObject.graph());
                    vobjPhi.addInput(virtualObjectField);
                    virtualObjectField = vobjPhi;
                }
                for (int i2 = 0; i2 < fieldState.length; i2++) {
                    if (fieldState[i2] != other.fieldState[i2] && valuePhis[i2] == null) {
                        valuePhis[i2] = new PhiNode(fieldState[i2].kind, merge, PhiType.Value, virtualObject.graph());
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
                    virtualObjectField = new VirtualObjectFieldNode(virtualObject, virtualObjectField, valuePhis[i2], i2, virtualObject.graph());
                    assert valuePhis[i2].valueCount() == withStates.size() + 1;
                }
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
            PhiNode vobjPhi = null;
            vobjPhi = new PhiNode(CiKind.Illegal, loopBegin, PhiType.Virtual, virtualObject.graph());
            vobjPhi.addInput(virtualObjectField);
            virtualObjectField = vobjPhi;
            for (int i2 = 0; i2 < fieldState.length; i2++) {
                PhiNode valuePhi = new PhiNode(fieldState[i2].kind, loopBegin, PhiType.Value, virtualObject.graph());
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
            assert node instanceof FixedWithNextNode;

            escapeFields = op.fields(node);
            for (int i = 0; i < escapeFields.length; i++) {
                fields.put(escapeFields[i].representation(), i);
            }
            final VirtualObjectNode virtual = new VirtualObjectNode(((ValueNode) node).exactType(), escapeFields, graph);
            if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                TTY.println("new virtual object: " + virtual);
            }
            node.replaceAtUsages(virtual);
            final FixedNode next = ((FixedWithNextNode) node).next();
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
            for (Node usage : node.usages().snapshot()) {
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
                Set<InvokeNode> invokes = new HashSet<InvokeNode>();
                int iterations = 0;

                int minimumWeight = GraalOptions.ForcedInlineEscapeWeight;
                do {
                    double weight = analyze(op, node, exits, invokes);
                    if (exits.size() != 0) {
                        if (GraalOptions.TraceEscapeAnalysis || GraalOptions.PrintEscapeAnalysis) {
                            TTY.println("%n####### escaping object: %d %s (%s) in %s", node.id(), node.shortName(), ((ValueNode) node).exactType(), compilation.method);
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
                            TTY.println("%n!!!!!!!! non-escaping object: %d %s (%s) in %s", node.id(), node.shortName(), ((ValueNode) node).exactType(), compilation.method);
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
                            TTY.println("%n!!!!!!!! object died while performing escape analysis: %d %s (%s) in %s", node.id(), node.shortName(), ((ValueNode) node).exactType(), compilation.method);
                        }
                        break;
                    }
                    exits.clear();
                    invokes.clear();
                } while (iterations++ < 3);
            }
        }
    }

    private double analyze(EscapeOp op, Node node, Collection<Node> exits, Collection<InvokeNode> invokes) {
        double weight = 0;
        for (Node usage : node.usages()) {
            boolean escapes = op.escape(node, usage);
            if (escapes) {
                if (usage instanceof FrameState) {
                    // nothing to do...
                } else if (usage instanceof InvokeNode) {
                    invokes.add((InvokeNode) usage);
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
