/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumMap;
import java.util.Map;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig.ConfigKind;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Prepares snippet graphs with a specific set of providers. Use an additional policy when a Native
 * Image feature needs the same snippets prepared with different folding or specialization decisions.
 * The default policy uses the standard providers; {@link HostedConfiguration} subclasses can provide
 * additional policies through {@code getAdditionalSnippetCompilationPolicies()}, replacing
 * constant-reflection, constant-field or meta-access-extension providers.
 * <p>
 * Register snippet declarations with the default policy's replacements; they are forwarded to every
 * additional policy. Each policy parses and analyzes the declarations with its analysis providers,
 * then transplants its graphs to hosted metadata for compilation. Policies share the default policy's
 * registry of snippet template caches, while each prepares separate encodings and specialized-template
 * graphs.
 * <p>
 * The {@link #forAnalysis} and {@link #forCompilation} hooks run once per image and must describe
 * matching policies in the analysis and hosted metadata universes. Analysis preparation must preserve
 * every operation needed by compilation. Retained operations must have compatible runtime storage and
 * layout; a policy that preserves otherwise folded static-field reads must explicitly request their
 * storage. Provider behavior must remain stable after preparation. Application graphs require their own
 * compatible analysis policy; this class prepares snippets only.
 */
public class SnippetCompilationPolicy {
    /** Uses analysis metadata to prepare snippet graphs and discover their analysis dependencies. */
    private HostedProviders analysisProviders;
    /** Hosted providers, including the replacements owning this policy's encoding and template cache. */
    private HostedProviders hostedProviders;
    /**
     * Bundles selected for compilation: share {@link #hostedProviders}' providers, plugins and snippet cache,
     * while retaining each backend kind's code cache and register configuration.
     */
    private final Map<ConfigKind, HostedProviders> compilationProviders = new EnumMap<>(ConfigKind.class);

    /**
     * Selects providers for snippet graph preparation before analysis. Overrides may change folding
     * decisions. A copied provider bundle retains its lowerer, which uses its original meta-access
     * extension provider. Overrides must preserve the field and array element storage kinds and
     * object layout assumed by that lowerer.
     */
    protected HostedProviders forAnalysis(HostedProviders providers) {
        return providers;
    }

    /** Creates a policy's replacements before any snippet declarations are registered. */
    public final HostedProviders createAnalysisProviders(HostedProviders original) {
        VMError.guarantee(!BuildPhaseProvider.isAnalysisStarted(), "Snippet compilation policies must be prepared before analysis starts");
        VMError.guarantee(analysisProviders == null, "Snippet compilation policy registered twice");
        HostedProviders providers = forAnalysis(original);
        SnippetCompilationPolicy defaultPolicy = HostedConfiguration.instance().getDefaultSnippetCompilationPolicy();
        SubstrateReplacements replacements;
        if (this == defaultPolicy) {
            replacements = new DefaultAnalysisReplacements(providers);
        } else {
            var templateRegistry = defaultPolicy.getAnalysisReplacements().getSnippetTemplateRegistry();
            replacements = new SubstrateReplacements(providers, new ResolvedJavaMethodBytecodeProvider(), SubstrateTarget.singleton(), new SubstrateGraphMakerFactory(),
                            templateRegistry);
        }
        analysisProviders = (HostedProviders) replacements.getProviders();
        return analysisProviders;
    }

    public final HostedProviders getAnalysisProviders() {
        return analysisProviders;
    }

    public final SubstrateReplacements getAnalysisReplacements() {
        return (SubstrateReplacements) analysisProviders.getReplacements();
    }

    /**
     * Selects matching providers for decoding, specializing and compiling the analyzed variants.
     * The storage and layout requirements of {@link #forAnalysis} apply here as well.
     */
    protected HostedProviders forCompilation(HostedProviders providers) {
        return providers;
    }

    /** Creates the hosted providers for this policy, before plugin registration and backend preparation. */
    public final HostedProviders createHostedProviders(HostedProviders original, HostedUniverse universe) {
        VMError.guarantee(!BuildPhaseProvider.isReadyForCompilation(), "Snippet compilation policies must be prepared before compilation starts");
        VMError.guarantee(analysisProviders != null, "Snippet compilation policy was not prepared before analysis");
        VMError.guarantee(hostedProviders == null, "Snippet compilation policy prepared twice for hosted compilation");
        ResolvedJavaMethodBytecodeProvider bytecodeProvider = new ResolvedJavaMethodBytecodeProvider();
        HostedProviders providers = forCompilation(original);
        SnippetCompilationPolicy defaultPolicy = HostedConfiguration.instance().getDefaultSnippetCompilationPolicy();
        SubstrateReplacements analysisReplacements = getAnalysisReplacements();
        HostedReplacements replacements;
        if (this == defaultPolicy) {
            // The default policy creates the SnippetTemplateCache registry; additional policies share it.
            replacements = new HostedReplacements(universe, providers, SubstrateTarget.singleton(), analysisReplacements, bytecodeProvider);
        } else {
            var templateRegistry = defaultPolicy.getHostedReplacements().getSnippetTemplateRegistry();
            replacements = new HostedReplacements(universe, providers, SubstrateTarget.singleton(), analysisReplacements, bytecodeProvider, templateRegistry);
        }
        hostedProviders = replacements.getProviders();
        return hostedProviders;
    }

    public final HostedReplacements getHostedReplacements() {
        return (HostedReplacements) hostedProviders.getReplacements();
    }

    /**
     * Prepares providers for each backend after hosted plugin registration, preserving its code cache
     * and register configuration. The default policy reuses existing bundles; additional policies
     * share their hosted providers and plugins across backend-specific copies.
     */
    public final void prepareBackendProviders(RuntimeConfiguration runtimeConfig) {
        VMError.guarantee(!BuildPhaseProvider.isReadyForCompilation(), "Snippet compilation policies must be prepared before compilation starts");
        SnippetCompilationPolicy defaultPolicy = HostedConfiguration.instance().getDefaultSnippetCompilationPolicy();
        for (var backend : runtimeConfig.getBackends()) {
            HostedProviders original = (HostedProviders) backend.getProviders();
            HostedProviders selected;
            if (this == defaultPolicy) {
                // Default backend bundles already carry this policy's providers.
                original.setGraphBuilderPlugins(hostedProviders.getGraphBuilderPlugins());
                selected = original;
            } else {
                selected = hostedProviders.copyWith(original.getCodeCache());
            }
            SubstrateRegisterConfig registerConfig = (SubstrateRegisterConfig) original.getCodeCache().getRegisterConfig();
            compilationProviders.put(registerConfig.getConfigKind(), selected);
        }
    }

    /**
     * Returns this policy's prepared providers for a backend's register configuration kind.
     * Preparation finishes before concurrent compilation begins; a policy cannot be added on demand
     * after analysis.
     */
    public final HostedProviders getCompilationProviders(ConfigKind configKind) {
        HostedProviders selected = compilationProviders.get(configKind);
        VMError.guarantee(selected != null, "Snippet compilation policy was not prepared for this backend before compilation");
        return selected;
    }

    /**
     * Forwards snippet-method registrations from the default policy to every additional policy for
     * independent preparation. All analysis replacements must exist before registration.
     */
    private static final class DefaultAnalysisReplacements extends SubstrateReplacements {
        DefaultAnalysisReplacements(HostedProviders providers) {
            super(providers, new ResolvedJavaMethodBytecodeProvider(), SubstrateTarget.singleton(), new SubstrateGraphMakerFactory());
        }

        @Override
        public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
            super.registerSnippet(method, original, receiver, trackNodeSourcePosition, options);
            // Additional replacements are created after this dispatcher, but before registration.
            HostedConfiguration configuration = HostedConfiguration.instance();
            for (SnippetCompilationPolicy policy : configuration.getSnippetCompilationPolicies()) {
                if (policy != configuration.getDefaultSnippetCompilationPolicy()) {
                    policy.getAnalysisReplacements().registerSnippet(method, original, receiver, trackNodeSourcePosition, options);
                }
            }
        }
    }
}
