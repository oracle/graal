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

package org.graalvm.wasm.test.suites.bytecode;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.api.Dictionary;
import org.graalvm.wasm.api.Executable;
import org.graalvm.wasm.api.Sequence;
import org.graalvm.wasm.api.TableKind;
import org.graalvm.wasm.api.ValueType;
import org.graalvm.wasm.api.WebAssembly;
import org.graalvm.wasm.exception.WasmJsApiException;
import org.graalvm.wasm.globals.WasmGlobal;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.testutil.TestutilModule;
import org.graalvm.wasm.utils.WasmBinaryTools;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

/**
 * Tests that modules can be instantiated several times.
 */
public class MultiInstantiationSuite {
    private static void test(Function<WebAssembly, byte[]> sourceFun, Function<WebAssembly, Object> importFun, BiConsumer<WebAssembly, WasmInstance> check) throws IOException {
        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.option("wasm.Builtins", "testutil:testutil");
        try (Context context = contextBuilder.build()) {
            Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binaryWithExports), "main");
            Source source = sourceBuilder.build();
            context.eval(source);
            Value main = context.getBindings(WasmLanguage.ID).getMember("main").getMember("main");
            main.execute();
            Value run = context.getBindings(WasmLanguage.ID).getMember("testutil").getMember(TestutilModule.Names.RUN_CUSTOM_INITIALIZATION);
            run.execute(new GuestCode(c -> {
                WebAssembly wasm = new WebAssembly(c);
                WasmModule module = wasm.moduleDecode(sourceFun.apply(wasm));
                WasmInstance instance1 = wasm.moduleInstantiate(module, importFun.apply(wasm));
                Value v1 = Value.asValue(instance1);
                // link module
                v1.getMember("main");
                WasmInstance instance2 = wasm.moduleInstantiate(module, importFun.apply(wasm));
                Value v2 = Value.asValue(instance2);
                // link module
                v2.getMember("main");
                check.accept(wasm, instance2);
            }));
        }
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

    @Test
    public void testImportsAndExports() throws IOException, InterruptedException {
        final byte[] source = WasmBinaryTools.compileWat("main", """
                        (module
                           (type (;0;) (func (result i32)))
                           (import "a" "f" (func (type 0)))
                           (import "a" "t" (table 2 2 funcref))
                           (import "a" "m" (memory 1 1))
                           (import "a" "g" (global i32))
                           (func (type 0) i32.const 13)
                           (func (export "main") (type 0) i32.const 0)
                           (func (export "test") (type 0)
                               call 0
                               i32.const 1
                               call_indirect (type 0)
                               i32.add
                               global.get 0
                               i32.add
                               i32.const 0
                               i32.load
                               i32.add
                           )
                           (export "f" (func 0))
                           (export "t" (table 0))
                           (export "m" (memory 0))
                           (export "g" (global 0))
                           (elem (i32.const 1) func 1)
                        )
                        """);
        final Executable tableFun = new Executable(args -> 13);
        test(wasm -> source, wasm -> {
            final Dictionary imports = new Dictionary();
            final Dictionary a = new Dictionary();

            a.addMember("f", new Executable(args -> 42));

            final WasmTable t = wasm.tableAlloc(2, 2, TableKind.anyfunc, tableFun);
            a.addMember("t", t);

            final WasmMemory m = WebAssembly.memAlloc(1, 1, false);
            m.store_i32_8(null, 0, (byte) 5);
            a.addMember("m", m);

            final WasmGlobal g = wasm.globalAlloc(ValueType.i32, false, 4);
            a.addMember("g", g);

            imports.addMember("a", a);
            return imports;
        }, (wasm, i) -> {
            try {
                InteropLibrary lib = InteropLibrary.getUncached();
                final Object f = WebAssembly.instanceExport(i, "f");
                final int fValue = lib.asInt(lib.execute(f));
                Assert.assertEquals("Function return does not match", 42, fValue);

                final Object t = WebAssembly.instanceExport(i, "t");
                final Object tableRead = wasm.readMember("table_read");
                final Object tFun = lib.execute(tableRead, t, 0);
                Assert.assertEquals("Table functions do not match", tableFun, tFun);

                final Object m = WebAssembly.instanceExport(i, "m");
                final byte b = lib.asByte(lib.readArrayElement(m, 0));
                Assert.assertEquals("Memory element does not match", (byte) 5, b);

                final Object g = WebAssembly.instanceExport(i, "g");
                final Object globalRead = wasm.readMember("global_read");
                final int gValue = lib.asInt(lib.execute(globalRead, g));
                Assert.assertEquals("Global value does not match", 4, gValue);

                final Object test = WebAssembly.instanceExport(i, "test");
                final int result = lib.asInt(lib.execute(test));
                Assert.assertEquals("Invalid test value", 64, result);
            } catch (UnknownIdentifierException | UnsupportedTypeException | ArityException | UnsupportedMessageException | InvalidArrayIndexException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testGlobalInitialization() throws IOException, InterruptedException {
        final byte[] source = WasmBinaryTools.compileWat("main", """
                        (module
                           (import "a" "g" (global i32))
                           (func (export "main") (result i32) i32.const 0)
                           (global (export "g1") i32 (i32.const 13))
                           (global (export "g2") i32 (global.get 0))
                           (global (export "g3") (mut i32) (i32.const 42))
                           (global (export "g4") (mut i32) (global.get 0))
                           (global (export "g5") funcref (ref.null func))
                           (global (export "g6") (mut funcref) (ref.func 0))
                        )
                        """);
        test(wasm -> source, wasm -> {
            WasmGlobal g = wasm.globalAlloc(ValueType.i32, false, 16);
            return Dictionary.create(new Object[]{"a", Dictionary.create(new Object[]{"g", g})});
        }, (wasm, i) -> {
            InteropLibrary lib = InteropLibrary.getUncached();
            try {
                final Object globalRead = wasm.readMember("global_read");
                final Object globalWrite = wasm.readMember("global_write");

                final Object g1 = WebAssembly.instanceExport(i, "g1");
                final int g1Value = lib.asInt(lib.execute(globalRead, g1));
                Assert.assertEquals("Global value of g1 does not match", 13, g1Value);
                try {
                    lib.execute(globalWrite, g1, 0);
                    Assert.fail("Global g1 should be immutable");
                } catch (WasmJsApiException e) {
                    Assert.assertTrue("Unexpected error", e.getMessage().contains("not mutable"));
                }

                final Object g2 = WebAssembly.instanceExport(i, "g2");
                final int g2Value = lib.asInt(lib.execute(globalRead, g2));
                Assert.assertEquals("Global value of g2 does not match", 16, g2Value);
                try {
                    lib.execute(globalWrite, g2, 0);
                    Assert.fail("Global g2 should be immutable");
                } catch (WasmJsApiException e) {
                    Assert.assertTrue("Unexpected error", e.getMessage().contains("not mutable"));
                }

                final Object g3 = WebAssembly.instanceExport(i, "g3");
                final int g3Value = lib.asInt(lib.execute(globalRead, g3));
                Assert.assertEquals("Global value of g3 does not match", 42, g3Value);
                lib.execute(globalWrite, g3, 0);

                final Object g4 = WebAssembly.instanceExport(i, "g4");
                final int g4Value = lib.asInt(lib.execute(globalRead, g4));
                Assert.assertEquals("Global value of g4 does not match", 16, g4Value);
                lib.execute(globalWrite, g4, 0);

                final Object g5 = WebAssembly.instanceExport(i, "g5");
                final Object g5Value = lib.execute(globalRead, g5);
                Assert.assertEquals("Global value of g5 does not match", WasmConstant.NULL, g5Value);
                try {
                    lib.execute(globalWrite, g5, WasmConstant.NULL);
                    Assert.fail("Global g5 should be immutable");
                } catch (WasmJsApiException e) {
                    Assert.assertTrue("Unexpected error", e.getMessage().contains("not mutable"));
                }

                final Object g6 = WebAssembly.instanceExport(i, "g6");
                final Object g6Value = lib.execute(globalRead, g6);
                final int g6FuncValue = lib.asInt(lib.execute(g6Value));
                Assert.assertEquals("Global value of g6 does not match", 0, g6FuncValue);

                final Object main = WebAssembly.instanceExport(i, "main");
                lib.execute(globalWrite, g6, main);
                lib.execute(globalWrite, g6, WasmConstant.NULL);
            } catch (UnknownIdentifierException | UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testTableInitialization() throws IOException, InterruptedException {
        final byte[] source = WasmBinaryTools.compileWat("main", """
                        (module
                           (func (export "main") (result i32) i32.const 0)
                           (table (export "t") 2 2 funcref)
                           (elem (i32.const 0) func 0)
                        )
                        """);
        test(wasm -> source, wasm -> null, (wasm, i) -> {
            InteropLibrary lib = InteropLibrary.getUncached();
            try {
                final Object tableRead = wasm.readMember("table_read");
                final Object tableWrite = wasm.readMember("table_write");

                final Object main = WebAssembly.instanceExport(i, "main");

                final Object t = WebAssembly.instanceExport(i, "t");
                final Object t1 = lib.execute(tableRead, t, 0);
                Assert.assertEquals("Table element [0] does not match", main, t1);

                final Object t2 = lib.execute(tableRead, t, 1);
                Assert.assertEquals("Table element [1] does not match", WasmConstant.NULL, t2);

                lib.execute(tableWrite, t, 0, WasmConstant.NULL);
                lib.execute(tableWrite, t, 1, main);
            } catch (UnknownIdentifierException | UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testMemoryInitialization() throws IOException, InterruptedException {
        final byte[] source = WasmBinaryTools.compileWat("main", """
                        (module
                           (func (export "main") (result i32) i32.const 0)
                           (memory (export "m") 1 1)
                           (data (i32.const 0) "\\01\\02\\03\\04")
                        )
                        """);
        test(wasm -> source, wasm -> null, (wasm, i) -> {
            final Value m = Value.asValue(WebAssembly.instanceExport(i, "m"));

            final int b0 = m.getArrayElement(0).asByte();
            Assert.assertEquals("Memory element [0] does not match", 1, b0);

            final int b1 = m.getArrayElement(1).asByte();
            Assert.assertEquals("Memory element [1] does not match", 2, b1);

            final int b2 = m.getArrayElement(2).asByte();
            Assert.assertEquals("Memory element [2] does not match", 3, b2);

            final int b3 = m.getArrayElement(3).asByte();
            Assert.assertEquals("Memory element [3] does not match", 4, b3);

            m.setArrayElement(0, (byte) 5);
        });
    }

    @Test
    public void testDataSections() throws IOException, InterruptedException {
        final byte[] source = WasmBinaryTools.compileWat("main", """
                        (module
                           (func (export "main") (result i32) i32.const 0)
                           (func (export "test")
                               i32.const 256
                               i32.const 0
                               i32.const 4
                               memory.init 2
                               data.drop 2
                           )
                           (memory (export "m") 2 2)
                           (data (i32.const 0) "\\00\\01\\02\\03")
                           (data (i32.const 65536) "\\04\\05\\06\\07")
                           (data "\\08\\09\\0A\\0B")
                        )
                        """);
        test(wasm -> source, wasm -> null, (wasm, i) -> {
            final Value m = Value.asValue(WebAssembly.instanceExport(i, "m"));

            final int b0 = m.getArrayElement(0).asByte();
            Assert.assertEquals("Memory element [0] does not match", 0, b0);
            final int b1 = m.getArrayElement(1).asByte();
            Assert.assertEquals("Memory element [1] does not match", 1, b1);
            final int b2 = m.getArrayElement(2).asByte();
            Assert.assertEquals("Memory element [2] does not match", 2, b2);
            final int b3 = m.getArrayElement(3).asByte();
            Assert.assertEquals("Memory element [3] does not match", 3, b3);

            final int b65536 = m.getArrayElement(65536).asByte();
            Assert.assertEquals("Memory element [65536] does not match", 4, b65536);
            final int b65537 = m.getArrayElement(65537).asByte();
            Assert.assertEquals("Memory element [65537] does not match", 5, b65537);
            final int b65538 = m.getArrayElement(65538).asByte();
            Assert.assertEquals("Memory element [65538] does not match", 6, b65538);
            final int b65539 = m.getArrayElement(65539).asByte();
            Assert.assertEquals("Memory element [65539] does not match", 7, b65539);

            int b256 = m.getArrayElement(256).asByte();
            Assert.assertEquals("Memory element [256] does not match", 0, b256);
            int b257 = m.getArrayElement(257).asByte();
            Assert.assertEquals("Memory element [257] does not match", 0, b257);
            int b258 = m.getArrayElement(258).asByte();
            Assert.assertEquals("Memory element [258] does not match", 0, b258);
            int b259 = m.getArrayElement(259).asByte();
            Assert.assertEquals("Memory element [259] does not match", 0, b259);

            final Value test = Value.asValue(WebAssembly.instanceExport(i, "test"));
            test.execute();

            b256 = m.getArrayElement(256).asByte();
            Assert.assertEquals("Memory element [256] does not match", 8, b256);
            b257 = m.getArrayElement(257).asByte();
            Assert.assertEquals("Memory element [257] does not match", 9, b257);
            b258 = m.getArrayElement(258).asByte();
            Assert.assertEquals("Memory element [258] does not match", 10, b258);
            b259 = m.getArrayElement(259).asByte();
            Assert.assertEquals("Memory element [259] does not match", 11, b259);
        });
    }

    @Test
    public void testElemSections() throws IOException, InterruptedException {
        final byte[] source = WasmBinaryTools.compileWat("main", """
                        (module
                           (type (;0;) (func (result i32)))
                           (type (;1;) (func))
                           (func (;0;) (type 0) i32.const 1)
                           (func (;1;) (type 0) i32.const 2)
                           (func (;2;) (type 0) i32.const 3)
                           (func (;3;) (type 0) i32.const 4)
                           (func (export "main") (type 0) i32.const 0)
                           (func (export "test") (type 1)
                               i32.const 2
                               i32.const 0
                               i32.const 4
                               table.init 2
                               elem.drop 2
                               ref.func 5
                               drop
                           )
                           (table (export "t") 6 6 funcref)
                           (elem (i32.const 1) func 4)
                           (elem declare func 5)
                           (elem func 0 1 2 3)
                        )
                        """);
        test(wasm -> source, wasm -> null, (wasm, i) -> {
            InteropLibrary lib = InteropLibrary.getUncached();
            try {
                final Object tableRead = wasm.readMember("table_read");
                final Object t = WebAssembly.instanceExport(i, "t");

                final Object t0 = lib.execute(tableRead, t, 0);
                Assert.assertEquals("Table element [0] does not match", WasmConstant.NULL, t0);
                final Object t1 = lib.execute(tableRead, t, 1);
                final int t1Value = lib.asInt(lib.execute(t1));
                Assert.assertEquals("Table element [1] does not match", 0, t1Value);

                Object t2 = lib.execute(tableRead, t, 2);
                Assert.assertEquals("Table element [2] does not match", WasmConstant.NULL, t2);
                Object t3 = lib.execute(tableRead, t, 3);
                Assert.assertEquals("Table element [3] does not match", WasmConstant.NULL, t3);
                Object t4 = lib.execute(tableRead, t, 4);
                Assert.assertEquals("Table element [4] does not match", WasmConstant.NULL, t4);
                Object t5 = lib.execute(tableRead, t, 5);
                Assert.assertEquals("Table element [5] does not match", WasmConstant.NULL, t5);

                final Object test = WebAssembly.instanceExport(i, "test");
                lib.execute(test);

                t2 = lib.execute(tableRead, t, 2);
                final int t2Value = lib.asInt(lib.execute(t2));
                Assert.assertEquals("Table element [2] does not match", 1, t2Value);
                t3 = lib.execute(tableRead, t, 3);
                final int t3Value = lib.asInt(lib.execute(t3));
                Assert.assertEquals("Table element [3] does not match", 2, t3Value);
                t4 = lib.execute(tableRead, t, 4);
                final int t4Value = lib.asInt(lib.execute(t4));
                Assert.assertEquals("Table element [4] does not match", 3, t4Value);
                t5 = lib.execute(tableRead, t, 5);
                final int t5Value = lib.asInt(lib.execute(t5));
                Assert.assertEquals("Table element [5] does not match", 4, t5Value);
            } catch (UnknownIdentifierException | UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMultiValueReturn() throws IOException, InterruptedException {
        final byte[] sourceCode = WasmBinaryTools.compileWat("main", """
                        (module
                            (import "a" "f" (func $f (result i32 i32 f64)))
                            (func (export "main") (result i64 i32 i32 f64)
                                i64.const 42
                                call $f
                                return
                            )
                            (func (export "test") (result i32 i32 f64)
                                i32.const 6
                                i32.const 8
                                f64.const 2.72
                                return
                            )
                        )
                        """);

        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.option("wasm.Builtins", "testutil:testutil");
        try (Context context = contextBuilder.build()) {
            Value run = context.getBindings(WasmLanguage.ID).getMember("testutil").getMember(TestutilModule.Names.RUN_CUSTOM_INITIALIZATION);
            run.execute(new GuestCode(c -> {
                WebAssembly wasm = new WebAssembly(c);
                WasmModule module = wasm.moduleDecode(sourceCode);

                final Dictionary imports1 = new Dictionary();
                final Dictionary importedModule1 = new Dictionary();
                importedModule1.addMember("f", new Executable(args -> new Sequence<>(List.of(1, 2, 3.14))));
                imports1.addMember("a", importedModule1);

                WasmInstance instance1 = wasm.moduleInstantiate(module, imports1);
                Object testFn1 = WebAssembly.instanceExport(instance1, "test");

                final Dictionary imports2 = new Dictionary();
                final Dictionary importedModule2 = new Dictionary();
                importedModule2.addMember("f", testFn1);
                imports2.addMember("a", importedModule2);

                WasmInstance instance2 = wasm.moduleInstantiate(module, imports2);

                Value v1 = context.asValue(instance1);
                v1.getMember("main");
                Value main1 = v1.getMember("main");
                Value result1 = main1.execute();
                Assert.assertEquals("Return value of main", List.of(42L, 1, 2, 3.14), List.copyOf(result1.as(List.class)));

                Value v2 = context.asValue(instance2);
                Value main2 = v2.getMember("main");
                Value result2 = main2.execute();
                Assert.assertEquals("Return value of main", List.of(42L, 6, 8, 2.72), List.copyOf(result2.as(List.class)));
            }));
        }
    }
}
