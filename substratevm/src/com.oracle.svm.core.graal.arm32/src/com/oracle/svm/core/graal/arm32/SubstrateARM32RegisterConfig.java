/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * ...
 */

// ファイルパス: substratevm/src/com.oracle.svm.core.graal.arm32/src/com/oracle/svm/core/graal/arm32/SubstrateARM32RegisterConfig.java

package com.oracle.svm.core.graal.arm32;

import static com.oracle.svm.shared.util.VMError.intentionallyUnimplemented;
import static com.oracle.svm.shared.util.VMError.shouldNotReachHereUnexpectedInput;
import static jdk.vm.ci.arm.ARM.allRegisters;
import static jdk.vm.ci.arm.ARM.r0;
import static jdk.vm.ci.arm.ARM.r1;
import static jdk.vm.ci.arm.ARM.r2;
import static jdk.vm.ci.arm.ARM.r3;
import static jdk.vm.ci.arm.ARM.r4;
import static jdk.vm.ci.arm.ARM.r5;
import static jdk.vm.ci.arm.ARM.r6;
import static jdk.vm.ci.arm.ARM.r7;
import static jdk.vm.ci.arm.ARM.r8;
import static jdk.vm.ci.arm.ARM.r9;
import static jdk.vm.ci.arm.ARM.r10;
import static jdk.vm.ci.arm.ARM.r11;
import static jdk.vm.ci.arm.ARM.r12;
import static jdk.vm.ci.arm.ARM.r13;
import static jdk.vm.ci.arm.ARM.r14;
import static jdk.vm.ci.arm.ARM.s0;
import static jdk.vm.ci.arm.ARM.s1;
import static jdk.vm.ci.arm.ARM.s2;
import static jdk.vm.ci.arm.ARM.s3;
import static jdk.vm.ci.arm.ARM.s4;
import static jdk.vm.ci.arm.ARM.s5;
import static jdk.vm.ci.arm.ARM.s6;
import static jdk.vm.ci.arm.ARM.s7;
import static jdk.vm.ci.arm.ARM.s8;
import static jdk.vm.ci.arm.ARM.s9;
import static jdk.vm.ci.arm.ARM.s10;
import static jdk.vm.ci.arm.ARM.s11;
import static jdk.vm.ci.arm.ARM.s12;
import static jdk.vm.ci.arm.ARM.s13;
import static jdk.vm.ci.arm.ARM.s14;
import static jdk.vm.ci.arm.ARM.s15;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.shared.util.VMError;

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

/**
 * ARM32 register configuration following AAPCS (ARM Architecture Procedure Call Standard).
 *
 * Calling convention:
 *   Integer args: r0-r3 (first 4), then stack
 *   Float args: s0-s15 (VFPv3 hard-float), then stack
 *   Return: r0 (int/ref), r0:r1 (long), s0 (float), d0 (double)
 */
public class SubstrateARM32RegisterConfig implements SubstrateRegisterConfig {

    private final TargetDescription target;
    private final int nativeParamsStackOffset;
    private final List<Register> intParameterRegs;
    private final List<Register> fpParameterRegs;
    private final List<Register> allocatableRegs;
    private final List<Register> calleeSaveRegisters;
    private final List<RegisterAttributes> attributesMap;
    private final MetaAccessProvider metaAccess;

    @SuppressWarnings("this-escape")
    public SubstrateARM32RegisterConfig(ConfigKind config, MetaAccessProvider metaAccess, TargetDescription target, boolean preserveFramePointer) {
        this.target = target;
        this.metaAccess = metaAccess;

        // AAPCS: r0-r3 for integer arguments
        intParameterRegs = List.of(r0, r1, r2, r3);
        // AAPCS hard-float: s0-s15 for float arguments
        fpParameterRegs = List.of(s0, s1, s2, s3, s4, s5, s6, s7,
                                   s8, s9, s10, s11, s12, s13, s14, s15);

        nativeParamsStackOffset = 0;

        ArrayList<Register> regs = new ArrayList<>(allRegisters);
        regs.remove(r13);  // SP - never allocatable
        if (preserveFramePointer) {
            regs.remove(r11);  // FP
        }
        // Reserved by GraalVM SubstrateVM
        regs.remove(ReservedRegisters.singleton().getHeapBaseRegister());    // r8
        regs.remove(ReservedRegisters.singleton().getThreadRegister());      // r9
        regs.remove(ReservedRegisters.singleton().getCodeBaseRegister());
        regs.remove(r14);  // LR - return address, not allocatable
        allocatableRegs = List.copyOf(regs);

        switch (config) {
            case NORMAL:
                calleeSaveRegisters = List.of();
                break;

            case NATIVE_TO_JAVA:
                // AAPCS callee-saved: r4-r11, s16-s31
                calleeSaveRegisters = List.of(r4, r5, r6, r7, r8, r9, r10, r11);
                break;

            default:
                throw shouldNotReachHereUnexpectedInput(config);
        }

        attributesMap = RegisterAttributes.createMap(this, allRegisters);
    }

