/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ValueNumberable;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Proxy;

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
    public boolean verifyNode() {
        assert !(value() instanceof ProxyNode) || ((ProxyNode) value()).loopExit != loopExit : Assertions.errorMessageContext("this", this, "value", value());
        return super.verifyNode();
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
