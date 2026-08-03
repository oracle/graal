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
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.impl.BarrieredAccess;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.java.AccessMonitorNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
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
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Snippets for lowering of monitor nodes (the nodes representing the Java "synchronized" keyword).
 * The snippets inline the fast paths of {@link MonitorSupport} to avoid a runtime call for
 * uncontendend lock/unlock operations.
 *
 * The fast paths require the {@link DynamicHub#getMonitorOffset() monitor offset} of the locked
 * object. To allow value numbering and constant folding of the monitor offset, it is loaded already
 * during high-tier lowering and passed as an argument to the snippets used for low-tier lowering.
 * The monitor nodes have a VM-dependent field to store such additional information:
 * {@link AccessMonitorNode#setObjectData}.
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

        callSlowPathMonitorEnter(SLOW_PATH_MONITOR_ENTER, obj);
    }

    @Snippet
    protected static void fastMonitorEnterSnippet(Object obj, int monitorOffset) {
        /* Kill all memory locations, like {@link MonitorEnterNode#getLocationIdentity()}. */
        MembarNode.memoryBarrier(MembarNode.FenceKind.NONE, LocationIdentity.any());

        if (probability(SLOW_PATH_PROBABILITY, !MultiThreadedMonitorSupport.tryInflatedMonitorEnter(obj, monitorOffset))) {
            callSlowPathMonitorEnter(SLOW_PATH_MONITOR_ENTER, obj);
        }
    }

    @Snippet
    protected static void monitorExitSnippet(Object obj) {
        /* Kill all memory locations, like {@link MonitorEnterNode#getLocationIdentity()}. */
        MembarNode.memoryBarrier(MembarNode.FenceKind.NONE, LocationIdentity.any());

        callSlowPathMonitorExit(SLOW_PATH_MONITOR_EXIT, obj, JavaMonitor.FastMonitorExitStatus.USE_SLOW_PATH);
    }

    @Snippet
    protected static void fastMonitorExitSnippet(Object obj, int monitorOffset) {
        /* Kill all memory locations, like {@link MonitorEnterNode#getLocationIdentity()}. */
        MembarNode.memoryBarrier(MembarNode.FenceKind.NONE, LocationIdentity.any());

        int status = JavaMonitor.FastMonitorExitStatus.USE_SLOW_PATH;
        if (probability(FAST_PATH_PROBABILITY, monitorOffset != 0)) {
            Object existingMonitor = BarrieredAccess.readObject(obj, monitorOffset);
            status = MultiThreadedMonitorSupport.tryFastBalancedMonitorExit(existingMonitor);
        }
        if (probability(SLOW_PATH_PROBABILITY, status != JavaMonitor.FastMonitorExitStatus.SUCCESS)) {
            callSlowPathMonitorExit(SLOW_PATH_MONITOR_EXIT, obj, status);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    protected static native void callSlowPathMonitorEnter(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object obj);

    @NodeIntrinsic(value = ForeignCallNode.class)
    protected static native void callSlowPathMonitorExit(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object obj, int status);

    /*
     * For runtime compilation in ristretto we want to defer the field resolution because ristretto
     * decorates svm fields with ristretto JVMCI. If we embed the field in the snippet here we are
     * never object replacing it any more.
     */
    private static final class MonitorOffsetFieldAccess {
        private final Object monitorOffsetField;

        private MonitorOffsetFieldAccess(Object monitorOffsetField) {
            this.monitorOffsetField = monitorOffsetField;
        }

        static MonitorOffsetFieldAccess create(Providers providers) {
            Field javaField = ReflectionUtil.lookupField(DynamicHub.class, "monitorOffset");
            if (SubstrateOptions.useRistretto()) {
                return new MonitorOffsetFieldAccess(javaField);
            }
            return new MonitorOffsetFieldAccess(providers.getMetaAccess().lookupJavaField(javaField));
        }

        ResolvedJavaField resolve(MetaAccessProvider metaAccess) {
            if (monitorOffsetField instanceof ResolvedJavaField resolvedJavaField) {
                return resolvedJavaField;
            }
            return metaAccess.lookupJavaField((Field) monitorOffsetField);
        }
    }

    private final MonitorOffsetFieldAccess monitorOffsetFieldAccess;

    private final SnippetInfo monitorEnter;
    private final SnippetInfo monitorExit;
    private final SnippetInfo fastMonitorEnter;
    private final SnippetInfo fastMonitorExit;

    @SuppressWarnings("this-escape")
    protected MonitorSnippets(OptionValues options, Providers providers) {
        super(options, providers);

        this.monitorEnter = snippet(providers, MonitorSnippets.class, "monitorEnterSnippet");
        this.monitorExit = snippet(providers, MonitorSnippets.class, "monitorExitSnippet");
        this.fastMonitorEnter = snippet(providers, MonitorSnippets.class, "fastMonitorEnterSnippet");
        this.fastMonitorExit = snippet(providers, MonitorSnippets.class, "fastMonitorExitSnippet");
        this.monitorOffsetFieldAccess = MonitorOffsetFieldAccess.create(providers);
    }

    protected void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        MonitorLowering lowering = new MonitorLowering();
        lowerings.put(MonitorEnterNode.class, lowering);
        lowerings.put(MonitorExitNode.class, lowering);
    }

    protected class MonitorLowering implements NodeLoweringProvider<AccessMonitorNode> {

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

            if (node.getObjectData() == null && useFastPath(node)) {

                /*
                 * The object and the pre-computed monitor offset must remain in sync, so for
                 * simplicity we modify the object only when the monitor offset was not yet
                 * computed.
                 */
                if (node.object().isJavaConstant()) {
                    /*
                     * We cannot use the SnippetReflectionProvider to unwrap the constant, because
                     * runtime compilation in separate isolates cannot unwrap arbitrary constants.
                     * But since we only need to replace Class/DynamicHub objects, unwrapping type
                     * constants is enough.
                     */
                    ResolvedJavaType constantType = tool.getConstantReflection().asJavaType(node.object().asJavaConstant());
                    if (constantType != null) {
                        DynamicHubCompanion replacedObject = ((SharedType) constantType).getHub().getCompanion();
                        /*
                         * For wrapping the replaced object, we can use the
                         * SnippetReflectionProvider because all DynamicHubCompanion objects are in
                         * the image heap.
                         */
                        node.setObject(ConstantNode.forConstant(tool.getSnippetReflection().forObject(replacedObject), tool.getMetaAccess(), node.graph()));
                    }
                }

                ValueNode hub = node.graph().addOrUnique(LoadHubNode.create(node.object(), tool.getStampProvider(), tool.getMetaAccess(), tool.getConstantReflection()));

                ResolvedJavaField effectiveMonitorOffsetField = monitorOffsetFieldAccess.resolve(tool.getMetaAccess());

                LoadFieldNode monitorOffset = node.graph().add(LoadFieldNode.create(node.graph().getAssumptions(), hub, effectiveMonitorOffsetField));
                node.graph().addBeforeFixed(node, monitorOffset);
                node.setObjectData(monitorOffset);
                monitorOffset.lower(tool);
            }
        }

        protected void lowerLowTier(AccessMonitorNode node, LoweringTool tool) {
            if (useFastPath(node)) {
                lowerLowTier(node, tool, fastMonitorEnter, fastMonitorExit, true);
            } else {
                lowerLowTier(node, tool, monitorEnter, monitorExit, false);
            }
        }

        protected void lowerLowTier(AccessMonitorNode node, LoweringTool tool, SnippetInfo enterSnippet, SnippetInfo exitSnippet, boolean withOffsetArg) {
            SnippetInfo snippet = switch (node) {
                case MonitorEnterNode _ -> enterSnippet;
                case MonitorExitNode _ -> exitSnippet;
                default -> throw VMError.shouldNotReachHereUnexpectedInput(node); // ExcludeFromJacocoGeneratedReport
            };
            Arguments args = new Arguments(snippet, node.graph(), tool.getLoweringStage());
            args.add("obj", node.object());
            if (withOffsetArg) {
                args.add("monitorOffset", Objects.requireNonNull(node.getObjectData()));
            }
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        protected boolean useFastPath(AccessMonitorNode node) {
            /*
             * The pre-loading of the hub and monitor offset cannot be done in deoptimization target
             * methods: value numbering of these nodes would lead to additional values live at
             * deoptimization entry points. Since peak performance is not critical in deoptimization
             * target methods, we do not use fast paths at all for these methods.
             */
            return SubstrateOptions.UseMonitorFastPath.getValue() &&
                            !((SharedMethod) node.graph().method()).isDeoptTarget();
        }

        @Override
        public final void lower(AccessMonitorNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
                assert StampTool.isPointerNonNull(node.object()) : "null check is inserted by high-tier lowering";
                lowerLowTier(node, tool);
            } else {
                lowerHighTier(node, tool);
            }
        }
    }
}
