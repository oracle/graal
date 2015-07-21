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
package com.oracle.graal.nodes;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;

/**
 * Value {@link PhiNode}s merge data flow values at control flow merges.
 */
@NodeInfo(nameTemplate = "Phi({i#values})")
public class ValuePhiNode extends PhiNode implements ArrayLengthProvider {

    public static final NodeClass<ValuePhiNode> TYPE = NodeClass.create(ValuePhiNode.class);
    @Input protected NodeInputList<ValueNode> values;

    public ValuePhiNode(Stamp stamp, AbstractMergeNode merge) {
        this(TYPE, stamp, merge);
    }

    protected ValuePhiNode(NodeClass<? extends ValuePhiNode> c, Stamp stamp, AbstractMergeNode merge) {
        super(c, stamp, merge);
        assert stamp != StampFactory.forVoid();
        values = new NodeInputList<>(this);
    }

    public ValuePhiNode(Stamp stamp, AbstractMergeNode merge, ValueNode[] values) {
        super(TYPE, stamp, merge);
        assert stamp != StampFactory.forVoid();
        this.values = new NodeInputList<>(this, values);
    }

    @Override
    public NodeInputList<ValueNode> values() {
        return values;
    }

    @Override
    public boolean inferStamp() {
        Stamp valuesStamp = StampTool.meet(values());
        if (stamp.isCompatible(valuesStamp)) {
            valuesStamp = stamp.join(valuesStamp);
        }
        return updateStamp(valuesStamp);
    }

    public ValueNode length() {
        if (merge() instanceof LoopBeginNode) {
            return null;
        }
        ValueNode length = null;
        for (ValueNode input : values()) {
            ValueNode l = GraphUtil.arrayLength(input);
            if (l == null) {
                return null;
            }
            if (length == null) {
                length = l;
            } else if (length != l) {
                return null;
            }
        }
        return length;
    }
}
