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
package com.oracle.graal.asm.sparc;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.sparc.*;

/**
 * This class implements an assembler that can encode most SPARC instructions.
 */
public abstract class SPARCAssembler extends Assembler {

    /**
     * Constructs an assembler for the SPARC architecture.
     *
     * @param registerConfig the register configuration used to bind {@link Register#Frame} and
     *            {@link Register#CallerFrame} to physical registers. This value can be null if this
     *            assembler instance will not be used to assemble instructions using these logical
     *            registers.
     */
    public SPARCAssembler(TargetDescription target, RegisterConfig registerConfig) {
        super(target);
    }

    public interface AssemblerEmittable {
        void emit(SPARCAssembler masm);
    }

    public static final int CCR_ICC_SHIFT = 0;
    public static final int CCR_XCC_SHIFT = 4;
    public static final int CCR_C_SHIFT = 0;
    public static final int CCR_V_SHIFT = 1;
    public static final int CCR_Z_SHIFT = 2;
    public static final int CCR_N_SHIFT = 3;

    protected static final int OP_SHIFT = 30;
    protected static final int CBCOND_SHIFT = 28;
    protected static final int OP2_SHIFT = 22;
    protected static final int A_SHIFT = 29;

    // @formatter:off
    protected static final int A_MASK        = 0b0010_0000_0000_0000_0000_0000_0000_0000;
    protected static final int OP_MASK     = 0b1100_0000_0000_0000_0000_0000_0000_0000;
    protected static final int CBCOND_MASK = 0b0001_0000_0000_0000_0000_0000_0000_0000; // Used for distinguish CBcond and BPr instructions
    protected static final int OP2_MASK    = 0b0000_0001_1100_0000_0000_0000_0000_0000;

    // @formatter:off
    /**
     * Instruction format for Fmt00 instructions. This abstraction is needed as it
     * makes the patching easier later on.
     * <pre>
     * | 00  |  ??    | op2 |               ??                        |
     * |31 30|29    25|24 22|21                                      0|
     * </pre>
     */
    // @formatter:on
    public abstract static class Fmt00 implements AssemblerEmittable {

        private int op2;

        public Fmt00(int op2) {
            this.op2 = op2;
        }

        public void write(SPARCAssembler masm, int pos) {
            verify();
            masm.emitInt(getInstructionBits(), pos);
        }

        public Op2s getOp2s() {
            return Op2s.byValue(op2);
        }

        protected int getInstructionBits() {
            return Ops.BranchOp.getValue() << OP_SHIFT | op2 << OP2_SHIFT;
        }

        public void verify() {
            assert ((op2 << OP2_SHIFT) & OP2_MASK) == (op2 << OP2_SHIFT) : Integer.toHexString(op2);
            assert Op2s.byValue(op2) != null : op2;
        }

        /**
         * Sets the immediate (displacement) value on this instruction.
         *
         * @see SPARCAssembler#patchJumpTarget(int, int)
         * @param imm Displacement/imediate value. Can either be a 22 or 19 bit immediate (dependent
         *            on the instruction)
         */
        public abstract void setImm(int imm);

        public abstract void emit(SPARCAssembler masm);

        public boolean hasDelaySlot() {
            return true;
        }

        public int getA() {
            throw GraalInternalError.shouldNotReachHere();
        }

        public void setA(@SuppressWarnings("unused") int a) {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    // @formatter:off
    /**
     * Instruction format for sethi.
     * <pre>
     * | 00  |  rd    | op2 |               imm22                     |
     * |31 30|29    25|24 22|21                                      0|
     * </pre>
     */
    // @formatter:on
    public static class Fmt00a extends Fmt00 implements AssemblerEmittable {

        private static final int RD_SHIFT = 25;
        private static final int IMM22_SHIFT = 0;

        // @formatter:off
        private static final int RD_MASK    = 0b00111110000000000000000000000000;
        private static final int IMM22_MASK = 0b00000000001111111111111111111111;
        // @formatter:on

        private int rd;
        private int imm22;

        private Fmt00a(int rd, int op2, int imm22) {
            super(op2);
            this.rd = rd;
            this.imm22 = imm22;
            verify();
        }

        public Fmt00a(Op2s op2, int imm22, Register rd) {
            this(rd.encoding(), op2.getValue(), imm22);
        }

        @Override
        protected int getInstructionBits() {
            return super.getInstructionBits() | rd << RD_SHIFT | (imm22 & IMM22_MASK) << IMM22_SHIFT;
        }

        public static Fmt00a read(SPARCAssembler masm, int pos) {
            final int inst = masm.getInt(pos);

            // Make sure it's the right instruction:
            final int op = (inst & OP_MASK) >> OP_SHIFT;
            assert op == Ops.BranchOp.getValue();

            // Get the instruction fields:
            final int rd = (inst & RD_MASK) >> RD_SHIFT;
            final int op2 = (inst & OP2_MASK) >> OP2_SHIFT;
            final int imm22 = (inst & IMM22_MASK) >> IMM22_SHIFT;

            return new Fmt00a(op2, imm22, rd);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            verify();
            masm.emitInt(getInstructionBits());
        }

        @Override
        public void verify() {
            super.verify();
            assert ((rd << RD_SHIFT) & RD_MASK) == (rd << RD_SHIFT);
            assert ((imm22 << IMM22_SHIFT) & IMM22_MASK) == (imm22 << IMM22_SHIFT) : String.format("imm22: %d (%x)", imm22, imm22);
        }

        @Override
        public void setImm(int imm) {
            setImm22(imm);
        }

        public void setImm22(int imm22) {
            this.imm22 = imm22;
        }

        @Override
        public boolean hasDelaySlot() {
            return false;
        }
    }

    protected static final int DISP22_SHIFT = 0;
    protected static final int DISP22_MASK = 0b00000000001111111111111111111111;

    protected static final int DISP19_SHIFT = 0;
    protected static final int DISP19_MASK = 0b00000000000001111111111111111111;

    protected static final int D16HI_SHIFT = 20;
    protected static final int D16HI_MASK = 0b0000_0000_0011_0000_0000_0000_0000_0000;
    protected static final int D16LO_SHIFT = 0;
    protected static final int D16LO_MASK = 0b0000_0000_0000_0000_0011_1111_1111_1111;

    // @formatter:off
    /**
     * Instruction format CBcond.
     * <pre>
     * |00   |chi|1 | clo | 011 |cc2|d10hi|rs1  |i |d10lo|rs2/simm5|
     * |31 30|29 |28|27 25|24 22|21 |20 19|18 14|13|12  5|4       0|
     * </pre>
     */
    // @formatter:on
    public static class Fmt00e extends Fmt00 {
        protected static final int CHI_SHIFT = 29;
        protected static final int CLO_SHIFT = 25;
        protected static final int CC2_SHIFT = 21;
        protected static final int D10HI_SHIFT = 19;
        protected static final int RS1_SHIFT = 14;
        protected static final int I_SHIFT = 13;
        protected static final int D10LO_SHIFT = 5;
        protected static final int RS2_SHIFT = 0;

        // @formatter:off
        protected static final int CHI_MASK      = 0b0010_0000_0000_0000_0000_0000_0000_0000;
        protected static final int CLO_MASK      = 0b0000_1110_0000_0000_0000_0000_0000_0000;
        protected static final int CC2_MASK      = 0b0000_0000_0010_0000_0000_0000_0000_0000;
        protected static final int D10HI_MASK    = 0b0000_0000_0001_1000_0000_0000_0000_0000;
        protected static final int RS1_MASK      = 0b0000_0000_0000_0111_1100_0000_0000_0000;
        protected static final int I_MASK        = 0b0000_0000_0000_0000_0010_0000_0000_0000;
        protected static final int D10LO_MASK    = 0b0000_0000_0000_0000_0001_1111_1110_0000;
        protected static final int RS2_MASK      = 0b0000_0000_0000_0000_0000_0000_0001_1111;
        // @formatter:on

        private int c;
        private int cc2;
        private int disp10;
        private int rs1;
        private int i;
        private int regOrImmediate;
        private Label label;

        public Fmt00e(int c, int cc2, int rs1, int disp10, int i, int regOrImmediate, Label label) {
            super(Op2s.Bpr.getValue());
            this.c = c;
            this.cc2 = cc2;
            this.rs1 = rs1;
            setDisp10(disp10);
            this.i = i;
            this.regOrImmediate = regOrImmediate;
            this.label = label;
        }

        @Override
        public void setImm(int imm) {
            setDisp10(imm);
        }

        public void setDisp10(int disp10) {
            this.disp10 = disp10 >> 2;
            if (!isSimm10(this.disp10)) {
                throw GraalInternalError.shouldNotReachHere("" + this.disp10);
            }
            assert isSimm10(this.disp10) : this.disp10;
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.CBCOND);
            if (label != null) {
                if (label.isBound()) {
                    final int disp = label.position() - masm.position();
                    setDisp10(disp);
                } else {
                    patchUnbound(masm, label);
                    setDisp10(0);
                }
            }
            verify();
            masm.emitInt(getInstructionBits());
        }

        private static int patchUnbound(SPARCAssembler masm, Label label) {
            label.addPatchAt(masm.position());
            return 0;
        }

        @Override
        protected int getInstructionBits() {
            int cSplit = 0;
            cSplit |= (c & 0b1000) << CHI_SHIFT - 3;
            cSplit |= (c & 0b0111) << CLO_SHIFT;
            int d10Split = 0;
            d10Split |= (disp10 & 0b11_0000_0000) << D10HI_SHIFT - 8;
            d10Split |= (disp10 & 0b00_1111_1111) << D10LO_SHIFT;
            int bits = super.getInstructionBits() | 1 << 28 | cSplit | cc2 << CC2_SHIFT | d10Split | rs1 << RS1_SHIFT | i << I_SHIFT | (regOrImmediate & 0b1_1111) << RS2_SHIFT;
            int hibits = (bits & 0xFF000000);
            if (hibits == 0xFF000000 || hibits == 0) {
                throw GraalInternalError.shouldNotReachHere();
            }
            return bits;
        }

        public static Fmt00e read(SPARCAssembler masm, int pos) {
            assert masm.hasFeature(CPUFeature.CBCOND);
            final int inst = masm.getInt(pos);

            // Make sure it's the right instruction:
            final int op = (inst & OP_MASK) >> OP_SHIFT;
            final int op2 = (inst & OP2_MASK) >> OP2_SHIFT;
            final int condFlag = (inst & CBCOND_MASK) >> CBCOND_SHIFT;
            assert op2 == Op2s.Bpr.getValue() && op == Ops.BranchOp.getValue() && condFlag == 1 : "0x" + Integer.toHexString(inst);

            // @formatter:off
            // Get the instruction fields:
            final int chi =            (inst & CHI_MASK)   >> CHI_SHIFT;
            final int clo =            (inst & CLO_MASK)   >> CLO_SHIFT;
            final int cc2 =            (inst & CC2_MASK)   >> CC2_SHIFT;
            final int d10hi =          (inst & D10HI_MASK) >> D10HI_SHIFT;
            final int rs1 =            (inst & RS1_MASK)   >> RS1_SHIFT;
            final int i =              (inst & I_MASK)     >> I_SHIFT;
            final int d10lo =          (inst & D10LO_MASK) >> D10LO_SHIFT;
                  int regOrImmediate = (inst & RS2_MASK)   >> RS2_SHIFT;
            // @formatter:on
            if (i == 1) { // if immediate, we do sign extend
                int shiftcnt = 31 - 4;
                regOrImmediate = (regOrImmediate << shiftcnt) >> shiftcnt;
            }
            int c = chi << 3 | clo;

            assert (d10lo & ~((1 << 8) - 1)) == 0;
            final int d10 = ((short) (((d10hi << 8) | d10lo) << 6)) >> 4; // Times 4 and sign extend
            Fmt00e fmt = new Fmt00e(c, cc2, rs1, d10, i, regOrImmediate, null);
            fmt.verify();
            return fmt;
        }

        @Override
        public void verify() {
            super.verify();
            assert (c & ~0b1111) == 0 : c;
            assert (cc2 & ~1) == 0 : cc2;
            assert isSimm(disp10, 10) : disp10;
            assert (rs1 & ~0b1_1111) == 0 : rs1;
            assert (i & ~1) == 0 : i;
            if (i == 1) {
                assert isSimm(regOrImmediate, 5) : regOrImmediate;
            } else {
                assert (regOrImmediate & ~0b1_1111) == 0 : regOrImmediate;
            }
        }

        @Override
        public boolean hasDelaySlot() {
            return false;
        }
    }

