/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types;

import java.io.IOException;

import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.JfrCheckpointWriter;

public class JfrTypeWriter {

    private static class JfrTypeWriterContext {
        final int offset;
        final int count;

        JfrTypeWriterContext(int offset, int count) {
            this.offset = offset;
            this.count = count;
        }
    }

    private final JfrCheckpointWriter writer;
    private final long typeId;
    private final boolean skipHeader;

    private JfrTypeWriterContext context;
    private int countOffset;
    private int count;

    public JfrTypeWriter(long typeId, JfrCheckpointWriter writer) {
        this(typeId, writer, false);
    }

    public JfrTypeWriter(long typeId, JfrCheckpointWriter writer, boolean skipHeader) {
        this.typeId = typeId;
        this.writer = writer;
        this.skipHeader = skipHeader;
    }

    public void begin() throws IOException {
        assert (writer != null);
        this.context = new JfrTypeWriterContext(this.writer.getCurrentOffset(), this.writer.count());

        if (!skipHeader) {
            this.writer.increment();
            this.writer.encoded().writeLong(typeId);
            this.countOffset = this.writer.reserve(Integer.BYTES);
        }
    }

    public void end() throws IOException {
        if (this.count == 0) {
            this.writer.setContext(this.context.offset, this.context.count);
            return;
        }
        assert (this.count > 0);
        if (!skipHeader) {
            this.writer.padded().writeInt(this.count, this.countOffset);
        }
    }

    public int count() {
        return this.count;
    }

    public void incrementCount(int count) {
        this.count += count;
    }
}
