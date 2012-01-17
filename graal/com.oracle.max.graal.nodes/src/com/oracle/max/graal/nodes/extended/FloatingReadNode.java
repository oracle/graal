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

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;


public final class FloatingReadNode extends FloatingAccessNode implements Node.IterableNodeType, LIRLowerable, Canonicalizable {

    @Input private final NodeInputList<Node> dependencies;

    public NodeInputList<Node> dependencies() {
        return dependencies;
    }

    public FloatingReadNode(CiKind kind, ValueNode object, GuardNode guard, LocationNode location, Node... dependencies) {
        super(kind, object, guard, location);
        this.dependencies = new NodeInputList<>(this, dependencies);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitLoad(location().createAddress(gen, object()), location().getValueKind(), getNullCheck()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (object() != null && object().isConstant() && object().kind() == CiKind.Object) {
            if (this.location() == LocationNode.FINAL_LOCATION && location().getClass() == LocationNode.class) {
                Object value = object().asConstant().asObject();
                long displacement = location().displacement();
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
}
