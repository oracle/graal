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
import com.oracle.graal.lir.StandardOp.FallThroughOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.calc.*;

import static com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;

public class SPARCControlFlow {

    public static class BranchOp extends SPARCLIRInstruction implements StandardOp.BranchOp {

        protected Condition condition;
        protected LabelRef destination;

        public BranchOp(Condition condition, LabelRef destination) {
            this.condition = condition;
            this.destination = destination;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
            // FIXME Using xcc is wrong! It depends on the compare.
            switch (condition) {
                case EQ:
                    new Bpe(CC.Xcc, destination.label()).emit(masm);
                    break;
                case NE:
                    new Bpne(CC.Xcc, destination.label()).emit(masm);
                    break;
                case BE:
                    new Bpleu(CC.Xcc, destination.label()).emit(masm);
                    break;
                case LE:
                    new Bple(CC.Xcc, destination.label()).emit(masm);
                    break;
                case AE:
                    new Bpgeu(CC.Xcc, destination.label()).emit(masm);
                    break;
                case GT:
                    new Bpg(CC.Xcc, destination.label()).emit(masm);
                    break;
                case AT:
                    new Bpgu(CC.Xcc, destination.label()).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
            new Nop().emit(masm);  // delay slot
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
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
            cmove(tasm, masm, result, false, condition, false, trueValue, falseValue);
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
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
            cmove(tasm, masm, result, true, condition, unorderedIsTrue, trueValue, falseValue);
        }
    }

    private static void cmove(TargetMethodAssembler tasm, SPARCMacroAssembler masm, Value result, boolean isFloat, ConditionFlag condition, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        // check that we don't overwrite an input operand before it is used.
        assert !result.equals(trueValue);

        SPARCMove.move(tasm, masm, result, falseValue);
        cmove(tasm, masm, result, condition, trueValue);

        if (isFloat) {
            if (unorderedIsTrue && !trueOnUnordered(condition)) {
                // cmove(tasm, masm, result, ConditionFlag.Parity, trueValue);
            } else if (!unorderedIsTrue && trueOnUnordered(condition)) {
                // cmove(tasm, masm, result, ConditionFlag.Parity, falseValue);
            }
        }
    }

    private static void cmove(TargetMethodAssembler tasm, SPARCMacroAssembler masm, Value result, ConditionFlag cond, Value other) {
        if (!isRegister(other)) {
            SPARCMove.move(tasm, masm, result, other);
            throw new InternalError("result should be scratch");
        }
        assert !asRegister(other).equals(asRegister(result)) : "other already overwritten by previous move";
        switch (other.getKind()) {
            case Int:
                new Movcc(cond, CC.Icc, asRegister(other), asRegister(result)).emit(masm);
                throw new InternalError("check instruction");
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
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
            new Ret().emit(masm);
            // On SPARC we always leave the frame.
            tasm.frameContext.leave(tasm);
        }
    }

    public static class SequentialSwitchOp extends SPARCLIRInstruction implements FallThroughOp {

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
        @SuppressWarnings("unused")
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
            if (key.getKind() == Kind.Int) {
                Register intKey = asIntReg(key);
                for (int i = 0; i < keyConstants.length; i++) {
                    if (tasm.runtime.needsDataPatch(keyConstants[i])) {
                        tasm.recordDataReferenceInCode(keyConstants[i], 0, true);
                    }
                    long lc = keyConstants[i].asLong();
                    assert NumUtil.isInt(lc);
                    new Cmp(intKey, (int) lc).emit(masm);
                    Label l = keyTargets[i].label();
                    l.addPatchAt(tasm.asm.codeBuffer.position());
                    new Bpe(CC.Icc, l).emit(masm);
                }
            } else if (key.getKind() == Kind.Long) {
                Register longKey = asLongReg(key);
                for (int i = 0; i < keyConstants.length; i++) {
                    // masm.setp_eq_s64(tasm.asLongConst(keyConstants[i]),
                    // longKey);
                    // masm.at();
                    Label l = keyTargets[i].label();
                    l.addPatchAt(tasm.asm.codeBuffer.position());
                    new Bpe(CC.Xcc, l).emit(masm);
                }
            } else if (key.getKind() == Kind.Object) {
                Register intKey = asObjectReg(key);
                Register temp = asObjectReg(scratch);
                for (int i = 0; i < keyConstants.length; i++) {
                    SPARCMove.move(tasm, masm, temp.asValue(Kind.Object), keyConstants[i]);
                    new Cmp(intKey, temp).emit(masm);
                    new Bpe(CC.Ptrcc, keyTargets[i].label()).emit(masm);
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

    public static class SwitchRangesOp extends SPARCLIRInstruction implements FallThroughOp {

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
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
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
                new Bpa(defaultTarget.label()).emit(masm);
            } else {
                masm.bind(actualDefaultTarget);
                // masm.hlt();
            }
            throw new InternalError("NYI");
        }

        @Override
        protected void verify() {
            super.verify();
            assert lowKeys.length == keyTargets.length;
            assert highKeys.length == keyTargets.length;
            assert key.getKind() == Kind.Int;
        }

        @Override
        public LabelRef fallThroughTarget() {
            return defaultTarget;
        }

        @Override
        public void setFallThroughTarget(LabelRef target) {
            defaultTarget = target;
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

    public static class TableSwitchOp extends SPARCLIRInstruction {

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
        public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
            tableswitch(tasm, masm, lowKey, defaultTarget, targets, asIntReg(index), asLongReg(scratch));
        }
    }

    private static void tableswitch(TargetMethodAssembler tasm, SPARCAssembler masm, int lowKey, LabelRef defaultTarget, LabelRef[] targets, Register value, Register scratch) {
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
        tasm.compilationResult.addAnnotation(jt);

        // SPARC: unimp: tableswitch extract
        throw new InternalError("NYI");
    }
}
