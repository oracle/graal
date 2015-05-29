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
package com.oracle.graal.phases.verify;

import com.oracle.jvmci.meta.ResolvedJavaMethod;
import com.oracle.jvmci.meta.ResolvedJavaType;
import com.oracle.jvmci.meta.Signature;
import com.oracle.jvmci.meta.TrustedInterface;
import com.oracle.jvmci.meta.MetaAccessProvider;
import com.oracle.jvmci.meta.Kind;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * For certain types, object identity should not be used for object equality check. This phase
 * checks the correct usage of the given type. Equality checks with == or != (except null checks)
 * results in an {@link AssertionError}.
 *
 * Note that only {@link TrustedInterface}s can be verified.
 */
public class VerifyUsageWithEquals extends VerifyPhase<PhaseContext> {

    /**
     * The type of values that must not use identity for testing object equality.
     */
    private final Class<?> restrictedClass;

    public VerifyUsageWithEquals(Class<?> restrictedClass) {
        this.restrictedClass = restrictedClass;
        assert !restrictedClass.isInterface() || TrustedInterface.class.isAssignableFrom(restrictedClass);
    }

    /**
     * Determines whether the type of {@code node} is assignable to the {@link #restrictedClass}.
     */
    private boolean isAssignableToRestrictedType(ValueNode node, MetaAccessProvider metaAccess) {
        if (node.stamp() instanceof ObjectStamp) {
            ResolvedJavaType restrictedType = metaAccess.lookupJavaType(restrictedClass);
            ResolvedJavaType nodeType = StampTool.typeOrNull(node);

            if (nodeType != null && restrictedType.isAssignableFrom(nodeType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNullConstant(ValueNode node) {
        return node.isConstant() && node.isNullConstant();
    }

    private static boolean isEqualsMethod(ResolvedJavaMethod method) {
        if (method.getName().equals("equals")) {
            Signature sig = method.getSignature();
            if (sig.getReturnKind() == Kind.Boolean) {
                if (sig.getParameterCount(false) == 1) {
                    ResolvedJavaType ptype = (ResolvedJavaType) sig.getParameterType(0, method.getDeclaringClass());
                    if (ptype.isJavaLangObject()) {
                        return true;
                    }

                }
            }
        }
        return false;
    }

    private static boolean isThisParameter(ValueNode node) {
        return node instanceof ParameterNode && ((ParameterNode) node).index() == 0;
    }

    /**
     * Checks whether the type of {@code x} is assignable to the restricted type and that {@code y}
     * is not a null constant.
     */
    private boolean isIllegalUsage(ResolvedJavaMethod method, ValueNode x, ValueNode y, MetaAccessProvider metaAccess) {
        if (isAssignableToRestrictedType(x, metaAccess) && !isNullConstant(y)) {
            if (isEqualsMethod(method) && isThisParameter(x) || isThisParameter(y)) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        for (ObjectEqualsNode cn : graph.getNodes().filter(ObjectEqualsNode.class)) {
            // bail out if we compare an object of type klass with == or != (except null checks)
            ResolvedJavaMethod method = graph.method();
            ResolvedJavaType restrictedType = context.getMetaAccess().lookupJavaType(restrictedClass);

            if (method.getDeclaringClass().equals(restrictedType)) {
                // Allow violation in methods of the restricted type itself.
            } else if (isIllegalUsage(method, cn.getX(), cn.getY(), context.getMetaAccess()) || isIllegalUsage(method, cn.getY(), cn.getX(), context.getMetaAccess())) {
                throw new VerificationError("Verification of " + restrictedClass.getName() + " usage failed: Comparing " + cn.getX() + " and " + cn.getY() + " in " + method +
                                " must use .equals() for object equality, not '==' or '!='");
            }
        }
        return true;
    }
}
