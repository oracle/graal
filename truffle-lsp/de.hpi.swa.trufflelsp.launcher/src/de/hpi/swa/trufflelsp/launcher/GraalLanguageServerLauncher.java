package de.hpi.swa.trufflelsp.launcher;

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
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapperRegistry;
import de.hpi.swa.trufflelsp.api.LanguageServerBootstrapper;
import de.hpi.swa.trufflelsp.api.VirtualLanguageServerFileProvider;
import de.hpi.swa.trufflelsp.filesystem.LSPFileSystem;

public class GraalLanguageServerLauncher extends AbstractLanguageLauncher {
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
            if (arg.indexOf('=') != -1) {
                newBuilder.option(arg.split("=")[0], arg.split("=")[1]);
            } else {
                newBuilder.option(arg, "true");
            }
        }
        Engine engine = newBuilder.build();
        Instrument instrument = engine.getInstruments().get("lsp");
        VirtualLanguageServerFileProvider lspFileProvider = instrument.lookup(VirtualLanguageServerFileProvider.class);

        contextBuilder.fileSystem(LSPFileSystem.newReadOnlyFileSystem(userDir, lspFileProvider));
        contextBuilder.engine(engine);

        ContextAwareExecutorWrapperRegistry registry = instrument.lookup(ContextAwareExecutorWrapperRegistry.class);
        ContextAwareExecutorWrapper executorWrapper = new ContextAwareExecutorWrapper() {
            String GRAAL_WORKER_THREAD_ID = "LSP server Graal worker thread";
            Context lastNestedContext = null;

            private ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                private final ThreadFactory factory = Executors.defaultThreadFactory();

                public Thread newThread(Runnable r) {
                    Thread thread = factory.newThread(r);
                    thread.setName(GRAAL_WORKER_THREAD_ID);
                    return thread;
                }
            });

            public <T> Future<T> executeWithDefaultContext(Callable<T> taskWithResult) {
                return execute(taskWithResult);
            }

            public <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult, boolean cached) {
                return execute(wrapWithNewContext(taskWithResult, cached));
            }

            private <T> Future<T> execute(Callable<T> taskWithResult) {
                if (GRAAL_WORKER_THREAD_ID.equals(Thread.currentThread().getName())) {
                    FutureTask<T> futureTask = new FutureTask<>(taskWithResult);
                    futureTask.run();
                    return futureTask;
                }

                return executor.submit(taskWithResult);
            }

            private <T> Callable<T> wrapWithNewContext(Callable<T> taskWithResult, boolean cached) {
                return new Callable<T>() {

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

            public void shutdown() {
                executor.shutdownNow();
            }

            public void resetCachedContext() {
                if (lastNestedContext != null) {
                    lastNestedContext.close();
                }
                lastNestedContext = contextBuilder.build();
            }
        };
        registry.register(executorWrapper);

        Future<Context> futureDefaultContext = executorWrapper.executeWithDefaultContext(() -> {
            // Create and enter the default context from "LSP server Graal worker"-thread
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
        System.out.println(String.format("%s (GraalVM %s)", getLanguageId(), engine.getVersion()));
    }
}
