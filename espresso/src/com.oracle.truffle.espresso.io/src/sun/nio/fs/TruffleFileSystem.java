/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Provides access to the Truffle virtual file system.
 *
 * <h4>File system traits</h4>
 *
 * The Truffle VFS may have different traits w.r.t to the guest OS/platform e.g. path separator,
 * case-insensitive path matching, roots, FileStore(s). By default, it will try to emulate the guest
 * OS/platform traits, under the assumption that the Truffle VFS is also platform-compatible, but
 * compatibility cannot be guaranteed, less so if Truffle and OS/platform have different traits.
 *
 * <h4>Usage</h4>
 *
 * This file system can only be used on Espresso as the default Java filesystem e.g. file://
 * handler. File descriptors obtained from this file system are virtual and should not be passed to
 * native code.
 *
 * <p>
 * This file must be compatible with 21+.
 */
final class TruffleFileSystem extends FileSystem {
    private static final Set<String> SUPPORTED_ATTRIBUTES = Collections.singleton("basic");

    private final TruffleFileSystemProvider provider;

    TruffleFileSystem(TruffleFileSystemProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        // Cannot know, so be conservative.
        return false;
    }

    @Override
    public String getSeparator() {
        return TruffleFileSystemProvider.SEPARATOR;
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return SUPPORTED_ATTRIBUTES;
    }

    @Override
    public Path getPath(String first, String... more) {
        String path;
        if (more.length == 0) {
            path = first;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(first);
            for (String segment : more) {
                if (!segment.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(getSeparator());
                    }
                    sb.append(segment);
                }
            }
            path = sb.toString();
        }
        return new TrufflePath(this, path);
    }

    private static final String GLOB_SYNTAX = "glob";
    private static final String REGEX_SYNTAX = "regex";

    /**
     * The underlying Truffle FS may have different path match requirements, such as case
     * insensitive or Unicode canonical equal on MacOSX. It is expected that the Truffle FS matches
     * the underlying platform path match requirements.
     */
    private static Pattern compilePathMatchPattern(String expr) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        int flags = 0; // unix
        if (os.contains("win")) {
            flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        } else if (os.contains("mac")) {
            flags = Pattern.CANON_EQ;
        }
        return Pattern.compile(expr, flags);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {

        int pos = syntaxAndPattern.indexOf(':');
        if (pos <= 0 || pos == syntaxAndPattern.length()) {
            throw new IllegalArgumentException();
        }
        String syntax = syntaxAndPattern.substring(0, pos);
        String input = syntaxAndPattern.substring(pos + 1);

        String expr;
        if (syntax.equalsIgnoreCase(GLOB_SYNTAX)) {
            String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
            if (os.contains("win")) {
                expr = sun.nio.fs.Globs.toWindowsRegexPattern(input);
            } else {
                expr = sun.nio.fs.Globs.toUnixRegexPattern(input);
            }
        } else {
            if (syntax.equalsIgnoreCase(REGEX_SYNTAX)) {
                expr = input;
            } else {
                throw new UnsupportedOperationException("Syntax '" + syntax +
                                "' not recognized");
            }
        }

        // return matcher
        final Pattern pattern = compilePathMatchPattern(expr);

        return path -> pattern.matcher(path.toString()).matches();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    /**
     * We cannot just throw {@link UnsupportedOperationException} since we implement the Default
     * filesystem.
     */
    @Override
    public WatchService newWatchService() throws IOException {
        // GR-42440: To implement a proper watch service, we need a currently missing feature from
        // the truffle side.
        return new DummyWatchService();
    }
}
