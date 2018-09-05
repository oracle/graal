/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

public final class MDAttachment implements MDBaseNode {

    private final MDKind kind;

    private MDBaseNode value;

    public MDAttachment(MDKind kind) {
        this.kind = kind;
        this.value = MDVoidNode.INSTANCE;
    }

    public MDKind getKind() {
        return kind;
    }

    public MDBaseNode getValue() {
        return value;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        if (value == oldValue) {
            value = newValue;
        }
    }

    @Override
    public String toString() {
        return String.format("Attachment !%s", kind);
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public static MDAttachment create(MDKind kind, long value, MetadataValueList md) {
        final MDAttachment attachment = new MDAttachment(kind);
        attachment.value = md.getNonNullable(value, attachment);
        return attachment;
    }

    public static MDAttachment create(MDKind kind, MDBaseNode value) {
        final MDAttachment attachment = new MDAttachment(kind);
        attachment.value = value;
        return attachment;
    }
}
