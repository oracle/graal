/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.hsail;

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.hsail.*;
import com.oracle.graal.lir.hsail.HSAILControlFlow.CondMoveOp;
import com.oracle.graal.lir.hsail.HSAILMove.CompareAndSwapCompressedOp;
import com.oracle.graal.lir.hsail.HSAILMove.CompareAndSwapOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;

/**
 * The HotSpot specific portion of the HSAIL LIR generator.
 */
public class HSAILHotSpotNodeLIRBuilder extends HSAILNodeLIRBuilder {

    public HSAILHotSpotNodeLIRBuilder(StructuredGraph graph, LIRGenerator lirGen) {
        super(graph, lirGen);
    }

    private HSAILHotSpotLIRGenerator getGen() {
        return (HSAILHotSpotLIRGenerator) gen;
    }

    /**
     * Appends either a {@link CompareAndSwapOp} or a {@link CompareAndSwapCompressedOp} depending
     * on whether the memory location of a given {@link LoweredCompareAndSwapNode} contains a
     * compressed oop. For the {@link CompareAndSwapCompressedOp} case, allocates a number of
     * scratch registers. The result {@link #operand(ValueNode) operand} for {@code node} complies
     * with the API for {@link Unsafe#compareAndSwapInt(Object, long, int, int)}.
     *
     * @param address the memory location targeted by the operation
     */
    @Override
    public void visitCompareAndSwap(LoweredCompareAndSwapNode node, Value address) {
        Kind kind = node.getNewValue().getKind();
        assert kind == node.getExpectedValue().getKind();
        Variable expected = gen.load(operand(node.getExpectedValue()));
        Variable newValue = gen.load(operand(node.getNewValue()));
        HSAILAddressValue addressValue = getGen().asAddressValue(address);
        Variable casResult = newVariable(kind);
        if (getGen().config.useCompressedOops && node.isCompressible()) {
            // make 64-bit scratch variables for expected and new
            Variable scratchExpected64 = newVariable(Kind.Long);
            Variable scratchNewValue64 = newVariable(Kind.Long);
            // make 32-bit scratch variables for expected and new and result
            Variable scratchExpected32 = newVariable(Kind.Int);
            Variable scratchNewValue32 = newVariable(Kind.Int);
            Variable scratchCasResult32 = newVariable(Kind.Int);
            append(new CompareAndSwapCompressedOp(casResult, addressValue, expected, newValue, scratchExpected64, scratchNewValue64, scratchExpected32, scratchNewValue32, scratchCasResult32,
                            getGen().getNarrowOopBase(), getGen().getNarrowOopShift(), getGen().getLogMinObjectAlignment()));
        } else {
            append(new CompareAndSwapOp(casResult, addressValue, expected, newValue));
        }
        Variable nodeResult = newVariable(node.getKind());
        append(new CondMoveOp(HSAILLIRGenerator.mapKindToCompareOp(kind), casResult, expected, nodeResult, Condition.EQ, Constant.INT_1, Constant.INT_0));
        setResult(node, nodeResult);
    }

    @Override
    protected void emitNode(ValueNode node) {
        if (node instanceof CurrentJavaThreadNode) {
            throw new GraalInternalError("HSAILHotSpotLIRGenerator cannot handle node: " + node);
        } else {
            super.emitNode(node);
        }
    }

    /**
     * @return a compressed version of the incoming constant lifted from AMD64HotSpotLIRGenerator
     */
    protected static Constant compress(Constant c, CompressEncoding encoding) {
        if (c.getKind() == Kind.Long) {
            int compressedValue = (int) (((c.asLong() - encoding.base) >> encoding.shift) & 0xffffffffL);
            if (c instanceof HotSpotMetaspaceConstant) {
                return HotSpotMetaspaceConstant.forMetaspaceObject(Kind.Int, compressedValue, HotSpotMetaspaceConstant.getMetaspaceObject(c));
            } else {
                return Constant.forIntegerKind(Kind.Int, compressedValue);
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }
}
