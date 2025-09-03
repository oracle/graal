/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.object.basic.test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.invoke.MethodHandles;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;

@SuppressWarnings("deprecation")
public class LeakCheckTest {
    private static final DynamicObjectLibrary LIBRARY = DynamicObjectLibrary.getUncached();

    private static Shape newEmptyShape() {
        return Shape.newBuilder().layout(TestDynamicObjectDefault.class, MethodHandles.lookup()).build();
    }

    private static DynamicObject newInstance(Shape emptyShape) {
        return new TestDynamicObjectDefault(emptyShape);
    }

    /**
     * Make sure the transition cache does not leak.
     */
    @Test
    public void leakCheck() {
        Shape emptyShape = newEmptyShape();
        List<WeakReference<Shape>> fullShapeRefs = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            DynamicObject obj = newInstance(emptyShape);
            for (int j = 0; j < 1000; j++) {
                LIBRARY.put(obj, "a" + Math.random(), Math.random());
                LIBRARY.put(obj, "b" + Math.random(), Math.random());
                LIBRARY.put(obj, "c" + Math.random(), Math.random());
            }
            fullShapeRefs.add(new WeakReference<>(obj.getShape()));
        }

        System.gc();
        for (WeakReference<Shape> fullShapeRef : fullShapeRefs) {
            assertNull("Shape should have been garbage-collected", fullShapeRef.get());
        }
        Reference.reachabilityFence(emptyShape);
    }

    /**
     * Make sure constant property transitions do not leak.
     */
    @Test
    public void constantPropertyLeakCheck() {
        Shape emptyShape = newEmptyShape();
        List<WeakReference<Shape>> weakShapeRefs = new ArrayList<>();
        List<Shape> strongShapeRefs = new ArrayList<>();

        for (int i = 0; i < 100000; i++) {
            DynamicObject obj = newInstance(emptyShape);
            Leak value = new Leak();
            LIBRARY.putConstant(obj, "a" + i, value, 0);

            Shape shape = obj.getShape();
            value.shape = shape;
            weakShapeRefs.add(new WeakReference<>(shape));
            strongShapeRefs.add(shape);
        }

        strongShapeRefs.clear();
        System.gc();

        for (WeakReference<Shape> fullShapeRef : weakShapeRefs) {
            assertNull("Shape should have been garbage-collected", fullShapeRef.get());
        }

        // trigger transition map cleanup
        DynamicObject obj = newInstance(emptyShape);
        LIBRARY.putConstant(obj, "const", new Leak(), 0);

        Reference.reachabilityFence(emptyShape);
    }

    /**
     * Make sure dynamic type transitions do not leak.
     */
    @Test
    public void dynamicTypeLeakCheck() {
        Shape emptyShape = newEmptyShape();
        List<WeakReference<Shape>> weakShapeRefs = new ArrayList<>();
        List<Shape> strongShapeRefs = new ArrayList<>();

        for (int i = 0; i < 100000; i++) {
            DynamicObject obj = newInstance(emptyShape);
            Leak value = new Leak();
            LIBRARY.setDynamicType(obj, value);

            Shape shape = obj.getShape();
            value.shape = shape;
            weakShapeRefs.add(new WeakReference<>(shape));
            strongShapeRefs.add(shape);
        }

        strongShapeRefs.clear();
        System.gc();

        for (WeakReference<Shape> fullShapeRef : weakShapeRefs) {
            assertNull("Shape should have been garbage-collected", fullShapeRef.get());
        }

        // trigger transition map cleanup
        DynamicObject obj = newInstance(emptyShape);
        LIBRARY.setDynamicType(obj, new Leak());

        Reference.reachabilityFence(emptyShape);
    }

    /**
     * Make sure constant property transitions do not leak.
     */
    @Test
    public void constantPropertyLeakCheckSingleTransition() {
        List<Shape> shapesToKeepAlive = new ArrayList<>();
        List<WeakReference<Shape>> weakShapeRefs = new ArrayList<>();
        List<Shape> strongShapeRefs = new ArrayList<>();

        for (int i = 0; i < 100000; i++) {
            Shape emptyShape = newEmptyShape();
            shapesToKeepAlive.add(emptyShape);
            DynamicObject obj = newInstance(emptyShape);

            Leak leak;
            leak = new Leak();
            LIBRARY.putConstant(obj, "a", leak, 0);
            leak.shape = obj.getShape();
            LIBRARY.putConstant(obj, "b", leak, 0);
            leak.shape = obj.getShape();
            LIBRARY.putConstant(obj, "c", leak, 0);
            leak.shape = obj.getShape();

            Shape shape = obj.getShape();
            weakShapeRefs.add(new WeakReference<>(shape));
            strongShapeRefs.add(shape);
        }

        strongShapeRefs.clear();
        System.gc();

        for (WeakReference<Shape> fullShapeRef : weakShapeRefs) {
            assertNull("Shape should have been garbage-collected", fullShapeRef.get());
        }

        Reference.reachabilityFence(shapesToKeepAlive);
    }

    /**
     * Make sure dynamic type transitions do not leak.
     */
    @Test
    public void dynamicTypeLeakCheckSingleTransition() {
        List<Shape> shapesToKeepAlive = new ArrayList<>();
        List<WeakReference<Shape>> weakShapeRefs = new ArrayList<>();
        List<Shape> strongShapeRefs = new ArrayList<>();

        for (int i = 0; i < 100000; i++) {
            Shape emptyShape = newEmptyShape();
            shapesToKeepAlive.add(emptyShape);
            DynamicObject obj = newInstance(emptyShape);

            Leak leak1 = new Leak();
            LIBRARY.setDynamicType(obj, leak1);
            leak1.shape = obj.getShape();

            Leak leak2 = new Leak();
            LIBRARY.setDynamicType(obj, leak2);
            leak2.shape = obj.getShape();

            Leak leak3 = new Leak();
            LIBRARY.setDynamicType(obj, leak3);
            leak3.shape = obj.getShape();

            Shape shape = obj.getShape();
            weakShapeRefs.add(new WeakReference<>(shape));
            strongShapeRefs.add(shape);
        }

        strongShapeRefs.clear();
        System.gc();

        for (WeakReference<Shape> fullShapeRef : weakShapeRefs) {
            assertNull("Shape should have been garbage-collected", fullShapeRef.get());
        }

        Reference.reachabilityFence(shapesToKeepAlive);
    }

    private static final class Leak {
        @SuppressWarnings("unused") Shape shape;
        @SuppressWarnings("unused") byte[] data = new byte[100];
    }

    /**
     * Weak keys (and entries) in the transition cache should be kept alive while the transition's
     * successor shape is alive.
     */
    @Test
    public void testWeakKeyStaysAlive() {
        Shape emptyShape = newEmptyShape();

        DynamicObject obj = newInstance(emptyShape);
        Leak const1 = new Leak();
        Leak const2 = new Leak();
        Leak const3 = new Leak();
        LIBRARY.putConstant(obj, "const1", const1, 0);
        LIBRARY.putConstant(obj, "const2", const2, 0);
        LIBRARY.putConstant(obj, "const3", const3, 0);

        Shape prevShape = obj.getShape();

        System.gc();

        obj = newInstance(emptyShape);
        LIBRARY.putConstant(obj, "const1", const1, 0);
        LIBRARY.putConstant(obj, "const2", const2, 0);
        LIBRARY.putConstant(obj, "const3", const3, 0);

        Shape currShape = obj.getShape();
        assertSame(prevShape, currShape);

        // switch from single transition to transition map
        obj = newInstance(emptyShape);
        Leak const4 = new Leak();
        LIBRARY.putConstant(obj, "const4", const4, 0);

        System.gc();

        obj = newInstance(emptyShape);
        LIBRARY.putConstant(obj, "const1", const1, 0);
        LIBRARY.putConstant(obj, "const2", const2, 0);
        LIBRARY.putConstant(obj, "const3", const3, 0);

        currShape = obj.getShape();
        assertSame(prevShape, currShape);
    }

}
