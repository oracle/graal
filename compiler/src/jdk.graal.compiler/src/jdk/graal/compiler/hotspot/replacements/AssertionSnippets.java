/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.api.directives.GraalDirectives.SLOWPATH_PROBABILITY;
import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static jdk.graal.compiler.hotspot.stubs.StubUtil.VM_MESSAGE_C;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.nodes.StubForeignCallNode;
import jdk.graal.compiler.hotspot.nodes.StubStartNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.nodes.AssertionNode;
import jdk.graal.compiler.replacements.nodes.CStringConstant;
import jdk.graal.compiler.word.Word;

public class AssertionSnippets implements Snippets {

    @Snippet
    public static void assertion(boolean condition, Word message, long l1, long l2) {
        if (injectBranchProbability(SLOWPATH_PROBABILITY, !condition)) {
            vmMessageC(VM_MESSAGE_C, true, message, l1, l2, 0L);
        }
    }

    @Snippet
    public static void stubAssertion(boolean condition, Word message, long l1, long l2) {
        if (injectBranchProbability(SLOWPATH_PROBABILITY, !condition)) {
            vmMessageStub(VM_MESSAGE_C, true, message, l1, l2, 0L);
        }
    }

    @NodeIntrinsic(ForeignCallNode.class)
    static native void vmMessageC(@ConstantNodeParameter ForeignCallDescriptor stubPrintfC, boolean vmError, Word format, long v1, long v2, long v3);

    @NodeIntrinsic(StubForeignCallNode.class)
    static native void vmMessageStub(@ConstantNodeParameter ForeignCallDescriptor stubPrintfC, boolean vmError, Word format, long v1, long v2, long v3);

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo assertion;
        private final SnippetInfo stubAssertion;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);

            this.assertion = snippet(providers, AssertionSnippets.class, "assertion");
            this.stubAssertion = snippet(providers, AssertionSnippets.class, "stubAssertion");
        }

        public void lower(AssertionNode assertionNode, LoweringTool tool) {
            StructuredGraph graph = assertionNode.graph();
            Arguments args = new Arguments(graph.start() instanceof StubStartNode ? stubAssertion : assertion, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("condition", assertionNode.condition());
            args.add("message",
                            graph.unique(new ConstantNode(new CStringConstant("failed runtime assertion in snippet/stub: " + assertionNode.message() + " (" + graph.method() + ")"),
                                            StampFactory.pointer())));
            args.add("l1", assertionNode.getL1());
            args.add("l2", assertionNode.getL2());
            template(tool, assertionNode, args).instantiate(tool.getMetaAccess(), assertionNode, DEFAULT_REPLACER, args);
        }
    }
}
