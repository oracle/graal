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
package com.oracle.graal.phases.common;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Assumptions.Assumption;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.JavaTypeProfile.ProfiledType;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

public class InliningUtil {

    private static final DebugMetric metricInliningTailDuplication = Debug.metric("InliningTailDuplication");
    private static final String inliningDecisionsScopeString = "InliningDecisions";

    /**
     * Meters the size (in bytecodes) of all methods processed during compilation (i.e., top level
     * and all inlined methods), irrespective of how many bytecodes in each method are actually
     * parsed (which may be none for methods whose IR is retrieved from a cache).
     */
    public static final DebugMetric InlinedBytecodes = Debug.metric("InlinedBytecodes");

    public interface InliningCallback {

        StructuredGraph buildGraph(final ResolvedJavaMethod method);
    }

    public interface InliningPolicy {

        void initialize(StructuredGraph graph);

        boolean continueInlining(StructuredGraph graph);

        InlineInfo next();

        void scanInvokes(Iterable<? extends Node> newNodes);

        boolean isWorthInlining(InlineInfo info, NodesToDoubles nodeProbabilities, NodesToDoubles nodeRelevance);
    }

    /**
     * Print a HotSpot-style inlining message to the console.
     */
    private static void printInlining(final InlineInfo info, final boolean success, final String msg, final Object... args) {
        printInlining(info.methodAt(0), info.invoke(), success, msg, args);
    }

    /**
     * Print a HotSpot-style inlining message to the console.
     */
    private static void printInlining(final ResolvedJavaMethod method, final Invoke invoke, final boolean success, final String msg, final Object... args) {
        if (GraalOptions.HotSpotPrintInlining) {
            final int mod = method.getModifiers();
            // 1234567
            TTY.print("        ");     // print timestamp
            // 1234
            TTY.print("     ");        // print compilation number
            // % s ! b n
            TTY.print("%c%c%c%c%c ", ' ', Modifier.isSynchronized(mod) ? 's' : ' ', ' ', ' ', Modifier.isNative(mod) ? 'n' : ' ');
            TTY.print("     ");        // more indent
            TTY.print("    ");         // initial inlining indent
            final int level = computeInliningLevel(invoke);
            for (int i = 0; i < level; i++) {
                TTY.print("  ");
            }
            TTY.println(String.format("@ %d  %s   %s%s", invoke.bci(), methodName(method, null), success ? "" : "not inlining ", String.format(msg, args)));
        }
    }

    public static boolean logInlinedMethod(InlineInfo info, String msg, Object... args) {
        logInliningDecision(info, true, msg, args);
        return true;
    }

    public static boolean logNotInlinedMethod(InlineInfo info, String msg, Object... args) {
        logInliningDecision(info, false, msg, args);
        return false;
    }

    public static void logInliningDecision(InlineInfo info, boolean success, String msg, final Object... args) {
        printInlining(info, success, msg, args);
        if (shouldLogInliningDecision()) {
            logInliningDecision(methodName(info), success, msg, args);
        }
    }

    public static void logInliningDecision(final String msg, final Object... args) {
        Debug.scope(inliningDecisionsScopeString, new Runnable() {

            public void run() {
                Debug.log(msg, args);
            }
        });
    }

    private static boolean logNotInlinedMethodAndReturnFalse(Invoke invoke, String msg) {
        if (shouldLogInliningDecision()) {
            String methodString = invoke.toString() + (invoke.callTarget() == null ? " callTarget=null" : invoke.callTarget().targetName());
            logInliningDecision(methodString, false, msg, new Object[0]);
        }
        return false;
    }

    private static InlineInfo logNotInlinedMethodAndReturnNull(Invoke invoke, ResolvedJavaMethod method, String msg) {
        return logNotInlinedMethodAndReturnNull(invoke, method, msg, new Object[0]);
    }

    private static InlineInfo logNotInlinedMethodAndReturnNull(Invoke invoke, ResolvedJavaMethod method, String msg, Object... args) {
        printInlining(method, invoke, false, msg, args);
        if (shouldLogInliningDecision()) {
            String methodString = methodName(method, invoke);
            logInliningDecision(methodString, false, msg, args);
        }
        return null;
    }

    private static boolean logNotInlinedMethodAndReturnFalse(Invoke invoke, ResolvedJavaMethod method, String msg) {
        printInlining(method, invoke, false, msg, new Object[0]);
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
        return Debug.scope(inliningDecisionsScopeString, new Callable<Boolean>() {

            public Boolean call() {
                return Debug.isLogEnabled();
            }
        });
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

    /**
     * Represents an opportunity for inlining at the given invoke, with the given weight and level.
     * The weight is the amortized weight of the additional code - so smaller is better. The level
     * is the number of nested inlinings that lead to this invoke.
     */
    public interface InlineInfo {

        Invoke invoke();

        int level();

        int numberOfMethods();

        ResolvedJavaMethod methodAt(int index);

        /**
         * Performs the inlining described by this object and returns the node that represents the
         * return value of the inlined method (or null for void methods and methods that have no
         * non-exceptional exit).
         **/
        void inline(StructuredGraph graph, MetaAccessProvider runtime, Replacements replacements, InliningCallback callback, Assumptions assumptions);

        /**
         * Try to make the call static bindable to avoid interface and virtual method calls.
         */
        void tryToDevirtualizeInvoke(StructuredGraph graph, MetaAccessProvider runtime, Assumptions assumptions);
    }

    public abstract static class AbstractInlineInfo implements InlineInfo {

        protected final Invoke invoke;

        public AbstractInlineInfo(Invoke invoke) {
            this.invoke = invoke;
        }

        @Override
        public Invoke invoke() {
            return invoke;
        }

        @Override
        public int level() {
            return computeInliningLevel(invoke);
        }

        protected static void inline(Invoke invoke, ResolvedJavaMethod concrete, InliningCallback callback, Replacements replacements, Assumptions assumptions, boolean receiverNullCheck) {
            Class<? extends FixedWithNextNode> macroNodeClass = getMacroNodeClass(replacements, concrete);
            StructuredGraph graph = (StructuredGraph) invoke.asNode().graph();
            if (macroNodeClass != null) {
                if (((MethodCallTargetNode) invoke.callTarget()).targetMethod() != concrete) {
                    assert ((MethodCallTargetNode) invoke.callTarget()).invokeKind() != InvokeKind.Static;
                    InliningUtil.replaceInvokeCallTarget(invoke, graph, InvokeKind.Special, concrete);
                }
                FixedWithNextNode macroNode;
                try {
                    macroNode = macroNodeClass.getConstructor(Invoke.class).newInstance(invoke);
                } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
                    throw new GraalInternalError(e).addContext(invoke.asNode()).addContext("macroSubstitution", macroNodeClass);
                }
                CallTargetNode callTarget = invoke.callTarget();
                if (invoke instanceof InvokeNode) {
                    graph.replaceFixedWithFixed((InvokeNode) invoke, graph.add(macroNode));
                } else {
                    InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
                    invokeWithException.killExceptionEdge();
                    graph.replaceSplitWithFixed(invokeWithException, graph.add(macroNode), invokeWithException.next());
                }
                GraphUtil.killWithUnusedFloatingInputs(callTarget);
            } else {
                StructuredGraph calleeGraph = getIntrinsicGraph(replacements, concrete);
                if (calleeGraph == null) {
                    calleeGraph = getGraph(concrete, callback);
                }
                InlinedBytecodes.add(concrete.getCodeSize());
                assumptions.recordMethodContents(concrete);
                InliningUtil.inline(invoke, calleeGraph, receiverNullCheck);

                graph.getLeafGraphIds().add(calleeGraph.graphId());
                // we might at some point cache already-inlined graphs, so add recursively:
                graph.getLeafGraphIds().addAll(calleeGraph.getLeafGraphIds());
            }
        }

        protected static StructuredGraph getGraph(final ResolvedJavaMethod concrete, final InliningCallback callback) {
            return Debug.scope("GetInliningGraph", concrete, new Callable<StructuredGraph>() {

                @Override
                public StructuredGraph call() throws Exception {
                    assert !Modifier.isNative(concrete.getModifiers());
                    return callback.buildGraph(concrete);
                }
            });
        }
    }

