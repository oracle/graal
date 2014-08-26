/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;

/**
 * This node represents an unconditional explicit request for immediate deoptimization.
 *
 * After this node, execution will continue using a fallback execution engine (such as an
 * interpreter) at the position described by the {@link #stateBefore() deoptimization state}.
 */
@NodeInfo
public abstract class AbstractDeoptimizeNode extends ControlSinkNode implements IterableNodeType, DeoptimizingNode.DeoptBefore {

    @OptionalInput(InputType.State) FrameState stateBefore;

    public AbstractDeoptimizeNode() {
        super(StampFactory.forVoid());
    }

    public AbstractDeoptimizeNode(FrameState stateBefore) {
        super(StampFactory.forVoid());
        this.stateBefore = stateBefore;
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
    public void setStateBefore(FrameState f) {
        updateUsages(stateBefore, f);
        stateBefore = f;
    }

    public abstract ValueNode getActionAndReason(MetaAccessProvider metaAccess);

    public abstract ValueNode getSpeculation(MetaAccessProvider metaAccess);
}
