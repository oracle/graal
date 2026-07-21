/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.test;

import org.junit.BeforeClass;

import com.oracle.truffle.api.nodes.RootNode;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCacheNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class PriorityInliningTest extends GraalCompilerTest {
    protected static StructuredGraph assertInlined(StructuredGraph graph) {
        return assertNotInGraph(graph, Invoke.class);
    }

    @BeforeClass
    public static void initializeRuntime() {
        // ensure Truffle runtime needs to be initialized here
        RootNode.createConstantNode(42).getCallTarget();
    }

    protected StructuredGraph getGraph(final String snippet, OptionValues options) {
        DebugContext debug = getDebugContext(options, null, null);
        try (DebugContext.Scope _ = debug.scope("InliningTest", new DebugDumpScope(snippet, true))) {
            ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
            StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES, debug);
            try (DebugContext.Scope _ = debug.scope("PriorityInlining", graph)) {
                PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
                HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                createCanonicalizerPhase().apply(graph, context);
                new PriorityInliningPhase(createCanonicalizerPhase(), graph.getOptions()).apply(graph, context);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                createCanonicalizerPhase().apply(graph, context);
                new DeadCodeEliminationPhase().apply(graph);
                return graph;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected CallTree getCallTreeBeforeInlining(final String snippet) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope _ = debug.scope("InliningTest", new DebugDumpScope(snippet, true))) {
            ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
            StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES, debug);
            try (DebugContext.Scope _ = debug.scope("PriorityInlining", graph)) {
                PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
                HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                createCanonicalizerPhase().apply(graph, context);
                CallTree callTree = new PriorityInliningPhase(this.createCanonicalizerPhase(), graph.getOptions()).createInstance(graph, context).callTree();
                return callTree;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected CallTree getCallTreeAfterInlining(final String snippet) {
        return getCallTreeAfterInlining(snippet, getInitialOptions());
    }

    protected CallTree getCallTreeAfterInlining(final String snippet, OptionValues options) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope _ = debug.scope("InliningTest", new DebugDumpScope(snippet, true))) {
            ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
            StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES, options);
            try (DebugContext.Scope _ = debug.scope("PriorityInlining", graph)) {
                PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
                HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                createCanonicalizerPhase().apply(graph, context);
                CallTree callTree = new PriorityInliningPhase(this.createCanonicalizerPhase(), graph.getOptions()).runInstance(graph, context);
                return callTree;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected CallTree getFullyExpandedCallTree(String methodName) {
        final CallTree callTree = getCallTreeBeforeInlining(methodName);
        callTree.initialize();
        fullyExpand(callTree.root());
        return callTree;
    }

    void fullyExpand(CallTreeNode node) {
        if (node instanceof CutoffNode) {
            final CallTreeNode expanded = node.callTree().expandCutoffNode((CutoffNode) node);
            node.replaceWithAndDelete(expanded);
            fullyExpand(expanded);
        } else if (node instanceof SubgraphNode) {
            ((SubgraphNode) node).createImmediateChildren(node.parent());
            for (CallTreeNode child : node.children()) {
                fullyExpand(child);
            }
        } else if (node instanceof InlineCacheNode) {
            for (CallTreeNode child : node.children()) {
                fullyExpand(child);
            }
        }
    }

    protected ResolvedJavaType getResolvedJavaType(Class<?> cls) {
        return getMetaAccess().lookupJavaType(cls);
    }
}
