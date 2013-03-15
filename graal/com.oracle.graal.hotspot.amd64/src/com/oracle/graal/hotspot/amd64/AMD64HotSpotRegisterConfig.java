/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;

import java.util.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.code.Register.RegisterFlag;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.phases.*;

// @formatter:off
public class AMD64HotSpotRegisterConfig implements RegisterConfig {

    private final Register[] allocatable = initAllocatable();

    private final EnumMap<RegisterFlag, Register[]> categorized = Register.categorize(allocatable);

    private final RegisterAttributes[] attributesMap;

    @Override
    public Register[] getAllocatableRegisters() {
        return allocatable;
    }

    @Override
    public EnumMap<RegisterFlag, Register[]> getCategorizedAllocatableRegisters() {
        return categorized;
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        return attributesMap;
    }

    private final Register[] javaGeneralParameterRegisters;
    private final Register[] nativeGeneralParameterRegisters;
    private final Register[] xmmParameterRegisters = {xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7};

    private final CalleeSaveLayout csl;

    private static Register findRegister(String name, Register[] all) {
        for (Register reg : all) {
            if (reg.name.equals(name)) {
                return reg;
            }
        }
        throw new IllegalArgumentException("register " + name + " is not allocatable");
    }

    private static Register[] initAllocatable() {
        Register[] allocatable = {
                        rax, rbx, rcx, rdx, /*rsp,*/ rbp, rsi, rdi, r8, r9, /* r10, */r11, r12, r13, r14, /*r15, */
                        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
                        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
                    };

        if (GraalOptions.RegisterPressure != null) {
            String[] names = GraalOptions.RegisterPressure.split(",");
            Register[] regs = new Register[names.length];
            for (int i = 0; i < names.length; i++) {
                regs[i] = findRegister(names[i], allocatable);
            }
            return regs;
        }

        return allocatable;
    }

    public AMD64HotSpotRegisterConfig(HotSpotVMConfig config, boolean globalStubConfig) {
        if (config.windowsOs) {
            javaGeneralParameterRegisters = new Register[] {rdx, r8, r9, rdi, rsi, rcx};
            nativeGeneralParameterRegisters = new Register[] {rcx, rdx, r8, r9};
        } else {
            javaGeneralParameterRegisters = new Register[] {rsi, rdx, rcx, r8, r9, rdi};
            nativeGeneralParameterRegisters = new Register[] {rdi, rsi, rdx, rcx, r8, r9};
        }

        if (globalStubConfig) {
            Register[] regs = {
                rax,  rcx,  rdx,   rbx,   rsp,   rbp,   rsi,   rdi,
                r8,   r9,   r10,   r11,   r12,   r13,   r14,   r15,
                xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
                xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
            };
            csl = new CalleeSaveLayout(0, -1, 8, regs);
        } else {
            csl = null;
        }

        attributesMap = RegisterAttributes.createMap(this, AMD64.allRegisters);
    }

    @Override
    public Register[] getCallerSaveRegisters() {
        return getAllocatableRegisters();
    }

    @Override
    public Register getRegisterForRole(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallingConvention getCallingConvention(Type type, JavaType returnType, JavaType[] parameterTypes, TargetDescription target, boolean stackOnly) {
        if (type == Type.NativeCall) {
            return callingConvention(nativeGeneralParameterRegisters, returnType, parameterTypes, type, target, stackOnly);
        }
        return callingConvention(javaGeneralParameterRegisters, returnType, parameterTypes, type, target, stackOnly);
    }

    public Register[] getCallingConventionRegisters(Type type, RegisterFlag flag) {
        if (flag == RegisterFlag.FPU) {
            return xmmParameterRegisters;
        }
        return type == Type.NativeCall ? nativeGeneralParameterRegisters : javaGeneralParameterRegisters;
    }

    private CallingConvention callingConvention(Register[] generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, Type type, TargetDescription target, boolean stackOnly) {
        Value[] locations = new Value[parameterTypes.length];

        int currentGeneral = 0;
        int currentXMM = 0;
        int currentStackOffset = 0;

        for (int i = 0; i < parameterTypes.length; i++) {
            final Kind kind = parameterTypes[i].getKind();

            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:
                case Long:
                case Object:
                    if (!stackOnly && currentGeneral < generalParameterRegisters.length) {
                        Register register = generalParameterRegisters[currentGeneral++];
                        locations[i] = register.asValue(kind);
                    }
                    break;
                case Float:
                case Double:
                    if (!stackOnly && currentXMM < xmmParameterRegisters.length) {
                        Register register = xmmParameterRegisters[currentXMM++];
                        locations[i] = register.asValue(kind);
                    }
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                locations[i] = StackSlot.get(kind.getStackKind(), currentStackOffset, !type.out);
                currentStackOffset += Math.max(target.sizeInBytes(kind), target.wordSize);
            }
        }

        Kind returnKind = returnType == null ? Kind.Void : returnType.getKind();
        Value returnLocation = returnKind == Kind.Void ? Value.ILLEGAL : getReturnRegister(returnKind).asValue(returnKind);
        return new CallingConvention(currentStackOffset, returnLocation, locations);
    }

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
                return rax;
            case Float:
            case Double:
                return xmm0;
            case Void:
            case Illegal:
                return null;
            default:
                throw new UnsupportedOperationException("no return register for type " + kind);
        }
    }

    @Override
    public Register getScratchRegister() {
        return r10;
    }

    @Override
    public Register getFrameRegister() {
        return rsp;
    }

    public CalleeSaveLayout getCalleeSaveLayout() {
        return csl;
    }

    @Override
    public String toString() {
        String res = String.format(
             "Allocatable: " + Arrays.toString(getAllocatableRegisters()) + "%n" +
             "CallerSave:  " + Arrays.toString(getCallerSaveRegisters()) + "%n" +
             "CalleeSave:  " + getCalleeSaveLayout() + "%n");
        return res;
    }
}
