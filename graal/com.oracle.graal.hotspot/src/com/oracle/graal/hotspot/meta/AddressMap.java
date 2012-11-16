/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import java.io.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.phases.*;

/**
 * Utility for logging an address to symbol mapping to a file.
 * This is useful when looking at disassembled code.
 *
 * @see GraalOptions#PrintAddressMap
 */
public class AddressMap {

    private static PrintStream addressMapStream;
    static {
        synchronized (AddressMap.class) {
            if (GraalOptions.PrintAddressMap) {
                File file = new File("addressMap-" + System.currentTimeMillis() + ".log");
                try {
                    addressMapStream = new PrintStream(new FileOutputStream(file), true);
                } catch (FileNotFoundException e) {
                    throw new GraalInternalError("Could not open " + file.getAbsolutePath());
                }
                TTY.println("Logging {address -> symbol} map to %s", file);
            }
        }
    }

    public static void log(long address, String symbol) {
        if (addressMapStream != null) {
            synchronized (addressMapStream) {
                addressMapStream.println("0x" + Long.toHexString(address) + " " + symbol);
            }
        }
    }
}
