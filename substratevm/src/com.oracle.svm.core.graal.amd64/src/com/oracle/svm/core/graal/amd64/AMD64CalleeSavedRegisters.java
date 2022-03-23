/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.svm.core.deopt.DeoptimizationSupport;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.amd64.AMD64CPUFeatureAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.cpufeature.RuntimeCPUFeatureCheckImpl;
import com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.ResolvedJavaField;

final class AMD64CalleeSavedRegisters extends CalleeSavedRegisters {

    @Fold
    public static AMD64CalleeSavedRegisters singleton() {
        return (AMD64CalleeSavedRegisters) CalleeSavedRegisters.singleton();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void createAndRegister() {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        SubstrateRegisterConfig registerConfig = new SubstrateAMD64RegisterConfig(SubstrateRegisterConfig.ConfigKind.NORMAL, null, target, SubstrateOptions.PreserveFramePointer.getValue());

        Register frameRegister = registerConfig.getFrameRegister();
        List<Register> calleeSavedRegisters = new ArrayList<>(registerConfig.getAllocatableRegisters().asList());
        List<Register> calleeSavedXMMRegisters = new ArrayList<>();

        /*
         * Reverse list so that CPU registers are spilled close to the beginning of the frame, i.e.,
         * with a closer-to-0 negative reference map index in the caller frame. That makes the
         * reference map encoding of the caller frame a bit smaller.
         */
        Collections.reverse(calleeSavedRegisters);

        int offset = 0;
        Map<Register, Integer> calleeSavedRegisterOffsets = new HashMap<>();
        for (Register register : calleeSavedRegisters) {
            calleeSavedRegisterOffsets.put(register, offset);
            RegisterCategory category = register.getRegisterCategory();
            boolean isXMM = category.equals(AMD64.XMM);
            if (isXMM) {
                // XMM registers are handled separately
                calleeSavedXMMRegisters.add(register);
            }
            if (isXMM && needDynamicFeatureCheck()) {
                // we might need to safe the full 512 bit vector register
                offset += AMD64Kind.V512_QWORD.getSizeInBytes();
            } else {
                offset += target.arch.getLargestStorableKind(category).getSizeInBytes();
            }
        }
        int calleeSavedRegistersSizeInBytes = offset;

        int saveAreaOffsetInFrame = -(FrameAccess.returnAddressSize() +
                        (SubstrateOptions.PreserveFramePointer.getValue() ? FrameAccess.wordSize() : 0) +
                        calleeSavedRegistersSizeInBytes);

        ImageSingletons.add(CalleeSavedRegisters.class,
                        new AMD64CalleeSavedRegisters(frameRegister, calleeSavedRegisters, calleeSavedXMMRegisters, calleeSavedRegisterOffsets, calleeSavedRegistersSizeInBytes,
                                        saveAreaOffsetInFrame));
    }

    /**
     * Returns {@code true} if the size of {@link AMD64#XMM} registers is not known at compile time,
     * so dynamic CPU feature checks need to be emitted for storing the XMM register sizes.
     */
    private static boolean needDynamicFeatureCheck() {
        return AMD64CPUFeatureAccess.canUpdateCPUFeatures() && isRuntimeCompilationEnabled();
    }

    /**
     * Returns {@code true} if there is support for run-time compilation. We are (ab)using
     * {@link DeoptimizationSupport} for that purpose because {@code GraalSupport}, which would be
     * more appropriate, is not visible.
     */
    private static boolean isRuntimeCompilationEnabled() {
        return DeoptimizationSupport.enabled() && !SubstrateUtil.isBuildingLibgraal();
    }

    private final List<Register> calleeSavedXMMRegister;

    @Platforms(Platform.HOSTED_ONLY.class)
    private AMD64CalleeSavedRegisters(Register frameRegister, List<Register> calleeSavedRegisters, List<Register> calleeSavedXMMRegisters, Map<Register, Integer> offsetsInSaveArea, int saveAreaSize,
                    int saveAreaOffsetInFrame) {
        super(frameRegister, calleeSavedRegisters, offsetsInSaveArea, saveAreaSize, saveAreaOffsetInFrame);
        this.calleeSavedXMMRegister = calleeSavedXMMRegisters;
    }

    /**
     * The increasing different size and number of registers of SSE vs. AVX vs. AVX512 complicates
     * saving and restoring when AOT compilation and JIT compilation use different CPU features: A
     * JIT compiled caller might use AVX512 registers, but the AOT compiled callee is compiled to
     * use SSE registers only. To make this work, AOT compiled callees must dynamically check which
     * features are enabled at run time and store the appropriate register sizes. See
     * {@link AMD64CPUFeatureAccess#enableFeatures}.
     */
    public void emitSave(AMD64MacroAssembler asm, int frameSize, CompilationResultBuilder crb) {
        for (Register register : calleeSavedRegisters) {
            AMD64Address address = calleeSaveAddress(asm, frameSize, register);
            RegisterCategory category = register.getRegisterCategory();
            if (category.equals(AMD64.CPU)) {
                asm.movq(address, register);
            } else if (category.equals(AMD64.XMM)) {
                // handled later
                continue;
            } else if (category.equals(AMD64.MASK)) {
                /* Graal does not use the AVX512 mask registers yet. */
                throw VMError.unimplemented();
            } else {
                throw VMError.shouldNotReachHere();
            }
        }
        emitXMM(asm, crb, frameSize, Mode.SAVE);
    }

    public void emitRestore(AMD64MacroAssembler asm, int frameSize, Register excludedRegister, CompilationResultBuilder crb) {
        for (Register register : calleeSavedRegisters) {
            if (register.equals(excludedRegister)) {
                continue;
            }

            AMD64Address address = calleeSaveAddress(asm, frameSize, register);
            RegisterCategory category = register.getRegisterCategory();
            if (category.equals(AMD64.CPU)) {
                asm.movq(register, address);
            } else if (category.equals(AMD64.XMM)) {
                // handled later
                continue;
            } else if (category.equals(AMD64.MASK)) {
                throw VMError.unimplemented();
            } else {
                throw VMError.shouldNotReachHere();
            }
        }
        emitXMM(asm, crb, frameSize, Mode.RESTORE);
    }

    private void emitXMM(AMD64MacroAssembler asm, CompilationResultBuilder crb, int frameSize, Mode mode) {
        new XMMSaverRestorer(asm, crb, frameSize, mode).emit();
    }

    private AMD64Address calleeSaveAddress(AMD64MacroAssembler asm, int frameSize, Register register) {
        return asm.makeAddress(frameRegister, frameSize + getOffsetInFrame(register));
    }

    private enum Mode {
        SAVE,
        RESTORE
    }

    private final class XMMSaverRestorer {

        private final AMD64MacroAssembler asm;
        private final CompilationResultBuilder crb;
        private final int frameSize;
        private final Mode mode;

        XMMSaverRestorer(AMD64MacroAssembler asm, CompilationResultBuilder crb, int frameSize, Mode mode) {
            this.asm = asm;
            this.crb = crb;
            this.frameSize = frameSize;
            this.mode = mode;
        }

        public void emit() {
            if (needDynamicFeatureCheck()) {
                Label end = new Label();
                try {
                    // AVX512
                    if (emitConditionalSaveRestore(end, AMD64.CPUFeature.AVX512F)) {
                        // AVX512 statically available, no further checks needed
                        return;
                    }
                    // AVX
                    if (emitConditionalSaveRestore(end, AMD64.CPUFeature.AVX)) {
                        // AVX statically available, no further checks needed
                        return;
                    }
                    // SSE
                    emitSaveRestore(null);
                } finally {
                    asm.bind(end);
                }
            } else {
                var features = ((AMD64) ConfigurationValues.getTarget().arch).getFeatures();
                final AMD64.CPUFeature avxVersion;
                if (features.contains(AMD64.CPUFeature.AVX512F)) {
                    avxVersion = AMD64.CPUFeature.AVX512F;
                } else if (features.contains(AMD64.CPUFeature.AVX)) {
                    avxVersion = AMD64.CPUFeature.AVX;
                } else {
                    avxVersion = null;
                }
                emitSaveRestore(avxVersion);
            }
        }

        /**
         * @return {@code true} if the {@code avxVersion} is statically available (so no further
         *         checks are needed).
         */
        private boolean emitConditionalSaveRestore(Label end, AMD64.CPUFeature avxVersion) {
            var hostedCPUFeatures = ImageSingletons.lookup(CPUFeatureAccess.class).buildtimeCPUFeatures();
            if (hostedCPUFeatures.contains(avxVersion)) {
                emitSaveRestore(avxVersion);
                return true;
            }
            if (SubstrateUtil.HOSTED) {
                /*
                 * Only emit dynamic feature checks for compilations at image build time. For
                 * run-time compilations, use the features available at run time.
                 */
                Label avxVersionNotAvailable = emitRuntimeFeatureTest(avxVersion);
                asm.addFeatures(EnumSet.of(avxVersion));
                try {
                    emitSaveRestore(avxVersion);
                } finally {
                    asm.removeFeatures();
                }
                asm.jmp(end);
                asm.bind(avxVersionNotAvailable);
            }
            return false;
        }

        private void emitSaveRestore(AMD64.CPUFeature avxVersion) {
            for (Register register : calleeSavedXMMRegister) {
                AMD64Address address = calleeSaveAddress(asm, frameSize, register);
                RegisterCategory category = register.getRegisterCategory();
                assert category.equals(AMD64.XMM);
                if (AMD64.CPUFeature.AVX512F.equals(avxVersion)) {
                    if (mode == Mode.SAVE) {
                        asm.evmovdqu64(address, register);
                    } else {
                        asm.evmovdqu64(register, address);
                    }
                } else if (AMD64.CPUFeature.AVX.equals(avxVersion)) {
                    if (mode == Mode.SAVE) {
                        asm.vmovdqu(address, register);
                    } else {
                        asm.vmovdqu(register, address);
                    }
                } else {
                    if (mode == Mode.SAVE) {
                        asm.movdqu(address, register);
                    } else {
                        asm.movdqu(register, address);
                    }
                }
            }
        }

        /**
         * Emits the run time feature check.
         * 
         * @return a new {@link Label} to continue execution of the {@code feature} is <em>not</em>
         *         available.
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        private Label emitRuntimeFeatureTest(AMD64.CPUFeature feature) {
            AMD64Address address = getFeatureMapAddress();
            int mask = RuntimeCPUFeatureCheckImpl.instance().computeFeatureMask(EnumSet.of(feature));
            GraalError.guarantee(mask != 0, "Mask must not be 0 for features %s", feature);
            Label label = new Label();
            asm.testAndJcc(getSize(), address, mask, AMD64Assembler.ConditionFlag.NotEqual, label, false, null);
            return label;
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        private AMD64BaseAssembler.OperandSize getSize() {
            Class<?> fieldType = RuntimeCPUFeatureCheckImpl.getMaskField().getType();
            Class<?> expectedType = int.class;
            GraalError.guarantee(expectedType.equals(fieldType), "Expected %s field, got %s", expectedType, fieldType);
            return DWORD;
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        private AMD64Address getFeatureMapAddress() {
            SubstrateObjectConstant object = (SubstrateObjectConstant) SubstrateObjectConstant.forObject(RuntimeCPUFeatureCheckImpl.instance());
            int fieldOffset = fieldOffset(RuntimeCPUFeatureCheckImpl.getMaskField(crb.providers.getMetaAccess()));
            GraalError.guarantee(ConfigurationValues.getTarget().inlineObjects, "Dynamic feature check for callee saved registers requires inlined objects");
            Register heapBase = ReservedRegisters.singleton().getHeapBaseRegister();
            GraalError.guarantee(heapBase != null, "Heap base register must not be null");
            return new AMD64Address(heapBase, Register.None, AMD64Address.Scale.Times1, displacement(object, (SharedConstantReflectionProvider) crb.providers.getConstantReflection()) + fieldOffset,
                            displacementAnnotation(object));
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        private int fieldOffset(ResolvedJavaField f) {
            SharedField field = (SharedField) f;
            return field.isAccessed() ? field.getLocation() : -1;
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        private Object displacementAnnotation(SubstrateObjectConstant constant) {
            if (SubstrateUtil.HOSTED) {
                /*
                 * AOT compilation during image generation happens before the image heap objects are
                 * layouted. So the offset of the constant is not known yet during compilation time,
                 * and instead needs to be patched in later. We annotate the machine code with the
                 * constant that needs to be patched in.
                 */
                return constant;
            } else {
                return null;
            }
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        private int displacement(SubstrateObjectConstant constant, SharedConstantReflectionProvider constantReflection) {
            if (SubstrateUtil.HOSTED) {
                return 0;
            } else {
                /*
                 * For JIT compilation at run time, the image heap is known and immutable, so the
                 * offset of the constant can be emitted immediately. No patching is required later
                 * on.
                 */
                return constantReflection.getImageHeapOffset(constant);
            }
        }
    }

