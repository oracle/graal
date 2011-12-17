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
package com.oracle.max.graal.nodes.java;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

public final class IsTypeNode extends BooleanNode implements Canonicalizable, LIRLowerable {

    @Input private ValueNode object;

    public ValueNode object() {
        return object;
    }

    private final RiResolvedType type;

    /**
     * Constructs a new IsTypeNode.
     *
     * @param object the instruction producing the object to check against the given type
     * @param type the type for this check
     */
    public IsTypeNode(ValueNode object, RiResolvedType type) {
        super(StampFactory.illegal());
        assert object == null || object.kind() == CiKind.Object;
        this.type = type;
        this.object = object;
    }

    public RiResolvedType type() {
        return type;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // nothing to do
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("type", type);
        return properties;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (object().exactType() != null) {
            return ConstantNode.forBoolean(object().exactType() == type(), graph());
        }
        // constants return the correct exactType, so they are handled by the code above
        return this;
    }

    @Override
    public BooleanNode negate() {
        throw new Error("unimplemented");
    }
}
