/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.hotspot;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.Z_LOAD_BARRIER;

import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.truffle.TruffleCompilerConfiguration;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Mechanism for injecting special code into
 * {@linkplain HotSpotTruffleCompilerImpl#installTruffleCallBoundaryMethod(jdk.vm.ci.meta.ResolvedJavaMethod, com.oracle.truffle.compiler.TruffleCompilable)
 * call boundary methods}.
 */
public abstract class TruffleEntryPointDecorator implements EntryPointDecorator {

    protected MetaAccessProvider metaAccess;
    protected GraalHotSpotVMConfig config;
    protected HotSpotRegistersProvider registers;
    protected final int installedCodeOffset;
    protected final int entryPointOffset;

    public TruffleEntryPointDecorator(TruffleCompilerConfiguration compilerConfig, GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        this.metaAccess = compilerConfig.lastTier().providers().getMetaAccess();
        this.config = config;
        this.registers = registers;

        HotSpotKnownTruffleTypes types = (HotSpotKnownTruffleTypes) compilerConfig.types();
        this.installedCodeOffset = types.HotSpotOptimizedCallTarget_installedCode.getOffset();
        this.entryPointOffset = types.InstalledCode_entryPoint.getOffset();
    }

    @Override
    public void initialize(CoreProviders providers, LIRGenerationResult lirGenRes) {
        if (config.gc == HotSpotGraalRuntime.HotSpotGC.Z) {
            ForeignCallLinkage callTarget = providers.getForeignCalls().lookupForeignCall(Z_LOAD_BARRIER);
            lirGenRes.getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        }
    }
}
