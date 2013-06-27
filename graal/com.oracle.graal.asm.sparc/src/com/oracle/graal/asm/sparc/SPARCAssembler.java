/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.asm.sparc;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.asm.*;
import com.oracle.graal.hotspot.HotSpotGraalRuntime;
import com.oracle.graal.sparc.*;

import static com.oracle.graal.sparc.SPARC.*;

/**
 * This class implements an assembler that can encode most SPARC instructions.
 */
public abstract class SPARCAssembler extends AbstractAssembler {

    /**
     * Constructs an assembler for the AMD64 architecture.
     * 
     * @param registerConfig the register configuration used to bind {@link Register#Frame} and
     *            {@link Register#CallerFrame} to physical registers. This value can be null if this
     *            assembler instance will not be used to assemble instructions using these logical
     *            registers.
     */
    public SPARCAssembler(TargetDescription target, RegisterConfig registerConfig) {
        super(target);
    }

    public static class Fmt00a {

        private int rd;
        private int op2;
        private int imm22;

        public Fmt00a(Op2s op2, int imm22, Register rd) {
            this.op2 = op2.getValue();
            this.imm22 = imm22;
            this.rd = rd.encoding();
        }

        public void emit(SPARCAssembler masm) {
            assert rd < 0x40;
            assert op2 < 0x8;
            masm.emitInt(Ops.BranchOp.getValue() << 30 | rd << 25 | op2 << 22 | (imm22 & 0x003fffff));
        }
    }

    // @formatter:off
    /**
     * Instruction format for branches.
     * 
     * | 00  |a | cond | op2 |             disp22                      |
     * |31 30|29|28  25|24 22|21                                      0|
     */
    // @formatter:on
    public static class Fmt2b {

        public Fmt2b(SPARCAssembler masm, int op, int a, int cond, int op2, int disp22) {
            assert op == 0;
            assert op == 0;
            assert cond < 0x10;
            assert op2 < 0x8;
            masm.emitInt(op << 30 | a << 29 | cond << 25 | op2 << 22 | (disp22 & 0x003fffff));
        }
    }

    // @formatter:off
    /**
     * Instruction format for conditional branches.
     * 
     * | 00  |a | cond | op2 |cc1|cc0|p |             disp19           |
     * |31 30|29|28  25|24 22|21 |20 |19|                             0|
     */
    // @formatter:on
    public static class Fmt00c {

        private static final int OP_SHIFT = 30;
        private static final int A_SHIFT = 29;
        private static final int COND_SHIFT = 25;
        private static final int OP2_SHIFT = 22;
        private static final int CC_SHIFT = 20;
        private static final int P_SHIFT = 19;
        private static final int DISP19_SHIFT = 0;

        // @formatter:off
        private static final int OP_MASK     = 0b11000000000000000000000000000000;
        private static final int A_MASK      = 0b00100000000000000000000000000000;
        private static final int COND_MASK   = 0b00011110000000000000000000000000;
        private static final int OP2_MASK    = 0b00000001110000000000000000000000;
        private static final int CC_MASK     = 0b00000000001100000000000000000000;
        private static final int P_MASK      = 0b00000000000010000000000000000000;
        private static final int DISP19_MASK = 0b00000000000001111111111111111111;
        // @formatter:on

        private int a;
        private int cond;
        private int op2;
        private int cc;
        private int p;
        private int disp19;
        private Label label;

        private Fmt00c(int a, int cond, int op2, int cc, int p, int disp19) {
            setA(a);
            setCond(cond);
            setOp2(op2);
            setCc(cc);
            setP(p);
            setDisp19(disp19);
            verify();
        }

        public Fmt00c(int a, ConditionFlag cond, Op2s op2, CC cc, int p, int disp19) {
            this(a, cond.getValue(), op2.getValue(), cc.getValue(), p, disp19);
        }

        public Fmt00c(int a, ConditionFlag cond, Op2s op2, CC cc, int p, Label label) {
            this(a, cond.getValue(), op2.getValue(), cc.getValue(), p, 0);
            this.label = label;
        }

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }

        public int getCond() {
            return cond;
        }

        public void setCond(int cond) {
            this.cond = cond;
        }

        public int getOp2() {
            return op2;
        }

        public void setOp2(int op2) {
            this.op2 = op2;
        }

        public int getCc() {
            return cc;
        }

        public void setCc(int cc) {
            this.cc = cc;
        }

        public int getP() {
            return p;
        }

        public void setP(int p) {
            this.p = p;
        }

        /**
         * Return the displacement in bytes.
         */
        public int getDisp19() {
            return disp19 << 2;
        }

        /**
         * The instructions requires displacements to be word-sized.
         */
        public void setDisp19(int disp19) {
            this.disp19 = disp19 >> 2;
        }

        private int getInstructionBits() {
            return Ops.BranchOp.getValue() << OP_SHIFT | a << A_SHIFT | cond << COND_SHIFT | op2 << OP2_SHIFT | cc << CC_SHIFT | p << P_SHIFT | (disp19 & DISP19_MASK) << DISP19_SHIFT;
        }

        public static Fmt00c read(SPARCAssembler masm, int pos) {
            final int inst = masm.codeBuffer.getInt(pos);

            // Make sure it's the right instruction:
            final int op = (inst & OP_MASK) >> OP_SHIFT;
            assert op == Ops.BranchOp.getValue();

            // Get the instruction fields:
            final int a = (inst & A_MASK) >> A_SHIFT;
            final int cond = (inst & COND_MASK) >> COND_SHIFT;
            final int op2 = (inst & OP2_MASK) >> OP2_SHIFT;
            final int cc = (inst & CC_MASK) >> CC_SHIFT;
            final int p = (inst & P_MASK) >> P_SHIFT;
            final int disp19 = (inst & DISP19_MASK) >> DISP19_SHIFT;

            Fmt00c fmt = new Fmt00c(a, cond, op2, cc, p, disp19);
            fmt.verify();
            return fmt;
        }

        public void write(SPARCAssembler masm, int pos) {
            verify();
            masm.codeBuffer.emitInt(getInstructionBits(), pos);
        }

        public void emit(SPARCAssembler masm) {
            if (label != null) {
                final int pos = label.isBound() ? label.position() : patchUnbound(masm, label);
                final int disp = pos - masm.codeBuffer.position();
                setDisp19(disp);
            }
            verify();
            masm.emitInt(getInstructionBits());
        }

