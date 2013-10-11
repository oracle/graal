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
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
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
        public void emitMemAccess(AMD64MacroAssembler masm) {
            if (kind == Kind.Long) {
                if (NumUtil.isInt(input.asLong())) {
                    masm.movl(address.toAddress(), (int) input.asLong());
                } else {
                    throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                }
            } else if (kind == Kind.Object) {
                if (input.isNull()) {
                    masm.movl(address.toAddress(), 0);
                } else {
                    throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                }
            } else {
                throw GraalInternalError.shouldNotReachHere("Attempt to store compressed constant of wrong type.");
            }
        }
    }

    public static class LoadCompressedPointer extends LoadOp {

        private final long klassBase;
        private final long heapBase;
        private final int shift;
        private final int alignment;
        @Alive({REG}) protected AllocatableValue heapBaseRegister;

        public LoadCompressedPointer(Kind kind, AllocatableValue result, AllocatableValue heapBaseRegister, AMD64AddressValue address, LIRFrameState state, long klassBase, long heapBase, int shift,
                        int alignment) {
            super(kind, result, address, state);
            this.klassBase = klassBase;
            this.heapBase = heapBase;
            this.shift = shift;
            this.alignment = alignment;
            this.heapBaseRegister = heapBaseRegister;
            assert kind == Kind.Object || kind == Kind.Long;
        }

        @Override
        public void emitMemAccess(AMD64MacroAssembler masm) {
            Register resRegister = asRegister(result);
            masm.movl(resRegister, address.toAddress());
            if (kind == Kind.Object) {
                decodePointer(masm, resRegister, asRegister(heapBaseRegister), heapBase, shift, alignment);
            } else {
                decodeKlassPointer(masm, resRegister, asRegister(heapBaseRegister), klassBase, heapBase, shift, alignment);
            }
        }
    }

    public static class StoreCompressedPointer extends AMD64LIRInstruction {

        protected final Kind kind;
        private final long klassBase;
        private final long heapBase;
        private final int shift;
        private final int alignment;
        @Temp({REG}) private AllocatableValue scratch;
        @Alive({REG}) protected AllocatableValue input;
        @Alive({COMPOSITE}) protected AMD64AddressValue address;
        @State protected LIRFrameState state;

        public StoreCompressedPointer(Kind kind, AMD64AddressValue address, AllocatableValue input, AllocatableValue scratch, LIRFrameState state, long klassBase, long heapBase, int shift,
                        int alignment) {
            this.klassBase = klassBase;
            this.heapBase = heapBase;
            this.shift = shift;
            this.alignment = alignment;
            this.scratch = scratch;
            this.kind = kind;
            this.address = address;
            this.state = state;
            this.input = input;
            assert kind == Kind.Object || kind == Kind.Long;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            Register heapBaseReg = ((HotSpotRuntime) tasm.codeCache).heapBaseRegister();
            masm.movq(asRegister(scratch), asRegister(input));
            if (kind == Kind.Object) {
                encodePointer(masm, asRegister(scratch), heapBaseReg, heapBase, shift, alignment);
            } else {
                encodeKlassPointer(masm, asRegister(scratch), heapBaseReg, klassBase, heapBase, shift, alignment);
            }
            if (state != null) {
                tasm.recordImplicitException(masm.codeBuffer.position(), state);
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

        private long base;
        private int shift;
        private int alignment;

        public CompareAndSwapCompressedOp(AllocatableValue result, AMD64AddressValue address, AllocatableValue cmpValue, AllocatableValue newValue, AllocatableValue scratch, long base, int shift,
                        int alignment) {
            this.base = base;
            this.shift = shift;
            this.alignment = alignment;
            this.scratch = scratch;
            this.result = result;
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
            assert cmpValue.getKind() == Kind.Object;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            compareAndSwapCompressed(tasm, masm, result, address, cmpValue, newValue, scratch, base, shift, alignment);
        }
    }

    protected static void compareAndSwapCompressed(TargetMethodAssembler tasm, AMD64MacroAssembler masm, AllocatableValue result, AMD64AddressValue address, AllocatableValue cmpValue,
                    AllocatableValue newValue, AllocatableValue scratch, long base, int shift, int alignment) {
        assert AMD64.rax.equals(asRegister(cmpValue)) && AMD64.rax.equals(asRegister(result));
        final Register scratchRegister = asRegister(scratch);
        final Register cmpRegister = asRegister(cmpValue);
        final Register newRegister = asRegister(newValue);
        Register heapBase = ((HotSpotRuntime) tasm.codeCache).heapBaseRegister();
        encodePointer(masm, cmpRegister, heapBase, base, shift, alignment);
        masm.movq(scratchRegister, newRegister);
        encodePointer(masm, scratchRegister, heapBase, base, shift, alignment);
        if (tasm.target.isMP) {
            masm.lock();
        }
        masm.cmpxchgl(scratchRegister, address.toAddress());
    }

    private static void encodePointer(AMD64MacroAssembler masm, Register scratchRegister, Register heapBaseRegister, long base, int shift, int alignment) {
        // If the base is zero, the uncompressed address has to be shifted right
        // in order to be compressed.
        if (base == 0) {
            if (shift != 0) {
                assert alignment == shift : "Encode algorithm is wrong";
                masm.shrq(scratchRegister, alignment);
            }
        } else {
            // Otherwise the heap base, which resides always in register 12, is subtracted
            // followed by right shift.
            masm.testq(scratchRegister, scratchRegister);
            // If the stored reference is null, move the heap to scratch
            // register and then calculate the compressed oop value.
            masm.cmovq(ConditionFlag.Equal, scratchRegister, heapBaseRegister);
            masm.subq(scratchRegister, heapBaseRegister);
            masm.shrq(scratchRegister, alignment);
        }
    }

    private static void decodePointer(AMD64MacroAssembler masm, Register resRegister, Register heapBaseRegister, long base, int shift, int alignment) {
        // If the base is zero, the compressed address has to be shifted left
        // in order to be uncompressed.
        if (base == 0) {
            if (shift != 0) {
                assert alignment == shift : "Decode algorithm is wrong";
                masm.shlq(resRegister, alignment);
            }
        } else {
            Label done = new Label();
            masm.shlq(resRegister, alignment);
            masm.jccb(ConditionFlag.Equal, done);
            // Otherwise the heap base is added to the shifted address.
            masm.addq(resRegister, heapBaseRegister);
            masm.bind(done);
        }
    }

    private static void encodeKlassPointer(AMD64MacroAssembler masm, Register scratchRegister, Register heapBaseRegister, long klassBase, long heapBase, int shift, int alignment) {
        if (klassBase != 0) {
            masm.movq(heapBaseRegister, klassBase);
            masm.subq(scratchRegister, heapBaseRegister);
            restoreHeapBase(masm, heapBaseRegister, heapBase);
        }
        if (shift != 0) {
            assert alignment == shift : "Encode algorithm is wrong";
            masm.shrq(scratchRegister, alignment);
        }
    }

    private static void decodeKlassPointer(AMD64MacroAssembler masm, Register resRegister, Register heapBaseRegister, long klassBase, long heapBase, int shift, int alignment) {
        if (shift != 0) {
            assert alignment == shift : "Decode algorithm is wrong";
            masm.shlq(resRegister, alignment);
        }
        if (klassBase != 0) {
            masm.movq(heapBaseRegister, klassBase);
            masm.addq(resRegister, heapBaseRegister);
            restoreHeapBase(masm, heapBaseRegister, heapBase);
        }
    }

    private static void restoreHeapBase(AMD64MacroAssembler masm, Register heapBaseRegister, long heapBase) {
        if (heapBase == 0) {
            masm.xorq(heapBaseRegister, heapBaseRegister);
        } else {
            masm.movq(heapBaseRegister, heapBase);
        }
    }

    public static void decodeKlassPointer(AMD64MacroAssembler masm, Register register, Register heapBaseRegister, AMD64Address address, long narrowKlassBase, long narrowOopBase, int narrowKlassShift,
                    int logKlassAlignment) {
        masm.movl(register, address);
        decodeKlassPointer(masm, register, heapBaseRegister, narrowKlassBase, narrowOopBase, narrowKlassShift, logKlassAlignment);
    }
}
