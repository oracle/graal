/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.replacements.test;

import org.junit.Test;

import jdk.graal.compiler.test.AddExports;

@AddExports("java.base/jdk.internal.misc")
public class UnsafeReplacementsTest extends MethodSubstitutionTest {

    static class Container {
        public volatile boolean booleanField;
        public volatile byte byteField = -17;
        public volatile char charField = 1025;
        public volatile short shortField = -2232;
        public volatile int intField = 0xcafebabe;
        public volatile long longField = 0xdedababafafaL;
        public volatile float floatField = 0.125f;
        public volatile double doubleField = 0.125;
        public byte[] byteArrayField = new byte[16];
    }

    static jdk.internal.misc.Unsafe unsafe = jdk.internal.misc.Unsafe.getUnsafe();
    static Container dummyValue = new Container();
    static Container newDummyValue = new Container();
    static long booleanOffset;
    static long byteOffset;
    static long charOffset;
    static long shortOffset;
    static long intOffset;
    static long longOffset;
    static long floatOffset;
    static long doubleOffset;
    static long byteArrayBaseOffset;

    static final int WEAK_ATTEMPTS = 10;

    static {
        try {
            booleanOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("booleanField"));
            byteOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("byteField"));
            charOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("charField"));
            shortOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("shortField"));
            intOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("intField"));
            longOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("longField"));
            floatOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("floatField"));
            doubleOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("doubleField"));
            byteArrayBaseOffset = unsafe.arrayBaseOffset(byte[].class);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean unsafeCompareAndSetBoolean() {
        Container container = new Container();
        return unsafe.compareAndSetBoolean(container, booleanOffset, false, true);
    }

    public static boolean unsafeCompareAndSetByte() {
        Container container = new Container();
        return unsafe.compareAndSetByte(container, byteOffset, (byte) -17, (byte) 121);
    }

    public static boolean unsafeCompareAndSetByteWithIntArgs(int expectedValue, int newValue) {
        Container container = new Container();
        return unsafe.compareAndSetByte(container, byteOffset, (byte) expectedValue, (byte) newValue);
    }

    public static boolean unsafeCompareAndSetChar() {
        Container container = new Container();
        return unsafe.compareAndSetChar(container, charOffset, (char) 1025, (char) 1777);
    }

    public static boolean unsafeCompareAndSetCharWithIntArgs(int expectedValue, int newValue) {
        Container container = new Container();
        return unsafe.compareAndSetChar(container, charOffset, (char) expectedValue, (char) newValue);
    }

    public static boolean unsafeCompareAndSetShort() {
        Container container = new Container();
        return unsafe.compareAndSetShort(container, shortOffset, (short) -2232, (short) 12111);
    }

    public static boolean unsafeCompareAndSetShortWithIntArgs(int expectedValue, int newValue) {
        Container container = new Container();
        return unsafe.compareAndSetShort(container, shortOffset, (short) expectedValue, (short) newValue);
    }

    public static boolean unsafeCompareAndSetInt() {
        Container container = new Container();
        return unsafe.compareAndSetInt(container, intOffset, 0xcafebabe, 0xbabefafa);
    }

    public static boolean unsafeCompareAndSetLong() {
        Container container = new Container();
        return unsafe.compareAndSetLong(container, longOffset, 0xdedababafafaL, 0xfafacecafafadedaL);
    }

    public static boolean unsafeCompareAndSetFloat() {
        Container container = new Container();
        return unsafe.compareAndSetFloat(container, floatOffset, 0.125f, 0.25f);
    }

    public static boolean unsafeCompareAndSetDouble() {
        Container container = new Container();
        return unsafe.compareAndSetDouble(container, doubleOffset, 0.125, 0.25);
    }

    @Test
    public void testCompareAndSet() {
        testGraph("unsafeCompareAndSetBoolean");
        testGraph("unsafeCompareAndSetByte");
        testGraph("unsafeCompareAndSetByteWithIntArgs");
        testGraph("unsafeCompareAndSetChar");
        testGraph("unsafeCompareAndSetCharWithIntArgs");
        testGraph("unsafeCompareAndSetShort");
        testGraph("unsafeCompareAndSetShortWithIntArgs");
        testGraph("unsafeCompareAndSetInt");
        testGraph("unsafeCompareAndSetLong");
        testGraph("unsafeCompareAndSetFloat");
        testGraph("unsafeCompareAndSetDouble");

        test("unsafeCompareAndSetBoolean");
        test("unsafeCompareAndSetByte");
        test("unsafeCompareAndSetByteWithIntArgs", -17, 121);
        test("unsafeCompareAndSetChar");
        test("unsafeCompareAndSetCharWithIntArgs", 1025, 1777);
        test("unsafeCompareAndSetShort");
        test("unsafeCompareAndSetShortWithIntArgs", -2232, 12111);
        test("unsafeCompareAndSetInt");
        test("unsafeCompareAndSetLong");
        test("unsafeCompareAndSetFloat");
        test("unsafeCompareAndSetDouble");
    }

    public static Boolean unsafeCompareAndSetFloatVar(Container c) {
        return unsafe.compareAndSetFloat(c, floatOffset, 1.0f, 2.0f);
    }

    @Test
    public void testUnsafeCompareAndSetFloatVar() {
        test("unsafeCompareAndSetFloatVar", new Container());
    }

    public static boolean unsafeWeakCompareAndSetBoolean() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetBoolean(container, booleanOffset, false, true);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetBooleanAcquire() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetBooleanAcquire(container, booleanOffset, false, true);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetBooleanPlain() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetBooleanPlain(container, booleanOffset, false, true);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetBooleanRelease() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            unsafe.weakCompareAndSetBooleanRelease(container, booleanOffset, false, true);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetByte() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetByte(container, byteOffset, (byte) -17, (byte) 121);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetByteAcquire() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetByteAcquire(container, byteOffset, (byte) -17, (byte) 121);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetBytePlain() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetBytePlain(container, byteOffset, (byte) -17, (byte) 121);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetByteRelease() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetByteRelease(container, byteOffset, (byte) -17, (byte) 121);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetChar() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetChar(container, charOffset, (char) 1025, (char) 1777);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetCharAcquire() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetCharAcquire(container, charOffset, (char) 1025, (char) 1777);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetCharPlain() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetCharPlain(container, charOffset, (char) 1025, (char) 1777);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetCharRelease() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetCharRelease(container, charOffset, (char) 1025, (char) 1777);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetShort() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetShort(container, shortOffset, (short) -2232, (short) 12111);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetShortAcquire() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetShortAcquire(container, shortOffset, (short) -2232, (short) 12111);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetShortPlain() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetShortPlain(container, shortOffset, (short) -2232, (short) 12111);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetShortRelease() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetShortRelease(container, shortOffset, (short) -2232, (short) 12111);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetInt() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetInt(container, intOffset, 0xcafebabe, 0xbabefafa);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetIntAcquire() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetIntAcquire(container, intOffset, 0xcafebabe, 0xbabefafa);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetIntPlain() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetIntPlain(container, intOffset, 0xcafebabe, 0xbabefafa);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetIntRelease() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetIntRelease(container, intOffset, 0xcafebabe, 0xbabefafa);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetLong() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetLong(container, longOffset, 0xdedababafafaL, 0xfafacecafafadedaL);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetLongAcquire() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetLongAcquire(container, longOffset, 0xdedababafafaL, 0xfafacecafafadedaL);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetLongPlain() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetLongPlain(container, longOffset, 0xdedababafafaL, 0xfafacecafafadedaL);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetLongRelease() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetLongRelease(container, longOffset, 0xdedababafafaL, 0xfafacecafafadedaL);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetFloat() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetFloat(container, floatOffset, 0.125f, 0.25f);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetFloatAcquire() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetFloatAcquire(container, floatOffset, 0.125f, 0.25f);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetFloatPlain() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetFloatPlain(container, floatOffset, 0.125f, 0.25f);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetFloatRelease() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetFloatRelease(container, floatOffset, 0.125f, 0.25f);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetDouble() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetDouble(container, doubleOffset, 0.125, 0.25);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetDoubleAcquire() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetDoubleAcquire(container, doubleOffset, 0.125, 0.25);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetDoublePlain() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetDoublePlain(container, doubleOffset, 0.125, 0.25);
        }
        return success;
    }

    public static boolean unsafeWeakCompareAndSetDoubleRelease() {
        Container container = new Container();
        boolean success = false;
        for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
            success = unsafe.weakCompareAndSetDoubleRelease(container, doubleOffset, 0.125, 0.25);
        }
        return success;
    }

    @Test
    public void testWeakCompareAndSet() {
        testGraph("unsafeWeakCompareAndSetBoolean");
        testGraph("unsafeWeakCompareAndSetBooleanAcquire");
        testGraph("unsafeWeakCompareAndSetBooleanPlain");
        testGraph("unsafeWeakCompareAndSetBooleanRelease");
        testGraph("unsafeWeakCompareAndSetByte");
        testGraph("unsafeWeakCompareAndSetByteAcquire");
        testGraph("unsafeWeakCompareAndSetBytePlain");
        testGraph("unsafeWeakCompareAndSetByteRelease");
        testGraph("unsafeWeakCompareAndSetChar");
        testGraph("unsafeWeakCompareAndSetCharAcquire");
        testGraph("unsafeWeakCompareAndSetCharPlain");
        testGraph("unsafeWeakCompareAndSetCharRelease");
        testGraph("unsafeWeakCompareAndSetShort");
        testGraph("unsafeWeakCompareAndSetShortAcquire");
        testGraph("unsafeWeakCompareAndSetShortPlain");
        testGraph("unsafeWeakCompareAndSetShortRelease");
        testGraph("unsafeWeakCompareAndSetInt");
        testGraph("unsafeWeakCompareAndSetIntAcquire");
        testGraph("unsafeWeakCompareAndSetIntPlain");
        testGraph("unsafeWeakCompareAndSetIntRelease");
        testGraph("unsafeWeakCompareAndSetLong");
        testGraph("unsafeWeakCompareAndSetLongAcquire");
        testGraph("unsafeWeakCompareAndSetLongPlain");
        testGraph("unsafeWeakCompareAndSetLongRelease");
        testGraph("unsafeWeakCompareAndSetFloat");
        testGraph("unsafeWeakCompareAndSetFloatAcquire");
        testGraph("unsafeWeakCompareAndSetFloatPlain");
        testGraph("unsafeWeakCompareAndSetFloatRelease");
        testGraph("unsafeWeakCompareAndSetDouble");
        testGraph("unsafeWeakCompareAndSetDoubleAcquire");
        testGraph("unsafeWeakCompareAndSetDoublePlain");
        testGraph("unsafeWeakCompareAndSetDoubleRelease");

        test("unsafeWeakCompareAndSetFloat");
        test("unsafeWeakCompareAndSetFloatAcquire");
        test("unsafeWeakCompareAndSetFloatPlain");
        test("unsafeWeakCompareAndSetFloatRelease");
        test("unsafeWeakCompareAndSetDouble");
        test("unsafeWeakCompareAndSetDoubleAcquire");
        test("unsafeWeakCompareAndSetDoublePlain");
        test("unsafeWeakCompareAndSetDoubleRelease");
        test("unsafeWeakCompareAndSetBoolean");
        test("unsafeWeakCompareAndSetBooleanAcquire");
        test("unsafeWeakCompareAndSetBooleanPlain");
        test("unsafeWeakCompareAndSetBooleanRelease");
        test("unsafeWeakCompareAndSetByte");
        test("unsafeWeakCompareAndSetByteAcquire");
        test("unsafeWeakCompareAndSetBytePlain");
        test("unsafeWeakCompareAndSetByteRelease");
        test("unsafeWeakCompareAndSetChar");
        test("unsafeWeakCompareAndSetCharAcquire");
        test("unsafeWeakCompareAndSetCharPlain");
        test("unsafeWeakCompareAndSetCharRelease");
        test("unsafeWeakCompareAndSetShort");
        test("unsafeWeakCompareAndSetShortAcquire");
        test("unsafeWeakCompareAndSetShortPlain");
        test("unsafeWeakCompareAndSetShortRelease");
        test("unsafeWeakCompareAndSetInt");
        test("unsafeWeakCompareAndSetIntAcquire");
        test("unsafeWeakCompareAndSetIntPlain");
        test("unsafeWeakCompareAndSetIntRelease");
        test("unsafeWeakCompareAndSetLong");
        test("unsafeWeakCompareAndSetLongAcquire");
        test("unsafeWeakCompareAndSetLongPlain");
        test("unsafeWeakCompareAndSetLongRelease");
    }

    public static boolean unsafeCompareAndExchangeBoolean() {
        Container container = new Container();
        return unsafe.compareAndExchangeBoolean(container, booleanOffset, false, true);
    }

    public static boolean unsafeCompareAndExchangeBooleanAcquire() {
        Container container = new Container();
        return unsafe.compareAndExchangeBooleanAcquire(container, booleanOffset, false, true);
    }

    public static boolean unsafeCompareAndExchangeBooleanRelease() {
        Container container = new Container();
        return unsafe.compareAndExchangeBooleanRelease(container, booleanOffset, false, true);
    }

    public static byte unsafeCompareAndExchangeByte() {
        Container container = new Container();
        return unsafe.compareAndExchangeByte(container, byteOffset, (byte) -17, (byte) 31);
    }

    public static int unsafeCompareAndExchangeByteAcquire() {
        Container container = new Container();
        return unsafe.compareAndExchangeByteAcquire(container, byteOffset, (byte) -17, (byte) 31);
    }

    public static int unsafeCompareAndExchangeByteRelease() {
        Container container = new Container();
        return unsafe.compareAndExchangeByteRelease(container, byteOffset, (byte) -17, (byte) 31);
    }

    public static char unsafeCompareAndExchangeChar() {
        Container container = new Container();
        return unsafe.compareAndExchangeChar(container, charOffset, (char) 1025, (char) 4502);
    }

    public static int unsafeCompareAndExchangeCharAcquire() {
        Container container = new Container();
        return unsafe.compareAndExchangeCharAcquire(container, charOffset, (char) 1025, (char) 4502);
    }

    public static int unsafeCompareAndExchangeCharRelease() {
        Container container = new Container();
        return unsafe.compareAndExchangeCharRelease(container, charOffset, (char) 1025, (char) 4502);
    }

    public static short unsafeCompareAndExchangeShort() {
        Container container = new Container();
        return unsafe.compareAndExchangeShort(container, shortOffset, (short) -2232, (short) 8121);
    }

    public static int unsafeCompareAndExchangeShortAcquire() {
        Container container = new Container();
        return unsafe.compareAndExchangeShortAcquire(container, shortOffset, (short) -2232, (short) 8121);
    }

    public static int unsafeCompareAndExchangeShortRelease() {
        Container container = new Container();
        return unsafe.compareAndExchangeShortRelease(container, shortOffset, (short) -2232, (short) 8121);
    }

    public static int unsafeCompareAndExchangeInt() {
        Container container = new Container();
        return unsafe.compareAndExchangeInt(container, intOffset, 0xcafebabe, 0xbabefafa);
    }

    public static int unsafeCompareAndExchangeIntAcquire() {
        Container container = new Container();
        return unsafe.compareAndExchangeIntAcquire(container, intOffset, 0xcafebabe, 0xbabefafa);
    }

    public static int unsafeCompareAndExchangeIntRelease() {
        Container container = new Container();
        return unsafe.compareAndExchangeIntRelease(container, intOffset, 0xcafebabe, 0xbabefafa);
    }

    public static long unsafeCompareAndExchangeLong() {
        Container container = new Container();
        return unsafe.compareAndExchangeLong(container, longOffset, 0xdedababafafaL, 0xfafacecafafadedaL);
    }

    public static long unsafeCompareAndExchangeLongAcquire() {
        Container container = new Container();
        return unsafe.compareAndExchangeLongAcquire(container, longOffset, 0xdedababafafaL, 0xfafacecafafadedaL);
    }

    public static long unsafeCompareAndExchangeLongRelease() {
        Container container = new Container();
        return unsafe.compareAndExchangeLongRelease(container, longOffset, 0xdedababafafaL, 0xfafacecafafadedaL);
    }

    public static float unsafeCompareAndExchangeFloat() {
        Container container = new Container();
        return unsafe.compareAndExchangeFloat(container, floatOffset, 0.125f, 0.25f);
    }

    public static float unsafeCompareAndExchangeFloatAcquire() {
        Container container = new Container();
        return unsafe.compareAndExchangeFloatAcquire(container, floatOffset, 0.125f, 0.25f);
    }

    public static float unsafeCompareAndExchangeFloatRelease() {
        Container container = new Container();
        return unsafe.compareAndExchangeFloatRelease(container, floatOffset, 0.125f, 0.25f);
    }

    public static double unsafeCompareAndExchangeDouble() {
        Container container = new Container();
        return unsafe.compareAndExchangeDouble(container, doubleOffset, 0.125, 0.25);
    }

    public static double unsafeCompareAndExchangeDoubleAcquire() {
        Container container = new Container();
        return unsafe.compareAndExchangeDoubleAcquire(container, doubleOffset, 0.125, 0.25);
    }

    public static double unsafeCompareAndExchangeDoubleRelease() {
        Container container = new Container();
        return unsafe.compareAndExchangeDoubleRelease(container, doubleOffset, 0.125, 0.25);
    }

    @Test
    public void testCompareAndExchange() {
        testGraph("unsafeCompareAndExchangeBoolean");
        testGraph("unsafeCompareAndExchangeBooleanAcquire");
        testGraph("unsafeCompareAndExchangeBooleanRelease");
        testGraph("unsafeCompareAndExchangeByte");
        testGraph("unsafeCompareAndExchangeByteAcquire");
        testGraph("unsafeCompareAndExchangeByteRelease");
        testGraph("unsafeCompareAndExchangeChar");
        testGraph("unsafeCompareAndExchangeCharAcquire");
        testGraph("unsafeCompareAndExchangeCharRelease");
        testGraph("unsafeCompareAndExchangeShort");
        testGraph("unsafeCompareAndExchangeShortAcquire");
        testGraph("unsafeCompareAndExchangeShortRelease");
        testGraph("unsafeCompareAndExchangeInt");
        testGraph("unsafeCompareAndExchangeIntAcquire");
        testGraph("unsafeCompareAndExchangeIntRelease");
        testGraph("unsafeCompareAndExchangeLong");
        testGraph("unsafeCompareAndExchangeLongAcquire");
        testGraph("unsafeCompareAndExchangeLongRelease");
        testGraph("unsafeCompareAndExchangeFloat");
        testGraph("unsafeCompareAndExchangeFloatAcquire");
        testGraph("unsafeCompareAndExchangeFloatRelease");
        testGraph("unsafeCompareAndExchangeDouble");
        testGraph("unsafeCompareAndExchangeDoubleAcquire");
        testGraph("unsafeCompareAndExchangeDoubleRelease");

        test("unsafeCompareAndExchangeBoolean");
        test("unsafeCompareAndExchangeBooleanAcquire");
        test("unsafeCompareAndExchangeBooleanRelease");
        test("unsafeCompareAndExchangeByte");
        test("unsafeCompareAndExchangeByteAcquire");
        test("unsafeCompareAndExchangeByteRelease");
        test("unsafeCompareAndExchangeChar");
        test("unsafeCompareAndExchangeCharAcquire");
        test("unsafeCompareAndExchangeCharRelease");
        test("unsafeCompareAndExchangeShort");
        test("unsafeCompareAndExchangeShortAcquire");
        test("unsafeCompareAndExchangeShortRelease");
        test("unsafeCompareAndExchangeInt");
        test("unsafeCompareAndExchangeIntAcquire");
        test("unsafeCompareAndExchangeIntRelease");
        test("unsafeCompareAndExchangeLong");
        test("unsafeCompareAndExchangeLongAcquire");
        test("unsafeCompareAndExchangeLongRelease");
        test("unsafeCompareAndExchangeFloat");
        test("unsafeCompareAndExchangeFloatAcquire");
        test("unsafeCompareAndExchangeFloatRelease");
        test("unsafeCompareAndExchangeDouble");
        test("unsafeCompareAndExchangeDoubleAcquire");
        test("unsafeCompareAndExchangeDoubleRelease");
    }

    public static int unsafeGetAndAddByte() {
        Container container = new Container();
        return unsafe.getAndAddByte(container, byteOffset, (byte) 2);
    }

    public static int unsafeGetAndAddBytePlusOne() {
        Container container = new Container();
        int value = unsafe.getAndAddByte(container, byteOffset, (byte) 2);
        return value + 1;
    }

    public static int unsafeGetAndAddChar() {
        Container container = new Container();
        return unsafe.getAndAddChar(container, charOffset, (char) 250);
    }

    public static int unsafeGetAndAddShort() {
        Container container = new Container();
        return unsafe.getAndAddShort(container, shortOffset, (short) 1250);
    }

    public static int unsafeGetAndAddInt() {
        Container container = new Container();
        return unsafe.getAndAddInt(container, intOffset, 104501);
    }

    public static long unsafeGetAndAddLong() {
        Container container = new Container();
        return unsafe.getAndAddLong(container, longOffset, 0x123456abcdL);
    }

    @Test
    public void testGetAndAdd() {
        testGraph("unsafeGetAndAddByte");
        testGraph("unsafeGetAndAddChar");
        testGraph("unsafeGetAndAddShort");
        testGraph("unsafeGetAndAddInt");
        testGraph("unsafeGetAndAddLong");

        test("unsafeGetAndAddByte");
        test("unsafeGetAndAddBytePlusOne");
        test("unsafeGetAndAddChar");
        test("unsafeGetAndAddShort");
        test("unsafeGetAndAddInt");
        test("unsafeGetAndAddLong");
    }

    public static boolean unsafeGetAndSetBoolean() {
        Container container = new Container();
        return unsafe.getAndSetBoolean(container, booleanOffset, true);
    }

    public static byte unsafeGetAndSetByte() {
        Container container = new Container();
        return unsafe.getAndSetByte(container, byteOffset, (byte) 129);
    }

    public static int unsafeGetAndSetBytePlusOne() {
        Container container = new Container();
        int value = unsafe.getAndSetByte(container, byteOffset, (byte) 129);
        return value + 1;
    }

    public static char unsafeGetAndSetChar() {
        Container container = new Container();
        return unsafe.getAndSetChar(container, charOffset, (char) 21111);
    }

    public static short unsafeGetAndSetShort() {
        Container container = new Container();
        return unsafe.getAndSetShort(container, shortOffset, (short) 21111);
    }

    public static int unsafeGetAndSetInt() {
        Container container = new Container();
        return unsafe.getAndSetInt(container, intOffset, 0x1234af);
    }

    public static long unsafeGetAndSetLong() {
        Container container = new Container();
        return unsafe.getAndSetLong(container, longOffset, 0x12345678abL);
    }

    @Test
    public void testGetAndSet() {
        testGraph("unsafeGetAndSetBoolean");
        testGraph("unsafeGetAndSetByte");
        testGraph("unsafeGetAndSetChar");
        testGraph("unsafeGetAndSetShort");
        testGraph("unsafeGetAndSetInt");
        testGraph("unsafeGetAndSetLong");

        test("unsafeGetAndSetBoolean");
        test("unsafeGetAndSetByte");
        test("unsafeGetAndSetBytePlusOne");
        test("unsafeGetAndSetChar");
        test("unsafeGetAndSetShort");
        test("unsafeGetAndSetInt");
        test("unsafeGetAndSetLong");
    }

    public static void assertEquals(Object seen, Object expected, String message) {
        if (seen != expected) {
            throw new AssertionError(message + " - seen: " + seen + ", expected: " + expected);
        }
    }

    public static boolean unsafeGetPutBoolean() {
        Container container = new Container();
        unsafe.putBoolean(container, booleanOffset, true);
        return unsafe.getBoolean(container, booleanOffset);
    }

    public static byte unsafeGetPutByte() {
        Container container = new Container();
        unsafe.putByte(container, byteOffset, (byte) 0x12);
        return unsafe.getByte(container, byteOffset);
    }

    public static short unsafeGetPutShort() {
        Container container = new Container();
        unsafe.putShort(container, shortOffset, (short) 0x1234);
        return unsafe.getShort(container, shortOffset);
    }

    public static char unsafeGetPutChar() {
        Container container = new Container();
        unsafe.putChar(container, charOffset, 'x');
        return unsafe.getChar(container, charOffset);
    }

    public static int unsafeGetPutInt() {
        Container container = new Container();
        unsafe.putInt(container, intOffset, 0x01234567);
        return unsafe.getInt(container, intOffset);
    }

    public static long unsafeGetPutLong() {
        Container container = new Container();
        unsafe.putLong(container, longOffset, 0x01234567890ABCDEFL);
        return unsafe.getLong(container, longOffset);
    }

    public static float unsafeGetPutFloat() {
        Container container = new Container();
        unsafe.putFloat(container, floatOffset, 1.234F);
        return unsafe.getFloat(container, floatOffset);
    }

    public static double unsafeGetPutDouble() {
        Container container = new Container();
        unsafe.putDouble(container, doubleOffset, 1.23456789);
        return unsafe.getDouble(container, doubleOffset);
    }

    public static boolean unsafeGetPutBooleanOpaque() {
        Container container = new Container();
        unsafe.putBooleanOpaque(container, booleanOffset, true);
        return unsafe.getBooleanOpaque(container, booleanOffset);
    }

    public static byte unsafeGetPutByteOpaque() {
        Container container = new Container();
        unsafe.putByteOpaque(container, byteOffset, (byte) 0x12);
        return unsafe.getByteOpaque(container, byteOffset);
    }

    public static short unsafeGetPutShortOpaque() {
        Container container = new Container();
        unsafe.putShortOpaque(container, shortOffset, (short) 0x1234);
        return unsafe.getShortOpaque(container, shortOffset);
    }

    public static char unsafeGetPutCharOpaque() {
        Container container = new Container();
        unsafe.putCharOpaque(container, charOffset, 'x');
        return unsafe.getCharOpaque(container, charOffset);
    }

    public static int unsafeGetPutIntOpaque() {
        Container container = new Container();
        unsafe.putIntOpaque(container, intOffset, 0x01234567);
        return unsafe.getIntOpaque(container, intOffset);
    }

    public static long unsafeGetPutLongOpaque() {
        Container container = new Container();
        unsafe.putLongOpaque(container, longOffset, 0x01234567890ABCDEFL);
        return unsafe.getLongOpaque(container, longOffset);
    }

    public static float unsafeGetPutFloatOpaque() {
        Container container = new Container();
        unsafe.putFloatOpaque(container, floatOffset, 1.234F);
        return unsafe.getFloatOpaque(container, floatOffset);
    }

    public static double unsafeGetPutDoubleOpaque() {
        Container container = new Container();
        unsafe.putDoubleOpaque(container, doubleOffset, 1.23456789);
        return unsafe.getDoubleOpaque(container, doubleOffset);
    }

    public static boolean unsafeGetPutBooleanRA() {
        Container container = new Container();
        unsafe.putBooleanRelease(container, booleanOffset, true);
        return unsafe.getBooleanAcquire(container, booleanOffset);
    }

    public static byte unsafeGetPutByteRA() {
        Container container = new Container();
        unsafe.putByteRelease(container, byteOffset, (byte) 0x12);
        return unsafe.getByteAcquire(container, byteOffset);
    }

    public static short unsafeGetPutShortRA() {
        Container container = new Container();
        unsafe.putShortRelease(container, shortOffset, (short) 0x1234);
        return unsafe.getShortAcquire(container, shortOffset);
    }

    public static char unsafeGetPutCharRA() {
        Container container = new Container();
        unsafe.putCharRelease(container, charOffset, 'x');
        return unsafe.getCharAcquire(container, charOffset);
    }

    public static int unsafeGetPutIntRA() {
        Container container = new Container();
        unsafe.putIntRelease(container, intOffset, 0x01234567);
        return unsafe.getIntAcquire(container, intOffset);
    }

    public static long unsafeGetPutLongRA() {
        Container container = new Container();
        unsafe.putLongRelease(container, longOffset, 0x01234567890ABCDEFL);
        return unsafe.getLongAcquire(container, longOffset);
    }

    public static float unsafeGetPutFloatRA() {
        Container container = new Container();
        unsafe.putFloatRelease(container, floatOffset, 1.234F);
        return unsafe.getFloatAcquire(container, floatOffset);
    }

    public static double unsafeGetPutDoubleRA() {
        Container container = new Container();
        unsafe.putDoubleRelease(container, doubleOffset, 1.23456789);
        return unsafe.getDoubleAcquire(container, doubleOffset);
    }

    public static boolean unsafeGetPutBooleanVolatile() {
        Container container = new Container();
        unsafe.putBooleanVolatile(container, booleanOffset, true);
        return unsafe.getBooleanVolatile(container, booleanOffset);
    }

    public static byte unsafeGetPutByteVolatile() {
        Container container = new Container();
        unsafe.putByteVolatile(container, byteOffset, (byte) 0x12);
        return unsafe.getByteVolatile(container, byteOffset);
    }

    public static short unsafeGetPutShortVolatile() {
        Container container = new Container();
        unsafe.putShortVolatile(container, shortOffset, (short) 0x1234);
        return unsafe.getShortVolatile(container, shortOffset);
    }

    public static char unsafeGetPutCharVolatile() {
        Container container = new Container();
        unsafe.putCharVolatile(container, charOffset, 'x');
        return unsafe.getCharVolatile(container, charOffset);
    }

    public static int unsafeGetPutIntVolatile() {
        Container container = new Container();
        unsafe.putIntVolatile(container, intOffset, 0x01234567);
        return unsafe.getIntVolatile(container, intOffset);
    }

    public static long unsafeGetPutLongVolatile() {
        Container container = new Container();
        unsafe.putLongVolatile(container, longOffset, 0x01234567890ABCDEFL);
        return unsafe.getLongVolatile(container, longOffset);
    }

    public static float unsafeGetPutFloatVolatile() {
        Container container = new Container();
        unsafe.putFloatVolatile(container, floatOffset, 1.234F);
        return unsafe.getFloatVolatile(container, floatOffset);
    }

    public static double unsafeGetPutDoubleVolatile() {
        Container container = new Container();
        unsafe.putDoubleVolatile(container, doubleOffset, 1.23456789);
        return unsafe.getDoubleVolatile(container, doubleOffset);
    }

    public static short unsafeGetPutShortUnaligned() {
        Container container = new Container();
        unsafe.putShortUnaligned(container.byteArrayField, byteArrayBaseOffset + 1, (short) 0x1234);
        return unsafe.getShortUnaligned(container.byteArrayField, byteArrayBaseOffset + 1);
    }

    public static char unsafeGetPutCharUnaligned() {
        Container container = new Container();
        unsafe.putCharUnaligned(container.byteArrayField, byteArrayBaseOffset + 1, 'x');
        return unsafe.getCharUnaligned(container.byteArrayField, byteArrayBaseOffset + 1);
    }

    public static int unsafeGetPutIntUnaligned() {
        Container container = new Container();
        unsafe.putIntUnaligned(container.byteArrayField, byteArrayBaseOffset + 1, 0x01234567);
        unsafe.putIntUnaligned(container.byteArrayField, byteArrayBaseOffset + 3, 0x01234567);
        return unsafe.getIntUnaligned(container.byteArrayField, byteArrayBaseOffset + 1) +
                        unsafe.getIntUnaligned(container.byteArrayField, byteArrayBaseOffset + 3);
    }

    public static long unsafeGetPutLongUnaligned() {
        Container container = new Container();
        unsafe.putLongUnaligned(container.byteArrayField, byteArrayBaseOffset + 1, 0x01234567890ABCDEFL);
        unsafe.putLongUnaligned(container.byteArrayField, byteArrayBaseOffset + 3, 0x01234567890ABCDEFL);
        unsafe.putLongUnaligned(container.byteArrayField, byteArrayBaseOffset + 7, 0x01234567890ABCDEFL);
        return unsafe.getLongUnaligned(container.byteArrayField, byteArrayBaseOffset + 1) +
                        unsafe.getLongUnaligned(container.byteArrayField, byteArrayBaseOffset + 3) +
                        unsafe.getLongUnaligned(container.byteArrayField, byteArrayBaseOffset + 7);
    }

    @Test
    public void testUnsafeGetPutPlain() {
        testGraph("unsafeGetPutBoolean");
        testGraph("unsafeGetPutByte");
        testGraph("unsafeGetPutShort");
        testGraph("unsafeGetPutChar");
        testGraph("unsafeGetPutInt");
        testGraph("unsafeGetPutLong");
        testGraph("unsafeGetPutFloat");
        testGraph("unsafeGetPutDouble");

        test("unsafeGetPutBoolean");
        test("unsafeGetPutByte");
        test("unsafeGetPutShort");
        test("unsafeGetPutChar");
        test("unsafeGetPutInt");
        test("unsafeGetPutLong");
        test("unsafeGetPutFloat");
        test("unsafeGetPutDouble");
    }

    @Test
    public void testUnsafeGetPutOpaque() {
        testGraph("unsafeGetPutBooleanOpaque");
        testGraph("unsafeGetPutByteOpaque");
        testGraph("unsafeGetPutShortOpaque");
        testGraph("unsafeGetPutCharOpaque");
        testGraph("unsafeGetPutIntOpaque");
        testGraph("unsafeGetPutLongOpaque");
        testGraph("unsafeGetPutFloatOpaque");
        testGraph("unsafeGetPutDoubleOpaque");

        test("unsafeGetPutBooleanOpaque");
        test("unsafeGetPutByteOpaque");
        test("unsafeGetPutShortOpaque");
        test("unsafeGetPutCharOpaque");
        test("unsafeGetPutIntOpaque");
        test("unsafeGetPutLongOpaque");
        test("unsafeGetPutFloatOpaque");
        test("unsafeGetPutDoubleOpaque");
    }

    @Test
    public void testUnsafeGetPutReleaseAcquire() {
        testGraph("unsafeGetPutBooleanRA");
        testGraph("unsafeGetPutByteRA");
        testGraph("unsafeGetPutShortRA");
        testGraph("unsafeGetPutCharRA");
        testGraph("unsafeGetPutIntRA");
        testGraph("unsafeGetPutLongRA");
        testGraph("unsafeGetPutFloatRA");
        testGraph("unsafeGetPutDoubleRA");

        test("unsafeGetPutBooleanRA");
        test("unsafeGetPutByteRA");
        test("unsafeGetPutShortRA");
        test("unsafeGetPutCharRA");
        test("unsafeGetPutIntRA");
        test("unsafeGetPutLongRA");
        test("unsafeGetPutFloatRA");
        test("unsafeGetPutDoubleRA");
    }

    @Test
    public void testUnsafeGetPutVolatile() {
        testGraph("unsafeGetPutBooleanVolatile");
        testGraph("unsafeGetPutByteVolatile");
        testGraph("unsafeGetPutShortVolatile");
        testGraph("unsafeGetPutCharVolatile");
        testGraph("unsafeGetPutIntVolatile");
        testGraph("unsafeGetPutLongVolatile");
        testGraph("unsafeGetPutFloatVolatile");
        testGraph("unsafeGetPutDoubleVolatile");

        test("unsafeGetPutBooleanVolatile");
        test("unsafeGetPutByteVolatile");
        test("unsafeGetPutShortVolatile");
        test("unsafeGetPutCharVolatile");
        test("unsafeGetPutIntVolatile");
        test("unsafeGetPutLongVolatile");
        test("unsafeGetPutFloatVolatile");
        test("unsafeGetPutDoubleVolatile");
    }

    @Test
    public void testUnsafeGetPutUnaligned() {
        testGraph("unsafeGetPutShortUnaligned");
        testGraph("unsafeGetPutCharUnaligned");
        testGraph("unsafeGetPutIntUnaligned");
        testGraph("unsafeGetPutLongUnaligned");

        test("unsafeGetPutShortUnaligned");
        test("unsafeGetPutCharUnaligned");
        test("unsafeGetPutIntUnaligned");
        test("unsafeGetPutLongUnaligned");
    }
}
