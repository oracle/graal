/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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
package org.graalvm.compiler.asm.aarch64;

import static jdk.vm.ci.aarch64.AArch64.CPU;
import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.aarch64.AArch64.cpuRegisters;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifyRegistersF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifyRegistersFF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifyRegistersR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifyRegistersRP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifyRegistersRRR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifyRegistersRRRZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifyRegistersRZP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifyRegistersZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifyRegistersZP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifyRegistersZRP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersFF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersFFF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersFFFF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersFZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersPP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersPPZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersPR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersPZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersRF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersRR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersRRR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersRZR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersRZZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersZP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersZZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeAndRegistersZZZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizeRegistersZPZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizesAndRegistersFF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizesAndRegistersFZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Asserts.verifySizesAndRegistersRF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ADC;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ADCS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ADD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ADDS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ADR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ADRP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.AND;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ANDS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ASRV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BFM;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BIC;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BICS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BLR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.BRK;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CAS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CCMP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CLREX;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CLS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CLZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CSEL;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CSINC;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.CSNEG;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.DC;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.DMB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.DSB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.EON;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.EOR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.EXTR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FABS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FADD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCCMP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCMP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCMPZERO;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCSEL;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCVTDS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCVTSD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FCVTZS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FDIV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FMADD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FMAX;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FMIN;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FMOV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FMSUB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FMUL;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FNEG;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FRINTM;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FRINTN;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FRINTP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FRINTZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FSQRT;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.FSUB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.HINT;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.HLT;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ISB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDADD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDAR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDAXR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDRS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LDXR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LSLV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.LSRV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MADD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MOVK;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MOVN;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MOVZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MRS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.MSUB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ORN;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.ORR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.RBIT;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.RET;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.REVW;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.REVX;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.RORV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SBC;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SBCS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SBFM;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SCVTF;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SDIV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.STLR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.STLXR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.STP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.STR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.STXR;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SUB;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SUBS;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.SWP;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.TBNZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.TBZ;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.UBFM;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.Instruction.UDIV;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.FP32;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.FP64;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.General32;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.General64;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.floatFromSize;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.InstructionType.generalFromSize;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.EnumSet;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64.CPUFeature;
import jdk.vm.ci.aarch64.AArch64.Flag;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

/**
 * This class encapsulates the AArch64 general-purpose and scalar floating-point instruction
 * assembler support. The documentation below heavily references the Arm Architecture Reference
 * Manual version G-a. The latest copy of the manual can be found
 * <a href="https://developer.arm.com/documentation/ddi0487/latest">here</a>.
 */
public abstract class AArch64Assembler extends Assembler<CPUFeature> {

    private final EnumSet<Flag> flags;

    public static class LogicalBitmaskImmediateEncoding {

        private static final Immediate[] IMMEDIATE_TABLE = buildImmediateTable();

        private static final int ImmediateOffset = 10;
        private static final int ImmediateRotateOffset = 16;
        private static final int ImmediateSizeOffset = 22;

        /**
         * Tests whether an immediate can be encoded for logical instructions.
         *
         * @param is64bit if true immediate part of a 64-bit instruction, false if part of a 32-bit
         *            instruction.
         * @return whether the value can be encoded.
         */
        protected static boolean canEncode(boolean is64bit, long immediate) {
            assert is64bit || NumUtil.isUnsignedNbit(32, immediate);

            int pos = getImmediateTablePosition(is64bit, immediate);
            return pos >= 0;
        }

        /**
         * Returns the immediate bitmask encoding for the requested value. Assumes that an encoding
         * is available.
         */
        public static int getEncoding(boolean is64bit, long value) {
            assert is64bit || NumUtil.isUnsignedNbit(32, value);

            int pos = getImmediateTablePosition(is64bit, value);
            assert pos >= 0 : "Value cannot be represented as logical immediate: " + value + ", is64bit=" + is64bit;
            Immediate imm = IMMEDIATE_TABLE[pos];
            assert is64bit || !imm.only64Bit() : "Immediate can only be represented for 64bit, but 32bit instruction specified";
            return IMMEDIATE_TABLE[pos].encoding;
        }

        /**
         * @param is64bit if true immediate part of a 64-bit instruction, false if part of a 32-bit
         *            instruction.
         * @return If positive the return value is the position into the IMMEDIATE_TABLE for the
         *         given immediate, if negative the immediate cannot be encoded.
         */
        private static int getImmediateTablePosition(boolean is64bit, long value) {
            assert is64bit || NumUtil.isUnsignedNbit(32, value);

            Immediate imm;
            if (!is64bit) {
                /*
                 * If we have a 32bit instruction (and therefore immediate) we have to duplicate it
                 * across 64bit to find it in the table.
                 */
                imm = new Immediate(value << 32 | value);
            } else {
                imm = new Immediate(value);
            }
            int pos = Arrays.binarySearch(IMMEDIATE_TABLE, imm);
            if (pos < 0) {
                return -1;
            }
            if (!is64bit && IMMEDIATE_TABLE[pos].only64Bit()) {
                return -1;
            }
            return pos;
        }

        /**
         * To quote 5.4.2: [..] an immediate is a 32 or 64 bit pattern viewed as a vector of
         * identical elements of size e = 2, 4, 8, 16, 32 or (in the case of bimm64) 64 bits. Each
         * element contains the same sub-pattern: a single run of 1 to e-1 non-zero bits, rotated by
         * 0 to e-1 bits. It is encoded in the following: 21..16: rotation amount (6bit) starting
         * from 1s in the LSB (i.e. 0111->1011->1101->1110) 22:15..10: This stores a combination of
         * the number of set bits and the pattern size. The pattern size is encoded as follows (x is
         * used to store the number of 1 bits - 1) e pattern 2 0_11110x 4 0_1110xx 8 0_110xxx 16
         * 0_10xxxx 32 0_0xxxxx 64 1_xxxxxx.
         */
        private static final class Immediate implements Comparable<Immediate> {
            public final long imm;
            public final boolean isOnly64Bit;
            public final int encoding;

            Immediate(long imm, boolean isOnly64Bit, int s, int r) {
                this.imm = imm;
                this.isOnly64Bit = isOnly64Bit;
                this.encoding = computeEncoding(isOnly64Bit, s, r);
            }

            // Used to be able to binary search for an immediate in the table.
            Immediate(long imm) {
                this(imm, false, 0, 0);
            }

