/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.jdwp.impl.JDWPLogger;

/**
 * Class representing common JDWP options.
 */
public final class JDWPOptions {
    public final String transport;
    public final String address;
    public final boolean server;
    public final boolean suspend;
    public final String logLevel;

    public JDWPOptions(String transport, String address, boolean server, boolean suspend, String logLevel) {
        this.transport = transport;
        this.address = address;
        this.server = server;
        this.suspend = suspend;
        this.logLevel = logLevel;
        JDWPLogger.setupLogLevel(logLevel);
    }

    @Override
    public String toString() {
        return "JDWPOptions{" +
                        "transport=" + transport + "," +
                        "address=" + address + "," +
                        "server=" + server + "," +
                        "suspend=" + suspend + "}";
    }
}
