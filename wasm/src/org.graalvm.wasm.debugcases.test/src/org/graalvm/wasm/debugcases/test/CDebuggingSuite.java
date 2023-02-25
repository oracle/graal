/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.debugcases.test;

import static org.graalvm.wasm.debugcases.test.DebugAssert.assertArrayElementEquals;
import static org.graalvm.wasm.debugcases.test.DebugAssert.assertPointersEquals;
import static org.graalvm.wasm.debugcases.test.DebugAssert.assertValueEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.debug.DebugValue;

public class CDebuggingSuite extends DebuggingSuiteBase {
    private static final int SHORT_MSB_SET = 0x8000;
    private static final int SHORT_LSB_SET = 0x0001;

    private static final int INT_MSB_SET = 0x8000_0000;
    private static final int INT_LSB_SET = 0x0000_0001;

    private static final long LONG_MSB_SET = 0x8000_0000_0000_0000L;
    private static final long LONG_LSB_SET = 0x0000_0000_0000_0001L;

    @Override
    protected String resourceDir() {
        return "c";
    }

    @Test
    public void testArrays() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 43, "main");
        checkLocals(i, 50, locals -> {
            DebugValue a = locals.getProperty("a");
            assertNotNull(a);
            assertTrue(a.isArray());
            List<DebugValue> aArray = a.getArray();
            assertArrayElementEquals(aArray, 0, 0);
            assertArrayElementEquals(aArray, 1, 1);
            assertArrayElementEquals(aArray, 2, 2);
            assertArrayElementEquals(aArray, 3, 3);

            DebugValue b = locals.getProperty("b");
            assertNotNull(b);
            assertTrue(b.isArray());
            List<DebugValue> bArray = b.getArray();
            assertEquals(bArray.size(), 2);

            DebugValue b0 = bArray.get(0);
            assertNotNull(b0);
            assertTrue(b0.isArray());
            List<DebugValue> b0Array = b0.getArray();
            assertArrayElementEquals(b0Array, 0, 0);
            assertArrayElementEquals(b0Array, 1, 1);
            assertArrayElementEquals(b0Array, 2, 2);

            DebugValue b1 = bArray.get(1);
            assertNotNull(b1);
            assertTrue(b1.isArray());
            List<DebugValue> b1Array = b1.getArray();
            assertArrayElementEquals(b1Array, 0, 3);
            assertArrayElementEquals(b1Array, 1, 4);
            assertArrayElementEquals(b1Array, 2, 5);

            DebugValue c = locals.getProperty("c");
            assertNotNull(c);
            assertTrue(c.isArray());
            List<DebugValue> cArray = c.getArray();
            assertArrayElementEquals(cArray, 0, "'H' 72");
            assertArrayElementEquals(cArray, 1, "'e' 101");
            assertArrayElementEquals(cArray, 2, "'l' 108");
            assertArrayElementEquals(cArray, 3, "'l' 108");
            assertArrayElementEquals(cArray, 4, "'o' 111");

            DebugValue dPtr = locals.getProperty("d");
            assertNotNull(dPtr);
            assertValueEquals(dPtr, "*d", "'W' 87");
        });
        runTest("arrays", i);
    }

    @Test
    public void testControlFlow() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 54, "main");
        enterFunction(i, 49, "foo");
        enterFunction(i, 45, "bar");
        exitFunction(i);
        enterFunction(i, 45, "bar");
        exitFunction(i);
        exitFunction(i);
        enterFunction(i, 49, "foo");
        enterFunction(i, 45, "bar");
        exitFunction(i);
        enterFunction(i, 45, "bar");
        exitFunction(i);
        exitFunction(i);
        enterFunction(i, 49, "foo");
        enterFunction(i, 45, "bar");
        exitFunction(i);
        enterFunction(i, 45, "bar");
        exitFunction(i);
        exitFunction(i);
        enterFunction(i, 49, "foo");
        enterFunction(i, 45, "bar");
        exitFunction(i);
        enterFunction(i, 45, "bar");
        exitFunction(i);
        exitFunction(i);
        enterFunction(i, 49, "foo");
        enterFunction(i, 45, "bar");
        exitFunction(i);
        enterFunction(i, 45, "bar");
        exitFunction(i);
        exitFunction(i);
        runTest("control-flow", i);
    }

    @Test
    public void testDecorators() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 45, "main");
        checkLocals(i, 49, locals -> {
            assertValueEquals(locals, "typedefedVal", Integer.toUnsignedLong(15));
            assertValueEquals(locals, "constVal", 234);
            assertValueEquals(locals, "cuVal", Integer.toUnsignedLong(128));
            assertValueEquals(locals, "volatileVal", 756);
        });
        exitFunction(i);
        runTest("decorators", i);
    }

    @Test
    public void testEnums() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 47, "main");
        checkLocals(i, 51, locals -> {
            assertValueEquals(locals, "w", "friday");
            assertValueEquals(locals, "w2", "Fri");
            assertValueEquals(locals, "d", "Satu");
            assertValueEquals(locals, "w3", "undefined(50)");
        });
        runTest("enums", i);
    }

    @Test
    public void testFunctionPointers() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 77, "main");
        checkLocals(i, 86, locals -> {
            assertPointersEquals(locals, "voidFuncNoArgsPtr", null, "*voidFuncNoArgsPtr", "void function()");
            assertPointersEquals(locals, "voidFuncImplicitVarArgsPtr", null, "*voidFuncImplicitVarArgsPtr", "void function(...)");
            assertPointersEquals(locals, "voidFuncIntArgPtr", null, "*voidFuncIntArgPtr", "void function(int)");
            assertPointersEquals(locals, "voidFuncIntVarArgsPtr", null, "*voidFuncIntVarArgsPtr", "void function(int, ...)");
            assertPointersEquals(locals, "intFuncNoArgsPtr", null, "*intFuncNoArgsPtr", "int function()");
            assertPointersEquals(locals, "intFuncImplicitVarArgsPtr", null, "*intFuncImplicitVarArgsPtr", "int function(...)");
            assertPointersEquals(locals, "intFuncIntArgPtr", null, "*intFuncIntArgPtr", "int function(int)");
            assertPointersEquals(locals, "intFuncIntVarArgsPtr", null, "*intFuncIntVarArgsPtr", "int function(int, ...)");
        });

        enterFunction(i, 46, "voidFuncNoArgs");
        exitFunction(i);

        enterFunction(i, 50, "voidFuncImplicitVarArgs");
        exitFunction(i);

        enterFunction(i, 54, "voidFuncIntArg");
        checkLocals(i, 54, locals -> assertValueEquals(locals, "i", 42));
        exitFunction(i);

        enterFunction(i, 58, "voidFuncIntVarArgs");
        checkLocals(i, 58, locals -> assertValueEquals(locals, "i", 42));
        exitFunction(i);

        enterFunction(i, 61, "intFuncNoArgs");
        exitFunction(i);

        checkLocals(i, 93, locals -> assertValueEquals(locals, "res", 42));

        enterFunction(i, 65, "intFuncImplicitVarArgs");
        exitFunction(i);

        checkLocals(i, 94, locals -> assertValueEquals(locals, "res", 42));

        enterFunction(i, 69, "intFuncIntArg");
        checkLocals(i, 69, locals -> assertValueEquals(locals, "i", 42));
        exitFunction(i);

        checkLocals(i, 95, locals -> assertValueEquals(locals, "res", 42));

        enterFunction(i, 73, "intFuncIntVarArgs");
        checkLocals(i, 73, locals -> assertValueEquals(locals, "i", 42));
        exitFunction(i);

        checkLocals(i, 97, locals -> assertValueEquals(locals, "res", 42));
        runTest("function-pointers", i);
    }

    @Test
    public void testLoops() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 43, "main");
        checkLocals(i, 46, locals -> {
            assertValueEquals(locals, "i", 0);
            assertValueEquals(locals, "j", 0);
        });
        moveTo(i, 45);
        checkLocals(i, 46, locals -> {
            assertValueEquals(locals, "i", 1);
            assertValueEquals(locals, "j", 0);
        });
        moveTo(i, 45);
        checkLocals(i, 46, locals -> {
            assertValueEquals(locals, "i", 2);
            assertValueEquals(locals, "j", 1);
        });

        checkLocals(i, 53, locals -> {
            assertValueEquals(locals, "i", 0);
            assertValueEquals(locals, "j", 0);
        });
        moveTo(i, 54);
        checkLocals(i, 53, locals -> {
            assertValueEquals(locals, "i", 1);
            assertValueEquals(locals, "j", 1);
        });
        moveTo(i, 54);
        checkLocals(i, 53, locals -> {
            assertValueEquals(locals, "i", 2);
            assertValueEquals(locals, "i", 2);
        });

        checkLocals(i, 61, locals -> {
            assertValueEquals(locals, "k", 0);
            assertValueEquals(locals, "j", 0);
            assertValueEquals(locals, "i", 0);
        });
        moveTo(i, 59);
        checkLocals(i, 61, locals -> {
            assertValueEquals(locals, "k", 0);
            assertValueEquals(locals, "j", 1);
            assertValueEquals(locals, "i", 1);
        });
        moveTo(i, 65);
        runTest("loops", i);
    }

    @Test
    public void testPrimitives() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 86, "main");
        checkLocals(i, 124, locals -> {
            assertValueEquals(locals, "c1", "'A' 65");
            assertValueEquals(locals, "c2", "'a' 97");
            assertValueEquals(locals, "c3", "'0' 48");
            assertValueEquals(locals, "c4", "'+' 43");

            assertValueEquals(locals, "s1", (short) SHORT_MSB_SET);
            assertValueEquals(locals, "s2", (short) SHORT_LSB_SET);
            assertValueEquals(locals, "s3", SHORT_MSB_SET);
            assertValueEquals(locals, "s4", SHORT_LSB_SET);

            assertValueEquals(locals, "i1", INT_MSB_SET);
            assertValueEquals(locals, "i2", INT_LSB_SET);
            assertValueEquals(locals, "i3", Integer.toUnsignedLong(INT_MSB_SET));
            assertValueEquals(locals, "i4", Integer.toUnsignedLong(INT_LSB_SET));

            assertValueEquals(locals, "l1", LONG_MSB_SET);
            assertValueEquals(locals, "l2", LONG_LSB_SET);
            assertValueEquals(locals, "l3", Long.toUnsignedString(LONG_MSB_SET));
            assertValueEquals(locals, "l4", Long.toUnsignedString(LONG_LSB_SET));

            assertValueEquals(locals, "f1", 0.0f);
            assertValueEquals(locals, "f2", 1.0f);
            assertValueEquals(locals, "f3", -1.0f);
            assertValueEquals(locals, "f4", 1.25f);
            assertValueEquals(locals, "f5", -1.25f);

            assertValueEquals(locals, "d1", 0.0);
            assertValueEquals(locals, "d2", 1.0);
            assertValueEquals(locals, "d3", -1.0);
            assertValueEquals(locals, "d4", 1.25);
            assertValueEquals(locals, "d5", -1.25);

            assertValueEquals(locals, "c1G", "'A' 65");
            assertValueEquals(locals, "c2G", "'a' 97");
            assertValueEquals(locals, "c3G", "'0' 48");
            assertValueEquals(locals, "c4G", "'+' 43");
        });
        checkGlobals(i, 124, globals -> {
            assertValueEquals(globals, "C1", "'A' 65");
            assertValueEquals(globals, "C2", "'a' 97");
            assertValueEquals(globals, "C3", "'0' 48");
            assertValueEquals(globals, "C4", "'+' 43");

            assertValueEquals(globals, "S1", (short) SHORT_MSB_SET);
            assertValueEquals(globals, "S2", (short) SHORT_LSB_SET);
            assertValueEquals(globals, "S3", SHORT_MSB_SET);
            assertValueEquals(globals, "S4", SHORT_LSB_SET);

            assertValueEquals(globals, "I1", INT_MSB_SET);
            assertValueEquals(globals, "I2", INT_LSB_SET);
            assertValueEquals(globals, "I3", Integer.toUnsignedLong(INT_MSB_SET));
            assertValueEquals(globals, "I4", Integer.toUnsignedLong(INT_LSB_SET));

            assertValueEquals(globals, "L1", LONG_MSB_SET);
            assertValueEquals(globals, "L2", LONG_LSB_SET);
            assertValueEquals(globals, "L3", Long.toUnsignedString(LONG_MSB_SET));
            assertValueEquals(globals, "L4", Long.toUnsignedString(LONG_LSB_SET));

            assertValueEquals(globals, "F1", 0.0f);
            assertValueEquals(globals, "F2", 1.0f);
            assertValueEquals(globals, "F3", -1.0f);
            assertValueEquals(globals, "F4", 1.25f);
            assertValueEquals(globals, "F5", -1.25f);

            assertValueEquals(globals, "D1", 0.0);
            assertValueEquals(globals, "D2", 1.0);
            assertValueEquals(globals, "D3", -1.0);
            assertValueEquals(globals, "D4", 1.25);
            assertValueEquals(globals, "D5", -1.25);
        });
        exitFunction(i);
        runTest("primitives", i);
    }

    @Test
    public void testRecursion() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 50, "main");
        enterFunction(i, 43, "foo");
        checkLocals(i, 46, locals -> assertValueEquals(locals, "i", 5));
        enterFunction(i, 43, "foo");
        checkLocals(i, 46, locals -> assertValueEquals(locals, "i", 4));
        enterFunction(i, 43, "foo");
        checkLocals(i, 46, locals -> assertValueEquals(locals, "i", 3));
        enterFunction(i, 43, "foo");
        checkLocals(i, 46, locals -> assertValueEquals(locals, "i", 2));
        enterFunction(i, 43, "foo");
        checkLocals(i, 43, locals -> assertValueEquals(locals, "i", 1));
        moveTo(i, 44);
        runTest("recursion", i);
    }

    @Test
    public void testStructs() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 56, "main");
        checkLocals(i, 58, locals -> {
            DebugValue a = locals.getProperty("a");
            assertNotNull(a);
            assertValueEquals(a, "a", 128);
            assertValueEquals(a, "b", 256);
        });

        enterFunction(i, 49, "test");
        checkLocals(i, 51, locals -> {
            DebugValue a = locals.getProperty("a");
            assertNotNull(a);
            assertValueEquals(a, "a", 128);
            assertValueEquals(a, "b", 256);

            DebugValue b = locals.getProperty("b");
            assertNotNull(b);
            assertValueEquals(b, "a", 268);
            assertValueEquals(b, "b", 140);
        });
        exitFunction(i);

        checkLocals(i, 59, locals -> {
            DebugValue a = locals.getProperty("a");
            assertNotNull(a);
            assertValueEquals(a, "a", 268);
            assertValueEquals(a, "b", 140);
        });
        runTest("structs", i);
    }

    @Test
    public void testUnions() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 74, "main");
        checkGlobals(i, 75, globals -> {
            DebugValue myGlobalSimpleUnion = globals.getProperty("myGlobalSimpleUnion");
            assertNotNull(myGlobalSimpleUnion);
            assertValueEquals(myGlobalSimpleUnion, "a", 16);
            assertValueEquals(myGlobalSimpleUnion, "b", 16);
            assertValueEquals(myGlobalSimpleUnion, "c", 16);
        });
        checkGlobals(i, 76, globals -> {
            DebugValue myGlobalSimpleUnion = globals.getProperty("myGlobalSimpleUnion");
            assertNotNull(myGlobalSimpleUnion);
            assertValueEquals(myGlobalSimpleUnion, "a", 32);
            assertValueEquals(myGlobalSimpleUnion, "b", 32);
            assertValueEquals(myGlobalSimpleUnion, "c", 32);
        });
        checkGlobals(i, 78, globals -> {
            DebugValue myGlobalSimpleUnion = globals.getProperty("myGlobalSimpleUnion");
            assertNotNull(myGlobalSimpleUnion);
            assertValueEquals(myGlobalSimpleUnion, "a", 512);
            assertValueEquals(myGlobalSimpleUnion, "b", 512);
            assertValueEquals(myGlobalSimpleUnion, "c", 512);
        });

        checkGlobals(i, 79, globals -> {
            DebugValue myGlobalFloatUnion = globals.getProperty("myGlobalFloatUnion");
            assertNotNull(myGlobalFloatUnion);
            assertValueEquals(myGlobalFloatUnion, "a", 5.9f);
            assertValueEquals(myGlobalFloatUnion, "b", (short) 52429);
            assertValueEquals(myGlobalFloatUnion, "c", (short) 52429);
            assertValueEquals(myGlobalFloatUnion, "d", 5.9f);
        });
        checkGlobals(i, 80, globals -> {
            DebugValue myGlobalFloatUnion = globals.getProperty("myGlobalFloatUnion");
            assertNotNull(myGlobalFloatUnion);
            assertValueEquals(myGlobalFloatUnion, "a", 5.875000476837158203125f);
            assertValueEquals(myGlobalFloatUnion, "b", (short) 1);
            assertValueEquals(myGlobalFloatUnion, "c", (short) 1);
            assertValueEquals(myGlobalFloatUnion, "d", 5.875000476837158203125f);
        });
        checkGlobals(i, 81, globals -> {
            DebugValue myGlobalFloatUnion = globals.getProperty("myGlobalFloatUnion");
            assertNotNull(myGlobalFloatUnion);
            assertValueEquals(myGlobalFloatUnion, "a", 5.875347137451171875f);
            assertValueEquals(myGlobalFloatUnion, "b", (short) 728);
            assertValueEquals(myGlobalFloatUnion, "c", (short) 728);
            assertValueEquals(myGlobalFloatUnion, "d", 5.875347137451171875f);
        });
        checkGlobals(i, 83, globals -> {
            DebugValue myGlobalFloatUnion = globals.getProperty("myGlobalFloatUnion");
            assertNotNull(myGlobalFloatUnion);
            assertValueEquals(myGlobalFloatUnion, "a", 0.0f);
            assertValueEquals(myGlobalFloatUnion, "b", (short) 0);
            assertValueEquals(myGlobalFloatUnion, "c", (short) 0);
            assertValueEquals(myGlobalFloatUnion, "d", 0.0f);
        });

        checkGlobals(i, 84, globals -> {
            DebugValue myGlobalDoubleUnion = globals.getProperty("myGlobalDoubleUnion");
            assertNotNull(myGlobalDoubleUnion);
            assertValueEquals(myGlobalDoubleUnion, "a", 9.2f);
            assertValueEquals(myGlobalDoubleUnion, "b", 5.39409672155347401714067869416e-315);
            assertValueEquals(myGlobalDoubleUnion, "c", 1091777331);
            assertValueEquals(myGlobalDoubleUnion, "d", 5.39409672155347401714067869416e-315);
        });
        checkGlobals(i, 85, globals -> {
            DebugValue myGlobalDoubleUnion = globals.getProperty("myGlobalDoubleUnion");
            assertNotNull(myGlobalDoubleUnion);
            assertValueEquals(myGlobalDoubleUnion, "a", 4.17232506322307017398998141289e-8f);
            assertValueEquals(myGlobalDoubleUnion, "b", 4.3);
            assertValueEquals(myGlobalDoubleUnion, "c", 858993459);
            assertValueEquals(myGlobalDoubleUnion, "d", 4.3);
        });
        checkGlobals(i, 86, globals -> {
            DebugValue myGlobalDoubleUnion = globals.getProperty("myGlobalDoubleUnion");
            assertNotNull(myGlobalDoubleUnion);
            assertValueEquals(myGlobalDoubleUnion, "a", 2.66246708221715243475508620825e-44f);
            assertValueEquals(myGlobalDoubleUnion, "b", 4.29999923706056375038997430238);
            assertValueEquals(myGlobalDoubleUnion, "c", 19);
            assertValueEquals(myGlobalDoubleUnion, "d", 4.29999923706056375038997430238);
        });
        checkGlobals(i, 88, globals -> {
            DebugValue myGlobalDoubleUnion = globals.getProperty("myGlobalDoubleUnion");
            assertNotNull(myGlobalDoubleUnion);
            assertValueEquals(myGlobalDoubleUnion, "a", 0.0f);
            assertValueEquals(myGlobalDoubleUnion, "b", 0.0);
            assertValueEquals(myGlobalDoubleUnion, "c", 0);
            assertValueEquals(myGlobalDoubleUnion, "d", 0.0);
        });

        checkGlobals(i, 89, globals -> {
            DebugValue myGlobalPointerUnion = globals.getProperty("myGlobalPointerUnion");
            assertNotNull(myGlobalPointerUnion);
            assertValueEquals(myGlobalPointerUnion, "a", (short) 14);
            assertValueEquals(myGlobalPointerUnion, "b", 14);
            assertPointersEquals(myGlobalPointerUnion, "c", "int*", "*c", null);
        });
        checkGlobals(i, 92, globals -> {
            DebugValue myGlobalPointerUnion = globals.getProperty("myGlobalPointerUnion");
            assertNotNull(myGlobalPointerUnion);
            assertValueEquals(myGlobalPointerUnion, "a", (short) 23);
            assertValueEquals(myGlobalPointerUnion, "b", 23);
            assertPointersEquals(myGlobalPointerUnion, "c", "int*", "*c", null);
        });

        checkLocals(i, 93, locals -> {
            DebugValue mySimpleUnion = locals.getProperty("mySimpleUnion");
            assertNotNull(mySimpleUnion);
            assertValueEquals(mySimpleUnion, "a", 8);
            assertValueEquals(mySimpleUnion, "b", 8);
            assertValueEquals(mySimpleUnion, "c", 8);
        });
        checkLocals(i, 94, locals -> {
            DebugValue mySimpleUnion = locals.getProperty("mySimpleUnion");
            assertNotNull(mySimpleUnion);
            assertValueEquals(mySimpleUnion, "a", 64);
            assertValueEquals(mySimpleUnion, "b", 64);
            assertValueEquals(mySimpleUnion, "c", 64);
        });
        checkLocals(i, 97, locals -> {
            DebugValue mySimpleUnion = locals.getProperty("mySimpleUnion");
            assertNotNull(mySimpleUnion);
            assertValueEquals(mySimpleUnion, "a", 256);
            assertValueEquals(mySimpleUnion, "b", 256);
            assertValueEquals(mySimpleUnion, "c", 256);
        });

        checkLocals(i, 98, locals -> {
            DebugValue myFloatUnion = locals.getProperty("myFloatUnion");
            assertNotNull(myFloatUnion);
            assertValueEquals(myFloatUnion, "a", 3.7f);
            assertValueEquals(myFloatUnion, "b", -13107);
            assertValueEquals(myFloatUnion, "c", -13107);
            assertValueEquals(myFloatUnion, "d", 3.7f);
        });
        checkLocals(i, 99, locals -> {
            DebugValue myFloatUnion = locals.getProperty("myFloatUnion");
            assertNotNull(myFloatUnion);
            assertValueEquals(myFloatUnion, "a", 3.68750024f);
            assertValueEquals(myFloatUnion, "b", 1);
            assertValueEquals(myFloatUnion, "c", 1);
            assertValueEquals(myFloatUnion, "d", 3.68750024f);
        });
        checkLocals(i, 100, locals -> {
            DebugValue myFloatUnion = locals.getProperty("myFloatUnion");
            assertNotNull(myFloatUnion);
            assertValueEquals(myFloatUnion, "a", 3.69044328f);
            assertValueEquals(myFloatUnion, "b", 12345);
            assertValueEquals(myFloatUnion, "c", 12345);
            assertValueEquals(myFloatUnion, "d", 3.69044328f);
        });
        checkLocals(i, 103, locals -> {
            DebugValue myFloatUnion = locals.getProperty("myFloatUnion");
            assertNotNull(myFloatUnion);
            assertValueEquals(myFloatUnion, "a", 0.0f);
            assertValueEquals(myFloatUnion, "b", 0);
            assertValueEquals(myFloatUnion, "c", 0);
            assertValueEquals(myFloatUnion, "d", 0.0f);
        });
        checkLocals(i, 104, locals -> {
            DebugValue myDoubleUnion = locals.getProperty("myDoubleUnion");
            assertNotNull(myDoubleUnion);
            assertValueEquals(myDoubleUnion, "a", 0.3f);
            assertValueEquals(myDoubleUnion, "b", 5.18894283457103004141078799899e-315);
            assertValueEquals(myDoubleUnion, "c", 1050253722);
            assertValueEquals(myDoubleUnion, "d", 5.18894283457103004141078799899e-315);
        });
        checkLocals(i, 105, locals -> {
            DebugValue myDoubleUnion = locals.getProperty("myDoubleUnion");
            assertNotNull(myDoubleUnion);
            assertValueEquals(myDoubleUnion, "a", 2.72008302208e+23f);
            assertValueEquals(myDoubleUnion, "b", 7.6);
            assertValueEquals(myDoubleUnion, "c", 1717986918);
            assertValueEquals(myDoubleUnion, "d", 7.6);
        });
        checkLocals(i, 106, locals -> {
            DebugValue myDoubleUnion = locals.getProperty("myDoubleUnion");
            assertNotNull(myDoubleUnion);
            assertValueEquals(myDoubleUnion, "a", 8.40779078595e-45f);
            assertValueEquals(myDoubleUnion, "b", 7.59999847412109819089209850063e0);
            assertValueEquals(myDoubleUnion, "c", 5);
            assertValueEquals(myDoubleUnion, "d", 7.59999847412109819089209850063e0);
        });
        checkLocals(i, 109, locals -> {
            DebugValue myDoubleUnion = locals.getProperty("myDoubleUnion");
            assertNotNull(myDoubleUnion);
            assertValueEquals(myDoubleUnion, "a", 0.0f);
            assertValueEquals(myDoubleUnion, "b", 0.0);
            assertValueEquals(myDoubleUnion, "c", 0);
            assertValueEquals(myDoubleUnion, "d", 0.0);
        });
        checkLocals(i, 110, locals -> {
            DebugValue myPointerUnion = locals.getProperty("myPointerUnion");
            assertNotNull(myPointerUnion);
            assertValueEquals(myPointerUnion, "a", (short) 213);
            assertValueEquals(myPointerUnion, "b", 213);
            assertPointersEquals(myPointerUnion, "c", "int*", "*c", null);
        });
        checkLocals(i, 112, locals -> {
            DebugValue myPointerUnion = locals.getProperty("myPointerUnion");
            assertNotNull(myPointerUnion);
            assertValueEquals(myPointerUnion, "a", (short) 3855);
            assertValueEquals(myPointerUnion, "b", 252645135);
            assertPointersEquals(myPointerUnion, "c", "int*", "*c", null);
        });
        runTest("unions", i);
    }
}
