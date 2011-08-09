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
import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.base.DeoptimizeNode.DeoptAction;
import com.oracle.max.graal.compiler.nodes.calc.*;
import com.oracle.max.graal.compiler.nodes.java.*;
import com.oracle.max.graal.extensions.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class InliningPhase extends Phase {
    /*
     * - Detect method which only call another method with some parameters set to constants: void foo(a) -> void foo(a, b) -> void foo(a, b, c) ...
     *   These should not be taken into account when determining inlining depth.
     */

    public static final HashMap<RiMethod, Integer> methodCount = new HashMap<RiMethod, Integer>();

    private static final int MAX_ITERATIONS = 1000;

    private final GraalCompilation compilation;
    private final IR ir;

    private int inliningSize;
    private final Collection<InvokeNode> hints;

    public InliningPhase(GraalCompilation compilation, IR ir, Collection<InvokeNode> hints) {
        this.compilation = compilation;
        this.ir = ir;
        this.hints = hints;
    }

    private Queue<InvokeNode> newInvokes = new ArrayDeque<InvokeNode>();
    private CompilerGraph graph;

    @Override
    protected void run(Graph graph) {
        this.graph = (CompilerGraph) graph;

        float ratio = GraalOptions.MaximumInlineRatio;
        inliningSize = compilation.method.codeSize();

        if (hints != null) {
            newInvokes.addAll(hints);
        } else {
            for (InvokeNode invoke : graph.getNodes(InvokeNode.class)) {
                newInvokes.add(invoke);
            }
        }

        for (int iterations = 0; iterations < MAX_ITERATIONS; iterations++) {
            Queue<InvokeNode> queue = newInvokes;
            newInvokes = new ArrayDeque<InvokeNode>();
            for (InvokeNode invoke : queue) {
                if (!invoke.isDeleted()) {
                    if (GraalOptions.Meter) {
                        GraalMetrics.InlineConsidered++;
                        if (!invoke.target.hasCompiledCode()) {
                            GraalMetrics.InlineUncompiledConsidered++;
                        }
                    }

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
                        if (GraalOptions.Meter) {
                            GraalMetrics.InlinePerformed++;
                            if (!invoke.target.hasCompiledCode()) {
                                GraalMetrics.InlineUncompiledPerformed++;
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

    private RiMethod inlineInvoke(InvokeNode invoke, int iterations, float ratio) {
        RiMethod parent = invoke.stateAfter().method();
        RiTypeProfile profile = parent.typeProfile(invoke.bci);
        if (GraalOptions.Intrinsify) {
            if (GraalOptions.Extend && intrinsicGraph(parent, invoke.bci, invoke.target, invoke.arguments()) != null) {
                return invoke.target;
            }
            if (compilation.runtime.intrinsicGraph(parent, invoke.bci, invoke.target, invoke.arguments()) != null) {
                // Always intrinsify.
                return invoke.target;
            }
        }
        if (!checkInvokeConditions(invoke)) {
            return null;
        }
        if (invoke.opcode() == Bytecodes.INVOKESPECIAL || invoke.target.canBeStaticallyBound()) {
            if (checkTargetConditions(invoke.target, iterations) && checkSizeConditions(parent, iterations, invoke.target, invoke, profile, ratio)) {
                return invoke.target;
            }
            return null;
        }
        if (invoke.receiver().exactType() != null) {
            RiType exact = invoke.receiver().exactType();
            assert exact.isSubtypeOf(invoke.target().holder()) : exact + " subtype of " + invoke.target().holder();
            RiMethod resolved = exact.resolveMethodImpl(invoke.target());
            if (checkTargetConditions(resolved, iterations) && checkSizeConditions(parent, iterations, resolved, invoke, profile, ratio)) {
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
            if (checkTargetConditions(concrete, iterations) && checkSizeConditions(parent, iterations, concrete, invoke, profile, ratio)) {
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
                if (concrete != null && checkTargetConditions(concrete, iterations) && checkSizeConditions(parent, iterations, concrete, invoke, profile, ratio)) {
                    IsTypeNode isType = new IsTypeNode(invoke.receiver(), profile.types[0], compilation.graph);
                    FixedGuardNode guard = new FixedGuardNode(isType, graph);
                    assert invoke.predecessor() != null;
                    invoke.predecessor().replaceFirstSuccessor(invoke, guard);
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

    private String methodName(RiMethod method, InvokeNode invoke) {
        if (invoke != null) {
            RiMethod parent = invoke.stateAfter().method();
            return parent.name() + "@" + invoke.bci + ": " + CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.codeSize() + " bytes)";
        } else {
            return CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.codeSize() + " bytes)";
        }
    }

    private boolean checkInvokeConditions(InvokeNode invoke) {
        if (!invoke.canInline()) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because the invoke is manually set to be non-inlinable", methodName(invoke.target, invoke));
            }
            return false;
        }
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
        if (invoke.predecessor() == null) {
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
        return true;
    }

    private boolean checkStaticSizeConditions(RiMethod method, InvokeNode invoke) {
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

    private boolean checkSizeConditions(RiMethod caller, int iterations, RiMethod method, InvokeNode invoke, RiTypeProfile profile, float adjustedRatio) {
        int maximumSize = GraalOptions.MaximumTrivialSize;
        int maximumCompiledSize = GraalOptions.MaximumTrivialCompSize;
        double ratio = 0;
        if (profile != null && profile.count > 0) {
            RiMethod parent = invoke.stateAfter().method();
            if (GraalOptions.ProbabilityAnalysis) {
                ratio = invoke.probability();
            } else {
                ratio = profile.count / (float) parent.invocationCount();
            }
            if (ratio >= GraalOptions.FreqInlineRatio) {
                maximumSize = GraalOptions.MaximumFreqInlineSize;
                maximumCompiledSize = GraalOptions.MaximumFreqInlineCompSize;
            } else if (ratio >= 1 * (1 - adjustedRatio)) {
                maximumSize = GraalOptions.MaximumInlineSize;
                maximumCompiledSize = GraalOptions.MaximumInlineCompSize;
            }
        }
        if (hints != null && hints.contains(invoke)) {
            maximumSize = GraalOptions.MaximumFreqInlineSize;
            maximumCompiledSize = GraalOptions.MaximumFreqInlineCompSize;
        }
        boolean oversize;
        int compiledSize = method.compiledCodeSize();
        if (compiledSize < 0) {
            oversize = (method.codeSize() > maximumSize);
        } else {
            oversize = (compiledSize > maximumCompiledSize);
        }
        if (oversize || iterations >= GraalOptions.MaximumInlineLevel || (method == compilation.method && iterations > GraalOptions.MaximumRecursiveInlineLevel)) {
            if (GraalOptions.TraceInlining) {
                TTY.println("not inlining %s because of size (bytecode: %d, bytecode max: %d, compiled: %d, compiled max: %d, ratio %5.3f, %s) or inlining level",
                                methodName(method, invoke), method.codeSize(), maximumSize, compiledSize, maximumCompiledSize, ratio, profile);
            }
            if (GraalOptions.Extend) {
                boolean newResult = overrideInliningDecision(iterations, caller, invoke.bci, method, false);
                if (GraalOptions.TraceInlining && newResult) {
                    TTY.println("overridden inlining decision");
                }
                return newResult;
            }
            return false;
        }
        if (GraalOptions.TraceInlining) {
            TTY.println("inlining %s (size: %d, max size: %d, ratio %5.3f, %s)", methodName(method, invoke), method.codeSize(), maximumSize, ratio, profile);
        }
        if (GraalOptions.Extend) {
            boolean newResult = overrideInliningDecision(iterations, caller, invoke.bci, method, true);
            if (GraalOptions.TraceInlining && !newResult) {
                TTY.println("overridden inlining decision");
            }
            return newResult;
        }
        return true;
    }

    public static ThreadLocal<ServiceLoader<InliningGuide>> guideLoader = new ThreadLocal<ServiceLoader<InliningGuide>>();

    private boolean overrideInliningDecision(int iteration, RiMethod caller, int bci, RiMethod target, boolean previousDecision) {
        ServiceLoader<InliningGuide> serviceLoader = guideLoader.get();
        if (serviceLoader == null) {
            serviceLoader = ServiceLoader.load(InliningGuide.class);
            guideLoader.set(serviceLoader);
        }

        boolean neverInline = false;
        boolean alwaysInline = false;
        for (InliningGuide guide : serviceLoader) {
            InliningHint hint = guide.getHint(iteration, caller, bci, target);

            if (hint == InliningHint.ALWAYS) {
                alwaysInline = true;
            } else if (hint == InliningHint.NEVER) {
                neverInline = true;
            }
        }

        if (neverInline && alwaysInline) {
            if (GraalOptions.TraceInlining) {
                TTY.println("conflicting inlining hints");
            }
        } else if (neverInline) {
            return false;
        } else if (alwaysInline) {
            return true;
        }
        return previousDecision;
    }


    public static ThreadLocal<ServiceLoader<Intrinsifier>> intrinsicLoader = new ThreadLocal<ServiceLoader<Intrinsifier>>();

    private Graph intrinsicGraph(RiMethod parent, int bci, RiMethod target, List<ValueNode> arguments) {
        ServiceLoader<Intrinsifier> serviceLoader = intrinsicLoader.get();
        if (serviceLoader == null) {
            serviceLoader = ServiceLoader.load(Intrinsifier.class);
            intrinsicLoader.set(serviceLoader);
        }

        for (Intrinsifier intrinsifier : serviceLoader) {
            Graph result = intrinsifier.intrinsicGraph(compilation.runtime, parent, bci, target, arguments);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private void inlineMethod(InvokeNode invoke, RiMethod method) {
        RiMethod parent = invoke.stateAfter().method();
        FrameState stateAfter = invoke.stateAfter();
        FixedNode exceptionEdge = invoke.exceptionEdge();
        if (exceptionEdge instanceof PlaceholderNode) {
            exceptionEdge = ((PlaceholderNode) exceptionEdge).next();
        }

        boolean withReceiver = !invoke.isStatic();

        int argumentCount = method.signature().argumentCount(false);
        ValueNode[] parameters = new ValueNode[argumentCount + (withReceiver ? 1 : 0)];
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
            if (GraalOptions.Extend) {
                graph = (CompilerGraph) intrinsicGraph(parent, invoke.bci, method, invoke.arguments());
            }
            if (graph == null) {
                graph = (CompilerGraph) compilation.runtime.intrinsicGraph(parent, invoke.bci, method, invoke.arguments());
            }
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
                TTY.println("Reusing graph for %s", methodName(method, invoke));
            }
        } else {
            if (GraalOptions.TraceInlining) {
                TTY.println("Building graph for %s, locals: %d, stack: %d", methodName(method, invoke), method.maxLocals(), method.maxStackSize());
            }
            graph = new CompilerGraph(null);
            new GraphBuilderPhase(compilation, method, true).apply(graph, true, false);
            if (GraalOptions.ProbabilityAnalysis) {
                new DeadCodeEliminationPhase().apply(graph, true, false);
                new ComputeProbabilityPhase().apply(graph, true, false);
            }
        }

        invoke.clearInputs();

        HashMap<Node, Node> replacements = new HashMap<Node, Node>();
        ArrayList<Node> nodes = new ArrayList<Node>();
        ArrayList<Node> frameStates = new ArrayList<Node>();
        ReturnNode returnNode = null;
        UnwindNode unwindNode = null;
        StartNode startNode = graph.start();
        for (Node node : graph.getNodes()) {
            if (node instanceof StartNode) {
                assert startNode == node;
            } else if (node instanceof LocalNode) {
                replacements.put(node, parameters[((LocalNode) node).index()]);
            } else {
                nodes.add(node);
                if (node instanceof ReturnNode) {
                    returnNode = (ReturnNode) node;
                } else if (node instanceof UnwindNode) {
                    unwindNode = (UnwindNode) node;
                } else if (node instanceof FrameState) {
                    frameStates.add(node);
                }
            }
        }

        if (GraalOptions.TraceInlining) {
            TTY.println("inlining %s: %d frame states, %d nodes", methodName(method), frameStates.size(), nodes.size());
        }

        assert invoke.successors().first() != null : invoke;
        assert invoke.predecessor() != null;
        FixedWithNextNode pred;
        if (withReceiver) {
            pred = new FixedGuardNode(new IsNonNullNode(parameters[0], compilation.graph), compilation.graph);
        } else {
            pred = new PlaceholderNode(compilation.graph);
        }
        invoke.replaceAtPredecessors(pred);
        replacements.put(startNode, pred);

        Map<Node, Node> duplicates = compilation.graph.addDuplicate(nodes, replacements);

        FrameState stateBefore = null;
        double invokeProbability = invoke.probability();
        for (Node node : duplicates.values()) {
            if (GraalOptions.ProbabilityAnalysis) {
                if (node instanceof FixedNode) {
                    FixedNode fixed = (FixedNode) node;
                    fixed.setProbability(fixed.probability() * invokeProbability);
                }
            }
            if (node instanceof InvokeNode && ((InvokeNode) node).canInline()) {
                newInvokes.add((InvokeNode) node);
            } else if (node instanceof FrameState) {
                FrameState frameState = (FrameState) node;
                if (frameState.bci == FrameState.BEFORE_BCI) {
                    if (stateBefore == null) {
                        stateBefore = stateAfter.duplicateModified(invoke.bci, false, invoke.kind, parameters);
                    }
                    frameState.replaceAndDelete(stateBefore);
                } else if (frameState.bci == FrameState.AFTER_BCI) {
                    frameState.replaceAndDelete(stateAfter);
                }
            }
        }

        int monitorIndexDelta = stateAfter.locksSize();
        if (monitorIndexDelta > 0) {
            for (Map.Entry<Node, Node> entry : duplicates.entrySet()) {
                if (entry.getValue() instanceof MonitorAddressNode) {
                    MonitorAddressNode address = (MonitorAddressNode) entry.getValue();
                    address.setMonitorIndex(address.monitorIndex() + monitorIndexDelta);
                }
            }
        }

        if (pred instanceof PlaceholderNode) {
            FixedNode next = ((PlaceholderNode) pred).next();
            ((PlaceholderNode) pred).setNext(null);
            pred.replaceAndDelete(next);
        }

        if (returnNode != null) {
            for (Node usage : invoke.usages().snapshot()) {
                if (returnNode.result() instanceof LocalNode) {
                    usage.replaceFirstInput(invoke, replacements.get(returnNode.result()));
                } else {
                    usage.replaceFirstInput(invoke, duplicates.get(returnNode.result()));
                }
            }
            Node returnDuplicate = duplicates.get(returnNode);
            returnDuplicate.clearInputs();
            Node n = invoke.next();
            invoke.setNext(null);
            returnDuplicate.replaceAndDelete(n);
        }

        if (exceptionEdge != null) {
            if (unwindNode != null) {
                assert unwindNode.predecessor() != null;
                assert exceptionEdge.successors().explicitCount() == 1;
                ExceptionObjectNode obj = (ExceptionObjectNode) exceptionEdge;

                UnwindNode unwindDuplicate = (UnwindNode) duplicates.get(unwindNode);
                for (Node usage : obj.usages().snapshot()) {
                    usage.replaceFirstInput(obj, unwindDuplicate.exception());
                }
                unwindDuplicate.clearInputs();
                Node n = obj.next();
                obj.setNext(null);
                unwindDuplicate.replaceAndDelete(n);
            }
        } else {
            if (unwindNode != null) {
                UnwindNode unwindDuplicate = (UnwindNode) duplicates.get(unwindNode);
                unwindDuplicate.replaceAndDelete(new DeoptimizeNode(DeoptAction.InvalidateRecompile, compilation.graph));
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
