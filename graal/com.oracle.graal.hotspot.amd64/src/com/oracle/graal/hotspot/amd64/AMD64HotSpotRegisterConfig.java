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
import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.util.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.type.*;

public class AMD64HotSpotRegisterConfig implements RegisterConfig {

    private final Architecture architecture;

    private final Register[] allocatable;

    private final int maxFrameSize;

    /**
     * The same as {@link #allocatable}, except if parameter registers are removed with the
     * {@link GraalOptions#RegisterPressure} option. The caller saved registers always include all
     * parameter registers.
     */
    private final Register[] callerSaved;

    private final boolean allAllocatableAreCallerSaved;

    private final HashMap<PlatformKind, Register[]> categorized = new HashMap<>();

    private final RegisterAttributes[] attributesMap;

    public int getMaximumFrameSize() {
        return maxFrameSize;
    }

    @Override
    public Register[] getAllocatableRegisters() {
        return allocatable.clone();
    }

    public Register[] getAllocatableRegisters(PlatformKind kind) {
        if (categorized.containsKey(kind)) {
            return categorized.get(kind);
        }

        PlatformKind primitiveKind;
        if (kind == NarrowOopStamp.NarrowOop) {
            primitiveKind = Kind.Int;
        } else {
            primitiveKind = kind;
        }

        ArrayList<Register> list = new ArrayList<>();
        for (Register reg : getAllocatableRegisters()) {
            if (architecture.canStoreValue(reg.getRegisterCategory(), primitiveKind)) {
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

    private static Register[] initAllocatable(boolean reserveForHeapBase) {
        Register[] registers = null;
        // @formatter:off
        if (reserveForHeapBase) {
            registers = new Register[] {
                        rax, rbx, rcx, rdx, /*rsp,*/ rbp, rsi, rdi, r8, r9,  r10, r11, /*r12,*/ r13, r14, /*r15, */
                        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
                        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
                      };
        } else {
            registers = new Register[] {
                        rax, rbx, rcx, rdx, /*rsp,*/ rbp, rsi, rdi, r8, r9,  r10, r11, r12, r13, r14, /*r15, */
                        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
                        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
                      };
        }
       // @formatter:on

        if (RegisterPressure.getValue() != null) {
            String[] names = RegisterPressure.getValue().split(",");
            Register[] regs = new Register[names.length];
            for (int i = 0; i < names.length; i++) {
                regs[i] = findRegister(names[i], registers);
            }
            return regs;
        }

        return registers;
    }

    public AMD64HotSpotRegisterConfig(Architecture architecture, HotSpotVMConfig config) {
        this(architecture, config, initAllocatable(config.useCompressedOops));
        assert callerSaved.length == allocatable.length || RegisterPressure.getValue() != null;
    }

    public AMD64HotSpotRegisterConfig(Architecture architecture, HotSpotVMConfig config, Register[] allocatable) {
        this.architecture = architecture;
        this.maxFrameSize = config.maxFrameSize;

        if (config.windowsOs) {
            javaGeneralParameterRegisters = new Register[]{rdx, r8, r9, rdi, rsi, rcx};
            nativeGeneralParameterRegisters = new Register[]{rcx, rdx, r8, r9};
        } else {
            javaGeneralParameterRegisters = new Register[]{rsi, rdx, rcx, r8, r9, rdi};
            nativeGeneralParameterRegisters = new Register[]{rdi, rsi, rdx, rcx, r8, r9};
        }

        csl = null;
        this.allocatable = allocatable.clone();
        Set<Register> callerSaveSet = new HashSet<>();
        Collections.addAll(callerSaveSet, allocatable);
        Collections.addAll(callerSaveSet, xmmParameterRegisters);
        Collections.addAll(callerSaveSet, javaGeneralParameterRegisters);
        Collections.addAll(callerSaveSet, nativeGeneralParameterRegisters);
        callerSaved = callerSaveSet.toArray(new Register[callerSaveSet.size()]);

        allAllocatableAreCallerSaved = true;
        attributesMap = RegisterAttributes.createMap(this, AMD64.allRegisters);
    }

    @Override
    public Register[] getCallerSaveRegisters() {
        return callerSaved;
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        return allAllocatableAreCallerSaved;
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
        // On x64, parameter locations are the same whether viewed
        // from the caller or callee perspective
        return callingConvention(javaGeneralParameterRegisters, returnType, parameterTypes, type, target, stackOnly);
    }

    public Register[] getCallingConventionRegisters(Type type, Kind kind) {
        if (architecture.canStoreValue(XMM, kind)) {
            return xmmParameterRegisters;
        }
        assert architecture.canStoreValue(CPU, kind);
        return type == Type.NativeCall ? nativeGeneralParameterRegisters : javaGeneralParameterRegisters;
    }

    private CallingConvention callingConvention(Register[] generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, Type type, TargetDescription target, boolean stackOnly) {
        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

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
                        locations[i] = register.asValue(target.getLIRKind(kind));
                    }
                    break;
                case Float:
                case Double:
                    if (!stackOnly && currentXMM < xmmParameterRegisters.length) {
                        Register register = xmmParameterRegisters[currentXMM++];
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
        AllocatableValue returnLocation = returnKind == Kind.Void ? Value.ILLEGAL : getReturnRegister(returnKind).asValue(target.getLIRKind(returnKind.getStackKind()));
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
    public Register getFrameRegister() {
        return rsp;
    }

    public CalleeSaveLayout getCalleeSaveLayout() {
        return csl;
    }

    @Override
    public String toString() {
        return String.format("Allocatable: " + Arrays.toString(getAllocatableRegisters()) + "%n" + "CallerSave:  " + Arrays.toString(getCallerSaveRegisters()) + "%n");
    }
}
