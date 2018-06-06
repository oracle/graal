/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

public class SubstrateServerMessage {
    final ServerCommand command;
    final byte[] payload;
    final int offset;
    final int length;

    SubstrateServerMessage(ServerCommand command, byte[] payload) {
        this(command, payload, 0, payload.length);
    }

    SubstrateServerMessage(ServerCommand command, byte[] payload, int offset, int length) {
        this.command = command;
        this.payload = payload;
        this.offset = offset;
        this.length = length;
    }

    static void send(SubstrateServerMessage message, DataOutputStream os) throws IOException {
        os.writeInt(message.command.ordinal());
        os.writeInt(message.length);
        os.write(message.payload, message.offset, message.length);
        os.flush();
    }

    static SubstrateServerMessage receive(DataInputStream is) throws IOException {
        try {
            ServerCommand command = ServerCommand.values()[is.readInt()];
            int length = is.readInt();
            byte[] payload = new byte[length];
            is.readFully(payload);
            return new SubstrateServerMessage(command, payload);
        } catch (EOFException ex) {
            return null;
        }
    }

    public String payloadString() {
        return new String(payload);
    }

    public enum ServerCommand {
        GET_VERSION,
        STOP_SERVER,
        BUILD_IMAGE,
        ABORT_BUILD,
        SEND_STATUS,
        WRITE_ERR,
        WRITE_OUT
    }
}
