/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.reachability;

import jdk.graal.compiler.nodes.StructuredGraph;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;

import jdk.vm.ci.meta.JavaConstant;

/**
 * This handler analyzes methods using method summaries, which are obtained via an instance of
 * MethodSummaryProvider.
 *
 * @see MethodSummaryProvider
 */
public class MethodSummaryBasedHandler implements ReachabilityMethodProcessingHandler {

    private final Timer summaryTimer;

    private final MethodSummaryProvider methodSummaryProvider;

    public MethodSummaryBasedHandler(MethodSummaryProvider methodSummaryProvider, TimerCollection timerCollection) {
        this.methodSummaryProvider = methodSummaryProvider;
        this.summaryTimer = timerCollection.createTimer("((summaries))");
    }

    @SuppressWarnings("try")
    @Override
    public void onMethodReachable(ReachabilityAnalysisEngine bb, ReachabilityAnalysisMethod method) {
        MethodSummary summary;
        try (Timer.StopTimer t = summaryTimer.start()) {
            summary = methodSummaryProvider.getSummary(bb, method);
        }
        processSummary(bb, method, summary);
    }

    @SuppressWarnings("try")
    @Override
    public void processGraph(ReachabilityAnalysisEngine bb, StructuredGraph graph) {
        MethodSummary summary;
        try (Timer.StopTimer t = summaryTimer.start()) {
            summary = methodSummaryProvider.getSummary(bb, graph);
        }
        ReachabilityAnalysisMethod method = (ReachabilityAnalysisMethod) graph.method();
        processSummary(bb, method, summary);
    }

    /**
     * Use the summary to update the analysis state.
     */
    private static void processSummary(ReachabilityAnalysisEngine bb, ReachabilityAnalysisMethod method, MethodSummary summary) {
        for (AnalysisMethod invokedMethod : summary.virtualInvokedMethods) {
            bb.markMethodInvoked((ReachabilityAnalysisMethod) invokedMethod, method);
        }
        for (AnalysisMethod invokedMethod : summary.specialInvokedMethods) {
            bb.markMethodSpecialInvoked((ReachabilityAnalysisMethod) invokedMethod, method);
        }
        for (AnalysisMethod invokedMethod : summary.implementationInvokedMethods) {
            bb.markMethodImplementationInvoked((ReachabilityAnalysisMethod) invokedMethod, method);
        }
        for (AnalysisType type : summary.accessedTypes) {
            bb.registerTypeAsReachable(type, method);
        }
        for (AnalysisType type : summary.instantiatedTypes) {
            bb.registerTypeAsAllocated(type, method);
        }
        for (AnalysisField field : summary.readFields) {
            bb.markFieldRead(field, method);
            bb.registerTypeAsReachable(field.getType(), method);
        }
        for (AnalysisField field : summary.writtenFields) {
            bb.markFieldWritten(field, method);
        }
        for (JavaConstant constant : summary.embeddedConstants) {
            bb.handleEmbeddedConstant(method, constant, method);
        }
        for (AnalysisMethod rootMethod : summary.foreignCallTargets) {
            bb.addRootMethod(rootMethod, false, method);
        }
    }
}
