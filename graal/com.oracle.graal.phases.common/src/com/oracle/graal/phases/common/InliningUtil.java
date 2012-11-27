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

public class InliningUtil {
    private static final DebugMetric metricInliningTailDuplication = Debug.metric("InliningTailDuplication");
    private static final String inliningDecisionsScopeString = "InliningDecisions";

    public interface InliningCallback {
        StructuredGraph buildGraph(final ResolvedJavaMethod method);
    }

    public interface InliningPolicy {
        void initialize(StructuredGraph graph);
        boolean continueInlining(StructuredGraph graph);
        InlineInfo next();
        void scanInvokes(Iterable<? extends Node> newNodes);
        double inliningWeight(ResolvedJavaMethod caller, ResolvedJavaMethod method, Invoke invoke);
        boolean isWorthInlining(InlineInfo info);
    }

    public interface WeightComputationPolicy {
        double computeWeight(ResolvedJavaMethod caller, ResolvedJavaMethod method, Invoke invoke, boolean preferredInvoke);
    }

    public static void logNotInlinedMethod(InlineInfo info, String msg, Object... args) {
        logInliningDecision(info, false, msg, args);
    }

    public static void logInliningDecision(InlineInfo info, boolean success, String msg, final Object... args) {
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
            String methodString = invoke.callTarget() == null ? "callTarget=null" : invoke.callTarget().targetName();
            logInliningDecision(methodString, false, msg, new Object[0]);
        }
        return false;
    }

    private static InlineInfo logNotInlinedMethodAndReturnNull(Invoke invoke, ResolvedJavaMethod method, String msg) {
        if (shouldLogInliningDecision()) {
            String methodString = methodName(method, invoke);
            logInliningDecision(methodString, false, msg, new Object[0]);
        }
        return null;
    }

    private static boolean logNotInlinedMethodAndReturnFalse(Invoke invoke, ResolvedJavaMethod method, String msg) {
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

    private static boolean shouldLogInliningDecision() {
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
     * The weight is the amortized weight of the additional code - so smaller is better.
     * The level is the number of nested inlinings that lead to this invoke.
     */
    public interface InlineInfo extends Comparable<InlineInfo> {
        Invoke invoke();
        double weight();
        int level();
        int compiledCodeSize();
        int compareTo(InlineInfo o);

        /**
         * Performs the inlining described by this object and returns the node that represents the return value of the
         * inlined method (or null for void methods and methods that have no non-exceptional exit).
         */
        void inline(StructuredGraph graph, GraalCodeCacheProvider runtime, InliningCallback callback, Assumptions assumptions);
    }

    public abstract static class AbstractInlineInfo implements InlineInfo {
        protected final Invoke invoke;
        protected final double weight;

        public AbstractInlineInfo(Invoke invoke, double weight) {
            this.invoke = invoke;
            this.weight = weight;
        }

        @Override
        public int compareTo(InlineInfo o) {
            return (weight < o.weight()) ? -1 : (weight > o.weight()) ? 1 : 0;
        }

        public Invoke invoke() {
            return invoke;
        }

        public double weight() {
            return weight;
        }

        public int level() {
            return computeInliningLevel(invoke);
        }

        protected static StructuredGraph getGraph(final Invoke invoke, final ResolvedJavaMethod concrete, final GraalCodeCacheProvider runtime, final InliningCallback callback) {
            return Debug.scope("GetInliningGraph", concrete, new Callable<StructuredGraph>() {
                @Override
                public StructuredGraph call() throws Exception {
                    StructuredGraph result = getIntrinsicGraph(invoke, concrete, runtime);
                    if (result == null) {
                        assert !Modifier.isNative(concrete.getModifiers());
                        result = callback.buildGraph(concrete);
                    }
                    return result;
                }
            });
        }
    }

    /**
     * Represents an inlining opportunity where the compiler can statically determine a monomorphic target method and
     * therefore is able to determine the called method exactly.
     */
    private static class ExactInlineInfo extends AbstractInlineInfo {
        public final ResolvedJavaMethod concrete;

        public ExactInlineInfo(Invoke invoke, double weight, ResolvedJavaMethod concrete) {
            super(invoke, weight);
            this.concrete = concrete;
        }

        @Override
        public void inline(StructuredGraph compilerGraph, GraalCodeCacheProvider runtime, InliningCallback callback, Assumptions assumptions) {
            StructuredGraph graph = getGraph(invoke, concrete, runtime, callback);
            assumptions.recordMethodContents(concrete);
            InliningUtil.inline(invoke, graph, true);
        }

        @Override
        public int compiledCodeSize() {
            return concrete.getCompiledCodeSize();
        }

        @Override
        public String toString() {
            return "exact " + MetaUtil.format("%H.%n(%p):%r", concrete);
        }
    }

    /**
     * Represents an inlining opportunity for which profiling information suggests a monomorphic receiver, but for which
     * the receiver type cannot be proven. A type check guard will be generated if this inlining is performed.
     */
    private static class TypeGuardInlineInfo extends AbstractInlineInfo {
        public final ResolvedJavaMethod concrete;
        public final ResolvedJavaType type;

        public TypeGuardInlineInfo(Invoke invoke, double weight, ResolvedJavaMethod concrete, ResolvedJavaType type) {
            super(invoke, weight);
            this.concrete = concrete;
            this.type = type;
        }

        @Override
        public int compiledCodeSize() {
            return concrete.getCompiledCodeSize();
        }

        @Override
        public void inline(StructuredGraph graph, GraalCodeCacheProvider runtime, InliningCallback callback, Assumptions assumptions) {
            // receiver null check must be before the type check
            InliningUtil.receiverNullCheck(invoke);
            ValueNode receiver = invoke.methodCallTarget().receiver();
            ConstantNode typeHub = ConstantNode.forConstant(type.getEncoding(Representation.ObjectHub), runtime, graph);
            LoadHubNode receiverHub = graph.add(new LoadHubNode(receiver, typeHub.kind()));
            CompareNode typeCheck = CompareNode.createCompareNode(Condition.EQ, receiverHub, typeHub);
            FixedGuardNode guard = graph.add(new FixedGuardNode(typeCheck, DeoptimizationReason.TypeCheckedInliningViolated, DeoptimizationAction.InvalidateReprofile, invoke.leafGraphId()));
            ValueAnchorNode anchor = graph.add(new ValueAnchorNode());
            assert invoke.predecessor() != null;

            ValueNode anchoredReceiver = createAnchoredReceiver(graph, anchor, type, receiver, true);
            invoke.callTarget().replaceFirstInput(receiver, anchoredReceiver);

            graph.addBeforeFixed(invoke.node(), receiverHub);
            graph.addBeforeFixed(invoke.node(), guard);
            graph.addBeforeFixed(invoke.node(), anchor);

            StructuredGraph calleeGraph = getGraph(invoke, concrete, runtime, callback);
            assumptions.recordMethodContents(concrete);
            InliningUtil.inline(invoke, calleeGraph, false);
        }

        @Override
        public String toString() {
            return "type-checked " + MetaUtil.format("%H.%n(%p):%r", concrete);
        }
    }

    /**
     * Polymorphic inlining of m methods with n type checks (n >= m) in case that the profiling information suggests a reasonable
     * amounts of different receiver types and different methods. If an unknown type is encountered a deoptimization is triggered.
     */
    private static class MultiTypeGuardInlineInfo extends AbstractInlineInfo {
        public final List<ResolvedJavaMethod> concretes;
        public final ProfiledType[] ptypes;
        public final int[] typesToConcretes;
        public final double notRecordedTypeProbability;

        public MultiTypeGuardInlineInfo(Invoke invoke, double weight, List<ResolvedJavaMethod> concretes, ProfiledType[] ptypes,
                        int[] typesToConcretes, double notRecordedTypeProbability) {
            super(invoke, weight);
            assert concretes.size() > 0 && concretes.size() <= ptypes.length : "must have at least one method but no more than types methods";
            assert ptypes.length == typesToConcretes.length : "array lengths must match";

            this.concretes = concretes;
            this.ptypes = ptypes;
            this.typesToConcretes = typesToConcretes;
            this.notRecordedTypeProbability = notRecordedTypeProbability;
        }

        @Override
        public int compiledCodeSize() {
            int result = 0;
            for (ResolvedJavaMethod m: concretes) {
                result += m.getCompiledCodeSize();
            }
            return result;
        }

        @Override
        public void inline(StructuredGraph graph, GraalCodeCacheProvider runtime, InliningCallback callback, Assumptions assumptions) {
            int numberOfMethods = concretes.size();
            boolean hasReturnValue = invoke.node().kind() != Kind.Void;

            // receiver null check must be the first node
            InliningUtil.receiverNullCheck(invoke);
            if (numberOfMethods > 1 || shouldFallbackToInvoke()) {
                inlineMultipleMethods(graph, runtime, callback, assumptions, numberOfMethods, hasReturnValue);
            } else {
                inlineSingleMethod(graph, runtime, callback, assumptions);
            }
        }

        private boolean shouldFallbackToInvoke() {
            return notRecordedTypeProbability > 0;
        }

        private void inlineMultipleMethods(StructuredGraph graph, GraalCodeCacheProvider runtime, InliningCallback callback, Assumptions assumptions, int numberOfMethods, boolean hasReturnValue) {
            FixedNode continuation = invoke.next();

            ValueNode originalReceiver = invoke.methodCallTarget().receiver();
            // setup merge and phi nodes for results and exceptions
            MergeNode returnMerge = graph.add(new MergeNode());
            returnMerge.setProbability(invoke.probability());
            returnMerge.setStateAfter(invoke.stateAfter().duplicate(invoke.stateAfter().bci));

            PhiNode returnValuePhi = null;
            if (hasReturnValue) {
                returnValuePhi = graph.unique(new PhiNode(invoke.node().kind(), returnMerge));
            }

            MergeNode exceptionMerge = null;
            PhiNode exceptionObjectPhi = null;
            if (invoke instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
                DispatchBeginNode exceptionEdge = invokeWithException.exceptionEdge();
                ExceptionObjectNode exceptionObject = (ExceptionObjectNode) exceptionEdge.next();

                exceptionMerge = graph.add(new MergeNode());
                exceptionMerge.setProbability(exceptionEdge.probability());

                FixedNode exceptionSux = exceptionObject.next();
                graph.addBeforeFixed(exceptionSux, exceptionMerge);
                exceptionObjectPhi = graph.unique(new PhiNode(Kind.Object, exceptionMerge));
                exceptionMerge.setStateAfter(exceptionEdge.stateAfter().duplicateModified(invoke.stateAfter().bci, true, Kind.Void, exceptionObjectPhi));
            }

            // create one separate block for each invoked method
            BeginNode[] successors = new BeginNode[numberOfMethods + 1];
            for (int i = 0; i < numberOfMethods; i++) {
                double probability = 0;
                for (int j = 0; j < typesToConcretes.length; j++) {
                    if (typesToConcretes[j] == i) {
                        probability += ptypes[j].getProbability();
                    }
                }

                successors[i] = createInvocationBlock(graph, invoke, returnMerge, returnValuePhi, exceptionMerge, exceptionObjectPhi, invoke.probability() * probability, true);
            }

            // create the successor for an unknown type
            FixedNode unknownTypeSux;
            if (shouldFallbackToInvoke()) {
                unknownTypeSux = createInvocationBlock(graph, invoke, returnMerge, returnValuePhi, exceptionMerge, exceptionObjectPhi, notRecordedTypeProbability, false);
            } else {
                unknownTypeSux = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TypeCheckedInliningViolated, invoke.leafGraphId()));
            }
            successors[successors.length - 1] = BeginNode.begin(unknownTypeSux);

            // replace the invoke exception edge
            if (invoke instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) invoke;
                BeginNode exceptionEdge = invokeWithExceptionNode.exceptionEdge();
                ExceptionObjectNode exceptionObject = (ExceptionObjectNode) exceptionEdge.next();
                exceptionObject.replaceAtUsages(exceptionObjectPhi);
                exceptionObject.setNext(null);
                GraphUtil.killCFG(invokeWithExceptionNode.exceptionEdge());
            }

            // get all graphs and record assumptions
            assert invoke.node().isAlive();
            StructuredGraph[] calleeGraphs = new StructuredGraph[numberOfMethods];
            for (int i = 0; i < numberOfMethods; i++) {
                ResolvedJavaMethod concrete = concretes.get(i);
                calleeGraphs[i] = getGraph(invoke, concrete, runtime, callback);
                assumptions.recordMethodContents(concrete);
            }

            // replace the invoke with a switch on the type of the actual receiver
            Kind hubKind = invoke.methodCallTarget().targetMethod().getDeclaringClass().getEncoding(Representation.ObjectHub).getKind();
            LoadHubNode receiverHub = graph.add(new LoadHubNode(invoke.methodCallTarget().receiver(), hubKind));
            graph.addBeforeFixed(invoke.node(), receiverHub);
            FixedNode dispatchOnType = createDispatchOnType(graph, receiverHub, successors);

            assert invoke.next() == continuation;
            invoke.setNext(null);
            returnMerge.setNext(continuation);
            invoke.node().replaceAtUsages(returnValuePhi);
            invoke.node().replaceAndDelete(dispatchOnType);

            ArrayList<PiNode> replacements = new ArrayList<>();

            // do the actual inlining for every invoke
            for (int i = 0; i < numberOfMethods; i++) {
                BeginNode node = successors[i];
                Invoke invokeForInlining = (Invoke) node.next();

                ResolvedJavaType commonType = getLeastCommonType(i);
                ValueNode receiver = invokeForInlining.methodCallTarget().receiver();
                boolean exact = getTypeCount(i) == 1;
                PiNode anchoredReceiver = createAnchoredReceiver(graph, node, commonType, receiver, exact);
                invokeForInlining.callTarget().replaceFirstInput(receiver, anchoredReceiver);

                StructuredGraph calleeGraph = calleeGraphs[i];
                InliningUtil.inline(invokeForInlining, calleeGraph, false);
                replacements.add(anchoredReceiver);
            }
            if (shouldFallbackToInvoke()) {
                replacements.add(null);
            }
            if (GraalOptions.OptTailDuplication) {
                /*
                 * We might want to perform tail duplication at the merge after a type switch, if there are invokes that would
                 * benefit from the improvement in type information.
                 */
                FixedNode current = returnMerge;
                int opportunities = 0;
                do {
                    if (current instanceof InvokeNode && ((InvokeNode) current).methodCallTarget().receiver() == originalReceiver) {
                        opportunities++;
                    } else if (current.inputs().contains(originalReceiver)) {
                        opportunities++;
                    }
                    current = ((FixedWithNextNode) current).next();
                } while (current instanceof FixedWithNextNode);
                if (opportunities > 0) {
                    metricInliningTailDuplication.increment();
                    Debug.log("MultiTypeGuardInlineInfo starting tail duplication (%d opportunities)", opportunities);
                    TailDuplicationPhase.tailDuplicate(returnMerge, TailDuplicationPhase.TRUE_DECISION, replacements);
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
                        commonType = ptypes[i].getType();
                    } else {
                        commonType = commonType.findLeastCommonAncestor(ptypes[i].getType());
                    }
                }
            }
            assert commonType != null;
            return commonType;
        }

        private void inlineSingleMethod(StructuredGraph graph, GraalCodeCacheProvider runtime, InliningCallback callback, Assumptions assumptions) {
            assert concretes.size() == 1 && ptypes.length > 1 && !shouldFallbackToInvoke() && notRecordedTypeProbability == 0;

            BeginNode calleeEntryNode = graph.add(new BeginNode());
            calleeEntryNode.setProbability(invoke.probability());
            Kind hubKind = invoke.methodCallTarget().targetMethod().getDeclaringClass().getEncoding(Representation.ObjectHub).getKind();
            LoadHubNode receiverHub = graph.add(new LoadHubNode(invoke.methodCallTarget().receiver(), hubKind));
            graph.addBeforeFixed(invoke.node(), receiverHub);

            BeginNode unknownTypeSux = BeginNode.begin(graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TypeCheckedInliningViolated, invoke.leafGraphId())));
            BeginNode[] successors = new BeginNode[] {calleeEntryNode, unknownTypeSux};
            FixedNode dispatchOnType = createDispatchOnType(graph, receiverHub, successors);

            FixedWithNextNode pred = (FixedWithNextNode) invoke.node().predecessor();
            pred.setNext(dispatchOnType);
            calleeEntryNode.setNext(invoke.node());

            ResolvedJavaMethod concrete = concretes.get(0);
            StructuredGraph calleeGraph = getGraph(invoke, concrete, runtime, callback);
            assumptions.recordMethodContents(concrete);
            InliningUtil.inline(invoke, calleeGraph, false);
        }

        private FixedNode createDispatchOnType(StructuredGraph graph, LoadHubNode hub, BeginNode[] successors) {
            assert ptypes.length > 1;

            ResolvedJavaType[] keys = new ResolvedJavaType[ptypes.length];
            double[] keyProbabilities = new double[ptypes.length + 1];
            int[] keySuccessors = new int[ptypes.length + 1];
            for (int i = 0; i < ptypes.length; i++) {
                keys[i] = ptypes[i].getType();
                keyProbabilities[i] = ptypes[i].getProbability();
                keySuccessors[i] = typesToConcretes[i];
                assert keySuccessors[i] < successors.length - 1 : "last successor is the unknownTypeSux";
            }
            keyProbabilities[keyProbabilities.length - 1] = notRecordedTypeProbability;
            keySuccessors[keySuccessors.length - 1] = successors.length - 1;

            double[] successorProbabilities = SwitchNode.successorProbabilites(successors.length, keySuccessors, keyProbabilities);
            TypeSwitchNode typeSwitch = graph.add(new TypeSwitchNode(hub, successors, successorProbabilities, keys, keyProbabilities, keySuccessors));

            return typeSwitch;
        }

        private static BeginNode createInvocationBlock(StructuredGraph graph, Invoke invoke, MergeNode returnMerge, PhiNode returnValuePhi,
                        MergeNode exceptionMerge, PhiNode exceptionObjectPhi, double probability, boolean useForInlining) {
            Invoke duplicatedInvoke = duplicateInvokeForInlining(graph, invoke, exceptionMerge, exceptionObjectPhi, useForInlining, probability);
            BeginNode calleeEntryNode = graph.add(new BeginNode());
            calleeEntryNode.setNext(duplicatedInvoke.node());
            calleeEntryNode.setProbability(probability);

            EndNode endNode = graph.add(new EndNode());
            endNode.setProbability(probability);

            duplicatedInvoke.setNext(endNode);
            returnMerge.addForwardEnd(endNode);

            if (returnValuePhi != null) {
                returnValuePhi.addInput(duplicatedInvoke.node());
            }
            return calleeEntryNode;
        }

        private static Invoke duplicateInvokeForInlining(StructuredGraph graph, Invoke invoke, MergeNode exceptionMerge, PhiNode exceptionObjectPhi, boolean useForInlining, double probability) {
            Invoke result = (Invoke) invoke.node().copyWithInputs();
            Node callTarget = result.callTarget().copyWithInputs();
            result.node().replaceFirstInput(result.callTarget(), callTarget);
            result.setUseForInlining(useForInlining);
            result.setProbability(probability);

            Kind kind = invoke.node().kind();
            if (!kind.isVoid()) {
                FrameState stateAfter = invoke.stateAfter();
                stateAfter = stateAfter.duplicate(stateAfter.bci);
                stateAfter.replaceFirstInput(invoke.node(), result.node());
                result.setStateAfter(stateAfter);
            }

            if (invoke instanceof InvokeWithExceptionNode) {
                assert exceptionMerge != null && exceptionObjectPhi != null;

                InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
                BeginNode exceptionEdge = invokeWithException.exceptionEdge();
                ExceptionObjectNode exceptionObject = (ExceptionObjectNode) exceptionEdge.next();
                FrameState stateAfterException = exceptionObject.stateAfter();

                BeginNode newExceptionEdge = (BeginNode) exceptionEdge.copyWithInputs();
                ExceptionObjectNode newExceptionObject = (ExceptionObjectNode) exceptionObject.copyWithInputs();
                // set new state (pop old exception object, push new one)
                newExceptionObject.setStateAfter(stateAfterException.duplicateModified(stateAfterException.bci, stateAfterException.rethrowException(), Kind.Object, newExceptionObject));
                newExceptionEdge.setNext(newExceptionObject);

                EndNode endNode = graph.add(new EndNode());
                newExceptionObject.setNext(endNode);
                exceptionMerge.addForwardEnd(endNode);
                exceptionObjectPhi.addInput(newExceptionObject);

                ((InvokeWithExceptionNode) result).setExceptionEdge(newExceptionEdge);
            }
            return result;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(shouldFallbackToInvoke() ? "megamorphic" : "polymorphic");
            builder.append(String.format(", %d methods with %d type checks:", concretes.size(), ptypes.length));
            for (int i = 0; i < concretes.size(); i++) {
                builder.append(MetaUtil.format("  %H.%n(%p):%r", concretes.get(i)));
            }
            return builder.toString();
        }
    }


    /**
     * Represents an inlining opportunity where the current class hierarchy leads to a monomorphic target method,
     * but for which an assumption has to be registered because of non-final classes.
     */
    private static class AssumptionInlineInfo extends ExactInlineInfo {
        public final ResolvedJavaType context;

        public AssumptionInlineInfo(Invoke invoke, double weight, ResolvedJavaType context, ResolvedJavaMethod concrete) {
            super(invoke, weight, concrete);
            this.context = context;
        }

        @Override
        public void inline(StructuredGraph graph, GraalCodeCacheProvider runtime, InliningCallback callback, Assumptions assumptions) {
            if (Debug.isLogEnabled()) {
                String targetName = MetaUtil.format("%H.%n(%p):%r", invoke.methodCallTarget().targetMethod());
                String concreteName = MetaUtil.format("%H.%n(%p):%r", concrete);
                Debug.log("recording concrete method assumption: %s on receiver type %s -> %s", targetName, context, concreteName);
            }
            assumptions.recordConcreteMethod(invoke.methodCallTarget().targetMethod(), context, concrete);

            super.inline(graph, runtime, callback, assumptions);
        }

        @Override
        public String toString() {
            return "assumption " + MetaUtil.format("%H.%n(%p):%r", concrete);
        }
    }

    /**
     * Determines if inlining is possible at the given invoke node.
     * @param invoke the invoke that should be inlined
     * @param runtime a GraalRuntime instance used to determine of the invoke can be inlined and/or should be intrinsified
     * @param inliningPolicy used to determine the weight of a specific inlining
     * @return an instance of InlineInfo, or null if no inlining is possible at the given invoke
     */
    public static InlineInfo getInlineInfo(Invoke invoke, GraalCodeCacheProvider runtime, Assumptions assumptions, InliningPolicy inliningPolicy, OptimisticOptimizations optimisticOpts) {
        if (!checkInvokeConditions(invoke)) {
            return null;
        }
        ResolvedJavaMethod caller = getCaller(invoke);
        MethodCallTargetNode callTarget = invoke.methodCallTarget();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();

        if (callTarget.invokeKind() == InvokeKind.Special || targetMethod.canBeStaticallyBound()) {
            if (!checkTargetConditions(invoke, targetMethod, optimisticOpts, runtime)) {
                return null;
            }
            double weight = inliningPolicy.inliningWeight(caller, targetMethod, invoke);
            return new ExactInlineInfo(invoke, weight, targetMethod);
        }
        ObjectStamp receiverStamp = callTarget.receiver().objectStamp();
        ResolvedJavaType receiverType = receiverStamp.type();
        if (receiverStamp.isExactType()) {
            assert receiverType.isAssignableTo(targetMethod.getDeclaringClass()) : receiverType + " subtype of " + targetMethod.getDeclaringClass() + " for " + targetMethod;
            ResolvedJavaMethod resolved = receiverType.resolveMethod(targetMethod);
            if (!checkTargetConditions(invoke, resolved, optimisticOpts, runtime)) {
                return null;
            }
            double weight = inliningPolicy.inliningWeight(caller, resolved, invoke);
            return new ExactInlineInfo(invoke, weight, resolved);
        }
        ResolvedJavaType holder = targetMethod.getDeclaringClass();

        if (receiverStamp.type() != null) {
            // the invoke target might be more specific than the holder (happens after inlining: locals lose their declared type...)
            // TODO (lstadler) fix this
            if (receiverType != null && receiverType.isAssignableTo(holder)) {
                holder = receiverType;
            }
        }
        // TODO (thomaswue) fix this
        if (assumptions.useOptimisticAssumptions()) {
            ResolvedJavaMethod concrete = holder.findUniqueConcreteMethod(targetMethod);
            if (concrete != null) {
                if (!checkTargetConditions(invoke, concrete, optimisticOpts, runtime)) {
                    return null;
                }
                double weight = inliningPolicy.inliningWeight(caller, concrete, invoke);
                return new AssumptionInlineInfo(invoke, weight, holder, concrete);
            }
        }

        // type check based inlining
        return getTypeCheckedInlineInfo(invoke, inliningPolicy, caller, targetMethod, optimisticOpts, runtime);
    }

    private static InlineInfo getTypeCheckedInlineInfo(Invoke invoke, InliningPolicy inliningPolicy, ResolvedJavaMethod caller,
                    ResolvedJavaMethod targetMethod, OptimisticOptimizations optimisticOpts, GraalCodeCacheProvider runtime) {
        ProfilingInfo profilingInfo = caller.getProfilingInfo();
        JavaTypeProfile typeProfile = profilingInfo.getTypeProfile(invoke.bci());
        if (typeProfile == null) {
            return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "no type profile exists");
        }

        ProfiledType[] ptypes = typeProfile.getTypes();
        if (ptypes == null || ptypes.length <= 0) {
            return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "no types/probabilities were recorded");
        }

        double notRecordedTypeProbability = typeProfile.getNotRecordedProbability();
        if (ptypes.length == 1 && notRecordedTypeProbability == 0) {
            if (!optimisticOpts.inlineMonomorphicCalls()) {
                return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "inlining monomorphic calls is disabled");
            }

            ResolvedJavaType type = ptypes[0].getType();
            ResolvedJavaMethod concrete = type.resolveMethod(targetMethod);
            if (!checkTargetConditions(invoke, concrete, optimisticOpts, runtime)) {
                return null;
            }
            double weight = inliningPolicy.inliningWeight(caller, concrete, invoke);
            return new TypeGuardInlineInfo(invoke, weight, concrete, type);
        } else {
            invoke.setPolymorphic(true);


            if (!optimisticOpts.inlinePolymorphicCalls() && notRecordedTypeProbability == 0) {
                return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "inlining polymorphic calls is disabled");
            }
            if (!optimisticOpts.inlineMegamorphicCalls() && notRecordedTypeProbability > 0) {
                return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "inlining megamorphic calls is disabled");
            }

            // TODO (chaeubl) inlining of multiple methods should work differently
            // 1. check which methods can be inlined
            // 2. for those methods, use weight and probability to compute which of them should be inlined
            // 3. do the inlining
            //    a) all seen methods can be inlined -> do so and guard with deopt
            //    b) some methods can be inlined -> inline them and fall back to invocation if violated

            // determine concrete methods and map type to specific method
            ArrayList<ResolvedJavaMethod> concreteMethods = new ArrayList<>();
            int[] typesToConcretes = new int[ptypes.length];
            for (int i = 0; i < ptypes.length; i++) {
                ResolvedJavaMethod concrete = ptypes[i].getType().resolveMethod(targetMethod);

                int index = concreteMethods.indexOf(concrete);
                if (index < 0) {
                    index = concreteMethods.size();
                    concreteMethods.add(concrete);
                }
                typesToConcretes[i] = index;
            }

            double totalWeight = 0;
            for (ResolvedJavaMethod concrete: concreteMethods) {
                if (!checkTargetConditions(invoke, concrete, optimisticOpts, runtime)) {
                    return logNotInlinedMethodAndReturnNull(invoke, targetMethod, "it is a polymorphic method call and at least one invoked method cannot be inlined");
                }
                totalWeight += inliningPolicy.inliningWeight(caller, concrete, invoke);
            }
            return new MultiTypeGuardInlineInfo(invoke, totalWeight, concreteMethods, ptypes, typesToConcretes, notRecordedTypeProbability);
        }
    }


    private static ResolvedJavaMethod getCaller(Invoke invoke) {
        return invoke.stateAfter().method();
    }

    private static PiNode createAnchoredReceiver(StructuredGraph graph, FixedNode anchor, ResolvedJavaType commonType, ValueNode receiver, boolean exact) {
        // to avoid that floating reads on receiver fields float above the type check
        return graph.unique(new PiNode(receiver, anchor, exact ? StampFactory.exactNonNull(commonType) : StampFactory.declaredNonNull(commonType)));
    }

    private static boolean checkInvokeConditions(Invoke invoke) {
        if (!(invoke.callTarget() instanceof MethodCallTargetNode)) {
            return logNotInlinedMethodAndReturnFalse(invoke, "the invoke has already been lowered, or has been created as a low-level node");
        } else if (invoke.methodCallTarget().targetMethod() == null) {
            return logNotInlinedMethodAndReturnFalse(invoke, "target method is null");
        } else if (invoke.stateAfter() == null) {
            return logNotInlinedMethodAndReturnFalse(invoke, "the invoke has no after state");
        } else if (invoke.predecessor() == null || !invoke.node().isAlive()) {
            return logNotInlinedMethodAndReturnFalse(invoke, "the invoke is dead code");
        } else if (!invoke.useForInlining()) {
            return logNotInlinedMethodAndReturnFalse(invoke, "the invoke is marked to be not used for inlining");
        } else {
            return true;
        }
    }

    private static boolean checkTargetConditions(Invoke invoke, ResolvedJavaMethod method, OptimisticOptimizations optimisticOpts, GraalCodeCacheProvider runtime) {
        if (method == null) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "the method is not resolved");
        } else if (Modifier.isNative(method.getModifiers()) && (!GraalOptions.Intrinsify || !InliningUtil.canIntrinsify(invoke, method, runtime))) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "it is a non-intrinsic native method");
        } else if (Modifier.isAbstract(method.getModifiers())) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "it is an abstract method");
        } else if (!method.getDeclaringClass().isInitialized()) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "the method's class is not initialized");
        } else if (!method.canBeInlined()) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "it is marked non-inlinable");
        } else if (computeInliningLevel(invoke) > GraalOptions.MaximumInlineLevel) {
            return logNotInlinedMethodAndReturnFalse(invoke, method, "it exceeds the maximum inlining depth");
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

    /**
     * Performs an actual inlining, thereby replacing the given invoke with the given inlineGraph.
     *
     * @param invoke the invoke that will be replaced
     * @param inlineGraph the graph that the invoke will be replaced with
     * @param receiverNullCheck true if a null check needs to be generated for non-static inlinings, false if no such check is required
     */
    public static void inline(Invoke invoke, StructuredGraph inlineGraph, boolean receiverNullCheck) {
        NodeInputList<ValueNode> parameters = invoke.callTarget().arguments();
        StructuredGraph graph = (StructuredGraph) invoke.node().graph();

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
        replacements.put(entryPointNode, BeginNode.prevBegin(invoke.node())); // ensure proper anchoring of things that where anchored to the StartNode

        assert invoke.node().successors().first() != null : invoke;
        assert invoke.node().predecessor() != null;

        Map<Node, Node> duplicates = graph.addDuplicates(nodes, replacements);
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        if (receiverNullCheck) {
            receiverNullCheck(invoke);
        }
        invoke.node().replaceAtPredecessor(firstCFGNodeDuplicate);

        FrameState stateAtExceptionEdge = null;
        if (invoke instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithException = ((InvokeWithExceptionNode) invoke);
            if (unwindNode != null) {
                assert unwindNode.predecessor() != null;
                assert invokeWithException.exceptionEdge().successors().count() == 1;
                ExceptionObjectNode obj = (ExceptionObjectNode) invokeWithException.exceptionEdge().next();
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
                DeoptimizeNode deoptimizeNode = new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler, invoke.leafGraphId());
                unwindDuplicate.replaceAndDelete(graph.add(deoptimizeNode));
                // move the deopt upwards if there is a monitor exit that tries to use the "after exception" frame state
                // (because there is no "after exception" frame state!)
                if (deoptimizeNode.predecessor() instanceof MonitorExitNode) {
                    MonitorExitNode monitorExit = (MonitorExitNode) deoptimizeNode.predecessor();
                    if (monitorExit.stateAfter() != null && monitorExit.stateAfter().bci == FrameState.AFTER_EXCEPTION_BCI) {
                        FrameState monitorFrameState = monitorExit.stateAfter();
                        graph.removeFixed(monitorExit);
                        monitorFrameState.safeDelete();
                    }
                }
            }
        }

        FrameState outerFrameState = null;
        double invokeProbability = invoke.node().probability();
        for (Node node : duplicates.values()) {
            if (GraalOptions.ProbabilityAnalysis) {
                if (node instanceof FixedNode) {
                    FixedNode fixed = (FixedNode) node;
                    double newProbability = fixed.probability() * invokeProbability;
                    if (GraalOptions.LimitInlinedProbability) {
                        newProbability = Math.min(newProbability, invokeProbability);
                    }
                    fixed.setProbability(newProbability);
                }
            }
            if (node instanceof FrameState) {
                FrameState frameState = (FrameState) node;
                assert frameState.bci != FrameState.BEFORE_BCI;
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
                        assert frameState.method() == inlineGraph.method();
                        if (outerFrameState == null) {
                            outerFrameState = stateAfter.duplicateModified(invoke.bci(), stateAfter.rethrowException(), invoke.node().kind());
                            outerFrameState.setDuringCall(true);
                        }
                        frameState.setOuterFrameState(outerFrameState);
                    }
                }
            }
        }

        Node returnValue = null;
        if (returnNode != null) {
            if (returnNode.result() instanceof LocalNode) {
                returnValue = replacements.get(returnNode.result());
            } else {
                returnValue = duplicates.get(returnNode.result());
            }
            invoke.node().replaceAtUsages(returnValue);
            Node returnDuplicate = duplicates.get(returnNode);
            returnDuplicate.clearInputs();
            Node n = invoke.next();
            invoke.setNext(null);
            returnDuplicate.replaceAndDelete(n);
        }

        invoke.node().replaceAtUsages(null);
        GraphUtil.killCFG(invoke.node());
    }

    public static void receiverNullCheck(Invoke invoke) {
        MethodCallTargetNode callTarget = invoke.methodCallTarget();
        StructuredGraph graph = (StructuredGraph) invoke.graph();
        NodeInputList<ValueNode> parameters = callTarget.arguments();
        ValueNode firstParam = parameters.size() <= 0 ? null : parameters.get(0);
        if (!callTarget.isStatic() && firstParam.kind() == Kind.Object && !firstParam.objectStamp().nonNull()) {
            graph.addBeforeFixed(invoke.node(), graph.add(new FixedGuardNode(graph.unique(new IsNullNode(firstParam)), DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true, invoke.leafGraphId())));
        }
    }

    public static boolean canIntrinsify(Invoke invoke, ResolvedJavaMethod target, GraalCodeCacheProvider runtime) {
        return getIntrinsicGraph(invoke, target, runtime) != null;
    }

    private static StructuredGraph getIntrinsicGraph(Invoke invoke, ResolvedJavaMethod target, GraalCodeCacheProvider runtime) {
        assert invoke.node().isAlive();

        StructuredGraph intrinsicGraph = (StructuredGraph) target.getCompilerStorage().get(Graph.class);
        if (intrinsicGraph == null && runtime != null) {
            // TODO remove once all intrinsics are available via compilerStorage
            intrinsicGraph = runtime.intrinsicGraph(invoke.stateAfter().method(), invoke.bci(), target, invoke.callTarget().arguments());
        }
        return intrinsicGraph;
    }
}
