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
import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.calc.*;

import static com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;

public class SPARCControlFlow {

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
            int sourceIndex = crb.getCurrentBlockIndex();
            Label actualTarget;
            Condition actualCondition;
            boolean needJump;
            if (trueDestination.isCodeEmittingOrderSuccessorEdge(sourceIndex)) {
                actualCondition = condition.negate();
                actualTarget = falseDestination.label();
                needJump = false;
            } else {
                actualCondition = condition;
                actualTarget = trueDestination.label();
                needJump = !falseDestination.isCodeEmittingOrderSuccessorEdge(sourceIndex);
            }
            assert kind == Kind.Int || kind == Kind.Long || kind == Kind.Object;
            CC cc = kind == Kind.Int ? CC.Icc : CC.Xcc;
            switch (actualCondition) {
                case EQ:
                    new Bpe(cc, actualTarget).emit(masm);
                    break;
                case NE:
                    new Bpne(cc, actualTarget).emit(masm);
                    break;
                case BT:
                    new Bplu(cc, actualTarget).emit(masm);
                    break;
                case LT:
                    new Bpl(cc, actualTarget).emit(masm);
                    break;
                case BE:
                    new Bpleu(cc, actualTarget).emit(masm);
                    break;
                case LE:
                    new Bple(cc, actualTarget).emit(masm);
                    break;
                case GE:
                    new Bpge(cc, actualTarget).emit(masm);
                    break;
                case AE:
                    new Bpgeu(cc, actualTarget).emit(masm);
                    break;
                case GT:
                    new Bpg(cc, actualTarget).emit(masm);
                    break;
                case AT:
                    new Bpgu(cc, actualTarget).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
            new Nop().emit(masm);  // delay slot
            if (needJump) {
                masm.jmp(falseDestination.label());
            }
        }
    }

    @Opcode("CMOVE")
    public static class CondMoveOp extends SPARCLIRInstruction {

        @Def({REG, HINT}) protected Value result;
        @Alive({REG}) protected Value trueValue;
        @Use({REG, STACK, CONST}) protected Value falseValue;

        private final ConditionFlag condition;

        public CondMoveOp(Variable result, Condition condition, Variable trueValue, Value falseValue) {
            this.result = result;
            this.condition = intCond(condition);
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            cmove(crb, masm, result, false, condition, false, trueValue, falseValue);
        }
    }

    @Opcode("CMOVE")
    public static class FloatCondMoveOp extends SPARCLIRInstruction {

        @Def({REG}) protected Value result;
        @Alive({REG}) protected Value trueValue;
        @Alive({REG}) protected Value falseValue;
        private final ConditionFlag condition;
        private final boolean unorderedIsTrue;

        public FloatCondMoveOp(Variable result, Condition condition, boolean unorderedIsTrue, Variable trueValue, Variable falseValue) {
            this.result = result;
            this.condition = floatCond(condition);
            this.unorderedIsTrue = unorderedIsTrue;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            cmove(crb, masm, result, true, condition, unorderedIsTrue, trueValue, falseValue);
        }
    }

    private static void cmove(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, boolean isFloat, ConditionFlag condition, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        // check that we don't overwrite an input operand before it is used.
        assert !result.equals(trueValue);

        SPARCMove.move(crb, masm, result, falseValue);
        cmove(crb, masm, result, condition, trueValue);

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

    private static void cmove(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, ConditionFlag cond, Value other) {
        if (!isRegister(other)) {
            SPARCMove.move(crb, masm, result, other);
            throw new InternalError("result should be scratch");
        }
        assert !asRegister(other).equals(asRegister(result)) : "other already overwritten by previous move";
        switch (other.getKind()) {
            case Int:
                // XXX CC depends on compare
                new Movcc(cond, CC.Icc, asRegister(other), asRegister(result)).emit(masm);
                break;
            case Long:
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

    public static class ReturnOp extends SPARCLIRInstruction {

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

    public static class SequentialSwitchOp extends SPARCLIRInstruction implements BlockEndOp {

        @Use({CONST}) protected Constant[] keyConstants;
        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        @Alive({REG}) protected Value key;
        @Temp({REG, ILLEGAL}) protected Value scratch;

        public SequentialSwitchOp(Constant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Value key, Value scratch) {
            assert keyConstants.length == keyTargets.length;
            this.keyConstants = keyConstants;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
            this.scratch = scratch;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (key.getKind() == Kind.Int) {
                Register intKey = asIntReg(key);
                for (int i = 0; i < keyConstants.length; i++) {
                    if (crb.codeCache.needsDataPatch(keyConstants[i])) {
                        crb.recordDataReferenceInCode(keyConstants[i], 0, true);
                    }
                    long lc = keyConstants[i].asLong();
                    assert NumUtil.isInt(lc);
                    new Cmp(intKey, (int) lc).emit(masm);
                    new Bpe(CC.Icc, keyTargets[i].label()).emit(masm);
                    new Nop().emit(masm);  // delay slot
                }
            } else if (key.getKind() == Kind.Long) {
                Register longKey = asLongReg(key);
                Register temp = asLongReg(scratch);
                for (int i = 0; i < keyConstants.length; i++) {
                    SPARCMove.move(crb, masm, temp.asValue(Kind.Long), keyConstants[i]);
                    new Cmp(longKey, temp).emit(masm);
                    new Bpe(CC.Xcc, keyTargets[i].label()).emit(masm);
                    new Nop().emit(masm);  // delay slot
                }
            } else if (key.getKind() == Kind.Object) {
                Register objectKey = asObjectReg(key);
                Register temp = asObjectReg(scratch);
                for (int i = 0; i < keyConstants.length; i++) {
                    SPARCMove.move(crb, masm, temp.asValue(Kind.Object), keyConstants[i]);
                    new Cmp(objectKey, temp).emit(masm);
                    new Bpe(CC.Ptrcc, keyTargets[i].label()).emit(masm);
                    new Nop().emit(masm);  // delay slot
                }
            } else {
                throw new GraalInternalError("sequential switch only supported for int, long and object");
            }
            if (defaultTarget != null) {
                if (!defaultTarget.isCodeEmittingOrderSuccessorEdge(crb.getCurrentBlockIndex())) {
                    masm.jmp(defaultTarget.label());
                }
            } else {
                new Illtrap(0).emit(masm);
            }
        }
    }

    public static class SwitchRangesOp extends SPARCLIRInstruction implements BlockEndOp {

        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        private final int[] lowKeys;
        private final int[] highKeys;
        @Alive protected Value key;

        public SwitchRangesOp(int[] lowKeys, int[] highKeys, LabelRef[] keyTargets, LabelRef defaultTarget, Value key) {
            this.lowKeys = lowKeys;
            this.highKeys = highKeys;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            assert isSorted(lowKeys) && isSorted(highKeys);

            Label actualDefaultTarget = defaultTarget == null ? new Label() : defaultTarget.label();
            int prevHighKey = 0;
            boolean skipLowCheck = false;
            for (int i = 0; i < lowKeys.length; i++) {
                int lowKey = lowKeys[i];
                int highKey = highKeys[i];
                if (lowKey == highKey) {
                    // masm.cmpl(asIntReg(key), lowKey);
                    // masm.jcc(ConditionFlag.Equal, keyTargets[i].label());
                    skipLowCheck = false;
                } else {
                    if (!skipLowCheck || (prevHighKey + 1) != lowKey) {
                        // masm.cmpl(asIntReg(key), lowKey);
                        // masm.jcc(ConditionFlag.Less, actualDefaultTarget);
                    }
                    // masm.cmpl(asIntReg(key), highKey);
                    // masm.jcc(ConditionFlag.LessEqual, keyTargets[i].label());
                    skipLowCheck = true;
                }
                prevHighKey = highKey;
            }

            if (defaultTarget != null) {
                if (!defaultTarget.isCodeEmittingOrderSuccessorEdge(crb.getCurrentBlockIndex())) {
                    new Bpa(defaultTarget.label()).emit(masm);
                }
            } else {
                masm.bind(actualDefaultTarget);
                new Illtrap(0).emit(masm);
            }
            throw GraalInternalError.unimplemented();
        }

        @Override
        protected void verify() {
            super.verify();
            assert lowKeys.length == keyTargets.length;
            assert highKeys.length == keyTargets.length;
            assert key.getKind() == Kind.Int;
        }

        private static boolean isSorted(int[] values) {
            for (int i = 1; i < values.length; i++) {
                if (values[i - 1] >= values[i]) {
                    return false;
                }
            }
            return true;
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
            tableswitch(crb, masm, lowKey, defaultTarget, targets, asIntReg(index), asLongReg(scratch));
        }
    }

    private static void tableswitch(CompilationResultBuilder crb, SPARCAssembler masm, int lowKey, LabelRef defaultTarget, LabelRef[] targets, Register value, Register scratch) {
        Buffer buf = masm.codeBuffer;
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
        new Jmp(new SPARCAddress(scratch, 0)).emit(masm);
        new Nop().emit(masm);  // delay slot

        // address of jump table
        int tablePos = buf.position();

        JumpTable jt = new JumpTable(tablePos, lowKey, highKey, 4);
        crb.compilationResult.addAnnotation(jt);

        // SPARC: unimp: tableswitch extract
        throw GraalInternalError.unimplemented();
    }
}
