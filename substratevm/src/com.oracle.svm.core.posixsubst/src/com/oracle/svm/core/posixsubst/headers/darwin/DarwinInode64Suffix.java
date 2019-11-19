/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posixsubst.headers.darwin;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.impl.DeprecatedPlatform;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.posixsubst.headers.Dirent.DIR;
import com.oracle.svm.core.posixsubst.headers.Dirent.dirent;
import com.oracle.svm.core.posixsubst.headers.Dirent.direntPointer;
import com.oracle.svm.core.posixsubst.headers.Stat.stat;

//Checkstyle: stop

@Platforms(DeprecatedPlatform.DARWIN_SUBSTITUTION.class)
class DarwinInode64Suffix {

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Dirent.class)
    static final class Target_com_oracle_svm_core_posix_headers_Dirent {

        @Substitute
        @CFunction("opendir$INODE64")
        private static native DIR opendir(CCharPointer name);

        @Substitute
        @CFunction("readdir_r$INODE64")
        private static native int readdir_r(DIR dirp, dirent entry, direntPointer result);
    }

    @TargetClass(com.oracle.svm.core.posixsubst.headers.Stat.class)
    static final class Target_com_oracle_svm_core_posix_headers_Stat {
        @Substitute
        @CFunction("stat$INODE64")
        private static native int stat(CCharPointer file, stat buf);

        @Substitute
        @CFunction("fstat$INODE64")
        private static native int fstat(int fd, stat buf);

        @Substitute
        @CFunction("lstat$INODE64")
        private static native int lstat(CCharPointer file, stat buf);
    }
}
