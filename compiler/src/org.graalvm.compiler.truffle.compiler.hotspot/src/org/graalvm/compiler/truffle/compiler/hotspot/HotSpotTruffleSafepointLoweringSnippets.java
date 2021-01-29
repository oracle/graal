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
package org.graalvm.compiler.truffle.compiler.hotspot;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleSafepointNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Snippets that do the HotSpot lowering of {@link TruffleSafepointNode}.
 */
@ServiceProvider(DefaultHotSpotLoweringProvider.Extension.class)
public final class HotSpotTruffleSafepointLoweringSnippets implements DefaultHotSpotLoweringProvider.Extension, Snippets {

    private Templates templates;

    /**
     * Description for a call to
     * {@code org.graalvm.compiler.truffle.runtime.hotspot.HotSpotThreadLocalHandshake.doHandshake()}
     * via a stub.
     */
    static final HotSpotForeignCallDescriptor THREAD_LOCAL_HANDSHAKE = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "HotSpotThreadLocalHandshake.doHandshake",
                    void.class);

    static final LocationIdentity JVMCI_COUNTERS_LOCATION = NamedLocationIdentity.mutable("JavaThread::_jvmci_counters");

    @Fold
    public static int jvmciCountersOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.jvmciCountersThreadOffset;
    }

    /**
     * Snippet that does the same as
     * {@code org.graalvm.compiler.truffle.runtime.hotspot.HotSpotThreadLocalHandshake.poll()}.
     */
    @Snippet
    private static void pollSnippet() {
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY,//
                        CurrentJavaThreadNode.get().readInt(jvmciCountersOffset(INJECTED_VMCONFIG), JVMCI_COUNTERS_LOCATION) != 0)) {
            foreignPoll(THREAD_LOCAL_HANDSHAKE);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void foreignPoll(@ConstantNodeParameter ForeignCallDescriptor descriptor);

    static class Templates extends AbstractTemplates {

        private final SnippetInfo pollSnippet = snippet(HotSpotTruffleSafepointLoweringSnippets.class, "pollSnippet", JVMCI_COUNTERS_LOCATION);

        public Templates(OptionValues options,
                        Iterable<DebugHandlersFactory> factories,
                        HotSpotProviders providers,
                        TargetDescription target) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
        }

        public void lower(TruffleSafepointNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(pollSnippet, graph.getGuardsStage(), tool.getLoweringStage());
            SnippetTemplate template = template(node, args);
            template.instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }
    }

    @Override
    public Class<TruffleSafepointNode> getNodeType() {
        return TruffleSafepointNode.class;
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
            if (templates == null) {
                throw new GraalError("Cannot lower %s nodes as JVMCIRuntime::invoke_java_method is missing", n);
            }
            templates.lower((TruffleSafepointNode) n, tool);
        }
    }

    @Override
    public void initialize(HotSpotProviders providers,
                    OptionValues options,
                    GraalHotSpotVMConfig config,
                    HotSpotHostForeignCallsProvider foreignCalls,
                    Iterable<DebugHandlersFactory> factories) {
        long invokeJavaMethodAddress = config.invokeJavaMethodAddress;
        if (invokeJavaMethodAddress != 0) {
            this.templates = new Templates(options, factories, providers, providers.getCodeCache().getTarget());
            ResolvedJavaType handshakeType = TruffleCompilerRuntime.getRuntime().resolveType(providers.getMetaAccess(), "org.graalvm.compiler.truffle.runtime.hotspot.HotSpotThreadLocalHandshake");
            HotSpotSignature noArgsVoidSig = new HotSpotSignature(foreignCalls.getJVMCIRuntime(), "()V");
            ResolvedJavaMethod staticMethod = handshakeType.findMethod("doHandshake", noArgsVoidSig);
            foreignCalls.invokeJavaMethodStub(options, providers, THREAD_LOCAL_HANDSHAKE, invokeJavaMethodAddress, staticMethod);
        }
    }
}
