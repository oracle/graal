/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

/** Bidirectional source path mappings between a DAP client and the guest runtime. */
final class PathMappings {

    static final PathMappings EMPTY = new PathMappings(Collections.emptyList());

    private final List<Mapping> mappings;

    private PathMappings(List<Mapping> mappings) {
        this.mappings = mappings;
    }

    static PathMappings parse(Object pathMappings, Object localRoot, Object remoteRoot) {
        List<Mapping> mappings = new ArrayList<>();
        if (pathMappings instanceof JSONArray) {
            JSONArray array = (JSONArray) pathMappings;
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject mapping = (JSONObject) item;
                    add(mappings, string(mapping.opt("localRoot")), string(mapping.opt("remoteRoot")), false);
                }
            }
        } else if (pathMappings instanceof JSONObject) {
            JSONObject object = (JSONObject) pathMappings;
            for (Iterator<String> keys = object.keys(); keys.hasNext();) {
                String runtimeRoot = keys.next();
                add(mappings, string(object.opt(runtimeRoot)), runtimeRoot, false);
            }
        }
        // Add this last for stable ordering. Its marker gives it precedence over an equally
        // specific pathMappings entry in either translation direction.
        add(mappings, string(localRoot), string(remoteRoot), true);
        return mappings.isEmpty() ? EMPTY : new PathMappings(Collections.unmodifiableList(mappings));
    }

    String toRuntime(String clientPath) {
        return translate(clientPath, true);
    }

    String toClient(String runtimePath) {
        return translate(runtimePath, false);
    }

    boolean hasMappings() {
        return !mappings.isEmpty();
    }

    boolean isMapped(String runtimePath) {
        if (runtimePath == null) {
            return false;
        }
        for (Mapping mapping : mappings) {
            if (matchesRoot(runtimePath, mapping.remoteRoot)) {
                return true;
            }
        }
        return false;
    }

    private String translate(String path, boolean toRuntime) {
        if (path == null) {
            return null;
        }
        Mapping best = null;
        String bestRoot = null;
        for (Mapping mapping : mappings) {
            String sourceRoot = toRuntime ? mapping.localRoot : mapping.remoteRoot;
            if (matchesRoot(path, sourceRoot) && (bestRoot == null || sourceRoot.length() > bestRoot.length() ||
                            sourceRoot.length() == bestRoot.length() && mapping.direct && !best.direct)) {
                best = mapping;
                bestRoot = sourceRoot;
            }
        }
        if (best == null) {
            return path;
        }
        String targetRoot = toRuntime ? best.remoteRoot : best.localRoot;
        return replaceRoot(path, bestRoot, targetRoot, toRuntime ? best.remoteSeparator : best.localSeparator);
    }

    private static boolean matchesRoot(String path, String root) {
        if (isWindowsRoot(root)) {
            // The client and runtime may use different operating systems. Normalize only for
            // comparison, preserving the configured target root and the suffix's case.
            String normalizedPath = path.replace('\\', '/');
            String normalizedRoot = root.replace('\\', '/');
            if (!normalizedPath.regionMatches(true, 0, normalizedRoot, 0, root.length())) {
                return false;
            }
        } else if (!path.startsWith(root)) {
            return false;
        }
        return path.length() == root.length() || isSeparator(root.charAt(root.length() - 1)) || isSeparator(path.charAt(root.length()));
    }

    private static boolean isWindowsRoot(String root) {
        if (root.length() >= 3 && root.charAt(1) == ':' && isSeparator(root.charAt(2))) {
            char drive = root.charAt(0);
            return drive >= 'A' && drive <= 'Z' || drive >= 'a' && drive <= 'z';
        }
        return root.startsWith("\\\\") || root.startsWith("//");
    }

    private static String replaceRoot(String path, String sourceRoot, String targetRoot, char targetSeparator) {
        String suffix = path.substring(sourceRoot.length());
        int first = 0;
        while (first < suffix.length() && isSeparator(suffix.charAt(first))) {
            first++;
        }
        if (first == suffix.length()) {
            return targetRoot;
        }
        suffix = suffix.substring(first).replace('/', targetSeparator).replace('\\', targetSeparator);
        if (isSeparator(targetRoot.charAt(targetRoot.length() - 1))) {
            return targetRoot + suffix;
        }
        return targetRoot + targetSeparator + suffix;
    }

    private static void add(List<Mapping> mappings, String localRoot, String remoteRoot, boolean direct) {
        if (localRoot != null && remoteRoot != null && !localRoot.isEmpty() && !remoteRoot.isEmpty()) {
            mappings.add(new Mapping(localRoot, remoteRoot, direct));
        }
    }

    private static String string(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static String normalizeRoot(String root) {
        int minimumLength = root.startsWith("/") || root.startsWith("\\") ? 1 : 0;
        if (root.length() >= 3 && root.charAt(1) == ':' && isSeparator(root.charAt(2))) {
            minimumLength = 3;
        }
        int end = root.length();
        while (end > minimumLength && isSeparator(root.charAt(end - 1))) {
            end--;
        }
        return root.substring(0, end);
    }

    private static char separator(String root) {
        int slash = root.lastIndexOf('/');
        int backslash = root.lastIndexOf('\\');
        return backslash > slash ? '\\' : '/';
    }

    private static boolean isSeparator(char c) {
        return c == '/' || c == '\\';
    }

    private static final class Mapping {

        final String localRoot;
        final String remoteRoot;
        final char localSeparator;
        final char remoteSeparator;
        final boolean direct;

        Mapping(String localRoot, String remoteRoot, boolean direct) {
            this.localSeparator = separator(localRoot);
            this.remoteSeparator = separator(remoteRoot);
            this.localRoot = normalizeRoot(localRoot);
            this.remoteRoot = normalizeRoot(remoteRoot);
            this.direct = direct;
        }
    }
}
