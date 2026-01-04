/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.test.WasmTestUtils.hexStringToByteArray;
import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.NativeWasmMemory;
import org.graalvm.wasm.memory.UnsafeWasmMemory;
import org.graalvm.wasm.memory.WasmMemoryLibrary;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;

public class WasmPolyglotTestSuite {
    @Test
    public void testEmpty() throws IOException {
        try (Context context = Context.newBuilder().build()) {
            context.parse(Source.newBuilder(WasmLanguage.ID, ByteSequence.create(new byte[0]), "someName").build());
        } catch (PolyglotException pex) {
            Assert.assertTrue("Must be a syntax error.", pex.isSyntaxError());
            Assert.assertFalse("Must not be an internal error.", pex.isInternalError());
        }
    }

    @Test
    public void test42() throws IOException {
        Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binaryReturnConst), "main");
        Source source = sourceBuilder.build();
        try (Context context = contextBuilder.build()) {
            Value mainFunction = context.eval(source).newInstance().getMember("exports").getMember("main");
            Value result = mainFunction.execute();
            Assert.assertEquals("Should be equal: ", 42, result.asInt());
        }
    }

    @Test
    public void unsafeMemoryFreed() throws IOException {
        Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binaryReturnConst), "main");
        Source source = sourceBuilder.build();
        contextBuilder.allowExperimentalOptions(true);
        contextBuilder.option("wasm.UseUnsafeMemory", "true");
        // Force use of UnsafeWasmMemory
        contextBuilder.option("wasm.DirectByteBufferMemoryAccess", "true");
        Context context = contextBuilder.build();
        context.enter();

        final Value mainExports = context.eval(source).newInstance().getMember("exports");
        mainExports.getMember("main").execute();
        final TruffleLanguage.Env env = WasmContext.get(null).environment();
        final Value memoryValue = mainExports.getMember("memory");
        final UnsafeWasmMemory memory = (UnsafeWasmMemory) env.asGuestValue(memoryValue);
        Assert.assertFalse("Memory should have been allocated.", WasmMemoryLibrary.getUncached().freed(memory));
        context.leave();
        context.close();
        // Cannot access memory after context was closed.
        Assert.assertThrows(IllegalStateException.class, () -> memoryValue.readBufferInt(ByteOrder.nativeOrder(), 0));

        // ByteBuffer and byte[]-backed memories are not automatically closed.
        memory.close();
        Assert.assertTrue("Memory should have been freed.", WasmMemoryLibrary.getUncached().freed(memory));
        // Cannot access memory after free.
        Assert.assertThrows(WasmException.class, () -> WasmMemoryLibrary.getUncached().load_i32(memory, null, 0));
    }

    @Test
    public void nativeMemoryFreed() throws IOException {
        Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binaryReturnConst), "main");
        Source source = sourceBuilder.build();
        contextBuilder.allowExperimentalOptions(true);
        contextBuilder.option("wasm.UseUnsafeMemory", "true");
        contextBuilder.option("wasm.DirectByteBufferMemoryAccess", "false");
        Context context = contextBuilder.build();
        context.enter();
        final Value mainExports = context.eval(source).newInstance().getMember("exports");
        mainExports.getMember("main").execute();
        final TruffleLanguage.Env env = WasmContext.get(null).environment();
        final Value memoryValue = mainExports.getMember("memory");
        final NativeWasmMemory memory = (NativeWasmMemory) env.asGuestValue(memoryValue);
        Assert.assertFalse("Memory should have been allocated.", WasmMemoryLibrary.getUncached().freed(memory));
        context.leave();
        context.close();
        // Cannot access memory after context was closed.
        Assert.assertThrows(IllegalStateException.class, () -> memoryValue.readBufferInt(ByteOrder.nativeOrder(), 0));

        // Native-backed memories are automatically closed.
        Assert.assertTrue("Memory should have been freed.", WasmMemoryLibrary.getUncached().freed(memory));

        // Cannot access memory after free. Must not crash either.
        Assert.assertThrows(WasmException.class, () -> WasmMemoryLibrary.getUncached().load_i32(memory, null, 0));
    }

    @Test
    public void overwriteElement() throws IOException, InterruptedException {
        final ByteSequence test = ByteSequence.create(compileWat("test", textOverwriteElement));
        Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, test, "main");
        Source source = sourceBuilder.build();
        try (Context context = contextBuilder.build()) {
            Value mainFunction = context.eval(source).newInstance().getMember("exports").getMember("main");
            Value result = mainFunction.execute();
            Assert.assertEquals("Should be equal: ", 11, result.asInt());
        }
    }

    @Test
    public void divisionByZeroStressTest() throws IOException, InterruptedException {
        String divisionByZeroWAT = "(module (func (export \"main\") (result i32) i32.const 1 i32.const 0 i32.div_s))";
        ByteSequence test = ByteSequence.create(compileWat("test", divisionByZeroWAT));
        Source source = Source.newBuilder(WasmLanguage.ID, test, "main").build();
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            Value mainFunction = context.eval(source).newInstance().getMember("exports").getMember("main");

            for (int iteration = 0; iteration < 20000; iteration++) {
                try {
                    mainFunction.execute();
                    Assert.fail("Should have thrown");
                } catch (PolyglotException pex) {
                    Assert.assertFalse("Should not throw internal error", pex.isInternalError());
                }
            }
        }
    }

    @Test
    public void extractKeys() throws IOException {
        ByteSequence test = ByteSequence.create(binaryReturnConst);
        Source source = Source.newBuilder(WasmLanguage.ID, test, "main").build();
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            Value instance = context.eval(source).newInstance();
            Set<String> keys = instance.getMember("exports").getMemberKeys();
            Assert.assertTrue("Should contain function 'main'", keys.contains("main"));
            Assert.assertTrue("Should contain memory 'memory'", keys.contains("memory"));
            Assert.assertTrue("Should contain global '__heap_base'", keys.contains("__heap_base"));
            Assert.assertTrue("Should contain global '__data_end'", keys.contains("__data_end"));
        }
    }

    @Test
    public void deeplyNestedBrIf() throws IOException, InterruptedException {
        // This code resembles the deeply nested br_if in WebAssembly part of undici
        int depth = 256;
        final String wat = "(module (func (export \"main\") (result i32) (block $my_block " +
                        "(block ".repeat(depth) +
                        "i32.const 0 br_if $my_block i32.const 35 i32.const 0 drop drop" +
                        ")".repeat(depth) +
                        ") i32.const 42))";

        ByteSequence bytes = ByteSequence.create(compileWat("test", wat));
        Source source = Source.newBuilder(WasmLanguage.ID, bytes, "main").build();
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            Value mainFunction = context.eval(source).newInstance().getMember("exports").getMember("main");
            Value result = mainFunction.execute();
            Assert.assertEquals("Should be equal: ", 42, result.asInt());
        }
    }

    // (module
    // (type (;0;) (func))
    // (type (;1;) (func (result i32)))
    // (func (;0;) (type 0))
    // (func (;1;) (type 1) (result i32)
    // i32.const 42)
    // (table (;0;) 1 1 funcref)
    // (memory (;0;) 0)
    // (global (;0;) (mut i32) (i32.const 66560))
    // (global (;1;) i32 (i32.const 66560))
    // (global (;2;) i32 (i32.const 1024))
    // (export "main" (func 1))
    // (export "memory" (memory 0))
    // (export "__heap_base" (global 1))
    // (export "__data_end" (global 2)))
    private static final byte[] binaryReturnConst = hexStringToByteArray(
                    "0061736d010000000108026000006000",
                    "017f0303020001040501700101010503",
                    "0100010615037f01418088040b7f0041",
                    "8088040b7f004180080b072c04046d61",
                    "696e0001066d656d6f727902000b5f5f",
                    "686561705f6261736503010a5f5f6461",
                    "74615f656e6403020a090202000b0400",
                    "412a0b");

    private static final String textOverwriteElement = """
                    (module
                      (table 10 funcref)
                      (type (func (result i32)))
                      (func $f (result i32)
                        i32.const 7)
                      (func $g (result i32)
                        i32.const 11)
                      (func (result i32)
                        i32.const 3
                        call_indirect (type 0))
                      (export "main" (func 2))
                      (elem (i32.const 0) $f)
                      (elem (i32.const 3) $f)
                      (elem (i32.const 7) $f)
                      (elem (i32.const 5) $f)
                      (elem (i32.const 3) $g)
                    )
                    """;

    private static final String simpleTestModule = """
                    (module
                        (func (export "main") (result i32)
                            i32.const 13
                        )
                    )
                    """;

    private static final String simpleImportModule = """
                        (module
                            (import "main" "main" (func $m (result i32)))
                            (func (export "test") (result i32)
                                call $m
                            )
                        )
                    """;

    private static Source simpleTestModuleSource;
    private static Source simpleImportModuleSource;

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        final ByteSequence simpleTestModuleData = ByteSequence.create(compileWat("simpleTestModule", simpleTestModule));
        simpleTestModuleSource = Source.newBuilder(WasmLanguage.ID, simpleTestModuleData, "main").build();

        final ByteSequence simpleImportModuleData = ByteSequence.create(compileWat("simpleImportModule", simpleImportModule));
        simpleImportModuleSource = Source.newBuilder(WasmLanguage.ID, simpleImportModuleData, "test").build();
    }

    @Test
    public void instantiateModuleWithImportObject() {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Proxy executable = (ProxyExecutable) args -> 42;
            final Proxy function = ProxyObject.fromMap(Map.of("main", executable));
            final Proxy importObject = ProxyObject.fromMap(Map.of("main", function));

            final Value importModule = context.eval(simpleImportModuleSource);

            final Value instance = importModule.newInstance(importObject);

            final Value result = instance.getMember("exports").invokeMember("test");

            Assert.assertEquals(42, result.asInt());
        }
    }

    @Test
    public void instantiateModuleWithMissingImportObject() {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Value importModule = context.eval(simpleImportModuleSource);

            try {
                importModule.newInstance();
                Assert.fail("Should have failed because of missing import");
            } catch (PolyglotException e) {
                Assert.assertFalse(e.isExit());
            }
        }
    }

    @Test
    public void instantiateModuleWithMissingFunction() {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Proxy importObject = ProxyObject.fromMap(Map.of("main", ProxyObject.fromMap(Collections.emptyMap())));

            final Value importModule = context.eval(simpleImportModuleSource);

            try {
                importModule.newInstance(importObject);
                Assert.fail("Should have failed because of incorrect import");
            } catch (PolyglotException e) {
                Assert.assertFalse(e.isExit());
                Assert.assertTrue(e.getMessage().contains("does not contain \"main\""));
            }
        }
    }

    @Test
    public void instantiateModuleWithNonExecutableFunction() {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Proxy importObject = ProxyObject.fromMap(Map.of("main", ProxyObject.fromMap(Map.of("main", 5))));

            final Value importModule = context.eval(simpleImportModuleSource);

            try {
                importModule.newInstance(importObject);
                Assert.fail("Should have failed because of incorrect import");
            } catch (PolyglotException e) {
                Assert.assertFalse(e.isExit());
                Assert.assertTrue(e.getMessage().contains("is not callable"));
            }
        }
    }

    @Test
    public void instantiateModuleWithTwoImportObjects() {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Proxy importObject = ProxyObject.fromMap(Map.of("main", ProxyObject.fromMap(Map.of("main", 5))));
            final Proxy importObject2 = ProxyObject.fromMap(Map.of("main2", ProxyObject.fromMap(Map.of("main2", 6))));

            final Value importModule = context.eval(simpleImportModuleSource);

            try {
                importModule.newInstance(importObject, importObject2);
                Assert.fail("Should have failed because of incorrect number of import objects");
            } catch (PolyglotException e) {
                Assert.assertFalse(e.isExit());
                Assert.assertTrue(e.getMessage().contains("single import object"));
            }
        }
    }

    @Test
    public void instantiateModuleWithExportsFromAnotherModule() {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Value testModule = context.eval(simpleTestModuleSource);
            final Value importModule = context.eval(simpleImportModuleSource);

            final Value testInstance = testModule.newInstance();
            final Value importInstance = importModule.newInstance(ProxyObject.fromMap(Map.of(
                            "main", testInstance.getMember("exports"))));

            final Value result = importInstance.getMember("exports").getMember("test").execute();

            Assert.assertEquals(13, result.asInt());
        }
    }

    @Test
    public void instantiateModuleWithGlobalImport() throws IOException, InterruptedException {
        final ByteSequence supplierBytes = ByteSequence.create(compileWat("supplier", """
                        (module
                          (global (export "g") i32 (i32.const 42))
                        )
                        """));
        final ByteSequence consumerBytes = ByteSequence.create(compileWat("consumer", """
                        (module
                          (import "supplier" "g" (global $g i32))
                          (export "gg" (global $g))
                          (func (export "main") (result i32)
                            (global.get $g)
                          )
                        )
                        """));

        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Value supplierModule = context.eval(Source.newBuilder(WasmLanguage.ID, supplierBytes, "supplier").build());
            final Value consumerModule = context.eval(Source.newBuilder(WasmLanguage.ID, consumerBytes, "consumer").build());

            final Value supplierInstance = supplierModule.newInstance();
            final Value consumerInstance = consumerModule.newInstance(ProxyObject.fromMap(Map.of(
                            "supplier", supplierInstance.getMember("exports"))));

            final Value consumerExports = consumerInstance.getMember("exports");
            final Value result = consumerExports.invokeMember("main");
            final Value global = consumerExports.getMember("gg");

            Assert.assertEquals(42, result.asInt());
            Assert.assertEquals(42, global.getMember("value").asInt());

            Assert.assertThrows(UnsupportedOperationException.class, () -> global.putMember("value", 43));
        }
    }

    @Test
    public void mutableGlobals() throws IOException, InterruptedException {
        final ByteSequence globalsBytes = ByteSequence.create(compileWat("mutable-globals", """
                        (module
                          (global (export "global-i32") (mut i32) (i32.const 42))
                          (global (export "global-i64") (mut i64) (i64.const 0x1_ffff_ffff))
                          (global (export "global-f32") (mut f32) (f32.const 3.14))
                          (global (export "global-f64") (mut f64) (f64.const 3.14))
                        )
                        """));

        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Source source = Source.newBuilder(WasmLanguage.ID, globalsBytes, "mutable-globals").build();
            final Value globals = context.eval(source).newInstance().getMember("exports");

            final Value globalI32 = globals.getMember("global-i32");
            Assert.assertEquals(42, globalI32.getMember("value").asInt());
            globalI32.putMember("value", 43);
            Assert.assertEquals(43, globalI32.getMember("value").asInt());

            final Value globalI64 = globals.getMember("global-i64");
            Assert.assertEquals(0x1_ffff_ffffL, globalI64.getMember("value").asLong());
            globalI64.putMember("value", -1L);
            Assert.assertEquals(-1L, globalI64.getMember("value").asLong());

            final Value globalF32 = globals.getMember("global-f32");
            Assert.assertEquals(3.14f, globalF32.getMember("value").asFloat(), 0.0f);
            globalF32.putMember("value", 13.37f);
            Assert.assertEquals(13.37f, globalF32.getMember("value").asFloat(), 0.0f);

            final Value globalF64 = globals.getMember("global-f64");
            Assert.assertEquals(3.14, globalF64.getMember("value").asDouble(), 0.0);
            globalF64.putMember("value", 13.37);
            Assert.assertEquals(13.37, globalF64.getMember("value").asDouble(), 0.0);
        }
    }

    @Test
    public void newInstanceWASI() throws IOException, InterruptedException {
        final ByteSequence mainModuleBytes = ByteSequence.create(compileWat("main", """
                        (module
                          (import "wasi_snapshot_preview1" "fd_write" (func $fd_write (param i32 i32 i32 i32) (result i32)))
                          (memory 1)
                          (export "memory" (memory 0))
                          (func (export "main") (result i32)
                            (i32.const 13)
                          )
                        )
                        """));
        final ByteSequence importModuleBytes = ByteSequence.create(compileWat("test", """
                        (module
                          (func (export "f") (result i32)
                            (i32.const 42)
                          )
                        )
                        """));

        final Source mainModuleSource = Source.newBuilder(WasmLanguage.ID, mainModuleBytes, "main-mod").build();
        final Source importModuleSource = Source.newBuilder(WasmLanguage.ID, importModuleBytes, "import-mod").build();

        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.Builtins", "wasi_snapshot_preview1").build()) {
            final Value mainModule = context.eval(mainModuleSource);
            final Value otherModule = context.eval(importModuleSource);

            final Value mainInstance = mainModule.newInstance();
            final Value mainExports = mainInstance.getMember("exports");

            Assert.assertEquals(13, mainExports.invokeMember("main").asInt());

            final Value otherInstance = otherModule.newInstance();
            final Value otherExports = otherInstance.getMember("exports");
            Assert.assertEquals(42, otherExports.invokeMember("f").asInt());
        }
    }

    @Test
    public void newInstanceWASIWithSupportModule() throws IOException, InterruptedException {
        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.Builtins", "wasi_snapshot_preview1").build()) {
            Value mainModule = context.eval(Source.newBuilder("wasm", ByteSequence.create(compileWat("main", """
                            (module
                                ;; Import WASI function: args_sizes_get(argc_ptr, argv_buf_size_ptr)
                                (import "wasi_snapshot_preview1" "args_sizes_get"
                                (func $args_sizes_get (param i32 i32) (result i32)))

                                ;; Import printf function from support module
                                (import "env" "printf"
                                (func $printf (param i32)))

                                ;; Export memory 0 (used by WASI)
                                (memory $mem 1)
                                (export "memory" (memory $mem))

                                ;; Dummy start function that calls both imported functions
                                (func (export "_start")
                                ;; Write to 0 and 4 memory offsets (pretend we use these as argc/argv)
                                (call $args_sizes_get
                                    (i32.const 0)   ;; argc pointer
                                    (i32.const 4))  ;; argv_buf_size pointer
                                drop

                                ;; Call printf with a dummy i32 pointer, say address 16 (assuming a string is there)
                                (call $printf (i32.const 16))
                                )
                            )
                            """)), "main").build());
            Value envModule = context.eval(Source.newBuilder("wasm", ByteSequence.create(compileWat("env", """
                            (module
                                (func $printf (export "printf") (import "hostEnv" "printf") (param i32))
                            )
                            """)), "env").build());

            AtomicBoolean printfCalled = new AtomicBoolean();
            ProxyExecutable printf = (args) -> {
                Assert.assertEquals(16, args[0].asInt());
                Assert.assertFalse(printfCalled.getAndSet(true));
                return null;
            };
            var hostEnvObject = ProxyObject.fromMap(Map.of("printf", printf));
            Value envInstance = envModule.newInstance(ProxyObject.fromMap(Map.of(
                            "hostEnv", hostEnvObject)));
            Value envExports = envInstance.getMember("exports");
            Value mainInstance = mainModule.newInstance(ProxyObject.fromMap(Map.of(
                            "hostEnv", hostEnvObject,
                            "env", envExports)));
            Value mainExports = mainInstance.getMember("exports");
            mainExports.getMember("_start").execute();
            Assert.assertTrue("printf called", printfCalled.get());
        }
    }

    @Test
    public void indirectImportOfHostFunction() throws IOException, InterruptedException {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            Value mainModule = context.eval(Source.newBuilder("wasm", ByteSequence.create(compileWat("main", """
                            (module
                                ;; Import printf function from support module
                                (import "env" "printf"
                                (func $printf (param i32)))

                                (func (export "_start")
                                    ;; Call printf with a dummy i32 pointer, say address 16
                                    (call $printf (i32.const 16))
                                )
                            )
                            """)), "main").build());
            Value envModule = context.eval(Source.newBuilder("wasm", ByteSequence.create(compileWat("env", """
                            (module
                                (func $printf (export "printf") (import "hostEnv" "printf") (param i32))
                            )
                            """)), "env").build());

            AtomicBoolean printfCalled = new AtomicBoolean();
            ProxyExecutable printf = (args) -> {
                Assert.assertEquals(16, args[0].asInt());
                Assert.assertFalse(printfCalled.getAndSet(true));
                return null;
            };
            var hostEnvObject = ProxyObject.fromMap(Map.of("printf", printf));
            Value envModuleInstance = envModule.newInstance(ProxyObject.fromMap(Map.of(
                            "hostEnv", hostEnvObject)));
            Value envExports = envModuleInstance.getMember("exports");
            Value mainModuleInstance = mainModule.newInstance(ProxyObject.fromMap(Map.of(
                            "hostEnv", hostEnvObject,
                            "env", envExports)));
            mainModuleInstance.getMember("exports").getMember("_start").execute();
            Assert.assertTrue("printf called", printfCalled.get());
        }
    }
}
