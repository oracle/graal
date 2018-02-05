/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.server;

import java.io.IOException;
import java.io.OutputStream;

import com.oracle.shadowed.com.google.gson.Gson;
import com.oracle.svm.hosted.server.SubstrateServerMessage.ServerCommand;

/**
 * Converts the data stream to streaming JSON packages containing the content as well as the command
 * that accompanies the content. Note: Should be used with BufferedOutputStream for better
 * performance and with standard OutputStreams for responsiveness.
 *
 * Format: { "command": $command, "payload": $string }
 */
public class StreamingJSONOutputStream extends OutputStream {

    private final ServerCommand command;
    private OutputStream original;
    private final Gson gson = new Gson();

    public void setOriginal(OutputStream original) {
        this.original = original;
    }

    StreamingJSONOutputStream(ServerCommand command, OutputStream original) {
        this.command = command;
        this.original = original;
    }

    @Override
    public void write(int b) throws IOException {
        String jsonString = gson.toJson(new SubstrateServerMessage(command, new String(new byte[]{(byte) b}, 0, 1)));
        for (byte ch : jsonString.getBytes()) {
            original.write(ch);
        }

        for (byte ch : System.lineSeparator().getBytes()) {
            original.write(ch);
        }
    }

    @Override
    public void flush() throws IOException {
        original.flush();
    }

    @Override
    public void close() throws IOException {
        original.close();
    }
}
