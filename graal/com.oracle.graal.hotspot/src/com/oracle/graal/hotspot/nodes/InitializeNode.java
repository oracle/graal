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
import com.oracle.graal.cri.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Initializes the header and body of an uninitialized object cell.
 * This node calls out to a stub to do both the allocation and formatting
 * if the memory address it is given is zero/null (e.g. due to
 * {@linkplain TLABAllocateNode TLAB allocation} failing).
 */
public final class InitializeNode extends FixedWithNextNode implements Lowerable {

    @Input private final ValueNode memory;
    private final ResolvedJavaType type;

    public InitializeNode(ValueNode memory, ResolvedJavaType type) {
        super(StampFactory.exactNonNull(type));
        this.memory = memory;
        this.type = type;
    }

    public ValueNode memory() {
        return memory;
    }

    public ResolvedJavaType type() {
        return type;
    }

    @Override
    public void lower(CiLoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static Object initialize(Object memory, @ConstantNodeParameter ResolvedJavaType type) {
        throw new UnsupportedOperationException();
    }
}
