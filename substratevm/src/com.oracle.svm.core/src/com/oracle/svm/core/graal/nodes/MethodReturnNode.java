/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_2;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.FixedNode;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.ReturnNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.spi.Lowerable;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.nodes.util.GraphUtil;

@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public class MethodReturnNode extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<MethodReturnNode> TYPE = NodeClass.create(MethodReturnNode.class);

    @Input protected ValueNode result;

    protected MethodReturnNode(ValueNode result) {
        super(TYPE, StampFactory.object());
        this.result = result;
    }

    @Override
    public void lower(LoweringTool tool) {
        FixedNode originalNext = next();
        replaceFirstSuccessor(originalNext, null);
        GraphUtil.killCFG(originalNext);
        replaceAtPredecessor(graph().add(new ReturnNode(result)));
        safeDelete();
    }

    @NodeIntrinsic
    public static native RuntimeException methodReturn(Object result);
}
