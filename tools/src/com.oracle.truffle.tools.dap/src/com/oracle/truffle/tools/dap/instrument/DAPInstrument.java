/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
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
        } else if (address.equals("false")) {
            return HostAndPort.disabled();
        } else {
            return HostAndPort.parse(address);
        }
    }, (Consumer<HostAndPort>) (address) -> address.verify());

    static final OptionType<List<URI>> SOURCE_PATH = new OptionType<>("folder" + File.pathSeparator + "file.zip" + File.pathSeparator + "...", (str) -> {
        if (str.isEmpty()) {
            return Collections.emptyList();
        }
        List<URI> uris = new ArrayList<>();
        int i1 = 0;
        while (i1 < str.length()) {
            int i2 = str.indexOf(File.pathSeparatorChar, i1);
            if (i2 < 0) {
                i2 = str.length();
            }
            String path = str.substring(i1, i2);
            try {
                uris.add(createURIFromPath(path));
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("Wrong path: " + path, ex);
            }
            i1 = i2 + 1;
        }
        return uris;
    });

    private static URI createURIFromPath(String path) throws URISyntaxException {
        String lpath = path.toLowerCase();
        int index = 0;
        File jarFile = null;
        while (index < lpath.length()) {
            int zi = lpath.indexOf(".zip", index);
            int ji = lpath.indexOf(".jar", index);
            if (zi >= 0 && zi < ji || ji < 0) {
                ji = zi;
            }
            if (ji >= 0) {
                index = ji + 4;
                File jar = new File(path.substring(0, index));
                if (jar.isFile()) {
                    jarFile = jar;
                    break;
                }
            } else {
                index = path.length();
            }
        }
        if (jarFile != null) {
            StringBuilder ssp = new StringBuilder("file://").append(jarFile.toPath().toUri().getPath());
            if (index < path.length()) {
                if (path.charAt(index) != '!') {
                    ssp.append('!');
                }
                ssp.append(path.substring(index));
            } else {
                ssp.append("!/");
            }
            return new URI("jar", ssp.toString(), null);
        } else {
            return new File(path).toPath().toUri();
        }
    }

    @Option(name = "", help = "Start the Debug Protocol Server on [[host:]port] (default: <loopback address>:" + DEFAULT_PORT +
                    ")", usageSyntax = "[[<host>:]<port>]", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<HostAndPort> Dap = new OptionKey<>(DEFAULT_ADDRESS, ADDRESS_OR_BOOLEAN);

    @Option(help = "Suspend the execution at first executed source line (default: true).", usageSyntax = "true|false", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> Suspend = new OptionKey<>(true);

    private static final String SOURCE_PATH_USAGE = "<path>,<path>,...";

    @com.oracle.truffle.api.Option(help = "Specifies list of directories or ZIP/JAR files representing source path (default: empty list).", usageSyntax = SOURCE_PATH_USAGE, category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<List<URI>> SourcePath = new OptionKey<>(Collections.emptyList(), SOURCE_PATH);

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
        if (options.hasSetOptions() && options.get(Dap).isEnabled()) {
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
        // Provide dynamic help example
        OptionDescriptors descriptors = new DAPInstrumentOptionDescriptors();
        return new OptionDescriptors() {
            @Override
            public OptionDescriptor get(String optionName) {
                return descriptors.get(optionName);
            }

            @Override
            public Iterator<OptionDescriptor> iterator() {
                Iterator<OptionDescriptor> iterator = descriptors.iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public OptionDescriptor next() {
                        OptionDescriptor descriptor = iterator.next();
                        if (descriptor.getKey() == SourcePath) {
                            String example = " Example: " + File.separator + "projects" + File.separator + "foo" + File.separator + "src" + File.pathSeparator + "sources.jar" + File.pathSeparator +
                                            "package.zip!/src";
                            descriptor = OptionDescriptor.newBuilder(SourcePath, descriptor.getName()).deprecated(descriptor.isDeprecated()).category(descriptor.getCategory()).help(
                                            descriptor.getHelp() + example).usageSyntax(SOURCE_PATH_USAGE).build();
                        }
                        return descriptor;
                    }
                };
            }
        };
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
            serverSocket.setReuseAddress(true);
            dapServer = DebugProtocolServerImpl.create(context, options.get(Suspend), options.get(WaitAttached), options.get(Initialization), options.get(SourcePath));
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

        private final boolean enabled;
        private final String host;
        private String portStr;
        private int port;
        private InetAddress inetAddress;

        private HostAndPort() {
            this.enabled = false;
            this.host = null;
        }

        private HostAndPort(String host, int port) {
            this.enabled = true;
            this.host = host;
            this.port = port;
        }

        private HostAndPort(String host, String portStr) {
            this.enabled = true;
            this.host = host;
            this.portStr = portStr;
        }

        static HostAndPort disabled() {
            return new HostAndPort();
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
            if (!enabled) {
                return;
            }
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

        boolean isEnabled() {
            return enabled;
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
