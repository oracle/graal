/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.core.amd64.AMD64MaskedAddressNode;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.LoopsDataProvider;
import org.graalvm.compiler.nodes.util.GraphUtil;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * A phase the visits each address node and lowers into a machine dependent form.
 */
public class AddressLoweringByNodePhase extends AddressLoweringPhase {

    public abstract static class AddressLowering {

        @SuppressWarnings("unused")
        public void preProcess(StructuredGraph graph, LoopsDataProvider loopsDataProvider) {
        }

        @SuppressWarnings("unused")
        public void postProcess(AddressNode lowered) {
        }

        public abstract AddressNode lower(ValueNode base, ValueNode offset);
    }

    private final AddressLoweringByNodePhase.AddressLowering lowering;

    public AddressLoweringByNodePhase(AddressLoweringByNodePhase.AddressLowering lowering) {
        this.lowering = lowering;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders providers) {
        lowering.preProcess(graph, providers.getLoopsDataProvider());
        for (Node node : graph.getNodes()) {
            AddressNode lowered;
            if (node instanceof OffsetAddressNode) {
                OffsetAddressNode address = (OffsetAddressNode) node;
                lowered = lowering.lower(address.getBase(), address.getOffset());
                lowering.postProcess(lowered);
            } else {
                continue;
            }
            if (lowered instanceof AMD64MaskedAddressNode){
                // In this case, I need to fix the lowered node with the node that might be using it.
                // The reason for that is the lack of derived references in SVM. Thus, the mask node,
                // has as result of the uncompression an unknown reference (instead of a derived one).

                // replace the old node usage with the new one
                if(node.getUsageCount() == 1){
                    ValueAnchorNode anchorNode = graph.add(new ValueAnchorNode(null));
                    ((AMD64MaskedAddressNode) lowered).setAnchorNode(anchorNode);
                    FixedNode fixedNode = (FixedNode) node.usages().iterator().next();
                    graph.addBeforeFixed(fixedNode, anchorNode);

                    node.replaceAtUsages(lowered);

                } else /*if (node.getUsageCount() == 2)*/{
                    while(node.usages().iterator().hasNext()){
                        ValueAnchorNode anchorNode = graph.addWithoutUnique(new ValueAnchorNode(null));
                        AMD64MaskedAddressNode newLowered = graph.addWithoutUnique(new AMD64MaskedAddressNode(lowered.getBase(), lowered.getIndex(), ((AMD64MaskedAddressNode) lowered).getMask(), ((AMD64MaskedAddressNode) lowered).getDisplacement(), ((AMD64MaskedAddressNode) lowered).getShift()));
                        newLowered.setAnchorNode(anchorNode);
                        FixedNode usageNode = (FixedNode) node.usages().iterator().next();
                        graph.addBeforeFixed(usageNode, anchorNode);
                        usageNode.replaceAllInputs(node, newLowered);
                    }
                }
                GraphUtil.killWithUnusedFloatingInputs(node);
                continue;

//                // fix the lowered node before each usage
//                for (Node usage: lowered.usages()){
////                    ValueAnchorNode anchorNode = graph.add(new ValueAnchorNode(null));
//                    AMD64MaskedAddressNode copyOfLower = graph.unique((AMD64MaskedAddressNode)lowered.copyWithInputs());
//                    copyOfLower.setAnchorNode(anchorNode);
//                    graph.addBeforeFixed((FixedNode) usage, anchorNode);
//                    usage.replaceAllInputs(lowered, copyOfLower);
//                }
            }
//            if (lowered instanceof AMD64MaskedAddressNode){
//                AMD64MaskedAddressNode copynode = (AMD64MaskedAddressNode) lowered;
//                int count = 0;
//                ArrayList<Node> nodeUsages = (ArrayList<Node>) node.usages().snapshot();
//                System.out.println();
//                for(Node usageNode: nodeUsages){
//                    System.out.println("Node usage class: " + usageNode.getClass().toString());
//                    if(count > 0){
//                        copynode = graph.unique((AMD64MaskedAddressNode) copynode.copyWithInputs(false));
////                        usageNode.replaceAllInputs(node, copynode);
//                    }else{
////                        usageNode.replaceAllInputs(node, lowered);
//                    }
//                    ValueAnchorNode valueAnchorNode = graph.add(new ValueAnchorNode(null));
//                    graph.addBeforeFixed((FixedNode) usageNode, valueAnchorNode);
//                    copynode.setAnchorNode(valueAnchorNode);
//                    count++;
//                }
////                for(Iterator<Node> nodeIterableIterator = node.usages().iterator(); nodeIterableIterator.hasNext();){
////                    Node usageNode = nodeIterableIterator.next();
////                    if(count > 0){
////                        copynode = graph.unique((AMD64MaskedAddressNode) copynode.copyWithInputs());
////                        usageNode.replaceAllInputs(node, copynode);
////                    }else{
////                        usageNode.replaceAllInputs(node, lowered);
////                    }
////                    ValueAnchorNode valueAnchorNode = graph.add(new ValueAnchorNode(null));
////                    graph.addBeforeFixed((FixedNode) usageNode, valueAnchorNode);
////                    copynode.setAnchorNode(valueAnchorNode);
////                    count++;
////                }
//            }else{
//                node.replaceAtUsages(lowered);
//            }
            node.replaceAtUsages(lowered);
            GraphUtil.killWithUnusedFloatingInputs(node);
        }
    }
}
