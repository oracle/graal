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
package com.oracle.graal.truffle.phases;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.truffle.*;

/**
 * Inline all trivial getters (i.e. simple field loads).
 */
public class InlineTrivialGettersPhase extends BasePhase<PhaseContext> {

    private static final int TRIVIAL_GETTER_SIZE = 5;
    private final MetaAccessProvider metaAccessProvider;
    private final CanonicalizerPhase canonicalizer;

    public InlineTrivialGettersPhase(MetaAccessProvider metaAccessProvider, CanonicalizerPhase canonicalizer) {
        this.metaAccessProvider = metaAccessProvider;
        this.canonicalizer = canonicalizer;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        for (MethodCallTargetNode methodCallTarget : graph.getNodes(MethodCallTargetNode.class)) {
            if (methodCallTarget.isAlive()) {
                InvokeKind invokeKind = methodCallTarget.invokeKind();
                if (invokeKind == InvokeKind.Special) {
                    ResolvedJavaMethod targetMethod = methodCallTarget.targetMethod();
                    if (methodCallTarget.receiver().isConstant() && !methodCallTarget.receiver().isNullConstant()) {
                        if (targetMethod.getCodeSize() == TRIVIAL_GETTER_SIZE && targetMethod.getDeclaringClass().isInitialized() && targetMethod.getName().startsWith("get")) {
                            StructuredGraph inlineGraph = new StructuredGraph(targetMethod);
                            new GraphBuilderPhase(metaAccessProvider, GraphBuilderConfiguration.getDefault(), TruffleCompilerImpl.Optimizations).apply(inlineGraph);
                            int mark = graph.getMark();
                            InliningUtil.inline(methodCallTarget.invoke(), inlineGraph, false);
                            Debug.dump(graph, "After inlining trivial getter %s", targetMethod.toString());
                            canonicalizer.applyIncremental(graph, context, mark);
                        }
                    }
                }
            }
        }
    }
}
