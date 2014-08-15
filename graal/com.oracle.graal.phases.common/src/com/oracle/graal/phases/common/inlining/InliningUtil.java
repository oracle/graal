/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.inlining;

import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.compiler.common.type.StampFactory.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.DuplicationReplacement;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.common.inlining.info.*;

public class InliningUtil {

    private static final String inliningDecisionsScopeString = "InliningDecisions";
    /**
     * Meters the size (in bytecodes) of all methods processed during compilation (i.e., top level
     * and all inlined methods), irrespective of how many bytecodes in each method are actually
     * parsed (which may be none for methods whose IR is retrieved from a cache).
     */
    public static final DebugMetric InlinedBytecodes = Debug.metric("InlinedBytecodes");

    /**
     * Print a HotSpot-style inlining message to the console.
     */
    private static void printInlining(final InlineInfo info, final int inliningDepth, final boolean success, final String msg, final Object... args) {
        printInlining(info.methodAt(0), info.invoke(), inliningDepth, success, msg, args);
    }

    /**
     * Print a HotSpot-style inlining message to the console.
     */
    private static void printInlining(final ResolvedJavaMethod method, final Invoke invoke, final int inliningDepth, final boolean success, final String msg, final Object... args) {
        if (HotSpotPrintInlining.getValue()) {
            // 1234567
            TTY.print("        ");     // print timestamp
            // 1234
            TTY.print("     ");        // print compilation number
            // % s ! b n
            TTY.print("%c%c%c%c%c ", ' ', method.isSynchronized() ? 's' : ' ', ' ', ' ', method.isNative() ? 'n' : ' ');
            TTY.print("     ");        // more indent
            TTY.print("    ");         // initial inlining indent
            for (int i = 0; i < inliningDepth; i++) {
                TTY.print("  ");
            }
            TTY.println(String.format("@ %d  %s   %s%s", invoke.bci(), methodName(method, null), success ? "" : "not inlining ", String.format(msg, args)));
        }
    }

    public static void logInlinedMethod(InlineInfo info, int inliningDepth, boolean allowLogging, String msg, Object... args) {
        logInliningDecision(info, inliningDepth, allowLogging, true, msg, args);
    }

    public static void logNotInlinedMethod(InlineInfo info, int inliningDepth, String msg, Object... args) {
        logInliningDecision(info, inliningDepth, true, false, msg, args);
    }

    public static void logInliningDecision(InlineInfo info, int inliningDepth, boolean allowLogging, boolean success, String msg, final Object... args) {
        if (allowLogging) {
            printInlining(info, inliningDepth, success, msg, args);
            if (shouldLogInliningDecision()) {
                logInliningDecision(methodName(info), success, msg, args);
            }
        }
    }

    public static void logInliningDecision(final String msg, final Object... args) {
        try (Scope s = Debug.scope(inliningDecisionsScopeString)) {
            // Can't use log here since we are varargs
            if (Debug.isLogEnabled()) {
                Debug.logv(msg, args);
            }
        }
    }

    public static void logNotInlinedMethod(Invoke invoke, String msg) {
        if (shouldLogInliningDecision()) {
            String methodString = invoke.toString() + (invoke.callTarget() == null ? " callTarget=null" : invoke.callTarget().targetName());
            logInliningDecision(methodString, false, msg, new Object[0]);
        }
    }

    public static void logNotInlined(Invoke invoke, int inliningDepth, ResolvedJavaMethod method, String msg) {
        logNotInlinedInvoke(invoke, inliningDepth, method, msg, new Object[0]);
    }

    public static void logNotInlinedInvoke(Invoke invoke, int inliningDepth, ResolvedJavaMethod method, String msg, Object... args) {
        printInlining(method, invoke, inliningDepth, false, msg, args);
        if (shouldLogInliningDecision()) {
            String methodString = methodName(method, invoke);
            logInliningDecision(methodString, false, msg, args);
        }
    }

