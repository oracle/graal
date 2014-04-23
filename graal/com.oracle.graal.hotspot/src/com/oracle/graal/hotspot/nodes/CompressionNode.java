/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Compress or uncompress an oop or metaspace pointer.
 */
@NodeInfo(nameTemplate = "{p#op/s}")
public final class CompressionNode extends FloatingNode implements LIRLowerable, Canonicalizable {

    private enum CompressionOp {
        Compress,
        Uncompress
    }

    private final CompressionOp op;
    private final CompressEncoding encoding;

    @Input private ValueNode input;

    private CompressionNode(CompressionOp op, ValueNode input, CompressEncoding encoding) {
        super(mkStamp(op, input.stamp(), encoding));
        this.op = op;
        this.encoding = encoding;
        this.input = input;
    }

    public static CompressionNode compress(ValueNode input, CompressEncoding encoding) {
        return input.graph().unique(new CompressionNode(CompressionOp.Compress, input, encoding));
    }

    public static CompressionNode uncompress(ValueNode input, CompressEncoding encoding) {
        return input.graph().unique(new CompressionNode(CompressionOp.Uncompress, input, encoding));
    }

    private static Stamp mkStamp(CompressionOp op, Stamp input, CompressEncoding encoding) {
        switch (op) {
            case Compress:
                if (input instanceof ObjectStamp) {
                    // compressed oop
                    return new NarrowOopStamp((ObjectStamp) input, encoding);
                } else if (input instanceof IntegerStamp) {
                    // compressed metaspace pointer
                    assert PrimitiveStamp.getBits(input) == 64;
                    return StampFactory.forInteger(32);
                }
                break;
            case Uncompress:
                if (input instanceof NarrowOopStamp) {
                    // oop
                    assert encoding.equals(((NarrowOopStamp) input).getEncoding());
                    return ((NarrowOopStamp) input).uncompressed();
                } else if (input instanceof IntegerStamp) {
                    // metaspace pointer
                    assert PrimitiveStamp.getBits(input) == 32;
                    return StampFactory.forInteger(64);
                }
                break;
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (input instanceof CompressionNode) {
            CompressionNode other = (CompressionNode) input;
            if (op != other.op && encoding.equals(other.encoding)) {
                return other.input;
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        HotSpotLIRGenerator hsGen = (HotSpotLIRGenerator) gen.getLIRGeneratorTool();
        boolean nonNull;
        if (input.stamp() instanceof ObjectStamp) {
            nonNull = StampTool.isObjectNonNull(input.stamp());
        } else {
            // metaspace pointers are never null
            nonNull = true;
        }

        Value result;
        switch (op) {
            case Compress:
                result = hsGen.emitCompress(gen.operand(input), encoding, nonNull);
                break;
            case Uncompress:
                result = hsGen.emitUncompress(gen.operand(input), encoding, nonNull);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        gen.setResult(this, result);
    }
}
