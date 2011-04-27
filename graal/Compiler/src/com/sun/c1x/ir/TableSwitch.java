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
package com.sun.c1x.ir;

import static com.sun.c1x.debug.InstructionPrinter.InstructionLineColumn.*;

import java.util.*;

import com.sun.c1x.debug.*;
import com.sun.c1x.value.*;

/**
 * The {@code TableSwitch} instruction represents a table switch.
 *
 * @author Ben L. Titzer
 */
public final class TableSwitch extends Switch {

    final int lowKey;

    /**
     * Constructs a new TableSwitch instruction.
     * @param value the instruction producing the value being switched on
     * @param successors the list of successors
     * @param lowKey the lowest integer key in the table
     * @param stateBefore the state before the switch
     * @param isSafepoint {@code true} if this instruction is a safepoint
     */
    public TableSwitch(Value value, List<BlockBegin> successors, int lowKey, FrameState stateBefore, boolean isSafepoint) {
        super(value, successors, stateBefore, isSafepoint);
        this.lowKey = lowKey;
    }

    /**
     * Gets the lowest key in the table switch (inclusive).
     * @return the low key
     */
    public int lowKey() {
        return lowKey;
    }

    /**
     * Gets the highest key in the table switch (exclusive).
     * @return the high key
     */
    public int highKey() {
        return lowKey + numberOfCases();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitTableSwitch(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("tableswitch ");
        if (isSafepoint()) {
            out.print("(safepoint) ");
        }
        out.println(value());
        int l = numberOfCases();
        for (int i = 0; i < l; i++) {
            INSTRUCTION.advance(out);
            out.printf("case %5d: B%d%n", lowKey() + i, successors().get(i).blockID);
        }
        INSTRUCTION.advance(out);
        out.print("default   : B").print(defaultSuccessor().blockID);
    }
}
