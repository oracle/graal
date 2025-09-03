/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.VMError;

public final class JDKUtils {

    /**
     * Returns the raw error message stored in {@link Throwable} and returned by default from
     * {@link Throwable#getMessage}. This method ignores possible overrides of
     * {@link Throwable#getMessage} and is therefore guaranteed to be allocation free.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static String getRawMessage(Throwable ex) {
        return SubstrateUtil.cast(ex, Target_java_lang_Throwable.class).detailMessage;
    }

    /**
     * Returns the raw cause stored in {@link Throwable} and returned by default from
     * {@link Throwable#getCause}. This method ignores possible overrides of
     * {@link Throwable#getCause} and is therefore guaranteed to be allocation free.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Throwable getRawCause(Throwable ex) {
        Throwable cause = SubstrateUtil.cast(ex, Target_java_lang_Throwable.class).cause;
        return cause == ex ? null : cause;
    }

    /**
     * Gets the materialized {@link StackTraceElement} array stored in a {@link Throwable} object.
     * Must only be called if {@link #isStackTraceValid} returns (or would return) {@code true}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static StackTraceElement[] getRawStackTrace(Throwable ex) {
        VMError.guarantee(isStackTraceValid(ex));
        return SubstrateUtil.cast(ex, Target_java_lang_Throwable.class).stackTrace;
    }

    /**
     * Returns {@code true} if the {@linkplain #getRawStackTrace stack trace} stored in a
     * {@link Throwable} object is valid. If not, {@link #getBacktrace} must be used to access the
     * Java stack trace frames.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isStackTraceValid(Throwable ex) {
        StackTraceElement[] stackTrace = SubstrateUtil.cast(ex, Target_java_lang_Throwable.class).stackTrace;
        return stackTrace != Target_java_lang_Throwable.UNASSIGNED_STACK && stackTrace != null;
    }

    /**
     * Gets the internal backtrace of a {@link Throwable} object. Only returns a non-null value if
     * {@link #isStackTraceValid} would return {@code false}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Object getBacktrace(Throwable ex) {
        return SubstrateUtil.cast(ex, Target_java_lang_Throwable.class).backtrace;
    }
}
