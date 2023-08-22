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
package com.oracle.svm.hosted.classinitialization;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysis;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.code.FactoryMethodMarker;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.phases.ConstantFoldLoadFieldPlugin;
import com.oracle.svm.hosted.phases.InlineBeforeAnalysisPolicyUtils;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This class is necessary because simulation of class initializer is based on
 * {@link InlineBeforeAnalysis}. The scope keeps track of the number of bytes that were allocated in
 * the image heap.
 * 
 * See {@link SimulateClassInitializerSupport} for an overview of class initializer simulation.
 */
public final class SimulateClassInitializerPolicy extends InlineBeforeAnalysisPolicy {

    static class AccumulativeCounters {
        long allocatedBytes;
    }

    public static final class SimulateClassInitializerInlineScope extends InlineBeforeAnalysisPolicy.AbstractPolicyScope {
        final AccumulativeCounters accumulativeCounters;

        long allocatedBytes;

        SimulateClassInitializerInlineScope(AccumulativeCounters accumulativeCounters, int inliningDepth) {
            super(inliningDepth);
            this.accumulativeCounters = accumulativeCounters;
        }

        @Override
        public boolean allowAbort() {
            return true;
        }

        @Override
        public void commitCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
            SimulateClassInitializerInlineScope calleeScope = (SimulateClassInitializerInlineScope) callee;
            assert accumulativeCounters == calleeScope.accumulativeCounters;
            allocatedBytes += calleeScope.allocatedBytes;
        }

        @Override
        public void abortCalleeScope(InlineBeforeAnalysisPolicy.AbstractPolicyScope callee) {
            SimulateClassInitializerInlineScope calleeScope = (SimulateClassInitializerInlineScope) callee;
            assert accumulativeCounters == calleeScope.accumulativeCounters;
            accumulativeCounters.allocatedBytes -= calleeScope.allocatedBytes;
        }

        @Override
        public boolean processNode(AnalysisMetaAccess metaAccess, ResolvedJavaMethod method, Node node) {
            return true;
        }

        @Override
        public String toString() {
            return "allocatedBytes: " + allocatedBytes + " (" + accumulativeCounters.allocatedBytes + ")";
        }
    }

    private final SVMHost hostVM;
    private final SimulateClassInitializerSupport support;

    SimulateClassInitializerPolicy(SVMHost hostVM, SimulateClassInitializerSupport support) {
        super(new NodePlugin[]{new ConstantFoldLoadFieldPlugin(ParsingReason.PointsToAnalysis)});

        this.hostVM = hostVM;
        this.support = support;
    }

    @Override
    protected boolean shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (b.getDepth() > support.maxInlineDepth) {
            /* Safeguard against excessive inlining, for example endless recursion. */
            return false;
        }
        if (AnnotationAccess.isAnnotationPresent(method.getDeclaringClass(), FactoryMethodMarker.class)) {
            /*
             * Synthetic factory methods are annotated as "never inline before analysis" because
             * they would all be inlined immediately. But for the class initializer analysis, we
             * want them inlined so that we can simulate the allocations.
             */
            return true;
        }
        return InlineBeforeAnalysisPolicyUtils.inliningAllowed(hostVM, b, method);
    }

    @Override
    protected boolean tryInvocationPlugins() {
        return true;
    }

    @Override
    protected boolean needsExplicitExceptions() {
        return true;
    }

    @Override
    protected InlineInvokePlugin.InlineInfo createInvokeInfo(ResolvedJavaMethod method) {
        return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
    }

    @Override
    protected FixedWithNextNode processInvokeArgs(ResolvedJavaMethod targetMethod, FixedWithNextNode insertionPoint, ValueNode[] arguments) {
        // No action is needed
        return insertionPoint;
    }

    @Override
    protected AbstractPolicyScope createRootScope() {
        /* The counters including all inlined methods. */
        var accumulated = new AccumulativeCounters();
        /* The counters just for the class initializer. */
        return new SimulateClassInitializerInlineScope(accumulated, 0);
    }

    @Override
    protected AbstractPolicyScope openCalleeScope(AbstractPolicyScope o, AnalysisMetaAccess metaAccess,
                    ResolvedJavaMethod method, boolean[] constArgsWithReceiver, boolean intrinsifiedMethodHandle) {
        var outer = (SimulateClassInitializerInlineScope) o;
        return new SimulateClassInitializerInlineScope(outer.accumulativeCounters, outer.inliningDepth + 1);
    }
}
