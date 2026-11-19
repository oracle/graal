/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.host;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Utility methods to read zip file.
 */
final class ZipUtils {

    private static final long UINT32_MAX_VALUE = 0xffffffffL;
    private static final int UINT16_MAX_VALUE = 0xffff;
    private static final int ZIP64_EXTRA_ID = 0x0001;
    private static final int DATA_DESCRIPTOR_FLAG = 0x0008;
    private static final long ZIP64_ENDSIG = 0x06064b50L;
    private static final long ZIP64_LOCSIG = 0x07064b50L;
    private static final int ZIP64_ENDHDR = 56;
    private static final int ZIP64_LOCHDR = 20;
    private static final int ZIP64_EXTHDR = 24;
    private static final int ZIP64_ENDLEN = 4;
    private static final int ZIP64_ENDTOT = 32;
    private static final int ZIP64_ENDSIZ = 40;
    private static final int ZIP64_ENDOFF = 48;
    private static final int ZIP64_LOCOFF = 8;

    private ZipUtils() {
        throw new IllegalStateException("No instance allowed.");
    }

    /**
     * Adapts {@link SeekableByteChannel} to {@link InputStream}.
     */
    private static class ChannelInputStream extends InputStream {

        final SeekableByteChannel channel;
        private final long end;

        ChannelInputStream(SeekableByteChannel channel) throws IOException {
            this(channel, channel.size());
        }

        ChannelInputStream(SeekableByteChannel channel, long end) {
            this.channel = channel;
            this.end = end;
        }

        @Override
        public int read(byte[] data, int offset, int size) throws IOException {
            Objects.checkFromIndexSize(offset, size, data.length);
            if (size == 0) {
                return 0;
            }
            int rem = available();
            if (rem == 0) {
                return -1;
            }
            ByteBuffer buffer = ByteBuffer.wrap(data, offset, Math.min(size, rem));
            int read;
            do {
                read = channel.read(buffer);
            } while (read == 0);
            return read;
        }

        @Override
        public int read() throws IOException {
            byte[] data = new byte[1];
            return read(data, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(data[0]);
        }

        @Override
        public int available() throws IOException {
            // SeekableByteChannel can seek beyond file size, prevent negative value by Math.max
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, end - this.channel.position()));
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    /** Reads a bounded STORED payload and verifies its trailing data descriptor. */
    private static final class StoredInputStream extends ChannelInputStream {

        private final long size;
        private final boolean zip64Descriptor;
        private final CRC32 crc = new CRC32();
        private long remaining;

        StoredInputStream(SeekableByteChannel channel, long size, long end, boolean zip64Descriptor) throws IOException {
            super(channel, end);
            this.size = size;
            this.remaining = size;
            this.zip64Descriptor = zip64Descriptor;
            if (size == 0) {
                checkDescriptor();
            }
        }

        @Override
        public int read(byte[] data, int offset, int length) throws IOException {
            int read = super.read(data, offset, length);
            if (read < 0 && remaining > 0) {
                throw new ZipException("Unexpected EOF in STORED entry data");
            }
            if (read > 0) {
                crc.update(data, offset, read);
                remaining -= read;
                if (remaining == 0) {
                    // Check on the last payload read: class loading need not request EOF.
                    checkDescriptor();
                }
            }
            return read;
        }

        private void checkDescriptor() throws IOException {
            int length = zip64Descriptor ? ZIP64_EXTHDR : ZipFile.EXTHDR;
            ByteBuffer descriptor = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
            descriptor.limit(length - Integer.BYTES);
            if (readFully(channel, descriptor) != length - Integer.BYTES) {
                throw new ZipException("Truncated STORED entry data descriptor");
            }
            // Try the signatureless form first, including when the CRC equals EXTSIG.
            if (matchesDescriptor(descriptor, 0)) {
                return;
            }
            if (getsig(descriptor) == ZipFile.EXTSIG) {
                descriptor.limit(length);
                if (readFully(channel, descriptor) != Integer.BYTES) {
                    throw new ZipException("Truncated STORED entry data descriptor");
                }
                if (matchesDescriptor(descriptor, Integer.BYTES)) {
                    return;
                }
            }
            throw new ZipException("Invalid STORED entry data descriptor (CRC or size mismatch)");
        }

