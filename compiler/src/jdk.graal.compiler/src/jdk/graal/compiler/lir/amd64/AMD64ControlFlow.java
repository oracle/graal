/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.WORD;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.code.CompilationResult.JumpTable;
import jdk.graal.compiler.code.CompilationResult.JumpTable.EntryFormat;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.SwitchStrategy;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.VMConstant;
import jdk.vm.ci.meta.Value;

public class AMD64ControlFlow {

    public static class BranchOp extends AMD64BlockEndOp implements StandardOp.BranchOp {
        public static final LIRInstructionClass<BranchOp> TYPE = LIRInstructionClass.create(BranchOp.class);
        protected final ConditionFlag condition;
        protected final LabelRef trueDestination;
        protected final LabelRef falseDestination;

        protected final double trueDestinationProbability;

        public BranchOp(Condition condition, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            this(intCond(condition), trueDestination, falseDestination, trueDestinationProbability);
        }

        public BranchOp(ConditionFlag condition, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            this(TYPE, condition, trueDestination, falseDestination, trueDestinationProbability);
        }

        protected BranchOp(LIRInstructionClass<? extends BranchOp> c, ConditionFlag condition, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(c);
            this.condition = condition;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.trueDestinationProbability = trueDestinationProbability;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            /*
             * The strategy for emitting jumps is: If either trueDestination or falseDestination is
             * the successor block, assume the block scheduler did the correct thing and jcc to the
             * other. Otherwise, we need a jcc followed by a jmp. Use the branch probability to make
             * sure it is more likely to branch on the jcc (= less likely to execute both the jcc
             * and the jmp instead of just the jcc). In the case of loops, that means the jcc is the
             * back-edge.
             */
            if (crb.isSuccessorEdge(trueDestination)) {
                jcc(masm, true, falseDestination);
            } else if (crb.isSuccessorEdge(falseDestination)) {
                jcc(masm, false, trueDestination);
            } else if (trueDestinationProbability < 0.5) {
                jcc(masm, true, falseDestination);
                masm.jmp(trueDestination.label());
            } else {
                jcc(masm, false, trueDestination);
                masm.jmp(falseDestination.label());
            }
        }

        protected void jcc(AMD64MacroAssembler masm, boolean negate, LabelRef target) {
            masm.jcc(negate ? condition.negate() : condition, target.label());
        }
    }

    public static class TestByteBranchOp extends BranchOp {

        public static final LIRInstructionClass<TestByteBranchOp> TYPE = LIRInstructionClass.create(TestByteBranchOp.class);

        @LIRInstruction.Use({LIRInstruction.OperandFlag.REG}) protected AllocatableValue x;
        @LIRInstruction.Use({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.STACK}) protected AllocatableValue y;

