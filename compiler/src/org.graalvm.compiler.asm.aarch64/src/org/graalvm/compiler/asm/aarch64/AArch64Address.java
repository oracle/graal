/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.aarch64.AArch64.zr;

import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;

/**
 * Represents an address in target machine memory, specified using one of the different addressing
 * modes of the AArch64 ISA. - Base register only - Base register + immediate or register with
 * shifted offset - Pre-indexed: base + immediate offset are written back to base register, value
 * used in instruction is base + offset - Post-indexed: base + offset (immediate or register) are
 * written back to base register, value used in instruction is base only - Literal: PC + 19-bit
 * signed word aligned offset
 * <p>
 * Not all addressing modes are supported for all instructions.
 */
public final class AArch64Address extends AbstractAddress {
    // Placeholder for addresses that get patched later.
    public static final AArch64Address PLACEHOLDER = createPcLiteralAddress(0);

    public enum AddressingMode {
        /**
         * base + uimm12 << log2(memory_transfer_size).
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
         * base + offset [<< log2(memory_transfer_size)].
         */
        REGISTER_OFFSET,
        /**
         * base + extend(offset) [<< log2(memory_transfer_size)].
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
         * base + imm7 << log2(memory_transfer_size).
         */
        IMMEDIATE_PAIR_SIGNED_SCALED,
        /**
         * address = base. base is updated to base + imm7 << log2(memory_transfer_size)
         */
        IMMEDIATE_PAIR_POST_INDEXED,
        /**
         * address = base + imm7 << log2(memory_transfer_size). base is updated to base + imm7 <<
         * log2(memory_transfer_size)
         */
        IMMEDIATE_PAIR_PRE_INDEXED,
    }

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
    public static AArch64Address createAddress(AddressingMode addressingMode, Register base, Register offset, int immediate, boolean isScaled, AArch64Assembler.ExtendType extendType) {
        return new AArch64Address(base, offset, immediate, isScaled, extendType, addressingMode);
    }

