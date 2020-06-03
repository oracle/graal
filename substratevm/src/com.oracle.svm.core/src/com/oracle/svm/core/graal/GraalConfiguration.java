/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.aarch64.AArch64NodeMatchRules;
import org.graalvm.compiler.core.amd64.AMD64NodeMatchRules;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.core.match.MatchRuleRegistry;
import org.graalvm.compiler.core.match.MatchStatement;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.CommunityCompilerConfigurationFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.code.SubstrateLoweringProviderFactory;
import com.oracle.svm.core.graal.code.SubstrateSuitesCreatorProvider;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.MetaAccessProvider;

public class GraalConfiguration {

    private static final String COMPILER_CONFIGURATION_NAME = CommunityCompilerConfigurationFactory.NAME;

    public static GraalConfiguration instance() {
        return ImageSingletons.lookup(GraalConfiguration.class);
    }

    public static void setDefaultIfEmpty() {
        if (!ImageSingletons.contains(GraalConfiguration.class)) {
            ImageSingletons.add(GraalConfiguration.class, new GraalConfiguration());
        }
    }

    public LoweringProvider createLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider) {
        return ImageSingletons.lookup(SubstrateLoweringProviderFactory.class).newLoweringProvider(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider,
                        ConfigurationValues.getTarget());
    }

    public Suites createSuites(OptionValues options, @SuppressWarnings("unused") boolean hosted) {
        return ImageSingletons.lookup(SubstrateSuitesCreatorProvider.class).getSuitesCreator().createSuites(options);
    }

    public Suites createFirstTierSuites(OptionValues options, @SuppressWarnings("unused") boolean hosted) {
        return ImageSingletons.lookup(SubstrateSuitesCreatorProvider.class).getFirstTierSuitesCreator().createSuites(options);
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

    public void populateMatchRuleRegistry(HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry) {
        Class<? extends NodeMatchRules> matchRuleClass;
        final Architecture hostedArchitecture = ConfigurationValues.getTarget().arch;
        if (hostedArchitecture instanceof AMD64) {
            matchRuleClass = AMD64NodeMatchRules.class;
        } else if (hostedArchitecture instanceof AArch64) {
            matchRuleClass = AArch64NodeMatchRules.class;
        } else {
            throw VMError.shouldNotReachHere("Can not instantiate NodeMatchRules for architecture " + hostedArchitecture.getName());
        }

        matchRuleRegistry.put(matchRuleClass, MatchRuleRegistry.createRules(matchRuleClass));
    }

    public SubstrateBackend createBackend(Providers newProviders) {
        return SubstrateBackendFactory.get().newBackend(newProviders);
    }

    public void runAdditionalCompilerPhases(@SuppressWarnings("unused") StructuredGraph graph, @SuppressWarnings("unused") Feature graalFeature) {
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
}
