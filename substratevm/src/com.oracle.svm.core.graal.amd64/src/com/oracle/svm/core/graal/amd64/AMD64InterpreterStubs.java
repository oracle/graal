/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.interpreter.InterpreterSupport.NATIVE_DOWNCALL_RETURNS_IN_BUFFER;
import static com.oracle.svm.core.interpreter.InterpreterSupport.NATIVE_DOWNCALL_RETURNS_IN_FP_REGISTER;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;

import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.c.struct.OffsetOf;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.deopt.DeoptimizationSlotPacking;
import com.oracle.svm.core.graal.code.InterpreterAccessStubData;
import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.meta.DynamicHubOffsets;
import com.oracle.svm.core.graal.meta.InterpreterExecutionOffsets;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.interpreter.InterpreterEnterStub;
import com.oracle.svm.core.interpreter.InterpreterJNIUpcallStub;
import com.oracle.svm.core.jni.CallVariant;
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
import com.oracle.svm.util.GuestAnnotationAccess;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.JavaKind;

public class AMD64InterpreterStubs {

    private static SubstrateAMD64RegisterConfig getRegisterConfig() {
        return new SubstrateAMD64RegisterConfig(SubstrateRegisterConfig.ConfigKind.NORMAL, null, SubstrateTarget.singleton(),
                        SubstrateOptions.PreserveFramePointer.getValue());
    }

    private static final int JAVA_GP_REGISTERS_SIZE = getRegisterConfig().getJavaGeneralParameterRegs().size();
    private static final int NATIVE_GP_REGISTERS_SIZE = getRegisterConfig().getNativeGeneralParameterRegs().size();

    public static class InterpreterEnterStubContext extends SubstrateAMD64Backend.SubstrateAMD64FrameContext {
        private final boolean emitDirectFastPath;
        private final boolean emitVTableFastPath;

        public InterpreterEnterStubContext(SharedMethod method, CallingConvention callingConvention) {
            super(method, callingConvention);
            boolean useRistretto = SubstrateOptions.useRistretto();
            InterpreterEnterStub stubType = GuestAnnotationAccess.getAnnotation(method, InterpreterEnterStub.class);
            assert stubType != null : "Missing @InterpreterEnterStub annotation on interpreter enter stub.";
            assert !useRistretto || ImageSingletons.contains(InterpreterExecutionOffsets.class) : "Missing InterpreterExecutionOffsets singleton while Ristretto is enabled.";
            emitDirectFastPath = useRistretto && stubType.value() == InterpreterEnterStub.Kind.DIRECT;
            emitVTableFastPath = useRistretto && stubType.value() == InterpreterEnterStub.Kind.VTABLE;
        }

        private static AMD64Address createAddress(int offset) {
            int deoptSlotSize = 8 + 8 /* padding */;
            return new AMD64Address(rsp, deoptSlotSize + offset);
        }

        private static AMD64BaseAssembler.OperandSize referenceOperandSize() {
            int refSize = ObjectLayout.singleton().getReferenceSize();
            return refSize == Integer.BYTES ? AMD64BaseAssembler.OperandSize.DWORD : AMD64BaseAssembler.OperandSize.QWORD;
        }

        private static AMD64Address heapObjectAddress(Register base, int offset, boolean compressedBase, int compressionShift) {
            if (compressedBase) {
                return new AMD64Address(ReservedRegisters.singleton().getHeapBaseRegister(), base, Stride.fromLog2(compressionShift), offset);
            }
            return new AMD64Address(base, offset);
        }

        private static void loadObjectObjectField(AMD64MacroAssembler masm, Register dst, Register obj, int offset, boolean compressedBase, int compressionShift) {
            AMD64Assembler.AMD64RMOp.MOV.emit(masm, referenceOperandSize(), dst, heapObjectAddress(obj, offset, compressedBase, compressionShift));
        }

        private static void loadObjectWordField(AMD64MacroAssembler masm, Register dst, Register obj, int offset, boolean compressedBase, int compressionShift) {
            AMD64Assembler.AMD64RMOp.MOV.emit(masm, AMD64BaseAssembler.OperandSize.QWORD, dst, heapObjectAddress(obj, offset, compressedBase, compressionShift));
        }

