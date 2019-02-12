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
package org.graalvm.tools.lsp.launcher;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.api.ContextAwareExecutorRegistry;
import org.graalvm.tools.lsp.api.LanguageServerBootstrapper;
import org.graalvm.tools.lsp.api.VirtualLanguageServerFileProvider;
import org.graalvm.tools.lsp.launcher.filesystem.LSPFileSystem;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;

/**
 * A special launcher used to start the LSP server. As the language server executes arbitrary source
 * snippets to collect run-time information, sandboxing of file system and {@link Context}s is
 * needed. This class provides the glue between Truffle-API and Graal-SDK. It does a look-up for the
 * an LSP instrument and uses its services ({@link VirtualLanguageServerFileProvider},
 * {@link ContextAwareExecutorRegistry} and {@link LanguageServerBootstrapper}) to register
 * callbacks for a custom file system, the creation of new {@link Context} instances and the start
 * of the actual LSP language server.
 *
 */
public final class GraalLanguageServerLauncher extends AbstractLanguageLauncher {
    private final ArrayList<String> lspargs = new ArrayList<>();

    public static void main(String[] args) {
        new GraalLanguageServerLauncher().launch(args);
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();

        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            if (arg.startsWith("--lsp")) {
                lspargs.add(arg.split("--")[1]);
            } else {
                unrecognized.add(arg);
            }
        }

        unrecognized.add("--polyglot");
        return unrecognized;
    }

    @Override
    protected void launch(Builder contextBuilder) {
        String userDirString = System.getProperty("user.home");
        Path userDir = null;
        if (userDirString != null) {
            userDir = Paths.get(userDirString);
        }

        org.graalvm.polyglot.Engine.Builder newBuilder = Engine.newBuilder();
        newBuilder.option("lsp", "").option("lsp.Languagespecific.hacks", "true");
        for (String arg : lspargs) {
            int index = arg.indexOf('=');
            if (index != -1) {
                newBuilder.option(arg.substring(0, index), arg.substring(index + 1));
            } else {
                newBuilder.option(arg, "true");
            }
        }
        Engine engine = newBuilder.build();
        Instrument instrument = engine.getInstruments().get("lsp");
        VirtualLanguageServerFileProvider lspFileProvider = instrument.lookup(VirtualLanguageServerFileProvider.class);

        contextBuilder.fileSystem(LSPFileSystem.newReadOnlyFileSystem(userDir, lspFileProvider));
        contextBuilder.engine(engine);

        ContextAwareExecutorRegistry registry = instrument.lookup(ContextAwareExecutorRegistry.class);
        ContextAwareExecutor executorWrapper = new ContextAwareExecutorImpl(contextBuilder);
        registry.register(executorWrapper);

        Future<Context> futureDefaultContext = executorWrapper.executeWithDefaultContext(() -> {
            // Create and enter the default context from "Context-aware worker"-thread
            Context context = contextBuilder.build();
            context.enter();
            return context;
        });

        try (Context defaultContext = futureDefaultContext.get()) {
            LanguageServerBootstrapper bootstrapper = instrument.lookup(LanguageServerBootstrapper.class);
            Future<?> futureStartServer = bootstrapper.startServer();
            futureStartServer.get(); // blocking until LSP server is shutting down

            executorWrapper.executeWithDefaultContext(() -> {
                defaultContext.leave();
                return null;
            }).get();
            executorWrapper.shutdown();
        } catch (InterruptedException | ExecutionException e) {
            throw abort(e, -1);
        }
    }

    @Override
    protected String getLanguageId() {
        // Actually we are no launcher for a language but need to specify an identifier
        return "GraalLSP";
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {

    }

    @Override
    protected void collectArguments(Set<String> options) {

    }

    @Override
    protected void printVersion(Engine engine) {
        // Checkstyle: stop system..print check
        System.out.println(String.format("%s (GraalVM %s)", getLanguageId(), engine.getVersion()));
        // Checkstyle: resume system..print check
    }

    private static final class ContextAwareExecutorImpl implements ContextAwareExecutor {
        private final Builder contextBuilder;
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

        private ContextAwareExecutorImpl(Builder contextBuilder) {
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
