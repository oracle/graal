/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.unsupportedFeature;
import static jdk.vm.ci.amd64.AMD64.k0;
import static jdk.vm.ci.amd64.AMD64.k1;
import static jdk.vm.ci.amd64.AMD64.k2;
import static jdk.vm.ci.amd64.AMD64.k3;
import static jdk.vm.ci.amd64.AMD64.k4;
import static jdk.vm.ci.amd64.AMD64.k5;
import static jdk.vm.ci.amd64.AMD64.k6;
import static jdk.vm.ci.amd64.AMD64.k7;
import static jdk.vm.ci.amd64.AMD64.r11;
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
import static jdk.vm.ci.amd64.AMD64.valueRegistersAVX512;
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
import static jdk.vm.ci.amd64.AMD64Kind.V128_QWORD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.graal.code.AssignedLocation;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class SubstrateAMD64RegisterConfig implements SubstrateRegisterConfig {

    private final TargetDescription target;
    private final int nativeParamsStackOffset;
    private final List<Register> javaGeneralParameterRegs;
    private final List<Register> nativeGeneralParameterRegs;
    private final List<Register> xmmParameterRegs;
    private final List<Register> allocatableRegs;
    private final List<Register> calleeSaveRegisters;
    private final List<RegisterAttributes> attributesMap;
    private final MetaAccessProvider metaAccess;
    private final boolean useBasePointer;

    private static final List<Register> MASK_REGISTERS = List.of(k1, k2, k3, k4, k5, k6, k7);

    @SuppressWarnings("this-escape")
    public SubstrateAMD64RegisterConfig(ConfigKind config, MetaAccessProvider metaAccess, TargetDescription target, boolean useBasePointer) {
        this.target = target;
        this.metaAccess = metaAccess;
        this.useBasePointer = useBasePointer;

        boolean haveAVX512 = ((AMD64) target.arch).getFeatures().contains(AMD64.CPUFeature.AVX512F);
        ArrayList<Register> regs;
        if (haveAVX512) {
            regs = new ArrayList<>();
            regs.addAll(valueRegistersAVX512);
            /*
             * valueRegistersAVX512 contains all mask registers, including k0. k0 is not a general
             * allocatable register, most instructions that read it interpret it as "no opmask"
             * rather than as a real opmask register.
             */
            regs.remove(k0);
        } else {
            regs = new ArrayList<>();
            regs.addAll(valueRegistersSSE);
            if (SubstrateUtil.HOSTED && RuntimeCompilation.isEnabled()) {
                // The stub calling convention must be able to generate runtime checked code for
                // saving and restoring mask registers.
                regs.addAll(MASK_REGISTERS);
            }
        }
        VMError.guarantee(!regs.contains(k0), "We must never treat k0 as a general allocatable register.");

        if (Platform.includedIn(InternalPlatform.WINDOWS_BASE.class)) {
            // This is the Windows 64-bit ABI for parameters.
            // Note that float parameters also "consume" a general register and vice versa in the
            // native ABI.
            nativeGeneralParameterRegs = List.of(rcx, rdx, r8, r9);

            javaGeneralParameterRegs = List.of(rdx, r8, r9, rdi, rsi, rcx);
            xmmParameterRegs = List.of(xmm0, xmm1, xmm2, xmm3);

            // Windows reserves space on the stack for first 4 native parameters
            // even though they are passed in registers.
            nativeParamsStackOffset = 4 * target.wordSize;

            regs.remove(ReservedRegisters.singleton().getFrameRegister());
            if (useBasePointer) {
                regs.remove(rbp);
            }
            regs.remove(ReservedRegisters.singleton().getHeapBaseRegister());
            regs.remove(ReservedRegisters.singleton().getThreadRegister());
            regs.remove(ReservedRegisters.singleton().getCodeBaseRegister());
            allocatableRegs = List.copyOf(regs);
        } else {
            // This is the Linux 64-bit ABI for parameters.
            javaGeneralParameterRegs = List.of(rdi, rsi, rdx, rcx, r8, r9);
            nativeGeneralParameterRegs = javaGeneralParameterRegs;
            xmmParameterRegs = List.of(xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7);

            nativeParamsStackOffset = 0;

            regs.remove(ReservedRegisters.singleton().getFrameRegister());
            if (useBasePointer) {
                regs.remove(rbp);
            }
            regs.remove(ReservedRegisters.singleton().getHeapBaseRegister());
            regs.remove(ReservedRegisters.singleton().getThreadRegister());
            regs.remove(ReservedRegisters.singleton().getCodeBaseRegister());
            allocatableRegs = List.copyOf(regs);
        }

        switch (config) {
            case NORMAL:
                calleeSaveRegisters = List.of();
                break;

            case NATIVE_TO_JAVA:
                /*
                 * rbp must be last in the list, so that it gets the location closest to the saved
                 * return address.
                 */
                if (Platform.includedIn(InternalPlatform.WINDOWS_BASE.class)) {
                    calleeSaveRegisters = List.of(rbx, rdi, rsi, r12, r13, r14, r15, rbp,
                                    xmm6, xmm7, xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15);
                } else {
                    calleeSaveRegisters = List.of(rbx, r12, r13, r14, r15, rbp);
                }
                break;

            default:
                throw VMError.shouldNotReachHereUnexpectedInput(config); // ExcludeFromJacocoGeneratedReport

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
                throw VMError.shouldNotReachHereUnexpectedInput(kind); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public List<Register> getAllocatableRegisters() {
        return allocatableRegs;
    }

    @Override
    public List<Register> getCalleeSaveRegisters() {
        return calleeSaveRegisters;
    }

    @Override
    public PlatformKind getCalleeSaveRegisterStorageKind(Architecture arch, Register calleeSaveRegister) {
        if (Platform.includedIn(InternalPlatform.WINDOWS_BASE.class) && AMD64.XMM.equals(calleeSaveRegister.getRegisterCategory())) {
            VMError.guarantee(calleeSaveRegister.encoding() >= xmm6.encoding() && calleeSaveRegister.encoding() <= xmm15.encoding(), "unexpected callee saved register");
            return V128_QWORD;
        }
        return SubstrateRegisterConfig.super.getCalleeSaveRegisterStorageKind(arch, calleeSaveRegister);
    }

    @Override
    public List<Register> getCallerSaveRegisters() {
        return getAllocatableRegisters();
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        return true;
    }

    @Override
    public List<RegisterAttributes> getAttributesMap() {
        return attributesMap;
    }

    @Override
    public List<Register> getCallingConventionRegisters(Type t, JavaKind kind) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    public boolean shouldUseBasePointer() {
        return this.useBasePointer;
    }

    @Override
    public CallingConvention getCallingConvention(Type t, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
        SubstrateCallingConventionType type = (SubstrateCallingConventionType) t;
        boolean isEntryPoint = type.nativeABI() && !type.outgoing;

        /*
         * We have to reserve a slot between return address and outgoing parameters for the
         * deoptimized frame (eager deoptimization), or the original return address (lazy
         * deoptimization). Exception: calls to native methods.
         */
        int currentStackOffset = type.nativeABI() ? nativeParamsStackOffset : target.wordSize;

        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];
        JavaKind[] kinds = new JavaKind[locations.length];

        int firstActualArgument = 0;
        if (type.usesReturnBuffer()) {
            VMError.guarantee(type.fixedParameterAssignment != null);
            VMError.guarantee(type.fixedParameterAssignment[0].isPlaceholder());
            /*
             * returnSaving implies an additional (prefix) parameter pointing to the buffer to use
             * for saving. This argument is not actually used by the function, so it will be ignored
             * in the remainder of this method.
             */
            firstActualArgument = 1;
            /*
             * Ideally, we would just pretend this argument never existed and would not give it a
             * location. In practice, it is not so simple, as the generated calling convention is
             * expected to match the arguments, and not just ignore one of them. It might be
             * possible to implement this using some kind of "SinkValue" as the location of the
             * argument. In the meantime, we put it in a scratch register. r10 contains the target,
             * rax the number of vector args, so r11 is the only scratch register left.
             */
            JavaKind kind = ObjectLayout.getCallSignatureKind(isEntryPoint, parameterTypes[0], metaAccess, target);
            kinds[0] = kind;
            ValueKind<?> paramValueKind = valueKindFactory.getValueKind(isEntryPoint ? kind : kind.getStackKind());
            locations[0] = r11.asValue(paramValueKind);
        }

        if (!type.customABI()) {
            int currentGeneral = 0;
            int currentXMM = 0;

            for (int i = firstActualArgument; i < parameterTypes.length; i++) {
                JavaKind kind = ObjectLayout.getCallSignatureKind(isEntryPoint, parameterTypes[i], metaAccess, target);
                kinds[i] = kind;

                if (type.nativeABI() && Platform.includedIn(InternalPlatform.WINDOWS_BASE.class)) {
                    // Strictly positional: float parameters consume a general register and vice
                    // versa
                    currentGeneral = i;
                    currentXMM = i;
                }
                Register register = null;
                if (type.kind == SubstrateCallingConventionKind.ForwardReturnValue) {
                    VMError.guarantee(i == 0, "Method with calling convention ForwardReturnValue cannot have more than one parameter");
                    register = getReturnRegister(kind);
                } else {
                    switch (kind) {
                        case Byte:
                        case Boolean:
                        case Short:
                        case Char:
                        case Int:
                        case Long:
                        case Object:
                            List<Register> registers = type.nativeABI() ? nativeGeneralParameterRegs : javaGeneralParameterRegs;
                            if (currentGeneral < registers.size()) {
                                register = registers.get(currentGeneral++);
                            }
                            break;
                        case Float:
                        case Double:
                            if (currentXMM < xmmParameterRegs.size()) {
                                register = xmmParameterRegs.get(currentXMM++);
                            }
                            break;
                        default:
                            throw VMError.shouldNotReachHereUnexpectedInput(kind); // ExcludeFromJacocoGeneratedReport
                    }
                }

                /*
                 * The AMD64 ABI does not specify whether subword (i.e., boolean, byte, char, short)
                 * values should be extended to 32 bits. Hence, for incoming native calls, we can
                 * only assume the bits sizes as specified in the standard.
                 *
                 * Since within the graal compiler subwords are already extended to 32 bits, we save
                 * extended values in outgoing calls. Note that some compilers also expect arguments
                 * to be extended
                 * (https://reviews.llvm.org/rG1db979bae832563efde2523bb36ddabad43293d8).
                 */
                ValueKind<?> paramValueKind = valueKindFactory.getValueKind(isEntryPoint ? kind : kind.getStackKind());
                if (register != null) {
                    locations[i] = register.asValue(paramValueKind);
                } else {
                    locations[i] = StackSlot.get(paramValueKind, currentStackOffset, !type.outgoing);
                    currentStackOffset += Math.max(paramValueKind.getPlatformKind().getSizeInBytes(), target.wordSize);
                }
            }
        } else {
            final int baseStackOffset = currentStackOffset;
            Set<Register> usedRegisters = new HashSet<>();
            VMError.guarantee(parameterTypes.length == type.fixedParameterAssignment.length, "Parameters/assignments size mismatch.");

            for (int i = firstActualArgument; i < locations.length; i++) {
                JavaKind kind = ObjectLayout.getCallSignatureKind(isEntryPoint, parameterTypes[i], metaAccess, target);
                kinds[i] = kind;

                ValueKind<?> paramValueKind = valueKindFactory.getValueKind(isEntryPoint ? kind : kind.getStackKind());

                AssignedLocation storage = type.fixedParameterAssignment[i];
                if (storage.assignsToRegister()) {
                    if (!kind.isNumericInteger() && !kind.isNumericFloat()) {
                        throw unsupportedFeature("Unsupported storage/kind pair - Storage: " + storage + " ; Kind: " + kind);
                    }
                    Register reg = storage.register();
                    VMError.guarantee(target.arch.canStoreValue(reg.getRegisterCategory(), paramValueKind.getPlatformKind()), "Cannot assign value to register.");
                    locations[i] = reg.asValue(paramValueKind);
                    VMError.guarantee(!usedRegisters.contains(reg), "Register was already used.");
                    usedRegisters.add(reg);
                } else if (storage.assignsToStack()) {
                    /*
                     * There should be no "empty spaces" between arguments on the stack. This
                     * assertion checks so, but assumes that stack arguments are encountered
                     * "in order". This assumption might not be necessary, but simplifies the check
                     * tremendously.
                     */
                    VMError.guarantee(currentStackOffset == baseStackOffset + storage.stackOffset(), "Potential stack ``completeness'' violation.");
                    locations[i] = StackSlot.get(paramValueKind, currentStackOffset, !type.outgoing);
                    currentStackOffset += Math.max(paramValueKind.getPlatformKind().getSizeInBytes(), target.wordSize);
                } else {
                    VMError.shouldNotReachHere("Placeholder assignment.");
                }
            }
        }

        /*
         * Inject a pseudo-argument for rax Its value (which is the number for xmm registers
         * containing an argument) will be injected in
         * SubstrateAMD64NodeLIRBuilder.visitInvokeArguments. This information can be useful for
         * functions taking a variable number of arguments (varargs).
         */
        if (type.nativeABI()) {
            kinds = Arrays.copyOf(kinds, kinds.length + 1);
            locations = Arrays.copyOf(locations, locations.length + 1);
            kinds[kinds.length - 1] = JavaKind.Int;
            locations[locations.length - 1] = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
            if (type.customABI()) {
                var extendsParametersAssignment = Arrays.copyOf(type.fixedParameterAssignment, type.fixedParameterAssignment.length + 1);
                extendsParametersAssignment[extendsParametersAssignment.length - 1] = AssignedLocation.forRegister(rax, JavaKind.Long);
                type = SubstrateCallingConventionType.makeCustom(
                                type.outgoing,
                                extendsParametersAssignment,
                                type.returnSaving);
            }
        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : ObjectLayout.getCallSignatureKind(isEntryPoint, returnType, metaAccess, target);
        AllocatableValue returnLocation = returnKind == JavaKind.Void ? Value.ILLEGAL : getReturnRegister(returnKind).asValue(valueKindFactory.getValueKind(returnKind.getStackKind()));
        return new SubstrateCallingConvention(type, kinds, currentStackOffset, returnLocation, locations);
    }

    @Override
    public List<Register> filterAllocatableRegisters(PlatformKind kind, List<Register> registers) {
        ArrayList<Register> list = new ArrayList<>();
        for (Register reg : registers) {
            if (target.arch.canStoreValue(reg.getRegisterCategory(), kind)) {
                list.add(reg);
            }
        }

        return List.copyOf(list);
    }

    public List<Register> getJavaGeneralParameterRegs() {
        return javaGeneralParameterRegs;
    }

    public List<Register> getFloatingPointParameterRegs() {
        return xmmParameterRegs;
    }
}
