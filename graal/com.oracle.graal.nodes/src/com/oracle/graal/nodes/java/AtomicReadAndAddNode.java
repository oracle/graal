/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Represents an atomic read-and-add operation like {@link Unsafe#getAndAddInt(Object, long, int)}.
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory})
public final class AtomicReadAndAddNode extends AbstractMemoryCheckpoint implements LIRLowerable, MemoryCheckpoint.Single {

    public static final NodeClass<AtomicReadAndAddNode> TYPE = NodeClass.create(AtomicReadAndAddNode.class);
    @Input ValueNode object;
    @Input ValueNode offset;
    @Input ValueNode delta;

    protected final LocationIdentity locationIdentity;

    public AtomicReadAndAddNode(ValueNode object, ValueNode offset, ValueNode delta, LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(delta.getKind()));
        this.object = object;
        this.offset = offset;
        this.delta = delta;
        this.locationIdentity = locationIdentity;
    }

    public ValueNode object() {
        return object;
    }

    public ValueNode offset() {
        return offset;
    }

    public ValueNode delta() {
        return delta;
    }

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    public void generate(NodeLIRBuilderTool gen) {
        LocationNode location = graph().unique(new IndexedLocationNode(getLocationIdentity(), 0, offset, 1));
        Value address = location.generateAddress(gen, gen.getLIRGeneratorTool(), gen.operand(object()));
        Value result = gen.getLIRGeneratorTool().emitAtomicReadAndAdd(address, gen.operand(delta));
        gen.setResult(this, result);
    }
}
