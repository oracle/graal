/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.test.WasmFileSuite;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.graalvm.wasm.test.WasmTestUtils.hexStringToByteArray;
import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;

@RunWith(Parameterized.class)
public class ValidationSuite extends WasmFileSuite {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                        // # Binary Format

                        binaryCase(
                                        "Custom Section - missing name",
                                        "The binary is truncated at: 10",
                                        "0061 736d 0100 0000 0000",
                                        Failure.Type.MALFORMED),

                        binaryCase(
                                        "Custom Section - excessive name length",
                                        "unexpected end of section or function: 14 should be <= 11",
                                        "0061 736d 0100 0000 0001 0300 0100",
                                        Failure.Type.MALFORMED),

                        binaryCase(
                                        "Incorrect order of sections",
                                        "Section 5 defined after section 6",
                                        // Global and memory sections in reverse order:
                                        // (memory 1) (global i32 (i32.const 1))
                                        "0061 736d 0100 0000 0606 017f 0041 010b 0503 0100 01",
                                        Failure.Type.MALFORMED),

                        binaryCase(
                                        "Duplicated sections",
                                        "Duplicated section 6",
                                        // (global (export "g1") i32 (i32.const 1))
                                        // (global (export "g2") i64 (i64.const 0)) but with each
                                        // export/global using its own export/global section
                                        "0061 736d 0100 0000 0606 017f 0041 010b 0606 017e 0042 000b 0706 0102 6731 0300 0706 0102 6732 0301",
                                        Failure.Type.MALFORMED),

                        binaryCase(
                                        "Code Section - more entries",
                                        "function and code section have inconsistent lengths: 1 should = 0",
                                        // (func) but without function section
                                        "0061 736d 0100 0000 0104 0160 0000 0a04 0102 000b",
                                        Failure.Type.MALFORMED),

                        binaryCase(
                                        "Code Section - less entries",
                                        "function and code section have inconsistent lengths: 0 should = 1",
                                        // (func) but with code section with 0 entries
                                        "0061 736d 0100 0000 0104 0160 0000 0302 0100 0a01 00",
                                        Failure.Type.MALFORMED),

                        binaryCase(
                                        "Export name - overlong encoding",
                                        "Invalid UTF-8 encoding of the name at: 23",
                                        // (func (export \"\\F0\\82\\82\\AC\")
                                        // (result i32) i32.const 42)
                                        // F0 82 82 AC is UTF-8 overlong encoding of Euro sign
                                        "0061 736d 0100 0000 0105 0160 0001 7f03 0201 0007 0801 04F0 8282 AC00 000a 0601 0400 412a 0b",
                                        Failure.Type.MALFORMED),

                        // # Validation

                        // ## Types
                        // https://webassembly.github.io/spec/core/valid/types.html

                        // ### Limits
                        // https://webassembly.github.io/spec/core/valid/types.html#limits

                        // See 3.2.3 and 3.2.4

                        // ### Function Types
                        // https://webassembly.github.io/spec/core/valid/types.html#function-types

                        // The arity `m` must not be larger than 1 (limitiation of MVP).
                        // Validated in: SymbolTable#allocateFunctionType
                        binaryCase(
                                        "Function - cannot return more than one value",
                                        "A function can return at most one result.",
                                        // (func $f (result i32) i32.const 42 i32.const 42)
                                        "0061 736d 0100 0000 0105 0160 0002 7f03 0201 000a 0801 0600 412a 412a 0b",
                                        Failure.Type.INVALID),

                        // ### Table types
                        // https://webassembly.github.io/spec/core/valid/types.html#table-types

                        // The limits `limits` must be valid within range `2^32`.
                        // Validated in: BinaryParser#readTableLimits
                        // Note: values are silently clamped if bigger than GraalWasm's `2^31 - 1`
                        // limit.
                        /*
                         * stringCase( "Table - initial size out of bounds",
                         * "size minimum must not be greater than maximum: 2147483648 should be <= 2147483647."
                         * , "(table $table1 2147483648888 funcref)", Failure.Type.INVALID),
                         * stringCase( "Table - initial size out of bounds",
                         * "size minimum must not be greater than maximum: 2147483648 should be <= 2147483647."
                         * , "(table $table1 1 2147483648 funcref)", Failure.Type.INVALID),
                         */
                        stringCase(
                                        "Table - max size lower than initial size",
                                        "size minimum must not be greater than maximum: 2 should be <= 1",
                                        "(table $table1 2 1 funcref)",
                                        Failure.Type.INVALID),