        /**
         * See {@code SubstrateBasicLoweringProvider#createReadHub}.
         */
        private static void loadHub(AMD64MacroAssembler masm, Register obj, Register hub, Register scratch2) {
            ObjectLayout ol = ObjectLayout.singleton();
            long reservedHubBitsMask = Heap.getHeap().getObjectHeader().getReservedHubBitsMask();
            int compressionShift = ReferenceAccess.singleton().getCompressionShift();
            int alignmentBits = CodeUtil.log2(ol.getAlignment());

            AMD64Assembler.AMD64RMOp.MOV.emit(masm, ol.getHubSize() == Integer.BYTES ? AMD64BaseAssembler.OperandSize.DWORD : AMD64BaseAssembler.OperandSize.QWORD,
                            hub, new AMD64Address(obj, ol.getHubOffset()));

            if (reservedHubBitsMask != 0) {
                int reservedHubBits = CodeUtil.log2(reservedHubBitsMask + 1L);
                if (reservedHubBits == alignmentBits && compressionShift == 0) {
                    if (ol.getHubSize() == Integer.BYTES) {
                        // Clear the reserved low hub-tag bits in place.
                        masm.andl(hub, (int) ~reservedHubBitsMask);
                    } else {
                        masm.movq(scratch2, ~reservedHubBitsMask);
                        // 64-bit hub: materialize the mask in a register first.
                        masm.andq(hub, scratch2);
                    }
                } else {
                    // Remove reserved tag bits and reconstruct the aligned heap address.
                    masm.shrq(hub, reservedHubBits);
                    if (compressionShift != alignmentBits) {
                        /*
                         * Keep scratch1 as a compressed reference; the remaining shift restores the
                         * alignment bits discarded by hub tagging without fully decoding the
                         * reference to a heap address.
                         */
                        masm.shlq(hub, alignmentBits - compressionShift);
                    }
                }
            }
            if (ol.getReferenceSize() == Integer.BYTES) {
                // Zero-extend the upper 32 bits.
                masm.movl(hub, hub);
            }
        }

        private static void goSlowPathIfNull(AMD64MacroAssembler masm, Register value, Label slowPath) {
            masm.testq(value, value);
            masm.jccb(AMD64Assembler.ConditionFlag.Zero, slowPath);
        }

        private static void goSlowPathIfIndexOutOfBounds(AMD64MacroAssembler masm, Register array, Register index, Register scratch, int arrayLengthOffset, Label slowPath) {
            AMD64Assembler.AMD64RMOp.MOV.emit(masm, AMD64BaseAssembler.OperandSize.DWORD, scratch, new AMD64Address(array, arrayLengthOffset));
            /*
             * AboveEqual in the unsigned compare covers both index >= array.length and negative
             * Java indices.
             */
            masm.cmpl(index, scratch);
            masm.jcc(AMD64Assembler.ConditionFlag.AboveEqual, slowPath);
        }

