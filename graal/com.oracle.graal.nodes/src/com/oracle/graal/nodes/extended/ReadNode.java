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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;


public final class ReadNode extends AccessNode implements Node.IterableNodeType, LIRLowerable, Canonicalizable {

    public ReadNode(ValueNode object, LocationNode location, Stamp stamp) {
        super(object, location, stamp);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitLoad(gen.makeAddress(location(), object()), getNullCheck()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        CodeCacheProvider runtime = tool.runtime();
        if (runtime != null && object() != null && object().isConstant() && object().kind() == Kind.Object) {
            if (location() == LocationNode.FINAL_LOCATION && location().getClass() == LocationNode.class) {
                Object value = object().asConstant().asObject();
                long displacement = location().displacement();
                Kind kind = location().kind();
                Constant constant = kind.readUnsafeConstant(value, displacement);
                if (constant != null) {
                    return ConstantNode.forCiConstant(constant, runtime, graph());
                }
            }
        }
        return this;
    }
}
