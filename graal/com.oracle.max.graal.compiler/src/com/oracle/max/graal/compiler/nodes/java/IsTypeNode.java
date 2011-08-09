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
package com.oracle.max.graal.compiler.nodes.java;

import java.util.*;

import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code TypeCheck} class represents an explicit type check instruction.
 */
public final class IsTypeNode extends BooleanNode implements Canonicalizable {

    @Input private ValueNode object;

    public ValueNode object() {
        return object;
    }

    public void setObject(ValueNode x) {
        updateUsages(object, x);
        object = x;
    }

    private final RiType type;

    /**
     * Constructs a new IsType instruction.
     *
     * @param object the instruction producing the object to check against the given type
     * @param graph
     */
    public IsTypeNode(ValueNode object, RiType type, Graph graph) {
        super(CiKind.Object, graph);
        assert type.isResolved();
        assert object == null || object.kind == CiKind.Object;
        this.type = type;
        setObject(object);
    }

    public RiType type() {
        return type;
    }

    @Override
    public void accept(ValueVisitor v) {
        // Nothing to do.
    }

    @Override
    public RiType declaredType() {
        // type check does not alter the type of the object
        return object().declaredType();
    }

    @Override
    public RiType exactType() {
        return type;
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("type", type);
        return properties;
    }

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        if (object().exactType() != null) {
            return ConstantNode.forBoolean(object().exactType() == type(), graph());
        }
        // constants return the correct exactType, so they are handled by the code above
        return this;
    }
}
