/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.CiValueUtil.*;
import static java.lang.Double.*;
import static java.lang.Float.*;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.asm.*;

public class AMD64Move {

    public static class SpillMoveOp extends AMD64LIRInstruction implements MoveOp {
        public SpillMoveOp(Value result, Value input) {
            super("MOVE", new Value[] {result}, null, new Value[] {input}, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            move(tasm, masm, getResult(), getInput());
        }

        @Override
        public Value getInput() {
            return input(0);
        }
        @Override
        public Value getResult() {
            return output(0);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input && index == 0) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Stack, OperandFlag.Constant);
            } else if (mode == OperandMode.Output && index == 0) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Stack);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class MoveToRegOp extends AMD64LIRInstruction implements MoveOp {
        public MoveToRegOp(Value result, Value input) {
            super("MOVE", new Value[] {result}, null, new Value[] {input}, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            move(tasm, masm, getResult(), getInput());
        }

        @Override
        public Value getInput() {
            return input(0);
        }
        @Override
        public Value getResult() {
            return output(0);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input && index == 0) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Stack, OperandFlag.Constant);
            } else if (mode == OperandMode.Output && index == 0) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.RegisterHint);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class MoveFromRegOp extends AMD64LIRInstruction implements MoveOp {
        public MoveFromRegOp(Value result, Value input) {
            super("MOVE", new Value[] {result}, null, new Value[] {input}, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            move(tasm, masm, getResult(), getInput());
        }

        @Override
        public Value getInput() {
            return input(0);
        }
        @Override
        public Value getResult() {
            return output(0);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input && index == 0) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Constant, OperandFlag.RegisterHint);
            } else if (mode == OperandMode.Output && index == 0) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Stack);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class LoadOp extends AMD64LIRInstruction {
        public LoadOp(Value result, Value address, LIRDebugInfo info) {
            super("LOAD", new Value[] {result}, info, new Value[] {address}, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            load(tasm, masm, output(0), (CiAddress) input(0), info);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input && index == 0) {
                return EnumSet.of(OperandFlag.Address);
            } else if (mode == OperandMode.Output && index == 0) {
                return EnumSet.of(OperandFlag.Register);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class StoreOp extends AMD64LIRInstruction {
        public StoreOp(Value address, Value input, LIRDebugInfo info) {
            super("STORE", LIRInstruction.NO_OPERANDS, info, new Value[] {address, input}, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            store(tasm, masm, (CiAddress) input(0), input(1), info);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input && index == 0) {
                return EnumSet.of(OperandFlag.Address);
            } else if (mode == OperandMode.Input && index == 1) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Constant);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class LeaOp extends AMD64LIRInstruction {
        public LeaOp(Value result, Value address) {
            super("LEA", new Value[] {result}, null, new Value[] {address}, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            masm.leaq(asLongReg(output(0)), tasm.asAddress(input(0)));
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input && index == 0) {
                return EnumSet.of(OperandFlag.Address, OperandFlag.Stack, OperandFlag.Uninitialized);
            } else if (mode == OperandMode.Output && index == 0) {
                return EnumSet.of(OperandFlag.Register);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class MembarOp extends AMD64LIRInstruction {
        private final int barriers;

        public MembarOp(final int barriers) {
            super("MEMBAR", LIRInstruction.NO_OPERANDS, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
            this.barriers = barriers;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            masm.membar(barriers);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class NullCheckOp extends AMD64LIRInstruction {
        public NullCheckOp(Variable input, LIRDebugInfo info) {
            super("NULL_CHECK", LIRInstruction.NO_OPERANDS, info, new Value[] {input}, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            tasm.recordImplicitException(masm.codeBuffer.position(), info);
            masm.nullCheck(asRegister(input(0)));
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input && index == 0) {
                return EnumSet.of(OperandFlag.Register);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class CompareAndSwapOp extends AMD64LIRInstruction {
        public CompareAndSwapOp(Value result, CiAddress address, Value cmpValue, Value newValue) {
            super("CAS", new Value[] {result}, null, new Value[] {address, cmpValue, newValue}, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            compareAndSwap(tasm, masm, output(0), asAddress(input(0)), input(1), input(2));
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input && index == 0) {
                return EnumSet.of(OperandFlag.Address);
            } else if (mode == OperandMode.Input && index == 1) {
                return EnumSet.of(OperandFlag.Register);
            } else if (mode == OperandMode.Input && index == 2) {
                return EnumSet.of(OperandFlag.Register);
            } else if (mode == OperandMode.Output && index == 0) {
                return EnumSet.of(OperandFlag.Register);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static void move(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value result, Value input) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(masm, result, input);
            } else if (isStackSlot(result)) {
                reg2stack(tasm, masm, result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                stack2reg(tasm, masm, result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            if (isRegister(result)) {
                const2reg(tasm, masm, result, (Constant) input);
            } else if (isStackSlot(result)) {
                const2stack(tasm, masm, result, (Constant) input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void reg2reg(AMD64MacroAssembler masm, Value result, Value input) {
        if (input.equals(result)) {
            return;
        }
        switch (input.kind) {
            case Jsr:
            case Int:    masm.movl(asRegister(result),    asRegister(input)); break;
            case Long:   masm.movq(asRegister(result),    asRegister(input)); break;
            case Float:  masm.movflt(asFloatReg(result),  asFloatReg(input)); break;
            case Double: masm.movdbl(asDoubleReg(result), asDoubleReg(input)); break;
            case Object: masm.movq(asRegister(result),    asRegister(input)); break;
            default:     throw GraalInternalError.shouldNotReachHere("kind=" + result.kind);
        }
    }

    private static void reg2stack(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value result, Value input) {
        switch (input.kind) {
            case Jsr:
            case Int:    masm.movl(tasm.asAddress(result),   asRegister(input)); break;
            case Long:   masm.movq(tasm.asAddress(result),   asRegister(input)); break;
            case Float:  masm.movflt(tasm.asAddress(result), asFloatReg(input)); break;
            case Double: masm.movsd(tasm.asAddress(result),  asDoubleReg(input)); break;
            case Object: masm.movq(tasm.asAddress(result),   asRegister(input)); break;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void stack2reg(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value result, Value input) {
        switch (input.kind) {
            case Jsr:
            case Int:    masm.movl(asRegister(result),    tasm.asAddress(input)); break;
            case Long:   masm.movq(asRegister(result),    tasm.asAddress(input)); break;
            case Float:  masm.movflt(asFloatReg(result),  tasm.asAddress(input)); break;
            case Double: masm.movdbl(asDoubleReg(result), tasm.asAddress(input)); break;
            case Object: masm.movq(asRegister(result),    tasm.asAddress(input)); break;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void const2reg(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value result, Constant input) {
        // Note: we use the kind of the input operand (and not the kind of the result operand) because they don't match
        // in all cases. For example, an object constant can be loaded to a long register when unsafe casts occurred (e.g.,
        // for a write barrier where arithmetic operations are then performed on the pointer).
        switch (input.kind.stackKind()) {
            case Jsr:
            case Int:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                masm.movl(asRegister(result), tasm.asIntConst(input));
                break;
            case Long:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                masm.movq(asRegister(result), input.asLong());
                break;
            case Float:
                // This is *not* the same as 'constant == 0.0f' in the case where constant is -0.0f
                if (Float.floatToRawIntBits(input.asFloat()) == Float.floatToRawIntBits(0.0f)) {
                    masm.xorps(asFloatReg(result), asFloatReg(result));
                } else {
                    masm.movflt(asFloatReg(result), tasm.asFloatConstRef(input));
                }
                break;
            case Double:
                // This is *not* the same as 'constant == 0.0d' in the case where constant is -0.0d
                if (Double.doubleToRawLongBits(input.asDouble()) == Double.doubleToRawLongBits(0.0d)) {
                    masm.xorpd(asDoubleReg(result), asDoubleReg(result));
                } else {
                    masm.movdbl(asDoubleReg(result), tasm.asDoubleConstRef(input));
                }
                break;
            case Object:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                if (input.isNull()) {
                    masm.movq(asRegister(result), 0x0L);
                } else if (tasm.target.inlineObjects) {
                    tasm.recordDataReferenceInCode(input, 0);
                    masm.movq(asRegister(result), 0xDEADDEADDEADDEADL);
                } else {
                    masm.movq(asRegister(result), tasm.recordDataReferenceInCode(input, 0));
                }
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void const2stack(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value result, Constant input) {
        switch (input.kind.stackKind()) {
            case Jsr:
            case Int:    masm.movl(tasm.asAddress(result), input.asInt()); break;
            case Long:   masm.movlong(tasm.asAddress(result), input.asLong()); break;
            case Float:  masm.movl(tasm.asAddress(result), floatToRawIntBits(input.asFloat())); break;
            case Double: masm.movlong(tasm.asAddress(result), doubleToRawLongBits(input.asDouble())); break;
            case Object:
                if (input.isNull()) {
                    masm.movlong(tasm.asAddress(result), 0L);
                } else {
                    throw GraalInternalError.shouldNotReachHere("Non-null object constants must be in register");
                }
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static void load(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value result, CiAddress loadAddr, LIRDebugInfo info) {
        if (info != null) {
            tasm.recordImplicitException(masm.codeBuffer.position(), info);
        }
        switch (loadAddr.kind) {
            case Boolean:
            case Byte:   masm.movsxb(asRegister(result),  loadAddr); break;
            case Char:   masm.movzxl(asRegister(result),  loadAddr); break;
            case Short:  masm.movswl(asRegister(result),  loadAddr); break;
            case Int:    masm.movslq(asRegister(result),  loadAddr); break;
            case Long:   masm.movq(asRegister(result),    loadAddr); break;
            case Float:  masm.movflt(asFloatReg(result),  loadAddr); break;
            case Double: masm.movdbl(asDoubleReg(result), loadAddr); break;
            case Object: masm.movq(asRegister(result),    loadAddr); break;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static void store(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiAddress storeAddr, Value input, LIRDebugInfo info) {
        if (info != null) {
            tasm.recordImplicitException(masm.codeBuffer.position(), info);
        }

        if (isRegister(input)) {
            switch (storeAddr.kind) {
                case Boolean:
                case Byte:   masm.movb(storeAddr,   asRegister(input)); break;
                case Char:
                case Short:  masm.movw(storeAddr,   asRegister(input)); break;
                case Int:    masm.movl(storeAddr,   asRegister(input)); break;
                case Long:   masm.movq(storeAddr,   asRegister(input)); break;
                case Float:  masm.movflt(storeAddr, asFloatReg(input)); break;
                case Double: masm.movsd(storeAddr,  asDoubleReg(input)); break;
                case Object: masm.movq(storeAddr,   asRegister(input)); break;
                default:     throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            Constant c = (Constant) input;
            switch (storeAddr.kind) {
                case Boolean:
                case Byte:   masm.movb(storeAddr, c.asInt() & 0xFF); break;
                case Char:
                case Short:  masm.movw(storeAddr, c.asInt() & 0xFFFF); break;
                case Jsr:
                case Int:    masm.movl(storeAddr, c.asInt()); break;
                case Long:
                    if (NumUtil.isInt(c.asLong())) {
                        masm.movslq(storeAddr, (int) c.asLong());
                    } else {
                        throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                    }
                    break;
                case Float:  masm.movl(storeAddr, floatToRawIntBits(c.asFloat())); break;
                case Double: throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                case Object:
                    if (c.isNull()) {
                        masm.movptr(storeAddr, 0);
                    } else {
                        throw GraalInternalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                    }
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }

        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    protected static void compareAndSwap(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value result, CiAddress address, Value cmpValue, Value newValue) {
        assert asRegister(cmpValue) == AMD64.rax && asRegister(result) == AMD64.rax;

        if (tasm.target.isMP) {
            masm.lock();
        }
        switch (cmpValue.kind) {
            case Int:    masm.cmpxchgl(asRegister(newValue), address); break;
            case Long:
            case Object: masm.cmpxchgq(asRegister(newValue), address); break;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
    }
}
