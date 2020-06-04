/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.options.OptionValues;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;

import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.getPolyglotOptionValue;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MaximumInlineNodeCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceInlining;

final class PEInliningPlanInvokePlugin implements InlineInvokePlugin {

    private final PartialEvaluator partialEvaluator;
    private final Deque<TruffleInliningPlan> inlining;
    private final StructuredGraph graph;
    private final int inliningNodeLimit;
    private final OptionValues options;
    private final CompilableTruffleAST compilable;
    private boolean graphTooBigReported;

    PEInliningPlanInvokePlugin(PartialEvaluator partialEvaluator, OptionValues options, CompilableTruffleAST compilable, TruffleInliningPlan inlining, StructuredGraph graph) {
        this.partialEvaluator = partialEvaluator;
        this.options = options;
        this.compilable = compilable;
        this.inlining = new ArrayDeque<>();
        this.inlining.push(inlining);
        this.graph = graph;
        this.inliningNodeLimit = getPolyglotOptionValue(options, MaximumInlineNodeCount);
    }

    @Override
    public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
        InlineInfo inlineInfo = PartialEvaluator.asInlineInfo(original);
        if (!inlineInfo.allowsInlining()) {
            return inlineInfo;
        }
        assert !builder.parsingIntrinsic();

        if (original.equals(partialEvaluator.callDirectMethod)) {
            ValueNode arg0 = arguments[1];
            if (!arg0.isConstant()) {
                GraalError.shouldNotReachHere("The direct call node does not resolve to a constant!");
            }
            if (graph.getNodeCount() > inliningNodeLimit) {
                logGraphTooBig();
                return inlineInfo;
            }
            TruffleInliningPlan.Decision decision = getDecision(inlining.peek(), (JavaConstant) arg0.asConstant());
            if (decision != null && decision.shouldInline()) {
                inlining.push(decision);
                JavaConstant assumption = decision.getNodeRewritingAssumption();
                builder.getAssumptions().record(new TruffleAssumption(assumption));
                return createStandardInlineInfo(partialEvaluator.callInlined);
            }
        }

        return inlineInfo;
    }

    static TruffleInliningPlan.Decision getDecision(TruffleInliningPlan inlining, JavaConstant callNode) {
        TruffleInliningPlan.Decision decision = inlining.findDecision(callNode);
        if (decision == null) {
            TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
            JavaConstant target = rt.getCallTargetForCallNode(callNode);
            PerformanceInformationHandler.reportDecisionIsNull(rt.asCompilableTruffleAST(target), callNode);
        } else if (!decision.isTargetStable()) {
            TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
            JavaConstant target = rt.getCallTargetForCallNode(callNode);
            PerformanceInformationHandler.reportCallTargetChanged(rt.asCompilableTruffleAST(target), callNode, decision);
            return null;
        }
        return decision;
    }

    private void logGraphTooBig() {
        if (!graphTooBigReported && getPolyglotOptionValue(options, TraceInlining)) {
            graphTooBigReported = true;
            final HashMap<String, Object> properties = new HashMap<>();
            properties.put("graph node count", graph.getNodeCount());
            properties.put("graph node limit", inliningNodeLimit);
            TruffleCompilerRuntime.getRuntime().logEvent(compilable, 0, "Truffle inlining caused graal node count to be too big during partial evaluation.", properties);
        }
    }

    @Override
    public void notifyAfterInline(ResolvedJavaMethod inlinedTargetMethod) {
        if (inlinedTargetMethod.equals(partialEvaluator.callInlined)) {
            inlining.pop();
        }
    }
}
