/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.api;

/**
 * Class representing common JDWP options.
 */
public final class JDWPOptions {
    public final String transport;
    public final String host;
    public final String port;
    public final boolean server;
    public final boolean suspend;

    public JDWPOptions(String transport, String host, String port, boolean server, boolean suspend) {
        this.transport = transport;
        this.host = host;
        this.port = port;
        this.server = server;
        this.suspend = suspend;
    }

    @Override
    public String toString() {
        return "JDWPOptions{" +
                        "transport=" + transport + "," +
                        "address=" + port + "," +
                        "server=" + server + "," +
                        "suspend=" + suspend + "}";
    }
}
