/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.io.Closeable;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.polyglot.Context;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.staticobject.DefaultStaticObjectFactory;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;

@RunWith(Parameterized.class)
public class StaticObjectCompilationTest extends PartialEvaluationTest {
    static final StaticObjectTestEnvironment[] environments = StaticObjectTestEnvironment.getEnvironments();

    @Parameterized.Parameters(name = "{0}")
    public static StaticObjectTestEnvironment[] data() {
        return environments;
    }

    @Parameterized.Parameter public StaticObjectTestEnvironment te;

    @BeforeClass
    public static void initialize() {
        for (StaticObjectTestEnvironment env : environments) {
            env.initialize();
        }
    }

    @AfterClass
    public static void teardown() {
        for (StaticObjectTestEnvironment env : environments) {
            env.close();
        }
    }

    static class FieldBasedStorage {
        final int finalProperty = 42;
        int property;
    }

    @Test
    public void simplePropertyAccesses() {
        // Field-based storage
        Assume.assumeTrue(te.isFieldBased());

        StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
        StaticProperty finalProperty = new DefaultStaticProperty("finalProperty");
        StaticProperty property = new DefaultStaticProperty("property");
        builder.property(finalProperty, int.class, true);
        builder.property(property, int.class, false);
        Object staticObject = builder.build().getFactory().create();

        FieldBasedStorage fbs = new FieldBasedStorage();

        // Property set
        assertPartialEvalEquals(toRootNode((f) -> fbs.property = 42), toRootNode((f) -> {
            finalProperty.setInt(staticObject, 42);
            return finalProperty.getInt(staticObject);
        }), new Object[0]);
        assertPartialEvalEquals(toRootNode((f) -> fbs.property = 42), toRootNode((f) -> {
            property.setInt(staticObject, 42);
            return property.getInt(staticObject);
        }), new Object[0]);

        finalProperty.setInt(staticObject, 42);
        // Property get
        assertPartialEvalEquals(toRootNode((f) -> 42), toRootNode((f) -> finalProperty.getInt(staticObject)), new Object[0]);
        assertPartialEvalEquals(toRootNode((f) -> fbs.finalProperty), toRootNode((f) -> finalProperty.getInt(staticObject)), new Object[0]);
        assertPartialEvalEquals(toRootNode((f) -> fbs.property), toRootNode((f) -> property.getInt(staticObject)), new Object[0]);
    }

    @Test
    public void propertyAccessesInHierarchy() {
        // Field-based storage
        Assume.assumeTrue(te.isFieldBased());
        StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
        StaticProperty s1p1 = new DefaultStaticProperty("property");
        b1.property(s1p1, int.class, true);
        StaticShape<DefaultStaticObjectFactory> s1 = b1.build();

        StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
        StaticProperty s2p1 = new DefaultStaticProperty("property");
        b2.property(s2p1, int.class, true);
        StaticShape<DefaultStaticObjectFactory> s2 = b2.build(s1);
        Object o2 = s2.getFactory().create();

        s1p1.setInt(o2, 24);
        s2p1.setInt(o2, 42);

        assertPartialEvalEquals(toRootNode((f) -> 24), toRootNode((f) -> s1p1.getInt(o2)), new Object[0]);
        assertPartialEvalEquals(toRootNode((f) -> 42), toRootNode((f) -> s2p1.getInt(o2)), new Object[0]);
    }

