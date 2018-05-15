/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.jdk9;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.replacements.test.MethodSubstitutionTest;
import org.graalvm.compiler.test.AddExports;
import org.junit.Test;

@AddExports("java.base/jdk.internal.misc")
public class UnsafeReplacementsTest extends MethodSubstitutionTest  {

    // See GR-9819.
    @SuppressWarnings("unused")
    ResolvedJavaMethod method = null;

    static class Container {
        public volatile boolean booleanField;
        public volatile byte byteField = 17;
        public volatile char charField = 1025;
        public volatile short shortField = 2232;
        public volatile int intField = 101532;
        public volatile long longField = 0x0123456789abcdefL;
        public volatile float floatField = 0.125f;
        public volatile double doubleField = 0.125;
    }

    static jdk.internal.misc.Unsafe unsafe = jdk.internal.misc.Unsafe.getUnsafe();
    static long booleanOffset;
    static long byteOffset;
    static long charOffset;
    static long shortOffset;
    static long intOffset;
    static long longOffset;
    static long floatOffset;
    static long doubleOffset;

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
        } catch (NoSuchFieldException e) {
          throw new RuntimeException(e);
        }
    }

    public static boolean unsafeCompareAndSetBoolean() throws NoSuchFieldException {
        Container container = new Container();
        return unsafe.compareAndSetBoolean(container, booleanOffset, false, true);
    }

    public static boolean unsafeCompareAndSetByte() throws NoSuchFieldException {
        Container container = new Container();
        return unsafe.compareAndSetByte(container, booleanOffset, (byte) 17, (byte) 121);
    }

    public static boolean unsafeCompareAndSetChar() throws NoSuchFieldException {
        Container container = new Container();
        return unsafe.compareAndSetChar(container, charOffset, (char) 1025, (char) 1777);
    }

    public static boolean unsafeCompareAndSetShort() throws NoSuchFieldException {
        Container container = new Container();
        return unsafe.compareAndSetShort(container, shortOffset, (short) 2232, (short) 12111);
    }

    public static boolean unsafeCompareAndSetInt() throws NoSuchFieldException {
        Container container = new Container();
        return unsafe.compareAndSetInt(container, intOffset, 101532, 102123);
    }

    public static boolean unsafeCompareAndSetLong() throws NoSuchFieldException {
        Container container = new Container();
        return unsafe.compareAndSetLong(container, longOffset, 0x0123456789abcdefL, 0xfedcba9876543210L);
    }

    public static boolean unsafeCompareAndSetFloat() throws NoSuchFieldException {
        Container container = new Container();
        return unsafe.compareAndSetFloat(container, floatOffset, 0.125f, 0.25f);
    }

    public static boolean unsafeCompareAndSetDouble() throws NoSuchFieldException {
        Container container = new Container();
        return unsafe.compareAndSetDouble(container, doubleOffset, 0.125, 0.25);
    }

    @Test
    public void testCompareAndSet() {
        testGraph("unsafeCompareAndSetBoolean");
        test("unsafeCompareAndSetBoolean");
        testGraph("unsafeCompareAndSetByte");
        test("unsafeCompareAndSetByte");
        testGraph("unsafeCompareAndSetChar");
        test("unsafeCompareAndSetChar");
        testGraph("unsafeCompareAndSetShort");
        test("unsafeCompareAndSetShort");
        testGraph("unsafeCompareAndSetInt");
        test("unsafeCompareAndSetInt");
        testGraph("unsafeCompareAndSetLong");
        test("unsafeCompareAndSetLong");
        testGraph("unsafeCompareAndSetFloat");
        test("unsafeCompareAndSetFloat");
        testGraph("unsafeCompareAndSetDouble");
        test("unsafeCompareAndSetDouble");
    }
}
