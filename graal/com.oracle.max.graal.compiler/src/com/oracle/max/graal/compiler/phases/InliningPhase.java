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
import com.oracle.max.graal.compiler.ir.Deoptimize.DeoptAction;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class InliningPhase extends Phase {

    public static final HashMap<RiMethod, Integer> methodCount = new HashMap<RiMethod, Integer>();

    private final GraalCompilation compilation;
    private final IR ir;

    private int inliningSize;
    private final Collection<Invoke> hints;

    public InliningPhase(GraalCompilation compilation, IR ir, Collection<Invoke> hints) {
        this.compilation = compilation;
        this.ir = ir;
        this.hints = hints;
    }

    private Queue<Invoke> newInvokes = new ArrayDeque<Invoke>();
    private CompilerGraph graph;

    @Override
    protected void run(Graph graph) {
        this.graph = (CompilerGraph) graph;

        float ratio = GraalOptions.MaximumInlineRatio;
        inliningSize = compilation.method.codeSize();

        if (hints != null) {
            newInvokes.addAll(hints);
        } else {
            for (Invoke invoke : graph.getNodes(Invoke.class)) {
                newInvokes.add(invoke);
            }
        }

        for (int iterations = 0; iterations < GraalOptions.MaximumInlineLevel; iterations++) {
            Queue<Invoke> queue = newInvokes;
            newInvokes = new ArrayDeque<Invoke>();
            for (Invoke invoke : queue) {
                if (!invoke.isDeleted()) {
                    RiMethod code = inlineInvoke(invoke, iterations, ratio);
                    if (code != null) {
                        if (graph.getNodeCount() > GraalOptions.MaximumInstructionCount) {
                            break;
                        }

                        inlineMethod(invoke, code);
                        if (GraalOptions.TraceInlining) {
                            if (methodCount.get(code) == null) {
                                methodCount.put(code, 1);
                            } else {
                                methodCount.put(code, methodCount.get(code) + 1);
                            }
                        }
                    }
                }
            }
            if (newInvokes.isEmpty()) {
                break;
            }

//            new DeadCodeEliminationPhase().apply(graph);

            ratio *= GraalOptions.MaximumInlineRatio;
        }

        if (GraalOptions.TraceInlining) {
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

    private RiMethod inlineInvoke(Invoke invoke, int iterations, float ratio) {
        RiMethod parent = invoke.stateAfter().method();
        RiTypeProfile profile = parent.typeProfile(invoke.bci);
        if (!checkInvokeConditions(invoke)) {
            return null;
        }
        if (GraalOptions.Intrinsify && compilation.runtime.intrinsicGraph(invoke.target, invoke.arguments()) != null) {
            // Always intrinsify.
            return invoke.target;
        }
        if (invoke.opcode() == Bytecodes.INVOKESPECIAL || invoke.target.canBeStaticallyBound()) {
            if (checkTargetConditions(invoke.target, iterations) && checkSizeConditions(invoke.target, invoke, profile, ratio)) {
                return invoke.target;
            }
            return null;
        }
        if (invoke.receiver().exactType() != null) {
            RiType exact = invoke.receiver().exactType();
            assert exact.isSubtypeOf(invoke.target().holder()) : exact + " subtype of " + invoke.target().holder();
            RiMethod resolved = exact.resolveMethodImpl(invoke.target());
            if (checkTargetConditions(resolved, iterations) && checkSizeConditions(resolved, invoke, profile, ratio)) {
                return resolved;
            }
            return null;
        }
        RiType holder = invoke.target().holder();

        if (invoke.receiver().declaredType() != null) {
            RiType declared = invoke.receiver().declaredType();
            // the invoke target might be more specific than the holder (happens after inlining: locals lose their declared type...)
            // TODO (ls) fix this
            if (declared.isResolved() && declared.isSubtypeOf(invoke.target().holder())) {
                holder = declared;
            }
        }

        RiMethod concrete = holder.uniqueConcreteMethod(invoke.target);
        if (concrete != null) {
            if (checkTargetConditions(concrete, iterations) && checkSizeConditions(concrete, invoke, profile, ratio)) {
                if (GraalOptions.TraceInlining) {
                    String targetName = CiUtil.format("%H.%n(%p):%r", invoke.target, false);
                    String concreteName = CiUtil.format("%H.%n(%p):%r", concrete, false);
                    TTY.println("recording concrete method assumption: %s -> %s", targetName, concreteName);
                }
                graph.assumptions().recordConcreteMethod(invoke.target, concrete);
                return concrete;
            }
            return null;
        }
        if (profile != null && profile.probabilities != null && profile.probabilities.length > 0 && profile.morphism == 1) {
            if (GraalOptions.InlineWithTypeCheck) {
                // type check and inlining...
                concrete = profile.types[0].resolveMethodImpl(invoke.target);
                if (concrete != null && checkTargetConditions(concrete, iterations) && checkSizeConditions(concrete, invoke, profile, ratio)) {
                    IsType isType = new IsType(invoke.receiver(), profile.types[0], compilation.graph);
                    FixedGuard guard = new FixedGuard(isType, graph);
                    assert invoke.predecessors().size() == 1;
                    invoke.predecessors().get(0).successors().replace(invoke, guard);
                    guard.setNext(invoke);

                    if (GraalOptions.TraceInlining) {
                        TTY.println("inlining with type check, type probability: %5.3f", profile.probabilities[0]);
                    }
                    return concrete;
                }
                return null;
            } else {
                if (GraalOptions.TraceInlining) {
                    TTY.println("not inlining %s because GraalOptions.InlineWithTypeCheck == false", methodName(invoke.target, invoke));
                }
                return null;
            }
        } else {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because no monomorphic receiver could be found", methodName(invoke.target, invoke));
            }
            return null;
        }
    }

    private String methodName(RiMethod method) {
        return CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.codeSize() + " bytes)";
    }

    private String methodName(RiMethod method, Invoke invoke) {
        if (invoke != null) {
            RiMethod parent = invoke.stateAfter().method();
            return parent.name() + "@" + invoke.bci + ": " + CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.codeSize() + " bytes)";
        } else {
            return CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.codeSize() + " bytes)";
        }
    }

    private boolean checkInvokeConditions(Invoke invoke) {
        if (invoke.stateAfter() == null) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because the invoke has no after state", methodName(invoke.target, invoke));
            }
            return false;
        }
        if (invoke.stateAfter().locksSize() > 0) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because of locks", methodName(invoke.target, invoke));
            }
            return false;
        }
        if (!invoke.target.isResolved()) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because the invoke target is unresolved", methodName(invoke.target, invoke));
            }
            return false;
        }
        if (invoke.predecessors().size() == 0) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because the invoke is dead code", methodName(invoke.target, invoke));
            }
            return false;
        }
        if (invoke.stateAfter() == null) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because of missing frame state", methodName(invoke.target, invoke));
            }
        }
        return true;
    }

    private boolean checkTargetConditions(RiMethod method, int iterations) {
        if (!method.isResolved()) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because it is unresolved", methodName(method));
            }
            return false;
        }
        if (Modifier.isNative(method.accessFlags())) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because it is a native method", methodName(method));
            }
            return false;
        }
        if (Modifier.isAbstract(method.accessFlags())) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because it is an abstract method", methodName(method));
            }
            return false;
        }
        if (!method.holder().isInitialized()) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because of non-initialized class", methodName(method));
            }
            return false;
        }
        if (method == compilation.method && iterations > GraalOptions.MaximumRecursiveInlineLevel) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because of recursive inlining limit", methodName(method));
            }
            return false;
        }
        return true;
    }

    private boolean checkStaticSizeConditions(RiMethod method, Invoke invoke) {
        int maximumSize = GraalOptions.MaximumInlineSize;
        if (hints != null && hints.contains(invoke)) {
            maximumSize = GraalOptions.MaximumFreqInlineSize;
        }
        if (method.codeSize() > maximumSize) {
            if (GraalOptions.TraceInlining) {
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
            RiMethod parent = invoke.stateAfter().method();
            ratio = profile.count / (float) parent.invocationCount();
            if (ratio >= GraalOptions.FreqInlineRatio) {
                maximumSize = GraalOptions.MaximumFreqInlineSize;
            } else if (ratio >= (1 - adjustedRatio)) {
                maximumSize = GraalOptions.MaximumInlineSize;
            }
        }
        if (hints != null && hints.contains(invoke)) {
            maximumSize = GraalOptions.MaximumFreqInlineSize;
        }
        if (method.codeSize() > maximumSize) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because of code size (size: %d, max size: %d, ratio %5.3f, %s)", methodName(method, invoke), method.codeSize(), maximumSize, ratio, profile);
            }
            return false;
        }
        if (GraalOptions.TraceInlining) {
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

        boolean withReceiver = !Modifier.isStatic(method.accessFlags());

        int argumentCount = method.signature().argumentCount(false);
        Value[] parameters = new Value[argumentCount + (withReceiver ? 1 : 0)];
        int slot = withReceiver ? 1 : 0;
        int param = withReceiver ? 1 : 0;
        for (int i = 0; i < argumentCount; i++) {
            parameters[param++] = invoke.arguments().get(slot);
            slot += method.signature().argumentKindAt(i).sizeInSlots();
        }
        if (withReceiver) {
            parameters[0] = invoke.arguments().get(0);
        }

        CompilerGraph graph = null;
        if (GraalOptions.Intrinsify) {
            graph = (CompilerGraph) compilation.runtime.intrinsicGraph(method, invoke.arguments());
        }
        if (graph != null) {
            if (GraalOptions.TraceInlining) {
                TTY.println("Using intrinsic graph");
            }
        } else {
            graph = GraphBuilderPhase.cachedGraphs.get(method);
        }

        if (graph != null) {
            if (GraalOptions.TraceInlining) {
                TTY.println("Reusing graph for %s, locals: %d, stack: %d", methodName(method, invoke), method.maxLocals(), method.maxStackSize());
            }
        } else {
            if (GraalOptions.TraceInlining) {
                TTY.println("Building graph for %s, locals: %d, stack: %d", methodName(method, invoke), method.maxLocals(), method.maxStackSize());
            }
            graph = new CompilerGraph(null);
            new GraphBuilderPhase(compilation, method, true, true).apply(graph);
        }

        invoke.inputs().clearAll();

        HashMap<Node, Node> replacements = new HashMap<Node, Node>();
        ArrayList<Node> nodes = new ArrayList<Node>();
        ArrayList<Node> frameStates = new ArrayList<Node>();
        Return returnNode = null;
        Unwind unwindNode = null;
        StartNode startNode = graph.start();
        for (Node node : graph.getNodes()) {
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

        if (GraalOptions.TraceInlining) {
            TTY.println("inlining %s: %d frame states, %d nodes", methodName(method), frameStates.size(), nodes.size());
        }

        assert invoke.successors().get(0) != null : invoke;
        assert invoke.predecessors().size() == 1 : "size: " + invoke.predecessors().size();
        FixedNodeWithNext pred;
        if (withReceiver) {
            pred = new FixedGuard(new IsNonNull(parameters[0], compilation.graph), compilation.graph);
        } else {
            pred = new Placeholder(compilation.graph);
        }
        invoke.predecessors().get(0).successors().replace(invoke, pred);
        replacements.put(startNode, pred);

        Map<Node, Node> duplicates = compilation.graph.addDuplicate(nodes, replacements);

        for (Node node : duplicates.values()) {
            if (node instanceof Invoke) {
                newInvokes.add((Invoke) node);
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
            pred.replaceAndDelete(((Placeholder) pred).next());
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
            returnDuplicate.replaceAndDelete(n);
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
                unwindDuplicate.replaceAndDelete(n);
            }
        } else {
            if (unwindNode != null) {
                Unwind unwindDuplicate = (Unwind) duplicates.get(unwindNode);
                unwindDuplicate.replaceAndDelete(new Deoptimize(DeoptAction.InvalidateRecompile, compilation.graph));
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

        if (GraalOptions.TraceInlining) {
            ir.printGraph("After inlining " + CiUtil.format("%H.%n(%p):%r", method, false), compilation.graph);
        }
    }
}
