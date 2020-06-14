/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.EnumMap;
import java.util.function.Function;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateMetaAccessExtensionProvider;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig.ConfigKind;
import com.oracle.svm.core.graal.meta.SubstrateSnippetReflectionProvider;
import com.oracle.svm.core.graal.meta.SubstrateStampProvider;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.thread.VMThreadMTFeature;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public abstract class SharedRuntimeConfigurationBuilder {

    protected final OptionValues options;
    protected final SVMHost hostVM;
    protected final MetaAccessProvider metaAccess;
    protected RuntimeConfiguration runtimeConfig;
    protected WordTypes wordTypes;
    protected final Function<Providers, SubstrateBackend> backendProvider;
    protected final NativeLibraries nativeLibraries;

    public SharedRuntimeConfigurationBuilder(OptionValues options, SVMHost hostVM, MetaAccessProvider metaAccess, Function<Providers, SubstrateBackend> backendProvider,
                    NativeLibraries nativeLibraries) {
        this.options = options;
        this.hostVM = hostVM;
        this.metaAccess = metaAccess;
        this.backendProvider = backendProvider;
        this.nativeLibraries = nativeLibraries;
    }

    public SharedRuntimeConfigurationBuilder build() {
        wordTypes = new SubstrateWordTypes(metaAccess, FrameAccess.getWordKind());
        Providers p = createProviders(null, null, null, null, null, null, null, null, null, null);
        StampProvider stampProvider = createStampProvider(p);
        p = createProviders(null, null, null, null, null, null, stampProvider, null, null, null);
        ConstantReflectionProvider constantReflection = createConstantReflectionProvider(p);
        p = createProviders(null, constantReflection, null, null, null, null, stampProvider, null, null, null);
        ConstantFieldProvider constantFieldProvider = createConstantFieldProvider(p);
        SnippetReflectionProvider snippetReflection = createSnippetReflectionProvider();
        ForeignCallsProvider foreignCalls = createForeignCallsProvider();
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, null, null, stampProvider, snippetReflection, null, null);
        BarrierSet barrierSet = ImageSingletons.lookup(Heap.class).createBarrierSet(metaAccess);
        PlatformConfigurationProvider platformConfig = new SubstratePlatformConfigurationProvider(barrierSet);
        MetaAccessExtensionProvider metaAccessExtensionProvider = new SubstrateMetaAccessExtensionProvider();
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, null, null, stampProvider, snippetReflection, platformConfig, metaAccessExtensionProvider);
        LoweringProvider lowerer = createLoweringProvider(p);
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider, snippetReflection, platformConfig, metaAccessExtensionProvider);
        Replacements replacements = createReplacements(p, snippetReflection);
        p = createProviders(null, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, snippetReflection, platformConfig, metaAccessExtensionProvider);

        EnumMap<ConfigKind, SubstrateBackend> backends = new EnumMap<>(ConfigKind.class);
        for (ConfigKind config : ConfigKind.values()) {
            RegisterConfig registerConfig = ImageSingletons.lookup(SubstrateRegisterConfigFactory.class).newRegisterFactory(config, metaAccess, ConfigurationValues.getTarget(),
                            SubstrateOptions.PreserveFramePointer.getValue());
            CodeCacheProvider codeCacheProvider = createCodeCacheProvider(registerConfig);

            Providers newProviders = createProviders(codeCacheProvider, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider,
                            snippetReflection, platformConfig, metaAccessExtensionProvider);
            backends.put(config, GraalConfiguration.instance().createBackend(newProviders));
        }

        runtimeConfig = new RuntimeConfiguration(p, snippetReflection, backends, wordTypes);
        return this;
    }

    public WordTypes getWordTypes() {
        return wordTypes;
    }

    protected Providers createProviders(CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, ForeignCallsProvider foreignCalls,
                    LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, @SuppressWarnings("unused") SnippetReflectionProvider snippetReflection,
                    PlatformConfigurationProvider platformConfigurationProvider, MetaAccessExtensionProvider metaAccessExtensionProvider) {
        return new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider,
                        metaAccessExtensionProvider, snippetReflection, wordTypes);
    }

    public RuntimeConfiguration getRuntimeConfig() {
        return runtimeConfig;
    }

    protected StampProvider createStampProvider(Providers p) {
        return new SubstrateStampProvider(p.getMetaAccess());
    }

    protected abstract ConstantReflectionProvider createConstantReflectionProvider(Providers p);

    protected abstract ConstantFieldProvider createConstantFieldProvider(Providers p);

    protected SnippetReflectionProvider createSnippetReflectionProvider() {
        return new SubstrateSnippetReflectionProvider(getWordTypes());
    }

    protected ForeignCallsProvider createForeignCallsProvider() {
        return new SubstrateForeignCallsProvider();
    }

    protected LoweringProvider createLoweringProvider(Providers p) {
        return SubstrateLoweringProvider.create(p.getMetaAccess(), p.getForeignCalls(), p.getPlatformConfigurationProvider(), p.getMetaAccessExtensionProvider());
    }

    protected abstract Replacements createReplacements(Providers p, SnippetReflectionProvider snippetReflection);

    protected abstract CodeCacheProvider createCodeCacheProvider(RegisterConfig registerConfig);

    public void updateLazyState(HostedMetaAccess hMetaAccess) {
        HybridLayout<DynamicHub> hubLayout = new HybridLayout<>(DynamicHub.class, ConfigurationValues.getObjectLayout(), hMetaAccess);
        int vtableBaseOffset = hubLayout.getArrayBaseOffset();
        int vtableEntrySize = ConfigurationValues.getObjectLayout().sizeInBytes(hubLayout.getArrayElementStorageKind());
        int instanceOfBitsOffset = hubLayout.getBitFieldOffset();

        int componentHubOffset;
        try {
            componentHubOffset = hMetaAccess.lookupJavaField(DynamicHub.class.getDeclaredField("componentHub")).getLocation();
        } catch (NoSuchFieldException ex) {
            throw shouldNotReachHere(ex);
        }

        int javaFrameAnchorLastSPOffset = findStructOffset(JavaFrameAnchor.class, "getLastJavaSP");
        int javaFrameAnchorLastIPOffset = findStructOffset(JavaFrameAnchor.class, "getLastJavaIP");

        int vmThreadStatusOffset = -1;
        if (SubstrateOptions.MultiThreaded.getValue()) {
            vmThreadStatusOffset = ImageSingletons.lookup(VMThreadMTFeature.class).offsetOf(VMThreads.StatusSupport.statusTL);
        }

        runtimeConfig.setLazyState(vtableBaseOffset, vtableEntrySize, instanceOfBitsOffset, componentHubOffset, javaFrameAnchorLastSPOffset, javaFrameAnchorLastIPOffset, vmThreadStatusOffset);
    }

    private int findStructOffset(Class<?> clazz, String accessorName) {
        try {
            AccessorInfo accessorInfo = (AccessorInfo) nativeLibraries.findElementInfo(metaAccess.lookupJavaMethod(clazz.getDeclaredMethod(accessorName)));
            StructFieldInfo structFieldInfo = (StructFieldInfo) accessorInfo.getParent();
            return structFieldInfo.getOffsetInfo().getProperty();
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
