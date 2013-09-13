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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code MonitorEnterNode} represents a monitor release.
 */
public final class MonitorExitNode extends AccessMonitorNode implements Virtualizable, Lowerable, IterableNodeType, MonitorExit, MemoryCheckpoint.Single, MonitorReference {

    private int lockDepth;

    /**
     * Creates a new MonitorExitNode.
     * 
     * @param object the instruction produces the object value
     */
    public MonitorExitNode(ValueNode object, int lockDepth) {
        super(object);
        this.lockDepth = lockDepth;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.ANY_LOCATION;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
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
        if (state != null && state.getState() == EscapeState.Virtual && state.getVirtualObject().hasIdentity()) {
            int removedLock = state.removeLock();
            assert removedLock == getLockDepth();
            tool.delete();
        }
    }
}
