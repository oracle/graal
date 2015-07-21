/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static jdk.internal.jvmci.code.ValueUtil.*;
import static jdk.internal.jvmci.sparc.SPARC.*;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;

public final class SPARCBitManipulationOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCBitManipulationOp> TYPE = LIRInstructionClass.create(SPARCBitManipulationOp.class);

    public enum IntrinsicOpcode {
        IPOPCNT(SizeEstimate.create(2)),
        LPOPCNT(SizeEstimate.create(1)),
        IBSR(SizeEstimate.create(13)),
        LBSR(SizeEstimate.create(14)),
        BSF(SizeEstimate.create(4));

        final SizeEstimate size;

        private IntrinsicOpcode(SizeEstimate size) {
            this.size = size;
        }
    }

    @Opcode private final IntrinsicOpcode opcode;
    @Def protected AllocatableValue result;
    @Alive({REG}) protected AllocatableValue input;
    @Temp({REG}) protected Value scratch;

    public SPARCBitManipulationOp(IntrinsicOpcode opcode, AllocatableValue result, AllocatableValue input, LIRGeneratorTool gen) {
        super(TYPE, opcode.size);
        this.opcode = opcode;
        this.result = result;
        this.input = input;
        scratch = gen.newVariable(LIRKind.combine(input));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        Register dst = asIntReg(result);
        if (isRegister(input)) {
            Register src = asRegister(input);
            switch (opcode) {
                case IPOPCNT:
                    // clear upper word for 64 bit POPC
                    masm.srl(src, g0, dst);
                    masm.popc(dst, dst);
                    break;
                case LPOPCNT:
                    masm.popc(src, dst);
                    break;
                case BSF:
                    Kind tkind = input.getKind();
                    if (tkind == Kind.Int) {
                        masm.sub(src, 1, dst);
                        masm.andn(dst, src, dst);
                        masm.srl(dst, g0, dst);
                        masm.popc(dst, dst);
                    } else if (tkind == Kind.Long) {
                        masm.sub(src, 1, dst);
                        masm.andn(dst, src, dst);
                        masm.popc(dst, dst);
                    } else {
                        throw JVMCIError.shouldNotReachHere("missing: " + tkind);
                    }
                    break;
                case IBSR: {
                    Kind ikind = input.getKind();
                    assert ikind == Kind.Int;
                    Register tmp = asRegister(scratch);
                    assert !tmp.equals(dst);
                    masm.srl(src, 1, tmp);
                    masm.srl(src, 0, dst);
                    masm.or(dst, tmp, dst);
                    masm.srl(dst, 2, tmp);
                    masm.or(dst, tmp, dst);
                    masm.srl(dst, 4, tmp);
                    masm.or(dst, tmp, dst);
                    masm.srl(dst, 8, tmp);
                    masm.or(dst, tmp, dst);
                    masm.srl(dst, 16, tmp);
                    masm.or(dst, tmp, dst);
                    masm.popc(dst, dst);
                    masm.sub(dst, 1, dst);
                    break;
                }
                case LBSR: {
                    Kind lkind = input.getKind();
                    assert lkind == Kind.Long;
                    Register tmp = asRegister(scratch);
                    assert !tmp.equals(dst);
                    masm.srlx(src, 1, tmp);
                    masm.or(src, tmp, dst);
                    masm.srlx(dst, 2, tmp);
                    masm.or(dst, tmp, dst);
                    masm.srlx(dst, 4, tmp);
                    masm.or(dst, tmp, dst);
                    masm.srlx(dst, 8, tmp);
                    masm.or(dst, tmp, dst);
                    masm.srlx(dst, 16, tmp);
                    masm.or(dst, tmp, dst);
                    masm.srlx(dst, 32, tmp);
                    masm.or(dst, tmp, dst);
                    masm.popc(dst, dst);
                    masm.sub(dst, 1, dst); // This is required to fit the given structure.
                    break;
                }
                default:
                    throw JVMCIError.shouldNotReachHere();

            }
        } else if (isConstant(input) && isSimm13(crb.asIntConst(input))) {
            switch (opcode) {
                case IPOPCNT:
                    masm.popc(crb.asIntConst(input), dst);
                    break;
                case LPOPCNT:
                    masm.popc(crb.asIntConst(input), dst);
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        } else {
            throw JVMCIError.shouldNotReachHere();
        }
    }
}
