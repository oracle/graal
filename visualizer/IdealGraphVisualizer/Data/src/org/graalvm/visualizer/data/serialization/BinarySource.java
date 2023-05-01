/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.data.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import static org.graalvm.visualizer.data.serialization.StreamSource.CURRENT_MAJOR_VERSION;
import static org.graalvm.visualizer.data.serialization.StreamSource.CURRENT_MINOR_VERSION;
import static org.graalvm.visualizer.data.serialization.StreamSource.CURRENT_VERSION;
import static org.graalvm.visualizer.data.serialization.StreamSource.MAGIC_BYTES;
import static org.graalvm.visualizer.data.serialization.StreamSource.versionPair;
import static org.graalvm.visualizer.data.serialization.StreamUtils.maybeIntern;

/**
 * Performs basic decoding, manages the input buffer. Reports the current position.
 */
public class BinarySource implements DataSource {

    public static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * For data sources on just part of stream, the base offset is the offset of the
     * processed portion from the stream start. Used to compute absolute positions
     * to match entries discovered during initial stream scan
     */
    private final long baseOffset;
    private final ByteBuffer buffer;
    private int lastPosition = 0;
    final ReadableByteChannel channel;
    long bufferOffset;

    private int majorVersion;
    private int minorVersion;
    private MessageDigest digest;
    private boolean performDigest;
    
    private Object sourceId;

    public BinarySource(Object sourceId, ReadableByteChannel channel) {
        this(channel, 0, 0, 0);
        this.sourceId = sourceId;
    }
    
    /**
     * Constructs source
     * @param channel the input channel
     * @param major major version of the protocol
     * @param minor minor version of the protocol
     * @param offset offset of the beginning of the channel, if reading from the middle of the data; 0 when reading whole file/stream
     */
    public BinarySource(ReadableByteChannel channel, int major, int minor, long offset) {
        this.majorVersion = major;
        this.minorVersion = minor;
        buffer = ByteBuffer.allocateDirect(256 * 1024);
        buffer.flip();
        this.channel = channel;
        this.bufferOffset = baseOffset = offset;
        try {
            this.digest = MessageDigest.getInstance("SHA-1"); // NOI18N
        } catch (NoSuchAlgorithmException e) {
        }
    }
    
    public Object getSourceId() {
        return sourceId;
    }

    public void useDigest(MessageDigest digest) {
        this.digest = digest;
    }

    private void setVersion(int newMajorVersion, int newMinorVersion) throws IOException {
        if (newMajorVersion > CURRENT_MAJOR_VERSION || (newMajorVersion == CURRENT_MAJOR_VERSION && newMinorVersion > CURRENT_MINOR_VERSION)) {
            throw new VersionMismatchException("File format version " + versionPair(newMajorVersion, newMinorVersion) + " unsupported.  Current version is " + CURRENT_VERSION);
        }
        majorVersion = newMajorVersion;
        minorVersion = newMinorVersion;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    /**
     * @return Returns absolute position of the source stream
     */
    public long getMark() {
        return bufferOffset + buffer.position();
    }

    /**
     * @return Returns relative position of the source stream
     */
    public long getMarkRelative() {
        return getMark() - baseOffset;
    }

    public int readInt() throws IOException {
        ensureAvailable(4);
        return buffer.getInt();
    }

    public double[] readDoubles() throws IOException {
        int len = readInt();
        if (len < 0) {
            return null;
        }
        ensureAvailable(len * 8);
        double[] props = new double[len];
        for (int i = 0; i < len; i++) {
            props[i] = buffer.getDouble();
        }
        return props;
    }

    public long readLong() throws IOException {
        ensureAvailable(8);
        return buffer.getLong();
    }

    byte[] peekBytes(int len) throws IOException {
        ensureAvailable(len);
        byte[] b = new byte[len];
        buffer.mark();
        buffer.get(b);
        buffer.reset();
        return b;
    }

    public float readFloat() throws IOException {
        ensureAvailable(4);
        return buffer.getFloat();
    }

    public int readByte() throws IOException {
        ensureAvailable(1);
        return ((int) buffer.get()) & 255;
    }

    public double readDouble() throws IOException {
        ensureAvailable(8);
        return buffer.getDouble();
    }

    public byte[] finishDigest() {
        assert performDigest;
        digestUpToPosition();
        performDigest = false;
        return digest.digest();
    }

    public void startDigest() {
        digest.reset();
        performDigest = true;
        lastPosition = buffer.position();
    }

    private void digestUpToPosition() {
        if (!performDigest) {
            return;
        }
        // All the data between lastPosition and position has been
        // used so add it to the digest.
        int position = buffer.position();
        buffer.position(lastPosition);
        byte[] remaining = new byte[position - buffer.position()];
        buffer.get(remaining);
        digest.update(remaining);
        assert position == buffer.position();
    }

    protected void fill() throws IOException {
        int position = buffer.position();
        digestUpToPosition();
        buffer.compact();
        bufferOffset += position;
        lastPosition = 0;
        // may throw EOF
        receiveBytes(buffer);
        buffer.flip();
    }

    protected void receiveBytes(ByteBuffer b) throws IOException {
        if (channel.read(b) < 0) {
            throw new EOFException();
        }
    }

    public byte[] readBytes() throws IOException {
        int len = readInt();
        if (len < 0) {
            return null;
        }
        return readBytes(len);
    }

    public byte[] readBytes(int len) throws IOException {
        return readBytes(new byte[len], len);
    }

    public byte[] readBytes(byte[] b, int len) throws IOException {
        int bytesRead = 0;
        while (bytesRead < len) {
            int toRead = Math.min(len - bytesRead, buffer.capacity());
            ensureAvailable(toRead);
            buffer.get(b, bytesRead, toRead);
            bytesRead += toRead;
        }
        return b;
    }

    public char readShort() throws IOException {
        ensureAvailable(2);
        return buffer.getChar();
    }

    public int[] readInts() throws IOException {
        int len = readInt();
        if (len < 0) {
            return null;
        }
        ensureAvailable(len * 4);
        int[] props = new int[len];
        for (int i = 0; i < len; i++) {
            props[i] = buffer.getInt();
        }
        return props;
    }

    // readString is only called from CP reads, CP items are cached, no need to intern
    public String readString() throws IOException {
        return maybeIntern(new String(readBytes(), UTF8));
    }

    private void ensureAvailable(int i) throws IOException {
        if (i > buffer.capacity()) {
            throw new IllegalArgumentException(String.format("Can not request %d bytes: buffer capacity is %d", i, buffer.capacity()));
        }
        while (buffer.remaining() < i) {
            fill();
        }
    }

    public boolean readHeader() throws IOException {
        // Check for a version specification
        byte[] magic = peekBytes(MAGIC_BYTES.length);
        if (Arrays.equals(MAGIC_BYTES, magic)) {
            // Consume the bytes for real
            readBytes(MAGIC_BYTES.length);
            setVersion(readByte(), readByte());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "BinarySource@" + Integer.toHexString(System.identityHashCode(this));
    }
}
