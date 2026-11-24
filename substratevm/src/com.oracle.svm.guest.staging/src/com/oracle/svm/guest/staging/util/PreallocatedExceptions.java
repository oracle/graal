/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.util;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.util.ReflectionUtil;

/**
 * Prepares VM-internal exception instances for reuse across multiple throws at run time. Disables
 * cause initialization, suppression, and stack trace writing so that state attached to one throw
 * cannot leak into a later throw of the same instance.
 * <p>
 * For example, a heap exhaustion path can throw a preallocated {@link OutOfMemoryError} because
 * allocating a fresh exception may fail. Without this preparation, an application could catch the
 * error and call, for example, {@code error.addSuppressed(otherException)}. A later heap exhaustion
 * would then expose that suppressed exception to another caller because it throws the same error
 * instance.
 * <p>
 * Note that for image heap objects, this preparation is necessary even though the substitutions in
 * {@code com.oracle.svm.core.jdk.Target_java_lang_Throwable} discard build-time stack traces. Those
 * substitutions cannot prevent callers from attaching state at run time because they also apply to
 * ordinary application exceptions, which must support normal {@link Throwable} mutability.
 * <p>
 * Use this class only for reused VM-internal instances. Fresh exceptions, including those created by
 * VM code, should retain normal {@link Throwable} mutability.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class PreallocatedExceptions {
    private PreallocatedExceptions() {
    }

    public static OutOfMemoryError createOutOfMemoryError(String message) {
        return prepare(new OutOfMemoryError(message));
    }

    public static NullPointerException createNullPointerException(String message) {
        return prepare(new NullPointerException(message));
    }

    public static ArrayIndexOutOfBoundsException createArrayIndexOutOfBoundsException(String message) {
        return prepare(new ArrayIndexOutOfBoundsException(message));
    }

    public static ClassCastException createClassCastException(String message) {
        return prepare(new ClassCastException(message));
    }

    public static ArrayStoreException createArrayStoreException(String message) {
        return prepare(new ArrayStoreException(message));
    }

    public static IncompatibleClassChangeError createIncompatibleClassChangeError(String message) {
        return prepare(new IncompatibleClassChangeError(message));
    }

    public static IllegalArgumentException createIllegalArgumentException(String message) {
        return prepare(new IllegalArgumentException(message));
    }

    public static NegativeArraySizeException createNegativeArraySizeException() {
        return createNegativeArraySizeException(null);
    }

    public static NegativeArraySizeException createNegativeArraySizeException(String message) {
        return prepare(new NegativeArraySizeException(message));
    }

    public static ArithmeticException createArithmeticException(String message) {
        return prepare(new ArithmeticException(message));
    }

    public static AssertionError createAssertionError(String message) {
        return prepare(new AssertionError(message));
    }

    public static StackOverflowError createStackOverflowError(String message) {
        return prepare(new StackOverflowError(message));
    }

    public static IllegalMonitorStateException createIllegalMonitorStateException(String message) {
        return prepare(new IllegalMonitorStateException(message));
    }

    public static IllegalThreadStateException createIllegalThreadStateException(String message) {
        return prepare(new IllegalThreadStateException(message));
    }

    public static UnsupportedOperationException createUnsupportedOperationException(String message) {
        return prepare(new UnsupportedOperationException(message));
    }

    /**
     * Applies the JDK's {@link Throwable} immutability protocol to an existing exception. Call this
     * method before publishing the exception. Subclass fields are unchanged.
     * <p>
     * Both {@code stackTrace} and {@code backtrace} must be null to prevent
     * {@link Throwable#fillInStackTrace} from recording a new trace. The image heap stack trace
     * transformer preserves a null {@code stackTrace}.
     */
    public static <T extends Throwable> T prepare(T exception) {
        ReflectionUtil.writeField(Throwable.class, "cause", exception, null);
        ReflectionUtil.writeField(Throwable.class, "suppressedExceptions", exception, null);
        ReflectionUtil.writeField(Throwable.class, "stackTrace", exception, null);
        ReflectionUtil.writeField(Throwable.class, "backtrace", exception, null);
        return exception;
    }
}
