/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.calc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.SerializableConstant;

import com.oracle.graal.compiler.common.type.ArithmeticStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.gen.ArithmeticLIRGenerator;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.NodeValueMap;

/**
 * The {@code ReinterpretNode} class represents a reinterpreting conversion that changes the stamp
 * of a primitive value to some other incompatible stamp. The new stamp must have the same width as
 * the old stamp.
 */
@NodeInfo
public final class ReinterpretNode extends UnaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<ReinterpretNode> TYPE = NodeClass.create(ReinterpretNode.class);

    public ReinterpretNode(JavaKind to, ValueNode value) {
        this(StampFactory.forKind(to), value);
    }

    public ReinterpretNode(Stamp to, ValueNode value) {
        super(TYPE, to, value);
        assert to instanceof ArithmeticStamp;
    }

    private SerializableConstant evalConst(SerializableConstant c) {
        /*
         * We don't care about byte order here. Either would produce the correct result.
         */
        ByteBuffer buffer = ByteBuffer.wrap(new byte[c.getSerializedSize()]).order(ByteOrder.nativeOrder());
        c.serialize(buffer);

        buffer.rewind();
        SerializableConstant ret = ((ArithmeticStamp) stamp()).deserialize(buffer);

        assert !buffer.hasRemaining();
        return ret;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            return ConstantNode.forConstant(stamp(), evalConst((SerializableConstant) forValue.asConstant()), null);
        }
        if (stamp().isCompatible(forValue.stamp())) {
            return forValue;
        }
        if (forValue instanceof ReinterpretNode) {
            ReinterpretNode reinterpret = (ReinterpretNode) forValue;
            return new ReinterpretNode(stamp(), reinterpret.getValue());
        }
        return this;
    }

    @Override
    public void generate(NodeValueMap nodeValueMap, ArithmeticLIRGenerator gen) {
        LIRKind kind = gen.getLIRKind(stamp());
        nodeValueMap.setResult(this, gen.emitReinterpret(kind, nodeValueMap.operand(getValue())));
    }

    public static ValueNode reinterpret(JavaKind toKind, ValueNode value) {
        return value.graph().unique(new ReinterpretNode(toKind, value));
    }

    @NodeIntrinsic
    public static native float reinterpret(@ConstantNodeParameter JavaKind kind, int value);

    @NodeIntrinsic
    public static native int reinterpret(@ConstantNodeParameter JavaKind kind, float value);

    @NodeIntrinsic
    public static native double reinterpret(@ConstantNodeParameter JavaKind kind, long value);

    @NodeIntrinsic
    public static native long reinterpret(@ConstantNodeParameter JavaKind kind, double value);
}
