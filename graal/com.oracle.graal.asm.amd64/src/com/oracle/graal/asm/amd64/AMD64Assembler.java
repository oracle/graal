/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.asm.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.api.code.MemoryBarriers.*;
import static com.oracle.graal.asm.NumUtil.*;
import static com.oracle.graal.asm.amd64.AMD64AsmOptions.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.asm.*;

/**
 * This class implements an assembler that can encode most X86 instructions.
 */
public class AMD64Assembler extends Assembler {

    private static final int MinEncodingNeedsRex = 8;

    /**
     * A sentinel value used as a place holder in an instruction stream for an address that will be
     * patched.
     */
    private static final AMD64Address Placeholder = new AMD64Address(rip);

    /**
     * The x86 condition codes used for conditional jumps/moves.
     */
    public enum ConditionFlag {
        Zero(0x4, "|zero|"),
        NotZero(0x5, "|nzero|"),
        Equal(0x4, "="),
        NotEqual(0x5, "!="),
        Less(0xc, "<"),
        LessEqual(0xe, "<="),
        Greater(0xf, ">"),
        GreaterEqual(0xd, ">="),
        Below(0x2, "|<|"),
        BelowEqual(0x6, "|<=|"),
        Above(0x7, "|>|"),
        AboveEqual(0x3, "|>=|"),
        Overflow(0x0, "|of|"),
        NoOverflow(0x1, "|nof|"),
        CarrySet(0x2, "|carry|"),
        CarryClear(0x3, "|ncarry|"),
        Negative(0x8, "|neg|"),
        Positive(0x9, "|pos|"),
        Parity(0xa, "|par|"),
        NoParity(0xb, "|npar|");

        private final int value;
        private final String operator;

        private ConditionFlag(int value, String operator) {
            this.value = value;
            this.operator = operator;
        }

        public ConditionFlag negate() {
            switch (this) {
                case Zero:
                    return NotZero;
                case NotZero:
                    return Zero;
                case Equal:
                    return NotEqual;
                case NotEqual:
                    return Equal;
                case Less:
                    return GreaterEqual;
                case LessEqual:
                    return Greater;
                case Greater:
                    return LessEqual;
                case GreaterEqual:
                    return Less;
                case Below:
                    return AboveEqual;
                case BelowEqual:
                    return Above;
                case Above:
                    return BelowEqual;
                case AboveEqual:
                    return Below;
                case Overflow:
                    return NoOverflow;
                case NoOverflow:
                    return Overflow;
                case CarrySet:
                    return CarryClear;
                case CarryClear:
                    return CarrySet;
                case Negative:
                    return Positive;
                case Positive:
                    return Negative;
                case Parity:
                    return NoParity;
                case NoParity:
                    return Parity;
            }
            throw new IllegalArgumentException();
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return operator;
        }
    }

