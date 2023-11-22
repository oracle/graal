/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import java.util.List;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import com.oracle.svm.core.graal.lir.DeoptSourcePositionInfoOp;

/**
 * Stores deoptimization source positions.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class DeoptSourcePositionInfoNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<DeoptSourcePositionInfoNode> TYPE = NodeClass.create(DeoptSourcePositionInfoNode.class);

    private final List<NodeSourcePosition> deoptimzationSourcePositions;

    public DeoptSourcePositionInfoNode(List<NodeSourcePosition> deoptimzationSourcePositions) {
        super(TYPE, StampFactory.forVoid());
        assert deoptimzationSourcePositions.get(0) == null : "First index is reserved for unknown source positions";
        this.deoptimzationSourcePositions = deoptimzationSourcePositions;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.getLIRGeneratorTool().append(new DeoptSourcePositionInfoOp(deoptimzationSourcePositions));
    }
}