                        // ### Memory types
                        // https://webassembly.github.io/spec/core/valid/types.html#memory-types

                        // The limits `limits` must be valid within range `2^16`.
                        // Validated in: BinaryParser#readMemoryLimits
                        stringCase(
                                        "Memory - initial size out of bounds",
                                        "memory size must be at most 65536 pages (4GiB): 2147483648 should be <= 65536",
                                        "(memory $memory1 2147483648)",
                                        Failure.Type.INVALID),
                        stringCase(
                                        "Memory - max size out of bounds",
                                        "memory size must be at most 65536 pages (4GiB): 2147483648 should be <= 65536",
                                        "(memory $memory1 1 2147483648)",
                                        Failure.Type.INVALID),
                        stringCase(
                                        "Memory - max size lower than initial size",
                                        "size minimum must not be greater than maximum: 2 should be <= 1",
                                        "(memory $memory1 2 1)",
                                        Failure.Type.INVALID),

                        // ### Global types
                        // https://webassembly.github.io/spec/core/valFid/types.html#global-types

                        // (No constraints)

                        // ## Modules
                        // https://webassembly.github.io/spec/core/valid/modules.html

                        // ### Functions
                        // https://webassembly.github.io/spec/core/valid/modules.html#functions

                        // The type `C.types[x]` must be defined in the context.
                        // Validated in: SymbolTable#allocateFunction
                        stringCase(
                                        "Function - invalid type index",
                                        "unknown type: 1 should be < 1",
                                        "(type (func (result i32))) (func (export \"f\") (type 1))",
                                        Failure.Type.INVALID),
                        stringCase(
                                        "Function - invalid type index",
                                        "unknown type: 4294967254 should be < 1",
                                        "(type (func (result i32))) (func (export \"f\") (type 4294967254))",
                                        Failure.Type.INVALID),

                        // Under the context `C'`, the expression `express` must be valid with type
                        // `t2`.
                        // Checked in OfficialTestSuite:
                        // https://github.com/WebAssembly/spec/blob/704d9d9e9c861fdb957c3d5e928f1d046a31497e/test/core/func.wast#L505)

                        // ### Tables
                        // https://webassembly.github.io/spec/core/valid/modules.html#tables

                        // The table type `tabletype` must be valid.
                        // See "Table types" above.

                        // ### Memories
                        // https://webassembly.github.io/spec/core/valid/modules.html#memories

                        // The table type `memtype` must be valid.
                        // See "Memory types above.

                        // ### Globals
                        // https://webassembly.github.io/spec/core/valid/modules.html#globals

                        // The global type `mut t` must be valid.
                        // See "Global types" above.

                        // The expression `expr` must be valid with result type `[t]`.
                        // Checked in OfficialTestSuite:
                        // https://github.com/WebAssembly/spec/blob/704d9d9e9c861fdb957c3d5e928f1d046a31497e/test/core/global.wast#L276

                        // The `expr` must be constant.
                        // Checked in OfficialTestSuite
                        // https://github.com/WebAssembly/spec/blob/704d9d9e9c861fdb957c3d5e928f1d046a31497e/test/core/global.wast#L249:

                        // ### Element Segments
                        // https://webassembly.github.io/spec/core/valid/modules.html#globals

                        // The table `C.tables[x]` must be defined in the context.
                        // Validated in: BinaryParser#readElementSection
                        binaryCase(
                                        "Element segment - invalid table index",
                                        "unknown table: 2 should = 0",
                                        // (table 1 funcref) (elem 5 (i32.const 0) $f1) (func $f1
                                        // (result i32) i32.const 42)
                                        "00 61 73 6d 01 00 00 00 01 05 01 60 00 01 7f 03" +
                                                        "03 02 00 00 04 04 01 70 00 01 07 09 01 05 5f 6d" +
                                                        "61 69 6e 00 01 09 09 01 02 05 41 00 0b 00 01 00" +
                                                        "0a 0b 02 04 00 41 2a 0b 04 00 41 2a 0b",
                                        Failure.Type.INVALID),

