/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.hsail.*;
import com.oracle.graal.lir.hsail.HSAILMove.CompareAndSwapOp;
import com.oracle.graal.nodes.*;

/**
 * The HotSpot specific portion of the HSAIL LIR generator.
 */
public class HSAILHotSpotNodeLIRBuilder extends HSAILNodeLIRBuilder implements HotSpotNodeLIRBuilder {

    public HSAILHotSpotNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        super(graph, lirGen);
    }

    @Override
    protected void emitNode(ValueNode node) {
        if (node instanceof CurrentJavaThreadNode) {
            throw new GraalInternalError("HSAILHotSpotLIRGenerator cannot handle node: " + node);
        } else {
            super.emitNode(node);
        }
    }

    private HSAILHotSpotLIRGenerator getGen() {
        return (HSAILHotSpotLIRGenerator) gen;
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

    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        Variable expected = getGen().load(operand(x.expectedValue()));
        Variable newVal = getGen().load(operand(x.newValue()));

        LIRKind kind = newVal.getLIRKind();
        assert kind.equals(expected.getLIRKind());

        int disp = 0;
        HSAILAddressValue address;
        Value index = operand(x.offset());
        if (ValueUtil.isConstant(index) && NumUtil.isInt(ValueUtil.asConstant(index).asLong() + disp)) {
            assert !getGen().getCodeCache().needsDataPatch(ValueUtil.asConstant(index));
            disp += (int) ValueUtil.asConstant(index).asLong();
            address = new HSAILAddressValue(kind, getGen().load(operand(x.object())), disp);
        } else {
            throw GraalInternalError.shouldNotReachHere("NYI");
        }

        Variable casResult = gen.newVariable(kind);
        append(new CompareAndSwapOp((Kind) kind.getPlatformKind(), casResult, address, expected, newVal));

        setResult(x, casResult);
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        HotSpotVMConfig config = getGen().config;
        if ((config.useHSAILSafepoints == true) && (config.useHSAILDeoptimization == true)) {
            LIRFrameState info = state(i);
            HSAILHotSpotSafepointOp safepoint = new HSAILHotSpotSafepointOp(info, config, this);
            ((HSAILHotSpotLIRGenerationResult) getGen().getResult()).addDeopt(safepoint);
            append(safepoint);
        } else {
            Debug.log("HSAIL safepoints turned off");
        }
    }

    @Override
    public void emitPrefetchAllocate(ValueNode address, ValueNode distance) {
        // nop
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        throw GraalInternalError.unimplemented();
    }
}
