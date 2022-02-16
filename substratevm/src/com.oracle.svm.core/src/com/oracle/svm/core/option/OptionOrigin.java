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

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class OptionOrigin {

    public URI container() {
        return null;
    }

    public Path location() {
        return null;
    }

    public static OptionOrigin of(String origin) {

        if (origin == null) {
            return CommandLineOptionOrigin.singleton;
        }

        URI originURI = originURI(origin);
        if (originURI == null) {
            return new UnsupportedOptionOrigin(origin);
        }

        switch (originURI.getScheme()) {
            case "jar":
                return new JarOptionOrigin(originURI);
            case "file":
                String rawPath = originURI.getPath();
                if (Files.isDirectory(Path.of(rawPath))) {
                    return new DirectoryOptionOrigin(rawPath);
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

    public static final class CommandLineOptionOrigin extends OptionOrigin {

        private static CommandLineOptionOrigin singleton = new CommandLineOptionOrigin();

        @Override
        public String toString() {
            return "command line";
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
            location = Path.of(specific.substring(sep + 1));
        }
    }

    public static final class DirectoryOptionOrigin extends URIOptionOrigin {
        protected DirectoryOptionOrigin(String rawPath) {
            String metaInf = "/META-INF/";
            int index = rawPath.indexOf(metaInf);
            container = Path.of(rawPath.substring(0, index)).toUri();
            location = Path.of(rawPath.substring(index));
        }
    }
}