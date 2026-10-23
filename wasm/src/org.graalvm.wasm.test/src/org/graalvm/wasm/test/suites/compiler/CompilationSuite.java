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
package org.graalvm.wasm.test.suites.compiler;

import static org.graalvm.wasm.test.WasmTestUtils.hexStringToByteArray;
import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;

import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.vector.Vector128Ops;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class CompilationSuite {

    @Test
    public void fallbackWideningLoadsCompile() throws IOException, InterruptedException {
        Assume.assumeTrue("SIMD fallback implementation is not used", Vector128Ops.usesFallbackImplementation());
        Assume.assumeTrue("Optimizing runtime is not available", Engine.supportsCompilation());
        byte[] binary = compileWat("widening-loads", """
                        (module
                          (memory 1)
                          (data (i32.const 0) "\\80\\7f\\ff\\00\\34\\12\\00\\80")
                          (func (export "load8x8_s") (result i32)
                            (i16x8.extract_lane_s 0 (v128.load8x8_s (i32.const 0))))
                          (func (export "load8x8_u") (result i32)
                            (i16x8.extract_lane_u 0 (v128.load8x8_u (i32.const 0))))
                          (func (export "load16x4_s") (result i32)
                            (i32x4.extract_lane 3 (v128.load16x4_s (i32.const 0))))
                          (func (export "load16x4_u") (result i32)
                            (i32x4.extract_lane 3 (v128.load16x4_u (i32.const 0))))
                          (func (export "load32x2_s") (result i64)
                            (i64x2.extract_lane 1 (v128.load32x2_s (i32.const 0))))
                          (func (export "load32x2_u") (result i64)
                            (i64x2.extract_lane 1 (v128.load32x2_u (i32.const 0)))))
                        """);
        Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binary), "widening-loads").build();
        Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.allowExperimentalOptions(true);
        contextBuilder.option("engine.BackgroundCompilation", "false");
        contextBuilder.option("engine.CompilationFailureAction", "Throw");
        contextBuilder.option("engine.CompileImmediately", "true");
        try (Context context = contextBuilder.build()) {
            Value exports = context.eval(source).newInstance().getMember("exports");
            Assert.assertEquals(-128, exports.getMember("load8x8_s").execute().asInt());
            Assert.assertEquals(128, exports.getMember("load8x8_u").execute().asInt());
            Assert.assertEquals(-32768, exports.getMember("load16x4_s").execute().asInt());
            Assert.assertEquals(32768, exports.getMember("load16x4_u").execute().asInt());
            Assert.assertEquals(0xffff_ffff_8000_1234L, exports.getMember("load32x2_s").execute().asLong());
            Assert.assertEquals(0x0000_0000_8000_1234L, exports.getMember("load32x2_u").execute().asLong());
        }
    }

    /*
     * (module
     *   (func (export "w0") (param externref) (result i32)
     *     (block (result nullexternref)
     *       (local.get 0)
     *       (br_on_cast 0 externref nullexternref)
     *       (drop)
     *       (i32.const 0)
     *       (return)
     *     )
     *     (drop)
     *     (i32.const 1)
     *   )
     * )
     */
    @Test
    public void brOnCastCompiles() throws IOException {
        Assume.assumeTrue("Optimizing runtime is not available", Engine.supportsCompilation());
        final byte[] binary = hexStringToByteArray(
                        "0061736d0100000001060160016f017f",
                        "0302010007060102773000000a160114",
                        "0002722000fb1803006f721a41000f0b",
                        "1a41010b");
        final Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binary), "issue14715").build();
        Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.allowExperimentalOptions(true);
        contextBuilder.option("engine.BackgroundCompilation", "false");
        contextBuilder.option("engine.CompilationFailureAction", "Throw");
        contextBuilder.option("engine.CompileImmediately", "true");
        try (Context context = contextBuilder.build()) {
            Value exports = context.eval(source).newInstance().getMember("exports");
            Value w0 = exports.getMember("w0");
            Assert.assertEquals(0, w0.execute("wasm-execution").asInt());
            Assert.assertEquals(1, w0.execute(WasmConstant.NULL).asInt());
            Assert.assertEquals(1, w0.execute(WasmConstant.NULL).asInt());
        }
    }
}
