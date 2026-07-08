/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.graal.aarch64.SubstrateAArch64RegisterConfig.fp;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createBaseRegisterOnlyAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.v0;

import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.struct.OffsetOf;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.deopt.DeoptimizationSlotPacking;
import com.oracle.svm.core.graal.code.InterpreterAccessStubData;
import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.core.graal.meta.DynamicHubOffsets;
import com.oracle.svm.core.graal.meta.InterpreterExecutionOffsets;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.interpreter.InterpreterEnterStub;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.NumUtil;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.AnnotationUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.aarch64.AArch64Move;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.JavaKind;

public class AArch64InterpreterStubs {

    public static class InterpreterEnterStubContext extends SubstrateAArch64Backend.SubstrateAArch64FrameContext {
        private final boolean emitDirectFastPath;
        private final boolean emitVTableFastPath;

        public InterpreterEnterStubContext(SharedMethod method) {
            super(method);
            boolean useRistretto = SubstrateOptions.useRistretto();
            InterpreterEnterStub stubType = AnnotationUtil.getAnnotation(method, InterpreterEnterStub.class);
            assert stubType != null : "Missing @InterpreterEnterStub annotation on interpreter enter stub.";
            assert !useRistretto || ImageSingletons.contains(InterpreterExecutionOffsets.class) : "Missing InterpreterExecutionOffsets singleton while Ristretto is enabled.";
            emitDirectFastPath = useRistretto && stubType.value() == InterpreterEnterStub.Kind.DIRECT;
            emitVTableFastPath = useRistretto && stubType.value() == InterpreterEnterStub.Kind.VTABLE;
        }

        private static AArch64Address createImmediate(int offset) {
            int deoptSlotSize = 8 + 8 /* padding */;
            return createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, deoptSlotSize + offset);
        }

        private static void materializeFieldAddress(AArch64MacroAssembler masm, Register base, int offset, boolean compressedBase, int compressionShift, Register addressScratch) {
            if (compressedBase) {
                // Decode the compressed heap reference into a full address before the load.
                masm.add(64, addressScratch, ReservedRegisters.singleton().getHeapBaseRegister(), base, AArch64Assembler.ShiftType.LSL, compressionShift);
            } else if (!addressScratch.equals(base)) {
                masm.mov(64, addressScratch, base);
            }
            if (offset != 0) {
                masm.add(64, addressScratch, addressScratch, offset);
            }
        }

        private static void loadObjectObjectField(AArch64MacroAssembler masm, Register dst, Register obj, int offset, boolean compressedBase, int compressionShift, Register addressScratch) {
            materializeFieldAddress(masm, obj, offset, compressedBase, compressionShift, addressScratch);
            int refSize = ObjectLayout.singleton().getReferenceSize();
            masm.ldr(refSize * Byte.SIZE, dst, createBaseRegisterOnlyAddress(refSize * Byte.SIZE, addressScratch));
        }

        private static void loadObjectObjectFieldAcquire(AArch64MacroAssembler masm, Register dst, Register obj, int offset, boolean compressedBase, int compressionShift, Register addressScratch) {
            materializeFieldAddress(masm, obj, offset, compressedBase, compressionShift, addressScratch);
            masm.ldar(ObjectLayout.singleton().getReferenceSize() * Byte.SIZE, dst, addressScratch);
        }

        private static void loadObjectWordField(AArch64MacroAssembler masm, Register dst, Register obj, int offset, boolean compressedBase, int compressionShift, Register addressScratch) {
            materializeFieldAddress(masm, obj, offset, compressedBase, compressionShift, addressScratch);
            masm.ldr(64, dst, createBaseRegisterOnlyAddress(64, addressScratch));
        }

