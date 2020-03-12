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
package org.graalvm.tools.lsp.instrument;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import org.graalvm.collections.Pair;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.exceptions.LSPIOException;
import org.graalvm.tools.lsp.server.LanguageServerImpl;
import org.graalvm.tools.lsp.server.LSPFileSystem;
import org.graalvm.tools.lsp.server.TruffleAdapter;
import org.graalvm.tools.lsp.server.utils.CoverageEventNode;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

@Registration(id = LSPInstrument.ID, name = "Language Server", version = "0.1", services = {EnvironmentProvider.class})
public final class LSPInstrument extends TruffleInstrument implements EnvironmentProvider {

    public static final String ID = "lsp";
    private static final int DEFAULT_PORT = 8123;
    private static final HostAndPort DEFAULT_ADDRESS = new HostAndPort(null, DEFAULT_PORT);

    private OptionValues options;
    private Env environment;
    private EventBinding<ExecutionEventNodeFactory> eventFactoryBinding;
    private volatile boolean waitForClose = false;

    static final OptionType<HostAndPort> ADDRESS_OR_BOOLEAN = new OptionType<>("[[host:]port]", (address) -> {
        if (address.isEmpty() || address.equals("true")) {
            return DEFAULT_ADDRESS;
        } else {
            return HostAndPort.parse(address);
        }
    }, (Consumer<HostAndPort>) (address) -> address.verify());

    static final OptionType<List<LanguageAndAddress>> DELEGATES = new OptionType<>("[languageId@][[host:]port],...", (addresses) -> {
        if (addresses.isEmpty()) {
            return Collections.emptyList();
        }
        String[] array = addresses.split(",");
        List<LanguageAndAddress> hostPorts = new ArrayList<>(array.length);
        for (String address : array) {
            hostPorts.add(LanguageAndAddress.parse(address));
        }
        return hostPorts;
    }, (Consumer<List<LanguageAndAddress>>) (addresses) -> addresses.forEach((address) -> address.verify()));

    @Option(help = "Enable features for language developers, e.g. hovering code snippets shows AST related information like the node class or tags. (default:false)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> DeveloperMode = new OptionKey<>(false);

    @Option(help = "Include internal sources in goto-definition, references and symbols search. (default:false)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> Internal = new OptionKey<>(false);

    @Option(name = "", help = "Start the Language Server on [[host:]port]. (default: <loopback address>:" + DEFAULT_PORT + ")", category = OptionCategory.USER) //
    static final OptionKey<HostAndPort> Lsp = new OptionKey<>(DEFAULT_ADDRESS, ADDRESS_OR_BOOLEAN);

    @Option(help = "Requested maximum length of the Socket queue of incoming connections. (default: -1)", category = OptionCategory.EXPERT) //
    static final OptionKey<Integer> SocketBacklogSize = new OptionKey<>(-1);

    @Option(help = "Delegate language servers", category = OptionCategory.USER) //
    static final OptionKey<List<LanguageAndAddress>> Delegates = new OptionKey<>(Collections.emptyList(), DELEGATES);

    @Override
    protected void onCreate(Env env) {
        env.registerService(this);
        this.environment = env;
        options = env.getOptions();
        if (options.hasSetOptions()) {
            final TruffleAdapter truffleAdapter = launchServer(new PrintWriter(env.out(), true), new PrintWriter(env.err(), true));
            SourceSectionFilter eventFilter = SourceSectionFilter.newBuilder().includeInternal(options.get(Internal)).build();
            eventFactoryBinding = env.getInstrumenter().attachExecutionEventFactory(eventFilter, new ExecutionEventNodeFactory() {
                private final long creatorThreadId = Thread.currentThread().getId();

                @Override
                public ExecutionEventNode create(final EventContext eventContext) {
                    final SourceSection section = eventContext.getInstrumentedSourceSection();
                    if (section != null && section.isAvailable()) {
                        final Node instrumentedNode = eventContext.getInstrumentedNode();
                        return new CoverageEventNode(section, instrumentedNode, null, truffleAdapter.surrogateGetter(instrumentedNode.getRootNode().getLanguageInfo()), creatorThreadId);
                    } else {
                        return null;
                    }
                }
            });
        }
    }

    @Override
    protected void onDispose(Env env) {
        if (eventFactoryBinding != null) {
            eventFactoryBinding.dispose();
        }
    }

    @Override
    protected void onFinalize(Env env) {
        if (waitForClose) {
            PrintWriter info = new PrintWriter(env.out());
            info.println("Waiting for the language client to disconnect...");
            info.flush();
            waitForClose();
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new LSPInstrumentOptionDescriptors();
    }

    @Override
    public Env getEnvironment() {
        return environment;
    }

    private void setWaitForClose() {
        waitForClose = true;
    }

    public synchronized void waitForClose() {
        while (waitForClose) {
            try {
                wait();
            } catch (InterruptedException ex) {
                break;
            }
        }
    }

    private synchronized void notifyClose() {
        waitForClose = false;
        notifyAll();
    }

    private TruffleAdapter launchServer(PrintWriter info, PrintWriter err) {
        assert options != null;
        assert options.hasSetOptions();

        TruffleAdapter truffleAdapter = new TruffleAdapter(environment, options.get(DeveloperMode));

        Context.Builder builder = Context.newBuilder();
        builder.allowAllAccess(true);
        builder.engine(Engine.create());
        builder.fileSystem(LSPFileSystem.newReadOnlyFileSystem(truffleAdapter));
        ContextAwareExecutor executorWrapper = new ContextAwareExecutorImpl(builder);

        setWaitForClose();
        executorWrapper.executeWithDefaultContext(() -> {
            HostAndPort hostAndPort = options.get(Lsp);
            try {
                Context context = builder.build();
                context.enter();

                Instrument instrument = context.getEngine().getInstruments().get(ID);
                EnvironmentProvider envProvider = instrument.lookup(EnvironmentProvider.class);
                truffleAdapter.register(envProvider.getEnvironment(), executorWrapper);

                InetSocketAddress socketAddress = hostAndPort.createSocket();
                int port = socketAddress.getPort();
                Integer backlog = options.get(SocketBacklogSize);
                InetAddress address = socketAddress.getAddress();
                ServerSocket serverSocket = new ServerSocket(port, backlog, address);
                List<Pair<String, SocketAddress>> delegates = createDelegateSockets(options.get(Delegates));
                LanguageServerImpl.create(truffleAdapter, info, err).start(serverSocket, delegates).thenRun(() -> {
                    try {
                        executorWrapper.executeWithDefaultContext(() -> {
                            context.leave();
                            return null;
                        }).get();
                    } catch (ExecutionException | InterruptedException ex) {
                    }
                    executorWrapper.shutdown();
                    notifyClose();
                }).exceptionally((throwable) -> {
                    throwable.printStackTrace(err);
                    notifyClose();
                    return null;
                });
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable e) {
                String message = String.format("[Graal LSP] Starting server on %s failed: %s", hostAndPort.getHostPort(), e.getLocalizedMessage());
                new LSPIOException(message, e).printStackTrace(err);
            }

            return null;
        });
        return truffleAdapter;
    }

    private static List<Pair<String, SocketAddress>> createDelegateSockets(List<LanguageAndAddress> hostPorts) {
        if (hostPorts.isEmpty()) {
            return Collections.emptyList();
        }
        List<Pair<String, SocketAddress>> sockets = new ArrayList<>(hostPorts.size());
        for (LanguageAndAddress langAddress : hostPorts) {
            sockets.add(Pair.create(langAddress.getLanguageId(), langAddress.getAddress().createSocket()));
        }
        return sockets;
    }

    private static final class ContextAwareExecutorImpl implements ContextAwareExecutor {
        private final Context.Builder contextBuilder;
        static final String WORKER_THREAD_ID = "LS Context-aware Worker";
        Context lastNestedContext = null;
        private volatile WeakReference<Thread> workerThread = new WeakReference<>(null);
        /**
         * This implementation uses a single-thread-executor, so that there is only one worker
         * Thread which calls the Truffle-API. This way, no further synchronization of data
         * structures is needed in the language server.
         */
        private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final ThreadFactory factory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = factory.newThread(r);
                thread.setName(WORKER_THREAD_ID);
                workerThread = new WeakReference<>(thread);
                return thread;
            }
        });

        private ContextAwareExecutorImpl(Context.Builder contextBuilder) {
            this.contextBuilder = contextBuilder;
        }

        @Override
        public <T> Future<T> executeWithDefaultContext(Callable<T> taskWithResult) {
            return execute(taskWithResult);
        }

        @Override
        public <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult, boolean cached) {
            return execute(wrapWithNewContext(taskWithResult, cached));
        }

