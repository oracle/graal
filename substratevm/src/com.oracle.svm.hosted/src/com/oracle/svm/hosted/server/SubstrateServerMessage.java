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

class SubstrateServerMessage {
    ServerCommand command;
    String payload;

    SubstrateServerMessage() {
        /* Needed for GSON use in native-image */
    }

    SubstrateServerMessage(String command, String payload) {
        this.command = ServerCommand.valueOf(command);
        this.payload = payload;
    }

    static void send(SubstrateServerMessage message, OutputStreamWriter os) throws IOException {
        new Gson().toJson(message, os);
        os.write(System.lineSeparator());
        os.flush();
    }

    public enum ServerCommand {
        version,  // command to get the current version of the server
        stop,     // stop server command
        build,    // build image command
        abort,    // abort compilation
        s,        // standard output (short name for efficiency)
        e,        // standard error (short name for efficiency
        o         // standard output (short name for efficiency)
    }
}
