/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.listeners.Metadata;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceFileReference;

public abstract class MDFile implements MDBaseNode {

    private MDFile() {
    }

    public abstract MDBaseNode getMDFile();

    public abstract MDBaseNode getMDDirectory();

    public abstract LLVMSourceFileReference toSourceFileReference();

    /**
     * Separate implementation to hide {@link LLVMSourceFileReference} from {@link MDFile}.
     */
    private static final class MDFileImpl extends MDFile implements LLVMSourceFileReference {
        private MDBaseNode directory;
        private MDBaseNode file;
        private int checksumKind;
        private MDBaseNode checksum;

        private MDFileImpl() {
            this.file = MDVoidNode.INSTANCE;
            this.directory = MDVoidNode.INSTANCE;
            this.checksumKind = 0;
            this.checksum = MDVoidNode.INSTANCE;
        }

        @Override
        public void accept(MetadataVisitor visitor) {
            visitor.visit(this);
        }

        @Override
        public MDBaseNode getMDFile() {
            return file;
        }

        @Override
        public MDBaseNode getMDDirectory() {
            return directory;
        }

        @Override
        public LLVMSourceFileReference toSourceFileReference() {
            return this;
        }

        @Override
        public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
            if (file == oldValue) {
                file = newValue;
            }
            if (directory == oldValue) {
                directory = newValue;
            }
            if (checksum == oldValue) {
                checksum = newValue;
            }
        }

        @Override
        public String getFilename() {
            return MDString.getIfInstance(file);
        }

        @Override
        public String getDirectory() {
            return MDString.getIfInstance(directory);
        }

        @Override
        public LLVMSourceFileReference.ChecksumKind getChecksumKind() {
            return LLVMSourceFileReference.ChecksumKind.forBitcodeValue(checksumKind);
        }

        @Override
        public String getChecksum() {
            return MDString.getIfInstance(checksum);
        }

    }

    private static final int ARGINDEX_FILENAME = 1;
    private static final int ARGINDEX_DIRECTORY = 2;
    private static final int ARGINDEX_CHECKSUMKIND = 3;
    private static final int ARGINDEX_CHECKSUM = 4;

    public static MDFile create38(long[] args, MetadataValueList md) {
        // [distinct, filename, directory]
        final MDFileImpl file = new MDFileImpl();
        file.file = md.getNullable(args[ARGINDEX_FILENAME], file);
        file.directory = md.getNullable(args[ARGINDEX_DIRECTORY], file);
        file.checksumKind = (int) args[ARGINDEX_CHECKSUMKIND];
        file.checksum = md.getNullable(args[ARGINDEX_CHECKSUM], file);
        return file;
    }

    public static MDFile create32(long[] args, Metadata md) {
        final MDFileImpl file = new MDFileImpl();
        file.file = ParseUtil.resolveReference(args, ARGINDEX_FILENAME, file, md);
        file.directory = ParseUtil.resolveReference(args, ARGINDEX_DIRECTORY, file, md);
        file.checksumKind = (int) args[ARGINDEX_CHECKSUMKIND];
        file.checksum = ParseUtil.resolveReference(args, ARGINDEX_CHECKSUM, file, md);
        return file;
    }

    public static MDFile create(MDBaseNode fileNode, MDBaseNode dirNode) {
        return create(fileNode, dirNode, 0, MDVoidNode.INSTANCE);
    }

    public static MDFile create(MDBaseNode fileNode, MDBaseNode dirNode, int checksumKind, MDBaseNode checksum) {
        final MDFileImpl file = new MDFileImpl();
        file.file = fileNode;
        file.directory = dirNode;
        file.checksumKind = checksumKind;
        file.checksum = checksum;
        return file;
    }
}
