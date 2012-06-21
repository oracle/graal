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
 * Allocates some uninitialized area. This is used for TLAB allocation
 * only. If allocation fails, zero/null is produced by this node.
 */
public final class TLABAllocateNode extends FixedWithNextNode implements Lowerable {

    private final int size;

    public TLABAllocateNode(int size, Kind wordKind) {
        super(StampFactory.forKind(wordKind));
        this.size = size;
    }

    public int size() {
        return size;
    }

    @Override
    public void lower(CiLoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    /**
     * @return null if allocation fails
     */
    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static Object allocate(@ConstantNodeParameter int size, @ConstantNodeParameter Kind wordKind) {
        throw new UnsupportedOperationException();
    }
}
