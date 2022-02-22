/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.option;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.oracle.svm.core.util.VMError;

public abstract class OptionOrigin {

    public URI container() {
        return null;
    }

    public Path location() {
        return null;
    }

    public boolean commandLineLike() {
        return false;
    }

    public List<String> getRedirectionValues(@SuppressWarnings("unused") Path valuesFile) throws IOException {
        throw new IOException(new UnsupportedOperationException());
    }

    public static OptionOrigin from(String origin) {

        if (origin == null) {
            return CommandLineOptionOrigin.singleton;
        }

        URI originURI = originURI(origin);
        if (originURI == null) {
            var macroOption = MacroOptionOrigin.from(origin);
            if (macroOption != null) {
                return macroOption;
            }
            throw VMError.shouldNotReachHere("Unsupported OptionOrigin: " + origin);
        }
        switch (originURI.getScheme()) {
            case "jar":
                return new JarOptionOrigin(originURI);
            case "file":
                Path originPath = Path.of(originURI);
                if (!Files.isReadable(originPath)) {
                    VMError.shouldNotReachHere("Directory origin with path that cannot be read: " + originPath);
                }
                return new DirectoryOptionOrigin(originPath);
            default:
                throw VMError.shouldNotReachHere("OptionOrigin of unsupported scheme: " + originURI);
        }
    }

    protected static URI originURI(String origin) {
        try {
            return new URI(origin);
        } catch (URISyntaxException x) {
            return null;
        }
    }

    public static final class CommandLineOptionOrigin extends OptionOrigin {

        public static final CommandLineOptionOrigin singleton = new CommandLineOptionOrigin();

        private CommandLineOptionOrigin() {
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CommandLineOptionOrigin;
        }

        @Override
        public boolean commandLineLike() {
            return true;
        }

        @Override
        public String toString() {
            return "command line";
        }
    }

    public static final class MacroOptionOrigin extends OptionOrigin {

        public final OptionUtils.MacroOptionKind kind;
        public final String name;

        private MacroOptionOrigin(OptionUtils.MacroOptionKind kind, String name) {
            this.kind = kind;
            this.name = name;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, name);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MacroOptionOrigin) {
                var that = (MacroOptionOrigin) obj;
                return Objects.equals(this.kind, that.kind) &&
                                Objects.equals(this.name, that.name);
            }
            return false;
        }

        public static MacroOptionOrigin from(String rawOrigin) {
            for (OptionUtils.MacroOptionKind kind : OptionUtils.MacroOptionKind.values()) {
                String prefix = kind.getDescriptionPrefix(true);
                if (rawOrigin.startsWith(prefix)) {
                    return new MacroOptionOrigin(kind, rawOrigin.substring(prefix.length()));
                }
            }
            return null;
        }

        @Override
        public boolean commandLineLike() {
            return OptionUtils.MacroOptionKind.Macro.equals(kind);
        }

        @Override
        public String toString() {
            return kind + " option '" + name + "'";
        }
    }

    protected abstract static class URIOptionOrigin extends OptionOrigin {

        protected URI container;

        @Override
        public URI container() {
            return container;
        }

        protected Path location;

        @Override
        public Path location() {
            return location;
        }

        @Override
        public int hashCode() {
            return Objects.hash(container, location);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof URIOptionOrigin) {
                var that = (URIOptionOrigin) obj;
                return Objects.equals(this.container, that.container) &&
                                Objects.equals(this.location, that.location);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("'%s' in '%s'", location(), container());
        }
    }

    public static final class JarOptionOrigin extends URIOptionOrigin {
        protected JarOptionOrigin(URI rawOrigin) {
            var specific = rawOrigin.getSchemeSpecificPart();
            int sep = specific.lastIndexOf('!');
            VMError.guarantee(sep > 0, "Invalid jar origin");
            var origin = specific.substring(0, sep);
            container = URIOptionOrigin.originURI(origin);
            location = Path.of(specific.substring(sep + 2));
        }

        @Override
        public List<String> getRedirectionValues(Path valuesFile) throws IOException {
            URI jarFileURI = URI.create("jar:" + container());
            FileSystem probeJarFS;
            try {
                probeJarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap());
            } catch (UnsupportedOperationException e) {
                probeJarFS = null;
            }
            if (probeJarFS == null) {
                throw new IOException("Unable to create jar file system for " + jarFileURI);
            }
            try (FileSystem jarFS = probeJarFS) {
                var normalizedRedirPath = location().getParent().resolve(valuesFile).normalize();
                var pathInJarFS = jarFS.getPath(normalizedRedirPath.toString());
                if (Files.isReadable(pathInJarFS)) {
                    return Files.readAllLines(pathInJarFS);
                }
                throw new FileNotFoundException("Unable to read " + pathInJarFS + " from jar file system " + jarFS);
            }
        }
    }

    public static final class DirectoryOptionOrigin extends URIOptionOrigin {
        protected DirectoryOptionOrigin(Path originPath) {
            int pathPos = 0;
            int metaInfPos = -1;
            for (Path entry : originPath) {
                if ("META-INF".equals(entry.toString())) {
                    metaInfPos = pathPos;
                    break;
                }
                ++pathPos;
            }
            VMError.guarantee(metaInfPos > 0, "Invalid directory origin");
            container = originPath.getRoot().resolve(originPath.subpath(0, metaInfPos)).toUri();
            location = originPath.subpath(metaInfPos, originPath.getNameCount());
        }

        @Override
        public List<String> getRedirectionValues(Path valuesFile) throws IOException {
            var normalizedRedirPath = Path.of(container()).resolve(location()).getParent().resolve(valuesFile).normalize();
            if (Files.isReadable(normalizedRedirPath)) {
                return Files.readAllLines(normalizedRedirPath);
            }
            throw new FileNotFoundException("Unable to read file from " + normalizedRedirPath);
        }
    }
}
