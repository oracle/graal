/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.analysis.flow;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.flow.NewInstanceTypeFlow;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.graal.nodes.NewPinnedArrayNode;
import com.oracle.svm.core.graal.nodes.NewPinnedInstanceNode;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.substitute.ComputedValueField;

import jdk.vm.ci.meta.JavaKind;

public class SVMMethodTypeFlowBuilder extends MethodTypeFlowBuilder {

    public SVMMethodTypeFlowBuilder(BigBang bb, MethodTypeFlow methodFlow) {
        super(bb, methodFlow);
    }

    public SVMMethodTypeFlowBuilder(BigBang bb, StructuredGraph graph) {
        super(bb, graph);
    }

    @Override
    public void registerUsedElements() {
        super.registerUsedElements();

        for (Node n : graph.getNodes()) {
            if (n instanceof ConstantNode) {
                ConstantNode cn = (ConstantNode) n;
                if (cn.hasUsages() && cn.asJavaConstant().getJavaKind() == JavaKind.Object && cn.asJavaConstant().isNonNull()) {
                    /*
                     * Constants that are embedded into graphs via constant folding of static fields
                     * have already been replaced. But constants embedded manually by graph builder
                     * plugins, or class constants that come directly from constant bytecodes, are
                     * not replaced. We verify here that the object replacer would not replace such
                     * objects.
                     *
                     * But more importantly, some object replacers also perform actions like forcing
                     * eager initialization of fields. We need to make sure that these object
                     * replacers really see all objects that are embedded into compiled code.
                     */
                    Object value = SubstrateObjectConstant.asObject(cn.asJavaConstant());
                    Object replaced = bb.getUniverse().replaceObject(value);
                    if (value != replaced) {
                        throw GraalError.shouldNotReachHere("Missed object replacement during graph building: " +
                                        value + " (" + value.getClass() + ") != " + replaced + " (" + replaced.getClass() + ")");
                    }
                }
            }
        }
    }

    @Override
    protected NewInstanceTypeFlow createNewInstanceTypeFlow(NewInstanceNode node, AnalysisType type, BytecodeLocation allocationLabel) {
        if (node instanceof NewPinnedInstanceNode) {
            return new PinnedNewInstanceTypeFlow((Inflation) bb, node, type, allocationLabel);
        }
        return super.createNewInstanceTypeFlow(node, type, allocationLabel);
    }

    @Override
    protected NewInstanceTypeFlow createNewArrayTypeFlow(NewArrayNode node, AnalysisType type, BytecodeLocation allocationLabel) {
        if (node instanceof NewPinnedArrayNode) {
            return new PinnedNewInstanceTypeFlow((Inflation) bb, node, type, allocationLabel);
        }
        return super.createNewArrayTypeFlow(node, type, allocationLabel);
    }

    @SuppressWarnings("serial")
    public static class UnsafeOffsetError extends UserException {

        UnsafeOffsetError(String message) {
            super(message);
        }

        static void report(String message) {
            throw new UnsafeOffsetError(message);
        }
    }

    @Override
    protected void checkUnsafeOffset(ValueNode offsetNode) {
        if (!NativeImageOptions.ThrowUnsafeOffsetErrors.getValue()) {
            /* Skip the checks bellow. */
            return;
        }

        /*
         * Offset fields used in unsafe operations need value recomputation. Check that they are
         * properly intercepted. Detection of offset fields that need value re-computation is best
         * effort. For some node types (e.g., SignExtendNode, LoadFieldNode, InvokeNode, AddNode,
         * ParameterNode, AndNode, ValueProxyNode, LoadIndexedNode) that can be used as an offset in
         * an unsafe operation we cannot determine if the value was properly intercepted or not by
         * simply looking at the node itself.
         *
         * Determining if an offset that comes from a ConstantNode was properly intercepted is not
         * reliable . First the canonicalization in UnsafeAccessNode tries to replace the unsafe
         * access with a field access when the offset is a constant. Thus, we don't actually see the
         * unsafe access node. Second if the field is intercepted but RecomputeFieldValue.isFinal is
         * set to true then the recomputed value is constant folded, i.e., there is no load of the
         * offset field. Then we cannot determine if the offset was recomputed or not and attempting
         * this check can lead to false positive errors.
         *
         * The only offset node type that we are left with and that we can unequivocally determine
         * if it was properly intercepted or not is LoadFieldNode.
         */

        if (offsetNode instanceof LoadFieldNode) {
            LoadFieldNode offsetLoadNode = (LoadFieldNode) offsetNode;
            AnalysisField field = (AnalysisField) offsetLoadNode.field();
            if (!(field.wrapped instanceof ComputedValueField)) {
                UnsafeOffsetError.report("Field " + field + " is used as an offset in an unsafe operation, but no value recomputation found. \n Wrapped field: " + field.wrapped);
            }
        } else if (NativeImageOptions.ReportUnsafeOffsetWarnings.getValue()) {

            String message = "Offset used in an unsafe operation. Cannot determine if the offset value is recomputed.";
            message += "Location: " + offsetNode.getNodeSourcePosition() + "\n";
            message += "Node class: " + offsetNode.getClass().getName() + "\n";
            if (NativeImageOptions.UnsafeOffsetWarningsAreFatal.getValue()) {
                UnsafeOffsetError.report(message);
            } else {
                System.out.println(message);
            }
        }

    }
}
