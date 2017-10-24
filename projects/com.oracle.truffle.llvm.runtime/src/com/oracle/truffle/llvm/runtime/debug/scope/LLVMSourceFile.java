/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.scope;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LLVMSourceFile {

    private static final String MIMETYPE_PLAINTEXT = "text/plain";
    private static final String UNAVAILABLE_NAME = "<unavailable source>";

    private final String name;
    private final String directory;

    public LLVMSourceFile(String name, String directory) {
        this.name = name;
        this.directory = directory;
    }

    private Source resolvedSource = null;

    Source toSource() {
        if (resolvedSource == null) {
            resolveSource();
        }
        return resolvedSource;
    }

    @TruffleBoundary
    private void resolveSource() {
        if (name == null) {
            return;
        }

        final Path path = getFullPath(name, directory);
        if (path == null) {
            return;

        }

        final File sourceFile = path.toFile();
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            return;
        }

        final String sourceName = sourceFile.getName();
        final String mimeType = getMimeType(sourceFile.getPath());

        try {
            resolvedSource = Source.newBuilder(sourceFile).mimeType(mimeType).name(sourceName).build();
        } catch (Throwable ignored) {
            // resolvedSource will stay null which indicates an error in any case
        }
    }

    @Override
    @TruffleBoundary
    public String toString() {
        if (resolvedSource == null) {
            return String.format("<unresolved source: %s>", super.toString());
        } else {
            return resolvedSource.getName();
        }
    }

    @TruffleBoundary
    public static String getMimeType(String path) {
        if (path == null) {
            return MIMETYPE_PLAINTEXT;
        }

        int extStartIndex = path.lastIndexOf('.') + 1;
        if (extStartIndex <= 0 || extStartIndex >= path.length()) {
            return MIMETYPE_PLAINTEXT;
        }

        switch (path.substring(extStartIndex)) {
            case "c":
                return "text/x-c";
            case "h":
                return "text/x-h";
            case "f":
            case "f90":
            case "for":
                return "text/x-fortran";
            case "rs":
                return "text/x-rust";
            default:
                return MIMETYPE_PLAINTEXT;
        }
    }

    @TruffleBoundary
    static String getSourceName(LLVMSourceFile file) {
        if (file == null) {
            return UNAVAILABLE_NAME;
        }

        final Source source = file.toSource();
        if (source != null) {
            return source.getName();
        }

        final Path fullPath = getFullPath(file.name, file.directory);
        return fullPath != null ? fullPath.toString() : UNAVAILABLE_NAME;
    }

    @Override
    @TruffleBoundary
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LLVMSourceFile that = (LLVMSourceFile) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }

        return directory != null ? directory.equals(that.directory) : that.directory == null;
    }

    @Override
    @TruffleBoundary
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (directory != null ? directory.hashCode() : 0);
        return result;
    }

    @TruffleBoundary
    private static Path getFullPath(String name, String directory) {
        if (name == null) {
            return null;
        }

        Path path = Paths.get(name);
        if (!path.isAbsolute() && directory != null) {
            path = Paths.get(directory, name);
        }
        return path.normalize();
    }
}
