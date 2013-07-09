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
        if (obj instanceof Class) {
            Class<?> clazz = (Class<?>) obj;
            long refHandle = OkraUtil.getRefHandle(clazz);
            String className = clazz.getName();
            String regName = "$d" + a.encoding();
            emitString("mov_b64 " + regName + ", 0x" + Long.toHexString(refHandle) + ";  // handle for " + className);
            emitString("ld_global_u64 " + regName + ", [" + regName + "];");
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

    public final void emitLoad(Value dest, HSAILAddress addr) {
        emitString("ld_global_" + getArgType(dest) + " " + HSAIL.mapRegister(dest) + ", " + mapAddress(addr) + ";");
    }

    public final void emitSpillLoad(Value dest, Value src) {
        emitString("ld_spill_" + getArgType(dest) + " " + HSAIL.mapRegister(dest) + ", " + HSAIL.mapStackSlot(src) + ";");
    }

    public final void emitStore(Value src, HSAILAddress addr) {
        emitString("st_global_" + getArgType(src) + " " + HSAIL.mapRegister(src) + ", " + mapAddress(addr) + ";");
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
        emitString("st_spill_" + getArgType(src) + " " + HSAIL.mapRegister(src) + ", " + HSAIL.mapStackSlot(dest) + ";");
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
        String prefix = "";
        switch (src.getKind()) {
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

    public void emitCompare(Value src0, Value src1, String condition, boolean unordered, boolean isUnsignedCompare) {
        String prefix = "cmp_" + condition + (unordered ? "u" : "") + "_b1_" + (isUnsignedCompare ? getArgTypeForceUnsigned(src1) : getArgType(src1));
        emitString(prefix + " $c0, " + mapRegOrConstToString(src0) + ", " + mapRegOrConstToString(src1) + ";");
    }

    public void emitConvert(Value dest, Value src) {
        String prefix = (getArgType(dest).equals("f32") && getArgType(src).equals("f64")) ? "cvt_near_" : "cvt_";
        emitString(prefix + getArgType(dest) + "_" + getArgType(src) + " " + HSAIL.mapRegister(dest) + ", " + HSAIL.mapRegister(src) + ";");
    }

    public void emitArg1(String mnemonic, Value dest, Value src) {
        emitString(mnemonic + "_" + getArgType(src) + " " + HSAIL.mapRegister(dest) + ", " + mapRegOrConstToString(src) + ";" + "");
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
                    return Long.toString(consrc.asLong());
                default:
                    throw GraalInternalError.shouldNotReachHere("unknown type: " + src);
            }
        }

    }

    public final void emit(String mnemonic, Value dest, Value src0, Value src1) {
        String prefix = getArgType(dest);
        emit(mnemonic + "_" + prefix, dest, "", src0, src1);
    }

    private void emit(String instr, Value dest, String controlRegString, Value src0, Value src1) {
        assert (!isConstant(dest));
        emitString(String.format("%s %s, %s%s, %s;", instr, HSAIL.mapRegister(dest), controlRegString, mapRegOrConstToString(src0), mapRegOrConstToString(src1)));
    }

    public final void cmovCommon(Value dest, Value trueReg, Value falseReg, int width) {
        String instr = (width == 32 ? "cmov_b32" : "cmov_b64");
        emit(instr, dest, "$c0, ", trueReg, falseReg);
    }

}
