/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.HashMap;
import java.util.function.Consumer;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.ModuleLimits;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.api.ByteArrayBuffer;
import org.graalvm.wasm.api.Dictionary;
import org.graalvm.wasm.api.Executable;
import org.graalvm.wasm.api.Global;
import org.graalvm.wasm.api.ImportExportKind;
import org.graalvm.wasm.api.Instance;
import org.graalvm.wasm.api.Memory;
import org.graalvm.wasm.api.MemoryDescriptor;
import org.graalvm.wasm.api.Module;
import org.graalvm.wasm.api.ModuleExportDescriptor;
import org.graalvm.wasm.api.ModuleImportDescriptor;
import org.graalvm.wasm.api.ProxyGlobal;
import org.graalvm.wasm.api.Sequence;
import org.graalvm.wasm.api.Table;
import org.graalvm.wasm.api.TableDescriptor;
import org.graalvm.wasm.api.TableKind;
import org.graalvm.wasm.api.WebAssembly;
import org.graalvm.wasm.api.WebAssemblyInstantiatedSource;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.predefined.testutil.TestutilModule;
import org.graalvm.wasm.utils.Assert;
import org.junit.Test;

public class WasmJsApiSuite {
    @Test
    public void testCompile() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final Module module = wasm.compile(binaryWithExports);
            try {
                HashMap<String, ModuleExportDescriptor> exports = new HashMap<>();
                int i = 0;
                while (i < module.exports().getArraySize()) {
                    final ModuleExportDescriptor d = (ModuleExportDescriptor) module.exports().readArrayElement(i);
                    exports.put(d.name(), d);
                    i++;
                }
                Assert.assertEquals("Should export main.", ImportExportKind.function, exports.get("main").kind());
                Assert.assertEquals("Should export memory.", ImportExportKind.memory, exports.get("memory").kind());
                Assert.assertEquals("Should export global __heap_base.", ImportExportKind.global, exports.get("__heap_base").kind());
                Assert.assertEquals("Should export global __data_end.", ImportExportKind.global, exports.get("__data_end").kind());
                Assert.assertEquals("Should have empty imports.", 0L, module.imports().getArraySize());
            } catch (InvalidArrayIndexException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiate() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WebAssemblyInstantiatedSource instantiatedSource = wasm.instantiate(binaryWithExports, null);
            final Instance instance = instantiatedSource.instance();
            try {
                final Executable main = (Executable) instance.exports().readMember("main");
                int result = (int) main.executeFunction(new Object[0]);
                Assert.assertEquals("Should return 42 from main.", 42, result);
            } catch (UnknownIdentifierException e) {
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
            final WebAssemblyInstantiatedSource instantiatedSource = wasm.instantiate(binaryWithImportsAndExports, importObject);
            final Instance instance = instantiatedSource.instance();
            try {
                final Executable addPlusOne = (Executable) instance.exports().readMember("addPlusOne");
                int result = (int) addPlusOne.executeFunction(new Object[]{17, 3});
                Assert.assertEquals("17 + 3 + 1 = 21.", 21, result);
            } catch (UnknownIdentifierException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithImportMemory() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final Memory memory = new Memory(new MemoryDescriptor(1, 4));
            Dictionary importObject = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{
                                            "defaultMemory", memory
                            }),
            });
            final WebAssemblyInstantiatedSource instantiatedSource = wasm.instantiate(binaryWithMemoryImport, importObject);
            final Instance instance = instantiatedSource.instance();
            try {
                final Executable initZero = (Executable) instance.exports().readMember("initZero");
                Assert.assertEquals("Must be zero initially.", 0, memory.wasmMemory().load_i32(null, 0));
                initZero.executeFunction(new Object[0]);
                Assert.assertEquals("Must be 174 after initialization.", 174, memory.wasmMemory().load_i32(null, 0));
            } catch (UnknownIdentifierException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithExportMemory() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WebAssemblyInstantiatedSource instantiatedSource = wasm.instantiate(binaryWithMemoryExport, null);
            final Instance instance = instantiatedSource.instance();
            try {
                final Memory memory = (Memory) instance.exports().readMember("memory");
                final Executable readZero = (Executable) instance.exports().readMember("readZero");
                memory.wasmMemory().store_i32(null, 0, 174);
                final Object result = readZero.executeFunction(new Object[0]);
                Assert.assertEquals("Must be 174.", 174, result);
            } catch (UnknownIdentifierException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithImportTable() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final Table table = new Table(new TableDescriptor(TableKind.anyfunc.name(), 1, 4));
            Dictionary importObject = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{
                                            "defaultTable", table
                            }),
            });
            table.set(0, new Executable(args -> 210));
            final WebAssemblyInstantiatedSource instantiatedSource = wasm.instantiate(binaryWithTableImport, importObject);
            final Instance instance = instantiatedSource.instance();
            try {
                final Executable callFirst = (Executable) instance.exports().readMember("callFirst");
                Object result = callFirst.executeFunction(new Object[0]);
                Assert.assertEquals("Must return 210.", 210, result);
            } catch (UnknownIdentifierException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithExportTable() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WebAssemblyInstantiatedSource instantiatedSource = wasm.instantiate(binaryWithTableExport, null);
            final Instance instance = instantiatedSource.instance();
            try {
                final Table table = (Table) instance.exports().readMember("defaultTable");
                final Object result = InteropLibrary.getUncached().execute(table.get(0), 9);
                Assert.assertEquals("Must be 81.", 81, result);
            } catch (UnknownIdentifierException e) {
                throw new RuntimeException(e);
            } catch (UnsupportedTypeException e) {
                throw new RuntimeException(e);
            } catch (UnsupportedMessageException e) {
                throw new RuntimeException(e);
            } catch (ArityException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithImportGlobal() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final Global global = new Global("i32", false, 17);
            Dictionary importObject = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{
                                            "defaultGlobal", global
                            }),
            });
            final WebAssemblyInstantiatedSource instantiatedSource = wasm.instantiate(binaryWithGlobalImport, importObject);
            final Instance instance = instantiatedSource.instance();
            try {
                final Executable readGlobal = (Executable) instance.exports().readMember("readGlobal");
                Assert.assertEquals("Must be 17 initially.", 17, readGlobal.executeFunction(new Object[0]));
            } catch (UnknownIdentifierException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateWithExportGlobal() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WebAssemblyInstantiatedSource instantiatedSource = wasm.instantiate(binaryWithGlobalExport, null);
            final Instance instance = instantiatedSource.instance();
            try {
                final ProxyGlobal global = (ProxyGlobal) instance.exports().readMember("exportedGlobal");
                Assert.assertEquals("Exported global must be 1096.", 1096, global.get());
                final Executable setGlobal = (Executable) instance.exports().readMember("setGlobal");
                final Executable getGlobal = (Executable) instance.exports().readMember("getGlobal");
                Assert.assertEquals("Must be 2345 initially.", 2345, getGlobal.executeFunction(new Object[0]));
                setGlobal.executeFunction(new Object[]{25});
                Assert.assertEquals("Must be 25 later.", 25, getGlobal.executeFunction(new Object[0]));
            } catch (UnknownIdentifierException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testInstantiateModuleTwice() throws IOException {
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            Module module = wasm.compile(binaryWithExports);
            Object importObject = new Dictionary();
            wasm.instantiate(module, importObject);
            wasm.instantiate(module, importObject);
        });
    }

    @Test
    public void testInstantiateWithUnicodeExport() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WebAssemblyInstantiatedSource instantiatedSource = wasm.instantiate(binaryWithUnicodeExport, null);
            final Instance instance = instantiatedSource.instance();
            try {
                final Executable euroSignFn = (Executable) instance.exports().readMember("\u20AC");
                Assert.assertEquals("Result should be 42", 42, euroSignFn.executeFunction(new Object[0]));
            } catch (UnknownIdentifierException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testExportOrder() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final WebAssemblyInstantiatedSource instantiatedSource = wasm.instantiate(binaryWithMixedExports, null);
            final Module module = instantiatedSource.module();
            final Instance instance = instantiatedSource.instance();
            final Sequence<ModuleExportDescriptor> moduleExports = module.exports();
            final Object instanceMembers = instance.exports().getMembers(false);
            String[] expected = new String[]{"f1", "g1", "t", "m", "g2", "f2"};
            try {
                final InteropLibrary lib = InteropLibrary.getUncached();
                Assert.assertEquals("Must export all members.", 6L, lib.getArraySize(instanceMembers));
                for (int i = 0; i < lib.getArraySize(instanceMembers); i++) {
                    final Object instanceMember = lib.readArrayElement(instanceMembers, i);
                    Assert.assertEquals("Module member " + i + " should correspond to the expected export.", expected[i], ((ModuleExportDescriptor) moduleExports.readArrayElement(i)).name());
                    Assert.assertEquals("Instance member " + i + " should correspond to the expected export.", expected[i], lib.asString(instanceMember));
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testImportOrder() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final Module module = wasm.compile(binaryWithMixedImports);
            final Sequence<ModuleImportDescriptor> moduleImports = module.imports();
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
    public void testModuleLimits() throws IOException {
        runTest(context -> {
            ModuleLimits limits = null;
            context.readModule(binaryWithMixedExports, limits);

            int noLimit = Integer.MAX_VALUE;
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
    public void testCustomSections() throws IOException {
        runTest(context -> {
            final WebAssembly wasm = new WebAssembly(context);
            final Module module = wasm.compile(binaryWithCustomSections);
            try {
                checkCustomSections(new byte[][]{}, module.customSections(""));
                checkCustomSections(new byte[][]{}, module.customSections("zero"));
                checkCustomSections(new byte[][]{{1, 3, 5}}, module.customSections("odd"));
                checkCustomSections(new byte[][]{{2, 4}, {6}}, module.customSections("even"));
            } catch (InvalidArrayIndexException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void checkCustomSections(byte[][] expected, Sequence<ByteArrayBuffer> actual) throws InvalidArrayIndexException {
        Assert.assertEquals("Custom section count", expected.length, (int) actual.getArraySize());
        for (int i = 0; i < expected.length; i++) {
            checkCustomSection(expected[i], (ByteArrayBuffer) actual.readArrayElement(i));
        }
    }

    private static void checkCustomSection(byte[] expected, ByteArrayBuffer actual) throws InvalidArrayIndexException {
        Assert.assertEquals("Custom section length", expected.length, (int) actual.getArraySize());
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals("Custom section data", expected[i], actual.readArrayElement(i));
        }
    }

    private static void runTest(Consumer<WasmContext> testCase) throws IOException {
        final Context.Builder contextBuilder = Context.newBuilder("wasm");
        contextBuilder.option("wasm.Builtins", "testutil:testutil");
        final Context context = contextBuilder.build();
        Source.Builder sourceBuilder = Source.newBuilder("wasm", ByteSequence.create(binaryWithExports), "main");
        Source source = sourceBuilder.build();
        context.eval(source);
        Value main = context.getBindings("wasm").getMember("main").getMember("main");
        main.execute();
        Value run = context.getBindings("wasm").getMember("testutil").getMember(TestutilModule.Names.RUN_CUSTOM_INITIALIZATION);
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
    // (global $global (import "host" "defaultGlobal") i32)
    // (func $readGlobal (export "readGlobal") (type $t0) (result i32)
    // get_global $global
    // )
    // )
    private static final byte[] binaryWithGlobalImport = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x01, (byte) 0x7f, (byte) 0x02, (byte) 0x17, (byte) 0x01, (byte) 0x04, (byte) 0x68, (byte) 0x6f, (byte) 0x73, (byte) 0x74, (byte) 0x0d, (byte) 0x64, (byte) 0x65,
                    (byte) 0x66, (byte) 0x61, (byte) 0x75, (byte) 0x6c, (byte) 0x74, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x03, (byte) 0x7f,
                    (byte) 0x00, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x07, (byte) 0x0e, (byte) 0x01, (byte) 0x0a, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64,
                    (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x00, (byte) 0x00, (byte) 0x0a, (byte) 0x06, (byte) 0x01, (byte) 0x04, (byte) 0x00,
                    (byte) 0x23, (byte) 0x00, (byte) 0x0b, (byte) 0x00, (byte) 0x19, (byte) 0x04, (byte) 0x6e, (byte) 0x61, (byte) 0x6d, (byte) 0x65, (byte) 0x01, (byte) 0x0d, (byte) 0x01,
                    (byte) 0x00, (byte) 0x0a, (byte) 0x72, (byte) 0x65, (byte) 0x61, (byte) 0x64, (byte) 0x47, (byte) 0x6c, (byte) 0x6f, (byte) 0x62, (byte) 0x61, (byte) 0x6c, (byte) 0x02,
                    (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x00,
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
}
