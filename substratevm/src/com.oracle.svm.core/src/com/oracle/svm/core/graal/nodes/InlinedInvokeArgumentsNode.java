/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_0;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.graph.NodeInputList;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.NodeSize;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.ValueNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This node is used to temporarily track the arguments to a call which was inlined before analysis
 * so that its parameter flows can be passed to other methods. The node itself will be deleted
 * during AnalysisStrengthenGraphsPhase.
 */
@NodeInfo(cycles = CYCLES_0, size = NodeSize.SIZE_0)
public class InlinedInvokeArgumentsNode extends FixedWithNextNode {
    public static final NodeClass<InlinedInvokeArgumentsNode> TYPE = NodeClass.create(InlinedInvokeArgumentsNode.class);

    @Input protected NodeInputList<ValueNode> arguments;
    private final ResolvedJavaMethod targetMethod;

    @SuppressWarnings("this-escape")
    public InlinedInvokeArgumentsNode(ResolvedJavaMethod targetMethod, ValueNode[] arguments) {
        super(TYPE, StampFactory.forVoid());
        this.targetMethod = targetMethod;
        this.arguments = new NodeInputList<>(this, arguments);
    }

    public ResolvedJavaMethod getInvokeTarget() {
        return targetMethod;
    }

    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }
}
