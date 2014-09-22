/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.SwitchStrategy.BaseSwitchClosure;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.sparc.*;

public class SPARCControlFlow {

    public static class ReturnOp extends SPARCLIRInstruction implements BlockEndOp {

        @Use({REG, ILLEGAL}) protected Value x;

        public ReturnOp(Value x) {
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitCodeHelper(crb, masm);
        }

        public static void emitCodeHelper(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            new Ret().emit(masm);
            // On SPARC we always leave the frame (in the delay slot).
            crb.frameContext.leave(crb);
        }
    }

    public static class CompareBranchOp extends SPARCLIRInstruction implements BlockEndOp, DelaySlotHolder {

        private final SPARCCompare opcode;
        @Use({REG}) protected Value x;
        @Use({REG, CONST}) protected Value y;
        protected final Condition condition;
        protected final LabelRef trueDestination;
        protected final LabelRef falseDestination;
        protected final Kind kind;
        protected final boolean unorderedIsTrue;
        private boolean emitted = false;
        private double trueDestinationProbability;

        public CompareBranchOp(SPARCCompare opcode, Value x, Value y, Condition condition, LabelRef trueDestination, LabelRef falseDestination, Kind kind, boolean unorderedIsTrue,
                        double trueDestinationProbability) {
            this.opcode = opcode;
            this.x = x;
            this.y = y;
            this.condition = condition;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
            this.unorderedIsTrue = unorderedIsTrue;
            this.trueDestinationProbability = trueDestinationProbability;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (!emitted) {
                SPARCCompare.emit(crb, masm, opcode, x, y);
                emitted = emitBranch(crb, masm, true);
            }
            assert emitted;
        }

        public void emitForDelay(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCCompare.emit(crb, masm, opcode, x, y);
            emitted = emitBranch(crb, masm, false);
        }

        public boolean emitBranch(CompilationResultBuilder crb, SPARCMacroAssembler masm, boolean withDelayedNop) {
            Label actualTarget;
            Condition actualCondition;
            boolean branchOnUnordered;
            boolean needJump;
            boolean predictBranchTaken;
            if (crb.isSuccessorEdge(trueDestination)) {
                actualCondition = condition != null ? condition.negate() : null;
                actualTarget = falseDestination.label();
                predictBranchTaken = trueDestinationProbability < .5; // false branch needs jump
                needJump = false;
                branchOnUnordered = !unorderedIsTrue;
            } else {
                actualCondition = condition;
                actualTarget = trueDestination.label();
                needJump = !crb.isSuccessorEdge(falseDestination);
                predictBranchTaken = trueDestinationProbability >= .5;
                branchOnUnordered = unorderedIsTrue;
            }
            if (!withDelayedNop && needJump) {
                // We cannot make use of the delay slot when we jump in true-case and false-case
                return false;
            }
            if (kind == Kind.Double || kind == Kind.Float) {
                emitFloatBranch(masm, actualTarget, actualCondition, branchOnUnordered);
            } else {
                CC cc = kind == Kind.Int ? CC.Icc : CC.Xcc;
                assert actualCondition != null;
                SPARCControlFlow.emitBranch(masm, actualTarget, actualCondition, cc, predictBranchTaken);
            }
            if (withDelayedNop) {
                new Nop().emit(masm);  // delay slot
            }
            if (needJump) {
                masm.jmp(falseDestination.label());
            }
            return true; // emitted
        }
    }

    public static class BranchOp extends SPARCLIRInstruction implements StandardOp.BranchOp {
        // TODO: Conditioncode/flag handling needs to be improved;
        protected final Condition condition;
        protected final ConditionFlag conditionFlag;
        protected final LabelRef trueDestination;
        protected final LabelRef falseDestination;
        protected final Kind kind;
        protected final boolean unorderedIsTrue;

        public BranchOp(ConditionFlag condition, LabelRef trueDestination, LabelRef falseDestination, Kind kind) {
            this.conditionFlag = condition;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
            this.condition = null;
            this.unorderedIsTrue = true;
        }

