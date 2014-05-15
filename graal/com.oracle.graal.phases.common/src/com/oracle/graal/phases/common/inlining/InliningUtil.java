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
import com.oracle.graal.api.code.Assumptions.Assumption;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.JavaTypeProfile.ProfiledType;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.DuplicationReplacement;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.inlining.info.*;
import com.oracle.graal.phases.common.inlining.walker.InliningData;

public class InliningUtil {

    private static final String inliningDecisionsScopeString = "InliningDecisions";
    /**
     * Meters the size (in bytecodes) of all methods processed during compilation (i.e., top level
     * and all inlined methods), irrespective of how many bytecodes in each method are actually
     * parsed (which may be none for methods whose IR is retrieved from a cache).
     */
    public static final DebugMetric InlinedBytecodes = Debug.metric("InlinedBytecodes");

    public interface Inlineable {

        int getNodeCount();

        Iterable<Invoke> getInvokes();
    }

    public static class InlineableGraph implements Inlineable {

        private final StructuredGraph graph;

        public InlineableGraph(StructuredGraph graph) {
            this.graph = graph;
        }

        @Override
        public int getNodeCount() {
            return graph.getNodeCount();
        }

        @Override
        public Iterable<Invoke> getInvokes() {
            return graph.getInvokes();
        }

        public StructuredGraph getGraph() {
            return graph;
        }
    }

    public static class InlineableMacroNode implements Inlineable {

        private final Class<? extends FixedWithNextNode> macroNodeClass;

        public InlineableMacroNode(Class<? extends FixedWithNextNode> macroNodeClass) {
            this.macroNodeClass = macroNodeClass;
        }

        @Override
        public int getNodeCount() {
            return 1;
        }

        @Override
        public Iterable<Invoke> getInvokes() {
            return Collections.emptyList();
        }

        public Class<? extends FixedWithNextNode> getMacroNodeClass() {
            return macroNodeClass;
        }
    }

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

    public static boolean logInlinedMethod(InlineInfo info, int inliningDepth, boolean allowLogging, String msg, Object... args) {
        return logInliningDecision(info, inliningDepth, allowLogging, true, msg, args);
    }

    public static boolean logNotInlinedMethod(InlineInfo info, int inliningDepth, String msg, Object... args) {
        return logInliningDecision(info, inliningDepth, true, false, msg, args);
    }

    public static boolean logInliningDecision(InlineInfo info, int inliningDepth, boolean allowLogging, boolean success, String msg, final Object... args) {
        if (allowLogging) {
            printInlining(info, inliningDepth, success, msg, args);
            if (shouldLogInliningDecision()) {
                logInliningDecision(methodName(info), success, msg, args);
            }
        }
        return success;
    }

    public static void logInliningDecision(final String msg, final Object... args) {
        try (Scope s = Debug.scope(inliningDecisionsScopeString)) {
            // Can't use log here since we are varargs
            if (Debug.isLogEnabled()) {
                Debug.logv(msg, args);
            }
        }
    }

    private static boolean logNotInlinedMethod(Invoke invoke, String msg) {
        if (shouldLogInliningDecision()) {
            String methodString = invoke.toString() + (invoke.callTarget() == null ? " callTarget=null" : invoke.callTarget().targetName());
            logInliningDecision(methodString, false, msg, new Object[0]);
        }
        return false;
    }

    private static InlineInfo logNotInlinedMethodAndReturnNull(Invoke invoke, int inliningDepth, ResolvedJavaMethod method, String msg) {
        return logNotInlinedMethodAndReturnNull(invoke, inliningDepth, method, msg, new Object[0]);
    }

    private static InlineInfo logNotInlinedMethodAndReturnNull(Invoke invoke, int inliningDepth, ResolvedJavaMethod method, String msg, Object... args) {
        printInlining(method, invoke, inliningDepth, false, msg, args);
        if (shouldLogInliningDecision()) {
            String methodString = methodName(method, invoke);
            logInliningDecision(methodString, false, msg, args);
        }
        return null;
    }

