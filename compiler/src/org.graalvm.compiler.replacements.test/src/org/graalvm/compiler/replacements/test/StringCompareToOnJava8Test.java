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
package org.graalvm.compiler.replacements.test;

import java.util.TreeMap;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.junit.Test;

public class StringCompareToOnJava8Test extends MethodSubstitutionTest {

    private final String[] testData = new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};

    static int length;
    static int length2;

    static int l0 = 0;
    static int l1 = 1;
    static int l2 = 2;
    static int l3 = 3;
    static int l4 = 4;
    static int l5 = 5;
    static int l6 = 6;
    static int l7 = 7;
    static int l8 = 8;
    static int l9 = 9;
    static int lsum = 0;

    public static String getFromTreeMap(TreeMap<String, String> treeMap, String[] keys, String key) {
        l0 = keys[0].length();
        length = key.length();
        l1 = keys[1].length();
        l2 = keys[2].length();
        l3 = keys[3].length();
        l4 = keys[4].length();
        l5 = keys[5].length();
        l6 = keys[6].length();
        l7 = keys[7].length();
        l8 = keys[8].length();
        l9 = keys[9].length();
        // All these code are for making key.length() << 1 stored in ECX. It heavily relies on how register
        // allocator works. Might not be effective.
        length2 = length << 1;
        String s = treeMap.get(key);
        GraalDirectives.bindToRegister(l0);
        GraalDirectives.bindToRegister(l1);
        GraalDirectives.bindToRegister(l2);
        GraalDirectives.bindToRegister(l3);
        GraalDirectives.bindToRegister(l4);
        GraalDirectives.bindToRegister(l5);
        GraalDirectives.bindToRegister(l6);
        GraalDirectives.bindToRegister(l7);
        GraalDirectives.bindToRegister(l7);
        GraalDirectives.bindToRegister(l8);
        GraalDirectives.bindToRegister(l9);
        lsum = l0 + l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8 + l9;
        return s;
    }

    @Test
    public void testTreeMap() throws InterruptedException {
        // This reproduces a correctness issue
        TreeMap<String, String> treeMap = new TreeMap<>();
        treeMap.put("A", "A");
        treeMap.put("BB", "BB");
        for (int i = 0; i < 2000; i++) {
            getFromTreeMap(treeMap, testData, "BB");
            getFromTreeMap(treeMap, testData, "");
        }
        test("getFromTreeMap", treeMap, testData, new String("BB"));
    }

    @Test
    public void testTreeMapCrash() throws InterruptedException {
        // This reproduces a SIGSEGV
        TreeMap<String, String> treeMap = new TreeMap<>();
        treeMap.put("\u0001", "key");
        treeMap.put("\u0001A", "key1");
        for (int i = 0; i < 2000; i++) {
            getFromTreeMap(treeMap, testData, "BB");
            getFromTreeMap(treeMap, testData, "");
        }
        test("getFromTreeMap", treeMap, testData, "\u0001B");
    }

}
