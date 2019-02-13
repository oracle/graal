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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file dirent.h.
 */
@CContext(PosixDirectives.class)
public class Dirent {

    @CStruct(addStructKeyword = true)
    public interface dirent extends PointerBase {
        @CField
        long d_ino();

        @CField
        @Platforms(Platform.LINUX.class)
        long d_off();

        @CField
        short d_reclen();

        @CField
        byte d_type();

        @CFieldAddress
        CCharPointer d_name();
    }

    @CPointerTo(dirent.class)
    public interface direntPointer extends PointerBase {
        public dirent read();

        public void write(dirent value);
    }

    /** File types for `d_type'. */
    @CConstant
    public static native int DT_UNKNOWN();

    @CConstant
    public static native int DT_FIFO();

    @CConstant
    public static native int DT_CHR();

    @CConstant
    public static native int DT_DIR();

    @CConstant
    public static native int DT_BLK();

    @CConstant
    public static native int DT_REG();

    @CConstant
    public static native int DT_LNK();

    @CConstant
    public static native int DT_SOCK();

    @CConstant
    public static native int DT_WHT();

    /**
     * This is the data type of directory stream objects. The actual structure is opaque to users.
     */
    public interface DIR extends PointerBase {
    }

    /**
     * Open a directory stream on NAME. Return a DIR stream on the directory, or NULL if it could
     * not be opened.
     */
    @CFunction
    public static native DIR opendir(CCharPointer name);

    @CFunction(value = "fdopendir", transition = CFunction.Transition.NO_TRANSITION)
    public static native DIR fdopendir_no_transition(int fd);

    /** Same as opendir, but open the stream on the file descriptor FD. */
    @CFunction
    public static native DIR fdopendir(int fd);

    /** Close the directory stream DIRP. Return 0 if successful, -1 if not. */
    @CFunction
    public static native int closedir(DIR dirp);

    @CFunction(value = "closedir", transition = CFunction.Transition.NO_TRANSITION)
    public static native int closedir_no_transition(DIR dirp);

    /**
     * Read a directory entry from DIRP. Return a pointer to a `struct dirent' describing the entry,
     * or NULL for EOF or error. The storage returned may be overwritten by a later readdir call on
     * the same DIR stream.
     */
    @CFunction
    public static native dirent readdir(DIR dirp);

    /** Reentrant version of `readdir'. Return in RESULT a pointer to the next entry. */
    @CFunction
    public static native int readdir_r(DIR dirp, dirent entry, direntPointer result);

    @CFunction(value = "readdir_r", transition = CFunction.Transition.NO_TRANSITION)
    public static native int readdir_r_no_transition(DIR dirp, dirent entry, direntPointer result);

    /** Rewind DIRP to the beginning of the directory. */
    @CFunction
    public static native void rewinddir(DIR dirp);

    /** Seek to position POS on DIRP. */
    @CFunction
    public static native void seekdir(DIR dirp, long pos);

    /** Return the current position of DIRP. */
    @CFunction
    public static native long telldir(DIR dirp);

    /** Return the file descriptor used by DIRP. */
    @CFunction
    public static native int dirfd(DIR dirp);

    /** `MAXNAMLEN' is the BSD name for what POSIX calls `NAME_MAX'. */
    @CConstant
    public static native int MAXNAMLEN();

    /**
     * Scan the directory DIR, calling SELECTOR on each directory entry. Entries for which SELECT
     * returns nonzero are individually malloc'd, sorted using qsort with CMP, and collected in a
     * malloc'd array inNAMELIST. Returns the number of entries selected, or -1 on error.
     */
    @CFunction
    public static native int scandir(CCharPointer dir, PointerBase namelist, CFunctionPointer selector, CFunctionPointer cmp);

    /**
     * Similar to `scandir' but a relative DIR name is interpreted relative to the directory for
     * which DFD is a descriptor.
     */
    @CFunction
    public static native int scandirat(int dfd, CCharPointer dir, PointerBase namelist, CFunctionPointer selector, CFunctionPointer cmp);

    /** Function to compare two `struct dirent's alphabetically. */
    @CFunction
    public static native int alphasort(direntPointer e1, direntPointer e2);

    /**
     * Read directory entries from FD into BUF, reading at most NBYTES. Reading starts at offset
     * *BASEP, and *BASEP is updated with the new position after reading. Returns the number of
     * bytes read; zero when at end of directory; or -1 for errors.
     */
    @CFunction
    public static native SignedWord getdirentries(int fd, CCharPointer buf, SignedWord nbytes, PointerBase basep);

    /** Function to compare two `struct dirent's by name & version. */
    @CFunction
    public static native int versionsort(direntPointer e1, direntPointer e2);
}
