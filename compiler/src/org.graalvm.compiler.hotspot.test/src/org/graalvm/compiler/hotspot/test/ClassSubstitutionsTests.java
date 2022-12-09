/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.hotspot.test;

import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Field;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ClassSubstitutionsTests extends GraalCompilerTest {

    public Number instanceField;

    public Object[] arrayField;

    public String[] stringArrayField;

    @SuppressWarnings("try")
    protected StructuredGraph test(final String snippet) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope("ClassSubstitutionsTest", getMetaAccess().lookupJavaMethod(getMethod(snippet)))) {
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES, debug);
            compile(graph.method(), graph);
            assertNotInGraph(graph, Invoke.class);
            debug.dump(DebugContext.BASIC_LEVEL, graph, snippet);
            return graph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected static StructuredGraph assertNotInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node : graph.getNodes()) {
            if (clazz.isInstance(node)) {
                fail(node.toString());
            }
        }
        return graph;
    }

    public boolean constantIsArray() {
        return "".getClass().isArray();
    }

    public boolean constantIsInterface() {
        return "".getClass().isInterface();
    }

    public boolean constantIsPrimitive() {
        return "".getClass().isPrimitive();
    }

    @Test
    public void testIsArray() {
        testConstantReturn("constantIsArray", 0);
    }

    @Test
    public void testIsInterface() {
        testConstantReturn("constantIsInterface", 0);
    }

    @Test
    public void testIsPrimitive() {
        testConstantReturn("constantIsPrimitive", 0);
    }

    public boolean fieldIsNotArray() {
        if (instanceField != null) {
            // The base type of instanceField is not Object or an Interface, so it's provably an
            // instance type, so isArray will always return false.
            return instanceField.getClass().isArray();
        }
        return false;
    }

    @Test
    public void testFieldIsNotArray() {
        testConstantReturn("fieldIsNotArray", 0);
    }

    public boolean foldComponentType() {
        return stringArrayField.getClass().getComponentType() == String.class;
    }

    @Test
    public void testFoldComponentType() {
        testConstantReturn("foldComponentType", 1);
    }

    public static Object unsafeFoldComponentType(Class<?> clazz) {
        return readComponentType(clazz);
    }

    static Runnable runTest;

    static class DummyClass {
        static {
            runTest.run();
        }

        static void init() {
        }
    }

    @Test
    public void testUnsafeFoldComponentType() {
        assumeTrue(GraalHotSpotVMConfig.jvmciGE(GraalHotSpotVMConfig.JVMCI_20_3_b04));
        runTest = new Runnable() {
            @Override
            public void run() {
                // Run this test while inside the static initializer of DummyClass to ensure
                // componentType has an init_lock.
                StructuredGraph graph = parseEager("unsafeFoldComponentType", AllowAssumptions.NO);
                HighTierContext highContext = getDefaultHighTierContext();
                ParameterNode param = graph.getNodes().filter(ParameterNode.class).first();
                param.replaceAtUsages(ConstantNode.forConstant(getSnippetReflection().forObject(DummyClass.class), getMetaAccess(), graph));
                createCanonicalizerPhase().apply(graph, highContext);
                ReturnNode returnNode = graph.getNodes().filter(ReturnNode.class).first();
                Object result = getSnippetReflection().asObject(Object.class, returnNode.result().asJavaConstant());
                assertDeepEquals("componentType must be null for non-array", null, result);
            }
        };
        DummyClass.init();
    }

    // This should be replaced by a plugin
    @SuppressWarnings("unused")
    static Class<?> readComponentType(Class<?> clazz) {
        throw new InternalError("unimplemented");
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins plugins) {
        try {
            Field f = Class.class.getDeclaredField("componentType");
            Registration r = new Registration(plugins, ClassSubstitutionsTests.class);
            r.register(new InvocationPlugin("readComponentType", Class.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode clazz) {
                    ResolvedJavaField field = b.getMetaAccess().lookupJavaField(f);
                    b.addPush(JavaKind.Object,
                                    LoadFieldNode.create(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(), b.getOptions(),
                                                    b.getAssumptions(), clazz, field, false, false, b.getGraph().currentNodeSourcePosition()));
                    return true;
                }
            });
        } catch (NoSuchFieldException e) {
            throw new InternalError(e);
        }
    }

    @Test
    public void testFieldIsArray() {
        testConstantReturn("fieldIsArray", 1);
    }

    public boolean fieldIsArray() {
        if (arrayField != null) {
            // The base type of arrayField is an array of some sort so isArray will always return
            // true.
            return arrayField.getClass().isArray();
        }
        return true;
    }

    private static class A {
    }

    private static class B extends A {
    }

    private static class C {
    }

    private static final A aInstance = new A();
    private static final B bInstance = new B();
    private static final C cInstance = new C();

    public boolean classIsAssignable1() {
        return aInstance.getClass().isAssignableFrom(aInstance.getClass());
    }

    public boolean classIsAssignable2() {
        return aInstance.getClass().isAssignableFrom(bInstance.getClass());
    }

    public boolean classIsAssignable3() {
        return aInstance.getClass().isAssignableFrom(cInstance.getClass());
    }

    public boolean classIsAssignable4() {
        return bInstance.getClass().isAssignableFrom(aInstance.getClass());
    }

    public boolean classIsAssignable5() {
        return cInstance.getClass().isAssignableFrom(bInstance.getClass());
    }

    public boolean classIsAssignable6() {
        return int.class.isAssignableFrom(bInstance.getClass());
    }

    public boolean classIsAssignable7() {
        return int.class.isAssignableFrom(int.class);
    }

    @Test
    public void testClassIsAssignable() {
        testConstantReturn("classIsAssignable1", 1);
        testConstantReturn("classIsAssignable2", 1);
        testConstantReturn("classIsAssignable3", 0);
        testConstantReturn("classIsAssignable4", 0);
        testConstantReturn("classIsAssignable5", 0);
        testConstantReturn("classIsAssignable6", 0);
        testConstantReturn("classIsAssignable7", 1);
    }

    private void testConstantReturn(String name, Object value) {
        StructuredGraph result = test(name);
        ReturnNode ret = result.getNodes(ReturnNode.TYPE).first();
        assertDeepEquals(1, result.getNodes(ReturnNode.TYPE).count());

        assertDeepEquals(true, ret.result().isConstant());
        assertDeepEquals(value, ret.result().asJavaConstant().asBoxedPrimitive());
    }
}
