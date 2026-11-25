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
package org.graalvm.wasm.test.suites.bytecode;

import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.api.Dictionary;
import org.graalvm.wasm.api.Executable;
import org.graalvm.wasm.api.WebAssembly;
import org.graalvm.wasm.memory.WasmMemoryLibrary;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;

public class InstanceCodeSharingSuite {
    private static final WasmMemoryLibrary MEMORY = WasmMemoryLibrary.getUncached();

    private static void withModule(byte[] binary, BiConsumer<WebAssembly, WasmModule> test) {
        withModule(binary, Context.newBuilder("wasm"), test);
    }

    private static void withModule(byte[] binary, Context.Builder builder, BiConsumer<WebAssembly, WasmModule> test) {
        try (Context context = builder.build()) {
            context.initialize("wasm");
            context.enter();
            try {
                assertFalse(WasmContext.get(null).language().isMultiContext());
                WebAssembly api = new WebAssembly(WasmContext.get(null));
                test.accept(api, api.moduleDecode(binary));
            } finally {
                context.leave();
            }
        }
    }

    private static Value exports(WasmInstance instance) {
        return Value.asValue(instance).getMember("exports");
    }

    @Test
    public void reusesTargetsAfterSecondInstantiation() throws IOException, InterruptedException {
        byte[] binary = compileWat("shared-targets", "(module (func (export \"value\") (result i32) i32.const 42))");
        withModule(binary, (api, module) -> {
            WasmInstance first = api.moduleInstantiate(module, WasmConstant.NULL);
            CallTarget firstTarget = first.target(0);
            assertEquals(42, exports(first).getMember("value").execute().asInt());
            WasmInstance second = api.moduleInstantiate(module, WasmConstant.NULL);
            assertNotSame(firstTarget, second.target(0));
            for (int i = 0; i < 20; i++) {
                WasmInstance next = api.moduleInstantiate(module, WasmConstant.NULL);
                assertSame("New instances must reuse the shared target", second.target(0), next.target(0));
                assertEquals(42, exports(next).getMember("value").execute().asInt());
                assertNotSame(second.functionInstance(module.function(0)), next.functionInstance(module.function(0)));
            }
            assertSame(firstTarget, first.target(0));
            assertEquals(42, exports(first).getMember("value").execute().asInt());
        });
    }

