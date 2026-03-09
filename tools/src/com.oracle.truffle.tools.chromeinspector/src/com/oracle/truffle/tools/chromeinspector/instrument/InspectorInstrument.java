/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.instrument;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.objects.Inspector;
import com.oracle.truffle.tools.chromeinspector.server.ConnectionWatcher;
import com.oracle.truffle.tools.chromeinspector.server.InspectServerSession;
import com.oracle.truffle.tools.chromeinspector.server.InspectorServer;
import com.oracle.truffle.tools.chromeinspector.server.InspectorServerConnection;
import com.oracle.truffle.tools.chromeinspector.server.WSInterceptorServer;

/**
 * Chrome inspector as an instrument.
 */
@TruffleInstrument.Registration(id = InspectorInstrument.INSTRUMENT_ID, name = "Chrome Inspector", version = InspectorInstrument.VERSION, services = TruffleObject.class, website = "https://www.graalvm.org/tools/chrome-debugger/")
public final class InspectorInstrument extends TruffleInstrument {

    private static final int DEFAULT_PORT = 9229;
    private static final int DISABLED_PORT = -1;
    private static final String HELP_URL = "https://www.graalvm.org/tools/chrome-debugger";

    private Server server;
    private ConnectionWatcher connectionWatcher;

    static final OptionType<Integer> PORT_OR_BOOLEAN = new OptionType<>("[port]", (port) -> {
        if (port.isEmpty() || port.equals("true")) {
            return DEFAULT_PORT;
        } else if (port.equals("false")) {
            return DISABLED_PORT;
        } else {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Port is not a number: " + port, ex);
            }
        }
    }, (Consumer<Integer>) InspectorInstrument::verifyPort);

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

    static final OptionType<Long> TIMEOUT = new OptionType<>("[1, inf)ms|s|m|h|d", (str) -> {
        ChronoUnit foundUnit = null;
        long value = -1;
        try {
            for (ChronoUnit unit : ChronoUnit.values()) {
                String unitName = getUnitName(unit);
                if (unitName == null) {
                    continue;
                }
                if (str.endsWith(unitName)) {
                    foundUnit = unit;
                    String time = str.substring(0, str.length() - unitName.length());
                    value = Long.parseLong(time);
                    break;
                }
            }
            if (value <= 0) {
                throw invalidUnitValue(str);
            }
            assert foundUnit != null;
            try {
                Duration duration = Duration.of(value, foundUnit);
                return duration.toMillis();
            } catch (ArithmeticException ex) {
                return Long.MAX_VALUE;
            }
        } catch (NumberFormatException | ArithmeticException e) {
            throw invalidUnitValue(str);
        }
    });

    private static String getUnitName(ChronoUnit unit) {
        switch (unit) {
            case MILLIS:
                return "ms";
            case SECONDS:
                return "s";
            case MINUTES:
                return "m";
            case HOURS:
                return "h";
            case DAYS:
                return "d";
        }
        return null;
    }

    private static IllegalArgumentException invalidUnitValue(String value) {
        throw new IllegalArgumentException("Invalid timeout '" + value + "' specified. " //
                        + "A valid timeout duration consists of a positive integer value followed by a chronological time unit. " //
                        + "For example '15m' or '60s'. Valid time units are " //
                        + "'ms' for milliseconds, " //
                        + "'s' for seconds, " //
                        + "'m' for minutes, " //
                        + "'h' for hours, and " //
                        + "'d' for days.");
    }

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

    @com.oracle.truffle.api.Option(name = "", help = "Start the Chrome inspector on 127.0.0.1:<port>.", usageSyntax = "[<port>]", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Integer> Inspect = new OptionKey<>(DEFAULT_PORT, PORT_OR_BOOLEAN);

    @com.oracle.truffle.api.Option(help = "Suspend the execution at first executed source line (default: true).", usageSyntax = "true|false", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> Suspend = new OptionKey<>(true);

    @com.oracle.truffle.api.Option(help = "Timeout of a debugger suspension. The debugger session is disconnected after the timeout expires. (default: no timeout). Example value: '5m'.", //
                    usageSyntax = "[1, inf)ms|s|m|h|d", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Long> SuspensionTimeout = new OptionKey<>(null, TIMEOUT);

    @com.oracle.truffle.api.Option(help = "Do not execute any source code until inspector client is attached.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> WaitAttached = new OptionKey<>(false);

    private static final String SOURCE_PATH_USAGE = "<path>,<path>,...";

    @com.oracle.truffle.api.Option(help = "Specifies list of directories or ZIP/JAR files representing source path (default: empty list).", usageSyntax = SOURCE_PATH_USAGE, category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<List<URI>> SourcePath = new OptionKey<>(Collections.emptyList(), SOURCE_PATH);

    @com.oracle.truffle.api.Option(help = "Hide internal errors that can occur as a result of debugger inspection.", category = OptionCategory.EXPERT) //
    static final OptionKey<Boolean> HideErrors = new OptionKey<>(false);

    @com.oracle.truffle.api.Option(help = "Path to the chrome inspect. (default: randomly generated)", //
                    usageSyntax = "<path>", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<String> Path = new OptionKey<>("");

    @com.oracle.truffle.api.Option(help = "Inspect internal sources.", category = OptionCategory.INTERNAL) //
    static final OptionKey<Boolean> Internal = new OptionKey<>(false);

    @com.oracle.truffle.api.Option(help = "Inspect language initialization.", category = OptionCategory.INTERNAL) //
    static final OptionKey<Boolean> Initialization = new OptionKey<>(false);

    public static final String INSTRUMENT_ID = "inspect";
    static final String VERSION = "0.1";

    @Override
    protected void onCreate(Env env) {
        OptionValues options = env.getOptions();
        if (options.hasSetOptions() && options.get(Inspect) != DISABLED_PORT) {
            connectionWatcher = new ConnectionWatcher();
            try {
                server = new Server(env, "Main Context", options.get(Inspect), options.get(Suspend), options.get(WaitAttached), options.get(HideErrors), options.get(Internal),
                                options.get(Initialization), options.get(Path), options.get(SourcePath),
                                options.get(SuspensionTimeout), connectionWatcher);
            } catch (IOException e) {
                throw new InspectorIOException(options.get(Inspect), e);
            }
        }

        env.registerService(new Inspector(env, server != null ? server.getConnection() : null, new InspectorServerConnection.Open() {
            @Override
            public synchronized InspectorServerConnection open(int port, String host, boolean wait) {
                if (server != null && server.wss != null) {
                    return null;
                }
                int inspectPort = port < 0 ? options.get(Inspect) : port;
                if (host != null && !host.isEmpty() && !"127.0.0.1".equals(host)) {
                    PrintWriter info = new PrintWriter(env.err());
                    info.println("Only 127.0.0.1 is supported by the inspector. Requested host: " + host);
                    info.flush();
                    return null;
                }
                try {
                    verifyPort(inspectPort);
                } catch (IllegalArgumentException ex) {
                    PrintWriter info = new PrintWriter(env.err());
                    info.println(ex.getLocalizedMessage());
                    info.flush();
                    return null;
                }
                connectionWatcher = new ConnectionWatcher();
                try {
                    server = new Server(env, "Main Context", inspectPort, false, wait, options.get(HideErrors), options.get(Internal),
                                    options.get(Initialization), null, options.get(SourcePath),
                                    options.get(SuspensionTimeout), connectionWatcher);
                } catch (IOException e) {
                    PrintWriter info = new PrintWriter(env.err());
                    info.println(new InspectorIOException(inspectPort, e).getLocalizedMessage());
                    info.flush();
                }
                return server != null ? server.getConnection() : null;
            }
        }, new Supplier<InspectorExecutionContext>() {
            @Override
            public InspectorExecutionContext get() {
                if (server != null) {
                    return server.getConnection().getExecutionContext();
                } else {
                    PrintWriter info = new PrintWriter(env.err(), true);
                    PrintWriter err = (options.get(HideErrors)) ? null : info;
                    return new InspectorExecutionContext("Main Context", options.get(Internal), options.get(Initialization), env, Collections.emptyList(), info, err, options.get(SuspensionTimeout));
                }
            }
        }));
    }

    @Override
    protected void onFinalize(Env env) {
        if (connectionWatcher != null && connectionWatcher.shouldWaitForClose()) {
            PrintWriter info = new PrintWriter(env.out());
            info.println("Waiting for the debugger to disconnect...");
            info.flush();
            connectionWatcher.waitForClose();
        }
        if (server != null) {
            server.doFinalize();
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        // Provide dynamic help example
        OptionDescriptors descriptors = new InspectorInstrumentOptionDescriptors();
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

    private static void verifyPort(int port) {
        if (port != DISABLED_PORT && port != 0 && (port < 1024 || 65535 < port)) {
            throw new IllegalArgumentException("Invalid port number: " + port + ". Needs to be 0, or in range from 1024 to 65535.");
        }
    }

    private static InetSocketAddress createLoopbackSocket(int port) {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
    }

    private static final class Server {

        private InspectorWSConnection wss;
        private final Token token;
        private final String urlContainingToken;
        private final InspectorExecutionContext executionContext;

        Server(final Env env, final String contextName, final int port, final boolean debugBreak, final boolean waitAttached, final boolean hideErrors,
                        final boolean inspectInternal, final boolean inspectInitialization, final String pathOrNull, final List<URI> sourcePath, final Long suspensionTimeout,
                        final ConnectionWatcher connectionWatcher) throws IOException {
            InetSocketAddress socketAddress = createLoopbackSocket(port);
            PrintWriter info = new PrintWriter(env.err(), true);
            final String pathContainingToken;
            if (pathOrNull == null || pathOrNull.isEmpty()) {
                pathContainingToken = "/" + generateRandomToken();
            } else {
                String head = pathOrNull.startsWith("/") ? "" : "/";
                pathContainingToken = head + pathOrNull;
            }
            token = Token.createHashedTokenFromString(pathContainingToken);

            PrintWriter err = (hideErrors) ? null : info;
            executionContext = new InspectorExecutionContext(contextName, inspectInternal, inspectInitialization, env, sourcePath, info, err, suspensionTimeout);
            final URI wsuri;
            try {
                wsuri = new URI("ws", null, socketAddress.getAddress().getHostAddress(), socketAddress.getPort(), pathContainingToken, null, null);
            } catch (URISyntaxException ex) {
                throw new IOException(ex);
            }
            InspectServerSession iss = InspectServerSession.create(executionContext, debugBreak, connectionWatcher, () -> doFinalize());
            boolean disposeIss = true;
            try {
                WSInterceptorServer interceptor = new WSInterceptorServer(socketAddress.getPort(), token, iss, connectionWatcher);
                MessageEndpoint serverEndpoint;
                try {
                    serverEndpoint = env.startServer(wsuri, iss);
                } catch (MessageTransport.VetoException vex) {
                    throw new IOException(vex.getLocalizedMessage());
                }
                if (serverEndpoint == null) {
                    InspectorServer server;
                    interceptor.resetSessionEndpoint(); // A new endpoint is going to be opened
                    server = InspectorServer.get(socketAddress, token, pathContainingToken, executionContext, debugBreak, connectionWatcher, iss);
                    String wsAddress = server.getWSAddress(token);
                    wss = server;
                    urlContainingToken = wsAddress;
                    info.println("Debugger listening on " + wsAddress);
                    info.println("For help, see: " + HELP_URL);
                    info.println("E.g. in Chrome open: " + server.getDevtoolsAddress(token));
                    info.flush();
                } else {
                    restartServerEndpointOnClose(port, env, wsuri, executionContext, connectionWatcher, iss, interceptor, () -> doFinalize());
                    interceptor.opened(serverEndpoint);
                    wss = interceptor;
                    urlContainingToken = wsuri.toString();
                }
                disposeIss = false;
            } finally {
                if (disposeIss) {
                    iss.dispose();
                }
            }
            if (debugBreak || waitAttached) {
                final AtomicReference<EventBinding<?>> execEnter = new AtomicReference<>();
                final AtomicBoolean disposeBinding = new AtomicBoolean(false);
                execEnter.set(env.getInstrumenter().attachContextsListener(new ContextsListener() {
                    @Override
                    public void onContextCreated(TruffleContext context) {
                    }

                    @Override
                    public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
                        if (inspectInitialization) {
                            waitForRunPermission();
                        }
                    }

                    @Override
                    public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                        if (!inspectInitialization) {
                            waitForRunPermission();
                        }
                    }

                    @Override
                    public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
                    }

                    @Override
                    public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
                    }

                    @Override
                    public void onContextClosed(TruffleContext context) {
                    }

                    @TruffleBoundary
                    private void waitForRunPermission() {
                        try {
                            boolean success = executionContext.waitForRunPermission(suspensionTimeout);
                            if (!success) {
                                // Timeout expired, abandon debugging
                                info.println("Timeout of " + suspensionTimeout + "ms as specified via '--" + InspectorInstrument.INSTRUMENT_ID +
                                                ".SuspensionTimeout' was reached. Debugging is abandoned.");
                                doFinalize();
                            }
                        } catch (InterruptedException ex) {
                        }
                        final EventBinding<?> binding = execEnter.getAndSet(null);
                        if (binding != null) {
                            binding.dispose();
                        } else {
                            disposeBinding.set(true);
                        }
                    }
                }, true));
                if (disposeBinding.get()) {
                    execEnter.get().dispose();
                }
            }
        }

        private static String generateRandomToken() {
            final byte[] tokenRaw = generateRawToken();
            // base64url (see https://tools.ietf.org/html/rfc4648 ) without padding
            // For a fixed-length token, there is no ambiguity in paddingless
            return Base64.getEncoder().withoutPadding().encodeToString(tokenRaw).replace('/', '_').replace('+', '-');
        }

        @SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "avoiding a static field which would cache the random seed in a native image")
        private static byte[] generateRawToken() {
            // 256 bits of entropy ought to be enough for everybody
            final byte[] tokenRaw = new byte[32];
            new SecureRandom().nextBytes(tokenRaw);
            return tokenRaw;
        }

        private static void restartServerEndpointOnClose(int port, Env env, URI wsuri, InspectorExecutionContext executionContext, ConnectionWatcher connectionWatcher,
                        InspectServerSession iss, WSInterceptorServer interceptor, Runnable sessionDisposal) {
            iss.onClose(() -> {
                // debugBreak = false, do not break on re-connect
                InspectServerSession newSession = InspectServerSession.create(executionContext, false, connectionWatcher, sessionDisposal);
                interceptor.newSession(newSession);
                MessageEndpoint serverEndpoint;
                try {
                    serverEndpoint = env.startServer(wsuri, newSession);
                } catch (MessageTransport.VetoException vex) {
                    newSession.dispose();
                    return;
                } catch (IOException ioex) {
                    throw new InspectorIOException(port, ioex);
                }
                interceptor.opened(serverEndpoint);
                restartServerEndpointOnClose(port, env, wsuri, executionContext, connectionWatcher, newSession, interceptor, sessionDisposal);
            });
        }

        public void close() throws IOException {
            if (wss != null) {
                wss.close(token);
                wss = null;
            }
        }

        void doFinalize() {
            if (wss != null) {
                wss.closing(token);
                try {
                    wss.close(token);
                } catch (IOException ioex) {
                }
                wss.dispose();
                wss = null;
            }
        }

        InspectorServerConnection getConnection() {
            return new InspectorServerConnection() {

                @Override
                public String getURL() {
                    return urlContainingToken;
                }

                @Override
                public void close() throws IOException {
                    Server.this.close();
                }

                @Override
                public InspectorExecutionContext getExecutionContext() {
                    return executionContext;
                }

                @Override
                public void consoleAPICall(String type, Object text) {
                    if (wss != null) {
                        wss.consoleAPICall(token, type, text);
                    }
                }
            };
        }
    }
}
