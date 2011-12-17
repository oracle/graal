/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.lir;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ci.CiValue.Formatter;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;

public abstract class LIRXirInstruction extends LIRInstruction {

    public final CiValue[] originalOperands;
    public final int outputOperandIndex;
    public final int[] inputOperandIndices;
    public final int[] tempOperandIndices;
    public final XirSnippet snippet;
    public final RiMethod method;
    public final LIRDebugInfo infoAfter;
    private LabelRef trueSuccessor;
    private LabelRef falseSuccessor;

    public LIRXirInstruction(LIROpcode opcode,
                             XirSnippet snippet,
                             CiValue[] originalOperands,
                             CiValue outputOperand,
                             CiValue[] inputs, CiValue[] temps,
                             int[] inputOperandIndices, int[] tempOperandIndices,
                             int outputOperandIndex,
                             LIRDebugInfo info,
                             LIRDebugInfo infoAfter,
                             RiMethod method) {
        // Note that we register the XIR input operands as Alive, because the XIR specification allows that input operands
        // are used at any time, even when the temp operands and the actual output operands have already be assigned.
        super(opcode, outputOperand, info, LIRInstruction.NO_OPERANDS, inputs, temps);
        this.infoAfter = infoAfter;
        this.method = method;
        this.snippet = snippet;
        this.inputOperandIndices = inputOperandIndices;
        this.tempOperandIndices = tempOperandIndices;
        this.outputOperandIndex = outputOperandIndex;
        this.originalOperands = originalOperands;
    }


    public void setFalseSuccessor(LabelRef falseSuccessor) {
        this.falseSuccessor = falseSuccessor;
    }


    public void setTrueSuccessor(LabelRef trueSuccessor) {
        this.trueSuccessor = trueSuccessor;
    }

    public LabelRef falseSuccessor() {
        return falseSuccessor;
    }

    public LabelRef trueSuccessor() {
        return trueSuccessor;
    }

    public CiValue[] getOperands() {
        for (int i = 0; i < inputOperandIndices.length; i++) {
            originalOperands[inputOperandIndices[i]] = alive(i);
        }
        for (int i = 0; i < tempOperandIndices.length; i++) {
            originalOperands[tempOperandIndices[i]] = temp(i);
        }
        if (outputOperandIndex != -1) {
            originalOperands[outputOperandIndex] = result();
        }
        return originalOperands;
    }

     /**
     * Prints this instruction.
     */
    @Override
    public String operationString(Formatter operandFmt) {
        return toString(operandFmt);
    }

    @Override
    public String toString(Formatter operandFmt) {
        StringBuilder sb = new StringBuilder();
        sb.append("XIR: ");

        if (result().isLegal()) {
            sb.append(operandFmt.format(result()) + " = ");
        }

        sb.append(snippet.template);
        sb.append("(");
        for (int i = 0; i < snippet.arguments.length; i++) {
            XirArgument a = snippet.arguments[i];
            if (i > 0) {
                sb.append(", ");
            }
            if (a.constant != null) {
                sb.append(operandFmt.format(a.constant));
            } else {
                Object o = a.object;
                if (o instanceof CiValue) {
                    sb.append(operandFmt.format((CiValue) o));
                } else {
                    sb.append(o);
                }
            }
        }
        sb.append(')');

        if (method != null) {
            sb.append(" method=");
            sb.append(method.toString());
        }


        for (LIRInstruction.OperandMode mode : LIRInstruction.OPERAND_MODES) {
            int n = operandCount(mode);
            if (mode == OperandMode.Output && n <= 1) {
                // Already printed single output (i.e. result())
                continue;
            }
            if (n != 0) {
                sb.append(' ').append(mode.name().toLowerCase()).append("=(");
                HashSet<String> operands = new HashSet<String>();
                for (int i = 0; i < n; i++) {
                    String operand = operandFmt.format(operandAt(mode, i));
                    if (!operands.contains(operand)) {
                        if (!operands.isEmpty()) {
                            sb.append(", ");
                        }
                        operands.add(operand);
                        sb.append(operand);
                    }
                }
                sb.append(')');
            }
        }

        appendDebugInfo(sb, operandFmt, info);

        return sb.toString();
    }
}