            /**
             * Returns true if this pattern is only representable as 64bit.
             */
            public boolean only64Bit() {
                return isOnly64Bit;
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
            final int[] elementSizeEncodings = {
                            /* 2 */ 0b111100,
                            /* 4 */ 0b111000,
                            /* 8 */ 0b110000,
                            /* 16 */ 0b100000,
                            /* 32 */ 0b000000,
                            /* 64 */ 0b000000,
            };
            final Immediate[] table = new Immediate[nrImmediates];
            int nrImms = 0;
            for (int logE = 1; logE <= 6; logE++) {
                /* e specifies the element size. */
                int e = 1 << logE;
                long mask = NumUtil.getNbitNumberLong(e);
                for (int nrOnes = 1; nrOnes < e; nrOnes++) {
                    long val = (1L << nrOnes) - 1;
                    /* r specifies how much we rotate the value. */
                    for (int r = 0; r < e; r++) {
                        long immediate = (val >>> r | val << (e - r)) & mask;
                        /* Duplicate pattern to fill whole 64bit range. */
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
                            case 6:
                                /* No duplication needed (element size is 64bits). */
                                break;
                        }
                        int s = elementSizeEncodings[logE - 1] | (nrOnes - 1);
                        table[nrImms++] = new Immediate(immediate, /* isOnly64Bit */e == 64, s, r);
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

    /**
     * This class encapsulates input checks for general-purpose and floating-point AArch64
     * instructions.
     *
     * To help highlight the expectations of each instruction. The following convention is used:
     * <ul>
     * <li>verifyRegisters[XYZ]: checks the register operands are of the appropriate category as
     * defined by [XYZ] using the below naming convention
     * <li>verifySize(s)AndRegisters[XYZ]: checks that the size of the operation is either 32 or 64
     * bits and then calls the matching verifyRegisters[XYZ] call.
     * </ul>
     *
     * The naming convention for the expected type of each register is as follows:
     * <ul>
     * <li>R: General-purpose registers x0-x30. Neither the zero register (zr) or stack pointer
     * register (sp).
     * <li>Z: General-purpose registers x0-x30 or the zero register (zr).
     * <li>P: General-purpose registers x0-x30 or the stack pointer register (sp).
     * <li>F: Floating-point registers v0-v31.
     * </ul>
     */
    static class Asserts {

        static boolean checkSize32Or64(int size) {
            assert size == 32 || size == 64 : String.format("Expected size 32 or 64: %s", size);
            return true;
        }

        static boolean checkRegR(Register reg) {
            assert reg != null : "Register is null";
            assert reg.getRegisterCategory().equals(CPU) : String.format("General-purpose register expected: %s", reg);
            assert !reg.equals(zr) : "Unexpected zero register (zr) found";
            assert !reg.equals(sp) : "Unexpected stack pointer register (sp) found";
            return true;
        }

        static boolean checkRegZ(Register reg) {
            assert reg != null : "Register is null";
            assert reg.getRegisterCategory().equals(CPU) : String.format("General-purpose register expected: %s", reg);
            assert !reg.equals(sp) : "Unexpected stack pointer register (sp) found";
            return true;
        }

        static boolean checkRegP(Register reg) {
            assert reg != null : "Register is null";
            assert reg.getRegisterCategory().equals(CPU) : String.format("General-purpose register expected: %s", reg);
            assert !reg.equals(zr) : "Unexpected zero register (zr) found";
            return true;
        }

        static boolean checkRegF(Register reg) {
            assert reg != null : "Register is null";
            assert reg.getRegisterCategory().equals(SIMD) : String.format("Floating-point register expected: %s", reg);
            return true;
        }

        static boolean verifyRegistersR(Register reg) {
            assert checkRegR(reg);
            return true;
        }

        static boolean verifySizeAndRegistersR(int size, Register reg) {
            assert checkSize32Or64(size);
            assert verifyRegistersR(reg);
            return true;
        }

        static boolean verifyRegistersZ(Register reg) {
            assert checkRegZ(reg);
            return true;
        }

        static boolean verifyRegistersF(Register reg) {
            assert checkRegF(reg);
            return true;
        }

        static boolean verifySizeAndRegistersF(int size, Register reg) {
            assert checkSize32Or64(size);
            assert verifyRegistersF(reg);
            return true;
        }

        static boolean verifyRegistersRR(Register reg1, Register reg2) {
            assert checkRegR(reg1);
            assert checkRegR(reg2);
            return true;
        }

        static boolean verifySizeAndRegistersRR(int size, Register reg1, Register reg2) {
            assert checkSize32Or64(size);
            assert verifyRegistersRR(reg1, reg2);
            return true;
        }

        static boolean verifyRegistersRP(Register reg1, Register reg2) {
            assert checkRegR(reg1);
            assert checkRegP(reg2);
            return true;
        }

        static boolean verifyRegistersRF(Register reg1, Register reg2) {
            assert checkRegR(reg1);
            assert checkRegF(reg2);
            return true;
        }

        static boolean verifySizeAndRegistersRF(int size, Register reg1, Register reg2) {
            assert checkSize32Or64(size);
            assert verifyRegistersRF(reg1, reg2);
            return true;
        }

        static boolean verifySizesAndRegistersRF(int size1, int size2, Register reg1, Register reg2) {
            assert checkSize32Or64(size1);
            assert checkSize32Or64(size2);
            assert verifyRegistersRF(reg1, reg2);
            return true;
        }

        static boolean verifyRegistersZZ(Register reg1, Register reg2) {
            assert checkRegZ(reg1);
            assert checkRegZ(reg2);
            return true;
        }

        static boolean verifySizeAndRegistersZZ(int size, Register reg1, Register reg2) {
            assert checkSize32Or64(size);
            assert verifyRegistersZZ(reg1, reg2);
            return true;
        }

        static boolean verifyRegistersZP(Register reg1, Register reg2) {
            assert checkRegZ(reg1);
            assert checkRegP(reg2);
            return true;
        }

        static boolean verifySizeAndRegistersZP(int size, Register reg1, Register reg2) {
            assert checkSize32Or64(size);
            assert verifyRegistersZP(reg1, reg2);
            return true;
        }

        static boolean verifyRegistersPR(Register reg1, Register reg2) {
            assert checkRegP(reg1);
            assert checkRegR(reg2);
            return true;
        }

        static boolean verifySizeAndRegistersPR(int size, Register reg1, Register reg2) {
            assert checkSize32Or64(size);
            assert verifyRegistersPR(reg1, reg2);
            return true;
        }

        static boolean verifyRegistersPZ(Register reg1, Register reg2) {
            assert checkRegP(reg1);
            assert checkRegZ(reg2);
            return true;
        }

        static boolean verifySizeAndRegistersPZ(int size, Register reg1, Register reg2) {
            assert checkSize32Or64(size);
            assert verifyRegistersPZ(reg1, reg2);
            return true;
        }

        static boolean verifyRegistersPP(Register reg1, Register reg2) {
            assert checkRegP(reg1);
            assert checkRegP(reg2);
            return true;
        }

        static boolean verifySizeAndRegistersPP(int size, Register reg1, Register reg2) {
            assert checkSize32Or64(size);
            assert verifyRegistersPP(reg1, reg2);
            return true;
        }

        static boolean verifyRegistersFZ(Register reg1, Register reg2) {
            assert checkRegF(reg1);
            assert checkRegZ(reg2);
            return true;
        }

        static boolean verifySizeAndRegistersFZ(int size, Register reg1, Register reg2) {
            assert checkSize32Or64(size);
            assert verifyRegistersFZ(reg1, reg2);
            return true;
        }

        static boolean verifySizesAndRegistersFZ(int size1, int size2, Register reg1, Register reg2) {
            assert checkSize32Or64(size1);
            assert checkSize32Or64(size2);
            assert verifyRegistersFZ(reg1, reg2);
            return true;
        }

        static boolean verifyRegistersFF(Register reg1, Register reg2) {
            assert checkRegF(reg1);
            assert checkRegF(reg2);
            return true;
        }

        static boolean verifySizeAndRegistersFF(int size, Register reg1, Register reg2) {
            assert checkSize32Or64(size);
            assert verifyRegistersFF(reg1, reg2);
            return true;
        }

        static boolean verifySizesAndRegistersFF(int size1, int size2, Register reg1, Register reg2) {
            assert checkSize32Or64(size1);
            assert checkSize32Or64(size2);
            assert verifyRegistersFF(reg1, reg2);
            return true;
        }

        static boolean verifyRegistersRRR(Register reg1, Register reg2, Register reg3) {
            assert checkRegR(reg1);
            assert checkRegR(reg2);
            assert checkRegR(reg3);
            return true;
        }

        static boolean verifySizeAndRegistersRRR(int size, Register reg1, Register reg2, Register reg3) {
            assert checkSize32Or64(size);
            assert verifyRegistersRRR(reg1, reg2, reg3);
            return true;
        }

        static boolean verifyRegistersRZR(Register reg1, Register reg2, Register reg3) {
            assert checkRegR(reg1);
            assert checkRegZ(reg2);
            assert checkRegR(reg3);
            return true;
        }

        static boolean verifySizeAndRegistersRZR(int size, Register reg1, Register reg2, Register reg3) {
            assert checkSize32Or64(size);
            assert verifyRegistersRZR(reg1, reg2, reg3);
            return true;
        }

        static boolean verifyRegistersRZZ(Register reg1, Register reg2, Register reg3) {
            assert checkRegR(reg1);
            assert checkRegZ(reg2);
            assert checkRegZ(reg3);
            return true;
        }

        static boolean verifySizeAndRegistersRZZ(int size, Register reg1, Register reg2, Register reg3) {
            assert checkSize32Or64(size);
            assert verifyRegistersRZZ(reg1, reg2, reg3);
            return true;
        }

        static boolean verifyRegistersRZP(Register reg1, Register reg2, Register reg3) {
            assert checkRegR(reg1);
            assert checkRegZ(reg2);
            assert checkRegP(reg3);
            return true;
        }

        static boolean verifyRegistersZRP(Register reg1, Register reg2, Register reg3) {
            assert checkRegZ(reg1);
            assert checkRegR(reg2);
            assert checkRegP(reg3);
            return true;
        }

        static boolean verifyRegistersZZZ(Register reg1, Register reg2, Register reg3) {
            assert checkRegZ(reg1);
            assert checkRegZ(reg2);
            assert checkRegZ(reg3);
            return true;
        }

        static boolean verifySizeAndRegistersZZZ(int size, Register reg1, Register reg2, Register reg3) {
            assert checkSize32Or64(size);
            assert verifyRegistersZZZ(reg1, reg2, reg3);
            return true;
        }

        static boolean verifyRegistersZPZ(Register reg1, Register reg2, Register reg3) {
            assert checkRegZ(reg1);
            assert checkRegP(reg2);
            assert checkRegZ(reg3);
            return true;
        }

        static boolean verifySizeRegistersZPZ(int size, Register reg1, Register reg2, Register reg3) {
            assert checkSize32Or64(size);
            assert verifyRegistersZPZ(reg1, reg2, reg3);
            return true;
        }

        static boolean verifyRegistersPPZ(Register reg1, Register reg2, Register reg3) {
            assert checkRegP(reg1);
            assert checkRegP(reg2);
            assert checkRegZ(reg3);
            return true;
        }

        static boolean verifySizeAndRegistersPPZ(int size, Register reg1, Register reg2, Register reg3) {
            assert checkSize32Or64(size);
            assert verifyRegistersPPZ(reg1, reg2, reg3);
            return true;
        }

        static boolean verifyRegistersFFF(Register reg1, Register reg2, Register reg3) {
            assert checkRegF(reg1);
            assert checkRegF(reg2);
            assert checkRegF(reg3);
            return true;
        }

        static boolean verifySizeAndRegistersFFF(int size, Register reg1, Register reg2, Register reg3) {
            assert checkSize32Or64(size);
            assert verifyRegistersFFF(reg1, reg2, reg3);
            return true;
        }

        static boolean verifyRegistersRRRZ(Register reg1, Register reg2, Register reg3, Register reg4) {
            assert checkRegR(reg1);
            assert checkRegR(reg2);
            assert checkRegR(reg3);
            assert checkRegZ(reg4);
            return true;
        }

        static boolean verifyRegistersFFFF(Register reg1, Register reg2, Register reg3, Register reg4) {
            assert checkRegF(reg1);
            assert checkRegF(reg2);
            assert checkRegF(reg3);
            assert checkRegF(reg4);
            return true;
        }

        static boolean verifySizeAndRegistersFFFF(int size, Register reg1, Register reg2, Register reg3, Register reg4) {
            assert checkSize32Or64(size);
            assert verifyRegistersFFFF(reg1, reg2, reg3, reg4);
            return true;
        }

    }

    static class ImmediateOpChecks {
        static void validateSigned(int numBits, int imm) {
            GraalError.guarantee(NumUtil.isSignedNbit(numBits, imm), "Immediate has to be %sbit signed number. Got value 0x%x", numBits, imm);
        }

        static void validateUnsigned(int numBits, int uimm) {
            GraalError.guarantee(NumUtil.isUnsignedNbit(numBits, uimm), "Immediate has to be %sbit unsigned number. Got value 0x%x", numBits, uimm);
        }

        static void validate4ByteAlignment(int value) {
            GraalError.guarantee((value & 0b11) == 0, "Expected 4-byte alignment. Got value 0x%x", value);
        }
    }

    private static final int RdOffset = 0;
    private static final int Rs1Offset = 5;
    private static final int Rs2Offset = 16;
    private static final int Rs3Offset = 10;
    private static final int RtOffset = 0;
    private static final int RnOffset = 5;
    private static final int Rt2Offset = 10;

    /* Helper functions */
    static int rd(Register reg) {
        return reg.encoding << RdOffset;
    }

    static int rs1(Register reg) {
        return reg.encoding << Rs1Offset;
    }

    static int rs2(Register reg) {
        return reg.encoding << Rs2Offset;
    }

    private static int rs3(Register reg) {
        return reg.encoding << Rs3Offset;
    }

    private static int rt(Register reg) {
        return reg.encoding << RtOffset;
    }

    private static int rt2(Register reg) {
        return reg.encoding << Rt2Offset;
    }

    static int rn(Register reg) {
        return reg.encoding << RnOffset;
    }

    /**
     * Enumeration of all different instruction kinds: General32/64 are the general instructions
     * (integer, branch, etc.), for 32-, respectively 64-bit operands. FP32/64 is the encoding for
     * the 32/64bit float operations.
     */
    protected enum InstructionType {
        General32(0b00 << 30, 32, true),
        General64(0b10 << 30, 64, true),
        FP32(0x00000000, 32, false),
        FP64(0x00400000, 64, false);

        public final int encoding;
        public final int width;
        public final boolean isGeneral;

        InstructionType(int encoding, int width, boolean isGeneral) {
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
    private static final int AddSubShift12 = 0b01 << 22;
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
    private static final int SignedMulLongOp = 0x9B200000;
    private static final int DataProcessing1SourceOp = 0x5AC00000;
    private static final int DataProcessing2SourceOp = 0x1AC00000;

    private static final int Fp1SourceOp = 0x1E204000;
    private static final int Fp2SourceOp = 0x1E200800;
    private static final int Fp3SourceOp = 0x1F000000;

    private static final int FpConvertOp = 0x1E200000;
    private static final int FpImmOp = 0x1E201000;
    private static final int FpImmOffset = 13;

    private static final int FpCmpOp = 0x1E202000;
    private static final int FpCmpeOp = 0x1E202010;

    private static final int PcRelImmHiOffset = 5;
    private static final int PcRelImmLoOffset = 29;

    private static final int PcRelImmOp = 0x10000000;

    private static final int UnconditionalBranchImmOp = 0x14000000;
    private static final int UnconditionalBranchRegOp = 0xD6000000;
    private static final int CompareBranchOp = 0x34000000;

    private static final int ConditionalBranchImmOffset = 5;

    private static final int ConditionalSelectOp = 0x1A800000;
    private static final int ConditionalConditionOffset = 12;

    private static final int LoadStoreScaledOp = 0b111_0_01_0_0_0 << 21;
    private static final int LoadStoreUnscaledOp = 0b111_0_00_0_0_0 << 21;
    private static final int LoadStorePostIndexedOp = LoadStoreUnscaledOp | 0b01 << 10;
    private static final int LoadStorePreIndexedOp = LoadStoreUnscaledOp | 0b11 << 10;
    private static final int LoadStoreRegisterOp = 0b111_0_00_0_0_1 << 21 | 0b10 << 10;
    private static final int LoadLiteralOp = 0x18000000;

    private static final int LoadStoreUnscaledImmOffset = 12;
    private static final int LoadStoreScaledImmOffset = 10;
    private static final int LoadStoreScaledRegOffset = 12;
    private static final int LoadStoreIndexedImmOffset = 12;
    private static final int LoadStoreTransferSizeOffset = 30;
    private static final int LoadStoreQuadWordTransferSizeOffset = 23;
    private static final int LoadStoreFpFlagOffset = 26;
    private static final int LoadLiteralImmOffset = 5;
    protected static final int LoadFlag = 0b1 << 22;

    private static final int LoadStorePairSignedOffsetOp = 0b101_0_010 << 23;
    private static final int LoadStorePairPostIndexOp = 0b101_0_001 << 23;
    private static final int LoadStorePairPreIndexOp = 0b101_0_011 << 23;
    private static final int LoadStorePairImm7Offset = 15;

    private static final int LogicalShiftOp = 0x0A000000;

    private static final int ExceptionOp = 0xD4000000;
    private static final int SystemImmediateOffset = 5;

    @SuppressWarnings("unused") private static final int SimdImmediateOffset = 16;

    private static final int BarrierOp = 0xD503301F;
    private static final int BarrierKindOffset = 8;

    private static final int CASAcquireOffset = 22;
    private static final int CASReleaseOffset = 15;

    private static final int LDADDAcquireOffset = 23;
    private static final int LDADDReleaseOffset = 22;

    /**
     * Encoding for all base and floating-point instructions.
     */
    public enum Instruction {
        BCOND(0x54000000),
        CBNZ(0x01000000),
        CBZ(0x00000000),
        TBZ(0x36000000),
        TBNZ(0x37000000),

        B(0x00000000),
        BL(0x80000000),
        BR(0x001F0000),
        BLR(0x003F0000),
        RET(0x005F0000),

        /*
         * This instruction does not automatically set the LoadFlag, since it is not set in the
         * PC-literal addressing mode. Note that this instruction is also used for prefetching
         * instructions (see prfm(AArch64Address, PrefetchMode) for more info).
         */
        LDR(0x00000000),
        /*
         * This instruction does not set the LoadFlag, as this bit is instead part of the target
         * size.
         */
        LDRS(0x00800000),
        LDXR(0x08000000 | LoadFlag | 0x1F << Rs2Offset | 0x1F << Rt2Offset),
        LDAR(0x08808000 | LoadFlag | 0x1F << Rs2Offset | 0x1F << Rt2Offset),
        LDAXR(0x08008000 | LoadFlag | 0x1F << Rs2Offset | 0x1F << Rt2Offset),

        STR(0x00000000),
        STXR(0x08000000 | 0x1F << Rt2Offset),
        STLR(0x08808000 | 0x1F << Rs2Offset | 0x1F << Rt2Offset),
        STLXR(0x08008000 | 0x1F << Rt2Offset),

        LDP(LoadFlag),
        STP(0x00000000),

        CAS(0x08A07C00),
        LDADD(0x38200000),
        SWP(0x38208000),

        ADR(0x00000000),
        ADRP(0x80000000),

        ADD(0x00000000),
        ADDS(ADD.encoding | AddSubSetFlag),
        SUB(0x40000000),
        SUBS(SUB.encoding | AddSubSetFlag),

        ADC(0b00 << 29),
        ADCS(0b01 << 29),
        SBC(0b10 << 29),
        SBCS(0b11 << 29),

        CCMP(0x7A400000),

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

        FRINTM(0x00050000),
        FRINTN(0x00040000),
        FRINTP(0x00048000),
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

        HLT(0x00400000),
        BRK(0x00200000),

        CLREX(0xD5033F5F),
        HINT(0xD503201F),
        DMB(0x000000A0),
        DSB(0x00000080),

        MRS(0xD5300000),
        MSR(0xD5100000),
        DC(0xD5087000),
        ISB(0x000000C0),

        BLR_NATIVE(0xC0000000);

        public final int encoding;

        Instruction(int encoding) {
            this.encoding = encoding;
        }

    }

    public enum SystemRegister {
        FPCR(0b11, 0b011, 0b0100, 0b0100, 0b000),
        FPSR(0b11, 0b011, 0b0100, 0b0100, 0b001),
        /* Counter-timer Virtual Count register */
        CNTVCT_EL0(0b11, 0b011, 0b110, 0b0000, 0b010);

        SystemRegister(int op0, int op1, int crn, int crm, int op2) {
            this.op0 = op0;
            this.op1 = op1;
            this.crn = crn;
            this.crm = crm;
            this.op2 = op2;
        }

        public int encoding() {
            return op0 << 19 | op1 << 16 | crn << 12 | crm << 8 | op2 << 5;
        }

        private final int op0;
        private final int op1;
        private final int crn;
        private final int crm;
        private final int op2;
    }

    public enum DataCacheOperationType {
        ZVA(0b011, 0b0100, 0b001),
        CVAP(0b011, 0b1100, 0b001);

        DataCacheOperationType(int op1, int crm, int op2) {
            this.op1 = op1;
            this.crm = crm;
            this.op2 = op2;
        }

        public int encoding() {
            return op1 << 16 | crm << 8 | op2 << 5;
        }

        private final int op1;
        private final int crm;
        private final int op2;
    }

    public enum ShiftType {
        /** Logical shift left. */
        LSL(0),
        /** Logical shift right. */
        LSR(1),
        /** Arithmetic shift right. */
        ASR(2),
        /** Rotate right. */
        ROR(3);

        public final int encoding;

        ShiftType(int encoding) {
            this.encoding = encoding;
        }
    }

    public enum ExtendType {

        /** Unsigned extend low byte (8 bit). */
        UXTB(0),

        /** Unsigned extend low halfword (16 bit). */
        UXTH(1),

        /** Unsigned extend low word (32 bit). */
        UXTW(2),

        /** Unsigned extend low doubleword (64 bit). */
        UXTX(3),

        /** Signed extend low byte (8 bit). */
        SXTB(4),

        /** Signed extend low halfword (16 bit). */
        SXTH(5),

        /** Signed extend low word (32 bit). */
        SXTW(6),

        /** Signed extend low doubleword (64 bit). */
        SXTX(7);

        public final int encoding;

        ExtendType(int encoding) {
            this.encoding = encoding;
        }
    }

    /**
     * Condition Flags for branches. See C1.2.4
     */
    public enum ConditionFlag {
        // Integer | Floating-point meanings
        /** Equal | Equal. */
        EQ(0x0),

        /** Not Equal | Not equal or unordered. */
        NE(0x1),

        /** Unsigned Higher or Same | Greater than, equal or unordered. */
        HS(0x2),

        /** Unsigned lower | less than. */
        LO(0x3),

        /** Minus (negative) | less than. */
        MI(0x4),

        /** Plus (positive or zero) | greater than, equal or unordered. */
        PL(0x5),

        /** Overflow set | unordered. */
        VS(0x6),

        /** Overflow clear | ordered. */
        VC(0x7),

        /** Unsigned higher | greater than or unordered. */
        HI(0x8),

        /** Unsigned lower or same | less than or equal. */
        LS(0x9),

        /** Signed greater than or equal | greater than or equal. */
        GE(0xA),

        /** Signed less than | less than or unordered. */
        LT(0xB),

        /** Signed greater than | greater than. */
        GT(0xC),

        /** Signed less than or equal | less than, equal or unordered. */
        LE(0xD),

        /** Always | always. */
        AL(0xE),

        /** Always | always (identical to AL, just to have valid 0b1111 encoding). */
        NV(0xF);

        public final int encoding;

        ConditionFlag(int encoding) {
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
                    throw GraalError.shouldNotReachHere();
            }
        }
    }

    public AArch64Assembler(TargetDescription target) {
        super(target, ((AArch64) target.arch).getFeatures().clone());
        this.flags = ((AArch64) target.arch).getFlags();
    }

    public final EnumSet<Flag> getFlags() {
        return flags;
    }

    public boolean supports(CPUFeature feature) {
        return getFeatures().contains(feature);
    }

    public boolean isFlagSet(Flag flag) {
        return getFlags().contains(flag);
    }

    /* Conditional Branch (5.2.1) */

    /**
     * C6.2.25 Branch conditionally.
     *
     * @param condition may not be null.
     * @param imm21 Signed 21-bit offset, has to be 4-byte aligned.
     */
    protected void b(ConditionFlag condition, int imm21) {
        b(condition, imm21, -1);
    }

    /**
     * C6.2.25 Branch conditionally. Inserts instruction into code buffer at pos.
     *
     * @param condition may not be null.
     * @param imm21 Signed 21-bit offset, has to be 4-byte aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void b(ConditionFlag condition, int imm21, int pos) {
        ImmediateOpChecks.validateSigned(21, imm21);
        ImmediateOpChecks.validate4ByteAlignment(imm21);

        int encoding = Instruction.BCOND.encoding | getConditionalBranchImm(imm21) | condition.encoding;
        if (pos == -1) {
            emitInt(encoding);
        } else {
            emitInt(encoding, pos);
        }
    }

    /**
     * C6.2.44 Compare register and branch if non-zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be 4-byte aligned.
     */
    protected void cbnz(int size, Register reg, int imm21) {
        assert verifySizeAndRegistersR(size, reg);
        ImmediateOpChecks.validateSigned(21, imm21);
        ImmediateOpChecks.validate4ByteAlignment(imm21);

        compareRegisterAndBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBNZ, -1);
    }

    /**
     * C6.2.44 Compare register and branch if non-zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be 4-byte aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void cbnz(int size, Register reg, int imm21, int pos) {
        assert verifySizeAndRegistersR(size, reg);
        ImmediateOpChecks.validateSigned(21, imm21);
        ImmediateOpChecks.validate4ByteAlignment(imm21);

        compareRegisterAndBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBNZ, pos);
    }

    /**
     * C6.2.45 Compare and branch if zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be 4-byte aligned.
     */
    protected void cbz(int size, Register reg, int imm21) {
        assert verifySizeAndRegistersR(size, reg);
        ImmediateOpChecks.validateSigned(21, imm21);
        ImmediateOpChecks.validate4ByteAlignment(imm21);

        compareRegisterAndBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBZ, -1);
    }

    /**
     * C6.2.45 Compare register and branch if zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param size Instruction size in bits. Should be either 32 or 64.
     * @param imm21 Signed 21-bit offset, has to be 4-byte aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void cbz(int size, Register reg, int imm21, int pos) {
        assert verifySizeAndRegistersR(size, reg);
        ImmediateOpChecks.validateSigned(21, imm21);
        ImmediateOpChecks.validate4ByteAlignment(imm21);

        compareRegisterAndBranchInstruction(reg, imm21, generalFromSize(size), Instruction.CBZ, pos);
    }

    /**
     * C6.2.330 Test a single bit and branch if the bit is nonzero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param uimm6 Unsigned 6-bit bit index.
     * @param imm16 signed 16 bit offset, has to be 4-byte aligned.
     */
    protected void tbnz(Register reg, int uimm6, int imm16) {
        tbnz(reg, uimm6, imm16, -1);
    }

    /**
     * C6.2.331 Test a single bit and branch if the bit is zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param uimm6 Unsigned 6-bit bit index.
     * @param imm16 signed 16 bit offset, has to be 4-byte aligned.
     */
    protected void tbz(Register reg, int uimm6, int imm16) {
        tbz(reg, uimm6, imm16, -1);
    }

    /**
     * C6.2.330 Test a single bit and branch if the bit is nonzero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param uimm6 Unsigned 6-bit bit index.
     * @param imm16 signed 16 bit offset, has to be 4-byte aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void tbnz(Register reg, int uimm6, int imm16, int pos) {
        assert verifyRegistersR(reg);
        ImmediateOpChecks.validateUnsigned(6, uimm6);
        ImmediateOpChecks.validateSigned(16, imm16);
        ImmediateOpChecks.validate4ByteAlignment(imm16);

        // size bit is overloaded as top bit of uimm6 bit index
        int size = (((uimm6 >> 5) & 1) == 0 ? 32 : 64);
        // remaining 5 bits are encoded lower down
        int uimm5 = uimm6 & 0x1F;
        int imm14 = (imm16 & NumUtil.getNbitNumberInt(16)) >> 2;
        InstructionType type = generalFromSize(size);
        int encoding = type.encoding | TBNZ.encoding | (uimm5 << 19) | (imm14 << 5) | rd(reg);
        if (pos == -1) {
            emitInt(encoding);
        } else {
            emitInt(encoding, pos);
        }
    }

    /**
     * C6.2.331 Test a single bit and branch if the bit is zero.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     * @param uimm6 Unsigned 6-bit bit index.
     * @param imm16 signed 16 bit offset, has to be 4-byte aligned.
     * @param pos Position at which instruction is inserted into buffer. -1 means insert at end.
     */
    protected void tbz(Register reg, int uimm6, int imm16, int pos) {
        assert verifyRegistersR(reg);
        ImmediateOpChecks.validateUnsigned(6, uimm6);
        ImmediateOpChecks.validateSigned(16, imm16);
        ImmediateOpChecks.validate4ByteAlignment(imm16);

        // size bit is overloaded as top bit of uimm6 bit index
        int size = (((uimm6 >> 5) & 1) == 0 ? 32 : 64);
        // remaining 5 bits are encoded lower down
        int uimm5 = uimm6 & 0x1F;
        int imm14 = (imm16 & NumUtil.getNbitNumberInt(16)) >> 2;
        InstructionType type = generalFromSize(size);
        int encoding = type.encoding | TBZ.encoding | (uimm5 << 19) | (imm14 << 5) | rd(reg);
        if (pos == -1) {
            emitInt(encoding);
        } else {
            emitInt(encoding, pos);
        }
    }

    private void compareRegisterAndBranchInstruction(Register reg, int imm21, InstructionType type, Instruction instr, int pos) {
        int instrEncoding = instr.encoding | CompareBranchOp;
        int encoding = type.encoding | instrEncoding | getConditionalBranchImm(imm21) | rd(reg);
        if (pos == -1) {
            emitInt(encoding);
        } else {
            emitInt(encoding, pos);
        }
    }

    private static int getConditionalBranchImm(int imm21) {
        // Note this condition is Guaranteed in the callers of this method.
        assert NumUtil.isSignedNbit(21, imm21) && (imm21 & 0b11) == 0 : String.format("Immediate has to be a 21-bit signed number and 4-byte aligned. Got value 0x%x", imm21);

        int imm = (imm21 & NumUtil.getNbitNumberInt(21)) >> 2;
        return imm << ConditionalBranchImmOffset;
    }

    /* C6.2.26 Unconditional Branch (immediate). */
    protected void b() {
        unconditionalBranchImmInstruction(0, Instruction.B, -1, true);
    }

    /**
     * C6.2.26 Unconditional Branch (immediate).
     *
     * @param imm28 Signed 28-bit offset, has to be word aligned.
     */
    protected void b(int imm28) {
        unconditionalBranchImmInstruction(imm28, Instruction.B, -1, false);
    }

    /**
     * C6.2.26 Unconditional Branch (immediate).
     *
     * @param imm28 Signed 28-bit offset, has to be word aligned.
     * @param pos Position where instruction is inserted into code buffer.
     */
    protected void b(int imm28, int pos) {
        unconditionalBranchImmInstruction(imm28, Instruction.B, pos, false);
    }

    /**
     * C6.2.33 Branch and link return address to register X30.
     */
    public void bl() {
        unconditionalBranchImmInstruction(0, Instruction.BL, -1, true);
    }

    private void unconditionalBranchImmInstruction(int imm28, Instruction instr, int pos, boolean needsImmAnnotation) {
        if (needsImmAnnotation) {
            annotatePatchingImmediate(pos == -1 ? position() : pos, instr, 26, 0, 2);
        }

        ImmediateOpChecks.validateSigned(28, imm28);
        ImmediateOpChecks.validate4ByteAlignment(imm28);

        int imm = (imm28 & NumUtil.getNbitNumberInt(28)) >> 2;
        int encoding = instr.encoding | UnconditionalBranchImmOp | imm;
        if (pos == -1) {
            emitInt(encoding);
        } else {
            emitInt(encoding, pos);
        }
    }

    /**
     * C6.2.34 Branches to address in register and writes return address into register X30.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     */
    public void blr(Register reg) {
        assert verifyRegistersR(reg);

        unconditionalBranchRegInstruction(BLR, reg);
    }

    /**
     * C6.2.36 Branches to address in register.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     */
    protected void br(Register reg) {
        assert verifyRegistersR(reg);

        unconditionalBranchRegInstruction(BR, reg);
    }

    /**
     * C6.2.220 Return to address in register.
     *
     * @param reg general purpose register. May not be null, zero-register or stackpointer.
     */
    public void ret(Register reg) {
        assert verifyRegistersR(reg);

        unconditionalBranchRegInstruction(RET, reg);
    }

    private void unconditionalBranchRegInstruction(Instruction instr, Register reg) {
        emitInt(instr.encoding | UnconditionalBranchRegOp | rs1(reg));

    }

    /**
     * Returns the log2 size of the number of bytes expected to be transferred.
     */
    public static int getLog2TransferSize(int bitMemoryTransferSize) {
        switch (bitMemoryTransferSize) {
            case 8:
                return 0;
            case 16:
                return 1;
            case 32:
                return 2;
            case 64:
                return 3;
            case 128:
                return 4;
        }
        throw GraalError.shouldNotReachHere("Unexpected transfer size.");
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
        ldrHelper(srcSize, rt, address, false);
    }

    protected final void ldrHelper(int srcSize, Register rt, AArch64Address address, boolean allowZeroReg) {
        assert srcSize == 8 || srcSize == 16 || srcSize == 32 || srcSize == 64;
        assert allowZeroReg ? verifyRegistersZ(rt) : verifyRegistersR(rt);

        /* When using an immediate or register based addressing mode, then the load flag is set. */
        int loadFlag = address.getAddressingMode() == AddressingMode.PC_LITERAL ? 0 : LoadFlag;
        loadStoreInstruction(LDR, rt, address, false, getLog2TransferSize(srcSize), loadFlag);
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
        assert srcSize == 8 || srcSize == 16 || srcSize == 32;
        assert targetSize == 32 || targetSize == 64;
        assert srcSize != targetSize;
        assert verifyRegistersR(rt);

        /* A flag is used to differentiate whether the value should be extended to 32 or 64 bits. */
        int target32BitFlag = targetSize == 32 ? 1 << 22 : 0;
        loadStoreInstruction(LDRS, rt, address, false, getLog2TransferSize(srcSize), target32BitFlag);
    }

    /**
     * The prefetch operation is defined as {@code <type><target><policy>}.
     * <p>
     * {@code <type>} is one of:
     * <ul>
     * <li><b>PLD</b>: Prefetch for load.</li>
     * <li><b>PLI</b>: Preload instructions.</li>
     * <li><b>PST</b>: Prefetch for store.</li>
     *
     * </ul>
     * </p>
     *
     * <p>
     * {@code <target>} is one of:
     * <ul>
     * <li><b>L1</b>: Level 1 cache.</li>
     * <li><b>L2</b>: Level 2 cache.</li>
     * <li><b>L3</b>: Level 3 cache.</li>
     *
     * </ul>
     * </p>
     *
     * <p>
     * {@code <policy>} is one of:
     * <ul>
     * <li><b>KEEP</b>: Retained or temporal prefetch, allocated in the cache normally.</li>
     * <li><b>STRM</b>: Streaming or non-temporal prefetch, for data that is used only once.</li>
     *
     * </ul>
     * </p>
     */
    public enum PrefetchMode {
        PLDL1KEEP(0b00000),
        PLDL1STRM(0b00001),
        PLDL2KEEP(0b00010),
        PLDL2STRM(0b00011),
        PLDL3KEEP(0b00100),
        PLDL3STRM(0b00101),

        PLIL1KEEP(0b01000),
        PLIL1STRM(0b01001),
        PLIL2KEEP(0b01010),
        PLIL2STRM(0b01011),
        PLIL3KEEP(0b01100),
        PLIL3STRM(0b01101),

        PSTL1KEEP(0b10000),
        PSTL1STRM(0b10001),
        PSTL2KEEP(0b10010),
        PSTL2STRM(0b10011),
        PSTL3KEEP(0b10100),
        PSTL3STRM(0b10101);

        private final int encoding;

        PrefetchMode(int encoding) {
            this.encoding = encoding;
        }

        private static PrefetchMode[] modes = {
                        PLDL1KEEP,
                        PLDL1STRM,
                        PLDL2KEEP,
                        PLDL2STRM,
                        PLDL3KEEP,
                        PLDL3STRM,

                        null,
                        null,

                        PLIL1KEEP,
                        PLIL1STRM,
                        PLIL2KEEP,
                        PLIL2STRM,
                        PLIL3KEEP,
                        PLIL3STRM,

                        null,
                        null,

                        PSTL1KEEP,
                        PSTL1STRM,
                        PSTL2KEEP,
                        PSTL2STRM,
                        PSTL3KEEP,
                        PSTL3STRM
        };

        public static PrefetchMode lookup(int enc) {
            assert enc >= 00 && enc < modes.length;
            return modes[enc];
        }

        public Register toRegister() {
            return cpuRegisters.get(encoding);
        }
    }

    /**
     * Implements a prefetch at a 64-bit aligned address.
     */
    public void prfm(AArch64Address address, PrefetchMode mode) {
        assert mode != null;

        /*
         * Prefetch instructions are encoded the same as LDR, except:
         *
         * 1) They do not have the load flag set [22]
         *
         * 2) They have an addressing mode variant flag set (either [31] or [23]).
         */
        int prfmFlag;
        switch (address.getAddressingMode()) {
            case IMMEDIATE_UNSIGNED_SCALED:
            case IMMEDIATE_SIGNED_UNSCALED:
            case BASE_REGISTER_ONLY:
            case REGISTER_OFFSET:
            case EXTENDED_REGISTER_OFFSET:
                prfmFlag = 1 << 23;
                break;
            case PC_LITERAL:
                prfmFlag = 1 << 31;
                break;
            default:
                /* Invalid addressing mode provided. */
                throw GraalError.shouldNotReachHere();
        }

        /* The prefetch mode is encoded within rt. */
        final Register rt = mode.toRegister();

        loadStoreInstruction(LDR, rt, address, false, getLog2TransferSize(64), prfmFlag);
    }

    /**
     * Stores register rt into memory pointed by address.
     *
     * @param destSize number of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param address all addressing modes allowed. May not be null.
     */
    public void str(int destSize, Register rt, AArch64Address address) {
        assert destSize == 8 || destSize == 16 || destSize == 32 || destSize == 64;
        assert verifyRegistersZ(rt);

        loadStoreInstruction(STR, rt, address, false, getLog2TransferSize(destSize));
    }

    private void loadStoreInstruction(Instruction instr, Register reg, AArch64Address address, boolean isFP, int log2TransferSize) {
        loadStoreInstruction(instr, reg, address, isFP, log2TransferSize, 0);
    }

    private void loadStoreInstruction(Instruction instr, Register reg, AArch64Address address, boolean isFP, int log2TransferSize, int extraEncoding) {
        assert log2TransferSize >= 0 && log2TransferSize < (isFP ? 5 : 4);
        assert address.getBitMemoryTransferSize() == AArch64Address.ANY_SIZE || getLog2TransferSize(address.getBitMemoryTransferSize()) == log2TransferSize;

        int transferSizeEncoding;
        if (address.getAddressingMode() == AddressingMode.PC_LITERAL) {
            assert log2TransferSize >= 2 : "PC literal loads only works for load/stores of 32-bit and larger";
            transferSizeEncoding = (log2TransferSize - 2) << LoadStoreTransferSizeOffset;
        } else {
            transferSizeEncoding = ((log2TransferSize & 0x3) << LoadStoreTransferSizeOffset) | ((log2TransferSize >> 2) << LoadStoreQuadWordTransferSizeOffset);
        }

        int floatFlag = isFP ? 1 << LoadStoreFpFlagOffset : 0;
        int memOp = extraEncoding | transferSizeEncoding | instr.encoding | floatFlag | rt(reg);
        switch (address.getAddressingMode()) {
            case IMMEDIATE_UNSIGNED_SCALED:
                emitInt(memOp | LoadStoreScaledOp | address.getImmediate() << LoadStoreScaledImmOffset | rs1(address.getBase()));
                break;
            case IMMEDIATE_SIGNED_UNSCALED:
                emitInt(memOp | LoadStoreUnscaledOp | address.getImmediate() << LoadStoreUnscaledImmOffset | rs1(address.getBase()));
                break;
            case BASE_REGISTER_ONLY:
                /* Note that this is the same as IMMEDIATE_UNSIGNED_SCALED with no immediate. */
                emitInt(memOp | LoadStoreScaledOp | rs1(address.getBase()));
                break;
            case EXTENDED_REGISTER_OFFSET:
            case REGISTER_OFFSET:
                ExtendType extendType = address.getAddressingMode() == AddressingMode.EXTENDED_REGISTER_OFFSET ? address.getExtendType() : ExtendType.UXTX;
                int shouldScaleFlag = (address.isRegisterOffsetScaled() ? 1 : 0) << LoadStoreScaledRegOffset;
                emitInt(memOp | LoadStoreRegisterOp | rs2(address.getOffset()) | extendType.encoding << ExtendTypeOffset | shouldScaleFlag | rs1(address.getBase()));
                break;
            case PC_LITERAL:
                annotatePatchingImmediate(position(), instr, 21, LoadLiteralImmOffset, 2);
                emitInt(transferSizeEncoding | floatFlag | LoadLiteralOp | rd(reg) | address.getImmediate() << LoadLiteralImmOffset);
                break;
            case IMMEDIATE_POST_INDEXED:
                emitInt(memOp | LoadStorePostIndexedOp | rs1(address.getBase()) | address.getImmediate() << LoadStoreIndexedImmOffset);
                break;
            case IMMEDIATE_PRE_INDEXED:
                emitInt(memOp | LoadStorePreIndexedOp | rs1(address.getBase()) | address.getImmediate() << LoadStoreIndexedImmOffset);
                break;
            default:
                throw GraalError.shouldNotReachHere("Unhandled addressing mode: " + address.getAddressingMode());
        }
    }

    /**
     * Insert ldp/stp at the specified position.
     */
    protected void insertLdpStp(int position, int size, Instruction instr, boolean isFP, Register rt, Register rt2, AArch64Address address) {
        if (isFP) {
            assert size == 32 || size == 64 || size == 128 : size;
            assert verifyRegistersFF(rt, rt2);
        } else {
            assert verifySizeAndRegistersZZ(size, rt, rt2);
        }

        int instructionEncoding = generateLoadStorePairInstructionEncoding(instr, rt, rt2, address, isFP, getLog2TransferSize(size));
        emitInt(instructionEncoding, position);
    }

    /**
     * C6.2.130 Load Pair of Registers calculates an address from a base register value and an
     * immediate offset, and stores two 32-bit words or two 64-bit doublewords to the calculated
     * address, from two registers.
     */
    public void ldp(int size, Register rt, Register rt2, AArch64Address address) {
        assert verifySizeAndRegistersRR(size, rt, rt2);

        loadStorePairInstruction(LDP, rt, rt2, address, false, getLog2TransferSize(size));
    }

    /**
     * C6.2.277 Store Pair of Registers calculates an address from a base register value and an
     * immediate offset, and stores two 32-bit words or two 64-bit doublewords to the calculated
     * address, from two registers.
     */
    public void stp(int size, Register rt, Register rt2, AArch64Address address) {
        assert verifySizeAndRegistersZZ(size, rt, rt2);

        loadStorePairInstruction(STP, rt, rt2, address, false, getLog2TransferSize(size));
    }

    private static int generateLoadStorePairInstructionEncoding(Instruction instr, Register rt, Register rt2, AArch64Address address, boolean isFP, int log2TransferSize) {
        assert log2TransferSize >= 2 && log2TransferSize < (isFP ? 5 : 4);
        assert getLog2TransferSize(address.getBitMemoryTransferSize()) == log2TransferSize;

        int transferSizeEncoding = (log2TransferSize - 2) << (isFP ? 30 : 31);
        int floatFlag = isFP ? 1 << LoadStoreFpFlagOffset : 0;
        // LDP/STP uses a 7-bit scaled offset
        int offset = address.getImmediate();
        int memOp = transferSizeEncoding | instr.encoding | floatFlag | offset << LoadStorePairImm7Offset | rt2(rt2) | rn(address.getBase()) | rt(rt);
        switch (address.getAddressingMode()) {
            case IMMEDIATE_PAIR_SIGNED_SCALED:
                return (memOp | LoadStorePairSignedOffsetOp);
            case IMMEDIATE_PAIR_POST_INDEXED:
                return (memOp | LoadStorePairPostIndexOp);
            case IMMEDIATE_PAIR_PRE_INDEXED:
                return (memOp | LoadStorePairPreIndexOp);
            default:
                throw GraalError.shouldNotReachHere("Unhandled addressing mode: " + address.getAddressingMode());
        }
    }

    private void loadStorePairInstruction(Instruction instr, Register rt, Register rt2, AArch64Address address, boolean isFP, int log2TransferSize) {
        int instructionEncoding = generateLoadStorePairInstructionEncoding(instr, rt, rt2, address, isFP, log2TransferSize);
        emitInt(instructionEncoding);
    }

    /* Load-Store Exclusive (5.3.6) */

    /**
     * Load address exclusive. Natural alignment of address is required.
     *
     * @param size size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    protected void ldxr(int size, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        assert verifyRegistersRP(rt, rn);

        exclusiveLoadInstruction(LDXR, rt, rn, getLog2TransferSize(size));
    }

    /**
     * Store address exclusive. Natural alignment of address is required. rs and rt may not point to
     * the same register.
     *
     * @param size size of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rs general purpose register. Set to exclusive access status. 0 means success,
     *            everything else failure. May not be null, or stackpointer.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    protected void stxr(int size, Register rs, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        assert verifyRegistersRZP(rs, rt, rn);

        exclusiveStoreInstruction(STXR, rs, rt, rn, getLog2TransferSize(size));
    }

    /* Load-Acquire/Store-Release (5.3.7) */

    /* non exclusive access */
    /**
     * Load acquire. Natural alignment of address is required.
     *
     * @param size size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    public void ldar(int size, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        assert verifyRegistersRP(rt, rn);

        exclusiveLoadInstruction(LDAR, rt, rn, getLog2TransferSize(size));
    }

    /**
     * Store-release. Natural alignment of address is required.
     *
     * @param size size of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    public void stlr(int size, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        assert verifyRegistersZP(rt, rn);

        // Hack: Passing the r0 means it is ignored when building the encoding.
        exclusiveStoreInstruction(STLR, r0, rt, rn, getLog2TransferSize(size));
    }

    /* exclusive access */
    /**
     * Load acquire exclusive. Natural alignment of address is required.
     *
     * @param size size of memory read in bits. Must be 8, 16, 32 or 64.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    protected void ldaxr(int size, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        assert verifyRegistersRP(rt, rn);

        exclusiveLoadInstruction(LDAXR, rt, rn, getLog2TransferSize(size));
    }

    /**
     * Store-release exclusive. Natural alignment of address is required. rs and rt may not point to
     * the same register.
     *
     * @param size size of bits written to memory. Must be 8, 16, 32 or 64.
     * @param rs general purpose register. Set to exclusive access status. 0 means success,
     *            everything else failure. May not be null, or stackpointer.
     * @param rt general purpose register. May not be null or stackpointer.
     * @param rn general purpose register.
     */
    protected void stlxr(int size, Register rs, Register rt, Register rn) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        assert verifyRegistersRZP(rs, rt, rn);

        exclusiveStoreInstruction(STLXR, rs, rt, rn, getLog2TransferSize(size));
    }

    /**
     * Loads data into rt from address and registers address as an exclusive access.
     *
     * @param rt general purpose register. May not be null
     * @param rn general purpose register containing the address specifying where rt is loaded from.
     * @param log2TransferSize log2Ceil of memory transfer size.
     */
    private void exclusiveLoadInstruction(Instruction instr, Register rt, Register rn, int log2TransferSize) {
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        emitInt(transferSizeEncoding | instr.encoding | rn(rn) | rt(rt));
    }

    /**
     * Stores data from rt into address and sets rs to the returned exclusive access status.
     *
     * @param rs general purpose register into which the exclusive access status is written. May not
     *            be null.
     * @param rt general purpose register containing data to be written to memory at address. May
     *            not be null
     * @param rn general purpose register containing the address specifying where rt is written to.
     * @param log2TransferSize log2Ceil of memory transfer size.
     */
    private void exclusiveStoreInstruction(Instruction instr, Register rs, Register rt, Register rn, int log2TransferSize) {
        assert log2TransferSize >= 0 && log2TransferSize < 4;
        assert instr == STLR || (!rs.equals(rt) && !rs.equals(rn));

        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        emitInt(transferSizeEncoding | instr.encoding | rs2(rs) | rn(rn) | rt(rt));
    }

    /**
     * Compare And Swap word or doubleword in memory. This reads a value from an address rn,
     * compares it against a given value rs, and, if equal, stores the value rt to memory. The value
     * read from address rn is stored in register rs.
     *
     * @param size size of bits read from memory. Must be 8, 16, 32 or 64.
     * @param rs general purpose register to be compared and loaded. May not be null.
     * @param rt general purpose register to be conditionally stored. May not be null.
     * @param rn general purpose register containing the address from which to read.
     * @param acquire boolean value signifying if the load should use acquire semantics.
     * @param release boolean value signifying if the store should use release semantics.
     */
    public void cas(int size, Register rs, Register rt, Register rn, boolean acquire, boolean release) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        assert verifyRegistersRZP(rs, rt, rn);

        compareAndSwapInstruction(CAS, rs, rt, rn, getLog2TransferSize(size), acquire, release);
    }

