/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.asm.sparc.SPARCAssembler.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Annul.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.Assembler.LabelHint;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict;
import com.oracle.graal.asm.sparc.SPARCAssembler.CC;
import com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Setx;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.SwitchStrategy.BaseSwitchClosure;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.sparc.SPARC.CPUFeature;

public class SPARCControlFlow {

    public static final class ReturnOp extends SPARCLIRInstruction implements BlockEndOp {
        public static final LIRInstructionClass<ReturnOp> TYPE = LIRInstructionClass.create(ReturnOp.class);

        @Use({REG, ILLEGAL}) protected Value x;

        public ReturnOp(Value x) {
            super(TYPE);
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitCodeHelper(crb, masm);
        }

        public static void emitCodeHelper(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            masm.ret();
            // On SPARC we always leave the frame (in the delay slot).
            crb.frameContext.leave(crb);
        }
    }

    public static final class CompareBranchOp extends SPARCLIRInstruction implements BlockEndOp, SPARCDelayedControlTransfer {
        public static final LIRInstructionClass<CompareBranchOp> TYPE = LIRInstructionClass.create(CompareBranchOp.class);

        private final SPARCCompare opcode;
        @Use({REG}) protected Value x;
        @Use({REG, CONST}) protected Value y;
        private ConditionFlag conditionFlag;
        protected final LabelRef trueDestination;
        protected LabelHint trueDestinationHint;
        protected final LabelRef falseDestination;
        protected LabelHint falseDestinationHint;
        protected final Kind kind;
        protected final boolean unorderedIsTrue;
        private boolean emitted = false;
        private int delaySlotPosition = -1;
        private double trueDestinationProbability;
        // This describes the maximum offset between the first emitted (load constant in to scratch,
        // if does not fit into simm5 of cbcond) instruction and the final branch instruction
        private static int maximumSelfOffsetInstructions = 4;

        public CompareBranchOp(SPARCCompare opcode, Value x, Value y, Condition condition, LabelRef trueDestination, LabelRef falseDestination, Kind kind, boolean unorderedIsTrue,
                        double trueDestinationProbability) {
            super(TYPE);
            this.opcode = opcode;
            this.x = x;
            this.y = y;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
            this.unorderedIsTrue = unorderedIsTrue;
            this.trueDestinationProbability = trueDestinationProbability;
            CC conditionCodeReg = CC.forKind(kind);
            conditionFlag = ConditionFlag.fromCondtition(conditionCodeReg, condition, unorderedIsTrue);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (emitted) { // Only if delayed control transfer is used we must check this
                assert masm.position() - delaySlotPosition == 4 : "Only one instruction can be stuffed into the delay slot";
            }
            if (!emitted) {
                requestHints(masm);
                int targetPosition = getTargetPosition(masm);
                if (canUseShortBranch(crb, masm, targetPosition)) {
                    emitted = emitShortCompareBranch(crb, masm);
                }
                if (!emitted) { // No short compare/branch was used, so we go into fallback
                    SPARCCompare.emit(crb, masm, opcode, x, y);
                    emitted = emitBranch(crb, masm, kind, conditionFlag, trueDestination, falseDestination, true, trueDestinationProbability);
                }
            }
            assert emitted;
        }

        private static int getTargetPosition(Assembler asm) {
            return asm.position() + maximumSelfOffsetInstructions * asm.target.wordSize;
        }

        public void emitControlTransfer(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            requestHints(masm);
            // When we use short branches, no delay slot is available
            int targetPosition = getTargetPosition(masm);
            if (!canUseShortBranch(crb, masm, targetPosition)) {
                SPARCCompare.emit(crb, masm, opcode, x, y);
                emitted = emitBranch(crb, masm, kind, conditionFlag, trueDestination, falseDestination, false, trueDestinationProbability);
                if (emitted) {
                    delaySlotPosition = masm.position();
                }
            }
        }

        private void requestHints(SPARCMacroAssembler masm) {
            if (trueDestinationHint == null) {
                this.trueDestinationHint = masm.requestLabelHint(trueDestination.label());
            }
            if (falseDestinationHint == null) {
                this.falseDestinationHint = masm.requestLabelHint(falseDestination.label());
            }
        }

