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

import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.sparc.*;
import com.oracle.graal.sparc.SPARC.CPUFeature;

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

    protected static final int A_MASK = 0b0010_0000_0000_0000_0000_0000_0000_0000;
    protected static final int OP_MASK = 0b1100_0000_0000_0000_0000_0000_0000_0000;
    protected static final int CBCOND_MASK = 0b0001_0000_0000_0000_0000_0000_0000_0000; // Used for
    // distinguish CBcond and BPr instructions
    protected static final int OP2_MASK = 0b0000_0001_1100_0000_0000_0000_0000_0000;

    protected static final int DISP22_SHIFT = 0;
    protected static final int DISP22_MASK = 0b00000000001111111111111111111111;

    protected static final int DISP19_SHIFT = 0;
    protected static final int DISP19_MASK = 0b00000000000001111111111111111111;

    protected static final int D16HI_SHIFT = 20;
    protected static final int D16HI_MASK = 0b0000_0000_0011_0000_0000_0000_0000_0000;
    protected static final int D16LO_SHIFT = 0;
    protected static final int D16LO_MASK = 0b0000_0000_0000_0000_0011_1111_1111_1111;

    protected static final int D10LO_MASK = 0b0000_0000_0000_0000_0001_1111_1110_0000;
    protected static final int D10HI_MASK = 0b0000_0000_0001_1000_0000_0000_0000_0000;
    protected static final int D10LO_SHIFT = 5;
    protected static final int D10HI_SHIFT = 19;

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
    public static class Fmt10d {

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

    public void add(Register rs1, Register rs2, Register rd) {
        op3(Add, rs1, rs2, rd);
    }

    public void add(Register rs1, int simm13, Register rd) {
        op3(Add, rs1, simm13, rd);
    }

    public void addc(Register rs1, Register rs2, Register rd) {
        op3(Addc, rs1, rs2, rd);
    }

    public void addc(Register rs1, int simm13, Register rd) {
        op3(Addc, rs1, simm13, rd);
    }

    public void addcc(Register rs1, Register rs2, Register rd) {
        op3(Addcc, rs1, rs2, rd);
    }

    public void addcc(Register rs1, int simm13, Register rd) {
        op3(Addcc, rs1, simm13, rd);
    }

    public void addccc(Register rs1, Register rs2, Register rd) {
        op3(Addccc, rs1, rs2, rd);
    }

    public void addccc(Register rs1, int simm13, Register rd) {
        op3(Addccc, rs1, simm13, rd);
    }

    public void and(Register rs1, Register rs2, Register rd) {
        op3(And, rs1, rs2, rd);
    }

    public void and(Register rs1, int simm13, Register rd) {
        op3(And, rs1, simm13, rd);
    }

    public void andcc(Register rs1, Register rs2, Register rd) {
        op3(Andcc, rs1, rs2, rd);
    }

    public void andcc(Register rs1, int simm13, Register rd) {
        op3(Andcc, rs1, simm13, rd);
    }

    public void andn(Register rs1, Register rs2, Register rd) {
        op3(Andn, rs1, rs2, rd);
    }

    public void andn(Register rs1, int simm13, Register rd) {
        op3(Andn, rs1, simm13, rd);
    }

    public void andncc(Register rs1, Register rs2, Register rd) {
        op3(Andncc, rs1, rs2, rd);
    }

    public void andncc(Register rs1, int simm13, Register rd) {
        op3(Andncc, rs1, simm13, rd);
    }

    public void movwtos(Register rs2, Register rd) {
        op3(Impdep1, Movwtos, null, rs2, rd);
    }

    public void umulxhi(Register rs1, Register rs2, Register rd) {
        op3(Impdep1, UMulxhi, rs1, rs2, rd);
    }

    public void fdtos(Register rs2, Register rd) {
        op3(Fpop1, Fdtos, null, rs2, rd);
    }

    public void movstouw(Register rs2, Register rd) {
        op3(Impdep1, Movstosw, null, rs2, rd);
    }

    public void movstosw(Register rs2, Register rd) {
        op3(Impdep1, Movstosw, null, rs2, rd);
    }

    public void movdtox(Register rs2, Register rd) {
        op3(Impdep1, Movdtox, null, rs2, rd);
    }

    public void movxtod(Register rs2, Register rd) {
        op3(Impdep1, Movxtod, null, rs2, rd);
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
    public void call(int disp30) {
        assert isImm(disp30, 30);
        int instr = 1 << 30;
        instr |= disp30;
        emitInt(instr);
    }

    public static class Casa extends Fmt11 {

        public Casa(Register src1, Register src2, Register dst, Asi asi) {
            super(Casa, src1, src2, dst, asi);
        }
    }

    public static class Casxa extends Fmt11 {

        public Casxa(Register src1, Register src2, Register dst, Asi asi) {
            super(Casxa, src1, src2, dst, asi);
        }
    }

    public void cbcondw(ConditionFlag cf, Register rs1, Register rs2, Label lab) {
        cbcond(0, 0, cf, rs1, rs2.encoding, lab);
    }

    public void cbcondw(ConditionFlag cf, Register rs1, int rs2, Label lab) {
        assert isSimm(rs2, 5);
        cbcond(0, 1, cf, rs1, rs2 & ((1 << 5) - 1), lab);
    }

    public void cbcondx(ConditionFlag cf, Register rs1, Register rs2, Label lab) {
        cbcond(1, 0, cf, rs1, rs2.encoding, lab);
    }

    public void cbcondx(ConditionFlag cf, Register rs1, int rs2, Label lab) {
        assert isSimm(rs2, 5);
        cbcond(1, 1, cf, rs1, rs2 & ((1 << 5) - 1), lab);
    }

    private void cbcond(int cc2, int i, ConditionFlag cf, Register rs1, int rs2, Label l) {
        int d10 = !l.isBound() ? patchUnbound(l) : (l.position() - position()) / 4;
        assert isSimm(d10, 10) && isImm(rs2, 5);
        d10 &= (1 << 10) - 1;
        final int c_lo = cf.value & 0b111;
        final int c_hi = cf.value >> 3;
        final int d10_lo = d10 & ((1 << 8) - 1);
        final int d10_hi = d10 >> 8;
        int a = c_hi << 4 | 0b1000 | c_lo;
        int b = cc2 << 21 | d10_hi << D10HI_SHIFT | rs1.encoding << 14 | i << 13 | d10_lo << D10LO_SHIFT | rs2;
        fmt00(a, Op2s.Bpr.value, b);
    }

    public void fadds(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fadds, rs1, rs2, rd);
    }

    public void faddd(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Faddd, rs1, rs2, rd);
    }

    public void faddq(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Faddq, rs1, rs2, rd);
    }

    public void fdivs(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fdivs, rs1, rs2, rd);
    }

    public void fdivd(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fdivd, rs1, rs2, rd);
    }

    public void fmovs(Register rs2, Register rd) {
        op3(Fpop1, Fmovs, null, rs2, rd);
    }

    public void fmovd(Register rs2, Register rd) {
        op3(Fpop1, Fmovd, null, rs2, rd);
    }

    public void fmuls(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fmuls, rs1, rs2, rd);
    }

    public void fsmuld(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fsmuld, rs1, rs2, rd);
    }

    public void fmuld(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fmuld, rs1, rs2, rd);
    }

    public void fnegs(Register rs2, Register rd) {
        op3(Fpop1, Fnegs, null, rs2, rd);
    }

    public void fnegd(Register rs2, Register rd) {
        op3(Fpop1, Fnegd, null, rs2, rd);
    }

    private void op3(Op3s op3, Opfs opf, Register rs1, Register rs2, Register rd) {
        int b = opf.value << 5 | (rs2 == null ? 0 : rs2.encoding);
        fmt10(rd.encoding, op3.value, rs1 == null ? 0 : rs1.encoding, b);
    }

    protected void op3(Op3s op3, Register rs1, Register rs2, Register rd) {
        int b = rs2 == null ? 0 : rs2.encoding;
        int xBit = getXBit(op3);
        fmt10(rd.encoding, op3.value, rs1 == null ? 0 : rs1.encoding, b | xBit);
    }

    protected void op3(Op3s op3, Register rs1, int simm13, Register rd) {
        assert isSimm13(simm13);
        int i = 1 << 13;
        int simm13WithX = simm13 | getXBit(op3);
        fmt10(rd.encoding, op3.value, rs1.encoding, i | simm13WithX & ((1 << 13) - 1));
    }

    /**
     * Helper method to determine if the instruction needs the X bit set.
     */
    private static int getXBit(Op3s op3) {
        switch (op3) {
            case Sllx:
            case Srax:
            case Srlx:
                return 1 << 12;
            default:
                return 0;
        }
    }

    public void fstoi(Register rs2, Register rd) {
        op3(Fpop1, Fstoi, null, rs2, rd);
    }

    public void fstox(Register rs2, Register rd) {
        op3(Fpop1, Fstox, null, rs2, rd);
    }

    public void fdtox(Register rs2, Register rd) {
        op3(Fpop1, Fdtox, null, rs2, rd);
    }

    public void fstod(Register rs2, Register rd) {
        op3(Fpop1, Fstod, null, rs2, rd);
    }

    public void fdtoi(Register rs2, Register rd) {
        op3(Fpop1, Fdtoi, null, rs2, rd);
    }

    public void fitos(Register rs2, Register rd) {
        op3(Fpop1, Fitos, null, rs2, rd);
    }

    public void fitod(Register rs2, Register rd) {
        op3(Fpop1, Fitod, null, rs2, rd);
    }

    public void fxtos(Register rs2, Register rd) {
        op3(Fpop1, Fxtos, null, rs2, rd);
    }

    public void fxtod(Register rs2, Register rd) {
        op3(Fpop1, Fxtod, null, rs2, rd);
    }

    public void fzeros(Register rd) {
        op3(Impdep1, Fzeros, null, null, rd);
    }

    public void fzerod(Register rd) {
        op3(Impdep1, Fzerod, null, null, rd);
    }

    public void flushw() {
        op3(Flushw, g0, g0, g0);
    }

    public void fsqrtd(Register rs2, Register rd) {
        op3(Fpop1, Fsqrtd, null, rs2, rd);
    }

    public void fsqrts(Register rs2, Register rd) {
        op3(Fpop1, Fsqrts, null, rs2, rd);
    }

    public void fabss(Register rs2, Register rd) {
        op3(Fpop1, Fabss, null, rs2, rd);
    }

    public void fabsd(Register rs2, Register rd) {
        op3(Fpop1, Fabsd, null, rs2, rd);
    }

    public void fsubs(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fsubs, rs1, rs2, rd);
    }

    public void fsubd(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fsubd, rs1, rs2, rd);
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
    public void fcmp(CC cc, Opfs opf, Register rs1, Register rs2) {
        int a = cc.value;
        int b = opf.value << 5 | rs2.encoding;
        fmt10(a, Fcmp.value, rs1.encoding, b);
    }

    // @formatter:off
    /**
     * Instruction format for most arithmetic stuff.
     * <pre>
     * |  10 | rd  | op3 | rs1 |   b   |
     * |31 30|29 25|24 19|18 14|13    0|
     * </pre>
     */
    // @formatter:on
    protected void fmt10(int rd, int op3, int rs1, int b) {
        assert isImm(rd, 5) && isImm(op3, 6) && isImm(b, 14) : String.format("rd: 0x%x op3: 0x%x b: 0x%x", rd, op3, b);
        int instr = 1 << 31 | rd << 25 | op3 << 19 | rs1 << 14 | b;
        emitInt(instr);
    }

    public void illtrap(int const22) {
        fmt00(0, Op2s.Illtrap.value, const22);
    }

    public void jmpl(Register rs1, Register rs2, Register rd) {
        op3(Jmpl, rs1, rs2, rd);
    }

    public void jmpl(Register rs1, int simm13, Register rd) {
        op3(Jmpl, rs1, simm13, rd);
    }

    public static class Lddf extends Fmt11 {

        public Lddf(SPARCAddress src, Register dst) {
            super(Lddf, src, dst);
            assert dst == f0 || dst == f2 || dst == f4 || dst == f6 || isDoubleFloatRegister(dst);
        }

        public Lddf(Register src, Register dst) {
            super(Lddf, src, dst);
            assert dst == f0 || dst == f2 || dst == f4 || dst == f6 || isDoubleFloatRegister(dst);
        }
    }

    public static class Ldf extends Fmt11 {

        public Ldf(SPARCAddress src, Register dst) {
            super(Ldf, src, dst);
            assert isSingleFloatRegister(dst);
        }

        public Ldf(Register src, Register dst) {
            super(Ldf, src, dst);
            assert isSingleFloatRegister(dst);
        }
    }

    public static class Ldsb extends Fmt11 {

        public Ldsb(SPARCAddress src, Register dst) {
            super(Ldsb, src, dst);
        }
    }

    public static class Ldsh extends Fmt11 {

        public Ldsh(SPARCAddress src, Register dst) {
            super(Ldsh, src, dst);
        }
    }

    public static class Lduh extends Fmt11 {

        public Lduh(SPARCAddress src, Register dst) {
            super(Lduh, src, dst);
        }
    }

    public static class Ldub extends Fmt11 {

        public Ldub(SPARCAddress src, Register dst) {
            super(Ldub, src, dst);
        }
    }

    public static class Ldsw extends Fmt11 {

        public Ldsw(SPARCAddress src, Register dst) {
            super(Ldsw, src, dst);
        }
    }

    public static class Lduw extends Fmt11 {

        public Lduw(SPARCAddress src, Register dst) {
            super(Lduw, src, dst);
        }
    }

    public static class Ldx extends Fmt11 {

        public Ldx(SPARCAddress src, Register dst) {
            super(Ldx, src, dst);
        }
    }

    public static class Ldxa extends Fmt11 {

        public Ldxa(Register src1, Register src2, Register dst, Asi asi) {
            super(Ldxa, src1, src2, dst, asi);
        }
    }

    public static class Lduwa extends Fmt11 {

        public Lduwa(Register src1, Register src2, Register dst, Asi asi) {
            super(Lduwa, src1, src2, dst, asi);
        }
    }

    public void membar(int barriers) {
        op3(Membar, r15, barriers, g0);
    }

    public static class Fmovscc extends Fmt10d {

        public Fmovscc(ConditionFlag cond, CC cca, Register src2, Register dst) {
            super(Fpop2, Fmovscc, cond, cca, src2, dst);
        }
    }

    public static class Fmovdcc extends Fmt10d {

        public Fmovdcc(ConditionFlag cond, CC cca, Register src2, Register dst) {
            super(Fpop2, Fmovdcc, cond, cca, src2, dst);
        }
    }

    public static class Movcc extends Fmt10c {

        public Movcc(ConditionFlag cond, CC cca, Register src2, Register dst) {
            super(Movcc, cond, cca, src2, dst);
        }

        public Movcc(ConditionFlag cond, CC cca, int simm11, Register dst) {
            super(Movcc, cond, cca, simm11, dst);
        }
    }

    public void mulx(Register rs1, Register rs2, Register rd) {
        op3(Mulx, rs1, rs2, rd);
    }

    public void mulx(Register rs1, int simm13, Register rd) {
        op3(Mulx, rs1, simm13, rd);
    }

    public void or(Register rs1, Register rs2, Register rd) {
        op3(Or, rs1, rs2, rd);
    }

    public void or(Register rs1, int simm13, Register rd) {
        op3(Or, rs1, simm13, rd);
    }

    public void popc(Register rs2, Register rd) {
        op3(Popc, g0, rs2, rd);
    }

    public void popc(int simm13, Register rd) {
        op3(Popc, g0, simm13, rd);
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
            super(Prefetch, addr, fcn);
        }
    }

    // A.44 Read State Register

    public void rdpc(Register rd) {
        op3(Rdreg, r5, g0, rd);
    }

    public void restore(Register rs1, Register rs2, Register rd) {
        op3(Restore, rs1, rs2, rd);
    }

    public static final int PC_RETURN_OFFSET = 8;

    public void save(Register rs1, Register rs2, Register rd) {
        op3(Save, rs1, rs2, rd);
    }

    public void save(Register rs1, int simm13, Register rd) {
        op3(Save, rs1, simm13, rd);
    }

    public void sdivx(Register rs1, Register rs2, Register rd) {
        op3(Sdivx, rs1, rs2, rd);
    }

    public void sdivx(Register rs1, int simm13, Register rd) {
        op3(Sdivx, rs1, simm13, rd);
    }

    public void sethi(int imm22, Register dst) {
        fmt00(dst.encoding, Op2s.Sethi.value, imm22);
    }

    public void sll(Register rs1, Register rs2, Register rd) {
        op3(Sll, rs1, rs2, rd);
    }

    public void sll(Register rs1, int shcnt32, Register rd) {
        assert isImm(shcnt32, 5);
        op3(Sll, rs1, shcnt32, rd);
    }

    public void sllx(Register rs1, Register rs2, Register rd) {
        op3(Sllx, rs1, rs2, rd);
    }

    public void sllx(Register rs1, int shcnt64, Register rd) {
        assert isImm(shcnt64, 6);
        op3(Sllx, rs1, shcnt64, rd);
    }

    public void sra(Register rs1, Register rs2, Register rd) {
        op3(Sra, rs1, rs2, rd);
    }

    public void sra(Register rs1, int simm13, Register rd) {
        op3(Sra, rs1, simm13, rd);
    }

    public void srax(Register rs1, Register rs2, Register rd) {
        op3(Srax, rs1, rs2, rd);
    }

    public void srax(Register rs1, int shcnt64, Register rd) {
        assert isImm(shcnt64, 6);
        op3(Srax, rs1, shcnt64, rd);
    }

    public void srl(Register rs1, Register rs2, Register rd) {
        op3(Srl, rs1, rs2, rd);
    }

    public void srl(Register rs1, int simm13, Register rd) {
        op3(Srl, rs1, simm13, rd);
    }

    public void srlx(Register rs1, Register rs2, Register rd) {
        op3(Srlx, rs1, rs2, rd);
    }

    public void srlx(Register rs1, int shcnt64, Register rd) {
        assert isImm(shcnt64, 6);
        op3(Srlx, rs1, shcnt64, rd);
    }

    public void fandd(Register rs1, Register rs2, Register rd) {
        op3(Impdep1, Fandd, rs1, rs2, rd);
    }

    public static class Stb extends Fmt11 {

        public Stb(Register dst, SPARCAddress addr) {
            super(Stb, addr, dst);
        }
    }

    public static class Stdf extends Fmt11 {

        public Stdf(Register dst, SPARCAddress src) {
            super(Stdf, src, dst);
        }
    }

    public static class Stf extends Fmt11 {

        public Stf(Register dst, SPARCAddress src) {
            super(Stf, src, dst);
        }
    }

    public static class Sth extends Fmt11 {

        public Sth(Register dst, SPARCAddress addr) {
            super(Sth, addr, dst);
        }
    }

    public static class Stw extends Fmt11 {

        public Stw(Register dst, SPARCAddress addr) {
            super(Stw, addr, dst);
        }
    }

    public static class Stx extends Fmt11 {

        public Stx(Register dst, SPARCAddress addr) {
            super(Stx, addr, dst);
        }
    }

    public void sub(Register rs1, Register rs2, Register rd) {
        op3(Sub, rs1, rs2, rd);
    }

    public void sub(Register rs1, int simm13, Register rd) {
        op3(Sub, rs1, simm13, rd);
    }

    public void subc(Register rs1, Register rs2, Register rd) {
        op3(Subc, rs1, rs2, rd);
    }

    public void subc(Register rs1, int simm13, Register rd) {
        op3(Subc, rs1, simm13, rd);
    }

    public void subcc(Register rs1, Register rs2, Register rd) {
        op3(Subcc, rs1, rs2, rd);
    }

    public void subcc(Register rs1, int simm13, Register rd) {
        op3(Subcc, rs1, simm13, rd);
    }

    public void subccc(Register rs1, Register rs2, Register rd) {
        op3(Subccc, rs1, rs2, rd);
    }

    public void subccc(Register rs1, int simm13, Register rd) {
        op3(Subccc, rs1, simm13, rd);
    }

    public void ta(int trap) {
        tcc(Icc, Always, trap);
    }

    public void tcc(CC cc, ConditionFlag flag, int trap) {
        assert isImm(trap, 8);
        int b = cc.value << 11;
        b |= trap;
        fmt10(flag.value, trap, 0, b);
    }

    public void udivx(Register rs1, Register rs2, Register rd) {
        op3(Udivx, rs1, rs2, rd);
    }

    public void udivx(Register rs1, int simm13, Register rd) {
        op3(Udivx, rs1, simm13, rd);
    }

    public void wrccr(Register rs1, Register rs2) {
        op3(Wrreg, rs1, rs2, r2);
    }

    public void wrccr(Register rs1, int simm13) {
        op3(Wrreg, rs1, simm13, r2);
    }

    public void xor(Register rs1, Register rs2, Register rd) {
        op3(Xor, rs1, rs2, rd);
    }

    public void xor(Register rs1, int simm13, Register rd) {
        op3(Xor, rs1, simm13, rd);
    }

    public void xorcc(Register rs1, Register rs2, Register rd) {
        op3(Xorcc, rs1, rs2, rd);
    }

    public void xorcc(Register rs1, int simm13, Register rd) {
        op3(Xorcc, rs1, simm13, rd);
    }

    public void xnor(Register rs1, Register rs2, Register rd) {
        op3(Xnor, rs1, rs2, rd);
    }

    public void xnor(Register rs1, int simm13, Register rd) {
        op3(Xnor, rs1, simm13, rd);
    }

    public void xnorcc(Register rs1, Register rs2, Register rd) {
        op3(Xnorcc, rs1, rs2, rd);
    }

    public void xnorcc(Register rs1, int simm13, Register rd) {
        op3(Xnorcc, rs1, simm13, rd);
    }
}
