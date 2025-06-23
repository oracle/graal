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

import java.nio.file.Path;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.hosted.webimage.test.util.JTTTestSuite;
import com.oracle.svm.hosted.webimage.test.util.WebImageTestOptions;
import com.oracle.svm.webimage.jtt.api.CoercionConversionTest;
import com.oracle.svm.webimage.jtt.api.HtmlApiExamplesTest;
import com.oracle.svm.webimage.jtt.api.JSErrorsTest;
import com.oracle.svm.webimage.jtt.api.JSObjectConversionTest;
import com.oracle.svm.webimage.jtt.api.JSObjectSubclassTest;
import com.oracle.svm.webimage.jtt.api.JSPrimitiveConversionTest;
import com.oracle.svm.webimage.jtt.api.JSRawCallTest;
import com.oracle.svm.webimage.jtt.api.JavaDocExamplesTest;
import com.oracle.svm.webimage.jtt.api.JavaProxyConversionTest;
import com.oracle.svm.webimage.jtt.api.JavaProxyTest;
import com.oracle.svm.webimage.jtt.testdispatcher.JSAnnotationTests;

/**
 * Tests for the JavaScriptBody annotation.
 */
public class JS_JTT_JSAnnotation extends JTTTestSuite {

    // @formatter:off
    private static final String REFLECT_CONFIG = """
[
  {
    "name" : "java.lang.String",
    "methods": [
        {"name" : "indexOf"}
    ]
  }
]""";

    public static Class<?> testDispatcher = JSAnnotationTests.class;

    @BeforeClass
    public static void setupClass() {
        Path reflectConfigFile = writeReflectionConfig(REFLECT_CONFIG);
        compileToJS(testDispatcher, "-H:ReflectionConfigurationFiles=" + reflectConfigFile);
    }

    @Test
    public void rawCallTest() {
        testFileAgainstNoBuild(JSRawCallTest.OUTPUT, JSRawCallTest.class.getName());
    }

    @Test
    public void coercionConversion() {
        // TODO GR-60603 Enable once JS annotation is supported in WasmGC
        Assume.assumeFalse(WebImageTestOptions.isWasmGCBackend());
        testFileAgainstNoBuild(CoercionConversionTest.OUTPUT, CoercionConversionTest.class.getName());
    }

    @Test
    public void javaDocExamples() {
        // TODO GR-60603 Enable once JS annotation is supported in WasmGC
        Assume.assumeFalse(WebImageTestOptions.isWasmGCBackend());
        testFileAgainstNoBuild(JavaDocExamplesTest.OUTPUT, JavaDocExamplesTest.class.getName());
    }

    @Test
    public void jsObjectConversion() {
        // TODO GR-60603 Enable once JS annotation is supported in WasmGC
        Assume.assumeFalse(WebImageTestOptions.isWasmGCBackend());
        testFileAgainstNoBuild(JSObjectConversionTest.OUTPUT, JSObjectConversionTest.class.getName());
    }

    @Test
    public void jsObjectSubclass() {
        // TODO GR-60603 Enable once JS annotation is supported in WasmGC
        Assume.assumeFalse(WebImageTestOptions.isWasmGCBackend());
        testFileAgainstNoBuild(JSObjectSubclassTest.OUTPUT, JSObjectSubclassTest.class.getName());
    }

    @Test
    public void jsPrimitiveConversion() {
        testFileAgainstNoBuild(JSPrimitiveConversionTest.OUTPUT, JSPrimitiveConversionTest.class.getName());
    }

    @Test
    public void javaProxyConversion() {
        // TODO GR-60603 Enable once JS annotation is supported in WasmGC
        Assume.assumeFalse(WebImageTestOptions.isWasmGCBackend());
        testFileAgainstNoBuild(JavaProxyConversionTest.OUTPUT, JavaProxyConversionTest.class.getName());
    }

    @Test
    public void javaProxy() {
        testFileAgainstNoBuild(JavaProxyTest.OUTPUT, JavaProxyTest.class.getName());
    }

    @Test
    public void jsErrors() {
        testFileAgainstNoBuild(JSErrorsTest.OUTPUT, JSErrorsTest.class.getName());
    }

    @Test
    public void htmlApiExamplesTest() {
        // TODO GR-60603 Enable once JS annotation is supported in WasmGC
        Assume.assumeFalse(WebImageTestOptions.isWasmGCBackend());
        testFileAgainstNoBuild(HtmlApiExamplesTest.OUTPUT, HtmlApiExamplesTest.class.getName());
    }

}
