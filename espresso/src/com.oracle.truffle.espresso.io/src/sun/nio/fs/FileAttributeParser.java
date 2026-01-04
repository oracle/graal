/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package sun.nio.fs;

import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;

class FileAttributeParser {
    static final int OWNER_READ_VALUE = 256;
    static final int OWNER_WRITE_VALUE = 128;
    static final int OWNER_EXECUTE_VALUE = 64;
    static final int GROUP_READ_VALUE = 32;
    static final int GROUP_WRITE_VALUE = 16;
    static final int GROUP_EXECUTE_VALUE = 8;
    static final int OTHERS_READ_VALUE = 4;
    static final int OTHERS_WRITE_VALUE = 2;
    static final int OTHERS_EXECUTE_VALUE = 1;

    static final int ALL_PERMISSIONS = OWNER_READ_VALUE |
                    OWNER_WRITE_VALUE |
                    OWNER_EXECUTE_VALUE |
                    GROUP_READ_VALUE |
                    GROUP_WRITE_VALUE |
                    GROUP_EXECUTE_VALUE |
                    OTHERS_READ_VALUE |
                    OTHERS_WRITE_VALUE |
                    OTHERS_EXECUTE_VALUE;
    static final int ALL_READWRITE = OWNER_READ_VALUE |
                    OWNER_WRITE_VALUE |
                    GROUP_READ_VALUE |
                    GROUP_WRITE_VALUE |
                    OTHERS_READ_VALUE |
                    OTHERS_WRITE_VALUE;

    static int parseWithDefault(int defaultAttrs, FileAttribute<?>... attrs) {
        if (attrs == null || attrs.length == 0) {
            return defaultAttrs;
        }
        for (FileAttribute<?> attr : attrs) {
            if (attr.name().equals("posix:permissions")) {
                @SuppressWarnings("unchecked")
                Set<PosixFilePermission> perms = (Set<PosixFilePermission>) attr.value();
                return getMaskfromPosix(perms);
            } else {
                throw new UnsupportedOperationException("file attributes: " + Arrays.toString(attrs));
            }
        }
        throw new IllegalStateException("should not reach here");
    }

    private static int getMaskfromPosix(Set<PosixFilePermission> perms) {
        int mask = 0;
        for (PosixFilePermission perm : perms) {
            switch (perm) {
                case OWNER_READ:
                    mask |= OWNER_READ_VALUE;
                    break;
                case OWNER_WRITE:
                    mask |= OWNER_WRITE_VALUE;
                    break;
                case OWNER_EXECUTE:
                    mask |= OWNER_EXECUTE_VALUE;
                    break;
                case GROUP_READ:
                    mask |= GROUP_READ_VALUE;
                    break;
                case GROUP_WRITE:
                    mask |= GROUP_WRITE_VALUE;
                    break;
                case GROUP_EXECUTE:
                    mask |= GROUP_EXECUTE_VALUE;
                    break;
                case OTHERS_READ:
                    mask |= OTHERS_READ_VALUE;
                    break;
                case OTHERS_WRITE:
                    mask |= OTHERS_WRITE_VALUE;
                    break;
                case OTHERS_EXECUTE:
                    mask |= OTHERS_EXECUTE_VALUE;
                    break;
            }
        }
        return mask;
    }
}
