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
import com.oracle.graal.snippets.*;

/**
 * Allocates some uninitialized area. This is used for TLAB allocation
 * only. If allocation fails, zero/null is produced by this node.
 */
public final class TLABAllocateNode extends FixedWithNextNode implements Lowerable {

    private final int size;
    @Input private ValueNode sizeNode;

    public TLABAllocateNode(int size, Kind wordKind) {
        super(StampFactory.forWord(wordKind, true));
        this.size = size;
        this.sizeNode = null;
    }

    public TLABAllocateNode(Kind wordKind, ValueNode size) {
        super(StampFactory.forWord(wordKind, true));
        this.size = -1;
        this.sizeNode = size;
    }

    public boolean isSizeConstant() {
        return sizeNode == null;
    }

    public int constantSize() {
        assert isSizeConstant();
        return size;
    }

    public ValueNode variableSize() {
        assert !isSizeConstant();
        return sizeNode;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    /**
     * @return null if allocation fails
     */
    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static Word allocateConstantSize(@ConstantNodeParameter int size, @ConstantNodeParameter Kind wordKind) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return null if allocation fails
     */
    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static Word allocateVariableSize(@ConstantNodeParameter Kind wordKind, int size) {
        throw new UnsupportedOperationException();
    }
}
