/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis.tesa;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.hosted.analysis.tesa.effect.TesaEffect;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;

/**
 * The base class for any Transitive Effect Summary Analysis.
 *
 * @param <T> the effect tracked by the given analysis.
 * @see TesaEngine
 */
public abstract class AbstractTesa<T extends TesaEffect<T>> {
    /**
     * Holds the current state for each method.
     */
    protected final Map<AnalysisMethod, T> methodToState = new ConcurrentHashMap<>();

    /**
     * The number of methods that can be optimized by this analysis, used for statistics.
     */
    private final AtomicInteger optimizableMethodsCounter = new AtomicInteger();

    /**
     * The number of invokes that can be optimized by this analysis, used for statistics.
     */
    private final AtomicInteger optimizableInvokesCounter = new AtomicInteger();

    /**
     * Timer for the core fixed-point loop.
     */
    private final Timer fixedPointLoopTimer = TimerCollection.singleton().createTimer(ClassUtil.getUnqualifiedName(getClass()) + "FixedPointLoop");

    /**
     * Computes the initial state for the given {@code method}.
     * <p>
     * While the algorithm to compute the initial state can be arbitrary, it is advisable that it
     * contains at most a single pass over the {@code graph} and does not rely on implementation
     * details of the Graal IR. Ideally, only high-level APIs and marker interfaces should be used
     * to make sure that the code in {@code computeInitialState} does not became incorrect when new
     * Graal IR nodes are introduced. See how the {@link KilledLocationTesa#computeInitialState}
     * uses {@link MemoryKill#isMemoryKill(Node)} as an example.
     */
    protected abstract T computeInitialState(AnalysisMethod method, StructuredGraph graph);

    /**
     * Computes and saves the initial state of this method to be used later by the analysis.
     */
    void initializeStateForMethod(AnalysisMethod method, StructuredGraph graph) {
        T previous = methodToState.put(method, computeInitialState(method, graph));
        AnalysisError.guarantee(previous == null, "A state for method %s has already been initialized.", method);
    }

    /**
     * By default, we are conservative about invokes with unknown target methods (as we cannot
     * compute their transitive effects). Subclasses may extend the check to reject more nodes, but
     * they should always call the check in this class as well unless they can somehow prove that
     * the unknown invoke is not a problem in their domain.
     */
    protected boolean isSupportedNode(Node node) {
        return !(node instanceof Invoke invoke && invoke.getTargetMethod() == null);
    }

    /**
     * By default, we skip the {@link StartNode} which has a {@code killedLocation} Any,
     * {@link Invoke}s, because their transitive effect is computed by the analyses, and
     * {@link ExceptionObjectNode}s, because they can only be reached from places covered by the
     * analyses. Subclasses may change this check based on their domain.
     */
    protected boolean shouldSkipNode(Node node) {
        return node instanceof StartNode || node instanceof Invoke || node instanceof ExceptionObjectNode;
    }

