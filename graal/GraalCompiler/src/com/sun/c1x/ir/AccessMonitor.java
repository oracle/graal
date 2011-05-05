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

import com.oracle.graal.graph.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code AccessMonitor} instruction is the base class of both monitor acquisition and release.
 */
public abstract class AccessMonitor extends StateSplit {

    private static final int INPUT_COUNT = 2;
    private static final int INPUT_OBJECT = 0;
    private static final int INPUT_LOCK_ADDRESS = 1;

    private static final int SUCCESSOR_COUNT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * The instruction producing the object locked or unlocked by this instruction.
     */
     public Value object() {
        return (Value) inputs().get(super.inputCount() + INPUT_OBJECT);
    }

    public Value setObject(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_OBJECT, n);
    }

    /**
     * The instruction producing the address of the lock object.
     */
    public Value lockAddress() {
        return (Value) inputs().get(super.inputCount() + INPUT_LOCK_ADDRESS);
    }

    public Value setLockAddress(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_LOCK_ADDRESS, n);
    }

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
     * @param inputCount
     * @param successorCount
     * @param graph
     */
    public AccessMonitor(Value object, Value lockAddress, FrameState stateBefore, int lockNumber, int inputCount, int successorCount, Graph graph) {
        super(CiKind.Illegal, stateBefore, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, graph);
        this.lockNumber = lockNumber;
        setObject(object);
        setLockAddress(lockAddress);
    }
}