    /**
     * Constants for X86 prefix bytes.
     */
    private static class Prefix {

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
     * The register to which {@link Register#Frame} and {@link Register#CallerFrame} are bound.
     */
    public final Register frameRegister;

    /**
     * Constructs an assembler for the AMD64 architecture.
     * 
     * @param registerConfig the register configuration used to bind {@link Register#Frame} and
     *            {@link Register#CallerFrame} to physical registers. This value can be null if this
     *            assembler instance will not be used to assemble instructions using these logical
     *            registers.
     */
    public AMD64Assembler(TargetDescription target, RegisterConfig registerConfig) {
        super(target);
        this.frameRegister = registerConfig == null ? null : registerConfig.getFrameRegister();
    }

    private boolean supports(CPUFeature feature) {
        return ((AMD64) target.arch).getFeatures().contains(feature);
    }

    private static int encode(Register r) {
        assert r.encoding < 16 && r.encoding >= 0 : "encoding out of range: " + r.encoding;
        return r.encoding & 0x7;
    }

    private void emitArith(int op1, int op2, Register dst, int imm32) {
        emitArith(op1, op2, dst, imm32, false);
    }

    private void emitArith(int op1, int op2, Register dst, int imm32, boolean force32Imm) {
        assert isUByte(op1) && isUByte(op2) : "wrong opcode";
        assert (op1 & 0x01) == 1 : "should be 32bit operation";
        assert (op1 & 0x02) == 0 : "sign-extension bit should not be set";
        if (isByte(imm32) && !force32Imm) {
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
    private void emitArithOperand(int op1, int op2, AMD64Address adr, int imm32) {
        assert (op1 & 0x01) == 1 : "should be 32bit operation";
        assert (op1 & 0x02) == 0 : "sign-extension bit should not be set";
        if (isByte(imm32)) {
            emitByte(op1 | 0x02); // set sign bit
            emitOperandHelper(op2, adr);
            emitByte(imm32 & 0xFF);
        } else {
            emitByte(op1);
            emitOperandHelper(op2, adr);
            emitInt(imm32);
        }
    }

    private void emitArith(int op1, int op2, Register dst, Register src) {
        assert isUByte(op1) && isUByte(op2) : "wrong opcode";
        emitByte(op1);
        emitByte(op2 | encode(dst) << 3 | encode(src));
    }

    protected void emitOperandHelper(Register reg, AMD64Address addr) {
        assert !reg.equals(Register.None);
        emitOperandHelper(encode(reg), addr);
    }

    protected void emitOperandHelper(int reg, AMD64Address addr) {
        assert (reg & 0x07) == reg;
        int regenc = reg << 3;

        Register base = addr.getBase();
        Register index = addr.getIndex();

        AMD64Address.Scale scale = addr.getScale();
        int disp = addr.getDisplacement();

        if (base.equals(Register.Frame)) {
            assert frameRegister != null : "cannot use register " + Register.Frame + " in assembler with null register configuration";
            base = frameRegister;
        }

        if (base.equals(AMD64.rip)) { // also matches Placeholder
            // [00 000 101] disp32
            assert index.equals(Register.None) : "cannot use RIP relative addressing with index register";
            emitByte(0x05 | regenc);
            emitInt(disp);
        } else if (base.isValid()) {
            int baseenc = base.isValid() ? encode(base) : 0;
            if (index.isValid()) {
                int indexenc = encode(index) << 3;
                // [base + indexscale + disp]
                if (disp == 0 && !base.equals(rbp) && !base.equals(r13)) {
                    // [base + indexscale]
                    // [00 reg 100][ss index base]
                    assert !index.equals(rsp) : "illegal addressing mode";
                    emitByte(0x04 | regenc);
                    emitByte(scale.log2 << 6 | indexenc | baseenc);
                } else if (isByte(disp)) {
                    // [base + indexscale + imm8]
                    // [01 reg 100][ss index base] imm8
                    assert !index.equals(rsp) : "illegal addressing mode";
                    emitByte(0x44 | regenc);
                    emitByte(scale.log2 << 6 | indexenc | baseenc);
                    emitByte(disp & 0xFF);
                } else {
                    // [base + indexscale + disp32]
                    // [10 reg 100][ss index base] disp32
                    assert !index.equals(rsp) : "illegal addressing mode";
                    emitByte(0x84 | regenc);
                    emitByte(scale.log2 << 6 | indexenc | baseenc);
                    emitInt(disp);
                }
            } else if (base.equals(rsp) || base.equals(r12)) {
                // [rsp + disp]
                if (disp == 0) {
                    // [rsp]
                    // [00 reg 100][00 100 100]
                    emitByte(0x04 | regenc);
                    emitByte(0x24);
                } else if (isByte(disp)) {
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
                assert !base.equals(rsp) && !base.equals(r12) : "illegal addressing mode";
                if (disp == 0 && !base.equals(rbp) && !base.equals(r13)) {
                    // [base]
                    // [00 reg base]
                    emitByte(0x00 | regenc | baseenc);
                } else if (isByte(disp)) {
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
                int indexenc = encode(index) << 3;
                // [indexscale + disp]
                // [00 reg 100][ss index 101] disp32
                assert !index.equals(rsp) : "illegal addressing mode";
                emitByte(0x04 | regenc);
                emitByte(scale.log2 << 6 | indexenc | 0x05);
                emitInt(disp);
            } else {
                // [disp] ABSOLUTE
                // [00 reg 100][00 100 101] disp32
                emitByte(0x04 | regenc);
                emitByte(0x25);
                emitInt(disp);
            }
        }
    }

    public final void addl(AMD64Address dst, int imm32) {
        prefix(dst);
        emitArithOperand(0x81, 0, dst, imm32);
    }

    public final void addl(Register dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xC0, dst, imm32);
    }

    public final void addl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x03);
        emitOperandHelper(dst, src);
    }

    public final void addl(Register dst, Register src) {
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

    public final void addsd(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM && src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x58);
        emitByte(0xC0 | encode);
    }

    public final void addsd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x58);
        emitOperandHelper(dst, src);
    }

    public final void addss(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM && src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x58);
        emitByte(0xC0 | encode);
    }

