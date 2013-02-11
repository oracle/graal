/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.virtual;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

public class BoxedVirtualObjectNode extends VirtualObjectNode implements LIRLowerable, Node.ValueNumberable {

    @Input ValueNode unboxedValue;
    private final ResolvedJavaType type;
    private final Kind kind;

    public BoxedVirtualObjectNode(ResolvedJavaType type, Kind kind, ValueNode unboxedValue) {
        this.type = type;
        this.kind = kind;
        this.unboxedValue = unboxedValue;
    }

    public ValueNode getUnboxedValue() {
        return unboxedValue;
    }

    @Override
    public ResolvedJavaType type() {
        return type;
    }

    @Override
    public int entryCount() {
        return 1;
    }

    @Override
    public String fieldName(int index) {
        assert index == 0;
        return "value";
    }

    @Override
    public int entryIndexForOffset(long constantOffset) {
        // (lstadler) unsafe access to a newly created boxing object should only ever touch the
        // value field
        return 0;
    }

    @Override
    public Kind entryKind(int index) {
        return kind;
    }

    @Override
    public BoxedVirtualObjectNode duplicate() {
        return new BoxedVirtualObjectNode(type, kind, unboxedValue);
    }
}
