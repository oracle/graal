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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.HINT;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import static jdk.internal.jvmci.code.ValueUtil.isRegister;
import static jdk.internal.jvmci.code.ValueUtil.isStackSlot;
import jdk.internal.jvmci.amd64.AMD64Kind;
import jdk.internal.jvmci.code.Register;
import jdk.internal.jvmci.code.StackSlotValue;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.hotspot.HotSpotMetaspaceConstant;
import jdk.internal.jvmci.hotspot.HotSpotObjectConstant;
import jdk.internal.jvmci.hotspot.HotSpotVMConfig.CompressEncoding;
import jdk.internal.jvmci.meta.AllocatableValue;
import jdk.internal.jvmci.meta.Constant;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.amd64.AMD64Address;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.asm.amd64.AMD64MacroAssembler;
import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.StandardOp.LoadConstantOp;
import com.oracle.graal.lir.StandardOp.StackStoreOp;
import com.oracle.graal.lir.amd64.AMD64LIRInstruction;
import com.oracle.graal.lir.amd64.AMD64Move;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

public class AMD64HotSpotMove {

    public static final class HotSpotLoadObjectConstantOp extends AMD64LIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<HotSpotLoadObjectConstantOp> TYPE = LIRInstructionClass.create(HotSpotLoadObjectConstantOp.class);

        @Def({REG, STACK}) private AllocatableValue result;
        private final HotSpotObjectConstant input;

        public HotSpotLoadObjectConstantOp(AllocatableValue result, HotSpotObjectConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            boolean compressed = input.isCompressed();
            if (crb.target.inlineObjects) {
                crb.recordInlineDataInCode(input);
                if (isRegister(result)) {
                    if (compressed) {
                        masm.movl(asRegister(result), 0xDEADDEAD);
                    } else {
                        masm.movq(asRegister(result), 0xDEADDEADDEADDEADL);
                    }
                } else {
                    assert isStackSlot(result);
                    if (compressed) {
                        masm.movl((AMD64Address) crb.asAddress(result), 0xDEADDEAD);
                    } else {
                        throw JVMCIError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                    }
                }
            } else {
                if (isRegister(result)) {
                    AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(input, compressed ? 4 : 8);
                    if (compressed) {
                        masm.movl(asRegister(result), address);
                    } else {
                        masm.movq(asRegister(result), address);
                    }
                } else {
                    throw JVMCIError.shouldNotReachHere("Cannot directly store data patch to memory");
                }
            }
        }

        public Constant getConstant() {
            return input;
        }

        public AllocatableValue getResult() {
            return result;
        }
    }

    public static final class HotSpotLoadMetaspaceConstantOp extends AMD64LIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<HotSpotLoadMetaspaceConstantOp> TYPE = LIRInstructionClass.create(HotSpotLoadMetaspaceConstantOp.class);

        @Def({REG, STACK}) private AllocatableValue result;
        private final HotSpotMetaspaceConstant input;

        public HotSpotLoadMetaspaceConstantOp(AllocatableValue result, HotSpotMetaspaceConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            boolean compressed = input.isCompressed();
            boolean isImmutable = GraalOptions.ImmutableCode.getValue();
            boolean generatePIC = GraalOptions.GeneratePIC.getValue();
            if (isRegister(result)) {
                if (compressed) {
                    if (isImmutable && generatePIC) {
                        int alignment = crb.target.wordSize;
                        // recordDataReferenceInCode forces the mov to be rip-relative
                        masm.movl(asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(input, alignment));
                    } else {
                        crb.recordInlineDataInCode(input);
                        masm.movl(asRegister(result), 0xDEADDEAD);
                    }
                } else {
                    if (isImmutable && generatePIC) {
                        int alignment = crb.target.wordSize;
                        // recordDataReferenceInCode forces the mov to be rip-relative
                        masm.movq(asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(input, alignment));
                    } else {
                        crb.recordInlineDataInCode(input);
                        masm.movq(asRegister(result), 0xDEADDEADDEADDEADL);
                    }
                }
            } else {
                assert isStackSlot(result);
                if (compressed) {
                    if (isImmutable && generatePIC) {
                        throw JVMCIError.shouldNotReachHere("Unsupported operation offset(%rip) -> mem (mem -> mem)");
                    } else {
                        crb.recordInlineDataInCode(input);
                        masm.movl((AMD64Address) crb.asAddress(result), 0xDEADDEAD);
                    }
                } else {
                    throw JVMCIError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                }
            }
        }

        public Constant getConstant() {
            return input;
        }

        public AllocatableValue getResult() {
            return result;
        }
    }

    public static final class CompressPointer extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CompressPointer> TYPE = LIRInstructionClass.create(CompressPointer.class);

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public CompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(AMD64Kind.QWORD, crb, masm, result, input);

            Register resReg = asRegister(result);
            if (encoding.base != 0) {
                Register baseReg = asRegister(baseRegister);
                if (!nonNull) {
                    masm.testq(resReg, resReg);
                    masm.cmovq(ConditionFlag.Equal, resReg, baseReg);
                }
                masm.subq(resReg, baseReg);
            }

            if (encoding.shift != 0) {
                masm.shrq(resReg, encoding.shift);
            }
        }
    }

    public static final class StoreRbpOp extends AMD64LIRInstruction implements StackStoreOp {
        public static final LIRInstructionClass<StoreRbpOp> TYPE = LIRInstructionClass.create(StoreRbpOp.class);

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Def({STACK}) protected StackSlotValue stackSlot;

        protected StoreRbpOp(AllocatableValue result, AllocatableValue input, StackSlotValue stackSlot) {
            super(TYPE);
            assert result.getLIRKind().equals(input.getLIRKind()) && stackSlot.getLIRKind().equals(input.getLIRKind()) : String.format("result %s, input %s, stackSlot %s", result.getLIRKind(),
                            input.getLIRKind(), stackSlot.getLIRKind());
            this.result = result;
            this.input = input;
            this.stackSlot = stackSlot;
        }

        public Value getInput() {
            return input;
        }

        public AllocatableValue getResult() {
            return result;
        }

        public StackSlotValue getStackSlot() {
            return stackSlot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
            AMD64Move.move(kind, crb, masm, result, input);
            AMD64Move.move(kind, crb, masm, stackSlot, input);
        }
    }

    public static final class UncompressPointer extends AMD64LIRInstruction {
        public static final LIRInstructionClass<UncompressPointer> TYPE = LIRInstructionClass.create(UncompressPointer.class);

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public UncompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(AMD64Kind.DWORD, crb, masm, result, input);

            Register resReg = asRegister(result);
            if (encoding.shift != 0) {
                masm.shlq(resReg, encoding.shift);
            }

            if (encoding.base != 0) {
                if (nonNull) {
                    masm.addq(resReg, asRegister(baseRegister));
                } else {
                    if (encoding.shift == 0) {
                        // if encoding.shift != 0, the flags are already set by the shlq
                        masm.testq(resReg, resReg);
                    }

                    Label done = new Label();
                    masm.jccb(ConditionFlag.Equal, done);
                    masm.addq(resReg, asRegister(baseRegister));
                    masm.bind(done);
                }
            }
        }
    }

    public static void decodeKlassPointer(AMD64MacroAssembler masm, Register register, Register scratch, AMD64Address address, CompressEncoding encoding) {
        masm.movl(register, address);
        if (encoding.shift != 0) {
            assert encoding.alignment == encoding.shift : "Decode algorithm is wrong";
            masm.shlq(register, encoding.alignment);
        }
        if (encoding.base != 0) {
            masm.movq(scratch, encoding.base);
            masm.addq(register, scratch);
        }
    }
}
