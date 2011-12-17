/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes.extended;

import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;

/**
 * Load of a value from a location specified as an offset relative to an object.
 */
public class UnsafeLoadNode extends AbstractStateSplit implements Lowerable, Node.ValueNumberable {

    @Input private ValueNode object;
    @Input private ValueNode offset;
    @Data private final int displacement;
    @Data private final CiKind loadKind;

    public ValueNode object() {
        return object;
    }

    public int displacement() {
        return displacement;
    }

    public ValueNode offset() {
        return offset;
    }

    public UnsafeLoadNode(ValueNode object, ValueNode offset, CiKind kind) {
        this(object, 0, offset, kind);
    }

    public UnsafeLoadNode(ValueNode object, int displacement, ValueNode offset, CiKind kind) {
        super(StampFactory.forKind(kind.stackKind()));
        this.object = object;
        this.displacement = displacement;
        this.offset = offset;
        this.loadKind = kind;
    }

    public CiKind loadKind() {
        return loadKind;
    }

    @Override
    public void lower(CiLoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @NodeIntrinsic
    public static <T> T load(Object object, long offset, @ConstantNodeParameter CiKind kind) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }
}
