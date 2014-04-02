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
package com.oracle.graal.nodes;

//JaCoCo Exclude

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A node that changes the type of its input, usually narrowing it. For example, a PiNode refines
 * the type of a receiver during type-guarded inlining to be the type tested by the guard.
 * 
 * In contrast to a {@link GuardedValueNode}, a PiNode is useless as soon as the type of its input
 * is as narrow or narrower than the PiNode's type. The PiNode, and therefore also the scheduling
 * restriction enforced by the anchor, will go away.
 */
public class PiNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, IterableNodeType, GuardingNode, Canonicalizable, ValueProxy {

    @Input private ValueNode object;

    public ValueNode object() {
        return object;
    }

    public PiNode(ValueNode object, Stamp stamp) {
        super(stamp);
        this.object = object;
    }

    public PiNode(ValueNode object, Stamp stamp, ValueNode anchor) {
        super(stamp, (GuardingNode) anchor);
        this.object = object;
    }

    public PiNode(ValueNode object, ResolvedJavaType toType, boolean exactType, boolean nonNull) {
        this(object, StampFactory.object(toType, exactType, nonNull || ObjectStamp.isObjectNonNull(object.stamp())));
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        if (object.getKind() != Kind.Void && object.getKind() != Kind.Illegal) {
            generator.setResult(this, generator.operand(object));
        }
    }

    @Override
    public boolean inferStamp() {
        if (stamp() == StampFactory.forNodeIntrinsic()) {
            return false;
        }
        return updateStamp(stamp().join(object().stamp()));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object);
        if (state != null && state.getState() == EscapeState.Virtual && ObjectStamp.typeOrNull(this) != null && ObjectStamp.typeOrNull(this).isAssignableFrom(state.getVirtualObject().type())) {
            tool.replaceWithVirtual(state.getVirtualObject());
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        inferStamp();
        if (stamp().equals(object().stamp())) {
            return object();
        }
        return this;
    }

    @Override
    public ValueNode getOriginalValue() {
        return object;
    }

    @NodeIntrinsic
    public static native <T> T piCast(Object object, @ConstantNodeParameter Stamp stamp);

    @NodeIntrinsic
    public static native <T> T piCast(Object object, @ConstantNodeParameter Stamp stamp, GuardingNode anchor);

    public static <T> T piCastExactNonNull(Object object, @ConstantNodeParameter Class<T> toType) {
        return piCast(object, toType, true, true);
    }

    public static <T> T piCast(Object object, @ConstantNodeParameter Class<T> toType) {
        return piCast(object, toType, false, false);
    }

    public static <T> T piCastNonNull(Object object, @ConstantNodeParameter Class<T> toType) {
        return piCast(object, toType, false, true);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    private static <T> T piCast(Object object, @ConstantNodeParameter Class<T> toType, @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull) {
        return toType.cast(object);
    }
}
