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
package com.oracle.svm.jdwp.bridge.jniutils;

import org.graalvm.nativeimage.ImageSingletons;

/**
 * Services used by the {@code com.oracle.svm.jdwp.bridge.jniutils} module. To enable the
 * {@code com.oracle.svm.jdwp.bridge.jniutils} module a {@code NativeBridgeSupport} instance must be
 * registered in the {@link ImageSingletons}.
 */
public interface NativeBridgeSupport {

    /**
     * Returns the name of a feature using {@code com.oracle.svm.jdwp.bridge.jniutils} module. The
     * feature name is used in the logging output.
     */
    String getFeatureName();

    /**
     * Checks if logging at given level is enabled.
     */
    boolean isTracingEnabled(int level);

    /**
     * Logs the message.
     */
    void trace(String message);

    /**
     * Returns a {@code NativeBridgeSupport} instance registered in the {@link ImageSingletons}.
     */
    static NativeBridgeSupport getInstance() {
        return ImageSingletons.lookup(NativeBridgeSupport.class);
    }
}
