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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

public class AMD64HotSpotMove {

    public static final class HotSpotLoadConstantOp extends AMD64LIRInstruction implements MoveOp {
        public static final LIRInstructionClass<HotSpotLoadConstantOp> TYPE = LIRInstructionClass.create(HotSpotLoadConstantOp.class);

        @Def({REG, STACK}) private AllocatableValue result;
        private final JavaConstant input;

        public HotSpotLoadConstantOp(AllocatableValue result, JavaConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(input)) {
                if (isRegister(result)) {
                    masm.movl(asRegister(result), 0);
                } else {
                    assert isStackSlot(result);
                    masm.movl((AMD64Address) crb.asAddress(result), 0);
                }
            } else if (input instanceof HotSpotObjectConstant) {
                boolean compressed = ((HotSpotObjectConstant) input).isCompressed();
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
                            throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
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
                        throw GraalInternalError.shouldNotReachHere("Cannot directly store data patch to memory");
                    }
                }
            } else if (input instanceof HotSpotMetaspaceConstant) {
                assert input.getKind() == Kind.Int || input.getKind() == Kind.Long;
                boolean compressed = input.getKind() == Kind.Int;
                boolean isImmutable = GraalOptions.ImmutableCode.getValue();
                boolean generatePIC = GraalOptions.GeneratePIC.getValue();
                crb.recordInlineDataInCode(input);
                if (isRegister(result)) {
                    if (compressed) {
                        if (isImmutable && generatePIC) {
                            Kind hostWordKind = HotSpotGraalRuntime.getHostWordKind();
                            int alignment = hostWordKind.getBitCount() / Byte.SIZE;
                            // recordDataReferenceInCode forces the mov to be rip-relative
                            masm.movl(asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(JavaConstant.INT_0, alignment));
                        } else {
                            masm.movl(asRegister(result), input.asInt());
                        }
                    } else {
                        if (isImmutable && generatePIC) {
                            Kind hostWordKind = HotSpotGraalRuntime.getHostWordKind();
                            int alignment = hostWordKind.getBitCount() / Byte.SIZE;
                            // recordDataReferenceInCode forces the mov to be rip-relative
                            masm.movq(asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(JavaConstant.INT_0, alignment));
                        } else {
                            masm.movq(asRegister(result), input.asLong());
                        }
                    }
                } else {
                    assert isStackSlot(result);
                    if (compressed) {
                        if (isImmutable && generatePIC) {
                            throw GraalInternalError.shouldNotReachHere("Unsupported operation offset(%rip) -> mem (mem -> mem)");
                        } else {
                            masm.movl((AMD64Address) crb.asAddress(result), input.asInt());
                        }
                    } else {
                        throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                    }
                }
            } else {
                AMD64Move.move(crb, masm, result, input);
            }
        }

        public Value getInput() {
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
            AMD64Move.move(Kind.Long, crb, masm, result, input);

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
            AMD64Move.move(Kind.Int, crb, masm, result, input);

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

    public static final class CompressedNullCheckOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CompressedNullCheckOp> TYPE = LIRInstructionClass.create(CompressedNullCheckOp.class);

        @Use({COMPOSITE}) protected AMD64AddressValue address;
        @State protected LIRFrameState state;

        public CompressedNullCheckOp(AMD64AddressValue address, LIRFrameState state) {
            super(TYPE);
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            crb.recordImplicitException(masm.position(), state);
            masm.testl(AMD64.rax, address.toAddress());
        }

        public LIRFrameState getState() {
            return state;
        }
    }
}
