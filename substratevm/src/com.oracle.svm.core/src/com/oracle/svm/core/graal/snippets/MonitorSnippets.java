/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.MonitorSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.nodes.KillMemoryNode;
import com.oracle.svm.core.graal.nodes.UnreachableNode;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;

public final class MonitorSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static void monitorEnterSnippet(Object obj) {
        /** Kill all memory locations. Cf. {@link MonitorEnterNode#getLocationIdentity()}. */
        KillMemoryNode.killMemory(LocationIdentity.any());
        nullCheck(obj);
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            return;
        }
        ForeignCalls.callMonitorEnter(ForeignCalls.MONITOR_SNIPPETS_SLOW_PATH_MONITOR_ENTER, obj);
    }

    @Snippet
    protected static void monitorExitSnippet(Object obj) {
        /** Kill all memory locations. Cf. {@link MonitorExitNode#getLocationIdentity()}. */
        KillMemoryNode.killMemory(LocationIdentity.any());
        nullCheck(obj);
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            return;
        }
        ForeignCalls.callMonitorExit(ForeignCalls.MONITOR_SNIPPETS_SLOW_PATH_MONITOR_EXIT, obj);
    }

    private static void nullCheck(Object obj) {
        if (obj == null) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.NullCheckException);
            throw UnreachableNode.unreachable();
        }
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new MonitorSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    private MonitorSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);

        lowerings.put(MonitorEnterNode.class, new MonitorEnterLowering());
        lowerings.put(MonitorExitNode.class, new MonitorExitLowering());
    }

    protected class MonitorEnterLowering implements NodeLoweringProvider<MonitorEnterNode> {

        private final SnippetInfo monitorEnter = snippet(MonitorSnippets.class, "monitorEnterSnippet");

        @Override
        public void lower(MonitorEnterNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
                return;
            }
            Arguments args = new Arguments(monitorEnter, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("obj", node.object());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class MonitorExitLowering implements NodeLoweringProvider<MonitorExitNode> {

        private final SnippetInfo monitorExit = snippet(MonitorSnippets.class, "monitorExitSnippet");

        @Override
        public void lower(MonitorExitNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
                return;
            }
            Arguments args = new Arguments(monitorExit, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("obj", node.object());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    @AutomaticFeature
    static class MonitorFeature implements GraalFeature {

        @Override
        public void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection,
                        Map<SubstrateForeignCallDescriptor, SubstrateForeignCallLinkage> foreignCalls, boolean hosted) {
            for (SubstrateForeignCallDescriptor descriptor : ForeignCalls.FOREIGN_CALLS) {
                foreignCalls.put(descriptor, new SubstrateForeignCallLinkage(providers, descriptor));
            }
        }
    }

    static class ForeignCalls {
        static final SubstrateForeignCallDescriptor MONITOR_SNIPPETS_SLOW_PATH_MONITOR_ENTER = SnippetRuntime.findForeignCall(MonitorSupport.class, "monitorEnter", false);
        static final SubstrateForeignCallDescriptor MONITOR_SNIPPETS_SLOW_PATH_MONITOR_EXIT = SnippetRuntime.findForeignCall(MonitorSupport.class, "monitorExit", false);
        private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{MONITOR_SNIPPETS_SLOW_PATH_MONITOR_ENTER, MONITOR_SNIPPETS_SLOW_PATH_MONITOR_EXIT};

        @NodeIntrinsic(value = ForeignCallNode.class)
        static native void callMonitorEnter(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object obj);

        @NodeIntrinsic(value = ForeignCallNode.class)
        static native void callMonitorExit(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object obj);

    }
}
