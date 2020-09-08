/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.interop;

import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.Assert;
import org.junit.Test;

public class CxxMethodsTest extends InteropTestBase {

    private static Value allocPoint;
    private static Value freePoint;
    private static Value allocXtendPoint;
    private static Value freeXtendPoint;

    private static Value constructor;
    private static Value squaredEuclideanDistance;
    private static Value swap;

    private static Value testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeValue("methodsTest.cpp");

        allocPoint = testLibrary.getMember("allocNativePoint");
        freePoint = testLibrary.getMember("freeNativePoint");
        allocXtendPoint = testLibrary.getMember("allocNativeXtendPoint");
        freeXtendPoint = testLibrary.getMember("freeNativeXtendPoint");
        squaredEuclideanDistance = testLibrary.getMember("squaredEuclideanDistance");
        constructor = testLibrary.getMember("Point");
        swap = testLibrary.getMember("swap");
    }

    private static void checkPoint(Value point, int x, int y) {
        Assert.assertTrue("hasMembers", point.hasMembers());
        Assert.assertFalse("hasArrayElements", point.hasArrayElements());

        Assert.assertTrue("hasMember(x)", point.hasMember("x"));
        Assert.assertTrue("hasMember(y)", point.hasMember("y"));

        Assert.assertEquals("y", y, point.getMember("y").asInt());
        Assert.assertEquals("x", x, point.getMember("x").asInt());
    }

    @Test
    public void testAllocPoint() {
        Value point = allocPoint.execute();
        try {

            Assert.assertTrue("hasMember(x)", point.hasMember("x"));
            Assert.assertTrue("hasMember(y)", point.hasMember("y"));

        } finally {
            freePoint.execute(point);
        }
    }

    @Test
    public void testGettersAndSetters() {
        Value point = allocPoint.execute();
        try {
            point.invokeMember("setX", 30000);
            point.invokeMember("setY", 4);
            checkPoint(point, 30000, 4);
            checkPoint(point, point.invokeMember("getX").asInt(), point.invokeMember("getY").asInt());

        } finally {
            freePoint.execute(point);
        }
    }

    @Test
    public void testMemberFunction() {
        Value point = testLibrary.invokeMember("allocNativePoint");
        Value point2 = allocPoint.execute();
        try {
            point.invokeMember("setX", 3);
            Assert.assertEquals("getX()==3 after setX(3)", 3, point.invokeMember("getX").asInt());
            point.invokeMember("setY", -4);
            point2.invokeMember("setX", -6);
            testLibrary.invokeMember("setY", point2, 8);
            checkPoint(point, 3, -4);
            checkPoint(point2, -6, 8);
            // swap point <-> point2
            swap.execute(point, point2);
            checkPoint(point2, 3, -4);
            checkPoint(point, -6, 8);
            // calculate distance
            Value distanceResult1 = squaredEuclideanDistance.execute(point, point2);
            Value distanceResult2 = squaredEuclideanDistance.execute(point2, point);
            Value distanceResult3 = point2.invokeMember("squaredEuclideanDistance", point);
            Value distanceResult4 = point.invokeMember("squaredEuclideanDistance", point2);

            Assert.assertEquals("distance(p, p2)", 0, Double.compare(225, distanceResult1.asDouble()));
            Assert.assertEquals("distance(p2, p)", 0, Double.compare(225, distanceResult2.asDouble()));
            Assert.assertEquals("p.distance(p2)", 0, Double.compare(225, distanceResult3.asDouble()));
            Assert.assertEquals("p2.distance(p)", 0, Double.compare(225, distanceResult4.asDouble()));

        } finally {
            testLibrary.invokeMember("freeNativePoint", point);
            freePoint.execute(point2);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonExistingMethod() {
        Value point = allocPoint.execute();
        try {
            point.invokeMember("methodWhichDoesNotExist");
        } finally {
            testLibrary.invokeMember("freeNativePoint", point);
        }
    }

    @Test
    public void testOverloadedMethods() {
        Value xPoint = allocXtendPoint.execute();
        try {
            xPoint.invokeMember("setZ", 1);
            Assert.assertEquals("getZ()", 1, xPoint.invokeMember("getZ").asInt());
            Assert.assertEquals("getZ(2)", 3, xPoint.invokeMember("getZ", 2).asInt());
        } finally {
            freeXtendPoint.execute(xPoint);
        }
    }

    @Test
    public void testInheritedMethodsFromSuperclass() {
        Value xPoint = allocXtendPoint.execute();
        try {
            xPoint.invokeMember("setX", 6);
            xPoint.invokeMember("setY", 7);
            Assert.assertEquals("direct call: getX()", 12, xPoint.invokeMember("getX").asInt());
            Assert.assertEquals("superclass::getY()", 7, xPoint.invokeMember("getY").asInt());
        } finally {
            freeXtendPoint.execute(xPoint);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrongArity() {
        Value point = constructor.newInstance();
        point.invokeMember("setX");
    }

    @Test
    public void testConstructor() {
        Value point = constructor.newInstance();
        checkPoint(point, 0, 0);
        point.invokeMember("setX", 6);
        checkPoint(point, 6, 0);
    }

}
