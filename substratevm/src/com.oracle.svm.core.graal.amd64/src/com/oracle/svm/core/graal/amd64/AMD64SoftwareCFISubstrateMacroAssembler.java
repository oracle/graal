/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.rsp;

import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.InvokeTarget;

/**
 * Overrides assembler operations that need extra functionality for software CFI support.
 */
public class AMD64SoftwareCFISubstrateMacroAssembler extends SubstrateAMD64MacroAssembler {

    private static final int ENDBR64_ENCODING = 0xfa1e0ff3;
    private static final int SCRATCH_REGISTER_SPILL_OFFSET = -16;
    private final boolean checkNativeTransitions;
    /** Jump target for the trap instruction ({@code int3}) when failing a CFI check. */
    private Label targetCheckFail;

    public AMD64SoftwareCFISubstrateMacroAssembler(TargetDescription target, OptionValues optionValues, boolean hasIntelJccErratum) {
        super(target, optionValues, hasIntelJccErratum);
        assert SubstrateControlFlowIntegrity.useSoftwareCFI() : "Software CFI support expected";
        VMError.guarantee(target.linuxOs || target.macOs, "Target ABI might not guarantee a safe zone below the stack pointer for spilling in branch target validation");
        checkNativeTransitions = SubstrateControlFlowIntegrity.singleton().getCFIMode() == SubstrateControlFlowIntegrity.CFIOptions.SW;
        targetCheckFail = null;
    }

    /**
     * Branch target validation checks whether the instruction at the target address is an ENDBR64.
     * If it is not, execution traps. To avoid introducing another ENDBR64 encoding in the code as
     * an immediate, the comparison uses an add with the negative of ENDBR64.
     */
    private void validateBranchTarget(Register target, boolean restoreScratchRegister) {
        Register scratchRegister = target.equals(r10) ? r11 : r10;
        if (restoreScratchRegister) {
            /*
             * Writing below the stack pointer is safe if the ABI guarantees a safe zone that is not
             * written by interrupts or signal handlers. AMD64 System V provides such a zone.
             *
             * In the context of a return, rsp already points to the caller frame and the popped
             * return address remains at [rsp - 8]. Spill into the next word so that asynchronous
             * stack walkers can still find the return address throughout target validation.
             */
            movq(makeAddress(rsp, SCRATCH_REGISTER_SPILL_OFFSET), scratchRegister);
        }
        movl(scratchRegister, new AMD64Address(target, 0));
        addl(scratchRegister, -ENDBR64_ENCODING);
        if (targetCheckFail == null) {
            targetCheckFail = new Label();
        }
        jcc(ConditionFlag.NotZero, targetCheckFail);
        if (restoreScratchRegister) {
            movq(scratchRegister, makeAddress(rsp, SCRATCH_REGISTER_SPILL_OFFSET));
        }
    }

    @Override
    public void jmp(Register target) {
        validateBranchTarget(target, true);
        super.jmp(target);
    }

    public void jmpNoValidate(Register target) {
        super.jmp(target);
    }

    public void jmp(Register returnTargetRegister, boolean restoreScratchRegister) {
        validateBranchTarget(returnTargetRegister, restoreScratchRegister);
        super.jmp(returnTargetRegister);
    }

    @Override
    public void jmp(AMD64Address address) {
        throw VMError.shouldNotReachHere("No memory-indirect jumps with software CFI");
    }

    @Override
    public void ret(int imm16) {
        throw VMError.shouldNotReachHere("No returns with software CFI");
    }

    @Override
    public int directJmp(long address, Register scratch) {
        throw VMError.shouldNotReachHere("No register-indirect direct jump with software CFI");
    }

