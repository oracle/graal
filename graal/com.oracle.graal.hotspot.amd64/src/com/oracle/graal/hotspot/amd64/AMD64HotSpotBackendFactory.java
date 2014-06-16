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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.hotspot.InitTimer.*;

import java.util.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.util.*;

@ServiceProvider(HotSpotBackendFactory.class)
public class AMD64HotSpotBackendFactory implements HotSpotBackendFactory {

    protected Architecture createArchitecture(HotSpotVMConfig config) {
        return new AMD64(computeFeatures(config));
    }

    protected EnumSet<AMD64.CPUFeature> computeFeatures(HotSpotVMConfig config) {
        // Configure the feature set using the HotSpot flag settings.
        EnumSet<AMD64.CPUFeature> features = EnumSet.noneOf(AMD64.CPUFeature.class);
        assert config.useSSE >= 2 : "minimum config for x64";
        features.add(AMD64.CPUFeature.SSE);
        features.add(AMD64.CPUFeature.SSE2);
        if ((config.x86CPUFeatures & config.cpuSSE3) != 0) {
            features.add(AMD64.CPUFeature.SSE3);
        }
        if ((config.x86CPUFeatures & config.cpuSSSE3) != 0) {
            features.add(AMD64.CPUFeature.SSSE3);
        }
        if ((config.x86CPUFeatures & config.cpuSSE4A) != 0) {
            features.add(AMD64.CPUFeature.SSE4a);
        }
        if ((config.x86CPUFeatures & config.cpuSSE41) != 0) {
            features.add(AMD64.CPUFeature.SSE4_1);
        }
        if ((config.x86CPUFeatures & config.cpuSSE42) != 0) {
            features.add(AMD64.CPUFeature.SSE4_2);
        }
        if ((config.x86CPUFeatures & config.cpuAVX) != 0) {
            features.add(AMD64.CPUFeature.AVX);
        }
        if ((config.x86CPUFeatures & config.cpuAVX2) != 0) {
            features.add(AMD64.CPUFeature.AVX2);
        }
        if ((config.x86CPUFeatures & config.cpuERMS) != 0) {
            features.add(AMD64.CPUFeature.ERMS);
        }
        if ((config.x86CPUFeatures & config.cpuLZCNT) != 0) {
            features.add(AMD64.CPUFeature.LZCNT);
        }
        if ((config.x86CPUFeatures & config.cpuPOPCNT) != 0) {
            features.add(AMD64.CPUFeature.POPCNT);
        }
        if ((config.x86CPUFeatures & config.cpuAES) != 0) {
            features.add(AMD64.CPUFeature.AES);
        }
        if ((config.x86CPUFeatures & config.cpu3DNOWPREFETCH) != 0) {
            features.add(AMD64.CPUFeature.AMD_3DNOW_PREFETCH);
        }
        return features;
    }

    protected TargetDescription createTarget(HotSpotVMConfig config) {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        return new HotSpotTargetDescription(createArchitecture(config), true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects, Kind.Int);
    }

