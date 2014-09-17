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

    public HotSpotBackend createBackend(HotSpotGraalRuntime runtime, HotSpotBackend host) {
        assert host == null;
        TargetDescription target = createTarget(runtime.getConfig());

        HotSpotRegistersProvider registers = createRegisters();
        HotSpotMetaAccessProvider metaAccess = new HotSpotMetaAccessProvider(runtime);
        HotSpotCodeCacheProvider codeCache = new SPARCHotSpotCodeCacheProvider(runtime, target);
        HotSpotConstantReflectionProvider constantReflection = new HotSpotConstantReflectionProvider(runtime);
        Value[] nativeABICallerSaveRegisters = createNativeABICallerSaveRegisters(runtime.getConfig(), codeCache.getRegisterConfig());
        HotSpotForeignCallsProvider foreignCalls = new SPARCHotSpotForeignCallsProvider(runtime, metaAccess, codeCache, nativeABICallerSaveRegisters);
        LoweringProvider lowerer = new SPARCHotSpotLoweringProvider(runtime, metaAccess, foreignCalls, registers, target);
        // Replacements cannot have speculative optimizations since they have
        // to be valid for the entire run of the VM.
        Assumptions assumptions = new Assumptions(false);
        Providers p = new Providers(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, null);
        HotSpotSnippetReflectionProvider snippetReflection = new HotSpotSnippetReflectionProvider();
        HotSpotReplacementsImpl replacements = new HotSpotReplacementsImpl(p, snippetReflection, runtime.getConfig(), assumptions, target);
        HotSpotDisassemblerProvider disassembler = new HotSpotDisassemblerProvider(runtime);
        HotSpotSuitesProvider suites = new HotSpotSuitesProvider(runtime);
        HotSpotMethodHandleAccessProvider methodHandleAccess = new HotSpotMethodHandleAccessProvider();
        HotSpotProviders providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, replacements, disassembler, suites, registers, snippetReflection,
                        methodHandleAccess);

        return new SPARCHotSpotBackend(runtime, providers);
    }

    protected Set<CPUFeature> computeFeatures(HotSpotVMConfig config) {
        Set<CPUFeature> features = EnumSet.noneOf(CPUFeature.class);
        if ((config.sparcFeatures & config.vis1Instructions) != 0) {
            features.add(CPUFeature.VIS1);
        }
        if ((config.sparcFeatures & config.vis2Instructions) != 0) {
            features.add(CPUFeature.VIS2);
        }
        if ((config.sparcFeatures & config.vis3Instructions) != 0) {
            features.add(CPUFeature.VIS3);
        }
        return features;
    }

    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(SPARC.g2, SPARC.g6, SPARC.sp);
    }

    @SuppressWarnings("unused")
    private static Value[] createNativeABICallerSaveRegisters(HotSpotVMConfig config, RegisterConfig regConfig) {
        CalleeSaveLayout csl = regConfig.getCalleeSaveLayout();
        Value[] nativeABICallerSaveRegisters = new Value[csl.registers.length];
        for (int i = 0; i < csl.registers.length; i++) {
            nativeABICallerSaveRegisters[i] = csl.registers[i].asValue();
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
