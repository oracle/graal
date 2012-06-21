/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.cri.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code UnsafeCastNode} produces the same value as its input, but with a different type.
 */
public final class UnsafeCastNode extends FloatingNode implements Canonicalizable, Lowerable, LIRLowerable {

    @Input private ValueNode object;
    private ResolvedJavaType toType;

    public ValueNode object() {
        return object;
    }

    public UnsafeCastNode(ValueNode object, ResolvedJavaType toType) {
        super(toType.kind().isObject() ? StampFactory.declared(toType, object.stamp().nonNull()) : StampFactory.forKind(toType.kind()));
        this.object = object;
        this.toType = toType;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (object != null && object.kind().isObject() && object.objectStamp().type() != null && object.objectStamp().type().isSubtypeOf(toType)) {
            return object;
        }
        return this;
    }


    @Override
    public void lower(CiLoweringTool tool) {
        if (object.kind() == kind()) {
            ((StructuredGraph) graph()).replaceFloating(this, object);
        } else {
            // Cannot remove an unsafe cast between two different kinds
        }
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static <T> T cast(Object object, @ConstantNodeParameter Class<?> toType) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        Value result = generator.newVariable(kind());
        generator.emitMove(generator.operand(object), result);
        generator.setResult(this, result);
    }
}
