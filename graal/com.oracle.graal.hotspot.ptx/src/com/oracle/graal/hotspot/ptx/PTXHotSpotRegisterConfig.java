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
package com.oracle.graal.hotspot.ptx;

import static com.oracle.graal.ptx.PTX.*;

import java.util.*;

import com.oracle.graal.ptx.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;

public class PTXHotSpotRegisterConfig implements RegisterConfig {

    private final Architecture architecture;

    private final Register[] allocatable;

    private final HashMap<PlatformKind, Register[]> categorized = new HashMap<>();

    private final RegisterAttributes[] attributesMap;

    @Override
    public Register[] getAllocatableRegisters() {
        return allocatable.clone();
    }

    public Register[] getAllocatableRegisters(PlatformKind kind) {
        if (categorized.containsKey(kind)) {
            return categorized.get(kind);
        }

        ArrayList<Register> list = new ArrayList<>();
        for (Register reg : getAllocatableRegisters()) {
            if (architecture.canStoreValue(reg.getRegisterCategory(), kind)) {
                list.add(reg);
            }
        }

        Register[] ret = list.toArray(new Register[0]);
        categorized.put(kind, ret);
        return ret;
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        return attributesMap.clone();
    }

    private final Register[] javaGeneralParameterRegisters;
    private final Register[] nativeGeneralParameterRegisters;

    private static Register[] initAllocatable() {
        Register[] registers = new Register[] {
            param0, param1, param2, param3,
            param4, param5, param6, param7,
            r0,  r1,  r2,  r3,  r4,  r5,  r6,  r7,
            r8,  r9,  r10, r11, r12, r13, r14, r15,
            // retReg,
        };

        return registers;
    }

    public PTXHotSpotRegisterConfig(Architecture architecture) {
        this.architecture = architecture;

        javaGeneralParameterRegisters = paramRegisters;
        nativeGeneralParameterRegisters = gprRegisters;

        allocatable = initAllocatable();
        attributesMap = RegisterAttributes.createMap(this, PTX.allRegisters);
    }

    @Override
    public Register[] getCallerSaveRegisters() {
        // No caller save registers; return empty array
        return new Register[]{};
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

    public Register[] getCallingConventionRegisters(Type type, Kind kind) {
        assert architecture.canStoreValue(REG, kind);
        return type == Type.NativeCall ? nativeGeneralParameterRegisters : javaGeneralParameterRegisters;
    }

    private CallingConvention callingConvention(Register[] generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, Type type, TargetDescription target, boolean stackOnly) {
        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

        int currentGeneral = 0;
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
                case Float:
                case Double:
                case Object:
                    if (!stackOnly && currentGeneral < generalParameterRegisters.length) {
                        Register register = generalParameterRegisters[currentGeneral++];
                        locations[i] = register.asValue(kind);
                    }
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                locations[i] = StackSlot.get(kind.getStackKind(), currentStackOffset, !type.out);
                currentStackOffset += Math.max(target.arch.getSizeInBytes(kind), target.wordSize);
            }
        }

        Kind returnKind = returnType == null ? Kind.Void : returnType.getKind();
        AllocatableValue returnLocation = returnKind == Kind.Void ? Value.ILLEGAL : getReturnRegister(returnKind).asValue(returnKind);
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
            case Float:
            case Double:
                return retReg;
            case Void:
            case Illegal:
                return null;
            default:
                throw new UnsupportedOperationException("no return register for type " + kind);
        }
    }

    @Override
    public Register getFrameRegister() {
        // No frame register
        return null;
    }

    public CalleeSaveLayout getCalleeSaveLayout() {
        return null;
    }

    @Override
    public String toString() {
        return String.format("Allocatable: " + Arrays.toString(getAllocatableRegisters()) + "%n" + "CallerSave:  " + Arrays.toString(getCallerSaveRegisters()) + "%n");
    }
}
