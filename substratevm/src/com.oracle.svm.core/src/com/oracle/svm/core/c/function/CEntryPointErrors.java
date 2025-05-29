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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.util.HostedByteBufferPointer;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

/**
 * Errors returned by {@link CEntryPointActions} and {@link CEntryPointNativeFunctions} and their
 * implementation, including snippets and foreign function calls. These are non-public API as
 * callers such as libgraal rely on those values.
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

    @Description("Memory allocation failed, the OS is probably out of memory.") //
    public static final int ALLOCATION_FAILED = 3;

    @Description("The specified thread is not attached to the isolate.") //
    public static final int UNATTACHED_THREAD = 4;

    @Description("The specified isolate is unknown.") //
    public static final int UNINITIALIZED_ISOLATE = 5;

    @Description("Locating the image file failed.") //
    public static final int LOCATE_IMAGE_FAILED = 6;

    @Description("Opening the located image file failed.") //
    public static final int OPEN_IMAGE_FAILED = 7;

    @Description("Mapping the heap from the image file into memory failed.") //
    public static final int MAP_HEAP_FAILED = 8;

    @Description("Reserving address space for the new isolate failed.") //
    public static final int RESERVE_ADDRESS_SPACE_FAILED = 801;

    @Description("The image heap does not fit in the available address space.") //
    public static final int INSUFFICIENT_ADDRESS_SPACE = 802;

    @Description("The operating system does not support mremap.") //
    public static final int MREMAP_NOT_SUPPORTED = 803;

    @Description("Setting the protection of the heap memory failed.") //
    public static final int PROTECT_HEAP_FAILED = 9;

    @Description("The version of the specified isolate parameters is unsupported.") //
    public static final int UNSUPPORTED_ISOLATE_PARAMETERS_VERSION = 10;

    @Description("Initialization of threading in the isolate failed.") //
    public static final int THREADING_INITIALIZATION_FAILED = 11;

    @Description("Some exception is not caught.") //
    public static final int UNCAUGHT_EXCEPTION = 12;

    @Description("Initializing the isolate failed.") //
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

    @Description("The auxiliary image was built from a different primary image.") //
    public static final int AUX_IMAGE_PRIMARY_IMAGE_MISMATCH = 21;

    @Description("The isolate arguments could not be parsed.") //
    public static final int ARGUMENT_PARSING_FAILED = 22;

    @Description("Current target does not support the CPU features that are required by the image.") //
    public static final int CPU_FEATURE_CHECK_FAILED = 23;

    @Description("Image page size is incompatible with run-time page size. Rebuild image with -H:PageSize=[pagesize] to set appropriately.") //
    public static final int PAGE_SIZE_CHECK_FAILED = 24;

    @Description("Creating an in-memory file for the GOT failed.") //
    public static final int DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_CREATE_FAILED = 25;

    @Description("Resizing the in-memory file for the GOT failed.") //
    public static final int DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_RESIZE_FAILED = 26;

    @Description("Mapping and populating the in-memory file for the GOT failed.") //
    public static final int DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_MAP_FAILED = 27;

    @Description("Mapping the GOT before an isolate's heap failed (no mapping).") //
    public static final int DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_MMAP_FAILED = 28;

    @Description("Mapping the GOT before an isolate's heap failed (wrong mapping).") //
    public static final int DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_WRONG_MMAP = 29;

    @Description("Mapping the GOT before an isolate's heap failed (invalid file).") //
    public static final int DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_INVALID = 30;

    @Description("Could not create unique GOT file even after retrying.") //
    public static final int DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_UNIQUE_FILE_CREATE_FAILED = 31;

    @Description("Could not determine the stack boundaries.") //
    public static final int UNKNOWN_STACK_BOUNDARIES = 32;

    @Description("The isolate could not be created because only a single isolate is supported.") //
    public static final int SINGLE_ISOLATE_ALREADY_CREATED = 33;

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

    /**
     * Gets the description for {@code code} as a C string.
     *
     * @param code an error code
     * @return the description for {@code} or a null pointer if no description is available
     */
    @Uninterruptible(reason = "Called when isolate creation fails.")
    public static CCharPointer getDescriptionAsCString(int code) {
        return (CCharPointer) getDescriptionAsCString(code, (Pointer) CSTRING_DESCRIPTIONS.get());
    }

    /**
     * Searches {@code cstrings} for a description of {@code code}.
     *
     * @return the description for {@code} or a null pointer if no description is available
     */
    @Uninterruptible(reason = "Called when isolate creation fails.")
    private static Pointer getDescriptionAsCString(int code, Pointer cstrings) {
        int offset = 0;
        int startOffset = 0;
        int codeIndex = 0;
        while (true) {
            byte ch = cstrings.readByte(offset);
            if (ch == 1) {
                break;
            } else if (ch == 0) {
                startOffset = offset + 1;
                codeIndex++;
            } else if (code == codeIndex) {
                return cstrings.add(startOffset);
            }
            offset++;
        }
        return Word.nullPointer();
    }

    private static final String[] DESCRIPTIONS;

    /**
     * The error descriptions as C strings in a single contiguous chunk of global memory. This is
     * required to be able to access descriptions when errors occur during isolate creation. Without
     * an isolate, static fields are not usable.
     *
     * @see #toCStrings
     */
    private static final CGlobalData<CCharPointer> CSTRING_DESCRIPTIONS;

    static {
        try {
            String[] array = new String[16];
            int maxValue = 0;
            for (Field field : CEntryPointErrors.class.getDeclaredFields()) {
                if (!field.getType().equals(int.class)) {
                    continue;
                }
                int value = field.getInt(null);
                String description = AnnotationAccess.getAnnotation(field, CEntryPointErrors.Description.class).value();
                maxValue = Math.max(value, maxValue);
                if (maxValue >= array.length) {
                    array = Arrays.copyOf(array, 2 * maxValue);
                }
                array[value] = description;
            }
            String[] descriptions = Arrays.copyOf(array, maxValue + 1);
            byte[] cstrings = toCStrings(descriptions);
            DESCRIPTIONS = descriptions;
            CSTRING_DESCRIPTIONS = CGlobalDataFactory.createBytes(() -> cstrings);
        } catch (IllegalAccessException | IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    /**
     * Converts all entries in {@code descriptions} to 0-terminated C strings and concatenates them
     * into a single byte array with a final terminator of 1. Null entries are converted to 0 length
     * C strings.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] toCStrings(String[] descriptions) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int code = 0; code < descriptions.length; code++) {
            String description = descriptions[code];
            if (description != null) {
                baos.write(description.getBytes(StandardCharsets.UTF_8));
            }
            baos.write(0);
        }
        baos.write(1);
        return checkedCStrings(descriptions, baos.toByteArray());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] checkedCStrings(String[] descriptions, byte[] cstrings) {
        for (int i = 0; i < cstrings.length; i++) {
            byte ch = cstrings[i];
            VMError.guarantee(ch != 1 || i == cstrings.length - 1, "only last byte in cstrings may be 1, got %d at index %d", ch, i);
        }
        for (int code = 0; code < descriptions.length; code++) {
            String expect = descriptions[code];
            Pointer cstringsPointer = new HostedByteBufferPointer(ByteBuffer.wrap(cstrings), 0);
            String actual = toJavaString(getDescriptionAsCString(code, cstringsPointer));
            if (!Objects.equals(expect, actual)) {
                throw VMError.shouldNotReachHere("code %d: expected %s, got %s", code, expect, actual);
            }
        }
        return cstrings;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static String toJavaString(Pointer res) {
        if (res.isNonNull()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int i = 0;
            while (true) {
                byte ch = res.readByte(i++);
                if (ch == 0) {
                    return new String(baos.toByteArray(), StandardCharsets.UTF_8);
                }
                baos.write(ch);
            }
        }
        return null;
    }
}
