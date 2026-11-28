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
import com.oracle.svm.core.pgo.BranchProfileKey;
import com.oracle.svm.core.pgo.BranchProfileThreadCounters;
import com.oracle.svm.core.pgo.BranchProfileRecorder;

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
 * Counts one successor of an instrumented branch: lowers to a relaxed increment in the thread's
 * branch-counter block. Every instrumented {@code IfNode} gets two of these nodes, one at the
 * start of its true successor and one at the start of its false successor
 * ({@link BranchProfileInstrumentationPhase}); they share the branch's {@link BranchProfileKey}
 * and increment its true or its false count.
 * <p>
 * The node is deliberately not a memory kill: the lowered snippet reads the thread-local block
 * pointer and reads and writes the counter location, and both are declared private to the
 * snippet, so no floating read outside the snippet can be attached to them. Declaring the node a
 * kill of any location would make the snippet template rewire every floating read that follows the
 * node to a kill inside the snippet, which the snippet does not contain.
 */
@NodeInfo
public final class BranchProfileCounterNode extends PGOInstrumentationNode {

    public static final NodeClass<BranchProfileCounterNode> TYPE = NodeClass.create(BranchProfileCounterNode.class);

    private final BranchProfileKey key;
    /** Whether this node sits on the true successor; the false successor has a node of its own. */
    private final boolean onTrueSuccessor;

    public BranchProfileCounterNode(BranchProfileKey key, boolean onTrueSuccessor) {
        super(TYPE);
        this.key = key;
        this.onTrueSuccessor = onTrueSuccessor;
    }

    @Override
    protected void lowerToSnippet(LoweringTool tool) {
        tool.getReplacements().getSnippetTemplateCache(Templates.class).lower(this, tool);
    }

    private static final class CounterSnippet implements Snippets {
        /* The names of the snippet and of its parameters, by which Templates binds arguments. */
        static final String INCREMENT = "increment";
        static final String COUNTER_INDEX = "counterIndex";
        static final String ON_TRUE_SUCCESSOR = "onTrueSuccessor";

        /**
         * Increments the true or the false count of the key numbered {@code counterIndex}. The
         * index is not a {@link ConstantParameter}: those are part of the key of the template cache,
         * so every counter would build a template of its own and push the templates of other
         * snippets out of the shared cache. The index is bound as a constant all the same, and the
         * canonicalizer folds the offset arithmetic.
         */
        @Snippet
        private static void increment(int counterIndex, @ConstantParameter boolean onTrueSuccessor) {
            BranchProfileRecorder.increment(counterIndex, onTrueSuccessor);
        }
    }

    static final class Templates extends SubstrateTemplates {
        private final SnippetInfo increment;

        Templates(OptionValues options, Providers providers) {
            super(options, providers);
            increment = snippet(providers, CounterSnippet.class, CounterSnippet.INCREMENT, BranchProfileThreadCounters.blockLocation(), BranchProfileRecorder.COUNTER_LOCATION);
        }

        void lower(BranchProfileCounterNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(increment, graph, tool.getLoweringStage());
            /* Numbers the key now: only keys whose counters reach machine code get counter memory. */
            args.add(CounterSnippet.COUNTER_INDEX, node.key.counterIndexForCode());
            args.add(CounterSnippet.ON_TRUE_SUCCESSOR, node.onTrueSuccessor);
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }
    }
}
