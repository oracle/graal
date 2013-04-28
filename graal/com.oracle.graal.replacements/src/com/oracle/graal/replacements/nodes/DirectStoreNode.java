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
package com.oracle.graal.replacements.nodes;

import static com.oracle.graal.graph.UnsafeAccess.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A special purpose store node that differs from {@link UnsafeStoreNode} in that it is not a
 * {@link StateSplit} and takes a computed address instead of an object.
 */
public class DirectStoreNode extends FixedWithNextNode implements LIRLowerable {

    @Input private ValueNode address;
    @Input private ValueNode value;
    private final Kind kind;

    public DirectStoreNode(ValueNode address, ValueNode value, Kind kind) {
        super(StampFactory.forVoid());
        this.address = address;
        this.value = value;
        this.kind = kind;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        Value v = gen.operand(value);
        gen.emitStore(kind, gen.operand(address), v, null);
    }

    /*
     * The kind of the store is provided explicitly in these intrinsics because it is not always
     * possible to determine the kind from the given value during compilation (because stack kinds
     * are used).
     */

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(long address, boolean value, @ConstantNodeParameter Kind kind) {
        int b = value ? 1 : 0;
        unsafe.putByte(address, (byte) b);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(long address, byte value, @ConstantNodeParameter Kind kind) {
        unsafe.putByte(address, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(long address, short value, @ConstantNodeParameter Kind kind) {
        unsafe.putShort(address, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(long address, char value, @ConstantNodeParameter Kind kind) {
        unsafe.putChar(address, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(long address, int value, @ConstantNodeParameter Kind kind) {
        unsafe.putInt(address, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(long address, long value, @ConstantNodeParameter Kind kind) {
        unsafe.putLong(address, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(long address, float value, @ConstantNodeParameter Kind kind) {
        unsafe.putFloat(address, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(long address, double value, @ConstantNodeParameter Kind kind) {
        unsafe.putDouble(address, value);
    }
}