    private static boolean logNotInlinedMethodAndReturnFalse(Invoke invoke, int inliningDepth, ResolvedJavaMethod method, String msg) {
        printInlining(method, invoke, inliningDepth, false, msg, new Object[0]);
        if (shouldLogInliningDecision()) {
            String methodString = methodName(method, invoke);
            logInliningDecision(methodString, false, msg, new Object[0]);
        }
        return false;
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
            return methodName(invoke.stateAfter(), invoke.bci()) + ": " + MetaUtil.format("%H.%n(%p):%r", method) + " (" + method.getCodeSize() + " bytes)";
        } else {
            return MetaUtil.format("%H.%n(%p):%r", method) + " (" + method.getCodeSize() + " bytes)";
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
        sb.append(MetaUtil.format("%h.%n", frameState.method()));
        sb.append("@").append(bci);
        return sb.toString();
    }

    public static void replaceInvokeCallTarget(Invoke invoke, StructuredGraph graph, InvokeKind invokeKind, ResolvedJavaMethod targetMethod) {
        MethodCallTargetNode oldCallTarget = (MethodCallTargetNode) invoke.callTarget();
        MethodCallTargetNode newCallTarget = graph.add(new MethodCallTargetNode(invokeKind, targetMethod, oldCallTarget.arguments().toArray(new ValueNode[0]), oldCallTarget.returnType()));
        invoke.asNode().replaceFirstInput(oldCallTarget, newCallTarget);
    }

    /**
     * Determines if inlining is possible at the given invoke node.
     *
     * @param invoke the invoke that should be inlined
     * @return an instance of InlineInfo, or null if no inlining is possible at the given invoke
     */
    public static InlineInfo getInlineInfo(InliningData data, Invoke invoke, int maxNumberOfMethods, Replacements replacements, Assumptions assumptions, OptimisticOptimizations optimisticOpts) {
        if (!checkInvokeConditions(invoke)) {
            return null;
        }
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();

        if (callTarget.invokeKind() == InvokeKind.Special || targetMethod.canBeStaticallyBound()) {
            return getExactInlineInfo(data, invoke, replacements, optimisticOpts, targetMethod);
        }

        assert callTarget.invokeKind() == InvokeKind.Virtual || callTarget.invokeKind() == InvokeKind.Interface;

        ResolvedJavaType holder = targetMethod.getDeclaringClass();
        if (!(callTarget.receiver().stamp() instanceof ObjectStamp)) {
            return null;
        }
        ObjectStamp receiverStamp = (ObjectStamp) callTarget.receiver().stamp();
        if (receiverStamp.alwaysNull()) {
            // Don't inline if receiver is known to be null
            return null;
        }
        if (receiverStamp.type() != null) {
            // the invoke target might be more specific than the holder (happens after inlining:
            // parameters lose their declared type...)
            ResolvedJavaType receiverType = receiverStamp.type();
            if (receiverType != null && holder.isAssignableFrom(receiverType)) {
                holder = receiverType;
                if (receiverStamp.isExactType()) {
                    assert targetMethod.getDeclaringClass().isAssignableFrom(holder) : holder + " subtype of " + targetMethod.getDeclaringClass() + " for " + targetMethod;
                    ResolvedJavaMethod resolvedMethod = holder.resolveMethod(targetMethod);
                    if (resolvedMethod != null) {
                        return getExactInlineInfo(data, invoke, replacements, optimisticOpts, resolvedMethod);
                    }
                }
            }
        }

        if (holder.isArray()) {
            // arrays can be treated as Objects
            ResolvedJavaMethod resolvedMethod = holder.resolveMethod(targetMethod);
            if (resolvedMethod != null) {
                return getExactInlineInfo(data, invoke, replacements, optimisticOpts, resolvedMethod);
            }
        }

        if (assumptions.useOptimisticAssumptions()) {
            ResolvedJavaType uniqueSubtype = holder.findUniqueConcreteSubtype();
            if (uniqueSubtype != null) {
                ResolvedJavaMethod resolvedMethod = uniqueSubtype.resolveMethod(targetMethod);
                if (resolvedMethod != null) {
                    return getAssumptionInlineInfo(data, invoke, replacements, optimisticOpts, resolvedMethod, new Assumptions.ConcreteSubtype(holder, uniqueSubtype));
                }
            }

            ResolvedJavaMethod concrete = holder.findUniqueConcreteMethod(targetMethod);
            if (concrete != null) {
                return getAssumptionInlineInfo(data, invoke, replacements, optimisticOpts, concrete, new Assumptions.ConcreteMethod(targetMethod, holder, concrete));
            }
        }

        // type check based inlining
        return getTypeCheckedInlineInfo(data, invoke, maxNumberOfMethods, replacements, targetMethod, optimisticOpts);
    }

