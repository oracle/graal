/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.inlining.walker;

import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import jdk.graal.compiler.phases.common.inlining.DirectedInliningRules;
import jdk.graal.compiler.phases.common.inlining.info.elem.InlineableGraph;
import jdk.graal.compiler.phases.common.inlining.policy.AbstractInliningPolicy;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.phases.graph.FixedNodeRelativeFrequencyCache;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * <p>
 * A {@link CallsiteHolder} whose graph has been copied already and thus can be modified without
 * affecting the original (usually cached) version.
 * </p>
 *
 * <p>
 * An instance of this class is derived from an {@link InlineableGraph InlineableGraph} and contains
 * a subset of the information there: just the {@link Invoke} nodes from it. Such nodes are
 * candidates for depth-first search of further inlining opportunities (thus the adjective
 * "explorable" given to this class)
 * </p>
 *
 * @see InliningData#moveForward()
 */
public final class CallsiteHolderExplorable extends CallsiteHolder {

    /**
     * Graph in which inlining may be performed at one or more of the callsites containined in
     * {@link #remainingInvokes}.
     */
    private final StructuredGraph graph;

    private final LinkedList<Invoke> remainingInvokes;
    private final double probability;
    private final double relevance;
    /**
     * BCI of the invoke in the compilation root that produced {@link #graph}. This is inherited by
     * every nested graph reached from this holder and is distinct from the BCI of invokes in
     * {@link #remainingInvokes}.
     */
    private final int rootInvokeBci;
    private final DirectedInliningRules.Callsite[] callsites;
    private final ResolvedJavaMethod ruleMethod;
    private final ResolvedJavaType receiverType;
    private final ResolvedJavaMethod concreteMethod;

    /**
     * @see #getFixedParams()
     */
    private final EconomicSet<ParameterNode> fixedParams;

    private final ToDoubleFunction<FixedNode> probabilities;
    private final ComputeInliningRelevance computeInliningRelevance;

    public CallsiteHolderExplorable(StructuredGraph graph, double probability, double relevance, BitSet freshlyInstantiatedArguments,
                    LinkedList<Invoke> invokes, int rootInvokeBci) {
        this(graph, probability, relevance, freshlyInstantiatedArguments, invokes, rootInvokeBci, DirectedInliningRules.EMPTY_CALLSITES,
                        graph.method(), null, graph.method());
    }

    public CallsiteHolderExplorable(StructuredGraph graph, double probability, double relevance, BitSet freshlyInstantiatedArguments,
                    LinkedList<Invoke> invokes, int rootInvokeBci, DirectedInliningRules.Callsite[] callsites) {
        this(graph, probability, relevance, freshlyInstantiatedArguments, invokes, rootInvokeBci, callsites,
                        graph.method(), null, graph.method());
    }

    public CallsiteHolderExplorable(StructuredGraph graph, double probability, double relevance, BitSet freshlyInstantiatedArguments,
                    LinkedList<Invoke> invokes, int rootInvokeBci, DirectedInliningRules.Callsite[] callsites,
                    ResolvedJavaMethod ruleMethod, ResolvedJavaType receiverType, ResolvedJavaMethod concreteMethod) {
        assert graph != null;
        this.graph = graph;
        this.probability = probability;
        this.relevance = relevance;
        this.rootInvokeBci = rootInvokeBci;
        this.callsites = callsites;
        this.ruleMethod = ruleMethod;
        this.receiverType = receiverType;
        this.concreteMethod = concreteMethod;
        this.fixedParams = fixedParamsAt(freshlyInstantiatedArguments);
        remainingInvokes = invokes == null ? new InliningIterator(graph).apply() : invokes;
        if (remainingInvokes.isEmpty()) {
            probabilities = null;
            computeInliningRelevance = null;
        } else {
            probabilities = new FixedNodeRelativeFrequencyCache();
            computeInliningRelevance = new ComputeInliningRelevance(graph, probabilities);
            computeProbabilities();
        }
        assert repOK();
    }

