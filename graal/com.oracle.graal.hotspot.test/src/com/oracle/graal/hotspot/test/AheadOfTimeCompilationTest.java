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
import static com.oracle.graal.compiler.GraalCompiler.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.nodes.ConstantNode.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.options.*;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.runtime.*;

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
    public static final String STATICFINALSTRING = "test string";

    public static Object getStaticFinalObject() {
        return AheadOfTimeCompilationTest.STATICFINALOBJECT;
    }

    @Test
    public void testStaticFinalObjectAOT() {
        StructuredGraph result = compile("getStaticFinalObject", true);
        assertDeepEquals(1, getConstantNodes(result).count());
        assertDeepEquals(getCodeCache().getTarget().wordKind, getConstantNodes(result).first().getKind());
        assertDeepEquals(2, result.getNodes(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testStaticFinalObject() {
        StructuredGraph result = compile("getStaticFinalObject", false);
        assertDeepEquals(1, getConstantNodes(result).count());
        assertDeepEquals(Kind.Object, getConstantNodes(result).first().getKind());
        assertDeepEquals(0, result.getNodes(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    public static Class<AheadOfTimeCompilationTest> getClassObject() {
        return AheadOfTimeCompilationTest.class;
    }

    @Test
    public void testClassObjectAOT() {
        StructuredGraph result = compile("getClassObject", true);

        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) getMetaAccess().lookupJavaType(AheadOfTimeCompilationTest.class);
        assertDeepEquals(type.klass(), filter.first().asConstant());

        assertDeepEquals(1, result.getNodes(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testClassObject() {
        StructuredGraph result = compile("getClassObject", false);

        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        Object mirror = HotSpotObjectConstant.asObject(filter.first().asConstant());
        assertDeepEquals(Class.class, mirror.getClass());
        assertDeepEquals(AheadOfTimeCompilationTest.class, mirror);

        assertDeepEquals(0, result.getNodes(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    public static Class<Integer> getPrimitiveClassObject() {
        return int.class;
    }

    @Test
    public void testPrimitiveClassObjectAOT() {
        StructuredGraph result = compile("getPrimitiveClassObject", true);
        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        assertDeepEquals(getCodeCache().getTarget().wordKind, filter.first().getKind());

        assertDeepEquals(2, result.getNodes(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testPrimitiveClassObject() {
        StructuredGraph result = compile("getPrimitiveClassObject", false);
        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        Object mirror = HotSpotObjectConstant.asObject(filter.first().asConstant());
        assertDeepEquals(Class.class, mirror.getClass());
        assertDeepEquals(Integer.TYPE, mirror);

        assertDeepEquals(0, result.getNodes(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    public static String getStringObject() {
        return AheadOfTimeCompilationTest.STATICFINALSTRING;
    }

    @Test
    public void testStringObjectAOT() {
        // embedded strings are fine
        testStringObjectCommon(true);
    }

    @Test
    public void testStringObject() {
        testStringObjectCommon(false);
    }

    private void testStringObjectCommon(boolean compileAOT) {
        StructuredGraph result = compile("getStringObject", compileAOT);

        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        Object mirror = HotSpotObjectConstant.asObject(filter.first().asConstant());
        assertDeepEquals(String.class, mirror.getClass());
        assertDeepEquals("test string", mirror);

        assertDeepEquals(0, result.getNodes(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    public static Boolean getBoxedBoolean() {
        return Boolean.valueOf(true);
    }

    @Ignore("ImmutableCode override may not work reliably in non-hosted mode")
    @Test
    public void testBoxedBooleanAOT() {
        StructuredGraph result = compile("getBoxedBoolean", true);

        assertDeepEquals(2, result.getNodes(FloatingReadNode.class).count());
        assertDeepEquals(1, result.getNodes(PiNode.class).count());
        assertDeepEquals(1, getConstantNodes(result).count());
        ConstantNode constant = getConstantNodes(result).first();
        assertDeepEquals(Kind.Long, constant.getKind());
        assertDeepEquals(((HotSpotResolvedObjectType) getMetaAccess().lookupJavaType(Boolean.class)).klass(), constant.asConstant());
    }

    @Test
    public void testBoxedBoolean() {
        StructuredGraph result = compile("getBoxedBoolean", false);
        assertDeepEquals(0, result.getNodes(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes(PiNode.class).count());
        assertDeepEquals(1, getConstantNodes(result).count());
        ConstantNode constant = getConstantNodes(result).first();
        assertDeepEquals(Kind.Object, constant.getKind());
        assertDeepEquals(Boolean.TRUE, HotSpotObjectConstant.asObject(constant.asConstant()));
    }

    private StructuredGraph compile(String test, boolean compileAOT) {
        StructuredGraph graph = parseEager(test);
        ResolvedJavaMethod method = graph.method();

        try (OverrideScope s = OptionValue.override(ImmutableCode, compileAOT)) {
            CallingConvention cc = getCallingConvention(getCodeCache(), Type.JavaCallee, graph.method(), false);
            // create suites everytime, as we modify options for the compiler
            final Suites suitesLocal = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().createSuites();
            final CompilationResult compResult = compileGraph(graph, null, cc, method, getProviders(), getBackend(), getCodeCache().getTarget(), null, getDefaultGraphBuilderSuite(),
                            OptimisticOptimizations.ALL, getProfilingInfo(graph), getSpeculationLog(), suitesLocal, new CompilationResult(), CompilationResultBuilderFactory.Default);
            addMethod(method, compResult);
        }

        return graph;
    }
}
