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
import java.io.OutputStreamWriter;

import com.oracle.shadowed.com.google.gson.Gson;

public class SubstrateServerMessage {
    ServerCommand command;
    byte[] payload;

    SubstrateServerMessage() {
        /* Needed for GSON use in native-image */
    }

    SubstrateServerMessage(ServerCommand command, byte[] payload) {
        this.command = command;
        this.payload = payload;
    }

    static void send(SubstrateServerMessage message, OutputStreamWriter os) throws IOException {
        new Gson().toJson(message, os);
        os.write(System.lineSeparator());
        os.flush();
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
