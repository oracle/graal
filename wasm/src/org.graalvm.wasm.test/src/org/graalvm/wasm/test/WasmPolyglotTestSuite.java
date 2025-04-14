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
        contextBuilder.option("wasm.EvalReturnsModule", "true");
        Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID,
                        ByteSequence.create(binaryReturnConst),
                        "main");
        Source source = sourceBuilder.build();
        try (Context context = contextBuilder.build()) {
            Value mainFunction = context.eval(source).newInstance().getMember("main");
            Value result = mainFunction.execute();
            Assert.assertEquals("Should be equal: ", 42, result.asInt());
        }
    }

    @Test
    public void unsafeMemoryFreed() throws IOException {
        Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID,
                        ByteSequence.create(binaryReturnConst),
                        "main");
        Source source = sourceBuilder.build();
        contextBuilder.allowExperimentalOptions(true);
        contextBuilder.option("wasm.UseUnsafeMemory", "true");
        // Force use of UnsafeWasmMemory
        contextBuilder.option("wasm.DirectByteBufferMemoryAccess", "true");
        contextBuilder.option("wasm.EvalReturnsModule", "true");
        Context context = contextBuilder.build();
        context.enter();

        final Value mainInstance = context.eval(source).newInstance();
        mainInstance.getMember("main").execute();
        final TruffleLanguage.Env env = WasmContext.get(null).environment();
        final Value memoryValue = mainInstance.getMember("memory");
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
        Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID,
                        ByteSequence.create(binaryReturnConst),
                        "main");
        Source source = sourceBuilder.build();
        contextBuilder.allowExperimentalOptions(true);
        contextBuilder.option("wasm.UseUnsafeMemory", "true");
        contextBuilder.option("wasm.DirectByteBufferMemoryAccess", "false");
        contextBuilder.option("wasm.EvalReturnsModule", "true");
        Context context = contextBuilder.build();
        context.enter();
        final Value mainInstance = context.eval(source).newInstance();
        mainInstance.getMember("main").execute();
        final TruffleLanguage.Env env = WasmContext.get(null).environment();
        final Value memoryValue = mainInstance.getMember("memory");
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
        contextBuilder.option("wasm.EvalReturnsModule", "true");
        Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, test, "main");
        Source source = sourceBuilder.build();
        try (Context context = contextBuilder.build()) {
            Value mainFunction = context.eval(source).newInstance().getMember("main");
            Value result = mainFunction.execute();
            Assert.assertEquals("Should be equal: ", 11, result.asInt());
        }
    }

    @Test
    public void divisionByZeroStressTest() throws IOException, InterruptedException {
        String divisionByZeroWAT = "(module (func (export \"main\") (result i32) i32.const 1 i32.const 0 i32.div_s))";
        ByteSequence test = ByteSequence.create(compileWat("test", divisionByZeroWAT));
        Source source = Source.newBuilder(WasmLanguage.ID, test, "main").build();
        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.EvalReturnsModule", "true").build()) {
            Value mainFunction = context.eval(source).newInstance().getMember("main");

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
        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.EvalReturnsModule", "true").build()) {
            Value instance = context.eval(source).newInstance();
            Set<String> keys = instance.getMemberKeys();
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
        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.EvalReturnsModule", "true").build()) {
            Value mainFunction = context.eval(source).newInstance().getMember("main");
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

    @Test
    public void instantiateModuleWithImportObject() throws IOException, InterruptedException {
        final ByteSequence simpleImportModuleData = ByteSequence.create(compileWat("test", simpleImportModule));

        final Source simpleImportModuleSource = Source.newBuilder(WasmLanguage.ID, simpleImportModuleData, "test").build();

        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.EvalReturnsModule", "true").build()) {
            final Proxy executable = (ProxyExecutable) args -> 42;
            final Proxy function = ProxyObject.fromMap(Map.of("main", executable));
            final Proxy importObject = ProxyObject.fromMap(Map.of("main", function));

            final Value simpleImportModule = context.eval(simpleImportModuleSource);

            final Value instance = simpleImportModule.newInstance(importObject);

            final Value result = instance.getMember("test").execute();

            Assert.assertEquals(42, result.asInt());
        }
    }

    @Test
    public void instantiateModuleWithMissingImportObject() throws IOException, InterruptedException {
        final ByteSequence simpleImportModuleData = ByteSequence.create(compileWat("test", simpleImportModule));

        final Source simpleImportModuleSource = Source.newBuilder(WasmLanguage.ID, simpleImportModuleData, "test").build();

        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.EvalReturnsModule", "true").build()) {
            final Value simpleImportModule = context.eval(simpleImportModuleSource);

            try {
                simpleImportModule.newInstance();
                Assert.fail("Should have failed because of incorrect import");
            } catch (PolyglotException e) {
                Assert.assertFalse(e.isExit());
                Assert.assertTrue(e.getMessage().contains("Module requires imports"));
            }
        }
    }

    @Test
    public void instantiateModuleWithMissingFunction() throws IOException, InterruptedException {
        final ByteSequence simpleImportModuleData = ByteSequence.create(compileWat("test", simpleImportModule));

        final Source simpleImportModuleSource = Source.newBuilder(WasmLanguage.ID, simpleImportModuleData, "test").build();

        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.EvalReturnsModule", "true").build()) {
            final Proxy importObject = ProxyObject.fromMap(Map.of("main", ProxyObject.fromMap(Collections.emptyMap())));

            final Value simpleImportModule = context.eval(simpleImportModuleSource);

            try {
                simpleImportModule.newInstance(importObject);
                Assert.fail("Should have failed because of incorrect import");
            } catch (PolyglotException e) {
                Assert.assertFalse(e.isExit());
                Assert.assertTrue(e.getMessage().contains("does not contain \"main\""));
            }
        }
    }

    @Test
    public void instantiateModuleWithNonExecutableFunction() throws IOException, InterruptedException {
        final ByteSequence simpleImportModuleData = ByteSequence.create(compileWat("test", simpleImportModule));

        final Source simpleImportModuleSource = Source.newBuilder(WasmLanguage.ID, simpleImportModuleData, "test").build();

        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.EvalReturnsModule", "true").build()) {
            final Proxy importObject = ProxyObject.fromMap(Map.of("main", ProxyObject.fromMap(Map.of("main", 5))));

            final Value simpleImportModule = context.eval(simpleImportModuleSource);

            try {
                simpleImportModule.newInstance(importObject);
                Assert.fail("Should have failed because of incorrect import");
            } catch (PolyglotException e) {
                Assert.assertFalse(e.isExit());
                Assert.assertTrue(e.getMessage().contains("is not callable"));
            }
        }
    }

    @Test
    public void instantiateInSameStore() throws IOException, InterruptedException {
        final ByteSequence simpleTestModuleData = ByteSequence.create(compileWat("main", simpleTestModule));
        final ByteSequence simpleImportModuleData = ByteSequence.create(compileWat("test", simpleImportModule));

        final Source simpleTestModuleSource = Source.newBuilder(WasmLanguage.ID, simpleTestModuleData, "main").build();
        final Source simpleImportModuleSource = Source.newBuilder(WasmLanguage.ID, simpleImportModuleData, "test").build();

        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.EvalReturnsModule", "true").build()) {
            final Value wasm = context.getBindings(WasmLanguage.ID);

            final Value store = wasm.invokeMember("newStore");

            final Value simpleTestModule = context.eval(simpleTestModuleSource);
            final Value simpleImportModule = context.eval(simpleImportModuleSource);

            store.invokeMember("newInstances", simpleTestModule, simpleImportModule);

            final Value importInstance = store.getMember("test");
            final Value result = importInstance.getMember("test").execute();

            Assert.assertEquals(13, result.asInt());
        }
    }

    @Test
    public void lateInstantiateInSameStore() throws IOException, InterruptedException {
        final ByteSequence simpleTestModuleData = ByteSequence.create(compileWat("main", simpleTestModule));
        final ByteSequence simpleImportModuleData = ByteSequence.create(compileWat("test", simpleImportModule));

        final Source simpleTestModuleSource = Source.newBuilder(WasmLanguage.ID, simpleTestModuleData, "main").build();
        final Source simpleImportModuleSource = Source.newBuilder(WasmLanguage.ID, simpleImportModuleData, "test").build();

        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.EvalReturnsModule", "true").build()) {
            final Value wasm = context.getBindings(WasmLanguage.ID);

            final Value store = wasm.invokeMember("newStore");

            final Value simpleTestModule = context.eval(simpleTestModuleSource);
            final Value simpleImportModule = context.eval(simpleImportModuleSource);

            store.invokeMember("newInstances", simpleTestModule);
            store.invokeMember("newInstances", simpleImportModule);

            final Value importInstance = store.getMember("test");
            final Value result = importInstance.getMember("test").execute();

            Assert.assertEquals(13, result.asInt());
        }
    }

    @Test
    public void consistentGetStore() throws IOException, InterruptedException {
        final ByteSequence simpleTestModuleData = ByteSequence.create(compileWat("main", simpleTestModule));
        final ByteSequence simpleImportModuleData = ByteSequence.create(compileWat("test", simpleImportModule));

        final Source simpleTestModuleSource = Source.newBuilder(WasmLanguage.ID, simpleTestModuleData, "main").build();
        final Source simpleImportModuleSource = Source.newBuilder(WasmLanguage.ID, simpleImportModuleData, "test").build();

        try (Context context = Context.newBuilder(WasmLanguage.ID).option("wasm.EvalReturnsModule", "true").build()) {
            final Value wasm = context.getBindings(WasmLanguage.ID);

            final Value simpleTestModule = context.eval(simpleTestModuleSource);

            final Value instance1 = simpleTestModule.newInstance();
            final Value instance2 = simpleTestModule.newInstance();

            final Value store1 = wasm.invokeMember("getStore", instance1);
            final Value store2 = wasm.invokeMember("getStore", instance1);

            Assert.assertEquals(store1, store2);

            final Value store3 = wasm.invokeMember("getStore", instance1);
            final Value store4 = wasm.invokeMember("getStore", instance2);

            Assert.assertNotEquals(store3, store4);

            final Value simpleImportModule = context.eval(simpleImportModuleSource);

            final Value store5 = wasm.invokeMember("newStore");
            store5.invokeMember("newInstances", simpleImportModule, simpleTestModule);

            final Value instance3 = store5.getMember("main");
            final Value instance4 = store5.getMember("test");

            final Value store6 = wasm.invokeMember("getStore", instance3);
            final Value store7 = wasm.invokeMember("getStore", instance4);

            Assert.assertEquals(store5, store6);
            Assert.assertEquals(store5, store7);
            Assert.assertEquals(store6, store7);
        }
    }
}
