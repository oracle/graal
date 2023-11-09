/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;

/**
 * Base class for {@link AbstractBeginNode}s that are associated with a frame state.
 *
 * TODO (dnsimon) this not needed until {@link AbstractBeginNode} no longer implements
 * {@link StateSplit} which is not possible until loop peeling works without requiring begin nodes
 * to have frames states.
 */
@NodeInfo
public abstract class BeginStateSplitNode extends AbstractBeginNode implements StateSplit {

    public static final NodeClass<BeginStateSplitNode> TYPE = NodeClass.create(BeginStateSplitNode.class);
    @OptionalInput(InputType.State) protected FrameState stateAfter;

    protected BeginStateSplitNode(NodeClass<? extends BeginStateSplitNode> c) {
        super(c);
    }

    protected BeginStateSplitNode(NodeClass<? extends BeginStateSplitNode> c, Stamp stamp) {
        super(c, stamp);
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        GraalError.guarantee(x == null || x.isAlive(), "frame state must be in a graph: %s", x);
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    /**
     * A begin node has no side effect.
     */
    @Override
    public boolean hasSideEffect() {
        return false;
    }
}
