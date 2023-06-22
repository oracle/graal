/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.type.StampTool;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ameta.ReadableJavaField;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Performs the constant folding of fields that are optimizable.
 */
final class StaticFinalFieldFoldingNodePlugin implements NodePlugin {

    private final StaticFinalFieldFoldingFeature feature;

    StaticFinalFieldFoldingNodePlugin(StaticFinalFieldFoldingFeature feature) {
        this.feature = feature;
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        assert field.isStatic();
        if (!field.isFinal()) {
            return false;
        }

        if (b.getMethod().isClassInitializer()) {
            /*
             * Cannot optimize static field loads in class initializers because that can lead to
             * deadlocks when classes have cyclic dependencies.
             */
            return false;
        }

        AnalysisField aField = StaticFinalFieldFoldingFeature.toAnalysisField(field);
        AnalysisMethod classInitializer = aField.getDeclaringClass().getClassInitializer();
        if (classInitializer == null) {
            /* If there is no class initializer, there cannot be a foldable constant found in it. */
            return false;
        }

        if (aField.wrapped instanceof ReadableJavaField && !((ReadableJavaField) aField.wrapped).isValueAvailable()) {
            /*
             * Cannot optimize static field whose value is recomputed and is not yet available,
             * i.e., it may depend on analysis/compilation derived data.
             */
            return false;
        }

        /*
         * The foldable field values are collected during parsing of the class initializer. If the
         * class initializer is not parsed yet, parsing needs to be forced so that {@link
         * StaticFinalFieldFoldingFeature#onAnalysisMethodParsed} determines which fields can be
         * optimized.
         */
        classInitializer.ensureGraphParsed(feature.bb);

        JavaConstant initializedValue = feature.foldedFieldValues.get(aField);
        if (initializedValue == null) {
            /* Field cannot be optimized. */
            return false;
        }

        /*
         * Create a if-else structure with a PhiNode that either has the optimized value of the
         * field, or the uninitialized value. The initialization status array and the index into
         * that array are not known yet during bytecode parsing, so the array access will be created
         * lazily.
         */
        ValueNode fieldCheckStatusNode = b.add(new IsStaticFinalFieldInitializedNode(field));
        LogicNode isUninitializedNode = b.add(IntegerEqualsNode.create(fieldCheckStatusNode, ConstantNode.forBoolean(false), NodeView.DEFAULT));

        JavaConstant uninitializedValue = aField.getConstantValue();
        if (uninitializedValue == null) {
            uninitializedValue = JavaConstant.defaultForKind(aField.getStorageKind());
        }
        ConstantNode uninitializedValueNode = ConstantNode.forConstant(uninitializedValue, b.getMetaAccess());
        ConstantNode initializedValueNode = ConstantNode.forConstant(initializedValue, b.getMetaAccess());

        EndNode uninitializedEndNode = b.getGraph().add(new EndNode());
        EndNode initializedEndNode = b.getGraph().add(new EndNode());
        b.add(new IfNode(isUninitializedNode, uninitializedEndNode, initializedEndNode, BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROFILE));

        MergeNode merge = b.append(new MergeNode());
        merge.addForwardEnd(uninitializedEndNode);
        merge.addForwardEnd(initializedEndNode);

        ConstantNode[] phiValueNodes = {uninitializedValueNode, initializedValueNode};
        ValuePhiNode phi = new ValuePhiNode(StampTool.meet(Arrays.asList(phiValueNodes)), merge, phiValueNodes);
        b.addPush(field.getJavaKind(), phi);
        b.setStateAfter(merge);

        return true;
    }

    @Override
    public boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        assert field.isStatic();
        if (!field.isFinal()) {
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
