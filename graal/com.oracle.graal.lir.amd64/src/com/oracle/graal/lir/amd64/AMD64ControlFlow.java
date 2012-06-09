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

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CiAddress.*;
import com.oracle.graal.api.code.CompilationResult.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.calc.*;

public class AMD64ControlFlow {

    public static class ReturnOp extends AMD64LIRInstruction {
        public ReturnOp(Value input) {
            super("RETURN", LIRInstruction.NO_OPERANDS, null, new Value[] {input}, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            if (tasm.frameContext != null) {
                tasm.frameContext.leave(tasm);
            }
            masm.ret(0);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input && index == 0) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Illegal);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class BranchOp extends AMD64LIRInstruction implements StandardOp.BranchOp {
        protected ConditionFlag condition;
        protected LabelRef destination;

        public BranchOp(Condition condition, LabelRef destination, LIRDebugInfo info) {
            this(intCond(condition), destination, info);
        }

        public BranchOp(ConditionFlag condition, LabelRef destination, LIRDebugInfo info) {
            super("BRANCH", LIRInstruction.NO_OPERANDS, info, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
            this.condition = condition;
            this.destination = destination;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            masm.jcc(condition, destination.label());
        }

        @Override
        public LabelRef destination() {
            return destination;
        }

        @Override
        public void negate(LabelRef newDestination) {
            destination = newDestination;
            condition = condition.negate();
        }

        @Override
        public String operationString() {
            return condition.operator + " [" + destination + "]";
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class FloatBranchOp extends BranchOp {
        protected boolean unorderedIsTrue;

        public FloatBranchOp(Condition condition, boolean unorderedIsTrue, LabelRef destination, LIRDebugInfo info) {
            super(floatCond(condition), destination, info);
            this.unorderedIsTrue = unorderedIsTrue;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            floatJcc(masm, condition, unorderedIsTrue, destination.label());
        }

        @Override
        public void negate(LabelRef newDestination) {
            super.negate(newDestination);
            unorderedIsTrue = !unorderedIsTrue;
        }

        @Override
        public String operationString() {
            return condition.operator + " [" + destination + "]" + (unorderedIsTrue ? " unorderedIsTrue" : " unorderedIsFalse");
        }
    }


    public static class TableSwitchOp extends AMD64LIRInstruction {
        private final int lowKey;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;

        public TableSwitchOp(final int lowKey, final LabelRef defaultTarget, final LabelRef[] targets, Variable index, Variable scratch) {
            super("TABLE_SWITCH", LIRInstruction.NO_OPERANDS, null, LIRInstruction.NO_OPERANDS, new Value[] {index}, new Value[] {scratch});
            this.lowKey = lowKey;
            this.defaultTarget = defaultTarget;
            this.targets = targets;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            tableswitch(tasm, masm, lowKey, defaultTarget, targets, asIntReg(alive(0)), asLongReg(temp(0)));
        }

        @Override
        public String operationString() {
            StringBuilder buf = new StringBuilder(super.operationString());
            buf.append("\ndefault: [").append(defaultTarget).append(']');
            int key = lowKey;
            for (LabelRef l : targets) {
                buf.append("\ncase ").append(key).append(": [").append(l).append(']');
                key++;
            }
            return buf.toString();
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Alive && index == 0) {
                return EnumSet.of(OperandFlag.Register);
            } else if (mode == OperandMode.Temp && index == 0) {
                return EnumSet.of(OperandFlag.Register);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static class CondMoveOp extends AMD64LIRInstruction {
        private final ConditionFlag condition;

        public CondMoveOp(Variable result, Condition condition, Variable trueValue, Value falseValue) {
            super("CMOVE", new Value[] {result}, null, new Value[] {falseValue}, new Value[] {trueValue}, LIRInstruction.NO_OPERANDS);
            this.condition = intCond(condition);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            cmove(tasm, masm, output(0), false, condition, false, alive(0), input(0));
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Input && index == 0) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Stack, OperandFlag.Constant);
            } else if (mode == OperandMode.Alive && index == 0) {
                return EnumSet.of(OperandFlag.Register);
            } else if (mode == OperandMode.Output && index == 0) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.RegisterHint);
            }
            throw GraalInternalError.shouldNotReachHere();
        }

        @Override
        public String operationString() {
            return condition.toString() + " " + super.operationString();
        }
    }


    public static class FloatCondMoveOp extends AMD64LIRInstruction {
        private final ConditionFlag condition;
        private final boolean unorderedIsTrue;

        public FloatCondMoveOp(Variable result, Condition condition, boolean unorderedIsTrue, Variable trueValue, Variable falseValue) {
            super("FLOAT_CMOVE", new Value[] {result}, null, LIRInstruction.NO_OPERANDS, new Value[] {trueValue, falseValue}, LIRInstruction.NO_OPERANDS);
            this.condition = floatCond(condition);
            this.unorderedIsTrue = unorderedIsTrue;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            cmove(tasm, masm, output(0), true, condition, unorderedIsTrue, alive(0), alive(1));
        }

