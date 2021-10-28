/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.staticobject.test;

import com.oracle.truffle.api.staticobject.DefaultStaticObjectFactory;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * These tests are derived from the Static Object Model tutorial
 * (`truffle/docs/StaticObjectModel.md`). Do not modify them without updating the tutorial.
 */
@RunWith(Parameterized.class)
public class TutorialTest extends StaticObjectModelTest {
    @Parameterized.Parameters(name = "{0}")
    public static TestConfiguration[] data() {
        return getTestConfigurations();
    }

    @Parameterized.Parameter public TestConfiguration config;

    public abstract static class MyStaticObject {
        final String arg1;
        final Object arg2;

        public MyStaticObject(String arg1) {
            this(arg1, null);
        }

        public MyStaticObject(String arg1, Object arg2) {
            this.arg1 = arg1;
            this.arg2 = arg2;
        }
    }

    public interface MyStaticObjectFactory {
        MyStaticObject create(String arg1);

        MyStaticObject create(String arg1, Object arg2);
    }

    @Test
    public void gettingStarted() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty p1 = new DefaultStaticProperty("property1");
            StaticProperty p2 = new DefaultStaticProperty("property2");
            builder.property(p1, int.class, false);
            builder.property(p2, Object.class, true);
            StaticShape<DefaultStaticObjectFactory> shape = builder.build();
            Object staticObject = shape.getFactory().create();
            p1.setInt(staticObject, 42);
            p2.setObject(staticObject, "42");
            assert p1.getInt(staticObject) == 42;
            assert p2.getObject(staticObject).equals("42");
        }
    }

    @Test
    public void shapeHierarchies() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            // Create a shape
            StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty s1p1 = new DefaultStaticProperty("property1");
            StaticProperty s1p2 = new DefaultStaticProperty("property2");
            b1.property(s1p1, int.class, false);
            b1.property(s1p2, Object.class, true);
            StaticShape<DefaultStaticObjectFactory> s1 = b1.build();
            // Create a sub-shape
            StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty s2p1 = new DefaultStaticProperty("property1");
            b2.property(s2p1, int.class, false);
            StaticShape<DefaultStaticObjectFactory> s2 = b2.build(s1);
            // Create a static object for the sub-shape
            Object o2 = s2.getFactory().create();
            // Perform property accesses
            s1p1.setInt(o2, 42);
            s1p2.setObject(o2, "42");
            s2p1.setInt(o2, 24);
            assert s1p1.getInt(o2) == 42;
            assert s1p2.getObject(o2).equals("42");
            assert s2p1.getInt(o2) == 24;
        }
    }

    @Test
    public void extendingCustomBaseClasses() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticProperty property = new DefaultStaticProperty("arg1");
            StaticShape<MyStaticObjectFactory> shape = StaticShape.newBuilder(te.testLanguage).property(property, Object.class, false).build(MyStaticObject.class, MyStaticObjectFactory.class);
            MyStaticObject staticObject = shape.getFactory().create("arg1");
            property.setObject(staticObject, "42");
            // fields of the custom super class are directly accessible
            assert staticObject.arg1.equals("arg1");
            // static properties are accessible as usual
            assert property.getObject(staticObject).equals("42");
        }
    }

    @Test
    @SuppressWarnings("unused")
    public void memoryFootprint1() {
        class MyField {
            final StaticProperty p;

            MyField(StaticProperty p) {
                this.p = p;
            }
        }

        new MyField(new DefaultStaticProperty("property1"));
    }

    @Test
    @SuppressWarnings("unused")
    public void memoryFootprint2() {
        class MyField extends StaticProperty {
            final Object name;

            MyField(Object name) {
                this.name = name;
            }

            @Override
            public String getId() {
                // this string must be a unique identifier within a Builder
                return name.toString();
            }
        }

        new MyField("property1");
    }

    @Test
    public void safetyChecks1() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("property");
            Object staticObject = builder.property(property, int.class, false).build().getFactory().create();
            try {
                property.setObject(staticObject, "wrong access type");
                assert false;
            } catch (IllegalArgumentException e) {
            }
        }
    }

    @Test
    @SuppressWarnings("unused")
    public void safetyChecks2() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("property");
            Object staticObject1 = builder.property(property, Object.class, false).build().getFactory().create();
            Object staticObject2 = StaticShape.newBuilder(te.testLanguage).build().getFactory().create();

            try {
                property.setObject(staticObject2, "wrong shape");
                assert false;
            } catch (IllegalArgumentException e) {
            }
        }
    }
}
