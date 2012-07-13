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


public final class ReadNode extends AccessNode implements Node.IterableNodeType, LIRLowerable/*, Canonicalizable*/ {

    public ReadNode(ValueNode object, LocationNode location, Stamp stamp) {
        super(object, location, stamp);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitLoad(gen.makeAddress(location(), object()), getNullCheck()));
    }

    // Canonicalization disabled untill we have a solution for non-Object oops in Hotspot
    /*@Override
    public ValueNode canonical(CanonicalizerTool tool) {
        return canonicalizeRead(this, tool);
    }*/

    public static ValueNode canonicalizeRead(Access read, CanonicalizerTool tool) {
        MetaAccessProvider runtime = tool.runtime();
        if (runtime != null && read.object() != null && read.object().isConstant() && read.object().kind() == Kind.Object) {
            if (read.location().locationIdentity() == LocationNode.FINAL_LOCATION && read.location().getClass() == LocationNode.class) {
                Object value = read.object().asConstant().asObject();
                long displacement = read.location().displacement();
                Kind kind = read.location().getValueKind();
                Constant constant = kind.readUnsafeConstant(value, displacement);
                if (constant != null) {
                    System.out.println("Canon read to " + constant);
                    return ConstantNode.forConstant(constant, runtime, read.node().graph());
                }
            }
        }
        return (ValueNode) read;
    }
}
