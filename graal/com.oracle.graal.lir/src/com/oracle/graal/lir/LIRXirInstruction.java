/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.max.cri.xir.*;

public abstract class LIRXirInstruction extends LIRInstruction {
    @Opcode protected final String opcode;
    @Def({REG, ILLEGAL}) protected Value outputOperand;
    @Alive({REG, CONST, ILLEGAL}) protected Value[] inputs;
    @Temp({REG, CONST, ILLEGAL}) protected Value[] temps;
    @State protected LIRFrameState state;
    @State protected LIRFrameState stateAfter;

    // Defined as Object[] so that the magic processing of Value[] does not complain this field is not annotated.
    public final Object[] originalOperands;

    public final int outputOperandIndex;
    public final int[] inputOperandIndices;
    public final int[] tempOperandIndices;
    public final XirSnippet snippet;
    public final LabelRef trueSuccessor;
    public final LabelRef falseSuccessor;

    public LIRXirInstruction(XirSnippet snippet,
                             Value[] originalOperands,
                             Value outputOperand,
                             Value[] inputs, Value[] temps,
                             int[] inputOperandIndices, int[] tempOperandIndices,
                             int outputOperandIndex,
                             LIRFrameState state,
                             LIRFrameState stateAfter,
                             LabelRef trueSuccessor,
                             LabelRef falseSuccessor) {
        // Note that we register the XIR input operands as Alive, because the XIR specification allows that input operands
        // are used at any time, even when the temp operands and the actual output operands have already be assigned.
        this.opcode = "XIR: " + snippet.template;
        this.outputOperand = outputOperand;
        this.inputs = inputs;
        this.temps = temps;
        this.state = state;
        this.stateAfter = stateAfter;
        this.snippet = snippet;
        this.inputOperandIndices = inputOperandIndices;
        this.tempOperandIndices = tempOperandIndices;
        this.outputOperandIndex = outputOperandIndex;
        this.originalOperands = originalOperands;
        this.falseSuccessor = falseSuccessor;
        this.trueSuccessor = trueSuccessor;
        assert isLegal(outputOperand) || outputOperandIndex == -1;
    }

    public Value[] getOperands() {
        for (int i = 0; i < inputOperandIndices.length; i++) {
            originalOperands[inputOperandIndices[i]] = inputs[i];
        }
        for (int i = 0; i < tempOperandIndices.length; i++) {
            originalOperands[tempOperandIndices[i]] = temps[i];
        }
        if (outputOperandIndex != -1) {
            originalOperands[outputOperandIndex] = outputOperand;
        }
        return (Value[]) originalOperands;
    }
}
