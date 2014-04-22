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

import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.compiler.common.type.StampFactory.*;
import static java.lang.reflect.Modifier.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Assumptions.Assumption;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.JavaTypeProfile.ProfiledType;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
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
import com.oracle.graal.phases.common.InliningPhase.InliningData;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;

public class InliningUtil {

    private static final DebugMetric metricInliningTailDuplication = Debug.metric("InliningTailDuplication");
    private static final String inliningDecisionsScopeString = "InliningDecisions";
    /**
     * Meters the size (in bytecodes) of all methods processed during compilation (i.e., top level
     * and all inlined methods), irrespective of how many bytecodes in each method are actually
     * parsed (which may be none for methods whose IR is retrieved from a cache).
     */
    public static final DebugMetric InlinedBytecodes = Debug.metric("InlinedBytecodes");

    public interface InliningPolicy {

        boolean continueInlining(StructuredGraph graph);

        boolean isWorthInlining(Replacements replacements, InlineInfo info, int inliningDepth, double probability, double relevance, boolean fullyProcessed);
    }

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
            final int mod = method.getModifiers();
            // 1234567
            TTY.print("        ");     // print timestamp
            // 1234
            TTY.print("     ");        // print compilation number
            // % s ! b n
            TTY.print("%c%c%c%c%c ", ' ', Modifier.isSynchronized(mod) ? 's' : ' ', ' ', ' ', Modifier.isNative(mod) ? 'n' : ' ');
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

    /**
     * Represents an opportunity for inlining at a given invoke, with the given weight and level.
     * The weight is the amortized weight of the additional code - so smaller is better. The level
     * is the number of nested inlinings that lead to this invoke.
     */
    public interface InlineInfo {

        /**
         * The graph containing the {@link #invoke() invocation} that may be inlined.
         */
        StructuredGraph graph();

        /**
         * The invocation that may be inlined.
         */
        Invoke invoke();

        /**
         * Returns the number of methods that may be inlined by the {@link #invoke() invocation}.
         * This may be more than one in the case of a invocation profile showing a number of "hot"
         * concrete methods dispatched to by the invocation.
         */
        int numberOfMethods();

        ResolvedJavaMethod methodAt(int index);

        Inlineable inlineableElementAt(int index);

        double probabilityAt(int index);

        double relevanceAt(int index);

        void setInlinableElement(int index, Inlineable inlineableElement);

        /**
         * Performs the inlining described by this object and returns the node that represents the
         * return value of the inlined method (or null for void methods and methods that have no
         * non-exceptional exit).
         */
        void inline(Providers providers, Assumptions assumptions);

        /**
         * Try to make the call static bindable to avoid interface and virtual method calls.
         */
        void tryToDevirtualizeInvoke(MetaAccessProvider metaAccess, Assumptions assumptions);

        boolean shouldInline();
    }

    public abstract static class AbstractInlineInfo implements InlineInfo {

        protected final Invoke invoke;

        public AbstractInlineInfo(Invoke invoke) {
            this.invoke = invoke;
        }

        @Override
        public StructuredGraph graph() {
            return invoke.asNode().graph();
        }

        @Override
        public Invoke invoke() {
            return invoke;
        }

