/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.hotspot.amd64;

import jdk.internal.jvmci.amd64.AMD64;
import jdk.internal.jvmci.code.CallingConvention.Type;
import jdk.internal.jvmci.code.CodeCacheProvider;
import jdk.internal.jvmci.code.CompilationResult;
import jdk.internal.jvmci.code.InstalledCode;
import jdk.internal.jvmci.code.Register;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.service.ServiceProvider;

import com.oracle.graal.asm.Assembler;
import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.amd64.AMD64Address;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.asm.amd64.AMD64MacroAssembler;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.FrameContext;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.truffle.hotspot.OptimizedCallTargetInstrumentation;
import com.oracle.graal.truffle.hotspot.OptimizedCallTargetInstrumentationFactory;

@ServiceProvider(OptimizedCallTargetInstrumentationFactory.class)
public class AMD64OptimizedCallTargetInstrumentationFactory extends OptimizedCallTargetInstrumentationFactory {

    public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, FrameContext frameContext,
                    CompilationResult compilationResult) {
        return new OptimizedCallTargetInstrumentation(codeCache, foreignCalls, frameMap, asm, frameContext, compilationResult, config, registers) {
            @Override
            protected void injectTailCallCode() {
                @SuppressWarnings("hiding")
                AMD64MacroAssembler asm = (AMD64MacroAssembler) this.asm;
                Register thisRegister = codeCache.getRegisterConfig().getCallingConventionRegisters(Type.JavaCall, JavaKind.Object)[0];
                Register spillRegister = AMD64.r10; // TODO(mg): fix me
                Label doProlog = new Label();

                AMD64Address codeBlobAddress = new AMD64Address(thisRegister, getFieldOffset("entryPoint", InstalledCode.class));
                asm.movq(spillRegister, codeBlobAddress);
                asm.testq(spillRegister, spillRegister);
                asm.jcc(ConditionFlag.Equal, doProlog);
                asm.jmp(spillRegister);

                asm.bind(doProlog);
            }
        };
    }

    @Override
    public String getArchitecture() {
        return "AMD64";
    }
}
