/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.graal;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.runtime.GraalRuntime;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.loop.LoopsDataProviderImpl;
import jdk.graal.compiler.nodes.spi.IdentityHashCodeProvider;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import com.oracle.truffle.espresso.jvmci.meta.EspressoConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.runtime.JVMCIRuntime;

public final class EspressoGraalRuntime implements GraalRuntime, RuntimeProvider {
    private final DummyEspressoBackend hostBackend;

    EspressoGraalRuntime(JVMCIRuntime jvmciRuntime) {
        hostBackend = new DummyEspressoBackend(createProviders(jvmciRuntime));
    }

    private static Providers createProviders(JVMCIRuntime jvmciRuntime) {
        JVMCIBackend hostJVMCIBackend = jvmciRuntime.getHostJVMCIBackend();
        TargetDescription target = hostJVMCIBackend.getTarget();
        MetaAccessProvider metaAccess = hostJVMCIBackend.getMetaAccess();
        CodeCacheProvider codeCache = hostJVMCIBackend.getCodeCache();
        EspressoConstantReflectionProvider constantReflection = (EspressoConstantReflectionProvider) hostJVMCIBackend.getConstantReflection();
        ConstantFieldProvider constantFieldProvider = new EspressoConstantFieldProvider(metaAccess);
        ForeignCallsProvider foreignCalls = new DummyForeignCallsProvider();
        LoweringProvider lowerer = new DummyLoweringProvider(target);
        StampProvider stampProvider = new DummyStampProvider();
        PlatformConfigurationProvider platformConfigurationProvider = new DummyPlatformConfigurationProvider();
        MetaAccessExtensionProvider metaAccessExtensionProvider = new EspressoMetaAccessExtensionProvider(constantReflection);
        SnippetReflectionProvider snippetReflection = new EspressoSnippetReflectionProvider(constantReflection);
        WordTypes wordTypes = new WordTypes(metaAccess, target.wordJavaKind);
        LoopsDataProvider loopsDataProvider = new LoopsDataProviderImpl();
        IdentityHashCodeProvider identityHashCodeProvider = new EspressoIdentityHashCodeProvider(snippetReflection);
        Providers providers = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider, platformConfigurationProvider,
                        metaAccessExtensionProvider, snippetReflection, wordTypes, loopsDataProvider, identityHashCodeProvider);

        Replacements replacements = new DummyReplacements(providers);
        return (Providers) replacements.getProviders();
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if (clazz == RuntimeProvider.class) {
            return (T) this;
        }
        if (clazz == SnippetReflectionProvider.class) {
            return (T) hostBackend.getProviders().getSnippetReflection();
        }
        throw GraalError.unimplemented(clazz.getName());
    }

    @Override
    public Backend getHostBackend() {
        return hostBackend;
    }

    @Override
    public String getCompilerConfigurationName() {
        return "default";
    }

    @Override
    public <T extends Architecture> Backend getBackend(Class<T> arch) {
        throw GraalError.unimplemented(arch.getName());
    }
}