                        // The element type `elemtype` must be `funcref`.
                        // Validated in: BinaryParser#readTableSection and
                        // BinaryParser#readImportSection
                        // TODO(mbovel)

                        // The expression `expr` must be valid with result type `[i32]`.
                        // Checked in OfficialTestSuite:
                        // https://github.com/WebAssembly/spec/blob/704d9d9e9c861fdb957c3d5e928f1d046a31497e/test/core/elem.wast#L257

                        // The `expr` must be constant.
                        // Checked in OfficialTestSuite:
                        // https://github.com/WebAssembly/spec/blob/704d9d9e9c861fdb957c3d5e928f1d046a31497e/test/core/elem.wast#L265

                        // For each `y` in `y*`, the function `C.funcs[y]` must be defined in the
                        // context.
                        // Validated in: SymbolTable#function
                        stringCase(
                                        "Element segments - invalid function index",
                                        "unknown function: 1 should be < 1",
                                        "(table 1 funcref) (elem (i32.const 0) 1)",
                                        Failure.Type.INVALID),

                        // ### 3.4.6 Data Segments

                        // The memory `C.mems[x]` must be defined in the context.
                        // Validated in: BinaryParser#readDataSection
                        binaryCase(
                                        "Data segment - invalid memory index",
                                        "unknown memory: 5 should = 0",
                                        // (memory 1) (data 5 (i32.const 0) "Hi")
                                        "0061 736d 0100 0000 0503 0100 010b 0801 0541 000b 0248 69",
                                        Failure.Type.INVALID),

                        // The expression `expr` must be valid with result type `[i32]`.
                        // Checked in OfficialTestSuite:
                        // https://github.com/WebAssembly/spec/blob/704d9d9e9c861fdb957c3d5e928f1d046a31497e/test/core/data.wast#L291

                        // The `expr` must be constant.
                        // Checked in OfficialTestSuite:
                        // https://github.com/WebAssembly/spec/blob/704d9d9e9c861fdb957c3d5e928f1d046a31497e/test/core/data.wast#L299

                        // ### 3.4.7 Start function

                        // The function `C.funcs[x]` must be defined in the context.
                        // Validated in: SymbolTable#function
                        stringCase(
                                        "Start function - invalid index",
                                        "unknown function: 1 should be < 1",
                                        "(start 1)",
                                        Failure.Type.INVALID),

                        // The type of `C.funcs[x]` must be [] -> [].
                        // Validated in SymbolTable#startFunction
                        stringCase(
                                        "Start function - returns a value",
                                        "Start function cannot return a value.",
                                        "(start 0) (func (result i32) i32.const 42)",
                                        Failure.Type.INVALID),
                        stringCase(
                                        "Start function - takes arguments",
                                        "Start function cannot take arguments.",
                                        "(start 0) (func (param i32))",
                                        Failure.Type.INVALID),

                        // The length of `C.tables` must not be larger than 1.
                        // Validated in: SymbolTable#validateSingleTable
                        stringCase(
                                        "Module - two tables (2 locals)",
                                        "A table has already been declared in the module.",
                                        "(table $table1 1 funcref) (table $table2 1 funcref)",
                                        Failure.Type.INVALID),
                        stringCase(
                                        "Module - two tables (1 local and 1 import)",
                                        "A table has already been imported in the module.",
                                        "(table $table2 (import \"some\" \"table\") 1 funcref) (table $table1 1 funcref)",
                                        Failure.Type.INVALID),
                        stringCase(
                                        "Module - two tables (2 imports)",
                                        "A table has already been imported in the module.",
                                        "(table $table1 (import \"some\" \"table\") 1 funcref) (table $table2 (import \"some\" \"table\") 1 funcref)",
                                        Failure.Type.INVALID),

