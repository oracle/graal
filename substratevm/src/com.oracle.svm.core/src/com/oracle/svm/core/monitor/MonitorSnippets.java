/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;

import java.util.Map;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.java.AccessMonitorNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
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

    protected static final SubstrateForeignCallDescriptor SLOW_PATH_MONITOR_ENTER = SnippetRuntime.findForeignCall(MultiThreadedMonitorSupport.class, "slowPathMonitorEnter",
                    HAS_SIDE_EFFECT, LocationIdentity.any());
    protected static final SubstrateForeignCallDescriptor SLOW_PATH_MONITOR_EXIT = SnippetRuntime.findForeignCall(MultiThreadedMonitorSupport.class, "slowPathMonitorExit",
                    HAS_SIDE_EFFECT, LocationIdentity.any());

    protected static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{SLOW_PATH_MONITOR_ENTER, SLOW_PATH_MONITOR_EXIT};

    @Snippet
    protected static void monitorEnterSnippet(Object obj) {
        /* Kill all memory locations, like {@link MonitorEnterNode#getLocationIdentity()}. */
        MembarNode.memoryBarrier(MembarNode.FenceKind.NONE, LocationIdentity.any());

        callSlowPath(SLOW_PATH_MONITOR_ENTER, obj);
    }

    @Snippet
    protected static void monitorExitSnippet(Object obj) {
        /* Kill all memory locations, like {@link MonitorEnterNode#getLocationIdentity()}. */
        MembarNode.memoryBarrier(MembarNode.FenceKind.NONE, LocationIdentity.any());

        callSlowPath(SLOW_PATH_MONITOR_EXIT, obj);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    protected static native void callSlowPath(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object obj);

    private final SnippetInfo monitorEnter;
    private final SnippetInfo monitorExit;

    @SuppressWarnings("this-escape")
    protected MonitorSnippets(OptionValues options, Providers providers) {
        super(options, providers);

        this.monitorEnter = snippet(providers, MonitorSnippets.class, "monitorEnterSnippet");
        this.monitorExit = snippet(providers, MonitorSnippets.class, "monitorExitSnippet");
    }

    protected void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        MonitorLowering lowering = new MonitorLowering();
        lowerings.put(MonitorEnterNode.class, lowering);
        lowerings.put(MonitorExitNode.class, lowering);
    }

    protected class MonitorLowering implements NodeLoweringProvider<AccessMonitorNode> {

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
                /*
                 * GR-30089: the object is null-checked before monitorenter and can therefore never
                 * be null here, but cycles with loop phis between monitorenter and monitorexit
                 * (with proxy nodes in deopt targets, for example) can cause the stamp to lose this
                 * information. This guard should never trigger, but is left here for caution and
                 * can be replaced with an assertion once the issue is fixed.
                 */
                GuardingNode nullCheck = tool.createGuard(node, node.graph().unique(IsNullNode.create(object)), NullCheckException, InvalidateReprofile, SpeculationLog.NO_SPECULATION, true, null);
                node.setObject(node.graph().addOrUnique(PiNode.create(object, StampFactory.objectNonNull(), (ValueNode) nullCheck)));
            }
        }

        protected void lowerLowTier(AccessMonitorNode node, LoweringTool tool) {
            SnippetInfo snippet;
            if (node instanceof MonitorEnterNode) {
                snippet = monitorEnter;
            } else if (node instanceof MonitorExitNode) {
                snippet = monitorExit;
            } else {
                throw VMError.shouldNotReachHereUnexpectedInput(node); // ExcludeFromJacocoGeneratedReport
            }
            Arguments args = new Arguments(snippet, node.graph(), tool.getLoweringStage());
            args.add("obj", node.object());
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
