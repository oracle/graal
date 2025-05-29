/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.ext.test;

import static org.junit.Assert.assertNotNull;

import java.lang.invoke.MethodHandles;

import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;

public class CustomLayoutTest {

    @Test
    public void testLayoutWithSuperclass() {
        var lookup = MethodHandles.lookup();
        assertNotNull(new TestDynamicObject1(Shape.newBuilder().layout(TestDynamicObject1.class, lookup).build()));
        assertNotNull(new TestDynamicObject2(Shape.newBuilder().layout(TestDynamicObject2.class, lookup).build()));
        assertNotNull(new TestDynamicObject3(Shape.newBuilder().layout(TestDynamicObject3.class, lookup).build()));
        assertNotNull(new TestDynamicObject4(Shape.newBuilder().layout(TestDynamicObject4.class, lookup).build()));
    }

    static class TestDynamicObject0 extends DynamicObject {
        Object pojfield = new Object();

        protected TestDynamicObject0(Shape shape) {
            super(shape);
            assert pojfield != null;
        }
    }

    static class TestDynamicObject1 extends TestDynamicObject0 {
        @DynamicField Object o0;

        protected TestDynamicObject1(Shape shape) {
            super(shape);
        }
    }

    static class TestDynamicObject2 extends TestDynamicObject1 {
        @DynamicField Object o1;
        @DynamicField Object o2;
        Object pojo;
        @DynamicField Object o3;
        @DynamicField long p0;
        @DynamicField long p1;
        @DynamicField long p2;

        protected TestDynamicObject2(Shape shape) {
            super(shape);
        }
    }

    static class TestDynamicObject3 extends TestDynamicObject1 {
        @DynamicField Object o1;
        @DynamicField Object o2;
        @DynamicField Object o3;
        @DynamicField long p0;
        long poj1;
        @DynamicField long p1;
        long poj2;
        @DynamicField long p2;

        protected TestDynamicObject3(Shape shape) {
            super(shape);
        }
    }

    static class TestDynamicObject4 extends TestDynamicObject2 {
        @DynamicField int p0;
        @DynamicField int p1;
        @DynamicField int p2;

        protected TestDynamicObject4(Shape shape) {
            super(shape);
        }
    }
}
