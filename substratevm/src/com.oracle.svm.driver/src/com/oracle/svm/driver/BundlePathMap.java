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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.oracle.svm.core.OS;
import com.oracle.svm.driver.launcher.configuration.BundleConfigurationParser;
import com.oracle.svm.driver.launcher.json.BundleJSONParser;
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

        private char comparisonChar(char value) {
            return this == Windows ? Character.toUpperCase(value) : value;
        }
    }

    enum RootKind {
        Relative(false),
        Absolute(true),
        DriveRelative(false),
        DriveAbsolute(true),
        DirectoryRelative(false),
        UNC(true),
        Unavailable(false);

        private final boolean absolute;

        RootKind(boolean absolute) {
            this.absolute = absolute;
        }

        boolean isAbsolute() {
            return absolute;
        }

        /* `WindowsPath.getParent()` treats every Windows path type except `RELATIVE` as rooted. */
        private boolean hasRoot() {
            return this != Relative;
        }

        /*
         * `WindowsPath.normalize()` prevents `..` from escaping `ABSOLUTE`, `UNC`, and
         * `DIRECTORY_RELATIVE` roots.
         */
        private boolean cannotEscapeRoot() {
            return absolute || this == DirectoryRelative;
        }
    }

    /// A path in the portable on-disk bundle representation. Its style, root kind, and payload text
    /// must remain attached while the path is parsed, mapped, transformed, and serialized.
    ///
    /// A `PortablePath` may be converted to a native {@link Path} only as the final step before
    /// filesystem access. The resulting native path must never be converted back to a
    /// `PortablePath`; paths entering the portable domain from a native path are encoded exactly
    /// once.
    ///
    /// The `BundleRelative`/`Unavailable` sentinel is a substitution destination with no path
    /// payload. It records an input that must remain unavailable on every replay; it must never
    /// participate in path operations or be materialized as the original source location.
    static final class PortablePath {

        static final PortablePath UNAVAILABLE = new PortablePath(PathStyle.BundleRelative, RootKind.Unavailable, "");

        private final PathStyle style;
        private final RootKind kind;
        private final String text;

        private final PathComponents components;

        private PortablePath(PathStyle style, RootKind kind, String text) {
            this(style, kind, PathComponents.parse(style, kind, text));
        }

        private PortablePath(PathStyle style, RootKind kind, PathComponents components) {
            this.style = style;
            this.kind = kind;
            this.text = components.payloadText();
            this.components = components;
        }

        static PortablePath parseLegacy(PathStyle style, String text) {
            List<String> components = PathComponents.split(text);
            if (style == PathStyle.BundleRelative) {
                return new PortablePath(style, RootKind.Relative, new PathComponents("", components));
            }
            if (style == PathStyle.Unix) {
                return new PortablePath(style, text.startsWith("/") ? RootKind.Absolute : RootKind.Relative, new PathComponents("", components));
            }
            if (text.startsWith("/")) {
                if (components.size() < 2 || !winPrefix.equals(components.get(0))) {
                    throw PathComponents.malformedLegacyWindows(text);
                }
                String root = components.get(1);
                if ("unc".equals(root)) {
                    if (components.size() < 4) {
                        throw PathComponents.malformedLegacyWindows(text);
                    }
                    return new PortablePath(style, RootKind.UNC, new PathComponents(String.join("/", components.subList(2, 4)), components.subList(4, components.size())));
                }
                if ("root".equals(root)) {
                    return new PortablePath(style, RootKind.DirectoryRelative, new PathComponents("", components.subList(2, components.size())));
                }
                if (PathComponents.isEncodedDrive(root)) {
                    return new PortablePath(style, RootKind.DriveAbsolute, new PathComponents(root, components.subList(2, components.size())));
                }
                throw PathComponents.malformedLegacyWindows(text);
            }
            if (!components.isEmpty() && winDriveRelativePrefix.equals(components.get(0))) {
                if (components.size() < 2 || !PathComponents.isEncodedDrive(components.get(1))) {
                    throw PathComponents.malformedLegacyWindows(text);
                }
                return new PortablePath(style, RootKind.DriveRelative, new PathComponents(components.get(1), components.subList(2, components.size())));
            }
            if (!components.isEmpty() && winRelativePrefix.equals(components.get(0))) {
                return new PortablePath(style, RootKind.Relative, new PathComponents("", components.subList(1, components.size())));
            }
            throw PathComponents.malformedLegacyWindows(text);
        }

        public static PortablePath parseSource(PathStyle style, String rawPath) {
            if (style == PathStyle.BundleRelative) {
                throw new IllegalArgumentException("Expected a source path style");
            }
            if (style == PathStyle.Unix) {
                RootKind kind = rawPath.startsWith("/") ? RootKind.Absolute : RootKind.Relative;
                return new PortablePath(style, kind, new PathComponents("", PathComponents.split(rawPath)));
            }
            String normalized = rawPath.replace('/', '\\');
            if (normalized.startsWith("\\\\")) {
                List<String> components = PathComponents.splitWindows(normalized.substring(2));
                if (components.size() < 2) {
                    // An incomplete UNC root must not become a current-drive-relative path.
                    throw new IllegalArgumentException("UNC path must contain a server and a share: " + rawPath);
                }
                String prefix = String.join("/", components.subList(0, 2));
                return new PortablePath(style, RootKind.UNC, new PathComponents(prefix, components.subList(2, components.size())));
            }
            if (normalized.length() >= 3 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':' && normalized.charAt(2) == '\\') {
                String prefix = Character.toString(Character.toLowerCase(normalized.charAt(0)));
                return new PortablePath(style, RootKind.DriveAbsolute, new PathComponents(prefix, PathComponents.splitWindows(normalized.substring(3))));
            }
            if (normalized.length() >= 2 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':') {
                String prefix = Character.toString(Character.toLowerCase(normalized.charAt(0)));
                return new PortablePath(style, RootKind.DriveRelative, new PathComponents(prefix, PathComponents.splitWindows(normalized.substring(2))));
            }
            if (normalized.startsWith("\\")) {
                return new PortablePath(style, RootKind.DirectoryRelative, new PathComponents("", PathComponents.splitWindows(normalized.substring(1))));
            }
            return new PortablePath(style, RootKind.Relative, new PathComponents("", PathComponents.splitWindows(normalized)));
        }

        /// Represents an absolute source path as a file URI without accessing the replay filesystem.
        URI toFileURI() {
            if (!isAbsolute()) {
                throw new IllegalArgumentException("Expected an absolute source path");
            }
            String path = sourcePathText();
            String host = "";
            if (style == PathStyle.Windows) {
                path = path.replace('\\', '/');
                if (kind == RootKind.UNC) {
                    int slash = path.indexOf('/', 2);
                    host = path.substring(2, slash);
                    path = path.substring(slash);
                    // Match WindowsUriSupport.toUri for IPv6 UNC server names.
                    if (host.endsWith(windowsIPv6LiteralSuffix)) {
                        host = host.substring(0, host.length() - windowsIPv6LiteralSuffix.length()).replace('-', ':').replace('s', '%');
                    }
                } else {
                    path = "/" + path;
                }
            }
            try {
                return new URI("file", host, path, null);
            } catch (URISyntaxException e) {
                if (kind != RootKind.UNC) {
                    throw new IllegalArgumentException("Cannot represent source path as a file URI", e);
                }
                // WindowsUriSupport.toUri encodes UNC hosts with reserved characters in the path.
                try {
                    return new URI("file", null, "//" + sourcePathText().replace('\\', '/'), null);
                } catch (URISyntaxException invalidPath) {
                    throw new IllegalArgumentException("Cannot represent UNC path as a file URI", invalidPath);
                }
            }
        }

        static PortablePath parseFileURI(PathStyle style, URI uri) {
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("URI is not absolute");
            }
            if (uri.isOpaque()) {
                throw new IllegalArgumentException("URI is not hierarchical");
            }
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("URI scheme is not \"file\"");
            }
            if (uri.getRawFragment() != null) {
                throw new IllegalArgumentException("URI has a fragment component");
            }
            if (uri.getRawQuery() != null) {
                throw new IllegalArgumentException("URI has a query component");
            }
            String path = uri.getPath();
            if (path.isEmpty()) {
                throw new IllegalArgumentException("URI path component is empty");
            }
            if (style == PathStyle.Unix) {
                if (uri.getRawAuthority() != null) {
                    throw new IllegalArgumentException("URI has an authority component");
                }
            } else if (style == PathStyle.Windows) {
                /* `WindowsUriSupport.fromUri` maps a file URI authority to a UNC server. */
                String authority = uri.getRawAuthority();
                if (authority != null && !authority.isEmpty()) {
                    String host = uri.getHost();
                    if (host == null) {
                        throw new IllegalArgumentException("URI authority component has undefined host");
                    }
                    if (uri.getUserInfo() != null) {
                        throw new IllegalArgumentException("URI authority component has user-info");
                    }
                    if (uri.getPort() != -1) {
                        throw new IllegalArgumentException("URI authority component has port number");
                    }
                    // Match the inverse IPv6 conversion in WindowsUriSupport.fromUri.
                    if (host.startsWith("[")) {
                        host = host.substring(1, host.length() - 1).replace(':', '-').replace('%', 's') + windowsIPv6LiteralSuffix;
                    }
                    path = "\\\\" + host + path;
                } else if (path.length() > 2 && path.charAt(2) == ':') {
                    /* `WindowsUriSupport.fromUri` converts "/c:/foo" to "c:/foo". */
                    path = path.substring(1);
                }
            }
            return parseSource(style, path);
        }

        PathStyle style() {
            return style;
        }

        RootKind kind() {
            return kind;
        }

        String text() {
            return text;
        }

        String sourcePathText() {
            requireAvailable();
            return switch (style) {
                case BundleRelative -> text;
                case Unix -> kind == RootKind.Absolute ? "/" + text : text;
                case Windows -> components.windowsPathText(kind);
            };
        }

        String platformRelativePathText() {
            if (isAbsolute()) {
                throw new IllegalArgumentException("Expected a relative path but got " + this);
            }
            List<String> currentPlatformSegments;
            if (style == PathStyle.Windows) {
                currentPlatformSegments = new ArrayList<>(components.segments());
                if (kind == RootKind.DriveRelative) {
                    String drive = components.prefix().toUpperCase(Locale.ROOT);
                    if (currentPlatformSegments.isEmpty()) {
                        return drive + ":";
                    }
                    currentPlatformSegments.set(0, drive + ":" + currentPlatformSegments.getFirst());
                }
            } else {
                currentPlatformSegments = Stream.of(unixSeparatorPattern.split(text.replace('\\', '/'))).filter(segment -> !segment.isEmpty()).toList();
            }
            if (currentPlatformSegments.isEmpty()) {
                return "";
            }
            Path relativePath = Path.of(currentPlatformSegments.getFirst(), currentPlatformSegments.subList(1, currentPlatformSegments.size()).toArray(String[]::new));
            return relativePath.toString();
        }

        boolean isAbsolute() {
            requireAvailable();
            return kind.isAbsolute();
        }

        PortablePath getFileName() {
            requireAvailable();
            PathComponents fileName = components.fileName();
            return fileName == null ? null : new PortablePath(style, RootKind.Relative, fileName);
        }

        PortablePath getParent() {
            requireAvailable();
            PathComponents parent = components.parent(kind.hasRoot());
            return parent == null ? null : new PortablePath(style, kind, parent);
        }

        /*
         * Keep Windows resolution aligned with `sun.nio.fs.WindowsPath.resolve(Path)`.
         * `Relative`, `DirectoryRelative`, and `DriveRelative` correspond to its `RELATIVE`,
         * `DIRECTORY_RELATIVE`, and `DRIVE_RELATIVE` cases, respectively.
         */
        PortablePath resolve(String otherPath) {
            requireAvailable();
            PortablePath other = parseSource(style, otherPath);
            if (other.kind == RootKind.Relative && other.text.isEmpty()) {
                return this;
            }
            if (other.isAbsolute()) {
                return other;
            }
            if (style != PathStyle.Windows || other.kind == RootKind.Relative) {
                return resolveRelative(other);
            }
            return switch (other.kind) {
                case DirectoryRelative -> resolveWindowsDirectoryRelative(other);
                case DriveRelative -> resolveWindowsDriveRelative(other);
                case Relative, Absolute, DriveAbsolute, UNC, Unavailable -> throw new AssertionError(other.kind);
            };
        }

        /* Resolve an ordinary `RELATIVE` other path as in `WindowsPath`. */
        private PortablePath resolveRelative(PortablePath other) {
            ArrayList<String> resolved = new ArrayList<>(components.segments());
            resolved.addAll(other.components.segments());
            return new PortablePath(style, kind, components.withSegments(resolved));
        }

        /*
         * The `WindowsPath` `DIRECTORY_RELATIVE` case keeps only the base root. Resolving
         * `\\child` against `C:base` therefore produces the drive-absolute `C:\\child`.
         */
        private PortablePath resolveWindowsDirectoryRelative(PortablePath other) {
            return switch (this.kind) {
                case DriveRelative, DriveAbsolute -> new PortablePath(style, RootKind.DriveAbsolute, components.withSegments(other.components.segments()));
                case UNC -> new PortablePath(style, RootKind.UNC, components.withSegments(other.components.segments()));
                case Relative, DirectoryRelative -> other;
                case Absolute, Unavailable -> throw new AssertionError(kind);
            };
        }

        /*
         * The `WindowsPath` `DRIVE_RELATIVE` case combines paths only when the base is absolute and
         * uses the same drive. Otherwise the drive-relative child remains unchanged.
         */
        private PortablePath resolveWindowsDriveRelative(PortablePath other) {
            if (this.kind == RootKind.DriveAbsolute && components.prefix().equals(other.components.prefix())) {
                return resolveRelative(other);
            }
            return other;
        }

        PortablePath normalize() {
            requireAvailable();
            return new PortablePath(style, kind, components.normalize(kind.cannotEscapeRoot()));
        }

        private void requireAvailable() {
            if (kind == RootKind.Unavailable) {
                throw new IllegalStateException("Unavailable substitution has no filesystem path");
            }
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof PortablePath other && style == other.style && kind == other.kind && pathTextEquals(other.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(style, kind, pathTextHashCode());
        }

        private boolean pathTextEquals(String otherText) {
            if (text.length() != otherText.length()) {
                return false;
            }
            for (int index = 0; index < text.length(); index++) {
                if (style.comparisonChar(text.charAt(index)) != style.comparisonChar(otherText.charAt(index))) {
                    return false;
                }
            }
            return true;
        }

        private int pathTextHashCode() {
            int hash = 0;
            for (int index = 0; index < text.length(); index++) {
                hash = 31 * hash + style.comparisonChar(text.charAt(index));
            }
            return hash;
        }

        @Override
        public String toString() {
            return "PortablePath[style=" + style + ", kind=" + kind + ", text=" + text + "]";
        }

        private record PathComponents(String prefix, List<String> segments) {
            private PathComponents {
                segments = List.copyOf(segments);
            }

            private static PathComponents parse(PathStyle style, RootKind kind, String text) {
                if (text.startsWith("/")) {
                    throw malformed(style, kind, text);
                }
                List<String> components = split(text);
                return switch (style) {
                    case BundleRelative -> {
                        if (kind != RootKind.Relative && !(kind == RootKind.Unavailable && text.isEmpty())) {
                            throw malformed(style, kind, text);
                        }
                        yield new PathComponents("", components);
                    }
                    case Unix -> {
                        if (kind != RootKind.Relative && kind != RootKind.Absolute) {
                            throw malformed(style, kind, text);
                        }
                        yield new PathComponents("", components);
                    }
                    case Windows -> parseWindows(kind, text, components);
                };
            }

            private static PathComponents parseWindows(RootKind kind, String text, List<String> components) {
                return switch (kind) {
                    case Relative, DirectoryRelative -> new PathComponents("", components);
                    case DriveRelative, DriveAbsolute -> {
                        if (components.isEmpty() || !isEncodedDrive(components.getFirst())) {
                            throw malformed(PathStyle.Windows, kind, text);
                        }
                        yield new PathComponents(components.getFirst(), components.subList(1, components.size()));
                    }
                    case UNC -> {
                        if (components.size() < 2) {
                            throw malformed(PathStyle.Windows, kind, text);
                        }
                        yield new PathComponents(String.join("/", components.subList(0, 2)), components.subList(2, components.size()));
                    }
                    case Absolute, Unavailable -> throw malformed(PathStyle.Windows, kind, text);
                };
            }

            private static List<String> split(String text) {
                return Stream.of(unixSeparatorPattern.split(text)).filter(component -> !component.isEmpty()).toList();
            }

            private static List<String> splitWindows(String text) {
                return Stream.of(windowsSeparatorPattern.split(text)).filter(component -> !component.isEmpty()).toList();
            }

            private static IllegalArgumentException malformed(PathStyle style, RootKind kind, String text) {
                return new IllegalArgumentException("Malformed portable path with style " + style + ", kind " + kind + ", and text " + text);
            }

            private static IllegalArgumentException malformedLegacyWindows(String text) {
                return new IllegalArgumentException("Malformed legacy portable Windows path " + text);
            }

            private static boolean isEncodedDrive(String component) {
                return component.length() == 1 && component.charAt(0) >= 'a' && component.charAt(0) <= 'z';
            }

            private PathComponents parent(boolean hasRoot) {
                if (segments.isEmpty()) {
                    return null;
                }
                if (segments.size() > 1) {
                    return withSegments(segments.subList(0, segments.size() - 1));
                }
                if (hasRoot) {
                    return withSegments(List.of());
                }
                return null;
            }

            private PathComponents fileName() {
                if (segments.isEmpty()) {
                    return null;
                }
                return new PathComponents("", List.of(segments.getLast()));
            }

            private PathComponents normalize(boolean cannotEscapeRoot) {
                ArrayList<String> normalized = new ArrayList<>(segments.size());
                for (String segment : segments) {
                    if (".".equals(segment)) {
                        continue;
                    } else if ("..".equals(segment)) {
                        if (!normalized.isEmpty() && !"..".equals(normalized.getLast())) {
                            /* A parent segment cancels the nearest preceding non-parent segment. */
                            normalized.removeLast();
                        } else if (!cannotEscapeRoot) {
                            /*
                             * Preserve unmatched parent segments for relative and drive-relative
                             * paths. Absolute and directory-relative paths cannot escape their root.
                             */
                            normalized.add(segment);
                        }
                    } else {
                        normalized.add(segment);
                    }
                }
                return withSegments(normalized);
            }

            private PathComponents withSegments(List<String> pathSegments) {
                return new PathComponents(prefix, pathSegments);
            }

            private String payloadText() {
                return joinPrefixAndSegments(prefix, segments);
            }

            private String windowsPathText(RootKind kind) {
                String suffix = String.join("\\", segments);
                return switch (kind) {
                    case Relative -> suffix;
                    case DriveRelative -> prefix.toUpperCase(Locale.ROOT) + ":" + suffix;
                    case DriveAbsolute -> prefix.toUpperCase(Locale.ROOT) + ":\\" + suffix;
                    case DirectoryRelative -> "\\" + suffix;
                    case UNC -> "\\\\" + prefix.replace('/', '\\') + (suffix.isEmpty() ? "" : "\\" + suffix);
                    case Absolute, Unavailable -> throw malformed(PathStyle.Windows, kind, payloadText());
                };
            }

            private static String joinPrefixAndSegments(String prefix, List<String> pathSegments) {
                String suffix = String.join("/", pathSegments);
                if (prefix.isEmpty()) {
                    return suffix;
                }
                return suffix.isEmpty() ? prefix : prefix + "/" + suffix;
            }
        }
    }

    private static final String srcField = "src";
    private static final String dstField = "dst";
    private static final String styleField = "style";
    private static final String kindField = "kind";
    private static final String textField = "text";

    private static final String winPrefix = "win";
    private static final String winRelativePrefix = "win-rel";
    private static final String winDriveRelativePrefix = "win-drive-rel";
    private static final String windowsIPv6LiteralSuffix = ".ipv6-literal.net";

    private static final Pattern windowsSeparatorPattern = Pattern.compile("[\\\\/]+");
    private static final Pattern unixSeparatorPattern = Pattern.compile("/+");

    private BundlePathMap() {
    }

    /**
     * Parses a portable path mapping file without discarding its path style or encoded text.
     */
    static void parseAndRegister(Reader reader, Map<PortablePath, PortablePath> pathMap) throws IOException {
        Object json = new BundleJSONParser(reader).parse();
        for (var rawEntry : BundleConfigurationParser.asList(json, "Expected a list of path substitution objects")) {
            var entry = BundleConfigurationParser.asMap(rawEntry, "Expected a substitution object");
            PortablePath source = parsePortablePath(entry, srcField);
            if (source.kind() == RootKind.Unavailable) {
                throw new BundleJSONParserException("Unavailable is only valid as a path substitution destination");
            }
            pathMap.put(source, parsePortablePath(entry, dstField));
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
        try {
            PathStyle style = PathStyle.valueOf(rawStyle.toString());
            Object rawKind = pathObject.get(kindField);
            RootKind kind = rawKind == null ? null : RootKind.valueOf(rawKind.toString());
            if (kind == RootKind.Unavailable) {
                if (style != PathStyle.BundleRelative || pathObject.containsKey(textField)) {
                    throw new BundleJSONParserException("Unavailable paths require BundleRelative style and no text field");
                }
                return PortablePath.UNAVAILABLE;
            }
            Object rawText = pathObject.get(textField);
            if (rawText == null) {
                throw new BundleJSONParserException("Expected " + textField + "-field in portable path object");
            }
            if (kind != null) {
                return new PortablePath(style, kind, rawText.toString());
            }
            // The kind field was added in bundle format version 2.
            return PortablePath.parseLegacy(style, rawText.toString());
        } catch (IllegalArgumentException ex) {
            throw new BundleJSONParserException("Malformed portable path " + pathObject);
        }
    }

    /**
     * Main bundle-file entry point for serializing a source-platform path into the portable bundle
     * schema.
     */
    static PortablePath sourcePath(Path path, PathStyle sourceStyle) {
        return PortablePath.parseSource(sourceStyle, path.toString());
    }

    /**
     * Main bundle-file entry point for serializing a bundle-relative path into the portable bundle
     * schema.
     */
    static PortablePath bundlePath(Path path) {
        return new PortablePath(PathStyle.BundleRelative, RootKind.Relative, encodeBundlePathText(path));
    }

    static Path resolveBundlePath(Path bundleRoot, PortablePath path) {
        if (path.style() != PathStyle.BundleRelative || path.kind() != RootKind.Relative) {
            throw new IllegalArgumentException("Expected a bundle-relative path but got " + path);
        }
        return bundleRoot.resolve(Path.of(path.text()));
    }

    static <T> Stream<Map.Entry<T, T>> withoutIdentityMappings(Map<T, T> pathMap) {
        return pathMap.entrySet().stream().filter(entry -> !entry.getKey().equals(entry.getValue()));
    }

    /**
     * Prints a path mapping entry using the portable on-disk bundle schema.
     */
    static void printPathMapping(Map.Entry<PortablePath, PortablePath> entry, JsonWriter writer) throws IOException {
        writer.append('{').quote(srcField).append(':');
        printPortablePath(entry.getKey(), writer);
        writer.append(',').quote(dstField).append(':');
        printPortablePath(entry.getValue(), writer);
        writer.append('}');
    }

    private static void printPortablePath(PortablePath portablePath, JsonWriter writer) throws IOException {
        writer.append('{').quote(styleField).append(':').quote(portablePath.style().name());
        writer.append(',').quote(kindField).append(':').quote(portablePath.kind().name());
        if (portablePath.kind() != RootKind.Unavailable) {
            writer.append(',').quote(textField).append(':').quote(portablePath.text());
        }
        writer.append('}');
    }

    private static String encodeBundlePathText(Path path) {
        return StreamSupport.stream(path.spliterator(), false)
                        .map(Path::toString)
                        .collect(Collectors.joining("/"));
    }

}
