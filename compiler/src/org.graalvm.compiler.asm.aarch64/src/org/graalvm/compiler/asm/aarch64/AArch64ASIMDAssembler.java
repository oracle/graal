package org.graalvm.compiler.asm.aarch64;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.PlatformKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static jdk.vm.ci.aarch64.AArch64.CPU;
import static jdk.vm.ci.aarch64.AArch64.SIMD;

import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.rs1;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.rd;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.rs2;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.rn;

/**
 * This class encapsulates the AArch64 Advanced SIMD (ASIMD) assembler support. The
 * documentation below heavily references the Arm Architecture Reference Manual version G-a. The
 * latest copy of the manual can be found
 * <a href="https://developer.arm.com/documentation/ddi0487/latest">here</a>.
 *
 * <p>
 * In order to minimize confusion between ASIMD and similarly named General-Purpose/FP
 * instructions, the following naming convention is used:
 * <ul>
 * <li>[instr]Vector: instructions operating on vector elements.</li>
 * <li>[instr]Scalar: instructions using a single lane. These instructions are primarily used to
 * perform integer operations within vector registers without having to transfer the values back
 * to the general-purpose registers.</li>
 * <li>[instr]General: instructions which use a general-purpose register, in addition to ASIMD
 * registers.</li>
 * </ul>
 *
 * <p>
 * In order to minimize confusion between ASIMD and similarly named General-Purpose/FP
 * instructions, each ASIMD instruction has a suffix indicate the type of each of the registers the instruction
 * uses in the form where:
 * <ul>
 * <li>V: ASIMD register.</li>
 * <li>S: ASIMD scalar register. This is primarily used to
 *  * perform integer operations within vector registers without having to transfer the values back
 *  * to the general-purpose register. </li>
 * <li>X: ASIMD register index (V[idx]).</li>
 * <li>R: General purpose register.</li>
 * </ul>
 */
public abstract class AArch64ASIMDAssembler {

    /**
     * Calculates and maintains a mapping of all possible ASIMD immediate values.
     * <p>
     * ASIMD immediates use the form op:abc:cmode:defgh with bits 29:18-16:15-12:9-5. How these
     * bits are expanded into 64-bit values is codified in
     * shared/functions/vectorAdvSIMDExpandImm (J1-8208).
     */
    public static class ASIMDImmediateTable {

        private static final int ImmediateOpOffset = 29;
        private static final int ImmediateCmodeOffset = 12;
        private static final int ImmediateABCOffset = 16;
        private static final int ImmediateDEFGHOffset = 5;

        public enum BitValues {
            ZERO(0),
            ONE(1),
            ANY(0, 1);

            final int[] values;

            BitValues(int... values) {
                this.values = values;
            }
        }

        public static final ASIMDImmediateTable.ImmediateEncodings[] IMMEDIATE_TABLE = buildImmediateTable();

