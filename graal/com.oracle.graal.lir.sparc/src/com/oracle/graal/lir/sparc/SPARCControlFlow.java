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
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.SwitchStrategy.BaseSwitchClosure;
import com.oracle.graal.lir.asm.*;

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

    public static class BranchOp extends SPARCLIRInstruction implements StandardOp.BranchOp {
        // TODO: Conditioncode/flag handling needs to be improved;
        protected final Condition condition;
        protected final ConditionFlag conditionFlag;
        protected final LabelRef trueDestination;
        protected final LabelRef falseDestination;
        protected final Kind kind;

        public BranchOp(ConditionFlag condition, LabelRef trueDestination, LabelRef falseDestination, Kind kind) {
            this.conditionFlag = condition;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
            this.condition = null;
        }

        public BranchOp(Condition condition, LabelRef trueDestination, LabelRef falseDestination, Kind kind) {
            this.condition = condition;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
            this.conditionFlag = null;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            assert condition == null && conditionFlag != null || condition != null && conditionFlag == null;
            Label actualTarget;
            Condition actualCondition;
            ConditionFlag actualConditionFlag;
            boolean needJump;
            if (crb.isSuccessorEdge(trueDestination)) {
                actualCondition = condition != null ? condition.negate() : null;
                actualConditionFlag = conditionFlag != null ? conditionFlag.negate() : null;
                actualTarget = falseDestination.label();
                needJump = false;
            } else {
                actualCondition = condition;
                actualConditionFlag = conditionFlag;
                actualTarget = trueDestination.label();
                needJump = !crb.isSuccessorEdge(falseDestination);
            }
            assert kind == Kind.Int || kind == Kind.Long || kind == Kind.Object || kind == Kind.Double || kind == Kind.Float : kind;
            if (kind == Kind.Double || kind == Kind.Float) {
                emitFloatCompare(masm, actualTarget, actualCondition);
            } else {
                CC cc = kind == Kind.Int ? CC.Icc : CC.Xcc;
                if (actualCondition != null) {
                    emitCompare(masm, actualTarget, actualCondition, cc);
                } else if (actualConditionFlag != null) {
                    emitCompare(masm, actualTarget, actualConditionFlag);
                } else {
                    GraalInternalError.shouldNotReachHere();
                }
                new Nop().emit(masm);  // delay slot
            }
            if (needJump) {
                masm.jmp(falseDestination.label());
            }
        }
    }

    private static void emitFloatCompare(SPARCMacroAssembler masm, Label target, Condition actualCondition) {
        switch (actualCondition) {
            case EQ:
                new Fbe(false, target).emit(masm);
                break;
            case NE:
                new Fbne(false, target).emit(masm);
                break;
            case LT:
                new Fbl(false, target).emit(masm);
                break;
            case LE:
                new Fble(false, target).emit(masm);
                break;
            case GT:
                new Fbg(false, target).emit(masm);
                break;
            case GE:
                new Fbge(false, target).emit(masm);
                break;
            case AE:
            case AT:
            case BT:
            case BE:
                GraalInternalError.unimplemented("Should not be required for float/dobule");
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        new Nop().emit(masm);
    }

    private static void emitCompare(SPARCMacroAssembler masm, Label target, ConditionFlag actualCondition) {
        new Fmt00b(false, actualCondition, Op2s.Br, target).emit(masm);
    }

    private static void emitCompare(SPARCMacroAssembler masm, Label target, Condition actualCondition, CC cc) {
        switch (actualCondition) {
            case EQ:
                new Bpe(cc, target).emit(masm);
                break;
            case NE:
                new Bpne(cc, target).emit(masm);
                break;
            case BT:
                new Bplu(cc, target).emit(masm);
                break;
            case LT:
                new Bpl(cc, target).emit(masm);
                break;
            case BE:
                new Bpleu(cc, target).emit(masm);
                break;
            case LE:
                new Bple(cc, target).emit(masm);
                break;
            case GE:
                new Bpge(cc, target).emit(masm);
                break;
            case AE:
                new Bpgeu(cc, target).emit(masm);
                break;
            case GT:
                new Bpg(cc, target).emit(masm);
                break;
            case AT:
                new Bpgu(cc, target).emit(masm);
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
                            emitCompare(masm, target, condition, CC.Icc);
                            break;
                        case Long: {
                            SPARCMove.move(crb, masm, scratch, keyConstants[index]);
                            new Cmp(keyRegister, asLongReg(scratch)).emit(masm);
                            emitCompare(masm, target, condition, CC.Xcc);
                            break;
                        }
                        case Object: {
                            SPARCMove.move(crb, masm, scratch, keyConstants[index]);
                            new Cmp(keyRegister, asObjectReg(scratch)).emit(masm);
                            emitCompare(masm, target, condition, CC.Ptrcc);
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
                new Setx(lowKey, g3).emit(masm);
                new Sub(value, g3, scratchReg).emit(masm);
            }
            int upperLimit = highKey - lowKey;
            if (isSimm13(upperLimit)) {
                new Cmp(scratchReg, upperLimit).emit(masm);
            } else {
                new Setx(upperLimit, g3).emit(masm);
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
            new Rdpc(g3).emit(masm);

            // The jump table follows four instructions after rdpc
            new Add(scratchReg, 4 * 4, scratchReg).emit(masm);
            new Jmpl(g3, scratchReg, g0).emit(masm);
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
        @Alive({REG}) protected Value trueValue;
        @Use({REG, STACK, CONST}) protected Value falseValue;

        private final ConditionFlag condition;

        public CondMoveOp(Kind kind, Variable result, Condition condition, Variable trueValue, Value falseValue) {
            this.kind = kind;
            this.result = result;
            this.condition = intCond(condition);
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            // check that we don't overwrite an input operand before it is used.
            assert !result.equals(trueValue);

            SPARCMove.move(crb, masm, result, falseValue);
            cmove(crb, masm, kind, result, condition, trueValue);
        }
    }

    @Opcode("CMOVE")
    public static class FloatCondMoveOp extends SPARCLIRInstruction {

        private final Kind kind;

        @Def({REG}) protected Value result;
        @Alive({REG}) protected Value trueValue;
        @Alive({REG}) protected Value falseValue;

        private final ConditionFlag condition;
        private final boolean unorderedIsTrue;

        public FloatCondMoveOp(Kind kind, Variable result, Condition condition, boolean unorderedIsTrue, Variable trueValue, Variable falseValue) {
            this.kind = kind;
            this.result = result;
            this.condition = floatCond(condition);
            this.unorderedIsTrue = unorderedIsTrue;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            // check that we don't overwrite an input operand before it is used.
            assert !result.equals(trueValue);

            SPARCMove.move(crb, masm, result, trueValue);
            cmove(crb, masm, kind, result, condition, falseValue);
            // TODO: This may be omitted, when doing the right check beforehand (There are
            // instructions which control the unordered behavior as well)
            if (!unorderedIsTrue) {
                cmove(crb, masm, kind, result, ConditionFlag.F_Unordered, falseValue);
            }
        }
    }

    private static void cmove(CompilationResultBuilder crb, SPARCMacroAssembler masm, Kind kind, Value result, ConditionFlag cond, Value other) {
        if (!isRegister(other)) {
            SPARCMove.move(crb, masm, result, other);
            throw GraalInternalError.shouldNotReachHere("result should be scratch");
        }
        assert !asRegister(other).equals(asRegister(result)) : "other already overwritten by previous move";
        switch (kind) {
            case Int:
                new Movcc(cond, CC.Icc, asRegister(other), asRegister(result)).emit(masm);
                break;
            case Long:
            case Object:
                new Movcc(cond, CC.Xcc, asRegister(other), asRegister(result)).emit(masm);
                break;
            case Float:
            case Double:
                switch (cond) {
                    case Equal:
                        new Fbne(true, 2 * 4).emit(masm);
                        break;
                    case Greater:
                        new Fble(true, 2 * 4).emit(masm);
                        break;
                    case GreaterEqual:
                        new Fbl(true, 2 * 4).emit(masm);
                        break;
                    case Less:
                        new Fbge(true, 2 * 4).emit(masm);
                        break;
                    case LessEqual:
                        new Fbg(true, 2 * 4).emit(masm);
                        break;
                    case F_Ordered:
                        new Fbo(true, 2 * 4).emit(masm);
                        break;
                    case F_Unordered:
                        new Fbu(true, 2 * 4).emit(masm);
                        break;
                    default:
                        GraalInternalError.shouldNotReachHere("Unknown condition code " + cond);
                        break;
                }
                SPARCMove.move(crb, masm, result, other);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static ConditionFlag intCond(Condition cond) {
        switch (cond) {
            case EQ:
                return ConditionFlag.Equal;
            case NE:
                return ConditionFlag.NotEqual;
            case BT:
                return ConditionFlag.LessUnsigned;
            case LT:
                return ConditionFlag.Less;
            case BE:
                return ConditionFlag.LessEqualUnsigned;
            case LE:
                return ConditionFlag.LessEqual;
            case AE:
                return ConditionFlag.GreaterEqualUnsigned;
            case GE:
                return ConditionFlag.GreaterEqual;
            case AT:
                return ConditionFlag.GreaterUnsigned;
            case GT:
                return ConditionFlag.Greater;
            default:
                throw GraalInternalError.shouldNotReachHere("Unimplemented for: " + cond);
        }
    }

    private static ConditionFlag floatCond(Condition cond) {
        switch (cond) {
            case EQ:
                return ConditionFlag.Equal;
            case NE:
                return ConditionFlag.NotEqual;
            case LT:
                return ConditionFlag.Less;
            case LE:
                return ConditionFlag.LessEqual;
            case GE:
                return ConditionFlag.GreaterEqual;
            case GT:
                return ConditionFlag.Greater;
            default:
                throw GraalInternalError.shouldNotReachHere("Unimplemented for " + cond);
        }
    }
}
