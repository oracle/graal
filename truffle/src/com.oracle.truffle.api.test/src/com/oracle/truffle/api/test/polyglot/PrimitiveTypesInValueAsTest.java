/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
