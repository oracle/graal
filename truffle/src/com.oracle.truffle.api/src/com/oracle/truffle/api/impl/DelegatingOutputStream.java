/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.io.IOException;
import java.io.OutputStream;

import com.oracle.truffle.api.impl.DispatchOutputStream.OutputStreamList;

/**
 * An {@link OutputStream} that can be dispatched to other output streams.
 */
public final class DelegatingOutputStream extends OutputStream {

    private final OutputStream out;
    private final DispatchOutputStream delegate;

    DelegatingOutputStream(OutputStream out, DispatchOutputStream delegate) {
        this.out = out;
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        OutputStreamList outs = delegate.getOutList();
        if (outs != null) {
            outs.writeMulti(b);
        }
        out.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        OutputStreamList outs = delegate.getOutList();
        if (outs != null) {
            outs.writeMulti(b);
        }
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        OutputStreamList outs = delegate.getOutList();
        if (outs != null) {
            outs.writeMulti(b, off, len);
        }
        out.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        OutputStreamList outs = delegate.getOutList();
        if (outs != null) {
            outs.flushMulti();
        }
        out.flush();
    }

    @Override
    public void close() throws IOException {
        OutputStreamList outs = delegate.getOutList();
        if (outs != null) {
            outs.closeMulti();
        }
        out.close();
    }
}
