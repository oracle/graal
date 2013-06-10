/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.test;

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.phases.GraalOptions.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;

/**
 * use
 * 
 * <pre>
 * mx unittest AheadOfTimeCompilationTest @-XX:CompileCommand='print,*AheadOfTimeCompilationTest.*'
 * </pre>
 * 
 * to print disassembly.
 */
public class AheadOfTimeCompilationTest extends GraalCompilerTest {

    public static final Object STATICFINALOBJECT = new Object();

    public static Object getStaticFinalObject() {
        return AheadOfTimeCompilationTest.STATICFINALOBJECT;
    }

    @Test
    @Ignore
    public void testStaticFinalObject1() {
        StructuredGraph result2 = compile("getStaticFinalObject", true);
        assert result2.getNodes().filter(ConstantNode.class).count() == 1;
        assert result2.getNodes(FloatingReadNode.class).count() == 1;
    }

    @Test
    public void testStaticFinalObject2() {
        StructuredGraph result1 = compile("getStaticFinalObject", false);
        assert result1.getNodes().filter(ConstantNode.class).count() == 1;
        assert result1.getNodes(FloatingReadNode.class).count() == 0;
    }

    private StructuredGraph compile(String test, boolean compileAOT) {
        StructuredGraph graph = parse(test);
        ResolvedJavaMethod method = graph.method();

        boolean originalSetting = OptCanonicalizeReads.getValue();
        OptCanonicalizeReads.setValue(!compileAOT);
        PhasePlan phasePlan = new PhasePlan();
        final StructuredGraph graphCopy = graph.copy();
        GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
        editPhasePlan(method, graph, phasePlan);
        CallingConvention cc = getCallingConvention(runtime, Type.JavaCallee, graph.method(), false);
        final CompilationResult compResult = GraalCompiler.compileGraph(graph, cc, method, runtime, replacements, backend, runtime().getTarget(), null, phasePlan, OptimisticOptimizations.ALL,
                        new SpeculationLog(), suites);
        addMethod(method, compResult, graphCopy);

        OptCanonicalizeReads.setValue(originalSetting);

        return graph;
    }
}
