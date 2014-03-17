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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.graph.*;

public class PTXHotSpotRegisterConfig implements RegisterConfig {

    private final Register[] allocatable;

    @Override
    public Register[] getAllocatableRegisters() {
        return allocatable.clone();
    }

    @Override
    public Register[] getAllocatableRegisters(PlatformKind kind) {
        throw GraalInternalError.unimplemented("PTXHotSpotRegisterConfig.getAllocatableRegisters()");
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        throw GraalInternalError.unimplemented("PTXHotSpotRegisterConfig.getAttributesMap()");
    }

    private final Register[] javaGeneralParameterRegisters;
    private final Register[] nativeGeneralParameterRegisters;

    private static Register[] initAllocatable() {
        Register[] registers = new Register[]{r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14, r15};

        return registers;
    }

    public PTXHotSpotRegisterConfig() {
        javaGeneralParameterRegisters = paramRegisters;
        nativeGeneralParameterRegisters = gprRegisters;

        allocatable = initAllocatable();
    }

    @Override
    public Register[] getCallerSaveRegisters() {
        // No caller save registers; return empty array
        return new Register[]{};
    }

    @Override
    public Register getRegisterForRole(int index) {
        throw GraalInternalError.unimplemented("PTXHotSpotRegisterConfig.getRegisterForRole()");
    }

    @Override
    public CallingConvention getCallingConvention(Type type, JavaType returnType, JavaType[] parameterTypes, TargetDescription target, boolean stackOnly) {
        if (type == Type.NativeCall) {
            return callingConvention(nativeGeneralParameterRegisters, returnType, parameterTypes, type, target, stackOnly);
        }
        return callingConvention(javaGeneralParameterRegisters, returnType, parameterTypes, type, target, stackOnly);
    }

    @Override
    public Register[] getCallingConventionRegisters(Type type, Kind kind) {
        throw GraalInternalError.unimplemented("PTXHotSpotRegisterConfig.getRegisterForRole()");
    }

    private static CallingConvention callingConvention(@SuppressWarnings("unused") Register[] generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, Type type,
                    TargetDescription target, boolean stackOnly) {

        assert stackOnly == false;

        int currentGeneral = 0;
        int currentStackOffset = 0;

        Kind returnKind = returnType == null ? Kind.Void : returnType.getKind();

        AllocatableValue returnLocation;
        if (returnKind == Kind.Void) {
            returnLocation = Value.ILLEGAL;
        } else {
            returnLocation = new Variable(returnKind, currentGeneral++);
        }

        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

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
                    if (!stackOnly) {
                        locations[i] = new Variable(kind, currentGeneral++);
                    }
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                locations[i] = StackSlot.get(kind.getStackKind(), currentStackOffset, !type.out);
                currentStackOffset += Math.max(target.getSizeInBytes(kind), target.wordSize);
            }
        }

        return new CallingConvention(currentStackOffset, returnLocation, locations);
    }

    @Override
    public Register getReturnRegister(Kind kind) {
        throw GraalInternalError.unimplemented("PTXHotSpotRegisterConfig.getRegisterForRole()");
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
