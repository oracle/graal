/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata.serialization;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;

@Platforms(Platform.HOSTED_ONLY.class)
final class ForkedDataOutput implements DataOutput, AutoCloseable {
    private final DataOutput root;
    private final ByteArrayOutputStream bytes;
    private final DataOutput delegate;

    ForkedDataOutput(DataOutput root) {
        this.root = getRoot(root);
        this.bytes = new ByteArrayOutputStream();
        this.delegate = new DataOutputStream(bytes);
    }

    private static DataOutput getRoot(DataOutput out) {
        DataOutput root = out;
        while (root instanceof ForkedDataOutput) {
            root = ((ForkedDataOutput) root).root;
        }
        return root;
    }

    @Override
    public void close() throws IOException {
        root.write(this.bytes.toByteArray());
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        delegate.writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
        delegate.writeByte(v);
    }

    @Override
    public void writeShort(int v) throws IOException {
        delegate.writeShort(v);
    }

    @Override
    public void writeChar(int v) throws IOException {
        delegate.writeChar(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        delegate.writeInt(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        delegate.writeLong(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        delegate.writeFloat(v);
    }

    @Override
    public void writeDouble(double v) throws IOException {
        delegate.writeDouble(v);
    }

    @Override
    public void writeBytes(String s) throws IOException {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public void writeChars(String s) throws IOException {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public void writeUTF(String s) throws IOException {
        throw VMError.shouldNotReachHereAtRuntime();
    }
}