    private void compareAndSwapInstruction(Instruction instr, Register rs, Register rt, Register rn, int log2TransferSize, boolean acquire, boolean release) {
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        emitInt(transferSizeEncoding | instr.encoding | rs2(rs) | rn(rn) | rt(rt) | (acquire ? 1 : 0) << CASAcquireOffset | (release ? 1 : 0) << CASReleaseOffset);
    }

    /**
     * Atomic add. This reads a value from an address rn, stores the value in rt, and adds the value
     * in rs to it, and stores the result back at address rn. The initial value read from memory is
     * stored in rt.
     *
     * @param size size of operand to read from memory. Must be 8, 16, 32, or 64.
     * @param rs general purpose register to be added to contents. May not be null.
     * @param rt general purpose register to be loaded. May not be null.
     * @param rn general purpose register or stack pointer holding an address from which to load.
     * @param acquire boolean value signifying if the load should use acquire semantics.
     * @param release boolean value signifying if the store should use release semantics.
     */
    public void ldadd(int size, Register rs, Register rt, Register rn, boolean acquire, boolean release) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        assert verifyRegistersZRP(rs, rt, rn);

        loadAndAddInstruction(LDADD, rs, rt, rn, getLog2TransferSize(size), acquire, release);
    }

    private void loadAndAddInstruction(Instruction instr, Register rs, Register rt, Register rn, int log2TransferSize, boolean acquire, boolean release) {
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        emitInt(transferSizeEncoding | instr.encoding | rs2(rs) | rn(rn) | rt(rt) | (acquire ? 1 : 0) << LDADDAcquireOffset | (release ? 1 : 0) << LDADDReleaseOffset);
    }