    // @formatter:off
    /**
     * Instruction format for calls.
     * <pre>
     * | 01  |                      disp30                             |
     * |31 30|29                                                      0|
     * </pre>
     */
    // @formatter:on
    public static class Fmt01 {

        private static final int DISP30_SHIFT = 0;

        // @formatter:off
        private static final int DISP30_MASK = 0b00111111111111111111111111111111;
        // @formatter:on

        private int disp30;

        public Fmt01(int disp30) {
            setDisp30(disp30);
        }

        /**
         * Return the displacement in bytes.
         */
        public int getDisp30() {
            return disp30 << 2;
        }

        /**
         * The instructions requires displacements to be word-sized.
         */
        public void setDisp30(int disp30) {
            this.disp30 = disp30 >> 2;
        }

        private int getInstructionBits() {
            return Ops.CallOp.getValue() << OP_SHIFT | (disp30 & DISP30_MASK) << DISP30_SHIFT;
        }

        public static Fmt01 read(SPARCAssembler masm, int pos) {
            final int inst = masm.getInt(pos);

            // Make sure it's the right instruction:
            final int op = (inst & OP_MASK) >> OP_SHIFT;
            assert op == Ops.CallOp.getValue();

            // Get the instruction fields:
            final int disp30 = (inst & DISP30_MASK) >> DISP30_SHIFT << 2;

            Fmt01 fmt = new Fmt01(disp30);
            fmt.verify();
            return fmt;
        }

        public void write(SPARCAssembler masm, int pos) {
            verify();
            masm.emitInt(getInstructionBits(), pos);
        }

        public void emit(SPARCAssembler masm) {
            verify();
            masm.emitInt(getInstructionBits());
        }

        public void verify() {
            assert isDisp30(disp30) : disp30;
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
        private int op;
        private int op3;
        private int opf;
        private int rs2;
        private int rd;

        public Fmt3n(int op, int op3, int opf, int rs2, int rd) {
            this.op = op;
            this.op3 = op3;
            this.opf = opf;
            this.rs2 = rs2;
            this.rd = rd;
        }

        public void emit(SPARCAssembler masm) {
            verify();
            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | opf << 5 | rs2);
        }

        public void verify() {
            assert op == 2 || op == 3;
            assert op3 >= 0 && op3 < 0x40;
            assert opf >= 0 && opf < 0x200;
            assert rs2 >= 0 && rs2 < 0x20;
            assert rd >= 0 && rd < 0x20;
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
            assert op == 2 || op == 3 : op;
            assert op3 >= 0 && op3 < 0x40 : op3;
            assert opf >= 0 && opf < 0x200 : opf;
            assert rs1 >= 0 && rs1 < 0x20 : rs1;
            assert rs2 >= 0 && rs2 < 0x20 : rs2;
            assert rd >= 0 && rd < 0x20 : rd;

            masm.emitInt(op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | opf << 5 | rs2);
        }
    }

    // @formatter:off
    /**
     * Instruction format for fcmp.
     * <pre>
     * | 10  | --- |cc1|cc0|desc |   rs1   |   opf  | rs2 |
     * |31 30|29 27|26 |25 |24 19|18     14|13     5|4   0|
     * </pre>
     */
    // @formatter:on
    public static class Fmt3c {
        private int op;
        private int cc;
        private int desc;
        private int opf;
        private int rs1;
        private int rs2;

        public Fmt3c(Ops op, CC cc, int desc, Opfs opf, Register rs1, Register rs2) {
            this.op = op.getValue();
            this.opf = opf.getValue();
            this.desc = desc;
            this.rs1 = rs1.encoding();
            this.rs2 = rs2.encoding();
            this.cc = cc.getValue();
        }

        public void emit(SPARCAssembler masm) {
            assert op == 2 || op == 3;
            assert cc >= 0 && cc < 0x4;
            assert opf >= 0 && opf < 0x200;
            assert rs1 >= 0 && rs1 < 0x20;
            assert rs2 >= 0 && rs2 < 0x20;
            assert desc >= 0 && desc < 0x40;

            masm.emitInt(op << 30 | cc << 25 | desc << 19 | rs1 << 14 | opf << 5 | rs2);
        }
    }

    // @formatter:off
    /**
     * Instruction format for Arithmetic, Logical, Moves, Tcc, Prefetch, and Misc.
     * <pre>
     * | 10  |   rd   |   op3   |   rs1   | i|     imm_asi   |   rs2   |
     * | 10  |   rd   |   op3   |   rs1   | i|          simm13         |
     * | 10  |   rd   |   op3   |   rs1   | i| x|            |   rs2   |
     * | 10  |   rd   |   op3   |   rs1   | i| x|            | shcnt32 |
     * | 10  |   rd   |   op3   |   rs1   | i| x|            | shcnt64 |
     * |31 30|29    25|24     19|18     14|13|12|11         5|4       0|
     * </pre>
     */
    // @formatter:on
    public static class Fmt10 implements AssemblerEmittable {

        private static final int RD_SHIFT = 25;
        private static final int OP3_SHIFT = 19;
        private static final int RS1_SHIFT = 14;
        private static final int I_SHIFT = 13;
        private static final int X_SHIFT = 12;
        private static final int IMM_ASI_SHIFT = 5;
        private static final int RS2_SHIFT = 0;
        private static final int SIMM13_SHIFT = 0;

        // @formatter:off
        private static final int RD_MASK      = 0b00111110000000000000000000000000;
        private static final int OP3_MASK     = 0b00000001111110000000000000000000;
        private static final int RS1_MASK     = 0b00000000000001111100000000000000;
        private static final int I_MASK       = 0b00000000000000000010000000000000;
        private static final int X_MASK       = 0b00000000000000000001000000000000;
        private static final int IMM_ASI_MASK = 0b00000000000000000001111111100000;
        private static final int RS2_MASK     = 0b00000000000000000000000000011111;
        private static final int SIMM13_MASK  = 0b00000000000000000001111111111111;
        // @formatter:on

        private int rd;
        private int op3;
        private int rs1;
        private int i;
        private int x;
        private int immAsi;
        private int rs2;
        private int simm13;

        private Fmt10(int rd, int op3, int rs1, int i, int x, int immAsi, int rs2, int simm13) {
            this.rd = rd;
            this.op3 = op3;
            this.rs1 = rs1;
            this.i = i;
            this.x = x;
            this.immAsi = immAsi;
            this.rs2 = rs2;
            this.simm13 = simm13;
            verify();
        }

        public Fmt10(Op3s op3, Register rs1, Register rs2, Register rd) {
            this(rd.encoding(), op3.getValue(), rs1.encoding(), 0, getXBit(op3), 0, rs2.encoding(), 0);
        }

        public Fmt10(Op3s op3, Register rs1, int simm13, Register rd) {
            this(rd.encoding(), op3.getValue(), rs1.encoding(), 1, getXBit(op3), 0, 0, simm13);
        }

        /**
         * Used for trap on Integer Condition Codes (Tcc).
         *
         * @param op3
         * @param rs1
         * @param simm13
         * @param cf
         */
        public Fmt10(Op3s op3, Register rs1, int simm13, ConditionFlag cf) {
            this(cf.getValue(), op3.getValue(), rs1.encoding(), 1, getXBit(op3), 0, 0, simm13);
        }

        public Fmt10(Op3s op3) {
            this(0, op3.getValue(), 0, 0, getXBit(op3), 0, 0, 0);
        }

        public Fmt10(Op3s op3, Register rs1, Register rd) {
            this(rd.encoding(), op3.getValue(), rs1.encoding(), 0, getXBit(op3), 0, 0, 0);
        }

        /**
         * Helper method to determine if the instruction needs the X bit set.
         */
        private static int getXBit(Op3s op3) {
            switch (op3) {
                case Sllx:
                case Srax:
                case Srlx:
                    return 1;
                default:
                    return 0;
            }
        }

        private int getInstructionBits() {
            if (i == 0) {
                return Ops.ArithOp.getValue() << OP_SHIFT | rd << RD_SHIFT | op3 << OP3_SHIFT | rs1 << RS1_SHIFT | i << I_SHIFT | x << X_SHIFT | immAsi << IMM_ASI_SHIFT | rs2 << RS2_SHIFT;
            } else {
                return Ops.ArithOp.getValue() << OP_SHIFT | rd << RD_SHIFT | op3 << OP3_SHIFT | rs1 << RS1_SHIFT | i << I_SHIFT | x << X_SHIFT | ((simm13 << SIMM13_SHIFT) & SIMM13_MASK);
            }
        }

        public static Fmt10 read(SPARCAssembler masm, int pos) {
            final int inst = masm.getInt(pos);

            // Make sure it's the right instruction:
            final int op = (inst & OP_MASK) >> OP_SHIFT;
            assert op == Ops.ArithOp.getValue();

            // Get the instruction fields:
            final int rd = (inst & RD_MASK) >> RD_SHIFT;
            final int op3 = (inst & OP3_MASK) >> OP3_SHIFT;
            final int rs1 = (inst & RS1_MASK) >> RS1_SHIFT;
            final int i = (inst & I_MASK) >> I_SHIFT;
            final int x = (inst & X_MASK) >> X_SHIFT;
            final int immAsi = (inst & IMM_ASI_MASK) >> IMM_ASI_SHIFT;
            final int rs2 = (inst & RS2_MASK) >> RS2_SHIFT;
            final int simm13 = (inst & SIMM13_MASK) >> SIMM13_SHIFT;

            return new Fmt10(rd, op3, rs1, i, x, immAsi, rs2, simm13);
        }

        public void write(SPARCAssembler masm, int pos) {
            verify();
            masm.emitInt(getInstructionBits(), pos);
        }

        public void emit(SPARCAssembler masm) {
            verify();
            masm.emitInt(getInstructionBits());
        }

        public void verify() {
            assert ((rd << RD_SHIFT) & RD_MASK) == (rd << RD_SHIFT) : this;
            assert ((op3 << OP3_SHIFT) & OP3_MASK) == (op3 << OP3_SHIFT) : this;
            assert ((rs1 << RS1_SHIFT) & RS1_MASK) == (rs1 << RS1_SHIFT) : this;
            assert ((i << I_SHIFT) & I_MASK) == (i << I_SHIFT) : this;
            assert ((x << X_SHIFT) & X_MASK) == (x << X_SHIFT) : this;
            assert ((immAsi << IMM_ASI_SHIFT) & IMM_ASI_MASK) == (immAsi << IMM_ASI_SHIFT) : this;
            assert ((rs2 << RS2_SHIFT) & RS2_MASK) == (rs2 << RS2_SHIFT) : this;
            assert isSimm13(simm13) : this;
        }

