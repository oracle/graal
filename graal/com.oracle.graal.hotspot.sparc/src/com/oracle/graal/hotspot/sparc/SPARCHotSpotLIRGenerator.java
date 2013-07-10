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

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.sparc.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

public class SPARCHotSpotLIRGenerator extends SPARCLIRGenerator implements HotSpotLIRGenerator {

    public StackSlot deoptimizationRescueSlot;

    public SPARCHotSpotLIRGenerator(StructuredGraph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, runtime, target, frameMap, cc, lir);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        // TODO Auto-generated method stub
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        InvokeKind invokeKind = ((HotSpotDirectCallTargetNode) callTarget).invokeKind();
        if (invokeKind == InvokeKind.Interface || invokeKind == InvokeKind.Virtual) {
// append(new SPARCHotspotDirectVirtualCallOp(callTarget.target(), result, parameters, temps,
// callState, invokeKind));
            throw new InternalError("NYI");
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
        // TODO Auto-generated method stub
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        // TODO Auto-generated method stub
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        // TODO Auto-generated method stub
    }

    @Override
    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        // TODO Auto-generated method stub
    }

    @Override
    public StackSlot getLockSlot(int lockDepth) {
        // TODO Auto-generated method stub
        return null;
    }

    public Stub getStub() {
        // TODO Auto-generated method stub
        return null;
    }

}
