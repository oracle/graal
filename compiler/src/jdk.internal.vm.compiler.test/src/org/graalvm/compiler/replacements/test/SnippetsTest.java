/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.Builder;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class SnippetsTest extends ReplacementsTest {

    protected final ReplacementsImpl installer;
    protected final ClassfileBytecodeProvider bytecodeProvider;

    @SuppressWarnings("this-escape")
    protected SnippetsTest() {
        ReplacementsImpl d = (ReplacementsImpl) getReplacements();
        bytecodeProvider = getSystemClassLoaderBytecodeProvider();
        installer = new ReplacementsImpl(null, d.getProviders(), d.snippetReflection, bytecodeProvider, d.target) {

            @Override
            protected GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
                return new GraphMaker(this, substitute, original) {

                    @Override
                    protected Instance createGraphBuilder(Providers providers1, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                                    IntrinsicContext initialIntrinsicContext) {
                        return new GraphBuilderPhase.Instance(providers1, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
                    }
                };
            }

        };
        installer.setGraphBuilderPlugins(d.getGraphBuilderPlugins());
    }

    @Override
    protected StructuredGraph parse(Builder builder, PhaseSuite<HighTierContext> graphBuilderSuite) {
        return installer.makeGraph(getDebugContext(), bytecodeProvider, builder.getMethod(), null, null, null, false, null);
    }
}
