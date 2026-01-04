/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.GraalOptions.SpeculateVirtualLocks;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.MonitorEnter;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * The {@code MonitorEnterNode} represents the acquisition of a monitor.
 */
@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public class MonitorEnterNode extends AccessMonitorNode implements Virtualizable, Lowerable, IterableNodeType, MonitorEnter, SingleMemoryKill {

    public static final NodeClass<MonitorEnterNode> TYPE = NodeClass.create(MonitorEnterNode.class);

    /**
     * Some configurations where {@link PlatformConfigurationProvider#areLocksSideEffectFree()}
     * returns true might speculatively attempt to acquire virtual locks. If this speculation fails
     * then these locks should not be considered virtualizable.
     */
    public static final SpeculationReasonGroup MONITOR_ENTER_NO_SIDE_EFFECT = new SpeculationReasonGroup("MonitorEnterNoSideEffect");

    /**
     * True if this was inserted by a {@link jdk.graal.compiler.nodes.virtual.CommitAllocationNode}.
     * This means that lock stack of the {@link #stateAfter} doesn't necessarily match the top of
     * the stack.
     */
    private boolean isSynthetic;

    public MonitorEnterNode(ValueNode object, MonitorIdNode monitorId) {
        this(TYPE, object, monitorId);
    }

    public MonitorEnterNode(NodeClass<? extends MonitorEnterNode> c, ValueNode object, MonitorIdNode monitorId) {
        super(c, object, monitorId);
    }

    public void setSynthetic() {
        isSynthetic = true;
    }

    public boolean isSynthetic() {
        return isSynthetic;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (!tool.getPlatformConfigurationProvider().areLocksSideEffectFree()) {
            // If locks have side effects then we can speculate that they can be safely acquired by
            // the CommitAllocationNode lowering. If that fails then they cannot be virtualized.
            if (!SpeculateVirtualLocks.getValue(tool.getOptions())) {
                return;
            }
            SpeculationLog speculationLog = graph().getSpeculationLog();
            if (speculationLog == null || !speculationLog.maySpeculate(MONITOR_ENTER_NO_SIDE_EFFECT.createSpeculationReason())) {
                return;
            }
        }
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            if (virtual.hasIdentity() && tool.canVirtualizeLock(virtual, getMonitorId())) {
                tool.addLock(virtual, getMonitorId());
                if (!tool.getPlatformConfigurationProvider().areLocksSideEffectFree()) {
                    if (object() instanceof AllocatedObjectNode) {
                        /*
                         * Don't let the allocation sink past the state split proxy, otherwise we
                         * end up with a virtual object in the state instead of the actual allocated
                         * one.
                         */
                        tool.ensureMaterialized(virtual);
                    }
                    // Ensure that the locks appear to have been acquired in the nearest FrameState.
                    tool.ensureAdded(new StateSplitProxyNode(stateAfter));
                }
                tool.delete();
            }
        }
    }

}
