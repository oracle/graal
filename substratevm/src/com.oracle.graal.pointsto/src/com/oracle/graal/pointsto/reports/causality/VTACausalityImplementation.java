/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.graal.pointsto.reports.causality;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.collections.Pair;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualParameterTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.AllInstantiatedTypeFlow;
import com.oracle.graal.pointsto.flow.ConstantTypeFlow;
import com.oracle.graal.pointsto.flow.FilterTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.NullCheckTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.reports.causality.facts.Fact;
import com.oracle.graal.pointsto.reports.causality.facts.Facts;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;

final class VTACausalityImplementation extends CausalityImplementationBase<VTACausalityImplementation.ThreadContext> {
    private final ConcurrentHashMap<Pair<TypeFlow<?>, TypeFlow<?>>, Boolean> interflows = new ConcurrentHashMap<>();

    /**
     * Saves for each virtual invocation the receiver typeflow before it may have been replaced
     * during saturation.
     */
    private final ConcurrentHashMap<AbstractVirtualInvokeTypeFlow, TypeFlow<?>> originalInvokeReceivers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Pair<Fact, TypeFlow<?>>, HashSet<AnalysisType>> flowingFromHeap = new ConcurrentHashMap<>();

    public static final class ThreadContext extends CausalityImplementationBase.ThreadContext {
        public int currentlySaturatingDepth; // Inhibits the registration of new typeflow edges

        public final class SaturationHappeningToken implements Causality.NonThrowingAutoCloseable {
            SaturationHappeningToken() {
                currentlySaturatingDepth++;
            }

            @Override
            public void close() {
                currentlySaturatingDepth--;
                if (currentlySaturatingDepth < 0) {
                    throw new RuntimeException();
                }
            }
        }
    }

    VTACausalityImplementation() {
        super(ThreadContext::new);
    }

    @Override
    public void registerTypeFlowEdge(TypeFlow<?> from, TypeFlow<?> to) {
        if (from == to) {
            return;
        }

        if (from instanceof AllInstantiatedTypeFlow && getContext().currentlySaturatingDepth > 0) {
            return;
        }

        assert getContext().currentlySaturatingDepth == 0 || to.isContextInsensitive() || from instanceof ActualReturnTypeFlow && to instanceof ActualReturnTypeFlow ||
                        to instanceof ActualParameterTypeFlow;
        interflows.put(Pair.create(from, to), Boolean.TRUE);
    }

    @Override
    public Causality.NonThrowingAutoCloseable setSaturationHappening() {
        return getContext().new SaturationHappeningToken();
    }

    @Override
    public void registerTypeEntering(PointsToAnalysis bb, Fact cause, TypeFlow<?> destination, AnalysisType type) {
        flowingFromHeap.computeIfAbsent(Pair.create(cause, destination), p -> new HashSet<>()).add(type);
    }

    @Override
    public void addVirtualInvokeTypeFlow(AbstractVirtualInvokeTypeFlow invocation) {
        originalInvokeReceivers.put(invocation, invocation.getReceiver());
    }

    private void forEachTypeflow(Consumer<TypeFlow<?>> callback) {
        for (var e : interflows.keySet()) {
            callback.accept(e.getLeft());
            callback.accept(e.getRight());
        }
    }

    @Override
    protected void forEachEvent(Consumer<Fact> callback) {
        super.forEachEvent(callback);

        flowingFromHeap.keySet().stream().map(Pair::getLeft).forEach(callback);

        // TODO: Unsure about this - whether it is necessary and whether it is correct/complete
        originalInvokeReceivers.keySet().stream().map(InvokeTypeFlow::getTargetMethod).flatMap(targetMethod -> targetMethod.collectMethodImplementations(false).stream())
                        .map(Facts.MethodImplementationInvoked::create).forEach(callback);

        forEachTypeflow(tf -> {
            if (tf != null) {
                Fact e = getContainingEvent(tf);
                if (e != null) {
                    callback.accept(e);
                }
            }
        });
    }

    private static BytecodePosition chopUnwindFrames(BytecodePosition p) {
        while (p != null && p.getBCI() == BytecodeFrame.UNWIND_BCI) {
            p = p.getCaller();
        }
        return p;
    }

    private static BytecodePosition takePlausibleFramesFromBottom(BytecodePosition p) {
        if (p == null) {
            return null;
        }

        BytecodePosition callerPos = takePlausibleFramesFromBottom(p.getCaller());

        if (callerPos != p.getCaller()) {
            return callerPos;
        }

        if (!((AnalysisMethod) p.getMethod()).isInlined()) {
            return callerPos != null ? callerPos : new BytecodePosition(null, p.getMethod(), p.getBCI());
        }

        return p;
    }

