/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata;

public final class MDFile implements MDBaseNode {

    private final MDReference directory;

    private final MDReference file;

    private MDFile(MDReference file, MDReference directory) {
        this.file = file;
        this.directory = directory;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDReference getFile() {
        return file;
    }

    public MDReference getDirectory() {
        return directory;
    }

    @Override
    public String toString() {
        return String.format("File (file=%s, directory=%s)", file, directory);
    }

    private static final int ARGINDEX_FILENAME = 1;
    private static final int ARGINDEX_DIRECTORY = 2;

    public static MDFile create38(long[] args, MetadataList md) {
        // [distinct, filename, directory]
        final MDReference file = md.getMDRefOrNullRef(args[ARGINDEX_FILENAME]);
        final MDReference directory = md.getMDRefOrNullRef(args[ARGINDEX_DIRECTORY]);
        return new MDFile(file, directory);
    }

    public static MDFile create32(MDTypedValue[] args) {
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_FILENAME]);
        final MDReference directory = ParseUtil.getReference(args[ARGINDEX_DIRECTORY]);
        return new MDFile(file, directory);
    }

}
