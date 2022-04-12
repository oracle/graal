/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.runtime.jimage.decompressor;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A resource header for compressed resource. This class is handled internally, you don't have to
 * add header to the resource, headers are added automatically for compressed resources.
 */
public final class CompressedResourceHeader {

    private static final int SIZE = 29;
    public static final int MAGIC = 0xCAFEFAFA;
    private final long uncompressedSize;
    private final long compressedSize;
    private final int decompressorNameOffset;
    private final int contentOffset;
    private final boolean isTerminal;

    public CompressedResourceHeader(long compressedSize,
                    long uncompressedSize, int decompressorNameOffset, int contentOffset,
                    boolean isTerminal) {
        this.compressedSize = compressedSize;
        this.uncompressedSize = uncompressedSize;
        this.decompressorNameOffset = decompressorNameOffset;
        this.contentOffset = contentOffset;
        this.isTerminal = isTerminal;
    }

    public boolean isTerminal() {
        return isTerminal;
    }

    public int getDecompressorNameOffset() {
        return decompressorNameOffset;
    }

    public ByteBuffer getStoredContent(ResourceDecompressor.StringsProvider provider) {
        Objects.requireNonNull(provider);
        if (contentOffset == -1) {
            return null;
        }
        return provider.getRawString(contentOffset);
    }

    public long getUncompressedSize() {
        return uncompressedSize;
    }

    public long getResourceSize() {
        return compressedSize;
    }

    public static int getSize() {
        return SIZE;
    }

    public static CompressedResourceHeader readFromResource(ByteBuffer resource) {
        Objects.requireNonNull(resource);
        if (resource.remaining() < getSize()) {
            return null;
        }
        int magic = resource.getInt();
        if (magic != MAGIC) {
            resource.position(resource.position() - 4);
            return null;
        }
        long compressedSize = resource.getLong();
        long uncompressedSize = resource.getLong();
        int decompressorNameOffset = resource.getInt();
        int contentIndex = resource.getInt();
        byte isTerminal = resource.get();
        return new CompressedResourceHeader(compressedSize, uncompressedSize, decompressorNameOffset, contentIndex, isTerminal == 1);
    }
}
