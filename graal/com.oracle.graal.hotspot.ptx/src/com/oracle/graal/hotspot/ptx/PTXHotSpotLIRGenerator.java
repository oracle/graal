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

package com.oracle.graal.hotspot.ptx;

import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.code.StackSlot;
import com.oracle.graal.api.meta.DeoptimizationAction;
import com.oracle.graal.api.meta.DeoptimizationReason;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.compiler.ptx.PTXLIRGenerator;
import com.oracle.graal.graph.GraalInternalError;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.hotspot.HotSpotVMConfig;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.nodes.DirectCompareAndSwapNode;
import com.oracle.graal.lir.FrameMap;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;

/**
 * LIR generator specialized for PTX HotSpot.
 */
public class PTXHotSpotLIRGenerator extends PTXLIRGenerator implements HotSpotLIRGenerator {

    private final HotSpotVMConfig config;

    protected PTXHotSpotLIRGenerator(StructuredGraph graph, HotSpotProviders providers, HotSpotVMConfig config, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, providers, frameMap, cc, lir);
        assert config.basicLockSize == 8;
        this.config = config;
    }

    public void emitPrefetchAllocate(ValueNode address, ValueNode distance) {
        // nop
    }

    public void emitTailcall(Value[] args, Value address) {
        throw GraalInternalError.unimplemented();
    }

    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        throw GraalInternalError.unimplemented();
    }

    public void emitPatchReturnAddress(ValueNode address) {
        throw GraalInternalError.unimplemented();
    }

    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        throw GraalInternalError.unimplemented();
    }

    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        throw GraalInternalError.unimplemented();
    }

    public StackSlot getLockSlot(int lockDepth) {
        throw GraalInternalError.unimplemented();
    }

    public HotSpotProviders getProviders() {
        throw GraalInternalError.unimplemented();
    }
}
