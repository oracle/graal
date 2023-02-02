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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Entry point to decompress resources.
 */
public final class Decompressor {

    private final Map<Integer, ResourceDecompressor> pluginsCache = new HashMap<>();

    /**
     * Decompress a resource.
     * 
     * @param order Byte order.
     * @param provider Strings provider
     * @param content The resource content to uncompress.
     * @return A fully uncompressed resource.
     */
    public ByteBuffer decompressResource(ByteOrder order, ResourceDecompressor.StringsProvider provider, ByteBuffer content) {
        Objects.requireNonNull(order);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(content);
        CompressedResourceHeader header;
        ByteBuffer currentContent = content;
        do {
            header = CompressedResourceHeader.readFromResource(currentContent);
            if (header != null) {
                ResourceDecompressor decompressor = pluginsCache.get(header.getDecompressorNameOffset());
                if (decompressor == null) {
                    String pluginName = provider.getString(header.getDecompressorNameOffset());
                    if (pluginName == null) {
                        throw EspressoError.shouldNotReachHere("Decompressor plugin name not found");
                    }
                    ByteBuffer storedContent = header.getStoredContent(provider);
                    Properties props = null;
                    if (storedContent != null) {
                        props = new Properties();
                        try (ByteBufferInputStream stream = new ByteBufferInputStream(storedContent)) {
                            // this API will guess the encoding from the InputStream
                            // it will use a BOM and/or the <xml header and defaults to UTF8
                            // The UTF8 reader actually only supports the UCS-2 subset of UTF8
                            // so the raw 'modified utf8' bytes can be read directly
                            // See jdk.internal.util.xml.impl.ReaderUTF8
                            props.loadFromXML(stream);
                        } catch (IOException e) {
                            throw EspressoError.shouldNotReachHere("Error while loading decompressor properties", e);
                        }
                    }
                    decompressor = ResourceDecompressorRepository.newResourceDecompressor(props, pluginName);
                    if (decompressor == null) {
                        throw EspressoError.shouldNotReachHere("Plugin not found: " + pluginName);
                    }
                    pluginsCache.put(header.getDecompressorNameOffset(), decompressor);
                }
                currentContent = decompressor.decompress(provider, currentContent, header.getUncompressedSize());
            }
        } while (header != null);
        return currentContent;
    }

    private static final class ByteBufferInputStream extends InputStream {
        private final ByteBuffer buffer;

        private ByteBufferInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public int read() throws IOException {
            if (!buffer.hasRemaining()) {
                return -1;
            }
            return buffer.get() & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int remaining = buffer.remaining();
            if (remaining == 0) {
                return -1;
            }
            int availableLength = Math.min(length, remaining);
            buffer.get(bytes, offset, availableLength);
            return availableLength;
        }
    }
}
