/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A value proxy that is inserted in the frame state of a loop exit for any value that is created
 * inside the loop (i.e. was not live on entry to the loop) and is (potentially) used after the
 * loop.
 */
@NodeInfo(nameTemplate = "{p#type/s}Proxy")
public class ProxyNode extends FloatingNode implements IterableNodeType, ValueNumberable, Canonicalizable, Virtualizable, ValueProxy, GuardingNode {

    @Input(notDataflow = true) private AbstractBeginNode proxyPoint;
    @Input private ValueNode value;
    private final PhiType type;

    public ProxyNode(ValueNode value, AbstractBeginNode exit, PhiType type) {
        super(type == PhiType.Value ? value.stamp() : type.stamp);
        this.type = type;
        assert exit != null;
        this.proxyPoint = exit;
        this.value = value;
    }

    public ValueNode value() {
        return value;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(value.stamp());
    }

    public AbstractBeginNode proxyPoint() {
        return proxyPoint;
    }

    public PhiType type() {
        return type;
    }

    @Override
    public boolean verify() {
        assert value != null;
        assert proxyPoint != null;
        assert !(value instanceof ProxyNode) || ((ProxyNode) value).proxyPoint != proxyPoint;
        return super.verify();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (type == PhiType.Value && value.isConstant()) {
            return value;
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (type == PhiType.Value) {
            State state = tool.getObjectState(value);
            if (state != null && state.getState() == EscapeState.Virtual) {
                tool.replaceWithVirtual(state.getVirtualObject());
            }
        }
    }

    public static ProxyNode forGuard(ValueNode value, AbstractBeginNode exit, StructuredGraph graph) {
        return graph.unique(new ProxyNode(value, exit, PhiType.Guard));
    }

    public static ProxyNode forValue(ValueNode value, AbstractBeginNode exit, StructuredGraph graph) {
        return graph.unique(new ProxyNode(value, exit, PhiType.Value));
    }

    @Override
    public ValueNode getOriginalValue() {
        return value;
    }
}
