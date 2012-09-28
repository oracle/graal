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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code UnsafeCastNode} produces the same value as its input, but with a different type.
 */
public final class UnsafeCastNode extends FloatingNode implements Canonicalizable, LIRLowerable {

    @Input private ValueNode object;
    private ResolvedJavaType toType;

    public ValueNode object() {
        return object;
    }

    public UnsafeCastNode(ValueNode object, ResolvedJavaType toType, boolean exactType, boolean nonNull) {
        super(toType.kind().isObject() ? new ObjectStamp(toType, exactType, nonNull, false) : StampFactory.forKind(toType.kind()));
        this.object = object;
        this.toType = toType;
    }

    public UnsafeCastNode(ValueNode object, ResolvedJavaType toType) {
        super(toType.kind().isObject() ? StampFactory.declared(toType, object.stamp().nonNull()) : StampFactory.forKind(toType.kind()));
        this.object = object;
        this.toType = toType;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (object != null) {
            if (object.kind().isObject()) {
                if (object.objectStamp().type() != null && object.objectStamp().type().isSubtypeOf(toType)) {
                    if (!isNarrower(objectStamp(), object.objectStamp())) {
                        return object;
                    }
                }
            } else if (object.kind() == kind()) {
                // Removes redundant casts introduced by WordTypeRewriterPhase
                return object;
            }
        }
        return this;
    }

    /**
     * Determines if one object stamp is narrower than another in terms of nullness and exactness.
     *
     * @return true if x is definitely non-null and y's nullness is unknown OR
     *                  x's type is exact and the exactness of y's type is unknown
     */
    private static boolean isNarrower(ObjectStamp x, ObjectStamp y) {
        if (x.nonNull() && !y.nonNull()) {
            return true;
        }
        if (x.isExactType() && !y.isExactType()) {
            return true;
        }
        return false;
    }

    @NodeIntrinsic
    public static native <T> T cast(Object object, @ConstantNodeParameter Class<?> toType);

    @NodeIntrinsic
    public static native <T> T cast(Object object, @ConstantNodeParameter Class<?> toType, @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull);

    @Override
    public void generate(LIRGeneratorTool generator) {
        Value result = generator.newVariable(kind());
        generator.emitMove(generator.operand(object), result);
        generator.setResult(this, result);
    }
}