                        // The length of `C.mems` must not be larger than 1.
                        // Validated in: BinaryParser#readMemorySection
                        stringCase(
                                        "Module - two memories (2 locals)",
                                        "A memory has already been declared in the module.",
                                        "(memory $mem1 1) (memory $mem2 1)",
                                        Failure.Type.INVALID),
                        stringCase(
                                        "Module - two memories (1 local and 1 import)",
                                        "A memory has already been imported in the module.",
                                        "(memory $mem1 (import \"some\" \"memory\") 1) (memory $mem2 1)",
                                        Failure.Type.INVALID),
                        // Validated in: SymbolTable#validateSingleMemory
                        stringCase(
                                        "Module - two memories (2 imports)",
                                        "A memory has already been imported in the module.",
                                        "(memory $mem1 (import \"some\" \"memory\") 1) (memory $mem2 (import \"some\" \"memory\") 1)",
                                        Failure.Type.INVALID),

                        // All export names `export_i.name` must be different.
                        // Validated in: SymbolTable#checkUniqueExport
                        stringCase(
                                        "Module - duplicate export (2 functions)",
                                        "All export names must be different, but 'a' is exported twice.",
                                        "(func (export \"a\") (result i32) i32.const 42) (func (export \"a\") (result i32) i32.const 42)",
                                        Failure.Type.INVALID),
                        stringCase(
                                        "Module - duplicate export (function and memory)",
                                        "All export names must be different, but 'a' is exported twice.",
                                        "(func (export \"a\") (result i32) i32.const 42) (memory (export \"a\") 1)",
                                        Failure.Type.INVALID),

                        // Function with too many return values
                        binaryCase("Function - too many return values (1)",
                                        "Expected result types [], but got [i32].",
                                        // (module
                                        // (func
                                        // i32.const 1
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 06 01 04 00 41 01 0B",
                                        Failure.Type.INVALID),

                        binaryCase("Function - too many return values (2)",
                                        "Expected result types [], but got [f32,i32].",
                                        // (module
                                        // (func
                                        // i32.const 1
                                        // f32.const 1
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 0B 01 09 00 41 01 43 00 00 80 3F 0B",
                                        Failure.Type.INVALID),

                        // Function with too few return values
                        binaryCase("Function - too few return values",
                                        "Expected result types [i32], but got [].",
                                        // (module
                                        // (func (result i32)
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 7F 03 02 01 00 0A 04 01 02 00 0B",
                                        Failure.Type.INVALID),
                        // Function with unexpected return type
                        binaryCase("Function - unexpected return type (i32 -> i64)",
                                        "Expected result types [i32], but got [i64].",
                                        // (module
                                        // (func (result i32)
                                        // i64.const 1
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 7F 03 02 01 00 0A 06 01 04 00 42 01 0B",
                                        Failure.Type.INVALID),

                        binaryCase("Function - unexpected return type (f32 -> f64)",
                                        "Expected result types [f32], but got [f64].",
                                        // (module
                                        // (func (result f32)
                                        // f64.const 1
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 7D 03 02 01 00 0A 0D 01 0B 00 44 00 00 00 00 00 00 F0 3F 0B",
                                        Failure.Type.INVALID),
                        binaryCase("Function - correct return type",
                                        "",
                                        // (module
                                        // (func (export "_main") (result i32)
                                        // i32.const 1
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 7F 03 02 01 00 07 09 01 05 5F 6D 61 69 6E 00 00 0A 06 01 04 00 41 01 0B",
                                        null),

                        // If with missing condition
                        binaryCase("If - missing condition",
                                        "Expected type [i32], but got [].",
                                        // (module
                                        // (func
                                        // if ;; label = @1
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 07 01 05 00 04 40 0B 0B",
                                        Failure.Type.INVALID),

                        // If with incorrect condition type
                        binaryCase("If - incorrect condition type (i64)",
                                        "Expected type [i32], but got [i64].",
                                        // (module
                                        // (func
                                        // i64.const 1
                                        // if ;; label = @1
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 09 01 07 00 42 01 04 40 0B 0B",
                                        Failure.Type.INVALID),

