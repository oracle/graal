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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Initializes the header and body of an uninitialized array cell. This node calls out to a stub to
 * do both the allocation and formatting if the memory address it is given is zero/null (e.g. due to
 * {@linkplain TLABAllocateNode TLAB allocation} failing).
 */
public final class InitializeArrayNode extends FixedWithNextNode implements Lowerable, ArrayLengthProvider {

    @Input private final ValueNode memory;
    @Input private final ValueNode length;
    @Input private final ValueNode allocationSize;
    private final ResolvedJavaType type;
    private final boolean fillContents;
    private final boolean locked;

    public InitializeArrayNode(ValueNode memory, ValueNode length, ValueNode allocationSize, ResolvedJavaType type, boolean fillContents, boolean locked) {
        super(StampFactory.exactNonNull(type));
        this.memory = memory;
        this.type = type;
        this.length = length;
        this.allocationSize = allocationSize;
        this.fillContents = fillContents;
        this.locked = locked;
    }

    public ValueNode memory() {
        return memory;
    }

    @Override
    public ValueNode length() {
        return length;
    }

    /**
     * Gets the size (in bytes) of the memory chunk allocated for the array.
     */
    public ValueNode allocationSize() {
        return allocationSize;
    }

    public ResolvedJavaType type() {
        return type;
    }

    public boolean fillContents() {
        return fillContents;
    }

    public boolean locked() {
        return locked;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @NodeIntrinsic
    public static native Object initialize(Object memory, int length, int allocationSize, @ConstantNodeParameter
    ResolvedJavaType type, @ConstantNodeParameter
    boolean fillContents, @ConstantNodeParameter
    boolean locked);
}