        /**
         * Tests whether an immediate can be encoded within an ASIMD instruction using the
         * provided ImmediateOp mode.
         */
        public static boolean isEncodable(long imm, ImmediateOp op) {
            int pos = Arrays.binarySearch(IMMEDIATE_TABLE, ASIMDImmediateTable.ImmediateEncodings.createRepresentativeEncoding(imm));
            if (pos < 0) {
                return false;
            }
            ASIMDImmediateTable.ImmediateEncodings immediate = IMMEDIATE_TABLE[pos];
            for (byte cmodeOpEncoding : ImmediateOp.getCmodeOpEncodings(op)) {
                if (immediate.validEncoding[cmodeOpEncoding]) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns the instruction encoding for immediate using the provided ImmediateOp mode.
         */
        public static int getEncoding(long imm, ImmediateOp op) {
            assert isEncodable(imm, op);

            int pos = Arrays.binarySearch(IMMEDIATE_TABLE, ASIMDImmediateTable.ImmediateEncodings.createRepresentativeEncoding(imm));
            ASIMDImmediateTable.ImmediateEncodings immediate = IMMEDIATE_TABLE[pos];
            for (byte cmodeOpEncoding : ImmediateOp.getCmodeOpEncodings(op)) {
                if (immediate.validEncoding[cmodeOpEncoding]) {
                    int imm8Encoding = getImm8Encoding(immediate.imm8[cmodeOpEncoding]);
                    int opBit = cmodeOpEncoding & 0x1;
                    int cmodeBits = (cmodeOpEncoding >> 1) & 0xF;
                    return imm8Encoding | opBit << ImmediateOpOffset | cmodeBits << ImmediateCmodeOffset;
                }
            }
            throw GraalError.shouldNotReachHere();
        }

        private static int getImm8Encoding(byte imm8) {
            int encoding = ((imm8 >>> 5) & 0x7) << ImmediateABCOffset | (imm8 & 0x1F) << ImmediateDEFGHOffset;
            return encoding;
        }

        private static long[] asBitArray(long imm8) {
            long[] bitArray = new long[8];
            long remaining = imm8;
            for (int i = 0; i < 8; i++) {
                bitArray[i] = remaining & 0x1L;
                remaining = remaining >> 1;
            }
            return bitArray;
        }

        private static int getCmodeOpEncoding(int cmodeBits3to1, int cmodeBit0, int op) {
            int encoding = cmodeBits3to1 << 2 | cmodeBit0 << 1 | op;
            return encoding;
        }

        private static long replicateBit(long bit, int repeatNum) {
            if (bit == 0) {
                return 0;
            } else {
                assert bit == 1;
                return (1 << repeatNum) - 1;
            }
        }

        private static long notBit(long bit) {
            if (bit == 0) {
                return 1;
            } else {
                assert bit == 1;
                return 0;
            }
        }

        /**
         * Adds new immediate encoding to the appropriate {@link ASIMDImmediateTable.ImmediateEncodings} object.
         */
        private static void registerImmediate(Map<Long, ASIMDImmediateTable.ImmediateEncodings> immediateMap, long imm64, long imm8, int cmodeBits3to1, ASIMDImmediateTable.BitValues cmodeBit0, ASIMDImmediateTable.BitValues op) {
            immediateMap.compute(imm64,
                    (k, v) -> v == null ? new ASIMDImmediateTable.ImmediateEncodings(k, (byte) imm8, cmodeBits3to1, cmodeBit0, op) : v.addEncoding(imm64, (byte) imm8, cmodeBits3to1, cmodeBit0, op));
        }

        /**
         * This method generates all possible encodings and stores them in an array sorted by
         * the generated 64-bit values. This table is generated based on the
         * shared/functions/vector/AdvSIMDExpandIMM function (J1-8208).
         */
        private static ASIMDImmediateTable.ImmediateEncodings[] buildImmediateTable() {
            Map<Long, ASIMDImmediateTable.ImmediateEncodings> immediateMap = new HashMap<>();

            /*
             * Generating all possible immediates and linking them to the proper cmode/op
             * values.
             */
            for (long imm8 = 0; imm8 < 256; imm8++) {
                long imm64;

                /* cmode<3:1> == 0 */
                imm64 = imm8 << 32 | imm8;
                registerImmediate(immediateMap, imm64, imm8, 0, ASIMDImmediateTable.BitValues.ANY, ASIMDImmediateTable.BitValues.ANY);

                /* cmode<3:1> == 1 */
                imm64 = imm8 << 40 | imm8 << 8;
                registerImmediate(immediateMap, imm64, imm8, 1, ASIMDImmediateTable.BitValues.ANY, ASIMDImmediateTable.BitValues.ANY);

                /* cmode<3:1> == 2 */
                imm64 = imm8 << 48 | imm8 << 16;
                registerImmediate(immediateMap, imm64, imm8, 2, ASIMDImmediateTable.BitValues.ANY, ASIMDImmediateTable.BitValues.ANY);

                /* cmode<3:1> == 3 */
                imm64 = imm8 << 56 | imm8 << 24;
                registerImmediate(immediateMap, imm64, imm8, 3, ASIMDImmediateTable.BitValues.ANY, ASIMDImmediateTable.BitValues.ANY);

                /* cmode<3:1> == 4 */
                imm64 = imm8 << 48 | imm8 << 32 | imm8 << 16 | imm8;
                registerImmediate(immediateMap, imm64, imm8, 4, ASIMDImmediateTable.BitValues.ANY, ASIMDImmediateTable.BitValues.ANY);

                /* cmode<3:1> == 5 */
                imm64 = imm8 << 56 | imm8 << 40 | imm8 << 24 | imm8 << 8;
                registerImmediate(immediateMap, imm64, imm8, 5, ASIMDImmediateTable.BitValues.ANY, ASIMDImmediateTable.BitValues.ANY);

                /* cmove<3:1> == 6 */
                /* cmode<0> == 0 */
                imm64 = imm8 << 40 | 0xFFL << 32 | imm8 << 8 | 0xFFL;
                registerImmediate(immediateMap, imm64, imm8, 6, ASIMDImmediateTable.BitValues.ZERO, ASIMDImmediateTable.BitValues.ANY);
                /* cmode<0> == 1 */
                imm64 = imm8 << 48 | 0xFFFFL << 32 | imm8 << 16 | 0xFFFFL;
                registerImmediate(immediateMap, imm64, imm8, 6, ASIMDImmediateTable.BitValues.ONE, ASIMDImmediateTable.BitValues.ANY);

                /* cmode <3:1> == 7 */
                long[] bitArray = asBitArray(imm8);
                /* cmode<0> == 0 && op == 0 */
                imm64 = imm8 << 56 | imm8 << 48 | imm8 << 40 | imm8 << 32 | imm8 << 24 | imm8 << 16 | imm8 << 8 | imm8;
                registerImmediate(immediateMap, imm64, imm8, 7, ASIMDImmediateTable.BitValues.ZERO, ASIMDImmediateTable.BitValues.ZERO);
                /* cmode<0> == 0 && op == 1 */
                imm64 = replicateBit(bitArray[7], 8) << 56 |
                        replicateBit(bitArray[6], 8) << 48 |
                        replicateBit(bitArray[5], 8) << 40 |
                        replicateBit(bitArray[4], 8) << 32 |
                        replicateBit(bitArray[3], 8) << 24 |
                        replicateBit(bitArray[2], 8) << 16 |
                        replicateBit(bitArray[1], 8) << 8 |
                        replicateBit(bitArray[0], 8);
                registerImmediate(immediateMap, imm64, imm8, 7, ASIMDImmediateTable.BitValues.ZERO, ASIMDImmediateTable.BitValues.ONE);
                /* cmode<0> == 1 && op == 0 */
                long imm32 = bitArray[7] << 31 | notBit(bitArray[6]) << 30 | replicateBit(bitArray[6], 5) << 25 | (imm8 & 0x3F) << 19;
                imm64 = imm32 << 32 | imm32;
                registerImmediate(immediateMap, imm64, imm8, 7, ASIMDImmediateTable.BitValues.ONE, ASIMDImmediateTable.BitValues.ZERO);
                /* cmode<0> == 1 && op == 1 */
                imm64 = bitArray[7] << 63 | notBit(bitArray[6]) << 62 | replicateBit(bitArray[6], 8) << 54 | (imm8 & 0x3F) << 48;
                registerImmediate(immediateMap, imm64, imm8, 7, ASIMDImmediateTable.BitValues.ONE, ASIMDImmediateTable.BitValues.ONE);
            }

            ASIMDImmediateTable.ImmediateEncodings[] table = immediateMap.values().toArray(new ASIMDImmediateTable.ImmediateEncodings[0]);
            Arrays.sort(table);

            return table;
        }

        /*
         * Contains the encodings associated with each 64-bit immediate value. Since multiple
         * cmode:op combinations can be used to represent the same value, each possible encoding
         * combination must be recorded.
         */
        private static final class ImmediateEncodings implements Comparable<ASIMDImmediateTable.ImmediateEncodings> {

            public final long imm;

            /* All of these operations are indexed by the bits cmode<3:0>:op. */
            private final boolean[] validEncoding;
            private final byte[] imm8;

            private ImmediateEncodings(long imm) {
                this.imm = imm;
                this.validEncoding = null;
                this.imm8 = null;
            }

            public static ASIMDImmediateTable.ImmediateEncodings createRepresentativeEncoding(long imm) {
                return new ASIMDImmediateTable.ImmediateEncodings(imm);
            }

            protected ImmediateEncodings(long imm, byte imm8, int cmodeBits3to1, ASIMDImmediateTable.BitValues cmodeBit0, ASIMDImmediateTable.BitValues op) {
                this.imm = imm;
                this.validEncoding = new boolean[32];
                this.imm8 = new byte[32];
                for (int bit0 : cmodeBit0.values) {
                    for (int opBit : op.values) {
                        int cmodeOpEncoding = getCmodeOpEncoding(cmodeBits3to1, bit0, opBit);
                        assert !validEncoding[cmodeOpEncoding];
                        this.validEncoding[cmodeOpEncoding] = true;
                        this.imm8[cmodeOpEncoding] = imm8;
                    }
                }
            }

            public ASIMDImmediateTable.ImmediateEncodings addEncoding(long imm64, byte imm8, int cmodeBits3to1, ASIMDImmediateTable.BitValues cmodeBit0, ASIMDImmediateTable.BitValues op) {
                assert imm64 == this.imm;
                for (int bit0 : cmodeBit0.values) {
                    for (int opBit : op.values) {
                        int cmodeOpEncoding = getCmodeOpEncoding(cmodeBits3to1, bit0, opBit);
                        assert !validEncoding[cmodeOpEncoding];
                        this.validEncoding[cmodeOpEncoding] = true;
                        this.imm8[cmodeOpEncoding] = imm8;
                    }
                }
                return this;
            }

            @Override
            public int compareTo(ASIMDImmediateTable.ImmediateEncodings o) {
                return Long.compare(imm, o.imm);
            }
        }
    }

    /**
     * Enumeration for all vector instructions which can have an immediate operand.
     */
    public enum ImmediateOp {
        MOVI,
        MVNI,
        ORR,
        BIC,
        FMOVSP,
        FMOVDP;

        private static byte[] moviEncodings = {
                /* 0xx00 */
                0b00000,
                0b00100,
                0b01000,
                0b01100,
                /* 10x00 */
                0b10000,
                0b10100,
                /* 110x0 */
                0b11000,
                0b11010,
                /* 1110x */
                0b11100,
                0b11101,
                /* 11110 */
                0b11110,
        };

        private static byte[] mvniEncodings = {
                /* 0xx01 */
                0b00001,
                0b00101,
                0b01001,
                0b01101,
                /* 10x01 */
                0b10001,
                0b10101,
                /* 110x1 */
                0b11001,
                0b11011,
        };

        private static byte[] orrEncodings = {
                /* 0xx10 */
                0b00010,
                0b00110,
                0b01010,
                0b01110,
                /* 10x10 */
                0b10010,
                0b10110,
        };

        private static byte[] bicEncodings = {
                /* 0xx11 */
                0b00011,
                0b00111,
                0b01011,
                0b01111,
                /* 10x11 */
                0b10011,
                0b10111,
        };

        private static byte[] fmovSPEncodings = {
                /* 11110 */
                0b11110
        };

        private static byte[] fmovDPEncodings = {
                /* 11111 */
                0b11111
        };

        /**
         * Returns all valid cmode:op encodings for the requested immediate op.
         */
        public static byte[] getCmodeOpEncodings(ImmediateOp op) {
            switch (op) {
                case MOVI:
                    return moviEncodings;
                case MVNI:
                    return mvniEncodings;
                case ORR:
                    return orrEncodings;
                case BIC:
                    return bicEncodings;
                case FMOVSP:
                    return fmovSPEncodings;
                case FMOVDP:
                    return fmovDPEncodings;
            }
            throw GraalError.shouldNotReachHere();
        }

    }

    /**
     * Enumeration of all different SIMD operation sizes.
     */
    public enum ASIMDSize {
        HalfReg(64),
        FullReg(128);

        private final int nbits;

        ASIMDSize(int nbits) {
            this.nbits = nbits;
        }

        public int bits() {
            return nbits;
        }

        public int bytes() {
            return nbits / Byte.SIZE;
        }

        public static ASIMDSize fromVectorKind(PlatformKind kind) {
            assert kind instanceof AArch64Kind;
            assert kind.getVectorLength() > 0;
            int bitSize = kind.getSizeInBytes() * Byte.SIZE;
            assert bitSize == 32 || bitSize == 64 || bitSize == 128;
            return bitSize == 128 ? FullReg : HalfReg;
        }
    }

    /**
     * Enumeration of all different lane types of SIMD register.
     * <p>
     * Byte(B):8b/lane; HalfWord(H):16b/lane; Word(S):32b/lane; DoubleWord(D):64b/lane.
     */
    public enum ElementSize {
        Byte(0, 8),
        HalfWord(1, 16),
        Word(2, 32),
        DoubleWord(3, 64);

        private final int encoding;
        private final int nbits;

        ElementSize(int encoding, int nbits) {
            this.encoding = encoding;
            this.nbits = nbits;
        }

        public int bits() {
            return nbits;
        }

        public int bytes() {
            return nbits / java.lang.Byte.SIZE;
        }

        public static ElementSize fromKind(PlatformKind kind) {
            switch (((AArch64Kind) kind).getScalar()) {
                case BYTE:
                    return Byte;
                case WORD:
                    return HalfWord;
                case DWORD:
                case SINGLE:
                    return Word;
                case QWORD:
                case DOUBLE:
                    return DoubleWord;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }

        private static ElementSize fromSize(int size) {
            switch (size) {
                case 8:
                    return Byte;
                case 16:
                    return HalfWord;
                case 32:
                    return Word;
                case 64:
                    return DoubleWord;
                default:
                    throw GraalError.shouldNotReachHere("Invalid ASIMD element size.");
            }
        }

        public ElementSize expand() {
            return ElementSize.fromSize(nbits * 2);
        }

        public ElementSize narrow() {
            return ElementSize.fromSize(nbits / 2);
        }
    }

    /**
     * Encodings for ASIMD instructions. These encodings are based on the encodings described in
     * C4.1.6.
     */
    private static final int UBit = 0b1 << 29;

    public enum ASIMDInstruction {


        /* Advanced SIMD extract. (C4-356). */
        EXT(0b00 << 22),

        /* Advanced SIMD copy (C4-356). */
        DUPELEM(0b0000 << 11),
        DUPGEN(0b0001 << 11),
        SMOV(0b0101 << 11),
        UMOV(0b0111 << 11),

        /* Advanced SIMD two-register miscellaneous (C4-361). */
        /* size xx */
        CNT(0b00101 << 12),
        CMGT_ZERO(0b01000 << 12),
        CMEQ_ZERO(0b01001 << 12),
        CMLT_ZERO(0b01010 << 12),
        ABS(0b01011 << 12),
        XTN(0b10010 << 12),
        /* size 0x */
        FCVTN(0b10110 << 12),
        FCVTL(0b10111 << 12),
        /* size 1x */
        FCVTZS(0b11011 << 12),
        SCVTF(0b11101 << 12),
        FABS(0b01111 << 12),
        /* UBit 1, size xx */
        NEG(UBit | 0b01011 << 12),
        /* UBit 1, size 1x */
        NOT(UBit | 0b00101 << 12),
        FNEG(UBit | 0b01111 << 12),
        FSQRT(UBit | 0b11111 << 12),

        /* Advanced SIMD across lanes (C4-364). */
        SADDLV(0b00011 << 12),
        ADDV(0b11011 << 12),
        UADDLV(UBit | 0b00011 << 12),

        /* Advanced SIMD three different (C4-365) */
        SMLAL(0b1000 << 12),
        SMLSL(0b1010 << 12),
        UMLAL(UBit | 0b1000 << 12),
        UMLSL(UBit | 0b1010 << 12),

        /*
         * Advanced SIMD three same (C4-366) & Advanced SIMD scalar three same (C4-349).
         */
        /* size xx */
        CMGT(0b00110 << 11),
        CMGE(0b00111 << 11),
        SSHL(0b01000 << 11),
        SMAX(0b01100 << 11),
        SMIN(0b01101 << 11),
        ADD(0b10000 << 11),
        MUL(0b10011 << 11),
        MLA(0b10010 << 11),
        /* size 0x */
        FMLA(0b11001 << 11),
        FADD(0b11010 << 11),
        FCMEQ(0b11100 << 11),
        FMAX(0b11110 << 11),
        /* size 00 */
        AND(0b00011 << 11),
        /* size 1x */
        FMLS(0b11001 << 11),
        FSUB(0b11010 << 11),
        FMIN(0b11110 << 11),
        /* size 10 */
        ORR(0b00011 << 11),
        /* UBit 1, size xx */
        CMHI(UBit | 0b00110 << 11),
        CMHS(UBit | 0b00111 << 11),
        USHL(UBit | 0b01000 << 11),
        SUB(UBit | 0b10000 << 11),
        CMEQ(UBit | 0b10001 << 11),
        MLS(UBit | 0b10010 << 11),
        /* UBit 1, size 0x */
        FMUL(UBit | 0b11011 << 11),
        FCMGE(0b11100 << 11),
        FDIV(UBit | 0b11111 << 11),
        /* UBit 1, size 00 */
        EOR(UBit | 0b00011 << 11),
        /* UBit 1, size 1x */
        FCMGT(UBit | 0b11100 << 11),

        /* Advanced SIMD shift by immediate (C4-371). */
        SSHR(0b00100 << 11),
        SHL(0b01010 << 11),
        SSHLL(0b10100 << 11),
        USHR(UBit | 0b00100 << 11),
        USHLL(UBit | 0b10100 << 11);

        public final int encoding;

        ASIMDInstruction(int encoding) {
            this.encoding = encoding;
        }

    }

    private final AArch64Assembler asm;

    protected AArch64ASIMDAssembler(AArch64Assembler asm) {
        this.asm = asm;
    }

    protected void emitInt(int x) {
        asm.emitInt(x);
    }

    /**
     * Returns whether the operation is utilizing multiple vector lanes. The only scenario when
     * this isn't true is when performing an operation using a 64-bit register and an element of
     * size 64 bits.
     */
    private static boolean usesMultipleLanes(ASIMDSize size, ElementSize eSize) {
        return !(size == ASIMDSize.HalfReg && eSize == ElementSize.DoubleWord);
    }

    /* Helper values/methods for encoding instructions */

    private static final int ASIMDSizeOffset = 22;

    private static final int elemSize00 = 0b00 << ASIMDSizeOffset;
    private static final int elemSize01 = 0b01 << ASIMDSizeOffset;
    private static final int elemSize10 = 0b10 << ASIMDSizeOffset;
    private static final int elemSize11 = 0b11 << ASIMDSizeOffset;

    private static int elemSizeXX(ElementSize eSize) {
        return eSize.encoding << ASIMDSizeOffset;
    }

    private static int elemSize1X(ElementSize eSize) {
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;
        return (0b10 | (eSize == ElementSize.DoubleWord ? 1 : 0)) << ASIMDSizeOffset;
    }

    private static int elemSize0X(ElementSize eSize) {
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;
        return (eSize == ElementSize.DoubleWord ? 1 : 0) << ASIMDSizeOffset;
    }

    /**
     * Sets the Q-bit if requested.
     */
    private static int qBit(boolean isSet) {
        return (isSet ? 1 : 0) << 30;
    }

    /**
     * Sets the Q-bit if using all 128-bits.
     */
    private static int qBit(ASIMDSize size) {
        return (size == ASIMDSize.FullReg ? 1 : 0) << 30;
    }

    private void scalarThreeSameEncoding(ASIMDInstruction instr, int eSizeEncoding, Register dst, Register src1, Register src2) {
        int baseEncoding = 0b01_0_11110_00_1_00000_00000_1_00000_00000;
        emitInt(instr.encoding | baseEncoding | eSizeEncoding | rd(dst) | rs1(src1) | rs2(src2));
    }

    private void copyEncoding(ASIMDInstruction instr, boolean setQBit, ElementSize eSize, Register dst, Register src, int index) {
        assert index >= 0 && index < ASIMDSize.FullReg.bytes() / eSize.bytes();
        int baseEncoding = 0b0_0_0_01110000_00000_0_0000_1_00000_00000;
        int imm5Encoding = (index * 2 * eSize.bytes() | eSize.bytes()) << 16;
        emitInt(instr.encoding | baseEncoding | qBit(setQBit) | imm5Encoding | rd(dst) | rs1(src));
    }

    private void twoRegMiscEncoding(ASIMDInstruction instr, ASIMDSize size, int eSizeEncoding, Register dst, Register src) {
        twoRegMiscEncoding(instr, size == ASIMDSize.FullReg, eSizeEncoding, dst, src);
    }

    private void twoRegMiscEncoding(ASIMDInstruction instr, boolean setQBit, int eSizeEncoding, Register dst, Register src) {
        int baseEncoding = 0b0_0_0_01110_00_10000_00000_10_00000_00000;
        emitInt(instr.encoding | baseEncoding | qBit(setQBit) | eSizeEncoding | rd(dst) | rs1(src));
    }

    private void acrossLanesEncoding(ASIMDInstruction instr, ASIMDSize size, int eSizeEncoding, Register dst, Register src) {
        int baseEncoding = 0b0_0_0_01110_00_11000_00000_10_00000_00000;
        emitInt(instr.encoding | baseEncoding | qBit(size) | eSizeEncoding | rd(dst) | rs1(src));
    }

    public void threeDifferentEncoding(ASIMDInstruction instr, boolean setQBit, int eSizeEncoding, Register dst, Register src1, Register src2) {
        int baseEncoding = 0b0_0_0_01110_00_1_00000_0000_00_00000_00000;
        emitInt(instr.encoding | baseEncoding | qBit(setQBit) | eSizeEncoding | rd(dst) | rs1(src1) | rs2(src2));
    }

    private void threeSameEncoding(ASIMDInstruction instr, ASIMDSize size, int eSizeEncoding, Register dst, Register src1, Register src2) {
        int baseEncoding = 0b0_0_0_01110_00_1_00000_00000_1_00000_00000;
        emitInt(instr.encoding | baseEncoding | qBit(size) | eSizeEncoding | rd(dst) | rs1(src1) | rs2(src2));

    }

    public void modifiedImmEncoding(ImmediateOp op, ASIMDSize size, Register dst, long imm) {
        int baseEncoding = 0b0_0_0_0111100000_000_0000_0_1_00000_00000;
        int immEncoding = ASIMDImmediateTable.getEncoding(imm, op);
        emitInt(baseEncoding | qBit(size) | immEncoding | rd(dst));
    }

    public void shiftByImmEncoding(ASIMDInstruction instr, ASIMDSize size, int imm7, Register dst, Register src) {
        shiftByImmEncoding(instr, size == ASIMDSize.FullReg, imm7, dst, src);
    }

    public void shiftByImmEncoding(ASIMDInstruction instr, boolean setQBit, int imm7, Register dst, Register src) {
        assert (imm7 & 0b1111_111) == imm7;
        assert (imm7 & 0b1111_111) != 0;
        int baseEncoding = 0b0_0_0_011110_0000_000_00000_1_00000_00000;
        emitInt(instr.encoding | baseEncoding | qBit(setQBit) | imm7 << 16 | rd(dst) | rs1(src));
    }

    /**
     * C7.2.1 Integer absolute value.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = int_abs(src[i])</code>
     *
     * @param size  register size.
     * @param eSize element size.
     * @param dst   SIMD register.
     * @param src   SIMD register.
     */
    public void absVector(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(size, eSize);

        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;

        twoRegMiscEncoding(ASIMDInstruction.ABS, size, elemSizeXX(eSize), dst, src);
    }

    /**
     * C7.2.2 Integer add scalar.<br>
     *
     * <code>dst[0] = int_add(src1[0], src2[0])</code>
     * <p>
     * Note that only 64-bit (DoubleWord) operations are available.
     *
     * @param eSize element size. Must be of type ElementSize.DoubleWord
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void addScalar(ElementSize eSize, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        assert eSize == ElementSize.DoubleWord; // only size supported

        scalarThreeSameEncoding(ASIMDInstruction.ADD, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.2 Integer add vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = int_add(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size.
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void addVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.ADD, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.6 Add across vector.<br>
     *
     * <code>dst = src[0] + ....+ src[n].</code>
     *
     * @param size        register size.
     * @param elementSize width of each addition operand.
     * @param dst         SIMD register. Should not be null.
     * @param src         SIMD register. Should not be null.
     */
    public void addv(ASIMDSize size, ElementSize elementSize, Register dst, Register src) {
        assert !(size == ASIMDSize.HalfReg && elementSize == ElementSize.Word) : "Invalid size and lane combination for addv";
        assert elementSize != ElementSize.DoubleWord : "Invalid lane width for addv";

        acrossLanesEncoding(ASIMDInstruction.ADDV, size, elemSizeXX(elementSize), dst, src);
    }

    /**
     * C7.2.11 Bitwise and vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = src1[i] & src2[i]</code>
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param src1 SIMD register.
     * @param src2 SIMD register.
     */
    public void andVector(ASIMDSize size, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.AND, size, elemSize00, dst, src1, src2);
    }

    /**
     * C7.2.20 Bitwise bit clear.<br>
     * This instruction performs a bitwise and between the SIMD register and the complement of
     * the provided immediate value.
     *
     * <code>dst = dst & ~(imm{1,2})</code>
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param imm  long value to move. If size is 128, then this value is copied twice
     */
    public void bicVector(ASIMDSize size, Register dst, long imm) {
        modifiedImmEncoding(ImmediateOp.BIC, size, dst, imm);
    }

    /**
     * C7.2.27 Compare bitwise equal.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = src1[i] == src2[i] ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. ElementSize.DoubleWord is only applicable when size is 128
     *              (i.e. the operation is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void cmeqVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);

        threeSameEncoding(ASIMDInstruction.CMEQ, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.28 Compare bitwise equal to zero.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = src[i] == 0 ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. ElementSize.DoubleWord is only applicable when size is 128
     *              (i.e. the operation is performed on more than one element).
     * @param dst   SIMD register.
     * @param src   SIMD register.
     */
    public void cmeqZeroVector(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(size, eSize);

        twoRegMiscEncoding(ASIMDInstruction.CMEQ_ZERO, size, elemSizeXX(eSize), dst, src);
    }

    /**
     * C7.2.29 Compare signed greater than or equal.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = src1[i] >= src2[i] ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. ElementSize.DoubleWord is only applicable when size is 128
     *              (i.e. the operation is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void cmgeVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);

        threeSameEncoding(ASIMDInstruction.CMGE, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.31 Compare signed greater than.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = src1[i] > src2[i] ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. ElementSize.DoubleWord is only applicable when size is 128
     *              (i.e. the operation is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void cmgtVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);

        threeSameEncoding(ASIMDInstruction.CMGT, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.32 Compare signed greater than zero.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = src[i] > 0 ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. ElementSize.DoubleWord is only applicable when size is 128
     *              (i.e. the operation is performed on more than one element).
     * @param dst   SIMD register.
     * @param src   SIMD register.
     */
    public void cmgtZeroVector(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(size, eSize);

        twoRegMiscEncoding(ASIMDInstruction.CMGT_ZERO, size, elemSizeXX(eSize), dst, src);
    }

    /**
     * C7.2.33 Compare unsigned higher.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = unsigned(src1[i]) > unsigned(src2[i]) ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. ElementSize.DoubleWord is only applicable when size is 128
     *              (i.e. the operation is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void cmhiVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);

        threeSameEncoding(ASIMDInstruction.CMHI, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.34 Compare unsigned higher or same.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = unsigned(src1[i]) >= unsigned(src2[i]) ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. ElementSize.DoubleWord is only applicable when size is 128
     *              (i.e. the operation is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void cmhsVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);

        threeSameEncoding(ASIMDInstruction.CMHS, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.36 Compare signed less than zero.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = src[i] < 0 ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. ElementSize.DoubleWord is only applicable when size is 128
     *              (i.e. the operation is performed on more than one element).
     * @param dst   SIMD register.
     * @param src   SIMD register.
     */
    public void cmltZeroVector(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(size, eSize);

        twoRegMiscEncoding(ASIMDInstruction.CMLT_ZERO, size, elemSizeXX(eSize), dst, src);
    }

    /**
     * C7.2.38 Population Count per byte.<br>
     *
     * <code>dst[0...n] = countBitCountOfEachByte(src[0...n]), n = size/8.</code>
     *
     * @param size register size.
     * @param dst  SIMD register. Should not be null.
     * @param src  SIMD register. Should not be null.
     */
    public void cnt(ASIMDSize size, Register dst, Register src) {
        twoRegMiscEncoding(ASIMDInstruction.CNT, size, elemSize00, dst, src);
    }

    /**
     * C7.2.39 Duplicate vector element to scalar.<br>
     * Note that, regardless of the source vector element's index, the value is always copied
     * into the beginning of the destination register (offset 0).
     *
     * <code>dst[0] = src[index]</code>
     *
     * @param eSize size of value to duplicate.
     * @param dst   SIMD register
     * @param src   SIMD register
     * @param index offset of value to duplicate
     */
    public void dupScalar(ElementSize eSize, Register dst, Register src, int index) {
        assert src.getRegisterCategory() == SIMD;
        assert dst.getRegisterCategory() == SIMD;
        assert index >= 0 && index < ASIMDSize.FullReg.bytes() / eSize.bytes();

        /*
         * Technically, this is instruction's encoding format is "advanced simd scalar copy"
         * (C4-343).
         */
        int baseEncoding = 0b01_0_11110000_00000_0_0000_1_00000_00000;
        int imm5Encoding = ((index * 2 * eSize.bytes()) | eSize.bytes()) << 16;
        emitInt(ASIMDInstruction.DUPELEM.encoding | baseEncoding | imm5Encoding | rd(dst) | rn(src));
    }

    /**
     * C7.2.39 Duplicate vector element to vector.<br>
     *
     * <code>dst[0..n-1] = src[index]{n}</code>
     *
     * @param dstSize total size of all duplicates.
     * @param eSize   size of value to duplicate.
     * @param dst     SIMD register.
     * @param src     SIMD register.
     * @param index   offset of value to duplicate
     */
    public void dupVector(ASIMDSize dstSize, ElementSize eSize, Register dst, Register src, int index) {
        assert src.getRegisterCategory() == SIMD;
        assert dst.getRegisterCategory() == SIMD;

        copyEncoding(ASIMDInstruction.DUPELEM, dstSize == ASIMDSize.FullReg, eSize, dst, src, index);
    }

    /**
     * C7.2.40 Duplicate general-purpose register to vector.<br>
     *
     * <code>dst(simd) = src(gp){n}</code>
     *
     * @param dstSize total size of all duplicates.
     * @param eSize   size of value to duplicate.
     * @param dst     SIMD register.
     * @param src     general-purpose register.
     */
    public void dupGeneral(ASIMDSize dstSize, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(dstSize, eSize);
        assert src.getRegisterCategory() == CPU;
        assert dst.getRegisterCategory() == SIMD;

        copyEncoding(ASIMDInstruction.DUPGEN, dstSize == ASIMDSize.FullReg, eSize, dst, src, 0);
    }

    /**
     * C7.2.41 Bitwise exclusive or vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = src1[i] ^ src2[i]</code>
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param src1 SIMD register.
     * @param src2 SIMD register.
     */
    public void eorVector(ASIMDSize size, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.EOR, size, elemSize00, dst, src1, src2);
    }

