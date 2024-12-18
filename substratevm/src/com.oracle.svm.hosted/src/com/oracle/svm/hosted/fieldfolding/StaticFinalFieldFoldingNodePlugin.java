/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.fieldfolding;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Intercepts loads and stores of static final fields that are candidates for folding. For loads, it
 * inserts {@link jdk.graal.compiler.nodes.java.LoadFieldNode} followed by a
 * {@link jdk.graal.compiler.nodes.extended.StateSplitProxyNode}. Since an optimizable static final
 * field load may be replaced by a diamond structure (see {@link StaticFinalFieldFoldingPhase}), we
 * need to capture the frame state after the load because the diamond structure is a state split and
 * will need a proper frame state.
 * 
 * For stores, it inserts {@link MarkStaticFinalFieldInitializedNode} in addition to the
 * {@link jdk.graal.compiler.nodes.java.StoreFieldNode}.
 */
final class StaticFinalFieldFoldingNodePlugin implements NodePlugin {

    private final FieldValueInterceptionSupport fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        assert field.isStatic();
        if (!field.isFinal()) {
            return false;
        }

        if (!StaticFinalFieldFoldingFeature.isAllowedTargetMethod(b.getMethod())) {
            return false;
        }

        AnalysisField aField = StaticFinalFieldFoldingFeature.toAnalysisField(field);
        AnalysisMethod definingClassInitializer = aField.getDeclaringClass().getClassInitializer();
        if (!StaticFinalFieldFoldingFeature.isOptimizationCandidate(aField, definingClassInitializer, fieldValueInterceptionSupport)) {
            return false;
        }

        LoadFieldNode loadFieldNode = b.append(LoadFieldNode.create(b.getAssumptions(), null, field));
        StateSplitProxyNode readProxy = b.addPush(field.getJavaKind(), new StateSplitProxyNode(loadFieldNode));
        assert readProxy.stateAfter() != null;
        return true;
    }

    @Override
    public boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        assert field.isStatic();
        if (!field.isFinal()) {
            return false;
        }

        if (SubstrateCompilationDirectives.isDeoptTarget(b.getMethod())) {
            return false;
        }

        /*
         * We don't know at this time if the field is really going to be optimized. This is only
         * known after static analysis. This node then replaces itself with an array store.
         */
        b.add(new MarkStaticFinalFieldInitializedNode(field));

        /* Always emit the regular field store. It is necessary also for optimized fields. */
        return false;
    }
}