    /**
     * Atomic swap. This reads a value from an address rn, stores the value in rt, and then stores
     * the value in rs back at address rn.
     *
     * @param size size of operand to read from memory. Must be 8, 16, 32, or 64.
     * @param rs general purpose register to be stored. May not be null.
     * @param rt general purpose register to be loaded. May not be null.
     * @param rn general purpose register or stack pointer holding an address from which to load.
     * @param acquire boolean value signifying if the load should use acquire semantics.
     * @param release boolean value signifying if the store should use release semantics.
     */
    public void swp(int size, Register rs, Register rt, Register rn, boolean acquire, boolean release) {
        assert size == 8 || size == 16 || size == 32 || size == 64;
        assert verifyRegistersRZP(rs, rt, rn);

        swapInstruction(SWP, rs, rt, rn, getLog2TransferSize(size), acquire, release);
    }

    private void swapInstruction(Instruction instr, Register rs, Register rt, Register rn, int log2TransferSize, boolean acquire, boolean release) {
        int transferSizeEncoding = log2TransferSize << LoadStoreTransferSizeOffset;
        emitInt(transferSizeEncoding | instr.encoding | rs2(rs) | rn(rn) | rt(rt) | (acquire ? 1 : 0) << LDADDAcquireOffset | (release ? 1 : 0) << LDADDReleaseOffset);
    }

