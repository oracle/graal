/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This file must be compatible with 21+.
 */
final class TruffleBasicFileAttributeView implements BasicFileAttributeView {

    private final TrufflePath path;
    private final boolean followLinks;

    TruffleBasicFileAttributeView(TrufflePath path, boolean followLinks) {
        this.path = Objects.requireNonNull(path);
        this.followLinks = followLinks;
    }

    @Override
    public String name() {
        return "basic";
    }

    @Override
    public BasicFileAttributes readAttributes() throws IOException {
        return readAttributes0(path, followLinks);
    }

    static long toMillis(FileTime ft) {
        if (ft == null) {
            return -1L;
        }
        return ft.toMillis();
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        setTimes0(path, followLinks, toMillis(lastModifiedTime), toMillis(lastAccessTime), toMillis(createTime));
    }

    Map<String, Object> readAttributes(String attributes) throws IOException {
        TruffleBasicFileAttributes bfa = (TruffleBasicFileAttributes) readAttributes();
        List<String> queriedAttributes = "*".equals(attributes)
                        ? TruffleBasicFileAttributes.BASIC_ATTRIBUTES
                        : List.of(attributes.split(","));

        HashMap<String, Object> map = new HashMap<>();
        for (String attributeName : queriedAttributes) {
            try {
                map.put(attributeName, bfa.getAttribute(attributeName));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return map;
    }

    void setAttribute(String attribute, Object value)
                    throws IOException {
        try {
            switch (attribute) {
                case "lastModifiedTime":
                    setTimes((FileTime) value, null, null);
                    break;
                case "lastAccessTime":
                    setTimes(null, (FileTime) value, null);
                    break;
                case "creationTime":
                    setTimes(null, null, (FileTime) value);
                    break;
            }
        } catch (IllegalArgumentException x) {
            throw new UnsupportedOperationException("'" + attribute +
                            "' is unknown or read-only attribute");
        }
    }

    // region native methods

    private static native TruffleBasicFileAttributes readAttributes0(TrufflePath path, boolean followLinks) throws IOException;

    /*
     * Updates any or all of the file's last modified time, last access time, and create time
     * attributes. This method updates the file's timestamp attributes. -1 values are ignored e.g.
     * not set.
     */
    private static native void setTimes0(TrufflePath path, boolean followLinks, long lastModifiedTimeMillis, long lastAccessTimeMillis, long createTimeMillis) throws IOException;

    // endregion native methods
}
