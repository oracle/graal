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
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;

@TargetClass(className = "sun.nio.fs.UnixFileSystemProvider", onlyWith = GraalOSEnabled.class)
final class Target_sun_nio_fs_UnixFileSystemProvider_GraalOS {

    @Alias
    native Target_sun_nio_fs_UnixFileSystem_GraalOS theFileSystem();

    /**
     * @return a non-secured directory stream only, in contrast to the original method. To return a
     *         SecureDirectoryStream instance, the underlying operating system should have
     *         corresponding support for that. And it is not the case of GraalOS at this moment.
     */
    @Substitute
    @SuppressWarnings({"all", "unchecked"})
    public DirectoryStream<Path> newDirectoryStream(Path path, DirectoryStream.Filter<? super Path> filter)
                    throws IOException {

        if (filter == null) {
            throw new NullPointerException();
        }

        Target_sun_nio_fs_UnixPath_GraalOS dir = Target_sun_nio_fs_UnixPath_GraalOS.toUnixPath(path);
        try {
            long ptr = Target_sun_nio_fs_UnixNativeDispatcher_GraalOS.opendir(dir);
            Object ds = new Target_sun_nio_fs_UnixDirectoryStream_GraalOS(dir, ptr, filter);
            return (DirectoryStream<Path>) ds;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            if ((Object) e instanceof Target_sun_nio_fs_UnixException_GraalOS x) {
                if (x.errno() == Target_sun_nio_fs_UnixConstants_GraalOS.ENOTDIR)
                    throw new NotDirectoryException(dir.getPathForExceptionMessage());
                x.rethrowAsIOException(dir);
                // Should not reach this point
                return null;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    @Substitute
    @SuppressWarnings("all")
    public void createSymbolicLink(Path obj1, Path obj2, FileAttribute<?>... attrs) {
        throw new UnsupportedOperationException();
    }

    @Delete
    public native FileAttributeView getFileAttributeView(Path obj, Class<?> type, LinkOption... options);

    @Delete
    protected native Target_sun_nio_fs_DynamicFileAttributeView_GraalOS getFileAttributeView(Path obj, String name, LinkOption... options);
}
