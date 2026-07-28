/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64.vector;

import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B0;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z0;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z1;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexIntegerCompareOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Masked operations from AVX512, they are in the form {@code op dst{mask}, src...}. E.g.
 * {@code vpaddd zmm0{k1}, zmm1, zmm2} adds dword elements from {@code zmm1} and {@code zmm2}. The
 * results are put into {@code zmm0} for each element at which the corresponding element in
 * {@code k1} is set. The elements at which the corresponding element in {@code k1} is unset are
 * unchanged by the operation; if {@code {z}} is additionally specified, those elements will be
 * cleared.
 */
public class AVX512MaskedOp {

    /**
     * Merge-masking operations, the elements not selected by {@code mask} are taken from {@code
     * background}.
     */
    public static class AVX512MaskedMergeOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVX512MaskedMergeOp> TYPE = LIRInstructionClass.create(AVX512MaskedMergeOp.class);

        @Opcode private final VexOp op;
        @Def({OperandFlag.REG, OperandFlag.HINT}) protected AllocatableValue result;
        @Use(OperandFlag.REG) protected AllocatableValue background;
        @Alive(OperandFlag.REG) protected AllocatableValue mask;
        @Alive({OperandFlag.REG, OperandFlag.ILLEGAL}) AllocatableValue src1;
        @Alive({OperandFlag.REG, OperandFlag.STACK}) AllocatableValue src2;

