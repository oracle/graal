/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;

/**
 * The {@code ControlSplitNode} is a base class for all instructions that split the control flow
 * (i.e., have more than one successor).
 */
@NodeInfo
public abstract class ControlSplitNode extends FixedNode {
    public static final NodeClass<ControlSplitNode> TYPE = NodeClass.create(ControlSplitNode.class);

    protected ControlSplitNode(NodeClass<? extends ControlSplitNode> c, Stamp stamp) {
        super(c, stamp);
    }

    public abstract double probability(AbstractBeginNode successor);

    /**
     * Attempts to set the probability for the given successor to the passed value (which has to be
     * in the range of 0.0 and 1.0). Returns whether setting the probability was successful. When
     * successful, sets the source of the knowledge about probabilities according to the
     * {@code profileData}'s profile source.
     */
    public abstract boolean setProbability(AbstractBeginNode successor, BranchProbabilityData profileData);

    /**
     * Primary successor of the control split. Data dependencies on the node have to be scheduled in
     * the primary successor. Returns null if data dependencies are not expected.
     *
     * @return the primary successor
     */
    public abstract AbstractBeginNode getPrimarySuccessor();

    /**
     * Returns the number of successors.
     */
    public abstract int getSuccessorCount();

    /**
     * Returns the source of this node's knowledge about its successor probabilities.
     */
    public abstract ProfileData getProfileData();
}
