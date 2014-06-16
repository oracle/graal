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

package com.oracle.graal.hotspot.hsail;

import static com.oracle.graal.hsail.HSAIL.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.hsail.*;

/**
 * This class defines a higher level interface for the register allocator to be able to access info
 * about the {@link HSAIL} register set.
 *
 * Note: In HSAIL, the number of registers of each type is actually a variable number that must
 * satisfy the equation: Total num of registers = No. of S registers + 2 * (No. of D registers) + 4
 * * (No. of Q registers) = 128 In other words we can have up to 128S or 64 D or 32Q or a blend.
 *
 * For now we haven't implemented support for a variable sized register file. Instead we've fixed
 * the number of registers of each type so that they satisfy the above equation. See {@link HSAIL}
 * for more details.
 */
public class HSAILHotSpotRegisterConfig implements RegisterConfig {

    private final Register[] allocatable = {s0, s1, s2, s3, s4, s5, s6, /* s7, */s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19, s20, s21, s22, s23, s24, s25, s26, s27, s28, s29, s30, s31,
                    d0, d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15, q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11, q12, q13, q14, q15};

    private final Register[] regBitness32 = {s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19, s20, s21, s22, s23, s24, s25, s26, s27, s28, s29, s30, s31};
    private final Register[] regBitness64 = {d0, d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15};
    private final RegisterAttributes[] attributesMap = RegisterAttributes.createMap(this, HSAIL.allRegisters);

    @Override
    public Register getReturnRegister(Kind kind) {
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
            case Object:
                return s0;
            case Float:
            case Double:
                return d0;
            case Void:
            case Illegal:
                return null;
            default:
                throw new UnsupportedOperationException("no return register for type " + kind);
        }
    }

    @Override
    public Register getFrameRegister() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CallingConvention getCallingConvention(Type type, JavaType returnType, JavaType[] parameterTypes, TargetDescription target, boolean stackOnly) {
        return callingConvention(regBitness32, regBitness64, returnType, parameterTypes, type, target, stackOnly);
    }

    private CallingConvention callingConvention(Register[] generalParameterRegisters, Register[] longParameterRegisters, JavaType returnType, JavaType[] parameterTypes, Type type,
                    TargetDescription target, boolean stackOnly) {
        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

        int currentRegs32 = 0;
        int currentRegs64 = 0;
        int currentStackOffset = 0;

        for (int i = 0; i < parameterTypes.length; i++) {
            final Kind kind = parameterTypes[i].getKind();

            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:
                case Float:

                    if (!stackOnly && currentRegs32 < generalParameterRegisters.length) {
                        Register register = generalParameterRegisters[currentRegs32++];
                        locations[i] = register.asValue(target.getLIRKind(kind));
                    }
                    break;
                case Long:
                case Object:
                case Double:
                    if (!stackOnly && currentRegs64 < longParameterRegisters.length) {
                        Register register = longParameterRegisters[currentRegs64++];
                        locations[i] = register.asValue(target.getLIRKind(kind));
                    }
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                locations[i] = StackSlot.get(target.getLIRKind(kind.getStackKind()), currentStackOffset, !type.out);
                currentStackOffset += Math.max(target.getSizeInBytes(kind), target.wordSize);
            }
        }

        Kind returnKind = returnType == null ? Kind.Void : returnType.getKind();
        AllocatableValue returnLocation = returnKind == Kind.Void ? Value.ILLEGAL : getReturnRegister(returnKind).asValue(target.getLIRKind(returnKind));
        return new CallingConvention(currentStackOffset, returnLocation, locations);
    }

    @Override
    public Register[] getCallingConventionRegisters(Type type, Kind kind) {
        throw new GraalInternalError("getCallingConventinoRegisters unimplemented");
    }

    @Override
    public Register[] getAllocatableRegisters() {
        return allocatable.clone();
    }

    @Override
    public Register[] getAllocatableRegisters(PlatformKind kind) {
        Kind primitiveKind;
        if (kind == NarrowOopStamp.NarrowOop) {
            primitiveKind = Kind.Int;
        } else {
            primitiveKind = (Kind) kind;
        }

        switch (primitiveKind) {
            case Int:
            case Short:
            case Byte:
            case Float:
                return regBitness32.clone();
            case Long:
            case Double:
            case Object:
                return regBitness64.clone();

            default:
                throw new GraalInternalError("unknown register allocation");
        }
    }

    @Override
    public Register[] getCallerSaveRegisters() {
        // TODO Auto-generated method stub
        return new Register[0];
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        return false;
    }

    @Override
    public CalleeSaveLayout getCalleeSaveLayout() {
        return null;
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        return attributesMap.clone();
    }

    @Override
    public Register getRegisterForRole(int id) {
        throw new UnsupportedOperationException();
    }

    public boolean isAllocatableSReg(Register reg) {
        return (reg.number >= HSAIL.s0.number && reg.number <= HSAIL.s31.number);
    }

    public boolean isAllocatableDReg(Register reg) {
        return (reg.number >= HSAIL.d0.number && reg.number <= HSAIL.d15.number);
    }

    public HSAILHotSpotRegisterConfig() {

    }
}