        public BranchOp(Condition condition, LabelRef trueDestination, LabelRef falseDestination, Kind kind) {
            this.condition = condition;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
            this.conditionFlag = null;
            this.unorderedIsTrue = true;
        }

        public BranchOp(Condition finalCondition, LabelRef trueDestination, LabelRef falseDestination, Kind kind, boolean unorderedIsTrue) {
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
            this.conditionFlag = null;
            this.unorderedIsTrue = unorderedIsTrue;
            this.condition = finalCondition;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            assert condition == null && conditionFlag != null || condition != null && conditionFlag == null;
            Label actualTarget;
            Condition actualCondition;
            ConditionFlag actualConditionFlag;
            boolean branchOnUnordered;
            boolean needJump;
            if (crb.isSuccessorEdge(trueDestination)) {
                actualCondition = condition != null ? condition.negate() : null;
                actualConditionFlag = conditionFlag != null ? conditionFlag.negate() : null;
                actualTarget = falseDestination.label();
                needJump = false;
                branchOnUnordered = !unorderedIsTrue;
            } else {
                actualCondition = condition;
                actualConditionFlag = conditionFlag;
                actualTarget = trueDestination.label();
                needJump = !crb.isSuccessorEdge(falseDestination);
                branchOnUnordered = unorderedIsTrue;
            }
            assert kind == Kind.Int || kind == Kind.Long || kind == Kind.Object || kind == Kind.Double || kind == Kind.Float : kind;
            if (kind == Kind.Double || kind == Kind.Float) {
                emitFloatBranch(masm, actualTarget, actualCondition, branchOnUnordered);
            } else {
                CC cc = kind == Kind.Int ? CC.Icc : CC.Xcc;
                if (actualCondition != null) {
                    emitBranch(masm, actualTarget, actualCondition, cc, false);
                } else if (actualConditionFlag != null) {
                    emitBranch(masm, actualTarget, actualConditionFlag);
                } else {
                    GraalInternalError.shouldNotReachHere();
                }
            }
            new Nop().emit(masm);  // delay slot
            if (needJump) {
                masm.jmp(falseDestination.label());
            }
        }
    }

