/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.GuardedUnsafeLoadNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.UnsafeAccessNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.truffle.nodes.ObjectLocationIdentity;
import jdk.graal.compiler.truffle.test.nodes.AbstractTestNode;
import jdk.graal.compiler.truffle.test.nodes.RootTestNode;
import jdk.vm.ci.meta.JavaKind;

public class DynamicObjectPartialEvaluationTest extends PartialEvaluationTest {

    Shape rootShapeWithoutFields;
    Shape rootShapeWithFields;

    @Before
    public void before() {
        var lookup = MethodHandles.lookup();
        rootShapeWithoutFields = Shape.newBuilder().layout(TestDynamicObject.class, lookup).build();
        rootShapeWithFields = Shape.newBuilder().layout(TestDynamicObjectWithFields.class, lookup).build();
        newInstanceWithFields();
        newInstanceWithoutFields();
    }

    private TestDynamicObjectWithFields newInstanceWithFields() {
        return new TestDynamicObjectWithFields(rootShapeWithFields);
    }

    private TestDynamicObject newInstanceWithoutFields() {
        return new TestDynamicObject(rootShapeWithoutFields);
    }

    @Test
    public void testFieldLocation() {
        TestDynamicObject obj = newInstanceWithFields();
        DynamicObject.PutNode.getUncached().execute(obj, "key", 22);

        Object[] args = {obj, 22};
        OptimizedCallTarget callTarget = makeCallTarget(new TestDynamicObjectGetAndPutNode(), "testFieldStoreLoad");
        callTarget.call(); // defy argument profiling

        StructuredGraph graph = partialEval(callTarget, args);

        if (graph.getNodes().filter(n -> n instanceof LoadFieldNode || n instanceof GuardedUnsafeLoadNode).isEmpty()) {
            Assert.fail("LoadFieldNode not found");
        }
        if (!graph.getNodes().filter(n -> n instanceof RawLoadNode && !(n instanceof GuardedUnsafeLoadNode)).isEmpty()) {
            Assert.fail("Found unexpected RawLoadNode: " + graph.getNodes().filter(RawLoadNode.class).snapshot());
        }
        if (graph.getNodes().filter(n -> n instanceof StoreFieldNode ||
                        (n instanceof RawStoreNode && (((RawStoreNode) n).getLocationIdentity() instanceof ObjectLocationIdentity))).isEmpty()) {
            Assert.fail("StoreFieldNode not found");
        }

        compile(callTarget, graph);

        Assert.assertTrue("CallTarget is valid", callTarget.isValid());
        Assert.assertEquals(42, callTarget.call(args));
    }

    @Test
    public void testArrayLocation() {
        TestDynamicObject obj = newInstanceWithoutFields();
        DynamicObject.PutNode.getUncached().execute(obj, "key", 22);

        Object[] args = {obj, 22};
        OptimizedCallTarget callTarget = makeCallTarget(new TestDynamicObjectGetAndPutNode(), "testArrayStoreLoad");
        callTarget.call(); // defy argument profiling

        StructuredGraph graph = partialEval(callTarget, args);

        for (Node n : graph.getNodes().filter(n -> n instanceof RawLoadNode || n instanceof RawStoreNode)) {
            UnsafeAccessNode rawAccess = (UnsafeAccessNode) n;
            if (rawAccess.getLocationIdentity() instanceof ObjectLocationIdentity) {
                Assert.assertTrue(rawAccess instanceof GuardedUnsafeLoadNode || rawAccess instanceof RawStoreNode);
            } else {
                Assert.assertTrue(rawAccess.getLocationIdentity().toString(), NamedLocationIdentity.isArrayLocation(rawAccess.getLocationIdentity()));
                Assert.assertEquals(NamedLocationIdentity.getArrayLocation(JavaKind.Int), rawAccess.getLocationIdentity());
            }
        }

        compile(callTarget, graph);

        Assert.assertTrue("CallTarget is valid", callTarget.isValid());
        Assert.assertEquals(42, callTarget.call(args));
    }

