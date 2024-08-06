/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * This node describes one locking scope; it ties the monitor enter, monitor exit and the frame
 * states together. It is thus referenced from the {@link MonitorEnterNode}, from the
 * {@link MonitorExitNode} and from the {@link FrameState}.
 */
@NodeInfo(allowedUsageTypes = Association, cycles = CYCLES_0, size = SIZE_0)
public class MonitorIdNode extends ValueNode implements IterableNodeType, LIRLowerable {

    public static final NodeClass<MonitorIdNode> TYPE = NodeClass.create(MonitorIdNode.class);
    protected int lockDepth;
    protected boolean eliminated;

    /**
     * We use the BCI as an identity for balanced locking.
     */
    protected final int bci;

    /**
     * Specifies if this is a monitor that was entered on disjoint control flow paths.
     */
    protected boolean multipleEntry;

    public MonitorIdNode(int lockDepth, int bci) {
        this(TYPE, lockDepth, bci);
    }

    public MonitorIdNode(int lockDepth, int bci, boolean multipleEntry) {
        this(TYPE, lockDepth, bci);
        this.multipleEntry = multipleEntry;
    }

    public MonitorIdNode(int lockDepth) {
        this(TYPE, lockDepth, -1);
    }

    protected MonitorIdNode(NodeClass<? extends MonitorIdNode> c, int lockDepth, int bci) {
        super(c, StampFactory.forVoid());
        this.lockDepth = lockDepth;
        this.bci = bci;
    }

    public int getBci() {
        return bci;
    }

    public void setMultipleEntry() {
        this.multipleEntry = true;
    }

    /**
     * Indicates that the associated monitor operations might have multiple distinct monitorenter
     * bytecodes for different objects. This violates some assumptions about well formed monitor
     * operations and may inhibit some high level lock optimizations.
     */
    public boolean isMultipleEntry() {
        return multipleEntry;
    }

    public int getLockDepth() {
        return lockDepth;
    }

    public void setLockDepth(int lockDepth) {
        this.lockDepth = lockDepth;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated() {
        eliminated = true;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // nothing to do
    }

    /**
     * Determine if the two monitor ID nodes represent locking of the same bytecode location.
     */
    public static boolean monitorIdentityEquals(MonitorIdNode m1, MonitorIdNode m2) {
        if (m1 == m2) {
            return true;
        }
        if (m1.getLockDepth() == m2.getLockDepth()) {
            if (m1.getBci() == m2.getBci()) {
                return true;
            }
        }
        return false;
    }

}