        /**
         * Tries to use the emit the compare/branch instruction.
         * <p>
         * CBcond has follwing limitations
         * <ul>
         * <li>Immediate field is only 5 bit and is on the right
         * <li>Jump offset is maximum of -+512 instruction
         *
         * <p>
         * We get from outside
         * <ul>
         * <li>at least one of trueDestination falseDestination is within reach of +-512
         * instructions
         * <li>two registers OR one register and a constant which fits simm13
         *
         * <p>
         * We do:
         * <ul>
         * <li>find out which target needs to be branched conditionally
         * <li>find out if fall-through is possible, if not, a unconditional branch is needed after
         * cbcond (needJump=true)
         * <li>if no fall through: we need to put the closer jump into the cbcond branch and the
         * farther into the jmp (unconditional branch)
         * <li>if constant on the left side, mirror to be on the right
         * <li>if constant on right does not fit into simm5, put it into a scratch register
         *
         * @param crb
         * @param masm
         * @return true if the branch could be emitted
         */
        private boolean emitShortCompareBranch(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Value tmpValue;
            Value actualX = x;
            Value actualY = y;
            ConditionFlag actualConditionFlag = conditionFlag;
            Label actualTrueTarget = trueDestination.label();
            Label actualFalseTarget = falseDestination.label();
            Label tmpTarget;
            boolean needJump;
            if (crb.isSuccessorEdge(trueDestination)) {
                actualConditionFlag = conditionFlag.negate();
                tmpTarget = actualTrueTarget;
                actualTrueTarget = actualFalseTarget;
                actualFalseTarget = tmpTarget;
                needJump = false;
            } else {
                needJump = !crb.isSuccessorEdge(falseDestination);
                int targetPosition = getTargetPosition(masm);
                if (needJump && !isShortBranch(masm, targetPosition, trueDestinationHint, actualTrueTarget)) {
                    // we have to jump in either way, so we must put the shorter
                    // branch into the actualTarget as only one of the two jump targets
                    // is guaranteed to be simm10
                    actualConditionFlag = actualConditionFlag.negate();
                    tmpTarget = actualTrueTarget;
                    actualTrueTarget = actualFalseTarget;
                    actualFalseTarget = tmpTarget;
                }
            }
            // Keep the constant on the right
            if (isConstant(actualX)) {
                tmpValue = actualX;
                actualX = actualY;
                actualY = tmpValue;
                actualConditionFlag = actualConditionFlag.mirror();
            }
            boolean isValidConstant = isConstant(actualY) && isSimm5(asConstant(actualY));
            try (ScratchRegister scratch = masm.getScratchRegister()) {
                if (isConstant(actualY) && !isValidConstant) { // Make sure, the y value is loaded
                    Value scratchValue = scratch.getRegister().asValue(actualY.getLIRKind());
                    SPARCMove.move(crb, masm, scratchValue, actualY, SPARCDelayedControlTransfer.DUMMY);
                    actualY = scratchValue;
                }
                // Test if the previous instruction was cbcond, if so, put a nop inbetween (See
                // SPARC Architecture 2011 manual)
                if (masm.isCbcond(masm.getInt(masm.position() - 1))) {
                    masm.nop();
                }
                emitCBCond(masm, actualX, actualY, actualTrueTarget, actualConditionFlag);
            }
            if (needJump) {
                masm.jmp(actualFalseTarget);
                masm.nop();
            }
            return true;
        }

        private static void emitCBCond(SPARCMacroAssembler masm, Value actualX, Value actualY, Label actualTrueTarget, ConditionFlag conditionFlag) {
            switch ((Kind) actualX.getLIRKind().getPlatformKind()) {
                case Byte:
                case Char:
                case Short:
                case Int:
                    if (isConstant(actualY)) {
                        int constantY = asConstant(actualY).asInt();
                        masm.cbcondw(conditionFlag, asIntReg(actualX), constantY, actualTrueTarget);
                    } else {
                        masm.cbcondw(conditionFlag, asIntReg(actualX), asIntReg(actualY), actualTrueTarget);
                    }
                    break;
                case Long:
                    if (isConstant(actualY)) {
                        int constantY = (int) asConstant(actualY).asLong();
                        masm.cbcondx(conditionFlag, asLongReg(actualX), constantY, actualTrueTarget);
                    } else {
                        masm.cbcondx(conditionFlag, asLongReg(actualX), asLongReg(actualY), actualTrueTarget);
                    }
                    break;
                case Object:
                    if (isConstant(actualY)) {
                        // Object constant valid can only be null
                        assert asConstant(actualY).isNull();
                        masm.cbcondx(conditionFlag, asObjectReg(actualX), 0, actualTrueTarget);
                    } else { // this is already loaded
                        masm.cbcondx(conditionFlag, asObjectReg(actualX), asObjectReg(actualY), actualTrueTarget);
                    }
                    break;
                default:
                    GraalInternalError.shouldNotReachHere();
            }
        }

