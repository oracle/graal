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
    private static final StaticProperty propertyB;
    private static final StaticProperty propertyS;
    private static final StaticProperty propertyC;
    private static final StaticProperty propertyI;
    private static final StaticProperty propertyL;
    private static final StaticProperty propertyF;
    private static final StaticProperty propertyD;
    private static final StaticProperty propertyTF;
    private static final StaticProperty propertyO1;
    private static final StaticProperty propertyO2;
    private static final Object staticObject;
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

    static {
        TestEnvironment environment = new TestEnvironment(new TestConfiguration(true, false));
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

    @Test
    public void staticallyDeclaredStaticObject() {
        Assert.assertEquals(testValueB, propertyB.getByte(staticObject));
        Assert.assertEquals(testValueS, propertyS.getShort(staticObject));
        Assert.assertEquals(testValueC, propertyC.getChar(staticObject));
        Assert.assertEquals(testValueI, propertyI.getInt(staticObject));
        Assert.assertEquals(testValueL, propertyL.getLong(staticObject));
        Assert.assertEquals(testValueF, propertyF.getFloat(staticObject), 1e-6f);
        Assert.assertEquals(testValueD, propertyD.getDouble(staticObject), 1e-6);
        Assert.assertEquals(testValueTF, propertyTF.getBoolean(staticObject));
        Assert.assertEquals(testValueO1, propertyO1.getObject(staticObject));
        Assert.assertEquals(testValueO2, propertyO2.getObject(staticObject));

        byte newTestValueB = 11;
        propertyB.setByte(staticObject, newTestValueB);
        Assert.assertEquals(newTestValueB, propertyB.getByte(staticObject));

        short newTestValueS = 22;
        propertyS.setShort(staticObject, newTestValueS);
        Assert.assertEquals(newTestValueS, propertyS.getShort(staticObject));

        char newTestValueC = 'b';
        propertyC.setChar(staticObject, newTestValueC);
        Assert.assertEquals(newTestValueC, propertyC.getChar(staticObject));

        int newTestValueI = 33;
        propertyI.setInt(staticObject, newTestValueI);
        Assert.assertEquals(newTestValueI, propertyI.getInt(staticObject));

        long newTestValueL = 44;
        propertyL.setLong(staticObject, newTestValueL);
        Assert.assertEquals(newTestValueL, propertyL.getLong(staticObject));

        float newTestValueF = 55.0f;
        propertyF.setFloat(staticObject, newTestValueF);
        Assert.assertEquals(newTestValueF, propertyF.getFloat(staticObject), 1e-6f);

        double newTestValueD = 66.0;
        propertyD.setDouble(staticObject, newTestValueD);
        Assert.assertEquals(newTestValueD, propertyD.getDouble(staticObject), 1e-6);

        propertyTF.setBoolean(staticObject, false);
        Assert.assertFalse(propertyTF.getBoolean(staticObject));

        Object newTestValueO = "object3";
        propertyO1.setObject(staticObject, newTestValueO);
        Assert.assertEquals(newTestValueO, propertyO1.getObject(staticObject));
        propertyO2.setObject(staticObject, newTestValueO);
        Assert.assertEquals(newTestValueO, propertyO2.getObject(staticObject));
    }
}