        /**
         * See {@code SubstrateBasicLoweringProvider#createReadHub}.
         */
        private static void loadHub(AArch64MacroAssembler masm, Register obj, Register hub, Register scratch) {
            ObjectLayout ol = ObjectLayout.singleton();
            long reservedHubBitsMask = Heap.getHeap().getObjectHeader().getReservedHubBitsMask();
            int compressionShift = ReferenceAccess.singleton().getCompressionShift();
            int alignmentBits = CodeUtil.log2(ol.getAlignment());

            masm.ldr(ol.getHubSize() * Byte.SIZE, hub, masm.makeAddress(ol.getHubSize() * Byte.SIZE, obj, ol.getHubOffset(), scratch));

            if (reservedHubBitsMask != 0) {
                int reservedHubBits = CodeUtil.log2(reservedHubBitsMask + 1L);
                if (reservedHubBits == alignmentBits && compressionShift == 0) {
                    masm.movNativeAddress(scratch, ~reservedHubBitsMask);
                    // Clear the reserved low hub-tag bits in the decoded hub word.
                    masm.and(ol.getHubSize() * Byte.SIZE, hub, hub, scratch);
                } else {
                    // Remove reserved tag bits and reconstruct the aligned heap address.
                    masm.lsr(64, hub, hub, reservedHubBits);
                    if (compressionShift != alignmentBits) {
                        /*
                         * Keep hub as a compressed reference; the remaining shift restores the
                         * alignment bits discarded by hub tagging without fully decoding the
                         * reference to a heap address.
                         */
                        masm.lsl(64, hub, hub, alignmentBits - compressionShift);
                    }
                }
            }
            if (ol.getReferenceSize() == Integer.BYTES) {
                // Zero-extend the decoded 32-bit reference to a canonical 64-bit pointer value.
                masm.mov(32, hub, hub);
            }
        }

        private static void goSlowPathIfNull(AArch64MacroAssembler masm, Register value, Label slowPath) {
            masm.cbz(64, value, slowPath);
        }

