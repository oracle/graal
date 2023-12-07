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
package jdk.graal.compiler.replacements.amd64;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.unknownProbability;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.Snippets;

/**
 * Snippets used for conversion operations on AMD64 where the AMD64 instruction used does not match
 * the semantics of the JVM specification.
 */
public class AMD64ConvertSnippets implements Snippets {

    /**
     * Converts a float to an int.
     * <p>
     * This snippet accounts for the semantics of the x64 CVTTSS2SI instruction used to do the
     * conversion. If the float value is a NaN, infinity or if the result of the conversion is
     * larger than {@link Integer#MAX_VALUE} then CVTTSS2SI returns {@link Integer#MIN_VALUE} and
     * extra tests are required on the float value to return the correct int value.
     *
     * @param input the float being converted
     */
    @Snippet
    public static int f2i(float input) {
        if (probability(SLOW_PATH_PROBABILITY, Float.isNaN(input))) {
            return 0;
        }
        GuardingNode guard = SnippetAnchorNode.anchor();
        float nonNanInput = PiNode.piCastNonNanFloat(input, guard);
        int result = AMD64FloatConvertNode.convertToInt(FloatConvert.F2I, nonNanInput);
        if (probability(SLOW_PATH_PROBABILITY, result == Integer.MIN_VALUE)) {
            if (unknownProbability(input > 0.0d)) {
                // input is positive -> return maxInt
                return Integer.MAX_VALUE;
            }
        }
        return result;
    }

    /**
     * Converts a float to a long.
     * <p>
     * This snippet accounts for the semantics of the x64 CVTTSS2SI instruction used to do the
     * conversion. If the float value is a NaN or infinity then CVTTSS2SI returns
     * {@link Long#MIN_VALUE} and extra tests are required on the float value to return the correct
     * long value.
     *
     * @param input the float being converted
     */
    @Snippet
    public static long f2l(float input) {
        if (probability(SLOW_PATH_PROBABILITY, Float.isNaN(input))) {
            return 0;
        }
        GuardingNode guard = SnippetAnchorNode.anchor();
        float nonNanInput = PiNode.piCastNonNanFloat(input, guard);
        long result = AMD64FloatConvertNode.convertToLong(FloatConvert.F2L, nonNanInput);
        if (probability(SLOW_PATH_PROBABILITY, result == Long.MIN_VALUE)) {
            if (unknownProbability(input > 0.0d)) {
                // input is positive -> return maxInt
                return Long.MAX_VALUE;
            }
        }
        return result;
    }

    /**
     * Converts a double to an int.
     * <p>
     * This snippet accounts for the semantics of the x64 CVTTSD2SI instruction used to do the
     * conversion. If the double value is a NaN, infinity or if the result of the conversion is
     * larger than {@link Integer#MAX_VALUE} then CVTTSD2SI returns {@link Integer#MIN_VALUE} and
     * extra tests are required on the double value to return the correct int value.
     *
     * @param input the double being converted
     */
    @Snippet
    public static int d2i(double input) {
        if (probability(SLOW_PATH_PROBABILITY, Double.isNaN(input))) {
            return 0;
        }
        GuardingNode guard = SnippetAnchorNode.anchor();
        double nonNanInput = PiNode.piCastNonNanDouble(input, guard);
        int result = AMD64FloatConvertNode.convertToInt(FloatConvert.D2I, nonNanInput);
        if (probability(SLOW_PATH_PROBABILITY, result == Integer.MIN_VALUE)) {
            if (unknownProbability(input > 0.0d)) {
                // input is positive -> return maxInt
                return Integer.MAX_VALUE;
            }
        }
        return result;
    }

    /**
     * Converts a double to a long.
     * <p>
     * This snippet accounts for the semantics of the x64 CVTTSD2SI instruction used to do the
     * conversion. If the double value is a NaN, infinity or if the result of the conversion is
     * larger than {@link Long#MAX_VALUE} then CVTTSD2SI returns {@link Long#MIN_VALUE} and extra
     * tests are required on the double value to return the correct long value.
     *
     * @param input the double being converted
     */
    @Snippet
    public static long d2l(double input) {
        if (probability(SLOW_PATH_PROBABILITY, Double.isNaN(input))) {
            return 0;
        }
        GuardingNode guard = SnippetAnchorNode.anchor();
        double nonNanInput = PiNode.piCastNonNanDouble(input, guard);
        long result = AMD64FloatConvertNode.convertToLong(FloatConvert.D2L, nonNanInput);
        if (probability(SLOW_PATH_PROBABILITY, result == Long.MIN_VALUE)) {
            if (unknownProbability(input > 0.0d)) {
                // input is positive -> return maxInt
                return Long.MAX_VALUE;
            }
        }
        return result;
    }

    public static class Templates extends SnippetTemplate.AbstractTemplates {

        private final SnippetTemplate.SnippetInfo f2i;
        private final SnippetTemplate.SnippetInfo f2l;
        private final SnippetTemplate.SnippetInfo d2i;
        private final SnippetTemplate.SnippetInfo d2l;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, Providers providers) {
            super(options, providers);

            f2i = snippet(providers, AMD64ConvertSnippets.class, "f2i");
            f2l = snippet(providers, AMD64ConvertSnippets.class, "f2l");
            d2i = snippet(providers, AMD64ConvertSnippets.class, "d2i");
            d2l = snippet(providers, AMD64ConvertSnippets.class, "d2l");
        }

        public void lower(FloatConvertNode convert, LoweringTool tool) {
            if (!convert.graph().isAfterStage(StageFlag.VALUE_PROXY_REMOVAL)) {
                return;
            }

            SnippetTemplate.SnippetInfo key;
            StructuredGraph graph = convert.graph();
            switch (convert.getFloatConvert()) {
                case F2I:
                    key = f2i;
                    break;
                case F2L:
                    key = f2l;
                    break;
                case D2I:
                    key = d2i;
                    break;
                case D2L:
                    key = d2l;
                    break;
                default:
                    return;
            }

            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(key, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("input", convert.getValue());
            SnippetTemplate template = template(tool, convert, args);
            convert.getDebug().log("Lowering %s in %s: node=%s, template=%s, arguments=%s", convert.getFloatConvert(), graph, convert, template, args);
            template.instantiate(tool.getMetaAccess(), convert, SnippetTemplate.DEFAULT_REPLACER, tool, args);
            convert.safeDelete();
        }
    }
}
