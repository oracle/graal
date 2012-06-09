/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import java.util.*;

import com.oracle.graal.api.code.CallingConvention.*;
import com.oracle.graal.api.code.Register.*;
import com.oracle.graal.api.meta.*;

/**
 * A default implementation of {@link RegisterConfig}.
 */
public class RegisterConfigImpl implements RegisterConfig {

    /**
     * The object describing the callee save area of this register configuration.
     */
    public CalleeSaveLayout csl;

    /**
     * The minimum register role identifier.
     */
    public final int minRole;

    /**
     * The map from register role IDs to registers.
     */
    public final Register[] registersRoleMap;

    /**
     * The set of registers that can be used by the register allocator.
     */
    public final Register[] allocatable;

    /**
     * The set of registers that can be used by the register allocator,
     * {@linkplain Register#categorize(Register[]) categorized} by register
     * {@linkplain RegisterFlag flags}.
     */
    public final EnumMap<RegisterFlag, Register[]> categorized;

    /**
     * The ordered set of registers used to pass integral arguments.
     */
    public final Register[] cpuParameters;

    /**
     * The ordered set of registers used to pass floating point arguments.
     */
    public final Register[] fpuParameters;

    /**
     * The caller saved registers.
     */
    public final Register[] callerSave;

    /**
     * The register to which {@link Register#Frame} and {@link Register#CallerFrame} are bound.
     */
    public final Register frame;

    /**
     * Register for returning an integral value.
     */
    public final Register integralReturn;

    /**
     * Register for returning a floating point value.
     */
    public final Register floatingPointReturn;

    /**
     * The map from register {@linkplain Register#number numbers} to register
     * {@linkplain RegisterAttributes attributes} for this register configuration.
     */
    public final RegisterAttributes[] attributesMap;

    /**
     * The scratch register.
     */
    public final Register scratch;

    /**
     * The frame offset of the first stack argument for each calling convention {@link CallingConvention.Type}.
     */
    public final int[] stackArg0Offsets = new int[CallingConvention.Type.VALUES.length];

