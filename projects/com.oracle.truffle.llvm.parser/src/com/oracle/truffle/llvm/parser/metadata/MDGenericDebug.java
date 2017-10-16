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
package com.oracle.truffle.llvm.parser.metadata;

import java.util.ArrayList;
import java.util.List;

public final class MDGenericDebug implements MDBaseNode {

    private final long tag;
    private final long version;

    private MDBaseNode header;
    private List<MDBaseNode> dwarfOps;

    private MDGenericDebug(long tag, long version, List<MDBaseNode> dwarfOps) {
        this.tag = tag;
        this.version = version;
        this.dwarfOps = dwarfOps;
        this.header = MDVoidNode.INSTANCE;
    }

    public long getTag() {
        return tag;
    }

    public long getVersion() {
        return version;
    }

    public MDBaseNode getHeader() {
        return header;
    }

    public List<MDBaseNode> getDwarfOps() {
        return dwarfOps;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        if (header == oldValue) {
            header = newValue;
        }
        for (int i = 0; i < dwarfOps.size(); i++) {
            if (dwarfOps.get(i) == oldValue) {
                dwarfOps.set(i, newValue);
            }
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private static final int ARGINDEX_TAG = 1;
    private static final int ARGINDEX_VERSION = 2;
    private static final int ARGINDEX_HEADER = 3;
    private static final int ARGINDEX_DATASTART = 4;

    public static MDGenericDebug create38(long[] args, MetadataValueList md) {
        final long tag = args[ARGINDEX_TAG];
        final long version = args[ARGINDEX_VERSION];

        final List<MDBaseNode> dwarfOps = new ArrayList<>(args.length - ARGINDEX_DATASTART);
        final MDGenericDebug debug = new MDGenericDebug(tag, version, dwarfOps);

        debug.header = md.getNullable(args[ARGINDEX_HEADER], debug);
        for (int i = ARGINDEX_DATASTART; i < args.length; i++) {
            dwarfOps.add(md.getNullable(args[i], debug));
        }

        return debug;
    }
}
