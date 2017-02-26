/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle;

import com.oracle.truffle.api.Truffle;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.runtime.RuntimeProvider;

public final class DefaultTruffleCompiler extends TruffleCompiler {

    public static TruffleCompiler create(GraalTruffleRuntime runtime) {
        Backend backend = runtime.getRequiredGraalCapability(RuntimeProvider.class).getHostBackend();
        OptionValues options = TruffleCompilerOptions.getOptions();
        Suites suites = backend.getSuites().getDefaultSuites(options);
        LIRSuites lirSuites = backend.getSuites().getDefaultLIRSuites(options);
        GraphBuilderPhase phase = (GraphBuilderPhase) backend.getSuites().getDefaultGraphBuilderSuite().findPhase(GraphBuilderPhase.class).previous();
        Plugins plugins = phase.getGraphBuilderConfig().getPlugins();
        SnippetReflectionProvider snippetReflection = runtime.getRequiredGraalCapability(SnippetReflectionProvider.class);
        return new DefaultTruffleCompiler(plugins, suites, lirSuites, backend, snippetReflection);
    }

    private DefaultTruffleCompiler(Plugins plugins, Suites suites, LIRSuites lirSuites, Backend backend, SnippetReflectionProvider snippetReflection) {
        super(plugins, suites, lirSuites, backend, snippetReflection);
    }

    public static TruffleCompiler createWithSuites(GraalTruffleRuntime runtime, Suites suites) {
        Backend backend = runtime.getRequiredGraalCapability(RuntimeProvider.class).getHostBackend();
        OptionValues options = TruffleCompilerOptions.getOptions();
        LIRSuites lirSuites = backend.getSuites().getDefaultLIRSuites(options);
        GraphBuilderPhase phase = (GraphBuilderPhase) backend.getSuites().getDefaultGraphBuilderSuite().findPhase(GraphBuilderPhase.class).previous();
        Plugins plugins = phase.getGraphBuilderConfig().getPlugins();
        SnippetReflectionProvider snippetReflection = runtime.getRequiredGraalCapability(SnippetReflectionProvider.class);
        return new DefaultTruffleCompiler(plugins, suites, lirSuites, backend, snippetReflection);
    }

    @Override
    protected PartialEvaluator createPartialEvaluator() {
        return new PartialEvaluator(providers, config, snippetReflection, backend.getTarget().arch, ((GraalTruffleRuntime) Truffle.getRuntime()).getInstrumentation());
    }

    @Override
    protected PhaseSuite<HighTierContext> createGraphBuilderSuite() {
        PhaseSuite<HighTierContext> suite = backend.getSuites().getDefaultGraphBuilderSuite().copy();
        return suite;
    }
}
