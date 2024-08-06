/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * Injects profile data to a {@link SwitchNode}. Specifically, it sets the successor probability for
 * the switch-case block this node is located in. For a given {@link SwitchNode}, either none or all
 * of its successors should be followed by a SwitchCaseProbabilityNode, and their probabilities
 * should sum to one.
 *
 * Instances of this node class will be simplified by their preceding switch node, injecting the
 * given probability of each branch into the switch profile data. Then all probability nodes will be
 * removed at once.
 */
@NodeInfo(cycles = CYCLES_0, cyclesRationale = "Artificial Node", size = SIZE_0)
public final class SwitchCaseProbabilityNode extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<SwitchCaseProbabilityNode> TYPE = NodeClass.create(SwitchCaseProbabilityNode.class);

    @Input ValueNode probability;

    public SwitchCaseProbabilityNode(ValueNode probability) {
        super(TYPE, StampFactory.forKind(JavaKind.Void));
        this.probability = probability;
    }

    public ValueNode getProbability() {
        return probability;
    }

    public void setProbability(ValueNode probability) {
        updateUsages(this.probability, probability);
        this.probability = probability;
    }

    @Override
    public void lower(LoweringTool tool) {
        throw new GraalError("Switch case probability could not be injected, because the probability value did not reduce to a constant value.");
    }
}
