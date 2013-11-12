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
package com.oracle.graal.lir.ptx;

import static com.oracle.graal.asm.ptx.PTXAssembler.*;
import static com.oracle.graal.asm.ptx.PTXMacroAssembler.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.lir.LIRValueUtil.*;
import static com.oracle.graal.nodes.calc.Condition.*;

import com.oracle.graal.api.code.CompilationResult.JumpTable;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.FallThroughOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.calc.*;

public class PTXControlFlow {

    public static class ReturnOp extends PTXLIRInstruction {

        @Use({REG, ILLEGAL}) protected Value x;

        public ReturnOp(Value x) {
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXMacroAssembler masm) {
            if (tasm.frameContext != null) {
                tasm.frameContext.leave(tasm);
            }
            masm.exit();
        }
    }

    public static class ReturnNoValOp extends PTXLIRInstruction {

        public ReturnNoValOp() {
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXMacroAssembler masm) {
            if (tasm.frameContext != null) {
                tasm.frameContext.leave(tasm);
            }
            masm.ret();
        }
    }

    public static class BranchOp extends PTXLIRInstruction implements StandardOp.BranchOp {

        protected Condition condition;
        protected LabelRef destination;
        protected int predRegNum;

        public BranchOp(Condition condition, LabelRef destination, int predReg) {
            this.condition = condition;
            this.destination = destination;
            this.predRegNum = predReg;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXMacroAssembler masm) {
            masm.bra(masm.nameOf(destination.label()), predRegNum);
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
    }

    public static class CondMoveOp extends PTXLIRInstruction {

        @Def({REG, HINT}) protected Value result;
        @Alive({REG}) protected Value trueValue;
        @Use({REG, STACK, CONST}) protected Value falseValue;
        private final Condition condition;
        private final int predicate;

        public CondMoveOp(Variable result, Condition condition, Variable trueValue, Value falseValue, int predicateRegister) {
            this.result = result;
            this.condition = condition;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
            this.predicate = predicateRegister;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXMacroAssembler masm) {
            cmove(tasm, masm, result, false, condition, false, trueValue, falseValue, predicate);
        }
    }

    public static class FloatCondMoveOp extends PTXLIRInstruction {

        @Def({REG}) protected Value result;
        @Alive({REG}) protected Value trueValue;
        @Alive({REG}) protected Value falseValue;
        private final Condition condition;
        private final boolean unorderedIsTrue;
        private final int predicate;

        public FloatCondMoveOp(Variable result, Condition condition, boolean unorderedIsTrue, Variable trueValue, Variable falseValue, int predicateRegister) {
            this.result = result;
            this.condition = condition;
            this.unorderedIsTrue = unorderedIsTrue;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
            this.predicate = predicateRegister;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXMacroAssembler masm) {
            cmove(tasm, masm, result, true, condition, unorderedIsTrue, trueValue, falseValue, predicate);
        }
    }

    private static void cmove(TargetMethodAssembler tasm, PTXMacroAssembler asm, Value result, boolean isFloat, Condition condition, boolean unorderedIsTrue, Value trueValue, Value falseValue,
                    int predicateRegister) {
        // check that we don't overwrite an input operand before it is used.
        assert !result.equals(trueValue);

        PTXMove.move(tasm, asm, result, falseValue);
        cmove(asm, result, trueValue, predicateRegister);

        if (isFloat) {
            if (unorderedIsTrue && !trueOnUnordered(condition)) {
                // cmove(tasm, masm, result, ConditionFlag.Parity, trueValue);
                throw GraalInternalError.unimplemented();
            } else if (!unorderedIsTrue && trueOnUnordered(condition)) {
                // cmove(tasm, masm, result, ConditionFlag.Parity, falseValue);
                throw GraalInternalError.unimplemented();
            }
        }
    }

