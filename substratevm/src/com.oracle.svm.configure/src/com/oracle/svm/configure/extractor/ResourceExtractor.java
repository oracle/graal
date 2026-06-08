/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.configure.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;

import com.oracle.svm.configure.ConfigurationUsageException;

/**
 * Class responsible for extracting compressed embedded resources from native images.
 */
public class ResourceExtractor {

    /**
     * Extract a gzipped resource and writes the content to the output stream.
     *
     * The image must export two symbols: one pointing to the start of the gzipped resource data
     * (e.g., "sbom"), and another pointing to its size (e.g., "sbom_length"). The argument "symbol"
     * refers to the symbol name that points to the start of the gzipped resource data. The size
     * symbol name is always the concatenation of the symbol name with "_length". These symbols are
     * used to locate the resource in the executable's or shared object's data section.
     *
     * This is implemented by memory mapping the image, finding the resource location in the image
     * using a {@link ResourceLocator}, extracting the gzipped resource slice, decompressing, and
     * writing to the provided output stream. {@link MemorySegment Memory segments} are used instead
     * of {@link java.nio.ByteBuffer byte buffers} to allow memory mapping images larger than 2 GB.
     *
     * @param image the path to the native image
     * @param symbol the symbol of the resource to extract
     * @param out the output stream to write the extracted resource to
     * @throws ConfigurationUsageException if there is an issue with the input file or parsing
     * @throws IOException if there is an I/O error during extraction or decompression
     */
    public static void extract(Path image, String symbol, OutputStream out) throws ConfigurationUsageException, IOException {
        try (FileChannel channel = FileChannel.open(image, StandardOpenOption.READ); Arena arena = Arena.ofConfined()) {
            MemorySegment memorySegment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
            ResourceLocation resourceLocation = locateResourceLocation(symbol, memorySegment);

            MemorySegment compressedResource = memorySegment.asSlice(resourceLocation.offset(), resourceLocation.size());
            decompressAndWrite(compressedResource, out);
        }
    }

    private static ResourceLocation locateResourceLocation(String symbol, MemorySegment segment) {
        ResourceLocatorFactory resourceLocatorFactory = new ResourceLocatorFactory();
        ResourceLocator resourceLocator = resourceLocatorFactory.create(segment);
        return resourceLocator.locateResource(symbol);
    }

    private static void decompressAndWrite(MemorySegment segment, OutputStream out) throws IOException {
        try (InputStream segmentInputStream = new MemorySegmentInputStream(segment);
                        InputStream gzipInputStream = new GZIPInputStream(segmentInputStream)) {
            gzipInputStream.transferTo(out);
        }
    }

    private static class MemorySegmentInputStream extends InputStream {
        private final MemorySegment segment;
        private long position = 0;

        MemorySegmentInputStream(MemorySegment segment) {
            this.segment = segment;
        }

        @Override
        public int read() {
            if (position >= segment.byteSize()) {
                return -1;
            }
            byte b = segment.get(ValueLayout.JAVA_BYTE, position);
            position++;
            // Convert signed byte to unsigned int
            return b & 0xFF;
        }

        @Override
        public void close() {
            // Segment closed by Arena
        }
    }
}
