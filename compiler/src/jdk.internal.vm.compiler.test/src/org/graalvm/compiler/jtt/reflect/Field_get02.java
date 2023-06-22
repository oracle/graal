/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.reflect;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class Field_get02 extends JTTTest {

    public static class TestClass {
        public final byte byteField = 11;
        public final short shortField = 12;
        public final char charField = 13;
        public final int intField = 14;
        public final long longField = 15;
        public final float floatField = 16;
        public final double doubleField = 17;
        public final boolean booleanField = true;
    }

    private static final TestClass object = new TestClass();

    public static boolean test(int arg) throws NoSuchFieldException, IllegalAccessException {
        if (arg == 0) {
            return TestClass.class.getField("byteField").get(object).equals(object.byteField);
        } else if (arg == 1) {
            return TestClass.class.getField("shortField").get(object).equals(object.shortField);
        } else if (arg == 2) {
            return TestClass.class.getField("charField").get(object).equals(object.charField);
        } else if (arg == 3) {
            return TestClass.class.getField("intField").get(object).equals(object.intField);
        } else if (arg == 4) {
            return TestClass.class.getField("longField").get(object).equals(object.longField);
        } else if (arg == 5) {
            return TestClass.class.getField("floatField").get(object).equals(object.floatField);
        } else if (arg == 6) {
            return TestClass.class.getField("doubleField").get(object).equals(object.doubleField);
        } else if (arg == 7) {
            return TestClass.class.getField("booleanField").get(object).equals(object.booleanField);
        }
        return false;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 4);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 5);
    }

    @Test
    public void run6() throws Throwable {
        runTest("test", 6);
    }

    @Test
    public void run7() throws Throwable {
        runTest("test", 7);
    }

    @Test
    public void run8() throws Throwable {
        runTest("test", 8);
    }

}
