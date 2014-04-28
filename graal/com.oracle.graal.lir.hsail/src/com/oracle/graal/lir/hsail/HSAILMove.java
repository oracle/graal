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
package com.oracle.graal.lir.hsail;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.hsail.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.asm.*;

/**
 * Implementation of move instructions.
 */
public class HSAILMove {

    // Stack size in bytes (used to keep track of spilling).
    static int maxDatatypeSize;
    // Maximum stack offset used by a store operation.
    static long maxStackOffset = 0;

    public static long upperBoundStackSize() {
        return maxStackOffset + maxDatatypeSize;
    }

    private abstract static class AbstractMoveOp extends HSAILLIRInstruction implements MoveOp {

        private Kind moveKind;

        public AbstractMoveOp(Kind moveKind) {
            this.moveKind = moveKind;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            move(moveKind, crb, masm, getResult(), getInput());
        }
    }

    @Opcode("MOVE")
    public static class SpillMoveOp extends AbstractMoveOp {

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public SpillMoveOp(Kind moveKind, AllocatableValue result, Value input) {
            super(moveKind);
            this.result = result;
            this.input = input;
        }

        @Override
        public Value getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("MOVE")
    public static class MoveToRegOp extends AbstractMoveOp {

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public MoveToRegOp(Kind moveKind, AllocatableValue result, Value input) {
            super(moveKind);
            this.result = result;
            this.input = input;
            checkForNullObjectInput();
        }

        private void checkForNullObjectInput() {
            if (result.getKind() == Kind.Object && isConstant(input) && input.getKind() == Kind.Long && ((Constant) input).asLong() == 0) {
                input = Constant.NULL_OBJECT;
            }
        }

        @Override
        public Value getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("MOVE")
    public static class MoveFromRegOp extends AbstractMoveOp {

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, CONST, HINT}) protected Value input;

        public MoveFromRegOp(Kind moveKind, AllocatableValue result, Value input) {
            super(moveKind);
            this.result = result;
            this.input = input;
        }

        @Override
        public Value getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    public abstract static class MemOp extends HSAILLIRInstruction {

        protected final Kind kind;
        @Use({COMPOSITE}) protected HSAILAddressValue address;
        @State protected LIRFrameState state;

        public MemOp(Kind kind, HSAILAddressValue address, LIRFrameState state) {
            this.kind = kind;
            this.address = address;
            this.state = state;
        }

        protected abstract void emitMemAccess(HSAILAssembler masm);

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            if (state != null) {
                // crb.recordImplicitException(masm.position(), state);
                throw new InternalError("NYI");
            }
            emitMemAccess(masm);
        }
    }

    public static class MembarOp extends HSAILLIRInstruction {

        private final int barriers;

        public MembarOp(final int barriers) {
            this.barriers = barriers;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            masm.emitMembar(barriers);
        }
    }

    public static class LoadOp extends MemOp {

        @Def({REG}) protected AllocatableValue result;

        public LoadOp(Kind kind, AllocatableValue result, HSAILAddressValue address, LIRFrameState state) {
            super(kind, address, state);
            this.result = result;
        }

        @Override
        public void emitMemAccess(HSAILAssembler masm) {
            HSAILAddress addr = address.toAddress();
            masm.emitLoad(kind, result, addr);
        }
    }

    public static class StoreOp extends MemOp {

        @Use({REG}) protected AllocatableValue input;

        public StoreOp(Kind kind, HSAILAddressValue address, AllocatableValue input, LIRFrameState state) {
            super(kind, address, state);
            this.input = input;
        }

        @Override
        public void emitMemAccess(HSAILAssembler masm) {
            assert isRegister(input);
            HSAILAddress addr = address.toAddress();
            masm.emitStore(kind, input, addr);
        }
    }

    public static class StoreConstantOp extends MemOp {

        protected final Constant input;

        public StoreConstantOp(Kind kind, HSAILAddressValue address, Constant input, LIRFrameState state) {
            super(kind, address, state);
            this.input = input;
        }