    @Override
    public void dumpRegisters(Log log, Pointer callerSP, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        log.string("Callee saved registers (sp=").zhex(callerSP).string(")").indent(true);
        /*
         * The loop to print all registers is manually unrolled so that the register order is
         * defined, and also so that the lookup of the "offset in frame" can be constant folded at
         * image build time using a @Fold method.
         */
        dumpReg(log, "RAX ", callerSP, offsetInFrameOrNull(AMD64.rax), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBX ", callerSP, offsetInFrameOrNull(AMD64.rbx), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RCX ", callerSP, offsetInFrameOrNull(AMD64.rcx), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDX ", callerSP, offsetInFrameOrNull(AMD64.rdx), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBP ", callerSP, offsetInFrameOrNull(AMD64.rbp), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSI ", callerSP, offsetInFrameOrNull(AMD64.rsi), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDI ", callerSP, offsetInFrameOrNull(AMD64.rdi), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSP ", callerSP, offsetInFrameOrNull(AMD64.rsp), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R8  ", callerSP, offsetInFrameOrNull(AMD64.r8), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R9  ", callerSP, offsetInFrameOrNull(AMD64.r9), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R10 ", callerSP, offsetInFrameOrNull(AMD64.r10), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R11 ", callerSP, offsetInFrameOrNull(AMD64.r11), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R12 ", callerSP, offsetInFrameOrNull(AMD64.r12), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R13 ", callerSP, offsetInFrameOrNull(AMD64.r13), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R14 ", callerSP, offsetInFrameOrNull(AMD64.r14), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R15 ", callerSP, offsetInFrameOrNull(AMD64.r15), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        log.indent(false);
    }

    private static void dumpReg(Log log, String label, Pointer callerSP, int offsetInFrameOrNull, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        if (offsetInFrameOrNull != 0) {
            long value = callerSP.readLong(offsetInFrameOrNull);
            RegisterDumper.dumpReg(log, label, value, printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        }
    }

    @Fold
    static int offsetInFrameOrNull(Register register) {
        AMD64CalleeSavedRegisters that = AMD64CalleeSavedRegisters.singleton();
        if (that.calleeSavedRegisters.contains(register)) {
            return that.getOffsetInFrame(register);
        } else {
            return 0;
        }
    }
}
