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
package com.oracle.svm.core.graal.aarch64;

import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;
import static com.oracle.svm.core.util.VMError.unsupportedFeature;
import static jdk.vm.ci.aarch64.AArch64.allRegisters;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r18;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.aarch64.SubstrateAArch64MacroAssembler;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.AssignedLocation;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.aarch64.AArch64;
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

public class SubstrateAArch64RegisterConfig implements SubstrateRegisterConfig {

    private final TargetDescription target;
    private final int nativeParamsStackOffset;
    private final List<Register> generalParameterRegs;
    private final List<Register> fpParameterRegs;
    private final List<Register> allocatableRegs;
    private final List<Register> calleeSaveRegisters;
    private final List<RegisterAttributes> attributesMap;
    private final MetaAccessProvider metaAccess;
    private final boolean preserveFramePointer;
    public static final Register fp = r29;

    @SuppressWarnings("this-escape")
    public SubstrateAArch64RegisterConfig(ConfigKind config, MetaAccessProvider metaAccess, TargetDescription target, boolean preserveFramePointer) {
        this.target = target;
        this.metaAccess = metaAccess;
        this.preserveFramePointer = preserveFramePointer;

        /*
         * This is the Linux 64-bit ABI for parameters.
         *
         * Note the Darwin and Windows ABI are the same with the following exception:
         *
         * On Windows, when calling a method with variadic args, all fp parameters must be passed on
         * the stack. Currently, this is unsupported. Adding support is tracked by GR-34188.
         */
        generalParameterRegs = List.of(r0, r1, r2, r3, r4, r5, r6, r7);
        fpParameterRegs = List.of(v0, v1, v2, v3, v4, v5, v6, v7);

        nativeParamsStackOffset = 0;

        ArrayList<Register> regs = new ArrayList<>(allRegisters);
        regs.remove(ReservedRegisters.singleton().getFrameRegister()); // sp
        regs.remove(zr);

        // Scratch registers.
        regs.remove(SubstrateAArch64MacroAssembler.scratch1);
        regs.remove(SubstrateAArch64MacroAssembler.scratch2);

        if (preserveFramePointer) {
            regs.remove(fp); // r29
        }
        /*
         * R31 is not a "real" register - depending on the instruction, this encoding is either zr
         * or sp.
         */
        regs.remove(r31);
        /* Reserved registers: see AArch64ReservedRegisters and ReservedRegisters for details. */
        regs.remove(ReservedRegisters.singleton().getHeapBaseRegister());
        regs.remove(ReservedRegisters.singleton().getThreadRegister());
        regs.remove(ReservedRegisters.singleton().getCodeBaseRegister());
        /*
         * Darwin and Windows specify that r18 is a platform-reserved register:
         *
         * https://developer.apple.com/documentation/xcode/writing-arm64-code-for-apple-platforms
         *
         * https://docs.microsoft.com/en-us/cpp/build/arm64-windows-abi-conventions
         *
         * Android uses r18 for maintaining a shadow call stack:
         *
         * https://developer.android.com/ndk/guides/abis#arm64-v8a
         */
        if (Platform.includedIn(Platform.DARWIN.class) || Platform.includedIn(InternalPlatform.WINDOWS_BASE.class) || Platform.includedIn(Platform.ANDROID.class)) {
            regs.remove(r18);
        }
        allocatableRegs = List.copyOf(regs);

        switch (config) {
            case NORMAL:
                calleeSaveRegisters = List.of();
                break;

            case NATIVE_TO_JAVA:
                calleeSaveRegisters = List.of(r19, r20, r21, r22, r23, r24, r25, r26, r27, r28,
                                v8, v9, v10, v11, v12, v13, v14, v15);
                break;

            default:
                throw shouldNotReachHereUnexpectedInput(config); // ExcludeFromJacocoGeneratedReport

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
                throw shouldNotReachHereUnexpectedInput(kind); // ExcludeFromJacocoGeneratedReport
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

    public boolean shouldPreserveFramePointer() {
        return preserveFramePointer;
    }

    private int javaStackParameterAssignment(ValueKindFactory<?> valueKindFactory, AllocatableValue[] locations, int index, JavaKind kind, int currentStackOffset, boolean isOutgoing) {
        /* All parameters within Java are assigned slots of at least 8 bytes */
        ValueKind<?> valueKind = valueKindFactory.getValueKind(kind.getStackKind());
        int alignment = Math.max(valueKind.getPlatformKind().getSizeInBytes(), target.wordSize);
        locations[index] = StackSlot.get(valueKind, currentStackOffset, !isOutgoing);
        return currentStackOffset + alignment;
    }

    /**
     * The Linux calling convention expects stack arguments to be aligned to at least 8 bytes, but
     * any unused padding bits have unspecified values.
     *
     * For more details, see <a
     * href=https://github.com/ARM-software/abi-aa/blob/d6e9abbc5e9cdcaa0467d8187eec0049b44044c4/aapcs64/aapcs64.rst#parameter-passing-rules>the
     * AArch64 procedure call standard</a>.
     */
    private int linuxNativeStackParameterAssignment(ValueKindFactory<?> valueKindFactory, AllocatableValue[] locations, int index, JavaKind kind, int currentStackOffset, boolean isOutgoing) {
        ValueKind<?> valueKind = valueKindFactory.getValueKind(isOutgoing ? kind.getStackKind() : kind);
        int alignment = Math.max(kind.getByteCount(), target.wordSize);
        locations[index] = StackSlot.get(valueKind, currentStackOffset, !isOutgoing);
        return currentStackOffset + alignment;
    }

    /**
     * The Darwin calling convention expects stack arguments to be aligned to the argument kind.
     *
     * For more details, see <a
     * href=https://developer.apple.com/documentation/xcode/writing-arm64-code-for-apple-platforms>https://developer.apple.com/documentation/xcode/writing-arm64-code-for-apple-platforms</a>.
     */
    private static int darwinNativeStackParameterAssignment(ValueKindFactory<?> valueKindFactory, AllocatableValue[] locations, int index, JavaKind kind, int currentStackOffset, boolean isOutgoing) {
        int paramByteSize = kind.getByteCount();
        int alignedStackOffset = NumUtil.roundUp(currentStackOffset, paramByteSize);
        locations[index] = StackSlot.get(valueKindFactory.getValueKind(kind), alignedStackOffset, !isOutgoing);
        return alignedStackOffset + paramByteSize;
    }

    @Override
    public CallingConvention getCallingConvention(Type t, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
        SubstrateCallingConventionType type = (SubstrateCallingConventionType) t;
        boolean isEntryPoint = type.nativeABI() && !type.outgoing;

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
             * argument. In the meantime, we put it in r8.
             */
            JavaKind kind = ObjectLayout.getCallSignatureKind(isEntryPoint, parameterTypes[0], metaAccess, target);
            kinds[0] = kind;
            ValueKind<?> paramValueKind = valueKindFactory.getValueKind(isEntryPoint ? kind : kind.getStackKind());
            locations[0] = AArch64.r8.asValue(paramValueKind);

            for (int i = 1; i < type.fixedParameterAssignment.length; i++) {
                AssignedLocation storage = type.fixedParameterAssignment[i];
                if (storage.assignsToRegister()) {
                    assert !storage.register().equals(AArch64.r8);
                }
            }
        }

        /*
         * We have to reserve a slot between return address and outgoing parameters for the
         * deoptimized frame (eager deoptimization), or the original return address (lazy
         * deoptimization). Exception: calls to native methods.
         */
        int currentStackOffset = (type.nativeABI() ? nativeParamsStackOffset : target.wordSize);
        boolean isDarwinPlatform = Platform.includedIn(Platform.DARWIN.class);

        if (!type.customABI()) {
            int currentGeneral = 0;
            int currentFP = 0;

            for (int i = firstActualArgument; i < parameterTypes.length; i++) {
                JavaKind kind = ObjectLayout.getCallSignatureKind(isEntryPoint, parameterTypes[i], metaAccess, target);
                kinds[i] = kind;

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
                            if (currentGeneral < generalParameterRegs.size()) {
                                register = generalParameterRegs.get(currentGeneral++);
                            }
                            break;
                        case Float:
                        case Double:
                            if (currentFP < fpParameterRegs.size()) {
                                register = fpParameterRegs.get(currentFP++);
                            }
                            break;
                        default:
                            throw shouldNotReachHereUnexpectedInput(kind); // ExcludeFromJacocoGeneratedReport
                    }

                }
                if (register != null) {
                    /*
                     * The AArch64 procedure call standard does not require subword (i.e., boolean,
                     * byte, char, short) values to be extended to 32 bits. Hence, for incoming
                     * native calls, we can only assume the bits sizes as specified in the standard.
                     *
                     * Since within the graal compiler subwords are already extended to 32 bits, we
                     * save extended values in outgoing calls.
                     *
                     * Darwin deviates from the call standard and requires the caller to extend
                     * subword values.
                     */
                    boolean useJavaKind = isEntryPoint && !isDarwinPlatform;
                    locations[i] = register.asValue(valueKindFactory.getValueKind(useJavaKind ? kind : kind.getStackKind()));
                } else {
                    if (type.nativeABI()) {
                        if (isDarwinPlatform) {
                            currentStackOffset = darwinNativeStackParameterAssignment(valueKindFactory, locations, i, kind, currentStackOffset, type.outgoing);
                        } else {
                            currentStackOffset = linuxNativeStackParameterAssignment(valueKindFactory, locations, i, kind, currentStackOffset, type.outgoing);
                        }
                    } else {
                        currentStackOffset = javaStackParameterAssignment(valueKindFactory, locations, i, kind, currentStackOffset, type.outgoing);
                    }
                }
            }
        } else {
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
                    assert currentStackOffset <= storage.stackOffset() : "currentStackOffset=" + currentStackOffset + ", stackOffset=" + storage.stackOffset();
                    locations[i] = StackSlot.get(valueKindFactory.getValueKind(kind), storage.stackOffset(), !type.outgoing);
                    currentStackOffset = storage.stackOffset();
                } else {
                    throw VMError.shouldNotReachHere("Placeholder assignment.");
                }
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
        return generalParameterRegs;
    }
}
