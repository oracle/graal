/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.replacements.nodes;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.type.IntegerStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.debug.GraalError;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.UnaryNode;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;
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
                    return IntegerStamp.stampForMask(16, Short.reverseBytes((short) valueStamp.mustBeSet()) & mask, Short.reverseBytes((short) valueStamp.mayBeSet()) & mask);
                }
                case 32: {
                    long mask = CodeUtil.mask(32);
                    return IntegerStamp.stampForMask(32, Integer.reverseBytes((int) valueStamp.mustBeSet()) & mask, Integer.reverseBytes((int) valueStamp.mayBeSet()) & mask);
                }
                case 64: {
                    return IntegerStamp.stampForMask(64, Long.reverseBytes(valueStamp.mustBeSet()), Long.reverseBytes(valueStamp.mayBeSet()));
                }
                default:
                    throw GraalError.unimplemented("Unsupported bit size " + valueStamp.getBits()); // ExcludeFromJacocoGeneratedReport
            }
        }
        return stamp(NodeView.DEFAULT);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue instanceof ReverseBytesNode) {
            return ((ReverseBytesNode) forValue).getValue();
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitReverseBytes(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}