    @Test
    public void testBulkReplacementInvalidatesCompiledAccess() {
        for (boolean fields : new boolean[]{false, true}) {
            TestDynamicObject object = fields ? newInstanceWithFields() : newInstanceWithoutFields();
            DynamicObject.PutNode.getUncached().execute(object, "first", 1);
            DynamicObject.PutNode.getUncached().execute(object, "second", 2);
            DynamicObject.PutNode.getUncached().execute(object, "tail", 42);
            Object[] values = {3, 4};
            Object[] args = {object, values};
            OptimizedCallTarget writer = makeCallTarget(new TestBulkPutNode(), "bulkPut");
            writer.call();
            writer.call(args);
            compile(writer, partialEval(writer, args));
            Assert.assertTrue(writer.isValid());
            Assert.assertEquals(42, writer.call(args));

            OptimizedCallTarget reader = (OptimizedCallTarget) new TestDynamicObjectGetFinalRootNode(object).getCallTarget();
            Object[] readArgs = {"first"};
            Assert.assertEquals(3, reader.call(readArgs));
            compile(reader, partialEval(reader, readArgs));
            Assert.assertTrue(reader.isValid());
            Shape oldShape = object.getShape();

            Assert.assertEquals(42, writer.call(object, new Object[]{"first", "second"}));
            Assert.assertFalse(oldShape.isValid());
            Assert.assertEquals("first", reader.call(readArgs));
            Assert.assertFalse(reader.isValid());
            Assert.assertEquals("first", DynamicObject.GetNode.getUncached().execute(object, "first", null));
            Assert.assertEquals("second", DynamicObject.GetNode.getUncached().execute(object, "second", null));

            Object[] newArgs = {object, new Object[]{"again", "again"}};
            compile(writer, partialEval(writer, newArgs));
            Assert.assertTrue(writer.isValid());
            Assert.assertEquals(42, writer.call(newArgs));
        }
    }

    @Test
    public void testPropertyAssumptionsInvalidateCompiledCode() {
        Shape root = Shape.newBuilder().layout(TestDynamicObject.class, MethodHandles.lookup()).propertyAssumptions(true).build();
        TestDynamicObject object = new TestDynamicObject(root);
        DynamicObject.PutNode.getUncached().execute(object, "key", 42);
        Assumption property = object.getShape().getPropertyAssumption("key");
        OptimizedCallTarget reader = compilePropertyAssumption(property);

        DynamicObject.SetPropertyFlagsNode.getUncached().execute(object, "key", 1);
        Assert.assertFalse(property.isValid());
        Assert.assertFalse(reader.isValid());
        Assert.assertEquals(43, reader.call());
        Assert.assertSame(Assumption.NEVER_VALID, root.getPropertyAssumption("key"));

        Assumption absent = root.getPropertyAssumption("absent");
        OptimizedCallTarget absentReader = compilePropertyAssumption(absent);
        DynamicObject.ResetShapeNode.getUncached().execute(object, root);
        Assert.assertFalse(absent.isValid());
        Assert.assertFalse(absentReader.isValid());
        Assert.assertEquals(43, absentReader.call());

        Assumption fresh = root.getPropertyAssumption("key");
        Assert.assertNotSame(property, fresh);
        OptimizedCallTarget freshReader = compilePropertyAssumption(fresh);
        DynamicObject.PutNode.getUncached().execute(object, "key", 43);
        Assert.assertFalse(fresh.isValid());
        Assert.assertFalse(freshReader.isValid());
        Assert.assertEquals(43, freshReader.call());
    }

    private OptimizedCallTarget compilePropertyAssumption(Assumption assumption) {
        Assert.assertTrue(assumption.isValid());
        OptimizedCallTarget target = makeCallTarget(new TestPropertyAssumptionNode(assumption), "propertyAssumption");
        Assert.assertEquals(42, target.call());
        compile(target, partialEval(target, new Object[0]));
        Assert.assertTrue(target.isValid());
        Assert.assertEquals(42, target.call());
        return target;
    }

    static class TestPropertyAssumptionNode extends AbstractTestNode {
        private final Assumption assumption;

        TestPropertyAssumptionNode(Assumption assumption) {
            this.assumption = assumption;
        }

        @Override
        public int execute(VirtualFrame frame) {
            return assumption.isValid() ? 42 : 43;
        }
    }

    static class TestBulkPutNode extends AbstractTestNode {
        @Child DynamicObject.PutAllNode putAll = DynamicObject.PutAllNode.create();
        @Child DynamicObject.GetNode get = DynamicObject.GetNode.create();
        @CompilerDirectives.CompilationFinal(dimensions = 1) private final Object[] keys = {"first", "second"};

