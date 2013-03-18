/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.jtt.reflect;

import com.oracle.graal.test.*;
import com.oracle.graal.jtt.*;

/*
 */
public class Field_get02 extends JTTTest {

    private static final Field_get02 object = new Field_get02();

    public final byte byteField = 11;
    public final short shortField = 12;
    public final char charField = 13;
    public final int intField = 14;
    public final long longField = 15;
    public final float floatField = 16;
    public final double doubleField = 17;
    public final boolean booleanField = true;

    public static boolean test(int arg) throws NoSuchFieldException, IllegalAccessException {
        if (arg == 0) {
            return Field_get02.class.getField("byteField").get(object).equals(object.byteField);
        } else if (arg == 1) {
            return Field_get02.class.getField("shortField").get(object).equals(object.shortField);
        } else if (arg == 2) {
            return Field_get02.class.getField("charField").get(object).equals(object.charField);
        } else if (arg == 3) {
            return Field_get02.class.getField("intField").get(object).equals(object.intField);
        } else if (arg == 4) {
            return Field_get02.class.getField("longField").get(object).equals(object.longField);
        } else if (arg == 5) {
            return Field_get02.class.getField("floatField").get(object).equals(object.floatField);
        } else if (arg == 6) {
            return Field_get02.class.getField("doubleField").get(object).equals(object.doubleField);
        } else if (arg == 7) {
            return Field_get02.class.getField("booleanField").get(object).equals(object.booleanField);
        }
        return false;
    }

    @LongTest
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @LongTest
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @LongTest
    public void run2() throws Throwable {
        runTest("test", 2);
    }

    @LongTest
    public void run3() throws Throwable {
        runTest("test", 3);
    }

    @LongTest
    public void run4() throws Throwable {
        runTest("test", 4);
    }

    @LongTest
    public void run5() throws Throwable {
        runTest("test", 5);
    }

    @LongTest
    public void run6() throws Throwable {
        runTest("test", 6);
    }

    @LongTest
    public void run7() throws Throwable {
        runTest("test", 7);
    }

    @LongTest
    public void run8() throws Throwable {
        runTest("test", 8);
    }

}
