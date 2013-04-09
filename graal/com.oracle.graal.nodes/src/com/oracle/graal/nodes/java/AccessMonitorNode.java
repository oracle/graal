/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code AccessMonitorNode} is the base class of both monitor acquisition and release.
 * <p>
 * The VM needs information about monitors in the debug information. This information is built from
 * the nesting level of {@link MonitorEnterNode} when the LIR is constructed. Therefore, monitor
 * nodes must not be removed from the graph unless it is guaranteed that the nesting level does not
 * change. For example, you must not remove a {@link MonitorEnterNode} for a thread-local object or
 * for a recursive locking. Instead, mark the node as {@link #eliminated}. This makes sure that the
 * meta data still contains the complete locking hierarchy.
 * <p>
 * The Java bytecode specification allows non-balanced locking. Graal does not handle such cases and
 * throws a {@link BailoutException} instead during graph building.
 */
public abstract class AccessMonitorNode extends AbstractStateSplit implements StateSplit, MemoryCheckpoint, Virtualizable {

    @Input private ValueNode object;
    private boolean eliminated;

    public ValueNode object() {
        return object;
    }

    public boolean eliminated() {
        return eliminated;
    }

    public void eliminate() {
        eliminated = true;
    }

    /**
     * Creates a new AccessMonitor instruction.
     * 
     * @param object the instruction producing the object
     */
    public AccessMonitorNode(ValueNode object) {
        super(StampFactory.forVoid());
        this.object = object;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual && state.getVirtualObject().getClass() == VirtualInstanceNode.class) {
            Debug.log("monitor operation %s on %s\n", this, state);
            int newLockCount = state.getLockCount() + (this instanceof MonitorEnterNode ? 1 : -1);
            state.setLockCount(newLockCount);
            tool.replaceFirstInput(object(), state.getVirtualObject());
            tool.customAction(new Runnable() {

                @Override
                public void run() {
                    eliminate();
                }
            });
        }
    }
}
