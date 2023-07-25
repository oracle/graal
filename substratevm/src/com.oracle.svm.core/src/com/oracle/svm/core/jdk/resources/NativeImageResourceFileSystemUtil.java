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

import java.io.InputStream;
import java.util.Arrays;

import com.oracle.svm.core.jdk.Resources;

public final class NativeImageResourceFileSystemUtil {

    private NativeImageResourceFileSystemUtil() {
    }

    public static byte[] getBytes(String resourceName, boolean readOnly) {
        Object entry = Resources.singleton().get(resourceName, true);
        if (entry == null) {
            return new byte[0];
        }
        byte[] bytes = ((ResourceStorageEntry) entry).getData().get(0);
        if (readOnly) {
            return bytes;
        } else {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }

    public static int getSize(String resourceName) {
        Object entry = Resources.singleton().get(resourceName, true);
        if (entry == null) {
            return 0;
        } else {
            return ((ResourceStorageEntry) entry).getData().get(0).length;
        }
    }

    public static String toRegexPattern(String globPattern) {
        return Target_jdk_nio_zipfs_ZipUtils.toRegexPattern(globPattern);
    }

    public static byte[] inputStreamToByteArray(InputStream is) {
        return Resources.inputStreamToByteArray(is);
    }
}
