/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.asm.aarch64;

import static com.oracle.graal.asm.aarch64.AArch64Assembler.InstructionType.floatFromSize;
import static com.oracle.graal.asm.aarch64.AArch64Assembler.InstructionType.generalFromSize;
import static jdk.vm.ci.aarch64.AArch64.CPU;
import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;

import java.util.Arrays;

import com.oracle.graal.asm.Assembler;
import com.oracle.graal.asm.NumUtil;
import com.oracle.graal.asm.aarch64.AArch64Address.AddressingMode;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;

public abstract class AArch64Assembler extends Assembler {

    public static class LogicalImmediateTable {

        private static final Immediate[] IMMEDIATE_TABLE = buildImmediateTable();

        private static final int ImmediateOffset = 10;
        private static final int ImmediateRotateOffset = 16;
        private static final int ImmediateSizeOffset = 22;

        /**
         * Specifies whether immediate can be represented in all cases (YES), as a 64bit instruction
         * (SIXTY_FOUR_BIT_ONLY) or not at all (NO).
         */
        static enum Representable {
            YES,
            SIXTY_FOUR_BIT_ONLY,
            NO
        }

        /**
         * Tests whether an immediate can be encoded for logical instructions.
         *
         * @param is64bit if true immediate is considered a 64-bit pattern. If false we may use a
         *            64-bit instruction to load the 32-bit pattern into a register.
         * @return enum specifying whether immediate can be used for 32- and 64-bit logical
         *         instructions ({@code #Representable.YES}), for 64-bit instructions only (
         *         {@code #Representable.SIXTY_FOUR_BIT_ONLY}) or not at all (
         *         {@code #Representable.NO} ).
         */
        public static Representable isRepresentable(boolean is64bit, long immediate) {
            int pos = getLogicalImmTablePos(is64bit, immediate);
            if (pos < 0) {
                // if 32bit instruction we can try again as 64bit immediate which may succeed.
                // i.e. 0xffffffff fails as a 32bit immediate but works as 64bit one.
                if (!is64bit) {
                    assert NumUtil.isUnsignedNbit(32, immediate);
                    pos = getLogicalImmTablePos(true, immediate);
                    return pos >= 0 ? Representable.SIXTY_FOUR_BIT_ONLY : Representable.NO;
                }
                return Representable.NO;
            }
            Immediate imm = IMMEDIATE_TABLE[pos];
            return imm.only64bit() ? Representable.SIXTY_FOUR_BIT_ONLY : Representable.YES;
        }

        public static Representable isRepresentable(int immediate) {
            return isRepresentable(false, immediate & 0xFFFF_FFFFL);
        }

        public static int getLogicalImmEncoding(boolean is64bit, long value) {
            int pos = getLogicalImmTablePos(is64bit, value);
            assert pos >= 0 : "Value cannot be represented as logical immediate";
            Immediate imm = IMMEDIATE_TABLE[pos];
            assert is64bit || !imm.only64bit() : "Immediate can only be represented for 64bit, but 32bit instruction specified";
            return IMMEDIATE_TABLE[pos].encoding;
        }

        /**
         * @param is64bit if true also allow 64-bit only encodings to be returned.
         * @return If positive the return value is the position into the IMMEDIATE_TABLE for the
         *         given immediate, if negative the immediate cannot be encoded.
         */
        private static int getLogicalImmTablePos(boolean is64bit, long value) {
            Immediate imm;
            if (!is64bit) {
                // 32bit instructions can only have 32bit immediates.
                if (!NumUtil.isUnsignedNbit(32, value)) {
                    return -1;
                }
                // If we have a 32bit instruction (and therefore immediate) we have to duplicate it
                // across 64bit to find it in the table.
                imm = new Immediate(value << 32 | value);
            } else {
                imm = new Immediate(value);
            }
            int pos = Arrays.binarySearch(IMMEDIATE_TABLE, imm);
            if (pos < 0) {
                return -1;
            }
            if (!is64bit && IMMEDIATE_TABLE[pos].only64bit()) {
                return -1;
            }
            return pos;
        }

        /**
         * To quote 5.4.2: [..] an immediate is a 32 or 64 bit pattern viewed as a vector of
         * identical elements of size e = 2, 4, 8, 16, 32 or (in the case of bimm64) 64 bits. Each
         * element contains the same sub-pattern: a single run of 1 to e-1 non-zero bits, rotated by
         * 0 to e-1 bits. It is encoded in the following: 10-16: rotation amount (6bit) starting
         * from 1s in the LSB (i.e. 0111->1011->1101->1110) 16-22: This stores a combination of the
         * number of set bits and the pattern size. The pattern size is encoded as follows (x is
         * used to store the number of 1 bits - 1) e pattern 2 1111xx 4 1110xx 8 110xxx 16 10xxxx 32
         * 0xxxxx 64 xxxxxx 22: if set we have an instruction with 64bit pattern?
         */
        private static final class Immediate implements Comparable<Immediate> {
            public final long imm;
            public final int encoding;

            public Immediate(long imm, boolean is64, int s, int r) {
                this.imm = imm;
                this.encoding = computeEncoding(is64, s, r);
            }

            // Used to be able to binary search for an immediate in the table.
            public Immediate(long imm) {
                this(imm, false, 0, 0);
            }

            /**
             * Returns true if this pattern is only representable as 64bit.
             */
            public boolean only64bit() {
                return (encoding & (1 << ImmediateSizeOffset)) != 0;
            }

            private static int computeEncoding(boolean is64, int s, int r) {
                int sf = is64 ? 1 : 0;
                return sf << ImmediateSizeOffset | r << ImmediateRotateOffset | s << ImmediateOffset;
            }

            @Override
            public int compareTo(Immediate o) {
                return Long.compare(imm, o.imm);
            }
        }

        private static Immediate[] buildImmediateTable() {
            final int nrImmediates = 5334;
            final Immediate[] table = new Immediate[nrImmediates];
            int nrImms = 0;
            for (int logE = 1; logE <= 6; logE++) {
                int e = 1 << logE;
                long mask = NumUtil.getNbitNumberLong(e);
                for (int nrOnes = 1; nrOnes < e; nrOnes++) {
                    long val = (1L << nrOnes) - 1;
                    // r specifies how much we rotate the value
                    for (int r = 0; r < e; r++) {
                        long immediate = (val >>> r | val << (e - r)) & mask;
                        // Duplicate pattern to fill whole 64bit range.
                        switch (logE) {
                            case 1:
                                immediate |= immediate << 2;
                                immediate |= immediate << 4;
                                immediate |= immediate << 8;
                                immediate |= immediate << 16;
                                immediate |= immediate << 32;
                                break;
                            case 2:
                                immediate |= immediate << 4;
                                immediate |= immediate << 8;
                                immediate |= immediate << 16;
                                immediate |= immediate << 32;
                                break;
                            case 3:
                                immediate |= immediate << 8;
                                immediate |= immediate << 16;
                                immediate |= immediate << 32;
                                break;
                            case 4:
                                immediate |= immediate << 16;
                                immediate |= immediate << 32;
                                break;
                            case 5:
                                immediate |= immediate << 32;
                                break;
                        }
                        // 5 - logE can underflow to -1, but we shift this bogus result
                        // out of the masked area.
                        int sizeEncoding = (1 << (5 - logE)) - 1;
                        int s = ((sizeEncoding << (logE + 1)) & 0x3f) | (nrOnes - 1);
                        table[nrImms++] = new Immediate(immediate, /* is64bit */e == 64, s, r);
                    }
                }
            }
            Arrays.sort(table);
            assert nrImms == nrImmediates : nrImms + " instead of " + nrImmediates + " in table.";
            assert checkDuplicates(table) : "Duplicate values in table.";
            return table;
        }

