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
package com.oracle.max.graal.nodes.extended;

import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;

/**
 * The {@code TableSwitchNode} represents a table switch.
 */
public final class TableSwitchNode extends SwitchNode implements LIRLowerable {

    @Data private final int lowKey;

    /**
     * Constructs a new TableSwitch instruction.
     * @param value the instruction producing the value being switched on
     * @param successors the list of successors
     * @param lowKey the lowest integer key in the table
     */
    public TableSwitchNode(ValueNode value, BeginNode[] successors, int lowKey, double[] probability) {
        super(value, successors, probability);
        this.lowKey = lowKey;
    }

    public TableSwitchNode(ValueNode value, int lowKey, double[] switchProbability) {
        this(value, new BeginNode[switchProbability.length], lowKey, switchProbability);
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
    public void generate(LIRGeneratorTool gen) {
        gen.emitTableSwitch(this);
    }
}
