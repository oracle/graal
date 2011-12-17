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

import sun.misc.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public final class ReadNode extends AccessNode implements Node.ValueNumberable, Node.IterableNodeType, LIRLowerable, Canonicalizable {

    public ReadNode(CiKind kind, ValueNode object, LocationNode location) {
        super(kind, object, location);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitLoad(location().createAddress(gen, object()), location().getValueKind(), getNullCheck()));
    }


    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (object() != null && this.object().isConstant() && this.object().kind() == CiKind.Object) {
            if (this.location() == LocationNode.FINAL_LOCATION && this.location().getClass() == LocationNode.class) {
                Object value = this.object().asConstant().asObject();
                long displacement = this.location().displacement();
                CiKind kind = location().kind();
                RiRuntime runtime = tool.runtime();
                CiConstant constant = kind.readUnsafeConstant(value, displacement);
                if (constant != null) {
                    return ConstantNode.forCiConstant(constant, runtime, graph());
                }
            }
        }

        return this;
    }



    public ConstantNode readUnsafeConstant(Object value, long displacement, CiKind kind, RiRuntime runtime) {
        Unsafe u = Unsafe.getUnsafe();
        switch(kind) {
            case Boolean:
                return ConstantNode.forBoolean(u.getBoolean(value, displacement), graph());
            case Byte:
                return ConstantNode.forByte(u.getByte(value, displacement), graph());
            case Char:
                return ConstantNode.forChar(u.getChar(value, displacement), graph());
            case Short:
                return ConstantNode.forShort(u.getShort(value, displacement), graph());
            case Int:
                return ConstantNode.forInt(u.getInt(value, displacement), graph());
            case Long:
                return ConstantNode.forLong(u.getLong(value, displacement), graph());
            case Float:
                return ConstantNode.forFloat(u.getFloat(value, displacement), graph());
            case Double:
                return ConstantNode.forDouble(u.getDouble(value, displacement), graph());
            case Object:
                return ConstantNode.forObject(u.getObject(value, displacement), runtime, graph());
            default:
                assert false : "unexpected kind: " + kind;
                return null;
        }
    }
}
