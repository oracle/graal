/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.Test;

public class SLExitTest {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    @Test
    public void testExit() {
        try (Context context = Context.create()) {
            context.eval("sl", "function main() {\n" +
                            "  exit(5);\n" +
                            "}\n");
            Assert.fail();
        } catch (PolyglotException pe) {
            Assert.assertTrue(pe.isExit());
            Assert.assertEquals(5, pe.getExitStatus());
        }
    }

    @Test
    public void testExitWithShutdownHook() throws IOException {
        String message = "Hello world!";
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (Context context = Context.newBuilder().out(out).build()) {
                context.eval("sl", "function onShutdown() {\n" +
                                "  println(\"" + message + "\");\n" +
                                "}\n" +
                                "\n" +
                                "function main() {\n" +
                                "  registerShutdownHook(onShutdown);\n" +
                                "  exit(5);\n" +
                                "}\n");
                Assert.fail();
            } catch (PolyglotException pe) {
                Assert.assertTrue(pe.isExit());
                Assert.assertEquals(5, pe.getExitStatus());
            }
            Assert.assertEquals(message + LINE_SEPARATOR, out.toString());
        }
    }

    @Test
    public void testShutdownHookWithoutExit() throws IOException {
        String message = "Hello world!";
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (Context context = Context.newBuilder().out(out).build()) {
                context.eval("sl", "function onShutdown() {\n" +
                                "  println(\"" + message + "\");\n" +
                                "}\n" +
                                "\n" +
                                "function main() {\n" +
                                "  registerShutdownHook(onShutdown);\n" +
                                "}\n");
            }
            Assert.assertEquals(message + LINE_SEPARATOR, out.toString());
        }
    }

    @Test
    public void testMultipleShutdownHooks() throws IOException {
        String message1 = "Hello";
        String message2 = "world!";
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (Context context = Context.newBuilder().out(out).build()) {
                context.eval("sl", "function onShutdown1() {\n" +
                                "  println(\"" + message1 + "\");\n" +
                                "}\n" +
                                "\n" +
                                "function onShutdown2() {\n" +
                                "  println(\"" + message2 + "\");\n" +
                                "}\n" +
                                "\n" +
                                "function main() {\n" +
                                "  registerShutdownHook(onShutdown1);\n" +
                                "  registerShutdownHook(onShutdown2);\n" +
                                "}\n");
            }
            Assert.assertEquals(message1 + LINE_SEPARATOR + message2 + LINE_SEPARATOR, out.toString());
        }
    }

}
