/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.JavaKind;

/**
 * Since SVM specializes behaviour on call sites, we need to differentiate the inlined call sites
 * which should not contain this specialized code.
 *
 * This node is a placeholder to be replaced with a {@code true} or {@code false} constant matching
 * the corresponding inlining decision. The inlining decision is made on the invoke which is
 * connected to this node with data flow through i.e. the {@link InlineDecisionInjectNode} wrapping
 * the arguments of the call whose inlining decision has this node as input.
 *
 * This is the expected situation in the graph.
 *
 * <pre>
 *     InlineDecisionNode------------------------
 *         |                                    |
 *     IntegerEqualsNode------C(0)              |
 *         |                                    |
 *     IfNode  ------------------         InlineDecisionInjectNode
 *         |                    |               |
 *     BeginNode             BeginNode    MethodCallTargetNode
 *         |                    |               |
 *    [Nodes handling  ]       Invoke!#callBoundary
 *    [non inlined case]      [this is where the inlining happens]
 *       ...                   ...
 *         |                    |
 *         -------      ---------
 *                |     |
 *               MergeNode
 * </pre>
 */
@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
public final class InlineDecisionNode extends ValueNode implements IterableNodeType {

    public static final NodeClass<InlineDecisionNode> TYPE = NodeClass.create(InlineDecisionNode.class);

    protected InlineDecisionNode() {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
    }

    public static InlineDecisionNode create() {
        return new InlineDecisionNode();
    }

    public void inlined() {
        replaceWith(true);
    }

    public void notInlined() {
        replaceWith(false);
    }

    private void replaceWith(boolean b) {
        replaceAtUsagesAndDelete(graph().unique(ConstantNode.forBoolean(b)));
    }
}
