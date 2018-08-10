/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrimitiveTypesInValueAsTest {

    @Parameters(name = "{index}: class {0} and value {1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
                        // boxed types
                        {Byte.class, (byte) 10},
                        {Short.class, (short) 11},
                        {Integer.class, 1},
                        {Long.class, 1332L},
                        {Float.class, 1.3f},
                        {Double.class, 1.4},
                        {Character.class, 'A'},
                        {Boolean.class, true},
                        {String.class, "String"},

                        // primitive types
                        {byte.class, (byte) 20},
                        {short.class, (short) 31},
                        {int.class, 41},
                        {long.class, 122332L},
                        {float.class, 1.6f},
                        {double.class, 1.466},
                        {char.class, 'B'},
                        {boolean.class, false},
        });
    }

    private final Class<?> type;
    private final Object value;

    public PrimitiveTypesInValueAsTest(Class<?> type, Object value) {
        this.type = type;
        this.value = value;
    }

    @Test
    public void testFindGlobalSymbolAndValueAs() {
        try (Context context = Context.create()) {
            Object computed = context.asValue(value).as(type);
            assertEquals(value, computed);
        }
    }
}
