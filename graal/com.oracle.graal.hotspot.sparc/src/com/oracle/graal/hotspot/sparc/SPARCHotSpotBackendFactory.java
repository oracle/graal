/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.sparc.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.sparc.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.compiler.*;
import jdk.internal.jvmci.hotspot.*;
import jdk.internal.jvmci.meta.*;
import jdk.internal.jvmci.runtime.*;
import jdk.internal.jvmci.service.*;
import jdk.internal.jvmci.sparc.*;

@ServiceProvider(StartupEventListener.class)
public class SPARCHotSpotBackendFactory implements HotSpotBackendFactory, StartupEventListener {

    @Override
    public void beforeJVMCIStartup() {
        DefaultHotSpotGraalCompilerFactory.registerBackend(SPARC.class, this);
    }

    @Override
    public HotSpotBackend createBackend(HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotBackend host) {
        assert host == null;

        HotSpotVMConfig config = jvmciRuntime.getConfig();
        JVMCIBackend jvmci = jvmciRuntime.getHostJVMCIBackend();
        HotSpotRegistersProvider registers = createRegisters();
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmci.getMetaAccess();
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) jvmci.getCodeCache();
        TargetDescription target = codeCache.getTarget();
        HotSpotConstantReflectionProvider constantReflection = new HotSpotGraalConstantReflectionProvider(jvmciRuntime);
        Value[] nativeABICallerSaveRegisters = createNativeABICallerSaveRegisters(config, codeCache.getRegisterConfig());
        HotSpotForeignCallsProvider foreignCalls = new SPARCHotSpotForeignCallsProvider(jvmciRuntime, runtime, metaAccess, codeCache, nativeABICallerSaveRegisters);
        LoweringProvider lowerer = createLowerer(runtime, metaAccess, foreignCalls, registers, target);
        HotSpotStampProvider stampProvider = new HotSpotStampProvider();
        Providers p = new Providers(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, null, stampProvider);
        HotSpotSnippetReflectionProvider snippetReflection = new HotSpotSnippetReflectionProvider(runtime);
        HotSpotReplacementsImpl replacements = new HotSpotReplacementsImpl(p, snippetReflection, config, target);
        HotSpotWordTypes wordTypes = new HotSpotWordTypes(metaAccess, target.wordJavaKind);
        Plugins plugins = createGraphBuilderPlugins(config, metaAccess, constantReflection, foreignCalls, stampProvider, snippetReflection, replacements, wordTypes);
        replacements.setGraphBuilderPlugins(plugins);
        HotSpotSuitesProvider suites = createSuites(config, runtime, compilerConfiguration, plugins, codeCache);
        HotSpotProviders providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, replacements, suites, registers, snippetReflection, wordTypes, plugins);

        return createBackend(config, runtime, providers);
    }

    protected Plugins createGraphBuilderPlugins(HotSpotVMConfig config, HotSpotMetaAccessProvider metaAccess, HotSpotConstantReflectionProvider constantReflection,
                    HotSpotForeignCallsProvider foreignCalls, HotSpotStampProvider stampProvider, HotSpotSnippetReflectionProvider snippetReflection, HotSpotReplacementsImpl replacements,
                    HotSpotWordTypes wordTypes) {
        Plugins plugins = HotSpotGraphBuilderPlugins.create(config, wordTypes, metaAccess, constantReflection, snippetReflection, foreignCalls, stampProvider, replacements);
        SPARCGraphBuilderPlugins.register(plugins, foreignCalls);
        return plugins;
    }

    protected HotSpotSuitesProvider createSuites(HotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, Plugins plugins, CodeCacheProvider codeCache) {
        return new HotSpotSuitesProvider(new DefaultSuitesProvider(compilerConfiguration, plugins), config, runtime, new SPARCAddressLowering(codeCache));
    }

    protected SPARCHotSpotBackend createBackend(HotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new SPARCHotSpotBackend(config, runtime, providers);
    }

    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider runtime, HotSpotMetaAccessProvider metaAccess, HotSpotForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, TargetDescription target) {
        return new SPARCHotSpotLoweringProvider(runtime, metaAccess, foreignCalls, registers, target);
    }

    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(SPARC.g2, SPARC.g6, SPARC.sp);
    }

    @SuppressWarnings("unused")
    private static Value[] createNativeABICallerSaveRegisters(HotSpotVMConfig config, RegisterConfig regConfig) {
        Set<Register> callerSavedRegisters = new HashSet<>();
        Collections.addAll(callerSavedRegisters, regConfig.getCalleeSaveRegisters());
        Collections.addAll(callerSavedRegisters, SPARC.fpuRegisters);
        callerSavedRegisters.add(SPARC.g1);
        callerSavedRegisters.add(SPARC.g4);
        callerSavedRegisters.add(SPARC.g5);
        Value[] nativeABICallerSaveRegisters = new Value[callerSavedRegisters.size()];
        int i = 0;
        for (Register reg : callerSavedRegisters) {
            nativeABICallerSaveRegisters[i] = reg.asValue();
            i++;
        }
        return nativeABICallerSaveRegisters;
    }

    @Override
    public String toString() {
        return "SPARC";
    }

    public Architecture initializeArchitecture(Architecture arch) {
        assert arch instanceof SPARC;
        return arch;
    }
}
