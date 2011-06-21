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
import com.oracle.max.graal.compiler.debug.*;
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

    private final Map<Invoke, RiMethod> parentMethod = new HashMap<Invoke, RiMethod>();
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
        inliningSize += method.codeSize();
    }

    public static HashMap<RiMethod, Integer> methodCount = new HashMap<RiMethod, Integer>();
    @Override
    protected void run(Graph graph) {
        float ratio = GraalOptions.MaximumInlineRatio;
        inliningSize = compilation.method.codeSize();
        for (int iterations = 0; iterations < GraalOptions.MaximumInlineLevel; iterations++) {
            for (Invoke invoke : graph.getNodes(Invoke.class)) {
                RiMethod parent = parentMethod.get(invoke);
                if (parent == null) {
                    parent = compilation.method;
                }
                RiTypeProfile profile = parent.typeProfile(invoke.bci);
                if (!checkInvokeConditions(invoke)) {
                    continue;
                }
                if (invoke.target.canBeStaticallyBound()) {
                    if (checkTargetConditions(invoke.target, iterations) && checkSizeConditions(invoke.target, invoke, profile, ratio)) {
                        addToQueue(invoke, invoke.target);
                    }
                } else {
                    RiMethod concrete = invoke.target.holder().uniqueConcreteMethod(invoke.target);
                    if (concrete != null) {
                        if (checkTargetConditions(concrete, iterations) && checkSizeConditions(concrete, invoke, profile, ratio)) {
                            if (trace) {
                                String targetName = CiUtil.format("%H.%n(%p):%r", invoke.target, false);
                                String concreteName = CiUtil.format("%H.%n(%p):%r", concrete, false);
                                TTY.println("recording concrete method assumption: %s -> %s", targetName, concreteName);
                            }
                            compilation.assumptions.recordConcreteMethod(invoke.target, concrete);
                            addToQueue(invoke, concrete);
                        }
                    } else if (profile != null && profile.probabilities != null && profile.probabilities.length > 0 && profile.morphism == 1) {
                        if (GraalOptions.InlineWithTypeCheck) {
                            // type check and inlining...
                            concrete = profile.types[0].resolveMethodImpl(invoke.target);
                            if (concrete != null && checkTargetConditions(concrete, iterations) && checkSizeConditions(concrete, invoke, profile, ratio)) {
                                IsType isType = new IsType(invoke.receiver(), profile.types[0], compilation.graph);
                                FixedGuard guard = new FixedGuard(graph);
                                guard.setNode(isType);
                                assert invoke.predecessors().size() == 1;
                                invoke.predecessors().get(0).successors().replace(invoke, guard);
                                guard.setNext(invoke);

                                if (trace) {
                                    TTY.println("inlining with type check, type probability: %5.3f", profile.probabilities[0]);
                                }
                                addToQueue(invoke, concrete);
                            }
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
                    TTY.println("inlining stopped: MaximumInstructionCount reached");
                }
                break;
            }
            ratio *= GraalOptions.MaximumInlineRatio;
        }

        if (trace) {
            int inlined = 0;
            int duplicate = 0;
            for (Map.Entry<RiMethod, Integer> entry : methodCount.entrySet()) {
                inlined += entry.getValue();
                duplicate += entry.getValue() - 1;
            }
            if (inlined > 0) {
                TTY.println("overhead: %d (%5.3f %%)", duplicate, duplicate * 100.0 / inlined);
            }
        }
    }

    private String methodName(RiMethod method) {
        return CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.codeSize() + " bytes)";
    }

    private String methodName(RiMethod method, Invoke invoke) {
        if (invoke != null) {
            RiMethod parent = parentMethod.get(invoke);
            if (parent == null) {
                parent = compilation.method;
            }
            return parent.name() + "@" + invoke.bci + ": " + CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.codeSize() + " bytes)";
        } else {
            return CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.codeSize() + " bytes)";
        }
    }

    private boolean checkInvokeConditions(Invoke invoke) {
        if (invoke.stateAfter() == null) {
            if (trace) {
                TTY.println("not inlining %s because the invoke has no after state", methodName(invoke.target, invoke));
            }
            return false;
        }
        if (invoke.stateAfter().locksSize() > 0) {
            if (trace) {
                TTY.println("not inlining %s because of locks", methodName(invoke.target, invoke));
            }
            return false;
        }
        if (!invoke.target.isResolved()) {
            if (trace) {
                TTY.println("not inlining %s because the invoke target is unresolved", methodName(invoke.target, invoke));
            }
            return false;
        }
        if (invoke.predecessors().size() == 0) {
            if (trace) {
                TTY.println("not inlining %s because the invoke is dead code", methodName(invoke.target, invoke));
            }
            return false;
        }
        if (invoke.stateAfter() == null) {
            if (trace) {
                TTY.println("not inlining %s because of missing frame state", methodName(invoke.target, invoke));
            }
        }
        return true;
    }

    private boolean checkTargetConditions(RiMethod method, int iterations) {
        if (!method.isResolved()) {
            if (trace) {
                TTY.println("not inlining %s because it is unresolved", methodName(method));
            }
            return false;
        }
        if (Modifier.isNative(method.accessFlags())) {
            if (trace) {
                TTY.println("not inlining %s because it is a native method", methodName(method));
            }
            return false;
        }
        if (Modifier.isAbstract(method.accessFlags())) {
            if (trace) {
                TTY.println("not inlining %s because it is an abstract method", methodName(method));
            }
            return false;
        }
        if (!method.holder().isInitialized()) {
            if (trace) {
                TTY.println("not inlining %s because of non-initialized class", methodName(method));
            }
            return false;
        }
        if (method == compilation.method && iterations > GraalOptions.MaximumRecursiveInlineLevel) {
            if (trace) {
                TTY.println("not inlining %s because of recursive inlining limit", methodName(method));
            }
            return false;
        }
        return true;
    }

    private boolean checkStaticSizeConditions(RiMethod method, Invoke invoke) {
        if (method.codeSize() > GraalOptions.MaximumInlineSize) {
            if (trace) {
                TTY.println("not inlining %s because of code size (size: %d, max size: %d)", methodName(method, invoke), method.codeSize(), GraalOptions.MaximumInlineSize);
            }
            return false;
        }
        return true;
    }

    private boolean checkSizeConditions(RiMethod method, Invoke invoke, RiTypeProfile profile, float adjustedRatio) {
        int maximumSize = GraalOptions.MaximumTrivialSize;
        float ratio = 0;
        if (profile != null && profile.count > 0) {
            RiMethod parent = parentMethod.get(invoke);
            if (parent == null) {
                parent = compilation.method;
            }
            ratio = profile.count / (float) parent.invocationCount();
            if (ratio >= GraalOptions.FreqInlineRatio) {
                maximumSize = GraalOptions.MaximumFreqInlineSize;
            } else if (ratio >= (1 - adjustedRatio)) {
                maximumSize = GraalOptions.MaximumInlineSize;
            }
        }
        if (method.codeSize() > maximumSize) {
            if (trace) {
                TTY.println("not inlining %s because of code size (size: %d, max size: %d, ratio %5.3f, %s)", methodName(method, invoke), method.codeSize(), maximumSize, ratio, profile);
            }
            return false;
        }
        if (trace) {
            TTY.println("inlining %s (size: %d, max size: %d, ratio %5.3f, %s)", methodName(method, invoke), method.codeSize(), maximumSize, ratio, profile);
        }
        return true;
    }

    private void inlineMethod(Invoke invoke, RiMethod method) {
        FrameState stateAfter = invoke.stateAfter();
        FixedNode exceptionEdge = invoke.exceptionEdge();
        if (exceptionEdge instanceof Placeholder) {
            exceptionEdge = ((Placeholder) exceptionEdge).next();
        }

        CompilerGraph graph;
        Object stored = GraphBuilderPhase.cachedGraphs.get(method);
        if (stored != null) {
            if (trace) {
                TTY.println("Reusing graph for %s, locals: %d, stack: %d", methodName(method, invoke), method.maxLocals(), method.maxStackSize());
            }
            graph = (CompilerGraph) stored;
        } else {
            if (trace) {
                TTY.println("Building graph for %s, locals: %d, stack: %d", methodName(method, invoke), method.maxLocals(), method.maxStackSize());
            }
            graph = new CompilerGraph(null);
            new GraphBuilderPhase(compilation, method, true, true).apply(graph);
        }

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
            TTY.println("inlining %s: %d frame states, %d nodes", methodName(method), frameStates.size(), nodes.size());
        }

        assert invoke.successors().get(0) != null : invoke;
        assert invoke.predecessors().size() == 1 : "size: " + invoke.predecessors().size();
        Instruction pred;
        if (withReceiver) {
            FixedGuard clipNode = new FixedGuard(compilation.graph);
            clipNode.setNode(new IsNonNull(parameters[0], compilation.graph));
            pred = clipNode;
        } else {
            pred = new Placeholder(compilation.graph);
        }
        invoke.predecessors().get(0).successors().replace(invoke, pred);
        replacements.put(startNode, pred);

        Map<Node, Node> duplicates = compilation.graph.addDuplicate(nodes, replacements);

        for (Node node : duplicates.values()) {
            if (node instanceof Invoke) {
                parentMethod.put((Invoke) node, method);
            }
        }

        int monitorIndexDelta = stateAfter.locksSize();
        if (monitorIndexDelta > 0) {
            for (Map.Entry<Node, Node> entry : duplicates.entrySet()) {
                if (entry.getValue() instanceof MonitorAddress) {
                    MonitorAddress address = (MonitorAddress) entry.getValue();
                    address.setMonitorIndex(address.monitorIndex() + monitorIndexDelta);
                }
            }
        }

        if (pred instanceof Placeholder) {
            pred.replace(((Placeholder) pred).next());
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
            Node n = invoke.next();
            invoke.setNext(null);
            returnDuplicate.replace(n);
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
                Node n = obj.next();
                obj.setNext(null);
                unwindDuplicate.replace(n);
            }
        }

        // adjust all frame states that were copied
        if (frameStates.size() > 0) {
            FrameState outerFrameState = stateAfter.duplicateModified(invoke.bci, stateAfter.rethrowException(), invoke.kind);
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
