/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.suites.memory;

import java.io.IOException;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.junit.Assert;
import org.junit.Test;

public class Memory64Suite extends AbstractBinarySuite {

    // (type (func (result i32)))
    // (func (type 0))
    private static final String DEFAULT_FUNCTION_DEFINITION = "" +
                    "01 05 01 60 00 01 7F" +
                    "03 02 01 00";

    // (export "a" (func 0))
    private static final String DEFAULT_FUNCTION_EXPORT = "" +
                    "07 05 01 01 61 00 00";

    @Test
    public void test32BitMemory() throws IOException {
        // memory 1 1
        // main:
        // i64.const 0
        // i32.const 1
        // i32.store
        // i64.const 0
        // i32.load
        final byte[] binary = hexToBinary(DEFAULT_FUNCTION_DEFINITION +
                        "05 04 01 05 01 01" +
                        DEFAULT_FUNCTION_EXPORT +
                        "0A 10 01 0E 00 42 00 41 01 36 00 00 42 00 28 00 00 0B");
        runRuntimeTest(binary, builder -> {
            builder.option("wasm.Memory64", "true");
            builder.option("wasm.UseUnsafeMemory", "true");
        }, instance -> {
            Value a = instance.getMember("a");
            Value x = a.execute();
            Assert.assertTrue(x.fitsInInt());
            Assert.assertEquals(1, x.asInt());
        });
    }

    @Test
    public void test64BitMemory() throws IOException {
        // memory 1 32769
        // main:
        // i64.const 0
        // i32.const 1
        // i32.store
        // i64.const 0
        // i32.load
        final byte[] binary = hexToBinary(DEFAULT_FUNCTION_DEFINITION +
                        "05 06 01 05 01 81 80 02" +
                        DEFAULT_FUNCTION_EXPORT +
                        "0A 18 01 16 00 42 80 80 80 80 00 41 01 36 00 00 42 80 80 80 80 00 28 00 00 0B");
        runRuntimeTest(binary, builder -> {
            builder.option("wasm.Memory64", "true");
            builder.option("wasm.UseUnsafeMemory", "true");
        }, instance -> {
            Value a = instance.getMember("a");
            Value x = a.execute();
            Assert.assertTrue(x.fitsInInt());
            Assert.assertEquals(1, x.asInt());
        });
    }

    @Test
    public void testAccess32BitMemoryWith64BitAddress() throws IOException {
        // memory 1 1
        // main:
        // i64.const 0
        // i32.const 1
        // i32.store
        final byte[] binary = hexToBinary(DEFAULT_FUNCTION_DEFINITION +
                        "05 04 01 01 01 01" +
                        DEFAULT_FUNCTION_EXPORT +
                        "0A 0B 01 09 00 42 00 41 01 36 00 00 0B");
        runParserTest(binary, builder -> {
            builder.option("wasm.Memory64", "true");
            builder.option("wasm.UseUnsafeMemory", "true");
        }, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected invalid address", e.getMessage().contains("Expected type [i32], but got [i64]"));
            }
        });
    }
}
