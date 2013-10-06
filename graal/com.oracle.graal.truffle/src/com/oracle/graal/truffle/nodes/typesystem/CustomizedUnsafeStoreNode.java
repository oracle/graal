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
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

public final class CustomizedUnsafeStoreNode extends UnsafeStoreNode {

    @Input private ValueNode customLocationIdentity;

    public CustomizedUnsafeStoreNode(ValueNode object, ValueNode offset, ValueNode value, Kind accessKind, ValueNode customLocationIdentity) {
        super(object, 0, offset, value, accessKind);
        this.customLocationIdentity = customLocationIdentity;
    }

    public ValueNode getCustomLocationIdentity() {
        return customLocationIdentity;
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
    public static void store(Object object, long offset, Object value, @ConstantNodeParameter Kind kind, Object customLocationIdentity) {
        UnsafeStoreNode.store(object, 0, offset, value, kind);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, boolean value, @ConstantNodeParameter Kind kind, Object customLocationIdentity) {
        UnsafeStoreNode.store(object, 0, offset, value, kind);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, byte value, @ConstantNodeParameter Kind kind, Object customLocationIdentity) {
        UnsafeStoreNode.store(object, 0, offset, value, kind);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, char value, @ConstantNodeParameter Kind kind, Object customLocationIdentity) {
        UnsafeStoreNode.store(object, 0, offset, value, kind);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, double value, @ConstantNodeParameter Kind kind, Object customLocationIdentity) {
        UnsafeStoreNode.store(object, 0, offset, value, kind);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, float value, @ConstantNodeParameter Kind kind, Object customLocationIdentity) {
        UnsafeStoreNode.store(object, 0, offset, value, kind);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, int value, @ConstantNodeParameter Kind kind, Object customLocationIdentity) {
        UnsafeStoreNode.store(object, 0, offset, value, kind);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, long value, @ConstantNodeParameter Kind kind, Object customLocationIdentity) {
        UnsafeStoreNode.store(object, 0, offset, value, kind);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, short value, @ConstantNodeParameter Kind kind, Object customLocationIdentity) {
        UnsafeStoreNode.store(object, 0, offset, value, kind);
    }
}
