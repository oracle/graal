/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.nodes.Node;
import java.util.Locale;

final class AgentException extends RuntimeException implements TruffleException {
    static final long serialVersionUID = 1L;
    private final int exitCode;

    @TruffleBoundary
    private AgentException(String msg, Throwable cause, int exitCode) {
        super("agentscript: " + msg, cause);
        this.exitCode = exitCode;
    }

    @SuppressWarnings("all")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public Node getLocation() {
        return null;
    }

    @Override
    public boolean isExit() {
        return exitCode >= 0;
    }

    @Override
    public int getExitStatus() {
        return exitCode;
    }

    @TruffleBoundary
    static AgentException raise(Exception ex) throws AgentException {
        throw new AgentException(ex.getMessage(), ex, -1);
    }

    @TruffleBoundary
    static AgentException notFound(TruffleFile file) {
        throw new AgentException(file.getName() + ": No such file or directory", null, 1);
    }

    @TruffleBoundary
    static AgentException notRecognized(TruffleFile file) {
        throw new AgentException(file.getName() + ": No language to process the file. Try --polyglot", null, 1);
    }

    @TruffleBoundary
    static AgentException unknownAttribute(String type) {
        throw new AgentException("Unknown attribute " + type, null, 1);
    }

    @TruffleBoundary
    static AgentException unknownType(Throwable originalError, String str, AgentType[] values) {
        StringBuilder sb = new StringBuilder();
        sb.append("Unknown event type '").append(str).append("'. Known types are:");
        String sep = " ";
        for (AgentType t : values) {
            sb.append(sep).append("'").append(t.toString().toLowerCase(Locale.ENGLISH)).append("'");
            sep = ", ";
        }
        throw new AgentException(sb.toString(), originalError, 1);
    }
}