        private static void goSlowPathIfIndexOutOfBounds(AArch64MacroAssembler masm, Register array, Register index, Register scratch, int arrayLengthOffset, Label slowPath) {
            masm.ldr(32, scratch, masm.makeAddress(32, array, arrayLengthOffset));
            /*
             * HS in the unsigned compare covers both index >= array.length and negative Java
             * indices.
             */
            masm.cmp(32, index, scratch);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.HS, slowPath);
        }

        /**
         * Fast path for {@code InterpreterStubSection.enterDirectInterpreterStub(...)}, or for the
         * method resolved by {@code InterpreterStubSection.enterVTableInterpreterStub(...)}. If the
         * target method already has valid installed code, jump to its entry point instead of
         * entering the interpreter.
         */
        private static void emitInstalledCodeFastPath(AArch64MacroAssembler masm, Register interpreterMethod, boolean compressedInterpreterMethod, Register scratch) {
            Label slowPath = new Label();

            boolean compressedReferences = ReferenceAccess.singleton().haveCompressedReferences();
            int compressionShift = ReferenceAccess.singleton().getCompressionShift();
            InterpreterExecutionOffsets executionOffsets = InterpreterExecutionOffsets.singleton();

            /*
             * Keep this sanity check for now so that, if we ever end up with a null method here, we
             * fall back to the slow path instead of crashing in the fast path. The slow path also
             * performs a sanity check, so this guard should be removable in the future.
             */
            goSlowPathIfNull(masm, interpreterMethod, slowPath);

            // Load volatile field with acquire semantics.
            loadObjectObjectFieldAcquire(masm, scratch, interpreterMethod, executionOffsets.getInterpreterResolvedJavaMethodRistrettoMethodOffset(), compressedInterpreterMethod, compressionShift,
                            scratch);
            goSlowPathIfNull(masm, scratch, slowPath);

            // Load volatile field with acquire semantics.
            loadObjectObjectFieldAcquire(masm, scratch, scratch, executionOffsets.getRistrettoMethodInstalledCodeOffset(), compressedReferences, compressionShift, scratch);
            goSlowPathIfNull(masm, scratch, slowPath);

            loadObjectWordField(masm, scratch, scratch, executionOffsets.getInstalledCodeEntryPointOffset(), compressedReferences, compressionShift, scratch);
            goSlowPathIfNull(masm, scratch, slowPath);
            // Jump to the entry point of the method.
            masm.jmp(scratch);

            masm.bind(slowPath);
        }

        /**
         * Fast path for resolving the target of
         * {@code InterpreterStubSection.enterVTableInterpreterStub(...)}. This performs the same
         * high-level lookup as the Java helper, then tries the installed-code jump before falling
         * back to the slow path.
         */
        private static void emitVTableInstalledCodeFastPath(AArch64MacroAssembler masm, Register receiver, Register vtableIndex, Register scratch1, Register scratch2) {
            ObjectLayout ol = ObjectLayout.singleton();
            boolean compression = ReferenceAccess.singleton().haveCompressedReferences();
            int compressionShift = ReferenceAccess.singleton().getCompressionShift();

            DynamicHubOffsets hubOffsets = DynamicHubOffsets.singleton();
            InterpreterExecutionOffsets executionOffsets = InterpreterExecutionOffsets.singleton();
            Label slowPath = new Label();

            loadHub(masm, receiver, scratch1, scratch2);

            // Extract the companion.
            loadObjectObjectField(masm, scratch1, scratch1, hubOffsets.getCompanionOffset(), compression, compressionShift, scratch2);
            // Extract the interpreter type of the companion.
            loadObjectObjectField(masm, scratch1, scratch1, executionOffsets.getDynamicHubCompanionInterpreterTypeOffset(), compression, compressionShift, scratch2);
            // Extract the vtable holder of the interpreter type.
            loadObjectObjectField(masm, scratch1, scratch1, executionOffsets.getInterpreterResolvedObjectTypeVtableHolderOffset(), compression, compressionShift, scratch2);
            // Extract the vtable.
            loadObjectObjectField(masm, scratch1, scratch1, executionOffsets.getVtableHolderVtableOffset(), compression, compressionShift, scratch2);

            if (compression) {
                AArch64Move.UncompressPointerOp.emitUncompressCode(masm, scratch1, scratch1, ReferenceAccess.singleton().getCompressEncoding(), true,
                                ReservedRegisters.singleton().getHeapBaseRegister(), true);
            }

            goSlowPathIfIndexOutOfBounds(masm, scratch1, vtableIndex, scratch2, ol.getArrayLengthOffset(), slowPath);

            Register method = scratch2;
            masm.add(64, scratch1, scratch1, ol.getArrayBaseOffset(JavaKind.Object));
            /*
             * Extract the method from the vtable. Address = vtable base + zero-extended 32-bit
             * index * referenceSize + array header.
             */
            int referenceSize = ol.getReferenceSize();
            masm.ldr(referenceSize * Byte.SIZE, method,
                            AArch64Address.createExtendedRegisterOffsetAddress(referenceSize * Byte.SIZE, scratch1, vtableIndex, true, AArch64Assembler.ExtendType.UXTW));

            emitInstalledCodeFastPath(masm, method, compression, scratch1);
            masm.bind(slowPath);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            SubstrateAArch64RegisterConfig registerConfig = ((SubstrateAArch64RegisterConfig) crb.frameMap.getRegisterConfig());
            List<Register> gps = registerConfig.getJavaGeneralParameterRegs();
            List<Register> fps = registerConfig.getFloatingPointParameterRegs();

            /*
             * Ristretto currently makes this frame context reachable during analysis. Explicitly
             * avoid the singleton lookup in that case until GR-55022 is fixed.
             */
            if (SubstrateUtil.HOSTED) {
                try (AArch64MacroAssembler.ScratchRegister scratch1 = masm.getScratchRegister()) {
                    Register fastPathScratch1 = scratch1.getRegister();
                    if (emitVTableFastPath) {
                        try (AArch64MacroAssembler.ScratchRegister scratch2 = masm.getScratchRegister()) {
                            emitVTableInstalledCodeFastPath(masm, r0, SubstrateAArch64Backend.HIDDEN_ARGUMENT_REGISTER, fastPathScratch1, scratch2.getRegister());
                        }
                    } else if (emitDirectFastPath) {
                        emitInstalledCodeFastPath(masm, SubstrateAArch64Backend.HIDDEN_ARGUMENT_REGISTER, false, fastPathScratch1);
                    }
                }
            }

            Register trampArg = SubstrateAArch64Backend.HIDDEN_ARGUMENT_REGISTER;
            Register spCopy = AArch64.r11;

            masm.mov(64, spCopy, sp);

            super.enter(crb);

            /* sp points to InterpreterData struct */
            masm.str(64, spCopy, createImmediate(offsetAbiSpReg()));

            for (int i = 0; i < gps.size(); i++) {
                masm.str(64, gps.get(i), createImmediate(offsetAbiGpArg(i)));
            }

            for (int i = 0; i < fps.size(); i++) {
                masm.fstr(64, fps.get(i), createImmediate(offsetAbiFpArg(i)));
            }

            /* sp+deoptSlotSize points to InterpreterData struct */
            masm.add(64, r1, sp, 16 /* deoptSlotSize */);

            /* Pass the interpreter method index as first arg */
            masm.mov(64, r0, trampArg);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;

            /* r0 contains the raw result. Make it available in both ABI return registers. */
            masm.fmov(64, v0, r0);

            super.leave(crb);
        }
    }

    public static class InterpreterLeaveStubContext extends SubstrateAArch64Backend.SubstrateAArch64FrameContext {

        public InterpreterLeaveStubContext(SharedMethod method) {
            super(method);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            super.enter(crb);
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;

            /* sp points to a reserved stack slot for this stub */

            /* arg3: true if the result of the function is in a floating-point register */
            masm.str(64, r3, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, 0));

            masm.sub(64, sp, sp, r2 /* variable stack size */);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            SubstrateAArch64RegisterConfig registerConfig = ((SubstrateAArch64RegisterConfig) crb.frameMap.getRegisterConfig());
            List<Register> gps = registerConfig.getJavaGeneralParameterRegs();
            List<Register> fps = registerConfig.getFloatingPointParameterRegs();
            Register callTarget = AArch64.r11;

            /* Save call target */
            masm.mov(64, callTarget, r0);

            /* Get pointer to InterpreterData struct */
            masm.mov(64, r0, r1);

            Register stackSize = AArch64.r2;

            /* Copy prepared outgoing args to the stack where the ABI expects it */
            Register calleeSpArgs = AArch64.r4;
            Register interpDataSp = AArch64.r1;
            masm.ldr(64, interpDataSp, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, r0, offsetAbiSpReg()));
            masm.mov(64, calleeSpArgs, sp);

            int wordSize = 8;
            Label spCopyBegin = new Label();
            masm.bind(spCopyBegin);
            masm.ldr(64, r3, createImmediateAddress(64, IMMEDIATE_POST_INDEXED, interpDataSp, wordSize));
            masm.str(64, r3, createImmediateAddress(64, IMMEDIATE_POST_INDEXED, calleeSpArgs, wordSize));
            masm.sub(64, stackSize, stackSize, wordSize);

            masm.cbnz(64, stackSize, spCopyBegin);

            /* Set fp argument registers */
            for (int i = 0; i < fps.size(); i++) {
                masm.fldr(64, fps.get(i), createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiFpArg(i)));
            }

            /*
             * Set gp argument registers. r0 contains the pointer to the InterpreterData, so this
             * register has to be set in the last iteration.
             */
            for (int i = gps.size() - 1; i >= 0; i--) {
                masm.ldr(64, gps.get(i), createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiGpArg(i)));
            }

            /* Call into target method */
            masm.blr(callTarget);
            masm.maybeEmitIndirectTargetMarker();

            /* Obtain stack size from deopt slot */
            masm.ldr(64, r2, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, 0));

            /* Assumption of deopt slot encoding */
            assert crb.target.stackAlignment == 0x10;
            masm.lsr(64, r2, r2, DeoptimizationSlotPacking.POS_VARIABLE_FRAMESIZE - DeoptimizationSlotPacking.STACK_ALIGNMENT);

            /* Restore stack pointer */
            masm.add(64, sp, sp, r2);

            Label gpResult = new Label();
            /* The leave stub returns a long, so check whether the actual call returned in v0. */
            masm.ldr(64, r2, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, 0));
            masm.cbz(64, r2, gpResult);
            /* Return the raw float/double bits in r0. */
            masm.fmov(64, r0, v0);
            masm.bind(gpResult);

            super.leave(crb);
        }
    }

    public static class InterpreterLeaveJNIStubContext extends SubstrateAArch64Backend.SubstrateAArch64FrameContext {

        public InterpreterLeaveJNIStubContext(SharedMethod method) {
            super(method);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            super.enter(crb);
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;

            /* sp points to one reserved stack slots for this stub */

            /* arg3: true if the result of the function is in a floating-point register */
            masm.str(64, r3, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, 0));

            /*
             * Variable stack size. Compared to Java calls we have no deopt stack slot available to
             * store the stack size, so we save it in a callee-saved register.
             */
            masm.mov(64, AArch64.r19, r2);

            masm.sub(64, sp, sp, r2 /* variable stack size */);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            SubstrateAArch64RegisterConfig registerConfig = ((SubstrateAArch64RegisterConfig) crb.frameMap.getRegisterConfig());
            List<Register> gps = registerConfig.getJavaGeneralParameterRegs();
            List<Register> fps = registerConfig.getFloatingPointParameterRegs();
            Register callTarget = AArch64.r11;

            /* Save call target */
            masm.mov(64, callTarget, r0);

            /* Get pointer to InterpreterData struct */
            masm.mov(64, r0, r1);

            Register stackSize = AArch64.r2;
            Label regsHandling = new Label();
            /* if stackSize == 0 */
            masm.cbz(64, stackSize, regsHandling);

            /* Copy prepared outgoing args to the stack where the ABI expects it */
            Register calleeSpArgs = AArch64.r4;
            Register interpDataSp = AArch64.r1;
            masm.ldr(64, interpDataSp, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, r0, offsetAbiSpReg()));
            masm.mov(64, calleeSpArgs, sp);

            int wordSize = 8;
            Label spCopyBegin = new Label();
            masm.bind(spCopyBegin);
            masm.ldr(64, r3, createImmediateAddress(64, IMMEDIATE_POST_INDEXED, interpDataSp, wordSize));
            masm.str(64, r3, createImmediateAddress(64, IMMEDIATE_POST_INDEXED, calleeSpArgs, wordSize));
            masm.sub(64, stackSize, stackSize, wordSize);

            masm.cbnz(64, stackSize, spCopyBegin);

            masm.bind(regsHandling);

            /* Set fp argument registers */
            for (int i = 0; i < fps.size(); i++) {
                masm.fldr(64, fps.get(i), createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiFpArg(i)));
            }

            /*
             * Set gp argument registers. r0 contains the pointer to the InterpreterData, so this
             * register has to be set in the last iteration.
             */
            for (int i = gps.size() - 1; i >= 0; i--) {
                masm.ldr(64, gps.get(i), createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiGpArg(i)));
            }

            /* Call into target method */
            masm.blr(callTarget);

            try (AArch64MacroAssembler.ScratchRegister scratchRegister = masm.getScratchRegister()) {
                Register resultCopy = scratchRegister.getRegister();
                masm.mov(64, resultCopy, r0);

                /* Restore stack pointer */
                masm.add(64, sp, sp, AArch64.r19);

                Label gpResult = new Label();
                /* The leave stub returns a long, so check whether the actual call returned in v0. */
                masm.ldr(64, r2, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, 0));
                masm.cbz(64, r2, gpResult);
                /* Return the raw float/double bits in r0. */
                masm.fmov(64, r0, v0);
                masm.bind(gpResult);
            }

            super.leave(crb);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int sizeOfInterpreterData() {
        return NumUtil.roundUp(SizeOf.get(InterpreterDataAArch64.class), 0x10);
    }

    public static int additionalFrameSizeEnterStub() {
        int wordSize = 8;
        int deoptSlotSize = wordSize + wordSize /* for padding */;
        return sizeOfInterpreterData() + deoptSlotSize;
    }

    public static int additionalFrameSizeLeaveStub() {
        /* Reserve one extra word to remember whether the actual call returns in the FP register. */
        return 8;
    }

    @RawStructure
    public interface InterpreterDataAArch64 extends PointerBase {
        @RawField
        long getStackSize();

        @RawField
        void setStackSize(long val);

        @RawField
        long getAbiSpReg();

        @RawField
        void setAbiSpReg(long val);

        /* arch specific */
        @RawField
        long getAbiGpArg0();

        @RawField
        void setAbiGpArg0(long val);

        @RawField
        long getAbiGpArg1();

        @RawField
        void setAbiGpArg1(long val);

        @RawField
        long getAbiGpArg2();

        @RawField
        void setAbiGpArg2(long val);

        @RawField
        long getAbiGpArg3();

        @RawField
        void setAbiGpArg3(long val);

        @RawField
        long getAbiGpArg4();

        @RawField
        void setAbiGpArg4(long val);

        @RawField
        long getAbiGpArg5();

        @RawField
        void setAbiGpArg5(long val);

        @RawField
        long getAbiGpArg6();

        @RawField
        void setAbiGpArg6(long val);

        @RawField
        long getAbiGpArg7();

        @RawField
        void setAbiGpArg7(long val);

        @RawField
        long getAbiFpArg0();

        @RawField
        void setAbiFpArg0(long val);

        @RawField
        long getAbiFpArg1();

        @RawField
        void setAbiFpArg1(long val);

        @RawField
        long getAbiFpArg2();

        @RawField
        void setAbiFpArg2(long val);

        @RawField
        long getAbiFpArg3();

        @RawField
        void setAbiFpArg3(long val);

        @RawField
        long getAbiFpArg4();

        @RawField
        void setAbiFpArg4(long val);

        @RawField
        long getAbiFpArg5();

        @RawField
        void setAbiFpArg5(long val);

        @RawField
        long getAbiFpArg6();

        @RawField
        void setAbiFpArg6(long val);

        @RawField
        long getAbiFpArg7();

        @RawField
        void setAbiFpArg7(long val);

        @RawField
        void setAbiGpRet(long val);

        @RawField
        long getAbiGpRet();

        @RawField
        void setAbiFpRet(long val);

        @RawField
        long getAbiFpRet();
    }

    @Fold
    public static int offsetAbiSpReg() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiSpReg");
    }

    @Fold
    public static int offsetAbiGpArg0() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpArg0");
    }

    public static int offsetAbiGpArg(int index) {
        int offset = offsetAbiGpArg0() + 8 * index;
        assert !SubstrateUtil.HOSTED || OffsetOf.get(InterpreterDataAArch64.class, "AbiGpArg" + index) == offset;
        return offset;
    }

    @Fold
    public static int offsetAbiFpArg0() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpArg0");
    }

    public static int offsetAbiFpArg(int index) {
        int offset = offsetAbiFpArg0() + 8 * index;
        assert !SubstrateUtil.HOSTED || OffsetOf.get(InterpreterDataAArch64.class, "AbiFpArg" + index) == offset;
        return offset;
    }

    @Fold
    public static int offsetAbiFpRet() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpRet");
    }

    @Fold
    public static int offsetAbiGpRet() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpRet");
    }

    @SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = DisallowLayered.class)
    public static class AArch64InterpreterAccessStubData implements InterpreterAccessStubData {

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setSp(Pointer data, int stackSize, Pointer stackBuffer, boolean saveStackSizeInDeoptSlot) {
            VMError.guarantee(stackBuffer.isNonNull());

            InterpreterDataAArch64 p = (InterpreterDataAArch64) data;
            p.setAbiSpReg(stackBuffer.rawValue());
            p.setStackSize(stackSize);

            if (saveStackSizeInDeoptSlot) {
                /*
                 * We re-use the deopt slot to leave the stack size at a known place for the stack
                 * walker. See
                 *
                 * com.oracle.svm.core.graal.aarch64.SubstrateAArch64RegisterConfig#
                 * getCallingConvention
                 *
                 * comment above `currentStackOffset` declaration.
                 */
                assert stackSize % 0x10 == 0;
                stackBuffer.writeLong(0, DeoptimizationSlotPacking.encodeVariableFrameSizeIntoDeoptSlot(stackSize));
            } else {
                /*
                 * Native calls have no deopt slot, the leave stub preserves the stack size in
                 * callee-saved r19.
                 */
            }
        }

        @Override
        @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
        public long getGpArgumentAt(int cArgType, Pointer data, int pos) {
            InterpreterDataAArch64 p = (InterpreterDataAArch64) data;
            return switch (pos) {
                case 0 -> p.getAbiGpArg0();
                case 1 -> p.getAbiGpArg1();
                case 2 -> p.getAbiGpArg2();
                case 3 -> p.getAbiGpArg3();
                case 4 -> p.getAbiGpArg4();
                case 5 -> p.getAbiGpArg5();
                case 6 -> p.getAbiGpArg6();
                case 7 -> p.getAbiGpArg7();
                default -> {
                    VMError.guarantee(PreparedSignature.isStackSlot(cArgType));
                    Pointer spVal = Word.pointer(p.getAbiSpReg());
                    yield spVal.readLong(PreparedSignature.getStackOffset(cArgType));
                }
            };
        }

        @Override
        @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
        public void setGpArgumentAt(int cArgType, Pointer data, int pos, long val, boolean incoming) {
            InterpreterDataAArch64 p = (InterpreterDataAArch64) data;
            if (pos >= 0 && pos <= 7) {
                VMError.guarantee(PreparedSignature.isRegister(cArgType));
                switch (pos) {
                    case 0 -> p.setAbiGpArg0(val);
                    case 1 -> p.setAbiGpArg1(val);
                    case 2 -> p.setAbiGpArg2(val);
                    case 3 -> p.setAbiGpArg3(val);
                    case 4 -> p.setAbiGpArg4(val);
                    case 5 -> p.setAbiGpArg5(val);
                    case 6 -> p.setAbiGpArg6(val);
                    case 7 -> p.setAbiGpArg7(val);
                }
                return;
            }
            VMError.guarantee(PreparedSignature.isStackSlot(cArgType));

            Pointer spVal = Word.pointer(p.getAbiSpReg());
            int offset = PreparedSignature.getStackOffset(cArgType);
            VMError.guarantee(spVal.isNonNull());
            VMError.guarantee(incoming || offset < p.getStackSize());

            spVal.writeLong(offset, val);
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public long getFpArgumentAt(int cArgType, Pointer data, int pos) {
            InterpreterDataAArch64 p = (InterpreterDataAArch64) data;
            return switch (pos) {
                case 0 -> p.getAbiFpArg0();
                case 1 -> p.getAbiFpArg1();
                case 2 -> p.getAbiFpArg2();
                case 3 -> p.getAbiFpArg3();
                case 4 -> p.getAbiFpArg4();
                case 5 -> p.getAbiFpArg5();
                case 6 -> p.getAbiFpArg6();
                case 7 -> p.getAbiFpArg7();
                default -> {
                    VMError.guarantee(PreparedSignature.isStackSlot(cArgType));
                    Pointer spVal = Word.pointer(p.getAbiSpReg());
                    yield spVal.readLong(PreparedSignature.getStackOffset(cArgType));
                }
            };
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setFpArgumentAt(int cArgType, Pointer data, int pos, long val) {
            InterpreterDataAArch64 p = (InterpreterDataAArch64) data;
            switch (pos) {
                case 0 -> p.setAbiFpArg0(val);
                case 1 -> p.setAbiFpArg1(val);
                case 2 -> p.setAbiFpArg2(val);
                case 3 -> p.setAbiFpArg3(val);
                case 4 -> p.setAbiFpArg4(val);
                case 5 -> p.setAbiFpArg5(val);
                case 6 -> p.setAbiFpArg6(val);
                case 7 -> p.setAbiFpArg7(val);
                default -> {
                    VMError.guarantee(PreparedSignature.isStackSlot(cArgType));

                    Pointer spVal = Word.pointer(p.getAbiSpReg());
                    int offset = PreparedSignature.getStackOffset(cArgType);

                    VMError.guarantee(spVal.isNonNull());
                    VMError.guarantee(offset < p.getStackSize());

                    spVal.writeLong(offset, val);
                }
            }
        }

        @Override
        @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
        public long getGpReturn(Pointer data) {
            return ((InterpreterDataAArch64) data).getAbiGpRet();
        }

        @Override
        @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
        public void setGpReturn(Pointer data, long gpReturn) {
            ((InterpreterDataAArch64) data).setAbiGpRet(gpReturn);
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public long getFpReturn(Pointer data) {
            return ((InterpreterDataAArch64) data).getAbiFpRet();
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setFpReturn(Pointer data, long fpReturn) {
            ((InterpreterDataAArch64) data).setAbiFpRet(fpReturn);
        }

        @Override
        @Fold
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public int allocateStubDataSize() {
            return sizeOfInterpreterData();
        }
    }

    /**
     * Frame context for
     * {@link com.oracle.svm.core.deopt.Deoptimizer.StubType#InterpreterDeoptEntryPointStub}. This
     * transition restores the source-frame stack/base pointers and return address, then jumps to
     * the interpreter deoptimization entry point.
     */
    protected static class InterpreterDeoptEntryPointStubFrameContext extends SubstrateAArch64Backend.SubstrateAArch64FrameContext {
        protected final CallingConvention callingConvention;

        protected InterpreterDeoptEntryPointStubFrameContext(SharedMethod method, CallingConvention callingConvention) {
            super(method);
            this.callingConvention = callingConvention;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            /* do nothing */
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            RegisterConfig registerConfig = crb.frameMap.getRegisterConfig();

            /* leave arg0 untouched, it's the first argument to the interpreter entry point */

            Register regRevertSp = ValueUtil.asRegister(callingConvention.getArgument(1));
            Register regInterpEntryPoint = ValueUtil.asRegister(callingConvention.getArgument(2));
            Register regOldReturnAddress = ValueUtil.asRegister(callingConvention.getArgument(3));
            Register regOldBasePointer = ValueUtil.asRegister(callingConvention.getArgument(4));

            masm.mov(64, sp, regRevertSp);
            if (((SubstrateAArch64RegisterConfig) registerConfig).shouldPreserveFramePointer()) {
                masm.mov(64, fp, regOldBasePointer);
            }
            /*
             * wire up lr properly so that the interpreter entrypoint returns to the original caller
             */
            masm.mov(64, lr, regOldReturnAddress);

            masm.jmp(regInterpEntryPoint);
        }
    }
}
