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

package com.oracle.graal.asm.hsail;

import com.oracle.graal.api.code.*;

import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.graph.GraalInternalError;
import com.amd.okra.OkraUtil;

/**
 * This class contains routines to emit HSAIL assembly code.
 */
public class HSAILAssembler extends AbstractHSAILAssembler {

    /**
     * Stack size in bytes (used to keep track of spilling).
     */
    private int maxDataTypeSize;

    /**
     * Maximum stack offset used by a store operation.
     */
    private long maxStackOffset = 0;

    public long upperBoundStackSize() {
        return maxStackOffset + maxDataTypeSize;
    }

    public HSAILAssembler(TargetDescription target) {
        super(target);
    }

    @Override
    public HSAILAddress makeAddress(Register base, int displacement) {
        return new HSAILAddress(base, displacement);
    }

    @Override
    public HSAILAddress getPlaceholder() {
        return null;
    }

    public final void undefined(String str) {
        emitString("undefined operation " + str);
    }

    public final void exit() {
        emitString("ret;" + "");
    }

    /**
     * An Object is only moved into a register when it is a class constant (which is not really a
     * constant because it can be moved by GC). Because we can't patch the HSAIL once it is
     * finalized, we handle changes due to GC movement by dereferencing a global reference that is
     * created by JNI since these JNI global references do not move.
     */
    public final void mov(Register a, Object obj) {
        String regName = "$d" + a.encoding();
        if (obj instanceof Class) {
            Class<?> clazz = (Class<?>) obj;
            long refHandle = OkraUtil.getRefHandle(clazz);
            String className = clazz.getName();
            emitString("mov_b64 " + regName + ", 0x" + Long.toHexString(refHandle) + ";  // handle for " + className);
            emitString("ld_global_u64 " + regName + ", [" + regName + "];");
        } else if (obj == null) {
            emitString("mov_b64 " + regName + ", 0x0;  // null object");
        } else {
            throw GraalInternalError.shouldNotReachHere("mov from object not a class");
        }

    }

    public final void emitMov(Value dst, Value src) {
        if (isRegister(dst) && isConstant(src) && src.getKind().getStackKind() == Kind.Object) {
            mov(asRegister(dst), (asConstant(src)).asObject());
        } else {
            String argtype = getArgType(dst).substring(1);
            emitString("mov_b" + argtype + " " + mapRegOrConstToString(dst) + ", " + mapRegOrConstToString(src) + ";");
        }
    }

    private void emitAddrOp(String instr, Value reg, HSAILAddress addr) {
        emitString(instr + " " + HSAIL.mapRegister(reg) + ", " + mapAddress(addr) + ";");
    }

    public final void emitLoad(Kind kind, Value dest, HSAILAddress addr) {
        emitLoad(dest, addr, getArgTypeFromKind(kind));
    }

    public final void emitLoad(Value dest, HSAILAddress addr, String argTypeStr) {
        emitAddrOp("ld_global_" + argTypeStr, dest, addr);
    }

    public final void emitLda(Value dest, HSAILAddress addr) {
        emitAddrOp("lda_global_u64", dest, addr);
    }

    public final void emitStore(Kind kind, Value src, HSAILAddress addr) {
        emitStore(src, addr, getArgTypeFromKind(kind));
    }

    public final void emitStore(Value dest, HSAILAddress addr, String argTypeStr) {
        emitAddrOp("st_global_" + argTypeStr, dest, addr);
    }

    public final void emitSpillLoad(Value dest, Value src) {
        emitString("ld_spill_" + getArgType(dest) + " " + HSAIL.mapRegister(dest) + ", " + mapStackSlot(src, getArgSize(dest)) + ";");
    }

    public final void emitSpillStore(Value src, Value dest) {
        int sizestored = getArgSize(src);
        if (maxDataTypeSize < sizestored) {
            maxDataTypeSize = sizestored;
        }
        int stackoffset = HSAIL.getStackOffset(dest);
        if (maxStackOffset < stackoffset) {
            maxStackOffset = stackoffset;
        }
        emitString("st_spill_" + getArgType(src) + " " + HSAIL.mapRegister(src) + ", " + mapStackSlot(dest, getArgSize(src)) + ";");
    }

    /**
     * The mapping to stack slots is always relative to the beginning of the spillseg.
     * HSAIL.getStackOffset returns the positive version of the originally negative offset. Then we
     * back up from that by the argSize in bytes. This ensures that slots of different size do not
     * overlap, even though we have converted from negative to positive offsets.
     */
    public static String mapStackSlot(Value reg, int argSize) {
        long offset = HSAIL.getStackOffset(reg);
        int argSizeBytes = argSize / 8;
        return "[%spillseg]" + "[" + (offset - argSizeBytes) + "]";
    }

    public void cbr(String target1) {
        emitString("cbr " + "$c0" + ", " + target1 + ";");
    }

