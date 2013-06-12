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
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.tiers.*;

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
    public void testStaticFinalObjectAOT() {
        StructuredGraph result = compile("getStaticFinalObject", true);
        assert result.getNodes().filter(ConstantNode.class).count() == 1;
        assert result.getNodes().filter(ConstantNode.class).first().kind() == runtime.getTarget().wordKind;
        assert result.getNodes(FloatingReadNode.class).count() == 2;
        assert result.getNodes(ReadNode.class).count() == 0;
    }

    @Test
    public void testStaticFinalObject() {
        StructuredGraph result = compile("getStaticFinalObject", false);
        assert result.getNodes().filter(ConstantNode.class).count() == 1;
        assert result.getNodes().filter(ConstantNode.class).first().kind() == Kind.Object;
        assert result.getNodes(FloatingReadNode.class).count() == 0;
        assert result.getNodes(ReadNode.class).count() == 0;
    }

    public static Class getClassObject() {
        return AheadOfTimeCompilationTest.class;
    }

    @Test
    public void testClassObjectAOT() {
        StructuredGraph result = compile("getClassObject", true);

        NodeIterable<ConstantNode> filter = result.getNodes().filter(ConstantNode.class);
        assert filter.count() == 1;
        HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) runtime.lookupJavaType(AheadOfTimeCompilationTest.class);
        assert filter.first().asConstant().equals(type.klass());

        assert result.getNodes(FloatingReadNode.class).count() == 1;
        assert result.getNodes(ReadNode.class).count() == 0;
    }

    @Test
    public void testClassObject() {
        StructuredGraph result = compile("getClassObject", false);

        NodeIterable<ConstantNode> filter = result.getNodes().filter(ConstantNode.class);
        assert filter.count() == 1;
        Object mirror = filter.first().asConstant().asObject();
        assert mirror.getClass().equals(Class.class);
        assert mirror.equals(AheadOfTimeCompilationTest.class);

        assert result.getNodes(FloatingReadNode.class).count() == 0;
        assert result.getNodes(ReadNode.class).count() == 0;
    }

    public static Class getPrimitiveClassObject() {
        return int.class;
    }

    @Test
    public void testPrimitiveClassObjectAOT() {
        StructuredGraph result = compile("getPrimitiveClassObject", true);
        NodeIterable<ConstantNode> filter = result.getNodes().filter(ConstantNode.class);
        assert filter.count() == 1;
        assert filter.first().kind() == runtime.getTarget().wordKind;

        assert result.getNodes(FloatingReadNode.class).count() == 2;
        assert result.getNodes(ReadNode.class).count() == 0;
    }

    @Test
    public void testPrimitiveClassObject() {
        StructuredGraph result = compile("getPrimitiveClassObject", false);
        NodeIterable<ConstantNode> filter = result.getNodes().filter(ConstantNode.class);
        assert filter.count() == 1;
        Object mirror = filter.first().asConstant().asObject();
        assert mirror.getClass().equals(Class.class);
        assert mirror.equals(Integer.TYPE);

        assert result.getNodes(FloatingReadNode.class).count() == 0;
        assert result.getNodes(ReadNode.class).count() == 0;
    }

    private StructuredGraph compile(String test, boolean compileAOT) {
        StructuredGraph graph = parse(test);
        ResolvedJavaMethod method = graph.method();

        boolean originalSetting = AOTCompilation.getValue();
        AOTCompilation.setValue(compileAOT);
        PhasePlan phasePlan = new PhasePlan();
        final StructuredGraph graphCopy = graph.copy();
        GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL);
        phasePlan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
        editPhasePlan(method, graph, phasePlan);
        CallingConvention cc = getCallingConvention(runtime, Type.JavaCallee, graph.method(), false);
        // create suites everytime, as we modify options for the compiler
        final Suites suitesLocal = Graal.getRequiredCapability(SuitesProvider.class).createSuites();
        final CompilationResult compResult = GraalCompiler.compileGraph(graph, cc, method, runtime, replacements, backend, runtime().getTarget(), null, phasePlan, OptimisticOptimizations.ALL,
                        new SpeculationLog(), suitesLocal);
        addMethod(method, compResult, graphCopy);

        AOTCompilation.setValue(originalSetting);

        return graph;
    }
}