    private static void logInliningDecision(final String methodString, final boolean success, final String msg, final Object... args) {
        String inliningMsg = "inlining " + methodString + ": " + msg;
        if (!success) {
            inliningMsg = "not " + inliningMsg;
        }
        logInliningDecision(inliningMsg, args);
    }

    public static boolean shouldLogInliningDecision() {
        try (Scope s = Debug.scope(inliningDecisionsScopeString)) {
            return Debug.isLogEnabled();
        }
    }

    private static String methodName(ResolvedJavaMethod method, Invoke invoke) {
        if (invoke != null && invoke.stateAfter() != null) {
            return methodName(invoke.stateAfter(), invoke.bci()) + ": " + method.format("%H.%n(%p):%r") + " (" + method.getCodeSize() + " bytes)";
        } else {
            return method.format("%H.%n(%p):%r") + " (" + method.getCodeSize() + " bytes)";
        }
    }

    private static String methodName(InlineInfo info) {
        if (info == null) {
            return "null";
        } else if (info.invoke() != null && info.invoke().stateAfter() != null) {
            return methodName(info.invoke().stateAfter(), info.invoke().bci()) + ": " + info.toString();
        } else {
            return info.toString();
        }
    }

    private static String methodName(FrameState frameState, int bci) {
        StringBuilder sb = new StringBuilder();
        if (frameState.outerFrameState() != null) {
            sb.append(methodName(frameState.outerFrameState(), frameState.outerFrameState().bci));
            sb.append("->");
        }
        sb.append(frameState.method().format("%h.%n"));
        sb.append("@").append(bci);
        return sb.toString();
    }

    public static void replaceInvokeCallTarget(Invoke invoke, StructuredGraph graph, InvokeKind invokeKind, ResolvedJavaMethod targetMethod) {
        MethodCallTargetNode oldCallTarget = (MethodCallTargetNode) invoke.callTarget();
        MethodCallTargetNode newCallTarget = graph.add(new MethodCallTargetNode(invokeKind, targetMethod, oldCallTarget.arguments().toArray(new ValueNode[0]), oldCallTarget.returnType()));
        invoke.asNode().replaceFirstInput(oldCallTarget, newCallTarget);
    }

    public static GuardedValueNode createAnchoredReceiver(StructuredGraph graph, GuardingNode anchor, ResolvedJavaType commonType, ValueNode receiver, boolean exact) {
        return createAnchoredReceiver(graph, anchor, receiver, exact ? StampFactory.exactNonNull(commonType) : StampFactory.declaredNonNull(commonType));
    }

    private static GuardedValueNode createAnchoredReceiver(StructuredGraph graph, GuardingNode anchor, ValueNode receiver, Stamp stamp) {
        // to avoid that floating reads on receiver fields float above the type check
        return graph.unique(new GuardedValueNode(receiver, anchor, stamp));
    }

    /**
     * @return null iff the check succeeds, otherwise a (non-null) descriptive message.
     */
    public static String checkInvokeConditions(Invoke invoke) {
        if (invoke.predecessor() == null || !invoke.asNode().isAlive()) {
            return "the invoke is dead code";
        }
        if (!(invoke.callTarget() instanceof MethodCallTargetNode)) {
            return "the invoke has already been lowered, or has been created as a low-level node";
        }
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        if (callTarget.targetMethod() == null) {
            return "target method is null";
        }
        if (invoke.stateAfter() == null) {
            // TODO (chaeubl): why should an invoke not have a state after?
            return "the invoke has no after state";
        }
        if (!invoke.useForInlining()) {
            return "the invoke is marked to be not used for inlining";
        }
        ValueNode receiver = callTarget.receiver();
        if (receiver != null && receiver.isConstant() && receiver.asConstant().isNull()) {
            return "receiver is null";
        }
        return null;
    }

