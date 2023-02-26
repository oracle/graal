/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;

import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;

/**
 * Represents an address in target machine memory, specified using one of the different addressing
 * modes of the AArch64 ISA.
 *
 * <pre>
 *
 *  - Base register only
 *  - Base register + immediate or register with shifted offset
 *  - Pre-indexed: base + immediate offset are written back to base register, value used in instruction is base + offset
 *  - Post-indexed: base + offset (immediate or register) are written back to base register, value used in instruction is base only
 *  - Literal: PC + 19-bit signed word aligned offset
 * </pre>
 *
 * Note not all addressing modes are supported for all instructions. For debugging purposes, the
 * address also stores the expected size of the memory access it will be associated with in
 * {@link #bitMemoryTransferSize}.
 */
public final class AArch64Address extends AbstractAddress {
    /** This means that {@link #bitMemoryTransferSize} is allowed to be any size. */
    public static final int ANY_SIZE = -1;

    /** Placeholder for addresses that get patched later. */
    public static final AArch64Address PLACEHOLDER = createPCLiteralAddress(ANY_SIZE);

    public enum AddressingMode {
        /**
         * base + uimm12 << log2(byte_memory_transfer_size).
         */
        IMMEDIATE_UNSIGNED_SCALED,
        /**
         * base + imm9.
         */
        IMMEDIATE_SIGNED_UNSCALED,
        /**
         * base.
         */
        BASE_REGISTER_ONLY,
        /**
         * base + offset [<< log2(byte_memory_transfer_size)].
         */
        REGISTER_OFFSET,
        /**
         * base + extend(offset) [<< log2(byte_memory_transfer_size)].
         */
        EXTENDED_REGISTER_OFFSET,
        /**
         * PC + imm21 (word aligned).
         */
        PC_LITERAL,
        /**
         * address = base. base is updated to base + imm9
         */
        IMMEDIATE_POST_INDEXED,
        /**
         * address = base + imm9. base is updated to base + imm9
         */
        IMMEDIATE_PRE_INDEXED,
        /**
         * base + imm7 << log2(byte_memory_transfer_size).
         */
        IMMEDIATE_PAIR_SIGNED_SCALED,
        /**
         * address = base. base is updated to base + imm7 << log2(byte_memory_transfer_size)
         */
        IMMEDIATE_PAIR_POST_INDEXED,
        /**
         * address = base + imm7 << log2(byte_memory_transfer_size). base is updated to base + imm7
         * << log2(byte_memory_transfer_size)
         */
        IMMEDIATE_PAIR_PRE_INDEXED,
        /**
         * address = base. base is updated to base + offset.
         */
        REGISTER_STRUCTURE_POST_INDEXED,
        /**
         * address = base. base is updated to base + constant.
         *
         * Constant value is determined by the specific instruction and the number of registers
         * used. See
         * {@link #determineStructureImmediateValue(ASIMDInstruction, ASIMDSize, ElementSize)} for
         * more details.
         */
        IMMEDIATE_STRUCTURE_POST_INDEXED,
    }

    private final int bitMemoryTransferSize;
    private final Register base;
    private final Register offset;
    private final int immediate;
    /**
     * Should register offset be scaled or not.
     */
    private final boolean registerOffsetScaled;
    private final AArch64Assembler.ExtendType extendType;
    private final AddressingMode addressingMode;

    /**
     * General address generation mechanism. Accepted values for all parameters depend on the
     * addressingMode. Null is never accepted for a register, if an addressMode doesn't use a
     * register the register has to be the zero-register. extendType has to be null for every
     * addressingMode except EXTENDED_REGISTER_OFFSET.
     */
    public static AArch64Address createAddress(int bitMemoryTransferSize, AddressingMode addressingMode, Register base, Register offset, int immediate, boolean registerOffsetScaled,
                    AArch64Assembler.ExtendType extendType) {
        return new AArch64Address(bitMemoryTransferSize, base, offset, immediate, registerOffsetScaled, extendType, addressingMode);
    }

