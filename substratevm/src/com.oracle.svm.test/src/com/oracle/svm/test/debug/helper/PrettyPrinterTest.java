/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.debug.helper;

import com.oracle.svm.core.NeverInline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrettyPrinterTest {

    enum Day {
        Monday,
        Tuesday,
        Wednesday,
        Thursday,
        Friday,
        Saturday,
        Sunday
    }

    static class ExampleClass {
        public static String s1 = ident("test");

        public long f1;
        public int f2;
        public short f3;
        public char f4;
        public byte f5;
        public boolean f6;
        public String f7;
        public Day f8;
        public Object f9;
        public ExampleClass f10;

        ExampleClass(long f1, int f2, short f3, char f4, byte f5, boolean f6, String f7, Day f8, Object f9, ExampleClass f10) {
            super();
            this.f1 = f1;
            this.f2 = f2;
            this.f3 = f3;
            this.f4 = f4;
            this.f5 = f5;
            this.f6 = f6;
            this.f7 = f7;
            this.f8 = f8;
            this.f9 = f9;
            this.f10 = f10;
        }

        ExampleClass() {
            this(0, 1, (short) 2, '3', (byte) 4, false, "test string", Day.Monday, new Object(), null);
        }

        @Override
        public String toString() {
            return "ExampleClass{" +
                            "f1=" + f1 +
                            ", f2=" + f2 +
                            ", f3=" + f3 +
                            ", f4=" + f4 +
                            ", f5=" + f5 +
                            ", f6=" + f6 +
                            ", f7='" + f7 + '\'' +
                            ", f8=" + f8 +
                            ", f9=" + f9 +
                            ", f10=" + f10 +
                            ", s1=" + s1 +
                            '}';
        }

        @NeverInline("For testing purposes")
        private static String ident(String s) {
            return s;
        }
    }

    @SuppressWarnings("unused")
    @NeverInline("For testing purposes")
    static void testPrimitive(byte b, Byte bObj, short s, Short sObj, char c, Character cObj, int i, Integer iObj, long l, Long lObj,
                    float f, Float fObj, double d, Double dObj, boolean x, Boolean xObj) {
        System.out.print("");
    }

    @SuppressWarnings("unused")
    @NeverInline("For testing purposes")
    static void testString(String nullStr, String emptyStr, String str, String uStr1, String uStr2, String uStr3, String uStr4, String uStr5, String str0) {
        System.out.print("");
    }

    @SuppressWarnings("unused")
    @NeverInline("For testing purposes")
    static void testArray(int[] ia, Object[] oa, String[] sa) {
        System.out.print("");
    }

    @SuppressWarnings("unused")
    @NeverInline("For testing purposes")
    static void testObject(ExampleClass object, ExampleClass recObject) {
        System.out.print("");
    }

    @SuppressWarnings("unused")
    @NeverInline("For testing purposes")
    static void testArrayList(ArrayList<String> strList, List<Object> mixedList, ArrayList<Object> nullList) {
        System.out.print("");
    }

    @SuppressWarnings("unused")
    @NeverInline("For testing purposes")
    static void testHashMap(HashMap<String, String> strMap, Map<Object, Object> mixedMap) {
        System.out.print("");
    }

    static ExampleClass setupExampleObject(boolean recursive) {
        ExampleClass example = new ExampleClass();
        example.f10 = new ExampleClass(10, 20, (short) 30, '\40', (byte) 50, true, "60", Day.Sunday, new Object(), null);
        example.f9 = new ArrayList<>(List.of(example.f10, new ExampleClass()));
        if (recursive) {
            example.f10.f10 = example;  // indirect recursion
            example.f10 = example;      // direct recursion
        }
        return example;
    }

    public static void main(String[] args) {
        testPrimitive((byte) 1, (byte) 1, (short) 2, (short) 2, '3', '3', 4, 4, 5L, 5L,
                        6.125F, 6.125F, 7.25, 7.25, true, true);
        // Checkstyle: stop
        testString(null, "", "string", "Привет Java", "Բարեւ Java", "你好的 Java", "こんにちは Java", "𝄞и𝄞и𝄞и𝄞и𝄞", "first " + '\0' + "second");
        // Checkstyle: resume
        testArray(new int[]{0, 1, 2, 3}, new Object[]{0, "random", new Object(), new ArrayList<String>()}, new String[]{"this", "is", "a", "string", "array"});
        testObject(setupExampleObject(false), setupExampleObject(true));

        ArrayList<Object> nullList = new ArrayList<>();
        nullList.add(null);
        nullList.add(null);
        nullList.add(null);

        testArrayList(new ArrayList<>(List.of("this", "is", "a", "string", "list")), new ArrayList<>(List.of(1, 2L, "string")), nullList);
        testHashMap(new HashMap<>(Map.of("this", "one", "is", "two", "a", "three", "string", "four", "list", "five")),
                        new HashMap<>(Map.of(1, new ExampleClass(), 2L, "string", (byte) 3, new ArrayList<>())));
    }
}
