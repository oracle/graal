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
package com.oracle.graal.nodes.extended;

import static com.oracle.graal.graph.FieldIntrospection.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Reads an {@linkplain AccessNode accessed} value.
 */
public final class ReadNode extends AccessNode implements Node.IterableNodeType, LIRLowerable, Canonicalizable {

    public ReadNode(ValueNode object, ValueNode location, Stamp stamp) {
        super(object, location, stamp);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitLoad(gen.makeAddress(location(), object()), getNullCheck()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        return canonicalizeRead(this, tool);
    }

    /**
     * Utility function for reading a value of this kind using an object and a displacement.
     *
     * @param object the object from which the value is read
     * @param displacement the displacement within the object in bytes
     * @return the read value encapsulated in a {@link Constant} object
     */
    public static Constant readUnsafeConstant(Kind kind, Object object, long displacement) {
        assert object != null;
        switch (kind) {
            case Boolean:
                return Constant.forBoolean(unsafe.getBoolean(object, displacement));
            case Byte:
                return Constant.forByte(unsafe.getByte(object, displacement));
            case Char:
                return Constant.forChar(unsafe.getChar(object, displacement));
            case Short:
                return Constant.forShort(unsafe.getShort(object, displacement));
            case Int:
                return Constant.forInt(unsafe.getInt(object, displacement));
            case Long:
                return Constant.forLong(unsafe.getLong(object, displacement));
            case Float:
                return Constant.forFloat(unsafe.getFloat(object, displacement));
            case Double:
                return Constant.forDouble(unsafe.getDouble(object, displacement));
            case Object:
                return Constant.forObject(unsafe.getObject(object, displacement));
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static ValueNode canonicalizeRead(Access read, CanonicalizerTool tool) {
        MetaAccessProvider runtime = tool.runtime();
        if (runtime != null && read.object() != null && read.object().isConstant() && read.object().kind() == Kind.Object) {
            if (read.location().locationIdentity() == LocationNode.FINAL_LOCATION && read.location().getClass() == LocationNode.class) {
                Object value = read.object().asConstant().asObject();
                if (value != null) {
                    long displacement = read.location().displacement();
                    Kind kind = read.location().getValueKind();
                    Constant constant = readUnsafeConstant(kind, value, displacement);
                    return ConstantNode.forConstant(constant, runtime, read.node().graph());
                }
            }
        }
        return (ValueNode) read;
    }

    private ReadNode(ValueNode object, ValueNode location) {
        this(object, location, StampFactory.forNodeIntrinsic());
    }

    @NodeIntrinsic
    public static native <T> T readMemory(Object object, Object location);
}
