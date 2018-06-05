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
package com.oracle.svm.core.posix.headers.darwin;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.posix.headers.Dirent.DIR;
import com.oracle.svm.core.posix.headers.Dirent.dirent;
import com.oracle.svm.core.posix.headers.Dirent.direntPointer;
import com.oracle.svm.core.posix.headers.Stat.stat;

//Checkstyle: stop

@Platforms(Platform.DARWIN.class)
class DarwinInode64Suffix {

    @TargetClass(com.oracle.svm.core.posix.headers.Dirent.class)
    static final class Target_com_oracle_svm_core_posix_headers_Dirent {

        @Substitute
        @CFunction("opendir$INODE64")
        private static native DIR opendir(CCharPointer name);

        @Substitute
        @CFunction("readdir$INODE64")
        private static native dirent readdir(DIR dirp);

        @Substitute
        @CFunction("readdir_r$INODE64")
        private static native int readdir_r(DIR dirp, dirent entry, direntPointer result);

        @Substitute
        @CFunction("rewinddir$INODE64")
        private static native void rewinddir(DIR dirp);

        @Substitute
        @CFunction("seekdir$INODE64")
        private static native void seekdir(DIR dirp, long pos);

        @Substitute
        @CFunction("telldir$INODE64")
        private static native long telldir(DIR dirp);

        @Substitute
        @CFunction("scandir$INODE64")
        private static native int scandir(CCharPointer dir, PointerBase namelist, CFunctionPointer selector, CFunctionPointer cmp);

        @Substitute
        @CFunction("alphasort$INODE64")
        private static native int alphasort(direntPointer e1, direntPointer e2);

        /*
         * getdirentries() doesn't work when 64-bit inodes is in effect, so we generate a link
         * error.
         */
        @Substitute
        @CFunction("_getdirentries_is_not_available_when_64_bit_inodes_are_in_effect")
        private static native SignedWord getdirentries(int fd, CCharPointer buf, SignedWord nbytes, PointerBase basep);
    }

    @TargetClass(com.oracle.svm.core.posix.headers.Stat.class)
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
