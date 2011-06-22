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
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ri.*;


public class EscapeAnalysisPhase extends Phase {


    public static class BlockExitState {
        public final HashMap<RiField, Node> fieldState;

        public BlockExitState() {
            this.fieldState = new HashMap<RiField, Node>();
        }
    }

    public class EscapementFixup {

        private List<Block> blocks;
        private final HashMap<String, RiField> fields = new HashMap<String, RiField>();

        private final HashMap<Block, BlockExitState> exitStates = new HashMap<Block, BlockExitState>();


        public void apply(final Graph graph, final Node node) {
            process(node);
            removeAllocation(graph, node);
        }

        public void removeAllocation(final Graph graph, final Node node) {
            final IdentifyBlocksPhase identifyBlocksPhase = new IdentifyBlocksPhase(true);
            identifyBlocksPhase.apply(graph);
            blocks = identifyBlocksPhase.getBlocks();

            final HashMap<Phi, RiField> phis = new HashMap<Phi, RiField>();

            Block.iteratePostOrder(blocks, new BlockClosure() {

                public void apply(Block block) {
//                    TTY.println("Block %d", block.blockID());
//                    for (Node n : block.getInstructions()) {
//                        TTY.println("  %d %s", n.id(), n.shortName());
//                    }
//                    for (Block b : block.getSuccessors()) {
//                        TTY.print(" %d", b.blockID());
//                    }
//                    TTY.println();

                    BlockExitState state;
                    List<Block> predecessors = block.getPredecessors();
                    Set<RiField> mergedFields = new HashSet<RiField>();
                    state = new BlockExitState();
                    for (int i = 0; i < predecessors.size(); i++) {
                        BlockExitState exitState = exitStates.get(predecessors.get(i));
                        if (exitState == null) {
                            mergedFields.addAll(fields.values());
                        } else {
                            for (RiField field : fields.values()) {
                                if (state.fieldState.get(field) == null) {
                                    state.fieldState.put(field, exitState.fieldState.get(field));
                                } else if (state.fieldState.get(field) != exitState.fieldState.get(field)) {
                                    mergedFields.add(field);
                                }
                            }
                        }
                    }
                    if (!mergedFields.isEmpty()) {
                        assert block.firstNode() instanceof Merge : "unexpected: " + block.firstNode().shortName() + " " +  block.firstNode().id();
                        for (RiField field : mergedFields) {
                            Phi phi = new Phi(field.kind().stackKind(), (Merge) block.firstNode(), graph);
                            state.fieldState.put(field, phi);
                            phis.put(phi, field);
                        }
                    }

                    if (identifyBlocksPhase.getNodeToBlock().get(node) == block) {
                        state = new BlockExitState();
                    }

                    Node current;
                    if (block.firstNode() instanceof StartNode) {
                        current = ((StartNode) block.firstNode()).start();
                    } else {
                        current = block.firstNode();
                    }
                    while (current != block.lastNode()) {
                        if (current instanceof LoadField) {
                            LoadField x = (LoadField) current;
                            if (x.object() == node) {
                                for (Node usage : new ArrayList<Node>(x.usages())) {
                                    assert state.fieldState.get(x.field()) != null;
                                    usage.inputs().replace(x, state.fieldState.get(x.field()));
                                }
                                current = ((Instruction) current).next();
                                x.replace(x.next());
                            } else {
                                current = ((Instruction) current).next();
                            }
                        } else if (current instanceof StoreField) {
                            StoreField x = (StoreField) current;
                            if (x.object() == node) {
                                state.fieldState.put(x.field(), x.value());
                                current = ((Instruction) current).next();
                                x.replace(x.next());
                            } else {
                                current = ((Instruction) current).next();
                            }
                        } else {
                            current = ((Instruction) current).next();
                        }
                    }

                    exitStates.put(block, state);
                }
            });

            for (Entry<Phi, RiField> entry : phis.entrySet()) {
                Phi phi = entry.getKey();
                RiField field = entry.getValue();
                Block block = identifyBlocksPhase.getNodeToBlock().get(entry.getKey().merge());

                List<Block> predecessors = block.getPredecessors();
                for (int i = 0; i < predecessors.size(); i++) {
                    BlockExitState exitState = exitStates.get(predecessors.get(i));
                    assert exitState != null;
                    Node value = exitState.fieldState.get(field);
                    TTY.println("fixing phi %d with %s", phi.id(), value);
                    if (value == null) {
                        phi.addInput(Constant.defaultForKind(field.kind(), graph));
                    } else {
                        phi.addInput(value);
                    }
                }

            }

            node.delete();
        }


