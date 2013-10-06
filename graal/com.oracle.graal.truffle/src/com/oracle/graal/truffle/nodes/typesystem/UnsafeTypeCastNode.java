/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes.typesystem;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public final class UnsafeTypeCastNode extends FixedWithNextNode implements Lowerable, com.oracle.graal.graph.IterableNodeType, ValueProxy, Virtualizable {

    @Input private ValueNode object;
    @Input private ValueNode condition;
    private final ResolvedJavaType castTarget;

    public UnsafeTypeCastNode(ValueNode object, ResolvedJavaType castTarget, ValueNode condition) {
        super(StampFactory.declaredNonNull(castTarget));
        this.condition = condition;
        this.object = object;
        this.castTarget = castTarget;
    }

    public ValueNode getObject() {
        return object;
    }

    public ValueNode getCondition() {
        return condition;
    }

    public ResolvedJavaType getCastTarget() {
        return castTarget;
    }

    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage() == StructuredGraph.GuardsStage.FLOATING_GUARDS) {
            ValueAnchorNode valueAnchorNode = graph().add(new ValueAnchorNode(null));
            PiNode piCast = graph().unique(new PiNode(object, this.stamp(), valueAnchorNode));
            this.replaceAtUsages(piCast);
            graph().replaceFixedWithFixed(this, valueAnchorNode);
        }
    }

    public ValueNode getOriginalValue() {
        return getObject();
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual) {
            tool.replaceWithVirtual(state.getVirtualObject());
        }
    }
}
