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

import com.oracle.svm.core.util.VMError;

public abstract class OptionOrigin {

    public URI container() {
        return null;
    }

    public Path location() {
        return null;
    }

    public List<String> getRedirectionValues(Path valuesFile) throws IOException {
        throw new UnsupportedOperationException();
    }

    public static OptionOrigin of(String origin) {

        if (origin == null) {
            return CommandLineOptionOrigin.singleton;
        }

        URI originURI = originURI(origin);
        if (originURI == null) {
            String macroName = MacroOptionOrigin.macroName(origin);
            if (macroName != null) {
                return new MacroOptionOrigin(macroName);
            }
            return new UnsupportedOptionOrigin(origin);
        }

        switch (originURI.getScheme()) {
            case "jar":
                return new JarOptionOrigin(originURI);
            case "file":
                Path originPath = Path.of(originURI);
                if (Files.isReadable(originPath)) {
                    return new DirectoryOptionOrigin(originPath);
                }
                return new UnsupportedOptionOrigin(origin);
            default:
                return new UnsupportedOptionOrigin(origin);
        }
    }

    protected static URI originURI(String origin) {
        try {
            return new URI(origin);
        } catch (URISyntaxException x) {
            return null;
        }
    }

    public static class CommandLineOptionOrigin extends OptionOrigin {

        private static CommandLineOptionOrigin singleton = new CommandLineOptionOrigin();

        @Override
        public String toString() {
            return "command line";
        }
    }

    public static final class MacroOptionOrigin extends CommandLineOptionOrigin {

        private static final String PREFIX = OptionUtils.MacroOptionKind.Macro.getDescriptionPrefix(true);

        private final String name;

        MacroOptionOrigin(String name) {
            this.name = name;
        }

        public static String macroName(String rawOrigin) {
            return rawOrigin.startsWith(PREFIX) ? rawOrigin.substring(PREFIX.length()) : null;
        }

        @Override
        public String toString() {
            return "macro option '" + name + "'";
        }
    }

    public static final class UnsupportedOptionOrigin extends OptionOrigin {

        protected final String rawOrigin;

        public UnsupportedOptionOrigin(String rawOrigin) {
            this.rawOrigin = rawOrigin;
        }

        @Override
        public String toString() {
            return rawOrigin;
        }
    }

    protected static abstract class URIOptionOrigin extends OptionOrigin {

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
        public String toString() {
            return String.format("'%s' in '%s'", location(), container());
        }
    }

    public static final class JarOptionOrigin extends URIOptionOrigin {
        protected JarOptionOrigin(URI rawOrigin) {
            var specific = rawOrigin.getSchemeSpecificPart();
            int sep = specific.lastIndexOf('!');
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
                throw new FileNotFoundException();
            }
            if (probeJarFS != null) {
                try (FileSystem jarFS = probeJarFS) {
                    var normalizedRedirPath = location().getParent().resolve(valuesFile).normalize();
                    var pathInJarFS = jarFS.getPath(normalizedRedirPath.toString());
                    if (Files.exists(pathInJarFS)) {
                        return Files.readAllLines(pathInJarFS);
                    }
                    throw new FileNotFoundException(pathInJarFS.toString());
                }
            }
            throw new FileNotFoundException(jarFileURI.toString());
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
            VMError.guarantee(metaInfPos > 0, "invalid directory origin");
            container = originPath.getRoot().resolve(originPath.subpath(0, metaInfPos)).toUri();
            location = originPath.subpath(metaInfPos, originPath.getNameCount());
        }

        @Override
        public List<String> getRedirectionValues(Path valuesFile) throws IOException {
            var normalizedRedirPath = Path.of(container()).resolve(location()).getParent().resolve(valuesFile).normalize();
            if (Files.exists(normalizedRedirPath)) {
                return Files.readAllLines(normalizedRedirPath);
            }
            throw new FileNotFoundException(normalizedRedirPath.toString());
        }
    }
}