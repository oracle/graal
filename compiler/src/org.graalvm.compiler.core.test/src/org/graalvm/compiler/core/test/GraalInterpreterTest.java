/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Ignore;
import org.junit.Test;

public class GraalInterpreterTest extends GraalCompilerTest {

    public static final int fact(int n) {
        int result = 1;
        if (n < 0) {
            throw new IllegalArgumentException();
        }
        int i = n;
        while (i > 1) {
            result *= i;
            i -= 1;
        }
        return result;
    }

    /**
     * Smoke test for interpreter.
     */
    @Test
    public void test01() {
        Result expect = new Result(1, null);
        checkAgainstInterpreter(expect, true, "fact", 0);
    }

    /**
     * Test the looping and Phi nodes.
     */
    @Test
    public void test05() {
        Result expect = new Result(120, null);
        checkAgainstInterpreter(expect, true, "fact", 5);
    }

    /**
     * Test int overflow.
     */
    @Test
    public void testOverflow() {
        Result expect = new Result(-288522240, null);
        checkAgainstInterpreter(expect, true, "fact", 17);
    }

    public static class Foo {
        public int value;
        public boolean neg;

        static double s1;

        public int getValue() {
            return value + 1;
        }

        public int fact1(int n) {
            if (n < 1) {
                return neg ? -value : value;
            } else {
                return fact1(n - 1) * n;
            }
        }

        public Foo getObj(int n) {
            Foo foo = new Foo();
            foo.value = -n;
            return foo;
        }
    }

    public static final int object() {
        Foo foo = new Foo();
        foo.value = 17 + 2;
        return foo.value;
    }

    @Test
    public void testReadWriteField() {
        Result expect = new Result(19, null);
        checkAgainstInterpreter(expect, true, "object");
    }

    public static final int callMethod() {
        Foo foo = new Foo();
        foo.value = 17 + 22;
        return foo.getValue();
    }

    @Test
    public void testCallMethod() {
        Result expect = new Result(40, null);
        checkAgainstInterpreter(expect, true, "callMethod");
    }

    public static final int twoFoo() {
        Foo foo1 = new Foo();
        Foo foo2 = new Foo();
        foo1.value = 30;
        foo2.value = 40;
        return foo1.getValue() + foo2.getValue();
    }

    @Test
    public void testTwoInstances() {
        Result expect = new Result(72, null);
        checkAgainstInterpreter(expect, true, "twoFoo");
    }

    public static final int fooFact(int n, int base, boolean neg) {
        Foo foo = new Foo();
        foo.value = base;
        foo.neg = neg;
        return foo.fact1(n);
    }

    @Test
    public void testFooFact0() {
        Result expect = new Result(-11, null);
        checkAgainstInterpreter(expect, true, "fooFact", 0, 11, true);
    }

    public void testFooFact5() {
        Result expect = new Result(120000, null);
        checkAgainstInterpreter(expect, true, "fooFact", 5, 1000, false);
    }

    public static final int returnObj(int n) {
        Foo foo = new Foo();
        Foo foo2 = foo.getObj(n);
        return foo2.value;
    }

    @Test
    public void testReturnObject() {
        Result expect = new Result(-13, null);
        checkAgainstInterpreter(expect, true, "returnObj", 13);
    }


    public static int unwrapObj(Foo foo) {
        return foo.neg ? -foo.value : foo.value;
    }

    public static final int passObj(int n, boolean neg) {
        Foo foo = new Foo();
        foo.value = n;
        foo.neg = neg;
        return unwrapObj(foo);
    }

    @Test
    public void testPassObject13() {
        Result expect = new Result(13, null);
        checkAgainstInterpreter(expect, true, "passObj", 13, false);
    }

    @Test
    public void testPassObject29() {
        Result expect = new Result(-29, null);
        checkAgainstInterpreter(expect, true, "passObj", 29, true);
    }

    public static class Bar {
        public Bar x;
        public float y;
        public Bar[] z;
        public int[] k;
    }

    public static final float nestedBar(int s) {
        Bar bar = new Bar();
        if (s == 0) {
            bar.x = null;
            bar.x.y = 12.0f;
        } else if (s == 1) {
            bar.x = new Bar();
            bar.x.y = 12.0f;
        } else {
            Bar bar2 = null;
            bar.x = null;
        }
        bar.y = 20f;
        return bar.y - bar.x.y;
    }

    @Test
    public void testNestedBar() {
        Result expect = new Result(8f, null);
        checkAgainstInterpreter(expect, true, "nestedBar", 1);
    }

    @Test
    public void testNestedBarNull() {
        Result expect = new Result(null, new NullPointerException());
        checkAgainstInterpreter(expect, true, "nestedBar", 0);
    }

    @Test
    public void testNestedBarNull2() {
        Result expect = new Result(null, new NullPointerException());
        checkAgainstInterpreter(expect, true, "nestedBar", 2);
    }

    public static final int intArray(int s) {
        Bar bar = new Bar();
        if (s == 0) {
            bar.k = new int[3];
            bar.k[0] = 10;
            bar.k[1] = 2;
            bar.k[2] = bar.k[0] - 3;
            return bar.k[0] + bar.k[1] + bar.k[2];
        } else if (s == 1) {
            bar.k = new int[0];
            bar.k[0] = 30;
            return 0;
        } else {
            return bar.k[0];
        }
    }

    @Test
    public void testIntArray0() {
        Result expect = new Result(19, null);
        checkAgainstInterpreter(expect, true, "intArray", 0);
    }

    @Test
    public void testIntArray1() {
        Result expect = new Result(null, new IllegalArgumentException("Invalid array access index"));
        checkAgainstInterpreter(expect, true, "intArray", 1);
    }

    @Test
    public void testIntArray2() {
        Result expect = new Result(null, new NullPointerException());
        checkAgainstInterpreter(expect, true, "intArray", 2);
    }

    /**
     * Test exception result.
     */
    @Ignore("cannot handle strange object constants inside Throwable.init yet.")
    @Test
    public void testNeg() {
        Result expect = new Result(null, new IllegalArgumentException());
        checkAgainstInterpreter(expect, true, "fact", -1);
    }
}
