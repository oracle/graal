/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import java.io.IOException;
import java.nio.file.Path;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.jfr.internal.Repository;

@TargetClass(value = Repository.class, onlyWith = HasJfrSupport.class)
@SuppressWarnings("unused")
public final class Target_jdk_jfr_internal_Repository {

    // Checkstyle: stop
    @Delete //
    private static Path JAVA_IO_TMPDIR;
    // Checkstyle: resume

    @Alias //
    private Path baseLocation;

    @Alias //
    public native void setBasePath(Path baseLocation) throws IOException;

    @Substitute
    synchronized void ensureRepository() throws Exception {
        if (baseLocation == null) {
            Path path = Target_jdk_jfr_internal_util_Utils.getPathInProperty("java.io.tmpdir", null);
            setBasePath(path);
        }
    }
}

@TargetClass(className = "jdk.jfr.internal.util.Utils", onlyWith = HasJfrSupport.class)
final class Target_jdk_jfr_internal_util_Utils {
    @Alias
    public static native Path getPathInProperty(String prop, String subPath);
}
