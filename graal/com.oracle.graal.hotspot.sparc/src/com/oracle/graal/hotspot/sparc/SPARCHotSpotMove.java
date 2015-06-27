/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static jdk.internal.jvmci.code.ValueUtil.*;
import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.hotspot.HotSpotVMConfig.CompressEncoding;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.Annul;
import com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict;
import com.oracle.graal.asm.sparc.SPARCAssembler.CC;
import com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag;
import com.oracle.graal.asm.sparc.SPARCAssembler.RCondition;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;

public class SPARCHotSpotMove {
    public static final class CompressPointer extends SPARCLIRInstruction {
        public static final LIRInstructionClass<CompressPointer> TYPE = LIRInstructionClass.create(CompressPointer.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(5);

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public CompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
            super(TYPE, SIZE);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Register inputRegister = asRegister(input);
            Register resReg = asRegister(result);
            if (encoding.base != 0) {
                Register baseReg = asRegister(baseRegister);
                if (!nonNull) {
                    masm.cmp(inputRegister, baseReg);
                    masm.movcc(ConditionFlag.Equal, CC.Xcc, baseReg, resReg);
                    masm.sub(resReg, baseReg, resReg);
                } else {
                    masm.sub(inputRegister, baseReg, resReg);
                }
                if (encoding.shift != 0) {
                    masm.srlx(resReg, encoding.shift, resReg);
                }
            } else {
                masm.srlx(inputRegister, encoding.shift, resReg);
            }
        }
    }

    public static final class UncompressPointer extends SPARCLIRInstruction {
        public static final LIRInstructionClass<UncompressPointer> TYPE = LIRInstructionClass.create(UncompressPointer.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(4);

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public UncompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
            super(TYPE, SIZE);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Register inputRegister = asRegister(input);
            Register resReg = asRegister(result);
            Register secondaryInput;
            if (encoding.shift != 0) {
                masm.sll(inputRegister, encoding.shift, resReg);
                secondaryInput = resReg;
            } else {
                secondaryInput = inputRegister;
            }

            if (encoding.base != 0) {
                if (nonNull) {
                    masm.add(secondaryInput, asRegister(baseRegister), resReg);
                } else {
                    Label done = new Label();
                    masm.bpr(RCondition.Rc_nz, Annul.ANNUL, done, BranchPredict.PREDICT_TAKEN, secondaryInput);
                    masm.add(asRegister(baseRegister), secondaryInput, resReg);
                    masm.bind(done);
                }
            }
        }
    }

}
