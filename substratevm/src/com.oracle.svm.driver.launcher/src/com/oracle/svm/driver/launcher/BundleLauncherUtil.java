/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver.launcher;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.oracle.svm.driver.launcher.configuration.BundleConfigurationParser;
import com.oracle.svm.driver.launcher.json.BundleJSONParser;

public class BundleLauncherUtil {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final char UNMAPPABLE_CHARACTER_REPLACEMENT = '\uFFFD';

    /// Materializes slash-separated portable path text on the current filesystem.
    /// Literal backslashes remain part of a component on Unix; on Windows they become U+FFFD replacement characters
    /// so materialization does not turn one portable component into multiple native components.
    public static Path materializePortablePath(String pathText) {
        return Path.of(File.separatorChar == '\\' ? pathText.replace('\\', UNMAPPABLE_CHARACTER_REPLACEMENT) : pathText);
    }

    /// Uses recorded entry roots rather than walking captured directory contents. Numbered entries
    /// retain their allocation order, while flat entries from older bundles remain supported.
    public static List<Path> getPathEntries(Path root, Path pathDir) throws IOException {
        List<Path> entries = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(root.resolve("input/stage/path_substitutions.json"))) {
            Object json = new BundleJSONParser(reader).parse();
            for (Object rawEntry : BundleConfigurationParser.asList(json, "Expected a list of path substitutions")) {
                var entry = BundleConfigurationParser.asMap(rawEntry, "Expected a path substitution object");
                Object destination = entry.get("dst");
                Object text = destination instanceof String ? destination : BundleConfigurationParser.asMap(destination, "Expected a destination path object").get("text");
                if (text instanceof String pathText) {
                    Path path = root.resolve(materializePortablePath(pathText)).normalize();
                    if (path.startsWith(pathDir) && !path.equals(pathDir) && Files.exists(path)) {
                        entries.add(path);
                    }
                }
            }
        }
        // Older flat-layout bundles can lose an input mapping when its source is also the output
        // directory. Numbered slots have recorded roots; retain other direct children as flat entries.
        try (Stream<Path> children = Files.list(pathDir)) {
            for (Path child : children.toList()) {
                if (entries.stream().noneMatch(path -> path.startsWith(child))) {
                    entries.add(child);
                }
            }
        }
        return entries.stream().distinct().sorted(Comparator.<Path> comparingInt(path -> {
            Path relative = pathDir.relativize(path);
            return relative.getNameCount() == 1 ? -1 : Integer.parseInt(relative.getName(0).toString());
        }).thenComparing(Path::toString)).toList();
    }

    static String digest(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(value.getBytes(StandardCharsets.UTF_8));
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new Error(ex);
        }
    }

    static String toHex(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(HEX[(b >> 4) & 0xf]);
            r.append(HEX[b & 0xf]);
        }
        return r.toString();
    }

    private static final Pattern SAFE_SHELL_ARG = Pattern.compile("[A-Za-z0-9@%_\\-+=:,./]+");

    static String quoteShellArg(String arg) {
        if (arg.isEmpty()) {
            return "''";
        }
        Matcher m = SAFE_SHELL_ARG.matcher(arg);
        if (m.matches()) {
            return arg;
        }
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }
}
