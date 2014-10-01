/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Represents an atomic read-and-add operation like {@link Unsafe#getAndAddInt(Object, long, int)}.
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory})
public class AtomicReadAndAddNode extends AbstractMemoryCheckpoint implements LIRLowerable, MemoryCheckpoint.Single {

    @Input ValueNode object;
    @Input ValueNode offset;
    @Input ValueNode delta;

    protected final LocationIdentity locationIdentity;

    public static AtomicReadAndAddNode create(ValueNode object, ValueNode offset, ValueNode delta, LocationIdentity locationIdentity) {
        return USE_GENERATED_NODES ? new AtomicReadAndAddNodeGen(object, offset, delta, locationIdentity) : new AtomicReadAndAddNode(object, offset, delta, locationIdentity);
    }

    protected AtomicReadAndAddNode(ValueNode object, ValueNode offset, ValueNode delta, LocationIdentity locationIdentity) {
        super(StampFactory.forKind(delta.getKind()));
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
        LocationNode location = IndexedLocationNode.create(getLocationIdentity(), delta.getKind(), 0, offset, graph(), 1);
        Value address = location.generateAddress(gen, gen.getLIRGeneratorTool(), gen.operand(object()));
        Value result = gen.getLIRGeneratorTool().emitAtomicReadAndAdd(address, gen.operand(delta));
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static int getAndAddInt(Object object, long offset, int delta, @ConstantNodeParameter @SuppressWarnings("unused") LocationIdentity locationIdentity) {
        return unsafe.getAndAddInt(object, offset, delta);
    }

    @NodeIntrinsic
    public static long getAndAddLong(Object object, long offset, long delta, @ConstantNodeParameter @SuppressWarnings("unused") LocationIdentity locationIdentity) {
        return unsafe.getAndAddLong(object, offset, delta);
    }
}
