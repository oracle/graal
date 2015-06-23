/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.memory.address;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Represents an address that is composed of a base and an offset. The base can be either a
 * {@link Kind#Object}, a word-sized integer or another pointer. The offset must be a word-sized
 * integer.
 */
@NodeInfo(allowedUsageTypes = InputType.Association)
public class OffsetAddressNode extends AddressNode implements Canonicalizable, PiPushable {
    public static final NodeClass<OffsetAddressNode> TYPE = NodeClass.create(OffsetAddressNode.class);

    @Input ValueNode base;
    @Input ValueNode offset;

    public OffsetAddressNode(ValueNode base, ValueNode offset) {
        super(TYPE);
        this.base = base;
        this.offset = offset;
    }

    public ValueNode getBase() {
        return base;
    }

    public void setBase(ValueNode base) {
        updateUsages(this.base, base);
        this.base = base;
    }

    public ValueNode getOffset() {
        return offset;
    }

    public void setOffset(ValueNode offset) {
        updateUsages(this.offset, offset);
        this.offset = offset;
    }

    public Node canonical(CanonicalizerTool tool) {
        if (base instanceof RawAddressNode) {
            // The RawAddressNode is redundant, just directly use its input as base.
            return new OffsetAddressNode(((RawAddressNode) base).getAddress(), offset);
        } else if (base instanceof OffsetAddressNode) {
            // Rewrite (&base[offset1])[offset2] to base[offset1 + offset2].
            OffsetAddressNode b = (OffsetAddressNode) base;
            return new OffsetAddressNode(b.getBase(), BinaryArithmeticNode.add(b.getOffset(), this.getOffset()));
        } else {
            return this;
        }
    }

    @Override
    public boolean push(PiNode parent) {
        if (!(offset.isConstant() && parent.stamp() instanceof ObjectStamp && parent.object().stamp() instanceof ObjectStamp)) {
            return false;
        }

        ObjectStamp piStamp = (ObjectStamp) parent.stamp();
        ResolvedJavaType receiverType = piStamp.type();
        if (receiverType == null) {
            return false;
        }
        ResolvedJavaField field = receiverType.findInstanceFieldWithOffset(offset.asJavaConstant().asLong(), Kind.Void);
        if (field == null) {
            // field was not declared by receiverType
            return false;
        }

        ObjectStamp valueStamp = (ObjectStamp) parent.object().stamp();
        ResolvedJavaType valueType = StampTool.typeOrNull(valueStamp);
        if (valueType != null && field.getDeclaringClass().isAssignableFrom(valueType)) {
            if (piStamp.nonNull() == valueStamp.nonNull() && piStamp.alwaysNull() == valueStamp.alwaysNull()) {
                replaceFirstInput(parent, parent.object());
                return true;
            }
        }

        return false;
    }

    @NodeIntrinsic
    public static native Address address(Object base, long offset);
}
