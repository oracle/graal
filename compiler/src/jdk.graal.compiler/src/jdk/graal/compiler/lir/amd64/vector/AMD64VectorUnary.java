/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexRMIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public class AMD64VectorUnary {

    public static final class AVXUnaryOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVXUnaryOp> TYPE = LIRInstructionClass.create(AVXUnaryOp.class);

        @Opcode private final VexRMOp opcode;

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue input;

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

    public static final class AVXRegisterOnlyUnaryOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVXRegisterOnlyUnaryOp> TYPE = LIRInstructionClass.create(AVXRegisterOnlyUnaryOp.class);

        @Opcode private final VexRROp opcode;

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue input;

        public AVXRegisterOnlyUnaryOp(VexRROp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue input) {
            super(TYPE, size);
            this.opcode = opcode;
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            opcode.emit(masm, size, asRegister(result), asRegister(input));
        }
    }

    public static final class AVXUnaryRVMOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVXUnaryRVMOp> TYPE = LIRInstructionClass.create(AVXUnaryRVMOp.class);

        @Opcode private final VexRVMOp opcode;

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue input;

        public AVXUnaryRVMOp(VexRVMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue input) {
            super(TYPE, size);
            this.opcode = opcode;
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(input)) {
                opcode.emit(masm, size, asRegister(result), asRegister(input), asRegister(input));
            } else {
                VexRVMOp.VXORPD.emit(masm, size, asRegister(result), asRegister(result), asRegister(result));
                opcode.emit(masm, size, asRegister(result), asRegister(result), (AMD64Address) crb.asAddress(input));
            }
        }
    }

    public static final class AVXUnaryMemoryOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVXUnaryMemoryOp> TYPE = LIRInstructionClass.create(AVXUnaryMemoryOp.class);

        @Opcode private final VexRMOp opcode;

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.COMPOSITE}) protected AMD64AddressValue input;
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

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.STACK, OperandFlag.CONST}) protected Value input;
        @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) protected AllocatableValue opmask;

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
            } else if (LIRValueUtil.isConstantValue(input)) {
                int align = crb.dataBuilder.ensureValidDataAlignment(input.getPlatformKind().getSizeInBytes());
                AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(LIRValueUtil.asConstant(input), align);
                opcode.emit(masm, size, asRegister(result), address, getOpmaskRegister(), z, b);
            } else {
                opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(input), getOpmaskRegister(), z, b);
            }
        }
    }

    public static final class AVXConvertMemoryOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<AVXConvertMemoryOp> TYPE = LIRInstructionClass.create(AVXConvertMemoryOp.class);

        @Opcode private final VexRVMOp opcode;

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.COMPOSITE}) protected AMD64AddressValue input;
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

        @Opcode private final VexRVMConvertOp opcode;
        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue input;

        public AVXConvertOp(VexRVMConvertOp opcode, AllocatableValue result, AllocatableValue input) {
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
                if (AMD64.XMM.equals(asRegister(input).getRegisterCategory())) {
                    opcode.emit(masm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(input), asRegister(input));
                } else {
                    // clear result register to avoid unnecessary dependency
                    VexRVMOp.VXORPD.emit(masm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), asRegister(result));
                    opcode.emit(masm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), asRegister(input));
                }
            } else {
                VexRVMOp.VXORPD.emit(masm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), asRegister(result));
                opcode.emit(masm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), (AMD64Address) crb.asAddress(input));
            }
        }
    }

    public static final class FloatPointClassTestOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<FloatPointClassTestOp> TYPE = LIRInstructionClass.create(FloatPointClassTestOp.class);

        // @formatter:off
        public static final int QUIET_NAN = 0b00000001;
        public static final int POS_ZERO  = 0b00000010;
        public static final int NEG_ZERO  = 0b00000100;
        public static final int POS_INF   = 0b00001000;
        public static final int NEG_INF   = 0b00010000;
        public static final int DENORMAL  = 0b00100000;
        public static final int FIN_NEG   = 0b01000000;
        public static final int SIG_NAN   = 0b10000000;
        // @formatter:on

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        @Use({OperandFlag.REG}) protected AllocatableValue input;

        @Temp({OperandFlag.REG}) protected AllocatableValue maskTemp;

        private final OperandSize size;
        private final int imm8;

        public FloatPointClassTestOp(LIRGeneratorTool tool, OperandSize size, int imm8, AllocatableValue result, AllocatableValue input) {
            super(TYPE);

            this.result = result;
            this.input = input;
            this.size = size;
            this.imm8 = imm8;
            this.maskTemp = tool.newVariable(LIRKind.value(AMD64Kind.MASK8));
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            switch (size) {
                case SS:
                    EvexRMIOp.VFPCLASSSS.emit(masm, AVXKind.AVXSize.XMM, asRegister(maskTemp), asRegister(input), imm8);
                    break;
                case SD:
                    EvexRMIOp.VFPCLASSSD.emit(masm, AVXKind.AVXSize.XMM, asRegister(maskTemp), asRegister(input), imm8);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("unsupported operand size " + size);
            }
            masm.kmovb(asRegister(result), asRegister(maskTemp));
        }
    }
}
