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

public final class TypeCastNode extends FixedWithNextNode implements Lowerable, com.oracle.graal.graph.Node.IterableNodeType, ValueProxy, Virtualizable {

    @Input private ValueNode receiver;
    @Input private ValueNode object;
    private final Object customType;
    private final ResolvedJavaType castTarget;

    public TypeCastNode(ValueNode object, ResolvedJavaType castTarget, ValueNode receiver, Object customType) {
        super(StampFactory.declaredNonNull(castTarget));
        this.receiver = receiver;
        this.object = object;
        this.customType = customType;
        this.castTarget = castTarget;
    }

    public ValueNode getObject() {
        return object;
    }

    public ValueNode getReceiver() {
        return receiver;
    }

    public ResolvedJavaType getCastTarget() {
        return castTarget;
    }

    public Object getCustomType() {
        return customType;
    }

    public void lower(LoweringTool tool, LoweringType loweringType) {
        if (loweringType == LoweringType.BEFORE_GUARDS) {
            ValueAnchorNode valueAnchorNode = graph().add(new ValueAnchorNode());
            UnsafeCastNode unsafeCast = graph().unique(new UnsafeCastNode(object, this.stamp(), (GuardingNode) valueAnchorNode));
            this.replaceAtUsages(unsafeCast);
            graph().replaceFixedWithFixed(this, valueAnchorNode);
        }
    }

    public ValueNode getOriginalValue() {
        return object;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual) {
            tool.replaceWithVirtual(state.getVirtualObject());
        }
    }
}
