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
     * Holds the string key that can be used to access a system property that provides information
     * about the stage in which (at the time of requesting the property) code is currently executed.
     *
     * @since 1.0
     */
    public static final String PROPERTY_IMAGE_STAGE_KEY = "org.graalvm.nativeimage.stage";

    /**
     * Describes the stages during which code execution for native images can happen.
     *
     * @since 1.0
     */
    public enum ImageStage {
        /**
         * Describes the stage during which code is executing in the context of image building (e.g.
         * in a static initializer of class that will be contained in the image). The
         * {@linkplain Enum#name() name of this enum constant} is also used as the string that will
         * be returned by a corresponding system property for
         * {@link ImageInfo#PROPERTY_IMAGE_STAGE_KEY}.
         * 
         * @since 1.0
         */
        BuildTime,
        /**
         * Describes the stage during which code is executing at image runtime. The
         * {@linkplain Enum#name() name of this enum constant} is also used as the string that will
         * be returned by a corresponding system property for
         * {@link ImageInfo#PROPERTY_IMAGE_STAGE_KEY}.
         * 
         * @since 1.0
         */
        RunTime
    }

    /**
     * @return {@link ImageStage#BuildTime} if, at the time of the call, code is executing in the
     *         context of image building or {@link ImageStage#RunTime} if code is executing at image
     *         runtime. If code is executing on the JVM (but <b>not</b> in the context of image
     *         building) <b>null</b> is returned.
     *
     * @since 1.0
     */
    public static ImageStage stage() {
        String propertyValue = System.getProperty(PROPERTY_IMAGE_STAGE_KEY);
        return propertyValue != null ? ImageStage.valueOf(propertyValue) : null;
    }
}