        protected static void inline(Invoke invoke, ResolvedJavaMethod concrete, Inlineable inlineable, Assumptions assumptions, boolean receiverNullCheck) {
            if (inlineable instanceof InlineableGraph) {
                StructuredGraph calleeGraph = ((InlineableGraph) inlineable).getGraph();
                InliningUtil.inline(invoke, calleeGraph, receiverNullCheck);
            } else {
                assert inlineable instanceof InlineableMacroNode;

                Class<? extends FixedWithNextNode> macroNodeClass = ((InlineableMacroNode) inlineable).getMacroNodeClass();
                inlineMacroNode(invoke, concrete, macroNodeClass);
            }

            InlinedBytecodes.add(concrete.getCodeSize());
            assumptions.recordMethodContents(concrete);
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
    public static class ExactInlineInfo extends AbstractInlineInfo {

        protected final ResolvedJavaMethod concrete;
        private Inlineable inlineableElement;
        private boolean suppressNullCheck;

        public ExactInlineInfo(Invoke invoke, ResolvedJavaMethod concrete) {
            super(invoke);
            this.concrete = concrete;
            assert concrete != null;
        }

        public void suppressNullCheck() {
            suppressNullCheck = true;
        }

        @Override
        public void inline(Providers providers, Assumptions assumptions) {
            inline(invoke, concrete, inlineableElement, assumptions, !suppressNullCheck);
        }

        @Override
        public void tryToDevirtualizeInvoke(MetaAccessProvider metaAccess, Assumptions assumptions) {
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
        public double probabilityAt(int index) {
            assert index == 0;
            return 1.0;
        }

        @Override
        public double relevanceAt(int index) {
            assert index == 0;
            return 1.0;
        }

        @Override
        public String toString() {
            return "exact " + MetaUtil.format("%H.%n(%p):%r", concrete);
        }

        @Override
        public Inlineable inlineableElementAt(int index) {
            assert index == 0;
            return inlineableElement;
        }

        @Override
        public void setInlinableElement(int index, Inlineable inlineableElement) {
            assert index == 0;
            this.inlineableElement = inlineableElement;
        }

        public boolean shouldInline() {
            return concrete.shouldBeInlined();
        }
    }

    /**
     * Represents an inlining opportunity for which profiling information suggests a monomorphic
     * receiver, but for which the receiver type cannot be proven. A type check guard will be
     * generated if this inlining is performed.
     */
    private static class TypeGuardInlineInfo extends AbstractInlineInfo {

        private final ResolvedJavaMethod concrete;
        private final ResolvedJavaType type;
        private Inlineable inlineableElement;

        public TypeGuardInlineInfo(Invoke invoke, ResolvedJavaMethod concrete, ResolvedJavaType type) {
            super(invoke);
            this.concrete = concrete;
            this.type = type;
            assert type.isArray() || !isAbstract(type.getModifiers()) : type;
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
        public Inlineable inlineableElementAt(int index) {
            assert index == 0;
            return inlineableElement;
        }

        @Override
        public double probabilityAt(int index) {
            assert index == 0;
            return 1.0;
        }

        @Override
        public double relevanceAt(int index) {
            assert index == 0;
            return 1.0;
        }

        @Override
        public void setInlinableElement(int index, Inlineable inlineableElement) {
            assert index == 0;
            this.inlineableElement = inlineableElement;
        }

        @Override
        public void inline(Providers providers, Assumptions assumptions) {
            createGuard(graph(), providers.getMetaAccess());
            inline(invoke, concrete, inlineableElement, assumptions, false);
        }

        @Override
        public void tryToDevirtualizeInvoke(MetaAccessProvider metaAccess, Assumptions assumptions) {
            createGuard(graph(), metaAccess);
            replaceInvokeCallTarget(invoke, graph(), InvokeKind.Special, concrete);
        }

        private void createGuard(StructuredGraph graph, MetaAccessProvider metaAccess) {
            ValueNode nonNullReceiver = InliningUtil.nonNullReceiver(invoke);
            ConstantNode typeHub = ConstantNode.forConstant(type.getEncoding(Representation.ObjectHub), metaAccess, graph);
            LoadHubNode receiverHub = graph.unique(new LoadHubNode(nonNullReceiver, typeHub.getKind()));

            CompareNode typeCheck = CompareNode.createCompareNode(graph, Condition.EQ, receiverHub, typeHub);
            FixedGuardNode guard = graph.add(new FixedGuardNode(typeCheck, DeoptimizationReason.TypeCheckedInliningViolated, DeoptimizationAction.InvalidateReprofile));
            assert invoke.predecessor() != null;

            ValueNode anchoredReceiver = createAnchoredReceiver(graph, guard, type, nonNullReceiver, true);
            invoke.callTarget().replaceFirstInput(nonNullReceiver, anchoredReceiver);

            graph.addBeforeFixed(invoke.asNode(), guard);
        }

        @Override
        public String toString() {
            return "type-checked with type " + type.getName() + " and method " + MetaUtil.format("%H.%n(%p):%r", concrete);
        }

        public boolean shouldInline() {
            return concrete.shouldBeInlined();
        }
    }

    /**
     * Polymorphic inlining of m methods with n type checks (n &ge; m) in case that the profiling
     * information suggests a reasonable amount of different receiver types and different methods.
     * If an unknown type is encountered a deoptimization is triggered.
     */
    private static class MultiTypeGuardInlineInfo extends AbstractInlineInfo {

        private final List<ResolvedJavaMethod> concretes;
        private final double[] methodProbabilities;
        private final double maximumMethodProbability;
        private final ArrayList<Integer> typesToConcretes;
        private final ArrayList<ProfiledType> ptypes;
        private final ArrayList<Double> concretesProbabilities;
        private final double notRecordedTypeProbability;
        private final Inlineable[] inlineableElements;

        public MultiTypeGuardInlineInfo(Invoke invoke, ArrayList<ResolvedJavaMethod> concretes, ArrayList<Double> concretesProbabilities, ArrayList<ProfiledType> ptypes,
                        ArrayList<Integer> typesToConcretes, double notRecordedTypeProbability) {
            super(invoke);
            assert concretes.size() > 0 : "must have at least one method";
            assert ptypes.size() == typesToConcretes.size() : "array lengths must match";

            this.concretesProbabilities = concretesProbabilities;
            this.concretes = concretes;
            this.ptypes = ptypes;
            this.typesToConcretes = typesToConcretes;
            this.notRecordedTypeProbability = notRecordedTypeProbability;
            this.inlineableElements = new Inlineable[concretes.size()];
            this.methodProbabilities = computeMethodProbabilities();
            this.maximumMethodProbability = maximumMethodProbability();
            assert maximumMethodProbability > 0;
        }

        private double[] computeMethodProbabilities() {
            double[] result = new double[concretes.size()];
            for (int i = 0; i < typesToConcretes.size(); i++) {
                int concrete = typesToConcretes.get(i);
                double probability = ptypes.get(i).getProbability();
                result[concrete] += probability;
            }
            return result;
        }

        private double maximumMethodProbability() {
            double max = 0;
            for (int i = 0; i < methodProbabilities.length; i++) {
                max = Math.max(max, methodProbabilities[i]);
            }
            return max;
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
        public Inlineable inlineableElementAt(int index) {
            assert index >= 0 && index < concretes.size();
            return inlineableElements[index];
        }

        @Override
        public double probabilityAt(int index) {
            return methodProbabilities[index];
        }

        @Override
        public double relevanceAt(int index) {
            return probabilityAt(index) / maximumMethodProbability;
        }

        @Override
        public void setInlinableElement(int index, Inlineable inlineableElement) {
            assert index >= 0 && index < concretes.size();
            inlineableElements[index] = inlineableElement;
        }

        @Override
        public void inline(Providers providers, Assumptions assumptions) {
            if (hasSingleMethod()) {
                inlineSingleMethod(graph(), providers.getMetaAccess(), assumptions);
            } else {
                inlineMultipleMethods(graph(), providers, assumptions);
            }
        }

        public boolean shouldInline() {
            for (ResolvedJavaMethod method : concretes) {
                if (method.shouldBeInlined()) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasSingleMethod() {
            return concretes.size() == 1 && !shouldFallbackToInvoke();
        }

        private boolean shouldFallbackToInvoke() {
            return notRecordedTypeProbability > 0;
        }

        private void inlineMultipleMethods(StructuredGraph graph, Providers providers, Assumptions assumptions) {
            int numberOfMethods = concretes.size();
            FixedNode continuation = invoke.next();

            ValueNode originalReceiver = ((MethodCallTargetNode) invoke.callTarget()).receiver();
            // setup merge and phi nodes for results and exceptions
            MergeNode returnMerge = graph.add(new MergeNode());
            returnMerge.setStateAfter(invoke.stateAfter());

            PhiNode returnValuePhi = null;
            if (invoke.asNode().getKind() != Kind.Void) {
                returnValuePhi = graph.addWithoutUnique(new ValuePhiNode(invoke.asNode().stamp().unrestricted(), returnMerge));
            }

            MergeNode exceptionMerge = null;
            PhiNode exceptionObjectPhi = null;
            if (invoke instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
                ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) invokeWithException.exceptionEdge();

                exceptionMerge = graph.add(new MergeNode());

                FixedNode exceptionSux = exceptionEdge.next();
                graph.addBeforeFixed(exceptionSux, exceptionMerge);
                exceptionObjectPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(Kind.Object), exceptionMerge));
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
            boolean methodDispatch = createDispatchOnTypeBeforeInvoke(graph, successors, false, providers.getMetaAccess());

            assert invoke.next() == continuation;
            invoke.setNext(null);
            returnMerge.setNext(continuation);
            invoke.asNode().replaceAtUsages(returnValuePhi);
            invoke.asNode().replaceAndDelete(null);

            ArrayList<GuardedValueNode> replacementNodes = new ArrayList<>();

            // do the actual inlining for every invoke
            for (int i = 0; i < numberOfMethods; i++) {
                BeginNode node = successors[i];
                Invoke invokeForInlining = (Invoke) node.next();

                ResolvedJavaType commonType;
                if (methodDispatch) {
                    commonType = concretes.get(i).getDeclaringClass();
                } else {
                    commonType = getLeastCommonType(i);
                }

                ValueNode receiver = ((MethodCallTargetNode) invokeForInlining.callTarget()).receiver();
                boolean exact = (getTypeCount(i) == 1 && !methodDispatch);
                GuardedValueNode anchoredReceiver = createAnchoredReceiver(graph, node, commonType, receiver, exact);
                invokeForInlining.callTarget().replaceFirstInput(receiver, anchoredReceiver);

                inline(invokeForInlining, methodAt(i), inlineableElementAt(i), assumptions, false);

                replacementNodes.add(anchoredReceiver);
            }
            if (shouldFallbackToInvoke()) {
                replacementNodes.add(null);
            }

            if (OptTailDuplication.getValue()) {
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
                    PhaseContext phaseContext = new PhaseContext(providers, assumptions);
                    CanonicalizerPhase canonicalizer = new CanonicalizerPhase(!ImmutableCode.getValue());
                    TailDuplicationPhase.tailDuplicate(returnMerge, TailDuplicationPhase.TRUE_DECISION, replacementNodes, phaseContext, canonicalizer);
                }
            }
        }

        private int getTypeCount(int concreteMethodIndex) {
            int count = 0;
            for (int i = 0; i < typesToConcretes.size(); i++) {
                if (typesToConcretes.get(i) == concreteMethodIndex) {
                    count++;
                }
            }
            return count;
        }

        private ResolvedJavaType getLeastCommonType(int concreteMethodIndex) {
            ResolvedJavaType commonType = null;
            for (int i = 0; i < typesToConcretes.size(); i++) {
                if (typesToConcretes.get(i) == concreteMethodIndex) {
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

        private void inlineSingleMethod(StructuredGraph graph, MetaAccessProvider metaAccess, Assumptions assumptions) {
            assert concretes.size() == 1 && inlineableElements.length == 1 && ptypes.size() > 1 && !shouldFallbackToInvoke() && notRecordedTypeProbability == 0;

            BeginNode calleeEntryNode = graph.add(new BeginNode());

            BeginNode unknownTypeSux = createUnknownTypeSuccessor(graph);
            BeginNode[] successors = new BeginNode[]{calleeEntryNode, unknownTypeSux};
            createDispatchOnTypeBeforeInvoke(graph, successors, false, metaAccess);

            calleeEntryNode.setNext(invoke.asNode());

            inline(invoke, methodAt(0), inlineableElementAt(0), assumptions, false);
        }

        private boolean createDispatchOnTypeBeforeInvoke(StructuredGraph graph, BeginNode[] successors, boolean invokeIsOnlySuccessor, MetaAccessProvider metaAccess) {
            assert ptypes.size() >= 1;
            ValueNode nonNullReceiver = nonNullReceiver(invoke);
            Kind hubKind = ((MethodCallTargetNode) invoke.callTarget()).targetMethod().getDeclaringClass().getEncoding(Representation.ObjectHub).getKind();
            LoadHubNode hub = graph.unique(new LoadHubNode(nonNullReceiver, hubKind));

            if (!invokeIsOnlySuccessor && chooseMethodDispatch()) {
                assert successors.length == concretes.size() + 1;
                assert concretes.size() > 0;
                Debug.log("Method check cascade with %d methods", concretes.size());

                ValueNode[] constantMethods = new ValueNode[concretes.size()];
                double[] probability = new double[concretes.size()];
                for (int i = 0; i < concretes.size(); ++i) {
                    ResolvedJavaMethod firstMethod = concretes.get(i);
                    Constant firstMethodConstant = firstMethod.getEncoding();

                    ValueNode firstMethodConstantNode = ConstantNode.forConstant(firstMethodConstant, metaAccess, graph);
                    constantMethods[i] = firstMethodConstantNode;
                    double concretesProbability = concretesProbabilities.get(i);
                    assert concretesProbability >= 0.0;
                    probability[i] = concretesProbability;
                    if (i > 0) {
                        double prevProbability = probability[i - 1];
                        if (prevProbability == 1.0) {
                            probability[i] = 1.0;
                        } else {
                            probability[i] = Math.min(1.0, Math.max(0.0, probability[i] / (1.0 - prevProbability)));
                        }
                    }
                }

                FixedNode lastSucc = successors[concretes.size()];
                for (int i = concretes.size() - 1; i >= 0; --i) {
                    LoadMethodNode method = graph.add(new LoadMethodNode(concretes.get(i), hub, constantMethods[i].getKind()));
                    CompareNode methodCheck = CompareNode.createCompareNode(graph, Condition.EQ, method, constantMethods[i]);
                    IfNode ifNode = graph.add(new IfNode(methodCheck, successors[i], lastSucc, probability[i]));
                    method.setNext(ifNode);
                    lastSucc = method;
                }

                FixedWithNextNode pred = (FixedWithNextNode) invoke.asNode().predecessor();
                pred.setNext(lastSucc);
                return true;
            } else {
                Debug.log("Type switch with %d types", concretes.size());
            }

            ResolvedJavaType[] keys = new ResolvedJavaType[ptypes.size()];
            double[] keyProbabilities = new double[ptypes.size() + 1];
            int[] keySuccessors = new int[ptypes.size() + 1];
            for (int i = 0; i < ptypes.size(); i++) {
                keys[i] = ptypes.get(i).getType();
                keyProbabilities[i] = ptypes.get(i).getProbability();
                keySuccessors[i] = invokeIsOnlySuccessor ? 0 : typesToConcretes.get(i);
                assert keySuccessors[i] < successors.length - 1 : "last successor is the unknownTypeSux";
            }
            keyProbabilities[keyProbabilities.length - 1] = notRecordedTypeProbability;
            keySuccessors[keySuccessors.length - 1] = successors.length - 1;

            TypeSwitchNode typeSwitch = graph.add(new TypeSwitchNode(hub, successors, keys, keyProbabilities, keySuccessors));
            FixedWithNextNode pred = (FixedWithNextNode) invoke.asNode().predecessor();
            pred.setNext(typeSwitch);
            return false;
        }

        private boolean chooseMethodDispatch() {
            for (ResolvedJavaMethod concrete : concretes) {
                if (!concrete.isInVirtualMethodTable()) {
                    return false;
                }
            }

            if (concretes.size() == 1 && this.notRecordedTypeProbability > 0) {
                // Always chose method dispatch if there is a single concrete method and the call
                // site is megamorphic.
                return true;
            }

            if (concretes.size() == ptypes.size()) {
                // Always prefer types over methods if the number of types is smaller than the
                // number of methods.
                return false;
            }

            return chooseMethodDispatchCostBased();
        }

        private boolean chooseMethodDispatchCostBased() {
            double remainder = 1.0 - this.notRecordedTypeProbability;
            double costEstimateMethodDispatch = remainder;
            for (int i = 0; i < concretes.size(); ++i) {
                if (i != 0) {
                    costEstimateMethodDispatch += remainder;
                }
                remainder -= concretesProbabilities.get(i);
            }

            double costEstimateTypeDispatch = 0.0;
            remainder = 1.0;
            for (int i = 0; i < ptypes.size(); ++i) {
                if (i != 0) {
                    costEstimateTypeDispatch += remainder;
                }
                remainder -= ptypes.get(i).getProbability();
            }
            costEstimateTypeDispatch += notRecordedTypeProbability;
            return costEstimateMethodDispatch < costEstimateTypeDispatch;
        }

        private static BeginNode createInvocationBlock(StructuredGraph graph, Invoke invoke, MergeNode returnMerge, PhiNode returnValuePhi, MergeNode exceptionMerge, PhiNode exceptionObjectPhi,
                        boolean useForInlining) {
            Invoke duplicatedInvoke = duplicateInvokeForInlining(graph, invoke, exceptionMerge, exceptionObjectPhi, useForInlining);
            BeginNode calleeEntryNode = graph.add(new BeginNode());
            calleeEntryNode.setNext(duplicatedInvoke.asNode());

            AbstractEndNode endNode = graph.add(new EndNode());
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

            Kind kind = invoke.asNode().getKind();
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

                AbstractEndNode endNode = graph.add(new EndNode());
                newExceptionEdge.setNext(endNode);
                exceptionMerge.addForwardEnd(endNode);
                exceptionObjectPhi.addInput(newExceptionEdge);

                ((InvokeWithExceptionNode) result).setExceptionEdge(newExceptionEdge);
            }
            return result;
        }

        @Override
        public void tryToDevirtualizeInvoke(MetaAccessProvider metaAccess, Assumptions assumptions) {
            if (hasSingleMethod()) {
                devirtualizeWithTypeSwitch(graph(), InvokeKind.Special, concretes.get(0), metaAccess);
            } else {
                tryToDevirtualizeMultipleMethods(graph(), metaAccess);
            }
        }

        private void tryToDevirtualizeMultipleMethods(StructuredGraph graph, MetaAccessProvider metaAccess) {
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
                        devirtualizeWithTypeSwitch(graph, InvokeKind.Virtual, leastCommonType.resolveMethod(targetMethod), metaAccess);
                    }
                }
            }
        }

        private void devirtualizeWithTypeSwitch(StructuredGraph graph, InvokeKind kind, ResolvedJavaMethod target, MetaAccessProvider metaAccess) {
            BeginNode invocationEntry = graph.add(new BeginNode());
            BeginNode unknownTypeSux = createUnknownTypeSuccessor(graph);
            BeginNode[] successors = new BeginNode[]{invocationEntry, unknownTypeSux};
            createDispatchOnTypeBeforeInvoke(graph, successors, true, metaAccess);

            invocationEntry.setNext(invoke.asNode());
            ValueNode receiver = ((MethodCallTargetNode) invoke.callTarget()).receiver();
            GuardedValueNode anchoredReceiver = createAnchoredReceiver(graph, invocationEntry, target.getDeclaringClass(), receiver, false);
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
        public void inline(Providers providers, Assumptions assumptions) {
            assumptions.record(takenAssumption);
            super.inline(providers, assumptions);
        }

        @Override
        public void tryToDevirtualizeInvoke(MetaAccessProvider metaAccess, Assumptions assumptions) {
            assumptions.record(takenAssumption);
            replaceInvokeCallTarget(invoke, graph(), InvokeKind.Special, concrete);
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
        assert !Modifier.isAbstract(concrete.getModifiers());
        if (!checkTargetConditions(data, replacements, invoke, concrete, optimisticOpts)) {
            return null;
        }
        return new AssumptionInlineInfo(invoke, concrete, takenAssumption);
    }

    private static InlineInfo getExactInlineInfo(InliningData data, Invoke invoke, Replacements replacements, OptimisticOptimizations optimisticOpts, ResolvedJavaMethod targetMethod) {
        assert !Modifier.isAbstract(targetMethod.getModifiers());
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
            assert type.isArray() || !isAbstract(type.getModifiers());
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
                    assert type.getType().isArray() || !isAbstract(type.getType().getModifiers()) : type + " " + concrete;
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

    private static GuardedValueNode createAnchoredReceiver(StructuredGraph graph, GuardingNode anchor, ResolvedJavaType commonType, ValueNode receiver, boolean exact) {
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
        } else if (Modifier.isNative(method.getModifiers()) && (!Intrinsify.getValue() || !InliningUtil.canIntrinsify(replacements, method))) {
            return logNotInlinedMethodAndReturnFalse(invoke, data.inliningDepth(), method, "it is a non-intrinsic native method");
        } else if (Modifier.isAbstract(method.getModifiers())) {
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
        final NodeInputList<ValueNode> parameters = invoke.callTarget().arguments();
        FixedNode invokeNode = invoke.asNode();
        StructuredGraph graph = invokeNode.graph();
        assert inlineGraph.getGuardsStage().ordinal() >= graph.getGuardsStage().ordinal();
        assert !invokeNode.graph().isAfterFloatingReadPhase() : "inline isn't handled correctly after floating reads phase";

        Kind returnKind = invokeNode.getKind();

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
                MonitorExitNode monitorExit = findPrecedingMonitorExit(unwindDuplicate);
                DeoptimizeNode deoptimizeNode = new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler);
                unwindDuplicate.replaceAndDelete(graph.add(deoptimizeNode));
                // move the deopt upwards if there is a monitor exit that tries to use the
                // "after exception" frame state
                // (because there is no "after exception" frame state!)
                if (monitorExit != null) {
                    if (monitorExit.stateAfter() != null && monitorExit.stateAfter().bci == BytecodeFrame.AFTER_EXCEPTION_BCI) {
                        FrameState monitorFrameState = monitorExit.stateAfter();
                        graph.removeFixed(monitorExit);
                        monitorFrameState.safeDelete();
                    }
                }
            }
        }

        if (stateAfter != null) {
            FrameState outerFrameState = null;
            int callerLockDepth = stateAfter.nestedLockDepth();
            for (FrameState original : inlineGraph.getNodes(FrameState.class)) {
                FrameState frameState = (FrameState) duplicates.get(original);
                if (frameState != null) {
                    assert frameState.bci != BytecodeFrame.BEFORE_BCI : frameState;
                    if (frameState.bci == BytecodeFrame.AFTER_BCI) {
                        frameState.replaceAndDelete(returnKind == Kind.Void ? stateAfter : stateAfter.duplicateModified(stateAfter.bci, stateAfter.rethrowException(), returnKind,
                                        frameState.stackAt(0)));
                    } else if (frameState.bci == BytecodeFrame.AFTER_EXCEPTION_BCI) {
                        if (frameState.isAlive()) {
                            assert stateAtExceptionEdge != null;
                            frameState.replaceAndDelete(stateAtExceptionEdge);
                        } else {
                            assert stateAtExceptionEdge == null;
                        }
                    } else {
                        // only handle the outermost frame states
                        if (frameState.outerFrameState() == null) {
                            assert frameState.bci == BytecodeFrame.INVALID_FRAMESTATE_BCI || frameState.method().equals(inlineGraph.method());
                            if (outerFrameState == null) {
                                outerFrameState = stateAfter.duplicateModified(invoke.bci(), stateAfter.rethrowException(), invokeNode.getKind());
                                outerFrameState.setDuringCall(true);
                            }
                            frameState.setOuterFrameState(outerFrameState);
                        }
                    }
                }
            }
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
