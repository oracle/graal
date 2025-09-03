/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.MonitorExit;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;

/**
 * The {@code MonitorExitNode} represents a monitor release. If it is the release of the monitor of
 * a synchronized method, then the return value of the method will be referenced via the edge
 * {@link #escapedReturnValue}, so that it will be materialized before releasing the monitor.
 */
@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public final class MonitorExitNode extends AccessMonitorNode implements Virtualizable, Lowerable, IterableNodeType, MonitorExit, SingleMemoryKill {

    public static final NodeClass<MonitorExitNode> TYPE = NodeClass.create(MonitorExitNode.class);

    /**
     * Non-null for the monitor exit introduced due to a synchronized root method and null in all
     * other cases.
     */
    @OptionalInput ValueNode escapedReturnValue;

    public MonitorExitNode(ValueNode object, MonitorIdNode monitorId, ValueNode escapedValue) {
        super(TYPE, object, monitorId);
        this.escapedReturnValue = escapedValue;
    }

    /**
     * Return value is cleared when a synchronized method graph is inlined.
     */
    public void clearEscapedValue() {
        updateUsages(escapedReturnValue, null);
        this.escapedReturnValue = null;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            if (virtual.hasIdentity()) {
                MonitorIdNode removedLock = tool.removeLock(virtual);
                GraalError.guarantee(MonitorIdNode.monitorIdentityEquals(removedLock, getMonitorId()), "mismatch at %s: %s vs. %s", this, removedLock, getMonitorId());
                tool.delete();
            }
        }
    }

}
