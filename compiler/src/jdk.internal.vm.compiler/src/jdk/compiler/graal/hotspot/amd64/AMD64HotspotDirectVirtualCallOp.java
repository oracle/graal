/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.amd64;

import jdk.compiler.graal.asm.amd64.AMD64MacroAssembler;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.hotspot.HotSpotMarkId;
import jdk.compiler.graal.lir.LIRFrameState;
import jdk.compiler.graal.lir.LIRInstructionClass;
import jdk.compiler.graal.lir.Opcode;
import jdk.compiler.graal.lir.amd64.AMD64Call;
import jdk.compiler.graal.lir.amd64.AMD64Call.DirectCallOp;
import jdk.compiler.graal.lir.asm.CompilationResultBuilder;
import jdk.compiler.graal.nodes.CallTargetNode.InvokeKind;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

/**
 * A direct call that complies with the conventions for such calls in HotSpot. In particular, for
 * calls using an inline cache, a MOVE instruction is emitted just prior to the aligned direct call.
 */
@Opcode("CALL_DIRECT")
final class AMD64HotspotDirectVirtualCallOp extends DirectCallOp {
    public static final LIRInstructionClass<AMD64HotspotDirectVirtualCallOp> TYPE = LIRInstructionClass.create(AMD64HotspotDirectVirtualCallOp.class);

    private final InvokeKind invokeKind;
    private final GraalHotSpotVMConfig config;

    AMD64HotspotDirectVirtualCallOp(ResolvedJavaMethod target, Value result, Value[] parameters, Value[] temps, LIRFrameState state, InvokeKind invokeKind, GraalHotSpotVMConfig config) {
        super(TYPE, target, result, parameters, temps, state);
        this.invokeKind = invokeKind;
        this.config = config;
        assert invokeKind.isIndirect();
    }

    @Override
    @SuppressWarnings("try")
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (config.supportsMethodHandleDeoptimizationEntry() && config.isMethodHandleCall((HotSpotResolvedJavaMethod) callTarget)) {
            crb.setNeedsMHDeoptHandler();
        }
        AMD64Call.directInlineCacheCall(crb, masm, callTarget, invokeKind == InvokeKind.Virtual ? HotSpotMarkId.INVOKEVIRTUAL : HotSpotMarkId.INVOKEINTERFACE, config.nonOopBits, state);
    }
}
