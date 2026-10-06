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

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;

/**
 * Only the basic file attributes supported by GraalOS.
 */
final class FileSystemProviderSubstCommon {

    @SuppressFBWarnings(value = {"H", "C", "BC"})
    static FileAttributeView getFileAttributeView(Path obj, Class<?> type, LinkOption... options) {
        // throws NPE if type is null
        if (type.equals(BasicFileAttributeView.class)) {
            Target_sun_nio_fs_UnixPath_GraalOS path = Target_sun_nio_fs_UnixPath_GraalOS.toUnixPath(obj);
            boolean followLinks = Target_sun_nio_fs_Util_GraalOS.followLinks(options);
            Object attrView = new Target_sun_nio_fs_UnixFileAttributeViews_Basic_GraalOS(path, followLinks);
            return (FileAttributeView) attrView;
        }

        return null;
    }

    @SuppressFBWarnings({"H", "C", "BC"})
    static Target_sun_nio_fs_DynamicFileAttributeView_GraalOS getFileAttributeView(Path obj, String name, LinkOption... options) {
        // throws NPE if name is null
        if (name.equals("basic")) {
            Target_sun_nio_fs_UnixPath_GraalOS path = Target_sun_nio_fs_UnixPath_GraalOS.toUnixPath(obj);
            boolean followLinks = Target_sun_nio_fs_Util_GraalOS.followLinks(options);
            Object attrView = new Target_sun_nio_fs_UnixFileAttributeViews_Basic_GraalOS(path, followLinks);
            return (Target_sun_nio_fs_DynamicFileAttributeView_GraalOS) attrView;
        }

        return null;
    }

    @SuppressFBWarnings({"H", "C", "BC"})
    static BasicFileAttributes readAttributes(Path obj, Class<?> type, LinkOption... options) throws IOException {
        // throws NPE if type is null
        if (type.equals(BasicFileAttributes.class)) {
            Target_sun_nio_fs_UnixPath_GraalOS path = Target_sun_nio_fs_UnixPath_GraalOS.toUnixPath(obj);
            boolean followLinks = Target_sun_nio_fs_Util_GraalOS.followLinks(options);
            Object attrView = new Target_sun_nio_fs_UnixFileAttributeViews_Basic_GraalOS(path, followLinks);
            return ((BasicFileAttributeView) attrView).readAttributes();
        }

        throw new UnsupportedOperationException();
    }
}
