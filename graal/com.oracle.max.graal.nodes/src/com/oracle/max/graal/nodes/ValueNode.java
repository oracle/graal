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
package com.oracle.max.graal.nodes;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * This class represents a value within the graph, including local variables, phis, and
 * all other instructions.
 */
public abstract class ValueNode extends Node implements StampProvider {

    /**
     * The kind of this value. This is {@link CiKind#Void} for instructions that produce no value.
     * This kind is guaranteed to be a {@linkplain CiKind#stackKind() stack kind}.
     */
    @Data private Stamp stamp;

    /**
     * Creates a new value with the specified kind.
     * @param kind the type of this value
     * @param inputCount
     * @param successorCount
     * @param graph
     */
    public ValueNode(Stamp stamp) {
        this.stamp = stamp;
        assert kind() != null && kind() == kind().stackKind() : kind() + " != " + kind().stackKind();
    }

    public Stamp stamp() {
        return stamp;
    }

    public void setStamp(Stamp stamp) {
        this.stamp = stamp;
    }

    public CiKind kind() {
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
     * @return the {@link CiConstant} represented by this value if it is a constant; {@code null}
     * otherwise
     */
    public final CiConstant asConstant() {
        if (this instanceof ConstantNode) {
            return ((ConstantNode) this).value;
        }
        return null;
    }

    /**
     * Computes the exact type of the result of this node, if possible.
     * @return the exact type of the result of this node, if it is known; {@code null} otherwise
     */
    public final RiResolvedType exactType() {
        return stamp.exactType();
    }

    /**
     * Computes the declared type of the result of this node, if possible.
     * @return the declared type of the result of this node, if it is known; {@code null} otherwise
     */
    public final RiResolvedType declaredType() {
        return stamp.declaredType();
    }
}