    private static InlineInfo getAssumptionInlineInfo(InliningData data, Invoke invoke, Replacements replacements, OptimisticOptimizations optimisticOpts, ResolvedJavaMethod concrete,
                    Assumption takenAssumption) {
        assert !concrete.isAbstract();
        if (!checkTargetConditions(data, replacements, invoke, concrete, optimisticOpts)) {
            return null;
        }
        return new AssumptionInlineInfo(invoke, concrete, takenAssumption);
    }

    private static InlineInfo getExactInlineInfo(InliningData data, Invoke invoke, Replacements replacements, OptimisticOptimizations optimisticOpts, ResolvedJavaMethod targetMethod) {
        assert !targetMethod.isAbstract();
        if (!checkTargetConditions(data, replacements, invoke, targetMethod, optimisticOpts)) {
            return null;
        }
        return new ExactInlineInfo(invoke, targetMethod);
    }

    private static InlineInfo getTypeCheckedInlineInfo(InliningData data, Invoke invoke, int maxNumberOfMethods, Replacements replacements, ResolvedJavaMethod targetMethod,
                    OptimisticOptimizations optimisticOpts) {
        JavaTypeProfile typeProfile;
        ValueNode receiver = invoke.callTarget().arguments().get(0);
        if (receiver instanceof TypeProfileProxyNode) {
            TypeProfileProxyNode typeProfileProxyNode = (TypeProfileProxyNode) receiver;
            typeProfile = typeProfileProxyNode.getProfile();
        } else {
            return logNotInlinedMethodAndReturnNull(invoke, data.inliningDepth(), targetMethod, "no type profile exists");
        }

        ProfiledType[] ptypes = typeProfile.getTypes();
        if (ptypes == null || ptypes.length <= 0) {
            return logNotInlinedMethodAndReturnNull(invoke, data.inliningDepth(), targetMethod, "no types in profile");
        }

        double notRecordedTypeProbability = typeProfile.getNotRecordedProbability();
        if (ptypes.length == 1 && notRecordedTypeProbability == 0) {
            if (!optimisticOpts.inlineMonomorphicCalls()) {
                return logNotInlinedMethodAndReturnNull(invoke, data.inliningDepth(), targetMethod, "inlining monomorphic calls is disabled");
            }

            ResolvedJavaType type = ptypes[0].getType();
            assert type.isArray() || !type.isAbstract();
            ResolvedJavaMethod concrete = type.resolveMethod(targetMethod);
            if (!checkTargetConditions(data, replacements, invoke, concrete, optimisticOpts)) {
                return null;
            }
            return new TypeGuardInlineInfo(invoke, concrete, type);
        } else {
            invoke.setPolymorphic(true);

            if (!optimisticOpts.inlinePolymorphicCalls() && notRecordedTypeProbability == 0) {
                return logNotInlinedMethodAndReturnNull(invoke, data.inliningDepth(), targetMethod, "inlining polymorphic calls is disabled (%d types)", ptypes.length);
            }
            if (!optimisticOpts.inlineMegamorphicCalls() && notRecordedTypeProbability > 0) {
                // due to filtering impossible types, notRecordedTypeProbability can be > 0 although
                // the number of types is lower than what can be recorded in a type profile
                return logNotInlinedMethodAndReturnNull(invoke, data.inliningDepth(), targetMethod, "inlining megamorphic calls is disabled (%d types, %f %% not recorded types)", ptypes.length,
                                notRecordedTypeProbability * 100);
            }

            // Find unique methods and their probabilities.
            ArrayList<ResolvedJavaMethod> concreteMethods = new ArrayList<>();
            ArrayList<Double> concreteMethodsProbabilities = new ArrayList<>();
            for (int i = 0; i < ptypes.length; i++) {
                ResolvedJavaMethod concrete = ptypes[i].getType().resolveMethod(targetMethod);
                if (concrete == null) {
                    return logNotInlinedMethodAndReturnNull(invoke, data.inliningDepth(), targetMethod, "could not resolve method");
                }
                int index = concreteMethods.indexOf(concrete);
                double curProbability = ptypes[i].getProbability();
                if (index < 0) {
                    index = concreteMethods.size();
                    concreteMethods.add(concrete);
                    concreteMethodsProbabilities.add(curProbability);
                } else {
                    concreteMethodsProbabilities.set(index, concreteMethodsProbabilities.get(index) + curProbability);
                }
            }

            if (concreteMethods.size() > maxNumberOfMethods) {
                return logNotInlinedMethodAndReturnNull(invoke, data.inliningDepth(), targetMethod, "polymorphic call with more than %d target methods", maxNumberOfMethods);
            }

            // Clear methods that fall below the threshold.
            if (notRecordedTypeProbability > 0) {
                ArrayList<ResolvedJavaMethod> newConcreteMethods = new ArrayList<>();
                ArrayList<Double> newConcreteMethodsProbabilities = new ArrayList<>();
                for (int i = 0; i < concreteMethods.size(); ++i) {
                    if (concreteMethodsProbabilities.get(i) >= MegamorphicInliningMinMethodProbability.getValue()) {
                        newConcreteMethods.add(concreteMethods.get(i));
                        newConcreteMethodsProbabilities.add(concreteMethodsProbabilities.get(i));
                    }
                }

                if (newConcreteMethods.size() == 0) {
                    // No method left that is worth inlining.
                    return logNotInlinedMethodAndReturnNull(invoke, data.inliningDepth(), targetMethod, "no methods remaining after filtering less frequent methods (%d methods previously)",
                                    concreteMethods.size());
                }

                concreteMethods = newConcreteMethods;
                concreteMethodsProbabilities = newConcreteMethodsProbabilities;
            }

            // Clean out types whose methods are no longer available.
            ArrayList<ProfiledType> usedTypes = new ArrayList<>();
            ArrayList<Integer> typesToConcretes = new ArrayList<>();
            for (ProfiledType type : ptypes) {
                ResolvedJavaMethod concrete = type.getType().resolveMethod(targetMethod);
                int index = concreteMethods.indexOf(concrete);
                if (index == -1) {
                    notRecordedTypeProbability += type.getProbability();
                } else {
                    assert type.getType().isArray() || !type.getType().isAbstract() : type + " " + concrete;
                    usedTypes.add(type);
                    typesToConcretes.add(index);
                }
            }

            if (usedTypes.size() == 0) {
                // No type left that is worth checking for.
                return logNotInlinedMethodAndReturnNull(invoke, data.inliningDepth(), targetMethod, "no types remaining after filtering less frequent types (%d types previously)", ptypes.length);
            }

            for (ResolvedJavaMethod concrete : concreteMethods) {
                if (!checkTargetConditions(data, replacements, invoke, concrete, optimisticOpts)) {
                    return logNotInlinedMethodAndReturnNull(invoke, data.inliningDepth(), targetMethod, "it is a polymorphic method call and at least one invoked method cannot be inlined");
                }
            }
            return new MultiTypeGuardInlineInfo(invoke, concreteMethods, concreteMethodsProbabilities, usedTypes, typesToConcretes, notRecordedTypeProbability);
        }
    }