    /**
     * @see #getFixedParams()
     */
    private EconomicSet<ParameterNode> fixedParamsAt(BitSet freshlyInstantiatedArguments) {
        if (freshlyInstantiatedArguments == null || freshlyInstantiatedArguments.isEmpty()) {
            return EconomicSet.create(Equivalence.IDENTITY);
        }
        EconomicSet<ParameterNode> result = EconomicSet.create(Equivalence.IDENTITY);
        for (ParameterNode p : graph.getNodes(ParameterNode.TYPE)) {
            if (freshlyInstantiatedArguments.get(p.index())) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * <p>
     * Parameters for which the callsite targeting {@link #graph()} provides "fixed" arguments. That
     * callsite isn't referenced by this instance. Instead, it belongs to the graph of the caller of
     * this {@link CallsiteHolderExplorable}
     * </p>
     *
     * <p>
     * Constant arguments don't contribute to fixed-params: those params have been removed already,
     * see {@link InlineableGraph}.
     * </p>
     *
     * <p>
     * Instead, fixed-params are those receiving freshly instantiated arguments (possibly
     * instantiated several levels up in the call-hierarchy)
     * </p>
     */
    public EconomicSet<ParameterNode> getFixedParams() {
        return fixedParams;
    }

    /**
     * Returns the compilation-root invoke BCI for directed rule matching. For the root graph itself,
     * this is the directed-inlining sentinel until a concrete root invoke is selected.
     */
    public int rootInvokeBci() {
        return rootInvokeBci;
    }

    /**
     * Returns the complete caller-edge path that produced this graph.
     */
    public DirectedInliningRules.Callsite[] callsites() {
        return callsites;
    }

    public ResolvedJavaMethod callerMethod(Invoke invoke) {
        return callsites.length == 0 ? DirectedInliningRules.callerMethod(invoke, method()) : method();
    }

    public int rootInvokeBci(Invoke invoke) {
        return DirectedInliningRules.rootInvokeBci(invoke, rootInvokeBci);
    }

    public DirectedInliningRules.Callsite[] callsitePath(Invoke invoke) {
        if (callsites.length == 0) {
            return DirectedInliningRules.rootGraphCallsites(invoke, callerMethod(invoke));
        }
        return DirectedInliningRules.append(callsites, currentCallsite(invoke.bci()));
    }

    public DirectedInliningRules.Callsite currentCallsite(int bci) {
        return new DirectedInliningRules.Callsite(ruleMethod, receiverType, concreteMethod, bci);
    }

    public boolean repOK() {
        for (Invoke invoke : remainingInvokes) {
            if (!invoke.asNode().isAlive() || !containsInvoke(invoke)) {
                assert false;
                return false;
            }
            if (!allArgsNonNull(invoke)) {
                assert false;
                return false;
            }
        }
        for (ParameterNode fixedParam : fixedParams) {
            if (!containsParam(fixedParam)) {
                assert false;
                return false;
            }
        }
        return true;
    }

    @Override
    public ResolvedJavaMethod method() {
        return graph == null ? null : graph.method();
    }

    @Override
    public boolean hasRemainingInvokes() {
        return !remainingInvokes.isEmpty();
    }

    public boolean hasRemainingInvokes(Predicate<Invoke> predicate) {
        for (Invoke invoke : remainingInvokes) {
            if (predicate.test(invoke)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public StructuredGraph graph() {
        return graph;
    }

    public Invoke popInvoke() {
        return remainingInvokes.removeFirst();
    }

    public Invoke popInvoke(Predicate<Invoke> predicate) {
        Iterator<Invoke> iterator = remainingInvokes.iterator();
        while (iterator.hasNext()) {
            Invoke invoke = iterator.next();
            if (predicate.test(invoke)) {
                iterator.remove();
                return invoke;
            }
        }
        return null;
    }

    public void pushInvoke(Invoke invoke) {
        remainingInvokes.push(invoke);
    }

    public static boolean allArgsNonNull(Invoke invoke) {
        for (ValueNode arg : invoke.callTarget().arguments()) {
            if (arg == null) {
                assert false;
                return false;
            }
        }
        return true;
    }

    public boolean containsInvoke(Invoke invoke) {
        for (Invoke i : graph().getInvokes()) {
            if (i == invoke) {
                return true;
            }
        }
        return false;
    }

    public boolean containsParam(ParameterNode param) {
        for (ParameterNode p : graph.getNodes(ParameterNode.TYPE)) {
            if (p == param) {
                return true;
            }
        }
        return false;
    }

    public void computeProbabilities() {
        computeInliningRelevance.compute();
    }

    public double invokeProbability(Invoke invoke) {
        return probability * probabilities.applyAsDouble(invoke.asFixedNode());
    }

    public double invokeRelevance(Invoke invoke) {
        return Math.min(AbstractInliningPolicy.CapInheritedRelevance, relevance) * computeInliningRelevance.getRelevance(invoke);
    }

    @Override
    public String toString() {
        return (graph != null ? method().format("%H.%n(%p)") : "<null method>") + remainingInvokes;
    }
}
