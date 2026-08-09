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
package com.oracle.svm.core.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/// Regenerates the `LogTagSet` and `LogTag` enum constant sections.
///
/// This class intentionally depends only on the JDK, so the Java source launcher
/// can run it without any SVM classes being compiled. The list below is the
/// golden list of tag sets; update it when a tag set is added or removed.
public final class LogTagSetGenerator {
    private static final String[] LOG_TAG_SET_NAMES = {
                    "class_init",
                    "class_load",
                    "class_load_cause",
                    "class_load_cause_native",
                    "class_load_image",
                    "gc",
                    "jfr",
                    "jfr_dcmd",
                    "jfr_event",
                    "jfr_metadata",
                    "jfr_methodtrace",
                    "jfr_oldobject_sampling",
                    "jfr_periodic",
                    "jfr_setting",
                    "jfr_start",
                    "jfr_startup",
                    "jfr_system",
                    "jfr_system_bytecode",
                    "jfr_system_event",
                    "jfr_system_metadata",
                    "jfr_system_parser",
                    "jfr_system_periodic",
                    "jfr_system_sampling",
                    "jfr_system_setting",
                    "jfr_system_streaming",
                    "jfr_system_throttle",
                    "logging",
                    "module_load",
                    "module_load_image",
                    "safepoint"
    };

    private static final Set<String> JAVA_KEYWORDS = Set.of(
                    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
                    "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
                    "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected",
                    "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
                    "transient", "try", "void", "volatile", "while", "true", "false", "null");

    private LogTagSetGenerator() {
    }

    /// Ensures [LogTagSet] and [LogTag] are in sync with [#LOG_TAG_SET_NAMES].
    ///
    /// Exit code is the number of files updated.
    public static void main(String[] args) throws IOException {
        Path thisSourcefile = Path.of(System.getProperty("jdk.launcher.sourcefile"));
        Path logTagSetPath = thisSourcefile.resolveSibling("LogTagSet.java");
        Path logTagPath = thisSourcefile.resolveSibling("LogTag.java");
        if (!Files.isRegularFile(logTagSetPath)) {
            throw new IllegalArgumentException("LogTagSet.java does not exist: " + logTagSetPath);
        }
        if (!Files.isRegularFile(logTagPath)) {
            throw new IllegalArgumentException("LogTag.java does not exist: " + logTagPath);
        }

        int changed = 0;
        if (writeGeneratedSection(logTagSetPath, LOG_TAG_SET_NAMES)) {
            System.out.println("Updated " + logTagSetPath);
            changed++;
        }
        if (writeGeneratedSection(logTagPath, componentTagNames())) {
            System.out.println("Updated " + logTagPath);
            changed++;
        }
        System.exit(changed);
    }

    /// Derives the distinct component tags in stable sorted order from the golden tag-set list.
    private static String[] componentTagNames() {
        Set<String> componentTags = new TreeSet<>();
        for (String tagSetName : LOG_TAG_SET_NAMES) {
            String externalName = tagSetName.endsWith("_") ? tagSetName.substring(0, tagSetName.length() - 1) : tagSetName;
            if (!externalName.equals("_no_tag")) {
                for (String component : externalName.split("_")) {
                    componentTags.add(toEnumConstantName(component));
                }
            }
        }
        return componentTags.toArray(String[]::new);
    }

    /// Escapes a component that is a Java keyword for use as an enum constant.
    private static String toEnumConstantName(String component) {
        return JAVA_KEYWORDS.contains(component) ? component + "_" : component;
    }

    /// Writes generated constants between the markers and preserves all other source text.
    ///
    /// @return true if `path` was updated
    private static boolean writeGeneratedSection(Path path, String[] names) throws IOException {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        String updatedSource = replaceGeneratedSection(source, names, path);
        if (!source.equals(updatedSource)) {
            Files.writeString(path, updatedSource, StandardCharsets.UTF_8);
            return true;
        }
        return false;
    }

    /// Replaces exactly one marked section in a source file.
    private static String replaceGeneratedSection(String source, String[] names, Path path) {
        String startMarker = "// START GENERATED";
        String endMarker = "// END GENERATED";
        int start = source.indexOf(startMarker);
        int end = start < 0 ? -1 : source.indexOf(endMarker, start + startMarker.length());
        int startLineEnd = start < 0 ? -1 : source.indexOf('\n', start);
        int endLineStart = end < 0 ? -1 : source.lastIndexOf('\n', end) + 1;
        if (start < 0 || end < 0 || startLineEnd < 0 || endLineStart <= startLineEnd ||
                        source.indexOf(startMarker, start + startMarker.length()) >= 0 ||
                        source.indexOf(endMarker, end + endMarker.length()) >= 0) {
            throw new IllegalArgumentException(path + " must contain one generated section");
        }

        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder generated = new StringBuilder();
        for (int index = 0; index < names.length; index++) {
            generated.append("    ").append(names[index]).append(index + 1 == names.length ? ';' : ',').append(newline);
        }
        return source.substring(0, startLineEnd + 1) + generated + source.substring(endLineStart);
    }
}
