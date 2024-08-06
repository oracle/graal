/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.calc.ConvertNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

/**
 * Compress or uncompress an oop or metaspace pointer.
 */
@NodeInfo(nameTemplate = "{p#op/s}", cycles = CYCLES_2, size = SIZE_2)
public abstract class CompressionNode extends UnaryNode implements ConvertNode, LIRLowerable {

    public static final NodeClass<CompressionNode> TYPE = NodeClass.create(CompressionNode.class);

    public enum CompressionOp {
        Compress,
        Uncompress
    }

    protected final CompressionOp op;
    protected final CompressEncoding encoding;

    public CompressionNode(NodeClass<? extends UnaryNode> c, CompressionOp op, ValueNode input, Stamp stamp, CompressEncoding encoding) {
        super(c, stamp, input);
        this.op = op;
        this.encoding = encoding;
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp(NodeView.DEFAULT));
        return stamp.improveWith(mkStamp(newStamp));
    }

    protected abstract Constant compress(Constant c);

    protected abstract Constant uncompress(Constant c);

    public JavaConstant nullConstant() {
        return JavaConstant.NULL_POINTER;
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        switch (op) {
            case Compress:
                return compress(c);
            case Uncompress:
                return uncompress(c);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Constant reverse(Constant c, ConstantReflectionProvider constantReflection) {
        switch (op) {
            case Compress:
                return uncompress(c);
            case Uncompress:
                return compress(c);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * Apply the inverse of this node's {@linkplain #getOp() operation} (with the same
     * {@linkplain #getEncoding() encoding}) to the given {@code input} node. Implementers may
     * return an existing node. If a new node is returned, it is not added to the graph.
     */
    public abstract ValueNode reverse(ValueNode input);

    @Override
    public boolean isLossless() {
        return true;
    }

    protected abstract Stamp mkStamp(Stamp input);

    public CompressionOp getOp() {
        return op;
    }

    public CompressEncoding getEncoding() {
        return encoding;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            ConstantNode constant = (ConstantNode) forValue;
            return ConstantNode.forConstant(stamp(NodeView.DEFAULT), convert(constant.getValue(), tool.getConstantReflection()), constant.getStableDimension(), constant.isDefaultStable(),
                            tool.getMetaAccess());
        } else if (forValue instanceof CompressionNode) {
            CompressionNode other = (CompressionNode) forValue;
            if (op != other.op && encoding.equals(other.encoding)) {
                return other.getValue();
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        boolean nonNull;
        if (stamp instanceof AbstractObjectStamp) {
            nonNull = StampTool.isPointerNonNull(stamp);
        } else {
            // metaspace pointers are never null
            nonNull = true;
        }

        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value result;
        switch (op) {
            case Compress:
                result = tool.emitCompress(gen.operand(value), encoding, nonNull);
                break;
            case Uncompress:
                result = tool.emitUncompress(gen.operand(value), encoding, nonNull);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
        }

        gen.setResult(this, result);
    }

    @Override
    public boolean mayNullCheckSkipConversion() {
        return true;
    }
}