    public static GuardedValueNode createAnchoredReceiver(StructuredGraph graph, GuardingNode anchor, ResolvedJavaType commonType, ValueNode receiver, boolean exact) {
        return createAnchoredReceiver(graph, anchor, receiver, exact ? StampFactory.exactNonNull(commonType) : StampFactory.declaredNonNull(commonType));
    }

    private static GuardedValueNode createAnchoredReceiver(StructuredGraph graph, GuardingNode anchor, ValueNode receiver, Stamp stamp) {
        // to avoid that floating reads on receiver fields float above the type check
        return graph.unique(new GuardedValueNode(receiver, anchor, stamp));
    }

    // TODO (chaeubl): cleanup this method
    private static boolean checkInvokeConditions(Invoke invoke) {
        if (invoke.predecessor() == null || !invoke.asNode().isAlive()) {
            return logNotInlinedMethod(invoke, "the invoke is dead code");
        } else if (!(invoke.callTarget() instanceof MethodCallTargetNode)) {
            return logNotInlinedMethod(invoke, "the invoke has already been lowered, or has been created as a low-level node");
        } else if (((MethodCallTargetNode) invoke.callTarget()).targetMethod() == null) {
            return logNotInlinedMethod(invoke, "target method is null");
        } else if (invoke.stateAfter() == null) {
            // TODO (chaeubl): why should an invoke not have a state after?
            return logNotInlinedMethod(invoke, "the invoke has no after state");
        } else if (!invoke.useForInlining()) {
            return logNotInlinedMethod(invoke, "the invoke is marked to be not used for inlining");
        } else if (((MethodCallTargetNode) invoke.callTarget()).receiver() != null && ((MethodCallTargetNode) invoke.callTarget()).receiver().isConstant() &&
                        ((MethodCallTargetNode) invoke.callTarget()).receiver().asConstant().isNull()) {
            return logNotInlinedMethod(invoke, "receiver is null");
        } else {
            return true;
        }
    }