    public static void replaceInvokeCallTarget(Invoke invoke, StructuredGraph graph, InvokeKind invokeKind, ResolvedJavaMethod targetMethod) {
        MethodCallTargetNode oldCallTarget = (MethodCallTargetNode) invoke.callTarget();
        MethodCallTargetNode newCallTarget = graph.add(new MethodCallTargetNode(invokeKind, targetMethod, oldCallTarget.arguments().toArray(new ValueNode[0]), oldCallTarget.returnType()));
        invoke.asNode().replaceFirstInput(oldCallTarget, newCallTarget);
    }

    /**
     * Represents an inlining opportunity where the compiler can statically determine a monomorphic
     * target method and therefore is able to determine the called method exactly.
     */
    private static class ExactInlineInfo extends AbstractInlineInfo {

        public final ResolvedJavaMethod concrete;

        public ExactInlineInfo(Invoke invoke, ResolvedJavaMethod concrete) {
            super(invoke);
            this.concrete = concrete;
        }

        @Override
        public void inline(StructuredGraph compilerGraph, MetaAccessProvider runtime, Replacements replacements, InliningCallback callback, Assumptions assumptions) {
            inline(invoke, concrete, callback, replacements, assumptions, true);
        }

        @Override
        public void tryToDevirtualizeInvoke(StructuredGraph graph, MetaAccessProvider runtime, Assumptions assumptions) {
            // nothing todo, can already be bound statically
        }

        @Override
        public int numberOfMethods() {
            return 1;
        }

        @Override
        public ResolvedJavaMethod methodAt(int index) {
            assert index == 0;
            return concrete;
        }

        @Override
        public String toString() {
            return "exact " + MetaUtil.format("%H.%n(%p):%r", concrete);
        }
    }

    /**
     * Represents an inlining opportunity for which profiling information suggests a monomorphic
     * receiver, but for which the receiver type cannot be proven. A type check guard will be
     * generated if this inlining is performed.
     */
    private static class TypeGuardInlineInfo extends AbstractInlineInfo {

        public final ResolvedJavaMethod concrete;
        public final ResolvedJavaType type;

        public TypeGuardInlineInfo(Invoke invoke, ResolvedJavaMethod concrete, ResolvedJavaType type) {
            super(invoke);
            this.concrete = concrete;
            this.type = type;
        }

        @Override
        public int numberOfMethods() {
            return 1;
        }

        @Override
        public ResolvedJavaMethod methodAt(int index) {
            assert index == 0;
            return concrete;
        }

        @Override
        public void inline(StructuredGraph graph, MetaAccessProvider runtime, Replacements replacements, InliningCallback callback, Assumptions assumptions) {
            createGuard(graph, runtime);
            inline(invoke, concrete, callback, replacements, assumptions, false);
        }

        @Override
        public void tryToDevirtualizeInvoke(StructuredGraph graph, MetaAccessProvider runtime, Assumptions assumptions) {
            createGuard(graph, runtime);
            replaceInvokeCallTarget(invoke, graph, InvokeKind.Special, concrete);
        }

        private void createGuard(StructuredGraph graph, MetaAccessProvider runtime) {
            InliningUtil.receiverNullCheck(invoke);
            ValueNode receiver = ((MethodCallTargetNode) invoke.callTarget()).receiver();
            ConstantNode typeHub = ConstantNode.forConstant(type.getEncoding(Representation.ObjectHub), runtime, graph);
            LoadHubNode receiverHub = graph.add(new LoadHubNode(receiver, typeHub.kind()));
            CompareNode typeCheck = CompareNode.createCompareNode(Condition.EQ, receiverHub, typeHub);
            FixedGuardNode guard = graph.add(new FixedGuardNode(typeCheck, DeoptimizationReason.TypeCheckedInliningViolated, DeoptimizationAction.InvalidateReprofile));
            ValueAnchorNode anchor = graph.add(new ValueAnchorNode());
            assert invoke.predecessor() != null;

            ValueNode anchoredReceiver = createAnchoredReceiver(graph, anchor, type, receiver, true);
            invoke.callTarget().replaceFirstInput(receiver, anchoredReceiver);

            graph.addBeforeFixed(invoke.asNode(), receiverHub);
            graph.addBeforeFixed(invoke.asNode(), guard);
            graph.addBeforeFixed(invoke.asNode(), anchor);
        }

