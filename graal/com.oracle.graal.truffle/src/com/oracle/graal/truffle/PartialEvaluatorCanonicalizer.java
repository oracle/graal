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
package com.oracle.graal.truffle;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.common.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.Node.Child;

final class PartialEvaluatorCanonicalizer implements CanonicalizerPhase.CustomCanonicalizer {

    private final MetaAccessProvider metaAccessProvider;
    private final ResolvedJavaType nodeClass;

    PartialEvaluatorCanonicalizer(MetaAccessProvider metaAccessProvider, ResolvedJavaType nodeClass) {
        this.metaAccessProvider = metaAccessProvider;
        this.nodeClass = nodeClass;
    }

    @Override
    public ValueNode canonicalize(ValueNode node) {
        if (node instanceof LoadFieldNode) {
            LoadFieldNode loadFieldNode = (LoadFieldNode) node;
            if (!loadFieldNode.isStatic() &&
                            loadFieldNode.object().isConstant() &&
                            !loadFieldNode.object().isNullConstant() &&
                            ((loadFieldNode.kind() == Kind.Object && loadFieldNode.field().getAnnotation(Child.class) != null) || Modifier.isFinal(loadFieldNode.field().getModifiers()) || loadFieldNode.field().getAnnotation(
                                            CompilerDirectives.CompilationFinal.class) != null)) {
                Constant constant = loadFieldNode.field().readValue(loadFieldNode.object().asConstant());
                return ConstantNode.forConstant(constant, metaAccessProvider, node.graph());
            }
        } else if (node instanceof LoadIndexedNode) {
            LoadIndexedNode loadIndexedNode = (LoadIndexedNode) node;
            Stamp stamp = loadIndexedNode.array().stamp();
            if (stamp.kind() == Kind.Object && loadIndexedNode.array().isConstant() && !loadIndexedNode.array().isNullConstant() && loadIndexedNode.index().isConstant()) {
                ObjectStamp objectStamp = (ObjectStamp) stamp;
                ResolvedJavaType type = objectStamp.type();
                if (type != null && type.isArray() && this.nodeClass.isAssignableFrom(type.getComponentType())) {
                    Object array = loadIndexedNode.array().asConstant().asObject();
                    int index = loadIndexedNode.index().asConstant().asInt();
                    Object value = Array.get(array, index);
                    return ConstantNode.forObject(value, metaAccessProvider, node.graph());
                }
            }
        }

        return node;
    }
}
