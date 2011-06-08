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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.graph.*;

/**
 * The {@code MonitorExit} instruction represents a monitor release.
 */
public final class MonitorExit extends AccessMonitor {

    private static final int INPUT_COUNT = 0;
    private static final int SUCCESSOR_COUNT = 0;

    /**
     * Creates a new MonitorExit instruction.
     *
     * @param object the instruction produces the object value
     * @param lockAddress the address of the on-stack lock object or {@code null} if the runtime does not place locks on the stack
     * @param lockNumber the number of the lock
     * @param graph
     */
    public MonitorExit(Value object, Value lockAddress, int lockNumber, Graph graph) {
        super(object, lockAddress, lockNumber, INPUT_COUNT, SUCCESSOR_COUNT, graph);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitMonitorExit(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("exit monitor[").print(lockNumber).print("](").print(object()).print(')');
    }

    @Override
    public Node copy(Graph into) {
        MonitorExit x = new MonitorExit(null, null, lockNumber, into);
        return x;
    }
}
