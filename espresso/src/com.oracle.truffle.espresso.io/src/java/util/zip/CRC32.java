/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.util.zip;

/**
 * A class that can be used to compute the CRC-32 of a data stream.
 * <p>
 * Replaces JDK's own {@link CRC32} class.
 */
public class CRC32 implements Checksum {
    static {
        ZipUtils.loadLibrary();
    }

    public CRC32() {
        init(this);
    }

    @Override
    public void update(int b) {
        update0(this, b);
    }

    @Override
    public void update(byte[] b, int off, int len) {
        updateBytes0(this, b, off, len);
    }

    @Override
    public long getValue() {
        return getValue0(this);
    }

    @Override
    public void reset() {
        reset0(this);
    }

    private static native void init(CRC32 crc);

    private static native void update0(CRC32 crc, int b);

    private static native void updateBytes0(CRC32 crc, byte[] b, int off, int len);

    private static native long getValue0(CRC32 crc);

    private static native void reset0(CRC32 crc);
}