    /**
     * Checks whether the memory size provided is available for the given immediate addressing mode.
     */
    private static boolean isValidSize(int bitMemoryTransferSize, AddressingMode mode) {
        switch (mode) {
            case IMMEDIATE_SIGNED_UNSCALED:
            case IMMEDIATE_POST_INDEXED:
            case IMMEDIATE_PRE_INDEXED:
            case IMMEDIATE_UNSIGNED_SCALED:
                return bitMemoryTransferSize == 8 || bitMemoryTransferSize == 16 || bitMemoryTransferSize == 32 || bitMemoryTransferSize == 64 || bitMemoryTransferSize == 128;
            case IMMEDIATE_PAIR_SIGNED_SCALED:
            case IMMEDIATE_PAIR_POST_INDEXED:
            case IMMEDIATE_PAIR_PRE_INDEXED:
                return bitMemoryTransferSize == 32 || bitMemoryTransferSize == 64 || bitMemoryTransferSize == 128;
        }
        throw GraalError.shouldNotReachHere();
    }

    /**
     * Checks if immediate address can fit within the provided immediate addressing mode.
     */
    private static boolean immediateFitsInInstruction(AddressingMode mode, int immediate) {
        switch (mode) {
            case IMMEDIATE_SIGNED_UNSCALED:
            case IMMEDIATE_POST_INDEXED:
            case IMMEDIATE_PRE_INDEXED:
                return NumUtil.isSignedNbit(9, immediate);
            case IMMEDIATE_UNSIGNED_SCALED:
                return NumUtil.isUnsignedNbit(12, immediate);
            case IMMEDIATE_PAIR_SIGNED_SCALED:
            case IMMEDIATE_PAIR_POST_INDEXED:
            case IMMEDIATE_PAIR_PRE_INDEXED:
                return NumUtil.isSignedNbit(7, immediate);
        }
        throw GraalError.shouldNotReachHere();
    }

    /**
     * Returns whether the immediate addressing mode being used scales the immediate operand.
     */
    public static boolean isImmediateScaled(AddressingMode mode) {
        switch (mode) {
            case IMMEDIATE_UNSIGNED_SCALED:
            case IMMEDIATE_PAIR_SIGNED_SCALED:
            case IMMEDIATE_PAIR_POST_INDEXED:
            case IMMEDIATE_PAIR_PRE_INDEXED:
                return true;
        }
        return false;
    }

    /**
     * Checks whether the offset provided is aligned for a given memory operation size.
     *
     * @param bitMemoryTransferSize Memory operation size. This determines the alignment
     *            requirements.
     * @param offset Value to be checked.
     */
    public static boolean isOffsetAligned(int bitMemoryTransferSize, long offset) {
        assert bitMemoryTransferSize == 8 || bitMemoryTransferSize == 16 || bitMemoryTransferSize == 32 || bitMemoryTransferSize == 64 || bitMemoryTransferSize == 128;
        int mask = (bitMemoryTransferSize / Byte.SIZE) - 1;
        return (offset & mask) == 0;
    }

    /**
     * Scales the immediate value according the size of the memory operation.
     *
     * @param bitMemoryTransferSize Memory operation size. This determines now many bits will be
     *            shifted out from the immediate in its scaled representation.
     * @param immediate Value to be scaled.
     * @return The scaled representation of the immediate. If non-zero bits would be shifted out,
     *         then Integer.MAX_VALUE is returned.
     */
    private static int getScaledImmediate(int bitMemoryTransferSize, int immediate) {
        if (!isOffsetAligned(bitMemoryTransferSize, immediate)) {
            /* Non-zero values would be shifted out. */
            return Integer.MAX_VALUE;
        }
        return immediate / (bitMemoryTransferSize / Byte.SIZE);
    }

