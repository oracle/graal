/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.aarch64;

import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_PRE_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PRE_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.GE;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.GT;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.HI;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.LT;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ExtendType.UXTW;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSL;
import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r13;
import static jdk.vm.ci.aarch64.AArch64.r14;
import static jdk.vm.ci.aarch64.AArch64.r15;
import static jdk.vm.ci.aarch64.AArch64.r16;
import static jdk.vm.ci.aarch64.AArch64.r17;
import static jdk.vm.ci.aarch64.AArch64.r19;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r21;
import static jdk.vm.ci.aarch64.AArch64.r22;
import static jdk.vm.ci.aarch64.AArch64.r23;
import static jdk.vm.ci.aarch64.AArch64.r24;
import static jdk.vm.ci.aarch64.AArch64.r29;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L10893-L11122",
          sha1 = "a284abb98ee916b87c14835d069432df55d3ee1d")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L11192-L11334",
          sha1 = "ebc8f36cdb78789afe735764ba047a4dc840fcd2")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L11794-L11799",
          sha1 = "dd68de814a308fd93f7d1b32647b7fc22ae6f585")
// @formatter:on
public final class AArch64BigIntegerMontgomeryMultiplyOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64BigIntegerMontgomeryMultiplyOp> TYPE = LIRInstructionClass.create(AArch64BigIntegerMontgomeryMultiplyOp.class);

    @Use private Value aValue;
    @Use private Value bValue;
    @Use private Value nValue;
    @Use private Value lenValue;
    @Use private Value invValue;
    @Use private Value productValue;

    @Temp private Value[] temps;

    public AArch64BigIntegerMontgomeryMultiplyOp(Value aValue, Value bValue, Value nValue, Value lenValue, Value invValue, Value productValue) {
        super(TYPE);

        GraalError.guarantee(asRegister(aValue).equals(r0), "expect aValue at r0, but was %s", aValue);
        GraalError.guarantee(asRegister(bValue).equals(r1), "expect bValue at r1, but was %s", bValue);
        GraalError.guarantee(asRegister(nValue).equals(r2), "expect nValue at r2, but was %s", nValue);
        GraalError.guarantee(asRegister(lenValue).equals(r3), "expect lenValue at r3, but was %s", lenValue);
        GraalError.guarantee(asRegister(invValue).equals(r4), "expect invValue at r4, but was %s", invValue);
        GraalError.guarantee(asRegister(productValue).equals(r5), "expect productValue at r5, but was %s", productValue);

        this.aValue = aValue;
        this.bValue = bValue;
        this.nValue = nValue;
        this.lenValue = lenValue;
        this.invValue = invValue;
        this.productValue = productValue;

        this.temps = new Value[]{
                        r0.asValue(),
                        r1.asValue(),
                        r2.asValue(),
                        r3.asValue(),
                        r5.asValue(),
                        r6.asValue(),
                        r7.asValue(),
                        r11.asValue(),
                        r12.asValue(),
                        r13.asValue(),
                        r14.asValue(),
                        r15.asValue(),
                        r16.asValue(),
                        r17.asValue(),
                        r19.asValue(),
                        r20.asValue(),
                        r21.asValue(),
                        r22.asValue(),
                        r23.asValue(),
                        r24.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(aValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid aValue kind: %s", aValue);
        GraalError.guarantee(bValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid bValue kind: %s", bValue);
        GraalError.guarantee(nValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid nValue kind: %s", nValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(invValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid invValue kind: %s", invValue);
        GraalError.guarantee(productValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid productValue kind: %s", productValue);

        Register aInts = asRegister(aValue);
        Register bInts = asRegister(bValue);
        Register nInts = asRegister(nValue);
        Register lenInts = asRegister(lenValue);
        Register inv = asRegister(invValue);
        try (ScratchRegister scratchRegister1 = masm.getScratchRegister();
                        ScratchRegister scratchRegister2 = masm.getScratchRegister()) {
            Register rscratch1 = scratchRegister1.getRegister();
            Register rscratch2 = scratchRegister2.getRegister();
            emitMontgomeryMultiply(masm,
                            new Regs(aInts, bInts, nInts, asRegister(productValue), inv, lenInts,
                                            r6, r7, rscratch1, rscratch2,
                                            r24, r11, r12, r13,
                                            r14, r15, r16,
                                            r17, r19,
                                            r20, r21, r22, r23),
                            false);
        }
    }

    private interface Block {
        void emit();
    }

    record Regs(Register paBase, Register pbBase, Register pnBase, Register pmBase, Register inv, Register rlen,
                    Register ra, Register rb, Register rm, Register rn,
                    Register pa, Register pb, Register pm, Register pn,
                    Register t0, Register t1, Register t2,
                    Register ri, Register rj,
                    Register rhiAb, Register rloAb, Register rhiMn, Register rloMn) {
    }

    private static void unroll2(AArch64MacroAssembler masm, Register count, Block block) {
        Label loop = new Label();
        Label end = new Label();
        Label odd = new Label();
        masm.tbnz(count, 0, odd);
        masm.cbz(64, count, end);
        masm.align(16);
        masm.bind(loop);
        block.emit();
        masm.bind(odd);
        block.emit();
        masm.subs(64, count, count, 2);
        masm.branchConditionally(GT, loop);
        masm.bind(end);
    }

    private static void unroll2(AArch64MacroAssembler masm, Register count, Register d, Register s, Register tmp) {
        Label loop = new Label();
        Label end = new Label();
        Label odd = new Label();
        masm.tbnz(count, 0, odd);
        masm.cbz(64, count, end);
        masm.align(16);
        masm.bind(loop);
        reverse1(masm, d, s, tmp);
        masm.bind(odd);
        reverse1(masm, d, s, tmp);
        masm.subs(64, count, count, 2);
        masm.branchConditionally(GT, loop);
        masm.bind(end);
    }

    private static void enter(AArch64MacroAssembler masm) {
        masm.stp(64, r29, lr, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_PRE_INDEXED, sp, -16));
        masm.mov(64, r29, sp);
    }

    private static void leave(AArch64MacroAssembler masm) {
        masm.mov(64, sp, r29);
        masm.ldp(64, r29, lr, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_POST_INDEXED, sp, 16));
    }

    private static void stop(AArch64MacroAssembler masm, Regs r) {
        masm.mov(r.paBase(), 0L, false);
        masm.emitInt(0xd4bbd5c1); // dcps1 #0xdeae
    }

    private static void saveRegs(AArch64MacroAssembler masm, Regs r, boolean squaring) {
        int frameSize = 48;
        if (squaring) {
            masm.stp(64, r.pmBase(), r.rhiAb(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_PRE_INDEXED, sp, -frameSize));
            masm.stp(64, r.rloAb(), r.rhiMn(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, sp, 16));
            masm.stp(64, r.rloMn(), zr, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, sp, 32));
        } else {
            masm.stp(64, r.pmBase(), r.rj(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_PRE_INDEXED, sp, -frameSize));
            masm.stp(64, r.rhiAb(), r.rloAb(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, sp, 16));
            masm.stp(64, r.rhiMn(), r.rloMn(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, sp, 32));
        }
    }

    private static void restoreRegs(AArch64MacroAssembler masm, Regs r, boolean squaring) {
        int frameSize = 48;
        if (squaring) {
            masm.ldp(64, r.rloAb(), r.rhiMn(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, sp, 16));
            ldpToZr(masm, r.rloMn(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, sp, 32));
            masm.ldp(64, r.pmBase(), r.rhiAb(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_POST_INDEXED, sp, frameSize));
        } else {
            masm.ldp(64, r.rhiAb(), r.rloAb(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, sp, 16));
            masm.ldp(64, r.rhiMn(), r.rloMn(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, sp, 32));
            masm.ldp(64, r.pmBase(), r.rj(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_POST_INDEXED, sp, frameSize));
        }
    }

    private static void ldpToZr(AArch64MacroAssembler masm, Register rt, AArch64Address address) {
        assert address.getAddressingMode() == IMMEDIATE_PAIR_SIGNED_SCALED : Assertions.errorMessage(address);
        int encoding = 0xa9400000 |
                        address.getImmediate() << 15 |
                        zr.encoding << 10 |
                        address.getBase().encoding << 5 |
                        rt.encoding;
        masm.emitInt(encoding);
    }

    private static void pre1(AArch64MacroAssembler masm, Regs r, Register i) {
        // pre1
        // Pa = Pa_base;
        // Pb = Pb_base + i;
        // Pm = Pm_base;
        // Pn = Pn_base + i;
        // Ra = *Pa;
        // Rb = *Pb;
        // Rm = *Pm;
        // Rn = *Pn;
        masm.ldr(64, r.ra(), AArch64Address.createBaseRegisterOnlyAddress(64, r.paBase()));
        masm.ldr(64, r.rb(), AArch64Address.createExtendedRegisterOffsetAddress(64, r.pbBase(), i, true, UXTW));
        masm.ldr(64, r.rm(), AArch64Address.createBaseRegisterOnlyAddress(64, r.pmBase()));
        masm.ldr(64, r.rn(), AArch64Address.createExtendedRegisterOffsetAddress(64, r.pnBase(), i, true, UXTW));
        masm.sub(64, r.pa(), r.paBase(), 0);
        masm.add(64, r.pb(), r.pbBase(), i, UXTW, 3);
        masm.sub(64, r.pm(), r.pmBase(), 0);
        masm.add(64, r.pn(), r.pnBase(), i, UXTW, 3);

        // Zero the m*n result.
        masm.mov(64, r.rhiMn(), zr);
        masm.mov(64, r.rloMn(), zr);
    }

    /**
     * The core multiply-accumulate step of a Montgomery multiplication. The idea is to schedule
     * operations as a pipeline so that instructions with long latencies (loads and multiplies) have
     * time to complete before their results are used. This most benefits in-order implementations
     * of the architecture but out-of-order ones also benefit.
     */
    private static void step(AArch64MacroAssembler masm, Regs r) {
        // step
        // MACC(Ra, Rb, t0, t1, t2);
        // Ra = *++Pa;
        // Rb = *--Pb;
        masm.umulh(64, r.rhiAb(), r.ra(), r.rb());
        masm.mul(64, r.rloAb(), r.ra(), r.rb());
        masm.ldr(64, r.ra(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, r.pa(), 8));
        masm.ldr(64, r.rb(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, r.pb(), -8));
        acc(masm, r.rhiMn(), r.rloMn(), r.t0(), r.t1(), r.t2()); // The pending m*n from the
        // previous iteration.
        // MACC(Rm, Rn, t0, t1, t2);
        // Rm = *++Pm;
        // Rn = *--Pn;
        masm.umulh(64, r.rhiMn(), r.rm(), r.rn());
        masm.mul(64, r.rloMn(), r.rm(), r.rn());
        masm.ldr(64, r.rm(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, r.pm(), 8));
        masm.ldr(64, r.rn(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, r.pn(), -8));
        acc(masm, r.rhiAb(), r.rloAb(), r.t0(), r.t1(), r.t2());
    }

    private static void post1(AArch64MacroAssembler masm, Regs r) {
        // post1

        // MACC(Ra, Rb, t0, t1, t2);
        // Ra = *++Pa;
        // Rb = *--Pb;
        masm.umulh(64, r.rhiAb(), r.ra(), r.rb());
        masm.mul(64, r.rloAb(), r.ra(), r.rb());
        acc(masm, r.rhiMn(), r.rloMn(), r.t0(), r.t1(), r.t2());  // The pending m*n
        acc(masm, r.rhiAb(), r.rloAb(), r.t0(), r.t1(), r.t2());

        // *Pm = Rm = t0 * inv;
        masm.mul(64, r.rm(), r.t0(), r.inv());
        masm.str(64, r.rm(), AArch64Address.createBaseRegisterOnlyAddress(64, r.pm()));

        // MACC(Rm, Rn, t0, t1, t2);
        // t0 = t1; t1 = t2; t2 = 0;
        masm.umulh(64, r.rhiMn(), r.rm(), r.rn());

        // We have very carefully set things up so that
        // m[i]*n[0] + t0 == 0 (mod b), so we don't have to calculate
        // the lower half of Rm * Rn because we know the result already:
        // it must be -t0. t0 + (-t0) must generate a carry iff
        // t0 != 0. So, rather than do a mul and an adds we just set
        // the carry flag iff t0 is nonzero.
        //
        // mul(Rlo_mn, Rm, Rn);
        // adds(zr, t0, Rlo_mn);
        masm.subs(64, zr, r.t0(), 1); // Set carry iff t0 is nonzero
        masm.adcs(64, r.t0(), r.t1(), r.rhiMn());
        masm.adc(64, r.t1(), r.t2(), zr);
        masm.mov(64, r.t2(), zr);
    }

    private static void pre2(AArch64MacroAssembler masm, Regs r, Register i, Register len) {
        // pre2
        // Pa = Pa_base + i-len;
        // Pb = Pb_base + len;
        // Pm = Pm_base + i-len;
        // Pn = Pn_base + len;

        masm.sub(64, r.rj(), i, len);
        // Rj == i-len

        masm.add(64, r.pa(), r.paBase(), r.rj(), UXTW, 3);
        masm.add(64, r.pb(), r.pbBase(), len, UXTW, 3);
        masm.add(64, r.pm(), r.pmBase(), r.rj(), UXTW, 3);
        masm.add(64, r.pn(), r.pnBase(), len, UXTW, 3);

        // Ra = *++Pa;
        // Rb = *--Pb;
        // Rm = *++Pm;
        // Rn = *--Pn;
        masm.ldr(64, r.ra(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, r.pa(), 8));
        masm.ldr(64, r.rb(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, r.pb(), -8));
        masm.ldr(64, r.rm(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, r.pm(), 8));
        masm.ldr(64, r.rn(), AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, r.pn(), -8));

        masm.mov(64, r.rhiMn(), zr);
        masm.mov(64, r.rloMn(), zr);
    }

    private static void post2(AArch64MacroAssembler masm, Regs r, Register i, Register len) {
        // post2
        masm.sub(64, r.rj(), i, len);

        masm.adds(64, r.t0(), r.t0(), r.rloMn()); // The pending m*n, low part

        // As soon as we know the least significant digit of our result,
        // store it.
        // Pm_base[i-len] = t0;
        masm.str(64, r.t0(), AArch64Address.createExtendedRegisterOffsetAddress(64, r.pmBase(), r.rj(), true, UXTW));

        // t0 = t1; t1 = t2; t2 = 0;
        masm.adcs(64, r.t0(), r.t1(), r.rhiMn()); // The pending m*n, high part
        masm.adc(64, r.t1(), r.t2(), zr);
        masm.mov(64, r.t2(), zr);
    }

    /**
     * A carry in t0 after Montgomery multiplication means that we should subtract multiples of n
     * from our result in m. We'll keep doing that until there is no carry.
     */
    private static void normalize(AArch64MacroAssembler masm, Regs r, Register len) {
        // normalize
        // while (t0)
        // t0 = sub(Pm_base, Pn_base, t0, len);
        Label loop = new Label();
        Label post = new Label();
        Label again = new Label();
        Register cnt = r.t1();
        Register i = r.t2(); // Re-use registers; we're done with them now
        masm.cbz(64, r.t0(), post);
        masm.bind(again);
        masm.mov(64, i, zr);
        masm.mov(64, cnt, len);
        masm.ldr(64, r.rm(), AArch64Address.createExtendedRegisterOffsetAddress(64, r.pmBase(), i, true, UXTW));
        masm.ldr(64, r.rn(), AArch64Address.createExtendedRegisterOffsetAddress(64, r.pnBase(), i, true, UXTW));
        masm.subs(64, zr, zr, zr); // set carry flag, i.e. no borrow
        masm.align(16);
        masm.bind(loop);
        masm.sbcs(64, r.rm(), r.rm(), r.rn());
        masm.str(64, r.rm(), AArch64Address.createExtendedRegisterOffsetAddress(64, r.pmBase(), i, true, UXTW));
        masm.add(64, i, i, 1);
        masm.ldr(64, r.rm(), AArch64Address.createExtendedRegisterOffsetAddress(64, r.pmBase(), i, true, UXTW));
        masm.ldr(64, r.rn(), AArch64Address.createExtendedRegisterOffsetAddress(64, r.pnBase(), i, true, UXTW));
        masm.sub(64, cnt, cnt, 1);
        masm.cbnz(64, cnt, loop);
        masm.sbc(64, r.t0(), r.t0(), zr);
        masm.cbnz(64, r.t0(), again);
        masm.bind(post);
    }

    /**
     * Move memory at s to d, reversing words.
     * <p>
     * Increments d to end of copied memory. Destroys tmp1, tmp2. Preserves len. Leaves s pointing
     * to the address which was in d at start.
     */
    private static void reverse(AArch64MacroAssembler masm, Register d, Register s, Register len, Register tmp1, Register tmp2) {
        masm.add(64, s, s, len, UXTW, 3);
        masm.mov(64, tmp1, len);
        unroll2(masm, tmp1, d, s, tmp2);
        masm.sub(64, s, d, len, UXTW, 3);
    }

    /**
     * Reverses one 64-bit word while copying.
     */
    private static void reverse1(AArch64MacroAssembler masm, Register d, Register s, Register tmp) {
        masm.ldr(64, tmp, AArch64Address.createImmediateAddress(64, IMMEDIATE_PRE_INDEXED, s, -8));
        masm.ror(64, tmp, tmp, 32);
        masm.str(64, tmp, AArch64Address.createImmediateAddress(64, IMMEDIATE_POST_INDEXED, d, 8));
    }

    private static void acc(AArch64MacroAssembler masm, Register rhi, Register rlo, Register t0, Register t1, Register t2) {
        masm.adds(64, t0, t0, rlo);
        masm.adcs(64, t1, t1, rhi);
        masm.adc(64, t2, t2, zr);
    }

    /**
     * Fast Montgomery multiplication. The derivation of the algorithm is in A Cryptographic Library
     * for the Motorola DSP56000, Dusse and Kaliski, Proc. EUROCRYPT 90, pp. 230-237.
     *
     * Arguments:
     *
     * Inputs for multiplication: c_rarg0 - int array elements a c_rarg1 - int array elements b
     * c_rarg2 - int array elements n (the modulus) c_rarg3 - int length c_rarg4 - int inv c_rarg5 -
     * int array elements m (the result)
     *
     * Inputs for squaring: c_rarg0 - int array elements a c_rarg1 - int array elements n (the
     * modulus) c_rarg2 - int length c_rarg3 - int inv c_rarg4 - int array elements m (the result)
     *
     */
    static void emitMontgomeryMultiply(AArch64MacroAssembler masm, Regs r, boolean squaring) {
        Label argh = new Label();
        Label nothing = new Label();

        masm.cbz(32, r.rlen(), nothing);

        enter(masm);

        // Make room.
        masm.compare(32, r.rlen(), 512);
        masm.branchConditionally(HI, argh);
        masm.sub(64, r.ra(), sp, r.rlen(), UXTW, 4);
        masm.and(64, sp, r.ra(), -16);

        masm.lsr(32, r.rlen(), r.rlen(), 1);  // length in longwords = len/2

        // Copy input args, reversing as we go. We use Ra as a
        // temporary variable.
        reverse(masm, r.ra(), r.paBase(), r.rlen(), r.t0(), r.t1());
        if (!squaring) {
            reverse(masm, r.ra(), r.pbBase(), r.rlen(), r.t0(), r.t1());
        }
        reverse(masm, r.ra(), r.pnBase(), r.rlen(), r.t0(), r.t1());

        // Push all call-saved registers and also Pm_base which we'll need
        // at the end.
        saveRegs(masm, r, squaring);

        masm.mov(64, r.pmBase(), r.ra());

        masm.mov(64, r.t0(), zr);
        masm.mov(64, r.t1(), zr);
        masm.mov(64, r.t2(), zr);

        // for (int i = 0; i < len; i++) {
        masm.mov(64, r.ri(), zr);
        Label loop1 = new Label();
        Label end1 = new Label();
        masm.cmp(32, r.ri(), r.rlen());
        masm.branchConditionally(GE, end1);

        masm.bind(loop1);
        pre1(masm, r, r.ri());

        // for (j = i; j; j--) {
        masm.mov(32, r.rj(), r.ri());
        unroll2(masm, r.rj(), () -> step(masm, r));
        // } // j

        post1(masm, r);
        masm.add(32, r.ri(), r.ri(), 1);
        masm.cmp(32, r.ri(), r.rlen());
        masm.branchConditionally(LT, loop1);
        masm.bind(end1);
        // } // i

        // for (int i = len; i < 2*len; i++) {
        masm.mov(64, r.ri(), r.rlen());
        Label loop2 = new Label();
        Label end2 = new Label();
        masm.subs(32, zr, r.ri(), r.rlen(), LSL, 1);
        masm.branchConditionally(GE, end2);

        masm.bind(loop2);
        pre2(masm, r, r.ri(), r.rlen());

        // for (j = len*2-i-1; j; j--) {
        masm.lsl(32, r.rj(), r.rlen(), 1);
        masm.sub(32, r.rj(), r.rj(), r.ri());
        masm.sub(32, r.rj(), r.rj(), 1);
        unroll2(masm, r.rj(), () -> step(masm, r));
        // } // j

        post2(masm, r, r.ri(), r.rlen());
        masm.add(32, r.ri(), r.ri(), 1);
        masm.subs(32, zr, r.ri(), r.rlen(), LSL, 1);
        masm.branchConditionally(LT, loop2);
        masm.bind(end2);
        // } // i

        normalize(masm, r, r.rlen());

        masm.mov(64, r.ra(), r.pmBase());  // Save Pm_base in Ra
        restoreRegs(masm, r, squaring);  // Restore caller's Pm_base

        // Copy our result into caller's Pm_base
        reverse(masm, r.pmBase(), r.ra(), r.rlen(), r.t0(), r.t1());

        leave(masm);
        masm.jmp(nothing);
        masm.bind(argh);
        stop(masm, r);
        masm.bind(nothing);
    }

}
