/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.nodes.GraphState.StageFlag.HIGH_TIER_LOWERING;
import static jdk.graal.compiler.nodes.GraphState.StageFlag.LOOP_OVERFLOWS_CHECKED;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.Pair;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.memory.FloatableAccessNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Denotes a loop header to be candidate for threaded switch optimization.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public class ThreadedSwitchNode extends FloatingNode implements Canonicalizable, LIRLowerable {

    public static final NodeClass<ThreadedSwitchNode> TYPE = NodeClass.create(ThreadedSwitchNode.class);

    @Input protected ValueNode value;

    public ThreadedSwitchNode(ValueNode value) {
        super(TYPE, value.stamp(NodeView.DEFAULT));
        this.value = value;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value != null && value.isConstant()) {
            // The switch can be folded if input is constant
            return value;
        }
        if (graph() == null) {
            // During graph decoding
            return this;
        }
        if (graph().isBeforeStage(LOOP_OVERFLOWS_CHECKED) && graph().isBeforeStage(HIGH_TIER_LOWERING)) {
            // Wait until inlining is done, latest until high tier lowering
            return this;
        }

        for (Node node : usages()) {
            if (node instanceof IntegerSwitchNode switchNode) {
                FixedNode current = switchNode;
                List<Pair<IfNode, AbstractBeginNode>> toInjectProbability = new ArrayList<>();

                // We allow memory reads, value anchors, and IfNodes in the loop header. IfNode
                // profiles are adjusted to enforce sequential execution and prevent branching
                // between the header and the marked switch.
                do {
                    FixedNode predecessor = (FixedNode) current.predecessor();
                    if (predecessor instanceof IfNode ifNode && current instanceof AbstractBeginNode ifSuccessor) {
                        if (ifNode.probability(ifSuccessor) < FREQUENT_PROBABILITY) {
                            toInjectProbability.add(Pair.create(ifNode, ifSuccessor));
                        }
                    }
                    current = predecessor;
                } while (skipFixedNode(current));

                if (current instanceof LoopBeginNode loopBeginNode) {
                    switchNode.markThreadedCode();
                    loopBeginNode.markThreadedCode();
                    for (var pair : toInjectProbability) {
                        pair.getLeft().setProbability(pair.getRight(), ProfileData.BranchProbabilityData.inferred(FREQUENT_PROBABILITY));
                    }
                    if (GraalOptions.TraceThreadedSwitchOptimization.getValue(tool.getOptions())) {
                        TTY.println("%s: %s marks %s - %s as candidate for threaded switch optimization.", graph().method().format("%h.%n"), this, loopBeginNode, switchNode);
                    }
                } else {
                    if (GraalOptions.TraceThreadedSwitchOptimization.getValue(tool.getOptions())) {
                        TTY.println("%s: %s does not find a valid LoopBeginNode-SwitchNode pair. It stopped searching at %s.", graph().method().format("%h.%n"), this, current);
                    }
                }
            }
        }
        return value;
    }

    /**
     * Defines a list of node types that are allowed when identifying loop header blocks for
     * threaded switches.
     */
    private static boolean skipFixedNode(FixedNode node) {
        return node instanceof BeginNode || node instanceof FullInfopointNode || node instanceof IfNode || node instanceof FixedGuardNode ||
                        node instanceof RawLoadNode || node instanceof FloatableAccessNode || node instanceof ValueAnchorNode || node instanceof LoadIndexedNode;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.setResult(this, generator.operand(value));
    }
}
