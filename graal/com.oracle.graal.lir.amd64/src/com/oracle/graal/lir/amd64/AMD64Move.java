/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MIOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.StandardOp.NullCheck;
import com.oracle.graal.lir.asm.*;
import com.oracle.jvmci.common.*;

public class AMD64Move {

    private abstract static class AbstractMoveOp extends AMD64LIRInstruction implements MoveOp {
        public static final LIRInstructionClass<AbstractMoveOp> TYPE = LIRInstructionClass.create(AbstractMoveOp.class);

        private Kind moveKind;

        protected AbstractMoveOp(LIRInstructionClass<? extends AbstractMoveOp> c, Kind moveKind) {
            super(c);
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
    public static final class MoveToRegOp extends AbstractMoveOp {
        public static final LIRInstructionClass<MoveToRegOp> TYPE = LIRInstructionClass.create(MoveToRegOp.class);

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public MoveToRegOp(Kind moveKind, AllocatableValue result, Value input) {
            super(TYPE, moveKind);
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
    public static final class MoveFromRegOp extends AbstractMoveOp {
        public static final LIRInstructionClass<MoveFromRegOp> TYPE = LIRInstructionClass.create(MoveFromRegOp.class);

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, CONST, HINT}) protected Value input;

        public MoveFromRegOp(Kind moveKind, AllocatableValue result, Value input) {
            super(TYPE, moveKind);
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

    @Opcode("STACKMOVE")
    public static final class AMD64StackMove extends AMD64LIRInstruction implements MoveOp {
        public static final LIRInstructionClass<AMD64StackMove> TYPE = LIRInstructionClass.create(AMD64StackMove.class);

        @Def({STACK}) protected AllocatableValue result;
        @Use({STACK, HINT}) protected Value input;
        @Alive({OperandFlag.STACK, OperandFlag.UNINITIALIZED}) private StackSlotValue backupSlot;

        private Register scratch;

        public AMD64StackMove(AllocatableValue result, Value input, Register scratch, StackSlotValue backupSlot) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.backupSlot = backupSlot;
            this.scratch = scratch;
        }

        @Override
        public Value getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            // backup scratch register
            move(backupSlot.getKind(), crb, masm, backupSlot, scratch.asValue(backupSlot.getLIRKind()));
            // move stack slot
            move(getInput().getKind(), crb, masm, scratch.asValue(getInput().getLIRKind()), getInput());
            move(getResult().getKind(), crb, masm, getResult(), scratch.asValue(getResult().getLIRKind()));
            // restore scratch register
            move(backupSlot.getKind(), crb, masm, scratch.asValue(backupSlot.getLIRKind()), backupSlot);

        }
    }

    public static final class LeaOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<LeaOp> TYPE = LIRInstructionClass.create(LeaOp.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE, UNINITIALIZED}) protected AMD64AddressValue address;

        public LeaOp(AllocatableValue result, AMD64AddressValue address) {
            super(TYPE);
            this.result = result;
            this.address = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.leaq(asLongReg(result), address.toAddress());
        }
    }

    public static final class LeaDataOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<LeaDataOp> TYPE = LIRInstructionClass.create(LeaDataOp.class);

        @Def({REG}) protected AllocatableValue result;
        private final byte[] data;

