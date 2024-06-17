/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graphio.parsing;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class StreamSource implements DataSource {
    static final byte[] MAGIC_BYTES = {'B', 'I', 'G', 'V'};
    static final int CURRENT_MAJOR_VERSION = 8;
    static final int CURRENT_MINOR_VERSION = 0;
    static final String CURRENT_VERSION = versionPair(CURRENT_MAJOR_VERSION, CURRENT_MINOR_VERSION);

    private final DataInputStream in;
    private int majorVersion;
    private int minorVersion;

    public StreamSource(InputStream is) {
        this.in = new DataInputStream(is);
    }

    @Override
    public int readByte() throws IOException {
        return in.readByte();
    }

    @Override
    public char readShort() throws IOException {
        return (char) in.readShort();
    }

    @Override
    public int readInt() throws IOException {
        return in.readInt();
    }

    @Override
    public long readLong() throws IOException {
        return in.readLong();
    }

    @Override
    public float readFloat() throws IOException {
        return in.readFloat();
    }

    @Override
    public double readDouble() throws IOException {
        return in.readDouble();
    }

    @Override
    public boolean readHeader() throws IOException {
        in.mark(MAGIC_BYTES.length);
        byte[] magic = readBytes(MAGIC_BYTES.length);
        if (Arrays.equals(MAGIC_BYTES, magic)) {
            setVersion(readByte(), readByte());
            return true;
        } else {
            in.reset();
            return false;
        }
    }

    @Override
    public double[] readDoubles() throws IOException {
        int len = readInt();
        if (len < 0) {
            return null;
        }
        double[] props = new double[len];
        for (int i = 0; i < len; i++) {
            props[i] = in.readDouble();
        }
        return props;
    }

    @Override
    public int[] readInts() throws IOException {
        int len = readInt();
        if (len < 0) {
            return null;
        }
        int[] props = new int[len];
        for (int i = 0; i < len; i++) {
            props[i] = in.readInt();
        }
        return props;
    }

    @Override
    public String readString() throws IOException {
        return new String(readBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] readBytes() throws IOException {
        int len = readInt();
        if (len < 0) {
            return null;
        }
        return readBytes(len);
    }

    @Override
    public byte[] readBytes(int len) throws IOException {
        return readBytes(new byte[len], len);
    }

    @Override
    public byte[] readBytes(byte[] b, int len) throws IOException {
        in.readFully(b, 0, len);
        return b;
    }

    @Override
    public long getMark() {
        throw new UnsupportedOperationException("getMark");
    }

    private void setVersion(int newMajorVersion, int newMinorVersion) throws IOException {
        if (newMajorVersion > CURRENT_MAJOR_VERSION || (newMajorVersion == CURRENT_MAJOR_VERSION && newMinorVersion > CURRENT_MINOR_VERSION)) {
            throw new VersionMismatchException("File format version " + versionPair(newMajorVersion, newMinorVersion) + " unsupported.  Current version is " + CURRENT_VERSION);
        }
        majorVersion = newMajorVersion;
        minorVersion = newMinorVersion;
    }

    @Override
    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    @Override
    public void startDigest() {
    }

    @Override
    public byte[] finishDigest() {
        return null;
    }

    static String versionPair(int major, int minor) {
        return major + "." + minor;
    }

}
