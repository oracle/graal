/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.jimage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import com.oracle.truffle.espresso.runtime.jimage.decompressor.Decompressor;

/**
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 *           It is used internally in the JDK to implement jimage/jrtfs access, but also compiled
 *           and delivered as part of the jrtfs.jar to support access to the jimage file provided by
 *           the shipped JDK by tools running on JDK 8.
 */
public class BasicImageReader implements AutoCloseable {
    private final ByteOrder byteOrder;
    private final String name;
    private final ByteBuffer memoryMap;
    private final FileChannel channel;
    private final ImageHeader header;
    private final long indexSize;
    private final IntBuffer redirect;
    private final IntBuffer offsets;
    private final ByteBuffer locations;
    private final ByteBuffer strings;
    private final ImageStringsReader stringsReader;
    private final Decompressor decompressor;

    protected BasicImageReader(Path path, ByteOrder byteOrder)
                    throws IOException {
        Path imagePath = Objects.requireNonNull(path);
        this.byteOrder = Objects.requireNonNull(byteOrder);
        this.name = imagePath.toString();

        channel = FileChannel.open(imagePath, StandardOpenOption.READ);

        ByteBuffer map = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());

        int headerSize = ImageHeader.getHeaderSize();
        if (map.capacity() < headerSize) {
            throw new IOException("\"" + name + "\" is not an image file");
        }

        // Interpret the image file header
        header = readHeader(intBuffer(map, 0, headerSize));
        indexSize = header.getIndexSize();

        memoryMap = map.asReadOnlyBuffer();

        // Interpret the image index
        if (memoryMap.capacity() < indexSize) {
            throw new IOException("The image file \"" + name + "\" is corrupted");
        }
        redirect = intBuffer(memoryMap, header.getRedirectOffset(), header.getRedirectSize());
        offsets = intBuffer(memoryMap, header.getOffsetsOffset(), header.getOffsetsSize());
        locations = slice(memoryMap, header.getLocationsOffset(), header.getLocationsSize());
        strings = slice(memoryMap, header.getStringsOffset(), header.getStringsSize());