    public RegisterConfigImpl(
                    Register frame,
                    Register integralReturn,
                    Register floatingPointReturn,
                    Register scratch,
                    Register[] allocatable,
                    Register[] callerSave,
                    Register[] parameters,
                    CalleeSaveLayout csl,
                    Register[] allRegisters,
                    Map<Integer, Register> roles) {
        this.frame = frame;
        this.csl = csl;
        this.allocatable = allocatable;
        this.callerSave = callerSave;
        assert !Arrays.asList(allocatable).contains(scratch);
        this.scratch = scratch;
        EnumMap<RegisterFlag, Register[]> categorizedParameters = Register.categorize(parameters);
        this.cpuParameters = categorizedParameters.get(RegisterFlag.CPU);
        this.fpuParameters = categorizedParameters.get(RegisterFlag.FPU);
        categorized = Register.categorize(allocatable);
        attributesMap = RegisterAttributes.createMap(this, allRegisters);
        this.floatingPointReturn = floatingPointReturn;
        this.integralReturn = integralReturn;
        int minRoleId = Integer.MAX_VALUE;
        int maxRoleId = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Register> e : roles.entrySet()) {
            int id = e.getKey();
            assert id >= 0;
            if (minRoleId > id) {
                minRoleId = id;
            }
            if (maxRoleId < id) {
                maxRoleId = id;
            }
        }
        registersRoleMap = new Register[(maxRoleId - minRoleId) + 1];
        for (Map.Entry<Integer, Register> e : roles.entrySet()) {
            int id = e.getKey();
            registersRoleMap[id] = e.getValue();
        }
        minRole = minRoleId;
    }

    public RegisterConfigImpl(RegisterConfigImpl src, CalleeSaveLayout csl) {
        this.frame = src.frame;
        this.csl = csl;
        this.allocatable = src.allocatable;
        this.callerSave = src.callerSave;
        this.scratch = src.scratch;
        this.cpuParameters = src.cpuParameters;
        this.fpuParameters = src.fpuParameters;
        this.categorized = src.categorized;
        this.attributesMap = src.attributesMap;
        this.floatingPointReturn = src.floatingPointReturn;
        this.integralReturn = src.integralReturn;
        this.registersRoleMap = src.registersRoleMap;
        this.minRole = src.minRole;
        System.arraycopy(src.stackArg0Offsets, 0, stackArg0Offsets, 0, stackArg0Offsets.length);
    }

    public Register getReturnRegister(Kind kind) {
        if (kind.isDouble() || kind.isFloat()) {
            return floatingPointReturn;
        }
        return integralReturn;
    }

    public Register getFrameRegister() {
        return frame;
    }

    public Register getScratchRegister() {
        return scratch;
    }

    /**
     * {@inheritDoc}
     *
     * This implementation assigns all available registers to parameters before assigning
     * any stack slots to parameters.
     */
    public CallingConvention getCallingConvention(Type type, Kind[] parameters, TargetDescription target, boolean stackOnly) {
        Value[] locations = new Value[parameters.length];

        int currentGeneral = 0;
        int currentXMM = 0;
        int currentStackOffset = stackArg0Offsets[type.ordinal()];

        for (int i = 0; i < parameters.length; i++) {
            final Kind kind = parameters[i];

            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:
                case Long:
                case Object:
                    if (!stackOnly && currentGeneral < cpuParameters.length) {
                        Register register = cpuParameters[currentGeneral++];
                        locations[i] = register.asValue(kind);
                    }
                    break;

                case Float:
                case Double:
                    if (!stackOnly && currentXMM < fpuParameters.length) {
                        Register register = fpuParameters[currentXMM++];
                        locations[i] = register.asValue(kind);
                    }
                    break;

                default:
                    throw new InternalError("Unexpected parameter kind: " + kind);
            }

            if (locations[i] == null) {
                locations[i] = StackSlot.get(kind.stackKind(), currentStackOffset, !type.out);
                currentStackOffset += Math.max(target.sizeInBytes(kind), target.wordSize);
            }
        }

        return new CallingConvention(locations, currentStackOffset);
    }

    public Register[] getCallingConventionRegisters(Type type, RegisterFlag flag) {
        if (flag == RegisterFlag.CPU) {
            return cpuParameters;
        }
        assert flag == RegisterFlag.FPU;
        return fpuParameters;
    }

    public Register[] getAllocatableRegisters() {
        return allocatable;
    }

    public EnumMap<RegisterFlag, Register[]> getCategorizedAllocatableRegisters() {
        return categorized;
    }

    public Register[] getCallerSaveRegisters() {
        return callerSave;
    }

    public CalleeSaveLayout getCalleeSaveLayout() {
        return csl;
    }

    public RegisterAttributes[] getAttributesMap() {
        return attributesMap;
    }

    public Register getRegisterForRole(int id) {
        return registersRoleMap[id - minRole];
    }

    @Override
    public String toString() {
        StringBuilder roleMap = new StringBuilder();
        for (int i = 0; i < registersRoleMap.length; ++i) {
            Register reg = registersRoleMap[i];
            if (reg != null) {
                if (roleMap.length() != 0) {
                    roleMap.append(", ");
                }
                roleMap.append(i + minRole).append(" -> ").append(reg);
            }
        }
        StringBuilder stackArg0OffsetsMap = new StringBuilder();
        for (Type t : Type.VALUES) {
            if (stackArg0OffsetsMap.length() != 0) {
                stackArg0OffsetsMap.append(", ");
            }
            stackArg0OffsetsMap.append(t).append(" -> ").append(stackArg0Offsets[t.ordinal()]);
        }
        String res = String.format(
             "Allocatable: " + Arrays.toString(getAllocatableRegisters()) + "%n" +
             "CallerSave:  " + Arrays.toString(getCallerSaveRegisters()) + "%n" +
             "CalleeSave:  " + getCalleeSaveLayout() + "%n" +
             "CPU Params:  " + Arrays.toString(cpuParameters) + "%n" +
             "FPU Params:  " + Arrays.toString(fpuParameters) + "%n" +
             "VMRoles:     " + roleMap + "%n" +
             "stackArg0:   " + stackArg0OffsetsMap + "%n" +
             "Scratch:     " + getScratchRegister() + "%n");
        return res;
    }
}
