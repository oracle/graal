/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.types;

import java.util.HashMap;
import java.util.Map;

/**
 * Predefined error codes.
 */
public enum ErrorCodes {

    ParseError(-32700),
    InvalidRequest(-32600),
    MethodNotFound(-32601),
    InvalidParams(-32602),
    InternalError(-32603),
    serverErrorStart(-32099),
    serverErrorEnd(-32000),
    ServerNotInitialized(-32002),
    UnknownErrorCode(-32001),
    RequestCancelled(-32800),
    ContentModified(-32801),
    MessageWriteError(1),
    MessageReadError(2);

    private final int intValue;

    ErrorCodes(int intValue) {
        this.intValue = intValue;
    }

    public int getIntValue() {
        return intValue;
    }

    private static final Map<Integer, ErrorCodes> lookup = new HashMap<>();

    static {
        for (ErrorCodes value : ErrorCodes.values()) {
            lookup.put(value.getIntValue(), value);
        }
    }

    public static ErrorCodes get(Integer intValue) {
        return lookup.get(intValue);
    }
}