        @Override
        public String toString() {
            return "type-checked with type " + type.getName() + " and method " + MetaUtil.format("%H.%n(%p):%r", concrete);
        }
    }

    /**
     * Polymorphic inlining of m methods with n type checks (n >= m) in case that the profiling
     * information suggests a reasonable amounts of different receiver types and different methods.
     * If an unknown type is encountered a deoptimization is triggered.
     */
    private static class MultiTypeGuardInlineInfo extends AbstractInlineInfo {

        public final List<ResolvedJavaMethod> concretes;
        public final ArrayList<ProfiledType> ptypes;
        public final int[] typesToConcretes;
        public final double notRecordedTypeProbability;

        public MultiTypeGuardInlineInfo(Invoke invoke, ArrayList<ResolvedJavaMethod> concretes, ArrayList<ProfiledType> ptypes, int[] typesToConcretes, double notRecordedTypeProbability) {
            super(invoke);
            assert concretes.size() > 0 && concretes.size() <= ptypes.size() : "must have at least one method but no more than types methods";
            assert ptypes.size() == typesToConcretes.length : "array lengths must match";

            this.concretes = concretes;
            this.ptypes = ptypes;
            this.typesToConcretes = typesToConcretes;
            this.notRecordedTypeProbability = notRecordedTypeProbability;
        }

        @Override
        public int numberOfMethods() {
            return concretes.size();
        }

        @Override
        public ResolvedJavaMethod methodAt(int index) {
            assert index >= 0 && index < concretes.size();
            return concretes.get(index);
        }

        @Override
        public void inline(StructuredGraph graph, MetaAccessProvider runtime, Replacements replacements, InliningCallback callback, Assumptions assumptions) {
            // receiver null check must be the first node
            InliningUtil.receiverNullCheck(invoke);
            if (hasSingleMethod()) {
                inlineSingleMethod(graph, callback, replacements, assumptions);
            } else {
                inlineMultipleMethods(graph, callback, replacements, assumptions, runtime);
            }
        }

        private boolean hasSingleMethod() {
            return concretes.size() == 1 && !shouldFallbackToInvoke();
        }

        private boolean shouldFallbackToInvoke() {
            return notRecordedTypeProbability > 0;
        }

        private void inlineMultipleMethods(StructuredGraph graph, InliningCallback callback, Replacements replacements, Assumptions assumptions, MetaAccessProvider runtime) {
            int numberOfMethods = concretes.size();
            FixedNode continuation = invoke.next();

            ValueNode originalReceiver = ((MethodCallTargetNode) invoke.callTarget()).receiver();
            // setup merge and phi nodes for results and exceptions
            MergeNode returnMerge = graph.add(new MergeNode());
            returnMerge.setStateAfter(invoke.stateAfter().duplicate(invoke.stateAfter().bci));

            PhiNode returnValuePhi = null;
            if (invoke.asNode().kind() != Kind.Void) {
                returnValuePhi = graph.unique(new PhiNode(invoke.asNode().kind(), returnMerge));
            }

            MergeNode exceptionMerge = null;
            PhiNode exceptionObjectPhi = null;
            if (invoke instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
                ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) invokeWithException.exceptionEdge();

                exceptionMerge = graph.add(new MergeNode());

                FixedNode exceptionSux = exceptionEdge.next();
                graph.addBeforeFixed(exceptionSux, exceptionMerge);
                exceptionObjectPhi = graph.unique(new PhiNode(Kind.Object, exceptionMerge));
                exceptionMerge.setStateAfter(exceptionEdge.stateAfter().duplicateModified(invoke.stateAfter().bci, true, Kind.Object, exceptionObjectPhi));
            }

            // create one separate block for each invoked method
            BeginNode[] successors = new BeginNode[numberOfMethods + 1];
            for (int i = 0; i < numberOfMethods; i++) {
                successors[i] = createInvocationBlock(graph, invoke, returnMerge, returnValuePhi, exceptionMerge, exceptionObjectPhi, true);
            }

