/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;

/**
 * ZIP Decompressor
 */
final class ZipDecompressor implements ResourceDecompressor {
    public static final String NAME = "zip";

    @Override
    public String getName() {
        return NAME;
    }

    static byte[] decompress(byte[] bytesIn, int offset, int originalSize) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(bytesIn, offset, bytesIn.length - offset);
        ByteArrayOutputStream stream = new ByteArrayOutputStream(originalSize);
        byte[] buffer = new byte[1024];

        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            stream.write(buffer, 0, count);
        }

        stream.close();

        byte[] bytesOut = stream.toByteArray();
        inflater.end();

        return bytesOut;
    }

    @Override
    public byte[] decompress(StringsProvider reader, byte[] content, int offset, long originalSize) throws Exception {
        return decompress(content, offset, Math.toIntExact(originalSize));
    }
}