    /**
     * Checks whether the memory size provided is available for the given immediate addressing mode.
     */
    private static boolean isValidSize(int size, AddressingMode mode) {
        switch (mode) {
            case IMMEDIATE_SIGNED_UNSCALED:
            case IMMEDIATE_POST_INDEXED:
            case IMMEDIATE_PRE_INDEXED:
            case IMMEDIATE_UNSIGNED_SCALED:
                return size == 8 || size == 16 || size == 32 || size == 64 || size == 128;
            case IMMEDIATE_PAIR_SIGNED_SCALED:
            case IMMEDIATE_PAIR_POST_INDEXED:
            case IMMEDIATE_PAIR_PRE_INDEXED:
                return size == 32 || size == 64 || size == 128;
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
    private static boolean isImmediateScaled(AddressingMode mode) {
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
     * Scales the immediate value according the size of the memory operation.
     *
     * @param size Memory operation size. This determines now many bits will be shifted out from the
     *            immediate in its scaled representation.
     * @param immediate Value to be scaled.
     * @return The scaled representation of the immediate. If non-zero bits would be shifted out,
     *         then Integer.MAX_VALUE is returned.
     */
    private static int getScaledImmediate(int size, int immediate) {
        int byteSize = size / 8;
        int mask = byteSize - 1;
        if (mask != 0 && ((immediate & mask) != 0)) {
            return Integer.MAX_VALUE;
        }
        return immediate / byteSize;
    }

    /**
     * Creates an address representing the requested immediate addressing mode. Note this method
     * expects an unscaled immediate value and will fail if the provided immediate cannot be encoded
     * within the requested addressing mode.
     *
     * @param size Memory operation size.
     * @param addressingMode Immediate addressing mode to use.
     * @param base Base register for memory operation. May not be null or the zero-register.
     * @param immediate *Unscaled* immediate value to encode. All scaling needed is performed within
     *            this method
     * @return Address encoding for the input operation.
     */
    public static AArch64Address createImmediateAddress(int size, AddressingMode addressingMode, Register base, int immediate) {
        GraalError.guarantee(isValidImmediateAddress(size, addressingMode, immediate), "provided immediate cannot be encoded in instruction");

        boolean isScaled = isImmediateScaled(addressingMode);
        int absoluteImm = immediate;
        if (isScaled) {
            absoluteImm = getScaledImmediate(size, immediate);
        }

        /** note register offset scaled field does not matter for immediate addresses */
        return new AArch64Address(base, zr, absoluteImm, false, null, addressingMode);
    }

    /**
     * Checks whether an immediate can be encoded within the provided immediate addressing mode.
     * This method expected an unscaled immediate value.
     */
    public static boolean isValidImmediateAddress(int size, AddressingMode addressingMode, int immediate) {
        assert isValidSize(size, addressingMode) : "invalid transfer size";

        int absoluteImm = immediate;
        if (isImmediateScaled(addressingMode)) {
            absoluteImm = getScaledImmediate(size, immediate);
            if (absoluteImm == Integer.MAX_VALUE) {
                return false;
            }
        }

        return immediateFitsInInstruction(addressingMode, absoluteImm);
    }

    /**
     * @param base May not be null or the zero register.
     * @return an address specifying the address pointed to by base.
     */
    public static AArch64Address createBaseRegisterOnlyAddress(Register base) {
        return createRegisterOffsetAddress(base, zr, false);
    }

    /**
     * @param base may not be null or the zero-register.
     * @param offset Register specifying some offset, optionally scaled by the memory_transfer_size.
     *            May not be null or the stackpointer.
     * @param scaled Specifies whether offset should be scaled by memory_transfer_size or not.
     * @return an address specifying a register offset address of the form base + offset [<< log2
     *         (memory_transfer_size)]
     */
    public static AArch64Address createRegisterOffsetAddress(Register base, Register offset, boolean scaled) {
        return new AArch64Address(base, offset, 0, scaled, null, AddressingMode.REGISTER_OFFSET);
    }

    /**
     * @param base may not be null or the zero-register.
     * @param offset Word register specifying some offset, optionally scaled by the
     *            memory_transfer_size. May not be null or the stackpointer.
     * @param scaled Specifies whether offset should be scaled by memory_transfer_size or not.
     * @param extendType Describes whether register is zero- or sign-extended. May not be null.
     * @return an address specifying an extended register offset of the form base +
     *         extendType(offset) [<< log2(memory_transfer_size)]
     */
    public static AArch64Address createExtendedRegisterOffsetAddress(Register base, Register offset, boolean scaled, AArch64Assembler.ExtendType extendType) {
        return new AArch64Address(base, offset, 0, scaled, extendType, AddressingMode.EXTENDED_REGISTER_OFFSET);
    }

    /**
     * @param imm21 Signed 21-bit offset, word aligned.
     * @return AArch64Address specifying a PC-literal address of the form PC + offset
     */
    public static AArch64Address createPcLiteralAddress(int imm21) {
        return new AArch64Address(zr, zr, imm21, false, null, AddressingMode.PC_LITERAL);
    }

    private AArch64Address(Register base, Register offset, int immediate, boolean registerOffsetScaled, AArch64Assembler.ExtendType extendType, AddressingMode addressingMode) {
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
        assert addressingMode != null;
        assert base.getRegisterCategory().equals(AArch64.CPU);
        assert offset.getRegisterCategory().equals(AArch64.CPU);

        switch (addressingMode) {
            case IMMEDIATE_UNSIGNED_SCALED:
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
                assert !base.equals(zr);
                assert offset.getRegisterCategory().equals(AArch64.CPU);
                assert extendType == null;
                assert immediate == 0;
                break;
            case EXTENDED_REGISTER_OFFSET:
                assert !base.equals(zr);
                assert offset.getRegisterCategory().equals(AArch64.CPU);
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
                assert !base.equals(zr);
                assert offset.equals(zr);
                assert extendType == null;
                assert NumUtil.isSignedNbit(7, immediate);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }

        return true;
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

    public String toString(int log2TransferSize) {
        int regShiftVal = registerOffsetScaled ? log2TransferSize : 0;
        switch (addressingMode) {
            case IMMEDIATE_UNSIGNED_SCALED:
            case IMMEDIATE_PAIR_SIGNED_SCALED:
                return String.format("[X%d, %d]", base.encoding, immediate << log2TransferSize);
            case IMMEDIATE_SIGNED_UNSCALED:
                return String.format("[X%d, %d]", base.encoding, immediate);
            case BASE_REGISTER_ONLY:
                return String.format("[X%d]", base.encoding);
            case EXTENDED_REGISTER_OFFSET:
                if (regShiftVal != 0) {
                    return String.format("[X%d, W%d, %s %d]", base.encoding, offset.encoding, extendType.name(), regShiftVal);
                } else {
                    return String.format("[X%d, W%d, %s]", base.encoding, offset.encoding, extendType.name());
                }
            case REGISTER_OFFSET:
                if (regShiftVal != 0) {
                    return String.format("[X%d, X%d, LSL %d]", base.encoding, offset.encoding, regShiftVal);
                } else {
                    /*
                     * LSL 0 may be optional, but still encoded differently so we always leave it
                     * off
                     */
                    return String.format("[X%d, X%d]", base.encoding, offset.encoding);
                }
            case PC_LITERAL:
                return String.format(".%s%d", immediate >= 0 ? "+" : "", immediate);
            case IMMEDIATE_POST_INDEXED:
                return String.format("[X%d],%d", base.encoding, immediate);
            case IMMEDIATE_PRE_INDEXED:
                return String.format("[X%d,%d]!", base.encoding, immediate);
            case IMMEDIATE_PAIR_POST_INDEXED:
                return String.format("[X%d],%d", base.encoding, immediate << log2TransferSize);
            case IMMEDIATE_PAIR_PRE_INDEXED:
                return String.format("[X%d,%d]!", base.encoding, immediate << log2TransferSize);
            default:
                throw GraalError.shouldNotReachHere();
        }
    }
}
