/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.phases.common.instrumentation.nodes;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeFlood;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.AbstractEndNode;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.LoopEndNode;
import com.oracle.graal.nodes.ValuePhiNode;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo
public final class RuntimePathNode extends InstrumentationContentNode {

    public static final NodeClass<RuntimePathNode> TYPE = NodeClass.create(RuntimePathNode.class);

    public RuntimePathNode() {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
    }

    private static boolean isCFGAccessible(FixedNode from, FixedNode to) {
        NodeFlood flood = from.graph().createNodeFlood();
        flood.add(from);
        for (Node current : flood) {
            if (current instanceof LoopEndNode) {
                continue;
            } else if (current instanceof AbstractEndNode) {
                flood.add(((AbstractEndNode) current).merge());
            } else {
                flood.addAll(current.successors());
            }
        }
        return flood.isMarked(to);
    }

    @Override
    public void onInlineInstrumentation(InstrumentationNode instrumentation, FixedNode position) {
        if (instrumentation.target() instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) instrumentation.target();

            if (isCFGAccessible(merge, position)) {
                ValuePhiNode phi = graph().addWithoutUnique(new ValuePhiNode(StampFactory.intValue(), merge));
                for (int i = 0; i < merge.cfgPredecessors().count(); i++) {
                    phi.addInput(ConstantNode.forInt(i, merge.graph()));
                }
                graph().replaceFixedWithFloating(this, phi);
                return;
            }
        }
        graph().replaceFixedWithFloating(this, ConstantNode.forInt(-1, graph()));
    }

}
