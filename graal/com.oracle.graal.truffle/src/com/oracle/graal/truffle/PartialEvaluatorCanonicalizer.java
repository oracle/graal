/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.truffle.nodes.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;

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
            ResolvedJavaField field = loadFieldNode.field();
            JavaType fieldType = field.getType();
            if (field.isFinal() || field.getAnnotation(CompilationFinal.class) != null ||
                            (fieldType.getKind() == Kind.Object && (field.getAnnotation(Child.class) != null || field.getAnnotation(Children.class) != null))) {
                JavaConstant constant = field.readValue(loadFieldNode.object().asJavaConstant());
                assert verifyFieldValue(field, constant);
                if (constant.isNonNull() && fieldType.getKind() == Kind.Object && fieldType.getComponentType() != null &&
                                (field.getAnnotation(CompilationFinal.class) != null || field.getAnnotation(Children.class) != null)) {
                    int stableDimensions = getDeclaredArrayDimensions(fieldType);
                    return StableArrayConstantNode.forStableArrayConstant(constant, stableDimensions, true, metaAccess);
                } else {
                    return ConstantNode.forConstant(constant, metaAccess);
                }
            }
        }
        return loadFieldNode;
    }

    private Node canonicalizeLoadIndex(LoadIndexedNode loadIndexedNode) {
        if (loadIndexedNode.array() instanceof StableArrayConstantNode && loadIndexedNode.index().isConstant()) {
            StableArrayConstantNode stableArray = (StableArrayConstantNode) loadIndexedNode.array();
            int index = loadIndexedNode.index().asJavaConstant().asInt();

            JavaConstant constant = constantReflection.readArrayElement(loadIndexedNode.array().asJavaConstant(), index);
            if (constant != null) {
                if (stableArray.getStableDimensions() > 1 && constant.isNonNull()) {
                    return StableArrayConstantNode.forStableArrayConstant(constant, stableArray.getStableDimensions() - 1, stableArray.isCompilationFinal(), metaAccess);
                } else if (constant.isNonNull() || stableArray.isCompilationFinal()) {
                    return ConstantNode.forConstant(constant, metaAccess);
                }
            }
        }
        return loadIndexedNode;
    }

    private static int getDeclaredArrayDimensions(JavaType type) {
        int dimensions = 0;
        JavaType componentType = type;
        while ((componentType = componentType.getComponentType()) != null) {
            dimensions++;
        }
        return dimensions;
    }

    private boolean verifyFieldValue(ResolvedJavaField field, JavaConstant constant) {
        assert field.getAnnotation(Child.class) == null || constant.isNull() ||
                        metaAccess.lookupJavaType(com.oracle.truffle.api.nodes.Node.class).isAssignableFrom(metaAccess.lookupJavaType(constant)) : "@Child field value must be a Node: " + field +
                        ", but was: " + constant;
        assert field.getAnnotation(Children.class) == null || constant.isNull() || metaAccess.lookupJavaType(constant).isArray() : "@Children field value must be an array: " + field + ", but was: " +
                        constant;
        return true;
    }
}
