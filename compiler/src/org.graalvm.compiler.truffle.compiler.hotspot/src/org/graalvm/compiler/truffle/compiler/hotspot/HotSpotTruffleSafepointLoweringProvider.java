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

import static org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleSafepointForeignCalls.THREAD_LOCAL_HANDSHAKE_POLL;
import static org.graalvm.word.LocationIdentity.any;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.nodes.ReadRegisterNode;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleSafepointNode;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleSafepointNode.TruffleSafepointLoweringProvider;

import com.oracle.truffle.api.CompilerDirectives;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;

@ServiceProvider(TruffleSafepointLoweringProvider.class)
public final class HotSpotTruffleSafepointLoweringProvider implements TruffleSafepointLoweringProvider {

    @Override
    public void lower(TruffleSafepointNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {

            DefaultHotSpotLoweringProvider hs = (DefaultHotSpotLoweringProvider) tool.getLowerer();

            HotSpotGraalRuntimeProvider runtime = hs.getRuntime();
            HotSpotRegistersProvider registers = hs.getRegisters();
            HotSpotConstantReflectionProvider constantReflection = hs.getConstantReflection();

            StructuredGraph graph = n.graph();
            assert n.stateBefore() != null;

            if (runtime.getVMConfig().invokeJavaMethodAddress == 0) {
                throw new JVMCIError("Can't implement TruffleSafepointNode");
            }

            if (!HotSpotTruffleSafepointForeignCalls.Options.HandshakeFastpath.getValue(n.getOptions())) {
                ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(THREAD_LOCAL_HANDSHAKE_POLL, n.stamp(NodeView.DEFAULT)));
                foreignCallNode.setStateDuring(n.stateBefore());
                graph.replaceFixedWithFixed(n, foreignCallNode);
                return;
            }

            FixedWithNextNode predecessor = tool.lastFixedNode();
            FixedNode oldNext = predecessor.next();

            JavaKind wordKind = runtime.getTarget().wordJavaKind;
            Stamp stamp = StampFactory.forKind(wordKind);
            ReadRegisterNode thread = graph.add(new ReadRegisterNode(stamp, registers.getThreadRegister(), true, false));
            predecessor.setNext(thread);

            int pendingOFfset = runtime.getVMConfig().jvmciCountersThreadOffset; // HotSpotThreadLocalHandshake.PENDING_OFFSET;
            AddressNode pendingAddress = hs.createOffsetAddress(graph, thread, pendingOFfset);
            ReadNode pendingRead = graph.add(new ReadNode(pendingAddress, any(), StampFactory.forKind(JavaKind.Int), BarrierType.NONE));
            thread.setNext(pendingRead);

            ValueNode zero = ConstantNode.defaultForKind(JavaKind.Int);
            LogicNode pendingEqualsZero = graph.addOrUniqueWithInputs(CompareNode.createCompareNode(CanonicalCondition.EQ, pendingRead, zero, constantReflection, NodeView.DEFAULT));
            EndNode pendingZeroEnd = graph.add(new EndNode());
            EndNode pendingNotZeroEnd = graph.add(new EndNode());

            int disabledOffset = runtime.getVMConfig().jvmciCountersThreadOffset + Integer.BYTES; // HotSpotThreadLocalHandshake.DISABLED_OFFSET;
            AddressNode disabledAddress = hs.createOffsetAddress(graph, thread, disabledOffset);
            ReadNode disabledRead = graph.add(new ReadNode(disabledAddress, any(), StampFactory.forKind(JavaKind.Int), BarrierType.NONE));

            LogicNode disabledEqualsZero = graph.addOrUniqueWithInputs(CompareNode.createCompareNode(CanonicalCondition.EQ, disabledRead, zero, constantReflection, NodeView.DEFAULT));
            EndNode disabledZeroEnd = graph.add(new EndNode());
            EndNode disabledNotZeroEnd = graph.add(new EndNode());

            ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(THREAD_LOCAL_HANDSHAKE_POLL, n.stamp(NodeView.DEFAULT)));
            foreignCallNode.setStateDuring(n.stateBefore());
            foreignCallNode.setNext(disabledZeroEnd);

            IfNode disabledIf = graph.add(new IfNode(disabledEqualsZero, foreignCallNode, disabledNotZeroEnd, CompilerDirectives.FASTPATH_PROBABILITY));
            disabledRead.setNext(disabledIf);
            MergeNode disabledMerge = graph.add(new MergeNode());
            disabledMerge.addForwardEnd(disabledZeroEnd);
            disabledMerge.addForwardEnd(disabledNotZeroEnd);
            disabledMerge.setNext(pendingNotZeroEnd);
            disabledMerge.setStateAfter(n.stateBefore());

            IfNode pendingIf = graph.add(new IfNode(pendingEqualsZero, pendingZeroEnd, disabledRead, CompilerDirectives.FASTPATH_PROBABILITY));
            pendingRead.setNext(pendingIf);
            MergeNode pendingMerge = graph.add(new MergeNode());
            pendingMerge.addForwardEnd(pendingZeroEnd);
            pendingMerge.addForwardEnd(pendingNotZeroEnd);
            pendingMerge.setNext(oldNext);
            pendingMerge.setStateAfter(n.stateBefore());

            graph.removeFixed(n);
        }
    }

}
