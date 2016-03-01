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

import jdk.vm.ci.meta.ResolvedJavaMethod;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.java.BytecodeParser;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.IntrinsicContext;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;
import com.oracle.graal.runtime.RuntimeProvider;
import com.oracle.graal.truffle.phases.InstrumentBranchesPhase;

public final class DefaultTruffleCompiler extends TruffleCompiler {

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
        super(plugins, suites, lirSuites, backend, snippetReflection);
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
        iterator.add(new InstrumentedGraphBuilderPhase(config));
        return suite;
    }

    public static class InstrumentedGraphBuilderPhase extends GraphBuilderPhase {
        public InstrumentedGraphBuilderPhase(GraphBuilderConfiguration config) {
            super(config);
        }

        @Override
        protected void run(StructuredGraph graph, HighTierContext context) {
            new InstrumentedGraphBuilderPhase.Instance(context, getGraphBuilderConfig(), null).run(graph);
        }

        public static class Instance extends GraphBuilderPhase.Instance {
            public Instance(HighTierContext context, GraphBuilderConfiguration config, IntrinsicContext intrinsicContext) {
                super(context.getMetaAccess(), context.getStampProvider(), context.getConstantReflection(),
                        config, context.getOptimisticOptimizations(), intrinsicContext);
            }

            @Override
            protected void run(StructuredGraph graph) {
                super.run(graph);
            }

            protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent,
                                                          ResolvedJavaMethod method, int entryBCI,
                                                          IntrinsicContext intrinsicContext) {
                return new BytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext) {
                    @Override
                    protected void postProcessIfNode(ValueNode node) {
                        if (TruffleCompilerOptions.TruffleInstrumentBranches.getValue()) {
                            InstrumentBranchesPhase.addNodeSourceLocation(node, createBytecodePosition());
                        }
                    }
                };
            }
        }
    }
}
