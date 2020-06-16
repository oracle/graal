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
package com.oracle.svm.core.c.function;

// Checkstyle: stop

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.util.DirectAnnotationAccess;

import com.oracle.svm.core.util.VMError;

// Checkstyle: resume

/**
 * Errors returned by {@link CEntryPointActions} and {@link CEntryPointNativeFunctions} and their
 * implementation, including snippets and foreign function calls. These are non-API, with the
 * exception of 0 = success.
 */
public final class CEntryPointErrors {
    private CEntryPointErrors() {
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface Description {
        String value();
    }

    @Description("No error occurred.") //
    public static final int NO_ERROR = 0;

    @Description("An unspecified error occurred.") //
    public static final int UNSPECIFIED = 1;

    @Description("An argument was NULL.") //
    public static final int NULL_ARGUMENT = 2;

    @Description("The specified thread is not attached to the isolate.") //
    public static final int UNATTACHED_THREAD = 4;

    @Description("The specified isolate is unknown.") //
    public static final int UNINITIALIZED_ISOLATE = 5;

    @Description("Locating the image file failed.") //
    public static final int LOCATE_IMAGE_FAILED = 6;

    @Description("Locating the image file failed.") //
    public static final int LOCATE_IMAGE_IDENTITY_MISMATCH = 601;

    @Description("Opening the located image file failed.") //
    public static final int OPEN_IMAGE_FAILED = 7;

    @Description("Mapping the heap from the image file into memory failed.") //
    public static final int MAP_HEAP_FAILED = 8;

    @Description("Reserving address space for the new isolate failed.") //
    public static final int RESERVE_ADDRESS_SPACE_FAILED = 801;

    @Description("The image heap does not fit in the available address space.") //
    public static final int INSUFFICIENT_ADDRESS_SPACE = 802;

    @Description("Setting the protection of the heap memory failed.") //
    public static final int PROTECT_HEAP_FAILED = 9;

    @Description("The version of the specified isolate parameters is unsupported.") //
    public static final int UNSUPPORTED_ISOLATE_PARAMETERS_VERSION = 10;

    @Description("Initialization of threading in the isolate failed.") //
    public static final int THREADING_INITIALIZATION_FAILED = 11;

    @Description("Some exception is not caught.") //
    public static final int UNCAUGHT_EXCEPTION = 12;

    @Description("Initialization the isolate failed.") //
    public static final int ISOLATE_INITIALIZATION_FAILED = 13;

    @Description("Opening the located auxiliary image file failed.") //
    public static final int OPEN_AUX_IMAGE_FAILED = 14;

    @Description("Reading the opened auxiliary image file failed.") //
    public static final int READ_AUX_IMAGE_META_FAILED = 15;

    @Description("Mapping the auxiliary image file into memory failed.") //
    public static final int MAP_AUX_IMAGE_FAILED = 16;

    @Description("Insufficient memory for the auxiliary image.") //
    public static final int INSUFFICIENT_AUX_IMAGE_MEMORY = 17;

    @Description("Auxiliary images are not supported on this platform or edition.") //
    public static final int AUX_IMAGE_UNSUPPORTED = 18;

    @Description("Releasing the isolate's address space failed.") //
    public static final int FREE_ADDRESS_SPACE_FAILED = 19;

    @Description("Releasing the isolate's image heap memory failed.") //
    public static final int FREE_IMAGE_HEAP_FAILED = 20;

    public static String getDescription(int code) {
        String result = null;
        if (code >= 0 && code < DESCRIPTIONS.length) {
            result = DESCRIPTIONS[code];
        }
        if (result == null) {
            return "(Unknown error)";
        }
        return result;
    }

    private static final String[] DESCRIPTIONS;
    static {
        try {
            String[] array = new String[16];
            int maxValue = 0;
            for (Field field : CEntryPointErrors.class.getDeclaredFields()) {
                if (!field.getType().equals(int.class)) {
                    continue;
                }
                int value = field.getInt(null);
                String description = DirectAnnotationAccess.getAnnotation(field, CEntryPointErrors.Description.class).value();
                maxValue = Math.max(value, maxValue);
                if (maxValue >= array.length) {
                    array = Arrays.copyOf(array, 2 * maxValue);
                }
                array[value] = description;
            }
            DESCRIPTIONS = Arrays.copyOf(array, maxValue + 1);
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}
