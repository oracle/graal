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
package com.oracle.graal.nodes;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public abstract class WriteBarrier extends FixedWithNextNode implements Lowerable, Node.IterableNodeType {

    @Input private ValueNode object;
    @Input private LocationNode location;
    private final boolean precise;

    public ValueNode getObject() {
        return object;
    }

    public LocationNode getLocation() {
        return location;
    }

    public boolean usePrecise() {
        return precise;
    }

    public WriteBarrier(ValueNode object, LocationNode location, boolean precise) {
        super(StampFactory.forVoid());
        this.object = object;
        this.location = location;
        this.precise = precise;
    }

    @Override
    public void lower(LoweringTool generator, LoweringType loweringType) {
        assert loweringType == LoweringType.AFTER_FSA;
        generator.getRuntime().lower(this, generator);
    }
}