    private boolean skipCallValidation(InvokeTarget callTarget, CallingConvention.Type callingConvention) {
        if (!checkNativeTransitions) {
            if (callingConvention != null) {
                return ((SubstrateCallingConventionType) callingConvention).nativeABI();
            }
            if (callTarget instanceof SharedMethod sharedMethod && sharedMethod.getCallingConventionKind().isNativeABI()) {
                return true;
            }
            if (callTarget instanceof SubstrateForeignCallLinkage linkage &&
                            ((SharedMethod) linkage.getMethod()).getCallingConventionKind().isNativeABI()) {
                return true;
            }
        }
        return false;
    }

    private static boolean restoreScratchRegisterAfterValidation(InvokeTarget callTarget) {
        return callTarget instanceof SharedMethod sharedMethod && sharedMethod.hasCalleeSavedRegisters();
    }

    private PostCallAction wrapPostCallAction(PostCallAction original, InvokeTarget callTarget) {
        return wrapPostCallAction(original, callTarget, null);
    }

    private PostCallAction wrapPostCallAction(PostCallAction original, InvokeTarget callTarget, CallingConvention.Type callingConventionType) {
        if (skipCallValidation(callTarget, callingConventionType)) {
            return original;
        }
        if (original == PostCallAction.NONE) {
            return (_, _) -> endbranch();
        }
        return (before, after) -> {
            endbranch();
            original.apply(before, after);
        };
    }

    @Override
    public int directCall(PostCallAction postCallAction, long address, Register scratch, InvokeTarget callTarget) {
        PostCallAction wrappedPostCallAction = wrapPostCallAction(postCallAction, callTarget);
        if (skipCallValidation(callTarget, null)) {
            return super.directCall(wrappedPostCallAction, address, scratch, callTarget);
        }

        movq(scratch, address);
        validateBranchTarget(scratch, restoreScratchRegisterAfterValidation(callTarget));
        int bytesToEmit = needsRex(scratch) ? 3 : 2;
        mitigateJCCErratum(bytesToEmit);
        int beforeCall = position();
        call(scratch);
        int afterCall = position();
        assert beforeCall + bytesToEmit == afterCall : Assertions.errorMessage(beforeCall, bytesToEmit, afterCall);
        if (wrappedPostCallAction != PostCallAction.NONE) {
            wrappedPostCallAction.apply(beforeCall, afterCall);
        }
        return beforeCall;
    }

    @Override
    public int call(PostCallAction postCallAction, InvokeTarget callTarget) {
        return super.call(wrapPostCallAction(postCallAction, callTarget), callTarget);
    }

    @Override
    public int indirectCall(PostCallAction postCallAction, Register callReg, boolean mitigateDecodingAsDirectCall, InvokeTarget callTarget, CallingConvention.Type callingConventionType) {
        if (!skipCallValidation(callTarget, callingConventionType)) {
            validateBranchTarget(callReg, restoreScratchRegisterAfterValidation(callTarget));
        }
        return super.indirectCall(wrapPostCallAction(postCallAction, callTarget, callingConventionType), callReg, mitigateDecodingAsDirectCall, callTarget, callingConventionType);
    }

    @Override
    public void maybeEmitIndirectTargetMarker() {
        assert SubstrateControlFlowIntegrity.useSoftwareCFI() : "Software CFI should be enabled";
        endbranch();
    }

    @Override
    public void maybeEmitIndirectTargetMarker(CompilationResultBuilder crb, Label label) {
        assert SubstrateControlFlowIntegrity.useSoftwareCFI() : "Software CFI should be enabled";
        if (crb.getLIR().getBlockById(label.getBlockId()).isIndirectBranchTarget()) {
            endbranch();
        }
    }

    private void maybeEmitCFIInterrupt() {
        if (targetCheckFail != null) {
            bind(targetCheckFail);
            int3();
            hlt();
        }
    }

    @Override
    public byte[] closeAligned(boolean trimmedCopy, int alignment) {
        maybeEmitCFIInterrupt();
        return super.closeAligned(trimmedCopy, alignment);
    }

    @Override
    public void reset() {
        maybeEmitCFIInterrupt();
        super.reset();
        targetCheckFail = null;
    }
}
