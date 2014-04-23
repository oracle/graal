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
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code MonitorExitNode} represents a monitor release. If it is the release of the monitor of
 * a synchronized method, then the return value of the method will be referenced, so that it will be
 * materialized before releasing the monitor.
 */
public final class MonitorExitNode extends AccessMonitorNode implements Virtualizable, Simplifiable, Lowerable, IterableNodeType, MonitorExit, MemoryCheckpoint.Single {

    @Input private ValueNode escapedReturnValue;

    /**
     * Creates a new MonitorExitNode.
     */
    public MonitorExitNode(ValueNode object, MonitorIdNode monitorId, ValueNode escapedReturnValue) {
        super(object, monitorId);
        this.escapedReturnValue = escapedReturnValue;
    }

    public void setEscapedReturnValue(ValueNode x) {
        updateUsages(escapedReturnValue, x);
        this.escapedReturnValue = x;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.ANY_LOCATION;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (escapedReturnValue != null && stateAfter() != null && stateAfter().bci != BytecodeFrame.AFTER_BCI) {
            ValueNode returnValue = escapedReturnValue;
            setEscapedReturnValue(null);
            tool.removeIfUnused(returnValue);
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object());
        // the monitor exit for a synchronized method should never be virtualized
        assert stateAfter().bci != BytecodeFrame.AFTER_BCI || state == null;
        if (state != null && state.getState() == EscapeState.Virtual && state.getVirtualObject().hasIdentity()) {
            MonitorIdNode removedLock = state.removeLock();
            assert removedLock == getMonitorId() : "mismatch at " + this + ": " + removedLock + " vs. " + getMonitorId();
            tool.delete();
        }
    }
}
