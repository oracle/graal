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
package com.oracle.svm.driver;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.regex.Pattern;

import com.oracle.svm.core.OS;
import com.oracle.svm.driver.launcher.configuration.BundleConfigurationParser;
import com.oracle.svm.driver.launcher.json.BundleJSONParserException;

import jdk.graal.compiler.util.json.JsonWriter;

final class BundlePathMap {

    enum PathStyle {
        BundleRelative,
        Unix,
        Windows;

        static PathStyle currentSourceStyle() {
            return OS.WINDOWS.isCurrent() ? Windows : Unix;
        }

        static PathStyle fromBundlePlatform(String platform) {
            return platform.startsWith("windows") ? Windows : Unix;
        }
    }

    record PortablePath(PathStyle style, String text) {
        Path asInternalPath() {
            return Path.of(text);
        }
    }

    private static final String srcField = "src";
    private static final String dstField = "dst";
    private static final String styleField = "style";
    private static final String textField = "text";

    private static final String winPrefix = "win";
    private static final String winRelativePrefix = "win-rel";
    private static final String winDriveRelativePrefix = "win-drive-rel";

    private static final Pattern windowsSeparatorPattern = Pattern.compile("[\\\\/]+");
    private static final Pattern unixSeparatorPattern = Pattern.compile("/+");

    private BundlePathMap() {
    }

    /**
     * Parses a portable path mapping file and registers its entries into the given in-memory path
     * map using the current platform's internal {@link Path} representation.
     */
    static void parseAndRegister(Reader reader, Map<Path, Path> pathMap) throws IOException {
        Object json = new com.oracle.svm.driver.launcher.json.BundleJSONParser(reader).parse();
        for (var rawEntry : BundleConfigurationParser.asList(json, "Expected a list of path substitution objects")) {
            var entry = BundleConfigurationParser.asMap(rawEntry, "Expected a substitution object");
            pathMap.put(parsePortablePath(entry, srcField).asInternalPath(), parsePortablePath(entry, dstField).asInternalPath());
        }
    }

    private static PortablePath parsePortablePath(Map<String, Object> entry, String fieldName) {
        Object rawPath = entry.get(fieldName);
        if (rawPath == null) {
            throw new BundleJSONParserException("Expected " + fieldName + "-field in substitution object");
        }
        Map<String, Object> pathObject = BundleConfigurationParser.asMap(rawPath, "Expected portable path object in field " + fieldName);
        Object rawStyle = pathObject.get(styleField);
        if (rawStyle == null) {
            throw new BundleJSONParserException("Expected " + styleField + "-field in portable path object");
        }
        Object rawText = pathObject.get(textField);
        if (rawText == null) {
            throw new BundleJSONParserException("Expected " + textField + "-field in portable path object");
        }
        try {
            return new PortablePath(PathStyle.valueOf(rawStyle.toString()), rawText.toString());
        } catch (IllegalArgumentException ex) {
            throw new BundleJSONParserException("Unknown portable path style '" + rawStyle + "'");
        }
    }

    static PortablePath sourcePath(Path path, PathStyle sourceStyle) {
        return new PortablePath(sourceStyle, encodeSourcePathText(path.toString(), sourceStyle));
    }

    static PortablePath bundlePath(Path path) {
        return new PortablePath(PathStyle.BundleRelative, encodeBundlePathText(path));
    }

    static Path portableSourcePath(String rawPath, PathStyle sourceStyle) {
        return Path.of(encodeSourcePathText(rawPath, sourceStyle));
    }

    static String sourceFileName(String rawPath, PathStyle sourceStyle) {
        Path fileName = portableSourcePath(rawPath, sourceStyle).getFileName();
        return fileName == null ? rawPath : fileName.toString();
    }

    static boolean isSourceAbsolute(String rawPath, PathStyle sourceStyle) {
        if (rawPath.isEmpty()) {
            return false;
        }
        if (sourceStyle == PathStyle.Windows) {
            String normalized = rawPath.replace('/', '\\');
            if (normalized.startsWith("\\\\") || normalized.startsWith("\\")) {
                return true;
            }
            return normalized.length() >= 3 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':' && normalized.charAt(2) == '\\';
        }
        String normalized = rawPath.replace('\\', '/');
        return normalized.startsWith("/");
    }

    static String toCurrentPlatformRelativePath(String rawPath, PathStyle sourceStyle) {
        if (rawPath.isEmpty()) {
            return rawPath;
        }
        String normalized = sourceStyle == PathStyle.Windows ? rawPath.replace('/', '\\') : rawPath.replace('\\', '/');
        String[] rawSegments = (sourceStyle == PathStyle.Windows ? windowsSeparatorPattern : unixSeparatorPattern).split(normalized);
        List<String> segments = Stream.of(rawSegments).filter(segment -> !segment.isEmpty()).toList();
        if (segments.isEmpty()) {
            return "";
        }
        Path relativePath = Path.of(segments.getFirst(), segments.subList(1, segments.size()).toArray(String[]::new));
        return relativePath.toString();
    }

    /**
     * Encodes a source-platform path string into the portable bundle representation.
     */
    static String encodeSourcePathText(String rawPath, PathStyle sourceStyle) {
        return sourceStyle == PathStyle.Windows ? lowerWindowsPath(rawPath) : rawPath;
    }