        public void verify() {
            assert p < 2;
            assert cond < 0x10;
            assert op2 < 0x8;
        }
    }

    public static class Fmt2d {

        public Fmt2d(SPARCAssembler masm, int op, int a, int rcond, int op2, int d16hi, int predict, int rs1, int d16lo) {
            assert predict == 0 || predict == 1;
            assert rcond >= 0 && rcond < 0x8;
            assert op == 0;
            assert op2 >= 0 && op2 < 0x8;
            assert rs1 >= 0 && rs1 < 0x20;

            masm.emitInt(op << 30 | a << 29 | rcond << 25 | op2 << 22 | d16hi & 3 | predict << 18 | rs1 << 14 | (d16lo & 0x003fff));
        }
    }

    public static class Fmt2e {

        public Fmt2e(SPARCAssembler asm, int op, int c4lo, int cc2, int rs1, int d10lo, int regOrImmediate) {
            assert op == 0;
            assert (cc2 & 0xFFFFFFFE) == 0;
            assert c4lo >= 0 && rs1 < 0x10;
            assert rs1 >= 0 && rs1 < 0x20;
            assert (regOrImmediate & 0x1F) < 0x20;
            assert (regOrImmediate & 0xFFFFC000) == 0;
            assert (d10lo & 0xFFFFFC00) == 0;

            asm.emitInt(op << 30 | 1 << 28 | 3 << 22 | cc2 << 21 | (d10lo >> 8) << 19 | rs1 << 14 | (d10lo & 0xff) << 5 | regOrImmediate);
        }
    }

    public static class Fmt01 {

        private int disp30;

        public Fmt01(int disp30) {
            this.disp30 = disp30;
        }

        public void emit(SPARCAssembler masm) {
            assert ((disp30 & 0xc0000000) == 0);
            masm.emitInt(Ops.CallOp.getValue() << 30 | disp30);
        }
    }

    public static class Fmt3a {

        public Fmt3a(SPARCAssembler masm, int op, int rd, int op3, int rs1, int rs2) {
            assert op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rs2 >= 0 && rs2 < 0x20;
            assert rd >= 0 && rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | rs2);
        }
    }

    public static class Fmt3b {

        private boolean isImm;
        private int op;
        private int op3;
        private int rs1;
        private int rs2;
        private int simm13;
        private int rd;

        public Fmt3b(Ops op, Op3s op3, Register rs1, Register rs2, Register rd) {
            this.op = op.getValue();
            this.op3 = op3.getValue();
            this.rs1 = rs1.encoding();
            this.rs2 = rs2.encoding();
            this.rd = rd.encoding();
        }

        public Fmt3b(Ops op, Op3s op3, Register rs1, int simm13, Register rd) {
            isImm = true;
            this.op = op.getValue();
            this.op3 = op3.getValue();
            this.rs1 = rs1.encoding();
            this.simm13 = simm13;
            this.rd = rd.encoding();
        }

        public void emit(SPARCAssembler masm) {
            assert op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rd >= 0 && rd < 0x20;

            if (isImm == false) {
                assert rs2 >= 0 && rs2 < 0x20;
                masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | rs2);
            } else {
                assert isSimm13(simm13) : simm13;
                masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | ImmedTrue | (simm13 & 0x1fff));
            }
        }
    }

    public static class Fmt3c {

        public Fmt3c(SPARCAssembler masm, int op, int op3, int rs1, int rs2) {
            assert op == 2;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rs2 >= 0 && rs2 < 0x20;

            masm.emitInt(op << 30 | op3 << 19 | rs1 << 14 | rs2);
        }
    }

    public static class Fmt3d {

        public Fmt3d(SPARCAssembler masm, int op, int op3, int rs1, int simm13) {
            assert op == 2;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;

            masm.emitInt(op << 30 | op3 << 19 | rs1 << 14 | ImmedTrue | simm13);
        }
    }

    public static class Fmt3e {

        public Fmt3e(SPARCAssembler masm, int op, int op3, int rcond, int rs1, int rs2, int rd) {
            assert op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rcond >= 0 && rcond < 0x8;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rs2 >= 0 && rs2 < 0x20;
            assert rd >= 0 && rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | rcond << 10 | rs2);
        }
    }

    public static class Fmt3f {

        public Fmt3f(SPARCAssembler masm, int op, int op3, int rcond, int rs1, int simm10, int rd) {
            assert op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rd >= 0 && rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | ImmedTrue | rs1 << 14 | rcond << 10 | (simm10 & 0x000003ff));
        }
    }

    public static class Fmt3n {

        public Fmt3n(SPARCAssembler masm, int op, int op3, int opf, int rs2, int rd) {
            assert op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert opf >= 0 && opf < 0x200;
            assert rs2 >= 0 && rs2 < 0x20;
            assert rd >= 0 && rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | opf << 5 | rs2);
        }
    }

    public static class Fmt3p {

        private int op;
        private int op3;
        private int opf;
        private int rs1;
        private int rs2;
        private int rd;

        public Fmt3p(Ops op, Op3s op3, Opfs opf, Register rs1, Register rs2, Register rd) {
            this.op = op.getValue();
            this.op3 = op3.getValue();
            this.opf = opf.getValue();
            this.rs1 = rs1.encoding();
            this.rs2 = rs2.encoding();
            this.rd = rd.encoding();
        }

        public void emit(SPARCAssembler masm) {
            assert op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert opf >= 0 && opf < 0x200;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rs2 >= 0 && rs2 < 0x20;
            assert rd >= 0 && rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | opf << 5 | rs2);
        }
    }

    public static class Fmt3q {

        public Fmt3q(SPARCAssembler masm, int op, int op3, int rs1, int rd) {
            assert op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rd >= 0 && rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14);
        }
    }

    public static class Fmt3r {

        public Fmt3r(SPARCAssembler masm, int op, int fcn, int op3) {
            assert op == 23;
            assert op3 >= 0 && op3 < 0x40;
            assert fcn >= 0 && fcn < 0x40;

            masm.emitInt(op << 30 | fcn << 25 | op3 << 19);
        }
    }

    public static class Fmt4a {

        public Fmt4a(SPARCAssembler masm, int op, int op3, int cc, int rs1, int regOrImmediate, int rd) {
            assert op == 2;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rd >= 0 && rd < 0x10;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | ((cc << 11) & 0x000001800) | regOrImmediate);
        }
    }

    public static class Fmt10c {

        private static final int OP_SHIFT = 30;
        private static final int RD_SHIFT = 25;
        private static final int OP3_SHIFT = 19;
        private static final int CC2_SHIFT = 18;
        private static final int COND_SHIFT = 14;
        private static final int I_SHIFT = 13;
        private static final int CC_SHIFT = 11;
        private static final int RS2_SHIFT = 0;
        private static final int SIMM11_SHIFT = 0;

        // @formatter:off
        private static final int OP_MASK     = 0b11000000000000000000000000000000;
        private static final int RD_MASK     = 0b00111110000000000000000000000000;
        private static final int OP3_MASK    = 0b00000001111110000000000000000000;
        private static final int CC2_MASK    = 0b00000000000001000000000000000000;
        private static final int COND_MASK   = 0b00000000000000111100000000000000;
        private static final int I_MASK      = 0b00000000000000000010000000000000;
        private static final int CC_MASK     = 0b00000000000000000001100000000000;
        private static final int RS2_MASK    = 0b00000000000000000000000000011111;
        private static final int SIMM11_MASK = 0b00000000000000000000011111111111;
        // @formatter:on

        private int rd;
        private int op3;
        private int cond;
        private int i;
        private int cc;
        private int rs2;
        private int simm11;

        private Fmt10c(int rd, int op3, int cond, int i, int cc, int rs2, int simm11) {
            this.rd = rd;
            this.op3 = op3;
            this.cond = cond;
            this.i = i;
            this.cc = cc;
            this.rs2 = rs2;
            this.simm11 = simm11;
            verify();
        }

        public Fmt10c(Op3s op3, ConditionFlag cond, CC cc, Register rs2, Register rd) {
            this(rd.encoding(), op3.getValue(), cond.getValue(), 0, cc.getValue(), rs2.encoding(), 0);
        }

        public Fmt10c(Op3s op3, ConditionFlag cond, CC cc, int simm11, Register rd) {
            this(rd.encoding(), op3.getValue(), cond.getValue(), 1, cc.getValue(), 0, simm11);
        }

        private int getInstructionBits() {
            if (i == 0) {
                return Ops.ArithOp.getValue() << OP_SHIFT | rd << RD_SHIFT | op3 << OP3_SHIFT | ((cc << CC2_SHIFT) & CC2_MASK) | cond << COND_SHIFT | i << I_SHIFT | ((cc << CC_SHIFT) & CC_MASK) |
                                rs2 << RS2_SHIFT;
            } else {
                return Ops.ArithOp.getValue() << OP_SHIFT | rd << RD_SHIFT | op3 << OP3_SHIFT | ((cc << CC2_SHIFT) & CC2_MASK) | cond << COND_SHIFT | i << I_SHIFT | ((cc << CC_SHIFT) & CC_MASK) |
                                ((simm11 << SIMM11_SHIFT) & SIMM11_MASK);
            }
        }

        public static Fmt10c read(SPARCAssembler masm, int pos) {
            final int inst = masm.codeBuffer.getInt(pos);

            // Make sure it's the right instruction:
            final int op = (inst & OP_MASK) >> OP_SHIFT;
            assert op == Ops.ArithOp.getValue();

            // Get the instruction fields:
            final int rd = (inst & RD_MASK) >> RD_SHIFT;
            final int op3 = (inst & OP3_MASK) >> OP3_SHIFT;
            final int cond = (inst & COND_MASK) >> COND_SHIFT;
            final int i = (inst & I_MASK) >> I_SHIFT;
            final int cc = (inst & CC2_MASK) >> CC2_SHIFT | (inst & CC_MASK) >> CC_SHIFT;
            final int rs2 = (inst & RS2_MASK) >> RS2_SHIFT;
            final int simm11 = (inst & SIMM11_MASK) >> SIMM11_SHIFT;

            return new Fmt10c(rd, op3, cond, i, cc, rs2, simm11);
        }

        public void write(SPARCAssembler masm, int pos) {
            verify();
            masm.codeBuffer.emitInt(getInstructionBits(), pos);
        }

        public void emit(SPARCAssembler masm) {
            verify();
            masm.emitInt(getInstructionBits());
        }

        public void verify() {
            assert ((rd << RD_SHIFT) & RD_MASK) == (rd << RD_SHIFT);
            assert ((op3 << OP3_SHIFT) & OP3_MASK) == (op3 << OP3_SHIFT);
            assert ((cond << COND_SHIFT) & COND_MASK) == (cond << COND_SHIFT);
            assert ((i << I_SHIFT) & I_MASK) == (i << I_SHIFT);
            // assert cc >= 0 && cc < 0x8;
            assert ((rs2 << RS2_SHIFT) & RS2_MASK) == (rs2 << RS2_SHIFT);
            // assert isSimm11(simm11);
        }
    }

    public static class Fmt4d {

        public Fmt4d(SPARCAssembler masm, int op, int op3, int cond, int cc, int simm11, int rd) {
            assert op == 2;
            assert op3 >= 0 && op3 < 0x40;
            assert cc >= 0 && cc < 0x8;
            assert cond >= 0 && cond < 0x10;
            assert rd >= 0 && rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | ImmedTrue | ((cc << 15) & 0x00040000) | cond << 14 | ((cc << 11) & 0x3) | simm11 & 0x00004000);
        }
    }

    public static class Fmt5a {

        public Fmt5a(SPARCAssembler masm, int op, int op3, int op5, int rs1, int rs2, int rs3, int rd) {
            assert op == 2;
            assert op3 >= 0 && op3 < 0x40;
            assert op5 >= 0 && op5 < 0x10;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rs2 >= 0 && rs2 < 0x20;
            assert rs3 >= 0 && rs3 < 0x20;
            assert rd >= 0 && rd < 0x20;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | rs3 << 9 | op5 << 5 | rs2);
        }
    }

    public static final int ImmedTrue = 0x00002000;

    public enum Ops {
        BranchOp(0b00), CallOp(0b01), ArithOp(0b10), LdstOp(0b11);

        private final int value;

        private Ops(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Op2s {
        Bpr(3), Fb(6), Fbp(5), Br(2), Bp(1), Cb(7), Sethi(4);

        private final int value;

        private Op2s(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Op3s {
        Add(0x00, "add"),
        And(0x01, "and"),
        Or(0x02, "or"),
        Xor(0x03, "xor"),
        Sub(0x04, "sub"),
        Andn(0x05, "andn"),
        Orn(0x06, "orn"),
        Xnor(0x07, "xnor"),
        Addc(0x08, "addc"),
        Mulx(0x09, "mulx"),
        Umul(0x0A, "umul"),
        Smul(0x0B, "smul"),
        Subc(0x0C, "subc"),
        Udivx(0x0D, "udivx"),
        Udiv(0x0E, "udiv"),
        Sdiv(0x0F, "sdiv"),

        Addcc(0x10, "addcc"),
        Andcc(0x11, "andcc"),
        Orcc(0x12, "orcc"),
        Xorcc(0x13, "xorcc"),
        Subcc(0x14, "subcc"),
        Andncc(0x15, "andncc"),
        Orncc(0x16, "orncc"),
        Xnorcc(0x17, "xnorcc"),
        Addccc(0x18, "addccc"),
        Mulxcc(0x19, "mulxcc"),
        Umulcc(0x1A, "umulcc"),
        Smulcc(0x1B, "smulcc"),
        Subccc(0x1C0, "subccc"),
        Udivcc(0x1E, "udivcc"),
        Sdivcc(0x1F, "sdivcc"),

        Taddcc(0x20, "taddcc"),
        Tsubcc(0x21, "tsubcc"),
        Taddcctv(0x22, "taddcctv"),
        Tsubcctv(0x23, "tsubcctv"),
        Mulscc(0x24, "mulscc"),
        Sll(0x25, "sll"),
        Sllx(0x25, "sllx"),
        Srl(0x26, "srl"),
        Srlx(0x26, "srlx"),
        Sra(0x27, "srax"),
        Srax(0x27, "srax"),
        Rdreg(0x28, "rdreg"),
        Membar(0x28, "membar"),

        Flushw(0x2B, "flushw"),
        Movcc(0x2C, "movcc"),
        Sdivx(0x2D, "sdivx"),
        Popc(0x2E, "popc"),
        Movr(0x2F, "movr"),

        Sir(0x30, "sir"),
        Wrreg(0x30, "wrreg"),
        Saved(0x31, "saved"),

        Fpop1(0x34, "fpop1"),
        Fpop2(0x35, "fpop2"),
        Impdep1(0x36, "impdep1"),
        Impdep2(0x37, "impdep2"),
        Jmpl(0x38, "jmpl"),
        Rett(0x39, "rett"),
        Trap(0x3a, "trap"),
        Flush(0x3b, "flush"),
        Save(0x3c, "save"),
        Restore(0x3d, "restore"),
        Done(0x3e, "done"),
        Retry(0x3e, "retry"),

        Lduw(0x00, "lduw"),
        Ldub(0x01, "ldub"),
        Lduh(0x02, "lduh"),
        Ldd(0x03, "ldd"),
        Stw(0x04, "stw"),
        Stb(0x05, "stb"),
        Sth(0x06, "sth"),
        Std(0x07, "std"),
        Ldsw(0x08, "ldsw"),
        Ldsb(0x09, "ldsb"),
        Ldsh(0x0A, "ldsh"),
        Ldx(0b001011, "ldx"),
        Stx(0b001110, "stx"),

        Ldf(0x20, "ldf"),
        Ldfsr(0x21, "ldfsr"),
        Ldaf(0x22, "ldaf"),
        Lddf(0x23, "lddf"),
        Stf(0x24, "stf"),
        Stfsr(0x25, "stfsr"),
        Staf(0x26, "staf"),
        Stdf(0x27, "stdf");

        private final int value;
        private final String operator;

        private Op3s(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }
    }

    public enum Op5s {
        Fmadds(0x1), Fmaddd(0x2), Fmsubs(0x5), Fmsubd(0x6), Fnmsubs(0x9), Fnmsubd(0xA), Fnmadds(0xD), Fnmaddd(0xE);

        private final int value;

        private Op5s(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Opfs {
        Fmovs(0x01, "fmovs"), Fmovd(0x02, "fmovd"), Fmovq(0x03, "fmovq"), Fnegs(0x05, "fnegs"), Fnegd(0x06, "fnegd"), Fnegq(0x07, "fnegq"), Fabss(0x09, "fabss"), Fabsd(0x0A, "fabsd"), Fabsq(
                        0x0B,
                        "fabsq"),

        // start VIS1
        Edge8cc(0x0, "edge8cc"),
        Edge8n(0x1, "edge8n"),
        Edge8lcc(0x2, "edge8lcc"),
        Edge8ln(0x3, "edge8ln"),
        Edge16cc(0x4, "edge16cc"),
        Edge16n(0x5, "edge16n"),
        Edge16lcc(0x6, "edge16lcc"),
        Edge16ln(0x7, "edge16ln"),
        Edge32cc(0x8, "edge32cc"),
        Edge32n(0x9, "edge32n"),
        Edge32lcc(0xA, "edge32lcc"),
        Edge32ln(0xB, "edge32ln"),
        Array8(0x10, "array8"),
        Array16(0x12, "array16"),
        Array32(0x14, "array32"),
        AlignAddress(0x18, "alignaddress"),
        AlignAddressLittle(0x1A, "alignaddress_little"),
        Fpcmple16(0x20, "fpcmple16"),
        Fpcmpne16(0x22, "fpcmpne16"),
        Fpcmple32(0x24, "fpcmple32"),
        Fpcmpne32(0x26, "fpcmpne32"),
        Fpcmpgt16(0x28, "fpcmpgt16"),
        Fpcmpeq16(0x2A, "fpcmpeq16"),
        Fpcmpgt32(0x2C, "fpcmpgt32"),
        Fpcmpeq32(0x2E, "fpcmpeq32"),
        Fmul8x16(0x31, "fmul8x16"),
        Fmul8x16au(0x33, "fmul8x16au"),
        Fmul8x16al(0x35, "fmul8x16al"),
        Fmul8sux16(0x36, "fmul8sux16"),
        Fmul8ulx16(0x37, "fmul8ulx16"),
        Fmuld8sux16(0x38, "fmuld8sux16"),
        Fmuld8ulx16(0x39, "fmuld8ulx16"),
        Fpack32(0x3A, "fpack32"),
        Fpack16(0x3B, "fpack16"),
        Fpackfix(0x3D, "fpackfix"),
        Faligndatag(0x48, "faligndata"),
        Fpmerge(0x4B, "fpmerge"),
        Fpadd16(0x50, "fpadd16"),
        Fpadd16s(0x51, "fpadd16s"),
        Fpadd32(0x52, "fpadd32"),
        Fpadd32s(0x53, "fpadd32s"),
        Fpsub16(0x54, "fpadd16"),
        Fpsub16s(0x55, "fpadd16s"),
        Fpsub32(0x56, "fpadd32"),
        Fpsub32s(0x57, "fpadd32s"),
        Fzerod(0x60, "fzerod"),
        Fzeros(0x61, "fzeros"),
        Fnot2d(0x66, "fnot1d"),
        Fnot2s(0x67, "fnot1s"),
        Fnot1d(0x6A, "fnot1d"),
        Fnot1s(0x6B, "fnot1s"),
        Fsrc1d(0x74, "fsrc1d"),
        Fsrc1s(0x75, "fsrc1s"),
        Fsrc2d(0x78, "fsrc2d"),
        Fsrc2s(0x79, "fsrc2s"),
        Foned(0x7E, "foned"),
        Fones(0x7F, "fones"),
        // end VIS1

        // start VIS2
        Bmask(0x19, "bmask"),
        Bshuffle(0x4c, "bshuffle"),
        // end VIS2 only

        // start VIS3
        Addxc(0x11, "addxc"),
        Addxccc(0x13, "addxccc"),
        Cmask8(0x1B, "cmask8"),
        Cmask16(0x1D, "cmask16"),
        Cmask32(0x1F, "cmask32"),
        Fmean16(0x40, "fmean16"),
        Fnadds(0x51, "fnadds"),
        Fnaddd(0x52, "fnaddd"),
        Fnmuls(0x59, "fnmuls"),
        Fnmuld(0x5A, "fnmuld"),
        Fnsmuld(0x79, "fnsmuld"),
        Fnhadds(0x71, "fnhadds"),
        Fnhaddd(0x72, "fnhaddd"),
        // end VIS3

        // start CAMMELLIA
        CammelliaFl(0x13C, "cammelia_fl"),
        CammelliaFli(0x13D, "cammellia_fli"),
        // end CAMMELLIA

        // start CRYPTO
        Crc32c(0x147, "crc32c"),
        // end CRYPTO

        // start OSA 2011
        Fpadd64(0x44, "fpadd64"),
        Fpsub64(0x46, "fpsub64"),
        Fpadds16(0x58, "fpadds16"),
        Fpadds16s(0x59, "fpadds16"),
        Fpadds32(0x5A, "fpadds32"),
        Fpadds32s(0x5B, "fpadds32s"),
        Fpsubs16(0x5C, "fpsubs16"),
        Fpsubs16s(0x5D, "fpsubs16s"),
        Fpsubs32(0x5E, "fpsubs32"),
        Fpsubs32s(0x5F, "fpsubs32s"),
        Fpcmpne8(0x122, "fpcmpne8"),
        Fpcmpeq8(0x12C, "fpcmpeq8"),
        // end OSA 2011

        Fadds(0x41, "fadds"),
        Faddd(0x42, "faddd"),
        Faddq(0x43, "faddq"),
        Fsubs(0x45, "fsubs"),
        Fsubd(0x46, "fsubd"),
        Fsubq(0x47, "fsubq"),
        Fmuls(0x49, "fmuls"),
        Fmuld(0x4A, "fmuld"),
        Fdivs(0x4C, "fdivs"),
        Fdivd(0x4D, "fdivd"),
        Fdivq(0x4E, "fdivq"),

        Fsqrts(0x29, "fsqrts"),
        Fsqrtd(0x2A, "fsqrtd"),
        Fsqrtq(0x2B, "fsqrtq"),

        Fsmuld(0x69, "fsmuld"),
        Fmulq(0x6B, "fmulq"),
        Fdmuldq(0x6E, "fdmulq"),

        Fstoi(0xD1, "fstoi"),
        Fdtoi(0xD2, "fdtoi");

        private final int value;
        private final String operator;

        private Opfs(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }
    }

    public enum MembarMask {
        StoreStore(1 << 3, "storestore"), LoadStore(1 << 2, "loadstore"), StoreLoad(1 << 1, "storeload"), LoadLoad(1 << 0, "loadload"), Sync(1 << 6, "sync"), MemIssue(1 << 5, "memissue"), LookAside(
                        1 << 4,
                        "lookaside");

        private final int value;
        private final String operator;

        private MembarMask(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value | 0x2000;
        }

        public String getOperator() {
            return operator;
        }
    }

    public enum CC {
        Icc(0, "icc"), Xcc(2, "xcc"), Ptrcc(HotSpotGraalRuntime.wordKind() == Kind.Long ? Xcc.getValue() : Icc.getValue(), "ptrcc"), Fcc0(0, "fcc0"), Fcc1(1, "fcc1"), Fcc2(2, "fcc2"), Fcc3(3, "fcc3");

        private final int value;
        private final String operator;

        private CC(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }
    }

    public enum ConditionFlag {
        // for FBfcc & FBPfcc instruction

        F_Never(0, "f_never"),
        F_NotEqual(1, "f_notEqual"),
        F_NotZero(1, "f_notZero"),
        F_LessOrGreater(2, "f_lessOrGreater"),
        F_UnorderedOrLess(3, "f_unorderedOrLess"),
        F_Less(4, "f_less"),
        F_UnorderedOrGreater(5, "f_unorderedOrGreater"),
        F_Greater(6, "f_greater"),
        F_Unordered(7, "f_unordered"),
        F_Always(8, "f_always"),
        F_Equal(9, "f_equal"),
        F_Zero(9, "f_zero"),
        F_UnorderedOrEqual(10, "f_unorderedOrEqual"),
        F_GreaterOrEqual(11, "f_greaterOrEqual"),
        F_UnorderedGreaterOrEqual(12, "f_unorderedGreaterOrEqual"),
        F_LessOrEqual(13, "f_lessOrEqual"),
        F_UnorderedOrLessOrEqual(14, "f_unorderedOrLessOrEqual"),
        F_Ordered(15, "f_ordered"),

        // for integers
        Never(0, "never"),
        Equal(1, "equal"),
        Zero(1, "zero"),
        LessEqual(2, "lessEqual"),
        Less(3, "less"),
        LessEqualUnsigned(4, "lessEqualUnsigned"),
        LessUnsigned(5, "lessUnsigned"),
        CarrySet(5, "carrySet"),
        Negative(6, "negative"),
        OverflowSet(7, "overflowSet"),
        Always(8, "always"),
        NotEqual(9, "notEqual"),
        NotZero(9, "notZero"),
        Greater(10, "greater"),
        GreaterEqual(11, "greaterEqual"),
        GreaterUnsigned(12, "greaterUnsigned"),
        GreaterEqualUnsigned(13, "greaterEqualUnsigned"),
        CarryClear(13, "carryClear"),
        Positive(14, "positive"),
        OverflowClear(15, "overflowClear");

        private final int value;
        private final String operator;

        private ConditionFlag(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }
    }

    public enum RCondition {
        Rc_z(1, "rc_z"), Rc_lez(2, "rc_lez"), Rc_lz(3, "rc_lz"), Rc_nz(5, "rc_nz"), Rc_gz(6, "rc_gz"), Rc_gez(7, "rc_gez"), Rc_last(Rc_gez.getValue(), "rc_last");

        private final int value;
        private final String operator;

        private RCondition(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }
    }

    public static int getFloatEncoding(int reg) {
        assert reg < 32;
        return reg;
    }

    public static int getDoubleEncoding(int reg) {
        assert reg < 64 && ((reg & 1) == 0);
        // ignore v8 assertion for now
        return (reg & 0x1e) | ((reg & 0x20) >> 5);
    }

    public static int getQuadEncoding(int reg) {
        assert reg < 64 && ((reg & 3) == 0);
        // ignore v8 assertion for now
        return (reg & 0x1c) | ((reg & 0x20) >> 5);
    }

    public static final int sx1 = 0x00001000;

    public static final int simm(int x, int nbits) {
        // assert_signed_range(x, nbits);
        return x & ((1 << nbits) - 1);
    }

    private static final int max13 = ((1 << 12) - 1);
    private static final int min13 = -(1 << 12);

    public static boolean isSimm13(int src) {
        return min13 <= src && src <= max13;
    }

    public static final int hi22(int x) {
        return x >> 10;
    }

    public static final int lo10(int x) {
        return x & ((1 << 10) - 1);
    }

    public static class Add extends Fmt3b {

        public Add(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Add, src1, simm13, dst);
        }

        public Add(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Add, src1, src2, dst);
        }
    }

    public static class Addc extends Fmt3b {

        public Addc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Addc, src1, simm13, dst);
        }

        public Addc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Addc, src1, src2, dst);
        }
    }

    public static class Addcc extends Fmt3b {

        public Addcc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Addcc, src1, simm13, dst);
        }

        public Addcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Addcc, src1, src2, dst);
        }
    }

    public static class Addccc extends Fmt3b {

        public Addccc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Addccc, src1, simm13, dst);
        }

        public Addccc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Addccc, src1, src2, dst);
        }
    }

    public static class Addxc extends Fmt3p {

        public Addxc(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Addxc, src1, src2, dst);
        }
    }

    public static class Addxccc extends Fmt3p {

        public Addxccc(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Addxccc, src1, src2, dst);
        }
    }

    public static class Alignaddr extends Fmt3p {

        public Alignaddr(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.AlignAddress, src1, src2, dst);
        }
    }

    public static class Alignaddrl extends Fmt3p {

        public Alignaddrl(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.AlignAddressLittle, src1, src2, dst);
        }
    }

    public static class And extends Fmt3b {

        public And(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.And, src1, simm13, dst);
        }

        public And(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.And, src1, src2, dst);
        }
    }

    public static class Andcc extends Fmt3b {

        public Andcc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Andcc, src1, simm13, dst);
        }

        public Andcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Andcc, src1, src2, dst);
        }
    }

    public static class Andn extends Fmt3b {

        public Andn(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Andn, src1, simm13, dst);
        }

        public Andn(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Andn, src1, src2, dst);
        }
    }

    public static class Andncc extends Fmt3b {

        public Andncc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Andncc, src1, simm13, dst);
        }

        public Andncc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Andncc, src1, src2, dst);
        }
    }

    public static class Array8 extends Fmt3p {

        public Array8(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Array8, src1, src2, dst);
        }
    }

    public static class Array16 extends Fmt3p {

        public Array16(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Array16, src1, src2, dst);
        }
    }

    public static class Array32 extends Fmt3p {

        public Array32(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Array32, src1, src2, dst);
        }
    }

    public static class Bmask extends Fmt3p {

        public Bmask(Register src1, Register src2, Register dst) {
            /* VIS2 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Bmask, src1, src2, dst);
        }
    }

    public static class Bpa extends Fmt00c {

        public Bpa(int simm19) {
            super(0, ConditionFlag.Always, Op2s.Bp, CC.Icc, 1, simm19);
        }

        public Bpa(Label label) {
            super(0, ConditionFlag.Always, Op2s.Bp, CC.Icc, 1, label);
        }
    }

    public static class Bpcc extends Fmt00c {

        public Bpcc(CC cc, int simm19) {
            super(0, ConditionFlag.CarryClear, Op2s.Bp, cc, 1, simm19);
        }

        public Bpcc(CC cc, Label label) {
            super(0, ConditionFlag.CarryClear, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpcs extends Fmt00c {

        public Bpcs(CC cc, int simm19) {
            super(0, ConditionFlag.CarrySet, Op2s.Bp, cc, 1, simm19);
        }

        public Bpcs(CC cc, Label label) {
            super(0, ConditionFlag.CarrySet, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpe extends Fmt00c {

        public Bpe(CC cc, int simm19) {
            super(0, ConditionFlag.Equal, Op2s.Bp, cc, 1, simm19);
        }

        public Bpe(CC cc, Label label) {
            super(0, ConditionFlag.Equal, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpg extends Fmt00c {

        public Bpg(CC cc, int simm19) {
            super(0, ConditionFlag.Greater, Op2s.Bp, cc, 1, simm19);
        }

        public Bpg(CC cc, Label label) {
            super(0, ConditionFlag.Greater, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpge extends Fmt00c {

        public Bpge(CC cc, int simm19) {
            super(0, ConditionFlag.GreaterEqual, Op2s.Bp, cc, 1, simm19);
        }

        public Bpge(CC cc, Label label) {
            super(0, ConditionFlag.GreaterEqual, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpgu extends Fmt00c {

        public Bpgu(CC cc, int simm19) {
            super(0, ConditionFlag.GreaterUnsigned, Op2s.Bp, cc, 1, simm19);
        }

        public Bpgu(CC cc, Label label) {
            super(0, ConditionFlag.GreaterUnsigned, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpl extends Fmt00c {

        public Bpl(CC cc, int simm19) {
            super(0, ConditionFlag.Less, Op2s.Bp, cc, 1, simm19);
        }

        public Bpl(CC cc, Label label) {
            super(0, ConditionFlag.Less, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bple extends Fmt00c {

        public Bple(CC cc, int simm19) {
            super(0, ConditionFlag.LessEqual, Op2s.Bp, cc, 1, simm19);
        }

        public Bple(CC cc, Label label) {
            super(0, ConditionFlag.LessEqual, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpleu extends Fmt00c {

        public Bpleu(CC cc, int simm19) {
            super(0, ConditionFlag.LessEqualUnsigned, Op2s.Bp, cc, 1, simm19);
        }

        public Bpleu(CC cc, Label label) {
            super(0, ConditionFlag.LessEqualUnsigned, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpn extends Fmt00c {

        public Bpn(CC cc, int simm19) {
            super(0, ConditionFlag.Never, Op2s.Bp, cc, 1, simm19);
        }

        public Bpn(CC cc, Label label) {
            super(0, ConditionFlag.Never, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpne extends Fmt00c {

        public Bpne(CC cc, int simm19) {
            super(0, ConditionFlag.NotZero, Op2s.Bp, cc, 1, simm19);
        }

        public Bpne(CC cc, Label label) {
            super(0, ConditionFlag.NotZero, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpneg extends Fmt00c {

        public Bpneg(CC cc, int simm19) {
            super(0, ConditionFlag.Negative, Op2s.Bp, cc, 1, simm19);
        }

        public Bpneg(CC cc, Label label) {
            super(0, ConditionFlag.Negative, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bppos extends Fmt00c {

        public Bppos(CC cc, int simm19) {
            super(0, ConditionFlag.Positive, Op2s.Bp, cc, 1, simm19);
        }

        public Bppos(CC cc, Label label) {
            super(0, ConditionFlag.Positive, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpvc extends Fmt00c {

        public Bpvc(CC cc, int simm19) {
            super(0, ConditionFlag.OverflowClear, Op2s.Bp, cc, 1, simm19);
        }

        public Bpvc(CC cc, Label label) {
            super(0, ConditionFlag.OverflowClear, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bpvs extends Fmt00c {

        public Bpvs(CC cc, int simm19) {
            super(0, ConditionFlag.OverflowSet, Op2s.Bp, cc, 1, simm19);
        }

        public Bpvs(CC cc, Label label) {
            super(0, ConditionFlag.OverflowSet, Op2s.Bp, cc, 1, label);
        }
    }

    public static class Bshuffle extends Fmt3p {

        public Bshuffle(Register src1, Register src2, Register dst) {
            /* VIS2 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Bshuffle, src1, src2, dst);
        }
    }

    public static class CammelliaFl extends Fmt3p {

        public CammelliaFl(Register src1, Register src2, Register dst) {
            /* CAMELLIA only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.CammelliaFl, src1, src2, dst);
        }
    }

    public static class CammelliaFli extends Fmt3p {

        public CammelliaFli(Register src1, Register src2, Register dst) {
            /* CAMELLIA only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.CammelliaFli, src1, src2, dst);
        }
    }

    private static int patchUnbound(SPARCAssembler masm, Label label) {
        label.addPatchAt(masm.codeBuffer.position());
        return 0;
    }

    public static class Cmask8 extends Fmt3n {

        public Cmask8(SPARCAssembler asm, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Cmask8.getValue(), src2.encoding(), 0);
        }
    }

    public static class Cmask16 extends Fmt3n {

        public Cmask16(SPARCAssembler asm, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Cmask16.getValue(), src2.encoding(), 0);
        }
    }

    public static class Cmask32 extends Fmt3n {

        public Cmask32(SPARCAssembler asm, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Cmask32.getValue(), src2.encoding(), 0);
        }
    }

    public static class Crc32c extends Fmt3p {

        public Crc32c(Register src1, Register src2, Register dst) {
            /* CRYPTO only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Crc32c, src1, src2, dst);
        }
    }

    public static class Cwbcc extends Fmt2e {

        public Cwbcc(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarryClear.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbcc(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Equal.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbcs extends Fmt2e {

        public Cwbcs(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarrySet.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbcs(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarrySet.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbe extends Fmt2e {

        public Cwbe(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarryClear.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbe(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Equal.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbg extends Fmt2e {

        public Cwbg(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Greater.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbg(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Greater.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbge extends Fmt2e {

        public Cwbge(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterEqual.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbge(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterEqual.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbgu extends Fmt2e {

        public Cwbgu(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterUnsigned.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbgu(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterUnsigned.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbl extends Fmt2e {

        public Cwbl(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Less.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbl(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Less.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwble extends Fmt2e {

        public Cwble(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqual.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwble(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqual.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbleu extends Fmt2e {

        public Cwbleu(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqualUnsigned.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbleu(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqualUnsigned.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbne extends Fmt2e {

        public Cwbne(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.NotEqual.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbne(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.NotEqual.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbneg extends Fmt2e {

        public Cwbneg(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Negative.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbneg(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Negative.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbpos extends Fmt2e {

        public Cwbpos(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Positive.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbpos(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Positive.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbvc extends Fmt2e {

        public Cwbvc(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowClear.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbvc(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowClear.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cwbvs extends Fmt2e {

        public Cwbvs(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowSet.getValue(), 0, src1.encoding(), simm10, src2.encoding());
        }

        public Cwbvs(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowSet.getValue(), 0, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbcc extends Fmt2e {

        public Cxbcc(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarryClear.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbcc(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Equal.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbcs extends Fmt2e {

        public Cxbcs(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarrySet.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbcs(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarrySet.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbe extends Fmt2e {

        public Cxbe(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.CarryClear.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbe(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Equal.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbg extends Fmt2e {

        public Cxbg(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Greater.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbg(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Greater.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbge extends Fmt2e {

        public Cxbge(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterEqual.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbge(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterEqual.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbgu extends Fmt2e {

        public Cxbgu(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterUnsigned.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbgu(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.GreaterUnsigned.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbl extends Fmt2e {

        public Cxbl(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Less.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbl(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Less.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxble extends Fmt2e {

        public Cxble(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqual.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxble(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqual.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbleu extends Fmt2e {

        public Cxbleu(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqualUnsigned.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbleu(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.LessEqualUnsigned.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbne extends Fmt2e {

        public Cxbne(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.NotEqual.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbne(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.NotEqual.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbneg extends Fmt2e {

        public Cxbneg(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Negative.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbneg(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Negative.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbpos extends Fmt2e {

        public Cxbpos(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Positive.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbpos(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.Positive.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbvc extends Fmt2e {

        public Cxbvc(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowClear.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbvc(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowClear.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Cxbvs extends Fmt2e {

        public Cxbvs(SPARCAssembler asm, Register src1, Register src2, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowSet.getValue(), 1, src1.encoding(), simm10, src2.encoding());
        }

        public Cxbvs(SPARCAssembler asm, Register src1, int immed5, int simm10) {
            super(asm, Ops.BranchOp.getValue(), ConditionFlag.OverflowSet.getValue(), 1, src1.encoding(), simm10, immed5 | ImmedTrue);
        }
    }

    public static class Edge8cc extends Fmt3p {

        public Edge8cc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge8cc, src1, src2, dst);
        }
    }

    public static class Edge8n extends Fmt3p {

        public Edge8n(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge8n, src1, src2, dst);
        }
    }

    public static class Edge8lcc extends Fmt3p {

        public Edge8lcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge8lcc, src1, src2, dst);
        }
    }

    public static class Edge8ln extends Fmt3p {

        public Edge8ln(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge8ln, src1, src2, dst);
        }
    }

    public static class Edge16cc extends Fmt3p {

        public Edge16cc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge16cc, src1, src2, dst);
        }
    }

    public static class Edge16n extends Fmt3p {

        public Edge16n(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge16n, src1, src2, dst);
        }
    }

    public static class Edge16lcc extends Fmt3p {

        public Edge16lcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge16lcc, src1, src2, dst);
        }
    }

    public static class Edge16ln extends Fmt3p {

        public Edge16ln(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge16ln, src1, src2, dst);
        }
    }

    public static class Edge32cc extends Fmt3p {

        public Edge32cc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge32cc, src1, src2, dst);
        }
    }

    public static class Edge32n extends Fmt3p {

        public Edge32n(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge32n, src1, src2, dst);
        }
    }

    public static class Edge32lcc extends Fmt3p {

        public Edge32lcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge32lcc, src1, src2, dst);
        }
    }

    public static class Edge32ln extends Fmt3p {

        public Edge32ln(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Edge32ln, src1, src2, dst);
        }
    }

    public static class Fadds extends Fmt3p {

        public Fadds(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fadds, src1, src2, dst);
        }
    }

    public static class Faddd extends Fmt3p {

        public Faddd(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Faddd, src1, src2, dst);
        }
    }

    public static class Faddq extends Fmt3p {

        public Faddq(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Faddq, src1, src2, dst);
        }
    }

    public static class Faligndata extends Fmt3p {

        public Faligndata(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Faligndatag, src1, src2, dst);
        }
    }

    public static class Fdivs extends Fmt3p {

        public Fdivs(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fdivs, src1, src2, dst);
        }
    }

    public static class Fdivd extends Fmt3p {

        public Fdivd(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fdivd, src1, src2, dst);
        }
    }

    public static class Fmadds extends Fmt5a {

        public Fmadds(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmadds.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fmaddd extends Fmt5a {

        public Fmaddd(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmaddd.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fmean16 extends Fmt3p {

        public Fmean16(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fmean16, src1, src2, dst);
        }
    }

    public static class Fmsubs extends Fmt5a {

        public Fmsubs(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmsubs.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fmsubd extends Fmt5a {

        public Fmsubd(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmsubd.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fmuls extends Fmt3p {

        public Fmuls(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fmuls, src1, src2, dst);
        }
    }

    public static class Fmuld extends Fmt3p {

        public Fmuld(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fmuld, src1, src2, dst);
        }
    }

    public static class Fmul8x16 extends Fmt3p {

        public Fmul8x16(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fmul8x16, src1, src2, dst);
        }
    }

    public static class Fmul8x16au extends Fmt3p {

        public Fmul8x16au(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fmul8x16, src1, src2, dst);
        }
    }

    public static class Fmul8x16al extends Fmt3p {

        public Fmul8x16al(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fmul8x16al, src1, src2, dst);
        }
    }

    public static class Fmul8sux16 extends Fmt3p {

        public Fmul8sux16(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fmul8sux16, src1, src2, dst);
        }
    }

    public static class Fmul8ulx16 extends Fmt3p {

        public Fmul8ulx16(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fmul8ulx16, src1, src2, dst);
        }
    }

    public static class Fmuld8sux16 extends Fmt3p {

        public Fmuld8sux16(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fmuld8sux16, src1, src2, dst);
        }
    }

    public static class Fmuld8ulx16 extends Fmt3p {

        public Fmuld8ulx16(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fmuld8ulx16, src1, src2, dst);
        }
    }

    public static class Fnadds extends Fmt3p {

        public Fnadds(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnadds, src1, src2, dst);
        }
    }

    public static class Fnaddd extends Fmt3p {

        public Fnaddd(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnaddd, src1, src2, dst);
        }
    }

    public static class Fnegs extends Fmt3n {

        public Fnegs(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fnegs.getValue(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fnegd extends Fmt3n {

        public Fnegd(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fnegd.getValue(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fnhadds extends Fmt3p {

        public Fnhadds(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnhadds, src1, src2, dst);
        }
    }

    public static class Fnhaddd extends Fmt3p {

        public Fnhaddd(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnhaddd, src1, src2, dst);
        }
    }

    public static class Fnmadds extends Fmt5a {

        public Fnmadds(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmadds.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fnmaddd extends Fmt5a {

        public Fnmaddd(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmaddd.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fnmsubs extends Fmt5a {

        public Fnmsubs(SPARCAssembler masm, Register src1, Register src2, Register src3, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmsubs.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fnmsubd extends Fmt5a {

        public Fnmsubd(SPARCAssembler masm, Register src1, Register src2, Register src3, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmsubd.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fnmuls extends Fmt3p {

        public Fnmuls(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnmuls, src1, src2, dst);
        }
    }

    public static class Fnmuld extends Fmt3p {

        public Fnmuld(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnmuld, src1, src2, dst);
        }
    }

    public static class Fnsmuld extends Fmt3p {

        public Fnsmuld(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnsmuld, src1, src2, dst);
        }
    }

    public static class Fstoi extends Fmt3n {

        public Fstoi(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fstoi.getValue(), src2.encoding(), dst.encoding());
        }
    }

    public static class Fdtoi extends Fmt3n {

        public Fdtoi(SPARCAssembler masm, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fdtoi.getValue(), src2.encoding(), dst.encoding());
        }
    }

    public static class Flushw extends Fmt3r {

        public Flushw(SPARCAssembler masm) {
            super(masm, Ops.ArithOp.getValue(), 0, Op3s.Flushw.getValue());
        }
    }

    public static class Fpack16 extends Fmt3p {

        public Fpack16(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpack16, src1, src2, dst);
        }
    }

    public static class Fpack32 extends Fmt3p {

        public Fpack32(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpack32, src1, src2, dst);
        }
    }

    public static class Fpackfix extends Fmt3p {

        public Fpackfix(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpackfix, src1, src2, dst);
        }
    }

    public static class Fpmaddx extends Fmt5a {

        public Fpmaddx(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), 0, src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fpmaddxhi extends Fmt5a {

        public Fpmaddxhi(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), 4, src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    public static class Fpmerge extends Fmt3p {

        public Fpmerge(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpmerge, src1, src2, dst);
        }
    }

    public static class Fpsub16 extends Fmt3p {

        public Fpsub16(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpsub16, src1, src2, dst);
        }
    }

    public static class Fpsub16s extends Fmt3p {

        public Fpsub16s(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpsub16s, src1, src2, dst);
        }
    }

    public static class Fpsub32 extends Fmt3p {

        public Fpsub32(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpsub32, src1, src2, dst);
        }
    }

    public static class Fpsub32s extends Fmt3p {

        public Fpsub32s(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpsub32s, src1, src2, dst);
        }
    }

    public static class Fpsub64 extends Fmt3p {

        public Fpsub64(Register src1, Register src2, Register dst) {
            /* OSA 2011 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpsub64, src1, src2, dst);
        }
    }

    public static class Fpsubs16 extends Fmt3p {

        public Fpsubs16(Register src1, Register src2, Register dst) {
            /* OSA 2011 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpsubs16, src1, src2, dst);
        }
    }

    public static class Fpsubs16s extends Fmt3p {

        public Fpsubs16s(Register src1, Register src2, Register dst) {
            /* OSA 2011 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpsubs16s, src1, src2, dst);
        }
    }

    public static class Fpsubs32 extends Fmt3p {

        public Fpsubs32(Register src1, Register src2, Register dst) {
            /* OSA 2011 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpsubs32, src1, src2, dst);
        }
    }

    public static class Fpsubs32s extends Fmt3p {

        public Fpsubs32s(Register src1, Register src2, Register dst) {
            /* OSA 2011 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpsubs32s, src1, src2, dst);
        }
    }

    public static class Fsqrtd extends Fmt3p {

        public Fsqrtd(Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fsqrtd, SPARC.r0, src2, dst);
        }
    }

    public static class Fsrc1d extends Fmt3p {

        public Fsrc1d(Register src1, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fsrc1d, src1, SPARC.r0, dst);
        }
    }

    public static class Fsrc1s extends Fmt3p {

        public Fsrc1s(Register src1, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fsrc1s, src1, SPARC.r0, dst);
        }
    }

    public static class Fsrc2d extends Fmt3p {

        public Fsrc2d(Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fsrc2d, SPARC.r0, src2, dst);
        }
    }

    public static class Fsrc2s extends Fmt3p {

        public Fsrc2s(Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fsrc2s, SPARC.r0, src2, dst);
        }
    }

    public static class Fsubs extends Fmt3p {

        public Fsubs(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fsubs, src1, src2, dst);
        }
    }

    public static class Fsubd extends Fmt3p {

        public Fsubd(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fsubd, src1, src2, dst);
        }
    }

    public static class Fsubq extends Fmt3p {

        public Fsubq(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fsubq, src1, src2, dst);
        }
    }

    public static class Fzeros extends Fmt3n {

        public Fzeros(SPARCAssembler asm, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fzeros.getValue(), 0, dst.encoding());
        }
    }

    public static class Fzerod extends Fmt3n {

        public Fzerod(SPARCAssembler asm, Register dst) {
            /* VIS1 only */
            super(asm, Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fzerod.getValue(), 0, dst.encoding());
        }
    }

    public static class Jmpl extends Fmt3b {

        public Jmpl(Register src, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Jmpl, src, simm13, dst);
        }
    }

    public static class Lddf extends Fmt3b {

        // TODO remove address
        public Lddf(SPARCAddress src, Register dst) {
            super(Ops.LdstOp, Op3s.Lddf, src.getBase(), src.getDisplacement(), dst);
        }
    }

    public static class Ldf extends Fmt3b {

        public Ldf(SPARCAddress src, Register dst) {
            super(Ops.LdstOp, Op3s.Ldf, src.getBase(), src.getDisplacement(), dst);
        }
    }

    public static class Ldsb extends Fmt3b {

        public Ldsb(SPARCAddress src, Register dst) {
            super(Ops.LdstOp, Op3s.Ldsb, src.getBase(), src.getDisplacement(), dst);
        }
    }

    public static class Ldsh extends Fmt3b {

        public Ldsh(SPARCAddress src, Register dst) {
            super(Ops.LdstOp, Op3s.Ldsh, src.getBase(), src.getDisplacement(), dst);
        }
    }

    public static class Ldsw extends Fmt3b {

        public Ldsw(SPARCAddress src, Register dst) {
            super(Ops.LdstOp, Op3s.Ldsw, src.getBase(), src.getDisplacement(), dst);
        }
    }

    public static class Lduw extends Fmt3b {

        public Lduw(SPARCAddress src, Register dst) {
            super(Ops.LdstOp, Op3s.Lduw, src.getBase(), src.getDisplacement(), dst);
        }
    }

    public static class Ldx extends Fmt3b {

        public Ldx(SPARCAddress src, Register dst) {
            super(Ops.LdstOp, Op3s.Ldx, src.getBase(), src.getDisplacement(), dst);
        }
    }

    public static class Membar extends Fmt3b {

        public Membar(int barriers) {
            super(Ops.ArithOp, Op3s.Membar, r15, ImmedTrue | barriers, r0);
        }
    }

    public static class Movcc extends Fmt10c {

        public Movcc(ConditionFlag cond, CC cca, Register src2, Register dst) {
            super(Op3s.Movcc, cond, cca, src2, dst);
        }

        public Movcc(ConditionFlag cond, CC cca, int simm11, Register dst) {
            super(Op3s.Movcc, cond, cca, simm11, dst);
        }
    }

    public static class Movr extends Fmt3f {

        public Movr(SPARCAssembler masm, RCondition rc, Register src1, Register src2, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Movr.getValue(), rc.getValue(), src1.encoding(), src2.encoding(), dst.encoding());
        }

        public Movr(SPARCAssembler masm, RCondition rc, Register src1, int simm10, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Movr.getValue(), rc.getValue(), src1.encoding(), simm10, dst.encoding());
        }
    }

    @Deprecated
    public static class Mulscc extends Fmt3b {

        @Deprecated
        public Mulscc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Mulscc, src1, simm13, dst);
        }

        @Deprecated
        public Mulscc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Mulscc, src1, src2, dst);
        }
    }

    public static class Mulx extends Fmt3b {

        public Mulx(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Mulx, src1, simm13, dst);
        }

        public Mulx(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Mulx, src1, src2, dst);
        }
    }

    public static class Or extends Fmt3b {

        public Or(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Or, src1, simm13, dst);
        }

        public Or(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Or, src1, src2, dst);
        }
    }

    public static class Orcc extends Fmt3b {

        public Orcc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Orcc, src1, simm13, dst);
        }

        public Orcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Orcc, src1, src2, dst);
        }
    }

    public static class Orn extends Fmt3b {

        public Orn(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Orn, src1, simm13, dst);
        }

        public Orn(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Orn, src1, src2, dst);
        }
    }

    public static class Orncc extends Fmt3b {

        public Orncc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Orncc, src1, simm13, dst);
        }

        public Orncc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Orncc, src1, src2, dst);
        }
    }

    public static class Popc extends Fmt3b {

        public Popc(int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Popc, r0, simm13, dst);
        }

        public Popc(Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Popc, r0, src2, dst);
        }
    }

    // A.44 Read State Register

    @Deprecated
    public static class Rdy extends Fmt3q {

        public Rdy(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 0, dst.encoding());
        }
    }

    public static class Rdccr extends Fmt3q {

        public Rdccr(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 2, dst.encoding());
        }
    }

    public static class Rdasi extends Fmt3q {

        public Rdasi(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 3, dst.encoding());
        }
    }

    public static class Rdtick extends Fmt3q {

        public Rdtick(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 4, dst.encoding());
        }
    }

    public static class Rdpc extends Fmt3q {

        public Rdpc(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 5, dst.encoding());
        }
    }

    public static class Rdfprs extends Fmt3q {

        public Rdfprs(SPARCAssembler masm, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rdreg.getValue(), 6, dst.encoding());
        }
    }

    public static class Restore extends Fmt3b {

        public Restore(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Restore, src1, src2, dst);
        }
    }

    public static class Restored extends Fmt3r {

        public Restored(SPARCAssembler asm) {
            super(asm, Ops.ArithOp.getValue(), 1, Op3s.Saved.getValue());
        }
    }

    public static class Return extends Fmt3d {

        public Return(SPARCAssembler masm, Register src1, int simm13) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rett.getValue(), src1.encoding(), simm13);
        }

        public Return(SPARCAssembler masm, Register src1, Register src2) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Rett.getValue(), src1.encoding(), src2.encoding());
        }
    }

    public static class Save extends Fmt3b {

        public Save(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Save, src1, src2, dst);
        }

        public Save(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Save, src1, simm13, dst);
        }
    }

    public static class Saved extends Fmt3r {

        public Saved(SPARCAssembler asm) {
            super(asm, Ops.ArithOp.getValue(), 0, Op3s.Saved.getValue());
        }
    }

    @Deprecated
    public static class Sdiv extends Fmt3b {

        @Deprecated
        public Sdiv(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Sdiv, src1, simm13, dst);
        }

        @Deprecated
        public Sdiv(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Sdiv, src1, src2, dst);
        }
    }

    @Deprecated
    public static class Sdivcc extends Fmt3b {

        @Deprecated
        public Sdivcc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Sdivcc, src1, simm13, dst);
        }

        @Deprecated
        public Sdivcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Sdivcc, src1, src2, dst);
        }
    }

    public static class Sdivx extends Fmt3b {

        public Sdivx(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Sdivx, src1, simm13, dst);
        }

        public Sdivx(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Sdivx, src1, src2, dst);
        }
    }

    public static class Sethi extends Fmt00a {

        public Sethi(int simm22, Register dst) {
            super(Op2s.Sethi, simm22, dst);
        }
    }

    public static class Sir extends Fmt3b {

        public Sir(int simm13) {
            super(Ops.ArithOp, Op3s.Sir, r0, simm13, r15);
        }
    }

    public static class Sll extends Fmt3b {

        public Sll(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Sll, src1, simm13, dst);
        }

        public Sll(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Sll, src1, src2, dst);
        }
    }

    public static class Sllx extends Fmt3b {

        public Sllx(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Sllx, src1, simm13, dst);
        }

        public Sllx(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Sllx, src1, src2, dst);
        }
    }

    public static class Sra extends Fmt3b {

        public Sra(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Sra, src1, simm13, dst);
        }

        public Sra(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Sra, src1, src2, dst);
        }
    }

    public static class Srax extends Fmt3b {

        public Srax(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Srax, src1, simm13, dst);
        }

        public Srax(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Srax, src1, src2, dst);
        }
    }

    public static class Srl extends Fmt3b {

        public Srl(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Srl, src1, simm13, dst);
        }

        public Srl(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Srl, src1, src2, dst);
        }
    }

    public static class Srlx extends Fmt3b {

        public Srlx(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Srlx, src1, simm13, dst);
        }

        public Srlx(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Srlx, src1, src2, dst);
        }
    }

    public static class Stb extends Fmt3b {

        public Stb(Register dst, SPARCAddress addr) {
            super(Ops.LdstOp, Op3s.Stb, addr.getBase(), addr.getDisplacement(), dst);
        }
    }

    public static class Sth extends Fmt3b {

        public Sth(Register dst, SPARCAddress addr) {
            super(Ops.LdstOp, Op3s.Sth, addr.getBase(), addr.getDisplacement(), dst);
        }
    }

    public static class Stw extends Fmt3b {

        public Stw(Register dst, SPARCAddress addr) {
            super(Ops.LdstOp, Op3s.Stw, addr.getBase(), addr.getDisplacement(), dst);
        }
    }

    public static class Stx extends Fmt3b {

        public Stx(Register dst, SPARCAddress addr) {
            super(Ops.LdstOp, Op3s.Stx, addr.getBase(), addr.getDisplacement(), dst);
        }
    }

    public static class Sub extends Fmt3b {

        public Sub(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Sub, src1, simm13, dst);
        }

        public Sub(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Sub, src1, src2, dst);
        }
    }

    public static class Subc extends Fmt3b {

        public Subc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Subc, src1, simm13, dst);
        }

        public Subc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Subc, src1, src2, dst);
        }
    }

    public static class Subcc extends Fmt3b {

        public Subcc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Subcc, src1, simm13, dst);
        }

        public Subcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Subcc, src1, src2, dst);
        }
    }

    public static class Subccc extends Fmt3b {

        public Subccc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Subccc, src1, simm13, dst);
        }

        public Subccc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Subccc, src1, src2, dst);
        }
    }

    public static class Ta extends Fmt4a {

        public Ta(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.Always.getValue());
        }

        public Ta(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.Always.getValue());
        }
    }

    public static class Taddcc extends Fmt3b {

        public Taddcc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Taddcc, src1, simm13, dst);
        }

        public Taddcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Taddcc, src1, src2, dst);
        }
    }

    public static class Tcc extends Fmt4a {

        public Tcc(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.CarryClear.getValue());
        }

        public Tcc(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.CarryClear.getValue());
        }
    }

    public static class Tcs extends Fmt4a {

        public Tcs(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.CarrySet.getValue());
        }

        public Tcs(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.CarrySet.getValue());
        }
    }

    public static class Te extends Fmt4a {

        public Te(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.Equal.getValue());
        }

        public Te(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.Equal.getValue());
        }
    }

    public static class Tg extends Fmt4a {

        public Tg(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.Greater.getValue());
        }

        public Tg(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.Greater.getValue());
        }
    }

    public static class Tge extends Fmt4a {

        public Tge(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.GreaterEqual.getValue());
        }

        public Tge(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.GreaterEqual.getValue());
        }
    }

    public static class Tle extends Fmt4a {

        public Tle(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.LessEqual.getValue());
        }

        public Tle(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.LessEqual.getValue());
        }
    }

    public static class Tleu extends Fmt4a {

        public Tleu(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.LessEqualUnsigned.getValue());
        }

        public Tleu(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.LessEqualUnsigned.getValue());
        }
    }

    public static class Tn extends Fmt4a {

        public Tn(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.Never.getValue());
        }

        public Tn(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.Never.getValue());
        }
    }

    public static class Tne extends Fmt4a {

        public Tne(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.NotEqual.getValue());
        }

        public Tne(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.NotEqual.getValue());
        }
    }

    public static class Tneg extends Fmt4a {

        public Tneg(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.Negative.getValue());
        }

        public Tneg(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.Negative.getValue());
        }
    }

    public static class Tpos extends Fmt4a {

        public Tpos(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.Positive.getValue());
        }

        public Tpos(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.Positive.getValue());
        }
    }

    public static class Tsubcc extends Fmt3b {

        public Tsubcc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Tsubcc, src1, simm13, dst);
        }

        public Tsubcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Tsubcc, src1, src2, dst);
        }
    }

    public static class Tvc extends Fmt4a {

        public Tvc(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.OverflowClear.getValue());
        }

        public Tvc(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.OverflowClear.getValue());
        }
    }

    public static class Tvs extends Fmt4a {

        public Tvs(SPARCAssembler asm, CC cc, Register src1, int trap) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), trap, ConditionFlag.OverflowSet.getValue());
        }

        public Tvs(SPARCAssembler asm, CC cc, Register src1, Register src2) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Trap.getValue(), cc.getValue(), src1.encoding(), src2.encoding(), ConditionFlag.OverflowSet.getValue());
        }
    }

    public static class Udivx extends Fmt3b {

        public Udivx(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Udivx, src1, simm13, dst);
        }

        public Udivx(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Udivx, src1, src2, dst);
        }
    }

    @Deprecated
    public static class Wry extends Fmt3b {

        @Deprecated
        public Wry(Register src1, int simm13) {
            super(Ops.ArithOp, Op3s.Wrreg, src1, simm13, r0);
        }

        @Deprecated
        public Wry(Register src1, Register src2) {
            super(Ops.ArithOp, Op3s.Wrreg, src1, src2, r0);
        }
    }

    public static class Wrccr extends Fmt3b {

        public Wrccr(Register src1, int simm13) {
            super(Ops.ArithOp, Op3s.Wrreg, src1, simm13, r2);
        }

        public Wrccr(Register src1, Register src2) {
            super(Ops.ArithOp, Op3s.Wrreg, src1, src2, r2);
        }
    }

    public static class Wrasi extends Fmt3b {

        public Wrasi(Register src1, int simm13) {
            super(Ops.ArithOp, Op3s.Wrreg, src1, simm13, r3);
        }

        public Wrasi(Register src1, Register src2) {
            super(Ops.ArithOp, Op3s.Wrreg, src1, src2, r3);
        }
    }

    public static class Wrfprs extends Fmt3b {

        public Wrfprs(Register src1, int simm13) {
            super(Ops.ArithOp, Op3s.Wrreg, src1, simm13, r6);
        }

        public Wrfprs(Register src1, Register src2) {
            super(Ops.ArithOp, Op3s.Wrreg, src1, src2, r6);
        }
    }

    public static class Xor extends Fmt3b {

        public Xor(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Xor, src1, simm13, dst);
        }

        public Xor(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Xor, src1, src2, dst);
        }
    }

    public static class Xorcc extends Fmt3b {

        public Xorcc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Xorcc, src1, simm13, dst);
        }

        public Xorcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Xorcc, src1, src2, dst);
        }
    }

    public static class Xnor extends Fmt3b {

        public Xnor(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Xnor, src1, simm13, dst);
        }

        public Xnor(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Xnor, src1, src2, dst);
        }
    }

    public static class Xnorcc extends Fmt3b {

        public Xnorcc(Register src1, int simm13, Register dst) {
            super(Ops.ArithOp, Op3s.Xnorcc, src1, simm13, dst);
        }

        public Xnorcc(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Xnorcc, src1, src2, dst);
        }
    }
}
