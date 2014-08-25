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
package com.oracle.graal.hotspot.nodes;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Reserves a block of memory in the stack frame of a method. The block is reserved in the frame for
 * the entire execution of the associated method.
 */
@NodeInfo
public class AllocaNode extends FixedWithNextNode implements LIRLowerable {

    /**
     * The number of slots in block.
     */
    private final int slots;

    /**
     * The indexes of the object pointer slots in the block. Each such object pointer slot must be
     * initialized before any safepoint in the method otherwise the garbage collector will see
     * garbage values when processing these slots.
     */
    private final BitSet objects;

    public static AllocaNode create(int slots, BitSet objects) {
        return USE_GENERATED_NODES ? new AllocaNodeGen(slots, objects) : new AllocaNode(slots, objects);
    }

    protected AllocaNode(int slots, BitSet objects) {
        super(StampFactory.forKind(HotSpotGraalRuntime.getHostWordKind()));
        this.slots = slots;
        this.objects = objects;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        StackSlot array = gen.getLIRGeneratorTool().getResult().getFrameMap().allocateStackSlots(slots, objects, null);
        Value result = gen.getLIRGeneratorTool().emitAddress(array);
        gen.setResult(this, result);
    }
}