        @Override
        public String operationString() {
            return condition.toString() + " unordered=" + unorderedIsTrue + " " + super.operationString();
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Alive && index == 0) {
                return EnumSet.of(OperandFlag.Register);
            } else if (mode == OperandMode.Alive && index == 1) {
                return EnumSet.of(OperandFlag.Register);
            } else if (mode == OperandMode.Output && index == 0) {
                return EnumSet.of(OperandFlag.Register);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    private static void tableswitch(TargetMethodAssembler tasm, AMD64MacroAssembler masm, int lowKey, LabelRef defaultTarget, LabelRef[] targets, CiRegister value, CiRegister scratch) {
        Buffer buf = masm.codeBuffer;
        // Compare index against jump table bounds
        int highKey = lowKey + targets.length - 1;
        if (lowKey != 0) {
            // subtract the low value from the switch value
            masm.subl(value, lowKey);
            masm.cmpl(value, highKey - lowKey);
        } else {
            masm.cmpl(value, highKey);
        }

        // Jump to default target if index is not within the jump table
        masm.jcc(ConditionFlag.above, defaultTarget.label());

        // Set scratch to address of jump table
        int leaPos = buf.position();
        masm.leaq(scratch, new CiAddress(tasm.target.wordKind, AMD64.rip.asValue(), 0));
        int afterLea = buf.position();

        // Load jump table entry into scratch and jump to it
        masm.movslq(value, new CiAddress(Kind.Int, scratch.asValue(), value.asValue(), Scale.Times4, 0));
        masm.addq(scratch, value);
        masm.jmp(scratch);

        // Inserting padding so that jump table address is 4-byte aligned
        if ((buf.position() & 0x3) != 0) {
            masm.nop(4 - (buf.position() & 0x3));
        }

        // Patch LEA instruction above now that we know the position of the jump table
        int jumpTablePos = buf.position();
        buf.setPosition(leaPos);
        masm.leaq(scratch, new CiAddress(tasm.target.wordKind, AMD64.rip.asValue(), jumpTablePos - afterLea));
        buf.setPosition(jumpTablePos);

        // Emit jump table entries
        for (LabelRef target : targets) {
            Label label = target.label();
            int offsetToJumpTableBase = buf.position() - jumpTablePos;
            if (label.isBound()) {
                int imm32 = label.position() - jumpTablePos;
                buf.emitInt(imm32);
            } else {
                label.addPatchAt(buf.position());

                buf.emitByte(0); // psuedo-opcode for jump table entry
                buf.emitShort(offsetToJumpTableBase);
                buf.emitByte(0); // padding to make jump table entry 4 bytes wide
            }
        }

        JumpTable jt = new JumpTable(jumpTablePos, lowKey, highKey, 4);
        tasm.targetMethod.addAnnotation(jt);
    }

    private static void floatJcc(AMD64MacroAssembler masm, ConditionFlag condition, boolean unorderedIsTrue, Label label) {
        Label endLabel = new Label();
        if (unorderedIsTrue && !trueOnUnordered(condition)) {
            masm.jcc(ConditionFlag.parity, label);
        } else if (!unorderedIsTrue && trueOnUnordered(condition)) {
            masm.jcc(ConditionFlag.parity, endLabel);
        }
        masm.jcc(condition, label);
        masm.bind(endLabel);
    }

    private static void cmove(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value result, boolean isFloat, ConditionFlag condition, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        // check that we don't overwrite an input operand before it is used.
        assert !result.equals(trueValue);

        AMD64Move.move(tasm, masm, result, falseValue);
        cmove(tasm, masm, result, condition, trueValue);

        if (isFloat) {
            if (unorderedIsTrue && !trueOnUnordered(condition)) {
                cmove(tasm, masm, result, ConditionFlag.parity, trueValue);
            } else if (!unorderedIsTrue && trueOnUnordered(condition)) {
                cmove(tasm, masm, result, ConditionFlag.parity, falseValue);
            }
        }
    }

    private static void cmove(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Value result, ConditionFlag cond, Value other) {
        if (isRegister(other)) {
            assert asRegister(other) != asRegister(result) : "other already overwritten by previous move";
            switch (other.kind) {
                case Int:  masm.cmovl(cond, asRegister(result), asRegister(other)); break;
                case Long: masm.cmovq(cond, asRegister(result), asRegister(other)); break;
                default:   throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            switch (other.kind) {
                case Int:  masm.cmovl(cond, asRegister(result), tasm.asAddress(other)); break;
                case Long: masm.cmovq(cond, asRegister(result), tasm.asAddress(other)); break;
                default:   throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    private static ConditionFlag intCond(Condition cond) {
        switch (cond) {
            case EQ: return ConditionFlag.equal;
            case NE: return ConditionFlag.notEqual;
            case LT: return ConditionFlag.less;
            case LE: return ConditionFlag.lessEqual;
            case GE: return ConditionFlag.greaterEqual;
            case GT: return ConditionFlag.greater;
            case BE: return ConditionFlag.belowEqual;
            case AE: return ConditionFlag.aboveEqual;
            case AT: return ConditionFlag.above;
            case BT: return ConditionFlag.below;
            default: throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static ConditionFlag floatCond(Condition cond) {
        switch (cond) {
            case EQ: return ConditionFlag.equal;
            case NE: return ConditionFlag.notEqual;
            case LT: return ConditionFlag.below;
            case LE: return ConditionFlag.belowEqual;
            case GE: return ConditionFlag.aboveEqual;
            case GT: return ConditionFlag.above;
            default: throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static boolean trueOnUnordered(ConditionFlag condition) {
        switch(condition) {
            case aboveEqual:
            case notEqual:
            case above:
            case less:
            case overflow:
                return false;
            case equal:
            case belowEqual:
            case below:
            case greaterEqual:
            case noOverflow:
                return true;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