        public TestByteBranchOp(AllocatableValue x, AllocatableValue y, Condition cond, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(TYPE, intCond(cond), trueDestination, falseDestination, trueDestinationProbability);

            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(y)) {
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.testbAndJcc(asRegister(x), asRegister(y), condition.negate(), falseDestination.label(), false);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.testbAndJcc(asRegister(x), asRegister(y), condition, trueDestination.label(), false);
                } else if (trueDestinationProbability < 0.5) {
                    masm.testbAndJcc(asRegister(x), asRegister(y), condition.negate(), falseDestination.label(), false);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.testbAndJcc(asRegister(x), asRegister(y), condition, trueDestination.label(), false);
                    masm.jmp(falseDestination.label());
                }
            } else {
                assert isStackSlot(y);
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.testbAndJcc(asRegister(x), (AMD64Address) crb.asAddress(y), condition.negate(), falseDestination.label(), false);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.testbAndJcc(asRegister(x), (AMD64Address) crb.asAddress(y), condition, trueDestination.label(), false);
                } else if (trueDestinationProbability < 0.5) {
                    masm.testbAndJcc(asRegister(x), (AMD64Address) crb.asAddress(y), condition.negate(), falseDestination.label(), false);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.testbAndJcc(asRegister(x), (AMD64Address) crb.asAddress(y), condition, trueDestination.label(), false);
                    masm.jmp(falseDestination.label());
                }
            }
        }
    }

    public static class TestBranchOp extends BranchOp implements StandardOp.ImplicitNullCheck {

        public static final LIRInstructionClass<TestBranchOp> TYPE = LIRInstructionClass.create(TestBranchOp.class);

        private final OperandSize size;

        @LIRInstruction.Use({LIRInstruction.OperandFlag.REG}) protected AllocatableValue x;
        @LIRInstruction.Use({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.STACK, LIRInstruction.OperandFlag.COMPOSITE}) protected Value y;

        @LIRInstruction.State protected LIRFrameState state;

        public TestBranchOp(OperandSize size, AllocatableValue x, Value y, LIRFrameState state, Condition cond, LabelRef trueDestination, LabelRef falseDestination,
                        double trueDestinationProbability) {
            super(TYPE, intCond(cond), trueDestination, falseDestination, trueDestinationProbability);
            assert size == WORD || size == DWORD || size == QWORD : size;
            this.size = size;

            this.x = x;
            this.y = y;

            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(y)) {
                assert state == null;
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.testAndJcc(size, asRegister(x), asRegister(y), condition.negate(), falseDestination.label(), false);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.testAndJcc(size, asRegister(x), asRegister(y), condition, trueDestination.label(), false);
                } else if (trueDestinationProbability < 0.5) {
                    masm.testAndJcc(size, asRegister(x), asRegister(y), condition.negate(), falseDestination.label(), false);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.testAndJcc(size, asRegister(x), asRegister(y), condition, trueDestination.label(), false);
                    masm.jmp(falseDestination.label());
                }
                return;
            }
            IntConsumer applyBeforeFusedPair = state == null ? null : pos -> crb.recordImplicitException(pos, state);
            if (isStackSlot(y)) {
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.testAndJcc(size, asRegister(x), (AMD64Address) crb.asAddress(y), condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.testAndJcc(size, asRegister(x), (AMD64Address) crb.asAddress(y), condition, trueDestination.label(), false, applyBeforeFusedPair);
                } else if (trueDestinationProbability < 0.5) {
                    masm.testAndJcc(size, asRegister(x), (AMD64Address) crb.asAddress(y), condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.testAndJcc(size, asRegister(x), (AMD64Address) crb.asAddress(y), condition, trueDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(falseDestination.label());
                }
            } else {
                AMD64AddressValue yAddress = (AMD64AddressValue) y;
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.testAndJcc(size, asRegister(x), yAddress.toAddress(), condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.testAndJcc(size, asRegister(x), yAddress.toAddress(), condition, trueDestination.label(), false, applyBeforeFusedPair);
                } else if (trueDestinationProbability < 0.5) {
                    masm.testAndJcc(size, asRegister(x), yAddress.toAddress(), condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.testAndJcc(size, asRegister(x), yAddress.toAddress(), condition, trueDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(falseDestination.label());
                }
            }
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && y instanceof AMD64AddressValue && ((AMD64AddressValue) y).isValidImplicitNullCheckFor(value, implicitNullCheckLimit)) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    public static class TestConstBranchOp extends BranchOp implements StandardOp.ImplicitNullCheck {

        public static final LIRInstructionClass<TestConstBranchOp> TYPE = LIRInstructionClass.create(TestConstBranchOp.class);

        private final OperandSize size;

        @LIRInstruction.Use({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.STACK, LIRInstruction.OperandFlag.COMPOSITE}) protected Value x;
        private final int y;

        @LIRInstruction.State protected LIRFrameState state;

        public TestConstBranchOp(OperandSize size, Value x, int y, LIRFrameState state, Condition cond, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(TYPE, intCond(cond), trueDestination, falseDestination, trueDestinationProbability);
            assert size == DWORD || size == QWORD : size;
            this.size = size;

            assert x.getPlatformKind().getVectorLength() == 1 : Assertions.errorMessage(x);

            this.x = x;
            this.y = y;

            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(x)) {
                assert state == null;
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.testAndJcc(size, asRegister(x), y, condition.negate(), falseDestination.label(), false);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.testAndJcc(size, asRegister(x), y, condition, trueDestination.label(), false);
                } else if (trueDestinationProbability < 0.5) {
                    masm.testAndJcc(size, asRegister(x), y, condition.negate(), falseDestination.label(), false);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.testAndJcc(size, asRegister(x), y, condition, trueDestination.label(), false);
                    masm.jmp(falseDestination.label());
                }
                return;
            }
            IntConsumer applyBeforeFusedPair = state == null ? null : pos -> crb.recordImplicitException(pos, state);
            if (isStackSlot(x)) {
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.testAndJcc(size, (AMD64Address) crb.asAddress(x), y, condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.testAndJcc(size, (AMD64Address) crb.asAddress(x), y, condition, trueDestination.label(), false, applyBeforeFusedPair);
                } else if (trueDestinationProbability < 0.5) {
                    masm.testAndJcc(size, (AMD64Address) crb.asAddress(x), y, condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.testAndJcc(size, (AMD64Address) crb.asAddress(x), y, condition, trueDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(falseDestination.label());
                }
            } else {
                AMD64AddressValue xAddress = (AMD64AddressValue) x;
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.testAndJcc(size, xAddress.toAddress(), y, condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.testAndJcc(size, xAddress.toAddress(), y, condition, trueDestination.label(), false, applyBeforeFusedPair);
                } else if (trueDestinationProbability < 0.5) {
                    masm.testAndJcc(size, xAddress.toAddress(), y, condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.testAndJcc(size, xAddress.toAddress(), y, condition, trueDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(falseDestination.label());
                }
            }
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && x instanceof AMD64AddressValue && ((AMD64AddressValue) x).isValidImplicitNullCheckFor(value, implicitNullCheckLimit)) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    public static class CmpBranchOp extends BranchOp implements StandardOp.ImplicitNullCheck {

        public static final LIRInstructionClass<CmpBranchOp> TYPE = LIRInstructionClass.create(CmpBranchOp.class);

        private final OperandSize size;

        @LIRInstruction.Use({LIRInstruction.OperandFlag.REG}) protected AllocatableValue x;
        @LIRInstruction.Use({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.STACK, LIRInstruction.OperandFlag.COMPOSITE}) protected Value y;

        @LIRInstruction.State protected LIRFrameState state;

        public CmpBranchOp(OperandSize size, AllocatableValue x, Value y, LIRFrameState state, Condition cond, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(TYPE, intCond(cond), trueDestination, falseDestination, trueDestinationProbability);
            this.size = size;

            this.x = x;
            this.y = y;

            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(y)) {
                assert state == null;
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.cmpAndJcc(size, asRegister(x), asRegister(y), condition.negate(), falseDestination.label(), false);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.cmpAndJcc(size, asRegister(x), asRegister(y), condition, trueDestination.label(), false);
                } else if (trueDestinationProbability < 0.5) {
                    masm.cmpAndJcc(size, asRegister(x), asRegister(y), condition.negate(), falseDestination.label(), false);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.cmpAndJcc(size, asRegister(x), asRegister(y), condition, trueDestination.label(), false);
                    masm.jmp(falseDestination.label());
                }
                return;
            }
            IntConsumer applyBeforeFusedPair = state == null ? null : pos -> crb.recordImplicitException(pos, state);
            if (isStackSlot(y)) {
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.cmpAndJcc(size, asRegister(x), (AMD64Address) crb.asAddress(y), condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.cmpAndJcc(size, asRegister(x), (AMD64Address) crb.asAddress(y), condition, trueDestination.label(), false, applyBeforeFusedPair);
                } else if (trueDestinationProbability < 0.5) {
                    masm.cmpAndJcc(size, asRegister(x), (AMD64Address) crb.asAddress(y), condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.cmpAndJcc(size, asRegister(x), (AMD64Address) crb.asAddress(y), condition, trueDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(falseDestination.label());
                }
            } else {
                AMD64AddressValue yAddress = (AMD64AddressValue) y;
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.cmpAndJcc(size, asRegister(x), yAddress.toAddress(), condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.cmpAndJcc(size, asRegister(x), yAddress.toAddress(), condition, trueDestination.label(), false, applyBeforeFusedPair);
                } else if (trueDestinationProbability < 0.5) {
                    masm.cmpAndJcc(size, asRegister(x), yAddress.toAddress(), condition.negate(), falseDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.cmpAndJcc(size, asRegister(x), yAddress.toAddress(), condition, trueDestination.label(), false, applyBeforeFusedPair);
                    masm.jmp(falseDestination.label());
                }
            }
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && y instanceof AMD64AddressValue && ((AMD64AddressValue) y).isValidImplicitNullCheckFor(value, implicitNullCheckLimit)) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    public static class CmpConstBranchOp extends BranchOp {

        public static final LIRInstructionClass<CmpConstBranchOp> TYPE = LIRInstructionClass.create(CmpConstBranchOp.class);

        private final OperandSize size;

        @LIRInstruction.Use({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.STACK, LIRInstruction.OperandFlag.COMPOSITE}) protected Value x;
        private final int y;
        private final VMConstant inlinedY;

        @LIRInstruction.State protected LIRFrameState state;

        public CmpConstBranchOp(OperandSize size, Value x, int y, LIRFrameState state, Condition cond, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(TYPE, intCond(cond), trueDestination, falseDestination, trueDestinationProbability);
            this.size = size;

            this.x = x;
            this.y = y;
            this.inlinedY = null;

            this.state = state;
        }

        public CmpConstBranchOp(OperandSize size, Value x, VMConstant y, LIRFrameState state, Condition cond, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(TYPE, intCond(cond), trueDestination, falseDestination, trueDestinationProbability);
            this.size = size;

            this.x = x;
            this.y = 0xDEADDEAD;
            this.inlinedY = y;

            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            final boolean inlineDataInCode = inlinedY != null;
            IntConsumer applyBeforeFusedPair = null;
            if (inlineDataInCode) {
                applyBeforeFusedPair = pos -> crb.recordInlineDataInCode(inlinedY);
            }
            if (isRegister(x)) {
                assert state == null;
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.cmpAndJcc(size, asRegister(x), y, condition.negate(), falseDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.cmpAndJcc(size, asRegister(x), y, condition, trueDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                } else if (trueDestinationProbability < 0.5) {
                    masm.cmpAndJcc(size, asRegister(x), y, condition.negate(), falseDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.cmpAndJcc(size, asRegister(x), y, condition, trueDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                    masm.jmp(falseDestination.label());
                }
                return;
            }
            if (state != null) {
                IntConsumer implicitException = pos -> crb.recordImplicitException(pos, state);
                applyBeforeFusedPair = applyBeforeFusedPair == null ? implicitException : applyBeforeFusedPair.andThen(implicitException);
            }
            if (isStackSlot(x)) {
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.cmpAndJcc(size, (AMD64Address) crb.asAddress(x), y, condition.negate(), falseDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.cmpAndJcc(size, (AMD64Address) crb.asAddress(x), y, condition, trueDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                } else if (trueDestinationProbability < 0.5) {
                    masm.cmpAndJcc(size, (AMD64Address) crb.asAddress(x), y, condition.negate(), falseDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.cmpAndJcc(size, (AMD64Address) crb.asAddress(x), y, condition, trueDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                    masm.jmp(falseDestination.label());
                }
            } else {
                AMD64AddressValue xAddress = (AMD64AddressValue) x;
                if (crb.isSuccessorEdge(trueDestination)) {
                    masm.cmpAndJcc(size, xAddress.toAddress(), y, condition.negate(), falseDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                } else if (crb.isSuccessorEdge(falseDestination)) {
                    masm.cmpAndJcc(size, xAddress.toAddress(), y, condition, trueDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                } else if (trueDestinationProbability < 0.5) {
                    masm.cmpAndJcc(size, xAddress.toAddress(), y, condition.negate(), falseDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                    masm.jmp(trueDestination.label());
                } else {
                    masm.cmpAndJcc(size, xAddress.toAddress(), y, condition, trueDestination.label(), false, inlineDataInCode, applyBeforeFusedPair);
                    masm.jmp(falseDestination.label());
                }
            }
        }
    }

    public static class CmpDataBranchOp extends BranchOp {

        public static final LIRInstructionClass<CmpDataBranchOp> TYPE = LIRInstructionClass.create(CmpDataBranchOp.class);

        private final OperandSize size;

        @LIRInstruction.Use({LIRInstruction.OperandFlag.REG}) protected AllocatableValue x;
        private final Constant y;

        public CmpDataBranchOp(OperandSize size, AllocatableValue x, Constant y, Condition cond, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(TYPE, intCond(cond), trueDestination, falseDestination, trueDestinationProbability);
            this.size = size;

            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            Supplier<AMD64Address> applyBeforeConsumer = () -> (AMD64Address) crb.recordDataReferenceInCode(y, size.getBytes());
            if (crb.isSuccessorEdge(trueDestination)) {
                masm.cmpAndJcc(size, asRegister(x), applyBeforeConsumer, condition.negate(), falseDestination.label());
            } else if (crb.isSuccessorEdge(falseDestination)) {
                masm.cmpAndJcc(size, asRegister(x), applyBeforeConsumer, condition, trueDestination.label());
            } else if (trueDestinationProbability < 0.5) {
                masm.cmpAndJcc(size, asRegister(x), applyBeforeConsumer, condition.negate(), falseDestination.label());
                masm.jmp(trueDestination.label());
            } else {
                masm.cmpAndJcc(size, asRegister(x), applyBeforeConsumer, condition, trueDestination.label());
                masm.jmp(falseDestination.label());
            }
        }
    }

    public static final class FloatBranchOp extends BranchOp {
        public static final LIRInstructionClass<FloatBranchOp> TYPE = LIRInstructionClass.create(FloatBranchOp.class);
        protected boolean unorderedIsTrue;
        protected boolean isSelfEqualsCheck;

        public FloatBranchOp(Condition condition, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            this(condition, unorderedIsTrue, trueDestination, falseDestination, trueDestinationProbability, false);
        }

        public FloatBranchOp(Condition condition, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability, boolean isSelfEqualsCheck) {
            super(TYPE, floatCond(condition), trueDestination, falseDestination, trueDestinationProbability);
            this.unorderedIsTrue = unorderedIsTrue;
            this.isSelfEqualsCheck = isSelfEqualsCheck;
        }

        @Override
        protected void jcc(AMD64MacroAssembler masm, boolean negate, LabelRef target) {
            Label label = target.label();
            Label endLabel = new Label();
            if (isSelfEqualsCheck) {
                // The condition is x == x, i.e., !isNaN(x).
                assert !unorderedIsTrue;
                ConditionFlag notNaN = negate ? ConditionFlag.Parity : ConditionFlag.NoParity;
                masm.jcc(notNaN, label);
                masm.bind(endLabel);
            } else {
                ConditionFlag condition1 = negate ? condition.negate() : condition;
                boolean unorderedIsTrue1 = negate ? !unorderedIsTrue : unorderedIsTrue;
                if (unorderedIsTrue1 && !trueOnUnordered(condition1)) {
                    masm.jcc(ConditionFlag.Parity, label);
                } else if (!unorderedIsTrue1 && trueOnUnordered(condition1)) {
                    masm.jccb(ConditionFlag.Parity, endLabel);
                }
                masm.jcc(condition1, label);
                masm.bind(endLabel);
            }
        }
    }

    public static class StrategySwitchOp extends AMD64BlockEndOp {
        public static final LIRInstructionClass<StrategySwitchOp> TYPE = LIRInstructionClass.create(StrategySwitchOp.class);
        protected final Constant[] keyConstants;
        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) protected AllocatableValue key;
        @LIRInstruction.Temp({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.ILLEGAL}) protected AllocatableValue scratch;
        protected final SwitchStrategy strategy;

        public StrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, AllocatableValue key, AllocatableValue scratch) {
            this(TYPE, strategy, keyTargets, defaultTarget, key, scratch);
        }

        protected StrategySwitchOp(LIRInstructionClass<? extends StrategySwitchOp> c, SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, AllocatableValue key,
                        AllocatableValue scratch) {
            super(c);
            this.strategy = strategy;
            this.keyConstants = strategy.getKeyConstants();
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
            this.scratch = scratch;
            assert keyConstants.length == keyTargets.length : Assertions.errorMessage(keyConstants, keyTargets);
            assert keyConstants.length == strategy.keyProbabilities.length : Assertions.errorMessage(keyConstants, strategy);
        }

        @Override
        public void emitCode(final CompilationResultBuilder crb, final AMD64MacroAssembler masm) {
            strategy.run(new SwitchClosure(asRegister(key), crb, masm));
        }

        public class SwitchClosure extends SwitchStrategy.BaseSwitchClosure {

            protected final Register keyRegister;
            protected final CompilationResultBuilder crb;
            protected final AMD64MacroAssembler masm;

            protected SwitchClosure(Register keyRegister, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
                super(crb, masm, keyTargets, defaultTarget);
                this.keyRegister = keyRegister;
                this.crb = crb;
                this.masm = masm;
            }

            protected void emitComparison(Constant c) {
                JavaConstant jc = (JavaConstant) c;
                switch (jc.getJavaKind()) {
                    case Int:
                        long lc = jc.asLong();
                        assert NumUtil.isInt(lc);
                        masm.cmpl(keyRegister, (int) lc);
                        break;
                    case Long:
                        masm.cmpq(keyRegister, (AMD64Address) crb.asLongConstRef(jc));
                        break;
                    case Object:
                        AMD64Move.const2reg(crb, masm, asRegister(scratch), jc, AMD64Kind.QWORD);
                        masm.cmpptr(keyRegister, asRegister(scratch));
                        break;
                    default:
                        throw new GraalError("switch only supported for int, long and object");
                }
            }

            @Override
            protected void conditionalJump(int index, Condition condition, Label target) {
                emitComparison(keyConstants[index]);
                masm.jcc(intCond(condition), target);
            }
        }
    }

    public static final class RangeTableSwitchOp extends AMD64BlockEndOp {
        public static final LIRInstructionClass<RangeTableSwitchOp> TYPE = LIRInstructionClass.create(RangeTableSwitchOp.class);
        private final int lowKey;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;
        @LIRInstruction.Use protected Value index;
        @LIRInstruction.Temp({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.HINT}) protected Value idxScratch;
        @LIRInstruction.Temp protected Value scratch;

        public RangeTableSwitchOp(final int lowKey, final LabelRef defaultTarget, final LabelRef[] targets, Value index, Variable scratch, Variable idxScratch) {
            super(TYPE);
            this.lowKey = lowKey;
            assert defaultTarget != null;
            this.defaultTarget = defaultTarget;
            this.targets = targets;
            this.index = index;
            this.scratch = scratch;
            this.idxScratch = idxScratch;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            Register indexReg = asRegister(index, AMD64Kind.DWORD);
            Register idxScratchReg = asRegister(idxScratch, AMD64Kind.DWORD);
            Register scratchReg = asRegister(scratch, AMD64Kind.QWORD);

            if (!indexReg.equals(idxScratchReg)) {
                masm.movl(idxScratchReg, indexReg);
            }

            // Compare index against jump table bounds
            int highKey = lowKey + targets.length - 1;
            if (lowKey != 0) {
                // subtract the low value from the switch value
                masm.subl(idxScratchReg, lowKey);
                masm.cmpl(idxScratchReg, highKey - lowKey);
            } else {
                masm.cmpl(idxScratchReg, highKey);
            }

            // Jump to default target if index is not within the jump table
            masm.jcc(ConditionFlag.Above, defaultTarget.label());

            emitJumpTable(crb, masm, scratchReg, idxScratchReg, lowKey, highKey, Arrays.stream(targets).map(LabelRef::label));
        }

        public static void emitJumpTable(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register scratchReg, Register idxScratchReg, int lowKey, int highKey, Stream<Label> targets) {
            // Set scratch to address of jump table
            masm.leaq(scratchReg, new AMD64Address(AMD64.rip, 0));
            final int afterLea = masm.position();

            // Load jump table entry into scratch and jump to it
            masm.movslq(idxScratchReg, new AMD64Address(scratchReg, idxScratchReg, Stride.S4, 0));
            masm.addq(scratchReg, idxScratchReg);
            masm.jmp(scratchReg);

            // Inserting padding so that jump table address is 4-byte aligned
            masm.align(4);

            // Patch LEA instruction above now that we know the position of the jump table
            // this is ugly but there is no better way to do this given the assembler API
            final int jumpTablePos = masm.position();
            final int leaDisplacementPosition = afterLea - 4;
            masm.emitInt(jumpTablePos - afterLea, leaDisplacementPosition);

            // Emit jump table entries
            targets.forEach(label -> {
                int offsetToJumpTableBase = masm.position() - jumpTablePos;
                if (label.isBound()) {
                    int imm32 = label.position() - jumpTablePos;
                    masm.emitInt(imm32);
                } else {
                    label.addPatchAt(masm.position(), masm);

                    masm.emitByte(0); // pseudo-opcode for jump table entry
                    masm.emitShort(offsetToJumpTableBase);
                    masm.emitByte(0); // padding to make jump table entry 4 bytes wide
                }
            });

            JumpTable jt = new JumpTable(jumpTablePos, lowKey, highKey, EntryFormat.OFFSET_ONLY);
            crb.compilationResult.addAnnotation(jt);
        }
    }

    public static final class HashTableSwitchOp extends AMD64BlockEndOp {
        public static final LIRInstructionClass<HashTableSwitchOp> TYPE = LIRInstructionClass.create(HashTableSwitchOp.class);
        private final JavaConstant[] keys;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;
        @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) protected AllocatableValue value;
        @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) protected AllocatableValue hash;
        @LIRInstruction.Temp({LIRInstruction.OperandFlag.REG}) protected AllocatableValue entryScratch;
        @LIRInstruction.Temp({LIRInstruction.OperandFlag.REG}) protected AllocatableValue scratch;

        public HashTableSwitchOp(final JavaConstant[] keys, final LabelRef defaultTarget, LabelRef[] targets, AllocatableValue value, AllocatableValue hash, Variable scratch, Variable entryScratch) {
            super(TYPE);
            this.keys = keys;
            this.defaultTarget = defaultTarget;
            this.targets = targets;
            this.value = value;
            this.hash = hash;
            this.scratch = scratch;
            this.entryScratch = entryScratch;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            Register valueReg = asRegister(value, AMD64Kind.DWORD);
            Register indexReg = asRegister(hash, AMD64Kind.DWORD);
            Register scratchReg = asRegister(scratch, AMD64Kind.QWORD);
            Register entryScratchReg = asRegister(entryScratch, AMD64Kind.QWORD);

            // Set scratch to address of jump table
            masm.leaq(scratchReg, new AMD64Address(AMD64.rip, 0));
            final int afterLea = masm.position();

            // When the default target is set, the jump table contains entries with two DWORDS:
            // the original key before hashing and the label jump address
            if (defaultTarget != null) {

                // Move the table entry (two DWORDs) into a QWORD
                masm.movq(entryScratchReg, new AMD64Address(scratchReg, indexReg, Stride.S8, 0));

                // Jump to the default target if the first DWORD (original key) doesn't match the
                // current key. Accounts for hash collisions with unknown keys
                masm.cmplAndJcc(entryScratchReg, valueReg, ConditionFlag.NotEqual, defaultTarget.label(), false);

                // Shift to the second DWORD
                masm.sarq(entryScratchReg, 32);
            } else {

                // The jump table has a single DWORD with the label address if there's no
                // default target
                masm.movslq(entryScratchReg, new AMD64Address(scratchReg, indexReg, Stride.S4, 0));
            }
            masm.addq(scratchReg, entryScratchReg);
            masm.jmp(scratchReg);

            // Inserting padding so that jump the table address is aligned
            EntryFormat entryFormat = defaultTarget == null ? EntryFormat.OFFSET_ONLY : EntryFormat.VALUE_AND_OFFSET;
            masm.align(entryFormat.size);

            // Patch LEA instruction above now that we know the position of the jump table
            // this is ugly but there is no better way to do this given the assembler API
            final int jumpTablePos = masm.position();
            final int leaDisplacementPosition = afterLea - 4;
            masm.emitInt(jumpTablePos - afterLea, leaDisplacementPosition);

            // Emit jump table entries
            for (int i = 0; i < targets.length; i++) {

                Label label = targets[i].label();

                if (defaultTarget != null) {
                    masm.emitInt(keys[i].asInt());
                }
                if (label.isBound()) {
                    int imm32 = label.position() - jumpTablePos;
                    masm.emitInt(imm32);
                } else {
                    int offsetToJumpTableBase = masm.position() - jumpTablePos;
                    label.addPatchAt(masm.position(), masm);
                    masm.emitByte(0); // pseudo-opcode for jump table entry
                    masm.emitShort(offsetToJumpTableBase);
                    masm.emitByte(0); // padding to make jump table entry 4 bytes wide
                }
            }

            JumpTable jt = new JumpTable(jumpTablePos, 0, keys.length - 1, entryFormat);
            crb.compilationResult.addAnnotation(jt);
        }
    }

    @Opcode("SETcc")
    public static final class CondSetOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CondSetOp> TYPE = LIRInstructionClass.create(CondSetOp.class);
        @LIRInstruction.Def({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.HINT}) protected Value result;
        private final ConditionFlag condition;

        public CondSetOp(Variable result, Condition condition) {
            super(TYPE);
            this.result = result;
            this.condition = intCond(condition);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            setcc(masm, result, condition);
        }
    }

    @Opcode("SETcc")
    public static final class FloatCondSetOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<FloatCondSetOp> TYPE = LIRInstructionClass.create(FloatCondSetOp.class);
        @LIRInstruction.Def({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.HINT}) protected Value result;
        private final ConditionFlag condition;

        public FloatCondSetOp(Variable result, Condition condition) {
            super(TYPE);
            this.result = result;
            this.condition = floatCond(condition);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            setcc(masm, result, condition);
        }
    }

    @Opcode("CMOVE")
    public static final class CondMoveOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CondMoveOp> TYPE = LIRInstructionClass.create(CondMoveOp.class);
        @LIRInstruction.Def({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.HINT}) protected Value result;
        @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) protected Value trueValue;
        @LIRInstruction.Use({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.STACK, LIRInstruction.OperandFlag.CONST}) protected Value falseValue;
        private final ConditionFlag condition;

        public CondMoveOp(Variable result, Condition condition, AllocatableValue trueValue, Value falseValue) {
            super(TYPE);
            this.result = result;
            this.condition = intCond(condition);
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            cmove(crb, masm, result, false, condition, false, trueValue, falseValue, false);
        }
    }

    @Opcode("CMOVE")
    public static final class FloatCondMoveOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<FloatCondMoveOp> TYPE = LIRInstructionClass.create(FloatCondMoveOp.class);
        @LIRInstruction.Def({LIRInstruction.OperandFlag.REG}) protected Value result;
        @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) protected Value trueValue;
        @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) protected Value falseValue;
        private final ConditionFlag condition;
        private final boolean unorderedIsTrue;
        private final boolean isSelfEqualsCheck;

        public FloatCondMoveOp(Variable result, Condition condition, boolean unorderedIsTrue, AllocatableValue trueValue, AllocatableValue falseValue, boolean isSelfEqualsCheck) {
            super(TYPE);
            this.result = result;
            this.condition = floatCond(condition);
            this.unorderedIsTrue = unorderedIsTrue;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
            this.isSelfEqualsCheck = isSelfEqualsCheck;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            cmove(crb, masm, result, true, condition, unorderedIsTrue, trueValue, falseValue, isSelfEqualsCheck);
        }
    }

    private static void cmove(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, boolean isFloat, ConditionFlag condition, boolean unorderedIsTrue, Value trueValue,
                    Value falseValue, boolean isSelfEqualsCheck) {
        // check that we don't overwrite an input operand before it is used.
        assert !result.equals(trueValue);

        // The isSelfEqualsCheck condition is x == x, i.e., !isNaN(x).
        ConditionFlag moveCondition = (isSelfEqualsCheck ? ConditionFlag.NoParity : condition);
        AMD64Move.move(crb, masm, result, falseValue);
        cmove(crb, masm, result, moveCondition, trueValue);

        if (isFloat && !isSelfEqualsCheck) {
            if (unorderedIsTrue && !trueOnUnordered(condition)) {
                cmove(crb, masm, result, ConditionFlag.Parity, trueValue);
            } else if (!unorderedIsTrue && trueOnUnordered(condition)) {
                cmove(crb, masm, result, ConditionFlag.Parity, falseValue);
            }
        }
    }

    private static void cmove(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value result, ConditionFlag cond, Value other) {
        if (isRegister(other)) {
            assert !asRegister(other).equals(asRegister(result)) : "other already overwritten by previous move";
            switch ((AMD64Kind) other.getPlatformKind()) {
                case BYTE:
                case WORD:
                case DWORD:
                    masm.cmovl(cond, asRegister(result), asRegister(other));
                    break;
                case QWORD:
                    masm.cmovq(cond, asRegister(result), asRegister(other));
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(other.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
            }
        } else {
            AMD64Address addr = (AMD64Address) crb.asAddress(other);
            switch ((AMD64Kind) other.getPlatformKind()) {
                case BYTE:
                case WORD:
                case DWORD:
                    masm.cmovl(cond, asRegister(result), addr);
                    break;
                case QWORD:
                    masm.cmovq(cond, asRegister(result), addr);
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(other.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    private static void setcc(AMD64MacroAssembler masm, Value result, ConditionFlag cond) {
        switch ((AMD64Kind) result.getPlatformKind()) {
            case BYTE:
            case WORD:
            case DWORD:
                masm.setl(cond, asRegister(result));
                break;
            case QWORD:
                masm.setq(cond, asRegister(result));
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(result.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
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
                return ConditionFlag.BelowEqual;
            case AE:
                return ConditionFlag.AboveEqual;
            case AT:
                return ConditionFlag.Above;
            case BT:
                return ConditionFlag.Below;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(cond); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static ConditionFlag floatCond(Condition cond) {
        switch (cond) {
            case EQ:
                return ConditionFlag.Equal;
            case NE:
                return ConditionFlag.NotEqual;
            case LT:
                return ConditionFlag.Below;
            case LE:
                return ConditionFlag.BelowEqual;
            case GE:
                return ConditionFlag.AboveEqual;
            case GT:
                return ConditionFlag.Above;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(cond); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static boolean trueOnUnordered(Condition condition) {
        return trueOnUnordered(floatCond(condition));
    }

    private static boolean trueOnUnordered(ConditionFlag condition) {
        switch (condition) {
            case AboveEqual:
            case NotEqual:
            case Above:
            case Less:
            case Overflow:
                return false;
            case Equal:
            case BelowEqual:
            case Below:
            case GreaterEqual:
            case NoOverflow:
                return true;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(condition); // ExcludeFromJacocoGeneratedReport
        }
    }
}
