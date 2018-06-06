/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.core.amd64.AMD64NodeMatchRules;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.core.match.MatchRuleRegistry;
import org.graalvm.compiler.core.match.MatchStatement;
import org.graalvm.compiler.core.phases.CommunityCompilerConfiguration;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.CommunityCompilerConfigurationFactory;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.amd64.SubstrateAMD64Backend;
import com.oracle.svm.core.graal.meta.SubstrateBasicLoweringProvider;

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

    public LoweringProvider createLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls) {
        return new SubstrateBasicLoweringProvider(metaAccess, foreignCalls, ConfigurationValues.getTarget());
    }

    public Suites createSuites(OptionValues options, @SuppressWarnings("unused") boolean hosted) {
        return Suites.createSuites(new CommunityCompilerConfiguration(), options);
    }

    public String getCompilerConfigurationName() {
        return COMPILER_CONFIGURATION_NAME;
    }

    public void populateMatchRuleRegistry(HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry) {
        matchRuleRegistry.put(AMD64NodeMatchRules.class, MatchRuleRegistry.createRules(AMD64NodeMatchRules.class));
    }

    public Backend createBackend(Providers newProviders) {
        return new SubstrateAMD64Backend(newProviders);
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
