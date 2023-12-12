/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.junit.Assert;
import org.junit.Test;

public class ReferenceTypesValidationSuite extends AbstractBinarySuite {

    private static AbstractBinarySuite.BinaryBuilder getDefaultTableInitBuilder(String mainHexCode) {
        // (module
        // (type (;0;) (func (result i32)))
        //
        // (table 3 3 funcref)
        //
        // (func (;0;) (type 0)
        // i32.const 0
        // )
        //
        // (func (;1;) (type 0)
        // i32.const 1
        // )
        //
        // (func (;2;) (type 0)
        // i32.const 2
        // )
        //
        // (func (export "main") (type 0)
        // ;; main hex code
        // )
        // )
        return newBuilder().addType(EMPTY_BYTES, new byte[]{WasmType.I32_TYPE}).addTable((byte) 3, (byte) 3, WasmType.FUNCREF_TYPE).addFunction((byte) 0, EMPTY_BYTES, "41 00 0B").addFunction((byte) 0,
                        EMPTY_BYTES, "41 01 0B").addFunction((byte) 0, EMPTY_BYTES, "41 02 0B").addFunction((byte) 0, EMPTY_BYTES, mainHexCode).addFunctionExport(
                                        (byte) 3, "main");
    }

    @Test
    public void testTableInitActiveType0() throws IOException {
        // main:
        // i32.const 0
        // call_indirect 0 (type 0)
        //
        // (elem (table 0) (offset i32.const 0) funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 11 00 00 0B").addElements("00 41 00 0B 03 00 01 02").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 0, result.asInt());
        });
    }

    @Test
    public void testTableInitActiveType2() throws IOException {
        // main:
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem (table 0) (offset i32.const 0) funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 01 11 00 00 0B").addElements("02 00 41 00 0B 00 03 00 01 02").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 1, result.asInt());
        });
    }

    @Test
    public void testTableInitActiveType2InvalidTableIndex() throws IOException {
        // main:
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem (table 2) (offset i32.const 0) funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 01 11 00 00 0B").addElements("02 02 41 00 0B 00 03 00 01 02").build();
        runParserTest(binary, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected unknown table", e.getMessage().contains("unknown table"));
            }
        });
    }

    @Test
    public void testTableInitActiveType4() throws IOException {
        // main:
        // i32.const 2
        // call_indirect 0 (type 0)
        //
        // (elem (table 0) (offset i32.const 0) funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 02 11 00 00 0B").addElements("04 41 00 0B 03 D2 00 0B D2 01 0B D2 02 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 2, result.asInt());
        });
    }

    @Test
    public void testTableInitActiveType6() throws IOException {
        // main:
        // i32.const 0
        // call_indirect 0 (type 0)
        //
        // (elem (table 0) (offset i32.const 0) funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 11 00 00 0B").addElements("06 00 41 00 0B 70 03 D2 00 0B D2 01 0B D2 02 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 0, result.asInt());
        });
    }

    @Test
    public void testTableInitActiveType6InvalidTableIndex() throws IOException {
        // main:
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem (table 2) (offset i32.const 0) funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 01 11 00 00 0B").addElements("06 02 41 00 0B 70 03 D2 00 0B D2 01 0B D2 02 0B").build();
        runParserTest(binary, ((context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected unknown table", e.getMessage().contains("unknown table"));
            }
        }));
    }

    @Test
    public void testTableInitPassiveType1() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 0
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 00 00 41 01 11 00 00 0B").addElements("01 00 03 00 01 02").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 1, result.asInt());
        });
    }

    @Test
    public void testTableInitPassiveType5() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 0
        // i32.const 2
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 00 00 41 02 11 00 00 0B").addElements("05 70 03 D2 00 0B D2 01 0B D2 02 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 2, result.asInt());
        });
    }

    @Test
    public void testTableInitDeclarativeType3() throws IOException {
        // main:
        // ref.func 3
        // drop
        // i32.const 0
        //
        // (elem declare (ref.func 3))
        final byte[] binary = getDefaultTableInitBuilder("D2 03 1A 41 00 0B").addFunction((byte) 0, EMPTY_BYTES, "41 03 0B").addElements("03 00 01 03").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 0, result.asInt());
        });
    }

    @Test
    public void testTableInitDeclarativeType7() throws IOException {
        // main
        // ref.func 3
        // drop
        // i32.const 0
        //
        // (elem declare (ref.func 3))
        final byte[] binary = getDefaultTableInitBuilder("D2 03 1A 41 00 0B").addFunction((byte) 0, EMPTY_BYTES, "41 03 0B").addElements("07 70 01 D2 03 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected return value", 0, result.asInt());
        });
    }

    @Test
    public void testCallNullValueAfterTableInit() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 0
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 0) (ref.null func) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 00 00 41 01 11 00 00 0B").addElements("05 70 03 D2 00 0B D0 70 0B D2 02 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected null reference error", e.getMessage().contains("Table element at index 1 is uninitialized"));
            }
        });
    }

    @Test
    public void testTableInitFunctionIndexDoesNotExist() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 0
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 0) (ref.func 1) (ref.func 4))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 00 00 41 01 11 00 00 0B").addElements("05 70 03 D2 00 0B D2 01 0B D2 04 0B").build();
        runParserTest(binary, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected null reference error", e.getMessage().contains("unknown function"));
            }
        });
    }

    @Test
    public void testTableInitInvalidElementExpression() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 0
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem funcref (i32.const 0))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 00 00 41 01 11 00 00 0B").addElements("05 70 01 41 00 0B").build();
        runParserTest(binary, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected type mismatch error", e.getMessage().contains("Invalid constant expression for table elem expression:"));
            }
        });
    }

    @Test
    public void testTableInitInvalidElementKind() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 0
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem 0x01 (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 00 00 41 01 11 00 00 0B").addElements("01 01 03 00 01 02").build();
        runParserTest(binary, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected invalid element kind error", e.getMessage().contains("Invalid element kind:"));
            }
        });
    }

    @Test
    public void testTableInitInvalidLength() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 4
        // table.init 0
        // i32.const 0
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 04 FC 0C 00 00 41 00 11 00 00 0B").addElements("05 70 03 D2 00 0B D2 01 0B D2 02 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expect out of bounds error", e.getMessage().contains("out of bounds table access"));
            }
        });
    }

    @Test
    public void testTableInitInvalidSourceOffset() throws IOException {
        // main
        // i32.const 0
        // i32.const 2
        // i32.const 3
        // table.init 0
        // i32.const 0
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 02 41 03 FC 0C 00 00 41 00 11 00 00 0B").addElements("05 70 03 D2 00 0B D2 01 0B D2 02 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expect out of bounds error", e.getMessage().contains("out of bounds table access"));
            }
        });
    }

    @Test
    public void testTableInitInvalidDestinationOffset() throws IOException {
        // main:
        // i32.const 2
        // i32.const 0
        // i32.const 3
        // table.init 0
        // i32.const 0
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 02 41 00 41 03 FC 0C 00 00 41 00 11 00 00 0B").addElements("05 70 03 D2 00 0B D2 01 0B D2 02 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expect out of bounds error", e.getMessage().contains("out of bounds table access"));
            }
        });
    }

    @Test
    public void testTableInitElementIndexDoesNotExist() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 1
        // i32.const 0
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 01 00 41 00 11 00 00 0B").addElements("05 70 03 D2 00 0B D2 01 0B D2 02 0B").build();
        runParserTest(binary, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expect unknown element segment error", e.getMessage().contains("unknown elem segment"));
            }
        });
    }

    @Test
    public void testTableCopyInvalidLength() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 0
        // i32.const 1
        // i32.const 0
        // i32.const 4
        // table.copy
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 1) (ref.null func) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 00 00 41 04 41 00 41 04 FC 0E 00 00 41 01 11 00 00 0B").addElements("05 70 03 D2 01 0B D0 70 0B D2 02 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expect out of bounds error", e.getMessage().contains("out of bounds table access"));
            }
        });
    }

    @Test
    public void testTableCopyInvalidSourceOffset() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 0
        // i32.const 1
        // i32.const 3
        // i32.const 1
        // table.copy
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 1) (ref.null func) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 00 00 41 01 41 03 41 01 FC 0E 00 00 41 01 11 00 00 0B").addElements("05 70 03 D2 01 0B D0 70 0B D2 02 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expect out of bounds error", e.getMessage().contains("out of bounds table access"));
            }
        });
    }

    @Test
    public void testTableCopyInvalidDestinationOffset() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 0
        // i32.const 3
        // i32.const 0
        // i32.const 1
        // table.copy
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 1) (ref.null func) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 00 00 41 03 41 00 41 01 FC 0E 00 00 41 01 11 00 00 0B").addElements("05 70 03 D2 01 0B D0 70 0B D2 02 0B").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            try {
                main.execute();
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expect out of bounds error", e.getMessage().contains("out of bounds table access"));
            }
        });
    }

    @Test
    public void testElemDropElementIndexDoesNotExist() throws IOException {
        // main:
        // i32.const 0
        // i32.const 0
        // i32.const 3
        // table.init 0
        // elem.drop 1
        // i32.const 1
        // call_indirect 0 (type 0)
        //
        // (elem funcref (ref.func 0) (ref.func 1) (ref.func 2))
        final byte[] binary = getDefaultTableInitBuilder("41 00 41 00 41 03 FC 0C 00 00 FC 0D 01 41 01 11 00 00 0B").addElements("05 70 03 D2 00 0B D2 01 0B D2 02 0B").build();
        runParserTest(binary, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expect unknown element segment error", e.getMessage().contains("unknown elem segment"));
            }
        });
    }

    @Test
    public void testMultipleTables() throws IOException {
        // (table 1 1 funcref)
        // (table 1 1 externref)
        final byte[] binary = newBuilder().addTable((byte) 1, (byte) 1, WasmType.FUNCREF_TYPE).addTable((byte) 1, (byte) 1, WasmType.EXTERNREF_TYPE).build();
        runParserTest(binary, Context::eval);
    }

    @Test
    public void testTableInvalidElemType() throws IOException {
        // (table 1 1 i32)
        final byte[] binary = newBuilder().addTable((byte) 1, (byte) 1, WasmType.I32_TYPE).build();
        runParserTest(binary, (context, source) -> {
            try {
                context.eval(source);
                Assert.fail("Should have thrown");
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected invalid reference type", e.getMessage().contains("Unexpected reference type"));
            }
        });
    }

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
                Assert.assertTrue("Expected unknown memory", e.getMessage().contains("unknown memory: 5 should be < 1"));
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

    @Test
    public void testRefFuncWithNoReference() throws IOException {
        // main:
        // ref.func 0
        // i32.const 0
        final byte[] binary = getDefaultTableInitBuilder("D2 00 41 00 0B").build();
        runParserTest(binary, (context, source) -> {
            try {
                context.eval(source);
            } catch (PolyglotException e) {
                Assert.assertTrue("Expected undeclared function reference", e.getMessage().contains("undeclared function reference"));
            }
        });
    }

    @Test
    public void testGlobalWithNull() throws IOException {
        // (global externref (ref.null))
        // (type (func (result externref)))
        // (func (export "main") (type 0)
        // global.get 0
        // )
        final byte[] binary = newBuilder().addGlobal(GlobalModifier.CONSTANT, WasmType.EXTERNREF_TYPE, "D0 6F 0B").addType(EMPTY_BYTES, new byte[]{WasmType.EXTERNREF_TYPE}).addFunction((byte) 0,
                        EMPTY_BYTES, "23 00 0B").addFunctionExport((byte) 0, "main").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertTrue("Unexpected result value", result.isNull());
        });
    }

    @Test
    public void testGlobalWithFunction() throws IOException {
        // (global funcref (ref.func 0))
        // (table 1 1 funcref)
        // (type (func (result i32)))
        // (func (type 0)
        // i32.const 1
        // )
        // (func (export "main") (type 0)
        // i32.const 0
        // global.get 0
        // table.set 0
        // i32.const 0
        // call_indirect 0 (type 0)
        // )
        final byte[] binary = newBuilder().addGlobal(GlobalModifier.CONSTANT, WasmType.FUNCREF_TYPE, "D2 00 0B").addTable((byte) 1, (byte) 1, WasmType.FUNCREF_TYPE).addType(EMPTY_BYTES,
                        new byte[]{WasmType.I32_TYPE}).addFunction((byte) 0, EMPTY_BYTES, "41 01 0B").addFunction((byte) 0, EMPTY_BYTES, "41 00 23 00 26 00 41 00 11 00 00 0B").addFunctionExport(
                                        (byte) 1, "main").build();
        runRuntimeTest(binary, instance -> {
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertEquals("Unexpected result value", 1, result.asInt());
        });
    }

    @Test
    public void testMultiValueWithReferenceTypes() throws IOException {
        // (table 2 2 funcref)
        // (table 2 2 externref)
        // (type (;0;) (func (result i32)))
        // (type (;1;) (func (param i32 funcref) (result externref funcref)))
        // (type (;2;) (func (result externref funcref)))
        // (type (;3;) (func (param i32 externref)))
        // (func (;0;) (type 0)
        // i32.const 1
        // )
        // (func (;1;) (type 0)
        // i32.const 2
        // )
        // (func (;2;) (type 1)
        // local.get 0
        // table.get 1
        // local.get 1
        // )
        // (func (;3;) (export "main") (type 2)
        // i32.const 1
        // i32.const 0
        // table.get 0
        // call 2
        // )
        // (func (;4;) (export "setRef") (type 3)
        // local.get 0
        // local.get 1
        // table.set 1
        // )
        // (elem (table 0) (ref.func 0) (ref.func 1))
        final byte[] binary = newBuilder().addTable((byte) 2, (byte) 2, WasmType.FUNCREF_TYPE).addTable((byte) 2, (byte) 2, WasmType.EXTERNREF_TYPE).addType(EMPTY_BYTES,
                        new byte[]{WasmType.I32_TYPE}).addType(new byte[]{WasmType.I32_TYPE, WasmType.FUNCREF_TYPE}, new byte[]{WasmType.EXTERNREF_TYPE, WasmType.FUNCREF_TYPE}).addType(EMPTY_BYTES,
                                        new byte[]{WasmType.EXTERNREF_TYPE, WasmType.FUNCREF_TYPE}).addType(new byte[]{WasmType.I32_TYPE, WasmType.EXTERNREF_TYPE}, EMPTY_BYTES).addFunction((byte) 0,
                                                        EMPTY_BYTES, "41 01 0B").addFunction((byte) 0, EMPTY_BYTES, "41 02 0B").addFunction((byte) 1, EMPTY_BYTES, "20 00 25 01 20 01 0B").addFunction(
                                                                        (byte) 2, EMPTY_BYTES, "41 01 41 00 25 00 10 02 0B").addFunction((byte) 3, EMPTY_BYTES,
                                                                                        "20 00 20 01 26 01 0B").addFunctionExport((byte) 3,
                                                                                                        "main").addFunctionExport((byte) 4, "setRef").addElements("00 41 00 0B 02 00 01").build();
        runRuntimeTest(binary, instance -> {
            Value setRef = instance.getMember("setRef");
            setRef.execute(0, "foo");
            setRef.execute(1, "bar");
            Value main = instance.getMember("main");
            Value result = main.execute();
            Assert.assertTrue("Result must be multi-value", result.hasArrayElements());
            Assert.assertEquals("Unexpected result value", "bar", result.getArrayElement(0).asString());
            Value f = result.getArrayElement(1);
            Assert.assertEquals("Unexpected result value", 1, f.execute().asInt());
        });
    }
}
