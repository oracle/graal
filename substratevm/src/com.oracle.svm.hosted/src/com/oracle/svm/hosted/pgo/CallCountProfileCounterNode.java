/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo;

import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.pgo.CallCountProfileRecorder;
import com.oracle.svm.core.pgo.CallCountProfileSlot;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;

/**
 * Counts one call edge, or one switch successor, in the thread's call-count table. The counter is
 * numbered when the first copy of the node is lowered, and copies of the node share it.
 */
@NodeInfo
public final class CallCountProfileCounterNode extends PGOInstrumentationNode {
    public static final NodeClass<CallCountProfileCounterNode> TYPE = NodeClass.create(CallCountProfileCounterNode.class);

    private final CallCountProfileSlot slot;

    public CallCountProfileCounterNode(CallCountProfileSlot slot) {
        super(TYPE);
        this.slot = slot;
    }

    @Override
    protected void lowerToSnippet(LoweringTool tool) {
        tool.getReplacements().getSnippetTemplateCache(Templates.class).lower(this, tool);
    }

    private static final class CounterSnippet implements Snippets {
        /* The names of the snippet and of its parameter, by which Templates binds arguments. */
        static final String INCREMENT = "increment";
        static final String COUNTER_INDEX = "counterIndex";

        @Snippet
        private static void increment(@ConstantParameter int counterIndex) {
            CallCountProfileRecorder.increment(counterIndex);
        }
    }

    static final class Templates extends SubstrateTemplates {
        private final SnippetInfo increment;

        Templates(OptionValues options, Providers providers) {
            super(options, providers);
            increment = snippet(providers, CounterSnippet.class, CounterSnippet.INCREMENT, CallCountProfileRecorder.tablePointerLocation(), CallCountProfileRecorder.TABLE_LOCATION);
        }

        void lower(CallCountProfileCounterNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(increment, graph, tool.getLoweringStage());
            /* Numbers the counter now: only counters that reach machine code get an index. */
            args.add(CounterSnippet.COUNTER_INDEX, node.slot.counterIndexForCode());
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }
    }
}