        stringsReader = new ImageStringsReader(this);
        decompressor = new Decompressor();
    }

    public static BasicImageReader open(Path path) throws IOException {
        return new BasicImageReader(path, ByteOrder.nativeOrder());
    }

    public ImageHeader getHeader() {
        return header;
    }

    private ImageHeader readHeader(IntBuffer buffer) throws IOException {
        ImageHeader result = ImageHeader.readFrom(buffer);

        if (result.getMagic() != ImageHeader.MAGIC) {
            throw new IOException("\"" + name + "\" is not an image file");
        }

        if (result.getMajorVersion() != ImageHeader.MAJOR_VERSION ||
                        result.getMinorVersion() != ImageHeader.MINOR_VERSION) {
            throw new IOException("The image file \"" + name + "\" is not " +
                            "the correct version. Major: " + result.getMajorVersion() +
                            ". Minor: " + result.getMinorVersion());
        }

        return result;
    }

    private static ByteBuffer slice(ByteBuffer buffer, int position, int capacity) {
        // Note that this is the only limit and position manipulation of
        // BasicImageReader private ByteBuffers. The synchronize could be avoided
        // by cloning the buffer to make a local copy, but at the cost of creating
        // a new object.
        synchronized (buffer) {
            buffer.limit(position + capacity);
            buffer.position(position);
            return buffer.slice();
        }
    }

    private IntBuffer intBuffer(ByteBuffer buffer, int offset, int size) {
        return slice(buffer, offset, size).order(byteOrder).asIntBuffer();
    }

    public String getName() {
        return name;
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
        }
    }

    public ImageStringsReader getStrings() {
        return stringsReader;
    }

    public ImageLocation findLocation(String module, String path) {
        int index = getLocationIndex(module, path);
        if (index < 0) {
            return null;
        }
        long[] attributes = getAttributes(offsets.get(index));
        // Make sure result is not a false positive.
        if (!ImageLocation.verify(module, path, attributes, stringsReader)) {
            return null;
        }
        return new ImageLocation(attributes, stringsReader);
    }

    public ImageLocation findLocation(String path) {
        int index = getLocationIndex(path);
        if (index < 0) {
            return null;
        }
        long[] attributes = getAttributes(offsets.get(index));
        if (!ImageLocation.verify(path, attributes, stringsReader)) {
            return null;
        }
        return new ImageLocation(attributes, stringsReader);
    }

    // Details of the algorithm used here can be found in
    // jdk.tools.jlink.internal.PerfectHashBuilder.
    public int getLocationIndex(String path) {
        int count = header.getTableLength();
        int index = redirect.get(ImageStringsReader.hashCode(path) % count);
        if (index < 0) {
            // index is twos complement of location attributes index.
            return -index - 1;
        } else if (index > 0) {
            // index is hash seed needed to compute location attributes index.
            return ImageStringsReader.hashCode(path, index) % count;
        } else {
            // No entry.
            return -1;
        }
    }

    private int getLocationIndex(String module, String path) {
        int count = header.getTableLength();
        int index = redirect.get(ImageStringsReader.hashCode(module, path) % count);
        if (index < 0) {
            // index is twos complement of location attributes index.
            return -index - 1;
        } else if (index > 0) {
            // index is hash seed needed to compute location attributes index.
            return ImageStringsReader.hashCode(module, path, index) % count;
        } else {
            // No entry.
            return -1;
        }
    }

    public long[] getAttributes(int offset) {
        if (offset < 0 || offset >= locations.limit()) {
            throw new IndexOutOfBoundsException("offset");
        }
        return ImageLocation.decompress(locations, offset);
    }

    public String getString(int offset) {
        if (offset < 0 || offset >= strings.limit()) {
            throw new IndexOutOfBoundsException("offset");
        }
        return ImageStringsReader.stringFromByteBuffer(strings, offset);
    }

    public int match(int offset, String string, int stringOffset) {
        if (offset < 0 || offset >= strings.limit()) {
            throw new IndexOutOfBoundsException("offset");
        }
        return ImageStringsReader.stringFromByteBufferMatches(strings, offset, string, stringOffset);
    }

    private static byte[] getBufferBytes(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        return bytes;
    }

    private ByteBuffer readBuffer(long offset, long size) {
        if (offset < 0 || Integer.MAX_VALUE <= offset) {
            throw new IndexOutOfBoundsException("Bad offset: " + offset);
        }

        if (size < 0 || Integer.MAX_VALUE <= size) {
            throw new IndexOutOfBoundsException("Bad size: " + size);
        }

        ByteBuffer buffer = slice(memoryMap, (int) offset, (int) size);
        buffer.order(byteOrder);

        return buffer;
    }

    public byte[] getResource(ImageLocation loc) {
        ByteBuffer buffer = getResourceBuffer(loc);
        if (buffer != null) {
            return getBufferBytes(buffer);
        }
        return null;
    }

    public ByteBuffer getResourceBuffer(ImageLocation loc) {
        Objects.requireNonNull(loc);
        long offset = loc.getContentOffset() + indexSize;
        long compressedSize = loc.getCompressedSize();
        long uncompressedSize = loc.getUncompressedSize();

        if (compressedSize < 0 || Integer.MAX_VALUE < compressedSize) {
            throw new IndexOutOfBoundsException("Bad compressed size: " + compressedSize);
        }

        if (uncompressedSize < 0 || Integer.MAX_VALUE < uncompressedSize) {
            throw new IndexOutOfBoundsException("Bad uncompressed size: " + uncompressedSize);
        }

        if (compressedSize == 0) {
            return readBuffer(offset, uncompressedSize);
        } else {
            ByteBuffer buffer = readBuffer(offset, compressedSize);

            byte[] bytesIn = getBufferBytes(buffer);
            byte[] bytesOut;

            try {
                bytesOut = decompressor.decompressResource(byteOrder, this::getString, bytesIn);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            return ByteBuffer.wrap(bytesOut).order(byteOrder);
        }
    }
}
