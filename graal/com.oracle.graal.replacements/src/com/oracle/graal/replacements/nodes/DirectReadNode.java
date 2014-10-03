/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.UnsafeAccess.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A special purpose store node that differs from {@link UnsafeStoreNode} in that it is not a
 * {@link StateSplit} and takes a computed address instead of an object.
 */
@NodeInfo
public class DirectReadNode extends FixedWithNextNode implements LIRLowerable {

    @Input protected ValueNode address;
    protected final Kind readKind;

    public static DirectReadNode create(ValueNode address, Kind readKind) {
        return USE_GENERATED_NODES ? new DirectReadNodeGen(address, readKind) : new DirectReadNode(address, readKind);
    }

    protected DirectReadNode(ValueNode address, Kind readKind) {
        super(StampFactory.forKind(readKind.getStackKind()));
        this.address = address;
        this.readKind = readKind;
    }

    protected ValueNode getAddress() {
        return address;
    }

    /**
     * If we are sub it sizes, we try to sign/zero extend the value to at least int as it is done in
     * the {@link com.oracle.graal.replacements.DefaultJavaLoweringProvider#implicitLoadConvert} and
     * {@link com.oracle.graal.replacements.DefaultJavaLoweringProvider#createUnsafeRead}
     *
     * @see com.oracle.graal.replacements.DefaultJavaLoweringProvider#implicitLoadConvert
     * @see com.oracle.graal.replacements.DefaultJavaLoweringProvider#createUnsafeRead
     */
    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind kind = gen.getLIRGeneratorTool().target().getLIRKind(readKind);
        Value loaded = gen.getLIRGeneratorTool().emitLoad(kind, gen.operand(address), null);
        switch ((Kind) kind.getPlatformKind()) {
            case Byte:
                loaded = gen.getLIRGeneratorTool().emitSignExtend(loaded, 8, 32);
                break;
            case Short:
                loaded = gen.getLIRGeneratorTool().emitSignExtend(loaded, 16, 32);
                break;
            case Boolean:
                loaded = gen.getLIRGeneratorTool().emitZeroExtend(loaded, 8, 32);
                break;
            case Char:
                loaded = gen.getLIRGeneratorTool().emitZeroExtend(loaded, 16, 32);
                break;
        }
        gen.setResult(this, loaded);
    }

    @SuppressWarnings("unchecked")
    @NodeIntrinsic
    public static <T> T read(long address, @ConstantNodeParameter Kind kind) {
        if (kind == Kind.Boolean) {
            return (T) Boolean.valueOf(unsafe.getByte(address) != 0);
        }
        if (kind == Kind.Byte) {
            return (T) (Byte) unsafe.getByte(address);
        }
        if (kind == Kind.Short) {
            return (T) (Short) unsafe.getShort(address);
        }
        if (kind == Kind.Char) {
            return (T) (Character) unsafe.getChar(address);
        }
        if (kind == Kind.Int) {
            return (T) (Integer) unsafe.getInt(address);
        }
        if (kind == Kind.Float) {
            return (T) (Float) unsafe.getFloat(address);
        }
        if (kind == Kind.Long) {
            return (T) (Long) unsafe.getLong(address);
        }
        assert kind == Kind.Double;
        return (T) (Double) unsafe.getDouble(address);
    }
}
