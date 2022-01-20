/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.amd64;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOfWithMaskNode;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayRegionEqualsWithMaskNode;
import org.graalvm.compiler.replacements.amd64.AMD64TruffleArrayUtilsWithMaskSnippets;
import org.graalvm.compiler.replacements.nodes.BitScanForwardNode;
import org.graalvm.compiler.replacements.nodes.BitScanReverseNode;
import org.graalvm.compiler.replacements.nodes.CountLeadingZerosNode;
import org.graalvm.compiler.replacements.nodes.CountTrailingZerosNode;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.JavaKind;

public interface AMD64LoweringProviderMixin extends LoweringProvider {

    @Override
    default Integer smallestCompareWidth() {
        return 8;
    }

    @Override
    default boolean supportsBulkZeroing() {
        return true;
    }

    @Override
    default boolean writesStronglyOrdered() {
        /*
         * While AMD64 supports non-temporal stores, these are not used by Graal for Java code.
         */
        return true;
    }

    /**
     * Performs AMD64-specific lowerings. Returns {@code true} if the given Node {@code n} was
     * lowered, {@code false} otherwise.
     */
    default boolean lowerAMD64(Node n, LoweringTool tool) {
        if (n instanceof AMD64ArrayIndexOfWithMaskNode) {
            tool.getReplacements().getSnippetTemplateCache(AMD64TruffleArrayUtilsWithMaskSnippets.Templates.class).lower((AMD64ArrayIndexOfWithMaskNode) n);
            return true;
        }

        if (n instanceof AMD64ArrayRegionEqualsWithMaskNode) {
            tool.getReplacements().getSnippetTemplateCache(AMD64TruffleArrayUtilsWithMaskSnippets.Templates.class).lower((AMD64ArrayRegionEqualsWithMaskNode) n);
            return true;
        }

        if (n instanceof CountLeadingZerosNode) {
            AMD64 arch = (AMD64) getTarget().arch;
            CountLeadingZerosNode count = (CountLeadingZerosNode) n;
            if (!arch.getFeatures().contains(AMD64.CPUFeature.LZCNT) || !arch.getFlags().contains(AMD64.Flag.UseCountLeadingZerosInstruction)) {
                StructuredGraph graph = count.graph();
                JavaKind kind = count.getValue().getStackKind();
                ValueNode zero = ConstantNode.forIntegerKind(kind, 0, graph);
                LogicNode compare = IntegerEqualsNode.create(count.getValue(), zero, NodeView.DEFAULT);
                ValueNode result = new SubNode(ConstantNode.forIntegerKind(JavaKind.Int, kind.getBitCount() - 1), new BitScanReverseNode(count.getValue()));
                ValueNode conditional = ConditionalNode.create(compare, ConstantNode.forInt(kind.getBitCount()), result, NodeView.DEFAULT);
                graph.addOrUniqueWithInputs(conditional);
                count.replaceAndDelete(conditional);
                return true;
            }
        }

        if (n instanceof CountTrailingZerosNode) {
            AMD64 arch = (AMD64) getTarget().arch;
            CountTrailingZerosNode count = (CountTrailingZerosNode) n;
            if (!arch.getFeatures().contains(AMD64.CPUFeature.BMI1) || !arch.getFlags().contains(AMD64.Flag.UseCountTrailingZerosInstruction)) {
                StructuredGraph graph = count.graph();
                JavaKind kind = count.getValue().getStackKind();
                ValueNode zero = ConstantNode.forIntegerKind(kind, 0, graph);
                LogicNode compare = IntegerEqualsNode.create(count.getValue(), zero, NodeView.DEFAULT);
                ValueNode conditional = ConditionalNode.create(compare, ConstantNode.forInt(kind.getBitCount()), new BitScanForwardNode(count.getValue()), NodeView.DEFAULT);
                graph.addOrUniqueWithInputs(conditional);
                count.replaceAndDelete(conditional);
                return true;
            }
        }

        return false;
    }
}
