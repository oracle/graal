/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.ValueProxy;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

@NodeInfo(nameTemplate = "ValueProxy({i#value})")
public final class ValueProxyNode extends ProxyNode implements Canonicalizable, Virtualizable, ValueProxy {

    public static final NodeClass<ValueProxyNode> TYPE = NodeClass.create(ValueProxyNode.class);
    @Input ValueNode value;

    public ValueProxyNode(ValueNode value, LoopExitNode loopExit) {
        super(TYPE, value.stamp(NodeView.DEFAULT), loopExit);
        this.value = value;
    }

    @Override
    public ValueNode value() {
        return value;
    }

    @Override
    public PhiNode createPhi(AbstractMergeNode merge) {
        /*
         * use the unrestricted stamp since not all future inputs are available, and thus
         * this.stamp() can be too precise (causing future transformations based on a wrong phi
         * stamp)
         */
        return graph().addWithoutUnique(new ValuePhiNode(stamp(NodeView.DEFAULT).unrestricted(), merge));
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(value.stamp(NodeView.DEFAULT));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Node result = super.canonical(tool);
        if (result != this) {
            return result;
        }

        ValueNode curValue = value;
        if (curValue.getNodeClass().isLeafNode()) {
            return curValue;
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(value);
        if (alias instanceof VirtualObjectNode) {
            tool.replaceWithVirtual((VirtualObjectNode) alias);
        }
    }

    @Override
    public ValueNode getOriginalNode() {
        return value();
    }

    @Override
    public GuardingNode getGuard() {
        return this.proxyPoint();
    }

    @Override
    public ProxyNode duplicateOn(LoopExitNode newProxyPoint, ValueNode newOriginalNode) {
        return graph().addWithoutUnique(new ValueProxyNode(newOriginalNode, newProxyPoint));
    }
}
