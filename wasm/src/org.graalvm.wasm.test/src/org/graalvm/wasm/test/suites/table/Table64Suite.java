/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.suites.table;

import java.io.IOException;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.junit.Assert;
import org.junit.Test;

public class Table64Suite extends AbstractBinarySuite {

    // (module
    // (table i64 1 4 funcref)
    // (func (export "size") (result i64) table.size)
    // (func (export "grow") (param i64) (result i64) ref.null func local.get 0 table.grow)
    // (func (export "get") (param i64) (result funcref) local.get 0 table.get))
    private static final String TABLE64_MODULE = "01 0F 03 60 00 01 7E 60 01 7E 01 7E 60 01 7E 01 70" +
                    "03 04 03 00 01 02" +
                    "04 05 01 70 05 01 04" +
                    "07 15 03 04 73 69 7A 65 00 00 04 67 72 6F 77 00 01 03 67 65 74 00 02" +
                    "0A 18 03 05 00 FC 10 00 0B 09 00 D0 70 20 00 FC 0F 00 0B 06 00 20 00 25 00 0B";

    private static final String ACTIVE_ELEMENT_WITHOUT_TABLE_MODULE = "09 06 01 00 41 00 0B 00";

    private static final String TABLE64_UNSIGNED_MAX_DECLARATION_MODULE = "04 0E 01 70 05 00 FF FF FF FF FF FF FF FF FF 01";

    @Test
    public void testTable64() throws IOException {
        runRuntimeTest(hexToBinary(TABLE64_MODULE), builder -> {
            builder.option("wasm.Memory64", "true");
            builder.option("wasm.UseUnsafeMemory", "true");
        }, instance -> {
            Value size = instance.getMember("size");
            Value grow = instance.getMember("grow");
            Value get = instance.getMember("get");

            Assert.assertEquals(1L, size.execute().asLong());
            Assert.assertEquals(1L, grow.execute(2L).asLong());
            Assert.assertEquals(3L, size.execute().asLong());
            Assert.assertTrue(get.execute(0L).isNull());
        });
    }

    @Test
    public void testTable64RequiresMemory64Option() throws IOException {
        runParserTest(hexToBinary(TABLE64_MODULE), builder -> {
            builder.option("wasm.UseUnsafeMemory", "true");
        }, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue(e.getMessage().contains("64-bit indexed table used without setting --wasm.Memory64"));
            }
        });
    }

    @Test
    public void testTable32RejectsI64Index() throws IOException {
        final String table32Module = TABLE64_MODULE.replace("04 05 01 70 05 01 04", "04 05 01 70 01 01 04");
        runParserTest(hexToBinary(table32Module), builder -> {
            builder.option("wasm.Memory64", "true");
            builder.option("wasm.UseUnsafeMemory", "true");
        }, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue(e.getMessage().contains("i32") && e.getMessage().contains("i64"));
            }
        });
    }

    @Test
    public void testTable64AllowsUnsignedMaximum() throws IOException {
        runParserTest(hexToBinary(TABLE64_UNSIGNED_MAX_DECLARATION_MODULE), builder -> {
            builder.option("wasm.Memory64", "true");
            builder.option("wasm.UseUnsafeMemory", "true");
        }, (context, source) -> context.eval(source));
    }

    @Test
    public void testActiveElementSegmentWithoutTableReportsUnknownTable() throws IOException {
        runParserTest(hexToBinary(ACTIVE_ELEMENT_WITHOUT_TABLE_MODULE), builder -> {
        }, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue(e.getMessage().contains("unknown table"));
            }
        });
    }
}
