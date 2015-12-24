/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.replacements.aarch64;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Rem;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.gen.ArithmeticLIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.BinaryArithmeticNode;
import com.oracle.graal.nodes.calc.RemNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.replacements.Snippet;
import com.oracle.graal.replacements.SnippetTemplate;
import com.oracle.graal.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;

/**
 * AArch64 does not have a remainder operation. We use <code>n % d == n - Truncate(n / d) * d</code>
 * for it instead. This is not correct for some edge cases, so we have to fix it up using these
 * snippets.
 */
public class AArch64FloatArithmeticSnippets extends SnippetTemplate.AbstractTemplates implements Snippets {

    private final SnippetTemplate.SnippetInfo drem;
    private final SnippetTemplate.SnippetInfo frem;

    public AArch64FloatArithmeticSnippets(Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
        super(providers, snippetReflection, target);
        drem = snippet(AArch64FloatArithmeticSnippets.class, "dremSnippet");
        frem = snippet(AArch64FloatArithmeticSnippets.class, "fremSnippet");
    }

    public void lower(RemNode node, LoweringTool tool) {
        // assert node.kind() == JavaKind.Float || node.kind() == JavaKind.Double;
        // if (node instanceof SafeNode) {
        // // We already introduced the necessary checks, nothing to do.
        // return;
        // }
        // SnippetTemplate.SnippetInfo snippet = node.kind() == Kind.Double ? drem : frem;
        // SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet,
        // node.graph().getGuardsStage());
        // args.add("x", node.x());
        // args.add("y", node.y());
        // args.add("isStrictFP", node.isStrictFP());
        // template(args).instantiate(providers.getMetaAccess(), node,
        // SnippetTemplate.DEFAULT_REPLACER,
        // tool, args);
        throw JVMCIError.unimplemented(node + ", " + tool);
    }

    @Snippet
    public static double dremSnippet(double x, double y, @Snippet.ConstantParameter boolean isStrictFP) {
        if (Double.isInfinite(x) || y == 0.0 || Double.isNaN(y)) {
            return Double.NaN;
        }
        // -0.0 % 5.0 will result in 0.0 and not -0.0 if we don't check here.
        if (Double.isInfinite(y) || x == 0.0) {
            return x;
        }
        return safeRem(JavaKind.Double, x, y, isStrictFP);
    }

    @Snippet
    public static float fremSnippet(float x, float y, @Snippet.ConstantParameter boolean isStrictFP) {
        if (Float.isInfinite(x) || y == 0.0f || Float.isNaN(y)) {
            return Float.NaN;
        }
        // -0.0 % 5.0 will result in 0.0 and not -0.0 if we don't check here.
        if (Float.isInfinite(y) || x == 0.0f) {
            return x;
        }
        return safeRem(JavaKind.Float, x, y, isStrictFP);
    }

    @NodeIntrinsic(SafeFloatRemNode.class)
    private static native double safeRem(@Node.ConstantNodeParameter JavaKind kind, double x, double y, @Node.ConstantNodeParameter boolean isStrictFP);

    @NodeIntrinsic(SafeFloatRemNode.class)
    private static native float safeRem(@Node.ConstantNodeParameter JavaKind kind, float x, float y, @Node.ConstantNodeParameter boolean isStrictFP);

    // Marker interface to distinguish untreated nodes from ones where we have installed the
    // additional checks
    private interface SafeNode {
    }

    @NodeInfo
    // static class SafeFloatRemNode extends FloatRemNode implements SafeNode {
    static class SafeFloatRemNode extends BinaryArithmeticNode<Rem> implements SafeNode {

        public static final NodeClass<SafeFloatRemNode> TYPE = NodeClass.create(SafeFloatRemNode.class);

        @SuppressWarnings("unused")
        public SafeFloatRemNode(JavaKind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
            super(TYPE, ArithmeticOpTable::getRem, x, y);
        }

        public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
            throw JVMCIError.unimplemented();
        }

        public void generate(NodeLIRBuilderTool generator) {
            throw JVMCIError.unimplemented();
        }

        public Node canonical(CanonicalizerTool tool) {
            throw JVMCIError.unimplemented();
        }
    }

}