        private void process(Node node) {
            for (Node usage : new ArrayList<Node>(node.usages())) {
                if (usage instanceof IsNonNull) {
                    IsNonNull x = (IsNonNull) usage;
                    if (x.usages().size() == 1 && x.usages().get(0) instanceof FixedGuard) {
                        FixedGuard guard = (FixedGuard) x.usages().get(0);
                        guard.replace(guard.next());
                    }
                    x.delete();
                } else if (usage instanceof IsType) {
                    IsType x = (IsType) usage;
                    assert x.type() == ((NewInstance) node).instanceClass();
                    if (x.usages().size() == 1 && x.usages().get(0) instanceof FixedGuard) {
                        FixedGuard guard = (FixedGuard) x.usages().get(0);
                        guard.replace(guard.next());
                    }
                    x.delete();
                } else if (usage instanceof FrameState) {
                    FrameState x = (FrameState) usage;
                    x.inputs().replace(node, Node.Null);
                } else if (usage instanceof StoreField) {
                    StoreField x = (StoreField) usage;
                    assert x.object() == node;
                    assert fields.get(x.field().name()) == null || fields.get(x.field().name()) == x.field();
                    fields.put(x.field().name(), x.field());
                } else if (usage instanceof AccessMonitor) {
                    AccessMonitor x = (AccessMonitor) usage;
                    TTY.println("replacing %d with %d (%s)", x.id(), x.next().id(), x.shortName());
                    x.replace(x.next());
                } else if (usage instanceof LoadField) {
                    LoadField x = (LoadField) usage;
                    assert x.object() == node;
                    assert fields.get(x.field().name()) == null || fields.get(x.field().name()) == x.field();
                    fields.put(x.field().name(), x.field());
                } else if (usage instanceof RegisterFinalizer) {
                    RegisterFinalizer x = (RegisterFinalizer) usage;
                    x.replace(x.next());
                } else {
                    assert false;
                }
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
//        if (compilation.method.holder().name().contains("jnt")) {
            for (Node node : graph.getNodes()) {
                if (node instanceof NewInstance && ((NewInstance) node).instanceClass().isResolved()) {
                    Set<Node> exits = new HashSet<Node>();
                    Set<Invoke> invokes = new HashSet<Invoke>();
                    int iterations = 0;

                    int weight;
                    do {
                        weight = analyse(node, exits, invokes);
//                        TTY.print("%d: new value: %d %s, weight %d, escapes at ", iterations, node.id(), node.shortName(), weight);
//                        for (Node n : exits) {
//                            TTY.print("%d %s, ", n.id(), n.shortName());
//                        }
//                        for (Node n : invokes) {
//                            TTY.print("%d %s, ", n.id(), n.shortName());
//                        }
//                        TTY.println();
                        if (exits.size() != 0) {
                            TTY.println("####### escaping object: %d in %s", node.id(), compilation.method);
                            break;
                        }
                        if (invokes.size() == 0) {
                            TTY.println("!!!!!!!! non-escaping object: %d in %s", node.id(), compilation.method);
                            new EscapementFixup().apply(graph, node);
                            break;
                        }
                        for (Invoke invoke : invokes) {
                            new InliningPhase(compilation, ir, invoke, GraalOptions.TraceInlining).apply(graph);
                        }
                        exits.clear();
                        invokes.clear();
                    } while (weight >= GraalOptions.ForcedInlineEscapeWeight && iterations++ < 15);
                }
            }
//        }
    }

    private int analyse(Node node, Collection<Node> exits, Collection<Invoke> invokes) {
        int weight = 0;
        for (Node usage : node.usages()) {
            if (usage instanceof IsNonNull) {
                IsNonNull x = (IsNonNull) usage;
                weight++;
            } else if (usage instanceof IsType) {
                IsType x = (IsType) usage;
                weight++;
            } else if (usage instanceof FrameState) {
                FrameState x = (FrameState) usage;
            } else if (usage instanceof StoreField) {
                StoreField x = (StoreField) usage;
                if (x.value() == node) {
                    exits.add(x);
                } else {
                    weight++;
                }
            } else if (usage instanceof AccessMonitor) {
                AccessMonitor x = (AccessMonitor) usage;
                weight++;
            } else if (usage instanceof LoadField) {
                LoadField x = (LoadField) usage;
                weight++;
            } else if (usage instanceof RegisterFinalizer) {
                RegisterFinalizer x = (RegisterFinalizer) usage;
                weight++;
            } else if (usage instanceof Invoke) {
                Invoke x = (Invoke) usage;
                invokes.add(x);
            } else {
                exits.add(usage);
            }
        }
        return weight;
    }


    public static enum Escape {
        NewValue,
        DoesNotEscape,
        DoesEscape
    }

    public static interface EscapeOp extends Op {
        Escape escape(Node node, Node value);
    }
}
