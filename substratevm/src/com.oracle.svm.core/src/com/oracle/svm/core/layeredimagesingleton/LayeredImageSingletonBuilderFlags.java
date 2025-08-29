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
package com.oracle.svm.core.layeredimagesingleton;

import java.util.EnumSet;

/**
 * Flags used during build time to determine how the native image generator handles the layered
 * image singleton.
 */
public enum LayeredImageSingletonBuilderFlags {
    /**
     * This singleton can be accessed from the runtime.
     */
    RUNTIME_ACCESS,
    /**
     * This singleton can be accessed from the buildtime.
     */
    BUILDTIME_ACCESS;

    /*
     * Below are some common flag patterns.
     */

    public static final EnumSet<LayeredImageSingletonBuilderFlags> BUILDTIME_ACCESS_ONLY = EnumSet.of(BUILDTIME_ACCESS);

    public static final EnumSet<LayeredImageSingletonBuilderFlags> RUNTIME_ACCESS_ONLY = EnumSet.of(RUNTIME_ACCESS);

    public static final EnumSet<LayeredImageSingletonBuilderFlags> ALL_ACCESS = EnumSet.of(RUNTIME_ACCESS, BUILDTIME_ACCESS);
}
