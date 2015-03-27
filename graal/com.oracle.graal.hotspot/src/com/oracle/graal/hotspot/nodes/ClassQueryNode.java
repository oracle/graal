/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.replacements.nodes.*;

/**
 * {@link MacroNode Macro node} for some basic query methods in {@link Class}.
 */
@NodeInfo
public final class ClassQueryNode extends MacroStateSplitNode implements Canonicalizable {

    /**
     * The query methods in {@link Class} supported by {@link ClassQueryNode}.
     */
    public enum Query {
        getClassLoader0(Kind.Object),
        getComponentType(Kind.Object),
        getSuperclass(Kind.Object),
        getModifiers(Kind.Int),
        isArray(Kind.Boolean),
        isInterface(Kind.Boolean),
        isPrimitive(Kind.Boolean);

        private Query(Kind returnKind) {
            this.returnKind = returnKind;
        }

        public final Kind returnKind;
    }

    public static final NodeClass<ClassQueryNode> TYPE = NodeClass.create(ClassQueryNode.class);

    protected final Query query;

    public ClassQueryNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, Query query, int bci, JavaType returnType, ValueNode receiver) {
        super(TYPE, invokeKind, targetMethod, bci, returnType, receiver);
        this.query = query;
        assert query.returnKind == targetMethod.getSignature().getReturnKind();
    }

    private ValueNode getJavaClass() {
        return arguments.get(0);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ValueNode value = tryFold(getJavaClass(), query, tool.getMetaAccess(), tool.getConstantReflection());
        return value == null ? this : value;
    }

    public static ValueNode tryFold(ValueNode javaClass, Query query, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        ValueNode value = GraphUtil.originalValue(javaClass);
        if (value != null && value.isConstant()) {
            if (query.returnKind == Kind.Object) {
                if (GraalOptions.ImmutableCode.getValue()) {
                    return null;
                }
                HotSpotObjectConstant c = (HotSpotObjectConstant) value.asConstant();
                JavaConstant answer;
                switch (query) {
                    case getClassLoader0:
                        answer = c.getClassLoader();
                        break;
                    case getComponentType:
                        answer = c.getComponentType();
                        break;
                    case getSuperclass:
                        answer = c.getSuperclass();
                        break;
                    default:
                        GraalInternalError.shouldNotReachHere();
                        answer = null;
                }
                if (answer != null) {
                    return ConstantNode.forConstant(answer, metaAccess);
                }
            } else {
                ResolvedJavaType type = constantReflection.asJavaType(value.asConstant());
                if (type != null) {
                    switch (query) {
                        case isArray:
                            return ConstantNode.forBoolean(type.isArray());
                        case isPrimitive:
                            return ConstantNode.forBoolean(type.isPrimitive());
                        case isInterface:
                            return ConstantNode.forBoolean(type.isInterface());
                        case getModifiers:
                            return ConstantNode.forInt(type.getModifiers());
                        default:
                            GraalInternalError.shouldNotReachHere();
                    }
                }
            }
        }
        return null;
    }
}