    @Test
    public void allocation() {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(new AllocationNode(te));
            assertNoInvokes(graph);
            if (te.isArrayBased()) {
                // The array that stores primitive fields
                assertCount(graph, VirtualArrayNode.class, 2);
            }
            assertCount(graph, VirtualInstanceNode.class, 1);
        } finally {
            te.context.leave();
        }
    }

    @Test
    public void readOnce() {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(new ReadOnceNode(te));
            assertNoInvokes(graph);

            if (te.isArrayBased()) {
                assertCount(graph, RawLoadNode.class, 1);
            } else {
                assertCount(graph, LoadFieldNode.class, 1);
            }
        } finally {
            te.context.leave();
        }
    }

    @Test
    public void readMultipleAndAdd() {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(new ReadMultipleAndSumNode(te));
            assertNoInvokes(graph);

            if (te.isArrayBased()) {
                assertCount(graph, RawLoadNode.class, 1);
            } else {
                assertCount(graph, LoadFieldNode.class, 1);
            }
            assertCount(graph, AddNode.class, 1);
            assertCount(graph, LeftShiftNode.class, 1);
        } finally {
            te.context.leave();
        }
    }

    @Test
    public void writeOnce() {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(new WriteOnceNode(te));
            assertNoInvokes(graph);

            if (te.isArrayBased()) {
                assertCount(graph, RawStoreNode.class, 1);
            } else {
                assertCount(graph, StoreFieldNode.class, 1);
            }
        } finally {
            te.context.leave();
        }
    }

    @Test
    public void writeMultiple() {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(new WriteMultipleNode(te));
            assertNoInvokes(graph);

            if (te.isArrayBased()) {
                assertCount(graph, RawStoreNode.class, 3);
            } else {
                assertCount(graph, StoreFieldNode.class, 1);
            }
        } finally {
            te.context.leave();
        }
    }

    @Test
    public void allocateSetAndGet() {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(new AllocateSetAndGetNode(te));
            assertNoInvokes(graph);
            assertCount(graph, VirtualInstanceNode.class, 0);
            assertCount(graph, RawLoadNode.class, 0);
            assertCount(graph, LoadFieldNode.class, 0);
            assertCount(graph, RawStoreNode.class, 0);
            assertCount(graph, StoreFieldNode.class, 0);
        } finally {
            te.context.leave();
        }
    }

    @Test
    public void allocateSetAndGetString() {
        te.context.enter();
        try {
            StructuredGraph graph = partialEval(new AllocateSetAndGetStringNode(te));
            assertNoInvokes(graph);
            if (te.isFieldBased()) {
                assertNoNodes(graph, InstanceOfNode.class);
            }
        } finally {
            te.context.leave();
        }
    }

    private StructuredGraph partialEval(StaticObjectAbstractNode node) {
        RootNode rootNode = new StaticObjectRootNode(te.testLanguage, new FrameDescriptor(), node);
        StructuredGraph graph = partialEval(rootNode);
        return graph;
    }

    private static void assertNoInvokes(StructuredGraph graph) {
        for (MethodCallTargetNode node : graph.getNodes(MethodCallTargetNode.TYPE)) {
            Assert.fail("Found invalid method call target node: " + node + " (" + node.targetMethod() + ")");
        }
    }

    private static void assertNoNodes(StructuredGraph graph, Class<? extends jdk.graal.compiler.graph.Node> nodeClass) {
        for (jdk.graal.compiler.graph.Node node : graph.getNodes()) {
            if (nodeClass.isAssignableFrom(node.getClass())) {
                Assert.fail("Found invalid node of type: " + nodeClass.getName());
            }
        }
    }

    private static void assertCount(StructuredGraph graph, Class<? extends jdk.graal.compiler.graph.Node> nodeClass, int expected) {
        Assert.assertEquals(expected, graph.getNodes().filter(nodeClass).count());
    }

    private abstract static class StaticObjectAbstractNode extends Node {
        final StaticShape<DefaultStaticObjectFactory> shape;
        final StaticProperty intProperty;
        final StaticProperty stringProperty;

        StaticObjectAbstractNode(StaticObjectTestEnvironment te) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            intProperty = new DefaultStaticProperty("intProperty");
            stringProperty = new DefaultStaticProperty("stringProperty");
            builder.property(intProperty, int.class, false);
            builder.property(stringProperty, String.class, false);
            shape = builder.build();
        }

        abstract Object execute(VirtualFrame frame);
    }

    private static class AllocationNode extends StaticObjectAbstractNode {
        AllocationNode(StaticObjectTestEnvironment te) {
            super(te);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return shape.getFactory().create();
        }
    }

    private static class ReadOnceNode extends StaticObjectAbstractNode {
        final Object staticObject;

        ReadOnceNode(StaticObjectTestEnvironment te) {
            super(te);
            staticObject = shape.getFactory().create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return intProperty.getInt(staticObject);
        }
    }

    private static class ReadMultipleAndSumNode extends StaticObjectAbstractNode {
        final Object staticObject;

        ReadMultipleAndSumNode(StaticObjectTestEnvironment te) {
            super(te);
            staticObject = shape.getFactory().create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int a = intProperty.getInt(staticObject);
            int b = intProperty.getInt(staticObject);
            int c = intProperty.getInt(staticObject);
            return a + b + c;
        }
    }

    private static class WriteOnceNode extends StaticObjectAbstractNode {
        final Object staticObject;

        WriteOnceNode(StaticObjectTestEnvironment te) {
            super(te);
            staticObject = shape.getFactory().create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            intProperty.setInt(staticObject, 42);
            return null;
        }
    }

    private static class WriteMultipleNode extends StaticObjectAbstractNode {
        final Object staticObject;

        WriteMultipleNode(StaticObjectTestEnvironment te) {
            super(te);
            staticObject = shape.getFactory().create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            intProperty.setInt(staticObject, 42);
            intProperty.setInt(staticObject, 42);
            intProperty.setInt(staticObject, 42);
            return null;
        }
    }

    private static class AllocateSetAndGetNode extends StaticObjectAbstractNode {
        AllocateSetAndGetNode(StaticObjectTestEnvironment te) {
            super(te);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object staticObject = shape.getFactory().create();
            intProperty.setInt(staticObject, 42);
            return intProperty.getInt(staticObject) == 42;
        }
    }

    private static class AllocateSetAndGetStringNode extends StaticObjectAbstractNode {
        final Object staticObject;

        AllocateSetAndGetStringNode(StaticObjectTestEnvironment te) {
            super(te);
            staticObject = shape.getFactory().create();
            stringProperty.setObject(staticObject, "value");
        }

        @Override
        public Object execute(VirtualFrame frame) {
            String str = (String) stringProperty.getObject(staticObject);
            return str.length();
        }
    }

    @NodeInfo
    private static class StaticObjectRootNode extends RootNode {
        @Child private StaticObjectAbstractNode node;

        StaticObjectRootNode(TruffleLanguage<?> language, FrameDescriptor descriptor, StaticObjectAbstractNode node) {
            super(language, descriptor);
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(frame);
        }
    }

    static class StaticObjectTestEnvironment implements Closeable {
        private final boolean arrayBased;
        TruffleLanguage<?> testLanguage;
        Context context;

        StaticObjectTestEnvironment(boolean arrayBased) {
            this.arrayBased = arrayBased;

        }

        public void initialize() {
            context = Context.newBuilder(TestLanguage.TEST_LANGUAGE_ID).//
                            allowExperimentalOptions(true).//
                            option("engine.StaticObjectStorageStrategy", this.arrayBased ? "array-based" : "field-based").//
                            build();
            context.initialize(TestLanguage.TEST_LANGUAGE_ID);
            context.enter();
            testLanguage = TestLanguage.REFERENCE.get(null);
            context.leave();
        }

        boolean isArrayBased() {
            return arrayBased;
        }

        boolean isFieldBased() {
            return !arrayBased;
        }

        @Override
        public void close() {
            context.close();
            context = null;
        }

        @Override
        public String toString() {
            return (arrayBased ? "Array-based" : "Field-based") + " storage";
        }

        static StaticObjectTestEnvironment[] getEnvironments() {
            return new StaticObjectTestEnvironment[]{new StaticObjectTestEnvironment(true), new StaticObjectTestEnvironment(false)};
        }
    }

    @TruffleLanguage.Registration(id = TestLanguage.TEST_LANGUAGE_ID, name = TestLanguage.TEST_LANGUAGE_ID)
    public static class TestLanguage extends TruffleLanguage<TestContext> {
        static final String TEST_LANGUAGE_ID = "StaticObjectCompilationTest_TestLanguage";

        @Override
        protected TestContext createContext(Env env) {
            return new TestContext(this);
        }

        static final LanguageReference<TestLanguage> REFERENCE = LanguageReference.create(TestLanguage.class);

    }

    static class TestContext {
        private final TestLanguage language;

        TestContext(TestLanguage language) {
            this.language = language;
        }

        TestLanguage getLanguage() {
            return language;
        }
    }
}
