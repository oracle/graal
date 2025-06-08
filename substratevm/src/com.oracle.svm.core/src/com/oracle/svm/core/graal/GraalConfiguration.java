/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import jdk.graal.compiler.core.aarch64.AArch64NodeMatchRules;
import jdk.graal.compiler.core.amd64.AMD64NodeMatchRules;
import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.code.SubstrateLoweringProviderFactory;
import com.oracle.svm.core.graal.code.SubstrateSuitesCreatorProvider;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.gen.NodeMatchRules;
import jdk.graal.compiler.core.match.MatchRuleRegistry;
import jdk.graal.compiler.core.match.MatchStatement;
import jdk.graal.compiler.core.riscv64.RISCV64NodeMatchRules;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.hotspot.CommunityCompilerConfigurationFactory;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.loop.LoopsDataProviderImpl;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.vector.lir.aarch64.AArch64VectorNodeMatchRules;
import jdk.graal.compiler.vector.lir.amd64.AMD64VectorNodeMatchRules;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.riscv64.RISCV64;

class HostedWrapper {
    GraalConfiguration config;

    HostedWrapper(GraalConfiguration config) {
        this.config = config;
    }
}

public class GraalConfiguration {

    private static final String COMPILER_CONFIGURATION_NAME = CommunityCompilerConfigurationFactory.NAME;

    public static GraalConfiguration hostedInstance() {
        return ImageSingletons.lookup(HostedWrapper.class).config;
    }

    public static void setHostedInstanceIfEmpty(GraalConfiguration config) {
        if (!ImageSingletons.contains(HostedWrapper.class)) {
            ImageSingletons.add(HostedWrapper.class, new HostedWrapper(config));
        }
    }

    public static GraalConfiguration runtimeInstance() {
        return ImageSingletons.lookup(GraalConfiguration.class);
    }

    public static void setRuntimeInstance(GraalConfiguration config) {
        ImageSingletons.add(GraalConfiguration.class, config);
    }

    public static void setDefaultIfEmpty() {
        // Avoid constructing a new instance if not necessary
        if (!ImageSingletons.contains(GraalConfiguration.class) || !ImageSingletons.contains(HostedWrapper.class)) {
            GraalConfiguration instance = new GraalConfiguration();
            if (!ImageSingletons.contains(GraalConfiguration.class)) {
                ImageSingletons.add(GraalConfiguration.class, instance);
            }
            if (!ImageSingletons.contains(HostedWrapper.class)) {
                ImageSingletons.add(HostedWrapper.class, new HostedWrapper(instance));
            }
        }
    }

    public LoweringProvider createLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider) {
        return ImageSingletons.lookup(SubstrateLoweringProviderFactory.class).newLoweringProvider(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider,
                        ConfigurationValues.getTarget());
    }

    public Suites createSuites(OptionValues options, @SuppressWarnings("unused") boolean hosted, Architecture arch) {
        return ImageSingletons.lookup(SubstrateSuitesCreatorProvider.class).getSuitesCreator().createSuites(options, arch);
    }

    public Suites createFirstTierSuites(OptionValues options, @SuppressWarnings("unused") boolean hosted, Architecture arch) {
        return ImageSingletons.lookup(SubstrateSuitesCreatorProvider.class).getFirstTierSuitesCreator().createSuites(options, arch);
    }

    public LIRSuites createLIRSuites(OptionValues options) {
        return ImageSingletons.lookup(SubstrateSuitesCreatorProvider.class).getSuitesCreator().createLIRSuites(options);
    }

    public LIRSuites createFirstTierLIRSuites(OptionValues options) {
        return ImageSingletons.lookup(SubstrateSuitesCreatorProvider.class).getFirstTierSuitesCreator().createLIRSuites(options);
    }

    public String getCompilerConfigurationName() {
        return COMPILER_CONFIGURATION_NAME;
    }

    protected void populateMatchRuleRegistry(HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry, Class<? extends NodeMatchRules> c) {
        matchRuleRegistry.put(c, MatchRuleRegistry.createRules(c));
    }

    public void populateMatchRuleRegistry(HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry) {
        /*
         * We generate both types of match rules to enable vectorization during run-time
         * compilation, even if the image was built without vectorization support (e.g., AVX on
         * AMD64 machine). This ensures that Truffle runtime compilation can leverage vectorized
         * code on target machines that support it.
         */
        final Architecture hostedArchitecture = ConfigurationValues.getTarget().arch;
        if (hostedArchitecture instanceof AMD64) {
            populateMatchRuleRegistry(matchRuleRegistry, AMD64NodeMatchRules.class);
            populateMatchRuleRegistry(matchRuleRegistry, AMD64VectorNodeMatchRules.class);
        } else if (hostedArchitecture instanceof AArch64) {
            populateMatchRuleRegistry(matchRuleRegistry, AArch64NodeMatchRules.class);
            populateMatchRuleRegistry(matchRuleRegistry, AArch64VectorNodeMatchRules.class);
        } else if (hostedArchitecture instanceof RISCV64) {
            populateMatchRuleRegistry(matchRuleRegistry, RISCV64NodeMatchRules.class);
        } else {
            throw VMError.shouldNotReachHere("Can not instantiate NodeMatchRules for architecture " + hostedArchitecture.getName());
        }
    }

    public SubstrateBackend createBackend(Providers newProviders) {
        return SubstrateBackendFactory.get().newBackend(newProviders);
    }

    public void removeDeoptTargetOptimizations(@SuppressWarnings("unused") Suites suites) {
    }

    /**
     * Creates the inlining phases that will be used for hosted compilation.
     * <p>
     * Returns a {@link ListIterator} at the position of the last inlining phase or null if no
     * inlining phases were created.
     */
    public ListIterator<BasePhase<? super HighTierContext>> createHostedInliners(@SuppressWarnings("unused") PhaseSuite<HighTierContext> highTier) {
        return null;
    }

    public LoopsDataProvider createLoopsDataProvider() {
        return new LoopsDataProviderImpl();
    }
}
