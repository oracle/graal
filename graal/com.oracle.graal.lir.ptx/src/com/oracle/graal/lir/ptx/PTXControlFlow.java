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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.lir.LIRValueUtil.*;
import static com.oracle.graal.nodes.calc.Condition.*;

import com.oracle.graal.api.code.CompilationResult.JumpTable;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.ptx.PTXAssembler.Global;
import com.oracle.graal.asm.ptx.PTXAssembler.Setp;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Mov;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.SwitchStrategy.BaseSwitchClosure;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.calc.*;

public class PTXControlFlow {

    public static class ReturnOp extends PTXLIRInstruction {

        @Use({REG, ILLEGAL}) protected Value x;

        public ReturnOp(Value x) {
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            crb.frameContext.leave(crb);
            masm.exit();
        }
    }

    public static class ReturnNoValOp extends PTXLIRInstruction implements BlockEndOp {

        public ReturnNoValOp() {
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            crb.frameContext.leave(crb);
            masm.ret();
        }
    }

    public static class BranchOp extends PTXLIRInstruction implements StandardOp.BranchOp {

        protected final Condition condition;
        protected final LabelRef trueDestination;
        protected final LabelRef falseDestination;
        protected int predRegNum;

        public BranchOp(Condition condition, LabelRef trueDestination, LabelRef falseDestination, int predReg) {
            this.condition = condition;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.predRegNum = predReg;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            if (crb.isSuccessorEdge(trueDestination)) {
                masm.bra(masm.nameOf(falseDestination.label()), predRegNum, false);
            } else {
                masm.bra(masm.nameOf(trueDestination.label()), predRegNum, true);
                if (!crb.isSuccessorEdge(falseDestination)) {
                    masm.jmp(falseDestination.label());
                }
            }
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
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            cmove(crb, masm, result, false, condition, false, trueValue, falseValue, predicate);
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
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            cmove(crb, masm, result, true, condition, unorderedIsTrue, trueValue, falseValue, predicate);
        }
    }

    private static void cmove(CompilationResultBuilder crb, PTXMacroAssembler asm, Value result, boolean isFloat, Condition condition, boolean unorderedIsTrue, Value trueValue, Value falseValue,
                    int predicateRegister) {
        // check that we don't overwrite an input operand before it is used.
        assert !result.equals(trueValue);

        PTXMove.move(crb, asm, result, falseValue);
        cmove(asm, result, trueValue, predicateRegister);

        if (isFloat) {
            if (unorderedIsTrue && !trueOnUnordered(condition)) {
                // cmove(crb, masm, result, ConditionFlag.Parity, trueValue);
                throw GraalInternalError.unimplemented();
            } else if (!unorderedIsTrue && trueOnUnordered(condition)) {
                // cmove(crb, masm, result, ConditionFlag.Parity, falseValue);
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

    public static class StrategySwitchOp extends PTXLIRInstruction implements BlockEndOp {

        @Use({CONST}) protected Constant[] keyConstants;
        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        @Alive({REG}) protected Value key;
        @Temp({REG, ILLEGAL}) protected Value scratch;
        private final SwitchStrategy strategy;
        // Number of predicate register that would be set by this instruction.
        protected int predRegNum;

        public StrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Value key, Value scratch, int predReg) {
            this.strategy = strategy;
            this.keyConstants = strategy.keyConstants;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
            this.scratch = scratch;
            assert keyConstants.length == keyTargets.length;
            assert keyConstants.length == strategy.keyProbabilities.length;
            assert (scratch.getKind() == Kind.Illegal) == (key.getKind() == Kind.Int || key.getKind() == Kind.Long);
            predRegNum = predReg;
        }

        @Override
        public void emitCode(final CompilationResultBuilder crb, final PTXMacroAssembler masm) {
            BaseSwitchClosure closure = new BaseSwitchClosure(crb, masm, keyTargets, defaultTarget) {
                @Override
                protected void conditionalJump(int index, Condition condition, Label target) {
                    switch (key.getKind()) {
                        case Int:
                        case Long:
                            if (crb.codeCache.needsDataPatch(keyConstants[index])) {
                                crb.recordInlineDataInCode(keyConstants[index]);
                            }
                            new Setp(EQ, keyConstants[index], key, predRegNum).emit(masm);
                            break;
                        case Object:
                            assert condition == Condition.EQ || condition == Condition.NE;
                            PTXMove.move(crb, masm, scratch, keyConstants[index]);
                            new Setp(condition, scratch, key, predRegNum).emit(masm);
                            break;
                        default:
                            throw new GraalInternalError("switch only supported for int, long and object");
                    }
                    masm.bra(masm.nameOf(target), predRegNum, true);
                }
            };
            strategy.run(closure);
        }
    }

    public static class TableSwitchOp extends PTXLIRInstruction implements BlockEndOp {

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
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            // Compare index against jump table bounds

            int highKey = lowKey + targets.length - 1;
            if (lowKey != 0) {
                // subtract the low value from the switch value
                // new Sub(value, value, lowKey).emit(masm);
                new Setp(GT, index, Constant.forInt(highKey - lowKey), predRegNum).emit(masm);
            } else {
                new Setp(GT, index, Constant.forInt(highKey), predRegNum).emit(masm);
            }

            // Jump to default target if index is not within the jump table
            if (defaultTarget != null) {
                masm.bra(masm.nameOf(defaultTarget.label()), predRegNum, true);
            }

            // address of jump table
            int tablePos = masm.position();

            JumpTable jt = new JumpTable(tablePos, lowKey, highKey, 4);
            String name = "jumptable" + jt.position;

            new Global(index, name, targets).emit(masm);

            // bra(Value, name);

            crb.compilationResult.addAnnotation(jt);
        }
    }
}
