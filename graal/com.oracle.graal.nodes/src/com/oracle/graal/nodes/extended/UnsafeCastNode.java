/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.ResolvedJavaType;

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.IterableNodeType;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FloatingGuardedNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.nodes.spi.ValueProxy;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;

/**
 * The {@code UnsafeCastNode} produces the same value as its input, but with a different type. It
 * allows unsafe casts "sideways" in the type hierarchy. It does not allow to "drop" type
 * information, i.e., an unsafe cast is removed if the input object has a more precise or equal type
 * than the type this nodes casts to.
 */
@NodeInfo
public final class UnsafeCastNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, GuardingNode, IterableNodeType, Canonicalizable, ValueProxy {

    public static final NodeClass<UnsafeCastNode> TYPE = NodeClass.create(UnsafeCastNode.class);
    @Input ValueNode object;

    public UnsafeCastNode(ValueNode object, Stamp stamp) {
        super(TYPE, stamp);
        this.object = object;
    }

    public UnsafeCastNode(ValueNode object, Stamp stamp, ValueNode anchor) {
        super(TYPE, stamp, (GuardingNode) anchor);
        this.object = object;
    }

    public UnsafeCastNode(ValueNode object, ResolvedJavaType toType, boolean exactType, boolean nonNull) {
        this(object, toType.getJavaKind() == JavaKind.Object ? StampFactory.object(toType, exactType, nonNull || StampTool.isPointerNonNull(object.stamp()), true)
                        : StampFactory.forKind(toType.getJavaKind()));
    }

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        assert getStackKind() == JavaKind.Object && object.getStackKind() == JavaKind.Object;

        ObjectStamp my = (ObjectStamp) stamp();
        ObjectStamp other = (ObjectStamp) object.stamp();

        if (my.type() == null || other.type() == null) {
            return this;
        }
        if (my.isExactType() && !other.isExactType()) {
            return this;
        }
        if (my.nonNull() && !other.nonNull()) {
            return this;
        }
        if (!my.type().isAssignableFrom(other.type())) {
            return this;
        }
        /*
         * The unsafe cast does not add any new type information, so it can be removed. Note that
         * this means that the unsafe cast cannot be used to "drop" type information (in which case
         * it must not be canonicalized in any case).
         */
        return object;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object);
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            if (StampTool.typeOrNull(this) != null && StampTool.typeOrNull(this).isAssignableFrom(virtual.type())) {
                tool.replaceWithVirtual(virtual);
            }
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        assert getStackKind() == JavaKind.Object && object.getStackKind() == JavaKind.Object;
        /*
         * The LIR only cares about the kind of an operand, not the actual type of an object. So we
         * do not have to introduce a new operand.
         */
        generator.setResult(this, generator.operand(object));
    }
}
