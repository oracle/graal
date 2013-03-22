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

import static com.oracle.graal.replacements.SnippetTemplate.*;
import static com.oracle.graal.replacements.SnippetTemplate.Arguments.*;
import static com.oracle.graal.replacements.nodes.BranchProbabilityNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.ConvertNode.Op;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.*;

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
    public static int f2i(@Parameter("input") float input, @Parameter("result") int result) {
        if (result == Integer.MIN_VALUE) {
            probability(NOT_FREQUENT_PROBABILITY);
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
    public static long f2l(@Parameter("input") float input, @Parameter("result") long result) {
        if (result == Long.MIN_VALUE) {
            probability(NOT_FREQUENT_PROBABILITY);
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
    public static int d2i(@Parameter("input") double input, @Parameter("result") int result) {
        if (result == Integer.MIN_VALUE) {
            probability(NOT_FREQUENT_PROBABILITY);
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
    public static long d2l(@Parameter("input") double input, @Parameter("result") long result) {
        if (result == Long.MIN_VALUE) {
            probability(NOT_FREQUENT_PROBABILITY);
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

    public static class Templates extends AbstractTemplates<AMD64ConvertSnippets> {

        private final ResolvedJavaMethod f2i;
        private final ResolvedJavaMethod f2l;
        private final ResolvedJavaMethod d2i;
        private final ResolvedJavaMethod d2l;

        public Templates(CodeCacheProvider runtime, Assumptions assumptions, TargetDescription target) {
            super(runtime, assumptions, target, AMD64ConvertSnippets.class);
            f2i = snippet("f2i", float.class, int.class);
            f2l = snippet("f2l", float.class, long.class);
            d2i = snippet("d2i", double.class, int.class);
            d2l = snippet("d2l", double.class, long.class);
        }

        public void lower(ConvertNode convert, LoweringTool tool) {
            if (convert.opcode == Op.F2I) {
                lower0(convert, tool, f2i);
            } else if (convert.opcode == Op.F2L) {
                lower0(convert, tool, f2l);
            } else if (convert.opcode == Op.D2I) {
                lower0(convert, tool, d2i);
            } else if (convert.opcode == Op.D2L) {
                lower0(convert, tool, d2l);
            }
        }

        private void lower0(ConvertNode convert, LoweringTool tool, ResolvedJavaMethod snippet) {
            StructuredGraph graph = (StructuredGraph) convert.graph();

            // Insert a unique placeholder node in place of the Convert node so that the
            // Convert node can be used as an input to the snippet. All usage of the
            // Convert node are replaced by the placeholder which in turn is replaced by the
            // snippet.

            LocalNode replacee = graph.add(new LocalNode(Integer.MAX_VALUE, convert.stamp()));
            convert.replaceAtUsages(replacee);
            Key key = new Key(snippet);
            Arguments arguments = arguments("input", convert.value()).add("result", convert);
            SnippetTemplate template = cache.get(key, assumptions);
            Debug.log("Lowering %s in %s: node=%s, template=%s, arguments=%s", convert.opcode, graph, convert, template, arguments);
            template.instantiate(runtime, replacee, DEFAULT_REPLACER, tool, arguments);
        }
    }
}
