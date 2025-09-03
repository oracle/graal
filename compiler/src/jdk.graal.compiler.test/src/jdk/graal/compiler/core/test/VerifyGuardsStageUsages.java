/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verify that {@link GuardsStage} enums are encapsulated.
 */
public class VerifyGuardsStageUsages extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        ResolvedJavaMethod caller = graph.method();

        if (caller.getDeclaringClass().equals(metaAccess.lookupJavaType(GraphState.class))) {
            return;
        }
        if (caller.getDeclaringClass().equals(metaAccess.lookupJavaType(GraphState.GuardsStage.class))) {
            return;
        }

        String callerName = caller.format("%H.%n");

        switch (callerName) {
            case "jdk.graal.compiler.phases.common.GuardLoweringPhase.updateGraphState":
            case "jdk.graal.compiler.replacements.arraycopy.ArrayCopySnippets.delayedCheckcastArraycopySnippet":
            case "jdk.graal.compiler.replacements.arraycopy.ArrayCopySnippets.delayedExactArraycopyWithExpandedLoopSnippet":
            case "jdk.graal.compiler.replacements.arraycopy.ArrayCopySnippets.delayedGenericArraycopySnippet":
            case "jdk.graal.compiler.replacements.arraycopy.ArrayCopySnippets.checkTypesAndLimits":
                // Exempted cases
                return;
            default:
        }

        final ResolvedJavaType typeGuardsStage = metaAccess.lookupJavaType(GraphState.GuardsStage.class);

        for (LoadFieldNode t : graph.getNodes().filter(LoadFieldNode.class)) {
            if (typeGuardsStage.equals(t.field().getDeclaringClass())) {
                throw new VerificationError("Access to %s fields at callsite %s is prohibited. Use API methods provided in %s.",
                                typeGuardsStage.toJavaName(true),
                                caller.format("%H.%n(%p)"),
                                typeGuardsStage.toJavaName(true));
            }
        }
    }
}
