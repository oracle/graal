/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.test.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;

/**
 * Tests the implementation of checkcast, allowing profiling information to be manually specified.
 */
public class CheckCastTest extends TypeCheckTest {

    @Override
    protected void replaceProfile(StructuredGraph graph, JavaTypeProfile profile) {
        CheckCastNode ccn = graph.getNodes().filter(CheckCastNode.class).first();
        if (ccn != null) {
            CheckCastNode ccnNew = graph.add(new CheckCastNode(ccn.type(), ccn.object(), profile, false));
            graph.replaceFixedWithFixed(ccn, ccnNew);
        }
    }

    @LongTest
    public void test1() {
        test("asNumber", profile(), 111);
        test("asNumber", profile(Integer.class), 111);
        test("asNumber", profile(Long.class, Short.class), 111);
        test("asNumberExt", profile(), 111);
        test("asNumberExt", profile(Integer.class), 111);
        test("asNumberExt", profile(Long.class, Short.class), 111);
    }

    @LongTest
    public void test2() {
        test("asString", profile(), "111");
        test("asString", profile(String.class), "111");
        test("asString", profile(String.class), "111");

        final String nullString = null;
        test("asString", profile(), nullString);
        test("asString", profile(String.class), nullString);
        test("asString", profile(String.class), nullString);

        test("asStringExt", profile(), "111");
        test("asStringExt", profile(String.class), "111");
        test("asStringExt", profile(String.class), "111");
    }

    @LongTest
    public void test3() {
        test("asNumber", profile(), "111");
    }

    @LongTest
    public void test4() {
        test("asString", profile(String.class), 111);
    }

    @LongTest
    public void test5() {
        test("asNumberExt", profile(), "111");
    }

    @LongTest
    public void test6() {
        test("asStringExt", profile(String.class), 111);
    }

    @LongTest
    public void test7() {
        Throwable throwable = new Exception();
        test("asThrowable", profile(), throwable);
        test("asThrowable", profile(Throwable.class), throwable);
        test("asThrowable", profile(Exception.class, Error.class), throwable);
    }

    @LongTest
    public void test8() {
        test("arrayStore", new Object[100], "111");
    }

    @LongTest
    public void test801() {
        test("arrayFill", new Object[100], "111");
    }

    public static Number asNumber(Object o) {
        return (Number) o;
    }

    public static String asString(Object o) {
        return (String) o;
    }

    public static Throwable asThrowable(Object o) {
        return (Throwable) o;
    }

    public static ValueNode asValueNode(Object o) {
        return (ValueNode) o;
    }

    public static Number asNumberExt(Object o) {
        Number n = (Number) o;
        return n.intValue() + 10;
    }

    public static String asStringExt(Object o) {
        String s = (String) o;
        return "#" + s;
    }

    public static Object[] arrayStore(Object[] arr, Object value) {
        arr[15] = value;
        return arr;
    }

    public static Object[] arrayFill(Object[] arr, Object value) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = value;
        }
        return arr;
    }

    static class Depth1 implements Cloneable {
    }

    static class Depth2 extends Depth1 {
    }

    static class Depth3 extends Depth2 {
    }

    static class Depth4 extends Depth3 {
    }

    static class Depth5 extends Depth4 {
    }

    static class Depth6 extends Depth5 {
    }

    static class Depth7 extends Depth6 {
    }

    static class Depth8 extends Depth7 {
    }

    static class Depth9 extends Depth8 {
    }

    static class Depth10 extends Depth9 {
    }

    static class Depth11 extends Depth10 {
    }

    static class Depth12 extends Depth11 {
    }

    static class Depth13 extends Depth12 {
    }

    static class Depth14 extends Depth12 {
    }

    public static Depth12 asDepth12(Object o) {
        return (Depth12) o;
    }

    public static Depth12[][] asDepth12Arr(Object o) {
        return (Depth12[][]) o;
    }

    public static Cloneable asCloneable(Object o) {
        return (Cloneable) o;
    }

    @LongTest
    public void test9() {
        Object o = new Depth13();
        test("asDepth12", profile(), o);
        test("asDepth12", profile(Depth13.class), o);
        test("asDepth12", profile(Depth13.class, Depth14.class), o);
    }

    @LongTest
    public void test10() {
        Object o = new Depth13[3][];
        test("asDepth12Arr", o);
    }
}
