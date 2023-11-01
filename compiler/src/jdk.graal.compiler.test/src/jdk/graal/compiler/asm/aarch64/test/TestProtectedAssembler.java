/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.graal.compiler.asm.aarch64.test;

import jdk.graal.compiler.asm.AbstractAddress;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

/**
 * Cheat so that we can test protected functions of assembler.
 */
class TestProtectedAssembler extends AArch64Assembler {

    public final TestProtectedASIMDAssembler neon;

    TestProtectedAssembler(TargetDescription target) {
        super(target);
        this.neon = new TestProtectedASIMDAssembler(this);
    }

    @Override
    protected void cbnz(int size, Register reg, int imm21, int pos) {
        super.cbnz(size, reg, imm21, pos);
    }

    @Override
    protected void cbz(int size, Register reg, int imm21, int pos) {
        super.cbz(size, reg, imm21, pos);
    }

    @Override
    protected void b(ConditionFlag condition, int imm21) {
        super.b(condition, imm21);
    }

    @Override
    protected void b(ConditionFlag condition, int imm21, int pos) {
        super.b(condition, imm21, pos);
    }

    @Override
    protected void cbnz(int size, Register reg, int imm21) {
        super.cbnz(size, reg, imm21);
    }

    @Override
    protected void cbz(int size, Register reg, int imm21) {
        super.cbz(size, reg, imm21);
    }

    @Override
    protected void b() {
        super.b();
    }

    @Override
    protected void b(int imm28, int pos) {
        super.b(imm28, pos);
    }

    @Override
    protected void br(Register reg) {
        super.br(reg);
    }

    @Override
    protected void ldxr(int size, Register rt, Register rn) {
        super.ldxr(size, rt, rn);
    }

    @Override
    protected void stxr(int size, Register rs, Register rt, Register rn) {
        super.stxr(size, rs, rt, rn);
    }

    @Override
    protected void add(int size, Register dst, Register src, int aimm) {
        super.add(size, dst, src, aimm);
    }

    @Override
    protected void adds(int size, Register dst, Register src, int aimm) {
        super.adds(size, dst, src, aimm);
    }

    @Override
    protected void sub(int size, Register dst, Register src, int aimm) {
        super.sub(size, dst, src, aimm);
    }

    @Override
    protected void subs(int size, Register dst, Register src, int aimm) {
        super.subs(size, dst, src, aimm);
    }

    @Override
    protected void movz(int size, Register dst, int uimm16, int shiftAmt) {
        super.movz(size, dst, uimm16, shiftAmt);
    }

    @Override
    protected void movn(int size, Register dst, int uimm16, int shiftAmt) {
        super.movn(size, dst, uimm16, shiftAmt);
    }

    @Override
    protected void movk(int size, Register dst, int uimm16, int pos) {
        super.movk(size, dst, uimm16, pos);
    }

    @Override
    protected void add(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        super.add(size, dst, src1, src2, shiftType, imm);
    }

    @Override
    protected void sub(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        super.sub(size, dst, src1, src2, shiftType, imm);
    }

    @Override
    protected void adds(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        super.adds(size, dst, src1, src2, extendType, shiftAmt);
    }

    @Override
    protected void ands(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        super.ands(size, dst, src1, src2, shiftType, shiftAmt);
    }

    @Override
    protected void bics(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        super.bics(size, dst, src1, src2, shiftType, shiftAmt);
    }

    @Override
    protected void rorv(int size, Register dst, Register src1, Register src2) {
        super.rorv(size, dst, src1, src2);
    }

    @Override
    protected void cls(int size, Register dst, Register src) {
        super.cls(size, dst, src);
    }

    @Override
    protected void fmovFpu2Cpu(int size, Register dst, Register src) {
        super.fmovFpu2Cpu(size, dst, src);
    }

    @Override
    protected void fmovCpu2Fpu(int size, Register dst, Register src) {
        super.fmovCpu2Fpu(size, dst, src);
    }

    @Override
    protected void fmov(int size, Register dst, double imm) {
        super.fmov(size, dst, imm);
    }

    @Override
    protected void fmsub(int size, Register dst, Register src1, Register src2, Register src3) {
        super.fmsub(size, dst, src1, src2, src3);
    }

    @Override
    protected void hlt(int uimm16) {
        super.hlt(uimm16);
    }

    @Override
    protected void hint(SystemHint hint) {
        super.hint(hint);
    }

    @Override
    protected void clrex() {
        super.clrex();
    }

    @Override
    public void align(int modulus) {
    }

    @Override
    public void halt() {
    }

    @Override
    public void jmp(Label l) {
    }

    @Override
    protected void patchJumpTarget(int branch, int jumpTarget) {

    }

    @Override
    public AbstractAddress makeAddress(int transferSize, Register base, int displacement) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AbstractAddress getPlaceholder(int instructionStartPosition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void ensureUniquePC() {
        throw new UnsupportedOperationException();
    }

    public static class TestProtectedASIMDAssembler extends AArch64ASIMDAssembler {

        protected TestProtectedASIMDAssembler(AArch64Assembler asm) {
            super(asm);
        }
    }
}
