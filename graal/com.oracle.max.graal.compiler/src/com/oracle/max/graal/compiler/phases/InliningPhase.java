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
    private final boolean trace;

    public InliningPhase(GraalCompilation compilation, IR ir, boolean trace) {
        this.compilation = compilation;
        this.ir = ir;
        this.trace = trace;
    }

    private void addToQueue(Invoke invoke, RiMethod method) {
        invokes.add(invoke);
        methods.add(method);
        inliningSize += method.code().length;
    }

    static HashMap<RiMethod, Integer> methodCount = new HashMap<RiMethod, Integer>();
    @Override
    protected void run(Graph graph) {
        inliningSize = compilation.method.code().length;
        for (int iterations = 0; iterations < GraalOptions.MaximumInlineLevel; iterations++) {
            for (Invoke invoke : graph.getNodes(Invoke.class)) {
                RiMethod target = invoke.target;
                if (invoke.stateAfter() == null || invoke.stateAfter().locksSize() > 0) {
                    if (trace) {
                        System.out.println("lock...");
                    }
                    continue;
                }
                if (!checkInliningConditions(invoke) || !target.isResolved() || Modifier.isNative(target.accessFlags())) {
                    continue;
                }
                if (target.canBeStaticallyBound()) {
                    if (checkInliningConditions(invoke.target, iterations)) {
                        addToQueue(invoke, invoke.target);
                    }
                } else {
                    RiMethod concrete = invoke.target.holder().uniqueConcreteMethod(invoke.target);
                    if (concrete != null && concrete.isResolved() && !Modifier.isNative(concrete.accessFlags())) {
                        if (checkInliningConditions(concrete, iterations)) {
                            if (trace) {
                                System.out.println("recording concrete method assumption...");
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

            assert invokes.size() == methods.size();
            if (invokes.isEmpty()) {
                break;
            }

            Invoke invoke;
            while ((invoke = invokes.poll()) != null) {
                RiMethod method = methods.remove();
                inlineMethod(invoke, method);

                if (methodCount.get(method) == null) {
                    methodCount.put(method, 1);
                } else {
                    methodCount.put(method, methodCount.get(method) + 1);
                }
            }
            DeadCodeEliminationPhase dce = new DeadCodeEliminationPhase();
            dce.apply(graph);

            if (inliningSize > GraalOptions.MaximumInstructionCount) {
                if (trace) {
                    System.out.println("inlining stopped: MaximumInstructionCount reached");
                }
                break;
            }
        }

        if (trace) {
            int inlined = 0;
            int duplicate = 0;
            for (Map.Entry<RiMethod, Integer> entry : methodCount.entrySet()) {
                inlined += entry.getValue();
                duplicate += entry.getValue() - 1;
            }
            if (inlined > 0) {
                System.out.printf("overhead_: %d (%5.3f %%)\n", duplicate, duplicate * 100.0 / inlined);
            }
        }
    }

    private boolean checkInliningConditions(Invoke invoke) {
        String name = !trace ? null : invoke.id() + ": " + CiUtil.format("%H.%n(%p):%r", invoke.target, false);
        if (invoke.profile() != null && invoke.profile().count < compilation.method.invocationCount() / 2) {
            if (trace) {
                System.out.println("not inlining " + name + " because the invocation counter is too low");
            }
            return false;
        }
        if (invoke.predecessors().size() == 0) {
            if (trace) {
                System.out.println("not inlining " + name + " because the invoke is dead code");
            }
            return false;
        }
        if (invoke.stateAfter() == null) {
            if (trace) {
                System.out.println("not inlining " + name + " because of missing frame state");
            }
        }
        return true;
    }

    private boolean checkInliningConditions(RiMethod method, int iterations) {
        String name = !trace ? null : CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.code().length + " bytes)";
        if (method.code().length > GraalOptions.MaximumInlineSize) {
            if (trace) {
                System.out.println("not inlining " + name + " because of code size");
            }
            return false;
        }
        if (!method.holder().isInitialized()) {
            if (trace) {
                System.out.println("not inlining " + name + " because of non-initialized class");
            }
            return false;
        }
        if (method == compilation.method && iterations > GraalOptions.MaximumRecursiveInlineLevel) {
            if (trace) {
                System.out.println("not inlining " + name + " because of recursive inlining limit");
            }
            return false;
        }
        return true;
    }

    private void inlineMethod(Invoke invoke, RiMethod method) {
        String name = !trace ? null : invoke.id() + ": " + CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.code().length + " bytes)";
        FrameState stateAfter = invoke.stateAfter();
        Instruction exceptionEdge = invoke.exceptionEdge();

        if (trace) {
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

        invoke.inputs().clearAll();

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

        if (trace) {
            ir.printGraph("Subgraph " + CiUtil.format("%H.%n(%p):%r", method, false), graph);
            System.out.println("inlining " + name + ": " + frameStates.size() + " frame states, " + nodes.size() + " nodes");
        }

        assert invoke.successors().get(0) != null : invoke;
        assert invoke.predecessors().size() == 1 : "size: " + invoke.predecessors().size();
        Instruction pred;
        if (withReceiver) {
            FixedGuard clipNode = new FixedGuard(compilation.graph);
            clipNode.setNode(new IsNonNull(parameters[0], compilation.graph));
            pred = clipNode;
        } else {
            pred = new Placeholder(compilation.graph);//(Instruction) invoke.predecessors().get(0);//new Merge(compilation.graph);
        }
        invoke.predecessors().get(0).successors().replace(invoke, pred);
        replacements.put(startNode, pred);

        Map<Node, Node> duplicates = compilation.graph.addDuplicate(nodes, replacements);

        int monitorIndexDelta = stateAfter.locksSize();
        if (monitorIndexDelta > 0) {
            for (Map.Entry<Node, Node> entry : duplicates.entrySet()) {
                if (entry.getValue() instanceof MonitorAddress) {
                    System.out.println("Adjusting monitor index");
                    MonitorAddress address = (MonitorAddress) entry.getValue();
                    address.setMonitorIndex(address.monitorIndex() + monitorIndexDelta);
                }
            }
        }

        if (pred instanceof Placeholder) {
            pred.replace(((Placeholder)pred).next());
        }

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
            returnDuplicate.replace(invoke.next());
            invoke.setNext(null);
        }

        if (exceptionEdge != null) {
            if (unwindNode != null) {
                assert unwindNode.predecessors().size() == 1;
                assert exceptionEdge.successors().size() == 1;
                ExceptionObject obj = (ExceptionObject) exceptionEdge;

                Unwind unwindDuplicate = (Unwind) duplicates.get(unwindNode);
                List<Node> usages = new ArrayList<Node>(obj.usages());
                for (Node usage : usages) {
                    usage.inputs().replace(obj, unwindDuplicate.exception());
                }
                unwindDuplicate.inputs().clearAll();
                unwindDuplicate.replace(obj.next());
            }
        }

        // adjust all frame states that were copied
        if (frameStates.size() > 0) {
            FrameState outerFrameState = stateAfter.duplicateModified(invoke.bci, invoke.kind);
            for (Node node : frameStates) {
                FrameState frameState = (FrameState) duplicates.get(node);
                if (!frameState.isDeleted()) {
                    frameState.setOuterFrameState(outerFrameState);
                }
            }
        }

        if (trace) {
            ir.printGraph("After inlining " + CiUtil.format("%H.%n(%p):%r", method, false), compilation.graph);
        }
    }
}
