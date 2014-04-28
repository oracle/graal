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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.CompilationResult.JumpTable;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.Bpe;
import com.oracle.graal.asm.sparc.SPARCAssembler.Bpg;
import com.oracle.graal.asm.sparc.SPARCAssembler.Bpge;
import com.oracle.graal.asm.sparc.SPARCAssembler.Bpgu;
import com.oracle.graal.asm.sparc.SPARCAssembler.Bpl;
import com.oracle.graal.asm.sparc.SPARCAssembler.Bple;
import com.oracle.graal.asm.sparc.SPARCAssembler.Bpleu;
import com.oracle.graal.asm.sparc.SPARCAssembler.Bpne;
import com.oracle.graal.asm.sparc.SPARCAssembler.CC;
import com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag;
import com.oracle.graal.asm.sparc.SPARCAssembler.Movcc;
import com.oracle.graal.asm.sparc.SPARCAssembler.Sub;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Bpgeu;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Bplu;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Cmp;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Jmp;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Nop;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Ret;
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

        protected final Condition condition;
        protected final LabelRef trueDestination;
        protected final LabelRef falseDestination;
        protected final Kind kind;

        public BranchOp(Condition condition, LabelRef trueDestination, LabelRef falseDestination, Kind kind) {
            this.condition = condition;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Label actualTarget;
            Condition actualCondition;
            boolean needJump;
            if (crb.isSuccessorEdge(trueDestination)) {
                actualCondition = condition.negate();
                actualTarget = falseDestination.label();
                needJump = false;
            } else {
                actualCondition = condition;
                actualTarget = trueDestination.label();
                needJump = !crb.isSuccessorEdge(falseDestination);
            }
            assert kind == Kind.Int || kind == Kind.Long || kind == Kind.Object;
            CC cc = kind == Kind.Int ? CC.Icc : CC.Xcc;
            emitCompare(masm, actualTarget, actualCondition, cc);
            new Nop().emit(masm);  // delay slot
            if (needJump) {
                masm.jmp(falseDestination.label());
            }
        }
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
        @Temp({REG, ILLEGAL}) protected Value scratch;
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
            assert (scratch.getKind() == Kind.Illegal) == (key.getKind() == Kind.Int);
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
                            assert NumUtil.isInt(lc);
                            new Cmp(keyRegister, (int) lc).emit(masm);
                            emitCompare(masm, target, condition, CC.Icc);
                            break;
                        case Long: {
                            Register temp = asLongReg(scratch);
                            SPARCMove.move(crb, masm, temp.asValue(Kind.Long), keyConstants[index]);
                            new Cmp(keyRegister, temp).emit(masm);
                            emitCompare(masm, target, condition, CC.Xcc);
                            break;
                        }
                        case Object: {
                            Register temp = asObjectReg(scratch);
                            SPARCMove.move(crb, masm, temp.asValue(Kind.Object), keyConstants[index]);
                            new Cmp(keyRegister, temp).emit(masm);
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
            if (lowKey != 0) {
                // subtract the low value from the switch value
                new Sub(value, lowKey, value).emit(masm);
                // masm.setp_gt_s32(value, highKey - lowKey);
            } else {
                // masm.setp_gt_s32(value, highKey);
            }

            // Jump to default target if index is not within the jump table
            if (defaultTarget != null) {
                new Bpgu(CC.Icc, defaultTarget.label()).emit(masm);
                new Nop().emit(masm);  // delay slot
            }

            // Load jump table entry into scratch and jump to it
            // masm.movslq(value, new AMD64Address(scratch, value, Scale.Times4, 0));
            // masm.addq(scratch, value);
            new Jmp(new SPARCAddress(scratchReg, 0)).emit(masm);
            new Nop().emit(masm);  // delay slot

            // address of jump table
            int tablePos = masm.position();

            JumpTable jt = new JumpTable(tablePos, lowKey, highKey, 4);
            crb.compilationResult.addAnnotation(jt);

            // SPARC: unimp: tableswitch extract
            throw GraalInternalError.unimplemented();
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

            SPARCMove.move(crb, masm, result, falseValue);
            cmove(crb, masm, kind, result, condition, trueValue);

            if (unorderedIsTrue && !trueOnUnordered(condition)) {
                // cmove(crb, masm, result, ConditionFlag.Parity, trueValue);
                throw GraalInternalError.unimplemented();
            } else if (!unorderedIsTrue && trueOnUnordered(condition)) {
                // cmove(crb, masm, result, ConditionFlag.Parity, falseValue);
                throw GraalInternalError.unimplemented();
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
            case LT:
                return ConditionFlag.Less;
            case LE:
                return ConditionFlag.LessEqual;
            case GE:
                return ConditionFlag.GreaterEqual;
            case GT:
                return ConditionFlag.Greater;
            case BE:
            case AE:
            case AT:
            case BT:
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static ConditionFlag floatCond(Condition cond) {
        switch (cond) {
            case EQ:
                return ConditionFlag.Equal;
            case NE:
                return ConditionFlag.NotEqual;
            case LT:
            case LE:
            case GE:
            case GT:
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static boolean trueOnUnordered(ConditionFlag condition) {
        switch (condition) {
            case NotEqual:
            case Less:
                return false;
            case Equal:
            case GreaterEqual:
                return true;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