    @Override
    public Register getReturnRegister(JavaKind kind) {
        return switch (kind) {
            case Boolean, Byte, Char, Short, Int, Long, Object -> r0;
            case Float, Double -> s0;
            case Void -> null;
            default -> throw VMError.shouldNotReachHereUnexpectedInput(kind);
        };
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
        throw VMError.intentionallyUnimplemented();
    }

    private int javaStackParameterAssignment(ValueKindFactory<?> valueKindFactory, AllocatableValue[] locations, int index, JavaKind kind, int currentStackOffset, boolean isOutgoing) {
        // ARM32 Java: minimum 4-byte slot alignment
        ValueKind<?> valueKind = valueKindFactory.getValueKind(kind.getStackKind());
        int alignment = Math.max(valueKind.getPlatformKind().getSizeInBytes(), target.wordSize);
        locations[index] = StackSlot.get(valueKind, currentStackOffset, !isOutgoing);
        return currentStackOffset + alignment;
    }

    @Override
    public CallingConvention getCallingConvention(Type t, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
        SubstrateCallingConventionType type = (SubstrateCallingConventionType) t;
        boolean isEntryPoint = type.nativeABI() && !type.outgoing;

        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];
        JavaKind[] kinds = new JavaKind[locations.length];

        int firstActualArgument = 0;
        // One reserved slot for deoptimization (non-native calls)
        int currentStackOffset = (type.nativeABI() ? nativeParamsStackOffset : target.wordSize);

        if (!type.customABI()) {
            int currentInt = 0;
            int currentFP = 0;

            for (int i = firstActualArgument; i < parameterTypes.length; i++) {
                JavaKind kind = ObjectLayout.getCallSignatureKind(isEntryPoint, parameterTypes[i], metaAccess, target);
                kinds[i] = kind;

                Register register = null;
                if (type.kind == SubstrateCallingConventionKind.ForwardReturnValue) {
                    VMError.guarantee(i == 0, "ForwardReturnValue cannot have more than one parameter");
                    register = getReturnRegister(kind);
                } else {
                    switch (kind) {
                        case Byte, Boolean, Short, Char, Int, Long, Object:
                            if (currentInt < intParameterRegs.size()) {
                                register = intParameterRegs.get(currentInt++);
                            }
                            break;
                        case Float, Double:
                            if (currentFP < fpParameterRegs.size()) {
                                register = fpParameterRegs.get(currentFP++);
                            }
                            break;
                        default:
                            throw shouldNotReachHereUnexpectedInput(kind);
                    }
                }

                if (register != null) {
                    boolean useJavaKind = isEntryPoint && Platform.includedIn(Platform.LINUX.class);
                    locations[i] = register.asValue(valueKindFactory.getValueKind(useJavaKind ? kind : kind.getStackKind()));
                } else {
                    currentStackOffset = javaStackParameterAssignment(valueKindFactory, locations, i, kind, currentStackOffset, type.outgoing);
                }
            }
        } else {
            throw intentionallyUnimplemented();
        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : ObjectLayout.getCallSignatureKind(isEntryPoint, returnType, metaAccess, target);
        AllocatableValue returnLocation = Value.ILLEGAL;
        if (returnKind != JavaKind.Void) {
            ValueKind<?> returnValueKind = valueKindFactory.getValueKind(returnKind.getStackKind());
            returnLocation = getReturnRegister(returnKind).asValue(returnValueKind);
        }
        return new SubstrateCallingConvention(type, kinds, currentStackOffset, returnLocation, locations);
    }

    @Override
    public List<Register> filterAllocatableRegisters(PlatformKind kind, List<Register> registers) {
        throw intentionallyUnimplemented();
    }
}