        public LeaDataOp(AllocatableValue result, byte[] data) {
            super(TYPE);
            this.result = result;
            this.data = data;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.leaq(asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(data, 16));
        }
    }

    public static final class StackLeaOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<StackLeaOp> TYPE = LIRInstructionClass.create(StackLeaOp.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected StackSlotValue slot;

        public StackLeaOp(AllocatableValue result, StackSlotValue slot) {
            super(TYPE);
            assert isStackSlotValue(slot) : "Not a stack slot: " + slot;
            this.result = result;
            this.slot = slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.leaq(asLongReg(result), (AMD64Address) crb.asAddress(slot));
        }
    }

    public static final class MembarOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<MembarOp> TYPE = LIRInstructionClass.create(MembarOp.class);

        private final int barriers;

        public MembarOp(final int barriers) {
            super(TYPE);
            this.barriers = barriers;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.membar(barriers);
        }
    }

    public static final class NullCheckOp extends AMD64LIRInstruction implements NullCheck {
        public static final LIRInstructionClass<NullCheckOp> TYPE = LIRInstructionClass.create(NullCheckOp.class);

        @Use({COMPOSITE}) protected AMD64AddressValue address;
        @State protected LIRFrameState state;

        public NullCheckOp(AMD64AddressValue address, LIRFrameState state) {
            super(TYPE);
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            crb.recordImplicitException(masm.position(), state);
            masm.nullCheck(address.toAddress());
        }

        public Value getCheckedValue() {
            return address.base;
        }

        public LIRFrameState getState() {
            return state;
        }
    }

    @Opcode("CAS")
    public static final class CompareAndSwapOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CompareAndSwapOp> TYPE = LIRInstructionClass.create(CompareAndSwapOp.class);

        private final Kind accessKind;

        @Def protected AllocatableValue result;
        @Use({COMPOSITE}) protected AMD64AddressValue address;
        @Use protected AllocatableValue cmpValue;
        @Use protected AllocatableValue newValue;

        public CompareAndSwapOp(Kind accessKind, AllocatableValue result, AMD64AddressValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
            super(TYPE);
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
                    throw JVMCIError.shouldNotReachHere();
            }
        }
    }

    @Opcode("ATOMIC_READ_AND_ADD")
    public static final class AtomicReadAndAddOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AtomicReadAndAddOp> TYPE = LIRInstructionClass.create(AtomicReadAndAddOp.class);

        private final Kind accessKind;

        @Def protected AllocatableValue result;
        @Alive({COMPOSITE}) protected AMD64AddressValue address;
        @Use protected AllocatableValue delta;

        public AtomicReadAndAddOp(Kind accessKind, AllocatableValue result, AMD64AddressValue address, AllocatableValue delta) {
            super(TYPE);
            this.accessKind = accessKind;
            this.result = result;
            this.address = address;
            this.delta = delta;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            move(accessKind, crb, masm, result, delta);
            if (crb.target.isMP) {
                masm.lock();
            }
            switch (accessKind) {
                case Int:
                    masm.xaddl(address.toAddress(), asRegister(result));
                    break;
                case Long:
                    masm.xaddq(address.toAddress(), asRegister(result));
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        }
    }

    @Opcode("ATOMIC_READ_AND_WRITE")
    public static final class AtomicReadAndWriteOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AtomicReadAndWriteOp> TYPE = LIRInstructionClass.create(AtomicReadAndWriteOp.class);

        private final Kind accessKind;

        @Def protected AllocatableValue result;
        @Alive({COMPOSITE}) protected AMD64AddressValue address;
        @Use protected AllocatableValue newValue;

        public AtomicReadAndWriteOp(Kind accessKind, AllocatableValue result, AMD64AddressValue address, AllocatableValue newValue) {
            super(TYPE);
            this.accessKind = accessKind;
            this.result = result;
            this.address = address;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            move(accessKind, crb, masm, result, newValue);
            switch (accessKind) {
                case Int:
                    masm.xchgl(asRegister(result), address.toAddress());
                    break;
                case Long:
                case Object:
                    masm.xchgq(asRegister(result), address.toAddress());
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
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
                throw JVMCIError.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                stack2reg(moveKind, crb, masm, result, input);
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            if (isRegister(result)) {
                const2reg(crb, masm, result, (JavaConstant) input);
            } else if (isStackSlot(result)) {
                const2stack(crb, masm, result, (JavaConstant) input);
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        } else {
            throw JVMCIError.shouldNotReachHere();
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
                throw JVMCIError.shouldNotReachHere("kind=" + result.getKind());
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
                throw JVMCIError.shouldNotReachHere();
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
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private static void const2reg(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, JavaConstant input) {
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
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private static void const2stack(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, JavaConstant input) {
        assert !crb.codeCache.needsDataPatch(input);
        AMD64Address dest = (AMD64Address) crb.asAddress(result);
        final long imm;
        switch (input.getKind().getStackKind()) {
            case Int:
                imm = input.asInt();
                break;
            case Long:
                imm = input.asLong();
                break;
            case Float:
                imm = floatToRawIntBits(input.asFloat());
                break;
            case Double:
                imm = doubleToRawLongBits(input.asDouble());
                break;
            case Object:
                if (input.isNull()) {
                    imm = 0;
                } else {
                    throw JVMCIError.shouldNotReachHere("Non-null object constants must be in register");
                }
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        switch (result.getKind()) {
            case Byte:
                assert NumUtil.isByte(imm) : "Is not in byte range: " + imm;
                AMD64MIOp.MOVB.emit(masm, OperandSize.BYTE, dest, (int) imm);
                break;
            case Short:
                assert NumUtil.isShort(imm) : "Is not in short range: " + imm;
                AMD64MIOp.MOV.emit(masm, OperandSize.WORD, dest, (int) imm);
                break;
            case Int:
            case Float:
                assert NumUtil.isInt(imm) : "Is not in int range: " + imm;
                masm.movl(dest, (int) imm);
                break;
            case Long:
            case Double:
            case Object:
                masm.movlong(dest, imm);
                break;
            default:
                throw JVMCIError.shouldNotReachHere("Unknown result Kind: " + result.getKind());
        }
    }
}