    /* PC-relative Address Calculation (5.4.4) */

    /**
     * C6.2.11 PC-relative address to 4KB page.
     *
     * Address of page: sign extends 21-bit offset, shifts if left by 12 and adds it to the value of
     * the PC with its bottom 12-bits cleared, writing the result to dst. No offset is emitted; the
     * instruction will be patched later.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     */
    public void adrp(Register dst) {
        assert verifyRegistersR(dst);

        emitInt(ADRP.encoding | PcRelImmOp | rd(dst));
    }

    /**
     * C6.2.10 PC-relative address.
     *
     * Adds a 21-bit signed offset to the program counter and writes the result to dst.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     * @param imm21 Signed 21-bit offset.
     */
    public void adr(Register dst, int imm21) {
        adr(dst, imm21, -1);
    }

    /**
     * C6.2.10 PC-relative address.
     *
     * Adds a 21-bit signed offset to the program counter and writes the result to dst.
     *
     * @param dst general purpose register. May not be null, zero-register or stackpointer.
     * @param imm21 Signed 21-bit offset.
     * @param pos the position in the code that the instruction is emitted.
     */
    public void adr(Register dst, int imm21, int pos) {
        assert verifyRegistersR(dst);
        ImmediateOpChecks.validateSigned(21, imm21);
        ImmediateOpChecks.validate4ByteAlignment(imm21);
        int imm = imm21 & NumUtil.getNbitNumberInt(21);
        // higher 19 bit
        int immHi = (imm >> 2) << PcRelImmHiOffset;
        // lower 2 bit
        int immLo = (imm & 0x3) << PcRelImmLoOffset;

        int encoding = ADR.encoding | PcRelImmOp | rd(dst) | immHi | immLo;
        if (pos == -1) {
            emitInt(encoding);
        } else {
            emitInt(encoding, pos);
        }
    }

    /* Arithmetic (Immediate) (5.4.1) */

    /**
     * C6.2.4 Add (Immediate).
     *
     * dst = src + aimm
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void add(int size, Register dst, Register src, int aimm) {
        assert verifySizeAndRegistersPP(size, dst, src);

        addSubImmInstruction(ADD, dst, src, aimm, generalFromSize(size));
    }

    /**
     * C6.2.8 Add (Immediate) & set flags.
     *
     * dst = src + aimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void adds(int size, Register dst, Register src, int aimm) {
        assert verifySizeAndRegistersZP(size, dst, src);

        addSubImmInstruction(ADDS, dst, src, aimm, generalFromSize(size));
    }

    /**
     * C6.2.313 Subtract (Immediate).
     *
     * dst = src - aimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void sub(int size, Register dst, Register src, int aimm) {
        assert verifySizeAndRegistersPP(size, dst, src);

        addSubImmInstruction(SUB, dst, src, aimm, generalFromSize(size));
    }

    /**
     * C6.2.319 Subtract (immediate) & set flags.
     *
     * dst = src - aimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src general purpose register. May not be null or zero-register.
     * @param aimm arithmetic immediate. Either unsigned 12-bit value or unsigned 24-bit value with
     *            the lower 12-bit cleared.
     */
    protected void subs(int size, Register dst, Register src, int aimm) {
        assert verifySizeAndRegistersZP(size, dst, src);

        addSubImmInstruction(SUBS, dst, src, aimm, generalFromSize(size));
    }

    private void addSubImmInstruction(Instruction instr, Register dst, Register src, int aimm, InstructionType type) {
        emitInt(type.encoding | instr.encoding | AddSubImmOp | encodeAddSubtractImm(aimm) | rd(dst) | rs1(src));
    }

    /**
     * C6.2.48 Conditional Compare (register).
     */
    public void ccmp(int size, Register x, Register y, int aimm, ConditionFlag condition) {
        assert verifySizeAndRegistersZZ(size, x, y);

        emitInt(generalFromSize(size).encoding | CCMP.encoding | rs1(x) | rs2(y) | encodeAddSubtractImm(aimm) | condition.encoding << ConditionalConditionOffset);
    }

    /**
     * Encodes add/subtract immediate.
     *
     * @param imm Immediate has to be either an unsigned 12-bit value or an unsigned 24-bit value
     *            with the lower 12 bits zero.
     * @return Representation of immediate for use with arithmetic instructions.
     */
    private static int encodeAddSubtractImm(int imm) {
        GraalError.guarantee(isAddSubtractImmediate(imm, false), "Immediate has to be legal add/subtract. Immediate value %s", imm);

        if (NumUtil.isUnsignedNbit(12, imm)) {
            return imm << ImmediateOffset;
        } else {
            // First 12-bit are zero, so shift immediate 12-bit and set flag to indicate
            // shifted immediate value.
            return (imm >>> 12 << ImmediateOffset) | AddSubShift12;
        }
    }

    /**
     * Checks whether immediate can be encoded as an add/subtract immediate.
     *
     * @param imm Immediate has to be either an unsigned 12bit value or un unsigned 24bit value with
     *            the lower 12 bits 0.
     * @param useAbs whether to check the absolute imm value, or check imm as provided.
     * @return true if valid arithmetic immediate, false otherwise.
     */
    public static boolean isAddSubtractImmediate(long imm, boolean useAbs) {
        long checkedImm = useAbs ? Math.abs(imm) : imm;
        return NumUtil.isUnsignedNbit(12, checkedImm) || (NumUtil.isUnsignedNbit(12, checkedImm >>> 12) && ((checkedImm & 0xfff) == 0));
    }

    /* Logical (immediate) (5.4.2) */

    /**
     * C6.2.12 Bitwise AND (immediate).
     *
     * dst = src & bimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalBitmaskImmediateEncoding} for exact
     *            definition.
     */
    public void and(int size, Register dst, Register src, long bimm) {
        assert verifySizeAndRegistersPR(size, dst, src);

        logicalImmInstruction(AND, dst, src, bimm, generalFromSize(size));
    }

    /**
     * 6.2.14 Bitwise AND (immediate) & set flags.
     *
     * dst = src & bimm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stack-pointer.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalBitmaskImmediateEncoding} for exact
     *            definition.
     */
    public void ands(int size, Register dst, Register src, long bimm) {
        assert verifySizeAndRegistersZZ(size, dst, src);

        logicalImmInstruction(ANDS, dst, src, bimm, generalFromSize(size));
    }

    /**
     * C6.2.85 Bitwise Exclusive OR (immediate).
     *
     * dst = src ^ bimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalBitmaskImmediateEncoding} for exact
     *            definition.
     */
    public void eor(int size, Register dst, Register src, long bimm) {
        assert verifySizeAndRegistersPZ(size, dst, src);

        logicalImmInstruction(EOR, dst, src, bimm, generalFromSize(size));
    }

    /**
     * C6.2.206 Bitwise OR (immediate).
     *
     * dst = src | bimm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src general purpose register. May not be null or stack-pointer.
     * @param bimm logical immediate. See {@link LogicalBitmaskImmediateEncoding} for exact
     *            definition.
     */
    public void orr(int size, Register dst, Register src, long bimm) {
        assert verifySizeAndRegistersPZ(size, dst, src);

        logicalImmInstruction(ORR, dst, src, bimm, generalFromSize(size));
    }

    private void logicalImmInstruction(Instruction instr, Register dst, Register src, long bimm, InstructionType type) {
        // Mask higher bits off, since we always pass longs around even for the 32-bit instruction.
        long bimmValue;
        if (type == General32) {
            assert (bimm >> 32) == 0 || ((bimm >> 32) == -1L && (int) bimm < 0) : "Immediate must be either 0x0000_0000_xxxx_xxxx or (0xFFFF_FFFF_xxxx_xxxx | 0x8000_0000)";
            bimmValue = bimm & NumUtil.getNbitNumberLong(32);
        } else {
            bimmValue = bimm;
        }
        int immEncoding = LogicalBitmaskImmediateEncoding.getEncoding(type == General64, bimmValue);
        emitInt(type.encoding | instr.encoding | LogicalImmOp | immEncoding | rd(dst) | rs1(src));
    }

    /* Move (wide immediate) (5.4.3) */

