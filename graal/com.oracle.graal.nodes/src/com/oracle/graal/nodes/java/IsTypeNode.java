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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.JavaType.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public final class IsTypeNode extends BooleanNode implements Canonicalizable, LIRLowerable {

    @Input private ValueNode objectClass;
    private final ResolvedJavaType type;

    public ValueNode objectClass() {
        return objectClass;
    }

    /**
     * Constructs a new IsTypeNode.
     *
     * @param objectClass the instruction producing the object to check against the given type
     * @param type the type for this check
     */
    public IsTypeNode(ValueNode objectClass, ResolvedJavaType type) {
        super(StampFactory.condition());
        assert objectClass == null || objectClass.kind() == Kind.Object;
        this.type = type;
        this.objectClass = objectClass;
    }

    public ResolvedJavaType type() {
        return type;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // nothing to do
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (objectClass().isConstant()) {
            Constant constant = objectClass().asConstant();
            Constant typeHub = type.getEncoding(Representation.ObjectHub);
            assert constant.kind == typeHub.kind;
            return ConstantNode.forBoolean(tool.runtime().areConstantObjectsEqual(constant, typeHub), graph());
        }
        // TODO(ls) since a ReadHubNode with an exactType should canonicalize itself to a constant this should actually never happen, maybe turn into an assertion?
        if (objectClass() instanceof ReadHubNode) {
            ObjectStamp stamp = ((ReadHubNode) objectClass()).object().objectStamp();
            if (stamp.isExactType()) {
                return ConstantNode.forBoolean(stamp.type() == type(), graph());
            }
        }
        return this;
    }
}
