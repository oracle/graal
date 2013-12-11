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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.hsail.*;
import com.oracle.graal.lir.hsail.HSAILControlFlow.*;
import com.oracle.graal.lir.hsail.HSAILMove.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.util.*;

/**
 * The HotSpot specific portion of the HSAIL LIR generator.
 */
public class HSAILHotSpotLIRGenerator extends HSAILLIRGenerator {

    private final HotSpotVMConfig config;

    public HSAILHotSpotLIRGenerator(StructuredGraph graph, Providers providers, HotSpotVMConfig config, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, providers, frameMap, cc, lir);
        this.config = config;
    }

    private int getLogMinObjectAlignment() {
        return config.logMinObjAlignment();
    }

    private int getNarrowOopShift() {
        return config.narrowOopShift;
    }

    private long getNarrowOopBase() {
        return config.narrowOopBase;
    }

    private int getLogKlassAlignment() {
        return config.logKlassAlignment;
    }

    private int getNarrowKlassShift() {
        return config.narrowKlassShift;
    }

    private long getNarrowKlassBase() {
        return config.narrowKlassBase;
    }

    private static boolean isCompressCandidate(Access access) {
        return access != null && access.isCompressible();
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
        Kind kind = node.getNewValue().kind();
        assert kind == node.getExpectedValue().kind();
        Variable expected = load(operand(node.getExpectedValue()));
        Variable newValue = load(operand(node.getNewValue()));
        HSAILAddressValue addressValue = asAddressValue(address);
        Variable casResult = newVariable(kind);
        if (config.useCompressedOops && node.isCompressible()) {
            // make 64-bit scratch variables for expected and new
            Variable scratchExpected64 = newVariable(Kind.Long);
            Variable scratchNewValue64 = newVariable(Kind.Long);
            // make 32-bit scratch variables for expected and new and result
            Variable scratchExpected32 = newVariable(Kind.Int);
            Variable scratchNewValue32 = newVariable(Kind.Int);
            Variable scratchCasResult32 = newVariable(Kind.Int);
            append(new CompareAndSwapCompressedOp(casResult, addressValue, expected, newValue, scratchExpected64, scratchNewValue64, scratchExpected32, scratchNewValue32, scratchCasResult32,
                            getNarrowOopBase(), getNarrowOopShift(), getLogMinObjectAlignment()));
        } else {
            append(new CompareAndSwapOp(casResult, addressValue, expected, newValue));
        }
        Variable nodeResult = newVariable(node.kind());
        append(new CondMoveOp(mapKindToCompareOp(kind), casResult, expected, nodeResult, Condition.EQ, Constant.INT_1, Constant.INT_0));
        setResult(node, nodeResult);
    }

    @Override
    public Variable emitLoad(Kind kind, Value address, Access access) {
        HSAILAddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(kind);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        if (isCompressCandidate(access) && config.useCompressedOops && kind == Kind.Object) {
            Variable scratch = newVariable(Kind.Long);
            append(new LoadCompressedPointer(kind, result, scratch, loadAddress, state, getNarrowOopBase(), getNarrowOopShift(), getLogMinObjectAlignment()));
        } else if (isCompressCandidate(access) && config.useCompressedClassPointers && kind == Kind.Long) {
            Variable scratch = newVariable(Kind.Long);
            append(new LoadCompressedPointer(kind, result, scratch, loadAddress, state, getNarrowKlassBase(), getNarrowKlassShift(), getLogKlassAlignment()));
        } else {
            append(new LoadOp(kind, result, loadAddress, state));
        }
        return result;
    }

    @Override
    public void emitStore(Kind kind, Value address, Value inputVal, Access access) {
        HSAILAddressValue storeAddress = asAddressValue(address);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        Variable input = load(inputVal);
        if (isCompressCandidate(access) && config.useCompressedOops && kind == Kind.Object) {
            Variable scratch = newVariable(Kind.Long);
            append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, getNarrowOopBase(), getNarrowOopShift(), getLogMinObjectAlignment()));
        } else if (isCompressCandidate(access) && config.useCompressedClassPointers && kind == Kind.Long) {
            Variable scratch = newVariable(Kind.Long);
            append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, getNarrowKlassBase(), getNarrowKlassShift(), getLogKlassAlignment()));
        } else {
            append(new StoreOp(kind, storeAddress, input, state));
        }
    }

    /***
     * This is a very temporary solution to emitForeignCall. We don't really support foreign calls
     * yet, but we do want to generate dummy code for them. The ForeignCallXXXOps just end up
     * emitting a comment as to what Foreign call they would have made.
     **/
    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, DeoptimizingNode info, Value... args) {
        Variable result = newVariable(Kind.Object);  // linkage.getDescriptor().getResultType());

        // to make the LIRVerifier happy, we move any constants into registers
        Value[] argLocations = new Value[args.length];
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = newVariable(arg.getKind());
            emitMove(loc, arg);
            argLocations[i] = loc;
        }

        // here we could check the callName if we wanted to only handle certain callnames
        String callName = linkage.getDescriptor().getName();
        switch (argLocations.length) {
            case 0:
                append(new ForeignCallNoArgOp(callName, result));
                break;
            case 1:
                append(new ForeignCall1ArgOp(callName, result, argLocations[0]));
                break;
            case 2:
                append(new ForeignCall2ArgOp(callName, result, argLocations[0], argLocations[1]));
                break;
            default:
                throw new InternalError("NYI emitForeignCall " + callName + ", " + argLocations.length + ", " + linkage);
        }
        return result;
    }

    @Override
    protected void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        // this version of emitForeignCall not used for now
    }

}