    /**
     * C7.2.43 Extract from pair of vectors.<br>
     * <p>
     * From the manual: "This instruction extracts the lowest vector elements from the second
     * source SIMD&FP register and the highest vector elements from the first source SIMD&FP
     * register, concatenates the results into a vector, and writes the vector to the
     * destination SIMD&FP register vector. The index value specifies the lowest vector element
     * to extract from the first source register, and consecutive elements are extracted from
     * the first, then second, source registers until the destination vector is filled." For
     * this operation, vector elements are always byte sized.
     *
     * @param size       operation size.
     * @param dst        SIMD register.
     * @param src1       first source SIMD register.
     * @param src2       second source SIMD register.
     * @param src1LowIdx The lowest index of the first source registers to extract
     */
    public void ext(ASIMDSize size, Register dst, Register src1, Register src2, int src1LowIdx) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;
        /* Must include at least one byte from src1 */
        assert src1LowIdx >= 0 && src1LowIdx < size.bytes();
        /*
         * Technically, this instruction's encoding format is "advanced simd extract" (C4-356)
         */
        int baseEncoding = 0b0_0_101110_00_0_00000_0_0000_0_00000_00000;
        emitInt(ASIMDInstruction.EXT.encoding | baseEncoding | qBit(size) | src1LowIdx << 11 | rd(dst) | rs1(src1) | rs2(src2));
    }

