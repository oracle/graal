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

import com.oracle.max.asm.*;
import com.sun.c1x.ir.*;
import com.sun.cri.ci.*;

/**
 * @author Marcelo Cintra
 * @author Thomas Wuerthinger
 *
 */
public class LIRBranch extends LIRInstruction {

    private Condition cond;
    private CiKind kind;
    private Label label;

    /**
     * The target block of this branch.
     */
    private BlockBegin block;

    /**
     * This is the unordered block for a float branch.
     */
    private BlockBegin unorderedBlock;


    public LIRBranch(Condition cond, Label label) {
        this(cond, label, null);
    }

    /**
     * Creates a new LIRBranch instruction.
     *
     * @param cond the branch condition
     * @param label target label
     *
     */
    public LIRBranch(Condition cond, Label label, LIRDebugInfo info) {
        super(LIROpcode.Branch, CiValue.IllegalValue, info, false);
        this.cond = cond;
        this.label = label;
    }

    /**
     * Creates a new LIRBranch instruction.
     *
     * @param cond
     * @param kind
     * @param block
     *
     */
    public LIRBranch(Condition cond, CiKind kind, BlockBegin block) {
        super(LIROpcode.Branch, CiValue.IllegalValue, null, false);
        this.cond = cond;
        this.kind = kind;
        this.label = block.label();
        this.block = block;
        this.unorderedBlock = null;
    }

    public LIRBranch(Condition cond, CiKind kind, BlockBegin block, BlockBegin ublock) {
        super(LIROpcode.CondFloatBranch, CiValue.IllegalValue, null, false);
        this.cond = cond;
        this.kind = kind;
        this.label = block.label();
        this.block = block;
        this.unorderedBlock = ublock;
    }

    /**
     * @return the condition
     */
    public Condition cond() {
        return cond;
    }

    public Label label() {
        return label;
    }

    public BlockBegin block() {
        return block;
    }

    public BlockBegin unorderedBlock() {
        return unorderedBlock;
    }

    public void changeBlock(BlockBegin b) {
        assert block != null : "must have old block";
        assert block.label() == label() : "must be equal";

        this.block = b;
        this.label = b.label();
    }

    public void changeUblock(BlockBegin b) {
        assert unorderedBlock != null : "must have old block";
        this.unorderedBlock = b;
    }

    public void negateCondition() {
        cond = cond.negate();
    }

    @Override
    public void emitCode(LIRAssembler masm) {
        masm.emitBranch(this);
    }

    @Override
    public String operationString(OperandFormatter operandFmt) {
        StringBuilder buf = new StringBuilder(cond().operator).append(' ');
        if (block() != null) {
            buf.append("[B").append(block.blockID).append(']');
        } else if (label().isBound()) {
            buf.append("[label:0x").append(Integer.toHexString(label().position())).append(']');
        } else {
            buf.append("[label:??]");
        }
        if (unorderedBlock() != null) {
            buf.append("unordered: [B").append(unorderedBlock().blockID).append(']');
        }
        return buf.toString();
    }

    public void substitute(BlockBegin oldBlock, BlockBegin newBlock) {
        if (block == oldBlock) {
            block = newBlock;
            LIRInstruction instr = newBlock.lir().instructionsList().get(0);
            assert instr instanceof LIRLabel : "first instruction of block must be label";
            label = ((LIRLabel) instr).label();
        }
        if (unorderedBlock == oldBlock) {
            unorderedBlock = newBlock;
        }
    }
}
