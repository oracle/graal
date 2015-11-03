/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotCompressedNullConstant;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.hotspot.HotSpotVMConfig.CompressEncoding;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.common.type.AbstractObjectStamp;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.hotspot.nodes.type.KlassPointerStamp;
import com.oracle.graal.hotspot.nodes.type.NarrowOopStamp;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.ConvertNode;
import com.oracle.graal.nodes.calc.UnaryNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.nodes.type.StampTool;

/**
 * Compress or uncompress an oop or metaspace pointer.
 */
@NodeInfo(nameTemplate = "{p#op/s}")
public final class CompressionNode extends UnaryNode implements ConvertNode, LIRLowerable {

    public static final NodeClass<CompressionNode> TYPE = NodeClass.create(CompressionNode.class);

    public enum CompressionOp {
        Compress,
        Uncompress
    }

    protected final CompressionOp op;
    protected final CompressEncoding encoding;

    public CompressionNode(CompressionOp op, ValueNode input, CompressEncoding encoding) {
        super(TYPE, mkStamp(op, input.stamp(), encoding), input);
        this.op = op;
        this.encoding = encoding;
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp());
        return mkStamp(op, newStamp, encoding);
    }

    public static CompressionNode compress(ValueNode input, CompressEncoding encoding) {
        return input.graph().unique(new CompressionNode(CompressionOp.Compress, input, encoding));
    }

    public static CompressionNode compressNoUnique(ValueNode input, CompressEncoding encoding) {
        return new CompressionNode(CompressionOp.Compress, input, encoding);
    }

    public static CompressionNode uncompress(ValueNode input, CompressEncoding encoding) {
        return input.graph().unique(new CompressionNode(CompressionOp.Uncompress, input, encoding));
    }

    private static Constant compress(Constant c) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return HotSpotCompressedNullConstant.COMPRESSED_NULL;
        } else if (c instanceof HotSpotConstant) {
            return ((HotSpotConstant) c).compress();
        } else {
            throw JVMCIError.shouldNotReachHere("invalid constant input for compress op: " + c);
        }
    }

    private static Constant uncompress(Constant c) {
        if (c instanceof HotSpotConstant) {
            return ((HotSpotConstant) c).uncompress();
        } else {
            throw JVMCIError.shouldNotReachHere("invalid constant input for uncompress op: " + c);
        }
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        switch (op) {
            case Compress:
                return compress(c);
            case Uncompress:
                return uncompress(c);
            default:
                throw JVMCIError.shouldNotReachHere();
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
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public boolean isLossless() {
        return true;
    }

    private static Stamp mkStamp(CompressionOp op, Stamp input, CompressEncoding encoding) {
        switch (op) {
            case Compress:
                if (input instanceof ObjectStamp) {
                    // compressed oop
                    return NarrowOopStamp.compressed((ObjectStamp) input, encoding);
                } else if (input instanceof KlassPointerStamp) {
                    // compressed klass pointer
                    return ((KlassPointerStamp) input).compressed(encoding);
                }
                break;
            case Uncompress:
                if (input instanceof NarrowOopStamp) {
                    // oop
                    assert encoding.equals(((NarrowOopStamp) input).getEncoding());
                    return ((NarrowOopStamp) input).uncompressed();
                } else if (input instanceof KlassPointerStamp) {
                    // metaspace pointer
                    assert encoding.equals(((KlassPointerStamp) input).getEncoding());
                    return ((KlassPointerStamp) input).uncompressed();
                }
                break;
        }
        throw JVMCIError.shouldNotReachHere(String.format("Unexpected input stamp %s", input));
    }

    public CompressionOp getOp() {
        return op;
    }

    public CompressEncoding getEncoding() {
        return encoding;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            return ConstantNode.forConstant(stamp(), convert(forValue.asConstant(), tool.getConstantReflection()), tool.getMetaAccess());
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
        HotSpotLIRGenerator hsGen = (HotSpotLIRGenerator) gen.getLIRGeneratorTool();
        boolean nonNull;
        if (getValue().stamp() instanceof AbstractObjectStamp) {
            nonNull = StampTool.isPointerNonNull(getValue().stamp());
        } else {
            // metaspace pointers are never null
            nonNull = true;
        }

        Value result;
        switch (op) {
            case Compress:
                result = hsGen.emitCompress(gen.operand(getValue()), encoding, nonNull);
                break;
            case Uncompress:
                result = hsGen.emitUncompress(gen.operand(getValue()), encoding, nonNull);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native Object compression(@ConstantNodeParameter CompressionOp op, Object object, @ConstantNodeParameter CompressEncoding encoding);
}
