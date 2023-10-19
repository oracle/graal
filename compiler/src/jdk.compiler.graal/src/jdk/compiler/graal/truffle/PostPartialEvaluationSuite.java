/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.truffle;

import jdk.compiler.graal.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.java.MethodCallTargetNode;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.BasePhase;
import jdk.compiler.graal.phases.PhaseSuite;
import jdk.compiler.graal.phases.common.CanonicalizerPhase;
import jdk.compiler.graal.phases.common.ConditionalEliminationPhase;
import jdk.compiler.graal.phases.common.inlining.InliningUtil;
import jdk.compiler.graal.truffle.phases.FrameAccessVerificationPhase;
import jdk.compiler.graal.truffle.phases.PhiTransformPhase;
import jdk.compiler.graal.virtual.phases.ea.PartialEscapePhase;

public class PostPartialEvaluationSuite extends PhaseSuite<TruffleTierContext> {

    @SuppressWarnings("this-escape")
    public PostPartialEvaluationSuite(OptionValues optionValues, boolean iterativePartialEscape) {
        CanonicalizerPhase canonicalizerPhase = CanonicalizerPhase.create();
        appendPhase(new ConvertDeoptimizeToGuardPhase(canonicalizerPhase));
        appendPhase(new InlineReplacementsPhase());
        appendPhase(new ConditionalEliminationPhase(false));
        appendPhase(canonicalizerPhase);
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
                                methodCallTargetNode.invoke().getInlineControl(), graph.trackNodeSourcePosition(), methodCallTargetNode.asNode().getNodeSourcePosition(),
                                graph.allowAssumptions(), context.debug.getOptions());
                if (inlineGraph != null) {
                    InliningUtil.inline(methodCallTargetNode.invoke(), inlineGraph, true, methodCallTargetNode.targetMethod());
                }
            }
        }
    }
}
