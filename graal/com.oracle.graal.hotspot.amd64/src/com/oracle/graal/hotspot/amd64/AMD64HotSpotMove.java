/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.data.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Move.LoadOp;
import com.oracle.graal.lir.amd64.AMD64Move.StoreConstantOp;
import com.oracle.graal.lir.asm.*;

public class AMD64HotSpotMove {

    public static class StoreCompressedConstantOp extends StoreConstantOp {

        public StoreCompressedConstantOp(Kind kind, AMD64AddressValue address, Constant input, LIRFrameState state) {
            super(kind, address, input, state);
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (kind == Kind.Long) {
                if (NumUtil.isInt(input.asLong())) {
                    if (input instanceof HotSpotMetaspaceConstant) {
                        crb.recordInlineDataInCode(new MetaspaceData(0, input.asLong(), HotSpotMetaspaceConstant.getMetaspaceObject(input), true));
                    }
                    masm.movl(address.toAddress(), (int) input.asLong());
                } else {
                    throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                }
            } else if (kind == Kind.Object) {
                if (input.isNull()) {
                    masm.movl(address.toAddress(), 0);
                } else if (crb.target.inlineObjects) {
                    crb.recordInlineDataInCode(new OopData(0, HotSpotObjectConstant.asObject(input), true));
                    masm.movl(address.toAddress(), 0xDEADDEAD);
                } else {
                    throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                }
            } else {
                throw GraalInternalError.shouldNotReachHere("Attempt to store compressed constant of wrong type.");
            }
        }
    }

    public static class HotSpotLoadConstantOp extends AMD64LIRInstruction implements MoveOp {

        @Def({REG, STACK}) private AllocatableValue result;
        private final Constant input;

