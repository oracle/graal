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
package com.oracle.truffle.espresso.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

class DelegatingInputStream extends InputStream {

    protected volatile InputStream delegate;

    DelegatingInputStream(InputStream delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return delegate.read(b, off, len);
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return delegate.readAllBytes();
    }

    @Override
    public int readNBytes(byte[] b, int off, int len)
                    throws IOException {
        return delegate.readNBytes(b, off, len);
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        return delegate.readNBytes(len);
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate.skip(n);
    }

    // Added on Java 12+.
    @Override
    public void skipNBytes(long n) throws IOException {
        delegate.skipNBytes(n);
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        return delegate.transferTo(out);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
