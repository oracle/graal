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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.common.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.Node.Child;

final class PartialEvaluatorCanonicalizer extends CanonicalizerPhase.CustomCanonicalizer {

    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;

    PartialEvaluatorCanonicalizer(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
    }

    @Override
    public Node canonicalize(Node node) {
        if (node instanceof LoadFieldNode) {
            return canonicalizeLoadField((LoadFieldNode) node);
        } else if (node instanceof LoadIndexedNode) {
            return canonicalizeLoadIndex((LoadIndexedNode) node);
        }
        return node;
    }

    private Node canonicalizeLoadField(LoadFieldNode loadFieldNode) {
        if (!loadFieldNode.isStatic() && loadFieldNode.object().isConstant() && !loadFieldNode.object().isNullConstant()) {
            if (loadFieldNode.field().isFinal() || (loadFieldNode.getKind() == Kind.Object && loadFieldNode.field().getAnnotation(Child.class) != null) ||
                            loadFieldNode.field().getAnnotation(CompilerDirectives.CompilationFinal.class) != null) {
                Constant constant = loadFieldNode.field().readValue(loadFieldNode.object().asConstant());
                assert verifyFieldValue(loadFieldNode.field(), constant);
                return ConstantNode.forConstant(constant, metaAccess);
            }
        }
        return loadFieldNode;
    }

    private Node canonicalizeLoadIndex(LoadIndexedNode loadIndexedNode) {
        if (loadIndexedNode.array().isConstant() && loadIndexedNode.index().isConstant()) {
            int index = loadIndexedNode.index().asConstant().asInt();

            Constant constant = constantReflection.readArrayElement(loadIndexedNode.array().asConstant(), index);
            if (constant != null) {
                return ConstantNode.forConstant(constant, metaAccess);
            }
        }
        return loadIndexedNode;
    }

    private boolean verifyFieldValue(ResolvedJavaField field, Constant constant) {
        assert field.getAnnotation(Child.class) == null || constant.isNull() ||
                        metaAccess.lookupJavaType(com.oracle.truffle.api.nodes.Node.class).isAssignableFrom(metaAccess.lookupJavaType(constant)) : "@Child field value must be a Node: " + field +
                        ", but was: " + constant;
        return true;
    }
}
