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
public final class MonitorExitNode extends AccessMonitorNode implements Virtualizable, Simplifiable, Lowerable, IterableNodeType, MonitorExit, MemoryCheckpoint.Single, MonitorReference {

    @Input private ValueNode escapedReturnValue;

    private int lockDepth;

    /**
     * Creates a new MonitorExitNode.
     */
    public MonitorExitNode(ValueNode object, ValueNode escapedReturnValue, int lockDepth) {
        super(object);
        this.escapedReturnValue = escapedReturnValue;
        this.lockDepth = lockDepth;
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
        if (escapedReturnValue != null && stateAfter().bci != FrameState.AFTER_BCI) {
            ValueNode returnValue = escapedReturnValue;
            setEscapedReturnValue(null);
            tool.removeIfUnused(returnValue);
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public int getLockDepth() {
        return lockDepth;
    }

    public void setLockDepth(int lockDepth) {
        this.lockDepth = lockDepth;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object());
        // the monitor exit for a synchronized method should never be virtualized
        assert stateAfter().bci != FrameState.AFTER_BCI || state == null;
        if (state != null && state.getState() == EscapeState.Virtual && state.getVirtualObject().hasIdentity()) {
            int removedLock = state.removeLock();
            assert removedLock == getLockDepth();
            tool.delete();
        }
    }
}
