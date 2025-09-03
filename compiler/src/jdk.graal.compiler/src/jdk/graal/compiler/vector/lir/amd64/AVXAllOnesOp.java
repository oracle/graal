/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.VCMPPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.VCMPPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.VCMPSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.VCMPSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.Predicate.TRUE_UQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPTERNLOGD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPEQD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp.KXNORB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp.KXNORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp.KXNORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp.KXNORW;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.vector.nodes.simd.SimdConstant;

import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.StandardOp.LoadConstantOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;

public final class AVXAllOnesOp extends AMD64VectorInstruction implements LoadConstantOp {
    public static final LIRInstructionClass<AVXAllOnesOp> TYPE = LIRInstructionClass.create(AVXAllOnesOp.class);

    @Def({REG}) AllocatableValue result;
    private final Constant input;

    public AVXAllOnesOp(AllocatableValue result, Constant input) {
        super(TYPE, AVXKind.getRegisterSize(result));
        assert SimdConstant.isAllOnes(input) : input;
        this.result = result;
        this.input = input;
    }

    @Override
    public AllocatableValue getResult() {
        return result;
    }

    @Override
    public Constant getConstant() {
        return input;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Kind kind = (AMD64Kind) result.getPlatformKind();

        if (kind.isMask()) {
            switch (kind.getSizeInBytes()) {
                case Long.BYTES -> KXNORQ.emit(masm, asRegister(result), AMD64.k0, AMD64.k0);
                case Integer.BYTES -> KXNORD.emit(masm, asRegister(result), AMD64.k0, AMD64.k0);
                case Short.BYTES -> KXNORW.emit(masm, asRegister(result), AMD64.k0, AMD64.k0);
                default -> KXNORB.emit(masm, asRegister(result), AMD64.k0, AMD64.k0);
            }
            return;
        }

        Register register = asRegister(result);
        AVXKind.AVXSize avxSize = AVXKind.getRegisterSize(kind);
        if (EVPTERNLOGD.isSupported(masm, avxSize)) {
            /**
             * To receive a ZMM register, AVX512F must be supported, which is enough for VPTERNLOGD
             * to be applicable. If we receive a non ZMM sized AVX512 register, we can safely
             * conclude that AVX512F_VL is supported, in which case this branch is executed. With
             * this we can conclude that for any AVX512 register EVPTERNLOGD is emitted.
             */
            EVPTERNLOGD.emit(masm, avxSize, register, register, register, 0xFF);
        } else if (kind.getScalar().isInteger() && VPCMPEQD.isSupported(masm, avxSize)) {
            VPCMPEQD.emit(masm, avxSize, register, register, register);
        } else {
            VexFloatCompareOp op;
            if (kind == AMD64Kind.SINGLE) {
                op = VCMPSS;
            } else if (kind == AMD64Kind.DOUBLE) {
                op = VCMPSD;
            } else if (kind.getScalar() == AMD64Kind.SINGLE) {
                op = VCMPPS;
            } else {
                assert kind.getScalar() == AMD64Kind.DOUBLE || kind.getScalar().isInteger() : kind;
                // in case that we don't support the integer instruction, we use this
                // instruction as a fall back (it can cause a delay of a few cycles, but it is
                // the best we can do)
                op = VCMPPD;
            }
            op.emit(masm, AVXKind.getRegisterSize(kind), register, register, register, TRUE_UQ);
        }
    }

    @Override
    public boolean canRematerializeToStack() {
        return false;
    }
}
