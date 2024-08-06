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
package com.oracle.svm.core.nodes;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import com.oracle.svm.core.thread.Safepoint;

import jdk.vm.ci.meta.JavaKind;

/**
 * Checks for a pending safepoint.
 * 
 * @see Safepoint
 */
@NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_2, sizeRationale = "dec+jmp")
public class SafepointCheckNode extends LogicNode implements LIRLowerable {
    public static final NodeClass<SafepointCheckNode> TYPE = NodeClass.create(SafepointCheckNode.class);

    protected SafepointCheckNode() {
        super(TYPE);
    }

    @NodeIntrinsic(value = SafepointPendingConditionalNode.class)
    public static native boolean test();

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // handled in NodeLIRBuilder
    }

    @NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_2, sizeRationale = "dec+jmp")
    static final class SafepointPendingConditionalNode extends FloatingNode implements Canonicalizable {

        public static final NodeClass<SafepointPendingConditionalNode> TYPE = NodeClass.create(SafepointPendingConditionalNode.class);

        protected SafepointPendingConditionalNode() {
            super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        }

        @Override
        public ValueNode canonical(CanonicalizerTool tool) {
            LogicNode condition = new SafepointCheckNode();
            return new ConditionalNode(condition, ConstantNode.forInt(1), ConstantNode.forInt(0));
        }
    }
}
