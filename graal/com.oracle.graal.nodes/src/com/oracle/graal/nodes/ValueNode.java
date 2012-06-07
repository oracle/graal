/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.type.GenericStamp.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

/**
 * This class represents a value within the graph, including local variables, phis, and
 * all other instructions.
 */
public abstract class ValueNode extends ScheduledNode implements StampProvider {

    /**
     * The kind of this value. This is {@link RiKind#Void} for instructions that produce no value.
     * This kind is guaranteed to be a {@linkplain RiKind#stackKind() stack kind}.
     */
    private Stamp stamp;

    @Input(notDataflow = true) private NodeInputList<ValueNode> dependencies;

    /**
     * This collection keeps dependencies that should be observed while scheduling (guards, etc.).
     */
    public NodeInputList<ValueNode> dependencies() {
        return dependencies;
    }

    public ValueNode(Stamp stamp) {
        this.stamp = stamp;
        this.dependencies = new NodeInputList<>(this);
        assert kind() != null && kind() == kind().stackKind() : kind() + " != " + kind().stackKind();
    }

    public ValueNode(Stamp stamp, ValueNode... dependencies) {
        this.stamp = stamp;
        this.dependencies = new NodeInputList<>(this, dependencies);
        assert kind() != null && kind() == kind().stackKind() : kind() + " != " + kind().stackKind();
    }

    public ValueNode(Stamp stamp, List<ValueNode> dependencies) {
        this.stamp = stamp;
        this.dependencies = new NodeInputList<>(this, dependencies);
        assert kind() != null && kind() == kind().stackKind() : kind() + " != " + kind().stackKind();
    }

    public Stamp stamp() {
        return stamp;
    }

    public void setStamp(Stamp stamp) {
        this.stamp = stamp;
    }

    public RiKind kind() {
        return stamp.kind();
    }

    /**
     * Checks whether this value is a constant (i.e. it is of type {@link ConstantNode}.
     * @return {@code true} if this value is a constant
     */
    public final boolean isConstant() {
        return this instanceof ConstantNode;
    }

    /**
     * Checks whether this value represents the null constant.
     * @return {@code true} if this value represents the null constant
     */
    public final boolean isNullConstant() {
        return this instanceof ConstantNode && ((ConstantNode) this).value.isNull();
    }

    /**
     * Convert this value to a constant if it is a constant, otherwise return null.
     * @return the {@link RiConstant} represented by this value if it is a constant; {@code null}
     * otherwise
     */
    public final RiConstant asConstant() {
        if (this instanceof ConstantNode) {
            return ((ConstantNode) this).value;
        }
        return null;
    }

    public final ObjectStamp objectStamp() {
        return (ObjectStamp) stamp;
    }

    public final IntegerStamp integerStamp() {
        return (IntegerStamp) stamp;
    }

    public final FloatStamp floatStamp() {
        return (FloatStamp) stamp;
    }

    @Override
    public boolean verify() {
        for (ValueNode v : dependencies().nonNull()) {
            assertTrue(!(v.stamp() instanceof GenericStamp) || ((GenericStamp) v.stamp()).type() == GenericStampType.Dependency, "cannot depend on node with stamp %s", v.stamp());
        }
        return super.verify();
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        if (!dependencies.isEmpty()) {
            StringBuilder str = new StringBuilder();
            for (int i = 0; i < dependencies.size(); i++) {
                str.append(i == 0 ? "" : ", ").append(dependencies.get(i) == null ? "null" : dependencies.get(i).toString(Verbosity.Id));
            }
            properties.put("dependencies", str.toString());
        }
        return properties;
    }
}