    /**
     * Core loop of any TESA. Runs a fixed-point analysis propagating information about methods in
     * reverse direction (callees to callers).
     * <p>
     * We currently initialize the worklist with <i>all</i> methods. Intuitively, we could start
     * only with leaves (e.g. methods that do not call any other methods). Unfortunately, we cannot
     * guarantee that all methods would be reached from the leaves, because there may be a cycle in
     * the graph and such a cycle would have no leaf in it (as each method calls another) and it
     * does not have to be reachable from any leaf either.
     * <p>
     * We could avoid having to run a fixed-point algorithm if we would perform
     * Strongly-Connected-Component (SCC) condensation first: We could identify all SCCs and merge
     * each of them into a single node. This would be sound and would lose no precision, because all
     * the nodes in a SCC will end up having the same state anyway as each node in the SCC will
     * propagate its state to the rest of the SCC, and we never remove anything from the state (go
     * down the lattice), i.e., we have no "kill sets" in the data-flow terminology. After SCC
     * condensation, we would have a directed acyclic graph and could use topological ordering to
     * perform a linear sweep over the graph. However, since the overhead of the fixed-point
     * algorithm is reasonably low, we stick with it for now, as it is simpler.
     */
    void runFixedPointLoop(TesaEngine engine, BigBang bb) {
        try (var _ = fixedPointLoopTimer.start()) {
            var scheduledMethods = TesaReverseCallGraph.getAllMethods(bb).collect(Collectors.toCollection(HashSet::new));
            var worklist = new ArrayDeque<>(scheduledMethods);
            /*
             * Reaching more than a quadratic number of iterations suggests that there is an issue
             * with the analysis (the merge is probably not monotonic).
             */
            long iterations = 0;
            long limit = ((long) scheduledMethods.size()) * scheduledMethods.size();
            while (!worklist.isEmpty()) {
                if (iterations >= limit) {
                    if (TesaEngine.Options.TesaThrowOnNonTermination.getValue()) {
                        throw AnalysisError.shouldNotReachHere(ClassUtil.getUnqualifiedName(getClass()) + ": fixed-point loop did not terminate after " + iterations + " iterations.");
                    } else {
                        /*
                         * Do not fail in production builds. Clearing methodToState results in
                         * treating each method as unoptimizable. Clearing the map is important
                         * because stopping the algorithm early will most likely make its results
                         * unsound.
                         */
                        methodToState.clear();
                        break;
                    }
                }
                iterations++;
                var currentMethod = worklist.removeFirst();
                scheduledMethods.remove(currentMethod);
                var currentState = getState(currentMethod);
                Set<AnalysisMethod> callers = engine.getCallGraph().getCallers(currentMethod);
                if (callers != null) {
                    for (var caller : callers) {
                        var callerState = getState(caller);
                        var merged = callerState.combineEffects(currentState);
                        if (!merged.equals(callerState)) {
                            methodToState.put(caller, merged);
                            if (scheduledMethods.add(caller)) {
                                worklist.add(caller);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the element corresponding to any effect (worst optimization potential).
     */
    protected abstract T anyEffect();

    /**
     * Get the element corresponding to no effect (best optimization potential).
     */
    protected abstract T noEffect();

    /**
     * Get the state for the given method.
     */
    public T getState(AnalysisMethod currentMethod) {
        T state = methodToState.get(currentMethod.getMultiMethod(MultiMethod.ORIGINAL_METHOD));
        if (state == null) {
            /* Can occur, e.g., for multimethods or in layers. */
            return anyEffect();
        }
        return state;
    }

    /**
     * Gets the state for the given invoke. For direct invokes, use the state of the target method.
     * For indirect invokes, use the {@link TesaEffect#combineEffects} over the states of all target
     * methods.
     */
    public T getState(TesaEngine engine, Invoke invoke) {
        var targetMethod = ((HostedMethod) invoke.callTarget().targetMethod());
        if (invoke.getInvokeKind().isDirect()) {
            return getState(targetMethod.wrapped);
        } else {
            T cummulativeState = noEffect();
            for (AnalysisMethod callee : engine.getCallGraph().getCallees(invoke, targetMethod)) {
                var targetState = getState(callee);
                cummulativeState = cummulativeState.combineEffects(targetState);
                if (cummulativeState.isAnyEffect()) {
                    break;
                }
            }
            return cummulativeState;
        }
    }

    /**
     * Apply the results of this analysis on the given compilation {@code graph}.
     */
    public void applyResults(TesaEngine engine, HostedUniverse universe, HostedMethod method, StructuredGraph graph) {
        var state = getState(method.wrapped);
        if (hasOptimizationPotential(state)) {
            optimizableMethodsCounter.incrementAndGet();
        }
        for (Node node : graph.getNodes()) {
            if (node instanceof Invoke invoke && canOptimizedInvoke(invoke) && isSupportedNode(node)) {
                var targetState = getState(engine, invoke);
                if (hasOptimizationPotential(targetState)) {
                    optimizableInvokesCounter.incrementAndGet();
                    optimizeInvoke(universe, graph, invoke, targetState);
                }
            }
        }
    }

    /**
     * Hook for subclasses to check if the given {@code invoke} can be optimized by the given
     * analysis. By default, try to optimize all invokes.
     */
    @SuppressWarnings("unused")
    protected boolean canOptimizedInvoke(Invoke invoke) {
        return true;
    }

    /**
     * Hook for subclasses to check if the given {@code state} can lead to optimizations. By
     * default, anything but the top element of the lattice has optimization potential.
     */
    protected boolean hasOptimizationPotential(T state) {
        return !state.isAnyEffect();
    }

    /**
     * Optimize the given {@code invoke} based on the computed {@code targetState}.
     * <p>
     * By default, an empty method, so that analyses that cannot directly optimize the graph do not
     * have to implement it.
     */
    @SuppressWarnings("unused")
    protected void optimizeInvoke(HostedUniverse universe, StructuredGraph graph, Invoke invoke, T targetState) {

    }

    /**
     * @see #optimizableInvokesCounter
     */
    public int getOptimizableInvokes() {
        return optimizableInvokesCounter.get();
    }

    /**
     * @see #optimizableMethodsCounter
     */
    public int getOptimizableMethods() {
        return optimizableMethodsCounter.get();
    }

    /**
     * @see #fixedPointLoopTimer
     */
    public double getFixedPointLoopTimeMs() {
        return fixedPointLoopTimer.getTotalTimeMs();
    }
}
