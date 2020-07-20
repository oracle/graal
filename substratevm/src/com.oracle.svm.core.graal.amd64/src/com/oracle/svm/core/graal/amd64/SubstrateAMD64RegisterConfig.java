/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.r15;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.valueRegistersSSE;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm12;
import static jdk.vm.ci.amd64.AMD64.xmm13;
import static jdk.vm.ci.amd64.AMD64.xmm14;
import static jdk.vm.ci.amd64.AMD64.xmm15;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;

import java.util.ArrayList;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.amd64.AMD64;
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

public class SubstrateAMD64RegisterConfig implements SubstrateRegisterConfig {

    public static final Register HEAP_BASE_REGISTER_CANDIDATE = r14;
    public static final Register THREAD_REGISTER_CANDIDATE = r15;

    private final TargetDescription target;
    private final int nativeParamsStackOffset;
    private final RegisterArray javaGeneralParameterRegs;
    private final RegisterArray nativeGeneralParameterRegs;
    private final RegisterArray xmmParameterRegs;
    private final RegisterArray allocatableRegs;
    private final RegisterArray calleeSaveRegisters;
    private final RegisterAttributes[] attributesMap;
    private final MetaAccessProvider metaAccess;
    private final Register threadRegister;
    private final Register heapBaseRegister;
    private final boolean useBasePointer;

    public SubstrateAMD64RegisterConfig(ConfigKind config, MetaAccessProvider metaAccess, TargetDescription target, boolean useBasePointer) {
        this.target = target;
        this.metaAccess = metaAccess;
        this.useBasePointer = useBasePointer;

        if (OS.getCurrent() == OS.WINDOWS) {
            // This is the Windows 64-bit ABI for parameters.
            // Note that float parameters also "consume" a general register and vice versa.
            nativeGeneralParameterRegs = new RegisterArray(rcx, rdx, r8, r9);
            javaGeneralParameterRegs = new RegisterArray(rdx, r8, r9, rdi, rsi, rcx);
            xmmParameterRegs = new RegisterArray(xmm0, xmm1, xmm2, xmm3);

            // Windows reserves space on the stack for first 4 native parameters
            // even though they are passed in registers.
            nativeParamsStackOffset = 4 * target.wordSize;

            heapBaseRegister = SubstrateOptions.SpawnIsolates.getValue() ? HEAP_BASE_REGISTER_CANDIDATE : null;
            threadRegister = SubstrateOptions.MultiThreaded.getValue() ? THREAD_REGISTER_CANDIDATE : null;

            ArrayList<Register> regs = new ArrayList<>(valueRegistersSSE.asList());
            regs.remove(rsp);
            regs.remove(rbp);
            regs.remove(heapBaseRegister);
            regs.remove(threadRegister);
            allocatableRegs = new RegisterArray(regs);
        } else {
            // This is the Linux 64-bit ABI for parameters.
            javaGeneralParameterRegs = new RegisterArray(rdi, rsi, rdx, rcx, r8, r9);
            nativeGeneralParameterRegs = javaGeneralParameterRegs;
            xmmParameterRegs = new RegisterArray(xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7);

            nativeParamsStackOffset = 0;

            heapBaseRegister = SubstrateOptions.SpawnIsolates.getValue() ? HEAP_BASE_REGISTER_CANDIDATE : null;
            threadRegister = SubstrateOptions.MultiThreaded.getValue() ? THREAD_REGISTER_CANDIDATE : null;

            ArrayList<Register> regs = new ArrayList<>(valueRegistersSSE.asList());
            regs.remove(rsp);
            if (useBasePointer) {
                regs.remove(rbp);
            }
            regs.remove(heapBaseRegister);
            regs.remove(threadRegister);
            allocatableRegs = new RegisterArray(regs);
        }

        switch (config) {
            case NORMAL:
                calleeSaveRegisters = new RegisterArray();
                break;

            case NATIVE_TO_JAVA:
                /*
                 * rbp must be last in the list, so that it gets the location closest to the saved
                 * return address.
                 */
                if (OS.getCurrent() == OS.WINDOWS) {
                    calleeSaveRegisters = new RegisterArray(rbx, rdi, rsi, r12, r13, r14, r15, rbp,
                                    xmm6, xmm7, xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15);
                } else {
                    calleeSaveRegisters = new RegisterArray(rbx, r12, r13, r14, r15, rbp);
                }
                break;

            default:
                throw VMError.shouldNotReachHere();

        }
        attributesMap = RegisterAttributes.createMap(this, AMD64.allRegisters);
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
                return rax;
            case Float:
            case Double:
                return xmm0;
            case Void:
                return null;
            default:
                throw VMError.shouldNotReachHere();
        }
    }

    @Override
    public Register getFrameRegister() {
        return rsp;
    }

    @Override
    public Register getThreadRegister() {
        return threadRegister;
    }

    @Override
    public Register getHeapBaseRegister() {
        return heapBaseRegister;
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
                return (type.nativeABI ? nativeGeneralParameterRegs : javaGeneralParameterRegs);
            case Float:
            case Double:
                return xmmParameterRegs;
            default:
                throw VMError.shouldNotReachHere();
        }
    }

    public boolean shouldUseBasePointer() {
        return this.useBasePointer;
    }

    @Override
    public CallingConvention getCallingConvention(Type t, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
        SubstrateCallingConventionType type = (SubstrateCallingConventionType) t;
        boolean isEntryPoint = type.nativeABI && !type.outgoing;

        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

        int currentGeneral = 0;
        int currentXMM = 0;

        /*
         * We have to reserve a slot between return address and outgoing parameters for the deopt
         * frame handle. Exception: calls to native methods.
         */
        int currentStackOffset = (type.nativeABI ? nativeParamsStackOffset : target.wordSize);

        JavaKind[] kinds = new JavaKind[locations.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            JavaKind kind = ObjectLayout.getCallSignatureKind(isEntryPoint, (ResolvedJavaType) parameterTypes[i], metaAccess, target);
            kinds[i] = kind;

            if (type.nativeABI && OS.getCurrent() == OS.WINDOWS) {
                // Strictly positional: float parameters consume a general register and vice versa
                currentGeneral = i;
                currentXMM = i;
            }
            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:
                case Long:
                case Object:
                    if (currentGeneral < (type.nativeABI ? nativeGeneralParameterRegs.size() : javaGeneralParameterRegs.size())) {
                        Register register = (type.nativeABI ? nativeGeneralParameterRegs.get(currentGeneral++) : javaGeneralParameterRegs.get(currentGeneral++));
                        locations[i] = register.asValue(valueKindFactory.getValueKind(kind.getStackKind()));
                    }
                    break;
                case Float:
                case Double:
                    if (currentXMM < xmmParameterRegs.size()) {
                        Register register = xmmParameterRegs.get(currentXMM++);
                        locations[i] = register.asValue(valueKindFactory.getValueKind(kind));
                    }
                    break;
                default:
                    throw VMError.shouldNotReachHere();
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
