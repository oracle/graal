/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.io;

import java.io.*;

/**
 * A {@link ByteArrayOutputStream} that can have its write position {@linkplain #seek(int) updated}.
 */
public class SeekableByteArrayOutputStream extends ByteArrayOutputStream {

    private int highestCount;

    /**
     * @see ByteArrayOutputStream#ByteArrayOutputStream()
     */
    public SeekableByteArrayOutputStream() {
    }

    /**
     * @see ByteArrayOutputStream#ByteArrayOutputStream(int)
     */
    public SeekableByteArrayOutputStream(int size) {
        super(size);
    }

    /**
     * Updates the write position of this stream. The stream can only be repositioned between 0 and the
     * {@linkplain #endOfStream() end of the stream}.
     * 
     * @param index
     *            the index to which the write position of this stream will be set
     * @throws IllegalArgumentException
     *             if {@code index > highestSeekIndex()}
     */
    public void seek(int index) throws IllegalArgumentException {
        if (endOfStream() < index) {
            throw new IllegalArgumentException();
        }
        count = index;
    }

    /**
     * Gets the index one past the highest index that has been written to in this stream.
     */
    public int endOfStream() {
        if (highestCount < count) {
            highestCount = count;
        }
        return highestCount;
    }

    @Override
    public void reset() {
        super.reset();
        highestCount = 0;
    }

    /**
     * Copies the {@code length} bytes of this byte array output stream starting at {@code offset} to {@code buf}
     * starting at {@code bufOffset}.
     */
    public void copyTo(int offset, byte[] toBuffer, int toOffset, int length) {
        System.arraycopy(this.buf, offset, toBuffer, toOffset, length);
    }
}
