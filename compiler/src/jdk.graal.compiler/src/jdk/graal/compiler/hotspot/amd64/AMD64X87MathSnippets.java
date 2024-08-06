/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

public class AMD64X87MathSnippets implements Snippets {

    private static final double PI_4 = Math.PI / 4;

    @Snippet
    public static double sin(double input) {
        if (probability(LIKELY_PROBABILITY, Math.abs(input) < PI_4)) {
            return AMD64X87MathIntrinsicNode.compute(input, UnaryOperation.SIN);
        }
        return callDouble1(UnaryOperation.SIN.foreignCallSignature, input);
    }

    @Snippet
    public static double cos(double input) {
        if (probability(LIKELY_PROBABILITY, Math.abs(input) < PI_4)) {
            return AMD64X87MathIntrinsicNode.compute(input, UnaryOperation.COS);
        }
        return callDouble1(UnaryOperation.COS.foreignCallSignature, input);
    }

    @Snippet
    public static double tan(double input) {
        if (probability(LIKELY_PROBABILITY, Math.abs(input) < PI_4)) {
            return AMD64X87MathIntrinsicNode.compute(input, UnaryOperation.TAN);
        }
        return callDouble1(UnaryOperation.TAN.foreignCallSignature, input);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native double callDouble1(@ConstantNodeParameter ForeignCallSignature signature, double value);

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo sin;
        private final SnippetInfo cos;
        private final SnippetInfo tan;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, Providers providers) {
            super(options, providers);

            sin = snippet(providers, AMD64X87MathSnippets.class, "sin");
            cos = snippet(providers, AMD64X87MathSnippets.class, "cos");
            tan = snippet(providers, AMD64X87MathSnippets.class, "tan");
        }

        public void lower(UnaryMathIntrinsicNode mathIntrinsicNode, LoweringTool tool) {
            SnippetInfo info;

            switch (mathIntrinsicNode.getOperation()) {
                case SIN:
                    info = sin;
                    break;
                case COS:
                    info = cos;
                    break;
                case TAN:
                    info = tan;
                    break;
                default:
                    throw GraalError.shouldNotReachHere("Snippet not found for math intrinsic " + mathIntrinsicNode.getOperation().name()); // ExcludeFromJacocoGeneratedReport
            }

            Arguments args = new Arguments(info, mathIntrinsicNode.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("input", mathIntrinsicNode.getValue());
            template(tool, mathIntrinsicNode, args).instantiate(tool.getMetaAccess(), mathIntrinsicNode, DEFAULT_REPLACER, tool, args);
            mathIntrinsicNode.safeDelete();
        }
    }
}
