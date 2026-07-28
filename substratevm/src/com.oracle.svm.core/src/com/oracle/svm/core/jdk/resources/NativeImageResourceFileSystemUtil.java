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
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;

import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.shared.util.VMError;

public final class NativeImageResourceFileSystemUtil {

    /// Separator used in the serialized content of directory resources. This is an internal image
    /// format and must not depend on the image build host's platform line separator.
    public static final String DIRECTORY_CONTENT_SEPARATOR = "\n";

    private NativeImageResourceFileSystemUtil() {
    }

    public static String formatRootedResourcePath(int rootId, String resourceName) {
        return formatRootedResourcePathFromAbsolute(rootId, "/" + resourceName);
    }

    /// Formats the path component used by resource URLs and URIs when the caller already has an
    /// absolute resource path. For example, root ID `2` and resource path `/META-INF/services/A`
    /// become `/2!/META-INF/services/A`.
    static String formatRootedResourcePathFromAbsolute(int rootId, String absoluteResourcePath) {
        validateRootId(rootId);
        if (absoluteResourcePath == null || absoluteResourcePath.isEmpty() || absoluteResourcePath.charAt(0) != '/') {
            throw new IllegalArgumentException("Rooted resource paths require an absolute resource path.");
        }
        return "/" + rootId + "!" + absoluteResourcePath;
    }

    /// Returns the `/<root-id>!` prefix from a rooted resource path, or `null` if the path does not
    /// have the rooted-resource form. This nullable probe is used for URL context parsing, where an
    /// invalid current path should simply not participate in root preservation.
    public static String rootedResourcePathPrefix(String path) {
        int bang = rootedResourcePathSeparator(path);
        if (bang < 0) {
            return null;
        }
        try {
            validateRootId(Integer.parseInt(path.substring(1, bang)));
            return path.substring(0, bang + 1);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /// Parses a rooted resource URL or URI path of the form `/<root-id>!/<resource-path>`. The
    /// returned resource name is in canonical resource form without a leading slash.
    static RootedResourcePath parseRootedResourcePath(String path, String kind, Object source) {
        int bang = rootedResourcePathSeparator(path);
        if (bang < 0) {
            throw new IllegalArgumentException("Resource " + kind + " path must have form /<root-id>!/<resource-path>: " + source);
        }
        return new RootedResourcePath(parseRootId(path.substring(1, bang), kind, source), path.substring(bang + 2));
    }

    /// Finds the `!` separator in a structurally rooted resource path. This intentionally does not
    /// validate that the root ID is numeric, so strict parsers can report contextual errors.
    private static int rootedResourcePathSeparator(String path) {
        int bang = path != null ? path.indexOf('!') : -1;
        if (path == null || path.isEmpty() || path.charAt(0) != '/' || bang <= 1 || bang == path.length() - 1 || path.charAt(bang + 1) != '/') {
            return -1;
        }
        return bang;
    }

    /// Parses and validates a root ID while preserving whether the caller was parsing a URL or URI
    /// for error reporting.
    private static int parseRootId(String rootId, String kind, Object source) {
        try {
            int parsedRootId = Integer.parseInt(rootId);
            if (parsedRootId < 0) {
                throw new IllegalArgumentException("Resource " + kind + " root id must be a non-negative integer: " + source);
            }
            return parsedRootId;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Resource " + kind + " root id must be a non-negative integer: " + source, e);
        }
    }

    private static void validateRootId(int rootId) {
        if (rootId < 0) {
            throw new IllegalArgumentException("Resource root id must be a non-negative integer.");
        }
    }

    record RootedResourcePath(int rootId, String resourceName) {
        RootedResourcePath {
            validateRootId(rootId);
        }
    }

    public static ResourceStorageEntryBase getEntry(String resourcePath, boolean probe) {
        return getEntry(null, resourcePath, probe);
    }

    public static ResourceStorageEntryBase getEntry(String moduleName, String resourcePath, boolean probe) {
        Module module = findModule(moduleName);
        if (ClassRegistries.respectClassLoader()) {
            int separator = resourcePath.indexOf('/');
            if (separator <= 0 || separator == resourcePath.length() - 1) {
                throw new IllegalArgumentException("Loader-aware resource paths require a loader key and resource name.");
            }
            String resourceHost = resourcePath.substring(0, separator);
            String resourceName = resourcePath.substring(separator + 1);
            return Resources.getAtRuntime(resourceHost, module, resourceName, probe);
        }
        return Resources.getAtRuntime(module, resourcePath, probe);
    }

    private static Module findModule(String moduleName) {
        return Optional.ofNullable(moduleName).flatMap(ModuleLayer.boot()::findModule).orElse(null);
    }

    public static byte[] getBytes(String moduleName, String resourcePath, int resourceIndex, boolean readOnly) {
        Object entry = getEntry(moduleName, resourcePath, false);
        if (entry == null) {
            return new byte[0];
        }
        byte[][] data = ((ResourceStorageEntry) entry).getData();
        if (resourceIndex < 0 || resourceIndex >= data.length) {
            throw new IllegalArgumentException("Resource index " + resourceIndex + " is out of bounds for resource path '" + resourcePath + "'.");
        }
        byte[] bytes = data[resourceIndex];
        if (readOnly) {
            return bytes;
        } else {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }

    public static String toRegexPattern(String globPattern) {
        return Target_jdk_nio_zipfs_ZipUtils.toRegexPattern(globPattern);
    }

    public static byte[] inputStreamToByteArray(InputStream is) {
        try {
            return is.readAllBytes();
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
