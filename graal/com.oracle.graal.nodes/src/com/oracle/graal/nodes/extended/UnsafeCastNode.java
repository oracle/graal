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
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code UnsafeCastNode} produces the same value as its input, but with a different type.
 */
public class UnsafeCastNode extends PiNode implements Canonicalizable, LIRLowerable {

    public UnsafeCastNode(ValueNode object, Stamp stamp) {
        super(object, stamp);
    }

    public UnsafeCastNode(ValueNode object, Stamp stamp, ValueNode anchor) {
        super(object, stamp, (FixedNode) anchor);
    }

    public UnsafeCastNode(ValueNode object, ResolvedJavaType toType, boolean exactType, boolean nonNull) {
        this(object, toType.getKind() == Kind.Object ? StampFactory.object(toType, exactType, nonNull || object.stamp().nonNull()) : StampFactory.forKind(toType.getKind()));
    }

    @Override
    public boolean inferStamp() {
        if (kind() != Kind.Object || object().kind() != Kind.Object) {
            return false;
        }
        if (stamp() == StampFactory.forNodeIntrinsic()) {
            return false;
        }
        return updateStamp(stamp().join(object().stamp()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (kind() != object().kind()) {
            return this;
        }

        if (kind() == Kind.Object) {
            ObjectStamp my = objectStamp();
            ObjectStamp other = object().objectStamp();

            if (my.type() == null || other.type() == null) {
                return this;
            }
            if (my.isExactType() && !other.isExactType()) {
                return this;
            }
            if (my.nonNull() && !other.nonNull()) {
                return this;
            }
            if (!my.type().isAssignableFrom(other.type())) {
                return this;
            }
        }
        return object();
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        if (kind() != object().kind()) {
            assert generator.target().arch.getSizeInBytes(kind()) == generator.target().arch.getSizeInBytes(object().kind()) : "unsafe cast cannot be used to change the size of a value";
            AllocatableValue result = generator.newVariable(kind());
            generator.emitMove(result, generator.operand(object()));
            generator.setResult(this, result);
        } else {
            // The LIR only cares about the kind of an operand, not the actual type of an object. So
            // we do not have to
            // introduce a new operand when the kind is the same.
            generator.setResult(this, generator.operand(object()));
        }
    }

    @NodeIntrinsic
    public static native <T> T unsafeCast(Object object, @ConstantNodeParameter Stamp stamp);

    @NodeIntrinsic
    public static native <T> T unsafeCast(Object object, @ConstantNodeParameter Stamp stamp, ValueNode anchor);

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static <T> T unsafeCast(Object object, @ConstantNodeParameter Class<T> toType, @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull) {
        return toType.cast(object);
    }
}
