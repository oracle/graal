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
package com.oracle.graal.lir.hsail;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.hsail.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.SwitchStrategy.BaseSwitchClosure;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.calc.*;

/**
 * Implementation of control flow instructions.
 */
public class HSAILControlFlow {

    /**
     * This class represents the LIR instruction that the HSAIL backend generates for a switch
     * construct in Java.
     *
     * The HSAIL backend compiles switch statements into a series of cascading compare and branch
     * instructions because this is the currently the recommended way to generate optimally
     * performing HSAIL code. Thus the execution path for both the TABLESWITCH and LOOKUPSWITCH
     * bytecodes go through this op.
     */
    public static class StrategySwitchOp extends HSAILLIRInstruction implements BlockEndOp {
        /**
         * The array of key constants used for the cases of this switch statement.
         */
        @Use({CONST}) protected Constant[] keyConstants;
        /**
         * The branch target labels that correspond to each case.
         */
        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        /**
         * The key of the switch. This will be compared with each of the keyConstants.
         */
        @Alive({REG}) protected Value key;

        private final SwitchStrategy strategy;

        /**
         * Constructor. Called from the HSAILLIRGenerator.emitStrategySwitch routine.
         */
        public StrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Value key) {
            this.strategy = strategy;
            this.keyConstants = strategy.keyConstants;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
            assert keyConstants.length == keyTargets.length;
            assert keyConstants.length == strategy.keyProbabilities.length;
        }

