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
package org.graalvm.tools.lsp.instrument;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.Future;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.tools.lsp.api.ContextAwareExecutorRegistry;
import org.graalvm.tools.lsp.api.LanguageServerBootstrapper;
import org.graalvm.tools.lsp.api.VirtualLanguageServerFileProvider;
import org.graalvm.tools.lsp.exceptions.LSPIOException;
import org.graalvm.tools.lsp.hacks.LanguageSpecificHacks;
import org.graalvm.tools.lsp.instrument.LSOptions.HostAndPort;
import org.graalvm.tools.lsp.server.LanguageServerImpl;
import org.graalvm.tools.lsp.server.TruffleAdapter;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

@Registration(id = LSPInstrument.ID, name = "Language Server", version = "0.1", services = {VirtualLanguageServerFileProvider.class, ContextAwareExecutorRegistry.class,
                LanguageServerBootstrapper.class})
public final class LSPInstrument extends TruffleInstrument implements LanguageServerBootstrapper {
    public static final String ID = "lsp";

    private OptionValues options;
    private LanguageServerImpl languageServer;

    @Override
    protected void onCreate(Env env) {
        options = env.getOptions();
        if (options.hasSetOptions()) {
            LanguageSpecificHacks.enableLanguageSpecificHacks = options.get(LSOptions.LanguageSpecificHacksOption).booleanValue();
        }

        TruffleAdapter truffleAdapter = new TruffleAdapter(env);
        PrintWriter info = new PrintWriter(env.out(), true);
        PrintWriter err = new PrintWriter(env.err(), true);
        languageServer = LanguageServerImpl.create(truffleAdapter, info, err);

        env.registerService(truffleAdapter);
        env.registerService(this);
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new LSOptionsOptionDescriptors();
    }

    @Override
    public Future<?> startServer() {
        assert languageServer != null;
        assert options != null;
        assert options.hasSetOptions();

        HostAndPort hostAndPort = options.get(LSOptions.Lsp);
        try {
            InetSocketAddress socketAddress = hostAndPort.createSocket();
            int port = socketAddress.getPort();
            Integer backlog = options.get(LSOptions.SocketBacklogSize);
            InetAddress address = socketAddress.getAddress();
            ServerSocket serverSocket = new ServerSocket(port, backlog, address);
            return languageServer.start(serverSocket);
        } catch (IOException e) {
            String message = String.format("[Graal LSP] Starting server on %s failed: %s", hostAndPort.getHostPort(), e.getLocalizedMessage());
            throw new LSPIOException(message, e);
        }
    }
}