        @Override
        public String toString() {
            return String.format("%s: [rd: 0x%x, op3: 0x%x, rs1: 0x%x, i: 0x%x, x: 0x%x, immAsi: 0x%x, rs2: 0x%x, simm13: 0x%x", getClass().getName(), rd, op3, rs1, i, x, immAsi, rs2, simm13);
        }
    }

    // @formatter:off
    /**
     * Instruction format for Loads, Stores and Misc.
     * <pre>
     * | 11  |   rd   |   op3   |   rs1   | i|   imm_asi   |   rs2   |
     * | 11  |   rd   |   op3   |   rs1   | i|        simm13         |
     * |31 30|29    25|24     19|18     14|13|12          5|4       0|
     * </pre>
     */
    // @formatter:on
    public static class Fmt11 {

        private static final int RD_SHIFT = 25;
        private static final int OP3_SHIFT = 19;
        private static final int RS1_SHIFT = 14;
        private static final int I_SHIFT = 13;
        private static final int IMM_ASI_SHIFT = 5;
        private static final int RS2_SHIFT = 0;
        private static final int SIMM13_SHIFT = 0;

        // @formatter:off
        private static final int RD_MASK      = 0b00111110000000000000000000000000;
        private static final int OP3_MASK     = 0b00000001111110000000000000000000;
        private static final int RS1_MASK     = 0b00000000000001111100000000000000;
        private static final int I_MASK       = 0b00000000000000000010000000000000;
        private static final int IMM_ASI_MASK = 0b00000000000000000001111111100000;
        private static final int RS2_MASK     = 0b00000000000000000000000000011111;
        private static final int SIMM13_MASK  = 0b00000000000000000001111111111111;
        // @formatter:on

        private int rd;
        private int op3;
        private int rs1;
        private int i;
        private int immAsi;
        private int rs2;
        private int simm13;

        private Fmt11(int rd, int op3, int rs1, int i, int immAsi, int rs2, int simm13) {
            this.rd = rd;
            this.op3 = op3;
            this.rs1 = rs1;
            this.i = i;
            this.immAsi = immAsi;
            this.rs2 = rs2;
            this.simm13 = simm13;
            verify();
        }

        public Fmt11(Op3s op3, Register rs1, Register rs2, Register rd) {
            this(rd.encoding(), op3.getValue(), rs1.encoding(), 0, 0, rs2.encoding(), 0);
        }

        public Fmt11(Op3s op3, Register rs1, int simm13, Register rd) {
            this(rd.encoding(), op3.getValue(), rs1.encoding(), 1, 0, 0, simm13);
        }

        public Fmt11(Op3s op3, Register rs1, Register rd) {
            this(rd.encoding(), op3.getValue(), rs1.encoding(), 0, 0, 0, 0);
        }

        /**
         * Special constructor for {@link Casa} and {@link Casxa}.
         */
        public Fmt11(Op3s op3, Register rs1, Register rs2, Register rd, Asi asi) {
            this(rd.encoding(), op3.getValue(), rs1.encoding(), asi.isValid() ? 0 : 1, asi.isValid() ? asi.getValue() : 0, rs2.encoding(), 0);
            assert asi.isValid() : "default asi is not supported yet";
        }

        /**
         * Special constructor for loads and stores.
         */
        public Fmt11(Op3s op3, SPARCAddress addr, Register rd) {
            this(rd.encoding(), op3.getValue(), addr.getBase().encoding(), 0, 0, 0, 0);
            decodeAddress(addr);
        }

        /**
         * Special constructor for {@link Prefetch} and Prefetcha.
         */
        public Fmt11(Op3s op3, SPARCAddress addr, Prefetch.Fcn fcn) {
            this(fcn.getValue(), op3.getValue(), addr.getBase().encoding(), 0, 0, 0, 0);
            decodeAddress(addr);
        }

        private void decodeAddress(SPARCAddress addr) {
            if (!addr.getIndex().equals(Register.None)) {
                this.rs2 = addr.getIndex().encoding();
            } else {
                this.simm13 = addr.getDisplacement();
                this.i = 1;
            }
            verify();
        }

        private int getInstructionBits() {
            if (i == 0) {
                return Ops.LdstOp.getValue() << OP_SHIFT | rd << RD_SHIFT | op3 << OP3_SHIFT | rs1 << RS1_SHIFT | i << I_SHIFT | immAsi << IMM_ASI_SHIFT | rs2 << RS2_SHIFT;
            } else {
                return Ops.LdstOp.getValue() << OP_SHIFT | rd << RD_SHIFT | op3 << OP3_SHIFT | rs1 << RS1_SHIFT | i << I_SHIFT | ((simm13 << SIMM13_SHIFT) & SIMM13_MASK);
            }
        }

        public static Fmt11 read(SPARCAssembler masm, int pos) {
            final int inst = masm.getInt(pos);

            // Make sure it's the right instruction:
            final int op = (inst & OP_MASK) >> OP_SHIFT;
            assert op == Ops.LdstOp.getValue();

            // Get the instruction fields:
            final int rd = (inst & RD_MASK) >> RD_SHIFT;
            final int op3 = (inst & OP3_MASK) >> OP3_SHIFT;
            final int rs1 = (inst & RS1_MASK) >> RS1_SHIFT;
            final int i = (inst & I_MASK) >> I_SHIFT;
            final int immAsi = (inst & IMM_ASI_MASK) >> IMM_ASI_SHIFT;
            final int rs2 = (inst & RS2_MASK) >> RS2_SHIFT;
            final int simm13 = (inst & SIMM13_MASK) >> SIMM13_SHIFT;

            return new Fmt11(rd, op3, rs1, i, immAsi, rs2, simm13);
        }

        public void write(SPARCAssembler masm, int pos) {
            verify();
            masm.emitInt(getInstructionBits(), pos);
        }

        public void emit(SPARCAssembler masm) {
            verify();
            masm.emitInt(getInstructionBits());
        }

        public void verify() {
            assert ((rd << RD_SHIFT) & RD_MASK) == (rd << RD_SHIFT) : rd;
            assert ((op3 << OP3_SHIFT) & OP3_MASK) == (op3 << OP3_SHIFT) : op3;
            assert ((rs1 << RS1_SHIFT) & RS1_MASK) == (rs1 << RS1_SHIFT) : rs1;
            assert ((i << I_SHIFT) & I_MASK) == (i << I_SHIFT);
            assert ((immAsi << IMM_ASI_SHIFT) & IMM_ASI_MASK) == (immAsi << IMM_ASI_SHIFT);
            assert ((rs2 << RS2_SHIFT) & RS2_MASK) == (rs2 << RS2_SHIFT);
            assert isSimm13(simm13) : String.format("simm13: %d (%x)", simm13, simm13);
        }
    }

    // @formatter:off
    /**
     * Instruction format for Movcc.
     * <pre>
     * | 10  |   rd   |   op3   |cc2|   cond  | i|cc1|cc0|      -      |   rs2   |
     * | 10  |   rd   |   op3   |cc2|   cond  | i|cc1|cc0|        simm11         |
     * |31 30|29    25|24     19| 18|17     14|13| 12| 11|10          5|4       0|
     * </pre>
     */
    // @formatter:on
    public static class Fmt10c {

        private static final int RD_SHIFT = 25;
        private static final int OP3_SHIFT = 19;
        private static final int CC2_SHIFT = 18;
        private static final int COND_SHIFT = 14;
        private static final int I_SHIFT = 13;
        private static final int CC1_SHIFT = 12;
        private static final int CC0_SHIFT = 11;
        private static final int RS2_SHIFT = 0;
        private static final int SIMM11_SHIFT = 0;

        // @formatter:off
        private static final int RD_MASK     = 0b00111110000000000000000000000000;
        private static final int OP3_MASK    = 0b00000001111110000000000000000000;
        private static final int CC2_MASK    = 0b00000000000001000000000000000000;
        private static final int COND_MASK   = 0b00000000000000111100000000000000;
        private static final int I_MASK      = 0b00000000000000000010000000000000;
        private static final int CC1_MASK    = 0b00000000000000000001000000000000;
        private static final int CC0_MASK    = 0b00000000000000000000100000000000;
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
            this(rd.encoding(), op3.getValue(), cond.getValue(), 0, getCC(cc), rs2.encoding(), 0);
        }

        public Fmt10c(Op3s op3, ConditionFlag cond, CC cc, int simm11, Register rd) {
            this(rd.encoding(), op3.getValue(), cond.getValue(), 1, getCC(cc), 0, simm11);
        }

        /**
         * Converts regular CC codes to CC codes used by Movcc instructions.
         */
        public static int getCC(CC cc) {
            switch (cc) {
                case Icc:
                case Xcc:
                    return 0b100 + cc.getValue();
                default:
                    return cc.getValue();
            }
        }

        private int getInstructionBits() {
            if (i == 0) {
                return Ops.ArithOp.getValue() << OP_SHIFT | rd << RD_SHIFT | op3 << OP3_SHIFT | ((cc << (CC2_SHIFT - 2)) & CC2_MASK) | cond << COND_SHIFT | i << I_SHIFT |
                                ((cc << (CC1_SHIFT - 1)) & CC1_MASK) | ((cc << CC0_SHIFT) & CC0_MASK) | rs2 << RS2_SHIFT;
            } else {
                return Ops.ArithOp.getValue() << OP_SHIFT | rd << RD_SHIFT | op3 << OP3_SHIFT | ((cc << (CC2_SHIFT - 2)) & CC2_MASK) | cond << COND_SHIFT | i << I_SHIFT |
                                ((cc << (CC1_SHIFT - 1)) & CC1_MASK) | ((cc << CC0_SHIFT) & CC0_MASK) | ((simm11 << SIMM11_SHIFT) & SIMM11_MASK);
            }
        }

        public static Fmt10c read(SPARCAssembler masm, int pos) {
            final int inst = masm.getInt(pos);

            // Make sure it's the right instruction:
            final int op = (inst & OP_MASK) >> OP_SHIFT;
            assert op == Ops.ArithOp.getValue();

            // Get the instruction fields:
            final int rd = (inst & RD_MASK) >> RD_SHIFT;
            final int op3 = (inst & OP3_MASK) >> OP3_SHIFT;
            final int cond = (inst & COND_MASK) >> COND_SHIFT;
            final int i = (inst & I_MASK) >> I_SHIFT;
            final int cc = (inst & CC2_MASK) >> CC2_SHIFT | (inst & CC1_MASK) >> CC1_SHIFT | (inst & CC0_MASK) >> CC0_SHIFT;
            final int rs2 = (inst & RS2_MASK) >> RS2_SHIFT;
            final int simm11 = (inst & SIMM11_MASK) >> SIMM11_SHIFT;

            return new Fmt10c(rd, op3, cond, i, cc, rs2, simm11);
        }

        public void write(SPARCAssembler masm, int pos) {
            verify();
            masm.emitInt(getInstructionBits(), pos);
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
            assert isSimm11(simm11);
        }
    }

    // @formatter:off
    /**
     * Instruction format for Fmovcc.
     * <pre>
     * | 10  |   rd   |   op3   | -|   cond  | opfcc | opf_low |   rs2   |
     * |31 30|29    25|24     19|18|17     14|13   11|10      5|4       0|
     * </pre>
     */
    // @formatter:on
    public static class Fmt10d implements AssemblerEmittable {

        private static final int RD_SHIFT = 25;
        private static final int OP3_SHIFT = 19;
        private static final int COND_SHIFT = 14;
        private static final int OPFCC_SHIFT = 12;
        private static final int OPF_LOW_SHIFT = 11;
        private static final int RS2_SHIFT = 0;

