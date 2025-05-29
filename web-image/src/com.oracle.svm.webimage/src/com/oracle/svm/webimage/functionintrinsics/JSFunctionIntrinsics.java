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
package com.oracle.svm.webimage.functionintrinsics;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Arrays;

import org.graalvm.webimage.api.JS;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.webimage.ExitError;
import com.oracle.svm.webimage.annotation.JSRawCall;

/*
 * Checkstyle: stop method name check
 * We want the intrinsic function names to match the names in the Java
 * standard library
 */
public class JSFunctionIntrinsics {

    @JSRawCall
    @JS("return o === undefined;")
    public static native boolean isUndefined(Object o);

    /**
     * Returns the given type but the java type system thinks it's an object.
     *
     * Use this when you want to pass a value to a JS intrinsic function while preserving the raw
     * value (e.g. undefined).
     */
    @JSRawCall
    @JS("return o;")
    public static native Object getRaw(boolean o);

    @JSRawCall
    @JS("return o;")
    public static native Object getRaw(byte o);

    @JSRawCall
    @JS("return o;")
    public static native Object getRaw(short o);

    @JSRawCall
    @JS("return o;")
    public static native Object getRaw(char o);

    @JSRawCall
    @JS("return o;")
    public static native Object getRaw(int o);

    @JSRawCall
    @JS("return o;")
    public static native Object getRaw(float o);

    @JSRawCall
    @JS("return o;")
    public static native Object getRaw(long o);

    @JSRawCall
    @JS("return o;")
    public static native Object getRaw(double o);

    public static void setExitCode(int exitCode) {
        JSCallNode.intConsumer(JSCallNode.SET_EXIT_CODE, exitCode);
    }

    /**
     * Pass an object to one of JS's native print functions.
     * <p>
     * This can be used to pass objects that aren't actually Java objects but rather JS objects
     * (this may be useful when JS throws a non-Java exception)
     */
    public static void printNative(Object o) {
        JSCallNode.call1(JSCallNode.LLOG, o);
    }

    public static void arrayCopy(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        JSCallNode.arrayCopy(JSCallNode.ARRAY_COPY, fromArray, fromIndex, toArray, toIndex, length);
    }

    /**
     * Terminate the VM execution.
     *
     * @param status exit code
     * @see ExitError
     */
    public static void exit(int status) {
        throw new ExitError(status);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long currentTimeMillis() {
        /*
         * js Date.now returns a double (java type) This doesn't pose a precision problem as a
         * double can accurately represent any millisecond timestamp until the year 287'396.
         */
        return (long) JSCallNode.doubleSupplier(JSCallNode.CURRENT_TIME_MILLIS_DATE);
    }

    /**
     * Calls {@code performance.now()}.
     *
     * @see JSCallNode#PERFORMANCE_NOW
     * @return milliseconds
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static double performanceNow() {
        return JSCallNode.doubleSupplier(JSCallNode.PERFORMANCE_NOW);
    }

    public static int readBytesFromStdIn(byte[] b, int off, int len) {
        throw VMError.unimplemented("readBytesFromStdIn(" + Arrays.toString(b) + ", " + off + ", " + len + ")");
    }

    /**
     * Generates a Java string containing a stacktrace as produced by the JS runtime.
     */
    public static Object generateStackTrace() {
        /*
         * NOTE (ld 10.03.15): it's necessary here to return type:object not type:string as we do
         * not have access to meta information about classes so we cannot instantiate a real object
         * stamp of type:string, for the static analysis svm uses it's necessary that the REAL stamp
         * of the call is present and not the object stmp. as object is not co-variant with string
         * (only string with object) so we return an object here, the real call will produce a
         * string anyway (we than cast in the substitution)
         */
        return JSCallNode.supplier(JSCallNode.GEN_CALL_STACK);
    }

    /**
     * Returns a JavaScript {@code Error} object. This object is later used to extract a JavaScript
     * stacktrace.
     */
    public static Object generateBacktrace() {
        return JSCallNode.supplier(JSCallNode.GEN_BACKTRACE);
    }

    /**
     * Extract the JavaScript stacktrace from the given backtrace, which was creates using
     * {@link #generateBacktrace()}.
     */
    public static String formatStacktrace(Object backtrace) {
        return (String) JSCallNode.function(JSCallNode.FORMAT_STACKTRACE, backtrace);
    }

    public static String getCurrentWorkingDirectory() {
        return (String) JSCallNode.supplier(JSCallNode.GET_CURRENT_WORKING_DIRECTORY);
    }

    public static boolean canReadFile() {
        return true;
    }

    public static double sin(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_SIN, a);
    }

    public static double cos(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_COS, a);
    }

    public static double tan(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_TAN, a);
    }

    public static double asin(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_ASIN, a);
    }

    public static double acos(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_ACOS, a);
    }

    public static double atan(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_ATAN, a);
    }

    public static double exp(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_EXP, a);
    }

    public static double log(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_LOG, a);
    }

    public static double log10(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_LOG10, a);
    }

    public static double sqrt(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_SQRT, a);
    }

    public static double IEEEremainder(double a, double b) {
        return a % b;
    }

    public static double atan2(double a, double b) {
        return JSCallNode.toDoubleBiFunction(JSCallNode.STRICT_MATH_ATAN2, a, b);
    }

    public static double sinh(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_SINH, a);
    }

    public static double cosh(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_COSH, a);
    }

    public static double tanh(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_TANH, a);
    }

    public static double hypot(double a, double b) {
        return JSCallNode.toDoubleBiFunction(JSCallNode.STRICT_MATH_HYPOT, a, b);
    }

    public static double expm1(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_EXPM1, a);
    }

    public static double log1p(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_LOG1P, a);
    }

    public static double pow(double a, double b) {
        return JSCallNode.toDoubleBiFunction(JSCallNode.STRICT_MATH_POW, a, b);
    }

    public static double cbrt(double a) {
        return JSCallNode.toDoubleFunction(JSCallNode.STRICT_MATH_CBRT, a);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long malloc(long size) {
        return JSCallNode.toLongFunction(JSCallNode.MEM_MALLOC, size);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long calloc(long size) {
        return JSCallNode.toLongFunction(JSCallNode.MEM_CALLOC, size);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long realloc(long ptr, long newSize) {
        return JSCallNode.toLongBiFunction(JSCallNode.MEM_REALLOC, ptr, newSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void free(long ptr) {
        JSCallNode.longConsumer(JSCallNode.MEM_FREE, ptr);
    }

    @JSRawCall
    @JS("return toJavaString(i.toString());")
    public static native String numberToString(int i);

    @SuppressWarnings("unused")
    private static void ensureInitialized(DynamicHub hub) {
        hub.ensureInitialized();
    }
}
