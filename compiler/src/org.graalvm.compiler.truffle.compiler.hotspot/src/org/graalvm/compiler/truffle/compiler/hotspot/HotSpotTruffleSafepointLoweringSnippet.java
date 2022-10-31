/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleSafepointNode;
import org.graalvm.compiler.truffle.compiler.phases.TruffleSafepointInsertionPhase;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Snippet that lowers {@link TruffleSafepointNode}.
 */
public final class HotSpotTruffleSafepointLoweringSnippet implements Snippets {

    /**
     * Description for a call to
     * {@code org.graalvm.compiler.truffle.runtime.hotspot.HotSpotThreadLocalHandshake.doHandshake()}
     * via a stub.
     */
    static final HotSpotForeignCallDescriptor THREAD_LOCAL_HANDSHAKE = new HotSpotForeignCallDescriptor(
                    SAFEPOINT,
                    Reexecutability.REEXECUTABLE,
                    NO_LOCATIONS,
                    "HotSpotThreadLocalHandshake.doHandshake",
                    void.class, Object.class);

    static final LocationIdentity PENDING_HANDSHAKE_LOCATION = NamedLocationIdentity.mutable("JavaThread::_jvmci_reserved0");

    /**
     * Snippet that does the same as
     * {@code org.graalvm.compiler.truffle.runtime.hotspot.HotSpotThreadLocalHandshake.poll()}.
     *
     * This condition cannot be hoisted out of loops as it is introduced in a phase late enough. See
     * {@link TruffleSafepointInsertionPhase}.
     */
    @Snippet
    private static void pollSnippet(Object node, @ConstantParameter int pendingHandshakeOffset) {
        Word thread = CurrentJavaThreadNode.get();
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY,
                        thread.readInt(pendingHandshakeOffset, PENDING_HANDSHAKE_LOCATION) != 0)) {
            foreignPoll(THREAD_LOCAL_HANDSHAKE, node);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void foreignPoll(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object node);

    static class Templates extends AbstractTemplates {

        private final SnippetInfo pollSnippet;
        private final int pendingHandshakeOffset;

        Templates(OptionValues options, HotSpotProviders providers, int pendingHandshakeOffset) {
            super(options, providers);
            this.pendingHandshakeOffset = pendingHandshakeOffset;
            this.pollSnippet = snippet(providers, HotSpotTruffleSafepointLoweringSnippet.class, "pollSnippet", PENDING_HANDSHAKE_LOCATION);
        }

        public void lower(TruffleSafepointNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(pollSnippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("node", node.location());
            args.addConst("pendingHandshakeOffset", pendingHandshakeOffset);
            SnippetTemplate template = template(tool, node, args);
            template.instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }
    }

    static class TruffleHotSpotSafepointLoweringExtension implements DefaultHotSpotLoweringProvider.Extension {

        @NativeImageReinitialize private Templates templates;

        /**
         * Initialization deferred until a {@link TruffleCompilerRuntime} is guaranteed to exist.
         */
        @NativeImageReinitialize private volatile Runnable deferredInit;

        @Override
        public Class<TruffleSafepointNode> getNodeType() {
            return TruffleSafepointNode.class;
        }

        @Override
        public void lower(Node n, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
                doDeferredInit();
                if (templates != null) {
                    templates.lower((TruffleSafepointNode) n, tool);
                } else {
                    GraphUtil.unlinkFixedNode((TruffleSafepointNode) n);
                    n.safeDelete();
                }
            }
        }

        private void doDeferredInit() {
            if (deferredInit != null) {
                synchronized (this) {
                    if (deferredInit != null) {
                        deferredInit.run();
                        deferredInit = null;
                    }
                }
            }
        }

        @Override
        public void initialize(HotSpotProviders providers,
                        OptionValues options,
                        GraalHotSpotVMConfig config,
                        HotSpotHostForeignCallsProvider foreignCalls,
                        Iterable<DebugHandlersFactory> factories) {
            GraalError.guarantee(templates == null, "cannot re-initialize %s", this);
            if (config.invokeJavaMethodAddress != 0 && config.jvmciReserved0Offset != -1) {
                this.templates = new Templates(options, providers, config.jvmciReserved0Offset);
                foreignCalls.register(THREAD_LOCAL_HANDSHAKE.getSignature());
                this.deferredInit = () -> {
                    long address = config.invokeJavaMethodAddress;
                    GraalError.guarantee(address != 0, "Cannot lower %s as JVMCIRuntime::invoke_static_method_one_arg is missing", address);
                    ResolvedJavaType handshakeType = TruffleCompilerRuntime.getRuntime().resolveType(providers.getMetaAccess(),
                                    "org.graalvm.compiler.truffle.runtime.hotspot.HotSpotThreadLocalHandshake");
                    HotSpotSignature sig = new HotSpotSignature(foreignCalls.getJVMCIRuntime(), "(Ljava/lang/Object;)V");
                    ResolvedJavaMethod staticMethod = handshakeType.findMethod("doHandshake", sig);
                    foreignCalls.invokeJavaMethodStub(options, providers, THREAD_LOCAL_HANDSHAKE, address, staticMethod);
                };
            }
        }
    }
}
