/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.core.jdk.Helper_sun_nio_fs_UnixNativeDispatcher.init1;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "sun.nio.fs.UnixNativeDispatcher", onlyWith = NonWindowsOS.class)
final class Target_sun_nio_fs_UnixNativeDispatcher {

    /**
     * Wraps the original native {@code init()} call with a pthread mutex. This is a workaround for
     * GR-69415. The assumption is that there is a race condition when multiple isolates call the
     * static initializer of {@code UnixNativeDispatcher} and thus the native {@code init} function
     * concurrently. All those calls operate on the same global C variables so multiple isolates can
     * interfere with each other.
     */
    @Substitute
    static int init() {
        return init1(Target_sun_nio_fs_UnixNativeDispatcher.class);
    }
}

final class Helper_sun_nio_fs_UnixNativeDispatcher {
    /**
     * A C-level thread-safe wrapper for the original
     * {@link Target_sun_nio_fs_UnixNativeDispatcher#init()}. Implementation is in
     * {@code UnixNativeDispatcher.c}.
     */
    static native int init1(Class<?> targetSunNioFsUnixNativeDispatcherClass);
}
