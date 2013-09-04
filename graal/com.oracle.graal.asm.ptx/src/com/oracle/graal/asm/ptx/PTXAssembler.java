/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.asm.ptx;

import com.oracle.graal.api.code.*;

public class PTXAssembler extends AbstractPTXAssembler {

    public PTXAssembler(TargetDescription target, @SuppressWarnings("unused") RegisterConfig registerConfig) {
        super(target);
    }

    public final void at() {
        emitString("@%p" + " " + "");
    }

    public final void atq() {
        emitString("@%q" + " " + "");
    }

    // Checkstyle: stop method name check
    public final void add_f32(Register d, Register a, Register b) {
        emitString("add.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void add_f64(Register d, Register a, Register b) {
        emitString("add.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void add_s16(Register d, Register a, Register b) {
        emitString("add.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void add_s32(Register d, Register a, Register b) {
        emitString("add.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void add_s64(Register d, Register a, Register b) {
        emitString("add.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void add_s16(Register d, Register a, short s16) {
        emitString("add.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s16 + ";" + "");
    }

    public final void add_s32(Register d, Register a, int s32) {
        emitString("add.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void add_s64(Register d, Register a, long s64) {
        emitString("add.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s64 + ";" + "");
    }

    public final void add_f32(Register d, Register a, float f32) {
        emitString("add.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + f32 + ";" + "");
    }

    public final void add_f64(Register d, Register a, double f64) {
        emitString("add.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + f64 + ";" + "");
    }

    public final void add_u16(Register d, Register a, Register b) {
        emitString("add.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void add_u32(Register d, Register a, Register b) {
        emitString("add.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void add_u64(Register d, Register a, Register b) {
        emitString("add.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void add_u16(Register d, Register a, short u16) {
        emitString("add.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u16 + ";" + "");
    }

    public final void add_u32(Register d, Register a, int u32) {
        emitString("add.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void add_u64(Register d, Register a, long u64) {
        emitString("add.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u64 + ";" + "");
    }

    public final void add_sat_s32(Register d, Register a, Register b) {
        emitString("add.sat.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void add_sat_s32(Register d, Register a, int s32) {
        emitString("add.sat.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void and_b16(Register d, Register a, Register b) {
        emitString("and.b16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void and_b32(Register d, Register a, Register b) {
        emitString("and.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void and_b64(Register d, Register a, Register b) {
        emitString("and.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void and_b16(Register d, Register a, short b16) {
        emitString("and.b16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b16 + ";" + "");
    }

    public final void and_b32(Register d, Register a, int b32) {
        emitString("and.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b32 + ";" + "");
    }

    public final void and_b64(Register d, Register a, long b64) {
        emitString("and.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b64 + ";" + "");
    }

    public final void bra(String tgt) {
        emitString("bra" + " " + tgt + ";" + "");
    }

    public final void bra_uni(String tgt) {
        emitString("bra.uni" + " " + tgt + ";" + "");
    }

    public final void cvt_s32_f32(Register d, Register a) {
        emitString("cvt.s32.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s64_f32(Register d, Register a) {
        emitString("cvt.s64.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_f64_f32(Register d, Register a) {
        emitString("cvt.f64.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_f32_f64(Register d, Register a) {
        emitString("cvt.f32.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s32_f64(Register d, Register a) {
        emitString("cvt.s32.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s64_f64(Register d, Register a) {
        emitString("cvt.s64.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_f32_s32(Register d, Register a) {
        emitString("cvt.f32.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_f64_s32(Register d, Register a) {
        emitString("cvt.f64.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s8_s32(Register d, Register a) {
        emitString("cvt.s8.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_b16_s32(Register d, Register a) {
        emitString("cvt.b16.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s64_s32(Register d, Register a) {
        emitString("cvt.s64.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void cvt_s32_s64(Register d, Register a) {
        emitString("cvt.s32.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void div_f32(Register d, Register a, Register b) {
        emitString("div.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_f64(Register d, Register a, Register b) {
        emitString("div.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_s16(Register d, Register a, Register b) {
        emitString("div.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_s32(Register d, Register a, Register b) {
        emitString("div.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_s64(Register d, Register a, Register b) {
        emitString("div.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_s16(Register d, Register a, short s16) {
        emitString("div.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s16 + ";" + "");
    }

    public final void div_s32(Register d, Register a, int s32) {
        emitString("div.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void div_s32(Register d, int s32, Register b) {
        emitString("div.s32" + " " + "%r" + d.encoding() + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_f32(Register d, float f32, Register b) {
        emitString("div.f32" + " " + "%r" + d.encoding() + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_f64(Register d, double f64, Register b) {
        emitString("div.f64" + " " + "%r" + d.encoding() + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_s64(Register d, Register a, long s64) {
        emitString("div.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s64 + ";" + "");
    }

    public final void div_f32(Register d, Register a, float f32) {
        emitString("div.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + f32 + ";" + "");
    }

    public final void div_f64(Register d, Register a, double f64) {
        emitString("div.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + f64 + ";" + "");
    }

    public final void div_u16(Register d, Register a, Register b) {
        emitString("div.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_u32(Register d, Register a, Register b) {
        emitString("div.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_u64(Register d, Register a, Register b) {
        emitString("div.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void div_u16(Register d, Register a, short u16) {
        emitString("div.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u16 + ";" + "");
    }

    public final void div_u32(Register d, Register a, int u32) {
        emitString("div.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void div_u64(Register d, Register a, long u64) {
        emitString("div.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u64 + ";" + "");
    }

    public final void exit() {
        emitString("exit;" + " " + "");
    }

    public final void ld_global_b8(Register d, Register a, long immOff) {
        emitString("ld.global.b8" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_b16(Register d, Register a, long immOff) {
        emitString("ld.global.b16" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_b32(Register d, Register a, long immOff) {
        emitString("ld.global.b32" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_b64(Register d, Register a, long immOff) {
        emitString("ld.global.b64" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_u8(Register d, Register a, long immOff) {
        emitString("ld.global.u8" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_u16(Register d, Register a, long immOff) {
        emitString("ld.global.u16" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_u32(Register d, Register a, long immOff) {
        emitString("ld.global.u32" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_u64(Register d, Register a, long immOff) {
        emitString("ld.global.u64" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_s8(Register d, Register a, long immOff) {
        emitString("ld.global.s8" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_s16(Register d, Register a, long immOff) {
        emitString("ld.global.s16" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_s32(Register d, Register a, long immOff) {
        emitString("ld.global.s32" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_s64(Register d, Register a, long immOff) {
        emitString("ld.global.s64" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_f32(Register d, Register a, long immOff) {
        emitString("ld.global.f32" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void ld_global_f64(Register d, Register a, long immOff) {
        emitString("ld.global.f64" + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    // Load from state space to destination register
    public final void ld_from_state_space(String s, Register d, Register a, long immOff) {
        emitString("ld" + s + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    // Load return address from return parameter which is in .param state space
    public final void ld_return_address(String s, Register d, Register a, long immOff) {
        emitString("ld.param." + s + " " + "%r" + d.encoding() + ", [" + a + " + " + immOff + "]" + ";" + "");
    }

    public final void mov_b16(Register d, Register a) {
        emitString("mov.b16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_b32(Register d, Register a) {
        emitString("mov.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_b64(Register d, Register a) {
        emitString("mov.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_u16(Register d, Register a) {
        emitString("mov.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_u32(Register d, Register a) {
        emitString("mov.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_u64(Register d, Register a) {
        emitString("mov.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_u64(@SuppressWarnings("unused") Register d, @SuppressWarnings("unused") AbstractAddress a) {
        // emitString("mov.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_s16(Register d, Register a) {
        emitString("mov.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_s32(Register d, Register a) {
        emitString("mov.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_s64(Register d, Register a) {
        emitString("mov.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_f32(Register d, Register a) {
        emitString("mov.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_f64(Register d, Register a) {
        emitString("mov.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void mov_b16(Register d, short b16) {
        emitString("mov.b16" + " " + "%r" + d.encoding() + ", " + b16 + ";" + "");
    }

    public final void mov_b32(Register d, int b32) {
        emitString("mov.b32" + " " + "%r" + d.encoding() + ", " + b32 + ";" + "");
    }

    public final void mov_b64(Register d, long b64) {
        emitString("mov.b64" + " " + "%r" + d.encoding() + ", " + b64 + ";" + "");
    }

    public final void mov_u16(Register d, short u16) {
        emitString("mov.u16" + " " + "%r" + d.encoding() + ", " + u16 + ";" + "");
    }

    public final void mov_u32(Register d, int u32) {
        emitString("mov.u32" + " " + "%r" + d.encoding() + ", " + u32 + ";" + "");
    }

    public final void mov_u64(Register d, long u64) {
        emitString("mov.u64" + " " + "%r" + d.encoding() + ", " + u64 + ";" + "");
    }

    public final void mov_s16(Register d, short s16) {
        emitString("mov.s16" + " " + "%r" + d.encoding() + ", " + s16 + ";" + "");
    }

    public final void mov_s32(Register d, int s32) {
        emitString("mov.s32" + " " + "%r" + d.encoding() + ", " + s32 + ";" + "");
    }

    public final void mov_s64(Register d, long s64) {
        emitString("mov.s64" + " " + "%r" + d.encoding() + ", " + s64 + ";" + "");
    }

    public final void mov_f32(Register d, float f32) {
        emitString("mov.f32" + " " + "%r" + d.encoding() + ", " + f32 + ";" + "");
    }

    public final void mov_f64(Register d, double f64) {
        emitString("mov.f64" + " " + "%r" + d.encoding() + ", " + f64 + ";" + "");
    }

    public final void mul_lo_f32(Register d, Register a, Register b) {
        emitString("mul.lo.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void mul_lo_f64(Register d, Register a, Register b) {
        emitString("mul.lo.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void mul_lo_s16(Register d, Register a, Register b) {
        emitString("mul.lo.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void mul_lo_s32(Register d, Register a, Register b) {
        emitString("mul.lo.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void mul_lo_s64(Register d, Register a, Register b) {
        emitString("mul.lo.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void mul_lo_s16(Register d, Register a, short s16) {
        emitString("mul.lo.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s16 + ";" + "");
    }

    public final void mul_lo_s32(Register d, Register a, int s32) {
        emitString("mul.lo.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void mul_lo_s64(Register d, Register a, long s64) {
        emitString("mul.lo.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s64 + ";" + "");
    }

    public final void mul_lo_f32(Register d, Register a, float f32) {
        emitString("mul.lo.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + f32 + ";" + "");
    }

    public final void mul_lo_f64(Register d, Register a, double f64) {
        emitString("mul.lo.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + f64 + ";" + "");
    }

    public final void mul_lo_u16(Register d, Register a, Register b) {
        emitString("mul.lo.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void mul_lo_u32(Register d, Register a, Register b) {
        emitString("mul.lo.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void mul_lo_u64(Register d, Register a, Register b) {
        emitString("mul.lo.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void mul_lo_u16(Register d, Register a, short u16) {
        emitString("mul.lo.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u16 + ";" + "");
    }

    public final void mul_lo_u32(Register d, Register a, int u32) {
        emitString("mul.lo.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void mul_lo_u64(Register d, Register a, long u64) {
        emitString("mul.lo.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u64 + ";" + "");
    }

    public final void neg_f32(Register d, Register a) {
        emitString("neg.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void neg_f64(Register d, Register a) {
        emitString("neg.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void neg_s16(Register d, Register a) {
        emitString("neg.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void neg_s32(Register d, Register a) {
        emitString("neg.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void neg_s64(Register d, Register a) {
        emitString("neg.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void not_s16(Register d, Register a) {
        emitString("not.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void not_s32(Register d, Register a) {
        emitString("not.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void not_s64(Register d, Register a) {
        emitString("not.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void or_b16(Register d, Register a, Register b) {
        emitString("or.b16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void or_b32(Register d, Register a, Register b) {
        emitString("or.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void or_b64(Register d, Register a, Register b) {
        emitString("or.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void or_b16(Register d, Register a, short b16) {
        emitString("or.b16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b16 + ";" + "");
    }

    public final void or_b32(Register d, Register a, int b32) {
        emitString("or.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b32 + ";" + "");
    }

    public final void or_b64(Register d, Register a, long b64) {
        emitString("or.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b64 + ";" + "");
    }

    public final void param_8_decl(Register d, boolean lastParam) {
        emitString(".param" + " " + ".s8" + " " + d + (lastParam ? "" : ","));
    }

    public final void param_32_decl(Register d, boolean lastParam) {
        emitString(".param" + " " + ".s32" + " " + d + (lastParam ? "" : ","));
    }

    public final void param_64_decl(Register d, boolean lastParam) {
        emitString(".param" + " " + ".s64" + " " + d + (lastParam ? "" : ","));
    }

    public final void popc_b32(Register d, Register a) {
        emitString("popc.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void popc_b64(Register d, Register a) {
        emitString("popc.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ";" + "");
    }

    public final void rem_s16(Register d, Register a, Register b) {
        emitString("rem.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void rem_s32(Register d, Register a, Register b) {
        emitString("rem.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void rem_s64(Register d, Register a, Register b) {
        emitString("rem.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void rem_s16(Register d, Register a, short s16) {
        emitString("rem.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s16 + ";" + "");
    }

    public final void rem_s32(Register d, Register a, int s32) {
        emitString("rem.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void rem_s64(Register d, Register a, long s64) {
        emitString("rem.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s64 + ";" + "");
    }

    public final void rem_u16(Register d, Register a, Register b) {
        emitString("rem.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void rem_u32(Register d, Register a, Register b) {
        emitString("rem.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void rem_u64(Register d, Register a, Register b) {
        emitString("rem.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void rem_u16(Register d, Register a, short u16) {
        emitString("rem.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u16 + ";" + "");
    }

    public final void rem_u32(Register d, Register a, int u32) {
        emitString("rem.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void rem_u64(Register d, Register a, long u64) {
        emitString("rem.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u64 + ";" + "");
    }

    public final void ret() {
        emitString("ret;" + " " + "");
    }

    public final void ret_uni() {
        emitString("ret.uni;" + " " + "");
    }

    public final void setp_eq_f32(Register a, Register b) {
        emitString("setp.eq.f32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_f32(Register a, Register b) {
        emitString("setp.ne.f32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_f32(Register a, Register b) {
        emitString("setp.lt.f32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_f32(Register a, Register b) {
        emitString("setp.le.f32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_f32(Register a, Register b) {
        emitString("setp.gt.f32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_f32(Register a, Register b) {
        emitString("setp.ge.f32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_f32(float f32, Register b) {
        emitString("setp.eq.f32" + " " + "%p" + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_f32(float f32, Register b) {
        emitString("setp.ne.f32" + " " + "%p" + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_f32(float f32, Register b) {
        emitString("setp.lt.f32" + " " + "%p" + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_f32(float f32, Register b) {
        emitString("setp.le.f32" + " " + "%p" + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_f32(float f32, Register b) {
        emitString("setp.gt.f32" + " " + "%p" + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_f32(float f32, Register b) {
        emitString("setp.ge.f32" + " " + "%p" + ", " + f32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_f64(double f64, Register b) {
        emitString("setp.eq.f64" + " " + "%p" + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_f64(double f64, Register b) {
        emitString("setp.ne.f64" + " " + "%p" + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_f64(double f64, Register b) {
        emitString("setp.lt.f64" + " " + "%p" + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_f64(double f64, Register b) {
        emitString("setp.le.f64" + " " + "%p" + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_f64(double f64, Register b) {
        emitString("setp.gt.f64" + " " + "%p" + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_f64(double f64, Register b) {
        emitString("setp.ge.f64" + " " + "%p" + ", " + f64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_s64(Register a, Register b) {
        emitString("setp.eq.s64" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_s64(long s64, Register b) {
        emitString("setp.eq.s64" + " " + "%p" + ", " + s64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_s32(Register a, Register b) {
        emitString("setp.eq.s32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_s32(Register a, Register b) {
        emitString("setp.ne.s32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_s32(Register a, Register b) {
        emitString("setp.lt.s32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_s32(Register a, Register b) {
        emitString("setp.le.s32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_s32(Register a, Register b) {
        emitString("setp.gt.s32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_s32(Register a, Register b) {
        emitString("setp.ge.s32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_s32(Register a, int s32) {
        emitString("setp.eq.s32" + " " + "%p" + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_ne_s32(Register a, int s32) {
        emitString("setp.ne.s32" + " " + "%p" + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_lt_s32(Register a, int s32) {
        emitString("setp.lt.s32" + " " + "%p" + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_le_s32(Register a, int s32) {
        emitString("setp.le.s32" + " " + "%p" + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_gt_s32(Register a, int s32) {
        emitString("setp.gt.s32" + " " + "%p" + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_ge_s32(Register a, int s32) {
        emitString("setp.ge.s32" + " " + "%p" + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void setp_eq_s32(int s32, Register b) {
        emitString("setp.eq.s32" + " " + "%p" + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_s32(int s32, Register b) {
        emitString("setp.ne.s32" + " " + "%p" + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_s32(int s32, Register b) {
        emitString("setp.lt.s32" + " " + "%p" + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_s32(int s32, Register b) {
        emitString("setp.le.s32" + " " + "%p" + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_s32(int s32, Register b) {
        emitString("setp.gt.s32" + " " + "%p" + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_s32(int s32, Register b) {
        emitString("setp.ge.s32" + " " + "%p" + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_u32(Register a, Register b) {
        emitString("setp.eq.u32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_u32(Register a, Register b) {
        emitString("setp.ne.u32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_u32(Register a, Register b) {
        emitString("setp.lt.u32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_u32(Register a, Register b) {
        emitString("setp.le.u32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_u32(Register a, Register b) {
        emitString("setp.gt.u32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_u32(Register a, Register b) {
        emitString("setp.ge.u32" + " " + "%p" + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_eq_u32(Register a, int u32) {
        emitString("setp.eq.u32" + " " + "%p" + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_ne_u32(Register a, int u32) {
        emitString("setp.ne.u32" + " " + "%p" + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_lt_u32(Register a, int u32) {
        emitString("setp.lt.u32" + " " + "%p" + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_le_u32(Register a, int u32) {
        emitString("setp.le.u32" + " " + "%p" + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_gt_u32(Register a, int u32) {
        emitString("setp.gt.u32" + " " + "%p" + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_ge_u32(Register a, int u32) {
        emitString("setp.ge.u32" + " " + "%p" + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void setp_eq_u32(int u32, Register b) {
        emitString("setp.eq.u32" + " " + "%p" + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ne_u32(int u32, Register b) {
        emitString("setp.ne.u32" + " " + "%p" + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_lt_u32(int u32, Register b) {
        emitString("setp.lt.u32" + " " + "%p" + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_le_u32(int u32, Register b) {
        emitString("setp.le.u32" + " " + "%p" + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_gt_u32(int u32, Register b) {
        emitString("setp.gt.u32" + " " + "%p" + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void setp_ge_u32(int u32, Register b) {
        emitString("setp.ge.u32" + " " + "%p" + ", " + u32 + ", %r" + b.encoding() + ";" + "");
    }

    // Shift left - only types supported are .b16, .b32 and .b64
    public final void shl_b16(Register d, Register a, Register b) {
        emitString("shl.b16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void shl_b32(Register d, Register a, Register b) {
        emitString("shl.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void shl_b64(Register d, Register a, Register b) {
        emitString("shl.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void shl_b16_const(Register d, Register a, int b) {
        emitString("shl.b16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b + ";" + "");
    }

    public final void shl_b32_const(Register d, Register a, int b) {
        emitString("shl.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b + ";" + "");
    }

    public final void shl_b64_const(Register d, Register a, int b) {
        emitString("shl.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b + ";" + "");
    }

    // Shift Right instruction
    public final void shr_s16(Register d, Register a, Register b) {
        emitString("shr.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void shr_s32(Register d, Register a, Register b) {
        emitString("shr.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void shr_s64(Register d, Register a, Register b) {
        emitString("shr.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void shr_s16(Register d, Register a, int u32) {
        emitString("shr.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void shr_s32(Register d, Register a, int u32) {
        emitString("shr.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void shr_s64(Register d, Register a, int u32) {
        emitString("shr.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void shr_u16(Register d, Register a, Register b) {
        emitString("shr.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void shr_u32(Register d, Register a, Register b) {
        emitString("shr.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void shr_u64(Register d, Register a, Register b) {
        emitString("shr.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void shr_u16(Register d, Register a, int u32) {
        emitString("shr.u16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void shr_u32(Register d, Register a, int u32) {
        emitString("shr.u32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u32 + ";" + "");
    }

    public final void shr_u64(Register d, Register a, long u64) {
        emitString("shr.u64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + u64 + ";" + "");
    }

    // Store in global state space

    public final void st_global_b8(Register a, long immOff, Register b) {
        emitString("st.global.b8" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_b16(Register a, long immOff, Register b) {
        emitString("st.global.b16" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_b32(Register a, long immOff, Register b) {
        emitString("st.global.b32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_b64(Register a, long immOff, Register b) {
        emitString("st.global.b64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_u8(Register a, long immOff, Register b) {
        emitString("st.global.u8" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_u16(Register a, long immOff, Register b) {
        emitString("st.global.u16" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_u32(Register a, long immOff, Register b) {
        emitString("st.global.u32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_u64(Register a, long immOff, Register b) {
        emitString("st.global.u64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_s8(Register a, long immOff, Register b) {
        emitString("st.global.s8" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_s16(Register a, long immOff, Register b) {
        emitString("st.global.s16" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_s32(Register a, long immOff, Register b) {
        emitString("st.global.s32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_s64(Register a, long immOff, Register b) {
        emitString("st.global.s64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_f32(Register a, long immOff, Register b) {
        emitString("st.global.f32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_f64(Register a, long immOff, Register b) {
        emitString("st.global.f64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    // Store return value
    public final void st_global_return_value_s8(Register a, long immOff, Register b) {
        emitString("st.global.s8" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_s32(Register a, long immOff, Register b) {
        emitString("st.global.s32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_s64(Register a, long immOff, Register b) {
        emitString("st.global.s64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_f32(Register a, long immOff, Register b) {
        emitString("st.global.f32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_f64(Register a, long immOff, Register b) {
        emitString("st.global.f64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_u32(Register a, long immOff, Register b) {
        emitString("st.global.u32" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    public final void st_global_return_value_u64(Register a, long immOff, Register b) {
        emitString("st.global.u64" + " " + "[%r" + a.encoding() + " + " + immOff + "], %r" + b.encoding() + ";" + "");
    }

    // Subtract instruction

    public final void sub_f32(Register d, Register a, Register b) {
        emitString("sub.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void sub_f64(Register d, Register a, Register b) {
        emitString("sub.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void sub_s16(Register d, Register a, Register b) {
        emitString("sub.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void sub_s32(Register d, Register a, Register b) {
        emitString("sub.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void sub_s64(Register d, Register a, Register b) {
        emitString("sub.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void sub_s16(Register d, Register a, short s16) {
        emitString("sub.s16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s16 + ";" + "");
    }

    public final void sub_s32(Register d, Register a, int s32) {
        emitString("sub.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void sub_s64(Register d, Register a, int s32) {
        emitString("sub.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void sub_s64(Register d, Register a, long s64) {
        emitString("sub.s64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s64 + ";" + "");
    }

    public final void sub_f32(Register d, Register a, float f32) {
        emitString("sub.f32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + f32 + ";" + "");
    }

    public final void sub_f64(Register d, Register a, double f64) {
        emitString("sub.f64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + f64 + ";" + "");
    }

    public final void sub_s16(Register d, short s16, Register b) {
        emitString("sub.s16" + " " + "%r" + d.encoding() + ", " + s16 + ", %r" + b.encoding() + ";" + "");
    }

    public final void sub_s32(Register d, int s32, Register b) {
        emitString("sub.s32" + " " + "%r" + d.encoding() + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void sub_s64(Register d, long s64, Register b) {
        emitString("sub.s64" + " " + "%r" + d.encoding() + ", " + s64 + ", %r" + b.encoding() + ";" + "");
    }

    public final void sub_f32(Register d, float f32, Register b) {
        emitString("sub.f32" + " " + "%r" + d.encoding() + ", %r" + b.encoding() + ", " + f32 + ";" + "");
    }

    public final void sub_f64(Register d, double f64, Register b) {
        emitString("sub.f64" + " " + "%r" + d.encoding() + ", %r" + b.encoding() + ", " + f64 + ";" + "");
    }

    public final void sub_sat_s32(Register d, Register a, Register b) {
        emitString("sub.sat.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void sub_sat_s32(Register d, Register a, int s32) {
        emitString("sub.sat.s32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + s32 + ";" + "");
    }

    public final void sub_sat_s32(Register d, int s32, Register b) {
        emitString("sub.sat.s32" + " " + "%r" + d.encoding() + ", " + s32 + ", %r" + b.encoding() + ";" + "");
    }

    public final void xor_b16(Register d, Register a, Register b) {
        emitString("xor.b16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void xor_b32(Register d, Register a, Register b) {
        emitString("xor.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void xor_b64(Register d, Register a, Register b) {
        emitString("xor.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", %r" + b.encoding() + ";" + "");
    }

    public final void xor_b16(Register d, Register a, short b16) {
        emitString("xor.b16" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b16 + ";" + "");
    }

    public final void xor_b32(Register d, Register a, int b32) {
        emitString("xor.b32" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b32 + ";" + "");
    }

    public final void xor_b64(Register d, Register a, long b64) {
        emitString("xor.b64" + " " + "%r" + d.encoding() + ", %r" + a.encoding() + ", " + b64 + ";" + "");
    }

    @Override
    public PTXAddress makeAddress(Register base, int displacement) {
        return new PTXAddress(base, displacement);
    }

    @Override
    public PTXAddress getPlaceholder() {
        // TODO Auto-generated method stub
        return null;
    }
}
