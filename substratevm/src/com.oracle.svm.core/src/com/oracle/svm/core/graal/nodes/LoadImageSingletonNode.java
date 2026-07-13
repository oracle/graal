/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.imagelayer.AccessImageSingletonFactory;
import com.oracle.svm.core.imagelayer.AccessImageSingletonFactory.LoadImageSingletonData;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

@NodeInfo(cycles = NodeCycles.CYCLES_4, size = NodeSize.SIZE_2)
public class LoadImageSingletonNode extends FixedWithNextNode implements Lowerable, Canonicalizable, SingleMemoryKill {
    public static final NodeClass<LoadImageSingletonNode> TYPE = NodeClass.create(LoadImageSingletonNode.class);

    private final LoadImageSingletonData singletonInfo;

    protected LoadImageSingletonNode(LoadImageSingletonData singletonInfo, Stamp stamp) {
        super(TYPE, stamp);
        this.singletonInfo = singletonInfo;
    }

    public static LoadImageSingletonNode createLoadImageSingleton(LoadImageSingletonData singletonInfo, MetaAccessProvider metaAccess) {
        return new LoadImageSingletonNode(singletonInfo, StampFactory.objectNonNull(TypeReference.createExactTrusted(metaAccess.lookupJavaType(singletonInfo.getAccessType()))));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (singletonInfo.isApplicationLayerConstant()) {
            return singletonInfo.asApplicationLayerConstant(tool.getMetaAccess(), tool.getSnippetReflection());
        } else if (tool.allUsagesAvailable() && hasNoUsages() && !singletonInfo.isApplicationLayerOnly()) {
            // can remove this load if it is never used.
            return null;
        }

        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph graph = graph();
        var singletonAccessInfo = singletonInfo.getAccessInfo();

        /*
         * Load the starting address of the singleton table.
         */

        CGlobalDataLoadAddressNode baseAddress = graph.unique(new CGlobalDataLoadAddressNode(singletonAccessInfo.tableBase()));

        /*
         * Read from the appropriate offset of the singleton table.
         */

        AddressNode address = graph.unique(new OffsetAddressNode(baseAddress, ConstantNode.forIntegerKind(JavaKind.Long, singletonAccessInfo.offset(), graph)));
        if (singletonInfo.isApplicationLayerOnly()) {
            addMissingSingletonCheck(graph, tool, address);
        }
        var tableReadStamp = SubstrateNarrowOopStamp.compressed((AbstractObjectStamp) stamp(NodeView.DEFAULT), ReferenceAccess.singleton().getCompressEncoding());
        ReadNode tableRead = graph.add(new ReadNode(address, NamedLocationIdentity.FINAL_LOCATION, tableReadStamp, BarrierType.NONE, MemoryOrderMode.PLAIN));

        CompressionNode uncompress = SubstrateCompressionNode.uncompress(graph(), tableRead, ReferenceAccess.singleton().getCompressEncoding());

        replaceAtUsages(uncompress);
        graph.replaceFixed(this, tableRead);
    }

    /**
     * Adds a check at run time to verify if the queried singleton is missing and avoid segmentation
     * faults. This is needed for application layer singletons as they can be expected in shared
     * layers, but some applications might not require them.
     */
    private void addMissingSingletonCheck(StructuredGraph graph, LoweringTool tool, AddressNode address) {
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        MetaAccessProvider metaAccess = tool.getMetaAccess();

        JavaKind referenceKind = ObjectLayout.singleton().getReferenceSize() == Integer.BYTES ? JavaKind.Int : JavaKind.Long;
        ReadNode rawTableRead = graph.add(new ReadNode(address, NamedLocationIdentity.FINAL_LOCATION, StampFactory.forKind(referenceKind), BarrierType.NONE, MemoryOrderMode.PLAIN));
        graph.addBeforeFixed(this, rawTableRead);

        ConstantNode missingOffset = ConstantNode.forIntegerKind(referenceKind, AccessImageSingletonFactory.MISSING_APPLICATION_LAYER_ONLY_SINGLETON_OFFSET, graph);
        LogicNode isMissing = graph.addOrUniqueWithInputs(IntegerEqualsNode.create(constantReflection, metaAccess, graph.getOptions(), null, rawTableRead, missingOffset, NodeView.DEFAULT));
        LogicNode isPresent = graph.addOrUniqueWithInputs(LogicNegationNode.create(isMissing));
        String errorMessage = "Application-layer-only ImageSingleton is not installed in the application layer: " + singletonInfo.getKeyName();
        ValueNode errorMessageNode = ConstantNode.forConstant(constantReflection.forString(errorMessage), metaAccess, graph);
        var guaranteeMethod = metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(VMError.class, "guarantee", boolean.class, String.class));
        MethodCallTargetNode callTarget = graph.add(new SubstrateMethodCallTargetNode(
                        InvokeKind.Static, guaranteeMethod, new ValueNode[]{ConstantNode.forBoolean(false, graph), errorMessageNode}, StampPair.createSingle(StampFactory.forVoid())));
        FrameState stateAfter = GraphUtil.findLastFrameState(this);
        InvokeNode invoke = graph.add(new InvokeNode(callTarget, stateAfter.bci));
        invoke.setStateAfter(stateAfter.duplicateWithVirtualState());
        invoke.setNext(graph.add(new LoweredDeadEndNode()));
        BeginNode present = graph.add(new BeginNode());
        BeginNode missing = graph.add(new BeginNode());
        IfNode check = graph.add(new IfNode(isPresent, present, missing, BranchProbabilityData.unknown()));
        replaceAtPredecessor(check);
        present.setNext(this);
        missing.setNext(invoke);
        tool.getLowerer().lower(invoke, tool);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return singletonInfo.isApplicationLayerOnly() ? LocationIdentity.any() : MemoryKill.NO_LOCATION;
    }
}