    /**
     * Creates an address representing the requested immediate addressing mode. Note this method
     * expects an unscaled immediate value and will fail if the provided immediate cannot be encoded
     * within the requested addressing mode.
     *
     * @param bitMemoryTransferSize Memory operation size.
     * @param addressingMode Immediate addressing mode to use.
     * @param base Base register for memory operation. May not be null or the zero-register.
     * @param immediate *Unscaled* immediate value to encode. All scaling needed is performed within
     *            this method
     * @return Address encoding for the input operation.
     */
    public static AArch64Address createImmediateAddress(int bitMemoryTransferSize, AddressingMode addressingMode, Register base, int immediate) {
        GraalError.guarantee(isValidImmediateAddress(bitMemoryTransferSize, addressingMode, immediate), "provided immediate cannot be encoded in instruction");

        boolean isScaled = isImmediateScaled(addressingMode);
        int absoluteImm = immediate;
        if (isScaled) {
            absoluteImm = getScaledImmediate(bitMemoryTransferSize, immediate);
        }

        /* Note register offset scaled field does not matter for immediate addresses. */
        return new AArch64Address(bitMemoryTransferSize, base, zr, absoluteImm, false, null, addressingMode);
    }

    /**
     * Checks whether an immediate can be encoded within the provided immediate addressing mode.
     * This method expected an unscaled immediate value.
     */
    public static boolean isValidImmediateAddress(int bitMemoryTransferSize, AddressingMode addressingMode, int immediate) {
        assert isValidSize(bitMemoryTransferSize, addressingMode) : "invalid transfer size";

        int absoluteImm = immediate;
        if (isImmediateScaled(addressingMode)) {
            absoluteImm = getScaledImmediate(bitMemoryTransferSize, immediate);
            if (absoluteImm == Integer.MAX_VALUE) {
                return false;
            }
        }

        return immediateFitsInInstruction(addressingMode, absoluteImm);
    }

    /**
     * @param bitMemoryTransferSize Memory operation size.
     * @param base May not be null or the zero register.
     * @return an address specifying the address pointed to by base.
     */
    public static AArch64Address createBaseRegisterOnlyAddress(int bitMemoryTransferSize, Register base) {
        return createRegisterOffsetAddress(bitMemoryTransferSize, base, zr, false);
    }

    /**
     * @param bitMemoryTransferSize Memory operation size.
     * @param base may not be null or the zero-register.
     * @param offset Register specifying some offset, optionally scaled by the
     *            byte_memory_transfer_size. May not be null or the stackpointer.
     * @param scaled Specifies whether offset should be scaled by byte_memory_transfer_size or not.
     * @return an address specifying a register offset address of the form base + offset [<< log2
     *         (byte_memory_transfer_size)]
     */
    public static AArch64Address createRegisterOffsetAddress(int bitMemoryTransferSize, Register base, Register offset, boolean scaled) {
        return new AArch64Address(bitMemoryTransferSize, base, offset, 0, scaled, null, AddressingMode.REGISTER_OFFSET);
    }

    /**
     * @param bitMemoryTransferSize Memory operation size.
     * @param base may not be null or the zero-register.
     * @param offset Word register specifying some offset, optionally scaled by the
     *            byte_memory_transfer_size. May not be null or the stackpointer.
     * @param scaled Specifies whether offset should be scaled by byte_memory_transfer_size or not.
     * @param extendType Describes whether register is zero- or sign-extended. May not be null.
     * @return an address specifying an extended register offset of the form base +
     *         extendType(offset) [<< log2(byte_memory_transfer_size)]
     */
    public static AArch64Address createExtendedRegisterOffsetAddress(int bitMemoryTransferSize, Register base, Register offset, boolean scaled, AArch64Assembler.ExtendType extendType) {
        return new AArch64Address(bitMemoryTransferSize, base, offset, 0, scaled, extendType, AddressingMode.EXTENDED_REGISTER_OFFSET);
    }

    /**
     * AArch64Address specifying a PC-literal address of the form PC + imm21
     *
     * Note that the imm21 offset is expected to be patched later.
     *
     * @param bitMemoryTransferSize Memory operation size.
     */
    public static AArch64Address createPCLiteralAddress(int bitMemoryTransferSize) {
        return new AArch64Address(bitMemoryTransferSize, zr, zr, 0, false, null, AddressingMode.PC_LITERAL);
    }

