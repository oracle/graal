/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes.typesystem;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

public final class CustomizedUnsafeLoadNode extends UnsafeLoadNode {

    @Input private ValueNode condition;
    @Input private ValueNode locationIdentity;

    public CustomizedUnsafeLoadNode(ValueNode object, ValueNode offset, Kind accessKind, ValueNode condition, ValueNode locationIdentity) {
        super(object, 0, offset, accessKind);
        this.condition = condition;
        this.locationIdentity = locationIdentity;
    }

    public ValueNode getCondition() {
        return condition;
    }

    public ValueNode getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        super.virtualize(tool);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static <T> T load(Object object, long offset, @ConstantNodeParameter Kind kind, boolean condition, Object locationIdentity) {
        return UnsafeLoadNode.load(object, 0, offset, kind);
    }
}