        /**
         * Fast path for {@code InterpreterStubSection.enterDirectInterpreterStub(...)}, or for the
         * method resolved by {@code InterpreterStubSection.enterVTableInterpreterStub(...)}. If the
         * target method already has valid installed code, jump to its entry point instead of
         * entering the interpreter.
         */
        private static void emitInstalledCodeFastPath(AMD64MacroAssembler masm, Register interpreterMethod, boolean compressedInterpreterMethod, Register scratch) {
            Label slowPath = new Label();

            int compressionShift = ReferenceAccess.singleton().getCompressionShift();
            InterpreterExecutionOffsets executionOffsets = InterpreterExecutionOffsets.singleton();

            /*
             * Keep this sanity check for now so that, if we ever end up with a null method here, we
             * fall back to the slow path instead of crashing in the fast path. The slow path also
             * performs a sanity check, so this guard should be removable in the future.
             */
            goSlowPathIfNull(masm, interpreterMethod, slowPath);

            loadObjectObjectField(masm, scratch, interpreterMethod, executionOffsets.getInterpreterResolvedJavaMethodRistrettoMethodOffset(), compressedInterpreterMethod, compressionShift);
            goSlowPathIfNull(masm, scratch, slowPath);

            loadObjectObjectField(masm, scratch, scratch, executionOffsets.getRistrettoMethodInstalledCodeOffset(), true, compressionShift);
            goSlowPathIfNull(masm, scratch, slowPath);

            loadObjectWordField(masm, scratch, scratch, executionOffsets.getInstalledCodeEntryPointOffset(), true, compressionShift);
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
        private static void emitVTableInstalledCodeFastPath(AMD64MacroAssembler masm, Register receiver, Register vtableIndex, Register scratch1, Register scratch2) {
            ObjectLayout ol = ObjectLayout.singleton();
            int compressionShift = ReferenceAccess.singleton().getCompressionShift();

            DynamicHubOffsets hubOffsets = DynamicHubOffsets.singleton();
            InterpreterExecutionOffsets executionOffsets = InterpreterExecutionOffsets.singleton();

            Label slowPath = new Label();

            loadHub(masm, receiver, scratch1, scratch2);

            // Extract the companion.
            loadObjectObjectField(masm, scratch1, scratch1, hubOffsets.getCompanionOffset(), true, compressionShift);
            // Extract the interpreter type of the companion.
            loadObjectObjectField(masm, scratch1, scratch1, executionOffsets.getDynamicHubCompanionInterpreterTypeOffset(), true, compressionShift);
            // Extract the vtable holder of the interpreter type.
            loadObjectObjectField(masm, scratch1, scratch1, executionOffsets.getInterpreterResolvedObjectTypeVtableHolderOffset(), true, compressionShift);
            // Extract the vtable.
            loadObjectObjectField(masm, scratch1, scratch1, executionOffsets.getVtableHolderVtableOffset(), true, compressionShift);

            AMD64Move.UncompressPointerOp.emitUncompressWithBaseRegister(masm, scratch1, ReservedRegisters.singleton().getHeapBaseRegister(), compressionShift, true);

            goSlowPathIfIndexOutOfBounds(masm, scratch1, vtableIndex, scratch2, ol.getArrayLengthOffset(), slowPath);

            Register method = scratch2;
            /*
             * Extract the method from the vtable. Address = vtable base + zero-extended 32-bit
             * index * referenceSize + array header.
             */
            AMD64Assembler.AMD64RMOp.MOV.emit(masm, referenceOperandSize(), method,
                            new AMD64Address(scratch1, vtableIndex, Stride.fromLog2(CodeUtil.log2(ol.getReferenceSize())), ol.getArrayBaseOffset(JavaKind.Object)));

            emitInstalledCodeFastPath(masm, method, true, scratch1);
            masm.bind(slowPath);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;

            List<Register> gps = getRegisterConfig().getJavaGeneralParameterRegs();
            List<Register> fps = getRegisterConfig().getFloatingPointParameterRegs();

            /*
             * Ristretto currently makes this frame context reachable during analysis. Explicitly
             * avoid the singleton lookup in that case until GR-74744 is fixed.
             */
            if (SubstrateUtil.HOSTED) {
                if (emitVTableFastPath) {
                    emitVTableInstalledCodeFastPath(masm, gps.getFirst(), SubstrateAMD64Backend.HIDDEN_ARGUMENT_REGISTER, r10, r11);
                } else if (emitDirectFastPath) {
                    emitInstalledCodeFastPath(masm, SubstrateAMD64Backend.HIDDEN_ARGUMENT_REGISTER, false, r10);
                }
            }

            Register trampArg = SubstrateAMD64Backend.HIDDEN_ARGUMENT_REGISTER;
            Register spCopy = r11;

            masm.movq(spCopy, rsp);

            super.enter(crb);

            /* sp points to InterpreterData struct */
            masm.movq(createAddress(offsetAbiSpReg()), spCopy);

            VMError.guarantee(gps.size() == 6);

            for (int i = 0; i < gps.size(); i++) {
                masm.movq(createAddress(offsetAbiGp(i)), gps.get(i));
            }

            for (int i = 0; i < fps.size(); i++) {
                masm.movq(createAddress(offsetAbiFpArg(i)), fps.get(i));
            }

            /* sp points to InterpreterData struct, move it as 2nd arg */
            masm.movq(gps.get(1), rsp);
            masm.addq(gps.get(1), 16 /* deoptSlotSize */);

            /* Pass the interpreter method index as first arg */
            masm.movq(gps.get(0), trampArg);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;

            /* rax contains the raw result. Make it available in both ABI return registers. */
            masm.movdq(xmm0, rax);

            super.leave(crb);
        }
    }

    /** Adapts incoming JNI arguments to the interpreter wrapper signature. */
    public static class InterpreterJNIUpcallStubContext extends SubstrateAMD64Backend.SubstrateAMD64FrameContext {
        private final CallVariant callVariant;
        private final boolean nonVirtual;

        public InterpreterJNIUpcallStubContext(SharedMethod method, CallingConvention callingConvention) {
            super(method, callingConvention);
            InterpreterJNIUpcallStub annotation = GuestAnnotationAccess.getAnnotation(method, InterpreterJNIUpcallStub.class);
            assert annotation != null : "Missing @InterpreterJNIUpcallStub annotation on JNI interpreter wrapper.";
            callVariant = annotation.callVariant();
            nonVirtual = annotation.nonVirtual();
        }