        private boolean matchesDescriptor(ByteBuffer descriptor, int start) {
            long checksum = descriptor.getInt(start) & UINT32_MAX_VALUE;
            int offset = start + Integer.BYTES;
            long compressedSize = zip64Descriptor ? descriptor.getLong(offset) : descriptor.getInt(offset) & UINT32_MAX_VALUE;
            offset += zip64Descriptor ? Long.BYTES : Integer.BYTES;
            long uncompressedSize = zip64Descriptor ? descriptor.getLong(offset) : descriptor.getInt(offset) & UINT32_MAX_VALUE;
            return checksum == crc.getValue() && compressedSize == size && uncompressedSize == size;
        }
    }

    static final class Info {
        /**
         * Offset of a zip entry in the zip file.
         */
        final long offset;
        /**
         * Uncompressed size of the zipped file.
         */
        final long size;
        /**
         * Whether this STORED entry defers its CRC and sizes to a data descriptor.
         */
        final boolean storedWithDescriptor;

        Info(long offset, long size, boolean storedWithDescriptor) {
            this.offset = offset;
            this.size = size;
            this.storedWithDescriptor = storedWithDescriptor;
        }
    }

    /**
     * Returns an {@link InputStream} for the entry identified by its central-directory metadata.
     * The returned stream owns the channel. Opening failures also close the channel.
     *
     * @param channel the channel to read the entry from
     * @param info the central-directory metadata identifying the entry
     * @return the {@link InputStream} to read the uncompressed zipped file content
     * @throws IOException in case of IO error
     */
    static InputStream getInputStream(SeekableByteChannel channel, Info info) throws IOException {
        InputStream in = null;
        try {
            channel.position(info.offset);
            if (info.storedWithDescriptor) {
                return getStoredInputStream(channel, info.size);
            }
            ZipInputStream zip = new ZipInputStream(new ChannelInputStream(channel));
            in = zip;
            zip.getNextEntry();
            return zip;
        } catch (Throwable failure) {
            try {
                if (in != null) {
                    in.close();
                } else {
                    channel.close();
                }
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static InputStream getStoredInputStream(SeekableByteChannel channel, long size) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(ZipFile.LOCHDR).order(ByteOrder.LITTLE_ENDIAN);
        if (readFully(channel, header) != ZipFile.LOCHDR || getsig(header) != ZipFile.LOCSIG) {
            throw new ZipException("Invalid LOC header (bad signature or truncated header)");
        }
        int flags = header.getShort(ZipFile.LOCFLG) & UINT16_MAX_VALUE;
        if ((flags & 1) != 0) {
            throw new ZipException("Invalid LOC header (encrypted entry)");
        }
        if ((header.getShort(ZipFile.LOCHOW) & UINT16_MAX_VALUE) != ZipEntry.STORED || (flags & DATA_DESCRIPTOR_FLAG) == 0) {
            throw new ZipException("Invalid LOC header (inconsistent STORED data-descriptor entry)");
        }
        long position = channel.position();
        long fileSize = channel.size();
        int skip = (header.getShort(ZipFile.LOCNAM) & UINT16_MAX_VALUE) + (header.getShort(ZipFile.LOCEXT) & UINT16_MAX_VALUE);
        if (skip > fileSize - position) {
            throw new ZipException("Invalid LOC header (truncated name or extra fields)");
        }
        long start = position + skip;
        if (size < 0 || size > fileSize - start) {
            throw new ZipException("Truncated STORED entry data");
        }
        long end = start + size;
        boolean zip64Descriptor = (header.getInt(ZipFile.LOCSIZ) & UINT32_MAX_VALUE) == UINT32_MAX_VALUE ||
                        (header.getInt(ZipFile.LOCLEN) & UINT32_MAX_VALUE) == UINT32_MAX_VALUE;
        if (fileSize - end < (zip64Descriptor ? ZIP64_EXTHDR : ZipFile.EXTHDR) - Integer.BYTES) {
            throw new ZipException("Truncated STORED entry data descriptor");
        }
        channel.position(start);
        return new StoredInputStream(channel, size, end, zip64Descriptor);
    }

    /**
     * Reads the zip file central table into map of folders.
     *
     * @param channel to read from
     * @return the map mapping folders to simple names and {@link Info infos}
     * @throws IOException in case of IO error
     */
    static Map<String, Map<String, Info>> readEntries(SeekableByteChannel channel) throws IOException {
        End end = findEnd(channel);
        long censize = end.size();
        long cenpos = end.position() - censize;
        long locpos = cenpos - end.offset();
        channel.position(cenpos);

        Map<String, Map<String, Info>> result = new HashMap<>();
        long cenread = 0;
        ByteBuffer data = ByteBuffer.allocate(ZipFile.CENHDR);
        data.order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer variableData = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
        while (censize - cenread >= ZipFile.CENHDR) {
            data.clear();
            if (readFully(channel, data) != ZipFile.CENHDR) {
                throw new ZipException("Invalid CEN header (truncated central directory header)");
            }
            if (getsig(data) != ZipFile.CENSIG) {
                throw new ZipException("Invalid CEN header (bad signature)");
            }
            int flags = data.getShort(ZipFile.CENFLG) & UINT16_MAX_VALUE;
            if ((flags & 1) != 0) {
                throw new ZipException("Invalid CEN header (encrypted entry)");
            }
            int method = data.getShort(ZipFile.CENHOW) & UINT16_MAX_VALUE;
            if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
                throw new ZipException("Invalid CEN header (bad compression method: " + method + ")");
            }
            int cennam = cennam(data);
            int cenext = cenext(data);
            int cencom = cencom(data);
            long lhoff = cenoff(data);
            if (lhoff > Long.MAX_VALUE - locpos) {
                throw new ZipException("Invalid CEN header (local header offset overflow)");
            }
            lhoff += locpos;
            long cenlen = cenlen(data);
            long cendatalen = (long) ZipFile.CENHDR + cennam + cenext + cencom;
            // As in ZipFile, the complete CEN record must fit in 65,535 bytes and in the
            // remaining directory region. Check before consuming any variable-length fields.
            if (cendatalen > UINT16_MAX_VALUE || cendatalen > censize - cenread) {
                throw new ZipException("Invalid CEN header (bad header size)");
            }
            String name = name(channel, cennam);
            int capacity = Math.max(cenext, cencom);
            if (variableData.capacity() < capacity) {
                variableData = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
            }
            variableData.clear().limit(cenext);
            if (readFully(channel, variableData) != cenext) {
                throw new ZipException("Invalid CEN header (truncated extra data field)");
            }
            variableData.flip();
            checkExtraFields(data, variableData);
            if (cencom > 0) {
                variableData.clear().limit(cencom);
                if (readFully(channel, variableData) != cencom) {
                    throw new ZipException("Invalid CEN header (truncated entry comment)");
                }
                variableData.flip();
                // Validate comments even though they are not retained in the resource index.
                decodeUTF8(variableData, "entry comment");
            }
            cenread += cendatalen;
            if (!isDirectory(name)) {
                String[] parts = split(name);
                Map<String, Info> names = result.computeIfAbsent(parts[0], new Function<String, Map<String, Info>>() {
                    @Override
                    public Map<String, Info> apply(String t) {
                        return new HashMap<>();
                    }
                });
                boolean storedWithDescriptor = method == ZipEntry.STORED && (flags & DATA_DESCRIPTOR_FLAG) != 0;
                names.put(parts[1], new Info(lhoff, cenlen, storedWithDescriptor));
            }
        }
        if (cenread != censize) {
            throw new ZipException("Invalid CEN header (bad header size)");
        }
        return result;
    }

    /**
     * Central-directory metadata from an ordinary or ZIP64 end-of-central-directory record.
     *
     * @param position absolute byte offset of the ordinary END record, or of the ZIP64 END record
     *            when ZIP64 metadata is used
     * @param size length of the central directory in bytes; its absolute start is
     *            {@code position - size}
     * @param offset recorded central-directory byte offset relative to the start of the ZIP data,
     *            excluding any prepended stub; {@code position - size - offset} is the stub length
     * @param entries advertised total entry count, used for END/ZIP64 consistency checks rather than
     *            resource-index allocation or iteration
     */
    private record End(long position, long size, long offset, long entries) {
    }

    /**
     * Finds and validates the archive's end-of-central-directory metadata.
     * <p>
     * Searches backwards within the maximum ZIP comment window without seeking before the file
     * start. Candidates must account for the comment length; trailing padding is accepted only when
     * central-directory and local-header signatures support the candidate. A ZIP64 locator is
     * inspected even when the ordinary END fields are not saturated, and the resulting directory
     * size and offset are validated before the directory is indexed.
     *
     * @param channel the archive channel; left open with an unspecified position after the call
     * @return ordinary END metadata, or ZIP64 END metadata when a valid locator is present
     * @throws ZipException if no END record is found or the selected END/ZIP64 metadata is invalid
     * @throws IOException if reading or positioning the channel fails
     */
    private static End findEnd(SeekableByteChannel channel) throws IOException {
        long fileSize = channel.size();
        if (fileSize < ZipFile.ENDHDR) {
            throw new ZipException("ZIP end header not found.");
        }
        // The search window is bounded by the file start and the maximum ZIP comment length.
        int length = (int) Math.min(fileSize, (long) ZipFile.ENDHDR + UINT16_MAX_VALUE);
        long start = fileSize - length;
        ByteBuffer tail = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        channel.position(start);
        if (readFully(channel, tail) != length) {
            throw new ZipException("Truncated ZIP end header search region");
        }
        for (int pos = length - ZipFile.ENDHDR; pos >= 0; pos--) {
            if ((tail.getInt(pos) & UINT32_MAX_VALUE) != ZipFile.ENDSIG) {
                continue;
            }
            int commentLength = tail.getShort(pos + ZipFile.ENDCOM) & UINT16_MAX_VALUE;
            int availableComment = length - pos - ZipFile.ENDHDR;
            if (commentLength > availableComment) {
                continue;
            }
            End end = new End(start + pos, tail.getInt(pos + ZipFile.ENDSIZ) & UINT32_MAX_VALUE,
                            tail.getInt(pos + ZipFile.ENDOFF) & UINT32_MAX_VALUE, tail.getShort(pos + ZipFile.ENDTOT) & UINT16_MAX_VALUE);
            if (commentLength != availableComment) {
                // Like ZipFile, allow trailing padding only when CEN and LOC signatures support
                // the candidate. Otherwise it may just be an END signature inside a comment.
                try {
                    end = readZip64End(channel, end);
                    checkEnd(end);
                } catch (ZipException invalidCandidate) {
                    continue;
                }
                if (!hasEndStructure(channel, end)) {
                    continue;
                }
            } else {
                end = readZip64End(channel, end);
                checkEnd(end);
            }
            return end;
        }
        throw new ZipException("ZIP end header not found.");
    }

    private static End readZip64End(SeekableByteChannel channel, End end) throws IOException {
        if (end.position() < ZIP64_LOCHDR) {
            return end;
        }
        long locatorPosition = end.position() - ZIP64_LOCHDR;
        ByteBuffer locator = ByteBuffer.allocate(ZIP64_LOCHDR).order(ByteOrder.LITTLE_ENDIAN);
        channel.position(locatorPosition);
        if (readFully(channel, locator) != ZIP64_LOCHDR || getsig(locator) != ZIP64_LOCSIG) {
            return end;
        }
        // Always inspect a locator, even when the ordinary END fields are not saturated.
        long position = locator.getLong(ZIP64_LOCOFF);
        if (position < 0 || position > locatorPosition - ZIP64_ENDHDR) {
            throw new ZipException("Invalid ZIP64 END header (bad record offset)");
        }
        ByteBuffer data = ByteBuffer.allocate(ZIP64_ENDHDR).order(ByteOrder.LITTLE_ENDIAN);
        channel.position(position);
        if (readFully(channel, data) != ZIP64_ENDHDR || getsig(data) != ZIP64_ENDSIG) {
            throw new ZipException("Invalid ZIP64 END header (bad signature or truncated record)");
        }
        long recordLength = data.getLong(ZIP64_ENDLEN);
        // The length excludes the signature and length field; extensions may follow the header.
        if (recordLength < ZIP64_ENDHDR - 12 || recordLength != locatorPosition - position - 12) {
            throw new ZipException("Invalid ZIP64 END header (bad record size)");
        }
        End zip64 = new End(position, data.getLong(ZIP64_ENDSIZ), data.getLong(ZIP64_ENDOFF), data.getLong(ZIP64_ENDTOT));
        checkEnd(zip64);
        if (end.size() != UINT32_MAX_VALUE && end.size() != zip64.size() ||
                        end.offset() != UINT32_MAX_VALUE && end.offset() != zip64.offset() ||
                        end.entries() != UINT16_MAX_VALUE && end.entries() != zip64.entries()) {
            throw new ZipException("Invalid ZIP64 END header (inconsistent END values)");
        }
        return zip64;
    }

    private static void checkEnd(End end) throws ZipException {
        if (end.size() < 0 || end.size() > end.position()) {
            throw new ZipException("Invalid END header (bad central directory size)");
        }
        if (end.offset() < 0 || end.offset() > end.position() - end.size()) {
            throw new ZipException("Invalid END header (bad central directory offset)");
        }
        if (end.entries() < 0) {
            throw new ZipException("Invalid END header (negative entry count)");
        }
    }

    private static boolean hasEndStructure(SeekableByteChannel channel, End end) throws IOException {
        long cenpos = end.position() - end.size();
        ByteBuffer signature = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        channel.position(cenpos);
        if (readFully(channel, signature) != Integer.BYTES || getsig(signature) != ZipFile.CENSIG) {
            return false;
        }
        signature.clear();
        channel.position(cenpos - end.offset());
        return readFully(channel, signature) == Integer.BYTES && getsig(signature) == ZipFile.LOCSIG;
    }

    /**
     * Splits a {@code resourceName} into folder and base file name.
     *
     * @param resourceName the name to split
     * @return an array containing parent folder and base file name.
     */
    static String[] split(String resourceName) {
        int index = resourceName.lastIndexOf('/');
        if (index < 0) {
            return new String[]{"", resourceName};
        } else {
            return new String[]{
                            resourceName.substring(0, index),
                            resourceName.substring(index + 1, resourceName.length())
            };
        }
    }

    /**
     * Validates central-directory extra-field boundaries and ZIP64 metadata against the header's
     * sentinel values. ZIP64 blocks must contain exactly the fields indicated by those sentinels;
     * uncompressed sizes, compressed sizes, and local-header offsets must be non-negative. The disk
     * number contributes to the required block length, but its value is not validated.
     * <p>
     * Like {@link ZipFile}, accepts empty ZIP64 blocks when no sentinel requires a ZIP64 value and
     * ignores incomplete trailing extra-field headers for compatibility with older ZIP producers.
     * This method validates metadata without resolving ZIP64 values into the entry information.
     */
    private static void checkExtraFields(ByteBuffer header, ByteBuffer extra) throws ZipException {
        boolean size64 = cenlen(header) == UINT32_MAX_VALUE;
        boolean compressedSize64 = (header.getInt(ZipFile.CENSIZ) & UINT32_MAX_VALUE) == UINT32_MAX_VALUE;
        boolean offset64 = cenoff(header) == UINT32_MAX_VALUE;
        boolean disk64 = (header.getShort(ZipFile.CENDSK) & UINT16_MAX_VALUE) == UINT16_MAX_VALUE;
        int expectedZip64Length = (size64 ? Long.BYTES : 0) + (compressedSize64 ? Long.BYTES : 0) +
                        (offset64 ? Long.BYTES : 0) + (disk64 ? Integer.BYTES : 0);
        if (!extra.hasRemaining() && expectedZip64Length != 0) {
            throw new ZipException("Invalid CEN header (missing ZIP64 extra data field)");
        }
        // Ignore incomplete trailing headers, as ZipFile does for older ZIP producers.
        while (extra.remaining() >= 2 * Short.BYTES) {
            int tag = extra.getShort() & UINT16_MAX_VALUE;
            int blockSize = extra.getShort() & UINT16_MAX_VALUE;
            if (blockSize > extra.remaining()) {
                throw new ZipException("Invalid CEN header (invalid extra data field size)");
            }
            int blockEnd = extra.position() + blockSize;
            if (tag == ZIP64_EXTRA_ID) {
                // Fields occur only for sentinel values in the CEN, in this fixed order.
                // An empty block is allowed for older Ant/Commons Compress archives, but
                // only if no CEN field requires a ZIP64 value.
                if (blockSize != expectedZip64Length) {
                    throw new ZipException("Invalid CEN header (invalid ZIP64 extra data field size)");
                }
                if (size64 && extra.getLong() < 0) {
                    throw new ZipException("Invalid ZIP64 extra block size value");
                }
                if (compressedSize64 && extra.getLong() < 0) {
                    throw new ZipException("Invalid ZIP64 extra block compressed size value");
                }
                if (offset64 && extra.getLong() < 0) {
                    throw new ZipException("Invalid ZIP64 extra block LOC offset value");
                }
                // The disk number contributes to the length, but its value is unused.
            }
            extra.position(blockEnd);
        }
    }

    private static String name(SeekableByteChannel channel, int cennam) throws IOException {
        ByteBuffer name = ByteBuffer.allocate(cennam);
        if (readFully(channel, name) != cennam) {
            throw new ZipException("Invalid CEN header (truncated entry name)");
        }
        name.flip();
        return decodeUTF8(name, "entry name");
    }

    private static String decodeUTF8(ByteBuffer data, String field) throws ZipException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(data).toString();
        } catch (CharacterCodingException cause) {
            ZipException error = new ZipException("Invalid CEN header (bad " + field + ")");
            error.initCause(cause);
            throw error;
        }
    }

    private static boolean isDirectory(String name) {
        return name.endsWith("/");
    }

    private static int readFully(SeekableByteChannel channel, ByteBuffer buffer) throws IOException {
        int res = 0;
        while (buffer.remaining() > 0) {
            int read = channel.read(buffer);
            if (read == -1) {
                break;
            }
            res += read;
        }
        return res;
    }

    private static long getsig(ByteBuffer b) {
        return b.getInt(0) & UINT32_MAX_VALUE;
    }

    private static long cenlen(ByteBuffer b) {
        return b.getInt(ZipFile.CENLEN) & UINT32_MAX_VALUE;
    }

    private static int cennam(ByteBuffer b) {
        return b.getShort(ZipFile.CENNAM) & UINT16_MAX_VALUE;
    }

    private static int cenext(ByteBuffer b) {
        return b.getShort(ZipFile.CENEXT) & UINT16_MAX_VALUE;
    }

    private static int cencom(ByteBuffer b) {
        return b.getShort(ZipFile.CENCOM) & UINT16_MAX_VALUE;
    }

    private static long cenoff(ByteBuffer b) {
        return b.getInt(ZipFile.CENOFF) & UINT32_MAX_VALUE;
    }

}
