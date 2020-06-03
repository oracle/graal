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

import java.util.Set;
import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.Assert;
import org.junit.Test;

public class CxxMethodsTest extends InteropTestBase {

    private static Value allocPoint;
    private static Value freePoint;

    private static Value getX;
    private static Value getY;
    private static Value setX;
    private static Value setY;

    private static Value constructor;

    private static Value squaredEuclideanDistance;
    private static Value swap;

    @BeforeClass
    public static void loadTestBitcode() {
        Value testLibrary = loadTestBitcodeValue("methodsTest.cpp");

        allocPoint = testLibrary.getMember("allocNativePoint");
        freePoint = testLibrary.getMember("freeNativePoint");
        getX = testLibrary.getMember("getX");
        getY = testLibrary.getMember("getY");
        setX = testLibrary.getMember("setX");
        setY = testLibrary.getMember("setY");
        squaredEuclideanDistance = testLibrary.getMember("squaredEuclideanDistance");
        constructor = testLibrary.getMember("Point");
        swap = testLibrary.getMember("swap");
    }

    private static void checkPoint(Value point, int x, int y) {
        Assert.assertTrue("hasMembers", point.hasMembers());
        Assert.assertFalse("hasArrayElements", point.hasArrayElements());

        Set<String> members = point.getMemberKeys();
        Assert.assertArrayEquals("getMemberKeys", new Object[]{"x", "y"}, members.toArray());

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
            setX.execute(point, 30000);
            setY.execute(point, 4);
            checkPoint(point, 30000, 4);
            checkPoint(point, getX.execute(point).asInt(), getY.execute(point).asInt());

        } finally {
            freePoint.execute(point);
        }
    }

    @Test
    public void testMemberFunction() {
        Value point = allocPoint.execute();
        Value point2 = allocPoint.execute();
        try {
            setX.execute(point, 3);
            setY.execute(point, -4);
            setX.execute(point2, -6);
            setY.execute(point2, 8);
            checkPoint(point, 3, -4);
            checkPoint(point2, -6, 8);
            // swap point <-> point2
            swap.execute(point, point2);
            checkPoint(point2, 3, -4);
            checkPoint(point, -6, 8);
            // calculate distance
            Value distanceResult1 = squaredEuclideanDistance.execute(point, point2);
            Value distanceResult2 = squaredEuclideanDistance.execute(point2, point);
            Assert.assertEquals("distance(p, p2)", 0, Double.compare(225, distanceResult1.asDouble()));
            Assert.assertEquals("distance(p2, p)", 0, Double.compare(225, distanceResult2.asDouble()));

        } finally {
            freePoint.execute(point);
            freePoint.execute(point2);
        }
    }

    @Test
    public void testConstructor() {
        Value point = allocPoint.execute();
        try {
            constructor.execute(point);
            checkPoint(point, 0, 0);
        } finally {
            freePoint.execute(point);
        }
    }

}
