/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.aarch64;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import com.oracle.graal.asm.aarch64.AArch64Address;
import com.oracle.graal.asm.aarch64.AArch64MacroAssembler;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.StandardOp.ImplicitNullCheck;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * AARCH64 LIR instructions that have one input and one output.
 */
public class AArch64Unary {

    /**
     * Instruction with a {@link AArch64AddressValue memory} operand.
     */
    public static class MemoryOp extends AArch64LIRInstruction implements ImplicitNullCheck {
        public static final LIRInstructionClass<MemoryOp> TYPE = LIRInstructionClass.create(MemoryOp.class);

        private final boolean isSigned;

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected AArch64AddressValue input;

        @State protected LIRFrameState state;

        private int targetSize;
        private int srcSize;

        public MemoryOp(boolean isSigned, int targetSize, int srcSize, AllocatableValue result, AArch64AddressValue input, LIRFrameState state) {
            super(TYPE);
            this.targetSize = targetSize;
            this.srcSize = srcSize;
            this.isSigned = isSigned;
            this.result = result;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            AArch64Address address = input.toAddress();
            Register dst = asRegister(result);
            if (isSigned)
                masm.ldrs(targetSize, srcSize, dst, address);
            else
                masm.ldr(srcSize, dst, address);
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            int immediate = input.getImmediate();
            if (state == null && value.equals(input.getBase()) && input.getOffset().equals(Value.ILLEGAL) && immediate >= 0 && immediate < implicitNullCheckLimit) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }
}
