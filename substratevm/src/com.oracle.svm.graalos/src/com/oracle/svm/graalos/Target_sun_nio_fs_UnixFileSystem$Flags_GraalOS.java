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

import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;

/**
 * A compact representation of {@code CopyOption[]}. This is a copy of the corresponding class in
 * JDK, with the following fields omitted from the factory methods:
 * {@code copyPosixAttributes, copyNonPosixAttributes, failIfUnableToCopyBasic, failIfUnableToCopyPosix, failIfUnableToCopyNonPosix}.
 * <p>
 * The first three of these fields would normally be set to {@code true} in some cases by the
 * factory methods. Leaving them out ensures they are always {@code false}.
 * <p>
 * The last two fields are never set by JDK itself. They are normally always {@code false} and this
 * substitution has no effect on them.
 */
@TargetClass(className = "sun.nio.fs.UnixFileSystem$Flags", onlyWith = GraalOSEnabled.class)
final class Target_sun_nio_fs_UnixFileSystem$Flags_GraalOS {
    @Alias boolean replaceExisting;
    @Alias boolean atomicMove;
    @Alias boolean followLinks;
    @Alias boolean interruptible;
    @Alias boolean copyBasicAttributes;

    @Substitute
    static Target_sun_nio_fs_UnixFileSystem$Flags_GraalOS fromCopyOptions(CopyOption... options) {
        Target_sun_nio_fs_UnixFileSystem$Flags_GraalOS flags = new Target_sun_nio_fs_UnixFileSystem$Flags_GraalOS();
        flags.followLinks = true;
        for (CopyOption option : options) {
            if (option == StandardCopyOption.REPLACE_EXISTING) {
                flags.replaceExisting = true;
                continue;
            }
            if (option == LinkOption.NOFOLLOW_LINKS) {
                flags.followLinks = false;
                continue;
            }
            if (option == StandardCopyOption.COPY_ATTRIBUTES) {
                flags.copyBasicAttributes = true;
                continue;
            }
            if (Target_sun_nio_fs_ExtendedOptions_GraalOS.INTERRUPTIBLE.matches(option)) {
                flags.interruptible = true;
                continue;
            }
            if (option == null)
                throw new NullPointerException();
            throw new UnsupportedOperationException("Unsupported copy option: " + option);
        }
        return flags;
    }

    @Substitute
    static Target_sun_nio_fs_UnixFileSystem$Flags_GraalOS fromMoveOptions(CopyOption... options) {
        Target_sun_nio_fs_UnixFileSystem$Flags_GraalOS flags = new Target_sun_nio_fs_UnixFileSystem$Flags_GraalOS();
        for (CopyOption option : options) {
            if (option == StandardCopyOption.ATOMIC_MOVE) {
                flags.atomicMove = true;
                continue;
            }
            if (option == StandardCopyOption.REPLACE_EXISTING) {
                flags.replaceExisting = true;
                continue;
            }
            if (option == LinkOption.NOFOLLOW_LINKS) {
                // ignore
                continue;
            }
            if (option == null)
                throw new NullPointerException();
            throw new UnsupportedOperationException("Unsupported option: " + option);
        }

        flags.copyBasicAttributes = true;
        return flags;
    }
}