        public AVX512MaskedMergeOp(VexOp op, AVXKind.AVXSize avxSize, AllocatableValue result, AllocatableValue background, AllocatableValue mask, AllocatableValue src1, AllocatableValue src2) {
            super(TYPE, avxSize);
            this.op = op;
            this.result = result;
            this.background = background;
            this.mask = mask;
            this.src1 = src1;
            this.src2 = src2;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            new AMD64VectorMove.MoveToRegOp(result, background, AMD64Assembler.AMD64SIMDInstructionEncoding.EVEX).emitCode(crb, masm);
            emitMaskedMerge(crb, masm, op, size, result, mask, src1, src2);
        }
    }

    /**
     * Merge-masking operations where {@code background} is also the first arithmetic input.
     */
    public static class AVX512MaskedInPlaceMergeOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVX512MaskedInPlaceMergeOp> TYPE = LIRInstructionClass.create(AVX512MaskedInPlaceMergeOp.class);

        @Opcode private final VexOp op;
        @Def({OperandFlag.REG, OperandFlag.HINT}) protected AllocatableValue result;
        @Use(OperandFlag.REG) protected AllocatableValue background;
        @Alive(OperandFlag.REG) protected AllocatableValue mask;
        @Alive({OperandFlag.REG, OperandFlag.STACK}) AllocatableValue src2;

        public AVX512MaskedInPlaceMergeOp(VexOp op, AVXKind.AVXSize avxSize, AllocatableValue result, AllocatableValue background, AllocatableValue mask, AllocatableValue src2) {
            super(TYPE, avxSize);
            this.op = op;
            this.result = result;
            this.background = background;
            this.mask = mask;
            this.src2 = src2;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            new AMD64VectorMove.MoveToRegOp(result, background, AMD64Assembler.AMD64SIMDInstructionEncoding.EVEX).emitCode(crb, masm);
            emitMaskedMerge(crb, masm, op, size, result, mask, result, src2);
        }
    }

    private static void emitMaskedMerge(CompilationResultBuilder crb, AMD64MacroAssembler masm, VexOp op, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue mask,
                    AllocatableValue src1, AllocatableValue src2) {
        if (isRegister(src2)) {
            switch (op) {
                case VexRMOp c -> c.emit(masm, size, asRegister(result), asRegister(src2), asRegister(mask), Z0, B0);
                case VexRVMOp c -> c.emit(masm, size, asRegister(result), asRegister(src1), asRegister(src2), asRegister(mask), Z0, B0);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(op);
            }
        } else {
            AMD64Address src2Address = (AMD64Address) crb.asAddress(src2);
            switch (op) {
                case VexRMOp c -> c.emit(masm, size, asRegister(result), src2Address, asRegister(mask), Z0, B0);
                case VexRVMOp c -> c.emit(masm, size, asRegister(result), asRegister(src1), src2Address, asRegister(mask), Z0, B0);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(op);
            }
        }
    }

    /**
     * Zero-masking operations, the unset elements in {@code mask} are cleared.
     */
    public static class AVX512MaskedZeroOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVX512MaskedZeroOp> TYPE = LIRInstructionClass.create(AVX512MaskedZeroOp.class);

        @Opcode private final VexOp op;
        // Predicate for comparisons
        private final Object predicate;
        @Def protected AllocatableValue result;
        @Use protected AllocatableValue mask;
        @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) AllocatableValue src1;
        @Use({OperandFlag.REG, OperandFlag.STACK}) AllocatableValue src2;

        public AVX512MaskedZeroOp(VexOp op, Object predicate, AVXKind.AVXSize avxSize, AllocatableValue result, AllocatableValue mask, AllocatableValue src1, AllocatableValue src2) {
            super(TYPE, avxSize);
            this.op = op;
            this.predicate = predicate;
            this.result = result;
            this.mask = mask;
            this.src1 = src1;
            this.src2 = src2;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(src2)) {
                switch (op) {
                    case VexRMOp c -> c.emit(masm, size, asRegister(result), asRegister(src2), asRegister(mask), Z1, B0);
                    case VexRVMOp c -> c.emit(masm, size, asRegister(result), asRegister(src1), asRegister(src2), asRegister(mask), Z1, B0);
                    case VexIntegerCompareOp c -> c.emit(masm, size, asRegister(result), asRegister(src1), asRegister(src2), asRegister(mask), (VexIntegerCompareOp.Predicate) predicate);
                    case VexFloatCompareOp c -> c.emit(masm, size, asRegister(result), asRegister(src1), asRegister(src2), asRegister(mask), (VexFloatCompareOp.Predicate) predicate);
                    default -> throw GraalError.shouldNotReachHereUnexpectedValue(op);
                }
            } else {
                AMD64Address src2Address = (AMD64Address) crb.asAddress(src2);
                switch (op) {
                    case VexRMOp c -> c.emit(masm, size, asRegister(result), src2Address, asRegister(mask), Z1, B0);
                    case VexRVMOp c -> c.emit(masm, size, asRegister(result), asRegister(src1), src2Address, asRegister(mask), Z1, B0);
                    case VexIntegerCompareOp c -> c.emit(masm, size, asRegister(result), asRegister(src1), src2Address, asRegister(mask), (VexIntegerCompareOp.Predicate) predicate, B0);
                    case VexFloatCompareOp c -> c.emit(masm, size, asRegister(result), asRegister(src1), src2Address, asRegister(mask), (VexFloatCompareOp.Predicate) predicate, B0);
                    default -> throw GraalError.shouldNotReachHereUnexpectedValue(op);
                }
            }
        }
    }

    /**
     * Merge-masking FMA operations where the first FMA input is also the background.
     */
    public static class AVX512MaskedFmaOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVX512MaskedFmaOp> TYPE = LIRInstructionClass.create(AVX512MaskedFmaOp.class);

        @Opcode private final VexRVMOp op;
        @Def({OperandFlag.REG, OperandFlag.HINT}) protected AllocatableValue result;
        @Use(OperandFlag.REG) protected AllocatableValue src1;
        @Alive(OperandFlag.REG) protected AllocatableValue mask;
        @Alive(OperandFlag.REG) protected AllocatableValue src2;
        @Alive({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue src3;

        public AVX512MaskedFmaOp(VexRVMOp op, AVXKind.AVXSize avxSize, AllocatableValue result, AllocatableValue mask, AllocatableValue src1, AllocatableValue src2, AllocatableValue src3) {
            super(TYPE, avxSize);
            this.op = op;
            this.result = result;
            this.mask = mask;
            this.src1 = src1;
            this.src2 = src2;
            this.src3 = src3;
            GraalError.guarantee(isFma213Op(op), "expected known FMA 213 op, got: %s", op);
        }

        private static boolean isFma213Op(VexRVMOp op) {
            return op == VexRVMOp.EVFMADD213PS || op == VexRVMOp.EVFMADD213PD;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            new AMD64VectorMove.MoveToRegOp(result, src1, AMD64Assembler.AMD64SIMDInstructionEncoding.EVEX).emitCode(crb, masm);
            if (isRegister(src3)) {
                op.emit(masm, size, asRegister(result), asRegister(src2), asRegister(src3), asRegister(mask), Z0, B0);
            } else {
                AMD64Address src3Address = (AMD64Address) crb.asAddress(src3);
                op.emit(masm, size, asRegister(result), asRegister(src2), src3Address, asRegister(mask), Z0, B0);
            }
        }
    }
}