        // @formatter:off
        private static final int RD_MASK      = 0b0011_1110_0000_0000_0000_0000_0000_0000;
        private static final int OP3_MASK     = 0b0000_0001_1111_1000_0000_0000_0000_0000;
        private static final int COND_MASK    = 0b0000_0000_0000_0011_1100_0000_0000_0000;
        private static final int OPFCC_MASK   = 0b0000_0000_0000_0000_0011_1000_0000_0000;
        private static final int OPF_LOW_MASK = 0b0000_0000_0000_0000_0000_0111_1110_0000;
        private static final int RS2_MASK     = 0b0000_0000_0000_0000_0000_0000_0001_1111;
        // @formatter:on

        private int rd;
        private int op3;
        private int cond;
        private int opfcc;
        private int opfLow;
        private int rs2;

        public Fmt10d(Op3s op3, Opfs opf, ConditionFlag cond, CC cc, Register rs2, Register rd) {
            this(rd.encoding(), op3.getValue(), cond.getValue(), Fmt10c.getCC(cc), opf.getValue(), rs2.encoding());
        }

        public Fmt10d(int rd, int op3, int cond, int opfcc, int opfLow, int rs2) {
            super();
            this.rd = rd;
            this.op3 = op3;
            this.cond = cond;
            this.opfcc = opfcc;
            this.opfLow = opfLow;
            this.rs2 = rs2;
        }

        public void emit(SPARCAssembler masm) {
            verify();
            masm.emitInt(getInstructionBits());
        }

        private int getInstructionBits() {
            return Ops.ArithOp.getValue() << OP_SHIFT | rd << RD_SHIFT | op3 << OP3_SHIFT | cond << COND_SHIFT | opfcc << OPFCC_SHIFT | opfLow << OPF_LOW_SHIFT | rs2 << RS2_SHIFT;

        }

