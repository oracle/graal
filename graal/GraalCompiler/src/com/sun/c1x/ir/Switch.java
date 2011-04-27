/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code Switch} class is the base of both lookup and table switches.
 *
 * @author Ben L. Titzer
 */
public abstract class Switch extends BlockEnd {

    Value value;

    /**
     * Constructs a new Switch.
     * @param value the instruction that provides the value to be switched over
     * @param successors the list of successors of this switch
     * @param stateBefore the state before the switch
     * @param isSafepoint {@code true} if this switch is a safepoint
     */
    public Switch(Value value, List<BlockBegin> successors, FrameState stateBefore, boolean isSafepoint) {
        super(CiKind.Illegal, stateBefore, isSafepoint, successors);
        this.value = value;
    }

    /**
     * Gets the instruction that provides the input value to this switch.
     * @return the instruction producing the input value
     */
    public Value value() {
        return value;
    }

    /**
     * Gets the number of cases that this switch covers (excluding the default case).
     * @return the number of cases
     */
    public int numberOfCases() {
        return successors.size() - 1;
    }

    /**
     * Iterates over the inputs to this instruction.
     * @param closure the closure to apply
     */
    @Override
    public void inputValuesDo(ValueClosure closure) {
        value = closure.apply(value);
    }
}
