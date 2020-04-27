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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import org.graalvm.wasm.utils.SystemProperties;
import org.graalvm.wasm.utils.cases.WasmBinaryCase;
import org.graalvm.wasm.utils.cases.WasmCase;
import org.graalvm.wasm.utils.cases.WasmCaseData;
import org.graalvm.wasm.utils.cases.WasmStringCase;
import org.junit.Test;

import org.graalvm.wasm.test.WasmSuiteBase;

public class ValidationSuite extends WasmSuiteBase {
    private WasmCase[] testCases = {
                    // # 3.2 Types

                    // ## 3.2.1 Limits
                    // See 3.2.3 and 3.2.4

                    // ## 3.2.2 Function Types
                    // The arity `m` must not be larger than 1.
                    // Validated in: SymbolTable.allocateFunctionType
                    binaryCase(
                                    "Function: cannot return more than one value",
                                    "A function can return at most one result.",
                                    // (module
                                    // (func $f (result i32) i32.const 42 i32.const 42)
                                    // )
                                    "0061 736d 0100 0000 0105 0160 0002 7f03 0201 000a 0801 0600 412a 412a 0b"),

                    // ## 3.2.3 Table types
                    // The limits `limits` must be valid within range `2^32`.
                    // Validated in: BinaryParser.readTableLimits
                    // Note: Limit is lowered to `2^31 - 1` due to Java indices limit.
                    stringCase(
                                    "Table: initial size out of bounds",
                                    "Invalid initial table size, must be less than upper bound: 2147483648 should be <= 2147483647.",
                                    "(table $table1 2147483648 funcref)"),
                    stringCase(
                                    "Table: initial size out of bounds",
                                    "Invalid max table size, must be less than upper bound: 2147483648 should be <= 2147483647.",
                                    "(table $table1 1 2147483648 funcref)"),
                    stringCase(
                                    "Table: max size lower than initial size",
                                    "Invalid initial table size, must be less than max table size: 2 should be <= 1.",
                                    "(table $table1 2 1 funcref)"),

                    // ## 3.2.4 Memory types
                    // The limits `limits` must be valid within range `2^16`.
                    // Validated in: BinaryParser.readMemoryLimits
                    stringCase(
                                    "Memory: initial size out of bounds",
                                    "Invalid initial memory size, must be less than upper bound: 2147483648 should be <= 65536.",
                                    "(memory $memory1 2147483648)"),
                    stringCase(
                                    "Memory: max size out of bounds",
                                    "Invalid max memory size, must be less than upper bound: 2147483648 should be <= 65536.",
                                    "(memory $memory1 1 2147483648)"),
                    stringCase(
                                    "Memory: max size lower than initial size",
                                    "Invalid initial memory size, must be less than max memory size: 2 should be <= 1.",
                                    "(memory $memory1 2 1)"),

                    // ## 3.2.5 Global types
                    // (No constraints)

                    // # 3.4 Modules

                    // ## 3.4.1 Functions
                    // The type `C.types[x]` must be defined in the context.
                    // Validated in: SymbolTable.allocateFunction
                    stringCase(
                                    "Function: invalid type index",
                                    "Function type out of bounds: 1 should be < 1.",
                                    "(type (func (result i32))) (func (export \"f\") (type 1))"),
                    stringCase(
                                    "Function: invalid type index",
                                    "Function type out of bounds: 4294967254 should be < 1.",
                                    "(type (func (result i32))) (func (export \"f\") (type 4294967254))"),

                    // Under the context `C'`, the expression `express` must be valid with type
                    // `t2`.
                    // TODO

                    // ## 3.4.2 Tables
                    // The table type `tabletype` must be valid.
                    // See 3.2.3.

                    // ## 3.4.3 Memories
                    // The table type `memtype` must be valid.
                    // See 3.2.4

                    // ## 3.4.4 Globals
                    // The global type `mut t` must be valid.
                    // See 3.2.5

                    // The expression `expr` must be valid with result type `[t]`.
                    // TODO

                    // The `expr` must be constant.
                    // TODO

                    // ## 3.4.5 Element Segments
                    // The table `C.tables[x]` must be defined in the context.
                    // Validated in: BinaryParser.readElementSection
                    binaryCase(
                                    "Element segment: invalid table index",
                                    "Invalid table index: 5 should = 0.",
                                    // (module
                                    // (table 1 funcref)
                                    // (elem 5 (i32.const 0) $f1)
                                    // (func $f1 (result i32) i32.const 42)
                                    // )
                                    "0061 736d 0100 0000 0105 0160 0001 7f03 0201 0004 0401 7000 0109 0701 0541 000b 0100 0a06 0104 0041 2a0b"),

                    // The element type `elemtype` must be `funcref`.
                    // Validated in: BinaryParser.readTableSection and
                    // BinaryParser.readImportSection
                    // TODO

                    // The expression `expr` must be valid with result type `[i32]`.
                    // TODO

                    // The `expr` must be constant.
                    // TODO

                    // For each `y` in `y*`, the function `C.funcs[y]` must be defined in the
                    // context.
                    // Validated in: SymbolTable.function
                    stringCase(
                                    "Element segments: invalid function index",
                                    "Function index out of bounds: 1 should be < 1.",
                                    "(table 1 funcref) (elem (i32.const 0) 1)"),

                    // ## 3.4.6 Data Segments
                    // The memory `C.mems[x]` must be defined in the context.
                    // Validated in: BinaryParser.readDataSection
                    binaryCase(
                                    "Data segment: invalid memory index",
                                    "Invalid memory index, only the memory index 0 is currently supported.: 5 should = 0.",
                                    // (module
                                    // (memory 1)
                                    // (data 5 (i32.const 0) "Hi")
                                    // )
                                    "0061 736d 0100 0000 0503 0100 010b 0801 0541 000b 0248 69"),

                    // The expression `expr` must be valid with result type `[i32]`.
                    // TODO

                    // The `expr` must be constant.
                    // TODO

                    // ## 3.4.7 Start function
                    // The function `C.funcs[x]` must be defined in the context.
                    // Validated in: SymbolTable.function
                    stringCase(
                                    "Start function: invalid index",
                                    "Function index out of bounds: 1 should be < 1.",
                                    "(start 1)"),

                    // The type of `C.funcs[x]` must be [] -> [].
                    // Validated in SymbolTable.startFunction
                    stringCase(
                                    "Start function: returns a value",
                                    "Start function cannot return a value.",
                                    "(start 0) (func (result i32) i32.const 42)"),
                    stringCase(
                                    "Start function: takes arguments",
                                    "Start function cannot take arguments.",
                                    "(start 0) (func (param i32))"),

                    // The length of `C.tables` must not be larger than 1.
                    // Validated in: BinaryParser.readTableSection
                    stringCase(
                                    "Module: two tables (2 locals)",
                                    "Can import or declare at most one table per module: 2 should be <= 1.",
                                    "(table $table1 1 funcref) (table $table2 1 funcref)"),
                    stringCase(
                                    "Module: two tables (1 local and 1 import)",
                                    "Can import or declare at most one table per module: 2 should be <= 1.",
                                    "(table $table2 (import \"some\" \"table\") 1 funcref) (table $table1 1 funcref)"),
                    // Validated in: SymbolTable.validateSingleTable
                    stringCase(
                                    "Module: two tables (2 imports)",
                                    "A table has been already imported in the module.",
                                    "(table $table1 (import \"some\" \"table\") 1 funcref) (table $table2 (import \"some\" \"table\") 1 funcref)"),

                    // The length of `C.mems` must not be larger than 1.
                    // Validated in: BinaryParser.readMemorySection
                    stringCase(
                                    "Module: two memories (2 locals)",
                                    "Can import or declare at most one memory per module: 2 should be <= 1.",
                                    "(memory $mem1 1) (memory $mem2 1)"),
                    stringCase(
                                    "Module: two memories (1 local and 1 import)",
                                    "Can import or declare at most one memory per module: 2 should be <= 1.",
                                    "(memory $mem1 (import \"some\" \"memory\") 1) (memory $mem2 1)"),
                    // Validated in: SymbolTable.validateSingleMemory
                    stringCase(
                                    "Module: two memories (2 imports)",
                                    "Memory has been already imported in the module.",
                                    "(memory $mem1 (import \"some\" \"memory\") 1) (memory $mem2 (import \"some\" \"memory\") 1)"),

                    // All export names `export_i.name` must be different.
                    // Validated in: SymbolTable.checkUniqueExport
                    stringCase(
                                    "Module: duplicate export (2 functions)",
                                    "All export names must be different, but 'a' is exported twice.",
                                    "(func (export \"a\") (result i32) i32.const 42) (func (export \"a\") (result i32) i32.const 42)"),
                    stringCase(
                                    "Module: duplicate export (function and memory)",
                                    "All export names must be different, but 'a' is exported twice.",
                                    "(func (export \"a\") (result i32) i32.const 42) (memory (export \"a\") 1)"),
    };

    private static Properties opts = SystemProperties.createFromOptions(
                    "zero-memory = false\n" +
                                    "interpreter-iterations = 1\n" +
                                    "sync-noinline-iterations = 0\n" +
                                    "sync-inline-iterations = 0\n" +
                                    "async-iterations = 0\n");

    @Override
    protected Collection<? extends WasmCase> collectStringTestCases() {
        return Arrays.asList(testCases);
    }

    @Override
    @Test
    public void test() throws IOException {
        // This is here just to make mx aware of the test suite class.
        super.test();
    }

    private static WasmStringCase stringCase(String name, String errorMessage, String snippet) {
        String source = "(module\n" +
                        snippet +
                        "(func (export \"_main\") (result i32) i32.const 42)\n )";
        return WasmCase.create(name, WasmCase.expectedThrows(errorMessage, WasmCaseData.ErrorType.Validation), source, opts);
    }

    private static WasmBinaryCase binaryCase(String name, String errorMessage, String hexString) {
        return WasmCase.create(name, WasmCase.expectedThrows(errorMessage, WasmCaseData.ErrorType.Validation), hexStringToByteArray(hexString), opts);
    }

    private static byte[] hexStringToByteArray(String input) {
        String s = input.replaceAll("\\s+", "");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
