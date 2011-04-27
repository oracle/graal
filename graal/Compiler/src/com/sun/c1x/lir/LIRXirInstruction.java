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
package com.sun.c1x.lir;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.gen.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;

public class LIRXirInstruction extends LIRInstruction {

    public final CiValue[] originalOperands;
    public final int outputOperandIndex;
    public final int[] operandIndices;
    public final XirSnippet snippet;
    public final RiMethod method;
    public final int inputTempCount;
    public final int tempCount;
    public final int inputCount;
    public final List<CiValue> pointerSlots;
    public final LIRDebugInfo infoAfter;

    public LIRXirInstruction(XirSnippet snippet,
                             CiValue[] originalOperands,
                             CiValue outputOperand,
                             int inputTempCount,
                             int tempCount,
                             CiValue[] operands,
                             int[] operandIndices,
                             int outputOperandIndex,
                             LIRDebugInfo info,
                             LIRDebugInfo infoAfter,
                             RiMethod method,
                             List<CiValue> pointerSlots) {
        super(LIROpcode.Xir, outputOperand, info, false, inputTempCount, tempCount, operands);
        this.infoAfter = infoAfter;
        this.pointerSlots = pointerSlots;
        assert this.pointerSlots == null || this.pointerSlots.size() >= 0;
        this.method = method;
        this.snippet = snippet;
        this.operandIndices = operandIndices;
        this.outputOperandIndex = outputOperandIndex;
        this.originalOperands = originalOperands;
        this.inputTempCount = inputTempCount;
        this.tempCount = tempCount;
        this.inputCount = operands.length - inputTempCount - tempCount;

        C1XMetrics.LIRXIRInstructions++;
    }

    public CiValue[] getOperands() {
        for (int i = 0; i < operandIndices.length; i++) {
            originalOperands[operandIndices[i]] = operand(i);
        }
        if (outputOperandIndex != -1) {
            originalOperands[outputOperandIndex] = result();
        }
        return originalOperands;
    }

    /**
     * Emits target assembly code for this instruction.
     *
     * @param masm the target assembler
     */
    @Override
    public void emitCode(LIRAssembler masm) {
        masm.emitXir(this);
    }

     /**
     * Prints this instruction.
     */
    @Override
    public String operationString(OperandFormatter operandFmt) {
        return toString(operandFmt);
    }

    @Override
    public String toString(OperandFormatter operandFmt) {
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
                if (o instanceof LIRItem) {
                    sb.append(operandFmt.format(((LIRItem) o).result()));
                } else if (o instanceof CiValue) {
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
