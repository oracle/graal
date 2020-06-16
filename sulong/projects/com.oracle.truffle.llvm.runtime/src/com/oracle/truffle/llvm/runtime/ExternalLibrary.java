/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class ExternalLibrary {

    private final String name;
    private final Path path;
    private final TruffleFile file;
    private static final String BC_EXT = ".bc";

    @CompilerDirectives.CompilationFinal private boolean isNative;
    private final boolean isInternal;

    public static ExternalLibrary externalFromName(String name, boolean isNative) {
        return ExternalLibrary.createFromName(name, isNative, false);
    }

    public static ExternalLibrary internalFromName(String name, boolean isNative) {
        return ExternalLibrary.createFromName(name, isNative, true);
    }

    public static ExternalLibrary internalFromPath(Path path, boolean isNative) {
        return ExternalLibrary.createFromPath(path, isNative, true);
    }

    public static ExternalLibrary createFromName(String name, boolean isNative, boolean isInternal) {
        return new ExternalLibrary(name, null, isNative, isInternal, null);
    }

    public static ExternalLibrary createFromPath(Path path, boolean isNative, boolean isInternal) {
        return new ExternalLibrary(extractName(path), path, isNative, isInternal, null);
    }

    public static ExternalLibrary createFromFile(TruffleFile file, boolean isNative, boolean isInternal) {
        Path path = Paths.get(file.getPath());
        return new ExternalLibrary(extractName(path), path, isNative, isInternal, getCanonicalFile(file));
    }

    private static TruffleFile getCanonicalFile(TruffleFile file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            /*
             * This should never happen since we've already proven existence of the file, but better
             * safe than sorry.
             */
            return file;
        }
    }

    private static String extractName(Path path) {
        Path filename = path.getFileName();
        if (filename == null) {
            throw new IllegalArgumentException("Path " + path + " is empty");
        }
        String nameWithExt = filename.toString();
        if (nameWithExt.endsWith(BC_EXT)) {
            return nameWithExt.substring(0, nameWithExt.length() - BC_EXT.length());
        }
        return nameWithExt;
    }

    private ExternalLibrary(String name, Path path, boolean isNative, boolean isInternal, TruffleFile file) {
        this.name = name;
        this.path = path;
        this.isNative = isNative;
        this.isInternal = isInternal;
        this.file = file;
    }

    public boolean hasFile() {
        return file != null;
    }

    public TruffleFile getFile() {
        return file;
    }

    public Path getPath() {
        return path;
    }

    public boolean isNative() {
        return isNative;
    }

    public boolean isInternal() {
        return isInternal;
    }

    public void makeBitcodeLibrary() {
        this.isNative = false;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof ExternalLibrary) {
            ExternalLibrary other = (ExternalLibrary) obj;
            if (file != null) {
                // If we have a canonical file already, the file is authoritative.
                return file.equals(other.file);
            }
            return name.equals(other.name) && Objects.equals(path, other.path);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ Objects.hashCode(path);
    }

    @Override
    public String toString() {
        return path == null ? name : name + " (" + path + ")";
    }
}
