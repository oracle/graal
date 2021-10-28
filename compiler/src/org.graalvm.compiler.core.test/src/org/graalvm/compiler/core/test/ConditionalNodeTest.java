/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.junit.Test;

public class ConditionalNodeTest extends GraalCompilerTest {

    @SuppressWarnings("unused") private static int sink0;
    @SuppressWarnings("unused") private static int sink1;

    @Test
    public void test0() {
        test("conditionalTest0", 0);
        test("conditionalTest0", 1);
    }

    public static int conditionalTest0(int a) {
        int value;
        if (a == 1) {
            value = -1;
            sink1 = 0;
        } else {
            value = 6;
            sink1 = 1;
        }
        sink0 = 1;
        return Math.max(value, 6);
    }

    @Test
    public void test1() {
        test("conditionalTest1", 0);
        test("conditionalTest1", 1);
    }

    public static int conditionalTest1(int a) {
        int value;
        if (a == 1) {
            value = -1;
            sink1 = 0;
        } else {
            value = 6;
            sink1 = 1;
        }
        sink0 = 1;
        return Math.max(6, value);
    }

    @Test
    public void test2() {
        test("conditionalTest2", 0);
        test("conditionalTest2", 1);
    }

    public static int conditionalTest2(int a) {
        int value;
        if (a == 1) {
            value = -1;
            sink1 = 0;
        } else {
            value = 6;
            sink1 = 1;
        }
        sink0 = 1;
        return Math.min(value, -1);
    }

    @Test
    public void test3() {
        test("conditionalTest3", 0);
        test("conditionalTest3", 1);
    }

    public static int conditionalTest3(int a) {
        int value;
        if (a == 1) {
            value = -1;
            sink1 = 0;
        } else {
            value = 6;
            sink1 = 1;
        }
        sink0 = 1;
        return Math.min(-1, value);
    }

    @Test
    public void test4() {
        test("conditionalTest4", this, 0);
        test("conditionalTest4", this, 1);
    }

    int a;
    InvokeKind b;

    public static int conditionalTest4(ConditionalNodeTest node, int a) {
        if (a == 1) {
            node.b = InvokeKind.Virtual;
        } else {
            node.b = InvokeKind.Special;
        }
        node.a = a;
        return a;
    }

    @SuppressWarnings("all")
    static int lastIndexOf(char[] source, int sourceOffset, int sourceCount,
                    char[] target, int targetOffset, int targetCount,
                    int fromIndex) {
        /*
         * Check arguments; return immediately where possible. For consistency, don't check for null
         * str.
         */
        int rightIndex = sourceCount - targetCount;
        if (fromIndex < 0) {
            return -1;
        }
        if (fromIndex > rightIndex) {
            fromIndex = rightIndex;
        }
        /* Empty string always matches. */
        if (targetCount == 0) {
            return fromIndex;
        }

        int strLastIndex = targetOffset + targetCount - 1;
        char strLastChar = target[strLastIndex];
        int min = sourceOffset + targetCount - 1;
        int i = min + fromIndex;

        startSearchForLastChar: while (true) {
            while (i >= min && source[i] != strLastChar) {
                i--;
            }
            if (i < min) {
                return -1;
            }
            int j = i - 1;
            int start = j - (targetCount - 1);
            int k = strLastIndex - 1;

            while (j > start) {
                if (source[j--] != target[k--]) {
                    i--;
                    continue startSearchForLastChar;
                }
            }
            return start - sourceOffset + 1;
        }
    }

    public static String simple(String simpleName) {
        char[] value = simpleName.toCharArray();
        char[] target = ".".toCharArray();
        int lastDotIndex = lastIndexOf(value, 0, value.length,
                        target, 0, target.length, value.length);
        if (lastDotIndex < 0) {
            return null;
        }
        GraalDirectives.deoptimize();
        return simpleName.substring(0, lastDotIndex);
    }

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        // Disable profile based optimizations
        return OptimisticOptimizations.NONE;
    }

    @Test
    public void testConditionalExit() {
        test("simple", Object.class.getName());
    }
}