        private boolean canUseShortBranch(CompilationResultBuilder crb, SPARCAssembler asm, int position) {
            if (!asm.hasFeature(CPUFeature.CBCOND)) {
                return false;
            }
            switch ((Kind) x.getPlatformKind()) {
                case Byte:
                case Char:
                case Short:
                case Int:
                case Long:
                case Object:
                    break;
                default:
                    return false;
            }
            boolean hasShortJumpTarget = false;
            if (!crb.isSuccessorEdge(trueDestination)) {
                hasShortJumpTarget |= isShortBranch(asm, position, trueDestinationHint, trueDestination.label());
            }
            if (!crb.isSuccessorEdge(falseDestination)) {
                hasShortJumpTarget |= isShortBranch(asm, position, falseDestinationHint, falseDestination.label());
            }
            return hasShortJumpTarget;
        }

        private static boolean isShortBranch(SPARCAssembler asm, int position, LabelHint hint, Label label) {
            int disp = 0;
            if (label.isBound()) {
                disp = label.position() - position;

            } else if (hint != null && hint.isValid()) {
                disp = hint.getTarget() - hint.getPosition();
            }
            if (disp != 0) {
                if (disp < 0) {
                    disp -= maximumSelfOffsetInstructions * asm.target.wordSize;
                } else {
                    disp += maximumSelfOffsetInstructions * asm.target.wordSize;
                }
                return isSimm10(disp >> 2);
            } else if (hint == null) {
                asm.requestLabelHint(label);
            }
            return false;
        }

        public void resetState() {
            emitted = false;
            delaySlotPosition = -1;
        }
    }

    public static final class BranchOp extends SPARCLIRInstruction implements StandardOp.BranchOp {
        public static final LIRInstructionClass<BranchOp> TYPE = LIRInstructionClass.create(BranchOp.class);
        protected final ConditionFlag conditionFlag;
        protected final LabelRef trueDestination;
        protected final LabelRef falseDestination;
        protected final Kind kind;
        protected final double trueDestinationProbability;

        public BranchOp(ConditionFlag conditionFlag, LabelRef trueDestination, LabelRef falseDestination, Kind kind, double trueDestinationProbability) {
            super(TYPE);
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
            this.conditionFlag = conditionFlag;
            this.trueDestinationProbability = trueDestinationProbability;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitBranch(crb, masm, kind, conditionFlag, trueDestination, falseDestination, true, trueDestinationProbability);
        }
    }

    private static boolean emitBranch(CompilationResultBuilder crb, SPARCMacroAssembler masm, Kind kind, ConditionFlag conditionFlag, LabelRef trueDestination, LabelRef falseDestination,
                    boolean withDelayedNop, double trueDestinationProbability) {
        Label actualTarget;
        ConditionFlag actualConditionFlag;
        boolean needJump;
        BranchPredict predictTaken;
        if (falseDestination != null && crb.isSuccessorEdge(trueDestination)) {
            actualConditionFlag = conditionFlag != null ? conditionFlag.negate() : null;
            actualTarget = falseDestination.label();
            needJump = false;
            predictTaken = trueDestinationProbability < .5d ? PREDICT_TAKEN : PREDICT_NOT_TAKEN;
        } else {
            actualConditionFlag = conditionFlag;
            actualTarget = trueDestination.label();
            needJump = falseDestination != null && !crb.isSuccessorEdge(falseDestination);
            predictTaken = trueDestinationProbability > .5d ? PREDICT_TAKEN : PREDICT_NOT_TAKEN;
        }
        if (!withDelayedNop && needJump) {
            // We cannot make use of the delay slot when we jump in true-case and false-case
            return false;
        }

        if (kind == Kind.Double || kind == Kind.Float) {
            masm.fbpcc(actualConditionFlag, NOT_ANNUL, actualTarget, CC.Fcc0, predictTaken);
        } else {
            CC cc = kind == Kind.Int ? CC.Icc : CC.Xcc;
            masm.bpcc(actualConditionFlag, NOT_ANNUL, actualTarget, cc, predictTaken);
        }
        if (withDelayedNop) {
            masm.nop();  // delay slot
        }
        if (needJump) {
            masm.jmp(falseDestination.label());
        }
        return true;
    }

    public static final class StrategySwitchOp extends SPARCLIRInstruction implements BlockEndOp {
        public static final LIRInstructionClass<StrategySwitchOp> TYPE = LIRInstructionClass.create(StrategySwitchOp.class);
        @Use({CONST}) protected JavaConstant[] keyConstants;
        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        @Alive({REG}) protected Value key;
        @Temp({REG}) protected Value scratch;
        private final SwitchStrategy strategy;

