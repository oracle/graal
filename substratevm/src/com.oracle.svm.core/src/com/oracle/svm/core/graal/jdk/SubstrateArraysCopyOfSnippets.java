/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.jdk;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiArrayNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.ForeignCallWithExceptionNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

public final class SubstrateArraysCopyOfSnippets extends SubstrateTemplates implements Snippets {
    private static final SubstrateForeignCallDescriptor ARRAYS_COPY_OF = SnippetRuntime.findForeignCall(SubstrateArraysCopyOfSnippets.class, "doArraysCopyOf", true);
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{ARRAYS_COPY_OF};

    public static void registerForeignCalls(Providers providers, SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(providers, FOREIGN_CALLS);
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new SubstrateArraysCopyOfSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static Object doArraysCopyOf(DynamicHub hub, Object original, int originalLength, int newLength) {
        Object newArray = java.lang.reflect.Array.newInstance(DynamicHub.toClass(hub.getComponentHub()), newLength);

        int layoutEncoding = hub.getLayoutEncoding();
        int copiedLength = originalLength < newLength ? originalLength : newLength;
        if (LayoutEncoding.isObjectArray(layoutEncoding)) {
            DynamicHub originalHub = KnownIntrinsics.readHub(original);
            if (originalHub == hub || hub.isAssignableFromHub(originalHub)) {
                ArraycopySnippets.objectCopyForward(original, 0, newArray, 0, copiedLength, layoutEncoding);
            } else {
                ArraycopySnippets.objectStoreCheckCopyForward(original, 0, newArray, 0, copiedLength);
            }
        } else {
            ArraycopySnippets.primitiveCopyForward(original, 0, newArray, 0, copiedLength, layoutEncoding);
        }
        // All elements beyond copiedLength were already zeroed by the allocation.
        return newArray;
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callArraysCopyOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> hub, Object original, int originalLength, int newLength);

    @Snippet
    public static Object arraysCopyOfSnippet(DynamicHub hub, Object original, int originalLength, int newLength) {
        Object result = callArraysCopyOf(ARRAYS_COPY_OF, DynamicHub.toClass(hub), original, originalLength, newLength);
        return PiArrayNode.piArrayCastToSnippetReplaceeStamp(result, newLength);
    }

    private SubstrateArraysCopyOfSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);

        ArraysCopyOfLowering arraysCopyOfLowering = new ArraysCopyOfLowering();
        lowerings.put(SubstrateArraysCopyOfNode.class, arraysCopyOfLowering);
        ArraysCopyOfWithExceptionLowering arraysCopyOfWithExceptionLowering = new ArraysCopyOfWithExceptionLowering();
        lowerings.put(SubstrateArraysCopyOfWithExceptionNode.class, arraysCopyOfWithExceptionLowering);
    }

    protected class ArraysCopyOfLowering implements NodeLoweringProvider<SubstrateArraysCopyOfNode> {
        private final SnippetInfo arraysCopyOf = snippet(SubstrateArraysCopyOfSnippets.class, "arraysCopyOfSnippet");

        @Override
        public void lower(SubstrateArraysCopyOfNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }

            Arguments args = new Arguments(arraysCopyOf, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("hub", node.getNewArrayType());
            args.add("original", node.getOriginal());
            args.add("originalLength", node.getOriginalLength());
            args.add("newLength", node.getNewLength());

            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class ArraysCopyOfWithExceptionLowering implements NodeLoweringProvider<SubstrateArraysCopyOfWithExceptionNode> {

        @Override
        public void lower(SubstrateArraysCopyOfWithExceptionNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            ForeignCallWithExceptionNode call = graph
                            .add(new ForeignCallWithExceptionNode(ARRAYS_COPY_OF, node.getNewArrayType(), node.getOriginal(), node.getOriginalLength(), node.getNewLength()));
            call.setBci(node.bci());
            call.setStamp(node.stamp(NodeView.DEFAULT));
            graph.replaceWithExceptionSplit(node, call);
        }
    }
}
