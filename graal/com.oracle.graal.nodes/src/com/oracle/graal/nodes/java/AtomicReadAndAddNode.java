/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import static com.oracle.graal.nodeinfo.InputType.Association;
import static com.oracle.graal.nodeinfo.InputType.Memory;
import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_10;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_3;

import com.oracle.graal.compiler.common.LocationIdentity;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.memory.AbstractMemoryCheckpoint;
import com.oracle.graal.nodes.memory.MemoryCheckpoint;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import sun.misc.Unsafe;

/**
 * Represents an atomic read-and-add operation like {@link Unsafe#getAndAddInt(Object, long, int)}.
 */
@NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_10, size = SIZE_3)
public final class AtomicReadAndAddNode extends AbstractMemoryCheckpoint implements LIRLowerable, MemoryCheckpoint.Single {

    public static final NodeClass<AtomicReadAndAddNode> TYPE = NodeClass.create(AtomicReadAndAddNode.class);
    @Input(Association) AddressNode address;
    @Input ValueNode delta;

    protected final LocationIdentity locationIdentity;

    public AtomicReadAndAddNode(AddressNode address, ValueNode delta, LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(delta.getStackKind()));
        this.address = address;
        this.delta = delta;
        this.locationIdentity = locationIdentity;
    }

    public ValueNode delta() {
        return delta;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitAtomicReadAndAdd(gen.operand(address), gen.operand(delta));
        gen.setResult(this, result);
    }
}
