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
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;

import de.hpi.swa.trufflelsp.LanguageServerBootstrapper;
import de.hpi.swa.trufflelsp.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.ContextAwareExecutorWrapperRegistry;
import de.hpi.swa.trufflelsp.VirtualLanguageServerFileProvider;
import de.hpi.swa.trufflelsp.filesystem.LSPFileSystem;

public class TruffleLanguageServerLauncher extends AbstractLanguageLauncher {
    private final ArrayList<String> lspargs = new ArrayList<>();

    public static void main(String[] args) {
        new TruffleLanguageServerLauncher().launch(args);
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

        contextBuilder.fileSystem(LSPFileSystem.newFullIOFileSystem(userDir, lspFileProvider));
        contextBuilder.engine(engine);

        ContextAwareExecutorWrapperRegistry registry = instrument.lookup(ContextAwareExecutorWrapperRegistry.class);
        ContextAwareExecutorWrapper executorWrapper = new ContextAwareExecutorWrapper() {

            private ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                private final ThreadFactory factory = Executors.defaultThreadFactory();

                public Thread newThread(Runnable r) {
                    Thread thread = factory.newThread(r);
                    thread.setName("LSP server Graal worker thread");
                    return thread;
                }
            });

            public <T> Future<T> executeWithDefaultContext(Supplier<T> taskWithResult) {
                return executor.submit(new Callable<T>() {

                    public T call() throws Exception {
                        return taskWithResult.get();
                    }
                });
            }

            public <T> Future<T> executeWithNestedContext(Supplier<T> taskWithResult) {
                return executor.submit(new Callable<T>() {

                    public T call() throws Exception {
                        try (Context newContext = contextBuilder.build()) {
                            newContext.enter();
                            try {
                                return taskWithResult.get();
                            } finally {
                                newContext.leave();
                            }
                        }
                    }
                });
            }

            public void shutdown() {
                executor.shutdownNow();
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

            executorWrapper.executeWithDefaultContext(() -> defaultContext.leave()).get();
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
