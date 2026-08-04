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

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;

import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Opcode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * This class replaces the {@link AMD64ReturnOp} if software CFI is enabled. Instead of a
 * backward-edge return instruction, it emits a "forward"-edge pop+jmp sequence, which is no longer
 * a memory-indirect branch and allows for validating the branch target without the danger of
 * modification by a concurrently executing thread.
 *
 * For transitions to native code, no target check is performed with the
 * {@link com.oracle.svm.core.SubstrateControlFlowIntegrity.CFIOptions#SW_NONATIVE} option.
 */
@Opcode("CFI_RETURN")
public class AMD64CFIReturnOp extends AMD64ReturnOp {
    public static final LIRInstructionClass<AMD64CFIReturnOp> TYPE = LIRInstructionClass.create(AMD64CFIReturnOp.class);

    @Alive({REG, ILLEGAL}) Value returnValue;
    @Temp({REG, ILLEGAL}) AllocatableValue returnTarget;

    private final boolean validateReturn;
    /**
     * Stubs and calling conventions with return buffers must restore the contents of all (scratch)
     * registers before returning.
     */
    private final boolean calleeSavedScratchRegisters;

    public AMD64CFIReturnOp(Value x, boolean validateReturn, boolean calleeSavedScratchRegisters, AllocatableValue tailCallTarget, AllocatableValue[] additionalReturns) {
        super(TYPE, x, tailCallTarget, additionalReturns);
        this.returnValue = x;

        var cfiMode = SubstrateControlFlowIntegrity.singleton().getCFIMode();
        assert SubstrateControlFlowIntegrity.useSoftwareCFI() : cfiMode;

        this.validateReturn = validateReturn;
        this.calleeSavedScratchRegisters = calleeSavedScratchRegisters;
        /*
         * N.b., returnTarget must be a hard-coded value to ensure it does not conflict with the
         * calling convention.
         */
        this.returnTarget = SubstrateControlFlowIntegrity.singleton().getCFITargetRegister().asValue();
    }

    @Override
    protected void emitReturn(AMD64MacroAssembler masm) {
        Register returnTargetRegister = loadBranchTarget(masm);
        if (validateReturn) {
            if (SubstrateUtil.assertionsEnabled() && !isIllegal(x)) {
                assert LIRValueUtil.differentRegisters(returnValue, returnTarget) : Assertions.errorMessage(returnValue, returnTarget);
            }
            ((AMD64SoftwareCFISubstrateMacroAssembler) masm).jmp(returnTargetRegister, calleeSavedScratchRegisters);
        } else {
            ((AMD64SoftwareCFISubstrateMacroAssembler) masm).jmpNoValidate(returnTargetRegister);
        }
    }

    private Register loadBranchTarget(AMD64MacroAssembler masm) {
        Register returnTargetRegister = asRegister(returnTarget);
        if (tailCallTarget.equals(Value.ILLEGAL)) {
            masm.pop(returnTargetRegister);
        } else {
            Register tailCallTargetRegister = asRegister(tailCallTarget);
            if (!returnTargetRegister.equals(tailCallTargetRegister)) {
                masm.movq(returnTargetRegister, tailCallTargetRegister);
            }
        }
        return returnTargetRegister;
    }
}