        @Override
        public void emitMemAccess(HSAILAssembler masm) {
            switch (kind) {
                case Boolean:
                case Byte:
                    masm.emitStoreImmediate(kind, input.asLong() & 0xFF, address.toAddress());
                    break;
                case Char:
                case Short:
                    masm.emitStoreImmediate(kind, input.asLong() & 0xFFFF, address.toAddress());
                    break;
                case Int:
                case Long:
                    masm.emitStoreImmediate(kind, input.asLong(), address.toAddress());
                    break;
                case Float:
                    masm.emitStoreImmediate(input.asFloat(), address.toAddress());
                    break;
                case Double:
                    masm.emitStoreImmediate(input.asDouble(), address.toAddress());
                    break;
                case Object:
                    if (input.isNull()) {
                        masm.emitStoreImmediate(kind, 0L, address.toAddress());
                    } else {
                        throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to object ref");
                    }
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static class LoadCompressedPointer extends LoadOp {

        private final long base;
        private final int shift;
        private final int alignment;
        @Temp({REG}) private AllocatableValue scratch;

        public LoadCompressedPointer(Kind kind, AllocatableValue result, AllocatableValue scratch, HSAILAddressValue address, LIRFrameState state, long base, int shift, int alignment) {
            super(kind, result, address, state);
            this.base = base;
            this.shift = shift;
            this.alignment = alignment;
            this.scratch = scratch;
            assert kind == Kind.Object || kind == Kind.Long;
        }

        @Override
        public void emitMemAccess(HSAILAssembler masm) {
            // we will do a 32 bit load, zero extending into a 64 bit register
            masm.emitLoad(result, address.toAddress(), "u32");
            boolean testForNull = (kind == Kind.Object);
            decodePointer(masm, result, base, shift, alignment, testForNull);
        }
    }

    public static class StoreCompressedPointer extends HSAILLIRInstruction {

        protected final Kind kind;
        private final long base;
        private final int shift;
        private final int alignment;
        @Temp({REG}) private AllocatableValue scratch;
        @Alive({REG}) protected AllocatableValue input;
        @Alive({COMPOSITE}) protected HSAILAddressValue address;
        @State protected LIRFrameState state;

        public StoreCompressedPointer(Kind kind, HSAILAddressValue address, AllocatableValue input, AllocatableValue scratch, LIRFrameState state, long base, int shift, int alignment) {
            this.base = base;
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
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            masm.emitMov(kind, scratch, input);
            boolean testForNull = (kind == Kind.Object);
            encodePointer(masm, scratch, base, shift, alignment, testForNull);
            if (state != null) {
                throw new InternalError("NYI");
                // crb.recordImplicitException(masm.position(), state);
            }
            masm.emitStore(scratch, address.toAddress(), "u32");
        }
    }

    public static class CompressPointer extends HSAILLIRInstruction {

        private final long base;
        private final int shift;
        private final int alignment;
        private final boolean nonNull;

        @Def({REG}) protected AllocatableValue result;
        @Temp({REG, HINT}) protected AllocatableValue scratch;
        @Use({REG}) protected AllocatableValue input;

        public CompressPointer(AllocatableValue result, AllocatableValue scratch, AllocatableValue input, long base, int shift, int alignment, boolean nonNull) {
            this.result = result;
            this.scratch = scratch;
            this.input = input;
            this.base = base;
            this.shift = shift;
            this.alignment = alignment;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            masm.emitMov(Kind.Long, scratch, input);
            boolean testForNull = !nonNull;
            encodePointer(masm, scratch, base, shift, alignment, testForNull);
            masm.emitConvert(result, scratch, "u32", "u64");
        }
    }

    public static class UncompressPointer extends HSAILLIRInstruction {

        private final long base;
        private final int shift;
        private final int alignment;
        private final boolean nonNull;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        public UncompressPointer(AllocatableValue result, AllocatableValue input, long base, int shift, int alignment, boolean nonNull) {
            this.result = result;
            this.input = input;
            this.base = base;
            this.shift = shift;
            this.alignment = alignment;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            masm.emitConvert(result, input, "u64", "u32");
            boolean testForNull = !nonNull;
            decodePointer(masm, result, base, shift, alignment, testForNull);
        }
    }

    private static void encodePointer(HSAILAssembler masm, Value scratch, long base, int shift, int alignment, boolean testForNull) {
        if (base == 0 && shift == 0) {
            return;
        }
        if (shift != 0) {
            assert alignment == shift : "Encode algorithm is wrong";
        }
        masm.emitCompressedOopEncode(scratch, base, shift, testForNull);
    }

    private static void decodePointer(HSAILAssembler masm, Value result, long base, int shift, int alignment, boolean testForNull) {
        if (base == 0 && shift == 0) {
            return;
        }
        if (shift != 0) {
            assert alignment == shift : "Decode algorithm is wrong";
        }
        masm.emitCompressedOopDecode(result, base, shift, testForNull);
    }

    public static class LeaOp extends HSAILLIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE, UNINITIALIZED}) protected HSAILAddressValue address;

