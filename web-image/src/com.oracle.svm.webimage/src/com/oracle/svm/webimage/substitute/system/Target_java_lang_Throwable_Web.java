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

package com.oracle.svm.webimage.substitute.system;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Reset;

import java.io.PrintStream;
import java.io.PrintWriter;

import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.webimage.JSExceptionSupport;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionIntrinsics;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;

@TargetClass(Throwable.class)
@SuppressWarnings({"static-method", "unused"})
public final class Target_java_lang_Throwable_Web {

    @Alias @RecomputeFieldValue(kind = Reset)//
    StackTraceElement[] stackTrace;

    /**
     * Holds the stacktrace string retrieved from JavaScript.
     */
    @Alias @RecomputeFieldValue(kind = Reset)//
    public Object backtrace;

    @Alias public String detailMessage;

    // Checkstyle: stop
    @Alias public static String SUPPRESSED_CAPTION;
    @Alias public static String CAUSE_CAPTION;
    // Checkstyle: resume

    @Alias
    @KeepOriginal
    public native Target_java_lang_Throwable_Web getCause();

    @Alias
    @KeepOriginal
    public native Target_java_lang_Throwable_Web[] getSuppressed();

    @Alias
    @KeepOriginal
    public native String getMessage();

    /**
     * Fill the backtrace field with a JS {@code Error} object. When the exception stack is printed,
     * the JS stack trace is extracted from the object and converted to a Java string.
     * <p>
     * This is more efficient because the conversion to the Java string has to only be done when a
     * stack trace is printed, while the backtrace field is filled whenever an exception is created.
     */
    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private Throwable fillInStackTrace() {
        if (!JSExceptionSupport.Options.DisableStackTraces.getValue()) {
            this.backtrace = JSFunctionIntrinsics.generateBacktrace();
        }
        return SubstrateUtil.cast(this, Throwable.class);
    }

    /**
     * For WasmLM, storing a JS object does not work at this point because it requires having the GC
     * keeping track of JS objects as well. Instead, the stack trace is immediately converted to a
     * Java string.
     */
    @Substitute
    @TargetElement(name = "fillInStackTrace")
    @Platforms(WebImageWasmLMPlatform.class)
    // Checkstyle: stop method name check
    private Throwable fillInStackTrace_WasmLM() {
        // Checkstyle: resume method name check
        if (!JSExceptionSupport.Options.DisableStackTraces.getValue()) {
            this.backtrace = JSFunctionIntrinsics.generateStackTrace();
        }
        return SubstrateUtil.cast(this, Throwable.class);
    }

    @Substitute
    public void setStackTrace(StackTraceElement[] stackTrace) {
    }

    @Substitute
    private StackTraceElement[] getOurStackTrace() {
        if (stackTrace != null) {
            return stackTrace;
        } else {
            return new StackTraceElement[0];
        }
    }

    @Substitute
    public void printStackTrace(PrintStream s) {
        JSExceptionSupport.printStackTrace(this, s::println);
    }

    @Substitute
    public void printStackTrace(PrintWriter w) {
        JSExceptionSupport.printStackTrace(this, w::println);
    }
}
