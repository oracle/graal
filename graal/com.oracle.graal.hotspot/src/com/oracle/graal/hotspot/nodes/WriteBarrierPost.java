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

import static com.oracle.graal.hotspot.replacements.HotSpotSnippetUtils.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

public class WriteBarrierPost extends WriteBarrier implements Lowerable {

    @Input private ValueNode object;
    @Input private ValueNode value;
    @Input private ValueNode index;
    @Input private LocationNode location;
    private final boolean precise;

    public ValueNode getObject() {
        return object;
    }

    public ValueNode getValue() {
        return value;
    }

    public LocationNode getLocation() {
        return location;
    }

    public boolean usePrecise() {
        return precise;
    }

    public WriteBarrierPost() {
        this.precise = false;
    }

    public WriteBarrierPost(ValueNode object, ValueNode value, ValueNode index) {
        this.object = object;
        this.value = value;
        this.index = index;
        this.precise = true;
        this.location = null;
    }

    public WriteBarrierPost(ValueNode object, ValueNode value, LocationNode location, boolean precise) {
        this.object = object;
        this.value = value;
        this.location = location;
        this.precise = precise;
    }

    @Override
    public void lower(LoweringTool generator) {
        StructuredGraph graph = (StructuredGraph) this.graph();
        if (location == null) { // Come from array copy intrinsic
            LocationNode arrayLocation = IndexedLocationNode.create(LocationNode.getArrayLocation(Kind.Object), Kind.Object, arrayBaseOffset(Kind.Object), index, graph, arrayIndexScale(Kind.Object));
            if (useG1GC()) {
                graph.replaceFixedWithFixed(this, graph().add(new G1WriteBarrierPost(object, value, arrayLocation, true)));
            } else {
                graph.replaceFixedWithFixed(this, graph().add(new SerialWriteBarrierPost(object, arrayLocation, true)));
            }
        } else { // Normal WriteBarrier
            assert location != null;
            if (useG1GC()) {
                graph.replaceFixedWithFixed(this, graph().add(new G1WriteBarrierPost(object, value, location, precise)));
            } else {
                graph.replaceFixedWithFixed(this, graph().add(new SerialWriteBarrierPost(object, location, precise)));
            }
        }
    }

    @NodeIntrinsic
    public static native void arrayCopyWriteBarrier(Object array, Object value, int index);
}
