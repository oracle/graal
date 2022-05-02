/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.GuardedUnsafeLoadNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.truffle.compiler.nodes.ObjectLocationIdentity;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;

import jdk.vm.ci.meta.JavaKind;

public class DynamicObjectPartialEvaluationTest extends PartialEvaluationTest {

    Shape rootShapeWithoutFields;
    Shape rootShapeWithFields;

    @Before
    public void before() {
        rootShapeWithoutFields = Shape.newBuilder().layout(TestDynamicObject.class).build();
        rootShapeWithFields = Shape.newBuilder().layout(TestDynamicObjectWithFields.class).build();
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
        DynamicObjectLibrary.getUncached().put(obj, "key", 22);

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
        DynamicObjectLibrary.getUncached().put(obj, "key", 22);

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

    private static OptimizedCallTarget makeCallTarget(AbstractTestNode testNode, String testName) {
        RootNode rootNode = new RootTestNode(new FrameDescriptor(), testName, testNode);
        return (OptimizedCallTarget) rootNode.getCallTarget();
    }

    static class TestDynamicObjectGetAndPutNode extends AbstractTestNode {
        @Child DynamicObjectLibrary dynamicObjectLibrary = DynamicObjectLibrary.getFactory().createDispatched(3);

        @Override
        public int execute(VirtualFrame frame) {
            if (frame.getArguments().length == 0) {
                return -1;
            }
            Object arg0 = frame.getArguments()[0];
            DynamicObject obj = (DynamicObject) arg0;
            if (frame.getArguments().length > 1) {
                Object arg1 = frame.getArguments()[1];
                dynamicObjectLibrary.put(obj, "key", (int) arg1);
            }
            int val;
            while (true) {
                val = getInt(obj, "key");
                if (val >= 42) {
                    break;
                }
                dynamicObjectLibrary.put(obj, "key", val + 2);
            }
            return val;
        }

        private int getInt(DynamicObject obj, Object key) {
            try {
                return dynamicObjectLibrary.getIntOrDefault(obj, key, null);
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
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
