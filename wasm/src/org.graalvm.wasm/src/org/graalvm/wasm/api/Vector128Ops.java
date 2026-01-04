/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.api;

public interface Vector128Ops<V128> {

    Vector128Ops<?> SINGLETON_IMPLEMENTATION = lookupImplementation();

    private static Vector128Ops<?> lookupImplementation() {
        if (ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent()) {
            try {
                return Vector128OpsVectorAPI.create();
            } catch (UnsupportedOperationException | NoClassDefFoundError e) {
            }
        }
        return Vector128OpsFallback.create();
    }

    V128 unary(V128 x, int vectorOpcode);

    V128 binary(V128 x, V128 y, int vectorOpcode);

    V128 ternary(V128 x, V128 y, V128 z, int vectorOpcode);

    int vectorToInt(V128 x, int vectorOpcode);

    V128 shift(V128 x, int shift, int vectorOpcode);

    // Checkstyle: stop method name check
    V128 v128_load8x8(long value, int vectorOpcode);

    V128 v128_load16x4(long value, int vectorOpcode);

    V128 v128_load32x2(long value, int vectorOpcode);

    V128 v128_load32_zero(int value);

    V128 v128_load64_zero(long value);

    V128 i8x16_splat(byte value);

    V128 i16x8_splat(short value);

    V128 i32x4_splat(int value);

    V128 i64x2_splat(long value);

    V128 f32x4_splat(float value);

    V128 f64x2_splat(double value);

    V128 i8x16_shuffle(V128 x, V128 y, V128 indices);

    byte i8x16_extract_lane_s(V128 vec, int laneIndex);

    int i8x16_extract_lane(V128 vec, int laneIndex, int vectorOpcode);

    V128 i8x16_replace_lane(V128 vec, int laneIndex, byte value);

    short i16x8_extract_lane_s(V128 vec, int laneIndex);

    int i16x8_extract_lane(V128 vec, int laneIndex, int vectorOpcode);

    V128 i16x8_replace_lane(V128 vec, int laneIndex, short value);

    int i32x4_extract_lane(V128 vec, int laneIndex);

    V128 i32x4_replace_lane(V128 vec, int laneIndex, int value);

    long i64x2_extract_lane(V128 vec, int laneIndex);

    V128 i64x2_replace_lane(V128 vec, int laneIndex, long value);

    float f32x4_extract_lane(V128 vec, int laneIndex);

    V128 f32x4_replace_lane(V128 vec, int laneIndex, float value);

    double f64x2_extract_lane(V128 vec, int laneIndex);

    V128 f64x2_replace_lane(V128 vec, int laneIndex, double value);
    // Checkstyle: resume method name check

    default V128 fromArray(byte[] bytes) {
        return fromArray(bytes, 0);
    }

    V128 fromArray(byte[] bytes, int offset);

    byte[] toArray(V128 vec);

    void intoArray(V128 vec, byte[] array, int offset);

    Vector128 toVector128(V128 vec);

    V128 fromVector128(Vector128 vector128);

    // The WasmMemoryLibrary has to use the Object type instead of the generic V128 type in
    // load_i128 and store_i128. We need to convince the compiler that it can safely cast the
    // Object parameters and return values to the vector implementation's type.
    @SuppressWarnings("unchecked")
    static <T> T cast(Object vec) {
        return (T) vec;
    }
}
