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

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.phases.InlineBeforeAnalysisPolicyUtils.AccumulativeInlineScope;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;

public class InlineBeforeAnalysisPolicyImpl extends InlineBeforeAnalysisPolicy {

    private final SVMHost hostVM;
    private final InlineBeforeAnalysisPolicyUtils inliningUtils;

    public InlineBeforeAnalysisPolicyImpl(SVMHost hostVM, InlineBeforeAnalysisPolicyUtils inliningUtils) {
        super(new NodePlugin[]{new ConstantFoldLoadFieldPlugin(ParsingReason.PointsToAnalysis)});
        this.hostVM = hostVM;
        this.inliningUtils = inliningUtils;
    }

    @Override
    protected boolean shouldInlineInvoke(GraphBuilderContext b, AbstractPolicyScope policyScope, AnalysisMethod method, ValueNode[] args) {
        return inliningUtils.shouldInlineInvoke(b, hostVM, (AccumulativeInlineScope) policyScope, method);
    }

    @Override
    protected InlineInfo createInvokeInfo(AnalysisMethod method) {
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
    protected AbstractPolicyScope openCalleeScope(AbstractPolicyScope outer, AnalysisMethod method) {
        return inliningUtils.createAccumulativeInlineScope((InlineBeforeAnalysisPolicyUtils.AccumulativeInlineScope) outer, method, (ignore) -> false);
    }

    @Override
    protected boolean shouldOmitIntermediateMethodInState(AnalysisMethod method) {
        return inliningUtils.shouldOmitIntermediateMethodInState(method);
    }

    @Override
    protected FixedWithNextNode processInvokeArgs(AnalysisMethod targetMethod, FixedWithNextNode insertionPoint, ValueNode[] arguments, NodeSourcePosition sourcePosition) {
        // No action is needed
        return insertionPoint;
    }
}
