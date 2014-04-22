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
package com.oracle.graal.phases.verify;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * For certain types object identity should not be used for object equality check. This phase checks
 * the correct usage of the given type. Equality checks with == or != (except null checks) results
 * in an {@link AssertionError}.
 */
public class VerifyUsageWithEquals extends VerifyPhase<PhaseContext> {

    private final Class<?> klass;

    public VerifyUsageWithEquals(Class<?> klass) {
        this.klass = klass;
    }

    private boolean isAssignableType(ValueNode node, MetaAccessProvider metaAccess) {
        if (node.stamp() instanceof ObjectStamp) {
            ResolvedJavaType valueType = metaAccess.lookupJavaType(klass);
            ResolvedJavaType nodeType = StampTool.typeOrNull(node);

            if (nodeType != null && valueType.isAssignableFrom(nodeType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNullConstant(ValueNode node) {
        return node.isConstant() && node.asConstant().isNull();
    }

    private boolean checkUsage(ValueNode x, ValueNode y, MetaAccessProvider metaAccess) {
        return isAssignableType(x, metaAccess) && !isNullConstant(y);
    }

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        for (ObjectEqualsNode cn : graph.getNodes().filter(ObjectEqualsNode.class)) {
            // bail out if we compare an object of type klass with == or != (except null checks)
            if (checkUsage(cn.x(), cn.y(), context.getMetaAccess()) && checkUsage(cn.y(), cn.x(), context.getMetaAccess())) {
                throw new VerificationError("Verification of " + klass.getName() + " usage failed: Comparing " + cn.x() + " and " + cn.y() + " in " + graph.method() +
                                " must use .equals() for object equality, not '==' or '!='");
            }
        }
        return true;
    }
}
