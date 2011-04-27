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

import com.sun.c1x.ir.*;
import com.sun.cri.ci.*;

/**
 * @author Doug Simon
 */
public class LIRTableSwitch extends LIRInstruction {

    public BlockBegin defaultTarget;

    public final BlockBegin[] targets;

    public final int lowKey;

    public LIRTableSwitch(CiValue value, int lowKey, BlockBegin defaultTarget, BlockBegin[] targets) {
        super(LIROpcode.TableSwitch, CiValue.IllegalValue, null, false, 1, 0, value);
        this.lowKey = lowKey;
        this.targets = targets;
        this.defaultTarget = defaultTarget;
    }

    @Override
    public void emitCode(LIRAssembler masm) {
        masm.emitTableSwitch(this);
    }

    /**
     * @return the input value to this switch
     */
    public CiValue value() {
        return operand(0);
    }

    @Override
    public String operationString(OperandFormatter operandFmt) {
        StringBuilder buf = new StringBuilder(super.operationString(operandFmt));
        buf.append("\ndefault: [B").append(defaultTarget.blockID).append(']');
        int key = lowKey;
        for (BlockBegin b : targets) {
            buf.append("\ncase ").append(key).append(": [B").append(b.blockID).append(']');
            key++;
        }
        return buf.toString();
    }

    private BlockBegin substitute(BlockBegin block, BlockBegin oldBlock, BlockBegin newBlock) {
        if (block == oldBlock) {
            LIRInstruction instr = newBlock.lir().instructionsList().get(0);
            assert instr instanceof LIRLabel : "first instruction of block must be label";
            return newBlock;
        }
        return oldBlock;
    }

    public void substitute(BlockBegin oldBlock, BlockBegin newBlock) {
        if (substitute(defaultTarget, oldBlock, newBlock) == newBlock) {
            defaultTarget = newBlock;
        }
        for (int i = 0; i < targets.length; i++) {
            if (substitute(targets[i], oldBlock, newBlock) == newBlock) {
                targets[i] = newBlock;
            }
        }
    }
}
