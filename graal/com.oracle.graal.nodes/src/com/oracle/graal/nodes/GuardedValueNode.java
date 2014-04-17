/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A node that changes the type of its input, usually narrowing it. For example, a GuardedValueNode
 * is used to keep the nodes depending on guards inside a loop during speculative guard movement.
 *
 * A GuardedValueNode will only go away if its guard is null or {@link StructuredGraph#start()}.
 */
public class GuardedValueNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, IterableNodeType, Canonicalizable, ValueProxy {

    @Input private ValueNode object;

    public GuardedValueNode(ValueNode object, GuardingNode guard, Stamp stamp) {
        super(stamp, guard);
        this.object = object;
    }

    public GuardedValueNode(ValueNode object, GuardingNode guard) {
        this(object, guard, object.stamp());
    }

    public ValueNode object() {
        return object;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        if (object.getKind() != Kind.Void && object.getKind() != Kind.Illegal) {
            generator.setResult(this, generator.operand(object));
        }
    }

    @Override
    public boolean inferStamp() {
        if (stamp() instanceof ObjectStamp && object().stamp() instanceof ObjectStamp) {
            return updateStamp(((ObjectStamp) object().stamp()).castTo((ObjectStamp) stamp()));
        }
        return updateStamp(object().stamp().join(stamp()));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual) {
            tool.replaceWithVirtual(state.getVirtualObject());
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (getGuard() == graph().start()) {
            if (stamp().equals(object().stamp())) {
                return object();
            } else {
                return graph().unique(new PiNode(object(), stamp()));
            }
        }
        return this;
    }

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }
}
