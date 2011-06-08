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

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class InliningPhase extends Phase {

    private final GraalCompilation compilation;
    private final IR ir;

    private final Queue<Invoke> invokes = new ArrayDeque<Invoke>();
    private final Queue<RiMethod> methods = new ArrayDeque<RiMethod>();
    private int inliningSize;

    public InliningPhase(GraalCompilation compilation, IR ir) {
        this.compilation = compilation;
        this.ir = ir;
    }

    private void addToQueue(Invoke invoke, RiMethod method) {
        invokes.add(invoke);
        methods.add(method);
        inliningSize += method.code().length;
    }

    @Override
    protected void run(Graph graph) {
        inliningSize = compilation.method.code().length;
        int iterations = GraalOptions.MaximumRecursiveInlineLevel;
        do {
            for (Node node : graph.getNodes()) {
                if (node instanceof Invoke) {
                    Invoke invoke = (Invoke) node;
                    RiMethod target = invoke.target;
                    if (!checkInliningConditions(invoke) || !target.isResolved() || Modifier.isNative(target.accessFlags())) {
                        continue;
                    }
                    if (target.canBeStaticallyBound()) {
                        if (checkInliningConditions(invoke.target)) {
                            addToQueue(invoke, invoke.target);
                        }
                    } else {
                        RiMethod concrete = invoke.target.holder().uniqueConcreteMethod(invoke.target);
                        if (concrete != null && concrete.isResolved() && !Modifier.isNative(concrete.accessFlags())) {
                            if (checkInliningConditions(concrete)) {
                                if (GraalOptions.TraceInlining) {
                                    System.out.println("registering concrete method assumption...");
                                }
                                compilation.assumptions.recordConcreteMethod(invoke.target, concrete);
                                addToQueue(invoke, concrete);
                            }
                        }
                    }
                    if (inliningSize > GraalOptions.MaximumInstructionCount) {
                        break;
                    }
                }
            }

            assert invokes.size() == methods.size();
            if (invokes.isEmpty()) {
                break;
            }

            Invoke invoke;
            while ((invoke = invokes.poll()) != null) {
                RiMethod method = methods.remove();
                inlineMethod(invoke, method);
            }
            DeadCodeEliminationPhase dce = new DeadCodeEliminationPhase();
            dce.apply(graph);

            if (inliningSize > GraalOptions.MaximumInstructionCount) {
                if (GraalOptions.TraceInlining) {
                    System.out.println("inlining stopped: MaximumInstructionCount reached");
                }
                break;
            }
        } while(--iterations > 0);
    }

    private boolean checkInliningConditions(Invoke invoke) {
        String name = invoke.id() + ": " + CiUtil.format("%H.%n(%p):%r", invoke.target, false);
        if (invoke.predecessors().size() == 0) {
            if (GraalOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because the invoke is dead code");
            }
            return false;
        }
        return true;
    }

    private boolean checkInliningConditions(RiMethod method) {
        String name = null;
        if (GraalOptions.TraceInlining) {
            name = CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.code().length + " bytes)";
        }
        if (method.code().length > GraalOptions.MaximumInlineSize) {
            if (GraalOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because of code size");
            }
            return false;
        }
        if (!method.holder().isInitialized()) {
            if (GraalOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because of non-initialized class");
            }
            return false;
        }
        return true;
    }

    private void inlineMethod(Invoke invoke, RiMethod method) {
        String name = invoke.id() + ": " + CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.code().length + " bytes)";
        FrameState stateAfter = invoke.stateAfter();
        Instruction exceptionEdge = invoke.exceptionEdge();

        if (GraalOptions.TraceInlining) {
            System.out.printf("Building graph for %s, locals: %d, stack: %d\n", name, method.maxLocals(), method.maxStackSize());
        }

        CompilerGraph graph = new CompilerGraph(compilation);
        new GraphBuilderPhase(compilation, method, true, true).apply(graph);

        boolean withReceiver = !Modifier.isStatic(method.accessFlags());

        int argumentCount = method.signature().argumentCount(false);
        Value[] parameters = new Value[argumentCount + (withReceiver ? 1 : 0)];
        int slot = withReceiver ? 1 : 0;
        int param = withReceiver ? 1 : 0;
        for (int i = 0; i < argumentCount; i++) {
            parameters[param++] = invoke.argument(slot);
            slot += method.signature().argumentKindAt(i).sizeInSlots();
        }
        if (withReceiver) {
            parameters[0] = invoke.argument(0);
        }

        HashMap<Node, Node> replacements = new HashMap<Node, Node>();
        ArrayList<Node> nodes = new ArrayList<Node>();
        ArrayList<Node> frameStates = new ArrayList<Node>();
        Return returnNode = null;
        Unwind unwindNode = null;
        StartNode startNode = graph.start();
        for (Node node : graph.getNodes()) {
            if (node != null) {
                if (node instanceof StartNode) {
                    assert startNode == node;
                } else if (node instanceof Local) {
                    replacements.put(node, parameters[((Local) node).index()]);
                } else {
                    nodes.add(node);
                    if (node instanceof Return) {
                        returnNode = (Return) node;
                    } else if (node instanceof Unwind) {
                        unwindNode = (Unwind) node;
                    } else if (node instanceof FrameState) {
                        frameStates.add(node);
                    }
                }
            }
        }

        if (GraalOptions.TraceInlining) {
            ir.printGraph("Subgraph " + CiUtil.format("%H.%n(%p):%r", method, false), graph);
            System.out.println("inlining " + name + ": " + frameStates.size() + " frame states, " + nodes.size() + " nodes");
        }

        assert invoke.predecessors().size() == 1 : "size: " + invoke.predecessors().size();
        Instruction pred;
        if (withReceiver) {
            pred = new NullCheck(parameters[0], compilation.graph);
        } else {
            pred = new Merge(compilation.graph);
        }
        invoke.predecessors().get(0).successors().replace(invoke, pred);
        replacements.put(startNode, pred);

        Map<Node, Node> duplicates = compilation.graph.addDuplicate(nodes, replacements);

        if (returnNode != null) {
            List<Node> usages = new ArrayList<Node>(invoke.usages());
            for (Node usage : usages) {
                if (returnNode.result() instanceof Local) {
                    usage.inputs().replace(invoke, replacements.get(returnNode.result()));
                } else {
                    usage.inputs().replace(invoke, duplicates.get(returnNode.result()));
                }
            }
            Node returnDuplicate = duplicates.get(returnNode);
            returnDuplicate.inputs().clearAll();

            assert returnDuplicate.predecessors().size() == 1;
            Node returnPred = returnDuplicate.predecessors().get(0);
            int index = returnDuplicate.predecessorsIndex().get(0);
            returnPred.successors().setAndClear(index, invoke, 0);
            returnDuplicate.delete();
        }

//        if (invoke.next() instanceof Merge) {
//            ((Merge) invoke.next()).removePhiPredecessor(invoke);
//        }
//        invoke.successors().clearAll();
        invoke.inputs().clearAll();
        invoke.setExceptionEdge(null);
//        invoke.delete();


        if (exceptionEdge != null) {
            if (unwindNode != null) {
                assert unwindNode.predecessors().size() == 1;
                assert exceptionEdge.successors().size() == 1;
                ExceptionObject obj = (ExceptionObject) exceptionEdge;

                List<Node> usages = new ArrayList<Node>(obj.usages());
                for (Node usage : usages) {
                    if (replacements.containsKey(unwindNode.exception())) {
                        usage.inputs().replace(obj, replacements.get(unwindNode.exception()));
                    } else {
                        usage.inputs().replace(obj, duplicates.get(unwindNode.exception()));
                    }
                }
                Node unwindDuplicate = duplicates.get(unwindNode);
                unwindDuplicate.inputs().clearAll();

                assert unwindDuplicate.predecessors().size() == 1;
                Node unwindPred = unwindDuplicate.predecessors().get(0);
                int index = unwindDuplicate.predecessorsIndex().get(0);
                unwindPred.successors().setAndClear(index, obj, 0);

                obj.inputs().clearAll();
                obj.delete();
                unwindDuplicate.delete();

            }
        }

        // adjust all frame states that were copied
        if (frameStates.size() > 0) {
            FrameState outerFrameState = stateAfter.duplicateModified(invoke.bci, invoke.kind);
            for (Node frameState : frameStates) {
                ((FrameState) duplicates.get(frameState)).setOuterFrameState(outerFrameState);
            }
        }

        if (GraalOptions.TraceInlining) {
            ir.printGraph("After inlining " + CiUtil.format("%H.%n(%p):%r", method, false), compilation.graph);
        }
    }
}
