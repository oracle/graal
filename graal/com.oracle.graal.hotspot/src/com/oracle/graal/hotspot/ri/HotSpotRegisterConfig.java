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
package com.oracle.graal.hotspot.ri;

import static com.oracle.max.asm.target.amd64.AMD64.*;

import java.util.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CiCallingConvention.*;
import com.oracle.graal.api.code.CiRegister.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;

public class HotSpotRegisterConfig implements CiRegisterConfig {

    // be careful - the contents of this array are duplicated in graal_CodeInstaller.cpp
    private final CiRegister[] allocatable = {
        rax, rbx, rcx, rdx, rsi, rdi, r8, r9, /* r10, */r11, r12, r13, r14, /*r15*/
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
    };

    private final EnumMap<RegisterFlag, CiRegister[]> categorized = CiRegister.categorize(allocatable);

    private final CiRegisterAttributes[] attributesMap;

    @Override
    public CiRegister[] getAllocatableRegisters() {
        return allocatable;
    }

    @Override
    public EnumMap<RegisterFlag, CiRegister[]> getCategorizedAllocatableRegisters() {
        return categorized;
    }

    @Override
    public CiRegisterAttributes[] getAttributesMap() {
        return attributesMap;
    }

    private final CiRegister[] generalParameterRegisters;
    private final CiRegister[] xmmParameterRegisters = {xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7};
    private final CiRegister[] allParameterRegisters;

    private final CiCalleeSaveLayout csl;

    public HotSpotRegisterConfig(HotSpotVMConfig config, boolean globalStubConfig) {
        if (config.windowsOs) {
            generalParameterRegisters = new CiRegister[] {rdx, r8, r9, rdi, rsi, rcx};
        } else {
            generalParameterRegisters = new CiRegister[] {rsi, rdx, rcx, r8, r9, rdi};
        }

        if (globalStubConfig) {
            CiRegister[] regs = {
                rax,  rcx,  rdx,   rbx,   rsp,   rbp,   rsi,   rdi,
                r8,   r9,   r10,   r11,   r12,   r13,   r14,   r15,
                xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
                xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
            };
            csl = new CiCalleeSaveLayout(0, -1, 8, regs);
        } else {
            // We reserve space for saving RBP but don't explicitly specify
            // it as a callee save register since we explicitly do the saving
            // with push and pop in HotSpotFrameContext
            final int size = 8;
            final CiRegister[] regs = {};
            csl = new CiCalleeSaveLayout(0, size, 8, regs);
        }

        attributesMap = CiRegisterAttributes.createMap(this, AMD64.allRegisters);
        allParameterRegisters = Arrays.copyOf(generalParameterRegisters, generalParameterRegisters.length + xmmParameterRegisters.length);
        System.arraycopy(xmmParameterRegisters, 0, allParameterRegisters, generalParameterRegisters.length, xmmParameterRegisters.length);
    }

    @Override
    public CiRegister[] getCallerSaveRegisters() {
        return getAllocatableRegisters();
    }

    @Override
    public CiRegister getRegisterForRole(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CiCallingConvention getCallingConvention(Type type, Kind[] parameters, CiTarget target, boolean stackOnly) {
        if (type == Type.NativeCall) {
            throw new UnsupportedOperationException();
        }
        return callingConvention(parameters, type, target, stackOnly);
    }

    public CiRegister[] getCallingConventionRegisters(Type type, RegisterFlag flag) {
        return allParameterRegisters;
    }

    private CiCallingConvention callingConvention(Kind[] types, Type type, CiTarget target, boolean stackOnly) {
        Value[] locations = new Value[types.length];

        int currentGeneral = 0;
        int currentXMM = 0;
        int currentStackOffset = 0;

        for (int i = 0; i < types.length; i++) {
            final Kind kind = types[i];

            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:
                case Long:
                case Object:
                    if (!stackOnly && currentGeneral < generalParameterRegisters.length) {
                        CiRegister register = generalParameterRegisters[currentGeneral++];
                        locations[i] = register.asValue(kind);
                    }
                    break;
                case Float:
                case Double:
                    if (!stackOnly && currentXMM < xmmParameterRegisters.length) {
                        CiRegister register = xmmParameterRegisters[currentXMM++];
                        locations[i] = register.asValue(kind);
                    }
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                locations[i] = CiStackSlot.get(kind.stackKind(), currentStackOffset, !type.out);
                currentStackOffset += Math.max(target.sizeInBytes(kind), target.wordSize);
            }
        }

        return new CiCallingConvention(locations, currentStackOffset);
    }

    @Override
    public CiRegister getReturnRegister(Kind kind) {
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
    public CiRegister getScratchRegister() {
        return r10;
    }

    @Override
    public CiRegister getFrameRegister() {
        return rsp;
    }

    public CiCalleeSaveLayout getCalleeSaveLayout() {
        return csl;
    }

    @Override
    public String toString() {
        String res = String.format(
             "Allocatable: " + Arrays.toString(getAllocatableRegisters()) + "%n" +
             "CallerSave:  " + Arrays.toString(getCallerSaveRegisters()) + "%n" +
             "CalleeSave:  " + getCalleeSaveLayout() + "%n" +
             "Scratch:     " + getScratchRegister() + "%n");
        return res;
    }
}
