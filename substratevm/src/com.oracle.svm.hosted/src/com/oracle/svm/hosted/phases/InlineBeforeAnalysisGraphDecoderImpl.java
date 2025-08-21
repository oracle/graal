/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisGraphDecoder;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.fieldfolding.IsStaticFinalFieldInitializedNode;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.replacements.nodes.ResolvedMethodHandleCallTargetNode;

public class InlineBeforeAnalysisGraphDecoderImpl extends InlineBeforeAnalysisGraphDecoder {

    private final SimulateClassInitializerSupport simulateClassInitializerSupport = SimulateClassInitializerSupport.singleton();
    private final FieldValueInterceptionSupport fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();

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
            processClassInitializer(type);
            if (simulateClassInitializerSupport.isSimulatedOrInitializedAtBuildTime(type) && !ClassInitializationSupport.singleton().requiresInitializationNodeForTypeReached(type)) {
                return null;
            }
        }
        return node;
    }

    private void processClassInitializer(AnalysisType type) {
        if (type.isReachable()) {
            /*
             * The class initializer is always analyzed for reachable types so that the DynamicHub
             * can be properly initialized. Since, the simulation might already be in the progress
             * on another thread, we use ensureDone to avoid starting a second concurrent analysis.
             */
            type.getInitializeMetaDataTask().ensureDone();
        } else {
            /*
             * Even for types that are not yet reachable, we can analyze the class initializer. If
             * the type gets reachable later, the analysis results are re-used. Or the type can
             * remain unreachable throughout the whole analysis, because the Graal IR we are
             * decoding here could actually be dead code that is removed before building the type
             * flow graph.
             */
            simulateClassInitializerSupport.trySimulateClassInitializer(bb, type);
        }
    }

    private Node handleLoadFieldNode(LoadFieldNode node) {
        var field = (AnalysisField) node.field();
        if (field.isStatic()) {
            /*
             * First, make sure the results of the simulation of the given class initializer are
             * available, compute them if necessary.
             */
            processClassInitializer(field.getDeclaringClass());
        }
        ConstantNode canonicalized = simulateClassInitializerSupport.tryCanonicalize(bb, node);
        if (canonicalized != null) {
            return canonicalized;
        }
        var intrinsified = fieldValueInterceptionSupport.tryIntrinsifyFieldLoad(providers, node);
        if (intrinsified != null) {
            return intrinsified;
        }
        return node;
    }

    private Node handleIsStaticFinalFieldInitializedNode(IsStaticFinalFieldInitializedNode node) {
        var field = (AnalysisField) node.getField();
        if (simulateClassInitializerSupport.isSimulatedOrInitializedAtBuildTime(field.getDeclaringClass())) {
            return ConstantNode.forBoolean(true);
        }
        return node;
    }

    @Override
    protected MethodCallTargetNode createCallTargetNode(ResolvedMethodHandleCallTargetNode t) {
        return new SubstrateMethodCallTargetNode(t.invokeKind(), t.targetMethod(), t.arguments().toArray(ValueNode.EMPTY_ARRAY), t.returnStamp(), t.getTypeProfile());
    }
}