    private static BytecodePosition takePlausibleFramesFromTop(BytecodePosition p) {
        if (p == null) {
            return null;
        }

        if (!((AnalysisMethod) p.getMethod()).isInlined()) {
            return new BytecodePosition(null, p.getMethod(), p.getBCI());
        }

        BytecodePosition callerPos = takePlausibleFramesFromTop(p.getCaller());
        if (callerPos != p.getCaller()) {
            return new BytecodePosition(callerPos, p.getMethod(), p.getBCI());
        }

        return p;
    }

    private static Fact getContainingEvent(TypeFlow<?> f) {
        if (f.getSource() instanceof BytecodePosition pos) {
            /*
             * For some reason the BytecodePosition assigned to a TypeFlow isn't always what you
             * would expect from browsing the code. We query AnalysisMethod.isInlined() in order to
             * improve implausible sources. Incorrect sources that are not corrected will lead to
             * underapproximation in the Causality Graph. TODO: Fix the underlying problem
             */
            if (f instanceof FilterTypeFlow || f instanceof NullCheckTypeFlow) {
                pos = takePlausibleFramesFromBottom(chopUnwindFrames(pos));
            } else if (f instanceof ConstantTypeFlow) {
                pos = takePlausibleFramesFromTop(chopUnwindFrames(pos));
            }
            return Facts.InlinedMethodCode.create(pos);
        } else {
            return null;
        }
    }

    private static void addSubtypeImplementations(PointsToAnalysis bb, Map<AnalysisMethod, TypeState> result, AnalysisMethod base, AnalysisType type) {
        AnalysisMethod override = type.resolveConcreteMethod(base);

        if (override != null && override.isImplementationInvoked()) {
            result.compute(override, (m, existingTypes) -> {
                if (existingTypes == null) {
                    existingTypes = TypeState.forEmpty();
                }
                return TypeState.forUnion(bb, existingTypes, TypeState.forExactType(bb, type, false));
            });
        }

        for (AnalysisType subType : type.getSubTypes()) {
            if (subType == type) {
                continue;
            }
            addSubtypeImplementations(bb, result, base, subType);
        }
    }

    /**
     * In order to save calls to {@link AnalysisType#resolveConcreteMethod(ResolvedJavaMethod)}, we
     * precompute the mapping Type -> Implementation.
     */
    private static Map<AnalysisMethod, TypeState> collectImplementationWithTypes(PointsToAnalysis bb, AnalysisMethod baseMethod) {
        Map<AnalysisMethod, TypeState> result = new HashMap<>();
        addSubtypeImplementations(bb, result, baseMethod, baseMethod.getDeclaringClass());
        return result;
    }

