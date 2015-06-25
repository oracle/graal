/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code MonitorEnterNode} represents the acquisition of a monitor. The object needs already be
 * non-null and the hub is an additional parameter to the node.
 */
@NodeInfo
public final class RawMonitorEnterNode extends AccessMonitorNode implements Virtualizable, Lowerable, IterableNodeType, MonitorEnter, MemoryCheckpoint.Single {

    public static final NodeClass<RawMonitorEnterNode> TYPE = NodeClass.create(RawMonitorEnterNode.class);

    @Input ValueNode hub;

    public RawMonitorEnterNode(ValueNode object, ValueNode hub, MonitorIdNode monitorId) {
        super(TYPE, object, monitorId);
        assert ((ObjectStamp) object.stamp()).nonNull();
        this.hub = hub;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object());
        if (state != null && state.getState() == EscapeState.Virtual && state.getVirtualObject().hasIdentity()) {
            state.addLock(getMonitorId());
            tool.delete();
        }
    }

    public ValueNode getHub() {
        return hub;
    }
}