        private <T> Future<T> execute(Callable<T> taskWithResult) {
            if (Thread.currentThread() == workerThread.get()) {
                FutureTask<T> futureTask = new FutureTask<>(taskWithResult);
                futureTask.run();
                return futureTask;
            }

            return executor.submit(taskWithResult);
        }

        private <T> Callable<T> wrapWithNewContext(Callable<T> taskWithResult, boolean cached) {
            return new Callable<T>() {

                @Override
                public T call() throws Exception {
                    Context context;
                    if (cached) {
                        if (lastNestedContext == null) {
                            lastNestedContext = contextBuilder.build();
                        }
                        context = lastNestedContext;
                    } else {
                        context = contextBuilder.build();
                    }

                    try {
                        context.enter();
                        try {
                            return taskWithResult.call();
                        } finally {
                            context.leave();
                        }
                    } finally {
                        if (!cached) {
                            context.close();
                        }
                    }
                }
            };
        }

        @Override
        public void shutdown() {
            executor.shutdownNow();
        }

        @Override
        public void resetContextCache() {
            if (lastNestedContext != null) {
                lastNestedContext.close();
            }
            lastNestedContext = contextBuilder.build();
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
                port = address;
                host = null;
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
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Invalid port number: " + port);
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
    }

    static final class LanguageAndAddress {

        private final String languageId;
        private final HostAndPort address;

        private LanguageAndAddress(String languageId, HostAndPort address) {
            this.languageId = languageId;
            this.address = address;
        }

        static LanguageAndAddress parse(String la) {
            int at = la.indexOf('@');
            if (at < 0) {
                return new LanguageAndAddress(null, HostAndPort.parse(la));
            } else {
                return new LanguageAndAddress(la.substring(0, at), HostAndPort.parse(la.substring(at + 1)));
            }
        }

        void verify() {
            if (languageId != null && languageId.isEmpty()) {
                throw new IllegalArgumentException("Unknown empty language specified.");
            }
            address.verify();
        }

        String getLanguageId() {
            return languageId;
        }

        HostAndPort getAddress() {
            return address;
        }
    }
}