        private static AMD64Address jniUpcallDataAddress(SubstrateAMD64Backend.SubstrateAMD64FrameMap frameMap, int offset) {
            return new AMD64Address(rsp, frameMap.offsetForStackSlot(frameMap.getInterpreterJNIUpcallData()) + offset);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
            SubstrateAMD64Backend.SubstrateAMD64FrameMap frameMap = (SubstrateAMD64Backend.SubstrateAMD64FrameMap) crb.frameMap;
            SubstrateAMD64RegisterConfig registerConfig = (SubstrateAMD64RegisterConfig) frameMap.getRegisterConfig();
            StackSlot jniUpcallData = frameMap.getInterpreterJNIUpcallData();
            assert (callVariant == CallVariant.VARARGS) == (jniUpcallData != null);

            List<Register> gps = registerConfig.getNativeGeneralParameterRegs();
            List<Register> fps = registerConfig.getFloatingPointParameterRegs();
            Register originalSp = r11;

            /* Preserve the caller stack pointer before the regular prologue allocates the frame. */
            masm.movq(originalSp, rsp);
            super.enter(crb);

            if (callVariant == CallVariant.VARARGS) {
                /* Capture the original JNI arguments before adapting registers for the wrapper. */
                masm.movq(jniUpcallDataAddress(frameMap, offsetAbiSpReg()), originalSp);
                for (int i = 0; i < gps.size(); i++) {
                    masm.movq(jniUpcallDataAddress(frameMap, offsetAbiGp(i)), gps.get(i));
                }
                for (int i = 0; i < fps.size(); i++) {
                    masm.movq(jniUpcallDataAddress(frameMap, offsetAbiFpArg(i)), fps.get(i));
                }
            }

            if (nonVirtual) {
                /* Drop the JNI clazz argument: methodID becomes the third argument. */
                masm.movq(gps.get(2), gps.get(3));
            }

            Register payload = gps.get(3);
            if (callVariant == CallVariant.VARARGS) {
                /* Pass the address of the dedicated frame slot that preserves the captured arguments. */
                masm.leaq(payload, jniUpcallDataAddress(frameMap, 0));
            } else if (nonVirtual) {
                /* Due to the drop of the JNI clazz argument: payload becomes the fourth argument. */
                if (gps.size() > 4) {
                    masm.movq(payload, gps.get(4));
                } else {
                    assert Platform.includedIn(InternalPlatform.WINDOWS_BASE.class);
                    /* On Windows, the fifth JNI argument follows shadow space on the caller stack. */
                    masm.movq(payload, new AMD64Address(originalSp, FrameAccess.returnAddressSize() + callingConvention.getStackSize()));
                }
            }
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
            /* The wrapper returns raw bits in rax; JNI floating-point variants return them in xmm0. */
            masm.movdq(xmm0, rax);
            super.leave(crb);
        }
    }

    public static class InterpreterLeaveStubContext extends SubstrateAMD64Backend.SubstrateAMD64FrameContext {

        public InterpreterLeaveStubContext(SharedMethod method, CallingConvention callingConvention) {
            super(method, callingConvention);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            super.enter(crb);
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
            List<Register> gps = getRegisterConfig().getJavaGeneralParameterRegs();

            /* sp points to a reserved stack slot for this stub */

            /* arg0 is untouched by this extra prolog */

            /* arg3: true if the result of the function is in a floating-point register */
            masm.movq(new AMD64Address(rsp, 0), gps.get(3));

            masm.subq(rsp, gps.get(2) /* variable stack size */);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
            List<Register> gps = getRegisterConfig().getJavaGeneralParameterRegs();
            List<Register> fps = getRegisterConfig().getFloatingPointParameterRegs();

            /* Save call target */
            Register callTarget = r10;
            masm.movq(callTarget, rax);

            /* Get pointer to InterpreterData struct */
            masm.movq(rax, gps.get(1));

            Register stackSize = r11;
            masm.movq(stackSize, gps.get(2));

            /* Copy prepared outgoing args to the stack where the ABI expects it */
            Register calleeSpArgs = r12;
            Register interpDataSp = r9;
            masm.movq(interpDataSp, new AMD64Address(rax, offsetAbiSpReg()));
            masm.movq(calleeSpArgs, rsp);

            int wordSize = 8;
            Label spCopyBegin = new Label();
            masm.bind(spCopyBegin);
            /* 5th arg is not used, use it as a temp register */
            masm.movq(gps.get(4), new AMD64Address(interpDataSp, 0));
            masm.movq(new AMD64Address(calleeSpArgs, 0), gps.get(4));
            masm.addq(interpDataSp, wordSize);
            masm.addq(calleeSpArgs, wordSize);
            masm.subl(stackSize, wordSize);

            masm.testq(stackSize, stackSize);
            masm.jccb(AMD64Assembler.ConditionFlag.NotZero, spCopyBegin);

            /* Set fp argument registers */
            for (int i = 0; i < fps.size(); i++) {
                masm.movq(fps.get(i), new AMD64Address(rax, offsetAbiFpArg(i)));
            }

            /* Set gp argument registers */
            for (int i = 0; i < gps.size(); i++) {
                masm.movq(gps.get(i), new AMD64Address(rax, offsetAbiGp(i)));
            }

            /* Call into target method */
            masm.indirectCall(AMD64MacroAssembler.PostCallAction.NONE, callTarget, false, null, SubstrateCallingConventionKind.Java.toType(true));

            /* Obtain stack size from deopt slot */
            masm.movq(r12, new AMD64Address(rsp, 0));

            /* Assumption of deopt slot encoding */
            assert crb.target.stackAlignment == 0x10;
            masm.shrq(r12, DeoptimizationSlotPacking.POS_VARIABLE_FRAMESIZE - DeoptimizationSlotPacking.STACK_ALIGNMENT);

            /* Restore stack pointer */
            masm.addq(rsp, r12);

            Label gpResult = new Label();
            /* The leave stub returns a long, so check whether the actual call returned in xmm0. */
            masm.movq(r10, new AMD64Address(rsp, 0));
            masm.testq(r10, r10);
            masm.jccb(AMD64Assembler.ConditionFlag.Zero, gpResult);
            /* Return the raw float/double bits in rax. */
            masm.movdq(rax, xmm0);
            masm.bind(gpResult);

            super.leave(crb);
        }
    }