            // create the successor for an unknown type
            FixedNode unknownTypeSux;
            if (shouldFallbackToInvoke()) {
                unknownTypeSux = createInvocationBlock(graph, invoke, returnMerge, returnValuePhi, exceptionMerge, exceptionObjectPhi, false);
            } else {
                unknownTypeSux = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TypeCheckedInliningViolated));
            }
            successors[successors.length - 1] = BeginNode.begin(unknownTypeSux);

            // replace the invoke exception edge
            if (invoke instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) invoke;
                ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) invokeWithExceptionNode.exceptionEdge();
                exceptionEdge.replaceAtUsages(exceptionObjectPhi);
                exceptionEdge.setNext(null);
                GraphUtil.killCFG(invokeWithExceptionNode.exceptionEdge());
            }

            assert invoke.asNode().isAlive();

            // replace the invoke with a switch on the type of the actual receiver
            createDispatchOnTypeBeforeInvoke(graph, successors, false);

            assert invoke.next() == continuation;
            invoke.setNext(null);
            returnMerge.setNext(continuation);
            invoke.asNode().replaceAtUsages(returnValuePhi);
            invoke.asNode().replaceAndDelete(null);

            ArrayList<PiNode> replacementNodes = new ArrayList<>();

            // do the actual inlining for every invoke
            for (int i = 0; i < numberOfMethods; i++) {
                BeginNode node = successors[i];
                Invoke invokeForInlining = (Invoke) node.next();

                ResolvedJavaType commonType = getLeastCommonType(i);
                ValueNode receiver = ((MethodCallTargetNode) invokeForInlining.callTarget()).receiver();
                boolean exact = getTypeCount(i) == 1;
                PiNode anchoredReceiver = createAnchoredReceiver(graph, node, commonType, receiver, exact);
                invokeForInlining.callTarget().replaceFirstInput(receiver, anchoredReceiver);

                inline(invokeForInlining, concretes.get(i), callback, replacements, assumptions, false);

                replacementNodes.add(anchoredReceiver);
            }
            if (shouldFallbackToInvoke()) {
                replacementNodes.add(null);
            }

            if (GraalOptions.OptTailDuplication) {
                /*
                 * We might want to perform tail duplication at the merge after a type switch, if
                 * there are invokes that would benefit from the improvement in type information.
                 */
                FixedNode current = returnMerge;
                int opportunities = 0;
                do {
                    if (current instanceof InvokeNode && ((InvokeNode) current).callTarget() instanceof MethodCallTargetNode &&
                                    ((MethodCallTargetNode) ((InvokeNode) current).callTarget()).receiver() == originalReceiver) {
                        opportunities++;
                    } else if (current.inputs().contains(originalReceiver)) {
                        opportunities++;
                    }
                    current = ((FixedWithNextNode) current).next();
                } while (current instanceof FixedWithNextNode);
                if (opportunities > 0) {
                    metricInliningTailDuplication.increment();
                    Debug.log("MultiTypeGuardInlineInfo starting tail duplication (%d opportunities)", opportunities);
                    TailDuplicationPhase.tailDuplicate(returnMerge, TailDuplicationPhase.TRUE_DECISION, replacementNodes, new HighTierContext(runtime, assumptions, replacements));
                }
            }
        }

        private int getTypeCount(int concreteMethodIndex) {
            int count = 0;
            for (int i = 0; i < typesToConcretes.length; i++) {
                if (typesToConcretes[i] == concreteMethodIndex) {
                    count++;
                }
            }
            return count;
        }

        private ResolvedJavaType getLeastCommonType(int concreteMethodIndex) {
            ResolvedJavaType commonType = null;
            for (int i = 0; i < typesToConcretes.length; i++) {
                if (typesToConcretes[i] == concreteMethodIndex) {
                    if (commonType == null) {
                        commonType = ptypes.get(i).getType();
                    } else {
                        commonType = commonType.findLeastCommonAncestor(ptypes.get(i).getType());
                    }
                }
            }
            assert commonType != null;
            return commonType;
        }

        private ResolvedJavaType getLeastCommonType() {
            ResolvedJavaType result = getLeastCommonType(0);
            for (int i = 1; i < concretes.size(); i++) {
                result = result.findLeastCommonAncestor(getLeastCommonType(i));
            }
            return result;
        }

        private void inlineSingleMethod(StructuredGraph graph, InliningCallback callback, Replacements replacements, Assumptions assumptions) {
            assert concretes.size() == 1 && ptypes.size() > 1 && !shouldFallbackToInvoke() && notRecordedTypeProbability == 0;

            BeginNode calleeEntryNode = graph.add(new BeginNode());

            BeginNode unknownTypeSux = createUnknownTypeSuccessor(graph);
            BeginNode[] successors = new BeginNode[]{calleeEntryNode, unknownTypeSux};
            createDispatchOnTypeBeforeInvoke(graph, successors, false);

            calleeEntryNode.setNext(invoke.asNode());

            ResolvedJavaMethod concrete = concretes.get(0);
            inline(invoke, concrete, callback, replacements, assumptions, false);
        }

        private void createDispatchOnTypeBeforeInvoke(StructuredGraph graph, BeginNode[] successors, boolean invokeIsOnlySuccessor) {
            assert ptypes.size() >= 1;

            Kind hubKind = ((MethodCallTargetNode) invoke.callTarget()).targetMethod().getDeclaringClass().getEncoding(Representation.ObjectHub).getKind();
            LoadHubNode hub = graph.add(new LoadHubNode(((MethodCallTargetNode) invoke.callTarget()).receiver(), hubKind));
            graph.addBeforeFixed(invoke.asNode(), hub);

            ResolvedJavaType[] keys = new ResolvedJavaType[ptypes.size()];
            double[] keyProbabilities = new double[ptypes.size() + 1];
            int[] keySuccessors = new int[ptypes.size() + 1];
            for (int i = 0; i < ptypes.size(); i++) {
                keys[i] = ptypes.get(i).getType();
                keyProbabilities[i] = ptypes.get(i).getProbability();
                keySuccessors[i] = invokeIsOnlySuccessor ? 0 : typesToConcretes[i];
                assert keySuccessors[i] < successors.length - 1 : "last successor is the unknownTypeSux";
            }
            keyProbabilities[keyProbabilities.length - 1] = notRecordedTypeProbability;
            keySuccessors[keySuccessors.length - 1] = successors.length - 1;

            TypeSwitchNode typeSwitch = graph.add(new TypeSwitchNode(hub, successors, keys, keyProbabilities, keySuccessors));
            FixedWithNextNode pred = (FixedWithNextNode) invoke.asNode().predecessor();
            pred.setNext(typeSwitch);
        }

        private static BeginNode createInvocationBlock(StructuredGraph graph, Invoke invoke, MergeNode returnMerge, PhiNode returnValuePhi, MergeNode exceptionMerge, PhiNode exceptionObjectPhi,
                        boolean useForInlining) {
            Invoke duplicatedInvoke = duplicateInvokeForInlining(graph, invoke, exceptionMerge, exceptionObjectPhi, useForInlining);
            BeginNode calleeEntryNode = graph.add(new BeginNode());
            calleeEntryNode.setNext(duplicatedInvoke.asNode());

            EndNode endNode = graph.add(new EndNode());
            duplicatedInvoke.setNext(endNode);
            returnMerge.addForwardEnd(endNode);

            if (returnValuePhi != null) {
                returnValuePhi.addInput(duplicatedInvoke.asNode());
            }
            return calleeEntryNode;
        }

        private static Invoke duplicateInvokeForInlining(StructuredGraph graph, Invoke invoke, MergeNode exceptionMerge, PhiNode exceptionObjectPhi, boolean useForInlining) {
            Invoke result = (Invoke) invoke.asNode().copyWithInputs();
            Node callTarget = result.callTarget().copyWithInputs();
            result.asNode().replaceFirstInput(result.callTarget(), callTarget);
            result.setUseForInlining(useForInlining);

            Kind kind = invoke.asNode().kind();
            if (kind != Kind.Void) {
                FrameState stateAfter = invoke.stateAfter();
                stateAfter = stateAfter.duplicate(stateAfter.bci);
                stateAfter.replaceFirstInput(invoke.asNode(), result.asNode());
                result.setStateAfter(stateAfter);
            }

            if (invoke instanceof InvokeWithExceptionNode) {
                assert exceptionMerge != null && exceptionObjectPhi != null;

                InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
                ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) invokeWithException.exceptionEdge();
                FrameState stateAfterException = exceptionEdge.stateAfter();

                ExceptionObjectNode newExceptionEdge = (ExceptionObjectNode) exceptionEdge.copyWithInputs();
                // set new state (pop old exception object, push new one)
                newExceptionEdge.setStateAfter(stateAfterException.duplicateModified(stateAfterException.bci, stateAfterException.rethrowException(), Kind.Object, newExceptionEdge));

                EndNode endNode = graph.add(new EndNode());
                newExceptionEdge.setNext(endNode);
                exceptionMerge.addForwardEnd(endNode);
                exceptionObjectPhi.addInput(newExceptionEdge);

                ((InvokeWithExceptionNode) result).setExceptionEdge(newExceptionEdge);
            }
            return result;
        }

        @Override
        public void tryToDevirtualizeInvoke(StructuredGraph graph, MetaAccessProvider runtime, Assumptions assumptions) {
            if (hasSingleMethod()) {
                tryToDevirtualizeSingleMethod(graph);
            } else {
                tryToDevirtualizeMultipleMethods(graph);
            }
        }

        private void tryToDevirtualizeSingleMethod(StructuredGraph graph) {
            devirtualizeWithTypeSwitch(graph, InvokeKind.Special, concretes.get(0));
        }

        private void tryToDevirtualizeMultipleMethods(StructuredGraph graph) {
            MethodCallTargetNode methodCallTarget = (MethodCallTargetNode) invoke.callTarget();
            if (methodCallTarget.invokeKind() == InvokeKind.Interface) {
                ResolvedJavaMethod targetMethod = methodCallTarget.targetMethod();
                ResolvedJavaType leastCommonType = getLeastCommonType();
                // check if we have a common base type that implements the interface -> in that case
                // we have a vtable entry for the interface method and can use a less expensive
                // virtual call
                if (!leastCommonType.isInterface() && targetMethod.getDeclaringClass().isAssignableFrom(leastCommonType)) {
                    ResolvedJavaMethod baseClassTargetMethod = leastCommonType.resolveMethod(targetMethod);
                    if (baseClassTargetMethod != null) {
                        devirtualizeWithTypeSwitch(graph, InvokeKind.Virtual, leastCommonType.resolveMethod(targetMethod));
                    }
                }
            }
        }

        private void devirtualizeWithTypeSwitch(StructuredGraph graph, InvokeKind kind, ResolvedJavaMethod target) {
            InliningUtil.receiverNullCheck(invoke);

            BeginNode invocationEntry = graph.add(new BeginNode());
            BeginNode unknownTypeSux = createUnknownTypeSuccessor(graph);
            BeginNode[] successors = new BeginNode[]{invocationEntry, unknownTypeSux};
            createDispatchOnTypeBeforeInvoke(graph, successors, true);

            invocationEntry.setNext(invoke.asNode());
            ValueNode receiver = ((MethodCallTargetNode) invoke.callTarget()).receiver();
            PiNode anchoredReceiver = createAnchoredReceiver(graph, invocationEntry, target.getDeclaringClass(), receiver, false);
            invoke.callTarget().replaceFirstInput(receiver, anchoredReceiver);
            replaceInvokeCallTarget(invoke, graph, kind, target);
        }

        private static BeginNode createUnknownTypeSuccessor(StructuredGraph graph) {
            return BeginNode.begin(graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TypeCheckedInliningViolated)));
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(shouldFallbackToInvoke() ? "megamorphic" : "polymorphic");
            builder.append(", ");
            builder.append(concretes.size());
            builder.append(" methods [ ");
            for (int i = 0; i < concretes.size(); i++) {
                builder.append(MetaUtil.format("  %H.%n(%p):%r", concretes.get(i)));
            }
            builder.append(" ], ");
            builder.append(ptypes.size());
            builder.append(" type checks [ ");
            for (int i = 0; i < ptypes.size(); i++) {
                builder.append("  ");
                builder.append(ptypes.get(i).getType().getName());
                builder.append(ptypes.get(i).getProbability());
            }
            builder.append(" ]");
            return builder.toString();
        }
    }

    /**
     * Represents an inlining opportunity where the current class hierarchy leads to a monomorphic
     * target method, but for which an assumption has to be registered because of non-final classes.
     */
    private static class AssumptionInlineInfo extends ExactInlineInfo {

        private final Assumption takenAssumption;

        public AssumptionInlineInfo(Invoke invoke, ResolvedJavaMethod concrete, Assumption takenAssumption) {
            super(invoke, concrete);
            this.takenAssumption = takenAssumption;
        }

        @Override
        public void inline(StructuredGraph graph, MetaAccessProvider runtime, Replacements replacements, InliningCallback callback, Assumptions assumptions) {
            assumptions.record(takenAssumption);
            super.inline(graph, runtime, replacements, callback, assumptions);
        }

        @Override
        public void tryToDevirtualizeInvoke(StructuredGraph graph, MetaAccessProvider runtime, Assumptions assumptions) {
            assumptions.record(takenAssumption);
            replaceInvokeCallTarget(invoke, graph, InvokeKind.Special, concrete);
        }

        @Override
        public String toString() {
            return "assumption " + MetaUtil.format("%H.%n(%p):%r", concrete);
        }
    }

    /**
     * Determines if inlining is possible at the given invoke node.
     * 
     * @param invoke the invoke that should be inlined
     * @return an instance of InlineInfo, or null if no inlining is possible at the given invoke
     */
    public static InlineInfo getInlineInfo(Invoke invoke, Replacements replacements, Assumptions assumptions, OptimisticOptimizations optimisticOpts) {
        if (!checkInvokeConditions(invoke)) {
            return null;
        }
        ResolvedJavaMethod caller = getCaller(invoke);
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();

        if (callTarget.invokeKind() == InvokeKind.Special || targetMethod.canBeStaticallyBound()) {
            return getExactInlineInfo(replacements, invoke, optimisticOpts, targetMethod);
        }

        assert callTarget.invokeKind() == InvokeKind.Virtual || callTarget.invokeKind() == InvokeKind.Interface;

        ResolvedJavaType holder = targetMethod.getDeclaringClass();
        ObjectStamp receiverStamp = callTarget.receiver().objectStamp();
        if (receiverStamp.type() != null) {
            // the invoke target might be more specific than the holder (happens after inlining:
            // locals lose their declared type...)
            ResolvedJavaType receiverType = receiverStamp.type();
            if (receiverType != null && holder.isAssignableFrom(receiverType)) {
                holder = receiverType;
                if (receiverStamp.isExactType()) {
                    assert targetMethod.getDeclaringClass().isAssignableFrom(holder) : holder + " subtype of " + targetMethod.getDeclaringClass() + " for " + targetMethod;
                    return getExactInlineInfo(replacements, invoke, optimisticOpts, holder.resolveMethod(targetMethod));
                }
            }
        }

        if (holder.isArray()) {
            // arrays can be treated as Objects
            return getExactInlineInfo(replacements, invoke, optimisticOpts, holder.resolveMethod(targetMethod));
        }

        if (assumptions.useOptimisticAssumptions()) {
            ResolvedJavaType uniqueSubtype = holder.findUniqueConcreteSubtype();
            if (uniqueSubtype != null) {
                return getAssumptionInlineInfo(replacements, invoke, optimisticOpts, uniqueSubtype.resolveMethod(targetMethod), new Assumptions.ConcreteSubtype(holder, uniqueSubtype));
            }

            ResolvedJavaMethod concrete = holder.findUniqueConcreteMethod(targetMethod);
            if (concrete != null) {
                return getAssumptionInlineInfo(replacements, invoke, optimisticOpts, concrete, new Assumptions.ConcreteMethod(targetMethod, holder, concrete));
            }
        }

        // type check based inlining
        return getTypeCheckedInlineInfo(replacements, invoke, caller, holder, targetMethod, optimisticOpts);
    }

    private static InlineInfo getAssumptionInlineInfo(Replacements replacements, Invoke invoke, OptimisticOptimizations optimisticOpts, ResolvedJavaMethod concrete, Assumption takenAssumption) {
        assert !Modifier.isAbstract(concrete.getModifiers());
        if (!checkTargetConditions(replacements, invoke, concrete, optimisticOpts)) {
            return null;
        }
        return new AssumptionInlineInfo(invoke, concrete, takenAssumption);
    }

    private static InlineInfo getExactInlineInfo(Replacements replacements, Invoke invoke, OptimisticOptimizations optimisticOpts, ResolvedJavaMethod targetMethod) {
        assert !Modifier.isAbstract(targetMethod.getModifiers());
        if (!checkTargetConditions(replacements, invoke, targetMethod, optimisticOpts)) {
            return null;
        }
        return new ExactInlineInfo(invoke, targetMethod);
    }

    private static InlineInfo getTypeCheckedInlineInfo(Replacements replacements, Invoke invoke, ResolvedJavaMethod caller, ResolvedJavaType holder, ResolvedJavaMethod targetMethod,
                    OptimisticOptimizations optimisticOpts) {
        ProfilingInfo profilingInfo = caller.getProfilingInfo();
        JavaTypeProfile typeProfile = profilingInfo.getTypeProfile(invoke.bci());
        if (typeProfile == null) {
            return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "no type profile exists");
        }

        ProfiledType[] rawProfiledTypes = typeProfile.getTypes();
        double[] newNotRecordedTypeProbability = new double[1];
        ArrayList<ProfiledType> ptypes = getCompatibleTypes(typeProfile, holder, newNotRecordedTypeProbability);
        if (ptypes == null || ptypes.size() <= 0) {
            return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "no types remained after filtering (%d types were recorded)", rawProfiledTypes.length);
        }

        double notRecordedTypeProbability = newNotRecordedTypeProbability[0];
        if (ptypes.size() == 1 && notRecordedTypeProbability == 0) {
            if (!optimisticOpts.inlineMonomorphicCalls()) {
                return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "inlining monomorphic calls is disabled");
            }

            ResolvedJavaType type = ptypes.get(0).getType();
            ResolvedJavaMethod concrete = type.resolveMethod(targetMethod);
            if (!checkTargetConditions(replacements, invoke, concrete, optimisticOpts)) {
                return null;
            }
            return new TypeGuardInlineInfo(invoke, concrete, type);
        } else {
            invoke.setPolymorphic(true);

            if (!optimisticOpts.inlinePolymorphicCalls() && notRecordedTypeProbability == 0) {
                return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "inlining polymorphic calls is disabled (%d types)", ptypes.size());
            }
            if (!optimisticOpts.inlineMegamorphicCalls() && notRecordedTypeProbability > 0) {
                // due to filtering impossible types, notRecordedTypeProbability can be > 0 although
                // the number of types is lower than what can be recorded in a type profile
                return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "inlining megamorphic calls is disabled (%d types, %f %% not recorded types)", ptypes.size(),
                                notRecordedTypeProbability * 100);
            }

            ArrayList<ProfiledType> usedTypes = ptypes;
            if (notRecordedTypeProbability > 0) {
                // Clean out uncommon types.
                ArrayList<ProfiledType> newTypes = new ArrayList<>();
                for (ProfiledType type : ptypes) {
                    if (type.getProbability() < GraalOptions.MegamorphicInliningMinTypeProbability) {
                        notRecordedTypeProbability += type.getProbability();
                    } else {
                        newTypes.add(type);
                    }
                }

                if (newTypes.size() == 0) {
                    // No type left that is worth checking for.
                    return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "no types remaining after filtering less frequent types (%d types previously)", ptypes.size());
                }

                usedTypes = newTypes;
            }

            // determine concrete methods and map type to specific method
            ArrayList<ResolvedJavaMethod> concreteMethods = new ArrayList<>();
            ArrayList<Double> concreteMethodsProbabilities = new ArrayList<>();
            int[] typesToConcretes = new int[usedTypes.size()];
            for (int i = 0; i < usedTypes.size(); i++) {
                ResolvedJavaMethod concrete = usedTypes.get(i).getType().resolveMethod(targetMethod);

                int index = concreteMethods.indexOf(concrete);
                if (index < 0) {
                    index = concreteMethods.size();
                    concreteMethods.add(concrete);
                    concreteMethodsProbabilities.add(usedTypes.get(i).getProbability());
                } else {
                    concreteMethodsProbabilities.set(index, concreteMethodsProbabilities.get(index) + usedTypes.get(i).getProbability());
                }
                typesToConcretes[i] = index;
            }

            if (notRecordedTypeProbability > 0) {
                // Clean out uncommon methods.
                ArrayList<ResolvedJavaMethod> newConcreteMethods = new ArrayList<>();
                for (int i = 0; i < concreteMethods.size(); ++i) {
                    if (concreteMethodsProbabilities.get(i) >= GraalOptions.MegamorphicInliningMinMethodProbability) {
                        newConcreteMethods.add(concreteMethods.get(i));
                    } else {
                        concreteMethods.set(i, null);
                    }
                }

                if (concreteMethods.size() != newConcreteMethods.size()) {

                    if (newConcreteMethods.size() == 0) {
                        // No method left that is worth inlining.
                        return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "no methods remaining after filtering less frequent methods (%d methods previously)", concreteMethods.size());
                    }

                    // Clean all types that referred to cleaned methods.
                    ArrayList<ProfiledType> newTypes = new ArrayList<>();
                    ArrayList<Integer> newTypesToConcretes = new ArrayList<>();
                    for (int i = 0; i < typesToConcretes.length; ++i) {
                        ResolvedJavaMethod resolvedJavaMethod = concreteMethods.get(typesToConcretes[i]);
                        if (resolvedJavaMethod != null) {
                            newTypes.add(usedTypes.get(i));
                            newTypesToConcretes.add(newConcreteMethods.indexOf(resolvedJavaMethod));
                        } else {
                            notRecordedTypeProbability += usedTypes.get(i).getProbability();
                        }
                    }

                    int[] newTypesToConcretesArray = new int[newTypesToConcretes.size()];
                    for (int i = 0; i < newTypesToConcretesArray.length; ++i) {
                        newTypesToConcretesArray[i] = newTypesToConcretes.get(i);
                        concreteMethods.get(typesToConcretes[i]);
                    }

                    usedTypes = newTypes;
                    typesToConcretes = newTypesToConcretesArray;
                    concreteMethods = newConcreteMethods;
                }
            }

            for (ResolvedJavaMethod concrete : concreteMethods) {
                if (!checkTargetConditions(replacements, invoke, concrete, optimisticOpts)) {
                    return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "it is a polymorphic method call and at least one invoked method cannot be inlined");
                }
            }
            return new MultiTypeGuardInlineInfo(invoke, concreteMethods, usedTypes, typesToConcretes, notRecordedTypeProbability);
        }
    }

    private static ArrayList<ProfiledType> getCompatibleTypes(JavaTypeProfile profile, ResolvedJavaType holder, double[] newNotRecordedTypeProbability) {
        ArrayList<ProfiledType> result = new ArrayList<>();
        double totalIncompatibleProbability = 0.0;
        for (int i = 0; i < profile.getTypes().length; i++) {
            ProfiledType ptype = profile.getTypes()[i];
            ResolvedJavaType type = ptype.getType();
            assert !type.isInterface() && (type.isArray() || !Modifier.isAbstract(type.getModifiers())) : type;
            if (!GraalOptions.OptFilterProfiledTypes || holder.isAssignableFrom(type)) {
                result.add(ptype);
            } else {
                totalIncompatibleProbability += ptype.getProbability();
            }
        }
        newNotRecordedTypeProbability[0] = profile.getNotRecordedProbability();
        if (result.size() != 0 && totalIncompatibleProbability > 0.0) {
            double factor = 1.0 / (1.0 - totalIncompatibleProbability);
            assert factor > 1.0;
            ArrayList<ProfiledType> newResult = new ArrayList<>();
            for (ProfiledType type : result) {
                newResult.add(new ProfiledType(type.getType(), Math.min(1.0, type.getProbability() * factor)));
            }
            newNotRecordedTypeProbability[0] *= factor;
        }
        return result;
    }

    private static ResolvedJavaMethod getCaller(Invoke invoke) {
        return invoke.stateAfter().method();
    }

    private static PiNode createAnchoredReceiver(StructuredGraph graph, FixedNode anchor, ResolvedJavaType commonType, ValueNode receiver, boolean exact) {
        // to avoid that floating reads on receiver fields float above the type check
        return graph.unique(new PiNode(receiver, exact ? StampFactory.exactNonNull(commonType) : StampFactory.declaredNonNull(commonType), anchor));
    }

    // TODO (chaeubl): cleanup this method
    private static boolean checkInvokeConditions(Invoke invoke) {
        if (invoke.predecessor() == null || !invoke.asNode().isAlive()) {
            return logNotInlinedMethodAndReturnFalse(invoke, "the invoke is dead code");
        } else if (!(invoke.callTarget() instanceof MethodCallTargetNode)) {
            return logNotInlinedMethodAndReturnFalse(invoke, "the invoke has already been lowered, or has been created as a low-level node");
        } else if (((MethodCallTargetNode) invoke.callTarget()).targetMethod() == null) {
            return logNotInlinedMethodAndReturnFalse(invoke, "target method is null");
        } else if (invoke.stateAfter() == null) {
            // TODO (chaeubl): why should an invoke not have a state after?
            return logNotInlinedMethodAndReturnFalse(invoke, "the invoke has no after state");
        } else if (!invoke.useForInlining()) {
            return logNotInlinedMethodAndReturnFalse(invoke, "the invoke is marked to be not used for inlining");
        } else if (((MethodCallTargetNode) invoke.callTarget()).receiver() != null && ((MethodCallTargetNode) invoke.callTarget()).receiver().isConstant() &&
                        ((MethodCallTargetNode) invoke.callTarget()).receiver().asConstant().isNull()) {
            return logNotInlinedMethodAndReturnFalse(invoke, "receiver is null");
        } else {
            return true;
        }
    }

    private static boolean checkTargetConditions(Replacements replacements, Invoke invoke, ResolvedJavaMethod method, OptimisticOptimizations optimisticOpts) {
        if (method == null) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "the method is not resolved");
        } else if (Modifier.isNative(method.getModifiers()) && (!GraalOptions.Intrinsify || !InliningUtil.canIntrinsify(replacements, method))) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "it is a non-intrinsic native method");
        } else if (Modifier.isAbstract(method.getModifiers())) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "it is an abstract method");
        } else if (!method.getDeclaringClass().isInitialized()) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "the method's class is not initialized");
        } else if (!method.canBeInlined()) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "it is marked non-inlinable");
        } else if (computeRecursiveInliningLevel(invoke.stateAfter(), method) > GraalOptions.MaximumRecursiveInlining) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "it exceeds the maximum recursive inlining depth");
        } else if (new OptimisticOptimizations(method).lessOptimisticThan(optimisticOpts)) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "the callee uses less optimistic optimizations than caller");
        } else {
            return true;
        }
    }

    private static int computeInliningLevel(Invoke invoke) {
        int count = -1;
        FrameState curState = invoke.stateAfter();
        while (curState != null) {
            count++;
            curState = curState.outerFrameState();
        }
        return count;
    }

    private static int computeRecursiveInliningLevel(FrameState state, ResolvedJavaMethod method) {
        assert state != null;

        int count = 0;
        FrameState curState = state;
        while (curState != null) {
            if (curState.method() == method) {
                count++;
            }
            curState = curState.outerFrameState();
        }
        return count;
    }

    static MonitorExitNode findPrecedingMonitorExit(UnwindNode unwind) {
        Node pred = unwind.predecessor();
        while (pred != null) {
            if (pred instanceof MonitorExitNode) {
                return (MonitorExitNode) pred;
            }
            pred = pred.predecessor();
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
     */
    public static Map<Node, Node> inline(Invoke invoke, StructuredGraph inlineGraph, boolean receiverNullCheck) {
        NodeInputList<ValueNode> parameters = invoke.callTarget().arguments();
        StructuredGraph graph = (StructuredGraph) invoke.asNode().graph();

        FrameState stateAfter = invoke.stateAfter();
        assert stateAfter.isAlive();

        IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();
        ArrayList<Node> nodes = new ArrayList<>();
        ReturnNode returnNode = null;
        UnwindNode unwindNode = null;
        StartNode entryPointNode = inlineGraph.start();
        FixedNode firstCFGNode = entryPointNode.next();
        for (Node node : inlineGraph.getNodes()) {
            if (node == entryPointNode || node == entryPointNode.stateAfter()) {
                // Do nothing.
            } else if (node instanceof LocalNode) {
                replacements.put(node, parameters.get(((LocalNode) node).index()));
            } else {
                nodes.add(node);
                if (node instanceof ReturnNode) {
                    assert returnNode == null;
                    returnNode = (ReturnNode) node;
                } else if (node instanceof UnwindNode) {
                    assert unwindNode == null;
                    unwindNode = (UnwindNode) node;
                }
            }
        }
        // ensure proper anchoring of things that were anchored to the StartNode
        replacements.put(entryPointNode, BeginNode.prevBegin(invoke.asNode()));

        assert invoke.asNode().successors().first() != null : invoke;
        assert invoke.asNode().predecessor() != null;

        Map<Node, Node> duplicates = graph.addDuplicates(nodes, replacements);
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        if (receiverNullCheck) {
            receiverNullCheck(invoke);
        }
        invoke.asNode().replaceAtPredecessor(firstCFGNodeDuplicate);

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
        } else {
            if (unwindNode != null) {
                UnwindNode unwindDuplicate = (UnwindNode) duplicates.get(unwindNode);
                MonitorExitNode monitorExit = findPrecedingMonitorExit(unwindDuplicate);
                DeoptimizeNode deoptimizeNode = new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler);
                unwindDuplicate.replaceAndDelete(graph.add(deoptimizeNode));
                // move the deopt upwards if there is a monitor exit that tries to use the
                // "after exception" frame state
                // (because there is no "after exception" frame state!)
                if (monitorExit != null) {
                    if (monitorExit.stateAfter() != null && monitorExit.stateAfter().bci == FrameState.AFTER_EXCEPTION_BCI) {
                        FrameState monitorFrameState = monitorExit.stateAfter();
                        graph.removeFixed(monitorExit);
                        monitorFrameState.safeDelete();
                    }
                }
            }
        }

        FrameState outerFrameState = null;
        int callerLockDepth = stateAfter.nestedLockDepth();
        for (Node node : duplicates.values()) {
            if (node instanceof FrameState) {
                FrameState frameState = (FrameState) node;
                assert frameState.bci != FrameState.BEFORE_BCI : frameState;
                if (frameState.bci == FrameState.AFTER_BCI) {
                    frameState.replaceAndDelete(stateAfter);
                } else if (frameState.bci == FrameState.AFTER_EXCEPTION_BCI) {
                    if (frameState.isAlive()) {
                        assert stateAtExceptionEdge != null;
                        frameState.replaceAndDelete(stateAtExceptionEdge);
                    } else {
                        assert stateAtExceptionEdge == null;
                    }
                } else {
                    // only handle the outermost frame states
                    if (frameState.outerFrameState() == null) {
                        assert frameState.bci == FrameState.INVALID_FRAMESTATE_BCI || frameState.method() == inlineGraph.method();
                        if (outerFrameState == null) {
                            outerFrameState = stateAfter.duplicateModified(invoke.bci(), stateAfter.rethrowException(), invoke.asNode().kind());
                            outerFrameState.setDuringCall(true);
                        }
                        frameState.setOuterFrameState(outerFrameState);
                    }
                }
            }
            if (callerLockDepth != 0 && node instanceof MonitorReference) {
                MonitorReference monitor = (MonitorReference) node;
                monitor.setLockDepth(monitor.getLockDepth() + callerLockDepth);
            }
        }

        Node returnValue = null;
        if (returnNode != null) {
            if (returnNode.result() instanceof LocalNode) {
                returnValue = replacements.get(returnNode.result());
            } else {
                returnValue = duplicates.get(returnNode.result());
            }
            invoke.asNode().replaceAtUsages(returnValue);
            Node returnDuplicate = duplicates.get(returnNode);
            returnDuplicate.clearInputs();
            Node n = invoke.next();
            invoke.setNext(null);
            returnDuplicate.replaceAndDelete(n);
        }

        invoke.asNode().replaceAtUsages(null);
        GraphUtil.killCFG(invoke.asNode());

        return duplicates;
    }

    public static void receiverNullCheck(Invoke invoke) {
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        StructuredGraph graph = (StructuredGraph) callTarget.graph();
        NodeInputList<ValueNode> parameters = callTarget.arguments();
        ValueNode firstParam = parameters.size() <= 0 ? null : parameters.get(0);
        if (!callTarget.isStatic() && firstParam.kind() == Kind.Object && !firstParam.objectStamp().nonNull()) {
            graph.addBeforeFixed(invoke.asNode(),
                            graph.add(new FixedGuardNode(graph.unique(new IsNullNode(firstParam)), DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true)));
        }
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
}
