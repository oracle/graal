/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.hosted.SVMHost;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The defaults for node limits are very conservative. Only small methods should be inlined. The
 * only exception are constants - an arbitrary number of constants is always allowed. Limiting to 1
 * node (which can be also 1 invoke) means that field accessors can be inlined and forwarding
 * methods can be inlined. But null checks and class initialization checks are already putting a
 * method above the limit. On the other hand, the inlining depth is generous because we do do not
 * need to limit it. Note that more experimentation is necessary to come up with the optimal
 * configuration.
 *
 * Important: the implementation details of this class are publicly observable API. Since
 * {@link java.lang.reflect.Method} constants can be produced by inlining lookup methods with
 * constant arguments, reducing inlining can break customer code. This means we can never reduce the
 * amount of inlining in a future version without breaking compatibility. This also means that we
 * must be conservative and only inline what is necessary for known use cases.
 */
public class InlineBeforeAnalysisPolicyImpl extends InlineBeforeAnalysisPolicy {
    private final int maxInliningDepth = InlineBeforeAnalysisPolicyUtils.Options.InlineBeforeAnalysisAllowedDepth.getValue();

    private final SVMHost hostVM;
    private final InlineBeforeAnalysisPolicyUtils inliningUtils;

    public InlineBeforeAnalysisPolicyImpl(SVMHost hostVM, InlineBeforeAnalysisPolicyUtils inliningUtils) {
        super(new NodePlugin[]{new ConstantFoldLoadFieldPlugin(ParsingReason.PointsToAnalysis)});
        this.hostVM = hostVM;
        this.inliningUtils = inliningUtils;
    }

    @Override
    protected boolean shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (inliningUtils.alwaysInlineInvoke((AnalysisMetaAccess) b.getMetaAccess(), method)) {
            return true;
        }
        if (b.getDepth() > maxInliningDepth) {
            return false;
        }
        if (b.recursiveInliningDepth(method) > 0) {
            /* Prevent recursive inlining. */
            return false;
        }

        return InlineBeforeAnalysisPolicyUtils.inliningAllowed(hostVM, b, method);
    }

    @Override
    protected InlineInfo createInvokeInfo(ResolvedJavaMethod method) {
        return InlineInfo.createStandardInlineInfo(method);
    }

    @Override
    protected boolean needsExplicitExceptions() {
        return true;
    }

    @Override
    protected boolean tryInvocationPlugins() {
        /*
         * We conditionally allow the invocation plugin to be triggered during graph decoding to see
         * what happens.
         */
        return true;
    }

    @Override
    protected AbstractPolicyScope createRootScope() {
        /* We do not need a scope for the root method. */
        return null;
    }

    @Override
    protected AbstractPolicyScope openCalleeScope(AbstractPolicyScope outer, AnalysisMetaAccess metaAccess,
                    ResolvedJavaMethod method, boolean[] constArgsWithReceiver, boolean intrinsifiedMethodHandle) {
        return inliningUtils.createAccumulativeInlineScope((InlineBeforeAnalysisPolicyUtils.AccumulativeInlineScope) outer, metaAccess, method, constArgsWithReceiver, intrinsifiedMethodHandle);
    }

    @Override
    protected boolean shouldOmitIntermediateMethodInState(ResolvedJavaMethod method) {
        return inliningUtils.shouldOmitIntermediateMethodInState(method);
    }

    @Override
    protected FixedWithNextNode processInvokeArgs(ResolvedJavaMethod targetMethod, FixedWithNextNode insertionPoint, ValueNode[] arguments, NodeSourcePosition sourcePosition) {
        // No action is needed
        return insertionPoint;
    }
}