    /**
     * Performs an actual inlining, thereby replacing the given invoke with the given inlineGraph.
     *
     * @param invoke the invoke that will be replaced
     * @param inlineGraph the graph that the invoke will be replaced with
     * @param receiverNullCheck true if a null check needs to be generated for non-static inlinings,
     *            false if no such check is required
     * @param canonicalizedNodes if non-null then append to this list any nodes which should be
     *            canonicalized after inlining
     */
    public static Map<Node, Node> inline(Invoke invoke, StructuredGraph inlineGraph, boolean receiverNullCheck, List<Node> canonicalizedNodes) {
        final NodeInputList<ValueNode> parameters = invoke.callTarget().arguments();
        FixedNode invokeNode = invoke.asNode();
        StructuredGraph graph = invokeNode.graph();
        assert inlineGraph.getGuardsStage().ordinal() >= graph.getGuardsStage().ordinal();
        assert !invokeNode.graph().isAfterFloatingReadPhase() : "inline isn't handled correctly after floating reads phase";

        FrameState stateAfter = invoke.stateAfter();
        assert stateAfter == null || stateAfter.isAlive();
        if (receiverNullCheck && !((MethodCallTargetNode) invoke.callTarget()).isStatic()) {
            nonNullReceiver(invoke);
        }

        ArrayList<Node> nodes = new ArrayList<>(inlineGraph.getNodes().count());
        ArrayList<ReturnNode> returnNodes = new ArrayList<>(4);
        UnwindNode unwindNode = null;
        final StartNode entryPointNode = inlineGraph.start();
        FixedNode firstCFGNode = entryPointNode.next();
        if (firstCFGNode == null) {
            throw new IllegalStateException("Inlined graph is in invalid state");
        }
        for (Node node : inlineGraph.getNodes()) {
            if (node == entryPointNode || node == entryPointNode.stateAfter() || node instanceof ParameterNode) {
                // Do nothing.
            } else {
                nodes.add(node);
                if (node instanceof ReturnNode) {
                    returnNodes.add((ReturnNode) node);
                } else if (node instanceof UnwindNode) {
                    assert unwindNode == null;
                    unwindNode = (UnwindNode) node;
                }
            }
        }

        final BeginNode prevBegin = BeginNode.prevBegin(invokeNode);
        DuplicationReplacement localReplacement = new DuplicationReplacement() {

            public Node replacement(Node node) {
                if (node instanceof ParameterNode) {
                    return parameters.get(((ParameterNode) node).index());
                } else if (node == entryPointNode) {
                    return prevBegin;
                }
                return node;
            }
        };

        assert invokeNode.successors().first() != null : invoke;
        assert invokeNode.predecessor() != null;

        Map<Node, Node> duplicates = graph.addDuplicates(nodes, inlineGraph, inlineGraph.getNodeCount(), localReplacement);
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        invokeNode.replaceAtPredecessor(firstCFGNodeDuplicate);

        FrameState stateAtExceptionEdge = null;
        if (invoke instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithException = ((InvokeWithExceptionNode) invoke);
            if (unwindNode != null) {
                assert unwindNode.predecessor() != null;
                assert invokeWithException.exceptionEdge().successors().count() == 1;
                ExceptionObjectNode obj = (ExceptionObjectNode) invokeWithException.exceptionEdge();
                stateAtExceptionEdge = obj.stateAfter();
                UnwindNode unwindDuplicate = (UnwindNode) duplicates.get(unwindNode);
                obj.replaceAtUsages(unwindDuplicate.exception());
                Node n = obj.next();
                obj.setNext(null);
                unwindDuplicate.replaceAndDelete(n);
            } else {
                invokeWithException.killExceptionEdge();
            }

            // get rid of memory kill
            BeginNode begin = invokeWithException.next();
            if (begin instanceof KillingBeginNode) {
                BeginNode newBegin = new BeginNode();
                graph.addAfterFixed(begin, graph.add(newBegin));
                begin.replaceAtUsages(newBegin);
                graph.removeFixed(begin);
            }
        } else {
            if (unwindNode != null) {
                UnwindNode unwindDuplicate = (UnwindNode) duplicates.get(unwindNode);
                DeoptimizeNode deoptimizeNode = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler));
                unwindDuplicate.replaceAndDelete(deoptimizeNode);
            }
        }

        processSimpleInfopoints(invoke, inlineGraph, duplicates);
        if (stateAfter != null) {
            processFrameStates(invoke, inlineGraph, duplicates, stateAtExceptionEdge, returnNodes.size() > 1);
            int callerLockDepth = stateAfter.nestedLockDepth();
            if (callerLockDepth != 0) {
                for (MonitorIdNode original : inlineGraph.getNodes(MonitorIdNode.class)) {
                    MonitorIdNode monitor = (MonitorIdNode) duplicates.get(original);
                    monitor.setLockDepth(monitor.getLockDepth() + callerLockDepth);
                }
            }
        } else {
            assert checkContainsOnlyInvalidOrAfterFrameState(duplicates);
        }
        if (!returnNodes.isEmpty()) {
            FixedNode n = invoke.next();
            invoke.setNext(null);
            if (returnNodes.size() == 1) {
                ReturnNode returnNode = (ReturnNode) duplicates.get(returnNodes.get(0));
                Node returnValue = returnNode.result();
                invokeNode.replaceAtUsages(returnValue);
                returnNode.replaceAndDelete(n);
            } else {
                ArrayList<ReturnNode> returnDuplicates = new ArrayList<>(returnNodes.size());
                for (ReturnNode returnNode : returnNodes) {
                    returnDuplicates.add((ReturnNode) duplicates.get(returnNode));
                }
                MergeNode merge = graph.add(new MergeNode());
                merge.setStateAfter(stateAfter);
                ValueNode returnValue = mergeReturns(merge, returnDuplicates, canonicalizedNodes);
                invokeNode.replaceAtUsages(returnValue);
                merge.setNext(n);
            }
        }

        invokeNode.replaceAtUsages(null);
        GraphUtil.killCFG(invokeNode);

        return duplicates;
    }

    private static void processSimpleInfopoints(Invoke invoke, StructuredGraph inlineGraph, Map<Node, Node> duplicates) {
        if (inlineGraph.getNodes(SimpleInfopointNode.class).isEmpty()) {
            return;
        }
        BytecodePosition pos = new BytecodePosition(toBytecodePosition(invoke.stateAfter()), invoke.asNode().graph().method(), invoke.bci());
        for (SimpleInfopointNode original : inlineGraph.getNodes(SimpleInfopointNode.class)) {
            SimpleInfopointNode duplicate = (SimpleInfopointNode) duplicates.get(original);
            duplicate.addCaller(pos);
        }
    }

    private static BytecodePosition toBytecodePosition(FrameState fs) {
        if (fs == null) {
            return null;
        }
        return new BytecodePosition(toBytecodePosition(fs.outerFrameState()), fs.method(), fs.bci);
    }

    protected static void processFrameStates(Invoke invoke, StructuredGraph inlineGraph, Map<Node, Node> duplicates, FrameState stateAtExceptionEdge, boolean alwaysDuplicateStateAfter) {
        FrameState stateAtReturn = invoke.stateAfter();
        FrameState outerFrameState = null;
        Kind invokeReturnKind = invoke.asNode().getKind();
        for (FrameState original : inlineGraph.getNodes(FrameState.class)) {
            FrameState frameState = (FrameState) duplicates.get(original);
            if (frameState != null && frameState.isAlive()) {
                if (frameState.bci == BytecodeFrame.AFTER_BCI) {
                    /*
                     * pop return kind from invoke's stateAfter and replace with this frameState's
                     * return value (top of stack)
                     */
                    FrameState stateAfterReturn = stateAtReturn;
                    if (invokeReturnKind != Kind.Void && (alwaysDuplicateStateAfter || (frameState.stackSize() > 0 && stateAfterReturn.stackAt(0) != frameState.stackAt(0)))) {
                        stateAfterReturn = stateAtReturn.duplicateModified(invokeReturnKind, frameState.stackAt(0));
                    }
                    frameState.replaceAndDelete(stateAfterReturn);
                } else if (stateAtExceptionEdge != null && isStateAfterException(frameState)) {
                    /*
                     * pop exception object from invoke's stateAfter and replace with this
                     * frameState's exception object (top of stack)
                     */
                    FrameState stateAfterException = stateAtExceptionEdge;
                    if (frameState.stackSize() > 0 && stateAtExceptionEdge.stackAt(0) != frameState.stackAt(0)) {
                        stateAfterException = stateAtExceptionEdge.duplicateModified(Kind.Object, frameState.stackAt(0));
                    }
                    frameState.replaceAndDelete(stateAfterException);
                } else if (frameState.bci == BytecodeFrame.UNWIND_BCI || frameState.bci == BytecodeFrame.AFTER_EXCEPTION_BCI) {
                    handleMissingAfterExceptionFrameState(frameState);
                } else {
                    // only handle the outermost frame states
                    if (frameState.outerFrameState() == null) {
                        assert frameState.bci != BytecodeFrame.BEFORE_BCI : frameState;
                        assert frameState.bci == BytecodeFrame.INVALID_FRAMESTATE_BCI || frameState.method().equals(inlineGraph.method());
                        assert frameState.bci != BytecodeFrame.AFTER_EXCEPTION_BCI && frameState.bci != BytecodeFrame.BEFORE_BCI && frameState.bci != BytecodeFrame.AFTER_EXCEPTION_BCI &&
                                        frameState.bci != BytecodeFrame.UNWIND_BCI : frameState.bci;
                        if (outerFrameState == null) {
                            outerFrameState = stateAtReturn.duplicateModified(invoke.bci(), stateAtReturn.rethrowException(), invokeReturnKind);
                            outerFrameState.setDuringCall(true);
                        }
                        frameState.setOuterFrameState(outerFrameState);
                    }
                }
            }
        }
    }

    private static boolean isStateAfterException(FrameState frameState) {
        return frameState.bci == BytecodeFrame.AFTER_EXCEPTION_BCI || (frameState.bci == BytecodeFrame.UNWIND_BCI && !frameState.method().isSynchronized());
    }

    protected static void handleMissingAfterExceptionFrameState(FrameState nonReplaceableFrameState) {
        Graph graph = nonReplaceableFrameState.graph();
        NodeWorkList workList = graph.createNodeWorkList();
        workList.add(nonReplaceableFrameState);
        for (Node node : workList) {
            FrameState fs = (FrameState) node;
            for (Node usage : fs.usages().snapshot()) {
                if (!usage.isAlive()) {
                    continue;
                }
                if (usage instanceof FrameState) {
                    workList.add(usage);
                } else {
                    StateSplit stateSplit = (StateSplit) usage;
                    FixedNode fixedStateSplit = stateSplit.asNode();
                    if (fixedStateSplit instanceof MergeNode) {
                        MergeNode merge = (MergeNode) fixedStateSplit;
                        while (merge.isAlive()) {
                            AbstractEndNode end = merge.forwardEnds().first();
                            DeoptimizeNode deoptimizeNode = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler));
                            end.replaceAtPredecessor(deoptimizeNode);
                            GraphUtil.killCFG(end);
                        }
                    } else {
                        FixedNode deoptimizeNode = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler));
                        if (fixedStateSplit instanceof BeginNode) {
                            deoptimizeNode = BeginNode.begin(deoptimizeNode);
                        }
                        fixedStateSplit.replaceAtPredecessor(deoptimizeNode);
                        GraphUtil.killCFG(fixedStateSplit);
                    }
                }
            }
        }
    }

    public static ValueNode mergeReturns(MergeNode merge, List<? extends ReturnNode> returnNodes, List<Node> canonicalizedNodes) {
        PhiNode returnValuePhi = null;

        for (ReturnNode returnNode : returnNodes) {
            // create and wire up a new EndNode
            EndNode endNode = merge.graph().add(new EndNode());
            merge.addForwardEnd(endNode);

            if (returnNode.result() != null) {
                if (returnValuePhi == null) {
                    returnValuePhi = merge.graph().addWithoutUnique(new ValuePhiNode(returnNode.result().stamp().unrestricted(), merge));
                    if (canonicalizedNodes != null) {
                        canonicalizedNodes.add(returnValuePhi);
                    }
                }
                returnValuePhi.addInput(returnNode.result());
            }
            returnNode.replaceAndDelete(endNode);

        }
        return returnValuePhi;
    }

    private static boolean checkContainsOnlyInvalidOrAfterFrameState(Map<Node, Node> duplicates) {
        for (Node node : duplicates.values()) {
            if (node instanceof FrameState) {
                FrameState frameState = (FrameState) node;
                assert frameState.bci == BytecodeFrame.AFTER_BCI || frameState.bci == BytecodeFrame.INVALID_FRAMESTATE_BCI : node.toString(Verbosity.Debugger);
            }
        }
        return true;
    }

    /**
     * Gets the receiver for an invoke, adding a guard if necessary to ensure it is non-null.
     */
    public static ValueNode nonNullReceiver(Invoke invoke) {
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        assert !callTarget.isStatic() : callTarget.targetMethod();
        StructuredGraph graph = callTarget.graph();
        ValueNode firstParam = callTarget.arguments().get(0);
        if (firstParam.getKind() == Kind.Object && !StampTool.isObjectNonNull(firstParam)) {
            IsNullNode condition = graph.unique(new IsNullNode(firstParam));
            Stamp stamp = firstParam.stamp().join(objectNonNull());
            GuardingPiNode nonNullReceiver = graph.add(new GuardingPiNode(firstParam, condition, true, NullCheckException, InvalidateReprofile, stamp));
            graph.addBeforeFixed(invoke.asNode(), nonNullReceiver);
            callTarget.replaceFirstInput(firstParam, nonNullReceiver);
            return nonNullReceiver;
        }
        return firstParam;
    }

    public static boolean canIntrinsify(Replacements replacements, ResolvedJavaMethod target) {
        return getIntrinsicGraph(replacements, target) != null || getMacroNodeClass(replacements, target) != null;
    }

    public static StructuredGraph getIntrinsicGraph(Replacements replacements, ResolvedJavaMethod target) {
        return replacements.getMethodSubstitution(target);
    }

    public static Class<? extends FixedWithNextNode> getMacroNodeClass(Replacements replacements, ResolvedJavaMethod target) {
        return replacements.getMacroSubstitution(target);
    }

    public static FixedWithNextNode inlineMacroNode(Invoke invoke, ResolvedJavaMethod concrete, Class<? extends FixedWithNextNode> macroNodeClass) throws GraalInternalError {
        StructuredGraph graph = invoke.asNode().graph();
        if (!concrete.equals(((MethodCallTargetNode) invoke.callTarget()).targetMethod())) {
            assert ((MethodCallTargetNode) invoke.callTarget()).invokeKind() != InvokeKind.Static;
            InliningUtil.replaceInvokeCallTarget(invoke, graph, InvokeKind.Special, concrete);
        }

        FixedWithNextNode macroNode = createMacroNodeInstance(macroNodeClass, invoke);

        CallTargetNode callTarget = invoke.callTarget();
        if (invoke instanceof InvokeNode) {
            graph.replaceFixedWithFixed((InvokeNode) invoke, graph.add(macroNode));
        } else {
            InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
            invokeWithException.killExceptionEdge();
            graph.replaceSplitWithFixed(invokeWithException, graph.add(macroNode), invokeWithException.next());
        }
        GraphUtil.killWithUnusedFloatingInputs(callTarget);
        return macroNode;
    }

    private static FixedWithNextNode createMacroNodeInstance(Class<? extends FixedWithNextNode> macroNodeClass, Invoke invoke) throws GraalInternalError {
        try {
            return macroNodeClass.getConstructor(Invoke.class).newInstance(invoke);
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
            throw new GraalGraphInternalError(e).addContext(invoke.asNode()).addContext("macroSubstitution", macroNodeClass);
        }
    }
}
