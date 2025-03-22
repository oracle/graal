/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.util.Arrays;

/**
 * The representation of a resource path in Native Image. This class is used by SVM core to
 * implement a {@link java.nio.file.Path}. The representation is defined here separately so that
 * non-core packages can perform path normalization without depending on the implementation in core.
 * <p>
 * Most of the code from this class is a copy of jdk.nio.zipfs.ZipPath with small tweaks. The main
 * reason why we cannot reuse this class is that this class is final in its original implementation.
 * </p>
 */
public class NativeImageResourcePathRepresentation {
    protected final byte[] path;
    protected volatile int[] offsets;
    protected byte[] resolved;
    private int hashcode = 0;

    public NativeImageResourcePathRepresentation(byte[] resourcePath, boolean normalized) {
        if (normalized) {
            this.path = resourcePath;
        } else {
            this.path = normalize(resourcePath);
        }
    }

    public static String toCanonicalForm(String resourceName) {
        String withoutTrailingSlash = resourceName.endsWith("/") ? resourceName.substring(0, resourceName.length() - 1) : resourceName;
        NativeImageResourcePathRepresentation path = new NativeImageResourcePathRepresentation(withoutTrailingSlash.getBytes(StandardCharsets.UTF_8), true);
        return new String(getResolved(path));
    }

    private static byte[] normalize(byte[] resourcePath) {
        if (resourcePath.length == 0) {
            return resourcePath;
        }
        int i = 0;
        for (int j = 0; j < resourcePath.length; j++) {
            int k = resourcePath[j];
            if (k == '\\' || (k == '/' && j == resourcePath.length - 1)) {
                return normalize(resourcePath, j);
            }
            if ((k == '/') && (i == '/')) {
                return normalize(resourcePath, j - 1);
            }
            if (k == 0) {
                throw new InvalidPathException(new String(resourcePath, StandardCharsets.UTF_8), "Path: nul character not allowed");
            }
            i = k;
        }
        return resourcePath;
    }

    private static byte[] normalize(byte[] resourcePath, int index) {
        byte[] arrayOfByte = new byte[resourcePath.length];
        int i = 0;
        while (i < index) {
            arrayOfByte[i] = resourcePath[i];
            i++;
        }
        int j = i;
        int k = 0;
        while (i < resourcePath.length) {
            int m = resourcePath[i++];
            if (m == '\\') {
                m = '/';
            }
            if ((m != '/') || (k != '/')) {
                if (m == 0) {
                    throw new InvalidPathException(new String(resourcePath, StandardCharsets.UTF_8), "Path: nul character not allowed");
                }
                arrayOfByte[j++] = (byte) m;
                k = m;
            }
        }
        if ((j > 1) && (arrayOfByte[j - 1] == '/')) {
            j--;
        }
        return j == arrayOfByte.length ? arrayOfByte : Arrays.copyOf(arrayOfByte, j);
    }

    protected void initOffsets() {
        if (this.offsets == null) {
            int count = 0;
            int index = 0;
            while (index < path.length) {
                byte c = path[index++];
                if (c != '/') {
                    count++;
                    while (index < path.length && path[index] != '/') {
                        index++;
                    }
                }
            }
            int[] result = new int[count];
            count = 0;
            index = 0;
            while (index < path.length) {
                int m = path[index];
                if (m == '/') {
                    index++;
                } else {
                    result[count++] = index++;
                    while (index < path.length && path[index] != '/') {
                        index++;
                    }
                }
            }

            synchronized (this) {
                if (offsets == null) {
                    offsets = result;
                }
            }
        }
    }

    protected static byte[] getResolved(NativeImageResourcePathRepresentation p) {
        int nc = p.getNameCount();
        byte[] path = p.path;
        int[] offsets = p.offsets;
        byte[] to = new byte[path.length];
        int[] lastM = new int[nc];
        int lastMOff = -1;
        int m = 0;
        for (int i = 0; i < nc; i++) {
            int n = offsets[i];
            int len = (i == offsets.length - 1) ? (path.length - n) : (offsets[i + 1] - n - 1);
            if (len == 1 && path[n] == (byte) '.') {
                if (m == 0 && path[0] == '/') { // absolute path
                    to[m++] = '/';
                }
                continue;
            }
            if (len == 2 && path[n] == '.' && path[n + 1] == '.') {
                if (lastMOff >= 0) {
                    m = lastM[lastMOff--];  // retreat
                    continue;
                }
                if (path[0] == '/') {  // "/../xyz" skip
                    if (m == 0) {
                        to[m++] = '/';
                    }
                } else {               // "../xyz" -> "../xyz"
                    if (m != 0 && to[m - 1] != '/') {
                        to[m++] = '/';
                    }
                    while (len-- > 0) {
                        to[m++] = path[n++];
                    }
                }
                continue;
            }
            if (m == 0 && path[0] == '/' ||   // absolute path
                            m != 0 && to[m - 1] != '/') {   // not the first name
                to[m++] = '/';
            }
            lastM[++lastMOff] = m;
            while (len-- > 0) {
                to[m++] = path[n++];
            }
        }
        if (m > 1 && to[m - 1] == '/') {
            m--;
        }
        return (m == to.length) ? to : Arrays.copyOf(to, m);
    }

    /**
     * NB: implicit implementation of {@link java.nio.file.Path#getNameCount}.
     */
    public int getNameCount() {
        initOffsets();
        return offsets.length;
    }

    @Override
    public int hashCode() {
        int h = hashcode;
        if (h == 0) {
            hashcode = h = Arrays.hashCode(path);
        }
        return h;
    }
}
