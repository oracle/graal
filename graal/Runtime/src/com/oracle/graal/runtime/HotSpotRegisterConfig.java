/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.oracle.graal.runtime;

import static com.sun.c1x.target.amd64.AMD64.*;

import java.util.*;

import com.sun.c1x.target.amd64.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiCallingConvention.Type;
import com.sun.cri.ci.CiRegister.RegisterFlag;
import com.sun.cri.ri.*;

/**
 * @author Thomas Wuerthinger
 *
 */
public class HotSpotRegisterConfig implements RiRegisterConfig {

    // be careful - the contents of this array are duplicated in c1x_CodeInstaller.cpp
    private final CiRegister[] allocatable = {
        rax, rbx, rcx, rdx, rsi, rdi, r8, r9, /* r10, */r11, r12, r13, r14, /*r15*/
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
    };

    private final EnumMap<RegisterFlag, CiRegister[]> categorized = CiRegister.categorize(allocatable);

    private final RiRegisterAttributes[] attributesMap;

    @Override
    public CiRegister[] getAllocatableRegisters() {
        return allocatable;
    }

    @Override
    public EnumMap<RegisterFlag, CiRegister[]> getCategorizedAllocatableRegisters() {
        return categorized;
    }

    @Override
    public RiRegisterAttributes[] getAttributesMap() {
        return attributesMap;
    }

    private final CiRegister[] generalParameterRegisters;
    private final CiRegister[] xmmParameterRegisters = {xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7};
    private final CiRegister[] allParameterRegisters;

    private final CiRegister[] rsaRegs = {
        rax,  rcx,  rdx,   rbx,   rsp,   rbp,   rsi,   rdi,
        r8,   r9,   r10,   r11,   r12,   r13,   r14,   r15,
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
    };

    private final CiCalleeSaveArea registerSaveArea;


    public HotSpotRegisterConfig(HotSpotVMConfig config, boolean globalStubConfig) {
        if (config.windowsOs) {
            generalParameterRegisters = new CiRegister[] {rdx, r8, r9, rdi, rsi, rcx};
        } else {
            generalParameterRegisters = new CiRegister[] {rsi, rdx, rcx, r8, r9, rdi};
        }

        if (globalStubConfig) {
            registerSaveArea = new CiCalleeSaveArea(-1, 8, rsaRegs);
        } else {
            registerSaveArea = CiCalleeSaveArea.EMPTY;
        }

        attributesMap = RiRegisterAttributes.createMap(this, AMD64.allRegisters);
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
    public CiCallingConvention getCallingConvention(Type type, CiKind[] parameters, CiTarget target) {
        if (type == Type.NativeCall) {
            throw new UnsupportedOperationException();
        }
        return callingConvention(parameters, type, target);
    }

    public CiRegister[] getCallingConventionRegisters(Type type, RegisterFlag flag) {
        return allParameterRegisters;
    }

    private CiCallingConvention callingConvention(CiKind[] types, Type type, CiTarget target) {
        CiValue[] locations = new CiValue[types.length];

        int currentGeneral = 0;
        int currentXMM = 0;
        int currentStackIndex = 0;

        for (int i = 0; i < types.length; i++) {
            final CiKind kind = types[i];

            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:
                case Long:
                case Word:
                case Object:
                    if (currentGeneral < generalParameterRegisters.length) {
                        CiRegister register = generalParameterRegisters[currentGeneral++];
                        locations[i] = register.asValue(kind);
                    }
                    break;
                case Float:
                case Double:
                    if (currentXMM < xmmParameterRegisters.length) {
                        CiRegister register = xmmParameterRegisters[currentXMM++];
                        locations[i] = register.asValue(kind);
                    }
                    break;
                default:
                    throw Util.shouldNotReachHere();
            }

            if (locations[i] == null) {
                // we need to adjust for the frame pointer stored on the stack, which shifts incoming arguments by one slot
                locations[i] = CiStackSlot.get(kind.stackKind(), currentStackIndex + (type.out ? 0 : 1), !type.out);
                currentStackIndex += target.spillSlots(kind);
            }
        }

        return new CiCallingConvention(locations, currentStackIndex * target.spillSlotSize);
    }

    @Override
    public CiRegister getReturnRegister(CiKind kind) {
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
            case Object:
            case Word:
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

    public CiCalleeSaveArea getCalleeSaveArea() {
        return registerSaveArea;
    }

    @Override
    public String toString() {
        String res = String.format(
             "Allocatable: " + Arrays.toString(getAllocatableRegisters()) + "%n" +
             "CallerSave:  " + Arrays.toString(getCallerSaveRegisters()) + "%n" +
             "CalleeSave:  " + getCalleeSaveArea() + "%n" +
             "Scratch:     " + getScratchRegister() + "%n");
        return res;
    }
}
