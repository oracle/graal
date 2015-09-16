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
package com.oracle.graal.compiler.test;

import static com.oracle.graal.compiler.GraalCompiler.compileGraph;
import static com.oracle.graal.compiler.GraalCompiler.getProfilingInfo;
import static com.oracle.graal.compiler.common.GraalOptions.OptAssumptions;
import static jdk.internal.jvmci.code.CodeUtil.getCallingConvention;
import static org.junit.Assert.assertNotNull;
import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.code.CallingConvention.Type;
import jdk.internal.jvmci.code.CompilationResult;
import jdk.internal.jvmci.code.CompilationResult.Call;
import jdk.internal.jvmci.code.CompilationResult.Infopoint;
import jdk.internal.jvmci.code.InfopointReason;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;

import org.junit.Test;

import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.nodes.FullInfopointNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;

/**
 * Test that infopoints in {@link CompilationResult}s have correctly assigned reasons.
 */
public class InfopointReasonTest extends GraalCompilerTest {

    public static final String[] STRINGS = new String[]{"world", "everyone", "you"};

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
        CallingConvention cc = getCallingConvention(getCodeCache(), Type.JavaCallee, graph.method(), false);
        final CompilationResult cr = compileGraph(graph, cc, graph.method(), getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL, getProfilingInfo(graph),
                        getSuites(), getLIRSuites(), new CompilationResult(), CompilationResultBuilderFactory.Default);
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
        final StructuredGraph graph = parseDebug(method, AllowAssumptions.from(OptAssumptions.getValue()));
        int graphLineSPs = 0;
        for (FullInfopointNode ipn : graph.getNodes().filter(FullInfopointNode.class)) {
            if (ipn.getReason() == InfopointReason.LINE_NUMBER) {
                ++graphLineSPs;
            }
        }
        assertTrue(graphLineSPs > 0);
        CallingConvention cc = getCallingConvention(getCodeCache(), Type.JavaCallee, graph.method(), false);
        PhaseSuite<HighTierContext> graphBuilderSuite = getCustomGraphBuilderSuite(GraphBuilderConfiguration.getFullDebugDefault(getDefaultGraphBuilderPlugins()));
        final CompilationResult cr = compileGraph(graph, cc, graph.method(), getProviders(), getBackend(), graphBuilderSuite, OptimisticOptimizations.ALL, getProfilingInfo(graph), getSuites(),
                        getLIRSuites(), new CompilationResult(), CompilationResultBuilderFactory.Default);
        int lineSPs = 0;
        for (Infopoint sp : cr.getInfopoints()) {
            assertNotNull(sp.reason);
            if (sp.reason == InfopointReason.LINE_NUMBER) {
                ++lineSPs;
            }
        }
        assertTrue(lineSPs > 0);
    }

}
