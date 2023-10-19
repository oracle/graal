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
package jdk.compiler.graal.truffle.hotspot;

import static jdk.compiler.graal.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static jdk.compiler.graal.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static jdk.compiler.graal.replacements.SnippetTemplate.DEFAULT_REPLACER;

import jdk.compiler.graal.api.replacements.Snippet;
import jdk.compiler.graal.api.replacements.Snippet.ConstantParameter;
import jdk.compiler.graal.core.common.spi.ForeignCallDescriptor;
import jdk.compiler.graal.debug.DebugHandlersFactory;
import jdk.compiler.graal.debug.GraalError;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.Node.ConstantNodeParameter;
import jdk.compiler.graal.graph.Node.NodeIntrinsic;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.hotspot.meta.DefaultHotSpotLoweringProvider;
import jdk.compiler.graal.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.compiler.graal.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability;
import jdk.compiler.graal.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.nodes.CurrentJavaThreadNode;
import jdk.compiler.graal.nodes.NamedLocationIdentity;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.extended.BranchProbabilityNode;
import jdk.compiler.graal.nodes.extended.ForeignCallNode;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.nodes.util.GraphUtil;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.replacements.SnippetTemplate;
import jdk.compiler.graal.replacements.SnippetTemplate.AbstractTemplates;
import jdk.compiler.graal.replacements.SnippetTemplate.Arguments;
import jdk.compiler.graal.replacements.SnippetTemplate.SnippetInfo;
import jdk.compiler.graal.replacements.Snippets;
import jdk.compiler.graal.truffle.nodes.TruffleSafepointNode;
import jdk.compiler.graal.truffle.phases.TruffleSafepointInsertionPhase;
import jdk.compiler.graal.word.Word;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Snippet that lowers {@link TruffleSafepointNode}.
 */
public final class HotSpotTruffleSafepointLoweringSnippet implements Snippets {

    /**
     * Description for a call to
     * {@code com.oracle.truffle.runtime.hotspot.HotSpotThreadLocalHandshake.doHandshake()} via a
     * stub.
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
     * {@code com.oracle.truffle.runtime.hotspot.HotSpotThreadLocalHandshake.poll()}.
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

        private final HotSpotKnownTruffleTypes types;

        /**
         * Initialization deferred until the first Truffle compilation starts.
         */
        @NativeImageReinitialize private volatile Runnable deferredInit;

        TruffleHotSpotSafepointLoweringExtension(HotSpotKnownTruffleTypes types) {
            this.types = types;
        }

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
                    ResolvedJavaMethod staticMethod = types.HotSpotThreadLocalHandshake_doHandshake;
                    foreignCalls.invokeJavaMethodStub(options, providers, THREAD_LOCAL_HANDSHAKE, address, staticMethod);
                };
            }
        }
    }
}
