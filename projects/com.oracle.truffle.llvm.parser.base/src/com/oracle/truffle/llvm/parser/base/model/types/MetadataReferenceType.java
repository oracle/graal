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
package com.oracle.truffle.llvm.parser.base.model.types;

import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock;

public interface MetadataReferenceType {
    MetadataBlock.MetadataReference getMetadataReference();

    void setMetadataReference(MetadataBlock.MetadataReference metadata);

    /**
     * Checks if a MetadataReference was already set, and if yes checks if it equals with the new
     * one.
     *
     * When this check fails it gives an AssertionError, otherwise it behaves like
     * setMetadataReference. This is required to spot contradictory type definitions (which
     * shouldn't happen)
     *
     * @param metadata MetadataReference where this type was defined
     *
     * @throws AssertionError()
     */
    default void setValidatedMetadataReference(MetadataBlock.MetadataReference metadata) {
        // the only valid writing would happen when the reference was empty before
        if (getMetadataReference() == MetadataBlock.voidRef) {
            setMetadataReference(metadata);
        }

        if (!getMetadataReference().equals(metadata)) {
            throw new AssertionError("contradictory type informations given");
        }
    }
}
