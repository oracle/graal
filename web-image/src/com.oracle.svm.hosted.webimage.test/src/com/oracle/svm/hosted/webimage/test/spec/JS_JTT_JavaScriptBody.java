/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.test.spec;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.hosted.webimage.codegen.long64.Long64Lowerer;
import com.oracle.svm.hosted.webimage.test.util.JTTTestSuite;
import com.oracle.svm.webimage.jtt.javascriptbody.BoolArgTest;
import com.oracle.svm.webimage.jtt.javascriptbody.BoolReturnTest;
import com.oracle.svm.webimage.jtt.javascriptbody.ByteReturnTest;
import com.oracle.svm.webimage.jtt.javascriptbody.CallFromCallbackTest;
import com.oracle.svm.webimage.jtt.javascriptbody.CallbackStaticTest;
import com.oracle.svm.webimage.jtt.javascriptbody.CallbackTest;
import com.oracle.svm.webimage.jtt.javascriptbody.CovariantCallbackTest;
import com.oracle.svm.webimage.jtt.javascriptbody.DoubleReturnTest;
import com.oracle.svm.webimage.jtt.javascriptbody.DynamicCallTest;
import com.oracle.svm.webimage.jtt.javascriptbody.FloatReturnTest;
import com.oracle.svm.webimage.jtt.javascriptbody.InitInCallbackTest;
import com.oracle.svm.webimage.jtt.javascriptbody.JavaScriptBodyObjectTest;
import com.oracle.svm.webimage.jtt.javascriptbody.JavaScriptResourceTest;
import com.oracle.svm.webimage.jtt.javascriptbody.LongArgTest;
import com.oracle.svm.webimage.jtt.javascriptbody.LongReturnTest;
import com.oracle.svm.webimage.jtt.javascriptbody.NestedJSArrayTest;
import com.oracle.svm.webimage.jtt.javascriptbody.NestedJavaScriptBodyObjectTest;
import com.oracle.svm.webimage.jtt.javascriptbody.SimpleOperationTest;
import com.oracle.svm.webimage.jtt.javascriptbody.StaticCallTest;
import com.oracle.svm.webimage.jtt.javascriptbody.StringArgTest;
import com.oracle.svm.webimage.jtt.javascriptbody.StringReturnTest;
import com.oracle.svm.webimage.jtt.testdispatcher.JavaScriptBodyTests;

/**
 * Tests for the JavaScriptBody annotation.
 */
public class JS_JTT_JavaScriptBody extends JTTTestSuite {

    public static Class<?> testDispatcher = JavaScriptBodyTests.class;

    @BeforeClass
    public static void setupClass() {
        compileToJS(testDispatcher);
    }

    @Test
    public void staticCallTest() {
        testFileAgainstNoBuild(new String[]{"10"}, StaticCallTest.class.getName(), "10");
    }

    @Test
    public void dynamicCallTest() {
        testFileAgainstNoBuild(new String[]{"12"}, DynamicCallTest.class.getName(), "10", "2");
    }

    @Test
    public void callbackTest() {
        testFileAgainstNoBuild(new String[]{"42"}, CallbackTest.class.getName(), "7", "6");
    }

    @Test
    public void callbackStaticTest() {
        testFileAgainstNoBuild(new String[]{"42"}, CallbackStaticTest.class.getName(), "7", "6");
    }

    @Test
    public void simpleOperationTest() {
        testFileAgainstNoBuild(new String[]{"42"}, SimpleOperationTest.class.getName(), "7", "6");
    }

    @Test
    public void stringReturnTest() {
        testFileAgainstNoBuild(new String[]{"Hello World"}, StringReturnTest.class.getName());
    }

    @Test
    public void boolReturnTest() {
        testFileAgainstNoBuild(new String[]{"false", "true"}, BoolReturnTest.class.getName());
    }

    @Test
    public void boolArgTest() {
        testFileAgainstNoBuild(new String[]{"boolean", "boolean"}, BoolArgTest.class.getName());
    }

    @Test
    public void byteReturnTest() {
        int[] numbers = {0, 1, 100, 128, 200, 12343245, -1};

        String[] args = new String[1 + numbers.length]; // first argument is class name
        String[] expected = new String[numbers.length];

        args[0] = ByteReturnTest.class.getName();
        for (int i = 0; i < numbers.length; i++) {
            args[i + 1] = String.valueOf(numbers[i]);
            expected[i] = String.valueOf((byte) numbers[i]);
        }

        testFileAgainstNoBuild(expected, args);
    }

    @Test
    public void longReturnTest() {
        long[] numbers = {Long64Lowerer.JS_MAX_EXACT_INT53, Long64Lowerer.JS_MIN_EXACT_INT53, Long.MAX_VALUE, Long.MIN_VALUE, -1};

        String[] expected = new String[numbers.length];

        for (int i = 0; i < numbers.length; i++) {
            double d = numbers[i];
            expected[i] = String.valueOf((long) d);
        }

        testFileAgainstNoBuild(expected, LongReturnTest.class.getName());
    }

    @Test
    public void longArgTest() {
        long[] numbers = {0, 1, 2, Long.MAX_VALUE};

        String[] expected = new String[numbers.length + 1];

        for (int i = 0; i < numbers.length; i++) {
            expected[i] = String.valueOf((double) numbers[i]);
        }

        expected[numbers.length] = "number";

        testFileAgainstNoBuild(expected, LongArgTest.class.getName());
    }

    @Test
    public void floatReturnTest() {
        testFileAgainstNoBuild(new String[]{FloatReturnTest.format((float) 1.123)}, FloatReturnTest.class.getName());
    }

    @Test
    public void doubleReturnTest() {
        testFileAgainstNoBuild(new String[]{DoubleReturnTest.format(1.123)}, DoubleReturnTest.class.getName());
    }

    @Test
    public void stringArgTest() {
        testFileAgainstNoBuild(new String[]{"11", "string"}, StringArgTest.class.getName(), "Hello World");
    }

    @Test
    public void javaScriptBodyObjectTest() {
        testFileAgainstNoBuild(new String[]{"JavaScriptBodyObject", "3.0", "4.0", "null", "7.0", "5.0", "9.0"}, JavaScriptBodyObjectTest.class.getName());
    }

    @Test
    public void nestedJavaScriptBodyObjectTest() {
        testFileAgainstNoBuild(new String[]{"JavaScriptBodyObject", "JavaScriptBodyObject", "42.0", "JavaScriptBodyObject", "43.0"}, NestedJavaScriptBodyObjectTest.class.getName());
    }

    @Test
    public void nestedJSArrayTest() {
        testFileAgainstNoBuild(new String[]{"Hello"}, NestedJSArrayTest.class.getName());
    }

    @Test
    public void covariantCallbackTest() {
        testFileAgainstNoBuild(new String[]{"null"}, CovariantCallbackTest.class.getName());
    }

    @Test
    public void callFromCallbackTest() {
        testFileAgainstNoBuild(new String[]{"null"}, CallFromCallbackTest.class.getName());
    }

    @Test
    public void initInCallbackTest() {
        testFileAgainstNoBuild(new String[]{}, InitInCallbackTest.class.getName());
    }

    @Test
    public void javaScriptResourceTest() {
        testFileAgainstNoBuild(new String[]{"42"}, JavaScriptResourceTest.class.getName());
    }
}
