/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.graal.aarch64;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static jdk.vm.ci.aarch64.AArch64.allRegisters;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r19;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r21;
import static jdk.vm.ci.aarch64.AArch64.r22;
import static jdk.vm.ci.aarch64.AArch64.r23;
import static jdk.vm.ci.aarch64.AArch64.r24;
import static jdk.vm.ci.aarch64.AArch64.r25;
import static jdk.vm.ci.aarch64.AArch64.r26;
import static jdk.vm.ci.aarch64.AArch64.r27;
import static jdk.vm.ci.aarch64.AArch64.r28;
import static jdk.vm.ci.aarch64.AArch64.r29;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r31;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.r8;
import static jdk.vm.ci.aarch64.AArch64.r9;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v10;
import static jdk.vm.ci.aarch64.AArch64.v11;
import static jdk.vm.ci.aarch64.AArch64.v12;
import static jdk.vm.ci.aarch64.AArch64.v13;
import static jdk.vm.ci.aarch64.AArch64.v14;
import static jdk.vm.ci.aarch64.AArch64.v15;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.v8;
import static jdk.vm.ci.aarch64.AArch64.v9;
import static jdk.vm.ci.aarch64.AArch64.zr;

import java.util.ArrayList;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class SubstrateAArch64RegisterConfig implements SubstrateRegisterConfig {

    private final TargetDescription target;
    private final int nativeParamsStackOffset;
    private final RegisterArray generalParameterRegs;
    private final RegisterArray simdParameterRegs;
    private final RegisterArray allocatableRegs;
    private final RegisterArray calleeSaveRegisters;
    private final RegisterAttributes[] attributesMap;
    private final MetaAccessProvider metaAccess;
    private final RegisterArray javaGeneralParameterRegisters;
    private final boolean preserveFramePointer;
    public static final Register fp = r29;

    public SubstrateAArch64RegisterConfig(ConfigKind config, MetaAccessProvider metaAccess, TargetDescription target, boolean preserveFramePointer) {
        this.target = target;
        this.metaAccess = metaAccess;
        this.preserveFramePointer = preserveFramePointer;

        // This is the Linux 64-bit ABI for parameters.
        generalParameterRegs = new RegisterArray(r0, r1, r2, r3, r4, r5, r6, r7);
        simdParameterRegs = new RegisterArray(v0, v1, v2, v3, v4, v5, v6, v7);

        javaGeneralParameterRegisters = new RegisterArray(r1, r2, r3, r4, r5, r6, r7, r0);

        nativeParamsStackOffset = 0;

        ArrayList<Register> regs = new ArrayList<>(allRegisters.asList());
        regs.remove(ReservedRegisters.singleton().getFrameRegister()); // sp
        regs.remove(zr);
        // Scratch registers.
        regs.remove(r8);
        regs.remove(r9);
        if (preserveFramePointer) {
            regs.remove(fp); // r29
        }
        /*
         * R31 is not a "real" register - depending on the instruction, this encoding is either zr
         * or sp.
         */
        regs.remove(r31);
        /*
         * If enabled, the heapBaseRegister and threadRegister are r27 and r28, respectively. See
         * AArch64ReservedRegisters and ReservedRegisters for more information.
         */
        regs.remove(ReservedRegisters.singleton().getHeapBaseRegister());
        regs.remove(ReservedRegisters.singleton().getThreadRegister());
        allocatableRegs = new RegisterArray(regs);

        switch (config) {
            case NORMAL:
                calleeSaveRegisters = new RegisterArray();
                break;

            case NATIVE_TO_JAVA:
                calleeSaveRegisters = new RegisterArray(r19, r20, r21, r22, r23, r24, r25, r26, r27, r28,
                                v8, v9, v10, v11, v12, v13, v14, v15);
                break;

            default:
                throw shouldNotReachHere();

        }
        attributesMap = RegisterAttributes.createMap(this, AArch64.allRegisters);
    }

    @Override
    public Register getReturnRegister(JavaKind kind) {
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
            case Object:
                return r0;
            case Float:
            case Double:
                return v0;
            case Void:
                return null;
            default:
                throw shouldNotReachHere();
        }
    }

    @Override
    public RegisterArray getAllocatableRegisters() {
        return allocatableRegs;
    }

    @Override
    public RegisterArray getCalleeSaveRegisters() {
        return calleeSaveRegisters;
    }

    @Override
    public RegisterArray getCallerSaveRegisters() {
        return getAllocatableRegisters();
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        return true;
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        return attributesMap;
    }

    @Override
    public RegisterArray getCallingConventionRegisters(Type t, JavaKind kind) {
        SubstrateCallingConventionType type = (SubstrateCallingConventionType) t;
        switch (kind) {
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
            case Long:
            case Object:
                return (type.nativeABI ? generalParameterRegs : javaGeneralParameterRegisters);
            case Float:
            case Double:
                return simdParameterRegs;
            default:
                throw VMError.shouldNotReachHere();
        }
    }

    public boolean shouldPreserveFramePointer() {
        return preserveFramePointer;
    }

    @Override
    public CallingConvention getCallingConvention(Type t, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
        SubstrateCallingConventionType type = (SubstrateCallingConventionType) t;
        boolean isEntryPoint = type.nativeABI && !type.outgoing;

        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

        int currentGeneral = 0;
        int currentSIMD = 0;

        /*
         * We have to reserve a slot between return address and outgoing parameters for the deopt
         * frame handle. Exception: calls to native methods.
         */
        int currentStackOffset = (type.nativeABI ? nativeParamsStackOffset : target.wordSize);

        JavaKind[] kinds = new JavaKind[locations.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            JavaKind kind = ObjectLayout.getCallSignatureKind(isEntryPoint, (ResolvedJavaType) parameterTypes[i], metaAccess, target);
            kinds[i] = kind;

            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:
                case Long:
                case Object:
                    if (currentGeneral < generalParameterRegs.size()) {
                        Register register = generalParameterRegs.get(currentGeneral++);
                        locations[i] = register.asValue(valueKindFactory.getValueKind(kind.getStackKind()));
                    }
                    break;
                case Float:
                case Double:
                    if (currentSIMD < simdParameterRegs.size()) {
                        Register register = simdParameterRegs.get(currentSIMD++);
                        locations[i] = register.asValue(valueKindFactory.getValueKind(kind));
                    }
                    break;
                default:
                    throw shouldNotReachHere();
            }

            if (locations[i] == null) {
                ValueKind<?> valueKind = valueKindFactory.getValueKind(kind.getStackKind());
                locations[i] = StackSlot.get(valueKind, currentStackOffset, !type.outgoing);
                currentStackOffset += Math.max(valueKind.getPlatformKind().getSizeInBytes(), target.wordSize);
            }
        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : ObjectLayout.getCallSignatureKind(isEntryPoint, (ResolvedJavaType) returnType, metaAccess, target);
        AllocatableValue returnLocation = returnKind == JavaKind.Void ? Value.ILLEGAL : getReturnRegister(returnKind).asValue(valueKindFactory.getValueKind(returnKind.getStackKind()));
        return new SubstrateCallingConvention(type, kinds, currentStackOffset, returnLocation, locations);
    }

    @Override
    public RegisterArray filterAllocatableRegisters(PlatformKind kind, RegisterArray registers) {
        ArrayList<Register> list = new ArrayList<>();
        for (Register reg : registers) {
            if (target.arch.canStoreValue(reg.getRegisterCategory(), kind)) {
                list.add(reg);
            }
        }

        return new RegisterArray(list);
    }

}
