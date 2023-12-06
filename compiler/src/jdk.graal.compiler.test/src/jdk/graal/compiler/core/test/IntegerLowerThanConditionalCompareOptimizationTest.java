/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.junit.Test;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;

public class IntegerLowerThanConditionalCompareOptimizationTest extends IntegerLowerThanCommonArithmeticTestBase {

    @BytecodeParserForceInline
    private static boolean compare(CanonicalCondition cc, int x, int y) {
        return switch (cc) {
            case LT -> x < y;
            case BT -> Integer.compareUnsigned(x, y) < 0;
            case EQ -> x == y;
        };
    }

    @BytecodeParserForceInline
    private static boolean compare(CanonicalCondition cc, int x, int y, boolean flip) {
        return flip ? compare(cc, y, x) : compare(cc, x, y);
    }

    public static boolean testSnippetTrueConst(int x, int c1, int c2, int c3, CanonicalCondition cc, boolean flipCC, CanonicalCondition eq, boolean flipEQ) {
        // ((x < c1) ? x : c2) <== c3 where c3 < c1 <= c2 can be simplified to x <== c3.
        // ((c1 < x) ? x : c2) <== c3 where c2 <= c1 < c3 can be simplified to x >== c3.
        return compare(eq, (compare(cc, x, c1, flipCC) ? x : c2), c3, flipEQ);
    }

    public static boolean testSnippetFalseConst(int x, int c1, int c2, int c3, CanonicalCondition cc, boolean flipCC, CanonicalCondition eq, boolean flipEQ) {
        // ((c1 < x) ? c2 : x) <== c3 where c3 < c1 <= c2 can be simplified to x <== c3.
        // ((x < c1) ? c2 : x) <== c3 where c2 <= c1 < c3 can be simplified to x >== c3.
        return compare(eq, (compare(cc, c1, x, flipCC) ? c2 : x), c3, flipEQ);
    }

    @Override
    protected Object[] getBindArgs(Object[] args) {
        return new Object[]{
                        NO_BIND,
                        args[1],
                        args[2],
                        args[3],
                        args[4],
                        args[5],
                        args[6],
                        args[7],
        };
    }

    @Test
    public void runTest() {
        for (String snippet : new String[]{
                        "testSnippetTrueConst",
                        "testSnippetFalseConst",
        }) {
            for (var cc : new CanonicalCondition[]{CanonicalCondition.LT, CanonicalCondition.BT}) {
                for (var eq : new CanonicalCondition[]{CanonicalCondition.EQ, cc}) {
                    for (var flipCC : new boolean[]{false, true}) {
                        for (var flipEQ : new boolean[]{false, true}) {
                            int c = 8;
                            for (int d : new int[]{c - 1, c, c + 1}) {
                                for (int e : new int[]{c - 1, c, c + 1, -c}) {
                                    for (int x : new int[]{c - 2, c - 1, c, c + 1, c + 2, -c}) {
                                        var args = new Object[]{x, c, d, e, cc, flipCC, eq, flipEQ};
                                        try {
                                            runTest(snippet, args);
                                        } catch (AssertionError failure) {
                                            throw new AssertionError(failure.getMessage() + " for " + snippet + " " + List.of(args), failure);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
