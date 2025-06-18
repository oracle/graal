/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import java.util.Optional;

import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.hotspot.meta.Bubo.BuboCache;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.ReachabilityFenceNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaConstant;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.code.CodeUtil;

/**
 * Adds ReadNode & Addres to Start of the graphg, this will be use in the Low Ter Instrumentation phase .
 */
public class BuboInstrumentationHighTierPhase extends BasePhase<HighTierContext> {

    @Override
    public boolean checkContract() {
        // the size / cost after is highly dynamic and dependent on the graph, thus we
        // do not verify
        // costs for this phase
        return false;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    public BuboInstrumentationHighTierPhase() {
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, HighTierContext context) {

        try {
            // the ID for this Compilation
            int id = Integer.parseInt(graph.compilationId().toString(Verbosity.ID).split("-")[1]);
            ValueNode ID = graph
                    .addWithoutUnique(new ConstantNode(JavaConstant.forInt(id), StampFactory.forKind(JavaKind.Int)));


            AddressNode TimeBuffer = createBuboAddress("TimeBuffer",ID,graph,context,StampFactory.forBuboTimeRead());
            AddressNode ActivationCountBuffer = createBuboAddress("ActivationCountBuffer",ID,graph,context,StampFactory.forBuboActivationCountRead());
            AddressNode CyclesBuffer = createBuboAddress("CyclesBuffer",ID,graph,context,StampFactory.forBuboCycleRead());
            AddressNode CallSiteRead = createBuboAddress("CallSiteBuffer",ID,graph,context,StampFactory.forBuboCallSiteRead());



            // add a ReachabilityFenceNode this should stop our address from being optmised out
            ValueNode[] list = new ValueNode[]{TimeBuffer,ActivationCountBuffer, CyclesBuffer, CallSiteRead};
            ReachabilityFenceNode fenceNode = graph.add(ReachabilityFenceNode.create(list));
            graph.addAfterFixed(graph.start(), fenceNode);
            fenceNode.setStamp(StampFactory.forBuboVoid());


        } catch (Exception e) {
            e.printStackTrace();
            // TODO: handle exception
        }

    }

    public AddressNode createBuboAddress(String FieldName, ValueNode ID, StructuredGraph graph, HighTierContext context, Stamp stamp) {
        try {
                    // Read the buffer form the static class
        LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null,
                    context.getMetaAccess().lookupJavaField(BuboCache.class.getField(FieldName))));
            graph.addAfterFixed(graph.start(), readBuffer);

            // create the address, and give it our unique stamp for identifaction later on
        AddressNode address = createArrayAddress(graph, readBuffer, context.getMetaAccess().getArrayBaseOffset(JavaKind.Long), JavaKind.Long, ID, context.getMetaAccess());
            address.setStamp(stamp);
            return address;

        } catch (Exception e) {
            e.printStackTrace();
            // TODO: handle exception
        }
        return null;

    }

    public AddressNode createArrayAddress(StructuredGraph graph, ValueNode array, int arrayBaseOffset,
            JavaKind elementKind, ValueNode index, MetaAccessProvider metaAccess) {
        ValueNode wordIndex;
        // this temproy work around the value 8 should not be hard codes
        if (8 > 4) {
            wordIndex = graph.unique(new SignExtendNode(index, 8 * 8));
        } else {
            assert 8 == 4 : "unsupported word size";
            wordIndex = index;
        }
        int shift = CodeUtil.log2(metaAccess.getArrayIndexScale(elementKind));
        ValueNode scaledIndex = graph.unique(new LeftShiftNode(wordIndex, ConstantNode.forInt(shift, graph)));
        ValueNode offset = graph
                .unique(new AddNode(scaledIndex, ConstantNode.forIntegerKind(JavaKind.Long, arrayBaseOffset, graph)));
        return graph.unique(new OffsetAddressNode(array, offset));
    }



} 