    /**
     * C7.2.45 Floating-point absolute value.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = fp_abs(src[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src   SIMD register.
     */
    public void fabsVector(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(size, eSize);
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;

        twoRegMiscEncoding(ASIMDInstruction.FABS, size, elemSize1X(eSize), dst, src);
    }

    /**
     * C7.2.49 floating point add vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = fp_add(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void faddVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;
        assert usesMultipleLanes(size, eSize);

        threeSameEncoding(ASIMDInstruction.FADD, size, elemSize0X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.56 Floating-point compare equal.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = src1[i] == src2[i] ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord.
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void fcmeqVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        threeSameEncoding(ASIMDInstruction.FCMEQ, size, elemSize0X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.58 Floating-point compare greater than or equal.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = src1[i] >= src2[i] ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord.
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void fcmgeVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        threeSameEncoding(ASIMDInstruction.FCMGE, size, elemSize0X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.60 Floating-point compare greater than.<br>
     * <p>
     * For elements which the comparison is true, all bits of the corresponding dst lane are set
     * to 1. Otherwise, if the comparison is false, then the corresponding dst lane is cleared.
     *
     * <code>for i in 0..n-1 do dst[i] = src1[i] > src2[i] ? -1 : 0</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord.
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void fcmgtVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        threeSameEncoding(ASIMDInstruction.FCMGT, size, elemSize1X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.74 Floating-point convert to higher precision long.<br>
     *
     * @param srcESize source element size. Must be ElementSize.HalfWord or ElementSize.Word.
     *                 The destination element size will be double this width.
     * @param dst      SIMD register.
     * @param src      SIMD register.
     */
    public void fcvtlVector(ElementSize srcESize, Register dst, Register src) {
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;
        assert srcESize == ElementSize.HalfWord || srcESize == ElementSize.Word;

        twoRegMiscEncoding(ASIMDInstruction.FCVTL, false, elemSize0X(srcESize), dst, src);
    }

