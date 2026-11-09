/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.EnumSet;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.utils.WasmBinaryTools;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class WasmOSRSuite {
    private static final int N_CONTEXTS = 2;
    private static final int OSR_TRIGGER_ITERATIONS = 1024;

    @Test
    public void testOSR() throws IOException, InterruptedException {
        final ByteSequence binaryMain = ByteSequence.create(compileWat("main", """
                        (module
                            (type (;0;) (func (result i32)))
                            (import "wasi_snapshot_preview1" "sched_yield" (func $__wasi_sched_yield (type 0)))
                            (memory (;0;) 4)
                            (export "memory" (memory 0))
                            (func (export "_main") (type 0)
                                (local $i i32)
                                i32.const 1000
                                local.set $i
                                block
                                    loop
                                        local.get $i
                                        i32.const 1
                                        i32.sub
                                        local.tee $i
                                        call $__wasi_sched_yield
                                        drop
                                        i32.eqz
                                        br_if 1
                                        br 0
                                    end
                                end
                                i32.const 0
                            )
                        )
                        """));
        final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "main").build();
        var eb = Engine.newBuilder().allowExperimentalOptions(true);
        eb.option("wasm.Builtins", "wasi_snapshot_preview1");
        eb.option("engine.OSRCompilationThreshold", "100");
        eb.option("engine.BackgroundCompilation", "false");
        try (Engine engine = eb.build()) {
            for (int i = 0; i < N_CONTEXTS; i++) {
                try (Context context = Context.newBuilder(WasmLanguage.ID).engine(engine).build()) {
                    Value mainExports = context.eval(sourceMain).newInstance().getMember("exports");
                    Value mainFun = mainExports.getMember("_main");
                    Assert.assertEquals(0, mainFun.execute().asInt());
                }
            }
        }
    }

    @Test
    public void testTailCallLoopOSR() throws IOException, InterruptedException {
        final ByteSequence binary = ByteSequence.create(compileWat("main", """
                        (module
                            (func $loop (param $i i32) (result i32)
                                local.get $i
                                i32.eqz
                                if (result i32)
                                    i32.const 42
                                else
                                    local.get $i
                                    i32.const 1
                                    i32.sub
                                    return_call $loop
                                end
                            )
                            (func (export "_main") (result i32)
                                i32.const 2048
                                return_call $loop
                            )
                        )
                        """, EnumSet.of(WasmBinaryTools.WabtOption.TAIL_CALLS)));
        final Source source = Source.newBuilder(WasmLanguage.ID, binary, "main").build();

        var engineBuilder = Engine.newBuilder().allowExperimentalOptions(true);
        engineBuilder.option("wasm.TailCalls", "true");
        engineBuilder.option("wasm.TailCallLoops", "true");
        engineBuilder.option("engine.OSR", "true");
        engineBuilder.option("engine.OSRCompilationThreshold", "1");
        engineBuilder.option("engine.FirstTierCompilationThreshold", "1000000");
        engineBuilder.option("engine.LastTierCompilationThreshold", "1000000");
        engineBuilder.option("engine.BackgroundCompilation", "false");

        try (Engine engine = engineBuilder.build();
                        Context context = Context.newBuilder(WasmLanguage.ID).engine(engine).build()) {
            Value exports = context.eval(source).newInstance().getMember("exports");
            Assert.assertEquals(42, exports.getMember("_main").execute().asInt());
        }
    }

    @Test
    public void testExceptionAfterOSRIsNotCaughtAtLoopOffset() throws IOException, InterruptedException {
        final ByteSequence binary = ByteSequence.create(compileWat("main", """
                        (module
                            (type $t0 (func))
                            (tag $e0 (type $t0))
                            (func (export "_main") (param $iterations i32) (result i32)
                                (local $i i32)
                                (block $handler
                                    (try_table (catch_all $handler)
                                        (loop $loop
                                            local.get $i
                                            i32.const 1
                                            i32.add
                                            local.tee $i
                                            local.get $iterations
                                            i32.lt_u
                                            br_if $loop
                                        )
                                    )
                                    throw $e0
                                )
                                i32.const 42
                            )
                        )
                        """, EnumSet.of(WasmBinaryTools.WabtOption.EXCEPTIONS)));
        final Source source = Source.newBuilder(WasmLanguage.ID, binary, "main").build();

        var engineBuilder = Engine.newBuilder().allowExperimentalOptions(true);
        engineBuilder.option("wasm.Exceptions", "true");
        engineBuilder.option("engine.OSR", "true");
        engineBuilder.option("engine.OSRCompilationThreshold", "1");
        engineBuilder.option("engine.FirstTierCompilationThreshold", "1000000");
        engineBuilder.option("engine.LastTierCompilationThreshold", "1000000");
        engineBuilder.option("engine.BackgroundCompilation", "false");

        try (Engine engine = engineBuilder.build();
                        Context context = Context.newBuilder(WasmLanguage.ID).engine(engine).build()) {
            Value exports = context.eval(source).newInstance().getMember("exports");
            PolyglotException exception = Assert.assertThrows(PolyglotException.class,
                            () -> exports.getMember("_main").execute(OSR_TRIGGER_ITERATIONS));
            Assert.assertTrue(exception.isGuestException());
        }
    }

    /**
     * GR-81497: OSR into an inner loop can require irreducible-loop reconstruction. The operand
     * retained below the inner loop gives its header a different stack height from the outer
     * header, even though each bytecode offset has a consistent stack height.
     */
    @Test
    public void testOSRInNestedLoopWithDifferentStackHeights() throws IOException, InterruptedException {
        runNestedLoopOSR(true);
    }

    @Test
    public void testOSRInNestedLoopWithSameStackHeight() throws IOException, InterruptedException {
        runNestedLoopOSR(false);
    }

    private static void runNestedLoopOSR(boolean extraOperand) throws IOException, InterruptedException {
        Assume.assumeTrue("Optimizing runtime is not available", Engine.supportsCompilation());
        final ByteSequence binary = ByteSequence.create(compileWat("nested-loop-osr", """
                        (module
                            (func (export "run") (result i32)
                                (local $remaining i32) (local $i i32) (local $sum i32)
                                i32.const 2048
                                local.set $remaining
                                (loop $outer
                                    local.get $remaining
                                    i32.const 1
                                    i32.sub
                                    local.set $remaining
                                    (block $skip_inner
                                        ;; The outer loop can bypass the inner loop.
                                        local.get $remaining
                                        i32.const 1
                                        i32.and
                                        br_if $skip_inner
                                        %s
                                        i32.const 2
                                        local.set $i
                                        (loop $inner
                                            local.get $sum
                                            i32.const 1
                                            i32.add
                                            local.set $sum
                                            local.get $i
                                            i32.const 1
                                            i32.sub
                                            local.set $i
                                            ;; Exit directly to the outer header or fall through.
                                            local.get $remaining
                                            i32.const 2
                                            i32.and
                                            if
                                                local.get $i
                                                i32.eqz
                                                br_if $outer
                                            end
                                            local.get $i
                                            br_if $inner
                                        )
                                        %s
                                    )
                                    local.get $remaining
                                    br_if $outer
                                )
                                local.get $sum
                            )
                        )
                        """.formatted(extraOperand ? "i32.const 42" : "", extraOperand ? "drop" : "")));
        final Source source = Source.newBuilder(WasmLanguage.ID, binary, "nested-loop-osr").build();
        var engineBuilder = Engine.newBuilder().allowExperimentalOptions(true);
        engineBuilder.option("engine.Compilation", "true");
        engineBuilder.option("engine.OSR", "true");
        engineBuilder.option("engine.MultiTier", "false");
        // The first OSR poll after 1024 loop iterations targets the inner loop. Keep ordinary
        // compilation thresholds high so only the OSR compilation is exercised.
        engineBuilder.option("engine.OSRCompilationThreshold", "1");
        engineBuilder.option("engine.FirstTierCompilationThreshold", "1000000");
        engineBuilder.option("engine.LastTierCompilationThreshold", "1000000");
        engineBuilder.option("engine.BackgroundCompilation", "false");
        engineBuilder.option("engine.CompilationFailureAction", "Throw");
        try (Engine engine = engineBuilder.build(); Context context = Context.newBuilder(WasmLanguage.ID).engine(engine).build()) {
            Value exports = context.eval(source).newInstance().getMember("exports");
            Assert.assertEquals(2048, exports.getMember("run").execute().asInt());
        }
    }
}