    @Override
    protected Graph createCausalityGraph(PointsToAnalysis bb) {
        Graph g = super.createCausalityGraph(bb);

        HashMap<TypeFlow<?>, Graph.RealFlowNode> flowMapping = new HashMap<>();

        Function<TypeFlow<?>, Graph.RealFlowNode> flowMapper = flow -> {
            if (flow.getState().isPrimitive() || flow.getState().typesCount() == 0 && !flow.isSaturated()) {
                return null;
            }

            return flowMapping.computeIfAbsent(flow, f -> {
                Fact reason = getContainingEvent(f);
                if (reason != null && reason.unused()) {
                    return null;
                }
                return Graph.RealFlowNode.create(bb, f, reason);
            });
        };

        Map<AnalysisMethod, Pair<Set<AnalysisMethod>, TypeState>> virtualInvokes = new HashMap<>();

        Map<AnalysisMethod, Map<AnalysisMethod, TypeState>> implementationsAndTheirTypes = new HashMap<>();
        Map<AnalysisMethod, Graph.FlowNode> targetMethodReceivers = new HashMap<>();
        for (var e : originalInvokeReceivers.keySet()) {
            implementationsAndTheirTypes.computeIfAbsent(e.getTargetMethod(), targetMethod -> collectImplementationWithTypes(bb, targetMethod));
            targetMethodReceivers.computeIfAbsent(e.getTargetMethod(),
                            targetMethod -> new Graph.FlowNode("Receiver node for " + targetMethod.getQualifiedName(), null, targetMethod.getDeclaringClass().instantiatedTypes.getState()));
        }

        for (var e : originalInvokeReceivers.entrySet()) {
            var invokeFlow = e.getKey();
            PointsToAnalysisMethod targetMethod = invokeFlow.getTargetMethod();
            var receiver = e.getValue();
            var accumulatedReceiver = targetMethodReceivers.get(targetMethod);

            TypeState receiverState = bb.getAllInstantiatedTypeFlow().getState();
            if (receiver != null && !receiver.isSaturated()) {
                receiverState = receiver.filter(bb, receiverState);
            }

            for (var implementationWithTypes : implementationsAndTheirTypes.get(targetMethod).entrySet()) {
                TypeState and = TypeState.forIntersection(bb, receiverState, implementationWithTypes.getValue());
                if (and.typesCount() == 0) {
                    continue;
                }

                var calleeList = bb.getHostVM().getMultiMethodAnalysisPolicy().determineCallees(bb, PointsToAnalysis.assertPointsToAnalysisMethod(implementationWithTypes.getKey()), targetMethod,
                                e.getKey().getCallerMultiMethodKey(), e.getKey());
                for (PointsToAnalysisMethod callee : calleeList) {
                    assert callee.getTypeFlow().getMethod().equals(callee);

                    virtualInvokes.compute(callee, (m, pair) -> {
                        Set<AnalysisMethod> invokes;
                        TypeState targetReachingTypes;

                        if (pair == null) {
                            invokes = new HashSet<>();
                            targetReachingTypes = TypeState.forEmpty();
                        } else {
                            invokes = pair.getLeft();
                            targetReachingTypes = pair.getRight();
                        }

                        invokes.add(targetMethod);
                        return Pair.create(invokes, TypeState.forUnion(bb, targetReachingTypes, and));
                    });
                }
            }

            if (invokeFlow.isContextInsensitive()) {
                // Root invocation
                Graph.FlowNode rootCallFlow = new Graph.FlowNode(
                                "Root call to " + invokeFlow.getTargetMethod(),
                                Facts.RootMethodRegistration.create(invokeFlow.getTargetMethod()),
                                bb.getAllInstantiatedTypeFlow().getState());

                g.add(new Graph.FlowEdge(
                                flowMapper.apply(invokeFlow.getTargetMethod().getDeclaringClass().instantiatedTypes),
                                rootCallFlow));
                g.add(new Graph.FlowEdge(
                                rootCallFlow,
                                accumulatedReceiver));
            } else {
                assert receiver != null;
                Graph.FlowNode receiverNode = flowMapper.apply(receiver);
                if (receiverNode != null) {
                    g.add(new Graph.FlowEdge(
                                    receiverNode,
                                    accumulatedReceiver));
                }
            }
        }

        for (var e : virtualInvokes.entrySet()) {
            Fact reason = Facts.MethodImplementationInvoked.create(e.getKey());

            if (reason.unused()) {
                continue;
            }

            Graph.InvocationFlowNode invocationFlowNode = new Graph.InvocationFlowNode(reason, e.getValue().getRight());
            for (AnalysisMethod targetMethod : e.getValue().getLeft()) {
                g.add(new Graph.FlowEdge(targetMethodReceivers.get(targetMethod), invocationFlowNode));
            }
        }

        for (Pair<TypeFlow<?>, TypeFlow<?>> e : interflows.keySet()) {
            Graph.RealFlowNode left = null;

            if (e.getLeft() != null) {
                left = flowMapper.apply(e.getLeft());
                if (left == null) {
                    continue;
                }
            }

            Graph.RealFlowNode right = flowMapper.apply(e.getRight());
            if (right == null) {
                continue;
            }

            g.add(new Graph.FlowEdge(left, right));
        }

        for (AnalysisType t : bb.getAllInstantiatedTypes()) {
            TypeState state = TypeState.forExactType(bb, t, false);
            Graph.FlowNode vfn = new Graph.FlowNode("Virtual Flow Node for reaching " + t.toJavaName(), Facts.TypeInstantiated.create(t), state);
            g.add(new Graph.FlowEdge(null, vfn));

            t.forAllSuperTypes(t1 -> {
                g.add(new Graph.FlowEdge(vfn, flowMapper.apply(t1.instantiatedTypes)));
                g.add(new Graph.FlowEdge(vfn, flowMapper.apply(t1.instantiatedTypesNonNull)));
            });
        }

        for (Map.Entry<Pair<Fact, TypeFlow<?>>, HashSet<AnalysisType>> e : flowingFromHeap.entrySet()) {
            Graph.RealFlowNode fieldNode = flowMapper.apply(e.getKey().getRight());

            if (fieldNode == null) {
                continue;
            }

            for (AnalysisType analysisType : e.getValue()) {
                TypeState state = TypeState.forExactType(bb, analysisType, false);
                Graph.FlowNode intermediate = new Graph.FlowNode("Virtual Flow from Heap", e.getKey().getLeft(), state);
                g.add(new Graph.FlowEdge(null, intermediate));
                g.add(new Graph.FlowEdge(intermediate, fieldNode));
            }
        }

        this.interflows.clear();
        this.flowingFromHeap.clear();
        this.originalInvokeReceivers.clear();

        return g;
    }
}
