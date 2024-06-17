/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;

import static org.junit.Assert.assertNull;

/**
 * The context pre-initialization aspect of the Static Object Model is tested in the
 * {@code ContextPreInitializationNativeImageTest#somObjectAllocatedOnContextPreInit()}.
 */
public class StaticTest {

    private static final byte testValueB = 1;
    private static final short testValueS = 2;
    private static final char testValueC = 'a';
    private static final int testValueI = 3;
    private static final long testValueL = 4;
    private static final float testValueF = 5.0f;
    private static final double testValueD = 6.0;
    private static final boolean testValueTF = true;
    private static final Object testValueO1 = "object1";
    private static final Object testValueO2 = "object2";

    private static final class TestData implements Closeable {
        private final TestEnvironment environment;
        final StaticProperty propertyB;
        final StaticProperty propertyS;
        final StaticProperty propertyC;
        final StaticProperty propertyI;
        final StaticProperty propertyL;
        final StaticProperty propertyF;
        final StaticProperty propertyD;
        final StaticProperty propertyTF;
        final StaticProperty propertyO1;
        final StaticProperty propertyO2;
        final Object staticObject;

        TestData() {
            environment = new TestEnvironment(new TestConfiguration(true, false));
            StaticShape.Builder builder = StaticShape.newBuilder(environment.testLanguage);
            propertyB = new DefaultStaticProperty("propertyB");
            propertyS = new DefaultStaticProperty("propertyS");
            propertyC = new DefaultStaticProperty("propertyC");
            propertyI = new DefaultStaticProperty("propertyI");
            propertyL = new DefaultStaticProperty("propertyL");
            propertyF = new DefaultStaticProperty("propertyF");
            propertyD = new DefaultStaticProperty("propertyD");
            propertyTF = new DefaultStaticProperty("propertyTF");
            propertyO1 = new DefaultStaticProperty("propertyO1");
            propertyO2 = new DefaultStaticProperty("propertyO2");
            builder.property(propertyB, byte.class, false);
            builder.property(propertyS, short.class, false);
            builder.property(propertyC, char.class, false);
            builder.property(propertyI, int.class, false);
            builder.property(propertyL, long.class, false);
            builder.property(propertyF, float.class, false);
            builder.property(propertyD, double.class, false);
            builder.property(propertyTF, boolean.class, false);
            builder.property(propertyO1, Object.class, false);
            builder.property(propertyO2, Object.class, false);
            staticObject = builder.build().getFactory().create();
            propertyB.setByte(staticObject, testValueB);
            propertyS.setShort(staticObject, testValueS);
            propertyC.setChar(staticObject, testValueC);
            propertyI.setInt(staticObject, testValueI);
            propertyL.setLong(staticObject, testValueL);
            propertyF.setFloat(staticObject, testValueF);
            propertyD.setDouble(staticObject, testValueD);
            propertyTF.setBoolean(staticObject, testValueTF);
            propertyO1.setObject(staticObject, testValueO1);
            propertyO2.setObject(staticObject, testValueO2);
        }

        @Override
        public void close() {
            environment.close();
        }
    }

    private TestData testData;

    @Before
    public void setUp() {
        assertNull(testData);
        testData = new TestData();
    }

    @After
    public void tearDown() {
        if (testData != null) {
            testData.close();
            testData = null;
        }
    }

    @Test
    public void staticallyDeclaredStaticObject() {
        Assert.assertEquals(testValueB, testData.propertyB.getByte(testData.staticObject));
        Assert.assertEquals(testValueS, testData.propertyS.getShort(testData.staticObject));
        Assert.assertEquals(testValueC, testData.propertyC.getChar(testData.staticObject));
        Assert.assertEquals(testValueI, testData.propertyI.getInt(testData.staticObject));
        Assert.assertEquals(testValueL, testData.propertyL.getLong(testData.staticObject));
        Assert.assertEquals(testValueF, testData.propertyF.getFloat(testData.staticObject), 1e-6f);
        Assert.assertEquals(testValueD, testData.propertyD.getDouble(testData.staticObject), 1e-6);
        Assert.assertEquals(testValueTF, testData.propertyTF.getBoolean(testData.staticObject));
        Assert.assertEquals(testValueO1, testData.propertyO1.getObject(testData.staticObject));
        Assert.assertEquals(testValueO2, testData.propertyO2.getObject(testData.staticObject));

        byte newTestValueB = 11;
        testData.propertyB.setByte(testData.staticObject, newTestValueB);
        Assert.assertEquals(newTestValueB, testData.propertyB.getByte(testData.staticObject));

        short newTestValueS = 22;
        testData.propertyS.setShort(testData.staticObject, newTestValueS);
        Assert.assertEquals(newTestValueS, testData.propertyS.getShort(testData.staticObject));

        char newTestValueC = 'b';
        testData.propertyC.setChar(testData.staticObject, newTestValueC);
        Assert.assertEquals(newTestValueC, testData.propertyC.getChar(testData.staticObject));

        int newTestValueI = 33;
        testData.propertyI.setInt(testData.staticObject, newTestValueI);
        Assert.assertEquals(newTestValueI, testData.propertyI.getInt(testData.staticObject));

        long newTestValueL = 44;
        testData.propertyL.setLong(testData.staticObject, newTestValueL);
        Assert.assertEquals(newTestValueL, testData.propertyL.getLong(testData.staticObject));

        float newTestValueF = 55.0f;
        testData.propertyF.setFloat(testData.staticObject, newTestValueF);
        Assert.assertEquals(newTestValueF, testData.propertyF.getFloat(testData.staticObject), 1e-6f);

        double newTestValueD = 66.0;
        testData.propertyD.setDouble(testData.staticObject, newTestValueD);
        Assert.assertEquals(newTestValueD, testData.propertyD.getDouble(testData.staticObject), 1e-6);

        testData.propertyTF.setBoolean(testData.staticObject, false);
        Assert.assertFalse(testData.propertyTF.getBoolean(testData.staticObject));

        Object newTestValueO = "object3";
        testData.propertyO1.setObject(testData.staticObject, newTestValueO);
        Assert.assertEquals(newTestValueO, testData.propertyO1.getObject(testData.staticObject));
        testData.propertyO2.setObject(testData.staticObject, newTestValueO);
        Assert.assertEquals(newTestValueO, testData.propertyO2.getObject(testData.staticObject));
    }
}
