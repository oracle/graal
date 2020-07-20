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
package com.oracle.svm.core.monitor;

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.java.AccessMonitorNode;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.nodes.KillMemoryNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.SpeculationLog;

/**
 * Snippets for lowering of monitor nodes (the nodes representing the Java "synchronized" keyword).
 * There is currently no fast path, because the {@link java.util.concurrent.locks.ReentrantLock}
 * used for monitor operations cannot be inlined in a snippet without changes or code duplication.
 * 
 * For AOT compiled code, the null check for the object is already inserted by the bytecode parser,
 * i.e., the object is already guaranteed to be non-null. For JIT compiled code though the null
 * check needs to be inserted during lowering. To enable further high-level optimizations, the null
 * check is inserted during high-tier lowerings, while the snippets are only lowered during the
 * lower tier lowering.
 */
public class MonitorSnippets extends SubstrateTemplates implements Snippets {

    protected static final SubstrateForeignCallDescriptor SLOW_PATH_MONITOR_ENTER = SnippetRuntime.findForeignCall(MultiThreadedMonitorSupport.class, "slowPathMonitorEnter", false);
    protected static final SubstrateForeignCallDescriptor SLOW_PATH_MONITOR_EXIT = SnippetRuntime.findForeignCall(MultiThreadedMonitorSupport.class, "slowPathMonitorExit", false);

    protected static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{SLOW_PATH_MONITOR_ENTER, SLOW_PATH_MONITOR_EXIT};

    @Snippet
    protected static void monitorEnterSnippet(Object obj) {
        /* Kill all memory locations, like {@link MonitorEnterNode#getLocationIdentity()}. */
        KillMemoryNode.killMemory(LocationIdentity.any());

        if (SubstrateOptions.MultiThreaded.getValue()) {
            callSlowPath(SLOW_PATH_MONITOR_ENTER, obj);
        }
    }

    @Snippet
    protected static void monitorExitSnippet(Object obj) {
        /* Kill all memory locations, like {@link MonitorEnterNode#getLocationIdentity()}. */
        KillMemoryNode.killMemory(LocationIdentity.any());

        if (SubstrateOptions.MultiThreaded.getValue()) {
            callSlowPath(SLOW_PATH_MONITOR_EXIT, obj);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    protected static native void callSlowPath(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object obj);

    protected MonitorSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection) {
        super(options, factories, providers, snippetReflection);
    }

    protected void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        MonitorLowering lowering = new MonitorLowering();
        lowerings.put(MonitorEnterNode.class, lowering);
        lowerings.put(MonitorExitNode.class, lowering);
    }

    protected class MonitorLowering implements NodeLoweringProvider<AccessMonitorNode> {

        private final SnippetInfo monitorEnter = snippet(MonitorSnippets.class, "monitorEnterSnippet");
        private final SnippetInfo monitorExit = snippet(MonitorSnippets.class, "monitorExitSnippet");

        @Override
        public final void lower(AccessMonitorNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
                assert StampTool.isPointerNonNull(node.object()) : "null check is inserted by high-tier lowering";
                lowerLowTier(node, tool);
            } else {
                lowerHighTier(node, tool);
            }
        }

        protected void lowerHighTier(AccessMonitorNode node, LoweringTool tool) {
            ValueNode object = node.object();
            if (!StampTool.isPointerNonNull(object)) {
                GuardingNode nullCheck = tool.createGuard(node, node.graph().unique(IsNullNode.create(object)), NullCheckException, InvalidateReprofile, SpeculationLog.NO_SPECULATION, true, null);
                node.setObject(node.graph().maybeAddOrUnique(PiNode.create(object, (object.stamp(NodeView.DEFAULT)).join(StampFactory.objectNonNull()), (ValueNode) nullCheck)));
            }
        }

        protected void lowerLowTier(AccessMonitorNode node, LoweringTool tool) {
            SnippetInfo snippet;
            if (node instanceof MonitorEnterNode) {
                snippet = monitorEnter;
            } else if (node instanceof MonitorExitNode) {
                snippet = monitorExit;
            } else {
                throw VMError.shouldNotReachHere();
            }
            Arguments args = new Arguments(snippet, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("obj", node.object());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