    @Test
    public void globalsMemoriesAndTablesStayIsolated() throws IOException, InterruptedException {
        byte[] binary = compileWat("instance-state", """
                        (module
                          (type $t (func (result i32)))
                          (memory 1)
                          (global $state (mut i32) (i32.const 0))
                          (table 1 funcref)
                          (func $get (type $t) (result i32) global.get $state)
                          (elem (i32.const 0) $get)
                          (func (export "increment") (param i32) (result i32)
                            global.get $state local.get 0 i32.add global.set $state
                            i32.const 0 global.get $state i32.store
                            call $get)
                          (func (export "indirect") (result i32) i32.const 0 call_indirect (type $t))
                          (func (export "clear") i32.const 0 ref.null func table.set)
                          (func (export "write") (param i32 i32) local.get 0 local.get 1 i32.store))
                        """);
        withModule(binary, (api, module) -> {
            List<WasmInstance> instances = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                WasmInstance instance = api.moduleInstantiate(module, WasmConstant.NULL);
                instances.add(instance);
                assertEquals(i + 1, exports(instance).getMember("increment").execute(i + 1).asInt());
                assertEquals(i + 1, exports(instance).getMember("indirect").execute().asInt());
                assertEquals(i + 1, MEMORY.load_i32(instance.memory(0), null, 0));
            }
            for (int i = 0; i < instances.size(); i++) {
                assertEquals(i + 1, exports(instances.get(i)).getMember("indirect").execute().asInt());
            }
            WasmInstance second = instances.get(1);
            exports(second).getMember("clear").executeVoid();
            assertThrows(org.graalvm.polyglot.PolyglotException.class, () -> exports(second).getMember("indirect").execute());
            assertEquals(3, exports(instances.get(2)).getMember("indirect").execute().asInt());
            assertEquals(1, MEMORY.grow(second.memory(0), 1));
            exports(second).getMember("write").executeVoid(65536, 99);
            assertEquals(99, MEMORY.load_i32(second.memory(0), null, 65536));
            assertThrows(org.graalvm.polyglot.PolyglotException.class, () -> exports(instances.get(2)).getMember("write").execute(65536, 99));
        });
    }

    @Test
    public void importsAndStartFunctionsUseEachInstance() throws IOException, InterruptedException {
        byte[] binary = compileWat("instance-imports", """
                        (module
                          (import "env" "host" (func $host (result i32)))
                          (import "env" "seed" (global $seed i32))
                          (global $state (mut i32) (i32.const 0))
                          (func $start call $host global.set $state)
                          (start $start)
                          (func (export "value") (result i32) global.get $state global.get $seed i32.add)
                          (func (export "call") (result i32) call $host))
                        """);
        withModule(binary, (api, module) -> {
            List<WasmInstance> instances = new ArrayList<>();
            int[] starts = {0};
            for (int i = 0; i < 8; i++) {
                int value = 100 + i;
                Dictionary env = new Dictionary();
                env.addMember("seed", i);
                env.addMember("host", new Executable(args -> {
                    starts[0]++;
                    return value;
                }));
                Dictionary imports = new Dictionary();
                imports.addMember("env", env);
                instances.add(api.moduleInstantiate(module, imports));
            }
            assertEquals(8, starts[0]);
            for (int i = 0; i < instances.size(); i++) {
                assertEquals(100 + i * 2, exports(instances.get(i)).getMember("value").execute().asInt());
                assertEquals(100 + i, exports(instances.get(i)).getMember("call").execute().asInt());
            }
            assertEquals(16, starts[0]);
        });
    }

    @Test
    public void passiveSegmentsAreDroppedPerInstance() throws IOException, InterruptedException {
        byte[] binary = compileWat("passive-state", """
                        (module
                          (type $t (func (result i32)))
                          (memory 1)
                          (table 1 funcref)
                          (func $f (type $t) (result i32) i32.const 42)
                          (data $d "z")
                          (elem $e func $f)
                          (func (export "data") i32.const 0 i32.const 0 i32.const 1 memory.init $d)
                          (func (export "dropData") data.drop $d)
                          (func (export "elem") i32.const 0 i32.const 0 i32.const 1 table.init $e)
                          (func (export "dropElem") elem.drop $e)
                          (func (export "value") (result i32) i32.const 0 call_indirect (type $t)))
                        """);
        withModule(binary, (api, module) -> {
            WasmInstance first = api.moduleInstantiate(module, WasmConstant.NULL);
            WasmInstance second = api.moduleInstantiate(module, WasmConstant.NULL);
            WasmInstance third = api.moduleInstantiate(module, WasmConstant.NULL);
            exports(second).getMember("dropData").executeVoid();
            exports(second).getMember("dropElem").executeVoid();
            assertThrows(org.graalvm.polyglot.PolyglotException.class, () -> exports(second).getMember("data").execute());
            assertThrows(org.graalvm.polyglot.PolyglotException.class, () -> exports(second).getMember("elem").execute());
            for (WasmInstance instance : List.of(first, third)) {
                exports(instance).getMember("data").executeVoid();
                exports(instance).getMember("elem").executeVoid();
                assertEquals('z', MEMORY.load_i32_8u(instance.memory(0), null, 0));
                assertEquals(42, exports(instance).getMember("value").execute().asInt());
            }
        });
    }

    @Test
    public void compiledSharedTargetsReadDifferentImportedGlobals() throws IOException, InterruptedException {
        Assume.assumeTrue("Optimizing runtime is unavailable", Engine.supportsCompilation());
        byte[] binary = compileWat("compiled-instance", """
                        (module
                          (import "env" "seed" (global i32))
                          (func (export "value") (result i32) global.get 0))
                        """);
        Context.Builder builder = Context.newBuilder("wasm").allowExperimentalOptions(true).option("engine.BackgroundCompilation", "false").option("engine.CompilationFailureAction", "Throw").option(
                        "engine.CompileImmediately", "true");
        withModule(binary, builder, (api, module) -> {
            CallTarget shared = null;
            for (int i = 0; i < 10; i++) {
                Dictionary env = new Dictionary();
                env.addMember("seed", 100 + i);
                Dictionary imports = new Dictionary();
                imports.addMember("env", env);
                WasmInstance instance = api.moduleInstantiate(module, imports);
                if (i == 1) {
                    shared = instance.target(0);
                } else if (i > 1) {
                    assertSame(shared, instance.target(0));
                }
                assertEquals(100 + i, exports(instance).getMember("value").execute().asInt());
            }
        });
    }

    @Test
    public void failedInstantiationDoesNotPoisonCachedCode() throws IOException, InterruptedException {
        byte[] binary = compileWat("failed-instance", """
                        (module
                          (import "env" "seed" (global $seed i32))
                          (func $start global.get $seed i32.eqz if unreachable end)
                          (start $start)
                          (func (export "value") (result i32) global.get $seed))
                        """);
        withModule(binary, (api, module) -> {
            assertThrows(RuntimeException.class, () -> api.moduleInstantiate(module, WasmConstant.NULL));
            Dictionary env = new Dictionary();
            env.addMember("seed", 0);
            Dictionary imports = new Dictionary();
            imports.addMember("env", env);
            assertThrows(RuntimeException.class, () -> api.moduleInstantiate(module, imports));
            for (int i = 1; i < 5; i++) {
                env.addMember("seed", i);
                WasmInstance instance = api.moduleInstantiate(module, imports);
                assertEquals(i, exports(instance).getMember("value").execute().asInt());
            }
        });
    }

    @Test
    public void importedOnlyAndEmptyModulesCanBeReused() throws IOException, InterruptedException {
        byte[] imported = compileWat("imported-only", """
                        (module (import "env" "host" (func $host (result i32)))
                          (export "value" (func $host)))
                        """);
        withModule(imported, (api, module) -> {
            for (int i = 0; i < 8; i++) {
                int result = i;
                Dictionary env = new Dictionary();
                env.addMember("host", new Executable(args -> result));
                Dictionary imports = new Dictionary();
                imports.addMember("env", env);
                assertEquals(i, exports(api.moduleInstantiate(module, imports)).getMember("value").execute().asInt());
            }
        });
        withModule(compileWat("empty", "(module)"), (api, module) -> {
            for (int i = 0; i < 8; i++) {
                api.moduleInstantiate(module, WasmConstant.NULL);
            }
        });
    }

    private static WasmModule decode(Context context, byte[] binary) {
        context.initialize("wasm");
        context.enter();
        try {
            return new WebAssembly(WasmContext.get(null)).moduleDecode(binary);
        } finally {
            context.leave();
        }
    }

    private static WasmInstance instantiateAndCall(Context context, WasmModule module) {
        context.enter();
        try {
            assertTrue(WasmContext.get(null).language().isMultiContext());
            WasmInstance instance = new WebAssembly(WasmContext.get(null)).moduleInstantiate(module, WasmConstant.NULL);
            assertEquals(42, exports(instance).getMember("value").execute().asInt());
            return instance;
        } finally {
            context.leave();
        }
    }

    @Test
    public void multiContextModeSharesFromFirstInstance() throws IOException, InterruptedException {
        byte[] binary = compileWat("cross-context", """
                        (module (func (export "value") (result i32) i32.const 42))
                        """);
        try (Engine engine = Engine.create();
                        Context first = Context.newBuilder("wasm").engine(engine).build();
                        Context second = Context.newBuilder("wasm").engine(engine).build()) {
            first.initialize("wasm");
            second.initialize("wasm");
            WasmModule one = decode(first, binary);
            WasmModule two = decode(second, binary);
            assertSame(one, two);
            WasmInstance firstInstance = instantiateAndCall(first, one);
            WasmInstance secondInstance = instantiateAndCall(second, two);
            assertSame(firstInstance.target(0), secondInstance.target(0));
        }
    }

    @Test
    public void concurrentInstantiationPublishesCompleteTargets() throws Exception {
        byte[] binary = compileWat("concurrent-module", """
                        (module
                          (func $helper (result i32) i32.const 42)
                          (func (export "value") (result i32) call $helper))
                        """);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Engine engine = Engine.create();
                        Context first = Context.newBuilder("wasm").engine(engine).build();
                        Context second = Context.newBuilder("wasm").engine(engine).build()) {
            first.initialize("wasm");
            second.initialize("wasm");
            WasmModule one = decode(first, binary);
            WasmModule two = decode(second, binary);
            assertSame(one, two);
            CountDownLatch start = new CountDownLatch(1);
            Future<WasmInstance> left = executor.submit(() -> {
                start.await();
                return instantiateAndCall(first, one);
            });
            Future<WasmInstance> right = executor.submit(() -> {
                start.await();
                return instantiateAndCall(second, two);
            });
            start.countDown();
            WasmInstance a = left.get(30, TimeUnit.SECONDS);
            WasmInstance b = right.get(30, TimeUnit.SECONDS);
            assertSame(a.target(0), b.target(0));
            assertSame(a.target(1), b.target(1));
        } finally {
            executor.shutdownNow();
        }
    }

}
