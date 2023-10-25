/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.debug;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.ReachabilityFenceNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Ensures that the provided values remains alive in the high-level IR and LIR until after register
 * allocation. No machine code is emitted for the LIR instruction.
 *
 * This node also prevents escape analysis, i.e., objects are materialized before this node even if
 * there are no other usages of the object. If you do not want this behavior, consider using
 * {@link ReachabilityFenceNode} (but keep in mind that {@link ReachabilityFenceNode} currently does
 * not keep primitive values alive).
 */
@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED, nameTemplate = "Blackhole {p#reason}")
public final class BlackholeNode extends FixedWithNextNode implements LIRLowerable, NodeWithIdentity {

    public static final NodeClass<BlackholeNode> TYPE = NodeClass.create(BlackholeNode.class);
    @Input ValueNode value;
    /** Indicates the reason why we need the blackhole, to be seen in IGV. */
    String reason;

    public BlackholeNode(ValueNode value) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
        this.reason = "";
    }

    public BlackholeNode(ValueNode value, String reason) {
        this(value);
        assert reason != null;
        this.reason = reason;
    }

    public ValueNode getValue() {
        return value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitBlackhole(gen.operand(value));
    }
}
