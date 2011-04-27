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

import com.sun.c1x.alloc.*;
import com.sun.c1x.asm.*;
import com.sun.cri.ci.*;

/**
 * The {@code LIRBlock} class definition.
 *
 * @author Ben L. Titzer
 */
public final class LIRBlock {

    public LIRBlock() {
    }

    public final Label label = new Label();
    private LIRList lir;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are live upon entry to this block.
     * These are values used in this block or any of its successors where such value are not defined
     * in this block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public CiBitMap liveIn;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are live upon exit from this block.
     * These are values used in a successor block that are either defined in this block or were live
     * upon entry to this block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public CiBitMap liveOut;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are used (before being defined) in this block.
     * That is, these are the values that are live upon entry to the block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public CiBitMap liveGen;

    /**
     * Bit map specifying which {@linkplain OperandPool operands} are defined/overwritten in this block.
     * The bit index of an operand is its {@linkplain OperandPool#operandNumber(com.sun.cri.ci.CiValue) operand number}.
     */
    public CiBitMap liveKill;

    public int firstLirInstructionID;
    public int lastLirInstructionID;
    public int exceptionHandlerPCO;

    public LIRList lir() {
        return lir;
    }

    public void setLir(LIRList lir) {
        this.lir = lir;
    }
}
