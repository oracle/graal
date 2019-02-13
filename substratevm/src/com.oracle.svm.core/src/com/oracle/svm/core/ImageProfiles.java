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
package com.oracle.svm.core;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Access to collected image profiles. This class provides an API - e.g. {@link #dumpProfiles()}
 * method that can be called any time to obtain information about the currently collected profiling
 * data, if any. This class also serves as an SPI - subclasses shall override the
 * {@link #computeProfiles()} method to return their collected image profiles. There can be at most
 * single implementation of this type, registered in {@link ImageSingletons}.
 *
 */
public abstract class ImageProfiles {
    /**
     * Constructor for subclasses. Can only be used in the hosted environment to register single
     * implementation of the class. Subsequent invocation of this constructur may yield an
     * {@link AssertionError}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    protected ImageProfiles() {
    }

    /**
     * Obtains the current profiles. Computes, or rather obtains, the currently collected profiles.
     * The format of the string isn't defined by this class.
     *
     * @return {@code null} or opaque string representing the collected image profiles
     */
    protected abstract String computeProfiles();

    /**
     * Dumps currently collected image profiles.
     *
     * @return {@code null} or opaque string representing the collected image profiles
     */
    public static String dumpProfiles() {
        return ImageSingletons.contains(ImageProfiles.class) ? ImageSingletons.lookup(ImageProfiles.class).computeProfiles() : null;
    }
}
