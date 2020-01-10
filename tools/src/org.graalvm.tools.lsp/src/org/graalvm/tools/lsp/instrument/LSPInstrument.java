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

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
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

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.exceptions.LSPIOException;
import org.graalvm.tools.lsp.instrument.LSOptions.HostAndPort;
import org.graalvm.tools.lsp.instrument.LSOptions.LanguageAndAddress;
import org.graalvm.tools.lsp.server.LanguageServerImpl;
import org.graalvm.tools.lsp.server.LSPFileSystem;
import org.graalvm.tools.lsp.server.TruffleAdapter;
import org.graalvm.tools.lsp.server.utils.CoverageEventNode;

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.collections.Pair;

@Registration(id = LSPInstrument.ID, name = "Language Server", version = "0.1", services = {EnvironmentProvider.class})
public final class LSPInstrument extends TruffleInstrument implements EnvironmentProvider {
    public static final String ID = "lsp";

    private OptionValues options;
    private Env environment;
    private EventBinding<ExecutionEventNodeFactory> eventFactoryBinding;
    private volatile boolean waitForClose = false;

    @Override
    protected void onCreate(Env env) {
        env.registerService(this);
        this.environment = env;
        options = env.getOptions();
        if (options.hasSetOptions()) {
            final TruffleAdapter truffleAdapter = launchServer(new PrintWriter(env.out(), true), new PrintWriter(env.err(), true));
            SourceSectionFilter eventFilter = SourceSectionFilter.newBuilder().includeInternal(options.get(LSOptions.Internal)).build();
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
        return new LSOptionsOptionDescriptors();
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

        setWaitForClose();

        TruffleAdapter truffleAdapter = new TruffleAdapter(options.get(LSOptions.DeveloperMode));

        Context.Builder builder = Context.newBuilder();
        builder.allowAllAccess(true);
        builder.engine(Engine.create());
        builder.fileSystem(LSPFileSystem.newReadOnlyFileSystem(truffleAdapter));
        ContextAwareExecutor executorWrapper = new ContextAwareExecutorImpl(builder);

        executorWrapper.executeWithDefaultContext(() -> {
            Context context = builder.build();
            context.enter();

            Instrument instrument = context.getEngine().getInstruments().get(ID);
            EnvironmentProvider envProvider = instrument.lookup(EnvironmentProvider.class);
            truffleAdapter.register(envProvider.getEnvironment(), executorWrapper);

            HostAndPort hostAndPort = options.get(LSOptions.Lsp);
            try {
                InetSocketAddress socketAddress = hostAndPort.createSocket();
                int port = socketAddress.getPort();
                Integer backlog = options.get(LSOptions.SocketBacklogSize);
                InetAddress address = socketAddress.getAddress();
                ServerSocket serverSocket = new ServerSocket(port, backlog, address);
                List<Pair<String, SocketAddress>> delegates = createDelegateSockets(options.get(LSOptions.Delegates));
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
                });
            } catch (IOException e) {
                String message = String.format("[Graal LSP] Starting server on %s failed: %s", hostAndPort.getHostPort(), e.getLocalizedMessage());
                throw new LSPIOException(message, e);
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
}
