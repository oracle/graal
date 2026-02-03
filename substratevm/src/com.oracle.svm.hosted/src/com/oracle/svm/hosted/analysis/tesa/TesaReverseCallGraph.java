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

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.AbstractAnalysisEngine;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.nodes.Invoke;
import jdk.vm.ci.code.BytecodePosition;

/**
 * A helper containing the call-graph-related information used by {@link TesaEngine} and its
 * analyses.
 */
public final class TesaReverseCallGraph {

    /**
     * Timer used to measure the initial processing of the call graph.
     */
    private final Timer callGraphInitializationTimer = TimerCollection.singleton().createTimer("TesaCallGraphInitialization");

    /**
     * Reverse call edges (from callees to callers).
     */
    private final Map<AnalysisMethod, Set<AnalysisMethod>> callers = new ConcurrentHashMap<>();

    /**
     * A key to be used in {@link #invokeToCallees}.
     */
    public record InvokePosition(AnalysisMethod sourceMethod, int bci, AnalysisMethod targetMethod) {
    }

    /**
     * A mapping from {@link InvokePosition} to the corresponding set of callees as computed by the
     * {@link AbstractAnalysisEngine}. As we cannot always guarantee a unique mapping, e.g., due to
     * the artificial source positions discussed below, it is a best-effort delivery with collision
     * handling. The values can be either a {@link Collection} of {@link AnalysisMethod} or the
     * {@link #COLLISION} marker object when the mapping is not unique. In that case, we cannot
     * retrieve the more precise location-specific set of callees, but we can always fall back to
     * set of all implementations of the given target method, loosing precision without compromising
     * soundness.
     * <p>
     * Using a custom {@link InvokePosition} as opposed to {@link BytecodePosition} leads to
     * slightly fewer collisions, because the target method sometimes helps to disambiguate more
     * invokes, e.g. those that have artificial source positions created by
     * {@link AbstractAnalysisEngine#sourcePosition}.
     */
    private final Map<InvokePosition, Object> invokeToCallees = new ConcurrentHashMap<>();

    /**
     * Returns a stream of methods that should be analyzed by TESA, which are those that are
     * "implementation invoked" by the terminology of {@link AbstractAnalysisEngine}. i.e. they are
     * either root methods or they can be invoked from another reachable method. We do not use
     * {@link AnalysisMethod#isReachable} to skip methods that are always inlined, because such
     * methods exist only in compilation graphs of other methods.
     */
    public static Stream<AnalysisMethod> getAllMethods(BigBang bb) {
        return bb.getUniverse().getMethods().stream().filter(AnalysisMethod::isImplementationInvoked);
    }

    /**
     * Singleton collision object used to mark that the given {@link InvokePosition} is not unique.
     */
    private static final Object COLLISION = new Object();

    /**
     * Private to enforce using the {@link #create} method.
     */
    private TesaReverseCallGraph() {
    }

    /**
     * Create a new {@link TesaReverseCallGraph} instance with data populated by the results from
     * {@code bb}. We use a factory method for the creation of the call graph as the process is
     * quite complex to be placed in a constructor.
     * <p>
     * We could lean on the {@link AbstractAnalysisEngine} more and use
     * {@link AnalysisMethod#getCallers()} instead of computing and storing the call graph
     * ourselves. However, the callers for each method are not tracked by default. This has to be
     * enabled per-method via {@link PointsToAnalysisMethod#startTrackInvocations}, which would
     * increase the memory footprint of {@link PointsToAnalysis}.
     */
    public static TesaReverseCallGraph create(BigBang bb) {
        var callGraph = new TesaReverseCallGraph();
        try (var _ = callGraph.callGraphInitializationTimer.start()) {
            getAllMethods(bb).parallel().forEach(callGraph::addMethod);
            return callGraph;
        }
    }

    /**
     * Adds the {@code method} to the callgraph, including all the back edges from its callees.
     */
    private void addMethod(AnalysisMethod method) {
        for (InvokeInfo invoke : method.getInvokes()) {
            InvokePosition key = new InvokePosition(method, invoke.getPosition().getBCI(), invoke.getTargetMethod());
            Collection<AnalysisMethod> callees = invoke.getAllCallees();
            var previousValue = invokeToCallees.get(key);
            if (previousValue == null) {
                Object previous = invokeToCallees.putIfAbsent(key, callees);
                assert previous == null : "A race condition occurred when inserting " + key;
            } else if (previousValue != COLLISION) {
                invokeToCallees.put(key, COLLISION);
            }
            for (AnalysisMethod callee : callees) {
                callers.computeIfAbsent(callee, _ -> ConcurrentHashMap.newKeySet()).add(method);
            }
        }
    }

    /**
     * Returns the set of callers of the given method {@code callee}.
     */
    public Set<AnalysisMethod> getCallers(AnalysisMethod callee) {
        return callers.get(callee);
    }

    /**
     * Returns the set of call target methods for the given invoke. We use the information obtained
     * from static analysis: We use the value extracted from the corresponding {@link InvokeInfo} if
     * we can locate it and there are no collisions. Otherwise, we use all the reachable
     * implementations of the given {@code targetMethod}.
     */
    @SuppressWarnings("unchecked")
    public Collection<AnalysisMethod> getCallees(Invoke invoke, HostedMethod targetMethod) {
        var node = invoke.asFixedNode();
        var key = new InvokePosition(((HostedMethod) node.graph().method()).wrapped, invoke.bci(), targetMethod.wrapped);
        Object callees = invokeToCallees.get(key);
        if (callees != null && callees != COLLISION) {
            return ((Collection<AnalysisMethod>) callees);
        }
        return Arrays.stream(targetMethod.getImplementations()).map(HostedMethod::getWrapped).toList();
    }

    public Timer getCallGraphInitializationTimer() {
        return callGraphInitializationTimer;
    }

    /**
     * Exposed only for testing. Should not be used in production code.
     */
    public Map<InvokePosition, Object> getInvokeToCallees() {
        return invokeToCallees;
    }
}
