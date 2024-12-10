/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;

/**
 * Marks a position in the graph where a safepoint should be emitted.
 */
// @formatter:off
@NodeInfo(cycles = CYCLES_2,
          cyclesRationale = "read",
          size = SIZE_1)
// @formatter:on
public final class SafepointNode extends DeoptimizingFixedWithNextNode implements Lowerable, LIRLowerable, Simplifiable {

    public static final NodeClass<SafepointNode> TYPE = NodeClass.create(SafepointNode.class);

    /**
     * Optional edge that is set when safepoints are added to a graph. If this safepoint is
     * semantically associated with a loop structure links to the loop begin or loop exit it is
     * mapped to. Over the course of optimizations can "degrade" and point to control flow dominated
     * by the original loop (exit).
     */
    @OptionalInput(InputType.Guard) protected GuardingNode loopLink;

    public SafepointNode() {
        super(TYPE, StampFactory.forVoid());
    }

    public SafepointNode(LoopBeginNode loop) {
        super(TYPE, StampFactory.forVoid());
        this.loopLink = loop;

    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.visitSafepointNode(this);
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (next() instanceof SafepointNode) {
            this.graph().removeFixed(this);
        }
    }

    public void setLoopLink(GuardingNode loopLink) {
        updateUsagesInterface(this.loopLink, loopLink);
        this.loopLink = loopLink;
    }

    public GuardingNode getLoopLink() {
        return loopLink;
    }
}
