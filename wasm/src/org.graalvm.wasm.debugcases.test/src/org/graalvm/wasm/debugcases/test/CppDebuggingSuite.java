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

import com.oracle.truffle.api.debug.DebugValue;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.graalvm.wasm.debugcases.test.DebugAssert.assertArrayElementEquals;
import static org.graalvm.wasm.debugcases.test.DebugAssert.assertStringEquals;
import static org.graalvm.wasm.debugcases.test.DebugAssert.assertValueEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CppDebuggingSuite extends DebuggingSuiteBase {
    private static int signExtend(int value, int bits) {
        return (value << (32 - bits)) >> (32 - bits);
    }

    private static long signExtend(long value, int bits) {
        return (value << (64 - bits)) >> (64 - bits);
    }

    @Override
    protected String resourceDir() {
        return "cpp";
    }

    @Test
    public void testBitFields() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 128, "main");
        checkLocals(i, 144, locals -> {
            DebugValue uiBool = locals.getProperty("uiBool");
            assertNotNull(uiBool);
            assertValueEquals(uiBool, "a", 1);
            assertValueEquals(uiBool, "b", 0);
            assertValueEquals(uiBool, "c", 1);
            assertValueEquals(uiBool, "d", 0);
            assertValueEquals(uiBool, "e", 1);
            assertValueEquals(uiBool, "f", 0);
            assertValueEquals(uiBool, "g", 1);
            assertValueEquals(uiBool, "h", 0);

            DebugValue siBool = locals.getProperty("siBool");
            assertNotNull(siBool);
            assertValueEquals(siBool, "a", signExtend(1, 1));
            assertValueEquals(siBool, "b", signExtend(0, 1));
            assertValueEquals(siBool, "c", signExtend(1, 1));
            assertValueEquals(siBool, "d", signExtend(0, 1));
            assertValueEquals(siBool, "e", signExtend(1, 1));
            assertValueEquals(siBool, "f", signExtend(0, 1));
            assertValueEquals(siBool, "g", signExtend(1, 1));
            assertValueEquals(siBool, "h", signExtend(0, 1));

            DebugValue uiTriple = locals.getProperty("uiTriple");
            assertNotNull(uiTriple);
            assertValueEquals(uiTriple, "a", 0b000);
            assertValueEquals(uiTriple, "b", 0b001);
            assertValueEquals(uiTriple, "c", 0b010);
            assertValueEquals(uiTriple, "d", 0b011);
            assertValueEquals(uiTriple, "e", 0b100);
            assertValueEquals(uiTriple, "f", 0b101);
            assertValueEquals(uiTriple, "g", 0b110);
            assertValueEquals(uiTriple, "h", 0b111);

            DebugValue siTriple = locals.getProperty("siTriple");
            assertNotNull(siTriple);
            assertValueEquals(siTriple, "a", signExtend(0b000, 3));
            assertValueEquals(siTriple, "b", signExtend(0b001, 3));
            assertValueEquals(siTriple, "c", signExtend(0b010, 3));
            assertValueEquals(siTriple, "d", signExtend(0b011, 3));
            assertValueEquals(siTriple, "e", signExtend(0b100, 3));
            assertValueEquals(siTriple, "f", signExtend(0b101, 3));
            assertValueEquals(siTriple, "g", signExtend(0b110, 3));
            assertValueEquals(siTriple, "h", signExtend(0b111, 3));

            DebugValue ui48Long = locals.getProperty("ui48Long");
            assertNotNull(ui48Long);
            assertValueEquals(ui48Long, "a", Long.toUnsignedString(140737488355328L));
            assertValueEquals(ui48Long, "b", Long.toUnsignedString(1L));
            assertValueEquals(ui48Long, "c", Long.toUnsignedString(0L));
            assertValueEquals(ui48Long, "d", Long.toUnsignedString(211106232532992L));
            assertValueEquals(ui48Long, "e", Long.toUnsignedString(150119987579016L));
            assertValueEquals(ui48Long, "f", Long.toUnsignedString(18764998447377L));
            assertValueEquals(ui48Long, "g", Long.toUnsignedString(56294995342134L));
            assertValueEquals(ui48Long, "h", Long.toUnsignedString(225179981368521L));

            DebugValue si48Long = locals.getProperty("si48Long");
            assertNotNull(si48Long);
            assertValueEquals(si48Long, "a", signExtend(140737488355328L, 48));
            assertValueEquals(si48Long, "b", signExtend(1L, 48));
            assertValueEquals(si48Long, "c", signExtend(0L, 48));
            assertValueEquals(si48Long, "d", signExtend(211106232532992L, 48));
            assertValueEquals(si48Long, "e", signExtend(150119987579016L, 48));
            assertValueEquals(si48Long, "f", signExtend(18764998447377L, 48));
            assertValueEquals(si48Long, "g", signExtend(56294995342134L, 48));
            assertValueEquals(si48Long, "h", signExtend(225179981368521L, 48));
        });
        exitFunction(i);
        runTest("bit-fields", i);
    }

    @Test
    public void testBooleans() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 55, "main");
        checkLocals(i, 68, locals -> {
            assertValueEquals(locals, "a", true);
            assertValueEquals(locals, "b", false);

            DebugValue bs = locals.getProperty("bs");
            assertNotNull(bs);
            assertValueEquals(bs, "a", true);
            assertValueEquals(bs, "b", false);
            assertValueEquals(bs, "c", true);
            assertValueEquals(bs, "d", false);
            assertValueEquals(bs, "e", true);
            assertValueEquals(bs, "f", false);
            assertValueEquals(bs, "g", true);
            assertValueEquals(bs, "h", false);
        });
        runTest("booleans", i);
    }

    @Test
    public void testClasses() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 188, "main");

        enterFunction(i, 54, "SimpleClass");
        checkLocals(i, 57, locals -> {
            assertValueEquals(locals, "_a", 7);
            assertValueEquals(locals, "_b", 28L);
            assertValueEquals(locals, "_c", 302.4);

            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            assertValueEquals(self, "a", 7);
            assertValueEquals(self, "b", 28L);
            assertValueEquals(self, "c", 302.4);
        });
        exitFunction(i);

        checkLocals(i, 189, locals -> {
            DebugValue a = locals.getProperty("a");
            assertNotNull(a);
            assertValueEquals(a, "a", 7);
            assertValueEquals(a, "b", 28L);
            assertValueEquals(a, "c", 302.4);
        });

        enterFunction(i, 77, "print");
        checkLocals(i, 77, locals -> {
            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            assertValueEquals(self, "a", 7);
            assertValueEquals(self, "b", 28L);
            assertValueEquals(self, "c", 302.4);
        });
        exitFunction(i);

        enterFunction(i, 62, "SimpleClass");
        checkLocals(i, 63, locals -> {
            assertValueEquals(locals, "_a", 7);
            assertValueEquals(locals, "_b", 28L);

            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            assertValueEquals(self, "a", 7);
            assertValueEquals(self, "b", 28L);
            assertValueEquals(self, "c", 305.7);
        });
        exitFunction(i);

        checkLocals(i, 192, locals -> {
            DebugValue b = locals.getProperty("b");
            assertNotNull(b);
            assertValueEquals(b, "a", 7);
            assertValueEquals(b, "b", 28L);
            assertValueEquals(b, "c", 305.7);
        });

        enterFunction(i, 77, "print");
        checkLocals(i, 77, locals -> {
            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            assertValueEquals(self, "a", 7);
            assertValueEquals(self, "b", 28L);
            assertValueEquals(self, "c", 305.7);
        });
        exitFunction(i);

        enterFunction(i, 68, "SimpleClass");
        checkLocals(i, 72, locals -> {
            assertValueEquals(locals, "_a", 8);
            assertValueEquals(locals, "_b", 32L);
            assertValueEquals(locals, "_c", 172.8);

            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            assertValueEquals(self, "a", 8);
            assertValueEquals(self, "b", 32L);
            assertValueEquals(self, "c", 345.6);
        });
        exitFunction(i);

        checkLocals(i, 195, locals -> {
            DebugValue cPtr = locals.getProperty("c");
            assertNotNull(cPtr);
            DebugValue c = cPtr.getProperty("*c");
            assertNotNull(c);
            assertValueEquals(c, "a", 8);
            assertValueEquals(c, "b", 32L);
            assertValueEquals(c, "c", 345.6);
        });

        enterFunction(i, 77, "print");
        checkLocals(i, 77, locals -> {
            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            assertValueEquals(self, "a", 8);
            assertValueEquals(self, "b", 32L);
            assertValueEquals(self, "c", 345.6);
        });
        exitFunction(i);

        enterFunction(i, 90, "Point");
        checkLocals(i, 90, locals -> {
            assertValueEquals(locals, "_x", 1);
            assertValueEquals(locals, "_x", 1);

            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            assertValueEquals(self, "x", 1);
            assertValueEquals(self, "y", 1);
        });
        exitFunction(i);

        enterFunctionUnchecked(i, "Circle");
        enterFunction(i, 122, "Shape");
        checkLocals(i, 122, locals -> {
            DebugValue center = locals.getProperty("_center");
            assertNotNull(center);
            assertValueEquals(center, "x", 1);
            assertValueEquals(center, "y", 1);

            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            DebugValue selfCenter = self.getProperty("center");
            assertNotNull(selfCenter);
            assertValueEquals(selfCenter, "x", 1);
            assertValueEquals(selfCenter, "y", 1);
        });
        exitFunction(i);

        checkLocals(i, 149, locals -> {
            DebugValue center = locals.getProperty("_center");
            assertNotNull(center);
            assertValueEquals(center, "x", 1);
            assertValueEquals(center, "y", 1);
            assertValueEquals(locals, "_radius", 3);

            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            DebugValue selfCenter = self.getProperty("center");
            assertNotNull(selfCenter);
            assertValueEquals(selfCenter, "x", 1);
            assertValueEquals(selfCenter, "y", 1);

            assertValueEquals(self, "radius", 3);
        });
        exitFunction(i);

        checkLocals(i, 199, locals -> {
            DebugValue myCircle = locals.getProperty("myCircle");
            assertNotNull(myCircle);
            DebugValue center = myCircle.getProperty("center");
            assertNotNull(center);
            assertValueEquals(center, "x", 1);
            assertValueEquals(center, "y", 1);
            assertValueEquals(myCircle, "radius", 3);
        });

        enterFunction(i, 127, "moveUp");

        checkLocals(i, 128, locals -> {
            assertValueEquals(locals, "offset", 10);
            assertValueEquals(locals, "newY", 11);
            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            DebugValue center = self.getProperty("center");
            assertNotNull(center);
            assertValueEquals(center, "y", 1);
        });

        checkLocals(i, 129, locals -> {
            assertValueEquals(locals, "offset", 10);
            assertValueEquals(locals, "newY", 11);

            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            DebugValue center = self.getProperty("center");
            assertNotNull(center);
            assertValueEquals(center, "y", 11);
        });

        exitFunction(i);

        enterFunction(i, 90, "Point");
        exitFunction(i);

        enterFunctionUnchecked(i, "Square");
        enterFunctionUnchecked(i, "Rectangle");
        enterFunction(i, 122, "Shape");

        checkLocals(i, 122, locals -> {
            DebugValue center = locals.getProperty("_center");
            assertNotNull(center);
            assertValueEquals(center, "x", 3);
            assertValueEquals(center, "y", 5);
        });
        exitFunction(i);

        checkLocals(i, 164, locals -> {
            DebugValue center = locals.getProperty("_center");
            assertNotNull(center);
            assertValueEquals(center, "x", 3);
            assertValueEquals(center, "y", 5);
            assertValueEquals(locals, "_width", 5);
            assertValueEquals(locals, "_height", 5);
        });
        exitFunction(i);

        checkLocals(i, 173, locals -> {
            DebugValue center = locals.getProperty("_center");
            assertNotNull(center);
            assertValueEquals(center, "x", 3);
            assertValueEquals(center, "y", 5);
            assertValueEquals(locals, "length", 5);

            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            DebugValue selfCenter = self.getProperty("center");
            assertNotNull(selfCenter);
            assertValueEquals(selfCenter, "x", 3);
            assertValueEquals(selfCenter, "y", 5);
            assertValueEquals(self, "width", 5);
            assertValueEquals(self, "height", 5);
        });
        exitFunction(i);

        enterFunction(i, 134, "moveLeft");
        checkLocals(i, 135, locals -> {
            assertValueEquals(locals, "offset", -3);
            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            DebugValue center = self.getProperty("center");
            assertNotNull(center);
            assertValueEquals(center, "x", 3);
        });
        checkLocals(i, 136, locals -> {
            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            DebugValue center = self.getProperty("center");
            assertNotNull(center);
            assertValueEquals(center, "x", 0);
        });
        exitFunction(i);

        enterFunction(i, 90, "Point");
        exitFunction(i);

        enterFunctionUnchecked(i, "SimpleSquare");
        enterFunction(i, 68, "SimpleClass");
        exitFunction(i);
        enterFunctionUnchecked(i, "Square");
        enterFunctionUnchecked(i, "Rectangle");
        enterFunction(i, 122, "Shape");

        checkLocals(i, 122, locals -> {
            DebugValue center = locals.getProperty("_center");
            assertNotNull(center);
            assertValueEquals(center, "x", 4);
            assertValueEquals(center, "y", 2);
        });
        exitFunction(i);

        checkLocals(i, 164, locals -> {
            DebugValue center = locals.getProperty("_center");
            assertNotNull(center);
            assertValueEquals(center, "x", 4);
            assertValueEquals(center, "y", 2);
            assertValueEquals(locals, "_width", 19);
            assertValueEquals(locals, "_height", 19);
        });
        exitFunction(i);

        checkLocals(i, 173, locals -> {
            DebugValue center = locals.getProperty("_center");
            assertNotNull(center);
            assertValueEquals(center, "x", 4);
            assertValueEquals(center, "y", 2);
            assertValueEquals(locals, "length", 19);
        });

        exitFunction(i);

        checkLocals(i, 182, locals -> {
            DebugValue center = locals.getProperty("center");
            assertNotNull(center);
            assertValueEquals(center, "x", 4);
            assertValueEquals(center, "y", 2);
            assertValueEquals(locals, "length", 19);
            assertValueEquals(locals, "a", 43);

            DebugValue selfPtr = locals.getProperty("this");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*this");
            assertNotNull(self);
            DebugValue selfCenter = self.getProperty("center");
            assertNotNull(selfCenter);
            assertValueEquals(selfCenter, "x", 4);
            assertValueEquals(selfCenter, "y", 2);
            assertValueEquals(self, "width", 19);
            assertValueEquals(self, "height", 19);
            assertValueEquals(self, "a", 43);
            assertValueEquals(self, "b", 172L);
            assertValueEquals(self, "c", 1857.6);
        });
        exitFunction(i);

        runTest("classes", i);
    }

    @Test
    public void testDefaultParameters() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 53, "main");
        enterFunction(i, 47, "printCity");
        checkLocals(i, 47, locals -> {
            assertValueEquals(locals, "name", "Berlin");
            assertValueEquals(locals, "iterations", 1);
        });
        exitFunction(i);
        moveTo(i, 54);
        enterFunction(i, 47, "printCity");
        checkLocals(i, 47, locals -> {
            assertValueEquals(locals, "name", "Stockholm");
            assertValueEquals(locals, "iterations", 2);
        });
        exitFunction(i);
        moveTo(i, 55);
        enterFunction(i, 47, "printCity");
        checkLocals(i, 47, locals -> {
            assertValueEquals(locals, "name", "Vienna");
            assertValueEquals(locals, "iterations", 1);
        });
        exitFunction(i);
        exitFunction(i);
        runTest("default-parameters", i);
    }

    @Test
    public void testMultiInheritance() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 57, "main");
        checkLocals(i, 57, locals -> {
            DebugValue c = locals.getProperty("c");
            assertNotNull(c);
            assertValueEquals(c, "x", 0);
            assertValueEquals(c, "y", 0);
        });
        checkLocals(i, 59, locals -> {
            DebugValue c = locals.getProperty("c");
            assertNotNull(c);
            assertValueEquals(c, "x", 5);
            assertValueEquals(c, "y", 10);
        });
        exitFunction(i);
        runTest("multi-inheritance", i);
    }

    @Test
    public void testObjectPointers() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 77, "main");
        checkLocals(i, 80, locals -> {
            DebugValue localObj = locals.getProperty("localObj");
            assertNotNull(localObj);
            assertValueEquals(localObj, "a", 16);
            assertValueEquals(localObj, "b", 3.2f);
            assertValueEquals(localObj, "c", 4.657);
            assertValueEquals(localObj, "d", 125604585);
            assertValueEquals(localObj, "e", "'e' 101");
            DebugValue fPtr = localObj.getProperty("f");
            assertNotNull(fPtr);
            assertTrue(fPtr.isArray());
            List<DebugValue> f = fPtr.getArray();
            assertArrayElementEquals(f, 0, (short) -32768);
            assertArrayElementEquals(f, 1, (short) -1);
            assertArrayElementEquals(f, 2, (short) 32767);

            DebugValue localPtr = locals.getProperty("localPtr");
            assertNotNull(localPtr);
            DebugValue l = localPtr.getProperty("*localPtr");
            assertNotNull(l);
            assertValueEquals(l, "a", 16);
            assertValueEquals(l, "b", 3.2f);
            assertValueEquals(l, "c", 4.657);
            assertValueEquals(l, "d", 125604585);
            assertValueEquals(l, "e", "'e' 101");
            fPtr = l.getProperty("f");
            assertNotNull(fPtr);
            assertTrue(fPtr.isArray());
            f = fPtr.getArray();
            assertArrayElementEquals(f, 0, (short) -32768);
            assertArrayElementEquals(f, 1, (short) -1);
            assertArrayElementEquals(f, 2, (short) 32767);
        });
        checkGlobals(i, 80, globals -> {
            DebugValue globalObj = globals.getProperty("globalObj");
            assertNotNull(globalObj);
            assertValueEquals(globalObj, "a", 16);
            assertValueEquals(globalObj, "b", 3.2f);
            assertValueEquals(globalObj, "c", 4.657);
            assertValueEquals(globalObj, "d", 125604585);
            assertValueEquals(globalObj, "e", "'e' 101");
            DebugValue fPtr = globalObj.getProperty("f");
            assertNotNull(fPtr);
            assertTrue(fPtr.isArray());
            List<DebugValue> f = fPtr.getArray();
            assertArrayElementEquals(f, 0, (short) -32768);
            assertArrayElementEquals(f, 1, (short) -1);
            assertArrayElementEquals(f, 2, (short) 32767);

            DebugValue globalPtr = globals.getProperty("globalPtr");
            assertNotNull(globalPtr);
            DebugValue g = globalPtr.getProperty("*globalPtr");
            assertNotNull(g);
            assertValueEquals(g, "a", 16);
            assertValueEquals(g, "b", 3.2f);
            assertValueEquals(g, "c", 4.657);
            assertValueEquals(g, "d", 125604585);
            assertValueEquals(g, "e", "'e' 101");
            fPtr = g.getProperty("f");
            assertNotNull(fPtr);
            assertTrue(fPtr.isArray());
            f = fPtr.getArray();
            assertArrayElementEquals(f, 0, (short) -32768);
            assertArrayElementEquals(f, 1, (short) -1);
            assertArrayElementEquals(f, 2, (short) 32767);

        });
        enterFunction(i, 66, "myMethod");
        exitFunction(i);
        enterFunction(i, 69, "myStaticMethod");
        checkLocals(i, 69, locals -> {
            DebugValue myClassPtr = locals.getProperty("myClass");
            assertNotNull(myClassPtr);
            DebugValue myClass = myClassPtr.getProperty("*myClass");
            assertNotNull(myClass);
            assertValueEquals(myClass, "a", 16);
        });
        exitFunction(i);
        runTest("object-pointers", i);
    }

    @Test
    public void testPointers() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 46, "main");
        checkLocals(i, 48, locals -> {
            DebugValue ptr = locals.getProperty("ptr");
            assertNotNull(ptr);
            assertValueEquals(ptr, "*ptr", "'a' 97");
        });
        moveTo(i, 50);
        checkLocals(i, 48, locals -> {
            DebugValue ptr = locals.getProperty("ptr");
            assertNotNull(ptr);
            assertValueEquals(ptr, "*ptr", "'b' 98");
        });
        moveTo(i, 50);
        checkLocals(i, 48, locals -> {
            DebugValue ptr = locals.getProperty("ptr");
            assertNotNull(ptr);
            assertValueEquals(ptr, "*ptr", "'c' 99");
        });
        checkLocals(i, 53, locals -> {
            DebugValue ptr = locals.getProperty("ptr");
            assertNotNull(ptr);
            assertValueEquals(ptr, "*ptr", "null");
        });
        exitFunction(i);
        runTest("pointers", i);
    }

    @Test
    public void testPointerToMember() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 55, "main");
        checkLocals(i, 59, locals -> {
            DebugValue c = locals.getProperty("c");
            assertNotNull(c);
            assertValueEquals(c, "value", 5);
        });
        checkLocals(i, 60, locals -> {
            DebugValue c = locals.getProperty("c");
            assertNotNull(c);
            assertValueEquals(c, "value", 7);
        });
        runTest("pointer-to-member", i);
    }

    @Test
    public void testPolymorphism() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 89, "main");
        checkLocals(i, 89, locals -> {
            assertValueEquals(locals, "a", "Animal*");
            DebugValue a = locals.getProperty("a");
            assertNotNull(a);
            assertValueEquals(a, "*a", "Animal");
            DebugValue aPtr = a.getProperty("*a");
            assertNotNull(aPtr);
            assertStringEquals(aPtr, "name", "B");
        });
        checkLocals(i, 90, locals -> {
            assertValueEquals(locals, "a", "Animal*");
            DebugValue a = locals.getProperty("a");
            assertNotNull(a);
            assertValueEquals(a, "*a", "Animal");
            DebugValue aPtr = a.getProperty("*a");
            assertNotNull(aPtr);
            assertStringEquals(aPtr, "name", "A");
        });
        exitFunction(i);
        runTest("polymorphism", i);
    }

    @Test
    public void testPrimitives() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 53, "main");
        checkLocals(i, 53, locals -> {
            assertValueEquals(locals, "i", 5);
            assertValueEquals(locals, "f", 3.14f);
            assertValueEquals(locals, "d", 9.1);
            assertValueEquals(locals, "c", "'a' 97");
            assertValueEquals(locals, "b", true);
            assertStringEquals(locals, "s", "abc");
        });
        exitFunction(i);
        runTest("primitives", i);
    }

    @Test
    public void testScopes() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 83, "main");
        checkGlobals(i, 84, globals -> {
            assertValueEquals(globals, "MyNamespace::nextID", 72);
            assertValueEquals(globals, "globalX", 512);
            assertValueEquals(globals, "lastId", -1);
        });
        checkLocals(i, 84, locals -> assertValueEquals(locals, "x", 0));
        enterFunction(i, 78, "getX");
        checkGlobals(i, 79, globals -> assertValueEquals(globals, "globalX", 513));
        checkLocals(i, 79, locals -> assertValueEquals(locals, "x", 512));
        exitFunction(i);
        checkLocals(i, 86, locals -> assertValueEquals(locals, "x", 512));
        checkGlobals(i, 90, globals -> {
            assertValueEquals(globals, "lastId", 72);
            assertValueEquals(globals, "MyNamespace::nextID", 73);
        });
        checkLocals(i, 90, locals -> assertValueEquals(locals, "x", 72));
        checkLocals(i, 92, locals -> assertValueEquals(locals, "x", 512));
        runTest("scopes", i);
    }

    @Test
    public void testStrings() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 47, "main");
        checkLocals(i, 50, locals -> {
            assertStringEquals(locals, "str1", "Hello");
            assertStringEquals(locals, "str2", "World, this is a large string.");
            DebugValue strPtr = locals.getProperty("strPtr");
            assertNotNull(strPtr);
            assertStringEquals(strPtr, "*strPtr", "Hello");
        });
        exitFunction(i);
        runTest("strings", i);
    }

    @Test
    public void testTemplates() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 57, "main");
        enterFunction(i, 47, "C");
        checkLocals(i, 47, locals -> assertValueEquals(locals, "v", 5));
        exitFunction(i);
        moveTo(i, 58);
        enterFunction(i, 47, "C");
        checkLocals(i, 47, locals -> assertValueEquals(locals, "v", 3.14));
        exitFunction(i);
        enterFunction(i, 53, "min<int>");
        exitFunction(i);
        moveTo(i, 60);
        enterFunction(i, 53, "min<double>");
        exitFunction(i);
        checkLocals(i, 61, locals -> {
            DebugValue cInt = locals.getProperty("cInt");
            assertNotNull(cInt);
            assertValueEquals(cInt, "value", 5);
            DebugValue cDouble = locals.getProperty("cDouble");
            assertNotNull(cDouble);
            assertValueEquals(cDouble, "value", 3.14);
            assertValueEquals(locals, "m", 1);
            assertValueEquals(locals, "d", 1.2);
        });
        runTest("templates", i);
    }
}