        public StrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Value key, Value scratch) {
            super(TYPE);
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
                    SPARCMove.move(crb, masm, scratch, keyConstants[index], SPARCDelayedControlTransfer.DUMMY);
                    CC conditionCode;
                    Register scratchRegister;
                    switch (key.getKind()) {
                        case Char:
                        case Byte:
                        case Short:
                        case Int:
                            conditionCode = CC.Icc;
                            scratchRegister = asIntReg(scratch);
                            break;
                        case Long: {
                            conditionCode = CC.Xcc;
                            scratchRegister = asLongReg(scratch);
                            break;
                        }
                        case Object: {
                            conditionCode = CC.Ptrcc;
                            scratchRegister = asObjectReg(scratch);
                            break;
                        }
                        default:
                            throw new GraalInternalError("switch only supported for int, long and object");
                    }
                    ConditionFlag conditionFlag = ConditionFlag.fromCondtition(conditionCode, condition, false);
                    masm.cmp(keyRegister, scratchRegister);
                    masm.bpcc(conditionFlag, NOT_ANNUL, target, conditionCode, PREDICT_TAKEN);
                    masm.nop();  // delay slot
                }
            };
            strategy.run(closure);
        }
    }

    public static final class TableSwitchOp extends SPARCLIRInstruction implements BlockEndOp {
        public static final LIRInstructionClass<TableSwitchOp> TYPE = LIRInstructionClass.create(TableSwitchOp.class);

        private final int lowKey;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;
        @Alive protected Value index;
        @Temp protected Value scratch;

        public TableSwitchOp(final int lowKey, final LabelRef defaultTarget, final LabelRef[] targets, Variable index, Variable scratch) {
            super(TYPE);
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
                masm.sub(value, lowKey, scratchReg);
            } else {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register scratch2 = sc.getRegister();
                    new Setx(lowKey, scratch2).emit(masm);
                    masm.sub(value, scratch2, scratchReg);
                }
            }
            int upperLimit = highKey - lowKey;
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch2 = sc.getRegister();
                if (isSimm13(upperLimit)) {
                    masm.cmp(scratchReg, upperLimit);
                } else {
                    new Setx(upperLimit, scratch2).emit(masm);
                    masm.cmp(scratchReg, upperLimit);
                }

                // Jump to default target if index is not within the jump table
                if (defaultTarget != null) {
                    masm.bpcc(GreaterUnsigned, NOT_ANNUL, defaultTarget.label(), Icc, PREDICT_TAKEN);
                    masm.nop();  // delay slot
                }

                // Load jump table entry into scratch and jump to it
                masm.sll(scratchReg, 3, scratchReg); // Multiply by 8
                // Zero the left bits sll with shcnt>0 does not mask upper 32 bits
                masm.srl(scratchReg, 0, scratchReg);
                masm.rdpc(scratch2);

                // The jump table follows four instructions after rdpc
                masm.add(scratchReg, 4 * 4, scratchReg);
                masm.jmpl(scratch2, scratchReg, g0);
            }
            masm.nop();

            // Emit jump table entries
            for (LabelRef target : targets) {
                masm.bpcc(Always, NOT_ANNUL, target.label(), Xcc, PREDICT_TAKEN);
                masm.nop(); // delay slot
            }
        }
    }

    @Opcode("CMOVE")
    public static final class CondMoveOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<CondMoveOp> TYPE = LIRInstructionClass.create(CondMoveOp.class);

        private final Kind kind;

        @Def({REG, HINT}) protected Value result;
        @Use({REG, CONST}) protected Value trueValue;
        @Use({REG, CONST}) protected Value falseValue;

        private final ConditionFlag condition;
        private final CC cc;

        public CondMoveOp(Kind kind, Variable result, CC cc, ConditionFlag condition, Value trueValue, Value falseValue) {
            super(TYPE);
            this.kind = kind;
            this.result = result;
            this.condition = condition;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
            this.cc = cc;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
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
                SPARCMove.move(crb, masm, result, actualFalseValue, SPARCDelayedControlTransfer.DUMMY);
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
                    masm.movcc(cond, cc, constant, asRegister(result));
                } else {
                    masm.movcc(cond, cc, asRegister(other), asRegister(result));
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
                    masm.movcc(cond, cc, (int) constant, asRegister(result));
                } else {
                    masm.movcc(cond, cc, asRegister(other), asRegister(result));
                }
                break;
            case Float:
                masm.fmovscc(cond, cc, asFloatReg(other), asFloatReg(result));
                break;
            case Double:
                masm.fmovdcc(cond, cc, asDoubleReg(other), asDoubleReg(result));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
