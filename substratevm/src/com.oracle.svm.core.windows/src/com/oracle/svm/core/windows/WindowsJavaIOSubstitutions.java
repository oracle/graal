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
package com.oracle.svm.core.windows;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import org.graalvm.nativeimage.Platform.WINDOWS;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.windows.headers.FileAPI;

import java.io.FileDescriptor;

@TargetClass(java.io.FileDescriptor.class)
@Platforms(WINDOWS.class)
final class Target_java_io_FileDescriptor {

    @Alias private long handle;

    @Substitute
    private static long set(int handle) {
        if (handle == 0) {
            return FileAPI.GetStdHandle(FileAPI.STD_INPUT_HANDLE());
        } else if (handle == 1) {
            return FileAPI.GetStdHandle(FileAPI.STD_OUTPUT_HANDLE());
        } else if (handle == 2) {
            return FileAPI.GetStdHandle(FileAPI.STD_ERROR_HANDLE());
        } else {
            return -1;
        }
    }

    @Substitute
    public static FileDescriptor standardStream(int handle) {
        FileDescriptor desc = new FileDescriptor();
        KnownIntrinsics.unsafeCast(desc, Target_java_io_FileDescriptor.class).handle = set(handle);
        return (desc);
    }
}

/** Dummy class to have a class with the file's name. */
@Platforms(WINDOWS.class)
public final class WindowsJavaIOSubstitutions {

    /** Private constructor: No instances. */
    private WindowsJavaIOSubstitutions() {
    }
}
