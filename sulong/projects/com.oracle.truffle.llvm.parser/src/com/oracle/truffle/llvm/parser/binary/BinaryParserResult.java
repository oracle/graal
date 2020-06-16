/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.binary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import org.graalvm.polyglot.io.ByteSequence;

/**
 * Encapsulates a bitcode {@link ByteSequence} as well as meta information such as dependencies and
 * search paths.
 */
public final class BinaryParserResult {

    private final ArrayList<String> libraries;
    private final ArrayList<String> paths;
    private final ByteSequence bitcode;
    private final LibraryLocator locator;
    private final Source source;

    BinaryParserResult(ArrayList<String> libraries, ArrayList<String> paths, ByteSequence bitcode, LibraryLocator locator, Source source) {
        this.libraries = libraries;
        this.paths = paths;
        this.bitcode = bitcode;
        this.locator = locator;
        this.source = source;
    }

    public List<String> getLibraries() {
        return Collections.unmodifiableList(libraries);
    }

    public List<String> getLibraryPaths() {
        return Collections.unmodifiableList(paths);
    }

    public ByteSequence getBitcode() {
        return bitcode;
    }

    public LibraryLocator getLocator() {
        return locator;
    }

    public Source getSource() {
        return source;
    }
}
