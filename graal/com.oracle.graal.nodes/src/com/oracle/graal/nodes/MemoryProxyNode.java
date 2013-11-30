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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

public class MemoryProxyNode extends ProxyNode implements MemoryProxy, LIRLowerable {

    private final LocationIdentity identity;

    public MemoryProxyNode(ValueNode value, AbstractBeginNode exit, LocationIdentity identity) {
        super(value, exit, PhiType.Memory);
        assert value instanceof MemoryNode;
        this.identity = identity;
    }

    public LocationIdentity getLocationIdentity() {
        return identity;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
    }

    @Override
    public boolean verify() {
        assert value() instanceof MemoryNode;
        return super.verify();
    }

    public static MemoryProxyNode forMemory(MemoryNode value, AbstractBeginNode exit, LocationIdentity location, StructuredGraph graph) {
        return graph.unique(new MemoryProxyNode(ValueNodeUtil.asNode(value), exit, location));
    }

    public MemoryNode getOriginalMemoryNode() {
        return (MemoryNode) value();
    }

    public MemoryCheckpoint asMemoryCheckpoint() {
        return getOriginalMemoryNode().asMemoryCheckpoint();
    }

    public MemoryPhiNode asMemoryPhi() {
        return getOriginalMemoryNode().asMemoryPhi();
    }
}
