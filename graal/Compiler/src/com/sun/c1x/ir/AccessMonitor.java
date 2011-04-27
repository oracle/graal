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

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code AccessMonitor} instruction is the base class of both monitor acquisition and release.
 *
 * @author Ben L. Titzer
 */
public abstract class AccessMonitor extends StateSplit {

    /**
     * The object locked or unlocked by this instruction.
     */
    private Value object;

    /**
     * The address of the on-stack lock object or {@code null} if the runtime does not place locks on the stack.
     */
    private Value lockAddress;

    /**
     * The lock number of this monitor access.
     */
    public final int lockNumber;


    /**
     * Creates a new AccessMonitor instruction.
     *
     * @param object the instruction producing the object
     * @param lockAddress the address of the on-stack lock object or {@code null} if the runtime does not place locks on the stack
     * @param stateBefore the state before executing the monitor operation
     * @param lockNumber the number of the lock being acquired
     */
    public AccessMonitor(Value object, Value lockAddress, FrameState stateBefore, int lockNumber) {
        super(CiKind.Illegal, stateBefore);
        this.object = object;
        this.lockAddress = lockAddress;
        this.lockNumber = lockNumber;
    }

    /**
     * Gets the instruction producing the object locked or unlocked by this instruction.
     */
    public Value object() {
        return object;
    }

    /**
     * Gets the instruction producing the address of the lock object.
     */
    public Value lockAddress() {
        return lockAddress;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        object = closure.apply(object);
        if (lockAddress != null) {
            lockAddress = closure.apply(lockAddress);
        }
    }
}