        private static boolean checkDuplicates(Immediate[] table) {
            for (int i = 0; i < table.length - 1; i++) {
                if (table[i].imm >= table[i + 1].imm) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final int RdOffset = 0;
    private static final int Rs1Offset = 5;
    private static final int Rs2Offset = 16;
    private static final int Rs3Offset = 10;
    private static final int RtOffset = 0;

    /**
     * Enumeration of all different instruction kinds: General32/64 are the general instructions
     * (integer, branch, etc.), for 32-, respectively 64-bit operands. FP32/64 is the encoding for
     * the 32/64bit float operations
     */
    protected enum InstructionType {
        General32(0x00000000, 32, true),
        General64(0x80000000, 64, true),
        FP32(0x00000000, 32, false),
        FP64(0x00400000, 64, false);

        public final int encoding;
        public final int width;
        public final boolean isGeneral;

        private InstructionType(int encoding, int width, boolean isGeneral) {
            this.encoding = encoding;
            this.width = width;
            this.isGeneral = isGeneral;
        }

        public static InstructionType generalFromSize(int size) {
            assert size == 32 || size == 64;
            return size == 32 ? General32 : General64;
        }

        public static InstructionType floatFromSize(int size) {
            assert size == 32 || size == 64;
            return size == 32 ? FP32 : FP64;
        }

    }

    private static final int ImmediateOffset = 10;
    private static final int ImmediateRotateOffset = 16;
    private static final int ImmediateSizeOffset = 22;
    private static final int ExtendTypeOffset = 13;

    private static final int AddSubImmOp = 0x11000000;
    // If 1 the immediate is interpreted as being left-shifted by 12 bits.
    private static final int AddSubShiftOffset = 22;
    private static final int AddSubSetFlag = 0x20000000;

    private static final int LogicalImmOp = 0x12000000;

    private static final int MoveWideImmOp = 0x12800000;
    private static final int MoveWideImmOffset = 5;
    private static final int MoveWideShiftOffset = 21;

    private static final int BitfieldImmOp = 0x13000000;

    private static final int AddSubShiftedOp = 0x0B000000;
    private static final int ShiftTypeOffset = 22;

    private static final int AddSubExtendedOp = 0x0B200000;

    private static final int MulOp = 0x1B000000;
    private static final int DataProcessing1SourceOp = 0x5AC00000;
    private static final int DataProcessing2SourceOp = 0x1AC00000;

    private static final int Fp1SourceOp = 0x1E204000;
    private static final int Fp2SourceOp = 0x1E200800;
    private static final int Fp3SourceOp = 0x1F000000;

    private static final int FpConvertOp = 0x1E200000;
    private static final int FpImmOp = 0x1E201000;
    private static final int FpImmOffset = 13;

    private static final int FpCmpOp = 0x1E202000;

    private static final int PcRelImmHiOffset = 5;
    private static final int PcRelImmLoOffset = 29;

    private static final int PcRelImmOp = 0x10000000;

    private static final int UnconditionalBranchImmOp = 0x14000000;
    private static final int UnconditionalBranchRegOp = 0xD6000000;
    private static final int CompareBranchOp = 0x34000000;

    private static final int ConditionalBranchImmOffset = 5;

    private static final int ConditionalSelectOp = 0x1A800000;
    private static final int ConditionalConditionOffset = 12;

    private static final int LoadStoreScaledOp = 0x39000000;
    private static final int LoadStoreUnscaledOp = 0x38000000;
    private static final int LoadStoreRegisterOp = 0x38200800;
    private static final int LoadLiteralOp = 0x18000000;
    private static final int LoadStorePostIndexedOp = 0x38000400;
    private static final int LoadStorePreIndexedOp = 0x38000C00;

    private static final int LoadStoreUnscaledImmOffset = 12;
    private static final int LoadStoreScaledImmOffset = 10;
    private static final int LoadStoreScaledRegOffset = 12;
    private static final int LoadStoreIndexedImmOffset = 12;
    private static final int LoadStoreTransferSizeOffset = 30;
    private static final int LoadStoreFpFlagOffset = 26;
    private static final int LoadLiteralImmeOffset = 5;

    private static final int LogicalShiftOp = 0x0A000000;

    private static final int ExceptionOp = 0xD4000000;
    private static final int SystemImmediateOffset = 5;

    @SuppressWarnings("unused") private static final int SimdImmediateOffset = 16;

    private static final int BarrierOp = 0xD503301F;
    private static final int BarrierKindOffset = 8;

    /**
     * Encoding for all instructions.
     */
    private enum Instruction {
        BCOND(0x54000000),
        CBNZ(0x01000000),
        CBZ(0x00000000),

        B(0x00000000),
        BL(0x80000000),
        BR(0x001F0000),
        BLR(0x003F0000),
        RET(0x005F0000),

        LDR(0x00000000),
        LDRS(0x00800000),
        LDXR(0x081f7c00),
        LDAR(0x8dffc00),
        LDAXR(0x85ffc00),

        STR(0x00000000),
        STXR(0x08007c00),
        STLR(0x089ffc00),
        STLXR(0x0800fc00),

        ADR(0x00000000),
        ADRP(0x80000000),

        ADD(0x00000000),
        ADDS(ADD.encoding | AddSubSetFlag),
        SUB(0x40000000),
        SUBS(SUB.encoding | AddSubSetFlag),

        NOT(0x00200000),
        AND(0x00000000),
        BIC(AND.encoding | NOT.encoding),
        ORR(0x20000000),
        ORN(ORR.encoding | NOT.encoding),
        EOR(0x40000000),
        EON(EOR.encoding | NOT.encoding),
        ANDS(0x60000000),
        BICS(ANDS.encoding | NOT.encoding),

        ASRV(0x00002800),
        RORV(0x00002C00),
        LSRV(0x00002400),
        LSLV(0x00002000),

        CLS(0x00001400),
        CLZ(0x00001000),
        RBIT(0x00000000),
        REVX(0x00000C00),
        REVW(0x00000800),

        MOVN(0x00000000),
        MOVZ(0x40000000),
        MOVK(0x60000000),

        CSEL(0x00000000),
        CSNEG(0x40000400),
        CSINC(0x00000400),

        BFM(0x20000000),
        SBFM(0x00000000),
        UBFM(0x40000000),
        EXTR(0x13800000),

        MADD(0x00000000),
        MSUB(0x00008000),
        SDIV(0x00000C00),
        UDIV(0x00000800),

        FMOV(0x00000000),
        FMOVCPU2FPU(0x00070000),
        FMOVFPU2CPU(0x00060000),

        FCVTDS(0x00028000),
        FCVTSD(0x00020000),

        FCVTZS(0x00180000),
        SCVTF(0x00020000),

        FABS(0x00008000),
        FSQRT(0x00018000),
        FNEG(0x00010000),

        FRINTZ(0x00058000),

        FADD(0x00002000),
        FSUB(0x00003000),
        FMUL(0x00000000),
        FDIV(0x00001000),
        FMAX(0x00004000),
        FMIN(0x00005000),

        FMADD(0x00000000),
        FMSUB(0x00008000),

        FCMP(0x00000000),
        FCMPZERO(0x00000008),
        FCCMP(0x1E200400),
        FCSEL(0x1E200C00),

        INS(0x4e081c00),
        UMOV(0x4e083c00),

        CNT(0xe205800),
        USRA(0x6f001400),

        HLT(0x00400000),
        BRK(0x00200000),

        CLREX(0xd5033f5f),
        HINT(0xD503201F),
        DMB(0x000000A0),

        BLR_NATIVE(0xc0000000);

        public final int encoding;

        private Instruction(int encoding) {
            this.encoding = encoding;
        }

    }

    public enum ShiftType {
        LSL(0),
        LSR(1),
        ASR(2),
        ROR(3);

        public final int encoding;

        private ShiftType(int encoding) {
            this.encoding = encoding;
        }
    }

    public enum ExtendType {
        UXTB(0),
        UXTH(1),
        UXTW(2),
        UXTX(3),
        SXTB(4),
        SXTH(5),
        SXTW(6),
        SXTX(7);

        public final int encoding;

        private ExtendType(int encoding) {
            this.encoding = encoding;
        }
    }

    /**
     * Condition Flags for branches. See 4.3
     */
    public enum ConditionFlag {
        // Integer | Floating-point meanings
        /**
         * Equal | Equal.
         */
        EQ(0x0),
        /**
         * Not Equal | Not equal or unordered.
         */
        NE(0x1),
        /**
         * Unsigned Higher or Same | Greater than, equal or unordered.
         */
        HS(0x2),
        /**
         * unsigned lower | less than.
         */
        LO(0x3),
        /**
         * minus (negative) | less than.
         */
        MI(0x4),
        /**
         * plus (positive or zero) | greater than, equal or unordered.
         */
        PL(0x5),
        /**
         * overflow set | unordered.
         */
        VS(0x6),
        /**
         * overflow clear | ordered.
         */
        VC(0x7),
        /**
         * unsigned higher | greater than or unordered.
         */
        HI(0x8),
        /**
         * unsigned lower or same | less than or equal.
         */
        LS(0x9),
        /**
         * signed greater than or equal | greater than or equal.
         */
        GE(0xA),
        /**
         * signed less than | less than or unordered.
         */
        LT(0xB),
        /**
         * signed greater than | greater than.
         */
        GT(0xC),
        /**
         * signed less than or equal | less than, equal or unordered.
         */
        LE(0xD),
        /**
         * always | always.
         */
        AL(0xE),
        /**
         * always | always (identical to AL, just to have valid 0b1111 encoding).
         */
        NV(0xF);

        public final int encoding;

        private ConditionFlag(int encoding) {
            this.encoding = encoding;
        }

        /**
         * @return ConditionFlag specified by decoding.
         */
        public static ConditionFlag fromEncoding(int encoding) {
            return values()[encoding];
        }

        public ConditionFlag negate() {
            switch (this) {
                case EQ:
                    return NE;
                case NE:
                    return EQ;
                case HS:
                    return LO;
                case LO:
                    return HS;
                case MI:
                    return PL;
                case PL:
                    return MI;
                case VS:
                    return VC;
                case VC:
                    return VS;
                case HI:
                    return LS;
                case LS:
                    return HI;
                case GE:
                    return LT;
                case LT:
                    return GE;
                case GT:
                    return LE;
                case LE:
                    return GT;
                case AL:
                case NV:
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        }
    }

    public AArch64Assembler(TargetDescription target) {
        super(target);
    }

    /* Conditional Branch (5.2.1) */

    /**
     * Branch conditionally.
     *
     * @param condition may not be null.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     */
    protected void b(ConditionFlag condition, int imm21) {
        b(condition, imm21, -1);
    }

    /**
     * Branch conditionally. Inserts instruction into code buffer at pos.
     *
     * @param condition may not be null.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void b(ConditionFlag condition, int imm21, int pos) {
        if (pos == -1) {
            emitInt(Instruction.BCOND.encoding | getConditionalBranchImm(imm21) | condition.encoding);
        } else {
            emitInt(Instruction.BCOND.encoding | getConditionalBranchImm(imm21) | condition.encoding, pos);
        }
    }

    /**
     * Compare register and branch if non-zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     */
    protected void cbnz(int size, Register reg, int imm21) {
        conditionalBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBNZ, -1);
    }

    /**
     * Compare register and branch if non-zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void cbnz(int size, Register reg, int imm21, int pos) {
        conditionalBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBNZ, pos);
    }

    /**
     * Compare and branch if zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     */
    protected void cbz(int size, Register reg, int imm21) {
        conditionalBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBZ, -1);
    }

    /**
     * Compare register and branch if zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be word aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void cbz(int size, Register reg, int imm21, int pos) {
        conditionalBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBZ, pos);
    }

    private void conditionalBranchInstruction(Register reg, int imm21, InstructionType type, Instruction instr, int pos) {
        assert reg.getRegisterCategory().equals(CPU);
        int instrEncoding = instr.encoding | CompareBranchOp;
        if (pos == -1) {
            emitInt(type.encoding | instrEncoding | getConditionalBranchImm(imm21) | rd(reg));
        } else {
            emitInt(type.encoding | instrEncoding | getConditionalBranchImm(imm21) | rd(reg), pos);
        }
    }

    private static int getConditionalBranchImm(int imm21) {
        assert NumUtil.isSignedNbit(21, imm21) && (imm21 & 0x3) == 0 : "Immediate has to be 21bit signed number and word aligned";
        int imm = (imm21 & NumUtil.getNbitNumberInt(21)) >> 2;
        return imm << ConditionalBranchImmOffset;
    }

    /* Unconditional Branch (immediate) (5.2.2) */

    /**
     * @param imm28 Signed 28-bit offset, has to be word aligned.
     */
    protected void b(int imm28) {
        unconditionalBranchImmInstruction(imm28, Instruction.B, -1);
    }

    /**
     *
     * @param imm28 Signed 28-bit offset, has to be word aligned.
     * @param pos Position where instruction is inserted into code buffer.
     */
    protected void b(int imm28, int pos) {
        unconditionalBranchImmInstruction(imm28, Instruction.B, pos);
    }

    /**
     * Branch and link return address to register X30.
     *
     * @param imm28 Signed 28-bit offset, has to be word aligned.
     */
    public void bl(int imm28) {
        unconditionalBranchImmInstruction(imm28, Instruction.BL, -1);
    }

    private void unconditionalBranchImmInstruction(int imm28, Instruction instr, int pos) {
        assert NumUtil.isSignedNbit(28, imm28) && (imm28 & 0x3) == 0 : "Immediate has to be 28bit signed number and word aligned";
        int imm = (imm28 & NumUtil.getNbitNumberInt(28)) >> 2;
        int instrEncoding = instr.encoding | UnconditionalBranchImmOp;
        if (pos == -1) {
            emitInt(instrEncoding | imm);
        } else {
            emitInt(instrEncoding | imm, pos);
        }
    }

    /* Unconditional Branch (register) (5.2.3) */

    /**
     * Branches to address in register and writes return address into register X30.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     */
    public void blr(Register reg) {
        unconditionalBranchRegInstruction(reg, Instruction.BLR);
    }

    /**
     * Branches to address in register.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     */
    protected void br(Register reg) {
        unconditionalBranchRegInstruction(reg, Instruction.BR);
    }

    /**
     * Return to address in register.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     */
    public void ret(Register reg) {
        unconditionalBranchRegInstruction(reg, Instruction.RET);
    }

    private void unconditionalBranchRegInstruction(Register reg, Instruction instr) {
        assert reg.getRegisterCategory().equals(CPU) && !reg.equals(zr) && !reg.equals(sp);
        final int instrEncoding = instr.encoding | UnconditionalBranchRegOp;
        emitInt(instrEncoding | rs1(reg));
    }

    /* Load-Store Single Register (5.3.1) */

    /**
     * Loads a srcSize value from address into rt zero-extending it.
     *
     * @param srcSize size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address all addressing modes allowed. May not be null.
     */
    public void ldr(int srcSize, Register rt, AArch64Address address) {
        assert rt.getRegisterCategory().equals(CPU);
        assert srcSize == 8 || srcSize == 16 || srcSize == 32 || srcSize == 64;
        int transferSize = NumUtil.log2Ceil(srcSize / 8);
        loadStoreInstruction(rt, address, InstructionType.General32, Instruction.LDR, transferSize);
    }

    /**
     * Loads a srcSize value from address into rt sign-extending it.
     *
     * @param targetSize size of target register in bits. Must be 32 or 64.
     * @param srcSize size of memory read in bits. Must be 8, 16 or 32, but may not be equivalent to
     *            targetSize.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address all addressing modes allowed. May not be null.
     */
    protected void ldrs(int targetSize, int srcSize, Register rt, AArch64Address address) {
        assert rt.getRegisterCategory().equals(CPU);
        assert (srcSize == 8 || srcSize == 16 || srcSize == 32) && srcSize != targetSize;
        int transferSize = NumUtil.log2Ceil(srcSize / 8);
        loadStoreInstruction(rt, address, generalFromSize(targetSize), Instruction.LDRS, transferSize);
    }

    /**
     * Stores register rt into memory pointed by address.
     *
     * @param destSize number of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address all addressing modes allowed. May not be null.
     */
    public void str(int destSize, Register rt, AArch64Address address) {
        assert rt.getRegisterCategory().equals(CPU);
        assert destSize == 8 || destSize == 16 || destSize == 32 || destSize == 64;
        int transferSize = NumUtil.log2Ceil(destSize / 8);
        loadStoreInstruction(rt, address, InstructionType.General64, Instruction.STR, transferSize);
    }

    private void loadStoreInstruction(Register reg, AArch64Address address, InstructionType type, Instruction instr, int log2TransferSize) {
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        int is32Bit = type.width == 32 ? 1 << ImmediateSizeOffset : 0;
        int isFloat = !type.isGeneral ? 1 << LoadStoreFpFlagOffset : 0;
        int memop = instr.encoding | transferSizeEncoding | is32Bit | isFloat | rt(reg);
        switch (address.getAddressingMode()) {
            case IMMEDIATE_SCALED:
                emitInt(memop | LoadStoreScaledOp | address.getImmediate() << LoadStoreScaledImmOffset | rs1(address.getBase()));
                break;
            case IMMEDIATE_UNSCALED:
                emitInt(memop | LoadStoreUnscaledOp | address.getImmediate() << LoadStoreUnscaledImmOffset | rs1(address.getBase()));
                break;
            case BASE_REGISTER_ONLY:
                emitInt(memop | LoadStoreScaledOp | rs1(address.getBase()));
                break;
            case EXTENDED_REGISTER_OFFSET:
            case REGISTER_OFFSET:
                ExtendType extendType = address.getAddressingMode() == AddressingMode.EXTENDED_REGISTER_OFFSET ? address.getExtendType() : ExtendType.UXTX;
                boolean shouldScale = address.isScaled() && log2TransferSize != 0;
                emitInt(memop | LoadStoreRegisterOp | rs2(address.getOffset()) | extendType.encoding << ExtendTypeOffset | (shouldScale ? 1 : 0) << LoadStoreScaledRegOffset | rs1(address.getBase()));
                break;
            case PC_LITERAL:
                assert log2TransferSize >= 2 : "PC literal loads only works for load/stores of 32-bit and larger";
                transferSizeEncoding = (log2TransferSize - 2) << LoadStoreTransferSizeOffset;
                emitInt(transferSizeEncoding | isFloat | LoadLiteralOp | rd(reg) | address.getImmediate() << LoadLiteralImmeOffset);
                break;
            case IMMEDIATE_POST_INDEXED:
                emitInt(memop | LoadStorePostIndexedOp | rs1(address.getBase()) | address.getImmediate() << LoadStoreIndexedImmOffset);
                break;
            case IMMEDIATE_PRE_INDEXED:
                emitInt(memop | LoadStorePreIndexedOp | rs1(address.getBase()) | address.getImmediate() << LoadStoreIndexedImmOffset);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    /* Load-Store Exclusive (5.3.6) */

    /**
     * Load address exclusive. Natural alignment of address is required.
     *
     * @param size size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address Has to be {@link AddressingMode#BASE_REGISTER_ONLY BASE_REGISTER_ONLY}. May
     *            not be null.
     */
    protected void ldxr(int size, Register rt, AArch64Address address) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        exclusiveLoadInstruction(rt, address, transferSize, Instruction.LDXR);
    }

    /**
     * Store address exclusive. Natural alignment of address is required. rs and rt may not point to
     * the same register.
     *
     * @param size size of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rs general purpose register. Set to exclusive access status. 0 means success,
     *            everything else failure. May not be null, or stackpointer.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address Has to be {@link AddressingMode#BASE_REGISTER_ONLY BASE_REGISTER_ONLY}. May
     *            not be null.
     */
    protected void stxr(int size, Register rs, Register rt, AArch64Address address) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        exclusiveStoreInstruction(rs, rt, address, transferSize, Instruction.STXR);
    }

    /* Load-Acquire/Store-Release (5.3.7) */

    /* non exclusive access */
    /**
     * Load acquire. Natural alignment of address is required.
     *
     * @param size size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address Has to be {@link AddressingMode#BASE_REGISTER_ONLY BASE_REGISTER_ONLY}. May
     *            not be null.
     */
    protected void ldar(int size, Register rt, AArch64Address address) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        exclusiveLoadInstruction(rt, address, transferSize, Instruction.LDAR);
    }