    @Override
    public HotSpotBackend createBackend(HotSpotGraalRuntime runtime, HotSpotBackend host) {
        assert host == null;
        TargetDescription target = createTarget(runtime.getConfig());

        HotSpotProviders providers;
        HotSpotRegistersProvider registers;
        HotSpotCodeCacheProvider codeCache;
        HotSpotConstantReflectionProvider constantReflection;
        HotSpotHostForeignCallsProvider foreignCalls;
        Value[] nativeABICallerSaveRegisters;
        HotSpotMetaAccessProvider metaAccess;
        HotSpotLoweringProvider lowerer;
        HotSpotSnippetReflectionProvider snippetReflection;
        Replacements replacements;
        HotSpotDisassemblerProvider disassembler;
        HotSpotSuitesProvider suites;
        HotSpotMethodHandleAccessProvider methodHandleAccess;
        try (InitTimer t = timer("create providers")) {
            try (InitTimer rt = timer("create HotSpotRegisters provider")) {
                registers = createRegisters();
            }
            try (InitTimer rt = timer("create MetaAccess provider")) {
                metaAccess = createMetaAccess(runtime);
            }
            try (InitTimer rt = timer("create CodeCache provider")) {
                codeCache = createCodeCache(runtime, target);
            }
            try (InitTimer rt = timer("create ConstantReflection provider")) {
                constantReflection = createConstantReflection(runtime);
            }
            try (InitTimer rt = timer("create NativeABICallerSaveRegisters")) {
                nativeABICallerSaveRegisters = createNativeABICallerSaveRegisters(runtime.getConfig(), codeCache.getRegisterConfig());
            }
            try (InitTimer rt = timer("create ForeignCalls provider")) {
                foreignCalls = createForeignCalls(runtime, metaAccess, codeCache, nativeABICallerSaveRegisters);
            }
            try (InitTimer rt = timer("create Lowerer provider")) {
                lowerer = createLowerer(runtime, metaAccess, foreignCalls, registers, target);
            }
            // Replacements cannot have speculative optimizations since they have
            // to be valid for the entire run of the VM.
            Assumptions assumptions = new Assumptions(false);
            Providers p = new Providers(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, null);
            try (InitTimer rt = timer("create SnippetReflection provider")) {
                snippetReflection = createSnippetReflection();
            }
            try (InitTimer rt = timer("create Replacements provider")) {
                replacements = createReplacements(runtime, assumptions, p, snippetReflection);
            }
            try (InitTimer rt = timer("create Disassembler provider")) {
                disassembler = createDisassembler(runtime);
            }
            try (InitTimer rt = timer("create Suites provider")) {
                suites = createSuites(runtime);
            }
            try (InitTimer rt = timer("create MethodHandleAccess provider")) {
                methodHandleAccess = new HotSpotMethodHandleAccessProvider();
            }
            providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, replacements, disassembler, suites, registers, snippetReflection, methodHandleAccess);
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return createBackend(runtime, providers);
        }
    }

    protected AMD64HotSpotBackend createBackend(HotSpotGraalRuntime runtime, HotSpotProviders providers) {
        return new AMD64HotSpotBackend(runtime, providers);
    }

    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(AMD64.r15, AMD64.r12, AMD64.rsp);
    }

    protected HotSpotDisassemblerProvider createDisassembler(HotSpotGraalRuntime runtime) {
        return new HotSpotDisassemblerProvider(runtime);
    }

    protected Replacements createReplacements(HotSpotGraalRuntime runtime, Assumptions assumptions, Providers p, SnippetReflectionProvider snippetReflection) {
        return new HotSpotReplacementsImpl(p, snippetReflection, runtime.getConfig(), assumptions, p.getCodeCache().getTarget());
    }

    protected AMD64HotSpotForeignCallsProvider createForeignCalls(HotSpotGraalRuntime runtime, HotSpotMetaAccessProvider metaAccess, HotSpotCodeCacheProvider codeCache,
                    Value[] nativeABICallerSaveRegisters) {
        return new AMD64HotSpotForeignCallsProvider(runtime, metaAccess, codeCache, nativeABICallerSaveRegisters);
    }

    protected HotSpotConstantReflectionProvider createConstantReflection(HotSpotGraalRuntime runtime) {
        return new HotSpotConstantReflectionProvider(runtime);
    }

    protected AMD64HotSpotCodeCacheProvider createCodeCache(HotSpotGraalRuntime runtime, TargetDescription target) {
        return new AMD64HotSpotCodeCacheProvider(runtime, target);
    }

    protected HotSpotMetaAccessProvider createMetaAccess(HotSpotGraalRuntime runtime) {
        return new HotSpotMetaAccessProvider(runtime);
    }

    protected HotSpotSuitesProvider createSuites(HotSpotGraalRuntime runtime) {
        return new HotSpotSuitesProvider(runtime);
    }

    protected HotSpotSnippetReflectionProvider createSnippetReflection() {
        return new HotSpotSnippetReflectionProvider();
    }

    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntime runtime, HotSpotMetaAccessProvider metaAccess, HotSpotForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers,
                    TargetDescription target) {
        return new AMD64HotSpotLoweringProvider(runtime, metaAccess, foreignCalls, registers, target);
    }

    protected Value[] createNativeABICallerSaveRegisters(HotSpotVMConfig config, RegisterConfig regConfig) {
        List<Register> callerSave = new ArrayList<>(Arrays.asList(regConfig.getAllocatableRegisters()));
        if (config.windowsOs) {
            // http://msdn.microsoft.com/en-us/library/9z1stfyw.aspx
            callerSave.remove(AMD64.rdi);
            callerSave.remove(AMD64.rsi);
            callerSave.remove(AMD64.rbx);
            callerSave.remove(AMD64.rbp);
            callerSave.remove(AMD64.rsp);
            callerSave.remove(AMD64.r12);
            callerSave.remove(AMD64.r13);
            callerSave.remove(AMD64.r14);
            callerSave.remove(AMD64.r15);
            callerSave.remove(AMD64.xmm6);
            callerSave.remove(AMD64.xmm7);
            callerSave.remove(AMD64.xmm8);
            callerSave.remove(AMD64.xmm9);
            callerSave.remove(AMD64.xmm10);
            callerSave.remove(AMD64.xmm11);
            callerSave.remove(AMD64.xmm12);
            callerSave.remove(AMD64.xmm13);
            callerSave.remove(AMD64.xmm14);
            callerSave.remove(AMD64.xmm15);
        } else {
            /*
             * System V Application Binary Interface, AMD64 Architecture Processor Supplement
             * 
             * Draft Version 0.96
             * 
             * http://www.uclibc.org/docs/psABI-x86_64.pdf
             * 
             * 3.2.1
             * 
             * ...
             * 
             * This subsection discusses usage of each register. Registers %rbp, %rbx and %r12
             * through %r15 "belong" to the calling function and the called function is required to
             * preserve their values. In other words, a called function must preserve these
             * registers' values for its caller. Remaining registers "belong" to the called
             * function. If a calling function wants to preserve such a register value across a
             * function call, it must save the value in its local stack frame.
             */
            callerSave.remove(AMD64.rbp);
            callerSave.remove(AMD64.rbx);
            callerSave.remove(AMD64.r12);
            callerSave.remove(AMD64.r13);
            callerSave.remove(AMD64.r14);
            callerSave.remove(AMD64.r15);
        }
        Value[] nativeABICallerSaveRegisters = new Value[callerSave.size()];
        for (int i = 0; i < callerSave.size(); i++) {
            nativeABICallerSaveRegisters[i] = callerSave.get(i).asValue();
        }
        return nativeABICallerSaveRegisters;
    }

    public String getArchitecture() {
        return "AMD64";
    }

    public String getGraalRuntimeName() {
        return "basic";
    }

    @Override
    public String toString() {
        return getGraalRuntimeName() + ":" + getArchitecture();
    }
}
