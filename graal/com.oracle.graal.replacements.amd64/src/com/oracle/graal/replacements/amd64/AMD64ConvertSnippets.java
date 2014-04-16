/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.replacements.amd64;

import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;

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
     * @param result the result produced by the CVTTSS2SI instruction
     */
    @Snippet
    public static int f2i(float input, int result) {
        if (probability(SLOW_PATH_PROBABILITY, result == Integer.MIN_VALUE)) {
            if (Float.isNaN(input)) {
                // input is NaN -> return 0
                return 0;
            } else if (input > 0.0f) {
                // input is > 0 -> return max int
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
     * @param result the result produced by the CVTTSS2SI instruction
     */
    @Snippet
    public static long f2l(float input, long result) {
        if (probability(SLOW_PATH_PROBABILITY, result == Long.MIN_VALUE)) {
            if (Float.isNaN(input)) {
                // input is NaN -> return 0
                return 0;
            } else if (input > 0.0f) {
                // input is > 0 -> return max int
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
     * @param result the result produced by the CVTTSS2SI instruction
     */
    @Snippet
    public static int d2i(double input, int result) {
        if (probability(SLOW_PATH_PROBABILITY, result == Integer.MIN_VALUE)) {
            if (Double.isNaN(input)) {
                // input is NaN -> return 0
                return 0;
            } else if (input > 0.0d) {
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
     * @param result the result produced by the CVTTSS2SI instruction
     */
    @Snippet
    public static long d2l(double input, long result) {
        if (probability(SLOW_PATH_PROBABILITY, result == Long.MIN_VALUE)) {
            if (Double.isNaN(input)) {
                // input is NaN -> return 0
                return 0;
            } else if (input > 0.0d) {
                // input is positive -> return maxInt
                return Long.MAX_VALUE;
            }
        }
        return result;
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo f2i;
        private final SnippetInfo f2l;
        private final SnippetInfo d2i;
        private final SnippetInfo d2l;

        public Templates(Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(providers, snippetReflection, target);

            f2i = snippet(AMD64ConvertSnippets.class, "f2i");
            f2l = snippet(AMD64ConvertSnippets.class, "f2l");
            d2i = snippet(AMD64ConvertSnippets.class, "d2i");
            d2l = snippet(AMD64ConvertSnippets.class, "d2l");
        }

        public void lower(FloatConvertNode convert, LoweringTool tool) {
            SnippetInfo key;
            switch (convert.getOp()) {
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

            StructuredGraph graph = convert.graph();

            Arguments args = new Arguments(key, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("input", convert.getInput());
            args.add("result", graph.unique(new AMD64FloatConvertNode(convert.stamp(), convert.getOp(), convert.getInput())));

            SnippetTemplate template = template(args);
            Debug.log("Lowering %s in %s: node=%s, template=%s, arguments=%s", convert.getOp(), graph, convert, template, args);
            template.instantiate(providers.getMetaAccess(), convert, DEFAULT_REPLACER, tool, args);
            graph.removeFloating(convert);
        }
    }
}