                        binaryCase("If - incorrect condition type (f32)",
                                        "Expected type [i32], but got [f32].",
                                        // (module
                                        // (func
                                        // f32.const 1
                                        // if ;; label = @1
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 0C 01 0A 00 43 00 00 80 3F 04 40 0B 0B",
                                        Failure.Type.INVALID),
                        binaryCase("If - incorrect condition type (f64)",
                                        "Expected type [i32], but got [f64].",
                                        // (module
                                        // (func
                                        // f64.const 1
                                        // if ;; label = @1
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 10 01 0E 00 44 00 00 00 00 00 00 F0 3F 04 40 0B 0B",
                                        Failure.Type.INVALID),

                        // If with missing end
                        binaryCase("If - missing end",
                                        "The binary is truncated at: 28",
                                        // (module
                                        // (func
                                        // i32.const 1
                                        // if ;; label = @1
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 09 01 07 00 41 01 04 40 0B",
                                        Failure.Type.MALFORMED),

                        // End without start block
                        binaryCase("End - missing start block",
                                        "The binary is truncated at: 25",
                                        // (module
                                        // (func
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 04 01 02 00 0B 0B",
                                        Failure.Type.MALFORMED),

                        // If with correct condition
                        binaryCase("If - correct condition",
                                        "",
                                        // (module
                                        // (func (export "_main")
                                        // i32.const 1
                                        // if ;; label = @1
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 07 09 01 05 5F 6D 61 69 6E 00 00 0A 09 01 07 00 41 01 04 40 0B 0B",
                                        null),

                        // If - no return value in true branch
                        binaryCase("If - true branch missing return value",
                                        "Expected result types [i32], but got [].",
                                        // (module
                                        // (func
                                        // i32.const 1
                                        // if (result i32)
                                        // else
                                        // i32.const 0
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 0D 01 0B 00 41 01 04 7F 05 41 00 0B 1A 0B",
                                        Failure.Type.INVALID),

                        // If - return type with missing else
                        binaryCase("If - return type without else branch",
                                        "Expected else branch. If with result value requires then and else branch.",
                                        // (module
                                        // (func
                                        // i32.const 1
                                        // if (result i32)
                                        // i32.const 1
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 0B 01 09 00 41 01 04 7F 41 01 0B 0B",
                                        Failure.Type.INVALID),

                        // If - else without if
                        binaryCase("If - else without if",
                                        "Expected then branch. Else branch requires preceding then branch.",
                                        // (module
                                        // (func
                                        // else
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 04 01 02 00 05 0B 0B",
                                        Failure.Type.INVALID),

                        // Correct if else
                        binaryCase("If - correct if else",
                                        "",
                                        // (module
                                        // (func (export "_main")
                                        // i32.const 1
                                        // if
                                        // else
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 07 09 01 05 5F 6D 61 69 6E 00 00 0A 09 01 07 00 41 01 04 40 0B 0B",
                                        null),
                        // Correct if else with return
                        binaryCase("If - correct if else with return",
                                        "",
                                        // (module
                                        // (func (export "_main")
                                        // i32.const 1
                                        // if (result i32)
                                        // i32.const 1
                                        // else
                                        // i32.const 2
                                        // end
                                        // drop
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 07 09 01 05 5F 6D 61 69 6E 00 00 0A 0F 01 0D 00 41 01 04 7F 41 01 05 41 02 0B 1A 0B",
                                        null),
                        // Branch to unknown label
                        binaryCase("Br - unknown label",
                                        "Unknown branch label 1 (max 0).",
                                        // (module
                                        // (func
                                        // br 1
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 06 01 04 00 0C 01 0B",
                                        Failure.Type.INVALID),
                        // Branch to existing label
                        binaryCase("Br - correct branch",
                                        "",
                                        // (module
                                        // (func (export "_main")
                                        // br 1
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 07 09 01 05 5F 6D 61 69 6E 00 00 0A 0F 01 0D 00 41 01 04 7F 41 01 05 41 02 0B 1A 0B",
                                        null),

