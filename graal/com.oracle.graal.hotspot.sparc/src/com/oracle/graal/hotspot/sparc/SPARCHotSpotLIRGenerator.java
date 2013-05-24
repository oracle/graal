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

import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.code.CodeCacheProvider;
import com.oracle.graal.api.code.DeoptimizationAction;
import com.oracle.graal.api.code.StackSlot;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.DeoptimizationReason;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.sparc.SPARCLIRGenerator;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.hotspot.nodes.DirectCompareAndSwapNode;
import com.oracle.graal.hotspot.stubs.Stub;
import com.oracle.graal.lir.FrameMap;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;

public class SPARCHotSpotLIRGenerator  extends SPARCLIRGenerator implements HotSpotLIRGenerator {

    public StackSlot deoptimizationRescueSlot;

    public SPARCHotSpotLIRGenerator(StructuredGraph graph,
            CodeCacheProvider runtime, TargetDescription target,
            FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, runtime, target, frameMap, cc, lir);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        // TODO Auto-generated method stub
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action,
            DeoptimizationReason reason) {
        // TODO Auto-generated method stub
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        // TODO Auto-generated method stub
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc,
            ValueNode exception, ValueNode exceptionPc) {
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