    /**
     * @param bitMemoryTransferSize Memory operation size.
     * @param base May not be null or the zero register.
     * @return an address specifying the address pointed to by base.
     */
    public static AArch64Address createPairBaseRegisterOnlyAddress(int bitMemoryTransferSize, Register base) {
        return createImmediateAddress(bitMemoryTransferSize, AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED, base, 0);
    }

    /**
     * AArch64Address specifying a structure memory access of the form "[Xn|SP]".
     */
    public static AArch64Address createStructureNoOffsetAddress(Register base) {
        return new AArch64Address(ANY_SIZE, base, zr, 0, false, null, AddressingMode.BASE_REGISTER_ONLY);
    }

    /**
     * AArch64Address specifying a structure memory access of the form "[Xn|SP], Xm", where Xm is a
     * post-indexed value to add and cannot be either SP or ZR.
     */
    public static AArch64Address createStructureRegisterPostIndexAddress(Register base, Register offset) {
        return new AArch64Address(ANY_SIZE, base, offset, 0, false, null, AddressingMode.REGISTER_STRUCTURE_POST_INDEXED);
    }

    /**
     * For structure memory accesses the size of the immediate value is dictated by its
     * {instruction, size, eSize} parameters.
     */
    static int determineStructureImmediateValue(ASIMDInstruction instruction, ASIMDSize size, ElementSize eSize) {
        int regByteSize = size.bytes();
        int eByteSize = eSize.bytes();
        switch (instruction) {
            case LD1R:
                return eByteSize;
            case ST1_MULTIPLE_1R:
            case LD1_MULTIPLE_1R:
                return regByteSize;
            case ST1_MULTIPLE_2R:
            case ST2_MULTIPLE_2R:
            case LD1_MULTIPLE_2R:
            case LD2_MULTIPLE_2R:
                return regByteSize * 2;
            case ST1_MULTIPLE_3R:
            case LD1_MULTIPLE_3R:
                return regByteSize * 3;
            case ST1_MULTIPLE_4R:
            case ST4_MULTIPLE_4R:
            case LD1_MULTIPLE_4R:
            case LD4_MULTIPLE_4R:
                return regByteSize * 4;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * AArch64Address specifying a structure memory access of the form "[Xn|SP], imm", where imm is
     * a post-indexed value to add. Note that the value of imm is dictated by the specific
     * {instruction, size, eSize} combination. See
     * {@link #determineStructureImmediateValue(ASIMDInstruction, ASIMDSize, ElementSize)} for what
     * the expected value is.
     */
    public static AArch64Address createStructureImmediatePostIndexAddress(ASIMDInstruction instruction, ASIMDSize size, ElementSize eSize, Register base, int immediate) {
        int expectedImmediate = determineStructureImmediateValue(instruction, size, eSize);
        GraalError.guarantee(expectedImmediate == immediate, "provided immediate cannot be encoded in instruction.");
        return new AArch64Address(ANY_SIZE, base, zr, immediate, false, null, AddressingMode.IMMEDIATE_STRUCTURE_POST_INDEXED);
    }

    private AArch64Address(int bitMemoryTransferSize, Register base, Register offset, int immediate, boolean registerOffsetScaled, AArch64Assembler.ExtendType extendType,
                    AddressingMode addressingMode) {
        this.bitMemoryTransferSize = bitMemoryTransferSize;
        this.base = base;
        this.offset = offset;
        if ((addressingMode == AddressingMode.REGISTER_OFFSET || addressingMode == AddressingMode.EXTENDED_REGISTER_OFFSET) && offset.equals(zr)) {
            this.addressingMode = AddressingMode.BASE_REGISTER_ONLY;
        } else {
            this.addressingMode = addressingMode;
        }
        this.immediate = immediate;
        this.registerOffsetScaled = registerOffsetScaled;
        this.extendType = extendType;
        assert verify();
    }

    private boolean verify() {
        assert bitMemoryTransferSize == ANY_SIZE || bitMemoryTransferSize == 8 || bitMemoryTransferSize == 16 || bitMemoryTransferSize == 32 || bitMemoryTransferSize == 64 ||
                        bitMemoryTransferSize == 128 : bitMemoryTransferSize;
        assert addressingMode != null;
        assert base.getRegisterCategory().equals(AArch64.CPU);
        assert offset.getRegisterCategory().equals(AArch64.CPU);

        switch (addressingMode) {
            case IMMEDIATE_UNSIGNED_SCALED:
                assert bitMemoryTransferSize != ANY_SIZE;
                assert !base.equals(zr);
                assert offset.equals(zr);
                assert extendType == null;
                assert NumUtil.isUnsignedNbit(12, immediate);
                break;
            case IMMEDIATE_SIGNED_UNSCALED:
            case IMMEDIATE_POST_INDEXED:
            case IMMEDIATE_PRE_INDEXED:
                assert !base.equals(zr);
                assert offset.equals(zr);
                assert extendType == null;
                assert NumUtil.isSignedNbit(9, immediate);
                break;
            case BASE_REGISTER_ONLY:
                assert !base.equals(zr);
                assert offset.equals(zr);
                assert extendType == null;
                assert immediate == 0;
                break;
            case REGISTER_OFFSET:
                assert !(registerOffsetScaled && bitMemoryTransferSize == ANY_SIZE);
                assert !base.equals(zr);
                assert !base.equals(offset);
                assert extendType == null;
                assert immediate == 0;
                break;
            case EXTENDED_REGISTER_OFFSET:
                assert !(registerOffsetScaled && bitMemoryTransferSize == ANY_SIZE);
                assert !base.equals(zr);
                assert !base.equals(offset);
                assert (extendType == AArch64Assembler.ExtendType.SXTW || extendType == AArch64Assembler.ExtendType.UXTW);
                assert immediate == 0;
                break;
            case PC_LITERAL:
                assert base.equals(zr);
                assert offset.equals(zr);
                assert extendType == null;
                assert NumUtil.isSignedNbit(21, immediate);
                assert ((immediate & 0x3) == 0);
                break;
            case IMMEDIATE_PAIR_SIGNED_SCALED:
            case IMMEDIATE_PAIR_POST_INDEXED:
            case IMMEDIATE_PAIR_PRE_INDEXED:
                assert bitMemoryTransferSize != ANY_SIZE;
                assert !base.equals(zr);
                assert offset.equals(zr);
                assert extendType == null;
                assert NumUtil.isSignedNbit(7, immediate);
                break;
            case REGISTER_STRUCTURE_POST_INDEXED:
                assert !registerOffsetScaled;
                assert !base.equals(zr);
                assert !(offset.equals(sp) || offset.equals(zr));
                assert extendType == null;
                break;
            case IMMEDIATE_STRUCTURE_POST_INDEXED:
                assert !base.equals(zr);
                assert offset.equals(zr);
                assert extendType == null;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }

        return true;
    }

    public int getBitMemoryTransferSize() {
        return bitMemoryTransferSize;
    }

    public Register getBase() {
        return base;
    }

    public Register getOffset() {
        return offset;
    }

    /**
     * @return immediate in correct representation for the given addressing mode. For example in
     *         case of <code>addressingMode ==IMMEDIATE_UNSCALED </code> the value will be returned
     *         as the 9-bit signed representation.
     */
    public int getImmediate() {
        switch (addressingMode) {
            case IMMEDIATE_SIGNED_UNSCALED:
            case IMMEDIATE_POST_INDEXED:
            case IMMEDIATE_PRE_INDEXED:
                // 9-bit signed value
                assert NumUtil.isSignedNbit(9, immediate);
                return immediate & NumUtil.getNbitNumberInt(9);
            case IMMEDIATE_UNSIGNED_SCALED:
                // Unsigned value can be returned as-is.
                assert NumUtil.isUnsignedNbit(12, immediate);
                return immediate;
            case IMMEDIATE_PAIR_SIGNED_SCALED:
            case IMMEDIATE_PAIR_POST_INDEXED:
            case IMMEDIATE_PAIR_PRE_INDEXED:
                // 7-bit signed value
                assert NumUtil.isSignedNbit(7, immediate);
                return immediate & NumUtil.getNbitNumberInt(7);
            case PC_LITERAL:
                // 21-bit signed value, but lower 2 bits are always 0 and are shifted out.
                assert NumUtil.isSignedNbit(19, immediate >> 2);
                return (immediate >> 2) & NumUtil.getNbitNumberInt(19);
            default:
                throw GraalError.shouldNotReachHere("Should only be called for addressing modes that use immediate values.");
        }
    }

    /**
     * @return Raw immediate as a 32-bit signed value.
     */
    public int getImmediateRaw() {
        switch (addressingMode) {
            case IMMEDIATE_SIGNED_UNSCALED:
            case IMMEDIATE_UNSIGNED_SCALED:
            case IMMEDIATE_POST_INDEXED:
            case IMMEDIATE_PRE_INDEXED:
            case IMMEDIATE_PAIR_SIGNED_SCALED:
            case IMMEDIATE_PAIR_POST_INDEXED:
            case IMMEDIATE_PAIR_PRE_INDEXED:
            case IMMEDIATE_STRUCTURE_POST_INDEXED:
            case PC_LITERAL:
                return immediate;
            default:
                throw GraalError.shouldNotReachHere("Should only be called for addressing modes that use immediate values.");
        }
    }

    public boolean isRegisterOffsetScaled() {
        return registerOffsetScaled;
    }

    public AArch64Assembler.ExtendType getExtendType() {
        return extendType;
    }

    public AddressingMode getAddressingMode() {
        return addressingMode;
    }

    @Override
    public String toString() {
        String addressEncoding;
        String transferSize = bitMemoryTransferSize == ANY_SIZE ? "(unknown)" : Integer.toString(AArch64Assembler.getLog2TransferSize(bitMemoryTransferSize));
        switch (addressingMode) {
            case IMMEDIATE_UNSIGNED_SCALED:
            case IMMEDIATE_PAIR_SIGNED_SCALED:
                addressEncoding = String.format("[X%d, %d << %s]", base.encoding, immediate, transferSize);
                break;
            case IMMEDIATE_SIGNED_UNSCALED:
                addressEncoding = String.format("[X%d, %d]", base.encoding, immediate);
                break;
            case BASE_REGISTER_ONLY:
                addressEncoding = String.format("[X%d]", base.encoding);
                break;
            case EXTENDED_REGISTER_OFFSET:
                if (registerOffsetScaled) {
                    addressEncoding = String.format("[X%d, W%d, %s << %s]", base.encoding, offset.encoding, extendType.name(), transferSize);
                } else {
                    addressEncoding = String.format("[X%d, W%d, %s]", base.encoding, offset.encoding, extendType.name());
                }
                break;
            case REGISTER_OFFSET:
                if (registerOffsetScaled) {
                    addressEncoding = String.format("[X%d, X%d, LSL %s]", base.encoding, offset.encoding, transferSize);
                } else {
                    /*
                     * LSL 0 may be optional, but still encoded differently so we always leave it
                     * off
                     */
                    addressEncoding = String.format("[X%d, X%d]", base.encoding, offset.encoding);
                }
                break;
            case PC_LITERAL:
                addressEncoding = String.format(".%s%d", immediate >= 0 ? "+" : "", immediate);
                break;
            case IMMEDIATE_POST_INDEXED:
            case IMMEDIATE_STRUCTURE_POST_INDEXED:
                addressEncoding = String.format("[X%d], %d", base.encoding, immediate);
                break;
            case IMMEDIATE_PRE_INDEXED:
                addressEncoding = String.format("[X%d, %d]!", base.encoding, immediate);
                break;
            case IMMEDIATE_PAIR_POST_INDEXED:
                addressEncoding = String.format("[X%d], %d << %s", base.encoding, immediate, transferSize);
                break;
            case IMMEDIATE_PAIR_PRE_INDEXED:
                addressEncoding = String.format("[X%d, %d << %s]!", base.encoding, immediate, transferSize);
                break;
            case REGISTER_STRUCTURE_POST_INDEXED:
                addressEncoding = String.format("[X%d], X%d", base.encoding, offset.encoding);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        return String.format("%s: %s", addressingMode.toString(), addressEncoding);
    }
}