                        // Branch if to unknown label
                        binaryCase("Br_if - unknown label",
                                        "Unknown branch label 1 (max 0).",
                                        // (module
                                        // (func
                                        // i32.const 0
                                        // br_if 1
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 08 01 06 00 41 00 0D 01 0B",
                                        Failure.Type.INVALID),

                        // Branch if - condition missing
                        binaryCase("Br_if - missing condition",
                                        "Expected type [i32], but got [].",
                                        // (module
                                        // (func
                                        // block
                                        // br_if 0
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 09 01 07 00 02 40 0D 00 0B 0B",
                                        Failure.Type.INVALID),

                        // Branch if - incorrect condition type
                        binaryCase("Br_if - incorrect condition type (i64)",
                                        "Expected type [i32], but got [i64].",
                                        // (module
                                        // (func
                                        // block
                                        // i64.const 0
                                        // br_if 0
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 0B 01 09 00 02 40 42 00 0D 00 0B 0B",
                                        Failure.Type.INVALID),
                        binaryCase("Br_if - incorrect condition type (f32)",
                                        "Expected type [i32], but got [f32].",
                                        // (module
                                        // (func
                                        // block
                                        // f32.const 0
                                        // br_if 0
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 0E 01 0C 00 02 40 43 00 00 00 00 0D 00 0B 0B",
                                        Failure.Type.INVALID),
                        binaryCase("Br_if - incorrect condition type (f64)",
                                        "Expected type [i32], but got [f64].",
                                        // (module
                                        // (func
                                        // block
                                        // f64.const 0
                                        // br_if 0
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 12 01 10 00 02 40 44 00 00 00 00 00 00 00 00 0D 00 0B 0B",
                                        Failure.Type.INVALID),

                        // Correct branch if
                        binaryCase("Br_if - correct",
                                        "",
                                        // (module
                                        // (func (export "_main")
                                        // block
                                        // i32.const 0
                                        // br_if 0
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 07 09 01 05 5F 6D 61 69 6E 00 00 0A 0B 01 09 00 02 40 41 00 0D 00 0B 0B",
                                        null),

                        // Branch table missing index
                        binaryCase("Br_table - missing index",
                                        "Expected type [i32], but got [].",
                                        // (module
                                        // (func
                                        // block
                                        // br_table 0 1
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 0B 01 09 00 02 40 0E 01 00 01 0B 0B",
                                        Failure.Type.INVALID),

                        // Branch table incorrect index type
                        binaryCase("Br_table - incorrect index type (i64)",
                                        "Expected type [i32], but got [i64].",
                                        // (module
                                        // (func
                                        // block
                                        // i64.const 0
                                        // br_table 0 1
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 0D 01 0B 00 02 40 42 00 0E 01 00 01 0B 0B",
                                        Failure.Type.INVALID),

                        binaryCase("Br_table - incorrect index type (f32)",
                                        "Expected type [i32], but got [f32].",
                                        // (module
                                        // (func
                                        // block
                                        // f32.const 0
                                        // br_table 0 1
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 10 01 0E 00 02 40 43 00 00 00 00 0E 01 00 01 0B 0B",
                                        Failure.Type.INVALID),
                        binaryCase("Br_table - incorrect index type (f64)",
                                        "Expected type [i32], but got [f64].",
                                        // (module
                                        // (func
                                        // block
                                        // f64.const 0
                                        // br_table 0 1
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 14 01 12 00 02 40 44 00 00 00 00 00 00 00 00 0E 01 00 01 0B 0B",
                                        Failure.Type.INVALID),

                        // Branch table to unknown label
                        binaryCase("Br_table - unknown label (last)",
                                        "Unknown branch label 2 (max 1).",
                                        // (module
                                        // (func
                                        // block
                                        // i32.const 0
                                        // br_table 0 1 2
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 0E 01 0C 00 02 40 41 00 0E 02 00 01 02 0B 0B",
                                        Failure.Type.INVALID),
                        binaryCase("Br_table - unknown label (first)",
                                        "Unknown branch label 2 (max 1).",
                                        // (module
                                        // (func
                                        // block
                                        // i32.const 0
                                        // br_table 2 1 0
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 0E 01 0C 00 02 40 41 00 0E 02 02 01 00 0B 0B",
                                        Failure.Type.INVALID),