    /**
     * C7.2.79 Floating-point convert to lower precision narrow.<br>
     *
     * @param srcESize source element size. Must be ElementSize.Word or ElementSize.DoubleWord.
     *                 The destination element size will be half this width.
     * @param dst      SIMD register.
     * @param src      SIMD register.
     */
    public void fcvtnVector(ElementSize srcESize, Register dst, Register src) {
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;
        assert srcESize == ElementSize.Word || srcESize == ElementSize.DoubleWord;

        twoRegMiscEncoding(ASIMDInstruction.FCVTN, false, elemSize0X(srcESize), dst, src);
    }

    /**
     * C7.2.90 Floating-point convert to to signed integer, rounding toward zero.<br>
     *
     * @param size  register size.
     * @param eSize source element size. Must be ElementSize.Word or ElementSize.DoubleWord.
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src   SIMD register.
     */
    public void fcvtzsVector(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(size, eSize);
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        twoRegMiscEncoding(ASIMDInstruction.FCVTZS, size, elemSize1X(eSize), dst, src);
    }

    /**
     * C7.2.97 floating point divide vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = fp_div(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void fdivVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;
        assert usesMultipleLanes(size, eSize);

        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.FDIV, size, elemSize0X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.101 floating-point maximum.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = fp_max(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */

