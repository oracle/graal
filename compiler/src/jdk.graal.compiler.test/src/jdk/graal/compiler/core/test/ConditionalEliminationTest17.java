/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.DeoptimizationReason;

public class ConditionalEliminationTest17 extends ConditionalEliminationTestBase {

    static final class A {
    }

    static final class B {
        int value = 1337;

        int value() {
            return value;
        }
    }

    private static boolean equals(byte[] a, byte[] b) {
        if (a == b) {
            return true;
        } else if (a.length != b.length) {
            return false;
        } else {
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final Object X = new Object();
    private static final Object Y = new Object();

    public static int test1Snippet(byte[][] haystack, byte[] needle, Object[] otherValues) {
        Object someValue = otherValues[0];
        Object anotherValue1 = otherValues[1];
        Object anotherValue2 = otherValues[2];
        if (haystack.length != 0) {
            for (int i = 0; i < haystack.length; i++) {
                byte[] hay = haystack[i];
                if (equals(hay, needle)) {
                    int result = 42;
                    if (anotherValue1 == X) {
                        if (anotherValue2 == Y) {
                            result++;
                            GraalDirectives.controlFlowAnchor();
                        }
                        GraalDirectives.controlFlowAnchor();
                    }
                    if (someValue.getClass() != A.class) {
                        GraalDirectives.deoptimizeAndInvalidate();
                    }
                    GraalDirectives.controlFlowAnchor();
                    return result;
                }
            }
        }
        return ((B) someValue).value();
    }

    @Test
    public void test1() {
        Object[] args = {new byte[][]{"foo".getBytes(), "bar".getBytes()}, "neither".getBytes(), new Object[]{new B(), X, Y}};
        test(getInitialOptions(), EnumSet.allOf(DeoptimizationReason.class), "test1Snippet", args);
    }

    public static int test2Snippet(byte[][] haystack, byte[] needle, Object[] otherValues) {
        Object someValue = otherValues[0];
        Object anotherValue1 = otherValues[1];
        Object anotherValue2 = otherValues[2];
        if (haystack.length != 0) {
            for (int i = 0; i < haystack.length; i++) {
                byte[] hay = haystack[i];
                if (equals(hay, needle)) {
                    int result = 42;
                    if (anotherValue1 == X) {
                        if (anotherValue2 == Y) {
                            result++;
                            GraalDirectives.controlFlowAnchor();
                        }
                        GraalDirectives.controlFlowAnchor();
                    }
                    if (someValue == null || someValue.getClass() != A.class) {
                        GraalDirectives.deoptimizeAndInvalidate();
                    }
                    GraalDirectives.controlFlowAnchor();
                    return result;
                }
            }
        }
        return ((B) someValue).value();
    }

    @Test
    public void test2() {
        Object[] args = {new byte[][]{"foo".getBytes(), "bar".getBytes()}, "bad".getBytes(), new Object[]{new B(), X, Y}};
        test(getInitialOptions(), EnumSet.allOf(DeoptimizationReason.class), "test2Snippet", args);
    }

    // simplified and reduced version of equals (semantics of this method is not relevant, only IR
    // shape)
    private static boolean simpleEquals(byte[][] a) {
        for (int i = 0; i < a.length; i++) {
            if (GraalDirectives.sideEffect(i) == 123) {
                return true;
            }
        }
        return false;

    }

    // simplified and reduced version of test1Snippet (semantics of this method is not relevant,
    // only IR shape)
    @SuppressWarnings("unused")
    public static int test3Snippet(byte[][] haystack, byte[] needle, Object someValue, Object anotherValue1) {
        for (int i = 0; i < haystack.length; i++) {
            if (simpleEquals(haystack)) {
                int result = 42;
                if (anotherValue1 == X) {
                    GraalDirectives.controlFlowAnchor();
                }
                if (someValue.getClass() != A.class) {
                    GraalDirectives.deoptimizeAndInvalidate();
                }
                return result;
            }
        }
        return 0;
    }

    @Test
    public void test3() {
        Object[] args = {new byte[][]{"foo".getBytes(), "bar".getBytes()}, "neither".getBytes(), X, Y};
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.OptFloatingReads, false);
        test(opt, EnumSet.allOf(DeoptimizationReason.class), "test3Snippet", args);
    }

}
