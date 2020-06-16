/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

public final class MDNamespace extends MDNamedLocation implements MDBaseNode {

    private MDNamespace(long line) {
        super(line);
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private static final int ARGINDEX_SCOPE = 1;

    private static final int ARGINDEX_38_FILE = 2;
    private static final int ARGINDEX_38_NAME = 3;
    private static final int ARGINDEX_38_LINE = 4;

    private static final int RECORDSIZE_50 = 3;
    private static final int ARGINDEX_50_NAME = 2;

    public static MDNamespace create38(long[] args, MetadataValueList md) {
        if (RECORDSIZE_50 != args.length) {
            final long line = args[ARGINDEX_38_LINE];
            final MDNamespace namespace = new MDNamespace(line);
            namespace.setFile(md.getNullable(args[ARGINDEX_38_FILE], namespace));
            namespace.setScope(md.getNullable(args[ARGINDEX_SCOPE], namespace));
            namespace.setName(md.getNullable(args[ARGINDEX_38_NAME], namespace));
            return namespace;

        } else {
            final long line = -1L;
            final MDNamespace namespace = new MDNamespace(line);
            namespace.setScope(md.getNullable(args[ARGINDEX_SCOPE], namespace));
            namespace.setName(md.getNullable(args[ARGINDEX_50_NAME], namespace));
            return namespace;
        }
    }

    private static final int ARGINDEX_32_NAME = 2;
    private static final int ARGINDEX_32_FILE = 3;
    private static final int ARGINDEX_32_LINE = 4;

    public static MDNamespace create32(long[] args, Metadata md) {
        final long line = ParseUtil.asLong(args, ARGINDEX_32_LINE, md);
        final MDNamespace namespace = new MDNamespace(line);

        namespace.setScope(ParseUtil.resolveReference(args, ARGINDEX_SCOPE, namespace, md));
        namespace.setFile(ParseUtil.resolveReference(args, ARGINDEX_32_FILE, namespace, md));
        namespace.setName(ParseUtil.resolveReference(args, ARGINDEX_32_NAME, namespace, md));

        return namespace;
    }
}
