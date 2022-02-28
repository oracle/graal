/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.junit.Test;

public class EarlyGVNTest extends GraalCompilerTest {

    @MustFold()
    public static int snippet00(int[] arr) {
        int i = arr.length;
        int i2 = arr.length;
        return i + i2;
    }

    @Test
    public void test00() {
        test("snippet00", new int[]{1, 2, 3});
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    @interface MustFold {
        boolean noLoopLeft() default true;
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        MustFold fold = graph.method().getAnnotation(MustFold.class);
        if (fold.noLoopLeft()) {
            assertFalse(graph.hasLoops());
        } else {
            assertTrue(graph.hasLoops());
        }
        super.checkHighTierGraph(graph);
    }

    @MustFold
    public static void snippet01(int[] arr) {
        int i = 0;
        while (true) {
            @SuppressWarnings("unused")
            int len = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test01() {
        test("snippet01", new int[0]);
    }

    public static int field = 0;

    @MustFold(noLoopLeft = false)
    public static void snippet02(@SuppressWarnings("unused") int[] arr) {
        int i = 0;
        while (true) {
            @SuppressWarnings("unused")
            int len = field;
            if (i < 10) {
                GraalDirectives.sideEffect(len);
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test02() {
        test("snippet02", new int[0]);
    }

    @MustFold
    public static void snippet03(int[] arr) {
        int i = 0;
        while (true) {
            @SuppressWarnings("unused")
            int len = arr.length;
            @SuppressWarnings("unused")
            int len2 = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test03() {
        test("snippet03", new int[0]);
    }

    static class X {
        int[] arr;

        X(int[] arr) {
            this.arr = arr;
        }
    }

    @MustFold
    public static void snippet04(X x) {
        int i = 0;
        while (true) {
            int[] arr = x.arr;
            @SuppressWarnings("unused")
            int len = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test04() {
        test("snippet04", new X(new int[0]));
    }

    static class Y {
        int y;
    }

    @MustFold(noLoopLeft = false)
    public static int snippet05(Y y) {
        int res = 0;
        int i = 0;
        while (true) {
            res = y.y;
            if (i < 10) {
                y.y = i;
                i++;
                continue;
            }
            break;
        }
        return res;
    }

    @Test
    public void test05() {
        test("snippet05", new ArgSupplier() {

            @Override
            public Object get() {
                return new Y();
            }
        });
    }

    @MustFold
    public static void snippet06(int[] arr) {
        int i = 0;
        while (true) {
            if (arr == null) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            @SuppressWarnings("unused")
            int len = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test06() {
        test("snippet06", new int[0]);
    }

    @MustFold
    public static void snippet07(int[] arr) {
        int i = 0;
        if (arr.length == 123) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        while (true) {
            if (arr.length == 123) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test07() {
        test("snippet07", new int[0]);
    }

    public static int field2;

    @MustFold
    public static int snippet08(int[] arr) {
        int f = field;
        field2 = f;
        if (arr.length == 123) {
            field = 123;
        }
        return field;
    }

    @Test
    public void test08() {
        test("snippet08", new int[0]);
    }

    @MustFold
    public static int snippet09(int[] arr) {
        int i = 0;
        while (true) {
            @SuppressWarnings("unused")
            int len = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
        return arr.length;
    }

    @Test
    public void test09() {
        test("snippet09", new int[0]);
    }

    @MustFold(noLoopLeft = false)
    public static int snippet10(Y y) {
        int res = 0;
        int i = 0;
        while (true) {
            if (i < 1000) {
                res = y.y;
                if (field == 123) {
                    res += 123;
                    field2 = res;
                } else {
                    if (field == 223) {
                        res += 33;
                    } else {
                        res += 55;
                    }
                    field2 = res;
                }
                i++;
                continue;
            }
            break;
        }
        return res;
    }

    @Test
    public void test10() {
        test("snippet10", new ArgSupplier() {

            @Override
            public Object get() {
                return new Y();
            }
        });
    }
}
