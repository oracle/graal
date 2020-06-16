/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

/**
 * Basic test of debug-a-lot instrument applied to simple language.
 */
public class SLDebugALot {

    private final Source slCode = Source.create("sl", "function main() {\n" +
                    "  n = 2;\n" +
                    "  return types(n);\n" +
                    "}\n" +
                    "function doNull() {}\n" +
                    "function compute(n, l) {\n" +
                    "  z = new();\n" +
                    "  z.a = n + l;\n" +
                    "  z.b = z;\n" +
                    "  z.c = n - l;\n" +
                    "  return z;\n" +
                    "}\n" +
                    "function types(n) {\n" +
                    "  a = 1;\n" +
                    "  b = n + a;\n" +
                    "  c = \"string\";\n" +
                    "  d = doNull();\n" +
                    "  e = 10 == 10;\n" +
                    "  f = new();\n" +
                    "  f.p1 = 1;\n" +
                    "  f.p2 = new();\n" +
                    "  f.p2.p21 = 21;\n" +
                    "  g = doNull;\n" +
                    "  i = 0;\n" +
                    "  while (i < n) {\n" +
                    "    b = b * i;\n" +
                    "    l = b + i;\n" +
                    "    z = compute(n, l);\n" +
                    "    a = a + z.a;\n" +
                    "    i = i + 1;\n" +
                    "  }\n" +
                    "  return n * a;\n" +
                    "}\n");

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    public void test() {
        try (Engine engine = Engine.newBuilder().out(out).err(err).allowExperimentalOptions(true).option("debugalot", "true").build()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                context.eval(slCode);
            }
        }
        String log = out.toString();
        String successMessage = "Executed successfully:";
        int index = log.lastIndexOf(successMessage);
        Assert.assertTrue(log, index > 0);
        String success = log.substring(index + successMessage.length()).trim();
        Assert.assertEquals(log, "TRUE", success);
    }
}
