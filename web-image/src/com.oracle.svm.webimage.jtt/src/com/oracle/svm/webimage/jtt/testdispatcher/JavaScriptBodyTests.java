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

public class JavaScriptBodyTests extends JTTTestDispatcher {
    public static void main(String[] args) {
        String className = args[0];
        String[] remainingArgs = Arrays.copyOfRange(args, 1, args.length);
        if (checkClass(StaticCallTest.class, className)) {
            StaticCallTest.main(remainingArgs);
        } else if (checkClass(DynamicCallTest.class, className)) {
            DynamicCallTest.main(remainingArgs);
        } else if (checkClass(CallbackTest.class, className)) {
            CallbackTest.main(remainingArgs);
        } else if (checkClass(CallbackStaticTest.class, className)) {
            CallbackStaticTest.main(remainingArgs);
        } else if (checkClass(SimpleOperationTest.class, className)) {
            SimpleOperationTest.main(remainingArgs);
        } else if (checkClass(StringReturnTest.class, className)) {
            StringReturnTest.main(remainingArgs);
        } else if (checkClass(BoolReturnTest.class, className)) {
            BoolReturnTest.main(remainingArgs);
        } else if (checkClass(BoolArgTest.class, className)) {
            BoolArgTest.main(remainingArgs);
        } else if (checkClass(ByteReturnTest.class, className)) {
            ByteReturnTest.main(remainingArgs);
        } else if (checkClass(LongReturnTest.class, className)) {
            LongReturnTest.main(remainingArgs);
        } else if (checkClass(LongArgTest.class, className)) {
            LongArgTest.main(remainingArgs);
        } else if (checkClass(FloatReturnTest.class, className)) {
            FloatReturnTest.main(remainingArgs);
        } else if (checkClass(DoubleReturnTest.class, className)) {
            DoubleReturnTest.main(remainingArgs);
        } else if (checkClass(StringArgTest.class, className)) {
            StringArgTest.main(remainingArgs);
        } else if (checkClass(JavaScriptBodyObjectTest.class, className)) {
            JavaScriptBodyObjectTest.main(remainingArgs);
        } else if (checkClass(NestedJavaScriptBodyObjectTest.class, className)) {
            NestedJavaScriptBodyObjectTest.main(remainingArgs);
        } else if (checkClass(NestedJSArrayTest.class, className)) {
            NestedJSArrayTest.main(remainingArgs);
        } else if (checkClass(CovariantCallbackTest.class, className)) {
            CovariantCallbackTest.main(remainingArgs);
        } else if (checkClass(CallFromCallbackTest.class, className)) {
            CallFromCallbackTest.main(remainingArgs);
        } else if (checkClass(InitInCallbackTest.class, className)) {
            InitInCallbackTest.main(remainingArgs);
        } else if (checkClass(JavaScriptResourceTest.class, className)) {
            JavaScriptResourceTest.main(remainingArgs);
        } else {
            throw new IllegalArgumentException("unexpected class name");
        }
    }
}
