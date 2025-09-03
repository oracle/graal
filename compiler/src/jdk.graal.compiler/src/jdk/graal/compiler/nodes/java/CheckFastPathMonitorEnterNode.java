/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

import java.util.List;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.spi.Lowerable;

/**
 * Checks if it's possible to acquire a set of locks without entering the runtime and exposing these
 * locks to deoptimization. This is used by the lowering of
 * {@link jdk.graal.compiler.nodes.virtual.CommitAllocationNode} to ensure that we aren't exposed to
 * deopt when acquiring locks for non-escaping objects.
 */
@NodeInfo(cycles = CYCLES_64, size = SIZE_64)
public class CheckFastPathMonitorEnterNode extends FixedWithNextNode implements Lowerable, DeoptimizingNode.DeoptBefore {

    public static final NodeClass<CheckFastPathMonitorEnterNode> TYPE = NodeClass.create(CheckFastPathMonitorEnterNode.class);

    @OptionalInput(Association) private NodeInputList<MonitorIdNode> ids;
    @OptionalInput(InputType.State) protected FrameState stateBefore;

    @SuppressWarnings("this-escape")
    public CheckFastPathMonitorEnterNode(List<MonitorIdNode> ids) {
        super(TYPE, StampFactory.forVoid());
        this.ids = new NodeInputList<>(this, ids);
    }

    /**
     * Returns the number of locks which must be acquired, excluding any locks which have been
     * eliminated.
     */
    public int lockDepth() {
        int count = 0;
        for (MonitorIdNode id : ids) {
            if (!id.isEliminated()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    @Override
    public void setStateBefore(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateBefore, x);
        stateBefore = x;
    }
}
