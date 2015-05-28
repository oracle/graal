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
package com.oracle.graal.hotspot.test;

import static com.oracle.graal.compiler.GraalCompiler.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.nodes.ConstantNode.*;
import static com.oracle.jvmci.code.CodeUtil.*;

import org.junit.*;

import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.runtime.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.code.CallingConvention.Type;
import com.oracle.jvmci.hotspot.*;
import com.oracle.jvmci.meta.*;
import com.oracle.jvmci.options.*;
import com.oracle.jvmci.options.OptionValue.OverrideScope;

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
        Stamp constantStamp = getConstantNodes(result).first().stamp();
        Assert.assertTrue(constantStamp.toString(), constantStamp instanceof KlassPointerStamp);
        assertDeepEquals(2, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testStaticFinalObject() {
        StructuredGraph result = compile("getStaticFinalObject", false);
        assertDeepEquals(1, getConstantNodes(result).count());
        assertDeepEquals(Kind.Object, getConstantNodes(result).first().getKind());
        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
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

        assertDeepEquals(1, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testClassObject() {
        StructuredGraph result = compile("getClassObject", false);

        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        HotSpotObjectConstantImpl c = (HotSpotObjectConstantImpl) filter.first().asConstant();
        Assert.assertEquals(Class.class, c.getObjectClass());
        Assert.assertTrue(c.isEqualTo(AheadOfTimeCompilationTest.class));

        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
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
        Stamp constantStamp = filter.first().stamp();
        Assert.assertTrue(constantStamp instanceof KlassPointerStamp);

        assertDeepEquals(2, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testPrimitiveClassObject() {
        StructuredGraph result = compile("getPrimitiveClassObject", false);
        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        HotSpotObjectConstantImpl c = (HotSpotObjectConstantImpl) filter.first().asConstant();
        Assert.assertEquals(Class.class, c.getObjectClass());
        Assert.assertTrue(c.isEqualTo(Integer.TYPE));

        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
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
        HotSpotObjectConstantImpl c = (HotSpotObjectConstantImpl) filter.first().asConstant();
        Assert.assertEquals(String.class, c.getObjectClass());
        Assert.assertTrue(c.isEqualTo("test string"));

        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    public static Boolean getBoxedBoolean() {
        return Boolean.valueOf(true);
    }

    @Ignore("ImmutableCode override may not work reliably in non-hosted mode")
    @Test
    public void testBoxedBooleanAOT() {
        StructuredGraph result = compile("getBoxedBoolean", true);

        assertDeepEquals(2, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(1, result.getNodes(PiNode.TYPE).count());
        assertDeepEquals(1, getConstantNodes(result).count());
        ConstantNode constant = getConstantNodes(result).first();
        assertDeepEquals(Kind.Long, constant.getKind());
        assertDeepEquals(((HotSpotResolvedObjectType) getMetaAccess().lookupJavaType(Boolean.class)).klass(), constant.asConstant());
    }

    @Test
    public void testBoxedBoolean() {
        StructuredGraph result = compile("getBoxedBoolean", false);
        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes(PiNode.TYPE).count());
        assertDeepEquals(1, getConstantNodes(result).count());
        ConstantNode constant = getConstantNodes(result).first();
        assertDeepEquals(Kind.Object, constant.getKind());

        HotSpotObjectConstantImpl c = (HotSpotObjectConstantImpl) constant.asConstant();
        Assert.assertTrue(c.isEqualTo(Boolean.TRUE));
    }

    private StructuredGraph compile(String test, boolean compileAOT) {
        try (OverrideScope s = OptionValue.override(ImmutableCode, compileAOT)) {
            StructuredGraph graph = parseEager(test, AllowAssumptions.YES);
            ResolvedJavaMethod method = graph.method();
            CallingConvention cc = getCallingConvention(getCodeCache(), Type.JavaCallee, graph.method(), false);
            // create suites everytime, as we modify options for the compiler
            SuitesProvider suitesProvider = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites();
            final Suites suitesLocal = suitesProvider.createSuites();
            final LIRSuites lirSuitesLocal = suitesProvider.createLIRSuites();
            final CompilationResult compResult = compileGraph(graph, cc, method, getProviders(), getBackend(), getCodeCache().getTarget(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL,
                            getProfilingInfo(graph), getSpeculationLog(), suitesLocal, lirSuitesLocal, new CompilationResult(), CompilationResultBuilderFactory.Default);
            addMethod(method, compResult);
            return graph;
        }
    }
}
