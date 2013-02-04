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

    private ReadNode(ValueNode object, int displacement, Object locationIdentity, Kind kind) {
        super(object, object.graph().add(new LocationNode(locationIdentity, kind, displacement)), StampFactory.forKind(kind));
    }

    private ReadNode(ValueNode object, ValueNode location) {
        // Used by node intrinsics. Since the initial value for location is a parameter, i.e., a
        // LocalNode, the
        // constructor cannot use the declared type LocationNode
        this(object, location, StampFactory.forNodeIntrinsic());
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitLoad(gen.makeAddress(location(), object()), getNullCheck()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        return canonicalizeRead(this, tool);
    }

    public static ValueNode canonicalizeRead(Access read, CanonicalizerTool tool) {
        MetaAccessProvider runtime = tool.runtime();
        if (runtime != null && read.object() != null && read.object().isConstant()) {
            if (read.location().locationIdentity() == LocationNode.FINAL_LOCATION && read.location().getClass() == LocationNode.class) {
                long displacement = read.location().displacement();
                Kind kind = read.location().getValueKind();
                if (read.object().kind() == Kind.Object) {
                    Object base = read.object().asConstant().asObject();
                    if (base != null) {
                        Constant constant = tool.runtime().readUnsafeConstant(kind, base, displacement);
                        if (constant != null) {
                            return ConstantNode.forConstant(constant, runtime, read.node().graph());
                        }
                    }
                } else if (read.object().kind() == Kind.Long || read.object().kind().getStackKind() == Kind.Int) {
                    long base = read.object().asConstant().asLong();
                    if (base != 0L) {
                        Constant constant = tool.runtime().readUnsafeConstant(kind, null, base + displacement);
                        if (constant != null) {
                            return ConstantNode.forConstant(constant, runtime, read.node().graph());
                        }
                    }
                }
            }
        }
        return (ValueNode) read;
    }

    /**
     * Reads a value from memory.
     * 
     * @param base the base pointer for the memory access
     * @param displacement the displacement of the access
     * @param locationIdentity the identity of the access
     * @param kind the kind of the value read
     * @return the value read from memory
     */
    @NodeIntrinsic(setStampFromReturnType = true)
    public static native <T> T read(Object base, @ConstantNodeParameter int displacement, @ConstantNodeParameter Object locationIdentity, @ConstantNodeParameter Kind kind);
}
