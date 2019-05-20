/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.test;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.wasm.binary.exception.WasmTrap;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

public abstract class WasmTest {
    private TestElement[] testElements = {
                    test("(module (func (result i32) i32.const 42))", expected(42)),
                    test("(module (func (result i32) i32.const 1895633))", expected(1_895_633)),
                    test("(module (func (result i32) i32.const 42 i32.const 17 drop))", expected(42)),
                    test("(module (func (result i32) i32.const 0x12345678 i32.eqz))", expected(0)),
                    test("(module (func (result i32) i32.const 0x0 i32.eqz))", expected(1)),
                    test("(module (func (result i32) i32.const 0x1234 i32.const 0x1234 i32.eq))", expected(1)),
                    test("(module (func (result i32) i32.const 0x1234 i32.const 0xbaba i32.eq))", expected(0)),
                    test("(module (func (result i32) i32.const 0x1234 i32.const 0x1234 i32.ne))", expected(0)),
                    test("(module (func (result i32) i32.const 0x1234 i32.const 0xbaba i32.ne))", expected(1)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xbaba i32.lt_s))", expected(1)),
                    test("(module (func (result i32) i32.const 0xbaba i32.const 0xbaba i32.lt_s))", expected(0)),
                    test("(module (func (result i32) i32.const 0x0dedbaba i32.const 0xbaba i32.lt_s))", expected(0)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xbabababa i32.lt_u))", expected(0)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xdedababa i32.lt_u))", expected(0)),
                    test("(module (func (result i32) i32.const 0xcafebabe i32.const 0xdedababa i32.lt_u))", expected(1)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xbaba i32.gt_s))", expected(0)),
                    test("(module (func (result i32) i32.const 0xbaba i32.const 0xbaba i32.gt_s))", expected(0)),
                    test("(module (func (result i32) i32.const 0xbaba i32.const 0xbaba i32.gt_s))", expected(0)),
                    test("(module (func (result i32) i32.const 0x0dedbaba i32.const 0xbaba i32.gt_s))", expected(1)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xbabababa i32.gt_u))", expected(1)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xdedababa i32.gt_u))", expected(0)),
                    test("(module (func (result i32) i32.const 0xcafebabe i32.const 0xdedababa i32.gt_u))", expected(0)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xbaba i32.le_s))", expected(1)),
                    test("(module (func (result i32) i32.const 0xbaba i32.const 0xbaba i32.le_s))", expected(1)),
                    test("(module (func (result i32) i32.const 0x0dedbaba i32.const 0xbaba i32.le_s))", expected(0)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xbabababa i32.le_u))", expected(0)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xdedababa i32.le_u))", expected(1)),
                    test("(module (func (result i32) i32.const 0xcafebabe i32.const 0xdedababa i32.le_u))", expected(1)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xbaba i32.ge_s))", expected(0)),
                    test("(module (func (result i32) i32.const 0xbaba i32.const 0xbaba i32.ge_s))", expected(1)),
                    test("(module (func (result i32) i32.const 0x0dedbaba i32.const 0xbaba i32.ge_s))", expected(1)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xbabababa i32.ge_u))", expected(1)),
                    test("(module (func (result i32) i32.const 0xdedababa i32.const 0xdedababa i32.ge_u))", expected(1)),
                    test("(module (func (result i32) i32.const 0xcafebabe i32.const 0xdedababa i32.ge_u))", expected(0)),
                    test("(module (func (result i32) i64.const 0x12345678 i64.eqz))", expected(0)),
                    test("(module (func (result i32) i64.const 0x0 i64.eqz))", expected(1)),
                    test("(module (func (result i32) i64.const 0x1234 i64.const 0x1234 i64.eq))", expected(1)),
                    test("(module (func (result i32) i64.const 0x1234 i64.const 0xbaba i64.eq))", expected(0)),
                    test("(module (func (result i32) i64.const 0x1234 i64.const 0x1234 i64.ne))", expected(0)),
                    test("(module (func (result i32) i64.const 0x1234 i64.const 0xbaba i64.ne))", expected(1)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xbaba i64.lt_s))", expected(1)),
                    test("(module (func (result i32) i64.const 0xbaba i64.const 0xbaba i64.lt_s))", expected(0)),
                    test("(module (func (result i32) i64.const 0x0dedbaba i64.const 0xbaba i64.lt_s))", expected(0)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.lt_u))", expected(0)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.lt_u))", expected(0)),
                    test("(module (func (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.lt_u))", expected(1)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xbaba i64.gt_s))", expected(0)),
                    test("(module (func (result i32) i64.const 0xbaba i64.const 0xbaba i64.gt_s))", expected(0)),
                    test("(module (func (result i32) i64.const 0xbaba i64.const 0xbaba i64.gt_s))", expected(0)),
                    test("(module (func (result i32) i64.const 0x0dedbaba i64.const 0xbaba i64.gt_s))", expected(1)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.gt_u))", expected(1)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.gt_u))", expected(0)),
                    test("(module (func (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.gt_u))", expected(0)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xbaba i64.le_s))", expected(1)),
                    test("(module (func (result i32) i64.const 0xbaba i64.const 0xbaba i64.le_s))", expected(1)),
                    test("(module (func (result i32) i64.const 0x0dedbaba i64.const 0xbaba i64.le_s))", expected(0)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.le_u))", expected(0)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.le_u))", expected(1)),
                    test("(module (func (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.le_u))", expected(1)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xbaba i64.ge_s))", expected(0)),
                    test("(module (func (result i32) i64.const 0xbaba i64.const 0xbaba i64.ge_s))", expected(1)),
                    test("(module (func (result i32) i64.const 0x0dedbaba i64.const 0xbaba i64.ge_s))", expected(1)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xbabababa i64.ge_u))", expected(1)),
                    test("(module (func (result i32) i64.const 0xdedababa i64.const 0xdedababa i64.ge_u))", expected(1)),
                    test("(module (func (result i32) i64.const 0xcafebabe i64.const 0xdedababa i64.ge_u))", expected(0)),
                    test("(module (func (result i32) f32.const 0.5 f32.const 0.5 f32.eq))", expected(1)),
                    test("(module (func (result i32) f32.const 0.5 f32.const 0.1 f32.eq))", expected(0)),
                    test("(module (func (result i32) f32.const -0.0 f32.const 0.0 f32.eq))", expected(1)),
                    test("(module (func (result i32) f32.const nan f32.const nan f32.eq))", expected(0)),
                    test("(module (func (result i32) f32.const 0.5 f32.const 0.5 f32.ne))", expected(0)),
                    test("(module (func (result i32) f32.const 0.5 f32.const 0.1 f32.ne))", expected(1)),
                    test("(module (func (result i32) f32.const -0.0 f32.const 0.0 f32.ne))", expected(0)),
                    test("(module (func (result i32) f32.const nan f32.const nan f32.ne))", expected(1)),
                    test("(module (func (result i32) f32.const -inf f32.const -inf f32.lt))", expected(0)),
                    test("(module (func (result i32) f32.const inf f32.const inf f32.lt))", expected(0)),
                    test("(module (func (result i32) f32.const -inf f32.const -1 f32.lt))", expected(1)),
                    test("(module (func (result i32) f32.const -inf f32.const inf f32.lt))", expected(1)),
                    test("(module (func (result i32) f32.const -inf f32.const nan f32.lt))", expected(0)),
                    test("(module (func (result i32) f32.const nan f32.const nan f32.lt))", expected(0)),
                    test("(module (func (result i32) f32.const 1.0 f32.const 1.0 f32.lt))", expected(0)),
                    test("(module (func (result i32) f32.const 2.0 f32.const 1.0 f32.lt))", expected(0)),
                    test("(module (func (result i32) f32.const 1.0 f32.const 1.5 f32.lt))", expected(1)),
                    test("(module (func (result i32) f32.const -inf f32.const -inf f32.gt))", expected(0)),
                    test("(module (func (result i32) f32.const inf f32.const inf f32.gt))", expected(0)),
                    test("(module (func (result i32) f32.const -inf f32.const -1 f32.gt))", expected(0)),
                    test("(module (func (result i32) f32.const inf f32.const -inf f32.gt))", expected(1)),
                    test("(module (func (result i32) f32.const inf f32.const nan f32.gt))", expected(0)),
                    test("(module (func (result i32) f32.const nan f32.const nan f32.gt))", expected(0)),
                    test("(module (func (result i32) f32.const 1.0 f32.const 1.0 f32.gt))", expected(0)),
                    test("(module (func (result i32) f32.const 2.0 f32.const 1.5 f32.gt))", expected(1)),
                    test("(module (func (result i32) f32.const 1.0 f32.const 1.5 f32.gt))", expected(0)),
                    test("(module (func (result i32) f32.const -inf f32.const -inf f32.le))", expected(1)),
                    test("(module (func (result i32) f32.const inf f32.const inf f32.le))", expected(1)),
                    test("(module (func (result i32) f32.const -inf f32.const -1 f32.le))", expected(1)),
                    test("(module (func (result i32) f32.const -inf f32.const inf f32.le))", expected(1)),
                    test("(module (func (result i32) f32.const -inf f32.const nan f32.le))", expected(0)),
                    test("(module (func (result i32) f32.const nan f32.const nan f32.le))", expected(0)),
                    test("(module (func (result i32) f32.const 1.0 f32.const 1.0 f32.le))", expected(1)),
                    test("(module (func (result i32) f32.const 2.0 f32.const 1.0 f32.le))", expected(0)),
                    test("(module (func (result i32) f32.const 1.0 f32.const 1.5 f32.le))", expected(1)),
                    test("(module (func (result i32) f32.const -inf f32.const -inf f32.ge))", expected(1)),
                    test("(module (func (result i32) f32.const inf f32.const inf f32.ge))", expected(1)),
                    test("(module (func (result i32) f32.const -inf f32.const -1 f32.ge))", expected(0)),
                    test("(module (func (result i32) f32.const inf f32.const -inf f32.ge))", expected(1)),
                    test("(module (func (result i32) f32.const inf f32.const nan f32.ge))", expected(0)),
                    test("(module (func (result i32) f32.const nan f32.const nan f32.ge))", expected(0)),
                    test("(module (func (result i32) f32.const 1.0 f32.const 1.0 f32.ge))", expected(1)),
                    test("(module (func (result i32) f32.const 2.0 f32.const 1.5 f32.ge))", expected(1)),
                    test("(module (func (result i32) f32.const 1.0 f32.const 1.5 f32.ge))", expected(0)),
            test("(module (func (result i32) f64.const 0.5 f64.const 0.5 f64.eq))", expected(1)),
            test("(module (func (result i32) f64.const 0.5 f64.const 0.1 f64.eq))", expected(0)),
            test("(module (func (result i32) f64.const -0.0 f64.const 0.0 f64.eq))", expected(1)),
            test("(module (func (result i32) f64.const nan f64.const nan f64.eq))", expected(0)),
            test("(module (func (result i32) f64.const 0.5 f64.const 0.5 f64.ne))", expected(0)),
            test("(module (func (result i32) f64.const 0.5 f64.const 0.1 f64.ne))", expected(1)),
            test("(module (func (result i32) f64.const -0.0 f64.const 0.0 f64.ne))", expected(0)),
            test("(module (func (result i32) f64.const nan f64.const nan f64.ne))", expected(1)),
            test("(module (func (result i32) f64.const -inf f64.const -inf f64.lt))", expected(0)),
            test("(module (func (result i32) f64.const inf f64.const inf f64.lt))", expected(0)),
            test("(module (func (result i32) f64.const -inf f64.const -1 f64.lt))", expected(1)),
            test("(module (func (result i32) f64.const -inf f64.const inf f64.lt))", expected(1)),
            test("(module (func (result i32) f64.const -inf f64.const nan f64.lt))", expected(0)),
            test("(module (func (result i32) f64.const nan f64.const nan f64.lt))", expected(0)),
            test("(module (func (result i32) f64.const 1.0 f64.const 1.0 f64.lt))", expected(0)),
            test("(module (func (result i32) f64.const 2.0 f64.const 1.0 f64.lt))", expected(0)),
            test("(module (func (result i32) f64.const 1.0 f64.const 1.5 f64.lt))", expected(1)),
            test("(module (func (result i32) f64.const -inf f64.const -inf f64.gt))", expected(0)),
            test("(module (func (result i32) f64.const inf f64.const inf f64.gt))", expected(0)),
            test("(module (func (result i32) f64.const -inf f64.const -1 f64.gt))", expected(0)),
            test("(module (func (result i32) f64.const inf f64.const -inf f64.gt))", expected(1)),
            test("(module (func (result i32) f64.const inf f64.const nan f64.gt))", expected(0)),
            test("(module (func (result i32) f64.const nan f64.const nan f64.gt))", expected(0)),
            test("(module (func (result i32) f64.const 1.0 f64.const 1.0 f64.gt))", expected(0)),
            test("(module (func (result i32) f64.const 2.0 f64.const 1.5 f64.gt))", expected(1)),
            test("(module (func (result i32) f64.const 1.0 f64.const 1.5 f64.gt))", expected(0)),
            test("(module (func (result i32) f64.const -inf f64.const -inf f64.le))", expected(1)),
            test("(module (func (result i32) f64.const inf f64.const inf f64.le))", expected(1)),
            test("(module (func (result i32) f64.const -inf f64.const -1 f64.le))", expected(1)),
            test("(module (func (result i32) f64.const -inf f64.const inf f64.le))", expected(1)),
            test("(module (func (result i32) f64.const -inf f64.const nan f64.le))", expected(0)),
            test("(module (func (result i32) f64.const nan f64.const nan f64.le))", expected(0)),
            test("(module (func (result i32) f64.const 1.0 f64.const 1.0 f64.le))", expected(1)),
            test("(module (func (result i32) f64.const 2.0 f64.const 1.0 f64.le))", expected(0)),
            test("(module (func (result i32) f64.const 1.0 f64.const 1.5 f64.le))", expected(1)),
            test("(module (func (result i32) f64.const -inf f64.const -inf f64.ge))", expected(1)),
            test("(module (func (result i32) f64.const inf f64.const inf f64.ge))", expected(1)),
            test("(module (func (result i32) f64.const -inf f64.const -1 f64.ge))", expected(0)),
            test("(module (func (result i32) f64.const inf f64.const -inf f64.ge))", expected(1)),
            test("(module (func (result i32) f64.const inf f64.const nan f64.ge))", expected(0)),
            test("(module (func (result i32) f64.const nan f64.const nan f64.ge))", expected(0)),
            test("(module (func (result i32) f64.const 1.0 f64.const 1.0 f64.ge))", expected(1)),
            test("(module (func (result i32) f64.const 2.0 f64.const 1.5 f64.ge))", expected(1)),
            test("(module (func (result i32) f64.const 1.0 f64.const 1.5 f64.ge))", expected(0)),
                    test("(module (func (result i32) i32.const 0x12345678 i32.clz))", expected(3)),
                    test("(module (func (result i32) i32.const 0x1234567C i32.ctz))", expected(2)),
                    test("(module (func (result i32) i32.const 0x12345670 i32.ctz))", expected(4)),
                    test("(module (func (result i32) i32.const 0xBABABABA i32.popcnt))", expected(20)),
                    test("(module (func (result i32) i32.const 42 i32.const 17 i32.add))", expected(59)),
                    test("(module (func (result i32) i32.const 42 i32.const 17 i32.sub))", expected(25)),
                    test("(module (func (result i32) i32.const 42 i32.const 43 i32.sub))", expected(-1)),
                    test("(module (func (result i32) i32.const 11 i32.const 12 i32.mul))", expected(132)),
                    test("(module (func (result i32) i32.const 11 i32.const 3 i32.div_s))", expected(3)),
                    test("(module (func (result i32) i32.const -11 i32.const 3 i32.div_s))", expected(-3)),
                    test("(module (func (result i32) i32.const 118 i32.const 11 i32.div_u))", expected(10)),
                    test("(module (func (result i32) i32.const 0x80000001 i32.const 0x1000 i32.div_u))", expected(524288)),
                    test("(module (func (result i32) i32.const 118 i32.const 11 i32.div_u))", expected(10)),
                    test("(module (func (result i32) i32.const 11 i32.const 3 i32.rem_s))", expected(2)),
                    test("(module (func (result i32) i32.const 11 i32.const -3 i32.rem_s))", expected(2)),
                    test("(module (func (result i32) i32.const -11 i32.const -3 i32.rem_s))", expected(-2)),
                    test("(module (func (result i32) i32.const -11 i32.const 3 i32.rem_s))", expected(-2)),
                    test("(module (func (result i32) i32.const 0x80000001 i32.const 0x1001 i32.rem_u))", expected(129)),
                    test("(module (func (result i32) i32.const 10 i32.const 7 i32.and))", expected(2)),
                    test("(module (func (result i32) i32.const -1 i32.const 7 i32.and))", expected(7)),
                    test("(module (func (result i32) i32.const -2 i32.const 7 i32.or))", expected(-1)),
                    test("(module (func (result i32) i32.const 6 i32.const 9 i32.or))", expected(15)),
                    test("(module (func (result i32) i32.const 11 i32.const 4 i32.xor))", expected(15)),
                    test("(module (func (result i32) i32.const -2 i32.const 3 i32.xor))", expected(-3)),
                    test("(module (func (result i32) i32.const -1 i32.const 1 i32.shl))", expected(-2)),
                    test("(module (func (result i32) i32.const -1 i32.const 1 i32.shr_s))", expected(-1)),
                    test("(module (func (result i32) i32.const -1 i32.const 1 i32.shr_u))", expected(Integer.MAX_VALUE)),
                    test("(module (func (result i32) i32.const -2 i32.const 1 i32.rotl))", expected(-3)),
                    test("(module (func (result i32) i32.const -3 i32.const 1 i32.rotr))", expected(-2)),
                    test("(module (func (result f32) f32.const -2.0 f32.const -4.5 f32.add))", expected(-6.5f)),
                    test("(module (func (result i32) i32.const 1690433))", expected(1690433)),
                    test("(module (func (result f32) f32.const 3.14))", expected(3.14f, 0.001f)),
                    test("(module (func (result f32) f32.const 3.14 f32.const 2.71 f32.add))", expected(5.85f, 0.001f)),
                    test("(module (func (result f64) f64.const 340.75))", expected(340.75, 0.001)),
                    test("(module (func (result i32) block $B0 (result i32) i32.const 11 end i32.const 21 i32.add))", expected(32)),
                    test("(module (func (result i32) block $B0 (result i32) i32.const 42 block $B1 (result i32) i32.const 12 end i32.add end))", expected(54)),
                    test("(module (func (result i32) block $B0 (result i32) i32.const 42 block $B1 (result i32) i32.const 12 i32.const 24 i32.add end i32.sub end))", expected(6)),
                    test("(module (func (result i32) block $B0 (result i32) i32.const 42 block $B1 (result i32) i32.const 12 i32.const 24 i32.add block $B3 (result i32) i32.const 36 end i32.sub end i32.sub end))",
                                    expected(42)),
                    test("(module (func (result i32) block $B0 (result i32) i32.const 42 block $B1 (result i32) i32.const 12 i32.const 24 block $B4 i32.const 0 i32.const 1 i32.const 2 drop drop drop end i32.add block $B3 (result i32) i32.const 36 end i32.sub end i32.sub end))",
                                    expected(42)),
                    test("(module (func (result i32) (local $l0 i32) i32.const 42 local.set $l0 local.get $l0))",
                                    expected(42)),
                    test("(module (func (result i32) (local $l0 i32) i32.const 42 local.set $l0 local.get $l0 unreachable))",
                                    expectedThrows("unreachable")),
                    test("(module (func (result i32) (local $l0 i32) i32.const 0 if $l0 (result i32) i32.const 42 else i32.const 55 end))",
                                    expected(55)),
                    test("(module (func (result i32) (local $l0 i32) i32.const 1 if $l0 (result i32) i32.const 42 else i32.const 55 end))",
                                    expected(42)),
                    test("(module (func (result i64) (local $l0 i32) i32.const 1 if $l0 i32.const 42 drop else i32.const 55 drop end i64.const 100))",
                                    expected(100L)),
                    test("(module (func (result i64) (local $l0 i32) i32.const 1 if $l0 i32.const 42 drop end i64.const 100))",
                                    expected(100L)),
    };

    private static TestElement test(String program, TestData data) {
        return new TestElement(program, data);
    }

    private static TestData expected(Object expectedValue) {
        return new TestData((result) -> Assert.assertEquals("", expectedValue, result.as(Object.class)));
    }

    private static TestData expected(float expectedValue, float e) {
        return new TestData((result) -> Assert.assertEquals(expectedValue, result.as(Float.class), e));
    }

    private static TestData expected(double expectedValue, double e) {
        return new TestData((result) -> Assert.assertEquals(expectedValue, result.as(Double.class), e));
    }

    private static TestData expectedThrows(String expectedErrorMessage) {
        return new TestData(expectedErrorMessage);
    }

    protected void validateResult(TestData data, Value value) {
        if (data.validator != null) {
            data.validator.accept(value);
        } else {
            Assert.fail("Test was not supposed to return a value.");
        }
    }

    protected void validateThrown(TestData data, PolyglotException e) throws PolyglotException {
        if (data.expectedErrorMessage != null) {
            if (!data.expectedErrorMessage.equals(e.getMessage())) {
                throw e;
            }
        } else {
            throw e;
        }
    }

    @Test
    public void runTests() throws InterruptedException {
        String filter = System.getProperty("wasm.TestFilter");
        Map<TestElement, Throwable> errors = new LinkedHashMap<>();
        System.out.println("Using runtime: " + Truffle.getRuntime().toString());
        for (TestElement element : testElements) {
            if (element.program.matches(filter)) {
                try {
                    runTest(element);
                    System.out.print("\uD83D\uDE0D");
                    System.out.flush();
                } catch (Throwable e) {
                    System.out.print("\uD83D\uDE2D");
                    System.out.flush();
                    errors.put(element, e);
                }
            }
        }
        System.out.println("");
        if (!errors.isEmpty()) {
            for (Map.Entry<TestElement, Throwable> entry : errors.entrySet()) {
                System.err.println("Failure in: " + entry.getKey().name);
                System.err.println(entry.getValue().getClass().getSimpleName() + ": " + entry.getValue().getMessage());
                entry.getValue().printStackTrace();
            }
            System.err.println("\u001B[31m" + (testElements.length - errors.size()) + "/" + testElements.length + " tests passed.\u001B[0m");
            throw new RuntimeException("Tests failed!");
        } else {
            System.out.println("\u001B[32m" + testElements.length + "/" + testElements.length + " tests passed.\u001B[0m");
        }
        Thread.sleep(10000);
    }

    protected abstract void runTest(TestElement element) throws Throwable;

    protected static class TestElement {
        public String name;
        public String program;
        public TestData data;

        TestElement(String name, String program, TestData data) {
            this.name = name;
            this.program = program;
            this.data = data;
        }

        TestElement(String program, TestData data) {
            this.name = program;
            this.program = program;
            this.data = data;
        }
    }
}