        public LeaOp(AllocatableValue result, HSAILAddressValue address) {
            this.result = result;
            this.address = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            throw new InternalError("NYI");
        }
    }

    public static class StackLeaOp extends HSAILLIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected StackSlot slot;

        public StackLeaOp(AllocatableValue result, StackSlot slot) {
            this.result = result;
            this.slot = slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            throw new InternalError("NYI");
        }
    }

    @Opcode("CAS")
    public static class CompareAndSwapOp extends HSAILLIRInstruction {

        private final Kind accessKind;

        @Def protected AllocatableValue result;
        @Use({COMPOSITE}) protected HSAILAddressValue address;
        @Use protected AllocatableValue cmpValue;
        @Use protected AllocatableValue newValue;

        public CompareAndSwapOp(Kind accessKind, AllocatableValue result, HSAILAddressValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
            this.accessKind = accessKind;
            this.result = result;
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            masm.emitAtomicCas(accessKind, result, address.toAddress(), cmpValue, newValue);
        }
    }

    @Opcode("ATOMIC_READ_AND_ADD")
    public static class AtomicReadAndAddOp extends HSAILLIRInstruction {

        private final Kind accessKind;

        @Def protected AllocatableValue result;
        @Use({COMPOSITE}) protected HSAILAddressValue address;
        @Use({REG, CONST}) protected Value delta;

        public AtomicReadAndAddOp(Kind accessKind, AllocatableValue result, HSAILAddressValue address, Value delta) {
            this.accessKind = accessKind;
            this.result = result;
            this.address = address;
            this.delta = delta;
        }

        public HSAILAddressValue getAddress() {
            return address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            switch (accessKind) {
                case Int:
                case Long:
                    masm.emitAtomicAdd(result, address.toAddress(), delta);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    @Opcode("ATOMIC_READ_AND_WRITE")
    public static class AtomicReadAndWriteOp extends HSAILLIRInstruction {

        private final Kind accessKind;

        @Def protected AllocatableValue result;
        @Use({COMPOSITE}) protected HSAILAddressValue address;
        @Use({REG, CONST}) protected Value newValue;

        public AtomicReadAndWriteOp(Kind accessKind, AllocatableValue result, HSAILAddressValue address, Value newValue) {
            this.accessKind = accessKind;
            this.result = result;
            this.address = address;
            this.newValue = newValue;
        }

        public HSAILAddressValue getAddress() {
            return address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            masm.emitAtomicExch(accessKind, result, address.toAddress(), newValue);
        }
    }

    public static class NullCheckOp extends HSAILLIRInstruction {

        @Use protected Value input;
        @State protected LIRFrameState state;

        public NullCheckOp(Variable input, LIRFrameState state) {
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            Debug.log("NullCheckOp unimplemented");
        }
    }

    @SuppressWarnings("unused")
    public static void move(Kind kind, CompilationResultBuilder crb, HSAILAssembler masm, Value result, Value input) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                masm.emitMov(kind, result, input);
            } else if (isStackSlot(result)) {
                masm.emitSpillStore(kind, input, result);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                masm.emitSpillLoad(kind, result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            if (isRegister(result)) {
                masm.emitMov(kind, result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

}
