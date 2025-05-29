/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.pltgot;

import java.util.Map;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.NonSnippetLowerings;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.pltgot.GOTAccess;
import com.oracle.svm.hosted.nodes.ReadReservedRegister;
import com.oracle.svm.hosted.pltgot.GOTEntryAllocator;
import com.oracle.svm.hosted.pltgot.HostedPLTGOTConfiguration;
import com.oracle.svm.hosted.pltgot.MethodAddressResolutionSupport;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LoweredCallTargetNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.JavaType;

@Platforms(Platform.HOSTED_ONLY.class)
public final class PLTGOTNonSnippetLowerings {

    public static void registerLowerings(RuntimeConfiguration runtimeConfig, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        InvokeThroughGOTLowering invokeLowering = new InvokeThroughGOTLowering(runtimeConfig);
        lowerings.put(InvokeNode.class, invokeLowering);
        lowerings.put(InvokeWithExceptionNode.class, invokeLowering);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static final class InvokeThroughGOTLowering extends NonSnippetLowerings.InvokeLowering {

        private final MethodAddressResolutionSupport methodAddressResolutionSupport;
        private final GOTEntryAllocator gotEntryAllocator;

        InvokeThroughGOTLowering(RuntimeConfiguration runtimeConfig) {
            super(runtimeConfig, SubstrateOptions.VerifyTypes.getValue(), KnownOffsets.singleton());
            this.methodAddressResolutionSupport = HostedPLTGOTConfiguration.singleton().getMethodAddressResolutionSupport();
            this.gotEntryAllocator = HostedPLTGOTConfiguration.singleton().getGOTEntryAllocator();
        }

        @Override
        protected LoweredCallTargetNode createDirectCall(StructuredGraph graph, MethodCallTargetNode callTarget, NodeInputList<ValueNode> parameters, JavaType[] signature,
                        CallingConvention.Type callType, CallTargetNode.InvokeKind invokeKind, SharedMethod callee, FixedNode node) {
            SharedMethod caller = (SharedMethod) graph.method();
            if (methodAddressResolutionSupport.shouldCallViaPLTGOT(caller, callee)) {
                ValueNode heapBaseNode = graph.addOrUnique(ReadReservedRegister.createReadHeapBaseNode(graph));
                int targetGotEntry = gotEntryAllocator.getMethodGotEntry(callee);
                ValueNode offsetNode = ConstantNode.forIntegerKind(ConfigurationValues.getWordKind(), GOTAccess.getGotEntryOffsetFromHeapRegister(targetGotEntry), graph);
                OffsetAddressNode offsetAddressNode = graph.unique(new OffsetAddressNode(heapBaseNode, offsetNode));
                ReadNode methodAddress = graph
                                .add(new ReadNode(offsetAddressNode, LocationIdentity.ANY_LOCATION, FrameAccess.getWordStamp(), BarrierType.NONE, MemoryOrderMode.PLAIN));
                SubstrateGOTCallTargetNode loweredCallTarget = graph.add(
                                new SubstrateGOTCallTargetNode(methodAddress, parameters.toArray(ValueNode.EMPTY_ARRAY), callTarget.returnStamp(), signature, callee, callType, invokeKind));

                graph.addBeforeFixed(node, methodAddress);
                return loweredCallTarget;
            }

            return super.createDirectCall(graph, callTarget, parameters, signature, callType, invokeKind, callee, node);
        }

        @Override
        protected IndirectCallTargetNode createIndirectCall(StructuredGraph graph, MethodCallTargetNode callTarget, NodeInputList<ValueNode> parameters, SharedMethod callee, JavaType[] signature,
                        CallingConvention.Type callType, CallTargetNode.InvokeKind invokeKind, ValueNode entry) {
            SharedMethod caller = (SharedMethod) graph.method();
            /*
             * We don't change how virtual methods are called; instead we make sure that the
             * relocation to an appropriate PLT stub will be emitted in a vtable slot for the
             * callee.
             *
             * This will force all the implementations of a callee method to be resolved via PLT/GOT
             * mechanism. In the future, when we introduce the concept of hot and cold calls we
             * could use a dispatch stub instead to reduce the number of GOT entries.
             */
            if (methodAddressResolutionSupport.shouldCallViaPLTGOT(caller, callee)) {
                for (SharedMethod implementation : callee.getImplementations()) {
                    gotEntryAllocator.reserveMethodGotEntry(implementation);
                }
            }
            return super.createIndirectCall(graph, callTarget, parameters, callee, signature, callType, invokeKind, entry);
        }
    }

}
