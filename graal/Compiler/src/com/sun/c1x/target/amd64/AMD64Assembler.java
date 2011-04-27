/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.target.amd64;

import static com.sun.c1x.target.amd64.AMD64.*;
import static com.sun.cri.bytecode.Bytecodes.MemoryBarriers.*;
import static com.sun.cri.ci.CiKind.*;

import com.sun.c1x.*;
import com.sun.c1x.asm.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * This class implements an assembler that can encode most X86 instructions.
 *
 * @author Thomas Wuerthinger
 */
public class AMD64Assembler extends AbstractAssembler {

    private static final int MinEncodingNeedsRex = 8;

    /**
     * The x86 condition codes used for conditional jumps/moves.
     */
    public enum ConditionFlag {
        zero(0x4),
        notZero(0x5),
        equal(0x4),
        notEqual(0x5),
        less(0xc),
        lessEqual(0xe),
        greater(0xf),
        greaterEqual(0xd),
        below(0x2),
        belowEqual(0x6),
        above(0x7),
        aboveEqual(0x3),
        overflow(0x0),
        noOverflow(0x1),
        carrySet(0x2),
        carryClear(0x3),
        negative(0x8),
        positive(0x9),
        parity(0xa),
        noParity(0xb);

        public final int value;

        private ConditionFlag(int value) {
            this.value = value;
        }

        public static final ConditionFlag[] values = values();
    }

    /**
     * Constants for X86 prefix bytes.
     */
    private class Prefix {
        private static final int REX = 0x40;
        private static final int REXB = 0x41;
        private static final int REXX = 0x42;
        private static final int REXXB = 0x43;
        private static final int REXR = 0x44;
        private static final int REXRB = 0x45;
        private static final int REXRX = 0x46;
        private static final int REXRXB = 0x47;
        private static final int REXW = 0x48;
        private static final int REXWB = 0x49;
        private static final int REXWX = 0x4A;
        private static final int REXWXB = 0x4B;
        private static final int REXWR = 0x4C;
        private static final int REXWRB = 0x4D;
        private static final int REXWRX = 0x4E;
        private static final int REXWRXB = 0x4F;
    }

    /**
     * The register to which {@link CiRegister#Frame} and {@link CiRegister#CallerFrame} are bound.
     */
    public final CiRegister frameRegister;

    /**
     * Constructs an assembler for the AMD64 architecture.
     *
     * @param registerConfig the register configuration used to bind {@link CiRegister#Frame} and
     *            {@link CiRegister#CallerFrame} to physical registers. This value can be null if this assembler
     *            instance will not be used to assemble instructions using these logical registers.
     */
    public AMD64Assembler(CiTarget target, RiRegisterConfig registerConfig) {
        super(target);
        this.frameRegister = registerConfig == null ? null : registerConfig.getFrameRegister();
    }

    private static int encode(CiRegister r) {
        assert r.encoding < 16 && r.encoding >= 0 : "encoding out of range: " + r.encoding;
        return r.encoding & 0x7;
    }

    private void emitArithB(int op1, int op2, CiRegister dst, int imm8) {
        assert dst.isByte() : "must have byte register";
        assert Util.isUByte(op1) && Util.isUByte(op2) : "wrong opcode";
        assert Util.isUByte(imm8) : "not a byte";
        assert (op1 & 0x01) == 0 : "should be 8bit operation";
        emitByte(op1);
        emitByte(op2 | encode(dst));
        emitByte(imm8);
    }

    private void emitArith(int op1, int op2, CiRegister dst, int imm32) {
        assert Util.isUByte(op1) && Util.isUByte(op2) : "wrong opcode";
        assert (op1 & 0x01) == 1 : "should be 32bit operation";
        assert (op1 & 0x02) == 0 : "sign-extension bit should not be set";
        if (Util.isByte(imm32)) {
            emitByte(op1 | 0x02); // set sign bit
            emitByte(op2 | encode(dst));
            emitByte(imm32 & 0xFF);
        } else {
            emitByte(op1);
            emitByte(op2 | encode(dst));
            emitInt(imm32);
        }
    }

    // immediate-to-memory forms
    private void emitArithOperand(int op1, CiRegister rm, CiAddress adr, int imm32) {
        assert (op1 & 0x01) == 1 : "should be 32bit operation";
        assert (op1 & 0x02) == 0 : "sign-extension bit should not be set";
        if (Util.isByte(imm32)) {
            emitByte(op1 | 0x02); // set sign bit
            emitOperandHelper(rm, adr);
            emitByte(imm32 & 0xFF);
        } else {
            emitByte(op1);
            emitOperandHelper(rm, adr);
            emitInt(imm32);
        }
    }

    private void emitArith(int op1, int op2, CiRegister dst, CiRegister src) {
        assert Util.isUByte(op1) && Util.isUByte(op2) : "wrong opcode";
        emitByte(op1);
        emitByte(op2 | encode(dst) << 3 | encode(src));
    }

    private void emitOperandHelper(CiRegister reg, CiAddress addr) {
        CiRegister base = addr.base();

        CiRegister index = addr.index();
        CiAddress.Scale scale = addr.scale;
        int disp = addr.displacement;

        if (base == CiRegister.Frame) {
            assert frameRegister != null : "cannot use register " + CiRegister.Frame + " in assembler with null register configuration";
            base = frameRegister;
        } else if (base == CiRegister.CallerFrame) {
            assert frameRegister != null : "cannot use register " + CiRegister.Frame + " in assembler with null register configuration";
            base = frameRegister;
            disp += targetMethod.frameSize() + 8;
        }

        // Encode the registers as needed in the fields they are used in

        assert reg != CiRegister.None;

        int regenc = encode(reg) << 3;
        int indexenc = index.isValid() ? encode(index) << 3 : 0;
        int baseenc = base.isValid() ? encode(base) : 0;

        if (base.isValid()) {
            if (index.isValid()) {
                // [base + indexscale + disp]
                if (disp == 0 && base != rbp && (base != r13)) {
                    // [base + indexscale]
                    // [00 reg 100][ss index base]
                    assert index != rsp : "illegal addressing mode";
                    emitByte(0x04 | regenc);
                    emitByte(scale.log2 << 6 | indexenc | baseenc);
                } else if (Util.isByte(disp)) {
                    // [base + indexscale + imm8]
                    // [01 reg 100][ss index base] imm8
                    assert index != rsp : "illegal addressing mode";
                    emitByte(0x44 | regenc);
                    emitByte(scale.log2 << 6 | indexenc | baseenc);
                    emitByte(disp & 0xFF);
                } else {
                    // [base + indexscale + disp32]
                    // [10 reg 100][ss index base] disp32
                    assert index != rsp : "illegal addressing mode";
                    emitByte(0x84 | regenc);
                    emitByte(scale.log2 << 6 | indexenc | baseenc);
                    emitInt(disp);
                }
            } else if (base == rsp || (base == r12)) {
                // [rsp + disp]
                if (disp == 0) {
                    // [rsp]
                    // [00 reg 100][00 100 100]
                    emitByte(0x04 | regenc);
                    emitByte(0x24);
                } else if (Util.isByte(disp)) {
                    // [rsp + imm8]
                    // [01 reg 100][00 100 100] disp8
                    emitByte(0x44 | regenc);
                    emitByte(0x24);
                    emitByte(disp & 0xFF);
                } else {
                    // [rsp + imm32]
                    // [10 reg 100][00 100 100] disp32
                    emitByte(0x84 | regenc);
                    emitByte(0x24);
                    emitInt(disp);
                }
            } else {
                // [base + disp]
                assert base != rsp && (base != r12) : "illegal addressing mode";
                if (disp == 0 && base != rbp && (base != r13)) {
                    // [base]
                    // [00 reg base]
                    emitByte(0x00 | regenc | baseenc);
                } else if (Util.isByte(disp)) {
                    // [base + disp8]
                    // [01 reg base] disp8
                    emitByte(0x40 | regenc | baseenc);
                    emitByte(disp & 0xFF);
                } else {
                    // [base + disp32]
                    // [10 reg base] disp32
                    emitByte(0x80 | regenc | baseenc);
                    emitInt(disp);
                }
            }
        } else {
            if (index.isValid()) {
                // [indexscale + disp]
                // [00 reg 100][ss index 101] disp32
                assert index != rsp : "illegal addressing mode";
                emitByte(0x04 | regenc);
                emitByte(scale.log2 << 6 | indexenc | 0x05);
                emitInt(disp);
            } else if (base == CiRegister.InstructionRelative) {
                // Adjust disp which is currently relative to the start of the instruction
                int instrStart = codeBuffer.mark();
                assert instrStart >= 0;
                int instrSize = (codeBuffer.position() - instrStart) + 5;
                disp = disp - instrSize;
                // [00 000 101] disp32
                emitByte(0x05 | regenc);
                emitInt(disp);
            } else if (addr == CiAddress.Placeholder) {
                // [00 000 101] disp32
                emitByte(0x05 | regenc);
                emitInt(0);
            } else {
                // [disp] ABSOLUTE
                // [00 reg 100][00 100 101] disp32
                emitByte(0x04 | regenc);
                emitByte(0x25);
                emitInt(disp);
            }
        }
    }

