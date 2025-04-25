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

package com.oracle.svm.hosted.webimage.wasmgc;

import java.lang.reflect.Method;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.webimage.WebImageJavaMainSupport;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;

@Platforms(WebImageWasmGCPlatform.class)
public class WebImageWasmGCJavaMainSupport extends WebImageJavaMainSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    public WebImageWasmGCJavaMainSupport(Method javaMainMethod) throws IllegalAccessException {
        super(javaMainMethod);
    }

    /**
     * ExceptionUnwind.exceptionsAreFatal will treat all exceptions as fatal if we don't transition
     * the status to Java mode.
     */
    private static void transitionToJavaMode() {
        VMThreads.StatusSupport.setStatusJavaUnguarded();
    }

    public static int run(String[] args) {
        transitionToJavaMode();
        return WebImageJavaMainSupport.run(args);
    }

    public static int initializeLibrary(String[] args) {
        transitionToJavaMode();
        return WebImageJavaMainSupport.initializeLibrary(args);
    }
}
