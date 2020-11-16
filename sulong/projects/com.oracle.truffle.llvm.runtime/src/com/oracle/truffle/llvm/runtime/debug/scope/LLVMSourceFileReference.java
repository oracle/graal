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
package com.oracle.truffle.llvm.runtime.debug.scope;

import org.graalvm.collections.Equivalence;

import java.util.Objects;

/**
 * A runtime representation of a DIFile.
 * 
 * @see <a href="https://llvm.org/docs/LangRef.html#difile">DIFile reference</a>
 */
public interface LLVMSourceFileReference {

    /**
     * @see <a href=
     *      "https://github.com/llvm/llvm-project/blob/llvmorg-11.0.0-rc1/llvm/include/llvm/IR/DebugInfoMetadata.h#L483">llvm::DIFile::ChecksumKind</a>
     */
    enum ChecksumKind {
        CSK_None,
        CSK_MD5,
        CSK_SHA1,
        CSK_SHA256;

        /**
         * Gets the kind from the encoded value in a bitcode file.
         */
        public static ChecksumKind forBitcodeValue(int value) {
            switch (value) {
                case 1:
                    return CSK_MD5;
                case 2:
                    return CSK_SHA1;
                case 3:
                    return CSK_SHA256;
                default:
                    return CSK_None;
            }
        }

        /**
         * Gets the kind from the name as used in .ll files.
         */
        static ChecksumKind forName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException e) {
                return CSK_None;
            }
        }
    }

    String getFilename();

    String getDirectory();

    ChecksumKind getChecksumKind();

    String getChecksum();

    static LLVMSourceFileReference create(String filename, String directory, String checksumKind, String checksum) {
        return new LLVMSourceFileReferenceImpl(filename, directory, ChecksumKind.forName(checksumKind), checksum);
    }

    Equivalence EQUIVALENCE = new Equivalence() {
        @Override
        public boolean equals(Object a, Object b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null) {
                return false;
            }
            if (!(a instanceof LLVMSourceFileReference)) {
                throw new IllegalArgumentException("Not applicable: " + a.getClass());
            }
            if (!(b instanceof LLVMSourceFileReference)) {
                throw new IllegalArgumentException("Not applicable: " + b.getClass());
            }
            LLVMSourceFileReference srcA = (LLVMSourceFileReference) a;
            LLVMSourceFileReference srcB = (LLVMSourceFileReference) b;
            return srcA.getChecksumKind() == srcB.getChecksumKind() &&
                            Objects.equals(srcA.getFilename(), srcB.getFilename()) &&
                            Objects.equals(srcA.getDirectory(), srcB.getDirectory()) &&
                            Objects.equals(srcA.getChecksum(), srcB.getChecksum());
        }

        @Override
        public int hashCode(Object o) {
            if (o instanceof LLVMSourceFileReference) {
                LLVMSourceFileReference src = (LLVMSourceFileReference) o;
                return Objects.hash(src.getFilename(), src.getDirectory(), src.getChecksumKind(), src.getChecksum());
            }
            throw new IllegalArgumentException("Not applicable: " + o.getClass());
        }
    };

    static String toString(LLVMSourceFileReference o) {
        // Using "DIFile" here since that is used in the .ll files as well
        return "DIFile(" +
                        "filename='" + o.getFilename() + '\'' +
                        ", directory='" + o.getDirectory() + '\'' +
                        ", checksumKind=" + o.getChecksumKind() +
                        ", checksum='" + o.getChecksum() + '\'' +
                        ')';
    }

    final class LLVMSourceFileReferenceImpl implements LLVMSourceFileReference {
        private final String filename;
        private final String directory;
        private final ChecksumKind checksumKind;
        private final String checksum;

        LLVMSourceFileReferenceImpl(String filename, String directory, ChecksumKind checksumKind, String checksum) {
            this.filename = filename;
            this.directory = directory;
            this.checksumKind = checksumKind;
            this.checksum = checksum;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public String getDirectory() {
            return directory;
        }

        @Override
        public ChecksumKind getChecksumKind() {
            return checksumKind;
        }

        @Override
        public String getChecksum() {
            return checksum;
        }
    }
}
