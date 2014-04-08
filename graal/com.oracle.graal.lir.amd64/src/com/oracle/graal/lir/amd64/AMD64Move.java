/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static java.lang.Double.*;
import static java.lang.Float.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.CompilationResult.RawData;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.ImplicitNullCheck;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.StandardOp.NullCheck;
import com.oracle.graal.lir.asm.*;

public class AMD64Move {

    private abstract static class AbstractMoveOp extends AMD64LIRInstruction implements MoveOp {

        private Kind moveKind;

        public AbstractMoveOp(Kind moveKind) {
            if (moveKind == Kind.Illegal) {
                // unknown operand size, conservatively move the whole register
                this.moveKind = Kind.Long;
            } else {
                this.moveKind = moveKind;
            }
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            move(moveKind, crb, masm, getResult(), getInput());
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

    public abstract static class MemOp extends AMD64LIRInstruction implements ImplicitNullCheck {

        protected final Kind kind;
        @Use({COMPOSITE}) protected AMD64AddressValue address;
        @State protected LIRFrameState state;

        public MemOp(Kind kind, AMD64AddressValue address, LIRFrameState state) {
            this.kind = kind;
            this.address = address;
            this.state = state;
        }

        protected abstract void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm);

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            emitMemAccess(crb, masm);
        }

        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && value.equals(address.base) && address.index.equals(Value.ILLEGAL) && address.displacement >= 0 && address.displacement < implicitNullCheckLimit) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    public static class LoadOp extends MemOp {

        @Def({REG}) protected AllocatableValue result;

        public LoadOp(Kind kind, AllocatableValue result, AMD64AddressValue address, LIRFrameState state) {
            super(kind, address, state);
            this.result = result;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            switch (kind) {
                case Boolean:
                    masm.movzbl(asRegister(result), address.toAddress());
                    break;
                case Byte:
                    masm.movsbl(asRegister(result), address.toAddress());
                    break;
                case Char:
                    masm.movzwl(asRegister(result), address.toAddress());
                    break;
                case Short:
                    masm.movswl(asRegister(result), address.toAddress());
                    break;
                case Int:
                    masm.movl(asRegister(result), address.toAddress());
                    break;
                case Long:
                    masm.movq(asRegister(result), address.toAddress());
                    break;
                case Float:
                    masm.movflt(asFloatReg(result), address.toAddress());
                    break;
                case Double:
                    masm.movdbl(asDoubleReg(result), address.toAddress());
                    break;
                case Object:
                    masm.movq(asRegister(result), address.toAddress());
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static class ZeroExtendLoadOp extends MemOp {

        @Def({REG}) protected AllocatableValue result;

        public ZeroExtendLoadOp(Kind kind, AllocatableValue result, AMD64AddressValue address, LIRFrameState state) {
            super(kind, address, state);
            this.result = result;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            switch (kind) {
                case Boolean:
                case Byte:
                    masm.movzbl(asRegister(result), address.toAddress());
                    break;
                case Char:
                case Short:
                    masm.movzwl(asRegister(result), address.toAddress());
                    break;
                case Int:
                    masm.movl(asRegister(result), address.toAddress());
                    break;
                case Long:
                    masm.movq(asRegister(result), address.toAddress());
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static class StoreOp extends MemOp {

        @Use({REG}) protected AllocatableValue input;

        public StoreOp(Kind kind, AMD64AddressValue address, AllocatableValue input, LIRFrameState state) {
            super(kind, address, state);
            this.input = input;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            assert isRegister(input);
            switch (kind) {
                case Boolean:
                case Byte:
                    masm.movb(address.toAddress(), asRegister(input));
                    break;
                case Char:
                case Short:
                    masm.movw(address.toAddress(), asRegister(input));
                    break;
                case Int:
                    masm.movl(address.toAddress(), asRegister(input));
                    break;
                case Long:
                    masm.movq(address.toAddress(), asRegister(input));
                    break;
                case Float:
                    masm.movflt(address.toAddress(), asFloatReg(input));
                    break;
                case Double:
                    masm.movsd(address.toAddress(), asDoubleReg(input));
                    break;
                case Object:
                    masm.movq(address.toAddress(), asRegister(input));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static class StoreConstantOp extends MemOp {

        protected final Constant input;

        public StoreConstantOp(Kind kind, AMD64AddressValue address, Constant input, LIRFrameState state) {
            super(kind, address, state);
            this.input = input;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            switch (kind) {
                case Boolean:
                case Byte:
                    masm.movb(address.toAddress(), input.asInt() & 0xFF);
                    break;
                case Char:
                case Short:
                    masm.movw(address.toAddress(), input.asInt() & 0xFFFF);
                    break;
                case Int:
                    masm.movl(address.toAddress(), input.asInt());
                    break;
                case Long:
                    if (NumUtil.isInt(input.asLong())) {
                        masm.movslq(address.toAddress(), (int) input.asLong());
                    } else {
                        throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                    }
                    break;
                case Float:
                    masm.movl(address.toAddress(), floatToRawIntBits(input.asFloat()));
                    break;
                case Double:
                    throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                case Object:
                    if (input.isNull()) {
                        masm.movptr(address.toAddress(), 0);
                    } else {
                        throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                    }
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static class LeaOp extends AMD64LIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE, UNINITIALIZED}) protected AMD64AddressValue address;

        public LeaOp(AllocatableValue result, AMD64AddressValue address) {
            this.result = result;
            this.address = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.leaq(asLongReg(result), address.toAddress());
        }
    }

    public static class LeaDataOp extends AMD64LIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        private final byte[] data;

        public LeaDataOp(AllocatableValue result, byte[] data) {
            this.result = result;
            this.data = data;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            RawData rawData = new RawData(data, 16);
            masm.leaq(asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(rawData));
        }
    }

    public static class StackLeaOp extends AMD64LIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected StackSlot slot;

        public StackLeaOp(AllocatableValue result, StackSlot slot) {
            this.result = result;
            this.slot = slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.leaq(asLongReg(result), (AMD64Address) crb.asAddress(slot));
        }
    }

    public static class MembarOp extends AMD64LIRInstruction {

        private final int barriers;

        public MembarOp(final int barriers) {
            this.barriers = barriers;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.membar(barriers);
        }
    }

    public static class NullCheckOp extends AMD64LIRInstruction implements NullCheck {

        @Use({REG}) protected AllocatableValue input;
        @State protected LIRFrameState state;

        public NullCheckOp(Variable input, LIRFrameState state) {
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            crb.recordImplicitException(masm.position(), state);
            masm.nullCheck(asRegister(input));
        }

        public Value getCheckedValue() {
            return input;
        }

        public LIRFrameState getState() {
            return state;
        }
    }

    @Opcode("CAS")
    public static class CompareAndSwapOp extends AMD64LIRInstruction {

        private final Kind accessKind;

        @Def protected AllocatableValue result;
        @Use({COMPOSITE}) protected AMD64AddressValue address;
        @Use protected AllocatableValue cmpValue;
        @Use protected AllocatableValue newValue;

        public CompareAndSwapOp(Kind accessKind, AllocatableValue result, AMD64AddressValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
            this.accessKind = accessKind;
            this.result = result;
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            assert asRegister(cmpValue).equals(AMD64.rax) && asRegister(result).equals(AMD64.rax);

            if (crb.target.isMP) {
                masm.lock();
            }
            switch (accessKind) {
                case Int:
                    masm.cmpxchgl(asRegister(newValue), address.toAddress());
                    break;
                case Long:
                case Object:
                    masm.cmpxchgq(asRegister(newValue), address.toAddress());
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static void move(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, Value input) {
        move(result.getKind(), crb, masm, result, input);
    }

    public static void move(Kind moveKind, CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, Value input) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(moveKind, masm, result, input);
            } else if (isStackSlot(result)) {
                reg2stack(moveKind, crb, masm, result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                stack2reg(moveKind, crb, masm, result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            if (isRegister(result)) {
                const2reg(crb, masm, result, (Constant) input);
            } else if (isStackSlot(result)) {
                const2stack(crb, masm, result, (Constant) input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void reg2reg(Kind kind, AMD64MacroAssembler masm, Value result, Value input) {
        if (asRegister(input).equals(asRegister(result))) {
            return;
        }
        switch (kind.getStackKind()) {
            case Int:
                masm.movl(asRegister(result), asRegister(input));
                break;
            case Long:
                masm.movq(asRegister(result), asRegister(input));
                break;
            case Float:
                masm.movflt(asFloatReg(result), asFloatReg(input));
                break;
            case Double:
                masm.movdbl(asDoubleReg(result), asDoubleReg(input));
                break;
            case Object:
                masm.movq(asRegister(result), asRegister(input));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("kind=" + result.getKind());
        }
    }

    private static void reg2stack(Kind kind, CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, Value input) {
        AMD64Address dest = (AMD64Address) crb.asAddress(result);
        switch (kind) {
            case Boolean:
            case Byte:
                masm.movb(dest, asRegister(input));
                break;
            case Short:
            case Char:
                masm.movw(dest, asRegister(input));
                break;
            case Int:
                masm.movl(dest, asRegister(input));
                break;
            case Long:
                masm.movq(dest, asRegister(input));
                break;
            case Float:
                masm.movflt(dest, asFloatReg(input));
                break;
            case Double:
                masm.movsd(dest, asDoubleReg(input));
                break;
            case Object:
                masm.movq(dest, asRegister(input));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void stack2reg(Kind kind, CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, Value input) {
        AMD64Address src = (AMD64Address) crb.asAddress(input);
        switch (kind) {
            case Boolean:
                masm.movzbl(asRegister(result), src);
                break;
            case Byte:
                masm.movsbl(asRegister(result), src);
                break;
            case Short:
                masm.movswl(asRegister(result), src);
                break;
            case Char:
                masm.movzwl(asRegister(result), src);
                break;
            case Int:
                masm.movl(asRegister(result), src);
                break;
            case Long:
                masm.movq(asRegister(result), src);
                break;
            case Float:
                masm.movflt(asFloatReg(result), src);
                break;
            case Double:
                masm.movdbl(asDoubleReg(result), src);
                break;
            case Object:
                masm.movq(asRegister(result), src);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void const2reg(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, Constant input) {
        /*
         * Note: we use the kind of the input operand (and not the kind of the result operand)
         * because they don't match in all cases. For example, an object constant can be loaded to a
         * long register when unsafe casts occurred (e.g., for a write barrier where arithmetic
         * operations are then performed on the pointer).
         */
        switch (input.getKind().getStackKind()) {
            case Int:
                if (crb.codeCache.needsDataPatch(input)) {
                    crb.recordInlineDataInCode(input);
                }
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                masm.movl(asRegister(result), input.asInt());

                break;
            case Long:
                boolean patch = false;
                if (crb.codeCache.needsDataPatch(input)) {
                    patch = true;
                    crb.recordInlineDataInCode(input);
                }
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                if (patch) {
                    masm.movq(asRegister(result), input.asLong());
                } else {
                    if (input.asLong() == (int) input.asLong()) {
                        // Sign extended to long
                        masm.movslq(asRegister(result), (int) input.asLong());
                    } else if ((input.asLong() & 0xFFFFFFFFL) == input.asLong()) {
                        // Zero extended to long
                        masm.movl(asRegister(result), (int) input.asLong());
                    } else {
                        masm.movq(asRegister(result), input.asLong());
                    }
                }
                break;
            case Float:
                // This is *not* the same as 'constant == 0.0f' in the case where constant is -0.0f
                if (Float.floatToRawIntBits(input.asFloat()) == Float.floatToRawIntBits(0.0f)) {
                    assert !crb.codeCache.needsDataPatch(input);
                    masm.xorps(asFloatReg(result), asFloatReg(result));
                } else {
                    masm.movflt(asFloatReg(result), (AMD64Address) crb.asFloatConstRef(input));
                }
                break;
            case Double:
                // This is *not* the same as 'constant == 0.0d' in the case where constant is -0.0d
                if (Double.doubleToRawLongBits(input.asDouble()) == Double.doubleToRawLongBits(0.0d)) {
                    assert !crb.codeCache.needsDataPatch(input);
                    masm.xorpd(asDoubleReg(result), asDoubleReg(result));
                } else {
                    masm.movdbl(asDoubleReg(result), (AMD64Address) crb.asDoubleConstRef(input));
                }
                break;
            case Object:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                if (input.isNull()) {
                    masm.movq(asRegister(result), 0x0L);
                } else if (crb.target.inlineObjects) {
                    crb.recordInlineDataInCode(input);
                    masm.movq(asRegister(result), 0xDEADDEADDEADDEADL);
                } else {
                    masm.movq(asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(input, 0));
                }
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void const2stack(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, Constant input) {
        assert !crb.codeCache.needsDataPatch(input);
        AMD64Address dest = (AMD64Address) crb.asAddress(result);
        switch (input.getKind().getStackKind()) {
            case Int:
                masm.movl(dest, input.asInt());
                break;
            case Long:
                masm.movlong(dest, input.asLong());
                break;
            case Float:
                masm.movl(dest, floatToRawIntBits(input.asFloat()));
                break;
            case Double:
                masm.movlong(dest, doubleToRawLongBits(input.asDouble()));
                break;
            case Object:
                if (input.isNull()) {
                    masm.movlong(dest, 0L);
                } else {
                    throw GraalInternalError.shouldNotReachHere("Non-null object constants must be in register");
                }
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
