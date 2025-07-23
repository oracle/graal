/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.InsertProxyPhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.truffle.phases.FrameAccessVerificationPhase;
import jdk.graal.compiler.truffle.phases.PhiTransformPhase;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;

public class PostPartialEvaluationSuite extends PhaseSuite<TruffleTierContext> {

    @SuppressWarnings("this-escape")
    public PostPartialEvaluationSuite(OptionValues optionValues, boolean iterativePartialEscape) {
        CanonicalizerPhase canonicalizerPhase = CanonicalizerPhase.create();
        appendPhase(new InsertProxyPhase());
        appendPhase(new ConvertDeoptimizeToGuardPhase(canonicalizerPhase));
        appendPhase(new InlineReplacementsPhase());
        appendPhase(canonicalizerPhase);
        appendPhase(new ConditionalEliminationPhase(canonicalizerPhase, false));
        appendPhase(new FrameAccessVerificationPhase());
        appendPhase(new PartialEscapePhase(iterativePartialEscape, canonicalizerPhase, optionValues));
        appendPhase(new PhiTransformPhase(canonicalizerPhase));
    }

    public static class InlineReplacementsPhase extends BasePhase<TruffleTierContext> {

        @Override
        protected void run(StructuredGraph graph, TruffleTierContext context) {
            for (MethodCallTargetNode methodCallTargetNode : graph.getNodes(MethodCallTargetNode.TYPE)) {
                if (!methodCallTargetNode.invokeKind().isDirect()) {
                    continue;
                }
                StructuredGraph inlineGraph = context.getProviders().getReplacements().getInlineSubstitution(methodCallTargetNode.targetMethod(), methodCallTargetNode.invoke().bci(),
                                false, methodCallTargetNode.invoke().getInlineControl(), graph.trackNodeSourcePosition(), methodCallTargetNode.asNode().getNodeSourcePosition(),
                                graph.allowAssumptions(), context.debug.getOptions());
                if (inlineGraph != null) {
                    InliningUtil.inline(methodCallTargetNode.invoke(), inlineGraph, true, methodCallTargetNode.targetMethod());
                }
            }
        }
    }
}
