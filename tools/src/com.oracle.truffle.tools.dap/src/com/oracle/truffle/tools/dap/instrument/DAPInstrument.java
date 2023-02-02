/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.instrument;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.function.Consumer;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.tools.dap.server.DebugProtocolServerImpl;
import com.oracle.truffle.tools.dap.server.ExecutionContext;

@Registration(id = DAPInstrument.ID, name = "Debug Protocol Server", version = "0.1", website = "https://www.graalvm.org/tools/dap/")
public final class DAPInstrument extends TruffleInstrument {

    public static final String ID = "dap";
    private static final int DEFAULT_PORT = 4711;
    private static final HostAndPort DEFAULT_ADDRESS = new HostAndPort(null, DEFAULT_PORT);

    private OptionValues options;

    static final OptionType<HostAndPort> ADDRESS_OR_BOOLEAN = new OptionType<>("[[host:]port]", (address) -> {
        if (address.isEmpty() || address.equals("true")) {
            return DEFAULT_ADDRESS;
        } else {
            return HostAndPort.parse(address);
        }
    }, (Consumer<HostAndPort>) (address) -> address.verify());

    @Option(name = "", help = "Start the Debug Protocol Server on [[host:]port] (default: <loopback address>:" + DEFAULT_PORT +
                    ")", usageSyntax = "[[<host>:]<port>]", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<HostAndPort> Dap = new OptionKey<>(DEFAULT_ADDRESS, ADDRESS_OR_BOOLEAN);

    @Option(help = "Suspend the execution at first executed source line (default: true).", usageSyntax = "true|false", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> Suspend = new OptionKey<>(true);

    @Option(help = "Do not execute any source code until debugger client is attached.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> WaitAttached = new OptionKey<>(false);

    @Option(help = "Debug internal sources.", category = OptionCategory.INTERNAL) //
    static final OptionKey<Boolean> Internal = new OptionKey<>(false);

    @Option(help = "Debug language initialization.", category = OptionCategory.INTERNAL) //
    static final OptionKey<Boolean> Initialization = new OptionKey<>(false);

    @Option(help = "Requested maximum length of the Socket queue of incoming connections (default: unspecified).", usageSyntax = "[0, inf)", category = OptionCategory.EXPERT) //
    static final OptionKey<Integer> SocketBacklogSize = new OptionKey<>(-1);

    private DebugProtocolServerImpl dapServer;

    @Override
    protected void onCreate(Env env) {
        options = env.getOptions();
        if (options.hasSetOptions()) {
            launchServer(env, new PrintWriter(env.out(), true), new PrintWriter(env.err(), true));
        }
    }

    @Override
    protected void onFinalize(Env env) {
        if (dapServer != null) {
            dapServer.dispose();
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new DAPInstrumentOptionDescriptors();
    }

    private void launchServer(TruffleInstrument.Env env, PrintWriter info, PrintWriter err) {
        assert options != null;
        assert options.hasSetOptions();

        final HostAndPort hostAndPort = options.get(Dap);
        try {
            final InetSocketAddress socketAddress = hostAndPort.createSocket();
            final int port = socketAddress.getPort();
            final ExecutionContext context = new ExecutionContext(env, info, err, options.get(Internal), options.get(Initialization));
            final ServerSocket serverSocket = new ServerSocket(port, options.get(SocketBacklogSize), socketAddress.getAddress());
            dapServer = DebugProtocolServerImpl.create(context, options.get(Suspend), options.get(WaitAttached), options.get(Initialization));
            dapServer.start(serverSocket).exceptionally(throwable -> {
                throwable.printStackTrace(err);
                return null;
            });
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable e) {
            String message = String.format(Locale.ENGLISH, "[Graal DAP] Starting server on %s failed: %s", hostAndPort.getHostPort(), e.getLocalizedMessage());
            new DAPIOException(message, e).printStackTrace(err);
        }
    }

    static final class HostAndPort {

        private final String host;
        private String portStr;
        private int port;
        private InetAddress inetAddress;

        private HostAndPort(String host, int port) {
            this.host = host;
            this.port = port;
        }

        private HostAndPort(String host, String portStr) {
            this.host = host;
            this.portStr = portStr;
        }

        static HostAndPort parse(String address) {
            int colon = address.indexOf(':');
            String port;
            String host;
            if (colon >= 0) {
                port = address.substring(colon + 1);
                host = address.substring(0, colon);
            } else {
                try {
                    Integer.parseInt(address);
                    // Successfully parsed, it's a port number
                    port = address;
                    host = null;
                } catch (NumberFormatException e) {
                    port = Integer.toString(DEFAULT_PORT);
                    host = address;
                }
            }
            return new HostAndPort(host, port);
        }

        void verify() {
            // Check port:
            if (port == 0) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Port is not a number: " + portStr);
                }
            }
            if (port != 0 && (port < 1024 || 65535 < port)) {
                throw new IllegalArgumentException("Invalid port number: " + port + ". Needs to be 0, or in range from 1024 to 65535.");
            }
            // Check host:
            if (host != null && !host.isEmpty()) {
                try {
                    inetAddress = InetAddress.getByName(host);
                } catch (UnknownHostException ex) {
                    throw new IllegalArgumentException(ex.getLocalizedMessage(), ex);
                }
            }
        }

        String getHostPort() {
            String hostName = host;
            if (hostName == null || hostName.isEmpty()) {
                if (inetAddress != null) {
                    hostName = inetAddress.toString();
                } else {
                    hostName = InetAddress.getLoopbackAddress().toString();
                }
            }
            return hostName + ":" + port;
        }

        InetSocketAddress createSocket() {
            InetAddress ia;
            if (inetAddress == null) {
                ia = InetAddress.getLoopbackAddress();
            } else {
                ia = inetAddress;
            }
            return new InetSocketAddress(ia, port);
        }

        @Override
        public String toString() {
            return (host != null ? host : "<loopback address>") + ":" + port;
        }
    }
}
