/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class ReverseBytesNode extends UnaryNode implements LIRLowerable {

    public static final NodeClass<ReverseBytesNode> TYPE = NodeClass.create(ReverseBytesNode.class);

    public ReverseBytesNode(ValueNode value) {
        super(TYPE, value.stamp(NodeView.DEFAULT).unrestricted(), value);
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp(NodeView.DEFAULT));
        if (newStamp instanceof IntegerStamp) {
            IntegerStamp valueStamp = (IntegerStamp) newStamp;
            switch (valueStamp.getBits()) {
                case 1:
                case 8: {
                    return stamp(NodeView.DEFAULT);
                }
                case 16: {
                    long mask = CodeUtil.mask(16);
                    return IntegerStamp.stampForMask(16, Short.reverseBytes((short) valueStamp.downMask()) & mask, Short.reverseBytes((short) valueStamp.upMask()) & mask);
                }
                case 32: {
                    long mask = CodeUtil.mask(32);
                    return IntegerStamp.stampForMask(32, Integer.reverseBytes((int) valueStamp.downMask()) & mask, Integer.reverseBytes((int) valueStamp.upMask()) & mask);
                }
                case 64: {
                    return IntegerStamp.stampForMask(64, Long.reverseBytes(valueStamp.downMask()), Long.reverseBytes(valueStamp.upMask()));
                }
                default:
                    throw GraalError.unimplemented("Unsupported bit size " + valueStamp.getBits());
            }
        }
        return stamp(NodeView.DEFAULT);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue instanceof ReverseBytesNode) {
            return ((ReverseBytesNode) forValue).getValue();
        }
        JavaConstant c = forValue.asJavaConstant();
        if (c != null) {
            switch (c.getJavaKind()) {
                case Byte:
                    return ConstantNode.forByte((byte) c.asInt(), forValue.graph());
                case Short:
                    return ConstantNode.forShort(Short.reverseBytes((short) c.asInt()), forValue.graph());
                case Char:
                    return ConstantNode.forChar(Character.reverseBytes((char) c.asInt()), forValue.graph());
                case Int:
                    return ConstantNode.forInt(Integer.reverseBytes(c.asInt()));
                case Long:
                    return ConstantNode.forLong(Long.reverseBytes(c.asLong()));
                default:
                    throw GraalError.unimplemented("Unhandled byte reverse on constant of kind " + c.getJavaKind());
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitByteSwap(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}
