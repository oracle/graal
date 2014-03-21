/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.compiler.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;

public class MemoryArithmeticTest extends GraalCompilerTest {

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph graph) {
        return super.getCode(method, graph, true);
    }

    /**
     * Called before a test is executed.
     */
    @Override
    protected void before(Method method) {
        // don't let any null exception tracking change the generated code.
        getMetaAccess().lookupJavaMethod(method).reprofile();
    }

    /**
     * A dummy field used by some tests to create side effects.
     */
    protected static int count;

    static class FieldObject {
        boolean booleanValue;
        byte byteValue;
        short shortValue;
        char charValue;
        int intValue;
        float floatValue;
        long longValue;
        double doubleValue;
        Object objectValue;
    }

    static final boolean booleanTestValue1 = false;
    static final byte byteTestValue1 = 0;
    static final short shortTestValue1 = 0;
    static final char charTestValue1 = 0;
    static final int intTestValue1 = 0;
    static final float floatTestValue1 = 0;
    static final long longTestValue1 = 0;
    static final double doubleTestValue1 = 0;
    static final Object objectTestValue1 = null;

    static final boolean booleanTestValue2 = true;
    static final byte byteTestValue2 = Byte.MAX_VALUE;
    static final short shortTestValue2 = Short.MAX_VALUE;
    static final char charTestValue2 = Character.MAX_VALUE;
    static final int intTestValue2 = Integer.MAX_VALUE;
    static final float floatTestValue2 = Float.MAX_VALUE;
    static final long longTestValue2 = Long.MAX_VALUE;
    static final double doubleTestValue2 = Double.MAX_VALUE;
    static final Object objectTestValue2 = "String";

    public static Object testBooleanCompare(FieldObject f, boolean booleanValue) {
        if (f.booleanValue == booleanValue) {
            return f;
        }
        return null;
    }

    public static Object testBooleanCompareConstant1(FieldObject f) {
        if (f.booleanValue == booleanTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testBooleanCompareConstant2(FieldObject f) {
        if (f.booleanValue == booleanTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testBooleanCompares() {
        FieldObject f = new FieldObject();
        test("testBooleanCompare", f, booleanTestValue1);
        test("testBooleanCompareConstant1", f);
        test("testBooleanCompareConstant2", f);
    }

    @Test
    public void testBooleanNullCompares() {
        test("testBooleanCompare", null, booleanTestValue1);
    }

    public static Object testByteCompare(FieldObject f, byte byteValue) {
        if (f.byteValue == byteValue) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareConstant1(FieldObject f) {
        if (f.byteValue == byteTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testByteCompareConstant2(FieldObject f) {
        if (f.byteValue == byteTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testByteCompares() {
        FieldObject f = new FieldObject();
        test("testByteCompare", f, byteTestValue1);
        test("testByteCompareConstant1", f);
        test("testByteCompareConstant2", f);
    }

    @Test
    public void testByteNullCompares() {
        test("testByteCompare", null, byteTestValue1);
    }

    public static Object testShortCompare(FieldObject f, short shortValue) {
        if (f.shortValue == shortValue) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareConstant1(FieldObject f) {
        if (f.shortValue == shortTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testShortCompareConstant2(FieldObject f) {
        if (f.shortValue == shortTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testShortCompares() {
        FieldObject f = new FieldObject();
        test("testShortCompare", f, shortTestValue1);
        test("testShortCompareConstant1", f);
        test("testShortCompareConstant2", f);
    }

    @Test
    public void testShortNullCompares() {
        test("testShortCompare", null, shortTestValue1);
    }

    public static Object testCharCompare(FieldObject f, char charValue) {
        if (f.charValue == charValue) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareConstant1(FieldObject f) {
        if (f.charValue == charTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testCharCompareConstant2(FieldObject f) {
        if (f.charValue == charTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testCharCompares() {
        FieldObject f = new FieldObject();
        test("testCharCompare", f, charTestValue1);
        test("testCharCompareConstant1", f);
        test("testCharCompareConstant2", f);
    }

    @Test
    public void testCharNullCompares() {
        test("testCharCompare", null, charTestValue1);
    }

    public static Object testIntCompare(FieldObject f, int intValue) {
        if (f.intValue == intValue) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareConstant1(FieldObject f) {
        if (f.intValue == intTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testIntCompareConstant2(FieldObject f) {
        if (f.intValue == intTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testIntCompares() {
        FieldObject f = new FieldObject();
        test("testIntCompare", f, intTestValue1);
        test("testIntCompareConstant1", f);
        test("testIntCompareConstant2", f);
    }

    @Test
    public void testIntNullCompares() {
        test("testIntCompare", null, intTestValue1);
    }

    public static Object testFloatCompare(FieldObject f, float floatValue) {
        if (f.floatValue == floatValue) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareConstant1(FieldObject f) {
        if (f.floatValue == floatTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testFloatCompareConstant2(FieldObject f) {
        if (f.floatValue == floatTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testFloatCompares() {
        FieldObject f = new FieldObject();
        test("testFloatCompare", f, floatTestValue1);
        test("testFloatCompareConstant1", f);
        test("testFloatCompareConstant2", f);
    }

    @Test
    public void testFloatNullCompares() {
        test("testFloatCompare", null, floatTestValue1);
    }

    public static Object testLongCompare(FieldObject f, long longValue) {
        if (f.longValue == longValue) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareConstant1(FieldObject f) {
        if (f.longValue == longTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testLongCompareConstant2(FieldObject f) {
        if (f.longValue == longTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testLongCompares() {
        FieldObject f = new FieldObject();
        test("testLongCompare", f, longTestValue1);
        test("testLongCompareConstant1", f);
        test("testLongCompareConstant2", f);
    }

    @Test
    public void testLongNullCompares() {
        test("testLongCompare", null, longTestValue1);
    }

    public static Object testDoubleCompare(FieldObject f, double doubleValue) {
        if (f.doubleValue == doubleValue) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareConstant1(FieldObject f) {
        if (f.doubleValue == doubleTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testDoubleCompareConstant2(FieldObject f) {
        if (f.doubleValue == doubleTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testDoubleCompares() {
        FieldObject f = new FieldObject();
        test("testDoubleCompare", f, doubleTestValue1);
        test("testDoubleCompareConstant1", f);
        test("testDoubleCompareConstant2", f);
    }

    @Test
    public void testDoubleNullCompares() {
        test("testDoubleCompare", null, doubleTestValue1);
    }

    public static Object testObjectCompare(FieldObject f, Object objectValue) {
        if (f.objectValue == objectValue) {
            return f;
        }
        return null;
    }

    public static Object testObjectCompareConstant1(FieldObject f) {
        if (f.objectValue == objectTestValue1) {
            return f;
        }
        return null;
    }

    public static Object testObjectCompareConstant2(FieldObject f) {
        if (f.objectValue == objectTestValue2) {
            return f;
        }
        return null;
    }

    @Test
    public void testObjectCompares() {
        FieldObject f = new FieldObject();
        test("testObjectCompare", f, objectTestValue1);
        test("testObjectCompareConstant1", f);
        test("testObjectCompareConstant2", f);
    }

    @Test
    public void testObjectNullCompares() {
        test("testObjectCompare", null, objectTestValue1);
    }

    public static int testByteAdd(FieldObject f, byte byteValue) {
        return f.byteValue + byteValue;
    }

    public static int testByteAddConstant1(FieldObject f) {
        return f.byteValue + byteTestValue1;
    }

    public static int testByteAddConstant2(FieldObject f) {
        return f.byteValue + byteTestValue2;
    }

    @Test
    public void testByteAdds() {
        FieldObject f = new FieldObject();
        test("testByteAdd", f, byteTestValue1);
        test("testByteAddConstant1", f);
        test("testByteAddConstant2", f);
    }

    @Test
    public void testByteNullAdd() {
        test("testByteAdd", null, byteTestValue1);
    }

    public static int testShortAdd(FieldObject f, short shortValue) {
        return f.shortValue + shortValue;
    }

    public static int testShortAddConstant1(FieldObject f) {
        return f.shortValue + shortTestValue1;
    }

    public static int testShortAddConstant2(FieldObject f) {
        return f.shortValue + shortTestValue2;
    }

    @Test
    public void testShortAdds() {
        FieldObject f = new FieldObject();
        test("testShortAdd", f, shortTestValue1);
        test("testShortAddConstant1", f);
        test("testShortAddConstant2", f);
    }

    @Test
    public void testShortNullAdd() {
        test("testShortAdd", null, shortTestValue1);
    }

    public static int testCharAdd(FieldObject f, char charValue) {
        return f.charValue + charValue;
    }

    public static int testCharAddConstant1(FieldObject f) {
        return f.charValue + charTestValue1;
    }

    public static int testCharAddConstant2(FieldObject f) {
        return f.charValue + charTestValue2;
    }

    @Test
    public void testCharAdds() {
        FieldObject f = new FieldObject();
        test("testCharAdd", f, charTestValue1);
        test("testCharAddConstant1", f);
        test("testCharAddConstant2", f);
    }

    @Test
    public void testCharNullAdd() {
        test("testCharAdd", null, charTestValue1);
    }

    public static int testIntAdd(FieldObject f, int intValue) {
        return f.intValue + intValue;
    }

    public static int testIntAddConstant1(FieldObject f) {
        return f.intValue + intTestValue1;
    }

    public static int testIntAddConstant2(FieldObject f) {
        return f.intValue + intTestValue2;
    }

    @Test
    public void testIntAdds() {
        FieldObject f = new FieldObject();
        test("testIntAdd", f, intTestValue1);
        test("testIntAddConstant1", f);
        test("testIntAddConstant2", f);
    }

    @Test
    public void testIntNullAdd() {
        test("testIntAdd", null, intTestValue1);
    }

    public static long testLongAdd(FieldObject f, long longValue) {
        return f.longValue + longValue;
    }

    public static long testLongAddConstant1(FieldObject f) {
        return f.longValue + longTestValue1;
    }

    public static long testLongAddConstant2(FieldObject f) {
        return f.longValue + longTestValue2;
    }

    @Test
    public void testLongAdds() {
        FieldObject f = new FieldObject();
        test("testLongAdd", f, longTestValue1);
        test("testLongAddConstant1", f);
        test("testLongAddConstant2", f);
    }

    @Test
    public void testLongNullAdd() {
        test("testLongAdd", null, longTestValue1);
    }

    public static float testFloatAdd(FieldObject f, float floatValue) {
        return f.floatValue + floatValue;
    }

    public static float testFloatAddConstant1(FieldObject f) {
        return f.floatValue + floatTestValue1;
    }

    public static float testFloatAddConstant2(FieldObject f) {
        return f.floatValue + floatTestValue2;
    }

    @Test
    public void testFloatAdds() {
        FieldObject f = new FieldObject();
        test("testFloatAdd", f, floatTestValue1);
        test("testFloatAddConstant1", f);
        test("testFloatAddConstant2", f);
    }

    @Test
    public void testFloatNullAdd() {
        test("testFloatAdd", null, floatTestValue1);
    }

    public static double testDoubleAdd(FieldObject f, double doubleValue) {
        return f.doubleValue + doubleValue;
    }

    public static double testDoubleAddConstant1(FieldObject f) {
        return f.doubleValue + doubleTestValue1;
    }

    public static double testDoubleAddConstant2(FieldObject f) {
        return f.doubleValue + doubleTestValue2;
    }

    @Test
    public void testDoubleAdds() {
        FieldObject f = new FieldObject();
        test("testDoubleAdd", f, doubleTestValue1);
        test("testDoubleAddConstant1", f);
        test("testDoubleAddConstant2", f);
    }

    @Test
    public void testDoubleNullAdd() {
        test("testDoubleAdd", null, doubleTestValue1);
    }

    public static int testByteSub(FieldObject f, byte byteValue) {
        return f.byteValue - byteValue;
    }

    public static int testByteSubConstant1(FieldObject f) {
        return f.byteValue - byteTestValue1;
    }

    public static int testByteSubConstant2(FieldObject f) {
        return f.byteValue - byteTestValue2;
    }

    @Test
    public void testByteSubs() {
        FieldObject f = new FieldObject();
        test("testByteSub", f, byteTestValue1);
        test("testByteSubConstant1", f);
        test("testByteSubConstant2", f);
    }

    @Test
    public void testByteNullSub() {
        test("testByteSub", null, byteTestValue1);
    }

    public static int testShortSub(FieldObject f, short shortValue) {
        return f.shortValue - shortValue;
    }

    public static int testShortSubConstant1(FieldObject f) {
        return f.shortValue - shortTestValue1;
    }

    public static int testShortSubConstant2(FieldObject f) {
        return f.shortValue - shortTestValue2;
    }

    @Test
    public void testShortSubs() {
        FieldObject f = new FieldObject();
        test("testShortSub", f, shortTestValue1);
        test("testShortSubConstant1", f);
        test("testShortSubConstant2", f);
    }

    @Test
    public void testShortNullSub() {
        test("testShortSub", null, shortTestValue1);
    }

    public static int testCharSub(FieldObject f, char charValue) {
        return f.charValue - charValue;
    }

    public static int testCharSubConstant1(FieldObject f) {
        return f.charValue - charTestValue1;
    }

    public static int testCharSubConstant2(FieldObject f) {
        return f.charValue - charTestValue2;
    }

    @Test
    public void testCharSubs() {
        FieldObject f = new FieldObject();
        test("testCharSub", f, charTestValue1);
        test("testCharSubConstant1", f);
        test("testCharSubConstant2", f);
    }

    @Test
    public void testCharNullSub() {
        test("testCharSub", null, charTestValue1);
    }

    public static int testIntSub(FieldObject f, int intValue) {
        return f.intValue - intValue;
    }

    public static int testIntSubConstant1(FieldObject f) {
        return f.intValue - intTestValue1;
    }

    public static int testIntSubConstant2(FieldObject f) {
        return f.intValue - intTestValue2;
    }

    @Test
    public void testIntSubs() {
        FieldObject f = new FieldObject();
        test("testIntSub", f, intTestValue1);
        test("testIntSubConstant1", f);
        test("testIntSubConstant2", f);
    }

    @Test
    public void testIntNullSub() {
        test("testIntSub", null, intTestValue1);
    }

    public static long testLongSub(FieldObject f, long longValue) {
        return f.longValue - longValue;
    }

    public static long testLongSubConstant1(FieldObject f) {
        return f.longValue - longTestValue1;
    }

    public static long testLongSubConstant2(FieldObject f) {
        return f.longValue - longTestValue2;
    }

    @Test
    public void testLongSubs() {
        FieldObject f = new FieldObject();
        test("testLongSub", f, longTestValue1);
        test("testLongSubConstant1", f);
        test("testLongSubConstant2", f);
    }

    @Test
    public void testLongNullSub() {
        test("testLongSub", null, longTestValue1);
    }

    public static float testFloatSub(FieldObject f, float floatValue) {
        return f.floatValue - floatValue;
    }

    public static float testFloatSubConstant1(FieldObject f) {
        return f.floatValue - floatTestValue1;
    }

    public static float testFloatSubConstant2(FieldObject f) {
        return f.floatValue - floatTestValue2;
    }

    @Test
    public void testFloatSubs() {
        FieldObject f = new FieldObject();
        test("testFloatSub", f, floatTestValue1);
        test("testFloatSubConstant1", f);
        test("testFloatSubConstant2", f);
    }

    @Test
    public void testFloatNullSub() {
        test("testFloatSub", null, floatTestValue1);
    }

    public static double testDoubleSub(FieldObject f, double doubleValue) {
        return f.doubleValue - doubleValue;
    }

    public static double testDoubleSubConstant1(FieldObject f) {
        return f.doubleValue - doubleTestValue1;
    }

    public static double testDoubleSubConstant2(FieldObject f) {
        return f.doubleValue - doubleTestValue2;
    }

    @Test
    public void testDoubleSubs() {
        FieldObject f = new FieldObject();
        test("testDoubleSub", f, doubleTestValue1);
        test("testDoubleSubConstant1", f);
        test("testDoubleSubConstant2", f);
    }

    @Test
    public void testDoubleNullSub() {
        test("testDoubleSub", null, doubleTestValue1);
    }

    public static int testByteMul(FieldObject f, byte byteValue) {
        return f.byteValue * byteValue;
    }

    public static int testByteMulConstant1(FieldObject f) {
        return f.byteValue * byteTestValue1;
    }

    public static int testByteMulConstant2(FieldObject f) {
        return f.byteValue * byteTestValue2;
    }

    @Test
    public void testByteMuls() {
        FieldObject f = new FieldObject();
        test("testByteMul", f, byteTestValue1);
        test("testByteMulConstant1", f);
        test("testByteMulConstant2", f);
    }

    @Test
    public void testByteNullMul() {
        test("testByteMul", null, byteTestValue1);
    }

    public static int testShortMul(FieldObject f, short shortValue) {
        return f.shortValue * shortValue;
    }

    public static int testShortMulConstant1(FieldObject f) {
        return f.shortValue * shortTestValue1;
    }

    public static int testShortMulConstant2(FieldObject f) {
        return f.shortValue * shortTestValue2;
    }

    @Test
    public void testShortMuls() {
        FieldObject f = new FieldObject();
        test("testShortMul", f, shortTestValue1);
        test("testShortMulConstant1", f);
        test("testShortMulConstant2", f);
    }

    @Test
    public void testShortNullMul() {
        test("testShortMul", null, shortTestValue1);
    }

    public static int testCharMul(FieldObject f, char charValue) {
        return f.charValue * charValue;
    }

    public static int testCharMulConstant1(FieldObject f) {
        return f.charValue * charTestValue1;
    }

    public static int testCharMulConstant2(FieldObject f) {
        return f.charValue * charTestValue2;
    }

    @Test
    public void testCharMuls() {
        FieldObject f = new FieldObject();
        test("testCharMul", f, charTestValue1);
        test("testCharMulConstant1", f);
        test("testCharMulConstant2", f);
    }

    @Test
    public void testCharNullMul() {
        test("testCharMul", null, charTestValue1);
    }

    public static int testIntMul(FieldObject f, int intValue) {
        return f.intValue * intValue;
    }

    public static int testIntMulConstant1(FieldObject f) {
        return f.intValue * intTestValue1;
    }

    public static int testIntMulConstant2(FieldObject f) {
        return f.intValue * intTestValue2;
    }

    @Test
    public void testIntMuls() {
        FieldObject f = new FieldObject();
        test("testIntMul", f, intTestValue1);
        test("testIntMulConstant1", f);
        test("testIntMulConstant2", f);
    }

    @Test
    public void testIntNullMul() {
        test("testIntMul", null, intTestValue1);
    }

    public static long testLongMul(FieldObject f, long longValue) {
        return f.longValue * longValue;
    }

    public static long testLongMulConstant1(FieldObject f) {
        return f.longValue * longTestValue1;
    }

    public static long testLongMulConstant2(FieldObject f) {
        return f.longValue * longTestValue2;
    }

    @Test
    public void testLongMuls() {
        FieldObject f = new FieldObject();
        test("testLongMul", f, longTestValue1);
        test("testLongMulConstant1", f);
        test("testLongMulConstant2", f);
    }

    @Test
    public void testLongNullMul() {
        test("testLongMul", null, longTestValue1);
    }

    public static float testFloatMul(FieldObject f, float floatValue) {
        return f.floatValue * floatValue;
    }

    public static float testFloatMulConstant1(FieldObject f) {
        return f.floatValue * floatTestValue1;
    }

    public static float testFloatMulConstant2(FieldObject f) {
        return f.floatValue * floatTestValue2;
    }

    @Test
    public void testFloatMuls() {
        FieldObject f = new FieldObject();
        test("testFloatMul", f, floatTestValue1);
        test("testFloatMulConstant1", f);
        test("testFloatMulConstant2", f);
    }

    @Test
    public void testFloatNullMul() {
        test("testFloatMul", null, floatTestValue1);
    }

    public static double testDoubleMul(FieldObject f, double doubleValue) {
        return f.doubleValue * doubleValue;
    }

    public static double testDoubleMulConstant1(FieldObject f) {
        return f.doubleValue * doubleTestValue1;
    }

    public static double testDoubleMulConstant2(FieldObject f) {
        return f.doubleValue * doubleTestValue2;
    }

    @Test
    public void testDoubleMuls() {
        FieldObject f = new FieldObject();
        test("testDoubleMul", f, doubleTestValue1);
        test("testDoubleMulConstant1", f);
        test("testDoubleMulConstant2", f);
    }

    @Test
    public void testDoubleNullMul() {
        test("testDoubleMul", null, doubleTestValue1);
    }

    public static int testByteDiv(FieldObject f, byte byteValue) {
        return f.byteValue / byteValue;
    }

    public static int testByteDivConstant1(FieldObject f) {
        return f.byteValue / byteTestValue1;
    }

    public static int testByteDivConstant2(FieldObject f) {
        return f.byteValue / byteTestValue2;
    }

    @Test
    public void testByteDivs() {
        FieldObject f = new FieldObject();
        test("testByteDiv", f, byteTestValue1);
        test("testByteDivConstant1", f);
        test("testByteDivConstant2", f);
    }

    @Test
    public void testByteNullDiv() {
        test("testByteDiv", null, byteTestValue1);
    }

    public static int testShortDiv(FieldObject f, short shortValue) {
        return f.shortValue / shortValue;
    }

    public static int testShortDivConstant1(FieldObject f) {
        return f.shortValue / shortTestValue1;
    }

    public static int testShortDivConstant2(FieldObject f) {
        return f.shortValue / shortTestValue2;
    }

    @Test
    public void testShortDivs() {
        FieldObject f = new FieldObject();
        test("testShortDiv", f, shortTestValue1);
        test("testShortDivConstant1", f);
        test("testShortDivConstant2", f);
    }

    @Test
    public void testShortNullDiv() {
        test("testShortDiv", null, shortTestValue1);
    }

    public static int testCharDiv(FieldObject f, char charValue) {
        return f.charValue / charValue;
    }

    public static int testCharDivConstant1(FieldObject f) {
        return f.charValue / charTestValue1;
    }

    public static int testCharDivConstant2(FieldObject f) {
        return f.charValue / charTestValue2;
    }

    @Test
    public void testCharDivs() {
        FieldObject f = new FieldObject();
        test("testCharDiv", f, charTestValue1);
        test("testCharDivConstant1", f);
        test("testCharDivConstant2", f);
    }

    @Test
    public void testCharNullDiv() {
        test("testCharDiv", null, charTestValue1);
    }

    public static int testIntDiv(FieldObject f, int intValue) {
        return f.intValue / intValue;
    }

    public static int testIntDivConstant1(FieldObject f) {
        return f.intValue / intTestValue1;
    }

    public static int testIntDivConstant2(FieldObject f) {
        return f.intValue / intTestValue2;
    }

    @Test
    public void testIntDivs() {
        FieldObject f = new FieldObject();
        test("testIntDiv", f, intTestValue1);
        test("testIntDivConstant1", f);
        test("testIntDivConstant2", f);
    }

    @Test
    public void testIntNullDiv() {
        test("testIntDiv", null, intTestValue1);
    }

    public static long testLongDiv(FieldObject f, long longValue) {
        return f.longValue / longValue;
    }

    public static long testLongDivConstant1(FieldObject f) {
        return f.longValue / longTestValue1;
    }

    public static long testLongDivConstant2(FieldObject f) {
        return f.longValue / longTestValue2;
    }

    @Test
    public void testLongDivs() {
        FieldObject f = new FieldObject();
        test("testLongDiv", f, longTestValue1);
        test("testLongDivConstant1", f);
        test("testLongDivConstant2", f);
    }

    @Test
    public void testLongNullDiv() {
        test("testLongDiv", null, longTestValue1);
    }

    public static float testFloatDiv(FieldObject f, float floatValue) {
        return f.floatValue / floatValue;
    }

    public static float testFloatDivConstant1(FieldObject f) {
        return f.floatValue / floatTestValue1;
    }

    public static float testFloatDivConstant2(FieldObject f) {
        return f.floatValue / floatTestValue2;
    }

    @Test
    public void testFloatDivs() {
        FieldObject f = new FieldObject();
        test("testFloatDiv", f, floatTestValue1);
        test("testFloatDivConstant1", f);
        test("testFloatDivConstant2", f);
    }

    @Test
    public void testFloatNullDiv() {
        test("testFloatDiv", null, floatTestValue1);
    }

    public static double testDoubleDiv(FieldObject f, double doubleValue) {
        return f.doubleValue / doubleValue;
    }

    public static double testDoubleDivConstant1(FieldObject f) {
        return f.doubleValue / doubleTestValue1;
    }

    public static double testDoubleDivConstant2(FieldObject f) {
        return f.doubleValue / doubleTestValue2;
    }

    @Test
    public void testDoubleDivs() {
        FieldObject f = new FieldObject();
        test("testDoubleDiv", f, doubleTestValue1);
        test("testDoubleDivConstant1", f);
        test("testDoubleDivConstant2", f);
    }

    @Test
    public void testDoubleNullDiv() {
        test("testDoubleDiv", null, doubleTestValue1);
    }

    public static int testByteOr(FieldObject f, byte byteValue) {
        return f.byteValue | byteValue;
    }

    public static int testByteOrConstant1(FieldObject f) {
        return f.byteValue | byteTestValue1;
    }

    public static int testByteOrConstant2(FieldObject f) {
        return f.byteValue | byteTestValue2;
    }

    @Test
    public void testByteOrs() {
        FieldObject f = new FieldObject();
        test("testByteOr", f, byteTestValue1);
        test("testByteOrConstant1", f);
        test("testByteOrConstant2", f);
    }

    @Test
    public void testByteNullOr() {
        test("testByteOr", null, byteTestValue1);
    }

    public static int testShortOr(FieldObject f, short shortValue) {
        return f.shortValue | shortValue;
    }

    public static int testShortOrConstant1(FieldObject f) {
        return f.shortValue | shortTestValue1;
    }

    public static int testShortOrConstant2(FieldObject f) {
        return f.shortValue | shortTestValue2;
    }

    @Test
    public void testShortOrs() {
        FieldObject f = new FieldObject();
        test("testShortOr", f, shortTestValue1);
        test("testShortOrConstant1", f);
        test("testShortOrConstant2", f);
    }

    @Test
    public void testShortNullOr() {
        test("testShortOr", null, shortTestValue1);
    }

    public static int testCharOr(FieldObject f, char charValue) {
        return f.charValue | charValue;
    }

    public static int testCharOrConstant1(FieldObject f) {
        return f.charValue | charTestValue1;
    }

    public static int testCharOrConstant2(FieldObject f) {
        return f.charValue | charTestValue2;
    }

    @Test
    public void testCharOrs() {
        FieldObject f = new FieldObject();
        test("testCharOr", f, charTestValue1);
        test("testCharOrConstant1", f);
        test("testCharOrConstant2", f);
    }

    @Test
    public void testCharNullOr() {
        test("testCharOr", null, charTestValue1);
    }

    public static int testIntOr(FieldObject f, int intValue) {
        return f.intValue | intValue;
    }

    public static int testIntOrConstant1(FieldObject f) {
        return f.intValue | intTestValue1;
    }

    public static int testIntOrConstant2(FieldObject f) {
        return f.intValue | intTestValue2;
    }

    @Test
    public void testIntOrs() {
        FieldObject f = new FieldObject();
        test("testIntOr", f, intTestValue1);
        test("testIntOrConstant1", f);
        test("testIntOrConstant2", f);
    }

    @Test
    public void testIntNullOr() {
        test("testIntOr", null, intTestValue1);
    }

    public static long testLongOr(FieldObject f, long longValue) {
        return f.longValue | longValue;
    }

    public static long testLongOrConstant1(FieldObject f) {
        return f.longValue | longTestValue1;
    }

    public static long testLongOrConstant2(FieldObject f) {
        return f.longValue | longTestValue2;
    }

    @Test
    public void testLongOrs() {
        FieldObject f = new FieldObject();
        test("testLongOr", f, longTestValue1);
        test("testLongOrConstant1", f);
        test("testLongOrConstant2", f);
    }

    @Test
    public void testLongNullOr() {
        test("testLongOr", null, longTestValue1);
    }

    public static int testByteXor(FieldObject f, byte byteValue) {
        return f.byteValue ^ byteValue;
    }

    public static int testByteXorConstant1(FieldObject f) {
        return f.byteValue ^ byteTestValue1;
    }

    public static int testByteXorConstant2(FieldObject f) {
        return f.byteValue ^ byteTestValue2;
    }

    @Test
    public void testByteXors() {
        FieldObject f = new FieldObject();
        test("testByteXor", f, byteTestValue1);
        test("testByteXorConstant1", f);
        test("testByteXorConstant2", f);
    }

    @Test
    public void testByteNullXor() {
        test("testByteXor", null, byteTestValue1);
    }

    public static int testShortXor(FieldObject f, short shortValue) {
        return f.shortValue ^ shortValue;
    }

    public static int testShortXorConstant1(FieldObject f) {
        return f.shortValue ^ shortTestValue1;
    }

    public static int testShortXorConstant2(FieldObject f) {
        return f.shortValue ^ shortTestValue2;
    }

    @Test
    public void testShortXors() {
        FieldObject f = new FieldObject();
        test("testShortXor", f, shortTestValue1);
        test("testShortXorConstant1", f);
        test("testShortXorConstant2", f);
    }

    @Test
    public void testShortNullXor() {
        test("testShortXor", null, shortTestValue1);
    }

    public static int testCharXor(FieldObject f, char charValue) {
        return f.charValue ^ charValue;
    }

    public static int testCharXorConstant1(FieldObject f) {
        return f.charValue ^ charTestValue1;
    }

    public static int testCharXorConstant2(FieldObject f) {
        return f.charValue ^ charTestValue2;
    }

    @Test
    public void testCharXors() {
        FieldObject f = new FieldObject();
        test("testCharXor", f, charTestValue1);
        test("testCharXorConstant1", f);
        test("testCharXorConstant2", f);
    }

    @Test
    public void testCharNullXor() {
        test("testCharXor", null, charTestValue1);
    }

    public static int testIntXor(FieldObject f, int intValue) {
        return f.intValue ^ intValue;
    }

    public static int testIntXorConstant1(FieldObject f) {
        return f.intValue ^ intTestValue1;
    }

    public static int testIntXorConstant2(FieldObject f) {
        return f.intValue ^ intTestValue2;
    }

    @Test
    public void testIntXors() {
        FieldObject f = new FieldObject();
        test("testIntXor", f, intTestValue1);
        test("testIntXorConstant1", f);
        test("testIntXorConstant2", f);
    }

    @Test
    public void testIntNullXor() {
        test("testIntXor", null, intTestValue1);
    }

    public static long testLongXor(FieldObject f, long longValue) {
        return f.longValue ^ longValue;
    }

    public static long testLongXorConstant1(FieldObject f) {
        return f.longValue ^ longTestValue1;
    }

    public static long testLongXorConstant2(FieldObject f) {
        return f.longValue ^ longTestValue2;
    }

    @Test
    public void testLongXors() {
        FieldObject f = new FieldObject();
        test("testLongXor", f, longTestValue1);
        test("testLongXorConstant1", f);
        test("testLongXorConstant2", f);
    }

    @Test
    public void testLongNullXor() {
        test("testLongXor", null, longTestValue1);
    }

    public static int testByteAnd(FieldObject f, byte byteValue) {
        return f.byteValue & byteValue;
    }

    public static int testByteAndConstant1(FieldObject f) {
        return f.byteValue & byteTestValue1;
    }

    public static int testByteAndConstant2(FieldObject f) {
        return f.byteValue & byteTestValue2;
    }

    @Test
    public void testByteAnds() {
        FieldObject f = new FieldObject();
        test("testByteAnd", f, byteTestValue1);
        test("testByteAndConstant1", f);
        test("testByteAndConstant2", f);
    }

    @Test
    public void testByteNullAnd() {
        test("testByteAnd", null, byteTestValue1);
    }

    public static int testShortAnd(FieldObject f, short shortValue) {
        return f.shortValue & shortValue;
    }

    public static int testShortAndConstant1(FieldObject f) {
        return f.shortValue & shortTestValue1;
    }

    public static int testShortAndConstant2(FieldObject f) {
        return f.shortValue & shortTestValue2;
    }

    @Test
    public void testShortAnds() {
        FieldObject f = new FieldObject();
        test("testShortAnd", f, shortTestValue1);
        test("testShortAndConstant1", f);
        test("testShortAndConstant2", f);
    }

    @Test
    public void testShortNullAnd() {
        test("testShortAnd", null, shortTestValue1);
    }

    public static int testCharAnd(FieldObject f, char charValue) {
        return f.charValue & charValue;
    }

    public static int testCharAndConstant1(FieldObject f) {
        return f.charValue & charTestValue1;
    }

    public static int testCharAndConstant2(FieldObject f) {
        return f.charValue & charTestValue2;
    }

    @Test
    public void testCharAnds() {
        FieldObject f = new FieldObject();
        test("testCharAnd", f, charTestValue1);
        test("testCharAndConstant1", f);
        test("testCharAndConstant2", f);
    }

    @Test
    public void testCharNullAnd() {
        test("testCharAnd", null, charTestValue1);
    }

    public static int testIntAnd(FieldObject f, int intValue) {
        return f.intValue & intValue;
    }

    public static int testIntAndConstant1(FieldObject f) {
        return f.intValue & intTestValue1;
    }

    public static int testIntAndConstant2(FieldObject f) {
        return f.intValue & intTestValue2;
    }

    @Test
    public void testIntAnds() {
        FieldObject f = new FieldObject();
        test("testIntAnd", f, intTestValue1);
        test("testIntAndConstant1", f);
        test("testIntAndConstant2", f);
    }

    @Test
    public void testIntNullAnd() {
        test("testIntAnd", null, intTestValue1);
    }

    public static long testLongAnd(FieldObject f, long longValue) {
        return f.longValue & longValue;
    }

    public static long testLongAndConstant1(FieldObject f) {
        return f.longValue & longTestValue1;
    }

    public static long testLongAndConstant2(FieldObject f) {
        return f.longValue & longTestValue2;
    }

    @Test
    public void testLongAnds() {
        FieldObject f = new FieldObject();
        test("testLongAnd", f, longTestValue1);
        test("testLongAndConstant1", f);
        test("testLongAndConstant2", f);
    }

    @Test
    public void testLongNullAnd() {
        test("testLongAnd", null, longTestValue1);
    }

    public static boolean testIntMask(FieldObject f, int intValue) {
        if ((f.intValue & intValue) != 0) {
            count++;
            return false;
        }
        return true;
    }

    public static boolean testIntMaskConstant1(FieldObject f) {
        return (f.intValue & intTestValue1) != 0;
    }

    public static boolean testIntMaskConstant2(FieldObject f) {
        return (f.intValue & intTestValue2) != 0;
    }

    @Test
    public void testIntMasks() {
        FieldObject f = new FieldObject();
        test("testIntMask", f, intTestValue1);
        test("testIntMaskConstant1", f);
        test("testIntMaskConstant2", f);
    }

    @Test
    public void testIntNullMask() {
        test("testIntMask", null, intTestValue1);
    }

    public static boolean testLongMask(FieldObject f, long longValue) {
        if ((f.longValue & longValue) != 0) {
            count++;
            return false;
        }
        return true;
    }

    public static boolean testLongMaskConstant1(FieldObject f) {
        return (f.longValue & longTestValue1) != 0;
    }

    public static boolean testLongMaskConstant2(FieldObject f) {
        return (f.longValue & longTestValue2) != 0;
    }

    @Test
    public void testLongMasks() {
        FieldObject f = new FieldObject();
        test("testLongMask", f, longTestValue1);
        test("testLongMaskConstant1", f);
        test("testLongMaskConstant2", f);
    }

    @Test
    public void testLongNullMask() {
        test("testLongMask", null, longTestValue1);
    }

}