                        // Branch table with different target function signature
                        binaryCase("Br_table - different target return types (i32 - void)",
                                        "Inconsistent label types. Expected [], but got [i32].",
                                        // (module
                                        // (func (result i32)
                                        // block
                                        // i32.const 0
                                        // br_table 1 0
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 7F 03 02 01 00 0A 0D 01 0B 00 02 40 41 00 0E 01 01 00 0B 0B",
                                        Failure.Type.INVALID),
                        binaryCase("Br_table - different target return types (i32 - f32)",
                                        "Inconsistent label types. Expected [f32], but got [i32].",
                                        // (module
                                        // (func (result i32)
                                        // block (result f32)
                                        // i32.const 0
                                        // br_table 1 0
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 7F 03 02 01 00 0A 0D 01 0B 00 02 7D 41 00 0E 01 01 00 0B 0B",
                                        Failure.Type.INVALID),
                        // Correct branch table
                        binaryCase("Br_table - correct",
                                        "",
                                        // (module
                                        // (func (export "_main") (result f32)
                                        // block (result f32)
                                        // f32.const 1
                                        // i32.const 0
                                        // br_table 1 0
                                        // end
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 7D 03 02 01 00 07 09 01 05 5F 6D 61 69 6E 00 00 0A 12 01 10 00 02 7D 43 00 00 80 3F 41 00 0E 01 01 00 0B 0B",
                                        null),

                        // Return with missing value
                        binaryCase("Return - missing value",
                                        "Expected result types [f32], but got [].",
                                        // (module
                                        // (func (result f32)
                                        // return
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 7D 03 02 01 00 0A 05 01 03 00 0F 0B",
                                        Failure.Type.INVALID),

                        // Return with incorrect type
                        binaryCase("Return - incorrect return type",
                                        "Expected result types [f32], but got [i32].",
                                        // (module
                                        // (func (result f32)
                                        // i32.const 0
                                        // return
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 7D 03 02 01 00 0A 07 01 05 00 41 00 0F 0B",
                                        Failure.Type.INVALID),

                        // Correct return
                        binaryCase("Return - correct",
                                        "",
                                        // (module
                                        // (func (export "_main") (result f32)
                                        // f32.const 0
                                        // return
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 7D 03 02 01 00 07 09 01 05 5F 6D 61 69 6E 00 00 0A 0A 01 08 00 43 00 00 00 00 0F 0B",
                                        null),

                        // Call with missing parameter
                        binaryCase("Call - missing parameter",
                                        "Expected param types [i32], but got [].",
                                        // (module
                                        // (func (param i32))
                                        // (func
                                        // call 0
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 08 02 60 01 7F 00 60 00 00 03 03 02 00 01 0A 09 02 02 00 0B 04 00 10 00 0B",
                                        Failure.Type.INVALID),
                        // Call with incorrect parameter
                        binaryCase("Call - incorrect parameter",
                                        "Expected param types [i32], but got [f32].",
                                        // (module
                                        // (func (param i32))
                                        // (func
                                        // f32.const 0
                                        // call 0
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 08 02 60 01 7F 00 60 00 00 03 03 02 00 01 0A 0E 02 02 00 0B 09 00 43 00 00 00 00 10 00 0B",
                                        Failure.Type.INVALID),

                        // Correct call
                        binaryCase("Call - correct",
                                        "",
                                        // (module
                                        // (func (param f32) (result i32)
                                        // local.get 0
                                        // i32.trunc_f32_s
                                        // )
                                        // (func (export "_main")
                                        // f32.const 1.5
                                        // call 0
                                        // drop
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 09 02 60 01 7D 01 7F 60 00 00 03 03 02 00 01 07 09 01 05 5F 6D 61 69 6E 00 01 0A 12 02 05 00 20 00 A8 0B 0A 00 43 00 00 C0 3F 10 00 1A 0B",
                                        null),

