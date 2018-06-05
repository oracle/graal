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
package com.oracle.svm.hosted.snippets;

import static com.oracle.svm.core.graal.nodes.UnreachableNode.unreachable;
import static com.oracle.svm.core.graal.snippets.SubstrateIntrinsics.runtimeCall;
import static com.oracle.svm.hosted.nodes.DiscardStampNode.discardStamp;
import static org.graalvm.compiler.nodes.PiNode.piCastToSnippetReplaceeStamp;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.Snippet.VarargsParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.ExplodeLoopNode;

import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.hosted.nodes.AssertStampNode;
import com.oracle.svm.hosted.nodes.AssertTypeStateNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

public final class AssertSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static Object assertStampSnippet(Object object, @ConstantParameter boolean alwaysNull, @ConstantParameter boolean nonNull, @ConstantParameter boolean exactType, DynamicHub expectedHub,
                    byte[] message) {
        /*
         * Discard the stamp information on the input object so that the checks below do not get
         * optimized away by the canonicalizer.
         */
        Object objectWithoutStamp = discardStamp(object);

        if (checkStamp(object, alwaysNull, nonNull, exactType, expectedHub)) {
            /*
             * We passed the check. Return the input object with the original stamp attached again.
             */
            return piCastToSnippetReplaceeStamp(objectWithoutStamp);
        }
        runtimeCall(SnippetRuntime.REPORT_TYPE_ASSERTION_ERROR, message, objectWithoutStamp);
        throw unreachable();
    }

    private static boolean checkStamp(Object object, boolean alwaysNull, boolean nonNull, boolean exactType, DynamicHub expectedHub) {
        if (object != null) {
            if (alwaysNull) {
                return false;
            } else if (expectedHub != null) {
                if (exactType) {
                    return expectedHub == KnownIntrinsics.readHub(object);
                } else {
                    /*
                     * We use assignableFrom instead of instanceOf because the necessary tables for
                     * instanceOf are only created when the static analysis sees a type check for a
                     * type, which is not the case for types just checked by the assertions we
                     * introduce.
                     */
                    return expectedHub.asClass().isAssignableFrom(object.getClass());
                }
            } else {
                return true;
            }
        } else {
            return !nonNull;
        }
    }

    @Snippet
    protected static Object assertTypeStateSnippet(Object object, @ConstantParameter boolean nonNull, @VarargsParameter DynamicHub[] expectedHubs, byte[] message) {
        /*
         * Discard the stamp information on the input object so that the checks below do not get
         * optimized away by the canonicalizer.
         */
        Object objectWithoutStamp = discardStamp(object);

        if (checkTypeState(objectWithoutStamp, nonNull, expectedHubs)) {
            /*
             * We passed the check. Return the input object with the original stamp attached again.
             */
            return piCastToSnippetReplaceeStamp(objectWithoutStamp);
        }
        runtimeCall(SnippetRuntime.REPORT_TYPE_ASSERTION_ERROR, message, objectWithoutStamp);
        throw unreachable();
    }

    private static boolean checkTypeState(Object object, boolean nonNull, DynamicHub[] expectedHubs) {
        if (expectedHubs.length == 0 && nonNull) {
            return false;
        } else if (object != null) {
            DynamicHub objectHub = KnownIntrinsics.readHub(object);
            ExplodeLoopNode.explodeLoop();
            for (DynamicHub expectedHub : expectedHubs) {
                if (expectedHub == objectHub) {
                    return true;
                }
            }
            return false;
        } else {
            return !nonNull;
        }
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new AssertSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    private AssertSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);

        lowerings.put(AssertStampNode.class, new AssertStampLowering());
        lowerings.put(AssertTypeStateNode.class, new AssertTypeStateLowering());
    }

    protected class AssertStampLowering implements NodeLoweringProvider<AssertStampNode> {

        private final SnippetInfo assertStamp = snippet(AssertSnippets.class, "assertStampSnippet");

        @Override
        public final void lower(AssertStampNode node, LoweringTool tool) {
            Arguments args = new Arguments(assertStamp, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", node.getInput());
            args.addConst("alwaysNull", StampTool.isPointerAlwaysNull(node.stamp(NodeView.DEFAULT)));
            args.addConst("nonNull", StampTool.isPointerNonNull(node.stamp(NodeView.DEFAULT)));
            args.addConst("exactType", StampTool.isExactType(node.stamp(NodeView.DEFAULT)));
            ResolvedJavaType type = StampTool.typeOrNull(node.stamp(NodeView.DEFAULT));
            args.add("expectedHub", type == null ? null : ConstantNode.forConstant(providers.getConstantReflection().asJavaClass(type), providers.getMetaAccess(), node.graph()));
            String msg = node.getClass().getSimpleName() + " failed: " + node.graph().method().format("%H.%n(%p)") + " @ " + node.toString(Verbosity.Short) + " expected stamp " +
                            node.stamp(NodeView.DEFAULT) +
                            " but found ";
            args.add("message", msg.getBytes());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class AssertTypeStateLowering implements NodeLoweringProvider<AssertTypeStateNode> {

        private final SnippetInfo assertTypeState = snippet(AssertSnippets.class, "assertTypeStateSnippet");

        @Override
        public final void lower(AssertTypeStateNode node, LoweringTool tool) {
            Arguments args = new Arguments(assertTypeState, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", node.getInput());
            args.addConst("nonNull", node.getTypeState().getNullSeen() == TriState.FALSE);

            ProfiledType[] types = node.getTypeState().getTypes();
            ConstantNode[] expectedHubs = new ConstantNode[types.length];
            for (int i = 0; i < expectedHubs.length; i++) {
                expectedHubs[i] = ConstantNode.forConstant(providers.getConstantReflection().asJavaClass(types[i].getType()), providers.getMetaAccess(), node.graph());
            }
            args.addVarargs("expectedHubs", Class.class, StampFactory.forKind(JavaKind.Object), expectedHubs);
            String msg = node.getClass().getSimpleName() + " failed: " + node.graph().method().format("%H.%n(%p)") + " @ " + node.toString(Verbosity.Short) + " expected type state " +
                            node.getTypeState() + " but found ";
            args.add("message", msg.getBytes());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
