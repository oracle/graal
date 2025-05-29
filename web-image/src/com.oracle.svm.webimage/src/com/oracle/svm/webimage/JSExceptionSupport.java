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

package com.oracle.svm.webimage;

import static com.oracle.svm.webimage.substitute.system.Target_java_lang_Throwable_Web.CAUSE_CAPTION;
import static com.oracle.svm.webimage.substitute.system.Target_java_lang_Throwable_Web.SUPPRESSED_CAPTION;

import org.graalvm.webimage.api.JSError;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSValue;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.webimage.functionintrinsics.JSConversion;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionIntrinsics;
import com.oracle.svm.webimage.substitute.system.Target_java_lang_Throwable_Web;

import jdk.graal.compiler.options.Option;

public class JSExceptionSupport {

    public static class Options {
        @Option(help = "Turn off collection of stack traces")//
        public static final HostedOptionKey<Boolean> DisableStackTraces = new HostedOptionKey<>(false);
    }

    @NeverInline("Throwable in catch block may also be JS objects (which is a bug) and we want to handle those separately.")
    public static boolean isThrowable(Object o) {
        return o instanceof Throwable;
    }

    public static void printStackTrace(Target_java_lang_Throwable_Web t, Printer s) {
        printStackTrace(t, s, new ExceptionToString());
    }

    public static void printStackTrace(Target_java_lang_Throwable_Web t, Printer s, ExceptionToString toString) {
        printStackTrace(t, s, toString, "", "");
    }

    /**
     * Prints the stack trace for the given {@link Target_java_lang_Throwable_Web}.
     * <p>
     * Recursively prints any inner exceptions (suppressed, caused by).
     * <p>
     * This is an implementation of the {@link Throwable#printStackTrace} family of methods.
     *
     * @param t The throwable
     * @param s Text is printed to here
     * @param toString Custom {@code toString} for throwables.
     * @param caption Caption used for inner exceptions (e.g. 'Caused by: ')
     * @param prefix Prefix to print before every line (identation)
     */
    public static void printStackTrace(Target_java_lang_Throwable_Web t, Printer s, ExceptionToString toString, String caption, String prefix) {
        // Differentiate between caught Java exceptions and caught JS objects.
        if (!isThrowable(t)) {
            Object proxied = JSConversion.unproxy(t);

            if (proxied instanceof Target_java_lang_Throwable_Web proxiedThrowable) {
                printStackTrace(proxiedThrowable, s, toString, caption, prefix);
            } else {
                s.println(prefix + caption + "<JS Error>:");
                JSFunctionIntrinsics.printNative(t);
            }
            return;
        }

        // Print our stack trace
        s.println(prefix + caption + toString.apply(t));
        Object backtrace = t.backtrace;

        if (JSExceptionSupport.Options.DisableStackTraces.getValue()) {
            s.println(prefix + "\tStacktraces have been disabled in this image");
        } else if (backtrace == null) {
            s.println(prefix + "\tThis exception does not have a stacktrace, this is a bug!");
        } else {
            /*
             * If the backtrace object is already a string, use it as-is, otherwise call JS code to
             * create a string from the backtrace object (which is a JS Error object).
             */
            String stacktrace = backtrace instanceof String str ? str : JSFunctionIntrinsics.formatStacktrace(backtrace);
            printNativeStacktrace(stacktrace, s, prefix);
        }

        // Print suppressed exceptions, if any
        for (Target_java_lang_Throwable_Web se : t.getSuppressed()) {
            printStackTrace(se, s, toString, SUPPRESSED_CAPTION, prefix + "\t");
        }

        // Print cause, if any
        Target_java_lang_Throwable_Web ourCause = t.getCause();
        if (ourCause != null) {
            printStackTrace(ourCause, s, toString, CAUSE_CAPTION, prefix);
        }

        /*
         * If the throwable is a JSError, the thrown JS object is implicitly a cause (if it is an
         * Error object). In that case, we also print the JS stack trace if available.
         */
        if (SubstrateUtil.cast(t, Throwable.class) instanceof JSError jsError) {
            Object thrownObject = jsError.getThrownObject();
            if (thrownObject instanceof JSObject jsObject && jsObject.get("stack") instanceof JSValue stack) {
                s.println(prefix + "Caused by JS Error: " + t.getMessage());
                if (stack instanceof JSString jsString) {
                    printNativeStacktrace(jsString.asString(), s, prefix);
                } else {
                    printNativeStacktrace("Unexpected stack trace: " + stack, s, prefix);
                }
            }
        }
    }

    private static void printNativeStacktrace(String stacktrace, Printer s, String prefix) {
        // Print each line of the trace with proper indentation
        char[] chars = stacktrace.toCharArray();
        int startIdx = 0;
        int endIdx = 0;

        while (endIdx < chars.length) {
            if (chars[endIdx] == '\n') {
                String line = stacktrace.substring(startIdx, endIdx);
                s.println(prefix + "\t" + line.stripLeading());
                startIdx = endIdx + 1;
            }

            endIdx++;
        }
    }

    /**
     * Custom {@code toString} method for {@linkplain Throwable throwables}.
     * <p>
     * The main goal is to not make the default {@code toString} method unconditionally reachable
     * (because it pulls in a lot of types). Because of this, no static instances of these classes
     * should exist.
     */
    static class ExceptionToString {
        public String apply(Target_java_lang_Throwable_Web t) {
            return t.toString();
        }
    }

    /**
     * Custom {@code toString} that uses {@link Throwable#getMessage()} instead of
     * {@link Throwable#getLocalizedMessage()}.
     */
    public static class ExceptionToNonLocalizedString extends ExceptionToString {
        @Override
        public String apply(Target_java_lang_Throwable_Web t) {
            String s = t.getClass().getName();
            String message = t.getMessage();
            return (message != null) ? (s + ": " + message) : s;
        }
    }

    @FunctionalInterface
    public interface Printer {
        void println(Object str);
    }
}
