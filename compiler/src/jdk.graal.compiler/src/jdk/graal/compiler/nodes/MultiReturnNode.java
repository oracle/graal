/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import java.util.List;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Represents a node in the graph that handles multiple return values. It encapsulates the original
 * return result, additional return results, and the tail call target address. The tail call target
 * address is used to overwrite the return address.
 *
 * @see ReturnNode#generate
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class MultiReturnNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<MultiReturnNode> TYPE = NodeClass.create(MultiReturnNode.class);

    @OptionalInput ValueNode returnResult;
    @OptionalInput ValueNode tailCallTarget;
    @Input NodeInputList<ValueNode> additionalReturnResults = new NodeInputList<>(this);

    public MultiReturnNode(ValueNode returnResult, ValueNode tailCallTarget) {
        super(TYPE, returnResult == null ? StampFactory.forVoid() : returnResult.stamp(NodeView.DEFAULT));
        this.returnResult = returnResult;
        this.tailCallTarget = tailCallTarget;
    }

    public ValueNode getReturnResult() {
        return returnResult;
    }

    public ValueNode getTailCallTarget() {
        return tailCallTarget;
    }

    public List<ValueNode> getAdditionalReturnResults() {
        return additionalReturnResults;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.setResult(this, generator.operand(returnResult));
    }
}