        @Override
        public int execute(VirtualFrame frame) {
            if (frame.getArguments().length == 0) {
                return -1;
            }
            DynamicObject object = (DynamicObject) frame.getArguments()[0];
            putAll.execute(object, keys, (Object[]) frame.getArguments()[1]);
            try {
                return get.executeInt(object, "tail", null);
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    private static OptimizedCallTarget makeCallTarget(AbstractTestNode testNode, String testName) {
        RootNode rootNode = new RootTestNode(new FrameDescriptor(), testName, testNode);
        return (OptimizedCallTarget) rootNode.getCallTarget();
    }

    static class TestDynamicObjectGetAndPutNode extends AbstractTestNode {
        @Child DynamicObject.GetNode getNode = DynamicObject.GetNode.create();
        @Child DynamicObject.PutNode putNode = DynamicObject.PutNode.create();

        @Override
        public int execute(VirtualFrame frame) {
            if (frame.getArguments().length == 0) {
                return -1;
            }
            Object arg0 = frame.getArguments()[0];
            DynamicObject obj = (DynamicObject) arg0;
            if (frame.getArguments().length > 1) {
                Object arg1 = frame.getArguments()[1];
                putNode.execute(obj, "key", (int) arg1);
            }
            int val;
            while (true) {
                val = getInt(obj, "key");
                if (val >= 42) {
                    break;
                }
                putNode.execute(obj, "key", val + 2);
            }
            return val;
        }

        private int getInt(DynamicObject obj, Object key) {
            try {
                return getNode.executeInt(obj, key, null);
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Test
    public void testConstantFoldingFromField() {
        testConstantFolding(newInstanceWithFields());
    }

    @Test
    public void testConstantFoldingFromArray() {
        testConstantFolding(newInstanceWithoutFields());
    }

    private void testConstantFolding(DynamicObject obj) {
        Object refKey = "refKey";
        Object refValue = "refValue";
        Object intKey = "intKey";
        int intValue = 42;
        Object longKey = "longKey";
        long longValue = 277777788888899L;
        Object doubleKey = "doubleKey";
        double doubleValue = Math.PI;

        DynamicObject.PutNode.getUncached().execute(obj, refKey, refValue);
        DynamicObject.PutNode.getUncached().execute(obj, intKey, intValue);
        DynamicObject.PutNode.getUncached().execute(obj, longKey, longValue);
        DynamicObject.PutNode.getUncached().execute(obj, doubleKey, doubleValue);

        OptimizedCallTarget callTarget = (OptimizedCallTarget) new TestDynamicObjectGetFinalRootNode(obj).getCallTarget();
        callTarget.call(refKey);
        callTarget.call(intKey);
        callTarget.call(longKey);
        callTarget.call(doubleKey);

        StructuredGraph graph = partialEval(callTarget, new Object[]{intKey});

        if (!graph.getNodes().filter(RawLoadNode.class).isEmpty()) {
            Assert.fail("Found unexpected RawLoadNode: " + graph.getNodes().filter(RawLoadNode.class).snapshot());
        }
        if (graph.getNodes().filter(ConstantNode.class).filter(n -> n instanceof ConstantNode c &&
                        c.getStackKind() == JavaKind.Object &&
                        getConstantReflection().constantEquals(c.asJavaConstant(), getSnippetReflection().forObject(refValue))).isEmpty()) {
            Assert.fail("Constant " + refValue + " not found in the graph.");
        }
        if (graph.getNodes().filter(ConstantNode.class).filter(n -> n instanceof ConstantNode c &&
                        c.getStackKind() == JavaKind.Int &&
                        c.asJavaConstant().asInt() == intValue).isEmpty()) {
            Assert.fail("Constant " + intValue + " not found in the graph.");
        }
        if (graph.getNodes().filter(ConstantNode.class).filter(n -> n instanceof ConstantNode c &&
                        c.getStackKind() == JavaKind.Long &&
                        c.asJavaConstant().asLong() == longValue).isEmpty()) {
            Assert.fail("Constant " + longValue + " not found in the graph.");
        }
        if (graph.getNodes().filter(ConstantNode.class).filter(n -> n instanceof ConstantNode c &&
                        c.getStackKind() == JavaKind.Double &&
                        Double.compare(c.asJavaConstant().asDouble(), doubleValue) == 0).isEmpty()) {
            Assert.fail("Constant " + doubleValue + " not found in the graph.");
        }

        compile(callTarget, graph);

        Assert.assertTrue("CallTarget is valid", callTarget.isValid());
        Assert.assertSame(refValue, callTarget.call(refKey));
        Assert.assertEquals(intValue, callTarget.call(intKey));
        Assert.assertEquals(longValue, callTarget.call(longKey));
        Assert.assertEquals(doubleValue, callTarget.call(doubleKey));
    }

    static class TestDynamicObjectGetFinalRootNode extends RootNode {
        final DynamicObject receiver;
        @Child DynamicObject.GetNode getNode = DynamicObject.GetNode.create();

        TestDynamicObjectGetFinalRootNode(DynamicObject receiver) {
            super(null);
            this.receiver = receiver;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object key = frame.getArguments()[0];
            return getNode.execute(receiver, key, null);
        }
    }

    static class TestDynamicObject extends DynamicObject {
        TestDynamicObject(Shape shape) {
            super(shape);
        }
    }

    static class TestDynamicObjectWithFields extends TestDynamicObject {
        @DynamicField private long primitive1;
        @DynamicField private long primitive2;
        @DynamicField private long primitive3;
        @DynamicField private Object object1;
        @DynamicField private Object object2;
        @DynamicField private Object object3;

        TestDynamicObjectWithFields(Shape shape) {
            super(shape);
        }
    }
}
