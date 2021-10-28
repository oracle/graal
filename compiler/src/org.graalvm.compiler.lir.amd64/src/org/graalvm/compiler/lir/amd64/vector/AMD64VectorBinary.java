/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.amd64.vector;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PS;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRRIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;

public class AMD64VectorBinary {

    public static final class AVXBinaryOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVXBinaryOp> TYPE = LIRInstructionClass.create(AVXBinaryOp.class);

        @Opcode private final VexRVMOp opcode;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({REG, STACK}) protected AllocatableValue y;

        public AVXBinaryOp(VexRVMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            super(TYPE, size);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(y)) {
                opcode.emit(masm, size, asRegister(result), asRegister(x), asRegister(y));
            } else {
                opcode.emit(masm, size, asRegister(result), asRegister(x), (AMD64Address) crb.asAddress(y));
            }
        }
    }

    public static final class AVXBinaryConstOp extends AMD64VectorInstruction {

        public static final LIRInstructionClass<AVXBinaryConstOp> TYPE = LIRInstructionClass.create(AVXBinaryConstOp.class);

        @Opcode private final VexRRIOp opcode;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        protected int y;

        public AVXBinaryConstOp(VexRRIOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, int y) {
            super(TYPE, size);
            assert (y & 0xFF) == y;
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            opcode.emit(masm, size, asRegister(result), asRegister(x), y);
        }
    }

    public static final class AVXBinaryConstFloatOp extends AMD64VectorInstruction {

        public static final LIRInstructionClass<AVXBinaryConstFloatOp> TYPE = LIRInstructionClass.create(AVXBinaryConstFloatOp.class);

        @Opcode private final VexRVMOp opcode;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        protected ConstantValue y;

        public AVXBinaryConstFloatOp(VexRVMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, ConstantValue y) {
            super(TYPE, size);
            assert y.getPlatformKind() == AMD64Kind.SINGLE || y.getPlatformKind() == AMD64Kind.DOUBLE;
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (y.getPlatformKind() == AMD64Kind.SINGLE) {
                opcode.emit(masm, size, asRegister(result), asRegister(x), (AMD64Address) crb.asFloatConstRef(y.getJavaConstant(), opcode.isPacked() ? PS.getBytes() : 4));
            } else {
                assert y.getPlatformKind() == AMD64Kind.DOUBLE;
                opcode.emit(masm, size, asRegister(result), asRegister(x), (AMD64Address) crb.asDoubleConstRef(y.getJavaConstant(), opcode.isPacked() ? PD.getBytes() : 8));
            }
        }
    }

    public static final class AVXBinaryMemoryOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVXBinaryMemoryOp> TYPE = LIRInstructionClass.create(AVXBinaryMemoryOp.class);

        @Opcode private final VexRVMOp opcode;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({COMPOSITE}) protected AMD64AddressValue y;
        @State protected LIRFrameState state;

        public AVXBinaryMemoryOp(VexRVMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, AMD64AddressValue y, LIRFrameState state) {
            super(TYPE, size);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            opcode.emit(masm, size, asRegister(result), asRegister(x), y.toAddress());
        }
    }
}