        /**
         * Generates the code for this switch op.
         *
         * The keys for switch statements in Java bytecode for of type int. However, Graal also
         * generates a TypeSwitchNode (for method dispatch) which triggers the invocation of these
         * routines with keys of type Long or Object. Currently we only support the
         * IntegerSwitchNode so we throw an exception if the key isn't of type int.
         *
         * @param crb the CompilationResultBuilder
         * @param masm the HSAIL assembler
         */
        @Override
        public void emitCode(CompilationResultBuilder crb, final HSAILAssembler masm) {
            BaseSwitchClosure closure = new BaseSwitchClosure(crb, masm, keyTargets, defaultTarget) {
                @Override
                protected void conditionalJump(int index, Condition condition, Label target) {
                    switch (key.getKind()) {
                        case Int:
                        case Long:
                            // Generate cascading compare and branches for each case.
                            masm.emitCompare(key, keyConstants[index], HSAILCompare.conditionToString(condition), false, false);
                            masm.cbr(masm.nameOf(target));
                            break;
                        case Object:
                        default:
                            throw new GraalInternalError("switch only supported for int");
                    }
                }
            };
            strategy.run(closure);
        }
    }

    public static class ReturnOp extends HSAILLIRInstruction implements BlockEndOp {

        @Use({REG, ILLEGAL}) protected Value x;

        public ReturnOp(Value x) {
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            crb.frameContext.leave(crb);
            masm.exit();
        }
    }

    /***
     * The ALIVE annotation is so we can get a scratch32 register that does not clobber
     * actionAndReason.
     */
    public static class DeoptimizeOp extends ReturnOp {

        @Alive({REG, CONST}) protected Value actionAndReason;
        @State protected LIRFrameState frameState;
        protected MetaAccessProvider metaAccessProvider;
        protected String emitName;
        protected int codeBufferPos = -1;
        protected int dregOopMap = 0;

        public DeoptimizeOp(Value actionAndReason, LIRFrameState frameState, String emitName, MetaAccessProvider metaAccessProvider) {
            super(Value.ILLEGAL);   // return with no ret value
            this.actionAndReason = actionAndReason;
            this.frameState = frameState;
            this.emitName = emitName;
            this.metaAccessProvider = metaAccessProvider;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            String reasonString;
            if (isConstant(actionAndReason)) {
                DeoptimizationReason reason = metaAccessProvider.decodeDeoptReason((Constant) actionAndReason);
                reasonString = reason.toString();
            } else {
                reasonString = "Variable Reason";
            }

            masm.emitComment("// " + emitName + ", Deoptimization for " + reasonString);

            if (frameState == null) {
                masm.emitComment("// frameState == null");
                // and emit the return
                super.emitCode(crb, masm);
                return;
            }
            // get a unique codeBuffer position
            // when we save our state, we will save this as well (it can be used as a key to get the
            // debugInfo)
            codeBufferPos = masm.position();

            // get the bitmap of $d regs that contain references
            ReferenceMap referenceMap = frameState.debugInfo().getReferenceMap();
            for (int dreg = HSAIL.d0.number; dreg <= HSAIL.d15.number; dreg++) {
                if (referenceMap.getRegister(dreg) == Kind.Object) {
                    dregOopMap |= 1 << (dreg - HSAIL.d0.number);
                }
            }

            // here we will by convention use some never-allocated registers to pass to the epilogue
            // deopt code
            // todo: define these in HSAIL.java
            // we need to pass the actionAndReason and the codeBufferPos

            AllocatableValue actionAndReasonReg = HSAIL.s32.asValue(Kind.Int);
            AllocatableValue codeBufferOffsetReg = HSAIL.s33.asValue(Kind.Int);
            AllocatableValue dregOopMapReg = HSAIL.s39.asValue(Kind.Int);
            masm.emitMov(Kind.Int, actionAndReasonReg, actionAndReason);
            masm.emitMov(Kind.Int, codeBufferOffsetReg, Constant.forInt(codeBufferPos));
            masm.emitMov(Kind.Int, dregOopMapReg, Constant.forInt(dregOopMap));
            masm.emitJumpToLabelName(masm.getDeoptLabelName());

            // now record the debuginfo
            crb.recordInfopoint(codeBufferPos, frameState, InfopointReason.IMPLICIT_EXCEPTION);
        }

        public LIRFrameState getFrameState() {
            return frameState;
        }

        public int getCodeBufferPos() {
            return codeBufferPos;
        }
    }

    public static class UnwindOp extends ReturnOp {

        protected String commentMessage;

        public UnwindOp(String commentMessage) {
            super(Value.ILLEGAL);   // return with no ret value
            this.commentMessage = commentMessage;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            masm.emitComment("// " + commentMessage);
            super.emitCode(crb, masm);
        }
    }

    public static class ForeignCallNoArgOp extends HSAILLIRInstruction {

        @Def({REG}) protected Value out;
        String callName;

        public ForeignCallNoArgOp(String callName, Value out) {
            this.out = out;
            this.callName = callName;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            masm.emitComment("//ForeignCall to " + callName + " would have gone here");
        }
    }

    public static class ForeignCall1ArgOp extends ForeignCallNoArgOp {

        @Use({REG, ILLEGAL}) protected Value arg1;

        public ForeignCall1ArgOp(String callName, Value out, Value arg1) {
            super(callName, out);
            this.arg1 = arg1;
        }
    }

    public static class ForeignCall2ArgOp extends ForeignCall1ArgOp {

        @Use({REG, ILLEGAL}) protected Value arg2;

        public ForeignCall2ArgOp(String callName, Value out, Value arg1, Value arg2) {
            super(callName, out, arg1);
            this.arg2 = arg2;
        }
    }

    public static class CompareBranchOp extends HSAILLIRInstruction implements StandardOp.BranchOp {

        @Opcode protected final HSAILCompare opcode;
        @Use({REG, CONST}) protected Value x;
        @Use({REG, CONST}) protected Value y;
        @Def({REG}) protected Value z;
        protected final Condition condition;
        protected final LabelRef trueDestination;
        protected final LabelRef falseDestination;
        @Def({REG}) protected Value result;
        protected final boolean unordered;

        public CompareBranchOp(HSAILCompare opcode, Condition condition, Value x, Value y, Value z, Value result, LabelRef trueDestination, LabelRef falseDestination, boolean unordered) {
            this.condition = condition;
            this.opcode = opcode;
            this.x = x;
            this.y = y;
            this.z = z;
            this.result = result;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.unordered = unordered;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            if (crb.isSuccessorEdge(trueDestination)) {
                HSAILCompare.emit(crb, masm, condition.negate(), x, y, z, !unordered);
                masm.cbr(masm.nameOf(falseDestination.label()));
            } else {
                HSAILCompare.emit(crb, masm, condition, x, y, z, unordered);
                masm.cbr(masm.nameOf(trueDestination.label()));
                if (!crb.isSuccessorEdge(falseDestination)) {
                    masm.jmp(falseDestination.label());
                }
            }
        }
    }

    public static class CondMoveOp extends HSAILLIRInstruction {

        @Opcode protected final HSAILCompare opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value trueValue;
        @Use({REG, STACK, CONST}) protected Value falseValue;
        @Use({REG, STACK, CONST}) protected Value left;
        @Use({REG, STACK, CONST}) protected Value right;
        protected final Condition condition;

        public CondMoveOp(HSAILCompare opcode, Variable left, Variable right, Variable result, Condition condition, Value trueValue, Value falseValue) {
            this.opcode = opcode;
            this.result = result;
            this.left = left;
            this.right = right;
            this.condition = condition;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            HSAILCompare.emit(crb, masm, condition, left, right, right, false);
            cmove(masm, result, trueValue, falseValue);
        }
    }

    public static class FloatCondMoveOp extends CondMoveOp {

        private final boolean unorderedIsTrue;

        public FloatCondMoveOp(HSAILCompare opcode, Variable left, Variable right, Variable result, Condition condition, boolean unorderedIsTrue, Variable trueValue, Value falseValue) {
            super(opcode, left, right, result, condition, trueValue, falseValue);
            this.unorderedIsTrue = unorderedIsTrue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            HSAILCompare.emit(crb, masm, condition, left, right, right, unorderedIsTrue);
            cmove(masm, result, trueValue, falseValue);
        }
    }

    private static void cmove(HSAILAssembler masm, Value result, Value trueValue, Value falseValue) {
        // Check that we don't overwrite an input operand before it is used.
        assert (result.getKind() == trueValue.getKind() && result.getKind() == falseValue.getKind());
        int width;
        switch (result.getKind()) {
        /**
         * We don't need to pass the cond to the assembler. We will always use $c0 as the control
         * register.
         */
            case Float:
            case Int:
                width = 32;
                break;
            case Double:
            case Long:
                width = 64;
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        masm.emitConditionalMove(result, trueValue, falseValue, width);
    }
}