    public static class InterpreterNativeDowncallStubContext extends SubstrateAMD64Backend.SubstrateAMD64FrameContext {

        public InterpreterNativeDowncallStubContext(SharedMethod method, CallingConvention callingConvention) {
            super(method, callingConvention);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            super.enter(crb);
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
            List<Register> gps = getRegisterConfig().getJavaGeneralParameterRegs();

            /* sp points to two reserved stack slots for this stub */

            /* arg0 is untouched by this extra prolog */

            /* arg3: return flags used to preserve the required native return registers. */
            masm.movq(new AMD64Address(rsp, 0), gps.get(3));

            /*
             * Interpreter data lives in the caller's stack. Do not preserve its absolute address
             * across the native call because relocating a virtual-thread stack would make that
             * address stale. Preserve its SP-relative offset instead.
             */
            masm.movq(r10, gps.get(1));
            masm.subq(r10, rsp);
            masm.movq(new AMD64Address(rsp, 8), r10);
            /*
             * arg2: Variable stack size. Compared to Java calls we have no deopt stack slot
             * available to store the stack size, so we save it in a callee-saved register.
             */
            masm.movq(rbx, gps.get(2));

            masm.subq(rsp, gps.get(2) /* variable stack size */);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
            List<Register> jgps = getRegisterConfig().getJavaGeneralParameterRegs();
            List<Register> ngps = getRegisterConfig().getNativeGeneralParameterRegs();
            List<Register> fps = getRegisterConfig().getFloatingPointParameterRegs();

            /* Save call target */
            Register callTarget = r10;
            masm.movq(callTarget, rax);

            /* Get pointer to InterpreterData struct */
            masm.movq(rax, jgps.get(1));

            Register stackSize = r11;
            masm.movq(stackSize, jgps.get(2));

            Label regsHandling = new Label();
            /* if stackSize == 0 */
            masm.testq(stackSize, stackSize);
            masm.jccb(AMD64Assembler.ConditionFlag.Zero, regsHandling);

            /* Copy prepared outgoing args to the stack where the ABI expects it */
            Register calleeSpArgs = r12;
            Register interpDataSp = r9;
            masm.movq(interpDataSp, new AMD64Address(rax, offsetAbiSpReg()));
            masm.movq(calleeSpArgs, rsp);

            int wordSize = 8;
            Label spCopyBegin = new Label();
            masm.bind(spCopyBegin);
            /* 5th arg is not used, use it as a temp register */
            masm.movq(jgps.get(4), new AMD64Address(interpDataSp, 0));
            masm.movq(new AMD64Address(calleeSpArgs, 0), jgps.get(4));
            masm.addq(interpDataSp, wordSize);
            masm.addq(calleeSpArgs, wordSize);
            masm.subl(stackSize, wordSize);

            masm.testq(stackSize, stackSize);
            masm.jccb(AMD64Assembler.ConditionFlag.NotZero, spCopyBegin);

            masm.bind(regsHandling);

            /* Set fp argument registers */
            for (int i = 0; i < fps.size(); i++) {
                masm.movq(fps.get(i), new AMD64Address(rax, offsetAbiFpArg(i)));
            }

            /* Set gp argument registers */
            for (int i = 0; i < ngps.size(); i++) {
                masm.movq(ngps.get(i), new AMD64Address(rax, offsetAbiGp(i)));
            }

            /*
             * FFM variadic calls pass the number of used XMM argument registers in al.
             */
            masm.movq(rax, new AMD64Address(rax, offsetAbiGpRet()));

            /* Call into target method */
            masm.indirectCall(AMD64MacroAssembler.PostCallAction.NONE, callTarget, false, null, SubstrateCallingConventionKind.Native.toType(true));

            /* Restore stack pointer */
            masm.addq(rsp, rbx);

            Register returnFlags = r10;
            masm.movq(returnFlags, new AMD64Address(rsp, 0));

            Label noReturnBuffer = new Label();
            masm.testl(returnFlags, NATIVE_DOWNCALL_RETURNS_IN_BUFFER);
            masm.jccb(AMD64Assembler.ConditionFlag.Zero, noReturnBuffer);
            /*
             * Outgoing argument slots are dead after the call. Reuse them to preserve every return
             * register needed by an FFM aggregate return before those registers are repurposed.
             */
            Register data = r11;
            masm.movq(data, new AMD64Address(rsp, 8));
            masm.addq(data, rsp);
            masm.movq(new AMD64Address(data, offsetAbiGp(0)), rax);
            masm.movq(new AMD64Address(data, offsetAbiGp(1)), rdx);
            masm.movq(new AMD64Address(data, offsetAbiFpArg(0)), xmm0);
            masm.movq(new AMD64Address(data, offsetAbiFpArg(1)), xmm1);
            masm.bind(noReturnBuffer);

            Label gpResult = new Label();
            /* The leave stub returns a long, so check whether the actual call returned in xmm0. */
            masm.testl(returnFlags, NATIVE_DOWNCALL_RETURNS_IN_FP_REGISTER);
            masm.jccb(AMD64Assembler.ConditionFlag.Zero, gpResult);
            /* Return the raw float/double bits in rax. */
            masm.movdq(rax, xmm0);
            masm.bind(gpResult);

            super.leave(crb);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int sizeOfInterpreterData() {
        return NumUtil.roundUp(SizeOf.get(InterpreterDataAMD64.class), 0x10);
    }

    public static int additionalFrameSizeEnterStub() {
        int wordSize = 8;
        int deoptSlotSize = wordSize + wordSize /* for padding */;
        return sizeOfInterpreterData() + deoptSlotSize;
    }

    public static int additionalFrameSizeLeaveStub() {
        /* Reserve words for the FP-return flag and the stable interpreter data pointer. */
        return 16;
    }

    @RawStructure
    public interface InterpreterDataAMD64 extends PointerBase {
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
        void setAbiGpRet(long val);

        @RawField
        long getAbiGpRet();

        @RawField
        void setAbiFpRet(long val);

        @RawField
        long getAbiFpRet();

        @RawField
        long getAbiGp0();

        @RawField
        void setAbiGp0(long val);

        @RawField
        long getAbiGp1();

        @RawField
        void setAbiGp1(long val);

        @RawField
        long getAbiGp2();

        @RawField
        void setAbiGp2(long val);

        @RawField
        long getAbiGp3();

        @RawField
        void setAbiGp3(long val);

        @RawField
        long getAbiGp4();

        @RawField
        void setAbiGp4(long val);

        @RawField
        long getAbiGp5();

        @RawField
        void setAbiGp5(long val);

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

        // GR-55154: We could save 32 bytes on Windows by exluding storage for xmm4-xmm7
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
    }

    @Fold
    public static int offsetAbiSpReg() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiSpReg");
    }

