/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.jniutils;

@SuppressWarnings("serial")
public final class ForeignException extends RuntimeException {

    private static final ThreadLocal<ForeignException> pendingException = new ThreadLocal<>();
    private final byte[] rawData;

    public ForeignException(byte[] rawData) {
        this(rawData, false);
    }

    private ForeignException(byte[] rawData, boolean writableStackTrace) {
        super(null, null, true, writableStackTrace);
        this.rawData = rawData;
    }

    public byte[] toByteArray() {
        return rawData;
    }

    public static void clearPendingException() {
        pendingException.set(null);
    }

    public static StackTraceElement[] mergeStackTrace(StackTraceElement[] foreignExceptionStack) {
        ForeignException localException = pendingException.get();
        if (localException != null) {
            return JNIExceptionWrapper.mergeStackTraces(localException.getStackTrace(), foreignExceptionStack, 2, 0, false);
        } else {
            return foreignExceptionStack;
        }
    }

    static ForeignException create(byte[] rawData) {
        ForeignException exception = new ForeignException(rawData, true);
        pendingException.set(exception);
        return exception;
    }
}
