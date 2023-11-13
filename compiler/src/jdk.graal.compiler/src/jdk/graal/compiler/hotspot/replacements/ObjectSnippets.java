/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.nodes.CurrentJavaThreadNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.word.Word;

public class ObjectSnippets implements Snippets {

    @NodeIntrinsic(ForeignCallNode.class)
    public static native byte fastNotifyStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word thread, Object o);

    static boolean fastNotifyStub(ForeignCallDescriptor descriptor, Object object) {
        // These functions return a jboolean which can be returned as a subword type so we must
        // explicitly mask the part we want to read.
        return (fastNotifyStub(descriptor, CurrentJavaThreadNode.get(), object) & 0xff) != 0;
    }

    @Snippet
    public static void fastNotify(Object thisObj) {
        if (probability(FAST_PATH_PROBABILITY, fastNotifyStub(HotSpotHostForeignCallsProvider.NOTIFY, thisObj))) {
            return;
        } else {
            PiNode.piCastNonNull(thisObj, SnippetAnchorNode.anchor()).notify();
        }
    }

    @Snippet
    public static void fastNotifyAll(Object thisObj) {
        if (probability(FAST_PATH_PROBABILITY, fastNotifyStub(HotSpotHostForeignCallsProvider.NOTIFY_ALL, thisObj))) {
            return;
        } else {
            PiNode.piCastNonNull(thisObj, SnippetAnchorNode.anchor()).notifyAll();
        }
    }

    public static class Templates extends AbstractTemplates {
        private final SnippetInfo notifySnippet;
        private final SnippetInfo notifyAllSnippet;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);

            notifySnippet = snippet(providers,
                            ObjectSnippets.class,
                            "fastNotify",
                            findMethod(providers.getMetaAccess(), Object.class, "notify"),
                            null);
            notifyAllSnippet = snippet(providers,
                            ObjectSnippets.class,
                            "fastNotifyAll",
                            findMethod(providers.getMetaAccess(), Object.class, "notifyAll"),
                            null);
        }

        public void lower(Node n, LoweringTool tool) {
            if (n instanceof FastNotifyNode) {
                FastNotifyNode fn = (FastNotifyNode) n;
                StructuredGraph graph = (StructuredGraph) n.graph();
                FrameState stateDuringCall = fn.stateDuring();
                assert stateDuringCall != null : "Must have valid state for snippet recursive notify call";
                Arguments args = new Arguments(fn.isNotifyAll() ? notifyAllSnippet : notifySnippet, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("thisObj", fn.object);
                SnippetTemplate template = template(tool, fn, args);
                graph.getDebug().log("Lowering fast notify in %s: node=%s, template=%s, arguments=%s", graph, fn, template, args);
                UnmodifiableEconomicMap<Node, Node> duplicates = template.instantiate(tool.getMetaAccess(), fn, DEFAULT_REPLACER, args);
                for (Node originalNode : duplicates.getKeys()) {
                    if (originalNode instanceof InvokeNode) {
                        InvokeNode invoke = (InvokeNode) duplicates.get(originalNode);
                        assert invoke.asNode().graph() == graph : Assertions.errorMessage(invoke, invoke.asNode().graph(), graph);
                        // Here we need to fix the bci of the invoke
                        invoke.setBci(fn.getBci());
                        invoke.setStateDuring(null);
                        invoke.setStateAfter(null);
                        invoke.setStateDuring(stateDuringCall);
                    } else if (originalNode instanceof InvokeWithExceptionNode) {
                        throw new GraalError("unexpected invoke with exception %s in snippet", originalNode);
                    }
                }
            } else {
                GraalError.shouldNotReachHere("Unknown object snippet lowered node"); // ExcludeFromJacocoGeneratedReport
            }
        }

    }
}
