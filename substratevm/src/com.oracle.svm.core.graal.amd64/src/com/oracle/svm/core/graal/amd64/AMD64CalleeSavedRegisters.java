/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.amd64.AMD64CPUFeatureAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.cpufeature.RuntimeCPUFeatureCheckImpl;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MIOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
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
        List<Register> calleeSavedRegisters = new ArrayList<>(registerConfig.getAllocatableRegisters());
        List<Register> calleeSavedXMMRegisters = new ArrayList<>();
        List<Register> calleeSavedMaskRegisters = new ArrayList<>();

        boolean isRuntimeCompilationEnabled = RuntimeCompilation.isEnabled();

        // The CFI target register cannot be saved across calls
        if (SubstrateControlFlowIntegrity.useSoftwareCFI()) {
            calleeSavedRegisters.remove(SubstrateControlFlowIntegrity.singleton().getCFITargetRegister());
        }

        /*
         * Handling of the rbp register is always done in the method prologue, so we remove it here
         * and record whether it is callee saved.
         */
        boolean rbpCalleeSaved = calleeSavedRegisters.remove(AMD64.rbp);

        /*
         * Reverse list so that CPU registers are spilled close to the beginning of the frame, i.e.,
         * with a closer-to-0 negative reference map index in the caller frame. That makes the
         * reference map encoding of the caller frame a bit smaller.
         */
        Collections.reverse(calleeSavedRegisters);

        int offset = 0;
        Map<Register, Integer> calleeSavedRegisterOffsets = new HashMap<>();
        for (Register register : calleeSavedRegisters) {
            int reservedSize = 0;
            RegisterCategory category = register.getRegisterCategory();
            boolean isXMM = category.equals(AMD64.XMM);
            if (isXMM) {
                // XMM registers are handled separately
                calleeSavedXMMRegisters.add(register);
            }
            boolean isMask = category.equals(AMD64.MASK);
            if (isMask) {
                // Mask registers are handled separately
                calleeSavedMaskRegisters.add(register);
            }
            if (isXMM && isRuntimeCompilationEnabled && AMD64CPUFeatureAccess.canUpdateCPUFeatures()) {
                // we might need to save the full 512 bit vector register
                reservedSize = AMD64Kind.V512_QWORD.getSizeInBytes();
            } else if (isMask && isRuntimeCompilationEnabled && AMD64CPUFeatureAccess.canUpdateCPUFeatures()) {
                // we might need to save the full 64 bit mask register
                reservedSize = AMD64Kind.MASK64.getSizeInBytes();
            } else if (target.arch.getLargestStorableKind(category) != null) {
                reservedSize = target.arch.getLargestStorableKind(category).getSizeInBytes();
            } else {
                // Mask registers are not present without AVX512
                VMError.guarantee(
                                isMask && !target.arch.getFeatures().contains(CPUFeature.AVX512F) &&
                                                !(isRuntimeCompilationEnabled && AMD64CPUFeatureAccess.canUpdateCPUFeatures()),
                                "unexpected register without largest storable kind: %s", register);
            }
            /*
             * Make sure this register's offset is aligned to its size. When using only AVX512F, its
             * 16-bit mask registers would otherwise mess up the alignment for larger registers.
             */
            if (reservedSize > 0) {
                offset = NumUtil.roundUp(offset, reservedSize);
            }
            calleeSavedRegisterOffsets.put(register, offset);
            offset += reservedSize;
        }
        int calleeSavedRegistersSizeInBytes = offset;

        int saveAreaOffsetInFrame = -(FrameAccess.returnAddressSize() +
                        FrameAccess.wordSize() + /* Space is always reserved for rbp. */
                        calleeSavedRegistersSizeInBytes);

        if (rbpCalleeSaved) {
            /*
             * When rbp is callee saved, it is pushed onto the stack in the method prologue right
             * after the return address, i.e., at the top of the callee save area, so we are just
             * referencing that location here.
             */
            calleeSavedRegisterOffsets.put(AMD64.rbp, calleeSavedRegistersSizeInBytes);
        }

        ImageSingletons.add(CalleeSavedRegisters.class,
                        new AMD64CalleeSavedRegisters(frameRegister, calleeSavedRegisters, calleeSavedXMMRegisters, calleeSavedMaskRegisters, calleeSavedRegisterOffsets,
                                        calleeSavedRegistersSizeInBytes, saveAreaOffsetInFrame, isRuntimeCompilationEnabled));
    }

    private final List<Register> calleeSavedXMMRegisters;
    private final List<Register> calleeSavedMaskRegisters;
    private final boolean isRuntimeCompilationEnabled;

    @Platforms(Platform.HOSTED_ONLY.class)
    private AMD64CalleeSavedRegisters(Register frameRegister, List<Register> calleeSavedRegisters, List<Register> calleeSavedXMMRegisters, List<Register> calleeSavedMaskRegisters,
                    Map<Register, Integer> offsetsInSaveArea, int saveAreaSize, int saveAreaOffsetInFrame, boolean isRuntimeCompilationEnabled) {
        super(frameRegister, calleeSavedRegisters, offsetsInSaveArea, saveAreaSize, saveAreaOffsetInFrame);
        this.calleeSavedXMMRegisters = calleeSavedXMMRegisters;
        this.calleeSavedMaskRegisters = calleeSavedMaskRegisters;
        this.isRuntimeCompilationEnabled = isRuntimeCompilationEnabled;
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
        if (!SubstrateUtil.HOSTED) {
            GraalError.shouldNotReachHere("hosted mode expected"); // ExcludeFromJacocoGeneratedReport
            return;
        }
        for (Register register : calleeSavedRegisters) {
            AMD64Address address = calleeSaveAddress(asm, frameSize, register);
            RegisterCategory category = register.getRegisterCategory();
            if (category.equals(AMD64.CPU)) {
                asm.movq(address, register);
            } else if (category.equals(AMD64.XMM)) {
                // handled later by emitXMM
                continue;
            } else if (category.equals(AMD64.MASK)) {
                // handled later by emitXMM
                continue;
            } else {
                throw VMError.shouldNotReachHereUnexpectedInput(category); // ExcludeFromJacocoGeneratedReport
            }
        }
        emitXMM(asm, crb, frameSize, Mode.SAVE);
    }

    public void emitRestore(AMD64MacroAssembler asm, int frameSize, Register excludedRegister, CompilationResultBuilder crb) {
        if (!SubstrateUtil.HOSTED) {
            GraalError.shouldNotReachHere("hosted mode expected"); // ExcludeFromJacocoGeneratedReport
            return;
        }
        for (Register register : calleeSavedRegisters) {
            if (register.equals(excludedRegister)) {
                continue;
            }

            AMD64Address address = calleeSaveAddress(asm, frameSize, register);
            RegisterCategory category = register.getRegisterCategory();
            if (category.equals(AMD64.CPU)) {
                asm.movq(register, address);
            } else if (category.equals(AMD64.XMM)) {
                // handled later by emitXMM
                continue;
            } else if (category.equals(AMD64.MASK)) {
                // handled later by emitXMM
                continue;
            } else {
                throw VMError.shouldNotReachHereUnexpectedInput(category); // ExcludeFromJacocoGeneratedReport
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

    /**
     * "Control-flow exception" to indicate that a CPU feature is statically available and no
     * further dynamic feature checks are needed.
     */
    private static final class StaticFeatureException extends Exception {
        static final long serialVersionUID = -1;
    }

    private final class XMMSaverRestorer {

        private final AMD64MacroAssembler asm;
        private final CompilationResultBuilder crb;
        private final int frameSize;
        private final Mode mode;
        private final EnumSet<?> hostedCPUFeatures;

        XMMSaverRestorer(AMD64MacroAssembler asm, CompilationResultBuilder crb, int frameSize, Mode mode) {
            this.asm = asm;
            this.crb = crb;
            this.frameSize = frameSize;
            this.mode = mode;
            this.hostedCPUFeatures = ImageSingletons.lookup(CPUFeatureAccess.class).buildtimeCPUFeatures();
        }

        @SuppressWarnings("unlikely-arg-type")
        public void emit() {
            assert isRuntimeCompilationEnabled == RuntimeCompilation.isEnabled() : "JIT compilation enabled after registering singleton?";
            if (isRuntimeCompilationEnabled && AMD64CPUFeatureAccess.canUpdateCPUFeatures()) {
                // JIT compilation is enabled -> need dynamic checks
                Label end = new Label();
                try {
                    // Checkstyle: stop AvoidNestedBlocks
                    Label noAVX512BW = new Label();
                    Label noAVX512 = new Label();
                    Label avx512SaveZMM = new Label();
                    testFeature(CPUFeature.AVX512F, noAVX512);
                    {   // if AVX512:
                        enterAvxRegion(CPUFeature.AVX512F);
                        testFeature(CPUFeature.AVX512BW, noAVX512BW);
                        {   // if AVX512BW:
                            enterAvxRegion(CPUFeature.AVX512BW);
                            kmovq();
                            leaveAvxRegion(CPUFeature.AVX512BW);
                            asm.jmp(avx512SaveZMM);
                        }
                        {   // else:
                            asm.bind(noAVX512BW);
                            kmovw();
                            // fall through
                        }
                        asm.bind(avx512SaveZMM);
                        evmovdqu64();
                        leaveAvxRegion(CPUFeature.AVX512F);
                    }
                    jumpToEndOrReturn(CPUFeature.AVX512F, end);
                    asm.bind(noAVX512);
                    Label noAVX = new Label();
                    testFeature(CPUFeature.AVX, noAVX);
                    {   // else if AVX:
                        enterAvxRegion(CPUFeature.AVX);
                        vmovdqu();
                        leaveAvxRegion(CPUFeature.AVX);
                    }
                    jumpToEndOrReturn(CPUFeature.AVX, end);
                    asm.bind(noAVX);
                    // else: SSE
                    movdqu();
                    // Checkstyle: resume AvoidNestedBlocks
                } catch (StaticFeatureException staticFeature) {
                    // feature is statically available -> done
                } finally {
                    asm.bind(end);
                }
            } else {
                // no JIT compilation is enabled -> static checks only
                if (hostedCPUFeatures.contains(CPUFeature.AVX512F)) {
                    if (hostedCPUFeatures.contains(CPUFeature.AVX512BW)) {
                        kmovq();
                    } else {
                        kmovw();
                    }
                    evmovdqu64();
                } else if (hostedCPUFeatures.contains(CPUFeature.AVX)) {
                    vmovdqu();
                } else {
                    movdqu();
                }
            }
        }

        /**
         * Emits a {@linkplain #emitRuntimeFeatureTest runtime feature check} if the {@code feature}
         * is not statically available.
         */
        @SuppressWarnings("unlikely-arg-type")
        private void testFeature(CPUFeature feature, Label falseLabel) {
            if (!hostedCPUFeatures.contains(feature)) {
                emitRuntimeFeatureTest(feature, falseLabel);
            }
        }

        /**
         * Emits a jump to {@code end}, or throws an {@link StaticFeatureException} if the
         * {@code feature} is statically available. If a CPU feature region for the {@code feature}
         * was {@linkplain #enterAvxRegion(CPUFeature) entered}, it must be
         * {@linkplain #leaveAvxRegion(CPUFeature) left} before calling this method.
         */
        @SuppressWarnings("unlikely-arg-type")
        private void jumpToEndOrReturn(CPUFeature feature, Label end) throws StaticFeatureException {
            if (hostedCPUFeatures.contains(feature)) {
                throw new StaticFeatureException();
            }
            asm.jmp(end);
        }

        private void enterAvxRegion(CPUFeature avxFeature) {
            /*
             * Do we need vzeroupper? We need it if the assembler doesn't support AVX at all before
             * entering the region (neither statically nor through any currently open CPU feature
             * regions). Contrast with the way a similar condition is checked in leaveAvxRegion.
             */
            asm.addFeatures(EnumSet.of(CPUFeature.AVX, avxFeature));
            boolean isAvxSseTransition = asm.isCurrentRegionFeature(CPUFeature.AVX);
            if (isAvxSseTransition && mode == Mode.RESTORE) {
                /*
                 * We are about to restore AVX registers before returning from SSE-only code. Need
                 * vzeroupper.
                 */
                asm.vzeroupper();
            }
        }

        private void leaveAvxRegion(CPUFeature avxFeature) {
            GraalError.guarantee(asm.supports(avxFeature), "trying to leave region for unset feature %s", avxFeature);
            /*
             * Do we need vzeroupper? We need it if after leaving the current CPU feature region the
             * assembler will not support AVX at all (neither statically nor through any open CPU
             * feature regions). This is the case if the current (i.e., topmost) CPU feature region
             * explicitly contains the AVX flag. Contrast with the way a similar condition is
             * checked in enterAvxRegion.
             */
            boolean isAvxSseTransition = asm.isCurrentRegionFeature(CPUFeature.AVX);
            if (isAvxSseTransition && mode == Mode.SAVE) {
                /*
                 * We have saved AVX registers and are about to enter SSE-only code. Need
                 * vzeroupper.
                 */
                asm.vzeroupper();
            }
            asm.removeFeatures();
        }

        private void emitSaveRestore(List<Register> registers, RegisterCategory requiredCategory, BiConsumer<AMD64Address, Register> saveInstruction,
                        BiConsumer<Register, AMD64Address> restoreInstruction) {
            for (Register register : registers) {
                AMD64Address address = calleeSaveAddress(asm, frameSize, register);
                RegisterCategory category = register.getRegisterCategory();
                assert category.equals(requiredCategory);
                if (mode == Mode.SAVE) {
                    saveInstruction.accept(address, register);
                } else {
                    restoreInstruction.accept(register, address);
                }
            }
        }

        private void kmovq() {
            emitSaveRestore(calleeSavedMaskRegisters, AMD64.MASK, asm::kmovq, asm::kmovq);
        }

        private void kmovw() {
            emitSaveRestore(calleeSavedMaskRegisters, AMD64.MASK, asm::kmovw, asm::kmovw);
        }

        private void evmovdqu64() {
            emitSaveRestore(calleeSavedXMMRegisters, AMD64.XMM, asm::evmovdqu64, asm::evmovdqu64);
        }

        private void vmovdqu() {
            emitSaveRestore(calleeSavedXMMRegisters, AMD64.XMM, asm::vmovdqu, asm::vmovdqu);
        }

        private void movdqu() {
            emitSaveRestore(calleeSavedXMMRegisters, AMD64.XMM, asm::movdqu, asm::movdqu);
        }

        /**
         * Emits the run time feature check.
         *
         * @param falseLabel a {@link Label} to continue execution of the {@code feature} is
         *            <em>not</em> available.
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        private Label emitRuntimeFeatureTest(CPUFeature feature, Label falseLabel) {
            AMD64Address address = getFeatureMapAddress();
            int mask = RuntimeCPUFeatureCheckImpl.instance().computeFeatureMask(EnumSet.of(feature));
            GraalError.guarantee(mask != 0, "Mask must not be 0 for features %s", feature);
            Class<?> fieldType = RuntimeCPUFeatureCheckImpl.getMaskField().getType();
            GraalError.guarantee(int.class.equals(fieldType), "Expected int field, got %s", fieldType);
            AMD64MIOp.TEST.emit(asm, DWORD, address, mask, false);
            asm.jcc(AMD64Assembler.ConditionFlag.NotEqual, falseLabel);
            return falseLabel;
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        private AMD64Address getFeatureMapAddress() {
            JavaConstant object = crb.getSnippetReflection().forObject(RuntimeCPUFeatureCheckImpl.instance());
            int fieldOffset = fieldOffset(RuntimeCPUFeatureCheckImpl.getMaskField(crb.getMetaAccess()));
            GraalError.guarantee(ConfigurationValues.getTarget().inlineObjects, "Dynamic feature check for callee saved registers requires inlined objects");
            Register heapBase = ReservedRegisters.singleton().getHeapBaseRegister();
            GraalError.guarantee(heapBase != null, "Heap base register must not be null");
            return new AMD64Address(heapBase, Register.None, Stride.S1, displacement(object, crb.getConstantReflection()) + fieldOffset,
                            displacementAnnotation(object));
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        private int fieldOffset(ResolvedJavaField f) {
            SharedField field = (SharedField) f;
            GraalError.guarantee(field.isAccessed(), "Field not accessed %s", f);
            return field.getLocation();
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        private Object displacementAnnotation(JavaConstant constant) {
            if (SubstrateUtil.HOSTED) {
                /*
                 * AOT compilation during image generation happens before the image heap objects are
                 * laid out. So the offset of the constant is not known yet during compilation time,
                 * and instead needs to be patched in later. We annotate the machine code with the
                 * constant that needs to be patched in.
                 */
                return constant;
            } else {
                return null;
            }
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        private int displacement(JavaConstant constant, ConstantReflectionProvider constantReflection) {
            if (SubstrateUtil.HOSTED) {
                return 0;
            } else {
                /*
                 * For JIT compilation at run time, the image heap is known and immutable, so the
                 * offset of the constant can be emitted immediately. No patching is required later
                 * on.
                 */
                return ((SharedConstantReflectionProvider) constantReflection).getImageHeapOffset(constant);
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
        if (that.calleeSaveable(register)) {
            return that.getOffsetInFrame(register);
        } else {
            return 0;
        }
    }
}
