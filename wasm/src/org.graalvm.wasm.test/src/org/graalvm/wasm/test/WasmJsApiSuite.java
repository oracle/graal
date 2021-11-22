/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test;

import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.ModuleLimits;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.WasmVoidResult;
import org.graalvm.wasm.api.ByteArrayBuffer;
import org.graalvm.wasm.api.Dictionary;
import org.graalvm.wasm.api.Executable;
import org.graalvm.wasm.api.ImportExportKind;
import org.graalvm.wasm.api.ModuleExportDescriptor;
import org.graalvm.wasm.api.ModuleImportDescriptor;
import org.graalvm.wasm.api.Sequence;
import org.graalvm.wasm.api.ValueType;
import org.graalvm.wasm.api.WebAssembly;
import org.graalvm.wasm.constants.Sizes;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.exception.WasmJsApiException;
import org.graalvm.wasm.globals.WasmGlobal;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.testutil.TestutilModule;
import org.graalvm.wasm.utils.Assert;
import org.junit.Test;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;

public class WasmJsApiSuite {
    @Test
    public void testCompile() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmModule module = wasm.moduleDecode(binaryWithExports);
            try {
                HashMap<String, ModuleExportDescriptor> exports = new HashMap<>();
                int i = 0;
                while (i < WebAssembly.moduleExports(module).getArraySize()) {
                    final ModuleExportDescriptor d = (ModuleExportDescriptor) WebAssembly.moduleExports(module).readArrayElement(i);
                    exports.put(d.name(), d);
                    i++;
                }
                Assert.assertEquals("Should export main.", ImportExportKind.function, exports.get("main").kind());
                Assert.assertEquals("Should export memory.", ImportExportKind.memory, exports.get("memory").kind());
                Assert.assertEquals("Should export global __heap_base.", ImportExportKind.global, exports.get("__heap_base").kind());
                Assert.assertEquals("Should export global __data_end.", ImportExportKind.global, exports.get("__data_end").kind());
                Assert.assertEquals("Should have empty imports.", 0L, WebAssembly.moduleImports(module).getArraySize());
            } catch (InvalidArrayIndexException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiate() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithExports, null);
            try {
                final Object main = WebAssembly.instanceExport(instance, "main");
                final Object result = InteropLibrary.getUncached(main).execute(main);
                Assert.assertEquals("Should return 42 from main.", 42, InteropLibrary.getUncached(result).asInt(result));
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithImports() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            Dictionary importObject = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{
                                            "inc", new Executable(args -> ((int) args[0]) + 1)
                            }),
            });
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithImportsAndExports, importObject);
            try {
                final Object addPlusOne = WebAssembly.instanceExport(instance, "addPlusOne");
                final Object result = InteropLibrary.getUncached(addPlusOne).execute(addPlusOne, 17, 3);
                Assert.assertEquals("17 + 3 + 1 = 21.", 21, InteropLibrary.getUncached(result).asInt(result));
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithImportMemory() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmMemory memory = WebAssembly.memAlloc(4, 8);
            final Dictionary importObject = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{
                                            "defaultMemory", memory
                            }),
            });
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithMemoryImport, importObject);
            try {
                final Object initZero = WebAssembly.instanceExport(instance, "initZero");
                Assert.assertEquals("Must be zero initially.", 0, memory.load_i32(null, 0));
                InteropLibrary.getUncached(initZero).execute(initZero);
                Assert.assertEquals("Must be 174 after initialization.", 174, memory.load_i32(null, 0));
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithExportMemory() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            WasmInstance instance = moduleInstantiate(wasm, binaryWithMemoryExport, null);
            try {
                final WasmMemory memory = (WasmMemory) WebAssembly.instanceExport(instance, "memory");
                final Object readZero = WebAssembly.instanceExport(instance, "readZero");
                memory.store_i32(null, 0, 174);
                final Object result = InteropLibrary.getUncached(readZero).execute(readZero);
                Assert.assertEquals("Must be 174.", 174, InteropLibrary.getUncached(result).asInt(result));
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithImportTable() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmTable table = WebAssembly.tableAlloc(4, 8);
            Dictionary importObject = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{
                                            "defaultTable", table
                            }),
            });
            WebAssembly.tableWrite(table, 0, new WasmFunctionInstance(context, null,
                            new RootNode(context.language()) {
                                @Override
                                public Object execute(VirtualFrame frame) {
                                    return 210;
                                }
                            }.getCallTarget()));
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithTableImport, importObject);
            try {
                final Object callFirst = WebAssembly.instanceExport(instance, "callFirst");
                Object result = InteropLibrary.getUncached(callFirst).execute(callFirst);
                Assert.assertEquals("Must return 210.", 210, InteropLibrary.getUncached(result).asInt(result));
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithExportTable() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithTableExport, null);
            try {
                final WasmTable table = (WasmTable) WebAssembly.instanceExport(instance, "defaultTable");
                final Object result = InteropLibrary.getUncached().execute(WebAssembly.tableRead(table, 0), 9);
                Assert.assertEquals("Must be 81.", 81, result);
            } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void checkInstantiateWithImportGlobal(byte[] binaryWithGlobalImport, String globalType, Object globalValue) throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmGlobal global = WebAssembly.globalAlloc(ValueType.valueOf(globalType), false, globalValue);
            Dictionary importObject = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{
                                            "defaultGlobal", global
                            }),
            });
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithGlobalImport, importObject);
            try {
                InteropLibrary interop = InteropLibrary.getUncached();
                final Object readGlobal1 = WebAssembly.instanceExport(instance, "readGlobal1");
                final Object readGlobal2 = WebAssembly.instanceExport(instance, "readGlobal2");
                final Object result1 = interop.execute(readGlobal1);
                final Object result2 = interop.execute(readGlobal2);
                Assert.assertEquals("Must be " + globalValue + " initially.", globalValue, result1);
                Assert.assertEquals("Must be " + globalValue + " initially.", globalValue, result2);
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithImportGlobalI32() throws IOException {
        checkInstantiateWithImportGlobal(binaryWithGlobalImportI32, "i32", 1234567890);
    }

    @Test
    public void testInstantiateWithImportGlobalI64() throws IOException {
        checkInstantiateWithImportGlobal(binaryWithGlobalImportI64, "i64", 1234567890123456789L);
    }

    @Test
    public void testInstantiateWithImportGlobalF32() throws IOException {
        checkInstantiateWithImportGlobal(binaryWithGlobalImportF32, "f32", (float) Math.PI);
    }

    @Test
    public void testInstantiateWithImportGlobalF64() throws IOException {
        checkInstantiateWithImportGlobal(binaryWithGlobalImportF64, "f64", Math.E);
    }

    @Test
    public void testInstantiateWithExportGlobal() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithGlobalExport, null);
            try {
                final WasmGlobal global = (WasmGlobal) WebAssembly.instanceExport(instance, "exportedGlobal");
                Assert.assertEquals("Exported global must be 1096.", 1096, global.loadAsInt());
                final Object setGlobal = WebAssembly.instanceExport(instance, "setGlobal");
                final Object getGlobal = WebAssembly.instanceExport(instance, "getGlobal");
                InteropLibrary interop = InteropLibrary.getUncached();
                final Object result1 = interop.execute(getGlobal);
                Assert.assertEquals("Must be 2345 initially.", 2345, interop.asInt(result1));
                interop.execute(setGlobal, 25);
                final Object result2 = interop.execute(getGlobal);
                Assert.assertEquals("Must be 25 later.", 25, interop.asInt(result2));
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateModuleTwice() throws IOException {
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            WasmModule module = wasm.moduleDecode(binaryWithExports);
            Object importObject = new Dictionary();
            wasm.moduleInstantiate(module, importObject);
            wasm.moduleInstantiate(module, importObject);
        });
    }

    @Test
    public void testInstantiateWithUnicodeExport() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithUnicodeExport, null);
            try {
                final Object euroSignFn = WebAssembly.instanceExport(instance, "\u20AC");
                final Object result = InteropLibrary.getUncached(euroSignFn).execute(euroSignFn);
                Assert.assertEquals("Result should be 42", 42, InteropLibrary.getUncached(result).asInt(result));
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testExportMemoryTwice() throws IOException, InterruptedException {
        final byte[] exportMemoryTwice = compileWat("exportMemoryTwice", "(memory 1) (export \"a\" (memory 0)) (export \"b\" (memory 0))");
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, exportMemoryTwice, null);
            try {
                final InteropLibrary lib = InteropLibrary.getUncached();
                final Object memoryABuffer = WebAssembly.instanceExport(instance, "a");
                final Object memoryBBuffer = WebAssembly.instanceExport(instance, "b");
                lib.writeArrayElement(memoryABuffer, 0, (byte) 42);
                final byte readValue = lib.asByte(lib.readArrayElement(memoryBBuffer, 0));
                Assert.assertEquals("Written value should correspond to read value", (byte) 42, readValue);
            } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testExportTableTwice() throws IOException, InterruptedException {
        final byte[] exportMemoryTwice = compileWat("exportTableTwice", "(module (table 1 funcref) (export \"a\" (table 0)) (export \"b\" (table 0)))");
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, exportMemoryTwice, null);
            final InteropLibrary lib = InteropLibrary.getUncached();
            try {
                final Object f = new WasmFunctionInstance(context, null,
                                new RootNode(context.language()) {
                                    @Override
                                    public Object execute(VirtualFrame frame) {
                                        return 42;
                                    }
                                }.getCallTarget());
                final Object writeTable = wasm.readMember("table_write");
                final Object readTable = wasm.readMember("table_read");
                final Object a = WebAssembly.instanceExport(instance, "a");
                final Object b = WebAssembly.instanceExport(instance, "b");
                lib.execute(writeTable, a, 0, f);
                final Object readValue = lib.execute(readTable, b, 0);
                Assert.assertEquals("Written function should correspond ro read function", 42, lib.asInt(lib.execute(readValue)));
            } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testExportSameFunctionWithDifferentNames() throws IOException, InterruptedException {
        final byte[] sameFunctionWithDifferentNames = compileWat("sameFunctionWithDifferentNames", "(module (func $f (result i32) i32.const 1) (export \"f1\" (func $f)) (export \"f2\" (func $f)))");
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, sameFunctionWithDifferentNames, null);
            final Object f1 = WebAssembly.instanceExport(instance, "f1");
            final Object f2 = WebAssembly.instanceExport(instance, "f2");
            Assert.assertTrue("Returned function instances must be reference equal", f1 == f2);
        });
    }

    @Test
    public void testExportSameFunctionInAndOutsideTable() throws IOException, InterruptedException {
        final byte[] sameFunctionInExportAndTable = compileWat("sameFunctionInExportAndTable",
                        "(module (func $f (result i32) i32.const 1) (table 1 funcref) (elem (i32.const 0) $f) (export \"f\" (func $f)) (export \"t\" (table 0)))");
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, sameFunctionInExportAndTable, null);
            final Object f = WebAssembly.instanceExport(instance, "f");
            final WasmTable t = (WasmTable) WebAssembly.instanceExport(instance, "t");
            final Object fInTable = WebAssembly.tableRead(t, 0);
            Assert.assertTrue("Returned function instances must be reference equal", f == fInTable);
        });
    }

    @Test
    public void testExportSameFunctionAtDifferentTableIndices() throws IOException, InterruptedException {
        final byte[] sameFunctionInExportAndTable = compileWat("sameFunctionInExportAndTable",
                        "(module (func $f (result i32) i32.const 1) (table 2 funcref) (elem (i32.const 0) $f) (elem (i32.const 1) $f) (export \"t\" (table 0)))");
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, sameFunctionInExportAndTable, null);
            final WasmTable t = (WasmTable) WebAssembly.instanceExport(instance, "t");
            final Object f1 = WebAssembly.tableRead(t, 0);
            final Object f2 = WebAssembly.tableRead(t, 1);
            Assert.assertTrue("Returned function instances must be reference equal", f1 == f2);
        });
    }

    @Test
    public void testExportSameFunctionInDifferentModules() throws IOException, InterruptedException {
        final byte[] m1 = compileWat("export", "(module (func $f (result i32) i32.const 42) (export \"f\" (func $f)))");
        final byte[] m2 = compileWat("import", "(module (import \"m\" \"f\" (func $f (result i32))) (export \"f\" (func $f)))");
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance m1Instance = moduleInstantiate(wasm, m1, null);
            final InteropLibrary lib = InteropLibrary.getUncached();
            try {
                final Object m1Function = WebAssembly.instanceExport(m1Instance, "f");
                final Dictionary d = new Dictionary();
                d.addMember("m", Dictionary.create(new Object[]{
                                "f", m1Function
                }));
                final WasmInstance m2Instance = moduleInstantiate(wasm, m2, d);
                final Object m2Function = WebAssembly.instanceExport(m2Instance, "f");
                Assert.assertTrue("Returned function instances must be reference equal", m1Function == m2Function);
                final Object m1Value = lib.execute(m1Function);
                final Object m2Value = lib.execute(m2Function);
                Assert.assertEquals("Return value of functions is equal", m1Value, m2Value);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testExportImportedFunctionInDifferentModules() throws IOException, InterruptedException {
        final byte[] m1 = compileWat("export", "(module (import \"a\" \"f\" (func $f (result i32))) (export \"f\" (func $f)))");
        final byte[] m2 = compileWat("import", "(module (import \"b\" \"f\" (func $f (result i32))) (func (result i32) (call $f)) (export \"f\" (func $f)))");
        runTest(context -> {
            final Dictionary importObject = new Dictionary();
            importObject.addMember("a", Dictionary.create(new Object[]{
                            "f", new Executable(args -> 2)
            }));
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance m1Instance = moduleInstantiate(wasm, m1, importObject);
            final InteropLibrary lib = InteropLibrary.getUncached();
            try {
                final Object m1Function = WebAssembly.instanceExport(m1Instance, "f");
                final Dictionary d = new Dictionary();
                d.addMember("b", Dictionary.create(new Object[]{
                                "f", m1Function
                }));
                final WasmInstance m2Instance = moduleInstantiate(wasm, m2, d);
                final Object m2Function = WebAssembly.instanceExport(m2Instance, "f");
                Assert.assertTrue("Returned function instances must be reference equal", m1Function == m2Function);
                final Object m1Value = lib.execute(m1Function);
                final Object m2Value = lib.execute(m2Function);
                Assert.assertEquals("Return value of functions is equal", m1Value, m2Value);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testExportSameFunctionInDifferentModuleTables() throws IOException, InterruptedException {
        final byte[] m1 = compileWat("export", "(module (func $f (result i32) i32.const 42) (table 1 funcref) (elem (i32.const 0) $f) (export \"t\" (table 0)))");
        final byte[] m2 = compileWat("import", "(module (import \"m\" \"t\" (table $t 1 funcref)) (export \"t\" (table $t)))");
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance m1Instance = moduleInstantiate(wasm, m1, null);
            final InteropLibrary lib = InteropLibrary.getUncached();
            try {
                final Object m1Table = WebAssembly.instanceExport(m1Instance, "t");
                final Object m1Function = WebAssembly.tableRead((WasmTable) m1Table, 0);
                final Dictionary d = new Dictionary();
                d.addMember("m", Dictionary.create(new Object[]{
                                "t", m1Table
                }));
                final WasmInstance m2Instance = moduleInstantiate(wasm, m2, d);
                final Object m2Table = WebAssembly.instanceExport(m2Instance, "t");
                final Object m2Function = WebAssembly.tableRead((WasmTable) m2Table, 0);
                Assert.assertTrue("Returned function instances must be reference equal", m1Function == m2Function);
                final Object m1Value = lib.execute(m1Function);
                final Object m2Value = lib.execute(m2Function);
                Assert.assertEquals("Return value of functions is equal", m1Value, m2Value);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testExportSameFunctionInDifferentModuleTable() throws IOException, InterruptedException {
        final byte[] m1 = compileWat("export", "(module (func $f (result i32) i32.const 42) (export \"f\" (func $f)))");
        final byte[] m2 = compileWat("import", "(module (import \"m\" \"f\" (func $f (result i32))) (table 1 funcref) (elem (i32.const 0) $f) (export \"t\" (table 0)))");
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance m1Instance = moduleInstantiate(wasm, m1, null);
            final InteropLibrary lib = InteropLibrary.getUncached();
            try {
                final Object m1Function = WebAssembly.instanceExport(m1Instance, "f");
                final Dictionary d = new Dictionary();
                d.addMember("m", Dictionary.create(new Object[]{
                                "f", m1Function
                }));
                final WasmInstance m2Instance = moduleInstantiate(wasm, m2, d);
                final Object m2Table = WebAssembly.instanceExport(m2Instance, "t");
                final Object m2Function = WebAssembly.tableRead((WasmTable) m2Table, 0);
                Assert.assertTrue("Returned function instances must be reference equal", m1Function == m2Function);
                final Object m1Value = lib.execute(m1Function);
                final Object m2Value = lib.execute(m2Function);
                Assert.assertEquals("Return value of functions is equal", m1Value, m2Value);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testExportImportedFunctionInDifferentModuleTable() throws IOException, InterruptedException {
        final byte[] m1 = compileWat("export", "(module (import \"a\" \"f\" (func $f (result i32))) (table $f 1 funcref) (elem (i32.const 0) $f) (export \"t\" (table 0)) (export \"f\" (func $f)))");
        final byte[] m2 = compileWat("import", "(module (import \"b\" \"t\" (table $t 1 funcref)) (export \"t\" (table 0)))");
        runTest(context -> {
            final Dictionary importObject = new Dictionary();
            importObject.addMember("a", Dictionary.create(new Object[]{
                            "f", new Executable(args -> 2)
            }));
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance m1Instance = moduleInstantiate(wasm, m1, importObject);
            final InteropLibrary lib = InteropLibrary.getUncached();
            try {
                final Object m1Function = WebAssembly.instanceExport(m1Instance, "f");
                final Object m1Table = WebAssembly.instanceExport(m1Instance, "t");
                final Object m1TableFunction = WebAssembly.tableRead((WasmTable) m1Table, 0);
                Assert.assertTrue("Returned function instances must be reference equal", m1Function == m1TableFunction);
                Object m1Value = lib.execute(m1Function);
                final Object m1TableValue = lib.execute(m1TableFunction);
                Assert.assertEquals("Return value of functions is equal", m1Value, m1TableValue);
                final Dictionary d = new Dictionary();
                d.addMember("b", Dictionary.create(new Object[]{
                                "f", m1Function,
                                "t", m1Table
                }));
                final WasmInstance m2Instance = moduleInstantiate(wasm, m2, d);
                final Object m2Table = WebAssembly.instanceExport(m2Instance, "t");
                final Object m2Function = WebAssembly.tableRead((WasmTable) m2Table, 0);
                Assert.assertTrue("Returned function instances must be reference equal", m1Function == m2Function);
                m1Value = lib.execute(m1Function);
                final Object m2Value = lib.execute(m2Function);
                Assert.assertEquals("Return value of functions is equal", m1Value, m2Value);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testImportOrder() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmModule module = wasm.moduleDecode(binaryWithMixedImports);
            final Sequence<ModuleImportDescriptor> moduleImports = WebAssembly.moduleImports(module);
            String[] expected = new String[]{"f1", "g1", "t", "m", "g2", "f2"};
            try {
                Assert.assertEquals("Must import all members.", 6L, moduleImports.getArraySize());
                for (int i = 0; i < moduleImports.getArraySize(); i++) {
                    Assert.assertEquals("Module member " + i + " should correspond to the expected import.", expected[i], ((ModuleImportDescriptor) moduleImports.readArrayElement(i)).name());
                }
            } catch (InvalidArrayIndexException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testExportCountsLimit() throws IOException {
        runTest(context -> {
            ModuleLimits limits = null;
            context.readModule(binaryWithMixedExports, limits);

            final int noLimit = Integer.MAX_VALUE;
            limits = new ModuleLimits(noLimit, noLimit, noLimit, noLimit, 6, noLimit, noLimit, noLimit, noLimit, noLimit, noLimit, noLimit, noLimit, noLimit);
            context.readModule(binaryWithMixedExports, limits);

            try {
                limits = new ModuleLimits(noLimit, noLimit, noLimit, noLimit, 5, noLimit, noLimit, noLimit, noLimit, noLimit, noLimit, noLimit, noLimit, noLimit);
                context.readModule(binaryWithMixedExports, limits);
                Assert.fail("Should have failed - export count exceeds the limit");
            } catch (WasmException ex) {
                Assert.assertEquals("Parsing error expected", ExceptionType.PARSE_ERROR, ex.getExceptionType());
            }
        });
    }

    @Test
    public void testTableInstanceOutOfBoundsGet() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithMixedExports, null);
            final InteropLibrary lib = InteropLibrary.getUncached();

            // We should be able to get element 1.
            try {
                final Object table = WebAssembly.instanceExport(instance, "t");
                final Object readTable = wasm.readMember("table_read");
                lib.execute(readTable, table, 0);
            } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            }

            // But not element 2.
            try {
                final Object table = WebAssembly.instanceExport(instance, "t");
                final Object readTable = wasm.readMember("table_read");
                lib.execute(readTable, table, 1);
                Assert.fail("Should have failed - export count exceeds the limit");
            } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            } catch (WasmJsApiException e) {
                Assert.assertEquals("Range error expected", WasmJsApiException.Kind.RangeError, e.kind());
            }
        });
    }

    @Test
    public void testTableInstanceOutOfBoundsSet() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithMixedExports, null);
            final InteropLibrary lib = InteropLibrary.getUncached();

            WasmContext wasmContext = WasmContext.get(null);
            final WasmFunctionInstance functionInstance = new WasmFunctionInstance(
                            wasmContext,
                            null,
                            new RootNode(wasmContext.language()) {
                                @Override
                                public Object execute(VirtualFrame frame) {
                                    return 42;
                                }
                            }.getCallTarget());

            // We should be able to set element 1.
            try {
                final Object table = WebAssembly.instanceExport(instance, "t");
                final Object writeTable = wasm.readMember("table_write");
                lib.execute(writeTable, table, 0, functionInstance);
            } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            }

            // But not element 2.
            try {
                final Object table = WebAssembly.instanceExport(instance, "t");
                final Object writeTable = wasm.readMember("table_write");
                lib.execute(writeTable, table, 1, functionInstance);
                Assert.fail("Should have failed - export count exceeds the limit");
            } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            } catch (WasmJsApiException e) {
                Assert.assertEquals("Range error expected", WasmJsApiException.Kind.RangeError, e.kind());
            }
        });
    }

    @Test
    public void testTableInstanceGrowLimit() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithMixedExports, null);
            final InteropLibrary lib = InteropLibrary.getUncached();

            // We should be able to grow the table to 10,000,000.
            try {
                final Object table = WebAssembly.instanceExport(instance, "t");
                final Object grow = wasm.readMember("table_grow");
                lib.execute(grow, table, 9999999);
            } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            }

            // But growing to 10,000,001 should fail.
            try {
                final Object table = WebAssembly.instanceExport(instance, "t");
                final Object grow = wasm.readMember("table_grow");
                lib.execute(grow, table, 1);
                Assert.fail("Should have failed - export count exceeds the limit");
            } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException | ArityException e) {
                throw new RuntimeException(e);
            } catch (WasmJsApiException e) {
                Assert.assertEquals("Range error expected", WasmJsApiException.Kind.RangeError, e.kind());
            }
        });
    }

    @Test
    public void testCustomSections() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmModule module = wasm.moduleDecode(binaryWithCustomSections);
            try {
                checkCustomSections(new byte[][]{}, WebAssembly.customSections(module, ""));
                checkCustomSections(new byte[][]{}, WebAssembly.customSections(module, "zero"));
                checkCustomSections(new byte[][]{{1, 3, 5}}, WebAssembly.customSections(module, "odd"));
                checkCustomSections(new byte[][]{{2, 4}, {6}}, WebAssembly.customSections(module, "even"));
            } catch (InteropException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void checkCustomSections(byte[][] expected, Sequence<ByteArrayBuffer> actual) throws InvalidArrayIndexException, UnsupportedMessageException {
        InteropLibrary interop = InteropLibrary.getUncached(actual);
        Assert.assertEquals("Custom section count", expected.length, (int) interop.getArraySize(actual));
        for (int i = 0; i < expected.length; i++) {
            checkCustomSection(expected[i], (ByteArrayBuffer) interop.readArrayElement(actual, i));
        }
    }

    private static void checkCustomSection(byte[] expected, ByteArrayBuffer actual) throws InvalidArrayIndexException, UnsupportedMessageException {
        InteropLibrary interop = InteropLibrary.getUncached(actual);
        Assert.assertEquals("Custom section length", expected.length, (int) interop.getArraySize(actual));
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals("Custom section data", expected[i], interop.readArrayElement(actual, i));
        }
    }

    @Test
    public void testNameSection() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            // Should not throw an exception i.e. is a valid module
            // (despite the name section may not be formed correctly).
            wasm.moduleDecode(binaryWithEmptyNameSection);
            wasm.moduleDecode(binaryWithTruncatedNameSection);
            wasm.moduleDecode(binaryWithNameSectionWithInvalidIndex);
        });
    }

    private static void assertThrowsIBOE(Callable<?> callable) {
        try {
            callable.call();
            Assert.fail("InvalidBufferOffsetException expected");
        } catch (Exception ex) {
            if (!(ex instanceof InvalidBufferOffsetException)) {
                Assert.fail(ex.getMessage());
            }
        }
    }

    @Test
    public void testMemoryBufferMessages() throws IOException {
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            WasmModule module = wasm.moduleDecode(binaryWithMemoryExport);
            WasmInstance instance = wasm.moduleInstantiate(module, new Dictionary());
            try {
                Object buffer = WebAssembly.instanceExport(instance, "memory");

                long bufferSize = 4 * Sizes.MEMORY_PAGE_SIZE;
                InteropLibrary interop = InteropLibrary.getUncached(buffer);
                Assert.assertTrue("Should have buffer elements", interop.hasBufferElements(buffer));
                Assert.assertEquals("Should have correct buffer size", bufferSize, interop.getBufferSize(buffer));
                Assert.assertTrue("Should have writable buffer", interop.isBufferWritable(buffer));
                Assert.assertEquals("Read first byte", (byte) 0, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read last byte", (byte) 0, interop.readBufferByte(buffer, bufferSize - 1));
                Assert.assertEquals("Read first short LE", (short) 0, interop.readBufferShort(buffer, ByteOrder.LITTLE_ENDIAN, 0));
                Assert.assertEquals("Read first short BE", (short) 0, interop.readBufferShort(buffer, ByteOrder.BIG_ENDIAN, 0));
                Assert.assertEquals("Read last short LE", (short) 0, interop.readBufferShort(buffer, ByteOrder.LITTLE_ENDIAN, bufferSize - 2));
                Assert.assertEquals("Read last short BE", (short) 0, interop.readBufferShort(buffer, ByteOrder.BIG_ENDIAN, bufferSize - 2));
                Assert.assertEquals("Read first int LE", 0, interop.readBufferInt(buffer, ByteOrder.LITTLE_ENDIAN, 0));
                Assert.assertEquals("Read first int BE", 0, interop.readBufferInt(buffer, ByteOrder.BIG_ENDIAN, 0));
                Assert.assertEquals("Read last int LE", 0, interop.readBufferInt(buffer, ByteOrder.LITTLE_ENDIAN, bufferSize - 4));
                Assert.assertEquals("Read last int BE", 0, interop.readBufferInt(buffer, ByteOrder.BIG_ENDIAN, bufferSize - 4));
                Assert.assertEquals("Read first long LE", 0L, interop.readBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, 0));
                Assert.assertEquals("Read first long BE", 0L, interop.readBufferLong(buffer, ByteOrder.BIG_ENDIAN, 0));
                Assert.assertEquals("Read last long LE", 0L, interop.readBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, bufferSize - 8));
                Assert.assertEquals("Read last long BE", 0L, interop.readBufferLong(buffer, ByteOrder.BIG_ENDIAN, bufferSize - 8));
                Assert.assertEquals("Read first float LE", (float) 0, interop.readBufferFloat(buffer, ByteOrder.LITTLE_ENDIAN, 0));
                Assert.assertEquals("Read first float BE", (float) 0, interop.readBufferFloat(buffer, ByteOrder.BIG_ENDIAN, 0));
                Assert.assertEquals("Read last float LE", (float) 0, interop.readBufferFloat(buffer, ByteOrder.LITTLE_ENDIAN, bufferSize - 4));
                Assert.assertEquals("Read last float BE", (float) 0, interop.readBufferFloat(buffer, ByteOrder.BIG_ENDIAN, bufferSize - 4));
                Assert.assertEquals("Read first double LE", 0d, interop.readBufferDouble(buffer, ByteOrder.LITTLE_ENDIAN, 0));
                Assert.assertEquals("Read first double BE", 0d, interop.readBufferDouble(buffer, ByteOrder.BIG_ENDIAN, 0));
                Assert.assertEquals("Read last double LE", 0d, interop.readBufferDouble(buffer, ByteOrder.LITTLE_ENDIAN, bufferSize - 8));
                Assert.assertEquals("Read last double BE", 0d, interop.readBufferDouble(buffer, ByteOrder.BIG_ENDIAN, bufferSize - 8));

                interop.writeBufferByte(buffer, 0, (byte) 1);
                Assert.assertEquals("Read written byte", (byte) 1, interop.readBufferByte(buffer, 0));

                interop.writeBufferShort(buffer, ByteOrder.LITTLE_ENDIAN, 0, (short) 0x0102);
                Assert.assertEquals("Read written short LE", (short) 0x0102, interop.readBufferShort(buffer, ByteOrder.LITTLE_ENDIAN, 0));
                Assert.assertEquals("Read byte 0 of short LE", (byte) 0x02, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read byte 1 of short LE", (byte) 0x01, interop.readBufferByte(buffer, 1));

                interop.writeBufferShort(buffer, ByteOrder.BIG_ENDIAN, 0, (short) 0x0102);
                Assert.assertEquals("Read written short BE", (short) 0x0102, interop.readBufferShort(buffer, ByteOrder.BIG_ENDIAN, 0));
                Assert.assertEquals("Read byte 0 of short BE", (byte) 0x01, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read byte 1 of short BE", (byte) 0x02, interop.readBufferByte(buffer, 1));

                interop.writeBufferInt(buffer, ByteOrder.LITTLE_ENDIAN, 0, 0x01020304);
                Assert.assertEquals("Read written int LE", 0x01020304, interop.readBufferInt(buffer, ByteOrder.LITTLE_ENDIAN, 0));
                Assert.assertEquals("Read byte 0 of int LE", (byte) 0x04, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read byte 1 of int LE", (byte) 0x03, interop.readBufferByte(buffer, 1));
                Assert.assertEquals("Read byte 2 of int LE", (byte) 0x02, interop.readBufferByte(buffer, 2));
                Assert.assertEquals("Read byte 3 of int LE", (byte) 0x01, interop.readBufferByte(buffer, 3));

                interop.writeBufferInt(buffer, ByteOrder.BIG_ENDIAN, 0, 0x01020304);
                Assert.assertEquals("Read written int BE", 0x01020304, interop.readBufferInt(buffer, ByteOrder.BIG_ENDIAN, 0));
                Assert.assertEquals("Read byte 0 of int BE", (byte) 0x01, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read byte 1 of int BE", (byte) 0x02, interop.readBufferByte(buffer, 1));
                Assert.assertEquals("Read byte 2 of int BE", (byte) 0x03, interop.readBufferByte(buffer, 2));
                Assert.assertEquals("Read byte 3 of int BE", (byte) 0x04, interop.readBufferByte(buffer, 3));

                interop.writeBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, 0, 0x0102030405060708L);
                Assert.assertEquals("Read written long LE", 0x0102030405060708L, interop.readBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, 0));
                Assert.assertEquals("Read byte 0 of long LE", (byte) 0x08, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read byte 1 of long LE", (byte) 0x07, interop.readBufferByte(buffer, 1));
                Assert.assertEquals("Read byte 2 of long LE", (byte) 0x06, interop.readBufferByte(buffer, 2));
                Assert.assertEquals("Read byte 3 of long LE", (byte) 0x05, interop.readBufferByte(buffer, 3));
                Assert.assertEquals("Read byte 4 of long LE", (byte) 0x04, interop.readBufferByte(buffer, 4));
                Assert.assertEquals("Read byte 5 of long LE", (byte) 0x03, interop.readBufferByte(buffer, 5));
                Assert.assertEquals("Read byte 6 of long LE", (byte) 0x02, interop.readBufferByte(buffer, 6));
                Assert.assertEquals("Read byte 7 of long LE", (byte) 0x01, interop.readBufferByte(buffer, 7));

                interop.writeBufferLong(buffer, ByteOrder.BIG_ENDIAN, 0, 0x0102030405060708L);
                Assert.assertEquals("Read written long BE", 0x0102030405060708L, interop.readBufferLong(buffer, ByteOrder.BIG_ENDIAN, 0));
                Assert.assertEquals("Read byte 0 of long BE", (byte) 0x01, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read byte 1 of long BE", (byte) 0x02, interop.readBufferByte(buffer, 1));
                Assert.assertEquals("Read byte 2 of long BE", (byte) 0x03, interop.readBufferByte(buffer, 2));
                Assert.assertEquals("Read byte 3 of long BE", (byte) 0x04, interop.readBufferByte(buffer, 3));
                Assert.assertEquals("Read byte 4 of long BE", (byte) 0x05, interop.readBufferByte(buffer, 4));
                Assert.assertEquals("Read byte 5 of long BE", (byte) 0x06, interop.readBufferByte(buffer, 5));
                Assert.assertEquals("Read byte 6 of long BE", (byte) 0x07, interop.readBufferByte(buffer, 6));
                Assert.assertEquals("Read byte 7 of long BE", (byte) 0x08, interop.readBufferByte(buffer, 7));

                float f = Float.intBitsToFloat(0x01020304);
                interop.writeBufferFloat(buffer, ByteOrder.LITTLE_ENDIAN, 0, f);
                Assert.assertEquals("Read written float LE", f, interop.readBufferFloat(buffer, ByteOrder.LITTLE_ENDIAN, 0));
                Assert.assertEquals("Read byte 0 of float LE", (byte) 0x04, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read byte 1 of float LE", (byte) 0x03, interop.readBufferByte(buffer, 1));
                Assert.assertEquals("Read byte 2 of float LE", (byte) 0x02, interop.readBufferByte(buffer, 2));
                Assert.assertEquals("Read byte 3 of float LE", (byte) 0x01, interop.readBufferByte(buffer, 3));

                interop.writeBufferFloat(buffer, ByteOrder.BIG_ENDIAN, 0, f);
                Assert.assertEquals("Read written float BE", f, interop.readBufferFloat(buffer, ByteOrder.BIG_ENDIAN, 0));
                Assert.assertEquals("Read byte 0 of float BE", (byte) 0x01, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read byte 1 of float BE", (byte) 0x02, interop.readBufferByte(buffer, 1));
                Assert.assertEquals("Read byte 2 of float BE", (byte) 0x03, interop.readBufferByte(buffer, 2));
                Assert.assertEquals("Read byte 3 of float BE", (byte) 0x04, interop.readBufferByte(buffer, 3));

                double d = Double.longBitsToDouble(0x0102030405060708L);
                interop.writeBufferDouble(buffer, ByteOrder.LITTLE_ENDIAN, 0, d);
                Assert.assertEquals("Read written double LE", d, interop.readBufferDouble(buffer, ByteOrder.LITTLE_ENDIAN, 0));
                Assert.assertEquals("Read byte 0 of double LE", (byte) 0x08, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read byte 1 of double LE", (byte) 0x07, interop.readBufferByte(buffer, 1));
                Assert.assertEquals("Read byte 2 of double LE", (byte) 0x06, interop.readBufferByte(buffer, 2));
                Assert.assertEquals("Read byte 3 of double LE", (byte) 0x05, interop.readBufferByte(buffer, 3));
                Assert.assertEquals("Read byte 4 of double LE", (byte) 0x04, interop.readBufferByte(buffer, 4));
                Assert.assertEquals("Read byte 5 of double LE", (byte) 0x03, interop.readBufferByte(buffer, 5));
                Assert.assertEquals("Read byte 6 of double LE", (byte) 0x02, interop.readBufferByte(buffer, 6));
                Assert.assertEquals("Read byte 7 of double LE", (byte) 0x01, interop.readBufferByte(buffer, 7));

                interop.writeBufferDouble(buffer, ByteOrder.BIG_ENDIAN, 0, d);
                Assert.assertEquals("Read written double BE", d, interop.readBufferDouble(buffer, ByteOrder.BIG_ENDIAN, 0));
                Assert.assertEquals("Read byte 0 of double BE", (byte) 0x01, interop.readBufferByte(buffer, 0));
                Assert.assertEquals("Read byte 1 of double BE", (byte) 0x02, interop.readBufferByte(buffer, 1));
                Assert.assertEquals("Read byte 2 of double BE", (byte) 0x03, interop.readBufferByte(buffer, 2));
                Assert.assertEquals("Read byte 3 of double BE", (byte) 0x04, interop.readBufferByte(buffer, 3));
                Assert.assertEquals("Read byte 4 of double BE", (byte) 0x05, interop.readBufferByte(buffer, 4));
                Assert.assertEquals("Read byte 5 of double BE", (byte) 0x06, interop.readBufferByte(buffer, 5));
                Assert.assertEquals("Read byte 6 of double BE", (byte) 0x07, interop.readBufferByte(buffer, 6));
                Assert.assertEquals("Read byte 7 of double BE", (byte) 0x08, interop.readBufferByte(buffer, 7));

                // Offset too small
                assertThrowsIBOE(() -> interop.readBufferByte(buffer, -1));
                assertThrowsIBOE(() -> interop.readBufferShort(buffer, ByteOrder.LITTLE_ENDIAN, -1));
                assertThrowsIBOE(() -> interop.readBufferShort(buffer, ByteOrder.BIG_ENDIAN, -1));
                assertThrowsIBOE(() -> interop.readBufferInt(buffer, ByteOrder.LITTLE_ENDIAN, -1));
                assertThrowsIBOE(() -> interop.readBufferInt(buffer, ByteOrder.BIG_ENDIAN, -1));
                assertThrowsIBOE(() -> interop.readBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, -1));
                assertThrowsIBOE(() -> interop.readBufferLong(buffer, ByteOrder.BIG_ENDIAN, -1));
                assertThrowsIBOE(() -> interop.readBufferFloat(buffer, ByteOrder.LITTLE_ENDIAN, -1));
                assertThrowsIBOE(() -> interop.readBufferFloat(buffer, ByteOrder.BIG_ENDIAN, -1));
                assertThrowsIBOE(() -> interop.readBufferDouble(buffer, ByteOrder.LITTLE_ENDIAN, -1));
                assertThrowsIBOE(() -> interop.readBufferDouble(buffer, ByteOrder.BIG_ENDIAN, -1));

                // Offset too large
                assertThrowsIBOE(() -> interop.readBufferByte(buffer, bufferSize));
                assertThrowsIBOE(() -> interop.readBufferShort(buffer, ByteOrder.LITTLE_ENDIAN, bufferSize - 1));
                assertThrowsIBOE(() -> interop.readBufferShort(buffer, ByteOrder.BIG_ENDIAN, bufferSize - 1));
                assertThrowsIBOE(() -> interop.readBufferInt(buffer, ByteOrder.LITTLE_ENDIAN, bufferSize - 3));
                assertThrowsIBOE(() -> interop.readBufferInt(buffer, ByteOrder.BIG_ENDIAN, bufferSize - 3));
                assertThrowsIBOE(() -> interop.readBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, bufferSize - 7));
                assertThrowsIBOE(() -> interop.readBufferLong(buffer, ByteOrder.BIG_ENDIAN, bufferSize - 7));
                assertThrowsIBOE(() -> interop.readBufferFloat(buffer, ByteOrder.LITTLE_ENDIAN, bufferSize - 3));
                assertThrowsIBOE(() -> interop.readBufferFloat(buffer, ByteOrder.BIG_ENDIAN, bufferSize - 3));
                assertThrowsIBOE(() -> interop.readBufferDouble(buffer, ByteOrder.LITTLE_ENDIAN, bufferSize - 7));
                assertThrowsIBOE(() -> interop.readBufferDouble(buffer, ByteOrder.BIG_ENDIAN, bufferSize - 7));
            } catch (InteropException ex) {
                Assert.fail(ex.getMessage());
            }
        });
    }

    @Test
    public void testTableImport() throws IOException, InterruptedException {
        // Exports table with a function
        final byte[] exportTable = compileWat("exportTable", "(module" +
                        "(func $f0 (result i32) i32.const 42)" +
                        "(table 1 1 funcref)" +
                        "(export \"table\" (table 0))" +
                        "(elem (i32.const 0) $f0)" +
                        ")");

        // Imports table and exports function that invokes functions from the table
        final byte[] importTable = compileWat("importTable", "(module" +
                        "(type (func (param i32) (result i32)))" +
                        "(type (func (result i32)))" +
                        "(import \"tableImport\" \"table\" (table 1 1 funcref))" +
                        "(func (type 0) (param i32) (result i32) local.get 0 call_indirect (type 1))" +
                        "(export \"testFunc\" (func 0))" +
                        ")");

        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            WasmInstance exportInstance = moduleInstantiate(wasm, exportTable, null);
            try {
                Object exportedTable = WebAssembly.instanceExport(exportInstance, "table");

                Dictionary importObject = new Dictionary();
                Dictionary tableImport = new Dictionary();
                tableImport.addMember("table", exportedTable);
                importObject.addMember("tableImport", tableImport);

                WasmInstance importInstance = moduleInstantiate(wasm, importTable, importObject);

                Object testFunc = WebAssembly.instanceExport(importInstance, "testFunc");
                Object result = InteropLibrary.getUncached().execute(testFunc, 0);

                Assert.assertEquals("Return value should be 42", 42, result);
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testMemoryAllocationFailure() throws IOException {
        // Memory allocation should either succeed or throw an interop
        // exception (not an internal error like OutOfMemoryError).
        runTest(context -> {
            try {
                Object[] memories = new Object[5];
                for (int i = 0; i < memories.length; i++) {
                    memories[i] = WebAssembly.memAlloc(32767, 32767);
                }
            } catch (AbstractTruffleException ex) {
                Assert.assertTrue("Should throw interop exception", InteropLibrary.getUncached(ex).isException(ex));
            }
        });
    }

    @Test
    public void testFuncTypeTable() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithTableExport, null);
            try {
                final Object funcType = wasm.readMember("func_type");
                final WasmTable table = (WasmTable) WebAssembly.instanceExport(instance, "defaultTable");
                final Object fn = WebAssembly.tableRead(table, 0);
                InteropLibrary interop = InteropLibrary.getUncached(funcType);
                Assert.assertEquals("func_type", "0(i32)i32", interop.execute(funcType, fn));
                // set + get should not break func_type()
                WebAssembly.tableWrite(table, 0, fn);
                final Object fnAgain = WebAssembly.tableRead(table, 0);
                Assert.assertEquals("func_type", "0(i32)i32", interop.execute(funcType, fnAgain));
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testFuncTypeExport() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, binaryWithMemoryExport, null);
            try {
                final Object funcType = wasm.readMember("func_type");
                final Object fn = WebAssembly.instanceExport(instance, "readZero");
                Assert.assertEquals("func_type", "0()i32", InteropLibrary.getUncached(funcType).execute(funcType, fn));
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testFuncTypeMultipleParameters() throws IOException, InterruptedException {
        final byte[] source = compileWat("data", "(module (func (export \"func\") (param i32 i64) (result f32) f32.const 0))");
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WasmInstance instance = moduleInstantiate(wasm, source, null);
            final WasmFunctionInstance fn = (WasmFunctionInstance) WebAssembly.instanceExport(instance, "func");
            final String fnType = WebAssembly.functionTypeToString(fn.function());
            Assert.assertEquals("func_type", "0(i32 i64)f32", fnType);
        });
    }

    @Test
    public void testInitialMemorySizeOutOfBounds() throws IOException {
        runTest(context -> {
            try {
                WebAssembly.memAlloc(32768, 32770);
                Assert.fail("Should have failed - initial memory size exceeds implementation limit");
            } catch (WasmJsApiException e) {
                Assert.assertEquals("Range error expected", WasmJsApiException.Kind.RangeError, e.kind());
            }
        });
    }

    @Test
    public void testMinMemorySizeExceedsMaxSize() throws IOException {
        runTest(context -> {
            try {
                WebAssembly.memAlloc(2, 1);
                Assert.fail("Should have failed - min memory size bigger than max size");
            } catch (WasmJsApiException e) {
                Assert.assertEquals("Range error expected", WasmJsApiException.Kind.RangeError, e.kind());
            }
        });
    }

    @Test
    public void testMemoryGrowLimit() throws IOException {
        runTest(context -> {
            try {
                WasmMemory memory = WebAssembly.memAlloc(1, 1);
                WebAssembly.memGrow(memory, 1);
                Assert.fail("Should have failed - try to grow memory beyond max size");
            } catch (WasmJsApiException e) {
                Assert.assertEquals("Range error expected", WasmJsApiException.Kind.RangeError, e.kind());
            }
        });
    }

    @Test
    public void testInitialTableSizeOutOfBounds() throws IOException {
        runTest(context -> {
            try {
                // Negative numbers represent unsigned values
                WebAssembly.tableAlloc(-10, -8);
                Assert.fail("Should have failed - initial table size exceeds implementation limit");
            } catch (WasmJsApiException e) {
                Assert.assertEquals("Range error expected", WasmJsApiException.Kind.RangeError, e.kind());
            }
        });
    }

    @Test
    public void testMinTableSizeExceedsMaxSize() throws IOException {
        runTest(context -> {
            try {
                WebAssembly.tableAlloc(2, 1);
                Assert.fail("Should have failed - min table size bigger than max size");
            } catch (WasmJsApiException e) {
                Assert.assertEquals("Range error expected", WasmJsApiException.Kind.RangeError, e.kind());
            }
        });
    }

    @Test
    public void testTableGrowLimit() throws IOException {
        runTest(context -> {
            try {
                WasmTable table = WebAssembly.tableAlloc(1, 1);
                WebAssembly.tableGrow(table, 1);
                Assert.fail("Should have failed - try to grow table beyond max size");
            } catch (WasmJsApiException e) {
                Assert.assertEquals("Range error expected", WasmJsApiException.Kind.RangeError, e.kind());
            }
        });
    }

    private static void checkEmbedderData(Object wasmEntity) {
        Object defaultEmbedderData = WebAssembly.embedderDataGet(new Object[]{wasmEntity});
        Assert.assertEquals("Unexpected default embedder data", WasmVoidResult.getInstance(), defaultEmbedderData);

        Object newEmbedderData = new Object();
        WebAssembly.embedderDataSet(new Object[]{wasmEntity, newEmbedderData});
        Object embedderData = WebAssembly.embedderDataGet(new Object[]{wasmEntity});
        Assert.assertEquals("Unexpected embedder data", newEmbedderData, embedderData);
    }

    @Test
    public void testFunctionEmbedderData() throws IOException {
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            WasmInstance instance = moduleInstantiate(wasm, binaryWithExports, null);
            Object fn = WebAssembly.instanceExport(instance, "main");
            checkEmbedderData(fn);
        });
    }

    @Test
    public void testGlobalEmbedderData() throws IOException {
        runTest(context -> {
            WasmGlobal global = WebAssembly.globalAlloc(ValueType.i32, false, 0);
            checkEmbedderData(global);
        });
    }

    @Test
    public void testMemoryEmbedderData() throws IOException {
        runTest(context -> {
            WasmMemory memory = WebAssembly.memAlloc(1, 1);
            checkEmbedderData(memory);
        });
    }

    @Test
    public void testTableEmbedderData() throws IOException {
        runTest(context -> {
            WasmTable table = WebAssembly.tableAlloc(1, 1);
            checkEmbedderData(table);
        });
    }

    @Test
    public void testInvalidEmbedderData() throws IOException {
        runTest(context -> {
            Object notEmbedderDataHolder = new Object();

            try {
                WebAssembly.embedderDataGet(new Object[]{notEmbedderDataHolder});
                Assert.fail("embedderDataGet failed to throw");
            } catch (WasmJsApiException ex) {
                Assert.assertEquals("Type error expected", WasmJsApiException.Kind.TypeError, ex.kind());
            }

            try {
                WebAssembly.embedderDataSet(new Object[]{notEmbedderDataHolder, 42});
                Assert.fail("embedderDataSet failed to throw");
            } catch (WasmJsApiException ex) {
                Assert.assertEquals("Type error expected", WasmJsApiException.Kind.TypeError, ex.kind());
            }
        });
    }

    @Test
    public void testValidateUnsupportedInstruction() throws IOException {
        // (module
        // (func
        // i32.const 0
        // 0x06 (* reserved instruction value *)
        // drop
        // )
        // )
        byte[] data = new byte[]{
                        (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6D, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                        (byte) 0x00, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x0A, (byte) 0x07, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x41, (byte) 0x00, (byte) 0x06,
                        (byte) 0x1A, (byte) 0x0B,
        };
        runValidationInvalid(data);
    }

    @Test
    public void testValidateMalformedInstruction() throws IOException {
        // (module
        // (func
        // i32.const (* missing int constant *)
        // )
        // )
        byte[] data = new byte[]{
                        (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6D, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                        (byte) 0x00, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x0A, (byte) 0x06, (byte) 0x01, (byte) 0x04, (byte) 0x00, (byte) 0x41, (byte) 0x0B,
        };
        runValidationInvalid(data);
    }

    @Test
    public void testSetMissingLocal() throws IOException {
        // (module
        // (func
        // i32.const 0
        // local.set 0
        // )
        // )
        byte[] data = new byte[]{
                        (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6D, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                        (byte) 0x00, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x0A, (byte) 0x08, (byte) 0x01, (byte) 0x06, (byte) 0x00, (byte) 0x41, (byte) 0x00, (byte) 0x21,
                        (byte) 0x00, (byte) 0x0B,
        };
        runValidationInvalid(data);
    }

    @Test
    public void testMissingStackValue() throws IOException {
        // (module
        // (func
        // i32.const 0
        // i32.eq
        // )
        // )
        byte[] data = new byte[]{
                        (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6D, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                        (byte) 0x00, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x0A, (byte) 0x07, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x41, (byte) 0x00, (byte) 0x46,
                        (byte) 0x0B,
        };
        runValidationInvalid(data);
    }

    @Test
    public void testBranchToMissingLabel() throws IOException {
        // (module
        // (func
        // br 1
        // )
        // )
        byte[] data = new byte[]{
                        (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6D, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                        (byte) 0x00, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x0A, (byte) 0x06, (byte) 0x01, (byte) 0x04, (byte) 0x00, (byte) 0x0C, (byte) 0x01, (byte) 0x0B,
        };
        runValidationInvalid(data);
    }

    @Test
    public void testValidFunctionBody() throws IOException {
        // (module
        // (func (export "exp") (param i32 i32) (result i32)
        // (local i32)
        // local.get 1
        // i32.const 0
        // i32.eq
        // if
        // i32.const 1
        // local.set 2
        // else
        // local.get 0
        // local.set 2
        // loop
        // local.get 2
        // local.get 0
        // i32.mul
        // local.set 2
        //
        // local.get 1
        // i32.const 1
        // i32.sub
        // local.set 1
        //
        // local.get 1
        // i32.const 0
        // i32.ne
        // br_if 0
        // end
        // end
        // local.get 2
        // return
        // )
        // )
        byte[] data = new byte[]{
                        (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6D, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x07, (byte) 0x01, (byte) 0x60, (byte) 0x02,
                        (byte) 0x7F, (byte) 0x7F, (byte) 0x01, (byte) 0x7F, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x07, (byte) 0x07, (byte) 0x01, (byte) 0x03, (byte) 0x65,
                        (byte) 0x78, (byte) 0x70, (byte) 0x00, (byte) 0x00, (byte) 0x0A, (byte) 0x32, (byte) 0x01, (byte) 0x30, (byte) 0x01, (byte) 0x01, (byte) 0x7F, (byte) 0x20, (byte) 0x01,
                        (byte) 0x41, (byte) 0x00, (byte) 0x46, (byte) 0x04, (byte) 0x40, (byte) 0x41, (byte) 0x01, (byte) 0x21, (byte) 0x02, (byte) 0x05, (byte) 0x20, (byte) 0x00, (byte) 0x21,
                        (byte) 0x02, (byte) 0x03, (byte) 0x40, (byte) 0x20, (byte) 0x02, (byte) 0x20, (byte) 0x00, (byte) 0x6C, (byte) 0x21, (byte) 0x02, (byte) 0x20, (byte) 0x01, (byte) 0x41,
                        (byte) 0x01, (byte) 0x6B, (byte) 0x21, (byte) 0x01, (byte) 0x20, (byte) 0x01, (byte) 0x41, (byte) 0x00, (byte) 0x47, (byte) 0x0D, (byte) 0x00, (byte) 0x0B, (byte) 0x0B,
                        (byte) 0x20, (byte) 0x02, (byte) 0x0F, (byte) 0x0B,
        };
        runValidationValid(data);
    }

    private static void runValidationInvalid(byte[] data) throws IOException {
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            Assert.assertTrue("Should have failed - invalid module", !wasm.moduleValidate(data));
        });
    }

    private static void runValidationValid(byte[] data) throws IOException {
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            Assert.assertTrue("Should have failed - valid module", wasm.moduleValidate(data));
        });
    }

    @Test
    public void testImportManyGlobals() throws IOException, InterruptedException {
        String importManyGlobalsWat = "(module\n" +
                        "(global $global0 (import \"globals\" \"global0\") i32)\n" +
                        "(global $global1 (import \"globals\" \"global1\") i32)\n" +
                        "(global $global2 (import \"globals\" \"global2\") i32)\n" +
                        "(global $global3 (import \"globals\" \"global3\") i32)\n" +
                        "(global $global4 (import \"globals\" \"global4\") i32)\n" +
                        "(global $global5 (import \"globals\" \"global5\") i32)\n" +
                        "(global $global6 (import \"globals\" \"global6\") i32)\n" +
                        "(global $global7 (import \"globals\" \"global7\") i32)\n" +
                        "(global $global8 (import \"globals\" \"global8\") i32)\n" +
                        "(func (export \"sum\") (result i32)\n" +
                        "    get_global $global0\n" +
                        "    get_global $global1\n" +
                        "    i32.add\n" +
                        "    get_global $global2\n" +
                        "    i32.add\n" +
                        "    get_global $global3\n" +
                        "    i32.add\n" +
                        "    get_global $global4\n" +
                        "    i32.add\n" +
                        "    get_global $global5\n" +
                        "    i32.add\n" +
                        "    get_global $global6\n" +
                        "    i32.add\n" +
                        "    get_global $global7\n" +
                        "    i32.add\n" +
                        "    get_global $global8\n" +
                        "    i32.add\n" +
                        ")\n" +
                        ")";
        byte[] importManyGlobalsBytes = compileWat("importManyGlobals", importManyGlobalsWat);
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            Dictionary importObject = Dictionary.create(new Object[]{
                            "globals", Dictionary.create(new Object[]{
                                            "global0", WebAssembly.globalAlloc(ValueType.i32, false, 1),
                                            "global1", WebAssembly.globalAlloc(ValueType.i32, false, 2),
                                            "global2", WebAssembly.globalAlloc(ValueType.i32, false, 3),
                                            "global3", WebAssembly.globalAlloc(ValueType.i32, false, 4),
                                            "global4", WebAssembly.globalAlloc(ValueType.i32, false, 5),
                                            "global5", WebAssembly.globalAlloc(ValueType.i32, false, 6),
                                            "global6", WebAssembly.globalAlloc(ValueType.i32, false, 7),
                                            "global7", WebAssembly.globalAlloc(ValueType.i32, false, 8),
                                            "global8", WebAssembly.globalAlloc(ValueType.i32, false, 9),
                            }),
            });
            WasmInstance instance = moduleInstantiate(wasm, importManyGlobalsBytes, importObject);
            try {
                InteropLibrary lib = InteropLibrary.getUncached();
                Object sum = lib.execute(WebAssembly.instanceExport(instance, "sum"));
                int intSum = lib.asInt(sum);
                Assert.assertEquals("Incorrect sum of imported globals", 45, intSum);
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });

    }

    private static void runTest(Consumer<WasmContext> testCase) throws IOException {
        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.option("wasm.Builtins", "testutil:testutil");
        final Context context = contextBuilder.build();
        Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binaryWithExports), "main");
        Source source = sourceBuilder.build();
        context.eval(source);
        Value main = context.getBindings(WasmLanguage.ID).getMember("main").getMember("main");
        main.execute();
        Value run = context.getBindings(WasmLanguage.ID).getMember("testutil").getMember(TestutilModule.Names.RUN_CUSTOM_INITIALIZATION);
        run.execute(new GuestCode(testCase));
    }

    private static final class GuestCode implements Consumer<WasmContext>, TruffleObject {
        private final Consumer<WasmContext> testCase;

        private GuestCode(Consumer<WasmContext> testCase) {
            this.testCase = testCase;
        }

        @Override
        public void accept(WasmContext context) {
            testCase.accept(context);
        }
    }

    // (module
    // (type (;0;) (func))
    // (type (;1;) (func (result i32)))
    // (func (;0;) (type 0))
    // (func (;1;) (type 1) (result i32)
    // i32.const 42
    // )
    // (table (;0;) 1 1 funcref)
    // (memory (;0;) 0)
    // (global (;0;) (mut i32) (i32.const 66560))
    // (global (;1;) i32 (i32.const 66560))
    // (global (;2;) i32 (i32.const 1024))
    // (export "main" (func 1))
    // (export "memory" (memory 0))
    // (export "__heap_base" (global 1))
    // (export "__data_end" (global 2))
    // )
    private static final byte[] binaryWithExports = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73,
                    (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x08, (byte) 0x02, (byte) 0x60, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x00,
                    (byte) 0x01, (byte) 0x7f, (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x05, (byte) 0x01, (byte) 0x70, (byte) 0x01, (byte) 0x01,
                    (byte) 0x01, (byte) 0x05, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x15, (byte) 0x03, (byte) 0x7f, (byte) 0x01, (byte) 0x41, (byte) 0x80,
                    (byte) 0x88, (byte) 0x04, (byte) 0x0b, (byte) 0x7f, (byte) 0x00, (byte) 0x41, (byte) 0x80, (byte) 0x88, (byte) 0x04, (byte) 0x0b, (byte) 0x7f, (byte) 0x00, (byte) 0x41,
                    (byte) 0x80, (byte) 0x08, (byte) 0x0b, (byte) 0x07, (byte) 0x2c, (byte) 0x04, (byte) 0x04, (byte) 0x6d, (byte) 0x61, (byte) 0x69, (byte) 0x6e, (byte) 0x00, (byte) 0x01,
                    (byte) 0x06, (byte) 0x6d, (byte) 0x65, (byte) 0x6d, (byte) 0x6f, (byte) 0x72, (byte) 0x79, (byte) 0x02, (byte) 0x00, (byte) 0x0b, (byte) 0x5f, (byte) 0x5f, (byte) 0x68,
                    (byte) 0x65, (byte) 0x61, (byte) 0x70, (byte) 0x5f, (byte) 0x62, (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x03, (byte) 0x01, (byte) 0x0a, (byte) 0x5f, (byte) 0x5f,
                    (byte) 0x64, (byte) 0x61, (byte) 0x74, (byte) 0x61, (byte) 0x5f, (byte) 0x65, (byte) 0x6e, (byte) 0x64, (byte) 0x03, (byte) 0x02, (byte) 0x0a, (byte) 0x09, (byte) 0x02,
                    (byte) 0x02, (byte) 0x00, (byte) 0x0b, (byte) 0x04, (byte) 0x00, (byte) 0x41, (byte) 0x2a, (byte) 0x0b
    };

    // (module
    // (func $inc (import "host" "inc") (param i32) (result i32))
    // (func $addPlusOne (param $lhs i32) (param $rhs i32) (result i32)
    // get_local $lhs
    // get_local $rhs
    // i32.add
    // call $inc
    // )
    // (export "addPlusOne" (func $addPlusOne))
    // )
    private static final byte[] binaryWithImportsAndExports = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6D, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x0C, (byte) 0x02, (byte) 0x60, (byte) 0x01,
                    (byte) 0x7F, (byte) 0x01, (byte) 0x7F, (byte) 0x60, (byte) 0x02, (byte) 0x7F, (byte) 0x7F, (byte) 0x01, (byte) 0x7F, (byte) 0x02, (byte) 0x0C, (byte) 0x01, (byte) 0x04,
                    (byte) 0x68, (byte) 0x6F, (byte) 0x73, (byte) 0x74, (byte) 0x03, (byte) 0x69, (byte) 0x6E, (byte) 0x63, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x02, (byte) 0x01,
                    (byte) 0x01, (byte) 0x07, (byte) 0x0E, (byte) 0x01, (byte) 0x0A, (byte) 0x61, (byte) 0x64, (byte) 0x64, (byte) 0x50, (byte) 0x6C, (byte) 0x75, (byte) 0x73, (byte) 0x4F,
                    (byte) 0x6E, (byte) 0x65, (byte) 0x00, (byte) 0x01, (byte) 0x0A, (byte) 0x0B, (byte) 0x01, (byte) 0x09, (byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x20, (byte) 0x01,
                    (byte) 0x6A, (byte) 0x10, (byte) 0x00, (byte) 0x0B, (byte) 0x00, (byte) 0x2C, (byte) 0x04, (byte) 0x6E, (byte) 0x61, (byte) 0x6D, (byte) 0x65, (byte) 0x01, (byte) 0x12,
                    (byte) 0x02, (byte) 0x00, (byte) 0x03, (byte) 0x69, (byte) 0x6E, (byte) 0x63, (byte) 0x01, (byte) 0x0A, (byte) 0x61, (byte) 0x64, (byte) 0x64, (byte) 0x50, (byte) 0x6C,
                    (byte) 0x75, (byte) 0x73, (byte) 0x4F, (byte) 0x6E, (byte) 0x65, (byte) 0x02, (byte) 0x11, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                    (byte) 0x02, (byte) 0x00, (byte) 0x03, (byte) 0x6C, (byte) 0x68, (byte) 0x73, (byte) 0x01, (byte) 0x03, (byte) 0x72, (byte) 0x68, (byte) 0x73
    };

    // (module
    // (import "host" "defaultMemory" (memory (;0;) 4))
    // (func $initZero
    // i32.const 0
    // i32.const 174
    // i32.store
    // )
    // (export "initZero" (func $initZero))
    // )
    private static final byte[] binaryWithMemoryImport = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x00, (byte) 0x02, (byte) 0x17, (byte) 0x01, (byte) 0x04, (byte) 0x68, (byte) 0x6f, (byte) 0x73, (byte) 0x74, (byte) 0x0d, (byte) 0x64, (byte) 0x65, (byte) 0x66,
                    (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x74, (byte) 0x4d, (byte) 0x65, (byte) 0x6d, (byte) 0x6f, (byte) 0x72, (byte) 0x79, (byte) 0x02, (byte) 0x00, (byte) 0x04,
                    (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x07, (byte) 0x0c, (byte) 0x01, (byte) 0x08, (byte) 0x69, (byte) 0x6e, (byte) 0x69, (byte) 0x74, (byte) 0x5a,
                    (byte) 0x65, (byte) 0x72, (byte) 0x6f, (byte) 0x00, (byte) 0x00, (byte) 0x0a, (byte) 0x0c, (byte) 0x01, (byte) 0x0a, (byte) 0x00, (byte) 0x41, (byte) 0x00, (byte) 0x41,
                    (byte) 0xae, (byte) 0x01, (byte) 0x36, (byte) 0x02, (byte) 0x00, (byte) 0x0b, (byte) 0x00, (byte) 0x17, (byte) 0x04, (byte) 0x6e, (byte) 0x61, (byte) 0x6d, (byte) 0x65,
                    (byte) 0x01, (byte) 0x0b, (byte) 0x01, (byte) 0x00, (byte) 0x08, (byte) 0x69, (byte) 0x6e, (byte) 0x69, (byte) 0x74, (byte) 0x5a, (byte) 0x65, (byte) 0x72, (byte) 0x6f,
                    (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x00,
    };

    // (module
    // (type $t0 (func (result i32)))
    // (func $readZero (export "readZero") (type $t0) (result i32)
    // i32.const 0
    // i32.load
    // )
    // (memory $memory (export "memory") 4)
    // )
    private static final byte[] binaryWithMemoryExport = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x01, (byte) 0x7f, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x05, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x07, (byte) 0x15,
                    (byte) 0x02, (byte) 0x06, (byte) 0x6d, (byte) 0x65, (byte) 0x6d, (byte) 0x6f, (byte) 0x72, (byte) 0x79, (byte) 0x02, (byte) 0x00, (byte) 0x08, (byte) 0x72, (byte) 0x65,
                    (byte) 0x61, (byte) 0x64, (byte) 0x5a, (byte) 0x65, (byte) 0x72, (byte) 0x6f, (byte) 0x00, (byte) 0x00, (byte) 0x0a, (byte) 0x09, (byte) 0x01, (byte) 0x07, (byte) 0x00,
                    (byte) 0x41, (byte) 0x00, (byte) 0x28, (byte) 0x02, (byte) 0x00, (byte) 0x0b, (byte) 0x00, (byte) 0x17, (byte) 0x04, (byte) 0x6e, (byte) 0x61, (byte) 0x6d, (byte) 0x65,
                    (byte) 0x01, (byte) 0x0b, (byte) 0x01, (byte) 0x00, (byte) 0x08, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64, (byte) 0x5a, (byte) 0x65, (byte) 0x72, (byte) 0x6f,
                    (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x00,
    };

    // (module
    // (type (;0;) $return_i32 (func (result i32)))
    // (import "host" "defaultTable" (table (;0;) 4 anyfunc))
    // (func $callFirst (result i32)
    // i32.const 0
    // call_indirect (type $return_i32)
    // )
    // (export "callFirst" (func $callFirst))
    // )
    private static final byte[] binaryWithTableImport = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x01, (byte) 0x7f, (byte) 0x02, (byte) 0x17, (byte) 0x01, (byte) 0x04, (byte) 0x68, (byte) 0x6f, (byte) 0x73, (byte) 0x74, (byte) 0x0c, (byte) 0x64, (byte) 0x65,
                    (byte) 0x66, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x74, (byte) 0x54, (byte) 0x61, (byte) 0x62, (byte) 0x6c, (byte) 0x65, (byte) 0x01, (byte) 0x70, (byte) 0x00,
                    (byte) 0x04, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x07, (byte) 0x0d, (byte) 0x01, (byte) 0x09, (byte) 0x63, (byte) 0x61, (byte) 0x6c, (byte) 0x6c,
                    (byte) 0x46, (byte) 0x69, (byte) 0x72, (byte) 0x73, (byte) 0x74, (byte) 0x00, (byte) 0x00, (byte) 0x0a, (byte) 0x09, (byte) 0x01, (byte) 0x07, (byte) 0x00, (byte) 0x41,
                    (byte) 0x00, (byte) 0x11, (byte) 0x00, (byte) 0x00, (byte) 0x0b, (byte) 0x00, (byte) 0x18, (byte) 0x04, (byte) 0x6e, (byte) 0x61, (byte) 0x6d, (byte) 0x65, (byte) 0x01,
                    (byte) 0x0c, (byte) 0x01, (byte) 0x00, (byte) 0x09, (byte) 0x63, (byte) 0x61, (byte) 0x6c, (byte) 0x6c, (byte) 0x46, (byte) 0x69, (byte) 0x72, (byte) 0x73, (byte) 0x74,
                    (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x00,
    };

    // (module
    // (table $defaultTable (export "defaultTable") 4 anyfunc)
    // (func $square (param i32) (result i32)
    // get_local 0
    // get_local 0
    // i32.mul
    // )
    // (elem (i32.const 0) $square)
    // )
    private static final byte[] binaryWithTableExport = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x01, (byte) 0x60, (byte) 0x01,
                    (byte) 0x7f, (byte) 0x01, (byte) 0x7f, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x04, (byte) 0x01, (byte) 0x70, (byte) 0x00, (byte) 0x04,
                    (byte) 0x07, (byte) 0x10, (byte) 0x01, (byte) 0x0c, (byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x74, (byte) 0x54, (byte) 0x61,
                    (byte) 0x62, (byte) 0x6c, (byte) 0x65, (byte) 0x01, (byte) 0x00, (byte) 0x09, (byte) 0x07, (byte) 0x01, (byte) 0x00, (byte) 0x41, (byte) 0x00, (byte) 0x0b, (byte) 0x01,
                    (byte) 0x00, (byte) 0x0a, (byte) 0x09, (byte) 0x01, (byte) 0x07, (byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x6c, (byte) 0x0b, (byte) 0x00,
                    (byte) 0x17, (byte) 0x04, (byte) 0x6e, (byte) 0x61, (byte) 0x6d, (byte) 0x65, (byte) 0x01, (byte) 0x09, (byte) 0x01, (byte) 0x00, (byte) 0x06, (byte) 0x73, (byte) 0x71,
                    (byte) 0x75, (byte) 0x61, (byte) 0x72, (byte) 0x65, (byte) 0x02, (byte) 0x05, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00,
    };

    // (module
    // (type $t0 (func (result i32)))
    // (global $global1 (import "host" "defaultGlobal") i32)
    // (global $global2 i32 (get_global $global1))
    // (func $readGlobal1 (export "readGlobal1") (type $t0) (result i32)
    // get_global $global1
    // )
    // (func $readGlobal2 (export "readGlobal2") (type $t0) (result i32)
    // get_global $global2
    // )
    // )
    private static final byte[] binaryWithGlobalImportI32 = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x01, (byte) 0x7f, (byte) 0x02, (byte) 0x17, (byte) 0x01, (byte) 0x04, (byte) 0x68, (byte) 0x6f, (byte) 0x73, (byte) 0x74, (byte) 0x0d, (byte) 0x64, (byte) 0x65,
                    (byte) 0x66, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x74, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x03, (byte) 0x7f,
                    (byte) 0x00, (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x06, (byte) 0x01, (byte) 0x7f, (byte) 0x00, (byte) 0x23, (byte) 0x00,
                    (byte) 0x0b, (byte) 0x07, (byte) 0x1d, (byte) 0x02, (byte) 0x0b, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62,
                    (byte) 0x61, (byte) 0x6c, (byte) 0x31, (byte) 0x00, (byte) 0x00, (byte) 0x0b, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64, (byte) 0x47, (byte) 0x6c, (byte) 0x6f,
                    (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x32, (byte) 0x00, (byte) 0x01, (byte) 0x0a, (byte) 0x0b, (byte) 0x02, (byte) 0x04, (byte) 0x00, (byte) 0x23, (byte) 0x00,
                    (byte) 0x0b, (byte) 0x04, (byte) 0x00, (byte) 0x23, (byte) 0x01, (byte) 0x0b
    };

    // (module
    // (type $t0 (func (result i64)))
    // (global $global1 (import "host" "defaultGlobal") i64)
    // (global $global2 i64 (get_global $global1))
    // (func $readGlobal1 (export "readGlobal1") (type $t0) (result i64)
    // get_global $global1
    // )
    // (func $readGlobal2 (export "readGlobal2") (type $t0) (result i64)
    // get_global $global2
    // )
    // )
    private static final byte[] binaryWithGlobalImportI64 = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x01, (byte) 0x7e, (byte) 0x02, (byte) 0x17, (byte) 0x01, (byte) 0x04, (byte) 0x68, (byte) 0x6f, (byte) 0x73, (byte) 0x74, (byte) 0x0d, (byte) 0x64, (byte) 0x65,
                    (byte) 0x66, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x74, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x03, (byte) 0x7e,
                    (byte) 0x00, (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x06, (byte) 0x01, (byte) 0x7e, (byte) 0x00, (byte) 0x23, (byte) 0x00,
                    (byte) 0x0b, (byte) 0x07, (byte) 0x1d, (byte) 0x02, (byte) 0x0b, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62,
                    (byte) 0x61, (byte) 0x6c, (byte) 0x31, (byte) 0x00, (byte) 0x00, (byte) 0x0b, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64, (byte) 0x47, (byte) 0x6c, (byte) 0x6f,
                    (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x32, (byte) 0x00, (byte) 0x01, (byte) 0x0a, (byte) 0x0b, (byte) 0x02, (byte) 0x04, (byte) 0x00, (byte) 0x23, (byte) 0x00,
                    (byte) 0x0b, (byte) 0x04, (byte) 0x00, (byte) 0x23, (byte) 0x01, (byte) 0x0b
    };

    // (module
    // (type $t0 (func (result f32)))
    // (global $global1 (import "host" "defaultGlobal") f32)
    // (global $global2 f32 (get_global $global1))
    // (func $readGlobal1 (export "readGlobal1") (type $t0) (result f32)
    // get_global $global1
    // )
    // (func $readGlobal2 (export "readGlobal2") (type $t0) (result f32)
    // get_global $global2
    // )
    // )
    private static final byte[] binaryWithGlobalImportF32 = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x01, (byte) 0x7d, (byte) 0x02, (byte) 0x17, (byte) 0x01, (byte) 0x04, (byte) 0x68, (byte) 0x6f, (byte) 0x73, (byte) 0x74, (byte) 0x0d, (byte) 0x64, (byte) 0x65,
                    (byte) 0x66, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x74, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x03, (byte) 0x7d,
                    (byte) 0x00, (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x06, (byte) 0x01, (byte) 0x7d, (byte) 0x00, (byte) 0x23, (byte) 0x00,
                    (byte) 0x0b, (byte) 0x07, (byte) 0x1d, (byte) 0x02, (byte) 0x0b, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62,
                    (byte) 0x61, (byte) 0x6c, (byte) 0x31, (byte) 0x00, (byte) 0x00, (byte) 0x0b, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64, (byte) 0x47, (byte) 0x6c, (byte) 0x6f,
                    (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x32, (byte) 0x00, (byte) 0x01, (byte) 0x0a, (byte) 0x0b, (byte) 0x02, (byte) 0x04, (byte) 0x00, (byte) 0x23, (byte) 0x00,
                    (byte) 0x0b, (byte) 0x04, (byte) 0x00, (byte) 0x23, (byte) 0x01, (byte) 0x0b
    };

    // (module
    // (type $t0 (func (result f64)))
    // (global $global1 (import "host" "defaultGlobal") f64)
    // (global $global2 f64 (get_global $global1))
    // (func $readGlobal1 (export "readGlobal1") (type $t0) (result f64)
    // get_global $global1
    // )
    // (func $readGlobal2 (export "readGlobal2") (type $t0) (result f64)
    // get_global $global2
    // )
    // )
    private static final byte[] binaryWithGlobalImportF64 = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x01, (byte) 0x7c, (byte) 0x02, (byte) 0x17, (byte) 0x01, (byte) 0x04, (byte) 0x68, (byte) 0x6f, (byte) 0x73, (byte) 0x74, (byte) 0x0d, (byte) 0x64, (byte) 0x65,
                    (byte) 0x66, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x74, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x03, (byte) 0x7c,
                    (byte) 0x00, (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x06, (byte) 0x01, (byte) 0x7c, (byte) 0x00, (byte) 0x23, (byte) 0x00,
                    (byte) 0x0b, (byte) 0x07, (byte) 0x1d, (byte) 0x02, (byte) 0x0b, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62,
                    (byte) 0x61, (byte) 0x6c, (byte) 0x31, (byte) 0x00, (byte) 0x00, (byte) 0x0b, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64, (byte) 0x47, (byte) 0x6c, (byte) 0x6f,
                    (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x32, (byte) 0x00, (byte) 0x01, (byte) 0x0a, (byte) 0x0b, (byte) 0x02, (byte) 0x04, (byte) 0x00, (byte) 0x23, (byte) 0x00,
                    (byte) 0x0b, (byte) 0x04, (byte) 0x00, (byte) 0x23, (byte) 0x01, (byte) 0x0b
    };

    // (module
    // (global i32 (i32.const 1096))
    // (global (mut i32) (i32.const 2345))
    // (func $setGlobal (export "setGlobal") (param i32)
    // get_local 0
    // set_global 1
    // )
    // (func $getGlobal (export "getGlobal") (result i32)
    // get_global 1
    // )
    // (export "exportedGlobal" (global 0))
    // )
    private static final byte[] binaryWithGlobalExport = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x09, (byte) 0x02, (byte) 0x60, (byte) 0x01,
                    (byte) 0x7f, (byte) 0x00, (byte) 0x60, (byte) 0x00, (byte) 0x01, (byte) 0x7f, (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x0d,
                    (byte) 0x02, (byte) 0x7f, (byte) 0x00, (byte) 0x41, (byte) 0xc8, (byte) 0x08, (byte) 0x0b, (byte) 0x7f, (byte) 0x01, (byte) 0x41, (byte) 0xa9, (byte) 0x12, (byte) 0x0b,
                    (byte) 0x07, (byte) 0x2a, (byte) 0x03, (byte) 0x09, (byte) 0x73, (byte) 0x65, (byte) 0x74, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62, (byte) 0x61, (byte) 0x6c,
                    (byte) 0x00, (byte) 0x00, (byte) 0x09, (byte) 0x67, (byte) 0x65, (byte) 0x74, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x00,
                    (byte) 0x01, (byte) 0x0e, (byte) 0x65, (byte) 0x78, (byte) 0x70, (byte) 0x6f, (byte) 0x72, (byte) 0x74, (byte) 0x65, (byte) 0x64, (byte) 0x47, (byte) 0x6c, (byte) 0x6f,
                    (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x03, (byte) 0x00, (byte) 0x0a, (byte) 0x0d, (byte) 0x02, (byte) 0x06, (byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x24,
                    (byte) 0x01, (byte) 0x0b, (byte) 0x04, (byte) 0x00, (byte) 0x23, (byte) 0x01, (byte) 0x0b, (byte) 0x00, (byte) 0x27, (byte) 0x04, (byte) 0x6e, (byte) 0x61, (byte) 0x6d,
                    (byte) 0x65, (byte) 0x01, (byte) 0x17, (byte) 0x02, (byte) 0x00, (byte) 0x09, (byte) 0x73, (byte) 0x65, (byte) 0x74, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62,
                    (byte) 0x61, (byte) 0x6c, (byte) 0x01, (byte) 0x09, (byte) 0x67, (byte) 0x65, (byte) 0x74, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62, (byte) 0x61, (byte) 0x6c,
                    (byte) 0x02, (byte) 0x07, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
    };

    // (module (func (export "\E2\82\AC") (result i32) i32.const 42))
    // E2 82 AC is UTF-8 encoding of Euro sign
    private static final byte[] binaryWithUnicodeExport = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x01, (byte) 0x7f, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x07, (byte) 0x07, (byte) 0x01, (byte) 0x03, (byte) 0xe2, (byte) 0x82, (byte) 0xac,
                    (byte) 0x00, (byte) 0x00, (byte) 0x0a, (byte) 0x06, (byte) 0x01, (byte) 0x04, (byte) 0x00, (byte) 0x41, (byte) 0x2a, (byte) 0x0b
    };

    // Module with 3 custom sections: "even" (with data 2, 4),
    // "odd" (with data 1, 3, 5) and "even" (with data 6)
    private static final byte[] binaryWithCustomSections = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x04, (byte) 0x65, (byte) 0x76,
                    (byte) 0x65, (byte) 0x6e, (byte) 0x02, (byte) 0x04, (byte) 0x00, (byte) 0x07, (byte) 0x03, (byte) 0x6f, (byte) 0x64, (byte) 0x64, (byte) 0x01, (byte) 0x03, (byte) 0x05,
                    (byte) 0x00, (byte) 0x06, (byte) 0x04, (byte) 0x65, (byte) 0x76, (byte) 0x65, (byte) 0x6e, (byte) 0x06
    };

    // Module with an empty name (custom) section
    private static final byte[] binaryWithEmptyNameSection = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x04, (byte) 0x6e, (byte) 0x61,
                    (byte) 0x6d, (byte) 0x65
    };

    // Module with a truncated name (custom) section
    private static final byte[] binaryWithTruncatedNameSection = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x04, (byte) 0x6e, (byte) 0x61,
                    (byte) 0x6d, (byte) 0x65, (byte) 0x00
    };

    // Module with a name (custom) section with function names subsection
    // with an invalid function index
    private static final byte[] binaryWithNameSectionWithInvalidIndex = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0a, (byte) 0x04, (byte) 0x6e, (byte) 0x61,
                    (byte) 0x6d, (byte) 0x65, (byte) 0x01, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x00
    };

    // (module
    // (func (export "f1"))
    // (global (export "g1") i32 (i32.const 1))
    // (table (export "t") 1 anyfunc)
    // (memory (export "m") 1)
    // (global (export "g2") f64 (f64.const 0))
    // (func (export "f2"))
    // )
    private static final byte[] binaryWithMixedExports = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x00, (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x04, (byte) 0x01, (byte) 0x70, (byte) 0x00, (byte) 0x01, (byte) 0x05,
                    (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x12, (byte) 0x02, (byte) 0x7f, (byte) 0x00, (byte) 0x41, (byte) 0x01, (byte) 0x0b, (byte) 0x7c,
                    (byte) 0x00, (byte) 0x44, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0b, (byte) 0x07, (byte) 0x1d,
                    (byte) 0x06, (byte) 0x02, (byte) 0x66, (byte) 0x31, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x67, (byte) 0x31, (byte) 0x03, (byte) 0x00, (byte) 0x01, (byte) 0x74,
                    (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x6d, (byte) 0x02, (byte) 0x00, (byte) 0x02, (byte) 0x67, (byte) 0x32, (byte) 0x03, (byte) 0x01, (byte) 0x02, (byte) 0x66,
                    (byte) 0x32, (byte) 0x00, (byte) 0x01, (byte) 0x0a, (byte) 0x07, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x0b, (byte) 0x02, (byte) 0x00, (byte) 0x0b, (byte) 0x00,
                    (byte) 0x0c, (byte) 0x04, (byte) 0x6e, (byte) 0x61, (byte) 0x6d, (byte) 0x65, (byte) 0x02, (byte) 0x05, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
    };

    // (module
    // (func (import "aux" "f1"))
    // (global (import "aux" "g1") i64)
    // (table (import "aux" "t") 1 anyfunc)
    // (memory (import "aux" "m") 1)
    // (global (import "aux" "g2") f64)
    // (func (import "aux" "f2"))
    // )
    private static final byte[] binaryWithMixedImports = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x00, (byte) 0x02, (byte) 0x3a, (byte) 0x06, (byte) 0x03, (byte) 0x61, (byte) 0x75, (byte) 0x78, (byte) 0x02, (byte) 0x66, (byte) 0x31, (byte) 0x00, (byte) 0x00,
                    (byte) 0x03, (byte) 0x61, (byte) 0x75, (byte) 0x78, (byte) 0x02, (byte) 0x67, (byte) 0x31, (byte) 0x03, (byte) 0x7e, (byte) 0x00, (byte) 0x03, (byte) 0x61, (byte) 0x75,
                    (byte) 0x78, (byte) 0x01, (byte) 0x74, (byte) 0x01, (byte) 0x70, (byte) 0x00, (byte) 0x01, (byte) 0x03, (byte) 0x61, (byte) 0x75, (byte) 0x78, (byte) 0x01, (byte) 0x6d,
                    (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x03, (byte) 0x61, (byte) 0x75, (byte) 0x78, (byte) 0x02, (byte) 0x67, (byte) 0x32, (byte) 0x03, (byte) 0x7c, (byte) 0x00,
                    (byte) 0x03, (byte) 0x61, (byte) 0x75, (byte) 0x78, (byte) 0x02, (byte) 0x66, (byte) 0x32, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0c, (byte) 0x04, (byte) 0x6e,
                    (byte) 0x61, (byte) 0x6d, (byte) 0x65, (byte) 0x02, (byte) 0x05, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
    };

    private static WasmInstance moduleInstantiate(WebAssembly wasm, byte[] source, Object importObject) {
        final WasmModule module = wasm.moduleDecode(source);
        return wasm.moduleInstantiate(module, importObject);
    }
}
