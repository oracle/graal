/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.LoadFieldNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisGraphDecoder;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.fieldfolding.IsStaticFinalFieldInitializedNode;

public class InlineBeforeAnalysisGraphDecoderImpl extends InlineBeforeAnalysisGraphDecoder {

    private final SimulateClassInitializerSupport simulateClassInitializerSupport = SimulateClassInitializerSupport.singleton();

    public InlineBeforeAnalysisGraphDecoderImpl(BigBang bb, InlineBeforeAnalysisPolicy policy, StructuredGraph graph, HostedProviders providers) {
        super(bb, policy, graph, providers, null);
    }

    @Override
    protected Node doCanonicalizeFixedNode(InlineBeforeAnalysisMethodScope methodScope, LoopScope loopScope, Node initialNode) {
        Node node = super.doCanonicalizeFixedNode(methodScope, loopScope, initialNode);

        if (node instanceof EnsureClassInitializedNode ensureClassInitializedNode) {
            node = handleEnsureClassInitializedNode(ensureClassInitializedNode);
        } else if (node instanceof LoadFieldNode loadFieldNode) {
            node = handleLoadFieldNode(loadFieldNode);
        } else if (node instanceof IsStaticFinalFieldInitializedNode isStaticFinalFieldInitializedNode) {
            node = handleIsStaticFinalFieldInitializedNode(isStaticFinalFieldInitializedNode);
        }
        return node;
    }

    private Node handleEnsureClassInitializedNode(EnsureClassInitializedNode node) {
        AnalysisType type = (AnalysisType) node.constantTypeOrNull(bb.getConstantReflectionProvider());
        if (type != null) {
            if (type.isReachable()) {
                /*
                 * The class initializer is always analyzed for reachable types so that the
                 * DynamicHub can be properly initialized. Avoid starting a second concurrent
                 * analysis.
                 */
                type.getInitializeMetaDataTask().ensureDone();
            } else {
                /*
                 * Even for types that are not yet reachable, we can analyze the class initializer.
                 * If the type gets reachable later, the analysis results are re-used. Or the type
                 * can remain unreachable throughout the whole analysis, because the Graal IR we are
                 * decoding here could actually be dead code that is removed before building the
                 * type flow graph.
                 */
                simulateClassInitializerSupport.trySimulateClassInitializer(bb, type);
            }
            if (simulateClassInitializerSupport.isClassInitializerSimulated(type)) {
                return null;
            }
        }
        return node;
    }

    private Node handleLoadFieldNode(LoadFieldNode node) {
        ConstantNode canonicalized = simulateClassInitializerSupport.tryCanonicalize(bb, node);
        if (canonicalized != null) {
            return canonicalized;
        }
        return node;
    }

    private Node handleIsStaticFinalFieldInitializedNode(IsStaticFinalFieldInitializedNode node) {
        var field = (AnalysisField) node.getField();
        if (simulateClassInitializerSupport.isClassInitializerSimulated(field.getDeclaringClass())) {
            return ConstantNode.forBoolean(true);
        }
        return node;
    }
}
