/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import java.util.ListIterator;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;
import com.oracle.graal.runtime.RuntimeProvider;

public final class DefaultTruffleCompiler extends TruffleCompiler {

    private final SnippetReflectionProvider snippetReflection;

    public static TruffleCompiler create(GraalTruffleRuntime runtime) {
        Backend backend = runtime.getRequiredGraalCapability(RuntimeProvider.class).getHostBackend();
        Suites suites = backend.getSuites().getDefaultSuites();
        LIRSuites lirSuites = backend.getSuites().getDefaultLIRSuites();
        GraphBuilderPhase phase = (GraphBuilderPhase) backend.getSuites().getDefaultGraphBuilderSuite().findPhase(GraphBuilderPhase.class).previous();
        Plugins plugins = phase.getGraphBuilderConfig().getPlugins();
        SnippetReflectionProvider snippetReflection = runtime.getRequiredGraalCapability(SnippetReflectionProvider.class);
        return new DefaultTruffleCompiler(plugins, suites, lirSuites, backend, snippetReflection);
    }

    private DefaultTruffleCompiler(Plugins plugins, Suites suites, LIRSuites lirSuites, Backend backend, SnippetReflectionProvider snippetReflection) {
        super(plugins, suites, lirSuites, backend);
        this.snippetReflection = snippetReflection;
    }

    @Override
    protected PartialEvaluator createPartialEvaluator() {
        return new PartialEvaluator(providers, config, snippetReflection, backend.getTarget().arch);
    }

    @Override
    protected PhaseSuite<HighTierContext> createGraphBuilderSuite() {
        PhaseSuite<HighTierContext> suite = backend.getSuites().getDefaultGraphBuilderSuite().copy();
        ListIterator<BasePhase<? super HighTierContext>> iterator = suite.findPhase(GraphBuilderPhase.class);
        iterator.remove();
        iterator.add(new GraphBuilderPhase(config));
        return suite;
    }
}
