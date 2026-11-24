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
package com.oracle.svm.guest.hosted.classinitialization;

import jdk.internal.misc.Unsafe;

/**
 * Guest-context utilities for class initialization requested by the image builder.
 */
public final class ClassInitializationUtil {

    /** Prevents instantiation. */
    private ClassInitializationUtil() {
    }

    /**
     * Runs as guest code and therefore initializes guest {@code clazz} directly with
     * {@link Unsafe#ensureClassInitialized(Class)}. Uses {@code libGraalLoader} as the context class
     * loader when it is also the class's defining loader.
     */
    @SuppressWarnings("try")
    public static void ensureClassInitialized(Class<?> clazz, ClassLoader libGraalLoader) {
        ClassLoader definingLoader = clazz.getClassLoader();
        // Graal and JVMCI make use of ServiceLoader which uses the
        // context class loader so it needs to be the libgraal loader.
        ClassLoader libGraalContextLoader = libGraalLoader == definingLoader ? definingLoader : null;
        try (var ignored = new ContextClassLoaderScope(libGraalContextLoader)) {
            Unsafe.getUnsafe().ensureClassInitialized(clazz);
        }
    }

    /**
     * Formats the guest exception details from a failed class initializer.
     */
    public static String formatExceptionInInitializerError(ExceptionInInitializerError error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        StringBuilder message = new StringBuilder(" Exception thrown by the class initializer:")
                        .append(System.lineSeparator()).append(System.lineSeparator())
                        .append(cause).append(System.lineSeparator());
        for (StackTraceElement element : cause.getStackTrace()) {
            if (ClassInitializationUtil.class.getName().equals(element.getClassName())) {
                message.append("\t(internal stack frames of the image generator are omitted)").append(System.lineSeparator());
                break;
            }
            message.append("\tat ").append(element).append(System.lineSeparator());
        }
        return message.append(System.lineSeparator()).toString();
    }
}