    /**
     * C6.2.193 Move wide with zero.
     *
     * dst = uimm16 << shiftAmt.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param uimm16 16-bit unsigned immediate
     * @param shiftAmt amount by which uimm16 is left shifted. Can be any multiple of 16 smaller
     *            than size.
     */
    protected void movz(int size, Register dst, int uimm16, int shiftAmt) {
        assert verifySizeAndRegistersR(size, dst);

        moveWideImmInstruction(MOVZ, dst, uimm16, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.192 Move wide with NOT.
     *
     * dst = ~(uimm16 << shiftAmt).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param uimm16 16-bit unsigned immediate
     * @param shiftAmt amount by which uimm16 is left shifted. Can be any multiple of 16 smaller
     *            than size.
     */
    protected void movn(int size, Register dst, int uimm16, int shiftAmt) {
        assert verifySizeAndRegistersR(size, dst);

        moveWideImmInstruction(MOVN, dst, uimm16, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.191 Move wide with keep.
     *
     * dst<pos+15:pos> = uimm16.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param uimm16 16-bit unsigned immediate
     * @param pos position into which uimm16 is inserted. Can be any multiple of 16 smaller than
     *            size.
     */
    protected void movk(int size, Register dst, int uimm16, int pos) {
        assert verifySizeAndRegistersR(size, dst);

        moveWideImmInstruction(MOVK, dst, uimm16, pos, generalFromSize(size));
    }

    private void moveWideImmInstruction(Instruction instr, Register dst, int uimm16, int shiftAmt, InstructionType type) {
        ImmediateOpChecks.validateUnsigned(16, uimm16);
        assert shiftAmt == 0 || shiftAmt == 16 || (type == InstructionType.General64 && (shiftAmt == 32 || shiftAmt == 48)) : "Invalid shift amount: " + shiftAmt;

        int shiftValue = shiftAmt >> 4;
        emitInt(type.encoding | instr.encoding | MoveWideImmOp | rd(dst) | uimm16 << MoveWideImmOffset | shiftValue << MoveWideShiftOffset);
    }

    /* Bitfield Operations (5.4.5) */

    /**
     * C6.2.29 Bitfield move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param r must be in the range 0 to size - 1
     * @param s must be in the range 0 to size - 1
     */
    public void bfm(int size, Register dst, Register src, int r, int s) {
        assert verifySizeAndRegistersRR(size, dst, src);

        bitfieldInstruction(BFM, dst, src, r, s, generalFromSize(size));
    }

    /**
     * C6.2.337 Unsigned bitfield move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param r must be in the range 0 to size - 1
     * @param s must be in the range 0 to size - 1
     */
    public void ubfm(int size, Register dst, Register src, int r, int s) {
        assert verifySizeAndRegistersRR(size, dst, src);

        bitfieldInstruction(UBFM, dst, src, r, s, generalFromSize(size));
    }

    /**
     * C6.2.234 Signed bitfield move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, stackpointer or zero-register.
     * @param src general purpose register. May not be null, stackpointer or zero-register.
     * @param r must be in the range 0 to size - 1
     * @param s must be in the range 0 to size - 1
     */
    public void sbfm(int size, Register dst, Register src, int r, int s) {
        assert verifySizeAndRegistersRR(size, dst, src);

        bitfieldInstruction(SBFM, dst, src, r, s, generalFromSize(size));
    }

    private void bitfieldInstruction(Instruction instr, Register dst, Register src, int r, int s, InstructionType type) {
        assert s >= 0 && s < type.width && r >= 0 && r < type.width;

        int sf = type == General64 ? 1 << ImmediateSizeOffset : 0;
        emitInt(type.encoding | instr.encoding | BitfieldImmOp | sf | r << ImmediateRotateOffset | s << ImmediateOffset | rd(dst) | rs1(src));
    }

    /* Extract (Immediate) (5.4.6) */

    /**
     * C6.2.90 Extract register.
     *
     * Extracts a register from a pair of registers.
     *
     * dst = src1:src2<lsb+31:lsb>
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param lsb must be in range 0 to size - 1.
     */
    public void extr(int size, Register dst, Register src1, Register src2, int lsb) {
        assert verifySizeAndRegistersRZZ(size, dst, src1, src2);

        InstructionType type = generalFromSize(size);
        assert lsb >= 0 && lsb < type.width;
        int sf = type == General64 ? 1 << ImmediateSizeOffset : 0;
        emitInt(type.encoding | EXTR.encoding | sf | lsb << ImmediateOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Arithmetic (shifted register) (5.5.1) */

    /**
     * C6.2.5 Add (shifted register).
     *
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
        assert verifySizeAndRegistersRZZ(size, dst, src1, src2);

        addSubShiftedInstruction(ADD, dst, src1, src2, shiftType, imm, generalFromSize(size));
    }

    /**
     * C6.2.9 Add (shift registers) set flags.
     *
     * dst = src1 + shiftType(src2, imm) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param imm must be in range 0 to size - 1.
     */
    public void adds(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        assert verifySizeAndRegistersZZZ(size, dst, src1, src2);

        addSubShiftedInstruction(ADDS, dst, src1, src2, shiftType, imm, generalFromSize(size));
    }

    /**
     * C6.2.314 Subtract (shifted register).
     *
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
        assert verifySizeAndRegistersRZZ(size, dst, src1, src2);

        addSubShiftedInstruction(SUB, dst, src1, src2, shiftType, imm, generalFromSize(size));
    }

    /**
     * C6.2.320 Subtract (shifted register) & set flags.
     *
     * dst = src1 - shiftType(src2, imm) and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType any type but ROR.
     * @param imm must be in range 0 to size - 1.
     */
    public void subs(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        assert verifySizeAndRegistersZZZ(size, dst, src1, src2);

        addSubShiftedInstruction(SUBS, dst, src1, src2, shiftType, imm, generalFromSize(size));
    }

    private void addSubShiftedInstruction(Instruction instr, Register dst, Register src1, Register src2, ShiftType shiftType, int imm, InstructionType type) {
        assert shiftType != ShiftType.ROR;
        assert imm >= 0 && imm < type.width;

        emitInt(type.encoding | instr.encoding | AddSubShiftedOp | imm << ImmediateOffset | shiftType.encoding << ShiftTypeOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Arithmetic (extended register) (5.5.2) */
    /**
     * C6.2.3 Add (extended register).
     *
     * dst = src1 + extendType(src2) << imm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    public void add(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert verifySizeAndRegistersPPZ(size, dst, src1, src2);

        addSubExtendedInstruction(ADD, dst, src1, src2, extendType, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.7 Add (extended register) & set flags.
     *
     * dst = src1 + extendType(src2) << imm and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    protected void adds(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert verifySizeRegistersZPZ(size, dst, src1, src2);

        addSubExtendedInstruction(ADDS, dst, src1, src2, extendType, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.312 Subtract (extended register).
     *
     * dst = src1 - extendType(src2) << imm.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or zero-register.
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    public void sub(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert verifySizeAndRegistersPPZ(size, dst, src1, src2);

        addSubExtendedInstruction(SUB, dst, src1, src2, extendType, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.318 Subtract (extended register) & set flags.
     *
     * dst = src1 - extendType(src2) << imm and sets flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or zero-register.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param extendType defines how src2 is extended to the same size as src1.
     * @param shiftAmt must be in range 0 to 4.
     */
    public void subs(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        assert verifySizeRegistersZPZ(size, dst, src1, src2);

        addSubExtendedInstruction(SUBS, dst, src1, src2, extendType, shiftAmt, generalFromSize(size));
    }

    private void addSubExtendedInstruction(Instruction instr, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt, InstructionType type) {
        assert shiftAmt >= 0 && shiftAmt <= 4;

        emitInt(type.encoding | instr.encoding | AddSubExtendedOp | shiftAmt << ImmediateOffset | extendType.encoding << ExtendTypeOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Logical (shifted register) (5.5.3) */
    /**
     * C6.2.13 Bitwise AND (shifted register).
     *
     * dst = src1 & shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    public void and(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        assert verifySizeAndRegistersRRR(size, dst, src1, src2);

        logicalRegInstruction(AND, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.15 Bitwise AND (shifted register) & set flags.
     *
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
        assert verifySizeAndRegistersZZZ(size, dst, src1, src2);

        logicalRegInstruction(ANDS, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.31 Bitwise Bit Clear (shifted register).
     *
     * dst = src1 & ~(shiftType(src2, imm)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    public void bic(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        assert verifySizeAndRegistersRRR(size, dst, src1, src2);

        logicalRegInstruction(BIC, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.32 Bitwise Bit Clear (shifted register) & set flags.
     *
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
        assert verifySizeAndRegistersZZZ(size, dst, src1, src2);

        logicalRegInstruction(BICS, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.84 Bitwise Exclusive OR NOT (shifted register).
     *
     * dst = src1 ^ ~(shiftType(src2, imm)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    public void eon(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        assert verifySizeAndRegistersRZZ(size, dst, src1, src2);

        logicalRegInstruction(EON, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.86 Bitwise Exclusive OR (shifted register).
     *
     * dst = src1 ^ shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    public void eor(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        assert verifySizeAndRegistersRZZ(size, dst, src1, src2);

        logicalRegInstruction(EOR, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * Bitwise OR (shifted register).
     *
     * dst = src1 | shiftType(src2, imm).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    public void orr(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        assert verifySizeAndRegistersRZZ(size, dst, src1, src2);

        logicalRegInstruction(ORR, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    /**
     * C6.2.205 Bitwise OR NOT (shifted register).
     *
     * dst = src1 | ~(shiftType(src2, imm)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     * @param shiftType all types allowed, may not be null.
     * @param shiftAmt must be in range 0 to size - 1.
     */
    public void orn(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        assert verifySizeAndRegistersRZR(size, dst, src1, src2);

        logicalRegInstruction(ORN, dst, src1, src2, shiftType, shiftAmt, generalFromSize(size));
    }

    private void logicalRegInstruction(Instruction instr, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt, InstructionType type) {
        assert shiftAmt >= 0 && shiftAmt < type.width;

        emitInt(type.encoding | instr.encoding | LogicalShiftOp | shiftAmt << ImmediateOffset | shiftType.encoding << ShiftTypeOffset | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Variable Shift (5.5.4) */
    /**
     * C6.2.16 Arithmetic Shift Right (register).
     *
     * dst = src1 >> (src2 & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void asr(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersRRR(size, dst, src1, src2);

        dataProcessing2SourceOp(ASRV, dst, src1, src2, generalFromSize(size));
    }

    /**
     * C6.2.178 Logical Shift Left (register).
     *
     * dst = src1 << (src2 & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void lsl(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersRRR(size, dst, src1, src2);

        dataProcessing2SourceOp(LSLV, dst, src1, src2, generalFromSize(size));
    }

    /**
     * C6.2.181 Logical Shift Right (register).
     *
     * dst = src1 >>> (src2 & log2(size)).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void lsr(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersRRR(size, dst, src1, src2);

        dataProcessing2SourceOp(LSRV, dst, src1, src2, generalFromSize(size));
    }

    /**
     * C6.2.229 Rotate right variable.
     *
     * dst = rotateRight(src1, (src2 & (size - 1))).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    protected void rorv(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersRRR(size, dst, src1, src2);

        dataProcessing2SourceOp(RORV, dst, src1, src2, generalFromSize(size));
    }

    /**
     * C6.2.1 Add with carry.
     *
     * dst = src1 + src2 + PSTATE.C.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void adc(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersZZZ(size, dst, src1, src2);

        addSubWithCarryOp(ADC, dst, src1, src2, generalFromSize(size));
    }

    /**
     * C6.2.2 Add with carry & set flags.
     *
     * dst = src1 + src2 + PSTATE.C, and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void adcs(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersZZZ(size, dst, src1, src2);

        addSubWithCarryOp(ADCS, dst, src1, src2, generalFromSize(size));
    }

    /**
     * C6.2.231 Subtract with carry.
     *
     * dst = src1 - src2 + NOT(PSTATE.C).
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void sbc(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersZZZ(size, dst, src1, src2);

        addSubWithCarryOp(SBC, dst, src1, src2, generalFromSize(size));
    }

    /**
     * C6.2.232 Subtract with carry & set flags.
     *
     * dst = src1 - src2 + NOT(PSTATE.C), and sets condition flags.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or stackpointer.
     * @param src1 general purpose register. May not be null or stackpointer.
     * @param src2 general purpose register. May not be null or stackpointer.
     */
    public void sbcs(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersZZZ(size, dst, src1, src2);

        addSubWithCarryOp(SBCS, dst, src1, src2, generalFromSize(size));
    }

    private void addSubWithCarryOp(Instruction instr, Register dst, Register src1, Register src2, InstructionType type) {
        int baseEncoding = 0b0_0_0_11010000_00000_000000_00000_00000;
        emitInt(instr.encoding | type.encoding | baseEncoding | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Bit Operations (5.5.5) */

    /**
     * C6.2.55 Count Leading Sign bits.
     *
     * Counts leading sign bits. Sets Wd to the number of consecutive bits following the topmost bit
     * in dst, that are the same as the topmost bit. The count does not include the topmost bit
     * itself , so the result will be in the range 0 to size-1 inclusive.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, zero-register or the stackpointer.
     * @param src source register. May not be null, zero-register or the stackpointer.
     */
    protected void cls(int size, Register dst, Register src) {
        assert verifySizeAndRegistersRR(size, dst, src);

        dataProcessing1SourceOp(CLS, dst, src, generalFromSize(size));
    }

    /**
     * C6.2.55 Count Leading Zeros.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, zero-register or the stackpointer.
     * @param src source register. May not be null, zero-register or the stackpointer.
     */
    public void clz(int size, Register dst, Register src) {
        assert verifySizeAndRegistersRR(size, dst, src);

        dataProcessing1SourceOp(CLZ, dst, src, generalFromSize(size));
    }

    /**
     * C6.2.219 Reverse bits.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null, zero-register or the stackpointer.
     * @param src source register. May not be null, zero-register or the stackpointer.
     */
    public void rbit(int size, Register dst, Register src) {
        assert verifySizeAndRegistersRR(size, dst, src);

        dataProcessing1SourceOp(RBIT, dst, src, generalFromSize(size));
    }

    /**
     * C6.2.222 Reverse bytes.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src source register. May not be null or the stackpointer.
     */
    public void rev(int size, Register dst, Register src) {
        assert verifySizeAndRegistersRR(size, dst, src);

        if (size == 64) {
            dataProcessing1SourceOp(REVX, dst, src, generalFromSize(size));
        } else {
            assert size == 32;
            dataProcessing1SourceOp(REVW, dst, src, generalFromSize(size));
        }
    }

    /* Conditional Data Processing (5.5.6) */

    /**
     * C6.2.69 Conditional select.
     *
     * dst = condition ? src1 : src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param condition any condition flag. May not be null.
     */
    public void csel(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        assert verifySizeAndRegistersRZZ(size, dst, src1, src2);

        conditionalSelectInstruction(CSEL, dst, src1, src2, condition, generalFromSize(size));
    }

    /**
     * C6.2.74 Conditional select negate.
     *
     * dst = condition ? src1 : -src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param condition any condition flag. May not be null.
     */
    public void csneg(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        assert verifySizeAndRegistersRZZ(size, dst, src1, src2);

        conditionalSelectInstruction(CSNEG, dst, src1, src2, condition, generalFromSize(size));
    }

    /**
     * C6.2.72 Conditional increase.
     *
     * dst = condition ? src1 : src2 + 1.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param condition any condition flag. May not be null.
     */
    public void csinc(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        assert verifySizeAndRegistersRZZ(size, dst, src1, src2);

        conditionalSelectInstruction(CSINC, dst, src1, src2, condition, generalFromSize(size));
    }

    private void conditionalSelectInstruction(Instruction instr, Register dst, Register src1, Register src2, ConditionFlag condition, InstructionType type) {
        emitInt(type.encoding | instr.encoding | ConditionalSelectOp | rd(dst) | rs1(src1) | rs2(src2) | condition.encoding << ConditionalConditionOffset);
    }

    /* Integer Multiply/Divide (5.6) */

    /**
     * C6.2.184 Multiply-Add.
     *
     * dst = src1 * src2 + src3.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    public void madd(int size, Register dst, Register src1, Register src2, Register src3) {
        assert verifySizeAndRegistersRRR(size, dst, src1, src2);

        mulInstruction(MADD, dst, src1, src2, src3, generalFromSize(size));
    }

    /**
     * C6.2.197 Multiply-Subtract.
     *
     * dst = src3 - src1 * src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    public void msub(int size, Register dst, Register src1, Register src2, Register src3) {
        assert verifySizeAndRegistersRRR(size, dst, src1, src2);

        mulInstruction(MSUB, dst, src1, src2, src3, generalFromSize(size));
    }

    /**
     * C6.2.244 Signed Multiply High.
     *
     * dst = (src1 * src2)[127:64]
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    protected void smulh(Register dst, Register src1, Register src2) {
        assert verifyRegistersRRR(dst, src1, src2);

        emitInt(0b10011011010 << 21 | dst.encoding | rs1(src1) | rs2(src2) | 0b011111 << ImmediateOffset);
    }

    /**
     * C6.2.344 Unsigned multiply high.
     *
     * dst = (src1 * src2)[127:64]
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    protected void umulh(Register dst, Register src1, Register src2) {
        assert verifyRegistersRRR(dst, src1, src2);

        emitInt(0b10011011110 << 21 | dst.encoding | rs1(src1) | rs2(src2) | 0b011111 << ImmediateOffset);
    }

    /**
     * C6.2.341 Unsigned multiply add-long.
     *
     * xDst = xSrc3 + (wSrc1 * wSrc2)
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    protected void umaddl(Register dst, Register src1, Register src2, Register src3) {
        assert verifyRegistersRRRZ(dst, src1, src2, src3);

        emitInt(0b10011011101 << 21 | dst.encoding | rs1(src1) | rs2(src2) | 0b011111 << ImmediateOffset);
    }

    /**
     * C6.2.240 Signed multiply-add long.
     *
     * xDst = xSrc3 + (wSrc1 * wSrc2)
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    public void smaddl(Register dst, Register src1, Register src2, Register src3) {
        assert verifyRegistersRRRZ(dst, src1, src2, src3);

        smullInstruction(MADD, dst, src1, src2, src3);
    }

    /**
     * C6.2.243 Signed multiply-sub long.
     *
     * xDst = xSrc3 - (wSrc1 * wSrc2)
     *
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     * @param src3 general purpose register. May not be null or the stackpointer.
     */
    public void smsubl(Register dst, Register src1, Register src2, Register src3) {
        assert verifyRegistersRRRZ(dst, src1, src2, src3);

        smullInstruction(MSUB, dst, src1, src2, src3);
    }

    private void mulInstruction(Instruction instr, Register dst, Register src1, Register src2, Register src3, InstructionType type) {
        emitInt(type.encoding | instr.encoding | MulOp | rd(dst) | rs1(src1) | rs2(src2) | rs3(src3));
    }

    private void smullInstruction(Instruction instr, Register dst, Register src1, Register src2, Register src3) {
        emitInt(instr.encoding | SignedMulLongOp | rd(dst) | rs1(src1) | rs2(src2) | rs3(src3));
    }

    /**
     * C6.2.236 Signed divide.
     *
     * dst = src1 / src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    public void sdiv(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersRRR(size, dst, src1, src2);

        dataProcessing2SourceOp(SDIV, dst, src1, src2, generalFromSize(size));
    }

    /**
     * C6.2.340 Unsigned divide.
     *
     * dst = src1 / src2.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst general purpose register. May not be null or the stackpointer.
     * @param src1 general purpose register. May not be null or the stackpointer.
     * @param src2 general purpose register. May not be null or the stackpointer.
     */
    public void udiv(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersRRR(size, dst, src1, src2);

        dataProcessing2SourceOp(UDIV, dst, src1, src2, generalFromSize(size));
    }

    private void dataProcessing1SourceOp(Instruction instr, Register dst, Register src, InstructionType type) {
        emitInt(type.encoding | instr.encoding | DataProcessing1SourceOp | rd(dst) | rs1(src));
    }

    private void dataProcessing2SourceOp(Instruction instr, Register dst, Register src1, Register src2, InstructionType type) {
        emitInt(type.encoding | instr.encoding | DataProcessing2SourceOp | rd(dst) | rs1(src1) | rs2(src2));
    }

    /* Floating point operations */

    /* Load-Store Single FP register (5.7.1.1) */
    /**
     * Floating point load.
     *
     * @param size number of bits read from memory into rt. Must be 8, 16, 32, 64 or 128.
     * @param rt floating point register. May not be null.
     * @param address all addressing modes allowed. May not be null.
     */
    public void fldr(int size, Register rt, AArch64Address address) {
        assert size == 8 || size == 16 || size == 32 || size == 64 || size == 128;
        assert verifyRegistersF(rt);

        /* When using an immediate or register based addressing mode, then the load flag is set. */
        int loadFlag = address.getAddressingMode() == AddressingMode.PC_LITERAL ? 0 : LoadFlag;
        loadStoreInstruction(LDR, rt, address, true, getLog2TransferSize(size), loadFlag);
    }

    /**
     * Floating point store.
     *
     * @param size number of bits read from memory into rt. Must be 32 or 64.
     * @param rt floating point register. May not be null.
     * @param address all addressing modes allowed. May not be null.
     */
    public void fstr(int size, Register rt, AArch64Address address) {
        assert size == 8 || size == 16 || size == 32 || size == 64 || size == 128;
        assert verifyRegistersF(rt);

        loadStoreInstruction(STR, rt, address, true, getLog2TransferSize(size));
    }

    /**
     * Load Pair of Registers calculates an address from a base register value and an immediate
     * offset, and stores two single, double, or quad words to the calculated address, from two
     * registers.
     */
    public void fldp(int size, Register rt, Register rt2, AArch64Address address) {
        assert size == 32 || size == 64 || size == 128;
        assert verifyRegistersFF(rt, rt2);

        loadStorePairInstruction(LDP, rt, rt2, address, true, getLog2TransferSize(size));
    }

    /**
     * Store Pair of Registers calculates an address from a base register value and an immediate
     * offset, and stores two single, double, or quad words to the calculated address, from two
     * registers.
     */
    public void fstp(int size, Register rt, Register rt2, AArch64Address address) {
        assert size == 32 || size == 64 || size == 128;
        assert verifyRegistersFF(rt, rt2);

        loadStorePairInstruction(STP, rt, rt2, address, true, getLog2TransferSize(size));
    }

    /* Floating-point Move (register) (5.7.2) */

    /**
     * C7.2.130 Floating point move.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    protected void fmovFpu2Fpu(int size, Register dst, Register src) {
        assert verifySizeAndRegistersFF(size, dst, src);

        fpDataProcessing1Source(FMOV, dst, src, floatFromSize(size));
    }

    /**
     * C7.2.131 Floating-point Move to general-purpose register.
     *
     * Move size bits from floating point register unchanged to general purpose register.
     *
     * @param size number of bits read from memory into rt. Must be 32 or 64.
     * @param dst general purpose register. May not be null, stack-pointer or zero-register
     * @param src floating point register. May not be null.
     */
    protected void fmovFpu2Cpu(int size, Register dst, Register src) {
        assert verifySizeAndRegistersRF(size, dst, src);

        fmovCpuFpuInstruction(dst, src, size == 64, Instruction.FMOVFPU2CPU);
    }

    /**
     * C7.2.131 Floating-point Move from general-purpose register.
     *
     * Move size bits from general purpose register unchanged to floating point register.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating point register. May not be null.
     * @param src general purpose register. May not be null or stack-pointer.
     */
    protected void fmovCpu2Fpu(int size, Register dst, Register src) {
        assert verifySizeAndRegistersFZ(size, dst, src);

        fmovCpuFpuInstruction(dst, src, size == 64, Instruction.FMOVCPU2FPU);
    }

    private void fmovCpuFpuInstruction(Register dst, Register src, boolean is64bit, Instruction instr) {
        int sf = is64bit ? FP64.encoding | General64.encoding : FP32.encoding | General32.encoding;
        emitInt(sf | instr.encoding | FpConvertOp | rd(dst) | rs1(src));
    }

    /* Floating-point Move (immediate) (5.7.3) */

    /**
     * C7.2.132 Floating-point move immediate (scalar)
     *
     * Move immediate into floating-point register.
     *
     * @param size register size. Has to be 32 or 64.
     * @param dst floating point register. May not be null.
     * @param imm immediate that is loaded into dst. If size is 32 only float immediates can be
     *            loaded, i.e. (float) imm == imm must be true. In all cases
     *            {@code isFloatImmediate}, respectively {@code #isDoubleImmediate} must be true
     *            depending on size.
     */
    protected void fmov(int size, Register dst, double imm) {
        assert verifySizeAndRegistersF(size, dst);

        InstructionType type = floatFromSize(size);
        int immEncoding;
        if (type == FP64) {
            immEncoding = getDoubleImmediate(imm);
        } else {
            assert imm == (float) imm : "float mov must use an immediate that can be represented using a float.";
            immEncoding = getFloatImmediate((float) imm);
        }
        emitInt(type.encoding | FMOV.encoding | FpImmOp | immEncoding | rd(dst));
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
        long pattern = (bits >> 54) & NumUtil.getNbitNumberLong(8);
        if (pattern != 0 && pattern != NumUtil.getNbitNumberLong(8)) {
            return false;
        }
        // bits[62] and bits[61] are opposites.
        boolean result = ((bits ^ (bits << 1)) & (1L << 62)) != 0;
        return result;
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
     * C7.2.69 Convert float to double and vice-versa.
     *
     * @param dstSize size of target register in bits.
     * @param srcSize size of source register in bits.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fcvt(int dstSize, int srcSize, Register dst, Register src) {
        assert verifySizesAndRegistersFF(dstSize, srcSize, dst, src);
        assert dstSize != srcSize;

        if (srcSize == 32) {
            fpDataProcessing1Source(FCVTDS, dst, src, floatFromSize(srcSize));
        } else {
            fpDataProcessing1Source(FCVTSD, dst, src, floatFromSize(srcSize));
        }
    }

    /* Convert to Integer (5.7.4.2) */

    /**
     * C7.2.92 Floating-point Convert to Signed integer, rounding toward Zero.
     *
     * @param dstSize size of integer register. 32 or 64.
     * @param srcSize size of floating point register. 32 or 64.
     * @param dst general purpose register. May not be null, the zero-register or the stackpointer.
     * @param src floating point register. May not be null.
     */
    public void fcvtzs(int dstSize, int srcSize, Register dst, Register src) {
        assert verifySizesAndRegistersRF(dstSize, srcSize, dst, src);

        fcvtCpuFpuInstruction(FCVTZS, dst, src, generalFromSize(dstSize), floatFromSize(srcSize));
    }

    /* Convert from Integer (5.7.4.2) */
    /**
     * C7.2.236 Signed integer Convert to Floating-point (scalar).
     *
     * @param dstSize size of floating point register. 32 or 64.
     * @param srcSize size of integer register. 32 or 64.
     * @param dst floating point register. May not be null.
     * @param src general purpose register. May not be null or the stackpointer.
     */
    public void scvtf(int dstSize, int srcSize, Register dst, Register src) {
        assert verifySizesAndRegistersFZ(dstSize, srcSize, dst, src);

        fcvtCpuFpuInstruction(SCVTF, dst, src, floatFromSize(dstSize), generalFromSize(srcSize));
    }

    private void fcvtCpuFpuInstruction(Instruction instr, Register dst, Register src, InstructionType type1, InstructionType type2) {
        emitInt(type1.encoding | type2.encoding | instr.encoding | FpConvertOp | rd(dst) | rs1(src));
    }

    /* Floating-point Round to Integral (5.7.5) */

    /**
     * C7.2.168 Floating-point Round to Integral, toward Zero (scalar).
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void frintz(int size, Register dst, Register src) {
        assert verifySizeAndRegistersFF(size, dst, src);

        fpDataProcessing1Source(FRINTZ, dst, src, floatFromSize(size));
    }

    /**
     * C7.2.162 Floating-point Round to Integral, to nearest with ties to even (scalar).
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void frintn(int size, Register dst, Register src) {
        assert verifySizeAndRegistersFF(size, dst, src);

        fpDataProcessing1Source(FRINTN, dst, src, floatFromSize(size));
    }

    /**
     * C7.2.160 Floating-point Round to Integral, toward Minus infinity (scalar).
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void frintm(int size, Register dst, Register src) {
        assert verifySizeAndRegistersFF(size, dst, src);

        fpDataProcessing1Source(FRINTM, dst, src, floatFromSize(size));
    }

    /**
     * C7.2.164 Floatin-point Round to Integral, toward Plug infinity (scalar).
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void frintp(int size, Register dst, Register src) {
        assert verifySizeAndRegistersFF(size, dst, src);

        fpDataProcessing1Source(FRINTP, dst, src, floatFromSize(size));
    }

    /* Floating-point Arithmetic (1 source) (5.7.6) */

    /**
     * C7.2.46 Floating-point Absolute value (scalar).
     *
     * dst = |src|.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fabs(int size, Register dst, Register src) {
        assert verifySizeAndRegistersFF(size, dst, src);

        fpDataProcessing1Source(FABS, dst, src, floatFromSize(size));
    }

    /**
     * C7.2.140 Floating-point Negate (scalar).
     *
     * dst = -neg.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fneg(int size, Register dst, Register src) {
        assert verifySizeAndRegistersFF(size, dst, src);

        fpDataProcessing1Source(FNEG, dst, src, floatFromSize(size));
    }

    /**
     * C7.2.172 Floating-point Square Root (scalar).
     *
     * dst = Sqrt(src).
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src floating point register. May not be null.
     */
    public void fsqrt(int size, Register dst, Register src) {
        assert verifySizeAndRegistersFF(size, dst, src);

        fpDataProcessing1Source(FSQRT, dst, src, floatFromSize(size));
    }

    private void fpDataProcessing1Source(Instruction instr, Register dst, Register src, InstructionType type) {
        emitInt(type.encoding | instr.encoding | Fp1SourceOp | rd(dst) | rs1(src));
    }

    /* Floating-point Arithmetic (2 source) (5.7.7) */

    /**
     * C7.2.50 Floating-point Add (scalar).
     *
     * dst = src1 + src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fadd(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersFFF(size, dst, src1, src2);

        fpDataProcessing2Source(FADD, dst, src1, src2, floatFromSize(size));
    }

    /**
     * C7.2.174 Floating-point Subtract (scalar).
     *
     * dst = src1 - src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fsub(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersFFF(size, dst, src1, src2);

        fpDataProcessing2Source(FSUB, dst, src1, src2, floatFromSize(size));
    }

    /**
     * C7.2.136 Floating-point Multiply (scalar).
     *
     * dst = src1 * src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fmul(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersFFF(size, dst, src1, src2);

        fpDataProcessing2Source(FMUL, dst, src1, src2, floatFromSize(size));
    }

    /**
     * C7.2.98 Floating-point Divide (scalar)
     *
     * dst = src1 / src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fdiv(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersFFF(size, dst, src1, src2);

        fpDataProcessing2Source(FDIV, dst, src1, src2, floatFromSize(size));
    }

    private void fpDataProcessing2Source(Instruction instr, Register dst, Register src1, Register src2, InstructionType type) {
        emitInt(type.encoding | instr.encoding | Fp2SourceOp | rd(dst) | rs1(src1) | rs2(src2));
    }

    /**
     * C7.2.102 Floating-point Maximum (scalar).
     *
     * dst = src1 > src2 ? src1 : src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fmax(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersFFF(size, dst, src1, src2);

        fpDataProcessing2Source(FMAX, dst, src1, src2, floatFromSize(size));
    }

    /**
     * C7.2.112 Floating-point Minimum (scalar).
     *
     * dst = src1 < src2 ? src1 : src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fmin(int size, Register dst, Register src1, Register src2) {
        assert verifySizeAndRegistersFFF(size, dst, src1, src2);

        fpDataProcessing2Source(FMIN, dst, src1, src2, floatFromSize(size));
    }

    /* Floating-point Multiply-Add (5.7.9) */

    /**
     * C7.2.100 Floating-point fused Multiply-Add (scalar).
     *
     * dst = src1 * src2 + src3.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param src3 floating point register. May not be null.
     */
    public void fmadd(int size, Register dst, Register src1, Register src2, Register src3) {
        assert verifySizeAndRegistersFFFF(size, dst, src1, src2, src3);

        fpDataProcessing3Source(FMADD, dst, src1, src2, src3, floatFromSize(size));
    }

    /**
     * C7.2.133 Floating-point Fused Multiply-Subtract (scalar).
     *
     * dst = src3 - src1 * src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param src3 floating point register. May not be null.
     */
    protected void fmsub(int size, Register dst, Register src1, Register src2, Register src3) {
        assert verifySizeAndRegistersFFFF(size, dst, src1, src2, src3);

        fpDataProcessing3Source(FMSUB, dst, src1, src2, src3, floatFromSize(size));
    }

    private void fpDataProcessing3Source(Instruction instr, Register dst, Register src1, Register src2, Register src3, InstructionType type) {
        emitInt(type.encoding | instr.encoding | Fp3SourceOp | rd(dst) | rs1(src1) | rs2(src2) | rs3(src3));
    }

    /* Floating-point Comparison (5.7.10) */

    /**
     * C7.2.66 Floating-point quiet Compare (scalar, register).
     *
     * Compares src1 to src2.
     *
     * @param size register size.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fcmp(int size, Register src1, Register src2) {
        assert verifySizeAndRegistersFF(size, src1, src2);

        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCMP.encoding | FpCmpOp | rs1(src1) | rs2(src2));
    }

    /**
     * C7.2.67 Floating-point signaling Compare (scalar).
     *
     * Compares src1 to src2.
     *
     * From the manual: "This instruction raises an Invalid Operation floating-point exception if
     * either or both of the operands is any type of NaN."
     *
     * @param size register size.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     */
    public void fcmpe(int size, Register src1, Register src2) {
        assert verifySizeAndRegistersFF(size, src1, src2);

        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCMP.encoding | FpCmpeOp | rs1(src1) | rs2(src2));
    }

    /**
     * C7.2.54 Floating-point Conditional quiet Compare (scalar).
     *
     * NZCV = fcmp(src1, src2) if condition else uimm4.
     *
     * @param size register size.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param uimm4 condition flags that are used if condition is false.
     * @param condition every condition allowed. May not be null.
     */
    public void fccmp(int size, Register src1, Register src2, int uimm4, ConditionFlag condition) {
        assert verifySizeAndRegistersFF(size, src1, src2);
        assert NumUtil.isUnsignedNbit(4, uimm4);

        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCCMP.encoding | uimm4 | condition.encoding << ConditionalConditionOffset | rs1(src1) | rs2(src2));
    }

    /**
     * C7.2.66 Floating-point quiet Compare Equal (scalar, zero variant).
     *
     * Compare register to 0.0 .
     *
     * @param size register size.
     * @param src floating point register. May not be null.
     */
    public void fcmpZero(int size, Register src) {
        assert verifySizeAndRegistersF(size, src);

        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCMPZERO.encoding | FpCmpOp | rs1(src));
    }

    /**
     * C7.2.67 Floating-point signaling Compare Equal (scalar, zero variant).
     *
     * Signalling compare register to 0.0 .
     *
     * @param size register size.
     * @param src floating point register. May not be null.
     */
    public void fcmpeZero(int size, Register src) {
        assert verifySizeAndRegistersF(size, src);

        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCMPZERO.encoding | FpCmpeOp | rs1(src));
    }

    /* Floating-point Conditional Select (5.7.11) */

    /**
     * C7.2.68 Floating-point Conditional Select (scalar).
     *
     * dst = condition ? src1 : src2.
     *
     * @param size register size.
     * @param dst floating point register. May not be null.
     * @param src1 floating point register. May not be null.
     * @param src2 floating point register. May not be null.
     * @param condition every condition allowed. May not be null.
     */
    public void fcsel(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        assert verifySizeAndRegistersFFF(size, dst, src1, src2);

        InstructionType type = floatFromSize(size);
        emitInt(type.encoding | FCSEL.encoding | rd(dst) | rs1(src1) | rs2(src2) | condition.encoding << ConditionalConditionOffset);
    }

    /* Debug exceptions (5.9.1.2) */

    /**
     * C6.2.93 Halt instruction.
     *
     * Halting mode software breakpoint: Enters halting mode debug state if enabled, else treated as
     * UNALLOCATED instruction.
     *
     * @param uimm16 Arbitrary 16-bit unsigned payload.
     */
    protected void hlt(int uimm16) {
        exceptionInstruction(HLT, uimm16);
    }

    /**
     * C6.2.38 Breakpoint instruction.
     *
     * Monitor mode software breakpoint: exception routed to a debug monitor executing in a higher
     * exception level.
     *
     * @param uimm16 Arbitrary 16-bit unsigned payload.
     */
    protected void brk(int uimm16) {
        exceptionInstruction(BRK, uimm16);
    }

    private void exceptionInstruction(Instruction instr, int uimm16) {
        ImmediateOpChecks.validateUnsigned(16, uimm16);

        emitInt(instr.encoding | ExceptionOp | uimm16 << SystemImmediateOffset);
    }

    /* Architectural hints (5.9.4) */
    public enum SystemHint {
        NOP(0x0),
        YIELD(0x1),
        WFE(0x2),
        WFI(0x3),
        SEV(0x4),
        SEVL(0x5),
        CSDB(0x14);

        private final int encoding;

        SystemHint(int encoding) {
            this.encoding = encoding;
        }
    }

    /**
     * C6.2.92 Hint instruction.
     *
     * @param hint Can be any of the defined hints. May not be null.
     */
    protected void hint(SystemHint hint) {
        emitInt(HINT.encoding | hint.encoding << SystemImmediateOffset);
    }

    /**
     * C6.2.54 Clear Exclusive.
     *
     * Clears the local record of the executing processor that an address has had a request for an
     * exclusive access.
     */
    protected void clrex() {
        emitInt(CLREX.encoding);
    }

    /**
     * Barrier definitions for AArch64.
     *
     * We only need synchronization across the inner shareable domain (see B2-90 in the Reference
     * documentation).
     */
    public enum BarrierKind {
        LOAD_ANY(0x9, "ISHLD"),
        STORE_STORE(0xA, "ISHST"),
        ANY_ANY(0xB, "ISH"),
        SYSTEM(0xF, "SYS");

        public final int encoding;
        public final String optionName;

        BarrierKind(int encoding, String optionName) {
            this.encoding = encoding;
            this.optionName = optionName;
        }
    }

    /**
     * C6.2.80 Data Memory Barrier.
     *
     * @param barrierKind barrier that is issued. May not be null.
     */
    public void dmb(BarrierKind barrierKind) {
        emitInt(DMB.encoding | BarrierOp | barrierKind.encoding << BarrierKindOffset);
    }

    /**
     * C6.2.82 Data Synchronization Barrier.
     *
     * @param barrierKind barrier that is issued. May not be null.
     */
    public void dsb(BarrierKind barrierKind) {
        emitInt(DSB.encoding | BarrierOp | barrierKind.encoding << BarrierKindOffset);
    }

    /**
     * C6.2.97 Instruction Synchronization Barrier.
     */
    public void isb() {
        emitInt(ISB.encoding | BarrierOp | BarrierKind.SYSTEM.encoding << BarrierKindOffset);
    }

    /**
     * C6.2.194 Move System Register<br>
     * <p>
     * Reads an AArch64 System register into a general-purpose register.
     */
    public void mrs(Register dst, SystemRegister systemRegister) {
        assert verifyRegistersR(dst);

        emitInt(MRS.encoding | systemRegister.encoding() | rt(dst));
    }

    /**
     * C6.2.196 Move general-purpose register to System Register<br>
     * <p>
     * Writes an AArch64 System register from general-purpose register.
     */
    public void msr(SystemRegister systemRegister, Register src) {
        assert verifyRegistersZ(src);

        emitInt(MRS.encoding | systemRegister.encoding() | rt(src));
    }

    /**
     * C6.2.75 Data Cache operation.
     */
    public void dc(DataCacheOperationType type, Register src) {
        assert verifyRegistersR(src);

        emitInt(DC.encoding | type.encoding() | rt(src));
    }

    public void annotatePatchingImmediate(int pos, Instruction instruction, int operandSizeBits, int offsetBits, int shift) {
        if (codePatchingAnnotationConsumer != null) {
            codePatchingAnnotationConsumer.accept(new SingleInstructionAnnotation(pos, instruction, operandSizeBits, offsetBits, shift));
        }
    }

    public abstract static class PatchableCodeAnnotation extends CodeAnnotation {

        /**
         * The position (bytes from the beginning of the method) of the annotated instruction.
         */
        public final int instructionPosition;

        PatchableCodeAnnotation(int instructionPosition) {
            this.instructionPosition = instructionPosition;
        }

        /**
         * Patch the code buffer.
         *
         * @param startAddress starting address for instruction sequence to patch
         * @param relative pc-relative value
         * @param code machine code generated for this method
         */
        abstract void patch(long startAddress, int relative, byte[] code);
    }

    /**
     * Contains methods for patching instructions within AArch64.
     */
    public static class PatcherUtil {
        /**
         * Convert byte array instruction into an int.
         */
        public static int readInstruction(byte[] code, int pos) {
            return ByteBuffer.wrap(code, pos, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }

        /**
         * Write int representation of instruction to byte array.
         */
        public static void writeInstruction(byte[] code, int pos, int val) {
            ByteBuffer.wrap(code, pos, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(val);
        }

        /**
         * Method to patch a series a bytes within an instruction.
         *
         * @param original Initial instruction value.
         * @param value The value to be given to the series of bytes.
         * @param bitsUsed The number of bits to patch within each byte.
         * @param offsets Where within the bytes the value should be added.
         * @return New patched instruction value.
         */
        public static int patchBitSequence(int original, int value, int[] bitsUsed, int[] offsets) {
            assert bitsUsed.length == 4 && offsets.length == 4 : "bitsUsed and offsets parameter should be of length 4";

            int result = 0;
            int curValue = value;
            for (int i = 0; i < bitsUsed.length; i++) {
                int usedBits = bitsUsed[i];
                if (usedBits == 0) {
                    // want to retain the original value
                    result = result | (original & (0xFF << (8 * i)));
                }

                int offset = offsets[i];
                int mask = (1 << usedBits) - 1;

                byte patchTarget = (byte) ((original >> (8 * i)) & 0xFF);
                byte patch = (byte) (((curValue & mask) << offset) & 0xFF);
                byte retainedPatchTarget = (byte) (patchTarget & ((~(mask << offset)) & 0xFF));
                int patchValue = (retainedPatchTarget | patch) & 0xFF;
                result = result | (patchValue << (8 * i));
                curValue = curValue >> usedBits;
            }
            return result;
        }

        /**
         * Patches adrp instruction with provided imm21 value.
         *
         * @param original Original instruction value.
         * @param imm21 The value to patch.
         * @return New patched instruction value.
         */
        public static int patchAdrpHi21(int original, int imm21) {
            assert (imm21 & 0x1FFFFF) == imm21;

            // adrp imm_hi bits
            int immHi = (imm21 >> 2) & 0x7FFFF;
            int immLo = imm21 & 0x3;
            int[] adrpBits = {3, 8, 8, 2};
            int[] adrpOffsets = {5, 0, 0, 5};
            int patch = (immLo << 19) | immHi;
            return patchBitSequence(original, patch, adrpBits, adrpOffsets);
        }

        /**
         * Patches add with provided imm12 value. Note this is expected to be used in conjunction
         * with an adrp to form a 33-bit pc-relative address.
         *
         * @param original Original instruction value.
         * @param imm12 The value to patch.
         * @return New patched instruction value.
         */
        public static int patchAddLo12(int original, int imm12) {
            assert (imm12 & 0xFFF) == imm12;

            int[] addBits = {0, 6, 6, 0};
            int[] addOffsets = {0, 2, 0, 0};
            return PatcherUtil.patchBitSequence(original, imm12, addBits, addOffsets);
        }

        /**
         * Patches lar (unsigned, scaled) with provided imm12 value. Note this is expected to be
         * used in conjunction with an adrp to perform a 33-bit pc-relative load.
         *
         * @param original Original instruction value.
         * @param imm12 The *unscaled* value to patch. The method performs all necessary shifting.
         * @param srcSize The memory operation size. This determines the scaling factor.
         * @return New patched instruction value.
         */
        public static int patchLdrLo12(int original, int imm12, int srcSize) {
            assert (imm12 & 0xFFF) == imm12;
            assert srcSize == 64 || srcSize == 32 || srcSize == 16 || srcSize == 8;
            int shiftSize = srcSize == 64 ? 3 : (srcSize == 32 ? 2 : (srcSize == 16 ? 1 : 0));
            assert (shiftSize == 0) || ((imm12 & ((1 << shiftSize) - 1)) == 0);

            int shiftedValue = (imm12 & 0xFFF) >> shiftSize;
            int[] ldrBits = {0, 6, 6, 0};
            int[] ldrOffsets = {0, 2, 0, 0};
            return PatcherUtil.patchBitSequence(original, shiftedValue, ldrBits, ldrOffsets);
        }

        /**
         * Patches mov instruction with provided imm16 value.
         *
         * @param original Original instruction value.
         * @param imm16 The value to patch.
         * @return New patched instruction value.
         */
        public static int patchMov(int original, int imm16) {
            assert (imm16 & 0xFFFF) == imm16;
            int[] movBits = {3, 8, 5, 0};
            int[] movOffsets = {5, 0, 0, 0};
            return PatcherUtil.patchBitSequence(original, imm16, movBits, movOffsets);
        }

        /**
         * Computes the page-relative difference between two addresses.
         */
        public static int computeRelativePageDifference(long target, long curPos, int pageSize) {
            int relative = (int) (target / pageSize - curPos / pageSize);
            return relative;
        }
    }

    public static class SingleInstructionAnnotation extends PatchableCodeAnnotation {

        /**
         * The size of the operand, in bytes.
         */
        public final int operandSizeBits;
        public final int offsetBits;
        public final Instruction instruction;
        public final int shift;

        SingleInstructionAnnotation(int instructionPosition, Instruction instruction, int operandSizeBits, int offsetBits, int shift) {
            super(instructionPosition);
            this.operandSizeBits = operandSizeBits;
            this.offsetBits = offsetBits;
            this.shift = shift;
            this.instruction = instruction;
        }

        @Override
        public String toString() {
            return "SINGLE_INSTRUCTION";
        }

        @Override
        public void patch(long startAddress, int relative, byte[] code) {
            boolean expectedInstruction = instruction == Instruction.B || instruction == Instruction.BL;
            GraalError.guarantee(expectedInstruction, "trying to patch an unexpected instruction");

            int curValue = relative; // B & BL are PC-relative
            assert (curValue & ((1 << shift) - 1)) == 0 : "relative offset has incorrect alignment";
            curValue = curValue >> shift;
            GraalError.guarantee(NumUtil.isSignedNbit(operandSizeBits, curValue), "value too large to fit into space");

            // fill in immediate operand of operandSizeBits starting at offsetBits within
            // instruction
            int bitsRemaining = operandSizeBits;
            int offsetRemaining = offsetBits;

            int[] bitsUsed = new int[4];
            int[] offsets = new int[4];

            for (int i = 0; i < 4; ++i) {
                if (offsetRemaining >= 8) {
                    offsetRemaining -= 8;
                    continue;
                }
                offsets[i] = offsetRemaining;
                // number of bits to be filled within this byte
                int bits = Math.min(8 - offsetRemaining, bitsRemaining);
                bitsUsed[i] = bits;
                bitsRemaining -= bits;

                offsetRemaining = 0;
            }
            int originalInst = PatcherUtil.readInstruction(code, instructionPosition);
            int newInst = PatcherUtil.patchBitSequence(originalInst, curValue, bitsUsed, offsets);
            PatcherUtil.writeInstruction(code, instructionPosition, newInst);
        }
    }

}
