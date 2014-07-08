/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.nodes.CStringNode.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

public class AssertionSnippets implements Snippets {

    /**
     * This call can only be used with true for the "vmError" parameter, so that it can be
     * configured to be a leaf method.
     */
    public static final ForeignCallDescriptor ASSERTION_VM_MESSAGE_C = new ForeignCallDescriptor("assertionVmMessageC", void.class, boolean.class, Word.class, long.class, long.class, long.class);

    @Snippet
    public static void assertion(boolean value, @ConstantParameter String message) {
        if (!value) {
            vmMessageC(ASSERTION_VM_MESSAGE_C, true, cstring(message), 0L, 0L, 0L);
        }
    }

    @Snippet
    public static void stubAssertion(boolean value, @ConstantParameter String message) {
        if (!value) {
            vmMessageC(ASSERTION_VM_MESSAGE_C, true, cstring(message), 0L, 0L, 0L);
        }
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void vmMessageC(@ConstantNodeParameter ForeignCallDescriptor stubPrintfC, boolean vmError, Word format, long v1, long v2, long v3);

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo assertion = snippet(AssertionSnippets.class, "assertion");
        private final SnippetInfo stubAssertion = snippet(AssertionSnippets.class, "stubAssertion");

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        public void lower(AssertionNode assertionNode, LoweringTool tool) {
            StructuredGraph graph = assertionNode.graph();
            Arguments args = new Arguments(graph.start() instanceof StubStartNode ? stubAssertion : assertion, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("value", assertionNode.value());
            args.addConst("message", "failed runtime assertion in snippet/stub: " + assertionNode.message() + " (" + graph.method() + ")");

            template(args).instantiate(providers.getMetaAccess(), assertionNode, DEFAULT_REPLACER, args);
        }
    }
}
