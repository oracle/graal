/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(allowedUsageTypes = {InputType.Memory})
public class MemoryProxyNode extends ProxyNode implements MemoryProxy, LIRLowerable {

    @Input(InputType.Memory) private MemoryNode value;
    private final LocationIdentity identity;

    public MemoryProxyNode(MemoryNode value, BeginNode exit, LocationIdentity identity) {
        super(StampFactory.forVoid(), exit);
        this.value = value;
        this.identity = identity;
    }

    @Override
    public ValueNode value() {
        return value.asNode();
    }

    public LocationIdentity getLocationIdentity() {
        return identity;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
    }

    @Override
    public boolean verify() {
        assert value() instanceof MemoryNode : this + " " + value();
        return super.verify();
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

    public Node getOriginalNode() {
        return value.asNode();
    }
}
