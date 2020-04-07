/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets.aarch64;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;

import com.oracle.svm.core.graal.snippets.ArithmeticSnippets;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;

import jdk.vm.ci.meta.JavaKind;

/**
 * AArch64 does not have a remainder operation. We use <code>n % d == n - Truncate(n / d) * d</code>
 * for it instead. This is not correct for some edge cases, so we have to fix it up using these
 * snippets.
 */
final class AArch64ArithmeticSnippets extends ArithmeticSnippets {
    @Snippet
    protected static float fremSnippet(float x, float y) {
        // JVMS: If either value1' or value2' is NaN, the result is NaN.
        // JVMS: If the dividend is an infinity or the divisor is a zero or both, the result is NaN.
        if (Float.isInfinite(x) || y == 0.0f || Float.isNaN(y)) {
            return Float.NaN;
        }
        // JVMS: If the dividend is finite and the divisor is an infinity, the result equals the
        // dividend.
        // JVMS: If the dividend is a zero and the divisor is finite, the result equals the
        // dividend.
        if (x == 0.0f || Float.isInfinite(y)) {
            return x;
        }

        float result = safeRem(x, y);

        // JVMS: If neither value1' nor value2' is NaN, the sign of the result equals the sign of
        // the dividend.
        if (result == 0.0f && x < 0.0f) {
            return -result;
        }
        return result;
    }

    @Snippet
    protected static double dremSnippet(double x, double y) {
        // JVMS: If either value1' or value2' is NaN, the result is NaN.
        // JVMS: If the dividend is an infinity or the divisor is a zero or both, the result is NaN.
        if (Double.isInfinite(x) || y == 0.0 || Double.isNaN(y)) {
            return Double.NaN;
        }
        // JVMS: If the dividend is finite and the divisor is an infinity, the result equals the
        // dividend.
        // JVMS: If the dividend is a zero and the divisor is finite, the result equals the
        // dividend.
        if (x == 0.0 || Double.isInfinite(y)) {
            return x;
        }

        double result = safeRem(x, y);

        // JVMS: If neither value1' nor value2' is NaN, the sign of the result equals the sign of
        // the dividend.
        if (result == 0.0 && x < 0.0) {
            return -result;
        }
        return result;
    }

    @NodeIntrinsic(SafeFloatRemNode.class)
    private static native float safeRem(float x, float y);

    @NodeIntrinsic(SafeFloatRemNode.class)
    private static native double safeRem(double x, double y);

    private final SnippetInfo drem;
    private final SnippetInfo frem;

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        new AArch64ArithmeticSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    private AArch64ArithmeticSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        super(options, factories, providers, snippetReflection, lowerings);
        frem = snippet(AArch64ArithmeticSnippets.class, "fremSnippet");
        drem = snippet(AArch64ArithmeticSnippets.class, "dremSnippet");

        lowerings.put(RemNode.class, new AArch64RemLowering());
        lowerings.put(SafeFloatRemNode.class, new IdentityLowering());
    }

    protected class AArch64RemLowering implements NodeLoweringProvider<RemNode> {
        @Override
        public void lower(RemNode node, LoweringTool tool) {
            JavaKind kind = node.stamp(NodeView.DEFAULT).getStackKind();
            assert kind == JavaKind.Float || kind == JavaKind.Double;
            SnippetTemplate.SnippetInfo snippet = kind == JavaKind.Float ? frem : drem;
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("x", node.getX());
            args.add("y", node.getY());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, tool, args);
        }
    }
}

@NodeInfo
class SafeFloatRemNode extends RemNode {
    public static final NodeClass<SafeFloatRemNode> TYPE = NodeClass.create(SafeFloatRemNode.class);

    protected SafeFloatRemNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y);
    }
}
