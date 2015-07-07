/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.gen;

import static com.oracle.graal.lir.LIRValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.StandardOp.StackMove;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;

/**
 * Base class for {@link SpillMoveFactory} that checks that the instructions created adhere to the
 * contract of {@link SpillMoveFactory}.
 */
public abstract class SpillMoveFactoryBase implements SpillMoveFactory {

    public final LIRInstruction createMove(AllocatableValue result, Value input) {
        LIRInstruction inst = createMoveIntern(result, input);
        assert checkResult(inst, result, input);
        return inst;
    }

    public final LIRInstruction createStackMove(AllocatableValue result, Value input) {
        LIRInstruction inst = createStackMoveIntern(result, input);
        assert checkResult(inst, result, input);
        return inst;
    }

    protected abstract LIRInstruction createMoveIntern(AllocatableValue result, Value input);

    protected LIRInstruction createStackMoveIntern(AllocatableValue result, Value input) {
        return new StackMove(result, input);
    }

    /** Closure for {@link SpillMoveFactoryBase#checkResult}. */
    @SuppressWarnings("unused")
    private static class CheckClosure {

        private final AllocatableValue result;
        private final Value input;

        private int tempCount = 0;
        private int aliveCount = 0;
        private int stateCount = 0;
        private int inputCount = 0;
        private int outputCount = 0;

        CheckClosure(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        void tempProc(LIRInstruction op, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            assert false : String.format("SpillMoveFactory: Instruction %s is not allowed to contain operand %s of mode %s", op, value, mode);
            tempCount++;
        }

        void stateProc(LIRInstruction op, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            assert false : String.format("SpillMoveFactory: Instruction %s is not allowed to contain operand %s of mode %s", op, value, mode);
            stateCount++;
        }

        void aliveProc(LIRInstruction op, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            assert !isVariable(value) && flags.contains(OperandFlag.UNINITIALIZED) : String.format("SpillMoveFactory: Instruction %s is not allowed to contain operand %s of mode %s", op, value, mode);
            aliveCount++;
        }

        void inputProc(LIRInstruction op, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            assert value.equals(input) : String.format("SpillMoveFactory: Instruction %s can only have %s as input, got %s", op, input, value);
            inputCount++;
        }

        void outputProc(LIRInstruction op, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            assert value.equals(result) : String.format("SpillMoveFactory: Instruction %s can only have %s as input, got %s", op, input, value);
            outputCount++;
        }
    }

    /** Checks that the instructions adheres to the contract of {@link SpillMoveFactory}. */
    private static boolean checkResult(LIRInstruction inst, AllocatableValue result, Value input) {

        SpillMoveFactoryBase.CheckClosure c = new CheckClosure(result, input);
        inst.visitEachInput(c::inputProc);
        inst.visitEachOutput(c::outputProc);
        inst.visitEachAlive(c::aliveProc);
        inst.visitEachTemp(c::tempProc);
        inst.visitEachState(c::stateProc);

        assert c.outputCount >= 1 : "no output produced" + inst;
        assert c.stateCount == 0 : "SpillMoveFactory: instruction must not have a state: " + inst;
        return true;
    }
}