    public final void addl(CiAddress dst, int imm32) {
        prefix(dst);
        emitArithOperand(0x81, rax, dst, imm32);
    }

    public final void addl(CiAddress dst, CiRegister src) {
        prefix(dst, src);
        emitByte(0x01);
        emitOperandHelper(src, dst);
    }

    public final void addl(CiRegister dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xC0, dst, imm32);
    }

    public final void addl(CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x03);
        emitOperandHelper(dst, src);
    }

    public final void addl(CiRegister dst, CiRegister src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x03, 0xC0, dst, src);
    }

    private void addrNop4() {
        // 4 bytes: NOP DWORD PTR [EAX+0]
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x40); // emitRm(cbuf, 0x1, EAXEnc, EAXEnc);
        emitByte(0); // 8-bits offset (1 byte)
    }

    private void addrNop5() {
        // 5 bytes: NOP DWORD PTR [EAX+EAX*0+0] 8-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x44); // emitRm(cbuf, 0x1, EAXEnc, 0x4);
        emitByte(0x00); // emitRm(cbuf, 0x0, EAXEnc, EAXEnc);
        emitByte(0); // 8-bits offset (1 byte)
    }

    private void addrNop7() {
        // 7 bytes: NOP DWORD PTR [EAX+0] 32-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x80); // emitRm(cbuf, 0x2, EAXEnc, EAXEnc);
        emitInt(0); // 32-bits offset (4 bytes)
    }

    private void addrNop8() {
        // 8 bytes: NOP DWORD PTR [EAX+EAX*0+0] 32-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x84); // emitRm(cbuf, 0x2, EAXEnc, 0x4);
        emitByte(0x00); // emitRm(cbuf, 0x0, EAXEnc, EAXEnc);
        emitInt(0); // 32-bits offset (4 bytes)
    }

    public final void addsd(CiRegister dst, CiRegister src) {
        assert dst.isFpu() && src.isFpu();
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x58);
        emitByte(0xC0 | encode);
    }

    public final void addsd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x58);
        emitOperandHelper(dst, src);
    }

    public final void addss(CiRegister dst, CiRegister src) {
        assert dst.isFpu() && src.isFpu();
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x58);
        emitByte(0xC0 | encode);
    }

    public final void addss(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x58);
        emitOperandHelper(dst, src);
    }

    public final void andl(CiRegister dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xE0, dst, imm32);
    }

    public final void andl(CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x23);
        emitOperandHelper(dst, src);
    }

    public final void andl(CiRegister dst, CiRegister src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x23, 0xC0, dst, src);
    }

    public final void andpd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x54);
        emitOperandHelper(dst, src);
    }

    public final void bsfq(CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBC);
        emitByte(0xC0 | encode);
    }

    public final void bsfq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0xBC);
        emitOperandHelper(dst, src);
    }

    public final void bsrq(CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBD);
        emitByte(0xC0 | encode);
    }


    public final void bsrq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0xBD);
        emitOperandHelper(dst, src);
    }

    public final void bswapl(CiRegister reg) { // bswap
        int encode = prefixAndEncode(reg.encoding);
        emitByte(0x0F);
        emitByte(0xC8 | encode);
    }

    public final void btli(CiAddress src, int imm8) {
        prefixq(src);
        emitByte(0x0F);
        emitByte(0xBA);
        emitOperandHelper(rsp, src);
        emitByte(imm8);
    }

    public final void nativeCall(CiRegister dst, String symbol, LIRDebugInfo info) {
        int before = codeBuffer.position();
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xD0 | encode);
        int after = codeBuffer.position();
        recordIndirectCall(before, after, symbol, info);
        recordExceptionHandlers(after, info);
    }

    public final int directCall(Object target, LIRDebugInfo info) {
        int before = codeBuffer.position();
        emitByte(0xE8);
        emitInt(0);
        int after = codeBuffer.position();
        recordDirectCall(before, after, target, info);
        recordExceptionHandlers(after, info);
        return before;
    }

    public final int directJmp(Object target) {
        int before = codeBuffer.position();
        emitByte(0xE9);
        emitInt(0);
        int after = codeBuffer.position();
        recordDirectCall(before, after, target, null);
        return before;
    }

    public final int indirectCall(CiRegister dst, Object target, LIRDebugInfo info) {
        int before = codeBuffer.position();
        int encode = prefixAndEncode(dst.encoding);

        emitByte(0xFF);
        emitByte(0xD0 | encode);
        int after = codeBuffer.position();
        recordIndirectCall(before, after, target, info);
        recordExceptionHandlers(after, info);
        return before;
    }

    public final void cdql() {
        emitByte(0x99);
    }

    public final void cmovl(ConditionFlag cc, CiRegister dst, CiRegister src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x40 | cc.value);
        emitByte(0xC0 | encode);
    }

    public final void cmovl(ConditionFlag cc, CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.value);
        emitOperandHelper(dst, src);
    }

    public final void cmpb(CiAddress dst, int imm8) {
        prefix(dst);
        emitByte(0x80);
        emitOperandHelper(rdi, dst);
        emitByte(imm8);
    }

    public final void cmpl(CiAddress dst, int imm32) {
        prefix(dst);
        emitByte(0x81);
        emitOperandHelper(rdi, dst);
        emitInt(imm32);
    }

    public final void cmpl(CiRegister dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xF8, dst, imm32);
    }

    public final void cmpl(CiRegister dst, CiRegister src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x3B, 0xC0, dst, src);
    }

    public final void cmpl(CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x3B);
        emitOperandHelper(dst, src);
    }

    // The 32-bit cmpxchg compares the value at adr with the contents of X86.rax,
    // and stores reg into adr if so; otherwise, the value at adr is loaded into X86.rax,.
    // The ZF is set if the compared values were equal, and cleared otherwise.
    public final void cmpxchgl(CiRegister reg, CiAddress adr) { // cmpxchg
        if ((C1XOptions.Atomics & 2) != 0) {
            // caveat: no instructionmark, so this isn't relocatable.
            // Emit a synthetic, non-atomic, CAS equivalent.
            // Beware. The synthetic form sets all ICCs, not just ZF.
            // cmpxchg r,[m] is equivalent to X86.rax, = CAS (m, X86.rax, r)
            cmpl(rax, adr);
            movl(rax, adr);
            if (reg != rax) {
                Label l = new Label();
                jcc(ConditionFlag.notEqual, l);
                movl(adr, reg);
                bind(l);
            }
        } else {

            prefix(adr, reg);
            emitByte(0x0F);
            emitByte(0xB1);
            emitOperandHelper(reg, adr);
        }
    }

    public final void comisd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        // NOTE: dbx seems to decode this as comiss even though the
        // 0x66 is there. Strangly ucomisd comes out correct
        emitByte(0x66);
        comiss(dst, src);
    }

    public final void comiss(CiRegister dst, CiAddress src) {
        assert dst.isFpu();

        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x2F);
        emitOperandHelper(dst, src);
    }

    public final void cvtdq2pd(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();

        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xE6);
        emitByte(0xC0 | encode);
    }

    public final void cvtdq2ps(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5B);
        emitByte(0xC0 | encode);
    }

    public final void cvtsd2ss(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5A);
        emitByte(0xC0 | encode);
    }

    public final void cvtsi2sdl(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2A);
        emitByte(0xC0 | encode);
    }

    public final void cvtsi2ssl(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2A);
        emitByte(0xC0 | encode);
    }

    public final void cvtss2sd(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5A);
        emitByte(0xC0 | encode);
    }

    public final void cvttsd2sil(CiRegister dst, CiRegister src) {
        assert src.isFpu();
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2C);
        emitByte(0xC0 | encode);
    }

    public final void cvttss2sil(CiRegister dst, CiRegister src) {
        assert src.isFpu();
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2C);
        emitByte(0xC0 | encode);
    }

    public final void decl(CiAddress dst) {
        // Don't use it directly. Use Macrodecrement() instead.
        prefix(dst);
        emitByte(0xFF);
        emitOperandHelper(rcx, dst);
    }

    public final void divsd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x5E);
        emitOperandHelper(dst, src);
    }

    public final void divsd(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5E);
        emitByte(0xC0 | encode);
    }

    public final void divss(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x5E);
        emitOperandHelper(dst, src);
    }

    public final void divss(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5E);
        emitByte(0xC0 | encode);
    }

    public final void hlt() {
        emitByte(0xF4);
    }

    public final void idivl(CiRegister src) {
        int encode = prefixAndEncode(src.encoding);
        emitByte(0xF7);
        emitByte(0xF8 | encode);
    }

    public final void imull(CiRegister dst, CiRegister src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xAF);
        emitByte(0xC0 | encode);
    }

    public final void imull(CiRegister dst, CiRegister src, int value) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        if (Util.isByte(value)) {
            emitByte(0x6B);
            emitByte(0xC0 | encode);
            emitByte(value);
        } else {
            emitByte(0x69);
            emitByte(0xC0 | encode);
            emitInt(value);
        }
    }

    public final void incl(CiAddress dst) {
        // Don't use it directly. Use Macroincrement() instead.
        prefix(dst);
        emitByte(0xFF);
        emitOperandHelper(rax, dst);
    }

    public final void jcc(ConditionFlag cc, int target, boolean forceDisp32) {
        int shortSize = 2;
        int longSize = 6;
        long disp = target - codeBuffer.position();
        if (!forceDisp32 && Util.isByte(disp - shortSize)) {
            // 0111 tttn #8-bit disp
            emitByte(0x70 | cc.value);
            emitByte((int) ((disp - shortSize) & 0xFF));
        } else {
            // 0000 1111 1000 tttn #32-bit disp
            assert Util.isInt(disp - longSize) : "must be 32bit offset (call4)";
            emitByte(0x0F);
            emitByte(0x80 | cc.value);
            emitInt((int) (disp - longSize));
        }
    }

    public final void jcc(ConditionFlag cc, Label l) {
        assert (0 <= cc.value) && (cc.value < 16) : "illegal cc";
        if (l.isBound()) {
            jcc(cc, l.position(), false);
        } else {
            // Note: could eliminate cond. jumps to this jump if condition
            // is the same however, seems to be rather unlikely case.
            // Note: use jccb() if label to be bound is very close to get
            // an 8-bit displacement
            l.addPatchAt(codeBuffer.position());
            emitByte(0x0F);
            emitByte(0x80 | cc.value);
            emitInt(0);
        }

    }

    public final void jccb(ConditionFlag cc, Label l) {
        if (l.isBound()) {
            int shortSize = 2;
            int entry = l.position();
            assert Util.isByte(entry - (codeBuffer.position() + shortSize)) : "Dispacement too large for a short jmp";
            long disp = entry - codeBuffer.position();
            // 0111 tttn #8-bit disp
            emitByte(0x70 | cc.value);
            emitByte((int) ((disp - shortSize) & 0xFF));
        } else {

            l.addPatchAt(codeBuffer.position());
            emitByte(0x70 | cc.value);
            emitByte(0);
        }
    }

    public final void jmp(CiAddress adr) {
        prefix(adr);
        emitByte(0xFF);
        emitOperandHelper(rsp, adr);
    }

    public final void jmp(int target, boolean forceDisp32) {
        int shortSize = 2;
        int longSize = 5;
        long disp = target - codeBuffer.position();
        if (!forceDisp32 && Util.isByte(disp - shortSize)) {
            emitByte(0xEB);
            emitByte((int) ((disp - shortSize) & 0xFF));
        } else {
            emitByte(0xE9);
            emitInt((int) (disp - longSize));
        }
    }

    public final void jmp(Label l) {
        if (l.isBound()) {
            jmp(l.position(), false);
        } else {
            // By default, forward jumps are always 32-bit displacements, since
            // we can't yet know where the label will be bound. If you're sure that
            // the forward jump will not run beyond 256 bytes, use jmpb to
            // force an 8-bit displacement.

            l.addPatchAt(codeBuffer.position());
            emitByte(0xE9);
            emitInt(0);
        }
    }

    public final void jmp(CiRegister entry) {
        int encode = prefixAndEncode(entry.encoding);
        emitByte(0xFF);
        emitByte(0xE0 | encode);
    }

    public final void jmpb(Label l) {
        if (l.isBound()) {
            int shortSize = 2;
            int entry = l.position();
            assert Util.isByte((entry - codeBuffer.position()) + shortSize) : "Dispacement too large for a short jmp";
            long offs = entry - codeBuffer.position();
            emitByte(0xEB);
            emitByte((int) ((offs - shortSize) & 0xFF));
        } else {

            l.addPatchAt(codeBuffer.position());
            emitByte(0xEB);
            emitByte(0);
        }
    }

    public final void leaq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x8D);
        emitOperandHelper(dst, src);
    }

    public final void enter(int imm16, int imm8) {
        emitByte(0xC8);
        emitShort(imm16);
        emitByte(imm8);
    }

    public final void leave() {
        emitByte(0xC9);
    }

    public final void lock() {
        if ((C1XOptions.Atomics & 1) != 0) {
            // Emit either nothing, a NOP, or a NOP: prefix
            emitByte(0x90);
        } else {
            emitByte(0xF0);
        }
    }

    // Emit mfence instruction
    public final void mfence() {
        emitByte(0x0F);
        emitByte(0xAE);
        emitByte(0xF0);
    }

    public final void mov(CiRegister dst, CiRegister src) {
        movq(dst, src);
    }

    public final void movapd(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        int dstenc = dst.encoding;
        int srcenc = src.encoding;
        emitByte(0x66);
        if (dstenc < 8) {
            if (srcenc >= 8) {
                emitByte(Prefix.REXB);
                srcenc -= 8;
            }
        } else {
            if (srcenc < 8) {
                emitByte(Prefix.REXR);
            } else {
                emitByte(Prefix.REXRB);
                srcenc -= 8;
            }
            dstenc -= 8;
        }
        emitByte(0x0F);
        emitByte(0x28);
        emitByte(0xC0 | dstenc << 3 | srcenc);
    }

    public final void movaps(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        int dstenc = dst.encoding;
        int srcenc = src.encoding;
        if (dstenc < 8) {
            if (srcenc >= 8) {
                emitByte(Prefix.REXB);
                srcenc -= 8;
            }
        } else {
            if (srcenc < 8) {
                emitByte(Prefix.REXR);
            } else {
                emitByte(Prefix.REXRB);
                srcenc -= 8;
            }
            dstenc -= 8;
        }
        emitByte(0x0F);
        emitByte(0x28);
        emitByte(0xC0 | dstenc << 3 | srcenc);
    }

    public final void movb(CiRegister dst, CiAddress src) {
        prefix(src, dst); // , true)
        emitByte(0x8A);
        emitOperandHelper(dst, src);
    }

    public final void movb(CiAddress dst, int imm8) {
        prefix(dst);
        emitByte(0xC6);
        emitOperandHelper(rax, dst);
        emitByte(imm8);
    }

    public final void movb(CiAddress dst, CiRegister src) {
        assert src.isByte() : "must have byte register";
        prefix(dst, src); // , true)
        emitByte(0x88);
        emitOperandHelper(src, dst);
    }

    public final void movdl(CiRegister dst, CiRegister src) {
        if (dst.isFpu()) {
            assert !src.isFpu() : "does this hold?";
            emitByte(0x66);
            int encode = prefixAndEncode(dst.encoding, src.encoding);
            emitByte(0x0F);
            emitByte(0x6E);
            emitByte(0xC0 | encode);
        } else if (src.isFpu()) {
            assert !dst.isFpu();
            emitByte(0x66);
            // swap src/dst to get correct prefix
            int encode = prefixAndEncode(src.encoding, dst.encoding);
            emitByte(0x0F);
            emitByte(0x7E);
            emitByte(0xC0 | encode);
        }
    }

    public final void movdqa(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x6F);
        emitOperandHelper(dst, src);
    }

    public final void movdqa(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        emitByte(0x66);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x6F);
        emitByte(0xC0 | encode);
    }

    public final void movdqa(CiAddress dst, CiRegister src) {
        assert src.isFpu();
        emitByte(0x66);
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0x7F);
        emitOperandHelper(src, dst);
    }

    public final void movdqu(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x6F);
        emitOperandHelper(dst, src);
    }

    public final void movdqu(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();

        emitByte(0xF3);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x6F);
        emitByte(0xC0 | encode);
    }

    public final void movdqu(CiAddress dst, CiRegister src) {
        assert src.isFpu();

        emitByte(0xF3);
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0x7F);
        emitOperandHelper(src, dst);
    }

    public final void movl(CiRegister dst, int imm32) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xB8 | encode);
        emitInt(imm32);
    }

    public final void movl(CiRegister dst, CiRegister src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x8B);
        emitByte(0xC0 | encode);
    }

    public final void movl(CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x8B);
        emitOperandHelper(dst, src);
    }

    public final void movl(CiAddress dst, int imm32) {
        prefix(dst);
        emitByte(0xC7);
        emitOperandHelper(rax, dst);
        emitInt(imm32);
    }

    public final void movl(CiAddress dst, CiRegister src) {
        prefix(dst, src);
        emitByte(0x89);
        emitOperandHelper(src, dst);
    }

    // New cpus require to use movsd and movss to avoid partial register stall
    // when loading from memory. But for old Opteron use movlpd instead of movsd.
    // The selection is done in Macromovdbl() and movflt().
    public final void movlpd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();

        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x12);
        emitOperandHelper(dst, src);

    }

    public final void movq(CiRegister dst, CiAddress src) {
        if (dst.isFpu()) {
            emitByte(0xF3);
            prefixq(src, dst);
            emitByte(0x0F);
            emitByte(0x7E);
            emitOperandHelper(dst, src);
        } else {
            prefixq(src, dst);
            emitByte(0x8B);
            emitOperandHelper(dst, src);
        }
    }

    public final void movq(CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x8B);
        emitByte(0xC0 | encode);
    }

    public final void movq(CiAddress dst, CiRegister src) {
        if (src.isFpu()) {
            emitByte(0x66);
            prefixq(dst, src);
            emitByte(0x0F);
            emitByte(0xD6);
            emitOperandHelper(src, dst);
        } else {
            prefixq(dst, src);
            emitByte(0x89);
            emitOperandHelper(src, dst);
        }
    }

    public final void movsxb(CiRegister dst, CiAddress src) { // movsxb
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xBE);
        emitOperandHelper(dst, src);
    }

    public final void movsxb(CiRegister dst, CiRegister src) { // movsxb
        int encode = prefixAndEncode(dst.encoding, src.encoding, true);
        emitByte(0x0F);
        emitByte(0xBE);
        emitByte(0xC0 | encode);
    }

    public final void movsd(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x10);
        emitByte(0xC0 | encode);
    }

    public final void movsd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x10);
        emitOperandHelper(dst, src);
    }

    public final void movsd(CiAddress dst, CiRegister src) {
        assert src.isFpu();
        emitByte(0xF2);
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0x11);
        emitOperandHelper(src, dst);
    }

    public final void movss(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x10);
        emitByte(0xC0 | encode);
    }

    public final void movss(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x10);
        emitOperandHelper(dst, src);
    }

    public final void movss(CiAddress dst, CiRegister src) {
        assert src.isFpu();
        emitByte(0xF3);
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0x11);
        emitOperandHelper(src, dst);
    }

    public final void movswl(CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xBF);
        emitOperandHelper(dst, src);
    }

    public final void movsxw(CiRegister dst, CiRegister src) { // movsxw
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBF);
        emitByte(0xC0 | encode);
    }

    public final void movsxw(CiRegister dst, CiAddress src) { // movsxw
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xBF);
        emitOperandHelper(dst, src);
    }

    public final void movzxd(CiRegister dst, CiRegister src) { // movzxd
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x63);
        emitByte(0xC0 | encode);
    }

    public final void movzxd(CiRegister dst, CiAddress src) { // movzxd
        prefix(src, dst);
        emitByte(0x63);
        emitOperandHelper(dst, src);
    }

    public final void movw(CiAddress dst, int imm16) {
        emitByte(0x66); // switch to 16-bit mode
        prefix(dst);
        emitByte(0xC7);
        emitOperandHelper(rax, dst);
        emitShort(imm16);
    }

    public final void movw(CiRegister dst, CiAddress src) {
        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x8B);
        emitOperandHelper(dst, src);
    }

    public final void movw(CiAddress dst, CiRegister src) {
        emitByte(0x66);
        prefix(dst, src);
        emitByte(0x89);
        emitOperandHelper(src, dst);
    }

    public final void movzxb(CiRegister dst, CiAddress src) { // movzxb
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xB6);
        emitOperandHelper(dst, src);
    }

    public final void movzxb(CiRegister dst, CiRegister src) { // movzxb
        int encode = prefixAndEncode(dst.encoding, src.encoding, true);
        emitByte(0x0F);
        emitByte(0xB6);
        emitByte(0xC0 | encode);
    }

    public final void movzxl(CiRegister dst, CiAddress src) { // movzxw
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xB7);
        emitOperandHelper(dst, src);
    }

    public final void movzxl(CiRegister dst, CiRegister src) { // movzxw
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xB7);
        emitByte(0xC0 | encode);
    }

    public final void mull(CiAddress src) {
        prefix(src);
        emitByte(0xF7);
        emitOperandHelper(rsp, src);
    }

    public final void mulsd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x59);
        emitOperandHelper(dst, src);
    }

    public final void mulsd(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();

        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x59);
        emitByte(0xC0 | encode);
    }

    public final void mulss(CiRegister dst, CiAddress src) {
        assert dst.isFpu();

        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x59);
        emitOperandHelper(dst, src);
    }

    public final void mulss(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x59);
        emitByte(0xC0 | encode);
    }

    public final void negl(CiRegister dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xF7);
        emitByte(0xD8 | encode);
    }

    @Override
    public final void nop() {
        nop(1);
    }

    public void nop(int i) {
        if (C1XOptions.UseNormalNop) {
            assert i > 0 : " ";
            // The fancy nops aren't currently recognized by debuggers making it a
            // pain to disassemble code while debugging. If assert are on clearly
            // speed is not an issue so simply use the single byte traditional nop
            // to do alignment.

            for (; i > 0; i--) {
                emitByte(0x90);
            }
            return;
        }

        if (C1XOptions.UseAddressNop) {
            //
            // Using multi-bytes nops "0x0F 0x1F [Address]" for AMD.
            // 1: 0x90
            // 2: 0x66 0x90
            // 3: 0x66 0x66 0x90 (don't use "0x0F 0x1F 0x00" - need patching safe padding)
            // 4: 0x0F 0x1F 0x40 0x00
            // 5: 0x0F 0x1F 0x44 0x00 0x00
            // 6: 0x66 0x0F 0x1F 0x44 0x00 0x00
            // 7: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
            // 8: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
            // 9: 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
            // 10: 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
            // 11: 0x66 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00

            // The rest coding is AMD specific - use consecutive Address nops

            // 12: 0x66 0x0F 0x1F 0x44 0x00 0x00 0x66 0x0F 0x1F 0x44 0x00 0x00
            // 13: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00 0x66 0x0F 0x1F 0x44 0x00 0x00
            // 14: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
            // 15: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
            // 16: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
            // Size prefixes (0x66) are added for larger sizes

            while (i >= 22) {
                i -= 11;
                emitByte(0x66); // size prefix
                emitByte(0x66); // size prefix
                emitByte(0x66); // size prefix
                addrNop8();
            }
            // Generate first nop for size between 21-12
            switch (i) {
                case 21:
                    i -= 1;
                    emitByte(0x66); // size prefix
                    // fall through
                case 20:
                    // fall through
                case 19:
                    i -= 1;
                    emitByte(0x66); // size prefix
                    // fall through
                case 18:
                    // fall through
                case 17:
                    i -= 1;
                    emitByte(0x66); // size prefix
                    // fall through
                case 16:
                    // fall through
                case 15:
                    i -= 8;
                    addrNop8();
                    break;
                case 14:
                case 13:
                    i -= 7;
                    addrNop7();
                    break;
                case 12:
                    i -= 6;
                    emitByte(0x66); // size prefix
                    addrNop5();
                    break;
                default:
                    assert i < 12;
            }

            // Generate second nop for size between 11-1
            switch (i) {
                case 11:
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    addrNop8();
                    break;
                case 10:
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    addrNop8();
                    break;
                case 9:
                    emitByte(0x66); // size prefix
                    addrNop8();
                    break;
                case 8:
                    addrNop8();
                    break;
                case 7:
                    addrNop7();
                    break;
                case 6:
                    emitByte(0x66); // size prefix
                    addrNop5();
                    break;
                case 5:
                    addrNop5();
                    break;
                case 4:
                    addrNop4();
                    break;
                case 3:
                    // Don't use "0x0F 0x1F 0x00" - need patching safe padding
                    emitByte(0x66); // size prefix
                    emitByte(0x66); // size prefix
                    emitByte(0x90); // nop
                    break;
                case 2:
                    emitByte(0x66); // size prefix
                    emitByte(0x90); // nop
                    break;
                case 1:
                    emitByte(0x90); // nop
                    break;
                default:
                    assert i == 0;
            }
            return;
        }

        // Using nops with size prefixes "0x66 0x90".
        // From AMD Optimization Guide:
        // 1: 0x90
        // 2: 0x66 0x90
        // 3: 0x66 0x66 0x90
        // 4: 0x66 0x66 0x66 0x90
        // 5: 0x66 0x66 0x90 0x66 0x90
        // 6: 0x66 0x66 0x90 0x66 0x66 0x90
        // 7: 0x66 0x66 0x66 0x90 0x66 0x66 0x90
        // 8: 0x66 0x66 0x66 0x90 0x66 0x66 0x66 0x90
        // 9: 0x66 0x66 0x90 0x66 0x66 0x90 0x66 0x66 0x90
        // 10: 0x66 0x66 0x66 0x90 0x66 0x66 0x90 0x66 0x66 0x90
        //
        while (i > 12) {
            i -= 4;
            emitByte(0x66); // size prefix
            emitByte(0x66);
            emitByte(0x66);
            emitByte(0x90); // nop
        }
        // 1 - 12 nops
        if (i > 8) {
            if (i > 9) {
                i -= 1;
                emitByte(0x66);
            }
            i -= 3;
            emitByte(0x66);
            emitByte(0x66);
            emitByte(0x90);
        }
        // 1 - 8 nops
        if (i > 4) {
            if (i > 6) {
                i -= 1;
                emitByte(0x66);
            }
            i -= 3;
            emitByte(0x66);
            emitByte(0x66);
            emitByte(0x90);
        }
        switch (i) {
            case 4:
                emitByte(0x66);
                emitByte(0x66);
                emitByte(0x66);
                emitByte(0x90);
                break;
            case 3:
                emitByte(0x66);
                emitByte(0x66);
                emitByte(0x90);
                break;
            case 2:
                emitByte(0x66);
                emitByte(0x90);
                break;
            case 1:
                emitByte(0x90);
                break;
            default:
                assert i == 0;
        }
    }

    public final void notl(CiRegister dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xF7);
        emitByte(0xD0 | encode);
    }

    public final void orl(CiAddress dst, int imm32) {
        prefix(dst);
        emitByte(0x81);
        emitOperandHelper(rcx, dst);
        emitInt(imm32);
    }

    public final void orl(CiRegister dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xC8, dst, imm32);
    }

    public final void orl(CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x0B);
        emitOperandHelper(dst, src);
    }

    public final void orl(CiRegister dst, CiRegister src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x0B, 0xC0, dst, src);
    }

    // generic
    public final void pop(CiRegister dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0x58 | encode);
    }

    public final void popl(CiAddress dst) {
        // NOTE: this will adjust stack by 8byte on 64bits
        prefix(dst);
        emitByte(0x8F);
        emitOperandHelper(rax, dst);
    }

    public final void prefetchPrefix(CiAddress src) {
        prefix(src);
        emitByte(0x0F);
    }

    public final void prefetchnta(CiAddress src) {
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(rax, src); // 0, src
    }

    public final void prefetchr(CiAddress src) {
        prefetchPrefix(src);
        emitByte(0x0D);
        emitOperandHelper(rax, src); // 0, src
    }

    public final void prefetcht0(CiAddress src) {
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(rcx, src); // 1, src

    }

    public final void prefetcht1(CiAddress src) {
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(rdx, src); // 2, src
    }

    public final void prefetcht2(CiAddress src) {
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(rbx, src); // 3, src
    }

    public final void prefetchw(CiAddress src) {
        prefetchPrefix(src);
        emitByte(0x0D);
        emitOperandHelper(rcx, src); // 1, src
    }

    public final void pshufd(CiRegister dst, CiRegister src, int mode) {
        assert dst.isFpu();
        assert src.isFpu();
        assert Util.isUByte(mode) : "invalid value";

        emitByte(0x66);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x70);
        emitByte(0xC0 | encode);
        emitByte(mode & 0xFF);
    }

    public final void pshufd(CiRegister dst, CiAddress src, int mode) {
        assert dst.isFpu();
        assert Util.isUByte(mode) : "invalid value";

        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x70);
        emitOperandHelper(dst, src);
        emitByte(mode & 0xFF);

    }

    public final void pshuflw(CiRegister dst, CiRegister src, int mode) {
        assert dst.isFpu();
        assert src.isFpu();
        assert Util.isUByte(mode) : "invalid value";

        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x70);
        emitByte(0xC0 | encode);
        emitByte(mode & 0xFF);
    }

    public final void pshuflw(CiRegister dst, CiAddress src, int mode) {
        assert dst.isFpu();
        assert Util.isUByte(mode) : "invalid value";

        emitByte(0xF2);
        prefix(src, dst); // QQ new
        emitByte(0x0F);
        emitByte(0x70);
        emitOperandHelper(dst, src);
        emitByte(mode & 0xFF);
    }

    public final void psrlq(CiRegister dst, int shift) {
        assert dst.isFpu();
        // HMM Table D-1 says sse2 or mmx

        int encode = prefixqAndEncode(xmm2.encoding, dst.encoding);
        emitByte(0x66);
        emitByte(0x0F);
        emitByte(0x73);
        emitByte(0xC0 | encode);
        emitByte(shift);
    }

    public final void punpcklbw(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0x66);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x60);
        emitByte(0xC0 | encode);
    }

    public final void push(int imm32) {
        // in 64bits we push 64bits onto the stack but only
        // take a 32bit immediate
        emitByte(0x68);
        emitInt(imm32);
    }

    public final void push(CiRegister src) {
        int encode = prefixAndEncode(src.encoding);
        emitByte(0x50 | encode);
    }

    public final void pushf() {
        emitByte(0x9C);
    }

    public final void pushl(CiAddress src) {
        // Note this will push 64bit on 64bit
        prefix(src);
        emitByte(0xFF);
        emitOperandHelper(rsi, src);
    }

    public final void pxor(CiRegister dst, CiAddress src) {
        assert dst.isFpu();

        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xEF);
        emitOperandHelper(dst, src);
    }

    public final void pxor(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();

        emitByte(0x66);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xEF);
        emitByte(0xC0 | encode);

    }

    public final void rcll(CiRegister dst, int imm8) {
        assert Util.isShiftCount(imm8) : "illegal shift count";
        int encode = prefixAndEncode(dst.encoding);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xD0 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xD0 | encode);
            emitByte(imm8);
        }
    }

    public final void pause() {
        emitByte(0xF3);
        emitByte(0x90);
    }

    // Copies data from [X86.rsi] to [X86.rdi] using X86.rcx heap words.
    public final void repeatMoveWords() {
        emitByte(0xF3);
        emitByte(Prefix.REXW);
        emitByte(0xA5);
    }

    // Copies data from [X86.rsi] to [X86.rdi] using X86.rcx bytes.
    public final void repeatMoveBytes() {
        emitByte(0xF3);
        emitByte(Prefix.REXW);
        emitByte(0xA4);
    }

    // sets X86.rcx pointer sized words with X86.rax, value at [edi]
    // generic
    public final void repSet() { // repSet
        emitByte(0xF3);
        // STOSQ
        emitByte(Prefix.REXW);
        emitByte(0xAB);
    }

    // scans X86.rcx pointer sized words at [edi] for occurance of X86.rax,
    // generic
    public final void repneScan() { // repneScan
        emitByte(0xF2);
        // SCASQ
        emitByte(Prefix.REXW);
        emitByte(0xAF);
    }

    // scans X86.rcx 4 byte words at [edi] for occurance of X86.rax,
    // generic
    public final void repneScanl() { // repneScan
        emitByte(0xF2);
        // SCASL
        emitByte(0xAF);
    }

    public final void ret(int imm16) {
        if (imm16 == 0) {
            emitByte(0xC3);
        } else {
            emitByte(0xC2);
            emitShort(imm16);
        }
    }

    public final void sarl(CiRegister dst, int imm8) {
        int encode = prefixAndEncode(dst.encoding);
        assert Util.isShiftCount(imm8) : "illegal shift count";
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xF8 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xF8 | encode);
            emitByte(imm8);
        }
    }

    public final void sarl(CiRegister dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xF8 | encode);
    }

    public final void sbbl(CiAddress dst, int imm32) {
        prefix(dst);
        emitArithOperand(0x81, rbx, dst, imm32);
    }

    public final void sbbl(CiRegister dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xD8, dst, imm32);
    }

    public final void sbbl(CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x1B);
        emitOperandHelper(dst, src);
    }

    public final void sbbl(CiRegister dst, CiRegister src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x1B, 0xC0, dst, src);
    }

    public final void setb(ConditionFlag cc, CiRegister dst) {
        assert 0 <= cc.value && cc.value < 16 : "illegal cc";
        int encode = prefixAndEncode(dst.encoding, true);
        emitByte(0x0F);
        emitByte(0x90 | cc.value);
        emitByte(0xC0 | encode);
    }

    public final void shll(CiRegister dst, int imm8) {
        assert Util.isShiftCount(imm8) : "illegal shift count";
        int encode = prefixAndEncode(dst.encoding);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xE0 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xE0 | encode);
            emitByte(imm8);
        }
    }

    public final void shll(CiRegister dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE0 | encode);
    }

    public final void shrl(CiRegister dst, int imm8) {
        assert Util.isShiftCount(imm8) : "illegal shift count";
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xC1);
        emitByte(0xE8 | encode);
        emitByte(imm8);
    }

    public final void shrl(CiRegister dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE8 | encode);
    }

    // copies a single word from [esi] to [edi]
    public final void smovl() {
        emitByte(0xA5);
    }

    public final void sqrtsd(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        // HMM Table D-1 says sse2
        // assert is64 || target.supportsSSE();
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x51);
        emitByte(0xC0 | encode);
    }

    public final void subl(CiAddress dst, int imm32) {
        prefix(dst);
        if (Util.isByte(imm32)) {
            emitByte(0x83);
            emitOperandHelper(rbp, dst);
            emitByte(imm32 & 0xFF);
        } else {
            emitByte(0x81);
            emitOperandHelper(rbp, dst);
            emitInt(imm32);
        }
    }

    public final void subl(CiRegister dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xE8, dst, imm32);
    }

    public final void subl(CiAddress dst, CiRegister src) {
        prefix(dst, src);
        emitByte(0x29);
        emitOperandHelper(src, dst);
    }

    public final void subl(CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x2B);
        emitOperandHelper(dst, src);
    }

    public final void subl(CiRegister dst, CiRegister src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x2B, 0xC0, dst, src);
    }

    public final void subsd(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5C);
        emitByte(0xC0 | encode);
    }

    public final void subsd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();

        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x5C);
        emitOperandHelper(dst, src);
    }

    public final void subss(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5C);
        emitByte(0xC0 | encode);
    }

    public final void subss(CiRegister dst, CiAddress src) {
        assert dst.isFpu();

        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x5C);
        emitOperandHelper(dst, src);
    }

    public final void testb(CiRegister dst, int imm8) {
        prefixAndEncode(dst.encoding, true);
        emitArithB(0xF6, 0xC0, dst, imm8);
    }

    public final void testl(CiRegister dst, int imm32) {
        // not using emitArith because test
        // doesn't support sign-extension of
        // 8bit operands
        int encode = dst.encoding;
        if (encode == 0) {
            emitByte(0xA9);
        } else {
            encode = prefixAndEncode(encode);
            emitByte(0xF7);
            emitByte(0xC0 | encode);
        }
        emitInt(imm32);
    }

    public final void testl(CiRegister dst, CiRegister src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x85, 0xC0, dst, src);
    }

    public final void testl(CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x85);
        emitOperandHelper(dst, src);
    }

    public final void ucomisd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        emitByte(0x66);
        ucomiss(dst, src);
    }

    public final void ucomisd(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0x66);
        ucomiss(dst, src);
    }

    public final void ucomiss(CiRegister dst, CiAddress src) {
        assert dst.isFpu();

        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x2E);
        emitOperandHelper(dst, src);
    }

    public final void ucomiss(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2E);
        emitByte(0xC0 | encode);
    }

    public final void xaddl(CiAddress dst, CiRegister src) {
        assert src.isFpu();

        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0xC1);
        emitOperandHelper(src, dst);
    }

    public final void xchgl(CiRegister dst, CiAddress src) { // xchg
        prefix(src, dst);
        emitByte(0x87);
        emitOperandHelper(dst, src);
    }

    public final void xchgl(CiRegister dst, CiRegister src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x87);
        emitByte(0xc0 | encode);
    }

    public final void xorl(CiRegister dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xF0, dst, imm32);
    }

    public final void xorl(CiRegister dst, CiAddress src) {
        prefix(src, dst);
        emitByte(0x33);
        emitOperandHelper(dst, src);
    }

    public final void xorl(CiRegister dst, CiRegister src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x33, 0xC0, dst, src);
    }

    public final void xorpd(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        assert src.isFpu();
        emitByte(0x66);
        xorps(dst, src);
    }

    public final void xorpd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();

        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x57);
        emitOperandHelper(dst, src);
    }

    public final void xorps(CiRegister dst, CiRegister src) {

        assert dst.isFpu();
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x57);
        emitByte(0xC0 | encode);
    }

    public final void xorps(CiRegister dst, CiAddress src) {
        assert dst.isFpu();

        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x57);
        emitOperandHelper(dst, src);
    }

    // 32bit only pieces of the assembler

    public final void decl(CiRegister dst) {
        // Don't use it directly. Use Macrodecrementl() instead.
        // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC8 | encode);
    }

    public final void incl(CiRegister dst) {
        // Don't use it directly. Use Macroincrementl() instead.
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC0 | encode);
    }

    int prefixAndEncode(int regEnc) {
        return prefixAndEncode(regEnc, false);
    }

    int prefixAndEncode(int regEnc, boolean byteinst) {
        if (regEnc >= 8) {
            emitByte(Prefix.REXB);
            regEnc -= 8;
        } else if (byteinst && regEnc >= 4) {
            emitByte(Prefix.REX);
        }
        return regEnc;
    }

    int prefixqAndEncode(int regEnc) {
        if (regEnc < 8) {
            emitByte(Prefix.REXW);
        } else {
            emitByte(Prefix.REXWB);
            regEnc -= 8;
        }
        return regEnc;
    }

    int prefixAndEncode(int dstEnc, int srcEnc) {
        return prefixAndEncode(dstEnc, srcEnc, false);
    }

    int prefixAndEncode(int dstEnc, int srcEnc, boolean byteinst) {
        if (dstEnc < 8) {
            if (srcEnc >= 8) {
                emitByte(Prefix.REXB);
                srcEnc -= 8;
            } else if (byteinst && srcEnc >= 4) {
                emitByte(Prefix.REX);
            }
        } else {
            if (srcEnc < 8) {
                emitByte(Prefix.REXR);
            } else {
                emitByte(Prefix.REXRB);
                srcEnc -= 8;
            }
            dstEnc -= 8;
        }
        return dstEnc << 3 | srcEnc;
    }

    /**
     * Creates prefix and the encoding of the lower 6 bits of the ModRM-Byte. It emits an operand prefix. If the given
     * operands exceed 3 bits, the 4th bit is encoded in the prefix.
     *
     * @param regEnc the encoding of the register part of the ModRM-Byte
     * @param rmEnc the encoding of the r/m part of the ModRM-Byte
     * @return the lower 6 bits of the ModRM-Byte that should be emitted
     */
    private int prefixqAndEncode(int regEnc, int rmEnc) {
        if (regEnc < 8) {
            if (rmEnc < 8) {
                emitByte(Prefix.REXW);
            } else {
                emitByte(Prefix.REXWB);
                rmEnc -= 8;
            }
        } else {
            if (rmEnc < 8) {
                emitByte(Prefix.REXWR);
            } else {
                emitByte(Prefix.REXWRB);
                rmEnc -= 8;
            }
            regEnc -= 8;
        }
        return regEnc << 3 | rmEnc;
    }

    private void prefix(CiRegister reg) {
        if (reg.encoding >= 8) {
            emitByte(Prefix.REXB);
        }
    }

    private void prefix(CiAddress adr) {
        if (adr.base().encoding >= MinEncodingNeedsRex) {
            if (adr.index().encoding >= MinEncodingNeedsRex) {
                emitByte(Prefix.REXXB);
            } else {
                emitByte(Prefix.REXB);
            }
        } else {
            if (adr.index().encoding >= MinEncodingNeedsRex) {
                emitByte(Prefix.REXX);
            }
        }
    }

    private void prefixq(CiAddress adr) {
        if (adr.base().encoding >= MinEncodingNeedsRex) {
            if (adr.index().encoding >= MinEncodingNeedsRex) {
                emitByte(Prefix.REXWXB);
            } else {
                emitByte(Prefix.REXWB);
            }
        } else {
            if (adr.index().encoding >= MinEncodingNeedsRex) {
                emitByte(Prefix.REXWX);
            } else {
                emitByte(Prefix.REXW);
            }
        }
    }

    private void prefix(CiAddress adr, CiRegister reg) {
        if (reg.encoding < 8) {
            if (adr.base().encoding >= MinEncodingNeedsRex) {
                if (adr.index().encoding >= MinEncodingNeedsRex) {
                    emitByte(Prefix.REXXB);
                } else {
                    emitByte(Prefix.REXB);
                }
            } else {
                if (adr.index().encoding >= MinEncodingNeedsRex) {
                    emitByte(Prefix.REXX);
                } else if (reg.encoding >= 4) {
                    emitByte(Prefix.REX);
                }
            }
        } else {
            if (adr.base().encoding >= MinEncodingNeedsRex) {
                if (adr.index().encoding >= MinEncodingNeedsRex) {
                    emitByte(Prefix.REXRXB);
                } else {
                    emitByte(Prefix.REXRB);
                }
            } else {
                if (adr.index().encoding >= MinEncodingNeedsRex) {
                    emitByte(Prefix.REXRX);
                } else {
                    emitByte(Prefix.REXR);
                }
            }
        }
    }

    private void prefixq(CiAddress adr, CiRegister src) {
        if (src.encoding < 8) {
            if (adr.base().encoding >= MinEncodingNeedsRex) {
                if (adr.index().encoding >= MinEncodingNeedsRex) {
                    emitByte(Prefix.REXWXB);
                } else {
                    emitByte(Prefix.REXWB);
                }
            } else {
                if (adr.index().encoding >= MinEncodingNeedsRex) {
                    emitByte(Prefix.REXWX);
                } else {
                    emitByte(Prefix.REXW);
                }
            }
        } else {
            if (adr.base().encoding >= MinEncodingNeedsRex) {
                if (adr.index().encoding >= MinEncodingNeedsRex) {
                    emitByte(Prefix.REXWRXB);
                } else {
                    emitByte(Prefix.REXWRB);
                }
            } else {
                if (adr.index().encoding >= MinEncodingNeedsRex) {
                    emitByte(Prefix.REXWRX);
                } else {
                    emitByte(Prefix.REXWR);
                }
            }
        }
    }

    public final void addq(CiAddress dst, int imm32) {
        prefixq(dst);
        emitArithOperand(0x81, rax, dst, imm32);
    }

    public final void addq(CiAddress dst, CiRegister src) {
        prefixq(dst, src);
        emitByte(0x01);
        emitOperandHelper(src, dst);
    }

    public final void addq(CiRegister dst, int imm32) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xC0, dst, imm32);
    }

    public final void addq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x03);
        emitOperandHelper(dst, src);
    }

    public final void addq(CiRegister dst, CiRegister src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x03, 0xC0, dst, src);
    }

    public final void andq(CiRegister dst, int imm32) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xE0, dst, imm32);
    }

    public final void andq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x23);
        emitOperandHelper(dst, src);
    }

    public final void andq(CiRegister dst, CiRegister src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x23, 0xC0, dst, src);
    }

    public final void bswapq(CiRegister reg) {
        int encode = prefixqAndEncode(reg.encoding);
        emitByte(0x0F);
        emitByte(0xC8 | encode);
    }

    public final void cdqq() {
        emitByte(Prefix.REXW);
        emitByte(0x99);
    }

    public final void cmovq(ConditionFlag cc, CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x40 | cc.value);
        emitByte(0xC0 | encode);
    }

    public final void cmovq(ConditionFlag cc, CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.value);
        emitOperandHelper(dst, src);
    }

    public final void cmpq(CiAddress dst, int imm32) {
        prefixq(dst);
        emitByte(0x81);
        emitOperandHelper(rdi, dst);
        emitInt(imm32);
    }

    public final void cmpq(CiRegister dst, int imm32) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xF8, dst, imm32);
    }

    public final void cmpq(CiAddress dst, CiRegister src) {
        prefixq(dst, src);
        emitByte(0x3B);
        emitOperandHelper(src, dst);
    }

    public final void cmpq(CiRegister dst, CiRegister src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x3B, 0xC0, dst, src);
    }

    public final void cmpq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x3B);
        emitOperandHelper(dst, src);
    }

    public final void cmpxchgq(CiRegister reg, CiAddress adr) {
        prefixq(adr, reg);
        emitByte(0x0F);
        emitByte(0xB1);
        emitOperandHelper(reg, adr);
    }

    public final void cvtsi2sdq(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        emitByte(0xF2);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2A);
        emitByte(0xC0 | encode);
    }

    public final void cvtsi2ssq(CiRegister dst, CiRegister src) {
        assert dst.isFpu();
        emitByte(0xF3);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2A);
        emitByte(0xC0 | encode);
    }

    public final void cvttsd2siq(CiRegister dst, CiRegister src) {
        assert src.isFpu();
        emitByte(0xF2);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2C);
        emitByte(0xC0 | encode);
    }

    public final void cvttss2siq(CiRegister dst, CiRegister src) {
        assert src.isFpu();
        emitByte(0xF3);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2C);
        emitByte(0xC0 | encode);
    }

    public final void decq(CiRegister dst) {
        // Don't use it directly. Use Macrodecrementq() instead.
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC8 | encode);
    }

    public final void decq(CiAddress dst) {
        // Don't use it directly. Use Macrodecrementq() instead.
        prefixq(dst);
        emitByte(0xFF);
        emitOperandHelper(rcx, dst);
    }

    public final void divq(CiRegister src) {
        int encode = prefixqAndEncode(src.encoding);
        emitByte(0xF7);
        emitByte(0xF0 | encode);
    }

    public final void idivq(CiRegister src) {
        int encode = prefixqAndEncode(src.encoding);
        emitByte(0xF7);
        emitByte(0xF8 | encode);
    }

    public final void imulq(CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xAF);
        emitByte(0xC0 | encode);
    }

    public final void imulq(CiRegister dst, CiRegister src, int value) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        if (Util.isByte(value)) {
            emitByte(0x6B);
            emitByte(0xC0 | encode);
            emitByte(value);
        } else {
            emitByte(0x69);
            emitByte(0xC0 | encode);
            emitInt(value);
        }
    }

    public final void incq(CiRegister dst) {
        // Don't use it directly. Use Macroincrementq() instead.
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC0 | encode);
    }

    public final void incq(CiAddress dst) {
        // Don't use it directly. Use Macroincrementq() instead.
        prefixq(dst);
        emitByte(0xFF);
        emitOperandHelper(rax, dst);
    }

    public final void movq(CiRegister dst, long imm64) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xB8 | encode);
        emitLong(imm64);
    }

    public final void movdq(CiRegister dst, CiRegister src) {

        // table D-1 says MMX/SSE2
        emitByte(0x66);

        if (dst.isFpu()) {
            assert dst.isFpu();
            int encode = prefixqAndEncode(dst.encoding, src.encoding);
            emitByte(0x0F);
            emitByte(0x6E);
            emitByte(0xC0 | encode);
        } else if (src.isFpu()) {

            // swap src/dst to get correct prefix
            int encode = prefixqAndEncode(src.encoding, dst.encoding);
            emitByte(0x0F);
            emitByte(0x7E);
            emitByte(0xC0 | encode);
        } else {
            Util.shouldNotReachHere();
        }
    }

    public final void movsbq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xBE);
        emitOperandHelper(dst, src);
    }

    public final void movsbq(CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBE);
        emitByte(0xC0 | encode);
    }

    public final void movslq(CiRegister dst, int imm32) {
        // dbx shows movslq(X86.rcx, 3) as movq $0x0000000049000000,(%X86.rbx)
        // and movslq(X86.r8, 3); as movl $0x0000000048000000,(%X86.rbx)
        // as a result we shouldn't use until tested at runtime...
        Util.shouldNotReachHere();

        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xC7 | encode);
        emitInt(imm32);
    }

    public final void movslq(CiAddress dst, int imm32) {
        prefixq(dst);
        emitByte(0xC7);
        emitOperandHelper(rax, dst);
        emitInt(imm32);
    }

    public final void movslq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x63);
        emitOperandHelper(dst, src);
    }

    public final void movslq(CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x63);
        emitByte(0xC0 | encode);
    }

    public final void movswq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xBF);
        emitOperandHelper(dst, src);
    }

    public final void movswq(CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBF);
        emitByte(0xC0 | encode);
    }

    public final void movzbq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xB6);
        emitOperandHelper(dst, src);
    }

    public final void movzbq(CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xB6);
        emitByte(0xC0 | encode);
    }

    public final void movzwq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xB7);
        emitOperandHelper(dst, src);
    }

    public final void movzwq(CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xB7);
        emitByte(0xC0 | encode);
    }

    public final void negq(CiRegister dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xF7);
        emitByte(0xD8 | encode);
    }

    public final void notq(CiRegister dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xF7);
        emitByte(0xD0 | encode);
    }

    public final void orq(CiAddress dst, int imm32) {
        prefixq(dst);
        emitByte(0x81);
        emitOperandHelper(rcx, dst);
        emitInt(imm32);
    }

    public final void orq(CiRegister dst, int imm32) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xC8, dst, imm32);
    }

    public final void orq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x0B);
        emitOperandHelper(dst, src);
    }

    public final void orq(CiRegister dst, CiRegister src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x0B, 0xC0, dst, src);
    }

    public final void popq(CiAddress dst) {
        prefixq(dst);
        emitByte(0x8F);
        emitOperandHelper(rax, dst);
    }

    public final void pushq(CiAddress src) {
        prefixq(src);
        emitByte(0xFF);
        emitOperandHelper(rsi, src);
    }

    public final void rclq(CiRegister dst, int imm8) {
        assert Util.isShiftCount(imm8 >> 1) : "illegal shift count";
        int encode = prefixqAndEncode(dst.encoding);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xD0 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xD0 | encode);
            emitByte(imm8);
        }
    }

    public final void sarq(CiRegister dst, int imm8) {
        assert Util.isShiftCount(imm8 >> 1) : "illegal shift count";
        int encode = prefixqAndEncode(dst.encoding);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xF8 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xF8 | encode);
            emitByte(imm8);
        }
    }

    public final void sarq(CiRegister dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xF8 | encode);
    }

    public final void shlq(CiRegister dst, int imm8) {
        assert Util.isShiftCount(imm8 >> 1) : "illegal shift count";
        int encode = prefixqAndEncode(dst.encoding);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xE0 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xE0 | encode);
            emitByte(imm8);
        }
    }

    public final void shlq(CiRegister dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE0 | encode);
    }

    public final void shrq(CiRegister dst, int imm8) {
        assert Util.isShiftCount(imm8 >> 1) : "illegal shift count";
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xC1);
        emitByte(0xE8 | encode);
        emitByte(imm8);
    }

    public final void shrq(CiRegister dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE8 | encode);
    }

    public final void sqrtsd(CiRegister dst, CiAddress src) {
        assert dst.isFpu();

        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x51);
        emitOperandHelper(dst, src);
    }

    public final void subq(CiAddress dst, int imm32) {
        prefixq(dst);
        if (Util.isByte(imm32)) {
            emitByte(0x83);
            emitOperandHelper(rbp, dst);
            emitByte(imm32 & 0xFF);
        } else {
            emitByte(0x81);
            emitOperandHelper(rbp, dst);
            emitInt(imm32);
        }
    }

    public final void subq(CiRegister dst, int imm32) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xE8, dst, imm32);
    }

    public final void subq(CiAddress dst, CiRegister src) {
        prefixq(dst, src);
        emitByte(0x29);
        emitOperandHelper(src, dst);
    }

    public final void subq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x2B);
        emitOperandHelper(dst, src);
    }

    public final void subq(CiRegister dst, CiRegister src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x2B, 0xC0, dst, src);
    }

    public final void testq(CiRegister dst, int imm32) {
        // not using emitArith because test
        // doesn't support sign-extension of
        // 8bit operands
        int encode = dst.encoding;
        if (encode == 0) {
            emitByte(Prefix.REXW);
            emitByte(0xA9);
        } else {
            encode = prefixqAndEncode(encode);
            emitByte(0xF7);
            emitByte(0xC0 | encode);
        }
        emitInt(imm32);
    }

    public final void testq(CiRegister dst, CiRegister src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x85, 0xC0, dst, src);
    }

    public final void xaddq(CiAddress dst, CiRegister src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0xC1);
        emitOperandHelper(src, dst);
    }

    public final void xchgq(CiRegister dst, CiAddress src) {
        prefixq(src, dst);
        emitByte(0x87);
        emitOperandHelper(dst, src);
    }

    public final void xchgq(CiRegister dst, CiRegister src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x87);
        emitByte(0xc0 | encode);
    }

    public final void xorq(CiRegister dst, CiRegister src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x33, 0xC0, dst, src);
    }

    public final void xorq(CiRegister dst, CiAddress src) {

        prefixq(src, dst);
        emitByte(0x33);
        emitOperandHelper(dst, src);

    }

    public final void membar(int barriers) {
        if (target.isMP) {
            // We only have to handle StoreLoad
            if ((barriers & STORE_LOAD) != 0) {
                // All usable chips support "locked" instructions which suffice
                // as barriers, and are much faster than the alternative of
                // using cpuid instruction. We use here a locked add [rsp],0.
                // This is conveniently otherwise a no-op except for blowing
                // flags.
                // Any change to this code may need to revisit other places in
                // the code where this idiom is used, in particular the
                // orderAccess code.
                lock();
                addl(new CiAddress(CiKind.Word, RSP, 0), 0); // Assert the lock# signal here
            }
        }
    }

    @Override
    public final void patchJumpTarget(int branch, int branchTarget) {
        int op = codeBuffer.getByte(branch);
        assert op == 0xE8 // call
            || op == 0x00 // jump table entry
            || op == 0xE9 // jmp
            || op == 0xEB // short jmp
            || (op & 0xF0) == 0x70 // short jcc
            || op == 0x0F && (codeBuffer.getByte(branch + 1) & 0xF0) == 0x80 // jcc
        : "Invalid opcode at patch point branch=" + branch + ", branchTarget=" + branchTarget + ", op=" + op;

        if (op == 0x00) {
            int offsetToJumpTableBase = codeBuffer.getShort(branch + 1);
            int jumpTableBase = branch - offsetToJumpTableBase;
            int imm32 = branchTarget - jumpTableBase;
            codeBuffer.emitInt(imm32, branch);
        } else if (op == 0xEB || (op & 0xF0) == 0x70) {

            // short offset operators (jmp and jcc)
            int imm8 = branchTarget - (branch + 2);
            codeBuffer.emitByte(imm8, branch + 1);

        } else {

            int off = 1;
            if (op == 0x0F) {
                off = 2;
            }

            int imm32 = branchTarget - (branch + 4 + off);
            codeBuffer.emitInt(imm32, branch + off);
        }
    }

    @Override
    public void nullCheck(CiRegister r) {
        testl(AMD64.rax, new CiAddress(CiKind.Word, r.asValue(Word), 0));
    }

    @Override
    public void align(int modulus) {
        if (codeBuffer.position() % modulus != 0) {
            nop(modulus - (codeBuffer.position() % modulus));
        }
    }
}
