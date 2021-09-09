/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Association;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ValueNumberable;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Proxy;
import org.graalvm.word.LocationIdentity;

/**
 * A proxy is inserted at loop exits for any value that is created inside the loop (i.e. was not
 * live on entry to the loop) and is (potentially) used after the loop.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public abstract class ProxyNode extends FloatingNode implements Proxy, ValueNumberable, Canonicalizable {

    public static final NodeClass<ProxyNode> TYPE = NodeClass.create(ProxyNode.class);
    @Input(Association) LoopExitNode loopExit;

    protected ProxyNode(NodeClass<? extends ProxyNode> c, Stamp stamp, LoopExitNode proxyPoint) {
        super(c, stamp);
        assert proxyPoint != null;
        this.loopExit = proxyPoint;
    }

    public abstract ValueNode value();

    public void setProxyPoint(LoopExitNode newProxyPoint) {
        this.updateUsages(loopExit, newProxyPoint);
        this.loopExit = newProxyPoint;
    }

    public LoopExitNode proxyPoint() {
        return loopExit;
    }

    @Override
    public ValueNode getOriginalNode() {
        return value();
    }

    @Override
    public boolean verify() {
        assert !(value() instanceof ProxyNode) || ((ProxyNode) value()).loopExit != loopExit;
        return super.verify();
    }

    public static ValueProxyNode forValue(ValueNode value, LoopExitNode exit) {
        return exit.graph().unique(new ValueProxyNode(value, exit));
    }

    public static GuardProxyNode forGuard(GuardingNode value, LoopExitNode exit) {
        return exit.graph().unique(new GuardProxyNode(value, exit));
    }

    public static MemoryProxyNode forMemory(MemoryKill value, LoopExitNode exit, LocationIdentity locationIdentity) {
        return exit.graph().unique(new MemoryProxyNode(value, exit, locationIdentity));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value() == null) {
            return null;
        }
        return this;
    }

    public abstract PhiNode createPhi(AbstractMergeNode merge);

    public abstract ProxyNode duplicateOn(LoopExitNode newProxyPoint, ValueNode newOriginalNode);
}
