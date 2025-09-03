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
package com.oracle.svm.webimage.jtt.testdispatcher;

import java.util.Arrays;

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

public class JSAnnotationTests extends JTTTestDispatcher {
    public static void main(String[] args) {
        String className = args[0];
        String[] remainingArgs = Arrays.copyOfRange(args, 1, args.length);
        if (checkClass(JSRawCallTest.class, className)) {
            JSRawCallTest.main(remainingArgs);
        } else if (checkClass(CoercionConversionTest.class, className)) {
            CoercionConversionTest.main(remainingArgs);
        } else if (checkClass(JavaDocExamplesTest.class, className)) {
            JavaDocExamplesTest.main(remainingArgs);
        } else if (checkClass(JSObjectConversionTest.class, className)) {
            JSObjectConversionTest.main(remainingArgs);
        } else if (checkClass(JSObjectSubclassTest.class, className)) {
            JSObjectSubclassTest.main(remainingArgs);
        } else if (checkClass(JSPrimitiveConversionTest.class, className)) {
            JSPrimitiveConversionTest.main(remainingArgs);
        } else if (checkClass(JavaProxyConversionTest.class, className)) {
            JavaProxyConversionTest.main(remainingArgs);
        } else if (checkClass(JavaProxyTest.class, className)) {
            JavaProxyTest.main(remainingArgs);
        } else if (checkClass(JSErrorsTest.class, className)) {
            JSErrorsTest.main(remainingArgs);
        } else if (checkClass(HtmlApiExamplesTest.class, className)) {
            HtmlApiExamplesTest.main(remainingArgs);
        } else {
            throw new IllegalArgumentException("unexpected class name");
        }
    }
}