    public int getArgSize(Value src) {
        switch (src.getKind()) {
            case Int:
            case Float:
                return 32;
            case Double:
            case Long:
            case Object:
                return 64;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static final String getArgType(Value src) {
        return getArgTypeFromKind(src.getKind());
    }

    private static String getArgTypeFromKind(Kind kind) {
        String prefix = "";
        switch (kind) {
            case Float:
                prefix = "f32";
                break;
            case Double:
                prefix = "f64";
                break;
            case Int:
                prefix = "s32";
                break;
            case Long:
                prefix = "s64";
                break;
            case Object:
                prefix = "u64";
                break;
            case Char:
                prefix = "u16";
                break;
            case Short:
                prefix = "s16";
                break;
            case Byte:
                prefix = "s8";
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return prefix;
    }

    public static final String getArgTypeForceUnsigned(Value src) {
        switch (src.getKind()) {
            case Int:
                return "u32";
            case Long:
            case Object:
                return "u64";
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static final String getArgTypeBitwiseLogical(Value src) {
        String length = getArgType(src);
        String prefix = "_b" + (length.endsWith("64") ? "64" : "32");
        return prefix;
    }

    public void emitCompare(Value src0, Value src1, String condition, boolean unordered, boolean isUnsignedCompare) {
        String prefix = "cmp_" + condition + (unordered ? "u" : "") + "_b1_" + (isUnsignedCompare ? getArgTypeForceUnsigned(src1) : getArgType(src1));
        String comment = (isConstant(src1) && (src1.getKind() == Kind.Object) && (asConstant(src1).asObject() == null) ? " // null test " : "");
        emitString(prefix + " $c0, " + mapRegOrConstToString(src0) + ", " + mapRegOrConstToString(src1) + ";" + comment);
    }

    public void emitConvert(Value dest, Value src, Kind destKind, Kind srcKind) {
        String destType = getArgTypeFromKind(destKind);
        String srcType = getArgTypeFromKind(srcKind);
        String prefix = (destType.equals("f32") && srcType.equals("f64")) ? "cvt_near_" : "cvt_";
        emitString(prefix + destType + "_" + srcType + " " + HSAIL.mapRegister(dest) + ", " + HSAIL.mapRegister(src) + ";");
    }

    public static String mapAddress(HSAILAddress addr) {
        return "[$d" + addr.getBase().encoding() + " + " + addr.getDisplacement() + "]";
    }

    private static String mapRegOrConstToString(Value src) {
        if (!isConstant(src)) {
            return HSAIL.mapRegister(src);
        } else {
            Constant consrc = asConstant(src);
            switch (src.getKind()) {
                case Int:
                    return Integer.toString(consrc.asInt());
                case Float:
                    return Float.toString(consrc.asFloat()) + "f";
                case Double:
                    return Double.toString(consrc.asDouble());
                case Long:
                    return "0x" + Long.toHexString(consrc.asLong());
                case Object:
                    Object obj = consrc.asObject();
                    if (obj == null) {
                        return "0";
                    } else {
                        throw GraalInternalError.shouldNotReachHere("unknown type: " + src);
                    }
                default:
                    throw GraalInternalError.shouldNotReachHere("unknown type: " + src);
            }
        }

    }

    /**
     * Emits an instruction.
     * 
     * @param mnemonic the instruction mnemonic
     * @param dest the destination operand
     * @param sources the source operands
     */
    public final void emit(String mnemonic, Value dest, Value... sources) {
        String prefix = getArgType(dest);
        emitTextFormattedInstruction(mnemonic + "_" + prefix, dest, sources);
    }

    /**
     * Emits an unsigned instruction.
     * 
     * @param mnemonic the instruction mnemonic
     * @param dest the destination argument
     * @param sources the source arguments
     * 
     */
    public final void emitForceUnsigned(String mnemonic, Value dest, Value... sources) {
        String prefix = getArgTypeForceUnsigned(dest);
        emitTextFormattedInstruction(mnemonic + "_" + prefix, dest, sources);
    }

    /**
     * Emits an instruction for a bitwise logical operation.
     * 
     * @param mnemonic the instruction mnemonic
     * @param dest the destination
     * @param sources the source operands
     */
    public final void emitForceBitwise(String mnemonic, Value dest, Value... sources) {
        String prefix = getArgTypeBitwiseLogical(dest);
        emitTextFormattedInstruction(mnemonic + prefix, dest, sources);
    }

    /**
     * Central helper routine that emits a text formatted HSAIL instruction via call to
     * AbstractAssembler.emitString. All the emit routines in the assembler end up calling this one.
     * 
     * @param instr the full instruction mnenomics including any prefixes
     * @param dest the destination operand
     * @param sources the source operand
     */
    private void emitTextFormattedInstruction(String instr, Value dest, Value... sources) {
        /**
         * Destination can't be a constant and no instruction has > 3 source operands.
         */
        assert (!isConstant(dest) && sources.length <= 3);
        switch (sources.length) {
            case 3:
                // Emit an instruction with three source operands.
                emitString(String.format("%s %s, %s, %s, %s;", instr, HSAIL.mapRegister(dest), mapRegOrConstToString(sources[0]), mapRegOrConstToString(sources[1]), mapRegOrConstToString(sources[2])));
                break;
            case 2:
                // Emit an instruction with two source operands.
                emitString(String.format("%s %s, %s, %s;", instr, HSAIL.mapRegister(dest), mapRegOrConstToString(sources[0]), mapRegOrConstToString(sources[1])));
                break;
            default:
                // Emit an instruction with one source operand.
                emitString(String.format("%s %s, %s;", instr, HSAIL.mapRegister(dest), mapRegOrConstToString(sources[0])));
                break;
        }
    }

    /**
     * Emits a conditional move instruction.
     * 
     * @param dest the destination operand storing result of the move
     * @param trueReg the register that should be copied to dest if the condition is true
     * @param falseReg the register that should be copied to dest if the condition is false
     * @param width the width of the instruction (32 or 64 bits)
     */
    public final void emitConditionalMove(Value dest, Value trueReg, Value falseReg, int width) {
        assert (!isConstant(dest));
        String instr = (width == 32 ? "cmov_b32" : "cmov_b64");
        emitString(String.format("%s %s, %s%s, %s;", instr, HSAIL.mapRegister(dest), "$c0, ", mapRegOrConstToString(trueReg), mapRegOrConstToString(falseReg)));
    }

    /**
     * Emits code to build a 64-bit pointer from a compressed value and the associated base and
     * shift. The compressed value could represent either a normal oop or a klass ptr. If the
     * compressed value is 0, the uncompressed must also be 0. We only emit this if base and shift
     * are not both zero.
     * 
     * @param result the register containing the compressed value on input and the uncompressed ptr
     *            on output
     * @param base the amount to be added to the compressed value
     * @param shift the number of bits to shift left the compressed value
     * @param testForNull true if the compressed value might be null
     */
    public void emitCompressedOopDecode(Value result, long base, int shift, boolean testForNull) {
        assert (base != 0 || shift != 0);
        assert (!isConstant(result));
        if (base == 0) {
            // we don't have to test for null if shl is the only operation
            emitForceUnsigned("shl", result, result, Constant.forInt(shift));
        } else if (shift == 0) {
            // only use add if result is not starting as null (test only if testForNull is true)
            emitWithOptionalTestForNull(testForNull, "add", result, result, Constant.forLong(base));
        } else {
            // only use mad if result is not starting as null (test only if testForNull is true)
            emitWithOptionalTestForNull(testForNull, "mad", result, result, Constant.forInt(1 << shift), Constant.forLong(base));
        }
    }

    /**
     * Emits code to build a compressed value from a full 64-bit pointer using the associated base
     * and shift. The compressed value could represent either a normal oop or a klass ptr. If the
     * ptr is 0, the compressed value must also be 0. We only emit this if base and shift are not
     * both zero.
     * 
     * @param result the register containing the 64-bit pointer on input and the compressed value on
     *            output
     * @param base the amount to be subtracted from the 64-bit pointer
     * @param shift the number of bits to shift right the 64-bit pointer
     * @param testForNull true if the 64-bit pointer might be null
     */
    public void emitCompressedOopEncode(Value result, long base, int shift, boolean testForNull) {
        assert (base != 0 || shift != 0);
        assert (!isConstant(result));
        if (base != 0) {
            // only use sub if result is not starting as null (test only if testForNull is true)
            emitWithOptionalTestForNull(testForNull, "sub", result, result, Constant.forLong(base));
        }
        if (shift != 0) {
            // note that the shr can still be done even if the result is null
            emitForceUnsigned("shr", result, result, Constant.forInt(shift));
        }
    }

    /**
     * Emits code for the requested mnemonic on the result and sources. In addition, if testForNull
     * is true, surrounds the instruction with code that will guarantee that if the result starts as
     * 0, it will remain 0.
     * 
     * @param testForNull true if we want to add the code to check for and preserve null
     * @param mnemonic the instruction to be applied (without size prefix)
     * @param result the register which is both an input and the final output
     * @param sources the sources for the mnemonic instruction
     */
    private void emitWithOptionalTestForNull(boolean testForNull, String mnemonic, Value result, Value... sources) {
        if (testForNull) {
            emitCompare(result, Constant.forLong(0), "eq", false, true);
        }
        emitForceUnsigned(mnemonic, result, sources);
        if (testForNull) {
            emitConditionalMove(result, Constant.forLong(0), result, 64);
        }
    }

    /**
     * Emits a comment. Useful for debugging purposes.
     * 
     * @param comment
     */
    public void emitComment(String comment) {
        emitString(comment);
    }
}
