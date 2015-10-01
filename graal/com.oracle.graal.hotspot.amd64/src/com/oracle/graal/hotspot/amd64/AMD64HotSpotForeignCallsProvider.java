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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.hotspot.HotSpotBackend.EXCEPTION_HANDLER;
import static com.oracle.graal.hotspot.HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER;
import static com.oracle.graal.hotspot.HotSpotBackend.Options.PreferGraalStubs;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.JUMP_ADDRESS;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect.PRESERVES_REGISTERS;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition.LEAF;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition.LEAF_NOFP;
import static com.oracle.graal.hotspot.HotSpotHostBackend.DEOPTIMIZATION_HANDLER;
import static com.oracle.graal.hotspot.HotSpotHostBackend.UNCOMMON_TRAP_HANDLER;
import static com.oracle.graal.hotspot.replacements.CRC32Substitutions.UPDATE_BYTES_CRC32;
import static jdk.internal.jvmci.amd64.AMD64.rax;
import static jdk.internal.jvmci.amd64.AMD64.rdx;
import static jdk.internal.jvmci.code.CallingConvention.Type.NativeCall;
import static jdk.internal.jvmci.meta.LocationIdentity.any;
import static jdk.internal.jvmci.meta.Value.ILLEGAL;
import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.code.CodeCacheProvider;
import jdk.internal.jvmci.code.RegisterValue;
import jdk.internal.jvmci.code.TargetDescription;
import jdk.internal.jvmci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.internal.jvmci.hotspot.HotSpotVMConfig;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.MetaAccessProvider;
import jdk.internal.jvmci.meta.PlatformKind;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.hotspot.HotSpotForeignCallLinkageImpl;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotHostForeignCallsProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;

public class AMD64HotSpotForeignCallsProvider extends HotSpotHostForeignCallsProvider {

    private final Value[] nativeABICallerSaveRegisters;

    public AMD64HotSpotForeignCallsProvider(HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache,
                    Value[] nativeABICallerSaveRegisters) {
        super(jvmciRuntime, runtime, metaAccess, codeCache);
        this.nativeABICallerSaveRegisters = nativeABICallerSaveRegisters;
    }

    @Override
    public void initialize(HotSpotProviders providers) {
        HotSpotVMConfig config = jvmciRuntime.getConfig();
        TargetDescription target = providers.getCodeCache().getTarget();
        PlatformKind word = target.arch.getWordKind();

        // The calling convention for the exception handler stub is (only?) defined in
        // TemplateInterpreterGenerator::generate_throw_exception()
        // in templateInterpreter_x86_64.cpp around line 1923
        RegisterValue exception = rax.asValue(LIRKind.reference(word));
        RegisterValue exceptionPc = rdx.asValue(LIRKind.value(word));
        CallingConvention exceptionCc = new CallingConvention(0, ILLEGAL, exception, exceptionPc);
        register(new HotSpotForeignCallLinkageImpl(EXCEPTION_HANDLER, 0L, PRESERVES_REGISTERS, LEAF_NOFP, null, exceptionCc, NOT_REEXECUTABLE, any()));
        register(new HotSpotForeignCallLinkageImpl(EXCEPTION_HANDLER_IN_CALLER, JUMP_ADDRESS, PRESERVES_REGISTERS, LEAF_NOFP, exceptionCc, null, NOT_REEXECUTABLE, any()));

        if (PreferGraalStubs.getValue()) {
            link(new AMD64DeoptimizationStub(providers, target, config, registerStubCall(DEOPTIMIZATION_HANDLER, REEXECUTABLE, LEAF, NO_LOCATIONS)));
            link(new AMD64UncommonTrapStub(providers, target, config, registerStubCall(UNCOMMON_TRAP_HANDLER, REEXECUTABLE, LEAF, NO_LOCATIONS)));
        }

        if (config.useCRC32Intrinsics) {
            // This stub does callee saving
            registerForeignCall(UPDATE_BYTES_CRC32, config.updateBytesCRC32Stub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, any());
        }

        super.initialize(providers);
    }

    @Override
    public Value[] getNativeABICallerSaveRegisters() {
        return nativeABICallerSaveRegisters;
    }

}
