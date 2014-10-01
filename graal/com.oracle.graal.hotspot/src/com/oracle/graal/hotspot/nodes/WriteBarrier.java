/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo
public abstract class WriteBarrier extends FixedWithNextNode implements Lowerable {

    @Input protected ValueNode object;
    @OptionalInput protected ValueNode value;
    @OptionalInput(InputType.Association) protected LocationNode location;
    protected final boolean precise;

    public WriteBarrier(ValueNode object, ValueNode value, LocationNode location, boolean precise) {
        super(StampFactory.forVoid());
        this.object = object;
        this.value = value;
        this.location = location;
        this.precise = precise;
    }

    public ValueNode getValue() {
        return value;
    }

    public ValueNode getObject() {
        return object;
    }

    public LocationNode getLocation() {
        return location;
    }

    public boolean usePrecise() {
        return precise;
    }

    @Override
    public void lower(LoweringTool tool) {
        assert graph().getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA;
        tool.getLowerer().lower(this, tool);
    }
}
