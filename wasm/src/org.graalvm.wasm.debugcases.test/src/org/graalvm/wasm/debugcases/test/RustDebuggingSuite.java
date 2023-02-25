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
import static org.graalvm.wasm.debugcases.test.DebugAssert.assertHidden;
import static org.graalvm.wasm.debugcases.test.DebugAssert.assertPointersEquals;
import static org.graalvm.wasm.debugcases.test.DebugAssert.assertStringEquals;
import static org.graalvm.wasm.debugcases.test.DebugAssert.assertValueEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RustDebuggingSuite extends DebuggingSuiteBase {
    @Override
    protected String resourceDir() {
        return "rust";
    }

    @Test
    public void testBindings() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 43, "main");
        checkLocals(i, 43, locals -> {
            assertHidden(locals, "a");
            assertHidden(locals, "b");
            assertHidden(locals, "c");
            assertHidden(locals, "d");
            assertHidden(locals, "e");
            assertHidden(locals, "f");
            assertHidden(locals, "g");
            assertHidden(locals, "h");
            assertHidden(locals, "i");
            assertHidden(locals, "j");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 44, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertHidden(locals, "b");
            assertHidden(locals, "c");
            assertHidden(locals, "d");
            assertHidden(locals, "e");
            assertHidden(locals, "f");
            assertHidden(locals, "g");
            assertHidden(locals, "h");
            assertHidden(locals, "i");
            assertHidden(locals, "j");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 45, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertHidden(locals, "c");
            assertHidden(locals, "d");
            assertHidden(locals, "e");
            assertHidden(locals, "f");
            assertHidden(locals, "g");
            assertHidden(locals, "h");
            assertHidden(locals, "i");
            assertHidden(locals, "j");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 46, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertHidden(locals, "d");
            assertHidden(locals, "e");
            assertHidden(locals, "f");
            assertHidden(locals, "g");
            assertHidden(locals, "h");
            assertHidden(locals, "i");
            assertHidden(locals, "j");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 47, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertHidden(locals, "e");
            assertHidden(locals, "f");
            assertHidden(locals, "g");
            assertHidden(locals, "h");
            assertHidden(locals, "i");
            assertHidden(locals, "j");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 48, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertHidden(locals, "f");
            assertHidden(locals, "g");
            assertHidden(locals, "h");
            assertHidden(locals, "i");
            assertHidden(locals, "j");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 49, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertHidden(locals, "g");
            assertHidden(locals, "h");
            assertHidden(locals, "i");
            assertHidden(locals, "j");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 50, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64);
            assertHidden(locals, "h");
            assertHidden(locals, "i");
            assertHidden(locals, "j");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 51, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64);
            assertValueEquals(locals, "h", "64");
            assertHidden(locals, "i");
            assertHidden(locals, "j");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 52, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64L);
            assertValueEquals(locals, "h", "64");
            assertValueEquals(locals, "i", "unsupported base type");
            assertHidden(locals, "j");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 53, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64L);
            assertValueEquals(locals, "h", "64");
            assertValueEquals(locals, "i", "unsupported base type");
            assertValueEquals(locals, "j", "unsupported base type");
            assertHidden(locals, "k");
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 54, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64L);
            assertValueEquals(locals, "h", "64");
            assertValueEquals(locals, "i", "unsupported base type");
            assertValueEquals(locals, "j", "unsupported base type");
            assertValueEquals(locals, "k", 10);
            assertHidden(locals, "l");

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 56, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64L);
            assertValueEquals(locals, "h", "64");
            assertValueEquals(locals, "i", "unsupported base type");
            assertValueEquals(locals, "j", "unsupported base type");
            assertValueEquals(locals, "k", 10);
            assertValueEquals(locals, "l", 32L);

            assertHidden(locals, "m");

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 58, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64L);
            assertValueEquals(locals, "h", "64");
            assertValueEquals(locals, "i", "unsupported base type");
            assertValueEquals(locals, "j", "unsupported base type");
            assertValueEquals(locals, "k", 10);
            assertValueEquals(locals, "l", 32L);

            assertValueEquals(locals, "m", 65);

            assertHidden(locals, "n");
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 59, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64L);
            assertValueEquals(locals, "h", "64");
            assertValueEquals(locals, "i", "unsupported base type");
            assertValueEquals(locals, "j", "unsupported base type");
            assertValueEquals(locals, "k", 10);
            assertValueEquals(locals, "l", 32L);

            assertValueEquals(locals, "m", 65);

            assertValueEquals(locals, "n", true);
            assertHidden(locals, "o");
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 60, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64L);
            assertValueEquals(locals, "h", "64");
            assertValueEquals(locals, "i", "unsupported base type");
            assertValueEquals(locals, "j", "unsupported base type");
            assertValueEquals(locals, "k", 10);
            assertValueEquals(locals, "l", 32L);

            assertValueEquals(locals, "m", 65);

            assertValueEquals(locals, "n", true);
            assertValueEquals(locals, "o", false);
            assertHidden(locals, "p");

            assertHidden(locals, "s");
        });
        checkLocals(i, 62, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64L);
            assertValueEquals(locals, "h", "64");
            assertValueEquals(locals, "i", "unsupported base type");
            assertValueEquals(locals, "j", "unsupported base type");
            assertValueEquals(locals, "k", 10);
            assertValueEquals(locals, "l", 32L);

            assertValueEquals(locals, "m", 65);

            assertValueEquals(locals, "n", true);
            assertValueEquals(locals, "o", false);
            assertValueEquals(locals, "p", "a");

            assertHidden(locals, "s");
        });
        checkLocals(i, 64, locals -> {
            assertValueEquals(locals, "a", (byte) -8);
            assertValueEquals(locals, "b", 8);
            assertValueEquals(locals, "c", (short) -16);
            assertValueEquals(locals, "d", 16);
            assertValueEquals(locals, "e", -32);
            assertValueEquals(locals, "f", 32L);
            assertValueEquals(locals, "g", -64L);
            assertValueEquals(locals, "h", "64");
            assertValueEquals(locals, "i", "unsupported base type");
            assertValueEquals(locals, "j", "unsupported base type");
            assertValueEquals(locals, "k", 10);
            assertValueEquals(locals, "l", 32L);

            assertValueEquals(locals, "m", 65);

            assertValueEquals(locals, "n", true);
            assertValueEquals(locals, "o", false);
            assertValueEquals(locals, "p", "a");

            assertStringEquals(locals, "s", "Hello, World");
        });
        exitFunction(i);
        runTest("bindings", "bindings", i);
    }

    @Test
    public void testCompoundTypes() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 43, "main");
        checkLocals(i, 49, locals -> {
            DebugValue tup = locals.getProperty("tup");
            assertNotNull(tup);
            assertValueEquals(tup, "__0", 500);
            assertValueEquals(tup, "__1", 6.4);
            assertValueEquals(tup, "__2", 1);
            assertValueEquals(locals, "a", 500);
            assertValueEquals(locals, "b", 6.4);
            assertValueEquals(locals, "c", 1);
            assertValueEquals(locals, "x", 500);
            assertValueEquals(locals, "y", 6.4);
            assertValueEquals(locals, "z", 1);
        });
        checkLocals(i, 50, locals -> {
            DebugValue t = locals.getProperty("t");
            assertNotNull(t);
            assertValueEquals(t, "__0", 100);
            DebugValue t1 = t.getProperty("__1");
            assertNotNull(t1);
            assertValueEquals(t1, "__0", 12);
            assertValueEquals(t1, "__1", true);
        });
        checkLocals(i, 58, locals -> {
            DebugValue a = locals.getProperty("a");
            assertNotNull(a);
            assertTrue(a.isArray());
            List<DebugValue> aElements = a.getArray();
            assertArrayElementEquals(aElements, 0, 1);
            assertArrayElementEquals(aElements, 1, 2);
            assertArrayElementEquals(aElements, 2, 3);
            assertArrayElementEquals(aElements, 3, 4);
            assertArrayElementEquals(aElements, 4, 5);

            DebugValue b = locals.getProperty("b");
            assertNotNull(b);
            assertTrue(a.isArray());
            List<DebugValue> bElements = b.getArray();
            assertArrayElementEquals(bElements, 0, 3);
            assertArrayElementEquals(bElements, 1, 3);
            assertArrayElementEquals(bElements, 2, 3);
            assertArrayElementEquals(bElements, 3, 3);

            assertValueEquals(locals, "c", 1);

            DebugValue d = locals.getProperty("d");
            assertNotNull(d);
            assertTrue(d.isArray());
            List<DebugValue> dElements = d.getArray();

            DebugValue subD1 = dElements.get(0);
            assertNotNull(subD1);
            assertTrue(subD1.isArray());
            List<DebugValue> subD1Elements = subD1.getArray();
            assertArrayElementEquals(subD1Elements, 0, 1);
            assertArrayElementEquals(subD1Elements, 1, 2);
            assertArrayElementEquals(subD1Elements, 2, 3);

            DebugValue subD2 = dElements.get(1);
            assertNotNull(subD2);
            assertTrue(subD2.isArray());
            List<DebugValue> subD2Elements = subD2.getArray();
            assertArrayElementEquals(subD2Elements, 0, 4);
            assertArrayElementEquals(subD2Elements, 1, 5);
            assertArrayElementEquals(subD2Elements, 2, 6);
        });
        exitFunction(i);
        runTest("compound_types", "compound_types", i);
    }

    @Test
    public void testControlFlow() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 43, "main");
        checkLocals(i, 48, locals -> assertValueEquals(locals, "n", 5));
        checkLocals(i, 56, locals -> assertValueEquals(locals, "count", 1L));
        moveTo(i, 59);
        checkLocals(i, 56, locals -> assertValueEquals(locals, "count", 2L));
        checkLocals(i, 66, locals -> assertValueEquals(locals, "n", 1));
        moveTo(i, 65);
        checkLocals(i, 66, locals -> assertValueEquals(locals, "n", 2));
        moveTo(i, 65);
        checkLocals(i, 70, locals -> assertValueEquals(locals, "i", 1));
        moveTo(i, 69);
        checkLocals(i, 70, locals -> assertValueEquals(locals, "i", 2));
        moveTo(i, 69);
        checkLocals(i, 70, locals -> assertValueEquals(locals, "i", 3));
        checkLocals(i, 73, locals -> assertValueEquals(locals, "n", 3));
        moveTo(i, 76);
        checkLocals(i, 82, locals -> {
            DebugValue number = locals.getProperty("number");
            assertNotNull(number);
            assertValueEquals(number, "__0", 7);
        });
        moveTo(i, 83);
        exitFunction(i);
        runTest("control_flow", "control_flow", i);
    }

    @Test
    public void testEnums() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 69, "main");
        checkLocals(i, 73, locals -> {
            assertValueEquals(locals, "a", "Expression::Nop");
            assertValueEquals(locals, "b", "Expression::Const");
            assertValueEquals(locals, "c", "Expression::Add");
            assertValueEquals(locals, "d", "Expression::Print");

            DebugValue b = locals.getProperty("b");
            assertNotNull(b);
            assertValueEquals(b, "__0", 5);

            DebugValue c = locals.getProperty("c");
            assertNotNull(c);
            assertValueEquals(c, "lhs", 12);
            assertValueEquals(c, "rhs", 1);
        });
        enterFunction(i, 53, "eval");
        checkLocals(i, 53, locals -> assertValueEquals(locals, "e", "Expression::Nop"));
        exitFunction(i);
        moveTo(i, 74);
        enterFunction(i, 53, "eval");
        checkLocals(i, 53, locals -> assertValueEquals(locals, "e", "Expression::Const"));
        exitFunction(i);
        moveTo(i, 75);
        enterFunction(i, 53, "eval");
        checkLocals(i, 53, locals -> assertValueEquals(locals, "e", "Expression::Add"));
        exitFunction(i);
        moveTo(i, 76);
        enterFunction(i, 53, "eval");
        checkLocals(i, 53, locals -> assertValueEquals(locals, "e", "Expression::Print"));
        exitFunction(i);
        checkLocals(i, 80, locals -> {
            assertValueEquals(locals, "x", "Number::Zero");
            assertValueEquals(locals, "y", "Number::One");
            assertValueEquals(locals, "z", "Number::Two");
        });
        exitFunction(i);
        runTest("enums", "enums", i);
    }

    @Test
    public void testGenerics() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 59, "main");
        enterFunction(i, 48, "min<i32>");
        exitFunction(i);
        checkLocals(i, 61, locals -> assertPointersEquals(locals, "result", "&i32", "*result", null));
        enterFunction(i, 48, "min<char>");
        exitFunction(i);
        checkLocals(i, 64, locals -> assertPointersEquals(locals, "result", "&char", "*result", null));
        checkLocals(i, 69, locals -> {
            DebugValue a = locals.getProperty("a");
            assertNotNull(a);
            assertValueEquals(a, "x", 5);
            assertValueEquals(a, "y", 12);
            assertValueEquals(locals, "x", 5);
            assertValueEquals(locals, "y", 12);
        });
        checkLocals(i, 73, locals -> {
            DebugValue b = locals.getProperty("b");
            assertNotNull(b);
            assertValueEquals(b, "x", 3.14);
            assertValueEquals(b, "y", 4.0);
            assertValueEquals(locals, "x", 3.14);
            assertValueEquals(locals, "y", 4.0);
        });
        exitFunction(i);
        runTest("generics", "generics", i);
    }

    @Test
    public void testLambdas() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 57, "main");

        moveTo(i, 59);
        moveTo(i, 43);
        moveTo(i, 53);
        checkLocals(i, 53, locals -> assertValueEquals(locals, "value", 2));

        moveTo(i, 60);
        moveTo(i, 43);
        moveTo(i, 60);
        checkLocals(i, 60, locals -> assertValueEquals(locals, "x", 2));

        moveTo(i, 61);
        moveTo(i, 43);
        moveTo(i, 61);
        checkLocals(i, 61, locals -> {
            assertValueEquals(locals, "x", 2);
            assertValueEquals(locals, "y", 5);
        });

        moveTo(i, 63);
        moveTo(i, 46);
        moveTo(i, 53);
        checkLocals(i, 53, locals -> assertValueEquals(locals, "value", 2));

        moveTo(i, 64);
        moveTo(i, 46);
        moveTo(i, 64);
        checkLocals(i, 64, locals -> assertValueEquals(locals, "x", 2));

        moveTo(i, 65);
        moveTo(i, 46);
        moveTo(i, 65);
        checkLocals(i, 65, locals -> {
            assertValueEquals(locals, "x", 2);
            assertValueEquals(locals, "y", 5);
        });

        moveTo(i, 67);
        moveTo(i, 49);
        moveTo(i, 53);
        checkLocals(i, 53, locals -> assertValueEquals(locals, "value", 2));

        moveTo(i, 68);
        moveTo(i, 49);
        moveTo(i, 68);
        checkLocals(i, 68, locals -> assertValueEquals(locals, "x", 2));

        exitFunction(i);
        runTest("lambdas", "lambdas", i);
    }

    @Test
    public void testMultiFile() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 49, "main");

        moveTo(i, 51);
        enterFile(i, 43, "calc.rs");
        enterFunction(i, 43, "min");
        exitFunction(i);

        enterFile(i, 52, "main.rs");
        enterFile(i, 51, "calc.rs");
        enterFunction(i, 51, "max");
        exitFunction(i);

        enterFile(i, 53, "main.rs");
        enterFile(i, 43, "print.rs");
        enterFunction(i, 43, "print_i32");
        exitFunction(i);

        enterFile(i, 54, "main.rs");
        enterFile(i, 43, "print.rs");
        enterFunction(i, 43, "print_i32");
        exitFunction(i);

        enterFile(i, 55, "main.rs");
        enterFile(i, 59, "calc.rs");
        enterFunction(i, 59, "sum");
        exitFunction(i);
        enterFile(i, 43, "print.rs");
        enterFunction(i, 43, "print_i32");
        exitFunction(i);

        exitFunction(i);

        runTest("multi_file", "multi_file", i);
    }

    @Test
    public void testScopes() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 71, "main");
        checkLocals(i, 73, locals -> assertValueEquals(locals, "x", 0));
        checkLocals(i, 74, locals -> assertValueEquals(locals, "x", 5));
        checkLocals(i, 76, locals -> assertValueEquals(locals, "x", 0));
        enterFunction(i, 50, "new");
        checkLocals(i, 50, locals -> {
            assertStringEquals(locals, "first_name", "First");
            assertStringEquals(locals, "last_name", "Last");
            assertValueEquals(locals, "age", 24);
        });
        exitFunction(i);
        enterFunction(i, 58, "name");
        checkLocals(i, 58, locals -> {
            DebugValue selfPtr = locals.getProperty("self");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*self");
            assertNotNull(self);
            assertValueEquals(self, "age", 24);
            assertStringEquals(self, "first_name", "First");
            assertStringEquals(self, "last_name", "Last");
        });
        exitFunction(i);
        enterFunction(i, 66, "print_age");
        checkLocals(i, 66, locals -> {
            DebugValue self = locals.getProperty("self");
            assertNotNull(self);
            assertValueEquals(self, "age", 24);
            assertStringEquals(self, "first_name", "First");
            assertStringEquals(self, "last_name", "Last");
        });
        exitFunction(i);
        exitFunction(i);
        runTest("scopes", "scopes", i);
    }

    @Test
    public void testStrings() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 43, "main");
        checkLocals(i, 45, locals -> {
            assertStringEquals(locals, "a", "Text");
            assertValueEquals(locals, "b", "String");
        });
        exitFunction(i);
        runTest("strings", "strings", i);
    }

    @Test
    public void testStructs() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 65, "main");
        checkLocals(i, 69, locals -> {
            assertValueEquals(locals, "name", "String");
            assertValueEquals(locals, "age", 24);
            DebugValue p = locals.getProperty("p");
            assertNotNull(p);
            assertValueEquals(p, "name", "String");
            assertValueEquals(p, "age", 24);
        });
        checkLocals(i, 73, locals -> {
            DebugValue point = locals.getProperty("point");
            assertNotNull(point);
            assertValueEquals(point, "x", 10.3f);
            assertValueEquals(point, "y", 0.4f);
        });
        checkLocals(i, 77, locals -> {
            DebugValue bottomRight = locals.getProperty("bottom_right");
            assertNotNull(bottomRight);
            assertValueEquals(bottomRight, "x", 5.2f);
            assertValueEquals(bottomRight, "y", 0.4f);
        });
        checkLocals(i, 95, locals -> {
            assertValueEquals(locals, "left_edge", 10.3f);
            assertValueEquals(locals, "top_edge", 0.4f);

            DebugValue rect = locals.getProperty("_rect");
            assertNotNull(rect);

            DebugValue topLeft = rect.getProperty("top_left");
            assertNotNull(topLeft);
            assertValueEquals(topLeft, "x", 10.3f);
            assertValueEquals(topLeft, "y", 0.4f);

            DebugValue bottomRight = rect.getProperty("bottom_right");
            assertNotNull(bottomRight);
            assertValueEquals(bottomRight, "x", 5.2f);
            assertValueEquals(bottomRight, "y", 0.4f);

            assertValueEquals(locals, "_unit", "Unit");
        });
        checkLocals(i, 99, locals -> {
            DebugValue pair = locals.getProperty("pair");
            assertNotNull(pair);
            assertValueEquals(pair, "__0", 1);
            assertValueEquals(pair, "__1", 0.1f);

            assertValueEquals(locals, "integer", 1);
            assertValueEquals(locals, "decimal", 0.1f);
        });
        exitFunction(i);
        runTest("structs", "structs", i);
    }

    @Test
    public void testTraits() throws IOException {
        final DebugInspector i = createInspector();
        enterFunction(i, 66, "main");
        checkLocals(i, 67, locals -> {
            DebugValue d = locals.getProperty("d");
            assertNotNull(d);
            assertValueEquals(d, "name", "Max");
        });
        enterFunction(i, 62, "f");

        enterFunction(i, 53, "name");
        checkLocals(i, 53, locals -> {
            DebugValue selfPtr = locals.getProperty("self");
            assertNotNull(selfPtr);
            DebugValue self = selfPtr.getProperty("*self");
            assertNotNull(self);
            assertValueEquals(self, "name", "Max");
        });
        exitFunction(i);

        exitFunction(i);
        exitFunction(i);
        runTest("traits", "traits", i);
    }
}
