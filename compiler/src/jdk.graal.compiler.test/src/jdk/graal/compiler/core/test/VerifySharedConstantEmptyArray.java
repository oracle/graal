/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.core.test;

import java.util.Set;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;

import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that if a shared 0-length array constant is available for some type (e.g.
 * {@link ValueNode#EMPTY_ARRAY}), then it is used everywhere such a value is needed.
 */
public class VerifySharedConstantEmptyArray extends VerifyStringFormatterUsage {

    /**
     * Names of static final fields in the Graal code base that defined shared 0-length arrays.
     */
    private static final Set<String> NAMES = CollectionsUtil.setOf(
                    "EMPTY_ARRAY",
                    "EMPTY_PATTERNS",
                    "NO_NODES");

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        for (NewArrayNode t : graph.getNodes().filter(NewArrayNode.class)) {
            checkNewArrayNode(graph, t);
        }
    }

    private static void checkNewArrayNode(StructuredGraph graph, NewArrayNode t) {
        ResolvedJavaMethod method = graph.method();
        if (t.length().isDefaultConstant()) {
            ResolvedJavaType elementType = t.elementType();
            if (!checkSharedConstantDefinition(t, method, elementType)) {
                for (ResolvedJavaField field : elementType.getStaticFields()) {
                    if (field.isFinal() && field.getType().isArray() && field.getType().getElementalType().equals(elementType) && NAMES.contains(field.getName())) {
                        if (isUsageVarargsParameter(t)) {
                            return;
                        }
                        throw new VerificationError("In %s use %s instead of `new %s[0]`%s", method.format("%H.%n(%p)"), field.format("%H.%n"), elementType.toJavaName(false), approxLocation(t));
                    }
                }
            }
        }
    }

    /**
     * Determines if {@code newZeroLengthArray} has a usage as a varargs parameter.
     */
    private static boolean isUsageVarargsParameter(NewArrayNode newZeroLengthArray) {
        for (Node usage : newZeroLengthArray.usages()) {
            if (usage instanceof MethodCallTargetNode) {
                MethodCallTargetNode target = (MethodCallTargetNode) usage;
                ResolvedJavaMethod m = target.targetMethod();
                if (m.isVarArgs()) {
                    NodeInputList<ValueNode> margs = target.arguments();
                    if (margs.last() == newZeroLengthArray) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if {@code newZeroLengthArray} in {@code method} is defining a shared constant and if
     * so, that its name is in {@link #NAMES}.
     *
     * @return {@code true} if it is a shared constant definition
     */
    private static boolean checkSharedConstantDefinition(NewArrayNode newZeroLengthArray, ResolvedJavaMethod method, ResolvedJavaType elementType) {
        if (method.getDeclaringClass().equals(elementType) && method.getName().equals("<clinit>")) {
            for (Node usage : newZeroLengthArray.usages()) {
                if (usage instanceof StoreFieldNode) {
                    StoreFieldNode store = (StoreFieldNode) usage;
                    ResolvedJavaField f = store.field();
                    if (f.isStatic() && f.isFinal() && store.value() == newZeroLengthArray) {
                        if (!NAMES.contains(f.getName())) {
                            throw new VerificationError("%s appears to be a shared 0-length array constant - rename to EMPTY_ARRAY or add its name to %s.NAMES%s", f.format("%H.%n"),
                                            VerifySharedConstantEmptyArray.class.getName(), approxLocation(newZeroLengthArray));
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String approxLocation(NewArrayNode n) {
        String loc = GraphUtil.approxSourceLocation(n);
        return loc == null ? "" : String.format(" [approx location: %s]", loc);
    }
}
