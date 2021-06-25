/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.resources;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class NativeImageResourceFileAttributesView implements BasicFileAttributeView {

    private enum AttributeID {
        size,
        creationTime,
        lastAccessTime,
        lastModifiedTime,
        isDirectory,
        isRegularFile,
        isSymbolicLink,
        isOther,
        fileKey;

        private static final Set<String> attributeValues = new HashSet<>();

        static {
            for (AttributeID choice : AttributeID.values()) {
                attributeValues.add(choice.name());
            }
        }

        public static boolean contains(String value) {
            return attributeValues.contains(value);
        }
    }

    private final NativeImageResourcePath path;
    private final boolean isBasic;

    public NativeImageResourceFileAttributesView(NativeImageResourcePath path, boolean isBasic) {
        this.path = path;
        this.isBasic = isBasic;
    }

    @SuppressWarnings("unchecked")
    static <V extends FileAttributeView> V get(NativeImageResourcePath path, Class<V> type) {
        if (type == null) {
            throw new NullPointerException();
        }
        if (type == BasicFileAttributeView.class) {
            return (V) new NativeImageResourceFileAttributesView(path, true);
        }
        if (type == NativeImageResourceFileAttributesView.class) {
            return (V) new NativeImageResourceFileAttributesView(path, false);
        }
        return null;
    }

    static NativeImageResourceFileAttributesView get(NativeImageResourcePath path, String type) {
        if (type == null) {
            throw new NullPointerException();
        }
        if (type.equals("basic")) {
            return new NativeImageResourceFileAttributesView(path, true);
        }
        if (type.equals("resource")) {
            return new NativeImageResourceFileAttributesView(path, false);
        }
        return null;
    }

    @Override
    public String name() {
        return isBasic ? "basic" : "resource";
    }

    @Override
    public NativeImageResourceFileAttributes readAttributes() throws IOException {
        return path.getAttributes();
    }

    public void setAttribute(String attribute, Object value) throws IOException {
        try {
            if (AttributeID.valueOf(attribute) == AttributeID.lastModifiedTime) {
                setTimes((FileTime) value, null, null);
            }
            if (AttributeID.valueOf(attribute) == AttributeID.lastAccessTime) {
                setTimes(null, (FileTime) value, null);
            }
            if (AttributeID.valueOf(attribute) == AttributeID.creationTime) {
                setTimes(null, null, (FileTime) value);
            }
        } catch (IllegalArgumentException x) {
            throw new UnsupportedOperationException("'" + attribute + "' is unknown or read-only attribute");
        }
    }

    Map<String, Object> readAttributes(String attributes) throws IOException {
        NativeImageResourceFileAttributes nativeImageResourceFileAttributes = readAttributes();
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        if ("*".equals(attributes)) {
            for (AttributeID id : AttributeID.values()) {
                map.put(id.name(), attribute(id, nativeImageResourceFileAttributes));
            }
        } else {
            String[] as = attributes.split(",");
            for (String a : as) {
                if (AttributeID.contains(a)) {
                    map.put(a, attribute(AttributeID.valueOf(a), nativeImageResourceFileAttributes));
                }
            }
        }
        return map;
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        path.setTimes(lastModifiedTime, lastAccessTime, createTime);
    }

    Object attribute(AttributeID id, NativeImageResourceFileAttributes nativeImageResourceFileAttributes) {
        switch (id) {
            case size:
                return nativeImageResourceFileAttributes.size();
            case creationTime:
                return nativeImageResourceFileAttributes.creationTime();
            case lastAccessTime:
                return nativeImageResourceFileAttributes.lastAccessTime();
            case lastModifiedTime:
                return nativeImageResourceFileAttributes.lastModifiedTime();
            case isDirectory:
                return nativeImageResourceFileAttributes.isDirectory();
            case isRegularFile:
                return nativeImageResourceFileAttributes.isRegularFile();
            case isSymbolicLink:
                return nativeImageResourceFileAttributes.isSymbolicLink();
            case isOther:
                return nativeImageResourceFileAttributes.isOther();
            case fileKey:
                return nativeImageResourceFileAttributes.fileKey();
        }
        return null;
    }
}
