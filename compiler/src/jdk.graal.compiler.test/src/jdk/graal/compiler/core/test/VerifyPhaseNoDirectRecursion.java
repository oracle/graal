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

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.RecursivePhase;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verify that no subclass of {@link BasePhase} uses direct recursion in its implementation. Direct
 * recursion can easily cause stack overflows, endless recursions, etc. The graal optimizer code
 * base must use iteration instead.
 */
public class VerifyPhaseNoDirectRecursion extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        final ResolvedJavaType basePhaseType = context.getMetaAccess().lookupJavaType(BasePhase.class);
        final ResolvedJavaType recursivePhaseType = context.getMetaAccess().lookupJavaType(RecursivePhase.class);
        final ResolvedJavaMethod graphMethod = graph.method();
        final ResolvedJavaType graphMethodType = graph.method().getDeclaringClass();

        if (graphMethod.getDeclaringClass().getName().contains("truffle")) {
            // We only enforce this code requirement for core compiler code and ignore truffle.
            return;
        }
        if (!basePhaseType.isAssignableFrom(graphMethodType)) {
            // Not a compiler phase.
            return;
        }
        if (recursivePhaseType.isAssignableFrom(graphMethodType)) {
            // Some exclude listed phases.
            return;
        }
        for (MethodCallTargetNode m : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = m.targetMethod();
            if (callee.equals(graphMethod)) {
                throw new VerificationError(
                                "Call %s in %s is a self recursive call in a phase subclass. This is prohibited for performance and safety reasons. Replace with iteration. " +
                                                "(Exceptions can be made for very stable well understood code, consider whitlisting in VerifyPhaseNoDirectRecursion.java).",
                                m.invoke(), m.invoke().asNode().getNodeSourcePosition());
            }
        }
    }
}