                        // Indirect call with missing index
                        binaryCase("Call_indirect - missing index",
                                        "Expected type [i32], but got [].",
                                        // (module
                                        // (table 1 funcref)
                                        // (type (func))
                                        // (func
                                        // call_indirect 0
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 04 04 01 70 00 01 0A 07 01 05 00 11 00 00 0B",
                                        Failure.Type.INVALID),

                        // Indirect call with missing table
                        binaryCase("Call_indirect - missing table",
                                        "Missing table.",
                                        // (module
                                        // (type (func))
                                        // (func
                                        // i32.const 0
                                        // call_indirect (type 0)
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 09 01 07 00 41 00 11 00 00 0B",
                                        Failure.Type.INVALID),

                        // Indirect call with missing type
                        binaryCase("Call_indirect - missing type",
                                        "Function type variable 1 out of range. (max 0)",
                                        // (module
                                        // (type (func))
                                        // (table 1 funcref)
                                        // (func
                                        // i32.const 0
                                        // call_indirect (type 1)
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 04 04 01 70 00 01 0A 09 01 07 00 41 00 11 01 00 0B",
                                        Failure.Type.INVALID),

                        binaryCase("Call_indirect - correct",
                                        "",
                                        // (module
                                        // (type (func))
                                        // (type (func (result i32)))
                                        // (table 1 funcref)
                                        // (func $f0 (type 1)
                                        // i32.const 0
                                        // )
                                        // (elem (i32.const 0) $f0)
                                        // (func (export "_main")
                                        // i32.const 0
                                        // call_indirect (type 1)
                                        // drop
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 08 02 60 00 00 60 00 01 7F 03 03 02 01 00 04 04 01 70 00 01 07 09 01 05 5F 6D 61 69 6E 00 01 09 07 01 00 41 00 0B 01 00 0A 0F 02 04 00 41 00 0B 08 00 41 00 11 01 00 1A 0B",
                                        null),

                        binaryCase("Invalid instruction",
                                        "Unknown opcode: 0x06",

                                        // (module
                                        // (func
                                        // 0x06
                                        // drop
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 04 01 60 00 00 03 02 01 00 0A 07 01 05 00 06 00 1A 0B",
                                        Failure.Type.MALFORMED),

                        binaryCase("Invalid return type",
                                        "Invalid value type: 0x20",
                                        // (module
                                        // (func (result 0x20)
                                        // i32.const 0
                                        // )
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 20 03 02 01 00 0A 06 01 04 00 41 00 0B",
                                        Failure.Type.MALFORMED));
    }

    private final String expectedErrorMessage;
    private final byte[] bytecode;
    private final Failure.Type expectedFailureType;

    @SuppressWarnings("unused")
    public ValidationSuite(String basename, String expectedErrorMessage, byte[] bytecode, Failure.Type failureType) {
        this.expectedErrorMessage = expectedErrorMessage;
        this.bytecode = bytecode;
        this.expectedFailureType = failureType;
    }

    @Override
    @Test
    public void test() throws IOException {
        final Context context = Context.newBuilder(WasmLanguage.ID).build();
        final Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(bytecode), "dummy_main").build();
        try {
            context.eval(source).getMember("_main").execute();
        } catch (final PolyglotException e) {
            final Value actualFailureObject = e.getGuestObject();

            if (e.isInternalError()) {
                throw e;
            }

            Assert.assertNotNull(actualFailureObject);
            Assert.assertTrue(actualFailureObject.hasMember("failureType"));

            Assert.assertEquals("unexpected error message", expectedErrorMessage, e.getMessage());
            final String failureType = actualFailureObject.getMember("failureType").asString();
            Assert.assertEquals("unexpected failure type", expectedFailureType.name, failureType);
            return;
        }
        if (expectedFailureType != null) {
            throw new AssertionError("expected to be invalid");
        }
    }

    private static Object[] stringCase(String name, String errorMessage, String textString, Failure.Type failureType) {
        try {
            return new Object[]{name, errorMessage, compileWat(name, textString + "(func (export \"_main\") (result i32) i32.const 42)"), failureType};
        } catch (final IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object[] binaryCase(String name, String errorMessage, String hexString, Failure.Type failureType) {
        return new Object[]{name, errorMessage, hexStringToByteArray(hexString), failureType};
    }
}
