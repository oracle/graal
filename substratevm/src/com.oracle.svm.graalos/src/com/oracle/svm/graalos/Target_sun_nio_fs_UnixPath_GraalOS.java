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
package com.oracle.svm.graalos;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

@TargetClass(className = "sun.nio.fs.UnixPath", onlyWith = GraalOSEnabled.class)
final class Target_sun_nio_fs_UnixPath_GraalOS {

    @Alias
    static native Target_sun_nio_fs_UnixPath_GraalOS toUnixPath(Path obj);

    @Alias
    native String getPathForExceptionMessage();

    /**
     * Same as in JDK, but refers to the copied {@link AbstractWatchService}.
     */
    @Substitute
    @SuppressFBWarnings({"H", "C", "BC"})
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        if (watcher == null)
            throw new NullPointerException();
        if (!(watcher instanceof AbstractWatchService))
            throw new ProviderMismatchException();
        return ((AbstractWatchService) watcher).register((Path) (Object) this, events, modifiers);
    }
}
