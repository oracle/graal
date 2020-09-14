/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.server;

import com.oracle.truffle.tools.dap.types.DebugProtocolServer;
import com.oracle.truffle.tools.dap.types.Message;
import java.util.Collections;

public final class Errors {

    public static DebugProtocolServer.ExceptionWithMessage setValueNotSupported() {
        return new DebugProtocolServer.ExceptionWithMessage(Message.create(2004, "Setting value not supported"), "Setting value not supported");
    }

    public static DebugProtocolServer.ExceptionWithMessage sourceRequestCouldNotRetrieveContent() {
        return new DebugProtocolServer.ExceptionWithMessage(Message.create(2016, "Could not retrieve content"), "Could not retrieve content");
    }

    public static DebugProtocolServer.ExceptionWithMessage stackFrameNotValid() {
        return new DebugProtocolServer.ExceptionWithMessage(Message.create(2020, "Stack frame not valid"), "Stack frame not valid");
    }

    public static DebugProtocolServer.ExceptionWithMessage noCallStackAvailable() {
        return new DebugProtocolServer.ExceptionWithMessage(Message.create(2023, "No call stack available"), "No call stack available");
    }

    public static DebugProtocolServer.ExceptionWithMessage errorFromEvaluate(String errMsg) {
        return new DebugProtocolServer.ExceptionWithMessage(Message.create(2025, errMsg), errMsg);
    }

    public static DebugProtocolServer.ExceptionWithMessage sourceRequestIllegalHandle() {
        return new DebugProtocolServer.ExceptionWithMessage(Message.create(2027, "source request error: illegal handle"), "source request error: illegal handle");
    }

    public static DebugProtocolServer.ExceptionWithMessage invalidThread(Integer threadId) {
        return new DebugProtocolServer.ExceptionWithMessage(Message.create(2030, "Invalid thread {_thread}").setVariables(Collections.singletonMap("_thread", threadId.toString())),
                        String.format("Invalid thread `%d`", threadId));
    }

    public static DebugProtocolServer.ExceptionWithMessage noStoredException() {
        return new DebugProtocolServer.ExceptionWithMessage(Message.create(2032, "exceptionInfoRequest error: no stored exception"), "exceptionInfoRequest error: no stored exception");
    }
}
