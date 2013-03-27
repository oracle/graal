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

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

public class WriteBarrierPre extends WriteBarrier implements Lowerable {

    @Input private ValueNode object;
    @Input private LocationNode location;
    @Input private ValueNode expectedObject;
    private final boolean doLoad;

    public ValueNode getObject() {
        return object;
    }

    public ValueNode getExpectedObject() {
        return expectedObject;
    }

    public boolean doLoad() {
        return doLoad;
    }

    public LocationNode getLocation() {
        return location;
    }

    public WriteBarrierPre() {
        this.doLoad = false;
    }

    public WriteBarrierPre(ValueNode object, ValueNode expectedObject, LocationNode location, boolean doLoad) {
        this.object = object;
        this.doLoad = doLoad;
        this.location = location;
        this.expectedObject = expectedObject;
    }

    @Override
    public void lower(LoweringTool generator) {
        StructuredGraph graph = (StructuredGraph) this.graph();
        if (useG1GC()) {
            graph.replaceFixedWithFixed(this, graph().add(new G1WriteBarrierPre(getObject(), getExpectedObject(), getLocation(), doLoad())));
        } else {
            graph.removeFixed(this);
        }
    }

}
