/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.tests.interop.values.NullValue;
import com.oracle.truffle.llvm.tests.Platform;
import java.util.Set;
import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class TypedExportTest extends InteropTestBase {

    private static Value allocPoint;
    private static Value allocPointUninitialized;
    private static Value readPoint;
    private static Value freePoint;

    private static Value allocPointArray;
    private static Value readPointArray;

    private static Value allocNested;
    private static Value freeNested;
    private static Value hashNested;

    private static Value getAliasedPtrIndex;
    private static Value findPoint;

    @BeforeClass
    public static void loadTestBitcode() {
        Value testLibrary = loadTestBitcodeValue("typedExport.c");
        allocPoint = testLibrary.getMember("allocPoint");
        allocPointUninitialized = testLibrary.getMember("allocPointUninitialized");
        readPoint = testLibrary.getMember("readPoint");
        freePoint = testLibrary.getMember("freePoint");

        allocPointArray = testLibrary.getMember("allocPointArray");
        readPointArray = testLibrary.getMember("readPointArray");

        allocNested = testLibrary.getMember("allocNested");
        freeNested = testLibrary.getMember("freeNested");
        hashNested = testLibrary.getMember("hashNested");

        getAliasedPtrIndex = testLibrary.getMember("getAliasedPtrIndex");
        findPoint = testLibrary.getMember("findPoint");
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
        Value point = allocPoint.execute(42, 24);
        try {
            checkPoint(point, 42, 24);

            Value ret = readPoint.execute(point);
            Assert.assertEquals("readPoint", 42024, ret.asInt());
        } finally {
            freePoint.execute(point);
        }
    }

    @Test
    public void testWriteToPoint() {
        Value point = allocPoint.execute(42, 24);
        try {
            checkPoint(point, 42, 24);

            point.putMember("x", 12);
            point.putMember("y", 34);

            checkPoint(point, 12, 34);

            Value ret = readPoint.execute(point);
            Assert.assertEquals("readPoint", 12034, ret.asInt());
        } finally {
            freePoint.execute(point);
        }
    }

    @Test
    public void testAllocUnitialized() {
        Value point = allocPointUninitialized.execute();
        try {
            point.putMember("x", 5);
            point.putMember("y", 7);

            checkPoint(point, 5, 7);

            Value ret = readPoint.execute(point);
            Assert.assertEquals("readPoint", 5007, ret.asInt());
        } finally {
            freePoint.execute(point);
        }
    }

    private static void checkPointArray(Value array, int size) {
        Assert.assertFalse("hasMembers", array.hasMembers());
        Assert.assertTrue("hasArrayElements", array.hasArrayElements());

        Assert.assertEquals("size", size, array.getArraySize());

        for (int i = 0; i < size; i++) {
            Value point = array.getArrayElement(i);
            checkPoint(point, 0, 0);
        }
    }

    @Test
    public void testAllocPointArray() {
        Value array = allocPointArray.execute(15);
        try {
            checkPointArray(array, 15);
        } finally {
            freePoint.execute(array);
        }
    }

    @Test
    public void testWriteToPointArray() {
        Value array = allocPointArray.execute(27);
        try {
            for (int i = 0; i < 27; i++) {
                Value point = array.getArrayElement(i);
                point.putMember("x", 2 * i);
                point.putMember("y", 2 * i + 1);
            }

            for (int i = 0; i < 27; i++) {
                Value point = array.getArrayElement(i);
                checkPoint(point, 2 * i, 2 * i + 1);

                Value ret = readPoint.execute(point);
                Assert.assertEquals("readPoint", 2002 * i + 1, ret.asInt());
            }

            for (int i = 0; i < 27; i++) {
                Value ret = readPointArray.execute(array, i);
                Assert.assertEquals("readPointArray", 2002 * i + 1, ret.asInt());
            }
        } finally {
            freePoint.execute(array);
        }
    }

    @Test
    public void testReadPrimArray() {
        Value nested = allocNested.execute();
        try {
            Value primArray = nested.getMember("primArray");
            Assert.assertEquals("primArray.getArraySize()", 13, primArray.getArraySize());

            for (int i = 0; i < primArray.getArraySize(); i++) {
                Assert.assertEquals("primArray[" + i + "]", 3 * i + 1, primArray.getArrayElement(i).asLong());
            }
        } finally {
            freeNested.execute(nested);
        }
    }

    @Test
    public void testNested() {
        Value nested = allocNested.execute();
        try {
            long expected = 0;
            Value primArray = nested.getMember("primArray");
            for (int i = 0; i < primArray.getArraySize(); i++) {
                long value = 5 * i + 3;
                expected = 13L * expected + value;
                primArray.setArrayElement(i, value);
            }

            Value pointArray = nested.getMember("pointArray");
            for (int i = 0; i < pointArray.getArraySize(); i++) {
                Value point = pointArray.getArrayElement(i);
                long x = 7L * i + 2;
                point.putMember("x", x);
                expected = 13 * expected + x;

                long y = 7L * i + 5;
                point.putMember("y", y);
                expected = 13 * expected + y;
            }

            Value ptrArray = nested.getMember("ptrArray");
            for (int i = 0; i < ptrArray.getArraySize(); i++) {
                Value point = ptrArray.getArrayElement(i);
                long x = 11L * i + 3;
                point.putMember("x", x);
                expected = 13 * expected + x;

                long y = 11L * i + 7;
                point.putMember("y", y);
                expected = 13 * expected + y;
            }

            Value actual = hashNested.execute(nested);
            Assert.assertEquals("hashNested", expected, actual.asLong());
        } finally {
            freeNested.execute(nested);
        }
    }

    @Test
    public void testAliasedPointer() {
        Assume.assumeFalse("Skipping AArch64 failing test", Platform.isAArch64());
        Value nested = allocNested.execute();
        try {
            Value pointArray = nested.getMember("pointArray");

            Value somePoint = pointArray.getArrayElement(2);
            nested.putMember("aliasedPtr", somePoint);

            Value ret = getAliasedPtrIndex.execute(nested);
            Assert.assertEquals("index", 2, ret.asInt());
        } finally {
            freeNested.execute(nested);
        }
    }

    @Test
    public void testWriteArray() {
        Value nested = allocNested.execute();
        Value pointToFree = null;
        try {
            Value ptrArray = nested.getMember("ptrArray");

            Value somePoint = allocPoint.execute(1, 2);
            pointToFree = somePoint;

            Value notFound = findPoint.execute(nested, somePoint);
            Assert.assertEquals("notFound", -1, notFound.asInt());

            Value oldPoint = ptrArray.getArrayElement(3);
            ptrArray.setArrayElement(3, somePoint);
            pointToFree = oldPoint;

            Value found = findPoint.execute(nested, somePoint);
            Assert.assertEquals("index", 3, found.asInt());
        } finally {
            if (pointToFree != null) {
                freePoint.execute(pointToFree);
            }
            freeNested.execute(nested);
        }
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testOutOfBoundsAccess() {
        Value nested = allocNested.execute();
        try {
            Value pointArray = nested.getMember("pointArray");
            pointArray.getArrayElement(15);
        } finally {
            freeNested.execute(nested);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInvalidStructWrite() {
        Value nested = allocNested.execute();
        try {
            nested.putMember("primArray", new NullValue());
        } finally {
            freeNested.execute(nested);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInvalidArrayWrite() {
        Value nested = allocNested.execute();
        try {
            Value pointArray = nested.getMember("pointArray");
            pointArray.setArrayElement(2, new NullValue());
        } finally {
            freeNested.execute(nested);
        }
    }
}
