/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Tests {@code <} and {@code |<|} implications where an operand is non-negative.
 */
public class IntegerLowerThanImplicationTest extends GraalCompilerTest {

    /**
     * Provides tested values for an unrestricted operand.
     */
    private static final int[] VALUES = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};

    /**
     * Provides tested values for a non-negative operand.
     */
    private static final int[] NON_NEGATIVE_VALUES = {0, 1, Integer.MAX_VALUE};

    /**
     * Returns {@code !(x < y) || x |<| y} for non-negative {@code x}.
     */
    public static boolean signedToUnsigned(int unrestricted, int nonNegative) {
        int x = GraalDirectives.positivePi(nonNegative);
        int y = unrestricted;
        return x >= y || Integer.compareUnsigned(x, y) < 0;
    }

    /**
     * {@code 0 <= x && x < y} implies {@code x |<| y}, so {@code !(x < y) || x |<| y} folds to
     * {@code true}.
     */
    @Test
    public void testSignedToUnsigned() {
        testSnippet("signedToUnsigned");
    }

    /**
     * Returns {@code x |<| y || !(x < y)} for non-negative {@code x}.
     */
    public static boolean notUnsignedToNotSigned(int unrestricted, int nonNegative) {
        int x = GraalDirectives.positivePi(nonNegative);
        int y = unrestricted;
        return Integer.compareUnsigned(x, y) < 0 || x >= y;
    }

    /**
     * {@code 0 <= x && !(x |<| y)} implies {@code !(x < y)}, so {@code x |<| y || !(x < y)} folds
     * to {@code true}.
     */
    @Test
    public void testNotUnsignedToNotSigned() {
        testSnippet("notUnsignedToNotSigned");
    }

    /**
     * Returns {@code !(x |<| y) || x < y} for non-negative {@code y}.
     */
    public static boolean unsignedToSigned(int unrestricted, int nonNegative) {
        int x = unrestricted;
        int y = GraalDirectives.positivePi(nonNegative);
        return Integer.compareUnsigned(x, y) >= 0 || x < y;
    }

    /**
     * {@code 0 <= y && x |<| y} implies {@code x < y}, so {@code !(x |<| y) || x < y} folds to
     * {@code true}.
     */
    @Test
    public void testUnsignedToSigned() {
        testSnippet("unsignedToSigned");
    }

    /**
     * Returns {@code x < y || !(x |<| y)} for non-negative {@code y}.
     */
    public static boolean notSignedToNotUnsigned(int unrestricted, int nonNegative) {
        int x = unrestricted;
        int y = GraalDirectives.positivePi(nonNegative);
        return x < y || Integer.compareUnsigned(x, y) >= 0;
    }

    /**
     * {@code 0 <= y && !(x < y)} implies {@code !(x |<| y)}, so {@code x < y || !(x |<| y)} folds
     * to {@code true}.
     */
    @Test
    public void testNotSignedToNotUnsigned() {
        testSnippet("notSignedToNotUnsigned");
    }

    /**
     * Returns {@code x < y || !(x |<| y)} for non-negative {@code x}. Its contrapositive,
     * {@code x |<| y => x < y}, does not hold for {@code x = 0, y = -1}.
     */
    public static boolean lowerStampCounterexample(int unrestricted, int nonNegative) {
        int x = GraalDirectives.positivePi(nonNegative);
        int y = unrestricted;
        return x < y || Integer.compareUnsigned(x, y) >= 0;
    }

    /**
     * Tests {@code 0 <= x} does not prove {@code !(x < y) => !(x |<| y)}.
     */
    @Test
    public void testLowerStampCounterexample() {
        testSnippet("lowerStampCounterexample");
    }

    /**
     * Returns {@code x |<| y || !(x < y)} for non-negative {@code y}. Its contrapositive,
     * {@code x < y => x |<| y}, does not hold for {@code x = -1, y = 0}.
     */
    public static boolean upperStampCounterexample(int unrestricted, int nonNegative) {
        int x = unrestricted;
        int y = GraalDirectives.positivePi(nonNegative);
        return Integer.compareUnsigned(x, y) < 0 || x >= y;
    }

    /**
     * Tests {@code 0 <= y} does not prove {@code !(x |<| y) => !(x < y)}.
     */
    @Test
    public void testUpperStampCounterexample() {
        testSnippet("upperStampCounterexample");
    }

    /**
     * Checks that each snippet has the expected constant or non-constant result after high-tier
     * optimization.
     */
    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        String methodName = graph.method().getName();
        if (methodName.equals("lowerStampCounterexample") || methodName.equals("upperStampCounterexample")) {
            Assert.assertFalse(methodName + " must not be folded to a constant", returnNode.result().isConstant());
        } else {
            Assert.assertTrue(methodName + " must be folded to a constant", returnNode.result().isConstant());
            Assert.assertEquals(1, returnNode.result().asJavaConstant().asInt());
        }
        super.checkHighTierGraph(graph);
    }

    /**
     * Tests a snippet with all combinations of unrestricted and non-negative values.
     */
    private void testSnippet(String snippet) {
        for (int unrestricted : VALUES) {
            for (int nonNegative : NON_NEGATIVE_VALUES) {
                test(snippet, unrestricted, nonNegative);
            }
        }
    }
}