    /**
     * Store-release. Natural alignment of address is required.
     *
     * @param size size of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address Has to be {@link AddressingMode#BASE_REGISTER_ONLY BASE_REGISTER_ONLY}. May
     *            not be null.
     */
    protected void stlr(int size, Register rt, AArch64Address address) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        // Hack: Passing the zero-register means it is ignored when building the encoding.
        exclusiveStoreInstruction(AArch64.r0, rt, address, transferSize, Instruction.STLR);
    }

    /* exclusive access */
    /**
     * Load acquire exclusive. Natural alignment of address is required.
     *
     * @param size size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address Has to be {@link AddressingMode#BASE_REGISTER_ONLY BASE_REGISTER_ONLY}. May
     *            not be null.
     */
    public void ldaxr(int size, Register rt, AArch64Address address) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        exclusiveLoadInstruction(rt, address, transferSize, Instruction.LDAXR);
    }

    /**
     * Store-release exclusive. Natural alignment of address is required. rs and rt may not point to
     * the same register.
     *
     * @param size size of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rs general purpose register. Set to exclusive access status. 0 means success,
     *            everything else failure. May not be null, or stackpointer.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address Has to be {@link AddressingMode#BASE_REGISTER_ONLY BASE_REGISTER_ONLY}. May
     *            not be null.
     */
    public void stlxr(int size, Register rs, Register rt, AArch64Address address) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        exclusiveStoreInstruction(rs, rt, address, transferSize, Instruction.STLXR);
    }

    private void exclusiveLoadInstruction(Register reg, AArch64Address address, int log2TransferSize, Instruction instr) {
        assert address.getAddressingMode() == AddressingMode.BASE_REGISTER_ONLY;
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        assert reg.getRegisterCategory().equals(CPU);
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        int instrEncoding = instr.encoding;
        emitInt(transferSizeEncoding | instrEncoding | 1 << ImmediateSizeOffset | rt(reg) | rs1(address.getBase()));
    }

    /**
     * Stores data from rt into address and sets rs to the returned exclusive access status.
     *
     * @param rs general purpose register into which the exclusive access status is written. May not
     *            be null.
     * @param rt general purpose register containing data to be written to memory at address. May
     *            not be null
     * @param address Address in base register without offset form specifying where rt is written
     *            to.
     * @param log2TransferSize log2Ceil of memory transfer size.
     */
    private void exclusiveStoreInstruction(Register rs, Register rt, AArch64Address address, int log2TransferSize, Instruction instr) {
        assert address.getAddressingMode() == AddressingMode.BASE_REGISTER_ONLY;
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        assert rt.getRegisterCategory().equals(CPU) && rs.getRegisterCategory().equals(CPU) && !rs.equals(rt);
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        int instrEncoding = instr.encoding;
        emitInt(transferSizeEncoding | instrEncoding | rs2(rs) | rt(rt) | rs1(address.getBase()));
    }

    /* PC-relative Address Calculation (5.4.4) */

    /**
     * Address of page: sign extends 21-bit offset, shifts if left by 12 and adds it to the value of
     * the PC with its bottom 12-bits cleared, writing the result to dst.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     * @param imm Signed 33-bit offset with lower 12bits clear.
     */
    // protected void adrp(Register dst, long imm) {
    // assert (imm & NumUtil.getNbitNumberInt(12)) == 0 : "Lower 12-bit of immediate must be zero.";
    // assert NumUtil.isSignedNbit(33, imm);
    // addressCalculationInstruction(dst, (int) (imm >>> 12), Instruction.ADRP);
    // }

    /**
     * Adds a 21-bit signed offset to the program counter and writes the result to dst.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     * @param imm21 Signed 21-bit offset.
     */
    public void adr(Register dst, int imm21) {
        addressCalculationInstruction(dst, imm21, Instruction.ADR);
    }

    private void addressCalculationInstruction(Register dst, int imm21, Instruction instr) {
        assert dst.getRegisterCategory().equals(CPU);
        int instrEncoding = instr.encoding | PcRelImmOp;
        emitInt(instrEncoding | rd(dst) | getPcRelativeImmEncoding(imm21));
    }

    private static int getPcRelativeImmEncoding(int imm21) {
        assert NumUtil.isSignedNbit(21, imm21);
        int imm = imm21 & NumUtil.getNbitNumberInt(21);
        // higher 19 bit
        int immHi = (imm >> 2) << PcRelImmHiOffset;
        // lower 2 bit
        int immLo = (imm & 0x3) << PcRelImmLoOffset;
        return immHi | immLo;
    }

    /* Arithmetic (Immediate) (5.4.1) */

    /**
     * dst = src + aimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void add(int size, Register dst, Register src, int aimm) {
        assert !dst.equals(zr);
        assert !src.equals(zr);
        addSubImmInstruction(dst, src, aimm, generalFromSize(size), Instruction.ADD);
    }

    /**
     * dst = src + aimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void adds(int size, Register dst, Register src, int aimm) {
        assert !dst.equals(sp);
        assert !src.equals(zr);
        addSubImmInstruction(dst, src, aimm, generalFromSize(size), Instruction.ADDS);
    }

    /**
     * dst = src - aimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void sub(int size, Register dst, Register src, int aimm) {
        assert !dst.equals(zr);
        assert !src.equals(zr);
        addSubImmInstruction(dst, src, aimm, generalFromSize(size), Instruction.SUB);
    }

    /**
     * dst = src - aimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void subs(int size, Register dst, Register src, int aimm) {
        assert !dst.equals(sp);
        assert !src.equals(zr);
        addSubImmInstruction(dst, src, aimm, generalFromSize(size), Instruction.SUBS);
    }

    private void addSubImmInstruction(Register dst, Register src, int aimm, InstructionType type, Instruction instr) {
        int instrEncoding = instr.encoding | AddSubImmOp;
        emitInt(type.encoding | instrEncoding | encodeAimm(aimm) | rd(dst) | rs1(src));
    }

    /**
     * Encodes arithmetic immediate.
     *
     * @param imm Immediate has to be either an unsigned 12bit value or un unsigned 24bit value with
     *            the lower 12 bits 0.
     * @return Representation of immediate for use with arithmetic instructions.
     */
    private static int encodeAimm(int imm) {
        assert isAimm(imm) : "Immediate has to be legal arithmetic immediate value " + imm;
        if (NumUtil.isUnsignedNbit(12, imm)) {
            return imm << ImmediateOffset;
        } else {
            // First 12 bit are 0, so shift immediate 12 bit and set flag to indicate
            // shifted immediate value.
            return (imm >>> 12 << ImmediateOffset) | (1 << AddSubShiftOffset);
        }
    }

    /**
     * Checks whether immediate can be encoded as an arithmetic immediate.
     *
     * @param imm Immediate has to be either an unsigned 12bit value or un unsigned 24bit value with
     *            the lower 12 bits 0.
     * @return true if valid arithmetic immediate, false otherwise.
     */
    protected static boolean isAimm(int imm) {
        return NumUtil.isUnsignedNbit(12, imm) || NumUtil.isUnsignedNbit(12, imm >>> 12) && (imm & 0xfff) == 0;
    }

    /* Logical (immediate) (5.4.2) */

    /**
     * dst = src & bimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalImmediateTable} for exact definition.
     */
    public void and(int size, Register dst, Register src, long bimm) {
        assert !dst.equals(zr);
        assert !src.equals(sp);
        logicalImmInstruction(dst, src, bimm, generalFromSize(size), Instruction.AND);
    }

    /**
     * dst = src & bimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stack-pointer.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalImmediateTable} for exact definition.
     */
    public void ands(int size, Register dst, Register src, long bimm) {
        assert !dst.equals(sp);
        assert !src.equals(sp);
        logicalImmInstruction(dst, src, bimm, generalFromSize(size), Instruction.ANDS);
    }

    /**
     * dst = src ^ bimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalImmediateTable} for exact definition.
     */
    public void eor(int size, Register dst, Register src, long bimm) {
        assert !dst.equals(zr);
        assert !src.equals(sp);
        logicalImmInstruction(dst, src, bimm, generalFromSize(size), Instruction.EOR);
    }

    /**
     * dst = src | bimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalImmediateTable} for exact definition.
     */
    protected void orr(int size, Register dst, Register src, long bimm) {
        assert !dst.equals(zr);
        assert !src.equals(sp);
        logicalImmInstruction(dst, src, bimm, generalFromSize(size), Instruction.ORR);
    }

    protected void logicalImmInstruction(Register dst, Register src, long bimm, InstructionType type, Instruction instr) {
        // Mask higher bits off, since we always pass longs around even for the 32-bit instruction.
        long bimmValue;
        if (type == InstructionType.General32) {
            assert (bimm >> 32) == 0 || (bimm >> 32) == -1L : "Higher order bits for 32-bit instruction must either all be 0 or 1.";
            bimmValue = bimm & NumUtil.getNbitNumberLong(32);
        } else {
            bimmValue = bimm;
        }
        int immEncoding = LogicalImmediateTable.getLogicalImmEncoding(type == InstructionType.General64, bimmValue);
        int instrEncoding = instr.encoding | LogicalImmOp;
        emitInt(type.encoding | instrEncoding | immEncoding | rd(dst) | rs1(src));
    }

    /* Move (wide immediate) (5.4.3) */

    /**
     * dst = uimm16 << shiftAmt.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param uimm16 16-bit unsigned immediate
     * @param shiftAmt amount by which uimm16 is left shifted. Can be any multiple of 16 smaller
     *            than size.
     */
    protected void movz(int size, Register dst, int uimm16, int shiftAmt) {
        moveWideImmInstruction(dst, uimm16, shiftAmt, generalFromSize(size), Instruction.MOVZ);
    }

    /**
     * dst = ~(uimm16 << shiftAmt).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param uimm16 16-bit unsigned immediate
     * @param shiftAmt amount by which uimm16 is left shifted. Can be any multiple of 16 smaller
     *            than size.
     */
    protected void movn(int size, Register dst, int uimm16, int shiftAmt) {
        moveWideImmInstruction(dst, uimm16, shiftAmt, generalFromSize(size), Instruction.MOVN);
    }

    /**
     * dst<pos+15:pos> = uimm16.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param uimm16 16-bit unsigned immediate
     * @param pos position into which uimm16 is inserted. Can be any multiple of 16 smaller than
     *            size.
     */
    protected void movk(int size, Register dst, int uimm16, int pos) {
        moveWideImmInstruction(dst, uimm16, pos, generalFromSize(size), Instruction.MOVK);
    }

    private void moveWideImmInstruction(Register dst, int uimm16, int shiftAmt, InstructionType type, Instruction instr) {
        assert dst.getRegisterCategory().equals(CPU);
        assert NumUtil.isUnsignedNbit(16, uimm16) : "Immediate has to be unsigned 16bit";
        assert shiftAmt == 0 || shiftAmt == 16 || (type == InstructionType.General64 && (shiftAmt == 32 || shiftAmt == 48)) : "Invalid shift amount: " + shiftAmt;
        int shiftValue = shiftAmt >> 4;
        int instrEncoding = instr.encoding | MoveWideImmOp;
        emitInt(type.encoding | instrEncoding | rd(dst) | uimm16 << MoveWideImmOffset | shiftValue << MoveWideShiftOffset);
    }

    /* Bitfield Operations (5.4.5) */

    /**
     * Bitfield move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param r must be in the range 0 to size - 1
     * @param s must be in the range 0 to size - 1
     */
    protected void bfm(int size, Register dst, Register src, int r, int s) {
        bitfieldInstruction(dst, src, r, s, generalFromSize(size), Instruction.BFM);
    }

    /**
     * Unsigned bitfield move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param r must be in the range 0 to size - 1
     * @param s must be in the range 0 to size - 1
     */
    protected void ubfm(int size, Register dst, Register src, int r, int s) {
        bitfieldInstruction(dst, src, r, s, generalFromSize(size), Instruction.UBFM);
    }

    /**
     * Signed bitfield move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param r must be in the range 0 to size - 1
     * @param s must be in the range 0 to size - 1
     */
    protected void sbfm(int size, Register dst, Register src, int r, int s) {
        bitfieldInstruction(dst, src, r, s, generalFromSize(size), Instruction.SBFM);
    }

    private void bitfieldInstruction(Register dst, Register src, int r, int s, InstructionType type, Instruction instr) {
        assert !dst.equals(sp) && !dst.equals(zr);
        assert !src.equals(sp) && !src.equals(zr);
        assert s >= 0 && s < type.width && r >= 0 && r < type.width;
        int instrEncoding = instr.encoding | BitfieldImmOp;
        int sf = type == InstructionType.General64 ? 1 << ImmediateSizeOffset : 0;
        emitInt(type.encoding | instrEncoding | sf | r << ImmediateRotateOffset | s << ImmediateOffset | rd(dst) | rs1(src));
    }

    /* Extract (Immediate) (5.4.6) */

    /**
     * Extract. dst = src1:src2<lsb+31:lsb>
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param lsb must be in range 0 to size - 1.
     */
    protected void extr(int size, Register dst, Register src1, Register src2, int lsb) {
        extractInstruction(dst, src1, src2, lsb, generalFromSize(size));
    }

    private void extractInstruction(Register dst, Register src1, Register src2, int lsb, InstructionType type) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        assert lsb >= 0 && lsb < type.width;
        int sf = type == InstructionType.General64 ? 1 << ImmediateSizeOffset : 0;
        emitInt(type.encoding | Instruction.EXTR.encoding | sf | lsb << ImmediateOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Arithmetic (shifted register) (5.5.1) */

    /**
     * dst = src1 + shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param imm must be in range 0 to size - 1.
     */
    protected void add(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        addSubShiftedInstruction(dst, src1, src2, shiftType, imm, generalFromSize(size), Instruction.ADD);
    }

    /**
     * dst = src1 + shiftType(src2, imm) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param imm must be in range 0 to size - 1.
     */
    protected void adds(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        addSubShiftedInstruction(dst, src1, src2, shiftType, imm, generalFromSize(size), Instruction.ADDS);
    }

    /**
     * dst = src1 - shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param imm must be in range 0 to size - 1.
     */
    protected void sub(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        addSubShiftedInstruction(dst, src1, src2, shiftType, imm, generalFromSize(size), Instruction.SUB);
    }

    /**
     * dst = src1 - shiftType(src2, imm) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param imm must be in range 0 to size - 1.
     */
    protected void subs(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        addSubShiftedInstruction(dst, src1, src2, shiftType, imm, generalFromSize(size), Instruction.SUBS);
    }

    private void addSubShiftedInstruction(Register dst, Register src1, Register src2, ShiftType shiftType, int imm, InstructionType type, Instruction instr) {
        assert shiftType != ShiftType.ROR;
        assert imm >= 0 && imm < type.width;
        int instrEncoding = instr.encoding | AddSubShiftedOp;
        emitInt(type.encoding | instrEncoding | imm << ImmediateOffset | shiftType.encoding << ShiftTypeOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Arithmetic (extended register) (5.5.2) */
    /**
     * dst = src1 + extendType(src2) << imm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register..
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    public void add(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert !dst.equals(zr);
        assert !src1.equals(zr);
        assert !src2.equals(sp);
        addSubExtendedInstruction(dst, src1, src2, extendType, shiftAmt, generalFromSize(size), Instruction.ADD);
    }

    /**
     * dst = src1 + extendType(src2) << imm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer..
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    protected void adds(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert !dst.equals(sp);
        assert !src1.equals(zr);
        assert !src2.equals(sp);
        addSubExtendedInstruction(dst, src1, src2, extendType, shiftAmt, generalFromSize(size), Instruction.ADDS);
    }

    /**
     * dst = src1 - extendType(src2) << imm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register..
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    protected void sub(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert !dst.equals(zr);
        assert !src1.equals(zr);
        assert !src2.equals(sp);
        addSubExtendedInstruction(dst, src1, src2, extendType, shiftAmt, generalFromSize(size), Instruction.SUB);
    }

    /**
     * dst = src1 - extendType(src2) << imm and sets flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer..
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    protected void subs(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert !dst.equals(sp);
        assert !src1.equals(zr);
        assert !src2.equals(sp);
        addSubExtendedInstruction(dst, src1, src2, extendType, shiftAmt, generalFromSize(size), Instruction.SUBS);
    }

    private void addSubExtendedInstruction(Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt, InstructionType type, Instruction instr) {
        assert shiftAmt >= 0 && shiftAmt <= 4;
        int instrEncoding = instr.encoding | AddSubExtendedOp;
        emitInt(type.encoding | instrEncoding | shiftAmt << ImmediateOffset | extendType.encoding << ExtendTypeOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Logical (shifted register) (5.5.3) */
    /**
     * dst = src1 & shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void and(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(dst, src1, src2, shiftType, shiftAmt, generalFromSize(size), Instruction.AND);
    }

    /**
     * dst = src1 & shiftType(src2, imm) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void ands(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(dst, src1, src2, shiftType, shiftAmt, generalFromSize(size), Instruction.ANDS);
    }

    /**
     * dst = src1 & ~(shiftType(src2, imm)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void bic(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(dst, src1, src2, shiftType, shiftAmt, generalFromSize(size), Instruction.BIC);
    }

    /**
     * dst = src1 & ~(shiftType(src2, imm)) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void bics(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(dst, src1, src2, shiftType, shiftAmt, generalFromSize(size), Instruction.BICS);
    }

    /**
     * dst = src1 ^ ~(shiftType(src2, imm)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void eon(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(dst, src1, src2, shiftType, shiftAmt, generalFromSize(size), Instruction.EON);
    }

    /**
     * dst = src1 ^ shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void eor(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(dst, src1, src2, shiftType, shiftAmt, generalFromSize(size), Instruction.EOR);
    }

    /**
     * dst = src1 | shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void orr(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(dst, src1, src2, shiftType, shiftAmt, generalFromSize(size), Instruction.ORR);
    }

    /**
     * dst = src1 | ~(shiftType(src2, imm)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    protected void orn(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        logicalRegInstruction(dst, src1, src2, shiftType, shiftAmt, generalFromSize(size), Instruction.ORN);
    }

    private void logicalRegInstruction(Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt, InstructionType type, Instruction instr) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        assert shiftAmt >= 0 && shiftAmt < type.width;
        int instrEncoding = instr.encoding | LogicalShiftOp;
        emitInt(type.encoding | instrEncoding | shiftAmt << ImmediateOffset | shiftType.encoding << ShiftTypeOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Variable Shift (5.5.4) */
    /**
     * dst = src1 >> (src2 & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    protected void asr(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(dst, src1, src2, generalFromSize(size), Instruction.ASRV);
    }

    /**
     * dst = src1 << (src2 & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    protected void lsl(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(dst, src1, src2, generalFromSize(size), Instruction.LSLV);
    }

    /**
     * dst = src1 >>> (src2 & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    protected void lsr(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(dst, src1, src2, generalFromSize(size), Instruction.LSRV);
    }

    /**
     * dst = rotateRight(src1, (src2 & log2(size))).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    protected void ror(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(dst, src1, src2, generalFromSize(size), Instruction.RORV);
    }

    /* Bit Operations (5.5.5) */

    /**
     * Counts leading sign bits. Sets Wd to the number of consecutive bits following the topmost bit
     * in dst, that are the same as the topmost bit. The count does not include the topmost bit
     * itself , so the result will be in the range 0 to size-1 inclusive.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, zero-register or the stackpointer.
     * @param src source register. May not be null, zero-register or the stackpointer.
     */
    protected void cls(int size, Register dst, Register src) {
        dataProcessing1SourceOp(dst, src, generalFromSize(size), Instruction.CLS);
    }

    /**
     * Counts leading zeros.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, zero-register or the stackpointer.
     * @param src source register. May not be null, zero-register or the stackpointer.
     */
    public void clz(int size, Register dst, Register src) {
        dataProcessing1SourceOp(dst, src, generalFromSize(size), Instruction.CLZ);
    }

    /**
     * Reverses bits.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, zero-register or the stackpointer.
     * @param src source register. May not be null, zero-register or the stackpointer.
     */
    protected void rbit(int size, Register dst, Register src) {
        dataProcessing1SourceOp(dst, src, generalFromSize(size), Instruction.RBIT);
    }

    /**
     * Reverses bytes.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src source register. May not be null or the stackpointer.
     */
    public void rev(int size, Register dst, Register src) {
        if (size == 64) {
            dataProcessing1SourceOp(dst, src, generalFromSize(size), Instruction.REVX);
        } else {
            assert size == 32;
            dataProcessing1SourceOp(dst, src, generalFromSize(size), Instruction.REVW);
        }
    }

    /* Conditional Data Processing (5.5.6) */

    /**
     * Conditional select. dst = src1 if condition else src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param condition any condition flag. May not be null.
     */
    protected void csel(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        conditionalSelectInstruction(dst, src1, src2, condition, generalFromSize(size), Instruction.CSEL);
    }

    /**
     * Conditional select negate. dst = src1 if condition else -src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param condition any condition flag. May not be null.
     */
    protected void csneg(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        conditionalSelectInstruction(dst, src1, src2, condition, generalFromSize(size), Instruction.CSNEG);
    }

    /**
     * Conditional increase. dst = src1 if condition else src2 + 1.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param condition any condition flag. May not be null.
     */
    protected void csinc(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        conditionalSelectInstruction(dst, src1, src2, condition, generalFromSize(size), Instruction.CSINC);
    }

    private void conditionalSelectInstruction(Register dst, Register src1, Register src2, ConditionFlag condition, InstructionType type, Instruction instr) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        int instrEncoding = instr.encoding | ConditionalSelectOp;
        emitInt(type.encoding | instrEncoding | rd(dst) | rs1(src1) | rs2(src2) | condition.encoding << ConditionalConditionOffset);
    }

    /* Integer Multiply/Divide (5.6) */

    /**
     * dst = src1 * src2 + src3.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    protected void madd(int size, Register dst, Register src1, Register src2, Register src3) {
        mulInstruction(dst, src1, src2, src3, generalFromSize(size), Instruction.MADD);
    }

    /**
     * dst = src3 - src1 * src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    protected void msub(int size, Register dst, Register src1, Register src2, Register src3) {
        mulInstruction(dst, src1, src2, src3, generalFromSize(size), Instruction.MSUB);
    }

    /**
     * Signed multiply high. dst = (src1 * src2)[127:64]
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    protected void smulh(Register dst, Register src1, Register src2) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        emitInt(0b10011011010 << 21 | dst.encoding | src1.encoding << 5 | src2.encoding << 16 | 0b011111 << 10);
    }

    /**
     * unsigned multiply high. dst = (src1 * src2)[127:64]
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    protected void umulh(Register dst, Register src1, Register src2) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        emitInt(0b10011011110 << 21 | dst.encoding | src1.encoding << 5 | src2.encoding << 16 | 0b011111 << 10);
    }

    /**
     * unsigned multiply add-long. xDst = xSrc3 + (wSrc1 * wSrc2)
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    protected void umaddl(Register dst, Register src1, Register src2, Register src3) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        assert !src3.equals(sp);
        emitInt(0b10011011101 << 21 | dst.encoding | src1.encoding << 5 | src2.encoding << 16 | 0b011111 << 10);
    }

    /**
     * signed multiply add-long. xDst = xSrc3 + (wSrc1 * wSrc2)
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    protected void smaddl(Register dst, Register src1, Register src2, Register src3) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        assert !src3.equals(sp);
        emitInt(0b10011011001 << 21 | dst.encoding | src1.encoding << 5 | src2.encoding << 16 | src3.encoding << 10);
    }

    private void mulInstruction(Register dst, Register src1, Register src2, Register src3, InstructionType type, Instruction instr) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        assert !src3.equals(sp);
        int instrEncoding = instr.encoding | MulOp;
        emitInt(type.encoding | instrEncoding | rd(dst) | rs1(src1) | rs2(src2) | rs3(src3));
    }

    /**
     * Signed divide. dst = src1 / src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    public void sdiv(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(dst, src1, src2, generalFromSize(size), Instruction.SDIV);
    }

    /**
     * Unsigned divide. dst = src1 / src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    public void udiv(int size, Register dst, Register src1, Register src2) {
        dataProcessing2SourceOp(dst, src1, src2, generalFromSize(size), Instruction.UDIV);
    }

    private void dataProcessing2SourceOp(Register dst, Register src1, Register src2, InstructionType type, Instruction instr) {
        assert !dst.equals(sp);
        assert !src1.equals(sp);
        assert !src2.equals(sp);
        int instrEncoding = instr.encoding | DataProcessing2SourceOp;
        emitInt(type.encoding | instrEncoding | rd(dst) | rs1(src1) | rs2(src2));
    }

    private void dataProcessing1SourceOp(Register dst, Register src, InstructionType type, Instruction instr) {
        int instrEncoding = instr.encoding | DataProcessing1SourceOp;
        emitInt(type.encoding | instrEncoding | rd(dst) | rs1(src));
    }

    /* Floating point operations */

    /* Load-Store Single FP register (5.7.1.1) */
    /**
     * Floating point load.
     *
     * @param size number of bits read from memory into rt. Must be 32 or 64.
     * @param rt floating point register. May not be null.
     * @param address all addressing modes allowed. May not be null.
     */
    public void fldr(int size, Register rt, AArch64Address address) {
        assert rt.getRegisterCategory().equals(SIMD);
        assert size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        loadStoreInstruction(rt, address, InstructionType.FP32, Instruction.LDR, transferSize);
    }

    /**
     * Floating point store.
     *
     * @param size number of bits read from memory into rt. Must be 32 or 64.
     * @param rt floating point register. May not be null.
     * @param address all addressing modes allowed. May not be null.
     */
    public void fstr(int size, Register rt, AArch64Address address) {
        assert rt.getRegisterCategory().equals(SIMD);
        assert size == 32 || size == 64;
        int transferSize = NumUtil.log2Ceil(size / 8);
        loadStoreInstruction(rt, address, InstructionType.FP64, Instruction.STR, transferSize);
    }

    /* Floating-point Move (register) (5.7.2) */

    /**
     * Floating point move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    protected void fmov(int size, Register dst, Register src) {
        fpDataProcessing1Source(dst, src, floatFromSize(size), Instruction.FMOV);
    }

    /**
     * Move size bits from floating point register unchanged to general purpose register.
     *
     * @param size number of bits read from memory into rt. Must be 32 or 64.
     * @param dst general purpose register. May not be null, stack-pointer or zero-register
     * @param src floating point register. May not be null.
     */
    protected void fmovFpu2Cpu(int size, Register dst, Register src) {
        assert dst.getRegisterCategory().equals(CPU);
        assert src.getRegisterCategory().equals(SIMD);
        fmovCpuFpuInstruction(dst, src, size == 64, Instruction.FMOVFPU2CPU);
    }

    /**
     * Move size bits from general purpose register unchanged to floating point register.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating point register. May not be null.
     * @param src general purpose register. May not be null or stack-pointer.
     */
    protected void fmovCpu2Fpu(int size, Register dst, Register src) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert src.getRegisterCategory().equals(CPU);
        fmovCpuFpuInstruction(dst, src, size == 64, Instruction.FMOVCPU2FPU);
    }

    private void fmovCpuFpuInstruction(Register dst, Register src, boolean is64bit, Instruction instr) {
        int instrEncoding = instr.encoding | FpConvertOp;
        int sf = is64bit ? InstructionType.FP64.encoding | InstructionType.General64.encoding : InstructionType.FP32.encoding | InstructionType.General32.encoding;
        emitInt(sf | instrEncoding | rd(dst) | rs1(src));
    }

    /* Floating-point Move (immediate) (5.7.3) */

    /**
     * Move immediate into register.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating point register. May not be null.
     * @param imm immediate that is loaded into dst. If size is 32 only float immediates can be
     *            loaded, i.e. (float) imm == imm must be true. In all cases
     *            {@code isFloatImmediate}, respectively {@code #isDoubleImmediate} must be true
     *            depending on size.
     */
    protected void fmov(int size, Register dst, double imm) {
        fmovImmInstruction(dst, imm, floatFromSize(size));
    }

    private void fmovImmInstruction(Register dst, double imm, InstructionType type) {
        assert dst.getRegisterCategory().equals(SIMD);
        int immEncoding;
        if (type == InstructionType.FP64) {
            immEncoding = getDoubleImmediate(imm);
        } else {
            assert imm == (float) imm : "float mov must use an immediate that can be represented using a float.";
            immEncoding = getFloatImmediate((float) imm);
        }
        int instrEncoding = Instruction.FMOV.encoding | FpImmOp;
        emitInt(type.encoding | instrEncoding | immEncoding | rd(dst));
    }

    private static int getDoubleImmediate(double imm) {
        assert isDoubleImmediate(imm);
        // bits: aBbb.bbbb.bbcd.efgh.0000.0000.0000.0000
        // 0000.0000.0000.0000.0000.0000.0000.0000
        long repr = Double.doubleToRawLongBits(imm);
        int a = (int) (repr >>> 63) << 7;
        int b = (int) ((repr >>> 61) & 0x1) << 6;
        int cToH = (int) (repr >>> 48) & 0x3f;
        return (a | b | cToH) << FpImmOffset;
    }

    protected static boolean isDoubleImmediate(double imm) {
        // Valid values will have the form:
        // aBbb.bbbb.bbcd.efgh.0000.0000.0000.0000
        // 0000.0000.0000.0000.0000.0000.0000.0000
        long bits = Double.doubleToRawLongBits(imm);
        // lower 48 bits are cleared
        if ((bits & NumUtil.getNbitNumberLong(48)) != 0) {
            return false;
        }
        // bits[61..54] are all set or all cleared.
        long pattern = (bits >> 54) & NumUtil.getNbitNumberLong(7);
        if (pattern != 0 && pattern != NumUtil.getNbitNumberLong(7)) {
            return false;
        }
        // bits[62] and bits[61] are opposites.
        return ((bits ^ (bits << 1)) & (1L << 62)) != 0;
    }

    private static int getFloatImmediate(float imm) {
        assert isFloatImmediate(imm);
        // bits: aBbb.bbbc.defg.h000.0000.0000.0000.0000
        int repr = Float.floatToRawIntBits(imm);
        int a = (repr >>> 31) << 7;
        int b = ((repr >>> 29) & 0x1) << 6;
        int cToH = (repr >>> 19) & NumUtil.getNbitNumberInt(6);
        return (a | b | cToH) << FpImmOffset;
    }

    protected static boolean isFloatImmediate(float imm) {
        // Valid values will have the form:
        // aBbb.bbbc.defg.h000.0000.0000.0000.0000
        int bits = Float.floatToRawIntBits(imm);
        // lower 20 bits are cleared.
        if ((bits & NumUtil.getNbitNumberInt(19)) != 0) {
            return false;
        }
        // bits[29..25] are all set or all cleared
        int pattern = (bits >> 25) & NumUtil.getNbitNumberInt(5);
        if (pattern != 0 && pattern != NumUtil.getNbitNumberInt(5)) {
            return false;
        }
        // bits[29] and bits[30] have to be opposite
        return ((bits ^ (bits << 1)) & (1 << 30)) != 0;
    }

    /* Convert Floating-point Precision (5.7.4.1) */
    /* Converts float to double and vice-versa */

    /**
     * Convert float to double and vice-versa.
     *
     * @param srcSize size of source register in bits.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fcvt(int srcSize, Register dst, Register src) {
        if (srcSize == 32) {
            fpDataProcessing1Source(dst, src, floatFromSize(srcSize), Instruction.FCVTDS);
        } else {
            fpDataProcessing1Source(dst, src, floatFromSize(srcSize), Instruction.FCVTSD);
        }
    }

    /* Convert to Integer (5.7.4.2) */

    /**
     * Convert floating point to integer. Rounds towards zero.
     *
     * @param targetSize size of integer register. 32 or 64.
     * @param srcSize size of floating point register. 32 or 64.
     * @param dst general purpose register. May not be null, the zero-register or the stackpointer.
     * @param src floating point register. May not be null.
     */
    public void fcvtzs(int targetSize, int srcSize, Register dst, Register src) {
        assert !dst.equals(zr) && !dst.equals(sp);
        assert src.getRegisterCategory().equals(SIMD);
        fcvtCpuFpuInstruction(dst, src, generalFromSize(targetSize), floatFromSize(srcSize), Instruction.FCVTZS);
    }

    /* Convert from Integer (5.7.4.2) */
    /**
     * Converts integer to floating point. Uses rounding mode defined by FCPR.
     *
     * @param targetSize size of floating point register. 32 or 64.
     * @param srcSize size of integer register. 32 or 64.
     * @param dst floating point register. May not be null.
     * @param src general purpose register. May not be null or the stackpointer.
     */
    public void scvtf(int targetSize, int srcSize, Register dst, Register src) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert !src.equals(sp);
        fcvtCpuFpuInstruction(dst, src, floatFromSize(targetSize), generalFromSize(srcSize), Instruction.SCVTF);
    }

    private void fcvtCpuFpuInstruction(Register dst, Register src, InstructionType type1, InstructionType type2, Instruction instr) {
        int instrEncoding = instr.encoding | FpConvertOp;
        emitInt(type1.encoding | type2.encoding | instrEncoding | rd(dst) | rs1(src));
    }

    /* Floating-point Round to Integral (5.7.5) */

    /**
     * Rounds floating-point to integral. Rounds towards zero.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    protected void frintz(int size, Register dst, Register src) {
        fpDataProcessing1Source(dst, src, floatFromSize(size), Instruction.FRINTZ);
    }

    /* Floating-point Arithmetic (1 source) (5.7.6) */

    /**
     * dst = |src|.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fabs(int size, Register dst, Register src) {
        fpDataProcessing1Source(dst, src, floatFromSize(size), Instruction.FABS);
    }

    /**
     * dst = -neg.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fneg(int size, Register dst, Register src) {
        fpDataProcessing1Source(dst, src, floatFromSize(size), Instruction.FNEG);
    }

    /**
     * dst = Sqrt(src).
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fsqrt(int size, Register dst, Register src) {
        fpDataProcessing1Source(dst, src, floatFromSize(size), Instruction.FSQRT);
    }

    private void fpDataProcessing1Source(Register dst, Register src, InstructionType type, Instruction instr) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert src.getRegisterCategory().equals(SIMD);
        int instrEncoding = instr.encoding | Fp1SourceOp;
        emitInt(type.encoding | instrEncoding | rd(dst) | rs1(src));
    }

    /* Floating-point Arithmetic (2 source) (5.7.7) */

    /**
     * dst = src1 + src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fadd(int size, Register dst, Register src1, Register src2) {
        fpDataProcessing2Source(dst, src1, src2, floatFromSize(size), Instruction.FADD);
    }

    /**
     * dst = src1 - src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fsub(int size, Register dst, Register src1, Register src2) {
        fpDataProcessing2Source(dst, src1, src2, floatFromSize(size), Instruction.FSUB);
    }

    /**
     * dst = src1 * src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fmul(int size, Register dst, Register src1, Register src2) {
        fpDataProcessing2Source(dst, src1, src2, floatFromSize(size), Instruction.FMUL);
    }

    /**
     * dst = src1 / src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fdiv(int size, Register dst, Register src1, Register src2) {
        fpDataProcessing2Source(dst, src1, src2, floatFromSize(size), Instruction.FDIV);
    }

    private void fpDataProcessing2Source(Register dst, Register src1, Register src2, InstructionType type, Instruction instr) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        int instrEncoding = instr.encoding | Fp2SourceOp;
        emitInt(type.encoding | instrEncoding | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Floating-point Multiply-Add (5.7.9) */

    /**
     * dst = src1 * src2 + src3.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param src3 floating point register. May not be null.
     */
    protected void fmadd(int size, Register dst, Register src1, Register src2, Register src3) {
        fpDataProcessing3Source(dst, src1, src2, src3, floatFromSize(size), Instruction.FMADD);
    }

    /**
     * dst = src3 - src1 * src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param src3 floating point register. May not be null.
     */
    protected void fmsub(int size, Register dst, Register src1, Register src2, Register src3) {
        fpDataProcessing3Source(dst, src1, src2, src3, floatFromSize(size), Instruction.FMSUB);
    }

    private void fpDataProcessing3Source(Register dst, Register src1, Register src2, Register src3, InstructionType type, Instruction instr) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        assert src3.getRegisterCategory().equals(SIMD);
        int instrEncoding = instr.encoding | Fp3SourceOp;
        emitInt(type.encoding | instrEncoding | rd(dst) | rs1(src1) | rs2(src2) | rs3(src3));
    }

    /* Floating-point Comparison (5.7.10) */

    /**
     * Compares src1 to src2.
     *
     * @param size register size.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fcmp(int size, Register src1, Register src2) {
        fcmpInstruction(src1, src2, floatFromSize(size));
    }

    private void fcmpInstruction(Register src1, Register src2, InstructionType type) {
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        int instrEncoding = Instruction.FCMP.encoding | FpCmpOp;
        emitInt(type.encoding | instrEncoding | rs1(src1) | rs2(src2));
    }

    /**
     * Conditional compare. NZCV = fcmp(src1, src2) if condition else uimm4.
     *
     * @param size register size.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param uimm4 condition flags that are used if condition is false.
     * @param condition every condition allowed. May not be null.
     */
    public void fccmp(int size, Register src1, Register src2, int uimm4, ConditionFlag condition) {
        fConditionalCompareInstruction(src1, src2, uimm4, condition, floatFromSize(size));
    }

    private void fConditionalCompareInstruction(Register src1, Register src2, int uimm4, ConditionFlag condition, InstructionType type) {
        assert NumUtil.isUnsignedNbit(4, uimm4);
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        emitInt(type.encoding | Instruction.FCCMP.encoding | uimm4 | condition.encoding << ConditionalConditionOffset | rs1(src1) | rs2(src2));
    }

    /**
     * Compare register to 0.0 .
     *
     * @param size register size.
     * @param src floating point register. May not be null.
     */
    public void fcmpZero(int size, Register src) {
        fcmpZeroInstruction(src, floatFromSize(size));
    }

    private void fcmpZeroInstruction(Register src, InstructionType type) {
        assert src.getRegisterCategory().equals(SIMD);
        int instrEncoding = Instruction.FCMPZERO.encoding | FpCmpOp;
        emitInt(type.encoding | instrEncoding | rs1(src));
    }

    /* Floating-point Conditional Select (5.7.11) */

    /**
     * Conditional select. dst = src1 if condition else src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param condition every condition allowed. May not be null.
     */
    protected void fcsel(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        fConditionalSelect(dst, src1, src2, condition, floatFromSize(size));
    }

    private void fConditionalSelect(Register dst, Register src1, Register src2, ConditionFlag condition, InstructionType type) {
        assert dst.getRegisterCategory().equals(SIMD);
        assert src1.getRegisterCategory().equals(SIMD);
        assert src2.getRegisterCategory().equals(SIMD);
        emitInt(type.encoding | Instruction.FCSEL.encoding | rd(dst) | rs1(src1) | rs2(src2) | condition.encoding << ConditionalConditionOffset);
    }

    /* Debug exceptions (5.9.1.2) */

    /**
     * Halting mode software breakpoint: Enters halting mode debug state if enabled, else treated as
     * UNALLOCATED instruction.
     *
     * @param uimm16 Arbitrary 16-bit unsigned payload.
     */
    protected void hlt(int uimm16) {
        exceptionInstruction(uimm16, Instruction.HLT);
    }

    /**
     * Monitor mode software breakpoint: exception routed to a debug monitor executing in a higher
     * exception level.
     *
     * @param uimm16 Arbitrary 16-bit unsigned payload.
     */
    protected void brk(int uimm16) {
        exceptionInstruction(uimm16, Instruction.BRK);
    }

    /* Architectural hints (5.9.4) */
    public enum SystemHint {
        NOP(0x0),
        YIELD(0x1),
        WFE(0x2),
        WFI(0x3),
        SEV(0x4),
        SEVL(0x5);

        private final int encoding;

        private SystemHint(int encoding) {
            this.encoding = encoding;
        }
    }

    /**
     * Architectural hints.
     *
     * @param hint Can be any of the defined hints. May not be null.
     */
    protected void hint(SystemHint hint) {
        emitInt(Instruction.HINT.encoding | hint.encoding << SystemImmediateOffset);
    }

    private void exceptionInstruction(int uimm16, Instruction instr) {
        assert NumUtil.isUnsignedNbit(16, uimm16);
        int instrEncoding = instr.encoding | ExceptionOp;
        emitInt(instrEncoding | uimm16 << SystemImmediateOffset);
    }

    /**
     * Clear Exclusive: clears the local record of the executing processor that an address has had a
     * request for an exclusive access.
     */
    protected void clrex() {
        emitInt(Instruction.CLREX.encoding);
    }

    /**
     * Possible barrier definitions for Aarch64. LOAD_LOAD and LOAD_STORE map to the same underlying
     * barrier.
     *
     * We only need synchronization across the inner shareable domain (see B2-90 in the Reference
     * documentation).
     */
    public enum BarrierKind {
        LOAD_LOAD(0x9, "ISHLD"),
        LOAD_STORE(0x9, "ISHLD"),
        STORE_STORE(0xA, "ISHST"),
        ANY_ANY(0xB, "ISH");

        public final int encoding;
        public final String optionName;

        private BarrierKind(int encoding, String optionName) {
            this.encoding = encoding;
            this.optionName = optionName;
        }
    }

    /**
     * Data Memory Barrier.
     *
     * @param barrierKind barrier that is issued. May not be null.
     */
    public void dmb(BarrierKind barrierKind) {
        barrierInstruction(barrierKind, Instruction.DMB);
    }

    private void barrierInstruction(BarrierKind barrierKind, Instruction instr) {
        int instrEncoding = instr.encoding | BarrierOp;
        emitInt(instrEncoding | barrierKind.encoding << BarrierKindOffset);
    }

    // Artificial instructions for simulator. These instructions are illegal in the normal aarch64
    // ISA,
    // but have special meaning for the simulator

    /**
     * Branch and link register instruction with the target code being native, i.e. not aarch64.
     *
     * The simulator has to do extra work so needs to know the number of arguments (both gp and fp)
     * as well as the type of the return value. See assembler_aarch64.hpp.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer. Contains
     *            address of target method.
     * @param gpArgs number of general purpose arguments passed to the function. 4-bit unsigned.
     * @param fpArgs number of floating point arguments passed to the function. 4-bit unsigned.
     * @param returnType returnType of function. May not be null, or Kind.ILLEGAL.
     */
    public void blrNative(Register reg, int gpArgs, int fpArgs, JavaKind returnType) {
        assert reg.getRegisterCategory().equals(CPU) && NumUtil.isUnsignedNbit(4, gpArgs) && NumUtil.isUnsignedNbit(4, fpArgs) && returnType != null;
        emitInt(Instruction.BLR_NATIVE.encoding | reg.encoding | getReturnTypeEncoding(returnType) << 5 | fpArgs << 7 | gpArgs << 11);
    }

    private static int getReturnTypeEncoding(JavaKind returnType) {
        // See assembler_aarch64.hpp for encoding details
        switch (returnType) {
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
            case Long:
            case Object:
                return 1;
            case Float:
                return 2;
            case Double:
                return 3;
            case Void:
            case Illegal:
                // Void functions use a result of Kind.Illegal apparently
                return 0;
            default:
                throw JVMCIError.shouldNotReachHere("Illegal kind");
        }
    }

    /* Helper functions */
    private static int rd(Register reg) {
        return reg.encoding << RdOffset;
    }

    private static int rs1(Register reg) {
        return reg.encoding << Rs1Offset;
    }

    private static int rs2(Register reg) {
        return reg.encoding << Rs2Offset;
    }

    private static int rs3(Register reg) {
        return reg.encoding << Rs3Offset;
    }

    private static int rt(Register reg) {
        return reg.encoding << RtOffset;
    }

}
