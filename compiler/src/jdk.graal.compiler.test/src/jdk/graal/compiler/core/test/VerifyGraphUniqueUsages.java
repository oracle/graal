/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * {@link Graph#unique(T node)} and related functions only add {@Code node} to the graph if there is
 * no GVN equivalent node in the graph yet. Thus, the return value of {@link Graph#unique} must
 * never be ignored.
 */
@SuppressWarnings("javadoc")
public class VerifyGraphUniqueUsages extends VerifyPhase<CoreProviders> {

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        final ResolvedJavaType graphType = metaAccess.lookupJavaType(Graph.class);

        for (MethodCallTargetNode m : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = m.targetMethod();
            if (callee.getDeclaringClass().equals(graphType) && isMethodToCheck(callee)) {
                if (!hasUsage(m)) {
                    throw new VerificationError(m, "Calls to %s should always use its return value because it may be different from the argument.", m.targetMethod());
                }
            }
        }
    }

    private static boolean isMethodToCheck(ResolvedJavaMethod method) {
        return method.getName().equals("unique") || method.getName().equals("addOrUnique") || method.getName().equals("addOrUniqueWithInputs");
    }

    /**
     * Checks that the return value is not ignored.
     *
     * <pre>
     * {@code x = ...;
     * graph.unique(x); // wrong!
     * }
     * </pre>
     */
    private static boolean hasUsage(MethodCallTargetNode m) {
        GraalError.guarantee(m.usages().count() == 1, "%s should have exactly one usage.", m);
        Invoke invoke = m.invoke();
        if (invoke.asNode().usages().count() == 1 && invoke.stateAfter().equals(invoke.asNode().usages().first())) {
            // invoke has no usages except its state --> return value is not used
            return false;
        }
        return true;
    }
}
