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

package org.graalvm.nativeimage;

/**
 * Utility class to retrieve information about the context in which code is currently executed.
 *
 * @since 1.0
 */
public final class ImageInfo {

    private ImageInfo() {
    }

    /**
     * Field that holds the string that can be used as system property key to access information
     * about the context in which code is currently executed.
     *
     * @since 1.0
     */
    public static final String PROPERTY_ISAOT_KEY = "org.graalvm.nativeimage.isaot";
    /**
     * Field that holds the string that will be returned by the system property for
     * {@link ImageInfo#PROPERTY_ISAOT_KEY} if code is executing at image runtime.
     *
     * @since 1.0
     */
    public static final String PROPERTY_ISAOT_VALUE_RUNTIME = "image.runtime";
    /**
     * Field that holds the string that will be returned by the system property for
     * {@link ImageInfo#PROPERTY_ISAOT_KEY} if code is executing in the context of image building
     * (e.g. in a static initializer of class that will be contained in the image).
     *
     * @since 1.0
     */
    public static final String PROPERTY_ISAOT_VALUE_BUILDTIME = "image.buildtime";

    /**
     * @return true if (at the time of the call) code is executing in the context of image building
     *         or during image runtime. If code is executing on JVM (but not in the context of image
     *         building) false is returned.
     *
     * @since 1.0
     */
    public static boolean isAOT() {
        return System.getProperty(PROPERTY_ISAOT_KEY) != null;
    }

    /**
     * @return true if (at the time of the call) code is executing at image runtime.
     *
     * @since 1.0
     */
    public static boolean isImageRuntime() {
        return PROPERTY_ISAOT_VALUE_RUNTIME.equals(System.getProperty(PROPERTY_ISAOT_KEY));
    }

    /**
     * @return true if (at the time of the call) code is executing in the context of image building
     *         (e.g. in a static initializer of class that will be contained in the image).
     *
     * @since 1.0
     */
    public static boolean isImageBuildtime() {
        return PROPERTY_ISAOT_VALUE_BUILDTIME.equals(System.getProperty(PROPERTY_ISAOT_KEY));
    }
}
