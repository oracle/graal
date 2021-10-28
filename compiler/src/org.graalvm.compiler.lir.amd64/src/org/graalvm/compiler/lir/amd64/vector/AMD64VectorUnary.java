/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public class AMD64VectorUnary {

    public static final class AVXUnaryOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVXUnaryOp> TYPE = LIRInstructionClass.create(AVXUnaryOp.class);

        @Opcode private final VexRMOp opcode;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue input;

        public AVXUnaryOp(VexRMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue input) {
            super(TYPE, size);
            this.opcode = opcode;
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(input)) {
                opcode.emit(masm, size, asRegister(result), asRegister(input));
            } else {
                opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(input));
            }
        }
    }

    public static final class AVXUnaryMemoryOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVXUnaryMemoryOp> TYPE = LIRInstructionClass.create(AVXUnaryMemoryOp.class);

        @Opcode private final VexRMOp opcode;

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected AMD64AddressValue input;
        @State protected LIRFrameState state;

        public AVXUnaryMemoryOp(VexRMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AMD64AddressValue input, LIRFrameState state) {
            super(TYPE, size);
            this.opcode = opcode;
            this.result = result;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            opcode.emit(masm, size, asRegister(result), input.toAddress());
        }
    }

    public static final class AVXBroadcastOp extends AMD64VectorInstruction implements AVX512Support {
        public static final LIRInstructionClass<AVXBroadcastOp> TYPE = LIRInstructionClass.create(AVXBroadcastOp.class);

        @Opcode private final VexRMOp opcode;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;
        @Use({REG, ILLEGAL}) protected AllocatableValue opmask;

        private final int z;
        private final int b;

        public AVXBroadcastOp(VexRMOp opcode, AVXKind.AVXSize size, AllocatableValue result, Value input) {
            this(opcode, size, result, input, Value.ILLEGAL, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        }

        public AVXBroadcastOp(VexRMOp opcode, AVXKind.AVXSize size, AllocatableValue result, Value input, AllocatableValue opmask, int z, int b) {
            super(TYPE, size);
            this.opcode = opcode;
            this.result = result;
            this.input = input;
            this.opmask = opmask;
            this.z = z;
            this.b = b;
        }

        @Override
        public AllocatableValue getOpmask() {
            return opmask;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(input)) {
                opcode.emit(masm, size, asRegister(result), asRegister(input));
            } else if (isConstantValue(input)) {
                int align = crb.dataBuilder.ensureValidDataAlignment(input.getPlatformKind().getSizeInBytes());
                AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(asConstant(input), align);
                opcode.emit(masm, size, asRegister(result), address, getOpmaskRegister(), z, b);
            } else {
                opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(input), getOpmaskRegister(), z, b);
            }
        }
    }

    public static final class AVXConvertMemoryOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVXConvertMemoryOp> TYPE = LIRInstructionClass.create(AVXConvertMemoryOp.class);

        @Opcode private final VexRVMOp opcode;

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected AMD64AddressValue input;
        @State protected LIRFrameState state;

        public AVXConvertMemoryOp(VexRVMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AMD64AddressValue input, LIRFrameState state) {
            super(TYPE, size);
            this.opcode = opcode;
            this.result = result;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            opcode.emit(masm, size, asRegister(result), asRegister(result), input.toAddress());
        }
    }

    public static final class AVXConvertOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AVXConvertOp> TYPE = LIRInstructionClass.create(AVXConvertOp.class);

        @Opcode private final VexRVMOp opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue input;

        public AVXConvertOp(VexRVMOp opcode, AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            // Note that we assume only XMM-size instructions are emitted here. Loosening this
            // restriction would require informing AMD64HotSpotReturnOp when emitting vzeroupper.
            if (isRegister(input)) {
                if (!asRegister(input).equals(asRegister(result))) {
                    // clear result register to avoid unnecessary dependency
                    VexRVMOp.VXORPD.emit(masm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), asRegister(result));
                }
                opcode.emit(masm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), asRegister(input));
            } else {
                VexRVMOp.VXORPD.emit(masm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), asRegister(result));
                opcode.emit(masm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), (AMD64Address) crb.asAddress(input));
            }
        }
    }
}
