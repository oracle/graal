/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.junit.Assert;
import org.junit.Test;

public class WasmOSRSuite {
    private static final int N_CONTEXTS = 2;

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
                    Value mainMod = context.eval(sourceMain);
                    Value mainFun = mainMod.getMember("_main");
                    Assert.assertEquals(0, mainFun.execute().asInt());
                }
            }
        }
    }
}
