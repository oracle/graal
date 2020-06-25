/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.libgraal.jni;

import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.CreateException;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetClassName;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetStackTrace;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetStackTraceElementClassName;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetStackTraceElementFileName;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetStackTraceElementLineNumber;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetStackTraceElementMethodName;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.GetThrowableMessage;
import static org.graalvm.libgraal.jni.annotation.JNIFromLibGraal.Id.UpdateStackTrace;

import org.graalvm.libgraal.jni.annotation.JNIFromLibGraal;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Entry points in HotSpot for exception handling from libgraal.
 */
@Platforms(Platform.HOSTED_ONLY.class)
final class JNIFromLibGraalEntryPoints {

    /**
     * Updates an exception stack trace by decoding a stack trace from libgraal.
     *
     * @param target the {@link Throwable} to update
     * @param rawElements the stringified stack trace elements. Each element has a form
     *            {@code className|methodName|fileName|lineNumber}. If the fileName is missing it's
     *            encoded as an empty string.
     * @return the updated {@link Throwable}
     */
    @JNIFromLibGraal(UpdateStackTrace)
    static Throwable updateStackTrace(Throwable target, String[] rawElements) {
        StackTraceElement[] elements = new StackTraceElement[rawElements.length];
        for (int i = 0; i < rawElements.length; i++) {
            String[] parts = rawElements[i].split("\\|");
            String className = parts[0];
            String methodName = parts[1];
            String fileName = parts[2];
            int lineNumber = Integer.parseInt(parts[3]);
            elements[i] = new StackTraceElement(className, methodName, fileName.isEmpty() ? null : fileName, lineNumber);
        }
        target.setStackTrace(elements);
        return target;
    }

    /**
     * Creates an exception used to throw native exception into Java code.
     *
     * @param message the exception message
     * @return exception
     */
    @JNIFromLibGraal(CreateException)
    static Throwable createException(String message) {
        return new RuntimeException(message);
    }

    @JNIFromLibGraal(GetStackTrace)
    static StackTraceElement[] getStackTrace(Throwable throwable) {
        return throwable.getStackTrace();
    }

    @JNIFromLibGraal(GetStackTraceElementClassName)
    static String getStackTraceElementClassName(StackTraceElement element) {
        return element.getClassName();
    }

    @JNIFromLibGraal(GetStackTraceElementFileName)
    static String getStackTraceElementFileName(StackTraceElement element) {
        return element.getFileName();
    }

    @JNIFromLibGraal(GetStackTraceElementLineNumber)
    static int getStackTraceElementLineNumber(StackTraceElement element) {
        return element.getLineNumber();
    }

    @JNIFromLibGraal(GetStackTraceElementMethodName)
    static String getStackTraceElementMethodName(StackTraceElement element) {
        return element.getMethodName();
    }

    @JNIFromLibGraal(GetThrowableMessage)
    static String getThrowableMessage(Throwable t) {
        return t.getMessage();
    }

    @JNIFromLibGraal(GetClassName)
    static String getClassName(Class<?> clz) {
        return clz.getName();
    }
}