    public final void addss(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x58);
        emitOperandHelper(dst, src);
    }

    public final void andl(Register dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xE0, dst, imm32);
    }

    public final void andl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x23);
        emitOperandHelper(dst, src);
    }

    public final void andl(Register dst, Register src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x23, 0xC0, dst, src);
    }

    public final void bsfq(Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBC);
        emitByte(0xC0 | encode);
    }

    public final void bsfq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xBC);
        emitOperandHelper(dst, src);
    }

    public final void bsrq(Register dst, Register src) {
        assert !supports(CPUFeature.LZCNT);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBD);
        emitByte(0xC0 | encode);
    }

    public final void bsrq(Register dst, AMD64Address src) {
        assert !supports(CPUFeature.LZCNT);
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xBD);
        emitOperandHelper(dst, src);
    }

    public final void bsrl(Register dst, Register src) {
        assert !supports(CPUFeature.LZCNT);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBD);
        emitByte(0xC0 | encode);
    }

    public final void bsrl(Register dst, AMD64Address src) {
        assert !supports(CPUFeature.LZCNT);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xBD);
        emitOperandHelper(dst, src);
    }

    public final void bswapl(Register reg) {
        int encode = prefixAndEncode(reg.encoding);
        emitByte(0x0F);
        emitByte(0xC8 | encode);
    }

    public final void cdql() {
        emitByte(0x99);
    }

    public final void cmovl(ConditionFlag cc, Register dst, Register src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitByte(0xC0 | encode);
    }

    public final void cmovl(ConditionFlag cc, Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitOperandHelper(dst, src);
    }

    public final void cmpl(Register dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xF8, dst, imm32);
    }

    public final void cmpl(Register dst, Register src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x3B, 0xC0, dst, src);
    }

    public final void cmpl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x3B);
        emitOperandHelper(dst, src);
    }

    public final void cmpl(AMD64Address dst, int imm32) {
        prefix(dst);
        emitByte(0x81);
        emitOperandHelper(7, dst);
        emitInt(imm32);
    }

    // The 32-bit cmpxchg compares the value at adr with the contents of X86.rax,
    // and stores reg into adr if so; otherwise, the value at adr is loaded into X86.rax,.
    // The ZF is set if the compared values were equal, and cleared otherwise.
    public final void cmpxchgl(Register reg, AMD64Address adr) { // cmpxchg
        prefix(adr, reg);
        emitByte(0x0F);
        emitByte(0xB1);
        emitOperandHelper(reg, adr);
    }

    public final void cvtsd2ss(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x5A);
        emitOperandHelper(dst, src);
    }

    public final void cvtsd2ss(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5A);
        emitByte(0xC0 | encode);
    }

    public final void cvtsi2sdl(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x2A);
        emitOperandHelper(dst, src);
    }

    public final void cvtsi2sdl(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2A);
        emitByte(0xC0 | encode);
    }

    public final void cvtsi2ssl(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x2A);
        emitOperandHelper(dst, src);
    }

    public final void cvtsi2ssl(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2A);
        emitByte(0xC0 | encode);
    }

    public final void cvtss2sd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x5A);
        emitOperandHelper(dst, src);
    }

    public final void cvtss2sd(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5A);
        emitByte(0xC0 | encode);
    }

    public final void cvttsd2sil(Register dst, AMD64Address src) {
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x2C);
        emitOperandHelper(dst, src);
    }

    public final void cvttsd2sil(Register dst, Register src) {
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2C);
        emitByte(0xC0 | encode);
    }

    public final void cvttss2sil(Register dst, AMD64Address src) {
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x2C);
        emitOperandHelper(dst, src);
    }

    public final void cvttss2sil(Register dst, Register src) {
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2C);
        emitByte(0xC0 | encode);
    }

    protected final void decl(AMD64Address dst) {
        prefix(dst);
        emitByte(0xFF);
        emitOperandHelper(1, dst);
    }

    public final void divsd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x5E);
        emitOperandHelper(dst, src);
    }

    public final void divsd(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5E);
        emitByte(0xC0 | encode);
    }

    public final void divss(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x5E);
        emitOperandHelper(dst, src);
    }

    public final void divss(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5E);
        emitByte(0xC0 | encode);
    }

    public final void hlt() {
        emitByte(0xF4);
    }

    public final void idivl(Register src) {
        int encode = prefixAndEncode(src.encoding);
        emitByte(0xF7);
        emitByte(0xF8 | encode);
    }

    public final void divl(Register src) {
        int encode = prefixAndEncode(src.encoding);
        emitByte(0xF7);
        emitByte(0xF0 | encode);
    }

    public final void imull(Register dst, Register src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xAF);
        emitByte(0xC0 | encode);
    }

    public final void imull(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xAF);
        emitOperandHelper(dst, src);
    }

    public final void imull(Register dst, Register src, int value) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        if (isByte(value)) {
            emitByte(0x6B);
            emitByte(0xC0 | encode);
            emitByte(value & 0xFF);
        } else {
            emitByte(0x69);
            emitByte(0xC0 | encode);
            emitInt(value);
        }
    }

    protected final void incl(AMD64Address dst) {
        prefix(dst);
        emitByte(0xFF);
        emitOperandHelper(0, dst);
    }

    public void jcc(ConditionFlag cc, int jumpTarget, boolean forceDisp32) {
        int shortSize = 2;
        int longSize = 6;
        long disp = jumpTarget - position();
        if (!forceDisp32 && isByte(disp - shortSize)) {
            // 0111 tttn #8-bit disp
            emitByte(0x70 | cc.getValue());
            emitByte((int) ((disp - shortSize) & 0xFF));
        } else {
            // 0000 1111 1000 tttn #32-bit disp
            assert isInt(disp - longSize) : "must be 32bit offset (call4)";
            emitByte(0x0F);
            emitByte(0x80 | cc.getValue());
            emitInt((int) (disp - longSize));
        }
    }

    public final void jcc(ConditionFlag cc, Label l) {
        assert (0 <= cc.getValue()) && (cc.getValue() < 16) : "illegal cc";
        if (l.isBound()) {
            jcc(cc, l.position(), false);
        } else {
            // Note: could eliminate cond. jumps to this jump if condition
            // is the same however, seems to be rather unlikely case.
            // Note: use jccb() if label to be bound is very close to get
            // an 8-bit displacement
            l.addPatchAt(position());
            emitByte(0x0F);
            emitByte(0x80 | cc.getValue());
            emitInt(0);
        }

    }

    public final void jccb(ConditionFlag cc, Label l) {
        if (l.isBound()) {
            int shortSize = 2;
            int entry = l.position();
            assert isByte(entry - (position() + shortSize)) : "Dispacement too large for a short jmp";
            long disp = entry - position();
            // 0111 tttn #8-bit disp
            emitByte(0x70 | cc.getValue());
            emitByte((int) ((disp - shortSize) & 0xFF));
        } else {
            l.addPatchAt(position());
            emitByte(0x70 | cc.getValue());
            emitByte(0);
        }
    }

    public final void jmp(int jumpTarget, boolean forceDisp32) {
        int shortSize = 2;
        int longSize = 5;
        long disp = jumpTarget - position();
        if (!forceDisp32 && isByte(disp - shortSize)) {
            emitByte(0xEB);
            emitByte((int) ((disp - shortSize) & 0xFF));
        } else {
            emitByte(0xE9);
            emitInt((int) (disp - longSize));
        }
    }

    @Override
    public final void jmp(Label l) {
        if (l.isBound()) {
            jmp(l.position(), false);
        } else {
            // By default, forward jumps are always 32-bit displacements, since
            // we can't yet know where the label will be bound. If you're sure that
            // the forward jump will not run beyond 256 bytes, use jmpb to
            // force an 8-bit displacement.

            l.addPatchAt(position());
            emitByte(0xE9);
            emitInt(0);
        }
    }

    public final void jmp(Register entry) {
        int encode = prefixAndEncode(entry.encoding);
        emitByte(0xFF);
        emitByte(0xE0 | encode);
    }

    public final void jmpb(Label l) {
        if (l.isBound()) {
            int shortSize = 2;
            int entry = l.position();
            assert isByte((entry - position()) + shortSize) : "Dispacement too large for a short jmp";
            long offs = entry - position();
            emitByte(0xEB);
            emitByte((int) ((offs - shortSize) & 0xFF));
        } else {

            l.addPatchAt(position());
            emitByte(0xEB);
            emitByte(0);
        }
    }

    public final void leaq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x8D);
        emitOperandHelper(dst, src);
    }

    public final void leave() {
        emitByte(0xC9);
    }

    public final void lock() {
        emitByte(0xF0);
    }

    public final void movapd(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
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

    public final void movaps(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
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

    public final void movb(AMD64Address dst, int imm8) {
        prefix(dst);
        emitByte(0xC6);
        emitOperandHelper(0, dst);
        emitByte(imm8);
    }

    public final void movb(AMD64Address dst, Register src) {
        assert src.getRegisterCategory() == AMD64.CPU : "must have byte register";
        prefix(dst, src, true);
        emitByte(0x88);
        emitOperandHelper(src, dst);
    }

    public final void movdl(Register dst, Register src) {
        if (dst.getRegisterCategory() == AMD64.XMM) {
            assert src.getRegisterCategory() != AMD64.XMM : "does this hold?";
            emitByte(0x66);
            int encode = prefixAndEncode(dst.encoding, src.encoding);
            emitByte(0x0F);
            emitByte(0x6E);
            emitByte(0xC0 | encode);
        } else if (src.getRegisterCategory() == AMD64.XMM) {
            assert dst.getRegisterCategory() != AMD64.XMM;
            emitByte(0x66);
            // swap src/dst to get correct prefix
            int encode = prefixAndEncode(src.encoding, dst.encoding);
            emitByte(0x0F);
            emitByte(0x7E);
            emitByte(0xC0 | encode);
        }
    }

    public final void movl(Register dst, int imm32) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xB8 | encode);
        emitInt(imm32);
    }

    public final void movl(Register dst, Register src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x8B);
        emitByte(0xC0 | encode);
    }

    public final void movl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x8B);
        emitOperandHelper(dst, src);
    }

    public final void movl(AMD64Address dst, int imm32) {
        prefix(dst);
        emitByte(0xC7);
        emitOperandHelper(0, dst);
        emitInt(imm32);
    }

    public final void movl(AMD64Address dst, Register src) {
        prefix(dst, src);
        emitByte(0x89);
        emitOperandHelper(src, dst);
    }

    /**
     * New CPUs require use of movsd and movss to avoid partial register stall when loading from
     * memory. But for old Opteron use movlpd instead of movsd. The selection is done in
     * {@link AMD64MacroAssembler#movdbl(Register, AMD64Address)} and
     * {@link AMD64MacroAssembler#movflt(Register, Register)}.
     */
    public final void movlpd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x12);
        emitOperandHelper(dst, src);
    }

    public final void movq(Register dst, AMD64Address src) {
        if (dst.getRegisterCategory() == AMD64.XMM) {
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

    public final void movq(Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x8B);
        emitByte(0xC0 | encode);
    }

    public final void movq(AMD64Address dst, Register src) {
        if (src.getRegisterCategory() == AMD64.XMM) {
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

    public final void movsbl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xBE);
        emitOperandHelper(dst, src);
    }

    public final void movsbl(Register dst, Register src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding, true);
        emitByte(0x0F);
        emitByte(0xBE);
        emitByte(0xC0 | encode);
    }

    public final void movsbq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xBE);
        emitOperandHelper(dst, src);
    }

    public final void movsbq(Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBE);
        emitByte(0xC0 | encode);
    }

    public final void movsd(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x10);
        emitByte(0xC0 | encode);
    }

    public final void movsd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x10);
        emitOperandHelper(dst, src);
    }

    public final void movsd(AMD64Address dst, Register src) {
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0x11);
        emitOperandHelper(src, dst);
    }

    public final void movss(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x10);
        emitByte(0xC0 | encode);
    }

    public final void movss(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x10);
        emitOperandHelper(dst, src);
    }

    public final void movss(AMD64Address dst, Register src) {
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0x11);
        emitOperandHelper(src, dst);
    }

    public final void movswl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xBF);
        emitOperandHelper(dst, src);
    }

    public final void movswl(Register dst, Register src) {
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBF);
        emitByte(0xC0 | encode);
    }

    public final void movswq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xBF);
        emitOperandHelper(dst, src);
    }

    public final void movswq(Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xBF);
        emitByte(0xC0 | encode);
    }

    public final void movw(AMD64Address dst, int imm16) {
        emitByte(0x66); // switch to 16-bit mode
        prefix(dst);
        emitByte(0xC7);
        emitOperandHelper(0, dst);
        emitShort(imm16);
    }

    public final void movw(AMD64Address dst, Register src) {
        emitByte(0x66);
        prefix(dst, src);
        emitByte(0x89);
        emitOperandHelper(src, dst);
    }

    public final void movzbl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xB6);
        emitOperandHelper(dst, src);
    }

    public final void movzwl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xB7);
        emitOperandHelper(dst, src);
    }

    public final void mulsd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x59);
        emitOperandHelper(dst, src);
    }

    public final void mulsd(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;

        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x59);
        emitByte(0xC0 | encode);
    }

    public final void mulss(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;

        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x59);
        emitOperandHelper(dst, src);
    }

    public final void mulss(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x59);
        emitByte(0xC0 | encode);
    }

    public final void negl(Register dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xF7);
        emitByte(0xD8 | encode);
    }

    public final void notl(Register dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xF7);
        emitByte(0xD0 | encode);
    }

    public final void ensureUniquePC() {
        nop();
    }

    public final void nop() {
        nop(1);
    }

    public void nop(int count) {
        int i = count;
        if (UseNormalNop) {
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

        if (UseAddressNop) {
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

    public final void orl(Register dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xC8, dst, imm32);
    }

    public final void orl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0B);
        emitOperandHelper(dst, src);
    }

    public final void orl(Register dst, Register src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x0B, 0xC0, dst, src);
    }

    public final void popcntl(Register dst, AMD64Address src) {
        assert supports(CPUFeature.POPCNT);
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xB8);
        emitOperandHelper(dst, src);
    }

    public final void popcntl(Register dst, Register src) {
        assert supports(CPUFeature.POPCNT);
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xB8);
        emitByte(0xC0 | encode);
    }

    public final void popcntq(Register dst, AMD64Address src) {
        assert supports(CPUFeature.POPCNT);
        emitByte(0xF3);
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xB8);
        emitOperandHelper(dst, src);
    }

    public final void popcntq(Register dst, Register src) {
        assert supports(CPUFeature.POPCNT);
        emitByte(0xF3);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xB8);
        emitByte(0xC0 | encode);
    }

    public final void pop(Register dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0x58 | encode);
    }

    public void popfq() {
        emitByte(0x9D);
    }

    public final void ptest(Register dst, Register src) {
        assert supports(CPUFeature.SSE4_1);
        emitByte(0x66);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x38);
        emitByte(0x17);
        emitByte(0xC0 | encode);
    }

    public final void push(Register src) {
        int encode = prefixAndEncode(src.encoding);
        emitByte(0x50 | encode);
    }

    public void pushfq() {
        emitByte(0x9c);
    }

    public final void pxor(Register dst, Register src) {
        emitByte(0x66);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xEF);
        emitByte(0xC0 | encode);
    }

    public final void ret(int imm16) {
        if (imm16 == 0) {
            emitByte(0xC3);
        } else {
            emitByte(0xC2);
            emitShort(imm16);
        }
    }

    public final void sarl(Register dst, int imm8) {
        int encode = prefixAndEncode(dst.encoding);
        assert isShiftCount(imm8) : "illegal shift count";
        if (imm8 == 1) {
            emitByte(0xD1);
            emitByte(0xF8 | encode);
        } else {
            emitByte(0xC1);
            emitByte(0xF8 | encode);
            emitByte(imm8);
        }
    }

    public final void sarl(Register dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xF8 | encode);
    }

    public final void shll(Register dst, int imm8) {
        assert isShiftCount(imm8) : "illegal shift count";
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

    public final void shll(Register dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE0 | encode);
    }

    public final void shrl(Register dst, int imm8) {
        assert isShiftCount(imm8) : "illegal shift count";
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xC1);
        emitByte(0xE8 | encode);
        emitByte(imm8);
    }

    public final void shrl(Register dst) {
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE8 | encode);
    }

    public final void sqrtsd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x51);
        emitOperandHelper(dst, src);
    }

    public final void sqrtsd(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        // HMM Table D-1 says sse2
        // assert is64 || target.supportsSSE();
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x51);
        emitByte(0xC0 | encode);
    }

    public final void subl(AMD64Address dst, int imm32) {
        prefix(dst);
        emitArithOperand(0x81, 5, dst, imm32);
    }

    public final void subl(Register dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xE8, dst, imm32);
    }

    public final void subl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x2B);
        emitOperandHelper(dst, src);
    }

    public final void subl(Register dst, Register src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x2B, 0xC0, dst, src);
    }

    public final void subsd(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5C);
        emitByte(0xC0 | encode);
    }

    public final void subsd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;

        emitByte(0xF2);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x5C);
        emitOperandHelper(dst, src);
    }

    public final void subss(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x5C);
        emitByte(0xC0 | encode);
    }

    public final void subss(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;

        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x5C);
        emitOperandHelper(dst, src);
    }

    public final void testl(Register dst, int imm32) {
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

    public final void testl(AMD64Address dst, int imm32) {
        prefixq(dst);
        emitByte(0xF7);
        emitOperandHelper(0, dst);
        emitInt(imm32);
    }

    public final void testl(Register dst, Register src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x85, 0xC0, dst, src);
    }

    public final void testl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x85);
        emitOperandHelper(dst, src);
    }

    public final void ucomisd(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0x66);
        ucomiss(dst, src);
    }

    public final void ucomisd(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0x66);
        ucomiss(dst, src);
    }

    public final void ucomiss(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;

        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x2E);
        emitOperandHelper(dst, src);
    }

    public final void ucomiss(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        assert src.getRegisterCategory() == AMD64.XMM;
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2E);
        emitByte(0xC0 | encode);
    }

    public final void xorl(Register dst, int imm32) {
        prefix(dst);
        emitArith(0x81, 0xF0, dst, imm32);
    }

    public final void xorl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x33);
        emitOperandHelper(dst, src);
    }

    public final void xorl(Register dst, Register src) {
        prefixAndEncode(dst.encoding, src.encoding);
        emitArith(0x33, 0xC0, dst, src);
    }

    public final void andpd(Register dst, Register src) {
        emitByte(0x66);
        andps(dst, src);
    }

    public final void andpd(Register dst, AMD64Address src) {
        emitByte(0x66);
        andps(dst, src);
    }

    public final void andps(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM && src.getRegisterCategory() == AMD64.XMM;
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x54);
        emitByte(0xC0 | encode);
    }

    public final void andps(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x54);
        emitOperandHelper(dst, src);
    }

    public final void orpd(Register dst, Register src) {
        emitByte(0x66);
        orps(dst, src);
    }

    public final void orpd(Register dst, AMD64Address src) {
        emitByte(0x66);
        orps(dst, src);
    }

    public final void orps(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM && src.getRegisterCategory() == AMD64.XMM;
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x56);
        emitByte(0xC0 | encode);
    }

    public final void orps(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x56);
        emitOperandHelper(dst, src);
    }

    public final void xorpd(Register dst, Register src) {
        emitByte(0x66);
        xorps(dst, src);
    }

    public final void xorpd(Register dst, AMD64Address src) {
        emitByte(0x66);
        xorps(dst, src);
    }

    public final void xorps(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM && src.getRegisterCategory() == AMD64.XMM;
        int encode = prefixAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x57);
        emitByte(0xC0 | encode);
    }

    public final void xorps(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x57);
        emitOperandHelper(dst, src);
    }

    protected final void decl(Register dst) {
        // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC8 | encode);
    }

    protected final void incl(Register dst) {
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        int encode = prefixAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC0 | encode);
    }

    private int prefixAndEncode(int regEnc) {
        return prefixAndEncode(regEnc, false);
    }

    private int prefixAndEncode(int regEnc, boolean byteinst) {
        if (regEnc >= 8) {
            emitByte(Prefix.REXB);
            return regEnc - 8;
        } else if (byteinst && regEnc >= 4) {
            emitByte(Prefix.REX);
        }
        return regEnc;
    }

    private int prefixqAndEncode(int regEnc) {
        if (regEnc < 8) {
            emitByte(Prefix.REXW);
            return regEnc;
        } else {
            emitByte(Prefix.REXWB);
            return regEnc - 8;
        }
    }

    private int prefixAndEncode(int dstEnc, int srcEnc) {
        return prefixAndEncode(dstEnc, srcEnc, false);
    }

    private int prefixAndEncode(int dstEncoding, int srcEncoding, boolean byteinst) {
        int srcEnc = srcEncoding;
        int dstEnc = dstEncoding;
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
     * Creates prefix and the encoding of the lower 6 bits of the ModRM-Byte. It emits an operand
     * prefix. If the given operands exceed 3 bits, the 4th bit is encoded in the prefix.
     * 
     * @param regEncoding the encoding of the register part of the ModRM-Byte
     * @param rmEncoding the encoding of the r/m part of the ModRM-Byte
     * @return the lower 6 bits of the ModRM-Byte that should be emitted
     */
    private int prefixqAndEncode(int regEncoding, int rmEncoding) {
        int rmEnc = rmEncoding;
        int regEnc = regEncoding;
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

    private void prefix(Register reg) {
        if (reg.encoding >= 8) {
            emitByte(Prefix.REXB);
        }
    }

    private static boolean needsRex(Register reg) {
        return reg.encoding >= MinEncodingNeedsRex;
    }

    private void prefix(AMD64Address adr) {
        if (needsRex(adr.getBase())) {
            if (needsRex(adr.getIndex())) {
                emitByte(Prefix.REXXB);
            } else {
                emitByte(Prefix.REXB);
            }
        } else {
            if (needsRex(adr.getIndex())) {
                emitByte(Prefix.REXX);
            }
        }
    }

    private void prefixq(AMD64Address adr) {
        if (needsRex(adr.getBase())) {
            if (needsRex(adr.getIndex())) {
                emitByte(Prefix.REXWXB);
            } else {
                emitByte(Prefix.REXWB);
            }
        } else {
            if (needsRex(adr.getIndex())) {
                emitByte(Prefix.REXWX);
            } else {
                emitByte(Prefix.REXW);
            }
        }
    }

    private void prefix(AMD64Address adr, Register reg) {
        prefix(adr, reg, false);
    }

    private void prefix(AMD64Address adr, Register reg, boolean byteinst) {
        if (reg.encoding < 8) {
            if (needsRex(adr.getBase())) {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXXB);
                } else {
                    emitByte(Prefix.REXB);
                }
            } else {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXX);
                } else if (byteinst && reg.encoding >= 4) {
                    emitByte(Prefix.REX);
                }
            }
        } else {
            if (needsRex(adr.getBase())) {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXRXB);
                } else {
                    emitByte(Prefix.REXRB);
                }
            } else {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXRX);
                } else {
                    emitByte(Prefix.REXR);
                }
            }
        }
    }

    private void prefixq(AMD64Address adr, Register src) {
        if (src.encoding < 8) {
            if (needsRex(adr.getBase())) {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXWXB);
                } else {
                    emitByte(Prefix.REXWB);
                }
            } else {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXWX);
                } else {
                    emitByte(Prefix.REXW);
                }
            }
        } else {
            if (needsRex(adr.getBase())) {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXWRXB);
                } else {
                    emitByte(Prefix.REXWRB);
                }
            } else {
                if (needsRex(adr.getIndex())) {
                    emitByte(Prefix.REXWRX);
                } else {
                    emitByte(Prefix.REXWR);
                }
            }
        }
    }

    public final void addq(Register dst, int imm32) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xC0, dst, imm32);
    }

    public final void addq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x03);
        emitOperandHelper(dst, src);
    }

    public final void addq(Register dst, Register src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x03, 0xC0, dst, src);
    }

    public final void andq(Register dst, int imm32) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xE0, dst, imm32);
    }

    public final void andq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x23);
        emitOperandHelper(dst, src);
    }

    public final void andq(Register dst, Register src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x23, 0xC0, dst, src);
    }

    public final void bswapq(Register reg) {
        int encode = prefixqAndEncode(reg.encoding);
        emitByte(0x0F);
        emitByte(0xC8 | encode);
    }

    public final void cdqq() {
        emitByte(Prefix.REXW);
        emitByte(0x99);
    }

    public final void cmovq(ConditionFlag cc, Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitByte(0xC0 | encode);
    }

    public final void cmovq(ConditionFlag cc, Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitOperandHelper(dst, src);
    }

    public final void cmpq(AMD64Address dst, int imm32) {
        prefixq(dst);
        emitByte(0x81);
        emitOperandHelper(7, dst);
        emitInt(imm32);
    }

    public final void cmpq(Register dst, int imm32) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xF8, dst, imm32);
    }

    public final void cmpq(Register dst, Register src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x3B, 0xC0, dst, src);
    }

    public final void cmpq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x3B);
        emitOperandHelper(dst, src);
    }

    public final void cmpxchgq(Register reg, AMD64Address adr) {
        prefixq(adr, reg);
        emitByte(0x0F);
        emitByte(0xB1);
        emitOperandHelper(reg, adr);
    }

    public final void cvtsi2sdq(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0x2A);
        emitOperandHelper(dst, src);
    }

    public final void cvtsi2sdq(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2A);
        emitByte(0xC0 | encode);
    }

    public final void cvtsi2ssq(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0x2A);
        emitOperandHelper(dst, src);
    }

    public final void cvtsi2ssq(Register dst, Register src) {
        assert dst.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2A);
        emitByte(0xC0 | encode);
    }

    public final void cvttsd2siq(Register dst, AMD64Address src) {
        emitByte(0xF2);
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0x2C);
        emitOperandHelper(dst, src);
    }

    public final void cvttsd2siq(Register dst, Register src) {
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF2);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2C);
        emitByte(0xC0 | encode);
    }

    public final void cvttss2siq(Register dst, AMD64Address src) {
        emitByte(0xF3);
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0x2C);
        emitOperandHelper(dst, src);
    }

    public final void cvttss2siq(Register dst, Register src) {
        assert src.getRegisterCategory() == AMD64.XMM;
        emitByte(0xF3);
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0x2C);
        emitByte(0xC0 | encode);
    }

    protected final void decq(Register dst) {
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC8 | encode);
    }

    protected final void decq(AMD64Address dst) {
        prefixq(dst);
        emitByte(0xFF);
        emitOperandHelper(1, dst);
    }

    public final void divq(Register src) {
        int encode = prefixqAndEncode(src.encoding);
        emitByte(0xF7);
        emitByte(0xF0 | encode);
    }

    public final void idivq(Register src) {
        int encode = prefixqAndEncode(src.encoding);
        emitByte(0xF7);
        emitByte(0xF8 | encode);
    }

    public final void imulq(Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x0F);
        emitByte(0xAF);
        emitByte(0xC0 | encode);
    }

    public final void imulq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xAF);
        emitOperandHelper(dst, src);
    }

    public final void imulq(Register dst, Register src, int value) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        if (isByte(value)) {
            emitByte(0x6B);
            emitByte(0xC0 | encode);
            emitByte(value & 0xFF);
        } else {
            emitByte(0x69);
            emitByte(0xC0 | encode);
            emitInt(value);
        }
    }

    public final void incq(Register dst) {
        // Don't use it directly. Use Macroincrementq() instead.
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xFF);
        emitByte(0xC0 | encode);
    }

    public final void movq(Register dst, long imm64) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xB8 | encode);
        emitLong(imm64);
    }

    public final void movslq(Register dst, int imm32) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xC7);
        emitByte(0xC0 | encode);
        emitInt(imm32);
    }

    public final void movdq(Register dst, Register src) {

        // table D-1 says MMX/SSE2
        emitByte(0x66);

        if (dst.getRegisterCategory() == AMD64.XMM) {
            int encode = prefixqAndEncode(dst.encoding, src.encoding);
            emitByte(0x0F);
            emitByte(0x6E);
            emitByte(0xC0 | encode);
        } else if (src.getRegisterCategory() == AMD64.XMM) {

            // swap src/dst to get correct prefix
            int encode = prefixqAndEncode(src.encoding, dst.encoding);
            emitByte(0x0F);
            emitByte(0x7E);
            emitByte(0xC0 | encode);
        } else {
            throw new InternalError("should not reach here");
        }
    }

    public final void movdqu(Register dst, AMD64Address src) {
        emitByte(0xF3);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x6F);
        emitOperandHelper(dst, src);
    }

    public final void movslq(AMD64Address dst, int imm32) {
        prefixq(dst);
        emitByte(0xC7);
        emitOperandHelper(0, dst);
        emitInt(imm32);
    }

    public final void movslq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x63);
        emitOperandHelper(dst, src);
    }

    public final void movslq(Register dst, Register src) {
        int encode = prefixqAndEncode(dst.encoding, src.encoding);
        emitByte(0x63);
        emitByte(0xC0 | encode);
    }

    public final void negq(Register dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xF7);
        emitByte(0xD8 | encode);
    }

    public final void notq(Register dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xF7);
        emitByte(0xD0 | encode);
    }

    public final void orq(Register dst, int imm32) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xC8, dst, imm32);
    }

    public final void orq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0B);
        emitOperandHelper(dst, src);
    }

    public final void orq(Register dst, Register src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x0B, 0xC0, dst, src);
    }

    public final void sarq(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
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

    public final void sarq(Register dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xF8 | encode);
    }

    public final void shlq(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
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

    public final void shlq(Register dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE0 | encode);
    }

    public final void shrq(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xC1);
        emitByte(0xE8 | encode);
        emitByte(imm8);
    }

    public final void shrq(Register dst) {
        int encode = prefixqAndEncode(dst.encoding);
        emitByte(0xD3);
        emitByte(0xE8 | encode);
    }

    public final void subq(Register dst, int imm32) {
        subq(dst, imm32, false);
    }

    public final void subqWide(Register dst, int imm32) {
        subq(dst, imm32, true);
    }

    private void subq(Register dst, int imm32, boolean force32Imm) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xE8, dst, imm32, force32Imm);
    }

    public final void subq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x2B);
        emitOperandHelper(dst, src);
    }

    public final void subq(Register dst, Register src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x2B, 0xC0, dst, src);
    }

    public final void testq(Register dst, int imm32) {
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

    public final void testq(Register dst, Register src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x85, 0xC0, dst, src);
    }

    public final void testq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x85);
        emitOperandHelper(dst, src);
    }

    public final void testq(AMD64Address dst, int imm32) {
        prefixq(dst);
        emitByte(0xF7);
        emitOperandHelper(0, dst);
        emitInt(imm32);
    }

    public final void xorq(Register dst, int imm32) {
        prefixqAndEncode(dst.encoding);
        emitArith(0x81, 0xF0, dst, imm32);
    }

    public final void xorq(Register dst, Register src) {
        prefixqAndEncode(dst.encoding, src.encoding);
        emitArith(0x33, 0xC0, dst, src);
    }

    public final void xorq(Register dst, AMD64Address src) {
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
                addl(new AMD64Address(rsp, 0), 0); // Assert the lock# signal here
            }
        }
    }

    @Override
    protected final void patchJumpTarget(int branch, int branchTarget) {
        int op = getByte(branch);
        assert op == 0xE8 // call
                        ||
                        op == 0x00 // jump table entry
                        || op == 0xE9 // jmp
                        || op == 0xEB // short jmp
                        || (op & 0xF0) == 0x70 // short jcc
                        || op == 0x0F && (getByte(branch + 1) & 0xF0) == 0x80 // jcc
        : "Invalid opcode at patch point branch=" + branch + ", branchTarget=" + branchTarget + ", op=" + op;

        if (op == 0x00) {
            int offsetToJumpTableBase = getShort(branch + 1);
            int jumpTableBase = branch - offsetToJumpTableBase;
            int imm32 = branchTarget - jumpTableBase;
            emitInt(imm32, branch);
        } else if (op == 0xEB || (op & 0xF0) == 0x70) {

            // short offset operators (jmp and jcc)
            final int imm8 = branchTarget - (branch + 2);
            /*
             * Since a wrongly patched short branch can potentially lead to working but really bad
             * behaving code we should always fail with an exception instead of having an assert.
             */
            if (!NumUtil.isByte(imm8)) {
                throw new InternalError("branch displacement out of range: " + imm8);
            }
            emitByte(imm8, branch + 1);

        } else {

            int off = 1;
            if (op == 0x0F) {
                off = 2;
            }

            int imm32 = branchTarget - (branch + 4 + off);
            emitInt(imm32, branch + off);
        }
    }

    public void nullCheck(Register r) {
        testl(AMD64.rax, new AMD64Address(r, 0));
    }

    @Override
    public void align(int modulus) {
        if (position() % modulus != 0) {
            nop(modulus - (position() % modulus));
        }
    }

    /**
     * Emits a direct call instruction. Note that the actual call target is not specified, because
     * all calls need patching anyway. Therefore, 0 is emitted as the call target, and the user is
     * responsible to add the call address to the appropriate patching tables.
     */
    public final void call() {
        emitByte(0xE8);
        emitInt(0);
    }

    public final void call(Register src) {
        int encode = prefixAndEncode(src.encoding);
        emitByte(0xFF);
        emitByte(0xD0 | encode);
    }

    public final void int3() {
        emitByte(0xCC);
    }

    private void emitx87(int b1, int b2, int i) {
        assert 0 <= i && i < 8 : "illegal stack offset";
        emitByte(b1);
        emitByte(b2 + i);
    }

    public final void fldd(AMD64Address src) {
        emitByte(0xDD);
        emitOperandHelper(0, src);
    }

    public final void flds(AMD64Address src) {
        emitByte(0xD9);
        emitOperandHelper(0, src);
    }

    public final void fldln2() {
        emitByte(0xD9);
        emitByte(0xED);
    }

    public final void fldlg2() {
        emitByte(0xD9);
        emitByte(0xEC);
    }

    public final void fyl2x() {
        emitByte(0xD9);
        emitByte(0xF1);
    }

    public final void fstps(AMD64Address src) {
        emitByte(0xD9);
        emitOperandHelper(3, src);
    }

    public final void fstpd(AMD64Address src) {
        emitByte(0xDD);
        emitOperandHelper(3, src);
    }

    private void emitFPUArith(int b1, int b2, int i) {
        assert 0 <= i && i < 8 : "illegal FPU register: " + i;
        emitByte(b1);
        emitByte(b2 + i);
    }

    public void ffree(int i) {
        emitFPUArith(0xDD, 0xC0, i);
    }

    public void fincstp() {
        emitByte(0xD9);
        emitByte(0xF7);
    }

    public void fxch(int i) {
        emitFPUArith(0xD9, 0xC8, i);
    }

    public void fnstswAX() {
        emitByte(0xDF);
        emitByte(0xE0);
    }

    public void fwait() {
        emitByte(0x9B);
    }

    public void fprem() {
        emitByte(0xD9);
        emitByte(0xF8);
    }

    public final void fsin() {
        emitByte(0xD9);
        emitByte(0xFE);
    }

    public final void fcos() {
        emitByte(0xD9);
        emitByte(0xFF);
    }

    public final void fptan() {
        emitByte(0xD9);
        emitByte(0xF2);
    }

    public final void fstp(int i) {
        emitx87(0xDD, 0xD8, i);
    }

    @Override
    public AMD64Address makeAddress(Register base, int displacement) {
        return new AMD64Address(base, displacement);
    }

    @Override
    public AMD64Address getPlaceholder() {
        return Placeholder;
    }

    private void prefetchPrefix(AMD64Address src) {
        prefix(src);
        emitByte(0x0F);
    }

    public void prefetchnta(AMD64Address src) {
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(0, src);
    }

    void prefetchr(AMD64Address src) {
        assert supports(CPUFeature.AMD_3DNOW_PREFETCH);
        prefetchPrefix(src);
        emitByte(0x0D);
        emitOperandHelper(0, src);
    }

    public void prefetcht0(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(1, src);
    }

    public void prefetcht1(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(2, src);
    }

    public void prefetcht2(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefix(src);
        emitByte(0x0f);
        emitByte(0x18);
        emitOperandHelper(3, src);
    }

    public void prefetchw(AMD64Address src) {
        assert supports(CPUFeature.AMD_3DNOW_PREFETCH);
        prefix(src);
        emitByte(0x0f);
        emitByte(0x0D);
        emitOperandHelper(1, src);
    }

}
