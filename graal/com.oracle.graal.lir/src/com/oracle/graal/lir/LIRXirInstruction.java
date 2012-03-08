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

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.xir.*;
import com.oracle.graal.graph.*;

public abstract class LIRXirInstruction extends LIRInstruction {

    public final CiValue[] originalOperands;
    public final int outputOperandIndex;
    public final int[] inputOperandIndices;
    public final int[] tempOperandIndices;
    public final XirSnippet snippet;
    public final LIRDebugInfo infoAfter;
    public final LabelRef trueSuccessor;
    public final LabelRef falseSuccessor;

    public LIRXirInstruction(Object opcode,
                             XirSnippet snippet,
                             CiValue[] originalOperands,
                             CiValue outputOperand,
                             CiValue[] inputs, CiValue[] temps,
                             int[] inputOperandIndices, int[] tempOperandIndices,
                             int outputOperandIndex,
                             LIRDebugInfo info,
                             LIRDebugInfo infoAfter,
                             LabelRef trueSuccessor,
                             LabelRef falseSuccessor) {
        // Note that we register the XIR input operands as Alive, because the XIR specification allows that input operands
        // are used at any time, even when the temp operands and the actual output operands have already be assigned.
        super(opcode, isLegal(outputOperand) ? new CiValue[] {outputOperand} : LIRInstruction.NO_OPERANDS, info, LIRInstruction.NO_OPERANDS, inputs, temps);
        this.infoAfter = infoAfter;
        this.snippet = snippet;
        this.inputOperandIndices = inputOperandIndices;
        this.tempOperandIndices = tempOperandIndices;
        this.outputOperandIndex = outputOperandIndex;
        this.originalOperands = originalOperands;
        this.falseSuccessor = falseSuccessor;
        this.trueSuccessor = trueSuccessor;
        assert isLegal(outputOperand) || outputOperandIndex == -1;
    }

    @Override
    protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
        if (mode == OperandMode.Alive || mode == OperandMode.Temp) {
            return EnumSet.of(OperandFlag.Register, OperandFlag.Constant, OperandFlag.Illegal);
        } else if (mode == OperandMode.Output && index == 0) {
            return EnumSet.of(OperandFlag.Register);
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    public CiValue[] getOperands() {
        for (int i = 0; i < inputOperandIndices.length; i++) {
            originalOperands[inputOperandIndices[i]] = alive(i);
        }
        for (int i = 0; i < tempOperandIndices.length; i++) {
            originalOperands[tempOperandIndices[i]] = temp(i);
        }
        if (outputOperandIndex != -1) {
            originalOperands[outputOperandIndex] = output(0);
        }
        return originalOperands;
    }

    @Override
    public String name() {
        return "XIR: " + snippet.template;
    }
}
