/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static jdk.graal.compiler.core.common.GraalOptions.OptAssumptions;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test that infopoints in {@link CompilationResult}s have correctly assigned reasons.
 */
public class InfopointReasonTest extends GraalCompilerTest {

    public static final String[] STRINGS = new String[]{"world", "everyone", "you"};

    @SuppressWarnings("this-escape")
    public InfopointReasonTest() {
        // Call testMethod to ensure all method references are resolved.
        testMethod();
    }

    public String testMethod() {
        StringBuilder sb = new StringBuilder("Hello ");
        for (String s : STRINGS) {
            sb.append(s).append(", ");
        }
        sb.replace(sb.length() - 2, sb.length(), "!");
        return sb.toString();
    }

    @Test
    public void callInfopoints() {
        final ResolvedJavaMethod method = getResolvedJavaMethod("testMethod");
        final StructuredGraph graph = parseEager(method, AllowAssumptions.YES);
        Providers providers = getProviders();
        Backend backend = getBackend();
        PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
        ProfilingInfo profilingInfo = graph.getProfilingInfo();
        Suites suites = createSuites(graph.getOptions());
        LIRSuites lirSuites = createLIRSuites(graph.getOptions());
        CompilationResult compilationResult = new CompilationResult(graph.compilationId());
        final CompilationResult cr = GraalCompiler.compile(new GraalCompiler.Request<>(graph,
                        graph.method(),
                        providers,
                        backend,
                        graphBuilderSuite,
                        OptimisticOptimizations.ALL,
                        profilingInfo,
                        suites,
                        lirSuites,
                        compilationResult,
                        CompilationResultBuilderFactory.Default,
                        true));
        for (Infopoint sp : cr.getInfopoints()) {
            assertNotNull(sp.reason);
            if (sp instanceof Call) {
                assertDeepEquals(InfopointReason.CALL, sp.reason);
            }
        }
    }

    @Test
    public void lineInfopoints() {
        final ResolvedJavaMethod method = getResolvedJavaMethod("testMethod");
        final StructuredGraph graph = parse(builder(method, AllowAssumptions.ifTrue(OptAssumptions.getValue(getInitialOptions()))), getDebugGraphBuilderSuite());
        int graphLineSPs = 0;
        for (FullInfopointNode ipn : graph.getNodes().filter(FullInfopointNode.class)) {
            if (ipn.getReason() == InfopointReason.BYTECODE_POSITION) {
                ++graphLineSPs;
            }
        }
        assertTrue(graphLineSPs > 0);
        PhaseSuite<HighTierContext> graphBuilderSuite = getCustomGraphBuilderSuite(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withFullInfopoints(true));
        Providers providers = getProviders();
        Backend backend = getBackend();
        ProfilingInfo profilingInfo = graph.getProfilingInfo();
        Suites suites = createSuites(graph.getOptions());
        LIRSuites lirSuites = createLIRSuites(graph.getOptions());
        CompilationResult compilationResult = new CompilationResult(graph.compilationId());
        final CompilationResult cr = GraalCompiler.compile(new GraalCompiler.Request<>(graph,
                        graph.method(),
                        providers,
                        backend,
                        graphBuilderSuite,
                        OptimisticOptimizations.ALL,
                        profilingInfo,
                        suites,
                        lirSuites,
                        compilationResult,
                        CompilationResultBuilderFactory.Default,
                        true));
        int lineSPs = 0;
        for (Infopoint sp : cr.getInfopoints()) {
            assertNotNull(sp.reason);
            if (sp.reason == InfopointReason.BYTECODE_POSITION) {
                ++lineSPs;
            }
        }
        assertTrue(lineSPs > 0);
    }

}
