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
package jdk.graal.compiler.truffle.hotspot;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.nodes.CurrentJavaThreadNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.truffle.nodes.TruffleSafepointNode;
import jdk.graal.compiler.truffle.phases.TruffleSafepointInsertionPhase;
import jdk.graal.compiler.word.Word;
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
                    NO_SIDE_EFFECT,
                    NO_LOCATIONS,
                    "HotSpotThreadLocalHandshake.doHandshake",
                    void.class, Word.class, Object.class);

    static final LocationIdentity PENDING_HANDSHAKE_LOCATION = NamedLocationIdentity.mutable("JavaThread::_jvmci_reserved0");

    /**
     * Snippet that does the same as
     * {@code com.oracle.truffle.runtime.hotspot.HotSpotThreadLocalHandshake.poll()}.
     *
     * This condition cannot be hoisted out of loops as it is introduced in a phase late enough. See
     * {@link TruffleSafepointInsertionPhase}.
     */
    @Snippet
    private static void pollSnippet(Word method, Object node, @ConstantParameter int pendingHandshakeOffset) {
        Word thread = CurrentJavaThreadNode.get();
        if (BranchProbabilityNode.probability(BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY,
                        thread.readInt(pendingHandshakeOffset, PENDING_HANDSHAKE_LOCATION) != 0)) {
            foreignPoll(THREAD_LOCAL_HANDSHAKE, method, node);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void foreignPoll(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word method, Object node);

    static class Templates extends AbstractTemplates {

        private final SnippetInfo pollSnippet;
        private final int pendingHandshakeOffset;

        Templates(OptionValues options, HotSpotProviders providers, int pendingHandshakeOffset) {
            super(options, providers);
            this.pendingHandshakeOffset = pendingHandshakeOffset;
            this.pollSnippet = snippet(providers, HotSpotTruffleSafepointLoweringSnippet.class, "pollSnippet", PENDING_HANDSHAKE_LOCATION);
        }

        public void lower(TruffleSafepointNode node, LoweringTool tool, ResolvedJavaMethod javaMethod) {
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(pollSnippet, graph, tool.getLoweringStage());
            ValueNode method = ConstantNode.forConstant(tool.getStampProvider().createMethodStamp(), javaMethod.getEncoding(), tool.getMetaAccess(), graph);
            args.add("method", method);
            args.add("node", node.location());
            args.add("pendingHandshakeOffset", pendingHandshakeOffset);
            SnippetTemplate template = template(tool, node, args);
            template.instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }
    }

    static class TruffleHotSpotSafepointLoweringExtension implements DefaultHotSpotLoweringProvider.Extension {

        private Templates templates;

        private final HotSpotKnownTruffleTypes types;

        /**
         * Initialization deferred until the first Truffle compilation starts.
         */
        private volatile Runnable deferredInit;

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
                    templates.lower((TruffleSafepointNode) n, tool, types.HotSpotThreadLocalHandshake_doHandshake);
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
                        Iterable<DebugDumpHandlersFactory> factories) {
            GraalError.guarantee(templates == null, "cannot re-initialize %s", this);
            if (config.invokeJavaMethodAddress != 0 && config.jvmciReserved0Offset != -1) {
                this.templates = new Templates(options, providers, config.jvmciReserved0Offset);
                foreignCalls.register(THREAD_LOCAL_HANDSHAKE.getSignature());
                this.deferredInit = () -> {
                    long address = config.invokeJavaMethodAddress;
                    foreignCalls.invokeJavaMethodStub(options, providers, THREAD_LOCAL_HANDSHAKE, address);
                };
            }
        }
    }
}