    @Fold
    public static int offsetAbiGp0() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiGp0");
    }

    public static int offsetAbiGp(int index) {
        int offset = offsetAbiGp0() + 8 * index;
        assert !SubstrateUtil.HOSTED || OffsetOf.get(InterpreterDataAMD64.class, "AbiGp" + index) == offset;
        return offset;
    }

    @Fold
    public static int offsetAbiFpArg0() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpArg0");
    }

    public static int offsetAbiFpArg(int index) {
        int offset = offsetAbiFpArg0() + 8 * index;
        assert !SubstrateUtil.HOSTED || OffsetOf.get(InterpreterDataAMD64.class, "AbiFpArg" + index) == offset;
        return offset;
    }

    @Fold
    public static int offsetAbiGpRet() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiGpRet");
    }

    @Fold
    public static int offsetAbiFpRet() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpRet");
    }

    @SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = DisallowLayered.class)
    public static class AMD64InterpreterAccessStubData implements InterpreterAccessStubData {

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static int spAdjustOnCall(int offset) {
            // offset is relative caller sp, undo side-effect of call instruction
            int spAdjustmentOnCall = SubstrateTarget.getWordSize();
            return offset + spAdjustmentOnCall;
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setSp(Pointer data, Pointer stackBuffer) {
            VMError.guarantee(stackBuffer.isNonNull());
            ((InterpreterDataAMD64) data).setAbiSpReg(stackBuffer.rawValue());
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setStackSize(Pointer data, int stackSize, boolean saveStackSizeInDeoptSlot) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            p.setStackSize(stackSize);

            if (saveStackSizeInDeoptSlot) {
                /*
                 * We re-use the deopt slot to leave the stack size at a known place for the stack
                 * walker. See
                 *
                 * com.oracle.svm.core.graal.amd64.SubstrateAMD64RegisterConfig#getCallingConvention
                 *
                 * comment above `currentStackOffset` declaration.
                 */
                assert stackSize % 0x10 == 0;
                Pointer stackBuffer = Word.pointer(p.getAbiSpReg());
                stackBuffer.writeLong(0, DeoptimizationSlotPacking.encodeVariableFrameSizeIntoDeoptSlot(stackSize));
            } else {
                /*
                 * Native calls have no deopt slot, the leave stub preserves the stack size in
                 * callee-saved rbx.
                 */
            }
        }

        @Override
        @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
        public long getGpArgumentAt(int cArgType, Pointer data, int pos) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            return switch (pos) {
                case 0 -> p.getAbiGp0();
                case 1 -> p.getAbiGp1();
                case 2 -> p.getAbiGp2();
                case 3 -> p.getAbiGp3();
                case 4 -> p.getAbiGp4();
                case 5 -> p.getAbiGp5();
                default -> {
                    VMError.guarantee(PreparedSignature.isStackSlot(cArgType));
                    Pointer sp = Word.pointer(p.getAbiSpReg());

                    yield sp.readLong(spAdjustOnCall(PreparedSignature.getStackOffset(cArgType)));
                }
            };
        }

        @Override
        @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
        public void setGpArgumentAt(int cArgType, Pointer data, int pos, long val, boolean incoming) {
            setGpArgumentAt0(cArgType, data, pos, val, incoming, JAVA_GP_REGISTERS_SIZE);
        }

        @Override
        @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
        public void setGpArgumentAtNative(int cArgType, Pointer data, int pos, long val, boolean incoming) {
            if (PreparedSignature.isRegister(cArgType) && pos == NATIVE_GP_REGISTERS_SIZE) {
                /*
                 * SysV models the number of XMM arguments to a variadic function as a synthetic
                 * GP argument assigned to rax, immediately after the ordinary GP argument
                 * registers. The native leave stub loads AbiGpRet into rax before the call.
                 */
                VMError.guarantee(!incoming && PreparedSignature.isRegister(cArgType));
                ((InterpreterDataAMD64) data).setAbiGpRet(val);
                return;
            }
            setGpArgumentAt0(cArgType, data, pos, val, incoming, NATIVE_GP_REGISTERS_SIZE);
        }

        @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
        private static void setGpArgumentAt0(int cArgType, Pointer data, int pos, long val, boolean incoming, int gpRegisterSize) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            if (pos >= 0 && pos < gpRegisterSize) {
                VMError.guarantee(PreparedSignature.isRegister(cArgType));
                switch (pos) {
                    case 0 -> p.setAbiGp0(val);
                    case 1 -> p.setAbiGp1(val);
                    case 2 -> p.setAbiGp2(val);
                    case 3 -> p.setAbiGp3(val);
                    case 4 -> p.setAbiGp4(val);
                    case 5 -> p.setAbiGp5(val);
                }
                return;
            }
            VMError.guarantee(PreparedSignature.isStackSlot(cArgType));

            Pointer sp = Word.pointer(p.getAbiSpReg());
            int offset = PreparedSignature.getStackOffset(cArgType);
            if (incoming) {
                offset = spAdjustOnCall(offset);
            }

            VMError.guarantee(sp.isNonNull());
            VMError.guarantee(incoming || offset < InterpreterAccessStubData.getStackBufferSize());

            sp.writeLong(offset, val);
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static int upperFpEnd() {
            /* only 4 floating point regs on Windows, 8 otherwise */
            return Platform.includedIn(InternalPlatform.WINDOWS_BASE.class) ? 3 : 7;
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public long getFpArgumentAt(int cArgType, Pointer data, int pos) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            if (pos >= 0 && pos <= upperFpEnd()) {
                VMError.guarantee(PreparedSignature.isRegister(cArgType));
                switch (pos) {
                    case 0:
                        return p.getAbiFpArg0();
                    case 1:
                        return p.getAbiFpArg1();
                    case 2:
                        return p.getAbiFpArg2();
                    case 3:
                        return p.getAbiFpArg3();
                    case 4:
                        return p.getAbiFpArg4();
                    case 5:
                        return p.getAbiFpArg5();
                    case 6:
                        return p.getAbiFpArg6();
                    case 7:
                        return p.getAbiFpArg7();
                }
            }
            VMError.guarantee(PreparedSignature.isStackSlot(cArgType));
            Pointer sp = Word.pointer(p.getAbiSpReg());

            return sp.readLong(spAdjustOnCall(PreparedSignature.getStackOffset(cArgType)));
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setFpArgumentAt(int cArgType, Pointer data, int pos, long val) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            if (pos >= 0 && pos <= upperFpEnd()) {
                VMError.guarantee(PreparedSignature.isRegister(cArgType));
                switch (pos) {
                    case 0 -> p.setAbiFpArg0(val);
                    case 1 -> p.setAbiFpArg1(val);
                    case 2 -> p.setAbiFpArg2(val);
                    case 3 -> p.setAbiFpArg3(val);
                    case 4 -> p.setAbiFpArg4(val);
                    case 5 -> p.setAbiFpArg5(val);
                    case 6 -> p.setAbiFpArg6(val);
                    case 7 -> p.setAbiFpArg7(val);
                }
            } else {
                VMError.guarantee(PreparedSignature.isStackSlot(cArgType));

                Pointer sp = Word.pointer(p.getAbiSpReg());
                int offset = PreparedSignature.getStackOffset(cArgType);

                VMError.guarantee(sp.isNonNull());
                VMError.guarantee(offset < InterpreterAccessStubData.getStackBufferSize());

                sp.writeLong(offset, val);
            }
        }

        @Override
        @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
        public long getGpReturn(Pointer data) {
            return ((InterpreterDataAMD64) data).getAbiGpRet();
        }

        @Override
        @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
        public void setGpReturn(Pointer data, long gpReturn) {
            ((InterpreterDataAMD64) data).setAbiGpRet(gpReturn);
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public long getFpReturn(Pointer data) {
            return ((InterpreterDataAMD64) data).getAbiFpRet();
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public void setFpReturn(Pointer data, long fpReturn) {
            ((InterpreterDataAMD64) data).setAbiFpRet(fpReturn);
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public long getGpResultAt(Pointer data, int index) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            return switch (index) {
                case 0 -> p.getAbiGp0();
                case 1 -> p.getAbiGp1();
                default -> throw VMError.shouldNotReachHereAtRuntime();
            };
        }

        @Override
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public long getFpResultAt(Pointer data, int index) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            return switch (index) {
                case 0 -> p.getAbiFpArg0();
                case 1 -> p.getAbiFpArg1();
                default -> throw VMError.shouldNotReachHereAtRuntime();
            };
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
     * transition restores the source-frame stack/base pointers, recreates the original return
     * address edge, and jumps to the interpreter deoptimization entry point.
     */
    public static class InterpreterEntryPointStubFrameContext extends SubstrateAMD64Backend.SubstrateAMD64FrameContext {
        public InterpreterEntryPointStubFrameContext(SharedMethod method, CallingConvention callingConvention) {
            super(method, callingConvention);
        }

        @Override
        public void enter(CompilationResultBuilder tasm) {
            /*
             * Keep this otherwise-empty entrypoint walkable (including Windows unwind expectations)
             * by reporting a minimal frame: return-address slot only.
             */
            tasm.setTotalFrameSize(FrameAccess.returnAddressSize());
        }

        @Override
        public void leave(CompilationResultBuilder tasm) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;

            RegisterConfig registerConfig = tasm.frameMap.getRegisterConfig();

            /* leave arg0 untouched, it's the first argument to the interpreter entry point */

            Register regRevertSp = ValueUtil.asRegister(callingConvention.getArgument(1));
            Register regInterpEntryPoint = ValueUtil.asRegister(callingConvention.getArgument(2));
            Register regOldReturnAddress = ValueUtil.asRegister(callingConvention.getArgument(3));
            Register regOldBasePointer = ValueUtil.asRegister(callingConvention.getArgument(4));

            if (((SubstrateAMD64RegisterConfig) registerConfig).shouldUseBasePointer()) {
                asm.movq(rbp, regOldBasePointer);
            }

            /*
             * Keep every IP in this epilogue walkable (notably on Windows): materialize the
             * synthetic return edge first, then make stack-pointer restoration the last state
             * change before the tail jump.
             *
             * This avoids a transient state where rsp already points to revertSp but the synthetic
             * return-address slot is not initialized yet.
             */
            /*
             * regRevertSp is the caller SP after the deoptimized frame is removed. The active
             * return-address slot for that SP is [regRevertSp - returnAddressSize()], so write the
             * original caller return PC there using regOldReturnAddress.
             */
            asm.movq(asm.makeAddress(regRevertSp, -FrameAccess.returnAddressSize()), regOldReturnAddress);
            /*
             * Set rsp to that exact slot address (not to regRevertSp): the jump target and stack
             * walkers expect [rsp] to be the current return-address word.
             */
            asm.leaq(rsp, asm.makeAddress(regRevertSp, -FrameAccess.returnAddressSize()));

            asm.jmp(regInterpEntryPoint);
        }
    }
}
