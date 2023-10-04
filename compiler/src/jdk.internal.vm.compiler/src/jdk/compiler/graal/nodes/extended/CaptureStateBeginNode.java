/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.compiler.graal.nodes.extended;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_0;

import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.graph.iterators.NodePredicates;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.AbstractBeginNode;
import jdk.compiler.graal.nodes.BeginNode;
import jdk.compiler.graal.nodes.BeginStateSplitNode;
import jdk.compiler.graal.nodes.LoopExitNode;
import jdk.compiler.graal.nodes.MemoryProxyNode;
import jdk.compiler.graal.nodes.ValueProxyNode;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;

/**
 * An {@linkplain AbstractBeginNode begin node} that can capture the state at a certain program
 * point. This is similar to {@link StateSplitProxyNode} but (a) it does not proxy a value, and (b)
 * as a begin node, it is a valid anchor point for floating guards.
 * <p/>
 *
 * This node is canonicalized away if it no longer has a state (i.e., after frame state assignment).
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public class CaptureStateBeginNode extends BeginStateSplitNode implements Canonicalizable {

    public static final NodeClass<CaptureStateBeginNode> TYPE = NodeClass.create(CaptureStateBeginNode.class);

    public CaptureStateBeginNode() {
        super(TYPE);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (stateAfter() == null) {
            return new BeginNode();
        }
        return this;
    }

    @Override
    public boolean verify() {
        if (predecessor() instanceof LoopExitNode loopExit) {
            /*
             * Must guarantee only value and memory proxies are attached to the loop exit. Anything
             * else should be attached to this node
             */
            assert loopExit.usages().stream().allMatch(NodePredicates.isA(ValueProxyNode.class).or(MemoryProxyNode.class)) : String.format("LoopExit has disallowed usages %s", loopExit);
        }

        return super.verify();
    }
}
