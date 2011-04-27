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

import com.sun.c1x.debug.*;
import com.sun.c1x.value.*;

/**
 * The {@code MonitorEnter} instruction represents the acquisition of a monitor.
 *
 * @author Ben L. Titzer
 */
public final class MonitorEnter extends AccessMonitor {

    private FrameState stateAfter;

    /**
     * Creates a new MonitorEnter instruction.
     *
     * @param object the instruction producing the object
     * @param lockAddress the address of the on-stack lock object or {@code null} if the runtime does not place locks on the stack
     * @param lockNumber the number of the lock
     * @param stateBefore the state before
     */
    public MonitorEnter(Value object, Value lockAddress, int lockNumber, FrameState stateBefore) {
        super(object, lockAddress, stateBefore, lockNumber);
        if (object.isNonNull()) {
            eliminateNullCheck();
        }
    }

    /**
     * Checks whether this instruction can trap.
     * @return {@code true} if this instruction may raise a {@link NullPointerException}
     */
    @Override
    public boolean canTrap() {
        return needsNullCheck();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitMonitorEnter(this);
    }

    public void setStateAfter(FrameState frameState) {
        this.stateAfter = frameState;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void print(LogStream out) {
        out.print("enter monitor[").print(lockNumber).print("](").print(object()).print(')');
    }
}
