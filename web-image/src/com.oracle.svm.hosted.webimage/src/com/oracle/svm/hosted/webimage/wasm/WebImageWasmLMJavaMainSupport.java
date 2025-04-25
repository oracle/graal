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

package com.oracle.svm.hosted.webimage.wasm;

import java.lang.reflect.Method;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess.Access;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.hosted.webimage.wasm.annotation.WasmStartFunction;
import com.oracle.svm.hosted.webimage.wasm.gc.MemoryLayout;
import com.oracle.svm.webimage.WebImageJavaMainSupport;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;

@Platforms(WebImageWasmLMPlatform.class)
public class WebImageWasmLMJavaMainSupport extends WebImageJavaMainSupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    public WebImageWasmLMJavaMainSupport(Method javaMainMethod) throws IllegalAccessException {
        super(javaMainMethod);
    }

    /**
     * @see WebImageJavaMainSupport#run(String[])
     * @see #readArguments(int, CIntPointer, CShortPointer)
     */
    public static int run(int argc, CIntPointer argLengthArray, CShortPointer argChars) {
        return WebImageJavaMainSupport.run(readArguments(argc, argLengthArray, argChars));
    }

    /**
     * @see WebImageJavaMainSupport#initializeLibrary(String[])
     * @see #readArguments(int, CIntPointer, CShortPointer)
     */
    public static int initializeLibrary(int argc, CIntPointer argLengthArray, CShortPointer argChars) {
        return WebImageJavaMainSupport.initializeLibrary(readArguments(argc, argLengthArray, argChars));
    }

    /**
     * Reads commandline arguments passed from the embedder.
     *
     * @param argc Number of arguments
     * @param argLengthArray Array of int32 containing the string length (in 16bit UTF-16 code
     *            units) for each string.
     * @param argChars All UTF-16 code units of all strings stitched together (no separator,
     *            boundaries can be determined from the length).
     * @return The string data converted into an array of Java strings.
     */
    private static String[] readArguments(int argc, CIntPointer argLengthArray, CShortPointer argChars) {
        String[] args = new String[argc];

        int base = 0;

        for (int i = 0; i < argc; i++) {
            int argLength = argLengthArray.read(i);
            char[] chars = new char[argLength];

            for (int j = 0; j < argLength; j++) {
                chars[j] = (char) argChars.read(base + j);
            }

            args[i] = new String(chars);
            base += argLength;
        }

        return args;
    }

    /**
     * Runs as part of the WASM module instantiation and should initialize everything required to
     * run compiled Java code.
     */
    @WasmStartFunction
    @Uninterruptible(reason = "Runs while module is constructed")
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Allocator not yet initialized")
    public static void initialize() {
        StackOverflowCheck.singleton().initialize();
        MemoryLayout.initialize();
        /*
         * ExceptionUnwind.exceptionsAreFatal will treat all exceptions as fatal if we don't
         * transition the status to Java mode
         */
        VMThreads.StatusSupport.setStatusJavaUnguarded();
    }
}