    public void fmaxVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.FMAX, size, elemSize0X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.111 floating-point minimum.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = fp_min(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void fminVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.FMIN, size, elemSize1X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.122 Floating-point fused multiply-add to accumulator.<br>
     *
     * <code>for i in 0..n-1 do dst[i] += fp_multiply(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void fmlaVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        threeSameEncoding(ASIMDInstruction.FMLA, size, elemSize0X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.126 Floating-point fused multiply-subtract from accumulator.<br>
     *
     * <code>for i in 0..n-1 do dst[i] -= fp_multiply(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void fmlsVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        threeSameEncoding(ASIMDInstruction.FMLS, size, elemSize1X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.132 Floating-point move immediate.<br>
     *
     * <code>dst = imm{2,4}</code>
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param imm  floating value to move. When the size == 64, this value is copied twice. When
     *             size == 128, this value is copied four times.
     */
    public void fmovVector(ASIMDSize size, Register dst, float imm) {
        long immTwice = ((long) Float.floatToIntBits(imm)) & NumUtil.getNbitNumberLong(32);
        immTwice = immTwice | immTwice << 32;
        modifiedImmEncoding(ImmediateOp.FMOVSP, size, dst, immTwice);
    }

    /**
     * C7.2.132 Floating-point move immediate.<br>
     *
     * <code>dst = imm{2}</code>
     *
     * @param size register size. Must be 128.
     * @param dst  SIMD register.
     * @param imm  floating value to move. This value is copied twice.
     */
    public void fmovVector(ASIMDSize size, Register dst, double imm) {
        assert size == ASIMDSize.FullReg;

        modifiedImmEncoding(ImmediateOp.FMOVDP, size, dst, Double.doubleToLongBits(imm));
    }

    /**
     * C7.2.135 floating point multiply vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = fp_mul(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void fmulVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;
        assert usesMultipleLanes(size, eSize);

        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.FMUL, size, elemSize0X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.139 Floating-point negate.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = -src[i]</code>
     *
     * @param size  register size.
     * @param eSize source element size. Must be ElementSize.Word or ElementSize.DoubleWord.
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src   SIMD register.
     */
    public void fnegVector(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(size, eSize);
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        twoRegMiscEncoding(ASIMDInstruction.FNEG, size, elemSize1X(eSize), dst, src);
    }

    /**
     * C7.2.171 Floating-point square root.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = fp_sqrt(src[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src   SIMD register.
     */
    public void fsqrtVector(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(size, eSize);
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;

        twoRegMiscEncoding(ASIMDInstruction.FSQRT, size, elemSize1X(eSize), dst, src);
    }

    /**
     * C7.2.173 floating point subtract vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = fp_sub(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void fsubVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;
        assert usesMultipleLanes(size, eSize);

        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.FSUB, size, elemSize1X(eSize), dst, src1, src2);
    }

    /**
     * C7.2.196 Multiply-add to accumulator.<br>
     *
     * <code>for i in 0..n-1 do dst[i] += int_multiply(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size.
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void mlaVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);

        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.MLA, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.198 Multiply-subtract from accumulator.<br>
     *
     * <code>for i in 0..n-1 do dst[i] -= int_multiply(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size.
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void mlsVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);

        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.MLS, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.204 Move immediate.<br>
     *
     * <code>dst = imm{1,2}</code>
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param imm  long value to move. If size is 128, then this value is copied twice
     */
    public void moviVector(ASIMDSize size, Register dst, long imm) {
        modifiedImmEncoding(ImmediateOp.MOVI, size, dst, imm);
    }

    /**
     * C7.2.206 Integer multiply vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = int_mul(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size.
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void mulVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.MUL, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.208 Move inverted immediate.<br>
     *
     * <code>dst = ~(imm{1,2})</code>
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param imm  long value to move. If size is 128, then this value is copied twice
     */
    public void mvniVector(ASIMDSize size, Register dst, long imm) {
        modifiedImmEncoding(ImmediateOp.MVNI, size, dst, imm);
    }

    /**
     * C7.2.209 Negate.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = -src[i]</code>
     *
     * @param size  register size.
     * @param eSize source element size. ElementSize.DoubleWord is only applicable when size is
     *              128 (i.e. the operation is performed on more than one element).
     * @param dst   SIMD register.
     * @param src   SIMD register.
     */
    public void negVector(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(size, eSize);
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;

        twoRegMiscEncoding(ASIMDInstruction.NEG, size, elemSizeXX(eSize), dst, src);
    }

    /**
     * C7.2.210 Bitwise not vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = ~src[i]</code>
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param src  SIMD register.
     */
    public void notVector(ASIMDSize size, Register dst, Register src) {
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;

        twoRegMiscEncoding(ASIMDInstruction.NOT, size, elemSize00, dst, src);
    }

    /**
     * C7.2.212 Bitwise inclusive or.<br>
     *
     * <code>dst = dst | imm{1,2}</code>
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param imm  long value to move. If size is 128, then this value is copied twice
     */
    public void orrVector(ASIMDSize size, Register dst, long imm) {
        modifiedImmEncoding(ImmediateOp.ORR, size, dst, imm);
    }

    /**
     * C7.2.213 Bitwise inclusive or vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = src1[i] | src2[i]</code>
     *
     * @param size register size.
     * @param dst  SIMD register.
     * @param src1 SIMD register.
     * @param src2 SIMD register.
     */
    public void orrVector(ASIMDSize size, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.ORR, size, elemSize10, dst, src1, src2);
    }

    /**
     * C7.2.231 Signed add across long vector.<br>
     *
     * <code>dst = src[0] + ....+ src[n].</code><br>
     * <p>
     * Dst is twice the width of the vector elements, so overflow is not possible.
     *
     * @param size        register size.
     * @param elementSize Unexpanded width of each addition operand.
     * @param dst         SIMD register. Should not be null.
     * @param src         SIMD register. Should not be null.
     */
    public void saddlv(ASIMDSize size, ElementSize elementSize, Register dst, Register src) {
        assert !(size == ASIMDSize.HalfReg && elementSize == ElementSize.Word) : "Invalid size and lane combination for saddlv";
        assert elementSize != ElementSize.DoubleWord : "Invalid lane width for saddlv";

        acrossLanesEncoding(ASIMDInstruction.SADDLV, size, elemSizeXX(elementSize), dst, src);
    }

    /**
     * C7.2.234 Signed integer convert to floating-point.<br>
     *
     * @param size  register size.
     * @param eSize source element size. Must be ElementSize.Word or ElementSize.DoubleWord.
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param src   SIMD register.
     */
    public void scvtfVector(ASIMDSize size, ElementSize eSize, Register dst, Register src) {
        assert usesMultipleLanes(size, eSize);
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;
        assert eSize == ElementSize.Word || eSize == ElementSize.DoubleWord;

        twoRegMiscEncoding(ASIMDInstruction.SCVTF, size, elemSize0X(eSize), dst, src);
    }

    /**
     * C7.2.254 shift left (immediate).<br>
     *
     * <code>for i in 0..n-1 do dst[i] = src[i] << imm</code>
     *
     * @param size     register size.
     * @param eSize    element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *                 ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *                 is performed on more than one element).
     * @param dst      SIMD register.
     * @param src      SIMD register.
     * @param shiftAmt shift amount.
     */
    public void shlVector(ASIMDSize size, ElementSize eSize, Register dst, Register src, int shiftAmt) {
        assert usesMultipleLanes(size, eSize);
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;

        /* Accepted shift range */
        assert shiftAmt >= 0 && shiftAmt < eSize.nbits;

        /* shift = imm7 - eSize.nbits */
        int imm7 = eSize.nbits + shiftAmt;

        shiftByImmEncoding(ASIMDInstruction.SHL, size, imm7, dst, src);
    }

    /**
     * C7.2.268 Signed maximum.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = int_max(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size.
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void smaxVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);

        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.SMAX, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.271 Signed minimum.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = int_min(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size.
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void sminVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);

        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.SMIN, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.275 Signed Multiply-Add Long.<br>
     *
     * <code>for i in 0..n-1 do dst[i] += int_multiply(src1[i], src2[i])</code>
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *                 element size will be double this width.
     * @param dst      SIMD register.
     * @param src1     SIMD register.
     * @param src2     SIMD register.
     */
    public void smlalVector(ElementSize srcESize, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;
        assert srcESize != ElementSize.DoubleWord;

        threeDifferentEncoding(ASIMDInstruction.SMLAL, false, elemSizeXX(srcESize), dst, src1, src2);
    }

    /**
     * C7.2.277 Signed Multiply-Subtract Long.<br>
     *
     * <code>for i in 0..n-1 do dst[i] -= int_multiply(src1[i], src2[i])</code>
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *                 element size will be double this width.
     * @param dst      SIMD register.
     * @param src1     SIMD register.
     * @param src2     SIMD register.
     */
    public void smlslVector(ElementSize srcESize, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;
        assert srcESize != ElementSize.DoubleWord;

        threeDifferentEncoding(ASIMDInstruction.SMLSL, false, elemSizeXX(srcESize), dst, src1, src2);
    }

    /**
     * C7.2.279 Signed move vector element to general-purpose register.<br>
     *
     * <code>dst (gp) = sign-extend(src[index]) (simd).</code>
     * <p>
     * Note that the target register size (dst) must be greater than the source element size.
     *
     * @param dstESize width of sign-extended.
     * @param srcESize width of element to move.
     * @param dst      general-purpose register.
     * @param src      SIMD register.
     * @param index    offset of value to move.
     */
    public void smovGeneral(ElementSize dstESize, ElementSize srcESize, Register dst, Register src, int index) {
        assert srcESize != ElementSize.DoubleWord;
        assert dstESize == ElementSize.Word || dstESize == ElementSize.DoubleWord;
        assert srcESize.nbits < dstESize.nbits : "the target size must be larger than the source size";

        assert dst.getRegisterCategory() == CPU;
        assert src.getRegisterCategory() == SIMD;

        copyEncoding(ASIMDInstruction.SMOV, dstESize == ElementSize.DoubleWord, srcESize, dst, src, index);
    }

    /**
     * C7.2.315 signed shift left (register).<br>
     *
     * <code>for i in 0..n-1 do<br>
     * if(byte(src2[i] > 0<br>
     * dst[i] = (src1[i] << byte(src2[i]<br>
     * else<br>
     * dst[i] = (src1[i] >> byte(src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     */
    public void sshlVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.SSHL, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.316 Signed shift left long.<br>
     * <p>
     * From the manual: "This instruction reads each vector element from the source SIMD&FP
     * register, left shifts each vector element by the specified shift amount ... The
     * destination vector elements are twice as long as the source vector elements. All the
     * values in this instruction are signed integer values."
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *                 element size will be double this width.
     * @param dst      SIMD register.
     * @param src      SIMD register.
     * @param shiftAmt shift left amount.
     */
    public void sshllVector(ElementSize srcESize, Register dst, Register src, int shiftAmt) {
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;
        assert srcESize != ElementSize.DoubleWord;

        /* Accepted shift range */
        assert shiftAmt >= 0 && shiftAmt < srcESize.nbits;

        /* shift = imm7 - srcESize.nbits */
        int imm7 = srcESize.nbits + shiftAmt;

        shiftByImmEncoding(ASIMDInstruction.SSHLL, false, imm7, dst, src);
    }

    /**
     * C7.2.317 signed shift right (immediate).<br>
     *
     * <code>for i in 0..n-1 do dst[i] = src[i] >> imm</code>
     *
     * @param size     register size.
     * @param eSize    element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *                 ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *                 is performed on more than one element).
     * @param dst      SIMD register.
     * @param src      SIMD register.
     * @param shiftAmt shift right amount.
     */
    public void sshrVector(ASIMDSize size, ElementSize eSize, Register dst, Register src, int shiftAmt) {
        assert usesMultipleLanes(size, eSize);
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;

        /* Accepted shift range */
        assert shiftAmt > 0 && shiftAmt <= eSize.nbits;

        /* shift = eSize.nbits * 2 - imm7 */
        int imm7 = eSize.nbits * 2 - shiftAmt;

        shiftByImmEncoding(ASIMDInstruction.SSHR, size, imm7, dst, src);
    }

    /**
     * C7.2.334 Integer subtract scalar.<br>
     *
     * <code>dst[0] = int_sub(src1[0], src2[0])</code>
     * <p>
     * Note that only 64-bit (DoubleWord) operations are available.
     *
     * @param eSize element size. Must be of type ElementSize.DoubleWord
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void subScalar(ElementSize eSize, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        assert eSize == ElementSize.DoubleWord; // only size supported

        scalarThreeSameEncoding(ASIMDInstruction.SUB, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.334 Integer subtract vector.<br>
     *
     * <code>for i in 0..n-1 do dst[i] = int_sub(src1[i], src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size.
     * @param dst   SIMD register.
     * @param src1  SIMD register.
     * @param src2  SIMD register.
     */
    public void subVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.SUB, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.350 Unsigned add across long vector.<br>
     *
     * <code>dst = src[0] + ....+ src[n].</code><br>
     * <p>
     * Dst is twice the width of the vector elements, so overflow is not possible.
     *
     * @param size        register size.
     * @param elementSize Unexpanded width of each addition operand.
     * @param dst         SIMD register. Should not be null.
     * @param src         SIMD register. Should not be null.
     */
    public void uaddlv(ASIMDSize size, ElementSize elementSize, Register dst, Register src) {
        assert !(size == ASIMDSize.HalfReg && elementSize == ElementSize.Word) : "Invalid size and lane combination for uaddlv";
        assert elementSize != ElementSize.DoubleWord : "Invalid lane width for uaddlv";

        acrossLanesEncoding(ASIMDInstruction.UADDLV, size, elemSizeXX(elementSize), dst, src);
    }

    /**
     * C7.2.367 Unsigned Multiply-Add Long.<br>
     *
     * <code>for i in 0..n-1 do dst[i] += uint_multiply(src1[i], src2[i])</code>
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *                 element size will be double this width.
     * @param dst      SIMD register.
     * @param src1     SIMD register.
     * @param src2     SIMD register.
     */
    public void umlalVector(ElementSize srcESize, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;
        assert srcESize != ElementSize.DoubleWord;

        threeDifferentEncoding(ASIMDInstruction.UMLAL, false, elemSizeXX(srcESize), dst, src1, src2);
    }

    /**
     * C7.2.369 Unsigned Multiply-Subtract Long.<br>
     *
     * <code>for i in 0..n-1 do dst[i] -= uint_multiply(src1[i], src2[i])</code>
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *                 element size will be double this width.
     * @param dst      SIMD register.
     * @param src1     SIMD register.
     * @param src2     SIMD register.
     */
    public void umlslVector(ElementSize srcESize, Register dst, Register src1, Register src2) {
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;
        assert srcESize != ElementSize.DoubleWord;

        threeDifferentEncoding(ASIMDInstruction.UMLSL, false, elemSizeXX(srcESize), dst, src1, src2);
    }

    /**
     * C7.2.371 Unsigned move vector element to general-purpose register.<br>
     *
     * <code>dst (gp) = src[index] (simd).</code>
     *
     * @param eSize width of element to move.
     * @param dst   general-purpose register.
     * @param src   SIMD register.
     * @param index offset of value to move.
     */
    public void umovGeneral(ElementSize eSize, Register dst, Register src, int index) {
        assert dst.getRegisterCategory() == CPU;
        assert src.getRegisterCategory() == SIMD;

        copyEncoding(ASIMDInstruction.UMOV, eSize == ElementSize.DoubleWord, eSize, dst, src, index);
    }

    /**
     * C7.2.390 unsigned shift left (register).<br>
     *
     * <code>for i in 0..n-1 do<br>
     * if(byte(src2[i] > 0)<br>
     * dst[i] = (src1[i] << byte(src2[i])<br>
     * else<br>
     * dst[i] = (src1[i] >>> byte(src2[i])</code>
     *
     * @param size  register size.
     * @param eSize element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *              ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *              is performed on more than one element).
     * @param dst   SIMD register.
     * @param dst   SIMD register.
     */
    public void ushlVector(ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
        assert usesMultipleLanes(size, eSize);
        assert dst.getRegisterCategory() == SIMD;
        assert src1.getRegisterCategory() == SIMD;
        assert src2.getRegisterCategory() == SIMD;

        threeSameEncoding(ASIMDInstruction.USHL, size, elemSizeXX(eSize), dst, src1, src2);
    }

    /**
     * C7.2.391 Unsigned shift left long.<br>
     * <p>
     * From the manual: " This instruction reads each vector element in the lower half of the
     * source SIMD&FP register, shifts the unsigned integer value left by the specified number
     * of bits ... The destination vector elements are twice as long as the source vector
     * elements."
     *
     * @param srcESize source element size. Cannot be ElementSize.DoubleWord. The destination
     *                 element size will be double this width.
     * @param dst      SIMD register.
     * @param src      SIMD register.
     * @param shiftAmt shift left amount.
     */
    public void ushllVector(ElementSize srcESize, Register dst, Register src, int shiftAmt) {
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;
        assert srcESize != ElementSize.DoubleWord;

        /* Accepted shift range */
        assert shiftAmt >= 0 && shiftAmt < srcESize.nbits;

        /* shift = imm7 - srcESize.nbits */
        int imm7 = srcESize.nbits + shiftAmt;

        shiftByImmEncoding(ASIMDInstruction.USHLL, false, imm7, dst, src);
    }

    /**
     * C7.2.392 unsigned shift right (immediate).<br>
     *
     * <code>for i in 0..n-1 do dst[i] = src[i] >>> imm</code>
     *
     * @param size     register size.
     * @param eSize    element size. Must be ElementSize.Word or ElementSize.DoubleWord. Note
     *                 ElementSize.DoubleWord is only applicable when size is 128 (i.e. the operation
     *                 is performed on more than one element).
     * @param dst      SIMD register.
     * @param src      SIMD register.
     * @param shiftAmt shift right amount.
     */
    public void ushrVector(ASIMDSize size, ElementSize eSize, Register dst, Register src, int shiftAmt) {
        assert usesMultipleLanes(size, eSize);
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;

        /* Accepted shift range */
        assert shiftAmt > 0 && shiftAmt <= eSize.nbits;

        /* shift = eSize.nbits * 2 - imm7 */
        int imm7 = eSize.nbits * 2 - shiftAmt;

        shiftByImmEncoding(ASIMDInstruction.USHR, size, imm7, dst, src);
    }

    /**
     * C7.2.402 Extract narrow.<br>
     * <p>
     * From the manual: "This instruction reads each vector element from the source SIMD&FP
     * register, narrows each value to half the original width, and writes the register..."
     *
     * @param srcESize source element size. Cannot be ElementSize.Byte. The destination element
     *                 size will be half this width.
     * @param dst      SIMD register.
     * @param src      SIMD register.
     */
    public void xtnVector(ElementSize srcESize, Register dst, Register src) {
        assert dst.getRegisterCategory() == SIMD;
        assert src.getRegisterCategory() == SIMD;
        assert srcESize != ElementSize.Byte;

        twoRegMiscEncoding(ASIMDInstruction.XTN, false, elemSizeXX(srcESize), dst, src);
    }
}
