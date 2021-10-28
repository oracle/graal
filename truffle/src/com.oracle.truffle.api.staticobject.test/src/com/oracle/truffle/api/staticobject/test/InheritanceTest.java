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
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InheritanceTest extends StaticObjectModelTest {
    @Parameterized.Parameters(name = "{0}")
    public static TestConfiguration[] data() {
        return getTestConfigurations();
    }

    @Parameterized.Parameter public TestConfiguration config;

    public static class CustomStaticObject {
        public byte field1;
        public boolean field2;
    }

    public interface CustomStaticObjectFactory {
        CustomStaticObject create();
    }

    @Test
    public void baseClassInheritance() throws NoSuchFieldException {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder builder = StaticShape.newBuilder(te.testLanguage);
            StaticProperty property = new DefaultStaticProperty("field1");
            builder.property(property, int.class, false);
            StaticShape<CustomStaticObjectFactory> shape = builder.build(CustomStaticObject.class, CustomStaticObjectFactory.class);
            CustomStaticObject object = shape.getFactory().create();

            // Set to the field declared in the super class
            object.field1 = 42;
            // Set to the field declared in the generated class
            property.setInt(object, 24);
            // Get the value of the field declared in the super class
            Assert.assertEquals(42, object.field1);
            // Get the value of the field declared in the generated class
            Assert.assertEquals(24, property.getInt(object));

            Assume.assumeTrue(te.isFieldBased());
            // `CustomStaticObject.field1` is shadowed
            Assert.assertEquals(int.class, object.getClass().getField("field1").getType());
            // `CustomStaticObject.field2` is visible
            Assert.assertEquals(boolean.class, object.getClass().getField("field2").getType());
        }
    }

    @Test
    public void baseShapeInheritance() throws NoSuchFieldException, IllegalAccessException {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder b1 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty s1p1 = new DefaultStaticProperty("field1");
            StaticProperty s1p2 = new DefaultStaticProperty("field2");
            b1.property(s1p1, int.class, false);
            b1.property(s1p2, int.class, false);
            // StaticShape s1 declares 2 properties: s1p1 and s1p2
            StaticShape<DefaultStaticObjectFactory> s1 = b1.build();

            StaticShape.Builder b2 = StaticShape.newBuilder(te.testLanguage);
            StaticProperty s2p1 = new DefaultStaticProperty("field1");
            b2.property(s2p1, int.class, false);
            // StaticShape s2:
            // 1. extends s1
            // 2. declares one property: s2p1
            // 3. inherits one property from s1: s1p2
            StaticShape<DefaultStaticObjectFactory> s2 = b2.build(s1);
            Object object = s2.getFactory().create();

            s1p1.setInt(object, 1);
            Assert.assertEquals(1, s1p1.getInt(object));
            Assert.assertEquals(0, s2p1.getInt(object));
            s1p2.setInt(object, 2);
            Assert.assertEquals(2, s1p2.getInt(object));
            s2p1.setInt(object, 3);
            // s1p1 accesses the field declared in s1
            Assert.assertEquals(1, s1p1.getInt(object));
            Assert.assertEquals(3, s2p1.getInt(object));

            Assume.assumeTrue(te.isFieldBased());
            Assert.assertEquals(3, object.getClass().getField("field1").getInt(object));
        }
    }
}