        public HotSpotLoadConstantOp(AllocatableValue result, Constant input) {
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
                boolean compressed = HotSpotObjectConstant.isCompressed(input);
                OopData data = new OopData(compressed ? 4 : 8, HotSpotObjectConstant.asObject(input), compressed);
                if (crb.target.inlineObjects) {
                    crb.recordInlineDataInCode(data);
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
                        AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(data);
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
                MetaspaceData data = new MetaspaceData(compressed ? 4 : 8, input.asLong(), HotSpotMetaspaceConstant.getMetaspaceObject(input), compressed);
                crb.recordInlineDataInCode(data);
                if (isRegister(result)) {
                    if (compressed) {
                        masm.movl(asRegister(result), input.asInt());
                    } else {
                        masm.movq(asRegister(result), input.asLong());
                    }
                } else {
                    assert isStackSlot(result);
                    if (compressed) {
                        masm.movl((AMD64Address) crb.asAddress(result), input.asInt());
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

    public static class HotSpotStoreConstantOp extends StoreConstantOp {

        public HotSpotStoreConstantOp(Kind kind, AMD64AddressValue address, Constant input, LIRFrameState state) {
            super(kind, address, input, state);
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (input.isNull() && kind == Kind.Int) {
                // compressed null
                masm.movl(address.toAddress(), 0);
            } else if (input instanceof HotSpotObjectConstant) {
                if (HotSpotObjectConstant.isCompressed(input) && crb.target.inlineObjects) {
                    // compressed oop
                    crb.recordInlineDataInCode(new OopData(0, HotSpotObjectConstant.asObject(input), true));
                    masm.movl(address.toAddress(), 0xDEADDEAD);
                } else {
                    // uncompressed oop
                    throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                }
            } else if (input instanceof HotSpotMetaspaceConstant) {
                if (input.getKind() == Kind.Int) {
                    // compressed metaspace pointer
                    crb.recordInlineDataInCode(new MetaspaceData(0, input.asInt(), HotSpotMetaspaceConstant.getMetaspaceObject(input), true));
                    masm.movl(address.toAddress(), input.asInt());
                } else {
                    // uncompressed metaspace pointer
                    throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                }
            } else {
                // primitive value
                super.emitMemAccess(crb, masm);
            }
        }
    }

    public static class CompressPointer extends AMD64LIRInstruction {

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public CompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
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

    public static class UncompressPointer extends AMD64LIRInstruction {

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public UncompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
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

    public static class LoadCompressedPointer extends LoadOp {

        private final CompressEncoding encoding;
        @Temp({REG, ILLEGAL}) protected AllocatableValue scratch;

        public LoadCompressedPointer(Kind kind, AllocatableValue result, AllocatableValue scratch, AMD64AddressValue address, LIRFrameState state, CompressEncoding encoding) {
            super(kind, result, address, state);
            this.encoding = encoding;
            this.scratch = scratch != null ? scratch : Value.ILLEGAL;
            assert kind == Kind.Object || kind == Kind.Long;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            Register resRegister = asRegister(result);
            if (kind == Kind.Object) {
                masm.movl(resRegister, address.toAddress());
                decodePointer(masm, resRegister, asRegister(scratch), encoding);
            } else {
                Register base = scratch.equals(Value.ILLEGAL) ? null : asRegister(scratch);
                decodeKlassPointer(masm, resRegister, base, address.toAddress(), encoding);
            }
        }
    }

    public static class StoreCompressedPointer extends AMD64LIRInstruction {

        protected final Kind kind;
        private final Register heapBaseReg;
        private final CompressEncoding encoding;
        @Temp({REG}) private AllocatableValue scratch;
        @Alive({REG}) protected Value input;
        @Alive({COMPOSITE}) protected AMD64AddressValue address;
        @State protected LIRFrameState state;

        public StoreCompressedPointer(Kind kind, AMD64AddressValue address, AllocatableValue input, AllocatableValue scratch, LIRFrameState state, CompressEncoding encoding, Register heapBaseReg) {
            this.encoding = encoding;
            this.heapBaseReg = heapBaseReg;
            this.scratch = scratch;
            this.kind = kind;
            this.address = address;
            this.state = state;
            this.input = input;
            assert kind == Kind.Object || kind == Kind.Long;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.movq(asRegister(scratch), asRegister(input));
            if (kind == Kind.Long && (encoding.base & 0xffffffffL) == 0 && encoding.shift == 0) {
                // Compressing the pointer won't change the low 32 bits, so just store it
                masm.movl(address.toAddress(), asRegister(input));
            } else if (kind == Kind.Object) {
                encodePointer(masm, asRegister(scratch), heapBaseReg, encoding);
            } else {
                masm.movq(asRegister(scratch), asRegister(input));
                if (kind == Kind.Object) {
                    encodePointer(masm, asRegister(scratch), heapBaseReg, encoding);
                } else {
                    assert !asRegister(scratch).equals(heapBaseReg) : "need to restore value otherwise";
                    encodeKlassPointer(masm, asRegister(scratch), heapBaseReg, encoding);
                }
                if (state != null) {
                    crb.recordImplicitException(masm.position(), state);
                }
                masm.movl(address.toAddress(), asRegister(scratch));
            }
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            masm.movl(address.toAddress(), asRegister(scratch));
        }
    }

    @Opcode("CAS")
    public static class CompareAndSwapCompressedOp extends AMD64LIRInstruction {

        @Def protected AllocatableValue result;
        @Alive({COMPOSITE}) protected AMD64AddressValue address;
        @Alive protected AllocatableValue cmpValue;
        @Alive protected AllocatableValue newValue;
        @Temp({REG}) protected AllocatableValue scratch;

        private CompressEncoding encoding;
        private final Register heapBaseReg;

        public CompareAndSwapCompressedOp(AllocatableValue result, AMD64AddressValue address, AllocatableValue cmpValue, AllocatableValue newValue, AllocatableValue scratch,
                        CompressEncoding encoding, Register heapBaseReg) {
            this.heapBaseReg = heapBaseReg;
            this.scratch = scratch;
            this.encoding = encoding;
            this.result = result;
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
            assert cmpValue.getKind() == Kind.Object;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            compareAndSwapCompressed(crb, masm, result, address, cmpValue, newValue, scratch, encoding, heapBaseReg);
        }
    }

    protected static void compareAndSwapCompressed(CompilationResultBuilder crb, AMD64MacroAssembler masm, AllocatableValue result, AMD64AddressValue address, AllocatableValue cmpValue,
                    AllocatableValue newValue, AllocatableValue scratch, CompressEncoding encoding, Register heapBaseReg) {
        assert AMD64.rax.equals(asRegister(cmpValue)) && AMD64.rax.equals(asRegister(result));
        final Register scratchRegister = asRegister(scratch);
        final Register cmpRegister = asRegister(cmpValue);
        final Register newRegister = asRegister(newValue);
        Register heapBase = heapBaseReg;
        encodePointer(masm, cmpRegister, heapBase, encoding);
        masm.movq(scratchRegister, newRegister);
        encodePointer(masm, scratchRegister, heapBase, encoding);
        if (crb.target.isMP) {
            masm.lock();
        }
        masm.cmpxchgl(scratchRegister, address.toAddress());
    }

    private static void encodePointer(AMD64MacroAssembler masm, Register scratchRegister, Register heapBaseRegister, CompressEncoding encoding) {
        // If the base is zero, the uncompressed address has to be shifted right
        // in order to be compressed.
        if (encoding.base == 0) {
            if (encoding.shift != 0) {
                assert encoding.alignment == encoding.shift : "Encode algorithm is wrong";
                masm.shrq(scratchRegister, encoding.alignment);
            }
        } else {
            // Otherwise the heap base, which resides always in register 12, is subtracted
            // followed by right shift.
            masm.testq(scratchRegister, scratchRegister);
            // If the stored reference is null, move the heap to scratch
            // register and then calculate the compressed oop value.
            masm.cmovq(ConditionFlag.Equal, scratchRegister, heapBaseRegister);
            masm.subq(scratchRegister, heapBaseRegister);
            masm.shrq(scratchRegister, encoding.alignment);
        }
    }

    public static void decodePointer(AMD64MacroAssembler masm, Register resRegister, Register heapBaseRegister, CompressEncoding encoding) {
        // If the base is zero, the compressed address has to be shifted left
        // in order to be uncompressed.
        if (encoding.base == 0) {
            if (encoding.shift != 0) {
                assert encoding.alignment == encoding.shift : "Decode algorithm is wrong";
                masm.shlq(resRegister, encoding.alignment);
            }
        } else {
            Label done = new Label();
            masm.shlq(resRegister, encoding.alignment);
            masm.jccb(ConditionFlag.Equal, done);
            // Otherwise the heap base is added to the shifted address.
            masm.addq(resRegister, heapBaseRegister);
            masm.bind(done);
        }
    }

    private static void encodeKlassPointer(AMD64MacroAssembler masm, Register scratchRegister, Register heapBaseRegister, CompressEncoding encoding) {
        if (encoding.base != 0) {
            masm.movq(heapBaseRegister, encoding.base);
            masm.subq(scratchRegister, heapBaseRegister);
        }
        if (encoding.shift != 0) {
            assert encoding.alignment == encoding.shift : "Encode algorithm is wrong";
            masm.shrq(scratchRegister, encoding.alignment);
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
