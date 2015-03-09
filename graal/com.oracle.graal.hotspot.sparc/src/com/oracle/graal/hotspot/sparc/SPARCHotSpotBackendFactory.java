/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.sparc.*;
import com.oracle.graal.sparc.SPARC.*;

@ServiceProvider(HotSpotBackendFactory.class)
public class SPARCHotSpotBackendFactory implements HotSpotBackendFactory {

    protected Architecture createArchitecture(HotSpotVMConfig config) {
        return new SPARC(computeFeatures(config));
    }

    protected TargetDescription createTarget(HotSpotVMConfig config) {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        return new HotSpotTargetDescription(createArchitecture(config), true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    public HotSpotBackend createBackend(HotSpotGraalRuntimeProvider runtime, HotSpotBackend host) {
        assert host == null;
        TargetDescription target = createTarget(runtime.getConfig());

        HotSpotRegistersProvider registers = createRegisters();
        HotSpotMetaAccessProvider metaAccess = new HotSpotMetaAccessProvider(runtime);
        RegisterConfig regConfig = new SPARCHotSpotRegisterConfig(target, runtime.getConfig());
        HotSpotCodeCacheProvider codeCache = createCodeCache(runtime, target, regConfig);
        HotSpotConstantReflectionProvider constantReflection = new HotSpotConstantReflectionProvider(runtime);
        Value[] nativeABICallerSaveRegisters = createNativeABICallerSaveRegisters(runtime.getConfig(), codeCache.getRegisterConfig());
        HotSpotForeignCallsProvider foreignCalls = new SPARCHotSpotForeignCallsProvider(runtime, metaAccess, codeCache, nativeABICallerSaveRegisters);
        LoweringProvider lowerer = createLowerer(runtime, metaAccess, foreignCalls, registers, target);
        Providers p = new Providers(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, null, new HotSpotStampProvider());
        HotSpotSnippetReflectionProvider snippetReflection = new HotSpotSnippetReflectionProvider(runtime);
        HotSpotReplacementsImpl replacements = new HotSpotReplacementsImpl(p, snippetReflection, runtime.getConfig(), target);
        HotSpotDisassemblerProvider disassembler = new HotSpotDisassemblerProvider(runtime);
        HotSpotSuitesProvider suites = createSuites(runtime, metaAccess, constantReflection, replacements);
        HotSpotProviders providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, replacements, disassembler, suites, registers, snippetReflection);

        return createBackend(runtime, providers);
    }

    protected HotSpotSuitesProvider createSuites(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, Replacements replacements) {
        return new HotSpotSuitesProvider(runtime, metaAccess, constantReflection, replacements);
    }

    protected HotSpotCodeCacheProvider createCodeCache(HotSpotGraalRuntimeProvider runtime, TargetDescription target, RegisterConfig regConfig) {
        return new HotSpotCodeCacheProvider(runtime, target, regConfig);
    }

    protected SPARCHotSpotBackend createBackend(HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new SPARCHotSpotBackend(runtime, providers);
    }

    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider runtime, HotSpotMetaAccessProvider metaAccess, HotSpotForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, TargetDescription target) {
        return new SPARCHotSpotLoweringProvider(runtime, metaAccess, foreignCalls, registers, target);
    }

    protected EnumSet<CPUFeature> computeFeatures(HotSpotVMConfig config) {
        EnumSet<CPUFeature> features = EnumSet.noneOf(CPUFeature.class);
        if ((config.sparcFeatures & config.vis1Instructions) != 0) {
            features.add(CPUFeature.VIS1);
        }
        if ((config.sparcFeatures & config.vis2Instructions) != 0) {
            features.add(CPUFeature.VIS2);
        }
        if ((config.sparcFeatures & config.vis3Instructions) != 0) {
            features.add(CPUFeature.VIS3);
        }
        if ((config.sparcFeatures & config.cbcondInstructions) != 0) {
            features.add(CPUFeature.CBCOND);
        }
        return features;
    }

    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(SPARC.g2, SPARC.g6, SPARC.sp);
    }

    @SuppressWarnings("unused")
    private static Value[] createNativeABICallerSaveRegisters(HotSpotVMConfig config, RegisterConfig regConfig) {
        Set<Register> callerSavedRegisters = new HashSet<>();
        Collections.addAll(callerSavedRegisters, regConfig.getCalleeSaveLayout().registers);
        Collections.addAll(callerSavedRegisters, SPARC.fpuRegisters);
        Value[] nativeABICallerSaveRegisters = new Value[callerSavedRegisters.size()];
        int i = 0;
        for (Register reg : callerSavedRegisters) {
            nativeABICallerSaveRegisters[i] = reg.asValue();
            i++;
        }
        return nativeABICallerSaveRegisters;
    }

    public String getArchitecture() {
        return "SPARC";
    }

    public String getGraalRuntimeName() {
        return "basic";
    }

    @Override
    public String toString() {
        return getGraalRuntimeName() + ":" + getArchitecture();
    }
}