    /**
     * Decodes a portable path string back into a native path string when replaying on Windows. On
     * non-Windows platforms the portable form is preserved.
     */
    static String decodeToCurrentPlatformPath(String portablePathText, PathStyle sourceStyle) {
        if (sourceStyle == PathStyle.Windows && OS.WINDOWS.isCurrent()) {
            return restoreWindowsPath(portablePathText);
        }
        return portablePathText;
    }

    /**
     * Prints a path mapping entry using the portable on-disk bundle schema.
     */
    static void printPathMapping(Map.Entry<Path, Path> entry, JsonWriter writer, PathStyle sourceStyle, boolean destinationIsBundleRelative) throws IOException {
        PortablePath srcPath = sourcePath(entry.getKey(), sourceStyle);
        PortablePath dstPath = destinationIsBundleRelative ? bundlePath(entry.getValue()) : sourcePath(entry.getValue(), sourceStyle);
        writer.append('{').quote(srcField).append(':');
        printPortablePath(srcPath, writer);
        writer.append(',').quote(dstField).append(':');
        printPortablePath(dstPath, writer);
        writer.append('}');
    }

    private static void printPortablePath(PortablePath portablePath, JsonWriter writer) throws IOException {
        writer.append('{').quote(styleField).append(':').quote(portablePath.style().name());
        writer.append(',').quote(textField).append(':').quote(portablePath.text());
        writer.append('}');
    }

    private static String encodeBundlePathText(Path path) {
        return StreamSupport.stream(path.spliterator(), false)
                        .map(Path::toString)
                        .collect(Collectors.joining("/"));
    }

    private static String lowerWindowsPath(String rawPath) {
        String normalized = rawPath.replace('/', '\\');
        if (normalized.startsWith("\\\\")) {
            String[] components = windowsSeparatorPattern.split(normalized.substring(2));
            if (components.length >= 2) {
                return "/" + joinSegments(winPrefix, "unc", components);
            }
        }
        if (normalized.length() >= 3 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':' && normalized.charAt(2) == '\\') {
            return "/" + joinSegments(winPrefix, Character.toString(Character.toLowerCase(normalized.charAt(0))), windowsSeparatorPattern.split(normalized.substring(3)));
        }
        if (normalized.length() >= 2 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':') {
            return joinSegments(winDriveRelativePrefix, Character.toString(Character.toLowerCase(normalized.charAt(0))), windowsSeparatorPattern.split(normalized.substring(2)));
        }
        if (normalized.startsWith("\\")) {
            return "/" + joinSegments(winPrefix, "root", windowsSeparatorPattern.split(normalized.substring(1)));
        }
        return joinSegments(winRelativePrefix, windowsSeparatorPattern.split(normalized));
    }

    private static String restoreWindowsPath(String portablePathText) {
        String absoluteWinPrefix = "/" + winPrefix;
        if (portablePathText.startsWith(absoluteWinPrefix + "/unc/")) {
            List<String> segments = List.of(unixSeparatorPattern.split(portablePathText.substring((absoluteWinPrefix + "/unc/").length())));
            if (segments.size() >= 2) {
                return "\\\\" + String.join("\\", segments);
            }
        }
        if (portablePathText.startsWith(absoluteWinPrefix + "/root/")) {
            return "\\" + joinWindowsSegments(unixSeparatorPattern.split(portablePathText.substring((absoluteWinPrefix + "/root/").length())));
        }
        if (portablePathText.startsWith(absoluteWinPrefix + "/")) {
            List<String> segments = List.of(unixSeparatorPattern.split(portablePathText.substring((absoluteWinPrefix + "/").length())));
            if (!segments.isEmpty()) {
                String drive = segments.getFirst();
                String tail = joinWindowsSegments(segments.subList(1, segments.size()).toArray(String[]::new));
                return Character.toUpperCase(drive.charAt(0)) + ":" + (tail.isEmpty() ? "\\" : "\\" + tail);
            }
        }
        if (portablePathText.startsWith(winDriveRelativePrefix + "/")) {
            List<String> segments = List.of(unixSeparatorPattern.split(portablePathText.substring((winDriveRelativePrefix + "/").length())));
            if (!segments.isEmpty()) {
                String drive = segments.getFirst();
                String tail = joinWindowsSegments(segments.subList(1, segments.size()).toArray(String[]::new));
                return Character.toUpperCase(drive.charAt(0)) + ":" + tail;
            }
        }
        if (portablePathText.startsWith(winRelativePrefix + "/")) {
            return joinWindowsSegments(unixSeparatorPattern.split(portablePathText.substring((winRelativePrefix + "/").length())));
        }
        return portablePathText;
    }

    private static String joinSegments(String first, String second, String[] remainder) {
        return joinSegments(first, second) + appendSegments(remainder);
    }

    private static String joinSegments(String first, String[] remainder) {
        return joinSegments(first) + appendSegments(remainder);
    }

    private static String joinSegments(String... segments) {
        return Stream.of(segments).filter(segment -> segment != null && !segment.isEmpty()).collect(Collectors.joining("/"));
    }

    private static String appendSegments(String[] segments) {
        String suffix = Stream.of(segments).filter(segment -> !segment.isEmpty()).collect(Collectors.joining("/"));
        return suffix.isEmpty() ? "" : "/" + suffix;
    }

    private static String joinWindowsSegments(String[] segments) {
        return Stream.of(segments).filter(segment -> !segment.isEmpty()).collect(Collectors.joining("\\"));
    }
}
