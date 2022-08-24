/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.suites.validation;

import java.io.IOException;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.graalvm.wasm.utils.Assert;
import org.junit.Test;

public class MemoryValidationSuite extends AbstractBinarySuite {

    private static AbstractBinarySuite.BinaryBuilder getDefaultMemoryInitBuilder(String mainHexCode) {
        // (module
        // (type (;0;) (func (result i32)))
        //
        // (memory 1 1)
        //
        // (func (export "main") (type 0)
        // ;; main hex code
        // )
        // )
        return newBuilder().addType(EMPTY_BYTES, new byte[]{WasmType.I32_TYPE}).addMemory((byte) 1, (byte) 1).addFunction((byte) 0, EMPTY_BYTES, mainHexCode).addFunctionExport((byte) 0, "main");
    }

    @Test
    public void testMemoryInitActiveType0() throws IOException {
        // main:
        // i32.const 0
        // i32.load offset=0
        //
        // (data (memory 0) (offset i32.const 0) "\01\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 00 28 00 00 0B").addData("00 41 00 0B 04 01 00 00 00").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 1, result.asInt());
        });
    }

    @Test
    public void testMemoryInitActiveType2() throws IOException {
        // main:
        // i32.const 0
        // i32.load offset=0
        //
        // (data (memory 0) (offset i32.const 0) "\02\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 00 28 00 00 0B").addData("02 00 41 00 0B 04 02 00 00 00").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 2, result.asInt());
        });
    }

    @Test
    public void testMemoryInitActiveType2InvalidMemoryIndex() throws IOException {
        // main:
        // i32.const 0
        // i32.load offset=0
        //
        // (data (memory 5) (offset i32.const 0) "\02\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 00 28 00 00 0B").addData("02 05 41 00 0B 04 02 00 00 00").build();
        runParserTest(binary, ((context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected unknown memory", e.getMessage().contains("unknown memory: 5 should = 0"));
            }
        }));
    }

    @Test
    public void testMemoryInitPassiveType1() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 4
        // memory.init 0
        // i32.const 0
        // i32.load offset=0
        //
        // (data "\03\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 00 41 00 41 04 FC 08 00 00 41 00 28 00 00 0B").addData("01 04 03 00 00 00").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 3, result.asInt());
        });
    }

    @Test
    public void testMemoryInitInvalidLength() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 5
        // memory.init 0
        // i32.const 0
        // i32.load offset=0
        //
        // (data "\03\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 00 41 00 41 05 FC 08 00 00 41 00 28 00 00 0B").addData("01 04 03 00 00 00").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected out of bounds error", e.getMessage().contains("out of bounds memory access"));
            }
        });
    }

    @Test
    public void testMemoryInitInvalidSourceOffset() throws IOException {
        // main:
        // i32.const 0
        // i32.const 2
        // i32.const 4
        // memory.init 0
        // i32.const 0
        // i32.load offset=0
        //
        // (data "\03\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 00 41 02 41 04 FC 08 00 00 41 00 28 00 00 0B").addData("01 04 03 00 00 00").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected out of bounds error", e.getMessage().contains("out of bounds memory access"));
            }
        });
    }

    @Test
    public void testMemoryInitInvalidDestinationOffset() throws IOException {
        // main:
        // i32.const 65535
        // i32.const 0
        // i32.const 4
        // memory.init 0
        // i32.const 0
        // i32.load offset=0
        //
        // (data "\03\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 FF FF 03 41 00 41 04 FC 08 00 00 41 00 28 00 00 0B").addData("01 04 03 00 00 00").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected out of bounds error", e.getMessage().contains("out of bounds memory access"));
            }
        });
    }

    @Test
    public void testMemoryInitDataIndexDoesNotExist() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 4
        // memory.init 0
        // i32.const 0
        // i32.load offset=0
        //
        // (data "\03\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 00 41 00 41 04 FC 08 01 00 41 00 28 00 00 0B").addData("01 04 03 00 00 00").build();
        runParserTest(binary, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected unknown data segment error", e.getMessage().contains("unknown data segment"));
            }
        });
    }

    @Test
    public void testMemoryCopyInvalidLength() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 65537
        // memory.copy 0
        // i32.const 0
        // i32.load offset=0
        //
        // (data "\03\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 00 41 00 41 81 80 04 FC 0A 00 00 41 00 28 00 00 0B").addData("01 04 03 00 00 00").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected out of bounds error", e.getMessage().contains("out of bounds memory access"));
            }
        });
    }

    @Test
    public void testMemoryCopyInvalidSourceOffset() throws IOException {
        // main:
        // i32.const 0
        // i32.const 65535
        // i32.const 4
        // memory.copy 0
        // i32.const 0
        // i32.load offset=0
        //
        // (data "\03\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 00 41 FF FF 03 41 04 FC 0A 00 00 41 00 28 00 00 0B").addData("01 04 03 00 00 00").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected out of bounds error", e.getMessage().contains("out of bounds memory access"));
            }
        });
    }

    @Test
    public void testMemoryCopyInvalidDestinationOffset() throws IOException {
        // main:
        // i32.const 65535
        // i32.const 0
        // i32.const 4
        // memory.copy 0
        // i32.const 0
        // i32.load offset=0
        //
        // (data "\03\00\00\00")
        final byte[] binary = getDefaultMemoryInitBuilder("41 FF FF 03 41 00 41 04 FC 0A 00 00 41 00 28 00 00 0B").addData("01 04 03 00 00 00").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected out of bounds error", e.getMessage().contains("out of bounds memory access"));
            }
        });
    }

    @Test
    public void testDataDropDataIndexDoesNotExist() throws IOException {
        // main:
        // data.drop 1
        //
        // (data "\00")
        final byte[] binary = getDefaultMemoryInitBuilder("FC 09 01").addData("01 01 00").build();
        runParserTest(binary, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected unknown data segment error", e.getMessage().contains("unknown data segment"));
            }
        });
    }

    @Test
    public void testMemoryFillInvalidLength() throws IOException {
        // main:
        // i32.const 0
        // i32.const 1
        // i32.const 65537
        // memory.fill
        // i32.const 0
        // i32.load offset=0
        final byte[] binary = getDefaultMemoryInitBuilder("41 00 41 00 41 81 80 04 FC 0B 00 41 00 28 00 00 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected out of bounds error", e.getMessage().contains("out of bounds memory access"));
            }
        });
    }

    @Test
    public void testMemoryFillInvalidDestinationOffset() throws IOException {
        // main:
        // i32.const 65537
        // i32.const 1
        // i32.const 1
        // memory.fill
        // i32.const 0
        // i32.load offset=0
        final byte[] binary = getDefaultMemoryInitBuilder("41 81 80 04 41 01 41 01 FC 0B 00 41 00 28 00 00 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected out of bounds error", e.getMessage().contains("out of bounds memory access"));
            }
        });
    }
}
