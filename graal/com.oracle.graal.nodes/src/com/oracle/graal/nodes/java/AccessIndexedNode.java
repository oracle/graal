/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code AccessIndexedNode} class is the base class of instructions that read or write elements
 * of an array.
 */
public abstract class AccessIndexedNode extends AccessArrayNode implements Lowerable {

    @Input private ValueNode index;
    private final Kind elementKind;

    public ValueNode index() {
        return index;
    }

    /**
     * Create an new AccessIndexedNode.
     * 
     * @param stamp the result kind of the access
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param elementKind the kind of the elements of the array
     */
    protected AccessIndexedNode(Stamp stamp, ValueNode array, ValueNode index, Kind elementKind) {
        super(stamp, array);
        this.index = index;
        this.elementKind = elementKind;
    }

    /**
     * Gets the element type of the array.
     * 
     * @return the element type
     */
    public Kind elementKind() {
        return elementKind;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }
}
