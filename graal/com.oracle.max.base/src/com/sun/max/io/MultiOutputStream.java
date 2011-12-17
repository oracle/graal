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
 * Writes the same output to several streams.
 */
public class MultiOutputStream extends OutputStream {

    private final OutputStream[] streams;

    public MultiOutputStream(OutputStream... streams) {
        this.streams = streams;
    }

    @Override
    public void write(int b) throws IOException {
        for (OutputStream stream : streams) {
            stream.write(b);
        }
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        for (OutputStream stream : streams) {
            stream.write(bytes);
        }
    }

    @Override
    public void write(byte[] bytes, int startOffset, int numberOfBytes) throws IOException {
        for (OutputStream stream : streams) {
            stream.write(bytes, startOffset, numberOfBytes);
        }
    }

    @Override
    public void flush() throws IOException {
        for (OutputStream stream : streams) {
            stream.flush();
        }
    }

    @Override
    public void close() throws IOException {
        for (OutputStream stream : streams) {
            stream.close();
        }
    }
}
