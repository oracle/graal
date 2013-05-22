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

public class VerifyUsageWithEquals extends VerifyPhase {

    private MetaAccessProvider runtime;
    private Class<?> klass;

    public VerifyUsageWithEquals(MetaAccessProvider runtime, Class<?> klass) {
        this.runtime = runtime;
        this.klass = klass;
    }

    private boolean isAssignableType(ValueNode node) {
        if (node.stamp() instanceof ObjectStamp) {
            ResolvedJavaType valueType = runtime.lookupJavaType(klass);
            ResolvedJavaType nodeType = node.objectStamp().type();

            if (valueType.isAssignableFrom(nodeType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNullConstant(ValueNode node) {
        return node.isConstant() && node.asConstant().isNull();
    }

    private boolean checkUsage(ValueNode x, ValueNode y) {
        return isAssignableType(x) && !isNullConstant(y);
    }

    private static boolean isEqualsMethod(StructuredGraph graph) {
        Signature signature = graph.method().getSignature();
        return graph.method().getName().equals("equals") && signature.getParameterCount(false) == 1 && signature.getParameterKind(0).equals(Kind.Object);
    }

    @Override
    protected boolean verify(StructuredGraph graph) {
        for (ObjectEqualsNode cn : graph.getNodes().filter(ObjectEqualsNode.class)) {
            if (!isEqualsMethod(graph)) {
                // bail out if we compare an object of type klass with == or != (except null checks)
                assert !(checkUsage(cn.x(), cn.y()) && checkUsage(cn.y(), cn.x())) : "VerifyUsage of " + klass.getName() + ": " + cn.x() + " or " + cn.y() + " in " + graph.method() +
                                " uses object identity. Should use equals() instead.";
            }
        }
        return true;
    }
}
