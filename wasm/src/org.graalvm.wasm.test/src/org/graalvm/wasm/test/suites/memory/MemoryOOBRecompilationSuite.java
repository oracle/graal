/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.suites.memory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.memory.ByteArrayWasmMemory;
import org.graalvm.wasm.memory.NativeWasmMemory;
import org.graalvm.wasm.memory.UnsafeWasmMemory;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;

public class MemoryOOBRecompilationSuite extends AbstractBinarySuite {

    private static byte[] createModule(int initPages, int maxPages) {
        final BinaryBuilder builder = newBuilder();
        builder.addType(new int[]{WasmType.I32_TYPE}, new int[]{WasmType.I32_TYPE});
        builder.addMemory(initPages, maxPages);
        // Function 0: oobFunc (i32) -> i32: local.get 0, i32.load 0 0, end
        builder.addFunction(0, EMPTY_INTS, "20 00 28 00 00 0B");
        // Function 1: inBoundsFunc (i32) -> i32: local.get 0, i32.load 0 0, end
        builder.addFunction(0, EMPTY_INTS, "20 00 28 00 00 0B");
        builder.addFunctionExport(0, "oobFunc");
        builder.addFunctionExport(1, "inBoundsFunc");
        builder.addMemoryExport(0, "memory");
        // Active data segment for memory 0 at offset 0: 4 bytes holding value 42 (0x2A)
        builder.addData("00 41 00 0B 04 2A 00 00 00");
        return builder.build();
    }

    private static void testMemoryOOBRecompilation(byte[] binary, Class<? extends WasmMemory> expectedMemoryClass, Consumer<Context.Builder> options) throws IOException {
        final ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        final Context.Builder builder = Context.newBuilder(WasmLanguage.ID);
        builder.allowExperimentalOptions(true);
        builder.err(errStream);

        if (options != null) {
            options.accept(builder);
        }

        boolean isOptimizing = false;
        try (Engine tempEngine = Engine.create()) {
            isOptimizing = !tempEngine.getImplementationName().equals("Interpreted");
        }
        if (isOptimizing) {
            builder.option("engine.Compilation", "true");
            builder.option("engine.BackgroundCompilation", "false");
            builder.option("engine.CompileImmediately", "true");
            builder.option("engine.CompilationFailureAction", "Throw");
            builder.option("engine.TraceCompilation", "true");
        }

        try (Context context = builder.build()) {
            final Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binary), "main").build();
            final Value exports = context.eval(source).newInstance().getMember("exports");
            final Value oobFunc = exports.getMember("oobFunc");
            final Value inBoundsFunc = exports.getMember("inBoundsFunc");
            final Value memoryValue = exports.getMember("memory");

            // 1. Initial in-bounds execution: verify correct functional results
            Assert.assertEquals(42, oobFunc.execute(0).asInt());
            Assert.assertEquals(42, inBoundsFunc.execute(0).asInt());

            // Verify memory type and initial unvisited profile state (if profiling is enabled)
            context.enter();
            try {
                final TruffleLanguage.Env env = WasmContext.get(null).environment();
                final WasmMemory memory = (WasmMemory) env.asGuestValue(memoryValue);
                Assert.assertEquals(expectedMemoryClass, memory.getClass());
                if (com.oracle.truffle.api.Truffle.getRuntime().isProfilingEnabled()) {
                    Assert.assertFalse("Profile should initially be unvisited", memory.isOutOfBoundsProfileVisited());
                }
            } finally {
                context.leave();
            }

            // 2. Repeated caught OOB memory accesses
            // Truffle's DeoptCycleDetectionThreshold bails out after ~15 invalidations.
            // Executing 30 iterations tests well past the cycle detection threshold.
            final int oobAddress = 70_000; // 1 page is 65536 bytes
            for (int i = 0; i < 30; i++) {
                try {
                    oobFunc.execute(oobAddress);
                    Assert.fail("Expected out-of-bounds memory access trap");
                } catch (PolyglotException e) {
                    Assert.assertTrue("Trap must be out-of-bounds memory access: " + e.getMessage(),
                                    e.getMessage().contains("out-of-bounds"));
                }
            }

            // Verify profile is now visited (if profiling is enabled)
            context.enter();
            try {
                final TruffleLanguage.Env env = WasmContext.get(null).environment();
                final WasmMemory memory = (WasmMemory) env.asGuestValue(memoryValue);
                if (com.oracle.truffle.api.Truffle.getRuntime().isProfilingEnabled()) {
                    Assert.assertTrue("Profile should be visited after OOB trap", memory.isOutOfBoundsProfileVisited());
                }
            } finally {
                context.leave();
            }

            // 3. Confirm affected function remains eligible and executes in-bounds correctly
            Assert.assertEquals(42, oobFunc.execute(0).asInt());

            // 4. Confirm other export sharing same signature executes in-bounds correctly
            Assert.assertEquals(42, inBoundsFunc.execute(0).asInt());

            // 5. Confirm no deoptimization cycle bailout occurred
            final String errOutput = errStream.toString();
            Assert.assertFalse("Deopt cycle should not occur: " + errOutput,
                            errOutput.contains("Deopt taken too many times"));
        }
    }

    @Test
    public void testDefaultByteArrayMemory() throws IOException {
        final byte[] binary = createModule(1, 1);
        testMemoryOOBRecompilation(binary, ByteArrayWasmMemory.class, null);
    }

    @Test
    public void testUnsafeMemory() throws IOException {
        final byte[] binary = createModule(1, 1);
        testMemoryOOBRecompilation(binary, UnsafeWasmMemory.class, builder -> {
            builder.option("wasm.UseUnsafeMemory", "true");
            builder.option("wasm.DirectByteBufferMemoryAccess", "true");
        });
    }

    @Test
    public void testNativeMemory() throws IOException {
        final byte[] binary = createModule(1, 32768);
        testMemoryOOBRecompilation(binary, NativeWasmMemory.class, builder -> {
            builder.option("wasm.UseUnsafeMemory", "true");
        });
    }
}
