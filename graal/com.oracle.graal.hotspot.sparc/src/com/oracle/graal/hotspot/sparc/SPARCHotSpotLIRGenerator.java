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
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.sparc.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.sparc.SPARCMove.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

public class SPARCHotSpotLIRGenerator extends SPARCLIRGenerator implements HotSpotLIRGenerator {

    private HotSpotRuntime runtime() {
        return (HotSpotRuntime) runtime;
    }

    public SPARCHotSpotLIRGenerator(StructuredGraph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, runtime, target, frameMap, cc, lir);
    }

    /**
     * The slot reserved for storing the original return address when a frame is marked for
     * deoptimization. The return address slot in the callee is overwritten with the address of a
     * deoptimization stub.
     */
    StackSlot deoptimizationRescueSlot;

    @Override
    public void visitSafepointNode(SafepointNode i) {
        LIRFrameState info = state(i);
        append(new SPARCSafepointOp(info, runtime().config, this));
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        throw new InternalError("NYI");
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        InvokeKind invokeKind = ((HotSpotDirectCallTargetNode) callTarget).invokeKind();
        if (invokeKind == InvokeKind.Interface || invokeKind == InvokeKind.Virtual) {
            append(new SPARCHotspotDirectVirtualCallOp(callTarget.target(), result, parameters, temps, callState, invokeKind));
        } else {
            assert invokeKind == InvokeKind.Static || invokeKind == InvokeKind.Special;
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) callTarget.target();
            assert !Modifier.isAbstract(resolvedMethod.getModifiers()) : "Cannot make direct call to abstract method.";
            Constant metaspaceMethod = resolvedMethod.getMetaspaceMethodConstant();
            append(new SPARCHotspotDirectStaticCallOp(callTarget.target(), result, parameters, temps, callState, invokeKind, metaspaceMethod));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
// AllocatableValue metaspaceMethod = AMD64.rbx.asValue();
// emitMove(metaspaceMethod, operand(((HotSpotIndirectCallTargetNode)
// callTarget).metaspaceMethod()));
// AllocatableValue targetAddress = AMD64.rax.asValue();
// emitMove(targetAddress, operand(callTarget.computedAddress()));
// append(new AMD64IndirectCallOp(callTarget.target(), result, parameters, temps, metaspaceMethod,
// targetAddress, callState));
        throw new InternalError("NYI");
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        throw new InternalError("NYI");
    }

    @Override
    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        Kind kind = x.newValue().kind();
        assert kind == x.expectedValue().kind();

        Variable address = load(operand(x.object()));
        Value offset = operand(x.offset());
        Variable cmpValue = (Variable) loadNonConst(operand(x.expectedValue()));
        Variable newValue = load(operand(x.newValue()));

        if (ValueUtil.isConstant(offset)) {
            assert !runtime.needsDataPatch(asConstant(offset));
            address = emitAdd(address, asConstant(offset));
        } else {
            if (isLegal(offset)) {
                address = emitAdd(address, offset);
            }
        }

        append(new CompareAndSwapOp(address, cmpValue, newValue));

        Variable result = newVariable(x.kind());
        emitMove(result, newValue);
        setResult(x, result);
    }

    @Override
    public StackSlot getLockSlot(int lockDepth) {
        throw new InternalError("NYI");
    }

    public Stub getStub() {
        throw new InternalError("NYI");
    }

}