    private static boolean checkTargetConditions(InliningData data, Replacements replacements, Invoke invoke, ResolvedJavaMethod method, OptimisticOptimizations optimisticOpts) {
        if (method == null) {
            return logNotInlinedMethodAndReturnFalse(invoke, data.inliningDepth(), method, "the method is not resolved");
        } else if (method.isNative() && (!Intrinsify.getValue() || !InliningUtil.canIntrinsify(replacements, method))) {
            return logNotInlinedMethodAndReturnFalse(invoke, data.inliningDepth(), method, "it is a non-intrinsic native method");
        } else if (method.isAbstract()) {
            return logNotInlinedMethodAndReturnFalse(invoke, data.inliningDepth(), method, "it is an abstract method");
        } else if (!method.getDeclaringClass().isInitialized()) {
            return logNotInlinedMethodAndReturnFalse(invoke, data.inliningDepth(), method, "the method's class is not initialized");
        } else if (!method.canBeInlined()) {
            return logNotInlinedMethodAndReturnFalse(invoke, data.inliningDepth(), method, "it is marked non-inlinable");
        } else if (data.countRecursiveInlining(method) > MaximumRecursiveInlining.getValue()) {
            return logNotInlinedMethodAndReturnFalse(invoke, data.inliningDepth(), method, "it exceeds the maximum recursive inlining depth");
        } else if (new OptimisticOptimizations(method.getProfilingInfo()).lessOptimisticThan(optimisticOpts)) {
            return logNotInlinedMethodAndReturnFalse(invoke, data.inliningDepth(), method, "the callee uses less optimistic optimizations than caller");
        } else {
            return true;
        }
    }

    /**
     * Performs an actual inlining, thereby replacing the given invoke with the given inlineGraph.
     *
     * @param invoke the invoke that will be replaced
     * @param inlineGraph the graph that the invoke will be replaced with
     * @param receiverNullCheck true if a null check needs to be generated for non-static inlinings,
     *            false if no such check is required
     */
    public static Map<Node, Node> inline(Invoke invoke, StructuredGraph inlineGraph, boolean receiverNullCheck) {
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
                unwindDuplicate.clearInputs();
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

        if (stateAfter != null) {
            processFrameStates(invoke, inlineGraph, duplicates, stateAtExceptionEdge);
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
                returnNode.clearInputs();
                returnNode.replaceAndDelete(n);
            } else {
                ArrayList<ReturnNode> returnDuplicates = new ArrayList<>(returnNodes.size());
                for (ReturnNode returnNode : returnNodes) {
                    returnDuplicates.add((ReturnNode) duplicates.get(returnNode));
                }
                MergeNode merge = graph.add(new MergeNode());
                merge.setStateAfter(stateAfter);
                ValueNode returnValue = mergeReturns(merge, returnDuplicates);
                invokeNode.replaceAtUsages(returnValue);
                merge.setNext(n);
            }
        }

        invokeNode.replaceAtUsages(null);
        GraphUtil.killCFG(invokeNode);

        return duplicates;
    }

    protected static void processFrameStates(Invoke invoke, StructuredGraph inlineGraph, Map<Node, Node> duplicates, FrameState stateAtExceptionEdge) {
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
                    if (invokeReturnKind != Kind.Void && frameState.stackSize() > 0 && stateAfterReturn.stackAt(0) != frameState.stackAt(0)) {
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

    public static ValueNode mergeReturns(MergeNode merge, List<? extends ReturnNode> returnNodes) {
        PhiNode returnValuePhi = null;

        for (ReturnNode returnNode : returnNodes) {
            // create and wire up a new EndNode
            EndNode endNode = merge.graph().add(new EndNode());
            merge.addForwardEnd(endNode);

            if (returnNode.result() != null) {
                if (returnValuePhi == null) {
                    returnValuePhi = merge.graph().addWithoutUnique(new ValuePhiNode(returnNode.result().stamp().unrestricted(), merge));
                }
                returnValuePhi.addInput(returnNode.result());
            }
            returnNode.clearInputs();
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
