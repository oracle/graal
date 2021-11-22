/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.CStringConstant;
import org.graalvm.compiler.replacements.nodes.LogNode;
import org.graalvm.compiler.word.Word;

/**
 * Collection of snippets to lower {@link LogNode} with different input edge constellations.
 */
public class LogSnippets implements Snippets {

    @Snippet
    public static void print(@ConstantParameter Word message) {
        Log.print(message);
    }

    @Snippet
    public static void printf1(@ConstantParameter Word message, long l1) {
        Log.printf(message, l1);
    }

    @Snippet
    public static void printf2(@ConstantParameter Word message, long l1, long l2) {
        Log.printf(message, l1, l2);
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo print = snippet(LogSnippets.class, "print");
        private final SnippetInfo printf1 = snippet(LogSnippets.class, "printf1");
        private final SnippetInfo printf2 = snippet(LogSnippets.class, "printf2");

        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);
        }

        public void lower(LogNode logNode, LoweringTool tool) {
            StructuredGraph graph = logNode.graph();
            SnippetInfo info = print;
            if (logNode.getL1() != null) {
                info = printf1;
            }
            if (logNode.getL2() != null) {
                info = printf2;
            }
            Arguments args = new Arguments(info, graph.getGuardsStage(), tool.getLoweringStage());
            args.addConst("message", graph.unique(new ConstantNode(new CStringConstant(logNode.message()), StampFactory.pointer())));
            if (logNode.getL1() != null) {
                args.add("l1", logNode.getL1());
            }
            if (logNode.getL2() != null) {
                args.add("l2", logNode.getL2());
            }
            template(logNode, args).instantiate(providers.getMetaAccess(), logNode, DEFAULT_REPLACER, args);
        }
    }
}
