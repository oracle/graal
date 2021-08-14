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

import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.api.staticobject.test.StaticObjectModelTest.TestConfiguration;
import com.oracle.truffle.api.staticobject.test.StaticObjectModelTest.TestEnvironment;
import org.junit.Assert;
import org.junit.Test;

/**
 * When running on Native Image, this test checks that the Static Object Model can be used at image
 * built time for context pre-initialization.
 */
public class StaticTest {
    private static final StaticProperty property1;
    private static final StaticProperty property2;
    private static final Object staticObject;
    private static final Object testValue1 = "object1";
    private static final Object testValue2 = "object2";

    static {
        TestEnvironment environment = new TestEnvironment(new TestConfiguration(true, false));
        StaticShape.Builder builder = StaticShape.newBuilder(environment.testLanguage);
        property1 = new DefaultStaticProperty("property1");
        property2 = new DefaultStaticProperty("property2");
        builder.property(property1, Object.class, false);
        builder.property(property2, Object.class, false);
        staticObject = builder.build().getFactory().create();
        property1.setObject(staticObject, testValue1);
        property2.setObject(staticObject, testValue2);
    }

    @Test
    public void staticallyDeclaredStaticObject() {
        Assert.assertEquals(testValue1, property1.getObject(staticObject));
        Assert.assertEquals(testValue2, property2.getObject(staticObject));
        Object newTestValue = "object3";
        property1.setObject(staticObject, newTestValue);
        Assert.assertEquals(newTestValue, property1.getObject(staticObject));
        property2.setObject(staticObject, newTestValue);
        Assert.assertEquals(newTestValue, property2.getObject(staticObject));
    }
}