    private static boolean trueOnUnordered(Condition condition) {
        switch (condition) {
            case NE:
            case EQ:
                return false;
            case LT:
            case GE:
                return true;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void cmove(PTXMacroAssembler asm, Value result, Value other, int predicateRegister) {
        if (isVariable(other)) {
            assert !asVariable(other).equals(asVariable(result)) : "other already overwritten by previous move";

            switch (other.getKind()) {
                case Int:
                    new Mov(asVariable(result), other, predicateRegister).emit(asm);
                    break;
                case Long:
                    new Mov(asVariable(result), other, predicateRegister).emit(asm);
                    break;
                default:
                    throw new InternalError("unhandled: " + other.getKind());
            }
        } else {
            throw GraalInternalError.shouldNotReachHere("cmove: not register");
        }
    }

    public static class SequentialSwitchOp extends PTXLIRInstruction implements FallThroughOp {

        @Use({CONST}) protected Constant[] keyConstants;
        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        @Alive({REG}) protected Value key;
        @Temp({REG, ILLEGAL}) protected Value scratch;
        // Number of predicate register that would be set by this instruction.
        protected int predRegNum;

        public SequentialSwitchOp(Constant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Value key, Value scratch, int predReg) {
            assert keyConstants.length == keyTargets.length;
            this.keyConstants = keyConstants;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
            this.scratch = scratch;
            predRegNum = predReg;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXMacroAssembler masm) {
            Kind keyKind = key.getKind();

            if (keyKind == Kind.Int || keyKind == Kind.Long) {
                for (int i = 0; i < keyConstants.length; i++) {
                    if (tasm.codeCache.needsDataPatch(keyConstants[i])) {
                        tasm.recordDataReferenceInCode(keyConstants[i], 0, true);
                    }
                    new Setp(EQ, keyConstants[i], key, predRegNum).emit(masm);
                    masm.bra(masm.nameOf(keyTargets[i].label()), predRegNum);
                }
            } else if (keyKind == Kind.Object) {
                for (int i = 0; i < keyConstants.length; i++) {
                    PTXMove.move(tasm, masm, scratch, keyConstants[i]);
                    new Setp(EQ, keyConstants[i], scratch, predRegNum).emit(masm);
                    masm.bra(keyTargets[i].label().toString(), predRegNum);
                }
            } else {
                throw new GraalInternalError("sequential switch only supported for int, long and object");
            }
            if (defaultTarget != null) {
                masm.jmp(defaultTarget.label());
            } else {
                // masm.hlt();
            }
        }

        @Override
        public LabelRef fallThroughTarget() {
            return defaultTarget;
        }

        @Override
        public void setFallThroughTarget(LabelRef target) {
            defaultTarget = target;
        }
    }

    public static class TableSwitchOp extends PTXLIRInstruction {

        private final int lowKey;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;
        @Alive protected Value index;
        @Temp protected Value scratch;
        // Number of predicate register that would be set by this instruction.
        protected int predRegNum;

        public TableSwitchOp(final int lowKey, final LabelRef defaultTarget, final LabelRef[] targets, Variable index, Variable scratch, int predReg) {
            this.lowKey = lowKey;
            this.defaultTarget = defaultTarget;
            this.targets = targets;
            this.index = index;
            this.scratch = scratch;
            predRegNum = predReg;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXMacroAssembler masm) {
            tableswitch(tasm, masm, lowKey, defaultTarget, targets, index, scratch, predRegNum);
        }
    }

    @SuppressWarnings("unused")
    private static void tableswitch(TargetMethodAssembler tasm, PTXAssembler masm, int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value value, Value scratch, int predNum) {
        Buffer buf = masm.codeBuffer;

        // Compare index against jump table bounds

        int highKey = lowKey + targets.length - 1;
        if (lowKey != 0) {
            // subtract the low value from the switch value
            // new Sub(value, value, lowKey).emit(masm);
            new Setp(GT, value, Constant.forInt(highKey - lowKey), predNum).emit(masm);
        } else {
            new Setp(GT, value, Constant.forInt(highKey), predNum).emit(masm);
        }

        // Jump to default target if index is not within the jump table
        if (defaultTarget != null) {
            masm.bra(masm.nameOf(defaultTarget.label()), predNum);
        }

        // address of jump table
        int tablePos = buf.position();

        JumpTable jt = new JumpTable(tablePos, lowKey, highKey, 4);
        String name = "jumptable" + jt.position;

        new Global(value, name, targets).emit(masm);

        // bra(Value, name);

        tasm.compilationResult.addAnnotation(jt);

    }
}