        public void verify() {
            assert ((RD_MASK >> RD_SHIFT) & rd) == rd;
            assert ((OP3_MASK >> OP3_SHIFT) & op3) == op3;
            assert ((COND_MASK >> COND_SHIFT) & cond) == cond;
            assert ((OPFCC_MASK >> OPFCC_SHIFT) & opfcc) == opfcc;
            assert ((OPF_LOW_MASK >> OPF_LOW_SHIFT) & opfLow) == opfLow;
            assert ((RS2_MASK >> RS2_SHIFT) & rs2) == rs2;
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
        // @formatter:off

        BranchOp(0b00),
        CallOp(0b01),
        ArithOp(0b10),
        LdstOp(0b11);

        // @formatter:on

        private final int value;

        private Ops(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean appliesTo(int instructionWord) {
            int opShift = 30;
            return (instructionWord >>> opShift) == value;
        }
    }

    public enum Op2s {
        // @formatter:off

        Illtrap(0b000),
        Bpr    (0b011),
        Fb     (0b110),
        Fbp    (0b101),
        Br     (0b010),
        Bp     (0b001),
        Cb     (0b111),
        Sethi  (0b100);


        // @formatter:on

        private final int value;

        private Op2s(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Op2s byValue(int value) {
            for (Op2s op : values()) {
                if (op.getValue() == value) {
                    return op;
                }
            }
            return null;
        }
    }

    public enum Op3s {
        // @formatter:off

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
        // dos not exist
        // Mulxcc(0x19, "mulxcc"),
        Umulcc(0x1A, "umulcc"),
        Smulcc(0x1B, "smulcc"),
        Subccc(0x1C, "subccc"),
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

        Fpop1(0b11_0100, "fpop1"),
        Fpop2(0b11_0101, "fpop2"),
        Impdep1(0b11_0110, "impdep1"),
        Impdep2(0b11_0111, "impdep2"),
        Jmpl(0x38, "jmpl"),
        Rett(0x39, "rett"),
        Trap(0x3a, "trap"),
        Flush(0x3b, "flush"),
        Save(0x3c, "save"),
        Restore(0x3d, "restore"),
        Done(0x3e, "done"),
        Retry(0x3e, "retry"),
        Casa(0b111100, "casa"),
        Casxa(0b111110, "casxa"),
        Prefetch(0b101101, "prefetch"),
        Prefetcha(0b111101, "prefetcha"),

        Lduw  (0b00_0000, "lduw"),
        Ldub  (0b00_0001, "ldub"),
        Lduh  (0b00_0010, "lduh"),
        Stw   (0b00_0100, "stw"),
        Stb   (0b00_0101, "stb"),
        Sth   (0b00_0110, "sth"),
        Ldsw  (0b00_1000, "ldsw"),
        Ldsb  (0b00_1001, "ldsb"),
        Ldsh  (0b00_1010, "ldsh"),
        Ldx   (0b00_1011, "ldx"),
        Stx   (0b00_1110, "stx"),

        Ldf   (0b10_0000, "ldf"),
        Ldfsr (0b10_0001, "ldfsr"),
        Ldaf  (0b10_0010, "ldaf"),
        Lddf  (0b10_0011, "lddf"),
        Stf   (0b10_0100, "stf"),
        Stfsr (0b10_0101, "stfsr"),
        Staf  (0x10_0110, "staf"),
        Rd    (0b10_1000, "rd"),
        Stdf  (0b10_0111, "stdf"),

        Wr    (0b11_0000, "wr"),
        Fcmp  (0b11_0101, "fcmp"),

        Ldxa  (0b01_1011, "ldxa"),
        Lduwa (0b01_0000, "lduwa");

        // @formatter:on

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

        public boolean appliesTo(int instructionWord) {
            return ((instructionWord >>> 19) & 0b1_1111) == value;
        }
    }

    public enum Op5s {
        // @formatter:off

        Fmadds(0x1),
        Fmaddd(0x2),
        Fmsubs(0x5),
        Fmsubd(0x6),
        Fnmsubs(0x9),
        Fnmsubd(0xA),
        Fnmadds(0xD),
        Fnmaddd(0xE);

        // @formatter:on

        private final int value;

        private Op5s(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Opfs {
        // @formatter:off

        Fmovs(0b0_0000_0001, "fmovs"),
        Fmovd(0b0_0000_0010, "fmovd"),
        Fmovq(0b0_0000_0011, "fmovq"),
        Fmovscc(0b00_0001, "fmovscc"),
        Fmovdcc(0b00_0010, "fmovdcc"),
        Fnegs(0x05, "fnegs"),
        Fnegd(0x06, "fnegd"),
        Fnegq(0x07, "fnegq"),
        Fabss(0x09, "fabss"),
        Fabsd(0x0A, "fabsd"),
        Fabsq(0x0B, "fabsq"),

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
        Fandd(0b0_0111_0000, "fandd"),
        Fands(0b0_0111_0001, "fands"),
        Fxord(0b0_0110_1100, "fxord"),
        Fxors(0b0_0110_1101, "fxors"),
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
        Movdtox(0x110, "movdtox"),
        Movstouw(0x111, "movstouw"),
        Movstosw(0x113, "movstosw"),
        Movxtod(0x118, "movxtod"),
        Movwtos(0b1_0001_1001, "movwtos"),
        UMulxhi(0b0_0001_0110, "umulxhi"),
        Lzcnt  (0b0_0001_0111, "lzcnt"),
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
        Fdivs(0x4D, "fdivs"),
        Fdivd(0x4E, "fdivd"),
        Fdivq(0x4F, "fdivq"),

        Fsqrts(0x29, "fsqrts"),
        Fsqrtd(0x2A, "fsqrtd"),
        Fsqrtq(0x2B, "fsqrtq"),

        Fsmuld(0x69, "fsmuld"),
        Fmulq(0x6B, "fmulq"),
        Fdmuldq(0x6E, "fdmulq"),

        Fstoi(0xD1, "fstoi"),
        Fdtoi(0xD2, "fdtoi"),
        Fstox(0x81, "fstox"),
        Fdtox(0x82, "fdtox"),
        Fxtos(0x84, "fxtos"),
        Fxtod(0x88, "fxtod"),
        Fxtoq(0x8C, "fxtoq"),
        Fitos(0xC4, "fitos"),
        Fdtos(0xC6, "fdtos"),
        Fitod(0xC8, "fitod"),
        Fstod(0xC9, "fstod"),
        Fitoq(0xCC, "fitoq"),


        Fcmps(0x51, "fcmps"),
        Fcmpd(0x52, "fcmpd"),
        Fcmpq(0x53, "fcmpq");

        // @formatter:on

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

    public enum Annul {
        ANNUL(1),
        NOT_ANNUL(0);
        public final int flag;

        Annul(int flag) {
            this.flag = flag;
        }
    }

    public enum BranchPredict {
        PREDICT_TAKEN(1),
        PREDICT_NOT_TAKEN(0);
        public final int flag;

        BranchPredict(int flag) {
            this.flag = flag;
        }
    }

    public enum MembarMask {
        // @formatter:off

        StoreStore(1 << 3, "storestore"),
        LoadStore(1 << 2, "loadstore"),
        StoreLoad(1 << 1, "storeload"),
        LoadLoad(1 << 0, "loadload"),
        Sync(1 << 6, "sync"),
        MemIssue(1 << 5, "memissue"),
        LookAside(1 << 4, "lookaside");

        // @formatter:on

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

    /**
     * Condition Codes to use for instruction.
     */
    public enum CC {
        // @formatter:off
        /**
         * Condition is considered as 32bit operation condition.
         */
        Icc(0b00, "icc"),
        /**
         * Condition is considered as 64bit operation condition.
         */
        Xcc(0b10, "xcc"),
        Ptrcc(getHostWordKind() == Kind.Long ? Xcc.getValue() : Icc.getValue(), "ptrcc"),
        Fcc0(0b00, "fcc0"),
        Fcc1(0b01, "fcc1"),
        Fcc2(0b10, "fcc2"),
        Fcc3(0b11, "fcc3");

        // @formatter:on

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

        public static CC forKind(Kind kind) {
            boolean isInt = kind == Kind.Boolean || kind == Kind.Byte || kind == Kind.Char || kind == Kind.Short || kind == Kind.Int;
            boolean isFloat = kind == Kind.Float || kind == Kind.Double;
            boolean isLong = kind == Kind.Long || kind == Kind.Object;
            assert isInt || isFloat || isLong;
            if (isLong) {
                return Xcc;
            } else if (isInt) {
                return Icc;
            } else if (isFloat) {
                return Fcc0;
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public enum ConditionFlag {
        // @formatter:off

        // for FBfcc & FBPfcc instruction
        F_Never(0, "f_never"),
        F_NotEqual(1, "f_notEqual"),
        F_LessOrGreater(2, "f_lessOrGreater"),
        F_UnorderedOrLess(3, "f_unorderedOrLess"),
        F_Less(4, "f_less"),
        F_UnorderedOrGreater(5, "f_unorderedOrGreater"),
        F_Greater(6, "f_greater"),
        F_Unordered(7, "f_unordered"),
        F_Always(8, "f_always"),
        F_Equal(9, "f_equal"),
        F_UnorderedOrEqual(10, "f_unorderedOrEqual"),
        F_GreaterOrEqual(11, "f_greaterOrEqual"),
        F_UnorderedGreaterOrEqual(12, "f_unorderedGreaterOrEqual"),
        F_LessOrEqual(13, "f_lessOrEqual"),
        F_UnorderedOrLessOrEqual(14, "f_unorderedOrLessOrEqual"),
        F_Ordered(15, "f_ordered"),

        // for integers
        Never(0, "never"),
        Equal(1, "equal", true),
        Zero(1, "zero"),
        LessEqual(2, "lessEqual", true),
        Less(3, "less", true),
        LessEqualUnsigned(4, "lessEqualUnsigned", true),
        LessUnsigned(5, "lessUnsigned", true),
        CarrySet(5, "carrySet"),
        Negative(6, "negative", true),
        OverflowSet(7, "overflowSet", true),
        Always(8, "always"),
        NotEqual(9, "notEqual", true),
        NotZero(9, "notZero"),
        Greater(10, "greater", true),
        GreaterEqual(11, "greaterEqual", true),
        GreaterUnsigned(12, "greaterUnsigned", true),
        GreaterEqualUnsigned(13, "greaterEqualUnsigned", true),
        CarryClear(13, "carryClear"),
        Positive(14, "positive", true),
        OverflowClear(15, "overflowClear", true);

        // @formatter:on

        private final int value;
        private final String operator;
        private boolean forCBcond = false;

        private ConditionFlag(int value, String op) {
            this(value, op, false);
        }

        private ConditionFlag(int value, String op, boolean cbcond) {
            this.value = value;
            this.operator = op;
            this.forCBcond = cbcond;
        }

        public boolean isCBCond() {
            return forCBcond;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }

        public ConditionFlag negate() {
            //@formatter:off
            switch (this) {
                case F_Never                  : return F_Always;
                case F_Always                 : return F_Never;
                case F_NotEqual               : return F_Equal;
                case F_Equal                  : return F_NotEqual;
                case F_LessOrGreater          : return F_UnorderedOrEqual;
                case F_UnorderedOrEqual       : return F_LessOrGreater;
                case F_Less                   : return F_UnorderedGreaterOrEqual;
                case F_UnorderedGreaterOrEqual: return F_Less;
                case F_LessOrEqual            : return F_UnorderedOrGreater;
                case F_UnorderedOrGreater     : return F_LessOrEqual;
                case F_Greater                : return F_UnorderedOrLessOrEqual;
                case F_UnorderedOrLessOrEqual : return F_Greater;
                case F_GreaterOrEqual         : return F_UnorderedOrLess;
                case F_UnorderedOrLess        : return F_GreaterOrEqual;
                case F_Unordered              : return F_Ordered;
                case F_Ordered                : return F_Unordered;
                case Never                    : return Always;
                case Always                   : return Never;
                case Equal                    : return NotEqual;
                case NotEqual                 : return Equal;
                case Zero                     : return NotZero;
                case NotZero                  : return Zero;
                case LessEqual                : return Greater;
                case Greater                  : return LessEqual;
                case Less                     : return GreaterEqual;
                case GreaterEqual             : return Less;
                case LessEqualUnsigned        : return GreaterUnsigned;
                case GreaterUnsigned          : return LessEqualUnsigned;
                case LessUnsigned             : return GreaterEqualUnsigned;
                case GreaterEqualUnsigned     : return LessUnsigned;
                case CarrySet                 : return CarryClear;
                case CarryClear               : return CarrySet;
                case Negative                 : return Positive;
                case Positive                 : return Negative;
                case OverflowSet              : return OverflowClear;
                case OverflowClear            : return OverflowSet;
                default:
                    GraalInternalError.unimplemented();
            }
            //@formatter:on
            return null;
        }

        public ConditionFlag mirror() {
            switch (this) {
            //@formatter:off
                case F_Less                   : return F_Greater;
                case F_Greater                : return F_Less;
                case F_LessOrEqual            : return F_GreaterOrEqual;
                case F_UnorderedGreaterOrEqual: return F_UnorderedOrLessOrEqual;
                case F_UnorderedOrGreater     : return F_UnorderedOrLess;
                case F_UnorderedOrLessOrEqual : return F_UnorderedGreaterOrEqual;
                case F_GreaterOrEqual         : return F_LessOrEqual;
                case F_UnorderedOrLess        : return F_UnorderedOrGreater;
                case LessEqual                : return GreaterEqual;
                case Greater                  : return Less;
                case Less                     : return Greater;
                case GreaterEqual             : return LessEqual;
                case LessEqualUnsigned        : return GreaterEqualUnsigned;
                case GreaterUnsigned          : return LessUnsigned;
                case LessUnsigned             : return GreaterUnsigned;
                case GreaterEqualUnsigned     : return LessEqualUnsigned;
                default:
                    return this;
                //@formatter:on
            }
        }

        public static ConditionFlag fromCondtition(CC conditionFlagsRegister, Condition cond, boolean unorderedIsTrue) {
            switch (conditionFlagsRegister) {
                case Xcc:
                case Icc:
                    switch (cond) {
                        case EQ:
                            return Equal;
                        case NE:
                            return NotEqual;
                        case BT:
                            return LessUnsigned;
                        case LT:
                            return Less;
                        case BE:
                            return LessEqualUnsigned;
                        case LE:
                            return LessEqual;
                        case AE:
                            return GreaterEqualUnsigned;
                        case GE:
                            return GreaterEqual;
                        case AT:
                            return GreaterUnsigned;
                        case GT:
                            return Greater;
                    }
                    throw GraalInternalError.shouldNotReachHere("Unimplemented for: " + cond);
                case Fcc0:
                case Fcc1:
                case Fcc2:
                case Fcc3:
                    switch (cond) {
                        case EQ:
                            return unorderedIsTrue ? F_UnorderedOrEqual : F_Equal;
                        case NE:
                            return ConditionFlag.F_NotEqual;
                        case LT:
                            return unorderedIsTrue ? F_UnorderedOrLess : F_Less;
                        case LE:
                            return unorderedIsTrue ? F_UnorderedOrLessOrEqual : F_LessOrEqual;
                        case GE:
                            return unorderedIsTrue ? F_UnorderedGreaterOrEqual : F_GreaterOrEqual;
                        case GT:
                            return unorderedIsTrue ? F_UnorderedOrGreater : F_Greater;
                    }
                    throw GraalInternalError.shouldNotReachHere("Unkown condition: " + cond);
            }
            throw GraalInternalError.shouldNotReachHere("Unknown condition flag register " + conditionFlagsRegister);
        }
    }

    public enum RCondition {
        // @formatter:off

        Rc_z(0b001, "rc_z"),
        Rc_lez(0b010, "rc_lez"),
        Rc_lz(0b011, "rc_lz"),
        Rc_nz(0b101, "rc_nz"),
        Rc_gz(0b110, "rc_gz"),
        Rc_gez(0b111, "rc_gez"),
        Rc_last(Rc_gez.getValue(), "rc_last");

        // @formatter:on

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

    /**
     * Represents the <b>Address Space Identifier</b> defined in the SPARC architecture.
     */
    public enum Asi {
        // @formatter:off

        INVALID(-1),
        ASI_PRIMARY(0x80),
        ASI_PRIMARY_NOFAULT(0x82),
        ASI_PRIMARY_LITTLE(0x88),
        // Block initializing store
        ASI_ST_BLKINIT_PRIMARY(0xE2),
        // Most-Recently-Used (MRU) BIS variant
        ASI_ST_BLKINIT_MRU_PRIMARY(0xF2);

        // @formatter:on

        private final int value;

        private Asi(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean isValid() {
            return value != INVALID.getValue();
        }
    }

    public boolean hasFeature(CPUFeature feature) {
        return ((SPARC) this.target.arch).features.contains(feature);
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

    public static final boolean isImm(int x, int nbits) {
        // assert_signed_range(x, nbits);
        return simm(x, nbits) == x;
    }

    /**
     * Minimum value for signed immediate ranges.
     */
    public static long minSimm(long nbits) {
        return -(1L << (nbits - 1));
    }

    /**
     * Maximum value for signed immediate ranges.
     */
    public static long maxSimm(long nbits) {
        return (1L << (nbits - 1)) - 1;
    }

    /**
     * Test if imm is within signed immediate range for nbits.
     */
    public static boolean isSimm(long imm, int nbits) {
        return minSimm(nbits) <= imm && imm <= maxSimm(nbits);
    }

    public static boolean isSimm10(long imm) {
        return isSimm(imm, 10);
    }

    public static boolean isSimm11(long imm) {
        return isSimm(imm, 11);
    }

    public static boolean isSimm11(JavaConstant constant) {
        return constant.isNull() || isSimm11(constant.asLong());
    }

    public static boolean isSimm5(JavaConstant constant) {
        return constant.isNull() || isSimm(constant.asLong(), 5);
    }

    public static boolean isSimm13(int imm) {
        return isSimm(imm, 13);
    }

    public static boolean isSimm13(long imm) {
        return NumUtil.isInt(imm) && isSimm(imm, 13);
    }

    public static boolean isDisp30(long imm) {
        return isSimm(imm, 30);
    }

    public static boolean isWordDisp30(long imm) {
        return isSimm(imm, 30 + 2);
    }

    public static final int hi22(int x) {
        return x >>> 10;
    }

    public static final int lo10(int x) {
        return x & ((1 << 10) - 1);
    }

    // @formatter:off
    /**
     * Instruction format for Fmt00 instructions. This abstraction is needed as it
     * makes the patching easier later on.
     * <pre>
     * | 00  |    a   | op2 |               b                         |
     * |31 30|29    25|24 22|21                                      0|
     * </pre>
     */
    // @formatter:on
    protected void fmt00(int a, int op2, int b) {
        assert isImm(a, 5) && isImm(op2, 3) && isImm(b, 22) : String.format("a: 0x%x op2: 0x%x b: 0x%x", a, op2, b);
        this.emitInt(a << 25 | op2 << 22 | b);
    }

    // @formatter:off
    /**
     * Branch on Integer Condition Codes.
     * <pre>
     * | 00  |annul| cond| 010 |               disp22                 |
     * |31 30|29   |28 25|24 22|21                                   0|
     * </pre>
     */
    // @formatter:on
    public void bicc(ConditionFlag cond, Annul annul, Label l) {
        bcc(Op2s.Br, cond, annul, l);
    }

    // @formatter:off
    /**
     * Branch on Floating-Point Condition Codes.
     * <pre>
     * | 00  |annul| cond| 110 |               disp22                 |
     * |31 30|29   |28 25|24 22|21                                   0|
     * </pre>
     */
    // @formatter:on
    public void fbcc(ConditionFlag cond, Annul annul, Label l) {
        bcc(Op2s.Fb, cond, annul, l);
    }

    // @formatter:off
    /**
     * Branch on (Integer|Floatingpoint) Condition Codes
     * <pre>
     * | 00  |annul| cond| op2 |               disp22                 |
     * |31 30|29   |28 25|24 22|21                                   0|
     * </pre>
     */
    // @formatter:on
    private void bcc(Op2s op2, ConditionFlag cond, Annul annul, Label l) {
        int pos = !l.isBound() ? patchUnbound(l) : (l.position() - position()) / 4;
        final int disp = 22;
        assert isSimm(pos, disp);
        pos &= (1 << disp) - 1;
        int a = (annul.flag << 4) | cond.getValue();
        fmt00(a, op2.getValue(), pos);
    }

    // @formatter:off
    /**
     * Branch on Integer Condition Codes with Prediction.
     * <pre>
     * | 00  |an|cond | 001 |cc1 2|p |           disp19               |
     * |31 30|29|28 25|24 22|21 20|19|                               0|
     * </pre>
     */
    // @formatter:on
    public void bpcc(ConditionFlag cond, Annul annul, Label l, CC cc, BranchPredict predictTaken) {
        bpcc(Op2s.Bp, cond, annul, l, cc, predictTaken);
    }

    // @formatter:off
    /**
     * Branch on Integer Condition Codes with Prediction.
     * <pre>
     * | 00  |an|cond | 101 |cc1 2|p |           disp19               |
     * |31 30|29|28 25|24 22|21 20|19|                               0|
     * </pre>
     */
    // @formatter:on
    public void fbpcc(ConditionFlag cond, Annul annul, Label l, CC cc, BranchPredict predictTaken) {
        bpcc(Op2s.Fbp, cond, annul, l, cc, predictTaken);
    }

    // @formatter:off
    /**
     * Used for fbpcc (Float) and bpcc (Integer)
     * <pre>
     * | 00  |an|cond | op2 |cc1 2|p |           disp19               |
     * |31 30|29|28 25|24 22|21 20|19|                               0|
     * </pre>
     */
    // @formatter:on
    private void bpcc(Op2s op2, ConditionFlag cond, Annul annul, Label l, CC cc, BranchPredict predictTaken) {
        int pos = !l.isBound() ? patchUnbound(l) : (l.position() - position()) / 4;
        final int disp = 19;
        assert isSimm(pos, disp);
        pos &= (1 << disp) - 1;
        int a = (annul.flag << 4) | cond.getValue();
        int b = (cc.getValue() << 20) | ((predictTaken.flag) << 19) | pos;
        fmt00(a, op2.getValue(), b);
    }

    // @formatter:off
    /**
     * Branch on Integer Register with Prediction.
     * <pre>
     * | 00  |an| 0|rcond | 011 |d16hi|p | rs1 |    d16lo             |
     * |31 30|29|28|27 25 |24 22|21 20|19|18 14|                     0|
     * </pre>
     */
    // @formatter:on
    public void bpr(RCondition cond, Annul annul, Label l, BranchPredict predictTaken, Register rs1) {
        int pos = !l.isBound() ? patchUnbound(l) : (l.position() - position()) / 4;
        final int disp = 16;
        assert isSimm(pos, disp);
        pos &= (1 << disp) - 1;
        int a = (annul.flag << 4) | cond.getValue();
        int d16hi = (pos >> 13) << 13;
        int d16lo = d16hi ^ pos;
        int b = (d16hi << 20) | (predictTaken.flag << 19) | (rs1.encoding() << 14) | d16lo;
        fmt00(a, Op2s.Bpr.getValue(), b);
    }

    // @formatter:off
    /**
     * NOP.
     * <pre>
     * | 00  |00000| 100 |                0                    |
     * |31 30|29 25|24 22|21                                  0|
     * </pre>
     */
    // @formatter:on
    public void nop() {
        emitInt(1 << 24);
    }

    private int patchUnbound(Label label) {
        label.addPatchAt(position());
        return 0;
    }

    public static class Add extends Fmt10 {

        public Add(Register src1, int simm13, Register dst) {
            super(Op3s.Add, src1, simm13, dst);
        }

        public Add(Register src1, Register src2, Register dst) {
            super(Op3s.Add, src1, src2, dst);
        }
    }

    public static class Addc extends Fmt10 {

        public Addc(Register src1, int simm13, Register dst) {
            super(Op3s.Addc, src1, simm13, dst);
        }

        public Addc(Register src1, Register src2, Register dst) {
            super(Op3s.Addc, src1, src2, dst);
        }
    }

    public static class Addcc extends Fmt10 {

        public Addcc(Register src1, int simm13, Register dst) {
            super(Op3s.Addcc, src1, simm13, dst);
        }

        public Addcc(Register src1, Register src2, Register dst) {
            super(Op3s.Addcc, src1, src2, dst);
        }
    }

    public static class Addccc extends Fmt10 {

        public Addccc(Register src1, int simm13, Register dst) {
            super(Op3s.Addccc, src1, simm13, dst);
        }

        public Addccc(Register src1, Register src2, Register dst) {
            super(Op3s.Addccc, src1, src2, dst);
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

    public static class Lzcnt extends Fmt3p {

        public Lzcnt(Register src1, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Lzcnt, g0, src1, dst);
        }
    }

    public static class And extends Fmt10 {

        public And(Register src1, int simm13, Register dst) {
            super(Op3s.And, src1, simm13, dst);
        }

        public And(Register src1, Register src2, Register dst) {
            super(Op3s.And, src1, src2, dst);
        }
    }

    public static class Andcc extends Fmt10 {

        public Andcc(Register src1, int simm13, Register dst) {
            super(Op3s.Andcc, src1, simm13, dst);
        }

        public Andcc(Register src1, Register src2, Register dst) {
            super(Op3s.Andcc, src1, src2, dst);
        }
    }

    public static class Andn extends Fmt10 {

        public Andn(Register src1, int simm13, Register dst) {
            super(Op3s.Andn, src1, simm13, dst);
        }

        public Andn(Register src1, Register src2, Register dst) {
            super(Op3s.Andn, src1, src2, dst);
        }
    }

    public static class Andncc extends Fmt10 {

        public Andncc(Register src1, int simm13, Register dst) {
            super(Op3s.Andncc, src1, simm13, dst);
        }

        public Andncc(Register src1, Register src2, Register dst) {
            super(Op3s.Andncc, src1, src2, dst);
        }
    }

    public static class Array8 extends Fmt3p {

        public Array8(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Array8, src1, src2, dst);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.VIS1);
            super.emit(masm);
        }
    }

    public static class Array16 extends Fmt3p {

        public Array16(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Array16, src1, src2, dst);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.VIS1);
            super.emit(masm);
        }
    }

    public static class Array32 extends Fmt3p {

        public Array32(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Array32, src1, src2, dst);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.VIS2);
            super.emit(masm);
        }
    }

    public static class Bmask extends Fmt3p {

        public Bmask(Register src1, Register src2, Register dst) {
            /* VIS2 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Bmask, src1, src2, dst);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.VIS2);
            super.emit(masm);
        }
    }

    public static class Movwtos extends Fmt3p {
        public Movwtos(Register src, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Movwtos, g0, src, dst);
            assert isSingleFloatRegister(dst);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.VIS3);
            super.emit(masm);
        }
    }

    public static class Umulxhi extends Fmt3p {
        public Umulxhi(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.UMulxhi, src1, src2, dst);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.VIS3);
            super.emit(masm);
        }
    }

    public static class Movxtod extends Fmt3p {
        public Movxtod(Register src, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Movxtod, g0, src, dst);
            assert isDoubleFloatRegister(dst);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.VIS3);
            super.emit(masm);
        }
    }

    public static class Movdtox extends Fmt3p {
        public Movdtox(Register src, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Movdtox, g0, src, dst);
            assert isDoubleFloatRegister(src);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.VIS3);
            super.emit(masm);
        }
    }

    public static class Movstosw extends Fmt3p {
        public Movstosw(Register src, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Movstosw, g0, src, dst);
            assert isSingleFloatRegister(src);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.VIS3);
            super.emit(masm);
        }
    }

    public static class Movstouw extends Fmt3p {
        public Movstouw(Register src, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Movstouw, g0, src, dst);
            assert isSingleFloatRegister(src);
        }

        @Override
        public void emit(SPARCAssembler masm) {
            assert masm.hasFeature(CPUFeature.VIS3);
            super.emit(masm);
        }
    }

    public static class Fdtos extends Fmt3p {
        public Fdtos(Register src, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fdtos, g0, src, dst);
            assert isSingleFloatRegister(dst);
            assert isDoubleFloatRegister(src);
        }
    }

    public static class Bshuffle extends Fmt3p {

        public Bshuffle(Register src1, Register src2, Register dst) {
            /* VIS2 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Bshuffle, src1, src2, dst);
        }
    }

    public static class Call extends Fmt01 {

        public Call(int disp30) {
            super(disp30);
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

    public static class Casa extends Fmt11 {

        public Casa(Register src1, Register src2, Register dst, Asi asi) {
            super(Op3s.Casa, src1, src2, dst, asi);
        }
    }

    public static class Casxa extends Fmt11 {

        public Casxa(Register src1, Register src2, Register dst, Asi asi) {
            super(Op3s.Casxa, src1, src2, dst, asi);
        }
    }

    public static class Cmask8 extends Fmt3n {

        public Cmask8(Register src2) {
            super(Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Cmask8.getValue(), src2.encoding(), 0);
        }
    }

    public static class Cmask16 extends Fmt3n {

        public Cmask16(Register src2) {
            super(Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Cmask16.getValue(), src2.encoding(), 0);
        }
    }

    public static class Cmask32 extends Fmt3n {

        public Cmask32(Register src2) {
            super(Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Cmask32.getValue(), src2.encoding(), 0);
        }
    }

    public static class Crc32c extends Fmt3p {

        public Crc32c(Register src1, Register src2, Register dst) {
            /* CRYPTO only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Crc32c, src1, src2, dst);
        }
    }

    public static class CBcondw extends Fmt00e {
        public CBcondw(ConditionFlag flag, Register src1, Register src2, Label label) {
            super(flag.getValue(), 0, src1.encoding(), -1, 0, src2.encoding(), label);
        }

        public CBcondw(ConditionFlag flag, Register src1, int simm5, Label label) {
            super(flag.getValue(), 0, src1.encoding(), -1, 1, simm5, label);
        }
    }

    public static class CBcondx extends Fmt00e {
        public CBcondx(ConditionFlag flag, Register src1, Register src2, Label label) {
            super(flag.getValue(), 1, src1.encoding(), -1, 0, src2.encoding(), label);
        }

        public CBcondx(ConditionFlag flag, Register src1, int simm5, Label label) {
            super(flag.getValue(), 1, src1.encoding(), -1, 1, simm5, label);
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

    /**
     * Floating-point multiply-add single (fused).
     */
    public static class Fmadds extends Fmt5a {

        public Fmadds(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmadds.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    /**
     * Floating-point multiply-add double (fused).
     */
    public static class Fmaddd extends Fmt5a {

        public Fmaddd(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmaddd.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
        }
    }

    /**
     * 16-bit partitioned average.
     */
    public static class Fmean16 extends Fmt3p {

        public Fmean16(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fmean16, src1, src2, dst);
        }
    }

    public static class Fmsubs extends Fmt5a {

        public Fmsubs(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmsubs.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
            assert isSingleFloatRegister(src1);
            assert isSingleFloatRegister(src2);
            assert isSingleFloatRegister(src3);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fmsubd extends Fmt5a {

        public Fmsubd(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fmsubd.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(src3);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fmovs extends Fmt3p {

        public Fmovs(Register src, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fmovs, g0, src, dst);
            assert isSingleFloatRegister(src);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fmovd extends Fmt3p {

        public Fmovd(Register src, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fmovd, g0, src, dst);
        }
    }

    public static class Fmuls extends Fmt3p {

        public Fmuls(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fmuls, src1, src2, dst);
            assert isSingleFloatRegister(src1);
            assert isSingleFloatRegister(src2);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fmuld extends Fmt3p {

        public Fmuld(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fmuld, src1, src2, dst);
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fsmuld extends Fmt3p {

        public Fsmuld(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fsmuld, src1, src2, dst);
            assert isSingleFloatRegister(src1);
            assert isSingleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
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
            assert isSingleFloatRegister(src1);
            assert isSingleFloatRegister(src2);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fnaddd extends Fmt3p {

        public Fnaddd(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnaddd, src1, src2, dst);
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fnegs extends Fmt3n {

        public Fnegs(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fnegs.getValue(), src2.encoding(), dst.encoding());
            assert isSingleFloatRegister(src2);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fnegd extends Fmt3n {

        public Fnegd(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fnegd.getValue(), src2.encoding(), dst.encoding());
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fnhadds extends Fmt3p {

        public Fnhadds(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnhadds, src1, src2, dst);
            assert isSingleFloatRegister(src1);
            assert isSingleFloatRegister(src2);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fnhaddd extends Fmt3p {

        public Fnhaddd(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnhaddd, src1, src2, dst);
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fnmadds extends Fmt5a {

        public Fnmadds(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmadds.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
            assert isSingleFloatRegister(src1);
            assert isSingleFloatRegister(src2);
            assert isSingleFloatRegister(src3);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fnmaddd extends Fmt5a {

        public Fnmaddd(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmaddd.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(src3);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fnmsubs extends Fmt5a {

        public Fnmsubs(SPARCAssembler masm, Register src1, Register src2, Register src3, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmsubs.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
            assert isSingleFloatRegister(src1);
            assert isSingleFloatRegister(src2);
            assert isSingleFloatRegister(src3);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fnmsubd extends Fmt5a {

        public Fnmsubd(SPARCAssembler masm, Register src1, Register src2, Register src3, Register dst) {
            super(masm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), Op5s.Fnmsubd.getValue(), src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(src3);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fnmuls extends Fmt3p {

        public Fnmuls(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnmuls, src1, src2, dst);
            assert isSingleFloatRegister(src1);
            assert isSingleFloatRegister(src2);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fnmuld extends Fmt3p {

        public Fnmuld(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnmuld, src1, src2, dst);
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fnsmuld extends Fmt3p {

        public Fnsmuld(Register src1, Register src2, Register dst) {
            /* VIS3 only */
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fnsmuld, src1, src2, dst);
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fstoi extends Fmt3n {

        public Fstoi(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fstoi.getValue(), src2.encoding(), dst.encoding());
            assert isSingleFloatRegister(dst);
            assert isSingleFloatRegister(src2);
        }
    }

    public static class Fstox extends Fmt3n {

        public Fstox(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fstox.getValue(), src2.encoding(), dst.encoding());
            assert isDoubleFloatRegister(dst);
            assert isSingleFloatRegister(src2);
        }
    }

    public static class Fdtox extends Fmt3n {

        public Fdtox(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fdtox.getValue(), src2.encoding(), dst.encoding());
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fstod extends Fmt3n {

        public Fstod(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fstod.getValue(), src2.encoding(), dst.encoding());
            assert isDoubleFloatRegister(dst);
            assert isSingleFloatRegister(src2);
        }
    }

    /**
     * Convert Double to 32-bit Integer.
     */
    public static class Fdtoi extends Fmt3n {

        public Fdtoi(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fdtoi.getValue(), src2.encoding(), dst.encoding());
            assert isDoubleFloatRegister(src2);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fitos extends Fmt3n {

        public Fitos(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fitos.getValue(), src2.encoding(), dst.encoding());
            assert isSingleFloatRegister(src2);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fitod extends Fmt3n {

        public Fitod(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fitod.getValue(), src2.encoding(), dst.encoding());
            assert isSingleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fxtos extends Fmt3n {

        public Fxtos(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fxtos.getValue(), src2.encoding(), dst.encoding());
            assert isDoubleFloatRegister(src2);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fxtod extends Fmt3n {

        public Fxtod(Register src2, Register dst) {
            super(Ops.ArithOp.getValue(), Op3s.Fpop1.getValue(), Opfs.Fxtod.getValue(), src2.encoding(), dst.encoding());
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
        }
    }

    /**
     * Flush register windows.
     */
    public static class Flushw extends Fmt10 {

        public Flushw() {
            super(Op3s.Flushw);
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
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(src3);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fpmaddxhi extends Fmt5a {

        public Fpmaddxhi(SPARCAssembler asm, Register src1, Register src2, Register src3, Register dst) {
            super(asm, Ops.ArithOp.getValue(), Op3s.Impdep2.getValue(), 4, src1.encoding(), src2.encoding(), src3.encoding(), dst.encoding());
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(src3);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fpmerge extends Fmt3p {

        public Fpmerge(Register src1, Register src2, Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fpmerge, src1, src2, dst);
            assert isSingleFloatRegister(src1);
            assert isSingleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
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

    public static class Fsqrts extends Fmt3p {

        public Fsqrts(Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fsqrts, SPARC.r0, src2, dst);
        }
    }

    public static class Fabss extends Fmt3p {
        public Fabss(Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fabss, SPARC.r0, src2, dst);
        }
    }

    public static class Fabsd extends Fmt3p {
        public Fabsd(Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fabsd, SPARC.r0, src2, dst);
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
            assert isSingleFloatRegister(src1);
            assert isSingleFloatRegister(src2);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fsubd extends Fmt3p {

        public Fsubd(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fsubd, src1, src2, dst);
            assert isDoubleFloatRegister(src1);
            assert isDoubleFloatRegister(src2);
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fsubq extends Fmt3p {

        public Fsubq(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Fpop1, Opfs.Fsubq, src1, src2, dst);
        }
    }

    public static class Fzeros extends Fmt3n {

        public Fzeros(Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Fzeros.getValue(), 0, dst.encoding());
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Fzerod extends Fmt3n {

        public Fzerod(Register dst) {
            /* VIS1 only */
            super(Ops.ArithOp.getValue(), Op3s.Impdep1.getValue(), Opfs.Fzerod.getValue(), 0, dst.encoding());
            assert isDoubleFloatRegister(dst);
        }
    }

    public static class Fcmp extends Fmt3c {

        public Fcmp(CC cc, Opfs opf, Register r1, Register r2) {
            super(Ops.ArithOp, cc, 0b110101, opf, r1, r2);
            assert opf != Opfs.Fcmpd || (isDoubleFloatRegister(r1) && isDoubleFloatRegister(r2));
            assert opf != Opfs.Fcmps || (isSingleFloatRegister(r1) && isSingleFloatRegister(r2));
        }
    }

    public static class Illtrap extends Fmt00a {

        public Illtrap(int const22) {
            super(Op2s.Illtrap, const22, g0);
        }
    }

    public static class Jmpl extends Fmt10 {

        public Jmpl(Register src, int simm13, Register dst) {
            super(Op3s.Jmpl, src, simm13, dst);
        }

        public Jmpl(Register src1, Register src2, Register dst) {
            super(Op3s.Jmpl, src1, src2, dst);
        }
    }

    public static class Lddf extends Fmt11 {

        public Lddf(SPARCAddress src, Register dst) {
            super(Op3s.Lddf, src, dst);
            assert dst == f0 || dst == f2 || dst == f4 || dst == f6 || isDoubleFloatRegister(dst);
        }

        public Lddf(Register src, Register dst) {
            super(Op3s.Lddf, src, dst);
            assert dst == f0 || dst == f2 || dst == f4 || dst == f6 || isDoubleFloatRegister(dst);
        }
    }

    public static class Ldf extends Fmt11 {

        public Ldf(SPARCAddress src, Register dst) {
            super(Op3s.Ldf, src, dst);
            assert isSingleFloatRegister(dst);
        }

        public Ldf(Register src, Register dst) {
            super(Op3s.Ldf, src, dst);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Ldsb extends Fmt11 {

        public Ldsb(SPARCAddress src, Register dst) {
            super(Op3s.Ldsb, src, dst);
        }
    }

    public static class Ldsh extends Fmt11 {

        public Ldsh(SPARCAddress src, Register dst) {
            super(Op3s.Ldsh, src, dst);
        }
    }

    public static class Lduh extends Fmt11 {

        public Lduh(SPARCAddress src, Register dst) {
            super(Op3s.Lduh, src, dst);
        }
    }

    public static class Ldub extends Fmt11 {

        public Ldub(SPARCAddress src, Register dst) {
            super(Op3s.Ldub, src, dst);
        }
    }

    public static class Ldsw extends Fmt11 {

        public Ldsw(SPARCAddress src, Register dst) {
            super(Op3s.Ldsw, src, dst);
        }
    }

    public static class Lduw extends Fmt11 {

        public Lduw(SPARCAddress src, Register dst) {
            super(Op3s.Lduw, src, dst);
        }
    }

    public static class Ldx extends Fmt11 {

        public Ldx(SPARCAddress src, Register dst) {
            super(Op3s.Ldx, src, dst);
        }
    }

    public static class Ldxa extends Fmt11 {

        public Ldxa(Register src1, Register src2, Register dst, Asi asi) {
            super(Op3s.Ldxa, src1, src2, dst, asi);
        }
    }

    public static class Lduwa extends Fmt11 {

        public Lduwa(Register src1, Register src2, Register dst, Asi asi) {
            super(Op3s.Lduwa, src1, src2, dst, asi);
        }
    }

    public static class Membar extends Fmt10 {

        public Membar(int barriers) {
            super(Op3s.Membar, r15, barriers, r0);
        }
    }

    public static class Fmovscc extends Fmt10d {

        public Fmovscc(ConditionFlag cond, CC cca, Register src2, Register dst) {
            super(Op3s.Fpop2, Opfs.Fmovscc, cond, cca, src2, dst);
        }
    }

    public static class Fmovdcc extends Fmt10d {

        public Fmovdcc(ConditionFlag cond, CC cca, Register src2, Register dst) {
            super(Op3s.Fpop2, Opfs.Fmovdcc, cond, cca, src2, dst);
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
    public static class Mulscc extends Fmt10 {

        @Deprecated
        public Mulscc(Register src1, int simm13, Register dst) {
            super(Op3s.Mulscc, src1, simm13, dst);
        }

        @Deprecated
        public Mulscc(Register src1, Register src2, Register dst) {
            super(Op3s.Mulscc, src1, src2, dst);
        }
    }

    public static class Mulx extends Fmt10 {

        public Mulx(Register src1, int simm13, Register dst) {
            super(Op3s.Mulx, src1, simm13, dst);
        }

        public Mulx(Register src1, Register src2, Register dst) {
            super(Op3s.Mulx, src1, src2, dst);
        }
    }

    public static class SMulcc extends Fmt10 {

        public SMulcc(Register src1, int simm13, Register dst) {
            super(Op3s.Smulcc, src1, simm13, dst);
        }

        public SMulcc(Register src1, Register src2, Register dst) {
            super(Op3s.Smulcc, src1, src2, dst);
        }
    }

    public static class Or extends Fmt10 {

        public Or(Register src1, int simm13, Register dst) {
            super(Op3s.Or, src1, simm13, dst);
        }

        public Or(Register src1, Register src2, Register dst) {
            super(Op3s.Or, src1, src2, dst);
        }
    }

    public static class Orcc extends Fmt10 {

        public Orcc(Register src1, int simm13, Register dst) {
            super(Op3s.Orcc, src1, simm13, dst);
        }

        public Orcc(Register src1, Register src2, Register dst) {
            super(Op3s.Orcc, src1, src2, dst);
        }
    }

    public static class Orn extends Fmt10 {

        public Orn(Register src1, int simm13, Register dst) {
            super(Op3s.Orn, src1, simm13, dst);
        }

        public Orn(Register src1, Register src2, Register dst) {
            super(Op3s.Orn, src1, src2, dst);
        }
    }

    public static class Orncc extends Fmt10 {

        public Orncc(Register src1, int simm13, Register dst) {
            super(Op3s.Orncc, src1, simm13, dst);
        }

        public Orncc(Register src1, Register src2, Register dst) {
            super(Op3s.Orncc, src1, src2, dst);
        }
    }

    public static class Popc extends Fmt10 {

        public Popc(int simm13, Register dst) {
            super(Op3s.Popc, r0, simm13, dst);
        }

        public Popc(Register src2, Register dst) {
            super(Op3s.Popc, r0, src2, dst);
        }
    }

    public static class Prefetch extends Fmt11 {

        public enum Fcn {
            SeveralWritesAndPossiblyReads(2),
            SeveralReadsWeak(0),
            OneRead(1),
            OneWrite(3),
            Page(4),
            NearestUnifiedCache(17),
            SeveralReadsStrong(20),
            OneReadStrong(21),
            SeveralWritesAndPossiblyReadsStrong(22),
            OneWriteStrong(23);

            private final int value;

            private Fcn(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }

        public Prefetch(SPARCAddress addr, Prefetch.Fcn fcn) {
            super(Op3s.Prefetch, addr, fcn);
        }
    }

    // A.44 Read State Register

    @Deprecated
    public static class Rdy extends Fmt10 {

        public Rdy(Register dst) {
            super(Op3s.Rdreg, r0, dst);
        }
    }

    public static class Rdccr extends Fmt10 {

        public Rdccr(Register dst) {
            super(Op3s.Rdreg, r2, dst);
        }
    }

    public static class Rdasi extends Fmt10 {

        public Rdasi(Register dst) {
            super(Op3s.Rdreg, r3, dst);
        }
    }

    public static class Rdtick extends Fmt10 {

        public Rdtick(Register dst) {
            super(Op3s.Rdreg, r4, dst);
        }
    }

    public static class Rdpc extends Fmt10 {

        public Rdpc(Register dst) {
            super(Op3s.Rdreg, r5, dst);
        }
    }

    public static class Rdfprs extends Fmt10 {

        public Rdfprs(Register dst) {
            super(Op3s.Rdreg, r6, dst);
        }
    }

    public static class Restore extends Fmt10 {

        public Restore(Register src1, Register src2, Register dst) {
            super(Op3s.Restore, src1, src2, dst);
        }
    }

    public static class Restored extends Fmt10 {

        public Restored() {
            super(Op3s.Saved, r0, r0, r1);
        }
    }

    public static class Return extends Fmt10 {

        public Return(Register src1, int simm13) {
            super(Op3s.Rett, src1, simm13, r0);
        }

        public Return(Register src1, Register src2) {
            super(Op3s.Rett, src1, src2, r0);
        }

        public static final int PC_RETURN_OFFSET = 8;
    }

    public static class Save extends Fmt10 {

        public Save(Register src1, Register src2, Register dst) {
            super(Op3s.Save, src1, src2, dst);
        }

        public Save(Register src1, int simm13, Register dst) {
            super(Op3s.Save, src1, simm13, dst);
        }
    }

    public static class Saved extends Fmt10 {

        public Saved() {
            super(Op3s.Saved, r0, r0, r0);
        }
    }

    @Deprecated
    public static class Sdiv extends Fmt10 {

        @Deprecated
        public Sdiv(Register src1, int simm13, Register dst) {
            super(Op3s.Sdiv, src1, simm13, dst);
        }

        @Deprecated
        public Sdiv(Register src1, Register src2, Register dst) {
            super(Op3s.Sdiv, src1, src2, dst);
        }
    }

    @Deprecated
    public static class Sdivcc extends Fmt10 {

        @Deprecated
        public Sdivcc(Register src1, int simm13, Register dst) {
            super(Op3s.Sdivcc, src1, simm13, dst);
        }

        @Deprecated
        public Sdivcc(Register src1, Register src2, Register dst) {
            super(Op3s.Sdivcc, src1, src2, dst);
        }
    }

    public static class Sdivx extends Fmt10 {

        public Sdivx(Register src1, int simm13, Register dst) {
            super(Op3s.Sdivx, src1, simm13, dst);
        }

        public Sdivx(Register src1, Register src2, Register dst) {
            super(Op3s.Sdivx, src1, src2, dst);
        }
    }

    public static class Sethi extends Fmt00a {

        public Sethi(int imm22, Register dst) {
            super(Op2s.Sethi, imm22, dst);
        }
    }

    public static class Sir extends Fmt10 {

        public Sir(int simm13) {
            super(Op3s.Sir, r0, simm13, r15);
        }
    }

    public static class Sll extends Fmt10 {

        public Sll(Register src1, int shcnt32, Register dst) {
            super(Op3s.Sll, src1, shcnt32, dst);
        }

        public Sll(Register src1, Register src2, Register dst) {
            super(Op3s.Sll, src1, src2, dst);
        }
    }

    public static class Sllx extends Fmt10 {

        public Sllx(Register src1, int shcnt64, Register dst) {
            super(Op3s.Sllx, src1, shcnt64, dst);
        }

        public Sllx(Register src1, Register src2, Register dst) {
            super(Op3s.Sllx, src1, src2, dst);
        }
    }

    public static class Sra extends Fmt10 {

        public Sra(Register src1, int shcnt32, Register dst) {
            super(Op3s.Sra, src1, shcnt32, dst);
        }

        public Sra(Register src1, Register src2, Register dst) {
            super(Op3s.Sra, src1, src2, dst);
        }
    }

    public static class Srax extends Fmt10 {

        public Srax(Register src1, int shcnt64, Register dst) {
            super(Op3s.Srax, src1, shcnt64, dst);
        }

        public Srax(Register src1, Register src2, Register dst) {
            super(Op3s.Srax, src1, src2, dst);
        }
    }

    public static class Srl extends Fmt10 {

        public Srl(Register src1, int shcnt32, Register dst) {
            super(Op3s.Srl, src1, shcnt32, dst);
        }

        public Srl(Register src1, Register src2, Register dst) {
            super(Op3s.Srl, src1, src2, dst);
        }
    }

    public static class Srlx extends Fmt10 {

        public Srlx(Register src1, int shcnt64, Register dst) {
            super(Op3s.Srlx, src1, shcnt64, dst);
        }

        public Srlx(Register src1, Register src2, Register dst) {
            super(Op3s.Srlx, src1, src2, dst);
        }
    }

    public static class Fandd extends Fmt3p {
        public Fandd(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fandd, src1, src2, dst);
        }
    }

    public static class Fxord extends Fmt3p {
        public Fxord(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fxord, src1, src2, dst);
        }
    }

    public static class Fxors extends Fmt3p {
        public Fxors(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fxors, src1, src2, dst);
        }
    }

    public static class Fands extends Fmt3p {
        public Fands(Register src1, Register src2, Register dst) {
            super(Ops.ArithOp, Op3s.Impdep1, Opfs.Fands, src1, src2, dst);
        }
    }

    public static class Stb extends Fmt11 {

        public Stb(Register dst, SPARCAddress addr) {
            super(Op3s.Stb, addr, dst);
        }
    }

    public static class Stdf extends Fmt11 {

        public Stdf(Register dst, SPARCAddress src) {
            super(Op3s.Stdf, src, dst);
        }
    }

    public static class Stf extends Fmt11 {

        public Stf(Register dst, SPARCAddress src) {
            super(Op3s.Stf, src, dst);
        }
    }

    public static class Sth extends Fmt11 {

        public Sth(Register dst, SPARCAddress addr) {
            super(Op3s.Sth, addr, dst);
        }
    }

    public static class Stw extends Fmt11 {

        public Stw(Register dst, SPARCAddress addr) {
            super(Op3s.Stw, addr, dst);
        }
    }

    public static class Stx extends Fmt11 {

        public Stx(Register dst, SPARCAddress addr) {
            super(Op3s.Stx, addr, dst);
        }
    }

    public static class Sub extends Fmt10 {

        public Sub(Register src1, int simm13, Register dst) {
            super(Op3s.Sub, src1, simm13, dst);
        }

        public Sub(Register src1, Register src2, Register dst) {
            super(Op3s.Sub, src1, src2, dst);
        }
    }

    public static class Subc extends Fmt10 {

        public Subc(Register src1, int simm13, Register dst) {
            super(Op3s.Subc, src1, simm13, dst);
        }

        public Subc(Register src1, Register src2, Register dst) {
            super(Op3s.Subc, src1, src2, dst);
        }
    }

    public static class Subcc extends Fmt10 {

        public Subcc(Register src1, int simm13, Register dst) {
            super(Op3s.Subcc, src1, simm13, dst);
        }

        public Subcc(Register src1, Register src2, Register dst) {
            super(Op3s.Subcc, src1, src2, dst);
        }
    }

    public static class Subccc extends Fmt10 {

        public Subccc(Register src1, int simm13, Register dst) {
            super(Op3s.Subccc, src1, simm13, dst);
        }

        public Subccc(Register src1, Register src2, Register dst) {
            super(Op3s.Subccc, src1, src2, dst);
        }
    }

    public static class Ta extends Fmt10 {

        public Ta(int trap) {
            super(Op3s.Trap, g0, trap, ConditionFlag.Always);
        }
    }

    public static class Tcc extends Fmt10 {

        public Tcc(ConditionFlag flag, int trap) {
            super(Op3s.Trap, g0, trap, flag);
        }
    }

    public static class Taddcc extends Fmt10 {

        public Taddcc(Register src1, int simm13, Register dst) {
            super(Op3s.Taddcc, src1, simm13, dst);
        }

        public Taddcc(Register src1, Register src2, Register dst) {
            super(Op3s.Taddcc, src1, src2, dst);
        }
    }

    public static class Tsubcc extends Fmt10 {

        public Tsubcc(Register src1, int simm13, Register dst) {
            super(Op3s.Tsubcc, src1, simm13, dst);
        }

        public Tsubcc(Register src1, Register src2, Register dst) {
            super(Op3s.Tsubcc, src1, src2, dst);
        }
    }

    public static class Udivx extends Fmt10 {

        public Udivx(Register src1, int simm13, Register dst) {
            super(Op3s.Udivx, src1, simm13, dst);
        }

        public Udivx(Register src1, Register src2, Register dst) {
            super(Op3s.Udivx, src1, src2, dst);
        }
    }

    @Deprecated
    public static class Wry extends Fmt10 {

        @Deprecated
        public Wry(Register src1, int simm13) {
            super(Op3s.Wrreg, src1, simm13, r0);
        }

        @Deprecated
        public Wry(Register src1, Register src2) {
            super(Op3s.Wrreg, src1, src2, r0);
        }
    }

    public static class Wrccr extends Fmt10 {

        public Wrccr(Register src1, int simm13) {
            super(Op3s.Wrreg, src1, simm13, r2);
        }

        public Wrccr(Register src1, Register src2) {
            super(Op3s.Wrreg, src1, src2, r2);
        }
    }

    public static class Wrasi extends Fmt10 {

        public Wrasi(Register src1, int simm13) {
            super(Op3s.Wrreg, src1, simm13, r3);
        }

        public Wrasi(Register src1, Register src2) {
            super(Op3s.Wrreg, src1, src2, r3);
        }
    }

    public static class Wrfprs extends Fmt10 {

        public Wrfprs(Register src1, int simm13) {
            super(Op3s.Wrreg, src1, simm13, r6);
        }

        public Wrfprs(Register src1, Register src2) {
            super(Op3s.Wrreg, src1, src2, r6);
        }
    }

    public static class Xor extends Fmt10 {

        public Xor(Register src1, int simm13, Register dst) {
            super(Op3s.Xor, src1, simm13, dst);
        }

        public Xor(Register src1, Register src2, Register dst) {
            super(Op3s.Xor, src1, src2, dst);
        }
    }

    public static class Xorcc extends Fmt10 {

        public Xorcc(Register src1, int simm13, Register dst) {
            super(Op3s.Xorcc, src1, simm13, dst);
        }

        public Xorcc(Register src1, Register src2, Register dst) {
            super(Op3s.Xorcc, src1, src2, dst);
        }
    }

    public static class Xnor extends Fmt10 {

        public Xnor(Register src1, int simm13, Register dst) {
            super(Op3s.Xnor, src1, simm13, dst);
        }

        public Xnor(Register src1, Register src2, Register dst) {
            super(Op3s.Xnor, src1, src2, dst);
        }
    }

    public static class Xnorcc extends Fmt10 {

        public Xnorcc(Register src1, int simm13, Register dst) {
            super(Op3s.Xnorcc, src1, simm13, dst);
        }

        public Xnorcc(Register src1, Register src2, Register dst) {
            super(Op3s.Xnorcc, src1, src2, dst);
        }
    }
}
