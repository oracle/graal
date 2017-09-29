/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.util.Collection;
import java.util.ServiceLoader;

import com.oracle.truffle.api.source.impl.SourceAccessor;

final class FileSourceImpl extends Content implements Content.CreateURI {
    private static final boolean AOT = SourceAccessor.isAOT();
    private final File file;
    private final String name; // Name used originally to describe the source
    private final String path; // Normalized path description of an actual file

    FileSourceImpl(String content, File file, String name, String path) {
        this.code = enforceCharSequenceContract(content);
        this.file = file.getAbsoluteFile();
        this.name = name;
        this.path = path;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    Object getHashKey() {
        return path;
    }

    @Override
    CharSequence getCharacters() {
        return code;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public URL getURL() {
        return null;
    }

    @Override
    URI getURI() {
        return createURIOnce(this);
    }

    @Override
    public URI createURI() {
        return file.toURI();
    }

    @Override
    public Reader getReader() {
        return new CharSequenceReader(code);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    String findMimeType() throws IOException {
        return findMimeType(file.toPath());
    }

    static String findMimeType(final Path filePath) throws IOException {
        if (!AOT) {
            Collection<ClassLoader> loaders = SourceAccessor.allLoaders();
            for (ClassLoader l : loaders) {
                for (FileTypeDetector detector : ServiceLoader.load(FileTypeDetector.class, l)) {
                    String mimeType = detector.probeContentType(filePath);
                    if (mimeType != null) {
                        return mimeType;
                    }
                }
            }
        }

        String found = Files.probeContentType(filePath);
        return found == null ? "content/unknown" : found;
    }
}
