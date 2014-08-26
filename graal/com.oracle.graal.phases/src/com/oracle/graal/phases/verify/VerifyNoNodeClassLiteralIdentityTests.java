/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * Since only {@linkplain GeneratedNode generated} {@link Node} types can be instantiated (which is
 * checked by an assertion in {@link Node#Node()}), any identity test of a node's
 * {@linkplain Object#getClass() class} against a class literal of a non-generated node types will
 * always return false. Instead, a static {@code getGenClass()} helper method should be used for
 * such identity tests. For example, instead of:
 *
 * <pre>
 *     if (operation.getClass() == IntegerAddNode.class) { ... }
 * </pre>
 *
 * this should be used:
 *
 * <pre>
 *     if (operation.getClass() == IntegerAddNode.getGenClass()) { ... }
 * </pre>
 *
 * This phase verifies there are no identity tests against class literals for non-generated Node
 * types.
 */
public class VerifyNoNodeClassLiteralIdentityTests extends VerifyPhase<PhaseContext> {

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        Map<String, String> errors = new HashMap<>();

        for (ConstantNode c : ConstantNode.getConstantNodes(graph)) {
            ResolvedJavaType nodeClassType = context.getMetaAccess().lookupJavaType(Node.class);
            ResolvedJavaType nodeType = context.getConstantReflection().asJavaType(c.asConstant());
            if (nodeType != null && nodeClassType.isAssignableFrom(nodeType)) {
                NodeIterable<Node> usages = c.usages();
                for (Node n : usages) {
                    if (!(n instanceof ObjectEqualsNode)) {
                        continue;
                    }
                    String loc = GraphUtil.approxSourceLocation(n);
                    if (loc == null) {
                        loc = graph.method().asStackTraceElement(0).toString() + "  " + n;
                    }
                    errors.put(nodeType.toJavaName(false), loc);
                }
            }
        }
        if (errors.isEmpty()) {
            return true;
        }
        Formatter f = new Formatter();
        boolean first = true;
        for (Map.Entry<String, String> e : errors.entrySet()) {
            if (!first) {
                f.format("%n");
            } else {
                first = false;
            }
            f.format("Found illegal use of Node class literal %s near:%n    %s", e.getKey(), e.getValue());
        }
        throw new VerificationError(f.toString());
    }
}
