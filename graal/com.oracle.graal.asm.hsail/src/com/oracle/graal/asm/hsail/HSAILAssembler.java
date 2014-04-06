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

import static com.oracle.graal.api.code.MemoryBarriers.*;
import static com.oracle.graal.api.code.ValueUtil.*;

import java.lang.reflect.*;

import com.amd.okra.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hsail.*;

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

    @Override
    public final void ensureUniquePC() {
        throw GraalInternalError.unimplemented();
    }

    public final void undefined(String str) {
        emitString("undefined operation " + str);
    }

    public final void exit() {
        emitString("ret;" + "");
    }

    /**
     * Moves an Object into a register.
     *
     * Because Object references become stale after Garbage collection (GC) the technique used here
     * is to load a JNI global reference to that Object into the register. These JNI global
     * references get updated by the GC whenever the GC moves an Object.
     *
     * @param a the destination register
     * @param obj the Object being moved
     */
    public final void mov(Register a, Object obj) {
        String regName = "$d" + a.encoding();
        // For a null object simply move 0x0 into the destination register.
        if (obj == null) {
            emitString("mov_b64 " + regName + ", 0x0;  // null object");
        } else {
            // Get a JNI reference handle to the object.
            long refHandle = OkraUtil.getRefHandle(obj);
            // Get the clasname of the object for emitting a comment.
            Class<?> clazz = obj.getClass();
            String className = clazz.getName();
            String comment = "// handle for object of type " + className;
            // If the object is an array note the array length in the comment.
            if (className.startsWith("[")) {
                comment += ", length " + Array.getLength(obj);
            }
            // First move the reference handle into a register.
            emitString("mov_b64 " + regName + ", 0x" + Long.toHexString(refHandle) + ";    " + comment);
            // Next load the Object addressed by this reference handle into the destination reg.
            emitString("ld_global_u64 " + regName + ", [" + regName + "];");
        }

    }

    public final void emitMov(Kind kind, Value dst, Value src) {
        if (isRegister(dst) && isConstant(src) && kind.getStackKind() == Kind.Object) {
            mov(asRegister(dst), (asConstant(src)).asObject());
        } else {
            String argtype = getArgTypeFromKind(kind).substring(1);
            emitString("mov_b" + argtype + " " + mapRegOrConstToString(dst) + ", " + mapRegOrConstToString(src) + ";");
        }
    }

    private void emitAddrOp(String instr, Value reg, HSAILAddress addr) {
        String storeValue = null;
        if (reg instanceof RegisterValue) {
            storeValue = HSAIL.mapRegister(reg);
        } else if (reg instanceof Constant) {
            storeValue = ((Constant) reg).asBoxedValue().toString();
        }
        emitString(instr + " " + storeValue + ", " + mapAddress(addr) + ";");
    }

    /**
     * Emits a memory barrier instruction.
     *
     * @param barriers the kind of barrier to emit
     */
    public final void emitMembar(int barriers) {
        if (barriers == 0) {
            emitString("// no barrier before volatile read");
        } else if (barriers == JMM_POST_VOLATILE_READ) {
            emitString("sync; // barriers=" + MemoryBarriers.barriersString(barriers));
        } else if (barriers == JMM_PRE_VOLATILE_WRITE) {
            emitString("sync; // barriers=" + MemoryBarriers.barriersString(barriers));
        } else if (barriers == JMM_POST_VOLATILE_WRITE) {
            emitString("sync; // barriers=" + MemoryBarriers.barriersString(barriers));
        }
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

    public final void emitLoadKernelArg(Value dest, String kernArgName, String argTypeStr) {
        emitString("ld_kernarg_" + argTypeStr + " " + HSAIL.mapRegister(dest) + ", [" + kernArgName + "];");
    }

    public final void emitStore(Kind kind, Value src, HSAILAddress addr) {
        emitStore(src, addr, getArgTypeFromKind(kind));
    }

    public final void emitStore(Value dest, HSAILAddress addr, String argTypeStr) {
        emitAddrOp("st_global_" + argTypeStr, dest, addr);
    }

    private void storeImmediateImpl(String storeType, String value, HSAILAddress addr) {
        emitString("st_global_" + storeType + " " + value + ", " + mapAddress(addr) + ";");
    }

    public final void emitStoreImmediate(Kind kind, long src, HSAILAddress addr) {
        assert (kind != Kind.Float && kind != Kind.Double);
        storeImmediateImpl(getArgTypeFromKind(kind), Long.toString(src), addr);
    }

    public final void emitStoreImmediate(float src, HSAILAddress addr) {
        storeImmediateImpl("f32", Float.toString(src), addr);
    }

    public final void emitStoreImmediate(double src, HSAILAddress addr) {
        storeImmediateImpl("f64", Double.toString(src), addr);
    }

    public final void emitSpillLoad(Kind kind, Value dest, Value src) {
        emitString("ld_spill_" + getArgTypeFromKind(kind) + " " + HSAIL.mapRegister(dest) + ", " + mapStackSlot(src, getArgSizeFromKind(kind)) + ";");
    }

    public final void emitStore(Value src, HSAILAddress addr) {
        emitString("st_global_" + getArgType(src) + " " + HSAIL.mapRegister(src) + ", " + mapAddress(addr) + ";");
    }

    public final void emitSpillStore(Kind kind, Value src, Value dest) {
        int sizestored = getArgSizeFromKind(kind);
        if (maxDataTypeSize < sizestored) {
            maxDataTypeSize = sizestored;
        }
        int stackoffset = HSAIL.getStackOffset(dest);
        if (maxStackOffset < stackoffset) {
            maxStackOffset = stackoffset;
        }
        emitString("st_spill_" + getArgTypeFromKind(kind) + " " + HSAIL.mapRegister(src) + ", " + mapStackSlot(dest, getArgSizeFromKind(kind)) + ";");
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
        return getArgSizeFromKind(src.getKind());
    }

    private static int getArgSizeFromKind(Kind kind) {
        switch (kind) {
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

    private static String getArgType(Value src) {
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
        return getArgTypeForceUnsignedKind(src.getKind());
    }

    public static final String getArgTypeForceUnsignedKind(Kind kind) {
        switch (kind) {
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

    /**
     * Emits a compare instruction.
     *
     * @param src0 - the first source register
     * @param src1 - the second source register
     * @param condition - the compare condition i.e., eq, ne, lt, gt
     * @param unordered - flag specifying if this is an unordered compare. This only applies to
     *            float compares.
     * @param isUnsignedCompare - flag specifying if this is a compare of unsigned values.
     */
    public void emitCompare(Value src0, Value src1, String condition, boolean unordered, boolean isUnsignedCompare) {
        // Formulate the prefix of the instruction.
        // if unordered is true, it should be ignored unless the src type is f32 or f64
        String argType = getArgTypeFromKind(src1.getKind().getStackKind());
        String unorderedPrefix = (argType.startsWith("f") && unordered ? "u" : "");
        String prefix = "cmp_" + condition + unorderedPrefix + "_b1_" + (isUnsignedCompare ? getArgTypeForceUnsigned(src1) : argType);
        // Generate a comment for debugging purposes
        String comment = (isConstant(src1) && (src1.getKind() == Kind.Object) && (asConstant(src1).asObject() == null) ? " // null test " : "");
        // Emit the instruction.
        emitString(prefix + " $c0, " + mapRegOrConstToString(src0) + ", " + mapRegOrConstToString(src1) + ";" + comment);
    }

    public void emitConvert(Value dest, Value src, String destType, String srcType) {
        String prefix = (destType.equals("f32") && srcType.equals("f64")) ? "cvt_near_" : "cvt_";
        emitString(prefix + destType + "_" + srcType + " " + HSAIL.mapRegister(dest) + ", " + HSAIL.mapRegister(src) + ";");
    }

    public void emitConvert(Value dest, Value src, Kind destKind, Kind srcKind) {
        String destType = getArgTypeFromKind(destKind);
        String srcType = getArgTypeFromKind(srcKind);
        emitConvert(dest, src, destType, srcType);
    }

    /**
     * Emits a convert instruction that uses unsigned prefix, regardless of the type of dest and
     * src.
     *
     * @param dest the destination operand
     * @param src the source operand
     */
    public void emitConvertForceUnsigned(Value dest, Value src) {
        emitString("cvt_" + getArgTypeForceUnsigned(dest) + "_" + getArgTypeForceUnsigned(src) + " " + HSAIL.mapRegister(dest) + ", " + HSAIL.mapRegister(src) + ";");
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
                case Boolean:
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

    public final void emitForceUnsignedKind(String mnemonic, Kind kind, Value dest, Value... sources) {
        String prefix = getArgTypeForceUnsignedKind(kind);
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
            case 1:
                // Emit an instruction with one source operand.
                emitString(String.format("%s %s, %s;", instr, HSAIL.mapRegister(dest), mapRegOrConstToString(sources[0])));
                break;
            default:
                // Emit an instruction with one source operand.
                emitString(String.format("%s %s;", instr, HSAIL.mapRegister(dest)));
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
            emitForceUnsignedKind("shl", Kind.Long, result, result, Constant.forInt(shift));
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
            emitForceUnsignedKind("shr", Kind.Long, result, result, Constant.forInt(shift));
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
     * Emits an atomic_cas_global instruction.
     *
     * @param result result operand that gets the original contents of the memory location
     * @param address the memory location
     * @param cmpValue the value that will be compared against the memory location
     * @param newValue the new value that will be written to the memory location if the cmpValue
     *            comparison matches
     */
    public void emitAtomicCas(AllocatableValue result, HSAILAddress address, Value cmpValue, Value newValue) {
        emitString(String.format("atomic_cas_global_b%d   %s, %s, %s, %s;", getArgSize(cmpValue), HSAIL.mapRegister(result), mapAddress(address), mapRegOrConstToString(cmpValue),
                        mapRegOrConstToString(newValue)));
    }

    /**
     * Emits an atomic_add_global instruction.
     *
     * @param result result operand that gets the original contents of the memory location
     * @param address the memory location
     * @param deltaValue the amount to add
     */
    public void emitAtomicAdd(AllocatableValue result, HSAILAddress address, Value deltaValue) {
        emitString(String.format("atomic_add_global_u%d   %s, %s, %s;", getArgSize(result), HSAIL.mapRegister(result), mapAddress(address), mapRegOrConstToString(deltaValue)));
    }

    /**
     * Emits a comment. Useful for debugging purposes.
     *
     * @param comment
     */
    public void emitComment(String comment) {
        emitString(comment);
    }

    public String getDeoptInfoName() {
        return "%_deoptInfo";
    }

    public String getDeoptLabelName() {
        return "@L_Deopt";
    }

    public void emitWorkItemAbsId(Value dest) {
        emitString(String.format("workitemabsid_u32 %s, 0;", HSAIL.mapRegister(dest)));
    }

    public void emitCuId(Value dest) {
        emitString(String.format("cuid_u32 %s;", HSAIL.mapRegister(dest)));
    }

    public void emitLaneId(Value dest) {
        emitString(String.format("laneid_u32 %s;", HSAIL.mapRegister(dest)));
    }

    public void emitWaveId(Value dest) {
        emitString(String.format("waveid_u32 %s;", HSAIL.mapRegister(dest)));
    }

    public void emitMaxWaveId(Value dest) {
        // emitString(String.format("maxwaveid_u32 %s;", HSAIL.mapRegister(dest)));
        int hardCodedMaxWaveId = 36;
        emitComment("// Hard-coded maxwaveid=" + hardCodedMaxWaveId + " until it works");
        emitMov(Kind.Int, dest, Constant.forInt(hardCodedMaxWaveId));
    }

    public void emitMultiplyByWavesize(Value dest) {
        String regName = HSAIL.mapRegister(dest);
        emitString(String.format("mul_u%d %s, %s, WAVESIZE;", getArgSize(dest), regName, regName));
    }

    public void emitGetWavesize(Value dest) {
        String regName = HSAIL.mapRegister(dest);
        emitString(String.format("mov_b%d %s, WAVESIZE;", getArgSize(dest), regName));
    }

    public void emitLoadAcquire(Value dest, HSAILAddress address) {
        emitString(String.format("ld_global_acq_u%d %s, %s;", getArgSize(dest), HSAIL.mapRegister(dest), mapAddress(address)));
    }

    public void emitStoreRelease(Value src, HSAILAddress address) {
        emitString(String.format("st_global_rel_u%d %s, %s;", getArgSize(src), HSAIL.mapRegister(src), mapAddress(address)));
    }
}
