/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni;

import static com.oracle.svm.core.MissingRegistrationUtils.ERROR_EMPHASIS_INDENT;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.graalvm.nativeimage.MissingJNIRegistrationError;

import com.oracle.svm.core.MissingRegistrationUtils;

public final class MissingJNIRegistrationUtils {

    public static void forClass(String className) {
        MissingJNIRegistrationError exception = new MissingJNIRegistrationError(errorMessage("access class", className),
                        Class.class, null, className, null);
        report(exception);
    }

    public static void forField(Class<?> declaringClass, String fieldName) {
        MissingJNIRegistrationError exception = new MissingJNIRegistrationError(errorMessage("access field",
                        declaringClass.getTypeName() + "#" + fieldName),
                        Field.class, declaringClass, fieldName, null);
        report(exception);
    }

    public static void forMethod(Class<?> declaringClass, String methodName, String signature) {
        MissingJNIRegistrationError exception = new MissingJNIRegistrationError(errorMessage("access method",
                        declaringClass.getTypeName() + "#" + methodName + signature),
                        Method.class, declaringClass, methodName, signature);
        report(exception);
    }

    private static String errorMessage(String failedAction, String elementDescriptor) {
        return errorMessage(failedAction, elementDescriptor, null, "JNI");
    }

    private static String errorMessage(String failedAction, String elementDescriptor, String note, String helpLink) {
        /* Can't use multi-line strings as they pull in format and bloat "Hello, World!" */
        return "The program tried to reflectively " + failedAction +
                        System.lineSeparator() +
                        System.lineSeparator() +
                        ERROR_EMPHASIS_INDENT + elementDescriptor +
                        System.lineSeparator() +
                        System.lineSeparator() +
                        "without it being registered for runtime JNI access. Add " + elementDescriptor + " to the " + helpLink + " metadata to solve this problem. " +
                        (note != null ? "Note: " + note + " " : "") +
                        "See https://www.graalvm.org/latest/reference-manual/native-image/metadata/#" + helpLink + " for help.";
    }

    private static void report(MissingJNIRegistrationError exception) {
        // GR-54504: get responsible class from anchor
        MissingRegistrationUtils.report(exception, null);
    }
}
