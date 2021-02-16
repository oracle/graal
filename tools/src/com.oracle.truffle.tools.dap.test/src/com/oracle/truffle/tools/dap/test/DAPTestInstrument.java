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
package com.oracle.truffle.tools.dap.test;

import java.io.PrintWriter;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.dap.server.DebugProtocolServerImpl;

import com.oracle.truffle.tools.dap.server.ExecutionContext;
import com.oracle.truffle.tools.dap.types.DebugProtocolServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Executors;

@TruffleInstrument.Registration(id = DAPTestInstrument.ID, services = DAPSessionHandlerProvider.class)
public class DAPTestInstrument extends TruffleInstrument {

    public static final String ID = "DAPTestInstrument";

    @Override
    protected void onCreate(final Env env) {
        env.registerService(new DAPSessionHandlerProvider() {
            @Override
            public DAPSessionHandler getSessionHandler(final boolean suspend, final boolean inspectInternal, final boolean inspectInitialization) throws IOException {
                return new DAPSessionHandler() {

                    private PipedOutputStream out;
                    private InputStream in;
                    private ExecutionContext context;

                    DAPSessionHandler init() throws IOException {
                        this.context = new ExecutionContext(env, new PrintWriter(env.out(), true), new PrintWriter(env.err(), true), inspectInternal, inspectInitialization);
                        this.out = new PipedOutputStream();
                        PipedOutputStream pos = new PipedOutputStream();
                        in = new PipedInputStream(pos, 2048);
                        DebugProtocolServer.Session.connect(DebugProtocolServerImpl.create(context, suspend, inspectInternal, inspectInitialization), new PipedInputStream(this.out, 2048), pos,
                                        Executors.newSingleThreadExecutor());
                        return this;
                    }

                    @Override
                    public OutputStream getOutputStream() {
                        return out;
                    }

                    @Override
                    public InputStream getInputStream() {
                        return in;
                    }
                }.init();
            }
        });
    }

}

interface DAPSessionHandlerProvider {
    DAPSessionHandler getSessionHandler(boolean suspend, boolean inspectInternal, boolean inspectInitialization) throws IOException;
}

interface DAPSessionHandler {

    OutputStream getOutputStream();

    InputStream getInputStream();
}
