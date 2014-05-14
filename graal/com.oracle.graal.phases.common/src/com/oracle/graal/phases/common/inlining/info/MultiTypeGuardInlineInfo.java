/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.inlining.info;

import com.oracle.graal.api.code.Assumptions;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.CompareNode;
import com.oracle.graal.nodes.extended.LoadHubNode;
import com.oracle.graal.nodes.extended.LoadMethodNode;
import com.oracle.graal.nodes.java.ExceptionObjectNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.java.TypeSwitchNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.TailDuplicationPhase;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;

import java.util.ArrayList;
import java.util.List;

import static com.oracle.graal.compiler.common.GraalOptions.ImmutableCode;
import static com.oracle.graal.compiler.common.GraalOptions.OptTailDuplication;

import com.oracle.graal.phases.common.inlining.InliningUtil.Inlineable;
import com.oracle.graal.api.meta.JavaTypeProfile.ProfiledType;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

/**
 * Polymorphic inlining of m methods with n type checks (n &ge; m) in case that the profiling
 * information suggests a reasonable amount of different receiver types and different methods. If an
 * unknown type is encountered a deoptimization is triggered.
 */
public class MultiTypeGuardInlineInfo extends AbstractInlineInfo {

    private static final DebugMetric metricInliningTailDuplication = Debug.metric("InliningTailDuplication");

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
            GuardedValueNode anchoredReceiver = InliningUtil.createAnchoredReceiver(graph, node, commonType, receiver, exact);
            invokeForInlining.callTarget().replaceFirstInput(receiver, anchoredReceiver);

            inline(invokeForInlining, methodAt(i), inlineableElementAt(i), assumptions, false);

            replacementNodes.add(anchoredReceiver);
        }
        if (shouldFallbackToInvoke()) {
            replacementNodes.add(null);
        }

        if (OptTailDuplication.getValue()) {
            /*
             * We might want to perform tail duplication at the merge after a type switch, if there
             * are invokes that would benefit from the improvement in type information.
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
        ValueNode nonNullReceiver = InliningUtil.nonNullReceiver(invoke);
        Kind hubKind = ((MethodCallTargetNode) invoke.callTarget()).targetMethod().getDeclaringClass().getEncoding(ResolvedJavaType.Representation.ObjectHub).getKind();
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
        GuardedValueNode anchoredReceiver = InliningUtil.createAnchoredReceiver(graph, invocationEntry, target.getDeclaringClass(), receiver, false);
        invoke.callTarget().replaceFirstInput(receiver, anchoredReceiver);
        InliningUtil.replaceInvokeCallTarget(invoke, graph, kind, target);
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