    private static void emitFloatBranch(SPARCMacroAssembler masm, Label target, Condition actualCondition, boolean branchOnUnordered) {
        switch (actualCondition) {
            case EQ:
                if (branchOnUnordered) {
                    new Fbue(false, target).emit(masm);
                } else {
                    new Fbe(false, target).emit(masm);
                }
                break;
            case NE:
                new Fbne(false, target).emit(masm); // Is also unordered
                break;
            case LT:
                if (branchOnUnordered) {
                    new Fbul(false, target).emit(masm);
                } else {
                    new Fbl(false, target).emit(masm);
                }
                break;
            case LE:
                if (branchOnUnordered) {
                    new Fbule(false, target).emit(masm);
                } else {
                    new Fble(false, target).emit(masm);
                }
                break;
            case GT:
                if (branchOnUnordered) {
                    new Fbug(false, target).emit(masm);
                } else {
                    new Fbg(false, target).emit(masm);
                }
                break;
            case GE:
                if (branchOnUnordered) {
                    new Fbuge(false, target).emit(masm);
                } else {
                    new Fbge(false, target).emit(masm);
                }
                break;
            case AE:
            case AT:
            case BT:
            case BE:
                GraalInternalError.unimplemented("Should not be required for float/dobule");
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void emitBranch(SPARCMacroAssembler masm, Label target, ConditionFlag actualCondition) {
        new Fmt00b(false, actualCondition, Op2s.Br, target).emit(masm);
    }

    private static void emitBranch(SPARCMacroAssembler masm, Label target, Condition actualCondition, CC cc, boolean predictTaken) {

        switch (actualCondition) {
            case EQ:
                new Bpe(cc, false, predictTaken, target).emit(masm);
                break;
            case NE:
                new Bpne(cc, false, predictTaken, target).emit(masm);
                break;
            case BT:
                new Bplu(cc, false, predictTaken, target).emit(masm);
                break;
            case LT:
                new Bpl(cc, false, predictTaken, target).emit(masm);
                break;
            case BE:
                new Bpleu(cc, false, predictTaken, target).emit(masm);
                break;
            case LE:
                new Bple(cc, false, predictTaken, target).emit(masm);
                break;
            case GE:
                new Bpge(cc, false, predictTaken, target).emit(masm);
                break;
            case AE:
                new Bpgeu(cc, false, predictTaken, target).emit(masm);
                break;
            case GT:
                new Bpg(cc, false, predictTaken, target).emit(masm);
                break;
            case AT:
                new Bpgu(cc, false, predictTaken, target).emit(masm);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static class StrategySwitchOp extends SPARCLIRInstruction implements BlockEndOp {
        @Use({CONST}) protected Constant[] keyConstants;
        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        @Alive({REG}) protected Value key;
        @Temp({REG}) protected Value scratch;
        private final SwitchStrategy strategy;

        public StrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Value key, Value scratch) {
            this.strategy = strategy;
            this.keyConstants = strategy.keyConstants;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
            this.scratch = scratch;
            assert keyConstants.length == keyTargets.length;
            assert keyConstants.length == strategy.keyProbabilities.length;
        }

        @Override
        public void emitCode(final CompilationResultBuilder crb, final SPARCMacroAssembler masm) {
            final Register keyRegister = asRegister(key);

            BaseSwitchClosure closure = new BaseSwitchClosure(crb, masm, keyTargets, defaultTarget) {
                @Override
                protected void conditionalJump(int index, Condition condition, Label target) {
                    switch (key.getKind()) {
                        case Char:
                        case Byte:
                        case Short:
                        case Int:
                            if (crb.codeCache.needsDataPatch(keyConstants[index])) {
                                crb.recordInlineDataInCode(keyConstants[index]);
                            }
                            long lc = keyConstants[index].asLong();
                            if (SPARCAssembler.isSimm13(lc)) {
                                assert NumUtil.isInt(lc);
                                new Cmp(keyRegister, (int) lc).emit(masm);
                            } else {
                                new Setx(lc, asIntReg(scratch)).emit(masm);
                                new Cmp(keyRegister, asIntReg(scratch)).emit(masm);
                            }
                            emitBranch(masm, target, condition, CC.Icc, false);
                            break;
                        case Long: {
                            SPARCMove.move(crb, masm, scratch, keyConstants[index], DelaySlotHolder.DUMMY);
                            new Cmp(keyRegister, asLongReg(scratch)).emit(masm);
                            emitBranch(masm, target, condition, CC.Xcc, false);
                            break;
                        }
                        case Object: {
                            SPARCMove.move(crb, masm, scratch, keyConstants[index], DelaySlotHolder.DUMMY);
                            new Cmp(keyRegister, asObjectReg(scratch)).emit(masm);
                            emitBranch(masm, target, condition, CC.Ptrcc, false);
                            break;
                        }
                        default:
                            throw new GraalInternalError("switch only supported for int, long and object");
                    }
                    new Nop().emit(masm);  // delay slot
                }
            };
            strategy.run(closure);
        }
    }

    public static class TableSwitchOp extends SPARCLIRInstruction implements BlockEndOp {

        private final int lowKey;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;
        @Alive protected Value index;
        @Temp protected Value scratch;

        public TableSwitchOp(final int lowKey, final LabelRef defaultTarget, final LabelRef[] targets, Variable index, Variable scratch) {
            this.lowKey = lowKey;
            this.defaultTarget = defaultTarget;
            this.targets = targets;
            this.index = index;
            this.scratch = scratch;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Register value = asIntReg(index);
            Register scratchReg = asLongReg(scratch);

            // Compare index against jump table bounds
            int highKey = lowKey + targets.length - 1;

            // subtract the low value from the switch value
            if (isSimm13(lowKey)) {
                new Sub(value, lowKey, scratchReg).emit(masm);
            } else {
                try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                    Register scratch2 = sc.getRegister();
                    new Setx(lowKey, scratch2).emit(masm);
                    new Sub(value, scratch2, scratchReg).emit(masm);
                }
            }
            int upperLimit = highKey - lowKey;
            try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                Register scratch2 = sc.getRegister();
                if (isSimm13(upperLimit)) {
                    new Cmp(scratchReg, upperLimit).emit(masm);
                } else {
                    new Setx(upperLimit, scratch2).emit(masm);
                    new Cmp(scratchReg, upperLimit).emit(masm);
                }

                // Jump to default target if index is not within the jump table
                if (defaultTarget != null) {
                    new Bpgu(CC.Icc, defaultTarget.label()).emit(masm);
                    new Nop().emit(masm);  // delay slot
                }

                // Load jump table entry into scratch and jump to it
                new Sll(scratchReg, 3, scratchReg).emit(masm); // Multiply by 8
                // Zero the left bits sll with shcnt>0 does not mask upper 32 bits
                new Srl(scratchReg, 0, scratchReg).emit(masm);
                new Rdpc(scratch2).emit(masm);

                // The jump table follows four instructions after rdpc
                new Add(scratchReg, 4 * 4, scratchReg).emit(masm);
                new Jmpl(scratch2, scratchReg, g0).emit(masm);
            }
            new Nop().emit(masm);
            // new Sra(value, 3, value).emit(masm); // delay slot, correct the value (division by 8)

            // Emit jump table entries
            for (LabelRef target : targets) {
                new Bpa(target.label()).emit(masm);
                new Nop().emit(masm); // delay slot
            }
        }
    }

    @Opcode("CMOVE")
    public static class CondMoveOp extends SPARCLIRInstruction {

        private final Kind kind;

        @Def({REG, HINT}) protected Value result;
        @Use({REG, CONST}) protected Value trueValue;
        @Use({REG, CONST}) protected Value falseValue;

        private final ConditionFlag condition;
        private final CC cc;

        public CondMoveOp(Kind kind, Variable result, CC cc, ConditionFlag condition, Value trueValue, Value falseValue) {
            this.kind = kind;
            this.result = result;
            this.condition = condition;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
            this.cc = cc;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            // check that we don't overwrite an input operand before it is used.
            // assert !result.equals(trueValue);
            if (result.equals(trueValue)) { // We have the true value in place, do he opposite
                cmove(masm, cc, kind, result, condition.negate(), falseValue);
            } else if (result.equals(falseValue)) {
                cmove(masm, cc, kind, result, condition, trueValue);
            } else { // We have to move one of the input values to the result
                ConditionFlag actualCondition = condition;
                Value actualTrueValue = trueValue;
                Value actualFalseValue = falseValue;
                if (isConstant(falseValue) && isSimm11(asConstant(falseValue))) {
                    actualCondition = condition.negate();
                    actualTrueValue = falseValue;
                    actualFalseValue = trueValue;
                }
                SPARCMove.move(crb, masm, result, actualFalseValue, DelaySlotHolder.DUMMY);
                cmove(masm, cc, kind, result, actualCondition, actualTrueValue);
            }
        }
    }

    private static void cmove(SPARCMacroAssembler masm, CC cc, Kind kind, Value result, ConditionFlag cond, Value other) {
        switch (kind) {
            case Int:
                if (isConstant(other)) {
                    int constant;
                    if (asConstant(other).isNull()) {
                        constant = 0;
                    } else {
                        constant = asConstant(other).asInt();
                    }
                    new Movcc(cond, cc, constant, asRegister(result)).emit(masm);
                } else {
                    new Movcc(cond, cc, asRegister(other), asRegister(result)).emit(masm);
                }
                break;
            case Long:
            case Object:
                if (isConstant(other)) {
                    long constant;
                    if (asConstant(other).isNull()) {
                        constant = 0;
                    } else {
                        constant = asConstant(other).asLong();
                    }
                    assert isSimm11(constant);
                    new Movcc(cond, cc, (int) constant, asRegister(result)).emit(masm);
                } else {
                    new Movcc(cond, cc, asRegister(other), asRegister(result)).emit(masm);
                }
                break;
            case Float:
                new Fmovscc(cond, cc, asFloatReg(other), asFloatReg(result)).emit(masm);
                break;
            case Double:
                new Fmovdcc(cond, cc, asDoubleReg(other), asDoubleReg(result)).emit(masm);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
