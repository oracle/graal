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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.Map;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyNode;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.ForeignCallWithExceptionNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.ArrayUtil;

import jdk.vm.ci.meta.JavaKind;

public final class SubstrateArraycopySnippets extends SubstrateTemplates implements Snippets {
    private static final SubstrateForeignCallDescriptor ARRAYCOPY = SnippetRuntime.findForeignCall(SubstrateArraycopySnippets.class, "doArraycopy", false, LocationIdentity.any());
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{ARRAYCOPY};

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    protected SubstrateArraycopySnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);
        lowerings.put(ArrayCopyNode.class, new SubstrateArrayCopyLowering());
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
                ArrayUtil.boundsCheckInSnippet(fromArray, fromIndex, toArray, toIndex, length);
                JavaMemoryUtil.copyPrimitiveArrayBackward(fromArray, fromIndex, fromArray, toIndex, length, fromLayoutEncoding);
                return;
            } else if (fromHub == toHub) {
                ArrayUtil.boundsCheckInSnippet(fromArray, fromIndex, toArray, toIndex, length);
                JavaMemoryUtil.copyPrimitiveArrayForward(fromArray, fromIndex, toArray, toIndex, length, fromLayoutEncoding);
                return;
            }
        } else if (LayoutEncoding.isObjectArray(fromLayoutEncoding)) {
            if (fromArray == toArray && fromIndex < toIndex) {
                ArrayUtil.boundsCheckInSnippet(fromArray, fromIndex, toArray, toIndex, length);
                JavaMemoryUtil.copyObjectArrayBackward(fromArray, fromIndex, fromArray, toIndex, length, fromLayoutEncoding);
                return;
            } else if (fromHub == toHub) {
                ArrayUtil.boundsCheckInSnippet(fromArray, fromIndex, toArray, toIndex, length);
                JavaMemoryUtil.copyObjectArrayForward(fromArray, fromIndex, toArray, toIndex, length, fromLayoutEncoding);
                return;
            } else if (LayoutEncoding.isObjectArray(toHub.getLayoutEncoding())) {
                ArrayUtil.boundsCheckInSnippet(fromArray, fromIndex, toArray, toIndex, length);
                if (DynamicHub.toClass(toHub).isAssignableFrom(DynamicHub.toClass(fromHub))) {
                    JavaMemoryUtil.copyObjectArrayForward(fromArray, fromIndex, toArray, toIndex, length, fromLayoutEncoding);
                } else {
                    JavaMemoryUtil.copyObjectArrayForwardWithStoreCheck(fromArray, fromIndex, toArray, toIndex, length);
                }
                return;
            }
        }
        throw new ArrayStoreException();
    }

    static final class SubstrateArrayCopyLowering implements NodeLoweringProvider<ArrayCopyNode> {
        @Override
        public void lower(ArrayCopyNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            ForeignCallWithExceptionNode call = graph.add(new ForeignCallWithExceptionNode(ARRAYCOPY, node.getSource(), node.getSourcePosition(), node.getDestination(),
                            node.getDestinationPosition(), node.getLength()));
            call.setStateAfter(node.stateAfter());
            call.setStateDuring(node.stateDuring());
            call.setBci(node.bci());
            graph.replaceWithExceptionSplit(node, call);
        }
    }

    @NodeInfo(allowedUsageTypes = {InputType.Memory, InputType.Value}, cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
    public static final class SubstrateGenericArrayCopyCallNode extends BasicArrayCopyNode implements Lowerable {
        public static final NodeClass<SubstrateGenericArrayCopyCallNode> TYPE = NodeClass.create(SubstrateGenericArrayCopyCallNode.class);

        public SubstrateGenericArrayCopyCallNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind) {
            super(TYPE, src, srcPos, dest, destPos, length, elementKind);
        }

        @Override
        public void lower(LoweringTool tool) {
            if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
                StructuredGraph graph = graph();
                ForeignCallWithExceptionNode call = graph.add(new ForeignCallWithExceptionNode(ARRAYCOPY, getSource(), getSourcePosition(), getDestination(),
                                getDestinationPosition(), getLength()));
                call.setStateAfter(stateAfter());
                call.setStateDuring(stateDuring());
                call.setBci(bci());
                graph.replaceWithExceptionSplit(this, call);
            }
        }

        @NodeIntrinsic
        public static native int genericArraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind);
    }
}
