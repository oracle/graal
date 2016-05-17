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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;

final class FileSourceImpl extends Content {

    private final File file;
    private final String name; // Name used originally to describe the source
    private final String path; // Normalized path description of an actual file
    private String code; // A cache of the file's contents

    FileSourceImpl(File file, String name, String path) {
        this.file = file.getAbsoluteFile();
        this.name = name;
        this.path = path;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getShortName() {
        return file.getName();
    }

    @Override
    Object getHashKey() {
        return path;
    }

    @Override
    public String getCode() {
        if (Source.fileCacheEnabled) {
            if (code == null) {
                try {
                    code = Source.read(getReader());
                } catch (IOException e) {
                }
            }
            return code;
        }
        try {
            return Source.read(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        } catch (IOException e) {
        }
        return null;
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
    public Reader getReader() {
        if (code != null) {
            return new StringReader(code);
        }
        try {
            return new InputStreamReader(new FileInputStream(file), "UTF-8");
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Can't find file " + path, e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unsupported encoding in file " + path, e);
        }
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    String findMimeType() throws IOException {
        return Files.probeContentType(file.toPath());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof FileSourceImpl) {
            FileSourceImpl other = (FileSourceImpl) obj;
            if (!path.equals(other.path)) {
                return false;
            }
            if (code == null && other.code == null) {
                return true;
            }
            return Objects.equals(getCode(), other.getCode());
        }
        return false;
    }

    @Override
    void reset() {
        this.code = null;
    }

}
