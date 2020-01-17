/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import com.oracle.svm.core.stack.JavaFrameAnchor;

/**
 * We use this class to transport some data from the {@link CFunctionPrologueNode} to the
 * {@link InvokeNode}. This is necessary because the prologue of a C function is decoupled from the
 * actual invocation of the C function even though the backend needs information about the prologue
 * when emitting the call.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
public final class CFunctionPrologueDataNode extends ValueNode implements CPrologueData, LIRLowerable {
    public static final NodeClass<CFunctionPrologueDataNode> TYPE = NodeClass.create(CFunctionPrologueDataNode.class);

    @Input ValueNode frameAnchor;
    private final int newThreadStatus;

    public CFunctionPrologueDataNode(ValueNode frameAnchor, int newThreadStatus) {
        super(TYPE, frameAnchor.stamp(NodeView.DEFAULT));
        this.frameAnchor = frameAnchor;
        this.newThreadStatus = newThreadStatus;
    }

    public ValueNode frameAnchor() {
        return frameAnchor;
    }

    public int getNewThreadStatus() {
        return newThreadStatus;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // nothing to do
    }

    @NodeIntrinsic
    public static native CPrologueData cFunctionPrologueData(JavaFrameAnchor frameAnchor, @ConstantNodeParameter int newThreadStatus);
}
