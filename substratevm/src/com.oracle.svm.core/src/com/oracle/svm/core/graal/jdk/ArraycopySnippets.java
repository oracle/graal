/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.ForeignCallWithExceptionNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

public final class ArraycopySnippets extends SubstrateTemplates implements Snippets {
    private static final SubstrateForeignCallDescriptor ARRAYCOPY = SnippetRuntime.findForeignCall(ArraycopySnippets.class, "doArraycopy", false, LocationIdentity.any());
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{ARRAYCOPY};

    public static void registerForeignCalls(Providers providers, SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(providers, FOREIGN_CALLS);
    }

    protected ArraycopySnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);
        lowerings.put(SubstrateArraycopyNode.class, new ArraycopyLowering());
        lowerings.put(ArrayCopyWithExceptionNode.class, new ArrayCopyWithExceptionLowering());
    }

    /**
     * The actual implementation of {@link System#arraycopy}, called via the foreign call
     * {@link #ARRAYCOPY}.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static void doArraycopy(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        if (fromArray == null || toArray == null) {
            throw new NullPointerException();
        }
        DynamicHub fromHub = KnownIntrinsics.readHub(fromArray);
        DynamicHub toHub = KnownIntrinsics.readHub(toArray);
        int fromLayoutEncoding = fromHub.getLayoutEncoding();

        if (LayoutEncoding.isPrimitiveArray(fromLayoutEncoding)) {
            if (fromArray == toArray && fromIndex < toIndex) {
                boundsCheck(fromArray, fromIndex, toArray, toIndex, length);
                JavaMemoryUtil.copyPrimitiveArrayBackward(fromArray, fromIndex, fromArray, toIndex, length, fromLayoutEncoding);
                return;
            } else if (fromHub == toHub) {
                boundsCheck(fromArray, fromIndex, toArray, toIndex, length);
                JavaMemoryUtil.copyPrimitiveArrayForward(fromArray, fromIndex, toArray, toIndex, length, fromLayoutEncoding);
                return;
            }
        } else if (LayoutEncoding.isObjectArray(fromLayoutEncoding)) {
            if (fromArray == toArray && fromIndex < toIndex) {
                boundsCheck(fromArray, fromIndex, toArray, toIndex, length);
                JavaMemoryUtil.copyObjectArrayBackward(fromArray, fromIndex, fromArray, toIndex, length, fromLayoutEncoding);
                return;
            } else if (fromHub == toHub || DynamicHub.toClass(toHub).isAssignableFrom(DynamicHub.toClass(fromHub))) {
                boundsCheck(fromArray, fromIndex, toArray, toIndex, length);
                JavaMemoryUtil.copyObjectArrayForward(fromArray, fromIndex, toArray, toIndex, length, fromLayoutEncoding);
                return;
            } else if (LayoutEncoding.isObjectArray(toHub.getLayoutEncoding())) {
                boundsCheck(fromArray, fromIndex, toArray, toIndex, length);
                JavaMemoryUtil.copyObjectArrayForwardWithStoreCheck(fromArray, fromIndex, toArray, toIndex, length);
                return;
            }
        }
        throw new ArrayStoreException();
    }

    private static void boundsCheck(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        if (fromIndex < 0 || toIndex < 0 || length < 0 || fromIndex > ArrayLengthNode.arrayLength(fromArray) - length || toIndex > ArrayLengthNode.arrayLength(toArray) - length) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    @Snippet
    private static void arraycopySnippet(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        callArraycopy(ARRAYCOPY, fromArray, fromIndex, toArray, toIndex, length);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callArraycopy(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object fromArray, int fromIndex, Object toArray, int toIndex, int length);

    final class ArraycopyLowering implements NodeLoweringProvider<SubstrateArraycopyNode> {
        private final SnippetInfo arraycopyInfo = snippet(ArraycopySnippets.class, "arraycopySnippet");

        @Override
        public void lower(SubstrateArraycopyNode node, LoweringTool tool) {
            Arguments args = new Arguments(arraycopyInfo, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("fromArray", node.getSource());
            args.add("fromIndex", node.getSourcePosition());
            args.add("toArray", node.getDestination());
            args.add("toIndex", node.getDestinationPosition());
            args.add("length", node.getLength());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    final class ArrayCopyWithExceptionLowering implements NodeLoweringProvider<ArrayCopyWithExceptionNode> {
        @Override
        public void lower(ArrayCopyWithExceptionNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            ForeignCallWithExceptionNode call = graph.add(new ForeignCallWithExceptionNode(ARRAYCOPY, node.getSource(), node.getSourcePosition(), node.getDestination(),
                            node.getDestinationPosition(), node.getLength()));
            call.setStateAfter(node.stateAfter());
            call.setBci(node.getBci());
            graph.replaceWithExceptionSplit(node, call);
        }
    }
}
