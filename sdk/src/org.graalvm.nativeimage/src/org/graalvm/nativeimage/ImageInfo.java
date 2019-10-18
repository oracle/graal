/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.nativeimage;

/**
 * Utility class to retrieve information about the context in which code gets executed. The provided
 * string constants are part of the API and guaranteed to remain unchanged in future versions. This
 * allows to use {@link System#getProperty(String)} directly with the string literals defined here
 * thus eliminating the need to depend on this class.
 *
 * @since 19.0
 */
public final class ImageInfo {

    private ImageInfo() {
    }

    /**
     * Holds the string that is the name of the system property providing information about the
     * context in which code is currently executing. If the property returns the string given by
     * {@link #PROPERTY_IMAGE_CODE_VALUE_BUILDTIME} the code is executing in the context of image
     * building (e.g. in a static initializer of a class that will be contained in the image). If
     * the property returns the string given by {@link #PROPERTY_IMAGE_CODE_VALUE_RUNTIME} the code
     * is executing at image runtime. Otherwise the property is not set.
     *
     * @since 19.0
     */
    public static final String PROPERTY_IMAGE_CODE_KEY = "org.graalvm.nativeimage.imagecode";

    /**
     * Holds the string that will be returned by the system property for
     * {@link ImageInfo#PROPERTY_IMAGE_CODE_KEY} if code is executing in the context of image
     * building (e.g. in a static initializer of class that will be contained in the image).
     *
     * @since 19.0
     */
    public static final String PROPERTY_IMAGE_CODE_VALUE_BUILDTIME = "buildtime";

    /**
     * Holds the string that will be returned by the system property for
     * {@link ImageInfo#PROPERTY_IMAGE_CODE_KEY} if code is executing at image runtime.
     *
     * @since 19.0
     */
    public static final String PROPERTY_IMAGE_CODE_VALUE_RUNTIME = "runtime";

    /**
     * Name of the system property that holds if this image is built as a shared library or an
     * executable. If the property is {@link #PROPERTY_IMAGE_KIND_VALUE_EXECUTABLE} the image is
     * built as an executable. If the property is {@link #PROPERTY_IMAGE_KIND_VALUE_SHARED_LIBRARY}
     * the image is built as a shared library.
     *
     * @since 19.0
     */
    public static final String PROPERTY_IMAGE_KIND_KEY = "org.graalvm.nativeimage.kind";

    /**
     * Holds the string that will be returned by the system property for
     * {@link ImageInfo#PROPERTY_IMAGE_KIND_KEY} if image is a shared library.
     *
     * @since 19.0
     */
    public static final String PROPERTY_IMAGE_KIND_VALUE_SHARED_LIBRARY = "shared";

    /**
     * Holds the string that will be returned by the system property for
     * {@link ImageInfo#PROPERTY_IMAGE_KIND_KEY} if image is an executable.
     *
     * @since 19.0
     */
    public static final String PROPERTY_IMAGE_KIND_VALUE_EXECUTABLE = "executable";

    /**
     * Returns true if (at the time of the call) code is executing in the context of image building
     * or during image runtime, else false. This method will be const-folded so that it can be used
     * to hide parts of an application that only work when running on the JVM. For example:
     * {@code if (!ImageInfo.inImageCode()) { ... JVM specific code here ... }}
     *
     * @since 19.0
     */
    public static boolean inImageCode() {
        return System.getProperty(PROPERTY_IMAGE_CODE_KEY) != null;
    }

    /**
     * Returns true if (at the time of the call) code is executing at image runtime. This method
     * will be const-folded. It can be used to hide parts of an application that only work when
     * running as native image.
     *
     * @since 19.0
     */
    public static boolean inImageRuntimeCode() {
        return PROPERTY_IMAGE_CODE_VALUE_RUNTIME.equals(System.getProperty(PROPERTY_IMAGE_CODE_KEY));
    }

    /**
     * Returns true if (at the time of the call) code is executing in the context of image building
     * (e.g. in a static initializer of class that will be contained in the image).
     *
     * @since 19.0
     */
    public static boolean inImageBuildtimeCode() {
        return PROPERTY_IMAGE_CODE_VALUE_BUILDTIME.equals(System.getProperty(PROPERTY_IMAGE_CODE_KEY));
    }

    /**
     * Returns true if the image is build as an executable.
     *
     * @since 19.0
     */
    public static boolean isExecutable() {
        ensureKindAvailable();
        return PROPERTY_IMAGE_KIND_VALUE_EXECUTABLE.equals(System.getProperty(PROPERTY_IMAGE_KIND_KEY));
    }

    /**
     * Returns true if the image is build as a shared library.
     *
     * @since 19.0
     */
    public static boolean isSharedLibrary() {
        ensureKindAvailable();
        return PROPERTY_IMAGE_KIND_VALUE_SHARED_LIBRARY.equals(System.getProperty(PROPERTY_IMAGE_KIND_KEY));
    }

    private static void ensureKindAvailable() {
        if (inImageCode() && System.getProperty(PROPERTY_IMAGE_KIND_KEY) == null) {
            throw new UnsupportedOperationException(
                            "The kind of image that is built (executable or shared library) is not available yet because the relevant command line option has not been parsed yet.");
        }
    }
}
